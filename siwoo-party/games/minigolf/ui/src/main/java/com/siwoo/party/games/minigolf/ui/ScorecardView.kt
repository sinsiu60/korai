package com.siwoo.party.games.minigolf.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.minigolf.GameConfig
import com.siwoo.party.games.minigolf.MinigolfGame

/**
 * 점수표: 위 두 줄 = 홀 번호·파, 아래 = 사람마다 홀별 타수 + 합계(파와의 차이). 끝낸 홀까지만 숫자.
 * 홀인원 금색, 파보다 적으면 초록, 많으면 분홍, 최대 타수(못 넣음)는 회색 칸. 지금 홀 칸은 금색 테두리, 1위 이름 옆에 왕관.
 * 글자는 refresh() 에서만 만든다 (onDraw 할당 없음).
 */
class ScorecardView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var game: MinigolfGame? = null
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private var highlight = -1

    private var holeLabels: Array<String> = emptyArray()
    private var parLabels: Array<String> = emptyArray()
    private var totalLabels: Array<String> = emptyArray()
    private var diffLabels: Array<String> = emptyArray()
    private var parTotal = ""
    private var leaders = BooleanArray(0)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val nameText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val rect = RectF()
    private val crown = android.graphics.Path()

    /** 표 높이 (dp) — 팝업 크기용 */
    fun heightDp(players: Int): Float = ROW_DP * (2 + players) + 4f

    fun bind(g: MinigolfGame, playerNames: List<String>, playerColors: IntArray) {
        game = g
        names = Array(playerNames.size) { ellipsize(playerNames[it]) }
        colors = playerColors
        holeLabels = Array(g.holes.size) { (it + 1).toString() }
        parLabels = Array(g.holes.size) { g.holes[it].par.toString() }
        refresh()
    }

    /** 점수가 바뀐 뒤 (지금 홀 = highlightHole, 없으면 −1) */
    fun refresh(highlightHole: Int = highlight) {
        val g = game ?: return
        highlight = highlightHole
        totalLabels = Array(g.playerCount) { g.total(it).toString() }
        diffLabels = Array(g.playerCount) {
            val played = g.parSoFar(it)
            if (played == 0) "" else {
                val diff = g.total(it) - played
                if (diff == 0) "±0" else if (diff > 0) "+$diff" else diff.toString()
            }
        }
        parTotal = g.totalPar.toString()
        val anyPlayed = (0 until g.playerCount).any { p -> g.scores[p].any { it > 0 } }
        val ranks = g.ranks()
        leaders = BooleanArray(g.playerCount) { anyPlayed && ranks[it] == 1 }
        invalidate()
    }

    private fun ellipsize(s: String): String = if (s.length > 4) s.take(4) + "…" else s

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val w = width.toFloat()
        val n = g.holes.size
        val rowH = ROW_DP * d
        val nameW = 84f * d
        val totalW = 64f * d
        val cellW = (w - nameW - totalW) / n
        text.textSize = 14f * d
        small.textSize = 10f * d
        nameText.textSize = 13f * d
        stroke.strokeWidth = 1f * d
        // 머리 두 줄
        fill.color = 0x33FFFFFF
        rect.set(0f, 0f, w, rowH * 2f)
        c.drawRoundRect(rect, 8f * d, 8f * d, fill)
        nameText.color = Theme.GOLD
        c.drawText(HOLE, 8f * d, rowH * 0.65f, nameText)
        nameText.color = Theme.TEXT_SUB
        c.drawText(PAR, 8f * d, rowH * 1.65f, nameText)
        for (h in 0 until n) {
            val cx = nameW + cellW * (h + 0.5f)
            text.color = Theme.GOLD
            c.drawText(holeLabels[h], cx, rowH * 0.68f, text)
            text.color = Theme.TEXT_SUB
            c.drawText(parLabels[h], cx, rowH * 1.68f, text)
        }
        text.color = Theme.GOLD
        c.drawText(TOTAL, w - totalW / 2f, rowH * 0.68f, text)
        text.color = Theme.TEXT_SUB
        c.drawText(parTotal, w - totalW / 2f, rowH * 1.68f, text)
        // 사람마다
        for (p in 0 until g.playerCount) {
            val top = rowH * (2 + p)
            if (p % 2 == 0) {
                fill.color = 0x14FFFFFF
                c.drawRect(0f, top, w, top + rowH, fill)
            }
            fill.color = colors[p]
            c.drawCircle(12f * d, top + rowH / 2f, 6f * d, fill)
            nameText.color = Theme.TEXT
            c.drawText(names[p], 23f * d, top + rowH * 0.66f, nameText)
            if (leaders[p]) {
                fill.color = Theme.GOLD
                val nx = 23f * d + nameText.measureText(names[p]) + 9f * d
                if (nx < nameW - 2f * d) com.siwoo.party.common.ui.Ui.drawCrown(c, nx, top + rowH * 0.62f, 12f * d, crown, fill)
            }
            for (h in 0 until n) {
                val s = g.scores[p][h]
                val l = nameW + cellW * h
                if (s > 0) {
                    val par = g.holes[h].par
                    val holed = g.holedAt(p, h)
                    fill.color = when {
                        !holed -> 0xFF5A607A.toInt()
                        s == 1 -> Theme.GOLD
                        s < par -> 0xFF2ECC71.toInt()
                        s == par -> 0x33FFFFFF
                        else -> 0xFFE0607A.toInt()
                    }
                    rect.set(l + 3f * d, top + 3f * d, l + cellW - 3f * d, top + rowH - 3f * d)
                    c.drawRoundRect(rect, 6f * d, 6f * d, fill)
                    text.color = if (s == 1) Theme.INK else Theme.TEXT
                    c.drawText(DIGITS[s.coerceAtMost(DIGITS.size - 1)], l + cellW / 2f, top + rowH * 0.68f, text)
                } else {
                    small.color = 0x55FFFFFF
                    c.drawText(DASH, l + cellW / 2f, top + rowH * 0.64f, small)
                    small.color = Theme.TEXT_SUB
                }
            }
            text.color = Theme.TEXT
            c.drawText(totalLabels[p], w - totalW / 2f - 10f * d, top + rowH * 0.68f, text)
            small.color = Theme.TEXT_SUB
            c.drawText(diffLabels[p], w - 13f * d, top + rowH * 0.64f, small)
        }
        // 지금 홀 칸
        if (highlight in 0 until n) {
            stroke.color = Theme.GOLD
            stroke.strokeWidth = 2f * d
            val l = nameW + cellW * highlight
            rect.set(l + 1f * d, 1f * d, l + cellW - 1f * d, rowH * (2 + g.playerCount) - 1f * d)
            c.drawRoundRect(rect, 6f * d, 6f * d, stroke)
        }
        // 합계 칸 구분선
        stroke.color = 0x44FFFFFF
        stroke.strokeWidth = 1f * d
        c.drawLine(w - totalW, rowH * 0.2f, w - totalW, rowH * (2 + g.playerCount) - rowH * 0.2f, stroke)
    }

    companion object {
        const val ROW_DP = 30f
        private const val HOLE = "홀"
        private const val PAR = "파"
        private const val TOTAL = "합계"
        private const val DASH = "–"
        private val DIGITS = Array(GameConfig.MAX_STROKES + 1) { it.toString() }
    }
}
