package com.siwoo.party.games.bowling.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.swipe.SwipeConfig
import com.siwoo.party.common.swipe.SwipeReject
import com.siwoo.party.common.swipe.SwipeResult
import com.siwoo.party.common.swipe.SwipeTracker
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bowling.Aim
import com.siwoo.party.games.bowling.BowlingGame
import com.siwoo.party.games.bowling.GameConfig
import com.siwoo.party.games.bowling.Lane
import com.siwoo.party.games.bowling.Phase
import com.siwoo.party.games.bowling.PinState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 위에서 본 볼링 레인 (가로: 왼쪽 어프로치·파울 라인 → 오른쪽 핀 덱·핏). 좌표는 Lane 의 레인 단위를 그대로 그린다.
 * - 나무 보드·결·표적 점·화살표·거터·핀 자리·핏은 크기가 바뀔 때 비트맵으로 한 번 그려 두고(release 에서 recycle) 붙이기만
 * - 조작: 어프로치(파울 라인 왼쪽)에서 공을 누르면 공이 손가락을 따라오고(위아래 = 던지는 자리),
 *   오른쪽으로 쓱 밀고 떼면 공통 스와이프(SwipeTracker → 방향·세기·휘어짐)를 listener.onSwipe 로
 * - 미는 동안: 예상 방향 점선(앞쪽 일부만) + 휘는 방향 표시
 * 움직임은 화면(Activity)의 프레임 루프가 game.advance 후 invalidate 한다 — 이 View 는 스스로 반복해서 그리지 않는다.
 */
class LaneView(context: Context) : View(context) {

    interface Listener {
        /** 지금 공을 잡아도 되는지 */
        fun canAim(): Boolean
        /** 스와이프 성공: 던질 레인 높이·벗어난 각도·세기·휘어짐 */
        fun onSwipe(startY: Float, deviationDeg: Float, power: Float, spin: Float)
        /** 스와이프 실패 (짧음·느림·뒤로 등) */
        fun onBadSwipe(reject: SwipeReject)
        /** 공을 잡음 */
        fun onGrab() {}
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: BowlingGame? = null
    private var ballColor = Theme.RED
    private var ballDark = Theme.RED_DARK

    // ── 배치: 레인 단위 1 = unitPx, 레인 (X0, Y0) = (ox, oy) ──
    var unitPx = 1f
        private set
    var ox = 0f
        private set
    var oy = 0f
        private set
    private var laneBmp: Bitmap? = null

