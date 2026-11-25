package com.example.executorchllamademo.adso

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.executorchllamademo.adso.data.AdsoDatabase
import com.example.executorchllamademo.adso.data.ChatEntity
import com.example.executorchllamademo.adso.data.ChatMessageEntity
import com.example.executorchllamademo.adso.data.CheckQuestion
import com.example.executorchllamademo.adso.data.Course
import com.example.executorchllamademo.adso.data.Content
import com.example.executorchllamademo.adso.data.CourseEntity
import com.example.executorchllamademo.adso.data.Courses
import com.example.executorchllamademo.adso.data.DayLogEntity
import com.example.executorchllamademo.adso.data.ProfileEntity
import com.example.executorchllamademo.adso.data.QuizQuestion
import com.example.executorchllamademo.adso.data.SessionKind
import com.example.executorchllamademo.adso.data.SkillEntity
import com.example.executorchllamademo.adso.data.SkillLevel
import com.example.executorchllamademo.adso.llm.Advisor
import com.example.executorchllamademo.adso.llm.LlamaEngine
import com.example.executorchllamademo.adso.llm.ModelDownloader
import com.example.executorchllamademo.adso.llm.PathStep
import com.example.executorchllamademo.adso.llm.Pathway
import com.example.executorchllamademo.adso.llm.PlanChange
import com.example.executorchllamademo.adso.llm.shortModelName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class Screen { WELCOME, PROFILE, ACCOUNT, EDIT_GOALS, COURSES, ADD_COURSE, CHECK, GAPS, GENERATING, HOME, PLAN, PROGRESS, SETTINGS, QUIZ, RESULT, LESSON, CHAT }

data class Session(val kind: SessionKind, val topicId: String, val why: String, val minutes: Int)
data class PlanDay(val label: String, val sessions: List<Session>) {
    val totalMinutes get() = sessions.sumOf { it.minutes }
}
data class QuizResult(val topicId: String, val correct: Int, val total: Int, val newLevel: SkillLevel, val note: String)
data class RoadmapWeek(val label: String, val title: String, val detail: String, val topicIds: List<String>)
data class ChatMsg(val mine: Boolean, val text: String, val meta: String = "")

/**
 * AdSo's learning logic: goals, skill check, learning pathway, study plan, lessons, adaptive quiz,
 * chat and progress. Everything is stored on the phone with Room.
 *
 * Llama on the phone (LlamaEngine + Advisor) writes the pathway, quiz questions, lessons, chat answers
 * and new courses. If the model is missing or a reply can't be parsed, AdSo falls back to built-in
 * rules and content and says so in the UI.
 */
class AdsoViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AdsoDatabase.get(app).dao()
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
    private val saveLock = Mutex()

    var ready by mutableStateOf(false); private set
    var screen by mutableStateOf(Screen.WELCOME); private set
    var profile by mutableStateOf(ProfileEntity()); private set
    var planTabRoute by mutableStateOf(false)
    var generatingTitle by mutableStateOf("Building your plan…"); private set
    var generatingDetail by mutableStateOf(""); private set

    // Courses and skills
    var course by mutableStateOf(Courses.dataStructures); private set
    var addedCourses by mutableStateOf(listOf("ds")); private set
    var levels by mutableStateOf(emptyMap<String, SkillLevel>()); private set
    var baseLevels by mutableStateOf(emptyMap<String, SkillLevel>()); private set
    var completed by mutableStateOf(emptySet<String>()); private set
    private var pathways by mutableStateOf(emptyMap<String, Pathway>())
    val pathway: Pathway? get() = pathways[course.id]
    val topics get() = course.topics
    val hasSkillCheck get() = topics.any { it.id in baseLevels }

    // Skill check
    var checkIndex by mutableIntStateOf(0); private set
    var checkPicked by mutableStateOf<Int?>(null); private set
    private val checkAnswers = mutableStateListOf<Int>()

    // Quiz (questions are written one at a time, so the next one can match how you're doing)
    var quizTopic by mutableStateOf("sq"); private set
    var quizIndex by mutableIntStateOf(0); private set
    var quizPicked by mutableStateOf<Int?>(null); private set
    var quizChecked by mutableStateOf(false); private set
    var quizCorrect by mutableIntStateOf(0); private set
    var lastResult by mutableStateOf<QuizResult?>(null); private set
    private val quizQs = mutableStateListOf<QuizQuestion>()
    private val quizFromLlama = mutableStateListOf<Boolean>()
    var quizLoading by mutableStateOf(false); private set
    private val quizMissed = mutableListOf<String>()

    // Llama's advice after a quiz (it can also move the next topic to the front of the pathway)
    var coachAdvice by mutableStateOf<String?>(null); private set
    var coachBusy by mutableStateOf(false); private set
    var coachMoved by mutableStateOf<String?>(null); private set
    private var coachJob: Job? = null
    private var nextQuestion: Deferred<Pair<QuizQuestion, Boolean>>? = null
    private var quizJob: Job? = null
    val quizTotal = 3

    // Lesson
    var lessonSession by mutableStateOf<Session?>(null); private set
    var lessonText by mutableStateOf(""); private set
    var lessonLoading by mutableStateOf(false); private set
    var lessonFailed by mutableStateOf(false); private set
    private var lessonJob: Job? = null

    // Add course
    var newCourseBusy by mutableStateOf(false); private set
    var newCourseProgress by mutableIntStateOf(0); private set
    var newCourseError by mutableStateOf<String?>(null); private set

    // Chat
    var chats by mutableStateOf(emptyList<ChatEntity>()); private set
    var chatId by mutableStateOf<Long?>(null); private set
    val chatMessages = mutableStateListOf<ChatMsg>()
    var chatStreaming by mutableStateOf<String?>(null); private set
    var chatBusy by mutableStateOf(false); private set
    private var chatJob: Job? = null

    // Progress
    var week by mutableStateOf(emptyList<Pair<String, Int>>()); private set
    var weekSessions by mutableIntStateOf(0); private set
    var accuracy by mutableStateOf<Int?>(null); private set

    init {
        LlamaEngine.appModelDir = ModelDownloader.dir(app)
        viewModelScope.launch {
            dao.profile()?.let { profile = it }
            LlamaEngine.temperature = profile.temperature.coerceIn(0.7f, 0.8f)
            // Courses the learner added (custom ones are registered so the rest of the app can find them).
            val saved = dao.courses()
            saved.filter { it.custom }.forEach { e -> Course.fromJson(e.id, e.title, e.topicsJson, e.checkJson)?.let(Courses::register) }
            val p = saved.mapNotNull { e -> Pathway.fromJson(e.pathwayJson)?.let { e.id to it } }.toMap().toMutableMap()
            if ("ds" !in p) Pathway.fromJson(profile.pathwayJson)?.let { p["ds"] = it } // older installs
            pathways = p
            addedCourses = ((if (prefs.getBoolean(KEY_DS_REMOVED, false)) emptyList() else listOf("ds")) + saved.map { it.id }).distinct().ifEmpty { listOf("ds") }
            course = Courses.get(profile.activeCourse)
            loadSkills()
            refreshProgress()
            chats = dao.chats()
            screen = if (profile.onboarded) Screen.HOME else Screen.WELCOME
            ready = true
            // Load the model in the background so it's ready before the learner needs it.
            launch { if (LlamaEngine.ensureLoaded(getApplication())) fixOldCourseNames() }
        }
    }

    private suspend fun loadSkills() {
        val rows = dao.skills()
        levels = rows.associate { it.topicId to SkillLevel.valueOf(it.level) }
        baseLevels = rows.associate { it.topicId to SkillLevel.valueOf(it.baseLevel) }
        completed = rows.filter { it.completed }.map { it.topicId }.toSet()
    }

    /** Level shown for a topic; before the skill check, every topic counts as new. */
    fun levelOf(topicId: String) = levels[topicId] ?: SkillLevel.NEW

    private fun saveSkillsNow() {
        val rows = (levels.keys + baseLevels.keys).map { id ->
            SkillEntity(id, (baseLevels[id] ?: levelOf(id)).name, levelOf(id).name, id in completed)
        }
        viewModelScope.launch { saveLock.withLock { dao.saveSkills(rows) } }
    }

    // ---------- navigation ----------
    fun go(s: Screen) { screen = s }

    /** Returns false when the system should handle Back (leave the app). */
    fun back(): Boolean {
        screen = when (screen) {
            Screen.PROFILE -> Screen.WELCOME
            Screen.COURSES -> if (profile.onboarded) Screen.SETTINGS else Screen.PROFILE
            Screen.ADD_COURSE -> { if (newCourseBusy) return true; Screen.COURSES }
            Screen.CHECK -> {
                if (checkIndex > 0) { checkIndex--; checkPicked = checkAnswers.removeLastOrNull(); return true }
                Screen.COURSES
            }
            Screen.GAPS -> Screen.CHECK
            Screen.EDIT_GOALS -> Screen.SETTINGS
            Screen.ACCOUNT -> Screen.HOME
            Screen.QUIZ, Screen.RESULT -> { quizJob?.cancel(); nextQuestion?.cancel(); stopLlamaQuestions(); Screen.HOME }
            Screen.LESSON -> { lessonJob?.cancel(); LlamaEngine.stop(); Screen.HOME }
            Screen.CHAT -> { if (chatBusy) stopChat(); Screen.HOME }
            Screen.PLAN, Screen.PROGRESS, Screen.SETTINGS -> Screen.HOME
            Screen.WELCOME, Screen.HOME, Screen.GENERATING -> return false
        }
        return true
    }

    // ---------- profile and goals ----------
    /**
     * Updates the profile and saves it. Saves run one at a time and always write the newest
     * profile, so fast typing can't leave an older value (this was the "Priy" bug).
     */
    fun updateProfile(change: ProfileEntity.() -> ProfileEntity) {
        profile = profile.change()
        viewModelScope.launch { saveLock.withLock { dao.saveProfile(profile) } }
    }

    val interestSet get() = profile.interests.split(',').filter { it.isNotBlank() }.toSet()
    fun toggleInterest(i: String) = updateProfile {
        val set = interestSet.toMutableSet().apply { if (!add(i)) remove(i) }
        copy(interests = set.joinToString(","))
    }

    fun setTemperature(t: Float) {
        val v = (Math.round(t * 100) / 100f).coerceIn(0.7f, 0.8f)
        LlamaEngine.temperature = v
        updateProfile { copy(temperature = v) }
    }

    fun deadlineLabel(): String {
        if (profile.deadlineDays <= 0) return "no fixed date"
        return LocalDate.now().plusDays(profile.deadlineDays.toLong()).format(DateTimeFormatter.ofPattern(if (profile.deadlineDays > 180) "d MMM yyyy" else "d MMM", Locale.ENGLISH))
    }

    fun saveGoalsAndReplan() = buildPlanAnimated("Updating your plan…", "Re-fitted to ${Content.minutesLabel(profile.minutesPerDay)} a day until ${deadlineLabel()}.")

    // ---------- courses ----------
    fun switchCourse(id: String) {
        course = Courses.get(id)
        updateProfile { copy(activeCourse = id) }
        screen = if (hasSkillCheck) Screen.HOME else Screen.COURSES
    }

    private val prefs get() = getApplication<Application>().getSharedPreferences("adso", 0)

    /** Removes a course with its skill check, progress and pathway. At least one course always stays. */
    fun deleteCourse(id: String) {
        if (addedCourses.size <= 1 || id !in addedCourses) return
        val ids = Courses.get(id).topics.map { it.id }
        addedCourses = addedCourses - id
        pathways = pathways - id
        levels = levels - ids.toSet(); baseLevels = baseLevels - ids.toSet(); completed = completed - ids.toSet()
        if (id == "ds") prefs.edit().putBoolean(KEY_DS_REMOVED, true).apply()
        viewModelScope.launch { saveLock.withLock { dao.deleteCourse(id); dao.deleteSkills(ids) } }
        if (course.id == id) {
            course = Courses.get(addedCourses.first())
            updateProfile { copy(activeCourse = course.id) }
        }
    }

    /** Once per course: corrects the name of courses made before names were checked (e.g. "Tf antenna" -> "RF Antenna"). */
    private suspend fun fixOldCourseNames() {
        val ctx = getApplication<Application>()
        for (e in dao.courses().filter { it.custom && !prefs.getBoolean("named3_" + it.id, false) }) {
            val fixed = Advisor.fixTitle(ctx, e.title)
            prefs.edit().putBoolean("named3_" + e.id, true).apply()
            if (fixed == e.title) continue
            val c = Courses.get(e.id).copy(title = fixed)
            Courses.register(c)
            saveLock.withLock { dao.saveCourse(e.copy(title = fixed)) }
            if (course.id == c.id) course = c
        }
    }

    /** The learner renames a course (e.g. when autocorrect guessed wrong). Their name always wins. */
    fun renameCourse(id: String, title: String) {
        val t = Advisor.tidyTitle(title).take(40)
        if (t.isBlank()) return
        val c = Courses.get(id).copy(title = t)
        Courses.register(c)
        prefs.edit().putBoolean("named3_" + id, true).apply()
        if (course.id == id) course = c
        addedCourses = addedCourses.toMutableList() // new list instance so the Courses screen redraws
        viewModelScope.launch {
            saveLock.withLock { dao.saveCourse((dao.courses().firstOrNull { it.id == id } ?: CourseEntity(id, t)).copy(title = t)) }
        }
    }

    /** Adds a built-in course and starts its skill check. */
    fun addCourse(id: String) {
        if (id == "ds") prefs.edit().putBoolean(KEY_DS_REMOVED, false).apply()
        addedCourses = (addedCourses + id).distinct()
        viewModelScope.launch { saveLock.withLock { dao.saveCourse(CourseEntity(id, Courses.get(id).title)) } }
        course = Courses.get(id)
        updateProfile { copy(activeCourse = id) }
        startSkillCheck()
    }

    /** Asks Llama to design a new course from its name, then starts its skill check. */
    fun createCourse(name: String) {
        if (name.isBlank() || newCourseBusy) return
        newCourseBusy = true; newCourseError = null; newCourseProgress = 0
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val c = if (LlamaEngine.ensureLoaded(ctx)) Advisor.newCourse(ctx, profile, Advisor.fixTitle(ctx, name)) { newCourseProgress = it } else null
            newCourseBusy = false
            if (c == null) {
                newCourseError = if (LlamaEngine.status == LlamaEngine.Status.MISSING) "No model on this phone, so AdSo can't design a course. Pick one of the built-in courses."
                else "Llama's answer couldn't be used. Try again, or use a shorter course name."
                return@launch
            }
            Courses.register(c)
            saveLock.withLock { dao.saveCourse(CourseEntity(c.id, c.title, c.topicsJson(), c.checkJson(), "", custom = true)) }
            addedCourses = (addedCourses + c.id).distinct()
            course = c
            updateProfile { copy(activeCourse = c.id) }
            startSkillCheck()
        }
    }

    fun courseProgress(c: Course) = c.topics.count { it.id in completed } * 100 / c.topics.size.coerceAtLeast(1)

    // ---------- skill check ----------
    val checkQuestions: List<CheckQuestion> get() = course.check
    val checkQuestion: CheckQuestion get() = checkQuestions[checkIndex.coerceAtMost(checkQuestions.lastIndex)]
    val checkIsLast get() = checkIndex >= checkQuestions.lastIndex

    fun startSkillCheck() {
        checkIndex = 0; checkPicked = null; checkAnswers.clear()
        screen = Screen.CHECK
    }

    fun pickCheck(i: Int) { checkPicked = i }

    fun nextCheck() {
        val picked = checkPicked ?: return
        checkAnswers.add(picked)
        if (!checkIsLast) { checkIndex++; checkPicked = null; return }
        val found = checkQuestions.mapIndexed { i, q ->
            q.topicId to when (checkAnswers[i]) { q.answer -> SkillLevel.STRONG; q.notSureIndex -> SkillLevel.NEW; else -> SkillLevel.SHAKY }
        }.toMap()
        levels = levels + found
        baseLevels = baseLevels + found
        completed = completed - found.keys // a fresh check restarts this course's pathway
        saveSkillsNow()
        viewModelScope.launch { addStudy(minutes = 0, sessions = 1) }
        screen = Screen.GAPS
    }

    fun count(level: SkillLevel) = topics.count { levelOf(it.id) == level }

    fun makePlan() = buildPlanAnimated("Building your plan…", "")

    /** True while Llama is personalising the pathway in the background (shown as a small tag). */
    var personalising by mutableStateOf(false); private set
    private var personaliseJob: Job? = null

    /**
     * Shows the plan straight away (built from the skill check), then lets Llama personalise the
     * pathway in the background. The learner never waits for the model.
     */
    private fun buildPlanAnimated(title: String, note: String) {
        generatingTitle = title
        generatingDetail = "Ordering your topics…"
        screen = Screen.GENERATING
        planTabRoute = false
        val c = course
        val lv = c.topics.associate { it.id to levelOf(it.id) }
        viewModelScope.launch {
            val quick = Advisor.rulesPathway(c, lv)
            savePathway(c, quick)
            addedCourses = (addedCourses + c.id).distinct()
            updateProfile { copy(onboarded = true, planNote = listOfNotNull(note.ifBlank { null }, "Your gaps come first. Llama is now personalising the pathway for your goal.").joinToString(" ")) }
            kotlinx.coroutines.delay(900) // let the orb screen show briefly
            screen = Screen.PLAN
            personalise(c, lv, note)
        }
    }

    /** Background step: Llama orders the topics and writes reasons; replaces the quick pathway if usable. */
    /** The learner wants Llama now (chat, lesson, quiz): stop the background pathway so they don't wait. The quick plan stays. */
    private fun yieldBackgroundLlama() {
        if (coachBusy) { coachJob?.cancel(); LlamaEngine.stop(); coachBusy = false }
        if (!personalising) return
        personaliseJob?.cancel(); LlamaEngine.stop(); personalising = false
        updateProfile { copy(planNote = "Your gaps come first. Tap Rebuild plan any time to let Llama personalise it.") }
    }

    private fun personalise(c: Course, lv: Map<String, SkillLevel>, note: String) {
        personaliseJob?.cancel()
        personaliseJob = viewModelScope.launch {
            personalising = true
            val ctx = getApplication<Application>()
            val start = System.currentTimeMillis()
            val pw = if (LlamaEngine.ensureLoaded(ctx)) Advisor.pathway(ctx, profile, c, lv, deadlineLabel()) else null
            personalising = false
            val secs = (System.currentTimeMillis() - start) / 1000
            if (pw != null && pw.byLlama) {
                savePathway(c, pw)
                updateProfile { copy(planNote = listOfNotNull(note.ifBlank { null }, "Llama personalised your pathway on this phone (${secs}s).", pw.summary.ifBlank { null }).joinToString(" ")) }
            } else {
                updateProfile { copy(planNote = listOfNotNull(note.ifBlank { null }, "Your gaps come first. (Llama couldn’t personalise it this time; tap Rebuild plan to try again.)").joinToString(" ")) }
            }
        }
    }

    private suspend fun savePathway(c: Course, pw: Pathway) {
        pathways = pathways + (c.id to pw)
        val existing = dao.courses().firstOrNull { it.id == c.id }
        saveLock.withLock { dao.saveCourse((existing ?: CourseEntity(c.id, c.title)).copy(pathwayJson = pw.toJson())) }
    }

    // ---------- pathway and plan ----------
    /** Pathway steps in Llama's order. Finished topics go last. */
    val route: List<PathStep>
        get() = (pathway ?: Advisor.rulesPathway(course, topics.associate { it.id to levelOf(it.id) })).steps
            .filter { s -> topics.any { it.id == s.topicId } }
            .sortedBy { if (it.topicId in completed) 1 else 0 }

    /** Topics finished in AdSo (quiz passed or reviewed), not just "strong" in the skill check. */
    val pathwayDone get() = topics.count { it.id in completed }

    /** The first topic not finished yet. */
    val currentTopicId get() = route.firstOrNull { it.topicId !in completed }?.topicId

    val plan: List<PlanDay> get() = buildPlan()

    /**
     * Week-by-week roadmap to the target date, following the pathway order. New topics get the most weeks, topics that
     * need work fewer, known ones the least; if there is time left, the last week is a final review.
     */
    val roadmap: List<RoadmapWeek>
        get() {
            val open = route.filter { it.topicId !in completed }
            if (open.isEmpty()) return emptyList()
            val totalWeeks = if (profile.deadlineDays > 0) ((profile.deadlineDays + 6) / 7).coerceIn(1, 52) else open.size + 1
            val studyWeeks = if (totalWeeks > open.size) totalWeeks - 1 else totalWeeks
            val weight = open.map { when (levelOf(it.topicId)) { SkillLevel.NEW -> 3.0; SkillLevel.SHAKY -> 2.0; SkillLevel.STRONG -> 1.0 } }
            val groups: List<Pair<List<PathStep>, Int>> = if (studyWeeks >= open.size) {
                // Every topic gets at least a week; spare weeks go to the heaviest topics.
                val extra = studyWeeks - open.size
                val raw = weight.map { it / weight.sum() * extra }
                val weeks = raw.map { 1 + it.toInt() }.toMutableList()
                var left = studyWeeks - weeks.sum()
                raw.withIndex().sortedByDescending { it.value - it.value.toInt() }.forEach { if (left > 0) { weeks[it.index]++; left-- } }
                open.mapIndexed { i, s -> listOf(s) to weeks[i] }
            } else {
                val per = (open.size + studyWeeks - 1) / studyWeeks
                open.chunked(per).map { it to 1 }
            }
            val hoursPerWeek = profile.minutesPerDay * 7 / 60f
            var week = 1
            val out = groups.map { (steps, n) ->
                val label = if (n == 1) "Week $week" else "Weeks $week–${week + n - 1}"
                week += n
                RoadmapWeek(
                    label, steps.joinToString(" + ") { Courses.topic(it.topicId).name },
                    steps.joinToString(" ") { it.goal.trimEnd('.') + "." } + " About %.0f h.".format(hoursPerWeek * n),
                    steps.map { it.topicId },
                )
            }
            return if (totalWeeks > studyWeeks) out + RoadmapWeek("Week $week", "Final review", "Review every topic and take the quizzes again before ${deadlineLabel()}.", emptyList()) else out
        }
    val firstSession: Session get() = plan.firstOrNull()?.sessions?.firstOrNull() ?: Session(SessionKind.REVIEW, topics.first().id, "Keep it fresh", profile.minutesPerDay)
    val upcoming: List<Pair<String, Session>> get() = plan.flatMap { d -> d.sessions.map { d.label to it } }.drop(1).take(3)

    private fun buildPlan(): List<PlanDay> {
        val todo = route.filter { it.topicId !in completed }.ifEmpty { route }
        val sessions = todo.flatMap { step ->
            when (levelOf(step.topicId)) {
                SkillLevel.SHAKY -> listOf(SessionKind.PRACTICE to step.why, SessionKind.QUIZ to "Checks the practice worked")
                SkillLevel.NEW -> listOf(SessionKind.LEARN to step.why, SessionKind.QUIZ to "Checks what you just learned")
                SkillLevel.STRONG -> listOf(SessionKind.REVIEW to "You know this; a short review keeps it fresh")
            }.map { (k, why) -> Session(k, step.topicId, why, 0) }
        }
        val today = LocalDate.now()
        // More daily time = more sessions a day (each about 45-60 min), not one very long session.
        val perDay = (profile.minutesPerDay / 45).coerceIn(2, 6)
        return sessions.chunked(perDay).take(7).mapIndexed { i, chunk ->
            val each = profile.minutesPerDay / chunk.size
            val label = when (i) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> today.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            }
            PlanDay(label, chunk.map { it.copy(minutes = each) })
        }
    }

    /** Opens a session: quizzes go straight to the quiz, the rest start with a lesson. */
    fun openSession(s: Session) = if (s.kind == SessionKind.QUIZ) startQuiz(s.topicId) else startLesson(s)

    // ---------- lessons ----------
    fun startLesson(s: Session) {
        yieldBackgroundLlama()
        lessonJob?.cancel()
        lessonSession = s; lessonText = ""; lessonFailed = false; lessonLoading = true
        screen = Screen.LESSON
        lessonJob = viewModelScope.launch {
            val ctx = getApplication<Application>()
            val text = Advisor.lesson(ctx, profile, s.topicId, s.kind) { lessonText = it }
            lessonLoading = false
            if (text.isNullOrBlank()) lessonFailed = true else lessonText = text
        }
    }

    /** Done with the lesson: logs the time; a review finishes the topic, other lessons lead to the quiz. */
    fun finishLesson(thenQuiz: Boolean) {
        val s = lessonSession ?: return
        viewModelScope.launch { addStudy(minutes = 0, sessions = 1) }
        if (s.kind == SessionKind.REVIEW) {
            completed = completed + s.topicId
            levels = levels + (s.topicId to SkillLevel.STRONG)
            saveSkillsNow()
            updateProfile { copy(planNote = "Reviewed ${Content_topic(s.topicId)}. It’s marked done on your pathway.") }
        }
        if (thenQuiz) startQuiz(s.topicId) else screen = Screen.HOME
    }

    private fun Content_topic(id: String) = Courses.topic(id).name

    // ---------- adaptive quiz ----------
    val quizQuestion: QuizQuestion? get() = quizQs.getOrNull(quizIndex)
    val quizQuestionByLlama get() = quizFromLlama.getOrNull(quizIndex) == true
    val quizDifficulty get() = difficultyFor(quizCorrect, quizIndex).first

    /** Label shown to the learner, and the instruction given to Llama. */
    private fun difficultyFor(correct: Int, index: Int): Pair<String, String> = when {
        index == 0 -> "Warm-up" to "easy, check the basic idea"
        correct >= index -> "Getting harder" to "harder, needs applying the idea to a small example"
        correct == 0 -> "Easier" to "easy, a simple definition question"
        else -> "Medium" to "medium"
    }

    // Llama writes questions in the background while the learner answers; only answer-checked ones are queued.
    private val llamaQueue = mutableListOf<QuizQuestion>()
    private var llamaGenJob: Job? = null

    private fun startLlamaQuestions(topic: String) {
        llamaGenJob?.cancel()
        llamaQueue.clear()
        llamaGenJob = viewModelScope.launch {
            var failures = 0
            while (llamaQueue.size < 2 && quizQs.size + llamaQueue.size < quizTotal + 1 && failures < 2) {
                val avoid = quizQs.map { it.text } + llamaQueue.map { it.text }
                val q = Advisor.question(getApplication(), topic, difficultyFor(quizCorrect, quizQs.size).second, profile, avoid)
                if (q != null) llamaQueue.add(q) else failures++
            }
        }
    }

    private fun stopLlamaQuestions() {
        if (llamaGenJob?.isActive == true) LlamaEngine.stop()
        llamaGenJob?.cancel()
    }

    /**
     * The next question: a Llama question if one is ready and checked, else the next unused question from the
     * built-in bank (instant). Courses without a bank (made by Llama) wait for Llama.
     */
    private fun fetchQuestion(): Deferred<Pair<QuizQuestion, Boolean>> = viewModelScope.async {
        val topic = quizTopic
        val shown = quizQs.map { it.text }.toSet()
        llamaQueue.removeFirstOrNull()?.let { return@async it to true }
        val bank = Courses.courseOf(topic).bank[topic].orEmpty()
        bank.firstOrNull { it.text !in shown }?.let { return@async it to false }
        // Bank used up: wait briefly for Llama, then repeat a bank question. No bank (own course): wait up to ~2 min.
        repeat(if (bank.isEmpty()) 40 else 7) {
            llamaQueue.removeFirstOrNull()?.let { return@async it to true }
            if (llamaGenJob?.isActive != true) return@repeat
            kotlinx.coroutines.delay(3000)
        }
        llamaQueue.removeFirstOrNull()?.let { return@async it to true }
        bank.randomOrNull()?.let { return@async it to false } // repeat one rather than stop the quiz
        QuizQuestion(
            "AdSo couldn’t write a question for “${Courses.topic(topic).name}” just now. Which would you like to do?",
            listOf("Read the lesson again", "Ask AdSo in the chat", "Try the quiz later", "Continue"), 3,
            "Questions for your own courses are written by Llama on the phone, and this time its answer didn’t pass the check.",
        ) to false
    }

    fun startQuiz(topicId: String = firstSession.topicId) {
        yieldBackgroundLlama()
        quizJob?.cancel(); nextQuestion?.cancel(); stopLlamaQuestions()
        quizMissed.clear()
        quizTopic = topicId; quizIndex = 0; quizPicked = null; quizChecked = false; quizCorrect = 0
        quizQs.clear(); quizFromLlama.clear()
        screen = Screen.QUIZ
        startLlamaQuestions(topicId)
        showQuestion(fetchQuestion())
    }

    private fun showQuestion(pending: Deferred<Pair<QuizQuestion, Boolean>>) {
        quizLoading = true
        quizJob = viewModelScope.launch {
            val (q, byLlama) = pending.await()
            quizQs.add(q); quizFromLlama.add(byLlama)
            quizLoading = false
        }
    }

    fun pickQuiz(i: Int) { if (!quizChecked && !quizLoading) quizPicked = i }

    fun checkQuiz() {
        val p = quizPicked ?: return
        val q = quizQuestion ?: return
        if (quizChecked) return
        quizChecked = true
        if (p == q.answer) quizCorrect++ else quizMissed += q.text
    }

    fun nextQuiz() {
        if (quizIndex + 1 < quizTotal) {
            quizIndex++; quizPicked = null; quizChecked = false
            showQuestion(fetchQuestion())
            return
        }
        stopLlamaQuestions()
        finishQuiz()
    }

    /** Adapts the pathway: a pass finishes the topic and the next one comes first; otherwise more practice. */
    private fun finishQuiz() {
        val id = quizTopic
        val total = quizTotal
        val before = levelOf(id)
        val name = Courses.topic(id).name
        val note: String
        if (quizCorrect >= 2) {
            levels = levels + (id to SkillLevel.STRONG)
            completed = completed + id
            val next = currentTopicId
            note = "AdSo marked $name done because you scored $quizCorrect of $total" +
                (if (before != SkillLevel.STRONG) " (it was “${before.label}”)" else "") + ". " +
                (next?.let { "${Courses.topic(it).name} now comes first." } ?: "You finished every topic in ${course.title}!")
        } else {
            if (before == SkillLevel.STRONG) levels = levels + (id to SkillLevel.SHAKY)
            note = "AdSo kept $name on your pathway and added more practice, because you scored $quizCorrect of $total."
        }
        lastResult = QuizResult(id, quizCorrect, total, levelOf(id), note)
        updateProfile { copy(planNote = note) }
        saveSkillsNow()
        val correct = quizCorrect
        viewModelScope.launch { addStudy(minutes = 0, sessions = 1, answered = total, correct = correct) }
        screen = Screen.RESULT
        coachAfterQuiz(id, correct, total, quizMissed.toList(), note)
    }

    private fun coachAfterQuiz(topicId: String, correct: Int, total: Int, missed: List<String>, note: String) {
        coachJob?.cancel(); coachAdvice = null; coachMoved = null; coachBusy = true
        val c = course
        val lv = c.topics.associate { it.id to levelOf(it.id) }
        val open = route.filter { it.topicId !in completed }.map { it.topicId }
        coachJob = viewModelScope.launch {
            val ctx = getApplication<Application>()
            val r = if (LlamaEngine.ensureLoaded(ctx)) Advisor.coach(ctx, profile, c, topicId, correct, total, missed, open, lv, deadlineLabel()) else null
            coachBusy = false
            if (r == null) return@launch
            coachAdvice = r.advice
            var moved: String? = null
            val next = r.nextTopicId
            if (next != null && open.firstOrNull() != next) {
                val pw = pathways[c.id] ?: Advisor.rulesPathway(c, lv)
                pw.steps.firstOrNull { it.topicId == next }?.let { step ->
                    val front = step.copy(why = r.why ?: step.why)
                    savePathway(c, Pathway(listOf(front) + pw.steps.filter { it.topicId != next }, pw.summary, byLlama = true))
                    moved = Courses.topic(next).name
                }
            }
            coachMoved = moved
            // The rule-based note said which topic comes first; Llama just changed that, so drop the old claim.
            val shown = if (moved != null) note.replace(Regex(" ?[^.]* now comes first\\."), "").trim() else note
            if (moved != null) lastResult = lastResult?.copy(note = shown)
            updateProfile { copy(planNote = listOfNotNull(shown, "Llama:${r.advice}", moved?.let { "$it moved to the front of your pathway." }).joinToString(" ")) }
        }
    }

    // ---------- chat with Llama ----------
    val chatOrbThinking get() = chatBusy && chatStreaming.isNullOrEmpty()

    fun openChat(question: String? = null) {
        screen = Screen.CHAT
        if (question != null) { newChat(); sendChat(question) }
    }

    fun newChat() {
        if (chatBusy) stopChat()
        chatId = null; chatMessages.clear(); chatStreaming = null
    }

    fun openChatHistory(id: Long) {
        if (chatBusy) stopChat()
        viewModelScope.launch {
            chatId = id
            chatMessages.clear()
            chatMessages.addAll(dao.messages(id).map { ChatMsg(it.mine, it.text, it.meta) })
        }
    }

    fun deleteChat(id: Long) = viewModelScope.launch {
        dao.deleteMessages(id); dao.deleteChat(id)
        if (chatId == id) newChat()
        chats = dao.chats()
    }

    fun sendChat(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || chatBusy) return
        yieldBackgroundLlama()
        chatMessages.add(ChatMsg(true, text))
        chatBusy = true; chatStreaming = ""
        chatJob = viewModelScope.launch {
            // If the message asks to change the plan (time, date, focus, skip), change it first, then let Llama answer.
            val change = Advisor.planChange(getApplication(), profile, course, route.filter { it.topicId !in completed }.map { it.topicId }, text)
            val changed = change?.let { applyPlanChange(it) }
            if (changed != null) chatMessages.add(ChatMsg(false, "✓ $changed", "Plan updated"))
            val id = chatId ?: dao.insertChat(ChatEntity(title = text.take(40), updated = System.currentTimeMillis())).also { chatId = it }
            dao.insertMessage(ChatMessageEntity(chatId = id, mine = true, text = text, time = System.currentTimeMillis()))
            val ctx = getApplication<Application>()
            val replyLang = Content.detectLanguage(text, profile.language)
            val turns = chatMessages.map { LlamaEngine.Turn(it.mine, it.text) }.toMutableList().also { t ->
                // Repeat the language rule on the newest message; the 1B model follows it there.
                val i = t.indexOfLast { it.user }
                if (i >= 0) t[i] = t[i].copy(text = t[i].text + Advisor.languageRule(replyLang))
            }
            // Reply in the language the question is written in (the challenge asks: Hindi question -> Hindi answer).
            val system = Advisor.chatSystem(profile, course, topics.associate { it.id to levelOf(it.id) }, replyLang) +
                (changed?.let { " AdSo has just changed the study plan: $it Confirm it in one short sentence, then answer anything else they asked." } ?: "")
            val start = System.currentTimeMillis()
            val reply = LlamaEngine.chat(ctx, system, turns, Advisor.chatBudget(text)) { chatStreaming = Advisor.plain(it) }?.let(Advisor::plain)
            val answer = reply?.ifBlank { null } ?: chatStreaming?.ifBlank { null }
                ?: (LlamaEngine.lastError?.let { "I couldn’t answer: $it" } ?: "I couldn’t answer that. Check that a model is loaded in Settings.")
            val meta = listOfNotNull(
                LlamaEngine.modelName?.let(::shortModelName),
                LlamaEngine.lastTokensPerSecond?.let { "%.1f tokens/s".format(it) },
                "${(System.currentTimeMillis() - start) / 1000}s",
            ).joinToString(" · ")
            chatMessages.add(ChatMsg(false, answer, meta))
            chatStreaming = null; chatBusy = false
            dao.insertMessage(ChatMessageEntity(chatId = id, mine = false, text = answer, meta = meta, time = System.currentTimeMillis()))
            dao.touchChat(id, System.currentTimeMillis())
            chats = dao.chats()
        }
    }

    fun stopChat() { LlamaEngine.stop() }

    /** True when no model is on the phone yet (then Home and Settings offer the one-time download). */
    val needsModel get() = LlamaEngine.status == LlamaEngine.Status.MISSING || (LlamaEngine.status != LlamaEngine.Status.READY && LlamaEngine.availableModels().isEmpty())

    fun downloadModel() = viewModelScope.launch {
        val app = getApplication<Application>()
        if (ModelDownloader.download(app)) LlamaEngine.ensureLoaded(app)
    }


    /** Applies a plan change asked for in chat and returns what changed, in words (null if nothing did). */
    private suspend fun applyPlanChange(ch: PlanChange): String? {
        val parts = mutableListOf<String>()
        ch.minutesPerDay?.takeIf { it != profile.minutesPerDay }?.let { m -> updateProfile { copy(minutesPerDay = m) }; parts += "${Content.minutesLabel(m)} a day" }
        ch.deadlineDays?.takeIf { it != profile.deadlineDays }?.let { d -> updateProfile { copy(deadlineDays = d) }; parts += "target date ${deadlineLabel()}" }
        if (ch.doneTopicIds.isNotEmpty()) {
            completed = completed + ch.doneTopicIds
            levels = levels + ch.doneTopicIds.associateWith { SkillLevel.STRONG }
            saveSkillsNow()
            parts += ch.doneTopicIds.joinToString(", ") { Courses.topic(it).name } + " marked as known"
        }
        ch.focusTopicId?.let { f ->
            val c = course
            val pw = pathways[c.id] ?: Advisor.rulesPathway(c, c.topics.associate { it.id to levelOf(it.id) })
            pw.steps.firstOrNull { it.topicId == f }?.let { step ->
                savePathway(c, Pathway(listOf(step.copy(why = "You asked to focus on this")) + pw.steps.filter { it.topicId != f }, pw.summary, pw.byLlama))
                parts += "${Courses.topic(f).name} moved to the front"
            }
        }
        if (parts.isEmpty()) return null
        val text = "Plan updated: " + parts.joinToString("; ") + "."
        updateProfile { copy(planNote = text) }
        return text
    }

    /** Suggested questions for an empty chat, from the learner's current topic. */
    val chatSuggestions: List<String>
        get() {
            val t = currentTopicId?.let { Courses.topic(it).name } ?: topics.first().name
            return listOf("Explain $t like I’m new to it", "Give me a practice question on $t", "How should I study for: ${profile.goalText.ifBlank { profile.goalType }}?")
        }

    // ---------- progress ----------
    private suspend fun addStudy(minutes: Int, sessions: Int, answered: Int = 0, correct: Int = 0) {
        val today = LocalDate.now().format(iso)
        saveLock.withLock {
            val cur = dao.log(today) ?: DayLogEntity(today)
            dao.saveLog(cur.copy(minutes = cur.minutes + minutes, sessions = cur.sessions + sessions, answered = cur.answered + answered, correct = cur.correct + correct))
        }
        refreshProgress()
    }

    private suspend fun refreshProgress() {
        val today = LocalDate.now()
        val from = today.minusDays(6)
        val logs = dao.logsSince(from.format(iso)).associateBy { it.date }
        week = (0..6).map { i ->
            val d = from.plusDays(i.toLong())
            d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) to (logs[d.format(iso)]?.minutes ?: 0)
        }
        weekSessions = logs.values.sumOf { it.sessions }
        val answered = dao.totalAnswered()
        accuracy = if (answered > 0) (dao.totalCorrect() * 100 / answered) else null
    }

    // Real study time: counted while AdSo is open on screen, saved minute by minute, so the graph moves as you use the app.
    private var trackJob: Job? = null
    private var pendingSeconds = 0
    fun appVisible(visible: Boolean) {
        trackJob?.cancel()
        if (!visible) return
        trackJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                pendingSeconds += 10
                if (pendingSeconds >= 60) { pendingSeconds -= 60; addStudy(minutes = 1, sessions = 0) }
            }
        }
    }

    private companion object { const val KEY_DS_REMOVED = "ds_removed" }
}
