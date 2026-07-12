package com.cycling.dashboard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.cycling.dashboard.MainActivity
import com.cycling.dashboard.R
import com.cycling.dashboard.gps.SpeedCalculator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * GPS 测速前台服务
 * 持续获取高精度定位，计算实时速度，广播给 UI 层
 */
class GPSSpeedService : Service() {

    companion object {
        const val ACTION_SPEED_UPDATE = "com.cycling.dashboard.SPEED_UPDATE"
        const val EXTRA_SPEED_MS = "speed_ms"
        const val EXTRA_SPEED_KMH = "speed_kmh"
        const val EXTRA_DISTANCE = "distance_m"
        const val EXTRA_SIGNAL_QUALITY = "signal_quality"
        const val EXTRA_IS_NAVIGATING = "is_navigating"

        const val CHANNEL_ID = "gps_speed_channel"
        const val NOTIFICATION_ID = 1001
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val speedCalculator = SpeedCalculator()
    private val binder = LocalBinder()

    private var totalDistanceMeters: Float = 0f
    private var lastLocation: Location? = null
    private var isNavigating: Boolean = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                handleNewLocation(location)
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): GPSSpeedService = this@GPSSpeedService
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isNavigating = intent?.getBooleanExtra(EXTRA_IS_NAVIGATING, false) ?: false
        startForeground(NOTIFICATION_ID, buildNotification("正在获取GPS信号..."))
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // 1秒更新一次
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setWaitForAccurateLocation(true)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun handleNewLocation(location: Location) {
        val (speedMs, signalQuality) = speedCalculator.process(location)
        val speedKmh = speedCalculator.msToKmh(speedMs)

        // 计算行驶距离
        lastLocation?.let { last ->
            val distance = location.distanceTo(last)
            if (distance > 0 && speedMs > 0.5f) {
                totalDistanceMeters += distance
            }
        }
        lastLocation = location

        // 更新通知
        updateNotification(speedKmh, signalQuality)

        // 广播给 UI
        broadcastSpeedUpdate(speedMs, speedKmh, totalDistanceMeters, signalQuality)
    }

    private fun broadcastSpeedUpdate(
        speedMs: Float,
        speedKmh: Float,
        distance: Float,
        signalQuality: SpeedCalculator.SignalQuality
    ) {
        val intent = Intent(ACTION_SPEED_UPDATE).apply {
            putExtra(EXTRA_SPEED_MS, speedMs)
            putExtra(EXTRA_SPEED_KMH, speedKmh)
            putExtra(EXTRA_DISTANCE, distance)
            putExtra(EXTRA_SIGNAL_QUALITY, signalQuality.name)
            putExtra(EXTRA_IS_NAVIGATING, isNavigating)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "骑行仪表盘 GPS 服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持 GPS 定位持续运行，提供实时车速"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("骑行彩屏仪表")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(speedKmh: Float, signalQuality: SpeedCalculator.SignalQuality) {
        val signalText = when (signalQuality) {
            SpeedCalculator.SignalQuality.GOOD -> "GPS信号良好"
            SpeedCalculator.SignalQuality.WEAK -> "GPS信号较弱"
            SpeedCalculator.SignalQuality.LOST -> "GPS信号丢失"
        }
        val content = String.format("时速: %.0f km/h | %s | 里程: %.1f km", 
            speedKmh, signalText, totalDistanceMeters / 1000f)

        val notification = buildNotification(content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun getTotalDistance(): Float = totalDistanceMeters

    fun resetTrip() {
        totalDistanceMeters = 0f
        lastLocation = null
        speedCalculator.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
