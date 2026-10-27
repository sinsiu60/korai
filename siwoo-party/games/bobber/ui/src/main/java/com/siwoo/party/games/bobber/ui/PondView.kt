package com.siwoo.party.games.bobber.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bobber.BiteSchedule
import com.siwoo.party.games.bobber.GameConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin

/**
 * 연못 그림 층 (MultiTouchZoneView 위에 겹침, 터치는 아래 구역이 받는다).
 * 구역마다 같은 연못·같은 찌를 그 사람 방향으로 돌려 그린다 (위쪽 구역 180°).
 * 찌 움직임은 라운드 예정표(BiteSchedule)의 시각 함수라 모든 구역이 똑같이 움직인다.
 * 시간은 라운드 시작 기준 ms (setNow) — 게임 시계라 일시정지하면 그대로 멈춘다.
 * 구역별: 실격(회색 + "너무 빨라요!"), 아래 글자, 잡은 물고기가 튀어 오르는 연출, 머리(이름·마릿수).
 */
class PondView(context: Context) : View(context) {

    private val dp = Theme.dpf(context, 1f)
    private var n = 0
    private var zones: List<Zone> = emptyList()
    private val names = ArrayList<String>()
    private var colors = IntArray(0)
    private var scores = arrayOfNulls<String>(0)
    private var status = arrayOfNulls<String>(0)
    private var statusColor = IntArray(0)
    private var dq = BooleanArray(0)
    private var catchAt = LongArray(0)
    private var fishColor = IntArray(0)
    private var roundText = ""

    /** 이번 라운드 예정표 (null = 라운드 전, 찌는 가만히) */
    private var schedule: BiteSchedule? = null
    private var now = 0L
    /** 결과가 난 시각 (라운드 기준, -1 = 아직) → 찌가 다시 떠오름 */
    private var settleAt = -1L
    private var settleSink = 0f

    /** 연출 시간 (속도 배율 적용된 값) */
    var leapMs = GameConfig.FISH_LEAP_MS
    var rippleMs = GameConfig.RIPPLE_MS
    var resurfaceMs = GameConfig.RESURFACE_MS

    // ── 배치 (구역 안 좌표, 모든 구역 같은 크기) ──
    private var zw = 0f
    private var zh = 0f
    private var u = dp
    private var pad = 0f
    private var headerY = 0f
    private var statusY = 0f
    private val pond = RectF()
    private val bank = RectF()
    private var pondR = 0f
    private var bx = 0f
    private var wl = 0f
    private var br = 0f
    private var rodX0 = 0f
    private var rodY0 = 0f
    private var tipX = 0f
    private var tipY = 0f

