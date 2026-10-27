package com.siwoo.party.games.onecard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.cards.Suit
import com.siwoo.party.common.ui.Theme

/**
 * 7 을 냈을 때 무늬 고르기 팝업의 큰 버튼 하나: 종이 카드 + 큰 무늬 + 이름 + "내 손에 N장".
 * 누르면 살짝 눌려 들어간다. 클릭은 setOnClickListener 로.
 */
class SuitTileView(context: Context, val suit: Suit, countInHand: Int) : View(context) {

    private val density = resources.displayMetrics.density
    private val name = suit.koName
    private val countText = "내 손에 ${countInHand}장"
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = SuitArt.color(suit) }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xFF5A6080.toInt() }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = name
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        paper.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Theme.PAPER, 0xFFEFE3C4.toInt(), Shader.TileMode.CLAMP)
        namePaint.textSize = minOf(h * 0.14f, w * 0.2f)
        subPaint.textSize = minOf(h * 0.085f, w * 0.12f)
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val d = density
        val push = if (isPressed) 3f * d else 0f
        val w = width.toFloat()
        val h = height.toFloat()
        val r = w * 0.12f
        rect.set(2f * d, 2f * d + 4f * d, w - 2f * d, h - 2f * d)
        fill.color = 0xFF8C6A1E.toInt()
        c.drawRoundRect(rect, r, r, fill)
        rect.set(2f * d, 2f * d + push, w - 2f * d, h - 6f * d + push)
        c.drawRoundRect(rect, r, r, paper)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = 2.5f * d
        c.drawRoundRect(rect, r, r, stroke)
        fill.color = SuitArt.color(suit)
        SuitArt.draw(c, suit, rect.centerX(), rect.top + rect.height() * 0.38f, rect.height() * 0.42f, fill)
        c.drawText(name, rect.centerX(), rect.top + rect.height() * 0.78f, namePaint)
        c.drawText(countText, rect.centerX(), rect.top + rect.height() * 0.93f, subPaint)
    }
}
