package com.siwoo.party.games.marbles.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
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
import com.siwoo.party.games.marbles.GameConfig
import com.siwoo.party.games.marbles.MarblesGame
import com.siwoo.party.games.marbles.Marble
import com.siwoo.party.games.marbles.Phase
import kotlin.math.ceil
import kotlin.random.Random

/**
 * 흙 마당 (위에서 본 모습): 땅에 그은 큰 링 + 가운데 유리구슬 + 내 구슬 + 새총 조준.
 * - 흙·알갱이·자갈·풀·링은 크기가 바뀔 때 RGB_565 비트맵 하나로 그려 두고(release 에서 recycle) 매 프레임 붙이기만
 * - 구슬 몸통은 MarbleSkins 의 셰이더를 translate 해서 재사용 (그리기 중 할당 없음)
 * - 터치: 내 구슬 근처를 누르면 새총(공통 SlingshotTouch — 뒤로 끌었다 놓기), 다른 곳을 누르면 그 자리(링 밖으로 당김)에 놓기·끌어 옮기기
 * 움직임은 화면(Activity)의 프레임 루프가 game.advance 후 invalidate — 이 View 는 스스로 반복해서 그리지 않는다.
 */
class FieldView(context: Context, private val skins: MarbleSkins) : View(context) {

    interface Listener {
        /** 지금 놓거나 쏠 수 있는지 (내 차례·연출 중 아님·일시정지 아님) */
        fun canTouch(): Boolean
        /** 마당 단위 (ux, uy) 에 내 구슬 놓기 (first = 손가락을 처음 댄 순간) */
        fun onPlace(ux: Float, uy: Float, first: Boolean)
        /** 새총 놓음: 방향(단위 벡터)·힘 0..1 */
        fun onShoot(dirX: Float, dirY: Float, power: Float)
        /** 새총을 너무 짧게 끌고 놓음 */
        fun onAimCancel()
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: MarblesGame? = null

    // ── 배치 (onSizeChanged) ──
    /** 마당 단위 1 = unitPx 픽셀, 마당 왼쪽 위 = (ox, oy) */
    var unitPx = 1f
        private set
    private var ox = 0f
    private var oy = 0f
    private var groundBmp: Bitmap? = null
    private var turnColor = Theme.GOLD

    // ── 새총 ──
    val sling = Slingshot(maxDistance = 100f, minDistance = 15f)
    private val slingTouch = SlingshotTouch(sling)
    private val slingDrawer = SlingshotDrawer(context)

    /** 손가락이 하는 일 */
    private var mode = MODE_NONE

    /** 지금 새총을 끄는 중인지 */
    val aiming: Boolean get() = mode == MODE_SLING && slingTouch.dragging

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val aux = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFF4B0 }
    private val placePath = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val rect = RectF()
    private val pt = PointF()

    init {
        isClickable = true
        slingTouch.listener = object : SlingshotTouch.Listener {
            override fun onAimMove(s: Slingshot) {
                invalidate()
            }

            override fun onShoot(s: Slingshot) {
                mode = MODE_NONE
                invalidate()
                listener?.onShoot(s.dirX, s.dirY, s.power)
            }

            override fun onAimCancel(s: Slingshot) {
                mode = MODE_NONE
                invalidate()
                listener?.onAimCancel()
            }
        }
    }

    /** 게임 연결 */
    fun bind(game: MarblesGame) {
        this.game = game
        invalidate()
    }

    /** 차례인 사람 색 (마당 테두리) */
    fun setTurnColor(color: Int) {
        if (turnColor == color) return
        turnColor = color
        invalidate()
    }

    /** 마당 단위 (x, y) → 이 View 좌표 */
    fun toScreen(x: Float, y: Float, out: PointF) {
        out.set(ox + x * unitPx, oy + y * unitPx)
    }

    /** 이 View 좌표 → 마당 단위 */
    fun toFieldX(px: Float): Float = (px - ox) / unitPx
    fun toFieldY(py: Float): Float = (py - oy) / unitPx

    /** 새총·끌어 옮기기 취소 (일시정지 등) */
    fun cancelAim() {
        if (mode == MODE_SLING) slingTouch.abort()
        mode = MODE_NONE
        invalidate()
    }

