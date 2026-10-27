package com.siwoo.party.common.aim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.ui.Theme

/**
 * 조준점(십자선) 그리기 도구 (다트·양궁 등, 할당 없음): 고리 + 네 방향 눈금 + 가운데 점.
 * 어두운 테두리를 먼저 그려 어느 배경에서도 잘 보인다.
 * [steady] (0..1, 예: 숨 참기로 흔들림이 줄어든 정도)가 0 보다 크면 고리 안을 은은하게 채운다.
 *
 * 예) `sway.offset(now, breath.multiplierAt(now), off); aim.draw(c, cx + off[0], cy + off[1])`
 */
class AimDrawer(context: Context) {

    private val d = Theme.dpf(context, 1f)

    /** 고리 반지름 (px) */
    var radius = 16f * d
    var color = Theme.TEXT
    var outlineColor = 0x99000000.toInt()
    /** 가운데 점 색 */
    var dotColor = Theme.RED
    /** 0..1 — 고리 안을 채우는 정도 (숨 참기 등) */
    var steady = 0f
    var steadyColor = 0x55FFC93C

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(c: Canvas, x: Float, y: Float, r: Float = radius) {
        if (steady > 0f) {
            fill.color = steadyColor
            fill.alpha = (fill.alpha * steady.coerceIn(0f, 1f)).toInt()
            c.drawCircle(x, y, r, fill)
        }
        // 테두리(어두움) → 본 색
        for (pass in 0..1) {
            val outline = pass == 0
            stroke.color = if (outline) outlineColor else color
            stroke.strokeWidth = (if (outline) 4.5f else 2.2f) * d
            c.drawCircle(x, y, r, stroke)
            val a = r * 0.45f
            val b = r * 1.35f
            c.drawLine(x - b, y, x - a, y, stroke)
            c.drawLine(x + a, y, x + b, y, stroke)
            c.drawLine(x, y - b, x, y - a, stroke)
            c.drawLine(x, y + a, x, y + b, stroke)
        }
        fill.color = outlineColor
        c.drawCircle(x, y, 3.6f * d, fill)
        fill.color = dotColor
        c.drawCircle(x, y, 2.4f * d, fill)
    }
}
