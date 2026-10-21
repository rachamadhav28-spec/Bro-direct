package com.bro.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.ui.theme.BroBrand
import com.bro.assistant.ui.theme.BroMotion
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * A short cinematic opening: a dark atmosphere, particles drawing in, the orb revealing, then the BRO wordmark.
 * It sits on top of the app while the app loads underneath, so it never delays startup. Tap to skip.
 * With reduced motion (animations off in the phone's settings) it only fades the finished frame in and out.
 */
@Composable
fun CinematicSplash(reduceMotion: Boolean, onFinished: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(if (reduceMotion) BroMotion.splashReduced else BroMotion.splash, easing = LinearEasing))
        onFinished()
    }
    val t = p.value
    fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    fun out(x: Float): Float { val u = 1f - x; return 1f - u * u * u }
    val orbIn = if (reduceMotion) 1f else out(seg(0.05f, 0.45f))
    val wordIn = if (reduceMotion) 1f else out(seg(0.38f, 0.72f))
    val particlesIn = out(seg(0.05f, 0.8f))
    val exit = seg(0.86f, 1f)

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = 1f - exit }
            .background(BroBrand.Midnight)
            .pointerInput(Unit) { detectTapGestures { onFinished() } }
    ) {
        val unitDp = minOf(maxWidth, maxHeight)
        val orbCenterY = maxHeight * 0.42f

        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.42f)
            val unit = minOf(size.width, size.height)
            drawRect(
                Brush.radialGradient(listOf(Color(0xFF2A1A66).copy(alpha = 0.85f), Color.Transparent), center = c, radius = size.maxDimension * 0.62f)
            )
            drawRect(
                Brush.radialGradient(
                    listOf(BroBrand.Cyan.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.95f), radius = size.maxDimension * 0.5f
                )
            )
            if (!reduceMotion) {
                for (i in 0 until 36) {
                    val f1 = i * 0.618f; val f2 = i * 0.37f; val f3 = i * 0.29f; val f4 = i * 0.71f
                    val start = unit * (0.55f + 0.45f * (f1 - floor(f1)))
                    val end = unit * (0.20f + 0.18f * (f2 - floor(f2)))
                    val rad = start + (end - start) * particlesIn
                    val a = i * 2.39996f + t * 0.9f
                    drawCircle(
                        color = (if (i % 3 == 0) BroBrand.Cyan else BroBrand.Lilac)
                            .copy(alpha = (0.15f + 0.5f * (f3 - floor(f3))) * seg(0.05f, 0.3f)),
                        radius = unit * (0.003f + 0.004f * (f4 - floor(f4))),
                        center = Offset(c.x + cos(a) * rad, c.y + sin(a) * rad)
                    )
                }
            }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = orbIn }) {
            val unit = minOf(size.width, size.height)
            drawGlassOrb(
                Offset(size.width / 2f, size.height * 0.42f),
                unit * 0.17f * (0.72f + 0.28f * orbIn),
                glow = orbIn
            )
        }
        Text(
            "BRO",
            color = BroBrand.Mist,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (16f - 10f * wordIn).sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = orbCenterY + unitDp * 0.17f + 40.dp)
                .graphicsLayer { alpha = wordIn; translationY = (1f - wordIn) * 12f }
        )
    }
}
