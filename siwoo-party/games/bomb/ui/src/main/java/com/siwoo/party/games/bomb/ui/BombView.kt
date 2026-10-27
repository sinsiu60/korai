package com.siwoo.party.games.bomb.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 가운데 큰 폭탄.
 * - IDLE: 불 안 붙은 폭탄 (주제 카드 단계). 다시 그리지 않는다.
 * - LIVE: 심지 끝에 불꽃 파티클 + 흔들림. 째깍마다 tick(level) 로 빨간 빛이 번쩍하고 흔들림 세기가 바뀐다.
 *   (남은 시간은 보여 주지 않으므로 심지 길이는 줄지 않는다)
 * - EXPLODED: 펑! 폭발 모양 + 연기
 * 프레임 루프(LIVE 때만)는 pause() 로 멈추고 resume() 으로 다시 (일시정지·백그라운드).
 * 불꽃은 공통 연출 층 파티클을 쓰지 않고 여기서 고정 개수(SPARKS)를 시간 함수로 그린다 → onDraw 할당 없음.
 */
class BombView(context: Context) : View(context) {

    enum class State { IDLE, LIVE, EXPLODED }

    var state = State.IDLE
        private set
    /** 흔들림 세기 0~1 (BombGame.shakeLevel) */
    var level = 0f
        private set
    /** 프레임 루프가 돌고 있는지 (테스트용) */
    val animating: Boolean get() = loop != null

    /** 애니메이션 시계(ms): 루프가 도는 동안만 흐른다 */
    private var clock = 0f
    private var shakePhase = 0f
    private var lastFrame = 0L
    /** 째깍 순간 1 → 0 으로 줄어듦 */
    private var tickPop = 0f
    /** 폭발 진행 0~1 */
    private var boom = 0f
    private var loop: ValueAnimator? = null
    private var boomAnim: ValueAnimator? = null

    private var cx = 0f
    private var cy = 0f
    private var r = 0f
    private var size = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val burstOuter = Path()
    private val burstInner = Path()
    private var bodyShader: Shader? = null
    private var tickShader: Shader? = null
    private var flameShader: Shader? = null

    init {
        isClickable = false
        isFocusable = false
        contentDescription = "폭탄"
    }

    /** 새 라운드: 불 안 붙은 폭탄으로 */
    fun reset() {
        stopLoop()
        boomAnim?.cancel()
        boomAnim = null
        state = State.IDLE
        level = 0f
        tickPop = 0f
        boom = 0f
        invalidate()
    }

    /** 불 붙이기 */
    fun light(level: Float) {
        state = State.LIVE
        this.level = level
        startLoop()
        invalidate()
    }

    /** 째깍: 흔들림 세기 갱신 + 빨간 빛 번쩍 */
    fun tick(level: Float) {
        if (state != State.LIVE) return
        this.level = level
        tickPop = 1f
        invalidate()
    }

