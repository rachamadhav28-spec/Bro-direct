package com.bro.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Entrances play once and end at rest. Emphasis plays once and returns to rest. Loops repeat for as long as they are on screen. */
enum class MotionKind(val label: String) { ENTRANCE("Entrances"), EMPHASIS("Emphasis"), LOOP("Loops") }

/** How a view looks at one moment. Offsets are fractions of the view's own size; angles are degrees. */
data class Pose(
    val alpha: Float = 1f, val sx: Float = 1f, val sy: Float = 1f,
    val tx: Float = 0f, val ty: Float = 0f,
    val rz: Float = 0f, val rx: Float = 0f, val ry: Float = 0f
)

/** One named animation: [pose] maps progress 0..1 to a [Pose]. */
class Fx(val name: String, val kind: MotionKind, val ms: Int, val pose: (Float) -> Pose)

/** The BRO motion kit: every animation in one list, used by the Motion lab and by the screens. */
object Motion {
    private const val PI_F = 3.1415927f
    private const val TAU = 6.2831855f

    private fun out(t: Float): Float { val u = 1f - t; return 1f - u * u * u }
    private fun back(t: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val x = t - 1f; return 1f + c3 * x * x * x + c1 * x * x }
    private fun elastic(t: Float): Float =
        if (t <= 0f) 0f else if (t >= 1f) 1f
        else (2.0.pow(-10.0 * t) * sin((t * 10.0 - 0.75) * (2.0 * Math.PI / 3.0)) + 1.0).toFloat()
    private fun bounce(t: Float): Float {
        var x = t; val n = 7.5625f; val d = 2.75f
        return if (x < 1f / d) n * x * x
        else if (x < 2f / d) { x -= 1.5f / d; n * x * x + 0.75f }
        else if (x < 2.5f / d) { x -= 2.25f / d; n * x * x + 0.9375f }
        else { x -= 2.625f / d; n * x * x + 0.984375f }
    }
    private fun inOut(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
    private fun fade(t: Float) = min(1f, t * 4f)
    private fun s(x: Float) = sin(x * TAU)
    private fun c(x: Float) = cos(x * TAU)
    private fun hump(t: Float) = sin(t * PI_F)
    private fun decay(t: Float, waves: Float) = sin(t * waves * TAU) * (1f - t)

    private fun ent(n: String, ms: Int = 560, f: (Float) -> Pose) = Fx(n, MotionKind.ENTRANCE, ms, f)
    private fun emp(n: String, ms: Int = 800, f: (Float) -> Pose) = Fx(n, MotionKind.EMPHASIS, ms, f)
    private fun lp(n: String, ms: Int = 2400, f: (Float) -> Pose) = Fx(n, MotionKind.LOOP, ms, f)

    val all: List<Fx> = listOf(
        // ── entrances ──
        ent("Fade In") { t -> Pose(alpha = out(t)) },
        ent("Fade from Bottom") { t -> val e = out(t); Pose(alpha = e, ty = (1f - e) * 0.35f) },
        ent("Fade from Top") { t -> val e = out(t); Pose(alpha = e, ty = -(1f - e) * 0.35f) },
        ent("Fade from Left") { t -> val e = out(t); Pose(alpha = e, tx = -(1f - e) * 0.35f) },
        ent("Fade from Right") { t -> val e = out(t); Pose(alpha = e, tx = (1f - e) * 0.35f) },
        ent("Slide from Bottom") { t -> Pose(alpha = fade(t), ty = (1f - out(t)) * 1.2f) },
        ent("Slide from Top") { t -> Pose(alpha = fade(t), ty = -(1f - out(t)) * 1.2f) },
        ent("Slide from Left") { t -> Pose(alpha = fade(t), tx = -(1f - out(t)) * 1.2f) },
        ent("Slide from Right") { t -> Pose(alpha = fade(t), tx = (1f - out(t)) * 1.2f) },
        ent("Zoom In") { t -> val e = out(t); val k = 0.3f + 0.7f * e; Pose(alpha = e, sx = k, sy = k) },
        ent("Zoom Out") { t -> val e = out(t); val k = 1.7f - 0.7f * e; Pose(alpha = e, sx = k, sy = k) },
        ent("Zoom from Bottom") { t -> val e = out(t); val k = 0.5f + 0.5f * e; Pose(alpha = e, sx = k, sy = k, ty = (1f - e) * 0.6f) },
        ent("Zoom from Top") { t -> val e = out(t); val k = 0.5f + 0.5f * e; Pose(alpha = e, sx = k, sy = k, ty = -(1f - e) * 0.6f) },
        ent("Zoom from Left") { t -> val e = out(t); val k = 0.5f + 0.5f * e; Pose(alpha = e, sx = k, sy = k, tx = -(1f - e) * 0.6f) },
        ent("Zoom from Right") { t -> val e = out(t); val k = 0.5f + 0.5f * e; Pose(alpha = e, sx = k, sy = k, tx = (1f - e) * 0.6f) },
        ent("Pop") { t -> val k = back(t); Pose(alpha = fade(t), sx = k, sy = k) },
        ent("Pop Soft") { t -> val e = out(t); val k = 0.8f + 0.2f * e; Pose(alpha = e, sx = k, sy = k) },
        ent("Elastic In", 900) { t -> val k = elastic(t); Pose(alpha = fade(t), sx = k, sy = k) },
        ent("Rubber In", 900) { t -> Pose(alpha = fade(t), sx = elastic(t), sy = elastic(min(1f, t * 1.15f))) },
        ent("Bounce In", 800) { t -> val k = bounce(t); Pose(alpha = fade(t), sx = k, sy = k) },
        ent("Bounce Down", 800) { t -> Pose(alpha = fade(t), ty = -(1f - bounce(t)) * 1.3f) },
        ent("Bounce Up", 800) { t -> Pose(alpha = fade(t), ty = (1f - bounce(t)) * 1.3f) },
        ent("Bounce from Left", 800) { t -> Pose(alpha = fade(t), tx = -(1f - bounce(t)) * 1.3f) },
        ent("Bounce from Right", 800) { t -> Pose(alpha = fade(t), tx = (1f - bounce(t)) * 1.3f) },
        ent("Flip X") { t -> Pose(alpha = fade(t), rx = (1f - out(t)) * 90f) },
        ent("Flip Y") { t -> Pose(alpha = fade(t), ry = (1f - out(t)) * 90f) },
        ent("Flip X Back") { t -> Pose(alpha = fade(t), rx = -(1f - out(t)) * 90f) },
        ent("Flip Y Back") { t -> Pose(alpha = fade(t), ry = -(1f - out(t)) * 90f) },
        ent("Rotate In") { t -> val e = out(t); val k = 0.3f + 0.7f * e; Pose(alpha = e, sx = k, sy = k, rz = -(1f - e) * 200f) },
        ent("Rotate from Left") { t -> val e = out(t); Pose(alpha = e, tx = -(1f - e) * 0.4f, rz = -(1f - e) * 90f) },
        ent("Rotate from Right") { t -> val e = out(t); Pose(alpha = e, tx = (1f - e) * 0.4f, rz = (1f - e) * 90f) },
        ent("Spin Zoom", 700) { t -> val e = out(t); Pose(alpha = e, sx = e, sy = e, rz = (1f - e) * 360f) },
        ent("Spin Drop", 800) { t -> Pose(alpha = fade(t), ty = -(1f - bounce(t)) * 1.2f, rz = (1f - out(t)) * 180f) },
        ent("Stretch X") { t -> Pose(alpha = fade(t), sx = out(t)) },
        ent("Stretch Y") { t -> Pose(alpha = fade(t), sy = out(t)) },
        ent("Squash In") { t -> val e = out(t); Pose(alpha = fade(t), sx = 1.5f - 0.5f * e, sy = 0.5f + 0.5f * e) },
        ent("Unfold", 700) { t -> Pose(alpha = fade(t), sy = max(0f, back(t))) },
        ent("Swing In", 900) { t -> Pose(alpha = fade(t), rz = 35f * (1f - t) * cos(t * 3f * TAU)) },
        ent("Drop In", 700) { t -> Pose(alpha = fade(t), ty = -(1f - t * t) * 1.6f) },
        ent("Rise Up", 900) { t -> val e = out(t); val k = 0.9f + 0.1f * e; Pose(alpha = e, sx = k, sy = k, ty = (1f - e) * 0.6f) },
        ent("Whip", 380) { t -> val e = out(t); Pose(alpha = fade(t), tx = -(1f - e) * 1.2f, rz = -(1f - e) * 18f) },
        ent("Settle", 600) { t -> val e = out(t); val k = 1.25f - 0.25f * e; Pose(alpha = e, sx = k, sy = k, rz = (1f - e) * 6f) },
        ent("Glide Up-Left") { t -> val e = out(t); Pose(alpha = e, tx = -(1f - e) * 0.5f, ty = -(1f - e) * 0.5f) },
        ent("Glide Up-Right") { t -> val e = out(t); Pose(alpha = e, tx = (1f - e) * 0.5f, ty = -(1f - e) * 0.5f) },
        ent("Glide Down-Left") { t -> val e = out(t); Pose(alpha = e, tx = -(1f - e) * 0.5f, ty = (1f - e) * 0.5f) },
        ent("Glide Down-Right") { t -> val e = out(t); Pose(alpha = e, tx = (1f - e) * 0.5f, ty = (1f - e) * 0.5f) },

        // ── emphasis (one shot, back to rest) ──
        emp("Pulse") { t -> val k = 1f + 0.14f * hump(t); Pose(sx = k, sy = k) },
        emp("Heartbeat", 900) { t -> val k = 1f + 0.2f * abs(sin(t * 2f * PI_F)) * (1f - t); Pose(sx = k, sy = k) },
        emp("Rubber Band", 900) { t -> val d = decay(t, 3f); Pose(sx = 1f + 0.3f * d, sy = 1f - 0.2f * d) },
        emp("Jelly", 900) { t -> val d = decay(t, 3.5f); Pose(sx = 1f - 0.25f * d, sy = 1f + 0.25f * d, rz = 3f * d) },
        emp("Tada", 900) { t -> val k = 1f + 0.12f * hump(t); Pose(sx = k, sy = k, rz = 6f * decay(t, 5f)) },
        emp("Shake X", 600) { t -> Pose(tx = 0.08f * decay(t, 5f)) },
        emp("Shake Y", 600) { t -> Pose(ty = 0.08f * decay(t, 5f)) },
        emp("Wobble", 900) { t -> val d = decay(t, 3f); Pose(tx = 0.15f * d, rz = 8f * d) },
        emp("Swing", 1000) { t -> Pose(rz = 16f * decay(t, 4f)) },
        emp("Flash") { t -> Pose(alpha = 0.5f + 0.5f * cos(t * 2f * TAU)) },
        emp("Blink", 500) { t -> Pose(alpha = 1f - 0.9f * hump(t)) },
        emp("Bounce") { t -> Pose(ty = -0.3f * abs(sin(t * 2f * PI_F)) * (1f - t)) },
        emp("Jump") { t -> Pose(ty = -0.5f * hump(t)) },
        emp("Hop") { t -> Pose(ty = -0.22f * abs(sin(t * 3f * PI_F))) },
        emp("Flip Pop") { t -> Pose(ry = 360f * out(t)) },
        emp("Spin Once") { t -> Pose(rz = 360f * inOut(t)) },
        emp("Spin Back") { t -> Pose(rz = -360f * inOut(t)) },
        emp("Tilt Left") { t -> Pose(rz = -14f * hump(t)) },
        emp("Tilt Right") { t -> Pose(rz = 14f * hump(t)) },
        emp("Nod") { t -> Pose(rx = 28f * sin(t * TAU)) },
        emp("Shrug") { t -> Pose(ty = -0.08f * hump(t), rz = 6f * sin(t * TAU)) },
        emp("Squish") { t -> Pose(sx = 1f + 0.2f * hump(t), sy = 1f - 0.3f * hump(t)) },
        emp("Stretch Pop") { t -> Pose(sx = 1f + 0.35f * hump(t), sy = 1f - 0.15f * hump(t)) },
        emp("Knock", 600) { t -> Pose(tx = -0.06f * abs(sin(t * 3f * PI_F))) },
        emp("Ping") { t -> val h = hump(t); Pose(alpha = 1f - 0.3f * h, sx = 1f + 0.3f * h, sy = 1f + 0.3f * h) },
        emp("Wiggle", 900) { t -> Pose(rz = 10f * decay(t, 6f)) },
        emp("Throb", 700) { t -> val h = hump(t); val k = 1f + 0.08f * h * h; Pose(sx = k, sy = k) },
        emp("Buzz", 500) { t -> Pose(tx = 0.02f * decay(t, 20f)) },

        // ── loops ──
        lp("Breathe") { t -> val k = 1f + 0.05f * s(t); Pose(sx = k, sy = k) },
        lp("Float", 3000) { t -> Pose(ty = -0.06f * s(t)) },
        lp("Hover Bob", 3000) { t -> Pose(ty = -0.04f * s(t), rz = 2f * s(t)) },
        lp("Sway", 3200) { t -> Pose(rz = 5f * s(t)) },
        lp("Pendulum", 2200) { t -> Pose(rz = 18f * s(t)) },
        lp("Spin Slow", 8000) { t -> Pose(rz = 360f * t) },
        lp("Spin Fast", 900) { t -> Pose(rz = 360f * t) },
        lp("Spin Reverse", 3000) { t -> Pose(rz = -360f * t) },
        lp("Pulse Loop", 1600) { t -> val k = 1f + 0.1f * (0.5f - 0.5f * c(t)); Pose(sx = k, sy = k) },
        lp("Heartbeat Loop", 1300) { t -> val k = 1f + 0.15f * max(0f, s(t * 2f)).pow(2); Pose(sx = k, sy = k) },
        lp("Bounce Loop", 1000) { t -> Pose(ty = -0.25f * abs(sin(t * PI_F))) },
        lp("Wobble Loop", 2000) { t -> Pose(tx = 0.05f * s(t), rz = 6f * s(t)) },
        lp("Shimmer", 2000) { t -> Pose(alpha = 0.7f + 0.3f * s(t)) },
        lp("Twinkle", 1800) { t -> val k = 1f + 0.1f * s(t * 2f); Pose(alpha = 0.75f + 0.25f * s(t), sx = k, sy = k) },
        lp("Blink Loop", 3000) { t -> val d = (t - 0.5f) * 30f; Pose(alpha = 1f - 0.9f * exp(-d * d)) },
        lp("Flicker", 1500) { t -> Pose(alpha = 0.8f + 0.2f * s(t * 7f) * s(t * 3f)) },
        lp("Jelly Loop", 1600) { t -> Pose(sx = 1f + 0.08f * s(t), sy = 1f - 0.08f * s(t)) },
        lp("Rock", 2000) { t -> Pose(rz = 10f * s(t), ty = 0.02f * c(t * 2f)) },
        lp("Orbit Small", 2400) { t -> Pose(tx = 0.08f * c(t), ty = 0.08f * s(t)) },
        lp("Figure Eight", 3000) { t -> Pose(tx = 0.1f * s(t), ty = 0.05f * s(t * 2f)) },
        lp("Drift", 5000) { t -> Pose(tx = 0.06f * s(t), ty = 0.04f * c(t * 2f)) },
        lp("Swim", 3600) { t -> Pose(tx = 0.04f * s(t), ty = 0.03f * s(t * 3f), rz = 3f * c(t)) },
        lp("Shake Slow", 1000) { t -> Pose(tx = 0.03f * s(t * 4f)) },
        lp("Tick", 1600) { t -> Pose(rz = 8f * (if (t < 0.25f) 0f else if (t < 0.5f) 1f else if (t < 0.75f) 0f else -1f)) },
        lp("Metronome", 1200) { t -> val v = s(t); Pose(rz = 25f * v * v * v) },
        lp("Seesaw", 2200) { t -> Pose(rz = 12f * s(t), tx = 0.05f * s(t)) },
        lp("Flutter", 2000) { t -> Pose(ry = 40f * s(t)) },
        lp("Throb Loop", 1400) { t -> Pose(sx = 1f + 0.06f * s(t), sy = 1f + 0.06f * c(t)) },
        lp("Levitate", 3200) { t -> val h = 0.5f - 0.5f * c(t); val k = 1f + 0.03f * h; Pose(ty = -0.08f * h, sx = k, sy = k) },
        lp("Glow Pulse", 1800) { t -> val h = 0.5f - 0.5f * c(t); val k = 1f + 0.04f * h; Pose(alpha = 0.55f + 0.45f * h, sx = k, sy = k) }
    )

    fun of(kind: MotionKind): List<Fx> = all.filter { it.kind == kind }
    fun named(name: String): Fx = all.first { it.name == name }
}

/**
 * Plays [fx] on this view. Entrances start hidden/offset and settle at rest, emphasis plays once, loops repeat.
 * Change [replay] to play it again.
 */
@Composable
fun Modifier.fx(fx: Fx, replay: Int = 0): Modifier {
    val p = remember(fx) { Animatable(0f) }
    LaunchedEffect(fx, replay) {
        if (fx.kind == MotionKind.LOOP) {
            while (isActive) { p.snapTo(0f); p.animateTo(1f, tween(fx.ms, easing = LinearEasing)) }
        } else {
            p.snapTo(0f); p.animateTo(1f, tween(fx.ms, easing = LinearEasing))
        }
    }
    return this.graphicsLayer {
        val ps = fx.pose(p.value)
        alpha = ps.alpha; scaleX = ps.sx; scaleY = ps.sy
        translationX = ps.tx * size.width; translationY = ps.ty * size.height
        rotationZ = ps.rz; rotationX = ps.rx; rotationY = ps.ry
        cameraDistance = 14f * density
    }
}
