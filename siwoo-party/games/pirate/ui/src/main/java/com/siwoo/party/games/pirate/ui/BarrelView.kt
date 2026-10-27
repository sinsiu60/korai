package com.siwoo.party.games.pirate.ui

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.LinearInterpolator
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.pirate.GameConfig
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 가운데 나무통 + 위로 머리를 내민 해적.
 * - 통 앞면에 구멍 24개를 타원 고리로 (통 곡면을 따라 양옆은 좁게 보이도록) 배치한다.
 * - 구멍 터치는 가장 가까운 구멍으로 판정 (구멍 간격만큼 넉넉하게) → listener.onHolePicked
 * - 연출: 칼이 천천히 꽂힘(stab) → 해적이 떨기(suspense) → 해적이 회전하며 하늘로(fly) / 다시 쏙(drop)
 * 셰이더·경로는 크기가 정해질 때(onSizeChanged) 한 번 만들고 onDraw 에서는 할당하지 않는다.
 * 애니메이션 중에만 다시 그린다.
 */
class BarrelView(context: Context) : View(context) {

    interface Listener {
        fun onHolePicked(hole: Int)
    }

    var listener: Listener? = null
    /** false 면 터치를 받지 않는다 */
    var touchEnabled = true

    private val n = GameConfig.HOLES

    // ── 구멍 ──
    private val hx = FloatArray(n)
    private val hy = FloatArray(n)
    /** 칼이 나오는 방향 (도, 고리 가운데에서 바깥쪽) */
    private val hAngle = FloatArray(n)
    /** 곡면 때문에 가로로 좁아 보이는 정도 (1 = 정면) */
    private val hSquash = FloatArray(n)
    private var holeR = 0f
    private var hitR = 0f
    private val swordColor = IntArray(n)
    private val swordDark = IntArray(n)
    private val hasSword = BooleanArray(n)
    private val trapMark = BooleanArray(n)
    private var trapsShown = false
    private var hitHole = -1

    // ── 칼 꽂기 연출 ──
    private var stabHole = -1
    private var stabP = 1f

    // ── 해적 ──
    private var mood = PirateArt.NORMAL
    private var pirX = 0f
    private var pirY = 0f
    private var pirRot = 0f
    private var pirScale = 1f
    /** 날아가기 진행 (0..1, 날아가는 중이 아니면 0) */
    private var flyF = 0f
    private var tremble = 0f

    private var stabAnim: ValueAnimator? = null
    private var suspenseAnim: ValueAnimator? = null
    private var pirateAnim: ValueAnimator? = null

    // ── 모양 (onSizeChanged) ──
    private var cx = 0f
    private var top = 0f
    private var bodyH = 0f
    private var rimRx = 0f
    private var rimRy = 0f
    private var halfW = 0f
    private var headR = 0f
    private var headCy = 0f
    private var ringCy = 0f
    private val rimOval = RectF()
    private var glowR = 0f
    private var glowCy = 0f

    private val bodyPath = Path()
    private val stavePath = Path()
    private val staveLightPath = Path()
    private val bandPath = Path()
    private val rivetX = FloatArray(RIVETS * 2)
    private val rivetY = FloatArray(RIVETS * 2)
    private val scratch = Path()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val woodPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private var skinShader: Shader? = null
    private var stripeShader: Shader? = null

    // 고리 위치 계산용 (크기가 바뀔 때만 사용)
    private val sampleX = FloatArray(SAMPLES + 1)
    private val sampleY = FloatArray(SAMPLES + 1)
    private val sampleU = FloatArray(SAMPLES + 1)
    private val sampleLen = FloatArray(SAMPLES + 1)

