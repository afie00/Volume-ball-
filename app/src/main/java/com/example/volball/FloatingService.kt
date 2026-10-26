package com.example.volball

import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.*
import android.view.*
import android.widget.*

class FloatingService : Service() {
    companion object { @Volatile var running = false }
    private lateinit var wm: WindowManager
    private lateinit var am: AudioManager
    private lateinit var ball: TextView
    private lateinit var panel: LinearLayout
    private lateinit var seek: SeekBar
    private lateinit var pct: TextView
    private lateinit var mute: TextView
    private lateinit var ballLp: WindowManager.LayoutParams
    private lateinit var panelLp: WindowManager.LayoutParams
    private val h = Handler(Looper.getMainLooper())
    private var open = false
    private var lastVol = 5
    private val autoClose = Runnable { closePanel() }
    private val sync = object : Runnable {
        override fun run() { if (open) { refresh(); h.postDelayed(this, 500) } }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private var stream = AudioManager.STREAM_MUSIC
    private val names = listOf("Media" to AudioManager.STREAM_MUSIC, "Ring" to AudioManager.STREAM_RING, "Alarm" to AudioManager.STREAM_ALARM)
    private var nameIdx = 0
    private fun type() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private fun lp(w: Int, hh: Int) = WindowManager.LayoutParams(
        w, hh, type(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        startForeground(1, notification())
        buildBall(); buildPanel()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "STOP") stopSelf()
        return START_STICKY
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("vb", "Volume Ball", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, FloatingService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "vb")
            .setContentTitle("Volume Ball is active")
            .setContentText("Tap Stop to remove the floating ball")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun buildBall() {
        val size = dp(56)
        ball = TextView(this).apply {
            text = "\uD83D\uDD0A"; textSize = 22f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(150, 255, 45, 70))
                setStroke(dp(2), Color.argb(200, 255, 255, 255))
            }
        }
        ballLp = lp(size, size).apply { x = resources.displayMetrics.widthPixels - size - dp(8); y = dp(220) }
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
        ball.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX; dy = e.rawY; sx = ballLp.x; sy = ballLp.y; moved = false
                    ball.animate().alpha(1f).scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (e.rawX - dx).toInt(); val my = (e.rawY - dy).toInt()
                    if (Math.abs(mx) > dp(6) || Math.abs(my) > dp(6)) moved = true
                    if (moved) { ballLp.x = sx + mx; ballLp.y = sy + my; wm.updateViewLayout(ball, ballLp); if (open) closePanel() }
                }
                MotionEvent.ACTION_UP -> {
                    ball.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (moved) snap() else { v.performClick(); v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); toggle() }
                    dimLater()
                }
            }
            true
        }
        wm.addView(ball, ballLp)
        dimLater()
    }

    private fun snap() {
        val w = resources.displayMetrics.widthPixels
        val target = if (ballLp.x + dp(28) < w / 2) dp(8) else w - dp(56) - dp(8)
        ValueAnimator.ofInt(ballLp.x, target).apply {
            duration = 220
            addUpdateListener { ballLp.x = it.animatedValue as Int; wm.updateViewLayout(ball, ballLp) }
        }.start()
    }

    private fun dimLater() {
        ball.animate().cancel()
        ball.postDelayed({ if (!open) ball.animate().alpha(0.6f).setDuration(500).start() }, 2500)
    }

    private fun roundBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(200, 232, 40, 63)) }
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick(); resetClose() }
    }

    private fun step(dir: Int) {
        try { am.adjustStreamVolume(stream, dir, AudioManager.FLAG_SHOW_UI) } catch (_: Exception) {}
        h.postDelayed({ refresh() }, 80)
    }

    private fun setVol(v: Int) { try { am.setStreamVolume(stream, v, 0) } catch (_: Exception) {} }

    private fun buildPanel() {
        val w = resources.displayMetrics.widthPixels - dp(24)
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(32).toFloat()
                setColor(Color.argb(235, 18, 18, 26)); setStroke(dp(1), Color.argb(130, 255, 59, 80))
            }
        }
        mute = TextView(this).apply {
            textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.argb(90, 255, 255, 255)) }
            setOnClickListener {
                nameIdx = (nameIdx + 1) % names.size; stream = names[nameIdx].second
                seek.max = am.getStreamMaxVolume(stream); refresh(); resetClose()
            }
        }
        seek = SeekBar(this).apply {
            max = am.getStreamMaxVolume(stream)
            val red = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF3B50"))
            progressTintList = red; thumbTintList = red
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) {
                    if (user) { setVol(p); s.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                    updateLabels(p)
                }
                override fun onStartTrackingTouch(s: SeekBar) { h.removeCallbacks(autoClose) }
                override fun onStopTrackingTouch(s: SeekBar) { resetClose() }
            })
        }
        pct = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.END }
        val minus = roundBtn("\u2212") { step(AudioManager.ADJUST_LOWER) }
        val plus = roundBtn("+") { step(AudioManager.ADJUST_RAISE) }
        panel.addView(mute, LinearLayout.LayoutParams(dp(54), dp(32)))
        panel.addView(minus, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(8) })
        panel.addView(seek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6); marginEnd = dp(6) })
        panel.addView(plus, LinearLayout.LayoutParams(dp(40), dp(40)))
        panel.addView(pct, LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT))
        panelLp = lp(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply { x = dp(12) }
    }

    private fun updateLabels(p: Int) {
        pct.text = "${p * 100 / maxOf(seek.max, 1)}%"
        mute.text = names[nameIdx].first
    }

    private fun refresh() {
        val v = am.getStreamVolume(stream)
        if (seek.progress != v) seek.progress = v
        updateLabels(v)
    }

    private fun toggle() = if (open) closePanel() else openPanel()

    private fun openPanel() {
        val screenH = resources.displayMetrics.heightPixels
        val below = ballLp.y + dp(56) + dp(10)
        panelLp.y = if (below + dp(80) > screenH) ballLp.y - dp(80) else below
        refresh()
        wm.addView(panel, panelLp)
        panel.alpha = 0f; panel.translationY = -dp(12).toFloat()
        panel.animate().alpha(1f).translationY(0f).setDuration(180).start()
        open = true
        ball.animate().alpha(1f).start()
        resetClose(); h.post(sync)
    }

    private fun closePanel() {
        if (!open) return
        open = false
        h.removeCallbacks(autoClose); h.removeCallbacks(sync)
        try { wm.removeView(panel) } catch (_: Exception) {}
        dimLater()
    }

    private fun resetClose() { h.removeCallbacks(autoClose); h.postDelayed(autoClose, 3500) }

    override fun onDestroy() {
        running = false
        closePanel()
        try { wm.removeView(ball) } catch (_: Exception) {}
        super.onDestroy()
    }
}
