package com.siwoo.party.games.baseball

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.baseball.ui.BaseballActivity
import com.siwoo.party.games.baseball.ui.BaseballArt

/** 숫자 야구: 모음집 등록 정보 */
object BaseballModule : GameModule {
    private val crownPath = Path()

    override val id = "baseball"
    override val name = "숫자 야구"
    override val tagline = "상대의 비밀 숫자 3자리를 스트라이크·볼 힌트로 먼저 맞혀라!"
    override val playerCounts = GameConfig.PLAYERS..GameConfig.PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "8분"
    override val color = 0xFF2E7D32.toInt()
    override val activityClass = BaseballActivity::class.java

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        // 야구공 + 아래 판정 전광판 (S S B 불)
        BaseballArt.ball(canvas, cx, cy - size * 0.07f, size * 0.3f, paint)
        val bh = size * 0.2f
        val bw = size * 0.56f
        val by = cy + size * 0.3f
        paint.style = Paint.Style.FILL
        paint.color = 0x66000000
        canvas.drawRoundRect(cx - bw / 2f, by - bh / 2f + size * 0.02f, cx + bw / 2f, by + bh / 2f + size * 0.02f, bh / 2f, bh / 2f, paint)
        paint.color = 0xFF141B45.toInt()
        canvas.drawRoundRect(cx - bw / 2f, by - bh / 2f, cx + bw / 2f, by + bh / 2f, bh / 2f, bh / 2f, paint)
        val lr = bh * 0.3f
        BaseballArt.lamp(canvas, cx - bw * 0.3f, by, lr, BaseballArt.STRIKE, true, paint)
        BaseballArt.lamp(canvas, cx, by, lr, BaseballArt.STRIKE, true, paint)
        BaseballArt.lamp(canvas, cx + bw * 0.3f, by, lr, BaseballArt.BALL, true, paint)
    }

    /** 규칙 그림용 숫자 칸 줄 (x0 왼쪽, 가운데 y, 칸 s) */
    private fun digits(c: Canvas, x0: Float, cy: Float, s: Float, d0: String, d1: String, d2: String, p: Paint, hidden: Boolean) {
        val g = s * 0.18f
        for (k in 0 until 3) {
            val l = x0 + k * (s + g)
            p.style = Paint.Style.FILL
            p.color = 0x44000000
            c.drawRoundRect(l, cy - s / 2f + s * 0.08f, l + s, cy + s / 2f + s * 0.08f, s * 0.2f, s * 0.2f, p)
            p.color = if (hidden) Theme.CARD_LIGHT else Theme.PAPER
            c.drawRoundRect(l, cy - s / 2f, l + s, cy + s / 2f, s * 0.2f, s * 0.2f, p)
            if (hidden) {
                p.color = Theme.GOLD
                c.drawCircle(l + s / 2f, cy, s * 0.16f, p)
            } else {
                BaseballArt.boldText(p, s * 0.66f, Theme.INK)
                c.drawText(if (k == 0) d0 else if (k == 1) d1 else d2, l + s / 2f, cy + s * 0.24f, p)
            }
        }
    }

    /** 규칙 그림용 판정 알약 */
    private fun pill(c: Canvas, l: Float, cy: Float, w: Float, h: Float, color: Int, label: String, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.shade(color, 0.55f)
        c.drawRoundRect(l, cy - h / 2f + h * 0.1f, l + w, cy + h / 2f + h * 0.1f, h / 2f, h / 2f, p)
        p.color = color
        c.drawRoundRect(l, cy - h / 2f, l + w, cy + h / 2f, h / 2f, h / 2f, p)
        BaseballArt.boldText(p, h * 0.62f, Theme.textOn(color))
        c.drawText(label, l + w / 2f, cy + h * 0.22f, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("비밀 숫자 3자리 정하기",
            "기기를 넘겨 받아 서로 다른 숫자 3개(0도 가능)를 몰래 눌러요.\n누른 숫자는 ● 로 가려지고, 칸을 꾹 누르는 동안만 보여요.") { c, w, h, p ->
            val s = h * 0.3f
            digits(c, w * 0.5f - s * 1.68f, h * 0.42f, s, "", "", "", p, true)
            BaseballArt.boldText(p, h * 0.1f, Theme.TEXT_SUB)
            c.drawText("꾹 누르면 보여요", w * 0.5f, h * 0.84f, p)
            BaseballArt.ball(c, w * 0.12f, h * 0.3f, h * 0.1f, p)
            BaseballArt.ball(c, w * 0.88f, h * 0.62f, h * 0.08f, p)
        },
        RuleCard("스트라이크 · 볼 · 아웃",
            "번갈아 상대 숫자를 추측해요. 숫자·자리 모두 맞으면 스트라이크(S), 숫자만 맞으면 볼(B), 하나도 없으면 아웃!\n예) 비밀 427 · 추측 472 → 1S 2B") { c, w, h, p ->
            val s = h * 0.2f
            val g = s * 0.18f
            val x0 = w * 0.07f
            val topY = h * 0.3f
            val botY = h * 0.78f
            // 맞는 숫자 잇기: 같은 자리(4) 금색, 자리만 다른(2·7) 초록
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.025f
            p.color = BaseballArt.STRIKE
            c.drawLine(x0 + s / 2f, topY + s / 2f, x0 + s / 2f, botY - s / 2f, p)
            p.color = BaseballArt.BALL
            c.drawLine(x0 + s * 1.5f + g, topY + s / 2f, x0 + s * 2.5f + g * 2, botY - s / 2f, p)
            c.drawLine(x0 + s * 2.5f + g * 2, topY + s / 2f, x0 + s * 1.5f + g, botY - s / 2f, p)
            p.strokeCap = Paint.Cap.BUTT
            digits(c, x0, topY, s, "4", "2", "7", p, false)
            digits(c, x0, botY, s, "4", "7", "2", p, false)
            val lx = x0 + s * 3 + g * 2 + w * 0.05f
            BaseballArt.boldText(p, h * 0.085f, Theme.TEXT_SUB)
            p.textAlign = Paint.Align.LEFT
            c.drawText("비밀", lx, topY + h * 0.03f, p)
            c.drawText("추측", lx, botY - h * 0.1f, p)
            val pw = (w * 0.95f - lx) / 2f - w * 0.01f
            pill(c, lx, botY + h * 0.06f, pw, h * 0.15f, BaseballArt.STRIKE, "1S", p)
            pill(c, lx + pw + w * 0.02f, botY + h * 0.06f, pw, h * 0.15f, BaseballArt.BALL, "2B", p)
        },
        RuleCard("먼저 3S 면 승리!",
            "먼저 3스트라이크를 만들면 승리! 단 선공이 맞히면 후공에게 마지막 1번의 기회 —\n후공도 맞히면 무승부예요. 끝나면 두 사람의 비밀 숫자를 공개해요.") { c, w, h, p ->
            val s = h * 0.22f
            digits(c, w * 0.5f - s * 1.68f, h * 0.36f, s, "4", "2", "7", p, false)
            pill(c, w * 0.5f - w * 0.17f, h * 0.72f, w * 0.34f, h * 0.18f, BaseballArt.STRIKE, "3S 정답!", p)
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            Ui.drawCrown(c, w * 0.5f, h * 0.17f, w * 0.12f, crownPath, p)
            BaseballArt.ball(c, w * 0.14f, h * 0.55f, h * 0.11f, p)
            BaseballArt.ball(c, w * 0.86f, h * 0.55f, h * 0.11f, p)
        },
    )
}
