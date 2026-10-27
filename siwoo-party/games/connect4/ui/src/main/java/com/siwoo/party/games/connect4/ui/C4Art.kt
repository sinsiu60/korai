package com.siwoo.party.games.connect4.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.ui.Theme

/**
 * 규칙 카드·아이콘·패널용 간단한 그림 (셰이더 없이 도형만 — 매번 그려도 할당 없음).
 * 게임 판(DropBoardView)은 미리 만든 틀 비트맵·광택 셰이더를 쓴다.
 * 판 틀은 어떤 플레이어 색과도 겹치지 않게 짙은 남보라 + 금색 테두리.
 */
object C4Art {

    const val FRAME_LIGHT = 0xFF5A63A0.toInt()
    const val FRAME = 0xFF434A82.toInt()
    const val FRAME_DARK = 0xFF2C3160.toInt()
    /** 빈 구멍 속 (판 뒤) */
    const val HOLE = 0xFF0A1030.toInt()

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

    /** 광택 원반 돌 (바탕 + 안쪽 고리 + 하이라이트) */
    fun disc(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int, alpha: Int = 255) {
        p.style = Paint.Style.FILL
        p.color = shade(color, 0.6f)
        p.alpha = alpha
        c.drawCircle(x, y, r, p)
        p.color = color
        p.alpha = alpha
        c.drawCircle(x - r * 0.05f, y - r * 0.06f, r * 0.88f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.1f
        p.color = shade(color, 0.72f)
        p.alpha = alpha
        c.drawCircle(x, y, r * 0.6f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = 150 * alpha / 255
        c.drawCircle(x - r * 0.38f, y - r * 0.42f, r * 0.17f, p)
        p.alpha = 255
    }

    /**
     * 작은 판 (cols × rows 구멍, 칸 cell) — 왼쪽 위 (l, t). 구멍은 어두운 원으로 그린다 (규칙·아이콘용).
     * 반환: 없음. 구멍 (i, j) 의 중심 = (l + cell*(i+0.5), t + cell*(j+0.5)) (j 는 위에서부터)
     */
    fun frame(c: Canvas, p: Paint, l: Float, t: Float, cols: Int, rows: Int, cell: Float) {
        val w = cols * cell
        val h = rows * cell
        val rr = cell * 0.25f
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawRoundRect(l + cell * 0.06f, t + cell * 0.12f, l + w + cell * 0.06f, t + h + cell * 0.12f, rr, rr, p)
        p.color = FRAME
        c.drawRoundRect(l, t, l + w, t + h, rr, rr, p)
        p.color = FRAME_LIGHT
        c.drawRoundRect(l, t, l + w, t + h * 0.12f, rr, rr, p)
        p.color = FRAME
        c.drawRect(l, t + h * 0.06f, l + w, t + h * 0.12f, p)
        for (j in 0 until rows) for (i in 0 until cols) {
            val x = l + cell * (i + 0.5f)
            val y = t + cell * (j + 0.5f)
            p.color = FRAME_DARK
            c.drawCircle(x, y + cell * 0.03f, cell * 0.41f, p)
            p.color = HOLE
            c.drawCircle(x, y, cell * 0.38f, p)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = cell * 0.06f
        p.color = Theme.GOLD
        c.drawRoundRect(l, t, l + w, t + h, rr, rr, p)
        p.style = Paint.Style.FILL
    }
}
