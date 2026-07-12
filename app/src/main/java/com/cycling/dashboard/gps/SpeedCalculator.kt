package com.cycling.dashboard.gps

import android.location.Location
import kotlin.math.max

/**
 * GPS 速度计算与滤波器
 * 处理 GPS 信号弱、速度抖动、静止漂移等问题
 */
class SpeedCalculator {

    companion object {
        // 速度阈值：低于此值视为静止 (m/s)
        private const val MIN_VALID_SPEED = 0.5f
        // 最大可信速度 (m/s)，约 120km/h
        private const val MAX_VALID_SPEED = 33.3f
        // 低通滤波系数 (0-1)，越大越跟随新值
        private const val SPEED_SMOOTHING_FACTOR = 0.3f
        // GPS 精度阈值 (米)，精度差时降低信任度
        private const val POOR_ACCURACY_THRESHOLD = 20f
    }

    private var lastValidSpeed: Float = 0f
    private var smoothedSpeed: Float = 0f
    private var signalQuality: SignalQuality = SignalQuality.GOOD

    enum class SignalQuality {
        GOOD,       // 信号良好
        WEAK,       // 信号较弱
        LOST        // 信号丢失
    }

    /**
     * 输入原始 Location，返回处理后的速度 (m/s) 和信号质量
     */
    fun process(location: Location?): Pair<Float, SignalQuality> {
        if (location == null) {
            signalQuality = SignalQuality.LOST
            return Pair(0f, signalQuality)
        }

        // 检查精度
        val accuracy = location.accuracy
        signalQuality = when {
            accuracy <= 10f -> SignalQuality.GOOD
            accuracy <= POOR_ACCURACY_THRESHOLD -> SignalQuality.WEAK
            else -> SignalQuality.WEAK
        }

        // 获取原始速度
        var rawSpeed = if (location.hasSpeed()) location.speed else 0f

        // 如果系统没有速度，用距离/时间计算
        if (rawSpeed == 0f) {
            rawSpeed = calculateSpeedFromDistance(location)
        }

        // 异常值过滤
        if (rawSpeed > MAX_VALID_SPEED) {
            rawSpeed = lastValidSpeed
        }

        // 静止阈值处理
        if (rawSpeed < MIN_VALID_SPEED) {
            rawSpeed = 0f
        }

        // 低通滤波平滑
        smoothedSpeed = smoothedSpeed * (1 - SPEED_SMOOTHING_FACTOR) + rawSpeed * SPEED_SMOOTHING_FACTOR

        // 精度差时进一步平滑
        if (accuracy > POOR_ACCURACY_THRESHOLD) {
            smoothedSpeed = smoothedSpeed * 0.7f + rawSpeed * 0.3f
        }

        lastValidSpeed = smoothedSpeed
        return Pair(smoothedSpeed, signalQuality)
    }

    /**
     * 将 m/s 转为 km/h
     */
    fun msToKmh(ms: Float): Float = ms * 3.6f

    /**
     * 获取格式化后的时速字符串
     */
    fun getFormattedSpeed(speedMs: Float): String {
        val kmh = msToKmh(speedMs)
        return String.format("%.0f", kmh)
    }

    private var lastLocation: Location? = null

    private fun calculateSpeedFromDistance(location: Location): Float {
        val last = lastLocation ?: run {
            lastLocation = location
            return 0f
        }

        val distance = location.distanceTo(last)
        val timeDelta = (location.time - last.time) / 1000f
        lastLocation = location

        return if (timeDelta > 0) distance / timeDelta else 0f
    }

    fun reset() {
        lastValidSpeed = 0f
        smoothedSpeed = 0f
        lastLocation = null
        signalQuality = SignalQuality.GOOD
    }
}
