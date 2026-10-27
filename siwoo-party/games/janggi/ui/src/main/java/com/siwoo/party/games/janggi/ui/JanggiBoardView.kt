package com.siwoo.party.games.janggi.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.janggi.GameConfig
import com.siwoo.party.games.janggi.JanggiGame
import com.siwoo.party.games.janggi.Kind
import com.siwoo.party.games.janggi.Phase
import com.siwoo.party.games.janggi.Piece
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 장기판 (나무 판 + 9×10 줄 + 궁성 대각선 + 팔각형 기물).
 * - 판은 크기가 바뀔 때 RGB_565 비트맵으로 한 번, 기물 14종(진영 2 × 종류 7)도 ARGB 비트맵으로 한 번 그려 두고 붙이기만 한다
 *   (release / onDetachedFromWindow 에서 recycle).
 * - 고른 기물: 금색 고리, 갈 수 있는 칸: 초록 점, 잡을 수 있는 칸: 빨간 테두리. 마지막 수 표시, 장군 받는 궁은 빨간 빛.
 * - 판 회전: flipped = true 면 판 전체가 180° 돌아 한(漢)이 아래. 돌아가는 동안 기물 글자는 늘 바로 서 있다.
 * - 스스로 다시 그리는 연출은 셋뿐 (진행 중일 때만, 일시정지면 멈춤): 기물 미끄러짐, 잡힌 기물 사라짐, 판 회전.
 *   시계는 listener.now() (= 게임 시계, 일시정지 시간 제외)
 */
class JanggiBoardView(context: Context) : View(context) {

    interface Listener {
        /** 지금 판을 눌러도 되는지 */
        fun canTap(): Boolean
        /** 교차점(칸 번호) 을 누름 */
        fun onPoint(sq: Int)
        /** 게임 시계 (ms) */
        fun now(): Long
        /** 일시정지 중이면 연출을 다시 그리지 않음 */
        fun isPausedNow(): Boolean
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: JanggiGame? = null

    // ── 배치 (onSizeChanged) — 회전하지 않은 판 기준 ──
    /** 줄 간격 (px) */
    var cellPx = 1f
        private set
    private var cx = 0f
    private var cy = 0f
    private var gx0 = 0f
    private var gy0 = 0f
    private val boardRect = RectF()
    private var boardBmp: Bitmap? = null
    /** 기물 비트맵 (기물 값 0~15 로 찾음) */
    private val pieceBmps = arrayOfNulls<Bitmap>(16)
    private val pieceHalf = FloatArray(16)
    /** 기물 반지름 (기물 값으로) */
    private val pieceR = FloatArray(16)

    // ── 연출 시간 (속도 배율 적용된 값을 화면이 넣어 줌) ──
    var moveMs = GameConfig.MOVE_MS
    var captureMs = GameConfig.CAPTURE_MS
    var rotateMs = GameConfig.ROTATE_MS

    // ── 상태 ──
    /** 판이 돌아가 있는지 (한이 아래) */
    var flipped = false
        private set
    private var rotFromFlipped = false
    private var rotAt = -1L
    private var selected = -1
    private val targets = IntArray(JanggiGame.MAX_TARGETS)
    private var targetCount = 0
    private var checkSq = -1
    private var animFrom = -1
    private var animTo = -1
    private var animPiece = 0
    private var animCaptured = 0
    private var animAt = 0L

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        isClickable = true
    }

    /** 게임 연결 (시작 전 미리보기 → 실제 판으로 바꿀 때도) */
    fun bind(game: JanggiGame) {
        this.game = game
        selected = -1
        targetCount = 0
        checkSq = -1
        animTo = -1
        invalidate()
    }

    // ── 화면이 부르는 것 ──

    /** 판 방향 (animate = 180° 돌아가는 연출) */
    fun setFlipped(v: Boolean, animate: Boolean) {
        if (flipped == v) return
        rotFromFlipped = flipped
        flipped = v
        rotAt = if (animate) listener?.now() ?: 0L else -1L
        invalidate()
    }

