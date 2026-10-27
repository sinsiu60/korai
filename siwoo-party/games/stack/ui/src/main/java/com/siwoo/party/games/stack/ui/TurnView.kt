package com.siwoo.party.games.stack.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 왼쪽 위 차례 카드: 라운드 알약 · "지금 차례" · 큰 색 블록(첫 글자) · 큰 이름 · 탑 높이.
 * 글자는 setter 에서만 만든다 (onDraw 할당 없음).
 */
class TurnView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    private var roundText = ""
    private var heightText = ""
    private var label = NOW_LABEL

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val heightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }

    val shownName: String get() = name

    fun setTurn(playerName: String, playerColor: Int) {
        name = playerName
        initial = playerName.take(1)
        color = playerColor
        initPaint.color = Theme.textOn(playerColor)
        fitName()
        invalidate()
    }

    /** 위 작은 글자 ("지금 차례" / "라운드 끝" 등) */
    fun setLabel(text: String) {
        if (label == text) return
        label = text
        invalidate()
    }

    fun setRound(round: Int, rounds: Int) {
        roundText = "라운드 $round / $rounds"
        invalidate()
    }

    fun setHeight(floors: Int) {
        heightText = "탑 ${floors}층"
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stroke.strokeWidth = 2.5f * d
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        pill.textSize = 12.5f * d
        sub.textSize = 12f * d
        heightPaint.textSize = 15f * d
        fitName()
    }

    private fun fitName() {
        var s = 26f * d
        namePaint.textSize = s
        val maxW = rect.width() - 14f * d
        while (maxW > 0f && namePaint.measureText(name) > maxW && s > 11f * d) {
            s *= 0.92f
            namePaint.textSize = s
        }
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val cx = rect.centerX()
        val r = 14f * d
        fill.color = Theme.CARD_LIGHT
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = Theme.GOLD
        c.drawRoundRect(rect, r, r, stroke)
        val h = rect.height()
        // 라운드 알약
        val py = rect.top + 17f * d
        val pw = pill.measureText(roundText) / 2f + 10f * d
        fill.color = Theme.GOLD
        c.drawRoundRect(cx - pw, py - 10f * d, cx + pw, py + 10f * d, 10f * d, 10f * d, fill)
        c.drawText(roundText, cx, py + pill.textSize * 0.36f, pill)
        // 남은 높이 배분: 라벨 · 블록 · 이름 · 높이
        val top = py + 14f * d
        val bottom = rect.bottom - 8f * d
        val space = bottom - top
        val blockH = (space * 0.3f).coerceIn(18f * d, 40f * d)
        val labelY = top + 14f * d
        c.drawText(label, cx, labelY, sub)
        // 큰 색 블록 (탑 블록과 같은 모양)
        val bw = blockH * 2.1f
        val bt = labelY + 7f * d
        val bb = bt + blockH
        val rr = 5f * d
        fill.color = Theme.shade(color, 0.62f)
        c.drawRoundRect(cx - bw / 2f, bt, cx + bw / 2f, bb, rr, rr, fill)
        fill.color = color
        c.drawRoundRect(cx - bw / 2f, bt, cx + bw / 2f, bb - blockH * 0.16f, rr, rr, fill)
        fill.color = Theme.shade(color, 1.35f)
        c.drawRoundRect(cx - bw / 2f, bt, cx + bw / 2f, bt + blockH * 0.24f, rr, rr, fill)
        initPaint.textSize = blockH * 0.55f
        c.drawText(initial, cx, bt + blockH * 0.45f + initPaint.textSize * 0.36f, initPaint)
        // 이름
        namePaint.color = Theme.TEXT
        val ny = bb + (bottom - bb) * 0.5f + namePaint.textSize * 0.1f
        c.drawText(name, cx, ny, namePaint)
        if (h > 0f) c.drawText(heightText, cx, bottom - 2f * d, heightPaint)
    }

    companion object {
        const val NOW_LABEL = "지금 차례"
    }
}
