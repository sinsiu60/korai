package com.siwoo.party.games.umul.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.umul.UmulBoard

/**
 * 규칙 카드·아이콘·패널용 간단한 그림 (셰이더 없이 도형만 — 매번 그려도 할당 없음).
 * 게임 판(WellBoardView)은 나뭇결·연못을 미리 그린 비트맵과 광택 셰이더를 쓴다.
 */
object UmulArt {

    // ── 나무판 색 ──
    const val WOOD_LIGHT = 0xFFE2B878.toInt()
    const val WOOD = 0xFFC99556.toInt()
    const val WOOD_DARK = 0xFF8E5F2E.toInt()
    const val WOOD_EDGE = 0xFF5E3B18.toInt()
    /** 판에 새긴 선 */
    const val GROOVE = 0xFF4A2C10.toInt()
    // ── 우물(연못) ──
    const val STONE_RIM = 0xFF8C8A84.toInt()
    const val STONE_RIM_DARK = 0xFF5D5B57.toInt()
    const val WATER_DEEP = 0xFF0F4E8A.toInt()
    const val WATER = 0xFF2C8BD6.toInt()
    const val WATER_LIGHT = 0xFF8FD3FF.toInt()

    private val oval = RectF()

    /** 색을 밝게(f > 1)/어둡게(f < 1) — 할당 없음 */
    fun shade(c: Int, f: Float): Int = Theme.shade(c, f)

    /** 동그란 돌 말 (그림자 + 바탕 + 밝은 면 + 반짝임) */
    fun stone(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        oval.set(x - r * 0.95f, y + r * 0.35f, x + r * 1.05f, y + r * 1.15f)
        c.drawOval(oval, p)
        p.color = shade(color, 0.5f)
        c.drawCircle(x, y, r, p)
        p.color = color
        c.drawCircle(x - r * 0.06f, y - r * 0.08f, r * 0.88f, p)
        p.color = shade(color, 1.35f)
        c.drawCircle(x - r * 0.2f, y - r * 0.24f, r * 0.5f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = 170
        c.drawCircle(x - r * 0.36f, y - r * 0.4f, r * 0.17f, p)
        p.alpha = 255
    }

    /**
     * 작은 우물고누 판 (나무 사각형 + 선 7개 + 우물). 점 사이 간격 = side, 왼쪽 위 점 = (l, t).
     * 점 i 의 중심은 px(l, side, i), py(t, side, i).
     */
    fun board(c: Canvas, p: Paint, l: Float, t: Float, side: Float) {
        val m = side * 0.24f
        val rr = side * 0.08f
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawRoundRect(l - m + side * 0.03f, t - m + side * 0.05f, l + side + m + side * 0.03f, t + side + m + side * 0.05f, rr, rr, p)
        p.color = WOOD_EDGE
        c.drawRoundRect(l - m, t - m, l + side + m, t + side + m, rr, rr, p)
        p.color = WOOD
        c.drawRoundRect(l - m + side * 0.025f, t - m + side * 0.025f, l + side + m - side * 0.025f, t + side + m - side * 0.025f, rr, rr, p)
        // 나뭇결 몇 줄
        p.style = Paint.Style.STROKE
        p.strokeWidth = side * 0.012f
        p.color = WOOD_DARK
        p.alpha = 90
        for (k in 0 until 5) {
            val y = t - m * 0.6f + (side + m * 1.2f) * (k + 0.5f) / 5f
            c.drawLine(l - m * 0.8f, y, l + side + m * 0.8f, y + side * 0.02f * (if (k % 2 == 0) 1f else -1f), p)
        }
        p.alpha = 255
        // 선 (우물 쪽 아래 변 없음)
        p.strokeWidth = side * 0.035f
        p.strokeCap = Paint.Cap.ROUND
        p.color = GROOVE
        for (line in UmulBoard.LINES) {
            c.drawLine(px(l, side, line[0]), py(t, side, line[0]), px(l, side, line[1]), py(t, side, line[1]), p)
        }
        p.strokeCap = Paint.Cap.BUTT
        well(c, p, l + side * 0.5f, t + side * 1.02f, side * 0.24f, side * 0.17f)
        // 점 자리
        p.style = Paint.Style.FILL
        for (i in 0 until UmulBoard.POINTS) {
            p.color = GROOVE
            c.drawCircle(px(l, side, i), py(t, side, i), side * 0.055f, p)
        }
    }

    /** 우물(연못): 돌 테두리 + 물 + 물결 — 가운데 (x, y), 반지름 rx·ry */
    fun well(c: Canvas, p: Paint, x: Float, y: Float, rx: Float, ry: Float) {
        p.style = Paint.Style.FILL
        p.color = STONE_RIM_DARK
        oval.set(x - rx, y - ry + ry * 0.08f, x + rx, y + ry + ry * 0.08f)
        c.drawOval(oval, p)
        p.color = STONE_RIM
        oval.set(x - rx, y - ry, x + rx, y + ry)
        c.drawOval(oval, p)
        p.color = WATER_DEEP
        oval.set(x - rx * 0.8f, y - ry * 0.76f, x + rx * 0.8f, y + ry * 0.76f)
        c.drawOval(oval, p)
        p.color = WATER
        oval.set(x - rx * 0.74f, y - ry * 0.62f, x + rx * 0.74f, y + ry * 0.72f)
        c.drawOval(oval, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, ry * 0.07f)
        p.color = WATER_LIGHT
        p.alpha = 200
        oval.set(x - rx * 0.42f, y - ry * 0.22f, x + rx * 0.3f, y + ry * 0.26f)
        c.drawOval(oval, p)
        p.alpha = 120
        oval.set(x - rx * 0.6f, y - ry * 0.42f, x + rx * 0.5f, y + ry * 0.46f)
        c.drawOval(oval, p)
        p.alpha = 255
        p.style = Paint.Style.FILL
        p.color = 0xCCFFFFFF.toInt()
        oval.set(x - rx * 0.5f, y - ry * 0.5f, x - rx * 0.18f, y - ry * 0.32f)
        c.drawOval(oval, p)
    }

    fun px(l: Float, side: Float, i: Int): Float = l + side * COL[i]
    fun py(t: Float, side: Float, i: Int): Float = t + side * ROW[i]

    /** 점 위치 (0 ~ 1): TL, TR, BL, BR, C */
    val COL = floatArrayOf(0f, 1f, 0f, 1f, 0.5f)
    val ROW = floatArrayOf(0f, 0f, 1f, 1f, 0.5f)
}
