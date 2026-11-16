package com.example.executorchllamademo.adso.llm

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.executorchllamademo.BackendType
import com.example.executorchllamademo.DemoSharedPreferences
import com.example.executorchllamademo.ETLogging
import com.example.executorchllamademo.ModelType
import com.example.executorchllamademo.ModelUtils
import com.example.executorchllamademo.PromptFormat
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.File
import java.util.concurrent.Executors

/** A model file found on the phone. */
data class ModelFile(val path: String, val name: String, val sizeGb: Float) {
    val label get() = name.removeSuffix(".pte").replace("-Instruct", " Instruct").replace("_", " ").replace("-", " ")
    val is3b get() = name.contains("3B", ignoreCase = true)
}

/**
 * AdSo's on-device Llama (ExecuTorch, XNNPACK). One model in memory at a time: 3B needs ~2.5 GB.
 * Every request starts from a fresh context (resetContext) and uses the Llama 3 chat template.
 * Tokens are streamed to the caller as they are generated.
 */
object LlamaEngine {
    private const val TAG = "AdsoLlama"
    const val MODEL_DIR = "/data/local/tmp/llama"
    private const val TOKENIZER = "$MODEL_DIR/tokenizer.model"
    private const val CONTEXT = 2048

    enum class Status { NOT_LOADED, LOADING, READY, WORKING, MISSING, FAILED }

    var status by mutableStateOf(Status.NOT_LOADED); private set
    var modelName by mutableStateOf<String?>(null); private set
    var loadSeconds by mutableStateOf<Float?>(null); private set
    var lastTokensPerSecond by mutableStateOf<Float?>(null); private set
    var lastError by mutableStateOf<String?>(null); private set

    /** Project rule: sampling temperature 0.7 to 0.8. Changing it reloads the model on next use. */
    var temperature by mutableStateOf(0.7f)

