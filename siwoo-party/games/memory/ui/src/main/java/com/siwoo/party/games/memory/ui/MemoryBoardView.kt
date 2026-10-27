package com.siwoo.party.games.memory.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.memory.GameConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 6×4 카드 판 (Canvas 하나로 24장을 그린다).
 * - 뒤집기: 카드마다 진행도를 따로 두고 가로 폭(scaleX)을 |cos| 로 접었다 펴며 가운데에서 면을 바꾼다
 * - 반짝임(짝 맞춤): 금색 빛 테두리가 맥박치며 살짝 커짐 → 떠남: 작아지며 사라지고 빈 자리 표시
 * - 판 테두리는 지금 차례인 사람 색
 * 연출이 하나라도 진행 중일 때만 ticker 가 돌며 다시 그린다. onDraw 에서는 객체를 만들지 않는다.
 * 규칙 판정은 하지 않는다 — 누른 칸 번호만 listener 로 알린다.
 */
class MemoryBoardView(context: Context) : View(context) {

    interface Listener {
        fun onCardTap(index: Int)
    }

    var listener: Listener? = null
    /** false 면 터치를 받기만 하고 알리지 않음 */
    var touchEnabled = true

    private val n = GameConfig.CARDS
    private val symbols = IntArray(n)
    /** 지금 보이는 면 (뒤집기가 끝난 기준) */
    private val face = BooleanArray(n)
    private val flipping = BooleanArray(n)
    private val flipTo = BooleanArray(n)
    private val flipStart = LongArray(n)
    private val flipDur = LongArray(n)
    private val glowing = BooleanArray(n)
    private val glowStart = LongArray(n)
    private val glowDur = LongArray(n)
    private val leaving = BooleanArray(n)
    private val leaveStart = LongArray(n)
    private val leaveDur = LongArray(n)
    /** 날아가서 빈 자리 */
    private val gone = BooleanArray(n)
    /** 맞춘 카드 (앞면에 금빛 테두리를 남긴다) */
    private val matched = BooleanArray(n)

