package com.example.shakeflash

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import kotlin.math.sqrt

class ShakeService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var torchOn = false
    private var wakeLock: PowerManager.WakeLock? = null

    private var shakeCount = 0
    private var firstShakeTime = 0L
    private var lastShakeTime = 0L
    private var lastToggleTime = 0L

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) torchOn = enabled
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val c = cameraManager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        cameraManager.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))

        startInForeground()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shakeflash:service").apply {
            setReferenceCounted(false)
            acquire()
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun startInForeground() {
        val channelId = "shake_flashlight"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Shake Flashlight", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId) else Notification.Builder(this)
        val notification = builder
            .setContentTitle("Shake Flashlight is on")
            .setContentText("Shake your phone to toggle the flashlight")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val gx = event.values[0] / SensorManager.GRAVITY_EARTH
        val gy = event.values[1] / SensorManager.GRAVITY_EARTH
        val gz = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gx * gx + gy * gy + gz * gz)

        if (gForce < SHAKE_THRESHOLD_G) return

        val now = System.currentTimeMillis()
        if (now - lastShakeTime < MIN_TIME_BETWEEN_SHAKES_MS) return
        lastShakeTime = now

        if (shakeCount == 0 || now - firstShakeTime > SHAKE_WINDOW_MS) {
            shakeCount = 0
            firstShakeTime = now
        }
        shakeCount++

        if (shakeCount >= SHAKES_REQUIRED && now - lastToggleTime > COOLDOWN_MS) {
            shakeCount = 0
            lastToggleTime = now
            toggleTorch()
        }
    }

    private fun toggleTorch() {
        val id = cameraId ?: return
        try {
            cameraManager.setTorchMode(id, !torchOn)
        } catch (_: Exception) {
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        cameraManager.unregisterTorchCallback(torchCallback)
        try {
            cameraId?.let { if (torchOn) cameraManager.setTorchMode(it, false) }
        } catch (_: Exception) {
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val SHAKE_THRESHOLD_G = 2.7f
        private const val SHAKES_REQUIRED = 3
        private const val SHAKE_WINDOW_MS = 1000L
        private const val MIN_TIME_BETWEEN_SHAKES_MS = 120L
        private const val COOLDOWN_MS = 1200L
    }
}
