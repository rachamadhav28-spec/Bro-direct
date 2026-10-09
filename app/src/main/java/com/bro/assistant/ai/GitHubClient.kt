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

    suspend fun createRepo(name: String, private: Boolean, autoInit: Boolean = false): String {
        val o = JSONObject(
            call(
                "POST", "/user/repos",
                JSONObject().put("name", name).put("private", private).put("auto_init", autoInit)
            )
        )
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

    data class RunInfo(val id: Long, val status: String, val conclusion: String?, val url: String)

    suspend fun latestRun(repo: String, onlySuccess: Boolean = false): RunInfo? {
        val full = fullName(repo)
        val q = if (onlySuccess) "&status=success" else ""
        val arr = JSONObject(call("GET", "/repos/$full/actions/runs?per_page=1$q")).getJSONArray("workflow_runs")
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        val conclusion = if (o.isNull("conclusion")) null else o.optString("conclusion").ifBlank { null }
        return RunInfo(o.getLong("id"), o.getString("status"), conclusion, o.getString("html_url"))
    }

    /** Text of the failed job's log (may be long). */
    suspend fun failedLog(repo: String, runId: Long): String {
        val full = fullName(repo)
        val jobs = JSONObject(call("GET", "/repos/$full/actions/runs/$runId/jobs")).getJSONArray("jobs")
        for (i in 0 until jobs.length()) {
            val j = jobs.getJSONObject(i)
            if (j.optString("conclusion") == "failure") {
                return String(download("/repos/$full/actions/jobs/${j.getLong("id")}/logs"), Charsets.UTF_8)
            }
        }
        return ""
    }

    /** Bytes of the first .apk inside the latest successful run's artifact, or null. */
    suspend fun latestApk(repo: String): ByteArray? {
        val full = fullName(repo)
        val run = latestRun(repo, onlySuccess = true) ?: return null
        val arts = JSONObject(call("GET", "/repos/$full/actions/runs/${run.id}/artifacts")).getJSONArray("artifacts")
        if (arts.length() == 0) return null
        val zip = download("/repos/$full/actions/artifacts/${arts.getJSONObject(0).getLong("id")}/zip")
        java.util.zip.ZipInputStream(zip.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name.endsWith(".apk")) return z.readBytes()
            }
        }
        return null
    }

    /** GET that follows the redirect to the storage URL without sending the token there. */
    private suspend fun download(path: String): ByteArray = withContext(Dispatchers.IO) {
        val first = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        try {
            first.instanceFollowRedirects = false
            first.connectTimeout = 15000
            first.readTimeout = 60000
            first.setRequestProperty("Authorization", "Bearer ${prefs.githubToken}")
            first.setRequestProperty("Accept", "application/vnd.github+json")
            first.setRequestProperty("User-Agent", "BRO-Assistant")
            val code = first.responseCode
            when {
                code in 300..399 -> {
                    val loc = first.getHeaderField("Location") ?: throw GitHubException(code, "no download link")
                    val second = URL(loc).openConnection() as HttpURLConnection
                    try {
                        second.connectTimeout = 15000
                        second.readTimeout = 120000
                        val c2 = second.responseCode
                        if (c2 !in 200..299) throw GitHubException(c2, "download failed")
                        second.inputStream.use { it.readBytes() }
                    } finally {
                        second.disconnect()
                    }
                }
                code in 200..299 -> first.inputStream.use { it.readBytes() }
                else -> throw GitHubException(code, "download failed")
            }
        } finally {
            first.disconnect()
        }
    }

    data class Build(val status: String, val conclusion: String?, val url: String, val title: String, val artifacts: List<String>)

    suspend fun latestBuild(repo: String): Build? {
        val full = fullName(repo)
        val runs = JSONObject(call("GET", "/repos/$full/actions/runs?per_page=1")).getJSONArray("workflow_runs")
        if (runs.length() == 0) return null
        val r = runs.getJSONObject(0)
        val conclusion = if (r.isNull("conclusion")) null else r.getString("conclusion")
        val arts = if (conclusion == "success") {
            val a = JSONObject(call("GET", "/repos/$full/actions/runs/${r.getLong("id")}/artifacts")).getJSONArray("artifacts")
            (0 until a.length()).map { a.getJSONObject(it).getString("name") }
        } else emptyList()
        return Build(r.getString("status"), conclusion, r.getString("html_url"), r.optString("display_title"), arts)
    }

    suspend fun issues(repo: String): List<String> {
        val arr = JSONArray(call("GET", "/repos/${fullName(repo)}/issues?state=open&per_page=20"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { !it.has("pull_request") }
            .map { "#" + it.getInt("number") + " " + it.getString("title") }
    }

    suspend fun commentOnIssue(repo: String, number: Int, text: String) {
        call("POST", "/repos/${fullName(repo)}/issues/$number/comments", JSONObject().put("body", text))
    }

    suspend fun commits(repo: String): List<String> {
        val arr = JSONArray(call("GET", "/repos/${fullName(repo)}/commits?per_page=10"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            o.getString("sha").take(7) + " " + o.getJSONObject("commit").getString("message").lines().first()
        }
    }

    suspend fun pulls(repo: String): List<String> {
        val arr = JSONArray(call("GET", "/repos/${fullName(repo)}/pulls?state=open&per_page=20"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            "#" + o.getInt("number") + " " + o.getString("title") + " (" +
                o.getJSONObject("head").getString("ref") + " -> " + o.getJSONObject("base").getString("ref") + ")"
        }
    }

    suspend fun createBranch(repo: String, name: String): String {
        val full = fullName(repo)
        val base = JSONObject(call("GET", "/repos/$full")).getString("default_branch")
        val sha = JSONObject(call("GET", "/repos/$full/git/ref/heads/$base")).getJSONObject("object").getString("sha")
        call("POST", "/repos/$full/git/refs", JSONObject().put("ref", "refs/heads/$name").put("sha", sha))
        return base
    }

    suspend fun createPull(repo: String, head: String, base: String, title: String): String {
        val o = JSONObject(
            call(
                "POST", "/repos/${fullName(repo)}/pulls",
                JSONObject().put("title", title).put("head", head).put("base", base)
            )
        )
        return o.getString("html_url")
    }

    suspend fun mergePull(repo: String, number: Int): String =
        JSONObject(call("PUT", "/repos/${fullName(repo)}/pulls/$number/merge", JSONObject())).optString("message", "Merged")
}
