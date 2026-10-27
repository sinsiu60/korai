package com.siwoo.party.games.boxes.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.boxes.BoxesGrid
import kotlin.math.PI
import kotlin.math.sin

/**
 * 땅따먹기 판 (Canvas 하나로 점·선·칸을 그린다).
 * - 금색 점 격자, 아직 안 그은 자리는 희미한 선
 * - 그은 선: 그은 사람 색, 한쪽 점에서 다른 쪽 점으로 쭉 그어짐 / 마지막 선은 흰 빛으로 강조
 * - 완성된 칸: 그 사람 색으로 커지며 차오르고 가운데 이름 첫 글자
 * - 손가락을 대고 있는 동안: 가장 가까운 빈 선을 현재 차례 색 점선으로 미리보기 → 손을 떼면 listener 로 알림
 * - 종료 칸 세기: focus 사람 칸만 밝게, 센 칸은 반짝이며 번호
 * 연출이 진행 중일 때만 postOnAnimation 으로 다시 그린다. onDraw 에서는 객체를 만들지 않는다.
 * 규칙 판정은 하지 않는다 — 고른 선 번호만 알린다.
 */
class BoxesBoardView(context: Context) : View(context) {

    interface Listener {
        /** 미리보기를 보여 줘도 되는지 (일시정지·게임 끝이면 false) */
        fun canPreview(): Boolean
        /** 손을 뗀 곳의 선 (빈 선만) */
        fun onLinePicked(line: Int)
    }

    var listener: Listener? = null

    lateinit var grid: BoxesGrid
        private set
    private var colors = IntArray(0)
    /** 선 테두리용 어두운 색 · 칸 위 글자색 (setup 때 미리) */
    private var darkColors = IntArray(0)
    private var textColors = IntArray(0)
    private var initials = emptyArray<String>()

    // ── 상태 (판에 보이는 것) ──
    private var drawn = BooleanArray(0)
    private var lineOwner = IntArray(0)
    private var lineStart = LongArray(0)
    private var lineDur = LongArray(0)
    private var boxOwner = IntArray(0)
    private var boxStart = LongArray(0)
    private var boxDur = LongArray(0)
    private var flashStart = LongArray(0)
    private var flashDur = LongArray(0)
    /** 종료 칸 세기에서 이 칸의 번호 (0 = 아직 안 셈) */
    private var countNum = IntArray(0)
    private var lastLine = -1

    /** 손가락 아래 미리보기 선 (-1 = 없음) */
    var previewLine = -1
        private set
    private var previewColor = Theme.GOLD
    /** 종료 칸 세기: 이 사람 칸만 밝게 (-1 = 모두) */
    private var focus = -1

    // "한 번 더!" 떠오르는 글자
    private var againX = 0f
    private var againY = 0f
    private var againStart = 0L
    private var againDur = 0L
    private var againColor = Theme.GOLD

