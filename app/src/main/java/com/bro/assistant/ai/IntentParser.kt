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

            Rules:
            - If the user is only chatting or asking a question, use an empty actions list and answer in reply (max 2 short sentences).
            - If the user wants actions, reply is a short confirmation such as "Working on it."
            - Resolve words like him, her, that app, the same message using the context.
            - If a needed detail is missing, ask for it in reply and use an empty actions list.
            - Never invent action types. Never include any text outside the JSON.
        """.trimIndent()
    }
}
