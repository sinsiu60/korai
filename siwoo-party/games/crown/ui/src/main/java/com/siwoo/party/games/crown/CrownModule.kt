package com.siwoo.party.games.crown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.crown.ui.CrownActivity
import com.siwoo.party.games.crown.ui.CrownArt

/** 왕관 쟁탈: 모음집 등록 정보 */
object CrownModule : GameModule {
    override val id = "crown"
    override val name = "왕관 쟁탈"
    override val tagline = "꾹 누르고 버텨라, 빼앗고 지켜라!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "1분"
    override val color = 0xFFE0A21B.toInt()
    override val activityClass = CrownActivity::class.java

    // 그림용 (그릴 때 만들지 않게 미리)
    private val path = Path()
    private val oval = RectF()
    private val BAR_VALS = intArrayOf(180, 240, 95)
    private val BAR_TEXT = arrayOf("180점", "240점", "95점")
    private val BAR_RANK = arrayOf("2위", "1위", "3위")
    private val PER_SEC = "+${GameConfig.POINTS_PER_SEC}점/초"
    private val COOLDOWN = "${GameConfig.STEAL_COOLDOWN_MS / 1000}초 쿨다운"

    private fun label(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.14f
        p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFF0A0F2E.toInt()
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.isFakeBoldText = true
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.isFakeBoldText = false
        p.textAlign = Paint.Align.LEFT
    }

    /** 점선 포물선 (x0,y0) → (x1,y1), 위로 bulge 만큼 휨 + 끝 화살촉 */
    private fun arc(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, bulge: Float, width: Float) {
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD_LIGHT
        val qx = (x0 + x1) / 2f
        val qy = (y0 + y1) / 2f - bulge
        for (k in 1 until 10) {
            val t = k / 10f
            val u = 1f - t
            val x = u * u * x0 + 2 * u * t * qx + t * t * x1
            val y = u * u * y0 + 2 * u * t * qy + t * t * y1
            c.drawCircle(x, y, width * (0.5f + 0.5f * t), p)
        }
    }

