package com.bro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.bro.assistant.task.ActionStatus
import com.bro.assistant.ui.theme.BroColors

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onMic: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onSecureConfirm: ((() -> Unit) -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // "Download chat": saves the whole conversation as a text file
    val chatSaver = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportText().toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            android.widget.Toast.makeText(context, if (ok) "Chat saved" else "Couldn't save the chat", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // the task card disappears by itself a few seconds after the task is over (it can also be closed with the X)
    val taskOver = vm.steps.isNotEmpty() && vm.steps.none { it.status == ActionStatus.PENDING || it.status == ActionStatus.RUNNING }
    LaunchedEffect(taskOver, vm.steps.size) {
        if (taskOver) {
            kotlinx.coroutines.delay(5000)
            vm.clearSteps()
        }
    }

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
    ) {
        // ── Top bar: identity on the left, state + actions on the right ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BroWordmark(22.sp, 2.sp)
                Text("build " + com.bro.assistant.BuildConfig.BUILD_NUMBER, color = BroColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.state != BroState.IDLE) StatusChip(vm.state)
                IconButton(onClick = { vm.newConversation() }, modifier = Modifier.semantics { contentDescription = "New chat" }) {
                    Text("+", color = BroColors.TextPrimary, fontSize = 26.sp)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "Menu" }) {
                        Text("\u22EE", color = BroColors.TextPrimary, fontSize = 22.sp)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = BroColors.Panel) {
                        DropdownMenuItem(text = { Text("History", color = BroColors.TextPrimary) }, onClick = { menuOpen = false; onOpenHistory() })
                        DropdownMenuItem(
                            text = { Text("Download chat", color = BroColors.TextPrimary) },
                            onClick = {
                                menuOpen = false
                                if (vm.messages.isEmpty()) {
                                    android.widget.Toast.makeText(context, "Nothing to download yet", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                                    chatSaver.launch("BRO-chat-$stamp.txt")
                                }
                            }
                        )
                        DropdownMenuItem(text = { Text("Settings", color = BroColors.TextPrimary) }, onClick = { menuOpen = false; onOpenSettings() })
                        DropdownMenuItem(
                            text = { Text("Zoom in  +", color = BroColors.TextPrimary) },
                            onClick = { com.bro.assistant.ui.theme.ThemeState.zoomBy(0.1f) }
                        )
                        DropdownMenuItem(
                            text = { Text("Zoom out  \u2212   (${(com.bro.assistant.ui.theme.ThemeState.zoom * 100).toInt()}%)", color = BroColors.TextPrimary) },
                            onClick = { com.bro.assistant.ui.theme.ThemeState.zoomBy(-0.1f) }
                        )
                    }
                }
            }
        }

        // The orb only appears while you are talking to BRO (mic on).
        if (vm.state == BroState.LISTENING) {
            Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                BroOrb(vm.state, Modifier.size(160.dp))
            }
            Text(
                statusLabel(vm.state, vm.partialText),
                color = BroColors.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        Box(Modifier.padding(horizontal = 16.dp)) { TaskProgressCard(vm.steps, onDismiss = { vm.clearSteps() }) }

        // ── Conversation, or a welcome with starters when it is empty ──
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.messages.isEmpty() && !thinking && vm.state != BroState.LISTENING) {
                val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
                val greeting = when { hour < 5 -> "Still up?"; hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; hour < 22 -> "Good evening"; else -> "Late night?" }
                Column(
                    Modifier.fillMaxSize().widthIn(max = 640.dp).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        HudRings(Modifier.fillMaxSize())
                        Box(
                            Modifier.size(56.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(NeonCyan, NeonViolet)))
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(greeting, color = BroColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("What can I do for you? Ask anything, or tell me what to do on your phone. English, Telugu or Tenglish.", color = BroColors.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StarterCard("Write code", "A program or a whole app, saved as files") { vm.send("write a python program for a calculator") }
                        StarterCard("Build an app", "BRO makes it on GitHub and keeps fixing until it builds") { vm.send("create an app: a simple calculator") }
                        StarterCard("Quiz me", "Practice a subject; I remember your weak points") { vm.send("quiz me on DBMS") }
                        StarterCard("Do it on my phone", "Say what you want done in any app") { vm.send("open settings and turn on dark mode") }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val lastBroId = vm.messages.lastOrNull { !it.fromUser }?.id
                    items(vm.messages, key = { it.id }) { message ->
                        val speakingNow = vm.state == BroState.SPEAKING && message.id == (vm.readingId ?: lastBroId)
                        Bubble(message, speakingNow, onListen = { vm.readAloud(message) }, onMute = { vm.mute() })
                    }
                    if (thinking || com.bro.assistant.Activity.active) item(key = "activity") { ActivityCard(live = true) }
                    else if (com.bro.assistant.Activity.lastItems.isNotEmpty() && vm.messages.lastOrNull()?.fromUser == false) item(key = "activity_done") { ActivityCard(live = false) }
                }
                }
            }
        }

        // ── Composer: a rounded card. Text on top; underneath, BRO's status on the left and one button on the right
        //    (Mic when empty, Send when typing, Stop while listening) ──
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth()
                    .clip(shape).background(BroColors.Panel)
                    .border(1.dp, BroColors.Outline, shape)
                    .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    placeholder = { Text("Message BRO", color = BroColors.TextSecondary) },
                    maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = BroColors.Accent,
                        focusedTextColor = BroColors.TextPrimary,
                        unfocusedTextColor = BroColors.TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() })
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        statusLabel(vm.state, vm.partialText),
                        modifier = Modifier.weight(1f),
                        color = BroColors.TextSecondary, fontSize = 13.sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    when {
                        vm.state == BroState.LISTENING -> RoundAction(RoundIcon.STOP, BroColors.Error, "Stop listening", onMic)
                        input.isNotBlank() -> RoundAction(RoundIcon.SEND, BroColors.Accent, "Send message") { submit() }
                        else -> RoundAction(RoundIcon.MIC, BroColors.Accent, "Talk to BRO", onMic)
                    }
                }
            }
        }
    }
    }

    vm.confirmPrompt?.let { question ->
        AlertDialog(
            onDismissRequest = { vm.answerConfirm(false) },
            title = { Text("Confirm") },
            text = { Text(question) },
            confirmButton = {
                TextButton(onClick = {
                    val yes = { vm.answerConfirm(true) }
                    // risky taps inside other apps (pay, delete, ...) need fingerprint / screen lock again
                    if (question.startsWith("BRO wants to tap") && onSecureConfirm != null) onSecureConfirm(yes) else yes()
                }) { Text("Yes") }
            },
            dismissButton = { TextButton(onClick = { vm.answerConfirm(false) }) { Text("No") } }
        )
    }
}

