package com.siwoo.party.games.forehead

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
import com.siwoo.party.games.forehead.ui.ForeheadActivity
import com.siwoo.party.games.forehead.ui.ForeheadArt
import com.siwoo.party.games.forehead.ui.ForeheadStore

/** 이마 퀴즈: 모음집 등록 정보 */
object ForeheadModule : GameModule {
    override val id = "forehead"
    override val name = "이마 퀴즈"
    override val tagline = "폰을 이마에! 친구들 설명 듣고 60초 동안 최대한 많이 맞혀라"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.PARTY
    override val partyTags = listOf(PartyTag.TALK)
    override val needsSensor = true
    override val minutes = "5분"
    override val color = 0xFFFF3FB4.toInt()
    override val activityClass = ForeheadActivity::class.java

    /** 허브 소개 팝업의 [게임 설정] → 제시어 편집·판 수·터치 조작 */
    override val settingsLabel: String get() = "게임 설정"

    override fun showSettings(popups: PopupLayer) {
        ForeheadStore.showSettings(popups)
    }

    private val path = Path()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF5A1E8C.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.Party.NEON_PINK
        canvas.drawCircle(cx, cy, r * 0.9f, paint)
        paint.style = Paint.Style.FILL
        canvas.save()
        canvas.clipRect(cx - r, cy - r, cx + r, cy + r * 0.8f)
        ForeheadArt.drawHeadWithPhone(canvas, cx, cy + r * 0.12f, r * 0.42f, Theme.Party.NEON_SKY, Theme.Party.NEON_PINK, "", paint, path)
        canvas.restore()
        // 정답 체크 (오른쪽 아래)
        paint.color = ForeheadArt.CORRECT
        canvas.drawCircle(cx + r * 0.58f, cy + r * 0.5f, r * 0.24f, paint)
        ForeheadArt.drawCheck(canvas, cx + r * 0.58f, cy + r * 0.5f, r * 0.28f, Theme.TEXT, paint)
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

    /** 말풍선 */
    private fun bubble(c: Canvas, x: Float, y: Float, w: Float, h: Float, tailLeft: Boolean, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.PAPER
        c.drawRoundRect(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f, h * 0.35f, h * 0.35f, p)
        path.reset()
        val d = if (tailLeft) -1f else 1f
        path.moveTo(x + d * w * 0.1f, y + h / 2f - 1f)
        path.lineTo(x + d * w * 0.3f, y + h * 0.95f)
        path.lineTo(x + d * w * 0.3f, y + h / 2f - 1f)
        path.close()
        c.drawPath(path, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("폰을 이마에 대고 3초!", "차례인 사람은 폰을 이마에 대고, 화면이 바깥을 보게 가로로 들어요.\n3초 카운트 뒤 제시어가 크게 나와요 (나만 못 봐요!).\n나머지는 말로 설명! 제시어를 그대로 말하면 안 돼요.") { c, w, h, p ->
            ForeheadArt.drawHeadWithPhone(c, w * 0.3f, h * 0.48f, h * 0.2f, Theme.Party.NEON_PURPLE, Theme.Party.NEON_PINK, "사자", p, path)
            bubble(c, w * 0.72f, h * 0.24f, w * 0.38f, h * 0.2f, true, p)
            text(c, "갈기가 있어!", w * 0.72f, h * 0.27f, h * 0.085f, Theme.INK, p)
            bubble(c, w * 0.74f, h * 0.62f, w * 0.38f, h * 0.2f, true, p)
            text(c, "동물의 왕!", w * 0.74f, h * 0.65f, h * 0.085f, Theme.INK, p)
        },
        RuleCard("아래로 = 정답, 위로 = 패스", "맞히면 폰을 아래로 기울여요 → 초록 화면 \"정답!\"\n모르겠으면 위로 기울여 패스 → 주황 화면.\n다시 똑바로 세우면 다음 단어. 센서가 불안정하면 설정의 터치 조작 (왼쪽 = 정답, 오른쪽 = 패스).") { c, w, h, p ->
            // 아래로 기울인 폰 (초록)
            ForeheadArt.drawPhone(c, w * 0.27f, h * 0.42f, w * 0.3f, h * 0.3f, 18f, ForeheadArt.CORRECT, "정답!", p)
            ForeheadArt.drawTiltArrow(c, w * 0.27f, h * 0.78f, h * 0.24f, true, ForeheadArt.CORRECT, p, path)
            // 위로 기울인 폰 (주황)
            ForeheadArt.drawPhone(c, w * 0.73f, h * 0.58f, w * 0.3f, h * 0.3f, -18f, ForeheadArt.PASS, "패스", p)
            ForeheadArt.drawTiltArrow(c, w * 0.73f, h * 0.22f, h * 0.24f, false, ForeheadArt.PASS, p, path)
        },
        RuleCard("60초 동안 많이 맞히기", "1인 60초. 시간이 끝나면 맞힌 단어(초록)와 패스한 단어(주황)를 모두 공개!\n맞힌 개수가 점수예요. 한 바퀴씩 돌고 (설정 1~2바퀴) 합계로 순위, 같으면 같은 등수.") { c, w, h, p ->
            ForeheadArt.drawWatch(c, w * 0.17f, h * 0.52f, h * 0.2f, 0.35f, p)
            val cw = w * 0.26f
            val ch = h * 0.15f
            for (k in 0 until 4) {
                val x = w * 0.36f + (k % 2) * (cw + w * 0.03f)
                val y = h * 0.2f + (k / 2) * (ch + h * 0.06f)
                p.color = if (k == 2) ForeheadArt.PASS else ForeheadArt.CORRECT
                c.drawRoundRect(x, y, x + cw, y + ch, ch / 2f, ch / 2f, p)
                if (k == 2) ForeheadArt.drawPassArrow(c, x + ch * 0.55f, y + ch * 0.45f, ch * 0.5f, Theme.TEXT, p, path)
                else ForeheadArt.drawCheck(c, x + ch * 0.55f, y + ch * 0.5f, ch * 0.5f, Theme.TEXT, p)
                p.color = Theme.TEXT
                c.drawRect(x + ch * 1.05f, y + ch * 0.42f, x + cw - ch * 0.4f, y + ch * 0.58f, p)
            }
            text(c, "정답 3개 = 3점", w * 0.64f, h * 0.84f, h * 0.12f, Theme.GOLD, p)
        },
    )
}
