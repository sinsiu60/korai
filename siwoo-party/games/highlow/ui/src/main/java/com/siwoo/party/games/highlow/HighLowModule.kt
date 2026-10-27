package com.siwoo.party.games.highlow

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.highlow.ui.HighLowActivity
import com.siwoo.party.games.highlow.ui.HighLowArt

/** 하이로우: 모음집 등록 정보 */
object HighLowModule : GameModule {
    override val id = "highlow"
    override val name = "하이로우"
    override val tagline = "다음 카드는 높을까 낮을까? 맞힐수록 두 배!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "6분"
    override val color = 0xFF0F7C8C.toInt()
    override val activityClass = HighLowActivity::class.java

    private val rect = RectF()
    private val POTS = arrayOf("1", "2", "4", "8")

    /** 초록 펠트 반원 (규칙 그림 바닥) */
    private fun felt(c: Canvas, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = HighLowArt.FELT_DARK
        rect.set(-w * 0.2f, -h * 0.9f, w * 1.2f, h * 0.62f)
        c.drawOval(rect, p)
        p.color = HighLowArt.FELT
        rect.set(-w * 0.2f, -h * 0.9f, w * 1.2f, h * 0.58f)
        c.drawOval(rect, p)
    }

    /** 둥근 글자 단추 (규칙 그림용) */
    private fun pill(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, color: Int, label: String, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.shade(color, 0.6f)
        rect.set(cx - w / 2f, cy - h / 2f + h * 0.12f, cx + w / 2f, cy + h / 2f + h * 0.12f)
        c.drawRoundRect(rect, h / 2f, h / 2f, p)
        p.color = color
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        c.drawRoundRect(rect, h / 2f, h / 2f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = h * 0.5f
        c.drawText(label, cx, cy + p.textSize * 0.36f, p)
    }

    /** 글자 (가운데 정렬) */
    private fun label(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = color
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        c.drawText(text, x, y, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val s = size
        // 둥근 펠트 판
        paint.style = Paint.Style.FILL
        paint.color = 0x44000000
        canvas.drawCircle(cx + s * 0.02f, cy + s * 0.03f, s * 0.42f, paint)
        paint.color = HighLowArt.FELT
        canvas.drawCircle(cx, cy, s * 0.42f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.03f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, s * 0.395f, paint)
        paint.style = Paint.Style.FILL
        // 기준 카드 7♥ + 엎어진 다음 카드
        HighLowArt.card(canvas, cx - s * 0.16f, cy + s * 0.01f, s * 0.28f, -8f, "7", HighLowArt.HEART, true, paint)
        HighLowArt.card(canvas, cx + s * 0.04f, cy + s * 0.03f, s * 0.24f, 8f, "", 0, false, paint)
        // ▲ ▼
        HighLowArt.arrow(canvas, cx + s * 0.27f, cy - s * 0.1f, s * 0.15f, true, HighLowArt.HIGH, paint)
        HighLowArt.arrow(canvas, cx + s * 0.27f, cy + s * 0.13f, s * 0.15f, false, HighLowArt.LOW, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("높을까? 낮을까?", "내 차례가 되면 카드 1장이 공개돼요. 다음 카드가 더 높을지 낮을지 [높다 ▲] [낮다 ▼] 로 골라요.\n" +
            "A가 가장 낮고(1) K가 가장 높아요(13). 무늬는 상관없고 조커는 없어요. 같은 숫자가 나오면 실패!") { c, w, h, p ->
            felt(c, w, h, p)
            HighLowArt.card(c, w * 0.24f, h * 0.42f, h * 0.36f, -5f, "7", HighLowArt.SPADE, true, p)
            HighLowArt.card(c, w * 0.5f, h * 0.42f, h * 0.36f, 5f, "", 0, false, p)
            pill(c, w * 0.8f, h * 0.27f, w * 0.27f, h * 0.17f, HighLowArt.HIGH, "높다 ▲", p)
            pill(c, w * 0.8f, h * 0.55f, w * 0.27f, h * 0.17f, HighLowArt.LOW, "낮다 ▼", p)
            label(c, "A = 1  …  K = 13", w * 0.37f, h * 0.88f, h * 0.085f, Theme.GOLD, p)
            label(c, "같으면 실패", w * 0.8f, h * 0.82f, h * 0.075f, Theme.TEXT_SUB, p)
        },
        RuleCard("맞힐 때마다 두 배!", "맞히면 이번 차례 점수가 1 → 2 → 4 → 8 … 두 배로 불어나요. 방금 공개된 카드가 다음 기준 카드가 돼요.\n" +
            "한 번이라도 맞히면 [멈추고 저장] 으로 점수를 지킬 수 있어요. 아래 안내에 높은·낮은 숫자 가짓수가 나와요.") { c, w, h, p ->
            felt(c, w, h, p)
            for (k in 0 until 4) {
                val x = w * (0.16f + k * 0.22f)
                val base = h * 0.66f
                val rx = h * 0.085f
                HighLowArt.chipStack(c, x, base, rx, k + 1, p)
                label(c, POTS[k], x, base - (k + 1) * rx * 0.26f - rx * 0.75f, h * 0.13f, Theme.GOLD, p)
                if (k < 3) label(c, "→", x + w * 0.11f, base - h * 0.04f, h * 0.09f, 0xFFFFFFFF.toInt(), p)
            }
            pill(c, w * 0.5f, h * 0.86f, w * 0.42f, h * 0.15f, 0xFFC98A0B.toInt(), "멈추고 저장", p)
        },
        RuleCard("욕심내면 펑!", "틀리거나 같은 숫자가 나오면 \"펑!\" 저장 안 한 점수는 0점이 돼요 (저장한 점수는 안전).\n" +
            "1인당 5차례씩 돌아가며 하고, 저장한 점수 합이 많은 순서로 순위! 덱이 떨어지면 버린 카드를 다시 섞어요.") { c, w, h, p ->
            felt(c, w, h, p)
            HighLowArt.card(c, w * 0.2f, h * 0.4f, h * 0.32f, -6f, "9", HighLowArt.CLUB, true, p)
            HighLowArt.card(c, w * 0.38f, h * 0.42f, h * 0.32f, 6f, "9", HighLowArt.DIAMOND, true, p)
            HighLowArt.burst(c, w * 0.3f, h * 0.72f, h * 0.17f, h * 0.11f, 12, Theme.GOLD, p)
            HighLowArt.burst(c, w * 0.3f, h * 0.72f, h * 0.145f, h * 0.095f, 12, Theme.RED, p)
            label(c, "펑!", w * 0.3f, h * 0.755f, h * 0.1f, 0xFFFFFFFF.toInt(), p)
            // 저장 점수 → 순위
            HighLowArt.chipStack(c, w * 0.66f, h * 0.48f, h * 0.08f, 3, p)
            HighLowArt.chipStack(c, w * 0.82f, h * 0.48f, h * 0.08f, 5, p)
            label(c, "1위", w * 0.82f, h * 0.2f, h * 0.1f, Theme.GOLD, p)
            label(c, "5차례 × 인원", w * 0.74f, h * 0.72f, h * 0.075f, 0xFFFFFFFF.toInt(), p)
            label(c, "저장 합으로 순위", w * 0.74f, h * 0.84f, h * 0.075f, Theme.TEXT_SUB, p)
        },
    )
}
