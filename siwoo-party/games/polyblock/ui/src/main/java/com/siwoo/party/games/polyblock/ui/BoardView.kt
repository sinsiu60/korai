package com.siwoo.party.games.polyblock.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.polyblock.Pieces
import com.siwoo.party.games.polyblock.PolyGame

/**
 * 조각 배치 판 (정사각형, View 높이에 맞춤).
 * - 칸 격자 + 네 모서리에 주인 색 꺾쇠(아직 첫 조각 전이면 별 표시)
 * - 놓인 조각: 주인 색 입체 블록, 방금 놓은 조각은 "톡" 커졌다 돌아옴 (그동안만 다시 그림)
 * - 지금 차례 사람의 꼭짓점 자리(새 조각을 둘 수 있는 칸): 작은 점
 * - 미리보기: 반투명 조각 + 칸마다 초록(놓을 수 있음)/빨강(안 됨) 테두리
 * 판을 누르거나 끌면 listener.onCell(x, y) 로 손가락 아래 칸을 알린다.
 */
class BoardView(context: Context) : View(context) {

    interface Listener {
        fun canTouch(): Boolean
        /** 손가락 아래 칸 (누를 때·끌 때 계속) */
        fun onCell(x: Int, y: Int)
        fun now(): Long
        fun isPausedNow(): Boolean
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: PolyGame? = null
    private var n = 20
    private var colors = IntArray(4)

    // ── 배치 ──
    /** 칸 크기 (px) */
    var cellPx = 1f
        private set
    /** 칸 (0,0) 왼쪽 위 */
    private var ox = 0f
    private var oy = 0f
    private var side = 0f
    private var gridLines = FloatArray(0)

    // ── 상태 ──
    private var anchorColor = 0
    /** 판 테두리 색 (지금 차례 / 이긴 사람) */
    private var frameColor = 0xFF3A4A8C.toInt()
    private var anchors = BooleanArray(0)
    private var showAnchors = false
    private var pvPiece = -1
    private var pvOrient = 0
    private var pvX = 0
    private var pvY = 0
    private var pvOk = false
    private var pvColor = 0

    // ── 연출 ──
    var placeMs = 280L
    private var placedAt = -1L

    // ── 그리기 도구 ──
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val block = Paint()
    private val line = Paint().apply { style = Paint.Style.STROKE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()

    init {
        isClickable = true
    }

    fun bind(game: PolyGame, playerColors: IntArray) {
        this.game = game
        n = game.size
        colors = playerColors.copyOf()
        anchors = BooleanArray(game.cells)
        if (width > 0) layoutBoard(width, height)
        invalidate()
    }

    /** 지금 차례 사람의 꼭짓점 자리 표시 (show=false 면 숨김) */
    fun setAnchors(player: Int, show: Boolean) {
        val g = game ?: return
        showAnchors = show && player >= 0
        if (player >= 0) frameColor = colors[player]
        if (showAnchors) {
            g.anchors(player, anchors)
            anchorColor = colors[player]
        }
        invalidate()
    }

    /** 미리보기 (piece < 0 이면 지움) */
    fun setPreview(piece: Int, orient: Int, x: Int, y: Int, ok: Boolean, color: Int) {
        pvPiece = piece
        pvOrient = orient
        pvX = x
        pvY = y
        pvOk = ok
        pvColor = color
        invalidate()
    }

    fun setFrameColor(color: Int) {
        frameColor = color
        invalidate()
    }

    fun clearPreview() {
        if (pvPiece < 0) return
        pvPiece = -1
        invalidate()
    }

    /** 미리보기가 있는지·놓을 수 있는지 (테스트용) */
    val previewShown: Boolean get() = pvPiece >= 0
    val previewOk: Boolean get() = pvPiece >= 0 && pvOk

    /** 방금 놓은 조각 연출 시작 (game.lastCells) */
    fun onPlaced() {
        placedAt = listener?.now() ?: 0L
        invalidate()
    }

    val animating: Boolean
        get() {
            val now = listener?.now() ?: return false
            return placedAt >= 0 && now - placedAt < placeMs
        }

    /** 칸 (x, y) 가운데 → 이 View 좌표 */
    fun cellCenter(x: Int, y: Int, out: PointF) {
        out.set(ox + (x + 0.5f) * cellPx, oy + (y + 0.5f) * cellPx)
    }

    /** 칸 (x, y) 왼쪽 위 → 이 View 좌표 (x·y 는 소수 가능) */
    fun cellPoint(x: Float, y: Float, out: PointF) {
        out.set(ox + x * cellPx, oy + y * cellPx)
    }

    // ── 크기 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) layoutBoard(w, h)
    }

