package com.siwoo.party.games.darts.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.aim.AimDrawer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.darts.GameConfig
import kotlin.math.PI
import kotlin.math.sin

/**
 * 다트판 화면: 어두운 벽 + 조명 + 다트판(크기가 정해질 때 한 번 비트맵으로 그려 둠) + 8자 궤적 미리 보기 + 흔들리는 조준점
 * + 이번 차례 다트 3개(날아감 → 꽂힘 → 회수) + 점수 글자(떠오르며 사라짐).
 * 좌표는 다트판 mm (가운데 (0, 0)). 시각은 Activity 가 프레임마다 [now] 에 넣는다(게임 시계, 일시정지 때 멈춤).
 * 스스로 반복해서 그리지 않는다 — Activity 프레임 루프가 [tick] 후 invalidate. onDraw 할당 없음.
 */
class BoardView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)

    /** 게임 시계 (프레임마다 Activity 가 넣음) */
    var now = 0L

    // ── 배치 ──
    private var cx = 0f
    private var cy = 0f
    /** px / mm */
    var scale = 1f
        private set
    private var dartLen = 0f
    private var boardBmp: Bitmap? = null
    private var bmpLeft = 0f
    private var bmpTop = 0f

    // ── 조준 ──
    /** 조준점 보이기 */
    var aimShown = false
        set(v) { field = v; invalidate() }
    /** 조준점 (mm) */
    var aimX = 0f
        private set
    var aimY = 0f
        private set
    private val aim = AimDrawer(context)
    private val trailX = FloatArray(GameConfig.TRAIL_POINTS)
    private val trailY = FloatArray(GameConfig.TRAIL_POINTS)
    var trailShown = false
        set(v) { field = v; invalidate() }

    // ── 다트 3개 ──
    private val state = IntArray(SLOTS)
    private val dx = FloatArray(SLOTS)
    private val dy = FloatArray(SLOTS)
    private val dColor = IntArray(SLOTS)
    private val dAngle = FloatArray(SLOTS)
    private val flyStart = LongArray(SLOTS)
    private val flyMs = LongArray(SLOTS)
    private val retStart = LongArray(SLOTS)
    private val retMs = LongArray(SLOTS)

    // ── 점수 글자 ──
    private val text = arrayOfNulls<String>(SLOTS)
    private val textColor = IntArray(SLOTS)
    private val textStart = LongArray(SLOTS)
    private val textMs = LongArray(SLOTS)

    // ── 그리기 도구 ──
    private val wall = Paint()
    private val spot = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val hitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hitStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; color = 0xE60B1340.toInt()
    }

    /** 꽂혀 있는(날아가는 중 포함) 다트 수 (테스트 확인용) */
    val stuckCount: Int get() {
        var n = 0
        for (s in state) if (s == STUCK) n++
        return n
    }

    /** 회수 중인 다트 수 */
    val retrievingCount: Int get() {
        var n = 0
        for (s in state) if (s == RETRIEVING) n++
        return n
    }

    /** 다트판 비트맵이 만들어졌는지 (테스트용) */
    val boardCached: Boolean get() = boardBmp != null

    fun setAim(xMm: Float, yMm: Float) {
        aimX = xMm
        aimY = yMm
    }

    /** 8자 궤적 미리 보기 (다트 시작 때 한 번) */
    fun setTrail(xs: FloatArray, ys: FloatArray) {
        System.arraycopy(xs, 0, trailX, 0, minOf(xs.size, trailX.size))
        System.arraycopy(ys, 0, trailY, 0, minOf(ys.size, trailY.size))
        invalidate()
    }

    /** 조준점 색 (차례인 사람 색) */
    fun setAimColor(color: Int) {
        aim.dotColor = color
        invalidate()
    }

    /** i 번째 다트를 (x, y) mm 로 던짐 (ms 동안 날아가 꽂힘) */
    fun throwDart(i: Int, xMm: Float, yMm: Float, color: Int, ms: Long) {
        state[i] = STUCK
        dx[i] = xMm
        dy[i] = yMm
        dColor[i] = color
        dAngle[i] = 22f + i * 9f
        flyStart[i] = now
        flyMs[i] = maxOf(1L, ms)
        invalidate()
    }

    /** i 번째 다트 점수 글자 (ms 동안 떠오르며 사라짐) */
    fun showText(i: Int, label: String, color: Int, ms: Long) {
        text[i] = label
        textColor[i] = color
        textStart[i] = now
        textMs[i] = maxOf(1L, ms)
        invalidate()
    }

    /** 꽂힌 다트를 모두 뽑아 회수 (하나씩 stagger 간격으로) */
    fun retrieveAll(ms: Long, staggerMs: Long) {
        var k = 0
        for (i in 0 until SLOTS) if (state[i] == STUCK) {
            state[i] = RETRIEVING
            retStart[i] = now + staggerMs * k
            retMs[i] = maxOf(1L, ms)
            k++
        }
        invalidate()
    }

    /** 다트·글자 모두 치움 */
    fun clearDarts() {
        for (i in 0 until SLOTS) { state[i] = NONE; text[i] = null }
        invalidate()
    }

    /** 시간에 따라 상태 정리 (회수 끝 → 없앰, 글자 끝 → 없앰). 아직 움직이는 게 있으면 true */
    fun tick(): Boolean {
        var busy = false
        for (i in 0 until SLOTS) {
            when (state[i]) {
                STUCK -> if (now - flyStart[i] < flyMs[i]) busy = true
                RETRIEVING -> if (now - retStart[i] >= retMs[i]) state[i] = NONE else busy = true
            }
            if (text[i] != null) {
                if (now - textStart[i] >= textMs[i]) text[i] = null else busy = true
            }
        }
        return busy
    }

    /** 다트판 mm → 이 View 좌표 */
    fun toScreen(xMm: Float, yMm: Float, out: PointF) {
        out.set(cx + xMm * scale, cy + yMm * scale)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        cx = w / 2f
        cy = h / 2f
        val radiusPx = minOf(w, h) / 2f - 4f * d
        scale = radiusPx / GameConfig.R_FRAME
        dartLen = DART_MM * scale
        wall.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF1A2350.toInt(), 0xFF0E1534.toInt(), 0xFF080C22.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        spot.shader = RadialGradient(cx, cy, radiusPx * 1.35f, intArrayOf(0x55FFE9B0, 0x22FFE9B0, 0x00FFE9B0),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        hitPaint.textSize = 17f * d
        hitStroke.textSize = 17f * d
        hitStroke.strokeWidth = 4.5f * d
        aim.radius = 13f * d
        buildBoard(radiusPx)
    }

    /** 다트판을 비트맵에 한 번 그려 둠 (크기가 바뀔 때만) */
    private fun buildBoard(radiusPx: Float) {
        val size = (radiusPx * 2f).toInt() + 4
        val old = boardBmp
        if (old != null && old.width == size) return
        old?.recycle()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val half = size / 2f
        val num = Paint(Paint.ANTI_ALIAS_FLAG)
        DartArt.numberPaint(num, 21f * scale)
        DartArt.board(c, p, half, half, scale, wires = true, text = num)
        // 나무 테두리 입체감 (위 밝게, 아래 어둡게) + 판 가장자리 그늘
        val rim = Paint(Paint.ANTI_ALIAS_FLAG)
        rim.style = Paint.Style.STROKE
        rim.strokeWidth = 5f * scale
        rim.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), 0x55FFFFFF, 0x55000000, Shader.TileMode.CLAMP)
        c.drawCircle(half, half, (GameConfig.R_FRAME - 6f) * scale, rim)
        val shade = Paint(Paint.ANTI_ALIAS_FLAG)
        shade.shader = RadialGradient(half, half, GameConfig.R_NUMBER_RING * scale,
            intArrayOf(0x00000000, 0x00000000, 0x40000000), floatArrayOf(0f, 0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(half, half, GameConfig.R_NUMBER_RING * scale, shade)
        boardBmp = bmp
        bmpLeft = cx - half
        bmpTop = cy - half
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        c.drawRect(0f, 0f, w, h, wall)
        c.drawRect(0f, 0f, w, h, spot)
        // 판 그림자
        fill.color = 0x66000000
        c.drawCircle(cx + 3f * d, cy + 5f * d, GameConfig.R_FRAME * scale, fill)
        val bmp = boardBmp
        if (bmp != null && !bmp.isRecycled) c.drawBitmap(bmp, bmpLeft, bmpTop, bmpPaint)

        if (trailShown) {
            // 어두운 테두리 + 밝은 점 (검정·크림 칸 어디서나 보이게)
            fill.color = 0x66000000
            for (k in 0 until GameConfig.TRAIL_POINTS) c.drawCircle(cx + trailX[k] * scale, cy + trailY[k] * scale, 2.3f * d, fill)
            fill.color = 0xB3FFE08A.toInt()
            for (k in 0 until GameConfig.TRAIL_POINTS) c.drawCircle(cx + trailX[k] * scale, cy + trailY[k] * scale, 1.3f * d, fill)
        }
        // 꽂힌 다트 (날아가는 것은 맨 위에 따로)
        for (i in 0 until SLOTS) if (state[i] == STUCK && now - flyStart[i] >= flyMs[i]) {
            DartArt.dart(c, art, path, cx + dx[i] * scale, cy + dy[i] * scale, dartLen, dAngle[i], dColor[i], 255, 1f)
        }
        if (aimShown) aim.draw(c, cx + aimX * scale, cy + aimY * scale)
        for (i in 0 until SLOTS) {
            if (state[i] == STUCK && now - flyStart[i] < flyMs[i]) drawFlying(c, i, w, h)
            else if (state[i] == RETRIEVING) drawRetrieving(c, i, w, h)
        }
        for (i in 0 until SLOTS) drawText(c, i, w)
    }

    /** 날아가는 다트: 아래 오른쪽(던지는 사람 쪽)에서 크게 → 꽂힐 자리로 작아지며 */
    private fun drawFlying(c: Canvas, i: Int, w: Float, h: Float) {
        val f = ((now - flyStart[i]).toFloat() / flyMs[i]).coerceIn(0f, 1f)
        val e = 1f - (1f - f) * (1f - f)
        val tx = cx + dx[i] * scale
        val ty = cy + dy[i] * scale
        val sx = w * 0.62f
        val sy = h + dartLen * 1.5f
        val x = sx + (tx - sx) * e
        val y = sy + (ty - sy) * e - sin(f * PI.toFloat()) * h * 0.12f
        val len = dartLen * (1f + (FLY_SCALE - 1f) * (1f - e))
        DartArt.dart(c, art, path, x, y, len, dAngle[i], dColor[i], 255, 0f)
    }

    /** 회수: 살짝 빠진 뒤 아래로 날아가며 사라짐 */
    private fun drawRetrieving(c: Canvas, i: Int, w: Float, h: Float) {
        val tx = cx + dx[i] * scale
        val ty = cy + dy[i] * scale
        val el = now - retStart[i]
        if (el < 0L) {
            DartArt.dart(c, art, path, tx, ty, dartLen, dAngle[i], dColor[i], 255, 1f)
            return
        }
        val f = (el.toFloat() / retMs[i]).coerceIn(0f, 1f)
        val e = f * f
        val ex = tx + w * 0.3f
        val ey = h + dartLen * 2f
        val x = tx + (ex - tx) * e
        val y = ty + (ey - ty) * e - sin(f * PI.toFloat()) * dartLen * 0.8f
        val len = dartLen * (1f + 0.7f * f)
        val alpha = if (f < 0.5f) 255 else (255 * (1f - f) * 2f).toInt()
        DartArt.dart(c, art, path, x, y, len, dAngle[i] + 30f * f, dColor[i], alpha, 1f - f)
    }

    /** 점수 글자: 꽂힌 자리 위에서 떠오르며 사라짐 */
    private fun drawText(c: Canvas, i: Int, w: Float) {
        val s = text[i] ?: return
        val el = now - textStart[i]
        if (el < 0L || el >= textMs[i]) return
        val f = el.toFloat() / textMs[i]
        val alpha = if (f < 0.65f) 255 else (255 * (1f - f) / 0.35f).toInt().coerceIn(0, 255)
        val pop = if (f < 0.12f) 0.7f + 0.3f * (f / 0.12f) else 1f
        hitPaint.textSize = 17f * d * pop
        hitStroke.textSize = hitPaint.textSize
        val half = hitPaint.measureText(s) / 2f + 4f * d
        val x = (cx + dx[i] * scale).coerceIn(half, w - half)
        var y = cy + dy[i] * scale - dartLen * 1.15f - f * 18f * d
        if (y < hitPaint.textSize + 4f * d) y = cy + dy[i] * scale + dartLen * 0.9f + hitPaint.textSize - f * 10f * d
        hitStroke.alpha = alpha * 230 / 255
        c.drawText(s, x, y, hitStroke)
        hitPaint.color = textColor[i]
        hitPaint.alpha = alpha
        c.drawText(s, x, y, hitPaint)
    }

    /** 비트맵 정리 (Activity onGameDestroy) */
    fun release() {
        boardBmp?.recycle()
        boardBmp = null
    }

    companion object {
        const val SLOTS = GameConfig.DARTS_PER_TURN
        private const val NONE = 0
        private const val STUCK = 1
        private const val RETRIEVING = 2
        /** 다트 그림 길이 (mm 환산 — 앞에서 비스듬히 보이는 길이) */
        private const val DART_MM = 54f
        /** 날아오기 시작할 때 크기 배율 */
        private const val FLY_SCALE = 3.2f
    }
}
