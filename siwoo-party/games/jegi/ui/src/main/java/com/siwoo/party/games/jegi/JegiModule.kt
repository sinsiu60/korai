package com.siwoo.party.games.jegi

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.jegi.ui.JegiActivity
import com.siwoo.party.games.jegi.ui.JegiArt
import kotlin.math.PI
import kotlin.math.sin

/** 제기차기: 모음집 등록 정보 (2~4인, 차례대로) */
object JegiModule : GameModule {
    override val id = "jegi"
    override val name = "제기차기"
    override val tagline = "살랑살랑 떨어지는 제기, 발 높이에 올 때 톡!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "5분"
    override val color = 0xFFD9472B.toInt()
    override val activityClass = JegiActivity::class.java

    private val rect = RectF()
    private val KID = Theme.PLAYER_COLORS[0]
    private val KID2 = Theme.PLAYER_COLORS[1]

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.42f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.05f, cy + r * 0.08f, r, paint)
        paint.color = 0xFF7EC8F2.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        // 아래쪽 흙마당
        canvas.save()
        canvas.clipRect(cx - r, cy + r * 0.45f, cx + r, cy + r)
        paint.color = 0xFFE2C48E.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        canvas.restore()
        // 움직임 줄
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * 0.05f
        paint.color = 0xCCFFFFFF.toInt()
        rect.set(cx - r * 0.62f, cy - r * 0.55f, cx + r * 0.1f, cy + r * 0.35f)
        canvas.drawArc(rect, 200f, 70f, false, paint)
        rect.set(cx - r * 0.1f, cy - r * 0.55f, cx + r * 0.62f, cy + r * 0.35f)
        canvas.drawArc(rect, 270f, 70f, false, paint)
        paint.strokeCap = Paint.Cap.BUTT
        // 제기 (크게)
        val u = r / 13f
        JegiArt.jegi(canvas, paint, cx, cy + r * 0.12f, u, 1f, 0.1f, 0.6f, false)
        // 테두리
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.07f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.FILL
    }

    /** 외곽선 있는 굵은 글자 */
    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        p.textAlign = align
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFF0B1340.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 손가락 (톡) */
    private fun finger(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.16f
        p.color = Theme.GOLD
        c.drawCircle(x, y, r * 1.6f, p)
        c.drawCircle(x, y, r * 2.2f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFE0C8.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.2f
        p.color = 0xFF8A5A26.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    /** 하늘 + 흙마당 바탕 (땅 높이 ground) */
    private fun yard(c: Canvas, p: Paint, w: Float, h: Float, ground: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFF9ED5F5.toInt()
        rect.set(0f, 0f, w, ground)
        c.drawRoundRect(rect, h * 0.04f, h * 0.04f, p)
        p.color = 0xFFE2C48E.toInt()
        c.drawRect(0f, ground, w, h, p)
        p.color = 0xFFB98F57.toInt()
        c.drawRect(0f, ground, w, ground + h * 0.012f, p)
    }

    /** 발 높이 판정 띠 (가운데 x, 높이 y, 반너비 hw, 반높이 hh) */
    private fun band(c: Canvas, p: Paint, x: Float, y: Float, hw: Float, hh: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x66FFC93C
        rect.set(x - hw, y - hh, x + hw, y + hh)
        c.drawRoundRect(rect, hh * 0.4f, hh * 0.4f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = hh * 0.12f + 1f
        p.color = Theme.GOLD
        c.drawRoundRect(rect, hh * 0.4f, hh * 0.4f, p)
        p.color = 0xEEFFFFFF.toInt()
        c.drawLine(x - hw * 0.85f, y, x + hw * 0.85f, y, p)
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("떨어지는 제기를 톡!", "제기가 살랑살랑 흔들리며 떨어져요.\n발 높이의 빛나는 띠에 올 때\n제기 아래를 터치하면 발로 차요!\n차면 다시 떠오르고 1개씩 올라가요") { c, w, h, p ->
            val ground = h * 0.9f
            yard(c, p, w, h, ground)
            val u = h * 0.82f / 52f
            val kx = w * 0.34f
            val kickY = ground - 14f * u
            band(c, p, kx, kickY, 10f * u, 6f * u)
            JegiArt.kid(c, p, kx, ground, u, KID, 0.15f, 0.3f, false, false)
            // 흔들리며 떨어지는 길 (점)
            for (k in 0 until 9) {
                val f = k / 8f
                val y = h * 0.08f + f * (kickY - h * 0.2f - h * 0.08f)
                val x = kx + 6f * u + (sin(f * 2.0 * PI * 1.3) * 5.0 * u).toFloat()
                p.color = 0x66000000
                c.drawCircle(x, y, h * 0.016f, p)
                p.color = Theme.TEXT
                c.drawCircle(x, y, h * 0.01f, p)
            }
            JegiArt.jegi(c, p, kx + 4f * u, kickY - h * 0.17f, u, 1f, 0.3f, 0.5f, false)
            // 아래쪽 화살표
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.016f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.GOLD
            val ax = kx + 18f * u
            c.drawLine(ax, kickY - h * 0.3f, ax, kickY - h * 0.08f, p)
            c.drawLine(ax, kickY - h * 0.08f, ax - h * 0.03f, kickY - h * 0.12f, p)
            c.drawLine(ax, kickY - h * 0.08f, ax + h * 0.03f, kickY - h * 0.12f, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
            finger(c, p, w * 0.8f, h * 0.62f, h * 0.06f)
            shout(c, p, "톡!", w * 0.8f, h * 0.32f, h * 0.15f, Theme.GOLD)
        },
        RuleCard("발 옮기기 · 판정", "터치한 가로 자리로 발이 가서 차요.\n점선 위(제기가 높을 때) 터치는 발만 옮겨요.\n너무 빠르거나 늦거나 발이 안 닿으면 실패!\n많이 찰수록 빛나는 띠가 좁아져요") { c, w, h, p ->
            val ground = h * 0.9f
            yard(c, p, w, h, ground)
            val u = h * 0.62f / 52f
            // 왼쪽: 아이가 제기 쪽으로 미끄러짐
            val kx = w * 0.14f
            val jx = w * 0.4f
            // 지나온 발자국
            p.style = Paint.Style.FILL
            p.color = 0x55FFFFFF
            for (k in 0 until 3) {
                rect.set(kx + k * w * 0.06f - h * 0.025f, ground + h * 0.02f, kx + k * w * 0.06f + h * 0.025f, ground + h * 0.05f)
                c.drawOval(rect, p)
            }
            JegiArt.kid(c, p, jx, ground, u, KID2, 1f, 0.7f, false, false)
            JegiArt.jegi(c, p, jx, ground - 15f * u, u, 1f, 0f, 0.2f, false)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.02f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.GOLD
            val ay = ground - 30f * u
            c.drawLine(kx, ay, jx - 10f * u, ay, p)
            c.drawLine(jx - 10f * u, ay, jx - 10f * u - h * 0.04f, ay - h * 0.04f, p)
            c.drawLine(jx - 10f * u, ay, jx - 10f * u - h * 0.04f, ay + h * 0.04f, p)
            // 차기 시작선 (점선)
            p.strokeWidth = h * 0.01f
            p.color = 0xCCFFFFFF.toInt()
            var x = w * 0.03f
            while (x < w * 0.55f) {
                c.drawLine(x, h * 0.3f, x + h * 0.025f, h * 0.3f, p)
                x += h * 0.05f
            }
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
            // 오른쪽: 좁아지는 띠 셋
            val bx = w * 0.8f
            band(c, p, bx, h * 0.24f, w * 0.1f, h * 0.085f)
            band(c, p, bx, h * 0.5f, w * 0.1f, h * 0.055f)
            band(c, p, bx, h * 0.74f, w * 0.1f, h * 0.03f)
            shout(c, p, "0개", w * 0.62f, h * 0.27f, h * 0.085f, Theme.TEXT, Paint.Align.CENTER)
            shout(c, p, "10개", w * 0.62f, h * 0.53f, h * 0.085f, Theme.TEXT, Paint.Align.CENTER)
            shout(c, p, "25개", w * 0.62f, h * 0.77f, h * 0.085f, Theme.GOLD, Paint.Align.CENTER)
        },
        RuleCard("2번 도전 · 최고 기록", "한 사람씩 돌아가며 차요 (모두 2번씩).\n떨어뜨리면 그 도전은 끝!\n두 기록 중 더 많이 찬 기록으로 순위\n(같으면 다른 한 번의 기록으로 가려요)") { c, w, h, p ->
            // 왼쪽: 땅에 떨어진 제기
            p.style = Paint.Style.FILL
            p.color = 0xFFE2C48E.toInt()
            rect.set(w * 0.03f, h * 0.62f, w * 0.33f, h * 0.84f)
            c.drawOval(rect, p)
            val u = h / 40f
            JegiArt.jegi(c, p, w * 0.18f, h * 0.71f, u, 1f, 0f, 0f, true)
            shout(c, p, "툭…", w * 0.18f, h * 0.38f, h * 0.13f, 0xFFFF8A8A.toInt())
            // 오른쪽: 기록판
            val l = w * 0.4f
            val r = w * 0.97f
            p.color = Theme.CARD
            rect.set(l, h * 0.1f, r, h * 0.9f)
            c.drawRoundRect(rect, h * 0.05f, h * 0.05f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = Theme.GOLD
            c.drawRoundRect(rect, h * 0.05f, h * 0.05f, p)
            p.style = Paint.Style.FILL
            val rows = 2
            for (i in 0 until rows) {
                val y = h * (0.36f + i * 0.32f)
                val col = if (i == 0) KID else KID2
                p.color = col
                c.drawCircle(l + h * 0.1f, y - h * 0.035f, h * 0.06f, p)
                val a1 = if (i == 0) "7" else "9"
                val a2 = if (i == 0) "12" else "4"
                shout(c, p, a1, l + w * 0.17f, y, h * 0.11f, Theme.TEXT_SUB)
                shout(c, p, a2, l + w * 0.27f, y, h * 0.11f, Theme.TEXT_SUB)
                shout(c, p, "→", l + w * 0.36f, y, h * 0.1f, Theme.TEXT)
                shout(c, p, if (i == 0) "12" else "9", l + w * 0.47f, y, h * 0.13f, Theme.GOLD)
            }
            // 1등 왕관
            p.color = Theme.GOLD
            val crx = l + h * 0.1f
            val cry = h * 0.2f
            val s = h * 0.05f
            c.drawRect(crx - s, cry, crx + s, cry + s * 0.5f, p)
            c.drawCircle(crx - s, cry - s * 0.5f, s * 0.3f, p)
            c.drawCircle(crx, cry - s * 0.8f, s * 0.3f, p)
            c.drawCircle(crx + s, cry - s * 0.5f, s * 0.3f, p)
            c.drawRect(crx - s, cry - s * 0.3f, crx + s, cry, p)
        },
    )
}
