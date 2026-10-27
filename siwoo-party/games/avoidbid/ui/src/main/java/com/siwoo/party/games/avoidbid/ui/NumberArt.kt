package com.siwoo.party.games.avoidbid.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.avoidbid.GameConfig

/**
 * 숫자 카드·칩 그림 (트럼프가 아닌 3~35 숫자 카드).
 * 카드 테두리 색은 숫자에 따라 초록(낮음, 좋음) → 노랑 → 빨강(높음, 나쁨).
 * 할당 없음 — 미리 만든 RectF·문자열만 쓴다 (메인 스레드 전용).
 */
object NumberArt {
    /** 숫자 글자 (0~35, 미리 만들어 둠) */
    val NUM: Array<String> = Array(GameConfig.HIGHEST_CARD + 1) { it.toString() }

    /** 숫자별 테두리 색 */
    private val COLORS = IntArray(GameConfig.HIGHEST_CARD + 1)
    /** 숫자별 글자 색 (테두리 색을 어둡게) */
    private val INKS = IntArray(GameConfig.HIGHEST_CARD + 1)

    init {
        val hsv = FloatArray(3)
        for (v in COLORS.indices) {
            val f = ((v - GameConfig.LOWEST_CARD).toFloat() / (GameConfig.HIGHEST_CARD - GameConfig.LOWEST_CARD)).coerceIn(0f, 1f)
            hsv[0] = 135f * (1f - f) // 초록 → 빨강
            hsv[1] = 0.78f
            hsv[2] = 0.92f
            COLORS[v] = Color.HSVToColor(hsv)
            INKS[v] = Theme.shade(COLORS[v], 0.5f)
        }
    }

    private const val DIM_PAPER = 0xFFD8D1C0.toInt()
    private const val DIM_INK = 0xFF8C8676.toInt()
    private const val BACK = 0xFF1E2C70.toInt()
    private const val BACK_DARK = 0xFF121B4D.toInt()
    private const val SHADOW = 0x55000000

    fun colorOf(v: Int): Int = COLORS[v.coerceIn(0, COLORS.size - 1)]

    private val r = RectF()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    /**
     * 카드 앞면. (l, t) 왼쪽 위, w×h.
     * dim = 묶음 안이라 점수에 안 들어가는 카드(흐리게), head = 묶음의 가장 낮은 숫자(금색 테두리 강조),
     * numX = 숫자 가운데 가로 위치(0..1, 겹친 카드는 보이는 오른쪽에), detail = 큰 카드(모서리 숫자·무늬).
     */
    fun drawFace(c: Canvas, l: Float, t: Float, w: Float, h: Float, v: Int, p: Paint,
                 dim: Boolean = false, head: Boolean = false, numX: Float = 0.5f, detail: Boolean = false, shadow: Boolean = true) {
        val rad = w * 0.13f
        p.style = Paint.Style.FILL
        if (shadow) {
            p.color = SHADOW
            r.set(l + w * 0.04f, t + w * 0.06f, l + w * 1.04f, t + h + w * 0.06f)
            c.drawRoundRect(r, rad, rad, p)
        }
        // 색 테두리
        p.color = if (dim) Theme.shade(COLORS[v], 0.75f) else COLORS[v]
        r.set(l, t, l + w, t + h)
        c.drawRoundRect(r, rad, rad, p)
        // 종이
        val bw = w * 0.075f
        p.color = if (dim) DIM_PAPER else Theme.PAPER
        r.set(l + bw, t + bw, l + w - bw, t + h - bw)
        c.drawRoundRect(r, rad * 0.7f, rad * 0.7f, p)
        val ink = if (dim) DIM_INK else INKS[v]
        if (detail) {
            // 가운데 둥근 무늬 + 왼쪽 위 작은 숫자
            p.color = COLORS[v]
            p.alpha = 46
            c.drawCircle(l + w / 2f, t + h * 0.5f, w * 0.36f, p)
            p.alpha = 255
            text.color = ink
            text.textSize = h * 0.11f
            c.drawText(NUM[v], l + bw + text.textSize * 0.75f, t + bw + text.textSize * 1.0f, text)
        }
        text.color = ink
        text.textSize = if (detail) h * 0.36f else if (numX != 0.5f) w * 0.46f else w * 0.56f
        c.drawText(NUM[v], l + w * numX, t + h * 0.5f + text.textSize * 0.36f, text)
        if (head) {
            // 점수에 들어가는 카드: 금색 테두리
            p.style = Paint.Style.STROKE
            p.strokeWidth = w * 0.07f
            p.color = Theme.GOLD
            r.set(l - p.strokeWidth * 0.3f, t - p.strokeWidth * 0.3f, l + w + p.strokeWidth * 0.3f, t + h + p.strokeWidth * 0.3f)
            c.drawRoundRect(r, rad * 1.1f, rad * 1.1f, p)
            p.style = Paint.Style.FILL
        }
    }

