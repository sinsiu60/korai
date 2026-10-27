package com.siwoo.party.games.connect4.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.connect4.Bounce
import com.siwoo.party.games.connect4.Connect4Game
import com.siwoo.party.games.connect4.GameConfig
import com.siwoo.party.games.connect4.Phase
import kotlin.math.ceil
import kotlin.math.sin

/**
 * 사목 판 (세운 판: 틀에 구멍 7 × 6, 위쪽에 떨어뜨릴 돌이 떠 있는 자리).
 * 그리는 순서: 구멍 속 어두운 바탕 → 돌(쌓인 돌 + 떨어지는 돌) → 구멍 뚫린 틀 비트맵 → 승리 빛 → 위쪽 대기 돌.
 * 돌이 틀 뒤에서 떨어지므로 구멍 사이로 보였다 가렸다 한다 (진짜 판처럼).
 * - 틀(구멍 뚫기·입체 테두리)은 크기가 바뀔 때 ARGB 비트맵으로 한 번 그리고(release 에서 recycle) 붙이기만 한다
 * - 돌 광택은 플레이어마다 RadialGradient 를 크기가 바뀔 때 만들고 translate 해서 재사용
 * - 스스로 다시 그리는 건 연출 중일 때만 (떨어지며 튕기기·꽉 찬 열 흔들기·승리 빛). 시계 = listener.now() (게임 시계)
 */
class DropBoardView(context: Context) : View(context) {

    interface Listener {
        /** 지금 열을 골라도 되는지 */
        fun canTap(): Boolean
        /** col 열을 골랐음 (손을 뗀 곳) */
        fun onColumn(col: Int)
        /** 게임 시계 (ms) */
        fun now(): Long
        /** 일시정지 중이면 연출을 다시 그리지 않음 */
        fun isPausedNow(): Boolean
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: Connect4Game? = null
    private var cols = GameConfig.CONNECT4_COLS
    private var rows = GameConfig.CONNECT4_ROWS
    private val colors = IntArray(GameConfig.PLAYERS)

    // ── 배치 (onSizeChanged) ──
    var cellPx = 1f
        private set
    /** 구멍 (0, 맨 위 줄) 의 중심 */
    private var gx = 0f
    private var gy = 0f
    /** 틀 바깥 사각형 (뷰 좌표) */
    private val frameRect = RectF()
    private var discR = 0f
    /** 위쪽 대기 돌 중심 y */
    private var hoverY = 0f
    private var frameBmp: Bitmap? = null
    private var frameLeft = 0f
    private var frameTop = 0f

    // ── 연출 ──
    /** 속도 배율 (t(1000)/1000) — 떨어지기 시간에 곱함 */
    var speed = 1f
    var shakeMs = GameConfig.SHAKE_MS
    var winStepMs = GameConfig.WIN_STEP_MS
    var winGlowMs = GameConfig.WIN_GLOW_MS

    /** 떨어지는 돌 (−1 = 없음) */
    private var fallCol = -1
    private var fallRow = -1
    private var fallAt = 0L
    private var fallDist = 0f
    private var shakeCol = -1
    private var shakeAt = 0L
    private var winAt = -1L

