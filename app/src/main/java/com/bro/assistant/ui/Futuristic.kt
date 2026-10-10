package com.bro.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bro.assistant.ui.theme.BroColors
import kotlin.math.PI
import kotlin.math.sin

val NeonCyan = Color(0xFF00E5FF)
val NeonViolet = Color(0xFF9D6BFF)

/**
 * Animated sci-fi backdrop: drifting neon grid, floating particles, a slow scan line and
 * glowing HUD corner brackets. Drawn on one Canvas, so it stays smooth.
 */
@Composable
fun FuturisticBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "hud")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "drift")
    val scan by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "scan")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "pulse")

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF02040A), Color(0xFF07122A), Color(0xFF0A0620))))

            // drifting grid
            val cell = 46.dp.toPx()
            val grid = NeonCyan.copy(alpha = 0.06f)
            var x = -((drift * cell) % cell)
            while (x < w) { drawLine(grid, Offset(x, 0f), Offset(x, h), 1f); x += cell }
            var y = (drift * cell) % cell
            while (y < h) { drawLine(grid, Offset(0f, y), Offset(w, y), 1f); y += cell }

            // floating particles (fixed seeds, no allocation per frame beyond draw calls)
            for (i in 0 until 36) {
                val seed = i * 0.6180339f
                val px = ((seed * 7.3f) % 1f) * w
                val py = (((seed * 3.1f) % 1f) + (1f - drift) * (0.4f + (i % 5) * 0.15f)) % 1f * h
                val a = 0.25f + 0.35f * sin((drift * 2f * PI + i).toFloat()).let { it * it }
                val c = if (i % 3 == 0) NeonViolet else NeonCyan
                drawCircle(c.copy(alpha = a * 0.35f), 5f + (i % 3) * 2f, Offset(px, py))
                drawCircle(c.copy(alpha = a), 1.8f + (i % 2), Offset(px, py))
            }

            // scan line
            val sy = scan * h
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, NeonCyan.copy(alpha = 0.10f), Color.Transparent),
                    startY = sy - 70f, endY = sy + 70f
                ),
                topLeft = Offset(0f, sy - 70f), size = androidx.compose.ui.geometry.Size(w, 140f)
            )

            // HUD corner brackets
            val m = 10.dp.toPx()
            val l = 30.dp.toPx()
            val col = NeonCyan.copy(alpha = 0.45f + 0.35f * pulse)
            val sw = Stroke(width = 2.5f)
            fun corner(cx: Float, cy: Float, dx: Float, dy: Float) {
                drawLine(col, Offset(cx, cy), Offset(cx + dx * l, cy), sw.width)
                drawLine(col, Offset(cx, cy), Offset(cx, cy + dy * l), sw.width)
            }
            corner(m, m, 1f, 1f); corner(w - m, m, -1f, 1f)
            corner(m, h - m, 1f, -1f); corner(w - m, h - m, -1f, -1f)
        }
        content()
    }
}

/** Glow value (0..1) that breathes slowly; used for neon borders and the title. */
@Composable
fun breathing(periodMs: Int = 2200): Float {
    val t = rememberInfiniteTransition(label = "breath")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs), RepeatMode.Reverse), label = "b")
    return v
}

fun neonBrush(alpha: Float = 1f) =
    Brush.horizontalGradient(listOf(NeonCyan.copy(alpha = alpha), NeonViolet.copy(alpha = alpha)))
