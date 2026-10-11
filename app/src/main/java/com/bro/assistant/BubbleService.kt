package com.bro.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.bro.assistant.memory.PreferencesStore
import kotlin.math.abs
import kotlin.math.sin

/** Lets the view and the service read BRO's current state. */
object BubbleBus {
    @Volatile var state: BroState = BroState.IDLE
    /** True while the circle is in "listen 6 seconds, rest 1 second" mode. */
    @Volatile var listening: Boolean = false
    @Volatile var vm: ChatViewModel? = null
}

/**
 * A round floating light that stays on top of every screen.
 * Idle: slowly cycles through 100 colours (never green, red, purple or orange).
 * Listening: purple. Thinking / working: orange. Task done: green. Task failed: red.
 */
class BubbleService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var phaseListen = false
    private var phaseEnd = 0L
    private var finishedAt = 0L

    private val loop = object : Runnable {
        override fun run() {
            if (!BubbleBus.listening) return
            val vm = BubbleBus.vm
            if (vm == null) { stopListeningMode(); return }
            val now = android.os.SystemClock.elapsedRealtime()
            if (phaseListen) {
                if (vm.state != BroState.LISTENING) { phaseListen = false; phaseEnd = now + REST_MS; finishedAt = 0 }   // ended early or got a result
                else if (now >= phaseEnd) { vm.finishListening(); phaseListen = false; phaseEnd = now + REST_MS; finishedAt = now }
            } else {
                if (finishedAt > 0 && vm.state == BroState.LISTENING && now - finishedAt > 5000) { vm.stopListening(); finishedAt = 0 }
                if (now >= phaseEnd && vm.state == BroState.IDLE && !vm.isBusy) {
                    vm.quietListen = true
                    vm.startListening()
                    phaseListen = true; phaseEnd = now + LISTEN_MS
                }
            }
            main.postDelayed(this, 200)
        }
    }

    fun startListeningMode() {
        if (BubbleBus.listening) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.widget.Toast.makeText(this, "Open BRO once and allow the microphone first.", android.widget.Toast.LENGTH_LONG).show()
            try { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            return
        }
        val vm = BubbleBus.vm
        if (vm == null) {
            // BRO's brain is not loaded yet (fresh start): open BRO for a moment, it comes straight back here.
            try { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_BUBBLE_LISTEN, true)) } catch (_: Exception) {}
            return
        }
        // the microphone may only be used by a foreground service of the microphone type
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(2002, ongoingNote(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) { ErrorHandler.log("BubbleService", "microphone type refused", e) }
        BroListenerService.setPaused(true)   // the wake-phrase listener would fight for the microphone
        BubbleBus.listening = true
        phaseListen = false; phaseEnd = 0; finishedAt = 0
        view?.invalidate()
        main.post(loop)
    }

    fun stopListeningMode() {
        if (!BubbleBus.listening) return
        BubbleBus.listening = false
        main.removeCallbacks(loop)
        BubbleBus.vm?.let { it.quietListen = false; if (it.state == BroState.LISTENING) it.stopListening() }
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(2002, ongoingNote(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (_: Exception) {}
        if (PreferencesStore(this).wakeEnabled) BroListenerService.setPaused(false)
    }

    private var wm: WindowManager? = null
    private var view: BubbleView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        startAsForeground()
        if (view == null) addBubble()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("bro_bubble", "BRO floating circle", NotificationManager.IMPORTANCE_MIN))
        val n = ongoingNote()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(2002, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(2002, n)
        } catch (e: Exception) { ErrorHandler.log("BubbleService", "foreground start failed", e) }
    }

    private fun ongoingNote(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "bro_bubble")
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentTitle("BRO is on")
            .setContentText("Tap the circle to make BRO listen. Turn it off in BRO settings.")
            .setContentIntent(open).setOngoing(true).build()
    }

    private fun addBubble() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val size = (58 * d).toInt()
        val sp = getSharedPreferences("bro_bubble", MODE_PRIVATE)
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = sp.getInt("x", (resources.displayMetrics.widthPixels - size - 12 * d).toInt())
            y = sp.getInt("y", (140 * d).toInt())
        }
        val v = BubbleView(this)
        v.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
            override fun onTouch(t: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(e.rawX - sx) > 12 * d || abs(e.rawY - sy) > 12 * d) moved = true
                        if (moved) { lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt(); try { manager.updateViewLayout(v, lp) } catch (_: Exception) {} }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) sp.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
                        else if (BubbleBus.listening) stopListeningMode() else startListeningMode()
                    }
                }
                return true
            }
        })
        try { manager.addView(v, lp); wm = manager; view = v; v.start() }
        catch (e: Exception) { ErrorHandler.log("BubbleService", "could not show circle", e); stopSelf() }
    }

    override fun onDestroy() {
        stopListeningMode()
        instance = null
        view?.let { it.stop(); try { wm?.removeView(it) } catch (_: Exception) {} }
        view = null
        super.onDestroy()
    }

    private class BubbleView(ctx: Context) : View(ctx) {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var t = 0L
        private var running = false
        private var shown = Color.CYAN
        private val palette = IntArray(100) { i -> Color.HSVToColor(floatArrayOf(hueFor(i), 0.85f, 1f)) }

        private val tick = object : Runnable {
            override fun run() {
                if (!running) return
                t += 40
                invalidate()
                handler.postDelayed(this, 40)
            }
        }

        fun start() { running = true; handler.post(tick) }
        fun stop() { running = false; handler.removeCallbacks(tick) }

        private fun target(): Int = when (BubbleBus.state) {
            BroState.LISTENING -> 0xFF9C27FF.toInt()            // purple
            BroState.THINKING, BroState.EXECUTING -> 0xFFFF9800.toInt() // orange
            BroState.SUCCESS -> 0xFF22DD55.toInt()              // green
            BroState.ERROR -> 0xFFFF2D2D.toInt()                // red
            else -> palette[((t / 600) % 100).toInt()]          // 100 colours, one every 0.6 s
        }

        override fun onDraw(c: Canvas) {
            val goal = target()
            // glide towards the goal colour so changes look smooth
            shown = blend(shown, goal, 0.18f)
            val s = BubbleBus.state
            val speed = if (s == BroState.LISTENING) 3.2 else if (s == BroState.THINKING || s == BroState.EXECUTING) 2.4 else 1.0
            val pulse = (0.5 + 0.5 * sin(t / 1000.0 * speed * 2 * Math.PI)).toFloat()
            val cx = width / 2f; val cy = height / 2f
            val r = width / 2f * (0.62f + 0.12f * pulse)
            paint.shader = RadialGradient(cx, cy, width / 2f, intArrayOf(withAlpha(shown, 255), withAlpha(shown, 150), withAlpha(shown, 0)), floatArrayOf(0.45f, 0.7f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, width / 2f, paint)
            paint.shader = null
            paint.color = shown
            c.drawCircle(cx, cy, r, paint)
            if (BubbleBus.listening) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = width * 0.045f; paint.color = Color.WHITE
                c.drawCircle(cx, cy, width / 2f - paint.strokeWidth, paint)
                paint.style = Paint.Style.FILL
            }
            paint.color = Color.argb(110, 255, 255, 255)
            c.drawCircle(cx - r * 0.28f, cy - r * 0.3f, r * 0.28f, paint)
        }

        private fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
        private fun blend(a: Int, b: Int, f: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
        )
    }

    companion object {
        const val EXTRA_BUBBLE_LISTEN = "bro_bubble_listen"
        private const val LISTEN_MS = 6000L   // microphone on
        private const val REST_MS = 1000L     // microphone off

        @Volatile private var instance: BubbleService? = null

        /** Called by the screen when it was opened only to load BRO, to start the listening rounds. */
        fun beginListening() { instance?.startListeningMode() }

        /** Hue (degrees) of idle colour number [i]: spread over yellow, cyan-blue and pink, skipping red, orange, green, purple. */
        private fun hueFor(i: Int): Float {
            // usable ranges: yellow 48-75, cyan to blue 170-250, pink 305-340  (total 27+80+35 = 142 degrees)
            val p = i / 100f * 142f
            return when {
                p < 27f -> 48f + p
                p < 107f -> 170f + (p - 27f)
                else -> 305f + (p - 107f)
            }
        }

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            try { ContextCompat.startForegroundService(context, Intent(context, BubbleService::class.java)) }
            catch (e: Exception) { ErrorHandler.log("BubbleService", "could not start", e) }
        }

        fun stop(context: Context) { runCatching { context.stopService(Intent(context, BubbleService::class.java)) } }
    }
}
