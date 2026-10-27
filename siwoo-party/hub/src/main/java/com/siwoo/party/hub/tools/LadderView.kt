package com.siwoo.party.hub.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import kotlin.math.hypot

/**
 * 사다리 그림 (세로줄·가로줄·가림막·따라 내려가는 색 선).
 * 칸 c 의 세로줄은 x = (c + 0.5) * 폭 / 세로줄 수 → 위 이름·아래 결과 입력 줄과 같은 칸에 맞는다.
 * 세로줄 칸을 누르면 [Listener.onStartTapped]. 연출은 [tick] 이 돌 때만. onDraw 할당 없음.
 */
class LadderView(context: Context) : View(context) {

    /** 연출 속도 배율 (PartySettings.speedFactor, 박싱 없는 Float 필드) */
    var speedFactor = 1f

    interface Listener {
        fun onStartTapped(column: Int)
        fun onArrive(start: Int, end: Int)
    }

    var listener: Listener? = null
    var ladder: Ladder? = null
        private set
    private var colors = IntArray(Ladder.MAX_COLUMNS)

    // ── 경로 (세로줄마다 꺾은선, 배치 때 계산) ──
    private val pts = Array(Ladder.MAX_COLUMNS) { FloatArray(MAX_POINTS * 2) }
    private val cum = Array(Ladder.MAX_COLUMNS) { FloatArray(MAX_POINTS) }
    private val ptCount = IntArray(Ladder.MAX_COLUMNS)
    private val ends = IntArray(Ladder.MAX_COLUMNS)

    // ── 진행 상태 ──
    /** 따라 내려간 길이 (px) */
    private val dist = FloatArray(Ladder.MAX_COLUMNS)
    /** 0 대기 / 1 출발 대기(지연) / 2 내려가는 중 / 3 도착 */
    private val state = IntArray(Ladder.MAX_COLUMNS)
    private val delay = FloatArray(Ladder.MAX_COLUMNS)
    /** 도착 칸별: 도착한 시작 칸 (-1 없음) */
    private val arrivedFrom = IntArray(Ladder.MAX_COLUMNS) { -1 }
    /** 가림막 불투명도 1 → 0 */
    var cover = 1f
        private set
    private var coverTarget = 1f

    private var top = 0f
    private var bottom = 0f
    private var markerY = 0f
    private var endY = 0f
    private val markerR = Theme.dpf(context, 13f)
    private val laneOff = Theme.dpf(context, 3f)
    private val coverRect = RectF()
    private val coverPath = Path()

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val qText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val rect = RectF()
    private val tri = Path()
    private var downX = -1f

    init {
        isClickable = true
        qText.textSize = Theme.dpf(context, 30f)
        hintText.textSize = Theme.dpf(context, 13f)
        hintText.setShadowLayer(Theme.dpf(context, 2f), 0f, Theme.dpf(context, 1f), 0xFF000000.toInt())
    }

    fun setLadder(l: Ladder, columnColors: IntArray) {
        ladder = l
        colors = columnColors
        resetProgress()
        cover = 1f
        coverTarget = 1f
        layoutPaths()
        invalidate()
    }

    private fun resetProgress() {
        dist.fill(0f)
        state.fill(0)
        delay.fill(0f)
        arrivedFrom.fill(-1)
    }

    fun columnColor(c: Int): Int = colors[c]
    fun isDone(c: Int): Boolean = state[c] == 3
    fun isBusy(c: Int): Boolean = state[c] == 1 || state[c] == 2
    fun allDone(): Boolean { val l = ladder ?: return false; return (0 until l.columns).all { state[it] == 3 } }
    fun endOf(c: Int): Int = ends[c]

    /** 세로줄 c 의 x (이 뷰 좌표) */
    fun columnX(c: Int): Float { val n = ladder?.columns ?: 1; return (c + 0.5f) * width / n }
    fun bottomY(): Float = endY

    /** 시작 칸 c 를 따라 내려가기 (지연 ms 뒤) */
    fun trace(c: Int, delayMs: Long = 0L): Boolean {
        if (state[c] != 0) return false
        state[c] = 1
        delay[c] = delayMs.toFloat()
        return true
    }

    /** 가림막을 걷는다 */
    fun reveal() { coverTarget = 0f }

