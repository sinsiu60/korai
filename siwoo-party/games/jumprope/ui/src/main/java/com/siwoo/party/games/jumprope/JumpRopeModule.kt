package com.siwoo.party.games.jumprope

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.jumprope.ui.JumpRopeActivity
import com.siwoo.party.games.jumprope.ui.RopeArt

/** 줄넘기 생존: 모음집 등록 정보 */
object JumpRopeModule : GameModule {
    override val id = "jumprope"
    override val name = "줄넘기 생존"
    override val tagline = "줄이 바닥에 닿는 순간 폴짝! 끝까지 살아남아라"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "2분"
    override val color = 0xFFE8457A.toInt()
    override val activityClass = JumpRopeActivity::class.java

    private const val SKY = 0xFF2E8BE6.toInt()
    private val oval = RectF()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.42f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = SKY
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = 0x33FFFFFF
        canvas.drawCircle(cx, cy - r * 0.1f, r * 0.78f, paint)
        // 땅
        paint.color = 0x88C98B54.toInt()
        oval.set(cx - r * 0.62f, cy + r * 0.5f, cx + r * 0.62f, cy + r * 0.72f)
        canvas.drawOval(oval, paint)
        // 줄: 뒤쪽(위) 반 → 아이 → 앞쪽(아래) 반
        oval.set(cx - r * 0.8f, cy - r * 0.76f, cx + r * 0.8f, cy + r * 0.64f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * 0.1f
        paint.color = Theme.shade(RopeArt.ROPE, 0.62f)
        canvas.drawArc(oval, 180f, 180f, false, paint)
        val u = size / 122f
        RopeArt.kid(canvas, paint, cx, cy + r * 0.44f, u, Theme.GOLD, 0f, 0.9f, 0.85f, 0f, false)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * 0.13f
        paint.color = RopeArt.ROPE
        canvas.drawArc(oval, 8f, 164f, false, paint)
        paint.style = Paint.Style.FILL
    }

    /**
     * 규칙 그림 공통 무대: 땅·기둥·줄 + 아이 셋.
     * phase = 줄 위상, lifts = 아이별 점프(0..1), fallen = 넘어진 아이 번호(−1 없음)
     */
    private fun stage(c: Canvas, w: Float, h: Float, p: Paint, phase: Float, lift0: Float, lift1: Float, lift2: Float, fallen: Int, ropeColor: Int, glow: Boolean) {
        val u = h / 150f
        val cy = h * 0.47f
        val radius = 44f * u
        val ground = cy + radius
        val xL = w * 0.1f
        val xR = w * 0.9f
        p.style = Paint.Style.FILL
        p.color = 0x55C98B54
        c.drawRect(w * 0.04f, ground, w * 0.96f, h * 0.95f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * u
        p.color = if (glow) Theme.GOLD else 0x55FFFFFF
        if (glow) p.strokeWidth = 4f * u
        c.drawLine(xL, ground, xR, ground, p)
        val front = RopeArt.inFront(phase)
        if (!front) RopeArt.rope(c, p, xL, xR, cy, radius, 6f * u, ground, phase, 5f * u, ropeColor, 255)
        RopeArt.pole(c, p, xL, cy, ground, u, 6f * u, phase)
        RopeArt.pole(c, p, xR, cy, ground, u, 6f * u, phase)
        for (k in 0 until 3) {
            val x = w / 2f + (k - 1) * 58f * u
            val l = when (k) { 0 -> lift0; 1 -> lift1; else -> lift2 }
            val f = if (k == fallen) 1f else 0f
            RopeArt.kid(c, p, x, ground, u, Theme.PLAYER_COLORS[k], l * 24f * u, l, l * 0.7f, f, k == fallen)
        }
        if (front) RopeArt.rope(c, p, xL, xR, cy, radius, 6f * u, ground, phase, 5f * u, ropeColor, 255)
    }

    /** 손가락 탭 표시 (x, y) */
    private fun tap(c: Canvas, x: Float, y: Float, s: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.12f
        p.color = color
        c.drawCircle(x, y, s, p)
        p.alpha = 110
        c.drawCircle(x, y, s * 1.5f, p)
        p.alpha = 255
        p.style = Paint.Style.FILL
        c.drawCircle(x, y, s * 0.45f, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("줄이 바닥에 닿을 때 탭!", "가운데 아이들이 나란히 서 있고 큰 줄이 돌아요.\n줄이 바닥에 닿는 순간(바닥이 금색으로 빛날 때) 자기 색 구역을 탭하면 내 아이가 폴짝!") { c, w, h, p ->
            stage(c, w, h, p, 0.02f, 1f, 0.9f, 1f, -1, RopeArt.ROPE, true)
            val s = h * 0.05f
            tap(c, w * 0.08f, h * 0.16f, s, Theme.PLAYER_COLORS[0], p)
            tap(c, w * 0.92f, h * 0.16f, s, Theme.PLAYER_COLORS[1], p)
            tap(c, w * 0.5f, h * 0.12f, s, Theme.PLAYER_COLORS[2], p)
        },
        RuleCard("일찍·늦게·안 누르면 탈락", "판정 순간보다 너무 일찍 누르거나, 늦거나, 안 누르면 줄에 걸려 넘어져요.\n한 번 걸리면 탈락! 같은 순간에 여러 번 눌러도 한 번만 쳐요.") { c, w, h, p ->
            stage(c, w, h, p, 0.06f, 0.8f, 0f, 0.85f, 1, RopeArt.ROPE, false)
            val u = h / 150f
            text.textSize = 13f * u
            RopeArt.stamp(c, p, text, w / 2f + 22f * u, h * 0.47f + 2f * u, u, 1f, 255, "탈락")
        },
        RuleCard("10번마다 빨라지고, 쌩쌩이!", "10번 넘을 때마다 줄이 빨라져요. \"쌩쌩이!\" 예고가 뜨면 줄이 빠르게 두 바퀴 — 두 번 탭!\n최후의 1인이 1등, 먼저 걸린 순서대로 순위 (같이 걸리면 같은 순위).") { c, w, h, p ->
            val u = h / 150f
            val cy = h * 0.47f
            val ground = cy + 44f * u
            // 잔상 줄 두 개 + 본 줄 (쌩쌩이 주황)
            RopeArt.rope(c, p, w * 0.1f, w * 0.9f, cy, 44f * u, 6f * u, ground, 0.86f, 4f * u, RopeArt.ROPE_DOUBLE, 60)
            RopeArt.rope(c, p, w * 0.1f, w * 0.9f, cy, 44f * u, 6f * u, ground, 0.93f, 4f * u, RopeArt.ROPE_DOUBLE, 120)
            stage(c, w, h, p, 0.99f, 0.95f, 1f, 0.9f, -1, RopeArt.ROPE_DOUBLE, true)
            // "쌩쌩이" 말풍선
            oval.set(w / 2f - 42f * u, h * 0.03f, w / 2f + 42f * u, h * 0.03f + 24f * u)
            p.style = Paint.Style.FILL
            p.color = Theme.RED_DARK
            c.drawRoundRect(oval, 12f * u, 12f * u, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f * u
            p.color = Theme.GOLD
            c.drawRoundRect(oval, 12f * u, 12f * u, p)
            text.textSize = 14f * u
            text.color = Theme.GOLD_LIGHT
            c.drawText("쌩쌩이 ×2", w / 2f, h * 0.03f + 17f * u, text)
        },
    )
}
