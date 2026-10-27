package com.siwoo.party.common.secret

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 글자 비밀 카드 (기본 제공): 밝은 종이 카드 + 위쪽 색 띠(title) + 가운데 큰 글자(big) + 아래 작은 글자(sub).
 * 예) TextSecret("나의 비밀 숫자", "427", "아무에게도 보여 주지 마세요", Theme.BLUE)
 * 글자 크기는 layout 때만 맞춘다 → draw 할당 없음.
 *
 * @param title 띠 안의 제목 ("" 이면 띠 글자 없음)
 * @param big 가운데 큰 글자 (한 줄, 길면 줄여서 맞춤)
 * @param sub 아래 작은 글자 ("" 이면 없음)
 * @param bandColor 띠 색
 * @param bigColor 큰 글자 색
 * @param paperColor 카드 바탕색
 */
open class TextSecret(
    title: String,
    big: String,
    sub: String = "",
    val bandColor: Int = Theme.GOLD_DARK,
    val bigColor: Int = Theme.INK,
    val paperColor: Int = Theme.PAPER,
) : SecretContent {

    var title = title
        private set
    var big = big
        private set
    var sub = sub
        private set

    override val text: String get() = big

    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    protected val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    protected val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    protected val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xFF5A6080.toInt() }
    private var minText = 8f

    override fun layout(host: View, card: RectF) {
        val h = card.height()
        val w = card.width()
        if (h <= 0f) return
        minText = Theme.dpf(host.context, 8f)
        titlePaint.color = Theme.textOn(bandColor)
        bigPaint.color = bigColor
        titlePaint.textSize = h * 0.1f
        fitText(titlePaint, title, w * 0.7f)
        bigPaint.textSize = h * 0.3f
        fitText(bigPaint, big, w * 0.86f)
        subPaint.textSize = h * 0.075f
        fitText(subPaint, sub, w * 0.88f)
    }

    /** 글자가 maxW 를 넘으면 줄인다 (layout 때만) */
    protected fun fitText(p: Paint, s: String, maxW: Float) {
        while (s.isNotEmpty() && p.measureText(s) > maxW && p.textSize > minText) p.textSize *= 0.92f
    }

    override fun draw(c: Canvas, card: RectF) {
        val h = card.height()
        val r = h * 0.1f
        fill.color = paperColor
        c.drawRoundRect(card, r, r, fill)
        // 위쪽 색 띠 (아래 모서리는 각지게)
        val bandB = card.top + h * 0.2f
        fill.color = bandColor
        c.drawRoundRect(card.left, card.top, card.right, bandB, r, r, fill)
        c.drawRect(card.left, bandB - r, card.right, bandB, fill)
        // 띠 양쪽 장식 (작은 다이아몬드)
        fill.color = 0x66FFFFFF
        drawDiamond(c, card.left + h * 0.12f, card.top + h * 0.1f, h * 0.035f)
        drawDiamond(c, card.right - h * 0.12f, card.top + h * 0.1f, h * 0.035f)
        if (title.isNotEmpty()) c.drawText(title, card.centerX(), card.top + h * 0.1f + titlePaint.textSize * 0.36f, titlePaint)
        stroke.color = Theme.GOLD_DARK
        stroke.strokeWidth = h * 0.02f
        c.drawRoundRect(card, r, r, stroke)
        drawBody(c, card, h)
        if (sub.isNotEmpty()) c.drawText(sub, card.centerX(), card.bottom - h * 0.08f, subPaint)
    }

    /** 가운데 내용 (기본: 큰 글자). 띠 아래 ~ sub 위 사이 */
    protected open fun drawBody(c: Canvas, card: RectF, h: Float) {
        c.drawText(big, card.centerX(), card.top + h * 0.56f + bigPaint.textSize * 0.36f, bigPaint)
    }

    private fun drawDiamond(c: Canvas, cx: Float, cy: Float, s: Float) {
        c.save()
        c.rotate(45f, cx, cy)
        c.drawRect(cx - s, cy - s, cx + s, cy + s, fill)
        c.restore()
    }

    override fun clear() {
        title = ""
        big = ""
        sub = ""
    }
}
