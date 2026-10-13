package com.bro.assistant.ui

import android.Manifest
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
    onWakeToggle: (Boolean) -> Unit,
    onLockSetup: (String) -> Unit = {},
    onMailSetup: () -> Unit = {}
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
    val uriHandler = LocalUriHandler.current
    val provKeys = remember {
        mutableStateMapOf<String, String>().apply {
            com.bro.assistant.ai.Provider.entries.filter { it != com.bro.assistant.ai.Provider.GEMINI }
                .forEach { put(it.id, prefs.providerKey(it.id)) }
        }
    }
    var saved by remember { mutableStateOf(false) }
    var openSection by remember { mutableStateOf("") }
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
        Spacer(Modifier.height(8.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            prefs.apiKey = apiKey
            provKeys.forEach { (id, v) -> prefs.setProviderKey(id, v) }
            prefs.model = model
            prefs.githubToken = githubToken
            prefs.wakePhrases = wakePhrases
            prefs.countryCode = countryCode
            prefs.confirmBeforeSend = confirmSend
            prefs.speakReplies = speakReplies
            saved = true
        }) { Text(if (saved) "Saved" else "Save") }
        Text("Tap a heading to open it. Tap Save after changing keys or switches.", color = Color.Gray, fontSize = 12.sp)

        SectionHeader("AI (needs internet)", openSection == "AI (needs internet)") { openSection = if (openSection == "AI (needs internet)") "" else "AI (needs internet)" }
        if (openSection == "AI (needs internet)") {
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it; saved = false },
            label = { Text("Gemini API key (several keys: separate with commas)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Tip: paste more than one free key, separated by commas. When one key runs out of free usage, BRO switches to the next one by itself.",
            color = Color.Gray, fontSize = 12.sp
        )
        TextButton(onClick = { uriHandler.openUri(com.bro.assistant.ai.Provider.GEMINI.signup) }) { Text("Get a free Gemini key") }
        OutlinedTextField(
            value = model, onValueChange = { model = it; saved = false },
            label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        }
        SectionHeader("More free AI keys (BRO splits big tasks between them)", openSection == "More free AI keys (BRO splits big tasks between them)") { openSection = if (openSection == "More free AI keys (BRO splits big tasks between them)") "" else "More free AI keys (BRO splits big tasks between them)" }
        if (openSection == "More free AI keys (BRO splits big tasks between them)") {
        Text(
            "Say \"divide <your big task>\" and BRO splits it, then gives coding to the best coder, quick parts to the fastest, " +
                "long text to Gemini, and so on. If one key runs out, the next one takes over. " +
                "Keys stay on this phone, encrypted. Say \"ai status\" to see who does what.",
            color = Color.Gray, fontSize = 12.sp
        )
        com.bro.assistant.ai.Provider.entries.filter { it != com.bro.assistant.ai.Provider.GEMINI }.forEach { p ->
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = provKeys[p.id] ?: "", onValueChange = { provKeys[p.id] = it; saved = false },
                label = { Text(p.label + " key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(p.strong, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { uriHandler.openUri(p.signup) }) { Text("Get key") }
            }
        }

        }
        SectionHeader("Offline brain (no internet, no API key)", openSection == "Offline brain (no internet, no API key)") { openSection = if (openSection == "Offline brain (no internet, no API key)") "" else "Offline brain (no internet, no API key)" }
        if (openSection == "Offline brain (no internet, no API key)") {
        OfflineBrainSection(prefs)

        }
        SectionHeader("Security (BRO lock)", openSection == "Security (BRO lock)") { openSection = if (openSection == "Security (BRO lock)") "" else "Security (BRO lock)" }
        if (openSection == "Security (BRO lock)") {
        val lockType = remember(tick) { com.bro.assistant.security.LockStore.type(ctx) }
        val lockOn = remember(tick) { com.bro.assistant.security.LockStore.isSet(ctx) }
        var bio by remember { mutableStateOf(prefs.lockBiometric) }
        Text(
            if (lockOn) "BRO lock is ON: " + lockType + ". It is asked when BRO opens, after you were away 30 seconds, and before risky taps in other apps."
            else "BRO lock is OFF. Choose a PIN, password or pattern. It belongs to BRO only, not your phone.",
            color = if (lockOn) Color(0xFF66BB6A) else Color.LightGray, fontSize = 13.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onLockSetup("pin") }) { Text("PIN") }
            OutlinedButton(onClick = { onLockSetup("password") }) { Text("Password") }
            OutlinedButton(onClick = { onLockSetup("pattern") }) { Text("Pattern") }
        }
        if (lockOn) {
            OutlinedButton(onClick = { onLockSetup("off") }) { Text("Turn lock off") }
            if (com.bro.assistant.security.AppLock.biometricAvailable(ctx)) {
                SwitchRow("Also unlock with fingerprint", bio) { bio = it; prefs.lockBiometric = it }
            }
        }
        Text("Signed in as ${prefs.accountEmail.ifBlank { "(not signed in)" }}", color = Color.White, fontSize = 13.sp)
        val mailOn = remember(tick) { com.bro.assistant.security.MailRecovery.isConfigured(prefs) }
        Text(
            if (mailOn) "Forgot-lock recovery by email: ON. A code is sent to your Gmail." else "Forgot-lock recovery by email: OFF.",
            color = if (mailOn) Color(0xFF66BB6A) else Color.LightGray, fontSize = 13.sp
        )
        OutlinedButton(onClick = onMailSetup) { Text(if (mailOn) "Email recovery settings" else "Set up email recovery") }
        Text("If you forget the lock, the lock screen's Forgot link accepts your fingerprint or the emailed code.", color = Color.Gray, fontSize = 12.sp)

        }
        SectionHeader("GitHub", openSection == "GitHub") { openSection = if (openSection == "GitHub") "" else "GitHub" }
        if (openSection == "GitHub") {
        OutlinedTextField(
            value = githubToken, onValueChange = { githubToken = it; saved = false },
            label = { Text("GitHub token (needs repo + workflow)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )

        }
        SectionHeader("Messaging", openSection == "Messaging") { openSection = if (openSection == "Messaging") "" else "Messaging" }
        if (openSection == "Messaging") {
        OutlinedTextField(
            value = countryCode, onValueChange = { countryCode = it; saved = false },
            label = { Text("Default country code (digits only)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        SwitchRow("Ask before sending a message", confirmSend) { confirmSend = it; saved = false }

        }
        SectionHeader("Voice", openSection == "Voice") { openSection = if (openSection == "Voice") "" else "Voice" }
        if (openSection == "Voice") {
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

        if (com.bro.assistant.security.LockStore.isSet(ctx)) {
            Heading("Voice unlock (optional)")
            var voiceSet by remember(tick) { mutableStateOf(com.bro.assistant.security.VoiceLock.isSet(ctx)) }
            var phrase by remember { mutableStateOf("") }
            var voiceMsg by remember { mutableStateOf("") }
            Text("Unlock BRO by saying a secret phrase of at least three words. It is less safe than your PIN: anyone who overhears it, or plays a recording, could unlock BRO. It never confirms risky actions.", color = Color.Gray, fontSize = 12.sp)
            OutlinedTextField(
                value = phrase, onValueChange = { phrase = it; voiceMsg = "" }, singleLine = true,
                label = { Text(if (voiceSet) "New secret phrase" else "Secret phrase") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (com.bro.assistant.security.VoiceLock.set(ctx, phrase)) { voiceSet = true; phrase = ""; voiceMsg = "Saved. The lock screen now has a \"Say phrase\" button." }
                    else voiceMsg = "Use at least three words."
                }) { Text("Save phrase") }
                if (voiceSet) OutlinedButton(onClick = { com.bro.assistant.security.VoiceLock.clear(ctx); voiceSet = false; voiceMsg = "Voice unlock removed." }) { Text("Remove") }
            }
            if (voiceMsg.isNotEmpty()) Text(voiceMsg, color = Color.LightGray, fontSize = 12.sp)
        }

        }
        SectionHeader("Morning briefing and alerts", openSection == "Morning briefing and alerts") { openSection = if (openSection == "Morning briefing and alerts") "" else "Morning briefing and alerts" }
        if (openSection == "Morning briefing and alerts") {
        var city by remember(tick) { mutableStateOf(prefs.city) }
        OutlinedTextField(
            value = city, onValueChange = { city = it; prefs.city = it },
            label = { Text("Your city (for the weather)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text("Say \"good morning\" and BRO tells you the time, your next alarm, the weather, today's calendar and what it finished while you were away.", color = Color.Gray, fontSize = 12.sp)
        var proactiveOn by remember(tick) { mutableStateOf(prefs.proactiveEnabled) }
        SwitchRow("Let BRO speak up by itself", proactiveOn) { on ->
            proactiveOn = on; prefs.proactiveEnabled = on
            if (on) com.bro.assistant.proactive.Proactive.schedule(ctx) else com.bro.assistant.proactive.Proactive.cancel(ctx)
        }
        Text("Low battery warnings, an evening heads-up about tomorrow's exams or events, and a warning an hour before an event. Checked about every 15 minutes (Android may delay it). Spoken aloud only while the phone is awake.", color = Color.Gray, fontSize = 12.sp)
        PermRow("Calendar (for exam and event alerts)", com.bro.assistant.proactive.CalendarReader.allowed(ctx)) { onRequest(Manifest.permission.READ_CALENDAR) }

        }
        SectionHeader("BRO's memory", openSection == "BRO's memory") { openSection = if (openSection == "BRO's memory") "" else "BRO's memory" }
        if (openSection == "BRO's memory") {
        var facts by remember(tick) { mutableStateOf(com.bro.assistant.memory.MemoryStore.all(ctx)) }
        Text("Say \"remember that my mom's number is ...\", \"remember my timetable: ...\", or \"what do you remember\". BRO never stores passwords, PINs or OTPs.", color = Color.Gray, fontSize = 12.sp)
        if (facts.isEmpty()) Text("Nothing saved yet.", color = Color.LightGray, fontSize = 13.sp)
        facts.forEach { f ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(f.text, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { com.bro.assistant.memory.MemoryStore.remove(ctx, f.id); facts = com.bro.assistant.memory.MemoryStore.all(ctx) }) { Text("Delete") }
            }
        }
        if (facts.isNotEmpty()) OutlinedButton(onClick = { com.bro.assistant.memory.MemoryStore.clear(ctx); facts = emptyList() }) { Text("Forget everything") }

        }
        SectionHeader("Messages and driving", openSection == "Messages and driving") { openSection = if (openSection == "Messages and driving") "" else "Messages and driving" }
        if (openSection == "Messages and driving") {
        val listenerOn = remember(tick) { com.bro.assistant.features.MessageReplies.enabled(ctx) }
        PermRow("Notification access (read and reply to messages)", listenerOn) {
            com.bro.assistant.security.LockGrace.allowFor(5 * 60_000L)
            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        var readMsgs by remember(tick) { mutableStateOf(prefs.readMessages) }
        SwitchRow("Read new messages aloud (when the phone is awake)", readMsgs) { readMsgs = it; prefs.readMessages = it }
        Text("Works with WhatsApp, Telegram and SMS. Say \"reply yes\", \"reply later\", \"reply <your words>\" or \"call back\". BRO asks before sending unless you turned that off.", color = Color.Gray, fontSize = 12.sp)
        var carName by remember(tick) { mutableStateOf(prefs.driveBtName) }
        OutlinedTextField(
            value = carName, onValueChange = { carName = it; prefs.driveBtName = it },
            label = { Text("Car Bluetooth name (starts drive mode)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (Build.VERSION.SDK_INT >= 31) PermRow("Bluetooth (to notice your car)", com.bro.assistant.PermissionManager.has(ctx, Manifest.permission.BLUETOOTH_CONNECT)) { onRequest(Manifest.permission.BLUETOOTH_CONNECT) }
        Text("Or just say \"drive mode\". Drive mode reads messages aloud even with the screen off and keeps the microphone on (needs the floating circle permission).", color = Color.Gray, fontSize = 12.sp)

        }
        SectionHeader("BRO calls me", openSection == "BRO calls me") { openSection = if (openSection == "BRO calls me") "" else "BRO calls me" }
        if (openSection == "BRO calls me") {
        val callScope = androidx.compose.runtime.rememberCoroutineScope()
        var twSid by remember { mutableStateOf(prefs.twilioSid) }
        var twTok by remember { mutableStateOf("") }
        var twFrom by remember { mutableStateOf(prefs.twilioFrom) }
        var myNum by remember { mutableStateOf(prefs.myNumber) }
        var callVoice by remember { mutableStateOf(prefs.callVoice) }
        var callRem by remember { mutableStateOf(prefs.callForReminders) }
        var callMsg by remember { mutableStateOf("") }
        Text(
            "FREE: say \"call me\" and BRO rings inside the app with a full-screen call. Answer it and BRO talks and listens to you. " +
                "Reminders ring the same way. Nothing to set up. " +
                "OPTIONAL (Twilio, one-way, a trial account only plays Twilio's sample message): ring your real phone number. Only fill in the boxes below if you want that.",
            color = Color.Gray, fontSize = 12.sp
        )
        OutlinedTextField(value = twSid, onValueChange = { twSid = it }, label = { Text("Twilio Account SID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = twTok, onValueChange = { twTok = it },
            label = { Text(if (prefs.twilioToken.isNotBlank()) "Auth token (saved, type to replace)" else "Twilio Auth token") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(value = twFrom, onValueChange = { twFrom = it }, label = { Text("Twilio phone number (+1...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = myNum, onValueChange = { myNum = it }, label = { Text("Your number to call") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = callVoice, onValueChange = { callVoice = it }, label = { Text("Voice name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        var ringRem by remember { mutableStateOf(prefs.reminderRings) }
        SwitchRow("Reminders ring like a BRO call (answer and talk, free)", ringRem) { ringRem = it; prefs.reminderRings = it }
        if (!com.bro.assistant.features.BroCall.canFullScreen(ctx)) {
            Text("Android is blocking full-screen calls. Allow it so the BRO call screen can appear over the lock screen.", color = Color.Gray, fontSize = 12.sp)
            OutlinedButton(onClick = {
                try {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) { com.bro.assistant.PermissionManager.openAppSettings(ctx) }
            }) { Text("Allow full-screen calls") }
        }
        Text("Say \"call me\" (without Twilio) or \"call me here\" and BRO rings inside the app. Answer it and BRO listens to you.", color = Color.Gray, fontSize = 12.sp)
        var viaTw by remember { mutableStateOf(prefs.callViaTwilio) }
        SwitchRow("Use Twilio for \"call me\" (optional)", viaTw) { viaTw = it; prefs.callViaTwilio = it }
        SwitchRow("Also call me when a reminder goes off", callRem) { callRem = it; prefs.callForReminders = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                prefs.twilioSid = twSid; prefs.twilioFrom = twFrom; prefs.myNumber = myNum; prefs.callVoice = callVoice
                if (twTok.isNotBlank()) { prefs.twilioToken = com.bro.assistant.security.SecretBox.encrypt(twTok.trim()); twTok = "" }
                callMsg = "Saved."
            }) { Text("Save call settings") }
            OutlinedButton(onClick = {
                callMsg = "Calling..."
                callScope.launch {
                    callMsg = com.bro.assistant.features.CallMe.place(ctx, "Hello boss, this is BRO. Your call setup works.") ?: "Call placed. Your phone should ring shortly."
                }
            }) { Text("Test call") }
        }
        if (callMsg.isNotEmpty()) Text(callMsg, color = Color.LightGray, fontSize = 12.sp)

        }
        SectionHeader("BRO learns", openSection == "BRO learns") { openSection = if (openSection == "BRO learns") "" else "BRO learns" }
        if (openSection == "BRO learns") {
            var nightly by remember(tick) { mutableStateOf(prefs.learnNightly) }
            var notes by remember(tick) { mutableStateOf(com.bro.assistant.learn.LearnStore.count(ctx)) }
            var learnMsg by remember { mutableStateOf("") }
            var learning by remember { mutableStateOf(false) }
            val learnScope = androidx.compose.runtime.rememberCoroutineScope()
            SwitchRow("Study by itself at night (12 to 3)", nightly) { on ->
                nightly = on; prefs.learnNightly = on
                com.bro.assistant.learn.LearnJob.schedule(ctx)
            }
            Text("BRO writes study notes on coding, Telugu and Tenglish using your AI keys or the offline brain, and reads them before answering. It only runs when the phone is idle. Notes saved: $notes", color = Color.Gray, fontSize = 12.sp)
            Button(enabled = !learning, onClick = {
                learning = true; learnMsg = "Studying..."
                learnScope.launch {
                    val done = try { com.bro.assistant.learn.Learner.session(ctx, prefs, 3, 4 * 60_000L) { learnMsg = it } } catch (e: Exception) { emptyList() }
                    learnMsg = if (done.isEmpty()) "Could not study now (no key or internet)." else "Learned ${done.size} topics."
                    notes = com.bro.assistant.learn.LearnStore.count(ctx); learning = false
                }
            }) { Text("Learn now") }
            if (learnMsg.isNotEmpty()) Text(learnMsg, color = Color.LightGray, fontSize = 12.sp)
            Button(onClick = { com.bro.assistant.learn.LearnStore.clear(ctx); notes = 0 }) { Text("Forget lessons") }
        }
        SectionHeader("Floating circle", openSection == "Floating circle") { openSection = if (openSection == "Floating circle") "" else "Floating circle" }
        if (openSection == "Floating circle") {
        var bubbleOn by remember(tick) { mutableStateOf(prefs.bubbleEnabled && android.provider.Settings.canDrawOverlays(ctx)) }
        SwitchRow("Show BRO's circle on every screen", bubbleOn) { on ->
            if (on && !android.provider.Settings.canDrawOverlays(ctx)) {
                com.bro.assistant.security.LockGrace.allowFor(5 * 60_000L)
                ctx.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + ctx.packageName)
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                prefs.bubbleEnabled = true
            } else {
                bubbleOn = on; prefs.bubbleEnabled = on
                if (on) com.bro.assistant.BubbleService.start(ctx) else com.bro.assistant.BubbleService.stop(ctx)
            }
        }
        Text(
            "A round light that floats over other apps. It cycles through 100 colours while idle, turns purple when listening, " +
                "orange when thinking, green when a task is done and red when it fails. Drag it to move it, tap it to open BRO.",
            color = Color.Gray, fontSize = 12.sp
        )

        }
        SectionHeader("Wake phrase", openSection == "Wake phrase") { openSection = if (openSection == "Wake phrase") "" else "Wake phrase" }
        if (openSection == "Wake phrase") {
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


        }
        SectionHeader("Permissions", openSection == "Permissions") { openSection = if (openSection == "Permissions") "" else "Permissions" }
        if (openSection == "Permissions") {
        PermRow("Accessibility service", accessibilityOn) { onRequest(PermissionManager.ACCESSIBILITY) }
        PermRow("Microphone", micOn) { onRequest(Manifest.permission.RECORD_AUDIO) }
        PermRow(
            "Notifications", notificationsOn || Build.VERSION.SDK_INT < 33
        ) { onRequest(Manifest.permission.POST_NOTIFICATIONS) }
        PermRow("Contacts", contactsOn) { onRequest(Manifest.permission.READ_CONTACTS) }
        PermRow("Phone calls", callsOn) { onRequest(Manifest.permission.CALL_PHONE) }
        PermRow("Display over other apps (helps wake open BRO)", overlayOn) { onRequest(PermissionManager.OVERLAY) }
        }
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
private fun SectionHeader(text: String, open: Boolean, onClick: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x3308162B))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = BroColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(if (open) "\u25B2" else "\u25BC", color = BroColors.Accent, fontSize = 14.sp)
    }
    Spacer(Modifier.height(4.dp))
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
