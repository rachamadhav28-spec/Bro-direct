package com.bro.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/*
 * The "no internet" dinosaur game, playing itself while BRO thinks.
 * Game units are pixels of the sprite (one unit = 2.5dp). The jump timing was checked by
 * simulation: with these numbers the dinosaur clears every cactus.
 */

private const val SPEED = 70f      // units per second
private const val GRAVITY = 260f
private const val JUMP_V = 120f
private const val DINO_X = 12f
private const val LOW_W = 18f      // width of the dinosaur's lower body (tail to hand)
private const val TRIGGER = 17.4f  // start jumping when the cactus is this close
private const val STEP = 1f / 120f
private const val CACTUS_W = 9f

private class Cactus(var x: Float, val h: Int)

private class DinoState {
    var scroll = 0f
    var y = 0f
    var vy = 0f
    var clock = 0f
    var score = 0f
    var nextGap = 100f
    var cloudA = 40f
    var cloudB = 105f
    val cacti = mutableListOf<Cactus>()
    private val rng = Random(System.nanoTime())

    fun update(dt: Float, width: Float) {
        scroll += SPEED * dt
        clock += dt
        score += dt * 10f
        cloudA -= SPEED * 0.2f * dt
        cloudB -= SPEED * 0.2f * dt
        if (cloudA < -16f) cloudA = width + 10f
        if (cloudB < -16f) cloudB = width + 40f

        val last = cacti.lastOrNull()
        if (last == null || last.x < width - nextGap) {
            cacti.add(Cactus(width + 5f, intArrayOf(12, 14, 17)[rng.nextInt(3)]))
            nextGap = 90f + rng.nextFloat() * 50f
        }
        for (c in cacti) c.x -= SPEED * dt
        cacti.removeAll { it.x + CACTUS_W < -5f }

        // auto-play: jump when a cactus gets close
        if (y == 0f && vy == 0f) {
            for (c in cacti) {
                val gap = c.x - (DINO_X + LOW_W)
                if (gap > 0f && gap <= TRIGGER) {
                    vy = JUMP_V
                    break
                }
            }
        }
        if (vy != 0f || y > 0f) {
            y += vy * dt
            vy -= GRAVITY * dt
            if (y <= 0f) {
                y = 0f
                vy = 0f
            }
        }
    }
}

private val CARD = Color(0xFF0E1726)
private val SPRITE = Color(0xFFD9D9D9)
private val GROUND = Color(0xFF8A8A8A)
private val CLOUD = Color(0xFF38404D)

@Composable
fun DinoThinking(label: String = "Thinking") {
    val unit = with(LocalDensity.current) { 2.5.dp.toPx() }
    var widthPx by remember { mutableStateOf(0f) }
    var frame by remember { mutableStateOf(0L) }
    val game = remember { DinoState() }

    LaunchedEffect(Unit) {
        var last = androidx.compose.runtime.withFrameNanos { it }
        var acc = 0f
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            acc += ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
            last = now
            val width = if (widthPx > 0f) widthPx / unit else 130f
            while (acc >= STEP) {
                game.update(STEP, width)
                acc -= STEP
            }
            frame = now
        }
    }

    val dots = ".".repeat(1 + ((frame / 400_000_000L) % 3).toInt())

    Column(
        Modifier
            .fillMaxWidth()
            .background(CARD, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label + dots, color = Color.LightGray, fontSize = 13.sp)
            Text(
                "%05d".format(game.score.toInt()),
                color = GROUND, fontSize = 13.sp, fontFamily = FontFamily.Monospace
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .onSizeChanged { widthPx = it.width.toFloat() }
        ) {
            val f = frame // read so the canvas redraws every frame
            if (f < 0L) return@Canvas
            drawGame(game, unit)
        }
    }
}

private fun DrawScope.box(u: Float, x: Float, y: Float, w: Float, h: Float, color: Color) {
    drawRect(color, Offset(x * u, y * u), Size(w * u, h * u))
}

private fun DrawScope.drawGame(g: DinoState, u: Float) {
    val widthUnits = size.width / u
    val heightUnits = size.height / u
    val ground = heightUnits - 6f

    // clouds
    for (cx in floatArrayOf(g.cloudA, g.cloudB)) {
        val cy = if (cx == g.cloudA) 8f else 20f
        box(u, cx, cy + 2f, 14f, 3f, CLOUD)
        box(u, cx + 3f, cy, 8f, 2f, CLOUD)
    }

    // ground line and bumps
    box(u, 0f, ground, widthUnits + 1f, 1f, GROUND)
    val first = (g.scroll / 10f).toInt()
    for (t in 0..(widthUnits / 10f).toInt() + 2) {
        val idx = first + t
        val px = t * 10f - (g.scroll % 10f)
        when ((idx * 31 + 7) % 5) {
            0 -> box(u, px, ground + 2f, 3f, 1f, GROUND)
            2 -> box(u, px + 4f, ground + 3f, 2f, 1f, GROUND)
        }
    }

    // cacti
    for (c in g.cacti) {
        val top = ground - c.h
        box(u, c.x + 3f, top, 3f, c.h.toFloat(), SPRITE)          // stem
        box(u, c.x, top + 3f, 2f, 7f, SPRITE)                      // left arm up
        box(u, c.x, top + 9f, 4f, 2f, SPRITE)                      // left arm across
        box(u, c.x + 7f, top + 5f, 2f, 6f, SPRITE)                 // right arm up
        box(u, c.x + 5f, top + 9f, 4f, 2f, SPRITE)                 // right arm across
    }

    // dinosaur
    val dx = DINO_X
    val dy = ground - 25f - g.y
    box(u, dx + 12f, dy, 10f, 8f, SPRITE)         // head
    box(u, dx + 15f, dy + 1f, 2f, 2f, CARD)       // eye
    box(u, dx + 17f, dy + 6f, 5f, 1f, CARD)       // mouth
    box(u, dx + 11f, dy + 8f, 5f, 3f, SPRITE)     // neck
    box(u, dx + 4f, dy + 10f, 11f, 9f, SPRITE)    // body
    box(u, dx + 5f, dy + 19f, 8f, 2f, SPRITE)     // belly
    box(u, dx, dy + 10f, 4f, 3f, SPRITE)          // tail
    box(u, dx, dy + 8f, 2f, 2f, SPRITE)           // tail tip
    box(u, dx + 15f, dy + 13f, 3f, 2f, SPRITE)    // arm
    val airborne = g.y > 0f
    val phase = if (airborne) -1 else (g.clock * 10f).toInt() % 2
    for ((i, lx) in floatArrayOf(5f, 10f).withIndex()) {
        val raised = phase == i
        if (raised) {
            box(u, dx + lx, dy + 21f, 3f, 2f, SPRITE)
            box(u, dx + lx, dy + 23f, 5f, 1f, SPRITE)
        } else {
            box(u, dx + lx, dy + 21f, 3f, 3f, SPRITE)
            box(u, dx + lx, dy + 24f, 5f, 1f, SPRITE)
        }
    }
}
