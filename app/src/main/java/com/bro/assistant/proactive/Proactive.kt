package com.bro.assistant.proactive

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bro.assistant.MainActivity
import com.bro.assistant.PermissionManager
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.voice.SpeechOutput
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** BRO speaks up by itself: low battery, tomorrow's events (exams, tests...) and events starting soon. */
object Proactive {
    private const val ACTION = "com.bro.assistant.PROACTIVE_CHECK"
    private const val CHANNEL = "bro_alerts"
    private const val EVERY_MS = 15 * 60_000L

    fun schedule(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                ctx, 7, Intent(ctx, ProactiveReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + EVERY_MS, pi)
        } catch (_: Exception) {}
    }

    fun cancel(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(PendingIntent.getBroadcast(ctx, 7, Intent(ctx, ProactiveReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } catch (_: Exception) {}
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("bro_proactive", Context.MODE_PRIVATE)

    fun check(ctx: Context) {
        val prefs = PreferencesStore(ctx)
        if (!prefs.proactiveEnabled) return
        val state = sp(ctx)

        // 1) battery
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bat != null) {
            val level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / maxOf(1, bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100))
            val charging = bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val last = state.getInt("battery_alert", 100)
            if (charging || level > 25) state.edit().putInt("battery_alert", 100).apply()
            else if (level <= 15 && level < last && (last == 100 || level <= 5)) {
                state.edit().putInt("battery_alert", if (level <= 5) 5 else 15).apply()
                alert(ctx, "Battery low", "Boss, your battery is at $level percent. Please plug in the charger.")
            }
        }

        // 2) calendar
        if (CalendarReader.allowed(ctx)) {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))

            // evening heads-up about tomorrow
            if (cal.get(Calendar.HOUR_OF_DAY) in 18..22 && state.getString("tomorrow_done", "") != today) {
                val midnight = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                val evs = CalendarReader.between(ctx, midnight, midnight + 24 * 3600_000L)
                state.edit().putString("tomorrow_done", today).apply()
                if (evs.isNotEmpty()) {
                    val key = Regex("exam|test|viva|deadline|interview|submission|quiz|lab", RegexOption.IGNORE_CASE)
                    val important = evs.firstOrNull { key.containsMatchIn(it.title) }
                    val list = evs.take(3).joinToString("; ") { it.title + if (it.allDay) "" else " at " + SimpleDateFormat("h:mm a", Locale.US).format(Date(it.start)) }
                    alert(ctx, "Tomorrow", (if (important != null) "Boss, you have ${important.title} tomorrow. " else "Tomorrow you have: ") + (if (important != null) "Full list: " else "") + list + ".")
                }
            }

            // something starting within the next hour
            val soon = CalendarReader.between(ctx, now, now + 70 * 60_000L).filter { !it.allDay && it.start - now > 5 * 60_000L }
            val seen = state.getStringSet("soon_seen", emptySet())!!.toMutableSet()
            for (e in soon) {
                val k = "${e.id}_${e.start}"
                if (k in seen) continue
                seen.add(k)
                val mins = ((e.start - now) / 60_000L).toInt()
                alert(ctx, "Coming up", "Boss, ${e.title} starts in about $mins minutes.")
            }
            state.edit().putStringSet("soon_seen", seen.toList().takeLast(30).toSet()).apply()
        }
    }

    @SuppressLint("MissingPermission")
    private fun alert(ctx: Context, title: String, text: String) {
        if (PermissionManager.hasNotifications(ctx)) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "BRO alerts", NotificationManager.IMPORTANCE_HIGH))
            }
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("BRO: $title").setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).setContentIntent(open).build()
            NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt() + 3000, n)
        }
        // say it aloud too, but only when the phone is awake, so BRO never talks in your pocket at night
        val prefs = PreferencesStore(ctx)
        val awake = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (prefs.speakReplies && awake) {
            var out: SpeechOutput? = null
            out = SpeechOutput(ctx.applicationContext, prefs)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                out?.speak(text, onDone = { out?.shutdown() })
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ out?.shutdown() }, 20000)
            }, 1500)
        }
    }
}

class ProactiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        try { Proactive.check(app) } catch (_: Exception) {}
        if (PreferencesStore(app).proactiveEnabled) Proactive.schedule(app)
    }
}
