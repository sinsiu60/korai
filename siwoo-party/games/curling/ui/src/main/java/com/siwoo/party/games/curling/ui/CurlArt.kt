package com.siwoo.party.games.curling.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.siwoo.party.games.curling.GameConfig

/**
 * 컬링 그림 도구 (도형만, 셰이더 없음 — 매번 그려도 할당 없음. 메인 스레드에서만).
 * 시트 화면·점수판 하우스 확대·규칙 카드·아이콘이 같이 쓴다.
 */
object CurlArt {

    const val ICE = 0xFFEAF4FB.toInt()
    const val ICE_DARK = 0xFFCFE2F0.toInt()
    const val RING_BLUE = 0xFF2F6BD8.toInt()
    const val RING_RED = 0xFFE53950.toInt()
    const val LINE = 0xFF33415E.toInt()
    const val HOG = 0xFFD8394C.toInt()
    const val GRANITE = 0xFF8E939C.toInt()
    const val GRANITE_DARK = 0xFF5A5F69.toInt()
    const val GRANITE_LIGHT = 0xFFC3C7CE.toInt()
    const val BROOM = 0xFF3A2A1A.toInt()
    const val BRUSH = 0xFFFFD34D.toInt()

    private val rect = RectF()
    private val arrow = Path()

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

    /** 색에 투명도 곱하기 */
    fun alpha(c: Int, a: Int): Int = (((c ushr 24) * a / 255) shl 24) or (c and 0xFFFFFF)

    /**
     * 스톤 하나 (위에서 본 모습): 그림자 → 화강암 → 편 색 윗면 → 광택 → 손잡이(angle 라디안).
     * alpha 0..255 (사라지는 중).
     */
    fun stone(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int, angle: Float, alpha: Int = 255, shadow: Boolean = true) {
        p.style = Paint.Style.FILL
        if (shadow) {
            p.color = alpha(0x55000000, alpha)
            c.drawCircle(x + r * 0.1f, y + r * 0.18f, r * 1.02f, p)
        }
        p.color = alpha(GRANITE_DARK, alpha)
        c.drawCircle(x, y, r, p)
        p.color = alpha(GRANITE, alpha)
        c.drawCircle(x - r * 0.05f, y - r * 0.06f, r * 0.9f, p)
        p.color = alpha(GRANITE_LIGHT, alpha)
        c.drawCircle(x - r * 0.3f, y - r * 0.32f, r * 0.22f, p)
        // 편 색 윗면 (손잡이 덮개)
        p.color = alpha(shade(color, 0.55f), alpha)
        c.drawCircle(x, y, r * 0.66f, p)
        p.color = alpha(color, alpha)
        c.drawCircle(x - r * 0.03f, y - r * 0.04f, r * 0.6f, p)
        p.color = alpha(0x66FFFFFF, alpha)
        rect.set(x - r * 0.42f, y - r * 0.46f, x - r * 0.04f, y - r * 0.26f)
        c.drawOval(rect, p)
        // 손잡이
        c.save()
        c.rotate(Math.toDegrees(angle.toDouble()).toFloat(), x, y)
        p.color = alpha(0xFF20242C.toInt(), alpha)
        rect.set(x - r * 0.62f, y - r * 0.15f, x + r * 0.62f, y + r * 0.15f)
        c.drawRoundRect(rect, r * 0.15f, r * 0.15f, p)
        p.color = alpha(0xFF5B6475.toInt(), alpha)
        rect.set(x - r * 0.55f, y - r * 0.1f, x + r * 0.55f, y - r * 0.02f)
        c.drawRoundRect(rect, r * 0.05f, r * 0.05f, p)
        c.restore()
    }

