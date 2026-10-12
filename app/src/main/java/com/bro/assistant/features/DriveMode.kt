package com.bro.assistant.features

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.bro.assistant.BubbleService
import com.bro.assistant.memory.PreferencesStore

/** Driving: BRO reads new messages aloud and keeps the microphone on, so you can answer without touching the phone. */
object DriveMode {
    private val I = RegexOption.IGNORE_CASE
    private val onRx = Regex("""^(?:start\s+|turn\s+on\s+|enable\s+)?driv(?:e|ing)\s*mode(?:\s+on)?[.!\s]*$|^i(?:'m|\s+am)\s+driving[.!\s]*$""", I)
    private val offRx = Regex("""^(?:stop|end|turn\s+off|disable|exit)\s+driv(?:e|ing)\s*mode[.!\s]*$|^driv(?:e|ing)\s*mode\s+off[.!\s]*$|^i(?:'m|\s+am)\s+(?:home|not\s+driving|done\s+driving|parked)[.!\s]*$""", I)

    @Volatile var active = false
        private set

    fun start(c: Context): String {
        active = true
        val canListen = Settings.canDrawOverlays(c)
        if (canListen) { BubbleService.wantListen = true; BubbleService.start(c) }
        return "Drive mode on. I'll read new messages aloud" +
            (if (canListen) " and keep listening, so just say \"reply yes\", \"call back\" or any command."
            else ". For hands-free listening, allow \"Display over other apps\" for BRO.") +
            " Say \"stop driving mode\" when you're done. Please keep your eyes on the road, boss."
    }

    fun stop(c: Context): String {
        active = false
        BubbleService.stopListening()
        return "Drive mode off. Welcome back, boss."
    }

    fun handle(c: Context, raw: String): String? {
        val t = raw.trim()
        if (offRx.matches(t)) return stop(c)
        if (onRx.matches(t)) return start(c)
        return null
    }
}

/** Starts drive mode when the chosen Bluetooth device (your car) connects, and stops it when it disconnects. */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val want = PreferencesStore(app).driveBtName.trim()
        if (want.isEmpty()) return
        val dev: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val name = try { dev?.name } catch (_: SecurityException) { null } ?: return
        if (!name.contains(want, ignoreCase = true)) return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> DriveMode.start(app)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> DriveMode.stop(app)
        }
    }
}
