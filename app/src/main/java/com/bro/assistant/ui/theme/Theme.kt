package com.bro.assistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BroColors {
    private val dark: Boolean get() = ThemeState.dark
    private fun pick(d: Long, l: Long) = Color(if (dark) d else l)

    val Background: Color get() = pick(0xFF0A0715, 0xFFF8F6FD)
    val Panel: Color get() = pick(0xFF150F28, 0xFFFFFFFF)
    /** The colour of buttons, links and highlights. Darkened on the light theme so text in it stays readable. */
    val Accent: Color get() = if (dark) ThemeState.primary else ThemeState.readableOnLight(ThemeState.primary)
    val AccentSoft: Color get() = Accent.copy(alpha = if (dark) 0.16f else 0.12f)
    val UserBubble: Color get() = pick(0xFF271C4D, 0xFFEBE6F8)
    val BroBubble: Color get() = pick(0xFF110B22, 0xFFF8F6FD)
    val Listening = Color(0xFF3DDC84)
    val Thinking: Color get() = pick(0xFFB9A4FF, 0xFF6A45E0)
    val Executing: Color get() = pick(0xFFFFA726, 0xFFE65100)
    val Success: Color get() = pick(0xFF66BB6A, 0xFF2E7D32)
    val Error: Color get() = pick(0xFFEF5350, 0xFFC62828)

    // Text passes 4.5:1 contrast on its background in both themes
    val TextPrimary: Color get() = pick(0xFFF1EEFF, 0xFF1B1730)
    val TextSecondary: Color get() = pick(0xFFA8A0CC, 0xFF5E5878)
    val Outline: Color get() = pick(0xFF2B2250, 0xFFDDD6EE)
    val Surface2: Color get() = pick(0xFF110B22, 0xFFF1EEF9)
    /** Code blocks are dark in both themes (like a terminal). */
    val CodeBg = Color(0xFF0F1115)
    val CodeText = Color(0xFFE6EDF3)
}

/** One spacing scale (8dp grid) used everywhere. */
object Space { val xxs = 2.dp; val xs = 4.dp; val s = 8.dp; val m = 16.dp; val l = 24.dp; val xl = 32.dp; val xxl = 48.dp }

/** Type scale. Sizes are in sp, so they follow the phone's font size setting. */
private val BroTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
)

private val BroShapes = Shapes(
    extraSmall = RoundedCornerShape(BroRadius.xs),
    small = RoundedCornerShape(BroRadius.s),
    medium = RoundedCornerShape(BroRadius.m),
    large = RoundedCornerShape(BroRadius.l),
    extraLarge = RoundedCornerShape(BroRadius.xl)
)

@Composable
fun BroTheme(content: @Composable () -> Unit) {
    val base = androidx.compose.ui.platform.LocalDensity.current
    val z = ThemeState.zoom
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * z, base.fontScale * z)
    ) {
    ThemeState.systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = ThemeState.dark
    val accent = BroColors.Accent
    val scheme = if (isDark) darkColorScheme(
        primary = accent, onPrimary = ThemeState.on(accent), secondary = ThemeState.secondary,
        background = BroColors.Background, onBackground = BroColors.TextPrimary,
        surface = BroColors.Panel, onSurface = BroColors.TextPrimary,
        surfaceVariant = BroColors.Surface2, onSurfaceVariant = BroColors.TextSecondary,
        outline = BroColors.Outline, error = BroColors.Error
    ) else lightColorScheme(
        primary = accent, onPrimary = ThemeState.on(accent), secondary = ThemeState.readableOnLight(ThemeState.secondary),
        background = BroColors.Background, onBackground = BroColors.TextPrimary,
        surface = BroColors.Panel, onSurface = BroColors.TextPrimary,
        surfaceVariant = BroColors.Surface2, onSurfaceVariant = BroColors.TextSecondary,
        outline = BroColors.Outline, error = BroColors.Error
    )
    // dark icons in the status bar on the light theme
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let {
            val c = androidx.core.view.WindowCompat.getInsetsController(it, view)
            c.isAppearanceLightStatusBars = !isDark
            c.isAppearanceLightNavigationBars = !isDark
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = BroTypography,
        shapes = BroShapes,
        content = content
    )
    }
}
