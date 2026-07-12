package com.cycling.dashboard.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.cycling.dashboard.R
import com.cycling.dashboard.service.MusicControlService

/**
 * 全局悬浮蓝牙音乐控制面板
 * 可折叠、可拖拽、跨页面保持显示
 */
class FloatingMusicPanel(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatView: View? = null
    private var collapsedView: View? = null
    private var expandedView: View? = null

    private var isExpanded = true
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var params: WindowManager.LayoutParams

    private var tvTitle: TextView? = null
    private var tvArtist: TextView? = null
    private var progressBar: ProgressBar? = null
    private var tvProgress: TextView? = null
    private var btnPlayPause: ImageButton? = null
    private var btnPrev: ImageButton? = null
    private var btnNext: ImageButton? = null
    private var btnVolUp: ImageButton? = null
    private var btnVolDown: ImageButton? = null
    private var btnCollapse: ImageButton? = null
    private var ivBtStatus: View? = null

    private val musicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MusicControlService.ACTION_MUSIC_UPDATE) {
                updateUI(
                    title = intent.getStringExtra(MusicControlService.EXTRA_TITLE) ?: "未知歌曲",
                    artist = intent.getStringExtra(MusicControlService.EXTRA_ARTIST) ?: "未知艺术家",
                    duration = intent.getLongExtra(MusicControlService.EXTRA_DURATION, 0L),
                    position = intent.getLongExtra(MusicControlService.EXTRA_POSITION, 0L),
                    isPlaying = intent.getBooleanExtra(MusicControlService.EXTRA_IS_PLAYING, false),
                    btConnected = intent.getBooleanExtra(MusicControlService.EXTRA_BT_CONNECTED, false)
                )
            }
        }
    }

    init {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - 400
            y = 40
        }
    }

    fun show() {
        if (floatView != null) return

        floatView = LayoutInflater.from(context).inflate(R.layout.floating_music_panel, null)
        setupViews(floatView!!)
        setupTouchListener(floatView!!)

        windowManager.addView(floatView, params)

        LocalBroadcastManager.getInstance(context).registerReceiver(
            musicReceiver,
            IntentFilter(MusicControlService.ACTION_MUSIC_UPDATE)
        )

        // 请求一次当前状态
        context.sendBroadcast(Intent(MusicControlService.ACTION_MUSIC_CONTROL))
    }

    fun hide() {
        floatView?.let {
            windowManager.removeView(it)
            floatView = null
        }
        try {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(musicReceiver)
        } catch (_: Exception) { }
    }

    private fun setupViews(view: View) {
        collapsedView = view.findViewById(R.id.collapsedLayout)
        expandedView = view.findViewById(R.id.expandedLayout)

        tvTitle = view.findViewById(R.id.tvMusicTitle)
        tvArtist = view.findViewById(R.id.tvMusicArtist)
        progressBar = view.findViewById(R.id.progressMusic)
        tvProgress = view.findViewById(R.id.tvProgress)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)
        btnVolUp = view.findViewById(R.id.btnVolUp)
        btnVolDown = view.findViewById(R.id.btnVolDown)
        btnCollapse = view.findViewById(R.id.btnCollapse)
        ivBtStatus = view.findViewById(R.id.ivBtStatus)

        btnPlayPause?.setOnClickListener {
            sendControl(MusicControlService.CMD_PLAY_PAUSE)
        }
        btnPrev?.setOnClickListener {
            sendControl(MusicControlService.CMD_PREV)
        }
        btnNext?.setOnClickListener {
            sendControl(MusicControlService.CMD_NEXT)
        }
        btnVolUp?.setOnClickListener {
            sendControl(MusicControlService.CMD_VOL_UP)
        }
        btnVolDown?.setOnClickListener {
            sendControl(MusicControlService.CMD_VOL_DOWN)
        }
        btnCollapse?.setOnClickListener {
            toggleCollapse()
        }
        collapsedView?.setOnClickListener {
            toggleCollapse()
        }
    }

    private fun setupTouchListener(view: View) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false // 让点击事件也能传递
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleCollapse() {
        isExpanded = !isExpanded
        expandedView?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        collapsedView?.visibility = if (isExpanded) View.GONE else View.VISIBLE
        btnCollapse?.setImageResource(
            if (isExpanded) R.drawable.ic_collapse else R.drawable.ic_expand
        )
    }

    private fun sendControl(cmd: String) {
        val intent = Intent(MusicControlService.ACTION_MUSIC_CONTROL).apply {
            putExtra(MusicControlService.EXTRA_CONTROL_CMD, cmd)
        }
        context.sendBroadcast(intent)
    }

    private fun updateUI(
        title: String,
        artist: String,
        duration: Long,
        position: Long,
        isPlaying: Boolean,
        btConnected: Boolean
    ) {
        tvTitle?.text = title
        tvArtist?.text = artist
        progressBar?.max = (duration / 1000).toInt().coerceAtLeast(1)
        progressBar?.progress = (position / 1000).toInt()

        val posStr = formatTime(position)
        val durStr = formatTime(duration)
        tvProgress?.text = "$posStr / $durStr"

        btnPlayPause?.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        ivBtStatus?.visibility = if (btConnected) View.VISIBLE else View.GONE
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