@Composable
private fun Bubble(message: ChatMessage, speakingNow: Boolean, onListen: () -> Unit, onMute: () -> Unit) {
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.tween(220)) }
    val shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1800); copied = false } }
    Column(
        Modifier.fillMaxWidth().graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 12f },
        horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start
    ) {
        // you: a soft bubble on the right. BRO: plain text across the full width, like a document.
        if (message.fromUser) {
            Box(
                Modifier.widthIn(max = 320.dp).background(BroColors.UserBubble, shape).padding(horizontal = 14.dp, vertical = 10.dp)
            ) { MessageBody(message.text, markdown = false) }
        } else {
            Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) { MessageBody(message.text, markdown = true) }
        }
        // under BRO's replies: Copy, and Mute (while it talks) or Listen
        if (!message.fromUser && message.text.isNotBlank()) {
            Row(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MessageAction(if (copied) "Copied \u2713" else "Copy", "Copy this reply") {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(message.text)); copied = true
                }
                if (speakingNow) MessageAction("Mute", "Stop talking", BroColors.Error, onMute)
                else MessageAction("Listen", "Read this reply aloud", onClick = onListen)
            }
        }
    }
}

@Composable
private fun MessageAction(label: String, description: String, color: Color = BroColors.TextSecondary, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = description) { onClick() }
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
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
