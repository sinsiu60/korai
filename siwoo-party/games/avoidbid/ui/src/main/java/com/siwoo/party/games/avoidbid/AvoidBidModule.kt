package com.siwoo.party.games.avoidbid

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.avoidbid.ui.AvoidBidActivity
import com.siwoo.party.games.avoidbid.ui.NumberArt

/** 피하기 경매: 모음집 등록 정보 */
object AvoidBidModule : GameModule {
    override val id = "avoidbid"
    override val name = "피하기 경매"
    override val tagline = "나쁜 카드는 칩을 내고 넘겨라! 칩이 떨어지면 끝장"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "10분"
    override val color = 0xFF2C8C7E.toInt()
    override val activityClass = AvoidBidActivity::class.java

    /** 기울인 숫자 카드 */
    private fun tilted(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, deg: Float, v: Int, p: Paint, dim: Boolean = false, head: Boolean = false) {
        c.save()
        c.rotate(deg, cx, cy)
        NumberArt.drawFace(c, cx - w / 2f, cy - h / 2f, w, h, v, p, dim = dim, head = head, detail = w > 60f && !dim)
        c.restore()
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val h = size * 0.6f
        val w = h * 0.7f
        tilted(canvas, cx - w * 0.42f, cy - h * 0.04f, w, h, -14f, 13, paint)
        tilted(canvas, cx + w * 0.28f, cy + h * 0.02f, w, h, 9f, 31, paint)
        // 칩 더미 + 날아드는 칩
        val r = size * 0.1f
        NumberArt.drawStack(canvas, cx + w * 0.78f, cy + h * 0.56f, r, 3, paint)
        NumberArt.drawCoin(canvas, cx - w * 0.92f, cy - h * 0.46f, r * 0.8f, paint)
    }

    // ── 규칙 그림 도우미 ──

    private fun label(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, p: Paint, center: Boolean = true) {
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = null
    }

