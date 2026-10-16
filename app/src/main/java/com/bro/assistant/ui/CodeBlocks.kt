package com.bro.assistant.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.ai.CodeFormat
import com.bro.assistant.ui.theme.BroColors

data class CodeBlock(val lang: String, val code: String, val fileName: String)

sealed interface Part {
    data class Plain(val text: String) : Part
    data class Code(val block: CodeBlock) : Part
}

/** Splits a message into normal text and fenced code blocks. Each block gets its real file name and is cleaned of separator lines. */
fun splitParts(text: String): List<Part> {
    val rx = Regex("```([^\\n`]*)\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
    val parts = mutableListOf<Part>()
    val used = mutableSetOf<String>()
    var last = 0
    for (m in rx.findAll(text)) {
        val beforeRaw = text.substring(last, m.range.first)
        val before = beforeRaw.trim()
        if (before.isNotEmpty()) parts.add(Part.Plain(before))
        val lang = m.groupValues[1].trim().lowercase()
        val raw = m.groupValues[2].trimEnd()
        val code = if (lang == "md" || lang == "markdown") raw else CodeFormat.clean(raw)
        parts.add(Part.Code(CodeBlock(lang, code, CodeFormat.fileName(lang, code, beforeRaw, used))))
        last = m.range.last + 1
    }
    val tail = text.substring(last).trim()
    if (tail.isNotEmpty()) parts.add(Part.Plain(tail))
    return parts
}

private fun AnnotatedString.Builder.appendInline(line: String) {
    val rx = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    var last = 0
    for (m in rx.findAll(line)) {
        append(line.substring(last, m.range.first))
        if (m.groupValues[1].isNotEmpty()) {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(m.groupValues[1].replace("`", ""))
            pop()
        } else {
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x33FFFFFF)))
            append(m.groupValues[2])
            pop()
        }
        last = m.range.last + 1
    }
    append(line.substring(last))
}

/** A light markdown reader: **bold**, `code`, # headings, bullets and tables, so replies do not show raw symbols. */
internal fun markdownLite(src: String): AnnotatedString = buildAnnotatedString {
    var first = true
    val headingRx = Regex("^\\s{0,3}#{1,6}\\s+(.*)$")
    val bulletRx = Regex("^(\\s*)[-*]\\s+(.*)$")
    val tableSepRx = Regex("^\\|?[\\s:|\\-]+\\|?$")
    val ruleRx = Regex("^[-*_]{3,}$")
    for (raw in src.lines()) {
        val t = raw.trim()
        if (ruleRx.matches(t)) continue
        if (t.contains("|") && t.contains("-") && tableSepRx.matches(t)) continue
        if (!first) append("\n")
        first = false
        var line = raw
        var heading = false
        val h = headingRx.find(line)
        if (h != null) {
            line = h.groupValues[1]
            heading = true
        } else if (t.startsWith("|")) {
            line = t.trim('|').split("|").joinToString("  •  ") { it.trim() }
        } else {
            val b = bulletRx.find(line)
            if (b != null) line = b.groupValues[1] + "• " + b.groupValues[2]
        }
        val start = length
        appendInline(line)
        if (heading) addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp), start, length)
    }
}

/** Message text, with each code block shown as a code card (file name, Copy, Download, and the code itself). */
@Composable
fun MessageBody(text: String, markdown: Boolean = true) {
    val parts = remember(text) { splitParts(text) }
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val code = pending
        pending = null
        if (uri != null && code != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(code.toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            Toast.makeText(context, if (ok) "Saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (part in parts) {
            when (part) {
                is Part.Plain -> SelectionContainer {
                    if (markdown) Text(remember(part.text) { markdownLite(part.text) }, color = Color.White, fontSize = 15.sp)
                    else Text(part.text, color = Color.White, fontSize = 15.sp)
                }
                is Part.Code -> CodeCard(
                    block = part.block,
                    onDownload = { pending = part.block.code; saver.launch(part.block.fileName) }
                )
            }
        }
    }
}

private const val PREVIEW_LINES = 16

@Composable
private fun CodeCard(block: CodeBlock, onDownload: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val allLines = remember(block.code) { block.code.lines() }
    val long = allLines.size > PREVIEW_LINES
    val shown = if (long && !expanded) allLines.take(PREVIEW_LINES).joinToString("\n") else block.code

    Surface(
        color = Color(0xFF0B1220),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BroColors.Accent.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(block.fileName, color = BroColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (block.lang.isBlank()) "code" else block.lang) + " · ${allLines.size} lines",
                        color = Color.LightGray, fontSize = 11.sp
                    )
                }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(block.code))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                TextButton(onClick = onDownload) { Text("Download") }
            }
            SelectionContainer {
                Text(
                    shown,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFE6EDF3),
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp)
                )
            }
            if (long) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(start = 4.dp)) {
                    Text(if (expanded) "Show less" else "Show all ${allLines.size} lines")
                }
            }
        }
    }
}
