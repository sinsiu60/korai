package com.siwoo.party.common.aim

import android.view.MotionEvent

/**
 * MotionEvent → [Slingshot] 상태. 게임 View 의 onTouchEvent 에서 `if (touch.onTouchEvent(e)) return true` 로 쓴다.
 *  - 첫 손가락 하나만 따라간다 (다른 손가락은 무시). 그 손가락이 떨어지면 쏘기/취소.
 *  - [setAnchor] 로 기준점(공 위치 등)을 정하면 끈 거리를 그 점에서 잰다 (안 정하면 손가락을 댄 곳).
 *  - [startFilter] 로 시작 자리 제한 (예: 공 근처를 눌렀을 때만), [enabled] = false 면 무시.
 *  - 콜백: [Listener.onAimStart] / [Listener.onAimMove] (다시 그리기) / [Listener.onShoot] (최소 거리 이상 끌고 뗌) /
 *    [Listener.onAimCancel] (최소 거리보다 짧게 뗌, 시스템 취소, [abort]).
 */
class SlingshotTouch(val sling: Slingshot) {

    interface Listener {
        fun onAimStart(s: Slingshot) {}
        fun onAimMove(s: Slingshot) {}
        /** 쏘기: s.dirX/dirY/power 사용 */
        fun onShoot(s: Slingshot)
        fun onAimCancel(s: Slingshot) {}
    }

    fun interface StartFilter {
        fun accept(x: Float, y: Float): Boolean
    }

    var listener: Listener? = null
    var startFilter: StartFilter? = null

    /** false 면 새 끌기를 받지 않음 (끄는 중에 false 가 되면 abort 할 것) */
    var enabled = true

    var anchorX = Float.NaN
        private set
    var anchorY = Float.NaN
        private set

    private var pointerId = -1

    /** 끄는 중인지 */
    val dragging: Boolean get() = pointerId >= 0

    /** 기준점 (공 위치 등) */
    fun setAnchor(x: Float, y: Float) {
        anchorX = x
        anchorY = y
    }

    /** 기준점 없음 → 손가락을 댄 곳에서 잼 */
    fun clearAnchor() {
        anchorX = Float.NaN
        anchorY = Float.NaN
    }

    /** 끄는 중이면 쏘지 않고 끝냄 (onAimCancel) */
    fun abort() {
        if (pointerId < 0) return
        pointerId = -1
        sling.cancel()
        listener?.onAimCancel(sling)
    }

    /** 처리했으면 true */
    fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = -1
                if (!enabled) return false
                val x = e.x
                val y = e.y
                if (startFilter?.accept(x, y) == false) return false
                pointerId = e.getPointerId(0)
                if (anchorX.isNaN()) sling.begin(x, y) else {
                    sling.begin(anchorX, anchorY)
                    sling.move(x, y)
                }
                listener?.onAimStart(sling)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pointerId < 0) return false
                val i = e.findPointerIndex(pointerId)
                if (i < 0) return true
                sling.move(e.getX(i), e.getY(i))
                listener?.onAimMove(sling)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (pointerId < 0) return false
                val i = e.actionIndex
                if (e.getPointerId(i) != pointerId) return true
                finish(e.getX(i), e.getY(i))
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pointerId < 0) return false
                val i = e.findPointerIndex(pointerId)
                if (i >= 0) finish(e.getX(i), e.getY(i)) else finish(sling.currentX, sling.currentY)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pointerId < 0) return false
                abort()
                return true
            }
        }
        return pointerId >= 0
    }

    private fun finish(x: Float, y: Float) {
        pointerId = -1
        if (sling.release(x, y)) listener?.onShoot(sling) else listener?.onAimCancel(sling)
    }
}
