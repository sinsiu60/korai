package com.siwoo.party.games.tug

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.tug.ui.TugActivity
import com.siwoo.party.games.tug.ui.TugArt

/** 연타 줄다리기: 모음집 등록 정보 (2인 1 대 1, 4인 2 대 2 — 3인은 안 됨) */
object TugModule : GameModule {
    override val id = "tug"
    override val name = "연타 줄다리기"
    override val tagline = "미친 듯이 연타해서 깃발을 우리 쪽으로!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val minutes = "1분"
    override val color = 0xFF1565C0.toInt()
    override val activityClass = TugActivity::class.java

    override fun supports(count: Int): Boolean = count == GameConfig.SOLO_PLAYERS || count == GameConfig.TEAM_PLAYERS

    override val playersLabel: String get() = "2·4인"

    private val clip = Path()

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
        // 원 안쪽만: 밧줄 · 양쪽 사람 · 가운데 깃발
        val u = r / 75f
        val ground = cy + r * 0.47f
        val ropeY = ground - 38f * u
        clip.reset()
        clip.addCircle(cx, cy, r * 0.88f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        TugArt.rope(canvas, paint, cx - r, cx + r, ropeY, r * 0.13f, 0f, r, 0f, 0f)
        TugArt.figure(canvas, paint, cx - r * 0.5f, ground, u, 1f, 16f, 0f, Theme.PLAYER_COLORS[0], false)
        TugArt.figure(canvas, paint, cx + r * 0.5f, ground, u, -1f, 16f, 0f, Theme.PLAYER_COLORS[1], false)
        canvas.restore()
        TugArt.pennant(canvas, paint, cx - r * 0.06f, ropeY, r * 0.42f, -r * 0.04f)
    }

    /** 규칙 그림 공통: 가운데 띠 + 승리선 + 밧줄(shift 만큼 밀림) + 깃발 + 사람 */
    private fun arena(c: Canvas, w: Float, h: Float, p: Paint, shift: Float, loser: Int) {
        val u = h / 165f
        val cy = h * 0.5f
        val half = w * 0.27f
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRect(0f, cy - 44f * u, w, cy + 44f * u, p)
        TugArt.winLine(c, p, w / 2f - half, cy - 40f * u, cy + 40f * u, u, Theme.GOLD)
        TugArt.winLine(c, p, w / 2f + half, cy - 40f * u, cy + 40f * u, u, Theme.GOLD)
        val ropeY = cy - 8f * u
        TugArt.rope(c, p, 0f, w, ropeY, 7f * u, 2f * u, 60f * u, 0.5f, shift)
        for (side in 0..1) {
            val dir = if (side == 0) 1f else -1f
            val x = w / 2f - dir * (half + 18f * u) + shift * 0.15f
            val fall = if (side == loser) 70f else 0f
            val dx = if (side == loser) dir * 18f * u else 0f
            TugArt.figure(c, p, x + dx, ropeY + 38f * u, u, dir, if (side == loser) 0f else 18f, fall, Theme.PLAYER_COLORS[side], false)
        }
        TugArt.pennant(c, p, w / 2f + shift, ropeY, 24f * u, 0f)
    }

    /** 터치 물결 표시 */
    private fun ripple(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.STROKE
        p.color = color
        p.strokeWidth = r * 0.16f
        c.drawCircle(x, y, r, p)
        p.alpha = 120
        c.drawCircle(x, y, r * 1.6f, p)
        p.alpha = 255
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, y, r * 0.35f, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("자기 구역을 마구 연타!", "2인은 1 대 1, 4인은 2 대 2 팀전!\n팀은 시작할 때 무작위로 정해요.\n자기 구역을 두드릴 때마다\n밧줄이 우리 팀 쪽으로 끌려와요.") { c, w, h, p ->
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.025f
            p.color = Theme.PLAYER_COLORS[0]
            c.drawRoundRect(w * 0.04f, h * 0.06f, w * 0.48f, h * 0.94f, h * 0.06f, h * 0.06f, p)
            p.color = Theme.PLAYER_COLORS[1]
            c.drawRoundRect(w * 0.52f, h * 0.06f, w * 0.96f, h * 0.94f, h * 0.06f, h * 0.06f, p)
            arena(c, w, h, p, -w * 0.06f, -1)
            ripple(c, p, w * 0.2f, h * 0.84f, h * 0.06f, Theme.PLAYER_COLORS[0])
            ripple(c, p, w * 0.32f, h * 0.18f, h * 0.05f, Theme.PLAYER_COLORS[0])
            ripple(c, p, w * 0.78f, h * 0.82f, h * 0.045f, Theme.PLAYER_COLORS[1])
        },
        RuleCard("깃발을 승리선까지", "빨간 깃발은 두 팀 탭 수 차이만큼 움직여요.\n금색 승리선에 먼저 닿으면 바로 승리!\n한 사람당 1초에 ${GameConfig.TUG_MAX_TAPS_PER_SEC}번까지만 인정돼요.") { c, w, h, p ->
            arena(c, w, h, p, -w * 0.27f, -1)
            // 화살표: 왼쪽으로 끌려감
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.03f
            p.color = Theme.GOLD_LIGHT
            val y = h * 0.86f
            c.drawLine(w * 0.62f, y, w * 0.36f, y, p)
            c.drawLine(w * 0.36f, y, w * 0.41f, y - h * 0.05f, p)
            c.drawLine(w * 0.36f, y, w * 0.41f, y + h * 0.05f, p)
        },
        RuleCard("${GameConfig.TUG_TIME_SEC}초가 끝나면", "깃발이 더 가까운 팀 승리!\n이긴 팀은 모두 1위예요.\n딱 가운데면 ${GameConfig.TUG_OVERTIME_MS / 1000}초 연장전,\n그래도 같으면 무승부(모두 1위).") { c, w, h, p ->
            arena(c, w, h, p, w * 0.12f, 0)
            // 시계 알약
            p.style = Paint.Style.FILL
            p.color = Theme.RED_DARK
            c.drawRoundRect(w * 0.38f, h * 0.04f, w * 0.62f, h * 0.2f, h * 0.08f, h * 0.08f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = Theme.RED
            c.drawRoundRect(w * 0.38f, h * 0.04f, w * 0.62f, h * 0.2f, h * 0.08f, h * 0.08f, p)
            p.style = Paint.Style.FILL
            p.color = 0xFFFFFFFF.toInt()
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.12f
            p.isFakeBoldText = true
            c.drawText("0", w * 0.5f, h * 0.165f, p)
            p.isFakeBoldText = false
            p.textAlign = Paint.Align.LEFT
            // 이긴 쪽 왕관 대신 별
            p.color = Theme.GOLD
            c.drawCircle(w * 0.8f, h * 0.2f, h * 0.035f, p)
            c.drawCircle(w * 0.87f, h * 0.14f, h * 0.025f, p)
            c.drawCircle(w * 0.73f, h * 0.13f, h * 0.02f, p)
        },
    )
}
