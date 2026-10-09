package com.bro.assistant

import com.bro.assistant.ai.GitHubClient
import com.bro.assistant.ai.GitHubException
import com.bro.assistant.memory.PreferencesStore

/**
 * Plain-language GitHub commands. Anything that changes GitHub (create repo, issue, push code)
 * asks for confirmation first. Returns null when the text is not a GitHub command.
 */
class GitHubCommands(private val prefs: PreferencesStore) {

    private val client = GitHubClient(prefs)

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

    suspend fun tryHandle(
        rawText: String,
        lastCode: String?,
        confirm: suspend (String) -> Boolean
    ): String? {
        val text = TeluguNormalizer.clause(TeluguNormalizer.preprocess(rawText))
            .trim().trimEnd('.', '!', '?')
        val isGithub = listOf(listRepos, createRepo, listFiles, readFile, issue, push).any { it.matches(text) }
        if (!isGithub) return null
        if (prefs.githubToken.isBlank()) {
            return "I need your GitHub token first. Add it in Menu, Settings, GitHub token."
        }
        return try {
            run(text, lastCode, confirm)
        } catch (e: GitHubException) {
            when (e.code) {
                401 -> "GitHub rejected the token. Check it in Settings."
                403 -> "GitHub says no: ${e.message}. The token may be missing permission."
                404 -> "GitHub couldn't find that. Check the repo name and the token's access."
                422 -> "GitHub refused it: ${e.message}."
                else -> "GitHub error ${e.code}: ${e.message}"
            }
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
        return "I didn't understand that GitHub command."
    }
}
