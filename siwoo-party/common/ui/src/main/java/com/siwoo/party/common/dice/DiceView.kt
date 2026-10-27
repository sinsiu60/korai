package com.siwoo.party.common.dice

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View

/**
 * 주사위 줄 View (야추 등): 가운데에 count 개를 한 줄로. 굴리지 않을 때 주사위를 누르면 onDieTapped.
 * 그리기는 공통 DiceSet.
 */
class DiceView(context: Context) : View(context) {

    val dice = DiceSet(this)
    var onDieTapped: ((Int) -> Unit)? = null
    /** 고정 표시 등 터치를 받을지 */
    var tapEnabled = true

    fun setCount(n: Int) {
        dice.count = n
        relayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0) return
        val n = dice.count
        val s = minOf(height * 0.62f, width / (n * 1.35f))
        dice.layoutRow(width / 2f, height * 0.44f, s, s * 0.32f)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) return tapEnabled && !dice.rolling
        if (e.actionMasked == MotionEvent.ACTION_UP && tapEnabled && !dice.rolling) {
            val i = dice.hit(e.x, e.y)
            if (i >= 0) {
                performClick()
                onDieTapped?.invoke(i)
            }
            return true
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(c: Canvas) {
        dice.draw(c)
    }

    override fun onDetachedFromWindow() {
        dice.cancel()
        super.onDetachedFromWindow()
    }
}
