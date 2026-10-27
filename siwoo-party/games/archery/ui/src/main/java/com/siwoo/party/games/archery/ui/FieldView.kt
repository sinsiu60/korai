package com.siwoo.party.games.archery.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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
import com.siwoo.party.common.aim.AimDrawer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.archery.ArcheryGame
import com.siwoo.party.games.archery.GameConfig
import com.siwoo.party.games.archery.Wind
import kotlin.math.sin

/**
 * 사대에서 본 경기장: 하늘·언덕·잔디 + 짚 과녁대 위 10점 과녁 (배경 전체를 크기가 정해질 때 한 번 비트맵으로) +
 * 바람 깃발 + 지금 사람이 쏜 화살 + 날아가는 화살 + 흔들리는 조준점 + 바람 표시(왼쪽 위) + 숨 막대(왼쪽 아래).
 * 터치: 누름 = 숨 참기, 누른 채 끌기 = 조준 옮기기, 뗌 = 발사 (판정은 Activity).
 * 시각 [now] 는 Activity 가 프레임마다 넣는다 (게임 시계 — 일시정지하면 멈춤). onDraw 할당 없음.
 */
class FieldView(context: Context) : View(context) {

    interface Listener {
        /** 누름 — 받아들였으면 true (그 뒤 끌기·뗌이 온다) */
        fun onPress(): Boolean
        fun onDrag(dxPx: Float, dyPx: Float)
        fun onRelease()
        fun onCancel()
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: ArcheryGame? = null
    private var colors = IntArray(GameConfig.MAX_PLAYERS)

    /** 지금 시각 (게임 시계) */
    var now = 0L
    /** 조준점을 보일지 */
    var aimVisible = false
    /** 화살을 보여 줄 사람 */
    var shooter = 0

    // ── 과녁 자리 (px) ──
    var targetX = 0f
        private set
    var targetY = 0f
        private set
    var targetR = 0f
        private set
    /** 고리 한 칸의 px */
    val unitPx: Float get() = targetR / GameConfig.FACE_R

    // ── 날아가는 화살 ──
    private var flyStart = 0L
    private var flyMs = 0L
    private var flyX = 0f
    private var flyY = 0f
    private var flying = false
    private var hideArrow = -1

    // ── 바람 ──
    private var windDir = 0
    private var windStrength = 0f
    private var windText = ""

    private var scene: Bitmap? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val rect = RectF()
    private val aim = AimDrawer(context)
    private val out = FloatArray(2)

    private var downX = 0f
    private var downY = 0f
    private var tracking = false

    fun bind(g: ArcheryGame, playerColors: IntArray) {
        game = g
        for (i in playerColors.indices) colors[i] = playerColors[i]
        setWind(g.wind)
        invalidate()
    }

    fun setWind(w: Wind) {
        windDir = w.dirDeg
        windStrength = w.speed10.toFloat() / GameConfig.WIND_MAX10
        windText = w.speedText + " m/s"
        invalidate()
    }

    /** 화살 날리기: 지금부터 ms 동안 아래(사수)에서 (x, y) 고리 자리로. k = 그 화살 번호(날아가는 동안 꽂힌 그림은 숨김) */
    fun startFlight(x: Float, y: Float, k: Int, ms: Long) {
        flyX = x
        flyY = y
        flyStart = now
        flyMs = maxOf(1L, ms)
        flying = true
        hideArrow = k
        invalidate()
    }

    /** 날아가는 중인지 (끝났으면 꽂힌 그림으로) */
    fun busy(): Boolean {
        if (flying && now - flyStart >= flyMs) {
            flying = false
            hideArrow = -1
        }
        return flying
    }

    /** 고리 좌표 → 화면 px */
    fun toScreen(x: Float, y: Float, pt: PointF) {
        pt.set(targetX + x * unitPx, targetY + y * unitPx)
    }

    /** 터치 중이던 것 버림 (일시정지 등) */
    fun cancelTouch() {
        tracking = false
    }

    fun release() {
        scene?.recycle()
        scene = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        targetR = minOf(h * 0.36f, w * 0.3f)
        targetX = w * 0.5f
        targetY = h * 0.45f
        label.textSize = 11f * d
        big.textSize = 15f * d
        aim.radius = 13f * d
        scene?.recycle()
        scene = null
        if (w > 0 && h > 0) {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawScene(Canvas(b), w.toFloat(), h.toFloat())
            scene = b
        }
    }

