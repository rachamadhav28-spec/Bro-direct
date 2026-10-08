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
class DeviceControl(private val context: Context) {

    private val tileLabels = mapOf(
        "wifi" to listOf("Wi-Fi", "WLAN", "Wifi"),
        "bluetooth" to listOf("Bluetooth"),
        "hotspot" to listOf("Personal hotspot", "Hotspot"),
        "location" to listOf("Location"),
        "airplane" to listOf("Airplane mode", "Flight mode", "Aeroplane mode"),
        "mobile_data" to listOf("Mobile data", "Cellular data", "Data")
    )

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
        val labels = tileLabels[name] ?: return ActionResult.fail("I don't know how to switch $name.")
        val pretty = name.replace('_', ' ')
        val wanted = if (wantOn) "on" else "off"

        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        delay(900)
        val node = UiFinder.waitForNode(labels, 4000)
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
        val after = UiFinder.findContaining(*labels.toTypedArray())?.let { UiFinder.stateOf(it) }
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

    private fun closePanel(service: AccessibilityService) {
        val action = if (Build.VERSION.SDK_INT >= 31) AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
        else AccessibilityService.GLOBAL_ACTION_BACK
        service.performGlobalAction(action)
    }
}
