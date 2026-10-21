package com.bro.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.bro.assistant.ui.theme.BroBrand

/**
 * The BRO orb: a glass sphere with an inner light, a cyan rim light, a soft highlight and a thin orbit ring.
 * One drawing used by the logo, the voice orb and the splash, so they always match.
 *
 * [tint] colours the sphere, [rim] the edge light and ring, [glow] (0..1) how bright the inner light and halo are.
 */
fun DrawScope.drawGlassOrb(
    c: Offset,
    r: Float,
    tint: Color = BroBrand.Violet,
    rim: Color = BroBrand.Cyan,
    glow: Float = 0.6f,
    orbit: Boolean = true
) {
    val halo = minOf(r * 2.0f, size.minDimension / 2f)
    drawCircle(
        Brush.radialGradient(listOf(tint.copy(alpha = 0.10f + 0.28f * glow), Color.Transparent), center = c, radius = halo),
        radius = halo, center = c
    )

    val ringTopLeft = Offset(c.x - r * 1.35f, c.y - r * 0.38f)
    val ringSize = Size(r * 2.7f, r * 0.76f)
    val ringW = maxOf(1f, r * 0.035f)
    fun ring(alpha: Float) = Brush.linearGradient(
        listOf(rim.copy(alpha = alpha), tint.copy(alpha = alpha)),
        start = Offset(c.x - r * 1.35f, c.y), end = Offset(c.x + r * 1.35f, c.y)
    )
    // back half of the ring: drawn first, so the sphere covers it
    if (orbit) rotate(-22f, c) { drawOval(ring(0.45f), ringTopLeft, ringSize, style = Stroke(ringW)) }

    // sphere
    val hi = lerp(tint, Color.White, 0.55f)
    val deep = lerp(tint, BroBrand.Midnight, 0.8f)
    drawCircle(
        Brush.radialGradient(listOf(hi, tint, deep), center = Offset(c.x - r * 0.35f, c.y - r * 0.4f), radius = r * 1.6f),
        radius = r, center = c
    )
    // inner light
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f + 0.40f * glow), Color.Transparent), center = c, radius = r * 0.65f),
        radius = r * 0.65f, center = c
    )
    // rim light from the lower right
    val w = r * 0.07f
    drawCircle(
        Brush.sweepGradient(
            0f to rim.copy(alpha = 0.55f), 0.12f to rim.copy(alpha = 0.95f), 0.32f to Color.Transparent,
            0.85f to Color.Transparent, 1f to rim.copy(alpha = 0.55f), center = c
        ),
        radius = r - w / 2f, center = c, style = Stroke(w)
    )
    // glass edge
    drawCircle(Color.White.copy(alpha = 0.20f), radius = r - w / 4f, center = c, style = Stroke(maxOf(1f, r * 0.02f)))
    // highlight on the upper left
    drawArc(
        Color.White.copy(alpha = 0.55f), startAngle = 198f, sweepAngle = 52f, useCenter = false,
        topLeft = Offset(c.x - r * 0.78f, c.y - r * 0.78f), size = Size(r * 1.56f, r * 1.56f),
        style = Stroke(r * 0.07f, cap = StrokeCap.Round)
    )
    drawOval(Color.White.copy(alpha = 0.14f), Offset(c.x - r * 0.55f, c.y - r * 0.72f), Size(r * 0.8f, r * 0.34f))

    // front half of the ring, over the sphere
    if (orbit) rotate(-22f, c) { drawArc(ring(0.95f), 0f, 180f, false, ringTopLeft, ringSize, style = Stroke(ringW)) }
}

/** The BRO logo: the glass orb. Used in headers, the lock screen and the Motion lab. */
@Composable
fun BroLogo(size: Dp, animate: Boolean = true) {
    val t = rememberInfiniteTransition(label = "logo")
    val glow by t.animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    Canvas(Modifier.size(size)) {
        drawGlassOrb(center, this.size.minDimension * 0.30f, glow = if (animate) glow else 0.7f)
    }
}
