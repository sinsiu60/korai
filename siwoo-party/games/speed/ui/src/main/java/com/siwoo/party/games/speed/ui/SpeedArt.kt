package com.siwoo.party.games.speed.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.cards.CardBitmaps
import com.siwoo.party.common.ui.Theme

/**
 * 아이콘·규칙 카드용 작은 카드 그림 (Canvas 도형, 할당 없음).
 * 실제 판은 공통 CardBitmaps 를 쓰고, 여기는 비트맵 없이 그리는 간단한 모양.
 */
object SpeedArt {
    private val rect = RectF()
    private val path = Path()
    private const val PAPER_EDGE = 0xFFB9B2A0.toInt()
    private const val BACK = 0xFF1B2766.toInt()

    /** 앞면 카드: 가운데 (cx, cy), 폭 w, rot 도, 숫자 label, red = 빨간 무늬(◆)/검은 무늬(♣) */
    fun card(c: Canvas, cx: Float, cy: Float, w: Float, rot: Float, label: String, red: Boolean, p: Paint) {
        val h = w * CardBitmaps.RATIO
        c.save()
        c.rotate(rot, cx, cy)
        shadowAndPaper(c, cx, cy, w, h, p)
        p.style = Paint.Style.FILL
        p.color = if (red) CardBitmaps.RED else CardBitmaps.BLACK
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = w * (if (label.length > 1) 0.42f else 0.5f)
        c.drawText(label, cx, cy - h * 0.08f, p)
        if (red) diamond(c, cx, cy + h * 0.24f, w * 0.26f, p) else club(c, cx, cy + h * 0.24f, w * 0.26f, p)
        c.restore()
    }

    /** 뒷면 카드 */
    fun back(c: Canvas, cx: Float, cy: Float, w: Float, rot: Float, p: Paint) {
        val h = w * CardBitmaps.RATIO
        c.save()
        c.rotate(rot, cx, cy)
        shadowAndPaper(c, cx, cy, w, h, p)
        p.style = Paint.Style.FILL
        p.color = BACK
        val m = w * 0.09f
        rect.set(cx - w / 2f + m, cy - h / 2f + m, cx + w / 2f - m, cy + h / 2f - m)
        c.drawRoundRect(rect, w * 0.06f, w * 0.06f, p)
        p.color = Theme.GOLD
        diamond(c, cx, cy, w * 0.3f, p)
        c.restore()
    }

    private fun shadowAndPaper(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, p: Paint) {
        val r = w * CardBitmaps.CORNER * 1.3f
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        rect.set(cx - w / 2f + w * 0.04f, cy - h / 2f + w * 0.07f, cx + w / 2f + w * 0.04f, cy + h / 2f + w * 0.07f)
        c.drawRoundRect(rect, r, r, p)
        p.color = 0xFFFFFDF6.toInt()
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        c.drawRoundRect(rect, r, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.025f
        p.color = PAPER_EDGE
        c.drawRoundRect(rect, r, r, p)
        p.style = Paint.Style.FILL
    }

    /** ◆ (s = 높이) */
    fun diamond(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        path.reset()
        path.moveTo(cx, cy - s / 2f)
        path.lineTo(cx + s * 0.36f, cy)
        path.lineTo(cx, cy + s / 2f)
        path.lineTo(cx - s * 0.36f, cy)
        path.close()
        c.drawPath(path, p)
    }

    /** ♣ (s = 높이) */
    fun club(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val r = s * 0.22f
        c.drawCircle(cx, cy - s * 0.24f, r, p)
        c.drawCircle(cx - s * 0.22f, cy + s * 0.04f, r, p)
        c.drawCircle(cx + s * 0.22f, cy + s * 0.04f, r, p)
        path.reset()
        path.moveTo(cx, cy - s * 0.05f)
        path.lineTo(cx + s * 0.16f, cy + s * 0.5f)
        path.lineTo(cx - s * 0.16f, cy + s * 0.5f)
        path.close()
        c.drawPath(path, p)
    }

    /** 굵은 화살표 (x0,y0) → (x1,y1) */
    fun arrow(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = width
        p.color = color
        val dx = x1 - x0
        val dy = y1 - y0
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val ux = dx / len
        val uy = dy / len
        val hl = width * 2.4f
        c.drawLine(x0, y0, x1 - ux * hl * 0.6f, y1 - uy * hl * 0.6f, p)
        p.style = Paint.Style.FILL
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x1 - ux * hl - uy * hl * 0.6f, y1 - uy * hl + ux * hl * 0.6f)
        path.lineTo(x1 - ux * hl + uy * hl * 0.6f, y1 - uy * hl - ux * hl * 0.6f)
        path.close()
        c.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    /** 입체 버튼 (규칙 그림용) */
    fun button(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, rot: Float, color: Int, label: String, p: Paint) {
        c.save()
        c.rotate(rot, cx, cy)
        val r = h * 0.3f
        val d = h * 0.12f
        p.style = Paint.Style.FILL
        p.color = Theme.shade(color, 0.55f)
        rect.set(cx - w / 2f, cy - h / 2f + d, cx + w / 2f, cy + h / 2f)
        c.drawRoundRect(rect, r, r, p)
        p.color = color
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f - d)
        c.drawRoundRect(rect, r, r, p)
        p.color = Theme.INK
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = h * 0.42f
        c.drawText(label, cx, cy - d / 2f + p.textSize * 0.36f, p)
        c.restore()
    }
}
