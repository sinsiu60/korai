package com.siwoo.party.games.sequence.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.sequence.GameConfig
import com.siwoo.party.games.sequence.SequenceGame
import com.siwoo.party.games.sequence.ZoneFrame

/**
 * 숫자 순서 터치 그림 층 (MultiTouchZoneView 위에 겹침, 터치는 받지 않음).
 * 구역마다 같은 4×4 배치를 그 플레이어 쪽으로 돌려 그린다 (위쪽 구역 180°).
 * - 넓적한 구역(3·4인): 플레이어 기준 왼쪽에 정보 칸(이름·다음 숫자·합계), 오른쪽에 격자를 꽉 차게
 * - 키 큰 구역(2인): 위쪽(화면 가운데 반대편)에 정보 줄, 아래에 격자
 * 누른 숫자는 플레이어 색으로 채워지고, 틀리면 그 칸이 빨갛게 흔들리며 격자가 0.5초 얼어붙는다.
 * 애니메이션은 진행 중일 때만 다시 그린다.
 */
class SequenceBoardView(
    context: Context,
    private val zoneView: MultiTouchZoneView,
    private val game: SequenceGame,
    private val names: List<String>,
    private val colors: IntArray,
) : View(context) {

    fun interface Clock { fun now(): Long }

    var clock: Clock = Clock { SystemClock.uptimeMillis() }
    /** 일시정지 중엔 애니메이션 루프를 멈춘다 */
    var frozen = false
        set(v) { field = v; if (!v) invalidate() }
    var flashMs = 450L

    private val n = names.size
    /** false = 라운드 시작 전 (숫자를 가린 뒷면) */
    private var revealed = false
    /** 라운드 결과 표시 중 */
    private var showResult = false
    private var roundText = ""
    private val totalText = Array(n) { "합계 0점" }
    private val wrongCell = IntArray(n) { -1 }
    private val wrongStart = LongArray(n)
    private val freezeEnd = LongArray(n) { Long.MIN_VALUE }
    private val freezeStart = LongArray(n)
    private val doneStart = LongArray(n)
    /** 이번 라운드 끝낸 등수 (0 = 아직) */
    private val donePlace = IntArray(n)

    // ── 구역별 배치 (플레이어 기준 좌표) ──
    private val zw = FloatArray(n)
    private val zh = FloatArray(n)
    private val tall = BooleanArray(n)
    private val info = Array(n) { RectF() }
    private val grid = Array(n) { RectF() }
    private val cells = Array(n) { Array(GameConfig.CELLS) { RectF() } }
    private val pt = FloatArray(2)

    private val d = Theme.dpf(context, 1f)
    private val rect = RectF()
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    // ── 상태 ──

    /** 라운드 시작 전(가림) / 진행 */
    fun setRevealed(v: Boolean) {
        revealed = v
        showResult = false
        for (i in 0 until n) { wrongCell[i] = -1; freezeEnd[i] = Long.MIN_VALUE; donePlace[i] = 0 }
        invalidate()
    }

    fun setRound(round: Int, total: Int) {
        roundText = "$round/$total"
        invalidate()
    }

    fun setTotal(i: Int, points: Int) {
        totalText[i] = "합계 ${points}점"
        invalidate()
    }

    fun showWrong(i: Int, cell: Int, untilMs: Long) {
        val now = clock.now()
        wrongCell[i] = cell
        wrongStart[i] = now
        freezeStart[i] = now
        freezeEnd[i] = untilMs
        invalidate()
    }

    fun showFinished(i: Int, place: Int) {
        donePlace[i] = place
        doneStart[i] = clock.now()
        invalidate()
    }

    /** 라운드 결과 (game.roundPlaces / roundPoints) */
    fun showRoundResult() {
        showResult = true
        for (i in 0 until n) if (donePlace[i] == 0) doneStart[i] = clock.now()
        invalidate()
    }

    // ── 배치·판정 ──

    private fun zoneOf(i: Int): Zone? {
        val z = zoneView.zone(i) ?: return null
        if (z.width != zw[i] || z.height != zh[i]) layoutZone(i, z.width, z.height)
        return z
    }

    private fun layoutZone(i: Int, w: Float, h: Float) {
        zw[i] = w
        zh[i] = h
        val pad = 9f * d
        val gap = CELL_GAP_DP * d
        tall[i] = h >= w * 0.8f
        if (tall[i]) {
            info[i].set(pad, pad, w - pad, pad + 38f * d)
            grid[i].set(pad, info[i].bottom + 6f * d, w - pad, h - pad)
        } else {
            val iw = (w * 0.2f).coerceIn(56f * d, 72f * d)
            info[i].set(pad, pad, pad + iw, h - pad)
            grid[i].set(info[i].right + 6f * d, pad, w - pad, h - pad)
        }
        val g = grid[i]
        val side = GameConfig.GRID_SIDE
        val cw = (g.width() - (side - 1) * gap) / side
        val ch = (g.height() - (side - 1) * gap) / side
        for (r in 0 until side) for (c in 0 until side) {
            val l = g.left + c * (cw + gap)
            val t = g.top + r * (ch + gap)
            cells[i][r * side + c].set(l, t, l + cw, t + ch)
        }
    }

    /** 화면 좌표 (x, y) 가 zone 구역의 몇 번 칸인지 (없으면 -1) */
    fun cellAt(zone: Int, x: Float, y: Float): Int {
        val z = zoneOf(zone) ?: return -1
        ZoneFrame.toLocal(z, x, y, pt)
        val s = CELL_GAP_DP * d / 2f
        val cs = cells[zone]
        for (k in cs.indices) {
            val r = cs[k]
            if (pt[0] >= r.left - s && pt[0] < r.right + s && pt[1] >= r.top - s && pt[1] < r.bottom + s) return k
        }
        return -1
    }

    /** zone 구역 cell 칸 가운데의 화면 좌표 → out */
    fun cellCenter(zone: Int, cell: Int, out: PointF) {
        val z = zoneOf(zone) ?: return
        val r = cells[zone][cell]
        ZoneFrame.toScreen(z, r.centerX(), r.centerY(), pt)
        out.set(pt[0], pt[1])
    }

    /** zone 구역 가운데 (격자 가운데)의 화면 좌표 → out */
    fun gridCenter(zone: Int, out: PointF) {
        val z = zoneOf(zone) ?: return
        ZoneFrame.toScreen(z, grid[zone].centerX(), grid[zone].centerY(), pt)
        out.set(pt[0], pt[1])
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val now = clock.now()
        var animating = false
        for (i in 0 until n) {
            val z = zoneOf(i) ?: continue
            c.save()
            c.translate(z.left, z.top)
            c.rotate(z.rotation, z.width / 2f, z.height / 2f)
            if (drawZone(c, i, now)) animating = true
            c.restore()
        }
        drawRoundBadge(c)
        if (animating && !frozen) postInvalidateOnAnimation()
    }

    private fun drawZone(c: Canvas, i: Int, now: Long): Boolean {
        var anim = false
        drawInfo(c, i)
        // 격자 바탕
        val g = grid[i]
        rect.set(g); rect.inset(-4f * d, -4f * d)
        fill.color = 0xCC070D2E.toInt()
        c.drawRoundRect(rect, 12f * d, 12f * d, fill)
        val next = game.next[i]
        val wf = if (wrongCell[i] >= 0) ((now - wrongStart[i]).toFloat() / flashMs.coerceAtLeast(1)).coerceIn(0f, 1f) else 1f
        if (wf < 1f) anim = true
        val cs = cells[i]
        for (k in cs.indices) {
            val r = cs[k]
            val number = game.layout[k]
            val rad = minOf(r.width(), r.height()) * 0.2f
            val depth = 3f * d
            if (!revealed) {
                fill.color = 0xFF2B3A80.toInt()
                c.drawRoundRect(r, rad, rad, fill)
                fill.color = 0x22FFFFFF
                c.drawCircle(r.centerX(), r.centerY(), minOf(r.width(), r.height()) * 0.18f, fill)
                continue
            }
            val pressed = number < next
            val wrong = k == wrongCell[i] && wf < 1f
            val dx = if (wrong) (Math.sin(wf * Math.PI * 6.0) * (1f - wf) * 4f * d).toFloat() else 0f
            val face = when {
                wrong -> WRONG_FACE
                pressed -> colors[i]
                else -> Theme.PAPER
            }
            if (!pressed) {
                fill.color = if (wrong) Theme.RED_DARK else 0xFFC9BFA4.toInt()
                c.drawRoundRect(r.left + dx, r.top + depth, r.right + dx, r.bottom, rad, rad, fill)
                fill.color = face
                c.drawRoundRect(r.left + dx, r.top, r.right + dx, r.bottom - depth, rad, rad, fill)
                if (wrong) {
                    // 틀린 칸: 어두운 바탕 + 빨간 테두리 (어떤 플레이어 색과도 헷갈리지 않게)
                    stroke.color = Theme.RED
                    stroke.strokeWidth = 3f * d
                    c.drawRoundRect(r.left + dx + 1.5f * d, r.top + 1.5f * d, r.right + dx - 1.5f * d, r.bottom - depth - 1.5f * d, rad, rad, stroke)
                }
            } else {
                // 눌린 칸: 내려앉은 플레이어 색
                fill.color = face
                c.drawRoundRect(r.left, r.top + depth, r.right, r.bottom, rad, rad, fill)
            }
            num.textSize = minOf((r.height() - depth) * 0.72f, r.width() * 0.5f)
            num.color = if (pressed) Theme.textOn(face) else if (wrong) Theme.RED else Theme.INK
            if (pressed) num.alpha = 170
            val cy = (if (pressed) r.centerY() + depth / 2f else r.centerY() - depth / 2f) + num.textSize * 0.36f
            c.drawText(NUMS[number], r.centerX() + dx, cy, num)
            num.alpha = 255
        }
        // 얼음 (정지)
        if (now < freezeEnd[i]) {
            anim = true
            drawFreeze(c, i, now)
        }
        // 끝냄 / 라운드 결과
        val place = if (showResult) game.roundPlaces[i] else donePlace[i]
        if (place > 0) {
            val f = ((now - doneStart[i]).toFloat() / flashMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            if (f < 1f) anim = true
            drawPlace(c, i, place, f)
        }
        return anim
    }

    private fun drawInfo(c: Canvas, i: Int) {
        val r = info[i]
        fill.color = 0xCC0A1236.toInt()
        c.drawRoundRect(r, 12f * d, 12f * d, fill)
        stroke.color = 0x33FFFFFF
        stroke.strokeWidth = 1.2f * d
        c.drawRoundRect(r, 12f * d, 12f * d, stroke)
        val next = game.next[i]
        val done = game.isFinished(i)
        if (tall[i]) {
            // 한 줄: 이름(왼쪽) · 다음 숫자(가운데) · 합계(오른쪽)
            val cy = r.centerY()
            text.textAlign = Paint.Align.LEFT
            fitText(names[i], r.width() * 0.3f, 16f * d)
            text.color = colors[i]
            c.drawText(names[i], r.left + 12f * d, cy + text.textSize * 0.36f, text)
            text.textAlign = Paint.Align.RIGHT
            text.textSize = 13f * d
            text.color = Theme.GOLD
            c.drawText(totalText[i], r.right - 12f * d, cy + text.textSize * 0.36f, text)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 12f * d
            text.color = Theme.TEXT_SUB
            c.drawText(NEXT, r.centerX() - 26f * d, cy + text.textSize * 0.36f, text)
            drawNextBubble(c, r.centerX() + 14f * d, cy, r.height() * 0.42f, next, done)
        } else {
            val cx = r.centerX()
            text.textAlign = Paint.Align.CENTER
            fitText(names[i], r.width() - 8f * d, 14f * d)
            text.color = colors[i]
            c.drawText(names[i], cx, r.top + 8f * d + text.textSize, text)
            text.textSize = 11f * d
            text.color = Theme.TEXT_SUB
            val bubbleR = minOf(r.width() * 0.36f, r.height() * 0.2f)
            val by = r.centerY() + 2f * d
            c.drawText(NEXT, cx, by - bubbleR - 5f * d, text)
            drawNextBubble(c, cx, by, bubbleR, next, done)
            text.textSize = 11f * d
            text.color = Theme.GOLD
            c.drawText(totalText[i], cx, r.bottom - 8f * d, text)
        }
        text.textAlign = Paint.Align.CENTER
    }

    /** 이름이 칸보다 길면 글자 크기를 줄인다 (할당 없음) */
    private fun fitText(s: String, maxW: Float, size: Float) {
        text.textSize = size
        val w = text.measureText(s)
        if (w > maxW) text.textSize = size * maxW / w
    }

    /** 다음에 누를 숫자 (금색 원), 끝냈으면 체크 */
    private fun drawNextBubble(c: Canvas, cx: Float, cy: Float, r: Float, next: Int, done: Boolean) {
        fill.color = 0x66000000
        c.drawCircle(cx, cy + 2f * d, r, fill)
        fill.color = if (done) Theme.GREEN else Theme.GOLD
        c.drawCircle(cx, cy, r, fill)
        if (done) {
            stroke.color = 0xFFFFFFFF.toInt()
            stroke.strokeWidth = r * 0.22f
            c.drawLine(cx - r * 0.45f, cy, cx - r * 0.1f, cy + r * 0.35f, stroke)
            c.drawLine(cx - r * 0.1f, cy + r * 0.35f, cx + r * 0.5f, cy - r * 0.35f, stroke)
            return
        }
        if (!revealed) return
        num.textSize = r * (if (next >= 10) 1.0f else 1.2f)
        num.color = Theme.INK
        c.drawText(NUMS[next.coerceAtMost(GameConfig.CELLS)], cx, cy + num.textSize * 0.36f, num)
    }

    /** 정지: 격자에 차가운 막 + 자물쇠 대신 "정지" + 줄어드는 막대 */
    private fun drawFreeze(c: Canvas, i: Int, now: Long) {
        val g = grid[i]
        rect.set(g); rect.inset(-4f * d, -4f * d)
        fill.color = 0x66FF4D5E
        c.drawRoundRect(rect, 12f * d, 12f * d, fill)
        stroke.color = Theme.RED
        stroke.strokeWidth = 3f * d
        c.drawRoundRect(rect, 12f * d, 12f * d, stroke)
        val total = (freezeEnd[i] - freezeStart[i]).coerceAtLeast(1)
        val left = ((freezeEnd[i] - now).toFloat() / total).coerceIn(0f, 1f)
        val bw = g.width() * 0.5f
        val bh = minOf(g.height() * 0.2f, 34f * d)
        rect.set(g.centerX() - bw / 2f, g.centerY() - bh / 2f, g.centerX() + bw / 2f, g.centerY() + bh / 2f)
        fill.color = 0xE6070D2E.toInt()
        c.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
        text.textSize = bh * 0.5f
        text.color = 0xFFFFFFFF.toInt()
        c.drawText(STOP, rect.centerX(), rect.centerY() + text.textSize * 0.25f, text)
        stroke.strokeWidth = 3f * d
        stroke.color = Theme.RED
        val y = rect.bottom - 5f * d
        if (left > 0.01f) c.drawLine(rect.left + bh * 0.4f, y, rect.left + bh * 0.4f + (rect.width() - bh * 0.8f) * left, y, stroke)
    }

    /** 끝낸 등수 (1등 왕관) + 라운드 결과면 받은 점수 */
    private fun drawPlace(c: Canvas, i: Int, place: Int, f: Float) {
        val g = grid[i]
        rect.set(g); rect.inset(-4f * d, -4f * d)
        fill.color = if (showResult) 0xE0050A24.toInt() else 0xB3050A24.toInt()
        c.drawRoundRect(rect, 12f * d, 12f * d, fill)
        val pop = 1f + 0.25f * (1f - f) * (1f - f)
        val size = minOf(g.height() * 0.3f, g.width() * 0.2f, 56f * d) * pop
        val cy = g.centerY() + (if (showResult) -size * 0.1f else size * 0.25f)
        if (place == 1) Ui.drawCrown(c, g.centerX(), cy - size * 0.95f, size * 0.9f, path, fill)
        text.textSize = size
        text.color = if (place == 1) Theme.GOLD else 0xFFFFFFFF.toInt()
        c.drawText(PLACES[place.coerceIn(1, PLACES.size - 1)], g.centerX(), cy + size * 0.36f, text)
        if (showResult) {
            val pts = game.roundPoints[i]
            text.textSize = size * 0.5f
            text.color = Theme.GOLD_LIGHT
            c.drawText(POINTS[pts.coerceIn(0, POINTS.size - 1)], g.centerX(), cy + size * 0.36f + size * 0.75f, text)
        }
    }

    /** 라운드 번호 (2인 위 가운데, 3인 왼쪽 아래 빈 곳, 4인 가운데) */
    private fun drawRoundBadge(c: Canvas) {
        if (roundText.isEmpty() || width == 0) return
        val r = if (n == 3) 28f * d else 19f * d
        val cx: Float
        val cy: Float
        when (n) {
            2 -> { cx = width / 2f; cy = r + 6f * d }
            3 -> { cx = width / 8f; cy = height * 0.75f }
            else -> { cx = width / 2f; cy = height / 2f }
        }
        fill.color = 0x66000000
        c.drawCircle(cx, cy + 2f * d, r, fill)
        fill.color = Theme.CARD
        c.drawCircle(cx, cy, r, fill)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = 2.5f * d
        c.drawCircle(cx, cy, r - 1.5f * d, stroke)
        text.textAlign = Paint.Align.CENTER
        text.textSize = r * 0.62f
        text.color = Theme.GOLD
        c.drawText(roundText, cx, cy + text.textSize * 0.36f, text)
    }

    companion object {
        private const val CELL_GAP_DP = 5f
        private val NUMS = Array(GameConfig.CELLS + 1) { it.toString() }
        private val PLACES = arrayOf("", "1등", "2등", "3등", "4등")
        private val POINTS = arrayOf("+0점", "+1점", "+2점", "+3점", "+4점")
        private const val NEXT = "다음"
        private const val STOP = "정지!"
        private const val WRONG_FACE = 0xFF2A0A18.toInt()
    }
}
