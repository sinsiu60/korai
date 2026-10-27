package com.siwoo.party.games.onecard

import android.graphics.Canvas
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
import com.siwoo.party.games.onecard.ui.OneCardActivity
import com.siwoo.party.games.onecard.ui.SuitArt

/** 원카드: 모음집 등록 정보 */
object OneCardModule : GameModule {
    override val id = "onecard"
    override val name = "원카드"
    override val tagline = "같은 무늬·같은 숫자! 공격 카드로 몰아붙이고 먼저 다 내기"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "10분"
    override val color = 0xFFC62840.toInt()
    override val activityClass = OneCardActivity::class.java

    private val rect = RectF()
    private val path = Path()
    private const val BACK = 0xFF1B2766.toInt()
    private const val PAPER_EDGE = 0xFFB8A77A.toInt()
    private const val OK = 0xFF2ECC71.toInt()

    // ── 그림 도우미 (할당 없음) ──

    /** 종이 카드 한 장: 가운데 (cx, cy), 폭 w (높이 1.4w), rot 도. label 은 왼쪽 위 글자, suit 가 null 이면 별(조커) */
    private fun card(c: Canvas, cx: Float, cy: Float, w: Float, rot: Float, label: String, suit: Suit?, red: Boolean, p: Paint) {
        val h = w * 1.4f
        c.save()
        c.rotate(rot, cx, cy)
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        rect.set(cx - w / 2f + w * 0.04f, cy - h / 2f + w * 0.07f, cx + w / 2f + w * 0.04f, cy + h / 2f + w * 0.07f)
        c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        p.color = Theme.PAPER
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.03f
        p.color = PAPER_EDGE
        c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        p.style = Paint.Style.FILL
        val ink = if (red) SuitArt.RED else SuitArt.BLACK
        p.color = ink
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.LEFT
        p.textSize = w * (if (label.length > 2) 0.2f else 0.32f)
        c.drawText(label, cx - w * 0.4f, cy - h / 2f + w * 0.36f, p)
        if (suit != null) SuitArt.draw(c, suit, cx, cy + h * 0.06f, w * 0.52f, p)
        else {
            p.color = Theme.GOLD_DARK
            SuitArt.drawStar(c, cx, cy + h * 0.08f, w * 0.62f, p)
        }
        c.restore()
    }

