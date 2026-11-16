package com.example.executorchllamademo.adso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorchllamademo.adso.AdsoViewModel
import com.example.executorchllamademo.adso.Screen
import com.example.executorchllamademo.adso.data.Content
import com.example.executorchllamademo.adso.data.Courses
import com.example.executorchllamademo.adso.data.SkillLevel
import com.example.executorchllamademo.ui.components.OrbState
import com.example.executorchllamademo.ui.theme.AdsoTheme

@Composable
fun skillColors(level: SkillLevel) = AdsoTheme.colors.let {
    when (level) {
        SkillLevel.STRONG -> it.success to it.successTile
        SkillLevel.SHAKY -> it.warning to it.warningTile
        SkillLevel.NEW -> it.info to it.infoTile
    }
}

@Composable
fun FieldLabel(text: String, hint: String? = null) {
    Row {
        Text(tr(text), color = AdsoTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (hint != null) Text("  $hint", color = AdsoTheme.colors.textSecondary, fontSize = 14.sp)
    }
}

@Composable
fun AdsoTextField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null, singleLine: Boolean = true) {
    val c = AdsoTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = c.textSecondary) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (onDone != null) ImeAction.Send else ImeAction.Done),
        keyboardActions = KeyboardActions(onSend = { onDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.card, unfocusedContainerColor = c.card,
            focusedBorderColor = c.info, unfocusedBorderColor = c.cardOutline,
            focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.info,
        ),
    )
}

@Composable
fun ScrollPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) { content() }
}

