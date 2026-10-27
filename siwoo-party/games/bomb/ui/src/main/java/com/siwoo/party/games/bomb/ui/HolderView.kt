package com.siwoo.party.games.bomb.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import com.siwoo.party.common.ui.Theme

/**
 * 폭탄을 든 사람 이름 (크게): 위에 작은 안내 + 플레이어 색 알약 안에 이름.
 * 넘기면 새 이름이 톡 튀어나오고, 터지면 빨간 "펑!" 도장이 쾅 찍힌다.
 */
class HolderView(context: Context) : View(context) {

    var name = ""
        private set
    /** "펑!" 도장이 찍혀 있는지 (테스트용) */
    var stamped = false
        private set

    private var color = Theme.GRAY
    /** 이름 바뀜 연출 0 → 1 */
    private var swap = 1f
    /** 도장 연출 0 → 1 */
    private var stampT = 0f
    private var swapAnim: ValueAnimator? = null
    private var stampAnim: ValueAnimator? = null
    private var nameWidth = 0f
    private var stampWidth = 0f
    private val pill = RectF()
    private val stampRect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB; typeface = Typeface.DEFAULT_BOLD }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = 0xFFFFFFFF.toInt() }

    init {
        isClickable = false
        isFocusable = false
    }

    /** 들고 있는 사람 바꾸기 (animate 면 durMs 동안 톡 튀어나옴) */
    fun setHolder(name: String, color: Int, animate: Boolean, durMs: Long = 0) {
        this.name = name
        this.color = color
        contentDescription = "폭탄을 든 사람 $name"
        namePaint.color = Theme.textOn(color)
        fit()
        swapAnim?.cancel()
        if (animate && durMs > 0) {
            swap = 0f
            swapAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durMs
                interpolator = LinearInterpolator()
                addUpdateListener { swap = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            swap = 1f
        }
        invalidate()
    }

    /** "펑!" 도장 쾅 */
    fun stamp(durMs: Long) {
        stamped = true
        stampAnim?.cancel()
        stampT = 0f
        stampAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durMs.coerceAtLeast(1)
            interpolator = LinearInterpolator()
            addUpdateListener { stampT = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun clearStamp() {
        stampAnim?.cancel()
        stampAnim = null
        stamped = false
        stampT = 0f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
    }

    /** 이름이 폭 안에 들어오게 글자 크기 맞추기 (값이 바뀔 때만) */
    private fun fit() {
        val h = height.toFloat()
        val w = width.toFloat()
        if (h <= 0f || w <= 0f) return
        captionPaint.textSize = h * 0.17f
        val pillH = h * 0.66f
        namePaint.textSize = pillH * 0.6f
        val maxW = w * 0.9f - pillH
        nameWidth = namePaint.measureText(name)
        if (nameWidth > maxW && nameWidth > 0f) {
            namePaint.textSize *= maxW / nameWidth
            nameWidth = namePaint.measureText(name)
        }
        stampPaint.textSize = pillH * 0.56f
        stampWidth = stampPaint.measureText(STAMP) + pillH * 0.5f
        stroke.strokeWidth = Theme.dpf(context, 2.5f)
        namePaint.setShadowLayer(Theme.dpf(context, 1.5f), 0f, Theme.dpf(context, 1f), 0x55000000)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (name.isEmpty() || h <= 0f) return
        c.drawText(if (stamped) CAPTION_BOOM else CAPTION, w / 2f, h * 0.17f, captionPaint)
        val pillH = h * 0.66f
        val pcy = h * 0.62f
        val pw = maxOf(nameWidth + pillH, pillH * 2.6f)
        // 도장이 찍히면 이름 알약이 왼쪽으로 비켜서 도장 자리를 만든다 (이름이 가려지지 않게)
        val shift = if (stamped) stampWidth * 0.34f * minOf(1f, stampT / 0.6f) else 0f
        val pcx = maxOf(w / 2f - shift, minOf(w / 2f, pw / 2f + stroke.strokeWidth))
        // 넘길 때: 작게 → 살짝 크게 → 제자리
        val s = if (swap < 1f) 0.6f + 0.4f * overshoot(swap) else 1f
        c.save()
        c.scale(s, s, pcx, pcy)
        pill.set(pcx - pw / 2f, pcy - pillH / 2f, pcx + pw / 2f, pcy + pillH / 2f)
        fill.color = 0x55000000
        c.drawRoundRect(pill.left, pill.top + h * 0.04f, pill.right, pill.bottom + h * 0.04f, pillH / 2f, pillH / 2f, fill)
        fill.color = color
        c.drawRoundRect(pill, pillH / 2f, pillH / 2f, fill)
        stroke.color = Theme.GOLD
        c.drawRoundRect(pill, pillH / 2f, pillH / 2f, stroke)
        c.drawText(name, pcx, pcy + namePaint.textSize * 0.36f, namePaint)
        c.restore()
        if (stamped) drawStamp(c, minOf(pill.right + stampWidth * 0.16f, w - stampWidth * 0.52f), pcy, pillH)
    }

    /** 빨간 "펑!" 도장: 크게 떨어지며 쾅 → 제자리 */
    private fun drawStamp(c: Canvas, x: Float, y: Float, pillH: Float) {
        val t = stampT
        val s = if (t < 0.6f) 2.4f - 1.5f * (t / 0.6f) else 0.9f + 0.1f * ((t - 0.6f) / 0.4f)
        val a = (minOf(1f, t / 0.35f) * 255).toInt()
        c.save()
        c.rotate(-14f, x, y)
        c.scale(s, s, x, y)
        val sh = pillH * 1.0f
        stampRect.set(x - stampWidth / 2f, y - sh / 2f, x + stampWidth / 2f, y + sh / 2f)
        fill.color = STAMP_RED
        fill.alpha = a
        c.drawRoundRect(stampRect, sh * 0.18f, sh * 0.18f, fill)
        fill.alpha = 255
        stroke.color = 0xFFFFFFFF.toInt()
        stroke.alpha = a
        val inset = sh * 0.09f
        c.drawRoundRect(stampRect.left + inset, stampRect.top + inset, stampRect.right - inset, stampRect.bottom - inset, sh * 0.12f, sh * 0.12f, stroke)
        stroke.alpha = 255
        stampPaint.alpha = a
        c.drawText(STAMP, x, y + stampPaint.textSize * 0.36f, stampPaint)
        c.restore()
    }

    private fun overshoot(t: Float): Float {
        val k = t - 1f
        return 1f + k * k * (2.7f * k + 1.7f)
    }

    override fun onDetachedFromWindow() {
        swapAnim?.cancel()
        stampAnim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val CAPTION = "지금 폭탄을 든 사람"
        private const val CAPTION_BOOM = "폭탄이 터진 사람"
        private const val STAMP = "펑!"
        private const val STAMP_RED = 0xFFD32F2F.toInt()
    }
}
