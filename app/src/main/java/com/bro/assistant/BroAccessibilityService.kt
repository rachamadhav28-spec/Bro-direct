package com.bro.assistant

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.provider.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BroAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BroAccessibilityService? = null

        /** Package name of the app currently in the foreground (best effort). */
        @Volatile
        var currentPackage: String = ""

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val component = ComponentName(context, BroAccessibilityService::class.java)
            val full = component.flattenToString()
            val short = component.flattenToShortString()
            return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
        }
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != "com.android.systemui" && !pkg.contains("inputmethod")) {
                currentPackage = pkg
            }
        }
    }

    /** Horizontal swipe across the middle of the screen. [left] = finger moves right to left. */
    suspend fun swipeHorizontal(left: Boolean): Boolean {
        val dm = resources.displayMetrics
        val y = dm.heightPixels * 0.55f
        val from = dm.widthPixels * (if (left) 0.85f else 0.15f)
        val to = dm.widthPixels * (if (left) 0.15f else 0.85f)
        val path = Path().apply { moveTo(from, y); lineTo(to, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
            .build()
        val done = CompletableDeferred<Boolean>()
        val started = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { done.complete(true) }
            override fun onCancelled(g: GestureDescription?) { done.complete(false) }
        }, null)
        if (!started) return false
        return withTimeoutOrNull(1500) { done.await() } ?: false
    }

    private suspend fun stroke(path: Path, ms: Long): Boolean {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
        val done = CompletableDeferred<Boolean>()
        val started = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { done.complete(true) }
            override fun onCancelled(g: GestureDescription?) { done.complete(false) }
        }, null)
        if (!started) return false
        return withTimeoutOrNull(2000) { done.await() } ?: false
    }

    suspend fun tap(x: Float, y: Float): Boolean =
        stroke(Path().apply { moveTo(x, y); lineTo(x + 1f, y) }, 60)

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
        stroke(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, 350)

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Quick Settings tile toggling (Ultra Game Mode, Bluetooth, DND, ...)
    // ------------------------------------------------------------------

    /**
     * Opens the Quick Settings panel, finds the tile whose label contains [label],
     * taps it only if its current state differs from [turnOn] (when the state can be read),
     * then closes the panel.
     *
     * [turnOn] = true -> make sure it is ON, false -> make sure it is OFF, null -> just toggle.
     * [onResult] is called on the main thread with a short status message.
     */
    fun setQuickSetting(label: String, turnOn: Boolean?, onResult: (Boolean, String) -> Unit) {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        // give the panel time to appear, then try a few times (it may need to be expanded)
        attempt(label, turnOn, tries = 0, onResult = onResult, delayMs = 800)
    }

    private fun attempt(
        label: String,
        turnOn: Boolean?,
        tries: Int,
        onResult: (Boolean, String) -> Unit,
        delayMs: Long
    ) {
        main.postDelayed({
            val root = rootInActiveWindow
            val tile = root?.let { findTile(it, label) }
            if (tile != null) {
                val state = readState(tile)
                val needsClick = turnOn == null || state == null || state != turnOn
                val ok = if (needsClick) clickNodeOrParent(tile) else true
                main.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 150)
                }, 500)
                val msg = when {
                    !ok -> "Couldn't tap $label"
                    !needsClick -> "$label is already ${if (turnOn == true) "on" else "off"}"
                    turnOn == true -> "$label turned on"
                    turnOn == false -> "$label turned off"
                    else -> "$label toggled"
                }
                onResult(ok, msg)
            } else if (tries < 4) {
                // try scrolling the panel in case the tile is below the fold
                root?.let { scrollForward(it) }
                attempt(label, turnOn, tries + 1, onResult, 600)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
                onResult(false, "Couldn't find $label in Quick Settings")
            }
        }, delayMs)
    }

    private fun findTile(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val want = label.lowercase().trim()
        // 1) system lookup by text
        root.findAccessibilityNodeInfosByText(label).firstOrNull()?.let { return it }
        // 2) manual walk: labels can be truncated on screen, node text is usually full
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = (n.text?.toString() ?: "").lowercase()
            val d = (n.contentDescription?.toString() ?: "").lowercase()
            if (t.contains(want) || d.contains(want)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    /** true = on, false = off, null = unknown */
    private fun readState(node: AccessibilityNodeInfo): Boolean? {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 4) {
            val s = (if (android.os.Build.VERSION.SDK_INT >= 30) n.stateDescription?.toString() else null)
                ?.lowercase()
            val d = n.contentDescription?.toString()?.lowercase()
            val txt = s ?: d
            if (txt != null) {
                if (Regex("\\boff\\b").containsMatchIn(txt)) return false
                if (Regex("\\bon\\b").containsMatchIn(txt)) return true
            }
            if (n.isCheckable) return n.isChecked
            n = n.parent
            depth++
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 5) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            depth++
        }
        return false
    }

    private fun scrollForward(root: AccessibilityNodeInfo) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isScrollable && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
    }
}
