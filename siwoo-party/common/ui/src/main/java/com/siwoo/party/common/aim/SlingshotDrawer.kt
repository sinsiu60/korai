package com.siwoo.party.common.aim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme

/**
 * 드래그 새총 그리기 도구 (할당 없음): 쏘는 방향 점선 + 고무줄(쏘는 자리 → 당긴 점) + 힘 고리.
 *  - 점선 길이 = [guideLength] × 힘 (최소 [minGuide] 비율), 점은 끝으로 갈수록 작고 흐려진다.
 *  - 힘 고리: 쏘는 자리 둘레에 힘만큼 시계 방향으로 차오르는 호 (위에서 시작). 힘 1 이면 [fullColor].
 *  - 최소 거리보다 짧으면(취소) 모두 빨강([cancelColor]) — 손을 떼도 안 쏜다는 표시.
 *
 * 예) onDraw 에서 `if (sling.active) slingDrawer.draw(c, sling, ballX, ballY)`
 */
class SlingshotDrawer(context: Context) {

    private val d = Theme.dpf(context, 1f)

    /** 힘 1 일 때 점선 길이 (px) */
    var guideLength = 150f * d
    /** 힘이 작아도 이 비율만큼은 점선을 보임 */
    var minGuide = 0.18f
    /** 점 개수 (최대 MAX_DOTS) */
    var dotCount = 12
    var dotRadius = 3.4f * d
    /** 점선 시작을 쏘는 자리에서 띄우는 거리 (공 반지름 등) */
    var guideStartGap = 0f

    var showBand = true
    var showRing = true
    var ringRadius = 26f * d

    var guideColor = Theme.TEXT
    var bandColor = Theme.GOLD_LIGHT
    var ringColor = Theme.GOLD
    var ringTrackColor = 0x66000000
    var fullColor = 0xFFFF6B3D.toInt()
    var cancelColor = Theme.RED

    private val pts = FloatArray(MAX_DOTS * 2)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val ringBox = RectF()

    /** originX/Y = 쏘는 자리 (공·구슬·포탄 위치). 끈 거리가 0 이면 아무것도 안 그림 */
    fun draw(c: Canvas, s: Slingshot, originX: Float, originY: Float) {
        if (s.distance <= 0f) return
        val cancelled = s.cancelled
        val power = s.power
        val full = power >= 0.999f
        // 고무줄: 쏘는 자리 → 당긴 점
        if (showBand) {
            val px = originX + (s.pullX - s.startX)
            val py = originY + (s.pullY - s.startY)
            stroke.color = 0x55000000
            stroke.strokeWidth = 6f * d
            c.drawLine(originX, originY, px, py, stroke)
            stroke.color = if (cancelled) cancelColor else bandColor
            stroke.strokeWidth = 3f * d
            c.drawLine(originX, originY, px, py, stroke)
            fill.color = if (cancelled) cancelColor else bandColor
            c.drawCircle(px, py, 5f * d, fill)
        }
        // 방향 점선
        // 점 간격은 늘 같게 (guideLength / dotCount) — 힘이 작으면 점 수가 줄어든다
        val maxDots = minOf(dotCount, MAX_DOTS)
        val frac = maxOf(power, minGuide)
        val n = maxOf(2, (maxDots * frac + 0.5f).toInt()).coerceAtMost(maxDots)
        if (maxDots > 0) {
            val len = guideLength * frac
            val dx = s.dirX
            val dy = s.dirY
            val k = s.guideLine(originX + dx * guideStartGap, originY + dy * guideStartGap, len, n, pts)
            val color = if (cancelled) cancelColor else if (full) fullColor else guideColor
            for (i in 0 until k) {
                val f = i.toFloat() / maxDots
                val r = dotRadius * (1f - 0.45f * f)
                val x = pts[2 * i]
                val y = pts[2 * i + 1]
                c.drawCircle(x + 1f * d, y + 1.5f * d, r, shadow)
                fill.color = color
                fill.alpha = ((if (cancelled) 150 else 255) * (1f - 0.55f * f)).toInt()
                c.drawCircle(x, y, r, fill)
            }
        }
        // 힘 고리
        if (showRing) {
            ringBox.set(originX - ringRadius, originY - ringRadius, originX + ringRadius, originY + ringRadius)
            stroke.strokeWidth = 5f * d
            stroke.color = ringTrackColor
            c.drawArc(ringBox, 0f, 360f, false, stroke)
            stroke.color = if (cancelled) cancelColor else if (full) fullColor else ringColor
            c.drawArc(ringBox, -90f, 360f * power, false, stroke)
        }
    }

    companion object {
        const val MAX_DOTS = 24
    }
}
