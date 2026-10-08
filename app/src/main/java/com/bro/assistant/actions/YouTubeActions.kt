package com.bro.assistant.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bro.assistant.BroAccessibilityService
import com.bro.assistant.PermissionManager
import com.bro.assistant.task.ActionResult

/**
 * Searching uses a normal YouTube intent. "Play first result" taps what the Accessibility
 * service can see, so it depends on YouTube's current layout and may stop working after an
 * app update. BRO reports honestly when it can't confirm playback.
 */
class YouTubeActions(private val context: Context) {

    private val pkg = "com.google.android.youtube"

    suspend fun search(query: String): ActionResult {
        if (context.packageManager.getLaunchIntentForPackage(pkg) == null) {
            return ActionResult.fail("YouTube isn't installed.")
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra("query", query)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)))
                    .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        if (BroAccessibilityService.instance == null) {
            return ActionResult.ok(
                "Searching YouTube for $query.",
                "I can't confirm the results appeared because the Accessibility service is off."
            )
        }
        val inYoutube = UiFinder.waitFor(8000) { BroAccessibilityService.currentPackage == pkg }
        if (!inYoutube) return ActionResult.fail("YouTube didn't come to the front.")

        val results = UiFinder.waitFor(8000) { UiFinder.findContaining("play video") != null }
        return if (results) ActionResult.ok("YouTube search completed for $query.")
        else ActionResult.ok("Searching YouTube for $query.", "I couldn't confirm the results list appeared.")
    }

    suspend fun playFirstResult(): ActionResult {
        if (BroAccessibilityService.instance == null) {
            return ActionResult.fail("Tapping a result needs the Accessibility service.", PermissionManager.ACCESSIBILITY)
        }
        val node = UiFinder.waitForNode(listOf("play video"), 6000)
            ?: return ActionResult.fail("I couldn't find a video result to tap.")
        if (!UiFinder.click(node)) return ActionResult.fail("I found a result but couldn't tap it.")

        val left = UiFinder.waitFor(6000) {
            BroAccessibilityService.currentPackage == pkg && UiFinder.findContaining("play video") == null
        }
        return if (left) ActionResult.ok("Playing the first result.")
        else ActionResult.ok("Tapped the first result.", "I can't confirm the video is playing.")
    }
}
