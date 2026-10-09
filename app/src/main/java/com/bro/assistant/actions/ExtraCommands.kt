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
            volumeCommand(t) != null -> volumeCommand(t)
            "volume up" in t -> adjustVolume(AudioManager.ADJUST_RAISE, "Volume up.")
            "volume down" in t -> adjustVolume(AudioManager.ADJUST_LOWER, "Volume down.")
            else -> null
        }
    }

    private val vol = "(?:vol(?:ume)?|sound)"
    private val muteRx = Regex("^(?:$vol\\s+(?:mute|off|zero|0)|(?:mute|silence)(?:\\s+(?:the\\s+)?(?:$vol|phone))?)$")
    private val unmuteRx = Regex("^(?:unmute|$vol\\s+(?:on|unmute))(?:\\s+.*)?$")
    private val maxRx = Regex("^(?:(?:set\\s+)?$vol\\s+(?:to\\s+)?(?:max(?:imum)?|full|100\\s*(?:%|percent)?)|max(?:imum)?\\s+$vol|full\\s+$vol)$")
    private val levelRx = Regex("^(?:set\\s+)?$vol\\s+(?:to\\s+)?(\\d{1,3})\\s*(?:%|percent)?$")

    /** Media volume: mute, unmute, max, or a percentage. Returns null for anything else. */
    private fun volumeCommand(text: String): String? {
        val s = text.trim().trimEnd('.', '!')
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        fun set(level: Int) = audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, AudioManager.FLAG_SHOW_UI)
        return when {
            muteRx.matches(s) -> { set(0); "Volume muted." }
            unmuteRx.matches(s) -> { set((max * 0.5).toInt().coerceAtLeast(1)); "Volume back on at 50 percent." }
            maxRx.matches(s) -> { set(max); "Volume at maximum." }
            levelRx.matches(s) -> {
                val pct = levelRx.find(s)!!.groupValues[1].toInt().coerceIn(0, 100)
                set((max * pct / 100.0).toInt()); "Volume set to $pct percent."
            }
            else -> null
        }
    }

    private fun adjustVolume(direction: Int, reply: String): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return reply
    }
}