    /** 가운데 시계 (남은 시간 호) */
    private fun clock(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, frac: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xEE0A0F2E.toInt()
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.14f
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0x33FFFFFF
        c.drawCircle(cx, cy, r * 0.88f, p)
        p.color = Theme.GOLD
        oval.set(cx - r * 0.88f, cy - r * 0.88f, cx + r * 0.88f, cy + r * 0.88f)
        c.drawArc(oval, -90f, 360f * frac, false, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /** 쿨다운 빨간 고리 */
    private fun cooldownRing(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, frac: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.12f
        p.color = 0x66000000
        c.drawCircle(cx, cy, r, p)
        p.color = Theme.RED
        oval.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(oval, -90f, 360f * frac, false, p)
        p.style = Paint.Style.FILL
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF2B3C8F.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 큰 빨간 버튼 + 그 위로 날아오는 왕관
        CrownArt.button(canvas, cx, cy + r * 0.3f, r * 0.42f, Theme.RED, true, paint)
        arc(canvas, paint, cx - r * 0.62f, cy + r * 0.05f, cx - r * 0.12f, cy - r * 0.42f, r * 0.12f, r * 0.04f)
        canvas.save()
        canvas.rotate(12f, cx + r * 0.08f, cy - r * 0.32f)
        CrownArt.crown(canvas, cx + r * 0.08f, cy - r * 0.32f, r * 0.72f, paint, path)
        canvas.restore()
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard(
            "왕관 주인만 점수",
            "구역 어디든 누르면 내 큰 버튼!\n왕관을 가진 사람이 누르고 있는 동안 1초에 ${GameConfig.POINTS_PER_SEC}점.\n다른 사람은 누르고 있어도 점수가 없어요.",
        ) { c, w, h, p ->
            // 왼쪽: 왕관 주인 (금빛) / 오른쪽: 그냥 누르는 사람
            val y = h * 0.62f
            p.style = Paint.Style.FILL
            p.color = 0x55FFC93C
            c.drawCircle(w * 0.3f, y, h * 0.3f, p)
            CrownArt.button(c, w * 0.3f, y, h * 0.24f, Theme.PLAYER_COLORS[0], true, p)
            CrownArt.crown(c, w * 0.3f, y - h * 0.02f, h * 0.3f, p, path)
            label(c, p, PER_SEC, w * 0.3f, h * 0.2f, h * 0.12f, Theme.GOLD)
            CrownArt.button(c, w * 0.72f, y, h * 0.24f, Theme.PLAYER_COLORS[1], true, p)
            label(c, p, "꾹!", w * 0.72f, y + h * 0.06f, h * 0.12f, Theme.TEXT)
            label(c, p, "0점", w * 0.72f, h * 0.2f, h * 0.12f, Theme.TEXT_SUB)
        },
        RuleCard(
            "새로 눌러서 빼앗기",
            "다른 사람이 자기 버튼을 새로 누르면 왕관이 날아가요!\n빼앗은 사람은 ${GameConfig.STEAL_COOLDOWN_MS / 1000}초 동안 다시 못 빼앗아요\n(버튼 둘레 빨간 고리). 동시에 누르면 먼저 닿은 순서대로.",
        ) { c, w, h, p ->
            val y = h * 0.62f
            CrownArt.button(c, w * 0.2f, y, h * 0.17f, Theme.PLAYER_COLORS[0], true, p)
            CrownArt.button(c, w * 0.8f, y, h * 0.17f, Theme.PLAYER_COLORS[1], true, p)
            cooldownRing(c, p, w * 0.8f, y + h * 0.015f, h * 0.215f, 0.7f)
            // 왼쪽 버튼에서 오른쪽 버튼으로 날아가는 왕관 (꼬리 점선)
            arc(c, p, w * 0.22f, y - h * 0.2f, w * 0.5f, h * 0.24f, h * 0.1f, h * 0.012f)
            c.save()
            c.rotate(18f, w * 0.6f, h * 0.24f)
            CrownArt.crown(c, w * 0.6f, h * 0.24f, h * 0.24f, p, path)
            c.restore()
            label(c, p, "휙!", w * 0.3f, h * 0.22f, h * 0.12f, Theme.GOLD_LIGHT)
            label(c, p, COOLDOWN, w * 0.8f, h * 0.96f, h * 0.08f, Theme.RED)
        },
        RuleCard(
            "손 떼면 가운데로",
            "주인이 손을 떼면 왕관은 가운데로 돌아가요.\n가운데 왕관은 먼저 새로 누른 사람 것 (누르던 손은 떼고 다시)!\n${GameConfig.GAME_TIME_SEC}초 뒤 점수 높은 순서로 순위 (같은 점수는 같은 순위).",
        ) { c, w, h, p ->
            // 가운데 시계 + 왕관, 손 뗀 버튼에서 가운데로 날아감
            val cx = w * 0.36f
            val cy = h * 0.5f
            clock(c, p, cx, cy, h * 0.22f, 0.62f)
            CrownArt.crown(c, cx, cy - h * 0.02f, h * 0.22f, p, path)
            CrownArt.button(c, w * 0.11f, h * 0.8f, h * 0.1f, Theme.PLAYER_COLORS[2], false, p)
            arc(c, p, w * 0.12f, h * 0.66f, cx - h * 0.24f, cy - h * 0.06f, h * 0.1f, h * 0.01f)
            // 순위 막대
            for (k in 0..2) {
                val x = w * (0.62f + k * 0.13f)
                val bh = h * 0.5f * BAR_VALS[k] / 240f
                p.style = Paint.Style.FILL
                p.color = Theme.PLAYER_COLORS[k]
                c.drawRoundRect(x - w * 0.05f, h * 0.84f - bh, x + w * 0.05f, h * 0.84f, h * 0.02f, h * 0.02f, p)
                label(c, p, BAR_TEXT[k], x, h * 0.81f - bh, h * 0.07f, Theme.TEXT)
                label(c, p, BAR_RANK[k], x, h * 0.96f, h * 0.075f, Theme.TEXT_SUB)
            }
            CrownArt.crown(c, w * 0.75f, h * 0.84f - h * 0.5f - h * 0.17f, h * 0.13f, p, path)
        },
    )
}
