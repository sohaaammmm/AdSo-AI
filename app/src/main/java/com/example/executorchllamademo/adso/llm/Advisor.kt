package com.example.executorchllamademo.adso.llm

import android.content.Context
import com.example.executorchllamademo.ETLogging
import com.example.executorchllamademo.adso.data.CheckQuestion
import com.example.executorchllamademo.adso.data.Course
import com.example.executorchllamademo.adso.data.Courses
import com.example.executorchllamademo.adso.data.ProfileEntity
import com.example.executorchllamademo.adso.data.QuizQuestion
import com.example.executorchllamademo.adso.data.SessionKind
import com.example.executorchllamademo.adso.data.SkillLevel
import com.example.executorchllamademo.adso.data.Topic
import org.json.JSONArray
import org.json.JSONObject

/** One step of the learning pathway, in order, with Llama's reason and goal. */
data class PathStep(val topicId: String, val why: String, val goal: String)

data class Pathway(val steps: List<PathStep>, val summary: String, val byLlama: Boolean) {
    fun toJson(): String = JSONObject().apply {
        put("summary", summary)
        put("byLlama", byLlama)
        put("steps", JSONArray().apply { steps.forEach { put(JSONObject().put("id", it.topicId).put("why", it.why).put("goal", it.goal)) } })
    }.toString()

    companion object {
        fun fromJson(s: String): Pathway? = try {
            val o = JSONObject(s)
            val a = o.getJSONArray("steps")
            Pathway((0 until a.length()).map { a.getJSONObject(it).let { j -> PathStep(j.getString("id"), j.optString("why"), j.optString("goal")) } }, o.optString("summary"), o.optBoolean("byLlama"))
        } catch (_: Exception) { null }
    }
}

/**
 * The advisory part of AdSo, done by Llama on the phone: the learning pathway (from goals, interests and
 * skill gaps), adaptive quiz questions, lessons, the chat tutor, and new courses.
 * Structured answers are requested as JSON and parsed leniently (temperature 0.7 makes formatting less
 * reliable), retried once, and if that still fails AdSo falls back to its built-in rules and says so.
 */
object Advisor {
    private const val JSON_ONLY = "Reply with valid JSON only. No markdown, no text before or after the JSON."

    private fun persona(p: ProfileEntity) =
        "You are AdSo, a friendly, encouraging study coach inside an offline phone app. The learner is a ${p.studyLevel} student." +
            if (p.language == "hi") " Write all learner-facing text in Hindi (Devanagari script); keep code, formulas and JSON keys in English." else ""

    // ---------------------------------------------------------------- pathway
    /** Rule-based pathway: gaps first (needs work, then new), strong topics last as review. */
    fun rulesPathway(course: Course, levels: Map<String, SkillLevel>): Pathway {
        val order = listOf(SkillLevel.SHAKY, SkillLevel.NEW, SkillLevel.STRONG)
        val steps = course.topics.sortedBy { order.indexOf(levels[it.id] ?: SkillLevel.NEW) }.map {
            val why = when (levels[it.id] ?: SkillLevel.NEW) {
                SkillLevel.SHAKY -> "You missed this in the skill check"
                SkillLevel.NEW -> "New topic for you"
                SkillLevel.STRONG -> "You know this; a short review keeps it fresh"
            }
            PathStep(it.id, why, it.goal)
        }
        return Pathway(steps, "Gaps first, then new topics, then a quick review of what you know.", byLlama = false)
    }

    suspend fun pathway(context: Context, p: ProfileEntity, course: Course, levels: Map<String, SkillLevel>, deadline: String): Pathway {
        val topics = course.topics.joinToString("\n") { "- ${it.id}: ${it.name} (${(levels[it.id] ?: SkillLevel.NEW).label})" }
        val user = """
            Goal type: ${p.goalType}. Goal: "${p.goalText.ifBlank { p.goalType }}". Target date: $deadline.
            Study time: ${p.minutesPerDay} minutes a day. Interests: ${p.interests.replace(",", ", ").ifBlank { "none given" }}.
            Course: ${course.title}. Skill check result per topic:
            $topics

            Order all ${course.topics.size} topics into a learning pathway for this learner. Topics that need work or are new go first,
            strong topics last as quick review. For each topic write "why" (max 12 words, linked to the learner's goal or interests)
            and "goal" (one concrete, checkable goal, max 10 words).
            Return JSON exactly like: {"pathway":[{"id":"${course.topics.first().id}","why":"...","goal":"..."}],"summary":"one encouraging sentence"}
        """.trimIndent()
        repeat(2) { attempt ->
            val reply = LlamaEngine.ask(context, persona(p) + " " + JSON_ONLY, if (attempt == 0) user else "$user\nYour previous reply was not valid JSON. Reply with the JSON object only.", 380)
            val parsed = reply?.let { parsePathway(it, course, levels) }
            log("pathway attempt ${attempt + 1}: ${if (parsed != null) "ok" else "unusable"}: ${reply?.take(400)}")
            if (parsed != null) return parsed
            if (reply == null) return rulesPathway(course, levels)
        }
        return rulesPathway(course, levels)
    }

