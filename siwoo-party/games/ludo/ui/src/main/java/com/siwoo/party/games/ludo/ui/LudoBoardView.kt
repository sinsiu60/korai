package com.siwoo.party.games.ludo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.ludo.GameConfig
import com.siwoo.party.games.ludo.LudoBoard
import com.siwoo.party.games.ludo.LudoGame
import com.siwoo.party.games.ludo.Move
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 십자형 판 (Canvas 하나): 판·마당·말 그리기, 이동 가능한 말 발광, 도착 칸 표시, 칸마다 통통 뛰는 이동, 잡힌 말이 마당으로 날아감.
 * - 이동 가능한 말: 금색 후광이 숨 쉬듯 커졌다 작아짐 + 도착할 칸에 같은 색 점선 고리 (입력을 기다리는 동안만 프레임 루프)
 * - 같은 칸의 말 여러 개는 작게 나란히 (2개 좌우, 3~4개 2×2)
 * - 터치: 이동 가능한 말 또는 그 말의 도착 칸 → listener.onPieceTapped
 * 연출 시계는 GameTimers.now() (일시정지 시간 제외) — 일시정지 중에는 프레임 루프를 멈춘다.
 * onDraw 에서는 객체를 만들지 않는다. 규칙 판정은 하지 않는다.
 */
class LudoBoardView(context: Context) : View(context) {

    interface Listener {
        /** 이동 가능한 말(또는 그 도착 칸)을 누름 */
        fun onPieceTapped(player: Int, piece: Int)
        /** 이동할 수 없는 곳을 누름 (입력 대기 중일 때만) */
        fun onEmptyTapped() {}
    }

    var listener: Listener? = null
    /** 연출 시계 (일시정지되는 게임 시간) */
    var timers: GameTimers? = null

    private var playerCount = 0
    private val seatOf = IntArray(MAX)
    private val colors = IntArray(MAX)
    private val darks = IntArray(MAX)
    private val seatColor = IntArray(GameConfig.SEATS) { LudoArt.EMPTY }
    private val seatLight = IntArray(GameConfig.SEATS) { LudoArt.EMPTY_LIGHT }

    // ── 표시 상태 (sync 때 게임에서 복사) ──
    /** shown[p * 4 + k] = 보이는 진행 (−1 대기) */
    private val shown = IntArray(MAX * PIECES) { -1 }

    // ── 이동 가능 표시 ──
    private val movable = BooleanArray(MAX * PIECES)
    private val destPos = IntArray(MAX * PIECES)
    private var movableCount = 0
    private var glowStart = 0L

    // ── 이동 연출 ──
    private var moveId = -1
    private val movePath = IntArray(8)
    private var moveLen = 0
    private var moveFrom = 0
    private var moveStart = 0L
    private var moveStep = 1L

    // ── 잡힌 말 날아가기 ──
    private val flying = BooleanArray(MAX * PIECES)
    private val flyX0 = FloatArray(MAX * PIECES)
    private val flyY0 = FloatArray(MAX * PIECES)
    private var flyStart = 0L
    private var flyDur = 1L

    // ── 배치 (칸 단위 → px) ──
    var boardLeft = 0f
        private set
    var boardTop = 0f
        private set
    var boardSize = 0f
        private set
    /** 한 칸 (px) */
    var unit = 0f
        private set

    // 말을 그릴 자리 (layoutPieces 에서 계산, px) — 그리기·터치가 같이 씀
    private val drawX = FloatArray(MAX * PIECES)
    private val drawY = FloatArray(MAX * PIECES)
    private val drawR = FloatArray(MAX * PIECES)
    private val drawVisible = BooleanArray(MAX * PIECES)
    private val ux = FloatArray(MAX * PIECES)
    private val uy = FloatArray(MAX * PIECES)
    private val pt = FloatArray(2)
    private val pt2 = FloatArray(2)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()

    // 프레임 루프 (연출·발광 중일 때만)
    private var looping = false
    private val frame = Runnable { onFrame() }

    init {
        isClickable = true
        contentDescription = "말판"
    }

    private fun now(): Long = timers?.now() ?: SystemClock.uptimeMillis()

