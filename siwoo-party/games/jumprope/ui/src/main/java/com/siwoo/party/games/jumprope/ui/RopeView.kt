package com.siwoo.party.games.jumprope.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import kotlin.math.exp
import kotlin.math.min

/**
 * 줄넘기 무대 (화면 전체 위에 겹치는 그림 층, 터치는 아래 MultiTouchZoneView 로 그대로 전달).
 * - 가운데 띠: 양 끝 기둥이 돌리는 큰 줄 + 나란히 선 아이들(플레이어 색) + 이름
 *   줄은 머리 위·뒤쪽일 때 아이들 뒤에, 앞쪽·바닥일 때 앞에 그린다.
 * - 띠 위 가운데: 넘은 횟수·속도 단계
 * - 각 구역 바깥쪽: 이름 + 상태 글자 (위쪽 구역은 180° 돌려서), 탈락한 구역은 어둡게
 * 움직임은 step(dtMs) 로 진행 (화면이 줄이 도는 동안·연출 중에만 프레임을 돌린다).
 */
class RopeView(context: Context) : View(context) {

    private val u = Theme.dpf(context, 1f)

    // ── 플레이어 ──
    private var n = 0
    private val colors = IntArray(MAX)
    private val names = arrayOfNulls<String>(MAX)
    private val subs = arrayOfNulls<String>(MAX)
    private val subColors = IntArray(MAX)
    /** 화면 왼쪽부터 k 번째 아이 = 플레이어 order[k] (구역 위치 순서) */
    private val order = IntArray(MAX)
    private val kidX = FloatArray(MAX)
    private var zones: List<Zone> = emptyList()

    // ── 상태 ──
    private val jumpT = FloatArray(MAX) { -1f }
    private val jumpMs = FloatArray(MAX) { 400f }
    private val fallT = FloatArray(MAX) { -1f }
    private var fallMs = 520f
    private val out = BooleanArray(MAX)
    private val win = BooleanArray(MAX)
    private var cheerT = -1f
    private var cheerMs = 2000f
    /** 줄 위상 (0 바닥, 0.5 머리 위), 쌩쌩이 중, 판정 구간 빛 */
    var ropePhase = 0.5f
        private set
    private var lastPhase = 0.5f
    private var ropeSpeed = 0f
    private var doubleSpin = false
    private var glowOn = false
    private var glow = 0f
    private var hudText = ""
    private var hudPulse = 0f
    private var hudHot = false

    // ── 배치 (onSizeChanged) ──
    private var cy = 0f
    private var radius = 0f
    private var ground = 0f
    private var crank = 0f
    private var xL = 0f
    private var xR = 0f
    private var bandTop = 0f
    private var bandBottom = 0f
    private var kidU = u