    private fun pill(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, color: Int, text: String, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.shade(color, 0.6f)
        c.drawRoundRect(cx - w / 2f, cy - h / 2f + h * 0.1f, cx + w / 2f, cy + h / 2f + h * 0.1f, h * 0.3f, h * 0.3f, p)
        p.color = color
        c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, h * 0.3f, h * 0.3f, p)
        label(c, text, cx, cy + h * 0.17f, h * 0.44f, Theme.TEXT, p)
    }

    private fun arrow(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, s: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.25f
        p.color = color
        c.drawLine(x0, y0, x1, y1, p)
        val dx = x1 - x0
        val dy = y1 - y0
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val ux = dx / len
        val uy = dy / len
        c.drawLine(x1, y1, x1 - ux * s + uy * s * 0.6f, y1 - uy * s - ux * s * 0.6f, p)
        c.drawLine(x1, y1, x1 - ux * s - uy * s * 0.6f, y1 - uy * s + ux * s * 0.6f, p)
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("가져갈까, 넘길까?", "카드 3~35 중 9장은 몰래 빠지고 24장으로 해요. 각자 칩 11개로 시작.\n공개된 카드를 [가져가기] 하거나, 칩 1개를 카드 위에 올리고 [넘기기] 해요.") { c, w, h, p ->
            val ch = h * 0.66f
            val cw = ch * 0.7f
            val cx = w * 0.5f
            val cy = h * 0.44f
            NumberArt.drawFace(c, cx - cw / 2f, cy - ch / 2f, cw, ch, 27, p, detail = true)
            NumberArt.drawStack(c, cx, cy + ch * 0.42f, cw * 0.12f, 3, p)
            // 왼쪽: 가져가기 / 오른쪽: 칩 1개 넘기기
            pill(c, w * 0.17f, h * 0.86f, w * 0.26f, h * 0.15f, 0xFF2E9E5B.toInt(), "가져가기", p)
            pill(c, w * 0.83f, h * 0.86f, w * 0.26f, h * 0.15f, Theme.BLUE, "넘기기", p)
            arrow(c, cx - cw * 0.6f, cy, w * 0.2f, h * 0.62f, h * 0.06f, Theme.GREEN, p)
            // 오른쪽 위에서 칩이 카드로 날아옴
            NumberArt.drawCoin(c, w * 0.84f, h * 0.36f, cw * 0.13f, p)
            arrow(c, w * 0.8f, h * 0.3f, cx + cw * 0.55f, cy - ch * 0.05f, h * 0.06f, Theme.GOLD, p)
            label(c, "-1", w * 0.86f, h * 0.24f, h * 0.1f, Theme.GOLD_LIGHT, p)
        },
        RuleCard("칩까지 몽땅, 그리고 한 장 더", "가져가면 카드 위에 쌓인 칩도 함께 받고, 같은 사람이 다음 카드를 이어서 골라요.\n칩이 하나도 없으면 넘길 수 없어서 꼭 가져가야 해요.") { c, w, h, p ->
            // 왼쪽: 카드 + 칩 → 내 자리 (+4)
            val ch = h * 0.5f
            val cw = ch * 0.7f
            val cl = w * 0.05f
            val ct = h * 0.12f
            NumberArt.drawFace(c, cl, ct, cw, ch, 18, p, detail = true)
            NumberArt.drawStack(c, cl + cw / 2f, ct + ch * 0.92f, cw * 0.13f, 4, p)
            arrow(c, cl + cw + w * 0.02f, ct + ch * 0.5f, w * 0.33f, ct + ch * 0.5f, h * 0.05f, Theme.GOLD, p)
            p.color = Theme.CARD_LIGHT
            c.drawRoundRect(w * 0.35f, ct, w * 0.5f, ct + ch, h * 0.05f, h * 0.05f, p)
            p.color = Theme.PLAYER_COLORS[1]
            c.drawCircle(w * 0.425f, ct + ch * 0.2f, h * 0.05f, p)
            NumberArt.drawStack(c, w * 0.425f, ct + ch * 0.92f, h * 0.05f, 6, p)
            label(c, "+4칩", w * 0.425f, ct + ch * 0.5f, h * 0.08f, Theme.GOLD_LIGHT, p)
            label(c, "한 장 더!", w * 0.27f, h * 0.84f, h * 0.11f, Theme.GOLD, p)
            // 오른쪽: 칩 0 → 넘기기 막힘 → 꼭 가져가기
            p.color = Theme.CARD
            c.drawRoundRect(w * 0.58f, ct, w * 0.96f, ct + ch, h * 0.05f, h * 0.05f, p)
            p.color = Theme.PLAYER_COLORS[0]
            c.drawCircle(w * 0.64f, ct + ch * 0.22f, h * 0.05f, p)
            label(c, "칩 0개", w * 0.69f, ct + ch * 0.22f + h * 0.03f, h * 0.08f, Theme.TEXT_SUB, p, center = false)
            pill(c, w * 0.77f, ct + ch * 0.68f, w * 0.3f, h * 0.15f, Theme.GRAY, "넘기기", p)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.022f
            p.color = Theme.RED
            c.drawLine(w * 0.64f, ct + ch * 0.52f, w * 0.9f, ct + ch * 0.86f, p)
            p.style = Paint.Style.FILL
            pill(c, w * 0.77f, h * 0.84f, w * 0.34f, h * 0.15f, 0xFF2E9E5B.toInt(), "꼭 가져가기", p)
        },
        RuleCard("점수는 낮을수록 좋아요", "받은 카드 숫자의 합 - 남은 칩이 점수예요. 연속 숫자 묶음(12·13·14)은 가장 낮은 숫자(12)만 세요!\n24장을 다 가져가면 끝, 점수가 낮은 순으로 순위.") { c, w, h, p ->
            val ch = minOf(h * 0.42f, w * 0.25f)
            val cw = ch * 0.72f
            val top = h * 0.14f
            val left = w * 0.07f
            val stride = cw * 0.66f
            val runR = left + stride * 2 + cw
            // 금색 묶음 틀
            val m = h * 0.025f
            p.color = 0x40FFC93C
            c.drawRoundRect(left - m, top - m, runR + m, top + ch + m, cw * 0.2f, cw * 0.2f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            p.color = Theme.GOLD
            c.drawRoundRect(left - m, top - m, runR + m, top + ch + m, cw * 0.2f, cw * 0.2f, p)
            p.style = Paint.Style.FILL
            NumberArt.drawFace(c, left + stride * 2, top, cw, ch, 14, p, dim = true, numX = 0.67f, shadow = false)
            NumberArt.drawFace(c, left + stride, top, cw, ch, 13, p, dim = true, numX = 0.67f, shadow = false)
            NumberArt.drawFace(c, left, top, cw, ch, 12, p, head = true)
            // 낱장 20
            val sl = runR + cw * 0.35f
            NumberArt.drawFace(c, sl, top, cw, ch, 20, p)
            // 칩 5개
            val sx = sl + cw + cw * 0.6f
            NumberArt.drawStack(c, sx, top + ch, cw * 0.36f, 5, p)
            label(c, "칩 5", sx, top + ch + h * 0.1f, h * 0.08f, Theme.TEXT_SUB, p)
            // 식
            label(c, "12 + 20 - 5 = 27점", w * 0.5f, h * 0.84f, h * 0.12f, Theme.GOLD, p)
            label(c, "13·14 는 안 세요", left + stride + cw / 2f, top + ch + h * 0.1f, h * 0.07f, Theme.TEXT_SUB, p)
        },
    )
}
