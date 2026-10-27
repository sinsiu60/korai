package com.siwoo.party.games.airhockey.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.airhockey.GameConfig

/**
 * 에어하키 그림 도구 (네온 판·말렛·퍽). 게임 화면·허브 아이콘·규칙 카드가 같이 쓴다.
 * 모두 받은 Paint·RectF 만 쓰고 객체를 만들지 않는다 (onDraw 에서 불러도 됨).
 */
object HockeyArt {
    /** 판 바닥 */
    const val SURFACE = 0xFF0D1846.toInt()
    /** 판 테두리 몸통 */
    const val RAIL_BODY = 0xFF1A2766.toInt()
    /** 네온 테두리 색 */
    const val NEON = 0xFF3FE0FF.toInt()
    /** 가운데 선 색 */
    const val CENTER = 0xFFB04CFF.toInt()
    /** 골문 주머니 (어두운 구멍) */
    const val POCKET = 0xFF03051A.toInt()
    /** 골문 주머니 깊이 (판 단위) */
    const val POCKET_DEPTH = 1.3f
    /** 테두리 몸통 두께 (판 단위) */
    const val RAIL = 0.42f

    /** 색에 알파 입히기 */
    fun alpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    /** 빛나는 선: 넓고 옅은 빛 두 겹 + 선 + 흰 심 */
    fun neonLine(c: Canvas, p: Paint, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = alpha(color, 0x2A)
        p.strokeWidth = width * 4f
        c.drawLine(x1, y1, x2, y2, p)
        p.color = alpha(color, 0x60)
        p.strokeWidth = width * 2.2f
        c.drawLine(x1, y1, x2, y2, p)
        p.color = color
        p.strokeWidth = width
        c.drawLine(x1, y1, x2, y2, p)
        p.color = 0xCCFFFFFF.toInt()
        p.strokeWidth = width * 0.35f
        c.drawLine(x1, y1, x2, y2, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    /** 빛나는 원호 (rect 에 맞춘 원, start°부터 sweep°) */
    fun neonArc(c: Canvas, p: Paint, rect: RectF, start: Float, sweep: Float, color: Int, width: Float) {
        p.style = Paint.Style.STROKE
        p.color = alpha(color, 0x2A)
        p.strokeWidth = width * 4f
        c.drawArc(rect, start, sweep, false, p)
        p.color = alpha(color, 0x60)
        p.strokeWidth = width * 2.2f
        c.drawArc(rect, start, sweep, false, p)
        p.color = color
        p.strokeWidth = width
        c.drawArc(rect, start, sweep, false, p)
    }

    /**
     * 판 전체 (테두리 몸통·골문 주머니·바닥·반쪽 색·공기 구멍·가운데 선·원·골 반원·네온 테두리·골대 기둥).
     * (ox, oy) = 판 왼쪽 위, u = 판 단위 1 의 픽셀. left/right = 두 사람 색. holes = 공기 구멍 점 (작게 그릴 땐 false)
     */
    fun table(c: Canvas, p: Paint, ox: Float, oy: Float, u: Float, left: Int, right: Int, rect: RectF, holes: Boolean) {
        val w = GameConfig.TABLE_W * u
        val h = GameConfig.TABLE_H * u
        val cy = oy + h / 2f
        val gt = oy + GameConfig.GOAL_TOP * u
        val gb = oy + GameConfig.GOAL_BOTTOM * u
        val rail = RAIL * u
        val depth = POCKET_DEPTH * u
        val line = maxOf(u * 0.11f, 1f)
        p.style = Paint.Style.FILL
        // 테두리 몸통 (그림자 + 몸통)
        p.color = 0x66000000
        rect.set(ox - rail, oy - rail + u * 0.25f, ox + w + rail, oy + h + rail + u * 0.25f)
        c.drawRoundRect(rect, rail * 2f, rail * 2f, p)
        p.color = RAIL_BODY
        rect.set(ox - rail, oy - rail, ox + w + rail, oy + h + rail)
        c.drawRoundRect(rect, rail * 2f, rail * 2f, p)
        // 골문 주머니 (판 밖 양 끝) — 주인 색으로 은은히 빛남
        pocket(c, p, ox - depth, gt, ox + rail * 0.5f, gb, left, rect, u)
        pocket(c, p, ox + w - rail * 0.5f, gt, ox + w + depth, gb, right, rect, u)
        // 바닥 + 반쪽 색
        p.style = Paint.Style.FILL
        p.color = SURFACE
        c.drawRect(ox, oy, ox + w, oy + h, p)
        p.color = alpha(left, 0x1E)
        c.drawRect(ox, oy, ox + w / 2f, oy + h, p)
        p.color = alpha(right, 0x1E)
        c.drawRect(ox + w / 2f, oy, ox + w, oy + h, p)
        // 공기 구멍
        if (holes) {
            p.color = 0x24FFFFFF
            val r = maxOf(u * 0.055f, 0.8f)
            var x = ox + u
            while (x < ox + w - u * 0.5f) {
                var y = oy + u
                while (y < oy + h - u * 0.5f) {
                    c.drawCircle(x, y, r, p)
                    y += u
                }
                x += u
            }
        }
        // 가운데 선·원·점
        val mx = ox + w / 2f
        neonLine(c, p, mx, oy + line, mx, oy + h - line, CENTER, line)
        val cr = 3f * u
        rect.set(mx - cr, cy - cr, mx + cr, cy + cr)
        neonArc(c, p, rect, 0f, 360f, CENTER, line)
        p.style = Paint.Style.FILL
        p.color = CENTER
        c.drawCircle(mx, cy, line * 1.6f, p)
        // 골 앞 반원 (주인 색)
        val gr = 4f * u
        rect.set(ox - gr, cy - gr, ox + gr, cy + gr)
        neonArc(c, p, rect, -90f, 180f, left, line)
        rect.set(ox + w - gr, cy - gr, ox + w + gr, cy + gr)
        neonArc(c, p, rect, 90f, 180f, right, line)
        // 네온 테두리 (골문 구멍은 비움)
        val e = line * 1.3f
        neonLine(c, p, ox, oy, ox + w, oy, NEON, e)
        neonLine(c, p, ox, oy + h, ox + w, oy + h, NEON, e)
        neonLine(c, p, ox, oy, ox, gt, NEON, e)
        neonLine(c, p, ox, gb, ox, oy + h, NEON, e)
        neonLine(c, p, ox + w, oy, ox + w, gt, NEON, e)
        neonLine(c, p, ox + w, gb, ox + w, oy + h, NEON, e)
        // 골대 기둥
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        val post = line * 1.5f
        c.drawCircle(ox, gt, post, p)
        c.drawCircle(ox, gb, post, p)
        c.drawCircle(ox + w, gt, post, p)
        c.drawCircle(ox + w, gb, post, p)
    }

    /** 골문 주머니 하나 */
    private fun pocket(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, color: Int, rect: RectF, u: Float) {
        p.style = Paint.Style.FILL
        p.color = alpha(color, 0x50)
        rect.set(l - u * 0.18f, t - u * 0.18f, r + u * 0.18f, b + u * 0.18f)
        c.drawRoundRect(rect, u * 0.6f, u * 0.6f, p)
        p.color = POCKET
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, u * 0.45f, u * 0.45f, p)
        // 그물 무늬
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(u * 0.05f, 0.8f)
        p.color = alpha(color, 0x70)
        var y = t + u * 0.75f
        while (y < b - u * 0.3f) {
            c.drawLine(l + u * 0.15f, y, r - u * 0.15f, y, p)
            y += u * 0.75f
        }
        p.style = Paint.Style.FILL
    }

    /** 말렛 (손잡이 달린 원반): 빛·그림자·몸통·고리·손잡이·광택 */
    fun mallet(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = alpha(color, 0x36)
        c.drawCircle(x, y, r * 1.32f, p)
        p.color = 0x77000000
        c.drawCircle(x + r * 0.1f, y + r * 0.16f, r, p)
        p.color = Theme.shade(color, 0.62f)
        c.drawCircle(x, y, r, p)
        p.color = color
        c.drawCircle(x, y, r * 0.86f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.07f
        p.color = Theme.shade(color, 0.7f)
        c.drawCircle(x, y, r * 0.62f, p)
        // 손잡이
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawCircle(x + r * 0.06f, y + r * 0.1f, r * 0.44f, p)
        p.color = Theme.shade(color, 1.25f)
        c.drawCircle(x, y, r * 0.42f, p)
        p.color = Theme.shade(color, 0.85f)
        c.drawCircle(x, y + r * 0.06f, r * 0.3f, p)
        p.color = 0xAAFFFFFF.toInt()
        c.drawCircle(x - r * 0.14f, y - r * 0.16f, r * 0.12f, p)
        p.color = 0x55FFFFFF
        c.drawCircle(x - r * 0.45f, y - r * 0.45f, r * 0.14f, p)
    }

    /** 퍽 (검은 원반 + 금빛 네온 테두리). glow 0..1 = 빛 세기 (빠를수록 밝게) */
    fun puck(c: Canvas, p: Paint, x: Float, y: Float, r: Float, glow: Float) {
        p.style = Paint.Style.FILL
        p.color = alpha(Theme.GOLD, (0x30 + 0x50 * glow).toInt())
        c.drawCircle(x, y, r * 1.6f, p)
        p.color = 0x77000000
        c.drawCircle(x + r * 0.08f, y + r * 0.14f, r, p)
        p.color = 0xFF14141C.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.2f
        p.color = Theme.GOLD
        c.drawCircle(x, y, r * 0.86f, p)
        p.strokeWidth = r * 0.07f
        p.color = 0xFFFFF4C2.toInt()
        c.drawCircle(x, y, r * 0.5f, p)
        p.style = Paint.Style.FILL
        p.color = 0x66FFFFFF
        c.drawCircle(x - r * 0.35f, y - r * 0.38f, r * 0.14f, p)
    }
}
