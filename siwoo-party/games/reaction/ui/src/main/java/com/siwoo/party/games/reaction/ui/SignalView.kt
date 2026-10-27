package com.siwoo.party.games.reaction.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 가운데 신호 원: 회색 "기다리세요..." / 빨강 "속임수!" / 초록 "지금!" / 금색 승자 이름.
 * 위에 라운드 번호. 터치는 받지 않는다(아래 구역으로 그대로 전달).
 */
class SignalView(context: Context) : View(context) {

    enum class State { WAIT, FAKE, GO, WIN, VOID }

    private var state = State.WAIT
    private var text = "기다리세요..."
    private var roundText = ""
    private var pop = 0f
    private var anim: ValueAnimator? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }

    init {
        isClickable = false
        isFocusable = false
    }

    fun setRound(text: String) {
        roundText = text
        invalidate()
    }

    fun show(s: State, label: String) {
        state = s
        text = label
        contentDescription = label
        // 바뀔 때 톡 커졌다 돌아옴
        anim?.cancel()
        anim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 260
            addUpdateListener { pop = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean = false

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.4f * (1f + 0.15f * pop)
        val color = when (state) {
            State.WAIT -> 0xFF5C6480.toInt()
            State.FAKE -> Theme.RED
            State.GO -> 0xFF2ECC71.toInt()
            State.WIN -> Theme.GOLD_DARK
            State.VOID -> Theme.GRAY
        }
        fill.color = 0x66000000
        c.drawCircle(cx + r * 0.04f, cy + r * 0.08f, r, fill)
        fill.color = color
        c.drawCircle(cx, cy, r, fill)
        ring.strokeWidth = r * 0.07f
        ring.color = if (state == State.GO || state == State.WIN) Theme.GOLD_LIGHT else 0x88FFFFFF.toInt()
        c.drawCircle(cx, cy, r * 0.9f, ring)
        textPaint.color = if (state == State.WIN) Theme.INK else 0xFFFFFFFF.toInt()
        textPaint.textSize = r * (if (text.length > 5) 0.3f else 0.42f)
        c.drawText(text, cx, cy + textPaint.textSize * 0.35f, textPaint)
        if (roundText.isNotEmpty()) {
            roundPaint.textSize = r * 0.2f
            c.drawText(roundText, cx, cy - r * 0.48f, roundPaint)
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}
