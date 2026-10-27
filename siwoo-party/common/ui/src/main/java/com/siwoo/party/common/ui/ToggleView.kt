package com.siwoo.party.common.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.siwoo.party.common.GroupState

/**
 * 입체 스위치 (Canvas). 켬 / 끔 / 일부(카테고리 스위치: 게임이 일부만 켜짐 → 손잡이가 가운데 + 줄 표시).
 * 누르면 onToggle 이 불리고, 상태는 부르는 쪽이 setState 로 정한다 (연동 규칙은 SwitchGroup).
 */
class ToggleView(context: Context, onColor: Int = Theme.GREEN) : View(context) {

    /** 켜진 색 (목록에서 재사용할 때 바꿀 수 있음) */
    var onColor: Int = onColor
        set(v) { if (field != v) { field = v; invalidate() } }

    var state: GroupState = GroupState.OFF
        private set
    var onToggle: (() -> Unit)? = null

    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
    /** 손잡이 위치 0(왼쪽·끔) ~ 1(오른쪽·켬) */
    private var pos = 0f
    private var anim: ValueAnimator? = null

    init {
        isClickable = true
        setOnClickListener { onToggle?.invoke() }
    }

    val isOn: Boolean get() = state == GroupState.ON

    fun setOn(on: Boolean, animate: Boolean = true) = setState(if (on) GroupState.ON else GroupState.OFF, animate)

    fun setState(s: GroupState, animate: Boolean = true) {
        state = s
        contentDescription = when (s) { GroupState.ON -> "켬"; GroupState.OFF -> "끔"; GroupState.PARTIAL -> "일부" }
        val target = when (s) { GroupState.ON -> 1f; GroupState.OFF -> 0f; GroupState.PARTIAL -> 0.5f }
        anim?.cancel()
        if (!animate || !isAttachedToWindow) {
            pos = target
            invalidate()
            return
        }
        anim = ValueAnimator.ofFloat(pos, target).apply {
            duration = 160
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        val th = minOf(h, w * 0.55f)
        val top = (h - th) / 2f
        rect.set(0f, top, w, top + th)
        val r = th / 2f
        paint.color = when (state) {
            GroupState.ON -> onColor
            GroupState.PARTIAL -> Theme.shade(onColor, 0.6f)
            GroupState.OFF -> 0xFF3A4370.toInt()
        }
        c.drawRoundRect(rect, r, r, paint)
        paint.color = 0x33000000
        rect.set(0f, top + th * 0.55f, w, top + th)
        c.drawRoundRect(rect, r, r, paint)
        val kr = th * 0.42f
        val kx = r + (w - 2 * r) * pos
        val ky = top + th / 2f
        c.drawCircle(kx, ky + kr * 0.12f, kr, shadow)
        c.drawCircle(kx, ky, kr, knob)
        if (state == GroupState.PARTIAL) {
            paint.color = Theme.shade(onColor, 0.6f)
            paint.strokeWidth = kr * 0.3f
            c.drawLine(kx - kr * 0.45f, ky, kx + kr * 0.45f, ky, paint)
        }
    }
}