    // ── 그리기 도구 ──
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val water = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT; color = Theme.GOLD }
    private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Theme.TEXT_SUB }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val bigEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF1A1030.toInt()
    }
    private val rect = RectF()

    init {
        isClickable = false
        isFocusable = false
    }

    fun setup(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size
        names.clear()
        names.addAll(playerNames)
        colors = playerColors.copyOf()
        scores = arrayOfNulls(n)
        for (i in 0 until n) scores[i] = ZERO
        status = arrayOfNulls(n)
        statusColor = IntArray(n) { Theme.TEXT_SUB }
        dq = BooleanArray(n)
        catchAt = LongArray(n) { -1L }
        fishColor = IntArray(n)
        relayout()
    }

    fun setScore(i: Int, text: String) { scores[i] = text; invalidate() }

    fun setRound(text: String) { roundText = text; invalidate() }

    /** 구역 아래 글자 */
    fun setStatus(i: Int, text: String?, color: Int = Theme.TEXT_SUB) {
        status[i] = text
        statusColor[i] = color
        invalidate()
    }

    /** 모든 구역 아래 글자 */
    fun setStatusAll(text: String?, color: Int = Theme.TEXT_SUB) {
        for (i in 0 until n) { status[i] = text; statusColor[i] = color }
        invalidate()
    }

    /** 이번 라운드 실격 (회색 + "너무 빨라요!") */
    fun setDisqualified(i: Int, v: Boolean) { dq[i] = v; invalidate() }

    fun isDisqualified(i: Int): Boolean = dq[i]

    /** 새 라운드 (예정표 null = 라운드 전) — 구역 표시 초기화 */
    fun newRound(s: BiteSchedule?) {
        schedule = s
        now = 0L
        settleAt = -1L
        for (i in 0 until n) { dq[i] = false; catchAt[i] = -1L }
        invalidate()
    }

    /** 라운드 기준 지금 시각 → 다시 그림 */
    fun setNow(t: Long) {
        now = t
        invalidate()
    }

    /** 결과가 남: 찌가 t 시각 모습에서 다시 떠오른다 */
    fun settle(t: Long) {
        settleSink = schedule?.sinkAt(t) ?: 0f
        settleAt = t
        invalidate()
    }

    /** 구역 i 가 물고기를 낚음 (t 시각부터 튀어 오름) */
    fun catchFish(i: Int, t: Long, color: Int) {
        catchAt[i] = t
        fishColor[i] = color
        invalidate()
    }

    /** 연출이 남았는지 (결과 뒤 프레임을 더 돌릴지) */
    fun busy(t: Long): Boolean {
        // 다시 떠오름 + 진짜 입질 물결이 남은 동안
        if (settleAt >= 0 && t - settleAt < resurfaceMs + rippleMs * 2) return true
        for (i in 0 until n) if (catchAt[i] >= 0 && t - catchAt[i] < leapMs + rippleMs) return true
        return false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || n == 0) return
        zones = TouchZones.layout(n, width.toFloat(), height.toFloat(), Theme.dpf(context, 10f))
        val z = zones[0]
        zw = z.width
        zh = z.height
        val k = minOf(zw / (300f * dp), zh / (165f * dp)).coerceIn(0.72f, 1.4f)
        u = dp * k
        pad = 12f * u
        namePaint.textSize = 16f * u
        scorePaint.textSize = 15f * u
        roundPaint.textSize = 13f * u
        statusPaint.textSize = 14f * u
        bigPaint.textSize = 21f * u
        bigEdge.textSize = 21f * u
        bigEdge.strokeWidth = 4.5f * u
        headerY = 9f * u + 17f * u
        statusY = zh - 9f * u
        // 연못: 머리 아래 ~ 아래 글자 위
        pond.set(pad + 4f * u, headerY + 9f * u, zw - pad - 4f * u, statusY - statusPaint.textSize - 6f * u)
        bank.set(pond.left - 5f * u, pond.top - 4f * u, pond.right + 5f * u, pond.bottom + 5f * u)
        pondR = minOf(pond.height() * 0.42f, 34f * u)
        water.shader = LinearGradient(0f, pond.top, 0f, pond.bottom, BobberArt.WATER_TOP, BobberArt.WATER_BOTTOM, Shader.TileMode.CLAMP)
        // 찌: 연못 가운데에서 조금 오른쪽, 물 높이는 가운데보다 조금 아래
        br = minOf(pond.height() * 0.14f, pond.width() * 0.075f)
        bx = pond.centerX() + pond.width() * 0.06f
        wl = pond.top + pond.height() * 0.6f
        // 낚싯대: 왼쪽 아래 물가 → 찌 왼쪽 위
        rodX0 = pond.left + pond.width() * 0.04f
        rodY0 = pond.bottom + 2f * u
        tipX = bx - pond.width() * 0.2f
        tipY = pond.top + pond.height() * 0.1f
        invalidate()
    }

    /** 구역 zone 의 찌 위치 (물 높이, 이 View 좌표) → out */
    fun bobberCenter(zone: Int, out: PointF) {
        val z = zones.getOrNull(zone) ?: return
        toView(z, bx, wl, out)
    }

    /** 구역 zone 의 연못 가운데 (이 View 좌표) → out */
    fun pondCenter(zone: Int, out: PointF) {
        val z = zones.getOrNull(zone) ?: return
        toView(z, pond.centerX(), pond.centerY(), out)
    }

    private fun toView(z: Zone, lx: Float, ly: Float, out: PointF) {
        if (z.rotation == 180f) out.set(z.right - lx, z.bottom - ly) else out.set(z.left + lx, z.top + ly)
    }

    /** 지금 찌 잠김 정도 */
    private fun sinkNow(): Float {
        val s = schedule ?: return 0f
        if (settleAt >= 0) {
            val f = ((now - settleAt).toFloat() / resurfaceMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            return settleSink * (1f - f) * (1f - f)
        }
        return s.sinkAt(now)
    }

    private fun tiltNow(): Float {
        val s = schedule ?: return 0f
        if (settleAt >= 0) return 0f
        return s.tiltAt(now)
    }

    override fun onDraw(c: Canvas) {
        if (zones.isEmpty()) return
        val sink = sinkNow()
        val tilt = tiltNow()
        for (zi in zones.indices) {
            val z = zones[zi]
            val i = z.index
            if (i >= n) continue
            c.save()
            c.rotate(z.rotation, z.centerX, z.centerY)
            c.translate(z.left, z.top)
            drawZone(c, i, sink, tilt)
            c.restore()
        }
    }

    private fun drawZone(c: Canvas, i: Int, sink: Float, tilt: Float) {
        // ── 머리: 이름 + 물고기 아이콘 + 마릿수, 오른쪽 라운드 ──
        namePaint.color = colors[i]
        val name = names[i]
        c.drawText(name, pad, headerY, namePaint)
        val fx = pad + namePaint.measureText(name) + 18f * u
        val fy = headerY - scorePaint.textSize * 0.36f
        BobberArt.fish(c, p, fx, fy, 22f * u, Theme.GOLD)
        val s = scores[i]
        if (s != null) c.drawText(s, fx + 14f * u, headerY, scorePaint)
        if (roundText.isNotEmpty()) c.drawText(roundText, zw - pad, headerY, roundPaint)

        // ── 연못 ──
        p.style = Paint.Style.FILL
        p.color = BobberArt.BANK_DARK
        rect.set(bank.left, bank.top + 3f * u, bank.right, bank.bottom + 3f * u)
        c.drawRoundRect(rect, pondR + 5f * u, pondR + 5f * u, p)
        p.color = BobberArt.BANK
        c.drawRoundRect(bank, pondR + 5f * u, pondR + 5f * u, p)
        water.alpha = 255
        c.drawRoundRect(pond, pondR, pondR, water)
        // 물빛 줄
        line.color = 0x33FFFFFF
        line.strokeWidth = 2f * u
        val pw = pond.width()
        val ph = pond.height()
        c.drawLine(pond.left + pw * 0.12f, pond.top + ph * 0.22f, pond.left + pw * 0.3f, pond.top + ph * 0.22f, line)
        c.drawLine(pond.left + pw * 0.62f, pond.top + ph * 0.3f, pond.left + pw * 0.86f, pond.top + ph * 0.3f, line)
        c.drawLine(pond.left + pw * 0.66f, pond.top + ph * 0.84f, pond.left + pw * 0.8f, pond.top + ph * 0.84f, line)
        // 연잎·부들
        BobberArt.lilyPad(c, p, pond.left + pw * 0.2f, pond.top + ph * 0.78f, ph * 0.13f)
        BobberArt.lilyPad(c, p, pond.right - pw * 0.13f, pond.top + ph * 0.58f, ph * 0.09f)
        BobberArt.reed(c, p, pond.right - pw * 0.04f, pond.bottom + 2f * u, ph * 0.5f, -ph * 0.05f)
        BobberArt.reed(c, p, pond.right - pw * 0.09f, pond.bottom + 3f * u, ph * 0.38f, -ph * 0.1f)

        // ── 물결: 가짜 입질(작게)·진짜 입질(크게) ──
        drawRipples(c)

        // ── 찌 ──
        val cy = wl - br * 0.3f + sink * br * 3.0f
        val tiltDeg = tilt * 16f
        c.save()
        // 깊이 잠긴 찌가 연못 밖(물가·글자)으로 나오지 않게
        c.clipRect(pond.left, 0f, pond.right, pond.bottom - 2f * u)
        c.rotate(tiltDeg, bx, wl)
        BobberArt.bobber(c, p, bx, cy, br)
        c.restore()
        // 물 아래 부분은 물빛으로 덮음 (잠긴 찌가 흐릿하게 보임)
        c.save()
        c.clipRect(bx - br * 2.2f, wl, bx + br * 2.2f, pond.bottom)
        water.alpha = 205
        c.drawRoundRect(pond, pondR, pondR, water)
        water.alpha = 255
        c.restore()
        // 수면 고리
        BobberArt.ripple(c, p, bx, wl, br * (1.05f + 0.15f * abs(sink)), 120, 1.6f * u)

        // ── 낚싯줄 + 낚싯대 ──
        val tipUp = BobberArt.TIP_UP * br
        val ax = bx + sin(tiltDeg * PI / 180.0).toFloat() * (wl - (cy - tipUp))
        val ay = minOf(cy - tipUp, wl + br * 0.2f)
        line.color = 0xCCFFFFFF.toInt()
        line.strokeWidth = 1.2f * u
        c.drawLine(tipX, tipY, ax, ay, line)
        line.color = BobberArt.ROD_DARK
        line.strokeWidth = 5.5f * u
        c.drawLine(rodX0, rodY0, rodX0 + (tipX - rodX0) * 0.35f, rodY0 + (tipY - rodY0) * 0.35f, line)
        line.color = BobberArt.ROD
        line.strokeWidth = 3f * u
        c.drawLine(rodX0, rodY0, tipX, tipY, line)

        // ── 낚은 물고기가 튀어 오름 ──
        if (catchAt[i] >= 0) drawLeap(c, i)

        // ── 실격: 회색 + 큰 글자 ──
        if (dq[i]) {
            p.style = Paint.Style.FILL
            p.color = 0xC8384058.toInt()
            c.drawRoundRect(bank, pondR + 5f * u, pondR + 5f * u, p)
            val ty = pond.centerY() + bigPaint.textSize * 0.35f
            bigPaint.color = Theme.RED
            c.drawText(TOO_FAST, pond.centerX(), ty, bigEdge)
            c.drawText(TOO_FAST, pond.centerX(), ty, bigPaint)
        }

        // ── 아래 글자 ──
        val st = status[i]
        if (st != null) {
            statusPaint.color = statusColor[i]
            c.drawText(st, zw / 2f, statusY, statusPaint)
        }
    }

    private fun drawRipples(c: Canvas) {
        val s = schedule ?: return
        val rm = rippleMs.coerceAtLeast(1)
        for (k in 0 until s.fakeCount) {
            for (j in 0 until 2) {
                val t0 = s.fakeAt[k] + j * GameConfig.FAKE_MS / 2
                val f = (now - t0).toFloat() / rm
                if (f in 0f..1f) BobberArt.ripple(c, p, bx, wl, br * (1.2f + 2.2f * f), ((1f - f) * 170).toInt(), 1.8f * u)
            }
        }
        for (j in 0 until 3) {
            val f = (now - s.biteAt - j * 140).toFloat() / (rm * 1.3f)
            if (f in 0f..1f) BobberArt.ripple(c, p, bx, wl, br * (1.4f + 4.6f * f), ((1f - f) * 230).toInt(), 2.6f * u)
        }
    }

    /** 물고기가 찌 자리에서 오른쪽 위로 튀어 올랐다가 물에 떨어짐 + "+1" */
    private fun drawLeap(c: Canvas, i: Int) {
        val t = now - catchAt[i]
        if (t < 0) return
        val lm = leapMs.coerceAtLeast(1).toFloat()
        val f = t / lm
        val x0 = bx
        val x1 = bx + pond.width() * 0.3f
        val y0 = wl
        val y1 = wl + pond.height() * 0.12f
        val hgt = (wl - pond.top) * 0.85f
        if (f <= 1f) {
            val x = x0 + (x1 - x0) * f
            val y = y0 + (y1 - y0) * f - hgt * 4f * f * (1f - f)
            // 진행 방향 (미분) 으로 몸을 돌림
            val dx = x1 - x0
            val dy = (y1 - y0) - hgt * 4f * (1f - 2f * f)
            val ang = (atan2(dy, dx) * 180.0 / PI).toFloat()
            c.save()
            c.rotate(ang, x, y)
            BobberArt.fish(c, p, x, y, br * 3.2f, fishColor[i])
            c.restore()
            // 튀는 물방울
            if (f < 0.35f) {
                val g = f / 0.35f
                p.style = Paint.Style.FILL
                p.color = 0xFFFFFFFF.toInt()
                p.alpha = ((1f - g) * 230).toInt()
                for (k in 0 until 5) {
                    val a = (k - 2) * 0.45f
                    c.drawCircle(x0 + a * br * 2.4f * g, y0 - br * 2.6f * g * (1.2f - abs(a) * 0.4f) + br * 2f * g * g, br * 0.22f, p)
                }
                p.alpha = 255
            }
        } else {
            // 떨어진 자리 물결
            val g = (t - lm) / rippleMs.coerceAtLeast(1)
            if (g in 0f..1f) BobberArt.ripple(c, p, x1, y1, br * (1f + 2.5f * g), ((1f - g) * 200).toInt(), 2f * u)
        }
        // "+1" 이 떠오름
        if (f <= 1.3f) {
            val a = if (f < 1f) 255 else ((1.3f - f) / 0.3f * 255).toInt()
            val py = pond.top + bigPaint.textSize * 1.1f - minOf(f, 1f) * 6f * u
            bigPaint.color = Theme.GOLD
            bigPaint.alpha = a
            bigEdge.alpha = a
            c.drawText(PLUS1, pond.left + pond.width() * 0.25f, py, bigEdge)
            c.drawText(PLUS1, pond.left + pond.width() * 0.25f, py, bigPaint)
            bigPaint.alpha = 255
            bigEdge.alpha = 255
        }
    }

    companion object {
        private const val ZERO = "0마리"
        private const val PLUS1 = "+1"
        private const val TOO_FAST = "너무 빨라요!"
    }
}