    private var module: LlmModule? = null
    private var loadedPath: String? = null
    private var loadedTemperature = 0f
    private val mutex = Mutex()
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "adso-llama") }.asCoroutineDispatcher()

    /** Model files (.pte) on the phone, biggest first. */
    fun availableModels(): List<ModelFile> =
        (File(MODEL_DIR).listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".pte") && it.canRead() }
            .map { ModelFile(it.absolutePath, it.name, it.length() / 1e9f) }
            .sortedByDescending { it.sizeGb }

    /**
     * The model to use: the one chosen in AdSo (or model settings) if it exists. Otherwise the default,
     * Llama 3.2 1B (about 4x faster than 3B on the test phone: 8.2 vs 2.1 tokens/s), else whatever is there.
     */
    fun pickModel(context: Context): Pair<String, String>? {
        val s = DemoSharedPreferences(context).getModuleSettings()
        if (s.modelFilePath.isNotBlank() && File(s.modelFilePath).canRead() && File(s.tokenizerFilePath).canRead()) {
            return s.modelFilePath to s.tokenizerFilePath
        }
        if (!File(TOKENIZER).canRead()) return null
        val models = availableModels()
        val default = models.firstOrNull { it.name.contains("1B", ignoreCase = true) } ?: models.minByOrNull { it.sizeGb }
        return default?.let { it.path to TOKENIZER }
    }

    /** Remembers the learner's model choice (shared with the chat's model settings). Takes effect on next use. */
    fun selectModel(context: Context, path: String) {
        val prefs = DemoSharedPreferences(context)
        val s = prefs.getModuleSettings()
        val tok = if (File(s.tokenizerFilePath).canRead()) s.tokenizerFilePath else TOKENIZER
        prefs.saveModuleSettings(
            s.copy(
                modelFilePath = path, tokenizerFilePath = tok, temperature = temperature.toDouble(),
                modelType = ModelType.LLAMA_3, backendType = BackendType.XNNPACK,
                userPrompt = PromptFormat.getUserPromptTemplate(ModelType.LLAMA_3),
            )
        )
    }

    suspend fun ensureLoaded(context: Context): Boolean = withContext(thread) { mutex.withLock { loadLocked(context) } }

    private fun loadLocked(context: Context): Boolean {
        val picked = pickModel(context) ?: run {
            status = Status.MISSING
            lastError = "No model found in $MODEL_DIR"
            return false
        }
        if (module != null && loadedPath == picked.first && loadedTemperature == temperature) return true
        release()
        status = Status.LOADING
        val start = System.currentTimeMillis()
        return try {
            val m = LlmModule(ModelUtils.getModelCategory(ModelType.LLAMA_3, BackendType.XNNPACK), picked.first, picked.second, temperature)
            val rc = m.load()
            if (rc != 0) error("load() returned $rc")
            module = m
            loadedPath = picked.first
            loadedTemperature = temperature
            modelName = picked.first.substringAfterLast('/')
            loadSeconds = (System.currentTimeMillis() - start) / 1000f
            status = Status.READY
            lastError = null
            ETLogging.getInstance().log("AdSo loaded $modelName in ${loadSeconds}s (temperature $temperature)")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "load failed", e)
            status = Status.FAILED
            lastError = e.message ?: e.toString()
            false
        }
    }

    /** One conversation turn: user (true) or assistant (false) and its text. */
    data class Turn(val user: Boolean, val text: String)

    /**
     * Runs a request and returns the full reply (without the stop token), or null on failure.
     * [onToken] receives the reply as it grows, for live display. Older turns are dropped to fit the context.
     */
    suspend fun chat(
        context: Context,
        system: String,
        turns: List<Turn>,
        maxNewTokens: Int,
        onToken: ((String) -> Unit)? = null,
    ): String? = withContext(thread) {
        mutex.withLock {
            if (!loadLocked(context)) return@withContext null
            val m = module ?: return@withContext null
            val prompt = buildPrompt(system, turns, maxNewTokens)
            val stopToken = PromptFormat.getStopToken(ModelType.LLAMA_3)
            val out = StringBuilder()
            val callback = object : LlmCallback {
                override fun onResult(result: String) {
                    if (result == stopToken || result == "<|end_of_text|>") { m.stop(); return }
                    out.append(result)
                    onToken?.invoke(out.toString())
                }

                override fun onStats(stats: String) {
                    try {
                        val j = JSONObject(stats)
                        val n = j.getInt("generated_tokens")
                        val ms = j.getInt("inference_end_ms") - j.getInt("prompt_eval_end_ms")
                        if (ms > 0) lastTokensPerSecond = n * 1000f / ms
                    } catch (_: Exception) { }
                }
            }
            status = Status.WORKING
            try {
                m.resetContext()
                val seqLen = (estimateTokens(prompt) + maxNewTokens).coerceAtMost(CONTEXT)
                val t0 = System.currentTimeMillis()
                m.generate(prompt, seqLen, callback, false)
                ETLogging.getInstance().log("AdSo request: ${out.length} chars in ${System.currentTimeMillis() - t0} ms, ${lastTokensPerSecond ?: 0f} tok/s")
                out.toString().trim()
            } catch (e: Throwable) {
                Log.e(TAG, "generate failed", e)
                lastError = e.message ?: e.toString()
                null
            } finally {
                status = Status.READY
            }
        }
    }

    /** Single question, no history. */
    suspend fun ask(context: Context, system: String, user: String, maxNewTokens: Int, onToken: ((String) -> Unit)? = null) =
        chat(context, system, listOf(Turn(true, user)), maxNewTokens, onToken)

    /** Stops the reply being written (safe from any thread). */
    fun stop() { try { module?.stop() } catch (_: Throwable) { } }

    private fun estimateTokens(s: String) = s.length / 3 + 8 // ~3-4 characters per token

    private fun buildPrompt(system: String, turns: List<Turn>, maxNew: Int): String {
        val head = "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$system<|eot_id|>"
        val tail = "<|start_header_id|>assistant<|end_header_id|>\n\n"
        val budget = CONTEXT - maxNew - estimateTokens(head + tail)
        val kept = ArrayDeque<String>()
        var used = 0
        for (t in turns.asReversed()) {
            val s = "<|start_header_id|>${if (t.user) "user" else "assistant"}<|end_header_id|>\n\n${t.text}<|eot_id|>"
            val cost = estimateTokens(s)
            if (used + cost > budget && kept.isNotEmpty()) break
            kept.addFirst(s.take(budget * 3)) // never let one huge message overflow
            used += cost
        }
        return head + kept.joinToString("") + tail
    }

    /** Frees the model's memory. */
    fun release() {
        module?.let { try { it.stop(); it.resetNative() } catch (_: Throwable) { } }
        module = null
        loadedPath = null
        if (status != Status.MISSING) status = Status.NOT_LOADED
    }

    /** Releases on the model thread, waiting for any running request to finish. */
    suspend fun releaseNow() = withContext(thread) { mutex.withLock { release() } }
}
