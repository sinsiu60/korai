package com.siwoo.party.games.forehead.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 차례 안내 그림: 이마에 폰을 댄 사람 + 조작 안내.
 * 기울기: 왼쪽 초록 ↓ / 오른쪽 주황 ↑ 화살표, 터치: 폰 화면 왼쪽 초록 / 오른쪽 주황 + 손가락.
 * 아래에 색 알약 두 개 ("아래로 = 정답", "위로 = 패스"). 글자 크기는 크기가 바뀔 때만 맞춤 → onDraw 할당 없음.
 */
class IntroArtView(context: Context) : View(context) {

    var shirt = Theme.Party.NEON_PURPLE
        set(v) { field = v; invalidate() }
    var touchMode = false
        set(v) { field = v; fitLabels(); invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val path = Path()
    private val left = RectF()
    private val right = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val ph = h * 0.13f
        val gap = w * 0.04f
        val pw = (w - gap * 3) / 2f
        left.set(gap, h * 0.84f - ph, gap + pw, h * 0.84f)
        right.set(left.right + gap, left.top, left.right + gap + pw, left.bottom)
        fitLabels()
    }

    private fun fitLabels() {
        if (width <= 0) return
        label.textSize = left.height() * 0.48f
        val maxW = left.width() * 0.88f
        val a = label.measureText(if (touchMode) TOUCH_LEFT else DOWN)
        val b = label.measureText(if (touchMode) TOUCH_RIGHT else UP)
        val m = maxOf(a, b)
        if (m > maxW) label.textSize *= maxW / m
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val cx = w / 2f
        val r = minOf(h * 0.16f, w * 0.14f)
        val cy = h * 0.36f
        p.style = Paint.Style.FILL
        p.color = Theme.Party.CARD_LIGHT
        c.drawOval(cx - r * 1.7f, cy + r * 1.75f, cx + r * 1.7f, cy + r * 2.15f, p)
        ForeheadArt.drawHeadWithPhone(c, cx, cy, r, shirt, Theme.Party.NEON_PINK, "", p, path)
        if (touchMode) {
            // 폰 화면 왼쪽/오른쪽 색 + 손가락 점
            val py = cy - r * 0.42f
            val pw = r * 1.75f
            val phh = r * 0.95f
            p.color = ForeheadArt.CORRECT
            c.drawRect(cx - pw * 0.4f, py - phh * 0.38f, cx, py + phh * 0.38f, p)
            p.color = ForeheadArt.PASS
            c.drawRect(cx, py - phh * 0.38f, cx + pw * 0.4f, py + phh * 0.38f, p)
            p.color = Theme.TEXT
            c.drawCircle(cx - pw * 0.2f, py, r * 0.12f, p)
            c.drawCircle(cx + pw * 0.2f, py, r * 0.12f, p)
        } else {
            val ax = r * 2.0f
            ForeheadArt.drawTiltArrow(c, cx - ax, cy, r * 0.9f, true, ForeheadArt.CORRECT, p, path)
            ForeheadArt.drawTiltArrow(c, cx + ax, cy, r * 0.9f, false, ForeheadArt.PASS, p, path)
        }
        val pr = left.height() / 2f
        p.color = ForeheadArt.CORRECT_DARK
        c.drawRoundRect(left, pr, pr, p)
        p.color = ForeheadArt.PASS_DARK
        c.drawRoundRect(right, pr, pr, p)
        val ty = left.centerY() + label.textSize * 0.36f
        c.drawText(if (touchMode) TOUCH_LEFT else DOWN, left.centerX(), ty, label)
        c.drawText(if (touchMode) TOUCH_RIGHT else UP, right.centerX(), ty, label)
    }

    companion object {
        const val DOWN = "아래로 = 정답"
        const val UP = "위로 = 패스"
        const val TOUCH_LEFT = "왼쪽 = 정답"
        const val TOUCH_RIGHT = "오른쪽 = 패스"
    }
}
