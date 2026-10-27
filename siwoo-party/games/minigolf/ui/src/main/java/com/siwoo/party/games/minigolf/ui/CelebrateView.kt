package com.siwoo.party.games.minigolf.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme
import kotlin.math.sin

/**
 * "홀인원!" 같은 아주 큰 글자 (화면 높이의 1/3 쯤): 튀어나오며 커졌다가 → 살짝 흔들림 → 사라짐.
 * 보이는 동안에만 다시 그린다 (postInvalidateOnAnimation). 터치는 받지 않는다. onDraw 할당 없음.
 */
class CelebrateView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var text = ""
    private var sub = ""
    private var start = -1L
    private var dur = 1L

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; color = 0xFF3A1A00.toInt()
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val subOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = 0xCC000000.toInt()
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 보이는 중인지 */
    val showing: Boolean get() = start >= 0L && SystemClock.uptimeMillis() - start < dur

    init {
        isClickable = false
        isFocusable = false
    }

    fun show(big: String, small: String, ms: Long) {
        text = big
        sub = small
        start = SystemClock.uptimeMillis()
        dur = maxOf(1L, ms)
        layoutText()
        postInvalidateOnAnimation()
    }

    fun hide() {
        start = -1L
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutText()
    }

    /** 글자 크기를 폭에 맞추고 금색 그라데이션 (값이 바뀔 때만) */
    private fun layoutText() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || text.isEmpty()) return
        var size = h * 0.3f
        fillPaint.textSize = size
        while (fillPaint.measureText(text) > w * 0.86f && size > 20f * d) {
            size *= 0.92f
            fillPaint.textSize = size
        }
        outline.textSize = size
        outline.strokeWidth = size * 0.14f
        fillPaint.shader = LinearGradient(0f, -size * 0.8f, 0f, size * 0.1f, intArrayOf(0xFFFFF6C8.toInt(), Theme.GOLD, 0xFFFF8A2A.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        subPaint.textSize = size * 0.24f
        subOutline.textSize = size * 0.24f
        subOutline.strokeWidth = size * 0.05f
    }

    override fun onDraw(c: Canvas) {
        if (start < 0L) return
        val t = SystemClock.uptimeMillis() - start
        if (t >= dur) {
            start = -1L
            return
        }
        val f = t.toFloat() / dur
        val w = width.toFloat()
        val h = height.toFloat()
        // 등장 (0..0.18 튀어나옴) → 흔들림 → 끝 0.15 동안 사라짐
        val scale = if (f < 0.18f) {
            val e = f / 0.18f
            0.2f + 1.05f * e - 0.25f * e * e * e
        } else 1f + 0.04f * sin((f - 0.18f) * 30f)
        val alpha = if (f > 0.85f) ((1f - f) / 0.15f).coerceIn(0f, 1f) else 1f
        val a = (255 * alpha).toInt()
        // 뒤쪽 빛
        glow.color = 0x55000000
        glow.alpha = (0x55 * alpha).toInt()
        c.drawRect(0f, h * 0.28f, w, h * 0.72f, glow)
        c.save()
        c.translate(w / 2f, h * 0.52f)
        c.rotate(-6f * (1f - minOf(1f, f / 0.18f)))
        c.scale(scale, scale)
        outline.alpha = a
        fillPaint.alpha = a
        c.drawText(text, 0f, 0f, outline)
        c.drawText(text, 0f, 0f, fillPaint)
        if (sub.isNotEmpty()) {
            subOutline.alpha = a
            subPaint.alpha = a
            val y = subPaint.textSize * 1.6f
            c.drawText(sub, 0f, y, subOutline)
            c.drawText(sub, 0f, y, subPaint)
        }
        c.restore()
        postInvalidateOnAnimation()
    }
}
