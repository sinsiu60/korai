package com.siwoo.party.games.blackjack

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.blackjack.ui.BlackjackActivity
import com.siwoo.party.games.blackjack.ui.ChipArt

/** 블랙잭: 모음집 등록 정보 */
object BlackjackModule : GameModule {
    override val id = "blackjack"
    override val name = "블랙잭"
    override val tagline = "딜러를 이겨라! 21에 가깝게, 넘으면 버스트"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "8분"
    override val color = 0xFF17804B.toInt()
    override val activityClass = BlackjackActivity::class.java

    private const val PAPER = 0xFFFFFBF0.toInt()
    private const val RED = 0xFFD0202E.toInt()
    private const val BLACK = 0xFF161A2C.toInt()
    private const val BACK = 0xFF1B2766.toInt()
    private const val FELT = 0xFF17804B.toInt()
    private const val FELT_DARK = 0xFF0E5A34.toInt()

    private const val SPADE = 0
    private const val HEART = 1
    private const val DIAMOND = 2

    private val path = Path()
    private val rect = RectF()

    /** 무늬 하나 (cx, cy) 가운데, 크기 s */
    private fun suit(c: Canvas, kind: Int, cx: Float, cy: Float, s: Float, p: Paint) {
        path.reset()
        when (kind) {
            HEART -> {
                path.moveTo(cx, cy + s * 0.45f)
                path.cubicTo(cx - s * 0.95f, cy - s * 0.12f, cx - s * 0.38f, cy - s * 0.8f, cx, cy - s * 0.25f)
                path.cubicTo(cx + s * 0.38f, cy - s * 0.8f, cx + s * 0.95f, cy - s * 0.12f, cx, cy + s * 0.45f)
            }
            SPADE -> {
                path.moveTo(cx, cy - s * 0.5f)
                path.cubicTo(cx - s * 0.95f, cy + s * 0.08f, cx - s * 0.38f, cy + s * 0.6f, cx, cy + s * 0.15f)
                path.cubicTo(cx + s * 0.38f, cy + s * 0.6f, cx + s * 0.95f, cy + s * 0.08f, cx, cy - s * 0.5f)
                path.moveTo(cx, cy + s * 0.05f)
                path.lineTo(cx - s * 0.2f, cy + s * 0.5f)
                path.lineTo(cx + s * 0.2f, cy + s * 0.5f)
                path.close()
            }
            else -> {
                path.moveTo(cx, cy - s * 0.5f)
                path.lineTo(cx + s * 0.36f, cy)
                path.lineTo(cx, cy + s * 0.5f)
                path.lineTo(cx - s * 0.36f, cy)
                path.close()
            }
        }
        c.drawPath(path, p)
    }

