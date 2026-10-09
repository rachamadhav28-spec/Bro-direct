package com.bro.assistant.task

object ActionValidator {

    private val toggleNames = com.bro.assistant.actions.Tiles.labels.keys

    /** Returns null when the action is valid, otherwise a short reason. */
    fun validate(a: BroAction): String? = when (a.type) {
        ActionType.LAUNCH_APP ->
            if (a.param("app").isBlank() || a.param("app").length > 40) "Which app should I open?" else null
        ActionType.SET_ALARM -> {
            val hour = a.param("hour").toIntOrNull()
            val minute = a.param("minute").toIntOrNull() ?: 0
            if (hour == null || hour !in 0..23 || minute !in 0..59) "That alarm time isn't valid." else null
        }
        ActionType.CALL ->
            if (a.param("contact").isBlank()) "Who should I call?" else null
        ActionType.FLASHLIGHT ->
            if (a.param("state") !in listOf("on", "off")) "Flashlight needs on or off." else null
        ActionType.TOGGLE_SETTING ->
            if (a.param("name") !in toggleNames) "I don't know that setting."
            else if (a.param("state") !in listOf("on", "off")) "That setting needs on or off."
            else null
        ActionType.YOUTUBE_SEARCH ->
            if (a.param("query").isBlank() || a.param("query").length > 100) "What should I search for?" else null
        ActionType.WHATSAPP_MESSAGE ->
            if (a.param("contact").isBlank()) "Who is the message for?"
            else if (a.param("message").isBlank()) "What should the message say?"
            else if (a.param("message").length > 1000) "That message is too long."
            else null
        else -> null
    }
}
