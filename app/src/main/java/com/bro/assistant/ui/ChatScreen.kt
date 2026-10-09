package com.bro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.BroState
import com.bro.assistant.ChatMessage
import com.bro.assistant.ChatViewModel
import com.bro.assistant.ui.theme.BroColors

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onMic: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    fun submit() {
        val text = input.trim()
        if (text.isNotEmpty()) {
            vm.send(text)
            input = ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BroColors.Background)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BRO", color = BroColors.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = { vm.newConversation() }) { Text("New") }
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("Menu") }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = BroColors.Background
                    ) {
                        DropdownMenuItem(
                            text = { Text("History", color = BroColors.Accent) },
                            onClick = { menuOpen = false; onOpenHistory() }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings", color = BroColors.Accent) },
                            onClick = { menuOpen = false; onOpenSettings() }
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
            BroOrb(vm.state, Modifier.size(180.dp))
        }
        Text(
            statusLabel(vm.state, vm.partialText),
            color = Color.LightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )

        TaskProgressCard(vm.steps)
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(vm.messages, key = { it.id }) { message -> Bubble(message) }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a command") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() })
            )
            Button(onClick = { submit() }) { Text("Send") }
            Button(
                onClick = onMic,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vm.state == BroState.LISTENING) BroColors.Listening else BroColors.Accent
                )
            ) { Text(if (vm.state == BroState.LISTENING) "Stop" else "Mic") }
        }
    }

    vm.confirmPrompt?.let { question ->
        AlertDialog(
            onDismissRequest = { vm.answerConfirm(false) },
            title = { Text("Confirm") },
            text = { Text(question) },
            confirmButton = { TextButton(onClick = { vm.answerConfirm(true) }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { vm.answerConfirm(false) }) { Text("No") } }
        )
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 290.dp)
                .background(
                    if (message.fromUser) BroColors.UserBubble else BroColors.BroBubble,
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(message.text, color = Color.White, fontSize = 15.sp)
        }
    }
}

private fun statusLabel(state: BroState, partial: String): String = when (state) {
    BroState.IDLE -> "Ready"
    BroState.LISTENING -> partial.ifBlank { "Listening\u2026" }
    BroState.THINKING -> "Thinking\u2026"
    BroState.EXECUTING -> "Working on it\u2026"
    BroState.SPEAKING -> "Speaking\u2026"
    BroState.SUCCESS -> "Done"
    BroState.ERROR -> "Something went wrong"
}
