package com.siwoo.party.games.gomoku.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.gomoku.GameConfig
import com.siwoo.party.games.gomoku.GomokuGame
import com.siwoo.party.games.gomoku.Phase
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 오목 바둑판 (나무 판 + 15줄 격자 + 광택 흑·백돌).
 * - 판(나무·나뭇결·격자·화점)은 크기가 바뀔 때 RGB_565 비트맵으로 한 번 그려 두고(release 에서 recycle) 붙이기만 한다
 * - 돌 광택은 흑·백 RadialGradient 를 크기가 바뀔 때 하나씩 만들고 translate 해서 재사용
 * - 미리보기 돌: 반투명 돌 + 금색 고리 + 가로·세로 안내선
 * - 연출 두 가지만 스스로 다시 그린다 (진행 중일 때만, 일시정지면 멈춤): 돌 놓기 "탁", 승리 줄이 한 알씩 빛나기.
 *   시계는 listener.now() (= 게임 시계, 일시정지 시간 제외)
 */
class GoBoardView(context: Context) : View(context) {

    interface Listener {
        /** 지금 판을 눌러도 되는지 */
        fun canTap(): Boolean
        /** 교차점 (x, y) 를 누름 */
        fun onPoint(x: Int, y: Int)
        /** 게임 시계 (ms) */
        fun now(): Long
        /** 일시정지 중이면 연출을 다시 그리지 않음 */
        fun isPausedNow(): Boolean
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: GomokuGame? = null
    private var n = GameConfig.GOMOKU_SIZE
    private val playerColors = IntArray(GameConfig.PLAYERS)

    // ── 배치 (onSizeChanged) ──
    /** 줄 간격 (px) */
    var cellPx = 1f
        private set
    /** 교차점 (0,0) 의 화면 좌표 */
    private var gx = 0f
    private var gy = 0f
    /** 나무 판 왼쪽 위·크기 */
    private var bx = 0f
    private var by = 0f
    private var bs = 0f
    private var thick = 0f
    private var stoneR = 0f
    private var boardBmp: Bitmap? = null

    // ── 연출 시간 (속도 배율 적용된 값을 화면이 넣어 줌) ──
    var placeMs = GameConfig.PLACE_MS
    var winStepMs = GameConfig.WIN_STEP_MS
    var winGlowMs = GameConfig.WIN_GLOW_MS

    private var placedCell = -1
    private var placedAt = 0L
    private var winAt = -1L

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val winLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()

    init {
        isClickable = true
    }

    /** 게임과 플레이어 색 연결 */
    fun bind(game: GomokuGame, colors: IntArray) {
        this.game = game
        n = game.size
        for (i in playerColors.indices) playerColors[i] = colors[i]
        if (width > 0) layoutBoard(width, height)
        invalidate()
    }

    /** 교차점 (x, y) → 이 View 좌표 */
    fun pointCenter(x: Int, y: Int, out: PointF) {
        out.set(gx + x * cellPx, gy + y * cellPx)
    }

    /** 방금 놓은 돌 "탁" 연출 시작 */
    fun onPlaced(cell: Int) {
        placedCell = cell
        placedAt = listener?.now() ?: 0L
        invalidate()
    }

    /** 승리 줄 빛내기 시작 */
    fun startWin() {
        winAt = listener?.now() ?: 0L
        invalidate()
    }

    /** 승리 줄 연출 전체 길이 (ms) */
    fun winAnimMs(len: Int): Long = winStepMs * (len - 1).coerceAtLeast(0) + winGlowMs

    /** 테스트용: 연출이 진행 중인지 */
    val animating: Boolean
        get() {
            val g = game ?: return false
            val now = listener?.now() ?: return false
            return placeActive(now) || winActive(g, now)
        }

    /** 지금까지 빛난 승리 줄 돌 수 */
    private fun winLitCount(g: GomokuGame, now: Long): Int {
        if (winAt < 0 || g.winLength <= 0) return 0
        val el = now - winAt
        if (el < 0) return 0
        return minOf(g.winLength, (el / winStepMs.coerceAtLeast(1)).toInt() + 1)
    }

    private fun placeActive(now: Long) = placedCell >= 0 && now - placedAt < placeMs
    private fun winActive(g: GomokuGame, now: Long) = winAt >= 0 && now - winAt < winAnimMs(g.winLength)

    // ── 크기·비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        layoutBoard(w, h)
    }

    private fun layoutBoard(w: Int, h: Int) {
        thick = 6f * d
        // 나무 판 = 격자 (n−1) 칸 + 양쪽 여백 EDGE 칸
        bs = minOf(w.toFloat(), h - thick - 4f * d)
        cellPx = bs / (n - 1 + 2f * EDGE)
        bx = (w - bs) / 2f
        by = (h - bs - thick) / 2f
        gx = bx + EDGE * cellPx
        gy = by + EDGE * cellPx
        stoneR = cellPx * 0.47f
        rim.strokeWidth = maxOf(1f, 0.9f * d)
        ring.strokeWidth = 2.2f * d
        guide.strokeWidth = 1.6f * d
        frame.strokeWidth = 3f * d
        winLinePaint.strokeWidth = stoneR * 0.34f
        makeShaders()
        buildBoard()
    }

