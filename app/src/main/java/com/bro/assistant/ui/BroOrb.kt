package com.bro.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as dsRotate
import com.bro.assistant.BroState
import com.bro.assistant.BuildConfig
import com.bro.assistant.ui.theme.BroColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The animated BRO orb: a globe made of dots that turns and breathes.
 * Colour and speed change with the assistant state.
 */
@Composable
fun BroOrb(state: BroState, modifier: Modifier = Modifier) {
    // Every new build gets its own idle colour, so you can see an update landed.
    // Nova violet; the build number nudges the hue a little so you can still see an update landed.
    val accent = remember { Color.hsv(255f + (BuildConfig.BUILD_NUMBER * 7) % 50, 0.55f, 1f) }
    val targetColor = when (state) {
        BroState.IDLE -> accent.copy(alpha = 0.85f)
        BroState.LISTENING -> BroColors.Listening
        BroState.THINKING -> BroColors.Thinking
        BroState.EXECUTING -> BroColors.Executing
        BroState.SPEAKING -> accent
        BroState.SUCCESS -> BroColors.Success
        BroState.ERROR -> BroColors.Error
    }
    val color by animateColorAsState(targetColor, tween(400))

    val duration = when (state) {
        BroState.IDLE -> 3200
        BroState.LISTENING -> 900
        BroState.THINKING -> 1400
        BroState.EXECUTING -> 700
        BroState.SPEAKING -> 600
        BroState.SUCCESS -> 1200
        BroState.ERROR -> 300
    }
    val amplitude = when (state) {
        BroState.IDLE -> 0.04f
        BroState.LISTENING -> 0.14f
        BroState.SPEAKING -> 0.12f
        BroState.ERROR -> 0.10f
        else -> 0.08f
    }

    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(duration * 6, easing = LinearEasing), RepeatMode.Restart)
    )

    // Evenly spread points on a sphere (Fibonacci spiral).
    val points = remember {
        val n = 150
        List(n) { i ->
            val y = 1f - i / (n - 1f) * 2f
            val ring = sqrt(1f - y * y)
            val angle = i * 2.399963f
            floatArrayOf(cos(angle) * ring, y, sin(angle) * ring)
        }
    }

    Canvas(modifier) {
        val c = center
        val unit = size.minDimension
        val r = unit / 2f * 0.78f
        val rot = Math.toRadians(spin.toDouble()).toFloat()
        val cs = cos(rot)
        val sn = sin(rot)

        // soft glow behind the globe
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.22f), Color.Transparent),
                center = c,
                radius = r * 1.3f
            ),
            radius = r * 1.3f,
            center = c
        )

        // turn the globe, let each dot breathe a little, draw far dots first
        val placed = points.mapIndexed { i, p ->
            val x = p[0] * cs + p[2] * sn
            val z = -p[0] * sn + p[2] * cs
            val k = 1f + amplitude * 0.9f * sin(pulse * 6.2832f + i * 0.5f)
            floatArrayOf(x * k, p[1] * k, z * k)
        }.sortedBy { it[2] }

        for (q in placed) {
            val depth = ((q[2] + 1f) / 2f).coerceIn(0f, 1f)
            drawCircle(
                color = lerp(color, Color.White, depth * 0.35f).copy(alpha = 0.25f + 0.75f * depth),
                radius = unit * (0.006f + 0.011f * depth),
                center = Offset(c.x + q[0] * r, c.y + q[1] * r)
            )
        }

        // Nova: a thin ring with a small moon, and the four-point star in the middle
        val ringR = r * 1.12f
        drawCircle(color.copy(alpha = 0.28f), radius = ringR, center = c, style = Stroke(width = unit * 0.004f))
        dsRotate(spin * 3f, c) {
            drawCircle(Color.White.copy(alpha = 0.9f), radius = unit * 0.014f, center = Offset(c.x, c.y - ringR))
        }
        val u = (r * 0.30f / 22.04f) * (1f + amplitude * 0.8f * sin(pulse * 3.1416f))
        fun pt(x: Float, y: Float) = Offset(c.x + x * u, c.y + y * u)
        val star = Path().apply {
            moveTo(pt(0f, -22.04f).x, pt(0f, -22.04f).y)
            cubicTo(pt(1.16f, -8.12f).x, pt(1.16f, -8.12f).y, pt(8.12f, -1.16f).x, pt(8.12f, -1.16f).y, pt(22.04f, 0f).x, pt(22.04f, 0f).y)
            cubicTo(pt(8.12f, 1.16f).x, pt(8.12f, 1.16f).y, pt(1.16f, 8.12f).x, pt(1.16f, 8.12f).y, pt(0f, 22.04f).x, pt(0f, 22.04f).y)
            cubicTo(pt(-1.16f, 8.12f).x, pt(-1.16f, 8.12f).y, pt(-8.12f, 1.16f).x, pt(-8.12f, 1.16f).y, pt(-22.04f, 0f).x, pt(-22.04f, 0f).y)
            cubicTo(pt(-8.12f, -1.16f).x, pt(-8.12f, -1.16f).y, pt(-1.16f, -8.12f).x, pt(-1.16f, -8.12f).y, pt(0f, -22.04f).x, pt(0f, -22.04f).y)
            close()
        }
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center = c, radius = r * 0.5f),
            radius = r * 0.5f, center = c
        )
        drawPath(star, Brush.linearGradient(listOf(Color.White, lerp(color, Color.White, 0.55f)), start = pt(-22f, -22f), end = pt(22f, 22f)))
    }
}
