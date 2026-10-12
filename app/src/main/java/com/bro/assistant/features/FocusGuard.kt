package com.bro.assistant.features

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.bro.assistant.BroAccessibilityService
import com.bro.assistant.proactive.Proactive

/** Study focus: while it is on, BRO counts the time spent in distracting apps and gently warns you. */
object FocusGuard {
    private val I = RegexOption.IGNORE_CASE
    private val onRx = Regex("""^(?:start\s+|turn\s+on\s+)?(?:focus|study)(?:\s+(?:mode|session))?(?:\s+on)?(?:\s+for\s+(\d{1,3})\s*(minutes?|mins?|hours?|hrs?))?[.!\s]*$|^i(?:'m|\s+am)\s+(?:going\s+to\s+)?study(?:ing)?[.!\s]*$""", I)
    private val offRx = Regex("""^(?:stop|end|turn\s+off|exit)\s+(?:the\s+)?(?:focus|study)(?:\s+(?:mode|session))?[.!\s]*$|^(?:focus|study)\s+(?:mode\s+)?off[.!\s]*$""", I)
    private val statusRx = Regex("""^(?:focus|study)\s+(?:report|status)[?.!\s]*$""", I)

    private val distracting = setOf(
        "com.instagram.android", "com.google.android.youtube", "com.facebook.katana", "com.snapchat.android",
        "com.twitter.android", "com.reddit.frontpage", "com.netflix.mediaclient", "com.zhiliaoapp.musically",
        "in.mohalla.sharechat", "com.pubg.imobile", "com.dts.freefireth", "com.supercell.clashofclans",
        "com.king.candycrushsaga", "com.amazon.avod.thirdpartyclient", "in.startv.hotstar"
    )

    @Volatile var active = false
        private set
    private var endAt = 0L
    private var startedAt = 0L
    private var current: String? = null
    private var since = 0L
    private var nextAlertMs = 10 * 60_000L
    private val perApp = HashMap<String, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var appCtx: Context

    private fun total() = perApp.values.sum()

    private fun flush(now: Long) {
        val cur = current
        val awake = try { (appCtx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive } catch (_: Exception) { true }
        if (cur != null && cur in distracting && awake) perApp[cur] = (perApp[cur] ?: 0L) + (now - since)
        since = now
    }

    private fun label(pkg: String): String = try {
        appCtx.packageManager.getApplicationLabel(appCtx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return
            val now = System.currentTimeMillis()
            flush(now)
            check()
            if (now >= endAt) { val s = stop(); Proactive.alert(appCtx, "Focus session over", s) }
            else handler.postDelayed(this, 30_000L)
        }
    }

    private fun check() {
        if (total() >= nextAlertMs) {
            val top = perApp.maxByOrNull { it.value }
            nextAlertMs += 10 * 60_000L
            Proactive.alert(appCtx, "Focus", "Boss, you've spent ${total() / 60_000} minutes on distracting apps" +
                (if (top != null) ", mostly ${label(top.key)}" else "") + ". Back to studying?")
        }
    }

    /** Called by the accessibility service whenever another app comes to the front. */
    fun onForeground(c: Context, pkg: String) {
        if (!active) return
        appCtx = c.applicationContext
        val now = System.currentTimeMillis()
        flush(now)
        current = pkg
        check()
    }

    fun start(c: Context, minutes: Int): String {
        appCtx = c.applicationContext
        active = true; startedAt = System.currentTimeMillis(); endAt = startedAt + minutes * 60_000L
        perApp.clear(); nextAlertMs = 10 * 60_000L; current = BroAccessibilityService.currentPackage; since = startedAt
        handler.removeCallbacks(ticker); handler.postDelayed(ticker, 30_000L)
        return "Focus mode on for $minutes minutes. I'll warn you if you spend too long on apps like Instagram or YouTube."
    }

    fun stop(): String {
        if (!active) return "Focus mode is not on."
        flush(System.currentTimeMillis())
        active = false; handler.removeCallbacks(ticker)
        val mins = (System.currentTimeMillis() - startedAt) / 60_000L
        val lost = total() / 60_000L
        return "Focus session over: $mins minutes, $lost of them on distracting apps." +
            (if (lost <= 2) " Excellent discipline, boss!" else " Let's do better next time.")
    }

    private fun report(): String {
        if (!active) return "Focus mode is not on. Say \"focus mode\" to start."
        flush(System.currentTimeMillis())
        val top = perApp.entries.sortedByDescending { it.value }.take(3).joinToString { "${label(it.key)} ${it.value / 60_000L} min" }
        return "Focus so far: ${(System.currentTimeMillis() - startedAt) / 60_000L} minutes, ${total() / 60_000L} on distracting apps" + (if (top.isNotEmpty()) " ($top)." else ".")
    }

    fun handle(c: Context, raw: String): String? {
        val t = raw.trim()
        appCtx = c.applicationContext
        if (offRx.matches(t)) return stop()
        if (statusRx.matches(t)) return report()
        val m = onRx.find(t) ?: return null
        var mins = 60
        val n = m.groupValues[1].toIntOrNull()
        if (n != null) mins = if (m.groupValues[2].startsWith("h", true)) n * 60 else n
        val warn = if (!BroAccessibilityService.isEnabled(c)) " (Turn on BRO's Accessibility service in Settings, or I can't see which app you're using.)" else ""
        return start(c, mins.coerceIn(5, 480)) + warn
    }
}
