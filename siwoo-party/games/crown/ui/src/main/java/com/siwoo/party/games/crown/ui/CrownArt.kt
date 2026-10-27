package com.siwoo.party.games.crown.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.Theme

/** 왕관·버튼 그림 (화면·규칙 카드·아이콘 공용). 할당 없음: Path·Paint 는 부르는 쪽이 넘긴다 */
object CrownArt {
    private const val SHADOW = 0x55000000
    private const val OUTLINE = 0xFF5A3B00.toInt()
    private const val JEWEL_BLUE = 0xFF3D9BFF.toInt()
    private const val JEWEL_GREEN = 0xFF2ECC71.toInt()

    /** 왕관 높이 / 폭 */
    const val RATIO = 0.74f

    /** 가운데 (cx, cy), 폭 w 인 왕관 */
    fun crown(c: Canvas, cx: Float, cy: Float, w: Float, p: Paint, path: Path) {
        val h = w * RATIO
        val l = cx - w / 2f
        val top = cy - h / 2f
        val bottom = cy + h / 2f
        // 그림자
        p.style = Paint.Style.FILL
        p.color = SHADOW
        outline(path, l + w * 0.05f, top + h * 0.07f, w, h)
        c.drawPath(path, p)
        // 몸통
        outline(path, l, top, w, h)
        p.color = Theme.GOLD
        c.drawPath(path, p)
        // 띠
        p.color = Theme.GOLD_DARK
        c.drawRect(l, bottom - h * 0.22f, l + w, bottom, p)
        // 밝은 면
        p.color = Theme.GOLD_LIGHT
        path.reset()
        path.moveTo(l + w * 0.08f, bottom - h * 0.26f)
        path.lineTo(l + w * 0.08f, top + h * 0.45f)
        path.lineTo(l + w * 0.28f, top + h * 0.68f)
        path.lineTo(l + w * 0.28f, bottom - h * 0.26f)
        path.close()
        c.drawPath(path, p)
        // 테두리
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = maxOf(w * 0.035f, 1f)
        p.color = OUTLINE
        outline(path, l, top, w, h)
        c.drawPath(path, p)
        c.drawLine(l, bottom - h * 0.22f, l + w, bottom - h * 0.22f, p)
        p.style = Paint.Style.FILL
        // 보석
        val jy = bottom - h * 0.11f
        p.color = Theme.RED
        c.drawCircle(cx, jy, w * 0.07f, p)
        p.color = JEWEL_BLUE
        c.drawCircle(l + w * 0.22f, jy, w * 0.05f, p)
        p.color = JEWEL_GREEN
        c.drawCircle(l + w * 0.78f, jy, w * 0.05f, p)
        // 뾰족 끝 구슬
        p.color = Theme.GOLD_LIGHT
        c.drawCircle(l, top + h * 0.2f, w * 0.065f, p)
        c.drawCircle(cx, top, w * 0.075f, p)
        c.drawCircle(l + w, top + h * 0.2f, w * 0.065f, p)
        p.color = Theme.RED
        c.drawCircle(cx, top + h * 0.42f, w * 0.06f, p)
    }

    private fun outline(path: Path, l: Float, top: Float, w: Float, h: Float) {
        val bottom = top + h
        path.reset()
        path.moveTo(l, bottom)
        path.lineTo(l, top + h * 0.2f)
        path.lineTo(l + w * 0.27f, top + h * 0.55f)
        path.lineTo(l + w * 0.5f, top)
        path.lineTo(l + w * 0.73f, top + h * 0.55f)
        path.lineTo(l + w, top + h * 0.2f)
        path.lineTo(l + w, bottom)
        path.close()
    }

    /** 단순한 입체 둥근 버튼 (규칙 카드·아이콘용). pressed 면 눌려 내려감 */
    fun button(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, pressed: Boolean, p: Paint) {
        val depth = r * 0.16f
        p.style = Paint.Style.FILL
        p.color = SHADOW
        c.drawCircle(cx + r * 0.05f, cy + depth + r * 0.08f, r, p)
        p.color = Theme.shade(color, 0.5f)
        c.drawCircle(cx, cy + depth, r, p)
        val fy = if (pressed) cy + depth * 0.8f else cy
        p.color = color
        c.drawCircle(cx, fy, r, p)
        p.color = 0x40FFFFFF
        c.drawCircle(cx - r * 0.25f, fy - r * 0.3f, r * 0.42f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.06f
        p.color = 0x55FFFFFF
        c.drawCircle(cx, fy, r * 0.82f, p)
        p.style = Paint.Style.FILL
    }
}
