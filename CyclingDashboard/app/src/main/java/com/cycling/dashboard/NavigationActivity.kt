package com.cycling.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.cycling.dashboard.gps.SpeedCalculator
import com.cycling.dashboard.service.GPSSpeedService
import com.cycling.dashboard.ui.MiniSpeedView
import com.cycling.dashboard.util.BrightnessHelper
import com.cycling.dashboard.util.KeepAliveHelper

/**
 * 导航 Activity（简化版）
 * 暂无高德地图集成，显示提示信息 + 角落小车速
 */
class NavigationActivity : AppCompatActivity() {

    private lateinit var miniSpeedView: MiniSpeedView
    private lateinit var tvHint: TextView
    private lateinit var btnBack: Button

    private val speedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == GPSSpeedService.ACTION_SPEED_UPDATE) {
                val speedKmh = intent.getFloatExtra(GPSSpeedService.EXTRA_SPEED_KMH, 0f)
                val signalName = intent.getStringExtra(GPSSpeedService.EXTRA_SIGNAL_QUALITY)
                    ?: SpeedCalculator.SignalQuality.GOOD.name
                val signalQuality = SpeedCalculator.SignalQuality.valueOf(signalName)
                miniSpeedView.setSpeed(speedKmh)
                miniSpeedView.setSignalQuality(signalQuality)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)

        miniSpeedView = findViewById(R.id.miniSpeedView)
        tvHint = findViewById(R.id.tvNavHint)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        setupWindow()
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
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            speedReceiver,
            IntentFilter(GPSSpeedService.ACTION_SPEED_UPDATE)
        )
        setupWindow()
        BrightnessHelper.boostBrightness(this)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(speedReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        BrightnessHelper.restoreBrightness(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupWindow()
    }
}
