package com.siwoo.party.hub

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.HubFilter
import com.siwoo.party.common.HubSort
import com.siwoo.party.common.ui.Theme

/**
 * 허브 필터 칩 줄: [지금 인원 가능만] [2인 전용] [동시 터치] [차례대로] [10분 이하] … 오른쪽 [정렬: 기본 ▾].
 * 켜진 칩은 금색, 꺼진 칩은 어둡게. 칩을 누르면 켜고 끄기 → onChange, 정렬 칩을 누르면 onSortTap.
 * 글자 폭은 크기·정렬이 바뀔 때만 잰다 (onDraw 할당 없음).
 */
class FilterBarView(context: Context) : View(context) {

    private val filters = HubFilter.values()
    private val labels = Array(filters.size) { filters[it].label }
    private val on = BooleanArray(filters.size)
    private var sortLabel = ""
    var sort: HubSort = HubSort.DEFAULT
        set(v) { field = v; sortLabel = "정렬: " + v.label + " ▾"; measureChips(); invalidate() }

    var onChange: ((Set<HubFilter>) -> Unit)? = null
    var onSortTap: (() -> Unit)? = null

    private val x0 = FloatArray(filters.size + 1)
    private val x1 = FloatArray(filters.size + 1)
    private val rect = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        isClickable = true
        contentDescription = "필터"
        sort = HubSort.DEFAULT
    }

    val selected: Set<HubFilter> get() = filters.filterIndexed { i, _ -> on[i] }.toSet()

    fun setSelected(set: Set<HubFilter>) {
        for (i in filters.indices) on[i] = filters[i] in set
        invalidate()
    }

    fun isOn(f: HubFilter): Boolean = on[f.ordinal]

    /** 테스트·접근성: 칩 누르기 */
    fun toggle(f: HubFilter) {
        on[f.ordinal] = !on[f.ordinal]
        invalidate()
        onChange?.invoke(selected)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = measureChips()

    /** 칩 위치: 필터는 왼쪽부터, 정렬은 오른쪽 끝. 넘치면 글자를 줄인다 */
    private fun measureChips() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        text.textSize = h * 0.46f
        val pad = h * 0.55f
        val gap = h * 0.22f
        while (true) {
            var total = 0f
            for (l in labels) total += text.measureText(l) + pad * 2 + gap
            total += text.measureText(sortLabel) + pad * 2
            if (total <= w || text.textSize < h * 0.3f) break
            text.textSize *= 0.94f
        }
        var x = 0f
        for (i in labels.indices) {
            x0[i] = x
            x += text.measureText(labels[i]) + pad * 2
            x1[i] = x
            x += gap
        }
        val s = filters.size
        x1[s] = w
        x0[s] = w - text.measureText(sortLabel) - pad * 2
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val x = e.x
            if (x >= x0[filters.size]) {
                onSortTap?.invoke()
            } else {
                for (i in filters.indices) if (x >= x0[i] && x <= x1[i]) { toggle(filters[i]); break }
            }
            performClick()
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val top = h * 0.1f
        val bot = h * 0.9f
        val r = (bot - top) / 2f
        stroke.strokeWidth = h * 0.05f
        for (i in filters.indices) {
            rect.set(x0[i], top, x1[i], bot)
            bg.color = if (on[i]) Theme.GOLD else 0x40000000
            c.drawRoundRect(rect, r, r, bg)
            stroke.color = if (on[i]) Theme.GOLD_LIGHT else 0x55FFFFFF
            c.drawRoundRect(rect, r, r, stroke)
            text.color = if (on[i]) Theme.INK else Theme.TEXT_SUB
            c.drawText(labels[i], (x0[i] + x1[i]) / 2f, h * 0.66f, text)
        }
        val s = filters.size
        rect.set(x0[s], top, x1[s] - stroke.strokeWidth, bot)
        bg.color = if (sort != HubSort.DEFAULT) Theme.BLUE else 0x40000000
        c.drawRoundRect(rect, r, r, bg)
        stroke.color = 0x88B8C3F0.toInt()
        c.drawRoundRect(rect, r, r, stroke)
        text.color = Theme.TEXT
        c.drawText(sortLabel, (x0[s] + x1[s]) / 2f, h * 0.66f, text)
    }
}
