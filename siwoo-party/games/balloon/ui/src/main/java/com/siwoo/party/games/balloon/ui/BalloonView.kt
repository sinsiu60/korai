package com.siwoo.party.games.balloon.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.balloon.BalloonLayout
import com.siwoo.party.games.balloon.BalloonState
import com.siwoo.party.games.balloon.GameConfig
import kotlin.math.exp
import kotlin.math.sin

/**
 * 풍선 판 (MultiTouchZoneView 위에 겹치는 그림 층, 터치는 아래 구역으로 그대로 전달).
 * 구역마다 같은 배치(BalloonLayout)를 구역 방향으로 돌려 그린다 (위쪽 구역 180°):
 * 위: 이름 · 라운드 · 합계, 시간 막대 / 왼쪽: 풍선(꼭지 고정, 위로 부풂, 클수록 떨림) / 오른쪽: 크기 숫자 + [멈춤] 버튼.
 * 움직임은 step(dtMs) 로 진행 (화면이 라운드 중·연출 중에만 프레임을 돌린다). onDraw 는 할당 없음.
 */
class BalloonView(context: Context) : View(context) {

    private val dp = Theme.dpf(context, 1f)
    val layout = BalloonLayout()
    private var zones: List<Zone> = emptyList()
    private var n = 0

    // ── 플레이어 정보 ──
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    /** 매듭(어둡게)·터진 조각 색 — setup 에서 미리 */
    private var knotColors = IntArray(0)
    private var shredColors = IntArray(0)
    private val redBase = Theme.shade(Theme.RED, 0.45f)
    private val grayBase = Theme.shade(Theme.GRAY, 0.55f)
    private var totals: Array<String> = emptyArray()
    private var roundLabel = ""

    // ── 상태 ──
    private var sizes = IntArray(0)
    private var states: Array<BalloonState> = emptyArray()
    /** 시간 끝 자동 확정 */
    private var auto = BooleanArray(0)
    /** 라운드 결과 표시 중이면 이번 라운드 점수, 아니면 -1 */
    private var result = IntArray(0)
    /** 화면 반지름 (목표를 부드럽게 따라감) */
    private var shownR = FloatArray(0)
    private var kick = FloatArray(0)
    /** 터짐·도장 연출 진행 0..1 (−1 = 없음) */
    private var popT = FloatArray(0)
    private var stampT = FloatArray(0)
    /** 라운드 진행 중 (떨림·버튼 활성) */
    var playing = false
        set(v) { field = v; invalidate() }
    private var timeFrac = 1f
    private var hurry = false

    // ── 연출 시간 (속도 배율 적용된 값을 화면이 넣는다) ──
    var easeMs = GameConfig.GROW_EASE_MS.toFloat()
    var popMs = GameConfig.POP_ANIM_MS.toFloat()
    var stampMs = GameConfig.STAMP_ANIM_MS.toFloat()

    // ── 미리 만든 글자 ──
    private val sizeStr = Array(GameConfig.POP_MAX + 2) { it.toString() }
    private val plusStr = Array(GameConfig.POP_MAX + 2) { "+$it" }

