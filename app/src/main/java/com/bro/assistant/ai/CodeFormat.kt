package com.bro.assistant.ai

/**
 * Makes BRO's code answers look like a proper code answer: real file names (MainActivity.kt, activity_main.xml),
 * no decorative separator comment lines, and the rules that are added to every coding prompt.
 */
object CodeFormat {

    const val RULES = "Code format rules: before EACH code block write the exact real file name on its own line in bold, " +
        "for example **MainActivity.kt** or **res/layout/activity_main.xml** (never names like script1.sh or main2.xml). " +
        "One file per code block, each in a fenced block with its language. When the user asks for the complete file, give the whole " +
        "file from the first line to the last, nothing skipped. Never put decorative separator comments in code: no lines made of " +
        "dashes, underscores, equals signs, box-drawing characters or banners, and no '// ---- Section ----' headers. " +
        "Use plain short comments only where they really help."

    private const val D = "[─━═—–\\-_=~*]"

    // a comment line that is only decoration (// ------, # ======, // ── Title ──, <!-- ____ -->)
    private val decoRx = Regex(
        "^(\\s*)(//+|#+|--|<!--|/\\*+)[ \\t]*(?:[─━═—–]$D+|$D{3,})[ \\t]*(.*?)[ \\t]*$D*[ \\t]*(?:-->|\\*+/)?[ \\t]*$"
    )

    /** Removes separator/banner comment lines. A banner with a title keeps just the title as a plain comment. */
    fun clean(code: String): String {
        val out = ArrayList<String>()
        for (line in code.lines()) {
            val m = decoRx.matchEntire(line)
            if (m == null) {
                out.add(line)
                continue
            }
            val label = m.groupValues[3].trim().let { if (it == "/") "" else it }
            if (label.isEmpty()) continue
            val marker = m.groupValues[2]
            val closer = when {
                marker.startsWith("<!--") -> " -->"
                marker.startsWith("/*") -> " */"
                else -> ""
            }
            out.add(m.groupValues[1] + marker + " " + label + closer)
        }
        return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trimEnd()
    }

    private val extensions = mapOf(
        "python" to "py", "py" to "py", "kotlin" to "kt", "kt" to "kt", "java" to "java",
        "javascript" to "js", "js" to "js", "typescript" to "ts", "ts" to "ts", "html" to "html",
        "css" to "css", "sql" to "sql", "bash" to "sh", "sh" to "sh", "shell" to "sh", "json" to "json",
        "c" to "c", "cpp" to "cpp", "c++" to "cpp", "csharp" to "cs", "c#" to "cs", "go" to "go",
        "rust" to "rs", "swift" to "swift", "php" to "php", "ruby" to "rb", "xml" to "xml",
        "yaml" to "yaml", "yml" to "yml", "markdown" to "md", "gradle" to "gradle", "groovy" to "gradle",
        "kts" to "kts", "toml" to "toml", "properties" to "properties", "dart" to "dart",
        "jsx" to "jsx", "tsx" to "tsx"
    )

    private val knownExt = extensions.values.toSet() + setOf("txt", "md")

    private val fileTokenRx = Regex("([A-Za-z0-9_\\-./]*[A-Za-z0-9_\\-]\\.([A-Za-z0-9]{1,10}))")

    /** A file name written on the line just above the code block, like **MainActivity.kt** or `res/layout/activity_main.xml`. */
    private fun nameFromText(before: String): String? {
        val line = before.trimEnd().lines().lastOrNull { it.isNotBlank() }?.takeLast(140) ?: return null
        for (m in fileTokenRx.findAll(line).toList().asReversed()) {
            val ext = m.groupValues[2].lowercase()
            if (ext in knownExt) return m.groupValues[1].substringAfterLast('/')
        }
        return null
    }

    private val commentNameRx = Regex(
        "^\\s*(?://+|#+|--|/\\*+|<!--)\\s*(?:file(?:name)?\\s*[:=-]?\\s*)?([A-Za-z0-9_\\-./]+\\.([A-Za-z0-9]{1,10}))\\s*(?:\\*/|-->)?\\s*$",
        RegexOption.IGNORE_CASE
    )

    private fun nameFromComment(code: String): String? {
        val first = code.lines().firstOrNull { it.isNotBlank() } ?: return null
        val m = commentNameRx.find(first) ?: return null
        return if (m.groupValues[2].lowercase() in knownExt) m.groupValues[1].substringAfterLast('/') else null
    }

    private fun nameFromContent(lang: String, code: String): String {
        val ext = extensions[lang] ?: "txt"
        when (ext) {
            "java" -> Regex("(?:public\\s+)?class\\s+(\\w+)").find(code)?.let { return it.groupValues[1] + ".java" }
            "kt" -> Regex("(?:class|object|interface)\\s+(\\w+)").find(code)?.let { return it.groupValues[1] + ".kt" }
            "py" -> return "main.py"
            "kts" -> return "build.gradle.kts"
            "gradle" -> return "build.gradle"
            "xml" -> {
                return when {
                    code.contains("<manifest") -> "AndroidManifest.xml"
                    code.contains("<resources") && code.contains("<style") -> "styles.xml"
                    code.contains("<resources") && code.contains("<color") -> "colors.xml"
                    code.contains("<resources") -> "strings.xml"
                    Regex("<(?:objectAnimator|animator|set|translate|alpha|scale|rotate)\\b").containsMatchIn(code) &&
                        !code.contains("layout_width") -> "animation.xml"
                    code.contains("<selector") || code.contains("<shape") || code.contains("<vector") -> "drawable.xml"
                    code.contains("layout_width") || code.contains("<androidx.") -> "activity_main.xml"
                    else -> "layout.xml"
                }
            }
        }
        val base = when (ext) {
            "html" -> "index"
            "css" -> "style"
            "js" -> "script"
            "sh" -> "script"
            "sql" -> "query"
            "json" -> "data"
            "kt" -> "Main"
            else -> "main"
        }
        return "$base.$ext"
    }

    /** The file name for one code block. [used] holds the names already used in the same message so none repeats. */
    fun fileName(lang: String, code: String, before: String, used: MutableSet<String>): String {
        val base = nameFromText(before) ?: nameFromComment(code) ?: nameFromContent(lang, code)
        var name = base
        var n = 2
        while (name in used) {
            val dot = base.lastIndexOf('.')
            name = if (dot > 0) base.substring(0, dot) + "_" + n + base.substring(dot) else base + "_" + n
            n++
        }
        used.add(name)
        return name
    }
}
