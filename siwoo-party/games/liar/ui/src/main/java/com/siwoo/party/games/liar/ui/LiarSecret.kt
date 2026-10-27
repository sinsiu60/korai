package com.siwoo.party.games.liar.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.secret.SecretContent
import com.siwoo.party.common.ui.Theme

/**
 * 라이어 게임 제시어 카드의 "보이는 면" (공통 SecretHoldView 안에서 꾹 누르는 동안만 그려진다).
 * - 제시어: 밝은 카드 + 카테고리 색 띠(뜬 눈 두 개) + 큰 단어 (바보 모드 라이어의 가짜 단어도 똑같이)
 * - 일반 모드 라이어(word == null): 빨간 카드 + 가면 얼굴 + "당신은 라이어입니다"
 * 글자 크기·Shader 는 layout 때만 → draw 할당 없음.
 */
class LiarSecret(word: String?, categoryName: String, private val color: Int) : SecretContent {

    private var word = word ?: ""
    private val liar = word == null
    private var sub = if (liar) "들키지 않게 설명하세요! · 카테고리: $categoryName" else "카테고리: $categoryName"
    private var cleared = false

    override val text: String get() = if (cleared) "" else if (liar) LIAR_TEXT else word

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val liarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
    private val path = Path()
    private var liarShader: Shader? = null
    private var minText = 8f

    override fun layout(host: View, card: RectF) {
        val h = card.height()
        val w = card.width()
        if (h <= 0f) return
        minText = Theme.dpf(host.context, 8f)
        liarShader = LinearGradient(card.left, card.top, card.right, card.bottom,
            intArrayOf(0xFFFF6B7A.toInt(), LiarArt.LIAR_RED, 0xFF8C0F24.toInt()), null, Shader.TileMode.CLAMP)
        wordPaint.textSize = h * 0.3f
        shrink(wordPaint, word, w * 0.86f)
        labelPaint.textSize = h * 0.1f
        subPaint.textSize = h * 0.085f
        shrink(subPaint, sub, w * 0.9f)
        liarPaint.textSize = h * 0.15f
        shrink(liarPaint, LIAR_TEXT, w * 0.84f)
    }

    private fun shrink(p: Paint, s: String, maxW: Float) {
        while (s.isNotEmpty() && p.measureText(s) > maxW && p.textSize > minText) p.textSize *= 0.92f
    }

    override fun draw(c: Canvas, card: RectF) {
        if (cleared) return
        val h = card.height()
        val r = h * 0.1f
        if (liar) drawLiar(c, card, h, r) else drawWord(c, card, h, r)
    }

    private fun drawWord(c: Canvas, card: RectF, h: Float, r: Float) {
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

    private fun drawLiar(c: Canvas, card: RectF, h: Float, r: Float) {
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

    override fun clear() {
        cleared = true
        word = ""
        sub = ""
    }

    companion object {
        const val LIAR_TEXT = "당신은 라이어입니다"
        private const val WORD_LABEL = "나의 제시어"
    }
}
