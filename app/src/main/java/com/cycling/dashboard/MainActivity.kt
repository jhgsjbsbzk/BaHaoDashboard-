package com.cycling.dashboard

import android.Manifest
import android.content.res.ColorStateList
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.cycling.dashboard.gps.SpeedCalculator
import com.cycling.dashboard.service.GPSSpeedService
import com.cycling.dashboard.service.BluetoothKeyService
import com.cycling.dashboard.service.MusicControlService
import com.cycling.dashboard.ui.FloatingMusicPanel
import com.cycling.dashboard.util.BrightnessHelper
import com.cycling.dashboard.util.KeepAliveHelper
import com.cycling.dashboard.util.SoundEffectManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主仪表盘 Activity
 * 九号 RideyFUN 风格：深色背景 + 大号数字速度 + 顶部状态栏 + 右侧信息面板
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val OVERLAY_REQUEST_CODE = 1002
    }

    // 状态栏
    private lateinit var tvStatus: TextView
    private lateinit var tvTime: TextView
    private lateinit var ivGpsIcon: ImageView
    private lateinit var ivBtIcon: ImageView

    // 主内容
    private lateinit var tvSpeed: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvGpsLabel: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvRideTime: TextView
    private lateinit var tvPower: TextView
    private lateinit var tvBattery: TextView
    private lateinit var batteryBar: View
    private lateinit var sideLightLeft: View
    private lateinit var sideLightRight: View
    private lateinit var blackLockView: View

    // 按钮
    private lateinit var btnNavigate: ImageButton
    private lateinit var btnMusic: ImageButton
    private lateinit var btnMode: ImageButton
    private lateinit var rootLayout: View

    // 服务
    private var gpsService: GPSSpeedService? = null
    private var musicPanel: FloatingMusicPanel? = null
    private var isNightMode = true // 默认深色模式（匹配九号风格）
    private var isMusicPanelShowing = false

    // 骑行计时
    private var rideStartTime = System.currentTimeMillis()
    private val rideTimerHandler = Handler(Looper.getMainLooper())
    private var currentSpeed = 0f

    private val speedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == GPSSpeedService.ACTION_SPEED_UPDATE) {
                val speedKmh = intent.getFloatExtra(GPSSpeedService.EXTRA_SPEED_KMH, 0f)
                val distance = intent.getFloatExtra(GPSSpeedService.EXTRA_DISTANCE, 0f)
                val signalName = intent.getStringExtra(GPSSpeedService.EXTRA_SIGNAL_QUALITY)
                    ?: SpeedCalculator.SignalQuality.GOOD.name
                val signalQuality = SpeedCalculator.SignalQuality.valueOf(signalName)

                updateDashboard(speedKmh, distance, signalQuality)
            }
        }
    }

    private val gpsConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as GPSSpeedService.LocalBinder
            gpsService = binder.getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            gpsService = null
        }
    }

    private val bluetoothKeyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothKeyService.ACTION_KEY_NEAR -> unlockByBluetoothKey()
                BluetoothKeyService.ACTION_KEY_FAR -> lockByBluetoothKey()
            }
        }
    }

    // 每秒更新骑行时间和时钟
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateClock()
            updateRideTime()
            rideTimerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupWindow()
        checkPermissions()
        startServices()
        KeepAliveHelper.requestIgnoreBatteryOptimizations(this)

        // 启动时钟和骑行计时
        rideTimerHandler.post(timerRunnable)
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.rootLayout)

        // 状态栏
        tvStatus = findViewById(R.id.tvStatus)
        tvTime = findViewById(R.id.tvTime)
        ivGpsIcon = findViewById(R.id.ivGpsIcon)
        ivBtIcon = findViewById(R.id.ivBtIcon)

        // 主内容
        tvSpeed = findViewById(R.id.tvSpeed)
        tvMode = findViewById(R.id.tvMode)
        tvGpsLabel = findViewById(R.id.tvGpsLabel)
        tvDistance = findViewById(R.id.tvDistance)
        tvRideTime = findViewById(R.id.tvRideTime)
        tvPower = findViewById(R.id.tvPower)
        tvBattery = findViewById(R.id.tvBattery)
        batteryBar = findViewById(R.id.batteryBar)
        sideLightLeft = findViewById(R.id.sideLightLeft)
        sideLightRight = findViewById(R.id.sideLightRight)

        // 按钮
        btnNavigate = findViewById(R.id.btnNavigate)
        btnMusic = findViewById(R.id.btnMusic)
        btnMode = findViewById(R.id.btnMode)
        addBlackLockView()

        btnNavigate.setOnClickListener {
            startActivity(Intent(this, NavigationActivity::class.java))
        }
        btnMusic.setOnClickListener {
            if (!isMusicAccessEnabled()) {
                Toast.makeText(this, "请先开启“八号音乐控制”的通知使用权", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                return@setOnClickListener
            }
            toggleMusicPanel()
        }
        btnMode.setOnClickListener {
            toggleDayNightMode()
        }
    }

    private fun setupWindow() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        KeepAliveHelper.keepScreenOn(this)
        BrightnessHelper.boostBrightness(this)
        applyThemeColors()
    }

    private fun addBlackLockView() {
        val decor = window.decorView as ViewGroup
        blackLockView = View(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            isClickable = true
            isFocusable = true
        }
        decor.addView(
            blackLockView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun lockByBluetoothKey() {
        blackLockView.visibility = View.VISIBLE
        tvSpeed.text = "0"
        tvPower.text = "0W"
        SoundEffectManager.playShutdownSound(this)
    }

    private fun unlockByBluetoothKey() {
        blackLockView.visibility = View.GONE
        setupWindow()
        SoundEffectManager.playBootSound(this)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }

        if (!KeepAliveHelper.canDrawOverlays(this)) {
            KeepAliveHelper.requestOverlayPermission(this)
        }

        if (!isMusicAccessEnabled()) {
            Toast.makeText(this, "想控制手机正在播放的音乐，请开启“八号音乐控制”的通知使用权", Toast.LENGTH_LONG).show()
        }
    }

    private fun isMusicAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(packageName)
    }

    private fun startServices() {
        // 启动 GPS 服务
        Intent(this, GPSSpeedService::class.java).also { intent ->
            bindService(intent, gpsConnection, Context.BIND_AUTO_CREATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        // 启动音乐控制服务
        Intent(this, MusicControlService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    /**
     * 更新仪表盘数据（由 GPS 广播驱动）
     */
    private fun updateDashboard(speedKmh: Float, distance: Float, signalQuality: SpeedCalculator.SignalQuality) {
        currentSpeed = speedKmh

        // 速度数字 - 格式化为整数
        tvSpeed.text = String.format("%d", speedKmh.toInt())

        // 里程
        val distKm = distance / 1000f
        tvDistance.text = String.format("%.1f", distKm)

        // GPS 信号
        when (signalQuality) {
            SpeedCalculator.SignalQuality.GOOD -> {
                tvGpsLabel.text = "GPS 良好"
                tvGpsLabel.setTextColor(getColor(R.color.status_ready))
                ivGpsIcon.setImageResource(R.drawable.ic_gps_small)
                tvStatus.text = "READY"
                tvStatus.setTextColor(getColor(R.color.status_ready))
            }
            SpeedCalculator.SignalQuality.WEAK -> {
                tvGpsLabel.text = "GPS 较弱"
                tvGpsLabel.setTextColor(getColor(R.color.status_warning))
                tvStatus.text = "SEARCHING"
                tvStatus.setTextColor(getColor(R.color.status_warning))
            }
            SpeedCalculator.SignalQuality.LOST -> {
                tvGpsLabel.text = "GPS 无信号"
                tvGpsLabel.setTextColor(getColor(R.color.status_danger))
                tvStatus.text = "NO GPS"
                tvStatus.setTextColor(getColor(R.color.status_danger))
            }
        }

        // 估算功率（简易模型：与速度成正比）
        val power = (speedKmh * 8).toInt() // 粗略估算
        tvPower.text = "${power}W"

        // 两侧 LED 氛围灯随速度变色：
        // 停车/低速蓝色，30 变黄色，40 及以上变红色
        updateSpeedSideLight(speedKmh)
    }

    /**
     * 根据实时车速更新左右两侧 LED 氛围灯。
     * 0-29 km/h：蓝色；30-39 km/h：黄色；40 km/h 及以上：红色。
     */
    private fun updateSpeedSideLight(speedKmh: Float) {
        val color = when {
            speedKmh < 30f -> getColor(R.color.side_light_stop_blue)
            speedKmh < 40f -> getColor(R.color.side_light_flat_yellow)
            else -> getColor(R.color.side_light_fast_red)
        }
        updateRoadSideLight(color)
    }

    private fun updateRoadSideLight(color: Int) {
        val tint = ColorStateList.valueOf(color)
        sideLightLeft.backgroundTintList = tint
        sideLightRight.backgroundTintList = tint
    }

    private fun updateClock() {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvTime.text = sdf.format(Date())
    }

    private fun updateRideTime() {
        val elapsed = System.currentTimeMillis() - rideStartTime
        val totalSeconds = (elapsed / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        tvRideTime.text = if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun toggleMusicPanel() {
        if (!KeepAliveHelper.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            KeepAliveHelper.requestOverlayPermission(this)
            return
        }

        if (isMusicPanelShowing) {
            musicPanel?.hide()
            musicPanel = null
            isMusicPanelShowing = false
        } else {
            musicPanel = FloatingMusicPanel(this).apply { show() }
            isMusicPanelShowing = true
        }
    }

    private fun toggleDayNightMode() {
        isNightMode = !isNightMode
        applyThemeColors()
    }

    private fun applyThemeColors() {
        if (isNightMode) {
            // 夜间模式 - 默认深色
            rootLayout.setBackgroundColor(getColor(R.color.bg_dashboard))
            tvSpeed.setTextColor(getColor(R.color.speed_number))
            tvStatus.setTextColor(getColor(R.color.status_ready))
            tvGpsLabel.setTextColor(getColor(R.color.status_ready))
            tvDistance.setTextColor(getColor(R.color.text_primary))
            tvRideTime.setTextColor(getColor(R.color.text_primary))
            tvPower.setTextColor(getColor(R.color.text_primary))
            tvBattery.setTextColor(getColor(R.color.text_primary))
            tvTime.setTextColor(getColor(R.color.text_secondary))
            tvMode.setTextColor(getColor(R.color.accent_cyan))
            updateSpeedSideLight(currentSpeed)
        } else {
            // 日间模式
            rootLayout.setBackgroundColor(getColor(R.color.bg_dashboard_day))
            tvSpeed.setTextColor(getColor(R.color.speed_number_day))
            tvStatus.setTextColor(getColor(R.color.status_ready))
            tvGpsLabel.setTextColor(getColor(R.color.status_ready))
            tvDistance.setTextColor(getColor(R.color.text_primary_day))
            tvRideTime.setTextColor(getColor(R.color.text_primary_day))
            tvPower.setTextColor(getColor(R.color.text_primary_day))
            tvBattery.setTextColor(getColor(R.color.text_primary_day))
            tvTime.setTextColor(getColor(R.color.text_secondary_day))
            tvMode.setTextColor(getColor(R.color.accent_blue))
            updateSpeedSideLight(currentSpeed)
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            speedReceiver,
            IntentFilter(GPSSpeedService.ACTION_SPEED_UPDATE)
        )
        val keyFilter = IntentFilter().apply {
            addAction(BluetoothKeyService.ACTION_KEY_NEAR)
            addAction(BluetoothKeyService.ACTION_KEY_FAR)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(bluetoothKeyReceiver, keyFilter)
        setupWindow()
        BrightnessHelper.boostBrightness(this)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(speedReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(bluetoothKeyReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        rideTimerHandler.removeCallbacks(timerRunnable)
        try { unbindService(gpsConnection) } catch (_: Exception) { }
        musicPanel?.hide()
        BrightnessHelper.restoreBrightness(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // GPS 权限已授予
            } else {
                Toast.makeText(this, "缺少定位权限，无法测速", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQUEST_CODE) {
            if (KeepAliveHelper.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupWindow()
    }
}
