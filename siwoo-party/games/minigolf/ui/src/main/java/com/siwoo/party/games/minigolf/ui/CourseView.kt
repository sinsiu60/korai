package com.siwoo.party.games.minigolf.ui

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
import com.siwoo.party.common.aim.Slingshot
import com.siwoo.party.common.aim.SlingshotDrawer
import com.siwoo.party.common.aim.SlingshotTouch
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.minigolf.AreaKind
import com.siwoo.party.games.minigolf.GameConfig
import com.siwoo.party.games.minigolf.HoleDef
import com.siwoo.party.games.minigolf.MinigolfGame
import kotlin.random.Random

/**
 * 위에서 본 코스 (잔디·나무 테두리·모래·물·경사 꺾쇠·블록·컵과 깃발) + 움직이는 블록 + 공 + 지나온 자국 + 새총 조준.
 * - 고정된 코스 그림은 홀이 바뀌거나 크기가 바뀔 때 비트맵 하나로 그려 두고(release 에서 recycle) 매 프레임 붙이기만 한다.
 * - 조준: 코스 아무 데나 눌러 뒤로 끌면(공통 aim 의 SlingshotTouch) 공에서 쏘는 방향 점선·힘 고리 → 놓으면 listener.onShoot.
 * - 움직임은 화면(Activity)의 프레임 루프가 game.advance 후 invalidate — 이 View 는 스스로 반복해서 그리지 않는다. onDraw 할당 없음.
 */
class CourseView(context: Context) : View(context), SlingshotTouch.Listener {

    fun interface Listener {
        /** 치기: 방향 (단위 벡터, 코스 좌표 = 화면 방향과 같음), 힘 0..1 */
        fun onShoot(dirX: Float, dirY: Float, power: Float)
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: MinigolfGame? = null
    private var hole: HoleDef? = null

    // ── 배치 ──
    /** 코스 단위 1 = unitPx 픽셀, 코스 상자 왼쪽 위 = (ox, oy) */
    var unitPx = 1f
        private set
    private var ox = 0f
    private var oy = 0f
    private var courseBmp: Bitmap? = null

    // ── 조준 ──
    val sling = Slingshot(GameConfig.MAX_DRAG_DP * d, GameConfig.MIN_DRAG_DP * d)
    private val touch = SlingshotTouch(sling).also {
        it.listener = this
        it.enabled = false
    }
    private val slingDrawer = SlingshotDrawer(context)

    /** 조준을 받을지 (끄면 끄는 중이던 조준도 취소) */
    var aimEnabled: Boolean
        get() = touch.enabled
        set(v) {
            touch.enabled = v
            if (!v) touch.abort()
            invalidate()
        }

    /** 지금 끌고 있는지 */
    val aiming: Boolean get() = touch.dragging

    /** 지금 치는 사람 색 (공 테두리) */
    var ballColor = Theme.GOLD
        set(v) { field = v; invalidate() }

    /** 게임 시계 (프레임마다 Activity 가 넣음) — 컵에 빠지는 연출용 */
    var now = 0L

    // ── 컵에 빠지는 연출 ──
    private var sinkStart = -1L
    private var sinkMs = 1L
    private var sinkFromX = 0f
    private var sinkFromY = 0f
    /** 공이 컵 안에 있어 그리지 않음 */
    private var ballInCup = false

    // ── 지나온 자국 (고리 버퍼) ──
    private val trail = FloatArray(TRAIL * 2)
    private var trailCount = 0
    private var trailHead = 0

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }

    init {
        isClickable = true
    }

    /** 게임 연결 + 지금 홀 코스 그림 다시 */
    fun showHole(g: MinigolfGame) {
        game = g
        hole = g.hole
        clearTrail()
        sinkStart = -1L
        ballInCup = false
        if (width > 0 && height > 0) {
            layoutCourse()
            buildCourse()
        }
        invalidate()
    }

