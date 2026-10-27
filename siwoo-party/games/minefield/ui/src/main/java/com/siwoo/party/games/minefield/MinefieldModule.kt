package com.siwoo.party.games.minefield

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.minefield.ui.MineArt
import com.siwoo.party.games.minefield.ui.MinefieldActivity

/** 지뢰 피하기: 모음집 등록 정보 */
object MinefieldModule : GameModule {
    override val id = "minefield"
    override val name = "지뢰 피하기"
    override val tagline = "돌아가며 칸을 열어라! 지뢰를 밟으면 펑, 탈락!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "8분"
    override val color = 0xFF3949AB.toInt()
    override val activityClass = MinefieldActivity::class.java

    /** 그림용 글꼴 (아이콘·규칙 그림에서 같이 씀, 미리 만듦) */
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val s = size * 0.36f
        // 2×2 칸: 왼쪽 위 숨은 칸 + 깃발, 오른쪽 위 열린 숫자, 아래 둘은 터진 지뢰·숨은 칸
        val l = cx - s
        val t = cy - s
        MineArt.drawTile(canvas, l, t, s, paint)
        MineArt.drawFlag(canvas, l + s / 2f, t + s / 2f, s * 0.9f, paint)
        MineArt.drawOpen(canvas, l + s, t, s, paint)
        text.textSize = s * 0.62f
        MineArt.drawNumber(canvas, 2, l + s * 1.5f, t + s / 2f, text)
        MineArt.drawTile(canvas, l, t + s, s, paint)
        MineArt.drawOpen(canvas, l + s, t + s, s, paint, MineArt.BOOM_BG)
        MineArt.drawBurst(canvas, l + s * 1.5f, t + s * 1.5f, s * 0.62f, paint, 0xFFFFC93C.toInt())
        MineArt.drawMine(canvas, l + s * 1.5f, t + s * 1.5f, s * 0.24f, paint)
        paint.style = Paint.Style.FILL
    }

    /** 규칙 그림 1: 작은 판 (6×4). 값: -1 숨은 칸, -2 깃발, 0~8 열린 칸 숫자 */
    private val RULE1 = intArrayOf(
        0, 0, 1, -1, -1, -1,
        0, 0, 1, -1, -1, -1,
        1, 1, 2, -1, -1, -1,
        -1, -1, -1, -1, -1, -1,
    )
    private fun miniBoard(c: Canvas, l: Float, t: Float, s: Float, cols: Int, cells: IntArray, p: Paint) {
        for (k in cells.indices) {
            val x = l + (k % cols) * s
            val y = t + (k / cols) * s
            when (val v = cells[k]) {
                -1 -> MineArt.drawTile(c, x, y, s, p)
                -2 -> { MineArt.drawTile(c, x, y, s, p); MineArt.drawFlag(c, x + s / 2f, y + s / 2f, s * 0.9f, p) }
                else -> {
                    MineArt.drawOpen(c, x, y, s, p)
                    text.textSize = s * 0.62f
                    MineArt.drawNumber(c, v, x + s / 2f, y + s / 2f, text)
                }
            }
        }
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("차례에 칸 하나 열기", "12×8 판에 지뢰 15개. 차례에 칸 하나를 눌러 열어요. 첫 칸은 언제나 안전!\n숫자 = 주변 8칸의 지뢰 수. 0 칸이면 주변이 물결처럼 열리고, 연 칸 수만큼 점수.") { c, w, h, p ->
            val s = h * 0.2f
            val cols = 6
            val l = (w - s * cols) / 2f
            val t = h * 0.1f
            miniBoard(c, l, t, s, cols, RULE1, p)
            // 물결 고리 (0 칸에서 퍼짐)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = Theme.GOLD
            c.drawCircle(l + s * 0.5f, t + s * 0.5f, s * 0.9f, p)
            p.alpha = 120
            c.drawArc(l - s * 1.0f, t - s * 1.0f, l + s * 2.0f, t + s * 2.0f, 0f, 90f, false, p)
            p.alpha = 255
            p.style = Paint.Style.FILL
            // +9점 말풍선
            p.color = Theme.GOLD
            c.drawRoundRect(l + s * 3.4f, t + s * 0.9f, l + s * 5.6f, t + s * 1.9f, s * 0.3f, s * 0.3f, p)
            text.textSize = s * 0.5f
            text.color = Theme.INK
            c.drawText(RULE1_SCORE, l + s * 4.5f, t + s * 1.58f, text)
        },
        RuleCard("깃발은 차례를 쓰지 않아요", "[깃발]을 켜고 칸을 누르면 깃발 꽂기·뽑기. 몇 번이든 차례가 넘어가지 않아요.\n깃발은 모두 같이 보는 표시예요. 깃발 칸은 뽑아야 열 수 있어요.") { c, w, h, p ->
            val s = h * 0.36f
            // 왼쪽: 깃발 꽂힌 큰 타일
            MineArt.drawTile(c, w * 0.12f, h * 0.18f, s, p)
            MineArt.drawFlag(c, w * 0.12f + s / 2f, h * 0.18f + s / 2f, s * 0.9f, p, Theme.PLAYER_COLORS[1], RULE2_INITIAL, text.apply { textSize = s * 0.18f; color = Theme.TEXT })
            // 오른쪽: [열기 | 깃발] 전환 버튼
            val bl = w * 0.5f
            val bw = w * 0.17f
            val bt = h * 0.24f
            val bh = h * 0.42f
            val r = h * 0.06f
            p.color = 0xFF151E52.toInt()
            c.drawRoundRect(bl, bt + h * 0.03f, bl + bw, bt + bh, r, r, p)
            p.color = Theme.CARD
            c.drawRoundRect(bl, bt, bl + bw, bt + bh - h * 0.03f, r, r, p)
            MineArt.drawShovel(c, bl + bw / 2f, bt + bh * 0.38f, bh * 0.45f, p, Theme.TEXT_SUB)
            val fl = bl + bw + w * 0.03f
            p.color = MineArt.FLAG_RED_DARK
            c.drawRoundRect(fl, bt + h * 0.03f, fl + bw, bt + bh, r, r, p)
            p.color = MineArt.FLAG_RED
            c.drawRoundRect(fl, bt + h * 0.015f, fl + bw, bt + bh - h * 0.015f, r, r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = Theme.GOLD
            c.drawRoundRect(fl, bt + h * 0.015f, fl + bw, bt + bh - h * 0.015f, r, r, p)
            p.style = Paint.Style.FILL
            MineArt.drawFlag(c, fl + bw / 2f, bt + bh * 0.4f, bh * 0.5f, p, flagColor = Theme.TEXT, poleColor = MineArt.FLAG_RED_DARK)
            text.color = Theme.TEXT_SUB
            text.textSize = h * 0.08f
            c.drawText(RULE2_OPEN, bl + bw / 2f, bt + bh * 0.85f, text)
            text.color = Theme.TEXT
            c.drawText(RULE2_FLAG, fl + bw / 2f, bt + bh * 0.85f, text)
            // 차례 그대로 표시: 화살표 고리
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.025f
            p.color = Theme.GOLD
            c.drawArc(w * 0.12f - s * 0.15f, h * 0.18f - s * 0.15f, w * 0.12f + s * 1.15f, h * 0.18f + s * 1.15f, 200f, 280f, false, p)
            p.style = Paint.Style.FILL
        },
        RuleCard("지뢰를 밟으면 펑! 탈락", "지뢰를 밟으면 폭발과 함께 탈락하고, 지뢰는 판에 남아요.\n최후의 1명이 남거나 안전한 칸이 모두 열리면 끝. 순위: 살아남은 사람(점수 순) → 늦게 탈락한 사람.") { c, w, h, p ->
            val s = h * 0.34f
            val bx = w * 0.25f
            val by = h * 0.45f
            MineArt.drawBurst(c, bx, by, s * 0.9f, p, 0xFFFF8A1C.toInt(), 12)
            MineArt.drawBurst(c, bx, by, s * 0.62f, p, 0xFFFFE08A.toInt(), 10)
            MineArt.drawMine(c, bx, by, s * 0.26f, p)
            // 탈락 도장 칸
            val tl = w * 0.52f
            val tt = h * 0.3f
            val tw = w * 0.44f
            val th = h * 0.3f
            p.color = Theme.CARD
            c.drawRoundRect(tl, tt, tl + tw, tt + th, h * 0.05f, h * 0.05f, p)
            p.color = Theme.PLAYER_COLORS[0]
            c.drawCircle(tl + th * 0.5f, tt + th / 2f, th * 0.3f, p)
            p.color = Theme.TEXT_SUB
            c.drawRoundRect(tl + th, tt + th * 0.3f, tl + th + tw * 0.16f, tt + th * 0.45f, h * 0.01f, h * 0.01f, p)
            c.save()
            c.rotate(-15f, tl + tw * 0.72f, tt + th / 2f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = Theme.RED
            c.drawRect(tl + tw * 0.56f, tt + th * 0.27f, tl + tw * 0.89f, tt + th * 0.73f, p)
            p.style = Paint.Style.FILL
            text.color = Theme.RED
            text.textSize = th * 0.28f
            c.drawText(RULE3_OUT, tl + tw * 0.725f, tt + th * 0.62f, text)
            c.restore()
        },
    )

    private const val RULE1_SCORE = "+9점"
    private const val RULE2_INITIAL = "나"
    private const val RULE2_OPEN = "열기"
    private const val RULE2_FLAG = "깃발"
    private const val RULE3_OUT = "탈락"
}
