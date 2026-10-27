package com.siwoo.party.games.mole

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.mole.ui.MoleActivity

/** 두더지 잡기 대결: 모음집 등록 정보 */
object MoleModule : GameModule {
    override val id = "mole"
    override val name = "두더지 잡기 대결"
    override val tagline = "모두에게 똑같이 튀어나오는 두더지, 누가 더 많이 잡을까?"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "1분"
    override val color = 0xFF8B5A3C.toInt()
    override val activityClass = MoleActivity::class.java

    private const val MOLE = 0xFF8B5A3C.toInt()
    private const val BELLY = 0xFFD6A77F.toInt()
    private const val NOSE = 0xFFFF8FA3.toInt()
    private const val DIRT = 0xFF6B4A2E.toInt()
    private const val HOLE = 0xFF120A06.toInt()
    private const val BOMB = 0xFF23263A.toInt()

    /** 구멍 (cx, 구멍 가운데 y, 반지름 rx) */
    private fun hole(c: Canvas, cx: Float, hy: Float, rx: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFF3E2A1A.toInt()
        c.drawOval(cx - rx * 1.12f, hy - rx * 0.38f, cx + rx * 1.12f, hy + rx * 0.44f, p)
        p.color = HOLE
        c.drawOval(cx - rx, hy - rx * 0.3f, cx + rx, hy + rx * 0.3f, p)
    }

    private fun rim(c: Canvas, cx: Float, hy: Float, rx: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = rx * 0.16f
        p.color = DIRT
        c.drawArc(cx - rx, hy - rx * 0.3f, cx + rx, hy + rx * 0.3f, 0f, 180f, false, p)
        p.style = Paint.Style.FILL
    }

    /** 구멍에서 올라온 두더지 (kind 0 일반 / 1 황금 / 2 폭탄) */
    private fun mole(c: Canvas, cx: Float, hy: Float, rx: Float, kind: Int, p: Paint) {
        hole(c, cx, hy, rx, p)
        val r = rx * 0.72f
        val top = hy - r * 2.1f
        c.save()
        c.clipRect(cx - rx * 1.5f, top - r * 2f, cx + rx * 1.5f, hy)
        p.style = Paint.Style.FILL
        if (kind == 2) {
            val cy = top + r * 1.2f
            p.color = 0xFFFF9F1C.toInt()
            c.drawCircle(cx + r * 0.95f, cy - r * 1.1f, r * 0.22f, p)
            p.color = BOMB
            c.drawCircle(cx, cy, r, p)
            p.color = Theme.RED
            c.drawCircle(cx - r * 0.32f, cy + r * 0.05f, r * 0.15f, p)
            c.drawCircle(cx + r * 0.32f, cy + r * 0.05f, r * 0.15f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.1f
            p.color = 0xFFC9A26B.toInt()
            c.drawLine(cx + r * 0.4f, cy - r * 0.85f, cx + r * 0.9f, cy - r * 1.05f, p)
            p.style = Paint.Style.FILL
        } else {
            p.color = if (kind == 1) Theme.GOLD else MOLE
            c.drawRoundRect(cx - r, top, cx + r, top + r * 3f, r, r, p)
            p.color = if (kind == 1) Theme.GOLD_LIGHT else BELLY
            c.drawOval(cx - r * 0.62f, top + r * 1.05f, cx + r * 0.62f, top + r * 3.2f, p)
            p.color = Theme.INK
            c.drawCircle(cx - r * 0.38f, top + r * 0.62f, r * 0.13f, p)
            c.drawCircle(cx + r * 0.38f, top + r * 0.62f, r * 0.13f, p)
            p.color = NOSE
            c.drawOval(cx - r * 0.2f, top + r * 0.74f, cx + r * 0.2f, top + r * 1.02f, p)
            p.color = 0xFFFFFFFF.toInt()
            c.drawRect(cx - r * 0.13f, top + r * 1.06f, cx + r * 0.13f, top + r * 1.28f, p)
        }
        c.restore()
        rim(c, cx, hy, rx, p)
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

    /** 망치 (머리 가운데 x, y, 크기 s) */
    private fun hammer(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        c.save()
        c.rotate(-30f, x, y)
        p.style = Paint.Style.FILL
        p.color = 0xFFC98B4A.toInt()
        c.drawRoundRect(x - s * 0.08f, y, x + s * 0.08f, y + s * 1.1f, s * 0.06f, s * 0.06f, p)
        p.color = Theme.RED
        c.drawRoundRect(x - s * 0.5f, y - s * 0.25f, x + s * 0.5f, y + s * 0.25f, s * 0.12f, s * 0.12f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(x - s * 0.5f, y - s * 0.25f, x - s * 0.36f, y + s * 0.25f, p)
        c.drawRect(x + s * 0.36f, y - s * 0.25f, x + s * 0.5f, y + s * 0.25f, p)
        c.restore()
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.05f, cy + r * 0.08f, r, paint)
        paint.color = 0xFF3C8A3E.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.94f, paint)
        mole(canvas, cx - r * 0.08f, cy + r * 0.48f, r * 0.55f, 0, paint)
        hammer(canvas, cx + r * 0.42f, cy - r * 0.42f, r * 0.55f, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("튀어나온 두더지를 톡!", "각자 구역에 구멍 6개 (3 × 2). 두더지가 올라오면 재빨리 터치!\n모든 구역에 같은 순서·같은 구멍으로 나와요.") { c, w, h, p ->
            val rx = w * 0.09f
            for (k in 0 until 6) {
                val x = w * (0.22f + 0.28f * (k % 3))
                val y = h * (0.42f + 0.42f * (k / 3))
                if (k == 1 || k == 5) mole(c, x, y, rx, 0, p) else { hole(c, x, y, rx, p); rim(c, x, y, rx, p) }
            }
            hammer(c, w * 0.62f, h * 0.2f, h * 0.18f, p)
        },
        RuleCard("황금 +3, 폭탄 -2", "갈색 두더지 +1, 반짝이는 황금 두더지 +3!\n까만 폭탄을 치면 -2점에 구역이 흔들려요. 한 두더지는 한 번만 잡혀요.") { c, w, h, p ->
            val rx = w * 0.1f
            val y = h * 0.66f
            mole(c, w * 0.2f, y, rx, 0, p)
            mole(c, w * 0.5f, y, rx, 1, p)
            mole(c, w * 0.8f, y, rx, 2, p)
            label(c, "+1", w * 0.2f, h * 0.92f, h * 0.13f, 0xFFFFFFFF.toInt(), p)
            label(c, "+3", w * 0.5f, h * 0.92f, h * 0.13f, Theme.GOLD, p)
            label(c, "-2", w * 0.8f, h * 0.92f, h * 0.13f, Theme.RED, p)
        },
        RuleCard("30초, 점점 빨라져요", "시간이 갈수록 두더지가 더 짧게, 더 자주 나와요.\n30초 뒤 점수 순으로 순위! 같은 점수는 같은 순위.") { c, w, h, p ->
            mole(c, w * 0.3f, h * 0.7f, w * 0.12f, 1, p)
            // 시계
            val cx = w * 0.7f
            val cy = h * 0.45f
            val r = h * 0.26f
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
            label(c, "30초", cx, cy + r + h * 0.16f, h * 0.12f, Theme.TEXT, p)
            p.strokeCap = Paint.Cap.BUTT
        },
    )
}
