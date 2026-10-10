package com.bro.assistant.actions

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.bro.assistant.BroAccessibilityService
import kotlinx.coroutines.delay

/** Helpers for finding and tapping things on screen through the Accessibility service. */
object UiFinder {

    /** Everything currently on screen, in reading order. */
    fun snapshot(): List<AccessibilityNodeInfo> = allNodes()

    private fun allNodes(): List<AccessibilityNodeInfo> {
        val root = BroAccessibilityService.instance?.rootInActiveWindow ?: return emptyList()
        val out = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty() && out.size < 2000) {
            val node = stack.removeLast()
            out.add(node)
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return out
    }

    private fun textOf(n: AccessibilityNodeInfo): String = n.text?.toString() ?: ""
    private fun descOf(n: AccessibilityNodeInfo): String = n.contentDescription?.toString() ?: ""

    /** First on-screen node whose text or description contains any label (labels tried in order). */
    fun findContaining(vararg labels: String): AccessibilityNodeInfo? {
        val nodes = allNodes()
        for (label in labels) {
            nodes.firstOrNull {
                textOf(it).contains(label, ignoreCase = true) || descOf(it).contains(label, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }

    /** Finds a Quick Settings tile: exact label first, then partial; skips nodes containing [exclude]. */
    fun findTile(labels: List<String>, exclude: String? = null): AccessibilityNodeInfo? {
        val nodes = allNodes().filter {
            exclude == null || !(textOf(it).contains(exclude, true) || descOf(it).contains(exclude, true))
        }
        for (label in labels) {
            nodes.firstOrNull { textOf(it).equals(label, true) || descOf(it).equals(label, true) }?.let { return it }
        }
        for (label in labels) {
            nodes.firstOrNull { textOf(it).contains(label, true) || descOf(it).contains(label, true) }?.let { return it }
        }
        return null
    }

    /** First on-screen node whose text or description equals the label. */
    fun findExact(label: String): AccessibilityNodeInfo? =
        allNodes().firstOrNull {
            textOf(it).equals(label, ignoreCase = true) || descOf(it).equals(label, ignoreCase = true)
        }

    fun findEditable(): AccessibilityNodeInfo? = allNodes().firstOrNull { it.isEditable }

    /** Taps the node, or the nearest clickable parent. */
    fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable && current.isEnabled) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Reads an on/off state from a node or its parents. Returns null when it can't be read. */
    fun stateOf(node: AccessibilityNodeInfo): Boolean? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 4) {
            val n: AccessibilityNodeInfo = current
            if (n.isCheckable) return n.isChecked
            val desc = StringBuilder()
            desc.append(descOf(n)).append(' ')
            if (Build.VERSION.SDK_INT >= 30) desc.append(n.stateDescription ?: "")
            val d = desc.toString().lowercase()
            if (Regex("\\boff\\b").containsMatchIn(d)) return false
            if (Regex("\\bon\\b").containsMatchIn(d)) return true
            current = n.parent
            depth++
        }
        return null
    }

    suspend fun waitFor(timeoutMs: Long = 6000, check: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (check()) return true
            delay(300)
        }
        return check()
    }

    suspend fun waitForNode(
        labels: List<String>,
        timeoutMs: Long = 5000,
        exact: Boolean = false
    ): AccessibilityNodeInfo? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            val node = if (exact) labels.firstNotNullOfOrNull { findExact(it) }
            else findContaining(*labels.toTypedArray())
            if (node != null) return node
            if (SystemClock.uptimeMillis() >= end) return null
            delay(300)
        }
    }
}
