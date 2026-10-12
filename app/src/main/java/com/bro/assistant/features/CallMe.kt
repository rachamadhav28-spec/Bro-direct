package com.bro.assistant.features

import android.content.Context
import android.util.Base64
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.security.SecretBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * BRO phones your number through your own Twilio account and speaks a message.
 * It is a one-way call: you hear BRO, BRO cannot hear you (that would need a server).
 */
object CallMe {
    private val I = RegexOption.IGNORE_CASE
    private val callRx = Regex("^(?:please\\s+)?(?:call\\s+me|give\\s+me\\s+a\\s+call|ring\\s+me)(?:\\s+(?:and\\s+)?(?:say|tell(?:\\s+me)?|read)\\s+(.{1,400}))?[.!?\\s]*$", I)
    private val testRx = Regex("^(?:test\\s+(?:the\\s+)?call|call\\s+test)[.!?\\s]*$", I)

    fun configured(p: PreferencesStore) =
        p.twilioSid.isNotBlank() && p.twilioToken.isNotBlank() && p.twilioFrom.isNotBlank() && p.myNumber.isNotBlank()

    private fun e164(raw: String, cc: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (raw.trim().startsWith("+")) "+$digits" else "+" + cc + digits.trimStart('0')
    }

    private fun xml(s: String) = s.replace("&", "and").replace("<", " ").replace(">", " ").replace("\"", "'")

    /** Returns null when the call was placed, or a short error message. */
    suspend fun place(c: Context, text: String): String? = withContext(Dispatchers.IO) {
        val p = PreferencesStore(c)
        if (!configured(p)) return@withContext "Call settings are not filled in yet (Settings, BRO calls me)."
        val token = SecretBox.decrypt(p.twilioToken) ?: return@withContext "Could not read the saved Twilio token. Enter it again in Settings."
        val sid = p.twilioSid.trim()
        val voice = p.callVoice.trim().let { if (it.isNotEmpty()) " voice=\"${xml(it)}\"" else "" }
        val twiml = "<Response><Say language=\"en-IN\"$voice>${xml(text.take(600))}</Say><Pause length=\"1\"/><Say language=\"en-IN\"$voice>${xml(text.take(600))}</Say></Response>"
        val body = "To=" + URLEncoder.encode(e164(p.myNumber, p.countryCode), "UTF-8") +
            "&From=" + URLEncoder.encode(e164(p.twilioFrom, ""), "UTF-8") +
            "&Twiml=" + URLEncoder.encode(twiml, "UTF-8")
        var conn: HttpURLConnection? = null
        try {
            conn = URL("https://api.twilio.com/2010-04-01/Accounts/$sid/Calls.json").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000; conn.readTimeout = 20000; conn.doOutput = true
            conn.setRequestProperty("Authorization", "Basic " + Base64.encodeToString("$sid:$token".toByteArray(), Base64.NO_WRAP))
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) null
            else {
                val err = try { conn.errorStream?.bufferedReader()?.readText().orEmpty() } catch (_: Exception) { "" }
                val msg = Regex("\"message\"\\s*:\\s*\"([^\"]{1,200})").find(err)?.groupValues?.get(1)
                when (code) {
                    401 -> "Twilio refused the Account SID or token."
                    else -> "Twilio could not place the call ($code)" + (if (msg != null) ": $msg" else ".")
                }
            }
        } catch (e: java.net.UnknownHostException) {
            "No internet connection."
        } catch (e: Exception) {
            "The call failed: ${e.message ?: e.javaClass.simpleName}"
        } finally { conn?.disconnect() }
    }

    /** Fire-and-forget, used when a reminder goes off. */
    fun placeInBackground(c: Context, text: String) {
        val app = c.applicationContext
        val p = PreferencesStore(app)
        if (!p.callForReminders || !configured(p)) return
        CoroutineScope(Dispatchers.IO).launch { place(app, text) }
    }

    suspend fun handle(c: Context, p: PreferencesStore, raw: String): String? {
        val t = raw.trim()
        val test = testRx.matches(t)
        val m = if (test) null else callRx.find(t)
        if (!test && m == null) return null
        if (!configured(p)) return "I can phone you through your Twilio account, but it isn't set up yet. Open Settings, \"BRO calls me\", and fill in the four boxes."
        val say = if (test) "Hello boss, this is BRO. Your call setup works." else m!!.groupValues[1].ifBlank { "" }.ifBlank { "Hello boss, BRO here. You asked me to call you." }
        val err = place(c, say)
        return err ?: "Calling you now, boss. It should ring on ${p.myNumber} in a few seconds."
    }
}
