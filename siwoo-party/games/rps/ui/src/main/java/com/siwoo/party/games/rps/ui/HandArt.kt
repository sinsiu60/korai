package com.siwoo.party.games.rps.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.siwoo.party.games.rps.Hand

/**
 * 손 모양 그림 (가위·바위·보) — 둥근 막대 조각을 겹쳐 만든 만화풍 손. 할당 없음.
 * (cx, cy) 가운데, 한 변 s 상자 안에 그린다. 손가락이 위쪽.
 */
object HandArt {
    const val SKIN = 0xFFFFD2A8.toInt()
    const val SKIN_LINE = 0xFFB0683E.toInt()
    const val CUFF = 0xFFE9ECF7.toInt()
    const val CUFF_LINE = 0xFF8C96C0.toInt()

    private val rect = RectF()
    /** 그림 전체를 아래로 살짝 (손가락이 위로 길어서) */
    private const val OY = 0.05f

    fun draw(c: Canvas, hand: Hand, cx: Float, cy: Float, s: Float, p: Paint, alpha: Int = 255) {
        when (hand) {
            Hand.ROCK -> rock(c, cx, cy, s, p, alpha)
            Hand.PAPER -> paper(c, cx, cy, s, p, alpha)
            else -> scissors(c, cx, cy, s, p, alpha)
        }
    }

    /** 둥근 조각 하나: 채우고 테두리 (단위 좌표 l,t,r,b, 반지름 rad) */
    private fun part(c: Canvas, cx: Float, cy: Float, s: Float, l: Float, t: Float, r: Float, b: Float, rad: Float, p: Paint, fill: Int, line: Int, alpha: Int) {
        rect.set(cx + l * s, cy + (t + OY) * s, cx + r * s, cy + (b + OY) * s)
        p.style = Paint.Style.FILL
        p.color = fill
        p.alpha = alpha
        c.drawRoundRect(rect, rad * s, rad * s, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.028f
        p.color = line
        p.alpha = alpha
        c.drawRoundRect(rect, rad * s, rad * s, p)
        p.style = Paint.Style.FILL
    }

    /** 회전한 조각 (pivot 단위 좌표 px,py 중심으로 deg) */
    private fun rotPart(c: Canvas, cx: Float, cy: Float, s: Float, deg: Float, px: Float, py: Float,
                        l: Float, t: Float, r: Float, b: Float, rad: Float, p: Paint, alpha: Int) {
        c.save()
        c.rotate(deg, cx + px * s, cy + (py + OY) * s)
        part(c, cx, cy, s, l, t, r, b, rad, p, SKIN, SKIN_LINE, alpha)
        c.restore()
    }

    private fun cuff(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, alpha: Int) {
        part(c, cx, cy, s, -0.25f, 0.33f, 0.25f, 0.47f, 0.05f, p, CUFF, CUFF_LINE, alpha)
    }

    private fun rock(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, alpha: Int) {
        cuff(c, cx, cy, s, p, alpha)
        // 접은 손가락 4개 (마디)
        for (k in 0 until 4) {
            val x = -0.27f + k * 0.18f
            part(c, cx, cy, s, x - 0.09f, -0.36f, x + 0.09f, 0.02f, 0.09f, p, SKIN, SKIN_LINE, alpha)
        }
        // 손바닥
        part(c, cx, cy, s, -0.36f, -0.14f, 0.36f, 0.36f, 0.16f, p, SKIN, SKIN_LINE, alpha)
        // 엄지 (앞을 가로질러)
        rotPart(c, cx, cy, s, -8f, -0.13f, 0.1f, -0.38f, 0.01f, 0.12f, 0.17f, 0.08f, p, alpha)
    }

    private fun paper(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, alpha: Int) {
        cuff(c, cx, cy, s, p, alpha)
        // 펼친 손가락 4개
        rotPart(c, cx, cy, s, -12f, -0.22f, 0.05f, -0.29f, -0.48f, -0.15f, 0.04f, 0.07f, p, alpha)
        rotPart(c, cx, cy, s, -4f, -0.075f, 0.05f, -0.145f, -0.56f, -0.005f, 0.04f, 0.07f, p, alpha)
        rotPart(c, cx, cy, s, 4f, 0.075f, 0.05f, 0.005f, -0.54f, 0.145f, 0.04f, 0.07f, p, alpha)
        rotPart(c, cx, cy, s, 12f, 0.22f, 0.05f, 0.15f, -0.44f, 0.29f, 0.04f, 0.07f, p, alpha)
        // 손바닥
        part(c, cx, cy, s, -0.3f, -0.1f, 0.3f, 0.36f, 0.15f, p, SKIN, SKIN_LINE, alpha)
        // 엄지 (왼쪽으로 벌림)
        rotPart(c, cx, cy, s, -38f, -0.24f, 0.12f, -0.5f, 0.04f, -0.2f, 0.18f, 0.07f, p, alpha)
    }

    private fun scissors(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, alpha: Int) {
        cuff(c, cx, cy, s, p, alpha)
        // 검지·중지 V
        rotPart(c, cx, cy, s, -16f, -0.1f, 0.02f, -0.17f, -0.58f, -0.03f, 0.04f, 0.07f, p, alpha)
        rotPart(c, cx, cy, s, 16f, 0.08f, 0.02f, 0.01f, -0.58f, 0.15f, 0.04f, 0.07f, p, alpha)
        // 접은 약지·새끼
        part(c, cx, cy, s, 0.1f, -0.25f, 0.24f, 0.02f, 0.07f, p, SKIN, SKIN_LINE, alpha)
        part(c, cx, cy, s, 0.21f, -0.19f, 0.35f, 0.04f, 0.07f, p, SKIN, SKIN_LINE, alpha)
        // 손바닥
        part(c, cx, cy, s, -0.3f, -0.1f, 0.34f, 0.36f, 0.15f, p, SKIN, SKIN_LINE, alpha)
        // 엄지 (앞을 가로질러)
        rotPart(c, cx, cy, s, -10f, -0.1f, 0.12f, -0.34f, 0.03f, 0.14f, 0.19f, 0.08f, p, alpha)
    }
}