    /** 카드 뒷면 (남색 + 금색 테두리 + 마름모 무늬) */
    fun drawBack(c: Canvas, l: Float, t: Float, w: Float, h: Float, p: Paint, shadow: Boolean = true) {
        val rad = w * 0.13f
        p.style = Paint.Style.FILL
        if (shadow) {
            p.color = SHADOW
            r.set(l + w * 0.04f, t + w * 0.06f, l + w * 1.04f, t + h + w * 0.06f)
            c.drawRoundRect(r, rad, rad, p)
        }
        p.color = Theme.GOLD_DARK
        r.set(l, t, l + w, t + h)
        c.drawRoundRect(r, rad, rad, p)
        val bw = w * 0.07f
        p.color = BACK
        r.set(l + bw, t + bw, l + w - bw, t + h - bw)
        c.drawRoundRect(r, rad * 0.7f, rad * 0.7f, p)
        // 마름모 무늬
        val cx = l + w / 2f
        val cy = t + h / 2f
        c.save()
        c.rotate(45f, cx, cy)
        p.color = BACK_DARK
        val s = w * 0.3f
        r.set(cx - s, cy - s, cx + s, cy + s)
        c.drawRoundRect(r, s * 0.2f, s * 0.2f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.04f
        p.color = Theme.GOLD
        val s2 = w * 0.2f
        r.set(cx - s2, cy - s2, cx + s2, cy + s2)
        c.drawRect(r, p)
        c.restore()
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD_LIGHT
        c.drawCircle(cx, cy, w * 0.06f, p)
    }

    /** 금색 칩 하나 (비스듬히 본 납작한 동전). (cx, cy) = 윗면 가운데, rx = 가로 반지름 */
    fun drawCoin(c: Canvas, cx: Float, cy: Float, rx: Float, p: Paint) {
        val ry = rx * 0.45f
        val th = rx * 0.32f
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD_DARK
        r.set(cx - rx, cy - ry + th, cx + rx, cy + ry + th)
        c.drawOval(r, p)
        c.drawRect(cx - rx, cy, cx + rx, cy + th, p)
        p.color = Theme.GOLD
        r.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = rx * 0.12f
        p.color = Theme.GOLD_LIGHT
        r.set(cx - rx * 0.62f, cy - ry * 0.62f, cx + rx * 0.62f, cy + ry * 0.62f)
        c.drawOval(r, p)
        p.strokeWidth = rx * 0.08f
        p.color = 0x66000000
        r.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(r, p)
        p.style = Paint.Style.FILL
    }

    /** 칩 쌓기: (cx, bottom) 에서 위로 n 개 (동전 두께 = rx × 0.32) */
    fun drawStack(c: Canvas, cx: Float, bottom: Float, rx: Float, n: Int, p: Paint) {
        val step = rx * 0.32f
        val top0 = bottom - rx * 0.45f - step
        for (k in 0 until n) drawCoin(c, cx, top0 - k * step, rx, p)
    }
}
