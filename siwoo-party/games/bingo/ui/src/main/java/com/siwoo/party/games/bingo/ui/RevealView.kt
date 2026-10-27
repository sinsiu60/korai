package com.siwoo.party.games.bingo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui

/**
 * 게임이 끝난 뒤 모두의 판 공개 (더 이상 비밀이 아님).
 * 사람마다: 이름(플레이어 색) + 줄 수 + 빙고한 사람은 왕관, 아래에 판(BoardSecret) 그대로.
 * show 때 한 번 배치하고 그 뒤로는 다시 그리지 않는다. onDraw 할당 없음.
 */
class RevealView(context: Context) : View(context) {

    private var boards: List<BoardSecret> = emptyList()
    private var names: Array<String> = emptyArray()
    private var labels: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private var winner = BooleanArray(0)
    private var rects: Array<RectF> = emptyArray()
    private val bg = RectF()

    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crownPath = Path()
    private var nameY = 0f
    private var labelY = 0f

    /** 공개 중인 판 수 (테스트용) */
    val count: Int get() = boards.size

    init {
        contentDescription = "모두의 판"
    }

    /**
     * @param labels 사람마다 아래 작은 글자 (예: "1위 · 3줄")
     */
    fun show(boards: List<BoardSecret>, names: List<String>, colors: IntArray, labels: List<String>, winners: Collection<Int>) {
        this.boards = boards
        this.names = names.toTypedArray()
        this.labels = labels.toTypedArray()
        this.colors = colors
        winner = BooleanArray(boards.size) { it in winners }
        rects = Array(boards.size) { RectF() }
        relayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout() {
        val n = boards.size
        val w = width.toFloat()
        val h = height.toFloat()
        if (n == 0 || w <= 0f) return
        val gap = Theme.dpf(context, 10f)
        val head = h * 0.17f
        val colW = (w - gap * (n + 1)) / n
        val side = minOf(colW, h - head - gap * 1.5f)
        val total = side * n + gap * (n - 1)
        var x = (w - total) / 2f
        val top = head + (h - head - side) / 2f
        nameY = top - head * 0.5f
        labelY = top - head * 0.08f
        namePaint.textSize = head * 0.4f
        labelPaint.textSize = head * 0.3f
        for (k in 0 until n) {
            rects[k].set(x, top, x + side, top + side)
            boards[k].layout(this, rects[k])
            x += side + gap
        }
        bg.set(0f, 0f, w, h)
        // 이름이 칸보다 길면 줄인다
        for (k in 0 until n) while (namePaint.measureText(names[k]) > side * 0.72f && namePaint.textSize > 8f) namePaint.textSize *= 0.92f
    }

    override fun onDraw(c: Canvas) {
        if (boards.isEmpty()) return
        val r = Theme.dpf(context, 16f)
        fill.color = Theme.BG_DEEP
        c.drawRoundRect(bg, r, r, fill)
        for (k in 0 until boards.size) {
            val rc = rects[k]
            val cx = rc.centerX()
            namePaint.color = Theme.shade(colors[k], 1.3f)
            c.drawText(names[k], cx, nameY, namePaint)
            c.drawText(labels[k], cx, labelY, labelPaint)
            boards[k].draw(c, rc)
            if (winner[k]) {
                // 이름 왼쪽에 왕관
                val cw = namePaint.textSize * 1.1f
                Ui.drawCrown(c, cx - namePaint.measureText(names[k]) / 2f - cw * 0.75f, nameY, cw, crownPath, crownPaint)
            }
        }
    }
}
