package com.siwoo.party.games.flood.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.Theme

/**
 * 왼쪽 위 "지금 차례" 판: 윗줄 "바퀴" + "n / 20"(마지막 바퀴면 빨강), 바퀴 진행 막대,
 * 아래 줄 색 동그라미(첫 글자) + 차례인 사람 이름(그 사람 색, 크게).
 * 차례가 바뀌면 이름이 톡 커졌다 돌아온다. 글자는 바뀔 때(setter)만 만든다.
 */
class TurnHeaderView(context: Context) : View(context) {

    private var roundText = ""
    private var round = 1
    private var maxRounds = 1
    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    /** 이름 뒤 작은 글자 (" 차례" / 끝나면 " 승리!") */
    private var sub = SUB
    private var pop = 0f
    private var anim: ValueAnimator? = null

    private val rect = RectF()
    private val bar = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Theme.CARD_BORDER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    private var nameSize = 0f
    private var badgeR = 0f
    private var rowY = 0f
    private var labelY = 0f

    /** 보이는 바퀴 글자 (테스트용) */
    val roundShown: String get() = roundText
    val playerName: String get() = name

    fun setRound(r: Int, max: Int) {
        if (round == r && maxRounds == max && roundText.isNotEmpty()) return
        round = r
        maxRounds = max
        roundText = r.toString() + " / " + max
        invalidate()
    }

    /** 차례인 사람 (animate 면 톡). after = 이름 뒤 작은 글자 */
    fun setPlayer(playerName: String, playerColor: Int, animate: Boolean, after: String = SUB) {
        name = playerName
        sub = after
        initial = playerName.take(1)
        color = playerColor
        initPaint.color = Theme.textOn(playerColor)
        contentDescription = playerName + after
        fitName()
        if (animate) {
            anim?.cancel()
            anim = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = PartySettings(context).t(320)
                addUpdateListener { pop = it.animatedValue as Float; invalidate() }
                start()
            }
        } else invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = Theme.dpf(context, 1f)
        stroke.strokeWidth = d * 1.5f
        rect.set(d, d, w - d, h - d)
        val pad = d * 10f
        labelPaint.textSize = minOf(d * 12f, h * 0.17f)
        roundPaint.textSize = minOf(d * 15f, h * 0.2f)
        labelY = rect.top + pad + roundPaint.textSize * 0.8f
        val barTop = labelY + d * 6f
        bar.set(rect.left + pad, barTop, rect.right - pad, barTop + d * 5f)
        rowY = (bar.bottom + rect.bottom) / 2f
        badgeR = minOf(d * 12f, (rect.bottom - bar.bottom) * 0.3f)
        initPaint.textSize = badgeR * 1.1f
        nameSize = minOf(d * 19f, (rect.bottom - bar.bottom) * 0.42f)
        subPaint.textSize = nameSize * 0.62f
        fitName()
    }

    private fun fitName() {
        if (width <= 0) return
        namePaint.textSize = nameSize
        val left = rect.left + Theme.dpf(context, 10f) + badgeR * 2.6f
        val maxW = rect.right - Theme.dpf(context, 8f) - left - subPaint.measureText(sub)
        val tw = namePaint.measureText(name)
        if (tw > maxW && tw > 0f) namePaint.textSize = nameSize * maxW / tw
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val d = Theme.dpf(context, 1f)
        val rr = d * 14f
        fill.color = Theme.CARD
        c.drawRoundRect(rect, rr, rr, fill)
        c.drawRoundRect(rect, rr, rr, stroke)
        val pad = d * 10f
        c.drawText(LABEL, rect.left + pad, labelY, labelPaint)
        roundPaint.color = if (round >= maxRounds) Theme.RED else Theme.GOLD
        c.drawText(roundText, rect.right - pad, labelY, roundPaint)
        // 바퀴 진행 막대
        val br = bar.height() / 2f
        fill.color = 0x33FFFFFF
        c.drawRoundRect(bar, br, br, fill)
        val f = (round.toFloat() / maxRounds).coerceIn(0f, 1f)
        fill.color = if (round >= maxRounds) Theme.RED else Theme.GOLD
        c.drawRoundRect(bar.left, bar.top, bar.left + bar.width() * f, bar.bottom, br, br, fill)
        // 차례인 사람
        if (name.isEmpty()) return
        val s = 1f + 0.18f * pop
        val bx = rect.left + pad + badgeR
        c.save()
        c.scale(s, s, bx, rowY)
        FloodArt.badge(c, fill, bx, rowY, badgeR, color)
        c.drawText(initial, bx, rowY + initPaint.textSize * 0.36f, initPaint)
        namePaint.color = color
        val nx = bx + badgeR * 1.6f
        val ny = rowY + namePaint.textSize * 0.36f
        c.drawText(name, nx, ny, namePaint)
        c.drawText(sub, nx + namePaint.measureText(name) + d * 3f, ny, subPaint)
        c.restore()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val LABEL = "바퀴"
        const val SUB = " 차례"
        const val WIN = " 승리!"
    }
}
