package com.siwoo.party.games.oddone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.lane.ZoneFrame
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.oddone.Diff
import com.siwoo.party.games.oddone.GameConfig
import com.siwoo.party.games.oddone.GridLayout
import com.siwoo.party.games.oddone.Puzzle

/**
 * 하나만 다른 모양 그림 층 (MultiTouchZoneView 위에 겹침, 터치는 받지 않음).
 * 구역마다 같은 4×4 격자를 그 플레이어 쪽으로 돌려 그린다 (위쪽 구역 180°) → 다른 하나도 모두 같은 자리.
 * 배치는 GridLayout (옆 배치: 왼쪽 이름·점수 / 가운데 격자 / 오른쪽 남은 시간, 위 배치: 위 띠 + 격자).
 * 남은 시간 고리·라운드 번호는 따로 겹친 [ringLayer] 가 그린다 (도는 동안 그 층만 다시 그림).
 * 이 층은 오답 X·잠금 막대·+1 떠오름이 진행 중일 때만 다시 그린다.
 */
class OddBoardView(context: Context, private val zoneView: MultiTouchZoneView, private val names: List<String>, private val colors: IntArray) : View(context) {

    /** 게임 시간 (일시정지 제외) */
    fun interface Clock { fun now(): Long }

    var clock: Clock = Clock { SystemClock.uptimeMillis() }
    /** 일시정지 중엔 애니메이션 루프를 멈춘다 */
    var frozen = false
        set(v) {
            field = v
            if (!v) { invalidate(); ringLayer.invalidate() }
        }
    /** 오답 X·+1 떠오름 길이 (속도 배율 적용된 값) */
    var flashMs = 650L

    /** 남은 시간 고리 + 라운드 번호 층 (보드 위에 겹쳐 붙인다) */
    val ringLayer: View = object : View(context) {
        override fun onDraw(c: Canvas) = drawRings(c)
        override fun onTouchEvent(event: MotionEvent?): Boolean = false
    }

    private val n = names.size
    private val art = ShapeArt
    private var puzzle: Puzzle? = null
    /** 라운드가 끝나 다른 하나를 보여 주는 중 */
    private var revealed = false
    private var winner = -1
    private var counterText = ""
    private var msgText = ""
    private var msgColor = Theme.GOLD
    private var diffText = ""
    private val scoreText = Array(n) { "0점" }
    private val wrongCell = IntArray(n) { -1 }
    private val wrongStart = LongArray(n)
    private val lockStart = LongArray(n)
    private val lockEnd = LongArray(n) { Long.MIN_VALUE }
    private var plusStart = Long.MIN_VALUE
    private var timerStart = 0L
    private var timerEnd = 0L
    private var timerOn = false

    // ── 구역별 배치 ──
    private val frames = Array(n) { ZoneFrame() }
    private val grids = Array(n) { GridLayout() }
    private val zw = FloatArray(n)
    private val zh = FloatArray(n)

    private val d = Theme.dpf(context, 1f)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapeFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    // ── 상태 바꾸기 (값이 바뀔 때만 문자열을 만든다) ──

    fun showPuzzle(p: Puzzle, total: Int) {
        puzzle = p
        revealed = false
        winner = -1
        msgText = ""
        diffText = ""
        plusStart = Long.MIN_VALUE
        for (i in 0 until n) wrongCell[i] = -1
        counterText = "${p.number}/$total"
        contentDescription = p.shape.label + " " + p.kind.label
        invalidate()
        ringLayer.invalidate()
    }

    /** 라운드 사이 빈 격자 */
    fun clearPuzzle() {
        puzzle = null
        revealed = false
        winner = -1
        msgText = ""
        diffText = ""
        for (i in 0 until n) wrongCell[i] = -1
        invalidate()
    }

    /** 라운드 끝: 다른 하나를 모든 구역에서 금색으로. winnerZone -1 = 시간 초과 */
    fun reveal(winnerZone: Int) {
        val p = puzzle ?: return
        revealed = true
        winner = winnerZone
        if (winnerZone >= 0) {
            msgText = names[winnerZone] + " +1"
            msgColor = colors[winnerZone]
            plusStart = clock.now()
        } else {
            msgText = TIMEOUT
            msgColor = Theme.RED
        }
        diffText = DIFF_TEXT[p.kind.ordinal]
        stopTimer()
        invalidate()
    }

