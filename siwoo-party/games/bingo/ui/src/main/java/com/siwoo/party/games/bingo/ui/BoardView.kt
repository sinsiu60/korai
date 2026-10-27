package com.siwoo.party.games.bingo.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * 내 빙고판 (비밀 화면 오른쪽). BoardSecret 을 그리고, 칸을 누르면 onCellTap(칸).
 * - 누른 칸과 뗀 칸이 같을 때만 누름으로 친다 (스치듯 미끄러지면 무시)
 * - pop(칸): 숫자가 톡 튀어 들어가는 짧은 연출 (그동안만 다시 그림)
 * - clear(): 내용(숫자)을 지우고 참조도 버린다
 * onDraw 할당 없음.
 */
class BoardView(context: Context) : View(context) {

    /** 그리는 판 (clear 뒤엔 null) */
    var content: BoardSecret? = null
        private set

    var onCellTap: ((Int) -> Unit)? = null
    var inputEnabled = true

    private val card = RectF()
    private var downCell = -1
    private var popAnim: ValueAnimator? = null

    init {
        contentDescription = DESC
        isClickable = true
    }

    fun setContent(c: BoardSecret) {
        content = c
        if (card.width() > 0f) c.layout(this, card)
        invalidate()
    }

    /** 숫자·도장 바뀜 → 다시 그리기 */
    fun refresh() = invalidate()

    /** 칸 숫자가 톡 튀어 들어가는 연출 */
    fun pop(cell: Int, ms: Long) {
        val c = content ?: return
        popAnim?.cancel()
        c.popCell = cell
        c.popScale = 1f
        popAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms.coerceAtLeast(1)
            addUpdateListener {
                val f = it.animatedValue as Float
                content?.popScale = 1f + 0.22f * kotlin.math.sin(f * Math.PI).toFloat()
                invalidate()
            }
            start()
        }
    }

    /** 칸 가운데 (이 View 좌표) — 테스트·연출용 */
    fun cellCenter(cell: Int, out: PointF): Boolean {
        val c = content ?: return false
        if (c.cell <= 0f) return false
        out.set(c.cellLeft(cell) + c.cell / 2f, c.cellTop(cell) + c.cell / 2f)
        return true
    }

    /** 내용 지우기 (다음 사람으로 넘어가기 전) */
    fun clear() {
        popAnim?.cancel()
        popAnim = null
        content?.clear()
        content = null
        onCellTap = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        card.set(0f, 0f, w.toFloat(), h.toFloat())
        content?.layout(this, card)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val c = content ?: return false
        if (!inputEnabled || !isEnabled) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> downCell = c.cellAt(e.x, e.y)
            MotionEvent.ACTION_UP -> {
                val k = c.cellAt(e.x, e.y)
                if (k >= 0 && k == downCell) onCellTap?.invoke(k)
                downCell = -1
            }
            MotionEvent.ACTION_CANCEL -> downCell = -1
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        content?.draw(c, card)
    }

    override fun onDetachedFromWindow() {
        popAnim?.cancel()
        popAnim = null
        super.onDetachedFromWindow()
    }

    companion object {
        const val DESC = "내 빙고판"
    }
}
