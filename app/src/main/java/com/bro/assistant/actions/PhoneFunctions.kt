package com.bro.assistant.actions

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import com.bro.assistant.BroAccessibilityService
import com.bro.assistant.TeluguNormalizer

/**
 * Direct control of the phone functions Android lets an app control, with no AI and no internet:
 * music keys, brightness, font size, screen timeout, auto-rotate, ringer, Do Not Disturb, per-stream
 * volume, lock/power menu/split-screen/screenshot/recents (through Accessibility), timers, settings
 * pages, navigation, web search, and Quick Settings tiles. Returns the reply, or null if not handled.
 */
object PhoneFunctions {

    fun handle(c: Context, spoken: String, onAsync: (String) -> Unit): String? {
        val t = TeluguNormalizer.preprocess(spoken).lowercase().trim().trimEnd('.', '!', '?', ' ')
            .replace(Regex("^(?:please|bro|hey bro|ok bro)\\s+"), "")
            .replace(Regex("\\s+(?:please|bro)$"), "")
        if (t.isEmpty() || t.length > 140) return null
        return try {
            media(c, t) ?: brightness(c, t) ?: display(c, t) ?: ringer(c, t) ?: streams(c, t)
                ?: global(t) ?: timer(c, t) ?: settingsPage(c, t) ?: web(c, t) ?: info(c, t)
                ?: tile(t, onAsync)
        } catch (e: SecurityException) {
            "Android blocked that: ${e.message ?: "permission missing"}."
        }
    }

