package com.bro.assistant.ai

import android.app.AlarmManager
import android.content.Context
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.memory.TaskLog
import com.bro.assistant.proactive.CalendarReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "Good morning": time, next alarm, today's weather, today's calendar and what BRO finished while you were away. */
object Briefing {
    val trigger = Regex(
        "^(?:good\\s+(?:morning|evening|afternoon)|morning|gm|(?:give\\s+me\\s+)?(?:my\\s+)?(?:morning\\s+)?(?:briefing|brief)(?:\\s+me)?|brief\\s+me|what(?:'s|\\s+is)\\s+(?:my\\s+)?(?:plan|schedule)\\s+(?:for\\s+)?today|suprabhatam)(?:\\s+bro)?[.!?\\s]*$",
        RegexOption.IGNORE_CASE
    )

    private fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when { h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
    }

    private fun nextAlarm(ctx: Context): String {
        val a = try { (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock } catch (_: Exception) { null }
            ?: return "You have no alarm set."
        val at = Date(a.triggerTime)
        val sameDay = SimpleDateFormat("yyyyMMdd", Locale.US).format(at) == SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val time = SimpleDateFormat("h:mm a", Locale.US).format(at)
        return "Your next alarm is at $time" + (if (sameDay) " today." else " on " + SimpleDateFormat("EEEE", Locale.US).format(at) + ".")
    }

    private fun weatherWord(code: Int) = when (code) {
        0 -> "clear sky"; 1, 2 -> "mostly clear"; 3 -> "cloudy"; 45, 48 -> "foggy"
        51, 53, 55, 56, 57 -> "drizzle"; 61, 63, 65, 66, 67 -> "rain"; 71, 73, 75, 77 -> "snow"
        80, 81, 82 -> "rain showers"; 95, 96, 99 -> "thunderstorms"; else -> "mixed weather"
    }

    private suspend fun weather(city: String): String? = withContext(Dispatchers.IO) {
        try {
            fun get(u: String): String {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
            }
            val g = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8")))
                .optJSONArray("results")?.optJSONObject(0) ?: return@withContext null
            val w = JSONObject(get(
                "https://api.open-meteo.com/v1/forecast?latitude=${g.getDouble("latitude")}&longitude=${g.getDouble("longitude")}" +
                    "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1"
            ))
            val cur = w.getJSONObject("current")
            val d = w.getJSONObject("daily")
            val rain = d.getJSONArray("precipitation_probability_max").optInt(0)
            "Weather in ${g.getString("name")}: ${weatherWord(cur.getInt("weather_code"))}, ${Math.round(cur.getDouble("temperature_2m"))}°C now. " +
                "Today ${Math.round(d.getJSONArray("temperature_2m_min").getDouble(0))} to ${Math.round(d.getJSONArray("temperature_2m_max").getDouble(0))}°C, " +
                "chance of rain $rain%."
        } catch (_: Exception) { null }
    }

    suspend fun build(ctx: Context): String {
        val prefs = PreferencesStore(ctx)
        val now = Date()
        val sb = StringBuilder()
        sb.append(greeting()).append(", boss. It's ").append(SimpleDateFormat("h:mm a", Locale.US).format(now))
            .append(" on ").append(SimpleDateFormat("EEEE, d MMMM", Locale.US).format(now)).append(".\n")
        sb.append(nextAlarm(ctx)).append('\n')

        if (prefs.city.isBlank()) sb.append("Tell me your city (say \"my city is ...\") and I'll add the weather.\n")
        else sb.append(weather(prefs.city) ?: "I couldn't get the weather right now (no internet?).").append('\n')

        if (CalendarReader.allowed(ctx)) {
            val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val evs = CalendarReader.between(ctx, System.currentTimeMillis(), start + 24 * 3600_000L)
            if (evs.isEmpty()) sb.append("Nothing on your calendar for the rest of today.\n")
            else sb.append("On your calendar today: ").append(evs.take(4).joinToString("; ") {
                it.title + if (it.allDay) "" else " at " + SimpleDateFormat("h:mm a", Locale.US).format(Date(it.start))
            }).append(".\n")
        }

        val since = maxOf(TaskLog.lastBriefing(ctx), System.currentTimeMillis() - 14 * 3600_000L)
        val done = TaskLog.all(ctx).filter { it.away && it.time >= since }
        if (done.isEmpty()) sb.append("No tasks finished while you were away.")
        else sb.append("While you were away I finished ${done.size} task${if (done.size > 1) "s" else ""}: ")
            .append(done.takeLast(4).joinToString("; ") { it.command.take(40) + " (" + it.result.take(50).trim() + ")" }).append('.')
        TaskLog.markBriefing(ctx)
        return sb.toString().trim()
    }
}
