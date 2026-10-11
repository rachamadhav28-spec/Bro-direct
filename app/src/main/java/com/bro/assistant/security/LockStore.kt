package com.bro.assistant.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * BRO's own lock: a PIN, password or pattern chosen inside BRO (not the phone's lock).
 * Only a salted, slow hash is stored, never the secret itself. Wrong guesses are slowed down.
 */
object LockStore {

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("bro_lock", Context.MODE_PRIVATE)

    /** "none", "pin", "password" or "pattern". */
    fun type(c: Context): String = sp(c).getString("type", "none") ?: "none"
    fun isSet(c: Context): Boolean = type(c) != "none" && sp(c).contains("hash")

    private fun hash(secret: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(secret.toCharArray(), salt, 100_000, 256)).encoded

    fun set(c: Context, type: String, secret: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp(c).edit()
            .putString("type", type)
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash(secret, salt), Base64.NO_WRAP))
            .putInt("fails", 0).putLong("until", 0L)
            .apply()
    }

    fun clear(c: Context) = sp(c).edit().clear().apply()

    /** Milliseconds left before another try is allowed (0 = allowed now). */
    fun waitMs(c: Context): Long = (sp(c).getLong("until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun verify(c: Context, secret: String): Boolean {
        if (waitMs(c) > 0) return false
        val s = sp(c)
        val salt = Base64.decode(s.getString("salt", "") ?: "", Base64.NO_WRAP)
        val want = Base64.decode(s.getString("hash", "") ?: "", Base64.NO_WRAP)
        val ok = want.isNotEmpty() && MessageDigest.isEqual(want, hash(secret, salt))
        if (ok) {
            s.edit().putInt("fails", 0).putLong("until", 0L).apply()
        } else {
            val fails = s.getInt("fails", 0) + 1
            // 5 wrong tries, then 30 s, 60 s, 90 s ... before the next one
            val until = if (fails >= 5) System.currentTimeMillis() + 30_000L * (fails - 4) else 0L
            s.edit().putInt("fails", fails).putLong("until", until).apply()
        }
        return ok
    }
}

/** Keeps the lock from triggering while BRO sends you to the file picker or a permission page. */
object LockGrace {
    @Volatile var until: Long = 0L
    fun allowFor(ms: Long) { until = System.currentTimeMillis() + ms }
    fun active() = System.currentTimeMillis() < until
}