    fun setScore(i: Int, score: Int) {
        scoreText[i] = "${score}점"
        invalidate()
    }

    /** 오답: 그 칸에 X, untilMs(게임 시간)까지 격자 잠금 표시 */
    fun showWrong(i: Int, cell: Int, untilMs: Long) {
        wrongCell[i] = cell
        wrongStart[i] = clock.now()
        lockStart[i] = wrongStart[i]
        lockEnd[i] = untilMs
        invalidate()
    }

    /** 남은 시간 고리 시작 (게임 시간 ms) */
    fun startTimer(startMs: Long, endMs: Long) {
        timerStart = startMs
        timerEnd = endMs
        timerOn = true
        ringLayer.invalidate()
    }

    fun stopTimer() {
        timerOn = false
        ringLayer.invalidate()
    }

    // ── 배치·판정 ──

    /** zone 구역 배치를 맞춘다 (없으면 false) */
    private fun prepare(i: Int): Boolean {
        val z = zoneView.zone(i) ?: return false
        val f = frames[i].set(z)
        if (f.width != zw[i] || f.height != zh[i]) {
            zw[i] = f.width
            zh[i] = f.height
            grids[i].layout(f.width, f.height, d)
        }
        return true
    }

    /** 화면 좌표 (x, y)(MultiTouchZoneView 기준)가 zone 구역 격자의 몇 번 칸인지 (밖이면 -1) */
    fun cellAt(zone: Int, x: Float, y: Float): Int {
        if (zone !in 0 until n || !prepare(zone)) return -1
        val f = frames[zone]
        return grids[zone].cellAt(f.toLocalX(x, y), f.toLocalY(x, y))
    }

    /** zone 구역 cell 칸 가운데의 화면 좌표 → out */
    fun cellCenter(zone: Int, cell: Int, out: PointF) {
        if (!prepare(zone)) return
        val f = frames[zone]
        val g = grids[zone]
        out.set(f.toScreenX(g.centerX(cell), g.centerY(cell)), f.toScreenY(g.centerX(cell), g.centerY(cell)))
    }

    /** 구역 안 좌표로 그리도록 캔버스를 돌린다 */
    private fun enter(c: Canvas, f: ZoneFrame) {
        c.save()
        c.translate(f.centerX, f.centerY)
        c.rotate(f.rotation)
        c.translate(-f.width / 2f, -f.height / 2f)
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val now = clock.now()
        var animating = false
        for (i in 0 until n) {
            if (!prepare(i)) continue
            enter(c, frames[i])
            if (drawZone(c, i, now)) animating = true
            c.restore()
        }
        if (animating && !frozen) postInvalidateOnAnimation()
    }

