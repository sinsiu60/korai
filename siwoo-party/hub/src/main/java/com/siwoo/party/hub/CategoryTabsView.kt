package com.siwoo.party.hub

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Theme

/**
 * 허브 상단 카테고리 탭: [전체] [🎲 보드게임] [🃏 카드·두뇌] [⚡ 순발력] [🎯 손맛·타이밍].
 * 탭마다 대표 색, 선택 탭 아래 밑줄이 미끄러지며 이동 (움직일 때만 다시 그림).
 * 선택 번호 0 = 전체, 1.. = GameCategory.HUB 순서.
 */
class CategoryTabsView(context: Context) : View(context) {

    val labels: Array<String> = arrayOf("전체") + GameCategory.HUB.map { it.emoji + " " + it.label }
    private val colors: IntArray = intArrayOf(Theme.GOLD_LIGHT) + GameCategory.HUB.map { it.color }
    /** 탭별 게임 수 (글자 옆 작은 숫자) */
    private val counts = arrayOfNulls<String>(labels.size)

    var selected = 0
        private set
    var onSelect: ((Int) -> Unit)? = null

    /** 밑줄 위치 (탭 번호, 실수) */
    private var lineAt = 0f
    private var anim: ValueAnimator? = null
    private val rect = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }

    init {
        isClickable = true
        contentDescription = "카테고리 탭"
    }

    fun setCount(tab: Int, n: Int) {
        counts[tab] = n.toString()
        invalidate()
    }

    /** 탭 고르기 (밑줄이 미끄러짐) */
    fun select(tab: Int, animate: Boolean = true) {
        if (tab !in labels.indices) return
        selected = tab
        anim?.cancel()
        if (!animate || !isAttachedToWindow) {
            lineAt = tab.toFloat()
            invalidate()
            return
        }
        anim = ValueAnimator.ofFloat(lineAt, tab.toFloat()).apply {
            duration = 240
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { lineAt = it.animatedValue as Float; invalidate() }
            start()
        }
        invalidate()
    }

    /** 테스트·접근성: 탭 누르기 */
    fun tap(tab: Int) {
        if (tab == selected) return
        select(tab)
        onSelect?.invoke(tab)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP && width > 0) {
            val tab = (e.x / (width.toFloat() / labels.size)).toInt().coerceIn(0, labels.size - 1)
            tap(tab)
            performClick()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        text.textSize = h * 0.42f
        small.textSize = h * 0.28f
        // 글자가 탭보다 넓으면 줄인다
        val tw = w.toFloat() / labels.size
        for (l in labels) {
            while (text.measureText(l) + h * 0.9f > tw && text.textSize > h * 0.25f) text.textSize *= 0.94f
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val tw = w / labels.size
        rect.set(0f, 0f, w, h * 0.86f)
        bg.color = 0x33000000
        c.drawRoundRect(rect, h * 0.3f, h * 0.3f, bg)
        // 선택 탭 배경 (밑줄과 함께 움직임)
        val lx = lineAt * tw
        val k = lineAt.toInt().coerceIn(0, labels.size - 1)
        val col = colors[if (lineAt - k > 0.5f && k + 1 < colors.size) k + 1 else k]
        bg.color = (col and 0x00FFFFFF) or 0x2E000000
        rect.set(lx + tw * 0.04f, h * 0.08f, lx + tw * 0.96f, h * 0.8f)
        c.drawRoundRect(rect, h * 0.25f, h * 0.25f, bg)
        for (i in labels.indices) {
            val cx = i * tw + tw / 2f
            text.color = if (i == selected) colors[i] else Theme.TEXT_SUB
            c.drawText(labels[i], cx, h * 0.58f, text)
            val n = counts[i]
            if (n != null) {
                small.color = if (i == selected) colors[i] else 0x88B8C3F0.toInt()
                c.drawText(n, cx + text.measureText(labels[i]) / 2f + h * 0.08f, h * 0.36f, small)
            }
        }
        // 밑줄
        bg.color = col
        rect.set(lx + tw * 0.18f, h * 0.88f, lx + tw * 0.82f, h)
        c.drawRoundRect(rect, h * 0.06f, h * 0.06f, bg)
    }
}
