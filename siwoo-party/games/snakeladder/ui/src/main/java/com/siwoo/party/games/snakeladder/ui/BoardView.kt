package com.siwoo.party.games.snakeladder.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.snakeladder.GameConfig
import com.siwoo.party.games.snakeladder.Jump
import com.siwoo.party.games.snakeladder.SnakeMap
import kotlin.math.PI
import kotlin.math.sin

/**
 * 뱀과 사다리 판 (10×10 지그재그) + 왼쪽 출발 받침 + 말.
 * - 말이 여럿 같은 칸이면 작게 나란히 (2개 좌우, 3개 삼각형, 4개 2×2)
 * - animateMove: 한 칸씩 깡충 → (사다리·뱀 입구에서 잠깐 멈춤) → 사다리를 타고 오르거나 뱀 몸을 따라 미끄러짐
 * - 연출 중일 때만 postOnAnimation 루프, 시계는 GameTimers (일시정지되면 멈춤)
 * onDraw 에서 객체를 만들지 않는다.
 */
class BoardView(context: Context) : View(context) {

    var timers: GameTimers? = null
    val painter = BoardPainter()
    private var map: SnakeMap? = null

    private var playerCount = 0
    private var colors = IntArray(0)
    private var darks = IntArray(0)
    /** 보이는 위치 (0 = 출발 받침) */
    private val shown = IntArray(GameConfig.MAX_PLAYERS)
    /** 지금 차례 (금색 테두리) */
    private var current = -1

    // 판 배치
    private var gutter = 0f
    private var padL = 0f
    private var padT = 0f
    private var padR = 0f
    private var padB = 0f

    // ── 이동 연출 ──
    private var mover = -1
    private var moveFrom = 0
    private val walk = IntArray(GameConfig.DIE_FACES)
    private var walkLen = 0
    private val jumpFrom = IntArray(GameConfig.MAX_CHAIN)
    private val jumpTo = IntArray(GameConfig.MAX_CHAIN)
    private val jumpLadder = IntArray(GameConfig.MAX_CHAIN)
    private val jumpSnake = IntArray(GameConfig.MAX_CHAIN)
    private val jumpDur = LongArray(GameConfig.MAX_CHAIN)
    private var jumpCount = 0
    private var stepMs = 1L
    private var pauseMs = 0L
    private var moveStart = 0L
    private var moveTotal = 0L

    // ── 떠오르는 글자 ──
    private var floatText = ""
    private var floatColor = Theme.GOLD
    private var floatSquare = -1
    private var floatStart = 0L
    private var floatDur = 1L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val pa = PointF()
    private val pb = PointF()

    private var looping = false
    private val frame = Runnable { onFrame() }

    init {
        contentDescription = "뱀과 사다리 판"
    }

    private fun now(): Long = timers?.now() ?: SystemClock.uptimeMillis()

    fun setup(map: SnakeMap, playerColors: IntArray) {
        this.map = map
        playerCount = playerColors.size
        colors = playerColors
        darks = IntArray(playerColors.size) { Theme.shade(playerColors[it], 0.5f) }
        relayout()
        invalidate()
    }

    /** 게임 위치를 그대로 보여 줌 (연출이 끝난 뒤) */
    fun sync(positions: IntArray) {
        for (p in 0 until playerCount) shown[p] = positions[p]
        mover = -1
        invalidate()
    }

    fun setCurrent(p: Int) {
        if (current == p) return
        current = p
        invalidate()
    }

    /** 보이는 위치 (테스트용) */
    fun shownPosition(p: Int): Int = shown[p]

    /**
     * 말 p 이동 연출. walkPath = 한 칸씩 밟는 칸, jumps = 차례로 타는 사다리·뱀, jumpMs[i] = i 번째 사다리·뱀 시간.
     * 돌려주는 값 = 전체 연출 시간(ms).
     */
    fun animateMove(p: Int, from: Int, walkPath: IntArray, jumps: List<Jump>, step: Long, pause: Long, jumpMs: LongArray): Long {
        mover = p
        moveFrom = from
        walkLen = minOf(walkPath.size, walk.size)
        for (k in 0 until walkLen) walk[k] = walkPath[k]
        jumpCount = minOf(jumps.size, jumpFrom.size)
        stepMs = step.coerceAtLeast(1)
        pauseMs = if (jumpCount > 0) pause else 0L
        var total = stepMs * walkLen + pauseMs
        for (k in 0 until jumpCount) {
            val j = jumps[k]
            jumpFrom[k] = j.from
            jumpTo[k] = j.to
            jumpLadder[k] = if (j.isLadder) painter.ladderIndex(j) else -1
            jumpSnake[k] = if (j.isLadder) -1 else painter.snakeIndex(j)
            jumpDur[k] = jumpMs[k].coerceAtLeast(1)
            total += jumpDur[k]
        }
        moveTotal = total
        moveStart = now()
        // 도착 칸 = 최종 칸 (연출이 끝나면 sync 로 맞춤)
        ensureRunning()
        return total
    }

