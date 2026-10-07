package com.bro.assistant

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.bro.assistant.memory.PreferencesStore
import com.bro.assistant.task.TaskNotifier
import com.bro.assistant.voice.WakeWordListener

/**
 * Foreground service that listens for the wake phrase while BRO's screen is closed.
 * Android limits this: it needs a visible notification, it uses battery, and on some
 * phones the system can still stop it. It pauses while the BRO screen is open.
 */
class BroListenerService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var listener: WakeWordListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val notification = TaskNotifier.listening(this)
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = PreferencesStore(this)
        if (listener == null) {
            listener = WakeWordListener(this, { prefs.wakePhraseList() }) { onWake() }
        }
        if (!paused) listener?.start()
        return START_STICKY
    }

    private fun onWake() {
        TaskNotifier.notifyWake(this)
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_WAKE, true)
            )
        } catch (e: Exception) {
            ErrorHandler.log("BroListenerService", "could not open BRO from background", e)
        }
        // If Android blocked opening the screen, start listening again after a few seconds.
        main.postDelayed({ if (!paused) listener?.start() }, 5000)
    }

    override fun onDestroy() {
        listener?.stop()
        listener = null
        instance = null
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 2002
        const val EXTRA_WAKE = "bro_wake"

        @Volatile
        private var instance: BroListenerService? = null

        @Volatile
        var paused = false
            private set

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, BroListenerService::class.java))
            } catch (e: Exception) {
                ErrorHandler.log("BroListenerService", "could not start", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BroListenerService::class.java))
        }

        fun setPaused(value: Boolean) {
            paused = value
            val service = instance ?: return
            service.main.post {
                if (value) service.listener?.stop() else service.listener?.start()
            }
        }
    }
}
