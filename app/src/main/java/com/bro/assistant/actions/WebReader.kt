package com.bro.assistant.actions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** Lets BRO read web pages and search the web without opening a browser. Public pages only. */
object WebReader {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"

    class Hit(val title: String, val url: String, val snippet: String)

    private fun safeHost(u: URL): Boolean {
        if (u.protocol != "https" && u.protocol != "http") return false
        return try {
            InetAddress.getAllByName(u.host).none { it.isLoopbackAddress || it.isAnyLocalAddress || it.isSiteLocalAddress || it.isLinkLocalAddress }
        } catch (e: Exception) { false }
    }

    private fun fetch(start: String, maxBytes: Int = 700_000): String {
        var url = URL(start)
        repeat(5) {
            if (!safeHost(url)) throw IllegalStateException("I only read public web pages.")
            val c = url.openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 12000; c.readTimeout = 15000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept-Language", "en")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IllegalStateException("Bad redirect.")
                    url = URL(url, loc); return@repeat
                }
                if (code !in 200..299) throw IllegalStateException("The site answered with error $code.")
                val bytes = c.inputStream.use { s ->
                    val buf = java.io.ByteArrayOutputStream()
                    val tmp = ByteArray(8192); var total = 0
                    while (total < maxBytes) { val n = s.read(tmp); if (n < 0) break; buf.write(tmp, 0, n); total += n }
                    buf.toByteArray()
                }
                return String(bytes, Charsets.UTF_8)
            } finally { c.disconnect() }
        }
        throw IllegalStateException("Too many redirects.")
    }

    private fun clean(html: String): String {
        var s = html
        s = Regex("(?is)<(script|style|noscript|svg|head|nav|footer|form)[^>]*>.*?</\\1>").replace(s, " ")
        s = Regex("(?s)<!--.*?-->").replace(s, " ")
        s = Regex("(?i)<(br|/p|/div|/h[1-6]|/li|/tr)[^>]*>").replace(s, "\n")
        s = Regex("<[^>]+>").replace(s, " ")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        return s.lines().map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.length > 2 }.joinToString("\n")
    }

    /** Readable text of a page, shortened to what an AI can use. */
    suspend fun read(url: String, limit: Int = 6000): String = withContext(Dispatchers.IO) {
        clean(fetch(url)).take(limit).ifBlank { "The page had no readable text." }
    }

    suspend fun search(query: String): List<Hit> = withContext(Dispatchers.IO) {
        val html = fetch("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8"), 400_000)
        val links = Regex("(?s)<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>").findAll(html).toList()
        val snippets = Regex("(?s)class=\"result__snippet\"[^>]*>(.*?)</a>").findAll(html).map { clean(it.groupValues[1]) }.toList()
        links.mapIndexedNotNull { i, m ->
            var href = m.groupValues[1].replace("&amp;", "&")
            if (href.startsWith("//")) href = "https:$href"
            val real = Regex("[?&]uddg=([^&]+)").find(href)?.groupValues?.get(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: href
            if (!real.startsWith("http")) null else Hit(clean(m.groupValues[2]).take(100), real, snippets.getOrNull(i)?.take(180).orEmpty())
        }.take(6)
    }
}
