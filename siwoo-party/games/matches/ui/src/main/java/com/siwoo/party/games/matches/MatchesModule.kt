package com.siwoo.party.games.matches

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
import com.siwoo.party.games.matches.ui.MatchArt
import com.siwoo.party.games.matches.ui.MatchesActivity

/** 성냥개비 가져가기: 모음집 등록 정보 */
object MatchesModule : GameModule {
    override val id = "matches"
    override val name = "성냥개비 가져가기"
    override val tagline = "1~3개씩 가져가기, 마지막 성냥을 집으면 펑!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "5분"
    override val color = 0xFF8D4A24.toInt()
    override val activityClass = MatchesActivity::class.java

    private val path = Path()
    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val GREEN = Theme.PLAYER_COLORS[2]
    private const val GA = "가"
    private const val NA = "나"
    private const val DA = "다"
    private const val TAKE = "가져가기"
    private const val BOOM = "펑!"
    private const val ROUND_1 = "1"
    private const val ROUND_2 = "2"
    private const val ROUND_3 = "3"

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        // 부채꼴로 놓인 성냥 세 개, 가운데 것에 불
        val l = size * 0.6f
        val by = cy + size * 0.3f
        for (k in 0 until 3) {
            val deg = (k - 1) * 28f
            val rad = Math.toRadians(deg.toDouble())
            val mx = cx + (kotlin.math.sin(rad) * l * 0.5f).toFloat()
            val my = by - (kotlin.math.cos(rad) * l * 0.5f).toFloat()
            if (k != 1) MatchArt.drawMatch(canvas, mx, my, l, deg, 0f, 1f, paint)
        }
        MatchArt.drawMatch(canvas, cx, by - l * 0.5f, l, 0f, 0.15f, 1f, paint)
        MatchArt.drawFlame(canvas, cx, by - l * 0.86f, l * 0.42f, 0.5f, 1f, path, paint)
    }

    /** 규칙 그림용 작은 플레이어 칸 (색 동그라미 + 해골 n개) */
    private fun tile(c: Canvas, l: Float, t: Float, w: Float, h: Float, color: Int, initial: String, skulls: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.CARD_LIGHT
        c.drawRoundRect(l, t, l + w, t + h, h * 0.2f, h * 0.2f, p)
        p.color = color
        val r = h * 0.3f
        c.drawCircle(l + r * 1.4f, t + h / 2f, r, p)
        p.color = Theme.textOn(color)
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = r
        c.drawText(initial, l + r * 1.4f, t + h / 2f + r * 0.36f, p)
        for (k in 0 until skulls) MatchArt.drawSkull(c, l + r * 3.4f + k * h * 0.45f, t + h / 2f, h * 0.42f, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("성냥을 1~3개 골라 가져가기", "성냥 21개가 놓여 있어요. 자기 차례에 가져갈 성냥을 1~3개 눌러 고르고(다시 누르면 취소) [가져가기]로 확정해요.\n고른 성냥은 내 패널로 날아가요.") { c, w, h, p ->
            val l = h * 0.5f
            val gap = w * 0.105f
            val left = w * 0.5f - gap * 3f
            for (k in 0 until 7) {
                val lifted = k == 2 || k == 3
                val x = left + k * gap
                val y = h * 0.42f - if (lifted) l * 0.16f else 0f
                if (lifted) {
                    p.style = Paint.Style.STROKE
                    p.strokeCap = Paint.Cap.ROUND
                    p.strokeWidth = l * MatchArt.THICK * 2.6f
                    p.color = MatchArt.fade(Theme.GOLD_LIGHT, 0.55f)
                    c.drawLine(x, y - l * 0.47f, x, y + l * 0.47f, p)
                    p.style = Paint.Style.FILL
                }
                MatchArt.drawMatch(c, x, y, l, if (k % 2 == 0) -4f else 4f, 0f, 1f, p)
            }
            // 터치 물결 (고른 성냥 위)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.016f
            p.color = Theme.TEXT
            c.drawCircle(left + gap * 3f + l * 0.1f, h * 0.5f, h * 0.06f, p)
            c.drawCircle(left + gap * 3f + l * 0.1f, h * 0.5f, h * 0.1f, p)
            // [가져가기] 버튼
            p.style = Paint.Style.FILL
            val bl = w * 0.62f
            val bt = h * 0.76f
            p.color = Theme.shade(Theme.RED, 0.55f)
            c.drawRoundRect(bl, bt + h * 0.02f, w * 0.94f, h * 0.94f, h * 0.05f, h * 0.05f, p)
            p.color = Theme.RED
            c.drawRoundRect(bl, bt, w * 0.94f, h * 0.92f, h * 0.05f, h * 0.05f, p)
            p.color = Theme.TEXT
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.08f
            c.drawText(TAKE, (bl + w * 0.94f) / 2f, h * 0.87f, p)
            // 고른 칸 3개 (2개 참)
            for (k in 0 until 3) {
                val sl = w * 0.08f + k * h * 0.17f
                val s = h * 0.14f
                p.style = Paint.Style.FILL
                p.color = if (k < 2) 0x33FFC93C else 0x26000000
                c.drawRoundRect(sl, h * 0.77f, sl + s, h * 0.77f + s, s * 0.2f, s * 0.2f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = h * 0.01f
                p.color = if (k < 2) Theme.GOLD else 0x55FFFFFF
                c.drawRoundRect(sl, h * 0.77f, sl + s, h * 0.77f + s, s * 0.2f, s * 0.2f, p)
                p.style = Paint.Style.FILL
                if (k < 2) MatchArt.drawMatch(c, sl + s / 2f, h * 0.77f + s / 2f, s * 0.95f, 35f, 0f, 1f, p)
            }
        },
        RuleCard("마지막 성냥을 집으면 펑!", "남은 성냥보다 많이는 못 가져가요. 한 번에 4개 이상도 안 돼요.\n마지막 남은 성냥을 가져간 사람은 성냥에 불이 붙으며 그 라운드 패배(해골 1개)!") { c, w, h, p ->
            val l = h * 0.62f
            val x = w * 0.36f
            val y = h * 0.6f
            p.style = Paint.Style.FILL
            p.color = 0x55FFE9A8
            c.drawCircle(x, y - l * 0.42f, l * 0.55f, p)
            MatchArt.drawMatch(c, x, y, l, 8f, 0.35f, 1f, p)
            MatchArt.drawFlame(c, x + l * 0.08f, y - l * 0.36f, l * 0.48f, 0.5f, 1f, path, p)
            // 펑!
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.2f
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.03f
            p.strokeJoin = Paint.Join.ROUND
            p.color = 0xFF0A0F2E.toInt()
            c.drawText(BOOM, w * 0.68f, h * 0.38f, p)
            p.style = Paint.Style.FILL
            p.color = 0xFFFF6B78.toInt()
            c.drawText(BOOM, w * 0.68f, h * 0.38f, p)
            // → 해골
            MatchArt.drawSkull(c, w * 0.68f, h * 0.66f, h * 0.24f, p)
        },
        RuleCard("라운드 = 인원 수, 해골 적은 순", "인원 수만큼 라운드를 해요(2인 2판, 4인 4판). 라운드마다 먼저 하는 사람이 한 칸씩 바뀌어요.\n모두 끝나면 패배(해골)가 적은 순서로 순위, 같으면 공동 순위.") { c, w, h, p ->
            // 위: 라운드 칸 3개 (진 사람 색)
            val rr = h * 0.075f
            val colorsY = h * 0.18f
            val labels = 3
            for (k in 0 until labels) {
                val x = w * 0.5f + (k - 1) * rr * 3f
                p.style = Paint.Style.FILL
                p.color = when (k) { 0 -> GREEN; 1 -> BLUE; else -> GREEN }
                c.drawCircle(x, colorsY, rr, p)
                p.color = Theme.textOn(p.color)
                p.typeface = Typeface.DEFAULT_BOLD
                p.textAlign = Paint.Align.CENTER
                p.textSize = rr
                c.drawText(if (k == 1) NA else DA, x, colorsY + rr * 0.36f, p)
                MatchArt.drawSkull(c, x + rr * 0.8f, colorsY + rr * 0.6f, rr * 0.8f, p)
                p.color = Theme.TEXT_SUB
                p.textSize = h * 0.06f
                c.drawText(if (k == 0) ROUND_1 else if (k == 1) ROUND_2 else ROUND_3, x, colorsY - rr * 1.35f, p)
            }
            // 아래: 패널 3칸 (해골 0·1·2), 1등 왕관
            val tw = w * 0.42f
            val th = h * 0.16f
            val tl = w * 0.5f - tw / 2f
            tile(c, tl, h * 0.36f, tw, th, RED, GA, 0, p)
            tile(c, tl, h * 0.36f + th * 1.18f, tw, th, BLUE, NA, 1, p)
            tile(c, tl, h * 0.36f + th * 2.36f, tw, th, GREEN, DA, 2, p)
            Ui.drawCrown(c, tl - th * 0.55f, h * 0.36f + th * 0.75f, th * 0.6f, path, p)
            p.color = Theme.GOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.07f
            c.drawText(ROUND_1, tl + tw + th * 0.5f, h * 0.36f + th * 0.68f, p)
            c.drawText(ROUND_2, tl + tw + th * 0.5f, h * 0.36f + th * 1.86f, p)
            c.drawText(ROUND_3, tl + tw + th * 0.5f, h * 0.36f + th * 3.04f, p)
        },
    )
}
