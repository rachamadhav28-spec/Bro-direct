package com.bro.assistant.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle

/** A small, dependency-free syntax colouring for code cards: comments, strings, numbers and keywords (tags and attributes for XML/HTML). */
private class Palette(val comment: Color, val string: Color, val number: Color, val keyword: Color)

private val DARK = Palette(Color(0xFF7C8699), Color(0xFF9BD79F), Color(0xFFFFB86B), Color(0xFFC4A7FF))
private val LIGHT = Palette(Color(0xFF6B7385), Color(0xFF2F7D3A), Color(0xFFB35A00), Color(0xFF7A3FD8))
private val XML_DARK = Palette(Color(0xFF7C8699), Color(0xFF9BD79F), Color(0xFFFFB86B), Color(0xFF7FB7FF))
private val XML_LIGHT = Palette(Color(0xFF6B7385), Color(0xFF2F7D3A), Color(0xFFB35A00), Color(0xFF1F5FBF))

private const val KOTLIN = "fun val var class object interface if else when for while do return break continue in is as null true false this super " +
    "override private public protected internal open abstract data sealed companion import package try catch finally throw suspend by init lateinit const enum typealias"
private const val JAVA = "public private protected static final class interface extends implements new return if else for while do switch case break continue " +
    "try catch finally throw throws import package void int long double float boolean char byte short null true false this super abstract enum instanceof"
private const val PYTHON = "def class if elif else for while return import from as try except finally raise with lambda pass break continue in is not and or " +
    "None True False yield global nonlocal async await"
private const val JS = "function const let var class if else for while return import export from default new try catch finally throw async await this null " +
    "undefined true false typeof instanceof switch case break continue extends super of in interface type enum"
private const val SHELL = "if then else elif fi for while do done case esac function in echo export cd git"
private const val SQL = "SELECT FROM WHERE INSERT INTO VALUES UPDATE SET DELETE CREATE TABLE ALTER DROP JOIN LEFT RIGHT INNER ON GROUP BY ORDER LIMIT AND OR NOT NULL AS PRIMARY KEY"
private const val GENERIC = "$JAVA fun val var when object fn let mut impl struct func package use"

private const val DQ = "\"(?:\\\\.|[^\"\\\\\\n])*\""
private const val SQ = "'(?:\\\\.|[^'\\\\\\n])*'"
private const val NUM = "\\b\\d+(?:\\.\\d+)?[fFLlUu]?\\b"

private fun words(list: String, ignoreCase: Boolean = false): String {
    val body = "\\b(?:" + list.trim().split(Regex("\\s+")).distinct().joinToString("|") + ")\\b"
    return if (ignoreCase) "(?i:$body)" else body
}

/** Four capture groups in a fixed order: 1 comment, 2 string, 3 number (or attribute), 4 keyword (or tag). */
private fun regexFor(lang: String): Regex? {
    val never = "(?!)"
    return when (lang) {
        "xml", "html", "svg" -> Regex("(<!--[\\s\\S]*?-->)|($DQ|$SQ)|([\\w:.-]+(?==))|(</?[A-Za-z][\\w.:-]*)")
        "json" -> Regex("($never)|($DQ)|(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)|(\\b(?:true|false|null)\\b)")
        "kotlin", "kt", "kts", "gradle", "groovy" ->
            Regex("(//[^\\n]*|/\\*[\\s\\S]*?\\*/)|(\"\"\"[\\s\\S]*?\"\"\"|$DQ|$SQ)|($NUM)|(${words(KOTLIN)})")
        "java" -> Regex("(//[^\\n]*|/\\*[\\s\\S]*?\\*/)|($DQ|$SQ)|($NUM)|(${words(JAVA)})")
        "python", "py" -> Regex("(#[^\\n]*)|(\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|$DQ|$SQ)|($NUM)|(${words(PYTHON)})")
        "javascript", "js", "jsx", "typescript", "ts", "tsx" ->
            Regex("(//[^\\n]*|/\\*[\\s\\S]*?\\*/)|(`[^`]*`|$DQ|$SQ)|($NUM)|(${words(JS)})")
        "bash", "sh", "shell", "zsh", "yaml", "yml", "toml", "properties" ->
            Regex("(#[^\\n]*)|($DQ|$SQ)|($NUM)|(${words(SHELL)})")
        "sql" -> Regex("(--[^\\n]*|/\\*[\\s\\S]*?\\*/)|($SQ|$DQ)|($NUM)|(${words(SQL, ignoreCase = true)})")
        "css" -> Regex("(/\\*[\\s\\S]*?\\*/)|($DQ|$SQ)|(#[0-9A-Fa-f]{3,8}\\b|-?\\d+(?:\\.\\d+)?(?:px|dp|sp|em|rem|%)?)|([a-z-]+(?=\\s*:))")
        "c", "cpp", "c++", "csharp", "c#", "go", "rust", "swift", "php", "dart", "ruby" ->
            Regex("(//[^\\n]*|/\\*[\\s\\S]*?\\*/|#[^\\n]*)|($DQ|$SQ)|($NUM)|(${words(GENERIC)})")
        else -> null
    }
}

/** [code] coloured for the current theme. Very large or unknown-language code is returned as plain text. */
fun highlight(code: String, lang: String, dark: Boolean): AnnotatedString {
    val l = lang.trim().lowercase()
    if (code.length > 60_000) return AnnotatedString(code)
    val rx = regexFor(l) ?: return AnnotatedString(code)
    val markup = l == "xml" || l == "html" || l == "svg"
    val pal = if (markup) (if (dark) XML_DARK else XML_LIGHT) else (if (dark) DARK else LIGHT)
    return buildAnnotatedString {
        var last = 0
        for (m in rx.findAll(code)) {
            if (m.value.isEmpty()) continue
            append(code.substring(last, m.range.first))
            val g = m.groups
            val color = when {
                g[1] != null -> pal.comment
                g[2] != null -> pal.string
                g[3] != null -> pal.number
                else -> pal.keyword
            }
            val comment = g[1] != null
            pushStyle(SpanStyle(color = color, fontStyle = if (comment) FontStyle.Italic else null))
            append(m.value)
            pop()
            last = m.range.last + 1
        }
        append(code.substring(last))
    }
}
