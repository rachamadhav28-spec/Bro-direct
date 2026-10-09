package com.bro.assistant

import com.bro.assistant.memory.SessionMemory
import com.bro.assistant.task.ActionType
import com.bro.assistant.task.BroAction

/**
 * Understands simple commands without the AI. Returns null when a command is too
 * complex, so the AI can handle it. Uses session memory to resolve him / her / that app.
 */
class LocalCommandParser(private val memory: SessionMemory) {

    data class Result(val reply: String?, val actions: List<BroAction>)

    private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private val tileAliases = listOf(
        "super battery saver" to "super_battery_saver",
        "battery saver|power saving mode" to "battery_saver",
        "ultra game mode|game mode" to "ultra_game_mode",
        "dark mode|dark theme" to "dark_mode",
        "auto-?\\s?rotate|screen rotation|rotation" to "auto_rotate",
        "do not disturb|dnd" to "dnd",
        "eye protection|eye comfort" to "eye_protection",
        "focus mode" to "focus_mode",
        "bedtime mode" to "bedtime_mode",
        "mic access|microphone access" to "mic_access",
        "camera access" to "camera_access",
        "data saver|data saving" to "data_saver",
        "extra dim" to "extra_dim",
        "color inversion|invert colou?rs" to "color_inversion",
        "wi-?fi" to "wifi",
        "bluetooth" to "bluetooth",
        "personal hotspot|hotspot" to "hotspot",
        "location|gps" to "location",
        "airplane mode|flight mode|aeroplane mode" to "airplane",
        "mobile data|cellular data|data" to "mobile_data"
    )
    private val toggleNames = tileAliases.joinToString("|") { it.first }

    private val wakePrefix = rx("^(?:hey|ok|okay)?\\s*bro\\b[,.!:]?\\s*")
    private val splitter = rx(
        "\\s*,?\\s*(?:and\\s+)?then\\s+(?=(?:open|launch|go|search|play|call|set|turn|take|send|message|text|switch|start|tell)\\b)"
    )
    private val openAnd = rx("^(?:open|launch|start)\\s+(.+?)\\s*(?:,\\s*(?:and\\s+)?|\\s+and\\s+)(.+)$")

    private val sameMessage = rx("^send\\s+(?:the\\s+)?same\\s+message(?:\\s+again)?(?:\\s+to\\s+(.+))?$")
    private val messagePatterns = listOf(
        rx("^send\\s+(?:a\\s+)?(?:whatsapp\\s+)?message\\s+to\\s+(.+?)\\s*(?::|\\bsaying\\b|\\bthat says\\b)\\s*(.+)$"),
        rx("^(?:send|message|text)\\s+(.+?)\\s*(?::|\\bsaying\\b|\\bthat\\b)\\s*(.+)$"),
        rx("^(?:send|message|text)\\s+(\\S+)\\s+[\"'\u201C\u2018](.+)[\"'\u201D\u2019]$")
    )
    private val sayPattern = rx("^(?:say|send|tell)\\s+(.+?)\\s+to\\s+(.+)$")
    private val tellPattern = rx("^(?:tell|reply\\s+to)\\s+(him|her|them)\\s+(?:that\\s+)?(.+)$")
    private val replyPattern = rx("^reply\\s*[:,]?\\s*(.+)$")
    private val contactOnly = rx("^(?:message|text|chat\\s+with|open\\s+(?:the\\s+)?chat\\s+(?:of|with))\\s+(.+)$")

