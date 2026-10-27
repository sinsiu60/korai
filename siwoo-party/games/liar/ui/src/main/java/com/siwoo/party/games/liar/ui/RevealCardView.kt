package com.siwoo.party.games.liar.ui

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
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.siwoo.party.common.ui.Theme

/**
 * 뒤집히는 공개 카드 두 가지.
 * - 카테고리: 뒷면(보라 "?") → 앞면(카테고리 색 띠 + 아이콘 + 이름)
 * - 정체: 뒷면(지목된 사람 이름 + "?") → 앞면(빨강 "라이어!" + 가면 얼굴 / 파랑 "시민")
 * 뒤집기는 rotationY 두 단계(0→90°, -90°→0°), 90°에서 면을 바꾼다. 글자는 set 할 때만 만들고 크기를 맞춘다.
 */
class RevealCardView(context: Context) : View(context) {

    private enum class Kind { CATEGORY, LIAR, CITIZEN }

    private var kind = Kind.CATEGORY
    private var category = 0
    /** 앞면 큰 글자 / 작은 글자 */
    private var title = ""
    private var sub = ""
    /** 앞면 띠 글자 (카테고리) */
    private var label = ""
    /** 뒷면 글자 */
    private var backTitle = ""
    private var backSub = ""
    private var accent = Theme.GOLD

    /** 앞면이 보이는지 */
    var front = false
        private set

    private val card = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
    private val qPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val path = Path()
    private var backShader: Shader? = null
    private var frontShader: Shader? = null

    init {
        cameraDistance = 9000f * resources.displayMetrics.density
    }

    /** 카테고리 카드 (뒷면부터) */
    fun setCategory(k: Int, name: String) {
        kind = Kind.CATEGORY
        category = k
        title = name
        sub = ""
        label = FRONT_LABEL
        backTitle = BACK_Q
        backSub = BACK_LABEL
        accent = LiarArt.categoryColor(k)
        reset()
    }

    /** 정체 카드: name 이 라이어인지 (뒷면부터). sub 는 앞면 아래 작은 글자 */
    fun setRole(name: String, color: Int, liar: Boolean, sub: String) {
        kind = if (liar) Kind.LIAR else Kind.CITIZEN
        title = if (liar) LIAR_TEXT else CITIZEN_TEXT
        this.sub = sub
        label = ""
        backTitle = name
        backSub = ROLE_BACK_SUB
        accent = color
        reset()
    }

    private fun reset() {
        front = false
        contentDescription = backTitle
        makeShaders()
        fit()
        invalidate()
    }

    /** 바로 앞면으로 (연출 없이) */
    fun showFront() {
        animate().cancel()
        animate().setListener(null)
        scaleX = 1f; scaleY = 1f; rotation = 0f; rotationY = 0f; alpha = 1f
        front = true
        contentDescription = title
        invalidate()
    }

    /** 빙글 확대 등장(zoomMs, 0 이면 바로) → 뒤집기(flipMs) → onEnd */
    fun reveal(zoomMs: Long, flipMs: Long, onEnd: () -> Unit) {
        front = false
        rotationY = 0f
        if (zoomMs <= 0L) {
            scaleX = 1f; scaleY = 1f; rotation = 0f; alpha = 1f
            flip(flipMs, onEnd)
            return
        }
        scaleX = 0.2f; scaleY = 0.2f; rotation = -14f; alpha = 0f
        animate().scaleX(1f).scaleY(1f).rotation(0f).alpha(1f).setDuration(zoomMs)
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
                contentDescription = title
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
        makeShaders()
        fit()
    }

