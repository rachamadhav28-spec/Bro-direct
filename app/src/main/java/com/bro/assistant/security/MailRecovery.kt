package com.bro.assistant.security

import android.content.Context
import android.util.Base64
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.SecureRandom
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Email recovery. BRO emails a one-time code from your own Gmail to your own Gmail, using a Google
 * "app password" you create once. The app password is stored encrypted on this phone only.
 */
object MailRecovery {

    // ---------- sending (plain SMTP over TLS, no server of ours involved) ----------

    private fun readReply(r: BufferedReader): String {
        var line = r.readLine() ?: throw IllegalStateException("The mail server closed the connection.")
        val sb = StringBuilder(line)
        while (line.length >= 4 && line[3] == '-') { line = r.readLine() ?: break; sb.append('\n').append(line) }
        return sb.toString()
    }

    private fun expect(reply: String, vararg codes: String) {
        if (codes.none { reply.startsWith(it) }) throw IllegalStateException(reply.lineSequence().last().take(120))
    }

    /** Returns null on success, or a short error message. */
    suspend fun sendMail(from: String, appPassword: String, to: String, subject: String, body: String): String? =
        withContext(Dispatchers.IO) {
            var socket: SSLSocket? = null
            try {
                socket = (SSLSocketFactory.getDefault().createSocket("smtp.gmail.com", 465) as SSLSocket).apply { soTimeout = 20000 }
                val r = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val w = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                fun send(line: String) { w.write(line + "\r\n"); w.flush() }
                fun b64(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                expect(readReply(r), "220")
                send("EHLO bro.app"); expect(readReply(r), "250")
                send("AUTH LOGIN"); expect(readReply(r), "334")
                send(b64(from)); expect(readReply(r), "334")
                send(b64(appPassword.replace(" ", ""))); 
                val auth = readReply(r)
                if (!auth.startsWith("235")) return@withContext "Google refused the app password. Create a new one and try again."
                send("MAIL FROM:<$from>"); expect(readReply(r), "250")
                send("RCPT TO:<$to>"); expect(readReply(r), "250", "251")
                send("DATA"); expect(readReply(r), "354")
                val safeBody = body.replace("\r\n", "\n").replace("\n.", "\n..").replace("\n", "\r\n")
                w.write("From: BRO <$from>\r\nTo: $to\r\nSubject: $subject\r\nMIME-Version: 1.0\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n\r\n$safeBody\r\n.\r\n")
                w.flush()
                expect(readReply(r), "250")
                send("QUIT")
                null
            } catch (e: java.net.UnknownHostException) {
                "No internet connection."
            } catch (e: java.net.SocketTimeoutException) {
                "The mail server took too long. Try again."
            } catch (e: Exception) {
                "Could not send the email: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }

    // ---------- configuration ----------

    fun isConfigured(prefs: PreferencesStore) = prefs.accountEmail.isNotBlank() && prefs.mailSecret.isNotBlank()

    fun mask(email: String): String {
        val at = email.indexOf('@')
        return if (at <= 1) email else email[0] + "***" + email.substring(at)
    }

    // ---------- one-time codes ----------

    private var codeHash: String? = null
    private var expires = 0L
    private var tries = 0
    private var lastSent = 0L

    private fun hash(code: String) =
        Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(("bro-recovery:" + code).toByteArray()), Base64.NO_WRAP)

    /** Emails a fresh 6-digit code to the signed-in Gmail. Returns null on success, else an error message. */
    suspend fun sendCode(prefs: PreferencesStore, testOnly: Boolean = false, appPasswordOverride: String? = null): String? {
        val email = prefs.accountEmail
        val pass = appPasswordOverride ?: SecretBox.decrypt(prefs.mailSecret) ?: return "Email recovery is not set up."
        if (email.isBlank()) return "Sign in with Gmail first."
        val now = System.currentTimeMillis()
        if (!testOnly && now - lastSent < 60_000L) return "Please wait a minute before asking for another code."
        val code = "%06d".format(SecureRandom().nextInt(1_000_000))
        val err = sendMail(
            email, pass, email, "BRO recovery code",
            "Your BRO recovery code is $code\n\nIt works for 10 minutes. If you did not ask for it, ignore this email."
        )
        if (err == null) {
            codeHash = hash(code); expires = now + 10 * 60_000L; tries = 0; lastSent = now
        }
        return err
    }

    fun verify(code: String): Boolean {
        val h = codeHash ?: return false
        if (System.currentTimeMillis() > expires || tries >= 5) { codeHash = null; return false }
        tries++
        val ok = MessageDigest.isEqual(h.toByteArray(), hash(code.trim()).toByteArray())
        if (ok) codeHash = null
        return ok
    }
}