    /** 기물 고르기: 고른 칸 + 갈 수 있는 칸들 */
    fun select(sq: Int, list: IntArray, n: Int) {
        selected = sq
        targetCount = n
        for (k in 0 until n) targets[k] = list[k]
        invalidate()
    }

    fun clearSelection() {
        if (selected < 0 && targetCount == 0) return
        selected = -1
        targetCount = 0
        invalidate()
    }

    val selectedSquare: Int get() = selected
    val shownTargets: Int get() = targetCount

    /** sq 가 지금 보이는 갈 수 있는 칸인지 */
    fun isTarget(sq: Int): Boolean {
        for (k in 0 until targetCount) if (targets[k] == sq) return true
        return false
    }

    /** 장군 받는 궁 자리 (−1 = 없음) */
    fun setCheck(sq: Int) {
        if (checkSq == sq) return
        checkSq = sq
        invalidate()
    }

    /** 기물 이동 연출 시작 (게임 판은 이미 바뀐 뒤) */
    fun animateMove(from: Int, to: Int, piece: Int, captured: Int) {
        animFrom = from
        animTo = to
        animPiece = piece
        animCaptured = captured
        animAt = listener?.now() ?: 0L
        invalidate()
    }

    /** 칸 번호 → 이 View 좌표 (지금 방향 기준) */
    fun pointCenter(sq: Int, out: PointF) {
        val x = gx0 + (sq % GameConfig.COLS) * cellPx
        val y = gy0 + (sq / GameConfig.COLS) * cellPx
        if (flipped) out.set(2f * cx - x, 2f * cy - y) else out.set(x, y)
    }

    /** 테스트용: 연출이 진행 중인지 */
    val animating: Boolean
        get() {
            val now = listener?.now() ?: return false
            return moveActive(now) || captureActive(now) || rotating(now)
        }

    /** 판이 돌아가는 중인지 */
    val isRotating: Boolean get() = rotating(listener?.now() ?: 0L)

    private fun moveActive(now: Long) = animTo >= 0 && now - animAt < moveMs
    private fun captureActive(now: Long) = animTo >= 0 && animCaptured != Piece.EMPTY && now - animAt < moveMs + captureMs
    private fun rotating(now: Long) = rotAt >= 0 && now - rotAt < rotateMs

