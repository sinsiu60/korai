package com.siwoo.party.games.clap369.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme

/** 369 박수 공통 그림: 손뼉 치는 두 손, 입체 버튼. 할당 없음 (Paint·RectF 는 부르는 쪽 것) */
object Clap369Art {
    const val SKIN = 0xFFFFD2A8.toInt()
    const val SKIN_DARK = 0xFFE0A070.toInt()

    /** 손뼉: 가운데로 기운 두 손바닥 + 부딪힘 선 (cx, cy 중심, 크기 s) */
    fun clap(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, burst: Boolean = true) {
        p.style = Paint.Style.FILL
        for (side in 0 until 2) {
            val sign = if (side == 0) -1f else 1f
            c.save()
            c.rotate(sign * 22f, cx + sign * s * 0.14f, cy + s * 0.32f)
            val hx = cx + sign * s * 0.17f
            // 손가락 4개 + 손바닥 + 엄지
            p.color = SKIN_DARK
            c.drawRoundRect(hx - s * 0.17f, cy - s * 0.12f + s * 0.03f, hx + s * 0.17f, cy + s * 0.42f + s * 0.03f, s * 0.12f, s * 0.12f, p)
            p.color = SKIN
            c.drawRoundRect(hx - s * 0.17f, cy - s * 0.12f, hx + s * 0.17f, cy + s * 0.42f, s * 0.12f, s * 0.12f, p)
            for (k in 0 until 4) {
                val fx = hx - s * 0.13f + k * s * 0.087f
                val top = cy - s * (if (k == 1 || k == 2) 0.46f else 0.38f)
                c.drawRoundRect(fx - s * 0.04f, top, fx + s * 0.04f, cy, s * 0.04f, s * 0.04f, p)
            }
            // 엄지 (바깥쪽)
            c.save()
            c.rotate(-sign * 35f, hx - sign * s * 0.15f, cy + s * 0.12f)
            c.drawRoundRect(hx - sign * s * 0.15f - s * 0.05f, cy - s * 0.12f, hx - sign * s * 0.15f + s * 0.05f, cy + s * 0.14f, s * 0.05f, s * 0.05f, p)
            c.restore()
            c.restore()
        }
        if (burst) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = s * 0.055f
            p.color = Theme.GOLD
            c.drawLine(cx, cy - s * 0.62f, cx, cy - s * 0.78f, p)
            c.drawLine(cx - s * 0.3f, cy - s * 0.52f, cx - s * 0.4f, cy - s * 0.64f, p)
            c.drawLine(cx + s * 0.3f, cy - s * 0.52f, cx + s * 0.4f, cy - s * 0.64f, p)
            p.style = Paint.Style.FILL
        }
    }

    /** 입체 버튼: 아래 두께 + 윗면 + 광택. pressed 면 눌린 만큼 내려감, dim 이면 회색 */
    fun slab(c: Canvas, r: RectF, color: Int, pressed: Boolean, dim: Boolean, depth: Float, p: Paint) {
        val rad = minOf(r.width(), r.height()) * 0.2f
        val col = if (dim) 0xFF4A4560.toInt() else color
        val off = if (pressed) depth * 0.8f else 0f
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRoundRect(r.left, r.top + depth * 0.6f, r.right, r.bottom + depth * 0.4f, rad, rad, p)
        p.color = Theme.shade(col, 0.5f)
        c.drawRoundRect(r.left, r.top + depth, r.right, r.bottom, rad, rad, p)
        p.color = col
        c.drawRoundRect(r.left, r.top + off, r.right, r.bottom - depth + off, rad, rad, p)
        p.color = if (dim) 0x18FFFFFF else 0x40FFFFFF
        c.drawRoundRect(r.left + rad * 0.5f, r.top + off + depth * 0.6f, r.right - rad * 0.5f, r.top + off + (r.height() - depth) * 0.36f, rad * 0.5f, rad * 0.5f, p)
    }
}
