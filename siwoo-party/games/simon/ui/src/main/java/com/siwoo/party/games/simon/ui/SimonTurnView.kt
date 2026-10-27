package com.siwoo.party.games.simon.ui

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
 * 왼쪽 위 차례 카드: 위 띠(차례 정보), 가운데 지금 차례 이름(플레이어 색, 크게), 아래 안내(두 줄까지).
 * 안내를 강조하면 금색 굵은 글씨 ("+1 새 색을 누르세요!").
 * 글자는 바뀔 때(set)만 만들고 크기를 맞춘다 → onDraw 할당 없음.
 */
class SimonTurnView(context: Context) : View(context) {

    private var head = ""
    private var name = ""
    private var hint1 = ""
    private var hint2 = ""
    private var color = Theme.GOLD
    private var strong = false
    private val rect = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init {
        namePaint.setShadowLayer(Theme.dpf(context, 3f), 0f, Theme.dpf(context, 2f), 0xAA000000.toInt())
    }

    /** hint 는 '\n' 으로 두 줄까지. strong 이면 금색 굵게 */
    fun set(head: String, name: String, color: Int, hint: String, strong: Boolean = false) {
        this.head = head
        this.name = name
        this.color = color
        this.strong = strong
        val k = hint.indexOf('\n')
        if (k >= 0) { hint1 = hint.substring(0, k); hint2 = hint.substring(k + 1) } else { hint1 = hint; hint2 = "" }
        contentDescription = "$head $name $hint"
        fit()
        invalidate()
    }

    /** 안내 글만 바꾼다 */
    fun setHint(hint: String, strong: Boolean = false) = set(head, name, color, hint, strong)

    val nameText: String get() = name
    val hintText: String get() = if (hint2.isEmpty()) hint1 else hint1 + "\n" + hint2
    val hintStrong: Boolean get() = strong

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

    /** 위 띠 / 가운데 이름 / 아래 안내 줄이 겹치지 않게 크기·위치 계산 */
    private fun fit() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val maxW = w * 0.86f
        val pad = Theme.dpf(context, 6f)
        headPaint.textSize = minOf(h * 0.13f, Theme.dpf(context, 13f))
        shrink(headPaint, head, maxW)
        bandH = headPaint.textSize + pad * 2f
        headY = bandH / 2f + headPaint.textSize * 0.36f
        hintLines = if (hint2.isNotEmpty()) 2 else if (hint1.isNotEmpty()) 1 else 0
        hintPaint.typeface = if (strong) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        hintPaint.color = if (strong) Theme.GOLD else Theme.TEXT_SUB
        hintPaint.textSize = minOf(h * (if (strong) 0.15f else 0.12f), Theme.dpf(context, if (strong) 16f else 13f))
        shrink(hintPaint, hint1, maxW)
        shrink(hintPaint, hint2, maxW)
        val lh = hintPaint.textSize * 1.25f
        val hintBlock = if (hintLines == 0) 0f else lh * hintLines + pad * 0.5f
        hintY = h - pad - lh * (hintLines - 1) - hintPaint.textSize * 0.25f
        val top = bandH
        val bottom = h - hintBlock
        namePaint.textSize = minOf((bottom - top) * 0.7f, Theme.dpf(context, 32f))
        shrink(namePaint, name, maxW)
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
        val r = Theme.dpf(context, 14f)
        c.drawRoundRect(rect, r, r, bg)
        accent.color = color
        accent.alpha = 80
        c.save()
        c.clipRect(rect.left, rect.top, rect.right, bandH)
        c.drawRoundRect(rect, r, r, accent)
        c.restore()
        border.strokeWidth = sw
        border.color = color
        c.drawRoundRect(rect, r, r, border)
        c.drawText(head, w / 2f, headY, headPaint)
        namePaint.color = color
        c.drawText(name, w / 2f, nameY, namePaint)
        if (hintLines > 0) {
            c.drawText(hint1, w / 2f, hintY, hintPaint)
            if (hintLines == 2) c.drawText(hint2, w / 2f, hintY + hintPaint.textSize * 1.25f, hintPaint)
        }
    }
}
