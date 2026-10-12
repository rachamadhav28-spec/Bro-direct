package com.bro.assistant.proactive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/** Reads the phone's calendar (only when the owner allowed it). */
object CalendarReader {
    data class Ev(val id: Long, val title: String, val start: Long, val allDay: Boolean)

    fun allowed(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun between(ctx: Context, from: Long, to: Long): List<Ev> {
        if (!allowed(ctx)) return emptyList()
        val out = ArrayList<Ev>()
        try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
                android.content.ContentUris.appendId(it, from); android.content.ContentUris.appendId(it, to); it.build()
            }
            ctx.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                null, null, CalendarContract.Instances.BEGIN + " ASC"
            )?.use { c ->
                while (c.moveToNext() && out.size < 25) {
                    out.add(Ev(c.getLong(0), c.getString(1) ?: "(no title)", c.getLong(2), c.getInt(3) == 1))
                }
            }
        } catch (_: Exception) {}
        return out
    }
}
