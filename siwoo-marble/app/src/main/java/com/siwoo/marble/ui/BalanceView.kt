package com.siwoo.marble.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * 거래 화면 가운데 저울: 왼쪽(내가 주는 총가치)과 오른쪽(내가 받는 총가치)을 비교해
 * 무거운 쪽으로 기울어진다. 값이 바뀌면 부드럽게 흔들리며 움직인다.
 */
class BalanceView(context: Context) : View(context) {

    private var left = 0
    private var right = 0
    private var angle = 0f          // 도. 양수 = 오른쪽이 내려감
    private var anim: ValueAnimator? = null

    private val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GOLD }
    private val goldDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GOLD_DARK }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GOLD_LIGHT; style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT_SUB; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val path = Path()
    private val rect = RectF()

    fun setValues(give: Int, take: Int) {
        left = give
        right = take
        val max = maxOf(give, take, 1)
        val target = ((take - give).toFloat() / max * MAX_ANGLE).coerceIn(-MAX_ANGLE, MAX_ANGLE)
        anim?.cancel()
        val from = angle
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(2.5f)
            addUpdateListener { angle = from + (target - from) * it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val top = h * 0.28f
        val beam = w * 0.42f
        line.strokeWidth = w * 0.025f
        label.textSize = w * 0.085f

        // 받침대
        path.reset()
        path.moveTo(cx, top)
        path.lineTo(cx - w * 0.12f, h * 0.9f)
        path.lineTo(cx + w * 0.12f, h * 0.9f)
        path.close()
        c.drawPath(path, goldDark)
        rect.set(cx - w * 0.22f, h * 0.88f, cx + w * 0.22f, h * 0.95f)
        c.drawRoundRect(rect, h * 0.03f, h * 0.03f, gold)

        // 기울어진 막대
        val rad = Math.toRadians(angle.toDouble())
        val dx = (cos(rad) * beam).toFloat()
        val dy = (sin(rad) * beam).toFloat()
        val lx = cx - dx; val ly = top - dy
        val rx = cx + dx; val ry = top + dy
        line.strokeWidth = w * 0.035f
        c.drawLine(lx, ly, rx, ry, line)
        c.drawCircle(cx, top, w * 0.04f, gold)

        // 접시 (줄 + 그릇)
        drawPan(c, lx, ly, w, h, "줌")
        drawPan(c, rx, ry, w, h, "받음")
    }

    private fun drawPan(c: Canvas, x: Float, y: Float, w: Float, h: Float, text: String) {
        val drop = h * 0.22f
        line.strokeWidth = w * 0.012f
        c.drawLine(x, y, x - w * 0.1f, y + drop, line)
        c.drawLine(x, y, x + w * 0.1f, y + drop, line)
        rect.set(x - w * 0.14f, y + drop - h * 0.03f, x + w * 0.14f, y + drop + h * 0.07f)
        c.drawArc(rect, 0f, 180f, true, gold)
        c.drawText(text, x, y + drop + h * 0.2f, label)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MAX_ANGLE = 16f
    }
}
