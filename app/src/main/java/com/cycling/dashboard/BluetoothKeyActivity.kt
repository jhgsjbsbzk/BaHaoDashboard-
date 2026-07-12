package com.cycling.dashboard

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.cycling.dashboard.service.BluetoothKeyService
import com.cycling.dashboard.util.SoundEffectManager

/**
 * 八号蓝牙钥匙入口。
 * 钥匙手机：启动钥匙模式，持续发 BLE 广播。
 * 车机手机：启动车机模式，扫描钥匙，靠近开机，离开锁车。
 * 额外功能：自定义开机/关机音效。
 */
class BluetoothKeyActivity : AppCompatActivity() {

    companion object {
        private const val PICK_BOOT_SOUND = 3001
        private const val PICK_SHUTDOWN_SOUND = 3002
    }

    private lateinit var statusText: TextView
    private lateinit var bootSoundStatus: TextView
    private lateinit var shutdownSoundStatus: TextView

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothKeyService.ACTION_KEY_STATUS) {
                statusText.text = intent.getStringExtra(BluetoothKeyService.EXTRA_STATUS) ?: "运行中"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        requestBluetoothPermissions()
        refreshSoundStatus()
    }

    private fun buildContent(): LinearLayout {
        val scrollRoot = ScrollView(this).apply {
            setBackgroundColor(0xFF080814.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(36, 36, 36, 36)
        }

        // ---- 标题 ----
        val title = TextView(this).apply {
            text = "八号蓝牙钥匙"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        }

        val desc = TextView(this).apply {
            text = "钥匙手机点"钥匙模式"。\n车机手机点"车机模式并打开仪表盘"。\n靠近自动开机，离开自动纯黑锁车。"
            setTextColor(0xFF9AA0B5.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }

        statusText = TextView(this).apply {
            text = "等待启动"
            setTextColor(0xFF00A8FF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }

        val keyButton = makeButton("启动钥匙模式")
        keyButton.setOnClickListener {
            if (!ensurePermissionReady()) return@setOnClickListener
            startBtService(BluetoothKeyService.ACTION_START_KEY)
            Toast.makeText(this, "钥匙模式已启动", Toast.LENGTH_SHORT).show()
        }

        val carButton = makeButton("启动车机模式并打开仪表盘")
        carButton.setOnClickListener {
            if (!ensurePermissionReady()) return@setOnClickListener
            startBtService(BluetoothKeyService.ACTION_START_CAR)
            startActivity(Intent(this, MainActivity::class.java))
        }

        val stopButton = makeButton("停止蓝牙钥匙")
        stopButton.setOnClickListener {
            startBtService(BluetoothKeyService.ACTION_STOP)
            statusText.text = "已停止"
        }

        // ---- 分割线 ----
        val divider = View(this).apply {
            setBackgroundColor(0xFF1A2A40.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, 2).apply {
                setMargins(0, 28, 0, 28)
            }
        }

        // ---- 音效设置区域 ----
        val soundTitle = TextView(this).apply {
            text = "音效设置"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 6)
        }

        val soundDesc = TextView(this).apply {
            text = "自定义开机和关机时的提示音效\n支持 MP3 / WAV 格式"
            setTextColor(0xFF9AA0B5.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        // 开机音效状态
        bootSoundStatus = TextView(this).apply {
            text = ""
            setTextColor(0xFF00A8FF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }

        val bootPickButton = makeButton("选择开机音效")
        bootPickButton.setOnClickListener {
            openFilePicker(PICK_BOOT_SOUND)
        }

        val bootPreviewButton = makeButton("试听开机音效")
        bootPreviewButton.setOnClickListener {
            val file = SoundEffectManager.getBootSoundFile(this)
            if (file != null) {
                SoundEffectManager.previewSound(this, file)
                Toast.makeText(this, "正在播放开机音效", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "未设置开机音效", Toast.LENGTH_SHORT).show()
            }
        }

        val bootDeleteButton = makeButton("删除开机音效")
        bootDeleteButton.setOnClickListener {
            if (SoundEffectManager.deleteBootSound(this)) {
                refreshSoundStatus()
                Toast.makeText(this, "已删除开机音效", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "没有开机音效可删除", Toast.LENGTH_SHORT).show()
            }
        }

        // 关机音效状态
        shutdownSoundStatus = TextView(this).apply {
            text = ""
            setTextColor(0xFF00A8FF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }

        val shutdownPickButton = makeButton("选择关机音效")
        shutdownPickButton.setOnClickListener {
            openFilePicker(PICK_SHUTDOWN_SOUND)
        }

        val shutdownPreviewButton = makeButton("试听关机音效")
        shutdownPreviewButton.setOnClickListener {
            val file = SoundEffectManager.getShutdownSoundFile(this)
            if (file != null) {
                SoundEffectManager.previewSound(this, file)
                Toast.makeText(this, "正在播放关机音效", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "未设置关机音效", Toast.LENGTH_SHORT).show()
            }
        }

        val shutdownDeleteButton = makeButton("删除关机音效")
        shutdownDeleteButton.setOnClickListener {
            if (SoundEffectManager.deleteShutdownSound(this)) {
                refreshSoundStatus()
                Toast.makeText(this, "已删除关机音效", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "没有关机音效可删除", Toast.LENGTH_SHORT).show()
            }
        }

        // ---- 组装 ----
        root.addView(title, lp(-1, -2))
        root.addView(desc, lp(-1, -2))
        root.addView(statusText, lp(-1, -2))
        root.addView(keyButton, buttonParams())
        root.addView(carButton, buttonParams())
        root.addView(stopButton, buttonParams())
        root.addView(divider, lp(-1, 2))
        root.addView(soundTitle, lp(-1, -2))
        root.addView(soundDesc, lp(-1, -2))
        root.addView(bootSoundStatus, lp(-1, -2))
        root.addView(bootPickButton, buttonParams())
        root.addView(bootPreviewButton, smallButtonParams())
        root.addView(bootDeleteButton, smallButtonParams())
        root.addView(shutdownSoundStatus, lp(-1, -2))
        root.addView(shutdownPickButton, buttonParams())
        root.addView(shutdownPreviewButton, smallButtonParams())
        root.addView(shutdownDeleteButton, smallButtonParams())

        scrollRoot.addView(root)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scrollRoot, LinearLayout.LayoutParams(-1, -1))
        }
    }

    private fun refreshSoundStatus() {
        bootSoundStatus.text = if (SoundEffectManager.hasBootSound(this)) "开机音效：已设置" else "开机音效：未设置"
        shutdownSoundStatus.text = if (SoundEffectManager.hasShutdownSound(this)) "关机音效：已设置" else "关机音效：未设置"
    }

    private fun openFilePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/mpeg", "audio/wav", "audio/ogg", "audio/aac", "audio/mp4"))
        }
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: return
            when (requestCode) {
                PICK_BOOT_SOUND -> {
                    if (SoundEffectManager.saveBootSound(this, uri)) {
                        refreshSoundStatus()
                        Toast.makeText(this, "开机音效已保存", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "保存失败，请换一个文件", Toast.LENGTH_SHORT).show()
                    }
                }
                PICK_SHUTDOWN_SOUND -> {
                    if (SoundEffectManager.saveShutdownSound(this, uri)) {
                        refreshSoundStatus()
                        Toast.makeText(this, "关机音效已保存", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "保存失败，请换一个文件", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun makeButton(textValue: String): Button {
        return Button(this).apply {
            text = textValue
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF16263A.toInt())
            isAllCaps = false
        }
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, 64).apply {
            setMargins(0, 8, 0, 8)
        }
    }

    private fun smallButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, 50).apply {
            setMargins(0, 4, 0, 4)
        }
    }

    private fun lp(w: Int, h: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(w, h).apply {
            setMargins(0, 4, 0, 4)
        }
    }

    private fun startBtService(action: String) {
        val intent = Intent(this, BluetoothKeyService::class.java).apply { this.action = action }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            ).forEach {
                if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(it)
                }
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 2201)
        }
    }

    private fun ensurePermissionReady(): Boolean {
        requestBluetoothPermissions()
        return true
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            statusReceiver,
            IntentFilter(BluetoothKeyService.ACTION_KEY_STATUS)
        )
        refreshSoundStatus()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundEffectManager.stopAll()
    }
}
