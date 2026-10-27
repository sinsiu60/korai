package com.siwoo.party.games.yut.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.yut.GameConfig
import com.siwoo.party.games.yut.YutBoard
import com.siwoo.party.games.yut.YutGame
import kotlin.math.PI
import kotlin.math.sin

/**
 * 윷판 (Canvas 하나): 한지 바탕 + 먹선, 모서리·방은 큰 원.
 * - 말: 플레이어 색 원 + 번호. 업힌 묶음은 겹쳐 쌓고 "×2" 같은 겹친 수 표시
 * - 고른 결과로 움직일 수 있는 말: 금색 점선 고리 / 고른 말: 금색 고리
 * - 도착 예정 칸 미리보기: 지나갈 칸 점 + 도착 칸이 숨 쉬듯 발광 (골인이면 참먹이에 "골인")
 * - 이동: 말이 칸마다 통통 뛰어감, 업기는 통 하고 커짐, 칸 위로 글자가 떠오름
 * 연출 시계는 GameTimers.now() (일시정지 시간 제외) — 일시정지 중에는 프레임 루프를 멈춘다.
 * onDraw 에서는 객체를 만들지 않는다. 규칙 판정은 하지 않고 누른 칸만 알린다.
 */
class YutBoardView(context: Context) : View(context) {

    interface Listener {
        /** 칸(말·도착 칸)을 누름 */
        fun onNodeTapped(node: Int)
    }

    var listener: Listener? = null
    /** 연출 시계 (일시정지되는 게임 시간) */
    var timers: GameTimers? = null

    private var playerCount = 0
    private var pieceCount = 0
    private var colors = IntArray(0)
    private var darks = IntArray(0)
    private var textColors = IntArray(0)

    // ── 표시 상태 (sync 때 게임에서 복사) ──
    /** shown[p * 4 + k] = 말이 보이는 칸 (-1 = 판 밖) */
    private val shown = IntArray(GameConfig.MAX_PLAYERS * GameConfig.PIECES_MAX) { -1 }
    private val nodeOwner = IntArray(YutBoard.NODES) { -1 }
    private val nodeCount = IntArray(YutBoard.NODES)
    private val nodeLead = IntArray(YutBoard.NODES)

    // ── 이동 연출 ──
    private var movePlayer = -1
    private val moving = BooleanArray(GameConfig.PIECES_MAX)
    private var moveCount = 0
    private var moveLead = 0
    private val movePath = IntArray(32)
    private var moveLen = 0
    private var moveFrom = 0
    private var moveStart = 0L
    private var moveStep = 1L

    // ── 선택·미리보기 ──
    var selectedNode = -1
        private set
    private val movable = BooleanArray(YutBoard.NODES)
    private val previewPath = IntArray(32)
    private var previewLen = 0
    /** 미리보기 도착 칸 (-1 = 없음, 골인이면 참먹이 0) */
    var previewNode = -1
        private set
    var previewGoal = false
        private set
    private var previewColor = Theme.GOLD
    private var previewStart = 0L

    // ── 통 커짐·떠오르는 글자 ──
    private var popNode = -1
    private var popStart = 0L
    private var popDur = 1L
    private var floatNode = -1
    private var floatText = ""
    private var floatColor = Theme.GOLD
    private var floatStart = 0L
    private var floatDur = 1L