    val revealed: Boolean get() = coverTarget == 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutPaths()
    }

    private fun levelY(r: Int, c: Int): Float {
        val l = ladder ?: return 0f
        val step = (bottom - top) / (l.rows + 1)
        return top + step * (r + 1 + l.shift[r][c])
    }

    private fun layoutPaths() {
        val l = ladder ?: return
        if (width == 0) return
        markerY = markerR + Theme.dpf(context, 4f)
        top = markerY + markerR + Theme.dpf(context, 10f)
        endY = height - Theme.dpf(context, 8f)
        bottom = endY - Theme.dpf(context, 12f)
        for (s in 0 until l.columns) {
            val p = pts[s]
            var k = 0
            var c = s
            fun add(x: Float, y: Float) { p[k * 2] = x; p[k * 2 + 1] = y; k++ }
            add(columnX(c), markerY + markerR)
            for (r in 0 until l.rows) {
                val nc = if (l.hasRung(r, c)) c + 1 else if (l.hasRung(r, c - 1)) c - 1 else c
                if (nc != c) {
                    // 가로줄 하나를 두 경로가 반대로 지난다 → 오른쪽으로 가는 선은 조금 위, 왼쪽은 조금 아래 (전체 공개 때 둘 다 보이게)
                    val y = levelY(r, minOf(c, nc)) + if (nc > c) -laneOff else laneOff
                    add(columnX(c), y)
                    add(columnX(nc), y)
                    c = nc
                }
            }
            add(columnX(c), endY - Theme.dpf(context, 4f))
            ptCount[s] = k
            ends[s] = c
            val cu = cum[s]
            cu[0] = 0f
            for (i in 1 until k) cu[i] = cu[i - 1] + hypot(p[i * 2] - p[i * 2 - 2], p[i * 2 + 1] - p[i * 2 - 1])
        }
        val n = l.columns
        val pad = minOf(width / n * 0.32f, Theme.dpf(context, 34f))
        coverRect.set(columnX(0) - pad, top - Theme.dpf(context, 2f), columnX(n - 1) + pad, bottom + Theme.dpf(context, 2f))
        coverPath.reset()
        coverPath.addRoundRect(coverRect, COVER_R * resources.displayMetrics.density, COVER_R * resources.displayMetrics.density, Path.Direction.CW)
    }

    private fun pathLength(s: Int): Float = cum[s][ptCount[s] - 1]

    /** 한 프레임. 움직이는 것이 있으면 true */
    fun tick(dt: Long): Boolean {
        val l = ladder ?: return false
        var moving = false
        val d = dt.toFloat()
        if (cover != coverTarget) {
            val step = d / (COVER_MS * speedFactor).coerceAtLeast(1f)
            cover = if (coverTarget < cover) (cover - step).coerceAtLeast(coverTarget) else (cover + step).coerceAtMost(coverTarget)
            moving = true
        }
        val traceMs = (TRACE_MS * speedFactor).coerceAtLeast(1f)
        for (s in 0 until l.columns) {
            when (state[s]) {
                1 -> { delay[s] -= d; if (delay[s] <= 0f) state[s] = 2; moving = true }
                2 -> {
                    val len = pathLength(s)
                    // 길이와 상관없이 비슷한 시간에 도착하도록 (기본 높이 기준 속도, 최소 보장)
                    val v = maxOf(len, height * 1.6f) / traceMs
                    dist[s] = (dist[s] + v * d).coerceAtMost(len)
                    moving = true
                    if (dist[s] >= len) {
                        state[s] = 3
                        arrivedFrom[ends[s]] = s
                        listener?.onArrive(s, ends[s])
                    }
                }
            }
        }
        invalidate()
        return moving
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val l = ladder ?: return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> downX = e.x
            MotionEvent.ACTION_UP -> {
                if (downX >= 0f && width > 0) {
                    val c = (e.x * l.columns / width).toInt().coerceIn(0, l.columns - 1)
                    listener?.onStartTapped(c)
                }
                downX = -1f
            }
            MotionEvent.ACTION_CANCEL -> downX = -1f
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val l = ladder ?: return
        val n = l.columns
        val stroke = Theme.dpf(context, 4f)
        // 가로줄 (가림막 아래)
        line.strokeWidth = stroke
        line.color = Theme.GOLD_LIGHT
        line.alpha = 255
        for (r in 0 until l.rows) for (k in 0 until n - 1) if (l.rungs[r][k]) {
            val y = levelY(r, k)
            c.drawLine(columnX(k), y, columnX(k + 1), y, line)
        }
        // 가림막
        if (cover > 0f) drawCover(c, n)
        // 세로줄 (가려도 보인다)
        line.color = Color.WHITE
        line.alpha = 200
        line.strokeWidth = stroke
        for (k in 0 until n) c.drawLine(columnX(k), markerY + markerR, columnX(k), endY - Theme.dpf(context, 4f), line)
        // 안내 (가림막 아래쪽, 어두운 알약 위)
        if (cover >= 1f) {
            val hw = hintText.measureText(HINT) / 2f + Theme.dpf(context, 12f)
            val cx = (coverRect.left + coverRect.right) / 2f
            val cy = coverRect.bottom - hintText.textSize * 1.3f
            rect.set(cx - hw, cy - hintText.textSize * 0.95f, cx + hw, cy + hintText.textSize * 0.55f)
            fill.color = PILL
            c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fill)
            c.drawText(HINT, cx, cy, hintText)
        }
        // 따라 내려간 색 선
        for (s in 0 until n) if (dist[s] > 0f) drawTrace(c, s)
        // 도착 점
        for (k in 0 until n) {
            val from = arrivedFrom[k]
            fill.color = if (from >= 0) colors[from] else Theme.GRAY
            c.drawCircle(columnX(k), endY - Theme.dpf(context, 4f), if (from >= 0) Theme.dpf(context, 7f) else Theme.dpf(context, 4f), fill)
        }
        // 출발 단추 (▼)
        for (k in 0 until n) {
            val x = columnX(k)
            if (state[k] == 3) {
                fill.color = Theme.GOLD
                c.drawCircle(x, markerY, markerR + Theme.dpf(context, 3f), fill)
            }
            fill.color = colors[k]
            c.drawCircle(x, markerY, markerR, fill)
            tri.reset()
            val s = markerR * 0.5f
            tri.moveTo(x - s, markerY - s * 0.55f)
            tri.lineTo(x + s, markerY - s * 0.55f)
            tri.lineTo(x, markerY + s * 0.75f)
            tri.close()
            fill.color = Theme.textOn(colors[k])
            c.drawPath(tri, fill)
        }
    }

    private fun drawCover(c: Canvas, n: Int) {
        val a = (cover * 255).toInt()
        rect.set(coverRect)
        val r = Theme.dpf(context, COVER_R)
        fill.color = Theme.CARD_LIGHT
        fill.alpha = a
        c.drawRoundRect(rect, r, r, fill)
        // 빗금
        c.save()
        c.clipPath(coverPath)
        line.color = Theme.CARD
        line.alpha = a
        line.strokeWidth = Theme.dpf(context, 7f)
        val gap = Theme.dpf(context, 22f)
        var x = rect.left - rect.height()
        while (x < rect.right) {
            c.drawLine(x, rect.bottom, x + rect.height(), rect.top, line)
            x += gap
        }
        c.restore()
        line.color = Theme.GOLD
        line.alpha = a
        line.strokeWidth = Theme.dpf(context, 2.5f)
        c.drawRoundRect(rect, r, r, line)
        // 세로줄 사이마다 "?"
        qText.color = Theme.GOLD
        qText.alpha = (a * 0.85f).toInt()
        val cy = (rect.top + rect.bottom) / 2f + qText.textSize * 0.35f
        for (k in 0 until n - 1) c.drawText(QUESTION, (columnX(k) + columnX(k + 1)) / 2f, cy, qText)
    }

    private fun drawTrace(c: Canvas, s: Int) {
        val p = pts[s]
        val cu = cum[s]
        val k = ptCount[s]
        val d = dist[s]
        var hx = p[0]
        var hy = p[1]
        for (pass in 0..1) {
            if (pass == 0) {
                line.color = 0xFF0A0F2E.toInt(); line.alpha = 200; line.strokeWidth = Theme.dpf(context, 10f)
            } else {
                line.color = colors[s]; line.alpha = 255; line.strokeWidth = Theme.dpf(context, 6f)
            }
            for (i in 1 until k) {
                if (cu[i - 1] >= d) break
                val x0 = p[i * 2 - 2]; val y0 = p[i * 2 - 1]
                var x1 = p[i * 2]; var y1 = p[i * 2 + 1]
                if (cu[i] > d) {
                    val f = (d - cu[i - 1]) / (cu[i] - cu[i - 1])
                    x1 = x0 + (x1 - x0) * f
                    y1 = y0 + (y1 - y0) * f
                }
                c.drawLine(x0, y0, x1, y1, line)
                hx = x1; hy = y1
            }
        }
        if (state[s] == 2) {
            fill.color = Color.WHITE
            c.drawCircle(hx, hy, Theme.dpf(context, 9f), fill)
            fill.color = colors[s]
            c.drawCircle(hx, hy, Theme.dpf(context, 6.5f), fill)
        }
    }

    companion object {
        /** 경로 꼭짓점 최대 (시작 + 단마다 2 + 끝) */
        const val MAX_POINTS = Ladder.ROWS * 2 + 2
        /** 한 줄 따라 내려가기 (× 속도 배율) */
        const val TRACE_MS = 1700L
        const val COVER_MS = 500L
        const val COVER_R = 14f
        const val PILL = 0xE00A0F2E.toInt()
        const val QUESTION = "?"
        const val HINT = "▼ 를 누르면 사다리를 타요"
    }
}
