package com.cycling.dashboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.cycling.dashboard.gps.SpeedCalculator
import kotlin.math.min

/**
 * 导航页角落悬浮小车速窗口
 * 紧凑显示实时车速与 GPS 信号状态
 */
class MiniSpeedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentSpeed = 0f
    private var signalQuality = SpeedCalculator.SignalQuality.GOOD

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CC1A1A2E")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#00B0FF")
    }
    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#A0A0B0")
    }
    private val signalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rect = RectF()

    init {
        updateTextSizes()
    }

    fun setSpeed(speedKmh: Float) {
        currentSpeed = speedKmh.coerceIn(0f, 150f)
        invalidate()
    }

    fun setSignalQuality(quality: SpeedCalculator.SignalQuality) {
        signalQuality = quality
        invalidate()
    }

    private fun updateTextSizes() {
        // 默认尺寸，会在 onSizeChanged 中覆盖
        speedPaint.textSize = 48f
        unitPaint.textSize = 18f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val r = min(w, h) / 2f
        speedPaint.textSize = r * 0.7f
        unitPaint.textSize = r * 0.22f
        borderPaint.strokeWidth = r * 0.06f
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f

        // 绘制圆角背景
        canvas.drawRoundRect(rect, radius * 0.25f, radius * 0.25f, bgPaint)
        canvas.drawRoundRect(rect, radius * 0.25f, radius * 0.25f, borderPaint)

        // 速度数字
        val speedStr = String.format("%.0f", currentSpeed)
        canvas.drawText(speedStr, cx, cy + speedPaint.textSize * 0.25f, speedPaint)

        // 单位
        canvas.drawText("km/h", cx, cy + speedPaint.textSize * 0.65f, unitPaint)

        // GPS 信号指示点（右上角）
        val dotRadius = radius * 0.08f
        val dotX = width - radius * 0.25f
        val dotY = radius * 0.25f
        signalPaint.color = when (signalQuality) {
            SpeedCalculator.SignalQuality.GOOD -> Color.parseColor("#00C853")
            SpeedCalculator.SignalQuality.WEAK -> Color.parseColor("#FFD600")
            SpeedCalculator.SignalQuality.LOST -> Color.parseColor("#FF1744")
        }
        canvas.drawCircle(dotX, dotY, dotRadius, signalPaint)
    }
}