    /** 새 사람 차례 (공이 티로) */
    fun newTurn() {
        clearTrail()
        sinkStart = -1L
        ballInCup = false
        invalidate()
    }

    fun clearTrail() {
        trailCount = 0
        trailHead = 0
    }

    /** 코스 (x, y) → 이 View 좌표 */
    fun toScreen(x: Float, y: Float, out: PointF) {
        out.set(ox + x * unitPx, oy + y * unitPx)
    }

    /** 공 중심 → 이 View 좌표 */
    fun ballCenter(out: PointF) {
        val b = game?.sim?.ball ?: return
        toScreen(b.x, b.y, out)
    }

    /** 컵에 빠지는 연출 시작 (공은 컵 쪽으로 미끄러지며 작아짐) */
    fun startSink(ms: Long) {
        val b = game?.sim?.ball ?: return
        sinkFromX = b.x
        sinkFromY = b.y
        sinkStart = now
        sinkMs = maxOf(1L, ms)
        invalidate()
    }

    /** 연출 중인지 (프레임 루프 유지 판단) */
    fun busy(): Boolean = sinkStart >= 0L && now - sinkStart < sinkMs

    /** 프레임마다: 굴러가는 공 자리를 자국에 */
    fun recordTrail() {
        val b = game?.sim?.ball ?: return
        if (!b.active) return
        if (trailCount > 0) {
            val last = ((trailHead - 1 + TRAIL) % TRAIL) * 2
            val dx = trail[last] - b.x
            val dy = trail[last + 1] - b.y
            if (dx * dx + dy * dy < TRAIL_GAP * TRAIL_GAP) return
        }
        trail[trailHead * 2] = b.x
        trail[trailHead * 2 + 1] = b.y
        trailHead = (trailHead + 1) % TRAIL
        if (trailCount < TRAIL) trailCount++
    }