    // ── 그리기 도구 ──
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val balloonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Theme.GOLD }
    private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF0A0F2E.toInt()
    }
    private val rect = RectF()
    private val path = Path()
    private val m = Matrix()
    private var shaders: Array<RadialGradient> = emptyArray()
    private var redFace: LinearGradient? = null
    private var goldFace: LinearGradient? = null
    private val tmp = FloatArray(2)

    init {
        isClickable = false
        isFocusable = false
    }

    fun setup(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size
        names = playerNames.toTypedArray()
        colors = playerColors
        knotColors = IntArray(n) { Theme.shade(colors[it], 0.6f) }
        shredColors = IntArray(n) { Theme.shade(colors[it], 0.8f) }
        totals = Array(n) { "합계 0" }
        sizes = IntArray(n)
        states = Array(n) { BalloonState.INFLATING }
        auto = BooleanArray(n)
        result = IntArray(n) { -1 }
        shownR = FloatArray(n)
        kick = FloatArray(n)
        popT = FloatArray(n) { -1f }
        stampT = FloatArray(n) { -1f }
        // 반지름 1 짜리 입체 그라데이션 (그릴 때 행렬로 크기만 맞춘다)
        shaders = Array(n) {
            val c = colors[it]
            RadialGradient(-0.35f, -0.45f, 1.45f, intArrayOf(Theme.shade(c, 1.55f), c, Theme.shade(c, 0.55f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        relayout()
    }

    fun setRound(label: String) { roundLabel = label; invalidate() }

    fun setTotal(i: Int, total: Int) { totals[i] = "합계 $total"; invalidate() }

    /** 새 라운드: 모두 크기 0 */
    fun resetRound() {
        for (i in 0 until n) {
            sizes[i] = 0
            states[i] = BalloonState.INFLATING
            auto[i] = false
            result[i] = -1
            shownR[i] = layout.rMin
            kick[i] = 0f
            popT[i] = -1f
            stampT[i] = -1f
        }
        timeFrac = 1f
        hurry = false
        invalidate()
    }

    /** 탭 → 크기 바뀜 (통통 튀는 연출) */
    fun grow(i: Int, size: Int) {
        sizes[i] = size
        kick[i] = 1f
        invalidate()
    }

    /** 멈춤 확정 (autoByTime = 시간 끝 자동 확정) */
    fun stamp(i: Int, size: Int, autoByTime: Boolean) {
        sizes[i] = size
        states[i] = BalloonState.STOPPED
        auto[i] = autoByTime
        stampT[i] = 0f
        invalidate()
    }

    fun pop(i: Int, size: Int) {
        sizes[i] = size
        states[i] = BalloonState.POPPED
        popT[i] = 0f
        invalidate()
    }

    /** 라운드 결과: 구역마다 이번 점수 */
    fun showResult(i: Int, score: Int) { result[i] = score; invalidate() }

    fun setTime(frac: Float, hurrying: Boolean) {
        if (frac == timeFrac && hurrying == hurry) return
        timeFrac = frac
        hurry = hurrying
        invalidate()
    }

    /** 풍선 가운데 (이 View 좌표) — 터지는 연출 위치 */
    fun balloonCenter(i: Int, out: PointF) {
        val z = zones.getOrNull(i) ?: return
        BalloonLayout.toScreen(z.centerX, z.centerY, z.rotation, layout.balloonX, layout.centerY(sizes[i]), tmp)
        out.set(tmp[0], tmp[1])
    }

    /** 연출 진행. 아직 움직이는 게 있으면 true */
    fun step(dtMs: Float): Boolean {
        var busy = false
        val ease = 1f - exp(-dtMs / easeMs.coerceAtLeast(1f))
        for (i in 0 until n) {
            val target = layout.radius(sizes[i])
            val d = target - shownR[i]
            if (kotlin.math.abs(d) > 0.3f) { shownR[i] += d * ease; busy = true } else shownR[i] = target
            if (kick[i] > 0.01f) { kick[i] *= (1f - ease); busy = true } else kick[i] = 0f
            if (popT[i] in 0f..0.999f) { popT[i] = (popT[i] + dtMs / popMs.coerceAtLeast(1f)).coerceAtMost(1f); busy = true }
            if (stampT[i] in 0f..0.999f) { stampT[i] = (stampT[i] + dtMs / stampMs.coerceAtLeast(1f)).coerceAtMost(1f); busy = true }
        }
        invalidate()
        return busy
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || n == 0) return
        // 공통 MultiTouchZoneView 와 같은 구역 (간격 10dp)
        zones = TouchZones.layout(n, width.toFloat(), height.toFloat(), Theme.dpf(context, 10f))
        var minW = Float.MAX_VALUE
        var minH = Float.MAX_VALUE
        for (z in zones) { minW = minOf(minW, z.width); minH = minOf(minH, z.height) }
        // 구역 테두리 안쪽으로 여유
        layout.layout(minW - 16f * dp, minH - 16f * dp)
        val ch = layout.cellH
        namePaint.textSize = minOf(ch * 0.095f, 17f * dp)
        totalPaint.textSize = namePaint.textSize
        roundPaint.textSize = namePaint.textSize * 0.78f
        val colH = layout.stop.top - layout.timeBar.bottom
        bigPaint.textSize = colH * 0.56f
        outline.textSize = bigPaint.textSize
        outline.strokeWidth = bigPaint.textSize * 0.12f
        capPaint.textSize = colH * 0.2f
        btnPaint.textSize = minOf(layout.stop.height * 0.42f, 22f * dp)
        val s = layout.stop
        redFace = LinearGradient(0f, s.top, 0f, s.bottom, Theme.shade(Theme.RED, 1.3f), Theme.RED_DARK, Shader.TileMode.CLAMP)
        goldFace = LinearGradient(0f, s.top, 0f, s.bottom, Theme.GOLD_LIGHT, Theme.GOLD, Shader.TileMode.CLAMP)
        for (i in 0 until n) shownR[i] = layout.radius(sizes[i])
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (zones.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        for (z in zones) {
            val i = z.index
            c.save()
            c.translate(z.centerX, z.centerY)
            c.rotate(z.rotation)
            drawHeader(c, i)
            drawBalloon(c, i, now)
            drawColumn(c, i)
            drawButton(c, i)
            c.restore()
        }
    }

    private fun drawHeader(c: Canvas, i: Int) {
        val l = layout
        val x = l.cellW * 0.44f
        namePaint.color = colors[i]
        c.drawText(names[i], -x, l.headerY, namePaint)
        c.drawText(totals[i], x, l.headerY, totalPaint)
        c.drawText(roundLabel, 0f, l.headerY, roundPaint)
        // 시간 막대
        val b = l.timeBar
        val r = b.height / 2f
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        rect.set(b.left, b.top, b.right, b.bottom)
        c.drawRoundRect(rect, r, r, p)
        if (timeFrac > 0f) {
            p.color = when {
                hurry -> Theme.RED
                timeFrac < 0.5f -> Theme.GOLD
                else -> Theme.GREEN
            }
            rect.set(b.left, b.top, b.left + b.width * timeFrac, b.bottom)
            c.drawRoundRect(rect, r, r, p)
        }
    }

    private fun drawBalloon(c: Canvas, i: Int, now: Long) {
        val l = layout
        val state = states[i]
        val bx = l.balloonX
        val knot = l.knotY
        val stringEnd = l.cellH * 0.48f
        // 줄 (꼭지에서 아래로 살짝 굽은 선)
        line.color = 0xCCFFFFFF.toInt()
        line.strokeWidth = 1.6f * dp
        path.reset()
        path.moveTo(bx, knot)
        path.quadTo(bx + 7f * dp, (knot + stringEnd) / 2f, bx - 2f * dp, stringEnd)
        c.drawPath(path, line)
        if (state == BalloonState.POPPED) {
            drawPopped(c, i, bx, knot)
            return
        }
        val r = shownR[i] * (1f + 0.07f * kick[i])
        val ry = r * BalloonLayout.STRETCH
        var cx = bx
        var cy = knot - ry
        var tilt = 0f
        // 클수록 미세하게 떨림 (크기에만 비례)
        if (playing && state == BalloonState.INFLATING && sizes[i] > 0) {
            val f = sizes[i].toFloat() / GameConfig.POP_MAX
            val amp = GameConfig.WOBBLE_MAX_DP * dp * f * f
            val t = (now % 100_000L).toFloat()
            cx += amp * sin(t * 0.071f + i * 1.7f)
            cy += amp * 0.6f * sin(t * 0.093f + i * 2.3f)
            tilt = 2.5f * f * f * sin(t * 0.05f + i.toFloat())
        }
        c.save()
        c.rotate(tilt, bx, knot)
        // 그림자
        p.style = Paint.Style.FILL
        p.color = 0x40000000
        rect.set(cx - r + 3f * dp, cy - ry + 4f * dp, cx + r + 3f * dp, cy + ry + 4f * dp)
        c.drawOval(rect, p)
        // 몸통 (입체 그라데이션)
        val sh = shaders[i]
        m.setScale(r, ry)
        m.postTranslate(cx, cy)
        sh.setLocalMatrix(m)
        balloonPaint.shader = sh
        rect.set(cx - r, cy - ry, cx + r, cy + ry)
        c.drawOval(rect, balloonPaint)
        // 꼭지 매듭
        p.color = knotColors[i]
        path.reset()
        val kw = maxOf(r * 0.16f, 4f * dp)
        path.moveTo(bx - kw, knot + kw * 0.2f)
        path.lineTo(bx + kw, knot + kw * 0.2f)
        path.lineTo(cx, cy + ry - kw * 0.4f)
        path.close()
        c.drawPath(path, p)
        // 광택
        p.color = 0x66FFFFFF
        rect.set(cx - r * 0.55f, cy - ry * 0.72f, cx - r * 0.18f, cy - ry * 0.25f)
        c.drawOval(rect, p)
        p.color = 0x99FFFFFF.toInt()
        c.drawCircle(cx - r * 0.2f, cy - ry * 0.68f, r * 0.06f, p)
        c.restore()
        // 확정 리본 (멈춘 풍선 위 금색 테두리)
        if (state == BalloonState.STOPPED) {
            val a = stampT[i].coerceIn(0f, 1f)
            line.color = Theme.GOLD
            line.alpha = (255 * a).toInt()
            line.strokeWidth = 3f * dp
            rect.set(cx - r - 4f * dp, cy - ry - 4f * dp, cx + r + 4f * dp, cy + ry + 4f * dp)
            c.drawOval(rect, line)
            line.alpha = 255
        }
    }

    /** 터진 풍선: 꼭지에 남은 고무 조각 + "펑!" (조각 파티클은 FxView 가 날린다) */
    private fun drawPopped(c: Canvas, i: Int, bx: Float, knot: Float) {
        val l = layout
        val f = popT[i].coerceIn(0f, 1f)
        // 꼭지 고무 조각
        p.style = Paint.Style.FILL
        p.color = shredColors[i]
        val s = l.rMin * 0.9f
        path.reset()
        path.moveTo(bx - s * 0.25f, knot)
        path.lineTo(bx + s * 0.25f, knot)
        path.lineTo(bx + s * 0.7f, knot - s * 0.55f)
        path.lineTo(bx + s * 0.25f, knot - s * 0.4f)
        path.lineTo(bx + s * 0.1f, knot - s * 0.95f)
        path.lineTo(bx - s * 0.2f, knot - s * 0.45f)
        path.lineTo(bx - s * 0.75f, knot - s * 0.7f)
        path.close()
        c.drawPath(path, p)
        // "펑!" 글자: 튀어나오며 커짐
        val e = 1f - (1f - f) * (1f - f)
        val ts = l.rMax * (0.55f + 0.25f * e)
        val y = l.knotY - l.rMax * 1.1f
        outline.textSize = ts
        outline.strokeWidth = ts * 0.14f
        bigPaint.textSize = ts
        bigPaint.color = Theme.RED
        c.save()
        c.rotate(-8f, bx, y)
        c.drawText(POP_TEXT, bx, y + ts * 0.35f, outline)
        c.drawText(POP_TEXT, bx, y + ts * 0.35f, bigPaint)
        c.restore()
        // 크기 글자 원래대로
        val colH = l.stop.top - l.timeBar.bottom
        bigPaint.textSize = colH * 0.56f
        outline.textSize = bigPaint.textSize
        outline.strokeWidth = bigPaint.textSize * 0.12f
    }

    /** 오른쪽 위: 설명 + 크기 숫자 (결과 때는 +점수) */
    private fun drawColumn(c: Canvas, i: Int) {
        val l = layout
        val top = l.timeBar.bottom
        val colH = l.stop.top - top
        val x = l.sizeX
        val state = states[i]
        val res = result[i]
        val cap: String
        val num: String
        val color: Int
        if (res >= 0) {
            cap = CAP_RESULT
            num = if (res == 0) sizeStr[0] else plusStr[res.coerceAtMost(GameConfig.POP_MAX + 1)]
            color = if (res > 0) Theme.GOLD else Theme.RED
        } else {
            val sz = sizes[i].coerceAtMost(GameConfig.POP_MAX + 1)
            if (state == BalloonState.POPPED) {
                cap = CAP_POP; num = sizeStr[0]; color = Theme.RED
            } else if (state == BalloonState.STOPPED) {
                cap = if (auto[i]) CAP_TIME else CAP_STOP; num = sizeStr[sz]; color = Theme.GOLD
            } else {
                cap = CAP_SIZE; num = sizeStr[sz]; color = Theme.TEXT
            }
        }
        capPaint.color = if (state == BalloonState.POPPED && res < 0) Theme.RED else Theme.TEXT_SUB
        c.drawText(cap, x, top + colH * 0.3f, capPaint)
        bigPaint.color = color
        val base = top + colH * 0.86f
        c.drawText(num, x, base, outline)
        c.drawText(num, x, base, bigPaint)
    }

    /** [멈춤] 입체 버튼 (멈추면 금색 "확정" 도장, 터지면 회색) */
    private fun drawButton(c: Canvas, i: Int) {
        val s = layout.stop
        val depth = 4f * dp
        val rad = minOf(s.height * 0.3f, 14f * dp)
        val state = states[i]
        p.style = Paint.Style.FILL
        p.shader = null
        if (state == BalloonState.STOPPED) {
            // 도장: 살짝 크게 찍혔다가 제자리
            val a = stampT[i].coerceIn(0f, 1f)
            val sc = 1f + 0.35f * (1f - a) * (1f - a)
            c.save()
            c.scale(sc, sc, s.centerX, s.centerY)
            c.rotate(-6f * a, s.centerX, s.centerY)
            p.color = 0xFFFFFFFF.toInt() // 그라데이션은 paint 의 알파를 따르므로 불투명하게
            p.shader = goldFace
            rect.set(s.left, s.top, s.right, s.bottom)
            c.drawRoundRect(rect, rad, rad, p)
            p.shader = null
            line.color = 0xFFFFFFFF.toInt()
            line.strokeWidth = 2f * dp
            rect.inset(3f * dp, 3f * dp)
            c.drawRoundRect(rect, rad * 0.7f, rad * 0.7f, line)
            btnPaint.color = Theme.INK
            c.drawText(BTN_DONE, s.centerX, s.centerY + btnPaint.textSize * 0.36f, btnPaint)
            btnPaint.color = Theme.TEXT
            c.restore()
            return
        }
        val popped = state == BalloonState.POPPED
        val live = playing && !popped
        // 두께
        p.color = if (popped) grayBase else redBase
        rect.set(s.left, s.top + depth, s.right, s.bottom)
        c.drawRoundRect(rect, rad, rad, p)
        // 윗면
        p.color = if (popped) Theme.GRAY else 0xFFFFFFFF.toInt()
        if (!popped) p.shader = redFace
        if (!live) p.alpha = if (popped) 255 else 170
        rect.set(s.left, s.top, s.right, s.bottom - depth)
        c.drawRoundRect(rect, rad, rad, p)
        p.shader = null
        p.alpha = 255
        // 광택
        p.color = 0x30FFFFFF
        rect.set(s.left + rad * 0.5f, s.top + depth * 0.6f, s.right - rad * 0.5f, s.top + (s.height - depth) * 0.42f)
        c.drawRoundRect(rect, rad * 0.7f, rad * 0.7f, p)
        btnPaint.alpha = if (live) 255 else 160
        c.drawText(BTN_STOP, s.centerX, s.top + (s.height - depth) / 2f + btnPaint.textSize * 0.36f, btnPaint)
        btnPaint.alpha = 255
    }

    companion object {
        private const val POP_TEXT = "펑!"
        private const val BTN_STOP = "멈춤"
        private const val BTN_DONE = "확정!"
        private const val CAP_SIZE = "크기"
        private const val CAP_STOP = "확정"
        private const val CAP_TIME = "시간 끝"
        private const val CAP_POP = "펑! 0점"
        private const val CAP_RESULT = "이번 점수"
    }
}