    private fun makeShaders() {
        val r = stoneR
        blackPaint.shader = RadialGradient(-r * 0.32f, -r * 0.38f, r * 1.5f,
            intArrayOf(0xFF7A7A7A.toInt(), 0xFF333333.toInt(), 0xFF111111.toInt(), 0xFF000000.toInt()),
            floatArrayOf(0f, 0.25f, 0.6f, 1f), Shader.TileMode.CLAMP)
        whitePaint.shader = RadialGradient(-r * 0.32f, -r * 0.38f, r * 1.5f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFF6F4EC.toInt(), 0xFFDCD9CE.toInt(), 0xFFA8A59A.toInt()),
            floatArrayOf(0f, 0.3f, 0.65f, 1f), Shader.TileMode.CLAMP)
    }

    /** 나무 윗면 + 나뭇결 + 격자 + 화점 + 앞쪽 옆면을 비트맵 하나에 */
    private fun buildBoard() {
        release()
        val size = ceil(bs).toInt().coerceAtLeast(1)
        val bh = ceil(bs + thick).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(size, bh, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val k = cellPx
        // 바탕 (판 밖 부분은 배경색으로 — 화면 배경과 비슷한 남색)
        c.drawColor(Theme.BG_BOTTOM)
        // 앞쪽 옆면
        p.shader = LinearGradient(0f, bs, 0f, bs + thick, GoArt.WOOD_SIDE, 0xFF5E3C18.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, bs, size.toFloat(), bh.toFloat(), p)
        // 윗면
        p.shader = LinearGradient(0f, 0f, size.toFloat(), bs,
            intArrayOf(GoArt.WOOD_LIGHT, GoArt.WOOD, 0xFFDDAE5E.toInt(), 0xFFE8BF73.toInt()),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), bs, p)
        p.shader = null
        // 나뭇결 (고정 난수 → 늘 같은 무늬)
        val rnd = Random(29)
        p.style = Paint.Style.STROKE
        val grain = Path()
        var y = rnd.nextFloat() * k
        while (y < bs) {
            p.strokeWidth = (0.5f + rnd.nextFloat() * 1.4f) * d
            p.color = if (rnd.nextInt(3) == 0) 0x1EFFF0D0 else (0x14 + rnd.nextInt(0x14) shl 24) or 0x6B3A10
            grain.reset()
            grain.moveTo(0f, y)
            var x = 0f
            val amp = (0.1f + rnd.nextFloat() * 0.3f) * k
            while (x < size) {
                val seg = (2f + rnd.nextFloat() * 3f) * k
                grain.quadTo(x + seg / 2f, y + (if (rnd.nextBoolean()) amp else -amp), x + seg, y + (rnd.nextFloat() - 0.5f) * 0.2f * k)
                x += seg
            }
            c.drawPath(grain, p)
            y += (0.25f + rnd.nextFloat() * 0.8f) * k
        }
        // 격자
        val ox = EDGE * k
        val end = ox + (n - 1) * k
        p.color = 0xE63A2410.toInt()
        p.strokeWidth = maxOf(1f, 0.9f * d)
        for (i in 0 until n) {
            val v = ox + i * k
            c.drawLine(v, ox, v, end, p)
            c.drawLine(ox, v, end, v, p)
        }
        p.strokeWidth = maxOf(1.5f, 1.8f * d)
        c.drawRect(ox, ox, end, end, p)
        // 화점
        p.style = Paint.Style.FILL
        if (n == GameConfig.GOMOKU_SIZE) {
            val s = GameConfig.STAR_POINTS
            var i = 0
            while (i < s.size) {
                c.drawCircle(ox + s[i] * k, ox + s[i + 1] * k, maxOf(2f, 0.12f * k), p)
                i += 2
            }
        }
        // 가장자리 입체감
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.color = 0x55FFF4DC
        c.drawLine(0f, d, size.toFloat(), d, p)
        c.drawLine(d, 0f, d, bs, p)
        p.color = 0x40000000
        c.drawLine(0f, bs - d, size.toFloat(), bs - d, p)
        c.drawLine(size - d, 0f, size - d, bs, p)
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
                // 손을 뗀 자리 기준 (누른 채 미끄러져도 뗀 곳으로)
                val c = cellAt(e.x, e.y)
                downCell = -1
                if (c >= 0 && listener?.canTap() == true) listener?.onPoint(c % g.size, c / g.size)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                downCell = -1
                return true
            }
        }
        return downCell >= 0
    }

    /** 화면 좌표 → 가장 가까운 교차점 칸 번호 (판에서 반 칸 넘게 벗어나면 −1) */
    fun cellAt(px: Float, py: Float): Int {
        val fx = (px - gx) / cellPx
        val fy = (py - gy) / cellPx
        if (fx < -0.5f || fy < -0.5f || fx > n - 0.5f || fy > n - 0.5f) return -1
        val x = fx.roundToInt().coerceIn(0, n - 1)
        val y = fy.roundToInt().coerceIn(0, n - 1)
        return y * n + x
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val now = listener?.now() ?: 0L
        // 판 그림자 + 판
        fill.color = 0x66000000
        rect.set(bx + 3f * d, by + 6f * d, bx + bs + 3f * d, by + bs + thick + 6f * d)
        c.drawRoundRect(rect, 6f * d, 6f * d, fill)
        val bmp = boardBmp
        if (bmp != null && !bmp.isRecycled) c.drawBitmap(bmp, bx, by, bmpPaint)

        // 차례(또는 이긴 사람) 색 테두리
        if (g.phase == Phase.PLAY || g.winner >= 0) {
            frame.color = playerColors[if (g.phase == Phase.OVER) g.winner else g.current]
            frame.alpha = 230
            rect.set(bx - 2.5f * d, by - 2.5f * d, bx + bs + 2.5f * d, by + bs + thick + 2.5f * d)
            c.drawRoundRect(rect, 4f * d, 4f * d, frame)
        }

        // 승리 줄 (돌 아래): 빛난 돌까지 금색 띠 + 퍼지는 빛
        val winLit = winLitCount(g, now)
        if (winLit > 0) {
            val line = g.winLine
            val el = now - winAt
            val last = winLit - 1
            if (last >= 1) {
                winLinePaint.color = 0xCCFFC93C.toInt()
                c.drawLine(gx + (line[0] % n) * cellPx, gy + (line[0] / n) * cellPx,
                    gx + (line[last] % n) * cellPx, gy + (line[last] / n) * cellPx, winLinePaint)
            }
            for (k in 0..last) {
                val f = ((el - k * winStepMs).toFloat() / winGlowMs).coerceIn(0f, 1f)
                fill.color = Theme.GOLD_LIGHT
                fill.alpha = (90 + 150 * (1f - f)).toInt()
                c.drawCircle(gx + (line[k] % n) * cellPx, gy + (line[k] / n) * cellPx, stoneR * (1.25f + 0.9f * (1f - f) * f * 4f), fill)
            }
            fill.alpha = 255
        }

        // 돌
        val board = g.board
        val placing = placeActive(now)
        for (i in 0 until g.cells) {
            val who = board[i]
            if (who == GomokuGame.EMPTY) continue
            val x = gx + (i % n) * cellPx
            val y = gy + (i / n) * cellPx
            var scale = 1f
            if (placing && i == placedCell) {
                val f = 1f - (now - placedAt).toFloat() / placeMs
                scale = 1f + 0.4f * f * f
            }
            drawStone(c, x, y, g.isBlack(who), scale, 255)
        }

        // 마지막 수 표시 (작은 빨간 점)
        val last = g.lastMove
        if (last >= 0 && !placing && g.winLength == 0) {
            fill.color = Theme.RED
            c.drawCircle(gx + (last % n) * cellPx, gy + (last / n) * cellPx, stoneR * 0.24f, fill)
        }

        // 미리보기 (안내선 + 반투명 돌 + 금색 고리)
        val pv = g.preview
        if (pv >= 0 && g.phase == Phase.PLAY) {
            val x = gx + (pv % n) * cellPx
            val y = gy + (pv / n) * cellPx
            guide.color = 0x99FFC93C.toInt()
            c.drawLine(gx, y, gx + (n - 1) * cellPx, y, guide)
            c.drawLine(x, gy, x, gy + (n - 1) * cellPx, guide)
            drawStone(c, x, y, g.isBlack(g.current), 1f, PREVIEW_ALPHA)
            ring.color = Theme.GOLD
            c.drawCircle(x, y, stoneR * 1.18f, ring)
        }

        // 승리 줄 (돌 위): 빛난 돌마다 금색 고리
        if (winLit > 0) {
            val line = g.winLine
            ring.color = Theme.GOLD
            ring.strokeWidth = 3f * d
            for (k in 0 until winLit) c.drawCircle(gx + (line[k] % n) * cellPx, gy + (line[k] / n) * cellPx, stoneR * 1.04f, ring)
            ring.strokeWidth = 2.2f * d
        }
        val animating = placing || winActive(g, now)
        if (animating && listener?.isPausedNow() != true) postInvalidateOnAnimation()
    }

    private fun drawStone(c: Canvas, x: Float, y: Float, black: Boolean, scale: Float, alpha: Int) {
        val r = stoneR
        c.save()
        c.translate(x, y)
        if (scale != 1f) c.scale(scale, scale)
        shadow.alpha = 0x66 * alpha / 255
        c.drawCircle(r * 0.1f, r * 0.18f * scale, r * 1.01f, shadow)
        val p = if (black) blackPaint else whitePaint
        p.alpha = alpha
        c.drawCircle(0f, 0f, r, p)
        rim.color = if (black) 0xFF000000.toInt() else 0xFF8C897E.toInt()
        rim.alpha = alpha
        c.drawCircle(0f, 0f, r - rim.strokeWidth / 2f, rim)
        c.restore()
    }

    companion object {
        /** 판 가장자리 여백 (칸 단위) */
        private const val EDGE = 0.8f
        private const val PREVIEW_ALPHA = 130
    }
}