    private val stabUpdate = ValueAnimator.AnimatorUpdateListener { stabP = it.animatedValue as Float; invalidate() }
    private val suspenseUpdate = ValueAnimator.AnimatorUpdateListener {
        val f = it.animatedValue as Float
        tremble = sin(f * PI * 2 * 9).toFloat() * headR * 0.06f * (1f - f * 0.3f)
        invalidate()
    }
    /** 위로 튀어 오르며 빙글빙글 → 하늘 멀리 작아지며 사라짐 */
    private val flyUpdate = ValueAnimator.AnimatorUpdateListener {
        val f = it.animatedValue as Float
        val e = 1f - (1f - f) * (1f - f)
        flyF = f
        pirY = (height * 0.07f - headCy) * e
        pirX = width * 0.3f * e
        pirRot = 1080f * e
        pirScale = (1f - f * 1.05f).coerceAtLeast(0f)
        invalidate()
    }
    private val dropUpdate = ValueAnimator.AnimatorUpdateListener {
        val f = it.animatedValue as Float
        pirY = -height * 0.9f * (1f - f)
        invalidate()
    }

    init {
        isClickable = true
        contentDescription = "나무통"
        hasSword.fill(false)
        line.strokeJoin = Paint.Join.ROUND
    }

    // ─────────────────────────────── 모양 계산

    /** 통 앞면 곡면 위의 점: u = 가로 -1..1, v = 세로 0(위)..1(아래) */
    private fun hw(v: Float): Float = rimRx + (halfW - rimRx) * sin(PI * v).toFloat()
    private fun sx(u: Float, v: Float): Float = cx + u * hw(v)
    private fun sy(u: Float, v: Float): Float = top + v * bodyH + rimRy * sqrt((1f - u * u).coerceAtLeast(0f))

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val W = w.toFloat()
        val H = h.toFloat()
        top = H * 0.31f
        val bottom = H * 0.965f
        val bh = bottom - top
        val bw = minOf(W * 0.94f, bh * 1.62f)
        cx = W / 2f
        halfW = bw / 2f
        rimRx = halfW * 0.84f
        rimRy = bh * 0.085f
        bodyH = bh - rimRy
        rimOval.set(cx - rimRx, top - rimRy, cx + rimRx, top + rimRy)
        headR = bw * 0.125f
        headCy = top - headR * 0.62f

        // 몸통 외곽
        bodyPath.reset()
        for (k in 0..STEPS) { // 위 (입구 앞쪽 가장자리) 왼→오
            val u = -1f + 2f * k / STEPS
            if (k == 0) bodyPath.moveTo(sx(u, 0f), sy(u, 0f)) else bodyPath.lineTo(sx(u, 0f), sy(u, 0f))
        }
        for (k in 1..STEPS) { val v = k.toFloat() / STEPS; bodyPath.lineTo(sx(1f, v), sy(1f, v)) }
        for (k in 1..STEPS) { val u = 1f - 2f * k / STEPS; bodyPath.lineTo(sx(u, 1f), sy(u, 1f)) }
        for (k in 1 until STEPS) { val v = 1f - k.toFloat() / STEPS; bodyPath.lineTo(sx(-1f, v), sy(-1f, v)) }
        bodyPath.close()

        // 나무 판자 (세로 줄)
        stavePath.reset()
        staveLightPath.reset()
        val dx = bw * 0.006f
        for (s in 1 until STAVES) {
            val u = -1f + 2f * s / STAVES
            for (k in 0..STEPS) {
                val v = k.toFloat() / STEPS
                val x = sx(u, v)
                val y = sy(u, v)
                if (k == 0) { stavePath.moveTo(x, y); staveLightPath.moveTo(x + dx, y) } else { stavePath.lineTo(x, y); staveLightPath.lineTo(x + dx, y) }
            }
        }
        // 쇠테 두 줄 (위·아래) + 리벳
        bandPath.reset()
        addBand(BAND_TOP, BAND_W, 0)
        addBand(BAND_BOTTOM, BAND_W, RIVETS)