    /** 펑! (durMs 동안 폭발 연출) */
    fun explode(durMs: Long) {
        stopLoop()
        state = State.EXPLODED
        boom = 0f
        boomAnim?.cancel()
        boomAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durMs.coerceAtLeast(1)
            interpolator = LinearInterpolator()
            addUpdateListener { boom = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** 일시정지: 흔들림·불꽃 멈춤 */
    fun pause() {
        stopLoop()
        boomAnim?.pause()
    }

    fun resume() {
        if (state == State.LIVE) startLoop()
        boomAnim?.resume()
    }

    private fun startLoop() {
        if (loop != null) return
        lastFrame = SystemClock.uptimeMillis()
        loop = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { frame() }
            start()
        }
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
    }

    private fun frame() {
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, 50L).toFloat()
        lastFrame = now
        clock += dt
        if (clock > CLOCK_WRAP) clock -= CLOCK_WRAP
        // 흔들림이 셀수록 빠르게 떤다
        shakePhase += dt * (0.012f + 0.055f * level)
        if (shakePhase > PHASE_WRAP) shakePhase -= PHASE_WRAP
        tickPop = (tickPop - dt / 200f).coerceAtLeast(0f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        size = minOf(w, h).toFloat()
        r = size * 0.3f
        // 심지·불꽃이 오른쪽 위로 나가므로 몸통을 왼쪽 아래로 조금 옮겨 전체가 가운데 오게
        cx = w / 2f - r * 0.24f
        cy = h / 2f + r * 0.4f
        if (r <= 0f) return
        bodyShader = RadialGradient(
            cx - r * 0.35f, cy - r * 0.4f, r * 1.45f,
            intArrayOf(BombArt.BODY_LIGHT, BombArt.BODY, BombArt.BODY_DARK), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        tickShader = RadialGradient(cx, cy, r * 1.6f, intArrayOf(0xEEFF3D00.toInt(), 0x88FF3D00.toInt(), 0x00FF3D00), floatArrayOf(0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
        val tx = cx + r * BombArt.TIP_X
        val ty = cy + r * BombArt.TIP_Y
        flameShader = RadialGradient(tx, ty, r * 0.4f, intArrayOf(0xAAFFB300.toInt(), 0x00FF6D00), null, Shader.TileMode.CLAMP)
        BombArt.star(burstOuter, 0f, 0f, size * 0.44f, size * 0.27f, 12, 0f)
        BombArt.star(burstInner, 0f, 0f, size * 0.29f, size * 0.18f, 12, 15f)
    }

    override fun onDraw(c: Canvas) {
        if (r <= 0f) return
        if (state == State.EXPLODED) drawBoom(c) else drawBomb(c)
    }

    private fun drawBomb(c: Canvas) {
        val live = state == State.LIVE
        var dx = 0f
        var dy = 0f
        var rot = 0f
        if (live) {
            val amp = r * 0.1f * level
            dx = Math.sin(shakePhase.toDouble()).toFloat() * amp
            dy = Math.cos(shakePhase * 1.7).toFloat() * amp * 0.4f
            rot = Math.sin(shakePhase * 1.3).toFloat() * 9f * level
        }
        // 째깍 빨간 빛
        if (live && tickPop > 0f) {
            glow.shader = tickShader
            glow.alpha = (tickPop * (90f + 165f * level)).toInt().coerceIn(0, 255)
            c.drawCircle(cx, cy, r * 1.6f, glow)
            glow.shader = null
        }
        val scale = 1f + 0.06f * tickPop
        c.save()
        c.translate(dx, dy)
        c.rotate(rot, cx, cy)
        c.scale(scale, scale, cx, cy)
        BombArt.drawBomb(c, cx, cy, r, paint, path, bodyShader)
        if (live) drawSparks(c, cx + r * BombArt.TIP_X, cy + r * BombArt.TIP_Y)
        c.restore()
    }

    /** 심지 끝 불꽃: 빛 + 튀는 불티 SPARKS 개 + 가운데 불꽃 별 */
    private fun drawSparks(c: Canvas, x: Float, y: Float) {
        val flicker = 0.85f + 0.15f * Math.sin(clock * 0.045).toFloat()
        glow.shader = flameShader
        glow.alpha = 255
        c.drawCircle(x, y, r * 0.4f, glow)
        glow.shader = null
        for (k in 0 until SPARKS) {
            val life = 300f + (k * 53 % 170)
            val f = ((clock + k * 131f) % life) / life
            val ang = -Math.PI / 2 + ((k * 37 % 100) / 100.0 - 0.5) * 2.9
            val dist = r * (0.3f + (k * 29 % 40) / 100f) * f
            val px = x + Math.cos(ang).toFloat() * dist
            val py = y + Math.sin(ang).toFloat() * dist + f * f * r * 0.35f
            spark.color = mix(SPARK_HOT, SPARK_COOL, f)
            spark.alpha = ((1f - f) * 255).toInt()
            c.drawCircle(px, py, r * 0.055f * (1f - f) + 1.5f, spark)
        }
        c.save()
        c.rotate((clock * 0.3f) % 360f, x, y)
        BombArt.drawFlame(c, x, y, r * 0.24f * flicker, paint, path)
        c.restore()
    }

    /** 펑: 폭발 별이 (뷰 가운데에서) 커졌다가 살짝 줄어들고 연기가 퍼지며 사라진다 */
    private fun drawBoom(c: Canvas) {
        val e = boom
        val grow = if (e < 0.3f) {
            val k = e / 0.3f
            (1f - (1f - k) * (1f - k)) * 1.12f
        } else 1.12f - 0.12f * ((e - 0.3f) / 0.7f)
        // 연기
        paint.style = Paint.Style.FILL
        paint.color = SMOKE
        paint.alpha = ((1f - e) * 190).toInt().coerceIn(0, 255)
        for (k in 0 until 8) {
            val a = k * Math.PI / 4 + 0.35
            val d = size * (0.18f + 0.3f * e)
            c.drawCircle(width / 2f + Math.cos(a).toFloat() * d, height / 2f + Math.sin(a).toFloat() * d, size * (0.09f + 0.07f * e), paint)
        }
        paint.alpha = 255
        c.save()
        c.translate(width / 2f, height / 2f)
        c.rotate(e * 18f)
        c.scale(grow, grow)
        paint.color = BOOM_OUT
        c.drawPath(burstOuter, paint)
        paint.color = BOOM_MID
        c.drawPath(burstInner, paint)
        paint.color = 0xFFFFFFFF.toInt()
        c.drawCircle(0f, 0f, size * 0.12f, paint)
        c.restore()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        boomAnim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val SPARKS = 14
        private const val CLOCK_WRAP = 3_600_000f
        private const val PHASE_WRAP = (Math.PI * 2000).toFloat()
        private const val SPARK_HOT = 0xFFFFF59D.toInt()
        private const val SPARK_COOL = 0xFFFF5722.toInt()
        private const val SMOKE = 0xFF9E9E9E.toInt()
        private const val BOOM_OUT = 0xFFFF5722.toInt()
        private const val BOOM_MID = 0xFFFFC93C.toInt()

        /** 두 색 섞기 (할당 없음) */
        fun mix(a: Int, b: Int, f: Float): Int {
            val g = 1f - f
            val ar = (a ushr 16) and 0xFF; val ag = (a ushr 8) and 0xFF; val ab = a and 0xFF
            val br = (b ushr 16) and 0xFF; val bg = (b ushr 8) and 0xFF; val bb = b and 0xFF
            return (0xFF shl 24) or ((ar * g + br * f).toInt() shl 16) or ((ag * g + bg * f).toInt() shl 8) or (ab * g + bb * f).toInt()
        }
    }
}
