package com.bro.assistant.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/*
 * BRO design system, "Cinematic Glass".
 * One place for the brand colours, radii, borders, elevations, component sizes and motion timing.
 * Screens should read from here instead of writing numbers. Light, Dark and Auto themes are handled in BroColors.
 */

/** Fixed brand colours (the logo and splash never change with the user's accent). */
object BroBrand {
    val Midnight = Color(0xFF0A0715)
    val Plum = Color(0xFF1A1038)
    val Violet = Color(0xFF7C5CFF)
    val Lilac = Color(0xFFB9A4FF)
    val Cyan = Color(0xFF4FD8E8)
    val Mist = Color(0xFFF1EEFF)
}

object BroRadius {
    val xs = 6.dp
    val s = 10.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val pill = 100.dp
}

object BroBorder {
    val hairline = 1.dp
    val strong = 1.5.dp
}

object BroElevation {
    val none = 0.dp
    val low = 2.dp
    val mid = 6.dp
    val high = 14.dp
}

/** Minimum touch target is 48dp. */
object BroSize {
    val touch = 48.dp
    val iconButton = 44.dp
    val button = 48.dp
    val field = 56.dp
    val chip = 36.dp
    val logoHeader = 30.dp
    val orbHero = 168.dp
    val orbCall = 170.dp
    val floatingAssistant = 64.dp
}

/** Animation timing in milliseconds and the shared easing curves. */
object BroMotion {
    const val instant = 90
    const val fast = 160
    const val base = 240
    const val slow = 420
    const val cinematic = 900
    const val splash = 1700
    const val splashReduced = 450
    val easeOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val easeInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

/** Translucent glass surfaces: a soft fill with a light edge on top. Works on the midnight dark theme and on the light theme. */
object BroGlass {
    val shapeDefault: Shape = RoundedCornerShape(BroRadius.l)

    @Composable
    fun fill(strong: Boolean = false): Brush =
        if (ThemeState.dark) Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (strong) 0.12f else 0.085f), Color.White.copy(alpha = if (strong) 0.07f else 0.04f))
        ) else Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.86f), Color.White.copy(alpha = 0.66f))
        )

    @Composable
    fun edge(): Brush =
        if (ThemeState.dark) Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.12f))
        ) else Brush.verticalGradient(
            listOf(Color.White, Color(0x1F1B1730), Color(0x141B1730))
        )
}

/** A glass panel: clip, translucent fill and a hairline light edge. */
@Composable
fun Modifier.glass(shape: Shape = BroGlass.shapeDefault, strong: Boolean = false): Modifier =
    this.clip(shape).background(BroGlass.fill(strong), shape).border(BroBorder.hairline, BroGlass.edge(), shape)
