package com.example.executorchllamademo.adso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorchllamademo.adso.AdsoViewModel
import com.example.executorchllamademo.adso.Screen
import com.example.executorchllamademo.adso.Session
import com.example.executorchllamademo.adso.data.Content
import com.example.executorchllamademo.adso.data.SessionKind
import com.example.executorchllamademo.adso.data.SkillLevel
import com.example.executorchllamademo.adso.llm.LlamaEngine
import com.example.executorchllamademo.ui.components.OrbState
import com.example.executorchllamademo.ui.theme.AdsoTheme
import java.time.LocalTime

@Composable
fun kindStyle(kind: SessionKind): Triple<ImageVector, Color, Color> {
    val c = AdsoTheme.colors
    return when (kind) {
        SessionKind.LEARN -> Triple(Icons.Rounded.Lightbulb, c.info, c.infoTile)
        SessionKind.PRACTICE -> Triple(Icons.Rounded.Edit, c.warning, c.warningTile)
        SessionKind.QUIZ -> Triple(Icons.Rounded.TrackChanges, c.warning, c.warningTile)
        SessionKind.REVIEW -> Triple(Icons.Rounded.Refresh, c.success, c.successTile)
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning,"
    in 12..16 -> "Good afternoon,"
    else -> "Good evening,"
}

@Composable
private fun SessionRow(day: String?, s: Session, highlight: Boolean = false, onStart: (() -> Unit)? = null) {
    val c = AdsoTheme.colors
    val (icon, fg, bg) = kindStyle(s.kind)
    GlassCard(outline = if (highlight) c.info else c.cardOutline, padding = 14.dp, onClick = onStart) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon, fg, bg)
            Column(Modifier.weight(1f)) {
                Text("${s.kind.label} · ${Content.topic(s.topicId).name}", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(day, "${s.minutes} min", if (day == null) s.why else null).joinToString(" · "),
                    color = c.textSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
            if (onStart != null && highlight) CircleIconButton(Icons.AutoMirrored.Rounded.ArrowForward, "Start ${s.kind.label}", onStart, filled = true, size = 40.dp)
        }
    }
}

