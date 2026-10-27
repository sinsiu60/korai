package com.siwoo.party.games.tuho.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.aim.ArcGaugeDrawer
import com.siwoo.party.common.aim.GaugeDrawer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.tuho.GameConfig
import com.siwoo.party.games.tuho.Phase
import com.siwoo.party.games.tuho.Shot
import com.siwoo.party.games.tuho.TuhoField
import com.siwoo.party.games.tuho.TuhoGame
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 투호 마당 (옆에서 본 모습, 하나의 View).
 * 한지 벽·창살 + 나무 마루 / 왼쪽: 던지는 사람(플레이어 색 저고리) / 오른쪽: 청동 항아리(가운데 입구 + 양옆 귀).
 * 각도 게이지(공통 ArcGaugeDrawer, 손 둘레 부채꼴)와 힘 게이지(공통 GaugeDrawer, 왼쪽 세로 막대)를 같이 그린다.
 * 화면 어디를 눌러도 listener.onCourtTap — 게이지 멈춤 판단은 Activity 가.
 * 비행은 판정 결과(Shot)의 포물선 식을 게임 시계([now])로 따라 그린다 (일시정지하면 시계가 멈춰 그대로 멈춤).
 * 스스로 반복해서 그리지 않는다. onDraw 할당 없음.
 */
class CourtView(context: Context) : View(context) {

    fun interface Listener {
        fun onCourtTap()
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: TuhoGame? = null
    private var colors = IntArray(GameConfig.MAX_PLAYERS)

    /** 게임 시계 (Activity 가 넣음) */
    var now = 0L

    // ── 비행 ──
    private var shot: Shot? = null
    private var shooter = 0
    private var flightStart = -1L
    /** 비행 1초를 몇 ms 로 보여 줄지 (느린 화면 × 속도 배율) */
    private var msPerSec = 1000f

    // ── 배치 (onSizeChanged) ──
    /** 1미터 = s 픽셀, 월드 x = 0 의 화면 x, 바닥 y */
    var s = 1f
        private set
    private var ox = 0f
    var floorY = 0f
        private set
    private val clip = Path()
    private val bounds = RectF()
    private val window = RectF()
    private val tmp = RectF()
    private var fibers = FloatArray(0)

    private val angleGauge = ArcGaugeDrawer(context).apply {
        label = ANGLE_LABEL
        startAngle = -GameConfig.ANGLE_MAX
        sweepAngle = GameConfig.ANGLE_MAX - GameConfig.ANGLE_MIN
        glow = 0f
        trackColor = 0x552A1A0C
        needleColor = 0xFF3A2410.toInt()
        needleIdleColor = 0xFF9A6A2A.toInt()
        hubColor = Theme.GOLD
        hubIdleColor = 0xFFB89A6A.toInt()
        labelColor = 0xFF6A3A10.toInt()
        labelIdleColor = 0xFF9A8060.toInt()
    }
    private val powerGauge = GaugeDrawer(context).apply {
        label = POWER_LABEL
        glow = 0f
        trackColor = 0x552A1A0C
        needleColor = 0xFF3A2410.toInt()
        needleIdleColor = 0xFF9A6A2A.toInt()
        borderColor = Theme.GOLD_DARK
        borderIdleColor = 0x886A4A2A.toInt()
        labelColor = 0xFF6A3A10.toInt()
        labelIdleColor = 0xFF9A8060.toInt()
        fillColor = 0xDDE0502A.toInt()
        fillIdleColor = 0x88E0502A.toInt()
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wall = Paint()
    private val floor = Paint()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val degPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFF5A2E0A.toInt()
    }
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val tagStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = 0xFF2A1404.toInt()
    }

    fun bind(g: TuhoGame, playerColors: IntArray) {
        game = g
        colors = playerColors
        invalidate()
    }

    // ── 비행 ──

