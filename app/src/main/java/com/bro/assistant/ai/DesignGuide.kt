package com.bro.assistant.ai

/** How BRO thinks about interfaces: a short professional UI/UX checklist added to prompts when the work has a screen. */
object DesignGuide {
    private val ui = Regex(
        "\\b(?:ui|ux|screen|layout|design|page|website|web\\s*site|landing|app|button|form|dashboard|navbar|menu|login|theme|colou?r|font|compose|html|css|react|flutter|figma|mockup|wireframe|responsive)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isUi(text: String) = ui.containsMatchIn(text)

    val GUIDE = """
        Think like a senior UI/UX designer before you write any interface code:
        - First decide who the user is and the ONE main task of each screen. Make that task the most prominent thing.
        - Clear visual hierarchy: one primary action per screen, sizes and weights that show importance, generous whitespace.
        - Consistent spacing on an 8dp grid, one type scale (title, body, caption), and a small palette (1 primary, 1 accent, neutrals, plus success/warning/error).
        - Contrast: body text at least 4.5:1 against its background. Never rely on colour alone to show meaning.
        - Touch targets at least 48dp, thumb-friendly bottom or reachable actions on phones, readable text of at least 14sp.
        - Design every state: empty (say what to do next), loading, error (say how to fix it), success, and disabled.
        - Forms: label every field, show the right keyboard, validate as the user goes, and keep the entry short.
        - Feedback for every tap (pressed state, short animation 150 to 300 ms), no surprises, easy undo for destructive actions.
        - Navigation must be obvious and consistent; the user should always know where they are and how to go back.
        - Use real, helpful microcopy (short verbs, no jargon). Support dark mode and different screen sizes.
    """.trimIndent()

    /** Short version for the review step. */
    val CRITIQUE = "Also critique the interface like a UI/UX designer: hierarchy, spacing, contrast, touch size, empty/error/loading states, " +
        "clear navigation. Fix any weakness in the code you return."
}