        woodPaint.shader = LinearGradient(cx - halfW, 0f, cx + halfW, 0f,
            intArrayOf(PirateArt.WOOD_DARK, PirateArt.WOOD, PirateArt.WOOD_LIGHT, PirateArt.WOOD, 0xFF4A2A12.toInt()),
            floatArrayOf(0f, 0.22f, 0.42f, 0.7f, 1f), Shader.TileMode.CLAMP)
        bandPaint.shader = LinearGradient(cx - halfW, 0f, cx + halfW, 0f,
            intArrayOf(0xFF2E323B.toInt(), PirateArt.METAL, PirateArt.METAL_LIGHT, PirateArt.METAL, 0xFF272A31.toInt()),
            floatArrayOf(0f, 0.2f, 0.4f, 0.72f, 1f), Shader.TileMode.CLAMP)
        glowR = minOf(W / 2f, H * 0.57f)
        glowCy = top + bh * 0.4f
        glowPaint.shader = RadialGradient(cx, glowCy, glowR,
            intArrayOf(0x44FFC93C, 0x14FFC93C, 0x00FFC93C), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        shadowPaint.shader = RadialGradient(cx, bottom, halfW * 1.1f,
            intArrayOf(0x88000000.toInt(), 0x00000000), null, Shader.TileMode.CLAMP)
        // 해적: 원점 기준 셰이더 (그릴 때 translate 하므로)
        skinShader = RadialGradient(-headR * 0.35f, -headR * 0.4f, headR * 1.5f,
            intArrayOf(0xFFFFDCC0.toInt(), PirateArt.SKIN, PirateArt.SKIN_DARK), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        stripeShader = LinearGradient(0f, 0f, 0f, headR * 0.36f,
            intArrayOf(PirateArt.SHIRT, PirateArt.SHIRT, 0xFFF6F0E4.toInt(), 0xFFF6F0E4.toInt()),
            floatArrayOf(0f, 0.5f, 0.5f, 1f), Shader.TileMode.REPEAT)

        layoutHoles()
    }

    private fun addBand(v: Float, thick: Float, rivetBase: Int) {
        val v0 = v - thick / 2f
        val v1 = v + thick / 2f
        for (k in 0..STEPS) {
            val u = -1f + 2f * k / STEPS
            if (k == 0) bandPath.moveTo(sx(u, v0), sy(u, v0)) else bandPath.lineTo(sx(u, v0), sy(u, v0))
        }
        for (k in 0..STEPS) {
            val u = 1f - 2f * k / STEPS
            bandPath.lineTo(sx(u, v1), sy(u, v1))
        }
        bandPath.close()
        for (k in 0 until RIVETS) {
            val u = -0.85f + 1.7f * k / (RIVETS - 1)
            rivetX[rivetBase + k] = sx(u, v)
            rivetY[rivetBase + k] = sy(u, v)
        }
    }

    /** 구멍 24개를 고리 둘레 길이가 같도록 배치 */
    private fun layoutHoles() {
        // 고리: u = A cos φ, v = VC + B sin φ (φ 는 맨 위에서 시작해 시계 방향)
        var total = 0f
        for (k in 0..SAMPLES) {
            val phi = -PI / 2 + 2 * PI * k / SAMPLES
            val u = (RING_U * cos(phi)).toFloat()
            val v = (RING_VC + RING_V * sin(phi)).toFloat()
            sampleU[k] = u
            sampleX[k] = sx(u, v)
            sampleY[k] = sy(u, v)
            if (k > 0) {
                val ddx = sampleX[k] - sampleX[k - 1]
                val ddy = sampleY[k] - sampleY[k - 1]
                total += sqrt(ddx * ddx + ddy * ddy)
            }
            sampleLen[k] = total
        }
        ringCy = sy(0f, RING_VC.toFloat())
        val spacing = total / n
        holeR = (spacing * 0.34f).coerceAtMost(halfW * 0.07f)
        hitR = maxOf(spacing * 0.95f, holeR * 2.4f)
        var j = 1
        for (i in 0 until n) {
            val target = spacing * (i + 0.5f)
            while (j < SAMPLES && sampleLen[j] < target) j++
            val seg = (sampleLen[j] - sampleLen[j - 1]).coerceAtLeast(0.0001f)
            val f = (target - sampleLen[j - 1]) / seg
            hx[i] = sampleX[j - 1] + (sampleX[j] - sampleX[j - 1]) * f
            hy[i] = sampleY[j - 1] + (sampleY[j] - sampleY[j - 1]) * f
            val u = sampleU[j - 1] + (sampleU[j] - sampleU[j - 1]) * f
            hSquash[i] = sqrt((1f - u * u).coerceAtLeast(0f)).coerceAtLeast(0.55f)
            hAngle[i] = Math.toDegrees(atan2((hy[i] - ringCy).toDouble(), (hx[i] - cx).toDouble())).toFloat()
        }
    }

    // ─────────────────────────────── 바깥에서 쓰는 것

    /** 구멍 가운데 (이 뷰 좌표) */
    fun holeCenter(hole: Int, out: PointF) { out.set(hx[hole], hy[hole]) }

    /** 해적 얼굴 가운데 (이 뷰 좌표, 지금 위치) */
    fun headCenter(out: PointF) { out.set(cx + pirX, headCy + pirY) }

    fun swordAt(hole: Int): Boolean = hasSword[hole]

    /** 칼을 바로 꽂아 둔다 (연출 없이) */
    fun setSword(hole: Int, color: Int) {
        hasSword[hole] = true
        swordColor[hole] = color
        swordDark[hole] = Theme.shade(color, 0.6f)
        invalidate()
    }

    /** 통 비우기 (칼·함정 표시 지움) */
    fun clearSwords() {
        stabAnim?.cancel()
        stabHole = -1
        hasSword.fill(false)
        trapMark.fill(false)
        trapsShown = false
        hitHole = -1
        invalidate()
    }

    /** 칼이 천천히 꽂힌다 (ms) */
    fun stab(hole: Int, color: Int, ms: Long) {
        stabAnim?.cancel()
        hasSword[hole] = true
        swordColor[hole] = color
        swordDark[hole] = Theme.shade(color, 0.6f)
        stabHole = hole
        stabP = 0f
        mood = PirateArt.NERVOUS
        stabAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms.coerceAtLeast(1)
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener(stabUpdate)
            addListener(object : AnimEnd() { override fun onAnimationEnd(animation: Animator) { stabP = 1f; stabHole = -1; invalidate() } })
            start()
        }
        invalidate()
    }

