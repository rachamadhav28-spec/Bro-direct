package com.bro.assistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BroColors {
    val Background = Color(0xFF000000)
    val Panel = Color(0xFF111111)
    val Accent: Color get() = ThemeState.primary
    val UserBubble = Color(0xFF12303F)
    val BroBubble = Color(0xFF161616)
    val Listening = Color(0xFF3DDC84)
    val Thinking = Color(0xFFB388FF)
    val Executing = Color(0xFFFFA726)
    val Success = Color(0xFF66BB6A)
    val Error = Color(0xFFEF5350)

    // Text: both pass 4.5:1 contrast on the dark background
    val TextPrimary = Color(0xFFF2F5FA)
    val TextSecondary = Color(0xFF9AA7BD)
    val Outline = Color(0xFF2A2A2A)
    val Surface2 = Color(0xFF0A0A0A)
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
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BroColors.Accent,
            onPrimary = Color.Black,
            secondary = ThemeState.secondary,
            background = BroColors.Background,
            onBackground = BroColors.TextPrimary,
            surface = BroColors.Panel,
            onSurface = BroColors.TextPrimary,
            surfaceVariant = BroColors.Surface2,
            onSurfaceVariant = BroColors.TextSecondary,
            outline = BroColors.Outline,
            error = BroColors.Error
        ),
        typography = BroTypography,
        shapes = BroShapes,
        content = content
    )
    }
}
