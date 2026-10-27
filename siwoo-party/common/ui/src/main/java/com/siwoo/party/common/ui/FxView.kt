package com.siwoo.party.common.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * 화면 전체를 덮는 연출 층: 날아가는 코인·카드, 터지는 별, 꽃가루, 중앙 큰 문구.
 * - 파티클(코인/카드)은 최대 20개를 미리 만들어 두고 재사용(객체 풀).
 * - 연출이 하나라도 진행 중일 때만 ValueAnimator 가 돌며 다시 그린다.
 * - onDraw 에서는 객체를 만들지 않는다.
 * 터치는 받지 않는다(아래 화면으로 그대로 전달).
 */
class FxView(context: Context) : View(context) {

    private class Particle {
        var active = false
        var card = false
        /** 0 = 코인/카드(포물선), 1 = 별(터지며 퍼짐), 2 = 꽃가루(떨어짐) */
        var kind = 0
        var spin = 0f
        var x0 = 0f; var y0 = 0f; var cx = 0f; var cy = 0f; var x1 = 0f; var y1 = 0f
        var start = 0L
        var dur = 1L
        var color = 0
        var label = ""
    }

    private val pool = Array(MAX_PARTICLES) { Particle() }

    // ── 중앙 문구 ──
    private var bannerActive = false
    private var bannerText = ""
    private var bannerSub = ""
    private var bannerStart = 0L
    private var bannerDur = 1L
    private var bannerCx = 0f
    private var bannerCy = 0f
    private var bannerSize = 0f
    private var bannerSubSize = 0f
    private var bannerFill = Theme.GOLD
    private var bannerBoxW = 0f

