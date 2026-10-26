package com.example.volball

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, fill: Int, click: () -> Unit) = Button(this).apply {
        text = label; setTextColor(Color.WHITE); isAllCaps = false; textSize = 16f
        background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(fill) }
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(dp(260), dp(54)).apply { topMargin = dp(14) }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101018"))
        }
        val ball = TextView(this).apply {
            text = "\uD83D\uDD0A"; textSize = 34f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(160, 255, 45, 70)); setStroke(dp(2), Color.argb(200, 255, 255, 255))
            }
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
        }
        val title = TextView(this).apply {
            text = "Volume Ball"; textSize = 28f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, dp(20), 0, dp(4))
        }
        status = TextView(this).apply { textSize = 14f; setTextColor(Color.parseColor("#9AA0B4")); gravity = Gravity.CENTER }
        root.addView(ball); root.addView(title); root.addView(status)
        root.addView(btn("Start floating ball", Color.parseColor("#E8283F")) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startForegroundService(Intent(this, FloatingService::class.java)); finish()
            }
        })
        root.addView(btn("Stop", Color.parseColor("#2A2A3A")) { stopService(Intent(this, FloatingService::class.java)) })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (Settings.canDrawOverlays(this)) "Permission granted" else "Allow \"Display over other apps\" to begin"
    }
}
