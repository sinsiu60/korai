package com.siwoo.party.games.liardice.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.dice.DiceSet
import com.siwoo.party.common.secret.SecretContent
import com.siwoo.party.common.ui.Theme

/**
 * 내 주사위 비밀 카드의 "보이는 면" (공통 SecretHoldView 안에서 꾹 누르는 동안만).
 * 밝은 종이 카드 + 위쪽 플레이어 색 띠("○○님의 주사위") + 가운데 공통 DiceSet 으로 그린 주사위 줄 + 아래 와일드 안내.
 * DiceSet·글자 크기는 layout 때만 만든다 → draw 할당 없음.
 * clear() 하면 눈·글자를 모두 지운다 (다음 사람 전환 화면 뒤에 남지 않음).
 */
class DiceSecret(values: IntArray, count: Int, ownerName: String, private val color: Int, wild: Boolean) : SecretContent {

    /** 보여 줄 눈 (clear 뒤엔 모두 0) */
    val values = IntArray(DiceSet.MAX).also { for (k in 0 until minOf(count, DiceSet.MAX)) it[k] = values[k] }
    var count = count.coerceIn(0, DiceSet.MAX)
        private set
    private var title = "${ownerName}님의 주사위"
    private var sub = if (wild) "1은 모든 눈으로 세어요 (와일드)" else "1 와일드 없음 · 1도 보통 눈"
    private var shown = (0 until this.count).joinToString(" ") { this.values[it].toString() }

    /** 눈을 글자로 ("3 5 1 6 2"), 지운 뒤엔 "" */
    override val text: String get() = shown

    /** 비밀이 남아 있는지 */
    val hasDice: Boolean get() = count > 0

    private var dice: DiceSet? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xFF5A6080.toInt() }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_DARK }
    private var countText = "주사위 ${this.count}개"

    override fun layout(host: View, card: RectF) {
        val h = card.height()
        val w = card.width()
        if (h <= 0f) return
        val d = dice ?: DiceSet(host).also { dice = it }
        titlePaint.color = Theme.textOn(color)
        titlePaint.textSize = h * 0.095f
        fit(titlePaint, title, w * 0.8f, host)
        subPaint.textSize = h * 0.06f
        fit(subPaint, sub, w * 0.9f, host)
        countPaint.textSize = h * 0.075f
        if (count > 0) {
            d.count = count
            d.setValues(values)
            val s = minOf(h * 0.3f, w * 0.9f / (count * 1.3f - 0.3f))
            d.layoutRow(card.centerX(), card.top + h * 0.5f, s, s * 0.3f)
        }
    }

    private fun fit(p: Paint, s: String, maxW: Float, host: View) {
        val min = Theme.dpf(host.context, 8f)
        while (s.isNotEmpty() && p.measureText(s) > maxW && p.textSize > min) p.textSize *= 0.92f
    }

    override fun draw(c: Canvas, card: RectF) {
        val h = card.height()
        val r = h * 0.1f
        fill.color = Theme.PAPER
        c.drawRoundRect(card, r, r, fill)
        if (count <= 0) return // 지워진 카드: 빈 종이
        // 위쪽 플레이어 색 띠
        val bandB = card.top + h * 0.19f
        fill.color = color
        c.drawRoundRect(card.left, card.top, card.right, bandB, r, r, fill)
        c.drawRect(card.left, bandB - r, card.right, bandB, fill)
        c.drawText(title, card.centerX(), card.top + h * 0.095f + titlePaint.textSize * 0.36f, titlePaint)
        // 주사위 깔개 (녹색 펠트)
        fill.color = 0xFF1E6B4A.toInt()
        c.drawRoundRect(card.left + h * 0.06f, card.top + h * 0.27f, card.right - h * 0.06f, card.top + h * 0.73f, r * 0.8f, r * 0.8f, fill)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = h * 0.012f
        c.drawRoundRect(card.left + h * 0.08f, card.top + h * 0.29f, card.right - h * 0.08f, card.top + h * 0.71f, r * 0.6f, r * 0.6f, stroke)
        dice?.draw(c)
        c.drawText(countText, card.centerX(), card.top + h * 0.81f, countPaint)
        c.drawText(sub, card.centerX(), card.bottom - h * 0.07f, subPaint)
        stroke.color = Theme.GOLD_DARK
        stroke.strokeWidth = h * 0.02f
        c.drawRoundRect(card, r, r, stroke)
    }

    override fun clear() {
        values.fill(0)
        count = 0
        shown = ""
        title = ""
        sub = ""
        countText = ""
        dice?.let { d -> for (k in 0 until DiceSet.MAX) d.values[k] = 1 }
    }
}
