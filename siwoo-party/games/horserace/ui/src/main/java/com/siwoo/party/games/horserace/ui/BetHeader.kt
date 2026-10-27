package com.siwoo.party.games.horserace.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 베팅 화면 왼쪽 위 띠: 색 동그라미(첫 글자) + "가람 베팅" + 아래 "남은 칩 70" / 오른쪽 "레이스 2 / 5".
 * 차례가 바뀌면 톡 커졌다 돌아온다. 글자는 set 때만 만든다.
 */
class BetHeader(context: Context) : View(context) {

    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    private var chipsText = ""
    private var raceText = ""
    private var pop = 0f
    private var anim: ValueAnimator? = null
    private val d = Theme.dpf(context, 1f)

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val turnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB; textAlign = Paint.Align.RIGHT }
    private val racePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD; textAlign = Paint.Align.RIGHT }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private var nameWidth = 0f
    private var nameLeft = 0f

    fun set(playerName: String, playerColor: Int, chips: Int, race: Int, races: Int, animate: Boolean) {
        name = playerName
        initial = playerName.take(1)
        color = playerColor
        initPaint.color = Theme.textOn(playerColor)
        raceText = "$race / $races"
        contentDescription = playerName + TURN
        setChips(chips)
        fitName()
        if (animate) {
            anim?.cancel()
            anim = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 320
                addUpdateListener { pop = it.animatedValue as Float; invalidate() }
                start()
            }
        } else invalidate()
    }

    fun setChips(chips: Int) {
        chipsText = "남은 칩 " + chips
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stroke.strokeWidth = d * 2.5f
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        initPaint.textSize = h * 0.28f
        turnPaint.textSize = minOf(h * 0.3f, d * 14f)
        chipPaint.textSize = minOf(h * 0.26f, d * 12f)
        labelPaint.textSize = minOf(h * 0.22f, d * 10.5f)
        racePaint.textSize = minOf(h * 0.36f, d * 18f)
        nameLeft = rect.left + h * 0.8f
        fitName()
    }

    private fun fitName() {
        if (height <= 0) return
        var s = minOf(height * 0.36f, d * 18f)
        namePaint.textSize = s
        val maxW = rect.right - d * 10f - racePaint.measureText(SAMPLE) - nameLeft - turnPaint.measureText(TURN) - d * 6f
        while (namePaint.measureText(name) > maxW && s > d * 9f) {
            s *= 0.92f
            namePaint.textSize = s
        }
        nameWidth = namePaint.measureText(name)
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val r = d * 14f
        fill.color = Theme.CARD
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = color
        stroke.strokeWidth = d * 2.5f
        c.drawRoundRect(rect, r, r, stroke)
        val cy = rect.centerY()
        val cr = height * 0.28f * (1f + 0.25f * pop)
        val ccx = rect.left + height * 0.4f
        fill.color = color
        c.drawCircle(ccx, cy, cr, fill)
        stroke.color = Theme.TEXT
        stroke.strokeWidth = d * 1.5f
        c.drawCircle(ccx, cy, cr, stroke)
        c.drawText(initial, ccx, cy + initPaint.textSize * 0.36f, initPaint)
        val base = cy - d * 1f
        namePaint.color = color
        c.drawText(name, nameLeft, base, namePaint)
        c.drawText(TURN, nameLeft + nameWidth + d * 2f, base, turnPaint)
        c.drawText(chipsText, nameLeft, base + chipPaint.textSize * 1.25f, chipPaint)
        val rx = rect.right - d * 10f
        c.drawText(LABEL_RACE, rx, cy - d * 3f, labelPaint)
        c.drawText(raceText, rx, cy + racePaint.textSize * 0.95f - d * 3f, racePaint)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TURN = " 베팅"
        private const val LABEL_RACE = "레이스"
        private const val SAMPLE = "5 / 5"
    }
}
