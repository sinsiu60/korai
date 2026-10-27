package com.siwoo.party.games.flood.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.flood.Flood
import com.siwoo.party.games.flood.FloodGame

/**
 * 색 번지기 판 (Canvas 하나로 14 × 14 칸을 그린다).
 * - 주인 없는 칸: 틈이 있는 둥근 타일 / 주인 있는 칸: 틈 없이 꽉 채워 한 덩어리 + 둘레에 주인 색 경계선
 *   (칸 색 = 고른 색이라도 경계선과 본진 표시로 누구 땅인지 보인다)
 * - 본진(모서리)에는 주인 색 동그라미 + 이름 첫 글자, 지금 차례 사람 땅은 경계선이 굵고 금빛이 돈다
 * - 색을 고르면: 원래 땅이 본진부터 새 색으로 물들고(물결), 이어서 흡수하는 칸이 원래 땅에서 가까운 순으로 톡톡 붙는다
 * - 색 버튼을 누르고 있는 동안 흡수될 칸을 흰 테두리로 미리 보여 준다
 * 판은 게임 상태를 직접 읽지 않고 자기 사본(보이는 상태)을 연출 시간에 맞춰 바꾼다.
 * 연출 중일 때만 postOnAnimation 으로 다시 그리고, onDraw 에서는 객체를 만들지 않는다.
 */
class FloodBoardView(context: Context) : View(context) {

    var size = 0
        private set
    private var cellCount = 0

    // ── 보이는 상태 (연출이 끝난 뒤 모습) ──
    private var color = IntArray(0)
    private var owner = IntArray(0)
    // 연출 전 모습 + 연출 시작 시각·종류
    private var prevColor = IntArray(0)
    private var prevOwner = IntArray(0)
    private var start = LongArray(0)
    private var kind = IntArray(0)
    private var popMs = 1L
    /** 미리보기 (이 색을 고르면 흡수될 칸) */
    private var preview = BooleanArray(0)
    private var previewCount = 0

    private var homes = IntArray(0)
    private var playerColors = IntArray(0)
    private var initials = emptyArray<String>()
    /** 굵은 경계선으로 강조할 사람 (-1 = 없음) */
    var current = -1
        private set
    private var animEnd = 0L

