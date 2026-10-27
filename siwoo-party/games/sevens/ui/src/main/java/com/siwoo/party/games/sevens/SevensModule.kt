package com.siwoo.party.games.sevens

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.cards.Suit
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.sevens.ui.SevensActivity
import com.siwoo.party.games.sevens.ui.SuitArt

/** 7 이어붙이기: 모음집 등록 정보 */
object SevensModule : GameModule {
    override val id = "sevens"
    override val name = "7 이어붙이기"
    override val tagline = "7 부터 위아래로 이어 붙여요! 막히면 패스 토큰, 먼저 다 내면 1등"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "10분"
    override val color = 0xFF1E8A5A.toInt()
    override val activityClass = SevensActivity::class.java

    private val rect = RectF()
    private val path = Path()
    private val dashEffect = DashPathEffect(floatArrayOf(6f, 5f), 0f)
    private const val PAPER_EDGE = 0xFFB8A77A.toInt()
    private const val FELT = 0xFF146B45.toInt()
    private const val FELT_DARK = 0xFF0B4A2F.toInt()

    // ── 그림 도우미 ──

    /** 종이 카드 한 장: 가운데 (cx, cy), 폭 w (높이 1.4w). 숫자는 가운데 위, 무늬는 아래. alpha 0..255 */
    private fun card(c: Canvas, cx: Float, cy: Float, w: Float, rot: Float, label: String, suit: Suit, p: Paint, alpha: Int = 255, gold: Boolean = false) {
        val h = w * 1.4f
        c.save()
        c.rotate(rot, cx, cy)
        p.style = Paint.Style.FILL
        p.pathEffect = null
        p.color = 0x55000000
        p.alpha = 0x55 * alpha / 255
        rect.set(cx - w / 2f + w * 0.04f, cy - h / 2f + w * 0.07f, cx + w / 2f + w * 0.04f, cy + h / 2f + w * 0.07f)
        c.drawRoundRect(rect, w * 0.12f, w * 0.12f, p)
        p.color = Theme.PAPER
        p.alpha = alpha
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        c.drawRoundRect(rect, w * 0.12f, w * 0.12f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = if (gold) w * 0.07f else w * 0.03f
        p.color = if (gold) Theme.GOLD else PAPER_EDGE
        p.alpha = alpha
        c.drawRoundRect(rect, w * 0.12f, w * 0.12f, p)
        p.style = Paint.Style.FILL
        val ink = SuitArt.color(suit)
        p.color = ink
        p.alpha = alpha
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = w * (if (label.length > 1) 0.42f else 0.55f)
        c.drawText(label, cx, cy - h * 0.04f, p)
        SuitArt.draw(c, suit, cx, cy + h * 0.25f, w * 0.44f, p)
        p.alpha = 255
        c.restore()
    }

    /** 빈 칸 (점선 테두리 + 흐린 숫자). glow 면 금색으로 "여기 놓을 수 있음" */
    private fun slot(c: Canvas, cx: Float, cy: Float, w: Float, label: String, glow: Boolean, p: Paint) {
        val h = w * 1.4f
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        if (glow) {
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            p.alpha = 70
            c.drawRoundRect(rect, w * 0.12f, w * 0.12f, p)
            p.alpha = 255
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * (if (glow) 0.06f else 0.035f)
        p.color = if (glow) Theme.GOLD else 0x66FFFFFF
        p.pathEffect = if (glow) null else dashEffect
        c.drawRoundRect(rect, w * 0.12f, w * 0.12f, p)
        p.pathEffect = null
        p.style = Paint.Style.FILL
        label(c, label, cx, cy + w * 0.2f, w * 0.5f, if (glow) Theme.GOLD else 0x99FFFFFF.toInt(), p)
    }

    /** 펠트 판 바탕 */
    private fun felt(c: Canvas, l: Float, t: Float, r: Float, b: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = FELT_DARK
        rect.set(l, t + (b - t) * 0.03f, r, b + (b - t) * 0.03f)
        c.drawRoundRect(rect, (b - t) * 0.1f, (b - t) * 0.1f, p)
        p.color = FELT
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, (b - t) * 0.1f, (b - t) * 0.1f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = (b - t) * 0.015f
        p.color = Theme.GOLD
        p.alpha = 140
        c.drawRoundRect(rect, (b - t) * 0.1f, (b - t) * 0.1f, p)
        p.alpha = 255
        p.style = Paint.Style.FILL
    }

    /** 가운데 정렬 글자 */
    private fun label(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.color = color
        c.drawText(text, x, y, p)
    }

    /** 화살표 (x0,y) → (x1,y) */
    private fun arrow(c: Canvas, x0: Float, x1: Float, y: Float, s: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.28f
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        c.drawLine(x0, y, x1, y, p)
        val dir = if (x1 > x0) 1f else -1f
        path.reset()
        path.moveTo(x1 - dir * s * 0.6f, y - s * 0.5f)
        path.lineTo(x1, y)
        path.lineTo(x1 - dir * s * 0.6f, y + s * 0.5f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    /** 패스 토큰 (금색 동전, used 면 빈 원) */
    private fun token(c: Canvas, x: Float, y: Float, r: Float, used: Boolean, p: Paint) {
        p.style = Paint.Style.FILL
        if (used) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.16f
            p.color = 0x88FFFFFF.toInt()
            c.drawCircle(x, y, r, p)
            p.style = Paint.Style.FILL
            return
        }
        p.color = Theme.GOLD_DARK
        c.drawCircle(x, y + r * 0.12f, r, p)
        p.color = Theme.GOLD
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.12f
        p.color = Theme.GOLD_LIGHT
        c.drawCircle(x, y, r * 0.72f, p)
        p.style = Paint.Style.FILL
        label(c, "P", x, y + r * 0.36f, r * 1.0f, Theme.INK, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val w = size * 0.3f
        // 6 · 7 · 8 이 한 줄로 이어 붙은 모양 (7 은 금색 테두리, 가운데 위)
        card(canvas, cx - size * 0.25f, cy + size * 0.05f, w, -10f, "6", Suit.HEART, paint)
        card(canvas, cx + size * 0.25f, cy + size * 0.05f, w, 10f, "8", Suit.HEART, paint)
        card(canvas, cx, cy - size * 0.02f, w * 1.15f, 0f, "7", Suit.HEART, paint, gold = true)
        // 양쪽 화살표
        arrow(canvas, cx - size * 0.3f, cx - size * 0.44f, cy + size * 0.36f, size * 0.07f, Theme.GOLD_LIGHT, paint)
        arrow(canvas, cx + size * 0.3f, cx + size * 0.44f, cy + size * 0.36f, size * 0.07f, Theme.GOLD_LIGHT, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard(
            "7 이 놓여야 줄이 열려요",
            "52장을 모두 나눠요. 판에는 무늬별 줄 4개 — 그 무늬의 7 이 놓여야 줄이 열려요.\n♠7 을 가진 사람이 자동으로 ♠7 을 내며 시작하고, 다음 사람부터 차례로.",
        ) { c, w, h, p ->
            felt(c, w * 0.05f, h * 0.06f, w * 0.95f, h * 0.94f, p)
            val cw = minOf(h * 0.13f, w * 0.085f)
            val rows = arrayOf(Suit.SPADE, Suit.HEART, Suit.DIAMOND, Suit.CLUB)
            for ((i, s) in rows.withIndex()) {
                val y = h * (0.2f + i * 0.2f)
                // 무늬 표
                p.style = Paint.Style.FILL
                p.color = Theme.PAPER
                c.drawCircle(w * 0.13f, y, cw * 0.42f, p)
                p.color = SuitArt.color(s)
                SuitArt.draw(c, s, w * 0.13f, y, cw * 0.5f, p)
                for (k in -2..2) {
                    val x = w * 0.5f + k * cw * 1.25f
                    val sevenOpen = s == Suit.SPADE || s == Suit.DIAMOND
                    when {
                        k == 0 && sevenOpen -> card(c, x, y, cw, 0f, "7", s, p, gold = true)
                        s == Suit.SPADE && (k == -1 || k == 1) -> card(c, x, y, cw, 0f, if (k < 0) "6" else "8", s, p)
                        k == 0 -> slot(c, x, y, cw, "7", false, p)
                        else -> slot(c, x, y, cw, (7 + k).toString(), false, p)
                    }
                }
                if (s == Suit.HEART || s == Suit.CLUB) label(c, "닫힘", w * 0.85f, y + h * 0.03f, h * 0.065f, 0xAAFFFFFF.toInt(), p)
                else label(c, "열림", w * 0.85f, y + h * 0.03f, h * 0.065f, Theme.GOLD_LIGHT, p)
            }
        },
        RuleCard(
            "위아래로 한 장씩 이어 붙이기",
            "7 에서 아래로 6 → A, 위로 8 → K. 줄 양 끝에 바로 이어지는 카드만 낼 수 있어요 (차례에 1장).\n낼 수 있는 카드는 떠오르며 빛나요. 눌러 고르고 한 번 더 누르면 판으로 날아가요.",
        ) { c, w, h, p ->
            felt(c, w * 0.04f, h * 0.08f, w * 0.96f, h * 0.62f, p)
            val cw = minOf(h * 0.2f, w * 0.1f)
            val y = h * 0.35f
            val step = cw * 1.18f
            val mid = w * 0.5f
            card(c, mid - step, y, cw, 0f, "6", Suit.SPADE, p)
            card(c, mid, y, cw, 0f, "7", Suit.SPADE, p, gold = true)
            card(c, mid + step, y, cw, 0f, "8", Suit.SPADE, p)
            slot(c, mid - step * 2, y, cw, "5", true, p)
            slot(c, mid + step * 2, y, cw, "9", true, p)
            slot(c, mid - step * 3, y, cw, "4", false, p)
            slot(c, mid + step * 3, y, cw, "10", false, p)
            arrow(c, mid - step * 1.6f, mid - step * 2.9f, h * 0.71f, h * 0.05f, Theme.GOLD_LIGHT, p)
            arrow(c, mid + step * 1.6f, mid + step * 2.9f, h * 0.71f, h * 0.05f, Theme.GOLD_LIGHT, p)
            label(c, "A 쪽으로", mid - step * 2.2f, h * 0.84f, h * 0.075f, Theme.GOLD_LIGHT, p)
            label(c, "K 쪽으로", mid + step * 2.2f, h * 0.84f, h * 0.075f, Theme.GOLD_LIGHT, p)
            label(c, "한 칸 건너뛰기 ✕", mid, h * 0.97f, h * 0.07f, Theme.RED, p)
        },
        RuleCard(
            "패스 토큰 3개, 막히면 탈락",
            "내기 싫거나 낼 카드가 없으면 토큰 1개로 패스 (1인 3개). 토큰도 낼 카드도 없으면 탈락 — 남은 카드는 판에 공개되고 자리가 나는 대로 저절로 놓여요.\n먼저 다 낸 순서로 순위, 탈락하면 그 뒤 (늦게 탈락할수록 위).",
        ) { c, w, h, p ->
            val r = h * 0.085f
            token(c, w * 0.12f, h * 0.24f, r, false, p)
            token(c, w * 0.24f, h * 0.24f, r, false, p)
            token(c, w * 0.36f, h * 0.24f, r, true, p)
            label(c, "패스!", w * 0.24f, h * 0.48f, h * 0.11f, Theme.GOLD, p)
            // 탈락 → 공개 카드 (반투명 + 주인 색 점선)
            val cw = h * 0.2f
            card(c, w * 0.62f, h * 0.32f, cw, -8f, "3", Suit.CLUB, p, alpha = 130)
            card(c, w * 0.78f, h * 0.32f, cw, 6f, "J", Suit.DIAMOND, p, alpha = 130)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            p.color = Theme.RED
            c.save()
            c.rotate(-12f, w * 0.7f, h * 0.36f)
            rect.set(w * 0.56f, h * 0.27f, w * 0.84f, h * 0.43f)
            c.drawRect(rect, p)
            c.restore()
            c.save()
            c.rotate(-12f, w * 0.7f, h * 0.36f)
            label(c, "탈락", w * 0.7f, h * 0.395f, h * 0.11f, Theme.RED, p)
            c.restore()
            label(c, "공개 → 자동으로 놓임", w * 0.7f, h * 0.6f, h * 0.068f, Theme.GOLD_LIGHT, p)
            // 순위 막대
            val by = h * 0.92f
            val bars = floatArrayOf(0.2f, 0.14f, 0.09f)
            val names = arrayOf("1등", "2등", "3등")
            for (k in 0 until 3) {
                val x = w * (0.3f + k * 0.2f)
                p.style = Paint.Style.FILL
                p.color = if (k == 0) Theme.GOLD else Theme.CARD_LIGHT
                rect.set(x - w * 0.07f, by - h * bars[k], x + w * 0.07f, by)
                c.drawRoundRect(rect, h * 0.02f, h * 0.02f, p)
                label(c, names[k], x, by - h * 0.025f, h * 0.06f, if (k == 0) Theme.INK else Theme.TEXT, p)
            }
        },
    )
}
