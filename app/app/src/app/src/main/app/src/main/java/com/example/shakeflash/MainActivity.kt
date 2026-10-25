package com.example.shakeflash

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var button: Button

    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildScreen()
        } catch (t: Throwable) {
            val tv = TextView(this).apply {
                text = Log.getStackTraceString(t)
                textSize = 12f
                setTextIsSelectable(true)
                setPadding(24, 24, 24, 24)
            }
            setContentView(ScrollView(this).apply { addView(tv) })
        }
    }

    private fun buildScreen() {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }

        val titleView = TextView(this).apply {
            text = "Shake Flashlight"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val hint = TextView(this).apply {
            text = "Turn this on, then shake your phone a few times to toggle the flashlight, even with the screen off."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }
        status = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad)
        }
        button = Button(this).apply {
            setOnClickListener { toggleService() }
        }

        layout.addView(titleView)
        layout.addView(hint)
        layout.addView(status)
        layout.addView(button)
        setContentView(layout)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            Toast.makeText(this, "This device has no flashlight", Toast.LENGTH_LONG).show()
        }

        refreshUi()
    }

    private fun toggleService() {
        try {
            val running = prefs.getBoolean("running", false)
            val intent = Intent(this, ShakeService::class.java)
            if (running) {
                stopService(intent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            prefs.edit().putBoolean("running", !running).apply()
            refreshUi()
        } catch (t: Throwable) {
            Toast.makeText(this, Log.getStackTraceString(t).take(300), Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshUi() {
        val running = prefs.getBoolean("running", false)
        status.text = if (running) "Shake detection: ON" else "Shake detection: OFF"
        button.text = if (running) "Turn off" else "Turn on"
    }
}
