package com.siwoo.party.games.liardice.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.dice.DiceSet
import com.siwoo.party.common.ui.Theme

/**
 * 부를 눈 고르기: 주사위 6개 (위 1·2·3, 아래 4·5·6) — 공통 DiceSet 두 줄로 그린다.
 * - 고른 눈: 금색 빛 + 금색 테(살짝 내려앉음)
 * - 지금 개수로는 직전보다 낮아지는 눈: 흐리게 (눌러서 고를 수는 있음, [부르기]가 꺼짐)
 * - 부를 수 없는 눈 (와일드일 때 1): 흐리게 + 빨간 빗금, 누르면 무시
 * onDraw 에서 객체를 만들지 않는다.
 */
class FacePickerView(context: Context) : View(context) {

    /** 고른 눈 (1~6) */
    var selected = 2
        private set
    /** 눈을 고를 때 (허용된 눈만) */
    var onPick: ((Int) -> Unit)? = null
    /** 입력 받기 */
    var inputEnabled = true
        set(v) { field = v; invalidate() }

    private val top = DiceSet(this).apply { count = 3 }
    private val bottom = DiceSet(this).apply { count = 3 }
    private val allowed = BooleanArray(7) { true }
    private val valid = BooleanArray(7) { true }
    private var s = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    init {
        top.setValues(intArrayOf(1, 2, 3))
        bottom.setValues(intArrayOf(4, 5, 6))
        contentDescription = "눈 고르기"
    }

    /** 고른 눈·부를 수 있는 눈(allowed)·지금 개수로 올라가는 눈(valid) 한꺼번에 (인덱스 1~6) */
    fun setState(sel: Int, allowedFaces: BooleanArray, validFaces: BooleanArray) {
        selected = sel
        for (f in 1..6) {
            allowed[f] = allowedFaces[f]
            valid[f] = validFaces[f]
        }
        for (k in 0 until 3) {
            top.held[k] = sel == k + 1
            bottom.held[k] = sel == k + 4
        }
        invalidate()
    }

    fun isAllowed(face: Int) = allowed[face]

    /** 눈 face 의 가운데 (이 View 좌표) — 테스트·연출용 */
    fun faceCenterX(face: Int): Float = if (face <= 3) top.centerX(face - 1) else bottom.centerX(face - 4)

    fun faceCenterY(face: Int): Float = if (face <= 3) height * 0.27f else height * 0.73f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        s = minOf(h * 0.36f, w / 4.3f)
        top.layoutRow(w / 2f, h * 0.27f, s, s * 0.42f)
        bottom.layoutRow(w / 2f, h * 0.73f, s, s * 0.42f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!inputEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val f = faceAt(e.x, e.y)
                if (f > 0 && allowed[f]) {
                    performClick()
                    onPick?.invoke(f)
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** (x, y) 의 눈 (없으면 0). 넉넉하게: 위/아래 절반 + 가장 가까운 칸 */
    fun faceAt(x: Float, y: Float): Int {
        val row = if (y < height / 2f) top else bottom
        var best = -1
        var bd = Float.MAX_VALUE
        for (k in 0 until 3) {
            val d = kotlin.math.abs(row.centerX(k) - x)
            if (d < bd) { bd = d; best = k }
        }
        if (best < 0 || bd > s * 0.95f) return 0
        return best + 1 + if (row === bottom) 3 else 0
    }

    override fun onDraw(c: Canvas) {
        if (s <= 0f) return
        // 고른 눈 빛
        if (selected in 1..6) {
            val x = faceCenterX(selected)
            val y = faceCenterY(selected) + s * 0.14f
            fill.color = 0x66FFC93C
            c.drawCircle(x, y, s * 0.82f, fill)
        }
        val a = if (inputEnabled) 255 else 130
        top.draw(c, 0f, a)
        bottom.draw(c, 0f, a)
        for (f in 1..6) {
            if (allowed[f] && valid[f]) continue
            val x = faceCenterX(f)
            val y = faceCenterY(f) + if (f == selected) s * 0.14f else 0f
            val h = s * 0.56f
            fill.color = Theme.BG_DEEP
            fill.alpha = if (allowed[f]) 120 else 170
            c.drawRoundRect(x - h, y - h, x + h, y + h, s * 0.2f, s * 0.2f, fill)
            fill.alpha = 255
            if (!allowed[f]) {
                stroke.color = Theme.RED
                stroke.strokeWidth = s * 0.1f
                c.drawLine(x - h * 0.8f, y + h * 0.8f, x + h * 0.8f, y - h * 0.8f, stroke)
            }
        }
    }

    override fun onDetachedFromWindow() {
        top.cancel()
        bottom.cancel()
        super.onDetachedFromWindow()
    }
}