    /** 움직이지 않는 배경 (한 번만) */
    private fun drawScene(c: Canvas, w: Float, h: Float) {
        val horizon = h * 0.4f
        // 하늘
        p.shader = LinearGradient(0f, 0f, 0f, horizon, 0xFF5BA8E8.toInt(), 0xFFBFE3F7.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, horizon, p)
        p.shader = null
        // 구름
        p.color = 0xCCFFFFFF.toInt()
        cloud(c, w * 0.16f, h * 0.12f, h * 0.05f)
        cloud(c, w * 0.78f, h * 0.08f, h * 0.04f)
        cloud(c, w * 0.6f, h * 0.2f, h * 0.03f)
        // 먼 언덕·나무줄
        p.color = 0xFF6FA86A.toInt()
        val hill = Path()
        hill.moveTo(0f, horizon)
        hill.quadTo(w * 0.2f, horizon - h * 0.12f, w * 0.42f, horizon - h * 0.03f)
        hill.quadTo(w * 0.68f, horizon - h * 0.14f, w, horizon - h * 0.04f)
        hill.lineTo(w, horizon)
        hill.close()
        c.drawPath(hill, p)
        p.color = 0xFF3F7A45.toInt()
        var x = 0f
        while (x < w) {
            c.drawCircle(x, horizon - h * 0.012f, h * 0.025f, p)
            x += h * 0.035f
        }
        // 잔디 (원근 줄무늬: 소실점은 지평선 가운데)
        p.shader = LinearGradient(0f, horizon, 0f, h, 0xFF57A84C.toInt(), 0xFF2F7D32.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, horizon, w, h, p)
        p.shader = null
        p.color = 0x18FFFFFF
        val vx = w * 0.5f
        val stripes = 9
        for (k in 0 until stripes step 2) {
            val l0 = -w * 0.6f + (w * 2.2f) * k / stripes
            val l1 = -w * 0.6f + (w * 2.2f) * (k + 1) / stripes
            val s = Path()
            s.moveTo(vx, horizon)
            s.lineTo(l0, h)
            s.lineTo(l1, h)
            s.close()
            c.drawPath(s, p)
        }
        // 사거리 흰 선
        p.color = 0x55FFFFFF
        p.strokeWidth = 2f * d
        c.drawLine(0f, horizon + (h - horizon) * 0.62f, w, horizon + (h - horizon) * 0.62f, p)
        // 과녁대 다리 (A자)
        val half = targetR * 1.14f
        val bossBottom = targetY + half
        val ground = minOf(h - 4f * d, bossBottom + h * 0.08f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = maxOf(3f, targetR * 0.07f)
        p.color = ArcheryArt.WOOD
        c.drawLine(targetX - half * 0.55f, targetY, targetX - half * 0.8f, ground, p)
        c.drawLine(targetX + half * 0.55f, targetY, targetX + half * 0.8f, ground, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        // 그림자
        p.color = 0x33000000
        c.drawOval(targetX - half * 1.1f, ground - h * 0.025f, targetX + half * 1.1f, ground + h * 0.02f, p)
        ArcheryArt.drawBoss(c, p, targetX, targetY, half)
        ArcheryArt.drawFace(c, p, targetX, targetY, targetR)
    }

    private fun cloud(c: Canvas, x: Float, y: Float, r: Float) {
        c.drawCircle(x, y, r, p)
        c.drawCircle(x + r * 1.1f, y + r * 0.2f, r * 0.8f, p)
        c.drawCircle(x - r * 1.1f, y + r * 0.25f, r * 0.7f, p)
        c.drawRoundRect(x - r * 1.7f, y + r * 0.1f, x + r * 1.8f, y + r * 0.85f, r * 0.4f, r * 0.4f, p)
    }

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val bmp = scene
        if (bmp != null) c.drawBitmap(bmp, 0f, 0f, bmpPaint) else c.drawColor(0xFF2F7D32.toInt())
        // 바람 깃발 (과녁 오른쪽)
        val flagX = minOf(w - 20f * d, targetX + targetR * 1.14f + w * 0.12f)
        ArcheryArt.drawFlag(c, p, flagX, targetY + targetR * 1.3f, targetR * 1.6f, windDir, windStrength, now * 0.012f)
        // 꽂힌 화살 (지금 사람)
        val ns = maxOf(5f * d, unitPx * 0.75f)
        val list = g.shots[shooter]
        for (k in list.indices) {
            val s = list[k] ?: continue
            if (k == hideArrow && flying) continue
            ArcheryArt.drawNock(c, p, targetX + s.x * unitPx, targetY + s.y * unitPx, ns, colors[shooter])
        }
        // 날아가는 화살: 아래(사수)에서 커다랗게 → 과녁 자리로 작아지며
        if (flying) {
            val f = ((now - flyStart).toFloat() / flyMs).coerceIn(0f, 1f)
            val e = 1f - (1f - f) * (1f - f)
            val ex = targetX + flyX * unitPx
            val ey = targetY + flyY * unitPx
            val sx = w * 0.5f + (ex - w * 0.5f) * 0.15f
            val sy = h * 1.1f
            val x = sx + (ex - sx) * e
            val y = sy + (ey - sy) * e - sin(f * Math.PI.toFloat()) * h * 0.08f
            ArcheryArt.drawNock(c, p, x, y, ns * (4f - 3f * e), colors[shooter])
        }
        // 조준점
        if (aimVisible) {
            g.aimAt(now, out)
            val holding = g.holding
            val scale = g.swayScaleAt(now)
            aim.steady = if (holding && scale < 1f) ((1f - scale) / (1f - GameConfig.BREATH_MIN)).coerceIn(0f, 1f) else 0f
            aim.color = if (g.breath.outOfBreath(now)) Theme.RED else Theme.TEXT
            aim.draw(c, targetX + out[0] * unitPx, targetY + out[1] * unitPx)
        }
        drawWindPanel(c)
        drawBreath(c, g, h)
    }

    /** 왼쪽 위: 바람 방향 화살표 + 세기 */
    private fun drawWindPanel(c: Canvas) {
        val l = 8f * d
        val t = 8f * d
        rect.set(l, t, l + 112f * d, t + 54f * d)
        p.color = 0xCC101840.toInt()
        c.drawRoundRect(rect, 12f * d, 12f * d, p)
        c.drawText(WIND, l + 10f * d, t + 16f * d, label)
        val cx = l + 30f * d
        val cy = t + 34f * d
        p.color = 0x33FFFFFF
        c.drawCircle(cx, cy, 15f * d, p)
        ArcheryArt.drawWindArrow(c, p, cx, cy, 26f * d, windDir, Theme.GOLD)
        c.drawText(windText, l + 52f * d, t + 40f * d, big)
    }

    /** 왼쪽 아래: 숨 막대 (누르는 동안 줄어듦, 다 쓰면 빨강 "숨이 차요!") */
    private fun drawBreath(c: Canvas, g: ArcheryGame, h: Float) {
        val l = 8f * d
        val b = h - 8f * d
        rect.set(l, b - 30f * d, l + 168f * d, b)
        p.color = 0x99101840.toInt()
        c.drawRoundRect(rect, 10f * d, 10f * d, p)
        val holding = g.holding
        val out = g.breath.outOfBreath(now)
        label.color = if (out) Theme.RED else if (holding) Theme.GOLD else Theme.TEXT_SUB
        c.drawText(if (out) OUT_OF_BREATH else BREATH, l + 9f * d, b - 11f * d, label)
        val bl = l + 66f * d
        val br = l + 158f * d
        val bt = b - 20f * d
        val bb = b - 10f * d
        rect.set(bl, bt, br, bb)
        p.color = 0x44FFFFFF
        c.drawRoundRect(rect, 5f * d, 5f * d, p)
        val f = if (holding) g.breath.breathLeft(now) else 1f
        if (out) {
            p.color = Theme.RED
            p.alpha = if ((now / 160L) % 2L == 0L) 255 else 140
            c.drawRoundRect(rect, 5f * d, 5f * d, p)
            p.alpha = 255
        } else if (f > 0f) {
            rect.set(bl, bt, bl + (br - bl) * f, bb)
            p.color = if (holding) Theme.GOLD else 0x88B8C3F0.toInt()
            c.drawRoundRect(rect, 5f * d, 5f * d, p)
        }
        label.color = Theme.TEXT_SUB
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val l = listener ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = l.onPress()
                downX = e.x
                downY = e.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return true
                val dx = e.x - downX
                val dy = e.y - downY
                downX = e.x
                downY = e.y
                if (dx != 0f || dy != 0f) l.onDrag(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (tracking) {
                    tracking = false
                    l.onRelease()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (tracking) {
                    tracking = false
                    l.onCancel()
                }
                return true
            }
        }
        return true
    }

    companion object {
        private const val WIND = "바람"
        private const val BREATH = "숨 참기"
        private const val OUT_OF_BREATH = "숨이 차요!"
    }
}