@Composable
fun StepDot(n: Int, done: Boolean, now: Boolean, size: Int = 28) {
    val c = AdsoTheme.colors
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (done) c.success else if (now) c.primary else c.cardRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Rounded.Check, null, tint = Color(0xFF06140E), modifier = Modifier.size((size * 0.55).dp))
        else Text("$n", color = if (now) Color.White else c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- Home
@Composable
fun HomeScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    var draft by rememberSaveable { mutableStateOf("") }
    val name = vm.profile.name.trim().ifEmpty { "there" }
    val focus = vm.firstSession
    ScrollPage(Modifier.statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(c.primaryGradient)), contentAlignment = Alignment.Center) {
                Text(name.first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text(
                "${greeting()} $name",
                color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(vm.course.title, color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(Pill).clickable(role = Role.Button) { vm.go(Screen.COURSES) }.padding(horizontal = 6.dp, vertical = 10.dp))
        }

        // The orb is the centrepiece; it starts thinking while you type.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Orb(if (draft.isNotBlank()) OrbState.THINKING else OrbState.IDLE, 200.dp)
            Text(tr("What should we study today?"), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, textAlign = TextAlign.Center)
            Text("Ask me anything, or start today’s focus below.", color = c.textSecondary, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val send = { if (draft.isNotBlank()) { vm.openChat(draft.trim()); draft = "" } }
                AdsoTextField(draft, { draft = it }, tr("Ask AdSo anything…"), Modifier.weight(1f), onDone = send)
                CircleIconButton(Icons.Rounded.ArrowUpward, tr("Send"), send, filled = true, size = 54.dp)
            }
        }

        // Today's focus
        Row(
            Modifier.fillMaxWidth().clip(CardShape)
                .background(Brush.linearGradient(listOf(Color(0xFF18245A), Color(0xFF0B1026))))
                .border(1.dp, Color(0x1AFFFFFF), CardShape).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(tr("Today’s focus").uppercase(), color = Color(0xFFB9C9FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                Text(Content.topic(focus.topicId).name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("${focus.kind.label} · ${focus.minutes} min · ${focus.why}", color = Color(0xFFC7D2F0), fontSize = 13.sp, lineHeight = 19.sp)
            }
            PrimaryButton(tr("Start"), { vm.openSession(focus) })
        }

        // Your learning pathway: the route, in order, with how much is actually done.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(tr("Your learning pathway"), action = "Open", onAction = { vm.planTabRoute = true; vm.go(Screen.PLAN) })
            if (vm.personalising) Tag(tr("Llama is personalising your pathway…"), c.info, c.infoTile)
            GlassCard {
                val done = vm.pathwayDone
                val total = vm.topics.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$done of $total topics done", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${done * 100 / total.coerceAtLeast(1)}%", color = c.info, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(10.dp))
                LevelBar(done / total.coerceAtLeast(1).toFloat(), c.info)
                Spacer(Modifier.size(12.dp))
                val now = vm.currentTopicId
                vm.route.forEachIndexed { i, step ->
                    val isDone = step.topicId in vm.completed
                    val isNow = step.topicId == now
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StepDot(i + 1, isDone, isNow)
                        Text(Content.topic(step.topicId).name, color = if (isDone || isNow) c.textPrimary else c.textSecondary, fontSize = 14.sp,
                            fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        Text(if (isDone) tr("Done") else if (isNow) tr("Now") else vm.levelOf(step.topicId).label,
                            color = if (isDone) c.success else if (isNow) c.info else c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // For your interests: each topic you picked turns the current topic into a real example and an exercise, by Llama.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(tr("For your interests"), action = "Edit", onAction = { vm.go(Screen.EDIT_GOALS) })
            val topicName = Content.topic(focus.topicId).name
            val picked = vm.interestSet.toList().take(4)
            if (picked.isEmpty()) {
                GlassCard(onClick = { vm.go(Screen.EDIT_GOALS) }) {
                    Text("Pick topics you like (robotics, music, finance…) and AdSo shows how $topicName is used there.", color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            picked.forEach { interest ->
                GlassCard(onClick = { vm.openChat("Show me how $topicName is used in $interest: one real example, then a short exercise for me to try.") }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(Icons.Rounded.AutoAwesome, c.info, c.infoTile)
                        Column(Modifier.weight(1f)) {
                            Text("$topicName in $interest", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("A real example and a small exercise, written by Llama", color = c.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(tr("Coming up"), action = "See plan", onAction = { vm.planTabRoute = false; vm.go(Screen.PLAN) })
            vm.upcoming.forEach { (day, s) -> SessionRow(day, s, onStart = { vm.openSession(s) }) }
        }
    }
}

// ---------------------------------------------------------------- Plan
@Composable
fun PlanScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    val p = vm.profile
    ScrollPage(Modifier.statusBarsPadding()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(tr("Study plan"))
                Spacer(Modifier.weight(1f))
                CircleIconButton(Icons.Rounded.SwapHoriz, "Switch course", { vm.go(Screen.COURSES) }, size = 36.dp)
            }
            Text(vm.course.title, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            Text("${p.goalType} · ${Content.minutesLabel(p.minutesPerDay)} a day · until ${vm.deadlineLabel()}", color = c.textSecondary, fontSize = 14.sp)
        }
        if (p.planNote.isNotBlank()) {
            GlassCard(outline = c.info, fill = c.infoTile, padding = 14.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = c.info, modifier = Modifier.size(20.dp))
                    Column {
                        Text(tr("Plan updated"), color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(p.planNote, color = c.textPrimary, fontSize = 13.sp, lineHeight = 19.sp)
                        if (vm.personalising) { Tag(tr("Llama is personalising your pathway…"), c.info, c.card) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(Pill).background(c.card).border(1.dp, c.cardOutline, Pill).padding(4.dp)) {
            listOf(false to tr("This week"), true to tr("Learning route")).forEach { (route, label) ->
                val on = vm.planTabRoute == route
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp).clip(Pill).background(if (on) c.primary else Color.Transparent)
                        .clickable(role = Role.Tab) { vm.planTabRoute = route },
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (on) Color.White else c.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (!vm.planTabRoute) {
            vm.plan.forEachIndexed { di, day ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text(day.label, color = if (di == 0) c.info else c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(Content.minutesLabel(day.totalMinutes), color = c.textSecondary, fontSize = 12.sp)
                    }
                    day.sessions.forEachIndexed { si, s ->
                        SessionRow(null, s, highlight = di == 0 && si == 0, onStart = { vm.openSession(s) })
                    }
                }
            }
        } else {
            if (vm.pathway?.byLlama == true) Text("Order, reasons and goals written by Llama on this phone.", color = c.textSecondary, fontSize = 12.sp)
            val now = vm.currentTopicId
            Column {
                vm.route.forEachIndexed { i, step ->
                    val done = step.topicId in vm.completed
                    val isNow = step.topicId == now
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StepDot(i + 1, done, isNow, 34)
                            if (i < vm.route.lastIndex) Box(Modifier.width(2.dp).heightIn(min = 56.dp).background(c.cardOutline))
                        }
                        GlassCard(Modifier.weight(1f).padding(bottom = 12.dp), outline = if (isNow) c.info else c.cardOutline, padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Content.topic(step.topicId).name, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                val (fg, bg) = when { done -> c.success to c.successTile; isNow -> c.info to c.infoTile; else -> skillColors(vm.levelOf(step.topicId)) }
                                Tag(if (done) tr("Done") else if (isNow) tr("Now") else vm.levelOf(step.topicId).label, fg, bg)
                            }
                            Text("Goal: ${step.goal}", color = c.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                            Text("Why: ${step.why}", color = c.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
        GhostButton(tr("Rebuild plan"), { vm.saveGoalsAndReplan() }, Modifier.fillMaxWidth(), leading = Icons.Rounded.Refresh)
        Text(
            if (vm.pathway?.byLlama == true) "Pathway by Llama on this phone, from your goals, interests and skill check. Changes after every quiz."
            else "Built from your skill check and quiz results. Changes after every quiz.",
            color = c.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------- Lesson (Learn / Practice / Review)
@Composable
fun LessonScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    val s = vm.lessonSession ?: return
    val topic = Content.topic(s.topicId)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        BackHeader("${s.kind.label} · ${s.minutes} min", { vm.back() })
        ScrollPage(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Orb(if (vm.lessonLoading) OrbState.GENERATING else OrbState.RESTING, 64.dp)
                Column(Modifier.weight(1f)) {
                    Eyebrow(vm.course.title)
                    Text(topic.name, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                    Text("Goal: ${topic.goal}", color = c.textSecondary, fontSize = 13.sp)
                }
            }
            GlassCard {
                when {
                    vm.lessonFailed -> Text(
                        "Llama couldn’t write this lesson${LlamaEngine.lastError?.let { " ($it)" } ?: ""}. Check the model in Settings, or go straight to the quiz.",
                        color = c.textSecondary, fontSize = 15.sp, lineHeight = 22.sp,
                    )
                    vm.lessonText.isBlank() -> Text("Llama is writing your lesson on this phone…", color = c.textSecondary, fontSize = 15.sp)
                    else -> Text(vm.lessonText + if (vm.lessonLoading) " ▌" else "", color = c.textPrimary, fontSize = 15.sp, lineHeight = 23.sp)
                }
            }
            if (!vm.lessonLoading && !vm.lessonFailed) Text("Written by Llama on this phone for your goal and interests.", color = c.textSecondary, fontSize = 12.sp)
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.kind == SessionKind.REVIEW) {
                PrimaryButton("Mark as reviewed", { vm.finishLesson(thenQuiz = false) }, Modifier.fillMaxWidth(), enabled = !vm.lessonLoading)
                GhostButton(tr("Practice with a quiz"), { vm.finishLesson(thenQuiz = true) }, Modifier.fillMaxWidth())
            } else {
                PrimaryButton(tr("Practice with a quiz"), { vm.finishLesson(thenQuiz = true) }, Modifier.fillMaxWidth(), enabled = !vm.lessonLoading || vm.lessonText.isNotBlank())
            }
        }
    }
}

// ---------------------------------------------------------------- Adaptive quiz
@Composable
fun QuizScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    val total = vm.quizTotal
    val q = vm.quizQuestion
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleIconButton(Icons.Rounded.Close, "Quit quiz", { vm.back() })
            LevelBar((vm.quizIndex + if (vm.quizChecked) 1 else 0) / total.toFloat(), c.info, Modifier.weight(1f))
            Text("${vm.quizIndex + 1}/$total", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (q == null || vm.quizLoading) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Orb(OrbState.THINKING, 150.dp)
                Spacer(Modifier.size(28.dp))
                Text(tr("Writing your next question…"), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.size(6.dp))
                Text(
                    "${vm.quizDifficulty} · ${Content.topic(vm.quizTopic).name}\nLlama is writing a question on this phone and checking its answer.",
                    color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                )
            }
            return@Column
        }
        val right = vm.quizPicked == q.answer
        ScrollPage(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(vm.quizDifficulty, c.textSecondary, c.cardRaised)
                Tag(if (vm.quizQuestionByLlama) "Written by Llama" else "Question bank", if (vm.quizQuestionByLlama) c.success else c.textSecondary, if (vm.quizQuestionByLlama) c.successTile else c.cardRaised)
            }
            Text(Content.topic(vm.quizTopic).name, color = c.info, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(q.text, color = c.textPrimary, fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                q.options.forEachIndexed { i, o ->
                    val state = when {
                        vm.quizChecked && i == q.answer -> OptionState.RIGHT
                        vm.quizChecked && i == vm.quizPicked -> OptionState.WRONG
                        !vm.quizChecked && i == vm.quizPicked -> OptionState.SELECTED
                        else -> OptionState.NONE
                    }
                    OptionRow(('A' + i).toString(), o, state) { vm.pickQuiz(i) }
                }
            }
        }
        if (!vm.quizChecked) {
            PrimaryButton(tr("Check"), { vm.checkQuiz() }, Modifier.fillMaxWidth().padding(20.dp), enabled = vm.quizPicked != null)
        } else {
            val ink = if (right) c.success else c.error
            GlassCard(Modifier.padding(12.dp), outline = ink, fill = if (right) c.successTile else c.errorTile) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(ink), contentAlignment = Alignment.Center) {
                        Icon(if (right) Icons.Rounded.Check else Icons.Rounded.Close, null, tint = c.background, modifier = Modifier.size(18.dp))
                    }
                    Text(if (right) tr("Correct") else tr("Not quite"), color = ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.size(10.dp))
                Text(if (right) q.why else "The answer is ${q.options[q.answer]}. ${q.why}", color = c.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.size(12.dp))
                PrimaryButton(if (vm.quizIndex < total - 1) tr("Next question") else tr("See result"), { vm.nextQuiz() }, Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------------------------------------------------------- Result
@Composable
fun ResultScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    val r = vm.lastResult ?: return
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScrollPage(Modifier.weight(1f).padding(top = 24.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Orb(OrbState.RESTING, 150.dp)
                Text(if (r.correct >= 2) tr("Nice work!") else tr("Good effort"), style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCard(Modifier.weight(1f), padding = 14.dp) {
                    Text("${r.correct * 100 / r.total}%", color = c.success, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Accuracy (${r.correct} of ${r.total})", color = c.textSecondary, fontSize = 12.sp)
                }
                GlassCard(Modifier.weight(1f), padding = 14.dp) {
                    Text(if (r.topicId in vm.completed) tr("Done") else r.newLevel.label, color = c.info, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${Content.topic(r.topicId).name} now", color = c.textSecondary, fontSize = 12.sp)
                }
            }
            GlassCard(outline = c.info, fill = c.infoTile, padding = 14.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = c.info, modifier = Modifier.size(20.dp))
                    Text(r.note, color = c.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            // Llama reviews the quiz: advice from the mistakes and the goal, and it may re-order the pathway.
            if (vm.coachBusy) {
                GlassCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Orb(OrbState.THINKING, 40.dp)
                        Text("Llama is reviewing your answers…", color = c.textSecondary, fontSize = 14.sp)
                    }
                }
            }
            vm.coachAdvice?.let { advice ->
                GlassCard(padding = 14.dp) {
                    Text(tr("Llama’s advice"), color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(4.dp))
                    Text(advice, color = c.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                    vm.coachMoved?.let { Spacer(Modifier.size(8.dp)); Tag("$it moved to the front of your pathway", c.info, c.infoTile) }
                }
            }
        }
        PrimaryButton(tr("See updated plan"), { vm.planTabRoute = false; vm.go(Screen.PLAN) }, Modifier.fillMaxWidth().padding(20.dp))
    }
}
