package com.siwoo.party.common.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

/**
 * 화면 안에서 띄우는 팝업 층 (Dialog 창 대신 같은 화면 위에 그려서 연출을 자유롭게).
 * 등장: 반투명 어두운 배경 페이드 + 카드 0.9배 → 1.0배 확대 + 페이드인 (아래에서 올라오기 옵션)
 * 퇴장: 반대
 */
class PopupLayer(context: Context) : FrameLayout(context) {

    inner class Popup internal constructor(
        val wrap: FrameLayout,
        val content: View,
        val cancelable: Boolean,
        val fromBottom: Boolean,
        val onDismiss: (() -> Unit)?,
    ) {
        var closing = false
        fun dismiss(animate: Boolean = true) = this@PopupLayer.dismiss(this, animate)
    }

    private val stack = ArrayList<Popup>()
    private val loc1 = IntArray(2)
    private val loc2 = IntArray(2)

    val isShowing: Boolean get() = stack.any { !it.closing }

    fun top(): Popup? = stack.lastOrNull { !it.closing }

    /**
     * @param widthPx 카드 폭 (null = 내용에 맞춤)
     * @param fromBottom 아래에서 위로 올라오며 등장
     * @param fromView 이 뷰(예: 게임 카드) 자리에서 커지며 등장
     */
    fun show(
        content: View,
        cancelable: Boolean = false,
        widthPx: Int? = null,
        heightPx: Int? = null,
        fromBottom: Boolean = false,
        dim: Boolean = true,
        onDismiss: (() -> Unit)? = null,
        fromView: View? = null,
    ): Popup {
        val wrap = FrameLayout(context)
        if (dim) wrap.setBackgroundColor(Theme.DIM)
        wrap.isClickable = true // 뒤 화면 터치 차단
        val lp = LayoutParams(widthPx ?: LayoutParams.WRAP_CONTENT, heightPx ?: LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        content.isClickable = true
        wrap.addView(content, lp)
        addView(wrap, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val popup = Popup(wrap, content, cancelable, fromBottom, onDismiss)
        if (cancelable) wrap.setOnClickListener { popup.dismiss() }
        stack.add(popup)

        wrap.alpha = 0f
        wrap.animate().alpha(1f).setDuration(200).start()
        if (fromBottom) {
            content.translationY = resources.displayMetrics.heightPixels.toFloat()
            content.animate().translationY(0f).setDuration(420).setInterpolator(DecelerateInterpolator(2.2f)).start()
        } else if (fromView != null && fromView.width > 0) {
            // 카드 자리·크기에서 시작해 가운데로 커진다 (크기를 알아야 하므로 배치 후)
            content.alpha = 0f
            content.post {
                if (content.width == 0) { content.alpha = 1f; return@post }
                fromView.getLocationInWindow(loc1)
                content.getLocationInWindow(loc2)
                val sx = fromView.width.toFloat() / content.width
                val sy = fromView.height.toFloat() / content.height
                content.pivotX = 0f
                content.pivotY = 0f
                content.translationX = (loc1[0] - loc2[0]).toFloat()
                content.translationY = (loc1[1] - loc2[1]).toFloat()
                content.scaleX = sx
                content.scaleY = sy
                content.alpha = 0.6f
                content.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(320).setInterpolator(DecelerateInterpolator(1.6f))
                    .withEndAction { content.pivotX = content.width / 2f; content.pivotY = content.height / 2f }
                    .start()
            }
        } else {
            content.scaleX = 0.9f
            content.scaleY = 0.9f
            content.alpha = 0f
            content.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        }
        return popup
    }

    fun dismiss(p: Popup, animate: Boolean = true) {
        if (p.closing || !stack.contains(p)) return
        p.closing = true
        val finish = {
            removeView(p.wrap)
            stack.remove(p)
            p.onDismiss?.invoke()
        }
        if (!animate) {
            p.wrap.animate().cancel()
            p.content.animate().cancel()
            finish()
            return
        }
        p.wrap.animate().alpha(0f).setDuration(180).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                p.wrap.animate().setListener(null)
                finish()
            }
        }).start()
        if (p.fromBottom) {
            p.content.animate().translationY(p.content.height.toFloat() + p.wrap.height / 2f).setDuration(220).start()
        } else {
            p.content.animate().scaleX(0.9f).scaleY(0.9f).alpha(0f).setDuration(180).start()
        }
    }

    /** 떠 있는(닫히는 중 아님) 팝업 중 조건에 맞는 내용이 있는지 */
    fun contains(pred: (View) -> Boolean): Boolean = stack.any { !it.closing && pred(it.content) }

    fun dismissAll() {
        for (p in ArrayList(stack)) dismiss(p, animate = false)
    }

    /** 뒤로 가기: 맨 위 팝업이 닫을 수 있는 것이면 닫고 true */
    fun handleBack(): Boolean {
        val t = top() ?: return false
        if (t.cancelable) t.dismiss()
        return true
    }
}
