package com.bro.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.Activity
import com.bro.assistant.ui.theme.BroColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Six dots that pulse in a wave: the "BRO is working" mark. */
@Composable
fun ThinkingDots(modifier: Modifier = Modifier.size(22.dp), color: Color = NeonCyan) {
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier) {
        val gap = size.width / 3f
        val r = size.width * 0.085f
        for (i in 0 until 6) {
            val col = i % 3; val row = i / 3
            val a = 0.25f + 0.75f * ((sin(2.0 * PI * (phase - i / 6f)).toFloat() + 1f) / 2f)
            drawCircle(color.copy(alpha = a), r * (0.8f + 0.5f * a), Offset(gap * (col + 0.5f), size.height * (0.3f + 0.4f * row)))
        }
    }
}

/**
 * Live "what is BRO doing" card: the current step with a seconds counter; tap to open the list of steps.
 * With [live] = false it is the finished summary ("Worked for 12s").
 */
@Composable
fun ActivityCard(live: Boolean) {
    var open by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) { while (live) { now = System.currentTimeMillis(); delay(1000) } }
    val items = if (live) Activity.items else Activity.lastItems
    if (items.isEmpty()) return
    val seconds = if (live) ((now - Activity.startedAt) / 1000).toInt().coerceAtLeast(0) else Activity.lastSeconds
    val current = items.lastOrNull { !it.done }?.label ?: "Thinking"
    val pulse by rememberInfiniteTransition(label = "txt").animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")

    Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = if (open) "Hide steps" else "Show steps") { open = !open }
                .semantics { contentDescription = if (live) "BRO is $current, ${seconds} seconds" else "Worked for $seconds seconds" },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (live) ThinkingDots() else Text("\u2713", color = BroColors.Success, fontSize = 16.sp, modifier = Modifier.width(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (live) current else "Worked for ${seconds}s \u00B7 ${items.size} step${if (items.size == 1) "" else "s"}",
                color = BroColors.TextSecondary.copy(alpha = if (live) pulse else 1f), fontSize = 16.sp
            )
            if (live) { Spacer(Modifier.width(8.dp)); Text("${seconds}s", color = BroColors.TextSecondary, fontSize = 14.sp) }
            Spacer(Modifier.width(6.dp))
            Text(if (open) "\u2303" else "\u2304", color = BroColors.TextSecondary, fontSize = 16.sp)
        }
        AnimatedVisibility(open) {
            Column(
                Modifier.fillMaxWidth().border(1.dp, BroColors.Outline, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items.forEach { it ->
                    Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (it.done) Text("\u2713", color = BroColors.Success, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                        else ThinkingDots(Modifier.size(18.dp))
                        Text(if (it.done) Activity.past(it.label) else it.label, color = BroColors.TextSecondary, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