    /** 비행 시작 (shot 의 포물선을 start 부터 msPerSecond 속도로 보여 줌) */
    fun startFlight(sh: Shot, player: Int, startMs: Long, msPerSecond: Float) {
        shot = sh
        shooter = player
        flightStart = startMs
        msPerSec = msPerSecond
        invalidate()
    }

    /** 지난 화살 지우기 (다음 사람이 조준할 때) */
    fun clearFlight() {
        shot = null
        flightStart = -1L
        invalidate()
    }

    /** 비행 시작 뒤 지난 "비행 시간" (초) */
    fun flightSec(nowMs: Long): Double =
        if (flightStart < 0L) 0.0 else maxOf(0L, nowMs - flightStart).toDouble() / msPerSec

    /** 화살이 결과 자리(구멍·테두리·몸통·바닥)에 닿았는지 */
    fun reachedEvent(nowMs: Long): Boolean {
        val sh = shot ?: return false
        return flightSec(nowMs) >= sh.t
    }

    /** 비행·튕김 연출이 아직 움직이는지 */
    fun animating(nowMs: Long): Boolean {
        val sh = shot ?: return false
        return flightSec(nowMs) < sh.totalT
    }

    /** 비행 중인 화살이 있는지 (테스트용) */
    val hasFlight: Boolean get() = shot != null

    /** 월드 (m) → 화면 (px) */
    fun toScreen(x: Double, y: Double, out: PointF) {
        out.set(ox + (x * s).toFloat(), floorY - (y * s).toFloat())
    }

    /** 구멍 위 끝 가운데 (화면) */
    fun openingPoint(index: Int, out: PointF) {
        val o = TuhoField.openings[index]
        toScreen(o.cx.toDouble(), o.top.toDouble(), out)
    }

