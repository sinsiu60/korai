package com.siwoo.party.games.boxes.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 화면 전체 둘레의 빛나는 테두리 (지금 차례인 사람 색).
 * 바깥쪽이 가장 진하고 안쪽으로 갈수록 옅어지는 띠를 겹쳐 그려 은은하게 빛나 보이게 한다.
 * 색이 바뀔 때만 잠깐(postOnAnimation) 섞어 바꾸고, 가만히 있을 때는 다시 그리지 않는다.
 * 터치는 받지 않는다 (아래 판으로 그대로 간다).
 */
class GlowBorderView(context: Context) : View(context) {

    private var from = Theme.GOLD
    private var to = Theme.GOLD
    private var start = 0L
    private var dur = 0L
    private var running = false
    private val frame = Runnable { onFrame() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    /** 지금 보이는 색 (테스트용) */
    val color: Int get() = to

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun now(): Long = SystemClock.uptimeMillis()

    /** 테두리 색 바꾸기 (durMs 동안 섞임, 0 이면 바로) */
    fun setColor(c: Int, durMs: Long) {
        if (c == to && !running) return
        from = current(now())
        to = c
        start = now()
        dur = durMs
        if (durMs <= 0L) {
            from = c
            invalidate()
            return
        }
        invalidate()
        if (!running) {
            running = true
            postOnAnimation(frame)
        }
    }

    private fun onFrame() {
        if (!running) return
        invalidate()
        if (now() - start < dur) postOnAnimation(frame) else running = false
    }

    private fun current(t: Long): Int {
        if (dur <= 0L) return to
        val f = ((t - start).toFloat() / dur).coerceIn(0f, 1f)
        if (f >= 1f) return to
        return Color.argb(255,
            mix(Color.red(from), Color.red(to), f),
            mix(Color.green(from), Color.green(to), f),
            mix(Color.blue(from), Color.blue(to), f))
    }

    private fun mix(a: Int, b: Int, f: Float): Int = (a + (b - a) * f).toInt()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val col = current(now())
        val step = Theme.dpf(context, 2.2f)
        val r = Theme.dpf(context, 22f)
        paint.strokeWidth = step + 1f
        paint.color = col
        for (k in ALPHAS.indices) {
            val inset = step * (k + 0.5f)
            rect.set(inset, inset, w - inset, h - inset)
            paint.alpha = ALPHAS[k]
            val rr = (r - inset).coerceAtLeast(step)
            c.drawRoundRect(rect, rr, rr, paint)
        }
        // 가장 바깥 밝은 선
        paint.strokeWidth = step * 0.6f
        paint.alpha = 255
        rect.set(step * 0.3f, step * 0.3f, w - step * 0.3f, h - step * 0.3f)
        c.drawRoundRect(rect, r, r, paint)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        /** 바깥 → 안쪽 띠의 진하기 */
        private val ALPHAS = intArrayOf(230, 170, 120, 82, 54, 32, 16, 6)
    }
}
