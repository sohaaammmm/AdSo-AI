package com.example.executorchllamademo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorchllamademo.AppearanceMode
import com.example.executorchllamademo.R

/** Theme colors beyond Material's ColorScheme (gradients, status tiles). */
@Immutable
data class AdsoColors(
    val background: Color,
    val backgroundGlow: Color,
    val card: Color,
    val cardRaised: Color,
    val cardOutline: Color,
    val primary: Color,
    val primaryGradient: List<Color>,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val success: Color,
    val successTile: Color,
    val error: Color,
    val errorTile: Color,
    val info: Color,
    val infoTile: Color,
    val warning: Color,
    val warningTile: Color,
    val isDark: Boolean,
)

// Light theme: the same blue look on a pale ground (optional, Settings > Appearance).
val AdsoLightColors = AdsoColors(
    background = Color(0xFFF2F4FA),
    backgroundGlow = Color(0x333D6BFF),
    card = Color(0xFFFFFFFF),
    cardRaised = Color(0xFFE7EBF5),
    cardOutline = Color(0xFFDCE1EE),
    primary = Color(0xFF2F5BEA),
    primaryGradient = listOf(Color(0xFF5C8DFF), Color(0xFF2F5BEA)),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF10131C),
    textSecondary = Color(0xFF525A6E),
    success = Color(0xFF12825A),
    successTile = Color(0xFFDDF4EA),
    error = Color(0xFFC2323A),
    errorTile = Color(0xFFFCE2E3),
    info = Color(0xFF2F5BEA),
    infoTile = Color(0xFFE3EAFD),
    warning = Color(0xFFA85F0C),
    warningTile = Color(0xFFFCEBD6),
    isDark = false,
)

// Dark theme (default): near-black with a blue glow, glass cards, electric blue. Matches the mock-up.
val AdsoDarkColors = AdsoColors(
    background = Color(0xFF07090F),
    backgroundGlow = Color(0x8C2F5BEA),
    card = Color(0x0BFFFFFF),
    cardRaised = Color(0x17FFFFFF),
    cardOutline = Color(0x1AFFFFFF),
    primary = Color(0xFF3D6BFF),
    primaryGradient = listOf(Color(0xFF6A94FF), Color(0xFF3D6BFF)),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFF3F5FA),
    textSecondary = Color(0xFF9EA4B3),
    success = Color(0xFF3DDC97),
    successTile = Color(0x1F3DDC97),
    error = Color(0xFFFF7A7F),
    errorTile = Color(0x1FFF7A7F),
    info = Color(0xFF6A94FF),
    infoTile = Color(0x293D6BFF),
    warning = Color(0xFFFFB255),
    warningTile = Color(0x1FFFB255),
    isDark = true,
)

val LocalAdsoColors = staticCompositionLocalOf { AdsoLightColors }

/** Spacing on a 4dp grid. */
object AdsoSpacing {
    val gutter = 20.dp
    val cardGap = 12.dp
    val section = 28.dp
    val cardPadding = 16.dp
}

/** Shapes not covered by Material's Shapes. */
object AdsoShapes {
    val pill = RoundedCornerShape(percent = 50)
    val card = RoundedCornerShape(24.dp)
    val tile = RoundedCornerShape(16.dp)
}

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

// Display 28/34, Headline 22/28, Title 17/22, Body 15/22, Label 13/18, Caption 12/16.
val AdsoTypography = Typography(
    displayLarge = style(34, 40, FontWeight.SemiBold),
    displayMedium = style(30, 36, FontWeight.SemiBold),
    displaySmall = style(28, 34, FontWeight.SemiBold),
    headlineLarge = style(26, 32, FontWeight.SemiBold),
    headlineMedium = style(24, 30, FontWeight.SemiBold),
    headlineSmall = style(22, 28, FontWeight.SemiBold),
    titleLarge = style(20, 26, FontWeight.SemiBold),
    titleMedium = style(17, 22, FontWeight.Medium),
    titleSmall = style(15, 20, FontWeight.Medium),
    bodyLarge = style(15, 22, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(13, 18, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium),
)

val AdsoMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun AdsoColors.toMaterial() = if (isDark) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = cardRaised, onPrimaryContainer = textPrimary,
        secondary = info, onSecondary = onPrimary,
        background = background, onBackground = textPrimary,
        surface = card, onSurface = textPrimary,
        surfaceVariant = cardRaised, onSurfaceVariant = textSecondary,
        outline = cardOutline, error = error, onError = onPrimary,
    )
} else {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = cardRaised, onPrimaryContainer = textPrimary,
        secondary = info, onSecondary = onPrimary,
        background = background, onBackground = textPrimary,
        surface = card, onSurface = textPrimary,
        surfaceVariant = cardRaised, onSurfaceVariant = textSecondary,
        outline = cardOutline, error = error, onError = onPrimary,
    )
}

@Composable
fun AdsoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) AdsoDarkColors else AdsoLightColors
    CompositionLocalProvider(LocalAdsoColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = AdsoTypography,
            shapes = AdsoMaterialShapes,
            content = content,
        )
    }
}

/** Resolves the saved appearance setting to dark/light. */
@Composable
fun AdsoTheme(
    appearanceMode: AppearanceMode,
    content: @Composable () -> Unit,
) {
    val dark = when (appearanceMode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    AdsoTheme(darkTheme = dark, content = content)
}

/** Shortcut: `AdsoTheme.colors.primary` */
object AdsoTheme {
    val colors: AdsoColors
        @Composable get() = LocalAdsoColors.current
}