    private fun makeShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        backShader = if (kind == Kind.CATEGORY) {
            LinearGradient(0f, 0f, w, h, intArrayOf(0xFF8E5BFF.toInt(), 0xFF5B2DB5.toInt(), 0xFF2B1068.toInt()), null, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, 0f, 0f, h, Theme.CARD_LIGHT, Theme.BG_DEEP, Shader.TileMode.CLAMP)
        }
        frontShader = when (kind) {
            Kind.LIAR -> LinearGradient(0f, 0f, w, h, intArrayOf(0xFFFF6B7A.toInt(), LiarArt.LIAR_RED, 0xFF8C0F24.toInt()), null, Shader.TileMode.CLAMP)
            Kind.CITIZEN -> LinearGradient(0f, 0f, w, h, intArrayOf(0xFF6FA8FF.toInt(), LiarArt.CITIZEN_BLUE, 0xFF143D8C.toInt()), null, Shader.TileMode.CLAMP)
            Kind.CATEGORY -> null
        }
    }

    /** 글자 크기 맞추기 (값이 바뀔 때만) */
    private fun fit() {
        val h = card.height()
        val w = card.width()
        if (h <= 0f) return
        qPaint.textSize = h * 0.5f
        subPaint.textSize = h * 0.11f
        shrink(subPaint, sub, w * 0.86f)
        bigPaint.textSize = h * 0.3f
        shrink(bigPaint, title, w * 0.5f)
        // 뒷면 이름 크기를 재 두고 띠 글자 크기로 되돌린다
        labelPaint.textSize = h * 0.2f
        shrink(labelPaint, backTitle, w * 0.8f)
        backNameSize = labelPaint.textSize
        labelPaint.textSize = h * 0.12f
    }

    private var backNameSize = 0f

    private fun shrink(p: Paint, s: String, maxW: Float) {
        while (s.isNotEmpty() && p.measureText(s) > maxW && p.textSize > Theme.dpf(context, 8f)) p.textSize *= 0.92f
    }

    override fun onDraw(c: Canvas) {
        if (card.width() <= 0f) return
        val h = card.height()
        val r = h * 0.1f
        // 그림자
        fill.shader = null
        fill.color = 0x55000000
        c.drawRoundRect(card.left + h * 0.02f, card.top + h * 0.04f, card.right + h * 0.02f, card.bottom + h * 0.04f, r, r, fill)
        if (!front) drawBack(c, h, r) else when (kind) {
            Kind.CATEGORY -> drawCategoryFront(c, h, r)
            else -> drawRoleFront(c, h, r)
        }
    }

    private fun drawBack(c: Canvas, h: Float, r: Float) {
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = backShader
        c.drawRoundRect(card, r, r, fill)
        fill.shader = null
        stroke.color = if (kind == Kind.CATEGORY) Theme.GOLD_LIGHT else accent
        stroke.strokeWidth = h * 0.025f
        val inset = h * 0.06f
        c.drawRoundRect(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset, r * 0.7f, r * 0.7f, stroke)
        if (kind == Kind.CATEGORY) {
            // 큰 물음표 + "카테고리"
            qPaint.color = Theme.GOLD
            c.drawText(backTitle, card.centerX(), card.top + h * 0.62f, qPaint)
            labelPaint.color = 0xFFFFFFFF.toInt()
            c.drawText(backSub, card.centerX(), card.bottom - h * 0.12f, labelPaint)
        } else {
            // 지목된 사람 이름 + "정체는…?"
            labelPaint.color = accent
            labelPaint.textSize = backNameSize
            c.drawText(backTitle, card.centerX(), card.top + h * 0.42f, labelPaint)
            labelPaint.textSize = h * 0.12f
            labelPaint.color = Theme.TEXT_SUB
            c.drawText(backSub, card.centerX(), card.top + h * 0.68f, labelPaint)
            labelPaint.color = 0xFFFFFFFF.toInt()
        }
    }

    private fun drawCategoryFront(c: Canvas, h: Float, r: Float) {
        fill.color = Theme.PAPER
        c.drawRoundRect(card, r, r, fill)
        val bandB = card.top + h * 0.24f
        fill.color = accent
        c.drawRoundRect(card.left, card.top, card.right, bandB, r, r, fill)
        c.drawRect(card.left, bandB - r, card.right, bandB, fill)
        labelPaint.color = 0xFFFFFFFF.toInt()
        c.drawText(label, card.centerX(), card.top + h * 0.165f, labelPaint)
        stroke.color = Theme.GOLD_DARK
        stroke.strokeWidth = h * 0.02f
        c.drawRoundRect(card, r, r, stroke)
        // 아이콘 (원 배경) + 이름
        val midY = (bandB + card.bottom) / 2f
        val ix = card.left + card.width() * 0.26f
        fill.color = accent
        fill.alpha = 40
        c.drawCircle(ix, midY, h * 0.27f, fill)
        fill.alpha = 255
        LiarArt.drawCategory(c, category, ix, midY, h * 0.4f, accent, art, path)
        bigPaint.color = Theme.INK
        c.drawText(title, card.left + card.width() * 0.66f, midY + bigPaint.textSize * 0.36f, bigPaint)
    }

    private fun drawRoleFront(c: Canvas, h: Float, r: Float) {
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = frontShader
        c.drawRoundRect(card, r, r, fill)
        fill.shader = null
        stroke.color = Theme.GOLD_LIGHT
        stroke.strokeWidth = h * 0.025f
        val inset = h * 0.06f
        c.drawRoundRect(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset, r * 0.7f, r * 0.7f, stroke)
        val ix = card.left + card.width() * 0.25f
        val iy = card.top + h * 0.44f
        if (kind == Kind.LIAR) {
            LiarArt.drawLiarFace(c, ix - h * 0.06f, iy, h * 0.24f, art, path)
        } else {
            art.color = 0x33FFFFFF
            c.drawCircle(ix, iy, h * 0.27f, art)
            LiarArt.drawCheck(c, ix, iy, h * 0.3f, 0xFFFFFFFF.toInt(), art)
        }
        bigPaint.color = 0xFFFFFFFF.toInt()
        c.drawText(title, card.left + card.width() * 0.66f, iy + bigPaint.textSize * 0.36f, bigPaint)
        if (sub.isNotEmpty()) {
            subPaint.color = 0xFFFFFFFF.toInt()
            c.drawText(sub, card.centerX(), card.bottom - h * 0.13f, subPaint)
        }
    }

    companion object {
        private const val FRONT_LABEL = "이번 카테고리"
        private const val BACK_LABEL = "카테고리"
        private const val BACK_Q = "?"
        private const val ROLE_BACK_SUB = "정체는...?"
        private const val LIAR_TEXT = "라이어!"
        private const val CITIZEN_TEXT = "시민"
    }
}
