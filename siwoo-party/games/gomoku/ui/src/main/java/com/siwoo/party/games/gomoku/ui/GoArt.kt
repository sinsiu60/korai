package com.siwoo.party.games.gomoku.ui

import android.graphics.Canvas
import android.graphics.Paint

/**
 * 규칙 카드·아이콘·패널용 간단한 그림 (셰이더 없이 도형만 — 매번 그려도 할당 없음).
 * 게임 판(GoBoardView)은 미리 만든 나무 비트맵·광택 셰이더를 쓴다.
 */
object GoArt {

    const val WOOD = 0xFFE3B868.toInt()
    const val WOOD_LIGHT = 0xFFEDC983.toInt()
    const val WOOD_DARK = 0xFFC9954A.toInt()
    const val WOOD_SIDE = 0xFF8A5A26.toInt()
    const val GRID = 0xFF3A2410.toInt()

    /** 흑돌 색 (바탕 · 밝은 부분) */
    const val BLACK_BASE = 0xFF121212.toInt()
    const val BLACK_MID = 0xFF2C2C2C.toInt()
    const val BLACK_HI = 0xFF6E6E6E.toInt()
    /** 백돌 색 */
    const val WHITE_BASE = 0xFFC4C2B8.toInt()
    const val WHITE_MID = 0xFFF1EFE6.toInt()
    const val WHITE_HI = 0xFFFFFFFF.toInt()

    /**
     * 나무 바둑판 조각 (l,t)-(r,b) + 격자(step 간격, 가장자리에서 반 칸 안쪽부터) + 앞쪽 옆면.
     * 격자 첫 줄 위치는 l + step/2 + offset.
     */
    fun board(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, step: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRect(l + step * 0.1f, t + step * 0.2f, r + step * 0.1f, b + step * 0.32f, p)
        p.color = WOOD_SIDE
        c.drawRect(l, b, r, b + step * 0.2f, p)
        p.color = WOOD
        c.drawRect(l, t, r, b, p)
        // 나뭇결 몇 줄
        p.style = Paint.Style.STROKE
        p.strokeWidth = step * 0.05f
        p.color = 0x22603A10
        var y = t + step * 0.3f
        var k = 0
        while (y < b) {
            c.drawLine(l, y, r, y + step * (if (k % 2 == 0) 0.1f else -0.07f), p)
            y += step * (0.5f + 0.18f * (k % 3))
            k++
        }
        p.color = GRID
        p.strokeWidth = step * 0.045f
        var x = l + step * 0.5f
        while (x < r) { c.drawLine(x, t, x, b, p); x += step }
        y = t + step * 0.5f
        while (y < b) { c.drawLine(l, y, r, y, p); y += step }
    }

    /** 광택 바둑돌 (셰이더 없이: 그림자 + 바탕 + 밝은 부분 + 하이라이트) */
    fun stone(c: Canvas, p: Paint, x: Float, y: Float, r: Float, black: Boolean, alpha: Int = 255) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        p.alpha = 0x55 * alpha / 255
        c.drawCircle(x + r * 0.1f, y + r * 0.18f, r, p)
        p.color = if (black) BLACK_BASE else WHITE_BASE
        p.alpha = alpha
        c.drawCircle(x, y, r, p)
        p.color = if (black) BLACK_MID else WHITE_MID
        p.alpha = alpha
        c.drawCircle(x - r * 0.08f, y - r * 0.1f, r * 0.84f, p)
        p.color = if (black) BLACK_HI else WHITE_HI
        p.alpha = (if (black) 150 else 255) * alpha / 255
        c.drawCircle(x - r * 0.3f, y - r * 0.34f, r * 0.26f, p)
        p.alpha = 255
    }

    /** 승리 줄 빛 (돌 아래에 먼저 그림) */
    fun halo(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = 110
        c.drawCircle(x, y, r * 1.5f, p)
        p.alpha = 255
    }

    /** 승리 줄 금색 고리 (돌 위에 그림) */
    fun ring(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.18f
        p.color = color
        c.drawCircle(x, y, r * 1.06f, p)
        p.style = Paint.Style.FILL
    }
}
