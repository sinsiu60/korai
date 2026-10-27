package com.siwoo.party.games.basket.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * 농구 그림 도구 (코트 화면·머리 줄·허브 아이콘·규칙 카드가 같이 씀). 모두 할당 없이 그린다 (메인 스레드 전용 도구 객체).
 */
object BasketArt {
    const val ORANGE = 0xFFF07A1E.toInt()
    const val ORANGE_DARK = 0xFFB4520C.toInt()
    const val SEAM = 0xFF3A1A06.toInt()
    const val RIM = 0xFFFF5A1F.toInt()
    const val RIM_DARK = 0xFFB33A10.toInt()
    const val NET = 0xEEFFFFFF.toInt()
    const val FLAME_RED = 0xFFFF3B1F.toInt()
    const val FLAME_ORANGE = 0xFFFF8A1E.toInt()
    const val FLAME_YELLOW = 0xFFFFE04A.toInt()
    const val WOOD = 0xFFD9A066.toInt()
    const val WOOD_DARK = 0xFFB57A43.toInt()

    private val rect = RectF()

    /** 농구공: 주황 원 + 줄무늬(rot 라디안 회전) + 윤곽 */
    fun ball(c: Canvas, p: Paint, x: Float, y: Float, r: Float, rot: Float) {
        p.style = Paint.Style.FILL
        p.color = ORANGE
        c.drawCircle(x, y, r, p)
        // 아래쪽 그늘
        p.color = 0x33000000
        rect.set(x - r, y - r * 0.2f, x + r, y + r)
        c.drawArc(rect, 0f, 180f, false, p)
        // 하이라이트
        p.color = 0x55FFFFFF
        c.drawCircle(x - r * 0.38f, y - r * 0.4f, r * 0.22f, p)
        // 줄무늬
        c.save()
        c.rotate(rot * 57.29578f, x, y)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, r * 0.09f)
        p.color = SEAM
        c.drawLine(x - r, y, x + r, y, p)
        c.drawLine(x, y - r, x, y + r, p)
        rect.set(x - r * 1.75f, y - r * 0.95f, x - r * 0.25f, y + r * 0.95f)
        c.drawArc(rect, -62f, 124f, false, p)
        rect.set(x + r * 0.25f, y - r * 0.95f, x + r * 1.75f, y + r * 0.95f)
        c.drawArc(rect, 118f, 124f, false, p)
        c.restore()
        p.color = ORANGE_DARK
        p.strokeWidth = maxOf(1f, r * 0.07f)
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    /** 물방울 모양 불꽃 하나 (아래 (x, y) 에서 위로 높이 h, 폭 w) — 겉 빨강·가운데 주황·속 노랑 */
    fun flame(c: Canvas, p: Paint, path: Path, x: Float, y: Float, w: Float, h: Float, alpha: Int = 255) {
        p.style = Paint.Style.FILL
        drop(c, p, path, x, y, w, h, FLAME_RED, alpha)
        drop(c, p, path, x, y - h * 0.04f, w * 0.7f, h * 0.72f, FLAME_ORANGE, alpha)
        drop(c, p, path, x, y - h * 0.08f, w * 0.4f, h * 0.42f, FLAME_YELLOW, alpha)
    }

    private fun drop(c: Canvas, p: Paint, path: Path, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Int) {
        p.color = color
        p.alpha = alpha
        path.reset()
        path.moveTo(x, y - h)
        path.quadTo(x + w * 1.1f, y - h * 0.35f, x, y)
        path.quadTo(x - w * 1.1f, y - h * 0.35f, x, y - h)
        path.close()
        c.drawPath(path, p)
        p.alpha = 255
    }

    /** 불붙은 공 둘레 불꽃 왕관 (가만히 있을 때) — 빛무리 + 공 위쪽으로 불꽃 5개 */
    fun fireCrown(c: Canvas, p: Paint, path: Path, x: Float, y: Float, r: Float, wobble: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x44FF7A1E
        c.drawCircle(x, y - r * 0.2f, r * 1.9f, p)
        p.color = 0x66FFA030
        c.drawCircle(x, y, r * 1.35f, p)
        for (k in 0 until 5) {
            val off = (k - 2) * 0.5f
            // 가운데가 가장 높고 바깥으로 갈수록 낮게, wobble 로 살짝 흔들림
            val base = if (k == 2) 2.8f else 2.2f - kotlin.math.abs(k - 2) * 0.35f
            val h = r * base * (1f + 0.1f * kotlin.math.sin(wobble + k * 1.7f))
            flame(c, p, path, x + off * r, y + r * 0.1f, r * 0.42f, h)
        }
    }

    /** 옆에서 본 골대 (규칙·아이콘용 작은 그림): 림 가운데 (x, y), 림 반폭 rw */
    fun hoop(c: Canvas, p: Paint, x: Float, y: Float, rw: Float) {
        // 보드
        p.style = Paint.Style.FILL
        p.color = 0xFFEFF4FF.toInt()
        rect.set(x + rw * 1.9f, y - rw * 2.4f, x + rw * 2.2f, y + rw * 0.4f)
        c.drawRect(rect, p)
        p.color = 0xFF8A93B8.toInt()
        c.drawRect(x + rw, y - rw * 0.1f, x + rw * 1.9f, y + rw * 0.12f, p)
        // 그물
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, rw * 0.07f)
        p.color = NET
        val bottom = y + rw * 1.5f
        for (k in 0..4) {
            val tx = x - rw + k * rw * 0.5f
            val bx = x - rw * 0.55f + k * rw * 0.275f
            c.drawLine(tx, y, bx, bottom, p)
        }
        c.drawLine(x - rw * 0.8f, y + rw * 0.5f, x + rw * 0.8f, y + rw * 0.5f, p)
        c.drawLine(x - rw * 0.66f, y + rw, x + rw * 0.66f, y + rw, p)
        // 림
        p.strokeWidth = maxOf(1.5f, rw * 0.16f)
        p.color = RIM
        rect.set(x - rw, y - rw * 0.18f, x + rw, y + rw * 0.18f)
        c.drawOval(rect, p)
        p.style = Paint.Style.FILL
    }

    /** 외곽선 있는 굵은 글자 (규칙 그림용) */
    fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF2A1A08.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = android.graphics.Typeface.DEFAULT
    }
}
