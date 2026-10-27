package com.siwoo.party.games.chosung

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PartyTag
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.chosung.ui.ChosungActivity
import com.siwoo.party.games.chosung.ui.ChosungArt
import com.siwoo.party.games.chosung.ui.ChosungWordStore

/** 초성 퀴즈: 모음집 등록 정보 */
object ChosungModule : GameModule {
    override val id = "chosung"
    override val name = "초성 퀴즈"
    override val tagline = "ㄱㅊㅉㄱ? 아는 순간 버저! 말로 외쳐라"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.PARTY
    override val partyTags = listOf(PartyTag.TALK)
    override val minutes = "5분"
    override val color = 0xFF2E9BFF.toInt()
    override val activityClass = ChosungActivity::class.java

    /** 허브 소개 팝업의 [단어 편집] → 공통 ListEditor (카테고리 6탭, 앱 내부 저장) */
    override val settingsLabel: String get() = "단어 편집"

    override fun showSettings(popups: PopupLayer) = ChosungWordStore.showEditor(popups)

    private val rect = RectF()
    private const val T1 = "ㄱ"
    private const val T2 = "ㅊ"
    private const val T3 = "ㅉ"
    private const val CH = "ㅊ"
    private const val SS = "ㅅ"

    /** 초성 타일 (어두운 면 + 하늘색 테두리, gold 면 힌트) */
    private fun tile(c: Canvas, s: String, cx: Float, cy: Float, size: Float, gold: Boolean, p: Paint) {
        val r = size * 0.2f
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRoundRect(cx - size / 2f, cy - size / 2f + size * 0.06f, cx + size / 2f, cy + size / 2f + size * 0.06f, r, r, p)
        p.color = if (gold) Theme.GOLD else 0xFF1C0B3A.toInt()
        c.drawRoundRect(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f, r, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.05f
        p.color = if (gold) 0x88FFFFFF.toInt() else Theme.Party.NEON_SKY
        c.drawRoundRect(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f, r, r, p)
        text(c, s, cx, cy + size * 0.23f, size * 0.64f, if (gold) Theme.INK else 0xFFFFFFFF.toInt(), p)
    }

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
        // 버저 (아래) + 초성 타일 두 장 (위)
        ChosungArt.buzzer(canvas, cx, cy + r * 0.47f, r * 0.27f, ChosungArt.NO_RED, 0f, false, paint)
        tile(canvas, CH, cx - r * 0.33f, cy - r * 0.2f, r * 0.6f, false, paint)
        tile(canvas, SS, cx + r * 0.33f, cy - r * 0.2f, r * 0.6f, true, paint)
        paint.style = Paint.Style.FILL
    }

