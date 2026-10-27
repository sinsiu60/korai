package com.siwoo.party.games.bobber.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * 찌·물고기 그림 (화면·아이콘·규칙 카드가 같이 쓴다). 할당 없이 그린다 (Path·RectF 는 필드).
 * 화면 스레드에서만 부른다.
 */
object BobberArt {

    const val BOBBER_RED = 0xFFE8384F.toInt()
    const val BOBBER_WHITE = 0xFFF7F4EC.toInt()
    const val TIP = 0xFFFFE14D.toInt()
    const val BAND = 0xFF2A2440.toInt()
    const val WATER_TOP = 0xFF4FA6DC.toInt()
    const val WATER_BOTTOM = 0xFF175A93.toInt()
    const val BANK = 0xFF3E8E4F.toInt()
    const val BANK_DARK = 0xFF2A6B3A.toInt()
    const val PAD = 0xFF5DBB63.toInt()
    const val PAD_DARK = 0xFF3D9446.toInt()
    const val ROD = 0xFF8D5A34.toInt()
    const val ROD_DARK = 0xFF5B371C.toInt()

    /** 잡히는 물고기 색 (무작위) */
    val FISH_COLORS = intArrayOf(
        0xFFFF8A3D.toInt(), 0xFFFF6FA8.toInt(), 0xFFFFC93C.toInt(),
        0xFF3FD0C9.toInt(), 0xFFB07CFF.toInt(), 0xFF7BE07B.toInt(),
    )

    private val path = Path()
    private val rect = RectF()

    /**
     * 찌 하나: (cx, cy) = 몸통 가운데, r = 몸통 반폭 기준.
     * 몸통 높이 2.2r (위 빨강·아래 흰색), 위로 안테나 1.4r (끝은 노란 형광), 아래 짧은 꼬리.
     */
    fun bobber(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        // 아래 꼬리
        p.strokeWidth = r * 0.16f
        p.color = BAND
        c.drawLine(cx, cy + r, cx, cy + r * 1.55f, p)
        // 안테나
        p.strokeWidth = r * 0.2f
        p.color = BOBBER_WHITE
        c.drawLine(cx, cy - r, cx, cy - r * 2.1f, p)
        p.color = TIP
        p.strokeWidth = r * 0.26f
        c.drawLine(cx, cy - r * 2.1f, cx, cy - r * 2.6f, p)
        // 몸통: 위 빨강 / 아래 흰색
        p.style = Paint.Style.FILL
        rect.set(cx - r * 0.82f, cy - r * 1.1f, cx + r * 0.82f, cy + r * 1.1f)
        p.color = BOBBER_WHITE
        c.drawArc(rect, 0f, 180f, true, p)
        p.color = BOBBER_RED
        c.drawArc(rect, 180f, 180f, true, p)
        // 가운데 띠
        p.color = BAND
        rect.set(cx - r * 0.84f, cy - r * 0.09f, cx + r * 0.84f, cy + r * 0.09f)
        c.drawRoundRect(rect, r * 0.08f, r * 0.08f, p)
        // 반짝
        p.color = 0x88FFFFFF.toInt()
        rect.set(cx - r * 0.55f, cy - r * 0.86f, cx - r * 0.2f, cy - r * 0.35f)
        c.drawOval(rect, p)
        // 테두리
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.07f
        p.color = 0x66000000
        rect.set(cx - r * 0.82f, cy - r * 1.1f, cx + r * 0.82f, cy + r * 1.1f)
        c.drawOval(rect, p)
        p.style = Paint.Style.FILL
    }

    /** 찌 꼭대기(안테나 끝)의 몸통 가운데 기준 높이 (r 배수) */
    const val TIP_UP = 2.6f

