package com.bro.assistant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.bro.assistant.ui.ChatScreen
import com.bro.assistant.ui.HistoryScreen
import com.bro.assistant.ui.SettingsScreen
import com.bro.assistant.ui.theme.BroTheme

private data class PermDialog(
    val title: String,
    val text: String,
    val positive: String,
    val onPositive: () -> Unit
)

class MainActivity : FragmentActivity() {

    private val vm: ChatViewModel by viewModels()

    private var dialog by mutableStateOf<PermDialog?>(null)
    private var showSettings by mutableStateOf(false)
    private var showHistory by mutableStateOf(false)
    private var tick by mutableStateOf(0)

    // app lock: BRO's own PIN / password / pattern (set in Settings), asked when BRO opens
    // or comes back after 30 seconds away, and before risky actions
    private var locked by mutableStateOf(false)
    private var lockFlow by mutableStateOf<String?>(null)       // "pin" | "password" | "pattern" | "off"
    private var verifyAction by mutableStateOf<(() -> Unit)?>(null)
    private var leftAt = 0L

    private fun lockEnabled() = com.bro.assistant.security.LockStore.isSet(this)
    private fun waitSeconds(): Int = ((com.bro.assistant.security.LockStore.waitMs(this) + 999) / 1000).toInt()
    private fun check(secret: String) = com.bro.assistant.security.LockStore.verify(this, secret)

    private fun biometricOffer(onOk: () -> Unit): (() -> Unit)? =
        if (vm.prefs.lockBiometric && com.bro.assistant.security.AppLock.biometricAvailable(this))
            ({ com.bro.assistant.security.AppLock.authenticateBiometric(this, "Unlock BRO", onSuccess = onOk) })
        else null

    // first-run Gmail sign-in, and lock recovery (fingerprint or emailed code)
    private var needLogin by mutableStateOf(false)
    private var recovery by mutableStateOf(false)
    private var mailSetup by mutableStateOf(false)

