package com.siwoo.party.games.reversi.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.reversi.GameConfig
import com.siwoo.party.games.reversi.Phase
import com.siwoo.party.games.reversi.ReversiGame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 뒤집기 돌 판 (초록 펠트 8 × 8 + 은은한 격자 + 흑·백 돌).
 * - 판(나무 테두리·펠트 결·격자·네 점)은 크기가 바뀔 때 RGB_565 비트맵으로 한 번 그려 두고(release 에서 recycle) 붙이기만 한다
 * - 돌은 셰이더 없이 도형으로 (RvArt.disc). 둘 수 있는 자리는 지금 차례 돌 색의 작은 점
 * - 연출 (진행 중일 때만 스스로 다시 그림, 일시정지면 멈춤. 시계 = listener.now()):
 *   놓은 돌 "탁" → 끼인 돌이 놓은 돌에서 가까운 것부터 차례로 가로로 납작해졌다 반대 색으로 펴짐, 못 두는 자리 빨간 깜빡임
 */
class ReversiBoardView(context: Context) : View(context) {

    interface Listener {
        /** 지금 판을 눌러도 되는지 */
        fun canTap(): Boolean
        /** 칸 (x, y) 를 누름 */
        fun onCell(x: Int, y: Int)
        /** 게임 시계 (ms) */
        fun now(): Long
        /** 일시정지 중이면 연출을 다시 그리지 않음 */
        fun isPausedNow(): Boolean
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: ReversiGame? = null
    private var n = GameConfig.BOARD_SIZE
    private val playerColors = IntArray(GameConfig.PLAYERS)

    // ── 배치 (onSizeChanged) ──
    /** 칸 크기 (px) */
    var cellPx = 1f
        private set
    /** 칸 (0,0) 왼쪽 위 */
    private var gx = 0f
    private var gy = 0f
    /** 테두리 포함 판 왼쪽 위·한 변 */
    private var bx = 0f
    private var by = 0f
    private var bs = 0f
    private var discR = 0f
    private var boardBmp: Bitmap? = null

    // ── 연출 시간 (속도 배율 적용된 값을 화면이 넣어 줌) ──
    var placeMs = GameConfig.PLACE_MS
    var flipDelayMs = GameConfig.FLIP_DELAY_MS
    var flipStepMs = GameConfig.FLIP_STEP_MS
    var flipMs = GameConfig.FLIP_MS
    var denyMs = GameConfig.DENY_MS

    /** 방금 둔 수 (−1 = 연출 없음) */
    private var moveCell = -1
    private var moveAt = 0L
    private var mover = 0
    private var maxDist = 0
    /** 칸마다 뒤집히는 순서 거리 (0 = 이번 수에 안 뒤집힘) */
    private val flipDistAt = IntArray(GameConfig.BOARD_SIZE * GameConfig.BOARD_SIZE)
    private var denyCell = -1
    private var denyAt = 0L

    /** 둘 수 있는 자리 점을 보일지 (화면이 차례 사이에 끔) */
    var showHints = true
        set(v) { field = v; invalidate() }

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Theme.RED }
    private val rect = RectF()

    init {
        isClickable = true
    }

    /** 게임과 플레이어 색 연결 */
    fun bind(game: ReversiGame, colors: IntArray) {
        this.game = game
        n = game.size
        for (i in playerColors.indices) playerColors[i] = colors[i]
        if (width > 0) layoutBoard(width, height)
        invalidate()
    }

    /** 칸 (x, y) 중심 → 이 View 좌표 */
    fun cellCenter(x: Int, y: Int, out: PointF) {
        out.set(gx + (x + 0.5f) * cellPx, gy + (y + 0.5f) * cellPx)
    }

    /**
     * 방금 둔 수 연출 시작 (게임의 flipped/flipDist 를 읽음 — 화면은 place 직후에 부른다).
     * 반환: 연출 전체 길이 (ms)
     */
    fun startMove(g: ReversiGame): Long {
        flipDistAt.fill(0)
        maxDist = 0
        for (i in 0 until g.flipCount) {
            val k = g.flipDist[i]
            flipDistAt[g.flipped[i]] = k
            if (k > maxDist) maxDist = k
        }
        moveCell = g.lastMove
        mover = g.lastMover
        moveAt = listener?.now() ?: 0L
        denyCell = -1
        invalidate()
        return moveAnimMs()
    }

    /** 거리 k(1부터) 돌이 뒤집히기 시작하는 시각 (수를 둔 때부터 ms) */
    fun flipStartMs(k: Int): Long = flipDelayMs + (k - 1) * flipStepMs

    /** 이번 수 연출 전체 길이 (ms) */
    fun moveAnimMs(): Long = if (maxDist == 0) placeMs else maxOf(placeMs, flipStartMs(maxDist) + flipMs)

