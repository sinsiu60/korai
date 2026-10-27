package com.siwoo.party.games.archery.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.archery.ArcheryGame
import com.siwoo.party.games.archery.GameConfig
import com.siwoo.party.games.archery.Target

/**
 * 점수표: 머리 줄(1~6발 · 합계 · X · 순위) + 사람마다 화살 점수 칸(고리 색) · 합계 · X 개수 · 순위. 1위 이름 옆에 왕관.
 * 글자는 refresh() 에서만 만든다 (onDraw 할당 없음).
 */
class ScoreTableView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var game: ArcheryGame? = null
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)

    private var totalLabels: Array<String> = emptyArray()
    private var xLabels: Array<String> = emptyArray()
    private var rankLabels: Array<String> = emptyArray()
    private var leaders = BooleanArray(0)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val nameText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val rect = RectF()
    private val crown = Path()

    /** 표 높이 (dp) — 팝업 크기용 */
    fun heightDp(players: Int): Float = ROW_DP * (1 + players) + 4f

    fun bind(g: ArcheryGame, playerNames: List<String>, playerColors: IntArray) {
        game = g
        names = Array(playerNames.size) { ellipsize(playerNames[it]) }
        colors = playerColors
        refresh()
    }

    fun refresh() {
        val g = game ?: return
        val ranks = g.ranks()
        val any = g.shotIndex > 0 || g.phase != com.siwoo.party.games.archery.Phase.AIM
        totalLabels = Array(g.players) { g.totals[it].toString() }
        xLabels = Array(g.players) { g.xCount[it].toString() }
        rankLabels = Array(g.players) { if (any) ranks[it].toString() + "위" else "–" }
        leaders = BooleanArray(g.players) { any && ranks[it] == 1 }
        invalidate()
    }

    /** 표에 보이는 합계 (테스트용) */
    fun shownTotal(p: Int): String = totalLabels.getOrElse(p) { "" }

    private fun ellipsize(s: String): String = if (s.length > 4) s.take(4) + "…" else s

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val w = width.toFloat()
        val rowH = ROW_DP * d
        val nameW = 96f * d
        val tailW = 150f * d
        val n = GameConfig.ARROWS
        val cellW = (w - nameW - tailW) / n
        val totalX = w - tailW + tailW * 0.2f
        val xX = w - tailW + tailW * 0.52f
        val rankX = w - tailW + tailW * 0.82f
        text.textSize = 14f * d
        nameText.textSize = 14f * d
        stroke.strokeWidth = 1f * d
        // 머리 줄
        fill.color = 0x33FFFFFF
        rect.set(0f, 0f, w, rowH)
        c.drawRoundRect(rect, 8f * d, 8f * d, fill)
        nameText.color = Theme.GOLD
        c.drawText(ARROW, 10f * d, rowH * 0.66f, nameText)
        text.color = Theme.GOLD
        for (k in 0 until n) c.drawText(NUMS[k], nameW + cellW * (k + 0.5f), rowH * 0.66f, text)
        c.drawText(TOTAL, totalX, rowH * 0.66f, text)
        c.drawText(XS, xX, rowH * 0.66f, text)
        c.drawText(RANK, rankX, rowH * 0.66f, text)
        // 사람마다
        for (p in 0 until g.players) {
            val top = rowH * (1 + p)
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
                val nx = 23f * d + nameText.measureText(names[p]) + 10f * d
                if (nx < nameW - 2f * d) Ui.drawCrown(c, nx, top + rowH * 0.62f, 13f * d, crown, fill)
            }
            val list = g.shots[p]
            val cr = minOf(cellW * 0.4f, rowH * 0.38f)
            for (k in 0 until n) {
                val cx = nameW + cellW * (k + 0.5f)
                val cy = top + rowH / 2f
                val s = list[k]
                if (s == null) {
                    stroke.color = 0x44FFFFFF
                    c.drawCircle(cx, cy, cr * 0.8f, stroke)
                } else {
                    fill.color = ArcheryArt.scoreColor(s.score)
                    c.drawCircle(cx, cy, cr, fill)
                    stroke.color = if (s.isX) Theme.GOLD else 0x66FFFFFF
                    c.drawCircle(cx, cy, cr, stroke)
                    text.color = ArcheryArt.scoreInk(s.score)
                    text.textSize = cr * 0.95f
                    c.drawText(Target.LABELS[Target.index(s.score, s.isX)], cx, cy + text.textSize * 0.36f, text)
                    text.textSize = 14f * d
                }
            }
            text.color = Theme.TEXT
            c.drawText(totalLabels[p], totalX, top + rowH * 0.66f, text)
            text.color = Theme.GOLD_LIGHT
            c.drawText(xLabels[p], xX, top + rowH * 0.66f, text)
            text.color = if (leaders[p]) Theme.GOLD else Theme.TEXT_SUB
            c.drawText(rankLabels[p], rankX, top + rowH * 0.66f, text)
        }
        stroke.color = 0x44FFFFFF
        c.drawLine(w - tailW, rowH * 0.2f, w - tailW, rowH * (1 + g.players) - rowH * 0.2f, stroke)
    }

    companion object {
        const val ROW_DP = 34f
        private const val ARROW = "발"
        private const val TOTAL = "합계"
        private const val XS = "X"
        private const val RANK = "순위"
        private val NUMS = Array(GameConfig.ARROWS) { (it + 1).toString() }
    }
}
