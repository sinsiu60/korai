package com.siwoo.party.games.minefield.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.minefield.MinefieldGame
import kotlin.math.cos
import kotlin.math.sin

/**
 * 12×8 지뢰 판 (Canvas 하나).
 * - 숨은 칸: 볼록 타일, 열린 칸: 납작한 크림색(연 사람 색이 아주 살짝 비침) + 고전 숫자 색
 * - 0 칸 물결 열기: 칸마다 열리는 시각(revealAt = 시작 + BFS 거리 × 간격)을 두고, 그 시각에 타일이 줄어들며 사라진다
 * - 깃발: 빨간 깃발 + 꽂은 사람 색 동그라미(첫 글자). 꽂을 때 톡
 * - 폭발: 섬광 + 충격파 고리 + 파편 + 연기, 칸 바탕은 빨강 (지뢰는 그대로 남음)
 * - 끝나면 숨은 지뢰가 하나씩 드러나고, 잘못 꽂힌 깃발엔 X
 * 시계는 listener.now() (= 게임 시간, 일시정지 때 멈춤). 연출이 남아 있을 때만 다음 프레임을 요청한다.
 * onDraw 에서는 객체를 만들지 않는다. 규칙 판정은 하지 않고 누른 칸 번호만 알린다.
 */
class MineBoardView(context: Context) : View(context) {

    interface Listener {
        /** 지금 칸을 누를 수 있는지 (누름 표시용) */
        fun canTap(): Boolean
        fun onCellTap(k: Int)
        /** 게임 시간 (일시정지 때 멈춤) */
        fun now(): Long
        fun isPausedNow(): Boolean
    }

    var listener: Listener? = null

    private var game: MinefieldGame? = null
    private var cols = 1
    private var rows = 1
    private var n = 0
    private var colors = IntArray(0)
    private var initials = arrayOf<String>()
    /** 연 사람 색이 살짝 비친 열린 칸 색 (플레이어별) */
    private var openTint = IntArray(0)

    // ── 칸별 연출 시각 ──
    private var revealAt = LongArray(0)
    private var flagAt = LongArray(0)
    private var shakeAt = LongArray(0)
    private var mineShowAt = LongArray(0)
    private var popMs = 1L
    private var flagPopMs = 1L
    private var shakeMs = 1L
    private var boomCell = -1
    private var boomAt = 0L
    private var boomMs = 1L
    /** 이 시각까지는 연출이 남아 있음 → 프레임 요청 */
    private var animUntil = 0L

