package com.bro.assistant

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class BroAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BroAccessibilityService? = null

        /** Package name of the app currently in the foreground (best effort). */
        @Volatile
        var currentPackage: String = ""

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val component = ComponentName(context, BroAccessibilityService::class.java)
            val full = component.flattenToString()
            val short = component.flattenToShortString()
            return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != "com.android.systemui" && !pkg.contains("inputmethod")) {
                currentPackage = pkg
            }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
