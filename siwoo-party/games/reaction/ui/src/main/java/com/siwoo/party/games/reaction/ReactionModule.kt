package com.siwoo.party.games.reaction

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.reaction.ui.ReactionActivity

/** 반응속도 결투: 모음집 등록 정보 */
object ReactionModule : GameModule {
    override val id = "reaction"
    override val name = "반응속도 결투"
    override val tagline = "초록불이 켜지는 순간, 누구보다 빠르게!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "2분"
    override val color = 0xFF00897B.toInt()
    override val activityClass = ReactionActivity::class.java

    private val path = Path()
    private const val GREEN = 0xFF2ECC71.toInt()

    /** 번개 (cx, cy, 크기 s) */
    private fun bolt(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        path.reset()
        path.moveTo(cx + s * 0.15f, cy - s * 0.5f)
        path.lineTo(cx - s * 0.3f, cy + s * 0.08f)
        path.lineTo(cx - s * 0.02f, cy + s * 0.08f)
        path.lineTo(cx - s * 0.15f, cy + s * 0.5f)
        path.lineTo(cx + s * 0.3f, cy - s * 0.08f)
        path.lineTo(cx + s * 0.02f, cy - s * 0.08f)
        path.close()
        c.drawPath(path, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.38f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = GREEN
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.12f
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, r * 0.82f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Theme.GOLD
        bolt(canvas, cx, cy, r * 1.15f, paint)
    }

    /** 규칙 그림 공통: 네 귀퉁이 구역 + 가운데 신호 원 */
    private fun zones(c: Canvas, w: Float, h: Float, p: Paint, signal: Int) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.025f
        for (k in 0 until 4) {
            val l = if (k % 2 == 0) w * 0.06f else w * 0.53f
            val t = if (k < 2) h * 0.08f else h * 0.54f
            p.color = Theme.PLAYER_COLORS[k]
            c.drawRoundRect(l, t, l + w * 0.41f, t + h * 0.38f, h * 0.05f, h * 0.05f, p)
        }
        p.style = Paint.Style.FILL
        p.color = signal
        c.drawCircle(w / 2f, h / 2f, h * 0.2f, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("각자 구역에 손가락 준비", "화면이 사람 수만큼 나뉘어요. 자기 색 구역 위에 손가락을 올려 두고\n가운데 원이 회색인 동안 기다리세요.") { c, w, h, p ->
            zones(c, w, h, p, Theme.GRAY)
        },
        RuleCard("초록 \"지금!\"에 가장 먼저", "가운데가 초록으로 바뀌면 자기 구역을 누르세요. 가장 빠른 사람이 라운드 승리!\n5라운드 승수로 순위, 같으면 평균 반응 시간.") { c, w, h, p ->
            zones(c, w, h, p, GREEN)
            p.color = Theme.GOLD
            bolt(c, w / 2f, h / 2f, h * 0.28f, p)
        },
        RuleCard("빨강은 속임수!", "초록 전에 누르거나 빨간 \"속임수!\"에 누르면 그 라운드 실격 (큰 X).\n모두 실격이면 그 라운드는 다시 해요.") { c, w, h, p ->
            zones(c, w, h, p, Theme.RED)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.05f
            p.color = 0xFFFFFFFF.toInt()
            val d = h * 0.09f
            c.drawLine(w / 2f - d, h / 2f - d, w / 2f + d, h / 2f + d, p)
            c.drawLine(w / 2f + d, h / 2f - d, w / 2f - d, h / 2f + d, p)
        },
    )
}