    // ── 배치 ──
    private var cell = 0f
    private var gx = 0f
    private var gy = 0f
    private val frame = RectF()
    private var frameColor = Theme.GOLD
    private var pressCell = -1

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        isClickable = true
        contentDescription = "지뢰 판"
    }

    private fun now(): Long = listener?.now() ?: 0L

    /** 판과 플레이어 색을 연결 (처음 한 번) */
    fun bind(g: MinefieldGame, playerColors: IntArray, playerInitials: Array<String>) {
        game = g
        cols = g.cols
        rows = g.rows
        n = g.cellCount
        colors = playerColors
        initials = playerInitials
        openTint = IntArray(playerColors.size) { MineArt.blend(MineArt.OPEN, playerColors[it], 0.12f) }
        revealAt = LongArray(n)
        flagAt = LongArray(n) { Long.MIN_VALUE / 2 }
        shakeAt = LongArray(n) { Long.MIN_VALUE / 2 }
        mineShowAt = LongArray(n) { Long.MAX_VALUE }
        requestLayout()
        invalidate()
    }

    fun setDurations(cellPopMs: Long, flagMs: Long, blockShakeMs: Long) {
        popMs = cellPopMs.coerceAtLeast(1)
        flagPopMs = flagMs.coerceAtLeast(1)
        shakeMs = blockShakeMs.coerceAtLeast(1)
    }

    fun setFrameColor(color: Int) {
        if (frameColor != color) { frameColor = color; invalidate() }
    }

    private fun extend(until: Long) {
        if (until > animUntil) animUntil = until
        invalidate()
    }

    /**
     * 칸들을 물결처럼 연다 (dist 는 같은 순서의 BFS 거리).
     * 마지막 칸이 다 열릴 때까지 걸리는 시간(ms)을 돌려준다.
     */
    fun reveal(cells: IntArray, dist: IntArray, stepMs: Long): Long {
        val t0 = now()
        var maxD = 0
        for (i in cells.indices) {
            revealAt[cells[i]] = t0 + dist[i] * stepMs
            if (dist[i] > maxD) maxD = dist[i]
        }
        val total = maxD * stepMs + popMs
        extend(t0 + total)
        return total
    }

    /** 깃발을 꽂거나 뽑을 때 톡 */
    fun flagPop(k: Int) {
        val t = now()
        flagAt[k] = t
        extend(t + flagPopMs)
    }

    /** 깃발 칸을 열려고 할 때 좌우로 흔들림 */
    fun shake(k: Int) {
        val t = now()
        shakeAt[k] = t
        extend(t + shakeMs)
    }

    /** 폭발 (durMs 동안 섬광·충격파·파편) */
    fun explode(k: Int, durMs: Long) {
        boomCell = k
        boomAt = now()
        boomMs = durMs.coerceAtLeast(1)
        extend(boomAt + boomMs)
    }

    /** 끝: 숨은 지뢰를 하나씩 드러냄. 다 드러날 때까지 걸리는 시간 */
    fun showMines(cells: List<Int>, stepMs: Long): Long {
        val t0 = now()
        for (i in cells.indices) mineShowAt[cells[i]] = t0 + i * stepMs
        val total = cells.size * stepMs + popMs
        extend(t0 + total)
        return total
    }

    /** 칸이 화면에서 열린 모습인지 (물결이 지나갔는지) */
    fun isShownOpen(k: Int): Boolean {
        val g = game ?: return false
        return g.isOpen(k) && now() >= revealAt[k]
    }

    /** 연출이 남아 있는지 */
    val isAnimating: Boolean get() = now() < animUntil

    /** k 번 칸 가운데 (이 뷰 좌표) */
    fun cellCenter(k: Int, out: PointF) {
        out.set(gx + (k % cols + 0.5f) * cell, gy + (k / cols + 0.5f) * cell)
    }

    fun cellSize(): Float = cell

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutBoard(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (cell <= 0f && width > 0) layoutBoard(width, height)
    }

    private fun layoutBoard(w: Int, h: Int) {
        if (n == 0 || w <= 0 || h <= 0) return
        val pad = Theme.dpf(context, 8f)
        cell = minOf((w - pad * 2) / cols, (h - pad * 2) / rows)
        val bw = cell * cols
        val bh = cell * rows
        gx = (w - bw) / 2f
        gy = (h - bh) / 2f
        frame.set(gx - pad * 0.75f, gy - pad * 0.75f, gx + bw + pad * 0.75f, gy + bh + pad * 0.75f)
        numPaint.textSize = cell * 0.62f
        initPaint.textSize = cell * 0.2f
        ring.strokeWidth = Theme.dpf(context, 3f)
    }

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        if (cell <= 0f) return
        val t = now()
        val d = Theme.dpf(context, 1f)
        // 판 바탕 + 차례인 사람 색 테두리
        val fr = d * 12f
        fill.style = Paint.Style.FILL
        fill.color = 0x59000000
        c.drawRoundRect(frame, fr, fr, fill)
        ring.color = frameColor
        ring.strokeWidth = d * 3f
        c.drawRoundRect(frame, fr, fr, ring)

        val canTap = listener?.canTap() == true
        for (k in 0 until n) {
            var x = gx + (k % cols) * cell
            val y = gy + (k / cols) * cell
            val se = t - shakeAt[k]
            if (se in 0 until shakeMs) x += sin(se * 0.09f) * cell * 0.12f * (1f - se.toFloat() / shakeMs)
            if (g.isExploded(k)) {
                drawExplodedCell(c, k, x, y, t)
                continue
            }
            if (g.isOpen(k) && t >= revealAt[k]) {
                val who = g.openedBy(k)
                MineArt.drawOpen(c, x, y, cell, art, if (who >= 0) openTint[who] else MineArt.OPEN)
                MineArt.drawNumber(c, g.adjacent(k), x + cell / 2f, y + cell / 2f, numPaint)
                val f = (t - revealAt[k]).toFloat() / popMs
                if (f < 1f) {
                    // 타일이 줄어들며 사라지고 하얀 빛이 번짐
                    val s = cell * (1f - f)
                    if (s > 1f) {
                        c.save()
                        c.translate(x + (cell - s) / 2f, y + (cell - s) / 2f)
                        MineArt.drawTile(c, 0f, 0f, s, art)
                        c.restore()
                    }
                    fill.color = 0xFFFFFFFF.toInt()
                    fill.alpha = (150 * (1f - f)).toInt()
                    c.drawRect(x, y, x + cell, y + cell, fill)
                    fill.alpha = 255
                }
                continue
            }
            // 숨은 칸 (끝나고 드러난 지뢰 포함)
            val flagged = g.isFlagged(k)
            if (g.isMine(k) && !flagged && t >= mineShowAt[k]) {
                MineArt.drawOpen(c, x, y, cell, art, MineArt.MINE_BG)
                val f = ((t - mineShowAt[k]).toFloat() / popMs).coerceIn(0f, 1f)
                MineArt.drawMine(c, x + cell / 2f, y + cell / 2f, cell * 0.24f * (0.6f + 0.4f * f), art)
                continue
            }
            MineArt.drawTile(c, x, y, cell, art, if (k == pressCell && canTap) MineArt.TILE_PRESSED else MineArt.TILE)
            val fe = t - flagAt[k]
            val pop = if (fe in 0 until flagPopMs) sin(fe.toFloat() / flagPopMs * Math.PI.toFloat()) else 0f
            if (flagged) {
                val s = cell * 0.9f * (1f + 0.3f * pop)
                val owner = g.flagOwner(k)
                initPaint.color = Theme.textOn(colors[owner])
                initPaint.textSize = s * 0.2f
                MineArt.drawFlag(c, x + cell / 2f, y + cell / 2f - cell * 0.03f, s, art, colors[owner], initials[owner], initPaint)
                if (g.gameOver && !g.isMine(k)) MineArt.drawCross(c, x + cell / 2f, y + cell / 2f, cell, art)
            } else if (pop > 0f) {
                // 뽑힌 깃발: 하얀 고리가 퍼짐
                ring.color = 0xFFFFFFFF.toInt()
                ring.alpha = (200 * pop).toInt()
                ring.strokeWidth = d * 2f
                c.drawCircle(x + cell / 2f, y + cell / 2f, cell * (0.2f + 0.25f * pop), ring)
                ring.alpha = 255
            }
        }

        if (boomCell >= 0) drawBlast(c, t)

        if (t < animUntil && listener?.isPausedNow() != true) postInvalidateOnAnimation()
    }

    /** 터진 칸: 빨간 바탕 + 지뢰 (터지는 순간엔 섬광 속에서 커짐) */
    private fun drawExplodedCell(c: Canvas, k: Int, x: Float, y: Float, t: Long) {
        MineArt.drawOpen(c, x, y, cell, art, MineArt.BOOM_BG)
        var s = 1f
        if (k == boomCell) {
            val f = (t - boomAt).toFloat() / boomMs
            if (f in 0f..1f) s = 1f + 0.5f * sin(minOf(f * 4f, 1f) * Math.PI.toFloat())
        }
        MineArt.drawMine(c, x + cell / 2f, y + cell / 2f, cell * 0.24f * s, art)
    }

    /** 폭발 연출: 섬광 + 충격파 고리 2개 + 파편 + 연기 (모두 시각으로 계산, 할당 없음) */
    private fun drawBlast(c: Canvas, t: Long) {
        val el = t - boomAt
        if (el < 0 || el >= boomMs) return
        val f = el.toFloat() / boomMs
        val cx = gx + (boomCell % cols + 0.5f) * cell
        val cy = gy + (boomCell / cols + 0.5f) * cell
        // 섬광
        if (f < 0.25f) {
            val q = f / 0.25f
            fill.color = 0xFFFFF3B0.toInt()
            fill.alpha = (230 * (1f - q)).toInt()
            c.drawCircle(cx, cy, cell * (0.6f + 2.4f * q), fill)
            fill.color = 0xFFFF8A1C.toInt()
            fill.alpha = (200 * (1f - q)).toInt()
            c.drawCircle(cx, cy, cell * (0.4f + 1.4f * q), fill)
            fill.alpha = 255
        }
        // 충격파 고리
        for (r in 0 until 2) {
            val q = (f * 1.8f - r * 0.18f)
            if (q <= 0f || q >= 1f) continue
            ring.color = if (r == 0) 0xFFFF7A1A.toInt() else 0xFFFFD34D.toInt()
            ring.alpha = (255 * (1f - q)).toInt()
            ring.strokeWidth = cell * 0.18f * (1f - q)
            c.drawCircle(cx, cy, cell * (0.5f + 3.2f * q), ring)
        }
        ring.alpha = 255
        // 파편: 사방으로 튀며 떨어짐
        val e = 1f - (1f - f) * (1f - f)
        for (i in 0 until DEBRIS) {
            val a = DEBRIS_ANGLE[i]
            val dist = cell * DEBRIS_DIST[i] * e
            val px = cx + cos(a) * dist
            val py = cy + sin(a) * dist + cell * 1.6f * f * f
            val sz = cell * 0.11f * (1f - f * 0.7f)
            fill.color = DEBRIS_COLORS[i % DEBRIS_COLORS.size]
            fill.alpha = (255 * (1f - f)).toInt()
            c.save()
            c.rotate(f * 720f * (if (i % 2 == 0) 1f else -1f), px, py)
            c.drawRect(px - sz, py - sz * 0.6f, px + sz, py + sz * 0.6f, fill)
            c.restore()
        }
        // 연기: 천천히 퍼지며 옅어짐
        if (f > 0.15f) {
            val q = (f - 0.15f) / 0.85f
            for (i in 0 until SMOKE) {
                val a = SMOKE_ANGLE[i]
                val dist = cell * (0.4f + 0.9f * q)
                fill.color = 0xFF55505A.toInt()
                fill.alpha = (150 * (1f - q)).toInt()
                c.drawCircle(cx + cos(a) * dist, cy + sin(a) * dist - cell * 0.6f * q, cell * (0.3f + 0.35f * q), fill)
            }
        }
        fill.alpha = 255
    }

    // ── 터치: 누른 칸 표시 → 같은 칸에서 떼면 알림 ──

    /** 화면 좌표 → 칸 번호 (판 밖이면 -1) */
    fun hit(x: Float, y: Float): Int {
        if (cell <= 0f) return -1
        val col = ((x - gx) / cell).toInt()
        val row = ((y - gy) / cell).toInt()
        if (x < gx || y < gy || col !in 0 until cols || row !in 0 until rows) return -1
        return row * cols + col
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressCell = hit(e.x, e.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val k = hit(e.x, e.y)
                if (k != pressCell) { pressCell = -1; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                val k = hit(e.x, e.y)
                val p = pressCell
                pressCell = -1
                invalidate()
                performClick()
                if (k >= 0 && k == p) listener?.onCellTap(k)
            }
            MotionEvent.ACTION_CANCEL -> cancelPress()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    fun cancelPress() {
        if (pressCell >= 0) { pressCell = -1; invalidate() }
    }

    companion object {
        private const val DEBRIS = 14
        private const val SMOKE = 6
        private val DEBRIS_ANGLE = FloatArray(DEBRIS) { (it * 2.0 * Math.PI / DEBRIS + (it % 3) * 0.21).toFloat() }
        private val DEBRIS_DIST = FloatArray(DEBRIS) { 1.8f + (it * 7 % 5) * 0.45f }
        private val DEBRIS_COLORS = intArrayOf(0xFFFF7A1A.toInt(), 0xFFFFD34D.toInt(), 0xFF3A3A44.toInt(), 0xFFFF4D5E.toInt())
        private val SMOKE_ANGLE = FloatArray(SMOKE) { (it * 2.0 * Math.PI / SMOKE + 0.4).toFloat() }
    }
}
