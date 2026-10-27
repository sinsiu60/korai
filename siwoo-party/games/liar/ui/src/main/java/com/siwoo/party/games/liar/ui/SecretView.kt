package com.siwoo.party.games.liar.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 제시어 확인 카드: 꾹 누르고 있는 동안만 제시어가 보이고, 손을 떼는 순간(ACTION_UP/CANCEL) 바로 가려진다 (서서히 사라지지 않음).
 * - 가려짐: 남색 카드 + 감은 눈 + "꾹 누르고 있는 동안만 보여요"
 * - 보임(제시어): 밝은 카드 + 카테고리 색 띠 + 큰 단어 (바보 모드 라이어의 가짜 단어도 똑같이)
 * - 보임(일반 모드 라이어): 빨간 카드 + 가면 얼굴 + "당신은 라이어입니다"
 * 다음 사람으로 넘어가기 전에 clear() 로 글자를 지운다 → 전환 화면 뒤에 이전 사람 정보가 남지 않음.
 * 글자 크기는 setSecret 때만 계산 → onDraw 할당 없음.
 */
class SecretView(context: Context) : View(context) {

    /** 보여 줄 단어 (라이어면 "") */
    private var word = ""
    private var liar = false
    private var sub = ""
    private var color = Theme.GOLD

    /** 지금 누르고 있어 보이는 중 */
    var revealed = false
        private set
    /** 한 번이라도 꾹 눌러 봤는지 ([확인 완료] 켜기) */
    var heldOnce = false
        private set
    /** 처음 눌렀을 때 / 보임·가림이 바뀔 때 */
    var onHoldChanged: ((Boolean) -> Unit)? = null

    /** 지금 화면에 보이는 비밀 글자 (가려져 있으면 "") — 테스트·접근성 확인용 */
    val shownText: String get() = if (!revealed) "" else if (liar) LIAR_TEXT else word
    /** 비밀이 들어 있는지 (clear 뒤엔 false) */
    val hasSecret: Boolean get() = liar || word.isNotEmpty()

    private val card = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val liarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
    private val path = Path()
    private var hiddenShader: Shader? = null
    private var liarShader: Shader? = null

    init {
        contentDescription = DESC
        isClickable = true
    }

    /**
     * 비밀 넣기. word == null 이면 "당신은 라이어입니다" (일반 모드 라이어).
     * categoryName·color 는 카테고리 (모두 아는 정보)
     */
    fun setSecret(word: String?, categoryName: String, color: Int) {
        this.word = word ?: ""
        liar = word == null
        this.color = color
        sub = if (liar) "들키지 않게 설명하세요! · 카테고리: $categoryName" else "카테고리: $categoryName"
        revealed = false
        heldOnce = false
        fit()
        invalidate()
    }

    /** 비밀 지우기 (다음 사람으로 넘어가기 전) */
    fun clear() {
        word = ""
        liar = false
        sub = ""
        revealed = false
        heldOnce = false
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                show(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> show(false)
        }
        return true
    }

    private fun show(v: Boolean) {
        if (v && !hasSecret) return
        if (revealed == v) return
        revealed = v
        if (v) heldOnce = true
        invalidate()
        onHoldChanged?.invoke(v)
    }

