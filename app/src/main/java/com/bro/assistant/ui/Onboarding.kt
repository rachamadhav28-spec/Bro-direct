package com.bro.assistant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.ui.theme.BroColors

private class Page(val title: String, val body: String, val bullets: List<String>)

private val PAGES = listOf(
    Page(
        "Meet BRO",
        "A voice assistant that answers, writes code and builds apps. Talk or type, in English, Telugu or Tenglish.",
        listOf("Ask anything and get a clear answer", "Say \"write a python calculator\" and get the file", "Say \"create an app: ...\" and BRO builds it on GitHub")
    ),
    Page(
        "Works on your phone",
        "BRO can open apps, tap, type and read the screen for you. It asks before anything risky, like paying or deleting.",
        listOf("Turn on BRO in Accessibility settings (Settings, Phone access)", "Allow the microphone to talk to BRO", "Say \"stop\" at any time to cancel")
    ),
    Page(
        "Give BRO a brain",
        "BRO needs one free AI key to answer, or you can download an offline model that works without internet.",
        listOf("Get a free Gemini key at aistudio.google.com", "Paste it in Settings, AI, then tap Save", "Or use Settings, Offline brain, to run with no internet")
    )
)

/** Three short pages shown once, right after the first sign-in. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, onOpenSettings: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val last = index == PAGES.lastIndex
    Box(Modifier.fillMaxSize().background(BroColors.Background).systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish) { Text("Skip", color = BroColors.TextSecondary) }
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(targetState = index, label = "page") { i ->
                val p = PAGES[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(NeonCyan, NeonViolet))))
                    Spacer(Modifier.height(28.dp))
                    Text(p.title, color = BroColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(p.body, color = BroColors.TextSecondary, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
                    Spacer(Modifier.height(24.dp))
                    p.bullets.forEach {
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(BroColors.Accent))
                            Spacer(Modifier.size(12.dp))
                            Text(it, color = BroColors.TextPrimary, fontSize = 15.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                PAGES.indices.forEach { i ->
                    Box(Modifier.size(if (i == index) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i == index) BroColors.Accent else BroColors.Outline))
                }
            }
            if (last) {
                Button(modifier = Modifier.fillMaxWidth().height(52.dp), onClick = onOpenSettings) { Text("Open settings") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(modifier = Modifier.fillMaxWidth().height(52.dp), onClick = onFinish) { Text("Start chatting") }
            } else {
                Button(modifier = Modifier.fillMaxWidth().height(52.dp), onClick = { index++ }) { Text("Next") }
            }
        }
    }
}
