package com.siwoo.party.games.liar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.PartyTag
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.liar.ui.LiarActivity
import com.siwoo.party.games.liar.ui.LiarArt
import com.siwoo.party.games.liar.ui.LiarWordStore

/** 라이어 게임: 모음집 등록 정보 */
object LiarModule : GameModule {
    override val id = "liar"
    override val name = "라이어 게임"
    override val tagline = "모두 같은 단어를 아는데 한 명만 모른다! 라이어를 찾아라"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.PARTY
    override val partyTags = listOf(PartyTag.TALK)
    override val minutes = "5분/판"
    override val color = 0xFF7B2FBE.toInt()
    override val activityClass = LiarActivity::class.java

    /** 허브 소개 팝업의 [제시어 편집] → 공통 ListEditor (카테고리 6탭, 앱 내부 저장) */
    override val settingsLabel: String get() = "제시어 편집"

    override fun showSettings(popups: PopupLayer) = LiarWordStore.showEditor(popups)

    private val path = Path()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.38f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF9C5BE0.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.1f
        paint.color = Theme.GOLD_LIGHT
        canvas.drawCircle(cx, cy, r * 0.86f, paint)
        paint.style = Paint.Style.FILL
        // 가면 쓴 얼굴 + 긴 코 (코가 원 안에 들어오게 왼쪽으로)
        LiarArt.drawLiarFace(canvas, cx - r * 0.24f, cy + r * 0.06f, r * 0.6f, paint, path)
        // 금색 물음표 (오른쪽 위)
        paint.color = Theme.GOLD
        paint.textSize = r * 0.44f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("?", cx + r * 0.56f, cy - r * 0.26f, paint)
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.LEFT
    }

    /** 규칙 그림: 말풍선 */
    private fun bubble(c: Canvas, x: Float, y: Float, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.PAPER
        c.drawRoundRect(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f, h * 0.35f, h * 0.35f, p)
        path.reset()
        path.moveTo(x - w * 0.1f, y + h / 2f - 1f)
        path.lineTo(x - w * 0.22f, y + h * 0.95f)
        path.lineTo(x + w * 0.08f, y + h / 2f - 1f)
        path.close()
        c.drawPath(path, p)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = color
        p.textSize = size
        p.isFakeBoldText = true
        p.textAlign = Paint.Align.CENTER
        c.drawText(s, x, y, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("카테고리 공개 → 몰래 제시어 확인", "카테고리를 모두에게 공개한 뒤, 한 명씩 화면을 꾹 누르는 동안만 제시어를 봐요.\n라이어 한 명만 \"당신은 라이어입니다\"!\n바보 모드: 라이어도 다른 단어를 받아 자기가 라이어인 줄 몰라요.") { c, w, h, p ->
            // 카테고리 카드
            p.style = Paint.Style.FILL
            p.color = Theme.PAPER
            c.drawRoundRect(w * 0.06f, h * 0.2f, w * 0.42f, h * 0.8f, h * 0.06f, h * 0.06f, p)
            p.color = LiarArt.categoryColor(0)
            c.drawRect(w * 0.06f, h * 0.27f, w * 0.42f, h * 0.36f, p)
            LiarArt.drawCategory(c, 0, w * 0.24f, h * 0.58f, h * 0.32f, LiarArt.categoryColor(0), p, path)
            // 제시어 카드 + 라이어 카드
            p.style = Paint.Style.FILL
            p.color = Theme.PAPER
            c.drawRoundRect(w * 0.5f, h * 0.12f, w * 0.94f, h * 0.44f, h * 0.05f, h * 0.05f, p)
            text(c, "피자", w * 0.72f, h * 0.34f, h * 0.15f, Theme.INK, p)
            p.color = LiarArt.LIAR_RED
            c.drawRoundRect(w * 0.5f, h * 0.54f, w * 0.94f, h * 0.88f, h * 0.05f, h * 0.05f, p)
            LiarArt.drawLiarFace(c, w * 0.6f, h * 0.71f, h * 0.1f, p, path)
            text(c, "라이어", w * 0.8f, h * 0.76f, h * 0.12f, 0xFFFFFFFF.toInt(), p)
        },
        RuleCard("설명 2바퀴 → 토론 → 비밀 투표", "한 명씩 한 문장으로 제시어 설명 (1인 20초, 2바퀴) → 60초 토론.\n한 명씩 몰래 라이어 지목 (나는 빼고).\n최다 득표가 동점이면 동점자끼리 재투표!") { c, w, h, p ->
            val s = h * 0.22f
            for (k in 0 until 3) LiarArt.drawPerson(c, w * (0.18f + 0.32f * k), h * 0.72f, s, Theme.PLAYER_COLORS[k], p)
            bubble(c, w * 0.2f, h * 0.24f, w * 0.26f, h * 0.2f, p)
            text(c, "동그래요", w * 0.2f, h * 0.27f, h * 0.09f, Theme.INK, p)
            bubble(c, w * 0.52f, h * 0.24f, w * 0.26f, h * 0.2f, p)
            text(c, "맛있어요", w * 0.52f, h * 0.27f, h * 0.09f, Theme.INK, p)
            // 투표 용지
            p.style = Paint.Style.FILL
            p.color = Theme.PAPER
            c.drawRoundRect(w * 0.7f, h * 0.12f, w * 0.95f, h * 0.36f, h * 0.04f, h * 0.04f, p)
            LiarArt.drawCheck(c, w * 0.825f, h * 0.24f, h * 0.15f, LiarArt.LIAR_RED, p)
        },
        RuleCard("라이어를 잡아라!", "지목된 사람이 라이어면 보기 8개 중 제시어 맞히기!\n· 안 걸림: 라이어 +2\n· 걸렸지만 정답: 라이어 +1\n· 걸리고 틀림: 나머지 모두 +1\n판 수 = 인원수, 누적 점수로 순위.") { c, w, h, p ->
            LiarArt.drawLiarFace(c, w * 0.26f, h * 0.48f, h * 0.27f, p, path)
            text(c, "+2", w * 0.7f, h * 0.36f, h * 0.2f, Theme.GOLD, p)
            text(c, "+1", w * 0.62f, h * 0.68f, h * 0.15f, Theme.GOLD_LIGHT, p)
            text(c, "+1", w * 0.82f, h * 0.68f, h * 0.15f, Theme.GOLD_LIGHT, p)
            text(c, "점수", w * 0.72f, h * 0.9f, h * 0.1f, Theme.TEXT_SUB, p)
        },
    )
}
