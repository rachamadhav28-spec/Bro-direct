package com.bro.assistant.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.ChatViewModel
import com.bro.assistant.PermissionManager
import com.bro.assistant.ui.theme.BroColors

@Composable
fun SettingsScreen(
    vm: ChatViewModel,
    tick: Int,
    onBack: () -> Unit,
    onRequest: (String) -> Unit,
    onWakeToggle: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val prefs = vm.prefs

    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var model by remember { mutableStateOf(prefs.model) }
    var wakePhrases by remember { mutableStateOf(prefs.wakePhrases) }
    var countryCode by remember { mutableStateOf(prefs.countryCode) }
    var confirmSend by remember { mutableStateOf(prefs.confirmBeforeSend) }
    var speakReplies by remember { mutableStateOf(prefs.speakReplies) }
    var voiceLabel by remember { mutableStateOf(prefs.voiceName.ifBlank { "Default (lower pitch)" }) }
    var speechLang by remember { mutableStateOf(prefs.language) }
    var githubToken by remember { mutableStateOf(prefs.githubToken) }
    var saved by remember { mutableStateOf(false) }
    val wakeOn = remember(tick) { prefs.wakeEnabled }

    val accessibilityOn = remember(tick) { PermissionManager.accessibilityEnabled(ctx) }
    val micOn = remember(tick) { PermissionManager.has(ctx, Manifest.permission.RECORD_AUDIO) }
    val notificationsOn = remember(tick) { PermissionManager.hasNotifications(ctx) }
    val contactsOn = remember(tick) { PermissionManager.has(ctx, Manifest.permission.READ_CONTACTS) }
    val callsOn = remember(tick) { PermissionManager.has(ctx, Manifest.permission.CALL_PHONE) }
    val overlayOn = remember(tick) { PermissionManager.canOverlay(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BroColors.Background)
            .systemBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = BroColors.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        Heading("AI (needs internet)")
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it; saved = false },
            label = { Text("Gemini API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it; saved = false },
            label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Heading("GitHub")
        OutlinedTextField(
            value = githubToken, onValueChange = { githubToken = it; saved = false },
            label = { Text("GitHub token (repo access)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )

        Heading("Messaging")
        OutlinedTextField(
            value = countryCode, onValueChange = { countryCode = it; saved = false },
            label = { Text("Default country code (digits only)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        SwitchRow("Ask before sending a message", confirmSend) { confirmSend = it; saved = false }

        Heading("Voice")
        SwitchRow("Speak replies", speakReplies) { speakReplies = it; saved = false }
        Text("Voice: $voiceLabel", color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val name = vm.tts.nextVoice()
                voiceLabel = name.ifBlank { "No voices found" }
            }) { Text("Next voice") }
            OutlinedButton(onClick = { vm.testVoice() }) { Text("Test voice") }
        }

        Text(
            "Speech language: " + if (speechLang.startsWith("te")) "Telugu (te-IN)" else "English / Tenglish (en-IN)",
            color = Color.White
        )
        OutlinedButton(onClick = {
            speechLang = if (speechLang.startsWith("te")) "en-IN" else "te-IN"
            prefs.language = speechLang
        }) { Text("Switch speech language") }

        Heading("Wake phrase")
        OutlinedTextField(
            value = wakePhrases, onValueChange = { wakePhrases = it; saved = false },
            label = { Text("Phrases, separated by commas") }, modifier = Modifier.fillMaxWidth()
        )
        SwitchRow("Listen for the wake phrase", wakeOn) { onWakeToggle(it) }
        Text(
            "Uses the microphone in the background with a notification and extra battery. " +
                "Set BRO's battery usage to \"No restrictions\" in phone settings so Android doesn't stop it.",
            color = Color.Gray, fontSize = 12.sp
        )

        Spacer(Modifier.height(10.dp))
        Button(onClick = {
            prefs.apiKey = apiKey
            prefs.model = model
            prefs.githubToken = githubToken
            prefs.wakePhrases = wakePhrases
            prefs.countryCode = countryCode
            prefs.confirmBeforeSend = confirmSend
            prefs.speakReplies = speakReplies
            saved = true
        }) { Text(if (saved) "Saved" else "Save") }

        Heading("Permissions")
        PermRow("Accessibility service", accessibilityOn) { onRequest(PermissionManager.ACCESSIBILITY) }
        PermRow("Microphone", micOn) { onRequest(Manifest.permission.RECORD_AUDIO) }
        PermRow(
            "Notifications", notificationsOn || Build.VERSION.SDK_INT < 33
        ) { onRequest(Manifest.permission.POST_NOTIFICATIONS) }
        PermRow("Contacts", contactsOn) { onRequest(Manifest.permission.READ_CONTACTS) }
        PermRow("Phone calls", callsOn) { onRequest(Manifest.permission.CALL_PHONE) }
        PermRow("Display over other apps (helps wake open BRO)", overlayOn) { onRequest(PermissionManager.OVERLAY) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, color = BroColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label: ${if (granted) "On" else "Off"}", color = Color.White, modifier = Modifier.weight(1f))
        if (!granted) OutlinedButton(onClick = onClick) { Text("Allow") }
    }
}
