package com.siwoo.party.games.highlow.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 카드 위 층: 덱 남은 장수 알약 + 결과 도장("성공!" / "펑!") 튀어나오기.
 * 도장이 튀어나오는 동안만 postOnAnimation 으로 다시 그리고, 멈춰 있으면 그리지 않는다. onDraw 할당 없음.
 */
class OverlayView(context: Context, private val geom: TableGeom) : View(context) {

    private val d = context.resources.displayMetrics.density
    private val rect = RectF()
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEE0B1235.toInt() }
    private val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Theme.GOLD; strokeWidth = 1.2f * d }
    private val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT; textSize = 11f * d }
    private val stampFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stampStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * d; color = 0xFFFFFFFF.toInt() }
    private val stampText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
    private val stampSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)

    private var deckText = ""

    /** 지금 도장 글자 (테스트용) */
    var stamp = ""
        private set
    private var stampSubText = ""
    private var stampColor = Theme.GREEN
    private var stampBurst = false
    private var popStart = 0L
    private var popMs = 1L
    private var popping = false
    private var pop = 1f
    private val frame = Runnable { onFrame() }

    /** 덱 남은 장수 (음수면 숨김) */
    fun setDeckCount(n: Int) {
        val s = if (n < 0) "" else n.toString() + "장"
        if (s == deckText) return
        deckText = s
        invalidate()
    }

    /**
     * 결과 도장: 다음 카드 위에 비스듬히 튀어나옴.
     * @param burst true 면 뾰족한 폭발 별 바탕("펑!"), false 면 둥근 사각 도장
     */
    fun showStamp(text: String, sub: String, color: Int, burst: Boolean, popDurationMs: Long) {
        stamp = text
        stampSubText = sub
        stampColor = color
        stampBurst = burst
        popStart = SystemClock.uptimeMillis()
        popMs = popDurationMs.coerceAtLeast(1L)
        pop = 0f
        if (!popping) {
            popping = true
            postOnAnimation(frame)
        }
        invalidate()
    }

    fun clearStamp() {
        if (stamp.isEmpty()) return
        stamp = ""
        stampSubText = ""
        stopPop()
        invalidate()
    }

    private fun stopPop() {
        popping = false
        removeCallbacks(frame)
        pop = 1f
    }

    private fun onFrame() {
        if (!popping) return
        val t = (SystemClock.uptimeMillis() - popStart).toFloat() / popMs
        pop = t.coerceIn(0f, 1f)
        invalidate()
        if (t >= 1f) popping = false else postOnAnimation(frame)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPop()
    }

    override fun onDraw(c: Canvas) {
        if (!geom.ready) return
        val g = geom
        // 덱 남은 장수
        if (deckText.isNotEmpty()) {
            val tw = pillText.measureText(deckText) / 2f + 7f * d
            val py = g.deckY + g.smallH / 2f - 2f * d
            val ph = 9f * d
            rect.set(g.deckX - tw, py - ph, g.deckX + tw, py + ph)
            c.drawRoundRect(rect, ph, ph, pill)
            c.drawRoundRect(rect, ph, ph, pillStroke)
            c.drawText(deckText, g.deckX, py + pillText.textSize * 0.36f, pillText)
        }
        // 결과 도장
        if (stamp.isNotEmpty()) {
            // 크게 → 제자리 (살짝 튕김)
            val e = 1f - (1f - pop) * (1f - pop)
            val sc = 1.9f - 0.9f * e + if (pop < 1f) (0.08f * Math.sin(pop * Math.PI).toFloat()) else 0f
            val alpha = (255 * minOf(1f, pop * 3f + 0.15f)).toInt().coerceIn(0, 255)
            val cx = g.slotX
            val cy = g.cy + g.bigH * 0.12f
            val bw = g.bigW * 0.62f
            c.save()
            c.translate(cx, cy)
            c.rotate(-12f)
            c.scale(sc, sc)
            stampText.textSize = g.bigW * 0.3f
            stampSub.textSize = g.bigW * 0.13f
            stampText.alpha = alpha
            stampSub.alpha = alpha
            if (stampBurst) {
                art.alpha = alpha
                HighLowArt.burst(c, 0f, 0f, bw * 1.05f, bw * 0.68f, 12, 0xFFFFC93C.toInt(), art)
                HighLowArt.burst(c, 0f, 0f, bw * 0.9f, bw * 0.6f, 12, stampColor, art)
                c.drawText(stamp, 0f, stampText.textSize * 0.2f, stampText)
                if (stampSubText.isNotEmpty()) c.drawText(stampSubText, 0f, stampText.textSize * 0.2f + stampSub.textSize * 1.25f, stampSub)
            } else {
                val bh = g.bigW * 0.27f
                rect.set(-bw, -bh, bw, bh)
                stampFill.color = stampColor
                stampFill.alpha = (alpha * 0.92f).toInt()
                c.drawRoundRect(rect, bh * 0.4f, bh * 0.4f, stampFill)
                stampStroke.alpha = alpha
                c.drawRoundRect(rect, bh * 0.4f, bh * 0.4f, stampStroke)
                if (stampSubText.isEmpty()) {
                    c.drawText(stamp, 0f, stampText.textSize * 0.36f, stampText)
                } else {
                    c.drawText(stamp, 0f, -bh * 0.08f, stampText)
                    c.drawText(stampSubText, 0f, bh * 0.68f, stampSub)
                }
            }
            c.restore()
        }
    }
}
