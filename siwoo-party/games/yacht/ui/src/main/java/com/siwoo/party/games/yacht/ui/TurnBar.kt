package com.siwoo.party.games.yacht.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 왼쪽 위 "지금 차례" 띠: 색 동그라미(첫 글자) + 이름(그 사람 색, 크게) + 오른쪽 "라운드 n / 12".
 * 차례가 바뀌면 톡 커졌다 돌아온다. 글자는 바뀔 때(setter)만 만든다.
 */
class TurnBar(context: Context) : View(context) {

    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    private var roundText = ""
    private var pop = 0f
    private var anim: ValueAnimator? = null

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB; textAlign = Paint.Align.RIGHT }
    private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD; textAlign = Paint.Align.RIGHT }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val turnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    private var baseName = 0f
    private var nameLeft = 0f
    private var nameMax = 0f
    private var nameWidth = 0f

    /** 차례인 사람 + 라운드 (바뀌면 톡) */
    fun set(playerName: String, playerColor: Int, round: Int, rounds: Int, animate: Boolean) {
        name = playerName
        initial = playerName.take(1)
        color = playerColor
        initPaint.color = Theme.textOn(playerColor)
        roundText = "$round / $rounds"
        contentDescription = playerName + LABEL_TURN
        fitName()
        if (animate) popNow() else invalidate()
    }

    private fun popNow() {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 320
            addUpdateListener { pop = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = Theme.dpf(context, 1f)
        stroke.strokeWidth = d * 2.5f
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        baseName = minOf(h * 0.42f, d * 26f)
        initPaint.textSize = h * 0.3f
        labelPaint.textSize = minOf(h * 0.22f, d * 11.5f)
        roundPaint.textSize = minOf(h * 0.36f, d * 20f)
        turnPaint.textSize = labelPaint.textSize
        nameLeft = rect.left + h * 0.82f + d * 4f
        fitName()
    }

    /** 이름이 폭에 맞게 (setter·크기 바뀔 때만) */
    private fun fitName() {
        if (baseName <= 0f) return
        var s = baseName
        namePaint.textSize = s
        nameMax = rect.right - Theme.dpf(context, 12f) - roundPaint.measureText(ROUND_SAMPLE) - nameLeft - turnPaint.measureText(LABEL_TURN)
        while (namePaint.measureText(name) > nameMax && s > baseName * 0.5f) {
            s *= 0.92f
            namePaint.textSize = s
        }
        nameWidth = namePaint.measureText(name)
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val d = Theme.dpf(context, 1f)
        val r = d * 14f
        fill.color = Theme.CARD
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = color
        stroke.strokeWidth = d * 2.5f
        c.drawRoundRect(rect, r, r, stroke)
        val cy = rect.centerY()
        // 색 동그라미 + 첫 글자 (톡 커짐)
        val cr = height * 0.3f * (1f + 0.25f * pop)
        val ccx = rect.left + height * 0.42f
        fill.color = color
        c.drawCircle(ccx, cy, cr, fill)
        stroke.color = Theme.TEXT
        stroke.strokeWidth = d * 1.5f
        c.drawCircle(ccx, cy, cr, stroke)
        c.drawText(initial, ccx, cy + initPaint.textSize * 0.36f, initPaint)
        // 이름 + " 차례"
        namePaint.color = color
        val base = cy + namePaint.textSize * 0.36f
        c.save()
        val sc = 1f + 0.12f * pop
        c.scale(sc, sc, nameLeft, base)
        c.drawText(name, nameLeft, base, namePaint)
        c.restore()
        c.drawText(LABEL_TURN, nameLeft + nameWidth + d * 2f, base, turnPaint)
        // 라운드
        val rx = rect.right - d * 12f
        c.drawText(LABEL_ROUND, rx, cy - d * 2f, labelPaint)
        c.drawText(roundText, rx, cy + roundPaint.textSize * 0.95f - d * 2f, roundPaint)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val LABEL_TURN = " 차례"
        private const val LABEL_ROUND = "라운드"
        private const val ROUND_SAMPLE = "12 / 12"
    }
}
