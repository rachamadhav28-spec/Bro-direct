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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Connects the screen to the brain: command -> plan -> validate -> run -> verify -> report.
 * Runs inside the app process, so tasks continue when the screen is hidden
 * (BroTaskService keeps the process alive with a notification).
 */
/** Lives as long as the app process, so a running task is not cancelled when the screen closes. */
private val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    val prefs = PreferencesStore(app)
    private val memory = SessionMemory()
    private val planner = ActionPlanner(app, memory, prefs)
    private val github = GitHubCommands(app, prefs)
    private val taskManager = TaskManager(app, memory, prefs)
    val tts = SpeechOutput(app, prefs)

    private val chatStore = ChatStore(app)
    private val stored = mutableListOf<StoredMessage>()
    private var currentId = 0L
    val history = mutableStateListOf<Conversation>()
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
        // reopen the last conversation instead of starting a new one every time
        val last = chatStore.get(chatStore.currentId)
        if (last != null && last.messages.isNotEmpty()) {
            currentId = last.id
            loadConversation(last)
        } else {
            currentId = System.currentTimeMillis()
            chatStore.currentId = currentId
            addMessage(false, "BRO is ready. Type a command or tap the mic.")
        }
        refreshHistory()
        if (com.bro.assistant.ai.LocalLlm.isInstalled(app)) {
            viewModelScope.launch { com.bro.assistant.ai.LocalLlm.warmUp(prefs) }
        }
    }

    private fun loadConversation(c: Conversation) {
        messages.clear()
        stored.clear()
        stored.addAll(c.messages)
        c.messages.forEach { messages.add(ChatMessage(nextId++, it.role == "user", it.text)) }
        memory.reset()
        memory.lastCode = c.messages.lastOrNull { it.role != "user" && "```" in it.text }
            ?.let { SessionMemory.extractLastCode(it.text) }
        c.messages.takeLast(10).forEach { if (it.role == "user") memory.addUser(it.text) else memory.addBro(it.text) }
    }

    fun refreshHistory() {
        history.clear()
        history.addAll(chatStore.all().sortedByDescending { it.updated })
    }

    fun openConversation(id: Long) {
        if (busy) { addMessage(false, "Wait for the current task to finish first."); return }
        val c = chatStore.get(id) ?: return
        tts.stop(); stt.stop()
        currentId = c.id
        chatStore.currentId = c.id
        taskManager.steps.clear()
        partialText = ""
        state = BroState.IDLE
        loadConversation(c)
    }

    fun deleteConversation(id: Long) {
        chatStore.delete(id)
        if (id == currentId) newConversation(force = true)
        refreshHistory()
    }

    private fun saveCurrent() {
        // only keep chats where the user actually said something
        val firstUser = stored.firstOrNull { it.role == "user" } ?: return
        val title = firstUser.text.trim().replace("\n", " ").take(40)
        chatStore.put(Conversation(currentId, title, System.currentTimeMillis(), stored.toList()))
        refreshHistory()
    }

    private fun addMessage(fromUser: Boolean, text: String) {
        messages.add(ChatMessage(nextId++, fromUser, text))
        stored.add(StoredMessage(if (fromUser) "user" else "bro", text))
        saveCurrent()
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

    private val queue = ArrayDeque<String>()
    private var currentJob: kotlinx.coroutines.Job? = null
    private val stopRx = Regex("^(?:stop|cancel|abort|stop (?:the )?task|cancel (?:the )?task|stop everything|nilupu|aapu)[.!]*$", RegexOption.IGNORE_CASE)

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        if (busy) {
            if (stopRx.matches(text)) { cancelAll(); return }
            // keep working: line the new command up behind the current one
            queue.addLast(text)
            addMessage(true, text)
            addMessage(false, "Queued (${queue.size} waiting). I'll do it right after the current task. Say \"stop\" to cancel everything.")
            return
        }
        launchTask(text, echo = true)
    }

    private fun cancelAll() {
        queue.clear()
        currentJob?.cancel()
        taskManager.steps.clear()
        confirmDeferred?.complete(false)
        addMessage(false, "Stopped.")
        state = BroState.IDLE
    }

    private fun launchTask(text: String, echo: Boolean) {
        busy = true
        lastReply = null
        // Runs in a process-wide scope with a foreground service, so the task keeps going when you
        // leave the app, and you get a notification when it is done.
        BroTaskService.start(app, "BRO is working on: " + text.take(60))
        currentJob = taskScope.launch {
            try {
                handle(text, echo)
                if (!appVisible && queue.isEmpty()) lastReply?.let { TaskNotifier.notifyDone(app, it.take(150)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorHandler.log("ChatViewModel", "command failed", e)
                reply(ErrorHandler.friendly(e), BroState.ERROR)
                if (!appVisible) TaskNotifier.notifyDone(app, ErrorHandler.friendly(e).take(150))
            } finally {
                busy = false
                val next = queue.removeFirstOrNull()
                if (next != null) launchTask(next, echo = false) else BroTaskService.stop(app)
            }
        }
    }

    private var lastReply: String? = null
    private var lastCommand: String? = null
    private val retryRx = Regex("^(?:please\\s+)?(?:try\\s+again|retry|again|do\\s+it\\s+again|once\\s+more)[.!]*$", RegexOption.IGNORE_CASE)

    private class StepOutcome(val success: Boolean, val message: String, val end: BroState)

    private suspend fun handle(shown: String, echo: Boolean = true) {
        // "try again" repeats the last real command
        val text = if (retryRx.matches(shown.trim())) (lastCommand ?: shown) else shown.also { lastCommand = it }
        tts.stop()
        if (echo) addMessage(true, shown)
        state = BroState.THINKING

        val parts = com.bro.assistant.task.TaskChain.split(text)
        if (parts.size > 1) {
            runChain(parts)
            return
        }
        memory.addUser(text)
        val o = runCommand(text, quiet = false)
        reply(o.message, o.end)
    }

    /** Several steps in order. Each step uses the same brain as a single command; stops at the first failure. */
    private suspend fun runChain(parts: List<String>) {
        taskManager.steps.clear()
        parts.forEach { taskManager.steps.add(StepUi(it.take(48), ActionStatus.PENDING)) }
        fun mark(i: Int, st: ActionStatus, note: String?) {
            if (i in taskManager.steps.indices) taskManager.steps[i] = StepUi(taskManager.steps[i].label, st, note)
        }
        memory.addUser(parts.joinToString(", then "))
        addMessage(false, "I'll do this in ${parts.size} steps.")
        state = BroState.EXECUTING
        val lines = ArrayList<String>()
        var failedAt = -1
        for ((i, part) in parts.withIndex()) {
            mark(i, ActionStatus.RUNNING, null)
            BroTaskService.update(app, "Step ${i + 1} of ${parts.size}: ${part.take(40)}")
            state = BroState.EXECUTING
            val o = runCommand(part, quiet = true)
            mark(i, if (o.success) ActionStatus.SUCCESS else ActionStatus.FAILED, o.message.take(120))
            lines.add("${i + 1}) ${part.take(40)}: ${o.message.take(110)}")
            if (!o.success) { failedAt = i; break }
            delay(900) // let the screen settle before the next step
        }
        val text = if (failedAt < 0) "All ${parts.size} steps done.\n" + lines.joinToString("\n")
        else "I stopped at step ${failedAt + 1} of ${parts.size}.\n" + lines.joinToString("\n")
        reply(text, if (failedAt < 0) BroState.SUCCESS else BroState.ERROR)
    }

    /** One command through the whole pipeline: direct controls, GitHub, then the planner (and agent). */
    private suspend fun runCommand(text: String, quiet: Boolean): StepOutcome {
        // direct phone functions (volume, brightness, lock, tiles...): no planner, no AI
        val later = CompletableDeferred<String>()
        val direct = com.bro.assistant.actions.PhoneFunctions.handle(app, text) { later.complete(it) }
            ?: SystemControls.handle(app, text) { later.complete(it) }
        if (direct != null) {
            // tile toggles answer "Okay" first and finish a moment later: wait for the real result
            val msg = if (direct.trim('.').equals("Okay", true)) withTimeoutOrNull(9000L) { later.await() } ?: direct else direct
            return StepOutcome(true, msg, BroState.SUCCESS)
        }

        github.tryHandle(text, memory.lastCode) { q -> askConfirm(q) }?.let {
            return StepOutcome(true, it, BroState.SUCCESS)
        }

        val plan = planner.plan(text)
        if (plan.error != null) return StepOutcome(false, plan.error, BroState.ERROR)
        if (plan.actions.isEmpty()) return StepOutcome(true, plan.reply ?: "I'm not sure what to do with that.", BroState.IDLE)

        if (!quiet) {
            val ack = plan.reply ?: "Working on it."
            addMessage(false, ack)
            memory.addBro(ack)
            if (prefs.speakReplies) tts.speak(ack, flush = false)
        }
        state = BroState.EXECUTING

        val outcome = taskManager.run(plan.actions, showSteps = !quiet) { question -> askConfirm(question) }
        if (outcome.needsPermission != null) permissionNeeded = outcome.needsPermission
        return StepOutcome(outcome.success, outcome.summary, if (outcome.success) BroState.SUCCESS else BroState.ERROR)
    }

    private suspend fun askConfirm(question: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmDeferred = deferred
        confirmPrompt = question
        if (prefs.speakReplies) tts.speak(question)
        if (!appVisible) TaskNotifier.notifyDone(app, "BRO needs your OK: $question")
        val answer = withTimeoutOrNull(600_000L) { deferred.await() } ?: false
        confirmPrompt = null
        confirmDeferred = null
        return answer
    }

    fun answerConfirm(yes: Boolean) {
        confirmDeferred?.complete(yes)
    }

    // ---------- replies ----------

    private fun reply(text: String, end: BroState, then: (() -> Unit)? = null) {
        lastReply = text
        addMessage(false, text)
        memory.addBro(text)
        state = end
        if (prefs.speakReplies) {
            tts.speak(
                if ("```" in text) "I've written the code. Please check the chat." else text,
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

    fun newConversation(force: Boolean = false) {
        if (busy && !force) {
            addMessage(false, "Wait for the current task to finish first.")
            return
        }
        tts.stop()
        stt.stop()
        memory.reset()
        messages.clear()
        taskManager.steps.clear()
        stored.clear()
        currentId = System.currentTimeMillis()
        chatStore.currentId = currentId
        partialText = ""
        state = BroState.IDLE
        addMessage(false, "New conversation. I'm ready.")
    }

    fun reportCrash(info: String) {
        addMessage(false, "BRO closed unexpectedly last time. Please send this to the developer:\n$info")
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
        if (!busy) tts.shutdown() // a task may still be finishing in the background
        super.onCleared()
    }
}
