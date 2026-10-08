package com.bro.assistant.actions

import android.content.Context
import android.provider.ContactsContract

object ContactLookup {

    /** Finds a phone number by contact name. Needs the READ_CONTACTS permission. */
    fun findNumber(context: Context, name: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val column = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        val attempts = listOf("$column LIKE ?" to name, "$column LIKE ?" to "%$name%")
        for ((selection, arg) in attempts) {
            context.contentResolver.query(uri, projection, selection, arrayOf(arg), null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        }
        return null
    }

    fun looksLikeNumber(text: String): Boolean =
        text.trim().matches(Regex("^\\+?[0-9 \\-]{7,15}$"))

    /** Builds digits with a country code for WhatsApp links. */
    fun toInternational(raw: String, countryCode: String): String {
        val hasPlus = raw.trim().startsWith("+")
        val digits = raw.filter { it.isDigit() }
        return when {
            hasPlus -> digits
            digits.length == 10 -> countryCode + digits
            digits.startsWith("0") -> countryCode + digits.drop(1)
            else -> digits
        }
    }
}
