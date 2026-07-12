package com.cycling.dashboard.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.cycling.dashboard.R
import java.util.UUID

/**
 * 八号蓝牙钥匙服务。
 * 钥匙模式：手机持续发 BLE 广播。
 * 车机模式：车机手机扫描钥匙广播，根据 RSSI 判断靠近/离开。
 */
class BluetoothKeyService : Service() {

    companion object {
        const val ACTION_START_KEY = "com.cycling.dashboard.bluetooth.START_KEY"
        const val ACTION_START_CAR = "com.cycling.dashboard.bluetooth.START_CAR"
        const val ACTION_STOP = "com.cycling.dashboard.bluetooth.STOP"
        const val ACTION_KEY_NEAR = "com.cycling.dashboard.bluetooth.KEY_NEAR"
        const val ACTION_KEY_FAR = "com.cycling.dashboard.bluetooth.KEY_FAR"
        const val ACTION_KEY_STATUS = "com.cycling.dashboard.bluetooth.KEY_STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_RSSI = "rssi"

        private const val CHANNEL_ID = "bahao_bluetooth_key"
        private const val NOTIFICATION_ID = 8801
        private const val NEAR_RSSI = -68
        private const val FAR_TIMEOUT_MS = 7000L
        private val SERVICE_UUID: UUID = UUID.fromString("8a7a0001-4b8f-4d7e-9a4d-8a7a8a7a0001")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var adapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var lastSeenTime = 0L
    private var isNear = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            broadcastStatus("钥匙模式已启动")
        }

        override fun onStartFailure(errorCode: Int) {
            broadcastStatus("钥匙广播失败：$errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScan(result.rssi)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScan(it.rssi) }
        }

        override fun onScanFailed(errorCode: Int) {
            broadcastStatus("扫描失败：$errorCode")
        }
    }

    private val farChecker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (isNear && now - lastSeenTime > FAR_TIMEOUT_MS) {
                isNear = false
                LocalBroadcastManager.getInstance(this@BluetoothKeyService)
                    .sendBroadcast(Intent(ACTION_KEY_FAR))
                broadcastStatus("钥匙离开，车机锁车")
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager.adapter
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("八号蓝牙钥匙运行中"))
        when (intent?.action) {
            ACTION_START_KEY -> startKeyMode()
            ACTION_START_CAR -> startCarMode()
            ACTION_STOP -> {
                stopAll()
                stopSelf()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startKeyMode() {
        stopAll()
        if (!hasBluetoothPermission()) {
            broadcastStatus("缺少蓝牙权限")
            return
        }
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            broadcastStatus("请先打开蓝牙")
            return
        }
        advertiser = bt.bluetoothLeAdvertiser
        if (advertiser == null) {
            broadcastStatus("本手机不支持 BLE 广播")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        advertiser?.startAdvertising(settings, data, advertiseCallback)
        broadcastStatus("钥匙模式启动中")
    }

    @SuppressLint("MissingPermission")
    private fun startCarMode() {
        stopAll()
        if (!hasBluetoothPermission()) {
            broadcastStatus("缺少蓝牙权限")
            return
        }
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            broadcastStatus("请先打开蓝牙")
            return
        }
        scanner = bt.bluetoothLeScanner
        if (scanner == null) {
            broadcastStatus("本手机不支持 BLE 扫描")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
        handler.post(farChecker)
        broadcastStatus("车机模式已启动，正在寻找钥匙")
    }

    private fun handleScan(rssi: Int) {
        broadcastStatus("钥匙信号 RSSI $rssi", rssi)
        if (rssi >= NEAR_RSSI) {
            lastSeenTime = System.currentTimeMillis()
            if (!isNear) {
                isNear = true
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(ACTION_KEY_NEAR))
                broadcastStatus("钥匙靠近，车机开机", rssi)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAll() {
        try { advertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        advertiser = null
        scanner = null
        isNear = false
        handler.removeCallbacks(farChecker)
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun broadcastStatus(text: String, rssi: Int = 0) {
        val intent = Intent(ACTION_KEY_STATUS)
            .putExtra(EXTRA_STATUS, text)
            .putExtra(EXTRA_RSSI, rssi)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "八号蓝牙钥匙",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "用于钥匙靠近开机、离开关机"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun notification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("八号蓝牙钥匙")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }
}
