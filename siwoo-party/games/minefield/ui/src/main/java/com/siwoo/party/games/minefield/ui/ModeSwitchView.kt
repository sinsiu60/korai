package com.siwoo.party.games.minefield.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * [열기 | 깃발] 두 칸짜리 입체 전환 버튼.
 * 고른 칸은 볼록 튀어나와 색이 들어가고(열기 = 초록, 깃발 = 빨강), 다른 칸은 눌린 듯 어둡다.
 * 휴대폰에서 꾹 누르기보다 헷갈리지 않게, 지금 모드를 눈으로 바로 볼 수 있도록 한다.
 */
class ModeSwitchView(context: Context) : View(context) {

    interface Listener {
        fun onModeChange(flagMode: Boolean)
    }

    var listener: Listener? = null

    /** 지금 깃발 모드인지 */
    var flagMode = false
        private set

    private var pressed = -1
    private val half = arrayOf(RectF(), RectF())
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private var depth = 0f

    init {
        isClickable = true
        contentDescription = DESC_OPEN
    }

    /** 모드 바꾸기 (fromUser = 사람이 눌러서 → listener 알림) */
    fun setFlagMode(v: Boolean, fromUser: Boolean = false) {
        if (flagMode == v) return
        flagMode = v
        contentDescription = if (v) DESC_FLAG else DESC_OPEN
        invalidate()
        if (fromUser) listener?.onModeChange(v)
    }

    /** 칸 가운데 (0 = 열기, 1 = 깃발) — 테스트에서 누를 때 */
    fun centerX(i: Int): Float = half[i].centerX()
    fun centerY(): Float = half[0].centerY()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = Theme.dpf(context, 1f)
        depth = d * 4f
        val gap = d * 6f
        val hw = (w - gap) / 2f
        half[0].set(0f, 0f, hw, h.toFloat())
        half[1].set(hw + gap, 0f, w.toFloat(), h.toFloat())
        labelPaint.textSize = minOf(d * 15f, h * 0.24f)
    }

    override fun onDraw(c: Canvas) {
        if (half[0].width() <= 0f) return
        for (i in 0..1) drawHalf(c, i, (i == 1) == flagMode)
    }

    private fun drawHalf(c: Canvas, i: Int, on: Boolean) {
        val r = half[i]
        val rad = Theme.dpf(context, 12f)
        val down = on || pressed == i
        val off = if (down) depth * (if (on) 0.4f else 1f) else 0f
        val base = if (i == 0) Theme.GREEN else MineArt.FLAG_RED
        val baseDark = if (i == 0) Theme.GREEN_DARK else MineArt.FLAG_RED_DARK
        // 두께
        fill.color = if (on) baseDark else 0xFF151E52.toInt()
        c.drawRoundRect(r.left, r.top + depth, r.right, r.bottom, rad, rad, fill)
        // 면
        fill.color = if (on) base else Theme.CARD
        c.drawRoundRect(r.left, r.top + off, r.right, r.bottom - depth + off, rad, rad, fill)
        if (on) {
            fill.color = 0x33FFFFFF
            c.drawRoundRect(r.left + rad * 0.4f, r.top + off + rad * 0.25f, r.right - rad * 0.4f, r.top + off + (r.height() - depth) * 0.42f, rad * 0.7f, rad * 0.7f, fill)
            // 금색 테두리
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = Theme.dpf(context, 2.5f)
            fill.color = Theme.GOLD
            c.drawRoundRect(r.left + fill.strokeWidth / 2f, r.top + off + fill.strokeWidth / 2f, r.right - fill.strokeWidth / 2f, r.bottom - depth + off - fill.strokeWidth / 2f, rad, rad, fill)
            fill.style = Paint.Style.FILL
        }
        val faceH = r.height() - depth
        val iconS = minOf(faceH * 0.5f, r.width() * 0.5f)
        val icx = r.centerX()
        val icy = r.top + off + faceH * 0.36f
        val ink = if (on) Theme.TEXT else Theme.TEXT_SUB
        if (i == 0) MineArt.drawShovel(c, icx, icy, iconS, art, if (on) Theme.TEXT else Theme.TEXT_SUB)
        else {
            // 고른 상태(빨간 면)에선 흰 깃발, 아니면 빨간 깃발
            if (on) MineArt.drawFlag(c, icx, icy, iconS * 1.1f, art, flagColor = Theme.TEXT, poleColor = MineArt.FLAG_RED_DARK)
            else MineArt.drawFlag(c, icx, icy, iconS * 1.1f, art)
        }
        labelPaint.color = ink
        c.drawText(if (i == 0) LABEL_OPEN else LABEL_FLAG, icx, r.top + off + faceH * 0.86f, labelPaint)
    }

    private fun halfAt(x: Float): Int = if (x < (half[0].right + half[1].left) / 2f) 0 else 1

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = halfAt(e.x); invalidate() }
            MotionEvent.ACTION_UP -> {
                val i = halfAt(e.x)
                val p = pressed
                pressed = -1
                performClick()
                if (i == p) setFlagMode(i == 1, fromUser = true)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    companion object {
        private const val LABEL_OPEN = "열기"
        private const val LABEL_FLAG = "깃발"
        private const val DESC_OPEN = "열기 모드"
        private const val DESC_FLAG = "깃발 모드"
    }
}
