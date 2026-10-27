package com.siwoo.party.games.curling.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.curling.GameConfig

/**
 * 위쪽 차례 표시: [n/3 엔드] (편 색 스톤) "가람 차례" + 안내 한 줄 | 편마다 남은 스톤 (채운 점 = 남음, 빈 점 = 던짐).
 * 글자는 setter 에서만 만든다 (onDraw 할당 없음).
 */
class TurnHeaderView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)

    private var endText = ""
    private var nameText = ""
    private var hintText = ""
    private var color = Theme.GOLD
    private var sides = 0
    private val sideColors = IntArray(GameConfig.MAX_PLAYERS)
    private val left = IntArray(GameConfig.MAX_PLAYERS)
    private var perSide = 0

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT_SUB }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    /** 지금 보이는 글자 (테스트용) */
    val shownName: String get() = nameText
    val shownHint: String get() = hintText

    /** 편 수·색·편당 스톤 수 */
    fun setup(sideCount: Int, colors: IntArray, stonesPerSide: Int) {
        sides = sideCount
        for (i in 0 until sideCount) sideColors[i] = colors[i]
        perSide = stonesPerSide
        invalidate()
    }

    fun setEnd(end: Int) {
        endText = (end + 1).toString() + " / " + GameConfig.ENDS + " 엔드"
        invalidate()
    }

    fun setTurn(name: String, sideColor: Int) {
        nameText = name
        color = sideColor
        invalidate()
    }

    fun setHint(text: String) {
        if (hintText == text) return
        hintText = text
        invalidate()
    }

    fun setRemaining(side: Int, n: Int) {
        if (left[side] == n) return
        left[side] = n
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        endPaint.textSize = 13f * d
        namePaint.textSize = minOf(19f * d, h * 0.4f)
        hintPaint.textSize = minOf(12.5f * d, h * 0.27f)
        ring.strokeWidth = 1.4f * d
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        // 바탕 카드
        p.color = 0x55101840
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, 14f * d, 14f * d, p)
        // 엔드 알약
        val pw = endPaint.measureText(endText) + 18f * d
        pill.color = Theme.GOLD
        rect.set(8f * d, h / 2f - 12f * d, 8f * d + pw, h / 2f + 12f * d)
        c.drawRoundRect(rect, 12f * d, 12f * d, pill)
        c.drawText(endText, rect.centerX(), h / 2f + endPaint.textSize * 0.36f, endPaint)
        // 스톤 + 이름 + 안내
        val sr = h * 0.27f
        val sx = rect.right + 10f * d + sr
        CurlArt.stone(c, p, sx, h / 2f, sr, color, 0.6f)
        val tx = sx + sr + 10f * d
        // 남은 스톤 점 영역 (오른쪽) 앞에서 글자를 자른다
        val dr = if (sides > 0) minOf(5f * d, (h - 8f * d) / sides / 2f - 1.5f * d) else 0f
        val dotsW = if (sides > 0 && perSide > 0) perSide * (dr * 2f + 4f * d) + 14f * d else 0f
        c.save()
        c.clipRect(0f, 0f, w - dotsW, h)
        c.drawText(nameText, tx, h * 0.47f, namePaint)
        c.drawText(hintText, tx, h * 0.47f + hintPaint.textSize * 1.25f, hintPaint)
        c.restore()
        // 남은 스톤 (오른쪽, 편마다 한 줄)
        if (sides <= 0 || perSide <= 0) return
        val rowH = (h - 8f * d) / sides
        val right = w - 10f * d
        for (s in 0 until sides) {
            val cy = 4f * d + rowH * (s + 0.5f)
            for (k in 0 until perSide) {
                val cx = right - dr - k * (dr * 2f + 4f * d)
                // 오른쪽부터 던진 것(빈 점), 왼쪽이 남은 것 → 남은 수만큼 왼쪽부터 채움
                val idx = perSide - 1 - k
                if (idx < left[s]) {
                    dot.color = sideColors[s]
                    c.drawCircle(cx, cy, dr, dot)
                    ring.color = 0xAAFFFFFF.toInt()
                    c.drawCircle(cx, cy, dr, ring)
                } else {
                    ring.color = 0x66FFFFFF
                    c.drawCircle(cx, cy, dr * 0.8f, ring)
                }
            }
        }
    }
}
