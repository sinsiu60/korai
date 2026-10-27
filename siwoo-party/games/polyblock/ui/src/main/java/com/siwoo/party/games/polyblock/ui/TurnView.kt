package com.siwoo.party.games.polyblock.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * "지금 차례" 판: 색 동그라미 + 이름 + 두 줄 안내(초록=놓을 수 있음, 빨강=안 되는 이유) + 남은 조각.
 * 글자는 setter 에서만 만들고, 너무 길면 setter 에서 글자 크기를 줄여 칸에 맞춘다.
 */
class TurnView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var name = ""
    private var initial = ""
    private var color = Theme.GRAY
    private var line1 = ""
    private var line2 = ""
    private var hintColor = Theme.TEXT_SUB
    private var foot = ""
    private var label = LABEL_TURN

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT_SUB }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GOLD }

    /** 테스트·표시용 지금 안내 */
    val hint: String get() = if (line2.isEmpty()) line1 else "$line1 $line2"

    fun setPlayer(name: String, color: Int) {
        this.name = name
        this.initial = name.take(1)
        this.color = color
        label = LABEL_TURN
        initPaint.color = Theme.textOn(color)
        fitText()
        invalidate()
    }

    /** 끝났을 때 (이긴 사람 표시) */
    fun setLabel(text: String) {
        label = text
        invalidate()
    }

    fun setHint(l1: String, l2: String, color: Int) {
        if (line1 == l1 && line2 == l2 && hintColor == color) return
        line1 = l1
        line2 = l2
        hintColor = color
        fitText()
        invalidate()
    }

    fun setFoot(text: String) {
        if (foot == text) return
        foot = text
        fitText()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stroke.strokeWidth = 2f * d
        fitText()
    }

    private fun fitText() {
        val w = width.toFloat()
        if (w <= 0f) return
        val avail = w - PAD * 2f * d
        labelPaint.textSize = 11f * d
        footPaint.textSize = 12f * d
        // 이름: 동그라미 오른쪽 자리에 맞춤
        val circle = 26f * d
        namePaint.textSize = 19f * d
        val nameAvail = avail - circle - 6f * d
        val nw = namePaint.measureText(name)
        if (nw > nameAvail && nw > 0f) namePaint.textSize = maxOf(10f * d, namePaint.textSize * nameAvail / nw)
        initPaint.textSize = 14f * d
        hintPaint.textSize = 13f * d
        val hw = maxOf(hintPaint.measureText(line1), hintPaint.measureText(line2))
        if (hw > avail && hw > 0f) hintPaint.textSize = hintPaint.textSize * avail / hw
        val fw = footPaint.measureText(foot)
        if (fw > avail && fw > 0f) footPaint.textSize = footPaint.textSize * avail / fw
    }

    override fun onDraw(c: Canvas) {
        val sw = stroke.strokeWidth
        rect.set(sw, sw, width - sw, height - sw)
        val r = 14f * d
        fill.color = Theme.CARD
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = color
        c.drawRoundRect(rect, r, r, stroke)
        val left = PAD * d
        var y = left + labelPaint.textSize
        c.drawText(label, left, y, labelPaint)
        // 이름 줄
        val cr = 13f * d
        val cy = y + 8f * d + cr
        fill.color = color
        c.drawCircle(left + cr, cy, cr, fill)
        c.drawText(initial, left + cr, cy + initPaint.textSize * 0.36f, initPaint)
        namePaint.color = color
        c.drawText(name, left + cr * 2f + 6f * d, cy + namePaint.textSize * 0.36f, namePaint)
        // 안내 두 줄 (남는 높이 가운데쯤)
        y = cy + cr + 10f * d + hintPaint.textSize
        hintPaint.color = hintColor
        c.drawText(line1, left, y, hintPaint)
        if (line2.isNotEmpty()) c.drawText(line2, left, y + hintPaint.textSize * 1.3f, hintPaint)
        // 아래: 남은 조각
        if (foot.isNotEmpty()) c.drawText(foot, left, height - left, footPaint)
    }

    companion object {
        private const val PAD = 10f
        private const val LABEL_TURN = "지금 차례"
    }
}