    // ── 스와이프 ──
    val tracker = SwipeTracker()
    val swipeConfig = SwipeConfig(
        minLength = GameConfig.SWIPE_MIN_LEN,
        minSpeed = GameConfig.SWIPE_MIN_SPEED,
        fullSpeed = GameConfig.SWIPE_FULL_SPEED,
        maxDurationMs = GameConfig.SWIPE_MAX_MS,
        forwardDeg = 0f,
        maxDeviationDeg = GameConfig.SWIPE_MAX_DEVIATION,
        fullSpinBow = GameConfig.SWIPE_FULL_BOW,
    )
    private val preview = SwipeResult()
    /** 공을 잡고 있음 */
    var holding = false
        private set
    /** 잡은(또는 기다리는) 공 자리 (레인 단위) */
    var holdX = GameConfig.BALL_REST_X
        private set
    var holdY = GameConfig.LANE_W / 2f
        private set
    private var hint = false

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xEEFFFFFF.toInt() }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = 0xCC3A220A.toInt()
    }
    private val path = Path()

    init {
        isClickable = true
    }

    /** 게임 연결 */
    fun bind(game: BowlingGame) {
        this.game = game
        invalidate()
    }

    /** 지금 굴릴 사람의 공 색 */
    fun setBallColor(color: Int) {
        ballColor = color
        ballDark = Theme.shade(color, 0.55f)
        invalidate()
    }

    /** 어프로치에 "밀어서 굴리기" 안내 표시 */
    fun setHint(on: Boolean) {
        if (hint != on) {
            hint = on
            invalidate()
        }
    }

    /** 공을 기다리는 자리로 (새 투구) */
    fun resetBall() {
        holding = false
        tracker.reset()
        holdX = GameConfig.BALL_REST_X
        holdY = GameConfig.LANE_W / 2f
        invalidate()
    }

    /** 잡고 있던 공 놓기 (일시정지 등) */
    fun cancelAim() {
        if (!holding) return
        holding = false
        tracker.reset()
        holdX = GameConfig.BALL_REST_X
        invalidate()
    }

    fun sx(x: Float): Float = ox + (x - X0) * unitPx
    fun sy(y: Float): Float = oy + (y - Y0) * unitPx

    /** 레인 단위 (x, y) → 이 View 좌표 */
    fun toScreen(x: Float, y: Float, out: PointF) {
        out.set(sx(x), sy(y))
    }

    private fun laneX(px: Float): Float = X0 + (px - ox) / unitPx
    private fun laneY(py: Float): Float = Y0 + (py - oy) / unitPx

    // ── 크기·비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val m = 2f * d
        unitPx = minOf((w - 2f * m) / (X1 - X0), (h - 2f * m) / (Y1 - Y0))
        ox = (w - (X1 - X0) * unitPx) / 2f
        oy = (h - (Y1 - Y0) * unitPx) / 2f
        label.textSize = 13f * d
        outline.textSize = 13f * d
        outline.strokeWidth = 3.5f * d
        buildLane()
    }

    /** 어프로치 + 레인 + 거터 + 표적 + 핀 덱 + 핏을 비트맵 하나에 (레인 범위만) */
    private fun buildLane() {
        release()
        val u = unitPx
        val bw = ((X1 - X0) * u).toInt().coerceAtLeast(1)
        val bh = ((Y1 - Y0) * u).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        fun x(v: Float) = (v - X0) * u
        fun y(v: Float) = (v - Y0) * u
        val top = y(-GameConfig.GUTTER_W - GameConfig.EDGE_W)
        val bottom = y(GameConfig.LANE_W + GameConfig.GUTTER_W + GameConfig.EDGE_W)
        val laneTop = y(0f)
        val laneBottom = y(GameConfig.LANE_W)
        // 어프로치 (밝은 단풍나무 바닥)
        q.color = 0xFFEBC995.toInt()
        c.drawRect(x(X0), top, x(0f), bottom, q)
        q.color = 0x22000000
        q.strokeWidth = maxOf(1f, u * 0.04f)
        val boardH = GameConfig.LANE_W / 39f * u
        var yy = top
        while (yy < bottom) { c.drawLine(x(X0), yy, x(0f), yy, q); yy += boardH }
        // 어프로치 점 (서는 자리 표시)
        q.color = 0x77503010
        for (ax in floatArrayOf(-5.6f, -3.2f)) for (b in intArrayOf(5, 10, 15, 20, 25, 30, 35)) {
            c.drawCircle(x(ax), y(b * GameConfig.LANE_W / 39f), u * 0.12f, q)
        }
        // 레인 (보드 39장, 번갈아 살짝 다른 색)
        val laneRight = x(GameConfig.PIT_X)
        q.color = 0xFF000000.toInt() // 그라데이션은 칠 색의 투명도를 따름 → 불투명으로
        q.shader = LinearGradient(0f, laneTop, 0f, laneBottom, intArrayOf(0xFFD9A462.toInt(), 0xFFEDC48A.toInt(), 0xFFD9A462.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(x(0f), laneTop, laneRight, laneBottom, q)
        q.shader = null
        for (i in 0 until 39) {
            if (i % 2 == 0) continue
            q.color = 0x10000000
            c.drawRect(x(0f), laneTop + i * boardH, laneRight, laneTop + (i + 1) * boardH, q)
        }
        q.color = 0x26502A08
        q.strokeWidth = maxOf(1f, u * 0.035f)
        for (i in 1 until 39) c.drawLine(x(0f), laneTop + i * boardH, laneRight, laneTop + i * boardH, q)
        // 나무결: 보드 안의 가늘고 긴 결 무늬 (고정 씨앗)
        val rnd = Random(7)
        q.strokeWidth = maxOf(1f, u * 0.025f)
        for (k in 0 until 70) {
            val b = rnd.nextInt(39)
            val gy = laneTop + (b + 0.25f + rnd.nextFloat() * 0.5f) * boardH
            val gx = x(rnd.nextFloat() * GameConfig.PIT_X)
            val len = u * (2f + rnd.nextFloat() * 7f)
            q.color = if (rnd.nextBoolean()) 0x1E6B3A10 else 0x18FFFFFF
            c.drawLine(gx, gy, minOf(laneRight, gx + len), gy, q)
        }
        // 표적 점 · 화살표
        q.color = 0xAA5A2E0C.toInt()
        for (b in intArrayOf(3, 5, 8, 11, 14, 25, 28, 31, 34, 36)) c.drawCircle(x(GameConfig.DOTS_X), laneTop + (b + 0.5f) * boardH, u * 0.13f, q)
        q.color = 0xCC4A2508.toInt()
        val pth = Path()
        for (k in 0 until 7) {
            val b = 5 + k * 5
            val off = 3 - kotlin.math.abs(k - 3)
            BowlingArt.arrow(c, q, x(GameConfig.ARROWS_X + off * 0.7f), laneTop + (b - 0.5f) * boardH, u * 0.9f, u * 0.22f, pth)
        }
        // 핀 덱 (조금 더 밝게) + 핀 자리
        q.color = 0x22FFFFFF
        c.drawRect(x(GameConfig.HEAD_X - 1.8f), laneTop, laneRight, laneBottom, q)
        q.color = 0x55402008
        for (i in 0 until GameConfig.PINS) c.drawCircle(x(Lane.spotX(i)), y(Lane.spotY(i)), u * 0.22f, q)
        // 파울 라인
        q.color = 0xFFB71C2C.toInt()
        c.drawRect(x(0f) - u * 0.12f, top, x(0f) + u * 0.12f, bottom, q)
        // 거터 (오목한 홈: 가장자리 어둡게)
        q.color = 0xFF000000.toInt()
        for (side in 0..1) {
            val gt = if (side == 0) y(-GameConfig.GUTTER_W) else laneBottom
            val gb = if (side == 0) laneTop else y(GameConfig.LANE_W + GameConfig.GUTTER_W)
            q.shader = LinearGradient(0f, gt, 0f, gb, intArrayOf(0xFF22252E.toInt(), 0xFF5A606E.toInt(), 0xFF22252E.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(x(0f), gt, laneRight, gb, q)
            q.shader = null
        }
        // 바깥 테두리 (캡) + 핀 덱 옆 킥백 판
        q.color = 0xFF3B2414.toInt()
        c.drawRect(x(0f), top, laneRight, y(-GameConfig.GUTTER_W), q)
        c.drawRect(x(0f), y(GameConfig.LANE_W + GameConfig.GUTTER_W), laneRight, bottom, q)
        q.color = 0xFF1C1F2A.toInt()
        c.drawRect(x(GameConfig.HEAD_X - 2.2f), top, laneRight, y(-GameConfig.GUTTER_W) + u * 0.1f, q)
        c.drawRect(x(GameConfig.HEAD_X - 2.2f), y(GameConfig.LANE_W + GameConfig.GUTTER_W) - u * 0.1f, laneRight, bottom, q)
        // 핏 (뒤 구덩이 + 커튼)
        q.color = 0xFF000000.toInt()
        q.shader = LinearGradient(laneRight, 0f, x(X1), 0f, 0xFF15161C.toInt(), 0xFF050508.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(laneRight, top, x(X1), bottom, q)
        q.shader = null
        q.color = 0x332A3B8F
        var cy = top
        while (cy < bottom) { c.drawRect(x(X1) - u * 0.7f, cy, x(X1), cy + u * 0.5f, q); cy += u * 1f }
        // 테두리 선
        q.style = Paint.Style.STROKE
        q.strokeWidth = maxOf(1.5f, u * 0.1f)
        q.color = Theme.GOLD_DARK
        c.drawRect(q.strokeWidth / 2f, top + q.strokeWidth / 2f, bw - q.strokeWidth / 2f, bottom - q.strokeWidth / 2f, q)
        laneBmp = bmp
    }

    fun release() {
        laneBmp?.recycle()
        laneBmp = null
    }

    // ── 터치 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val l = listener
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (l == null || !l.canAim()) return false
                if (laneX(e.x) > APPROACH_SLACK) return false // 어프로치에서만 잡기
                holding = true
                tracker.begin(e.x, e.y, e.eventTime)
                follow(e.x, e.y)
                l.onGrab()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!holding) return false
                tracker.add(e.x, e.y, e.eventTime)
                follow(e.x, e.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!holding) return false
                tracker.add(e.x, e.y, e.eventTime)
                follow(e.x, e.y)
                holding = false
                val r = tracker.analyze(GameConfig.LANE_W * unitPx, swipeConfig)
                tracker.reset()
                if (l == null || !l.canAim()) {
                    resetBall()
                    return true
                }
                if (r.valid) {
                    l.onSwipe(holdY, r.deviationDeg, r.power, r.spin)
                } else {
                    holdX = GameConfig.BALL_REST_X
                    l.onBadSwipe(r.reject)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAim()
                return true
            }
        }
        return false
    }

    /** 잡은 공이 손가락을 따라감 (어프로치 안, 레인 폭 안) */
    private fun follow(px: Float, py: Float) {
        holdX = laneX(px).coerceIn(-GameConfig.APPROACH + GameConfig.BALL_R, GameConfig.BALL_START_X)
        holdY = laneY(py).coerceIn(GameConfig.BALL_R, GameConfig.LANE_W - GameConfig.BALL_R)
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val bmp = laneBmp ?: return
        val g = game ?: return
        c.drawBitmap(bmp, ox, oy, bmpPaint)
        val u = unitPx
        val lane = g.lane
        // 쓰러진 핀 (회색, 누움) → 서 있는 핀
        for (pin in lane.pins) {
            if (pin.state != PinState.FALLEN) continue
            BowlingArt.fallenPin(c, p, sx(pin.x), sy(pin.y), GameConfig.PIN_R * u, pin.fallAngle, if (pin.active) 255 else 200)
        }
        for (pin in lane.pins) {
            if (pin.state != PinState.STANDING) continue
            BowlingArt.pin(c, p, sx(pin.x), sy(pin.y), GameConfig.PIN_R * u)
        }
        // 공
        val ball = lane.ball
        val br = GameConfig.BALL_R * u
        if (ball.active) {
            BowlingArt.ball(c, p, sx(ball.x), sy(ball.y), br, ballColor, ballDark, ball.roll)
        } else if (g.phase == Phase.AIM) {
            if (holding) drawGuide(c)
            BowlingArt.ball(c, p, sx(holdX), sy(holdY), br, ballColor, ballDark, 0f)
            if (hint && !holding) drawHint(c)
        }
    }

    /** 미는 중: 지금까지의 스와이프로 예상 방향(앞쪽 일부) 점선 + 휘는 방향 */
    private fun drawGuide(c: Canvas) {
        if (tracker.sampleCount < 2) return
        val r = tracker.analyze(GameConfig.LANE_W * unitPx, swipeConfig, preview)
        if (r.lengthNorm < GameConfig.SWIPE_MIN_LEN * 0.5f || r.dirX <= 0f) return
        val a = Math.toRadians(Aim.angleDeg(r.deviationDeg).toDouble()).toFloat()
        val ca = cos(a)
        val sa = sin(a)
        val hook = Aim.hook(r.spin)
        // 직선 점선 (레인 앞쪽 45%까지)
        dot.style = Paint.Style.FILL
        dot.color = if (r.valid) 0xDDFFFFFF.toInt() else 0x88FFFFFF.toInt()
        var t = 1.2f
        val end = GameConfig.HEAD_X * 0.45f - GameConfig.BALL_START_X
        while (t < end) {
            val x = GameConfig.BALL_START_X + ca * t
            val y = holdY + sa * t
            c.drawCircle(sx(x), sy(y), unitPx * 0.13f, dot)
            t += 1.3f
        }
        // 휘는 방향: 점선 끝에서 훅 방향으로 짧은 곡선 화살표 (세기만큼)
        if (hook > 0.08f || hook < -0.08f) {
            val ex = GameConfig.BALL_START_X + ca * end
            val ey = holdY + sa * end
            dot.color = Theme.GOLD
            val steps = 6
            for (k in 1..steps) {
                val f = k / steps.toFloat()
                c.drawCircle(sx(ex + f * 3.2f), sy(ey + hook * 2.2f * f * f), unitPx * (0.12f + 0.05f * f), dot)
            }
        }
        // 세기 막대 (공 왼쪽)
        val bx = sx(holdX) - GameConfig.BALL_R * unitPx * 1.7f
        val by0 = sy(holdY) + GameConfig.BALL_R * unitPx
        val bh = GameConfig.BALL_R * unitPx * 2f
        dot.color = 0x66000000
        c.drawRect(bx - 2.5f * d, by0 - bh, bx + 2.5f * d, by0, dot)
        dot.color = if (r.power > 0.8f) Theme.RED else Theme.GOLD
        c.drawRect(bx - 2.5f * d, by0 - bh * r.power, bx + 2.5f * d, by0, dot)
    }

    /** 대기 중 안내: 공 오른쪽으로 화살표 + 글자 */
    private fun drawHint(c: Canvas) {
        val y = sy(holdY)
        val x0 = sx(holdX) + GameConfig.BALL_R * unitPx * 1.4f
        val x1 = sx(-0.3f)
        if (x1 <= x0) return
        dot.color = 0xCCFFFFFF.toInt()
        dot.style = Paint.Style.FILL
        BowlingArt.arrow(c, dot, x1 - unitPx * 1.1f, y, unitPx * 1.1f, unitPx * 0.55f, path)
        dot.style = Paint.Style.STROKE
        dot.strokeWidth = unitPx * 0.28f
        dot.strokeCap = Paint.Cap.ROUND
        c.drawLine(x0, y, x1 - unitPx * 0.9f, y, dot)
        dot.strokeCap = Paint.Cap.BUTT
        dot.style = Paint.Style.FILL
        val ty = if (holdY < GameConfig.LANE_W / 2f) sy(holdY + 2.6f) else sy(holdY - 1.9f)
        val tx = sx(-GameConfig.APPROACH / 2f)
        c.drawText(HINT, tx, ty, outline)
        c.drawText(HINT, tx, ty, label)
    }

    companion object {
        /** 그리는 범위 (레인 단위) */
        const val X0 = -GameConfig.APPROACH
        const val X1 = GameConfig.PIT_X + GameConfig.PIT_DEPTH
        const val Y0 = -GameConfig.GUTTER_W - GameConfig.EDGE_W
        const val Y1 = GameConfig.LANE_W + GameConfig.GUTTER_W + GameConfig.EDGE_W
        /** 파울 라인을 이만큼 넘어 눌러도 공을 잡음 */
        private const val APPROACH_SLACK = 0.6f
        private const val HINT = "밀어서 굴리기"
    }
}