    // ── 배치 (onSizeChanged 에서 계산) ──
    private val cx = FloatArray(n)
    private val cy = FloatArray(n)
    private var cardW = 0f
    private var cardH = 0f
    private var gap = 0f
    private val frame = RectF()
    private var frameColor = Theme.GOLD
    private var backShader: Shader? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33FFFFFF }

    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { onTick() }
    }

    init {
        isClickable = true
        contentDescription = "카드 판"
    }

    private fun now(): Long = SystemClock.uptimeMillis()

    /** 자리별 그림 번호 (CardArt 순서). 모두 뒷면으로 */
    fun setSymbols(s: IntArray) {
        for (k in 0 until n) {
            symbols[k] = s[k]
            face[k] = false; flipping[k] = false; glowing[k] = false; leaving[k] = false; gone[k] = false; matched[k] = false
        }
        invalidate()
    }

    /** k 번 카드를 앞면(toFace=true) 또는 뒷면으로 뒤집기 */
    fun flip(k: Int, toFace: Boolean, durMs: Long) {
        if (face[k] == toFace && !flipping[k]) return
        flipping[k] = true
        flipTo[k] = toFace
        flipStart[k] = now()
        flipDur[k] = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    /** 짝 맞춤 반짝임 */
    fun sparkle(k: Int, durMs: Long) {
        matched[k] = true
        glowing[k] = true
        glowStart[k] = now()
        glowDur[k] = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    /** 작아지며 떠남 → 빈 자리 */
    fun leave(k: Int, durMs: Long) {
        matched[k] = true
        glowing[k] = false
        leaving[k] = true
        leaveStart[k] = now()
        leaveDur[k] = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    fun setFrameColor(color: Int) {
        if (frameColor != color) { frameColor = color; invalidate() }
    }

    fun isFaceShown(k: Int): Boolean = if (flipping[k]) flipTo[k] else face[k]
    fun isGone(k: Int): Boolean = gone[k]

    /** 뒤집기·반짝임·떠남 중 하나라도 진행 중 */
    val isAnimating: Boolean get() = ticker.isRunning

    /** k 번 카드 가운데 (이 뷰 좌표) */
    fun cardCenter(k: Int, out: PointF) { out.set(cx[k], cy[k]) }

    private fun ensureRunning() {
        if (!ticker.isRunning) ticker.start()
        invalidate()
    }

    /** 끝난 연출을 정리하고, 남은 게 없으면 ticker 를 멈춘다 */
    private fun onTick() {
        val t = now()
        var any = false
        for (k in 0 until n) {
            if (flipping[k]) {
                if (t - flipStart[k] >= flipDur[k]) { flipping[k] = false; face[k] = flipTo[k] } else any = true
            }
            if (glowing[k]) {
                if (t - glowStart[k] >= glowDur[k]) glowing[k] = false else any = true
            }
            if (leaving[k]) {
                if (t - leaveStart[k] >= leaveDur[k]) { leaving[k] = false; gone[k] = true } else any = true
            }
        }
        if (!any) ticker.cancel()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = Theme.dpf(context, 10f)
        gap = Theme.dpf(context, 7f)
        val aw = w - pad * 2
        val ah = h - pad * 2
        val cols = GameConfig.COLS
        val rows = GameConfig.ROWS
        // 세로로 긴 카드 (폭:높이 = 0.78:1), 판 안에 꽉 차게
        cardW = minOf((aw - gap * (cols - 1)) / cols, (ah - gap * (rows - 1)) / rows * RATIO)
        cardH = cardW / RATIO
        val gw = cardW * cols + gap * (cols - 1)
        val gh = cardH * rows + gap * (rows - 1)
        val left = (w - gw) / 2f
        val top = (h - gh) / 2f
        for (k in 0 until n) {
            val c = k % cols
            val r = k / cols
            cx[k] = left + c * (cardW + gap) + cardW / 2f
            cy[k] = top + r * (cardH + gap) + cardH / 2f
        }
        frame.set(left - pad * 0.7f, top - pad * 0.7f, left + gw + pad * 0.7f, top + gh + pad * 0.7f)
        framePaint.strokeWidth = Theme.dpf(context, 3f)
        slotPaint.strokeWidth = Theme.dpf(context, 1.5f)
        backShader = LinearGradient(-cardW / 2f, -cardH / 2f, cardW / 2f, cardH / 2f,
            intArrayOf(Theme.GOLD_LIGHT, Theme.GOLD, Theme.GOLD_DARK), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        if (cardW <= 0f) return
        val t = now()
        // 판 바탕 + 차례인 사람 색 테두리
        val fr = Theme.dpf(context, 14f)
        framePaint.style = Paint.Style.FILL
        framePaint.color = 0x40000000
        c.drawRoundRect(frame, fr, fr, framePaint)
        framePaint.style = Paint.Style.STROKE
        framePaint.color = frameColor
        c.drawRoundRect(frame, fr, fr, framePaint)

        val rr = minOf(cardW, cardH) * 0.14f
        for (k in 0 until n) {
            if (gone[k]) {
                // 빈 자리
                c.drawRoundRect(cx[k] - cardW / 2f, cy[k] - cardH / 2f, cx[k] + cardW / 2f, cy[k] + cardH / 2f, rr, rr, slotPaint)
                continue
            }
            var sx = 1f
            var front = face[k]
            if (flipping[k]) {
                val p = ((t - flipStart[k]).toFloat() / flipDur[k]).coerceIn(0f, 1f)
                sx = abs(cos(p * PI.toFloat()))
                front = if (p < 0.5f) !flipTo[k] else flipTo[k]
            }
            var s = 1f
            var glow = if (matched[k]) 0.35f else 0f
            var rot = 0f
            if (glowing[k]) {
                val q = ((t - glowStart[k]).toFloat() / glowDur[k]).coerceIn(0f, 1f)
                val wave = sin(q * PI.toFloat())
                s = 1f + 0.1f * wave
                glow = 0.4f + 0.6f * abs(sin(q * PI.toFloat() * 2.5f))
            }
            if (leaving[k]) {
                val q = ((t - leaveStart[k]).toFloat() / leaveDur[k]).coerceIn(0f, 1f)
                s = (1f - q * q) * 1.05f
                rot = q * 25f
                glow = 1f - q
            }
            if (s <= 0.01f) continue
            c.save()
            c.translate(cx[k], cy[k])
            c.rotate(rot)
            c.scale(sx * s, s)
            // 그림자
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = 0x55000000
            c.drawRoundRect(-cardW / 2f + gap * 0.25f, -cardH / 2f + gap * 0.4f, cardW / 2f + gap * 0.25f, cardH / 2f + gap * 0.4f, rr, rr, paint)
            if (front) CardArt.drawFront(c, symbols[k], 0f, 0f, cardW, cardH, paint, glow)
            else CardArt.drawBack(c, 0f, 0f, cardW, cardH, paint, backShader)
            c.restore()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!touchEnabled) return true
                val k = hit(e.x, e.y)
                if (k >= 0) listener?.onCardTap(k)
                return true
            }
            MotionEvent.ACTION_UP -> { performClick(); return true }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** 터치한 칸 (카드 둘레 간격 절반까지 포함), 없으면 -1 */
    fun hit(x: Float, y: Float): Int {
        val hw = (cardW + gap) / 2f
        val hh = (cardH + gap) / 2f
        for (k in 0 until n) if (abs(x - cx[k]) <= hw && abs(y - cy[k]) <= hh) return k
        return -1
    }

    override fun onDetachedFromWindow() {
        ticker.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        /** 카드 폭 : 높이 */
        private const val RATIO = 0.78f
    }
}
