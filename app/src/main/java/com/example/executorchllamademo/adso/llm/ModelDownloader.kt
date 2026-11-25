package com.example.executorchllamademo.adso.llm

import android.content.Context
import android.os.StatFs
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * One-time download of the default model (Llama 3.2 1B QAT+LoRA, ExecuTorch build) into the app's own storage, so
 * people who install the APK don't need a PC or adb. After this AdSo works fully offline. Downloads resume if cut
 * off, and the model file is checked against its published SHA-256 before it is used.
 */
object ModelDownloader {
    private const val TAG = "AdsoDownload"
    private const val BASE = "https://huggingface.co/executorch-community/Llama-3.2-1B-Instruct-QLORA_INT4_EO8-ET/resolve/main/"
    private data class Part(val name: String, val bytes: Long, val sha256: String)
    private val parts = listOf(
        Part("tokenizer.model", 2_183_982L, "82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55"),
        Part("Llama-3.2-1B-Instruct-QLORA_INT4_EO8.pte", 1_181_451_008L, "ab7b7ebf97421c7770ad97d4abfdf8650a7d9f9dc1ce9a0c4663d49f7b7a08ac"),
    )
    val totalBytes = parts.sumOf { it.bytes }

    var running by mutableStateOf(false); private set
    var downloaded by mutableStateOf(0L); private set
    var stage by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set

    fun dir(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, "models").apply { mkdirs() }

    /** Downloads what is missing. Returns true when the model is ready to load. */
    suspend fun download(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (running) return@withContext false
        running = true; error = null
        try {
            val dir = dir(context)
            downloaded = parts.sumOf { minOf(File(dir, it.name).length(), it.bytes) }
            val need = parts.filter { File(dir, it.name).length() != it.bytes }.sumOf { it.bytes }
            if (StatFs(dir.path).availableBytes < need + 200_000_000L) error("Not enough free space: about ${"%.1f".format(need / 1e9)} GB is needed.")
            for (p in parts) {
                val done = File(dir, p.name)
                if (done.length() == p.bytes) continue
                val tmp = File(dir, p.name + ".part")
                stage = "Downloading ${if (p.name.endsWith(".pte")) "Llama 3.2 1B" else "tokenizer"}…"
                fetch(BASE + p.name, tmp, p.bytes)
                stage = "Checking the file…"
                if (sha256(tmp) != p.sha256) { tmp.delete(); error("The download was damaged. Please try again.") }
                if (!tmp.renameTo(done)) error("Couldn't save ${p.name}.")
            }
            stage = "Ready"
            true
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "download failed", e)
            error = e.message ?: "Download failed. Check the internet connection and try again."
            false
        } finally {
            running = false
        }
    }

    private suspend fun fetch(url: String, tmp: File, size: Long) {
        val before = parts.takeWhile { !url.endsWith(it.name) }.sumOf { it.bytes }
        var have = tmp.length()
        var redirects = 0
        var target = url
        while (true) {
            val c = URL(target).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000; c.readTimeout = 30_000
            if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
            val code = c.responseCode
            if (code in 300..399 && redirects < 5) { target = URL(URL(target), c.getHeaderField("Location")).toString(); redirects++; c.disconnect(); continue }
            if (code == 200) have = 0 else if (code != 206) error("Server answered $code")
            RandomAccessFile(tmp, "rw").use { out ->
                out.setLength(have); out.seek(have)
                c.inputStream.use { input ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); have += n
                        downloaded = before + have
                    }
                }
            }
            c.disconnect()
            if (have != size) error("Download stopped early. Tap Download to resume.")
            return
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { val b = ByteArray(1 shl 20); while (true) { val n = it.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
