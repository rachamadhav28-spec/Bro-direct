package com.bro.assistant.actions

import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small offline answers: time, date, battery, volume. Returns null when it isn't one of them. */
class ExtraCommands(private val context: Context) {

    fun tryHandle(text: String): String? {
        val t = text.lowercase()
        return when {
            "what time" in t || "time is it" in t ->
                "It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."
            "what's the date" in t || "what is the date" in t || "today's date" in t || "what day" in t ->
                "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) + "."
            "battery" in t && ("how" in t || "what" in t || "level" in t || "percent" in t) -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                "Battery is at ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent."
            }
            "volume up" in t -> adjustVolume(AudioManager.ADJUST_RAISE, "Volume up.")
            "volume down" in t -> adjustVolume(AudioManager.ADJUST_LOWER, "Volume down.")
            t.trim() == "mute" -> adjustVolume(AudioManager.ADJUST_MUTE, "Muted.")
            else -> null
        }
    }

    private fun adjustVolume(direction: Int, reply: String): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return reply
    }
}