    private val coinPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = Theme.INK
    }
    private val bannerOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = 0xFF0A0F2E.toInt()
    }
    private val bannerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bannerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
    }
    private val bannerBox = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC0A0F2E.toInt() }
    private val boxRect = RectF()
    private val tmpRect = RectF()

    private val coinR = Theme.dpf(context, 7f)
    private val cardW = Theme.dpf(context, 46f)
    private val cardH = Theme.dpf(context, 30f)

    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { onTick() }
    }

    init {
        isClickable = false
        isFocusable = false
        labelPaint.textSize = Theme.dpf(context, 10f)
        bannerSubPaint.setShadowLayer(Theme.dpf(context, 2f), 0f, Theme.dpf(context, 1f), 0xFF000000.toInt())
        bannerFillPaint.setShadowLayer(Theme.dpf(context, 6f), 0f, Theme.dpf(context, 3f), Theme.GOLD_DARK)
    }

    private fun now(): Long = SystemClock.uptimeMillis()

    private fun obtain(): Particle? {
        for (p in pool) if (!p.active) return p
        return null // 동시에 최대 20개
    }

    private fun ensureRunning() {
        if (!ticker.isRunning) ticker.start()
        invalidate()
    }

    private fun onTick() {
        var any = bannerActive
        if (!any) for (p in pool) if (p.active) { any = true; break }
        if (!any) ticker.cancel()
        invalidate()
    }

    /**
     * 코인 count 개가 (x0,y0) → (x1,y1) 로 포물선을 그리며 날아간다.
     * totalMs 안에 모두 도착하도록 출발을 조금씩 늦춘다.
     */
    fun coins(x0: Float, y0: Float, x1: Float, y1: Float, count: Int, totalMs: Long) {
        val n = count.coerceIn(1, MAX_PARTICLES)
        val flight = (totalMs * 0.6f).toLong().coerceAtLeast(1)
        val spread = totalMs - flight
        val t = now()
        val lift = Theme.dpf(context, 60f) + kotlin.math.abs(x1 - x0) * 0.15f
        for (k in 0 until n) {
            val p = obtain() ?: break
            p.active = true
            p.card = false
            p.kind = 0
            val jx = (k % 3 - 1) * coinR * 0.9f
            val jy = (k % 2) * coinR * 0.6f
            p.x0 = x0 + jx; p.y0 = y0 + jy
            p.x1 = x1 - jx * 0.5f; p.y1 = y1
            p.cx = (p.x0 + p.x1) / 2f + jx * 2f
            p.cy = minOf(p.y0, p.y1) - lift - k * coinR * 0.4f
            p.start = t + if (n > 1) spread * k / (n - 1) else 0L
            p.dur = flight
        }
        ensureRunning()
    }

    /** 땅 카드가 날아간다 (거래) */
    fun card(x0: Float, y0: Float, x1: Float, y1: Float, label: String, color: Int, delayMs: Long, flightMs: Long) {
        val p = obtain() ?: return
        p.active = true
        p.card = true
        p.kind = 0
        p.x0 = x0; p.y0 = y0; p.x1 = x1; p.y1 = y1
        p.cx = (x0 + x1) / 2f
        p.cy = minOf(y0, y1) - Theme.dpf(context, 90f)
        p.start = now() + delayMs
        p.dur = flightMs.coerceAtLeast(1)
        p.color = color
        p.label = label
        ensureRunning()
    }

    /** 보드 중앙 큰 문구: 확대 등장 → 살짝 흔들림 → 사라짐 */
    fun banner(cx: Float, cy: Float, maxW: Float, text: String, sub: String, tone: BannerTone, durMs: Long) {
        bannerText = text
        bannerSub = sub
        bannerCx = cx
        bannerCy = cy
        bannerStart = now()
        bannerDur = durMs.coerceAtLeast(1)
        bannerFill = when (tone) {
            BannerTone.GOOD -> Theme.GOLD
            BannerTone.BAD -> 0xFFFF6B78.toInt()
            BannerTone.INFO -> Color.WHITE
        }
        // 글자 크기를 폭에 맞춘다 (여기서 한 번만 측정)
        var size = Theme.dpf(context, 40f)
        bannerFillPaint.textSize = size
        while (bannerFillPaint.measureText(text) > maxW * 0.9f && size > Theme.dpf(context, 14f)) {
            size *= 0.9f
            bannerFillPaint.textSize = size
        }
        bannerSize = size
        bannerOutline.textSize = size
        bannerOutline.strokeWidth = size * 0.16f
        var sub2 = size * 0.42f
        bannerSubPaint.textSize = sub2
        while (sub.isNotEmpty() && bannerSubPaint.measureText(sub) > maxW * 0.9f && sub2 > Theme.dpf(context, 9f)) {
            sub2 *= 0.9f
            bannerSubPaint.textSize = sub2
        }
        bannerSubSize = sub2
        bannerBoxW = maxOf(bannerFillPaint.measureText(text), if (sub.isEmpty()) 0f else bannerSubPaint.measureText(sub)) + size
        bannerActive = true
        ensureRunning()
    }

    /** 별이 (x,y)에서 사방으로 터지며 사라진다 */
    fun stars(x: Float, y: Float, count: Int, durMs: Long) {
        val t = now()
        val n = count.coerceIn(1, MAX_PARTICLES)
        for (k in 0 until n) {
            val p = obtain() ?: break
            p.active = true; p.card = false; p.kind = 1
            val a = (k * 2.0 * PI / n).toFloat()
            val dist = Theme.dpf(context, 60f + (k % 3) * 25f)
            p.x0 = x; p.y0 = y
            p.x1 = x + kotlin.math.cos(a) * dist
            p.y1 = y + sin(a) * dist
            p.start = t + (k % 4) * 40L
            p.dur = durMs.coerceAtLeast(1)
            p.color = STAR_COLORS[k % STAR_COLORS.size]
            p.spin = (k * 37 % 360).toFloat()
        }
        ensureRunning()
    }

    /** 꽃가루: 화면 위에서 흩날리며 떨어진다 (폭 width) */
    fun confetti(width: Float, height: Float, count: Int, durMs: Long) {
        val t = now()
        val n = count.coerceIn(1, MAX_PARTICLES)
        for (k in 0 until n) {
            val p = obtain() ?: break
            p.active = true; p.card = false; p.kind = 2
            p.x0 = width * ((k * 53 + 7) % 100) / 100f
            p.y0 = -Theme.dpf(context, 20f)
            p.x1 = p.x0 + Theme.dpf(context, ((k * 29) % 80 - 40).toFloat())
            p.y1 = height + Theme.dpf(context, 20f)
            p.start = t + (k * 97L) % (durMs / 2).coerceAtLeast(1)
            p.dur = (durMs * (0.55f + (k % 5) * 0.1f)).toLong().coerceAtLeast(1)
            p.color = CONFETTI_COLORS[k % CONFETTI_COLORS.size]
            p.spin = (k * 53 % 360).toFloat()
        }
        ensureRunning()
    }

    private fun drawStar(c: Canvas, p: Particle, f: Float) {
        val e = 1f - (1f - f) * (1f - f)
        val x = p.x0 + (p.x1 - p.x0) * e
        val y = p.y0 + (p.y1 - p.y0) * e
        val s = coinR * (1.2f - 0.6f * f)
        c.save()
        c.translate(x, y)
        c.rotate(p.spin + f * 180f)
        coinPaint.color = p.color
        coinPaint.alpha = ((1f - f) * 255).toInt()
        // 네 갈래 별: 마름모 두 개
        tmpRect.set(-s * 0.35f, -s * 1.2f, s * 0.35f, s * 1.2f)
        c.drawOval(tmpRect, coinPaint)
        tmpRect.set(-s * 1.2f, -s * 0.35f, s * 1.2f, s * 0.35f)
        c.drawOval(tmpRect, coinPaint)
        coinPaint.alpha = 255
        c.restore()
    }

    private fun drawConfetti(c: Canvas, p: Particle, f: Float) {
        val x = p.x0 + (p.x1 - p.x0) * f + sin(f * PI.toFloat() * 4f + p.spin) * coinR * 2f
        val y = p.y0 + (p.y1 - p.y0) * f
        c.save()
        c.translate(x, y)
        c.rotate(p.spin + f * 720f)
        coinPaint.color = p.color
        tmpRect.set(-coinR * 0.6f, -coinR * 0.3f, coinR * 0.6f, coinR * 0.3f)
        c.drawRect(tmpRect, coinPaint)
        c.restore()
    }

    fun clearAll() {
        for (p in pool) p.active = false
        bannerActive = false
        ticker.cancel()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val t = now()
        for (p in pool) {
            if (!p.active) continue
            if (t < p.start) continue
            var f = (t - p.start).toFloat() / p.dur
            if (f >= 1f) { p.active = false; continue }
            if (f < 0f) f = 0f
            val e = f * f * (3f - 2f * f) // 부드러운 시작·끝
            val a = 1f - e
            val x = a * a * p.x0 + 2f * a * e * p.cx + e * e * p.x1
            val y = a * a * p.y0 + 2f * a * e * p.cy + e * e * p.y1
            when {
                p.kind == 1 -> drawStar(canvas, p, f)
                p.kind == 2 -> drawConfetti(canvas, p, f)
                p.card -> drawCard(canvas, x, y, p, e)
                else -> drawCoin(canvas, x, y, f)
            }
        }
        if (bannerActive) drawBanner(canvas, t)
    }

    private fun drawCoin(c: Canvas, x: Float, y: Float, f: Float) {
        // 회전하는 느낌: 폭을 sin 으로 줄였다 늘림
        val squash = 0.55f + 0.45f * kotlin.math.abs(sin(f * PI.toFloat() * 3f))
        tmpRect.set(x - coinR * squash, y - coinR, x + coinR * squash, y + coinR)
        coinPaint.color = Theme.GOLD_DARK
        c.drawOval(tmpRect, coinPaint)
        tmpRect.inset(coinR * 0.18f * squash, coinR * 0.18f)
        coinPaint.color = Theme.GOLD
        c.drawOval(tmpRect, coinPaint)
        coinPaint.color = Theme.GOLD_LIGHT
        c.drawCircle(x - coinR * 0.3f * squash, y - coinR * 0.35f, coinR * 0.22f, coinPaint)
    }

    private fun drawCard(c: Canvas, x: Float, y: Float, p: Particle, e: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(sin(e * PI.toFloat()) * 18f)
        tmpRect.set(-cardW / 2, -cardH / 2, cardW / 2, cardH / 2)
        cardPaint.color = Theme.PAPER
        c.drawRoundRect(tmpRect, cardH * 0.18f, cardH * 0.18f, cardPaint)
        tmpRect.bottom = tmpRect.top + cardH * 0.3f
        cardPaint.color = p.color
        c.drawRect(tmpRect, cardPaint)
        c.drawText(p.label, 0f, cardH * 0.32f, labelPaint)
        c.restore()
    }

    private fun drawBanner(c: Canvas, t: Long) {
        val f = (t - bannerStart).toFloat() / bannerDur
        if (f >= 1f) { bannerActive = false; return }
        // 0~18%: 확대 등장(살짝 튀어오름), 18~40%: 흔들림, 마지막 20%: 사라짐
        val scale = when {
            f < 0.18f -> {
                val q = f / 0.18f
                0.3f + 0.7f * q + 0.25f * sin(q * PI.toFloat())
            }
            else -> 1f
        }
        val rot = if (f in 0.18f..0.4f) sin((f - 0.18f) * PI.toFloat() * 9f) * 4f * (1f - (f - 0.18f) / 0.22f) else 0f
        val alpha = if (f > 0.8f) (1f - (f - 0.8f) / 0.2f) else 1f
        val a255 = (alpha * 255).toInt().coerceIn(0, 255)

        c.save()
        c.translate(bannerCx, bannerCy)
        c.rotate(rot)
        c.scale(scale, scale)
        val h = bannerSize * 1.3f + if (bannerSub.isEmpty()) 0f else bannerSubSize * 1.5f
        boxRect.set(-bannerBoxW / 2, -bannerSize * 1.05f, bannerBoxW / 2, -bannerSize * 1.05f + h)
        bannerBox.alpha = (a255 * 0.8f).toInt()
        c.drawRoundRect(boxRect, bannerSize * 0.35f, bannerSize * 0.35f, bannerBox)
        bannerOutline.alpha = a255
        bannerFillPaint.color = bannerFill
        bannerFillPaint.alpha = a255
        c.drawText(bannerText, 0f, 0f, bannerOutline)
        c.drawText(bannerText, 0f, 0f, bannerFillPaint)
        if (bannerSub.isNotEmpty()) {
            bannerSubPaint.alpha = a255
            c.drawText(bannerSub, 0f, bannerSubSize * 1.45f, bannerSubPaint)
        }
        c.restore()
    }

    override fun onDetachedFromWindow() {
        ticker.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        const val MAX_PARTICLES = 20
        private val STAR_COLORS = intArrayOf(0xFFFFE08A.toInt(), 0xFFFFC93C.toInt(), 0xFFFFFFFF.toInt())
        private val CONFETTI_COLORS = intArrayOf(0xFFFF5252.toInt(), 0xFF448AFF.toInt(), 0xFF00D27A.toInt(), 0xFFFFD600.toInt(), 0xFFFFC93C.toInt(), 0xFFE040FB.toInt())
    }
}

/** 중앙 문구 색: 보통(흰색) / 좋음(금색) / 나쁨(빨강) */
enum class BannerTone { INFO, GOOD, BAD
}
