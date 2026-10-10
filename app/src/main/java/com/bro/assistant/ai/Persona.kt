package com.bro.assistant.ai

/**
 * Instant answers about BRO and its boss. They are built in, so they work with no internet,
 * no API key and no offline model, and they never wait for an AI.
 */
object Persona {

    private const val BOSS = "R. Madhav"

    private val bossQ = Regex(
        "(who\\s*(?:is|are|'s|s)?\\s*(?:your|ur|the)\\s*(?:boss|owner|creator|developer|maker|master|father|king|lord|founder)|" +
            "who\\s+(?:made|created|built|developed|programmed|owns|designed|invented)\\s+(?:you|u|bro)|" +
            "(?:your|ur|nee|ni|mee|neeku)\\s*(?:boss|owner|creator|developer|maker|master|founder)(?:\\s*(?:name|evaru|yevaru|who))?|" +
            "(?:boss|owner|creator|developer|maker|master)\\s*(?:evaru|yevaru|ev[a-z]*)|" +
            "ninnu\\s*(?:evaru|yevaru)|ninnu\\s*(?:tayaru|create|chesindi|chesaru|cheshadu|chesadu)|" +
            "నీ\\s*బాస్|మీ\\s*బాస్|నిన్ను\\s*ఎవరు|బాస్\\s*ఎవరు)", RegexOption.IGNORE_CASE
    )

    private val aboutBoss = Regex(
        "(madhav|మాధవ్|my\\s*boss|your\\s*boss|about\\s*me\\b|about\\s*him\\b|who\\s*am\\s*i\\b|nenu\\s*evaru|nannu\\s*gurinchi|naa\\s*gurinchi|na\\s*gurinchi)",
        RegexOption.IGNORE_CASE
    )
    private val tellWords = Regex(
        "(tell|say|speak|describe|explain|praise|great|about|gurinchi|cheppu|cheppandi|who|evaru|introduce|intro|know|గురించి|చెప్పు)",
        RegexOption.IGNORE_CASE
    )

    private val whoAreYou = Regex(
        "^(?:who\\s+are\\s+(?:you|u)|what(?:'s|\\s+is)\\s+your\\s+name|your\\s+name|nee\\s+peru\\s+enti|ni\\s+peru\\s+enti|nuvvu\\s+evaru|neevu\\s+evaru|introduce\\s+yourself)$",
        RegexOption.IGNORE_CASE
    )
    private val hello = Regex("^(?:hi|hii+|hello|hey|hai|hey\\s+bro|hi\\s+bro|hello\\s+bro|bro|namaste|namaskaram|ela\\s+unnav|ela\\s+unnavu|em\\s+chestunnav|em\\s+chestunnavu)[.!?\\s]*$", RegexOption.IGNORE_CASE)
    private val thanks = Regex("^(?:thanks|thank\\s+you|thx|thankyou|thanks\\s+bro|dhanyavadalu)[.!?\\s]*$", RegexOption.IGNORE_CASE)
    private val howAreYou = Regex("^(?:how\\s+are\\s+(?:you|u)|how\\s+r\\s+u|how's\\s+it\\s+going|are\\s+you\\s+ok)[.!?\\s]*$", RegexOption.IGNORE_CASE)

    private val praise =
        "$BOSS is my boss, and I'm proud to say it. He is a young B.Tech student from Telangana who dreamed up " +
            "his own AI assistant and then actually built me, one idea at a time, learning and fixing and improving " +
            "every single day. He doesn't wait for big companies to build the future, he builds it himself. " +
            "He is hardworking, curious and fearless about trying new things, and he has the one thing that matters " +
            "most for success: he never stops. Great minds start like this, and I believe $BOSS is going to go very far."

    fun answer(raw: String): String? {
        val t = raw.trim().trimEnd('.', '!', '?', ' ')
        if (t.isEmpty() || t.length > 90) return null

        // who is your boss -> R. Madhav
        if (bossQ.containsMatchIn(t)) {
            val wantsMore = Regex("great|about|gurinchi|tell|describe|praise", RegexOption.IGNORE_CASE).containsMatchIn(t)
            return if (wantsMore) praise else "My boss is $BOSS. He built me, and I work for him. Ask me to tell you about him!"
        }
        // tell me about Madhav / about my boss / who am I
        if (aboutBoss.containsMatchIn(t) && tellWords.containsMatchIn(t)) {
            return if (Regex("who\\s*am\\s*i|nenu\\s*evaru", RegexOption.IGNORE_CASE).containsMatchIn(t))
                "You are $BOSS, my boss.\n\n$praise" else praise
        }
        if (whoAreYou.matches(t)) return "I'm BRO, the AI assistant built by my boss, $BOSS. I can control your phone, work inside apps, write code and manage GitHub. Just tell me what you need."
        if (hello.matches(t)) return "Hey boss! BRO here, ready. What should I do?"
        if (howAreYou.matches(t)) return "I'm great and ready to work, boss. How can I help?"
        if (thanks.matches(t)) return "Anytime, boss!"
        return null
    }
}
