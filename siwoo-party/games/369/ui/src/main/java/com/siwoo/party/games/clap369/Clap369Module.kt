package com.siwoo.party.games.clap369

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PartyTag
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.clap369.ui.Clap369Activity
import com.siwoo.party.games.clap369.ui.Clap369Art
import com.siwoo.party.games.clap369.ui.ClapPadView

/** 369 박수: 모음집 등록 정보 (폴더는 games/369, 패키지는 clap369) */
object Clap369Module : GameModule {
    override val id = "clap369"
    override val name = "369 박수"
    override val tagline = "삼, 육, 구! 숫자 대신 짝! 틀리면 바로 탈락"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.PARTY
    override val partyTags = listOf(PartyTag.TALK)
    override val minutes = "3분"
    override val color = 0xFFFF3FA4.toInt()
    override val activityClass = Clap369Activity::class.java

    private val rect = RectF()
    private const val LOGO = "369"
    private val RULE_NUMS = arrayOf("1~9", "30~39", "60~69", "90~")
    private val RULE_LIMITS = arrayOf("3.0초", "2.4초", "1.8초", "1.2초")

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.color = color
        c.drawText(s, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = Theme.Party.CARD_LIGHT
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.09f
        paint.color = Theme.Party.NEON_PINK
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        Clap369Art.clap(canvas, cx, cy - r * 0.02f, r * 0.82f, paint, true)
        // "369" 띠
        paint.style = Paint.Style.FILL
        paint.color = Theme.Party.NEON_SKY
        canvas.drawRoundRect(cx - r * 0.6f, cy + r * 0.32f, cx + r * 0.6f, cy + r * 0.72f, r * 0.2f, r * 0.2f, paint)
        text(canvas, LOGO, cx, cy + r * 0.64f, r * 0.36f, Theme.INK, paint)
    }

    /** 규칙 그림: 숫자 → 누를 버튼 한 줄 */
    private fun row(c: Canvas, w: Float, h: Float, y: Float, num: String, claps: Int, p: Paint) {
        text(c, num, w * 0.18f, y + h * 0.07f, h * 0.18f, 0xFFFFFFFF.toInt(), p)
        text(c, "→", w * 0.33f, y + h * 0.06f, h * 0.12f, Theme.Party.TEXT_SUB, p)
        val bh = h * 0.2f
        if (claps == 0) {
            rect.set(w * 0.42f, y - bh / 2f, w * 0.68f, y + bh / 2f)
            Clap369Art.slab(c, rect, ClapPadView.NUMBER_BLUE, false, false, bh * 0.12f, p)
            text(c, num, rect.centerX(), y + h * 0.045f, bh * 0.55f, 0xFFFFFFFF.toInt(), p)
        } else {
            for (k in 0 until claps) {
                val l = w * 0.42f + k * w * 0.2f
                rect.set(l, y - bh / 2f, l + w * 0.17f, y + bh / 2f)
                Clap369Art.slab(c, rect, ClapPadView.CLAP_PINK, false, false, bh * 0.12f, p)
                Clap369Art.clap(c, rect.centerX(), y - bh * 0.02f, bh * 0.62f, p, false)
            }
        }
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("숫자를 이어 가요", "차례대로 1, 2, 3 … 숫자를 말하며 이어 가요.\n3·6·9가 없으면 [숫자]를 1번,\n있으면 그 개수만큼 [짝]! (33은 짝 2번, 13은 짝 1번)") { c, w, h, p ->
            row(c, w, h, h * 0.2f, "12", 0, p)
            row(c, w, h, h * 0.5f, "13", 1, p)
            row(c, w, h, h * 0.8f, "33", 2, p)
        },
        RuleCard("점점 빨라져요", "제한 시간 3초에서 10, 20, 30 …마다 줄어요 (90부터 1.2초).\n실제로 숫자를 말하고 손뼉 치면서 누르세요!\n다 누른 뒤 0.4초 안에 더 누르면 탈락.") { c, w, h, p ->
            for (k in 0 until 4) {
                val y = h * (0.18f + k * 0.22f)
                val frac = (3.0f - 0.6f * k) / 3.0f
                text(c, RULE_NUMS[k], w * 0.14f, y + h * 0.04f, h * 0.085f, Theme.Party.TEXT_SUB, p)
                p.style = Paint.Style.FILL
                p.color = Theme.BG_DEEP
                c.drawRoundRect(w * 0.28f, y - h * 0.05f, w * 0.78f, y + h * 0.05f, h * 0.05f, h * 0.05f, p)
                p.color = if (k == 3) Theme.RED else if (k == 2) Theme.GOLD else Theme.GREEN
                c.drawRoundRect(w * 0.28f, y - h * 0.05f, w * 0.28f + w * 0.5f * frac, y + h * 0.05f, h * 0.05f, h * 0.05f, p)
                text(c, RULE_LIMITS[k], w * 0.89f, y + h * 0.04f, h * 0.09f, Theme.GOLD, p)
            }
        },
        RuleCard("틀리면 탈락, 최후의 1인", "틀린 버튼·모자란 짝·시간 초과면 바로 탈락!\n다음 사람이 그 숫자부터 이어 가요.\n마지막까지 남은 사람이 1위, 늦게 탈락할수록 높은 순위.") { c, w, h, p ->
            for (k in 0 until 4) {
                val x = w * (0.14f + k * 0.24f)
                val out = k != 2
                p.style = Paint.Style.FILL
                p.color = if (out) Theme.GRAY else Theme.PLAYER_COLORS[k]
                c.drawCircle(x, h * 0.36f, h * 0.12f, p)
                c.drawRoundRect(x - h * 0.15f, h * 0.5f, x + h * 0.15f, h * 0.72f, h * 0.08f, h * 0.08f, p)
                if (out) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = h * 0.03f
                    p.color = Theme.RED
                    c.drawLine(x - h * 0.13f, h * 0.24f, x + h * 0.13f, h * 0.68f, p)
                    c.drawLine(x + h * 0.13f, h * 0.24f, x - h * 0.13f, h * 0.68f, p)
                }
            }
            text(c, "1위!", w * 0.62f, h * 0.15f, h * 0.11f, Theme.GOLD, p)
            text(c, "4위", w * 0.14f, h * 0.88f, h * 0.08f, Theme.Party.TEXT_SUB, p)
            text(c, "3위", w * 0.38f, h * 0.88f, h * 0.08f, Theme.Party.TEXT_SUB, p)
            text(c, "2위", w * 0.86f, h * 0.88f, h * 0.08f, Theme.Party.TEXT_SUB, p)
        },
    )
}
