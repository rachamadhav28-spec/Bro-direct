package com.bro.assistant

import android.content.Context
import android.media.AudioManager

/** Direct volume controls. No Settings screen is opened. */
object SystemControls {

    private fun am(c: Context) = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun volumeMax(c: Context): String {
        val a = am(c)
        a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        a.setStreamVolume(AudioManager.STREAM_MUSIC, a.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
        return "Volume at maximum"
    }

    fun volumeMute(c: Context): String {
        am(c).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        return "Volume muted"
    }

    fun volumeUnmute(c: Context): String {
        am(c).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
        return "Volume unmuted"
    }

    fun volumeUp(c: Context): String {
        am(c).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        return "Volume up"
    }

    fun volumeDown(c: Context): String {
        am(c).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        return "Volume down"
    }

    /**
     * Routes a spoken command to a direct action. Returns the reply text, or null if not a system command.
     * Call this BEFORE whatever code currently opens Settings.
     */
    fun handle(c: Context, spoken: String, onAsyncResult: (String) -> Unit): String? {
        val t = spoken.lowercase()
        return when {
            "volume max" in t || "max volume" in t || "full volume" in t -> volumeMax(c)
            Regex("\\bunmute\\b").containsMatchIn(t) -> volumeUnmute(c)
            Regex("\\bmute\\b").containsMatchIn(t) -> volumeMute(c)
            "volume up" in t -> volumeUp(c)
            "volume down" in t -> volumeDown(c)
            "ultra game" in t || "game mode" in t -> {
                val svc = BroAccessibilityService.instance
                    ?: return "Please enable the BRO accessibility service first"
                val on = when {
                    Regex("turn off|disable|off").containsMatchIn(t) -> false
                    Regex("turn on|enable|\\bon\\b|start|open").containsMatchIn(t) -> true
                    else -> null
                }
                svc.setQuickSetting("Ultra Game", on) { _, msg -> onAsyncResult(msg) }
                "Okay"
            }
            else -> null
        }
    }
}