    /** 뒷면 카드 */
    private fun back(c: Canvas, cx: Float, cy: Float, w: Float, rot: Float, p: Paint) {
        val h = w * 1.4f
        c.save()
        c.rotate(rot, cx, cy)
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        rect.set(cx - w / 2f + w * 0.04f, cy - h / 2f + w * 0.07f, cx + w / 2f + w * 0.04f, cy + h / 2f + w * 0.07f)
        c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        p.color = Theme.PAPER
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        p.color = BACK
        rect.inset(w * 0.07f, w * 0.07f)
        c.drawRoundRect(rect, w * 0.06f, w * 0.06f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.035f
        p.color = Theme.GOLD
        rect.inset(w * 0.07f, w * 0.07f)
        c.drawRoundRect(rect, w * 0.04f, w * 0.04f, p)
        p.style = Paint.Style.FILL
        SuitArt.drawStar(c, cx, cy, w * 0.4f, p)
        c.restore()
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

    private fun check(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = OK
        c.drawCircle(x, y, s, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.3f
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFFFFFFFF.toInt()
        path.reset()
        path.moveTo(x - s * 0.45f, y)
        path.lineTo(x - s * 0.1f, y + s * 0.38f)
        path.lineTo(x + s * 0.48f, y - s * 0.35f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun cross(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.RED
        c.drawCircle(x, y, s, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.3f
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFFFFFFFF.toInt()
        val d = s * 0.4f
        c.drawLine(x - d, y - d, x + d, y + d, p)
        c.drawLine(x + d, y - d, x - d, y + d, p)
        p.style = Paint.Style.FILL
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val w = size * 0.42f
        back(canvas, cx - size * 0.13f, cy + size * 0.02f, w, -16f, paint)
        card(canvas, cx + size * 0.1f, cy + size * 0.02f, w, 12f, "A", Suit.HEART, true, paint)
        // 금색 "1" 배지
        val bx = cx + size * 0.3f
        val by = cy - size * 0.28f
        val r = size * 0.14f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(bx + r * 0.08f, by + r * 0.14f, r, paint)
        paint.color = Theme.GOLD
        canvas.drawCircle(bx, by, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.14f
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(bx, by, r * 0.84f, paint)
        label(canvas, "1", bx, by + r * 0.4f, r * 1.15f, Theme.INK, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard(
            "같은 무늬·같은 숫자로 내기",
            "1인당 7장. 맨 위 카드와 무늬나 숫자가 같으면 낼 수 있어요. 조커는 언제나!\n낼 카드가 없으면 1장 뽑고 끝. 먼저 다 내면 1등.",
        ) { c, w, h, p ->
            val cw = h * 0.36f
            card(c, w * 0.2f, h * 0.48f, cw * 1.2f, -4f, "5", Suit.HEART, true, p)
            label(c, "맨 위", w * 0.2f, h * 0.93f, h * 0.08f, Theme.GOLD, p)
            card(c, w * 0.44f, h * 0.44f, cw * 0.72f, 0f, "9", Suit.HEART, true, p)
            card(c, w * 0.59f, h * 0.44f, cw * 0.72f, 0f, "5", Suit.SPADE, false, p)
            card(c, w * 0.74f, h * 0.44f, cw * 0.72f, 0f, "JOKER", null, true, p)
            card(c, w * 0.89f, h * 0.44f, cw * 0.72f, 0f, "4", Suit.CLUB, false, p)
            check(c, w * 0.44f, h * 0.84f, h * 0.06f, p)
            check(c, w * 0.59f, h * 0.84f, h * 0.06f, p)
            check(c, w * 0.74f, h * 0.84f, h * 0.06f, p)
            cross(c, w * 0.89f, h * 0.84f, h * 0.06f, p)
        },
        RuleCard(
            "공격 카드와 특수 카드",
            "A +3 · 2 +2 · 조커 +5 공격! 같거나 센 공격 카드(2<A<조커, 무늬나 숫자 맞춤)로 넘기면 쌓이고, 못 넘기면 다 받아요.\nJ 건너뛰기 · Q 방향 바꾸기 · K 한 번 더 · 7 무늬 바꾸기.",
        ) { c, w, h, p ->
            val cw = h * 0.22f
            val y1 = h * 0.24f
            card(c, w * 0.2f, y1, cw, -6f, "A", Suit.SPADE, false, p)
            card(c, w * 0.5f, y1, cw, 0f, "2", Suit.HEART, true, p)
            card(c, w * 0.8f, y1, cw, 6f, "JOKER", null, true, p)
            label(c, "+3", w * 0.2f, h * 0.53f, h * 0.1f, Theme.RED, p)
            label(c, "+2", w * 0.5f, h * 0.53f, h * 0.1f, Theme.RED, p)
            label(c, "+5", w * 0.8f, h * 0.53f, h * 0.1f, Theme.RED, p)
            val y2 = h * 0.73f
            val sw = cw * 0.8f
            card(c, w * 0.13f, y2, sw, 0f, "J", Suit.CLUB, false, p)
            card(c, w * 0.38f, y2, sw, 0f, "Q", Suit.DIAMOND, true, p)
            card(c, w * 0.63f, y2, sw, 0f, "K", Suit.SPADE, false, p)
            card(c, w * 0.88f, y2, sw, 0f, "7", Suit.HEART, true, p)
            val ty = h * 0.97f
            val ts = h * 0.065f
            label(c, "건너뛰기", w * 0.13f, ty, ts, Theme.GOLD_LIGHT, p)
            label(c, "방향", w * 0.38f, ty, ts, Theme.GOLD_LIGHT, p)
            label(c, "한 번 더", w * 0.63f, ty, ts, Theme.GOLD_LIGHT, p)
            label(c, "무늬", w * 0.88f, ty, ts, Theme.GOLD_LIGHT, p)
        },
        RuleCard(
            "1장 남으면 원카드!",
            "카드를 내고 1장 남으면 3초 안에 [원카드!]. 늦으면 1장 더!\n차례마다 기기를 받아 나만 보기 — 떠 있는 카드를 눌러 고르고 한 번 더 누르면 내요.",
        ) { c, w, h, p ->
            card(c, w * 0.22f, h * 0.5f, h * 0.38f, -8f, "K", Suit.DIAMOND, true, p)
            // 금색 [원카드!] 버튼
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD_DARK
            rect.set(w * 0.44f, h * 0.24f, w * 0.94f, h * 0.56f)
            c.drawRoundRect(rect, h * 0.08f, h * 0.08f, p)
            p.color = Theme.GOLD
            rect.set(w * 0.44f, h * 0.2f, w * 0.94f, h * 0.51f)
            c.drawRoundRect(rect, h * 0.08f, h * 0.08f, p)
            label(c, "원카드!", w * 0.69f, h * 0.41f, h * 0.15f, Theme.INK, p)
            // 3초 막대
            p.color = 0x55FFFFFF
            rect.set(w * 0.44f, h * 0.64f, w * 0.94f, h * 0.72f)
            c.drawRoundRect(rect, h * 0.04f, h * 0.04f, p)
            p.color = Theme.RED
            rect.set(w * 0.44f, h * 0.64f, w * 0.58f, h * 0.72f)
            c.drawRoundRect(rect, h * 0.04f, h * 0.04f, p)
            label(c, "3초!   늦으면 +1장", w * 0.69f, h * 0.88f, h * 0.085f, Theme.GOLD_LIGHT, p)
        },
    )
}