    /** 판 준비: 플레이어 색과 앉은 자리 (빈 자리는 회색) */
    fun setup(playerColors: IntArray, seats: IntArray) {
        playerCount = playerColors.size
        for (s in 0 until GameConfig.SEATS) { seatColor[s] = LudoArt.EMPTY; seatLight[s] = LudoArt.EMPTY_LIGHT }
        for (p in 0 until playerCount) {
            colors[p] = playerColors[p]
            darks[p] = LudoArt.dark(playerColors[p])
            seatOf[p] = seats[p]
            seatColor[seats[p]] = playerColors[p]
            seatLight[seats[p]] = LudoArt.light(playerColors[p])
        }
        invalidate()
    }

    /** 게임 상태를 그대로 보여 줌 (이동 연출이 끝난 뒤) */
    fun sync(game: LudoGame) {
        for (p in 0 until playerCount) for (k in 0 until PIECES) shown[p * PIECES + k] = game.pos(p, k)
        moveId = -1
        invalidate()
    }

    /** 보이는 진행 값 (테스트용) */
    fun shownPos(p: Int, k: Int): Int = shown[p * PIECES + k]

    // ── 이동 가능 표시 ──

    /** 이동 가능한 말 발광 + 도착 칸 고리 (null 또는 빈 목록 = 끔) */
    fun setMovable(moves: List<Move>?) {
        for (i in movable.indices) movable[i] = false
        movableCount = 0
        if (moves != null) for (m in moves) {
            val id = m.player * PIECES + m.piece
            movable[id] = true
            destPos[id] = m.to
            movableCount++
        }
        glowStart = now()
        if (movableCount > 0) ensureRunning() else invalidate()
    }

    val glowing: Boolean get() = movableCount > 0
    fun isMovable(p: Int, k: Int): Boolean = movable[p * PIECES + k]

    // ── 연출 ──

    /**
     * 말 하나가 path 를 따라 칸마다 통통 뛰어감 (from = 출발 진행, −1 이면 마당에서 출발 칸으로 한 번 크게).
     * 한 걸음 stepMs.
     */
    fun animateMove(p: Int, k: Int, from: Int, path: IntArray, stepMs: Long) {
        moveId = p * PIECES + k
        moveFrom = from
        moveLen = minOf(path.size, movePath.size)
        for (i in 0 until moveLen) movePath[i] = path[i]
        moveStep = stepMs.coerceAtLeast(1)
        moveStart = now()
        ensureRunning()
    }

    /** 잡힌 말들 (player * 4 + piece) 이 지금 보이는 칸에서 마당으로 날아감 — sync 보다 먼저 부를 것 */
    fun flyHome(ids: IntArray, durMs: Long) {
        for (i in flying.indices) flying[i] = false
        for (id in ids) {
            flying[id] = true
            unitPoint(id / PIECES, id % PIECES, shown[id], pt)
            flyX0[id] = pt[0]
            flyY0[id] = pt[1]
        }
        flyStart = now()
        flyDur = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    /** 이동 연출 중 */
    val isMoving: Boolean get() = moveId >= 0 && now() - moveStart < moveStep * moveLen
    /** 프레임 루프가 도는 중 */
    val isAnimating: Boolean get() = looping
    val isFlying: Boolean get() = now() - flyStart < flyDur && anyFlying()

    private fun anyFlying(): Boolean {
        for (f in flying) if (f) return true
        return false
    }

    /** 진행 pos 칸의 가운데 (이 뷰 좌표) */
    fun cellCenter(p: Int, k: Int, pos: Int, out: PointF) {
        unitPoint(p, k, pos, pt2)
        out.set(boardLeft + pt2[0] * unit, boardTop + pt2[1] * unit)
    }

    /** 지금 말이 그려진 자리 (이 뷰 좌표, 테스트·연출용) */
    fun pieceCenter(p: Int, k: Int, out: PointF) {
        layoutPieces(now())
        val id = p * PIECES + k
        out.set(drawX[id], drawY[id])
    }

    private fun unitPoint(p: Int, k: Int, pos: Int, out: FloatArray) = LudoBoard.point(seatOf[p], k, pos, out)

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
        if (movableCount > 0) return true // 발광은 입력을 기다리는 동안만
        if (moveId >= 0 && t - moveStart < moveStep * moveLen + moveStep) return true
        if (t - flyStart < flyDur + 50 && anyFlying()) return true
        return false
    }

