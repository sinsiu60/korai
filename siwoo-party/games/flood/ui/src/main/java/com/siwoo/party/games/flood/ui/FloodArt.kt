package com.siwoo.party.games.flood.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.ui.Theme

/**
 * 색 번지기 점령 그림 도우미: 판 색 6가지와 칸·색 버튼 그리기 (판·규칙 카드·아이콘이 같이 쓴다).
 * 모두 할당 없이 주어진 Paint 로 그린다.
 */
object FloodArt {

    /** 판 색 6가지 (딸기·귤·레몬·풀잎·하늘·포도) */
    val PALETTE = intArrayOf(
        0xFFFF5A72.toInt(),
        0xFFFF9D2E.toInt(),
        0xFFFFDA3A.toInt(),
        0xFF3FD989.toInt(),
        0xFF3FA6FF.toInt(),
        0xFFA56CFF.toInt(),
    )
    /** 각 색의 어두운 쪽 (칸 아래 그림자) */
    val DARK = IntArray(PALETTE.size) { Theme.shade(PALETTE[it], 0.62f) }
    /** 각 색의 밝은 쪽 (윗면 반짝임) */
    val LIGHT = IntArray(PALETTE.size) { Theme.shade(PALETTE[it], 1.35f) }

    /** 판 바탕 (칸 사이 틈 색) */
    const val BOARD_BG = 0xFF0E1645.toInt()
    /** 못 고르는 색 버튼 */
    const val DISABLED = 0xFF4A5170.toInt()
    const val DISABLED_DARK = 0xFF2C3150.toInt()

    /** 주인 없는 칸: 틈을 두고 둥근 네모 + 윗면 반짝임 */
    fun tile(c: Canvas, p: Paint, l: Float, t: Float, s: Float, color: Int) {
        val g = s * 0.07f
        val r = s * 0.2f
        p.style = Paint.Style.FILL
        p.color = DARK[color]
        c.drawRoundRect(l + g, t + g, l + s - g, t + s - g, r, r, p)
        p.color = PALETTE[color]
        c.drawRoundRect(l + g, t + g, l + s - g, t + s - g - s * 0.1f, r, r, p)
        p.color = LIGHT[color]
        p.alpha = 110
        c.drawRoundRect(l + g + s * 0.14f, t + g + s * 0.1f, l + s * 0.55f, t + g + s * 0.22f, s * 0.06f, s * 0.06f, p)
        p.alpha = 255
    }

    /** 주인 있는 칸: 틈 없이 꽉 (이웃 칸과 살짝 겹쳐 이음매 없이 한 덩어리로 보임) */
    fun owned(c: Canvas, p: Paint, l: Float, t: Float, s: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = PALETTE[color]
        c.drawRect(l - 0.6f, t - 0.6f, l + s + 0.6f, t + s + 0.6f, p)
    }

    /** 둥근 색 버튼 (입체: 아래 그림자 + 윗면 반짝임) */
    fun button(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, color: Int, enabled: Boolean) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawCircle(cx, cy + r * 0.12f, r, p)
        p.color = if (enabled) DARK[color] else DISABLED_DARK
        c.drawCircle(cx, cy + r * 0.06f, r, p)
        p.color = if (enabled) PALETTE[color] else DISABLED
        c.drawCircle(cx, cy - r * 0.02f, r * 0.92f, p)
        p.color = if (enabled) LIGHT[color] else 0xFF5E6588.toInt()
        p.alpha = if (enabled) 150 else 90
        c.drawOval(cx - r * 0.55f, cy - r * 0.78f, cx + r * 0.2f, cy - r * 0.38f, p)
        p.alpha = 255
    }

    /** 둥근 ✕ (못 고르는 색) */
    fun cross(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.34f
        p.color = 0xAA000000.toInt()
        c.drawLine(cx - r, cy - r, cx + r, cy + r, p)
        c.drawLine(cx + r, cy - r, cx - r, cy + r, p)
        p.strokeWidth = r * 0.2f
        p.color = 0xFFE6E9F5.toInt()
        c.drawLine(cx - r, cy - r, cx + r, cy + r, p)
        c.drawLine(cx + r, cy - r, cx - r, cy + r, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /**
     * 작은 판 그림 (규칙 카드·아이콘용). colors: 칸 색 숫자(줄 순서), owners: '.' 또는 사람 숫자 → Theme 플레이어 색.
     * 같은 사람 칸 사이에는 경계선을 긋지 않아 땅이 한 덩어리로 보인다.
     */
    fun mini(c: Canvas, p: Paint, l: Float, t: Float, s: Float, n: Int, colors: String, owners: String) {
        p.style = Paint.Style.FILL
        p.color = BOARD_BG
        c.drawRoundRect(l - s * 0.25f, t - s * 0.25f, l + s * (n + 0.25f), t + s * (n + 0.25f), s * 0.4f, s * 0.4f, p)
        for (k in 0 until n * n) {
            val x = l + (k % n) * s
            val y = t + (k / n) * s
            val col = colors[k] - '0'
            if (owners[k] == '.') tile(c, p, x, y, s, col) else owned(c, p, x, y, s, col)
        }
        val w = s * 0.12f
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        // 어두운 테두리를 모두 먼저, 그 위에 주인 색
        for (pass in 0..1) for (k in 0 until n * n) {
            val o = owners[k]
            if (o == '.') continue
            p.color = if (pass == 0) Theme.INK else Theme.PLAYER_COLORS[o - '0']
            p.strokeWidth = if (pass == 0) w * 1.7f else w
            val r = k / n
            val cc = k % n
            val x = l + cc * s
            val y = t + r * s
            val i = w * 0.85f
            val up = r == 0 || owners[k - n] != o
            val down = r == n - 1 || owners[k + n] != o
            val left = cc == 0 || owners[k - 1] != o
            val right = cc == n - 1 || owners[k + 1] != o
            val x0 = if (left) x + i else x
            val x1 = if (right) x + s - i else x + s
            val y0 = if (up) y + i else y
            val y1 = if (down) y + s - i else y + s
            if (up) c.drawLine(x0, y + i, x1, y + i, p)
            if (down) c.drawLine(x0, y + s - i, x1, y + s - i, p)
            if (left) c.drawLine(x + i, y0, x + i, y1, p)
            if (right) c.drawLine(x + s - i, y0, x + s - i, y1, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /** 사람 표시 동그라미 (사람 색 + 흰 테 + 첫 글자는 따로) */
    fun badge(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, playerColor: Int) {
        p.style = Paint.Style.FILL
        p.color = Theme.INK
        c.drawCircle(cx, cy + r * 0.08f, r * 1.12f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy, r * 1.06f, p)
        p.color = playerColor
        c.drawCircle(cx, cy, r * 0.88f, p)
    }
}
