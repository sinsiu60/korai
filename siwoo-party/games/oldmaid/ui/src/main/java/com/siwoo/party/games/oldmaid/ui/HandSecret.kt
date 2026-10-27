package com.siwoo.party.games.oldmaid.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.CardBitmaps
import com.siwoo.party.common.secret.SecretContent
import com.siwoo.party.common.ui.Theme

/**
 * 내 손패 확인 (공통 SecretHoldView 안에서 꾹 누르는 동안만 보임): 초록 펠트 위에 앞면 카드를 1~2줄로 펼친다.
 * 조커는 빨간 빛 테두리 + "조커!" 표시. 카드 그림은 게임의 CardBitmaps (미리 그려 둠), 자리 계산은 layout 때만 → draw 할당 없음.
 */
class HandSecret(private val bitmaps: CardBitmaps, cards: List<Card>, title: String) : SecretContent {

    private var cards: List<Card> = cards
    private var title = title
    private var note = if (cards.any { it.isJoker }) NOTE_JOKER else NOTE_PLAIN
    private var shown: String = cards.joinToString(" ") { it.shortName }

    override val text: String get() = shown

    private val dst = Array(MAX) { RectF() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xE6FFFFFF.toInt() }
    private val jokerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
    private var shader: Shader? = null
    private var r = 0f
    private var jokerIndex = -1

    init {
        bitmaps.prepare(cards)
    }

    override fun layout(host: View, card: RectF) {
        val w = card.width()
        val h = card.height()
        if (h <= 0f) return
        r = h * 0.08f
        shader = LinearGradient(0f, card.top, 0f, card.bottom, 0xFF237A62.toInt(), 0xFF0E4236.toInt(), Shader.TileMode.CLAMP)
        val minText = Theme.dpf(host.context, 8f)
        titlePaint.textSize = h * 0.075f
        while (titlePaint.measureText(title) > w * 0.86f && titlePaint.textSize > minText) titlePaint.textSize *= 0.92f
        notePaint.textSize = h * 0.052f
        while (notePaint.measureText(note) > w * 0.9f && notePaint.textSize > minText) notePaint.textSize *= 0.92f
        jokerPaint.textSize = h * 0.045f
        // 카드 자리: 7장 이하 한 줄, 그 이상 두 줄
        val n = minOf(cards.size, MAX)
        jokerIndex = -1
        if (n == 0) return
        val rows = if (n <= 7) 1 else 2
        val perRow = (n + rows - 1) / rows
        val areaTop = card.top + h * 0.15f
        val areaH = h * 0.7f
        val availW = w * 0.9f
        var ch = areaH / rows * 0.9f
        var cw = ch / CardBitmaps.RATIO
        // 겹치지 않고 들어가면 그대로, 아니면 겹치게 (최소 40% 보이게)
        var step = cw * 1.06f
        if (cw + step * (perRow - 1) > availW) {
            step = (availW - cw) / maxOf(1, perRow - 1)
            if (step < cw * 0.4f) {
                cw = availW / (1f + 0.4f * (perRow - 1))
                ch = cw * CardBitmaps.RATIO
                step = cw * 0.4f
            }
        }
        val rowGap = areaH / rows
        for (i in 0 until n) {
            val row = i / perRow
            val col = i % perRow
            val inRow = if (row == rows - 1) n - perRow * (rows - 1) else perRow
            val span = cw + step * (inRow - 1)
            val left = card.centerX() - span / 2f + col * step
            val cy = areaTop + rowGap * (row + 0.5f)
            dst[i].set(left, cy - ch / 2f, left + cw, cy + ch / 2f)
            if (cards[i].isJoker) jokerIndex = i
        }
    }

    override fun draw(c: Canvas, card: RectF) {
        val h = card.height()
        fill.shader = shader
        c.drawRoundRect(card, r, r, fill)
        fill.shader = null
        stroke.color = Theme.GOLD
        stroke.strokeWidth = h * 0.012f
        c.drawRoundRect(card, r, r, stroke)
        c.drawText(title, card.centerX(), card.top + h * 0.1f, titlePaint)
        val n = minOf(cards.size, MAX)
        for (i in 0 until n) {
            val d = dst[i]
            if (i == jokerIndex) {
                stroke.color = Theme.RED
                stroke.strokeWidth = h * 0.018f
                c.drawRoundRect(d.left - h * 0.008f, d.top - h * 0.008f, d.right + h * 0.008f, d.bottom + h * 0.008f, r * 0.5f, r * 0.5f, stroke)
            }
            c.drawBitmap(bitmaps.face(cards[i]), null, d, bmpPaint)
        }
        if (jokerIndex >= 0) {
            val d = dst[jokerIndex]
            fill.color = Theme.RED_DARK
            val tw = jokerPaint.textSize * 1.9f
            c.drawRoundRect(d.centerX() - tw, d.top - jokerPaint.textSize * 1.25f, d.centerX() + tw, d.top - jokerPaint.textSize * 0.05f, r * 0.4f, r * 0.4f, fill)
            c.drawText(JOKER, d.centerX(), d.top - jokerPaint.textSize * 0.3f, jokerPaint)
        }
        c.drawText(note, card.centerX(), card.bottom - h * 0.045f, notePaint)
    }

    override fun clear() {
        cards = emptyList()
        title = ""
        note = ""
        shown = ""
        jokerIndex = -1
    }

    companion object {
        private const val MAX = 27
        private const val JOKER = "조커!"
        private const val NOTE_JOKER = "쉿! 조커는 들키지 않게 넘기세요"
        private const val NOTE_PLAIN = "조커가 없어요 · 옆 사람 카드를 잘 골라 보세요"
    }
}
