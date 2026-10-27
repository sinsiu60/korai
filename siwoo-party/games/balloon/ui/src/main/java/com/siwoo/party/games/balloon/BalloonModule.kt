package com.siwoo.party.games.balloon

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.balloon.ui.BalloonActivity

/** 풍선 치킨 게임: 모음집 등록 정보 */
object BalloonModule : GameModule {
    override val id = "balloon"
    override val name = "풍선 치킨 게임"
    override val tagline = "누가 더 크게? 터지기 직전에 멈춰라!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "2분"
    override val color = 0xFFE53970.toInt()
    override val activityClass = BalloonActivity::class.java

    private val path = Path()
    // 규칙 그림용 (그릴 때 만들지 않게 미리)
    private val BAR_VALS = intArrayOf(41, 63, 27)
    private val BAR_TEXT = arrayOf("41", "63", "27")
    private val BAR_RANK = arrayOf("2위", "1위", "3위")

    /** 풍선 하나: 꼭지 (cx, knotY), 반지름 r (세로는 1.12배), 줄 길이 tail */
    private fun balloon(c: Canvas, p: Paint, cx: Float, knotY: Float, r: Float, color: Int, tail: Float) {
        val ry = r * BalloonLayout.STRETCH
        val cy = knotY - ry
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.06f
        p.color = 0xCCFFFFFF.toInt()
        path.reset()
        path.moveTo(cx, knotY)
        path.quadTo(cx + r * 0.25f, knotY + tail / 2f, cx - r * 0.05f, knotY + tail)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.color = Theme.shade(color, 0.6f)
        path.reset()
        path.moveTo(cx - r * 0.16f, knotY + r * 0.04f)
        path.lineTo(cx + r * 0.16f, knotY + r * 0.04f)
        path.lineTo(cx, knotY - r * 0.2f)
        path.close()
        c.drawPath(path, p)
        p.color = Theme.shade(color, 0.7f)
        c.drawOval(cx - r, cy - ry, cx + r, cy + ry, p)
        p.color = color
        c.drawOval(cx - r * 0.94f, cy - ry * 0.98f, cx + r * 0.88f, cy + ry * 0.9f, p)
        p.color = 0x70FFFFFF
        c.drawOval(cx - r * 0.55f, cy - ry * 0.72f, cx - r * 0.18f, cy - ry * 0.25f, p)
    }

    /** 터지는 별 조각 */
    private fun burst(c: Canvas, p: Paint, cx: Float, cy: Float, s: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        path.reset()
        for (k in 0 until 16) {
            val a = Math.PI * 2 * k / 16
            val rad = if (k % 2 == 0) s else s * 0.45f
            val x = cx + (rad * Math.cos(a)).toFloat()
            val y = cy + (rad * Math.sin(a)).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, p)
    }

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

