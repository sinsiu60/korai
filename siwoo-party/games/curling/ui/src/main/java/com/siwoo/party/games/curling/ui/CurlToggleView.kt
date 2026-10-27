package com.siwoo.party.games.curling.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 휘는 방향 고르기 버튼 두 칸: [↺ 왼쪽] [↻ 오른쪽] (Canvas 로 그린 입체 버튼).
 * 고른 칸은 금색, 못 고를 때(스톤이 움직이는 중)는 흐리게. 칸을 누르면 listener.onPick(−1 / +1).
 */
class CurlToggleView(context: Context) : View(context) {

    fun interface Listener {
        fun onPick(dir: Int)
    }

    var listener: Listener? = null

    /** 고른 방향 (+1 오른쪽, −1 왼쪽) */
    var selected = 1
        private set
    /** false 면 누를 수 없음 (흐리게) */
    var active = true
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }

    private val d = Theme.dpf(context, 1f)
    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val rect = RectF()
    private var downSeg = 0
    private var pressedSeg = 0

    init {
        isClickable = true
    }

    fun select(dir: Int) {
        val v = if (dir < 0) -1 else 1
        if (selected == v) return
        selected = v
        invalidate()
    }

    /** 칸 가운데 (테스트용): dir −1 = 왼쪽 칸, +1 = 오른쪽 칸 */
    fun segmentCenter(dir: Int, out: PointF) {
        val half = width / 2f
        out.set(if (dir < 0) half / 2f else half * 1.5f, height / 2f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        text.textSize = minOf(15f * d, h * 0.34f)
    }

    private fun segAt(x: Float): Int = if (x < width / 2f) -1 else 1

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!active) return false
                downSeg = segAt(e.x)
                pressedSeg = downSeg
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val s = if (e.x >= 0f && e.x <= width && e.y >= 0f && e.y <= height) segAt(e.x) else 0
                val v = if (s == downSeg) downSeg else 0
                if (v != pressedSeg) { pressedSeg = v; invalidate() }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val hit = pressedSeg
                pressedSeg = 0
                downSeg = 0
                invalidate()
                if (hit != 0 && active) {
                    select(hit)
                    listener?.onPick(hit)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedSeg = 0
                downSeg = 0
                invalidate()
                return true
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val depth = 3f * d
        val gap = 6f * d
        val segW = (w - gap) / 2f
        for (k in 0..1) {
            val dir = if (k == 0) -1 else 1
            val l = k * (segW + gap)
            val on = selected == dir
            val down = pressedSeg == dir
            val dy = if (down) depth else 0f
            val base = if (on) Theme.GOLD_DARK else 0xFF1A2550.toInt()
            val top = if (on) Theme.GOLD else Theme.CARD_LIGHT
            // 두께
            face.color = base
            rect.set(l, depth, l + segW, h)
            c.drawRoundRect(rect, 12f * d, 12f * d, face)
            // 면
            face.color = top
            rect.set(l, dy, l + segW, h - depth + dy)
            c.drawRoundRect(rect, 12f * d, 12f * d, face)
            face.color = 0x30FFFFFF
            rect.set(l + 4f * d, dy + 3f * d, l + segW - 4f * d, dy + (h - depth) * 0.42f)
            c.drawRoundRect(rect, 9f * d, 9f * d, face)
            // 아이콘 + 글자 (가운데 정렬)
            val txt = if (dir < 0) LEFT else RIGHT
            val ir = (h - depth) * 0.2f
            val tw = text.measureText(txt)
            val total = ir * 2f + 6f * d + tw
            val x0 = l + (segW - total) / 2f
            val cy = dy + (h - depth) / 2f
            val fg = if (on) Theme.INK else Theme.TEXT
            CurlArt.spinIcon(c, icon, x0 + ir, cy + ir * 0.1f, ir, dir, fg)
            text.color = fg
            c.drawText(txt, x0 + ir * 2f + 6f * d, cy + text.textSize * 0.36f, text)
            if (!active) {
                face.color = 0x88101840.toInt()
                rect.set(l, dy, l + segW, h - depth + dy)
                c.drawRoundRect(rect, 12f * d, 12f * d, face)
            }
        }
    }

    companion object {
        private const val LEFT = "왼쪽"
        private const val RIGHT = "오른쪽"
    }
}