    private fun launch(c: Context, i: Intent): Boolean = try {
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    // ---------- music ----------

    private fun key(c: Context, code: Int) {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun media(c: Context, t: String): String? = when {
        Regex("^(?:pause|stop)(?: the)?(?: music| song| video| playback| playing)?$").matches(t) -> { key(c, KeyEvent.KEYCODE_MEDIA_PAUSE); "Paused." }
        Regex("^(?:play|resume|continue)(?: the)?(?: music| song| video| playback)?$").matches(t) -> { key(c, KeyEvent.KEYCODE_MEDIA_PLAY); "Playing." }
        Regex("^(?:next|skip)(?: the)?(?: song| track| music)?$").matches(t) -> { key(c, KeyEvent.KEYCODE_MEDIA_NEXT); "Next track." }
        Regex("^(?:previous|prev|last)(?: song| track)$|^go back(?: a)? (?:song|track)$").matches(t) -> { key(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS); "Previous track." }
        else -> null
    }

    // ---------- brightness / display ----------

    private fun needWriteSettings(c: Context): String? {
        if (Settings.System.canWrite(c)) return null
        launch(c, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${c.packageName}")))
        return "I need the \"Modify system settings\" permission for that. I opened the page: turn it on for BRO, come back, and say it again."
    }

    private fun brightness(c: Context, t: String): String? {
        val auto = Regex("^auto(?:matic)? brightness (on|off)$|^(?:turn )?(on|off) auto(?:matic)? brightness$").find(t)
        if (auto != null) {
            needWriteSettings(c)?.let { return it }
            val on = (auto.groupValues[1].ifEmpty { auto.groupValues[2] }) == "on"
            Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            return "Auto brightness ${if (on) "on" else "off"}."
        }
        val pct: Int? = Regex("^(?:set )?brightness (?:to )?(\\d{1,3}) ?(?:%|percent)?$").find(t)?.groupValues?.get(1)?.toInt()
            ?: when {
                Regex("^(?:max|full|maximum|highest) brightness$|^brightness (?:max|full|maximum|high)$").matches(t) -> 100
                Regex("^(?:min|minimum|lowest|low) brightness$|^brightness (?:min|minimum|low)$").matches(t) -> 5
                Regex("^(?:half|medium) brightness$|^brightness (?:half|medium)$").matches(t) -> 50
                else -> null
            }
        val step = when {
            Regex("^(?:increase|raise|brightness up|more brightness|brighter)(?: brightness)?$").matches(t) -> 20
            Regex("^(?:decrease|reduce|lower|brightness down|less brightness|dim)(?: brightness)?$").matches(t) -> -20
            else -> 0
        }
        if (pct == null && step == 0) return null
        needWriteSettings(c)?.let { return it }
        val cur = Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val target = if (pct != null) (pct.coerceIn(0, 100) * 255 / 100) else (cur + step * 255 / 100).coerceIn(5, 255)
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target.coerceIn(1, 255))
        return "Brightness set to ${target * 100 / 255} percent."
    }

    private fun display(c: Context, t: String): String? {
        // font size
        Regex("^(?:set )?font size(?: to)? (small|default|normal|large|huge|bigger|smaller|big)$|^(?:make )?(?:the )?font (bigger|smaller|larger)$").find(t)?.let { m ->
            needWriteSettings(c)?.let { return it }
            val w = m.groupValues[1].ifEmpty { m.groupValues[2] }
            val cur = Settings.System.getFloat(c.contentResolver, Settings.System.FONT_SCALE, 1f)
            val v = when (w) {
                "small" -> 0.85f; "default", "normal" -> 1.0f; "large", "big" -> 1.15f; "huge" -> 1.3f
                "bigger", "larger" -> (cur + 0.15f).coerceAtMost(1.3f)
                else -> (cur - 0.15f).coerceAtLeast(0.85f)
            }
            Settings.System.putFloat(c.contentResolver, Settings.System.FONT_SCALE, v)
            return "Font size changed."
        }
        // screen timeout
        Regex("^(?:set )?screen (?:timeout|off(?: time)?)(?: to)? (\\d+) ?(second|sec|minute|min)s?$").find(t)?.let { m ->
            needWriteSettings(c)?.let { return it }
            val n = m.groupValues[1].toInt()
            val ms = if (m.groupValues[2].startsWith("sec")) n * 1000 else n * 60_000
            Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, ms)
            return "Screen timeout set to $n ${m.groupValues[2]}${if (n == 1) "" else "s"}."
        }
        // auto rotate
        Regex("^(?:turn |switch )?(on|off) auto[- ]?rotate$|^auto[- ]?rotate (on|off)$").find(t)?.let { m ->
            needWriteSettings(c)?.let { return it }
            val on = (m.groupValues[1].ifEmpty { m.groupValues[2] }) == "on"
            Settings.System.putInt(c.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
            return "Auto-rotate ${if (on) "on" else "off"}."
        }
        return null
    }

    // ---------- ringer, Do Not Disturb, stream volumes ----------

    private fun ringer(c: Context, t: String): String? {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dnd = Regex("^(?:turn |switch )?(on|off) (?:do not disturb|dnd|don't disturb)$|^(?:do not disturb|dnd|don't disturb) (on|off)$").find(t)
        if (dnd != null) {
            if (!nm.isNotificationPolicyAccessGranted) {
                launch(c, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                return "I need Do Not Disturb access for that. I opened the page: allow BRO, then say it again."
            }
            val on = (dnd.groupValues[1].ifEmpty { dnd.groupValues[2] }) == "on"
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
            return "Do Not Disturb ${if (on) "on" else "off"}."
        }
        val mode = when {
            Regex("^(?:silent|silent mode|go silent|ringer silent|put (?:the )?phone on silent)$").matches(t) -> AudioManager.RINGER_MODE_SILENT
            Regex("^(?:vibrate|vibrate mode|vibration mode|put (?:the )?phone on vibrate|vibration on)$").matches(t) -> AudioManager.RINGER_MODE_VIBRATE
            Regex("^(?:normal|normal mode|ring|ringer on|sound mode|general mode|vibration off)$").matches(t) -> AudioManager.RINGER_MODE_NORMAL
            else -> return null
        }
        if (mode == AudioManager.RINGER_MODE_SILENT && !nm.isNotificationPolicyAccessGranted) {
            am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            return "Android only lets me use vibrate without Do Not Disturb access, so I set vibrate. Allow it in Settings for full silent."
        }
        am.ringerMode = mode
        return when (mode) { AudioManager.RINGER_MODE_SILENT -> "Silent mode on."; AudioManager.RINGER_MODE_VIBRATE -> "Vibrate mode on."; else -> "Sound mode on." }
    }

    private fun streams(c: Context, t: String): String? {
        val m = Regex("^(?:set )?(ring|ringtone|alarm|notification|call) volume(?: to)? (max|full|mute|off|zero|\\d{1,3}) ?(?:%|percent)?$").find(t) ?: return null
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = when (m.groupValues[1]) {
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "call" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_RING
        }
        val max = am.getStreamMaxVolume(stream)
        val v = when (val w = m.groupValues[2]) {
            "max", "full" -> max
            "mute", "off", "zero" -> 0
            else -> max * w.toInt().coerceIn(0, 100) / 100
        }
        am.setStreamVolume(stream, v, AudioManager.FLAG_SHOW_UI)
        return "${m.groupValues[1].replaceFirstChar { it.uppercase() }} volume set."
    }

    // ---------- global actions (Accessibility) ----------

    private fun global(t: String): String? {
        val action: Int
        val reply: String
        when {
            Regex("^(?:lock|lock the|lock my)(?: screen| phone)?$|^lock screen$|^screen lock$").matches(t) -> { action = AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN; reply = "Screen locked." }
            Regex("^(?:power off|shut ?down|switch off(?: the)? phone|turn off(?: the)? phone|restart|reboot|power menu)(?: the phone)?$").matches(t) -> { action = AccessibilityService.GLOBAL_ACTION_POWER_DIALOG; reply = "I opened the power menu. Android doesn't let apps switch the phone off, so tap Power off yourself." }
            Regex("^(?:split ?screen|split the screen|toggle split ?screen)$").matches(t) -> { action = AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN; reply = "Toggled split screen." }
            Regex("^(?:take (?:a )?screenshot|screenshot|capture screen)$").matches(t) -> { action = AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT; reply = "Screenshot taken." }
            Regex("^(?:recent apps|recents|show recents|open recents)$").matches(t) -> { action = AccessibilityService.GLOBAL_ACTION_RECENTS; reply = "Recent apps." }
            Regex("^(?:notifications|notification panel|open notifications|show notifications|pull down notifications)$").matches(t) -> { action = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS; reply = "Notifications." }
            Regex("^(?:quick settings|open quick settings|control center)$").matches(t) -> { action = AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS; reply = "Quick settings." }
            Regex("^(?:go )?home$|^home screen$").matches(t) -> { action = AccessibilityService.GLOBAL_ACTION_HOME; reply = "Home." }
            Regex("^go back$|^back$").matches(t) -> { action = AccessibilityService.GLOBAL_ACTION_BACK; reply = "Back." }
            else -> return null
        }
        val svc = BroAccessibilityService.instance
            ?: return "That needs BRO's Accessibility service. Turn it on in Settings."
        return if (svc.performGlobalAction(action)) reply else "Android didn't allow that on this phone."
    }

    // ---------- timer, settings pages ----------

    private fun timer(c: Context, t: String): String? {
        val m = Regex("^(?:set )?(?:a )?timer(?: for)? (\\d+) ?(second|sec|minute|min|hour|hr)s?$").find(t) ?: return null
        val n = m.groupValues[1].toInt()
        val secs = when { m.groupValues[2].startsWith("sec") -> n; m.groupValues[2].startsWith("h") -> n * 3600; else -> n * 60 }
        val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, secs).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(c, i)) "Timer set for $n ${m.groupValues[2]}${if (n == 1) "" else "s"}." else "No clock app could set the timer."
    }

    private val pages = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS, "wi-fi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS, "battery" to Intent.ACTION_POWER_USAGE_SUMMARY,
        "display" to Settings.ACTION_DISPLAY_SETTINGS, "sound" to Settings.ACTION_SOUND_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS, "nfc" to Settings.ACTION_NFC_SETTINGS,
        "hotspot" to "android.settings.TETHER_SETTINGS", "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS, "date" to Settings.ACTION_DATE_SETTINGS,
        "language" to Settings.ACTION_LOCALE_SETTINGS, "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "privacy" to Settings.ACTION_PRIVACY_SETTINGS, "security" to Settings.ACTION_SECURITY_SETTINGS,
        "notification" to Settings.ACTION_APP_NOTIFICATION_SETTINGS, "vpn" to Settings.ACTION_VPN_SETTINGS,
        "data usage" to Settings.ACTION_DATA_USAGE_SETTINGS, "airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        "cast" to Settings.ACTION_CAST_SETTINGS, "developer" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        "about phone" to Settings.ACTION_DEVICE_INFO_SETTINGS, "keyboard" to Settings.ACTION_INPUT_METHOD_SETTINGS,
        "mobile network" to Settings.ACTION_DATA_ROAMING_SETTINGS, "sim" to Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
        "default apps" to Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, "usage access" to Settings.ACTION_USAGE_ACCESS_SETTINGS
    )