    /** 일시정지: 프레임 루프 멈춤 (시계도 멈춰 있으므로 다시 시작하면 이어서) */
    fun pauseAnims() {
        looping = false
        removeCallbacks(frame)
    }

    fun resumeAnims() {
        if (anyActive(now())) ensureRunning()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val side = minOf(w, h).toFloat() - Theme.dpf(context, 8f)
        boardSize = side
        boardLeft = (w - side) / 2f
        boardTop = (h - side) / 2f
        unit = side / LudoBoard.SIZE
    }

    /** 말마다 그릴 자리 계산 (같은 칸의 말은 작게 나란히) */
    private fun layoutPieces(t: Long) {
        val n = playerCount * PIECES
        for (id in 0 until n) {
            unitPoint(id / PIECES, id % PIECES, shown[id], pt)
            ux[id] = pt[0]
            uy[id] = pt[1]
            drawVisible[id] = id != moveId && !(flying[id] && t - flyStart < flyDur)
        }
        for (id in 0 until n) {
            if (!drawVisible[id]) continue
            val pos = shown[id]
            var base = if (pos < 0) unit * 0.5f else if (pos >= GameConfig.HOME) unit * 0.3f else unit * 0.4f
            var dx = 0f
            var dy = 0f
            if (pos >= 0 && pos < GameConfig.HOME) {
                // 같은 칸에 몇 개, 나는 몇 번째
                var count = 0
                var mine = 0
                for (o in 0 until n) {
                    if (!drawVisible[o] || shown[o] < 0 || shown[o] >= GameConfig.HOME) continue
                    if (abs(ux[o] - ux[id]) < 0.01f && abs(uy[o] - uy[id]) < 0.01f) {
                        if (o < id) mine++
                        count++
                    }
                }
                if (count == 2) {
                    base = unit * 0.34f
                    dx = if (mine == 0) -0.2f else 0.2f
                } else if (count >= 3) {
                    base = unit * 0.3f
                    dx = if (mine % 2 == 0) -0.21f else 0.21f
                    dy = if (mine / 2 == 0) -0.21f else 0.21f
                }
            }
            drawX[id] = boardLeft + (ux[id] + dx) * unit
            drawY[id] = boardTop + (uy[id] + dy) * unit
            drawR[id] = base
        }
        // 움직이는 말 (통통)
        if (moveId >= 0) {
            val p = moveId / PIECES
            val k = moveId % PIECES
            val e = t - moveStart
            var si = (e / moveStep).toInt()
            var f = (e - si * moveStep).toFloat() / moveStep
            if (si >= moveLen) { si = moveLen - 1; f = 1f }
            if (si < 0) { si = 0; f = 0f }
            unitPoint(p, k, if (si == 0) moveFrom else movePath[si - 1], pt)
            unitPoint(p, k, movePath[si], pt2)
            val hop = sin(f * PI.toFloat()) * (if (moveFrom < 0) 1.6f else 0.55f)
            drawX[moveId] = boardLeft + (pt[0] + (pt2[0] - pt[0]) * f) * unit
            drawY[moveId] = boardTop + (pt[1] + (pt2[1] - pt[1]) * f - hop) * unit
            drawR[moveId] = unit * (0.42f + 0.08f * sin(f * PI.toFloat()))
            drawVisible[moveId] = true
        }
        // 마당으로 날아가는 말 (포물선)
        if (t - flyStart < flyDur) {
            val f = ((t - flyStart).toFloat() / flyDur).coerceIn(0f, 1f)
            for (id in 0 until n) {
                if (!flying[id]) continue
                unitPoint(id / PIECES, id % PIECES, -1, pt)
                val arc = sin(f * PI.toFloat()) * 2.5f
                drawX[id] = boardLeft + (flyX0[id] + (pt[0] - flyX0[id]) * f) * unit
                drawY[id] = boardTop + (flyY0[id] + (pt[1] - flyY0[id]) * f - arc) * unit
                drawR[id] = unit * (0.42f + 0.25f * sin(f * PI.toFloat()))
                drawVisible[id] = true
            }
        }
    }

