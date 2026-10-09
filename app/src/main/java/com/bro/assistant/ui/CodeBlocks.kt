package com.bro.assistant.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.ui.theme.BroColors

data class CodeBlock(val lang: String, val code: String, val fileName: String)

sealed interface Part {
    data class Plain(val text: String) : Part
    data class Code(val block: CodeBlock) : Part
}

private val extensions = mapOf(
    "python" to "py", "py" to "py", "kotlin" to "kt", "kt" to "kt", "java" to "java",
    "javascript" to "js", "js" to "js", "typescript" to "ts", "ts" to "ts", "html" to "html",
    "css" to "css", "sql" to "sql", "bash" to "sh", "sh" to "sh", "shell" to "sh", "json" to "json",
    "c" to "c", "cpp" to "cpp", "c++" to "cpp", "csharp" to "cs", "c#" to "cs", "go" to "go",
    "rust" to "rs", "swift" to "swift", "php" to "php", "ruby" to "rb", "xml" to "xml",
    "yaml" to "yaml", "markdown" to "md"
)

private val defaultNames = mapOf(
    "html" to "index", "css" to "style", "js" to "script", "javascript" to "script",
    "sql" to "query", "bash" to "script", "sh" to "script", "json" to "data", "kotlin" to "Main", "kt" to "Main"
)

internal fun fileNameFor(lang: String, code: String, index: Int): String {
    val ext = extensions[lang] ?: "txt"
    if (ext == "java") {
        Regex("(?:public\\s+)?class\\s+(\\w+)").find(code)?.let { return it.groupValues[1] + ".java" }
    }
    val base = defaultNames[lang] ?: "main"
    return if (index == 0) "$base.$ext" else "$base${index + 1}.$ext"
}

/** Splits a message into normal text and fenced code blocks. */
fun splitParts(text: String): List<Part> {
    val rx = Regex("```([^\\n`]*)\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
    val parts = mutableListOf<Part>()
    var last = 0
    var index = 0
    for (m in rx.findAll(text)) {
        val before = text.substring(last, m.range.first).trim()
        if (before.isNotEmpty()) parts.add(Part.Plain(before))
        val lang = m.groupValues[1].trim().lowercase()
        val code = m.groupValues[2].trimEnd()
        parts.add(Part.Code(CodeBlock(lang, code, fileNameFor(lang, code, index++))))
        last = m.range.last + 1
    }
    val tail = text.substring(last).trim()
    if (tail.isNotEmpty()) parts.add(Part.Plain(tail))
    return parts
}

/** Message text, with each code block shown as a file card (View / Download). */
@Composable
fun MessageBody(text: String) {
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
                    Text(part.text, color = Color.White, fontSize = 15.sp)
                }
                is Part.Code -> CodeCard(
                    block = part.block,
                    onDownload = { pending = part.block.code; saver.launch(part.block.fileName) }
                )
            }
        }
    }
}

@Composable
private fun CodeCard(block: CodeBlock, onDownload: () -> Unit) {
    var viewing by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val lines = block.code.lines().size

    Surface(
        color = Color(0xFF0B1220),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BroColors.Accent.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(block.fileName, color = BroColors.Accent, fontSize = 15.sp)
            Text(
                (if (block.lang.isBlank()) "code" else block.lang) + " · $lines lines",
                color = Color.LightGray, fontSize = 12.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { viewing = true }) { Text("View") }
                TextButton(onClick = onDownload) { Text("Download") }
            }
        }
    }

    if (viewing) {
        AlertDialog(
            onDismissRequest = { viewing = false },
            title = { Text(block.fileName) },
            text = {
                SelectionContainer {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(block.code, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(block.code))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { viewing = false }) { Text("Close") } }
        )
    }
}
