package com.siwoo.party.games.speed

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.speed.ui.SpeedActivity
import com.siwoo.party.games.speed.ui.SpeedArt

/** 스피드 카드: 모음집 등록 정보 */
object SpeedModule : GameModule {
    override val id = "speed"
    override val name = "스피드 카드"
    override val tagline = "마주 앉아 동시에! 1 크거나 작은 카드를 누구보다 빨리 내려놓기"
    override val playerCounts = GameConfig.PLAYERS..GameConfig.PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val needsMultiTouch = 2
    override val minutes = "3분"
    override val color = 0xFFE0662A.toInt()
    override val activityClass = SpeedActivity::class.java

    private val path = Path()
    private val TABLE_LABELS = arrayOf("3", "J", "9", "5")

    /** 번개 (cx, cy, 크기 s) */
    private fun bolt(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        path.reset()
        path.moveTo(cx + s * 0.15f, cy - s * 0.5f)
        path.lineTo(cx - s * 0.3f, cy + s * 0.08f)
        path.lineTo(cx - s * 0.02f, cy + s * 0.08f)
        path.lineTo(cx - s * 0.15f, cy + s * 0.5f)
        path.lineTo(cx + s * 0.3f, cy - s * 0.08f)
        path.lineTo(cx + s * 0.02f, cy - s * 0.08f)
        path.close()
        p.style = Paint.Style.FILL
        c.drawPath(path, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val w = size * 0.34f
        SpeedArt.card(canvas, cx - size * 0.2f, cy + size * 0.05f, w, -20f, "6", false, paint)
        SpeedArt.card(canvas, cx + size * 0.2f, cy + size * 0.05f, w, 20f, "8", false, paint)
        SpeedArt.card(canvas, cx, cy - size * 0.02f, w, 0f, "7", true, paint)
        paint.color = 0x66000000
        bolt(canvas, cx + size * 0.31f + size * 0.015f, cy - size * 0.28f + size * 0.02f, size * 0.3f, paint)
        paint.color = Theme.GOLD
        bolt(canvas, cx + size * 0.31f, cy - size * 0.28f, size * 0.3f, paint)
    }

    /** 규칙 그림: 위·아래 자기 줄(위는 180°) + 가운데 두 더미 */
    private fun table(c: Canvas, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        for (side in 0..1) {
            val top = side == 1
            p.color = Theme.PLAYER_COLORS[side]
            p.alpha = 60
            val y0 = if (top) h * 0.02f else h * 0.7f
            c.drawRoundRect(w * 0.03f, y0, w * 0.97f, y0 + h * 0.28f, h * 0.05f, h * 0.05f, p)
            p.alpha = 255
        }
        val cw = w * 0.1f
        for (side in 0..1) {
            val top = side == 1
            val y = if (top) h * 0.16f else h * 0.84f
            val rot = if (top) 180f else 0f
            // 더미 + 칸 4장 (위 줄은 화면 가운데 기준 점대칭)
            for (i in 0..4) {
                val lx = w * 0.2f + i * cw * 1.25f
                val x = if (top) w - lx else lx
                if (i == 0) SpeedArt.back(c, x, y, cw, rot, p)
                else SpeedArt.card(c, x, y, cw, rot, TABLE_LABELS[(i - 1 + side) % 4], (i + side) % 2 == 0, p)
            }
        }
        SpeedArt.card(c, w * 0.42f, h * 0.5f, w * 0.13f, 0f, "7", true, p)
        SpeedArt.card(c, w * 0.58f, h * 0.5f, w * 0.13f, 180f, "Q", false, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("마주 앉아 동시에", "폰을 가운데 두고 긴 변 양쪽에 마주 앉아요.\n내 앞에는 카드 4장과 뽑을 더미, 가운데에는 더미 2개.\n차례 없이 두 사람이 동시에 진행!") { c, w, h, p ->
            table(c, w, h, p)
        },
        RuleCard("1 크거나 작은 카드를 끌어 놓기", "가운데 맨 위 카드보다 숫자가 1 크거나 작은 카드를 끌어다 놓아요 (무늬 상관없음, K와 A는 이어짐).\n낸 자리는 내 더미에서 바로 채워져요.") { c, w, h, p ->
            SpeedArt.card(c, w * 0.5f, h * 0.4f, w * 0.2f, 0f, "7", true, p)
            SpeedArt.card(c, w * 0.15f, h * 0.48f, w * 0.15f, -12f, "6", false, p)
            SpeedArt.card(c, w * 0.85f, h * 0.48f, w * 0.15f, 12f, "8", true, p)
            SpeedArt.arrow(c, w * 0.24f, h * 0.36f, w * 0.37f, h * 0.36f, h * 0.035f, Theme.GOLD, p)
            SpeedArt.arrow(c, w * 0.76f, h * 0.36f, w * 0.63f, h * 0.36f, h * 0.035f, Theme.GOLD, p)
            SpeedArt.card(c, w * 0.38f, h * 0.83f, w * 0.085f, 0f, "K", false, p)
            SpeedArt.card(c, w * 0.62f, h * 0.83f, w * 0.085f, 0f, "A", true, p)
            SpeedArt.arrow(c, w * 0.45f, h * 0.83f, w * 0.56f, h * 0.83f, h * 0.022f, Theme.GOLD_LIGHT, p)
            SpeedArt.arrow(c, w * 0.55f, h * 0.83f, w * 0.44f, h * 0.83f, h * 0.022f, Theme.GOLD_LIGHT, p)
        },
        RuleCard("막히면 [다시], 먼저 다 내면 승리", "둘 다 낼 카드가 없으면 [다시]가 빛나요.\n둘 다 누르면 가운데에 새 카드가 나와요!\n칸과 더미를 먼저 모두 비운 사람이 승리.") { c, w, h, p ->
            SpeedArt.button(c, w * 0.42f, h * 0.15f, w * 0.3f, h * 0.17f, 180f, Theme.GOLD, "다시", p)
            SpeedArt.button(c, w * 0.42f, h * 0.85f, w * 0.3f, h * 0.17f, 0f, Theme.GOLD, "다시", p)
            SpeedArt.back(c, w * 0.12f, h * 0.5f, w * 0.11f, 0f, p)
            SpeedArt.arrow(c, w * 0.19f, h * 0.5f, w * 0.27f, h * 0.5f, h * 0.03f, Theme.GOLD, p)
            SpeedArt.card(c, w * 0.35f, h * 0.5f, w * 0.12f, 0f, "4", true, p)
            SpeedArt.card(c, w * 0.51f, h * 0.5f, w * 0.12f, 180f, "10", false, p)
            Ui.drawCrown(c, w * 0.81f, h * 0.6f, w * 0.2f, path, p)
            p.color = Theme.GOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.1f
            c.drawText("0장!", w * 0.81f, h * 0.75f, p)
        },
    )
}