    private fun settingsPage(c: Context, t: String): String? {
        val m = Regex("^(?:open |show |go to )?(?:the )?(.+?) settings$|^open (.+?) settings page$").find(t) ?: return null
        val name = (m.groupValues[1].ifEmpty { m.groupValues[2] }).removePrefix("the ").trim()
        val action = pages[name] ?: pages[name.removeSuffix("s")] ?: return null
        val i = Intent(action)
        if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) i.putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
        return if (launch(c, i)) "Opened ${name} settings." else "I couldn't open ${name} settings on this phone."
    }

    // ---------- web, maps ----------

    private fun web(c: Context, t: String): String? {
        Regex("^(?:navigate|directions|take me|route|drive|go)(?: me)? to (.+)$").find(t)?.let { m ->
            val place = m.groupValues[1].trim()
            val ok = launch(c, Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place))))
                || launch(c, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))))
            return if (ok) "Starting navigation to $place." else "I couldn't open a maps app."
        }
        Regex("^(?:google|search(?: google)?(?: for)?|web search|look up) (.+?)(?: on (?:google|the web|chrome))?$").find(t)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.length < 2 || Regex("^(?:youtube|whatsapp)\\b").containsMatchIn(q) || q.contains(" on youtube")) return null
            val ok = launch(c, Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q))
                || launch(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
            return if (ok) "Searching the web for $q." else "I couldn't open a browser."
        }
        Regex("^(?:open|go to|visit) ((?:https?://)?[\\w.-]+\\.(?:com|in|org|net|io|dev|app|co|edu|gov)(?:/\\S*)?)$").find(t)?.let { m ->
            val u = m.groupValues[1].let { if (it.startsWith("http")) it else "https://$it" }
            return if (launch(c, Intent(Intent.ACTION_VIEW, Uri.parse(u)))) "Opening ${m.groupValues[1]}." else "I couldn't open that link."
        }
        return null
    }

    // ---------- info ----------

    private fun info(c: Context, t: String): String? = when {
        Regex("^(?:how much )?(?:free )?storage(?: left| free| space)?$|^(?:free|available) space$|^how much storage.*").matches(t) -> {
            val st = StatFs(Environment.getDataDirectory().path)
            val free = st.availableBytes / 1_000_000_000.0
            val total = st.totalBytes / 1_000_000_000.0
            "%.1f GB free of %.1f GB.".format(free, total)
        }
        Regex("^(?:what(?:'s| is) )?(?:my |this )?phone(?: model| name)?$|^(?:which|what) phone.*|^phone model$").matches(t) ->
            "This is a ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}."
        Regex("^(?:what(?:'s| is) )?(?:my |the )?android version$").matches(t) -> "Android ${Build.VERSION.RELEASE} (security patch ${Build.VERSION.SECURITY_PATCH})."
        else -> null
    }

    // ---------- Quick Settings tiles (iQOO etc.) ----------

    private val tiles = listOf(
        "screen record" to "Screen record", "screen recording" to "Screen record", "record screen" to "Screen record",
        "live caption" to "Live Caption", "quick share" to "Quick Share", "select to speak" to "Select to Speak",
        "scan code" to "Scan code", "qr scanner" to "Scan code", "wallet" to "Wallet", "screen mirroring" to "Screen Mirroring",
        "cast" to "Cast", "mini screen" to "Mini screen", "mini window" to "Mini screen", "monster mode" to "Monster Mode",
        "office kit" to "Office Kit", "iqoo share" to "iQOO Share", "song search" to "Song Search", "live transcribe" to "Live Transcribe",
        "sound notifications" to "Sound Notifications", "super screenshot" to "Super screenshot", "speed up" to "Speed up",
        "global search" to "Global search", "vowifi" to "VoWiFi", "scan and pay" to "Scan and Pay", "device controls" to "Device controls",
        "switch sim" to "Switch SIM", "vibration" to "Vibration", "calculator" to "Calculator"
    )

    private fun tile(t: String, onAsync: (String) -> Unit): String? {
        val m = Regex("^(?:turn |switch )?(on|off|start|stop|enable|disable|open|toggle)(?: the)? (.+)$|^(.+?) (on|off)$").find(t) ?: return null
        val verb = m.groupValues[1].ifEmpty { m.groupValues[4] }
        val subject = (m.groupValues[2].ifEmpty { m.groupValues[3] }).trim()
        val label = tiles.firstOrNull { it.first == subject }?.second ?: return null
        val svc = BroAccessibilityService.instance ?: return "That needs BRO's Accessibility service. Turn it on in Settings."
        val state: Boolean? = when (verb) { "on", "start", "enable", "open" -> true; "off", "stop", "disable" -> false; else -> null }
        svc.setQuickSetting(label, state) { _, msg -> onAsync(msg) }
        return "Okay."
    }
}
