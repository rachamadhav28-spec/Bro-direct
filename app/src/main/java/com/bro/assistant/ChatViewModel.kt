package com.bro.assistant

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.memory.SessionMemory
import com.bro.assistant.task.ActionPlanner
import com.bro.assistant.task.StepUi
import com.bro.assistant.task.TaskManager
import com.bro.assistant.task.TaskNotifier
import com.bro.assistant.voice.SpeechInput
import com.bro.assistant.voice.SpeechOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Connects the screen to the brain: command -> plan -> validate -> run -> verify -> report.
 * Runs inside the app process, so tasks continue when the screen is hidden
 * (BroTaskService keeps the process alive with a notification).
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    val prefs = PreferencesStore(app)
    private val memory = SessionMemory()
    private val planner = ActionPlanner(app, memory, prefs)
    private val taskManager = TaskManager(app, memory, prefs)
    val tts = SpeechOutput(app, prefs)

    private val chatStore = ChatStore(app)
    private val stored = mutableListOf<StoredMessage>()
    val messages = mutableStateListOf<ChatMessage>()

    var state by mutableStateOf(BroState.IDLE)
        private set
    var partialText by mutableStateOf("")
        private set
    var confirmPrompt by mutableStateOf<String?>(null)
        private set
    var permissionNeeded by mutableStateOf<String?>(null)

    var appVisible = true

    val steps: List<StepUi> get() = taskManager.steps

    private var busy = false
    private var nextId = 0L
    private var confirmDeferred: CompletableDeferred<Boolean>? = null

    private val stt = SpeechInput(
        app,
        onPartial = { partialText = it },
        onFinal = { text ->
            partialText = ""
            send(text)
        },
        onProblem = { message ->
            partialText = ""
            state = BroState.IDLE
            addMessage(false, message)
        }
    )

    init {
        // restore the saved chat instead of starting a new one every time
        val saved = chatStore.load()
        if (saved.isEmpty()) {
            addMessage(false, "BRO is ready. Type a command or tap the mic.")
        } else {
            stored.addAll(saved)
            saved.forEach { messages.add(ChatMessage(nextId++, it.role == "user", it.text)) }
            // give the assistant its context back
            saved.takeLast(10).forEach { if (it.role == "user") memory.addUser(it.text) else memory.addBro(it.text) }
        }
    }

    private fun addMessage(fromUser: Boolean, text: String) {
        messages.add(ChatMessage(nextId++, fromUser, text))
        stored.add(StoredMessage(if (fromUser) "user" else "bro", text))
        chatStore.save(stored)
    }

    // ---------- voice ----------

    fun startListening() {
        if (busy) return
        tts.stop()
        partialText = ""
        state = BroState.LISTENING
        stt.start()
    }

    fun stopListening() {
        stt.stop()
        partialText = ""
        if (state == BroState.LISTENING) state = BroState.IDLE
    }

    fun onWake() {
        if (busy) return
        reply("Yes, I'm listening.", BroState.IDLE) { startListening() }
    }

    // ---------- commands ----------

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        if (busy) {
            addMessage(false, "I'm still working on the last task. One moment.")
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                handle(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorHandler.log("ChatViewModel", "command failed", e)
                reply(ErrorHandler.friendly(e), BroState.ERROR)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun handle(text: String) {
        tts.stop()
        addMessage(true, text)
        memory.addUser(text)
        state = BroState.THINKING

        // direct system commands (volume, mute, ultra game mode): no Settings screen, no planner
        val direct = SystemControls.handle(app, text) { later ->
            reply(later, BroState.SUCCESS)
        }
        if (direct != null) {
            reply(direct, BroState.SUCCESS)
            return
        }

        val plan = planner.plan(text)
        if (plan.error != null) {
            reply(plan.error, BroState.ERROR)
            return
        }
        if (plan.actions.isEmpty()) {
            reply(plan.reply ?: "I'm not sure what to do with that.", BroState.IDLE)
            return
        }

        val ack = plan.reply ?: "Working on it."
        addMessage(false, ack)
        memory.addBro(ack)
        if (prefs.speakReplies) tts.speak(ack, flush = false)
        state = BroState.EXECUTING

        BroTaskService.start(app, ack)
        val outcome = try {
            taskManager.run(plan.actions) { question -> askConfirm(question) }
        } finally {
            BroTaskService.stop(app)
        }

        if (outcome.needsPermission != null) permissionNeeded = outcome.needsPermission
        if (!appVisible) TaskNotifier.notifyDone(app, outcome.summary)
        reply(outcome.summary, if (outcome.success) BroState.SUCCESS else BroState.ERROR)
    }

    private suspend fun askConfirm(question: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmDeferred = deferred
        confirmPrompt = question
        if (prefs.speakReplies) tts.speak(question)
        val answer = withTimeoutOrNull(60_000L) { deferred.await() } ?: false
        confirmPrompt = null
        confirmDeferred = null
        return answer
    }

    fun answerConfirm(yes: Boolean) {
        confirmDeferred?.complete(yes)
    }

    // ---------- replies ----------

    private fun reply(text: String, end: BroState, then: (() -> Unit)? = null) {
        addMessage(false, text)
        memory.addBro(text)
        state = end
        if (prefs.speakReplies) {
            tts.speak(
                text,
                flush = true,
                onStart = { state = BroState.SPEAKING },
                onDone = { settle(end, then) }
            )
        } else {
            settle(end, then)
        }
    }

    private fun settle(end: BroState, then: (() -> Unit)?) {
        state = end
        if (end == BroState.IDLE) {
            then?.invoke()
            return
        }
        viewModelScope.launch {
            delay(1500)
            if (state == end) state = BroState.IDLE
            then?.invoke()
        }
    }

    // ---------- misc ----------

    fun newConversation() {
        if (busy) {
            addMessage(false, "Wait for the current task to finish first.")
            return
        }
        tts.stop()
        stt.stop()
        memory.reset()
        messages.clear()
        taskManager.steps.clear()
        chatStore.clear()
        stored.clear()
        partialText = ""
        state = BroState.IDLE
        addMessage(false, "New conversation. I'm ready.")
    }

    fun notifyPermissionDenied() {
        addMessage(false, "Without that permission I can't do this. You can allow it later in Settings.")
    }

    fun onPermissionGranted() {
        addMessage(false, "Permission granted. Please say that command again.")
    }

    fun testVoice() {
        tts.speak("Hello, I am BRO. Is this voice okay?")
    }

    override fun onCleared() {
        stt.destroy()
        tts.shutdown()
        super.onCleared()
    }
}