    /** 칸 위로 글자가 떠오름 (text 는 미리 만든 글자) */
    fun floatLabel(square: Int, text: String, color: Int, durMs: Long) {
        floatSquare = square
        floatText = text
        floatColor = color
        floatStart = now()
        floatDur = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    val isMoving: Boolean get() = mover >= 0 && now() - moveStart < moveTotal
    val isAnimating: Boolean get() = looping

    /** 칸(1..100, 0 = 출발 받침의 p 자리) 가운데 (이 뷰 좌표) */
    fun squareCenter(square: Int, p: Int, out: PointF) {
        if (square <= 0) padSlot(p, out) else painter.center(square, out)
    }

    private fun padSlot(p: Int, out: PointF) {
        val cell = painter.cell
        val x = (padL + padR) / 2f
        val y = padB - cell * 0.42f - p * cell * 0.62f
        out.set(x, y)
    }

    private fun ensureRunning() {
        invalidate()
        if (looping || timers?.paused == true) return
        looping = true
        postOnAnimation(frame)
    }

    private fun onFrame() {
        if (!looping) return
        if (timers?.paused == true) { looping = false; return }
        invalidate()
        if (anyActive(now())) postOnAnimation(frame) else looping = false
    }

    private fun anyActive(t: Long): Boolean {
        if (mover >= 0 && t - moveStart < moveTotal + 50) return true
        if (floatSquare >= 0 && t - floatStart < floatDur) return true
        return false
    }

    /** 일시정지: 프레임 루프 멈춤 (게임 시계도 멈춰 있으므로 이어서 진행) */
    fun pauseAnims() {
        looping = false
        removeCallbacks(frame)
    }

    fun resumeAnims() {
        if (anyActive(now())) ensureRunning()
    }

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout() {
        val m = map ?: return
        if (width == 0 || height == 0) return
        val margin = Theme.dpf(context, 8f)
        // 판 + 왼쪽 출발 받침(칸 1.2개 폭)
        val size = minOf(height - margin * 2, (width - margin * 2) / (1f + GUTTER_CELLS / 10f))
        val cell = size / 10f
        gutter = cell * GUTTER_CELLS
        val total = size + gutter
        val l = (width - total) / 2f + gutter
        val t = (height - size) / 2f
        painter.layout(m, l, t, size)
        padL = l - gutter
        padR = l - cell * 0.32f
        padB = t + size
        padT = padB - cell * 3.2f
    }

    override fun onDraw(c: Canvas) {
        if (map == null || painter.cell <= 0f) return
        val t = now()
        val cell = painter.cell
        // 출발 받침
        fill.style = Paint.Style.FILL
        fill.color = SnakeArt.FRAME
        c.drawRoundRect(padL, padT, padR, padB + cell * 0.22f, cell * 0.2f, cell * 0.2f, fill)
        fill.color = SnakeArt.START
        c.drawRoundRect(padL + cell * 0.1f, padT + cell * 0.1f, padR - cell * 0.1f, padB + cell * 0.12f, cell * 0.15f, cell * 0.15f, fill)
        padPaint.textSize = cell * 0.3f
        padPaint.color = 0xFF1B5E20.toInt()
        c.drawText(START_TEXT, (padL + padR) / 2f, padT + cell * 0.45f, padPaint)

        // 이동 연출 상태
        var glowLadder = -1
        var glowSnake = -1
        var glow = 0f
        var mx = 0f
        var my = 0f
        var moving = false
        if (mover >= 0) {
            moving = true
            val e = (t - moveStart).coerceAtLeast(0L)
            val walkEnd = stepMs * walkLen
            if (e < walkEnd) {
                val k = (e / stepMs).toInt()
                val f = (e - k * stepMs).toFloat() / stepMs
                squareCenter(if (k == 0) moveFrom else walk[k - 1], mover, pa)
                painter.center(walk[k], pb)
                mx = pa.x + (pb.x - pa.x) * f
                my = pa.y + (pb.y - pa.y) * f - sin(f * PI.toFloat()) * cell * 0.45f
            } else if (e < walkEnd + pauseMs || jumpCount == 0) {
                squareCenter(if (walkLen > 0) walk[walkLen - 1] else moveFrom, mover, pa)
                mx = pa.x
                my = pa.y
                if (jumpCount > 0) {
                    val f = ((e - walkEnd).toFloat() / pauseMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                    if (jumpLadder[0] >= 0) glowLadder = jumpLadder[0] else glowSnake = jumpSnake[0]
                    glow = f
                    // 입구에서 몸을 움찔 (작게 들썩)
                    my -= sin(f * PI.toFloat() * 2f) * cell * 0.08f
                }
            } else {
                var rest = e - walkEnd - pauseMs
                var k = 0
                while (k < jumpCount - 1 && rest >= jumpDur[k]) { rest -= jumpDur[k]; k++ }
                val f = (rest.toFloat() / jumpDur[k]).coerceIn(0f, 1f)
                glow = 1f - f * 0.3f
                if (jumpLadder[k] >= 0) {
                    glowLadder = jumpLadder[k]
                    // 가로대를 하나씩 디디듯 살짝 끊어 오름
                    val eased = f * f * (3f - 2f * f)
                    painter.ladderPoint(jumpLadder[k], eased, pa)
                    mx = pa.x + sin(f * PI.toFloat() * 10f) * cell * 0.06f
                    my = pa.y - kotlin.math.abs(sin(f * PI.toFloat() * 10f)) * cell * 0.08f
                } else if (jumpSnake[k] >= 0) {
                    glowSnake = jumpSnake[k]
                    // 점점 빨라지며 미끄러짐
                    painter.snakePoint(jumpSnake[k], f * f * 0.6f + f * 0.4f, pa)
                    mx = pa.x
                    my = pa.y
                } else {
                    // 맵에 없는 연쇄 (안전장치): 곧장 날아감
                    painter.center(jumpFrom[k], pa)
                    painter.center(jumpTo[k], pb)
                    mx = pa.x + (pb.x - pa.x) * f
                    my = pa.y + (pb.y - pa.y) * f
                }
                if (e >= moveTotal) {
                    painter.center(jumpTo[jumpCount - 1], pa)
                    mx = pa.x
                    my = pa.y
                    glowLadder = -1
                    glowSnake = -1
                }
            }
        }

        painter.drawBoard(c, true, fill)
        painter.drawLadders(c, glowLadder, glow, fill)
        painter.drawSnakes(c, glowSnake, glow, fill)

        // 서 있는 말 (같은 칸이면 나란히)
        for (p in 0 until playerCount) {
            if (moving && p == mover) continue
            val sq = shown[p]
            if (sq <= 0) {
                padSlot(p, pa)
                SnakeArt.drawToken(c, pa.x, pa.y, cell * 0.27f, colors[p], darks[p], p == current && !moving, fill)
                continue
            }
            var n = 0
            var idx = 0
            for (q in 0 until playerCount) {
                if (moving && q == mover) continue
                if (shown[q] == sq) {
                    if (q == p) idx = n
                    n++
                }
            }
            painter.center(sq, pa)
            val r = if (n == 1) cell * 0.3f else cell * 0.21f
            val ox = when (n) {
                1 -> 0f
                2 -> if (idx == 0) -0.22f else 0.22f
                3 -> if (idx == 0) 0f else if (idx == 1) -0.22f else 0.22f
                else -> if (idx % 2 == 0) -0.22f else 0.22f
            } * cell
            val oy = when (n) {
                1, 2 -> 0f
                3 -> if (idx == 0) -0.18f else 0.17f
                else -> if (idx < 2) -0.2f else 0.2f
            } * cell
            SnakeArt.drawToken(c, pa.x + ox, pa.y + oy, r, colors[p], darks[p], p == current && !moving, fill)
        }
        if (moving) SnakeArt.drawToken(c, mx, my, cell * 0.32f, colors[mover], darks[mover], true, fill)

        // 떠오르는 글자
        if (floatSquare >= 0) {
            val e = t - floatStart
            if (e < floatDur) {
                val f = e.toFloat() / floatDur
                painter.center(floatSquare, pa)
                val a = if (f < 0.7f) 255 else (255 * (1f - (f - 0.7f) / 0.3f)).toInt()
                val y = (pa.y - cell * 0.6f - f * cell * 0.9f).coerceAtLeast(cell * 0.5f)
                val x = pa.x.coerceIn(painter.left + cell * 1.6f, painter.left + painter.size - cell * 1.6f)
                painter.drawLabel(c, floatText, x, y, cell * 0.62f, floatColor, a.coerceIn(0, 255))
            }
        }
    }

    companion object {
        private const val GUTTER_CELLS = 1.3f
        private const val START_TEXT = "출발"
    }
}
