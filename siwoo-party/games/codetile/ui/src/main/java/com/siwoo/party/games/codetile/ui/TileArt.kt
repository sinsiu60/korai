package com.siwoo.party.games.codetile.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme

/**
 * 숫자 타일 그림 (공개 탁자·내 타일 비밀 카드·숫자 패드·규칙 카드·아이콘이 함께 쓴다).
 * 검정 타일: 짙은 먹색 면 + 흰 숫자 / 흰 타일: 상아색 면 + 남색 숫자.
 * 숨은 면(number = -1)은 색만 보이고 가운데에 마름모 무늬 — 숫자는 그리지 않는다.
 * 6·9 는 밑줄로 구분. 모든 Paint·Path·글자는 미리 만들어 둔다 → 그리기 할당 없음 (메인 스레드 전용).
 */
object TileArt {

    /** 0~11 숫자 글자 (미리 만듦) */
    val NUM: Array<String> = Array(12) { it.toString() }

    const val BLACK_FACE = 0xFF2A2C36.toInt()
    const val BLACK_SIDE = 0xFF101117.toInt()
    const val BLACK_EDGE = 0xFF4A4D5C.toInt()
    const val WHITE_FACE = 0xFFF6F1E4.toInt()
    const val WHITE_SIDE = 0xFFB8AF98.toInt()
    const val WHITE_EDGE = 0xFFFFFFFF.toInt()
    const val WHITE_INK = 0xFF1A2040.toInt()

    /** 너비 / 높이 */
    const val ASPECT = 0.66f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val path = Path()

    /**
     * 타일 한 장. (l, t) 왼쪽 위, w×h (옆면 두께 포함).
     * @param number 공개 숫자, -1 이면 숨은 면
     * @param alpha 0~255
     */
    fun draw(c: Canvas, l: Float, t: Float, w: Float, h: Float, black: Boolean, number: Int, alpha: Int = 255) {
        if (w <= 0f || h <= 0f) return
        val r = w * 0.16f
        val depth = h * 0.07f
        val faceB = t + h - depth
        // 그림자
        fill.color = 0x55000000
        fill.alpha = 0x55 * alpha / 255
        c.drawRoundRect(l + w * 0.04f, t + depth * 1.4f, l + w + w * 0.04f, t + h + depth * 0.5f, r, r, fill)
        // 옆면 (두께)
        fill.color = if (black) BLACK_SIDE else WHITE_SIDE
        fill.alpha = alpha
        c.drawRoundRect(l, t + depth, l + w, t + h, r, r, fill)
        // 앞면
        fill.color = if (black) BLACK_FACE else WHITE_FACE
        fill.alpha = alpha
        c.drawRoundRect(l, t, l + w, faceB, r, r, fill)
        // 위쪽 광택
        fill.color = if (black) 0x22FFFFFF else 0x66FFFFFF
        fill.alpha = (if (black) 0x22 else 0x66) * alpha / 255
        c.drawRoundRect(l + w * 0.1f, t + h * 0.05f, l + w * 0.9f, t + h * 0.2f, r * 0.6f, r * 0.6f, fill)
        // 테두리
        stroke.strokeWidth = maxOf(1f, w * 0.035f)
        stroke.color = if (black) BLACK_EDGE else WHITE_SIDE
        stroke.alpha = alpha
        c.drawRoundRect(l, t, l + w, faceB, r, r, stroke)
        val cx = l + w / 2f
        val cy = t + (faceB - t) / 2f
        if (number < 0) {
            // 숨은 면: 마름모 무늬
            val s = w * 0.2f
            path.reset()
            path.moveTo(cx, cy - s * 1.4f)
            path.lineTo(cx + s, cy)
            path.lineTo(cx, cy + s * 1.4f)
            path.lineTo(cx - s, cy)
            path.close()
            fill.color = if (black) 0xFF3C3F4D.toInt() else 0xFFE4DCC8.toInt()
            fill.alpha = alpha
            c.drawPath(path, fill)
            stroke.color = if (black) 0xFF575B6E.toInt() else 0xFFCFC5AC.toInt()
            stroke.alpha = alpha
            c.drawPath(path, stroke)
            fill.color = if (black) 0xFF575B6E.toInt() else 0xFFCFC5AC.toInt()
            fill.alpha = alpha
            c.drawCircle(cx, cy, s * 0.28f, fill)
            return
        }
        val n = number.coerceIn(0, 11)
        text.color = if (black) Theme.TEXT else WHITE_INK
        text.alpha = alpha
        text.textSize = (faceB - t) * (if (n >= 10) 0.5f else 0.62f)
        val base = cy - (text.descent() + text.ascent()) / 2f
        c.drawText(NUM[n], cx, base, text)
        if (n == 6 || n == 9) {
            fill.color = text.color
            fill.alpha = alpha
            val uw = w * 0.22f
            val uy = base + (faceB - t) * 0.07f
            c.drawRect(cx - uw, uy, cx + uw, uy + maxOf(1f, h * 0.025f), fill)
        }
    }

    /** 고른 타일·넣을 자리 강조용 금색 테두리 */
    fun outline(c: Canvas, l: Float, t: Float, w: Float, h: Float, color: Int, widthPx: Float) {
        val r = w * 0.2f
        val o = widthPx * 0.8f
        stroke.strokeWidth = widthPx
        stroke.color = color
        c.drawRoundRect(l - o, t - o, l + w + o, t + h + o, r, r, stroke)
    }

    /** 외곽선 있는 굵은 글자 (규칙 카드·아이콘) */
    fun shout(c: Canvas, p: Paint, s: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFF0A1030.toInt()
        c.drawText(s, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(s, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 누르는 손가락 (끝이 (x, y)) */
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

    /** 말풍선 (가운데 (x, y), 꼬리는 아래 왼쪽) */
    fun bubble(c: Canvas, p: Paint, x: Float, y: Float, w: Float, h: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRoundRect(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f, h * 0.4f, h * 0.4f, p)
        path.reset()
        path.moveTo(x - w * 0.18f, y + h * 0.4f)
        path.lineTo(x - w * 0.34f, y + h * 0.85f)
        path.lineTo(x - w * 0.02f, y + h * 0.4f)
        path.close()
        c.drawPath(path, p)
    }
}
