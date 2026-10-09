package com.bro.assistant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {

    private val vm: ChatViewModel by viewModels()

    private var dialog by mutableStateOf<PermDialog?>(null)
    private var showSettings by mutableStateOf(false)
    private var showHistory by mutableStateOf(false)
    private var tick by mutableStateOf(0)

    private var pendingPermission: String? = null
    private var afterGrant: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleWake(intent)

        setContent {
            BroTheme {
                Box(Modifier.fillMaxSize()) {
                    if (showSettings) {
                        BackHandler { showSettings = false }
                        SettingsScreen(
                            vm = vm,
                            tick = tick,
                            onBack = { showSettings = false },
                            onRequest = { requestPermission(it) },
                            onWakeToggle = { setWake(it) }
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
                            onOpenHistory = { vm.refreshHistory(); showHistory = true }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWake(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.appVisible = true
        BroListenerService.setPaused(true) // free the microphone while BRO is on screen
        tick++
    }

    override fun onStop() {
        vm.appVisible = false
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