    private fun layoutBoard(w: Int, h: Int) {
        val frame = 5f * d
        val avail = minOf(w, h) - frame * 2f
        cellPx = Math.floor((avail / n).toDouble()).toFloat().coerceAtLeast(2f)
        side = cellPx * n
        ox = Math.round((w - side) / 2f).toFloat()
        oy = Math.round((h - side) / 2f).toFloat()
        // 격자선 (n−1 개씩 가로·세로)
        val lines = FloatArray((n - 1) * 8)
        var k = 0
        for (i in 1 until n) {
            val v = i * cellPx
            lines[k++] = ox + v; lines[k++] = oy; lines[k++] = ox + v; lines[k++] = oy + side
            lines[k++] = ox; lines[k++] = oy + v; lines[k++] = ox + side; lines[k++] = oy + v
        }
        gridLines = lines
        line.strokeWidth = maxOf(1f, 0.7f * d)
        stroke.strokeWidth = maxOf(1.5f, cellPx * 0.12f)
    }

    // ── 터치 ──

    private var tracking = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val l = listener ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!l.canTouch() || !onBoard(e.x, e.y, cellPx)) return false
                tracking = true
                report(e.x, e.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking && l.canTouch()) report(e.x, e.y)
                return tracking
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!tracking) return false
                tracking = false
                if (e.actionMasked == MotionEvent.ACTION_UP && l.canTouch()) report(e.x, e.y)
                return true
            }
        }
        return tracking
    }

    private fun onBoard(px: Float, py: Float, margin: Float): Boolean =
        px >= ox - margin && py >= oy - margin && px <= ox + side + margin && py <= oy + side + margin

    private fun report(px: Float, py: Float) {
        val x = ((px - ox) / cellPx).toInt().coerceIn(0, n - 1)
        val y = ((py - oy) / cellPx).toInt().coerceIn(0, n - 1)
        listener?.onCell(x, y)
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val now = listener?.now() ?: 0L
        // 판 테두리·그림자·바탕
        fill.color = 0x66000000
        rect.set(ox - 3f * d, oy - 1f * d, ox + side + 5f * d, oy + side + 7f * d)
        c.drawRoundRect(rect, 6f * d, 6f * d, fill)
        fill.color = frameColor
        rect.set(ox - 4f * d, oy - 4f * d, ox + side + 4f * d, oy + side + 4f * d)
        c.drawRoundRect(rect, 6f * d, 6f * d, fill)
        fill.color = BOARD
        c.drawRect(ox, oy, ox + side, oy + side, fill)
        line.color = GRID
        c.drawLines(gridLines, line)

        // 모서리 꺾쇠 (주인 색) + 첫 조각 전이면 별
        for (p in 0 until g.playerCount) drawCorner(c, g, p)

        // 놓인 조각
        val popping = placedAt >= 0 && now - placedAt < placeMs
        val board = g.board
        for (i in 0 until g.cells) {
            val who = board[i]
            if (who < 0) continue
            if (popping && who == g.lastPlayer && isLast(g, i)) continue
            BlockArt.block(c, block, ox + (i % n) * cellPx, oy + (i / n) * cellPx, cellPx, colors[who])
        }
        if (g.lastCount > 0 && g.lastPlayer >= 0) {
            val f = if (popping) 1f - (now - placedAt).toFloat() / placeMs else 0f
            val grow = cellPx * 0.22f * f * f
            for (k in 0 until g.lastCount) {
                val i = g.lastCells[k]
                val l = ox + (i % n) * cellPx
                val t = oy + (i / n) * cellPx
                if (popping) BlockArt.block(c, block, l - grow, t - grow, cellPx + grow * 2f, colors[g.lastPlayer])
                // 방금 놓은 조각: 흰 점 (다음 사람도 알아보게)
                if (k == 0 && !popping) {
                    fill.color = 0xCCFFFFFF.toInt()
                    c.drawCircle(l + cellPx / 2f, t + cellPx / 2f, cellPx * 0.12f, fill)
                }
            }
        }

        // 꼭짓점 자리 점
        if (showAnchors) {
            val r = maxOf(2f * d, cellPx * 0.17f)
            for (i in 0 until g.cells) {
                if (!anchors[i]) continue
                val cx = ox + (i % n + 0.5f) * cellPx
                val cy = oy + (i / n + 0.5f) * cellPx
                fill.color = 0xCCFFFFFF.toInt()
                c.drawCircle(cx, cy, r * 1.35f, fill)
                fill.color = anchorColor
                c.drawCircle(cx, cy, r, fill)
            }
        }

        // 미리보기
        if (pvPiece >= 0) {
            val o = Pieces.shape(pvPiece).orients[pvOrient]
            val l = ox + pvX * cellPx
            val t = oy + pvY * cellPx
            // 놓을 수 있으면 내 색, 안 되면 회색 블록 (빨간 플레이어도 구별되게)
            BlockArt.piece(c, block, o, l, t, cellPx, if (pvOk) pvColor else BAD_BLOCK, PREVIEW_ALPHA)
            fill.color = if (pvOk) OK_TINT else BAD_TINT
            stroke.color = if (pvOk) Theme.GREEN else Theme.RED
            val inset = stroke.strokeWidth / 2f
            for (k in 0 until o.size) {
                val cl = l + o.xs[k] * cellPx
                val ct = t + o.ys[k] * cellPx
                c.drawRect(cl, ct, cl + cellPx, ct + cellPx, fill)
                rect.set(cl + inset, ct + inset, cl + cellPx - inset, ct + cellPx - inset)
                c.drawRoundRect(rect, cellPx * 0.12f, cellPx * 0.12f, stroke)
            }
            // 안 되면 가운데에 X
            if (!pvOk) {
                val cx = l + o.w * cellPx / 2f
                val cy = t + o.h * cellPx / 2f
                val a = cellPx * 0.4f
                stroke.color = 0xFFFFFFFF.toInt()
                c.drawLine(cx - a, cy - a, cx + a, cy + a, stroke)
                c.drawLine(cx - a, cy + a, cx + a, cy - a, stroke)
            }
        }

        if (popping && listener?.isPausedNow() != true) postInvalidateOnAnimation()
    }

    private fun isLast(g: PolyGame, i: Int): Boolean {
        for (k in 0 until g.lastCount) if (g.lastCells[k] == i) return true
        return false
    }

    /** 모서리 칸에 주인 색 꺾쇠 (판 바깥쪽 두 변) */
    private fun drawCorner(c: Canvas, g: PolyGame, p: Int) {
        val ci = g.corners[p]
        val x = ci % n
        val y = ci / n
        val l = ox + x * cellPx
        val t = oy + y * cellPx
        val right = x > 0
        val bottom = y > 0
        val len = cellPx * 2.2f
        val ex = if (right) l + cellPx else l
        val ey = if (bottom) t + cellPx else t
        val sx = if (right) -1f else 1f
        val sy = if (bottom) -1f else 1f
        stroke.color = colors[p]
        val w = stroke.strokeWidth
        stroke.strokeWidth = maxOf(3f * d, cellPx * 0.22f)
        c.drawLine(ex, ey, ex + sx * len, ey, stroke)
        c.drawLine(ex, ey, ex, ey + sy * len, stroke)
        stroke.strokeWidth = w
        if (g.isFirstMove(p) && g.board[ci] < 0) {
            // 시작 칸: 옅은 주인 색 + 별 모양 점
            fill.color = BlockArt.alpha(colors[p], 90)
            c.drawRect(l, t, l + cellPx, t + cellPx, fill)
            fill.color = colors[p]
            c.drawCircle(l + cellPx / 2f, t + cellPx / 2f, cellPx * 0.2f, fill)
        }
    }

    companion object {
        private const val BOARD = 0xFF141E4E.toInt()
        private const val GRID = 0xFF2C3A78.toInt()
        private const val PREVIEW_ALPHA = 170
        private const val BAD_BLOCK = 0xFF8A90A8.toInt()
        private const val OK_TINT = 0x3D2ECC71
        private const val BAD_TINT = 0x4DFF4D5E
    }
}