    /** 결과 전 정적: 해적이 덜덜 떤다 (ms) */
    fun suspense(ms: Long) {
        suspenseAnim?.cancel()
        mood = PirateArt.NERVOUS
        suspenseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms.coerceAtLeast(1)
            addUpdateListener(suspenseUpdate)
            addListener(object : AnimEnd() { override fun onAnimationEnd(animation: Animator) { tremble = 0f; invalidate() } })
            start()
        }
    }

    /** 안전: 해적이 안심 */
    fun relax() {
        suspenseAnim?.cancel()
        tremble = 0f
        mood = PirateArt.NORMAL
        invalidate()
    }

    /** 함정: 해적이 회전하며 하늘로 날아간다. 함정 구멍(traps)을 빨갛게 보여 준다 */
    fun fly(hitHole: Int, traps: BooleanArray, ms: Long) {
        suspenseAnim?.cancel()
        tremble = 0f
        this.hitHole = hitHole
        for (i in 0 until n) trapMark[i] = traps[i]
        trapsShown = true
        mood = PirateArt.FLYING
        startPirate(ms, flyUpdate, LinearInterpolator(), endY = Float.NaN)
    }

    /** 새 판: 해적이 위에서 떨어져 통에 쏙 */
    fun drop(ms: Long) {
        mood = PirateArt.NORMAL
        pirX = 0f
        pirRot = 0f
        pirScale = 1f
        flyF = 0f
        pirY = -height * 0.9f
        startPirate(ms, dropUpdate, BounceInterpolator(), endY = 0f)
    }

    private fun startPirate(ms: Long, update: ValueAnimator.AnimatorUpdateListener, interp: android.animation.TimeInterpolator, endY: Float) {
        pirateAnim?.cancel()
        pirateAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms.coerceAtLeast(1)
            interpolator = interp
            addUpdateListener(update)
            if (!endY.isNaN()) addListener(object : AnimEnd() { override fun onAnimationEnd(animation: Animator) { pirY = endY; invalidate() } })
            start()
        }
        invalidate()
    }

    /** 해적 연출 위치 (날아간 끝 지점 별 반짝 용) */
    fun flyEnd(out: PointF) { out.set(cx + width * 0.3f, height * 0.07f) }

    /** 해적이 통 안에 있는지 (날아간 뒤면 false) */
    val pirateHome: Boolean get() = pirY == 0f

    /** 일시정지: 진행 중인 연출을 멈춘다 */
    fun pauseAnims() {
        stabAnim?.takeIf { it.isRunning }?.pause()
        suspenseAnim?.takeIf { it.isRunning }?.pause()
        pirateAnim?.takeIf { it.isRunning }?.pause()
    }

    fun resumeAnims() {
        stabAnim?.takeIf { it.isPaused }?.resume()
        suspenseAnim?.takeIf { it.isPaused }?.resume()
        pirateAnim?.takeIf { it.isPaused }?.resume()
    }

    // ─────────────────────────────── 터치

    /** (x, y) 에서 가장 가까운 구멍 (넉넉한 판정 반경 안, 없으면 -1) */
    fun holeAt(x: Float, y: Float): Int {
        var best = -1
        var bestD = hitR * hitR
        for (i in 0 until n) {
            val ddx = x - hx[i]
            val ddy = y - hy[i]
            val d = ddx * ddx + ddy * ddy
            if (d <= bestD) { bestD = d; best = i }
        }
        return best
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!touchEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val h = holeAt(e.x, e.y)
                if (h >= 0) {
                    performClick()
                    listener?.onHolePicked(h)
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    // ─────────────────────────────── 그리기

    override fun onDraw(c: Canvas) {
        if (headR <= 0f) return
        // 뒤 조명 + 바닥 그림자
        c.drawCircle(cx, glowCy, glowR, glowPaint)
        c.drawOval(cx - halfW * 1.1f, top + bodyH + rimRy * 0.2f, cx + halfW * 1.1f, top + bodyH + rimRy * 2.6f, shadowPaint)

        // 입구 안쪽 + 뒤쪽 테두리
        fill.style = Paint.Style.FILL
        fill.color = 0xFF1E1006.toInt()
        c.drawOval(rimOval, fill)
        line.color = PirateArt.WOOD
        line.strokeWidth = rimRy * 0.5f
        c.drawArc(rimOval, 180f, 180f, false, line)

        // 날아오르기 시작하면 통 앞으로 (처음엔 통에 가려진 채 튀어 오름)
        val flying = flyF > 0.12f
        if (!flying) drawPirate(c)

        // 몸통
        c.drawPath(bodyPath, woodPaint)
        line.color = 0x99331A08.toInt()
        line.strokeWidth = halfW * 0.012f
        c.drawPath(stavePath, line)
        line.color = 0x30FFE0B0
        line.strokeWidth = halfW * 0.006f
        c.drawPath(staveLightPath, line)
        // 쇠테
        c.drawPath(bandPath, bandPaint)
        fill.color = 0xFF2A2D35.toInt()
        val rr = halfW * 0.016f
        for (k in 0 until RIVETS * 2) c.drawCircle(rivetX[k], rivetY[k], rr, fill)
        fill.color = 0xFFD5DAE4.toInt()
        for (k in 0 until RIVETS * 2) c.drawCircle(rivetX[k] - rr * 0.3f, rivetY[k] - rr * 0.3f, rr * 0.45f, fill)
        // 몸통 외곽선
        line.color = 0xCC2A1608.toInt()
        line.strokeWidth = halfW * 0.016f
        c.drawPath(bodyPath, line)
        // 앞쪽 입구 테두리
        line.color = PirateArt.WOOD_LIGHT
        line.strokeWidth = rimRy * 0.5f
        c.drawArc(rimOval, 0f, 180f, false, line)
        line.color = 0x88FFE6B8.toInt()
        line.strokeWidth = rimRy * 0.12f
        c.drawArc(rimOval, 20f, 140f, false, line)

        // 구멍
        for (i in 0 until n) drawHole(c, i)
        // 칼 (꽂히는 중인 칼은 마지막에)
        for (i in 0 until n) if (hasSword[i] && i != stabHole) drawSword(c, i, 1f)
        if (stabHole >= 0) drawSword(c, stabHole, stabP)

        if (flying) drawPirate(c)
    }

    private fun drawHole(c: Canvas, i: Int) {
        val rx = holeR * hSquash[i]
        val ry = holeR * 0.92f
        val x = hx[i]
        val y = hy[i]
        fill.style = Paint.Style.FILL
        if (trapsShown && trapMark[i]) {
            fill.color = if (i == hitHole) 0xCCFF3B3B.toInt() else 0x99FF4D5E.toInt()
            c.drawOval(x - rx * 1.9f, y - ry * 1.9f, x + rx * 1.9f, y + ry * 1.9f, fill)
        }
        // 테두리 (짙은 나무) → 구멍 → 아래쪽 밝은 단면
        fill.color = 0xFF3E220E.toInt()
        c.drawOval(x - rx * 1.32f, y - ry * 1.32f, x + rx * 1.32f, y + ry * 1.32f, fill)
        fill.color = 0xFFD9A066.toInt()
        c.drawOval(x - rx * 1.05f, y - ry * 0.95f, x + rx * 1.05f, y + ry * 1.18f, fill)
        fill.color = 0xFF0E0603.toInt()
        c.drawOval(x - rx, y - ry, x + rx, y + ry * 0.98f, fill)
        if (trapsShown && trapMark[i] && !hasSword[i]) {
            // 숨어 있던 함정: 빨간 X
            line.color = Theme.RED
            line.strokeWidth = holeR * 0.3f
            val d = holeR * 0.5f
            c.drawLine(x - d * hSquash[i], y - d, x + d * hSquash[i], y + d, line)
            c.drawLine(x + d * hSquash[i], y - d, x - d * hSquash[i], y + d, line)
        }
    }

    private fun drawSword(c: Canvas, i: Int, p: Float) {
        val s = holeR * 0.6f
        val stub = holeR * 1.05f
        val blade = stub + (1f - p) * holeR * 3.4f
        c.save()
        c.translate(hx[i], hy[i])
        c.rotate(hAngle[i])
        art.alpha = 255
        PirateArt.drawSword(c, blade, s, swordColor[i], swordDark[i], art)
        c.restore()
    }

    private fun drawPirate(c: Canvas) {
        c.save()
        c.translate(cx + pirX + tremble, headCy + pirY)
        if (pirRot != 0f) c.rotate(pirRot)
        if (pirScale != 1f) c.scale(pirScale, pirScale)
        PirateArt.drawPirate(c, headR, art, scratch, mood, skinShader, stripeShader)
        c.restore()
    }

    override fun onDetachedFromWindow() {
        stabAnim?.cancel()
        suspenseAnim?.cancel()
        pirateAnim?.cancel()
        super.onDetachedFromWindow()
    }

    /** onAnimationEnd 만 쓰는 리스너 */
    private abstract class AnimEnd : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    }

    private companion object {
        const val STEPS = 28
        const val STAVES = 9
        const val SAMPLES = 480
        const val RIVETS = 7
        /** 쇠테 위치 (v) · 두께 */
        const val BAND_TOP = 0.075f
        const val BAND_BOTTOM = 0.925f
        const val BAND_W = 0.065f
        /** 구멍 고리: 가로 u 반지름, 세로 가운데·반지름 (v) */
        const val RING_U = 0.8
        const val RING_VC = 0.52
        const val RING_V = 0.31
    }
}
