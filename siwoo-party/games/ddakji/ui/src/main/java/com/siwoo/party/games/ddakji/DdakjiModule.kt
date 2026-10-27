package com.siwoo.party.games.ddakji

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.ddakji.ui.DdakjiActivity
import com.siwoo.party.games.ddakji.ui.DdakjiArt

/** 딱지치기: 모음집 등록 정보 (2인 전용, 차례대로) */
object DdakjiModule : GameModule {
    override val id = "ddakji"
    override val name = "딱지치기"
    override val tagline = "힘과 각도를 맞춰 내리쳐 상대 딱지를 뒤집어라!"
    override val playerCounts = GameConfig.PLAYERS..GameConfig.PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "5분"
    override val color = 0xFFC2185B.toInt()
    override val activityClass = DdakjiActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private const val DIRT = 0xFF8A6844.toInt()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = DIRT
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 바닥의 파란 딱지 + 위에서 내리치는 빨간 딱지 + 충격 선
        DdakjiArt.draw(canvas, paint, cx - r * 0.05f, cy + r * 0.32f, r * 0.42f, 0.5f, BLUE, 0f, r * 0.06f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * 0.07f
        paint.color = Theme.GOLD_LIGHT
        canvas.drawLine(cx - r * 0.68f, cy + r * 0.05f, cx - r * 0.52f, cy + r * 0.15f, paint)
        canvas.drawLine(cx + r * 0.6f, cy + r * 0.02f, cx + r * 0.46f, cy + r * 0.14f, paint)
        canvas.drawLine(cx - r * 0.05f, cy - r * 0.12f, cx - r * 0.05f, cy - r * 0.02f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        DdakjiArt.draw(canvas, paint, cx + r * 0.12f, cy - r * 0.32f, r * 0.36f, 0.72f, RED, -18f, r * 0.06f)
        paint.style = Paint.Style.FILL
    }

    /** 규칙 그림 바탕: 흙바닥 + 분필 원 */
    private fun yard(c: Canvas, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = DIRT
        c.drawRect(0f, 0f, w, h, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.012f
        p.color = 0xBBFFFFFF.toInt()
        c.drawOval(w * 0.15f, h * 0.5f, w * 0.75f, h * 0.92f, p)
        p.style = Paint.Style.FILL
    }

    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF2A1A08.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("힘 → 각도 게이지를 멈춰요", "공격하는 사람은 화면을 두 번 터치해요.\n① 오르내리는 힘 게이지 멈추기\n② 왔다 갔다 하는 각도 바늘 멈추기\n둘 다 금색 칸이면 뒤집을 확률 최고!") { c, w, h, p ->
            yard(c, w, h, p)
            // 힘 막대
            val bl = w * 0.08f
            val br = w * 0.16f
            val bt = h * 0.12f
            val bb = h * 0.9f
            p.color = 0xAA1A1030.toInt()
            c.drawRoundRect(bl, bt, br, bb, w * 0.04f, w * 0.04f, p)
            val bh = bb - bt
            p.color = Theme.GOLD
            c.drawRect(bl, bb - bh * (GameConfig.POWER_SWEET + GameConfig.POWER_SWEET_HALF), br, bb - bh * (GameConfig.POWER_SWEET - GameConfig.POWER_SWEET_HALF), p)
            p.color = 0xCCFF6B3D.toInt()
            c.drawRect(bl + w * 0.01f, bb - bh * 0.6f, br - w * 0.01f, bb - w * 0.01f, p)
            // 각도 부채
            val ax = w * 0.96f
            val ay = h * 0.92f
            val ar = h * 0.46f
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.08f
            p.color = 0xAA1A1030.toInt()
            c.drawArc(ax - ar, ay - ar, ax + ar, ay + ar, 180f, 90f, false, p)
            p.color = Theme.GOLD
            c.drawArc(ax - ar, ay - ar, ax + ar, ay + ar, 180f + 90f * (GameConfig.ANGLE_SWEET - GameConfig.ANGLE_SWEET_HALF), 90f * GameConfig.ANGLE_SWEET_HALF * 2f, false, p)
            p.strokeWidth = h * 0.025f
            p.strokeCap = Paint.Cap.ROUND
            p.color = 0xFFFFFFFF.toInt()
            c.drawLine(ax, ay, ax - ar * 0.98f, ay - ar * 0.55f, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
            DdakjiArt.draw(c, p, w * 0.4f, h * 0.68f, h * 0.15f, 0.5f, BLUE)
            DdakjiArt.draw(c, p, w * 0.46f, h * 0.27f, h * 0.14f, 0.75f, RED, -12f)
        },
        RuleCard("내리쳐서 뒤집기", "딱지를 들어 올렸다가 쾅! 내리쳐요.\n상대 딱지가 들썩이다가 뒤집히면 성공 —\n그 딱지를 가져오고 한 번 더 공격!\n못 뒤집으면 공격이 넘어가요.") { c, w, h, p ->
            yard(c, w, h, p)
            // 뒤집히는 딱지 (뒷면이 보이며 공중에)
            DdakjiArt.draw(c, p, w * 0.42f, h * 0.48f, h * 0.17f, -0.32f, BLUE, 22f)
            DdakjiArt.draw(c, p, w * 0.6f, h * 0.76f, h * 0.15f, 0.5f, RED, 6f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.02f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.GOLD_LIGHT
            c.drawArc(w * 0.22f, h * 0.22f, w * 0.62f, h * 0.8f, 200f, 110f, false, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
            shout(c, p, "뒤집었다!", w * 0.74f, h * 0.24f, h * 0.13f, Theme.GOLD)
        },
        RuleCard("딱지를 모두 따면 승리", "처음엔 각자 ${GameConfig.DDAKJI_COUNT}장.\n상대 딱지를 모두 따면 1위, 다 잃은 사람 2위.\n뒤집힐 확률은 ${(GameConfig.CHANCE_MIN * 100).toInt()}% ~ ${(GameConfig.CHANCE_MAX * 100).toInt()}% —\n정확할수록 높아져요.") { c, w, h, p ->
            yard(c, w, h, p)
            for (k in 0 until 7) DdakjiArt.draw(c, p, w * 0.3f + (k % 2) * w * 0.02f, h * 0.82f - k * h * 0.075f, h * 0.15f, 0.45f, if (k % 3 == 0) BLUE else RED, if (k % 2 == 0) 4f else -4f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = 0x88FFFFFF.toInt()
            c.drawOval(w * 0.62f, h * 0.66f, w * 0.9f, h * 0.86f, p)
            p.style = Paint.Style.FILL
            shout(c, p, "1위", w * 0.3f, h * 0.2f, h * 0.15f, Theme.GOLD)
            shout(c, p, "0장", w * 0.76f, h * 0.55f, h * 0.12f, Theme.TEXT_SUB)
        },
    )
}