    // ── 그리기 도구 ──
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val crownPath = Path()
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val kidName = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        isClickable = false
        isFocusable = false
        namePaint.textSize = 20f * u
        kidName.textSize = 13f * u
        subPaint.textSize = 15f * u
        hudPaint.textSize = 15f * u
        stampPaint.textSize = 15f * u
    }

    /** 플레이어 이름·색 (players 순서) */
    fun setup(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size
        for (i in 0 until n) {
            names[i] = playerNames[i]
            colors[i] = playerColors[i]
            subs[i] = ""
            subColors[i] = Theme.TEXT_SUB
        }
        relayout()
    }

    /** 구역 아래 상태 글자 */
    fun setSub(i: Int, text: String, color: Int = Theme.TEXT_SUB) {
        subs[i] = text
        subColors[i] = color
        invalidate()
    }

    fun sub(i: Int): String = subs[i] ?: ""

    /** 띠 위 가운데 글자 (넘은 횟수·속도), hot = 빨갛게(쌩쌩이 예고) */
    fun setHud(text: String, hot: Boolean, pulse: Boolean) {
        hudText = text
        hudHot = hot
        if (pulse) hudPulse = 1f
        invalidate()
    }

    /** 줄 상태 (프레임마다) */
    fun setRope(phase: Float, double: Boolean, window: Boolean) {
        ropePhase = phase
        doubleSpin = double
        glowOn = window
    }

    /** 점프 (durMs 동안) */
    fun jump(i: Int, durMs: Long) {
        if (fallT[i] >= 0f) return
        jumpT[i] = 0f
        jumpMs[i] = durMs.coerceAtLeast(60).toFloat()
    }

    fun jumping(i: Int): Boolean = jumpT[i] in 0f..0.999f

    /** 걸려서 넘어짐 (누운 채로 남는다) */
    fun fall(i: Int, durMs: Long) {
        out[i] = true
        if (fallT[i] >= 0f) return
        jumpT[i] = -1f
        fallT[i] = 0f
        fallMs = durMs.coerceAtLeast(1).toFloat()
    }

    fun fallen(i: Int): Boolean = fallT[i] >= 0f

    /** 탈락 표시 (구역 어둡게) — 넘어지는 건 fall */
    fun setOut(i: Int) {
        out[i] = true
        invalidate()
    }

    /** 최후 1인: 만세 + 왕관 */
    fun setWinner(i: Int, durMs: Long) {
        win[i] = true
        cheerT = 0f
        cheerMs = durMs.coerceAtLeast(1).toFloat()
    }

    /**
     * 시간 dtMs 만큼 연출을 진행하고 다시 그린다. 아직 움직일 게 남았으면 true.
     * (줄 위상은 setRope 로 따로 들어온다)
     */
    fun step(dtMs: Float): Boolean {
        var busy = false
        for (i in 0 until n) {
            if (jumpT[i] >= 0f && jumpT[i] < 1f) {
                jumpT[i] = (jumpT[i] + dtMs / jumpMs[i]).coerceAtMost(1f)
                busy = true
            }
            if (fallT[i] >= 0f && fallT[i] < 1f) {
                fallT[i] = (fallT[i] + dtMs / fallMs).coerceAtMost(1f)
                busy = true
            }
        }
        if (cheerT in 0f..0.999f) {
            cheerT = (cheerT + dtMs / cheerMs).coerceAtMost(1f)
            busy = true
        }
        // 줄 빠르기 (잔상 길이): 위상 변화량을 부드럽게
        var d = ropePhase - lastPhase
        if (d < -0.5f) d += 1f
        if (d < 0f) d = 0f
        lastPhase = ropePhase
        val target = if (dtMs > 0f) d / dtMs * 16f else 0f
        ropeSpeed += (target - ropeSpeed) * 0.4f
        if (ropeSpeed < 0.0005f) ropeSpeed = 0f
        val g = if (glowOn) 1f else 0f
        glow += (g - glow) * (1f - exp(-dtMs / 40f))
        if (glow < 0.01f) glow = 0f
        hudPulse *= exp(-dtMs / 240f)
        if (hudPulse < 0.01f) hudPulse = 0f else busy = true
        invalidate()
        return busy || glow > 0f || ropeSpeed > 0f
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || n == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        zones = TouchZones.layout(n, w, h, Theme.dpf(context, 10f))
        // 아이 크기: 화면 높이에 맞춰 (360dp 기준 1배)
        kidU = u * (h / (360f * u)).coerceIn(0.75f, 1.25f)
        radius = 47f * kidU
        crank = 7f * kidU
        cy = h / 2f - 6f * kidU
        ground = cy + radius
        bandTop = cy - radius - 20f * kidU
        bandBottom = ground + 28f * kidU
        xL = 34f * u
        xR = w - 34f * u
        // 아이 위치: 구역 가로 위치 순서 (같으면 번호 순)
        for (k in 0 until n) order[k] = k
        for (a in 0 until n) for (b in 0 until n - 1 - a) {
            val p0 = order[b]
            val p1 = order[b + 1]
            val x0 = zones[p0].centerX
            val x1 = zones[p1].centerX
            if (x0 > x1 + 0.5f || (kotlin.math.abs(x0 - x1) <= 0.5f && p0 > p1)) {
                order[b] = p1
                order[b + 1] = p0
            }
        }
        val gapX = min(if (n == 2) 150f * u else 98f * u, (xR - xL) * 0.6f / (n - 1))
        for (k in 0 until n) kidX[order[k]] = w / 2f + (k - (n - 1) / 2f) * gapX
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        if (w <= 0f || n == 0 || zones.isEmpty()) return
        drawZoneShade(c)
        drawBand(c, w)
        // 줄 잔상 + 줄 (뒤쪽 것은 아이들 뒤에)
        val col = if (doubleSpin) RopeArt.ROPE_DOUBLE else RopeArt.ROPE
        val trail = (ropeSpeed * 1.6f).coerceAtMost(0.09f)
        drawRopes(c, col, trail, false)
        RopeArt.pole(c, p, xL, cy, ground, kidU, crank, ropePhase)
        RopeArt.pole(c, p, xR, cy, ground, kidU, crank, ropePhase)
        drawKids(c)
        drawRopes(c, col, trail, true)
        drawStamps(c)
        drawHud(c, w)
        drawLabels(c)
    }

    /** 잔상 2개 + 본 줄: front = 앞쪽 줄만 / 뒤쪽 줄만 */
    private fun drawRopes(c: Canvas, col: Int, trail: Float, front: Boolean) {
        val th = 5.5f * kidU
        if (trail > 0f) {
            for (k in 2 downTo 1) {
                var ph = ropePhase - trail * k
                if (ph < 0f) ph += 1f
                if (RopeArt.inFront(ph) == front) RopeArt.rope(c, p, xL, xR, cy, radius, crank, ground, ph, th * 0.8f, col, if (k == 2) 45 else 95)
            }
        }
        if (RopeArt.inFront(ropePhase) == front) RopeArt.rope(c, p, xL, xR, cy, radius, crank, ground, ropePhase, th, col, 255)
    }

    private fun drawZoneShade(c: Canvas) {
        val r = 18f * u
        val inset = 4f * u
        band.style = Paint.Style.FILL
        band.color = 0x99070D2E.toInt()
        for (zi in 0 until zones.size) {
            val z = zones[zi]
            if (!out[z.index]) continue
            rect.set(z.left + inset, z.top + inset, z.right - inset, z.bottom - inset)
            c.drawRoundRect(rect, r, r, band)
        }
    }

    private fun drawBand(c: Canvas, w: Float) {
        rect.set(6f * u, bandTop, w - 6f * u, bandBottom)
        band.style = Paint.Style.FILL
        band.color = 0xD90A1240.toInt()
        c.drawRoundRect(rect, 18f * u, 18f * u, band)
        // 바닥 (운동장 흙)
        c.save()
        c.clipRect(6f * u, ground, w - 6f * u, bandBottom)
        band.color = 0x55C98B54
        c.drawRoundRect(rect, 18f * u, 18f * u, band)
        c.restore()
        band.style = Paint.Style.STROKE
        band.strokeWidth = 1.5f * u
        band.color = Theme.CARD_BORDER
        c.drawRoundRect(rect, 18f * u, 18f * u, band)
        // 땅 선 + 판정 구간 빛 (줄이 바닥에 닿을 때 금색)
        band.strokeWidth = 2f * u
        band.color = 0x44FFFFFF
        c.drawLine(xL, ground, xR, ground, band)
        if (glow > 0f) {
            band.color = Theme.GOLD
            band.alpha = (glow * 150f).toInt()
            band.strokeWidth = 7f * u
            c.drawLine(xL + 20f * u, ground + 1f * u, xR - 20f * u, ground + 1f * u, band)
            band.alpha = (glow * 255f).toInt()
            band.strokeWidth = 2.5f * u
            c.drawLine(xL + 20f * u, ground + 1f * u, xR - 20f * u, ground + 1f * u, band)
            band.alpha = 255
        }
    }

    private fun drawKids(c: Canvas) {
        val cheer = if (cheerT >= 0f) cheerT else 0f
        for (k in 0 until n) {
            val i = order[k]
            val x = kidX[i]
            var lift = 0f
            var tuck = 0f
            var arms = 0f
            var fall = 0f
            if (fallT[i] >= 0f) {
                // 넘어짐: 살짝 걸려 튀었다가 눕는다
                val f = fallT[i]
                fall = f * f * (3f - 2f * f)
                lift = RopeArt.hop(f * 0.9f) * 6f * kidU
                arms = 0.6f * (1f - f)
            } else if (jumpT[i] >= 0f) {
                val hgt = RopeArt.hop(jumpT[i])
                lift = hgt * 26f * kidU
                tuck = hgt
                arms = hgt * 0.75f
            }
            if (win[i]) {
                lift += RopeArt.bounce(cheer * 6f) * 10f * kidU * (1f - cheer * 0.5f)
                arms = 1f
            }
            RopeArt.kid(c, p, x, ground, kidU, colors[i], lift, tuck, arms, fall, out[i] && fallT[i] >= 0f)
            if (win[i]) {
                p.style = Paint.Style.FILL
                p.color = Theme.GOLD
                Ui.drawCrown(c, x, ground - lift - 59f * kidU, 22f * kidU, crownPath, p)
            }
            // 이름 (발 아래)
            kidName.color = if (out[i]) Theme.GRAY else colors[i]
            c.drawText(names[i] ?: "", x, ground + 17f * kidU, kidName)
        }
    }

    private fun drawStamps(c: Canvas) {
        for (i in 0 until n) {
            val f = fallT[i]
            if (f < 0.35f) continue
            val e = ((f - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val s = 1.6f - 0.6f * e
            RopeArt.stamp(c, p, stampPaint, kidX[i] + 24f * kidU, ground - 30f * kidU, kidU, s, (255 * e).toInt(), STAMP)
        }
    }

    private fun drawHud(c: Canvas, w: Float) {
        if (hudText.isEmpty()) return
        val s = 1f + 0.16f * hudPulse
        val cx = w / 2f
        val y = bandTop
        val hw = (hudPaint.measureText(hudText) / 2f + 16f * u) * s
        val hh = 14f * u * s
        rect.set(cx - hw, y - hh, cx + hw, y + hh)
        band.style = Paint.Style.FILL
        band.color = if (hudHot) Theme.RED_DARK else Theme.BG_DEEP
        c.drawRoundRect(rect, hh, hh, band)
        band.style = Paint.Style.STROKE
        band.strokeWidth = 2f * u
        band.color = if (hudHot) Theme.RED else Theme.GOLD
        c.drawRoundRect(rect, hh, hh, band)
        c.save()
        c.scale(s, s, cx, y)
        hudPaint.color = if (hudHot) Theme.GOLD_LIGHT else Theme.TEXT
        c.drawText(hudText, cx, y + hudPaint.textSize * 0.36f, hudPaint)
        c.restore()
    }

    /** 구역 바깥쪽 이름 + 상태 (위쪽 구역은 180°) */
    private fun drawLabels(c: Canvas) {
        for (zi in 0 until zones.size) {
            val z = zones[zi]
            val i = z.index
            val top = z.rotation == 180f
            val x = z.centerX
            val y = if (top) z.top + 50f * u else z.bottom - 40f * u
            c.save()
            c.rotate(z.rotation, x, y)
            namePaint.color = if (out[i]) Theme.GRAY else colors[i]
            c.drawText(names[i] ?: "", x, y, namePaint)
            val sub = subs[i] ?: ""
            if (sub.isNotEmpty()) {
                subPaint.color = subColors[i]
                c.drawText(sub, x, y + 21f * u, subPaint)
            }
            c.restore()
        }
    }

    /** 테스트용: 플레이어 i 아이의 가로 위치 */
    fun kidCenterX(i: Int): Float = kidX[i]

    companion object {
        private const val MAX = 4
        private const val STAMP = "탈락"
    }
}