    // ── 배치 (onSizeChanged) ──
    /** 왼쪽 위 점 (뷰 좌표) */
    var originX = 0f
        private set
    var originY = 0f
        private set
    /** 칸 한 변 (px) */
    var cell = 0f
        private set
    private val panel = RectF()
    private val tmp = RectF()
    private var lineW = 0f
    private var dotR = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0x2EFFFFFF }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val numOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = Theme.INK
    }
    private val againPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val againOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = Theme.INK
    }

    // 프레임 루프 (연출 중일 때만)
    private var looping = false
    private val frame = Runnable { onFrame() }

    init {
        isClickable = true
        contentDescription = "땅따먹기 판"
    }

    private fun now(): Long = SystemClock.uptimeMillis()

    /** 판 준비: 격자, 플레이어 색·이름 첫 글자 */
    fun setup(g: BoxesGrid, playerColors: IntArray, playerInitials: Array<String>) {
        grid = g
        colors = playerColors
        darkColors = IntArray(playerColors.size) { Theme.shade(playerColors[it], 0.5f) }
        textColors = IntArray(playerColors.size) { Theme.textOn(playerColors[it]) }
        initials = playerInitials
        drawn = BooleanArray(g.lineCount)
        lineOwner = IntArray(g.lineCount) { -1 }
        lineStart = LongArray(g.lineCount)
        lineDur = LongArray(g.lineCount)
        boxOwner = IntArray(g.boxCount) { -1 }
        boxStart = LongArray(g.boxCount)
        boxDur = LongArray(g.boxCount)
        flashStart = LongArray(g.boxCount)
        flashDur = LongArray(g.boxCount)
        countNum = IntArray(g.boxCount)
        lastLine = -1
        previewLine = -1
        layoutGrid()
        invalidate()
    }

    val isReady: Boolean get() = cell > 0f && ::grid.isInitialized

    /** 선 긋기 (durMs 동안 한쪽 끝에서 그어짐) */
    fun addLine(line: Int, player: Int, durMs: Long) {
        drawn[line] = true
        lineOwner[line] = player
        lineStart[line] = now()
        lineDur[line] = durMs.coerceAtLeast(1)
        lastLine = line
        if (previewLine == line) previewLine = -1
        ensureRunning()
    }

    /** 칸 채우기 (커지며 나타남) */
    fun fillBox(box: Int, player: Int, durMs: Long) {
        boxOwner[box] = player
        boxStart[box] = now()
        boxDur[box] = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    /** "한 번 더!" 글자를 칸 위로 띄움 */
    fun again(box: Int, color: Int, durMs: Long) {
        againX = grid.boxCol(box) + 0.5f
        againY = grid.boxRow(box) + 0.5f
        againColor = color
        againStart = now()
        againDur = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    /** 미리보기 색 (현재 차례) */
    fun setCurrentColor(color: Int) {
        if (previewColor != color) { previewColor = color; invalidate() }
    }

    /** 종료 칸 세기: player 칸만 밝게 (-1 = 모두 보통) */
    fun setFocus(player: Int) {
        if (focus == player) return
        focus = player
        invalidate()
    }

    /** 종료 칸 세기: 칸 반짝 + 번호 */
    fun flash(box: Int, number: Int, durMs: Long) {
        countNum[box] = number
        flashStart[box] = now()
        flashDur[box] = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    fun isLineShown(line: Int): Boolean = drawn[line]
    fun boxOwnerShown(box: Int): Int = boxOwner[box]
    fun countShown(box: Int): Int = countNum[box]

    /** 선 가운데 (이 뷰 좌표) */
    fun lineCenter(line: Int, out: PointF) {
        out.set(originX + grid.midX(line) * cell, originY + grid.midY(line) * cell)
    }

    /** 칸 가운데 (이 뷰 좌표) */
    fun boxCenter(box: Int, out: PointF) {
        out.set(originX + (grid.boxCol(box) + 0.5f) * cell, originY + (grid.boxRow(box) + 0.5f) * cell)
    }

    /** 연출이 하나라도 진행 중 */
    val isAnimating: Boolean get() = looping

    private fun ensureRunning() {
        invalidate()
        if (looping) return
        looping = true
        postOnAnimation(frame)
    }

    private fun onFrame() {
        if (!looping) return
        invalidate()
        if (anyActive(now())) postOnAnimation(frame) else looping = false
    }

    private fun anyActive(t: Long): Boolean {
        if (t - againStart < againDur) return true
        for (k in lineStart.indices) if (lineOwner[k] >= 0 && t - lineStart[k] < lineDur[k]) return true
        for (k in boxStart.indices) {
            if (boxOwner[k] >= 0 && t - boxStart[k] < boxDur[k]) return true
            if (countNum[k] > 0 && t - flashStart[k] < flashDur[k]) return true
        }
        return false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutGrid()
    }

    private fun layoutGrid() {
        if (!::grid.isInitialized || width <= 0 || height <= 0) return
        val n = grid.cells
        // 판 = 격자 + 둘레 0.55칸 (터치 여유·점이 잘리지 않게)
        val side = minOf(width, height).toFloat()
        cell = side / (n + 1.1f)
        val gw = cell * n
        originX = (width - gw) / 2f
        originY = (height - gw) / 2f
        val m = cell * 0.5f
        panel.set(originX - m, originY - m, originX + gw + m, originY + gw + m)
        lineW = cell * 0.11f
        dotR = cell * 0.1f
        slotPaint.strokeWidth = Theme.dpf(context, 1.5f)
        val dash = cell * 0.12f
        dashPaint.pathEffect = DashPathEffect(floatArrayOf(dash, dash * 0.8f), 0f)
        initPaint.textSize = cell * 0.46f
        numPaint.textSize = cell * 0.5f
        numOutline.textSize = cell * 0.5f
        numOutline.strokeWidth = cell * 0.08f
        againPaint.textSize = maxOf(cell * 0.5f, Theme.dpf(context, 18f))
        againOutline.textSize = againPaint.textSize
        againOutline.strokeWidth = againPaint.textSize * 0.16f
    }

    override fun onDraw(c: Canvas) {
        if (!isReady) return
        val t = now()
        val n = grid.cells

        // 판 바탕
        val pr = cell * 0.35f
        fill.color = 0x38000000
        c.drawRoundRect(panel, pr, pr, fill)
        stroke.strokeWidth = Theme.dpf(context, 1.5f)
        stroke.color = Theme.CARD_BORDER
        c.drawRoundRect(panel, pr, pr, stroke)

        // 칸
        val inset = lineW * 0.9f
        for (b in 0 until grid.boxCount) {
            val o = boxOwner[b]
            if (o < 0) continue
            val p = ((t - boxStart[b]).toFloat() / boxDur[b]).coerceIn(0f, 1f)
            // 살짝 넘쳤다 돌아오는 커짐 (easeOutBack)
            val q = p - 1f
            var s = 1f + 2.2f * q * q * q + 1.2f * q * q
            var alpha = (p * 2.5f).coerceAtMost(1f)
            val dim = focus >= 0 && o != focus
            if (dim) alpha *= 0.28f
            var flash = 0f
            if (countNum[b] > 0 && t - flashStart[b] < flashDur[b]) {
                val f = (t - flashStart[b]).toFloat() / flashDur[b]
                flash = sin(f * PI.toFloat())
                s *= 1f + 0.14f * flash
            }
            val l = originX + grid.boxCol(b) * cell
            val tp = originY + grid.boxRow(b) * cell
            val cx = l + cell / 2f
            val cy = tp + cell / 2f
            val half = (cell / 2f - inset) * s
            if (half <= 0f) continue
            tmp.set(cx - half, cy - half, cx + half, cy + half)
            val rr = cell * 0.1f
            fill.color = colors[o]
            fill.alpha = (alpha * 225).toInt()
            c.drawRoundRect(tmp, rr, rr, fill)
            // 위쪽 반 밝은 띠 (입체감)
            fill.color = 0x26FFFFFF
            fill.alpha = (alpha * 0x26).toInt()
            c.drawRoundRect(tmp.left, tmp.top, tmp.right, tmp.centerY(), rr, rr, fill)
            if (flash > 0f) {
                fill.color = Theme.TEXT
                fill.alpha = (flash * 150).toInt()
                c.drawRoundRect(tmp, rr, rr, fill)
                stroke.color = Theme.GOLD_LIGHT
                stroke.alpha = (flash * 255).toInt()
                stroke.strokeWidth = lineW * 0.6f
                c.drawRoundRect(tmp, rr, rr, stroke)
                stroke.alpha = 255
            }
            val numShown = countNum[b] > 0 && focus == o
            if (numShown) {
                val y = cy + numPaint.textSize * 0.36f
                c.save()
                c.scale(s, s, cx, cy)
                numOutline.alpha = (alpha * 255).toInt()
                numPaint.alpha = (alpha * 255).toInt()
                c.drawText(NUMS[countNum[b]], cx, y, numOutline)
                c.drawText(NUMS[countNum[b]], cx, y, numPaint)
                c.restore()
            } else {
                initPaint.color = textColors[o]
                initPaint.alpha = (alpha * 255).toInt()
                c.save()
                c.scale(s, s, cx, cy)
                c.drawText(initials[o], cx, cy + initPaint.textSize * 0.36f, initPaint)
                c.restore()
            }
        }

        // 빈 자리 (희미한 선)
        for (line in 0 until grid.lineCount) {
            if (drawn[line]) continue
            c.drawLine(px(grid.x0(line)), py(grid.y0(line)), px(grid.x1(line)), py(grid.y1(line)), slotPaint)
        }

        // 마지막 선 강조 (금빛)
        val last = lastLine
        if (last >= 0 && focus < 0) {
            stroke.color = Theme.GOLD_LIGHT
            stroke.alpha = 120
            stroke.strokeWidth = lineW * 2.6f
            drawLinePart(c, last, lineProgress(last, t))
            stroke.alpha = 255
        }

        // 그은 선: 어두운 테두리 + 사람 색
        for (line in 0 until grid.lineCount) {
            if (!drawn[line]) continue
            val p = lineProgress(line, t)
            val o = lineOwner[line]
            stroke.color = darkColors[o]
            stroke.strokeWidth = lineW * 1.45f
            drawLinePart(c, line, p)
            stroke.color = colors[o]
            stroke.strokeWidth = lineW
            drawLinePart(c, line, p)
        }

        // 미리보기: 현재 차례 색 빛 + 점선
        val pv = previewLine
        if (pv >= 0) {
            stroke.color = previewColor
            stroke.alpha = 70
            stroke.strokeWidth = lineW * 3f
            drawLinePart(c, pv, 1f)
            stroke.alpha = 255
            dashPaint.color = previewColor
            dashPaint.alpha = 220
            dashPaint.strokeWidth = lineW * 1.05f
            c.drawLine(px(grid.x0(pv)), py(grid.y0(pv)), px(grid.x1(pv)), py(grid.y1(pv)), dashPaint)
        }

        // 점 (금색 구슬)
        for (r in 0..n) for (col in 0..n) {
            val x = originX + col * cell
            val y = originY + r * cell
            fill.color = Theme.GOLD_DARK
            c.drawCircle(x, y + dotR * 0.18f, dotR * 1.12f, fill)
            fill.color = Theme.GOLD
            c.drawCircle(x, y, dotR, fill)
            fill.color = Theme.GOLD_LIGHT
            c.drawCircle(x - dotR * 0.3f, y - dotR * 0.32f, dotR * 0.38f, fill)
        }
        // 미리보기 선 양 끝 점은 현재 차례 색 고리
        if (pv >= 0) {
            stroke.color = previewColor
            stroke.strokeWidth = lineW * 0.55f
            c.drawCircle(px(grid.x0(pv)), py(grid.y0(pv)), dotR * 1.7f, stroke)
            c.drawCircle(px(grid.x1(pv)), py(grid.y1(pv)), dotR * 1.7f, stroke)
        }

        // "한 번 더!" (칸 위로 떠오르며 사라짐)
        val ad = t - againStart
        if (ad >= 0L && ad < againDur) {
            val f = ad.toFloat() / againDur
            val a = if (f < 0.7f) 1f else (1f - (f - 0.7f) / 0.3f)
            val pop = if (f < 0.15f) 0.6f + 0.4f * (f / 0.15f) else 1f
            val x = (px(againX)).coerceIn(panel.left + againPaint.textSize * 2f, panel.right - againPaint.textSize * 2f)
            val y = (py(againY) - cell * 0.15f - cell * 0.55f * f).coerceAtLeast(againPaint.textSize)
            c.save()
            c.scale(pop, pop, x, y)
            againOutline.alpha = (a * 255).toInt()
            againPaint.color = Theme.GOLD
            againPaint.alpha = (a * 255).toInt()
            c.drawText(AGAIN, x, y, againOutline)
            c.drawText(AGAIN, x, y, againPaint)
            c.restore()
        }
    }

    private fun px(gx: Float): Float = originX + gx * cell
    private fun py(gy: Float): Float = originY + gy * cell

    private fun lineProgress(line: Int, t: Long): Float {
        val p = ((t - lineStart[line]).toFloat() / lineDur[line]).coerceIn(0f, 1f)
        return 1f - (1f - p) * (1f - p)
    }

    /** 선을 시작 점에서 p 만큼만 */
    private fun drawLinePart(c: Canvas, line: Int, p: Float) {
        val x0 = px(grid.x0(line))
        val y0 = py(grid.y0(line))
        val x1 = px(grid.x1(line))
        val y1 = py(grid.y1(line))
        c.drawLine(x0, y0, x0 + (x1 - x0) * p, y0 + (y1 - y0) * p, stroke)
    }

    // ── 터치: 대고 있는 동안 미리보기, 떼면 확정 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                setPreview(pick(e.x, e.y))
                return true
            }
            MotionEvent.ACTION_UP -> {
                val line = pick(e.x, e.y)
                setPreview(-1)
                performClick()
                if (line >= 0) listener?.onLinePicked(line)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                setPreview(-1)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** 터치한 곳에서 고를 선 (미리보기 불가·판 밖이면 -1) */
    private fun pick(x: Float, y: Float): Int {
        if (!isReady || listener?.canPreview() != true) return -1
        return grid.nearestFreeLine((x - originX) / cell, (y - originY) / cell, drawn)
    }

    private fun setPreview(line: Int) {
        if (previewLine == line) return
        previewLine = line
        invalidate()
    }

    /** 미리보기 지우기 (게임 끝·일시정지) */
    fun clearPreview() = setPreview(-1)

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val AGAIN = "한 번 더!"
        /** 칸 세기 번호 글자 (그릴 때 만들지 않도록 미리) */
        private val NUMS = Array(50) { it.toString() }
    }
}