    /** 가려진 채로 (손을 떼지 않고 화면이 바뀌는 경우 등) */
    fun hide() = show(false)

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) hide()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) hide()
    }

    override fun onDetachedFromWindow() {
        revealed = false
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = Theme.dpf(context, 4f)
        card.set(pad, pad, w - pad, h - pad)
        hiddenShader = LinearGradient(0f, 0f, 0f, h.toFloat(), Theme.CARD_LIGHT, Theme.BG_DEEP, Shader.TileMode.CLAMP)
        liarShader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFFFF6B7A.toInt(), LiarArt.LIAR_RED, 0xFF8C0F24.toInt()), null, Shader.TileMode.CLAMP)
        val d = Theme.dpf(context, 8f)
        dash.pathEffect = DashPathEffect(floatArrayOf(d, d * 0.7f), 0f)
        fit()
    }

    /** 글자 크기 (setSecret·크기 바뀔 때만) */
    private fun fit() {
        val h = card.height()
        val w = card.width()
        if (h <= 0f) return
        wordPaint.textSize = h * 0.3f
        shrink(wordPaint, word, w * 0.86f)
        labelPaint.textSize = h * 0.1f
        hintPaint.textSize = h * 0.115f
        shrink(hintPaint, HINT, w * 0.86f)
        subPaint.textSize = h * 0.085f
        shrink(subPaint, sub, w * 0.9f)
        shrink(subPaint, HINT_SUB, w * 0.82f)
        liarPaint.textSize = h * 0.15f
        shrink(liarPaint, LIAR_TEXT, w * 0.84f)
        dash.strokeWidth = Theme.dpf(context, 2f)
    }

    private fun shrink(p: Paint, s: String, maxW: Float) {
        while (s.isNotEmpty() && p.measureText(s) > maxW && p.textSize > Theme.dpf(context, 8f)) p.textSize *= 0.92f
    }

    override fun onDraw(c: Canvas) {
        if (card.width() <= 0f) return
        val h = card.height()
        val r = h * 0.1f
        fill.shader = null
        fill.color = 0x55000000
        c.drawRoundRect(card.left + h * 0.02f, card.top + h * 0.04f, card.right + h * 0.02f, card.bottom + h * 0.04f, r, r, fill)
        if (!revealed || !hasSecret) drawHidden(c, h, r)
        else if (liar) drawLiar(c, h, r)
        else drawWord(c, h, r)
    }

    private fun drawHidden(c: Canvas, h: Float, r: Float) {
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = hiddenShader
        c.drawRoundRect(card, r, r, fill)
        fill.shader = null
        val inset = h * 0.06f
        dash.color = Theme.GOLD
        c.drawRoundRect(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset, r * 0.7f, r * 0.7f, dash)
        // 누르는 곳 (동심원) + 감은 눈
        val cx = card.centerX()
        val cy = card.top + h * 0.36f
        stroke.color = Theme.GOLD
        for (k in 1..3) {
            stroke.alpha = 220 - k * 55
            stroke.strokeWidth = h * 0.018f
            c.drawCircle(cx, cy, h * (0.1f + k * 0.055f), stroke)
        }
        stroke.alpha = 255
        LiarArt.drawEye(c, cx, cy - h * 0.02f, h * 0.2f, false, Theme.GOLD_LIGHT, art)
        c.drawText(HINT, cx, card.top + h * 0.75f, hintPaint)
        subPaint.color = Theme.TEXT_SUB
        c.drawText(HINT_SUB, cx, card.top + h * 0.88f, subPaint)
    }

    private fun drawWord(c: Canvas, h: Float, r: Float) {
        fill.color = Theme.PAPER
        c.drawRoundRect(card, r, r, fill)
        val bandB = card.top + h * 0.2f
        fill.color = color
        c.drawRoundRect(card.left, card.top, card.right, bandB, r, r, fill)
        c.drawRect(card.left, bandB - r, card.right, bandB, fill)
        c.drawText(WORD_LABEL, card.centerX(), card.top + h * 0.137f, labelPaint)
        LiarArt.drawEye(c, card.left + h * 0.16f, card.top + h * 0.1f, h * 0.13f, true, 0xFFFFFFFF.toInt(), art)
        LiarArt.drawEye(c, card.right - h * 0.16f, card.top + h * 0.1f, h * 0.13f, true, 0xFFFFFFFF.toInt(), art)
        stroke.color = Theme.GOLD_DARK
        stroke.strokeWidth = h * 0.02f
        c.drawRoundRect(card, r, r, stroke)
        c.drawText(word, card.centerX(), card.top + h * 0.6f + wordPaint.textSize * 0.36f - h * 0.04f, wordPaint)
        subPaint.color = 0xFF5A6080.toInt()
        c.drawText(sub, card.centerX(), card.bottom - h * 0.08f, subPaint)
    }

    private fun drawLiar(c: Canvas, h: Float, r: Float) {
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = liarShader
        c.drawRoundRect(card, r, r, fill)
        fill.shader = null
        stroke.color = Theme.GOLD_LIGHT
        stroke.strokeWidth = h * 0.02f
        val inset = h * 0.05f
        c.drawRoundRect(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset, r * 0.7f, r * 0.7f, stroke)
        // 위: 가면 얼굴 (코가 오른쪽으로 나오므로 살짝 왼쪽) / 아래: 큰 글자
        LiarArt.drawLiarFace(c, card.centerX() - h * 0.08f, card.top + h * 0.32f, h * 0.17f, art, path)
        c.drawText(LIAR_TEXT, card.centerX(), card.top + h * 0.69f, liarPaint)
        subPaint.color = 0xFFFFFFFF.toInt()
        c.drawText(sub, card.centerX(), card.bottom - h * 0.12f, subPaint)
    }

    companion object {
        const val LIAR_TEXT = "당신은 라이어입니다"
        private const val WORD_LABEL = "나의 제시어"
        private const val HINT = "꾹 누르고 있는 동안만 보여요"
        private const val HINT_SUB = "손을 떼면 바로 가려져요 · 다른 사람은 보지 마세요"
        private const val DESC = "제시어 확인 카드"
    }
}
