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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.bro.assistant.BroState
import com.bro.assistant.ui.theme.BroColors

/** The animated BRO orb. Colour and motion change with the assistant state. */
@Composable
fun BroOrb(state: BroState, modifier: Modifier = Modifier) {
    val targetColor = when (state) {
        BroState.IDLE -> BroColors.Accent.copy(alpha = 0.7f)
        BroState.LISTENING -> BroColors.Listening
        BroState.THINKING -> BroColors.Thinking
        BroState.EXECUTING -> BroColors.Executing
        BroState.SPEAKING -> BroColors.Accent
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
        animationSpec = infiniteRepeatable(tween(duration * 2, easing = LinearEasing), RepeatMode.Restart)
    )

    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        val coreRadius = r * (0.45f + amplitude * pulse)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.10f)),
                center = c,
                radius = coreRadius * 1.6f
            ),
            radius = coreRadius * 1.6f,
            center = c
        )
        for (i in 1..3) {
            drawCircle(
                color = color.copy(alpha = 0.40f / i),
                radius = r * (0.55f + 0.14f * i) + r * amplitude * pulse * i,
                center = c,
                style = Stroke(width = 3f)
            )
        }
        if (state == BroState.THINKING || state == BroState.EXECUTING) {
            val arcRadius = r * 0.82f
            drawArc(
                color = color,
                startAngle = spin,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(c.x - arcRadius, c.y - arcRadius),
                size = Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )
        }
    }
}
