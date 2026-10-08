package com.bro.assistant.actions

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import com.bro.assistant.BroAccessibilityService
import com.bro.assistant.PermissionManager
import com.bro.assistant.task.ActionResult

class PhoneActions(private val context: Context) {

    fun goHome(): ActionResult = global(AccessibilityService.GLOBAL_ACTION_HOME, "Went to the home screen.")

    fun goBack(): ActionResult = global(AccessibilityService.GLOBAL_ACTION_BACK, "Went back.")

    fun screenshot(): ActionResult {
        if (Build.VERSION.SDK_INT < 28) return ActionResult.fail("Screenshots by voice need Android 9 or newer.")
        return global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "Screenshot taken.")
    }

    private fun global(action: Int, okMessage: String): ActionResult {
        val service = BroAccessibilityService.instance
            ?: return ActionResult.fail("The Accessibility service is off.", PermissionManager.ACCESSIBILITY)
        return if (service.performGlobalAction(action)) ActionResult.ok(okMessage)
        else ActionResult.fail("Android refused that action.")
    }

    fun openSettings(): ActionResult {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ActionResult.ok("Opened Settings.")
    }

    fun setAlarm(hour: Int, minute: Int): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "BRO alarm")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            val label = String.format("%d:%02d %s", h12, minute, if (hour < 12) "AM" else "PM")
            ActionResult.ok("Alarm set for $label.", "I can't read the Clock app to confirm the alarm was saved.")
        } catch (e: ActivityNotFoundException) {
            ActionResult.fail("No clock app on this phone accepted the alarm.")
        }
    }

    fun call(contact: String): ActionResult {
        val number: String
        if (ContactLookup.looksLikeNumber(contact)) {
            number = contact
        } else {
            if (!PermissionManager.has(context, Manifest.permission.READ_CONTACTS)) {
                return ActionResult.fail("I need contacts permission to find $contact.", Manifest.permission.READ_CONTACTS)
            }
            number = ContactLookup.findNumber(context, contact)
                ?: return ActionResult.fail("I couldn't find $contact in your contacts.")
        }
        if (!PermissionManager.has(context, Manifest.permission.CALL_PHONE)) {
            return ActionResult.fail("I need permission to place calls.", Manifest.permission.CALL_PHONE)
        }
        context.startActivity(
            Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return ActionResult.ok("Calling $contact.")
    }
}