    /** 위쪽 대기 돌이 있는 열 (마지막으로 가리킨 열) */
    var hoverCol = GameConfig.CONNECT4_COLS / 2
        private set
    /** 손가락이 눌려 있는지 (열 강조) */
    var pressing = false
        private set

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val discPaint = Array(GameConfig.PLAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arrow = Path()
    private val rect = RectF()

    init {
        isClickable = true
    }

    fun bind(game: Connect4Game, playerColors: IntArray) {
        this.game = game
        cols = game.cols
        rows = game.rows
        hoverCol = cols / 2
        for (i in colors.indices) colors[i] = playerColors[i]
        if (width > 0) layoutBoard(width, height)
        invalidate()
    }

    /** 열 col · 행 row(0 = 맨 아래) 구멍 중심 → 이 View 좌표 */
    fun holeCenter(col: Int, row: Int, out: PointF) {
        out.set(gx + col * cellPx, gy + (rows - 1 - row) * cellPx)
    }

    /** 열 col 의 가운데 x */
    fun columnX(col: Int): Float = gx + col * cellPx

    /** 떨어뜨리기 시작 (위쪽 대기 자리 → row 칸). 걸리는 시간은 dropTotalMs / dropImpactMs */
    fun startDrop(col: Int, row: Int) {
        fallCol = col
        fallRow = row
        fallAt = listener?.now() ?: 0L
        fallDist = dropDist(row)
        hoverCol = col
        invalidate()
    }

    /** row 칸까지 떨어지는 데 걸리는 전체 시간 (배율 적용, ms) */
    fun dropTotalMs(row: Int): Long = (Bounce.totalMs(dropDist(row)) * speed).toLong() + 1L

    /** 처음 바닥(또는 아래 돌)에 닿는 시간 (배율 적용, ms) */
    fun dropImpactMs(row: Int): Long = (Bounce.fallMs(dropDist(row)) * speed).toLong()

    /** 위쪽 대기 자리 → row 칸 중심까지 거리 (칸) */
    private fun dropDist(row: Int): Float = (rows - 1 - row) + (gy - hoverY) / cellPx

    /** 떨어지기 끝 (화면이 착지 시점에 부름) */
    fun endDrop() {
        fallCol = -1
        invalidate()
    }

    /** 꽉 찬 열 흔들기 */
    fun shakeColumn(col: Int) {
        shakeCol = col
        shakeAt = listener?.now() ?: 0L
        invalidate()
    }

    /** 승리 넷 빛내기 시작 */
    fun startWin() {
        winAt = listener?.now() ?: 0L
        invalidate()
    }

    fun winAnimMs(len: Int): Long = winStepMs * (len - 1).coerceAtLeast(0) + winGlowMs

    /** 테스트용: 연출 중인지 */
    val animating: Boolean
        get() {
            val g = game ?: return false
            val now = listener?.now() ?: return false
            return fallActive(now) || shakeActive(now) || winActive(g, now)
        }

    /** 테스트용: 떨어지는 중인지 */
    val falling: Boolean get() = fallCol >= 0

    private fun fallActive(now: Long) = fallCol >= 0 && (now - fallAt) < Bounce.totalMs(fallDist) * speed
    private fun shakeActive(now: Long) = shakeCol >= 0 && now - shakeAt < shakeMs
    private fun winActive(g: Connect4Game, now: Long) = winAt >= 0 && now - winAt < winAnimMs(g.winLength)

    // ── 크기·비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        layoutBoard(w, h)
    }

    private fun layoutBoard(w: Int, h: Int) {
        // 세로: 대기 자리 TOP 칸 + 틀(rows + 2·EDGE) + 받침 BASE 칸
        cellPx = minOf(w / (cols + 2f * EDGE), h / (TOP + rows + 2f * EDGE + BASE))
        val fw = (cols + 2f * EDGE) * cellPx
        val fh = (rows + 2f * EDGE) * cellPx
        val totalH = (TOP + rows + 2f * EDGE + BASE) * cellPx
        val top = (h - totalH) / 2f
        frameLeft = (w - fw) / 2f
        frameTop = top + TOP * cellPx
        frameRect.set(frameLeft, frameTop, frameLeft + fw, frameTop + fh)
        gx = frameLeft + (EDGE + 0.5f) * cellPx
        gy = frameTop + (EDGE + 0.5f) * cellPx
        hoverY = top + TOP * cellPx * 0.5f
        discR = cellPx * 0.4f
        rim.strokeWidth = maxOf(1f, 1.2f * d)
        ring.strokeWidth = 3f * d
        makeShaders()
        buildFrame()
    }

