package com.bro.assistant.features

import android.content.Context
import com.bro.assistant.memory.PreferencesStore

/** Runs the newer commands in order. Returns BRO's answer, or null when none of them applies. */
object Features {
    suspend fun handle(c: Context, prefs: PreferencesStore, text: String, confirm: suspend (String) -> Boolean): String? {
        Study.handle(c, prefs, text)?.let { return it }          // a quiz in progress takes every answer
        CallMe.handle(c, prefs, text)?.let { return it }
        DriveMode.handle(c, text)?.let { return it }
        FocusGuard.handle(c, text)?.let { return it }
        Reminders.handle(c, text)?.let { return it }
        Notes.handle(c, text)?.let { return it }
        MessageReplies.handle(c, prefs, text, confirm)?.let { return it }
        return null
    }
}