    /** 못 두는 자리 빨간 깜빡임 */
    fun deny(x: Int, y: Int) {
        denyCell = y * n + x
        denyAt = listener?.now() ?: 0L
        invalidate()
    }

    /** 테스트용: 연출이 진행 중인지 */
    val animating: Boolean
        get() {
            val now = listener?.now() ?: return false
            return moveActive(now) || denyActive(now)
        }

    private fun moveActive(now: Long) = moveCell >= 0 && now - moveAt < moveAnimMs()
    private fun denyActive(now: Long) = denyCell >= 0 && now - denyAt < denyMs

    // ── 크기·비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        layoutBoard(w, h)
    }

    private fun layoutBoard(w: Int, h: Int) {
        // 판 = 칸 n 개 + 양쪽 테두리 EDGE 칸, 아래 그림자 자리 조금
        bs = minOf(w.toFloat(), h - 6f * d)
        cellPx = bs / (n + 2f * EDGE)
        bx = (w - bs) / 2f
        by = (h - bs) / 2f - 2f * d
        gx = bx + EDGE * cellPx
        gy = by + EDGE * cellPx
        discR = cellPx * 0.41f
        frame.strokeWidth = 3f * d
        cross.strokeWidth = 3f * d
        buildBoard()
    }

    /** 나무 테두리 + 펠트(빛 번짐·결 점) + 격자 + 네 점을 비트맵 하나에 */
    private fun buildBoard() {
        release()
        val size = ceil(bs).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val k = cellPx
        val e = EDGE * k
        val s = n * k
        c.drawColor(Theme.BG_BOTTOM)
        // 나무 테두리 (위 밝게 → 아래 어둡게)
        p.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), RvArt.RIM_LIGHT, RvArt.RIM, Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), e * 0.7f, e * 0.7f, p)
        // 펠트: 가운데가 조금 밝은 원형 번짐
        p.shader = RadialGradient(e + s * 0.45f, e + s * 0.4f, s * 0.75f,
            intArrayOf(RvArt.FELT_LIGHT, RvArt.FELT, RvArt.FELT_DARK), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(e, e, e + s, e + s, p)
        p.shader = null
        // 펠트 결 (고정 난수 → 늘 같은 무늬, 아주 옅은 점)
        val rnd = Random(17)
        val dots = (s * s / (3.5f * d * 3.5f * d)).toInt().coerceAtMost(9000)
        for (i in 0 until dots) {
            p.color = if (rnd.nextBoolean()) 0x0FFFFFFF else 0x14000000
            c.drawCircle(e + rnd.nextFloat() * s, e + rnd.nextFloat() * s, (0.5f + rnd.nextFloat() * 0.8f) * d, p)
        }
        // 격자: 어두운 선 + 바로 아래 옅은 밝은 선 (살짝 파인 느낌)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, 1.1f * d)
        p.color = 0x10FFFFFF
        for (i in 1 until n) {
            c.drawLine(e + i * k + d, e, e + i * k + d, e + s, p)
            c.drawLine(e, e + i * k + d, e + s, e + i * k + d, p)
        }
        p.color = RvArt.GRID
        for (i in 0..n) {
            c.drawLine(e + i * k, e, e + i * k, e + s, p)
            c.drawLine(e, e + i * k, e + s, e + i * k, p)
        }
        // 네 점 (2·6 번째 줄 교차점)
        p.style = Paint.Style.FILL
        p.color = 0x80002A12.toInt()
        val q = n / 4
        for (a in intArrayOf(q, n - q)) for (b in intArrayOf(q, n - q)) c.drawCircle(e + a * k, e + b * k, maxOf(2f, 0.07f * k), p)
        // 테두리 안쪽 금색 선 + 바깥 빛
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * d
        p.color = Theme.GOLD_DARK
        c.drawRect(e - 2f * d, e - 2f * d, e + s + 2f * d, e + s + 2f * d, p)
        p.color = 0x40FFFFFF
        c.drawLine(e * 0.4f, d, size - e * 0.4f, d, p)
        boardBmp = bmp
    }

    /** 판 비트맵 해제 */
    fun release() {
        boardBmp?.recycle()
        boardBmp = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (boardBmp == null && width > 0) buildBoard()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ── 터치 ──

    private var downCell = -1

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = game ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (listener?.canTap() != true) return false
                downCell = cellAt(e.x, e.y)
                return downCell >= 0
            }
            MotionEvent.ACTION_UP -> {
                if (downCell < 0) return false
                // 손을 뗀 칸 기준
                val c = cellAt(e.x, e.y)
                downCell = -1
                if (c >= 0 && listener?.canTap() == true) listener?.onCell(c % g.size, c / g.size)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                downCell = -1
                return true
            }
        }
        return downCell >= 0
    }

    /** 화면 좌표 → 칸 번호 (판 밖이면 −1) */
    fun cellAt(px: Float, py: Float): Int {
        val fx = (px - gx) / cellPx
        val fy = (py - gy) / cellPx
        if (fx < 0f || fy < 0f || fx >= n || fy >= n) return -1
        return fy.toInt() * n + fx.toInt()
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val now = listener?.now() ?: 0L
        // 판 그림자 + 판
        fill.color = 0x66000000
        rect.set(bx + 3f * d, by + 6f * d, bx + bs + 3f * d, by + bs + 6f * d)
        c.drawRoundRect(rect, 8f * d, 8f * d, fill)
        val bmp = boardBmp
        if (bmp != null && !bmp.isRecycled) c.drawBitmap(bmp, bx, by, bmpPaint)

        // 차례(끝나면 이긴 사람) 색 테두리
        val who = if (g.phase == Phase.PLAY) g.current else g.winner
        if (who >= 0) {
            frame.color = playerColors[who]
            frame.alpha = 230
            rect.set(bx - 2.5f * d, by - 2.5f * d, bx + bs + 2.5f * d, by + bs + 2.5f * d)
            c.drawRoundRect(rect, 8f * d, 8f * d, frame)
        }

        val moving = moveActive(now)
        val el = now - moveAt

        // 둘 수 있는 자리 (연출 중이 아니고 누를 수 있을 때)
        if (showHints && !moving && g.phase == Phase.PLAY && listener?.canTap() == true) {
            val black = g.current == ReversiGame.BLACK
            for (i in 0 until g.cells) {
                if (!g.legal[i]) continue
                RvArt.hintDot(c, fill, gx + (i % n + 0.5f) * cellPx, gy + (i / n + 0.5f) * cellPx, cellPx * 0.11f, black)
            }
        }

        // 못 두는 자리: 빨간 칸 + X
        if (denyActive(now)) {
            val f = (now - denyAt).toFloat() / denyMs
            val x = gx + (denyCell % n) * cellPx
            val y = gy + (denyCell / n) * cellPx
            fill.color = Theme.RED
            fill.alpha = (110 * (1f - f)).toInt()
            c.drawRect(x + d, y + d, x + cellPx - d, y + cellPx - d, fill)
            fill.alpha = 255
            cross.alpha = (255 * (1f - f)).toInt()
            val m = cellPx * 0.3f
            c.drawLine(x + m, y + m, x + cellPx - m, y + cellPx - m, cross)
            c.drawLine(x + cellPx - m, y + m, x + m, y + cellPx - m, cross)
        }

        // 돌
        val board = g.board
        for (i in 0 until g.cells) {
            val v = board[i]
            if (v == ReversiGame.EMPTY) continue
            val x = gx + (i % n + 0.5f) * cellPx
            val y = gy + (i / n + 0.5f) * cellPx
            if (moving && i == moveCell) {
                // 놓은 돌 "탁": 크게 나타나서 내려앉음
                val f = (el.toFloat() / placeMs).coerceIn(0f, 1f)
                val lift = (1f - f) * (1f - f)
                RvArt.disc(c, art, x, y, discR, v == ReversiGame.BLACK, 1f, lift, (120 + 135 * f).toInt())
                continue
            }
            val k = if (moving) flipDistAt[i] else 0
            if (k > 0) {
                val f = (el - flipStartMs(k)).toFloat() / flipMs
                when {
                    // 아직 차례가 안 옴: 원래(상대) 색
                    f <= 0f -> RvArt.disc(c, art, x, y, discR, v != ReversiGame.BLACK)
                    // 뒤집히는 중: 가로로 납작해졌다가 반대 색으로 펴짐 + 살짝 들림
                    f < 1f -> {
                        val sx = abs(cos(PI.toFloat() * f))
                        val black = if (f < 0.5f) v != ReversiGame.BLACK else v == ReversiGame.BLACK
                        RvArt.disc(c, art, x, y, discR, black, sx, sin(PI.toFloat() * f))
                    }
                    else -> RvArt.disc(c, art, x, y, discR, v == ReversiGame.BLACK)
                }
            } else {
                RvArt.disc(c, art, x, y, discR, v == ReversiGame.BLACK)
            }
        }

        // 마지막 수 표시
        val last = g.lastMove
        if (last >= 0 && !(moving && el < placeMs)) {
            RvArt.lastMark(c, fill, gx + (last % n + 0.5f) * cellPx, gy + (last / n + 0.5f) * cellPx, cellPx * 0.07f)
        }

        if ((moving || denyActive(now)) && listener?.isPausedNow() != true) postInvalidateOnAnimation()
    }

    companion object {
        /** 판 테두리 두께 (칸 단위) */
        private const val EDGE = 0.32f
    }
}
