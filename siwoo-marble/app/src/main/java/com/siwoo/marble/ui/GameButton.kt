package com.siwoo.marble.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/**
 * 입체 버튼: 위쪽이 밝은 세로 그라데이션 + 아래쪽 어두운 두께.
 * 누르면 면이 2~3dp 아래로 내려가 두께가 사라진다. 비활성화되면 회색.
 * blinking = true 면 빨갛게 깜빡인다(잔액 부족 시 [판매]).
 */
class GameButton(context: Context, color: Int, textSp: Float = 17f) : TextView(context) {

    var color: Int = color
        set(v) { field = v; rebuildShader(); invalidate() }

    private val depth = Theme.dpf(context, 3f)
    private val radius = Theme.dpf(context, 14f)
    private val face = RectF()
    private val base = RectF()
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { setColor(0x30FFFFFF) }
    private val blinkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var blinkAlpha = 0f
    private var blinkAnim: ValueAnimator? = null

    init {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        typeface = Typeface.DEFAULT_BOLD
        isClickable = true
        isFocusable = true
        includeFontPadding = false
        val ph = Theme.dp(context, 12f)
        setPadding(ph, 0, ph, depth.toInt())
        setShadowLayer(Theme.dpf(context, 1.5f), 0f, Theme.dpf(context, 1f), 0x66000000)
    }

    var blinking: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            blinkAnim?.cancel()
            blinkAnim = null
            blinkAlpha = 0f
            if (v) {
                blinkAnim = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                    duration = 900
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { blinkAlpha = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
            invalidate()
        }

    /** 눌렸다가 튀어오르는 연출 (인원 선택 등) */
    fun bounce() {
        animate().cancel()
        scaleX = 0.88f
        scaleY = 0.88f
        animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(OvershootInterpolator(3f)).start()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.55f
        rebuildShader()
        invalidate()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
    }

    private fun rebuildShader() {
        val h = height.toFloat()
        if (h <= 0f) return
        val c = if (isEnabled) color else Theme.GRAY
        facePaint.shader = LinearGradient(
            0f, 0f, 0f, h - depth,
            Theme.shade(c, 1.35f), c, Shader.TileMode.CLAMP,
        )
        basePaint.color = Theme.shade(c, 0.55f)
        blinkPaint.color = Theme.RED
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val down = isPressed && isEnabled
        val off = if (down) depth else 0f
        if (!down) {
            base.set(0f, depth, w, h)
            canvas.drawRoundRect(base, radius, radius, basePaint)
        }
        face.set(0f, off, w, h - depth + off)
        canvas.drawRoundRect(face, radius, radius, facePaint)
        if (blinkAlpha > 0f) {
            blinkPaint.alpha = (blinkAlpha * 200).toInt()
            canvas.drawRoundRect(face, radius, radius, blinkPaint)
        }
        // 윗면 광택
        face.set(radius * 0.5f, off + depth * 0.6f, w - radius * 0.5f, off + (h - depth) * 0.42f)
        canvas.drawRoundRect(face, radius * 0.7f, radius * 0.7f, glossPaint)
        canvas.save()
        canvas.translate(0f, off)
        super.onDraw(canvas)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        blinkAnim?.cancel()
        super.onDetachedFromWindow()
    }
}
