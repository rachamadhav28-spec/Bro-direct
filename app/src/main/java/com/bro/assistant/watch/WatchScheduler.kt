package com.bro.assistant.watch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object WatchScheduler {
    const val ACTION_RUN = "com.bro.assistant.WATCH_RUN"

    private fun pi(c: Context, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            c, id.hashCode(),
            Intent(c, WatchReceiver::class.java).setAction(ACTION_RUN).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun schedule(c: Context, w: Watch) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, w.nextRun, pi(c, w.id))
    }

    fun cancel(c: Context, id: String) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(c, id))
    }

    fun rescheduleAll(c: Context) {
        val now = System.currentTimeMillis()
        WatchStore.all(c).forEach { schedule(c, it.also { w -> if (w.nextRun < now) w.nextRun = now + 60_000L }) }
    }
}

/** Wakes BRO when a watch is due, and re-arms the alarms after the phone restarts. */
class WatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WatchScheduler.ACTION_RUN -> intent.getStringExtra("id")?.let { WatchRunner.run(context.applicationContext, it) }
            Intent.ACTION_BOOT_COMPLETED -> WatchScheduler.rescheduleAll(context.applicationContext)
        }
    }
}
