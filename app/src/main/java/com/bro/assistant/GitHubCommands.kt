package com.bro.assistant

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.bro.assistant.ai.GitHubClient
import com.bro.assistant.ai.GitHubException
import com.bro.assistant.memory.PreferencesStore
import org.json.JSONException

/**
 * Plain-language GitHub commands. Anything that changes GitHub (create repo, issue, push code)
 * asks for confirmation first. Returns null when the text is not a GitHub command.
 */
class GitHubCommands(private val context: Context, private val prefs: PreferencesStore) {

    private val client = GitHubClient(prefs)
    private val builder = AppBuilder(prefs, client)

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private val repoRef = "([\\w.-]+(?:/[\\w.-]+)?)"

    private val listRepos = rx("^(?:list|show)\\s+(?:all\\s+)?(?:my\\s+)?(?:github\\s+)?repo(?:sitorie)?s$")
    private val createRepo = rx(
        "^create\\s+(?:a\\s+)?(?:new\\s+)?(private\\s+|public\\s+)?(?:github\\s+)?repo(?:sitory)?\\s+(?:called\\s+|named\\s+)?([\\w.-]+)$"
    )
    private val listFiles = rx("^(?:list|show)\\s+files\\s+(?:in|of)\\s+$repoRef(?:\\s+(?:folder|path)\\s+(\\S+))?$")
    private val readFile = rx("^(?:read|show|open)\\s+file\\s+(\\S+)\\s+(?:in|from|of)\\s+$repoRef$")
    private val issue = rx("^create\\s+(?:an\\s+)?issue\\s+(?:in|on)\\s+$repoRef\\s*[:\\-]\\s*(.+)$")
    private val push = rx(
        "^(?:push|upload|save|commit)\\s+(?:that\\s+|this\\s+|the\\s+|my\\s+|last\\s+)*code\\s+(?:to|in|into)\\s+$repoRef\\s+(?:as|in|at)\\s+(\\S+)$"
    )

    private val createApp = rx(
        "^(?:create|build|make|generate)\\s+(?:an?\\s+)?(?:android\\s+app|apk)" +
            "(?:\\s+(?:in|into)\\s+(?:repo\\s+)?([\\w.-]+))?\\s*(?:that|which|for|of|to|:|-)?\\s*(.+)$"
    )
    private val buildStatus = rx("^(?:check\\s+)?(?:the\\s+)?build\\s+(?:status\\s+)?(?:of|for|in)\\s+$repoRef$")
    private val whyFailed = rx("^why\\s+(?:did\\s+)?(?:the\\s+)?build\\s+fail(?:ed)?\\s+(?:of|for|in)\\s+$repoRef$")
    private val fixBuild = rx("^fix\\s+(?:the\\s+)?build\\s+(?:of|for|in)\\s+$repoRef$")
    private val downloadApk = rx("^(?:download|get)\\s+(?:the\\s+)?apk\\s+(?:of|for|from)\\s+$repoRef$")

    suspend fun tryHandle(
        rawText: String,
        lastCode: String?,
        confirm: suspend (String) -> Boolean
    ): String? {
        val text = TeluguNormalizer.clause(TeluguNormalizer.preprocess(rawText))
            .trim().trimEnd('.', '!', '?')
        val isGithub = listOf(listRepos, createRepo, listFiles, readFile, issue, push, createApp, buildStatus, whyFailed, fixBuild, downloadApk).any { it.matches(text) }
        if (!isGithub) return null
        if (prefs.githubToken.isBlank()) {
            return "I need your GitHub token first. Add it in Menu, Settings, GitHub token."
        }
        return try {
            run(text, lastCode, confirm)
        } catch (e: GitHubException) {
            when (e.code) {
                401 -> "GitHub rejected the token. Check it in Settings."
                403 -> "GitHub says no: ${e.message}. The token may be missing a permission. " +
                    "For apps and builds it needs both \"repo\" and \"workflow\"."
                404 -> "GitHub couldn't find that. Check the repo name and the token's access."
                422 -> "GitHub refused it: ${e.message}."
                else -> "GitHub error ${e.code}: ${e.message}"
            }
        } catch (e: AiException) {
            ErrorHandler.friendly(e)
        } catch (e: JSONException) {
            "The AI's answer wasn't valid. Please try again."
        } catch (e: IllegalStateException) {
            e.message ?: "Something went wrong. Please try again."
        } catch (e: java.io.IOException) {
            "I couldn't reach GitHub. Check your internet connection."
        }
    }

    private suspend fun run(text: String, lastCode: String?, confirm: suspend (String) -> Boolean): String {
        listRepos.matchEntire(text)?.let {
            val list = client.repos()
            return if (list.isEmpty()) "You have no repositories." else "Your repositories:\n" + list.joinToString("\n")
        }
        createRepo.matchEntire(text)?.let { m ->
            val private = m.groupValues[1].trim().equals("private", true)
            val name = m.groupValues[2]
            if (!confirm("Create ${if (private) "private" else "public"} repo \"$name\" on GitHub?")) return "Okay, I didn't create it."
            return "Created: " + client.createRepo(name, private)
        }
        listFiles.matchEntire(text)?.let { m ->
            val files = client.listFiles(m.groupValues[1], m.groupValues[2])
            return if (files.isEmpty()) "That folder is empty." else files.joinToString("\n")
        }
        readFile.matchEntire(text)?.let { m ->
            val content = client.readFile(m.groupValues[2], m.groupValues[1])
            return "```\n" + content.take(3000) + "\n```" + if (content.length > 3000) "\n(shortened)" else ""
        }
        issue.matchEntire(text)?.let { m ->
            val repo = m.groupValues[1]
            val title = m.groupValues[2].trim()
            if (!confirm("Create issue \"$title\" in $repo?")) return "Okay, I didn't create it."
            return "Issue created: " + client.createIssue(repo, title)
        }
        push.matchEntire(text)?.let { m ->
            val code = lastCode ?: return "I don't have any code to push yet. Ask me to write some first."
            val repo = m.groupValues[1]
            val path = m.groupValues[2]
            if (!confirm("Commit this code to $repo as $path?")) return "Okay, I didn't push it."
            val what = client.putFile(repo, path, code, "Add $path via BRO")
            return "File $what in $repo: $path"
        }
        createApp.matchEntire(text)?.let { m ->
            return builder.create(m.groupValues[2].trim(), m.groupValues[1].ifBlank { null }, confirm)
        }
        buildStatus.matchEntire(text)?.let { return builder.status(it.groupValues[1]) }
        whyFailed.matchEntire(text)?.let { return builder.whyFailed(it.groupValues[1]) }
        fixBuild.matchEntire(text)?.let { return builder.fix(it.groupValues[1], confirm) }
        downloadApk.matchEntire(text)?.let { m ->
            val repo = m.groupValues[1]
            val bytes = client.latestApk(repo) ?: return "There's no successful build with an APK yet. Say \"build status of $repo\"."
            val name = repo.substringAfter('/') + ".apk"
            return if (saveToDownloads(name, bytes)) {
                "Saved $name in your Downloads folder. Open Files, tap it, and allow the install."
            } else "I couldn't save the file on this phone."
        }
        return "I didn't understand that GitHub command."
    }

    private fun saveToDownloads(name: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }
}