    // ── 배치 ──
    /** 왼쪽 위 칸 모서리 (뷰 좌표) */
    var originX = 0f
        private set
    var originY = 0f
        private set
    /** 칸 한 변 (px) */
    var cell = 0f
        private set
    private val panel = RectF()
    /** 경계선 굵기 (line 의 굵기는 그릴 때마다 바뀌므로 따로 둔다) */
    private var lineW = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    /** 땅 칸은 안티앨리어싱 없이 (이웃 칸과 틈 없이 붙게) */
    private val solid = Paint()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Theme.GOLD_LIGHT }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt() }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private var textColors = IntArray(0)

    private var looping = false
    private val frame = Runnable { onFrame() }

    init {
        contentDescription = "색 번지기 판"
    }

    private fun now(): Long = SystemClock.uptimeMillis()

    /** 판 준비: 게임의 지금 모습을 복사 */
    fun setup(g: FloodGame, colors: IntArray, names: Array<String>) {
        size = g.size
        cellCount = g.cellCount
        color = IntArray(cellCount) { g.colorAt(it) }
        owner = IntArray(cellCount) { g.ownerAt(it) }
        prevColor = color.copyOf()
        prevOwner = owner.copyOf()
        start = LongArray(cellCount)
        kind = IntArray(cellCount)
        preview = BooleanArray(cellCount)
        previewCount = 0
        homes = g.home.copyOf()
        playerColors = colors
        textColors = IntArray(colors.size) { Theme.textOn(colors[it]) }
        initials = Array(names.size) { names[it].take(1) }
        layoutGrid()
        invalidate()
    }

    val isReady: Boolean get() = cell > 0f && cellCount > 0

    /** 지금 보이는(연출이 끝난 뒤) 주인 / 색 — 테스트용 */
    fun shownOwner(cell: Int): Int = owner[cell]
    fun shownColor(cell: Int): Int = color[cell]
    val previewSize: Int get() = previewCount

    /** 강조할 사람 (지금 차례) */
    fun setCurrent(p: Int) {
        if (current == p) return
        current = p
        invalidate()
    }

    /** 미리보기 칸 (null·빈 배열 = 지우기) */
    fun setPreview(cells: IntArray?) {
        if (previewCount == 0 && (cells == null || cells.isEmpty())) return
        java.util.Arrays.fill(preview, false)
        previewCount = 0
        if (cells != null) for (c in cells) { preview[c] = true; previewCount++ }
        invalidate()
    }

    /**
     * 색 고르기 연출 시작. 시간은 이미 속도 배율이 적용된 값.
     * 원래 땅: 본진에서의 거리 × recolorStep (전체 recolorMax 이하) 뒤 색이 바뀜,
     * 흡수 칸: 원래 땅 물결 뒤 거리 × absorbStep (전체 absorbMax 이하) 뒤 톡.
     * 연출 전체 시간(ms)을 돌려준다.
     */
    fun play(f: Flood, recolorStep: Long, recolorMax: Long, absorbStep: Long, absorbMax: Long, pop: Long): Long {
        setPreview(null)
        val t0 = now()
        // 이전 연출이 남아 있으면 끝난 모습으로
        for (k in 0 until cellCount) { prevColor[k] = color[k]; prevOwner[k] = owner[k]; kind[k] = KIND_NONE }
        popMs = pop.coerceAtLeast(1L)
        val maxR = f.maxRecolorDepth
        val rStep = if (maxR <= 0) 0L else minOf(recolorStep, recolorMax / maxR)
        for (k in f.recolored.indices) {
            val cell = f.recolored[k]
            prevColor[cell] = f.oldColor
            color[cell] = f.color
            start[cell] = t0 + f.recolorDepth[k] * rStep
            kind[cell] = KIND_RECOLOR
        }
        val absorbStart = maxR * rStep + popMs / 2
        val maxA = f.maxAbsorbDepth
        val aStep = if (maxA <= 1) 0L else minOf(absorbStep, absorbMax / (maxA - 1))
        for (k in f.absorbed.indices) {
            val cell = f.absorbed[k]
            prevOwner[cell] = FloodGame.NONE
            prevColor[cell] = color[cell]
            owner[cell] = f.player
            color[cell] = f.color
            start[cell] = t0 + absorbStart + (f.absorbDepth[k] - 1) * aStep
            kind[cell] = KIND_ABSORB
        }
        val total = if (f.absorbed.isEmpty()) maxR * rStep + popMs else absorbStart + (maxA - 1).coerceAtLeast(0) * aStep + popMs
        animEnd = t0 + total
        ensureRunning()
        return total
    }

    /** 연출 중인지 */
    val isAnimating: Boolean get() = looping

    /** 칸 가운데 (이 뷰 좌표) */
    fun cellCenter(cell: Int, out: PointF) {
        out.set(originX + (cell % size + 0.5f) * this.cell, originY + (cell / size + 0.5f) * this.cell)
    }

    private fun ensureRunning() {
        invalidate()
        if (looping) return
        looping = true
        postOnAnimation(frame)
    }

    private fun onFrame() {
        if (!looping) return
        invalidate()
        if (now() < animEnd) postOnAnimation(frame) else looping = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutGrid()
    }

    private fun layoutGrid() {
        if (cellCount == 0 || width <= 0 || height <= 0) return
        val side = minOf(width, height).toFloat()
        cell = side / (size + 0.5f)
        val gw = cell * size
        originX = (width - gw) / 2f
        originY = (height - gw) / 2f
        val m = cell * 0.25f
        panel.set(originX - m, originY - m, originX + gw + m, originY + gw + m)
        lineW = cell * 0.1f
        glow.strokeWidth = cell * 0.36f
        ring.strokeWidth = cell * 0.09f
        initPaint.textSize = cell * 0.5f
    }

    // ── 보이는 상태 (시각 t) ──

    private fun ownerAt(k: Int, t: Long): Int = if (kind[k] != KIND_NONE && t < start[k]) prevOwner[k] else owner[k]

    override fun onDraw(c: Canvas) {
        if (!isReady) return
        val t = now()
        val s = cell

        // 판 바탕
        val pr = s * 0.4f
        fill.style = Paint.Style.FILL
        fill.color = FloodArt.BOARD_BG
        c.drawRoundRect(panel, pr, pr, fill)

        // 칸
        for (k in 0 until cellCount) {
            val l = originX + (k % size) * s
            val tp = originY + (k / size) * s
            val kd = kind[k]
            val o = ownerAt(k, t)
            if (kd == KIND_NONE || t >= start[k] + popMs) {
                if (o < 0) FloodArt.tile(c, fill, l, tp, s, color[k]) else FloodArt.owned(c, solid, l, tp, s, color[k])
                continue
            }
            if (t < start[k]) {
                // 아직 차례가 안 옴: 예전 모습
                if (prevOwner[k] < 0) FloodArt.tile(c, fill, l, tp, s, prevColor[k]) else FloodArt.owned(c, solid, l, tp, s, prevColor[k])
                continue
            }
            val f = (t - start[k]).toFloat() / popMs
            if (kd == KIND_RECOLOR) {
                // 원래 땅: 색이 스르르 바뀌며 하얗게 반짝
                solid.color = mix(FloodArt.PALETTE[prevColor[k]], FloodArt.PALETTE[color[k]], f)
                c.drawRect(l, tp, l + s, tp + s, solid)
                fill.color = 0xFFFFFFFF.toInt()
                fill.alpha = ((1f - f) * 90).toInt()
                c.drawRect(l, tp, l + s, tp + s, fill)
                fill.alpha = 255
            } else {
                // 흡수: 타일이 톡 커지며 땅에 붙음
                val q = f - 1f
                val sc = 0.55f + 0.45f * (1f + 2.70158f * q * q * q + 1.70158f * q * q)
                val half = s * 0.5f * sc
                val cx = l + s * 0.5f
                val cy = tp + s * 0.5f
                fill.color = FloodArt.PALETTE[color[k]]
                c.drawRoundRect(cx - half, cy - half, cx + half, cy + half, s * 0.2f * (1f - f), s * 0.2f * (1f - f), fill)
                fill.color = 0xFFFFFFFF.toInt()
                fill.alpha = ((1f - f) * 200).toInt()
                c.drawRoundRect(cx - half, cy - half, cx + half, cy + half, s * 0.2f, s * 0.2f, fill)
                fill.alpha = 255
            }
        }

        // 미리보기: 흡수될 칸에 흰 테
        if (previewCount > 0) {
            val g = s * 0.16f
            for (k in 0 until cellCount) {
                if (!preview[k]) continue
                val l = originX + (k % size) * s
                val tp = originY + (k / size) * s
                c.drawRoundRect(l + g, tp + g, l + s - g, tp + s - g, s * 0.18f, s * 0.18f, ring)
            }
        }

        // 지금 차례 땅: 경계 바깥 금빛
        val cur = current
        if (cur >= 0) {
            glow.alpha = 110
            for (k in 0 until cellCount) if (ownerAt(k, t) == cur) edges(c, k, cur, t, PASS_GLOW)
        }
        // 경계선: 어두운 테두리를 모두 먼저, 그 위에 주인 색 (마디가 끊겨 보이지 않게)
        line.style = Paint.Style.STROKE
        line.strokeCap = Paint.Cap.ROUND
        line.color = Theme.INK
        for (k in 0 until cellCount) {
            val o = ownerAt(k, t)
            if (o >= 0) edges(c, k, o, t, PASS_INK)
        }
        for (k in 0 until cellCount) {
            val o = ownerAt(k, t)
            if (o >= 0) edges(c, k, o, t, PASS_COLOR)
        }

        // 본진 표시
        for (p in homes.indices) {
            val k = homes[p]
            val cx = originX + (k % size + 0.5f) * s
            val cy = originY + (k / size + 0.5f) * s
            FloodArt.badge(c, fill, cx, cy, s * 0.36f, playerColors[p])
            initPaint.color = textColors[p]
            c.drawText(initials[p], cx, cy + initPaint.textSize * 0.36f, initPaint)
        }
    }

    /** 칸 k (주인 o) 의 네 변 중 이웃 주인이 다른 변에 경계선 */
    private fun edges(c: Canvas, k: Int, o: Int, t: Long, pass: Int) {
        val r = k / size
        val col = k % size
        val l = originX + col * cell
        val tp = originY + r * cell
        val rr = l + cell
        val b = tp + cell
        val w = if (o == current) lineW * 1.4f else lineW
        when (pass) {
            PASS_INK -> line.strokeWidth = w * 1.7f
            PASS_COLOR -> { line.strokeWidth = w; line.color = playerColors[o] }
        }
        val up = r == 0 || ownerAt(k - size, t) != o
        val down = r == size - 1 || ownerAt(k + size, t) != o
        val left = col == 0 || ownerAt(k - 1, t) != o
        val right = col == size - 1 || ownerAt(k + 1, t) != o
        if (!up && !down && !left && !right) return
        // 선을 칸 안쪽으로 들여 그린다 → 두 사람 땅이 맞닿아도 두 경계선이 나란히 보임
        val i = w * 0.85f
        val x0 = if (left) l + i else l
        val x1 = if (right) rr - i else rr
        val y0 = if (up) tp + i else tp
        val y1 = if (down) b - i else b
        if (up) side(c, x0, tp + i, x1, tp + i, pass)
        if (down) side(c, x0, b - i, x1, b - i, pass)
        if (left) side(c, l + i, y0, l + i, y1, pass)
        if (right) side(c, rr - i, y0, rr - i, y1, pass)
    }

    private fun side(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, pass: Int) {
        c.drawLine(x0, y0, x1, y1, if (pass == PASS_GLOW) glow else line)
    }

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val KIND_NONE = 0
        private const val KIND_RECOLOR = 1
        private const val KIND_ABSORB = 2
        private const val PASS_GLOW = 0
        private const val PASS_INK = 1
        private const val PASS_COLOR = 2

        /** 두 색 사이 (f = 0 → a, 1 → b) */
        fun mix(a: Int, b: Int, f: Float): Int {
            val g = f.coerceIn(0f, 1f)
            val ar = (a shr 16) and 0xFF
            val ag = (a shr 8) and 0xFF
            val ab = a and 0xFF
            val r = ar + (((b shr 16) and 0xFF) - ar) * g
            val gg = ag + (((b shr 8) and 0xFF) - ag) * g
            val bb = ab + ((b and 0xFF) - ab) * g
            return (0xFF shl 24) or (r.toInt() shl 16) or (gg.toInt() shl 8) or bb.toInt()
        }
    }
}
