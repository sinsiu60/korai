package com.siwoo.party.games.tuho.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.tuho.GameConfig

/**
 * 위쪽 차례 표시: [화살 3/10] (화살 그림) "가람 차례" + 안내 한 줄 | 지금 사람의 화살 10칸 (2·3·0 또는 빈 칸).
 * 글자는 setter 에서만 만든다 (onDraw 할당 없음).
 */
class TurnHeaderView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)

    private var roundText = ""
    private var nameText = ""
    private var hintText = ""
    private var color = Theme.GOLD
    /** 지금 사람의 화살마다 점수 (−1 = 아직) */
    private val slots = IntArray(GameConfig.ARROWS) { -1 }
    /** 지금 던질 칸 (−1 = 없음) */
    private var currentSlot = -1

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT_SUB }
    private val slotText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    /** 지금 보이는 글자 (테스트용) */
    val shownName: String get() = nameText
    val shownHint: String get() = hintText

    fun setRound(arrow: Int) {
        roundText = ROUND_TEXT[arrow.coerceIn(0, GameConfig.ARROWS - 1)]
        invalidate()
    }

    fun setTurn(name: String, playerColor: Int) {
        nameText = name
        color = playerColor
        invalidate()
    }

    fun setHint(text: String) {
        if (hintText == text) return
        hintText = text
        invalidate()
    }

    /** 화살 칸 (scores: 그 사람의 화살별 점수, −1 = 아직), 지금 칸 */
    fun setSlots(scores: IntArray?, current: Int) {
        for (i in slots.indices) slots[i] = scores?.getOrNull(i) ?: -1
        currentSlot = current
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        roundPaint.textSize = 13f * d
        namePaint.textSize = minOf(19f * d, h * 0.4f)
        hintPaint.textSize = minOf(12.5f * d, h * 0.27f)
        slotText.textSize = 11f * d
        ring.strokeWidth = 1.4f * d
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        p.style = Paint.Style.FILL
        p.color = 0x55101840
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, 14f * d, 14f * d, p)
        // 순번 알약
        val pw = roundPaint.measureText(roundText) + 18f * d
        pill.color = Theme.GOLD
        rect.set(8f * d, h / 2f - 12f * d, 8f * d + pw, h / 2f + 12f * d)
        c.drawRoundRect(rect, 12f * d, 12f * d, pill)
        c.drawText(roundText, rect.centerX(), h / 2f + roundPaint.textSize * 0.36f, roundPaint)
        // 화살 그림 (플레이어 색 깃)
        val ax = rect.right + 10f * d
        val alen = 30f * d
        TuhoArt.arrow(c, p, ax + alen * 0.8f, h / 2f - alen * 0.4f, 0.8f, -0.6f, alen, color)
        val tx = ax + alen + 8f * d
        // 화살 칸 영역 (오른쪽)
        val sr = minOf(9f * d, h * 0.22f)
        val gap = 3.5f * d
        val slotsW = GameConfig.ARROWS * (sr * 2f + gap) + 10f * d
        c.save()
        c.clipRect(0f, 0f, w - slotsW - 6f * d, h)
        c.drawText(nameText, tx, h * 0.47f, namePaint)
        c.drawText(hintText, tx, h * 0.47f + hintPaint.textSize * 1.25f, hintPaint)
        c.restore()
        // 칸 (왼쪽부터 1번 화살)
        val cy = h / 2f
        var cx = w - slotsW + sr
        for (i in 0 until GameConfig.ARROWS) {
            val v = slots[i]
            p.style = Paint.Style.FILL
            when {
                v < 0 -> {
                    ring.color = if (i == currentSlot) Theme.GOLD else 0x66FFFFFF
                    ring.strokeWidth = (if (i == currentSlot) 2.4f else 1.4f) * d
                    c.drawCircle(cx, cy, sr * 0.85f, ring)
                }
                v == 0 -> {
                    p.color = 0x55FFFFFF
                    c.drawCircle(cx, cy, sr * 0.5f, p)
                }
                else -> {
                    p.color = if (v >= GameConfig.EAR_POINTS) Theme.GOLD else color
                    c.drawCircle(cx, cy, sr, p)
                    slotText.color = if (v >= GameConfig.EAR_POINTS) Theme.INK else Theme.textOn(color)
                    c.drawText(if (v >= GameConfig.EAR_POINTS) EAR_TEXT else MOUTH_TEXT, cx, cy + slotText.textSize * 0.36f, slotText)
                }
            }
            cx += sr * 2f + gap
        }
    }

    companion object {
        private val ROUND_TEXT = Array(GameConfig.ARROWS) { "화살 " + (it + 1) + "/" + GameConfig.ARROWS }
        private val MOUTH_TEXT = GameConfig.MOUTH_POINTS.toString()
        private val EAR_TEXT = GameConfig.EAR_POINTS.toString()
    }
}