    private val openSettings = rx("^open\\s+(?:the\\s+)?settings$")
    private val home = rx("^(?:go\\s+(?:to\\s+)?(?:the\\s+)?)?home(?:\\s+screen)?$")
    private val back = rx("^(?:go\\s+)?back$")
    private val screenshot = rx("^(?:take\\s+(?:a\\s+)?)?screenshot$")
    private val alarm = rx(
        "^(?:set|create)\\s+(?:an?\\s+)?alarm\\s+(?:for|at)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?$"
    )
    private val torch = rx("^(?:turn\\s+(on|off)\\s+(?:the\\s+)?(?:flashlight|torch))$|^(?:flashlight|torch)\\s+(on|off)$")
    private val toggle1 = rx("^(?:turn|switch)\\s+(on|off)\\s+(?:the\\s+)?($toggleNames)$")
    private val toggle2 = rx("^(?:turn|switch)\\s+(?:the\\s+)?($toggleNames)\\s+(on|off)$")
    private val toggle3 = rx("^($toggleNames)\\s+(on|off)$")
    private val enableTile = rx("^(?:enable|activate|start)\\s+(?:the\\s+)?($toggleNames)$")
    private val disableTile = rx("^(?:disable|deactivate|stop)\\s+(?:the\\s+)?($toggleNames)$")
    private val bareTile = rx("^(?:the\\s+)?($toggleNames)$")
    private val call = rx("^call\\s+(.+)$")

    private val playFirstOnly = rx("^(?:now\\s+)?play\\s+(?:the\\s+)?first\\s+(?:result|video|one)$")
    private val playSuffix = rx("\\s*(?:,\\s*)?(?:and\\s+)?play\\s+(?:the\\s+)?first\\s+(?:result|video|one)$")
    private val ytSearch1 = rx("^(?:now\\s+)?search\\s+(?:on\\s+)?youtube\\s+for\\s+(.+)$")
    private val ytSearch2 = rx("^(?:now\\s+)?search\\s+(?:for\\s+)?(.+?)\\s+on\\s+youtube$")
    private val ytSearch3 = rx("^(?:now\\s+)?search\\s+(?:for\\s+)?(.+)$")
    private val openApp = rx("^(?:open|launch|start)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$")

    private val pronouns = setOf("him", "her", "them", "he", "she")

    fun parse(raw: String): Result? {
        val text = TeluguNormalizer.preprocess(raw.trim().replace(wakePrefix, "").trim().trimEnd('.', '!', '?'))
        if (text.isEmpty()) return null

        val clauses = text.split(splitter).filter { it.isNotBlank() }
        var reply: String? = null
        val actions = mutableListOf<BroAction>()
        for (clause in clauses) {
            val result = parseClause(clause) ?: return null
            actions.addAll(result.actions)
            if (result.reply != null) reply = result.reply
        }
        if (actions.isNotEmpty()) reply = null
        if (actions.isEmpty() && reply == null) return null
        return Result(reply, actions)
    }