    /** 규칙 그림 바탕: 네온 판 */
    private fun board(c: Canvas, l: Float, t: Float, r: Float, b: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.Party.CARD
        c.drawRoundRect(l, t, r, b, h * 0.06f, h * 0.06f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.015f
        p.color = Theme.Party.NEON_PINK
        c.drawRoundRect(l, t, r, b, h * 0.06f, h * 0.06f, p)
        p.style = Paint.Style.FILL
    }

    private fun pill(c: Canvas, s: String, x: Float, y: Float, h: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRoundRect(x, y, x + h * 2.6f, y + h, h / 2f, h / 2f, p)
        text(c, s, x + h * 1.3f, y + h * 0.72f, h * 0.62f, Theme.INK, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("초성을 보고 단어를 떠올려요", "가운데 판에 카테고리와 초성이 나와요.\n예) 음식 · ㄱㅊㅉㄱ → 김치찌개\n3·4명이면 위·아래로 두 번 보여요.") { c, w, h, p ->
            board(c, w * 0.12f, h * 0.1f, w * 0.88f, h * 0.9f, h, p)
            pill(c, "음식", w * 0.17f, h * 0.17f, h * 0.12f, ChosungArt.CATEGORY_COLORS[0], p)
            text(c, "3 / 15", w * 0.8f, h * 0.26f, h * 0.08f, Theme.GOLD, p)
            val s = h * 0.2f
            for (k in 0 until 4) tile(c, if (k == 0 || k == 3) T1 else if (k == 1) T2 else T3, w * 0.5f + (k - 1.5f) * s * 1.15f, h * 0.53f, s, false, p)
            text(c, "아는 사람은 버저를 누르세요!", w * 0.5f, h * 0.82f, h * 0.075f, Theme.Party.TEXT_SUB, p)
        },
        RuleCard("버저 → 말로 답 → 다른 사람이 판정", "버저(자기 구역)를 가장 먼저 누른 사람이 5초 안에 말로 답하고,\n나머지가 [O 정답] / [X 오답]으로 판정!\n정답 +1점, 5초 안에 판정이 없으면 오답.") { c, w, h, p ->
            ChosungArt.buzzer(c, w * 0.2f, h * 0.62f, h * 0.17f, Theme.PLAYER_COLORS[0], 1f, false, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.03f
            p.color = Theme.GOLD
            c.drawCircle(w * 0.2f, h * 0.62f, h * 0.23f, p)
            // 말풍선
            p.style = Paint.Style.FILL
            p.color = Theme.PAPER
            c.drawRoundRect(w * 0.06f, h * 0.08f, w * 0.44f, h * 0.3f, h * 0.08f, h * 0.08f, p)
            text(c, "김치찌개!", w * 0.25f, h * 0.23f, h * 0.1f, Theme.INK, p)
            // 판정 버튼
            rect.set(w * 0.52f, h * 0.3f, w * 0.72f, h * 0.72f)
            ChosungArt.slab(c, rect, ChosungArt.OK_GREEN, false, false, h * 0.03f, p)
            ChosungArt.ok(c, rect.centerX(), h * 0.46f, h * 0.11f, 0xFFFFFFFF.toInt(), p)
            text(c, "정답", rect.centerX(), h * 0.66f, h * 0.075f, 0xFFFFFFFF.toInt(), p)
            rect.set(w * 0.76f, h * 0.3f, w * 0.96f, h * 0.72f)
            ChosungArt.slab(c, rect, ChosungArt.NO_RED, false, false, h * 0.03f, p)
            ChosungArt.no(c, rect.centerX(), h * 0.46f, h * 0.11f, 0xFFFFFFFF.toInt(), p)
            text(c, "오답", rect.centerX(), h * 0.66f, h * 0.075f, 0xFFFFFFFF.toInt(), p)
            text(c, "+1", w * 0.44f, h * 0.66f, h * 0.13f, Theme.GOLD, p)
        },
        RuleCard("오답은 -1점·그 문제 잠금", "틀리면 -1점, 그 문제는 버저 잠금.\n아무도 못 맞히면 10초에 힌트(한 글자 공개),\n20초에 정답 공개. 15문제 뒤 점수 순!") { c, w, h, p ->
            // 잠긴 버저
            ChosungArt.buzzer(c, w * 0.17f, h * 0.42f, h * 0.15f, Theme.PLAYER_COLORS[1], 0f, true, p)
            text(c, "-1", w * 0.17f, h * 0.47f, h * 0.15f, Theme.RED, p)
            text(c, "잠금", w * 0.17f, h * 0.76f, h * 0.08f, Theme.Party.TEXT_SUB, p)
            // 힌트 타일
            val s = h * 0.15f
            tile(c, "김", w * 0.45f, h * 0.3f, s, true, p)
            tile(c, T2, w * 0.45f + s * 1.15f, h * 0.3f, s, false, p)
            tile(c, T3, w * 0.45f + s * 2.3f, h * 0.3f, s, false, p)
            tile(c, T1, w * 0.45f + s * 3.45f, h * 0.3f, s, false, p)
            // 시간 줄: 0 — 10초 힌트 — 20초 정답
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.035f
            p.color = Theme.Party.NEON_SKY
            c.drawLine(w * 0.4f, h * 0.62f, w * 0.88f, h * 0.62f, p)
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            c.drawCircle(w * 0.64f, h * 0.62f, h * 0.035f, p)
            p.color = Theme.RED
            c.drawCircle(w * 0.88f, h * 0.62f, h * 0.035f, p)
            text(c, "10초 힌트", w * 0.6f, h * 0.78f, h * 0.075f, Theme.GOLD, p)
            text(c, "20초 정답", w * 0.8f, h * 0.9f, h * 0.075f, Theme.RED, p)
        },
    )
}
