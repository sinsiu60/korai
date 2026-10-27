package com.siwoo.party.games.flood

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
import com.siwoo.party.games.flood.ui.FloodActivity
import com.siwoo.party.games.flood.ui.FloodArt

/** 색 번지기 점령: 모음집 등록 정보 (2~4인, 차례대로) */
object FloodModule : GameModule {
    override val id = "flood"
    override val name = "색 번지기 점령"
    override val tagline = "색을 바꿀 때마다 땅이 물결처럼 번져요!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "8분"
    override val color = 0xFF00897B.toInt()
    override val activityClass = FloodActivity::class.java

    private val crown = Path()
    private val arrow = Path()
    private const val GA = "가"
    private const val NA = "나"
    private const val DA = "다"
    private const val FIRST = "1등!"
    private const val CELLS = "12칸"

    // ── 그림용 작은 판 (색 숫자 / 주인: '.' 없음, 0 빨강 1 파랑 2 초록 3 노랑) ──
    private const val ICON_COLORS = "1113" + "1124" + "2215" + "4350"
    private const val ICON_OWNERS = "000." + "00.." + "...." + "...."

    private const val R1_BEFORE_COLORS = "01134" + "02144" + "11123" + "34112" + "53213"
    private const val R1_BEFORE_OWNERS = "0...." + "0...." + "....." + "....." + "....1"
    private const val R1_AFTER_COLORS = "11134" + "12144" + "11123" + "34112" + "53213"
    private const val R1_AFTER_OWNERS = "000.." + "0.0.." + "000.." + "..00." + "...01"

    private const val R3_COLORS = "444422" + "444422" + "444222" + "114552" + "115555" + "115555"
    private const val R3_OWNERS = "000011" + "000011" + "000111" + "330221" + "332222" + "332222"

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF0F5C55.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 4 × 4 판: 빨강 땅이 왼쪽 위에서 번져 나감
        val cell = r * 0.34f
        val l = cx - cell * 2f
        val t = cy - cell * 2f
        FloodArt.mini(canvas, paint, l, t, cell, 4, ICON_COLORS, ICON_OWNERS)
    }

    /** 외곽선 있는 굵은 글자 */
    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF0A1030.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 사람 동그라미 + 첫 글자 */
    private fun person(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int, initial: String) {
        FloodArt.badge(c, p, x, y, r, color)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = r * 1.1f
        p.color = Theme.textOn(color)
        c.drawText(initial, x, y + p.textSize * 0.36f, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("색을 골라 번지기", "내 차례에 오른쪽 색 버튼을 골라요.\n내 땅이 모두 그 색으로 바뀌고\n맞닿은 같은 색 칸을 흡수해요.\n이어진 칸은 줄줄이 계속 퍼져요!") { c, w, h, p ->
            val cell = minOf(h / 6.2f, w / 13.5f)
            val top = (h - cell * 5f) / 2f
            val l1 = w / 2f - cell * 6.2f
            val l2 = w / 2f + cell * 1.2f
            FloodArt.mini(c, p, l1, top, cell, 5, R1_BEFORE_COLORS, R1_BEFORE_OWNERS)
            FloodArt.mini(c, p, l2, top, cell, 5, R1_AFTER_COLORS, R1_AFTER_OWNERS)
            person(c, p, l1 + cell * 0.5f, top + cell * 0.5f, cell * 0.34f, Theme.PLAYER_COLORS[0], GA)
            person(c, p, l2 + cell * 0.5f, top + cell * 0.5f, cell * 0.34f, Theme.PLAYER_COLORS[0], GA)
            // 가운데: 고른 색 버튼 + 화살표
            val ax = w / 2f
            val ay = top + cell * 2.5f
            FloodArt.button(c, p, ax, ay - cell * 0.9f, cell * 0.55f, 1, true)
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            arrow.reset()
            arrow.moveTo(ax - cell * 0.5f, ay + cell * 0.15f)
            arrow.lineTo(ax + cell * 0.15f, ay + cell * 0.15f)
            arrow.lineTo(ax + cell * 0.15f, ay - cell * 0.1f)
            arrow.lineTo(ax + cell * 0.6f, ay + cell * 0.35f)
            arrow.lineTo(ax + cell * 0.15f, ay + cell * 0.8f)
            arrow.lineTo(ax + cell * 0.15f, ay + cell * 0.55f)
            arrow.lineTo(ax - cell * 0.5f, ay + cell * 0.55f)
            arrow.close()
            c.drawPath(arrow, p)
        },
        RuleCard("못 고르는 색", "지금 내 색과 다른 사람의 지금 색은\n고를 수 없어요 (✕ 와 그 사람 표시).\n남의 땅은 빼앗을 수 없으니\n상대가 노리는 색을 먼저 막아요!") { c, w, h, p ->
            val r = minOf(h / 6f, w / 9f)
            val gap = r * 2.7f
            val x0 = w / 2f - gap
            for (i in 0 until 6) {
                val x = x0 + gap * (i % 3)
                val y = if (i < 3) h * 0.3f else h * 0.72f
                val blocked = i == 0 || i == 3 || i == 4
                FloodArt.button(c, p, x, y, r, i, !blocked)
                if (blocked) {
                    FloodArt.cross(c, p, x, y, r * 0.36f)
                    val pc = when (i) { 0 -> Theme.PLAYER_COLORS[0]; 3 -> Theme.PLAYER_COLORS[1]; else -> Theme.PLAYER_COLORS[2] }
                    val ini = when (i) { 0 -> GA; 3 -> NA; else -> DA }
                    person(c, p, x + r * 0.7f, y + r * 0.66f, r * 0.3f, pc, ini)
                }
            }
        },
        RuleCard("넓은 땅이 1등", "모든 칸에 주인이 생기거나\n${GameConfig.MAX_ROUNDS}바퀴가 지나면 끝!\n땅이 넓은 순서로 순위가 정해져요.\n칸 수가 같으면 같은 순위예요.") { c, w, h, p ->
            val cell = h / 7.4f
            val l = w / 2f - cell * 3f
            val top = h - cell * 6f - cell * 0.5f
            FloodArt.mini(c, p, l, top, cell, 6, R3_COLORS, R3_OWNERS)
            person(c, p, l + cell * 0.5f, top + cell * 0.5f, cell * 0.34f, Theme.PLAYER_COLORS[0], GA)
            person(c, p, l + cell * 5.5f, top + cell * 0.5f, cell * 0.34f, Theme.PLAYER_COLORS[1], NA)
            person(c, p, l + cell * 5.5f, top + cell * 5.5f, cell * 0.34f, Theme.PLAYER_COLORS[2], DA)
            person(c, p, l + cell * 0.5f, top + cell * 5.5f, cell * 0.34f, Theme.PLAYER_COLORS[3], "라")
            // 가장 넓은 땅(빨강 12칸) 왕관
            Ui.drawCrown(c, l - cell * 1.6f, top + cell * 1.9f, cell * 1.3f, crown, p)
            shout(c, p, FIRST, l - cell * 1.6f, top + cell * 2.9f, cell * 0.7f, Theme.GOLD)
            shout(c, p, CELLS, l - cell * 1.6f, top + cell * 3.7f, cell * 0.55f, Theme.PLAYER_COLORS[0])
        },
    )
}
