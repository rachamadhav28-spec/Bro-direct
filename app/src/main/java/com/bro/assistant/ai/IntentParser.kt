package com.bro.assistant.ai

import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.memory.SessionMemory
import com.bro.assistant.task.ActionType
import com.bro.assistant.task.BroAction
import org.json.JSONArray
import org.json.JSONObject

/** Turns a natural-language request into a reply plus structured actions using the AI. */
class IntentParser(private val memory: SessionMemory, prefs: PreferencesStore) {

    data class AiPlan(val reply: String, val actions: List<BroAction>)

    private val client = GeminiClient(prefs)

    suspend fun understand(userText: String): AiPlan {
        val context = buildString {
            append("Context:\n")
            append("last_app=").append(memory.lastApp ?: "none").append('\n')
            append("last_contact=").append(memory.lastContact ?: "none").append('\n')
            append("last_message=").append(memory.lastMessage ?: "none").append('\n')
            append("Recent conversation:\n").append(memory.historyText()).append("\n\n")
            append("User says: ").append(userText)
        }
        return parse(client.generate(SYSTEM_PROMPT, context))
    }

    /** Free-form answer (code, explanations). Not JSON. */
    suspend fun answerFreely(userText: String): String {
        val context = buildString {
            memory.lastCode?.let { append("Code you wrote earlier (edit this when the user asks for changes or fixes):\n```\n").append(it).append("\n```\n\n") }
            append("Recent conversation:\n").append(memory.historyText()).append("\n\nUser says: ").append(userText)
        }
        return client.generate(CODE_PROMPT, context, json = false).trim()
    }

    private fun parse(raw: String): AiPlan {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = JSONObject(cleaned)
        val reply = obj.optString("reply", "")
        val arr = obj.optJSONArray("actions") ?: JSONArray()
        val actions = mutableListOf<BroAction>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val type = try {
                ActionType.valueOf(item.getString("type").trim().uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("The AI suggested an action I don't support.")
            }
            val params = mutableMapOf<String, String>()
            val p = item.optJSONObject("params")
            if (p != null) {
                val keys = p.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    params[key] = p.optString(key)
                }
            }
            actions.add(BroAction(type, params))
        }
        return AiPlan(reply, actions)
    }

    companion object {
        private val CODE_PROMPT = """
            You are BRO, an expert programmer who can write code in any language (Java, Kotlin, Python,
            JavaScript, TypeScript, C, C++, C#, Go, Rust, Swift, PHP, SQL, HTML/CSS, Bash and more).
            Rules:
            - Put every piece of code in a fenced block with the language name, e.g. ```python.
            - Give complete, runnable code with all imports. Check it carefully for syntax, logic and
              edge-case errors before answering, and never leave placeholders or "..." in code.
            - After the code, add at most 3 short lines: how to run it, and any assumption you made.
            - If the request is unclear, ask one short question instead of guessing.
            - The user may write English, Telugu, or Tenglish (Telugu in English letters). Answer in the
              same language style, but keep code, identifiers and comments in English.
        """.trimIndent()

        private val SYSTEM_PROMPT = """
            You are the planner for BRO, an Android voice assistant. Reply ONLY with JSON in this shape:
            {"reply": "short text", "actions": [{"type": "TYPE", "params": {"key": "value"}}]}

            Allowed action types and their params:
            LAUNCH_APP {app}
            GO_HOME
            GO_BACK
            SCREENSHOT
            OPEN_SETTINGS
            SET_ALARM {hour (0-23), minute (0-59)}
            CALL {contact}
            FLASHLIGHT {state: on|off}
            TOGGLE_SETTING {name: wifi|bluetooth|hotspot|location|airplane|mobile_data|ultra_game_mode|dark_mode|battery_saver|super_battery_saver|auto_rotate|dnd|eye_protection|focus_mode|bedtime_mode|mic_access|camera_access|data_saver|extra_dim|color_inversion, state: on|off}
            YOUTUBE_SEARCH {query}
            YOUTUBE_PLAY_FIRST
            WHATSAPP_MESSAGE {contact, message}
            AGENT_TASK {goal}  = do real work INSIDE any app by looking at its screen and tapping/typing/scrolling.
              Use it for anything the actions above cannot do, e.g. "in Instagram open my latest message",
              "order a coffee in Swiggy", "in Settings change the wallpaper", "check my last SMS", "in Chrome search X
              and read the first result", "in Gmail open the newest email". goal = one clear English sentence with every
              detail the user gave (names, texts, items). Prefer the specific actions above when they fit.

            Language: the user may speak or type English, Telugu (తెలుగు script), Tenglish (Telugu written in
            English letters, e.g. "whatsapp open chey", "ammulu akka ki hi cheppu", "wifi off cheyyi"), or a mix.
            Understand all of them. Telugu word order puts the verb last: "<thing> open chey" = open <thing>,
            "<name> ki <text> cheppu/pampu" = send <text> to <name>, "<name> ki call chey" = call <name>,
            "<setting> on/off chey" = turn the setting on/off, "penchu" = increase, "taggu" = decrease,
            "tarvata" = then, "mariyu" = and, "ela unnav/em chestunnav" = casual chat.
            Always keep action params in plain English/Latin form (app names, contact names as spoken, and the
            message text exactly as the user meant it, in the language they wanted to send it).
            Write "reply" in the same language style the user used: Telugu script -> Telugu, Tenglish -> Tenglish,
            English -> English. Keep replies short.

            Rules:
            - If the user is only chatting or asking a question, use an empty actions list and answer in reply (max 2 short sentences).
            - If the user wants actions, reply is a short confirmation such as "Working on it."
            - Resolve words like him, her, that app, the same message using the context.
            - If a needed detail is missing, ask for it in reply and use an empty actions list.
            - Never invent action types. Never include any text outside the JSON.
        """.trimIndent()
    }
}