    // ── 크기·비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        layoutCourse()
        if (hole != null) buildCourse()
    }

    /** 지금 홀 테두리(+나무 두께)가 화면에 꽉 차게 배율·위치를 정하고 크기에 따른 도구를 맞춘다 */
    private fun layoutCourse() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        var minX = 0f
        var minY = 0f
        var maxX = GameConfig.COURSE_W
        var maxY = GameConfig.COURSE_H
        val hd = hole
        if (hd != null) {
            minX = Float.MAX_VALUE; minY = Float.MAX_VALUE; maxX = -Float.MAX_VALUE; maxY = -Float.MAX_VALUE
            for (i in 0 until hd.corners) {
                minX = minOf(minX, hd.outline[2 * i]); maxX = maxOf(maxX, hd.outline[2 * i])
                minY = minOf(minY, hd.outline[2 * i + 1]); maxY = maxOf(maxY, hd.outline[2 * i + 1])
            }
            minX -= FIT_MARGIN; minY -= FIT_MARGIN; maxX += FIT_MARGIN; maxY += FIT_MARGIN
        }
        val margin = 4f * d
        unitPx = minOf((w - 2f * margin) / (maxX - minX), (h - 2f * margin) / (maxY - minY))
        ox = (w - (maxX - minX) * unitPx) / 2f - minX * unitPx
        oy = (h - (maxY - minY) * unitPx) / 2f - minY * unitPx
        val r = GameConfig.BALL_R * unitPx
        ballPaint.shader = RadialGradient(
            -r * 0.35f, -r * 0.4f, r * 1.5f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFF2F4F7.toInt(), 0xFFB9C1CC.toInt()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        ring.strokeWidth = 1.6f * d
        slingDrawer.guideLength = 8f * unitPx
        slingDrawer.guideStartGap = r * 2.2f
        slingDrawer.ringRadius = r * 2.6f
        slingDrawer.dotRadius = 2.8f * d
    }

    /** 고정된 코스 그림 (배경 풀·테두리·잔디 줄무늬·구역·블록·티·컵·깃발) */
    private fun buildCourse() {
        val h = hole ?: return
        release()
        val w = width
        val ht = height
        if (w <= 0 || ht <= 0) return
        val bmp = Bitmap.createBitmap(w, ht, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        val k = unitPx
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // 바깥 풀 (거친 잔디 + 풀 무늬)
        c.drawColor(GolfArt.ROUGH)
        val rnd = Random(7)
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 1.4f * d
        for (i in 0 until 420) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * ht
            p.color = if (rnd.nextBoolean()) 0x2A000000 else 0x1E9CFF9C
            c.drawLine(x, y, x + (rnd.nextFloat() - 0.5f) * 3f * d, y - (2f + rnd.nextFloat() * 3f) * d, p)
        }
        // 테두리 다각형
        val outline = Path()
        val o = h.outline
        for (i in 0 until h.corners) {
            val x = ox + o[2 * i] * k
            val y = oy + o[2 * i + 1] * k
            if (i == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
        }
        outline.close()
        // 나무 테두리 (바깥쪽 반만 보이게 굵게 그린 뒤 잔디로 덮음)
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.MITER
        val rail = RAIL_U * k
        p.color = 0x66000000
        p.strokeWidth = rail * 2f + 3f * d
        c.save()
        c.translate(1.5f * d, 2.5f * d)
        c.drawPath(outline, p)
        c.restore()
        p.color = GolfArt.RAIL_DARK
        p.strokeWidth = rail * 2f
        c.drawPath(outline, p)
        p.color = GolfArt.RAIL
        p.strokeWidth = rail * 2f - 3f * d
        c.drawPath(outline, p)
        // 잔디 + 깎은 줄무늬
        p.style = Paint.Style.FILL
        p.color = 0xFF000000.toInt()
        p.shader = LinearGradient(0f, oy, 0f, oy + GameConfig.COURSE_H * k, GolfArt.GRASS, GolfArt.GRASS_DARK, Shader.TileMode.CLAMP)
        c.drawPath(outline, p)
        p.shader = null
        c.save()
        c.clipPath(outline)
        p.color = 0x14FFFFFF
        var sx = 0f
        while (sx < GameConfig.COURSE_W) {
            c.drawRect(ox + sx * k, oy, ox + (sx + 1.5f) * k, oy + GameConfig.COURSE_H * k, p)
            sx += 3f
        }
        // 구역
        val box = RectF()
        for (a in h.areas) {
            box.set(ox + a.box.left * k, oy + a.box.top * k, ox + a.box.right * k, oy + a.box.bottom * k)
            when (a.kind) {
                AreaKind.SLOPE -> drawSlope(c, p, box, a.ax, a.ay, k)
                AreaKind.SAND -> drawSand(c, p, box, a.circle, k, rnd)
                AreaKind.WATER -> drawWater(c, p, box, a.circle, k)
            }
        }
        // 테두리 안쪽 그림자
        p.style = Paint.Style.STROKE
        p.color = 0x40000000
        p.strokeWidth = 0.45f * k
        c.drawPath(outline, p)
        c.restore()
        // 블록
        for (b in h.blocks) GolfArt.block(c, p, ox + b.left * k, oy + b.top * k, ox + b.right * k, oy + b.bottom * k, k)
        // 티 (출발 매트)
        p.style = Paint.Style.FILL
        p.color = 0x33FFFFFF
        val tx = ox + h.teeX * k
        val ty = oy + h.teeY * k
        box.set(tx - 0.85f * k, ty - 0.85f * k, tx + 0.85f * k, ty + 0.85f * k)
        c.drawRoundRect(box, 0.25f * k, 0.25f * k, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        p.color = 0x88FFFFFF.toInt()
        c.drawRoundRect(box, 0.25f * k, 0.25f * k, p)
        // 컵 + 깃발
        val cx = ox + h.cupX * k
        val cy = oy + h.cupY * k
        GolfArt.cup(c, p, cx, cy, GameConfig.CUP_DRAW_R * k)
        GolfArt.flag(c, p, cx, cy, 2.6f * k)
        courseBmp = bmp
    }

    private fun drawSlope(c: Canvas, p: Paint, box: RectF, ax: Float, ay: Float, k: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFF000000.toInt()
        // 기울어진 쪽이 어둡게
        val x0 = box.centerX() - ax / 10f * box.width() / 2f
        val y0 = box.centerY() - ay / 10f * box.height() / 2f
        val x1 = box.centerX() + ax / 10f * box.width() / 2f
        val y1 = box.centerY() + ay / 10f * box.height() / 2f
        p.shader = LinearGradient(x0, y0, x1, y1, 0x22FFF6C0, 0x33002A00, Shader.TileMode.CLAMP)
        c.drawRect(box, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.14f * k
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0x66FFFFFF
        val step = 2.6f * k
        var y = box.top + step / 2f
        var row = 0
        while (y < box.bottom) {
            var x = box.left + step / 2f + if (row % 2 == 1) step / 2f else 0f
            while (x < box.right) {
                GolfArt.chevron(c, p, x, y, ax, ay, 0.9f * k)
                x += step
            }
            y += step * 0.8f
            row++
        }
        p.strokeWidth = 1.2f * d
        p.color = 0x44FFFFFF
        c.drawRect(box, p)
    }

    private fun drawSand(c: Canvas, p: Paint, box: RectF, circle: Boolean, k: Float, rnd: Random) {
        p.style = Paint.Style.FILL
        p.color = GolfArt.SAND
        if (circle) c.drawOval(box, p) else c.drawRoundRect(box, 0.2f * k, 0.2f * k, p)
        c.save()
        if (circle) {
            val path = Path()
            path.addOval(box, Path.Direction.CW)
            c.clipPath(path)
        } else {
            c.clipRect(box)
        }
        p.color = 0x22000000
        val n = (box.width() * box.height() / (k * k) * 5f).toInt()
        for (i in 0 until n) c.drawCircle(box.left + rnd.nextFloat() * box.width(), box.top + rnd.nextFloat() * box.height(), 0.06f * k, p)
        p.color = 0x55FFFFFF
        for (i in 0 until n / 2) c.drawCircle(box.left + rnd.nextFloat() * box.width(), box.top + rnd.nextFloat() * box.height(), 0.05f * k, p)
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.12f * k
        p.color = GolfArt.SAND_DARK
        if (circle) c.drawOval(box, p) else c.drawRoundRect(box, 0.2f * k, 0.2f * k, p)
    }

    private fun drawWater(c: Canvas, p: Paint, box: RectF, circle: Boolean, k: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFF000000.toInt() // 셰이더에 앞 색의 투명도가 섞이지 않게
        p.shader = LinearGradient(box.left, box.top, box.right, box.bottom, GolfArt.WATER, GolfArt.WATER_DARK, Shader.TileMode.CLAMP)
        if (circle) c.drawOval(box, p) else c.drawRoundRect(box, 0.2f * k, 0.2f * k, p)
        p.shader = null
        // 물결
        c.save()
        if (circle) {
            val path = Path()
            path.addOval(box, Path.Direction.CW)
            c.clipPath(path)
        } else {
            c.clipRect(box)
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 0.12f * k
        p.color = 0x66FFFFFF
        val arc = RectF()
        var y = box.top + 0.9f * k
        var row = 0
        while (y < box.bottom) {
            var x = box.left + (if (row % 2 == 0) 0.4f else 1.4f) * k
            while (x < box.right) {
                arc.set(x, y - 0.3f * k, x + 0.9f * k, y + 0.3f * k)
                c.drawArc(arc, 200f, 140f, false, p)
                x += 2f * k
            }
            y += 1.2f * k
            row++
        }
        c.restore()
        p.strokeWidth = 0.14f * k
        p.color = GolfArt.WATER_LIGHT
        if (circle) c.drawOval(box, p) else c.drawRoundRect(box, 0.2f * k, 0.2f * k, p)
        p.style = Paint.Style.FILL
    }

    /** 코스 비트맵 해제 (onGameDestroy·떼어질 때) */
    fun release() {
        courseBmp?.recycle()
        courseBmp = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (courseBmp == null && width > 0 && hole != null) buildCourse()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ── 터치 (공통 새총) ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (touch.onTouchEvent(e)) {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        return false
    }

    override fun onAimStart(s: Slingshot) = invalidate()
    override fun onAimMove(s: Slingshot) = invalidate()
    override fun onAimCancel(s: Slingshot) = invalidate()

    override fun onShoot(s: Slingshot) {
        invalidate()
        listener?.onShoot(s.dirX, s.dirY, s.power)
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val bmp = courseBmp
        if (bmp != null && !bmp.isRecycled) c.drawBitmap(bmp, 0f, 0f, bmpPaint) else c.drawColor(GolfArt.ROUGH)
        val g = game ?: return
        val h = hole ?: return
        val k = unitPx
        // 움직이는 블록
        val sim = g.sim
        for (i in h.sliders.indices) {
            val b = h.sliders[i].box
            val dx = sim.sliderOffsetX(i)
            val dy = sim.sliderOffsetY(i)
            GolfArt.slider(c, fill, ox + (b.left + dx) * k, oy + (b.top + dy) * k, ox + (b.right + dx) * k, oy + (b.bottom + dy) * k, 0.7f * k)
        }
        // 지나온 자국
        if (trailCount > 1) {
            fill.style = Paint.Style.FILL
            for (j in 0 until trailCount) {
                val idx = ((trailHead - trailCount + j + TRAIL) % TRAIL) * 2
                val f = (j + 1).toFloat() / trailCount
                fill.color = 0xFFFFFFFF.toInt()
                fill.alpha = (110 * f).toInt()
                c.drawCircle(ox + trail[idx] * k, oy + trail[idx + 1] * k, GameConfig.BALL_R * k * (0.25f + 0.25f * f), fill)
            }
        }
        drawBall(c, g)
    }

    private fun drawBall(c: Canvas, g: MinigolfGame) {
        val k = unitPx
        val b = g.sim.ball
        val r = GameConfig.BALL_R * k
        var x = b.x
        var y = b.y
        var scale = 1f
        if (sinkStart >= 0L) {
            val f = ((now - sinkStart).toFloat() / sinkMs).coerceIn(0f, 1f)
            if (f >= 1f) {
                ballInCup = true
                return
            }
            val h = hole ?: return
            x = sinkFromX + (h.cupX - sinkFromX) * f
            y = sinkFromY + (h.cupY - sinkFromY) * f
            scale = 1f - 0.65f * f
        } else if (!b.active || ballInCup) {
            return
        }
        val sx = ox + x * k
        val sy = oy + y * k
        val aimingNow = touch.dragging
        // 조준 대기: 공 둘레 고리
        if (touch.enabled && !aimingNow) {
            ring.color = 0xAAFFFFFF.toInt()
            c.drawCircle(sx, sy, r * 2.1f, ring)
        }
        if (aimingNow) slingDrawer.draw(c, sling, sx, sy)
        val rr = r * scale
        if (scale >= 1f) c.drawCircle(sx + rr * 0.2f, sy + rr * 0.3f, rr, shadow)
        c.save()
        c.translate(sx, sy)
        if (scale < 1f) c.scale(scale, scale)
        c.drawCircle(0f, 0f, r, ballPaint)
        ring.color = ballColor
        c.drawCircle(0f, 0f, r - ring.strokeWidth / 2f, ring)
        c.restore()
    }

    companion object {
        /** 테두리 나무 두께 (코스 단위) */
        private const val RAIL_U = 0.55f
        /** 테두리 바깥으로 남기는 여백 (나무 두께 + 그림자, 코스 단위) */
        private const val FIT_MARGIN = 0.9f
        private const val TRAIL = 48
        /** 자국 점 간격 (코스 단위) */
        private const val TRAIL_GAP = 0.45f
    }
}