    private fun sx(x: Float): Float = ox + x * s
    private fun sy(y: Float): Float = floorY - y * s

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.reset()
        clip.addRoundRect(bounds, 16f * d, 16f * d, Path.Direction.CW)
        floorY = h - maxOf(24f * d, h * 0.12f)
        val spanX = VIEW_RIGHT - VIEW_LEFT
        s = minOf((w - 8f * d) / spanX, (floorY - 4f * d) / VIEW_TOP)
        ox = (w - spanX * s) / 2f - VIEW_LEFT * s
        // 한지 벽·나무 마루 그라데이션
        wall.shader = LinearGradient(0f, 0f, 0f, floorY, 0xFFF7EFDC.toInt(), 0xFFE6D6B4.toInt(), Shader.TileMode.CLAMP)
        floor.shader = LinearGradient(0f, floorY, 0f, h.toFloat(), 0xFFC48D55.toInt(), 0xFF8A5A2E.toInt(), Shader.TileMode.CLAMP)
        // 한지 섬유 (고정 씨앗)
        val rnd = kotlin.random.Random(7)
        fibers = FloatArray(FIBERS * 4)
        for (i in 0 until FIBERS) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * floorY
            val len = (6f + rnd.nextFloat() * 14f) * d
            val a = rnd.nextFloat() * 3.1416f
            fibers[i * 4] = x
            fibers[i * 4 + 1] = y
            fibers[i * 4 + 2] = x + cos(a) * len
            fibers[i * 4 + 3] = y + sin(a) * len
        }
        // 창살 창 (가운데 위)
        window.set(sx(0.55f), 10f * d, sx(1.45f), maxOf(10f * d + 40f * d, floorY * 0.42f))
        // 게이지
        val ax = sx(GameConfig.RELEASE_X)
        val ay = sy(GameConfig.RELEASE_Y)
        angleGauge.bandWidth = 9f * d
        angleGauge.needleWidth = 3f * d
        angleGauge.needleExtra = 6f * d
        angleGauge.hubRadius = 4.5f * d
        angleGauge.labelSize = 12f * d
        angleGauge.layout(ax, ay, GameConfig.ARROW_LEN * s * 1.15f)
        angleGauge.labelX = ax + 4f * d
        angleGauge.labelY = ay - angleGauge.radius - 14f * d
        powerGauge.layout(12f * d, 30f * d, 32f * d, floorY - 10f * d)
        powerGauge.labelSize = 12f * d
        powerGauge.labelX = 24f * d
        degPaint.textSize = 12f * d
        tagPaint.textSize = maxOf(9f * d, 0.075f * s)
        tagStroke.textSize = tagPaint.textSize
        tagStroke.strokeWidth = 2.5f * d
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val a = e.actionMasked
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) listener?.onCourtTap()
        return true
    }

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        c.save()
        c.clipPath(clip)
        drawRoom(c, w, h)
        // 항아리 + 꽂힌 화살
        val jx = sx(GameConfig.JAR_X)
        TuhoArt.jarShadow(c, p, jx, floorY, s)
        TuhoArt.jarBody(c, p, jx, floorY, s)
        drawStuck(c, g)
        TuhoArt.jarFront(c, p, jx, floorY, s)
        drawTags(c, jx)
        // 던지는 사람 + 손에 든 화살
        val phase = g.phase
        val av = when (phase) {
            Phase.ANGLE -> g.angleGauge.valueAt(now)
            Phase.WAIT -> 0.5f
            else -> g.angleValue
        }
        val deg = TuhoField.angleFor(av)
        val rad = Math.toRadians(deg.toDouble())
        val dx = cos(rad).toFloat()
        val dy = -sin(rad).toFloat()
        val tipX = sx(GameConfig.RELEASE_X)
        val tipY = sy(GameConfig.RELEASE_Y)
        val hold = GameConfig.ARROW_LEN * s * 0.45f
        val holding = phase == Phase.WAIT || phase == Phase.ANGLE || phase == Phase.POWER
        val player = if (holding) g.current else shooter
        TuhoArt.thrower(c, p, sx(THROWER_X), floorY, s, colors[player], tipX - dx * hold, tipY - dy * hold)
        // 각도 게이지 (손 둘레)
        val aiming = phase == Phase.ANGLE || phase == Phase.POWER
        if (aiming || phase == Phase.WAIT) {
            angleGauge.draw(c, 1f - av, active = phase == Phase.ANGLE, showNeedle = aiming)
            if (aiming) {
                val r = angleGauge.radius + 16f * d
                c.drawText(DEG[(deg + 0.5f).toInt().coerceIn(0, 90)], tipX + dx * r, tipY + dy * r + 4f * d, degPaint)
            }
        }
        if (holding) TuhoArt.arrow(c, p, tipX, tipY, dx, dy, GameConfig.ARROW_LEN * s, colors[player])
        // 힘 게이지 (왼쪽)
        val pv = when (phase) {
            Phase.POWER -> g.powerGauge.valueAt(now)
            Phase.FLIGHT, Phase.RESULT -> g.powerValue
            else -> 0f
        }
        powerGauge.draw(c, pv, active = phase == Phase.POWER, showNeedle = phase != Phase.WAIT && phase != Phase.ANGLE)
        // 날아가는 화살
        drawFlight(c)
        c.restore()
        // 테두리
        line.strokeWidth = 2f * d
        line.color = 0xAA6A4A2A.toInt()
        c.drawRoundRect(bounds, 16f * d, 16f * d, line)
    }

    /** 한지 벽 + 섬유 + 창살 + 걸레받이 + 나무 마루 */
    private fun drawRoom(c: Canvas, w: Float, h: Float) {
        c.drawRect(0f, 0f, w, floorY, wall)
        line.strokeWidth = 1f * d
        line.color = 0x22A07040
        val f = fibers
        var i = 0
        while (i + 3 < f.size) {
            c.drawLine(f[i], f[i + 1], f[i + 2], f[i + 3], line)
            i += 4
        }
        // 창살 (아(亞)자 느낌의 격자)
        p.style = Paint.Style.FILL
        p.color = 0x22FFFFFF
        c.drawRect(window, p)
        line.color = 0x557A5230
        line.strokeWidth = 4f * d
        c.drawRect(window, line)
        line.strokeWidth = 1.6f * d
        val cols = 5
        val rows = 3
        for (k in 1 until cols) {
            val x = window.left + window.width() * k / cols
            c.drawLine(x, window.top, x, window.bottom, line)
        }
        for (k in 1 until rows) {
            val y = window.top + window.height() * k / rows
            c.drawLine(window.left, y, window.right, y, line)
        }
        // 마루
        c.drawRect(0f, floorY, w, h, floor)
        p.color = 0xFF5A3A1C.toInt()
        c.drawRect(0f, floorY - 5f * d, w, floorY, p)
        p.color = 0x33FFFFFF
        c.drawRect(0f, floorY, w, floorY + 1.5f * d, p)
        line.color = 0x55402410
        line.strokeWidth = 1.2f * d
        val fh = h - floorY
        val y1 = floorY + fh * 0.38f
        val y2 = floorY + fh * 0.72f
        c.drawLine(0f, y1, w, y1, line)
        c.drawLine(0f, y2, w, y2, line)
        val step = 74f * d
        var x = step * 0.4f
        while (x < w) {
            c.drawLine(x, floorY, x, y1, line)
            c.drawLine(x + step * 0.5f, y1, x + step * 0.5f, y2, line)
            c.drawLine(x + step * 0.25f, y2, x + step * 0.25f, h, line)
            x += step
        }
    }

    /** 목·귀에 점수 글자 */
    private fun drawTags(c: Canvas, jx: Float) {
        val m = TuhoField.mouth
        val ty = sy((m.top + m.bottom) / 2f + 0.03f)
        val off = tagPaint.textSize * 0.36f
        c.drawText(TAG_MOUTH, jx, ty + off, tagStroke)
        c.drawText(TAG_MOUTH, jx, ty + off, tagPaint)
        for (k in 1..2) {
            val e = TuhoField.openings[k]
            val ex = sx(e.cx)
            val ey = sy(e.top - 0.05f)
            c.drawText(TAG_EAR, ex, ey + off, tagStroke)
            c.drawText(TAG_EAR, ex, ey + off, tagPaint)
        }
    }

    /** 이번 순번에 꽂힌 화살 (관 속 부분은 앞에 그리는 관이 가림) */
    private fun drawStuck(c: Canvas, g: TuhoGame) {
        val list = g.stuck
        val len = GameConfig.ARROW_LEN * s
        for (i in 0 until list.size) {
            val a = list[i]
            if (a.slot >= GameConfig.MAX_STUCK_DRAWN) continue
            val o = TuhoField.openings[a.opening]
            val tilt = Math.toRadians(a.tiltDeg.toDouble())
            val dx = sin(tilt).toFloat()
            val dy = cos(tilt).toFloat()
            val depth = STUCK_DEPTH * s
            // 구멍 높이에서 지나는 자리 (칸마다 조금씩 비켜)
            val cross = sx(o.cx) + SLOT_OFF[a.slot] * o.half * s
            val tx = cross + dx / dy * depth
            val ty = sy(o.top) + depth
            TuhoArt.arrow(c, p, tx, ty, dx, dy, len, colors[a.player])
        }
    }

    /** 날아가는·튕기는·떨어진 화살 */
    private fun drawFlight(c: Canvas) {
        val sh = shot ?: return
        val e = flightSec(now)
        val len = GameConfig.ARROW_LEN * s
        val col = colors[shooter]
        if (e < sh.t) {
            // 비행 + 잔상
            p.style = Paint.Style.FILL
            for (k in TRAIL downTo 1) {
                val te = e - k * TRAIL_DT
                if (te < 0.0) continue
                p.color = col
                p.alpha = 150 - k * 16
                c.drawCircle(ox + (sh.xAt(te) * s).toFloat(), floorY - (sh.yAt(te) * s).toFloat(), (3.2f - k * 0.3f) * d, p)
            }
            p.alpha = 255
            val tx = ox + (sh.xAt(e) * s).toFloat()
            val ty = floorY - (sh.yAt(e) * s).toFloat()
            val vx = sh.vx.toFloat()
            val vy = (-sh.vyAt(e)).toFloat()
            val n = sqrt(vx * vx + vy * vy)
            TuhoArt.arrow(c, p, tx, ty, vx / n, vy / n, len, col)
            if (ty < 0f) offscreenMarker(c, tx, col)
            return
        }
        if (sh.outcome.inside) return // 꽂힌 화살로 그림
        if (sh.outcome.bounces) {
            val be = minOf(e - sh.t, sh.bounceT)
            val tx = ox + (sh.bounceXAt(be) * s).toFloat()
            val ty = floorY - (sh.bounceYAt(be) * s).toFloat()
            val a0 = Math.atan2(-sh.hitVy, sh.hitVx)
            if (e - sh.t < sh.bounceT) {
                val a = a0 + Math.toRadians(SPIN_DEG_PER_SEC * be)
                TuhoArt.arrow(c, p, tx, ty, cos(a).toFloat(), sin(a).toFloat(), len, col)
            } else {
                // 바닥에 누움: 끝이 항아리 쪽(오른쪽), 몸통은 항아리 앞 왼쪽으로 (항아리와 안 겹치게)
                val rx = minOf(tx, sx(GameConfig.JAR_X - REST_GAP))
                val a = Math.toRadians(REST_DEG)
                TuhoArt.arrow(c, p, rx, floorY + 1f * d, cos(a).toFloat(), sin(a).toFloat(), len, col)
            }
            return
        }
        // 바닥에 꽂힘 (기울기는 완만하게)
        val a = minOf(Math.atan2(-sh.hitVy, sh.hitVx), Math.toRadians(FLOOR_MAX_DEG))
        TuhoArt.arrow(c, p, ox + (sh.x * s).toFloat(), floorY + 2f * d, cos(a).toFloat(), sin(a).toFloat(), len, col)
    }

    /** 화면 위로 나간 화살 위치 표시 (위 가장자리 작은 삼각형) */
    private fun offscreenMarker(c: Canvas, x: Float, col: Int) {
        p.style = Paint.Style.FILL
        p.color = col
        val mx = x.coerceIn(8f * d, width - 8f * d)
        c.drawCircle(mx, 9f * d, 6f * d, p)
        line.color = 0xFF3A2410.toInt()
        line.strokeWidth = 2f * d
        c.drawLine(mx, 12f * d, mx, 6f * d, line)
        c.drawLine(mx - 3f * d, 9f * d, mx, 6f * d, line)
        c.drawLine(mx + 3f * d, 9f * d, mx, 6f * d, line)
    }

    companion object {
        /** 보이는 월드 범위 (m) */
        private const val VIEW_LEFT = -0.88f
        private const val VIEW_RIGHT = GameConfig.JAR_X + 0.62f
        private const val VIEW_TOP = 1.9f
        /** 던지는 사람 발 위치 */
        private const val THROWER_X = -0.24f
        private const val FIBERS = 46
        private const val TRAIL = 6
        private const val TRAIL_DT = 0.035
        private const val SPIN_DEG_PER_SEC = -620.0
        private const val REST_DEG = 5.0
        /** 누운 화살 끝이 항아리 가운데에서 떨어진 최소 거리 (m) */
        private const val REST_GAP = 0.2f
        private const val FLOOR_MAX_DEG = 32.0
        /** 꽂힌 화살이 관 속으로 들어간 깊이 (m) */
        private const val STUCK_DEPTH = 0.07f
        /** 칸마다 구멍 안 가로 위치 (반폭 배수) */
        private val SLOT_OFF = floatArrayOf(0f, -0.5f, 0.5f, -0.2f)
        private const val ANGLE_LABEL = "① 각도"
        private const val POWER_LABEL = "② 힘"
        private const val TAG_MOUTH = "2"
        private const val TAG_EAR = "3"
        private val DEG = Array(91) { it.toString() + "°" }
    }
}
