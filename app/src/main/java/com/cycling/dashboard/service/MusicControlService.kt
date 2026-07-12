package com.cycling.dashboard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.cycling.dashboard.R

/**
 * 音乐控制服务
 * 监听系统中活跃的 MediaSession，提供播放控制、元数据获取
 * 通过广播与悬浮面板通信
 */
class MusicControlService : Service() {

    companion object {
        const val ACTION_MUSIC_UPDATE = "com.cycling.dashboard.MUSIC_UPDATE"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_POSITION = "position"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_BT_CONNECTED = "bt_connected"

        const val ACTION_MUSIC_CONTROL = "com.cycling.dashboard.MUSIC_CONTROL"
        const val EXTRA_CONTROL_CMD = "control_cmd"
        const val CMD_PLAY_PAUSE = "play_pause"
        const val CMD_NEXT = "next"
        const val CMD_PREV = "prev"
        const val CMD_VOL_UP = "vol_up"
        const val CMD_VOL_DOWN = "vol_down"

        const val CHANNEL_ID = "music_control_channel"
        const val NOTIFICATION_ID = 1002
    }

    private val binder = LocalBinder()
    private var mediaController: MediaController? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var audioManager: AudioManager? = null

    private var currentTitle = "未知歌曲"
    private var currentArtist = "未知艺术家"
    private var currentDuration = 0L
    private var currentPosition = 0L
    private var isPlaying = false
    private val refreshHandler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            findActiveMediaController()
            refreshHandler.postDelayed(this, 2000)
        }
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            isPlaying = state?.state == PlaybackState.STATE_PLAYING
            currentPosition = state?.position ?: 0L
            broadcastMusicUpdate()
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            metadata?.let {
                currentTitle = it.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "未知歌曲"
                currentArtist = it.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                    ?: it.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: "未知艺术家"
                currentDuration = it.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
                broadcastMusicUpdate()
            }
        }
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MUSIC_CONTROL) {
                when (intent.getStringExtra(EXTRA_CONTROL_CMD)) {
                    CMD_PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    CMD_NEXT -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    CMD_PREV -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    CMD_VOL_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
                    CMD_VOL_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): MusicControlService = this@MusicControlService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        createNotificationChannel()
        registerReceiver(controlReceiver, IntentFilter(ACTION_MUSIC_CONTROL),
            Context.RECEIVER_NOT_EXPORTED)
        findActiveMediaController()
        refreshHandler.post(refreshRunnable)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun findActiveMediaController() {
        try {
            val controllers = mediaSessionManager?.getActiveSessions(
                ComponentName(this, BaHaoMediaNotificationListener::class.java)
            )
            val best = controllers
                ?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers?.firstOrNull()
            best?.let { attachController(it) }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun attachController(controller: MediaController) {
        mediaController?.unregisterCallback(mediaCallback)
        mediaController = controller
        controller.registerCallback(mediaCallback)

        // 获取初始状态
        controller.metadata?.let { mediaCallback.onMetadataChanged(it) }
        controller.playbackState?.let { mediaCallback.onPlaybackStateChanged(it) }
    }

    private fun sendMediaKey(keyCode: Int) {
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun adjustVolume(direction: Int) {
        audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
    }

    private fun broadcastMusicUpdate() {
        val intent = Intent(ACTION_MUSIC_UPDATE).apply {
            putExtra(EXTRA_TITLE, currentTitle)
            putExtra(EXTRA_ARTIST, currentArtist)
            putExtra(EXTRA_DURATION, currentDuration)
            putExtra(EXTRA_POSITION, currentPosition)
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_BT_CONNECTED, isBluetoothConnected())
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun isBluetoothConnected(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "音乐控制服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持音乐控制面板可用"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("骑行彩屏仪表")
            .setContentText("音乐控制面板运行中")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun getCurrentState(): MusicState = MusicState(
        title = currentTitle,
        artist = currentArtist,
        duration = currentDuration,
        position = currentPosition,
        isPlaying = isPlaying,
        btConnected = isBluetoothConnected()
    )

    data class MusicState(
        val title: String,
        val artist: String,
        val duration: Long,
        val position: Long,
        val isPlaying: Boolean,
        val btConnected: Boolean
    )

    override fun onDestroy() {
        super.onDestroy()
        refreshHandler.removeCallbacks(refreshRunnable)
        mediaController?.unregisterCallback(mediaCallback)
        unregisterReceiver(controlReceiver)
    }
}
