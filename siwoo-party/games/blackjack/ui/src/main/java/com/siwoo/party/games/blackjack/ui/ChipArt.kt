package com.siwoo.party.games.blackjack.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme

/**
 * 카지노 칩 그림 (비스듬히 본 납작한 원통: 옆면 + 윗면 + 흰 테두리 무늬 + 안쪽 원).
 * 할당 없음 — 받은 Paint 와 미리 만든 RectF 만 쓴다 (메인 스레드 전용).
 * 액면: 50 금색, 10 파랑, 5 빨강, 1 흰색. 베팅 금액은 큰 액면부터 나눠 쌓는다.
 */
object ChipArt {
    /** 큰 것부터 */
    val DENOMS = intArrayOf(50, 10, 5, 1)
    val COLORS = intArrayOf(0xFFE8A317.toInt(), 0xFF2F6FE4.toInt(), 0xFFE53945.toInt(), 0xFFE9ECF2.toInt())

    private val SIDES = IntArray(COLORS.size) { Theme.shade(COLORS[it], 0.62f) }
    private val INNERS = IntArray(COLORS.size) { Theme.shade(COLORS[it], 1.22f) }
    private const val MARK_DARK = 0xFF3B4A7A.toInt()
    private val r = RectF()

    fun colorOf(denomIndex: Int): Int = COLORS[denomIndex.coerceIn(0, COLORS.size - 1)]

    /**
     * 칩 한 개: (cx, cy) = 윗면 가운데, rx = 가로 반지름, 세로는 rx × 0.4, 두께 th, denom = DENOMS 번호.
     * paint 는 스타일·색을 바꿔 쓴다.
     */
    fun draw(c: Canvas, cx: Float, cy: Float, rx: Float, th: Float, denom: Int, paint: Paint) {
        val k = denom.coerceIn(0, COLORS.size - 1)
        val ry = rx * 0.4f
        val sw = rx * 0.07f
        paint.style = Paint.Style.FILL
        // 옆면
        paint.color = SIDES[k]
        r.set(cx - rx, cy - ry + th, cx + rx, cy + ry + th)
        c.drawOval(r, paint)
        c.drawRect(cx - rx, cy, cx + rx, cy + th, paint)
        // 윗면
        paint.color = COLORS[k]
        r.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(r, paint)
        // 테두리 무늬 (흰 조각 6개)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ry * 0.42f
        paint.color = if (k == 3) MARK_DARK else 0xFFFFFFFF.toInt()
        r.set(cx - rx * 0.8f, cy - ry * 0.8f, cx + rx * 0.8f, cy + ry * 0.8f)
        var a = 0f
        while (a < 360f) {
            c.drawArc(r, a, 22f, false, paint)
            a += 60f
        }
        // 안쪽 원
        paint.style = Paint.Style.FILL
        paint.color = INNERS[k]
        r.set(cx - rx * 0.5f, cy - ry * 0.5f, cx + rx * 0.5f, cy + ry * 0.5f)
        c.drawOval(r, paint)
        // 외곽선
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sw
        paint.color = 0x66000000
        r.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(r, paint)
        paint.style = Paint.Style.FILL
    }

    /**
     * 금액 → 액면별 개수 (DENOMS 순서). out 은 크기 4.
     * 예) 40 = 10×4, 75 = 50 + 10×2 + 5, 7 = 5 + 1×2
     */
    fun breakdown(amount: Int, out: IntArray) {
        var left = amount.coerceAtLeast(0)
        for (k in DENOMS.indices) {
            out[k] = left / DENOMS[k]
            left -= out[k] * DENOMS[k]
        }
    }
}
