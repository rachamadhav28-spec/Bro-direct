package com.bro.assistant.security

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backs up every setting and key into ONE file locked with a password you choose, so you can delete the app,
 * install a new one and bring everything back. (The phone's keystore does not survive uninstall, so secrets are
 * stored inside the file, protected by your password.)
 */
object Backup {
    private val MAGIC = "BROBK1".toByteArray()
    private val files = listOf(
        "bro_prefs", "bro_memory", "bro_notes", "bro_study", "bro_reminders", "bro_watch",
        "bro_lock", "bro_voice_lock", "bro_proactive", "bro_bubble"
    )
    private val secretShape = Regex("^[A-Za-z0-9+/=]+:[A-Za-z0-9+/=]+$")

    private fun key(pw: CharArray, salt: ByteArray): SecretKeySpec {
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pw, salt, 120_000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun export(c: Context, password: String): ByteArray {
        val app = c.applicationContext
        val root = JSONObject()
        val prefs = JSONObject()
        for (name in files) {
            val sp = app.getSharedPreferences(name, Context.MODE_PRIVATE)
            val o = JSONObject()
            for ((k, v) in sp.all) {
                val e = JSONObject()
                when (v) {
                    is String -> {
                        val plain = if (secretShape.matches(v)) SecretBox.decrypt(v) else null
                        if (plain != null) e.put("t", "x").put("v", plain) else e.put("t", "s").put("v", v)
                    }
                    is Boolean -> e.put("t", "b").put("v", v)
                    is Int -> e.put("t", "i").put("v", v)
                    is Long -> e.put("t", "l").put("v", v)
                    is Float -> e.put("t", "f").put("v", v.toDouble())
                    is Set<*> -> e.put("t", "z").put("v", JSONArray(v.map { it.toString() }))
                    else -> continue
                }
                o.put(k, e)
            }
            prefs.put(name, o)
        }
        root.put("prefs", prefs)
        File(app.filesDir, "learned_notes.json").takeIf { it.exists() }?.let { root.put("learned", it.readText()) }

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password.toCharArray(), salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8))
    }

    /** Returns a message for the user. */
    fun restore(c: Context, password: String, data: ByteArray): String {
        val app = c.applicationContext
        if (data.size < MAGIC.size + 28 || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return "That is not a BRO backup file."
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = data.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val body = data.copyOfRange(MAGIC.size + 28, data.size)
        val json = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password.toCharArray(), salt), GCMParameterSpec(128, iv))
            JSONObject(String(cipher.doFinal(body), Charsets.UTF_8))
        } catch (e: Exception) { return "Wrong password, or the file is damaged." }

        var count = 0
        val prefs = json.getJSONObject("prefs")
        for (name in prefs.keys()) {
            if (name !in files) continue
            val o = prefs.getJSONObject(name)
            val ed = app.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            for (k in o.keys()) {
                val e = o.getJSONObject(k)
                when (e.getString("t")) {
                    "s" -> ed.putString(k, e.getString("v"))
                    "x" -> ed.putString(k, SecretBox.encrypt(e.getString("v")))
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "z" -> { val a = e.getJSONArray("v"); ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
                    else -> continue
                }
                count++
            }
            ed.commit()
        }
        json.optString("learned").takeIf { it.isNotBlank() }?.let { File(app.filesDir, "learned_notes.json").writeText(it) }
        return "Restored $count settings and keys. Close BRO fully and open it again."
    }
}
