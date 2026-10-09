package com.bro.assistant.ai

import android.util.Base64
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GitHubException(val code: Int, message: String) : Exception(message)

/** Minimal GitHub REST client. The personal access token is stored only on this phone. */
class GitHubClient(private val prefs: PreferencesStore) {

    private suspend fun call(method: String, path: String, body: JSONObject? = null): String =
        withContext(Dispatchers.IO) {
            val conn = URL("https://api.github.com$path").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("Authorization", "Bearer ${prefs.githubToken}")
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                conn.setRequestProperty("User-Agent", "BRO-Assistant")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    val msg = runCatching { JSONObject(text).getString("message") }.getOrDefault(text.take(120))
                    throw GitHubException(code, msg)
                }
                text
            } finally {
                conn.disconnect()
            }
        }

    suspend fun login(): String = JSONObject(call("GET", "/user")).getString("login")

    /** "name" -> "<me>/name"; "owner/name" stays as is. */
    suspend fun fullName(repo: String): String = if ("/" in repo) repo else "${login()}/$repo"

    suspend fun repos(): List<String> {
        val arr = JSONArray(call("GET", "/user/repos?per_page=30&sort=updated"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            o.getString("full_name") + if (o.optBoolean("private")) " (private)" else ""
        }
    }

    suspend fun createRepo(name: String, private: Boolean): String {
        val o = JSONObject(call("POST", "/user/repos", JSONObject().put("name", name).put("private", private)))
        return o.getString("html_url")
    }

    suspend fun listFiles(repo: String, path: String): List<String> {
        val full = fullName(repo)
        val raw = call("GET", "/repos/$full/contents/${path.trim('/')}")
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            o.getString("name") + if (o.getString("type") == "dir") "/" else ""
        }
    }

    suspend fun readFile(repo: String, path: String): String {
        val full = fullName(repo)
        val o = JSONObject(call("GET", "/repos/$full/contents/${path.trim('/')}"))
        val b64 = o.getString("content").replace("\n", "")
        return String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
    }

    /** Creates or updates a file with a commit on the default branch. */
    suspend fun putFile(repo: String, path: String, content: String, message: String): String {
        val full = fullName(repo)
        val clean = path.trim('/')
        val sha = try {
            JSONObject(call("GET", "/repos/$full/contents/$clean")).getString("sha")
        } catch (e: GitHubException) {
            if (e.code == 404) null else throw e
        }
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        if (sha != null) body.put("sha", sha)
        call("PUT", "/repos/$full/contents/$clean", body)
        return if (sha == null) "created" else "updated"
    }

    suspend fun createIssue(repo: String, title: String): String {
        val full = fullName(repo)
        val o = JSONObject(call("POST", "/repos/$full/issues", JSONObject().put("title", title)))
        return o.getString("html_url")
    }
}
