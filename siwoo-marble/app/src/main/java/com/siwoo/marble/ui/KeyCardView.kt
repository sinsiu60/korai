package com.siwoo.marble.ui

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
import com.siwoo.marble.model.Card

/**
 * 황금열쇠 카드: 뒷면(금색 열쇠)으로 나타났다가 뒤집혀 앞면(내용)을 보여준다.
 * 뒤집기는 rotationY 애니메이션 두 단계(0→90°, -90°→0°)로, 90°에서 면을 바꾼다.
 */
class KeyCardView(context: Context) : View(context) {

    private var front = false
    private var title = ""
    private var bodyLayout: StaticLayout? = null
    private var description = ""

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

    fun setCard(c: Card) {
        title = c.title
        description = c.description
        front = false
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
        backShader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(Theme.GOLD_LIGHT, Theme.GOLD, Theme.GOLD_DARK), null, Shader.TileMode.CLAMP)
        titlePaint.textSize = h * 0.15f
        bodyPaint.textSize = h * 0.095f
        backText.textSize = h * 0.14f
        rebuildLayout()
    }

    private fun rebuildLayout() {
        val w = (card.width() - Theme.dpf(context, 28f)).toInt()
        if (w <= 0 || description.isEmpty()) return
        @Suppress("DEPRECATION")
        bodyLayout = StaticLayout(description, bodyPaint, w, Layout.Alignment.ALIGN_CENTER, 1.15f, 0f, false)
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
            fill.color = Theme.GOLD
            c.drawRect(card.left, card.top + r, card.right, card.top + card.height() * 0.06f + r, fill)
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
