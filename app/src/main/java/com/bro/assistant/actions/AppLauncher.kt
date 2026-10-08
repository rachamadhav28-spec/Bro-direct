package com.bro.assistant.actions

import android.content.Context
import android.content.Intent
import com.bro.assistant.BroAccessibilityService
import com.bro.assistant.task.ActionResult

class AppLauncher(private val context: Context) {

    private val aliases = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "instagram" to "com.instagram.android",
        "chrome" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "telegram" to "org.telegram.messenger",
        "facebook" to "com.facebook.katana",
        "spotify" to "com.spotify.music"
    )

    private fun isInstalled(pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg) != null

    fun resolvePackage(name: String): String? {
        val key = name.trim().lowercase()
        val compact = key.replace(" ", "")
        (aliases[key] ?: aliases[compact])?.let { if (isInstalled(it)) return it }

        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        return apps.firstOrNull { it.loadLabel(pm).toString().equals(key, ignoreCase = true) }
            ?.activityInfo?.packageName
            ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(key, ignoreCase = true) }
                ?.activityInfo?.packageName
    }

    suspend fun open(appName: String): ActionResult {
        val pkg = resolvePackage(appName) ?: return ActionResult.fail("I couldn't find an app called $appName.")
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return ActionResult.fail("I can't open $appName.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        if (BroAccessibilityService.instance == null) {
            return ActionResult.ok(
                "Opening $appName.",
                "I can't confirm it opened because the Accessibility service is off."
            )
        }
        val front = UiFinder.waitFor(5000) { BroAccessibilityService.currentPackage == pkg }
        return if (front) ActionResult.ok("$appName is open.")
        else ActionResult.fail("I asked Android to open $appName, but it didn't come to the front. Android may block opening apps from the background.")
    }
}