    // ── 크기·비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        layoutBoard(w, h)
    }

    private fun layoutBoard(w: Int, h: Int) {
        val gw = GameConfig.COLS - 1 + 2f * EDGE
        val gh = GameConfig.ROWS - 1 + 2f * EDGE
        cellPx = minOf((w - 6f * d) / gw, (h - 6f * d) / gh)
        cx = w / 2f
        cy = h / 2f
        val bw = gw * cellPx
        val bh = gh * cellPx
        boardRect.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
        gx0 = boardRect.left + EDGE * cellPx
        gy0 = boardRect.top + EDGE * cellPx
        buildBitmaps()
    }

    private fun buildBitmaps() {
        release()
        buildBoard()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (side in 0..1) for (kind in Kind.ALL) {
            val code = Piece.code(side, kind)
            val r = cellPx * PIECE_R * JanggiArt.sizeFactor(kind)
            val size = ceil(r * 2.3f).toInt().coerceAtLeast(2)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            JanggiArt.piece(Canvas(bmp), p, size / 2f, size / 2f, r, side, kind)
            pieceBmps[code] = bmp
            pieceHalf[code] = size / 2f
            pieceR[code] = r
        }
    }

    /** 나무 판 + 나뭇결 + 줄 + 궁성 대각선 */
    private fun buildBoard() {
        val bw = ceil(boardRect.width()).toInt().coerceAtLeast(1)
        val bh = ceil(boardRect.height()).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val k = cellPx
        p.shader = LinearGradient(0f, 0f, bw.toFloat(), bh.toFloat(),
            intArrayOf(JanggiArt.WOOD_LIGHT, JanggiArt.WOOD, 0xFFE0B061.toInt(), 0xFFEBC67C.toInt()),
            floatArrayOf(0f, 0.4f, 0.75f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
        p.shader = null
        // 나뭇결 (고정 난수 → 늘 같은 무늬)
        val rnd = Random(41)
        p.style = Paint.Style.STROKE
        val grain = Path()
        var y = rnd.nextFloat() * k
        while (y < bh) {
            p.strokeWidth = (0.5f + rnd.nextFloat() * 1.4f) * d
            p.color = if (rnd.nextInt(3) == 0) 0x1EFFF0D0 else (0x12 + rnd.nextInt(0x14) shl 24) or 0x6B3A10
            grain.reset()
            grain.moveTo(0f, y)
            var x = 0f
            val amp = (0.1f + rnd.nextFloat() * 0.3f) * k
            while (x < bw) {
                val seg = (2f + rnd.nextFloat() * 3f) * k
                grain.quadTo(x + seg / 2f, y + (if (rnd.nextBoolean()) amp else -amp), x + seg, y + (rnd.nextFloat() - 0.5f) * 0.2f * k)
                x += seg
            }
            c.drawPath(grain, p)
            y += (0.25f + rnd.nextFloat() * 0.8f) * k
        }
        // 줄
        val ox = EDGE * k
        val oy = EDGE * k
        val right = ox + (GameConfig.COLS - 1) * k
        val bottom = oy + (GameConfig.ROWS - 1) * k
        p.color = 0xE6402408.toInt()
        p.strokeWidth = maxOf(1f, 1.1f * d)
        for (i in 0 until GameConfig.COLS) c.drawLine(ox + i * k, oy, ox + i * k, bottom, p)
        for (j in 0 until GameConfig.ROWS) c.drawLine(ox, oy + j * k, right, oy + j * k, p)
        // 궁성 대각선 (위: 한, 아래: 초)
        for (top in intArrayOf(0, 7)) {
            val px = ox + 3 * k
            val py = oy + top * k
            c.drawLine(px, py, px + 2 * k, py + 2 * k, p)
            c.drawLine(px + 2 * k, py, px, py + 2 * k, p)
        }
        // 바깥 테두리
        p.strokeWidth = maxOf(2f, 2.4f * d)
        c.drawRect(ox, oy, right, bottom, p)
        // 졸·포 자리 작은 꺾쇠 표시
        p.strokeWidth = maxOf(1f, 1.1f * d)
        val m = k * 0.1f
        val l = k * 0.16f
        for (j in intArrayOf(2, 3, 6, 7)) {
            val xs = if (j == 2 || j == 7) intArrayOf(1, 7) else intArrayOf(0, 2, 4, 6, 8)
            for (i in xs) {
                val x0 = ox + i * k
                val y0 = oy + j * k
                for (sx in -1..1 step 2) {
                    if ((i == 0 && sx < 0) || (i == 8 && sx > 0)) continue
                    for (sy in -1..1 step 2) {
                        c.drawLine(x0 + sx * m, y0 + sy * m, x0 + sx * (m + l), y0 + sy * m, p)
                        c.drawLine(x0 + sx * m, y0 + sy * m, x0 + sx * m, y0 + sy * (m + l), p)
                    }
                }
            }
        }
        // 가장자리 입체감
        p.strokeWidth = 2f * d
        p.color = 0x55FFF4DC
        c.drawRect(d, d, bw - d, bh - d, p)
        boardBmp = bmp
    }

    /** 판·기물 비트맵 해제 */
    fun release() {
        boardBmp?.recycle()
        boardBmp = null
        for (i in pieceBmps.indices) {
            pieceBmps[i]?.recycle()
            pieceBmps[i] = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (boardBmp == null && width > 0) buildBitmaps()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ── 터치 ──

    private var downSq = -1

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (game == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (listener?.canTap() != true) return false
                downSq = cellAt(e.x, e.y)
                return downSq >= 0
            }
            MotionEvent.ACTION_UP -> {
                if (downSq < 0) return false
                val sq = cellAt(e.x, e.y)
                downSq = -1
                if (sq >= 0 && listener?.canTap() == true) listener?.onPoint(sq)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                downSq = -1
                return true
            }
        }
        return downSq >= 0
    }

    /** 화면 좌표 → 가장 가까운 교차점 칸 번호 (반 칸 넘게 벗어나면 −1) */
    fun cellAt(px: Float, py: Float): Int {
        val ux = if (flipped) 2f * cx - px else px
        val uy = if (flipped) 2f * cy - py else py
        val fx = (ux - gx0) / cellPx
        val fy = (uy - gy0) / cellPx
        if (fx < -0.5f || fy < -0.5f || fx > GameConfig.COLS - 0.5f || fy > GameConfig.ROWS - 0.5f) return -1
        val x = fx.roundToInt().coerceIn(0, GameConfig.COLS - 1)
        val y = fy.roundToInt().coerceIn(0, GameConfig.ROWS - 1)
        return y * GameConfig.COLS + x
    }

    // ── 그리기 ──

    private fun sx(sq: Int): Float = gx0 + (sq % GameConfig.COLS) * cellPx
    private fun sy(sq: Int): Float = gy0 + (sq / GameConfig.COLS) * cellPx

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val now = listener?.now() ?: 0L
        // 판 방향: 회전 중이면 시작 방향 + 180° × 진행률, 가운데쯤 조금 작게
        var angle = if (flipped) 180f else 0f
        var scale = 1f
        val turning = rotating(now)
        if (turning) {
            val f = ((now - rotAt).toFloat() / rotateMs).coerceIn(0f, 1f)
            val e = f * f * (3f - 2f * f)
            angle = (if (rotFromFlipped) 180f else 0f) + 180f * e
            // 돌아간 판의 바깥 상자가 View 안에 들어오도록 줄임 (45° 쯤 가장 작음)
            val rad = Math.toRadians((180f * e).toDouble())
            val cs = Math.abs(Math.cos(rad)).toFloat()
            val sn = Math.abs(Math.sin(rad)).toFloat()
            val bw = boardRect.width() + 8f * d
            val bh = boardRect.height() + 8f * d
            scale = minOf(1f, width / (bw * cs + bh * sn), height / (bw * sn + bh * cs))
        }
        c.save()
        if (angle != 0f) c.rotate(angle, cx, cy)
        if (scale != 1f) c.scale(scale, scale, cx, cy)

        // 그림자 (사방 고르게 — 돌아도 어색하지 않게) + 판
        fill.color = 0x70000000
        rect.set(boardRect.left - 4f * d, boardRect.top - 3f * d, boardRect.right + 4f * d, boardRect.bottom + 5f * d)
        c.drawRoundRect(rect, 8f * d, 8f * d, fill)
        val bmp = boardBmp
        if (bmp != null && !bmp.isRecycled) c.drawBitmap(bmp, boardRect.left, boardRect.top, bmpPaint)

        // 차례(진영 색) 테두리
        if (g.phase == Phase.PLAY) {
            stroke.color = JanggiArt.sideColor(g.turn)
            stroke.alpha = 220
            stroke.strokeWidth = 3f * d
            rect.set(boardRect.left - 2f * d, boardRect.top - 2f * d, boardRect.right + 2f * d, boardRect.bottom + 2f * d)
            c.drawRoundRect(rect, 4f * d, 4f * d, stroke)
            stroke.alpha = 255
        }

        val moving = moveActive(now)
        val last = g.lastMove
        // 마지막 수: 떠난 자리 작은 고리, 도착 자리 금빛
        if (last != null && !moving) {
            stroke.color = 0xB3FFC93C.toInt()
            stroke.strokeWidth = 2f * d
            c.drawCircle(sx(last.from), sy(last.from), cellPx * 0.2f, stroke)
            fill.color = 0x66FFC93C
            c.drawCircle(sx(last.to), sy(last.to), cellPx * 0.5f, fill)
        }
        // 장군 받는 궁: 빨간 빛
        if (checkSq >= 0) {
            fill.color = 0x88FF2E3E.toInt()
            c.drawCircle(sx(checkSq), sy(checkSq), cellPx * 0.62f, fill)
            fill.color = 0x55FF2E3E
            c.drawCircle(sx(checkSq), sy(checkSq), cellPx * 0.74f, fill)
        }
        // 고른 기물: 금빛
        if (selected >= 0) {
            fill.color = 0x99FFE08A.toInt()
            c.drawCircle(sx(selected), sy(selected), cellPx * 0.58f, fill)
        }

        // 기물 (움직이는 기물은 맨 위에 따로)
        val board = g.board
        for (i in board.indices) {
            val code = board[i]
            if (code == Piece.EMPTY) continue
            if (animTo == i && moving) continue
            drawPiece(c, code, sx(i), sy(i), angle, 1f, 255)
        }
        // 잡힌 기물: 움직이는 기물이 닿을 때까지 그대로, 닿은 뒤 작아지며 사라짐
        if (animTo >= 0 && animCaptured != Piece.EMPTY && captureActive(now)) {
            val el = now - animAt - moveMs
            val f = if (el <= 0) 0f else (el.toFloat() / captureMs).coerceIn(0f, 1f)
            if (moving) drawPiece(c, animCaptured, sx(animTo), sy(animTo), angle, 1f, 255)
            else drawPiece(c, animCaptured, sx(animTo), sy(animTo) - cellPx * 0.4f * f, angle, 1f - 0.5f * f, (255 * (1f - f)).toInt())
        }
        if (moving) {
            val f = ((now - animAt).toFloat() / moveMs).coerceIn(0f, 1f)
            val e = 1f - (1f - f) * (1f - f)
            val x = sx(animFrom) + (sx(animTo) - sx(animFrom)) * e
            val y = sy(animFrom) + (sy(animTo) - sy(animFrom)) * e
            drawPiece(c, animPiece, x, y, angle, 1f + 0.18f * Math.sin(Math.PI * f).toFloat(), 255)
        }
        // 고른 기물 금색 고리
        if (selected >= 0 && board[selected] != Piece.EMPTY) {
            stroke.color = Theme.GOLD
            stroke.strokeWidth = 3f * d
            c.drawCircle(sx(selected), sy(selected), pieceR[board[selected]] * 1.08f, stroke)
        }
        // 갈 수 있는 칸: 점, 잡을 수 있는 칸: 빨간 테두리
        for (k in 0 until targetCount) {
            val t = targets[k]
            val code = board[t]
            if (code == Piece.EMPTY) JanggiArt.dot(c, art, sx(t), sy(t), cellPx * 0.14f)
            else JanggiArt.captureRing(c, art, sx(t), sy(t), pieceR[code] * 1.1f, 3.2f * d)
        }
        c.restore()
        val animate = moving || captureActive(now) || turning
        if (animate && listener?.isPausedNow() != true) postInvalidateOnAnimation()
    }

    /** 기물 비트맵 붙이기. 판이 angle 만큼 돌아가 있으면 기물만 거꾸로 돌려 글자를 바로 세운다 */
    private fun drawPiece(c: Canvas, code: Int, x: Float, y: Float, angle: Float, scale: Float, alpha: Int) {
        val bmp = pieceBmps[code] ?: return
        if (bmp.isRecycled) return
        val half = pieceHalf[code]
        c.save()
        if (angle != 0f) c.rotate(-angle, x, y)
        if (scale != 1f) c.scale(scale, scale, x, y)
        bmpPaint.alpha = alpha
        c.drawBitmap(bmp, x - half, y - half, bmpPaint)
        bmpPaint.alpha = 255
        c.restore()
    }

    companion object {
        /** 판 가장자리 여백 (칸 단위) */
        private const val EDGE = 0.62f
        /** 궁 반지름 (칸 단위) — 다른 기물은 JanggiArt.sizeFactor 배 */
        private const val PIECE_R = 0.5f
    }
}
