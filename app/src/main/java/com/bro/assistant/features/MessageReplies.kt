package com.bro.assistant.features

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.proactive.Proactive

/** Receives message notifications so BRO can read them aloud and reply with the notification's own reply button. */
class BroNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) try { MessageReplies.onPosted(applicationContext, sbn) } catch (_: Exception) {}
    }
}

object MessageReplies {
    private val I = RegexOption.IGNORE_CASE
    class Msg(val key: String, val sender: String, val text: String, val reply: Notification.Action?, val time: Long)

    private val apps = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.google.android.apps.messaging",
        "com.android.mms", "com.samsung.android.messaging", "com.truecaller"
    )
    private val recent = ArrayList<Msg>()

    fun enabled(c: Context) = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun onPosted(c: Context, sbn: StatusBarNotification) {
        if (sbn.packageName !in apps) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() || text.isEmpty()) return
        if (Regex("^\\d+ (new )?messages?$", I).matches(text) || title.equals("whatsapp", true) || title.equals("you", true)) return
        val action = n.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true }
        synchronized(recent) {
            if (recent.any { it.key == sbn.key && it.text == text }) return
            recent.add(Msg(sbn.key, title, text, action, System.currentTimeMillis()))
            while (recent.size > 15) recent.removeAt(0)
        }
        val prefs = PreferencesStore(c)
        val awake = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (DriveMode.active || (prefs.readMessages && awake)) {
            Proactive.speakNow(c, "New message from $title. $text. Say reply, then what to send.")
        }
    }

    private fun latest(): Msg? = synchronized(recent) { recent.lastOrNull() }

    private val readRx = Regex("""^(?:read\s+(?:my\s+)?(?:new\s+)?messages?|any\s+(?:new\s+)?messages?|do\s+i\s+have\s+(?:any\s+)?(?:new\s+)?messages?|what\s+(?:are\s+)?my\s+messages?)[?.!\s]*$""", I)
    private val replyRx = Regex("""^reply(?:\s+(?:with|saying|that))?\s+(.{1,300})$""", I)
    private val callBackRx = Regex("""^call\s+(?:him|her|them|back|(?:him|her|them)\s+back)[.!\s]*$""", I)

    /** The sender's name when the owner says "call back"; BRO then dials that contact. */
    fun callBackTarget(text: String): String? = if (callBackRx.matches(text.trim())) latest()?.sender else null

    private fun shortcut(s: String): String = when (s.trim().lowercase().trimEnd('.', '!')) {
        "yes", "yeah", "yep" -> "Yes"
        "no", "nope" -> "No"
        "ok", "okay" -> "Okay"
        "later" -> "I'll reply later."
        "busy" -> "I'm busy right now, I'll talk to you later."
        "on my way" -> "On my way."
        "thanks", "thank you" -> "Thank you!"
        else -> s.trim().replaceFirstChar { it.uppercase() }
    }

    suspend fun handle(c: Context, prefs: PreferencesStore, text: String, confirm: suspend (String) -> Boolean): String? {
        val t = text.trim()
        if (readRx.matches(t)) {
            val l = synchronized(recent) { recent.takeLast(3).reversed() }
            if (l.isEmpty()) return if (enabled(c)) "No new messages I can see." else "I can't see your messages yet. Turn on notification access for BRO in Settings."
            return "Latest messages:\n" + l.joinToString("\n") { "From ${it.sender}: ${it.text}" } + "\n\nSay \"reply yes\", \"reply later\" or \"reply <your words>\", or \"call back\"."
        }
        val m = replyRx.find(t) ?: return null
        val msg = latest() ?: return if (enabled(c)) "There is no message to reply to." else "I can't see your messages yet. Turn on notification access for BRO in Settings."
        val action = msg.reply ?: return "I can't reply to ${msg.sender}'s message from here. Open the app to answer."
        val body = shortcut(m.groupValues[1])
        if (prefs.confirmBeforeSend && !confirm("Send \"$body\" to ${msg.sender}?")) return "Okay, I did not send it."
        return try {
            val inputs = action.remoteInputs
            val bundle = Bundle()
            inputs.forEach { bundle.putCharSequence(it.resultKey, body) }
            val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            android.app.RemoteInput.addResultsToIntent(inputs, intent, bundle)
            action.actionIntent.send(c, 0, intent)
            "Sent \"$body\" to ${msg.sender}."
        } catch (e: Exception) {
            "I couldn't send that reply (${e.message ?: "the message is no longer open"})."
        }
    }
}
