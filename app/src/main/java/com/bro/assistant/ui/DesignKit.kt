package com.bro.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.BroState
import com.bro.assistant.ui.theme.BroColors

/** Quiet backdrop: a soft gradient and one glow. (The animated HUD stays on the lock and call screens.) */
@Composable
fun CalmBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF060B16), Color(0xFF0A1226))))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(NeonViolet.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.5f, 0f), radius = size.width * 0.9f),
                radius = size.width * 0.9f, center = Offset(size.width * 0.5f, 0f)
            )
        }
        content()
    }
}

fun stateColor(s: BroState): Color = when (s) {
    BroState.IDLE -> Color(0xFF8EA1BD)
    BroState.LISTENING -> BroColors.Listening
    BroState.THINKING -> BroColors.Thinking
    BroState.EXECUTING -> BroColors.Executing
    BroState.SPEAKING -> BroColors.Accent
    BroState.SUCCESS -> BroColors.Success
    BroState.ERROR -> BroColors.Error
}

fun stateShort(s: BroState): String = when (s) {
    BroState.IDLE -> "Ready"; BroState.LISTENING -> "Listening"; BroState.THINKING -> "Thinking"
    BroState.EXECUTING -> "Working"; BroState.SPEAKING -> "Speaking"; BroState.SUCCESS -> "Done"; BroState.ERROR -> "Error"
}

/** A small pill that always tells the user what BRO is doing (colour + word, never colour alone). */
@Composable
fun StatusChip(state: BroState) {
    val busy = state == BroState.LISTENING || state == BroState.THINKING || state == BroState.EXECUTING || state == BroState.SPEAKING
    val t = rememberInfiniteTransition(label = "chip")
    val pulse by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")
    val c = stateColor(state)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "BRO is " + stateShort(state) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c.copy(alpha = if (busy) pulse else 1f)))
        Spacer(Modifier.width(8.dp))
        Text(stateShort(state), color = c, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Big tappable starter card for the empty screen (min height 56dp). */
@Composable
fun StarterCard(title: String, hint: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    Column(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = sc; scaleY = sc }.heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
            .background(BroColors.Surface2).border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClickLabel = title) { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = BroColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, color = BroColors.TextSecondary, fontSize = 13.sp)
    }
}

enum class RoundIcon { MIC, SEND, STOP }

/** 48dp round action button with an icon drawn in code (no icon library needed). */
@Composable
fun RoundAction(icon: RoundIcon, fill: Color, label: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    Box(
        Modifier.size(48.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape).background(fill)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClickLabel = label) { onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width; val h = size.height; val ink = com.bro.assistant.ui.theme.ThemeState.on(fill)
            when (icon) {
                RoundIcon.MIC -> {
                    drawRoundRect(ink, Offset(w * 0.36f, h * 0.06f), Size(w * 0.28f, h * 0.52f), CornerRadius(w * 0.14f))
                    drawArc(ink, 0f, 180f, false, Offset(w * 0.22f, h * 0.26f), Size(w * 0.56f, h * 0.44f), style = Stroke(2.2f * density, cap = StrokeCap.Round))
                    drawLine(ink, Offset(w * 0.5f, h * 0.7f), Offset(w * 0.5f, h * 0.88f), 2.2f * density, StrokeCap.Round)
                    drawLine(ink, Offset(w * 0.36f, h * 0.9f), Offset(w * 0.64f, h * 0.9f), 2.2f * density, StrokeCap.Round)
                }
                RoundIcon.SEND -> {
                    val p = Path().apply { moveTo(w * 0.1f, h * 0.1f); lineTo(w * 0.92f, h * 0.5f); lineTo(w * 0.1f, h * 0.9f); lineTo(w * 0.28f, h * 0.5f); close() }
                    drawPath(p, ink)
                }
                RoundIcon.STOP -> drawRoundRect(ink, Offset(w * 0.22f, h * 0.22f), Size(w * 0.56f, h * 0.56f), CornerRadius(w * 0.1f))
            }
        }
    }
}

/** The BRO name with the theme gradient and a soft glow. */
@Composable
fun BroWordmark(size: androidx.compose.ui.unit.TextUnit, spacing: androidx.compose.ui.unit.TextUnit = 4.sp) {
    val glow = breathing(2600)
    Text(
        "BRO", fontSize = size, fontWeight = FontWeight.Black, letterSpacing = spacing,
        style = androidx.compose.ui.text.TextStyle(
            brush = neonBrush(),
            shadow = androidx.compose.ui.graphics.Shadow(NeonCyan.copy(alpha = 0.35f + 0.4f * glow), blurRadius = 12f + 12f * glow)
        )
    )
}

/** Slowly turning HUD rings. Draws nothing when the HUD animation is off. */
@Composable
fun HudRings(modifier: Modifier = Modifier) {
    if (!com.bro.assistant.ui.theme.ThemeState.hud) return
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "rings")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = androidx.compose.animation.core.LinearEasing)), label = "a")
    val b by t.animateFloat(360f, 0f, infiniteRepeatable(tween(9000, easing = androidx.compose.animation.core.LinearEasing)), label = "b")
    Canvas(modifier) {
        val w = size.width
        fun ring(inset: Float, start: Float, sweep: Float, c: Color, width: Float) =
            drawArc(c, start, sweep, false, Offset(inset, inset), Size(w - 2 * inset, w - 2 * inset), style = Stroke(width, cap = StrokeCap.Round))
        ring(0f, a, 70f, NeonCyan.copy(alpha = 0.9f), 3.dp.toPx()); ring(0f, a + 180f, 70f, NeonCyan.copy(alpha = 0.9f), 3.dp.toPx())
        ring(10.dp.toPx(), b, 110f, NeonViolet.copy(alpha = 0.8f), 2.dp.toPx()); ring(10.dp.toPx(), b + 180f, 110f, NeonViolet.copy(alpha = 0.8f), 2.dp.toPx())
        ring(20.dp.toPx(), a * 2f, 30f, NeonCyan.copy(alpha = 0.5f), 1.5f.dp.toPx())
    }
}
