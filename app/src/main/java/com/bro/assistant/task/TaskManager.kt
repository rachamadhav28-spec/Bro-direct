package com.bro.assistant.task

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.bro.assistant.ErrorHandler
import com.bro.assistant.actions.AppLauncher
import com.bro.assistant.actions.DeviceControl
import com.bro.assistant.actions.MessagingActions
import com.bro.assistant.actions.PhoneActions
import com.bro.assistant.actions.YouTubeActions
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.memory.SessionMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Runs a list of validated actions one by one, tracks status, retries safe steps once,
 * and only reports success when every step succeeded.
 */
class TaskManager(
    private val context: Context,
    private val memory: SessionMemory,
    private val prefs: PreferencesStore
) {
    /** Live progress shown on screen. */
    val steps = mutableStateListOf<StepUi>()
    private val hidden = mutableStateListOf<StepUi>()
    private var target = steps

    private val apps = AppLauncher(context)
    private val phone = PhoneActions(context)
    private val device = DeviceControl(context)
    private val messaging = MessagingActions(context, prefs)
    private val youtube = YouTubeActions(context)
    private val agent = com.bro.assistant.actions.AppAgent(context, prefs)

    suspend fun run(actions: List<BroAction>, showSteps: Boolean = true, confirm: suspend (String) -> Boolean): TaskOutcome {
        // inside a multi-step chain the chain owns the on-screen list, so this run uses a hidden one
        target = if (showSteps) steps else hidden
        target.clear()
        actions.forEach { target.add(StepUi(label(it), ActionStatus.PENDING)) }
        val caveats = mutableListOf<String>()

        for ((index, action) in actions.withIndex()) {
            setStep(index, ActionStatus.RUNNING, null)
            action.status = ActionStatus.RUNNING

            var result = execute(action, confirm)
            if (!result.success && action.type.retryable && result.needsPermission == null) {
                delay(800)
                result = execute(action, confirm)
            }

            action.status = if (result.success) ActionStatus.SUCCESS else ActionStatus.FAILED
            action.error = if (result.success) null else result.message
            action.verified = result.success && result.caveat == null
            setStep(index, action.status, result.message)

            if (!result.success) {
                return TaskOutcome(
                    false,
                    "I couldn't complete that task. ${result.message}",
                    result.needsPermission
                )
            }
            result.caveat?.let { caveats.add(it) }
        }

        val summary = if (caveats.isEmpty()) "Task completed."
        else "Task completed, but " + caveats.joinToString(" ")
        return TaskOutcome(true, summary)
    }

    private fun setStep(index: Int, status: ActionStatus, note: String?) {
        if (index in target.indices) target[index] = StepUi(target[index].label, status, note)
    }

    private suspend fun execute(a: BroAction, confirm: suspend (String) -> Boolean): ActionResult {
        return try {
            when (a.type) {
                ActionType.LAUNCH_APP -> {
                    val r = apps.open(a.param("app"))
                    if (r.success) memory.lastApp = a.param("app")
                    r
                }
                ActionType.GO_HOME -> phone.goHome()
                ActionType.GO_BACK -> phone.goBack()
                ActionType.SCREENSHOT -> phone.screenshot()
                ActionType.OPEN_SETTINGS -> phone.openSettings()
                ActionType.SET_ALARM ->
                    phone.setAlarm(a.param("hour").toInt(), a.param("minute").toIntOrNull() ?: 0)
                ActionType.CALL -> phone.call(a.param("contact"))
                ActionType.FLASHLIGHT -> device.flashlight(a.param("state") == "on")
                ActionType.TOGGLE_SETTING -> device.toggleSetting(a.param("name"), a.param("state") == "on")
                ActionType.YOUTUBE_SEARCH -> {
                    val r = youtube.search(a.param("query"))
                    if (r.success) memory.lastApp = "YouTube"
                    r
                }
                ActionType.YOUTUBE_PLAY_FIRST -> youtube.playFirstResult()
                ActionType.AGENT_TASK -> agent.run(a.param("goal"), confirm) { note ->
                    // live progress lines under the plan (keep the list short)
                    if (target.size > 12 && target.size > 1) target.removeAt(1)
                    target.add(StepUi(note, ActionStatus.SUCCESS))
                    com.bro.assistant.BroTaskService.update(context, "BRO: $note")
                }
                ActionType.WHATSAPP_MESSAGE -> {
                    val contact = a.param("contact")
                    val message = a.param("message")
                    val r = messaging.sendWhatsApp(contact, message, confirm)
                    if (r.success) {
                        memory.lastApp = "WhatsApp"
                        memory.lastContact = contact
                        memory.lastMessage = message
                    }
                    r
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ErrorHandler.log("TaskManager", "action ${a.type} failed", e)
            ActionResult.fail(ErrorHandler.friendly(e))
        }
    }

    private fun label(a: BroAction): String = when (a.type) {
        ActionType.LAUNCH_APP -> "Open ${a.param("app")}"
        ActionType.GO_HOME -> "Go to the home screen"
        ActionType.GO_BACK -> "Go back"
        ActionType.SCREENSHOT -> "Take a screenshot"
        ActionType.OPEN_SETTINGS -> "Open Settings"
        ActionType.SET_ALARM -> "Set alarm for ${a.param("hour")}:${a.param("minute").padStart(2, '0')}"
        ActionType.CALL -> "Call ${a.param("contact")}"
        ActionType.FLASHLIGHT -> "Flashlight ${a.param("state")}"
        ActionType.TOGGLE_SETTING -> "Turn ${a.param("state")} ${a.param("name").replace('_', ' ')}"
        ActionType.YOUTUBE_SEARCH -> "Search YouTube for ${a.param("query")}"
        ActionType.YOUTUBE_PLAY_FIRST -> "Play the first result"
        ActionType.WHATSAPP_MESSAGE -> "Send message to ${a.param("contact")}"
        ActionType.AGENT_TASK -> "Working inside apps: ${a.param("goal").take(40)}"
    }
}