    /**
     * 물고기: (cx, cy) 가운데, len = 꼬리까지 길이, 머리는 +x 쪽. 회전은 부르는 쪽에서.
     */
    fun fish(c: Canvas, p: Paint, cx: Float, cy: Float, len: Float, color: Int) {
        val bl = len * 0.72f // 몸통 길이
        val bh = len * 0.36f // 몸통 높이
        val bx = cx + len * 0.12f // 몸통 가운데
        p.style = Paint.Style.FILL
        // 꼬리
        p.color = darker(color)
        path.reset()
        path.moveTo(bx - bl * 0.38f, cy)
        path.lineTo(cx - len * 0.5f, cy - bh * 0.62f)
        path.quadTo(cx - len * 0.4f, cy, cx - len * 0.5f, cy + bh * 0.62f)
        path.close()
        c.drawPath(path, p)
        // 등지느러미
        path.reset()
        path.moveTo(bx - bl * 0.2f, cy - bh * 0.38f)
        path.quadTo(bx - bl * 0.05f, cy - bh * 0.95f, bx + bl * 0.18f, cy - bh * 0.42f)
        path.close()
        c.drawPath(path, p)
        // 몸통
        p.color = color
        rect.set(bx - bl / 2f, cy - bh / 2f, bx + bl / 2f, cy + bh / 2f)
        c.drawOval(rect, p)
        // 배 (밝게)
        p.color = 0x55FFFFFF
        rect.set(bx - bl * 0.36f, cy + bh * 0.02f, bx + bl * 0.36f, cy + bh * 0.42f)
        c.drawOval(rect, p)
        // 줄무늬
        p.style = Paint.Style.STROKE
        p.strokeWidth = len * 0.03f
        p.color = darker(color)
        rect.set(bx - bl * 0.12f, cy - bh * 0.42f, bx + bl * 0.02f, cy + bh * 0.42f)
        c.drawArc(rect, -70f, 140f, false, p)
        // 아가미
        rect.set(bx + bl * 0.08f, cy - bh * 0.3f, bx + bl * 0.22f, cy + bh * 0.3f)
        c.drawArc(rect, -60f, 120f, false, p)
        p.style = Paint.Style.FILL
        // 눈
        val ex = bx + bl * 0.32f
        val ey = cy - bh * 0.1f
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(ex, ey, bh * 0.15f, p)
        p.color = 0xFF1A2040.toInt()
        c.drawCircle(ex + bh * 0.03f, ey, bh * 0.08f, p)
    }

    /** 색을 조금 어둡게 (할당 없음) */
    fun darker(color: Int): Int {
        val r = ((color shr 16) and 0xFF) * 3 / 4
        val g = ((color shr 8) and 0xFF) * 3 / 4
        val b = (color and 0xFF) * 3 / 4
        return (color and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    /** 물결 동심원 (타원) */
    fun ripple(c: Canvas, p: Paint, cx: Float, cy: Float, rx: Float, alpha: Int, width: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = alpha.coerceIn(0, 255)
        rect.set(cx - rx, cy - rx * 0.3f, cx + rx, cy + rx * 0.3f)
        c.drawOval(rect, p)
        p.alpha = 255
        p.style = Paint.Style.FILL
    }

    /** 연잎 (갈라진 원) */
    fun lilyPad(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = PAD_DARK
        rect.set(cx - r, cy - r * 0.42f + r * 0.06f, cx + r, cy + r * 0.42f + r * 0.06f)
        c.drawArc(rect, 20f, 320f, true, p)
        p.color = PAD
        rect.set(cx - r, cy - r * 0.42f, cx + r, cy + r * 0.42f)
        c.drawArc(rect, 20f, 320f, true, p)
    }

    /** 부들 (물가 풀): (x, 밑동 y), 높이 h */
    fun reed(c: Canvas, p: Paint, x: Float, y: Float, h: Float, lean: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = h * 0.05f
        p.color = 0xFF6FB35A.toInt()
        c.drawLine(x, y, x + lean, y - h, p)
        p.style = Paint.Style.FILL
        p.color = 0xFF7A4A2A.toInt()
        val tx = x + lean * 0.8f
        val ty = y - h * 0.8f
        rect.set(tx - h * 0.06f, ty - h * 0.14f, tx + h * 0.06f, ty + h * 0.14f)
        c.drawRoundRect(rect, h * 0.06f, h * 0.06f, p)
    }
}
