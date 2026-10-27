package com.siwoo.party.games.charades

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PartyTag
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.charades.ui.CharadesActivity
import com.siwoo.party.games.charades.ui.CharadesArt
import com.siwoo.party.games.charades.ui.CharadesStore

/** 몸으로 말해요: 모음집 등록 정보 */
object CharadesModule : GameModule {
    override val id = "charades"
    override val name = "몸으로 말해요"
    override val tagline = "말은 금지! 설명자만 보고 몸짓으로 90초 동안 최대한 많이 맞혀라"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.PARTY
    override val partyTags = listOf(PartyTag.BODY)
    override val minutes = "7분"
    override val color = 0xFF2BB673.toInt()
    override val activityClass = CharadesActivity::class.java

    /** 허브 소개 팝업의 [제시어 편집] → 공통 ListEditor (카테고리 5탭, 앱 내부 저장) */
    override val settingsLabel: String get() = "제시어 편집"

    override fun showSettings(popups: PopupLayer) = CharadesStore.showEditor(popups)

    private val path = Path()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF4A1F7E.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.Party.NEON_PINK
        canvas.drawCircle(cx, cy, r * 0.9f, paint)
        paint.style = Paint.Style.FILL
        // 몸짓하는 사람 (두 팔 번쩍) + 말 금지 말풍선
        CharadesArt.drawActor(canvas, cx - r * 0.2f, cy + r * 0.12f, r * 1.25f, Theme.Party.NEON_SKY, 0, paint)
        CharadesArt.drawNoTalk(canvas, cx + r * 0.46f, cy - r * 0.4f, r * 0.3f, paint, path)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = color
        p.textSize = size
        p.isFakeBoldText = true
        p.textAlign = Paint.Align.CENTER
        c.drawText(s, x, y, p)
        p.isFakeBoldText = false
        p.textAlign = Paint.Align.LEFT
    }

    /** 규칙 그림: 둥근 버튼 */
    private fun button(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, dark: Int, label: String, p: Paint) {
        val rr = (b - t) * 0.3f
        p.style = Paint.Style.FILL
        p.color = dark
        c.drawRoundRect(l, t, r, b, rr, rr, p)
        p.color = color
        c.drawRoundRect(l, t, r, b - (b - t) * 0.14f, rr, rr, p)
        text(c, label, (l + r) / 2f, t + (b - t) * 0.6f, (b - t) * 0.42f, Theme.TEXT, p)
    }

    /** 규칙 그림: 사람 머리 동그라미 */
    private fun head(c: Canvas, x: Float, y: Float, r: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = color
        c.drawCircle(x, y, r, p)
        c.drawRoundRect(x - r * 1.2f, y + r * 1.1f, x + r * 1.2f, y + r * 2.4f, r * 0.6f, r * 0.6f, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("설명자만 보고, 몸으로만!", "차례인 사람에게 기기를 넘기면 그 사람만 제시어를 봐요.\n몸짓으로만 설명! 말·소리·입 모양·글자 쓰기는 금지.\n카테고리: 동물·운동·직업·행동·물건 (설정에서 단어 추가)") { c, w, h, p ->
            // 제시어 카드 (폰 화면)
            p.style = Paint.Style.FILL
            p.color = Theme.Party.BG_DEEP
            c.drawRoundRect(w * 0.05f, h * 0.22f, w * 0.38f, h * 0.7f, h * 0.06f, h * 0.06f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.02f
            p.color = Theme.Party.NEON_PINK
            c.drawRoundRect(w * 0.05f, h * 0.22f, w * 0.38f, h * 0.7f, h * 0.06f, h * 0.06f, p)
            text(c, "펭귄", w * 0.215f, h * 0.52f, h * 0.17f, Theme.TEXT, p)
            CharadesArt.drawActor(c, w * 0.58f, h * 0.55f, h * 0.7f, Theme.Party.NEON_SKY, 0, p)
            CharadesArt.drawNoTalk(c, w * 0.84f, h * 0.3f, h * 0.15f, p, path)
        },
        RuleCard("90초 · [정답] [패스]", "맞히면 [정답] → 화면이 초록으로 번쩍, 바로 다음 단어!\n모르겠으면 [패스] (한 차례에 ${GameConfig.MAX_PASSES}번까지).\n시간이 끝나면 맞힌 단어·패스한 단어를 모두 공개해요.") { c, w, h, p ->
            CharadesArt.drawWatch(c, w * 0.14f, h * 0.36f, h * 0.15f, 0.65f, p)
            text(c, "90초", w * 0.14f, h * 0.68f, h * 0.12f, Theme.GOLD, p)
            // 초록 번쩍 화면
            p.style = Paint.Style.FILL
            p.color = CharadesArt.CORRECT
            c.drawRoundRect(w * 0.3f, h * 0.12f, w * 0.95f, h * 0.5f, h * 0.05f, h * 0.05f, p)
            CharadesArt.drawCheck(c, w * 0.42f, h * 0.31f, h * 0.18f, Theme.TEXT, p)
            text(c, "정답!", w * 0.68f, h * 0.37f, h * 0.17f, Theme.TEXT, p)
            button(c, w * 0.3f, h * 0.62f, w * 0.56f, h * 0.86f, CharadesArt.PASS, CharadesArt.PASS_DARK, "패스 (3)", p)
            button(c, w * 0.6f, h * 0.62f, w * 0.95f, h * 0.86f, CharadesArt.CORRECT, CharadesArt.CORRECT_DARK, "정답!", p)
        },
        RuleCard("4인 팀전 · 3인 개인전", "4인: 무작위 2 대 2 팀! A1 → B1 → A2 → B2 순서로 모두 한 번씩 설명, 팀원이 맞히면 팀 +1. 같은 팀은 같은 순위.\n3인: 개인전. 먼저 맞힌 사람 +1, 설명한 사람도 +1.\n합계 점수로 순위, 같으면 같은 등수.") { c, w, h, p ->
            // 팀 상자 두 개
            for (s in 0..1) {
                val l = if (s == 0) w * 0.03f else w * 0.39f
                p.style = Paint.Style.STROKE
                p.strokeWidth = h * 0.02f
                p.color = CharadesArt.TEAM_COLORS[s]
                c.drawRoundRect(l, h * 0.14f, l + w * 0.25f, h * 0.84f, h * 0.06f, h * 0.06f, p)
                text(c, CharadesArt.TEAM_NAMES[s], l + w * 0.125f, h * 0.3f, h * 0.12f, CharadesArt.TEAM_COLORS[s], p)
                head(c, l + w * 0.068f, h * 0.48f, h * 0.065f, Theme.PLAYER_COLORS[s * 2], p)
                head(c, l + w * 0.182f, h * 0.48f, h * 0.065f, Theme.PLAYER_COLORS[s * 2 + 1], p)
            }
            text(c, "VS", w * 0.335f, h * 0.53f, h * 0.09f, Theme.GOLD, p)
            // 개인전: 맞힌 사람 +1, 설명자 +1
            head(c, w * 0.75f, h * 0.36f, h * 0.07f, Theme.PLAYER_COLORS[2], p)
            head(c, w * 0.9f, h * 0.36f, h * 0.07f, Theme.PLAYER_COLORS[3], p)
            text(c, "+1", w * 0.75f, h * 0.76f, h * 0.15f, Theme.GOLD, p)
            text(c, "+1", w * 0.9f, h * 0.76f, h * 0.15f, Theme.GOLD, p)
            text(c, "3인", w * 0.825f, h * 0.2f, h * 0.1f, Theme.Party.TEXT_SUB, p)
        },
    )
}
