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

    val Background: Color get() = pick(0xFF000000, 0xFFFAF9F5)
    val Panel: Color get() = pick(0xFF151517, 0xFFFFFFFF)
    /** The colour of buttons, links and highlights. Darkened on the light theme so text in it stays readable. */
    val Accent: Color get() = if (dark) ThemeState.primary else ThemeState.readableOnLight(ThemeState.primary)
    val AccentSoft: Color get() = Accent.copy(alpha = if (dark) 0.16f else 0.12f)
    val UserBubble: Color get() = pick(0xFF26262B, 0xFFEFECE3)
    val BroBubble: Color get() = pick(0xFF0E0E10, 0xFFFAF9F5)
    val Listening = Color(0xFF3DDC84)
    val Thinking: Color get() = pick(0xFFB388FF, 0xFF7C4DFF)
    val Executing: Color get() = pick(0xFFFFA726, 0xFFE65100)
    val Success: Color get() = pick(0xFF66BB6A, 0xFF2E7D32)
    val Error: Color get() = pick(0xFFEF5350, 0xFFC62828)

    // Text passes 4.5:1 contrast on its background in both themes
    val TextPrimary: Color get() = pick(0xFFF2F5FA, 0xFF1F1E1D)
    val TextSecondary: Color get() = pick(0xFF9AA7BD, 0xFF65635C)
    val Outline: Color get() = pick(0xFF2A2A2D, 0xFFE3E0D6)
    val Surface2: Color get() = pick(0xFF0C0C0E, 0xFFF3F1EA)
    /** Code blocks are dark in both themes (like a terminal). */
    val CodeBg = Color(0xFF0F1115)
    val CodeText = Color(0xFFE6EDF3)
}

/** One spacing scale (8dp grid) used everywhere. */
object Space { val xs = 4.dp; val s = 8.dp; val m = 16.dp; val l = 24.dp; val xl = 32.dp }

private val BroTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)

private val BroShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
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