    /** 한 구역 (구역 안 좌표). 애니메이션 중이면 true */
    private fun drawZone(c: Canvas, i: Int, now: Long): Boolean {
        val g = grids[i]
        val p = puzzle
        var anim = false
        // 격자 판
        rect.set(g.left - 5f * d, g.top - 5f * d, g.right + 5f * d, g.bottom + 5f * d)
        fill.color = 0xE60A1236.toInt()
        c.drawRoundRect(rect, 12f * d, 12f * d, fill)
        stroke.strokeWidth = 1.5f * d
        stroke.color = if (winner == i) Theme.GOLD else 0x33FFFFFF
        c.drawRoundRect(rect, 12f * d, 12f * d, stroke)
        val cr = g.cell * 0.18f
        val outline = (g.cell * 0.035f).coerceIn(1f * d, 2.2f * d)
        for (k in 0 until GameConfig.CELLS) {
            val l = g.cellLeft(k)
            val t = g.cellTop(k)
            fill.color = TILE
            c.drawRoundRect(l, t, l + g.cell, t + g.cell, cr, cr, fill)
            if (p != null) {
                val look = p.look(k)
                art.draw(c, p.shape, l + g.cell / 2f, t + g.cell / 2f, g.cell * SHAPE_RADIUS * look.scale, look.rotation, p.argb(k), outline, shapeFill, shapeStroke)
                if (revealed && !p.isOdd(k)) {
                    fill.color = 0x8C050A24.toInt()
                    c.drawRoundRect(l, t, l + g.cell, t + g.cell, cr, cr, fill)
                }
            } else {
                text.textSize = g.cell * 0.42f
                text.color = 0x3DB8C3F0
                c.drawText(WAIT_MARK, l + g.cell / 2f, t + g.cell / 2f + text.textSize * 0.36f, text)
            }
        }
        // 잠금 (격자를 어둡게 + 자물쇠 + 줄어드는 막대)
        if (now < lockEnd[i]) {
            anim = true
            drawLock(c, i, now)
        }
        // 오답 X (잠금 위에 또렷하게)
        val wc = wrongCell[i]
        if (wc >= 0) {
            val f = ((now - wrongStart[i]).toFloat() / flashMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            if (now < lockEnd[i] || f < 1f) {
                if (f < 1f) anim = true
                val dx = if (f < 1f) (Math.sin(f * Math.PI * 6.0) * (1f - f) * 4f * d).toFloat() else 0f
                drawX(c, g.centerX(wc) + dx, g.centerY(wc), g.cell * 0.32f)
            }
        }
        // 다른 하나 공개: 금색 테두리 (잠금 덮개 위에 — 잠긴 사람도 또렷이 보이게)
        if (p != null && revealed) {
            val l = g.cellLeft(p.oddIndex)
            val t = g.cellTop(p.oddIndex)
            if (now < lockEnd[i]) {
                val look = p.odd
                fill.color = TILE
                c.drawRoundRect(l, t, l + g.cell, t + g.cell, cr, cr, fill)
                art.draw(c, p.shape, l + g.cell / 2f, t + g.cell / 2f, g.cell * SHAPE_RADIUS * look.scale, look.rotation, p.oddArgb, outline, shapeFill, shapeStroke)
            }
            stroke.color = Theme.GOLD
            stroke.strokeWidth = 3.5f * d
            c.drawRoundRect(l - 1.5f * d, t - 1.5f * d, l + g.cell + 1.5f * d, t + g.cell + 1.5f * d, cr, cr, stroke)
        }
        drawInfo(c, i, now)
        if (winner == i && now - plusStart < flashMs) anim = true
        return anim
    }

    /** 이름·점수·공개 문구 (격자 옆 또는 위 띠) */
    private fun drawInfo(c: Canvas, i: Int, now: Long) {
        val g = grids[i]
        val pad = GridLayout.PAD_DP * d
        if (g.sideMode) {
            val x0 = pad
            val x1 = g.left - pad
            val cx = (x0 + x1) / 2f
            val colW = x1 - x0
            // 이름
            text.textAlign = Paint.Align.CENTER
            fitText(names[i], 15f * d, colW)
            text.color = colors[i]
            c.drawText(names[i], cx, g.top + text.textSize, text)
            // 점수
            fitText(scoreText[i], 24f * d, colW)
            text.color = Theme.GOLD
            val sy = g.top + g.size * 0.47f
            c.drawText(scoreText[i], cx, sy, text)
            drawPlus(c, i, now, cx, sy - text.textSize * 1.05f)
            // 공개 문구 (왼쪽 아래)
            if (msgText.isNotEmpty()) {
                drawMsgPill(c, cx, g.bottom - 14f * d, colW, 13f * d)
            }
            // 오른쪽 칸: 다른 점 (고리는 ringLayer)
            if (diffText.isNotEmpty()) {
                val rx = (g.right + g.width - pad) / 2f
                val rw = g.width - pad - g.right - pad
                fitText(DIFF_HEAD, 11f * d, rw)
                text.color = Theme.TEXT_SUB
                c.drawText(DIFF_HEAD, rx, g.bottom - 24f * d, text)
                fitText(diffText, 15f * d, rw)
                text.color = Theme.GOLD
                c.drawText(diffText, rx, g.bottom - 6f * d, text)
            }
        } else {
            // 위 띠: 이름 + 점수 (왼쪽) · 공개 문구 (가운데) · 고리 (오른쪽, ringLayer)
            val midY = pad + (g.headerH - pad) / 2f
            text.textAlign = Paint.Align.LEFT
            text.textSize = 16f * d
            text.color = colors[i]
            val x = g.left + 2f * d
            c.drawText(names[i], x, midY + text.textSize * 0.36f, text)
            val nw = text.measureText(names[i])
            text.color = Theme.GOLD
            text.textSize = 18f * d
            c.drawText(scoreText[i], x + nw + 8f * d, midY + text.textSize * 0.36f, text)
            val sw = text.measureText(scoreText[i])
            text.textAlign = Paint.Align.CENTER
            drawPlus(c, i, now, x + nw + 8f * d + sw + 14f * d, midY + 6f * d)
            if (msgText.isNotEmpty()) {
                val ringR = ringRadius(i)
                val left = x + nw + sw + 20f * d
                val right = g.right - 2f * ringR - 10f * d
                val w = right - left
                // 공개 문구 + 다른 점
                text.textSize = 15f * d
                val mw = text.measureText(msgText) + 18f * d
                text.textSize = 13f * d
                val dw = text.measureText(diffText)
                if (mw + dw + 8f * d <= w) {
                    val start = left + (w - mw - dw - 8f * d) / 2f
                    drawMsgPill(c, start + mw / 2f, midY, mw, 15f * d)
                    text.textSize = 13f * d
                    text.color = Theme.GOLD
                    text.textAlign = Paint.Align.LEFT
                    c.drawText(diffText, start + mw + 8f * d, midY + text.textSize * 0.36f, text)
                    text.textAlign = Paint.Align.CENTER
                } else {
                    drawMsgPill(c, left + w / 2f, midY, w, 15f * d)
                }
            }
        }
        text.textAlign = Paint.Align.CENTER
    }

    /** 이긴 사람 점수 옆 +1 떠오름 */
    private fun drawPlus(c: Canvas, i: Int, now: Long, x: Float, y: Float) {
        if (winner != i) return
        val f = ((now - plusStart).toFloat() / flashMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        if (f >= 1f) return
        text.textAlign = Paint.Align.CENTER
        text.textSize = 20f * d
        text.color = Theme.GOLD
        text.alpha = (255 * (1f - f * f)).toInt()
        c.drawText(PLUS, x, y - f * 12f * d, text)
        text.alpha = 255
    }

    /** "○○ +1" / "시간 초과!" 알약. cx·cy 가운데, maxW 최대 폭 */
    private fun drawMsgPill(c: Canvas, cx: Float, cy: Float, maxW: Float, size: Float) {
        text.textAlign = Paint.Align.CENTER
        fitText(msgText, size, maxW - 14f * d)
        val w = (text.measureText(msgText) + 14f * d).coerceAtMost(maxW)
        val h = text.textSize * 1.6f
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        fill.color = 0x66000000
        c.drawRoundRect(rect.left, rect.top + 2f * d, rect.right, rect.bottom + 2f * d, h / 2f, h / 2f, fill)
        fill.color = Theme.CARD
        c.drawRoundRect(rect, h / 2f, h / 2f, fill)
        stroke.color = msgColor
        stroke.strokeWidth = 2f * d
        c.drawRoundRect(rect, h / 2f, h / 2f, stroke)
        text.color = if (winner >= 0) Theme.GOLD else Theme.RED
        c.drawText(msgText, cx, cy + text.textSize * 0.36f, text)
    }

    /** 글자 크기를 size 로 하되 maxW 를 넘으면 줄인다 */
    private fun fitText(s: String, size: Float, maxW: Float) {
        text.textSize = size
        val w = text.measureText(s)
        if (w > maxW && maxW > 0f) text.textSize = size * maxW / w
    }

    private fun drawX(c: Canvas, cx: Float, cy: Float, s: Float) {
        stroke.color = 0xFFFFFFFF.toInt()
        stroke.strokeWidth = s * 0.45f
        c.drawLine(cx - s, cy - s, cx + s, cy + s, stroke)
        c.drawLine(cx + s, cy - s, cx - s, cy + s, stroke)
        stroke.color = Theme.RED_DARK
        stroke.strokeWidth = s * 0.26f
        c.drawLine(cx - s, cy - s, cx + s, cy + s, stroke)
        c.drawLine(cx + s, cy - s, cx - s, cy + s, stroke)
    }

    /** 격자를 어둡게 덮고 자물쇠 + 줄어드는 막대 */
    private fun drawLock(c: Canvas, i: Int, now: Long) {
        val g = grids[i]
        rect.set(g.left - 5f * d, g.top - 5f * d, g.right + 5f * d, g.bottom + 5f * d)
        fill.color = 0x99050A24.toInt()
        c.drawRoundRect(rect, 12f * d, 12f * d, fill)
        val total = (lockEnd[i] - lockStart[i]).coerceAtLeast(1)
        val left = ((lockEnd[i] - now).toFloat() / total).coerceIn(0f, 1f)
        val s = (g.size * 0.2f).coerceAtMost(34f * d)
        val cx = rect.centerX()
        val cy = rect.centerY() - s * 0.15f
        stroke.color = 0xFFFFFFFF.toInt()
        stroke.strokeWidth = s * 0.16f
        c.drawArc(cx - s * 0.32f, cy - s * 0.72f, cx + s * 0.32f, cy + s * 0.05f, 180f, 180f, false, stroke)
        c.drawLine(cx - s * 0.32f, cy - s * 0.33f, cx - s * 0.32f, cy, stroke)
        c.drawLine(cx + s * 0.32f, cy - s * 0.33f, cx + s * 0.32f, cy, stroke)
        fill.color = Theme.RED
        c.drawRoundRect(cx - s * 0.5f, cy - s * 0.05f, cx + s * 0.5f, cy + s * 0.6f, s * 0.12f, s * 0.12f, fill)
        fill.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy + s * 0.25f, s * 0.09f, fill)
        val bw = g.size * 0.6f
        val by = cy + s * 0.95f
        stroke.strokeWidth = 5f * d
        stroke.color = 0x55FFFFFF
        c.drawLine(cx - bw / 2f, by, cx + bw / 2f, by, stroke)
        stroke.color = Theme.RED
        if (left > 0.01f) c.drawLine(cx - bw / 2f, by, cx - bw / 2f + bw * left, by, stroke)
    }

    // ── 남은 시간 고리 (ringLayer) ──

    private fun ringRadius(i: Int): Float {
        val g = grids[i]
        val pad = GridLayout.PAD_DP * d
        return if (g.sideMode) ((g.width - g.right - 2f * pad) / 2f - 4f * d).coerceIn(10f * d, 26f * d)
        else ((g.headerH - pad) / 2f - 2f * d).coerceAtLeast(8f * d)
    }

    private fun drawRings(c: Canvas) {
        if (counterText.isEmpty()) return
        val now = clock.now()
        val frac = if (timerOn) (1f - (now - timerStart).toFloat() / (timerEnd - timerStart).coerceAtLeast(1)).coerceIn(0f, 1f) else 0f
        for (i in 0 until n) {
            if (!prepare(i)) continue
            val g = grids[i]
            val pad = GridLayout.PAD_DP * d
            val r = ringRadius(i)
            val cx: Float
            val cy: Float
            if (g.sideMode) {
                cx = (g.right + g.width - pad) / 2f
                cy = g.top + r + 4f * d
            } else {
                cx = g.right - r - 2f * d
                cy = pad + (g.headerH - pad) / 2f
            }
            enter(c, frames[i])
            fill.color = 0x66000000
            c.drawCircle(cx, cy + 2f * d, r, fill)
            fill.color = Theme.CARD
            c.drawCircle(cx, cy, r, fill)
            stroke.strokeWidth = r * 0.16f
            stroke.color = 0x33FFFFFF
            c.drawCircle(cx, cy, r - stroke.strokeWidth / 2f, stroke)
            if (frac > 0f) {
                stroke.color = if (frac < 0.3f) Theme.RED else Theme.GOLD
                val rr = r - stroke.strokeWidth / 2f
                rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
                c.drawArc(rect, -90f, 360f * frac, false, stroke)
            }
            text.textAlign = Paint.Align.CENTER
            text.textSize = r * 0.62f
            text.color = Theme.GOLD
            c.drawText(counterText, cx, cy + text.textSize * 0.36f, text)
            c.restore()
        }
        if (timerOn && frac > 0f && !frozen) ringLayer.postInvalidateOnAnimation()
    }

    companion object {
        /** 칸 한 변 대비 모양 기본 반지름 (크기 문제 최대 1.3배여도 칸 안) */
        const val SHAPE_RADIUS = 0.36f
        private const val TILE = 0xFF253578.toInt()
        private const val WAIT_MARK = "?"
        private const val PLUS = "+1"
        private const val TIMEOUT = "시간 초과!"
        private const val DIFF_HEAD = "다른 점"
        /** Diff 순서: 색·기울기·크기 */
        private val DIFF_TEXT = Array(Diff.values().size) { Diff.values()[it].label }
    }
}