    private fun parseClause(clause: String): Result? {
        val c = TeluguNormalizer.clause(clause.trim().trimEnd('.', '!', '?', ',', ' '))
        if (c.isEmpty()) return null

        openAnd.find(c)?.let { m ->
            val first = parseClause("open " + m.groupValues[1]) ?: return null
            val rest = parseClause(m.groupValues[2]) ?: return null
            return Result(rest.reply, first.actions + rest.actions)
        }

        sameMessage.find(c)?.let { m ->
            val who = m.groupValues[1].ifBlank { "him" }
            val contact = resolveContact(who) ?: return ask("Who should I send it to?")
            val message = memory.lastMessage ?: return ask("I don't have an earlier message to repeat.")
            return Result(null, listOf(sendAction(contact, message)))
        }

        for (pattern in messagePatterns) {
            pattern.find(c)?.let { m ->
                val who = m.groupValues[1].replace(rx("^to\\s+"), "").trim()
                val contact = resolveContact(who) ?: return ask("Who do you mean?")
                return Result(null, listOf(sendAction(contact, m.groupValues[2].trim())))
            }
        }

        sayPattern.find(c)?.let { m ->
            val message = m.groupValues[1].trim().trim('"', '\'')
            val contact = resolveContact(m.groupValues[2].trim()) ?: return ask("Who do you mean?")
            return Result(null, listOf(sendAction(contact, message)))
        }

        tellPattern.find(c)?.let { m ->
            val contact = memory.lastContact ?: return ask("Who should I tell?")
            return Result(null, listOf(sendAction(contact, m.groupValues[2].trim())))
        }
        replyPattern.find(c)?.let { m ->
            val contact = memory.lastContact ?: return ask("Who should I reply to?")
            return Result(null, listOf(sendAction(contact, m.groupValues[1].trim())))
        }
        contactOnly.find(c)?.let { m ->
            val name = m.groupValues[1].trim()
            memory.lastContact = name
            return ask("Okay. What should I tell $name?")
        }

        if (openSettings.matches(c)) return act(ActionType.OPEN_SETTINGS)
        if (home.matches(c)) return act(ActionType.GO_HOME)
        if (back.matches(c)) return act(ActionType.GO_BACK)
        if (screenshot.matches(c)) return act(ActionType.SCREENSHOT)

        alarm.find(c)?.let { m ->
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifBlank { "0" }.toInt()
            val ampm = m.groupValues[3].lowercase()
            if (ampm.startsWith("p") && hour < 12) hour += 12
            if (ampm.startsWith("a") && hour == 12) hour = 0
            return act(ActionType.SET_ALARM, "hour" to hour.toString(), "minute" to minute.toString())
        }

        torch.find(c)?.let { m ->
            val state = m.groupValues[1].ifBlank { m.groupValues[2] }.lowercase()
            return act(ActionType.FLASHLIGHT, "state" to state)
        }

        toggle1.find(c)?.let { m -> return toggle(m.groupValues[2], m.groupValues[1]) }
        toggle2.find(c)?.let { m -> return toggle(m.groupValues[1], m.groupValues[2]) }
        toggle3.find(c)?.let { m -> return toggle(m.groupValues[1], m.groupValues[2]) }

        enableTile.find(c)?.let { m -> return toggle(m.groupValues[1], "on") }
        disableTile.find(c)?.let { m -> return toggle(m.groupValues[1], "off") }
        bareTile.find(c)?.let { return ask("Do you want ${it.groupValues[1]} on or off?") }

        call.find(c)?.let { m ->
            val who = m.groupValues[1].trim()
            val contact = resolveContact(who) ?: return ask("Who do you mean?")
            return act(ActionType.CALL, "contact" to contact)
        }

        if (playFirstOnly.matches(c)) return act(ActionType.YOUTUBE_PLAY_FIRST)

        val playFirst = playSuffix.containsMatchIn(c)
        val base = if (playFirst) c.replace(playSuffix, "").trim() else c
        val query = ytSearch1.find(base)?.groupValues?.get(1)
            ?: ytSearch2.find(base)?.groupValues?.get(1)
            ?: if (memory.lastApp.equals("youtube", ignoreCase = true)) ytSearch3.find(base)?.groupValues?.get(1) else null
        if (query != null) {
            val list = mutableListOf(BroAction(ActionType.YOUTUBE_SEARCH, mapOf("query" to query.trim())))
            if (playFirst) list.add(BroAction(ActionType.YOUTUBE_PLAY_FIRST))
            return Result(null, list)
        }

        openApp.find(c)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.split(" ").size > 4) return null
            return act(ActionType.LAUNCH_APP, "app" to name)
        }

        return null
    }

    private fun resolveContact(name: String): String? =
        if (name.lowercase() in pronouns) memory.lastContact else name

    private fun sendAction(contact: String, message: String) =
        BroAction(ActionType.WHATSAPP_MESSAGE, mapOf("contact" to contact, "message" to message))

    private fun toggle(rawName: String, state: String): Result {
        val raw = rawName.trim()
        val name = tileAliases.firstOrNull { rx("^(?:${it.first})$").matches(raw) }?.second ?: "mobile_data"
        return act(ActionType.TOGGLE_SETTING, "name" to name, "state" to state.lowercase())
    }

    private fun ask(text: String) = Result(text, emptyList())

    private fun act(type: ActionType, vararg params: Pair<String, String>) =
        Result(null, listOf(BroAction(type, mapOf(*params))))
}
