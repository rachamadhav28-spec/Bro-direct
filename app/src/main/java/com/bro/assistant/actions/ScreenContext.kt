package com.bro.assistant.actions

import android.view.accessibility.AccessibilityNodeInfo
import com.bro.assistant.BroAccessibilityService

/**
 * What BRO can "see" on the screen right now, as plain text read through the Accessibility service
 * (names of buttons, labels and text). It is only used when you refer to the screen
 * ("open settings in this page", "what is on my screen"), and password fields are never read.
 */
object ScreenContext {
    private val refersToScreen = Regex(
        "\\b(?:this|current|the)\\s+(?:page|screen|app|window|chat|post|video|message|menu|tab)\\b|\\bon\\s+(?:my\\s+|the\\s+)?screen\\b|\\bhere\\b|\\bwhat(?:'s| is)\\s+on\\b|" +
            "\\b(?:ee|ikkada|ippudu)\\s*(?:screen|page|app)?\\b|ఈ\\s*(?:స్క్రీన్|పేజీ|యాప్)",
        RegexOption.IGNORE_CASE
    )

    fun isAboutScreen(text: String) = refersToScreen.containsMatchIn(text)

    /** Empty when the service is off or nothing readable is on screen. */
    fun describe(maxChars: Int = 1600): String {
        val svc = BroAccessibilityService.instance ?: return ""
        val root = try { svc.rootInActiveWindow } catch (e: Exception) { null } ?: return ""
        val pkg = root.packageName?.toString() ?: return ""
        val seen = LinkedHashSet<String>()
        val clickable = LinkedHashSet<String>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var count = 0
        while (stack.isNotEmpty() && count < 1500) {
            val n = stack.removeLast(); count++
            if (!n.isPassword && n.isVisibleToUser) {
                val t = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim().replace(Regex("\\s+"), " ")
                if (t.length in 1..80) { seen.add(t); if (n.isClickable) clickable.add(t) }
            }
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.addLast(it) }
        }
        if (seen.isEmpty()) return ""
        val sb = StringBuilder("Screen right now (app: $pkg).\n")
        if (clickable.isNotEmpty()) sb.append("Tappable: ").append(clickable.take(40).joinToString(" | ")).append('\n')
        sb.append("All visible text: ").append(seen.take(80).joinToString(" | "))
        return sb.toString().take(maxChars) + "\n\n"
    }
}
