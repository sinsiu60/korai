package com.siwoo.party.games.baseball.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme

/**
 * 숫자 야구 공통 그림 (아이콘·규칙 카드·기록표가 같이 쓴다). 모두 할당 없이 그린다 (RectF 는 미리 만든 것 재사용).
 */
object BaseballArt {

    /** 스트라이크 (노랑) */
    const val STRIKE = 0xFFFFC93C.toInt()
    /** 볼 (초록) */
    const val BALL = 0xFF2ECC71.toInt()
    /** 아웃 (빨강) */
    const val OUT = 0xFFFF4D5E.toInt()
    /** 공 실밥 */
    const val STITCH = 0xFFE0303A.toInt()
    /** 야구장 잔디 */
    const val FIELD = 0xFF1E8C4E.toInt()

    private val arc = RectF()

    /** 야구공 (cx, cy, 반지름 r). p 의 style·color·strokeWidth 를 바꾼다 */
    fun ball(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, p)
        p.color = 0xFFFDFBF3.toInt()
        c.drawCircle(cx, cy, r, p)
        p.color = 0x22000000
        c.drawCircle(cx + r * 0.18f, cy + r * 0.2f, r * 0.8f, p)
        p.color = 0xFFFDFBF3.toInt()
        c.drawCircle(cx - r * 0.1f, cy - r * 0.12f, r * 0.72f, p)
        // 실밥: 양쪽 곡선 + 짧은 빗금
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = STITCH
        p.strokeWidth = r * 0.07f
        arc.set(cx - r * 1.75f, cy - r, cx - r * 0.35f, cy + r)
        c.drawArc(arc, -50f, 100f, false, p)
        arc.set(cx + r * 0.35f, cy - r, cx + r * 1.75f, cy + r)
        c.drawArc(arc, 130f, 100f, false, p)
        p.strokeWidth = r * 0.06f
        for (k in -2..2) {
            val dy = k * r * 0.3f
            // 왼쪽 곡선 위 x (원 방정식 근사)
            val lx = cx - r * 1.05f + r * 0.7f * kotlin.math.sqrt(1f - (dy / r) * (dy / r) * 0.9f)
            val rx = cx + r * 1.05f - r * 0.7f * kotlin.math.sqrt(1f - (dy / r) * (dy / r) * 0.9f)
            c.drawLine(lx - r * 0.1f, dy + cy - r * 0.06f, lx + r * 0.1f, dy + cy + r * 0.02f, p)
            c.drawLine(rx + r * 0.1f, dy + cy - r * 0.06f, rx - r * 0.1f, dy + cy + r * 0.02f, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /** 판정 동그라미 (S/B/아웃 표시등) — 켜짐/꺼짐 */
    fun lamp(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, on: Boolean, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = if (on) Theme.shade(color, 0.55f) else 0xFF141B45.toInt()
        c.drawCircle(cx, cy + r * 0.12f, r, p)
        p.color = if (on) color else 0xFF2A3570.toInt()
        c.drawCircle(cx, cy, r, p)
        if (on) {
            p.color = 0x66FFFFFF
            c.drawCircle(cx - r * 0.3f, cy - r * 0.32f, r * 0.32f, p)
        }
    }

    /** 숫자 칸 (밝은 종이 + 진한 숫자) */
    fun digitBox(c: Canvas, l: Float, t: Float, s: Float, digit: String, p: Paint, text: Paint) {
        val rr = s * 0.2f
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        c.drawRoundRect(l, t + s * 0.08f, l + s, t + s * 1.08f, rr, rr, p)
        p.color = Theme.PAPER
        c.drawRoundRect(l, t, l + s, t + s, rr, rr, p)
        text.color = Theme.INK
        c.drawText(digit, l + s / 2f, t + s / 2f + text.textSize * 0.36f, text)
    }

    /** 규칙 카드·아이콘용 굵은 글자 Paint 설정 */
    fun boldText(p: Paint, size: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.color = color
    }
}