    /** 하우스 고리 (cx, cy 중심, 바깥 반지름 r): 파랑 → 흰색 → 빨강 → 흰 버튼 */
    fun house(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        val rr = GameConfig.RING_R
        val k = r / GameConfig.HOUSE_R
        p.style = Paint.Style.FILL
        p.color = RING_BLUE
        c.drawCircle(cx, cy, rr[0] * k, p)
        p.color = 0xFFF7FBFF.toInt()
        c.drawCircle(cx, cy, rr[1] * k, p)
        p.color = RING_RED
        c.drawCircle(cx, cy, rr[2] * k, p)
        p.color = 0xFFF7FBFF.toInt()
        c.drawCircle(cx, cy, rr[3] * k, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.012f
        p.color = 0x5520305A
        for (v in rr) c.drawCircle(cx, cy, v * k, p)
        p.style = Paint.Style.FILL
    }

    /** 얼음 판 조각 (규칙 카드·아이콘용) */
    fun ice(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRect(l + (b - t) * 0.02f, t + (b - t) * 0.04f, r + (b - t) * 0.02f, b + (b - t) * 0.04f, p)
        p.color = ICE
        c.drawRect(l, t, r, b, p)
        p.color = ICE_DARK
        c.drawRect(l, t, r, t + (b - t) * 0.06f, p)
        c.drawRect(l, b - (b - t) * 0.06f, r, b, p)
    }

    /**
     * 빗자루 (솔 중심 x, y, 솔 길이 len, 자루 기울기 tilt: 솔에서 자루 끝까지 (dx, dy) 비율).
     */
    fun broom(c: Canvas, p: Paint, x: Float, y: Float, len: Float, tiltX: Float, tiltY: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = len * 0.12f
        p.color = BROOM
        c.drawLine(x, y, x + tiltX * len * 2.2f, y + tiltY * len * 2.2f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        p.color = 0xFF222222.toInt()
        rect.set(x - len * 0.2f, y - len * 0.5f, x + len * 0.2f, y + len * 0.5f)
        c.drawRoundRect(rect, len * 0.12f, len * 0.12f, p)
        p.color = BRUSH
        rect.set(x - len * 0.13f, y - len * 0.44f, x + len * 0.13f, y + len * 0.44f)
        c.drawRoundRect(rect, len * 0.08f, len * 0.08f, p)
    }

    /**
     * 휘는 방향 화살표: (cx, cy) 를 지나 오른쪽(+x)으로 가다 dir 쪽(+1 아래, −1 위)으로 휘는 곡선 + 화살촉.
     * len = 가로 길이, w = 선 굵기.
     */
    fun curlArrow(c: Canvas, p: Paint, x0: Float, y0: Float, len: Float, bend: Float, dir: Int, w: Float, color: Int) {
        val s = if (dir < 0) -1f else 1f
        val x1 = x0 + len
        val y1 = y0 + bend * s
        arrow.reset()
        arrow.moveTo(x0, y0)
        arrow.quadTo(x0 + len * 0.7f, y0, x1, y1)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w
        p.color = color
        c.drawPath(arrow, p)
        p.strokeCap = Paint.Cap.BUTT
        // 화살촉: 끝 방향 (x1 − 0.7len, y1 − y0 쪽)
        val dx = len * 0.3f
        val dy = bend * s
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        val ux = dx / d
        val uy = dy / d
        val h = w * 2.6f
        arrow.reset()
        arrow.moveTo(x1 + ux * h * 0.6f, y1 + uy * h * 0.6f)
        arrow.lineTo(x1 - ux * h * 0.6f - uy * h * 0.75f, y1 - uy * h * 0.6f + ux * h * 0.75f)
        arrow.lineTo(x1 - ux * h * 0.6f + uy * h * 0.75f, y1 - uy * h * 0.6f - ux * h * 0.75f)
        arrow.close()
        p.style = Paint.Style.FILL
        c.drawPath(arrow, p)
    }

    /**
     * 회전 화살표 아이콘 (컬 버튼): (cx, cy) 중심 반지름 r 의 3/4 원호 + 화살촉. dir +1 = 시계(↻), −1 = 반시계(↺).
     */
    fun spinIcon(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, dir: Int, color: Int) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.28f
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        rect.set(cx - r, cy - r, cx + r, cy + r)
        // 시계: 30° 에서 시계 방향 270° → 끝 = 위 오른쪽(−60°). 반시계는 좌우 대칭 (끝 = 위 왼쪽)
        if (dir > 0) c.drawArc(rect, 30f, 270f, false, p) else c.drawArc(rect, 150f, -270f, false, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        // 화살촉: 호의 끝에서 진행 방향(접선)으로
        val s = if (dir > 0) 1f else -1f
        val ax = cx + s * r * 0.5f
        val ay = cy - r * 0.866f
        val tx = s * 0.866f
        val ty = 0.5f
        val h = r * 0.62f
        arrow.reset()
        arrow.moveTo(ax + tx * h * 0.85f, ay + ty * h * 0.85f)
        arrow.lineTo(ax - tx * h * 0.3f - ty * h * 0.7f, ay - ty * h * 0.3f + tx * h * 0.7f)
        arrow.lineTo(ax - tx * h * 0.3f + ty * h * 0.7f, ay - ty * h * 0.3f - tx * h * 0.7f)
        arrow.close()
        c.drawPath(arrow, p)
    }
}
