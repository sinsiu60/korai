package com.siwoo.party.games.rps

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.rps.ui.HandArt
import com.siwoo.party.games.rps.ui.RpsActivity

/** 가위바위보 토너먼트: 모음집 등록 정보 */
object RpsModule : GameModule {
    override val id = "rps"
    override val name = "가위바위보 토너먼트"
    override val tagline = "\"보!\" 하는 순간 0.6초 — 최후의 1인은 누구?"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "2분"
    override val color = 0xFFE53935.toInt()
    override val activityClass = RpsActivity::class.java

    private val path = Path()
    private const val BTN = 0xFF2E4396.toInt()
    private val HAND_ORDER = arrayOf(Hand.SCISSORS, Hand.ROCK, Hand.PAPER)

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = color
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.1f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.9f, paint)
        paint.style = Paint.Style.FILL
        HandArt.draw(canvas, Hand.SCISSORS, cx, cy - r * 0.04f, r * 1.25f, paint)
    }

    /** 규칙 그림: 버튼 하나 (손 + 글자) */
    private fun button(c: Canvas, cx: Float, cy: Float, s: Float, hand: Hand, hot: Boolean, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawRoundRect(cx - s / 2f, cy - s / 2f + s * 0.05f, cx + s / 2f, cy + s / 2f + s * 0.05f, s * 0.16f, s * 0.16f, p)
        p.color = if (hot) 0xFF4361D8.toInt() else BTN
        c.drawRoundRect(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f, s * 0.16f, s * 0.16f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * (if (hot) 0.05f else 0.025f)
        p.color = if (hot) Theme.GOLD else 0x55FFFFFF
        c.drawRoundRect(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f, s * 0.16f, s * 0.16f, p)
        HandArt.draw(c, hand, cx, cy - s * 0.09f, s * 0.6f, p)
        p.style = Paint.Style.FILL
        p.color = if (hot) Theme.GOLD_LIGHT else Theme.TEXT
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = s * 0.17f
        c.drawText(hand.label, cx, cy + s * 0.42f, p)
        p.typeface = null
        p.textAlign = Paint.Align.LEFT
    }

    /** 규칙 그림: 공개 카드 */
    private fun card(c: Canvas, cx: Float, cy: Float, ch: Float, hand: Hand, border: Int, p: Paint) {
        val cw = ch * 0.8f
        p.style = Paint.Style.FILL
        p.color = 0x77000000
        c.drawRoundRect(cx - cw / 2f + cw * 0.03f, cy - ch / 2f + ch * 0.04f, cx + cw / 2f + cw * 0.03f, cy + ch / 2f + ch * 0.04f, cw * 0.12f, cw * 0.12f, p)
        p.color = Theme.PAPER
        c.drawRoundRect(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f, cw * 0.12f, cw * 0.12f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = cw * 0.04f
        p.color = border
        c.drawRoundRect(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f, cw * 0.12f, cw * 0.12f, p)
        HandArt.draw(c, hand, cx, cy - ch * 0.08f, ch * 0.62f, p)
        p.style = Paint.Style.FILL
        p.color = Theme.INK
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = ch * 0.13f
        c.drawText(hand.label, cx, cy + ch * 0.38f, p)
        p.typeface = null
        p.textAlign = Paint.Align.LEFT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("구역마다 버튼 세 개", "화면이 사람 수만큼 나뉘고, 구역마다 [가위] [바위] [보] 버튼이 있어요.\n\"가위… 바위…\" 구호가 나오는 동안은 손가락만 준비!") { c, w, h, p ->
            val s = minOf(w * 0.24f, h * 0.5f)
            for (k in 0 until 3) button(c, w / 2f + (k - 1) * s * 1.18f, h * 0.55f, s, HAND_ORDER[k], false, p)
            p.color = Theme.TEXT
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.12f
            c.drawText("가위… 바위…", w / 2f, h * 0.2f, p)
            p.typeface = null
            p.textAlign = Paint.Align.LEFT
        },
        RuleCard("\"보!\" 순간부터 0.6초!", "\"보!\"가 나오면 0.6초 동안만 버튼이 켜져요. 처음 누른 손이 내 손.\n\"보!\" 전에 누르거나(반칙) 0.6초 안에 못 누르면 그 판은 패배!") { c, w, h, p ->
            val s = minOf(w * 0.22f, h * 0.44f)
            for (k in 0 until 3) button(c, w / 2f + (k - 1) * s * 1.18f, h * 0.5f, s, HAND_ORDER[k], true, p)
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.15f
            c.drawText("보!", w / 2f, h * 0.2f, p)
            p.typeface = null
            p.textAlign = Paint.Align.LEFT
            // 줄어드는 0.6초 막대
            val bw = s * 3.36f
            val y = h * 0.86f
            p.color = 0x44FFFFFF
            c.drawRoundRect(w / 2f - bw / 2f, y - h * 0.025f, w / 2f + bw / 2f, y + h * 0.025f, h * 0.025f, h * 0.025f, p)
            p.color = Theme.GOLD
            c.drawRoundRect(w / 2f - bw / 2f, y - h * 0.025f, w / 2f + bw * 0.1f, y + h * 0.025f, h * 0.025f, h * 0.025f, p)
        },
        RuleCard("최후의 1인까지", "모든 카드가 동시에 뒤집혀요. 모두 같거나 세 가지가 다 나오면 다시!\n아니면 진 손(반칙 포함)은 탈락 — 함께 탈락하면 같은 순위. 마지막 한 명이 우승!") { c, w, h, p ->
            val ch = minOf(h * 0.6f, w * 0.36f)
            card(c, w * 0.26f, h * 0.56f, ch, Hand.ROCK, Theme.GOLD, p)
            card(c, w * 0.74f, h * 0.56f, ch, Hand.SCISSORS, Theme.PLAYER_COLORS[1], p)
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            Ui.drawCrown(c, w * 0.26f, h * 0.56f - ch * 0.47f, ch * 0.28f, path, p)
            // 탈락 도장
            c.save()
            c.rotate(-12f, w * 0.74f, h * 0.56f)
            val sw = ch * 0.8f
            val sh = ch * 0.3f
            p.color = 0xCC2A0710.toInt()
            c.drawRoundRect(w * 0.74f - sw / 2f, h * 0.56f - sh / 2f, w * 0.74f + sw / 2f, h * 0.56f + sh / 2f, sh * 0.2f, sh * 0.2f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = Theme.RED
            c.drawRoundRect(w * 0.74f - sw / 2f, h * 0.56f - sh / 2f, w * 0.74f + sw / 2f, h * 0.56f + sh / 2f, sh * 0.2f, sh * 0.2f, p)
            p.style = Paint.Style.FILL
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = sh * 0.5f
            c.drawText("탈락", w * 0.74f, h * 0.56f + p.textSize * 0.36f, p)
            p.typeface = null
            p.textAlign = Paint.Align.LEFT
            c.restore()
            p.color = Theme.TEXT
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.color = Theme.GOLD
            p.textSize = h * 0.13f
            c.drawText("VS", w * 0.5f, h * 0.6f, p)
            p.typeface = null
            p.textAlign = Paint.Align.LEFT
        },
    )
}
