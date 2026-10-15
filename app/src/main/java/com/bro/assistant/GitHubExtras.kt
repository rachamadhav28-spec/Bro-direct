package com.bro.assistant

import com.bro.assistant.ai.GitHubClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * More GitHub abilities that go with the token's permissions: gists, notifications, profile and follows,
 * SSH/GPG keys, webhooks, codespaces, packages, projects, discussions, stars, visibility, workflows.
 * Anything that changes or deletes asks first.
 */
class GitHubExtras(private val c: GitHubClient) {
    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private val repo = "([\\w.-]+(?:/[\\w.-]+)?)"
    private val inRepo = "(?:in|on|of|for|from)\\s+$repo"

    private val visibility = rx("^make\\s+repo(?:sitory)?\\s+$repo\\s+(private|public)$")
    private val star = rx("^(star|unstar)\\s+repo(?:sitory)?\\s+$repo$")
    private val starred = rx("^list\\s+starred\\s+repos?$")
    private val gist = rx("^create\\s+(?:a\\s+)?(?:secret\\s+)?gist\\s+(?:from\\s+last\\s+code|([\\w.-]+)\\s*[:\\-]\\s*(.+))$")
    private val gists = rx("^list\\s+(?:my\\s+)?gists$")
    private val delGist = rx("^delete\\s+gist\\s+([0-9a-f]+)$")
    private val notes = rx("^(?:show|list|check)\\s+(?:my\\s+)?notifications$")
    private val readNotes = rx("^mark\\s+(?:all\\s+)?notifications\\s+(?:as\\s+)?read$")
    private val profile = rx("^show\\s+my\\s+github\\s+profile$")
    private val follow = rx("^(follow|unfollow)\\s+([\\w-]+)$")
    private val followers = rx("^list\\s+(followers|following)$")
    private val keys = rx("^list\\s+(ssh|gpg)\\s+keys$")
    private val hooks = rx("^list\\s+webhooks\\s+$inRepo$")
    private val delHook = rx("^delete\\s+webhook\\s+(\\d+)\\s+$inRepo$")
    private val codespaces = rx("^list\\s+codespaces$")
    private val codespaceAct = rx("^(stop|delete)\\s+codespace\\s+([\\w.-]+)$")
    private val packages = rx("^list\\s+packages$")
    private val projects = rx("^list\\s+projects$")
    private val discussions = rx("^list\\s+discussions\\s+$inRepo$")
    private val rerun = rx("^rerun\\s+(?:the\\s+)?build\\s+$inRepo$")
    private val workflows = rx("^list\\s+workflows\\s+$inRepo$")
    private val runWorkflow = rx("^run\\s+workflow\\s+(\\S+)\\s+$inRepo$")

    private val all = listOf(visibility, star, starred, gist, gists, delGist, notes, readNotes, profile, follow, followers, keys,
        hooks, delHook, codespaces, codespaceAct, packages, projects, discussions, rerun, workflows, runWorkflow)

    fun matches(t: String) = all.any { it.matches(t) }

    private fun arr(s: String) = JSONArray(s)
    private fun JSONArray.lines(f: (JSONObject) -> String): String =
        if (length() == 0) "Nothing there." else (0 until minOf(length(), 25)).joinToString("\n") { f(getJSONObject(it)) }

