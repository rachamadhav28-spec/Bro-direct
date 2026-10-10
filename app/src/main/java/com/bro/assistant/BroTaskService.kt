package com.bro.assistant

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.bro.assistant.task.TaskNotifier

/** Keeps BRO alive (with a notification) while a task runs and the screen is not visible. */
class BroTaskService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val text = intent?.getStringExtra("text") ?: "Working on your task"
        val notification = TaskNotifier.ongoing(this, text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.bro.assistant.STOP_TASK"

        fun start(context: Context, text: String) {
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, BroTaskService::class.java).putExtra("text", text)
                )
            } catch (e: Exception) {
                ErrorHandler.log("BroTaskService", "could not start foreground service", e)
            }
        }

        fun stop(context: Context) {
            // Sent as a normal intent so it is handled after the start intent (stopping at once
            // after startForegroundService() crashes the app when the task finishes quickly).
            try {
                context.startService(Intent(context, BroTaskService::class.java).setAction(ACTION_STOP))
            } catch (e: Exception) {
                runCatching { context.stopService(Intent(context, BroTaskService::class.java)) }
            }
        }
    }
}
