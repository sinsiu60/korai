package com.siwoo.party.games.liar.ui

import com.siwoo.party.common.ui.PartySettings
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.liar.GameConfig

/**
 * 남은 시간 원형 타이머: 금색 고리가 줄어들고 가운데 남은 초. 5초 이하면 빨갛게.
 * 초가 바뀔 때(setTime)만 글자를 만든다 → onDraw 할당 없음. 매초 한 번만 다시 그린다.
 */
class TimerView(context: Context) : View(context) {

    var seconds = 0
        private set
    private var total = 1
    private var text = ""
    private var label = ""

    private val oval = RectF()
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33FFFFFF }
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }

    /** 남은 초 / 전체 초, 아래 작은 글자 */
    fun setTime(sec: Int, totalSec: Int, label: String = this.label) {
        val s = sec.coerceAtLeast(0)
        if (s == seconds && totalSec == total && label == this.label && text.isNotEmpty()) return
        seconds = s
        total = totalSec.coerceAtLeast(1)
        text = s.toString()
        this.label = label
        contentDescription = "남은 시간 ${s}초"
        // 마지막 몇 초는 통통 튄다
        if (s in 1..GameConfig.WARN_SEC) {
            animate().cancel()
            scaleX = 1.12f; scaleY = 1.12f
            animate().scaleX(1f).scaleY(1f).setDuration(PartySettings(context).t(250)).start()
        }
        invalidate()
    }

    val warn: Boolean get() = seconds <= GameConfig.WARN_SEC

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val s = minOf(w, h).toFloat()
        val sw = s * 0.09f
        ring.strokeWidth = sw
        track.strokeWidth = sw
        oval.set((w - s) / 2f + sw, (h - s) / 2f + sw, (w + s) / 2f - sw, (h + s) / 2f - sw)
        numPaint.textSize = s * 0.32f
        labelPaint.textSize = s * 0.1f
    }

    override fun onDraw(c: Canvas) {
        if (oval.width() <= 0f) return
        val col = if (warn) Theme.RED else Theme.GOLD
        disc.color = Theme.BG_DEEP
        c.drawCircle(oval.centerX(), oval.centerY(), oval.width() / 2f + ring.strokeWidth * 0.5f, disc)
        c.drawArc(oval, 0f, 360f, false, track)
        ring.color = col
        val sweep = 360f * seconds / total
        if (sweep > 0f) c.drawArc(oval, -90f, sweep, false, ring)
        numPaint.color = if (warn) Theme.RED else Theme.TEXT
        c.drawText(text, oval.centerX(), oval.centerY() + numPaint.textSize * 0.3f, numPaint)
        if (label.isNotEmpty()) c.drawText(label, oval.centerX(), oval.centerY() + oval.height() * 0.3f, labelPaint)
    }
}
