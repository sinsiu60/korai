package com.siwoo.party.games.flood.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme

/**
 * PlayerStrip 위에 겹쳐 그리는 땅 비율 막대: 각 패널 칸 아래쪽에 그 사람 색 막대 (전체 칸 중 차지한 비율).
 * 값이 바뀌면 막대가 스르르 늘어난다(짧은 ValueAnimator, 끝나면 멈춤). 터치는 받지 않는다.
 */
class ShareBarsView(context: Context, private val strip: PlayerStrip) : View(context) {

    private var colors = IntArray(0)
    private var from = FloatArray(0)
    private var to = FloatArray(0)
    private var shown = FloatArray(0)
    private var anim: ValueAnimator? = null
    /** 늘어나는 시간 (속도 배율 적용된 값) */
    var growMs = 400L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
    }

    fun setColors(c: IntArray) {
        colors = c
        from = FloatArray(c.size)
        to = FloatArray(c.size)
        shown = FloatArray(c.size)
    }

    /** 사람별 칸 수 / 전체 칸 수 (animate 면 늘어나며) */
    fun setShares(counts: IntArray, total: Int, animate: Boolean) {
        for (i in to.indices) {
            from[i] = shown[i]
            to[i] = if (total > 0) counts[i].toFloat() / total else 0f
        }
        anim?.cancel()
        if (!animate) {
            for (i in to.indices) shown[i] = to[i]
            invalidate()
            return
        }
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = growMs.coerceAtLeast(1L)
            addUpdateListener {
                val f = it.animatedValue as Float
                for (i in shown.indices) shown[i] = from[i] + (to[i] - from[i]) * f
                invalidate()
            }
            start()
        }
    }

    /** 보이는 비율 (테스트용) */
    fun shareOf(i: Int): Float = to[i]

    override fun onDraw(c: Canvas) {
        val d = Theme.dpf(context, 1f)
        val tiles = strip.tiles
        for (i in tiles.indices) {
            if (i >= shown.size) break
            val t = tiles[i]
            if (t.width <= 0) continue
            // 패널 칸 안쪽 아래 (칸 테두리 안)
            val l = strip.left + t.left + d * 12f
            val r = strip.left + t.right - d * 12f
            val b = strip.top + t.bottom - d * 8f
            val h = d * 5f
            val rr = h / 2f
            fill.color = 0x40000000
            c.drawRoundRect(l, b - h, r, b, rr, rr, fill)
            val w = (r - l) * shown[i].coerceIn(0f, 1f)
            if (w > 0f) {
                fill.color = colors[i]
                c.drawRoundRect(l, b - h, l + maxOf(w, h), b, rr, rr, fill)
            }
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}
