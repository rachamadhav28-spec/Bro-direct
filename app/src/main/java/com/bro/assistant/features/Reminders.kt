package com.bro.assistant.features

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bro.assistant.proactive.Proactive
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** "Remind me to call mom at 6 pm" - saved on the phone, spoken and shown as a notification at that time. */
object Reminders {
    data class R(val id: Long, val text: String, val at: Long)

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("bro_reminders", Context.MODE_PRIVATE)

    fun all(c: Context): List<R> = try {
        val a = JSONArray(sp(c).getString("list", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); R(o.getLong("id"), o.getString("t"), o.getLong("at")) }.sortedBy { it.at }
    } catch (e: Exception) { emptyList() }

    private fun save(c: Context, l: List<R>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("t", it.text).put("at", it.at)) }
        sp(c).edit().putString("list", a.toString()).apply()
    }

    private fun pi(c: Context, id: Long) = PendingIntent.getBroadcast(
        c, (id % 1_000_000_000L).toInt(), Intent(c, ReminderReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun schedule(c: Context, r: R) {
        try {
            (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi(c, r.id))
        } catch (_: Exception) {}
    }

    fun add(c: Context, text: String, at: Long): R {
        val r = R(System.currentTimeMillis(), text, at)
        save(c, all(c) + r); schedule(c, r); return r
    }

    fun remove(c: Context, id: Long) {
        try { (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(c, id)) } catch (_: Exception) {}
        save(c, all(c).filter { it.id != id })
    }

    /** After a phone restart: set the alarms again, and say the ones that were missed. */
    fun rescheduleAll(c: Context) {
        val now = System.currentTimeMillis()
        for (r in all(c)) { if (r.at > now) schedule(c, r) else fire(c, r.id, missed = true) }
    }

    fun fire(c: Context, id: Long, missed: Boolean = false) {
        val r = all(c).firstOrNull { it.id == id } ?: return
        save(c, all(c).filter { it.id != id })
        Proactive.alert(c, "Reminder", (if (missed) "Boss, you missed this reminder: " else "Boss, reminder: ") + r.text)
    }

    // ---------- understanding the sentence ----------

    private val I = RegexOption.IGNORE_CASE
    private val cmd = Regex("""^(?:please\s+)?(?:remind\s+me|set\s+(?:a\s+)?reminder)\s*(?:to\s+|about\s+|that\s+|for\s+)?(.{2,300})$""", I)
    private val rel = Regex("""\bin\s+(\d{1,3}|an?|one|two|three|five|ten|fifteen|twenty|thirty)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)\b""", I)
    private val abs = Regex("""\b(?:at|by)\s+(\d{1,2})(?:[:.](\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?""", I)
    private val dayWord = Regex("""\b(day\s+after\s+tomorrow|tomorrow|today|tonight)\b""", I)
    private val weekday = Regex("""\b(?:on\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", I)
    private val listRx = Regex("""^(?:what(?:'s|\s+are|\s+is)?\s+)?(?:show\s+|list\s+)?(?:my\s+)?reminders?(?:\s+list)?[?.!\s]*$""", I)
    private val cancelRx = Regex("""^(?:cancel|delete|remove|clear)\s+(?:all\s+)?(?:my\s+)?reminders?(?:\s+(?:about|for|to)\s+(.+))?[.!\s]*$""", I)

    private fun num(s: String) = when (s.lowercase()) {
        "a", "an", "one" -> 1; "two" -> 2; "three" -> 3; "five" -> 5; "ten" -> 10; "fifteen" -> 15; "twenty" -> 20; "thirty" -> 30
        else -> s.toIntOrNull() ?: 1
    }

    /** Returns (what to remind, when) or null when no time was given. */
    fun parse(body: String, now: Calendar = Calendar.getInstance()): Pair<String, Long>? {
        var text = body
        val cal = now.clone() as Calendar
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)

        rel.find(text)?.let { m ->
            val n = num(m.groupValues[1]); val u = m.groupValues[2].lowercase()
            val ms = when {
                u.startsWith("sec") -> n * 1000L
                u.startsWith("min") -> n * 60_000L
                u.startsWith("h") -> n * 3_600_000L
                else -> n * 86_400_000L
            }
            text = text.removeRange(m.range)
            return clean(text) to now.timeInMillis + ms
        }

        var dayOffset = -1
        dayWord.find(text)?.let { m ->
            dayOffset = when (m.groupValues[1].lowercase().replace(Regex("\\s+"), " ")) {
                "tomorrow" -> 1; "day after tomorrow" -> 2; else -> 0
            }
            if (m.groupValues[1].equals("tonight", true)) cal.set(Calendar.HOUR_OF_DAY, 20)
            text = text.removeRange(m.range)
        }
        weekday.find(text)?.let { m ->
            val names = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
            val want = names.indexOf(m.groupValues[1].lowercase()) + 1
            var diff = (want - now.get(Calendar.DAY_OF_WEEK) + 7) % 7
            if (diff == 0) diff = 7
            dayOffset = diff
            text = text.removeRange(m.range)
        }
        var hour = -1; var minute = 0; var mer = ""
        abs.find(text)?.let { m ->
            hour = m.groupValues[1].toInt(); minute = m.groupValues[2].ifBlank { "0" }.toInt()
            mer = m.groupValues[3].lowercase().replace(".", "")
            text = text.removeRange(m.range)
        }
        if (hour < 0 && dayOffset < 0) return null
        if (hour > 23 || minute > 59) return null

        if (hour < 0) { hour = if (cal.get(Calendar.HOUR_OF_DAY) == 20 && dayOffset == 0) 20 else 9; minute = 0 }
        else if (mer == "pm" && hour < 12) hour += 12
        else if (mer == "am" && hour == 12) hour = 0

        cal.set(Calendar.MINUTE, minute)
        val tries = ArrayList<Long>()
        fun at(h: Int, d: Int): Long = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, d); set(Calendar.HOUR_OF_DAY, h) }.timeInMillis
        val d0 = if (dayOffset >= 0) dayOffset else 0
        tries.add(at(hour, d0))
        if (mer.isEmpty() && hour in 1..11 && dayOffset < 0) tries.add(at(hour + 12, d0))   // "at 6" with no am/pm: next 6 o'clock
        if (dayOffset < 0) tries.add(at(hour, d0 + 1))
        val whenMs = tries.firstOrNull { it > now.timeInMillis + 30_000L } ?: return null
        return clean(text) to whenMs
    }

    private fun clean(s: String): String =
        s.replace(Regex("\\s+"), " ").trim().trim(',', '.', '!').replace(Regex("^(?:to|about|that|me|for)\\s+", I), "").trim()

    private fun pretty(ms: Long) = SimpleDateFormat("EEE d MMM, h:mm a", Locale.US).format(ms)

    fun handle(c: Context, raw: String): String? {
        val t = raw.trim()
        if (listRx.matches(t)) {
            val l = all(c)
            return if (l.isEmpty()) "You have no reminders. Say \"remind me to ... at 6 pm\"."
            else "Your reminders:\n" + l.mapIndexed { i, r -> "${i + 1}. ${r.text} - ${pretty(r.at)}" }.joinToString("\n")
        }
        cancelRx.find(t)?.let { m ->
            val key = m.groupValues[1].trim().lowercase()
            val l = all(c)
            val hit = if (key.isEmpty()) l else l.filter { key.split(" ").filter { w -> w.length > 2 }.all { w -> w in it.text.lowercase() } }
            if (hit.isEmpty()) return "I couldn't find a reminder like that."
            hit.forEach { remove(c, it.id) }
            return "Done. I cancelled ${hit.size} reminder${if (hit.size > 1) "s" else ""}."
        }
        val m = cmd.find(t) ?: return null
        val parsed = parse(m.groupValues[1])
            ?: return "When should I remind you? Say it like \"remind me to call mom at 6 pm\", \"in 10 minutes\", or \"tomorrow at 7 am\"."
        if (parsed.first.length < 2) return "What should I remind you about? Say it like \"remind me to call mom at 6 pm\"."
        val r = add(c, parsed.first, parsed.second)
        return "Okay, I'll remind you to ${r.text} on ${pretty(r.at)}."
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0L)
        if (id != 0L) try { Reminders.fire(context.applicationContext, id) } catch (_: Exception) {}
    }
}
