package com.siwoo.party.games.bingo

import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bingo.ui.BingoActivity
import com.siwoo.party.games.bingo.ui.BingoArt

/** 빙고: 모음집 등록 정보 (2~4인, 차례대로, 기기를 넘기며 각자 비밀 판) */
object BingoModule : GameModule {
    override val id = "bingo"
    override val name = "빙고"
    override val tagline = "몰래 채운 내 판, 3줄 먼저 빙고!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "8분"
    override val color = 0xFFE0662A.toInt()
    override val activityClass = BingoActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]

    /** 아이콘 판: 대각선 ↘ 다섯 칸 + 몇 칸 도장, 금색 줄 */
    private val ICON_STAMPS = bits(0, 6, 12, 18, 24, 3, 15)
    private val ICON_LINE = 1 shl 10

    /** 규칙 1: 1~7 까지 넣은 판 */
    private val RULE1_NUMS = IntArray(25).also { a ->
        val order = intArrayOf(12, 0, 7, 19, 4, 23, 10)
        for ((k, c) in order.withIndex()) a[c] = k + 1
    }

    /** 규칙 2: 두 사람 판 (같은 숫자 17 이 다른 칸에) */
    private val RULE2_A = intArrayOf(5, 17, 22, 1, 9, 14, 3, 25, 11, 20, 8, 19, 13, 2, 24, 16, 6, 10, 21, 4, 12, 23, 7, 18, 15)
    private val RULE2_B = intArrayOf(11, 4, 19, 23, 2, 7, 15, 9, 17, 25, 1, 13, 20, 6, 10, 22, 3, 18, 8, 14, 24, 5, 16, 12, 21)
    private val RULE2_A_STAMPS = bits(1, 3, 6, 12)
    private val RULE2_B_STAMPS = bits(8, 10, 2, 11)

    /** 규칙 3: 3줄 완성 판 (가로 둘째·세로 넷째·대각선 ↙) */
    private val RULE3_NUMS = RULE2_A
    private val RULE3_LINES = (1 shl 1) or (1 shl 8) or (1 shl 11)
    private val RULE3_STAMPS = bits(5, 6, 7, 8, 9, 3, 13, 18, 23, 4, 12, 16, 20, 0, 22)

    private fun bits(vararg cells: Int): Int {
        var b = 0
        for (c in cells) b = b or (1 shl c)
        return b
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF3A2A7A.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        paint.style = Paint.Style.FILL
        // 숫자 없는 작은 판 + 도장 + 금색 대각선
        val s = r * 1.16f
        BingoArt.miniBoard(canvas, paint, cx - s * 0.62f, cy - s * 0.5f, s, null, ICON_STAMPS, RED, ICON_LINE)
        // 오른쪽 아래 빙고 공
        BingoArt.ball(canvas, paint, cx + r * 0.5f, cy + r * 0.42f, r * 0.34f, BingoArt.NUM[7])
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("몰래 내 판 채우기", "기기를 넘겨받으면 나만 보는\n5×5 판에 1~25를 채워요.\n빈칸을 누르면 1, 2, 3… 차례로!\n[랜덤 배치]로 한 번에 채울 수도 있어요.") { c, w, h, p ->
            val s = h * 0.8f
            val l = w * 0.3f
            val t = h * 0.1f
            BingoArt.miniBoard(c, p, l, t, s, RULE1_NUMS, 0, RED, 0)
            // 다음 숫자 8 을 넣으려는 손가락
            val k = 16
            val x = BingoArt.miniCellX(l, s, k)
            val y = BingoArt.miniCellY(t, s, k)
            BingoArt.shout(c, p, BingoArt.NUM[8], x, y + h * 0.035f, h * 0.1f, Theme.GOLD)
            BingoArt.finger(c, p, x + h * 0.035f, y + h * 0.06f, h * 0.05f)
            // 왼쪽: 다음 숫자 안내
            BingoArt.shout(c, p, "다음", w * 0.15f, h * 0.4f, h * 0.11f, Theme.TEXT)
            BingoArt.shout(c, p, BingoArt.NUM[8], w * 0.15f, h * 0.66f, h * 0.24f, Theme.GOLD)
            // 오른쪽 위: 남들은 못 봄 (감은 눈)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.02f
            p.color = Theme.GOLD_LIGHT
            val ex = w * 0.93f
            val ey = h * 0.16f
            val ew = h * 0.13f
            c.drawArc(ex - ew / 2f, ey - ew * 0.4f, ex + ew / 2f, ey + ew * 0.3f, 15f, 150f, false, p)
            c.drawLine(ex, ey + ew * 0.3f, ex, ey + ew * 0.44f, p)
            c.drawLine(ex - ew * 0.26f, ey + ew * 0.24f, ex - ew * 0.34f, ey + ew * 0.36f, p)
            c.drawLine(ex + ew * 0.26f, ey + ew * 0.24f, ex + ew * 0.34f, ey + ew * 0.36f, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        },
        RuleCard("차례대로 숫자 부르기", "내 차례엔 내 판에서 아직 안 부른\n숫자를 골라 [부르기].\n부른 숫자는 모두의 판에 자동으로\n도장이 찍혀요. 줄 수는 모두에게 보여요.") { c, w, h, p ->
            val s = h * 0.52f
            val t = h * 0.44f
            val la = w * 0.04f
            val lb = w * 0.96f - s
            BingoArt.miniBoard(c, p, la, t, s, RULE2_A, RULE2_A_STAMPS, RED, 0)
            BingoArt.miniBoard(c, p, lb, t, s, RULE2_B, RULE2_B_STAMPS, BLUE, 0)
            val bx = w * 0.5f
            val by = h * 0.25f
            val br = h * 0.18f
            // 공 → 양쪽 판의 17 칸으로
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.02f
            p.color = Theme.GOLD
            c.drawLine(bx - br * 0.8f, by + br * 0.6f, BingoArt.miniCellX(la, s, 1), BingoArt.miniCellY(t, s, 1) - h * 0.04f, p)
            c.drawLine(bx + br * 0.8f, by + br * 0.6f, BingoArt.miniCellX(lb, s, 8), BingoArt.miniCellY(t, s, 8) - h * 0.04f, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
            BingoArt.ball(c, p, bx, by, br, BingoArt.SHOUT[17])
        },
        RuleCard("3줄 먼저 빙고!", "가로·세로·대각선 다섯 칸이 모두\n찍히면 한 줄! 3줄을 먼저 만들면 빙고!\n한 번에 여럿이 3줄이 되면 함께 1위,\n나머지는 줄 수 순이에요.") { c, w, h, p ->
            val s = h * 0.84f
            BingoArt.miniBoard(c, p, w * 0.04f, h * 0.08f, s, RULE3_NUMS, RULE3_STAMPS, RED, RULE3_LINES)
            val x = w * 0.04f + s + (w * 0.96f - w * 0.04f - s) / 2f
            BingoArt.shout(c, p, "3줄", x, h * 0.42f, h * 0.17f, Theme.TEXT)
            BingoArt.shout(c, p, "빙고!", x, h * 0.72f, h * 0.18f, Theme.GOLD)
        },
    )
}
