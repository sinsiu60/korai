package com.siwoo.party.games.bingo.ui

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.secret.SecretContent
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bingo.BingoBoard
import com.siwoo.party.games.bingo.BingoLines
import com.siwoo.party.games.bingo.GameConfig

/**
 * 한 사람의 빙고판 그림 = 공통 비밀 내용(SecretContent).
 * 판의 숫자·도장·완성 줄을 복사해 들고 있다가 clear() 하면 모두 지운다 (다음 사람 화면 뒤에 남지 않게).
 * BoardView(내 판 화면)와 RevealView(끝난 뒤 공개)가 그린다. draw 는 할당 없음 (숫자 글자는 BingoArt.NUM).
 */
class BoardSecret(val color: Int) : SecretContent {

    private val nums = IntArray(GameConfig.CELLS)
    private val marks = BooleanArray(GameConfig.CELLS)
    private val lineOn = BooleanArray(GameConfig.LINE_COUNT)
    private var textCache = ""

    /** 완성 줄 수 */
    var lines = 0
        private set
    /** 고른 칸 (부르기 전, 금색 테두리) */
    var selected = -1
    /** 방금 부른 숫자의 칸 (금색 고리) */
    var latest = -1
    /** 톡 튀는 칸과 크기 배율 (BoardView 연출) */
    var popCell = -1
    var popScale = 1f
    /** 판 채우기 중: 빈칸에 + 표시 */
    var setupMode = false

    /** 판 숫자를 칸 순서 글자로 (테스트·확인용, 지우면 "") */
    override val text: String get() = textCache

    /** 숫자가 하나라도 남아 있는지 */
    val hasNumbers: Boolean get() = nums.any { it != 0 }

    fun numberAt(cell: Int): Int = nums[cell]

    // ── 그리기 위치 (layout 때 계산) ──
    private var ox = 0f
    private var oy = 0f
    private var side = 0f
    private var pad = 0f
    private var gap = 0f
    /** 칸 한 변 */
    var cell = 0f
        private set

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val stampTint = Theme.shade(color, 1.55f)
    private val stampDark = Theme.shade(color, 0.7f)

    /** 판 내용 복사 (latestNumber = 방금 부른 숫자, 없으면 0) */
    fun set(board: BingoBoard, latestNumber: Int) {
        for (k in 0 until GameConfig.CELLS) {
            nums[k] = board.numberAt(k)
            marks[k] = board.isMarked(k)
        }
        for (k in 0 until GameConfig.LINE_COUNT) lineOn[k] = board.lineDone(k)
        lines = board.lines
        latest = if (latestNumber > 0) board.cellOf(latestNumber) else -1
        val sb = StringBuilder()
        for (k in 0 until GameConfig.CELLS) {
            if (k > 0) sb.append(if (k % GameConfig.SIZE == 0) '/' else ' ')
            sb.append(nums[k])
        }
        textCache = if (hasNumbers) sb.toString() else ""
    }

    override fun layout(host: View, card: RectF) {
        side = minOf(card.width(), card.height())
        ox = card.centerX() - side / 2f
        oy = card.centerY() - side / 2f
        pad = side * 0.04f
        gap = side * 0.014f
        cell = (side - pad * 2f - gap * 4f) / GameConfig.SIZE
        numPaint.textSize = cell * 0.46f
        stroke.strokeWidth = cell * 0.08f
        dash.strokeWidth = cell * 0.05f
        dash.pathEffect = DashPathEffect(floatArrayOf(cell * 0.12f, cell * 0.09f), 0f)
    }

    fun cellLeft(k: Int): Float = ox + pad + (k % GameConfig.SIZE) * (cell + gap)
    fun cellTop(k: Int): Float = oy + pad + (k / GameConfig.SIZE) * (cell + gap)

    /** (x, y) 의 칸 (판 밖·틈이면 가까운 칸, 판 밖이면 -1) */
    fun cellAt(x: Float, y: Float): Int {
        if (cell <= 0f) return -1
        val cx = ((x - ox - pad + gap / 2f) / (cell + gap)).toInt()
        val cy = ((y - oy - pad + gap / 2f) / (cell + gap)).toInt()
        if (x < ox || y < oy || x > ox + side || y > oy + side) return -1
        if (cx !in 0 until GameConfig.SIZE || cy !in 0 until GameConfig.SIZE) return -1
        return cy * GameConfig.SIZE + cx
    }

