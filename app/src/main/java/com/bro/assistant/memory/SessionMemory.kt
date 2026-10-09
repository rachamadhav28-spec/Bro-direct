package com.bro.assistant.memory

/** Conversation memory for the current session only. Cleared by "New conversation". */
class SessionMemory {
    var lastApp: String? = null
    var lastContact: String? = null
    var lastMessage: String? = null
    var lastCode: String? = null

    private val history = ArrayDeque<String>()

    fun addUser(text: String) = add("User: $text")

    fun addBro(text: String) = add("BRO: $text")

    private fun add(line: String) {
        history.addLast(line)
        while (history.size > 12) history.removeFirst()
    }

    fun historyText(): String = history.joinToString("\n")

    fun reset() {
        lastApp = null
        lastContact = null
        lastMessage = null
        lastCode = null
        history.clear()
    }
}
