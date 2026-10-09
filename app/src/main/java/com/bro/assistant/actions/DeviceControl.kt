package com.bro.assistant.actions

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import com.bro.assistant.BroAccessibilityService
import com.bro.assistant.PermissionManager
import com.bro.assistant.task.ActionResult
import kotlinx.coroutines.delay

/**
 * Flashlight uses the official torch API. Wi-Fi, Bluetooth, hotspot, location, airplane mode
 * and mobile data cannot be switched by normal apps on modern Android, so BRO opens Quick
 * Settings and taps the tile through the Accessibility service, then re-reads the tile.
 */
/** Quick Settings tiles BRO can switch. Key -> on-screen labels (tried in order). */
object Tiles {
    val labels: Map<String, List<String>> = mapOf(
        "wifi" to listOf("Wi-Fi", "WLAN", "Wifi"),
        "bluetooth" to listOf("Bluetooth"),
        "hotspot" to listOf("Personal hotspot", "Hotspot"),
        "location" to listOf("Location"),
        "airplane" to listOf("Airplane mode", "Flight mode", "Aeroplane mode"),
        "mobile_data" to listOf("Mobile data", "Cellular data"),
        "ultra_game_mode" to listOf("Ultra Game Mode", "Game Mode"),
        "dark_mode" to listOf("Dark mode"),
        "battery_saver" to listOf("Battery Saver"),
        "super_battery_saver" to listOf("Super Battery Saver", "Super Battery"),
        "auto_rotate" to listOf("Auto-rotate", "Auto rotate", "Autorotate"),
        "dnd" to listOf("Do Not Disturb", "DND"),
        "eye_protection" to listOf("Eye Protection"),
        "focus_mode" to listOf("Focus mode", "Focus"),
        "bedtime_mode" to listOf("Bedtime mode"),
        "mic_access" to listOf("Mic access", "Microphone access"),
        "camera_access" to listOf("Camera access"),
        "data_saver" to listOf("Data-saving", "Data saver"),
        "extra_dim" to listOf("Extra dim"),
        "color_inversion" to listOf("Color inversion")
    )

    /** Tiles whose partial label would also match a different tile. */
    val exclude: Map<String, String> = mapOf("battery_saver" to "Super")
}

class DeviceControl(private val context: Context) {


    fun flashlight(on: Boolean): ActionResult {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ActionResult.fail("This phone has no flashlight I can control.")
        cm.setTorchMode(id, on)
        return ActionResult.ok(if (on) "Flashlight on." else "Flashlight off.")
    }

    suspend fun toggleSetting(name: String, wantOn: Boolean): ActionResult {
        val service = BroAccessibilityService.instance
            ?: return ActionResult.fail("The Accessibility service is off.", PermissionManager.ACCESSIBILITY)
        val labels = Tiles.labels[name] ?: return ActionResult.fail("I don't know how to switch $name.")
        val pretty = name.replace('_', ' ')
        val wanted = if (wantOn) "on" else "off"

        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        delay(900)
        val exclude = Tiles.exclude[name]
        val node = findTileAcrossPages(service, labels, exclude)
        if (node == null) {
            closePanel(service)
            return ActionResult.fail("I couldn't find the $pretty tile in Quick Settings.")
        }

        val before = UiFinder.stateOf(node)
        if (before == wantOn) {
            closePanel(service)
            return ActionResult.ok("$pretty is already $wanted.")
        }
        if (!UiFinder.click(node)) {
            closePanel(service)
            return ActionResult.fail("I found the $pretty tile but couldn't tap it.")
        }

        delay(1000)
        val after = UiFinder.findTile(labels, exclude)?.let { UiFinder.stateOf(it) }
        closePanel(service)
        return when (after) {
            wantOn -> ActionResult.ok("$pretty turned $wanted.")
            null -> ActionResult.ok(
                "I tapped the $pretty tile.",
                "I couldn't read the tile state, so I can't confirm it changed."
            )
            else -> ActionResult.fail("I tapped the $pretty tile but it didn't change. The phone may be blocking it.")
        }
    }

    /** Looks for the tile on the current Quick Settings page, then swipes through the other pages. */
    private suspend fun findTileAcrossPages(
        service: BroAccessibilityService,
        labels: List<String>,
        exclude: String?
    ): android.view.accessibility.AccessibilityNodeInfo? {
        // swipe left to later pages first, then right to earlier ones
        val moves = listOf(true, true, true, false, false, false, false, false, false)
        var wait = 2500L
        for (i in 0..moves.size) {
            val end = android.os.SystemClock.uptimeMillis() + wait
            while (true) {
                UiFinder.findTile(labels, exclude)?.let { return it }
                if (android.os.SystemClock.uptimeMillis() >= end) break
                delay(300)
            }
            if (i == moves.size) break
            service.swipeHorizontal(moves[i])
            wait = 700L
            if (i == 2) delay(300) // let the pager settle before reversing
        }
        return null
    }

    private fun closePanel(service: AccessibilityService) {
        val action = if (Build.VERSION.SDK_INT >= 31) AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
        else AccessibilityService.GLOBAL_ACTION_BACK
        service.performGlobalAction(action)
    }
}
