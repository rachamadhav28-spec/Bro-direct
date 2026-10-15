package com.bro.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What BRO is doing right now, step by step ("Searching the web", "Running a command", "Reading the screen").
 * The chat screen shows it as a live card; when the answer is ready it stays as a short "Worked for 12s" summary.
 */
object Activity {
    class Item(val label: String, val done: Boolean)

    var items by mutableStateOf(listOf<Item>()); private set
    var startedAt by mutableLongStateOf(0L); private set
    var active by mutableStateOf(false); private set
    var lastItems by mutableStateOf(listOf<Item>()); private set
    var lastSeconds by mutableIntStateOf(0); private set

    @Synchronized fun begin(first: String = "Thinking") {
        items = listOf(Item(first, false)); startedAt = System.currentTimeMillis(); active = true; lastItems = emptyList()
    }

    /** A new thing BRO starts doing; the one before it is marked finished. */
    @Synchronized fun step(label: String) {
        if (!active) return
        val last = items.lastOrNull()
        if (last != null && !last.done && last.label == label) return
        items = (items.map { if (it.done) it else Item(it.label, true) } + Item(label, false)).takeLast(30)
    }

    /** Something that already finished (shown as a ticked line). */
    @Synchronized fun note(label: String) {
        if (!active) return
        items = (items.filter { it.done || true } + Item(label, true)).takeLast(30)
    }

    @Synchronized fun finish() {
        if (!active) return
        lastItems = items.map { Item(it.label, true) }
        lastSeconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        items = emptyList(); active = false
    }

    private val verbs = mapOf(
        "Thinking" to "Thought", "Searching" to "Searched", "Reading" to "Read", "Running" to "Ran", "Writing" to "Wrote",
        "Asking" to "Asked", "Checking" to "Checked", "Planning" to "Planned", "Reviewing" to "Reviewed", "Talking" to "Talked",
        "Pushing" to "Pushed", "Opening" to "Opened", "Understanding" to "Understood", "Looking" to "Looked", "Building" to "Built",
        "Fixing" to "Fixed", "Studying" to "Studied", "Listening" to "Listened", "Creating" to "Created", "Taking" to "Took"
    )

    /** "Searching the web" -> "Searched the web" */
    fun past(label: String): String {
        val first = label.substringBefore(' ')
        return verbs[first]?.let { it + label.removePrefix(first) } ?: label
    }
}
