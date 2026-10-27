package com.siwoo.party.games.liar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 가운데 차례 카드: 위 띠(단계·진행), 가운데 지금 사람 이름(크게, 플레이어 색), 아래 안내 두 줄.
 * 이름이 바뀌면 살짝 튀어 오른다. 글자는 set 할 때만 만든다 → onDraw 할당 없음.
 */
class TurnCardView(context: Context) : View(context) {

    private var head = ""
    var nameText = ""
        private set
    private var hint1 = ""
    private var hint2 = ""
    private var color = Theme.GOLD
    private val rect = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }

    init {
        namePaint.setShadowLayer(Theme.dpf(context, 3f), 0f, Theme.dpf(context, 2f), 0xAA000000.toInt())
    }

    /** hint 는 '\n' 으로 두 줄까지. bounce = 이름이 바뀔 때 통통 */
    fun set(head: String, name: String, color: Int, hint: String, bounce: Boolean = false) {
        this.head = head
        this.nameText = name
        this.color = color
        val k = hint.indexOf('\n')
        if (k >= 0) { hint1 = hint.substring(0, k); hint2 = hint.substring(k + 1) } else { hint1 = hint; hint2 = "" }
        contentDescription = "$head $name"
        fit()
        invalidate()
        if (bounce) {
            animate().cancel()
            scaleX = 0.9f; scaleY = 0.9f
            animate().scaleX(1f).scaleY(1f).setDuration(260).start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Theme.CARD_LIGHT, Theme.CARD, Shader.TileMode.CLAMP)
        fit()
    }

    private var bandH = 0f
    private var headY = 0f
    private var nameY = 0f
    private var hintY = 0f
    private var hintLines = 0

    private fun fit() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val maxW = w * 0.86f
        val pad = Theme.dpf(context, 8f)
        headPaint.textSize = minOf(h * 0.1f, Theme.dpf(context, 15f))
        shrink(headPaint, head, maxW)
        bandH = headPaint.textSize + pad * 2f
        headY = bandH / 2f + headPaint.textSize * 0.36f
        hintLines = if (hint2.isNotEmpty()) 2 else if (hint1.isNotEmpty()) 1 else 0
        hintPaint.textSize = minOf(h * 0.085f, Theme.dpf(context, 14f))
        shrink(hintPaint, if (hint1.length >= hint2.length) hint1 else hint2, maxW)
        val lh = hintPaint.textSize * 1.35f
        val hintBlock = if (hintLines == 0) 0f else lh * hintLines + pad
        hintY = h - pad * 1.5f - lh * (hintLines - 1) - hintPaint.textSize * 0.2f
        val top = bandH
        val bottom = h - hintBlock
        namePaint.textSize = minOf((bottom - top) * 0.6f, Theme.dpf(context, 44f))
        shrink(namePaint, nameText, maxW)
        nameY = (top + bottom) / 2f + namePaint.textSize * 0.36f
    }

    private fun shrink(p: Paint, s: String, maxW: Float) {
        while (s.isNotEmpty() && p.measureText(s) > maxW && p.textSize > Theme.dpf(context, 8f)) p.textSize *= 0.92f
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = Theme.dpf(context, 2f)
        rect.set(sw, sw, w - sw, h - sw)
        val r = Theme.dpf(context, 16f)
        c.drawRoundRect(rect, r, r, bg)
        band.color = color
        band.alpha = 90
        c.save()
        c.clipRect(rect.left, rect.top, rect.right, bandH)
        c.drawRoundRect(rect, r, r, band)
        c.restore()
        border.strokeWidth = sw
        border.color = color
        c.drawRoundRect(rect, r, r, border)
        c.drawText(head, w / 2f, headY, headPaint)
        namePaint.color = color
        c.drawText(nameText, w / 2f, nameY, namePaint)
        if (hintLines > 0) {
            c.drawText(hint1, w / 2f, hintY, hintPaint)
            if (hintLines == 2) c.drawText(hint2, w / 2f, hintY + hintPaint.textSize * 1.35f, hintPaint)
        }
    }
}
