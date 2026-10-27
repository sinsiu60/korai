package com.siwoo.party.games.doubt.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.ui.Theme

/**
 * 카드 테이블 아래 바닥 층 (터치는 받지 않음, 카드보다 아래에 그린다):
 * - 가운데 둥근 펠트 판
 * - 선언 순서 칩 A 2 3 … K (지금 숫자는 금색으로 크게, K 다음은 다시 A)
 * - 더미 왼쪽 "이번 숫자" 종이 카드, 오른쪽 "더미 N장"
 * - 오른쪽 위 "남은 차례 N" (5 이하 빨강)
 * - 공개 때 카드 아래 ✓(진짜)·✗(거짓) 표시
 * 글자는 값이 바뀔 때만 만든다 → onDraw 할당 없음. 움직임이 없으므로 값이 바뀔 때만 다시 그린다.
 */
class TableInfoView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    // ── 상태 ──
    private var rank = 1
    private var rankShown = false
    private var pileX = 0f
    private var pileY = 0f
    private var cardW = 0f
    private var cardH = 0f
    private var pileCount = 0
    private var pileText = "0장"
    private var turnsLeft = 0
    private var turnsText = ""
    private var seqLeft = 0f
    private var seqRight = 0f
    private var seqTop = 0f
    private var feltOn = true

    private val markX = FloatArray(MAX_MARKS)
    private val markY = FloatArray(MAX_MARKS)
    private val markOk = BooleanArray(MAX_MARKS)
    private var marks = 0

    /** 지금 보이는 선언 숫자 (테스트용) */
    val rankOnScreen: Int get() = rank
    /** 지금 보이는 더미 장수 (테스트용) */
    val pileShown: Int get() = pileCount
    /** 지금 보이는 남은 차례 (테스트용) */
    val turnsShown: Int get() = turnsLeft
    /** ✓·✗ 표시 수 (테스트용) */
    val markCount: Int get() = marks

    // ── 그리기 도구 ──
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val chipText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB; typeface = Typeface.DEFAULT_BOLD }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT; typeface = Typeface.DEFAULT_BOLD }
    private val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.INK; typeface = Typeface.DEFAULT_BOLD }
    private val rect = RectF()
    private var felt: Shader? = null
    private val feltRect = RectF()

    init {
        isClickable = false
        isFocusable = false
        small.textSize = 11f * density
        big.textSize = 18f * density
        chipText.textSize = 12f * density
    }

    // ── 값 바꾸기 (글자는 여기서만 만든다) ──

    /** 더미 자리·카드 크기 (px) */
    fun setPile(x: Float, y: Float, cardWidth: Float, cardHeight: Float) {
        pileX = x; pileY = y; cardW = cardWidth; cardH = cardHeight
        rankPaint.textSize = cardW * 0.62f
        rebuildFelt()
        invalidate()
    }

    /** 선언 순서 칩 자리 (px) */
    fun setSequenceArea(left: Float, top: Float, right: Float) {
        seqLeft = left; seqTop = top; seqRight = right
        invalidate()
    }

    fun setRank(r: Int) {
        if (rank == r && rankShown) return
        rank = r
        rankShown = true
        invalidate()
    }

    fun setPileCount(n: Int) {
        if (pileCount == n && pileText.isNotEmpty()) return
        pileCount = n
        pileText = n.toString() + "장"
        invalidate()
    }

    fun setTurnsLeft(n: Int) {
        if (turnsLeft == n && turnsText.isNotEmpty()) return
        turnsLeft = n
        turnsText = "남은 차례 $n"
        invalidate()
    }

    /** 공개한 카드 아래 ✓·✗ (화면 좌표 px) */
    fun setMark(i: Int, x: Float, y: Float, ok: Boolean) {
        if (i !in 0 until MAX_MARKS) return
        markX[i] = x; markY[i] = y; markOk[i] = ok
        if (i >= marks) marks = i + 1
        invalidate()
    }

    fun clearMarks() {
        if (marks == 0) return
        marks = 0
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildFelt()
    }

    /** 펠트 판 (크기·자리가 바뀔 때만 Shader 를 만든다) */
    private fun rebuildFelt() {
        if (width == 0 || cardW <= 0f) { felt = null; return }
        val rx = width * 0.34f
        val ry = cardH * 1.45f
        feltRect.set(pileX - rx, pileY - ry, pileX + rx, pileY + ry)
        felt = RadialGradient(pileX, pileY, rx, FELT_IN, FELT_OUT, Shader.TileMode.CLAMP)
    }

    // ── 그리기 (할당 없음) ──

    override fun onDraw(c: Canvas) {
        drawFelt(c)
        drawSequence(c)
        if (cardW > 0f) {
            drawRankCard(c)
            drawPileLabel(c)
        }
        if (turnsText.isNotEmpty() && seqRight > 0f) {
            big.textAlign = Paint.Align.RIGHT
            big.textSize = 14f * density
            big.color = if (turnsLeft <= LOW_TURNS) Theme.RED else Theme.GOLD_LIGHT
            c.drawText(turnsText, seqRight, seqTop + 52f * density, big)
            big.textAlign = Paint.Align.CENTER
            big.textSize = 18f * density
            big.color = Theme.TEXT
        }
        for (i in 0 until marks) drawMark(c, markX[i], markY[i], markOk[i])
    }

    private fun drawFelt(c: Canvas) {
        val sh = felt ?: return
        if (!feltOn) return
        val r = feltRect.height() / 2f
        fill.shader = sh
        fill.alpha = 255
        c.drawRoundRect(feltRect, r, r, fill)
        fill.shader = null
        stroke.color = Theme.GOLD_DARK
        stroke.alpha = 120
        stroke.strokeWidth = 2f * density
        c.drawRoundRect(feltRect, r, r, stroke)
        stroke.alpha = 255
    }

    private fun drawSequence(c: Canvas) {
        if (seqRight <= seqLeft) return
        val n = 13
        val gap = 4f * density
        val cw = ((seqRight - seqLeft) - gap * (n - 1)) / n
        val ch = 30f * density
        val rr = 6f * density
        for (k in 0 until n) {
            val r = k + 1
            val x = seqLeft + k * (cw + gap)
            val cur = rankShown && r == rank
            val grow = if (cur) 4f * density else 0f
            rect.set(x - grow * 0.4f, seqTop - grow, x + cw + grow * 0.4f, seqTop + ch + grow)
            fill.color = if (cur) Theme.GOLD else Theme.CARD
            c.drawRoundRect(rect, rr, rr, fill)
            stroke.color = if (cur) Theme.GOLD_LIGHT else Theme.CARD_LIGHT
            stroke.strokeWidth = 1.5f * density
            c.drawRoundRect(rect, rr, rr, stroke)
            chipText.color = if (cur) Theme.INK else Theme.TEXT_SUB
            chipText.textSize = (if (cur) 15f else 12f) * density
            c.drawText(Card.RANK_LABELS[k], rect.centerX(), rect.centerY() + chipText.textSize * 0.36f, chipText)
        }
    }

    /** 더미 왼쪽: 이번 숫자 종이 카드 */
    private fun drawRankCard(c: Canvas) {
        if (!rankShown) return
        val w = cardW * PILE_SCALE
        val h = cardH * PILE_SCALE
        val cx = pileX - w * 1.55f
        val cy = pileY
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        val r = w * 0.12f
        fill.color = 0x55000000
        rect.offset(2f * density, 3f * density)
        c.drawRoundRect(rect, r, r, fill)
        rect.offset(-2f * density, -3f * density)
        fill.color = Theme.PAPER
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = 3f * density
        c.drawRoundRect(rect, r, r, stroke)
        c.drawText(Card.RANK_LABELS[rank - 1], cx, cy + rankPaint.textSize * 0.36f, rankPaint)
        small.color = Theme.GOLD_LIGHT
        c.drawText(RANK_CAPTION, cx, rect.top - 7f * density, small)
        small.color = Theme.TEXT_SUB
    }

    /** 더미 오른쪽: "더미" + "N장" */
    private fun drawPileLabel(c: Canvas) {
        val x = pileX + cardW * PILE_SCALE * 0.95f
        small.textAlign = Paint.Align.LEFT
        big.textAlign = Paint.Align.LEFT
        c.drawText(PILE_CAPTION, x, pileY - 4f * density, small)
        c.drawText(if (pileCount == 0) EMPTY_TEXT else pileText, x, pileY + 18f * density, big)
        small.textAlign = Paint.Align.CENTER
        big.textAlign = Paint.Align.CENTER
    }

    private fun drawMark(c: Canvas, x: Float, y: Float, ok: Boolean) {
        val s = 11f * density
        fill.color = 0x66000000
        c.drawCircle(x + density, y + 2f * density, s, fill)
        fill.color = if (ok) Theme.GREEN else Theme.RED
        c.drawCircle(x, y, s, fill)
        stroke.color = Theme.TEXT
        stroke.strokeWidth = 3f * density
        if (ok) {
            c.drawLine(x - s * 0.45f, y, x - s * 0.1f, y + s * 0.38f, stroke)
            c.drawLine(x - s * 0.1f, y + s * 0.38f, x + s * 0.48f, y - s * 0.35f, stroke)
        } else {
            val d = s * 0.4f
            c.drawLine(x - d, y - d, x + d, y + d, stroke)
            c.drawLine(x + d, y - d, x - d, y + d, stroke)
        }
    }

    companion object {
        const val MAX_MARKS = 4
        /** 더미 카드 크기 배율 (DoubtActivity 와 같게) */
        const val PILE_SCALE = 0.85f
        /** 남은 차례가 이 이하면 빨강 */
        const val LOW_TURNS = 5
        private const val RANK_CAPTION = "이번 숫자"
        private const val PILE_CAPTION = "더미"
        private const val EMPTY_TEXT = "없음"
        private const val FELT_IN = 0xFF1E5A63.toInt()
        private const val FELT_OUT = 0xFF123844.toInt()
    }
}
