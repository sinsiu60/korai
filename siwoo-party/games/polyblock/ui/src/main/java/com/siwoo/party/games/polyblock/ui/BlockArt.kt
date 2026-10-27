package com.siwoo.party.games.polyblock.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.games.polyblock.Orient

/**
 * 블록 한 칸 그리기 (판·조각 목록·규칙 카드·아이콘 공통). 할당 없음.
 * 바깥 어두운 테두리 + 왼쪽 위 밝은 띠 + 가운데 면 + 작은 광택 → 입체 플라스틱 조각처럼.
 */
object BlockArt {

    /** 색을 밝게(f > 1)/어둡게(f < 1). 정수 계산만 (할당 없음) */
    fun shade(c: Int, f: Float): Int {
        val a = c ushr 24
        var r = (c shr 16) and 0xFF
        var g = (c shr 8) and 0xFF
        var b = c and 0xFF
        if (f >= 1f) {
            val k = f - 1f
            r = (r + (255 - r) * k).toInt()
            g = (g + (255 - g) * k).toInt()
            b = (b + (255 - b) * k).toInt()
        } else {
            r = (r * f).toInt()
            g = (g * f).toInt()
            b = (b * f).toInt()
        }
        return (a shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    /** 색에 투명도 곱하기 */
    fun alpha(c: Int, alpha: Int): Int = (((c ushr 24) * alpha / 255) shl 24) or (c and 0xFFFFFF)

    /** (l, t) 에서 한 변 s 인 블록 한 칸. alpha 0..255 */
    fun block(c: Canvas, p: Paint, l: Float, t: Float, s: Float, color: Int, alpha: Int = 255) {
        p.style = Paint.Style.FILL
        val e = s * 0.14f
        p.color = alpha(shade(color, 0.55f), alpha)
        c.drawRect(l, t, l + s, t + s, p)
        p.color = alpha(shade(color, 1.45f), alpha)
        c.drawRect(l, t, l + s - e * 0.5f, t + s - e * 0.5f, p)
        p.color = alpha(color, alpha)
        c.drawRect(l + e, t + e, l + s - e, t + s - e, p)
        p.color = alpha(0x40FFFFFF, alpha)
        c.drawRect(l + e * 1.6f, t + e * 1.6f, l + s * 0.45f, t + s * 0.32f, p)
    }

    /** 조각 하나를 (l, t) 부터 칸 크기 cs 로 */
    fun piece(c: Canvas, p: Paint, o: Orient, l: Float, t: Float, cs: Float, color: Int, alpha: Int = 255) {
        for (k in 0 until o.size) block(c, p, l + o.xs[k] * cs, t + o.ys[k] * cs, cs, color, alpha)
    }

    /** 조각을 (cx, cy) 가운데에 */
    fun pieceCentered(c: Canvas, p: Paint, o: Orient, cx: Float, cy: Float, cs: Float, color: Int, alpha: Int = 255) {
        piece(c, p, o, cx - o.w * cs / 2f, cy - o.h * cs / 2f, cs, color, alpha)
    }

    /** 좌표 쌍 목록 (x, y, x, y …) 으로 블록 여러 개 */
    fun cells(c: Canvas, p: Paint, xy: IntArray, l: Float, t: Float, cs: Float, color: Int, alpha: Int = 255) {
        var i = 0
        while (i + 1 < xy.size) {
            block(c, p, l + xy[i] * cs, t + xy[i + 1] * cs, cs, color, alpha)
            i += 2
        }
    }
}
