package com.siwoo.party.games.bomb.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.siwoo.party.common.ui.Theme

/**
 * 주제 카드: 뒷면(주황 폭탄 무늬 "주제")으로 빙글 확대되며 등장 → 뒤집혀 앞면(이번 주제)을 보여준다.
 * 뒤집기는 rotationY 두 단계(0→90°, -90°→0°), 90°에서 면을 바꾼다 (공통 FlipCardView 와 같은 방식).
 */
class TopicCardView(context: Context) : View(context) {

    var topic = ""
        private set
    /** 앞면(주제)이 보이는지 */
    var front = false
        private set

    private var topicLayout: StaticLayout? = null
    private val card = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val topicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.INK }
    private val path = Path()
    private var backShader: Shader? = null

    init {
        cameraDistance = 9000f * resources.displayMetrics.density
    }

    /** 주제 설정 (뒷면부터 다시) */
    fun setTopic(t: String) {
        topic = t
        front = false
        contentDescription = "주제 카드"
        rebuildLayout()
        invalidate()
    }

    /** 바로 앞면으로 (연출 없이) */
    fun showFront() {
        animate().cancel()
        scaleX = 1f; scaleY = 1f; rotation = 0f; rotationY = 0f; alpha = 1f
        front = true
        contentDescription = topic
        invalidate()
    }

    /** 빙글 확대 등장(zoomMs) → 뒤집기(flipMs) → onEnd */
    fun reveal(zoomMs: Long, flipMs: Long, onEnd: () -> Unit) {
        front = false
        scaleX = 0.2f; scaleY = 0.2f; rotation = -14f; alpha = 0f; rotationY = 0f
        animate().scaleX(1f).scaleY(1f).rotation(0f).alpha(1f).setDuration(zoomMs.coerceAtLeast(1))
            .setInterpolator(OvershootInterpolator(1.3f)).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animate().setListener(null)
                    flip(flipMs, onEnd)
                }
            }).start()
    }

    private fun flip(durMs: Long, onEnd: () -> Unit) {
        val half = (durMs / 2).coerceAtLeast(1)
        animate().rotationY(90f).setDuration(half).setInterpolator(AccelerateInterpolator()).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animate().setListener(null)
                front = true
                contentDescription = topic
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
        backShader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFFFF8A3D.toInt(), 0xFFE64A19.toInt(), 0xFF8E1B00.toInt()), null, Shader.TileMode.CLAMP)
        rebuildLayout()
    }

    /** 주제 글자: 두 줄 안에 들어오는 가장 큰 크기로 (값이 바뀔 때만) */
    private fun rebuildLayout() {
        val w = (card.width() - Theme.dpf(context, 24f)).toInt()
        if (w <= 0 || topic.isEmpty()) return
        val maxH = card.height() * 0.5f
        var size = card.height() * 0.24f
        var lay: StaticLayout
        while (true) {
            topicPaint.textSize = size
            @Suppress("DEPRECATION")
            lay = StaticLayout(topic, topicPaint, w, Layout.Alignment.ALIGN_CENTER, 1.05f, 0f, false)
            val tooWide = lay.lineCount == 1 && topicPaint.measureText(topic) > w
            if ((lay.lineCount <= 2 && lay.height <= maxH && !tooWide) || size < card.height() * 0.1f) break
            size *= 0.92f
        }
        topicLayout = lay
    }

    override fun onDraw(c: Canvas) {
        if (card.width() <= 0f) return
        val h = card.height()
        val r = h * 0.1f
        // 그림자
        fill.color = 0x55000000
        c.drawRoundRect(card.left + h * 0.02f, card.top + h * 0.04f, card.right + h * 0.02f, card.bottom + h * 0.04f, r, r, fill)
        if (!front) drawBack(c, h, r) else drawFront(c, h, r)
    }

    private fun drawBack(c: Canvas, h: Float, r: Float) {
        fill.color = 0xFFFFFFFF.toInt() // 그림자 색의 투명도가 남지 않게
        fill.shader = backShader
        c.drawRoundRect(card, r, r, fill)
        fill.shader = null
        stroke.color = Theme.GOLD_LIGHT
        stroke.strokeWidth = h * 0.025f
        val inset = h * 0.06f
        c.drawRoundRect(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset, r * 0.7f, r * 0.7f, stroke)
        val br = h * 0.2f
        val bx = card.left + card.width() * 0.3f
        val by = card.centerY() + h * 0.1f
        BombArt.drawBomb(c, bx, by, br, fill, path)
        BombArt.drawFlame(c, bx + br * BombArt.TIP_X, by + br * BombArt.TIP_Y, br * 0.45f, fill, path)
        labelPaint.color = 0xFFFFFFFF.toInt()
        labelPaint.textSize = h * 0.26f
        c.drawText(BACK_TEXT, card.left + card.width() * 0.66f, card.centerY() + labelPaint.textSize * 0.36f, labelPaint)
    }

    private fun drawFront(c: Canvas, h: Float, r: Float) {
        fill.color = Theme.PAPER
        c.drawRoundRect(card, r, r, fill)
        // 위 띠 + "이번 주제"
        val bandB = card.top + h * 0.27f
        fill.color = BAND
        c.drawRoundRect(card.left, card.top, card.right, bandB, r, r, fill)
        c.drawRect(card.left, bandB - r, card.right, bandB, fill)
        labelPaint.color = 0xFFFFFFFF.toInt()
        labelPaint.textSize = h * 0.14f
        c.drawText(FRONT_LABEL, card.centerX(), card.top + h * 0.185f, labelPaint)
        BombArt.drawFlame(c, card.left + h * 0.17f, card.top + h * 0.135f, h * 0.085f, fill, path)
        BombArt.drawFlame(c, card.right - h * 0.17f, card.top + h * 0.135f, h * 0.085f, fill, path)
        stroke.color = Theme.GOLD_DARK
        stroke.strokeWidth = h * 0.02f
        c.drawRoundRect(card, r, r, stroke)
        val lay = topicLayout ?: return
        val areaTop = bandB
        val y = areaTop + (card.bottom - areaTop - lay.height) / 2f
        c.save()
        c.translate(card.left + Theme.dpf(context, 12f), y)
        lay.draw(c)
        c.restore()
    }

    companion object {
        private const val BACK_TEXT = "주제"
        private const val FRONT_LABEL = "이번 주제"
        private const val BAND = 0xFFE64A19.toInt()
    }
}
