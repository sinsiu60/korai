package com.siwoo.party.games.doubt

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
import com.siwoo.party.games.doubt.ui.DoubtActivity

/** 의심 카드: 모음집 등록 정보 */
object DoubtModule : GameModule {
    override val id = "doubt"
    override val name = "의심 카드"
    override val tagline = "A부터 K까지 순서대로 선언! 거짓말은 들키지 않게, 수상하면 [의심!]"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    /** [의심!] 은 먼저 누른 한 명만 받으므로 손가락 하나씩이면 충분 (동시 터치 필요 없음) */
    override val needsMultiTouch = 0
    override val minutes = "10분"
    override val color = 0xFF7A3FD1.toInt()
    override val activityClass = DoubtActivity::class.java

    private val rect = RectF()
    private val path = Path()
    private const val BACK = 0xFF1B2766.toInt()
    private const val PAPER_EDGE = 0xFFB8A77A.toInt()
    private const val BLACK_INK = 0xFF1A1F33.toInt()
    private const val RED_INK = 0xFFD3263A.toInt()
    private const val OK = 0xFF2ECC71.toInt()
    private val SEQ = arrayOf("5", "6", "7", "8", "9")

    // ── 그림 도우미 (할당 없음) ──

    /** 종이 카드 한 장 (앞면): 가운데 (cx, cy), 폭 w (높이 1.4w), rot 도, 큰 숫자 label + 무늬 글자 */
    private fun card(c: Canvas, cx: Float, cy: Float, w: Float, rot: Float, label: String, suit: String, red: Boolean, p: Paint) {
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
        p.color = if (red) RED_INK else BLACK_INK
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = w * 0.62f
        c.drawText(label, cx, cy + w * 0.1f, p)
        p.textSize = w * 0.36f
        c.drawText(suit, cx, cy + h * 0.38f, p)
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
        // 가운데 마름모
        p.style = Paint.Style.FILL
        path.reset()
        path.moveTo(cx, cy - w * 0.22f)
        path.lineTo(cx + w * 0.16f, cy)
        path.lineTo(cx, cy + w * 0.22f)
        path.lineTo(cx - w * 0.16f, cy)
        path.close()
        c.drawPath(path, p)
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

    /** 입체 빨간 [의심!] 버튼 */
    private fun doubtButton(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, gold: Boolean, p: Paint) {
        val r = h * 0.3f
        p.style = Paint.Style.FILL
        p.color = if (gold) Theme.GOLD_DARK else Theme.RED_DARK
        rect.set(cx - w / 2f, cy - h / 2f + h * 0.12f, cx + w / 2f, cy + h / 2f + h * 0.12f)
        c.drawRoundRect(rect, r, r, p)
        p.color = if (gold) Theme.GOLD else Theme.RED
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        c.drawRoundRect(rect, r, r, p)
        label(c, "의심!", cx, cy + h * 0.2f, h * 0.55f, if (gold) Theme.INK else 0xFFFFFFFF.toInt(), p)
    }

    private fun mark(c: Canvas, x: Float, y: Float, s: Float, ok: Boolean, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = if (ok) OK else Theme.RED
        c.drawCircle(x, y, s, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.3f
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFFFFFFFF.toInt()
        if (ok) {
            path.reset()
            path.moveTo(x - s * 0.45f, y)
            path.lineTo(x - s * 0.1f, y + s * 0.38f)
            path.lineTo(x + s * 0.48f, y - s * 0.35f)
            c.drawPath(path, p)
        } else {
            val d = s * 0.4f
            c.drawLine(x - d, y - d, x + d, y + d, p)
            c.drawLine(x + d, y - d, x - d, y + d, p)
        }
        p.style = Paint.Style.FILL
    }

    /** 말풍선 (꼬리는 아래 왼쪽) */
    private fun bubble(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = color
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        c.drawRoundRect(rect, h * 0.45f, h * 0.45f, p)
        path.reset()
        path.moveTo(cx - w * 0.25f, cy + h * 0.4f)
        path.lineTo(cx - w * 0.36f, cy + h * 0.85f)
        path.lineTo(cx - w * 0.06f, cy + h * 0.4f)
        path.close()
        c.drawPath(path, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val w = size * 0.36f
        back(canvas, cx - size * 0.2f, cy + size * 0.1f, w, -18f, paint)
        back(canvas, cx - size * 0.04f, cy + size * 0.06f, w, -4f, paint)
        card(canvas, cx + size * 0.14f, cy + size * 0.1f, w, 12f, "7", "♠", false, paint)
        // 빨간 "?!" 말풍선
        val bx = cx + size * 0.22f
        val by = cy - size * 0.27f
        bubble(canvas, bx, by, size * 0.36f, size * 0.24f, Theme.RED, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.02f
        paint.color = 0xFFFFFFFF.toInt()
        rect.set(bx - size * 0.18f, by - size * 0.12f, bx + size * 0.18f, by + size * 0.12f)
        canvas.drawRoundRect(rect, size * 0.108f, size * 0.108f, paint)
        label(canvas, "?!", bx, by + size * 0.07f, size * 0.2f, 0xFFFFFFFF.toInt(), paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard(
            "A → K 순서로 선언",
            "52장을 모두 나눠요. 선언할 숫자는 A → 2 → 3 … → K → 다시 A 로 정해져 있어요.\n내 차례엔 기기를 받아 나만 보고, 1~4장을 뒷면으로 내며 \"7 × 2장!\"",
        ) { c, w, h, p ->
            // 순서 칩
            val labels = SEQ
            val cw = w * 0.11f
            for (k in labels.indices) {
                val x = w * 0.2f + k * w * 0.15f
                val cur = k == 2
                p.style = Paint.Style.FILL
                p.color = if (cur) Theme.GOLD else Theme.CARD_LIGHT
                rect.set(x - cw / 2f, h * 0.06f, x + cw / 2f, h * 0.26f)
                c.drawRoundRect(rect, cw * 0.2f, cw * 0.2f, p)
                label(c, labels[k], x, h * 0.21f, h * 0.12f, if (cur) Theme.INK else Theme.TEXT_SUB, p)
                if (k < labels.size - 1) label(c, "›", x + w * 0.075f, h * 0.2f, h * 0.11f, Theme.GOLD_LIGHT, p)
            }
            // 뒷면 2장 + 선언 말풍선
            val bw = h * 0.25f
            back(c, w * 0.25f, h * 0.64f, bw, -10f, p)
            back(c, w * 0.34f, h * 0.62f, bw, 8f, p)
            bubble(c, w * 0.68f, h * 0.55f, w * 0.44f, h * 0.24f, Theme.PAPER, p)
            label(c, "7 × 2장!", w * 0.68f, h * 0.6f, h * 0.14f, Theme.INK, p)
            label(c, "다음 사람은 8, 그다음은 9 …", w * 0.5f, h * 0.95f, h * 0.075f, Theme.GOLD_LIGHT, p)
        },
        RuleCard(
            "거짓말도 OK — 대신 [의심!]",
            "다른 숫자를 섞어 내도 돼요. 낸 직후 3초 동안 다른 사람 구역마다 [의심!] 버튼이 떠요.\n먼저 누른 한 명이 의심하면 낸 카드를 공개!",
        ) { c, w, h, p ->
            // 구역 넷 (위 둘은 뒤집혀 있음)
            for (k in 0 until 4) {
                val left = if (k % 2 == 0) w * 0.04f else w * 0.52f
                val top = if (k < 2) h * 0.04f else h * 0.52f
                rect.set(left, top, left + w * 0.44f, top + h * 0.44f)
                p.style = Paint.Style.FILL
                p.color = Theme.playerColor(k)
                p.alpha = 70
                c.drawRoundRect(rect, h * 0.05f, h * 0.05f, p)
                p.alpha = 255
                p.style = Paint.Style.STROKE
                p.strokeWidth = h * 0.012f
                c.drawRoundRect(rect, h * 0.05f, h * 0.05f, p)
                val bx = rect.centerX()
                val by = rect.centerY()
                c.save()
                if (k < 2) c.rotate(180f, bx, by)
                if (k == 3) {
                    label(c, "기다려요…", bx, by + h * 0.04f, h * 0.085f, Theme.TEXT_SUB, p)
                } else {
                    doubtButton(c, bx, by - h * 0.02f, w * 0.26f, h * 0.15f, k == 2, p)
                    // 3초 막대
                    p.style = Paint.Style.FILL
                    p.color = 0x55FFFFFF
                    rect.set(bx - w * 0.13f, by + h * 0.11f, bx + w * 0.13f, by + h * 0.135f)
                    c.drawRoundRect(rect, h * 0.01f, h * 0.01f, p)
                    p.color = Theme.GOLD
                    rect.right = bx - w * 0.13f + w * 0.26f * 0.55f
                    c.drawRoundRect(rect, h * 0.01f, h * 0.01f, p)
                }
                c.restore()
            }
        },
        RuleCard(
            "들키면 더미를 몽땅",
            "거짓말이면 낸 사람이, 진짜였으면 의심한 사람이 더미 전체를 가져가요.\n먼저 다 비운 순서로 순위 — 마지막 카드도 들키면 무효! ${GameConfig.MAX_TURNS}차례가 지나면 카드가 적은 순서.",
        ) { c, w, h, p ->
            val cw = h * 0.27f
            card(c, w * 0.14f, h * 0.36f, cw, -4f, "7", "♥", true, p)
            card(c, w * 0.3f, h * 0.36f, cw, 0f, "7", "♠", false, p)
            card(c, w * 0.46f, h * 0.36f, cw, 4f, "J", "♦", true, p)
            mark(c, w * 0.14f, h * 0.71f, h * 0.06f, true, p)
            mark(c, w * 0.3f, h * 0.71f, h * 0.06f, true, p)
            mark(c, w * 0.46f, h * 0.71f, h * 0.06f, false, p)
            // 화살표 → 더미 묶음
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.03f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.GOLD
            c.drawLine(w * 0.57f, h * 0.4f, w * 0.7f, h * 0.4f, p)
            c.drawLine(w * 0.7f, h * 0.4f, w * 0.66f, h * 0.33f, p)
            c.drawLine(w * 0.7f, h * 0.4f, w * 0.66f, h * 0.47f, p)
            p.style = Paint.Style.FILL
            for (k in 0 until 4) back(c, w * 0.82f + k * w * 0.012f, h * 0.38f - k * h * 0.02f, cw * 0.8f, -8f + k * 5f, p)
            label(c, "거짓말!", w * 0.3f, h * 0.95f, h * 0.11f, Theme.RED, p)
            label(c, "몽땅 가져가기", w * 0.82f, h * 0.76f, h * 0.075f, Theme.GOLD_LIGHT, p)
        },
    )
}
