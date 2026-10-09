package com.bro.assistant

/**
 * Turns Telugu script and Tenglish (Telugu written in English letters) into the simple English
 * forms the offline parser already understands. Text without Telugu words passes through
 * unchanged, and anything not recognised still falls through to the AI, which understands
 * Telugu, Tenglish and English natively.
 */
object TeluguNormalizer {

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    /** Longest phrases first so "డార్క్ మోడ్" wins over "మోడ్". */
    private val script = listOf(
        "అల్ట్రా గేమ్ మోడ్" to "ultra game mode",
        "గేమ్ మోడ్" to "game mode",
        "డార్క్ మోడ్" to "dark mode",
        "బ్యాటరీ సేవర్" to "battery saver",
        "ఎయిర్‌ప్లేన్ మోడ్" to "airplane mode",
        "ఫ్లైట్ మోడ్" to "airplane mode",
        "మొబైల్ డేటా" to "mobile data",
        "స్క్రీన్‌షాట్" to "screenshot",
        "స్క్రీన్ షాట్" to "screenshot",
        "ఫ్లాష్‌లైట్" to "flashlight",
        "హాట్‌స్పాట్" to "hotspot",
        "అన్‌మ్యూట్" to "unmute",
        "బ్లూటూత్" to "bluetooth",
        "సెట్టింగ్స్" to "settings",
        "వాట్సాప్" to "whatsapp",
        "యూట్యూబ్" to "youtube",
        "లొకేషన్" to "location",
        "వాల్యూమ్" to "volume",
        "మెసేజ్" to "message",
        "అలారం" to "alarm",
        "చెయ్యండి" to "chey",
        "చేయండి" to "chey",
        "చెయ్యి" to "chey",
        "చేయి" to "chey",
        "చెప్పండి" to "cheppu",
        "చెప్పు" to "cheppu",
        "పంపండి" to "pampu",
        "పంపు" to "pampu",
        "తెరవండి" to "open",
        "తెరువు" to "open",
        "తర్వాత" to " then ",
        "మరియు" to " and ",
        "వైఫై" to "wifi",
        "టార్చ్" to "torch",
        "మ్యూట్" to "mute",
        "మ్యాక్స్" to "max",
        "ఫుల్" to "max",
        "ఓపెన్" to "open",
        "ఫోన్" to "phone",
        "కాల్" to "call",
        "ఆన్" to "on",
        "ఆఫ్" to "off",
        "హోమ్" to "home",
        "బ్యాక్" to "back",
        "అని" to "ani",
        " కి " to " ki "
    )

    private val words = listOf(
        rx("\\b(?:tarvata|taruvata|taruvatha|tarvatha|aa tarvata)\\b") to " then ",
        rx("\\b(?:mariyu|inka)\\b") to " and "
    )

    private val tail = rx("\\s+(?:ra|raa|bro|plz|pls|please|andi|ayya)$")
    private val head = rx("^(?:please|plz|dayachesi|dayachesi)\\s+")

    /** Whole-text pass: Telugu script words and joining words ("mariyu", "tarvata"). */
    fun preprocess(text: String): String {
        var s = text
        for ((from, to) in script) if (s.contains(from)) s = s.replace(from, to)
        for ((r, to) in words) s = s.replace(r, to)
        return s.replace(Regex("\\s{2,}"), " ").trim()
    }

    private val msg = rx(
        "^(.+?)\\s+ki\\s+(.+?)\\s+(?:ani\\s+)?(?:message\\s+|msg\\s+|whatsapp\\s+)?" +
            "(?:cheppu|cheppandi|cheppara|cheppura|pampu|pampandi|pampara|send\\s+chey\\w*)$"
    )
    private val call = rx("^(.+?)\\s+(?:ki\\s+)?(?:call|phone|fone|ring)\\s+chey\\w*$")
    private val openTe = rx("^(.+?)(?:\\s+ni)?\\s+open\\s+chey\\w*(.*)$")
    private val openTe2 = rx("^(.+?)(?:\\s+ni)?\\s+(?:teruvu|teruvandi|teravandi|tera)\\b(.*)$")
    private val onOff = rx("^(.+?)(?:\\s+ni)?\\s+(on|off)\\s+chey\\w*(.*)$")
    private val volUp = rx("^(?:volume|sound)\\s+(?:penchu|pencu|penchandi|perugu)\\w*$")
    private val volDown = rx("^(?:volume|sound)\\s+(?:taggu|taggincu|tagginchu|taggandi)\\w*$")
    private val trailingChey = rx("^(.+?)\\s+chey(?:yi|yu|i|andi)?$")

    /** Single-clause pass: Telugu word order (object first, verb last) to English order. */
    private val marker = rx(
        "\\b(?:chey\\w*|cheppu|cheppandi|cheppara|cheppura|pampu|pampandi|pampara|teruvu|teruvandi|" +
            "teravandi|tera|penchu|pencu|penchandi|perugu\\w*|taggu\\w*|taggincu|tagginchu|taggandi|ki)\\b"
    )

    private val joiner = rx("\\s+and\\s+")

    fun clause(input: String): String {
        if (!marker.containsMatchIn(input)) return input
        // "whatsapp open chey and amma ki hi cheppu": convert each part on its own
        val parts = input.split(joiner)
        if (parts.size > 1) return parts.joinToString(" and ") { one(it) }
        return one(input)
    }

    private fun one(input: String): String {
        var s = input.trim().replace(head, "").replace(tail, "").trim()

        msg.find(s)?.let { return "send ${it.groupValues[1].trim()} saying ${it.groupValues[2].trim()}" }
        call.find(s)?.let { return "call ${it.groupValues[1].trim()}" }
        openTe.find(s)?.let { return "open ${it.groupValues[1].trim()}${it.groupValues[2]}".trim() }
        openTe2.find(s)?.let { return "open ${it.groupValues[1].trim()}${it.groupValues[2]}".trim() }
        onOff.find(s)?.let {
            return "turn ${it.groupValues[2].lowercase()} ${it.groupValues[1].trim()}${it.groupValues[3]}".trim()
        }
        if (volUp.matches(s)) return "volume up"
        if (volDown.matches(s)) return "volume down"
        trailingChey.find(s)?.let { s = it.groupValues[1].trim() }
        return s
    }
}
