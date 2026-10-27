package com.siwoo.party.games.highlow.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme

/**
 * 하이로우 공용 그림 (칩·작은 카드·화살표·"펑!" 별). 할당 없음 — 미리 만든 Path/RectF 와 받은 Paint 만 쓴다
 * (메인 스레드 전용). 허브 아이콘·규칙 카드·점수 판(PotView)이 함께 쓴다.
 */
object HighLowArt {
    /** 칩 색 (단계마다 돌아가며: 1점 흰색, 2점 빨강, 4점 파랑, 8점 초록, 16점 보라, 32점 금색 …) */
    val CHIP_COLORS = intArrayOf(
        0xFFE9ECF2.toInt(), 0xFFE53945.toInt(), 0xFF2F6FE4.toInt(),
        0xFF22A35A.toInt(), 0xFF8E5BD8.toInt(), 0xFFE8A317.toInt(),
    )
    private val CHIP_SIDES = IntArray(CHIP_COLORS.size) { Theme.shade(CHIP_COLORS[it], 0.62f) }
    private val CHIP_INNERS = IntArray(CHIP_COLORS.size) { Theme.shade(CHIP_COLORS[it], 1.2f) }
    private const val MARK_DARK = 0xFF3B4A7A.toInt()

    const val PAPER = 0xFFFFFBF0.toInt()
    const val RED = 0xFFD0202E.toInt()
    const val BLACK = 0xFF161A2C.toInt()
    const val BACK = 0xFF1B2766.toInt()
    const val FELT = 0xFF136B5A.toInt()
    const val FELT_DARK = 0xFF0B4A3E.toInt()
    const val HIGH = 0xFFE5533D.toInt()
    const val LOW = 0xFF2F6FE4.toInt()

    const val SPADE = 0
    const val HEART = 1
    const val DIAMOND = 2
    const val CLUB = 3

    private val path = Path()
    private val r = RectF()

