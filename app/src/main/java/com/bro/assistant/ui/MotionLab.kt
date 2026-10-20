package com.bro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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

/** Every BRO animation on one screen. Tap a tile to play it again. */
@Composable
fun MotionLabScreen(onBack: () -> Unit, prefs: com.bro.assistant.memory.PreferencesStore) {
    var kind by remember { mutableStateOf(MotionKind.ENTRANCE) }
    var playAll by remember { mutableIntStateOf(0) }
    val list = remember(kind) { Motion.of(kind) }
    var selected by remember { mutableStateOf<Fx?>(null) }

    Column(Modifier.fillMaxSize().background(BroColors.Background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = BroColors.TextSecondary) }
            Box(Modifier.weight(1f))
            BroLogo(28.dp)
            Text("  Motion lab", color = BroColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.size(12.dp))
        }
        Text(
            "${Motion.all.size} animations. Tap a tile to play it again.",
            color = BroColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MotionKind.entries.forEach { k ->
                val label = "${k.label} (${Motion.of(k).size})"
                val pick = { kind = k }
                if (kind == k) Button(onClick = pick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text(label, fontSize = 12.sp, maxLines = 1) }
                else OutlinedButton(onClick = pick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text(label, fontSize = 12.sp, maxLines = 1) }
            }
        }
        Text(
            "In use: messages ${Motion.messageFx.name}, cards and lists ${Motion.cardFx.name}, send button ${Motion.sendFx.name}, logo ${Motion.logoFx?.name ?: "none"}",
            color = BroColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)
        )
        val pickedFx = selected?.takeIf { it.kind == kind }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pickedFx == null) {
                Text("Tap an animation, then choose where BRO should use it.", color = BroColors.TextPrimary, fontSize = 13.sp)
            } else {
                Text("Selected: ${pickedFx.name}", color = BroColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (kind) {
                        MotionKind.ENTRANCE -> {
                            Button(onClick = { Motion.choose("message", pickedFx, prefs) }) { Text("Use for messages", fontSize = 12.sp) }
                            Button(onClick = { Motion.choose("card", pickedFx, prefs) }) { Text("Use for cards", fontSize = 12.sp) }
                        }
                        MotionKind.EMPHASIS -> Button(onClick = { Motion.choose("send", pickedFx, prefs) }) { Text("Use for the send button", fontSize = 12.sp) }
                        MotionKind.LOOP -> {
                            Button(onClick = { Motion.choose("logo", pickedFx, prefs) }) { Text("Use on the logo", fontSize = 12.sp) }
                            OutlinedButton(onClick = { Motion.choose("logo", null, prefs) }) { Text("No logo loop", fontSize = 12.sp) }
                        }
                    }
                }
            }
            if (kind != MotionKind.LOOP) OutlinedButton(onClick = { playAll++ }) { Text("Play all again") }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list, key = { it.name }) { fx -> MotionTile(fx, playAll, selected == fx) { selected = fx } }
        }
    }
}

@Composable
private fun MotionTile(fx: Fx, playAll: Int, chosen: Boolean, onChoose: () -> Unit) {
    var mine by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(BroColors.Panel).border(if (chosen) 2.dp else 1.dp, if (chosen) BroColors.Accent else BroColors.Outline, shape)
            .clickable { mine++; onChoose() }.padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fx(fx, playAll * 1000 + mine).size(44.dp)) { BroLogo(44.dp, animate = false) }
        }
        Text(fx.name, color = BroColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2)
        Text("${fx.ms} ms", color = BroColors.TextSecondary, fontSize = 11.sp)
    }
}
