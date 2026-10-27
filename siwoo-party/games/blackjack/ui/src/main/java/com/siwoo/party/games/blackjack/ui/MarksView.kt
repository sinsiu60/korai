package com.siwoo.party.games.blackjack.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.blackjack.GameConfig

/**
 * 카드 위 도장 층: "버스트", "블랙잭!", "승리 +20", "관전" 처럼 손패 위에 비스듬히 찍히는 표시.
 * 자리 0..3 = 플레이어, [DEALER] = 딜러 카드 위. 찍힐 때 크게 나타났다 줄어드는 동안만 프레임이 돈다.
 * 글자는 [stamp] 에서만 받는다 (onDraw 할당 없음). 터치는 받지 않는다.
 */
class MarksView(context: Context, private val geom: TableGeom) : View(context) {

    companion object {
        const val DEALER = GameConfig.MAX_PLAYERS
        private const val SLOTS = GameConfig.MAX_PLAYERS + 1
    }

    private val d = Theme.dpf(context, 1f)
    private val main = Array(SLOTS) { "" }
    private val sub = Array(SLOTS) { "" }
    private val color = IntArray(SLOTS)
    private val start = LongArray(SLOTS)
    private val widths = FloatArray(SLOTS)
    private val on = BooleanArray(SLOTS)
    private val faint = BooleanArray(SLOTS)
    private var popMs = 320L
    private var ticking = false
    private val tick = Runnable { onTick() }

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        textSize = 17f * d
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        textSize = 12.5f * d
        color = Theme.TEXT
    }

    init {
        isClickable = false
        isFocusable = false
    }

    /** slot 에 도장 (popMs 동안 톡). faint = 흐리게 (관전) */
    fun stamp(slot: Int, text: String, subText: String, stampColor: Int, popDurationMs: Long, isFaint: Boolean = false) {
        main[slot] = text
        sub[slot] = subText
        color[slot] = stampColor
        faint[slot] = isFaint
        widths[slot] = maxOf(mainPaint.measureText(text), subPaint.measureText(subText)) + 22f * d
        on[slot] = true
        popMs = popDurationMs.coerceAtLeast(1L)
        start[slot] = SystemClock.uptimeMillis()
        if (!ticking) {
            ticking = true
            postOnAnimation(tick)
        }
        invalidate()
    }

    fun clear(slot: Int) {
        if (on[slot]) { on[slot] = false; invalidate() }
    }

    fun clearAll() {
        for (k in 0 until SLOTS) on[k] = false
        invalidate()
    }

    fun isStamped(slot: Int): Boolean = on[slot]
    fun text(slot: Int): String = if (on[slot]) main[slot] else ""

    private fun onTick() {
        if (!ticking) return
        invalidate()
        val now = SystemClock.uptimeMillis()
        var busy = false
        for (k in 0 until SLOTS) if (on[k] && now - start[k] < popMs) busy = true
        if (busy) postOnAnimation(tick) else ticking = false
    }

    override fun onDraw(c: Canvas) {
        val g = geom
        if (g.w <= 0f) return
        val now = SystemClock.uptimeMillis()
        for (k in 0 until SLOTS) {
            if (!on[k]) continue
            val cx: Float
            val cy: Float
            if (k == DEALER) {
                cx = g.dealerCx
                cy = g.dealerCy + g.cardH * g.dealerScale * 0.08f
            } else {
                if (k >= g.n) continue
                cx = g.seats[k].centerX()
                cy = g.handCy + g.cardH * g.handScale * 0.08f
            }
            val p = ((now - start[k]).toFloat() / popMs).coerceIn(0f, 1f)
            val u = 1f - p
            val sc = 1f + 0.9f * u * u
            val alpha = (255 * (0.35f + 0.65f * p)).toInt()
            drawStamp(c, k, cx, cy, sc, if (faint[k]) alpha * 2 / 3 else alpha)
        }
    }

    private fun drawStamp(c: Canvas, k: Int, cx: Float, cy: Float, sc: Float, alpha: Int) {
        val hasSub = sub[k].isNotEmpty()
        val w = widths[k]
        val h = (if (hasSub) 41f else 28f) * d
        c.save()
        c.translate(cx, cy)
        c.rotate(-9f)
        c.scale(sc, sc)
        rect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        fill.color = 0xE6101634.toInt()
        fill.alpha = alpha * 230 / 255
        c.drawRoundRect(rect, 8f * d, 8f * d, fill)
        stroke.color = color[k]
        stroke.alpha = alpha
        stroke.strokeWidth = 3f * d
        c.drawRoundRect(rect, 8f * d, 8f * d, stroke)
        rect.inset(4f * d, 4f * d)
        stroke.strokeWidth = 1.2f * d
        c.drawRoundRect(rect, 5f * d, 5f * d, stroke)
        mainPaint.color = color[k]
        mainPaint.alpha = alpha
        val mainBase = if (hasSub) -1.5f * d else mainPaint.textSize * 0.36f
        c.drawText(main[k], 0f, mainBase, mainPaint)
        if (hasSub) {
            subPaint.alpha = alpha
            c.drawText(sub[k], 0f, mainBase + 15f * d, subPaint)
        }
        c.restore()
    }

    override fun onDetachedFromWindow() {
        ticking = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}
