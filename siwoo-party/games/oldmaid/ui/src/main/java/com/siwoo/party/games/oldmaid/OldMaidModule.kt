package com.siwoo.party.games.oldmaid

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.oldmaid.ui.OldMaidActivity

/** 도둑잡기: 모음집 등록 정보 */
object OldMaidModule : GameModule {
    override val id = "oldmaid"
    override val name = "도둑잡기"
    override val tagline = "옆 사람 카드를 쏙! 조커를 끝까지 들면 도둑!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "7분"
    override val color = 0xFF8E2C6E.toInt()
    override val activityClass = OldMaidActivity::class.java

    private val path = Path()
    private const val PAPER = 0xFFFFFBF0.toInt()
    private const val CARD_RED = 0xFFD0202E.toInt()
    private const val CARD_BLACK = 0xFF161A2C.toInt()
    private const val BACK = 0xFF1B2766.toInt()
    private const val FELT = 0xFF17624F.toInt()
    private const val JOKER_PURPLE = 0xFF6A2C9E.toInt()

    /** 작은 카드 한 장: (cx, cy) 가운데, 폭 w, 회전 rot. label 이 "" 이면 뒷면 */
    private fun miniCard(c: Canvas, cx: Float, cy: Float, w: Float, rot: Float, label: String, red: Boolean, p: Paint) {
        val h = w * 1.4f
        c.save()
        c.rotate(rot, cx, cy)
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRoundRect(cx - w / 2f + w * 0.05f, cy - h / 2f + w * 0.08f, cx + w / 2f + w * 0.05f, cy + h / 2f + w * 0.08f, w * 0.1f, w * 0.1f, p)
        if (label.isEmpty()) {
            p.color = BACK
            c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, w * 0.1f, w * 0.1f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = w * 0.06f
            p.color = Theme.GOLD
            c.drawRoundRect(cx - w * 0.36f, cy - h * 0.4f, cx + w * 0.36f, cy + h * 0.4f, w * 0.06f, w * 0.06f, p)
            p.style = Paint.Style.FILL
            diamond(c, cx, cy, w * 0.18f, p)
        } else {
            p.color = PAPER
            c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, w * 0.1f, w * 0.1f, p)
            p.color = if (red) CARD_RED else CARD_BLACK
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = w * 0.5f
            c.drawText(label, cx, cy + p.textSize * 0.36f, p)
            p.textSize = w * 0.22f
            c.drawText(label, cx - w * 0.3f, cy - h * 0.32f, p)
        }
        c.restore()
    }

    private fun diamond(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        path.reset()
        path.moveTo(cx, cy - s)
        path.lineTo(cx + s * 0.7f, cy)
        path.lineTo(cx, cy + s)
        path.lineTo(cx - s * 0.7f, cy)
        path.close()
        c.drawPath(path, p)
    }

    /** 조커 카드 (보라 광대 모자 + 도둑 가면) */
    private fun jokerCard(c: Canvas, cx: Float, cy: Float, w: Float, rot: Float, p: Paint) {
        val h = w * 1.4f
        c.save()
        c.rotate(rot, cx, cy)
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRoundRect(cx - w / 2f + w * 0.05f, cy - h / 2f + w * 0.08f, cx + w / 2f + w * 0.05f, cy + h / 2f + w * 0.08f, w * 0.1f, w * 0.1f, p)
        p.color = PAPER
        c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, w * 0.1f, w * 0.1f, p)
        // 광대 모자 (세 갈래)
        val hy = cy - h * 0.12f
        path.reset()
        path.moveTo(cx - w * 0.3f, hy)
        path.lineTo(cx - w * 0.38f, hy - h * 0.26f)
        path.lineTo(cx - w * 0.1f, hy - h * 0.08f)
        path.lineTo(cx, hy - h * 0.32f)
        path.lineTo(cx + w * 0.1f, hy - h * 0.08f)
        path.lineTo(cx + w * 0.38f, hy - h * 0.26f)
        path.lineTo(cx + w * 0.3f, hy)
        path.close()
        p.color = JOKER_PURPLE
        c.drawPath(path, p)
        p.color = Theme.GOLD
        c.drawCircle(cx - w * 0.38f, hy - h * 0.26f, w * 0.06f, p)
        c.drawCircle(cx, hy - h * 0.32f, w * 0.06f, p)
        c.drawCircle(cx + w * 0.38f, hy - h * 0.26f, w * 0.06f, p)
        // 얼굴
        p.color = 0xFFFFE0C2.toInt()
        c.drawCircle(cx, cy + h * 0.06f, w * 0.27f, p)
        // 도둑 가면
        p.color = CARD_BLACK
        c.drawRoundRect(cx - w * 0.3f, cy - h * 0.02f, cx + w * 0.3f, cy + h * 0.08f, w * 0.06f, w * 0.06f, p)
        p.color = PAPER
        c.drawCircle(cx - w * 0.12f, cy + h * 0.03f, w * 0.05f, p)
        c.drawCircle(cx + w * 0.12f, cy + h * 0.03f, w * 0.05f, p)
        // 웃는 입
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.04f
        p.color = CARD_RED
        c.drawArc(cx - w * 0.1f, cy + h * 0.08f, cx + w * 0.1f, cy + h * 0.17f, 10f, 160f, false, p)
        p.style = Paint.Style.FILL
        c.restore()
    }

    /** 빨간 "도둑!" 도장 */
    private fun stamp(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        c.save()
        c.rotate(-14f, cx, cy)
        p.style = Paint.Style.STROKE
        p.color = Theme.RED_DARK
        p.strokeWidth = r * 0.1f
        c.drawCircle(cx, cy, r, p)
        p.strokeWidth = r * 0.04f
        c.drawCircle(cx, cy, r * 0.84f, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = r * 0.5f
        c.drawText("도둑!", cx, cy + p.textSize * 0.36f, p)
        c.restore()
    }

    private fun felt(c: Canvas, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = FELT
        c.drawRoundRect(w * 0.04f, h * 0.06f, w * 0.96f, h * 0.94f, h * 0.1f, h * 0.1f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.015f
        p.color = Theme.GOLD
        c.drawRoundRect(w * 0.04f, h * 0.06f, w * 0.96f, h * 0.94f, h * 0.1f, h * 0.1f, p)
        p.style = Paint.Style.FILL
    }

    private fun arrow(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, s: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.3f
        p.strokeCap = Paint.Cap.ROUND
        p.color = Theme.GOLD
        c.drawLine(x0, y0, x1, y1, p)
        val dx = x1 - x0
        val dy = y1 - y0
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        val ux = dx / len
        val uy = dy / len
        p.style = Paint.Style.FILL
        path.reset()
        path.moveTo(x1 + ux * s, y1 + uy * s)
        path.lineTo(x1 - uy * s, y1 + ux * s)
        path.lineTo(x1 + uy * s, y1 - ux * s)
        path.close()
        c.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val w = size * 0.42f
        miniCard(canvas, cx - size * 0.17f, cy + size * 0.04f, w, -16f, "7", true, paint)
        jokerCard(canvas, cx + size * 0.14f, cy, w, 12f, paint)
        stamp(canvas, cx + size * 0.2f, cy + size * 0.26f, size * 0.16f, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("짝은 바로 버려요", "52장 + 조커 1장을 모두 나눠 가져요.\n같은 숫자 두 장(짝)은 처음부터 가운데에 버려요.") { c, w, h, p ->
            felt(c, w, h, p)
            val cw = h * 0.3f
            miniCard(c, w * 0.2f, h * 0.5f, cw, -10f, "7", true, p)
            miniCard(c, w * 0.34f, h * 0.5f, cw, 8f, "7", false, p)
            arrow(c, w * 0.45f, h * 0.5f, w * 0.6f, h * 0.5f, h * 0.05f, p)
            for (k in 0 until 3) miniCard(c, w * 0.76f + k * w * 0.012f, h * 0.5f - k * h * 0.02f, cw, -12f + k * 10f, "", false, p)
            miniCard(c, w * 0.79f, h * 0.48f, cw, 6f, "K", false, p)
        },
        RuleCard("옆 사람 카드를 한 장 쏙!", "내 차례엔 다음 사람의 뒷면 카드 중 한 장을 골라 가져와요.\n짝이 생기면 바로 버려요. 내 카드는 [내 카드 보기]로 몰래 확인!") { c, w, h, p ->
            felt(c, w, h, p)
            val cw = h * 0.24f
            for (k in 0 until 6) {
                val off = k - 2.5f
                val lift = if (k == 3) h * 0.12f else 0f
                miniCard(c, w * 0.5f + off * cw * 0.6f, h * 0.36f - lift + kotlin.math.abs(off) * h * 0.015f, cw, off * 5f, "", false, p)
            }
            arrow(c, w * 0.55f, h * 0.48f, w * 0.55f, h * 0.68f, h * 0.045f, p)
            // 손가락 (동그라미)
            p.style = Paint.Style.FILL
            p.color = 0xFFFFE0C2.toInt()
            c.drawCircle(w * 0.5f + 0.5f * cw * 0.6f, h * 0.16f, h * 0.06f, p)
            miniCard(c, w * 0.42f, h * 0.8f, cw, -6f, "Q", true, p)
            miniCard(c, w * 0.6f, h * 0.8f, cw, 6f, "Q", false, p)
        },
        RuleCard("조커를 끝까지 들면 도둑!", "카드를 먼저 다 버린 사람부터 1등, 2등…\n마지막까지 조커를 든 사람이 꼴찌 \"도둑!\"이에요.") { c, w, h, p ->
            felt(c, w, h, p)
            jokerCard(c, w * 0.42f, h * 0.5f, h * 0.42f, -6f, p)
            stamp(c, w * 0.62f, h * 0.58f, h * 0.2f, p)
        },
    )
}
