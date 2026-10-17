package com.bro.assistant.actions

import android.content.Context
import com.bro.assistant.memory.PreferencesStore

/**
 * UI test runner for the phone itself: "test Settings: open Battery; check it shows a percentage".
 * Each step is done by the phone agent, which then says whether what you expected is really on screen.
 * The result is a PASS / FAIL report per step.
 */
object PhoneTests {
    private val rx = Regex(
        "^(?:please\\s+)?(?:run\\s+(?:a\\s+)?)?(?:ui\\s+|app\\s+|phone\\s+)?test(?:ing)?\\s+(?:of\\s+|on\\s+|for\\s+)?(.{2,40}?)\\s*[:\\-]\\s*(.{6,})$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun matches(text: String) = rx.matches(text.trim())

    suspend fun handle(c: Context, prefs: PreferencesStore, text: String, confirm: suspend (String) -> Boolean): String? {
        val m = rx.matchEntire(text.trim()) ?: return null
        val target = m.groupValues[1].trim()
        val steps = m.groupValues[2].split(Regex("\\s*(?:;|\\n|\\bthen\\b)\\s*", RegexOption.IGNORE_CASE)).map { it.trim().trimEnd('.') }.filter { it.length > 2 }.take(12)
        if (steps.isEmpty()) return null
        com.bro.assistant.Activity.step("Testing $target: ${steps.size} steps")
        val agent = AppAgent(c, prefs)
        val lines = ArrayList<String>()
        var pass = 0
        val started = System.currentTimeMillis()
        for ((i, step) in steps.withIndex()) {
            val t0 = System.currentTimeMillis()
            com.bro.assistant.Activity.step("Test step ${i + 1} of ${steps.size}: ${step.take(40)}")
            val goal = "TEST of \"$target\". Do this test step: $step. " +
                "If the step is a check or expectation, look at the screen and verify it. Finish with done only if it really holds " +
                "(say what you saw), or fail with the reason if it does not."
            val r = try { agent.run(goal, confirm) { } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { com.bro.assistant.task.ActionResult.fail(e.message ?: "error") }
            val secs = (System.currentTimeMillis() - t0) / 1000
            if (r.success) pass++
            lines.add("${if (r.success) "PASS" else "FAIL"}  ${i + 1}. $step (${secs}s)\n      ${r.message.take(160)}")
            if (!r.success && r.needsPermission != null) { lines.add("Stopped: ${r.message}"); break }
        }
        val total = (System.currentTimeMillis() - started) / 1000
        val head = "Test of $target: $pass of ${steps.size} steps passed in ${total}s" + if (pass == steps.size) " - all good." else "."
        val report = head + "\n\n" + lines.joinToString("\n")
        try { saveReport(c, target, report) } catch (_: Exception) {}
        return report + "\n\n(Report saved in Downloads/BRO.)"
    }

    private fun saveReport(c: Context, target: String, text: String) {
        if (android.os.Build.VERSION.SDK_INT < 29) return
        val name = "BRO-test-" + target.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(20) + "-" + System.currentTimeMillis() / 1000 + ".txt"
        val v = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/BRO")
        }
        val uri = c.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return
        c.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }
}
