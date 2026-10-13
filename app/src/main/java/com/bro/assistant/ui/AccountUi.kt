package com.bro.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.security.MailRecovery
import com.bro.assistant.security.SecretBox
import kotlinx.coroutines.launch

@Composable
private fun Page(content: @Composable () -> Unit) {
    FuturisticBackground {
        Column(
            Modifier.fillMaxSize().padding(26.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    }
}

/** Shown the very first time BRO opens: pick a Google account from the phone's own account chooser (not a web page). */
@Composable
fun LoginScreen(onPickGoogle: () -> Boolean, onTyped: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    Page {
        BroWordmark(42.sp, 6.sp)
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier.fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .background(Color(0xFF121212))
                .padding(22.dp)
        ) {
            Text("Sign in with Google", color = Color.LightGray, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Text("Choose an account", color = Color.White, fontSize = 26.sp)
            Spacer(Modifier.height(6.dp))
            Text("to continue to BRO", color = Color(0xFF8AB4F8), fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Your phone will show the Google accounts on it. BRO keeps only the email you pick, to help you recover your BRO lock.",
                color = Color.Gray, fontSize = 13.sp
            )
            Spacer(Modifier.height(18.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (!onPickGoogle()) { error = "The phone's account chooser is not available. Type your Gmail below."; manual = true } }
            ) { Text("Choose an account") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { manual = !manual }) { Text("Use another account") }
            if (manual) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it.trim(); error = "" }, singleLine = true,
                    label = { Text("Gmail address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    if (Regex("^[A-Za-z0-9._%+-]+@gmail\\.com$", RegexOption.IGNORE_CASE).matches(typed)) onTyped(typed)
                    else error = "Please type a valid Gmail address (name@gmail.com)."
                }) { Text("Continue") }
            }
            if (error.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(error, color = Color(0xFFEF5350), fontSize = 13.sp) }
        }
    }
}

/** Forgot the BRO lock: prove it is you with your fingerprint, or with a code emailed to your Gmail. */
@Composable
fun RecoveryScreen(
    prefs: PreferencesStore,
    fingerprintAvailable: Boolean,
    onFingerprint: () -> Unit,
    onVerified: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf("menu") }
    var code by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val mailReady = MailRecovery.isConfigured(prefs)

    fun send() {
        busy = true; msg = "Sending the code..."
        scope.launch {
            val err = MailRecovery.sendCode(prefs)
            busy = false
            if (err == null) { stage = "code"; msg = "Code sent to ${MailRecovery.mask(prefs.accountEmail)}. Check your inbox." } else msg = err
        }
    }

    Page {
        Text("Forgot your BRO lock?", color = NeonCyan, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        if (stage == "menu") {
            if (fingerprintAvailable) { Button(onClick = onFingerprint) { Text("Use my fingerprint") }; Spacer(Modifier.height(10.dp)) }
            if (mailReady) Button(enabled = !busy, onClick = { send() }) { Text("Email me a code") }
            if (!fingerprintAvailable && !mailReady) {
                Text(
                    "Neither fingerprint nor email recovery is set up on this phone. The only way back in is Android Settings, Apps, BRO, Storage, Clear data. " +
                        "That also erases BRO's saved keys and chats.",
                    color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center
                )
            } else if (!mailReady) {
                Spacer(Modifier.height(6.dp))
                Text("Email recovery is not set up yet (Settings, Security).", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            Text(msg, color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                label = { Text("6-digit code") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = code.length == 6, onClick = {
                    if (MailRecovery.verify(code)) onVerified() else { msg = "Wrong or expired code."; code = "" }
                }) { Text("Verify") }
                OutlinedButton(enabled = !busy, onClick = { send() }) { Text("Send again") }
            }
        }
        if (stage == "menu" && msg.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(msg, color = Color(0xFFEF5350), fontSize = 13.sp, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onCancel) { Text("Back") }
    }
}

/** One-time setup so BRO can email recovery codes from your Gmail to your Gmail. */
@Composable
fun MailSetupScreen(prefs: PreferencesStore, onOpenGooglePage: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf(MailRecovery.isConfigured(prefs)) }

    Page {
        Text("Email recovery", color = NeonCyan, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "BRO can email a one-time code to ${prefs.accountEmail.ifBlank { "your Gmail" }} if you forget your lock. " +
                "A phone app cannot send email by itself, so you create a Google app password once:\n\n" +
                "1. Tap the button below (2-step verification must be on for your Google account).\n" +
                "2. Create an app password named BRO and copy the 16 letters.\n" +
                "3. Paste it here and tap Save.\n\n" +
                "BRO stores it encrypted on this phone and uses it only to email you codes. " +
                "Keep it private: it works like a key to that Gmail. You can delete it any time on the same Google page.",
            color = Color.LightGray, fontSize = 13.sp
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onOpenGooglePage) { Text("Open Google app passwords") }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it; msg = "" }, singleLine = true,
            label = { Text("App password (16 letters)") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && pass.replace(" ", "").length >= 12, onClick = {
                busy = true; msg = "Sending a test email..."
                scope.launch {
                    val err = MailRecovery.sendCode(prefs, testOnly = true, appPasswordOverride = pass)
                    busy = false
                    if (err == null) {
                        prefs.mailSecret = SecretBox.encrypt(pass.replace(" ", "")); configured = true; pass = ""
                        msg = "Saved. A test code was sent to ${MailRecovery.mask(prefs.accountEmail)}. Email recovery is ON."
                    } else msg = err
                }
            }) { Text("Save and send test") }
            if (configured) OutlinedButton(onClick = { prefs.mailSecret = ""; configured = false; msg = "Email recovery removed from BRO." }) { Text("Remove") }
        }
        if (msg.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(msg, color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onDone) { Text("Done") }
    }
}