    override fun onDraw(c: Canvas) {
        if (boardSize <= 0f) return
        val t = now()
        LudoArt.drawBoard(c, boardLeft, boardTop, boardSize, seatColor, seatLight, fill, path)
        layoutPieces(t)
        val n = playerCount * PIECES
        val pulse = if (movableCount > 0) 0.5f + 0.5f * sin(((t - glowStart) % GameConfig.GLOW_PULSE_MS).toFloat() / GameConfig.GLOW_PULSE_MS * 2f * PI.toFloat()) else 0f

        // 도착할 칸: 말 색 점선 느낌 고리 + 가운데 점
        if (movableCount > 0) {
            for (id in 0 until n) {
                if (!movable[id]) continue
                unitPoint(id / PIECES, id % PIECES, destPos[id], pt)
                val x = boardLeft + pt[0] * unit
                val y = boardTop + pt[1] * unit
                val rr = unit * (if (destPos[id] >= GameConfig.HOME) 0.3f else 0.42f)
                fill.color = colors[id / PIECES]
                fill.alpha = (70 + 60 * pulse).toInt()
                c.drawCircle(x, y, rr, fill)
                fill.alpha = 255
                stroke.color = Theme.GOLD
                stroke.strokeWidth = unit * 0.09f
                c.drawCircle(x, y, rr * (0.95f + 0.1f * pulse), stroke)
            }
        }

        // 이동 가능한 말 후광 (말 아래)
        if (movableCount > 0) {
            for (id in 0 until n) {
                if (!movable[id] || !drawVisible[id]) continue
                val r = drawR[id]
                fill.color = Theme.GOLD_LIGHT
                for (ring in 3 downTo 1) {
                    fill.alpha = ((50 + 40 * pulse) * (4 - ring) / 3).toInt()
                    c.drawCircle(drawX[id], drawY[id], r * (1.05f + ring * 0.22f + pulse * 0.2f), fill)
                }
                fill.alpha = 255
            }
        }

        // 말 (마당·집·길 — 움직이는 말과 날아가는 말은 맨 위에)
        for (id in 0 until n) {
            if (!drawVisible[id] || id == moveId || flying[id] && t - flyStart < flyDur) continue
            LudoArt.drawPiece(c, drawX[id], drawY[id], drawR[id], colors[id / PIECES], darks[id / PIECES], fill)
        }
        if (movableCount > 0) {
            for (id in 0 until n) {
                if (!movable[id] || !drawVisible[id]) continue
                stroke.color = Theme.GOLD
                stroke.strokeWidth = unit * (0.08f + 0.04f * pulse)
                c.drawCircle(drawX[id], drawY[id], drawR[id] * 1.12f, stroke)
            }
        }
        for (id in 0 until n) {
            if (!drawVisible[id]) continue
            if (id == moveId || flying[id] && t - flyStart < flyDur) {
                LudoArt.drawPiece(c, drawX[id], drawY[id], drawR[id], colors[id / PIECES], darks[id / PIECES], fill)
            }
        }
    }

    // ── 터치 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
            val id = pick(e.x, e.y)
            if (id >= 0) listener?.onPieceTapped(id / PIECES, id % PIECES)
            else if (movableCount > 0) listener?.onEmptyTapped()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** 누른 곳의 이동 가능한 말 (말 자체 → 도착 칸 순으로 찾음), 없으면 −1 */
    fun pick(x: Float, y: Float): Int {
        if (movableCount == 0 || unit <= 0f) return -1
        layoutPieces(now())
        val n = playerCount * PIECES
        var best = -1
        var bestD = Float.MAX_VALUE
        for (id in 0 until n) {
            if (!movable[id] || !drawVisible[id]) continue
            val dx = x - drawX[id]
            val dy = y - drawY[id]
            val d = dx * dx + dy * dy
            val lim = maxOf(drawR[id] * 1.5f, unit * 0.6f)
            if (d < lim * lim && d < bestD) { bestD = d; best = id }
        }
        if (best >= 0) return best
        for (id in 0 until n) {
            if (!movable[id]) continue
            unitPoint(id / PIECES, id % PIECES, destPos[id], pt)
            val dx = x - (boardLeft + pt[0] * unit)
            val dy = y - (boardTop + pt[1] * unit)
            val d = dx * dx + dy * dy
            val lim = unit * 0.6f
            if (d < lim * lim && d < bestD) { bestD = d; best = id }
        }
        return best
    }

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MAX = GameConfig.MAX_PLAYERS
        private const val PIECES = GameConfig.PIECES
    }
}
