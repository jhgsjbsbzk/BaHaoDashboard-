package com.cycling.dashboard.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 开机/关机音效管理器。
 * 用户可从手机选 MP3/WAV 文件，复制到 App 私有目录，开机/关机时自动播放。
 */
object SoundEffectManager {

    private const val DIR_NAME = "sound_effects"
    private const val BOOT_FILE = "boot_sound.mp3"
    private const val SHUTDOWN_FILE = "shutdown_sound.mp3"

    private var bootPlayer: MediaPlayer? = null
    private var shutdownPlayer: MediaPlayer? = null

    /** 获取开机音效文件，null 表示未设置 */
    fun getBootSoundFile(context: Context): File? {
        val dir = getDir(context) ?: return null
        val file = File(dir, BOOT_FILE)
        return if (file.exists()) file else null
    }

    /** 获取关机音效文件，null 表示未设置 */
    fun getShutdownSoundFile(context: Context): File? {
        val dir = getDir(context) ?: return null
        val file = File(dir, SHUTDOWN_FILE)
        return if (file.exists()) file else null
    }

    /** 保存开机音效：把用户选的 Uri 复制到私有目录 */
    fun saveBootSound(context: Context, sourceUri: Uri): Boolean {
        return copyUriToFile(context, sourceUri, BOOT_FILE)
    }

    /** 保存关机音效：把用户选的 Uri 复制到私有目录 */
    fun saveShutdownSound(context: Context, sourceUri: Uri): Boolean {
        return copyUriToFile(context, sourceUri, SHUTDOWN_FILE)
    }

    /** 播放开机音效 */
    fun playBootSound(context: Context) {
        val file = getBootSoundFile(context) ?: return
        playSound(file)
    }

    /** 播放开机音效（使用传入的 Uri，兼容网页版 assets） */
    fun playBootSoundUri(context: Context, uri: Uri) {
        playSoundUri(uri)
    }

    /** 播放关机音效 */
    fun playShutdownSound(context: Context) {
        val file = getShutdownSoundFile(context) ?: return
        playSound(file)
    }

    /** 试听音效文件 */
    fun previewSound(context: Context, file: File) {
        playSound(file)
    }

    /** 停止所有音效 */
    fun stopAll() {
        try { bootPlayer?.stop() } catch (_: Exception) {}
        try { shutdownPlayer?.stop() } catch (_: Exception) {}
        bootPlayer?.release()
        shutdownPlayer?.release()
        bootPlayer = null
        shutdownPlayer = null
    }

    /** 删除开机音效 */
    fun deleteBootSound(context: Context): Boolean {
        val file = getBootSoundFile(context) ?: return false
        return file.delete()
    }

    /** 删除关机音效 */
    fun deleteShutdownSound(context: Context): Boolean {
        val file = getShutdownSoundFile(context) ?: return false
        return file.delete()
    }

    /** 检查是否已设置开机音效 */
    fun hasBootSound(context: Context): Boolean {
        return getBootSoundFile(context) != null
    }

    /** 检查是否已设置关机音效 */
    fun hasShutdownSound(context: Context): Boolean {
        return getShutdownSoundFile(context) != null
    }

    // ---- 内部方法 ----

    private fun getDir(context: Context): File? {
        val dir = File(context.filesDir, DIR_NAME)
        return if (dir.mkdirs() || dir.isDirectory) dir else null
    }

    private fun copyUriToFile(context: Context, sourceUri: Uri, fileName: String): Boolean {
        val dir = getDir(context) ?: return false
        return try {
            val file = File(dir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun playSound(file: File) {
        try {
            stopAll()
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { release() }
                setOnErrorListener { _, _, _ -> true }
            }
            player.start()
            if (file.name.contains("boot")) bootPlayer = player else shutdownPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playSoundUri(uri: Uri) {
        try {
            stopAll()
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(uri.toString())
                prepare()
                setOnCompletionListener { release() }
                setOnErrorListener { _, _, _ -> true }
            }
            player.start()
            bootPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