    private fun parsePathway(reply: String, course: Course, levels: Map<String, SkillLevel>): Pathway? {
        val obj = extractJson(reply) ?: return null
        val arr = obj.optJSONArray("pathway") ?: obj.optJSONArray("steps") ?: return null
        val ids = course.topics.map { it.id }.toSet()
        val steps = mutableListOf<PathStep>()
        for (i in 0 until arr.length()) {
            val j = arr.optJSONObject(i) ?: continue
            val id = j.optString("id").trim()
            if (id !in ids || steps.any { it.topicId == id }) continue
            steps += PathStep(id, j.optString("why").trim().take(90).ifBlank { "Part of your pathway" }, j.optString("goal").trim().take(80).ifBlank { Courses.topic(id).goal })
        }
        if (steps.size < course.topics.size / 2 + 1) return null
        rulesPathway(course, levels).steps.filter { s -> steps.none { it.topicId == s.topicId } }.forEach { steps += it }
        return Pathway(steps, obj.optString("summary").trim().take(160), byLlama = true)
    }

    // ---------------------------------------------------------------- quiz
    /** One adaptive question about [topicId] at [difficulty], or null if Llama's reply can't be used. */
    suspend fun question(context: Context, topicId: String, difficulty: String, p: ProfileEntity, avoid: List<String>): QuizQuestion? {
        val topic = Courses.topic(topicId)
        val course = Courses.courseOf(topicId)
        val interest = p.interests.split(',').firstOrNull { it.isNotBlank() }
        // Kept short on purpose: every prompt and output token costs time on the phone.
        val user = """
            One multiple-choice question on "${topic.name}" (${course.title}), difficulty: $difficulty.${if (interest != null) " Use a $interest example if natural." else ""}
            ${if (avoid.isNotEmpty()) "Not these: " + avoid.joinToString(" | ") { it.take(60) } else ""}
            4 options of max 8 words, exactly one correct; "answer" = its index 0-3; "why" = max 15 words.
            JSON: {"q":"...","options":["...","...","...","..."],"answer":0,"why":"..."}
        """.trimIndent()
        repeat(2) { attempt ->
            val reply = LlamaEngine.ask(context, "You write quiz questions for a ${p.studyLevel} student. " + JSON_ONLY, user, 170) ?: return null
            val q = parseQuestion(reply)
            log("question attempt ${attempt + 1} ($difficulty): ${if (q != null) "ok" else "unusable"}: ${reply.take(300)}")
            if (q != null) return q
        }
        return null
    }

    private fun parseQuestion(reply: String): QuizQuestion? {
        val o = extractJson(reply) ?: return null
        val text = (o.optString("q").ifBlank { o.optString("question") }).trim()
        val arr = o.optJSONArray("options") ?: return null
        val options = (0 until arr.length()).map { cleanOption(arr.optString(it)) }.filter { it.isNotBlank() }
        val answer = o.optInt("answer", -1)
        if (text.length < 8 || options.size != 4 || options.toSet().size != 4 || answer !in 0..3) return null
        return QuizQuestion(text.take(220), options.map { it.take(90) }, answer, o.optString("why").trim().take(200).ifBlank { "The correct option is ${options[answer]}." })
    }

    private fun cleanOption(s: String) = s.trim().replace(Regex("^[A-Da-d][).:]\\s+"), "")

    // ---------------------------------------------------------------- lessons
    /** A short lesson for a Learn / Practice / Review session, streamed as it is written. */
    suspend fun lesson(context: Context, p: ProfileEntity, topicId: String, kind: SessionKind, onText: (String) -> Unit): String? {
        val topic = Courses.topic(topicId)
        val course = Courses.courseOf(topicId)
        val interest = p.interests.split(',').firstOrNull { it.isNotBlank() } ?: "everyday life"
        val task = when (kind) {
            SessionKind.LEARN -> "Teach \"${topic.name}\" from scratch in about 130 words, then give one worked example related to $interest."
            SessionKind.PRACTICE -> "Give a 60-word recap of \"${topic.name}\", then two short practice problems with their answers on a separate line starting with 'Answer:'."
            SessionKind.REVIEW -> "Write a quick review of \"${topic.name}\": 4 key points as short lines starting with '•', then one tricky mistake to avoid."
            SessionKind.QUIZ -> "Give 3 tips for answering questions about \"${topic.name}\"."
        }
        val user = "Course: ${course.title}. Learner goal: ${p.goalText.ifBlank { p.goalType }}. Lesson goal: ${topic.goal}.\n$task\nUse plain text, no markdown headings or bold. End with one line starting 'Try this:' and a quick question."
        return LlamaEngine.ask(context, persona(p), user, 360, onText)
    }