// ---------------------------------------------------------------- Welcome
@Composable
fun WelcomeScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth()) { Tag("●  Works offline", c.success, c.successTile) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Orb(OrbState.IDLE, 210.dp)
            Text("AdSo AI", style = MaterialTheme.typography.displayLarge, color = c.textPrimary)
            Text(
                "Tell me your goal. I’ll find your skill gaps and build a learning pathway that changes as you learn.",
                color = c.textSecondary, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.width(310.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(tr("Get started"), { vm.go(Screen.PROFILE) }, Modifier.fillMaxWidth())
            Text("Runs Llama 3.2 on this phone with ExecuTorch", color = c.textSecondary, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- You and your goal (step 1, or editing later)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalScreen(vm: AdsoViewModel, editing: Boolean) {
    val c = AdsoTheme.colors
    val p = vm.profile
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (editing) BackHeader(tr("Your goal"), { vm.back() }) else StepHeader(1, 3) { vm.back() }
        ScrollPage(Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow(if (editing) "Your goal" else "You and your goal")
                Text(tr("What are you aiming for?"), style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
            }
            if (!editing) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("Language")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Content.languages.forEach { l -> SelectChip(l.native, p.language == l.code, { vm.updateProfile { copy(language = l.code) } }) }
                    }
                    Text("English and हिन्दी: full app and AI answers. Other languages: main menus; AI answers in English.", color = c.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("Your name")
                    AdsoTextField(p.name, { v -> vm.updateProfile { copy(name = v) } }, "e.g. Priya")
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("Where are you studying?")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Content.studyLevels.forEach { l -> SelectChip(l, p.studyLevel == l, { vm.updateProfile { copy(studyLevel = l) } }) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("Your goal")
                Content.goalTypes.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (label, sub) ->
                            SelectChip(label, p.goalType == label, { vm.updateProfile { copy(goalType = label) } }, Modifier.weight(1f), sub = sub, shapeRadius = 18.dp)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("In your words")
                AdsoTextField(p.goalText, { v -> vm.updateProfile { copy(goalText = v) } }, "e.g. Get an A in Data Structures")
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("Target date")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Content.deadlines.forEach { (label, days) -> SelectChip(label, p.deadlineDays == days, { vm.updateProfile { copy(deadlineDays = days) } }) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("Time each day")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Content.minuteOptions.forEach { m -> SelectChip("$m min", p.minutesPerDay == m, { vm.updateProfile { copy(minutesPerDay = m) } }, Modifier.weight(1f)) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("Topics you like", "(pick any)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Content.interests.forEach { i -> SelectChip(i, i in vm.interestSet, { vm.toggleInterest(i) }) }
                }
                Text("AdSo uses these for examples, like coding problems if you pick Coding.", color = c.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (editing) {
                PrimaryButton("Save and update plan", { vm.saveGoalsAndReplan() }, Modifier.fillMaxWidth())
            } else {
                PrimaryButton(tr("Continue"), { vm.go(Screen.COURSES) }, Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------------------------------------------------------- Courses (step 2, or from Settings)
@Composable
fun CoursesScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    val onboarding = !vm.profile.onboarded
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (onboarding) StepHeader(2, 3) { vm.back() } else BackHeader(tr("Courses"), { vm.back() })
        ScrollPage(Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow("Your courses")
                Text(tr("What are you studying?"), style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
                Text("Each course gets a short skill check, then its own learning pathway. Tap a course to work on it.", color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
            }
            vm.addedCourses.map { Courses.get(it) }.distinctBy { it.id }.forEach { course ->
                val active = course.id == vm.course.id
                val pct = vm.courseProgress(course)
                val checked = course.topics.any { it.id in vm.baseLevels }
                GlassCard(outline = if (active) c.info else c.cardOutline, onClick = { vm.switchCourse(course.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(if (course.custom) Icons.Rounded.AutoAwesome else Icons.AutoMirrored.Rounded.MenuBook, c.info, c.infoTile)
                        Column(Modifier.weight(1f)) {
                            Text(course.title, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                (if (checked) "${course.topics.count { it.id in vm.completed }} of ${course.topics.size} topics done" else "Skill check not taken yet") +
                                    (if (course.custom) " · made by Llama" else "") + (if (active) " · current" else ""),
                                color = c.textSecondary, fontSize = 13.sp,
                            )
                        }
                        Text("$pct%", color = c.info, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.size(12.dp))
                    LevelBar(pct / 100f, c.info)
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(20.dp))
                    .border(1.dp, c.cardOutline, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { vm.go(Screen.ADD_COURSE) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Add a course"), color = c.textSecondary, fontSize = 15.sp)
            }
            PrimaryButton("Start skill check for ${vm.course.title}", { vm.startSkillCheck() }, Modifier.fillMaxWidth())
            Text("${vm.course.check.size} questions, about 2 minutes. No score, just a starting point.", color = c.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------------------------------------------------------------- Add a course
@Composable
fun AddCourseScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackHeader(tr("Add a course"), { vm.back() })
        if (vm.newCourseBusy) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Orb(OrbState.GENERATING, 170.dp)
                Spacer(Modifier.size(28.dp))
                Text("Designing “$name”…", style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.size(8.dp))
                Text("Llama is choosing 5 topics for it on this phone. This takes about half a minute.", color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.size(12.dp))
                Tag("${vm.newCourseProgress} characters written", c.info, c.infoTile)
            }
            return@Column
        }
        ScrollPage(Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Ready-made courses")
                Courses.builtIn.forEach { course ->
                    val added = course.id in vm.addedCourses
                    GlassCard(onClick = { if (added) vm.switchCourse(course.id) else vm.addCourse(course.id) }, padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconTile(Icons.AutoMirrored.Rounded.MenuBook, c.info, c.infoTile)
                            Column(Modifier.weight(1f)) {
                                Text(course.title, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(course.topics.joinToString { it.name }, color = c.textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (added) Icon(Icons.Rounded.Check, "Added", tint = c.success) else Icon(Icons.Rounded.Add, "Add", tint = c.info)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Your own course")
                Text("Type any subject. Llama picks 5 topics for it on this phone, then you rate how well you know each one.", color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                AdsoTextField(name, { name = it }, "e.g. Operating Systems, Organic Chemistry", onDone = { vm.createCourse(name) })
                vm.newCourseError?.let { Text(it, color = c.error, fontSize = 13.sp, lineHeight = 18.sp) }
                PrimaryButton("Design with Llama", { vm.createCourse(name) }, Modifier.fillMaxWidth(), enabled = name.isNotBlank(), leading = Icons.Rounded.AutoAwesome)
            }
        }
    }
}

// ---------------------------------------------------------------- Skill check (step 3)
enum class OptionState { NONE, SELECTED, RIGHT, WRONG }

@Composable
fun OptionRow(key: String, label: String, state: OptionState, onClick: () -> Unit) {
    val c = AdsoTheme.colors
    val (outline, fill) = when (state) {
        OptionState.SELECTED -> c.info to c.infoTile
        OptionState.RIGHT -> c.success to c.successTile
        OptionState.WRONG -> c.error to c.errorTile
        OptionState.NONE -> c.cardOutline to c.card
    }
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(shape).background(fill).border(1.dp, outline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).border(1.dp, c.cardOutline, CircleShape), contentAlignment = Alignment.Center) {
            Text(key, color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(label, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
    }
}

@Composable
fun SkillCheckScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    val q = vm.checkQuestion
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        StepHeader(3, 3) { vm.back() }
        ScrollPage(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag("Skill check · ${Content.topic(q.topicId).name}", c.info, c.infoTile, Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Text("${vm.checkIndex + 1} of ${vm.checkQuestions.size}", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(q.text, color = c.textPrimary, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                q.options.forEachIndexed { i, o ->
                    OptionRow(('A' + i).toString(), o, if (vm.checkPicked == i) OptionState.SELECTED else OptionState.NONE) { vm.pickCheck(i) }
                }
            }
            Text("Pick “Not sure yet” rather than guessing. It helps AdSo find what to teach first.", color = c.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        PrimaryButton(if (vm.checkIsLast) tr("See my gaps") else tr("Next"), { vm.nextCheck() }, Modifier.fillMaxWidth().padding(20.dp), enabled = vm.checkPicked != null)
    }
}

// ---------------------------------------------------------------- Gaps
@Composable
fun GapsScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScrollPage(Modifier.weight(1f).padding(top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Orb(OrbState.THINKING, 64.dp)
                Column {
                    Eyebrow("Your skill gaps · ${vm.course.title}")
                    Text(tr("Here’s where you stand"), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SkillLevel.SHAKY to "Need work", SkillLevel.NEW to "New to you", SkillLevel.STRONG to "Strong").forEach { (l, label) ->
                    val (fg, _) = skillColors(l)
                    GlassCard(Modifier.weight(1f), padding = 12.dp) {
                        Text("${vm.count(l)}", color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(label, color = c.textSecondary, fontSize = 12.sp)
                    }
                }
            }
            GlassCard(padding = 16.dp) {
                vm.topics.forEachIndexed { i, t ->
                    val l = vm.levelOf(t.id)
                    val (fg, bg) = skillColors(l)
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Tag(l.label, fg, bg)
                        }
                        LevelBar(l.fraction, fg)
                    }
                    if (i < vm.topics.lastIndex) Box(Modifier.fillMaxWidth().size(1.dp).background(c.cardOutline))
                }
            }
            Text("Llama will put the topics you need most first, then fit them into ${vm.profile.minutesPerDay} minutes a day until ${vm.deadlineLabel()}.", color = c.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        PrimaryButton(tr("Make my learning pathway"), { vm.makePlan() }, Modifier.fillMaxWidth().padding(20.dp), leading = Icons.Rounded.AutoAwesome)
    }
}

// ---------------------------------------------------------------- Building the plan
@Composable
fun GeneratingScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Orb(OrbState.THINKING, 200.dp)
        Spacer(Modifier.size(36.dp))
        Text(vm.generatingTitle, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        Spacer(Modifier.size(8.dp))
        Text(
            "Putting your gaps first and fitting ${vm.profile.minutesPerDay} minutes a day until ${vm.deadlineLabel()}.",
            color = c.textSecondary, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.width(300.dp),
        )
        Spacer(Modifier.size(18.dp))
        Tag(vm.generatingDetail, c.info, c.infoTile)
        Spacer(Modifier.size(8.dp))
        Text("Runs fully on your phone. This can take up to a minute.", color = c.textSecondary, fontSize = 12.sp)
    }
}