    override fun draw(c: Canvas, card: RectF) {
        if (cell <= 0f) return
        val r = side * 0.05f
        // 판 테두리 (남색 + 플레이어 색 띠)
        fill.color = 0x55000000
        c.drawRoundRect(ox + side * 0.01f, oy + side * 0.018f, ox + side * 1.01f, oy + side * 1.018f, r, r, fill)
        fill.color = BingoArt.FRAME
        c.drawRoundRect(ox, oy, ox + side, oy + side, r, r, fill)
        stroke.color = color
        stroke.alpha = 200
        val sw = stroke.strokeWidth
        stroke.strokeWidth = pad * 0.35f
        c.drawRoundRect(ox + pad * 0.3f, oy + pad * 0.3f, ox + side - pad * 0.3f, oy + side - pad * 0.3f, r * 0.8f, r * 0.8f, stroke)
        stroke.strokeWidth = sw
        stroke.alpha = 255
        for (k in 0 until GameConfig.CELLS) drawCell(c, k)
        // 완성 줄 (금색 굵은 선)
        stroke.color = Theme.GOLD
        stroke.alpha = 165
        stroke.strokeWidth = cell * 0.13f
        for (k in 0 until GameConfig.LINE_COUNT) {
            if (!lineOn[k]) continue
            val line = BingoLines.LINES[k]
            val a = line[0]
            val b = line[GameConfig.SIZE - 1]
            c.drawLine(cellLeft(a) + cell / 2f, cellTop(a) + cell / 2f, cellLeft(b) + cell / 2f, cellTop(b) + cell / 2f, stroke)
        }
        stroke.alpha = 255
        stroke.strokeWidth = sw
        // 고른 칸 (금색 테두리) — 줄 위에
        if (selected >= 0) {
            val l = cellLeft(selected)
            val t = cellTop(selected)
            stroke.color = Theme.GOLD
            stroke.strokeWidth = cell * 0.11f
            c.drawRoundRect(l - cell * 0.03f, t - cell * 0.03f, l + cell * 1.03f, t + cell * 1.03f, cell * 0.2f, cell * 0.2f, stroke)
            stroke.strokeWidth = sw
        }
    }

    private fun drawCell(c: Canvas, k: Int) {
        val l = cellLeft(k)
        val t = cellTop(k)
        val n = nums[k]
        val s = if (k == popCell) popScale else 1f
        val cx = l + cell / 2f
        val cy = t + cell / 2f
        val h = cell * s / 2f
        val rr = cell * 0.17f
        val sel = k == selected
        fill.color = when {
            sel -> SELECTED
            n == 0 -> BingoArt.EMPTY
            marks[k] -> stampTint
            else -> Theme.PAPER
        }
        c.drawRoundRect(cx - h, cy - h, cx + h, cy + h, rr, rr, fill)
        if (n == 0) {
            if (setupMode) {
                // 빈칸: 점선 테두리 + 작은 +
                dash.color = 0xFF9AA0B8.toInt()
                c.drawRoundRect(l + cell * 0.12f, t + cell * 0.12f, l + cell * 0.88f, t + cell * 0.88f, rr * 0.7f, rr * 0.7f, dash)
                stroke.color = 0xFFB0B5C8.toInt()
                c.drawLine(cx - cell * 0.12f, cy, cx + cell * 0.12f, cy, stroke)
                c.drawLine(cx, cy - cell * 0.12f, cx, cy + cell * 0.12f, stroke)
            }
            return
        }
        if (marks[k]) {
            // 도장: 플레이어 색 원 + 안쪽 고리
            fill.color = color
            fill.alpha = 225
            c.drawCircle(cx, cy, h * 0.8f, fill)
            fill.alpha = 255
            stroke.color = stampDark
            c.drawCircle(cx, cy, h * 0.8f, stroke)
            stroke.color = 0x88FFFFFF.toInt()
            val sw = stroke.strokeWidth
            stroke.strokeWidth = sw * 0.5f
            c.drawCircle(cx, cy, h * 0.62f, stroke)
            stroke.strokeWidth = sw
        }
        if (k == latest) {
            // 방금 부른 숫자: 진한 남색 고리 (어느 플레이어 색 위에서도 보이게)
            stroke.color = Theme.INK
            val sw = stroke.strokeWidth
            stroke.strokeWidth = sw * 1.3f
            c.drawCircle(cx, cy, h * 0.96f, stroke)
            stroke.strokeWidth = sw
        }
        numPaint.color = when {
            sel -> Theme.GOLD
            marks[k] -> Theme.textOn(color)
            else -> Theme.INK
        }
        val ts = numPaint.textSize
        if (s != 1f) numPaint.textSize = ts * s
        c.drawText(BingoArt.NUM[n], cx, cy + numPaint.textSize * 0.36f, numPaint)
        numPaint.textSize = ts
    }

    companion object {
        /** 고른 칸 바탕 (진한 남색 + 금색 글자 — 어떤 도장 색과도 헷갈리지 않게) */
        private const val SELECTED = 0xFF101848.toInt()
    }

    override fun clear() {
        nums.fill(0)
        marks.fill(false)
        lineOn.fill(false)
        lines = 0
        selected = -1
        latest = -1
        popCell = -1
        textCache = ""
    }
}
