package com.bro.assistant.features

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves the code BRO wrote as a PDF in Downloads/BRO, so you can open, share or print it. */
object CodePdf {
    private val I = RegexOption.IGNORE_CASE
    @Volatile var lastUri: Uri? = null
    @Volatile var lastName: String = ""

    private val block = Regex("```([A-Za-z0-9+#_.-]*)[ \\t]*\\r?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)

    /** Returns the saved file name, or null when there is no code (or the phone is too old to save it). */
    fun make(c: Context, request: String, answer: String): String? {
        val blocks = block.findAll(answer).map { it.groupValues[1].ifBlank { "code" } to it.groupValues[2].trimEnd() }.toList()
        if (blocks.isEmpty() || Build.VERSION.SDK_INT < 29) return null
        return try {
            val doc = PdfDocument()
            val w = 595; val h = 842; val margin = 36f
            val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textSize = 8f }
            val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 12f }
            val lineH = 10.5f
            val cols = ((w - 2 * margin) / mono.measureText("M")).toInt().coerceAtLeast(40)
            var pageNo = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            var canvas = page.canvas
            var y = margin
            fun newPage() {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create()); canvas = page.canvas; y = margin
            }
            canvas.drawText("BRO code: " + request.replace('\n', ' ').take(70), margin, y + 10f, head); y += 28f
            blocks.forEachIndexed { i, (lang, code) ->
                if (y + 30f > h - margin) newPage()
                canvas.drawText("File ${i + 1} (${lang})", margin, y + 8f, head.apply { textSize = 10f }); y += 20f
                for (raw in code.replace("\t", "    ").split('\n')) {
                    var line = raw
                    do {
                        val part = if (line.length > cols) line.substring(0, cols) else line
                        line = line.substring(part.length)
                        if (y + lineH > h - margin) newPage()
                        canvas.drawText(part, margin, y + 8f, mono); y += lineH
                    } while (line.isNotEmpty())
                }
                y += 14f
            }
            doc.finishPage(page)
            val name = "BRO-code-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".pdf"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/BRO")
            }
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: run { doc.close(); return null }
            c.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
            doc.close()
            lastUri = uri; lastName = name
            name
        } catch (e: Exception) { null }
    }

    /** "open the pdf" / "share the pdf" */
    fun handle(c: Context, raw: String): String? {
        val t = raw.trim()
        val open = Regex("^(?:open|show|view)\\s+(?:the\\s+|that\\s+|my\\s+)?(?:code\\s+)?pdf[.!\\s]*$", I).matches(t)
        val share = Regex("^(?:share|send)\\s+(?:the\\s+|that\\s+|my\\s+)?(?:code\\s+)?pdf[.!\\s]*$", I).matches(t)
        if (!open && !share) return null
        val uri = lastUri ?: return "I haven't made a code PDF yet. Ask me for some code first."
        return try {
            val i = if (open) Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
            else Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri), "Share code PDF")
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            c.startActivity(i)
            if (open) "Opening $lastName." else "Choose where to send $lastName."
        } catch (e: Exception) { "I couldn't open it. It is saved in Downloads/BRO as $lastName." }
    }
}
