package com.siwoo.party.games.matches.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.Theme

/**
 * 오른쪽 위 "지금 차례" 판: 윗줄 라벨 + 색 동그라미(첫 글자) + 이름(그 사람 색, 크게) + 안내 한 줄.
 * 높이가 넉넉하면(2~3인) 아래에 "마지막 성냥 = 패배" 알림(불붙은 성냥 그림)을 보여 준다.
 * 차례가 바뀌면 톡 커졌다 돌아온다. 글자는 바뀔 때(setter)만 만든다.
 */
class TurnView(context: Context) : View(context) {

    private var label = LABEL_TURN
    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    private var sub = ""
    private var pop = 0f
    private var anim: ValueAnimator? = null

    private val rect = RectF()
    private val noteRect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    /** 지금 보이는 안내·이름 (테스트용) */
    val subText: String get() = sub
    val playerName: String get() = name

    fun setPlayer(playerName: String, playerColor: Int, animate: Boolean) {
        name = playerName
        initial = playerName.take(1)
        color = playerColor
        initPaint.color = Theme.textOn(playerColor)
        contentDescription = playerName + LABEL_SUFFIX
        fitName()
        if (animate) popNow() else invalidate()
    }

    fun setLabel(text: String) {
        if (label == text) return
        label = text
        invalidate()
    }

    fun setSub(text: String) {
        if (sub == text) return
        sub = text
        fitSub()
        invalidate()
    }

    private fun popNow() {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = PartySettings(context).t(320)
            addUpdateListener { pop = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private var baseName = 0f
    private var baseSub = 0f
    private var textLeft = 0f
    private var textRight = 0f
    private var headTop = 0f
    private var headH = 0f
    private var showNote = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = Theme.dpf(context, 1f)
        stroke.strokeWidth = d * 2.5f
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        val head = minOf(h.toFloat(), d * 84f)
        labelPaint.textSize = minOf(head * 0.15f, d * 12f)
        baseName = minOf(head * 0.3f, d * 24f)
        baseSub = minOf(head * 0.15f, d * 13f)
        val circleR = baseName * 0.62f
        textLeft = rect.left + d * 10f + circleR * 2f + d * 8f
        textRight = rect.right - d * 8f
        initPaint.textSize = circleR
        val noteH = d * 46f
        showNote = h - head >= noteH + d * 12f
        headTop = if (showNote) (h - head - noteH - d * 6f) / 2f else (h - head) / 2f
        headH = head
        if (showNote) {
            val top = headTop + head + d * 6f
            noteRect.set(rect.left + d * 8f, top, rect.right - d * 8f, top + noteH)
            notePaint.textSize = d * 12f
            while (notePaint.measureText(NOTE_1) > noteRect.width() - noteH * 0.9f && notePaint.textSize > d * 8f) notePaint.textSize *= 0.92f
        }
        fitName()
        fitSub()
    }

    private fun fitName() {
        if (baseName <= 0f) return
        var s = baseName
        namePaint.textSize = s
        val maxW = textRight - textLeft
        while (namePaint.measureText(name) > maxW && s > baseName * 0.5f) {
            s *= 0.92f
            namePaint.textSize = s
        }
    }

    private fun fitSub() {
        if (baseSub <= 0f) return
        var s = baseSub
        subPaint.textSize = s
        val maxW = rect.width() - Theme.dpf(context, 20f)
        while (subPaint.measureText(sub) > maxW && s > baseSub * 0.6f) {
            s *= 0.92f
            subPaint.textSize = s
        }
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val d = Theme.dpf(context, 1f)
        val r = d * 14f
        fill.color = Theme.CARD
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = color
        c.drawRoundRect(rect, r, r, stroke)

        val block = labelPaint.textSize * 1.25f + baseName * 1.25f + baseSub * 1.2f
        var y = headTop + (headH - block) / 2f + labelPaint.textSize
        c.drawText(label, rect.left + d * 10f, y, labelPaint)
        y += baseName * 1.2f
        val cr = baseName * 0.62f * (1f + 0.25f * pop)
        val ccx = rect.left + d * 10f + baseName * 0.62f
        val ccy = y - baseName * 0.36f
        fill.color = color
        c.drawCircle(ccx, ccy, cr, fill)
        stroke.color = Theme.TEXT
        stroke.strokeWidth = d * 1.5f
        c.drawCircle(ccx, ccy, cr, stroke)
        stroke.strokeWidth = d * 2.5f
        c.drawText(initial, ccx, ccy + initPaint.textSize * 0.36f, initPaint)
        namePaint.color = color
        c.save()
        val sc = 1f + 0.12f * pop
        c.scale(sc, sc, textLeft, y)
        c.drawText(name, textLeft, y, namePaint)
        c.restore()
        y += baseSub * 1.6f
        if (sub.isNotEmpty()) c.drawText(sub, rect.left + d * 10f, y, subPaint)

        if (showNote) {
            // 알림 상자: 불붙은 성냥 + "마지막 성냥을 / 가져가면 패배!"
            val nr = d * 10f
            fill.color = 0x33FF4D5E
            c.drawRoundRect(noteRect, nr, nr, fill)
            stroke.color = 0x88FF4D5E.toInt()
            stroke.strokeWidth = d * 1.2f
            c.drawRoundRect(noteRect, nr, nr, stroke)
            stroke.strokeWidth = d * 2.5f
            val ml = noteRect.height() * 0.62f
            val mx = noteRect.left + noteRect.height() * 0.45f
            val my = noteRect.centerY() + ml * 0.14f
            MatchArt.drawMatch(c, mx, my, ml, 12f, 0.25f, 1f, art)
            MatchArt.drawFlame(c, mx + ml * 0.08f, my - ml * 0.36f, ml * 0.45f, 0.4f, 1f, path, art)
            val tx = noteRect.left + noteRect.height() * 0.9f
            c.drawText(NOTE_1, tx, noteRect.centerY() - notePaint.textSize * 0.2f, notePaint)
            notePaint.color = Theme.RED
            c.drawText(NOTE_2, tx, noteRect.centerY() + notePaint.textSize * 1.05f, notePaint)
            notePaint.color = Theme.TEXT
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        const val LABEL_TURN = "지금 차례"
        private const val LABEL_SUFFIX = " 차례"
        private const val NOTE_1 = "마지막 성냥을"
        private const val NOTE_2 = "가져가면 패배!"
    }
}
