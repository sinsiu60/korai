package com.siwoo.party.common.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/** 카드 뒷면 모양: 황금열쇠(금색) / 벌칙(빨강+번개) */
enum class CardBack { KEY, PENALTY }

/**
 * 뒤집히는 카드 (황금열쇠·벌칙 카드 등): 뒷면으로 나타났다가 뒤집혀 앞면(내용)을 보여준다.
 * badge 가 있으면 앞면 왼쪽 위에 띠 색으로 작은 표시, redTail 이 있으면 본문 끝에 빨간 굵은 글씨로 덧붙인다.
 * 뒤집기는 rotationY 애니메이션 두 단계(0→90°, -90°→0°)로, 90°에서 면을 바꾼다.
 */
class FlipCardView(context: Context) : View(context) {

    private var front = false
    private var title = ""
    private var bodyLayout: StaticLayout? = null
    private var description = ""
    private var penalty = false
    private var bandColor = Theme.GOLD
    private var gradeText = ""
    private var realText = ""
    private val realPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC62828.toInt(); typeface = Typeface.DEFAULT_BOLD }
    private val gradePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.WHITE }
    private val boltPath = android.graphics.Path()

    private val card = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A4060.toInt() }
    private val backText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFF6B4A00.toInt() }
    private var backShader: Shader? = null

    init {
        cameraDistance = 9000f * resources.displayMetrics.density
    }

    /** 내용 설정 (뒷면부터 다시 보인다) */
    fun setContent(
        title: String,
        body: String,
        back: CardBack = CardBack.KEY,
        bandColor: Int = Theme.GOLD,
        badge: String? = null,
        redTail: String? = null,
    ) {
        this.title = title
        realText = redTail ?: ""
        description = body + if (realText.isEmpty()) "" else "\n\n$realText"
        val wasPenalty = penalty
        penalty = back == CardBack.PENALTY
        gradeText = badge ?: ""
        this.bandColor = bandColor
        front = false
        if (wasPenalty != penalty && width > 0) makeBackShader(width, height)
        rebuildLayout()
        invalidate()
    }

    /** 뒤집기 연출. 끝나면 onEnd */
    fun flip(durMs: Long, onEnd: () -> Unit) {
        val half = (durMs / 2).coerceAtLeast(1)
        rotationY = 0f
        animate().rotationY(90f).setDuration(half).setInterpolator(AccelerateInterpolator()).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animate().setListener(null)
                front = true
                invalidate()
                rotationY = -90f
                animate().rotationY(0f).setDuration(half).setInterpolator(DecelerateInterpolator()).setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        animate().setListener(null)
                        onEnd()
                    }
                }).start()
            }
        }).start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = Theme.dpf(context, 4f)
        card.set(pad, pad, w - pad, h - pad)
        makeBackShader(w, h)
        gradePaint.textSize = h * 0.08f
        realPaint.textSize = h * 0.085f
        titlePaint.textSize = h * 0.15f
        bodyPaint.textSize = h * 0.095f
        backText.textSize = h * 0.14f
        rebuildLayout()
    }

    private fun makeBackShader(w: Int, h: Int) {
        backShader = if (penalty) {
            LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFFFF6B6B.toInt(), 0xFFE53935.toInt(), 0xFF8E0000.toInt()), null, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(Theme.GOLD_LIGHT, Theme.GOLD, Theme.GOLD_DARK), null, Shader.TileMode.CLAMP)
        }
    }

    private fun rebuildLayout() {
        val w = (card.width() - Theme.dpf(context, 28f)).toInt()
        if (w <= 0 || description.isEmpty()) return
        // 현실 벌칙 문구는 빨간 굵은 글씨로
        val text = android.text.SpannableString(description)
        if (realText.isNotEmpty()) {
            val at = description.length - realText.length
            if (at >= 0) {
                text.setSpan(android.text.style.ForegroundColorSpan(0xFFC62828.toInt()), at, description.length, 0)
                text.setSpan(android.text.style.StyleSpan(Typeface.BOLD), at, description.length, 0)
            }
        }
        @Suppress("DEPRECATION")
        bodyLayout = StaticLayout(text, bodyPaint, w, Layout.Alignment.ALIGN_CENTER, 1.15f, 0f, false)
    }

    override fun onDraw(c: Canvas) {
        val r = card.height() * 0.1f
        if (!front) {
            fill.shader = backShader
            c.drawRoundRect(card, r, r, fill)
            fill.shader = null
            stroke.color = 0xFF8D6E00.toInt()
            stroke.strokeWidth = card.height() * 0.025f
            val inset = card.height() * 0.06f
            c.drawRoundRect(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset, r * 0.7f, r * 0.7f, stroke)
            if (penalty) {
                // 번개 그림 + "벌칙"
                val bx = card.centerX()
                val by = card.top + card.height() * 0.4f
                val s2 = card.height() * 0.22f
                boltPath.reset()
                boltPath.moveTo(bx + s2 * 0.25f, by - s2)
                boltPath.lineTo(bx - s2 * 0.55f, by + s2 * 0.15f)
                boltPath.lineTo(bx - s2 * 0.02f, by + s2 * 0.15f)
                boltPath.lineTo(bx - s2 * 0.25f, by + s2)
                boltPath.lineTo(bx + s2 * 0.55f, by - s2 * 0.15f)
                boltPath.lineTo(bx + s2 * 0.02f, by - s2 * 0.15f)
                boltPath.close()
                fill.color = Theme.GOLD_LIGHT
                c.drawPath(boltPath, fill)
                backText.color = android.graphics.Color.WHITE
                c.drawText("벌칙", bx, card.bottom - card.height() * 0.17f, backText)
                return
            }
            backText.color = 0xFF6B4A00.toInt()
            // 열쇠 그림
            val x = card.centerX()
            val y = card.top + card.height() * 0.4f
            val s = card.height() * 0.13f
            fill.color = 0xFF6B4A00.toInt()
            c.drawCircle(x - s * 1.1f, y, s, fill)
            fill.color = Theme.GOLD_LIGHT
            c.drawCircle(x - s * 1.1f, y, s * 0.45f, fill)
            fill.color = 0xFF6B4A00.toInt()
            c.drawRect(x - s * 0.2f, y - s * 0.22f, x + s * 1.8f, y + s * 0.22f, fill)
            c.drawRect(x + s * 1.0f, y, x + s * 1.3f, y + s * 0.7f, fill)
            c.drawRect(x + s * 1.5f, y, x + s * 1.8f, y + s * 0.55f, fill)
            c.drawText("황금열쇠", x, card.bottom - card.height() * 0.17f, backText)
        } else {
            fill.color = Theme.PAPER
            c.drawRoundRect(card, r, r, fill)
            fill.color = bandColor
            c.drawRect(card.left, card.top + r, card.right, card.top + card.height() * 0.06f + r, fill)
            if (gradeText.isNotEmpty()) {
                // 등급 표시 (왼쪽 위 작은 띠)
                val gw = gradePaint.measureText(gradeText) + card.height() * 0.08f
                c.drawRoundRect(card.left + r, card.top + r * 0.4f, card.left + r + gw, card.top + r * 0.4f + card.height() * 0.12f, r * 0.5f, r * 0.5f, fill)
                c.drawText(gradeText, card.left + r + card.height() * 0.04f, card.top + r * 0.4f + card.height() * 0.092f, gradePaint)
            }
            stroke.color = Theme.GOLD_DARK
            stroke.strokeWidth = card.height() * 0.02f
            c.drawRoundRect(card, r, r, stroke)
            c.drawText(title, card.centerX(), card.top + card.height() * 0.36f, titlePaint)
            val bl = bodyLayout ?: return
            c.save()
            c.translate(card.left + Theme.dpf(context, 14f), card.top + card.height() * 0.48f)
            bl.draw(c)
            c.restore()
        }
    }
}