    // ---------------------------------------------------------------- chat tutor
    /** System prompt for the Ask AdSo chat: knows the learner's goal, course and gaps. */
    fun chatSystem(p: ProfileEntity, course: Course, levels: Map<String, SkillLevel>): String {
        val gaps = course.topics.filter { levels[it.id] != SkillLevel.STRONG }.joinToString { it.name }.ifBlank { "none" }
        return persona(p) + " Learner goal: ${p.goalText.ifBlank { p.goalType }} (${p.goalType}). Current course: ${course.title}. " +
            "Topics that still need work: $gaps. Interests: ${p.interests.replace(",", ", ")}. " +
            "Answer clearly in a few short paragraphs or steps, with a small example when it helps. " +
            "If the question is outside the learner's studies, answer briefly. If you are not sure, say so. Use plain text, no markdown."
    }

    // ---------------------------------------------------------------- custom courses
    /** Asks Llama to design a new course: 5 topics and a 5-question skill check. Null if unusable. */
    suspend fun newCourse(context: Context, p: ProfileEntity, name: String, onProgress: (Int) -> Unit): Course? {
        val user = """
            Design a short course called "$name" for this learner (goal: ${p.goalText.ifBlank { p.goalType }}).
            Give exactly 5 topics in a sensible learning order, each with a concrete goal (max 10 words).
            Then give exactly 5 multiple-choice skill-check questions, one per topic (topic = 1 to 5), each with 3 options and the index (0-2) of the correct one.
            Return JSON exactly like:
            {"topics":[{"name":"...","goal":"..."}],"check":[{"topic":1,"q":"...","options":["...","...","..."],"answer":0}]}
        """.trimIndent()
        repeat(2) { attempt ->
            val reply = LlamaEngine.ask(context, persona(p).substringBefore(" Write all") + " " + JSON_ONLY, user, 700) { onProgress(it.length) } ?: return null
            val c = parseCourse(reply, name)
            log("new course attempt ${attempt + 1}: ${if (c != null) "ok" else "unusable"}: ${reply.take(500)}")
            if (c != null) return c
        }
        return null
    }

    private fun parseCourse(reply: String, name: String): Course? {
        val o = extractJson(reply) ?: return null
        val t = o.optJSONArray("topics") ?: return null
        val c = o.optJSONArray("check") ?: return null
        val id = "c" + Integer.toHexString((name.lowercase() + System.currentTimeMillis()).hashCode()).takeLast(6)
        val topics = (0 until t.length()).mapNotNull { i ->
            t.optJSONObject(i)?.let { j -> j.optString("name").trim().takeIf { it.isNotBlank() }?.let { Topic("$id.t${i + 1}", it.take(50), j.optString("goal").trim().take(80)) } }
        }.take(6)
        if (topics.size < 3) return null
        val check = (0 until c.length()).mapNotNull { i ->
            val j = c.optJSONObject(i) ?: return@mapNotNull null
            val ti = (j.optInt("topic", i + 1) - 1).coerceIn(0, topics.lastIndex)
            val arr = j.optJSONArray("options") ?: return@mapNotNull null
            val opts = (0 until arr.length()).map { cleanOption(arr.optString(it)) }.filter { it.isNotBlank() }
            val ans = j.optInt("answer", -1)
            val text = j.optString("q").trim()
            if (opts.size !in 3..4 || ans !in opts.indices || text.length < 6) null
            else CheckQuestion(topics[ti].id, text.take(200), opts.map { it.take(80) } + "Not sure yet", ans)
        }.distinctBy { it.topicId }
        // every topic needs a check question
        if (check.size < topics.size) return null
        return Course(id, name.trim().take(40), topics, topics.map { tp -> check.first { it.topicId == tp.id } }, custom = true)
    }

    /** Finds the first {...} block in the reply and parses it, tolerating text or code fences around it. */
    private fun extractJson(reply: String): JSONObject? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try { JSONObject(reply.substring(start, end + 1)) } catch (_: Exception) { null }
    }

    private fun log(msg: String) = ETLogging.getInstance().log("AdSo advisor: $msg")
}
