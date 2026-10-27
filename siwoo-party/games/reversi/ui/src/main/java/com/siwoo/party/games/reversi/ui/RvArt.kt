package com.siwoo.party.games.reversi.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.ui.Theme

/**
 * 뒤집기 돌 그림 도우미 (셰이더 없이 도형만 — 매 프레임 그려도 할당 없음).
 * 게임 판·규칙 카드·아이콘·패널이 같은 돌·같은 초록 판 색을 쓴다.
 */
object RvArt {

    /** 초록 펠트 */
    const val FELT = 0xFF1F7A47.toInt()
    const val FELT_LIGHT = 0xFF2A9157.toInt()
    const val FELT_DARK = 0xFF145C33.toInt()
    /** 판 테두리 (짙은 나무) */
    const val RIM = 0xFF4A2E17.toInt()
    const val RIM_LIGHT = 0xFF6E4524.toInt()
    /** 격자 (은은한 어두운 선) */
    const val GRID = 0x59002A12

    /** 흑돌: 테두리 · 윗면 · 하이라이트 */
    const val BLACK_EDGE = 0xFF050505.toInt()
    const val BLACK_FACE = 0xFF262626.toInt()
    const val BLACK_SHINE = 0xFF5C5C5C.toInt()
    /** 백돌 */
    const val WHITE_EDGE = 0xFFB9B6AA.toInt()
    const val WHITE_FACE = 0xFFF4F2EA.toInt()
    const val WHITE_SHINE = 0xFFFFFFFF.toInt()

    /**
     * 돌 하나 (그림자 + 테두리 + 윗면 + 하이라이트).
     * sx = 가로 배율 (뒤집기 연출: 1 → 0 → 1), lift = 들려 올라간 정도 (0..1, 그림자가 멀어지고 조금 커짐).
     */
    fun disc(c: Canvas, p: Paint, x: Float, y: Float, r: Float, black: Boolean, sx: Float = 1f, lift: Float = 0f, alpha: Int = 255) {
        p.style = Paint.Style.FILL
        // 그림자 (들릴수록 멀어지고 옅어짐)
        p.color = 0xFF000000.toInt()
        p.alpha = ((90 - 40 * lift) * alpha / 255).toInt()
        val off = r * (0.12f + 0.25f * lift)
        c.save()
        c.translate(x + off * 0.6f, y + off)
        c.scale(sx.coerceAtLeast(0.08f), 1f)
        c.drawCircle(0f, 0f, r, p)
        c.restore()

        val s = 1f + 0.14f * lift
        c.save()
        c.translate(x, y - r * 0.18f * lift)
        c.scale(sx * s, s)
        // 옆면처럼 보이는 테두리 (아래쪽이 조금 더 두꺼움)
        p.color = if (black) BLACK_EDGE else WHITE_EDGE
        p.alpha = alpha
        c.drawCircle(0f, 0f, r, p)
        p.color = if (black) BLACK_FACE else WHITE_FACE
        p.alpha = alpha
        c.drawCircle(0f, -r * 0.05f, r * 0.86f, p)
        // 하이라이트 (왼쪽 위 작은 빛 + 얇은 초승달)
        p.color = if (black) BLACK_SHINE else WHITE_SHINE
        p.alpha = (if (black) 170 else 255) * alpha / 255
        c.drawCircle(-r * 0.34f, -r * 0.38f, r * 0.2f, p)
        p.alpha = (if (black) 90 else 200) * alpha / 255
        c.drawCircle(r * 0.05f, -r * 0.5f, r * 0.1f, p)
        c.restore()
        p.alpha = 255
    }

    /**
     * 작은 초록 판 (규칙 카드·아이콘): 왼쪽 위 (l, t), cols × rows 칸, 칸 cell.
     * 칸 (i, j) 중심 = (l + cell*(i+0.5), t + cell*(j+0.5)).
     */
    fun board(c: Canvas, p: Paint, l: Float, t: Float, cols: Int, rows: Int, cell: Float) {
        val sw = cols * cell
        val s = rows * cell
        val e = cell * 0.22f
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawRoundRect(l - e + cell * 0.08f, t - e + cell * 0.14f, l + sw + e + cell * 0.08f, t + s + e + cell * 0.14f, e, e, p)
        p.color = RIM
        c.drawRoundRect(l - e, t - e, l + sw + e, t + s + e, e, e, p)
        p.color = FELT
        c.drawRect(l, t, l + sw, t + s, p)
        p.color = FELT_LIGHT
        c.drawRect(l, t, l + sw, t + cell * 0.35f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, cell * 0.04f)
        p.color = GRID
        for (i in 0..cols) c.drawLine(l + i * cell, t, l + i * cell, t + s, p)
        for (j in 0..rows) c.drawLine(l, t + j * cell, l + sw, t + j * cell, p)
        p.strokeWidth = maxOf(1f, cell * 0.05f)
        p.color = Theme.GOLD_DARK
        c.drawRoundRect(l - e * 0.5f, t - e * 0.5f, l + sw + e * 0.5f, t + s + e * 0.5f, e * 0.6f, e * 0.6f, p)
        p.style = Paint.Style.FILL
    }

    /** 둘 수 있는 자리 점 */
    fun hintDot(c: Canvas, p: Paint, x: Float, y: Float, r: Float, black: Boolean) {
        p.style = Paint.Style.FILL
        p.color = if (black) 0x99000000.toInt() else 0xCCFFFFFF.toInt()
        c.drawCircle(x, y, r, p)
    }

    /** 마지막 수 표시 (돌 위의 작은 빨간 점 + 흰 테) */
    fun lastMark(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, y, r * 1.35f, p)
        p.color = Theme.RED
        c.drawCircle(x, y, r, p)
    }
}
