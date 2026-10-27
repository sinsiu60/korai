package com.siwoo.party.games.rhythm

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.rhythm.ui.RhythmActivity

/** 리듬 노트: 모음집 등록 정보 */
object RhythmModule : GameModule {
    override val id = "rhythm"
    override val name = "리듬 노트"
    override val tagline = "똑같이 떨어지는 노트, 박자에 맞춰 톡! 누가 더 정확할까?"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "2분"
    override val color = 0xFFB04CFF.toInt()
    override val activityClass = RhythmActivity::class.java

    private const val LANE = 0xFF0E1640.toInt()
    private const val PINK = 0xFFFF5FA8.toInt()
    private const val SKY = 0xFF3FC8FF.toInt()

    /** 둥근 북 노트 (가운데 x, y, 반지름 r, 빛 0..1) */
    private fun note(c: Canvas, x: Float, y: Float, r: Float, color: Int, glow: Float, p: Paint) {
        p.style = Paint.Style.FILL
        if (glow > 0f) {
            p.color = Theme.GOLD_LIGHT
            for (k in 3 downTo 1) {
                p.alpha = (glow * 34f * (4 - k)).toInt()
                c.drawCircle(x, y, r * (1f + 0.22f * k * glow), p)
            }
            p.alpha = 255
        }
        p.color = 0x66000000
        c.drawCircle(x, y + r * 0.14f, r, p)
        p.color = color
        c.drawCircle(x, y, r, p)
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = (80 + 150 * glow).toInt()
        c.drawCircle(x, y, r * 0.58f, p)
        p.alpha = 130
        c.drawCircle(x - r * 0.3f, y - r * 0.34f, r * 0.17f, p)
        p.alpha = 255
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.12f
        p.color = if (glow > 0.35f) Theme.GOLD else 0xFFFFFFFF.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    /** 세로 레인 + 금색 판정선 + 과녁 고리 (레인 가운데 x, 위 y0, 아래 y1, 폭 w, 판정선 jy) */
    private fun lane(c: Canvas, x: Float, y0: Float, y1: Float, w: Float, jy: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = LANE
        c.drawRoundRect(x - w / 2f, y0, x + w / 2f, y1, w * 0.12f, w * 0.12f, p)
        p.color = color
        p.alpha = 45
        c.drawRoundRect(x - w / 2f, y0, x + w / 2f, y1, w * 0.12f, w * 0.12f, p)
        p.alpha = 255
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = Theme.GOLD
        p.alpha = 70
        p.strokeWidth = w * 0.12f
        c.drawLine(x - w * 0.46f, jy, x + w * 0.46f, jy, p)
        p.alpha = 255
        p.strokeWidth = w * 0.035f
        c.drawLine(x - w * 0.46f, jy, x + w * 0.46f, jy, p)
        p.color = Theme.GOLD_LIGHT
        p.alpha = 200
        p.strokeWidth = w * 0.025f
        c.drawCircle(x, jy, w * 0.3f, p)
        p.alpha = 255
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    private fun label(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.textSize = size
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.color = color
        c.drawText(text, x, y, p)
        p.typeface = null
        p.textAlign = Paint.Align.LEFT
    }

    /** 8분음표 (머리 가운데 x, y, 크기 s) */
    private fun eighth(c: Canvas, x: Float, y: Float, s: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = color
        c.save()
        c.rotate(-20f, x, y)
        c.drawOval(x - s * 0.28f, y - s * 0.19f, x + s * 0.28f, y + s * 0.19f, p)
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.11f
        val sx = x + s * 0.24f
        c.drawLine(sx, y - s * 0.05f, sx, y - s * 0.95f, p)
        c.drawLine(sx, y - s * 0.95f, sx + s * 0.34f, y - s * 0.55f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /** 빛줄기 고리 (맞힌 순간) */
    private fun burst(c: Canvas, x: Float, y: Float, r: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        p.strokeWidth = r * 0.08f
        c.drawCircle(x, y, r, p)
        for (k in 0 until 8) {
            val a = k * Math.PI / 4
            val cs = Math.cos(a).toFloat()
            val sn = Math.sin(a).toFloat()
            c.drawLine(x + cs * r * 1.15f, y + sn * r * 1.15f, x + cs * r * 1.45f, y + sn * r * 1.45f, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.05f, cy + r * 0.08f, r, paint)
        paint.color = 0xFF3A1C6E.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        // 레인 + 판정선
        val lw = r * 0.62f
        val jy = cy + r * 0.42f
        canvas.save()
        canvas.clipRect(cx - r, cy - r, cx + r, cy + r)
        lane(canvas, cx - r * 0.12f, cy - r * 0.95f, cy + r * 0.95f, lw, jy, PINK, paint)
        canvas.restore()
        note(canvas, cx - r * 0.12f, cy - r * 0.32f, lw * 0.3f, PINK, 0f, paint)
        note(canvas, cx - r * 0.12f, jy, lw * 0.3f, SKY, 1f, paint)
        eighth(canvas, cx + r * 0.5f, cy - r * 0.1f, r * 0.48f, Theme.GOLD, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.94f, paint)
        paint.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("금빛 선에서 톡!", "각자 구역에 레인 하나, 모두 같은 노트가 떨어져요.\n금빛 판정선에 닿는 순간 내 구역 아무 곳이나 톡!") { c, w, h, p ->
            val x = w * 0.32f
            val jy = h * 0.72f
            lane(c, x, h * 0.06f, h * 0.96f, w * 0.2f, jy, PINK, p)
            note(c, x, h * 0.22f, w * 0.06f, PINK, 0f, p)
            note(c, x, h * 0.46f, w * 0.06f, PINK, 0.3f, p)
            note(c, x, jy, w * 0.06f, PINK, 1f, p)
            // 손가락
            val fx = w * 0.68f
            val fy = h * 0.6f
            p.style = Paint.Style.FILL
            p.color = 0xFFFFD7B0.toInt()
            c.drawRoundRect(fx - w * 0.04f, fy - h * 0.05f, fx + w * 0.04f, fy + h * 0.3f, w * 0.04f, w * 0.04f, p)
            p.color = 0xFFFFE9D6.toInt()
            c.drawOval(fx - w * 0.025f, fy - h * 0.03f, fx + w * 0.025f, fy + h * 0.04f, p)
            burst(c, fx, fy - h * 0.1f, h * 0.08f, Theme.GOLD, p)
            eighth(c, w * 0.88f, h * 0.3f, h * 0.2f, Theme.GOLD_LIGHT, p)
        },
        RuleCard("퍼펙트 3 · 굿 1 · 미스 0", "딱 맞으면 퍼펙트 3점, 조금 어긋나면 굿 1점.\n너무 이르거나 놓치면 미스! 연속으로 맞히면 콤보가 쌓여요.") { c, w, h, p ->
            val y = h * 0.45f
            val r = h * 0.15f
            note(c, w * 0.2f, y, r, SKY, 1f, p)
            burst(c, w * 0.2f, y, r * 1.2f, Theme.GOLD, p)
            note(c, w * 0.5f, y, r, SKY, 0.4f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.12f
            p.color = Theme.GREEN
            c.drawCircle(w * 0.5f, y, r * 1.35f, p)
            note(c, w * 0.8f, y, r, Theme.GRAY, 0f, p)
            label(c, "퍼펙트 3", w * 0.2f, h * 0.88f, h * 0.12f, Theme.GOLD, p)
            label(c, "굿 1", w * 0.5f, h * 0.88f, h * 0.12f, Theme.GREEN, p)
            label(c, "미스 0", w * 0.8f, h * 0.88f, h * 0.12f, Theme.RED, p)
        },
        RuleCard("60초, 점점 빨라져요", "똑딱 박자음에 맞춰 노트가 점점 빨라져요.\n60초 뒤 점수 순! 같은 점수면 최대 콤보가 큰 사람이 위.") { c, w, h, p ->
            // 박자 막대: 점점 촘촘
            p.style = Paint.Style.FILL
            var x = w * 0.06f
            var gap = w * 0.085f
            var k = 0
            while (x < w * 0.5f) {
                p.color = if (k % 4 == 0) Theme.GOLD else Theme.TEXT_SUB
                val bh = if (k % 4 == 0) h * 0.32f else h * 0.2f
                c.drawRoundRect(x, h * 0.62f - bh, x + w * 0.022f, h * 0.62f, w * 0.01f, w * 0.01f, p)
                x += gap
                gap *= 0.84f
                k++
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.025f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.GOLD_LIGHT
            c.drawLine(w * 0.06f, h * 0.75f, w * 0.52f, h * 0.75f, p)
            c.drawLine(w * 0.52f, h * 0.75f, w * 0.48f, h * 0.71f, p)
            c.drawLine(w * 0.52f, h * 0.75f, w * 0.48f, h * 0.79f, p)
            p.strokeCap = Paint.Cap.BUTT
            label(c, "점점 빠르게", w * 0.29f, h * 0.92f, h * 0.1f, Theme.TEXT_SUB, p)
            // 시계
            val cx = w * 0.79f
            val cy = h * 0.42f
            val r = minOf(h * 0.24f, w * 0.15f)
            p.style = Paint.Style.FILL
            p.color = 0xFF22337A.toInt()
            c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.12f
            p.color = Theme.GOLD
            c.drawCircle(cx, cy, r, p)
            p.strokeCap = Paint.Cap.ROUND
            p.color = 0xFFFFFFFF.toInt()
            c.drawLine(cx, cy, cx, cy - r * 0.65f, p)
            c.drawLine(cx, cy, cx + r * 0.45f, cy + r * 0.2f, p)
            p.strokeCap = Paint.Cap.BUTT
            label(c, "60초", cx, cy + r + h * 0.16f, h * 0.12f, Theme.TEXT, p)
        },
    )
}
