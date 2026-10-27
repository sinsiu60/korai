package com.siwoo.party.games.horserace.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.horserace.RaceResult

/**
 * 레이스 사이 결과표 (팝업 안).
 * 위: [1착] 번호 패 "3번 말"  [2착] 번호 패 "5번 말"
 * 표: 순위(1등 왕관) · 이름 · 걸음 · 받음 · 손익(초록/빨강) · 칩 — 칩 많은 순.
 * 글자는 set 때만 만든다.
 */
class StandingsView(context: Context) : View(context) {

    private val painter = HorsePainter()
    private val d = Theme.dpf(context, 1f)
    private var first = 0
    private var second = 1
    private var order = IntArray(0)
    private var rankText: Array<String> = emptyArray()
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private var stakeText: Array<String> = emptyArray()
    private var payText: Array<String> = emptyArray()
    private var netText: Array<String> = emptyArray()
    private var netSign = IntArray(0)
    private var chipText: Array<String> = emptyArray()
    private var ranks = IntArray(0)

    private val rect = RectF()
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val horsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB; textAlign = Paint.Align.RIGHT }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT; textAlign = Paint.Align.RIGHT }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD; textAlign = Paint.Align.RIGHT }
    private val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT; textAlign = Paint.Align.CENTER }

    init {
        horsePaint.textSize = d * 15f
        pillPaint.textSize = d * 13f
        headPaint.textSize = d * 11f
        cellPaint.textSize = d * 14f
        namePaint.textSize = d * 15f
        chipPaint.textSize = d * 17f
        rankPaint.textSize = d * 15f
    }

    /** 필요한 높이 (px) */
    fun desiredHeight(players: Int): Int = (d * (TOP_H + HEAD_H + ROW_H * players + 4f)).toInt()

    fun set(result: RaceResult, playerNames: List<String>, playerColors: IntArray, rankArr: IntArray) {
        first = result.first
        second = result.second
        val n = playerNames.size
        ranks = rankArr.copyOf()
        order = (0 until n).sortedWith(compareBy({ rankArr[it] }, { it })).toIntArray()
        rankText = Array(n) { rankArr[it].toString() }
        names = Array(n) { playerNames[it] }
        colors = playerColors.copyOf()
        stakeText = Array(n) { result.stake[it].toString() }
        payText = Array(n) { result.payout[it].toString() }
        netSign = IntArray(n) { Integer.signum(result.net(it)) }
        netText = Array(n) { val v = result.net(it); if (v > 0) "+$v" else if (v < 0) "−" + (-v) else "0" }
        chipText = Array(n) { result.chipsAfter[it].toString() + "칩" }
        requestLayout()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (order.isEmpty()) return
        val w = width.toFloat()
        // 1착·2착
        drawPlace(c, 0f, FIRST, Theme.GOLD, first)
        drawPlace(c, w * 0.5f, SECOND, RaceView.SILVER, second)
        // 표 머리
        val hy = d * (TOP_H + HEAD_H - 5f)
        c.drawText(H_STAKE, w * COL_STAKE, hy, headPaint)
        c.drawText(H_PAY, w * COL_PAY, hy, headPaint)
        c.drawText(H_NET, w * COL_NET, hy, headPaint)
        c.drawText(H_CHIPS, w * COL_CHIPS - d * 6f, hy, headPaint)
        for (row in order.indices) {
            val p = order[row]
            val top = d * (TOP_H + HEAD_H) + row * d * ROW_H
            rect.set(0f, top + d * 2f, w, top + d * (ROW_H - 2f))
            fill.color = if (ranks[p] == 1) 0x33FFC93C else 0x22FFFFFF
            c.drawRoundRect(rect, d * 8f, d * 8f, fill)
            val base = rect.centerY() + cellPaint.textSize * 0.36f
            // 순위
            val rx = w * 0.045f
            if (ranks[p] == 1) Ui.drawCrown(c, rx, rect.centerY() + d * 7f, d * 20f, path, fill)
            else c.drawText(rankText[p], rx, rect.centerY() + rankPaint.textSize * 0.36f, rankPaint)
            fill.color = colors[p]
            c.drawCircle(w * 0.115f, rect.centerY(), d * 6f, fill)
            c.drawText(names[p], w * 0.145f, rect.centerY() + namePaint.textSize * 0.36f, namePaint)
            cellPaint.color = Theme.TEXT_SUB
            c.drawText(stakeText[p], w * COL_STAKE, base, cellPaint)
            cellPaint.color = Theme.TEXT
            c.drawText(payText[p], w * COL_PAY, base, cellPaint)
            cellPaint.color = when (netSign[p]) { 1 -> Theme.GREEN; -1 -> Theme.RED; else -> Theme.TEXT_SUB }
            c.drawText(netText[p], w * COL_NET, base, cellPaint)
            c.drawText(chipText[p], w * COL_CHIPS - d * 6f, rect.centerY() + chipPaint.textSize * 0.36f, chipPaint)
        }
    }

    private fun drawPlace(c: Canvas, x: Float, label: String, color: Int, horse: Int) {
        val cy = d * TOP_H * 0.45f
        rect.set(x + d * 4f, cy - d * 11f, x + d * 44f, cy + d * 11f)
        fill.color = color
        c.drawRoundRect(rect, d * 11f, d * 11f, fill)
        c.drawText(label, rect.centerX(), cy + pillPaint.textSize * 0.36f, pillPaint)
        if (horse < 0) return
        painter.badge(c, x + d * 64f, cy, d * 24f, horse)
        c.drawText(HORSE_NAMES[horse], x + d * 82f, cy + horsePaint.textSize * 0.36f, horsePaint)
    }

    companion object {
        private const val TOP_H = 36f
        private const val HEAD_H = 18f
        private const val ROW_H = 34f
        private const val COL_STAKE = 0.53f
        private const val COL_PAY = 0.66f
        private const val COL_NET = 0.79f
        private const val COL_CHIPS = 1f
        private const val FIRST = "1착"
        private const val SECOND = "2착"
        private const val H_STAKE = "걸음"
        private const val H_PAY = "받음"
        private const val H_NET = "손익"
        private const val H_CHIPS = "칩"
        private val HORSE_NAMES = Array(6) { (it + 1).toString() + "번 말" }
    }
}
