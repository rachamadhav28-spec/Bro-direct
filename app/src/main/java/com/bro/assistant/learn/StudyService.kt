package com.bro.assistant.learn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Study non-stop: BRO keeps learning until you press Stop. Shows a notification with a Stop button. */
class StudyService : Service() {
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopStudy("You pressed Stop."); return START_NOT_STICKY }
        if (job?.isActive == true) return START_STICKY
        val prefs = PreferencesStore(this)
        prefs.studyNonStop = true
        startForegroundNote("Starting to study...")
        StudyBus.running = true; StudyBus.learnedThisRun = 0; StudyBus.current = "starting"
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bro:study").apply { setReferenceCounted(false) }
        job = CoroutineScope(Dispatchers.Default).launch { loop(prefs) }
        return START_STICKY
    }

    private suspend fun loop(prefs: PreferencesStore) {
        val all = ArrayList<String>()
        var why = "Finished."
        var backoff = 10 * 60_000L
        try {
            while (true) {
                if (!prefs.hasBrain()) { why = "No AI key or offline brain to study with."; break }
                if (lowBattery()) { why = "Battery is low, so I stopped to save it."; break }
                try { wake?.acquire(10 * 60_000L) } catch (_: Exception) {}
                val learned = Learner.session(applicationContext, prefs, maxLessons = 1, budgetMs = 4 * 60_000L) { step ->
                    StudyBus.current = step; note("$step  (learned ${StudyBus.learnedThisRun} so far)")
                }
                try { if (wake?.isHeld == true) wake?.release() } catch (_: Exception) {}
                if (learned.isNotEmpty()) {
                    all.addAll(learned); StudyBus.learnedThisRun = all.size; backoff = 10 * 60_000L
                    // lessons are spaced out so the free AI services are not overloaded
                    StudyBus.current = "resting 2 minutes"; note("Learned ${all.size}. Resting 2 minutes.")
                    delay(if (Learner.hasFresh(applicationContext)) 2 * 60_000L else 60 * 60_000L)
                } else {
                    StudyBus.current = "waiting (no internet or AI limit)"; note("Waiting ${backoff / 60000} min (no internet or the AI said wait).")
                    delay(backoff); backoff = minOf(backoff * 2, 60 * 60_000L)
                }
            }
        } catch (e: CancellationException) {
            why = "Stopped."
        } catch (e: Exception) {
            why = "Stopped because of an error."
        } finally {
            StudyLog.add(applicationContext, all, why)
            finish(all, why)
        }
    }

    private fun lowBattery(): Boolean {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 1..14 && !bm.isCharging
    }

    private fun stopStudy(why: String) {
        PreferencesStore(this).studyNonStop = false
        job?.cancel()
        if (job == null) finish(emptyList(), why)
    }

    private fun finish(learned: List<String>, why: String) {
        StudyBus.running = false; StudyBus.current = ""
        try { if (wake?.isHeld == true) wake?.release() } catch (_: Exception) {}
        // what BRO did after studying: a summary you can read
        if (learned.isNotEmpty()) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("bro_study_done", "BRO study report", NotificationManager.IMPORTANCE_DEFAULT))
            val text = "Learned ${learned.size}: " + learned.joinToString(", ") + ". " + why + " I will use these notes in my answers."
            nm.notify(5105, Notification.Builder(this, "bro_study_done").setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("BRO finished studying").setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setAutoCancel(true).build())
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun builder(text: String): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, StudyService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "bro_study").setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("BRO is studying").setContentText(text).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop studying", stop).build()).build()
    }

    private fun startForegroundNote(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("bro_study", "BRO studying", NotificationManager.IMPORTANCE_LOW))
        val n = builder(text)
        if (Build.VERSION.SDK_INT >= 34) startForeground(5104, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(5104, n)
    }

    private fun note(text: String) {
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(5104, builder(text)) } catch (_: Exception) {}
    }

    override fun onDestroy() { job?.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "com.bro.assistant.STOP_STUDY"
        fun start(c: Context) {
            try { ContextCompat.startForegroundService(c, Intent(c, StudyService::class.java)) } catch (_: Exception) {}
        }
        fun stop(c: Context) {
            PreferencesStore(c).studyNonStop = false
            try { c.startService(Intent(c, StudyService::class.java).setAction(ACTION_STOP)) } catch (_: Exception) {}
        }
    }
}

/** Shared state the Settings screen watches. */
object StudyBus {
    var running by mutableStateOf(false)
    var current by mutableStateOf("")
    var learnedThisRun by mutableStateOf(0)
}
