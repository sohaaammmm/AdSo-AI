package com.example.executorchllamademo.adso.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorchllamademo.adso.AdsoViewModel
import com.example.executorchllamademo.adso.Screen
import com.example.executorchllamademo.adso.data.Content
import com.example.executorchllamademo.adso.data.SkillLevel
import com.example.executorchllamademo.adso.llm.LlamaEngine
import com.example.executorchllamademo.ui.theme.AdsoTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

// ---------------------------------------------------------------- Progress
@Composable
fun ProgressScreen(vm: AdsoViewModel) {
    val c = AdsoTheme.colors
    val totalMin = vm.week.sumOf { it.second }
    ScrollPage(Modifier.statusBarsPadding()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Progress"), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            Text(vm.course.title, color = c.textSecondary, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Modifier.weight(1f), Icons.Rounded.Schedule, "${totalMin / 60}h ${totalMin % 60}m", tr("Study time"), c.info, c.infoTile)
            StatTile(Modifier.weight(1f), Icons.Rounded.CheckCircle, "${vm.weekSessions}", tr("Sessions"), c.warning, c.warningTile)
            StatTile(Modifier.weight(1f), Icons.Rounded.TrackChanges, vm.accuracy?.let { "$it%" } ?: "–", tr("Quiz accuracy"), c.success, c.successTile)
        }
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Study time this week"), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text("Tap a day", color = c.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.size(8.dp))
            StudyChart(vm.week, vm.profile.minutesPerDay)
        }
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pathway", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text("${vm.pathwayDone} of ${vm.topics.size} done · ${vm.pathwayDone * 100 / vm.topics.size.coerceAtLeast(1)}%", color = c.info, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.size(10.dp))
            LevelBar(vm.pathwayDone / vm.topics.size.coerceAtLeast(1).toFloat(), c.info)
        }
        GlassCard {
            SectionTitle(tr("Your gaps"), action = "Open plan", onAction = { vm.planTabRoute = false; vm.go(Screen.PLAN) })
            val gaps = vm.topics.filter { it.id !in vm.completed && vm.levelOf(it.id) != SkillLevel.STRONG }
            if (gaps.isEmpty()) Text("No gaps left in ${vm.course.title}. Nice work.", color = c.textSecondary, fontSize = 14.sp)
            gaps.forEach { t ->
                val l = vm.levelOf(t.id)
                val (fg, bg) = skillColors(l)
                val source = when {
                    !vm.hasSkillCheck -> "Take the skill check to find your real gaps"
                    vm.baseLevels[t.id] != l -> "Changed after a quiz"
                    l == SkillLevel.NEW -> "You chose “Not sure yet” in the skill check"
                    else -> "Missed in the skill check"
                }
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(source, color = c.textSecondary, fontSize = 12.sp)
                    }
                    Tag(l.label, fg, bg)
                }
            }
        }
        GlassCard {
            Text(tr("Skill map"), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(c.textSecondary.copy(alpha = 0.45f), "At skill check"); Legend(c.info, "Now")
            }
            vm.topics.forEach { t ->
                val now = vm.levelOf(t.id)
                val before = vm.baseLevels[t.id] ?: now
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row {
                        Text(t.name, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${(now.fraction * 100).toInt()}% · ${now.label}", color = c.textSecondary, fontSize = 13.sp)
                    }
                    LevelBar(before.fraction, c.textSecondary.copy(alpha = 0.45f), height = 5.dp)
                    LevelBar(now.fraction, c.info, height = 5.dp)
                }
            }
        }
        if (vm.week.all { it.second == 0 }) {
            Text("No study logged this week yet. Finish a lesson or quiz and it shows up here.", color = c.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(12.dp).height(4.dp).clip(Pill).background(color))
        Text(label, color = AdsoTheme.colors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun StatTile(modifier: Modifier, icon: ImageVector, value: String, label: String, fg: Color, bg: Color) {
    GlassCard(modifier, padding = 12.dp) {
        IconTile(icon, fg, bg, size = 30.dp)
        Spacer(Modifier.size(8.dp))
        Text(value, color = AdsoTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AdsoTheme.colors.textSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

/** Minutes per day for the last 7 days, with the daily target as a dashed line. Tap a point. */
@Composable
private fun StudyChart(week: List<Pair<String, Int>>, target: Int) {
    val c = AdsoTheme.colors
    if (week.isEmpty()) return
    var sel by remember(week) { mutableIntStateOf(week.lastIndex) }
    val maxV = maxOf(60, week.maxOf { it.second }, target).let { ((it + 29) / 30) * 30 }
    Column {
        Box(
            Modifier.fillMaxWidth().height(170.dp)
                .semantics { contentDescription = week.joinToString { "${it.first} ${it.second} minutes" } }
                .pointerInput(week) {
                    detectTapGestures { pos ->
                        val left = 30.dp.toPx(); val right = size.width - 10.dp.toPx()
                        val step = (right - left) / (week.size - 1)
                        sel = week.indices.minBy { abs(left + it * step - pos.x) }
                    }
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val left = 30.dp.toPx(); val right = size.width - 10.dp.toPx()
                val top = 18.dp.toPx(); val bottom = size.height - 4.dp.toPx()
                val y = { v: Int -> bottom - (v / maxV.toFloat()) * (bottom - top) }
                val step = (right - left) / (week.size - 1)
                listOf(0, maxV / 2, maxV).forEach { g -> drawLine(c.cardOutline, Offset(left, y(g)), Offset(right, y(g)), 1.dp.toPx()) }
                drawLine(c.warning, Offset(left, y(target)), Offset(right, y(target)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                val pts = week.mapIndexed { i, (_, v) -> Offset(left + i * step, y(v)) }
                val line = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                val area = Path().apply { addPath(line); lineTo(pts.last().x, bottom); lineTo(pts.first().x, bottom); close() }
                drawPath(area, c.primary.copy(alpha = 0.18f))
                drawPath(line, c.info, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                pts.forEachIndexed { i, p ->
                    drawCircle(if (i == sel) c.info else c.background, if (i == sel) 6.dp.toPx() else 4.dp.toPx(), p)
                    drawCircle(c.info, if (i == sel) 6.dp.toPx() else 4.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
                }
            }
            Text("$maxV m", color = c.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
            Text("${week[sel].first} · ${week[sel].second} min", color = c.background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopEnd).clip(Pill).background(c.textPrimary).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(start = 18.dp)) {
            week.forEach { (d, _) -> Text(d, color = c.textSecondary, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(16.dp).height(2.dp).background(c.warning))
            Text("Daily target ($target min)", color = c.textSecondary, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- Settings
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AdsoViewModel, onAdvanced: () -> Unit, onLogs: () -> Unit) {
    val c = AdsoTheme.colors
    val p = vm.profile
    ScrollPage(Modifier.statusBarsPadding()) {
        Text(tr("Settings"), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        GlassCard(padding = 0.dp) {
            SettingsLink("Goals, interests and time") { vm.go(Screen.EDIT_GOALS) }
            Divider()
            SettingsLink("${tr("Courses")} · ${vm.course.title}") { vm.go(Screen.COURSES) }
            Divider()
            SettingsLink("Retake skill check") { vm.startSkillCheck() }
        }
        ModelCard(vm, onAdvanced)
        GlassCard {
            Text(tr("Appearance"), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip("Dark", p.darkTheme, { vm.updateProfile { copy(darkTheme = true) } }, Modifier.weight(1f))
                SelectChip("Light", !p.darkTheme, { vm.updateProfile { copy(darkTheme = false) } }, Modifier.weight(1f))
            }
            Spacer(Modifier.size(16.dp))
            Text(tr("Language"), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(
                "Hindi translates the whole app and Llama answers in Hindi. Other languages translate the main menus; AI answers stay in English.",
                color = c.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Content.languages.forEach { l -> SelectChip(l.native, p.language == l.code, { vm.updateProfile { copy(language = l.code) } }, sub = if (l.aiGood) "AI: good" else "Menus only") }
            }
        }
        GlassCard(padding = 0.dp) {
            SettingsLink("Logs", onLogs)
            Divider()
            SettingsLink("Add a sample study week (for demos)") { vm.addSampleWeek() }
        }
    }
}

/** The model on the phone: status, choice of model file, temperature, speed (numbers for the report). */
@Composable
fun ModelCard(vm: AdsoViewModel, onAdvanced: () -> Unit) {
    val c = AdsoTheme.colors
    val e = LlamaEngine
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val models = remember { e.availableModels() }
    var chosen by remember { mutableStateOf(e.pickModel(ctx)?.first) }
    GlassCard {
        Text(tr("Model"), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.size(10.dp))
        InfoRow("Status", when (e.status) {
            LlamaEngine.Status.READY -> "Loaded"
            LlamaEngine.Status.WORKING -> "Thinking…"
            LlamaEngine.Status.LOADING -> "Loading…"
            LlamaEngine.Status.MISSING -> "Model file not found"
            LlamaEngine.Status.FAILED -> "Failed to load"
            LlamaEngine.Status.NOT_LOADED -> "Not loaded yet"
        })
        if (models.isEmpty()) {
            Text("No model files in ${LlamaEngine.MODEL_DIR}. Copy a .pte model and tokenizer.model there.", color = c.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
        models.forEach { m ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(CardShape).clickable(role = Role.RadioButton) {
                    chosen = m.path; e.selectModel(ctx, m.path); scope.launch { e.ensureLoaded(ctx) }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = chosen == m.path, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.info, unselectedColor = c.textSecondary))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(m.label, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("%.2f GB · %s · INT4 SpinQuant".format(m.sizeGb, if (m.is3b) "≈ 3.21 B parameters" else "≈ 1.24 B parameters"), color = c.textSecondary, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Temperature", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("%.2f".format(e.temperature), color = c.info, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = e.temperature, onValueChange = { vm.setTemperature(it) }, valueRange = 0.7f..0.8f, steps = 9,
            colors = SliderDefaults.colors(thumbColor = c.info, activeTrackColor = c.info, inactiveTrackColor = c.cardRaised),
        )
        Text("Kept between 0.70 and 0.80, as the project requires. Changing it reloads the model.", color = c.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.size(10.dp))
        InfoRow("Runtime", "ExecuTorch · XNNPACK (CPU)")
        InfoRow("Load time", e.loadSeconds?.let { "%.1f s".format(it) } ?: "–")
        InfoRow("Last speed", e.lastTokensPerSecond?.let { "%.1f tokens/s".format(it) } ?: "–")
        e.lastError?.let { Text(it, color = c.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.size(12.dp))
        PrimaryButton(if (e.status == LlamaEngine.Status.READY) "Reload model" else "Load model now", {
            scope.launch { if (e.status == LlamaEngine.Status.READY) e.releaseNow(); e.ensureLoaded(ctx) }
        }, Modifier.fillMaxWidth(), enabled = e.status != LlamaEngine.Status.LOADING && e.status != LlamaEngine.Status.WORKING)
        Spacer(Modifier.size(8.dp))
        GhostButton("Advanced model settings", onAdvanced, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(AdsoTheme.colors.cardOutline))

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = AdsoTheme.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = AdsoTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsLink(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AdsoTheme.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = AdsoTheme.colors.textSecondary)
    }
}
