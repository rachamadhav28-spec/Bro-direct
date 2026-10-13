package com.bro.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown instead of BRO until the owner proves it is them. Nothing from the chat is visible here. */
@Composable
fun LockScreen(message: String, onUnlock: () -> Unit) {
    FuturisticBackground {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BroWordmark(40.sp, 6.sp)
            Spacer(Modifier.height(8.dp))
            Text("Locked", color = Color.White, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}
