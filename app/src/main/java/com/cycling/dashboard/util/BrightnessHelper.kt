package com.cycling.dashboard.util

import android.app.Activity
import android.content.Context
import android.provider.Settings
import android.view.WindowManager

/**
 * 屏幕亮度控制工具
 * 自动拉高亮度以适应户外强光环境
 */
object BrightnessHelper {

    private var originalBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    private var originalBrightness = 128

    /**
     * 进入仪表盘时拉高屏幕亮度到最大
     */
    fun boostBrightness(activity: Activity) {
        try {
            // 保存原始设置
            originalBrightnessMode = Settings.System.getInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE
            )
            originalBrightness = Settings.System.getInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )

            // 切换到手动模式并设置最大亮度
            Settings.System.putInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                255
            )

            // 同时设置窗口亮度（即时生效）
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            activity.window.attributes = layoutParams

        } catch (e: SecurityException) {
            // 没有 WRITE_SETTINGS 权限，使用窗口级别亮度
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            activity.window.attributes = layoutParams
        }
    }

    /**
     * 恢复原始亮度设置
     */
    fun restoreBrightness(activity: Activity) {
        try {
            Settings.System.putInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                originalBrightnessMode
            )
            Settings.System.putInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                originalBrightness
            )
        } catch (_: SecurityException) { }

        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = layoutParams
    }

    /**
     * 检查是否有修改系统设置权限
     */
    fun canWriteSettings(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }
}
