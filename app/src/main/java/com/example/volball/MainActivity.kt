package com.example.volball

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var am: AudioManager
    private lateinit var ballBtn: Button
    private lateinit var status: TextView
    private val h = Handler(Looper.getMainLooper())
    private val rows = mutableListOf<Triple<Int, SeekBar, TextView>>()
    private var active = false
    private val red = Color.parseColor("#E8283F")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(c: Int, r: Int) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(c) }

    private fun btn(label: String, fill: Int, w: Int = ViewGroup.LayoutParams.MATCH_PARENT, click: () -> Unit) = Button(this).apply {
        text = label; setTextColor(Color.WHITE); isAllCaps = false; textSize = 15f
        background = bg(fill, 26); setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(w, dp(50)).apply { topMargin = dp(10) }
    }

    private fun round(label: String, click: () -> Unit) = TextView(this).apply {
        text = label; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(red) }
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
    }

    private fun setVol(stream: Int, v: Int) { try { am.setStreamVolume(stream, v, 0) } catch (_: Exception) {} }
    private fun step(stream: Int, dir: Int) { try { am.adjustStreamVolume(stream, dir, 0) } catch (_: Exception) {}; refresh() }

    private fun addRow(parent: LinearLayout, title: String, stream: Int) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg(Color.parseColor("#1A1A26"), 18)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        card.addView(TextView(this).apply { text = title; setTextColor(Color.parseColor("#9AA0B4")); textSize = 13f })
        val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val seek = SeekBar(this).apply {
            max = am.getStreamMaxVolume(stream)
            val t = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF3B50"))
            progressTintList = t; thumbTintList = t
        }
        val pct = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.END }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) {
                if (user) setVol(stream, p)
                pct.text = "${p * 100 / maxOf(s.max, 1)}%"
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        line.addView(round("\u2212") { step(stream, AudioManager.ADJUST_LOWER) })
        line.addView(seek, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6); marginEnd = dp(6) })
        line.addView(round("+") { step(stream, AudioManager.ADJUST_RAISE) })
        line.addView(pct, LinearLayout.LayoutParams(dp(46), -2))
        card.addView(line)
        parent.addView(card)
        rows.add(Triple(stream, seek, pct))
    }

    private fun preset(label: String, frac: Float) = btn(label, Color.parseColor("#2A2A3A"), 0) {
        val s = AudioManager.STREAM_MUSIC
        setVol(s, Math.round(am.getStreamMaxVolume(s) * frac)); refresh()
    }.apply { (layoutParams as LinearLayout.LayoutParams).apply { weight = 1f; marginEnd = dp(6) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(28)) }
        col.addView(TextView(this).apply { text = "Volume Ball"; textSize = 28f; setTextColor(Color.WHITE) })
        status = TextView(this).apply { textSize = 13f; setTextColor(Color.parseColor("#9AA0B4")); setPadding(0, dp(2), 0, dp(6)) }
        col.addView(status)
        addRow(col, "Media (music, videos)", AudioManager.STREAM_MUSIC)
        addRow(col, "Ringtone", AudioManager.STREAM_RING)
        addRow(col, "Alarm", AudioManager.STREAM_ALARM)
        addRow(col, "Call", AudioManager.STREAM_VOICE_CALL)
        col.addView(TextView(this).apply { text = "Media presets"; textSize = 13f; setTextColor(Color.parseColor("#9AA0B4")); setPadding(0, dp(16), 0, 0) })
        val pr = LinearLayout(this)
        pr.addView(preset("Mute", 0f)); pr.addView(preset("30%", 0.3f)); pr.addView(preset("60%", 0.6f)); pr.addView(preset("Max", 1f))
        col.addView(pr)
        ballBtn = btn("", red) { toggleBall() }
        (ballBtn.layoutParams as LinearLayout.LayoutParams).topMargin = dp(22)
        col.addView(ballBtn)
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#101018")); addView(col) })
    }

    private fun toggleBall() {
        if (FloatingService.running) {
            stopService(Intent(this, FloatingService::class.java))
        } else if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            startForegroundService(Intent(this, FloatingService::class.java))
        }
        h.postDelayed({ refresh() }, 400)
    }

    private fun refresh() {
        for ((stream, seek, pct) in rows) {
            val v = am.getStreamVolume(stream)
            if (seek.progress != v) seek.progress = v
            pct.text = "${v * 100 / maxOf(seek.max, 1)}%"
        }
        val on = FloatingService.running
        ballBtn.text = if (on) "Hide floating ball" else "Show floating ball"
        ballBtn.background = bg(if (on) Color.parseColor("#2A2A3A") else red, 26)
        status.text = if (!Settings.canDrawOverlays(this)) "Tap the button and allow \"Display over other apps\""
        else if (on) "Ball is on. Close this app any time." else "Ball is off. Set your volume below."
    }

    private val poll = object : Runnable { override fun run() { if (active) { refresh(); h.postDelayed(this, 700) } } }
    override fun onResume() { super.onResume(); active = true; h.post(poll) }
    override fun onPause() { active = false; super.onPause() }
}
