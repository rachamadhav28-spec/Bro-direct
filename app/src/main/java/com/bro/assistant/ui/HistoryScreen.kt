package com.bro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.ChatViewModel
import com.bro.assistant.ui.theme.BroColors
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(vm: ChatViewModel, onBack: () -> Unit, onOpen: () -> Unit, embedded: Boolean = false) {
    var query by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    val shown = vm.history.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    FuturisticBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!embedded) TextButton(onClick = onBack) { Text("\u2190 Back") }
                Text("History", color = BroColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.newConversation(); onOpen() }) { Text("New chat") }
            }
            TextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp),
                placeholder = { Text("Search your chats", color = BroColors.TextSecondary) },
                singleLine = true, shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BroColors.Panel, unfocusedContainerColor = BroColors.Panel,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = BroColors.TextPrimary, unfocusedTextColor = BroColors.TextPrimary, cursorColor = NeonCyan
                )
            )
            if (shown.isEmpty()) {
                Text(
                    if (vm.history.isEmpty()) "No saved chats yet. Say something to BRO and it will show up here." else "No chat matches \"$query\".",
                    modifier = Modifier.padding(24.dp), color = BroColors.TextSecondary, fontSize = 15.sp
                )
            }
            val groups = remember(shown) { shown.groupBy { dayGroup(it.updated) }.toList().sortedBy { groupOrder(it.first) } }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                groups.forEach { (label, list) ->
                    item(key = "g_$label") {
                        Text(label, color = BroColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp))
                    }
                    items(list, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                                .clickable(onClickLabel = "Open chat") { vm.openConversation(c.id); onOpen() }
                                .padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(c.title.ifBlank { "Chat" }, maxLines = 2, overflow = TextOverflow.Ellipsis, color = BroColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { deleteId = c.id }) { Text("Delete", color = BroColors.TextSecondary, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete this chat?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteConversation(id); deleteId = null }) { Text("Delete", color = BroColors.Error) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep") } }
        )
    }
}

private fun dayStart(t: Long): Long {
    val c = java.util.Calendar.getInstance(); c.timeInMillis = t
    c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0); c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun dayGroup(updated: Long): String {
    val days = ((dayStart(System.currentTimeMillis()) - dayStart(updated)) / 86_400_000L).toInt()
    return when { days <= 0 -> "Today"; days == 1 -> "Yesterday"; days <= 7 -> "Previous 7 days"; days <= 30 -> "Previous 30 days"; else -> "Older" }
}

private fun groupOrder(g: String) = listOf("Today", "Yesterday", "Previous 7 days", "Previous 30 days", "Older").indexOf(g)
