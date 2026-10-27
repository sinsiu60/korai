package com.siwoo.party.games.shutbox.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.shutbox.GameConfig
import kotlin.math.PI
import kotlin.math.sin

/**
 * 오른쪽 점수표: 줄 = 플레이어, 칸 = 1R·2R·3R·합계 (낮을수록 좋음).
 * 지금 차례 줄은 그 사람 색으로 빛나고, 지금 라운드 칸에는 남은 숫자 합이 흐리게 실시간으로 보인다.
 * 라운드 첫 차례인 사람 이름 옆에 "선" 표시 (라운드마다 돈다). 완벽(0점)은 금색 0 + 별.
 * 끝나면 1등 줄에 왕관. 글자는 값이 바뀔 때만 만든다 (onDraw 할당 없음).
 */
class ScoreTableView(context: Context) : View(context) {

    private var n = 0
    private var names = arrayOf<String>()
    private var initials = arrayOf<String>()
    private var colors = IntArray(0)
    private var scores = Array(0) { IntArray(0) }
    private var cellText = Array(0) { arrayOf<String>() }
    private var totalText = arrayOf<String>()
    private var ranks = IntArray(0)
    private var showRanks = false
    private var current = -1
    private var curRound = 1
    private var starter = -1
    private var liveText = ""
    private var popP = -1
    private var popR = -1
    private var pop = 0f
    private var popAnim: ValueAnimator? = null

