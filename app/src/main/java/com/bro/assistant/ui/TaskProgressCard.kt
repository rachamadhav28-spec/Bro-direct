package com.bro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.task.ActionStatus
import com.bro.assistant.task.StepUi
import com.bro.assistant.ui.theme.BroColors

/** The current task: a progress bar and each step with a tick, cross or in-progress mark (word plus colour). */
@Composable
fun TaskProgressCard(steps: List<StepUi>, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    val done = steps.count { it.status == ActionStatus.SUCCESS }
    val failed = steps.any { it.status == ActionStatus.FAILED }
    Column(
        modifier
            .fillMaxWidth()
            .background(BroColors.Surface2, RoundedCornerShape(16.dp))
            .border(1.dp, (if (failed) BroColors.Error else NeonCyan).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Working on it", color = BroColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("$done of ${steps.size}", color = BroColors.TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { done.toFloat() / steps.size },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = if (failed) BroColors.Error else BroColors.Success,
            trackColor = BroColors.Outline
        )
        Spacer(Modifier.height(8.dp))
        for (step in steps) {
            val (mark, word, color) = when (step.status) {
                ActionStatus.SUCCESS -> Triple("\u2713", "done", BroColors.Success)
                ActionStatus.FAILED -> Triple("\u2717", "failed", BroColors.Error)
                ActionStatus.RUNNING -> Triple("\u25CF", "working", BroColors.Executing)
                ActionStatus.PENDING -> Triple("\u25CB", "waiting", BroColors.TextSecondary)
            }
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(mark, color = color, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                Text(step.label, color = BroColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(word, color = color, fontSize = 12.sp)
            }
            if (step.status == ActionStatus.FAILED && !step.note.isNullOrBlank()) {
                Text(step.note, color = BroColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 22.dp))
            }
        }
    }
}
