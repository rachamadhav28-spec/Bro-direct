package com.bro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.BroState
import com.bro.assistant.ChatViewModel
import kotlinx.coroutines.delay

@Composable
private fun RoundButton(label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(color).clickable(onClickLabel = label) { onClick() }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Text(if (color == Color(0xFF22C55E)) "\u260E" else "\u2715", color = Color.White, fontSize = 28.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

/** The full-screen "BRO is calling" screen and the in-call screen. */
@Composable
fun BroCallScreen(vm: ChatViewModel, talking: Boolean, text: String, onAnswer: () -> Unit, onHangUp: () -> Unit) {
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(talking) { seconds = 0; while (talking) { delay(1000); seconds++ } }
    FuturisticBackground {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 60.dp)) {
                BroWordmark(40.sp, 6.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (!talking) "BRO is calling..." else "%d:%02d".format(seconds / 60, seconds % 60),
                    color = Color.White, fontSize = 18.sp
                )
                Spacer(Modifier.height(30.dp))
                BroOrb(if (talking) vm.state else BroState.SPEAKING, Modifier.size(170.dp))
                Spacer(Modifier.height(20.dp))
                Text(
                    if (!talking) text.take(120)
                    else when (vm.state) {
                        BroState.LISTENING -> vm.partialText.ifBlank { "Listening to you..." }
                        BroState.SPEAKING -> "BRO is talking..."
                        BroState.THINKING, BroState.EXECUTING -> "BRO is thinking..."
                        else -> "Say something, boss"
                    },
                    color = Color.LightGray, fontSize = 15.sp, textAlign = TextAlign.Center
                )
            }
            if (!talking) {
                Row(Modifier.fillMaxSize().padding(bottom = 40.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    RoundButton("Decline", Color(0xFFEF4444), onHangUp)
                    RoundButton("Answer", Color(0xFF22C55E), onAnswer)
                }
            } else {
                Column(Modifier.padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Say \"that's all\" or tap to end", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    RoundButton("Hang up", Color(0xFFEF4444), onHangUp)
                }
            }
        }
    }
}
