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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.bro.assistant.BroState
import com.bro.assistant.ui.theme.BroBrand
import com.bro.assistant.ui.theme.BroColors
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The voice orb: the BRO glass orb with a thin ring of particles around it.
 * The sphere colour, pulse and particle speed follow what the assistant is doing.
 */
@Composable
fun BroOrb(state: BroState, modifier: Modifier = Modifier) {
    val target = when (state) {
        BroState.IDLE -> BroBrand.Violet
        BroState.LISTENING -> BroColors.Listening
        BroState.THINKING -> BroBrand.Lilac
        BroState.EXECUTING -> BroColors.Executing
        BroState.SPEAKING -> lerp(BroBrand.Violet, BroBrand.Cyan, 0.35f)
        BroState.SUCCESS -> BroColors.Success
        BroState.ERROR -> BroColors.Error
    }
    val tint by animateColorAsState(target, tween(400), label = "orbTint")
    val rim = if (state == BroState.ERROR) BroColors.Error else BroBrand.Cyan

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

    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(duration, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin by transition.animateFloat(
        0f, 6.2831855f,
        infiniteRepeatable(tween(duration * 8, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )

    Canvas(modifier) {
        val c = center
        val unit = size.minDimension
        val r = unit * 0.27f * (1f + amplitude * 0.9f * pulse)

        // particles on a loose ring around the orb
        for (i in 0 until 40) {
            val f = i * 0.618f
            val ring = unit * (0.40f + 0.09f * (f - floor(f)))
            val a = i * 2.39996f + spin * (if (i % 2 == 0) 1f else -0.6f)
            val twinkle = 0.5f + 0.5f * sin(pulse * 6.2831855f + i)
            drawCircle(
                color = (if (i % 3 == 0) rim else BroBrand.Lilac).copy(alpha = 0.18f + 0.5f * twinkle),
                radius = unit * (0.005f + 0.006f * (i % 4) / 3f),
                center = Offset(c.x + cos(a) * ring, c.y + sin(a) * ring)
            )
        }
        drawGlassOrb(c, r, tint = tint, rim = rim, glow = 0.4f + 0.6f * pulse)
    }
}