    // ── 배치 ──
    var boardLeft = 0f
        private set
    var boardTop = 0f
        private set
    var boardSize = 0f
        private set
    private var gridL = 0f
    private var gridT = 0f
    private var gridS = 0f
    /** 말 반지름 (px) */
    var pieceR = 0f
        private set

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = YutArt.INK }
    private val floatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val floatOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = YutArt.INK
    }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    /** 한지 결 (판 좌표 0~1 선분들) */
    private val fiber = FloatArray(FIBERS * 4).also {
        val r = java.util.Random(11)
        for (k in 0 until FIBERS) {
            val x = r.nextFloat()
            val y = r.nextFloat()
            val a = r.nextFloat() * PI.toFloat()
            val l = 0.02f + r.nextFloat() * 0.05f
            it[k * 4] = x
            it[k * 4 + 1] = y
            it[k * 4 + 2] = (x + kotlin.math.cos(a) * l).coerceIn(0.02f, 0.98f)
            it[k * 4 + 3] = (y + sin(a) * l).coerceIn(0.02f, 0.98f)
        }
    }

    // 프레임 루프 (연출 중일 때만)
    private var looping = false
    private val frame = Runnable { onFrame() }

    init {
        isClickable = true
        contentDescription = "윷판"
    }

    private fun now(): Long = timers?.now() ?: SystemClock.uptimeMillis()

    /** 판 준비: 플레이어 색, 말 수 */
    fun setup(playerColors: IntArray, pieces: Int) {
        playerCount = playerColors.size
        pieceCount = pieces
        colors = playerColors
        darks = IntArray(playerColors.size) { Theme.shade(playerColors[it], 0.45f) }
        textColors = IntArray(playerColors.size) { Theme.textOn(playerColors[it]) }
        invalidate()
    }

    /** 게임 상태를 그대로 보여 줌 (이동 연출이 끝난 뒤) */
    fun sync(game: YutGame) {
        for (p in 0 until playerCount) for (k in 0 until pieceCount) shown[p * 4 + k] = game.node(p, k)
        movePlayer = -1
        moveCount = 0
        for (k in moving.indices) moving[k] = false
        rebuildNodes()
        invalidate()
    }

    private fun rebuildNodes() {
        for (n in 0 until YutBoard.NODES) { nodeOwner[n] = -1; nodeCount[n] = 0; nodeLead[n] = 0 }
        for (p in 0 until playerCount) for (k in 0 until pieceCount) {
            val n = shown[p * 4 + k]
            if (n < 0) continue
            if (p == movePlayer && moving[k]) continue
            if (nodeCount[n] == 0) nodeLead[n] = k
            nodeOwner[n] = p
            nodeCount[n]++
        }
    }

    /** 칸 node 에 보이는 말 주인 (-1 = 빈 칸) */
    fun ownerShown(node: Int): Int = nodeOwner[node]
    /** 칸 node 에 보이는 말 수 */
    fun countShown(node: Int): Int = nodeCount[node]

    // ── 선택·미리보기 ──

    /** 고른 결과로 움직일 수 있는 칸 표시 (null = 없음) */
    fun setMovable(nodes: BooleanArray?) {
        for (n in movable.indices) movable[n] = nodes != null && nodes[n]
        invalidate()
    }

    fun setSelected(node: Int) {
        if (selectedNode == node) return
        selectedNode = node
        invalidate()
    }

    /** 도착 예정 칸 미리보기 (path: 지나갈 칸, 마지막이 도착 / 골인이면 참먹이) */
    fun setPreview(path: IntArray, goal: Boolean, color: Int) {
        previewLen = minOf(path.size, previewPath.size)
        for (k in 0 until previewLen) previewPath[k] = path[k]
        previewGoal = goal
        previewNode = if (goal) YutBoard.START else if (previewLen > 0) previewPath[previewLen - 1] else -1
        previewColor = color
        previewStart = now()
        ensureRunning()
    }

    fun clearPreview() {
        if (previewNode < 0 && previewLen == 0) return
        previewNode = -1
        previewLen = 0
        previewGoal = false
        invalidate()
    }

    val hasPreview: Boolean get() = previewNode >= 0

    // ── 연출 ──

    /**
     * 말 묶음이 path 를 따라 칸마다 뛰어감 (from = 출발 칸, 대기에서 올라오면 참먹이 0).
     * 한 칸 stepMs. 골인이면 path 마지막이 참먹이.
     */
    fun animateMove(player: Int, pieces: IntArray, from: Int, path: IntArray, stepMs: Long) {
        movePlayer = player
        for (k in moving.indices) moving[k] = false
        for (k in pieces) moving[k] = true
        moveCount = pieces.size
        moveLead = pieces[0]
        moveFrom = from
        moveLen = minOf(path.size, movePath.size)
        for (k in 0 until moveLen) movePath[k] = path[k]
        moveStep = stepMs.coerceAtLeast(1)
        moveStart = now()
        rebuildNodes()
        ensureRunning()
    }

    /** 업기: 칸 위 말이 통 하고 커짐 */
    fun pop(node: Int, durMs: Long) {
        popNode = node
        popStart = now()
        popDur = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    /** 칸 위로 글자가 떠오름 (text 는 미리 만든 글자) */
    fun floatLabel(node: Int, text: String, color: Int, durMs: Long) {
        floatNode = node
        floatText = text
        floatColor = color
        floatStart = now()
        floatDur = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    /** 이동 연출 중 */
    val isMoving: Boolean get() = movePlayer >= 0 && now() - moveStart < moveStep * moveLen

    /** 연출이 하나라도 진행 중 (프레임 루프가 도는 중) */
    val isAnimating: Boolean get() = looping

    /** 칸 가운데 (이 뷰 좌표) */
    fun nodeCenter(node: Int, out: PointF) {
        out.set(gridL + YutBoard.X[node] * gridS, gridT + YutBoard.Y[node] * gridS)
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
        if (previewNode >= 0) return true // 발광은 미리보기를 보여 주는 동안만
        if (movePlayer >= 0 && t - moveStart < moveStep * moveLen + moveStep) return true
        if (popNode >= 0 && t - popStart < popDur) return true
        if (floatNode >= 0 && t - floatStart < floatDur) return true
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
        val side = minOf(w, h).toFloat()
        boardSize = side - Theme.dpf(context, 4f)
        boardLeft = (w - boardSize) / 2f
        boardTop = (h - boardSize) / 2f
        gridL = boardLeft + boardSize * INSET
        gridT = boardTop + boardSize * INSET
        gridS = boardSize * (1f - INSET * 2f)
        pieceR = boardSize * 0.042f
        badgePaint.textSize = pieceR * 0.78f
        floatPaint.textSize = maxOf(pieceR * 1.3f, Theme.dpf(context, 16f))
        floatOutline.textSize = floatPaint.textSize
        floatOutline.strokeWidth = floatPaint.textSize * 0.16f
        goalPaint.textSize = pieceR * 0.95f
    }

    override fun onDraw(c: Canvas) {
        if (boardSize <= 0f) return
        val t = now()
        YutArt.drawBoard(c, boardLeft, boardTop, boardSize, INSET, fill, fiber, labelPaint)
        drawStartArrow(c)

        // 미리보기: 지나갈 칸 점 + 도착 칸 발광 (말 아래)
        val pulse = if (previewNode >= 0) 0.5f + 0.5f * sin(((t - previewStart) % GameConfig.GLOW_PULSE_MS).toFloat() / GameConfig.GLOW_PULSE_MS * 2f * PI.toFloat()) else 0f
        if (previewNode >= 0) {
            fill.style = Paint.Style.FILL
            for (k in 0 until previewLen - 1) {
                val n = previewPath[k]
                val x = nx(n)
                val y = ny(n)
                fill.color = previewColor
                fill.alpha = 120
                c.drawCircle(x, y, pieceR * 0.55f, fill)
                fill.color = Theme.GOLD_LIGHT
                fill.alpha = 230
                c.drawCircle(x, y, pieceR * 0.28f, fill)
            }
            val x = nx(previewNode)
            val y = ny(previewNode)
            for (ring in 3 downTo 1) {
                fill.color = previewColor
                fill.alpha = (40 + 35 * pulse).toInt() * (4 - ring) / 3
                c.drawCircle(x, y, pieceR * (1.15f + ring * 0.38f + pulse * 0.25f), fill)
            }
            fill.color = Theme.GOLD_LIGHT
            fill.alpha = (110 + 90 * pulse).toInt()
            c.drawCircle(x, y, pieceR * 1.15f, fill)
            fill.alpha = 255
        }

        // 움직일 수 있는 말 (금색 고리) · 고른 말 (굵은 고리)
        for (n in 0 until YutBoard.NODES) {
            if (!movable[n] || nodeCount[n] == 0) continue
            stroke.color = Theme.GOLD
            stroke.alpha = 200
            stroke.strokeWidth = pieceR * 0.16f
            c.drawCircle(nx(n), ny(n) - stackLift(nodeCount[n]) * 0.5f, pieceR * 1.45f, stroke)
            stroke.alpha = 255
        }

        // 판 위 말 (묶음)
        for (n in 0 until YutBoard.NODES) {
            val cnt = nodeCount[n]
            if (cnt == 0) continue
            var s = 1f
            if (n == popNode && t - popStart < popDur) {
                val f = (t - popStart).toFloat() / popDur
                s = 1f + 0.35f * sin(f * PI.toFloat())
            }
            drawStack(c, nx(n), ny(n), nodeOwner[n], nodeLead[n], cnt, s)
        }

        if (selectedNode >= 0 && nodeCount[selectedNode] > 0) {
            stroke.color = Theme.GOLD
            stroke.strokeWidth = pieceR * 0.26f
            c.drawCircle(nx(selectedNode), ny(selectedNode) - stackLift(nodeCount[selectedNode]) * 0.5f, pieceR * 1.5f, stroke)
            stroke.color = YutArt.INK
            stroke.strokeWidth = pieceR * 0.08f
            c.drawCircle(nx(selectedNode), ny(selectedNode) - stackLift(nodeCount[selectedNode]) * 0.5f, pieceR * 1.68f, stroke)
        }

        // 도착 칸 위 금색 고리 + 골인 글자 (말 위)
        if (previewNode >= 0) {
            val x = nx(previewNode)
            val y = ny(previewNode)
            stroke.color = Theme.GOLD
            stroke.strokeWidth = pieceR * 0.22f
            c.drawCircle(x, y, pieceR * (1.2f + pulse * 0.12f), stroke)
            if (previewGoal) {
                goalPaint.style = Paint.Style.STROKE
                goalPaint.strokeWidth = goalPaint.textSize * 0.25f
                goalPaint.color = Theme.GOLD_LIGHT
                c.drawText(GOAL, x - pieceR * 0.4f, y - pieceR * 1.9f, goalPaint)
                goalPaint.style = Paint.Style.FILL
                goalPaint.color = YutArt.SEAL
                c.drawText(GOAL, x - pieceR * 0.4f, y - pieceR * 1.9f, goalPaint)
            }
        }

        // 움직이는 묶음 (맨 위)
        if (movePlayer >= 0 && moveCount > 0) {
            val e = t - moveStart
            if (moveLen == 0) {
                drawStack(c, nx(moveFrom), ny(moveFrom), movePlayer, moveLead, moveCount, 1f)
            } else {
                var si = (e / moveStep).toInt()
                var f = (e - si * moveStep).toFloat() / moveStep
                if (si >= moveLen) { si = moveLen - 1; f = 1f }
                val a = if (si == 0) moveFrom else movePath[si - 1]
                val b = movePath[si]
                val x = nx(a) + (nx(b) - nx(a)) * f
                val hop = sin(f * PI.toFloat()) * pieceR * 1.3f
                val y = ny(a) + (ny(b) - ny(a)) * f - hop
                drawStack(c, x, y, movePlayer, moveLead, moveCount, 1f + 0.12f * sin(f * PI.toFloat()))
            }
        }

        // 떠오르는 글자
        val fd = t - floatStart
        if (floatNode >= 0 && fd < floatDur) {
            val f = fd.toFloat() / floatDur
            val a = if (f < 0.7f) 1f else 1f - (f - 0.7f) / 0.3f
            val x = nx(floatNode).coerceIn(boardLeft + floatPaint.textSize * 1.6f, boardLeft + boardSize - floatPaint.textSize * 1.6f)
            val y = (ny(floatNode) - pieceR * 2.4f - pieceR * 1.4f * f).coerceAtLeast(boardTop + floatPaint.textSize * 1.1f)
            floatOutline.alpha = (a * 255).toInt()
            floatPaint.color = floatColor
            floatPaint.alpha = (a * 255).toInt()
            c.drawText(floatText, x, y, floatOutline)
            c.drawText(floatText, x, y, floatPaint)
        }
    }

    /** 묶음 하나 그리기: 아래 말부터 위로 겹쳐 쌓고 맨 위에 대표 번호, 2개 이상이면 "×n" */
    private fun drawStack(c: Canvas, x: Float, y: Float, p: Int, lead: Int, count: Int, scale: Float) {
        val r = pieceR * scale
        val step = r * STACK_STEP
        numPaint.color = textColors[p]
        for (j in 0 until count) {
            val top = j == count - 1
            YutArt.drawPiece(c, x, y - j * step, r, colors[p], darks[p], if (top) NUMS[lead] else null, fill, if (top) numPaint else null)
        }
        if (count >= 2) {
            val bx = x + r * 0.95f
            val by = y - (count - 1) * step - r * 0.85f
            fill.color = YutArt.INK
            c.drawCircle(bx, by, r * 0.72f, fill)
            fill.color = Theme.GOLD
            c.drawCircle(bx, by, r * 0.62f, fill)
            badgePaint.textSize = r * 0.72f
            c.drawText(COUNTS[count], bx, by + badgePaint.textSize * 0.36f, badgePaint)
        }
    }

    /** 묶음이 위로 쌓이는 높이 */
    private fun stackLift(count: Int): Float = if (count <= 1) 0f else pieceR * STACK_STEP * (count - 1)

    /** 참먹이 옆 출발 방향 화살표 (오른쪽 변을 따라 위로) */
    private fun drawStartArrow(c: Canvas) {
        val x = gridL + gridS + boardSize * 0.045f
        val y0 = gridT + gridS - boardSize * 0.09f
        val y1 = gridT + gridS - boardSize * 0.2f
        stroke.color = YutArt.SEAL
        stroke.strokeWidth = boardSize * 0.008f
        c.drawLine(x, y0, x, y1, stroke)
        val a = boardSize * 0.014f
        c.drawLine(x, y1, x - a, y1 + a, stroke)
        c.drawLine(x, y1, x + a, y1 + a, stroke)
    }

    private fun nx(n: Int): Float = gridL + YutBoard.X[n] * gridS
    private fun ny(n: Int): Float = gridT + YutBoard.Y[n] * gridS

    // ── 터치: 가장 가까운 칸 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
            val n = pick(e.x, e.y)
            if (n >= 0) listener?.onNodeTapped(n)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** 누른 곳에서 가장 가까운 칸 (칸 간격의 반보다 멀면 -1) — 쌓인 묶음은 위쪽도 판정 */
    fun pick(x: Float, y: Float): Int {
        if (gridS <= 0f) return -1
        var best = -1
        var bestD = (gridS * 0.085f) * (gridS * 0.085f)
        for (n in 0 until YutBoard.NODES) {
            val dx = x - nx(n)
            var dy = y - ny(n)
            if (nodeCount[n] > 1 && dy < 0f) dy = minOf(0f, dy + stackLift(nodeCount[n]))
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = n }
        }
        return best
    }

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        /** 판 가장자리에서 바깥 칸 줄까지 (판 한 변 비율) */
        const val INSET = 0.085f
        private const val STACK_STEP = 0.34f
        private const val FIBERS = 160
        private const val GOAL = "골인"
        private val NUMS = arrayOf("1", "2", "3", "4")
        private val COUNTS = arrayOf("", "", "×2", "×3", "×4")
    }
}
