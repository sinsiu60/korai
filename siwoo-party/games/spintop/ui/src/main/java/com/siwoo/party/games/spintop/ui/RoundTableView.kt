package com.siwoo.party.games.spintop.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 판 결과 표: 판 순위 순서로 한 줄씩 (순위 · 팽이 · 이름 · 이번 판 점수 · 합계).
 * 글자는 [set] 에서 한 번 만든다. onDraw 할당 없음.
 */
class RoundTableView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var order = IntArray(0)
    private var colors = IntArray(0)
    private var rankText = emptyArray<String>()
    private var nameText = emptyArray<String>()
    private var gainText = emptyArray<String>()
    private var totalText = emptyArray<String>()
    private var first = BooleanArray(0)

    private val headH = 24f * d
    private val rowH = 34f * d
    private val tmp = RectF()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f * d; color = Theme.TEXT_SUB }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f * d; typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val right = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f * d; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }

    /** 표 내용 (players 순서의 판 순위·판 점수·합계) */
    fun set(names: List<String>, playerColors: IntArray, ranks: IntArray, points: IntArray, totals: IntArray) {
        val n = names.size
        order = (0 until n).sortedWith(compareBy({ ranks[it] }, { it })).toIntArray()
        colors = playerColors
        rankText = Array(n) { ranks[it].toString() + "위" }
        nameText = Array(n) { names[it] }
        gainText = Array(n) { "+" + points[it] + "점" }
        totalText = Array(n) { totals[it].toString() + "점" }
        first = BooleanArray(n) { ranks[it] == 1 }
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (headH + rowH * order.size + 2f * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val xRank = 6f * d
        val xTop = 54f * d
        val xName = 76f * d
        val xGain = w * 0.72f
        val xTotal = w - 8f * d
        c.drawText(H_RANK, xRank, headH - 8f * d, head)
        c.drawText(H_NAME, xName, headH - 8f * d, head)
        head.textAlign = Paint.Align.RIGHT
        c.drawText(H_GAIN, xGain, headH - 8f * d, head)
        c.drawText(H_TOTAL, xTotal, headH - 8f * d, head)
        head.textAlign = Paint.Align.LEFT
        for (k in order.indices) {
            val i = order[k]
            val top = headH + rowH * k
            val mid = top + rowH / 2f
            p.style = Paint.Style.FILL
            p.color = if (first[i]) 0x40FFC93C else if (k % 2 == 0) 0x22FFFFFF else 0x11FFFFFF
            tmp.set(0f, top + 2f * d, w, top + rowH - 2f * d)
            c.drawRoundRect(tmp, 8f * d, 8f * d, p)
            text.color = if (first[i]) Theme.GOLD else Theme.TEXT
            c.drawText(rankText[i], xRank, mid + 5f * d, text)
            SpinArt.top(c, p, xTop, mid, 10f * d, colors[i], 0.3f + k)
            text.color = Theme.TEXT
            c.drawText(nameText[i], xName, mid + 5f * d, text)
            right.color = Theme.GREEN
            c.drawText(gainText[i], xGain, mid + 5f * d, right)
            right.color = Theme.GOLD
            c.drawText(totalText[i], xTotal, mid + 5f * d, right)
        }
    }

    companion object {
        private const val H_RANK = "판 순위"
        private const val H_NAME = "이름"
        private const val H_GAIN = "이번 판"
        private const val H_TOTAL = "합계"
    }
}
