package com.siwoo.party.games.bingo.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bingo.BingoLines
import com.siwoo.party.games.bingo.GameConfig

/**
 * 빙고 그림 도우미 (아이콘·규칙 카드·공개 화면 공용). 모두 할당 없이 그린다.
 * 숫자 글자는 NUM 에 미리 만들어 둔다.
 */
object BingoArt {
    /** 0..25 숫자 글자 (0 은 "") */
    val NUM: Array<String> = Array(GameConfig.CELLS + 1) { if (it == 0) "" else it.toString() }
    /** "1!".."25!" (부른 숫자 크게) */
    val SHOUT: Array<String> = Array(GameConfig.CELLS + 1) { if (it == 0) "?" else "$it!" }

    /** 공 색 (주황 → 금색) */
    const val BALL = 0xFFFF8A2A.toInt()
    const val BALL_LIGHT = 0xFFFFC46B.toInt()
    /** 판 테두리 남색 */
    const val FRAME = 0xFF2B3C8F.toInt()
    /** 빈칸 바탕 */
    const val EMPTY = 0xFFE6E2D6.toInt()

    /**
     * 작은 빙고판 (l, t, 한 변 s).
     * @param nums 칸 순서 숫자 (null 이면 숫자 없음, 0 은 빈칸)
     * @param stamped 도장 찍힌 칸 비트 (1 shl 칸)
     * @param lines 완성 줄 비트 (1 shl 줄 번호, BingoLines 순서)
     */
    fun miniBoard(c: Canvas, p: Paint, l: Float, t: Float, s: Float, nums: IntArray?, stamped: Int, stampColor: Int, lines: Int) {
        val pad = s * 0.05f
        val gap = s * 0.018f
        val cell = (s - pad * 2f - gap * 4f) / 5f
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRoundRect(l + s * 0.02f, t + s * 0.03f, l + s * 1.02f, t + s * 1.03f, s * 0.08f, s * 0.08f, p)
        p.color = FRAME
        c.drawRoundRect(l, t, l + s, t + s, s * 0.08f, s * 0.08f, p)
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = cell * 0.48f
        for (k in 0 until GameConfig.CELLS) {
            val x = l + pad + (k % 5) * (cell + gap)
            val y = t + pad + (k / 5) * (cell + gap)
            val n = nums?.get(k) ?: -1
            p.color = if (n == 0) EMPTY else Theme.PAPER
            c.drawRoundRect(x, y, x + cell, y + cell, cell * 0.16f, cell * 0.16f, p)
            val on = stamped and (1 shl k) != 0
            if (on) {
                p.color = stampColor
                p.alpha = 215
                c.drawCircle(x + cell / 2f, y + cell / 2f, cell * 0.42f, p)
                p.alpha = 255
            }
            if (n > 0) {
                p.color = if (on) 0xFFFFFFFF.toInt() else Theme.INK
                c.drawText(NUM[n], x + cell / 2f, y + cell / 2f + p.textSize * 0.36f, p)
            }
        }
        if (lines != 0) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = cell * 0.2f
            p.color = Theme.GOLD
            p.alpha = 230
            for (k in BingoLines.LINES.indices) {
                if (lines and (1 shl k) == 0) continue
                val a = BingoLines.LINES[k][0]
                val b = BingoLines.LINES[k][4]
                c.drawLine(
                    l + pad + (a % 5) * (cell + gap) + cell / 2f, t + pad + (a / 5) * (cell + gap) + cell / 2f,
                    l + pad + (b % 5) * (cell + gap) + cell / 2f, t + pad + (b / 5) * (cell + gap) + cell / 2f, p,
                )
            }
            p.alpha = 255
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 작은 판의 칸 중심 x (l, 한 변 s, 칸 k) */
    fun miniCellX(l: Float, s: Float, k: Int): Float {
        val pad = s * 0.05f
        val gap = s * 0.018f
        val cell = (s - pad * 2f - gap * 4f) / 5f
        return l + pad + (k % 5) * (cell + gap) + cell / 2f
    }

    fun miniCellY(t: Float, s: Float, k: Int): Float {
        val pad = s * 0.05f
        val gap = s * 0.018f
        val cell = (s - pad * 2f - gap * 4f) / 5f
        return t + pad + (k / 5) * (cell + gap) + cell / 2f
    }

    /** 빙고 공 (가운데 흰 원 + 글자) */
    fun ball(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, label: String) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, p)
        p.color = BALL
        c.drawCircle(cx, cy, r, p)
        p.color = BALL_LIGHT
        c.drawCircle(cx - r * 0.3f, cy - r * 0.32f, r * 0.32f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy, r * 0.62f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.06f
        p.color = Theme.GOLD_DARK
        c.drawCircle(cx, cy, r * 0.62f, p)
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = r * (if (label.length >= 3) 0.5f else 0.62f)
        p.color = Theme.INK
        c.drawText(label, cx, cy + p.textSize * 0.36f, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 외곽선 있는 굵은 글자 */
    fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFF0A1030.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 누르는 손가락 끝 (동그라미 + 손톱) */
    fun finger(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x66FFFFFF
        c.drawCircle(x, y, r * 1.5f, p)
        p.color = 0xFFFFE0C8.toInt()
        c.drawRoundRect(x - r * 0.7f, y - r * 0.2f, x + r * 0.7f, y + r * 2.6f, r * 0.7f, r * 0.7f, p)
        p.color = 0xFFFFF4EA.toInt()
        c.drawRoundRect(x - r * 0.4f, y - r * 0.05f, x + r * 0.4f, y + r * 0.7f, r * 0.3f, r * 0.3f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.12f
        p.color = 0xFF8A5A26.toInt()
        c.drawRoundRect(x - r * 0.7f, y - r * 0.2f, x + r * 0.7f, y + r * 2.6f, r * 0.7f, r * 0.7f, p)
        p.style = Paint.Style.FILL
    }
}
