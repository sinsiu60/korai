package com.siwoo.party.games.minigolf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.minigolf.ui.GolfArt
import com.siwoo.party.games.minigolf.ui.MinigolfActivity

/** 미니 골프: 모음집 등록 정보 (2~4인, 차례대로, 9홀) */
object MinigolfModule : GameModule {
    override val id = "minigolf"
    override val name = "미니 골프"
    override val tagline = "끌었다 놓아 퍼팅! 9홀을 가장 적은 타수로"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "15분"
    override val color = 0xFF2E9D4A.toInt()
    override val activityClass = MinigolfActivity::class.java

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.42f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.05f, cy + r * 0.09f, r, paint)
        paint.color = GolfArt.RAIL
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = GolfArt.GRASS
        canvas.drawCircle(cx, cy, r * 0.88f, paint)
        paint.color = 0x22FFFFFF
        canvas.drawRect(cx - r * 0.3f, cy - r * 0.84f, cx + r * 0.05f, cy + r * 0.84f, paint)
        // 공이 지나온 점선 → 컵
        paint.color = 0xCCFFFFFF.toInt()
        for (k in 0 until 4) canvas.drawCircle(cx - r * 0.55f + k * r * 0.2f, cy + r * 0.45f - k * r * 0.14f, r * (0.035f + k * 0.01f), paint)
        GolfArt.cup(canvas, paint, cx + r * 0.32f, cy + r * 0.22f, r * 0.16f)
        GolfArt.flag(canvas, paint, cx + r * 0.32f, cy + r * 0.22f, r * 0.85f)
        GolfArt.ball(canvas, paint, cx + r * 0.05f, cy + r * 0.05f, r * 0.13f)
        paint.style = Paint.Style.FILL
    }

    // ── 규칙 그림 (그릴 때 할당하지 않도록 미리) ──
    private val RULE_COLORS = intArrayOf(Theme.PLAYER_COLORS[0], Theme.PLAYER_COLORS[1], Theme.PLAYER_COLORS[2])
    private val RULE_ROWS = arrayOf(intArrayOf(2, 3, 1), intArrayOf(3, 4, 3), intArrayOf(2, 6, 2))
    private val RULE_SUMS = Array(RULE_ROWS.size) { RULE_ROWS[it].sum().toString() }
    private val DIGITS = Array(10) { it.toString() }
    private val crownPath = android.graphics.Path()

    /** 잔디 판 + 나무 테두리 */
    private fun green(c: Canvas, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = GolfArt.ROUGH
        c.drawRect(0f, 0f, w, h, p)
        p.color = GolfArt.RAIL
        c.drawRoundRect(w * 0.04f, h * 0.08f, w * 0.96f, h * 0.92f, h * 0.05f, h * 0.05f, p)
        p.color = GolfArt.GRASS
        c.drawRect(w * 0.06f, h * 0.13f, w * 0.94f, h * 0.87f, p)
        p.color = 0x14FFFFFF
        var x = w * 0.06f
        while (x < w * 0.94f) {
            c.drawRect(x, h * 0.13f, minOf(x + w * 0.05f, w * 0.94f), h * 0.87f, p)
            x += w * 0.1f
        }
    }

    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF1A2A10.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("끌었다 놓아 퍼팅", "코스를 누른 채 뒤로 끌면 공이 반대쪽으로 굴러가요.\n많이 끌수록 세게 — 점선과 고리로 힘을 봐요.\n아주 짧게 끌고 놓으면 취소!") { c, w, h, p ->
            green(c, w, h, p)
            val bx = w * 0.3f
            val by = h * 0.58f
            val r = h * 0.05f
            // 고무줄 (공 → 뒤로 당긴 손가락)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.02f
            p.color = Theme.GOLD_LIGHT
            c.drawLine(bx, by, bx - w * 0.16f, by + h * 0.18f, p)
            p.strokeCap = Paint.Cap.BUTT
            // 힘 고리
            p.strokeWidth = h * 0.018f
            p.color = 0x66000000
            c.drawCircle(bx, by, r * 2.6f, p)
            p.color = Theme.GOLD
            c.drawArc(bx - r * 2.6f, by - r * 2.6f, bx + r * 2.6f, by + r * 2.6f, -90f, 250f, false, p)
            p.style = Paint.Style.FILL
            // 방향 점선
            p.color = Theme.TEXT
            for (k in 1..7) c.drawCircle(bx + k * w * 0.055f, by - k * h * 0.0618f, h * (0.02f - k * 0.0015f), p)
            GolfArt.ball(c, p, bx, by, r)
            // 손가락 (동그라미)
            p.color = 0xFFFFD7B0.toInt()
            c.drawCircle(bx - w * 0.16f, by + h * 0.18f, h * 0.06f, p)
            p.color = 0x33000000
            c.drawCircle(bx - w * 0.16f, by + h * 0.2f, h * 0.035f, p)
            GolfArt.cup(c, p, w * 0.78f, h * 0.26f, h * 0.05f)
            GolfArt.flag(c, p, w * 0.78f, h * 0.26f, h * 0.22f)
        },
        RuleCard("모래 · 물 · 비탈 · 움직이는 벽", "모래에선 공이 금방 멈추고, 물에 빠지면\n벌타 +1타 후 친 자리에서 다시 쳐요.\n꺾쇠 무늬 비탈은 그쪽으로 공을 밀고,\n노랑 블록은 공이 구를 때만 움직여요.") { c, w, h, p ->
            green(c, w, h, p)
            // 모래
            p.color = GolfArt.SAND
            c.drawOval(w * 0.08f, h * 0.2f, w * 0.3f, h * 0.5f, p)
            p.color = 0x33000000
            for (k in 0 until 14) c.drawCircle(w * (0.11f + (k * 37 % 17) / 100f), h * (0.27f + (k * 23 % 19) / 100f), h * 0.008f, p)
            // 물 + 물보라
            p.color = GolfArt.WATER
            c.drawRoundRect(w * 0.36f, h * 0.18f, w * 0.62f, h * 0.55f, h * 0.04f, h * 0.04f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.014f
            p.color = GolfArt.WATER_LIGHT
            c.drawOval(w * 0.44f, h * 0.32f, w * 0.54f, h * 0.4f, p)
            c.drawOval(w * 0.41f, h * 0.29f, w * 0.57f, h * 0.43f, p)
            // 비탈 꺾쇠
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.016f
            p.color = 0xAAFFFFFF.toInt()
            for (k in 0 until 3) GolfArt.chevron(c, p, w * (0.2f + k * 0.1f), h * 0.72f, 0f, 1f, h * 0.09f)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
            // 움직이는 블록 + 화살표
            GolfArt.slider(c, p, w * 0.74f, h * 0.3f, w * 0.8f, h * 0.62f, h * 0.06f)
            p.color = Theme.TEXT
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            c.drawLine(w * 0.86f, h * 0.24f, w * 0.86f, h * 0.68f, p)
            p.style = Paint.Style.FILL
            shout(c, p, "+1", w * 0.49f, h * 0.27f, h * 0.12f, 0xFFFF6B78.toInt())
        },
        RuleCard("한 홀씩 차례로 · 적은 타수가 우승", "한 사람씩 컵에 넣을 때까지 쳐요.\n홀마다 최대 ${GameConfig.MAX_STROKES}타 — 못 넣으면 ${GameConfig.MAX_STROKES}타로 기록.\n한 번에 넣으면 홀인원!\n${GameConfig.HOLES}홀 타수 합이 가장 적은 사람이 1위.") { c, w, h, p ->
            p.style = Paint.Style.FILL
            p.color = Theme.CARD
            c.drawRoundRect(w * 0.05f, h * 0.1f, w * 0.95f, h * 0.9f, h * 0.06f, h * 0.06f, p)
            // 작은 점수표
            val names = RULE_COLORS
            val rows = RULE_ROWS
            val left = w * 0.1f
            val cell = w * 0.12f
            val top = h * 0.42f
            val rh = h * 0.13f
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = rh * 0.6f
            for (i in rows.indices) {
                val row = rows[i]
                val y = top + i * rh
                p.color = names[i]
                c.drawCircle(left + rh * 0.3f, y + rh * 0.5f, rh * 0.28f, p)
                for (k in row.indices) {
                    val s = row[k]
                    p.color = if (s == 1) Theme.GOLD else if (s >= GameConfig.MAX_STROKES) 0xFF5A607A.toInt() else 0x33FFFFFF
                    c.drawRoundRect(left + rh + k * cell, y + rh * 0.08f, left + rh + (k + 1) * cell - w * 0.01f, y + rh * 0.92f, rh * 0.2f, rh * 0.2f, p)
                    p.color = if (s == 1) Theme.INK else Theme.TEXT
                    c.drawText(DIGITS[s], left + rh + (k + 0.5f) * cell, y + rh * 0.72f, p)
                }
                p.color = if (i == 0) Theme.GOLD else Theme.TEXT
                c.drawText(RULE_SUMS[i], left + rh + 3.6f * cell, y + rh * 0.72f, p)
            }
            p.textAlign = Paint.Align.LEFT
            p.typeface = Typeface.DEFAULT
            shout(c, p, "홀인원!", w * 0.5f, h * 0.32f, h * 0.17f, Theme.GOLD)
            p.color = Theme.GOLD
            com.siwoo.party.common.ui.Ui.drawCrown(c, w * 0.83f, h * 0.53f, h * 0.14f, crownPath, p)
        },
    )
}
