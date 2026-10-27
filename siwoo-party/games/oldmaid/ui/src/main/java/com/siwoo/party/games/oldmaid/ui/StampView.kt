package com.siwoo.party.games.oldmaid.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.view.animation.OvershootInterpolator
import com.siwoo.party.common.ui.Theme

/**
 * "도둑!" 빨간 도장 (끝날 때 조커 위에 쾅). 그림은 고정이고 [slam] 은 View 의 크기·투명도·회전 속성만 움직인다.
 * 이름(name)은 setName 에서만 바뀐다 → onDraw 할당 없음.
 */
class StampView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = STAMP_RED }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FF2030 }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = STAMP_RED }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = STAMP_RED }
    private var name = ""

    /** 도장이 찍혔는지 */
    var stamped = false
        private set

    init {
        visibility = INVISIBLE
        isClickable = false
    }

    fun setName(n: String) {
        name = n
        fitName()
        invalidate()
    }

    private fun fitName() {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        namePaint.textSize = s * 0.12f
        while (name.isNotEmpty() && namePaint.measureText(name) > s * 0.62f && namePaint.textSize > 8f * density) namePaint.textSize *= 0.92f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val s = minOf(w, h).toFloat()
        textPaint.textSize = s * 0.24f
        fitName()
    }

    /** 쾅! (크게·투명 → 제자리, 살짝 기울어짐) */
    fun slam(ms: Long) {
        stamped = true
        visibility = VISIBLE
        alpha = 0f
        scaleX = 2.6f; scaleY = 2.6f
        rotation = -32f
        animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(-14f).setDuration(ms)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
    }

    fun reset() {
        animate().cancel()
        stamped = false
        visibility = INVISIBLE
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val rOut = s * 0.46f
        c.drawCircle(cx, cy, rOut, fill)
        ring.strokeWidth = s * 0.045f
        c.drawCircle(cx, cy, rOut, ring)
        ring.strokeWidth = s * 0.018f
        c.drawCircle(cx, cy, rOut * 0.84f, ring)
        // 위아래 작은 별점
        for (k in -1..1) {
            c.drawCircle(cx + k * s * 0.09f, cy - rOut * 0.62f, s * 0.018f, bandPaint)
        }
        c.drawText(TEXT, cx, cy + textPaint.textSize * 0.2f, textPaint)
        if (name.isNotEmpty()) {
            val bw = s * 0.36f
            val by = cy + rOut * 0.5f
            c.drawRoundRect(cx - bw, by - namePaint.textSize * 0.85f, cx + bw, by + namePaint.textSize * 0.45f, s * 0.03f, s * 0.03f, bandPaint)
            c.drawText(name, cx, by + namePaint.textSize * 0.1f, namePaint)
        }
    }

    companion object {
        const val TEXT = "도둑!"
        private const val STAMP_RED = Theme.RED_DARK
    }
}