    // 배치
    private val area = RectF()
    private var titleH = 0f
    private var headH = 0f
    private var rowH = 0f
    private var rowGap = 0f
    private var rowsTop = 0f
    private var nameW = 0f
    private var cellW = 0f
    private var totalW = 0f
    private var footTop = 0f
    private var circleR = 0f
    private var d = 1f

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB; textAlign = Paint.Align.RIGHT }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB; textAlign = Paint.Align.CENTER }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val nameSize = FloatArray(4)
    private var rankText = arrayOf<String>()

    /** 플레이어 이름·색 */
    fun bind(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size
        names = playerNames.toTypedArray()
        initials = Array(n) { names[it].take(1) }
        colors = playerColors
        scores = Array(n) { IntArray(GameConfig.ROUNDS) { EMPTY } }
        cellText = Array(n) { Array(GameConfig.ROUNDS) { DOT } }
        totalText = Array(n) { ZERO }
        ranks = IntArray(n)
        rankText = Array(n) { "" }
        fitNames()
        invalidate()
    }

    /** 라운드 점수 기록 (round 1부터) */
    fun setScore(p: Int, round: Int, value: Int) {
        scores[p][round - 1] = value
        cellText[p][round - 1] = value.toString()
        var s = 0
        for (v in scores[p]) if (v > 0) s += v
        totalText[p] = s.toString()
        invalidate()
    }

    fun score(p: Int, round: Int): Int = scores[p][round - 1]

    /** 지금 차례 (줄 강조) + 라운드 (-1 = 없음) */
    fun setCurrent(p: Int, round: Int) {
        current = p
        curRound = round
        invalidate()
    }

    /** 지금 라운드 칸에 흐리게 보일 남은 합 (-1 = 안 보임) */
    fun setLive(value: Int) {
        liveText = if (value < 0) "" else value.toString()
        invalidate()
    }

    /** 이번 라운드 첫 차례인 사람 ("선" 표시) */
    fun setStarter(p: Int) {
        starter = p
        invalidate()
    }

    /** 순위 표시 (1등 왕관) */
    fun showRanks(r: IntArray) {
        ranks = r
        rankText = Array(n) { r[it].toString() + RANK_SUFFIX }
        showRanks = true
        current = -1
        liveText = ""
        invalidate()
    }

    /** 칸 숫자가 톡 커졌다 돌아온다 */
    fun pop(p: Int, round: Int, ms: Long) {
        popP = p
        popR = round - 1
        popAnim?.cancel()
        popAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms.coerceAtLeast(1)
            addUpdateListener { pop = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** p 줄 round 칸 가운데 (이 View 좌표, round 0 = 합계 칸) */
    fun cellCenter(p: Int, round: Int, out: PointF) {
        val y = rowsTop + p * (rowH + rowGap) + rowH / 2f
        val x = if (round <= 0) area.left + nameW + cellW * GameConfig.ROUNDS + totalW / 2f
        else area.left + nameW + cellW * (round - 1) + cellW / 2f
        out.set(x, y)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        d = Theme.dpf(context, 1f)
        area.set(d * 4f, d * 4f, w - d * 4f, h - d * 4f)
        titleH = d * 28f
        headH = d * 22f
        val footH = d * 62f
        footTop = area.bottom - footH
        rowGap = d * 5f
        val avail = footTop - d * 6f - (area.top + titleH + headH)
        rowH = minOf((avail - rowGap * (maxOf(n, 1) - 1)) / maxOf(n, 1), d * 62f)
        rowsTop = area.top + titleH + headH
        nameW = area.width() * 0.44f
        totalW = area.width() * 0.17f
        circleR = minOf(rowH * 0.27f, d * 13f)
        cellW = (area.width() - nameW - totalW) / GameConfig.ROUNDS
        titlePaint.textSize = d * 16f
        subPaint.textSize = d * 11f
        headPaint.textSize = d * 11.5f
        cellPaint.textSize = minOf(rowH * 0.4f, d * 19f)
        totalPaint.textSize = minOf(rowH * 0.44f, d * 21f)
        initPaint.textSize = minOf(circleR * 1.05f, d * 14f)
        badgePaint.textSize = d * 10f
        footPaint.textSize = d * 11f
        rankPaint.textSize = d * 11f
        stroke.strokeWidth = d * 2f
        fitNames()
    }

    private fun fitNames() {
        if (rowH <= 0f || n == 0) return
        val base = minOf(rowH * 0.32f, d * 15f)
        // 이름 칸 = 왼쪽 여백 + 동그라미 + 틈을 뺀 나머지
        val max = nameW - d * 12f - circleR * 2f - d * 6f - d * 2f
        for (i in 0 until n) {
            var s = base
            namePaint.textSize = s
            while (namePaint.measureText(names[i]) > max && s > base * 0.55f) {
                s *= 0.92f
                namePaint.textSize = s
            }
            nameSize[i] = s
        }
    }

    override fun onDraw(c: Canvas) {
        if (n == 0 || rowH <= 0f) return
        // 판 바탕
        fill.color = Theme.CARD
        rect.set(area)
        c.drawRoundRect(rect, d * 14f, d * 14f, fill)
        stroke.color = Theme.CARD_BORDER
        stroke.strokeWidth = d
        c.drawRoundRect(rect, d * 14f, d * 14f, stroke)
        // 제목
        c.drawText(TITLE, area.left + d * 10f, area.top + titleH * 0.72f, titlePaint)
        c.drawText(SUB, area.right - d * 10f, area.top + titleH * 0.72f, subPaint)
        // 머리줄
        val hy = area.top + titleH + headH * 0.68f
        for (r in 0 until GameConfig.ROUNDS) {
            headPaint.color = if (!showRanks && r == curRound - 1) Theme.GOLD else Theme.TEXT_SUB
            c.drawText(HEADS[r], area.left + nameW + cellW * r + cellW / 2f, hy, headPaint)
        }
        headPaint.color = Theme.TEXT_SUB
        c.drawText(HEAD_TOTAL, area.left + nameW + cellW * GameConfig.ROUNDS + totalW / 2f, hy, headPaint)
        // 줄
        for (p in 0 until n) drawRow(c, p)
        // 아래 규칙 요약
        fill.color = 0x26000000
        rect.set(area.left + d * 6f, footTop, area.right - d * 6f, area.bottom - d * 6f)
        c.drawRoundRect(rect, d * 10f, d * 10f, fill)
        val fx = rect.left + d * 8f
        val lh = rect.height() / 3f
        for (k in FOOT.indices) c.drawText(FOOT[k], fx, rect.top + lh * k + lh * 0.68f, footPaint)
    }

    private fun drawRow(c: Canvas, p: Int) {
        val top = rowsTop + p * (rowH + rowGap)
        rect.set(area.left + d * 6f, top, area.right - d * 6f, top + rowH)
        val cur = p == current
        val win = showRanks && ranks[p] == 1
        fill.color = if (cur) colors[p] else Theme.CARD_LIGHT
        fill.alpha = if (cur) 0x60 else 0xC0
        c.drawRoundRect(rect, d * 10f, d * 10f, fill)
        fill.alpha = 255
        if (cur || win) {
            stroke.color = if (win) Theme.GOLD else colors[p]
            stroke.strokeWidth = d * 2.5f
            c.drawRoundRect(rect, d * 10f, d * 10f, stroke)
        }
        val cy = top + rowH / 2f
        // 색 동그라미 + 첫 글자 (1등은 왕관)
        val cr = circleR
        val ccx = rect.left + d * 6f + cr
        fill.color = colors[p]
        c.drawCircle(ccx, cy, cr, fill)
        initPaint.color = Theme.textOn(colors[p])
        c.drawText(initials[p], ccx, cy + initPaint.textSize * 0.36f, initPaint)
        if (win) Ui.drawCrown(c, ccx, cy - cr + d * 2f, cr * 1.5f, path, crownPaint)
        // 이름
        namePaint.textSize = nameSize[p]
        val nx = ccx + cr + d * 6f
        c.drawText(names[p], nx, cy + namePaint.textSize * 0.36f, namePaint)
        // "선" (이번 라운드 첫 차례) — 색 동그라미 왼쪽 위에 작은 금색 배지
        if (!showRanks && p == starter) {
            val bx = ccx - cr * 0.85f
            val by = cy - cr * 0.85f
            fill.color = Theme.GOLD
            c.drawCircle(bx, by, d * 7f, fill)
            stroke.color = Theme.INK
            stroke.strokeWidth = d
            c.drawCircle(bx, by, d * 7f, stroke)
            c.drawText(BADGE_FIRST, bx, by + badgePaint.textSize * 0.36f, badgePaint)
        }
        // 라운드 칸
        for (r in 0 until GameConfig.ROUNDS) {
            val x = area.left + nameW + cellW * r + cellW / 2f
            val v = scores[p][r]
            if (v == EMPTY) {
                if (cur && r == curRound - 1 && liveText.isNotEmpty()) {
                    cellPaint.color = LIVE
                    c.drawText(liveText, x, cy + cellPaint.textSize * 0.36f, cellPaint)
                } else {
                    cellPaint.color = 0x66B8C3F0
                    c.drawText(DOT, x, cy + cellPaint.textSize * 0.36f, cellPaint)
                }
                continue
            }
            val sc = if (p == popP && r == popR) 1f + 0.45f * sin(pop * PI.toFloat()) else 1f
            cellPaint.color = if (v == 0) Theme.GOLD else Theme.TEXT
            c.save()
            c.scale(sc, sc, x, cy)
            c.drawText(cellText[p][r], x, cy + cellPaint.textSize * 0.36f, cellPaint)
            if (v == 0) drawStar(c, x + cellPaint.textSize * 0.5f, cy - cellPaint.textSize * 0.45f, d * 4.5f)
            c.restore()
        }
        // 합계
        val tx = area.left + nameW + cellW * GameConfig.ROUNDS + totalW / 2f
        c.drawText(totalText[p], tx, cy + totalPaint.textSize * 0.36f - (if (showRanks) d * 4f else 0f), totalPaint)
        if (showRanks) c.drawText(rankText[p], tx, top + rowH - d * 3f, rankPaint)
    }

    /** 작은 반짝 별 (완벽) — 마름모 두 개 */
    private fun drawStar(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = Theme.GOLD_LIGHT
        c.drawOval(x - r * 0.3f, y - r, x + r * 0.3f, y + r, fill)
        c.drawOval(x - r, y - r * 0.3f, x + r, y + r * 0.3f, fill)
    }

    override fun onDetachedFromWindow() {
        popAnim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        const val EMPTY = -1
        private const val TITLE = "점수표"
        private const val SUB = "낮을수록 좋아요"
        private const val HEAD_TOTAL = "합계"
        private val HEADS = arrayOf("1R", "2R", "3R")
        private const val DOT = "·"
        private const val ZERO = "0"
        private const val BADGE_FIRST = "선"
        private const val RANK_SUFFIX = "위"
        private const val LIVE = 0x99FFE08A.toInt()
        private val FOOT = arrayOf(
            "• 주사위 합 = 고른 숫자 합 → 닫기",
            "• 더 못 닫으면 남은 숫자 합이 점수",
            "• 남은 합 6 이하면 주사위 1개도 OK",
        )
    }
}