    suspend fun run(t: String, lastCode: String?, confirm: suspend (String) -> Boolean): String? {
        visibility.matchEntire(t)?.let { m ->
            val priv = m.groupValues[2].equals("private", true)
            if (!confirm("Make repo \"${m.groupValues[1]}\" ${if (priv) "private" else "public"}?")) return "Okay, I left it as it was."
            c.api("PATCH", "/repos/${c.fullName(m.groupValues[1])}", JSONObject().put("private", priv))
            return "Done. ${m.groupValues[1]} is now ${if (priv) "private" else "public"}."
        }
        star.matchEntire(t)?.let { m ->
            val on = m.groupValues[1].equals("star", true)
            c.api(if (on) "PUT" else "DELETE", "/user/starred/${c.fullName(m.groupValues[2])}")
            return if (on) "Starred ${m.groupValues[2]}." else "Removed the star from ${m.groupValues[2]}."
        }
        starred.matchEntire(t)?.let { return "Starred repos:\n" + arr(c.api("GET", "/user/starred?per_page=25")).lines { it.getString("full_name") } }
        gist.matchEntire(t)?.let { m ->
            val name = m.groupValues[1].ifBlank { "bro-code.txt" }
            val body = if (m.groupValues[1].isBlank()) lastCode else m.groupValues[2]
            if (body.isNullOrBlank()) return "I have no code to put in a gist yet."
            if (!confirm("Create a secret gist \"$name\" on your GitHub?")) return "Okay, I didn't create it."
            val files = JSONObject().put(name, JSONObject().put("content", body))
            val o = JSONObject(c.api("POST", "/gists", JSONObject().put("public", false).put("files", files)))
            return "Gist created (secret link): " + o.getString("html_url")
        }
        gists.matchEntire(t)?.let {
            return "Your gists:\n" + arr(c.api("GET", "/gists?per_page=25")).lines {
                it.getString("id") + "  " + (it.optJSONObject("files")?.keys()?.asSequence()?.firstOrNull() ?: "")
            }
        }
        delGist.matchEntire(t)?.let { m ->
            if (!confirm("Delete gist ${m.groupValues[1]} forever?")) return "Okay, I didn't delete it."
            c.api("DELETE", "/gists/${m.groupValues[1]}"); return "Gist deleted."
        }
        notes.matchEntire(t)?.let {
            return "Notifications:\n" + arr(c.api("GET", "/notifications?per_page=20")).lines {
                "• " + it.getJSONObject("repository").getString("full_name") + ": " + it.getJSONObject("subject").getString("title")
            }
        }
        readNotes.matchEntire(t)?.let {
            if (!confirm("Mark all your GitHub notifications as read?")) return "Okay."
            c.api("PUT", "/notifications", JSONObject()); return "All notifications marked as read."
        }
        profile.matchEntire(t)?.let {
            val o = JSONObject(c.api("GET", "/user"))
            return "${o.optString("name").ifBlank { o.getString("login") }} (@${o.getString("login")})\n" +
                "Public repos: ${o.optInt("public_repos")}, private repos: ${o.optInt("total_private_repos")}\n" +
                "Followers: ${o.optInt("followers")}, following: ${o.optInt("following")}\n" + o.getString("html_url")
        }
        follow.matchEntire(t)?.let { m ->
            val on = m.groupValues[1].equals("follow", true)
            c.api(if (on) "PUT" else "DELETE", "/user/following/${m.groupValues[2]}")
            return (if (on) "Now following " else "Unfollowed ") + m.groupValues[2] + "."
        }
        followers.matchEntire(t)?.let { m ->
            return "${m.groupValues[1].replaceFirstChar { it.uppercase() }}:\n" + arr(c.api("GET", "/user/${m.groupValues[1].lowercase()}?per_page=30")).lines { it.getString("login") }
        }
        keys.matchEntire(t)?.let { m ->
            return if (m.groupValues[1].equals("ssh", true))
                "SSH keys:\n" + arr(c.api("GET", "/user/keys")).lines { it.optString("title") + "  (id " + it.getLong("id") + ")" }
            else "GPG keys:\n" + arr(c.api("GET", "/user/gpg_keys")).lines { "id " + it.getLong("id") + "  " + it.optString("key_id") }
        }
        hooks.matchEntire(t)?.let { m ->
            return "Webhooks in ${m.groupValues[1]}:\n" + arr(c.api("GET", "/repos/${c.fullName(m.groupValues[1])}/hooks")).lines {
                "id " + it.getLong("id") + "  " + it.optJSONObject("config")?.optString("url").orEmpty()
            }
        }
        delHook.matchEntire(t)?.let { m ->
            if (!confirm("Delete webhook ${m.groupValues[1]} in ${m.groupValues[2]}?")) return "Okay, I didn't delete it."
            c.api("DELETE", "/repos/${c.fullName(m.groupValues[2])}/hooks/${m.groupValues[1]}"); return "Webhook deleted."
        }
        codespaces.matchEntire(t)?.let {
            return "Codespaces:\n" + JSONObject(c.api("GET", "/user/codespaces")).getJSONArray("codespaces").lines {
                it.getString("name") + "  " + it.optString("state")
            }
        }
        codespaceAct.matchEntire(t)?.let { m ->
            val stop = m.groupValues[1].equals("stop", true)
            if (!confirm("${if (stop) "Stop" else "DELETE"} codespace ${m.groupValues[2]}?")) return "Okay."
            if (stop) c.api("POST", "/user/codespaces/${m.groupValues[2]}/stop") else c.api("DELETE", "/user/codespaces/${m.groupValues[2]}")
            return if (stop) "Codespace stopped." else "Codespace deleted."
        }
        packages.matchEntire(t)?.let {
            return "Packages:\n" + arr(c.api("GET", "/user/packages?package_type=container")).lines { it.getString("name") + " (" + it.optString("package_type") + ")" }
        }
        projects.matchEntire(t)?.let {
            val q = JSONObject().put("query", "{viewer{projectsV2(first:20){nodes{title url closed}}}}")
            val nodes = JSONObject(c.api("POST", "/graphql", q)).getJSONObject("data").getJSONObject("viewer")
                .getJSONObject("projectsV2").getJSONArray("nodes")
            return "Projects:\n" + nodes.lines { it.getString("title") + (if (it.optBoolean("closed")) " (closed)" else "") + "  " + it.getString("url") }
        }
        discussions.matchEntire(t)?.let { m ->
            val full = c.fullName(m.groupValues[1]); val (o, n) = full.split("/", limit = 2)
            val q = JSONObject().put("query", "{repository(owner:\"$o\",name:\"$n\"){discussions(first:15){nodes{title url}}}}")
            val d = JSONObject(c.api("POST", "/graphql", q)).getJSONObject("data").optJSONObject("repository")
                ?: return "I couldn't open that repo's discussions."
            return "Discussions in $full:\n" + d.getJSONObject("discussions").getJSONArray("nodes").lines { it.getString("title") + "  " + it.getString("url") }
        }
        rerun.matchEntire(t)?.let { m ->
            val full = c.fullName(m.groupValues[1])
            val id = JSONObject(c.api("GET", "/repos/$full/actions/runs?per_page=1")).getJSONArray("workflow_runs")
                .optJSONObject(0)?.getLong("id") ?: return "There is no build to re-run in $full."
            c.api("POST", "/repos/$full/actions/runs/$id/rerun")
            return "Started the build again. Check GitHub Actions: https://github.com/$full/actions"
        }
        workflows.matchEntire(t)?.let { m ->
            return "Workflows in ${m.groupValues[1]}:\n" + JSONObject(c.api("GET", "/repos/${c.fullName(m.groupValues[1])}/actions/workflows"))
                .getJSONArray("workflows").lines { it.getString("path") + "  (" + it.optString("state") + ")" }
        }
        runWorkflow.matchEntire(t)?.let { m ->
            val full = c.fullName(m.groupValues[2])
            val def = JSONObject(c.api("GET", "/repos/$full")).getString("default_branch")
            if (!confirm("Run workflow ${m.groupValues[1]} in $full?")) return "Okay."
            c.api("POST", "/repos/$full/actions/workflows/${m.groupValues[1]}/dispatches", JSONObject().put("ref", def))
            return "Workflow started. Check https://github.com/$full/actions"
        }
        return null
    }
}
