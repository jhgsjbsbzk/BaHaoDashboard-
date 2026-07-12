package com.cycling.dashboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.cycling.dashboard.R
import kotlin.math.min

/**
 * 自定义 GPS 速度仪表盘 View
 * 复刻九号电动车彩色 TFT 仪表盘风格
 * 支持日间/夜间模式
 */
class SpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_SPEED = 120f // 最大显示速度 km/h
        private const val SWEEP_ANGLE = 270f // 表盘总跨度角度
        private const val START_ANGLE = 135f // 起始角度（从右下开始逆时针）
    }

    // 当前数据
    private var currentSpeed = 0f
    private var currentDistance = 0f // 米
    private var isNightMode = false
    private var gpsSignalLevel = 2 // 0=丢失, 1=弱, 2=良好

    // Paint 对象
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val speedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val distanceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val signalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val signalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }

    // 尺寸缓存
    private val arcRect = RectF()
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var strokeWidth = 0f

    init {
        updateColors()
    }

    fun setSpeed(speedKmh: Float) {
        currentSpeed = speedKmh.coerceIn(0f, MAX_SPEED)
        invalidate()
    }

    fun setDistance(meters: Float) {
        currentDistance = meters
        invalidate()
    }

    fun setNightMode(night: Boolean) {
        if (isNightMode != night) {
            isNightMode = night
            updateColors()
            invalidate()
        }
    }

    fun setGpsSignal(level: Int) {
        gpsSignalLevel = level.coerceIn(0, 2)
        invalidate()
    }

    private fun updateColors() {
        if (isNightMode) {
            // 夜间模式：深色背景，高对比度文字
            arcBackgroundPaint.color = Color.parseColor("#2A2A3A")
            speedTextPaint.color = Color.WHITE
            unitTextPaint.color = Color.parseColor("#A0A0B0")
            labelTextPaint.color = Color.parseColor("#808090")
            distanceTextPaint.color = Color.parseColor("#C0C0D0")
            signalTextPaint.color = Color.parseColor("#A0A0B0")
        } else {
            // 日间模式：浅色背景
            arcBackgroundPaint.color = Color.parseColor("#E8E8F0")
            speedTextPaint.color = Color.parseColor("#1A1A2E")
            unitTextPaint.color = Color.parseColor("#6B6B80")
            labelTextPaint.color = Color.parseColor("#9A9AAF")
            distanceTextPaint.color = Color.parseColor("#4A4A5E")
            signalTextPaint.color = Color.parseColor("#6B6B80")
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = min(w, h) * 0.38f
        strokeWidth = radius * 0.12f

        val padding = strokeWidth / 2 + 8
        arcRect.set(
            centerX - radius + padding,
            centerY - radius + padding,
            centerX + radius - padding,
            centerY + radius - padding
        )

        arcPaint.strokeWidth = strokeWidth
        arcBackgroundPaint.strokeWidth = strokeWidth

        // 文字大小根据视图尺寸动态计算
        val speedTextSize = radius * 0.9f
        speedTextPaint.textSize = speedTextSize

        unitTextPaint.textSize = radius * 0.18f
        labelTextPaint.textSize = radius * 0.12f
        distanceTextPaint.textSize = radius * 0.22f
        signalTextPaint.textSize = radius * 0.11f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制背景弧（整个量程）
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, arcBackgroundPaint)

        // 计算当前速度对应的扫过角度
        val speedRatio = currentSpeed / MAX_SPEED
        val sweep = SWEEP_ANGLE * speedRatio

        // 设置速度弧颜色（随速度渐变）
        arcPaint.color = getSpeedColor(currentSpeed)
        canvas.drawArc(arcRect, START_ANGLE, sweep, false, arcPaint)

        // 绘制中心速度数字
        val speedStr = String.format("%.0f", currentSpeed)
        val yOffset = radius * 0.15f
        canvas.drawText(speedStr, centerX, centerY + yOffset, speedTextPaint)

        // 绘制单位 km/h
        val unitY = centerY + yOffset + radius * 0.28f
        canvas.drawText("km/h", centerX, unitY, unitTextPaint)

        // 绘制刻度标签（0, 30, 60, 90, 120）
        drawScaleLabels(canvas)

        // 绘制本次里程
        val distKm = currentDistance / 1000f
        val distStr = String.format("本次里程 %.1f km", distKm)
        val distY = centerY + radius * 0.65f
        canvas.drawText(distStr, centerX, distY, distanceTextPaint)

        // 绘制 GPS 信号状态
        drawGpsSignal(canvas)
    }

    private fun getSpeedColor(speed: Float): Int {
        return when {
            speed < 30f -> Color.parseColor("#00C853")   // 绿色
            speed < 60f -> Color.parseColor("#00B0FF")   // 蓝色
            speed < 90f -> Color.parseColor("#FFD600")   // 黄色
            else -> Color.parseColor("#FF1744")          // 红色
        }
    }

    private fun drawScaleLabels(canvas: Canvas) {
        val labels = listOf(0, 30, 60, 90, 120)
        val labelRadius = radius + strokeWidth * 0.8f

        labels.forEach { labelSpeed ->
            val ratio = labelSpeed / MAX_SPEED
            val angle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * ratio).toDouble())
            val x = centerX + (labelRadius * kotlin.math.cos(angle)).toFloat()
            val y = centerY + (labelRadius * kotlin.math.sin(angle)).toFloat() + labelTextPaint.textSize * 0.3f
            canvas.drawText(labelSpeed.toString(), x, y, labelTextPaint)
        }
    }

    private fun drawGpsSignal(canvas: Canvas) {
        val dotRadius = radius * 0.06f
        val startX = centerX + radius * 0.5f
        val startY = centerY - radius * 0.75f
        val gap = dotRadius * 2.5f

        // 信号状态颜色
        val activeColor = when (gpsSignalLevel) {
            2 -> Color.parseColor("#00C853") // 良好 - 绿
            1 -> Color.parseColor("#FFD600") // 弱 - 黄
            else -> Color.parseColor("#FF1744") // 丢失 - 红
        }

        // 绘制3个信号柱
        for (i in 0..2) {
            val isActive = i < when (gpsSignalLevel) {
                2 -> 3
                1 -> 2
                else -> 1
            }
            signalPaint.color = if (isActive) activeColor else Color.parseColor("#404050")
            val cx = startX + i * gap
            canvas.drawCircle(cx, startY, dotRadius * (0.6f + i * 0.2f), signalPaint)
        }

        // GPS 文字标签
        val signalLabel = when (gpsSignalLevel) {
            2 -> "GPS"
            1 -> "GPS弱"
            else -> "GPS无信号"
        }
        canvas.drawText(signalLabel, startX + gap * 3.5f, startY + signalTextPaint.textSize * 0.3f, signalTextPaint)
    }
}
