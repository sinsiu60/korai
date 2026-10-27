package com.siwoo.party.games.highlow.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 카드 테이블 바닥 (카드 그림 CardTableView 아래 층): 초록 펠트 + 금색 테두리, 맨 위 숫자 순서 안내,
 * 덱·버린 카드 빈자리, "기준 카드"·"다음 카드" 이름표, 다음 카드 점선 자리(?), 가운데 ▲▼ 화살표,
 * 맨 아래 확률 안내("▲ 높은 숫자 n가지 · ▼ 낮은 숫자 m가지 · 같으면 실패").
 * 값이 바뀔 때(setter)만 글자를 만들고 다시 그린다. onDraw 할당 없음.
 */
class FeltView(context: Context, private val geom: TableGeom) : View(context) {

    private val d = context.resources.displayMetrics.density
    private val rect = RectF()
    private val feltPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Theme.GOLD_DARK; strokeWidth = 2f * d }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x40FFFFFF; strokeWidth = 1.5f * d }
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0x99FFE08A.toInt(); strokeWidth = 2.5f * d
        pathEffect = DashPathEffect(floatArrayOf(9f * d, 6f * d), 0f)
    }
    private val slotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }
    private val qPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0x55FFE08A }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xDDFFFFFF.toInt(); textSize = 12.5f * d }
    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT; textSize = 11.5f * d }
    private val holeText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0x66FFFFFF; textSize = 10.5f * d }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC08142E.toInt() }
    private val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x55FFC93C; strokeWidth = 1.2f * d }
    private val oddsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 13f * d }
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── 확률 안내 글자 (setOdds 에서 만든다) ──
    private var oddsOn = false
    private var hiText = ""
    private var loText = ""
    private var hiW = 0f
    private var loW = 0f
    private var tieW = 0f
    private var hiColor = HI_TEXT
    private var loColor = LO_TEXT

    /** 화살표 강조: 0 없음, 1 높다, -1 낮다 (고른 쪽을 밝게) */
    private var picked = 0

    init {
        tieW = oddsPaint.measureText(TIE)
    }

    /** 기준 카드보다 높은·낮은 숫자 가짓수 (음수면 안내를 숨김) */
    fun setOdds(higher: Int, lower: Int) {
        if (higher < 0 || lower < 0) {
            if (oddsOn) { oddsOn = false; invalidate() }
            return
        }
        oddsOn = true
        hiText = "높은 숫자 " + higher + "가지"
        loText = "낮은 숫자 " + lower + "가지"
        hiW = oddsPaint.measureText(hiText)
        loW = oddsPaint.measureText(loText)
        hiColor = if (higher == 0) DEAD_TEXT else HI_TEXT
        loColor = if (lower == 0) DEAD_TEXT else LO_TEXT
        invalidate()
    }

    /** 가운데 화살표 강조 (1 높다, -1 낮다, 0 없음) */
    fun setPicked(dir: Int) {
        if (picked == dir) return
        picked = dir
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        feltPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Theme.shade(HighLowArt.FELT, 1.12f), HighLowArt.FELT_DARK, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        if (!geom.ready) return
        val g = geom
        val r = 16f * d
        rect.set(d, d, width - d, height - d)
        c.drawRoundRect(rect, r, r, feltPaint)
        rect.inset(5f * d, 5f * d)
        c.drawRoundRect(rect, r * 0.7f, r * 0.7f, rimPaint)

        // 맨 위: 숫자 순서 안내
        c.drawText(TOP_LINE, width / 2f, g.topLineY + topPaint.textSize * 0.36f, topPaint)

        // 덱·버린 카드 빈자리
        val cr = g.smallW * 0.08f
        rect.set(g.deckX - g.smallW / 2f, g.deckY - g.smallH / 2f, g.deckX + g.smallW / 2f, g.deckY + g.smallH / 2f)
        c.drawRoundRect(rect, cr, cr, holePaint)
        c.drawText(DECK, g.deckX, g.deckY + holeText.textSize * 0.36f, holeText)
        rect.set(g.discardX - g.smallW / 2f, g.discardY - g.smallH / 2f, g.discardX + g.smallW / 2f, g.discardY + g.smallH / 2f)
        c.drawRoundRect(rect, cr, cr, holePaint)
        c.drawText(DISCARD1, g.discardX, g.discardY - holeText.textSize * 0.2f, holeText)
        c.drawText(DISCARD2, g.discardX, g.discardY + holeText.textSize * 1.0f, holeText)

        // 기준 카드 이름표 + 빈자리 테두리
        c.drawText(BASE, g.baseX, g.captionY, capPaint)
        val br = g.bigW * 0.08f
        rect.set(g.baseX - g.bigW / 2f, g.cy - g.bigH / 2f, g.baseX + g.bigW / 2f, g.cy + g.bigH / 2f)
        c.drawRoundRect(rect, br, br, holePaint)

        // 다음 카드 점선 자리 + ?
        c.drawText(NEXT, g.slotX, g.captionY, capPaint)
        rect.set(g.slotX - g.bigW / 2f, g.cy - g.bigH / 2f, g.slotX + g.bigW / 2f, g.cy + g.bigH / 2f)
        c.drawRoundRect(rect, br, br, slotFill)
        c.drawRoundRect(rect, br, br, slotPaint)
        qPaint.textSize = g.bigW * 0.62f
        c.drawText(QUESTION, g.slotX, g.cy + qPaint.textSize * 0.36f, qPaint)

        // 가운데: ▲ / ▼ (고른 쪽은 진하게)
        val ax = (g.baseX + g.bigW / 2f + g.slotX - g.bigW / 2f) / 2f
        val gap = g.slotX - g.bigW / 2f - (g.baseX + g.bigW / 2f)
        val s = minOf(gap * 0.75f, g.bigH * 0.2f)
        if (s > 6f * d) {
            artPaint.alpha = 255
            HighLowArt.arrow(c, ax, g.cy - s * 0.75f, s, true, if (picked == -1) DIM_ARROW else HighLowArt.HIGH, artPaint)
            HighLowArt.arrow(c, ax, g.cy + s * 0.75f, s, false, if (picked == 1) DIM_ARROW else HighLowArt.LOW, artPaint)
        }

        // 맨 아래: 확률 안내
        if (oddsOn) {
            val a = oddsPaint.textSize
            val gapW = a * 0.9f
            val total = a + 4f * d + hiW + gapW + a + 4f * d + loW + gapW + tieW
            val pad = 12f * d
            val cx = width / 2f
            val left = cx - total / 2f
            rect.set(left - pad, g.oddsY - g.oddsH / 2f, left + total + pad, g.oddsY + g.oddsH / 2f)
            c.drawRoundRect(rect, g.oddsH / 2f, g.oddsH / 2f, pillPaint)
            c.drawRoundRect(rect, g.oddsH / 2f, g.oddsH / 2f, pillStroke)
            val base = g.oddsY + a * 0.36f
            var x = left
            HighLowArt.arrow(c, x + a / 2f, g.oddsY, a * 0.9f, true, if (hiColor == DEAD_TEXT) DEAD_TEXT else HighLowArt.HIGH, artPaint)
            x += a + 4f * d
            oddsPaint.color = hiColor
            c.drawText(hiText, x, base, oddsPaint)
            x += hiW + gapW
            HighLowArt.arrow(c, x + a / 2f, g.oddsY, a * 0.9f, false, if (loColor == DEAD_TEXT) DEAD_TEXT else HighLowArt.LOW, artPaint)
            x += a + 4f * d
            oddsPaint.color = loColor
            c.drawText(loText, x, base, oddsPaint)
            x += loW + gapW
            oddsPaint.color = Theme.TEXT_SUB
            c.drawText(TIE, x, base, oddsPaint)
        }
    }

    /** 테스트용: 지금 확률 안내 글자 */
    val oddsText: String get() = if (oddsOn) "$hiText / $loText" else ""

    companion object {
        private const val TOP_LINE = "A = 1 가장 낮음   ·   K = 13 가장 높음   ·   조커 없음"
        private const val DECK = "덱"
        private const val DISCARD1 = "버린"
        private const val DISCARD2 = "카드"
        private const val BASE = "기준 카드"
        private const val NEXT = "다음 카드"
        private const val QUESTION = "?"
        private const val TIE = "같으면 실패"
        private const val HI_TEXT = 0xFFFFB4A6.toInt()
        private const val LO_TEXT = 0xFFA9C6FF.toInt()
        private const val DEAD_TEXT = 0xFF7D8699.toInt()
        private const val DIM_ARROW = 0x66455068
    }
}
