package com.bro.assistant.features

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bro.assistant.MainActivity
import com.bro.assistant.PermissionManager

/**
 * BRO "calls" you inside the app: a full-screen incoming-call screen with a ringtone. When you answer, BRO talks
 * and listens to you (two-way) with your phone's own microphone. It is not a real phone call, so it costs nothing.
 */
object BroCall {
    const val EXTRA_CALL = "bro_call"
    const val EXTRA_TEXT = "bro_call_text"
    const val ACTION_DECLINE = "com.bro.assistant.CALL_DECLINE"
    private const val CHANNEL = "bro_call"
    const val NOTIF_ID = 2090

    fun ring(c: Context, text: String) {
        val app = c.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "BRO calls", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
        val open = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_CALL, true).putExtra(EXTRA_TEXT, text)
        val full = PendingIntent.getActivity(app, 90, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val decline = PendingIntent.getBroadcast(app, 91, Intent(app, CallReceiver::class.java).setAction(ACTION_DECLINE), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("BRO is calling")
            .setContentText(text.take(80))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setTimeoutAfter(45_000L)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(android.R.drawable.ic_menu_call, "Answer", full)
            .addAction(android.R.drawable.ic_delete, "Decline", decline)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT   // keeps ringing until answered, declined or 45 seconds pass
        notify(app, n)
    }

    @SuppressLint("MissingPermission")
    private fun notify(app: Context, n: Notification) {
        if (PermissionManager.hasNotifications(app)) NotificationManagerCompat.from(app).notify(NOTIF_ID, n)
    }

    fun stopRinging(c: Context) { NotificationManagerCompat.from(c.applicationContext).cancel(NOTIF_ID) }

    fun canFullScreen(c: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BroCall.ACTION_DECLINE) BroCall.stopRinging(context)
    }
}
