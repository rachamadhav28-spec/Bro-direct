package com.bro.assistant.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Optional: unlock BRO by saying a secret phrase. Convenient but weaker than a PIN: anyone who overhears
 * the phrase can use it, and speech recognition can be fooled by a recording. Only the lock screen accepts it,
 * never the "confirm risky action" screen.
 */
object VoiceLock {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("bro_voice_lock", Context.MODE_PRIVATE)

    fun normalize(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun hash(p: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(p.toCharArray(), salt, 100_000, 256)).encoded

    fun isSet(c: Context) = sp(c).contains("hash") && LockStore.isSet(c)

    fun set(c: Context, phrase: String): Boolean {
        val n = normalize(phrase)
        if (n.split(" ").size < 3 || n.length < 12) return false     // at least three words, so it is not trivial
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp(c).edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash(n, salt), Base64.NO_WRAP)).putInt("fails", 0).putLong("until", 0).apply()
        return true
    }

    fun clear(c: Context) = sp(c).edit().clear().apply()

    fun waitMs(c: Context) = (sp(c).getLong("until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun verify(c: Context, spoken: String): Boolean {
        if (waitMs(c) > 0) return false
        val s = sp(c)
        val salt = Base64.decode(s.getString("salt", "") ?: "", Base64.NO_WRAP)
        val want = Base64.decode(s.getString("hash", "") ?: "", Base64.NO_WRAP)
        val ok = want.isNotEmpty() && MessageDigest.isEqual(want, hash(normalize(spoken), salt))
        if (ok) s.edit().putInt("fails", 0).putLong("until", 0).apply()
        else {
            val f = s.getInt("fails", 0) + 1
            s.edit().putInt("fails", f).putLong("until", if (f >= 3) System.currentTimeMillis() + 60_000L * (f - 2) else 0L).apply()
        }
        return ok
    }
}
