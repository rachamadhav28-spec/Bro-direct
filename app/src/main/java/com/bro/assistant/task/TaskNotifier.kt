package com.bro.assistant.task

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bro.assistant.BroListenerService
import com.bro.assistant.MainActivity
import com.bro.assistant.PermissionManager

object TaskNotifier {

    private const val CHANNEL_TASKS = "bro_tasks"
    private const val CHANNEL_WAKE = "bro_wake"
    private const val ID_DONE = 2003
    private const val ID_WAKE = 2004

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_TASKS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_TASKS, "BRO tasks", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (nm.getNotificationChannel(CHANNEL_WAKE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_WAKE, "BRO wake", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun openApp(context: Context, wake: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(BroListenerService.EXTRA_WAKE, wake)
        return PendingIntent.getActivity(
            context, if (wake) 1 else 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun ongoing(context: Context, text: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BRO")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp(context, false))
            .build()
    }

    fun listening(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BRO")
            .setContentText("Listening for your wake phrase")
            .setOngoing(true)
            .setContentIntent(openApp(context, false))
            .build()
    }

    @SuppressLint("MissingPermission")
    fun notifyDone(context: Context, text: String) {
        if (!PermissionManager.hasNotifications(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("While you were away")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, false))
            .build()
        NotificationManagerCompat.from(context).notify(ID_DONE, n)
    }

    @SuppressLint("MissingPermission")
    fun notifyWake(context: Context) {
        if (!PermissionManager.hasNotifications(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_WAKE)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BRO heard you")
            .setContentText("Tap to talk")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, true))
            .build()
        NotificationManagerCompat.from(context).notify(ID_WAKE, n)
    }
}