    // ── 크기·비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val margin = 6f * d
        unitPx = minOf((w - 2f * margin) / GameConfig.FIELD_W, (h - 2f * margin) / GameConfig.FIELD_H)
        ox = (w - GameConfig.FIELD_W * unitPx) / 2f
        oy = (h - GameConfig.FIELD_H * unitPx) / 2f
        stroke.strokeWidth = 3f * d
        dash.strokeWidth = 2f * d
        dash.pathEffect = DashPathEffect(floatArrayOf(7f * d, 6f * d), 0f)
        sling.maxDistance = GameConfig.MAX_DRAG * unitPx
        sling.minDistance = GameConfig.MIN_DRAG * unitPx
        slingDrawer.guideLength = 11f * unitPx
        slingDrawer.guideStartGap = GameConfig.SHOOTER_R * unitPx * 1.3f
        slingDrawer.ringRadius = GameConfig.SHOOTER_R * unitPx * 1.55f
        skins.build(GameConfig.TARGET_R * unitPx)
        // 놓을 수 있는 곳 = 마당 − 링(+여유) 원 (짝홀 채우기)
        placePath.reset()
        placePath.addRect(ox, oy, ox + GameConfig.FIELD_W * unitPx, oy + GameConfig.FIELD_H * unitPx, Path.Direction.CW)
        placePath.addCircle(ox + GameConfig.RING_X * unitPx, oy + GameConfig.RING_Y * unitPx, GameConfig.PLACE_MIN_DIST * unitPx, Path.Direction.CW)
        buildGround()
    }

    /** 흙 마당 + 알갱이 + 자갈 + 풀 + 링을 비트맵 하나에 */
    private fun buildGround() {
        release()
        val k = unitPx
        val bw = ceil(GameConfig.FIELD_W * k).toInt().coerceAtLeast(1)
        val bh = ceil(GameConfig.FIELD_H * k).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // 흙: 위에서 비치는 빛 (가운데 밝게)
        p.shader = LinearGradient(0f, 0f, 0f, bh.toFloat(), 0xFFD2A46B.toInt(), 0xFFB98652.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
        p.shader = RadialGradient(
            bw / 2f, bh / 2f, bw * 0.62f,
            intArrayOf(0x26FFF4D8, 0x00FFF4D8, 0x40553311),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
        p.shader = null
        // 흙 알갱이 (고정 난수 → 늘 같은 무늬)
        val rnd = Random(23)
        repeat((bw * bh / (14f * d * d)).toInt().coerceAtMost(4000)) {
            val x = rnd.nextFloat() * bw
            val y = rnd.nextFloat() * bh
            p.color = if (rnd.nextInt(3) == 0) 0x30FFF0D0 else 0x2E5A3510
            c.drawCircle(x, y, (0.4f + rnd.nextFloat() * 1.1f) * d, p)
        }
        // 자갈
        repeat(26) {
            val x = rnd.nextFloat() * bw
            val y = rnd.nextFloat() * bh
            val cx = x / k - GameConfig.RING_X
            val cy = y / k - GameConfig.RING_Y
            if (cx * cx + cy * cy < (GameConfig.RING_R + 1f) * (GameConfig.RING_R + 1f)) return@repeat
            val r = (0.12f + rnd.nextFloat() * 0.22f) * k
            p.color = 0x50000000
            c.drawOval(x - r + r * 0.2f, y - r * 0.7f + r * 0.3f, x + r + r * 0.2f, y + r * 0.7f + r * 0.3f, p)
            p.color = if (rnd.nextBoolean()) 0xFF9C9488.toInt() else 0xFF857B6E.toInt()
            c.drawOval(x - r, y - r * 0.7f, x + r, y + r * 0.7f, p)
            p.color = 0x40FFFFFF
            c.drawOval(x - r * 0.6f, y - r * 0.55f, x, y - r * 0.1f, p)
        }
        // 가장자리 풀 몇 포기
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 1.6f * d
        repeat(18) {
            val edge = rnd.nextInt(4)
            val t = rnd.nextFloat()
            val x = when (edge) { 0 -> t * bw; 1 -> bw - rnd.nextFloat() * 0.6f * k; 2 -> t * bw; else -> rnd.nextFloat() * 0.6f * k }
            val y = when (edge) { 0 -> rnd.nextFloat() * 0.6f * k; 1 -> t * bh; 2 -> bh - rnd.nextFloat() * 0.6f * k; else -> t * bh }
            for (b in -2..2) {
                p.color = if (b % 2 == 0) 0xCC4E9A2E.toInt() else 0xCC6DB540.toInt()
                c.drawLine(x, y, x + b * 0.13f * k, y - (0.35f + rnd.nextFloat() * 0.25f) * k, p)
            }
        }
        p.style = Paint.Style.FILL
        // 링 안쪽은 다져진 흙 (조금 밝게)
        val rx = GameConfig.RING_X * k
        val ry = GameConfig.RING_Y * k
        p.color = 0x1CFFF4E0
        c.drawCircle(rx, ry, GameConfig.RING_R * k, p)
        MarbleArt.ring(c, p, rx, ry, GameConfig.RING_R * k, 0.24f * k)
        // 가운데 작은 십자 표시 (처음 구슬 놓던 자리)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        p.color = 0x30000000
        c.drawLine(rx - 0.5f * k, ry, rx + 0.5f * k, ry, p)
        c.drawLine(rx, ry - 0.5f * k, rx, ry + 0.5f * k, p)
        // 가장자리 그늘
        p.strokeWidth = 4f * d
        p.color = 0x40000000
        c.drawRect(2f * d, 2f * d, bw - 2f * d, bh - 2f * d, p)
        groundBmp = bmp
    }

    /** 마당 비트맵 해제 (onGameDestroy·떼어질 때) */
    fun release() {
        groundBmp?.recycle()
        groundBmp = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (groundBmp == null && width > 0) buildGround()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ── 터치 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = game ?: return false
        if (mode == MODE_SLING) {
            slingTouch.onTouchEvent(e)
            return true
        }
        if (mode == MODE_PLACE) {
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (listener?.canTouch() == true) listener?.onPlace(toFieldX(e.x), toFieldY(e.y), false)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = MODE_NONE
            }
            return true
        }
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return false
        if (listener?.canTouch() != true || !g.canAct) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        if (g.shooterPlaced && nearShooter(g, e.x, e.y)) {
            // 내 구슬에서 끌기 시작 → 새총 (끈 거리는 구슬 중심에서 잰다)
            toScreen(g.shooter.x, g.shooter.y, pt)
            slingTouch.setAnchor(pt.x, pt.y)
            mode = MODE_SLING
            slingTouch.onTouchEvent(e)
            invalidate()
            return true
        }
        mode = MODE_PLACE
        listener?.onPlace(toFieldX(e.x), toFieldY(e.y), true)
        return true
    }

    private fun nearShooter(g: MarblesGame, x: Float, y: Float): Boolean {
        toScreen(g.shooter.x, g.shooter.y, pt)
        val reach = maxOf(GameConfig.SHOOTER_R * unitPx * 1.7f, 30f * d)
        val dx = x - pt.x
        val dy = y - pt.y
        return dx * dx + dy * dy <= reach * reach
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val k = unitPx
        val fw = GameConfig.FIELD_W * k
        val fh = GameConfig.FIELD_H * k
        // 마당 그림자 + 흙
        fill.color = 0x66000000
        rect.set(ox + 3f * d, oy + 5f * d, ox + fw + 3f * d, oy + fh + 5f * d)
        c.drawRoundRect(rect, 6f * d, 6f * d, fill)
        val bmp = groundBmp
        if (bmp != null && !bmp.isRecycled) c.drawBitmap(bmp, ox, oy, bmpPaint)

        // 차례인 사람 색 테두리
        val input = listener?.canTouch() == true && g.canAct
        stroke.color = turnColor
        stroke.alpha = if (input) 255 else 140
        rect.set(ox - 2f * d, oy - 2f * d, ox + fw + 2f * d, oy + fh + 2f * d)
        c.drawRoundRect(rect, 5f * d, 5f * d, stroke)

        val rcx = ox + GameConfig.RING_X * k
        val rcy = oy + GameConfig.RING_Y * k
        // 놓을 곳 안내: 아직 안 놓았으면 링 밖을 은은하게 + 점선 (놓은 뒤엔 점선만)
        if (input) {
            if (!g.shooterPlaced) c.drawPath(placePath, tint)
            dash.color = if (g.shooterPlaced) 0x66FFFFFF else 0xCCFFF2A0.toInt()
            c.drawCircle(rcx, rcy, GameConfig.PLACE_MIN_DIST * k, dash)
        }

        // 가운데 구슬 (굴러가는 중 링 밖에 있는 구슬은 금빛 고리 — 멈추면 획득)
        val moving = g.phase == Phase.MOVING
        val rt = GameConfig.TARGET_R * k
        for (t in g.targets) {
            if (!t.inPlay) continue
            if (moving && MarblesGame.isOutsideRing(t.x, t.y)) {
                stroke.color = Theme.GOLD
                stroke.alpha = 230
                c.drawCircle(ox + t.x * k, oy + t.y * k, rt * 1.35f, stroke)
            }
            drawMarble(c, t, rt)
        }
        // 내 구슬
        val s = g.shooter
        if (s.inPlay) {
            val sx = ox + s.x * k
            val sy = oy + s.y * k
            val rs = GameConfig.SHOOTER_R * k
            if (g.phase == Phase.AIM && input && !aiming) {
                // "여기를 끌어요" 고리
                stroke.color = 0xE6FFFFFF.toInt()
                stroke.alpha = 230
                c.drawCircle(sx, sy, rs * 1.5f, stroke)
            }
            drawShooter(c, s, sx, sy, rs)
            if (aiming) slingDrawer.draw(c, sling, sx, sy)
        }
    }

    private fun drawMarble(c: Canvas, m: Marble, r: Float) {
        val color = MarbleArt.COLORS[m.color % MarbleArt.COLORS.size]
        val skin = if (skins.baseR > 0f) skins.target[m.color % skins.target.size] else null
        MarbleArt.draw(c, ox + m.x * unitPx, oy + m.y * unitPx, r, color, aux, skin, skins.baseR, rollAngle(m), 255, true, false)
    }

    private fun drawShooter(c: Canvas, m: Marble, x: Float, y: Float, r: Float) {
        val g = game ?: return
        val p = if (g.phase == Phase.MOVING) g.shotBy else g.current
        val skin = if (skins.baseR > 0f) skins.player[p] else null
        MarbleArt.draw(c, x, y, r, skins.playerColor(p), aux, skin, skins.baseR, rollAngle(m), 255, true, true)
    }

    /** 구른 거리로 소용돌이 각도 (도) — 따로 상태를 두지 않고 위치에서 바로 */
    private fun rollAngle(m: Marble): Float = (m.x * 0.83f + m.y * 0.61f) / m.radius * DEG

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_PLACE = 1
        private const val MODE_SLING = 2
        private const val DEG = 57.29578f
    }
}
