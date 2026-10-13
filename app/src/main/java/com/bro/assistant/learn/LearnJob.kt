package com.bro.assistant.learn

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import com.bro.assistant.memory.PreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar

/** Studies by itself at night (between 12 at night and 3 in the morning) while the phone is idle. */
class LearnJobService : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val prefs = PreferencesStore(this)
        val awake = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (!prefs.learnNightly || !prefs.hasBrain() || awake) {
            LearnJob.schedule(this, nextNight = true)   // you are using the phone: try again tomorrow night
            return false
        }
        job = CoroutineScope(Dispatchers.Default).launch {
            try {
                Learner.session(applicationContext, prefs, maxLessons = 6, budgetMs = 8 * 60_000L)
            } catch (_: Exception) {
            } finally {
                LearnJob.schedule(applicationContext, nextNight = true)
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        LearnJob.schedule(this, nextNight = true)
        return false
    }
}

object LearnJob {
    private const val ID = 4401

    /** Plans one run inside tonight's 00:00 to 03:00 window (or tomorrow's). */
    fun schedule(c: Context, nextNight: Boolean = false) {
        try {
            val js = c.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val prefs = PreferencesStore(c)
            if (!prefs.learnNightly) { js.cancel(ID); return }
            val now = Calendar.getInstance()
            val start = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            val end = (start.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 3) }
            // today's window already over, or we just ran it: use the next one
            if (nextNight || now.after(end)) { start.add(Calendar.DAY_OF_YEAR, 1); end.add(Calendar.DAY_OF_YEAR, 1) }
            val latency = maxOf(0L, start.timeInMillis - now.timeInMillis)
            val deadline = maxOf(latency + 60_000L, end.timeInMillis - now.timeInMillis)
            val info = JobInfo.Builder(ID, ComponentName(c, LearnJobService::class.java))
                .setMinimumLatency(latency)
                .setOverrideDeadline(deadline)
                .setRequiresBatteryNotLow(true)
                .setPersisted(true)
                .build()
            js.schedule(info)
        } catch (_: Exception) {}
    }
}
