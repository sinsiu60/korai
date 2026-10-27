package com.siwoo.party.common.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 세로 슬롯머신 (랜덤 후보가 많을 때 원형 룰렛 대신).
 * 이름 칸이 위에서 아래로 빠르게 지나가다 점점 느려져 가운데 줄에 멈춘다.
 * 결과는 부르는 쪽이 정하고(spin 의 target) 연출만 한다.
 */
class SlotMachineView(context: Context) : View(context) {

    private var names: Array<String> = emptyArray()
    private var colors: IntArray = IntArray(0)
    /** 맨 위로 지나간 칸 수 (실수) — 가운데 줄에 보이는 칸 = floor(offset) */
    private var offset = 0f
    private var anim: ValueAnimator? = null
    var spinning = false
        private set
    /** [돌리기] 터치 (멈춰 있을 때만) */
    var onSpinRequest: (() -> Unit)? = null

    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var fadeTop: Shader? = null
    private var fadeBottom: Shader? = null

    init {
        isClickable = true
        setOnClickListener { if (!spinning) onSpinRequest?.invoke() }
    }

    fun setItems(names: Array<String>, colors: IntArray) {
        this.names = names
        this.colors = colors
        offset = 0f
        invalidate()
    }

    /**
     * target 칸에 멈추도록 돌린다. 최소 몇 바퀴 돈 뒤 감속.
     * onTick: 한 칸 지날 때마다 (효과음)
     */
    fun spin(target: Int, durationMs: Long, onTick: () -> Unit, onEnd: () -> Unit) {
        val n = names.size
        if (n == 0) return
        anim?.cancel()
        spinning = true
        val start = offset
        val cur = Math.floorMod(start.toInt(), n)
        val laps = 3
        val end = start - (start - start.toInt()) + laps * n + Math.floorMod(target - cur, n)
        var lastCell = start.toInt()
        anim = ValueAnimator.ofFloat(start, end).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener {
                offset = it.animatedValue as Float
                val cell = offset.toInt()
                if (cell != lastCell) { lastCell = cell; onTick() }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    offset = end
                    spinning = false
                    invalidate()
                    onEnd()
                }
            })
            start()
        }
    }

    /** 지금 가운데 줄 칸 */
    val current: Int get() = if (names.isEmpty()) -1 else Math.floorMod(offset.toInt(), names.size)

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val rowH = h / ROWS.toFloat()
        fadeTop = LinearGradient(0f, 0f, 0f, rowH * 1.4f, 0xEE0B1340.toInt(), 0x000B1340, Shader.TileMode.CLAMP)
        fadeBottom = LinearGradient(0f, h - rowH * 1.4f, 0f, h.toFloat(), 0x000B1340, 0xEE0B1340.toInt(), Shader.TileMode.CLAMP)
        text.textSize = rowH * 0.42f
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rowH = h / ROWS
        val r = rowH * 0.25f
        rect.set(0f, 0f, w, h)
        paint.color = Theme.BG_DEEP
        c.drawRoundRect(rect, r, r, paint)
        val n = names.size
        if (n > 0) {
            c.save()
            c.clipRect(0f, 0f, w, h)
            val base = offset.toInt()
            val frac = offset - base
            // 가운데 줄(ROWS/2)에 base 칸이 오고, 칸은 위에서 아래로 내려온다
            for (k in -1..ROWS) {
                val cell = base + (ROWS / 2 - k)
                val idx = Math.floorMod(cell, n)
                val y = k * rowH + frac * rowH
                rect.set(w * 0.04f, y + rowH * 0.06f, w * 0.96f, y + rowH * 0.94f)
                paint.color = Theme.shade(colors[idx], 0.8f)
                c.drawRoundRect(rect, r, r, paint)
                text.color = Theme.textOn(colors[idx])
                c.drawText(names[idx], w / 2f, y + rowH * 0.64f, text)
            }
            c.restore()
        }
        paint.shader = fadeTop
        c.drawRect(0f, 0f, w, rowH * 1.4f, paint)
        paint.shader = fadeBottom
        c.drawRect(0f, h - rowH * 1.4f, w, h, paint)
        paint.shader = null
        // 가운데 줄 금색 틀
        frame.color = Theme.GOLD
        frame.strokeWidth = rowH * 0.07f
        val cy = (ROWS / 2) * rowH
        rect.set(w * 0.02f, cy, w * 0.98f, cy + rowH)
        c.drawRoundRect(rect, r, r, frame)
    }

    companion object {
        /** 보이는 줄 수 (홀수, 가운데가 결과) */
        const val ROWS = 5
    }
}
