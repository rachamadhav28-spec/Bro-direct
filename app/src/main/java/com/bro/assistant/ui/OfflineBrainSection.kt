package com.bro.assistant.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bro.assistant.ai.LocalLlm
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings block for BRO's own on-device brain (no internet, no API key). */
@Composable
fun OfflineBrainSection(prefs: PreferencesStore) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(LocalLlm.isInstalled(ctx)) }
    var offlineOnly by remember { mutableStateOf(prefs.brainMode == "offline") }
    var link by remember { mutableStateOf("") }
    var downloadId by remember { mutableStateOf(-1L) }
    var status by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) LocalLlm.copyFrom(ctx, uri, prefs)
    }
    // the copy runs in the background (not tied to this screen); show its progress and result here
    LaunchedEffect(LocalLlm.copyStatus) {
        if (LocalLlm.copyStatus.isNotEmpty()) status = LocalLlm.copyStatus
        installed = LocalLlm.isInstalled(ctx)
    }

    // follow the download while it runs
    LaunchedEffect(downloadId) {
        if (downloadId < 0) return@LaunchedEffect
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        while (true) {
            val c = dm.query(DownloadManager.Query().setFilterById(downloadId))
            var done = false
            if (c != null && c.moveToFirst()) {
                val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val got = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        installed = LocalLlm.finishPart(ctx, link.trim()) && LocalLlm.isInstalled(ctx)
                        status = if (installed) "Download finished. Offline brain installed." else "The download finished but the file looks incomplete."
                        done = true
                    }
                    DownloadManager.STATUS_FAILED -> { status = "Download failed. Check the link and your internet."; done = true }
                    else -> status = "Downloading... " + (got / 1_000_000) + " MB" + (if (total > 0) " of " + (total / 1_000_000) + " MB" else "")
                }
            }
            c?.close()
            if (done) { downloadId = -1; break }
            delay(1000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (installed) "Offline brain: installed (" + (LocalLlm.modelFile(ctx).length() / 1_000_000) + " MB)"
            else "Offline brain: not installed",
            color = if (installed) Color(0xFF66BB6A) else Color.LightGray
        )
        Text(
            "Lets BRO think on the phone with no internet and no API key. One-time download (use Wi-Fi). " +
                "Recommended: Gemma 4 E2B from litert-community (about 2.6 GB, needs roughly 4 GB free RAM). " +
                "Or paste the link of any .litertlm / .task model file, or download one yourself and choose the file.",
            color = Color.Gray, fontSize = 12.sp
        )
        OutlinedButton(onClick = {
            link = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        }) { Text("Use Gemma 4 E2B link") }
        OutlinedTextField(
            value = link, onValueChange = { link = it },
            label = { Text("Link to the .task model file") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = link.startsWith("https://") && downloadId < 0, onClick = {
                try {
                    LocalLlm.deleteAll(ctx)
                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val req = DownloadManager.Request(Uri.parse(link.trim()))
                        .setTitle("BRO offline brain")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalFilesDir(ctx, null, LocalLlm.partFile(ctx, link.trim()).name)
                    prefs.localModelName = link.trim().substringAfterLast('/').substringBefore('?')
                    downloadId = dm.enqueue(req)
                    status = "Starting download..."
                } catch (e: Exception) { status = "Could not start the download: ${e.message}" }
            }) { Text("Download") }
            OutlinedButton(onClick = {
                // the picker leaves BRO for a while: do not lock BRO because of that
                com.bro.assistant.security.LockGrace.allowFor(15 * 60_000L)
                picker.launch(arrayOf("*/*"))
            }) { Text("Choose file") }
            if (installed) OutlinedButton(onClick = {
                LocalLlm.deleteAll(ctx); installed = false; status = "Offline brain removed."
            }) { Text("Remove") }
        }
        if (status.isNotEmpty()) Text(status, color = Color.LightGray, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Always use the offline brain (never Gemini)", color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = offlineOnly, onCheckedChange = { offlineOnly = it; prefs.brainMode = if (it) "offline" else "auto" })
        }
        var buildMode by remember { mutableStateOf(prefs.appBuildMode) }
        Text("When BRO builds an app", color = Color.White, fontSize = 15.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("api" to "API keys", "offline" to "Offline", "team" to "Team").forEach { (key, label) ->
                val pick = { buildMode = key; prefs.appBuildMode = key }
                if (buildMode == key) Button(onClick = pick) { Text(label) } else OutlinedButton(onClick = pick) { Text(label) }
            }
        }
        Text(
            "API keys = cloud AI only. Offline = the offline brain only (small one-screen apps). Team = the cloud AI writes, the offline brain reviews, and each covers for the other.",
            color = Color.Gray, fontSize = 12.sp
        )
        Text(
            "Off = BRO uses Gemini when online and switches to the offline brain when there is no internet or no key.",
            color = Color.Gray, fontSize = 12.sp
        )
    }
}