    /** 빨간 [멈춤] 버튼 (또는 금색 확정 도장) */
    private fun button(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, done: Boolean) {
        val rad = (b - t) * 0.3f
        p.style = Paint.Style.FILL
        p.color = if (done) Theme.GOLD_DARK else Theme.RED_DARK
        c.drawRoundRect(l, t + (b - t) * 0.1f, r, b, rad, rad, p)
        p.color = if (done) Theme.GOLD else Theme.RED
        c.drawRoundRect(l, t, r, b - (b - t) * 0.1f, rad, rad, p)
        label(c, p, if (done) "확정!" else "멈춤", (l + r) / 2f, t + (b - t) * 0.62f, (b - t) * 0.5f, if (done) Theme.INK else 0xFFFFFFFF.toInt())
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
        // 부르르 떠는 큰 풍선 + 떨림 선
        paint.strokeWidth = r * 0.06f
        paint.color = Theme.GOLD_LIGHT
        canvas.drawLine(cx - r * 0.62f, cy - r * 0.42f, cx - r * 0.5f, cy - r * 0.3f, paint)
        canvas.drawLine(cx - r * 0.66f, cy - r * 0.18f, cx - r * 0.52f, cy - r * 0.14f, paint)
        canvas.drawLine(cx + r * 0.62f, cy - r * 0.42f, cx + r * 0.5f, cy - r * 0.3f, paint)
        canvas.drawLine(cx + r * 0.66f, cy - r * 0.18f, cx + r * 0.52f, cy - r * 0.14f, paint)
        balloon(canvas, paint, cx, cy + r * 0.38f, r * 0.42f, Theme.RED, r * 0.4f)
        burst(canvas, paint, cx + r * 0.42f, cy - r * 0.48f, r * 0.2f, Theme.GOLD)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard(
            "연타로 풍선 키우기",
            "자기 구역을 두드릴수록 풍선이 커져요.\n크기가 곧 점수! (1초에 ${GameConfig.MAX_TAPS_PER_SEC}번까지만 인정)\n커질수록 풍선이 부르르 떨려요.",
        ) { c, w, h, p ->
            val base = h * 0.8f
            balloon(c, p, w * 0.2f, base, h * 0.11f, Theme.PLAYER_COLORS[1], h * 0.12f)
            balloon(c, p, w * 0.45f, base, h * 0.19f, Theme.PLAYER_COLORS[1], h * 0.12f)
            balloon(c, p, w * 0.74f, base, h * 0.29f, Theme.PLAYER_COLORS[1], h * 0.12f)
            // 손가락 탭 표시
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = Theme.GOLD_LIGHT
            for (k in 0..2) {
                val x = w * (0.2f + k * 0.27f) + h * 0.11f + h * 0.05f * k
                c.drawCircle(x, h * 0.92f, h * 0.025f, p)
                c.drawCircle(x, h * 0.92f, h * 0.05f, p)
            }
            label(c, p, "3", w * 0.2f, base - h * 0.08f, h * 0.09f, 0xFFFFFFFF.toInt())
            label(c, p, "12", w * 0.45f, base - h * 0.16f, h * 0.11f, 0xFFFFFFFF.toInt())
            label(c, p, "28", w * 0.74f, base - h * 0.27f, h * 0.14f, 0xFFFFFFFF.toInt())
        },
        RuleCard(
            "터지기 전에 [멈춤]!",
            "풍선마다 터지는 크기가 몰래 정해져 있어요\n(${GameConfig.POP_MIN}~${GameConfig.POP_MAX}, 사람마다 달라요).\n[멈춤]을 누르면 지금 크기가 점수로 확정,\n터지면 그 라운드는 0점!",
        ) { c, w, h, p ->
            // 왼쪽: 멈춰서 확정
            balloon(c, p, w * 0.22f, h * 0.72f, h * 0.22f, Theme.PLAYER_COLORS[2], h * 0.14f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.02f
            p.color = Theme.GOLD
            c.drawOval(w * 0.22f - h * 0.25f, h * 0.72f - h * 0.52f, w * 0.22f + h * 0.25f, h * 0.72f + h * 0.03f, p)
            button(c, p, w * 0.08f, h * 0.8f, w * 0.36f, h * 0.98f, true)
            label(c, p, "23점", w * 0.22f, h * 0.15f, h * 0.11f, Theme.GOLD)
            // 오른쪽: 터짐
            burst(c, p, w * 0.7f, h * 0.48f, h * 0.24f, Theme.GOLD)
            burst(c, p, w * 0.7f, h * 0.48f, h * 0.15f, Theme.RED)
            label(c, p, "펑!", w * 0.7f, h * 0.53f, h * 0.15f, 0xFFFFFFFF.toInt())
            button(c, p, w * 0.56f, h * 0.8f, w * 0.84f, h * 0.98f, false)
            label(c, p, "0점", w * 0.7f, h * 0.15f, h * 0.11f, Theme.RED)
        },
        RuleCard(
            "${GameConfig.BALLOON_ROUNDS}라운드 합계로 순위",
            "모두 멈추거나 터지면 라운드 끝.\n${GameConfig.ROUND_TIME_SEC}초가 지나면 남은 풍선은 그 크기로 확정!\n${GameConfig.BALLOON_ROUNDS}라운드 점수를 더해 높은 순서로 순위\n(같은 점수는 같은 순위).",
        ) { c, w, h, p ->
            for (k in 0..2) {
                val x = w * (0.22f + k * 0.28f)
                val bh = h * 0.6f * BAR_VALS[k] / 63f
                p.style = Paint.Style.FILL
                p.color = Theme.PLAYER_COLORS[k]
                c.drawRoundRect(x - w * 0.09f, h * 0.86f - bh, x + w * 0.09f, h * 0.86f, h * 0.03f, h * 0.03f, p)
                label(c, p, BAR_TEXT[k], x, h * 0.82f - bh, h * 0.1f, 0xFFFFFFFF.toInt())
                label(c, p, BAR_RANK[k], x, h * 0.97f, h * 0.08f, Theme.TEXT_SUB)
            }
            // 1위 왕관
            val x = w * 0.5f
            val top = h * 0.86f - h * 0.6f - h * 0.17f
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            path.reset()
            path.moveTo(x - h * 0.08f, top + h * 0.06f)
            path.lineTo(x - h * 0.08f, top - h * 0.02f)
            path.lineTo(x - h * 0.035f, top + h * 0.02f)
            path.lineTo(x, top - h * 0.04f)
            path.lineTo(x + h * 0.035f, top + h * 0.02f)
            path.lineTo(x + h * 0.08f, top - h * 0.02f)
            path.lineTo(x + h * 0.08f, top + h * 0.06f)
            path.close()
            c.drawPath(path, p)
        },
    )
}
