package com.bro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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

    val thinking = vm.state == BroState.THINKING || vm.state == BroState.EXECUTING
    LaunchedEffect(vm.messages.size, thinking) {
        val total = vm.messages.size + if (thinking) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    fun submit() {
        val text = input.trim()
        if (text.isNotEmpty()) {
            vm.send(text)
            input = ""
        }
    }

    FuturisticBackground {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                val glow = breathing()
                Text(
                    "BRO", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = neonBrush(),
                        shadow = androidx.compose.ui.graphics.Shadow(NeonCyan.copy(alpha = 0.4f + 0.5f * glow), blurRadius = 14f + 14f * glow)
                    )
                )
                Text(
                    "  build " + com.bro.assistant.BuildConfig.BUILD_NUMBER,
                    color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp)
                )
            }
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

        // The orb only appears while you are talking to BRO (mic on).
        if (vm.state == BroState.LISTENING) {
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
        }

        TaskProgressCard(vm.steps)
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(vm.messages, key = { it.id }) { message -> Bubble(message) }
            if (thinking) item(key = "dino") { DinoThinking(if (vm.state == BroState.EXECUTING) "Working" else "Thinking") }
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
                placeholder = { Text("Type") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = NeonCyan.copy(alpha = 0.35f),
                    cursorColor = NeonCyan,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedPlaceholderColor = Color.Gray,
                    unfocusedPlaceholderColor = Color.Gray,
                    focusedContainerColor = Color(0x6608162B),
                    unfocusedContainerColor = Color(0x4D08162B)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() })
            )
            Button(
                onClick = onMic,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vm.state == BroState.LISTENING) BroColors.Listening else BroColors.Accent
                )
            ) { Text(if (vm.state == BroState.LISTENING) "Stop" else "Mic") }
            Button(onClick = { submit() }) { Text("Send") }
        }
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
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.tween(380)) }
    val accent = if (message.fromUser) NeonViolet else NeonCyan
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 28f
            },
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 290.dp)
                .background(
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.20f), Color(0xCC0B1426))),
                    shape
                )
                .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.15f))), shape)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            MessageBody(message.text)
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