    private fun makeShaders() {
        val r = discR
        for (i in colors.indices) {
            val c = colors[i]
            discPaint[i].shader = RadialGradient(-r * 0.35f, -r * 0.4f, r * 1.5f,
                intArrayOf(C4Art.shade(c, 1.6f), C4Art.shade(c, 1.1f), c, C4Art.shade(c, 0.55f)),
                floatArrayOf(0f, 0.22f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
    }

    /** 틀: 그라데이션 판 + 구멍(투명) + 구멍 테두리 그림자 + 금색 테두리 + 받침 다리 */
    private fun buildFrame() {
        release()
        val k = cellPx
        val fw = frameRect.width()
        val fh = frameRect.height()
        val baseH = BASE * k
        val bw = ceil(fw).toInt().coerceAtLeast(1)
        val bh = ceil(fh + baseH).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rr = 0.28f * k
        // 받침 다리 (양 끝)
        p.color = C4Art.FRAME_DARK
        c.drawRoundRect(-0.0f, fh - rr, k * 0.9f, fh + baseH, rr * 0.5f, rr * 0.5f, p)
        c.drawRoundRect(fw - k * 0.9f, fh - rr, fw, fh + baseH, rr * 0.5f, rr * 0.5f, p)
        // 판
        p.shader = LinearGradient(0f, 0f, 0f, fh,
            intArrayOf(C4Art.FRAME_LIGHT, C4Art.FRAME, C4Art.FRAME_DARK),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, fw, fh, rr, rr, p)
        p.shader = null
        // 구멍 테두리 (아래쪽이 밝은 테 → 오목한 느낌)
        val hr = HOLE_R * k
        for (j in 0 until rows) for (i in 0 until cols) {
            val x = (EDGE + 0.5f + i) * k
            val y = (EDGE + 0.5f + j) * k
            p.color = 0x66FFFFFF
            c.drawCircle(x, y + 0.035f * k, hr + 0.035f * k, p)
            p.color = 0xAA0A0E2A.toInt()
            c.drawCircle(x, y - 0.03f * k, hr + 0.03f * k, p)
        }
        // 구멍 뚫기
        val clear = Paint(Paint.ANTI_ALIAS_FLAG)
        clear.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        for (j in 0 until rows) for (i in 0 until cols) {
            c.drawCircle((EDGE + 0.5f + i) * k, (EDGE + 0.5f + j) * k, hr, clear)
        }
        // 위쪽 빛 + 금색 테두리
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * d
        p.color = 0x55FFFFFF
        c.drawRoundRect(2f * d, 2f * d, fw - 2f * d, fh - 2f * d, rr, rr, p)
        p.strokeWidth = 2.5f * d
        p.color = Theme.GOLD
        c.drawRoundRect(1.25f * d, 1.25f * d, fw - 1.25f * d, fh - 1.25f * d, rr, rr, p)
        frameBmp = bmp
    }

    fun release() {
        frameBmp?.recycle()
        frameBmp = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (frameBmp == null && width > 0) buildFrame()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ── 터치 ──

    /** x → 열 (판 밖이면 −1) */
    fun colAt(x: Float): Int {
        val f = (x - gx) / cellPx + 0.5f
        if (f < 0f || f >= cols) return -1
        return f.toInt()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (game == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (listener?.canTap() != true) return false
                val col = colAt(e.x)
                if (col < 0) return false
                pressing = true
                hoverCol = col
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pressing) return false
                val col = colAt(e.x)
                if (col >= 0 && col != hoverCol) {
                    hoverCol = col
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!pressing) return false
                pressing = false
                val col = colAt(e.x)
                if (col >= 0) hoverCol = col
                invalidate()
                if (col >= 0 && listener?.canTap() == true) listener?.onColumn(col)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressing = false
                invalidate()
                return true
            }
        }
        return pressing
    }

    /** 일시정지 등에서 누름 취소 */
    fun cancelPress() {
        if (!pressing) return
        pressing = false
        invalidate()
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val now = listener?.now() ?: 0L
        val k = cellPx
        // 그림자 + 구멍 속 바탕
        fill.color = 0x66000000
        rect.set(frameRect.left + 4f * d, frameRect.top + 7f * d, frameRect.right + 4f * d, frameRect.bottom + 7f * d)
        c.drawRoundRect(rect, 0.28f * k, 0.28f * k, fill)
        fill.color = C4Art.HOLE
        rect.set(frameRect.left + 0.2f * k, frameRect.top + 0.2f * k, frameRect.right - 0.2f * k, frameRect.bottom - 0.2f * k)
        c.drawRect(rect, fill)

        // 누르고 있는 열 / 꽉 찬 열 강조 (구멍 속)
        val canTap = listener?.canTap() == true
        if (pressing && canTap && hoverCol >= 0) {
            fill.color = if (g.isFull(hoverCol)) 0x55FF4D5E else 0x33FFFFFF
            rect.set(columnX(hoverCol) - k * 0.5f, frameRect.top, columnX(hoverCol) + k * 0.5f, frameRect.bottom)
            c.drawRect(rect, fill)
        }

        // 쌓인 돌 (떨어지는 중인 칸은 따로)
        val shaking = shakeActive(now)
        var shakeDx = 0f
        if (shaking) {
            val f = (now - shakeAt).toFloat() / shakeMs
            shakeDx = sin((now - shakeAt) * 0.075f) * k * 0.12f * (1f - f)
        }
        val falling = fallActive(now)
        for (row in 0 until rows) for (col in 0 until cols) {
            val who = g.at(col, row)
            if (who == Connect4Game.EMPTY) continue
            if (fallCol == col && fallRow == row) continue
            val dx = if (shaking && col == shakeCol) shakeDx else 0f
            drawDisc(c, columnX(col) + dx, gy + (rows - 1 - row) * k, who, 255)
        }
        // 떨어지는 돌 (끝났는데 화면이 아직 endDrop 을 안 불렀으면 제자리)
        if (fallCol >= 0) {
            val who = g.at(fallCol, fallRow)
            if (who != Connect4Game.EMPTY) {
                val y = if (falling) hoverY + Bounce.y((now - fallAt) / speed, fallDist) * k else gy + (rows - 1 - fallRow) * k
                drawDisc(c, columnX(fallCol), y, who, 255)
            }
        }

        // 틀
        val bmp = frameBmp
        if (bmp != null && !bmp.isRecycled) c.drawBitmap(bmp, frameLeft, frameTop, bmpPaint)

        // 승리 넷: 한 알씩 빛남 (틀 위로 금빛 + 고리)
        if (winAt >= 0 && g.winLength > 0) {
            val el = now - winAt
            val line = g.winLine
            for (i in 0 until g.winLength) {
                val t = el - i * winStepMs
                if (t < 0) break
                val x = columnX(line[i] % cols)
                val y = gy + (rows - 1 - line[i] / cols) * k
                val f = (t.toFloat() / winGlowMs).coerceIn(0f, 1f)
                if (f < 1f) {
                    fill.color = Theme.GOLD_LIGHT
                    fill.alpha = (180 * (1f - f)).toInt()
                    c.drawCircle(x, y, discR * (1.05f + 0.9f * f), fill)
                    fill.alpha = 255
                }
                ring.color = Theme.GOLD
                ring.strokeWidth = 4f * d
                c.drawCircle(x, y, discR * 1.02f, ring)
                ring.color = 0xFFFFFFFF.toInt()
                ring.strokeWidth = 1.5f * d
                c.drawCircle(x, y, discR * 0.55f, ring)
            }
            ring.strokeWidth = 3f * d
        }

        // 위쪽 대기 돌 (내 차례에 열을 고르는 중)
        if (g.phase == Phase.PLAY && fallCol < 0 && canTap) {
            val x = columnX(hoverCol) + (if (shaking && hoverCol == shakeCol) shakeDx * 1.5f else 0f)
            drawDisc(c, x, hoverY, g.current, if (pressing) 255 else 200)
            // 아래 화살표
            fill.color = if (shaking && hoverCol == shakeCol) Theme.RED else Theme.GOLD
            val ay = hoverY + discR + 0.12f * k
            arrow.reset()
            arrow.moveTo(x - 0.16f * k, ay)
            arrow.lineTo(x + 0.16f * k, ay)
            arrow.lineTo(x, ay + 0.14f * k)
            arrow.close()
            c.drawPath(arrow, fill)
        }

        if ((falling || shaking || winActive(g, now)) && listener?.isPausedNow() != true) postInvalidateOnAnimation()
    }

    private fun drawDisc(c: Canvas, x: Float, y: Float, who: Int, alpha: Int) {
        val r = discR
        c.save()
        c.translate(x, y)
        val p = discPaint[who]
        p.alpha = alpha
        c.drawCircle(0f, 0f, r, p)
        rim.color = C4Art.shade(colors[who], 0.45f)
        rim.alpha = alpha
        c.drawCircle(0f, 0f, r - rim.strokeWidth / 2f, rim)
        rim.color = C4Art.shade(colors[who], 0.7f)
        rim.alpha = alpha
        c.drawCircle(0f, 0f, r * 0.62f, rim)
        c.restore()
    }

    companion object {
        /** 틀 가장자리 여백 (칸) */
        private const val EDGE = 0.22f
        /** 위쪽 대기 자리 높이 (칸) */
        private const val TOP = 1.15f
        /** 받침 다리 높이 (칸) */
        private const val BASE = 0.3f
        /** 구멍 반지름 (칸) */
        private const val HOLE_R = 0.42f
    }
}
