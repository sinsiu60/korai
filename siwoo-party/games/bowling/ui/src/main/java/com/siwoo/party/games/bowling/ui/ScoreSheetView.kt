package com.siwoo.party.games.bowling.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bowling.BowlingGame

/**
 * 볼링 점수판 (종이 점수표 모양): 줄 = 사람, 칸 = 프레임(위 작은 칸에 X / - 숫자, 아래 누적 점수) + 합계.
 * 보너스를 기다리는 프레임은 누적 점수가 비어 있다. 지금 굴리는 사람·프레임은 금색 테두리.
 * 글자는 refresh() 에서 미리 만들어 두고 onDraw 는 그리기만 (할당 없음).
 */
class ScoreSheetView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var game: BowlingGame? = null
    private var colors = IntArray(0)
    private var rawNames: List<String> = emptyList()

    private var rows = 0
    private var frames = 0
    private var names = arrayOf<String>()
    private var totals = arrayOf<String>()
    /** [사람][프레임] 누적 점수 글자 ("" = 미확정) */
    private var cum = arrayOf<Array<String>>()
    /** [사람][프레임][칸] 칸 글자 */
    private var marks = arrayOf<Array<Array<String>>>()
    private var hiRow = -1
    private var hiFrame = -1
    private val frameLabels = Array(10) { (it + 1).toString() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val nameText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val rect = RectF()

    /** 게임·이름·색 연결 */
    fun bind(game: BowlingGame, names: List<String>, colors: IntArray) {
        this.game = game
        this.rawNames = names
        this.colors = colors
        rows = game.players
        frames = game.frames
        refresh(-1, -1)
    }

    /** 점수가 바뀌면 글자를 다시 만들고 (hiRow, hiFrame) 칸을 강조 */
    fun refresh(hiRow: Int, hiFrame: Int) {
        val g = game ?: return
        this.hiRow = hiRow
        this.hiFrame = hiFrame
        totals = Array(rows) { g.cards[it].total.toString() }
        cum = Array(rows) { r -> Array(frames) { f -> g.cards[r].cumulative(f)?.toString() ?: "" } }
        marks = Array(rows) { r -> Array(frames) { f -> Array(g.cards[r].boxes(f)) { b -> g.cards[r].mark(f, b) } } }
        ellipsizeNames()
        invalidate()
    }

    private fun nameW(): Float = NAME_DP * d

    private fun ellipsizeNames() {
        nameText.textSize = 14f * d
        val avail = nameW() - 22f * d
        names = Array(rows) { i ->
            val s = rawNames.getOrElse(i) { "" }
            BowlingArt.fit(s, nameText, avail)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (HEAD_DP * d + rows * ROW_DP * d + 2f * d).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ellipsizeNames()
    }

    override fun onDraw(c: Canvas) {
        if (rows == 0 || frames == 0 || names.size < rows) return
        val w = width.toFloat()
        val head = HEAD_DP * d
        val rowH = ROW_DP * d
        val nameW = nameW()
        val totalW = TOTAL_DP * d
        val fw = (w - nameW - totalW) / frames
        line.strokeWidth = 1f * d
        // 머리줄: 프레임 번호 · 합계
        text.textSize = 11f * d
        text.color = Theme.TEXT_SUB
        for (f in 0 until frames) c.drawText(frameLabels[f], nameW + fw * (f + 0.5f), head * 0.72f, text)
        c.drawText(TOTAL, w - totalW / 2f, head * 0.72f, text)
        for (r in 0 until rows) {
            val top = head + r * rowH
            val bottom = top + rowH - 3f * d
            // 줄 바탕
            fill.color = if (r == hiRow) Theme.CARD_LIGHT else Theme.CARD
            rect.set(0f, top, w, bottom)
            c.drawRoundRect(rect, 6f * d, 6f * d, fill)
            // 이름 칸: 색 막대 + 이름
            fill.color = colors[r]
            rect.set(3f * d, top + 4f * d, 8f * d, bottom - 4f * d)
            c.drawRoundRect(rect, 2f * d, 2f * d, fill)
            nameText.textSize = 14f * d
            nameText.color = Theme.TEXT
            c.drawText(names[r], 12f * d, (top + bottom) / 2f + 5f * d, nameText)
            // 프레임 칸
            for (f in 0 until frames) {
                val left = nameW + f * fw
                val right = left + fw
                line.color = 0x55FFFFFF
                c.drawRect(left, top, right, bottom, line)
                val boxes = marks[r][f].size
                val bs = minOf(fw / boxes, rowH * 0.42f)
                val boxTop = top
                val boxBottom = top + bs
                for (b in 0 until boxes) {
                    val bl = right - bs * (boxes - b)
                    c.drawRect(bl, boxTop, bl + bs, boxBottom, line)
                    val m = marks[r][f][b]
                    if (m.isEmpty()) continue
                    text.textSize = bs * 0.72f
                    text.color = when (m) {
                        "X" -> Theme.GOLD
                        "/" -> Theme.GOLD_LIGHT
                        "-" -> Theme.TEXT_SUB
                        else -> Theme.TEXT
                    }
                    c.drawText(m, bl + bs / 2f, boxBottom - bs * 0.22f, text)
                }
                val s = cum[r][f]
                if (s.isNotEmpty()) {
                    text.textSize = minOf(15f * d, fw * 0.42f)
                    text.color = Theme.TEXT
                    c.drawText(s, (left + right) / 2f, bottom - (bottom - boxBottom - text.textSize) / 2f - 2f * d, text)
                }
                if (r == hiRow && f == hiFrame) {
                    line.color = Theme.GOLD
                    line.strokeWidth = 2.5f * d
                    c.drawRect(left + 1f * d, top + 1f * d, right - 1f * d, bottom - 1f * d, line)
                    line.strokeWidth = 1f * d
                }
            }
            // 합계
            text.textSize = 19f * d
            text.color = Theme.GOLD
            c.drawText(totals[r], w - totalW / 2f, (top + bottom) / 2f + 7f * d, text)
        }
    }

    companion object {
        const val NAME_DP = 84f
        const val TOTAL_DP = 54f
        const val HEAD_DP = 18f
        const val ROW_DP = 46f
        private const val TOTAL = "합계"

        /** 프레임 수에 맞는 점수판 폭 (dp) */
        fun widthDp(frames: Int): Float = NAME_DP + TOTAL_DP + frames * if (frames > 5) 44f else 60f
    }
}
