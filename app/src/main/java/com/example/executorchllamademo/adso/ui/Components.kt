package com.example.executorchllamademo.adso.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorchllamademo.ui.components.EnergyOrb
import com.example.executorchllamademo.ui.components.OrbState
import com.example.executorchllamademo.ui.theme.AdsoTheme

val Pill = RoundedCornerShape(percent = 50)
val CardShape = RoundedCornerShape(24.dp)

/** Near-black ground with a soft blue glow from the top, like the mock-up. */
@Composable
fun AdsoBackground(content: @Composable BoxScope.() -> Unit) {
    val c = AdsoTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .background(Brush.radialGradient(listOf(c.backgroundGlow, Color.Transparent), radius = 1400f, center = androidx.compose.ui.geometry.Offset(540f, -200f))),
        content = content,
    )
}

/** Frosted glass card: faint fill and a thin light outline. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    outline: Color = AdsoTheme.colors.cardOutline,
    fill: Color = AdsoTheme.colors.card,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(fill)
            .border(BorderStroke(1.dp, outline), CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Main action: a blue gradient pill. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: ImageVector? = null) {
    val c = AdsoTheme.colors
    val brush = if (enabled) Brush.verticalGradient(c.primaryGradient) else Brush.verticalGradient(listOf(c.cardRaised, c.cardRaised))
    Row(
        modifier
            .heightIn(min = 54.dp)
            .clip(Pill)
            .background(brush)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = if (enabled) Color.White else c.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (enabled) Color.White else c.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Secondary action: glass pill with an outline. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    val c = AdsoTheme.colors
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(Pill)
            .background(c.card)
            .border(1.dp, c.cardOutline, Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Round icon button with a glass fill. */
@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, size: Dp = 44.dp) {
    val c = AdsoTheme.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) c.primary else c.card)
            .then(if (filled) Modifier else Modifier.border(1.dp, c.cardOutline, CircleShape))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.White else c.textPrimary, modifier = Modifier.size(size * 0.45f))
    }
}

/** Selectable chip. Selected = blue outline, tinted fill and a soft ring. */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    shapeRadius: Dp? = null,
) {
    val c = AdsoTheme.colors
    val shape = if (shapeRadius != null) RoundedCornerShape(shapeRadius) else Pill
    Column(
        modifier
            .heightIn(min = 44.dp)
            .then(if (selected) Modifier.border(3.dp, c.info.copy(alpha = 0.25f), shape).padding(3.dp) else Modifier.padding(3.dp))
            .clip(shape)
            .background(if (selected) c.infoTile else c.card)
            .border(1.dp, if (selected) c.info else c.cardOutline, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = if (sub != null) 10.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (sub != null) Text(sub, color = c.textSecondary, fontSize = 12.sp)
    }
}

/** Small rounded label, e.g. "Needs work". */
@Composable
fun Tag(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(Pill).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun Eyebrow(text: String) {
    Text(text.uppercase(), color = AdsoTheme.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = AdsoTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                color = AdsoTheme.colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.clip(Pill).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 6.dp, vertical = 12.dp),
            )
        }
    }
}

/** Thin progress bar. */
@Composable
fun LevelBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp, track: Color = AdsoTheme.colors.cardRaised) {
    Box(modifier.fillMaxWidth().height(height).clip(Pill).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(Pill).background(color))
    }
}

/** Onboarding header: back, step bars, "2/3". */
@Composable
fun StepHeader(step: Int, total: Int, onBack: () -> Unit) {
    val c = AdsoTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Row(Modifier.weight(1f).semantics { contentDescription = "Step $step of $total" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(Pill).background(if (i < step) c.info else c.cardRaised))
            }
        }
        Text("$step/$total", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Plain screen header with a back button and a centred title. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = { Spacer(Modifier.size(44.dp)) }) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Text(title, style = MaterialTheme.typography.titleMedium, color = AdsoTheme.colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        trailing()
    }
}

/**
 * AdSo's 3D orb at a given visual size. The orb's canvas includes its glow, so it is drawn
 * larger than [size] and allowed to spill over its neighbours.
 */
@Composable
fun Orb(state: OrbState, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        EnergyOrb(state = state, modifier = Modifier.requiredSize(size * 1.62f))
    }
}

/** Icon in a soft coloured rounded square. */
@Composable
fun IconTile(icon: ImageVector, fg: Color, bg: Color, size: Dp = 42.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    PLAN("Plan", Icons.Rounded.CalendarMonth),
    PROGRESS("Progress", Icons.Rounded.BarChart),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

@Composable
fun BottomTabs(current: Tab, onSelect: (Tab) -> Unit) {
    val c = AdsoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (c.isDark) Color(0xF00A0C12) else Color(0xF2FFFFFF))
            .border(BorderStroke(1.dp, c.cardOutline))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == current
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .semantics { selected = on },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, contentDescription = null, tint = if (on) c.info else c.textSecondary, modifier = Modifier.size(24.dp))
                Text(tr(tab.label), color = if (on) c.info else c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}
