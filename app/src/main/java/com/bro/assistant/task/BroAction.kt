package com.bro.assistant.task

enum class ActionType(val retryable: Boolean = false) {
    LAUNCH_APP(true),
    GO_HOME,
    GO_BACK,
    SCREENSHOT,
    OPEN_SETTINGS,
    SET_ALARM,
    CALL,
    FLASHLIGHT,
    TOGGLE_SETTING(true),
    YOUTUBE_SEARCH,
    YOUTUBE_PLAY_FIRST(true),
    WHATSAPP_MESSAGE,
    AGENT_TASK
}

enum class ActionStatus { PENDING, RUNNING, SUCCESS, FAILED }

/** One structured step. The AI only ever produces these; it never runs code. */
data class BroAction(
    val type: ActionType,
    val params: Map<String, String> = emptyMap(),
    var status: ActionStatus = ActionStatus.PENDING,
    var error: String? = null,
    var verified: Boolean = false
) {
    fun param(key: String): String = params[key].orEmpty()
}

/** Result of running one action. [caveat] is set when success could not be fully verified. */
data class ActionResult(
    val success: Boolean,
    val message: String,
    val caveat: String? = null,
    val needsPermission: String? = null
) {
    companion object {
        fun ok(message: String, caveat: String? = null) = ActionResult(true, message, caveat)
        fun fail(message: String, needsPermission: String? = null) =
            ActionResult(false, message, null, needsPermission)
    }
}

data class TaskOutcome(val success: Boolean, val summary: String, val needsPermission: String? = null)

/** One line in the on-screen task progress list. */
data class StepUi(val label: String, val status: ActionStatus, val note: String? = null)