    private val accountLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val name = r.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!name.isNullOrBlank()) { vm.prefs.accountEmail = name; needLogin = false; tick++ }
        }

    private fun pickGoogleAccount(): Boolean = try {
        val i = com.google.android.gms.common.AccountPicker.newChooseAccountIntent(
            com.google.android.gms.common.AccountPicker.AccountChooserOptions.Builder()
                .setAllowableAccountsTypes(listOf("com.google")).build()
        )
        com.bro.assistant.security.LockGrace.allowFor(5 * 60_000L)
        accountLauncher.launch(i)
        true
    } catch (e: Exception) { false }

    /** Runs [action] only after the BRO lock is entered (used before risky actions BRO wants to take). */
    private fun secure(action: () -> Unit) {
        if (!lockEnabled()) { action(); return }
        verifyAction = action
    }

    private var pendingPermission: String? = null
    private var afterGrant: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val crashPrefs = getSharedPreferences("bro_crash", MODE_PRIVATE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                crashPrefs.edit().putString("last", (e.toString() + "\n" + e.stackTrace.take(4).joinToString("\n")).take(600)).commit()
            } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
        crashPrefs.getString("last", null)?.let {
            crashPrefs.edit().remove("last").apply()
            vm.reportCrash(it)
        }
        needLogin = vm.prefs.accountEmail.isBlank()
        locked = lockEnabled()
        showSettings = savedInstanceState?.getBoolean("s") ?: false
        showHistory = savedInstanceState?.getBoolean("h") ?: false
        handleWake(intent)

        setContent {
            BroTheme {
                Box(Modifier.fillMaxSize()) {
                    if (needLogin) {
                        com.bro.assistant.ui.LoginScreen(
                            onPickGoogle = { pickGoogleAccount() },
                            onTyped = { vm.prefs.accountEmail = it; needLogin = false; tick++ }
                        )
                    } else if (recovery) {
                        BackHandler { recovery = false }
                        com.bro.assistant.ui.RecoveryScreen(
                            prefs = vm.prefs,
                            fingerprintAvailable = com.bro.assistant.security.AppLock.biometricAvailable(this@MainActivity),
                            onFingerprint = {
                                com.bro.assistant.security.AppLock.authenticateBiometric(this@MainActivity, "Reset BRO lock", onSuccess = {
                                    com.bro.assistant.security.LockStore.clear(this@MainActivity)
                                    locked = false; recovery = false; tick++
                                })
                            },
                            onVerified = {
                                com.bro.assistant.security.LockStore.clear(this@MainActivity)
                                locked = false; recovery = false; tick++
                            },
                            onCancel = { recovery = false }
                        )
                    } else if (mailSetup) {
                        BackHandler { mailSetup = false }
                        com.bro.assistant.ui.MailSetupScreen(
                            prefs = vm.prefs,
                            onOpenGooglePage = {
                                com.bro.assistant.security.LockGrace.allowFor(10 * 60_000L)
                                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://myaccount.google.com/apppasswords")))
                            },
                            onDone = { mailSetup = false; tick++ }
                        )
                    } else if (locked) {
                        com.bro.assistant.ui.BroLockScreen(
                            type = com.bro.assistant.security.LockStore.type(this@MainActivity),
                            message = "BRO is locked",
                            onSubmit = { secret -> check(secret).also { ok -> if (ok) locked = false } },
                            waitSeconds = { waitSeconds() },
                            onBiometric = biometricOffer { locked = false },
                            onForgot = { recovery = true }
                        )
                    } else if (lockFlow != null) {
                        BackHandler { lockFlow = null }
                        val flow = lockFlow!!
                        com.bro.assistant.ui.LockSetupFlow(
                            newType = if (flow == "off") com.bro.assistant.security.LockStore.type(this@MainActivity) else flow,
                            hasOld = lockEnabled(),
                            oldType = com.bro.assistant.security.LockStore.type(this@MainActivity),
                            verifyOld = { check(it) },
                            waitSeconds = { waitSeconds() },
                            onSave = { secret ->
                                if (flow == "off") com.bro.assistant.security.LockStore.clear(this@MainActivity)
                                else com.bro.assistant.security.LockStore.set(this@MainActivity, flow, secret)
                                lockFlow = null; tick++
                            },
                            onCancel = { lockFlow = null }
                        )
                    } else if (verifyAction != null) {
                        com.bro.assistant.ui.BroLockScreen(
                            type = com.bro.assistant.security.LockStore.type(this@MainActivity),
                            message = "Confirm this action",
                            onSubmit = { secret -> check(secret).also { ok -> if (ok) { val a = verifyAction; verifyAction = null; a?.invoke() } } },
                            waitSeconds = { waitSeconds() },
                            onBiometric = biometricOffer { val a = verifyAction; verifyAction = null; a?.invoke() },
                            onCancel = { verifyAction = null }
                        )
                    } else if (showSettings) {
                        BackHandler { showSettings = false }
                        SettingsScreen(
                            vm = vm,
                            tick = tick,
                            onBack = { showSettings = false },
                            onRequest = { requestPermission(it) },
                            onWakeToggle = { setWake(it) },
                            onLockSetup = { lockFlow = it },
                            onMailSetup = { mailSetup = true }
                        )
                    } else if (showHistory) {
                        BackHandler { showHistory = false }
                        HistoryScreen(
                            vm = vm,
                            onBack = { showHistory = false },
                            onOpen = { showHistory = false }
                        )
                    } else {
                        ChatScreen(
                            vm = vm,
                            onMic = { onMicTapped() },
                            onOpenSettings = { showSettings = true },
                            onOpenHistory = { vm.refreshHistory(); showHistory = true },
                            onSecureConfirm = { action -> secure(action) }
                        )
                    }

                    val needed = vm.permissionNeeded
                    LaunchedEffect(needed) {
                        if (needed != null) {
                            vm.permissionNeeded = null
                            requestPermission(needed)
                        }
                    }

                    dialog?.let { d ->
                        AlertDialog(
                            onDismissRequest = { dialog = null },
                            title = { Text(d.title) },
                            text = { Text(d.text) },
                            confirmButton = { TextButton(onClick = d.onPositive) { Text(d.positive) } },
                            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Not now") } }
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("s", showSettings)
        outState.putBoolean("h", showHistory)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWake(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.appVisible = true
        BroListenerService.setPaused(true) // free the microphone while BRO is on screen
        if (!locked && leftAt > 0 && lockEnabled() && !com.bro.assistant.security.LockGrace.active() &&
            android.os.SystemClock.elapsedRealtime() - leftAt > 30_000L) locked = true
        // the offline model was released while BRO was away: load it again in the background
        vm.warmUpBrain()
        if (vm.prefs.bubbleEnabled) BubbleService.start(this)
        tick++
    }

    override fun onStop() {
        vm.appVisible = false
        leftAt = android.os.SystemClock.elapsedRealtime()
        vm.freeBrainMemory()
        if (vm.prefs.wakeEnabled) BroListenerService.setPaused(false)
        super.onStop()
    }

    private fun handleWake(i: Intent?) {
        if (i != null && i.getBooleanExtra(BroListenerService.EXTRA_WAKE, false)) {
            i.removeExtra(BroListenerService.EXTRA_WAKE)
            vm.onWake()
        }
    }

    // ---------- microphone ----------

    private fun onMicTapped() {
        if (vm.state == BroState.LISTENING) {
            vm.stopListening()
            return
        }
        ensurePermission(Manifest.permission.RECORD_AUDIO, reasonFor(Manifest.permission.RECORD_AUDIO)) {
            vm.startListening()
        }
    }

    private fun setWake(enable: Boolean) {
        if (!enable) {
            vm.prefs.wakeEnabled = false
            BroListenerService.stop(this)
            tick++
            return
        }
        ensurePermission(
            Manifest.permission.RECORD_AUDIO,
            "BRO needs the microphone to listen for your wake phrase."
        ) {
            vm.prefs.wakeEnabled = true
            BroListenerService.setPaused(true)
            BroListenerService.start(this)
            tick++
        }
    }

    // ---------- permissions ----------

    private fun requestPermission(permission: String) {
        when (permission) {
            PermissionManager.ACCESSIBILITY -> dialog = PermDialog(
                "Accessibility needed",
                "BRO uses the Accessibility service to tap buttons and type inside other apps, only when you ask. " +
                    "On the next screen, find BRO in the list and turn it on.",
                "Open settings"
            ) {
                dialog = null
                PermissionManager.openAccessibilitySettings(this)
            }
            PermissionManager.OVERLAY -> dialog = PermDialog(
                "Display over other apps",
                "This lets BRO open its screen when it hears the wake phrase while another app is in front.",
                "Open settings"
            ) {
                dialog = null
                PermissionManager.openOverlaySettings(this)
            }
            else -> {
                if (permission == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < 33) {
                    tick++
                    return
                }
                ensurePermission(permission, reasonFor(permission)) {
                    if (permission != Manifest.permission.RECORD_AUDIO) vm.onPermissionGranted()
                    tick++
                }
            }
        }
    }

    private fun ensurePermission(permission: String, reason: String, onGranted: () -> Unit) {
        if (PermissionManager.has(this, permission)) {
            onGranted()
            return
        }
        dialog = PermDialog("Permission needed", reason, "Continue") {
            dialog = null
            pendingPermission = permission
            afterGrant = onGranted
            permissionLauncher.launch(permission)
        }
    }

    private fun onPermissionResult(granted: Boolean) {
        val permission = pendingPermission ?: return
        val callback = afterGrant
        pendingPermission = null
        afterGrant = null
        tick++
        if (granted) {
            callback?.invoke()
        } else if (!shouldShowRequestPermissionRationale(permission)) {
            dialog = PermDialog(
                "Permission blocked",
                "This permission was turned off permanently. Open app settings and allow it there.",
                "Open settings"
            ) {
                dialog = null
                PermissionManager.openAppSettings(this)
            }
        } else {
            vm.notifyPermissionDenied()
        }
    }

    private fun reasonFor(permission: String): String = when (permission) {
        Manifest.permission.RECORD_AUDIO -> "BRO needs the microphone to hear your voice commands."
        Manifest.permission.READ_CONTACTS -> "BRO needs your contacts to find the person you name."
        Manifest.permission.CALL_PHONE -> "BRO needs permission to place calls when you ask."
        Manifest.permission.POST_NOTIFICATIONS -> "BRO uses notifications to tell you when a background task finishes."
        else -> "BRO needs this permission to do what you asked."
    }
}