    /**
     * 칩 한 개: (cx, cy) = 윗면 가운데, rx = 가로 반지름(세로 rx × 0.4), 두께 th, level = 색 번호.
     */
    fun chip(c: Canvas, cx: Float, cy: Float, rx: Float, th: Float, level: Int, p: Paint) {
        val k = ((level % CHIP_COLORS.size) + CHIP_COLORS.size) % CHIP_COLORS.size
        val ry = rx * 0.4f
        p.style = Paint.Style.FILL
        p.color = CHIP_SIDES[k]
        r.set(cx - rx, cy - ry + th, cx + rx, cy + ry + th)
        c.drawOval(r, p)
        c.drawRect(cx - rx, cy, cx + rx, cy + th, p)
        p.color = CHIP_COLORS[k]
        r.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = ry * 0.42f
        p.color = if (k == 0) MARK_DARK else 0xFFFFFFFF.toInt()
        r.set(cx - rx * 0.8f, cy - ry * 0.8f, cx + rx * 0.8f, cy + ry * 0.8f)
        var a = 0f
        while (a < 360f) {
            c.drawArc(r, a, 22f, false, p)
            a += 60f
        }
        p.style = Paint.Style.FILL
        p.color = CHIP_INNERS[k]
        r.set(cx - rx * 0.5f, cy - ry * 0.5f, cx + rx * 0.5f, cy + ry * 0.5f)
        c.drawOval(r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = rx * 0.07f
        p.color = 0x66000000
        r.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(r, p)
        p.style = Paint.Style.FILL
    }

    /** 칩 더미: 바닥 base 에서 위로 count 개 (아래부터 level 0, 1, 2 …) */
    fun chipStack(c: Canvas, cx: Float, base: Float, rx: Float, count: Int, p: Paint) {
        val th = rx * 0.26f
        for (k in 0 until count) chip(c, cx, base - k * th, rx, th, k, p)
    }

    /** 무늬 하나 (cx, cy) 가운데, 크기 s */
    fun suit(c: Canvas, kind: Int, cx: Float, cy: Float, s: Float, p: Paint) {
        path.reset()
        when (kind) {
            HEART -> {
                path.moveTo(cx, cy + s * 0.45f)
                path.cubicTo(cx - s * 0.95f, cy - s * 0.12f, cx - s * 0.38f, cy - s * 0.8f, cx, cy - s * 0.25f)
                path.cubicTo(cx + s * 0.38f, cy - s * 0.8f, cx + s * 0.95f, cy - s * 0.12f, cx, cy + s * 0.45f)
            }
            SPADE -> {
                path.moveTo(cx, cy - s * 0.5f)
                path.cubicTo(cx - s * 0.95f, cy + s * 0.08f, cx - s * 0.38f, cy + s * 0.6f, cx, cy + s * 0.15f)
                path.cubicTo(cx + s * 0.38f, cy + s * 0.6f, cx + s * 0.95f, cy + s * 0.08f, cx, cy - s * 0.5f)
                path.moveTo(cx, cy + s * 0.05f)
                path.lineTo(cx - s * 0.2f, cy + s * 0.5f)
                path.lineTo(cx + s * 0.2f, cy + s * 0.5f)
                path.close()
            }
            CLUB -> {
                val rr = s * 0.2f
                path.addCircle(cx, cy - s * 0.22f, rr, Path.Direction.CW)
                path.addCircle(cx - s * 0.22f, cy + s * 0.05f, rr, Path.Direction.CW)
                path.addCircle(cx + s * 0.22f, cy + s * 0.05f, rr, Path.Direction.CW)
                path.moveTo(cx, cy)
                path.lineTo(cx - s * 0.16f, cy + s * 0.5f)
                path.lineTo(cx + s * 0.16f, cy + s * 0.5f)
                path.close()
            }
            else -> {
                path.moveTo(cx, cy - s * 0.5f)
                path.lineTo(cx + s * 0.36f, cy)
                path.lineTo(cx, cy + s * 0.5f)
                path.lineTo(cx - s * 0.36f, cy)
                path.close()
            }
        }
        c.drawPath(path, p)
    }

    /**
     * 작은 카드 그림 (아이콘·규칙 카드용): 가운데 (cx, cy), 폭 w, 회전 rot.
     * faceUp 이면 왼쪽 위 숫자 + 가운데 큰 무늬, 아니면 남색 뒷면.
     */
    fun card(c: Canvas, cx: Float, cy: Float, w: Float, rot: Float, label: String, kind: Int, faceUp: Boolean, p: Paint) {
        val h = w * 1.4f
        val rad = w * 0.09f
        c.save()
        c.translate(cx, cy)
        c.rotate(rot)
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        r.set(-w / 2f + w * 0.04f, -h / 2f + w * 0.07f, w / 2f + w * 0.04f, h / 2f + w * 0.07f)
        c.drawRoundRect(r, rad, rad, p)
        r.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        p.color = if (faceUp) PAPER else BACK
        c.drawRoundRect(r, rad, rad, p)
        if (faceUp) {
            p.color = if (kind == HEART || kind == DIAMOND) RED else BLACK
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = w * 0.32f
            c.drawText(label, -w * 0.27f, -h * 0.27f, p)
            suit(c, kind, -w * 0.27f, -h * 0.13f, w * 0.18f, p)
            suit(c, kind, w * 0.04f, h * 0.12f, w * 0.55f, p)
        } else {
            p.style = Paint.Style.STROKE
            p.strokeWidth = w * 0.04f
            p.color = Theme.GOLD
            r.inset(w * 0.1f, w * 0.1f)
            c.drawRoundRect(r, rad * 0.6f, rad * 0.6f, p)
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = w * 0.55f
            c.drawText("?", 0f, w * 0.2f, p)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.025f
        p.color = 0x33000000
        r.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        c.drawRoundRect(r, rad, rad, p)
        p.style = Paint.Style.FILL
        c.restore()
    }

    /** 굵은 세모 화살표 (위 = up, 아래 = !up): 가운데 (cx, cy), 크기 s */
    fun arrow(c: Canvas, cx: Float, cy: Float, s: Float, up: Boolean, color: Int, p: Paint) {
        val sg = if (up) -1f else 1f
        path.reset()
        path.moveTo(cx, cy + sg * s * 0.5f)
        path.lineTo(cx + s * 0.5f, cy - sg * s * 0.05f)
        path.lineTo(cx + s * 0.2f, cy - sg * s * 0.05f)
        path.lineTo(cx + s * 0.2f, cy - sg * s * 0.5f)
        path.lineTo(cx - s * 0.2f, cy - sg * s * 0.5f)
        path.lineTo(cx - s * 0.2f, cy - sg * s * 0.05f)
        path.lineTo(cx - s * 0.5f, cy - sg * s * 0.05f)
        path.close()
        p.style = Paint.Style.FILL
        p.color = darken(color)
        c.save()
        c.translate(s * 0.04f, s * 0.06f)
        c.drawPath(path, p)
        c.restore()
        p.color = color
        c.drawPath(path, p)
    }

    /** 그림자 색 (각 채널 × 0.55, 알파 유지) — 할당 없음 */
    fun darken(color: Int): Int {
        val a = color ushr 24
        val rr = ((color shr 16) and 0xFF) * 55 / 100
        val gg = ((color shr 8) and 0xFF) * 55 / 100
        val bb = (color and 0xFF) * 55 / 100
        return (a shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    /** 뾰족한 폭발 별 ("펑!" 바탕): 가운데 (cx, cy), 바깥 반지름 ro, 안 반지름 ri, 꼭지 n 개 */
    fun burst(c: Canvas, cx: Float, cy: Float, ro: Float, ri: Float, n: Int, color: Int, p: Paint) {
        path.reset()
        val step = Math.PI / n
        for (k in 0 until n * 2) {
            val rr = if (k % 2 == 0) ro else ri
            val a = -Math.PI / 2 + step * k
            val x = cx + (Math.cos(a) * rr).toFloat()
            val y = cy + (Math.sin(a) * rr).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(path, p)
    }
}
