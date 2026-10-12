package com.bro.assistant.features

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A to-do list and a notebook, by voice. Kept on this phone only. */
object Notes {
    private val I = RegexOption.IGNORE_CASE
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("bro_notes", Context.MODE_PRIVATE)

    data class Todo(val id: Long, val text: String, val done: Boolean)
    data class Note(val id: Long, val text: String, val time: Long)

    fun todos(c: Context): List<Todo> = try {
        val a = JSONArray(sp(c).getString("todos", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Todo(o.getLong("id"), o.getString("t"), o.optBoolean("d")) }
    } catch (e: Exception) { emptyList() }

    fun notes(c: Context): List<Note> = try {
        val a = JSONArray(sp(c).getString("notes", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Note(o.getLong("id"), o.getString("t"), o.getLong("time")) }
    } catch (e: Exception) { emptyList() }

    private fun saveTodos(c: Context, l: List<Todo>) {
        val a = JSONArray(); l.takeLast(150).forEach { a.put(JSONObject().put("id", it.id).put("t", it.text).put("d", it.done)) }
        sp(c).edit().putString("todos", a.toString()).apply()
    }
    private fun saveNotes(c: Context, l: List<Note>) {
        val a = JSONArray(); l.takeLast(300).forEach { a.put(JSONObject().put("id", it.id).put("t", it.text).put("time", it.time)) }
        sp(c).edit().putString("notes", a.toString()).apply()
    }

    private val addTodo1 = Regex("""^(?:please\s+)?(?:add|put)\s+(.{2,100}?)\s+(?:to|on|in)\s+(?:my\s+)?(?:to-?do\s+|todo\s+|task\s+|shopping\s+|grocery\s+)?(?:list|tasks)[.!\s]*$""", I)
    private val addTodo2 = Regex("""^(?:add\s+(?:a\s+)?task|new\s+task|add\s+to-?do|todo)[:,]?\s+(.{2,100})$""", I)
    private val showTodo = Regex("""^(?:what(?:'s|\s+is)\s+on\s+my\s+(?:to-?do\s+)?list|(?:show|read|list)\s+(?:me\s+)?(?:my\s+)?(?:to-?do(?:s)?|tasks|list|todo\s+list)|my\s+(?:to-?do(?:s)?|tasks|list)|what\s+(?:do\s+i\s+have\s+to\s+do|are\s+my\s+tasks))[?.!\s]*$""", I)
    private val doneRx = Regex("""^(?:mark\s+(.{2,60}?)\s+(?:as\s+)?(?:done|finished|completed)|(?:i\s+)?(?:finished|completed|done\s+with)\s+(.{2,60}))[.!\s]*$""", I)
    private val removeRx = Regex("""^(?:delete|remove)\s+(.{2,60}?)\s+from\s+(?:my\s+)?(?:to-?do\s+)?(?:list|tasks)[.!\s]*$""", I)
    private val clearDone = Regex("""^clear\s+(?:my\s+)?(?:done|completed|finished)\s+(?:tasks|items|list)?[.!\s]*$""", I)
    private val clearList = Regex("""^(?:clear|empty)\s+(?:my\s+)?(?:to-?do\s+)?(?:list|tasks)[.!\s]*$""", I)
    private val addNote = Regex("""^(?:take\s+a\s+note|note\s+down|write\s+down|make\s+a\s+note|new\s+note)(?:\s+that)?[:,]?\s+(.{3,400})$""", I)
    private val readNotes = Regex("""^(?:read|show)\s+(?:me\s+)?(?:my\s+)?notes(?:\s+(?:from\s+)?(today|yesterday))?[?.!\s]*$|^(?:what\s+(?:are\s+)?)?my\s+notes[?.!\s]*$""", I)
    private val clearNotes = Regex("""^(?:delete|clear)\s+(?:all\s+)?(?:my\s+)?notes[.!\s]*$""", I)

    private fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }
    private fun matches(todo: String, query: String): Boolean {
        val w = words(query); return w.isNotEmpty() && w.all { it in todo.lowercase() }
    }
    private fun day(ms: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

    fun handle(c: Context, raw: String): String? {
        val t = raw.trim()
        if (t.length > 420) return null

        (addTodo1.find(t) ?: addTodo2.find(t))?.let { m ->
            val item = m.groupValues[1].trim().trimEnd('.', '!')
            saveTodos(c, todos(c) + Todo(System.currentTimeMillis(), item, false))
            return "Added \"$item\" to your list. You have ${todos(c).count { !it.done }} open item${if (todos(c).count { !it.done } == 1) "" else "s"}."
        }
        if (showTodo.matches(t)) {
            val l = todos(c)
            if (l.isEmpty()) return "Your list is empty. Say \"add milk to my list\"."
            return "Your list:\n" + l.mapIndexed { i, x -> "${i + 1}. ${if (x.done) "[done] " else ""}${x.text}" }.joinToString("\n")
        }
        doneRx.find(t)?.let { m ->
            val q = m.groupValues[1].ifBlank { m.groupValues[2] }
            val hit = todos(c).filter { !it.done && matches(it.text, q) }
            if (hit.isEmpty()) return null            // not about the list: let the normal brain answer
            saveTodos(c, todos(c).map { if (it in hit) it.copy(done = true) else it })
            return "Nice work, boss. Marked \"${hit.first().text}\" as done."
        }
        removeRx.find(t)?.let { m ->
            val hit = todos(c).filter { matches(it.text, m.groupValues[1]) }
            if (hit.isEmpty()) return "I couldn't find that on your list."
            saveTodos(c, todos(c).filter { it !in hit }); return "Removed \"${hit.first().text}\" from your list."
        }
        if (clearDone.matches(t)) { val n = todos(c).count { it.done }; saveTodos(c, todos(c).filter { !it.done }); return "Cleared $n finished item${if (n == 1) "" else "s"}." }
        if (clearList.matches(t)) { saveTodos(c, emptyList()); return "Your list is now empty." }

        addNote.find(t)?.let { m ->
            val n = m.groupValues[1].trim()
            if (Regex("password|passcode|\\bpin\\b|\\botp\\b|cvv|card\\s*number", I).containsMatchIn(n))
                return "I won't write down passwords, PINs, OTPs or card details. Please keep those somewhere safe yourself, boss."
            saveNotes(c, notes(c) + Note(System.currentTimeMillis(), n, System.currentTimeMillis()))
            return "Noted: $n"
        }
        readNotes.find(t)?.let { m ->
            var l = notes(c)
            val which = m.groupValues.getOrNull(1)?.lowercase().orEmpty()
            if (which == "today") l = l.filter { day(it.time) == day(System.currentTimeMillis()) }
            if (which == "yesterday") l = l.filter { day(it.time) == day(System.currentTimeMillis() - 86_400_000L) }
            if (l.isEmpty()) return "I have no notes${if (which.isNotEmpty()) " from $which" else ""}. Say \"take a note ...\"."
            return "Your notes:\n" + l.takeLast(10).joinToString("\n") { "- " + SimpleDateFormat("d MMM h:mm a", Locale.US).format(Date(it.time)) + ": " + it.text }
        }
        if (clearNotes.matches(t)) { saveNotes(c, emptyList()); return "All notes deleted." }
        return null
    }
}