    /**
     * 카드 한 장 (가운데 cx, cy, 폭 w, 회전 rot). faceUp 이면 왼쪽 위 글자 + 가운데 큰 무늬, 아니면 남색 뒷면.
     */
    private fun card(c: Canvas, cx: Float, cy: Float, w: Float, rot: Float, label: String, kind: Int, faceUp: Boolean, p: Paint) {
        val h = w * 1.4f
        val r = w * 0.09f
        c.save()
        c.translate(cx, cy)
        c.rotate(rot)
        rect.set(-w / 2f + w * 0.04f, -h / 2f + w * 0.07f, w / 2f + w * 0.04f, h / 2f + w * 0.07f)
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRoundRect(rect, r, r, p)
        rect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        p.color = if (faceUp) PAPER else BACK
        c.drawRoundRect(rect, r, r, p)
        if (faceUp) {
            val col = if (kind == SPADE) BLACK else RED
            p.color = col
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = w * 0.34f
            c.drawText(label, -w * 0.28f, -h * 0.27f, p)
            suit(c, kind, -w * 0.28f, -h * 0.13f, w * 0.2f, p)
            suit(c, kind, w * 0.06f, h * 0.12f, w * 0.55f, p)
        } else {
            p.style = Paint.Style.STROKE
            p.strokeWidth = w * 0.04f
            p.color = Theme.GOLD
            rect.inset(w * 0.1f, w * 0.1f)
            c.drawRoundRect(rect, r * 0.6f, r * 0.6f, p)
            p.style = Paint.Style.FILL
            suit(c, DIAMOND, 0f, 0f, w * 0.4f, p)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.025f
        p.color = 0x33000000
        rect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        c.drawRoundRect(rect, r, r, p)
        p.style = Paint.Style.FILL
        c.restore()
    }

    /** 칩 더미 (denom 번호 count 개) */
    private fun chips(c: Canvas, cx: Float, base: Float, rx: Float, denom: Int, count: Int, p: Paint) {
        val th = rx * 0.26f
        for (k in 0 until count) ChipArt.draw(c, cx, base - k * th, rx, th, denom, p)
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

    /** 초록 펠트 반원 (규칙 그림 바닥) */
    private fun felt(c: Canvas, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = FELT_DARK
        rect.set(-w * 0.2f, -h * 0.9f, w * 1.2f, h * 0.62f)
        c.drawOval(rect, p)
        p.color = FELT
        rect.set(-w * 0.2f, -h * 0.9f, w * 1.2f, h * 0.58f)
        c.drawOval(rect, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val s = size
        // 둥근 펠트 판
        paint.style = Paint.Style.FILL
        paint.color = 0x44000000
        canvas.drawCircle(cx + s * 0.02f, cy + s * 0.03f, s * 0.42f, paint)
        paint.color = FELT
        canvas.drawCircle(cx, cy, s * 0.42f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.03f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, s * 0.395f, paint)
        paint.style = Paint.Style.FILL
        // A♠ + K♥ 부채꼴
        card(canvas, cx - s * 0.1f, cy - s * 0.04f, s * 0.3f, -14f, "A", SPADE, true, paint)
        card(canvas, cx + s * 0.09f, cy - s * 0.02f, s * 0.3f, 12f, "K", HEART, true, paint)
        // 칩 더미
        chips(canvas, cx + s * 0.2f, cy + s * 0.25f, s * 0.11f, 0, 3, paint)
        chips(canvas, cx - s * 0.17f, cy + s * 0.27f, s * 0.1f, 1, 2, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("칩을 걸고 21에 가깝게", "시작 칩 100개. 라운드마다 10·20·50 중에서 걸어요 (칩이 10개보다 적으면 전부).\n" +
            "카드 합이 21에 가까울수록 좋아요. J·Q·K는 10, A는 1 또는 11 (\"7/17\"처럼 둘 다 보여 줘요).") { c, w, h, p ->
            felt(c, w, h, p)
            card(c, w * 0.32f, h * 0.36f, h * 0.32f, -8f, "A", SPADE, true, p)
            card(c, w * 0.5f, h * 0.38f, h * 0.32f, 6f, "6", HEART, true, p)
            pill(c, w * 0.41f, h * 0.76f, w * 0.26f, h * 0.15f, 0xFF0B1235.toInt(), "7/17", p)
            chips(c, w * 0.8f, h * 0.82f, h * 0.1f, 0, 2, p)
            chips(c, w * 0.8f, h * 0.82f - h * 0.052f, h * 0.1f, 1, 3, p)
            p.color = Theme.GOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.1f
            c.drawText("10 · 20 · 50", w * 0.8f, h * 0.5f, p)
        },
        RuleCard("히트 · 스탠드 · 더블", "내 차례에 [히트] 한 장 더, [스탠드] 여기서 멈춤.\n" +
            "[더블] 처음 두 장일 때 베팅을 두 배로 하고 딱 한 장만 더 받아요.\n21을 넘으면 버스트 — 바로 패배!") { c, w, h, p ->
            felt(c, w, h, p)
            card(c, w * 0.2f, h * 0.34f, h * 0.32f, -6f, "9", DIAMOND, true, p)
            card(c, w * 0.34f, h * 0.34f, h * 0.32f, 0f, "7", SPADE, true, p)
            card(c, w * 0.48f, h * 0.35f, h * 0.32f, 7f, "8", HEART, true, p)
            // 버스트 도장
            c.save()
            c.translate(w * 0.34f, h * 0.62f)
            c.rotate(-8f)
            rect.set(-w * 0.17f, -h * 0.08f, w * 0.17f, h * 0.08f)
            p.style = Paint.Style.FILL
            p.color = 0xEE101634.toInt()
            c.drawRoundRect(rect, h * 0.03f, h * 0.03f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.018f
            p.color = Theme.RED
            c.drawRoundRect(rect, h * 0.03f, h * 0.03f, p)
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = h * 0.09f
            c.drawText("버스트 24", 0f, h * 0.032f, p)
            c.restore()
            pill(c, w * 0.8f, h * 0.24f, w * 0.27f, h * 0.15f, 0xFF22A35A.toInt(), "히트", p)
            pill(c, w * 0.8f, h * 0.5f, w * 0.27f, h * 0.15f, 0xFFD8434F.toInt(), "스탠드", p)
            pill(c, w * 0.8f, h * 0.76f, w * 0.27f, h * 0.15f, 0xFF8E5BD8.toInt(), "더블", p)
        },
        RuleCard("딜러를 이겨라!", "딜러는 엎어 둔 카드를 공개하고 17 이상이 될 때까지 뽑아요.\n" +
            "딜러보다 높으면 +베팅, 블랙잭(첫 두 장 21)은 1.5배! 5라운드 뒤 칩이 많은 순서로 순위. 칩이 0이면 관전.") { c, w, h, p ->
            felt(c, w, h, p)
            card(c, w * 0.3f, h * 0.3f, h * 0.28f, -5f, "K", SPADE, true, p)
            card(c, w * 0.45f, h * 0.31f, h * 0.28f, 5f, "", 0, false, p)
            pill(c, w * 0.75f, h * 0.27f, w * 0.24f, h * 0.14f, 0xFF33406E.toInt(), "10 + ?", p)
            // 플레이어 블랙잭
            card(c, w * 0.3f, h * 0.76f, h * 0.26f, -6f, "A", HEART, true, p)
            card(c, w * 0.43f, h * 0.77f, h * 0.26f, 6f, "Q", SPADE, true, p)
            pill(c, w * 0.75f, h * 0.76f, w * 0.3f, h * 0.15f, Theme.GOLD_DARK, "블랙잭 ×1.5", p)
        },
    )
}
