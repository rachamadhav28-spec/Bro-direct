package com.bro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.task.ActionStatus
import com.bro.assistant.task.StepUi
import com.bro.assistant.ui.theme.BroColors

/** Shows each step of the current task with a tick, cross or in-progress mark. */
@Composable
fun TaskProgressCard(steps: List<StepUi>, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    Column(
        modifier
            .fillMaxWidth()
            .background(BroColors.Panel, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        for (step in steps) {
            val mark = when (step.status) {
                ActionStatus.SUCCESS -> "\u2713"
                ActionStatus.FAILED -> "\u2717"
                ActionStatus.RUNNING -> "\u2026"
                ActionStatus.PENDING -> "\u2022"
            }
            val color = when (step.status) {
                ActionStatus.SUCCESS -> BroColors.Success
                ActionStatus.FAILED -> BroColors.Error
                ActionStatus.RUNNING -> BroColors.Executing
                ActionStatus.PENDING -> Color.Gray
            }
            Text("$mark ${step.label}", color = color, fontSize = 14.sp)
            if (step.status == ActionStatus.FAILED && !step.note.isNullOrBlank()) {
                Text(step.note, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}
