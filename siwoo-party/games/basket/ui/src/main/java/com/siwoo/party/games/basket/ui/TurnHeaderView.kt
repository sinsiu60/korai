package com.siwoo.party.games.basket.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 위쪽 차례 표시: [슛 n/10] (공·불꽃) "가람 차례" + 안내 한 줄 | 오른쪽: [4.2m · 2점] 거리 배지 + [불꽃 +1] 배지.
 * 글자는 setter 에서만 만든다 (onDraw 할당 없음).
 */
class TurnHeaderView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)

    private var roundText = ""
    private var nameText = ""
    private var hintText = ""
    private var spotText = ""
    private var three = false
    private var fire = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT_SUB }
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()
    private var nameColor = Theme.GOLD

    /** 지금 보이는 글자 (테스트용) */
    val shownName: String get() = nameText
    val shownHint: String get() = hintText
    val shownSpot: String get() = spotText
    val shownFire: Boolean get() = fire

    fun setRound(round: Int, total: Int) {
        roundText = "슛 " + (round + 1) + " / " + total
        invalidate()
    }

    fun setTurn(name: String, color: Int) {
        nameText = name
        nameColor = color
        invalidate()
    }

    fun setHint(text: String) {
        if (hintText == text) return
        hintText = text
        invalidate()
    }

    /** 거리·점수 배지 */
    fun setSpot(distance: Float, isThree: Boolean) {
        spotText = (Math.round(distance * 10f) / 10f).toString() + "m · " + (if (isThree) "3점" else "2점")
        three = isThree
        invalidate()
    }

    fun setFire(on: Boolean) {
        if (fire == on) return
        fire = on
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pillText.textSize = 13f * d
        namePaint.textSize = minOf(18f * d, h * 0.4f)
        hintPaint.textSize = minOf(12f * d, h * 0.27f)
        badgeText.textSize = 14f * d
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        val cy = h / 2f
        // 바탕 카드
        p.color = Theme.CARD
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, 12f * d, 12f * d, p)
        // 슛 번호 알약
        val pw = pillText.measureText(roundText) + 18f * d
        p.color = Theme.GOLD
        rect.set(8f * d, cy - 12f * d, 8f * d + pw, cy + 12f * d)
        c.drawRoundRect(rect, 12f * d, 12f * d, p)
        c.drawText(roundText, rect.centerX(), cy + pillText.textSize * 0.36f, pillText)
        // 공 (사람 색 테두리) + 불꽃
        val br = h * 0.25f
        val bx = rect.right + 10f * d + br
        if (fire) BasketArt.fireCrown(c, p, path, bx, cy + br * 0.15f, br * 0.6f, 0f)
        p.style = Paint.Style.FILL
        p.color = nameColor
        c.drawCircle(bx, cy + br * 0.15f, br * 1.12f, p)
        BasketArt.ball(c, p, bx, cy + br * 0.15f, br * 0.92f, 0.5f)
        // 오른쪽 배지들 (거리·불꽃)
        var right = w - 8f * d
        val bh = 13f * d
        if (fire) {
            val fw = badgeText.measureText(FIRE) + 18f * d
            p.color = BasketArt.FLAME_RED
            rect.set(right - fw, cy - bh, right, cy + bh)
            c.drawRoundRect(rect, bh, bh, p)
            badgeText.color = BasketArt.FLAME_YELLOW
            c.drawText(FIRE, rect.centerX(), cy + badgeText.textSize * 0.36f, badgeText)
            right = rect.left - 6f * d
        }
        if (spotText.isNotEmpty()) {
            val sw = badgeText.measureText(spotText) + 18f * d
            p.color = if (three) Theme.GOLD else 0xFF33478F.toInt()
            rect.set(right - sw, cy - bh, right, cy + bh)
            c.drawRoundRect(rect, bh, bh, p)
            badgeText.color = if (three) Theme.INK else Theme.TEXT
            c.drawText(spotText, rect.centerX(), cy + badgeText.textSize * 0.36f, badgeText)
            right = rect.left - 6f * d
        }
        // 이름 + 안내 (배지 앞에서 자름)
        val tx = bx + br * 1.2f + 8f * d
        c.save()
        c.clipRect(0f, 0f, right, h)
        c.drawText(nameText, tx, h * 0.47f, namePaint)
        c.drawText(hintText, tx, h * 0.47f + hintPaint.textSize * 1.25f, hintPaint)
        c.restore()
    }

    companion object {
        private const val FIRE = "불꽃 +1"
    }
}
