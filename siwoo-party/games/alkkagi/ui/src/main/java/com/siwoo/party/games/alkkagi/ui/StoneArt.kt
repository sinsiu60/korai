package com.siwoo.party.games.alkkagi.ui

import android.graphics.Canvas
import android.graphics.Paint

/**
 * 규칙 카드·아이콘용 간단한 그림 (셰이더 없이 도형만 — 매번 그려도 할당 없음).
 * 게임 판(BoardView)은 미리 만든 광택 셰이더·나무 비트맵을 쓴다.
 */
object StoneArt {

    /** 나무 색 */
    const val WOOD = 0xFFE0B26A.toInt()
    const val WOOD_DARK = 0xFFC48E45.toInt()
    const val WOOD_SIDE = 0xFF8A5A26.toInt()
    const val GRID = 0xFF4A2E14.toInt()

    /** 색을 밝게(f > 1)/어둡게(f < 1) — 할당 없음 */
    fun shade(c: Int, f: Float): Int {
        val a = c ushr 24
        var r = (c shr 16) and 0xFF
        var g = (c shr 8) and 0xFF
        var b = c and 0xFF
        if (f >= 1f) {
            val k = f - 1f
            r += ((255 - r) * k).toInt()
            g += ((255 - g) * k).toInt()
            b += ((255 - b) * k).toInt()
        } else {
            r = (r * f).toInt()
            g = (g * f).toInt()
            b = (b * f).toInt()
        }
        return (a shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    /** 나무판 조각 (l,t)-(r,b) + 격자(step 간격) + 앞쪽 옆면 */
    fun wood(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, step: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRect(l + step * 0.12f, t + step * 0.22f, r + step * 0.12f, b + step * 0.3f, p)
        p.color = WOOD_SIDE
        c.drawRect(l, b, r, b + step * 0.18f, p)
        p.color = WOOD
        c.drawRect(l, t, r, b, p)
        // 나뭇결 몇 줄
        p.style = Paint.Style.STROKE
        p.strokeWidth = step * 0.05f
        p.color = 0x22603A10
        var y = t + step * 0.35f
        var k = 0
        while (y < b) {
            c.drawLine(l, y, r, y + step * (if (k % 2 == 0) 0.12f else -0.08f), p)
            y += step * (0.55f + 0.2f * (k % 3))
            k++
        }
        p.color = GRID
        p.strokeWidth = step * 0.035f
        var x = l + step
        while (x < r - step * 0.5f) { c.drawLine(x, t + step, x, b - step, p); x += step }
        y = t + step
        while (y < b - step * 0.5f) { c.drawLine(l + step, y, r - step, y, p); y += step }
    }

    /** 광택 있는 알 (셰이더 없이: 바탕 + 밝은 부분 + 하이라이트 + 그림자) */
    fun stone(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int, alpha: Int = 255) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        p.alpha = 0x55 * alpha / 255
        c.drawCircle(x + r * 0.12f, y + r * 0.2f, r, p)
        p.color = shade(color, 0.62f)
        p.alpha = alpha
        c.drawCircle(x, y, r, p)
        p.color = color
        p.alpha = alpha
        c.drawCircle(x - r * 0.08f, y - r * 0.1f, r * 0.86f, p)
        p.color = shade(color, 1.45f)
        p.alpha = alpha
        c.drawCircle(x - r * 0.24f, y - r * 0.28f, r * 0.45f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = 220 * alpha / 255
        c.drawCircle(x - r * 0.36f, y - r * 0.42f, r * 0.16f, p)
        p.alpha = 255
    }

    /** 점선 (dash 길이 d) — 경로 효과 없이 짧은 선 여러 개로 */
    fun dotted(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, d: Float) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len <= 0f) return
        val ux = dx / len
        val uy = dy / len
        var s = 0f
        while (s < len) {
            val e = minOf(s + d, len)
            c.drawLine(x0 + ux * s, y0 + uy * s, x0 + ux * e, y0 + uy * e, p)
            s += d * 2f
        }
    }
}
