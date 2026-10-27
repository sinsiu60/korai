package com.siwoo.party.games.minigolf.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * 미니 골프 그림 조각 (아이콘·규칙 카드·코스가 같이 씀). 할당 없음 — Path 하나를 재사용하므로 메인 스레드에서만.
 */
object GolfArt {
    const val GRASS = 0xFF4FAE4A.toInt()
    const val GRASS_DARK = 0xFF3E9440.toInt()
    const val ROUGH = 0xFF1E5A2C.toInt()
    const val RAIL = 0xFF9A6634.toInt()
    const val RAIL_DARK = 0xFF5E3A1A.toInt()
    const val SAND = 0xFFEBD49A.toInt()
    const val SAND_DARK = 0xFFC9A964.toInt()
    const val WATER = 0xFF2C86DA.toInt()
    const val WATER_DARK = 0xFF1A5AA6.toInt()
    const val WATER_LIGHT = 0xFFA6DBFF.toInt()
    const val BLOCK = 0xFFB98A5E.toInt()
    const val BLOCK_DARK = 0xFF6E4A2A.toInt()
    const val SLIDER = 0xFFFFC93C.toInt()
    const val SLIDER_DARK = 0xFF3A2A10.toInt()
    const val FLAG = 0xFFE53935.toInt()
    const val CUP = 0xFF101410.toInt()

    private val path = Path()

    /** 하얀 골프공 (그림자 + 몸 + 반짝이) — 반지름 r */
    fun ball(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawCircle(x + r * 0.18f, y + r * 0.28f, r, p)
        p.color = 0xFFF4F6F8.toInt()
        c.drawCircle(x, y, r, p)
        p.color = 0xFFD5DAE0.toInt()
        c.drawCircle(x + r * 0.18f, y + r * 0.2f, r * 0.72f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x - r * 0.12f, y - r * 0.12f, r * 0.7f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x - r * 0.35f, y - r * 0.38f, r * 0.22f, p)
    }

    /** 컵 (검은 구멍 + 하얀 테) */
    fun cup(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFFEFF3EA.toInt()
        c.drawCircle(x, y, r * 1.12f, p)
        p.color = CUP
        c.drawCircle(x, y, r, p)
        p.color = 0xFF2E3A2E.toInt()
        c.drawCircle(x, y + r * 0.22f, r * 0.72f, p)
    }

    /** 깃발: 컵에서 위로 기둥 h, 오른쪽으로 깃발 */
    fun flag(c: Canvas, p: Paint, x: Float, y: Float, h: Float, color: Int = FLAG) {
        val w = h * 0.08f
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        c.drawRect(x + w * 0.5f, y - h + w, x + w * 1.6f, y, p)
        p.color = 0xFFF5F5F5.toInt()
        c.drawRect(x - w / 2f, y - h, x + w / 2f, y, p)
        path.reset()
        path.moveTo(x + w / 2f, y - h)
        path.lineTo(x + h * 0.55f, y - h * 0.82f)
        path.lineTo(x + w / 2f, y - h * 0.62f)
        path.close()
        p.color = color
        c.drawPath(path, p)
        p.color = 0x33000000
        path.reset()
        path.moveTo(x + w / 2f, y - h * 0.72f)
        path.lineTo(x + h * 0.55f, y - h * 0.82f)
        path.lineTo(x + w / 2f, y - h * 0.62f)
        path.close()
        c.drawPath(path, p)
    }

    /** 경사 표시 화살 (꺾쇠): (x, y) 에서 (dx, dy) 방향, 크기 s */
    fun chevron(c: Canvas, p: Paint, x: Float, y: Float, dx: Float, dy: Float, s: Float) {
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len <= 0f) return
        val ux = dx / len
        val uy = dy / len
        val tipX = x + ux * s * 0.5f
        val tipY = y + uy * s * 0.5f
        val bx = x - ux * s * 0.2f
        val by = y - uy * s * 0.2f
        c.drawLine(bx - uy * s * 0.5f, by + ux * s * 0.5f, tipX, tipY, p)
        c.drawLine(bx + uy * s * 0.5f, by - ux * s * 0.5f, tipX, tipY, p)
    }

    /** 노랑·검정 줄무늬 움직이는 블록 */
    fun slider(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, stripe: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRect(l + stripe * 0.25f, t + stripe * 0.45f, r + stripe * 0.25f, b + stripe * 0.45f, p)
        p.color = SLIDER
        c.drawRect(l, t, r, b, p)
        c.save()
        c.clipRect(l, t, r, b)
        p.color = SLIDER_DARK
        p.strokeWidth = stripe * 0.45f
        var x = l - (b - t)
        while (x < r) {
            c.drawLine(x, b, x + (b - t), t, p)
            x += stripe
        }
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = stripe * 0.18f
        p.color = SLIDER_DARK
        c.drawRect(l, t, r, b, p)
        p.style = Paint.Style.FILL
    }

    /** 나무 블록 (그림자 + 윗면 + 테두리) */
    fun block(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, u: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawRect(l + u * 0.18f, t + u * 0.3f, r + u * 0.18f, b + u * 0.3f, p)
        p.color = BLOCK_DARK
        c.drawRect(l, t, r, b, p)
        p.color = BLOCK
        c.drawRect(l + u * 0.12f, t + u * 0.1f, r - u * 0.12f, b - u * 0.18f, p)
        p.color = 0x33FFFFFF
        c.drawRect(l + u * 0.12f, t + u * 0.1f, r - u * 0.12f, t + u * 0.3f, p)
    }
}
