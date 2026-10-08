package com.bro.assistant.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bro.assistant.BroAccessibilityService
import com.bro.assistant.PermissionManager
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.task.ActionResult

/**
 * Sends a WhatsApp message by opening a prefilled chat with an official WhatsApp link,
 * then tapping Send through the Accessibility service. Success is only reported when the
 * Send button disappears after the tap (WhatsApp swaps it for the voice button).
 */
class MessagingActions(private val context: Context, private val prefs: PreferencesStore) {

    suspend fun sendWhatsApp(
        contact: String,
        message: String,
        confirm: suspend (String) -> Boolean
    ): ActionResult {
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull {
            context.packageManager.getLaunchIntentForPackage(it) != null
        } ?: return ActionResult.fail("WhatsApp isn't installed.")

        val raw: String
        if (ContactLookup.looksLikeNumber(contact)) {
            raw = contact
        } else {
            if (!PermissionManager.has(context, Manifest.permission.READ_CONTACTS)) {
                return ActionResult.fail("I need contacts permission to find $contact.", Manifest.permission.READ_CONTACTS)
            }
            raw = ContactLookup.findNumber(context, contact)
                ?: return ActionResult.fail("I couldn't find $contact in your contacts.")
        }
        val phone = ContactLookup.toInternational(raw, prefs.countryCode)

        if (BroAccessibilityService.instance == null) {
            return ActionResult.fail(
                "Sending needs the Accessibility service so BRO can tap Send.",
                PermissionManager.ACCESSIBILITY
            )
        }

        if (prefs.confirmBeforeSend && !confirm("Send \"$message\" to $contact on WhatsApp?")) {
            return ActionResult.fail("You chose not to send it.")
        }

        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        val opened = UiFinder.waitFor(8000) { BroAccessibilityService.currentPackage.startsWith("com.whatsapp") }
        if (!opened) return ActionResult.fail("WhatsApp didn't open.")

        val sendButton = UiFinder.waitForNode(listOf("Send"), 8000, exact = true)
            ?: return ActionResult.fail("I opened the chat with $contact but couldn't find the Send button.")
        if (!UiFinder.click(sendButton)) return ActionResult.fail("I found the Send button but couldn't tap it.")

        val sent = UiFinder.waitFor(4000) { UiFinder.findExact("Send") == null }
        return if (sent) ActionResult.ok("Message sent to $contact.")
        else ActionResult.fail("I tapped Send, but WhatsApp still shows the message waiting. Please check the chat.")
    }
}
