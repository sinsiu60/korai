package com.siwoo.party.games.minefield.ui

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
 * 오른쪽 위 안내 판: "지금 차례" + 색 동그라미·이름(크게) + 안내 한 줄,
 * 아래에 지뢰 그림 + 남은 지뢰(지뢰 − 깃발 − 터진 지뢰)와 남은 안전 칸.
 * 차례가 바뀌면 톡 커졌다 돌아온다. 글자는 바뀔 때(setter)만 만든다.
 */
class InfoPanelView(context: Context) : View(context) {

    private var name = ""
    private var label = LABEL_TOP
    private var initial = ""
    private var color = Theme.GOLD
    private var hint = ""
    private var hintWarn = false
    private var minesText = "0"
    private var safeText = ""
    private var pop = 0f
    private var anim: ValueAnimator? = null

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }

    private var d = 1f
    private var baseName = 0f
    private var baseHint = 0f
    private var textLeft = 0f
    private var textRight = 0f
    private var divider = 0f

    /** 차례인 사람 (animate 면 톡) */
    fun setPlayer(playerName: String, playerColor: Int, animate: Boolean) {
        name = playerName
        initial = playerName.take(1)
        color = playerColor
        initPaint.color = Theme.textOn(playerColor)
        contentDescription = playerName + LABEL
        fitName()
        if (animate) popNow() else invalidate()
    }

    fun setHint(text: String, warn: Boolean = false) {
        if (hint == text && hintWarn == warn) return
        hint = text
        hintWarn = warn
        fitHint()
        invalidate()
    }

    /** 남은 지뢰 추정·남은 안전 칸 */
    fun setCounts(minesLeft: Int, safeLeft: Int) {
        val m = minesLeft.toString()
        val s = SAFE_PREFIX + safeLeft + SAFE_SUFFIX
        if (m == minesText && s == safeText) return
        minesText = m
        safeText = s
        invalidate()
    }

    fun hintText(): String = hint

    /** 위 작은 제목 ("지금 차례" / 끝나면 "승리") */
    fun setLabel(text: String) {
        if (label == text) return
        label = text
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        d = Theme.dpf(context, 1f)
        stroke.strokeWidth = d * 2.5f
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        labelPaint.textSize = d * 11.5f
        baseName = minOf(d * 22f, h * 0.15f)
        baseHint = d * 12f
        val circleR = baseName * 0.62f
        textLeft = rect.left + d * 10f + circleR * 2f + d * 8f
        textRight = rect.right - d * 8f
        initPaint.textSize = circleR
        bigPaint.textSize = minOf(d * 30f, h * 0.2f)
        smallPaint.textSize = d * 11.5f
        divider = rect.top + h * 0.55f
        fitName()
        fitHint()
    }

    private fun fitName() {
        if (baseName <= 0f) return
        var s = baseName
        namePaint.textSize = s
        while (namePaint.measureText(name) > textRight - textLeft && s > baseName * 0.5f) {
            s *= 0.92f
            namePaint.textSize = s
        }
    }

    private fun fitHint() {
        if (baseHint <= 0f) return
        var s = baseHint
        hintPaint.textSize = s
        val maxW = rect.width() - d * 18f
        while (hintPaint.measureText(hint) > maxW && s > baseHint * 0.6f) {
            s *= 0.93f
            hintPaint.textSize = s
        }
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val r = d * 14f
        fill.color = Theme.CARD
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = color
        stroke.strokeWidth = d * 2.5f
        c.drawRoundRect(rect, r, r, stroke)

        // 위: 지금 차례 (위쪽 절반 가운데)
        val block = labelPaint.textSize * 1.3f + baseName * 1.3f + baseHint * 1.4f
        var y = rect.top + (divider - rect.top - block) / 2f + labelPaint.textSize
        c.drawText(label, rect.left + d * 10f, y, labelPaint)
        y += baseName * 1.25f
        val cr = baseName * 0.62f * (1f + 0.25f * pop)
        val ccx = rect.left + d * 10f + baseName * 0.62f
        val ccy = y - baseName * 0.36f
        fill.color = color
        c.drawCircle(ccx, ccy, cr, fill)
        stroke.color = Theme.TEXT
        stroke.strokeWidth = d * 1.5f
        c.drawCircle(ccx, ccy, cr, stroke)
        c.drawText(initial, ccx, ccy + initPaint.textSize * 0.36f, initPaint)
        namePaint.color = color
        c.save()
        val sc = 1f + 0.12f * pop
        c.scale(sc, sc, textLeft, y)
        c.drawText(name, textLeft, y, namePaint)
        c.restore()
        y += baseHint * 1.6f
        hintPaint.color = if (hintWarn) Theme.RED else Theme.GOLD
        if (hint.isNotEmpty()) c.drawText(hint, rect.left + d * 10f, y, hintPaint)

        // 가운데 줄
        fill.color = Theme.CARD_BORDER
        c.drawRect(rect.left + d * 10f, divider, rect.right - d * 10f, divider + d, fill)

        // 아래: 지뢰 그림 + 남은 지뢰 수 + 남은 안전 칸
        val lowTop = divider + d
        val lowH = rect.bottom - lowTop
        val mr = minOf(lowH * 0.2f, d * 15f)
        val mx = rect.left + d * 14f + mr * 1.3f
        val my = lowTop + lowH * 0.42f
        MineArt.drawMine(c, mx, my, mr, art)
        val tx = mx + mr * 1.3f + d * 12f
        c.drawText(LABEL_MINES, tx, my - bigPaint.textSize * 0.42f, smallPaint)
        c.drawText(minesText, tx, my + bigPaint.textSize * 0.62f, bigPaint)
        c.drawText(safeText, rect.left + d * 12f, rect.bottom - lowH * 0.12f, smallPaint)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val LABEL = " 차례"
        private const val LABEL_TOP = "지금 차례"
        private const val LABEL_MINES = "남은 지뢰"
        private const val SAFE_PREFIX = "안전한 칸 "
        private const val SAFE_SUFFIX = "개 남음"
    }
}
