package com.siwoo.party.games.reversi

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.reversi.ui.ReversiActivity
import com.siwoo.party.games.reversi.ui.RvArt

/** 뒤집기 돌: 모음집 등록 정보 (2인 전용, 차례대로) */
object ReversiModule : GameModule {
    override val id = "reversi"
    override val name = "뒤집기 돌"
    override val tagline = "끼우면 뒤집혀요! 돌을 더 많이 차지하기"
    override val playerCounts = GameConfig.PLAYERS..GameConfig.PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "10분"
    override val color = 0xFF1E8A4C.toInt()
    override val activityClass = ReversiActivity::class.java

    private val arrow = Path()

    /** 그림 속 판 칸 (i, j) 중심 */
    private fun hx(l: Float, cell: Float, i: Int) = l + cell * (i + 0.5f)
    private fun hy(t: Float, cell: Float, j: Int) = t + cell * (j + 0.5f)

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = RvArt.FELT_DARK
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 3 × 3 판 + 돌 (가운데 위 돌이 뒤집히는 중)
        val cell = r * 0.48f
        val l = cx - cell * 1.5f
        val t = cy - cell * 1.5f
        RvArt.board(canvas, paint, l, t, 3, 3, cell)
        val dr = cell * 0.4f
        RvArt.disc(canvas, paint, hx(l, cell, 0), hy(t, cell, 1), dr, true)
        RvArt.disc(canvas, paint, hx(l, cell, 1), hy(t, cell, 1), dr, false)
        RvArt.disc(canvas, paint, hx(l, cell, 2), hy(t, cell, 1), dr, true)
        RvArt.disc(canvas, paint, hx(l, cell, 1), hy(t, cell, 2), dr, true)
        RvArt.disc(canvas, paint, hx(l, cell, 2), hy(t, cell, 2), dr, false)
        RvArt.disc(canvas, paint, hx(l, cell, 0), hy(t, cell, 2), dr, false)
        // 뒤집히는 돌 + 회전 화살표
        val fx = hx(l, cell, 1)
        val fy = hy(t, cell, 0)
        RvArt.disc(canvas, paint, fx, fy, dr, false, 0.35f, 0.8f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dr * 0.18f
        paint.color = Theme.GOLD_LIGHT
        canvas.drawArc(fx - dr * 1.25f, fy - dr * 1.05f, fx + dr * 1.25f, fy + dr * 0.75f, 200f, 140f, false, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
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

    /** 무늬로 판 채우기: pattern 의 글자 B/W/. (위 줄부터) */
    private fun fill(c: Canvas, p: Paint, l: Float, t: Float, cell: Float, pattern: String, cols: Int) {
        val dr = cell * 0.4f
        for (k in pattern.indices) {
            val ch = pattern[k]
            if (ch != 'B' && ch != 'W') continue
            RvArt.disc(c, p, hx(l, cell, k % cols), hy(t, cell, k / cols), dr, ch == 'B')
        }
    }

    /** 손가락 끝 (살구색 원) */
    private fun finger(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFFFFE0C8.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.2f
        p.color = 0xFF8A5A26.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("끼우면 뒤집혀요", "내 돌 두 개 사이에 상대 돌을\n빈틈 없이 끼우면 모두 내 돌로!\n가로·세로·대각선 8방향 모두 돼요.\n가까운 돌부터 차례로 뒤집혀요.") { c, w, h, p ->
            val cell = minOf(h / 3.2f, w / 7.4f)
            val l = (w - cell * 6f) / 2f
            val t = (h - cell * 3f) / 2f + h * 0.04f
            RvArt.board(c, p, l, t, 6, 3, cell)
            fill(c, p, l, t, cell, "......" + "B....." + "......", 6)
            val y = hy(t, cell, 1)
            val dr = cell * 0.4f
            // 방금 놓은 흑돌 (금색 고리) ← 사이 백 셋이 차례로 뒤집힘
            RvArt.disc(c, p, hx(l, cell, 4), y, dr, true)
            p.style = Paint.Style.STROKE
            p.strokeWidth = cell * 0.07f
            p.color = Theme.GOLD
            c.drawCircle(hx(l, cell, 4), y, dr * 1.18f, p)
            p.style = Paint.Style.FILL
            RvArt.disc(c, p, hx(l, cell, 3), y, dr, true)
            RvArt.disc(c, p, hx(l, cell, 2), y, dr, false, 0.3f, 0.9f)
            RvArt.disc(c, p, hx(l, cell, 1), y, dr, false)
            // 뒤집히는 순서 화살표 (놓은 돌 → 바깥쪽)
            p.color = Theme.GOLD
            val ay = hy(t, cell, 0) + cell * 0.1f
            p.strokeWidth = cell * 0.08f
            p.style = Paint.Style.STROKE
            c.drawLine(hx(l, cell, 3) + cell * 0.2f, ay, hx(l, cell, 1), ay, p)
            p.style = Paint.Style.FILL
            arrow.reset()
            arrow.moveTo(hx(l, cell, 1) - cell * 0.22f, ay)
            arrow.lineTo(hx(l, cell, 1) + cell * 0.05f, ay - cell * 0.16f)
            arrow.lineTo(hx(l, cell, 1) + cell * 0.05f, ay + cell * 0.16f)
            arrow.close()
            c.drawPath(arrow, p)
            finger(c, p, hx(l, cell, 4) + cell * 0.45f, y + cell * 0.55f, cell * 0.2f)
        },
        RuleCard("둘 곳은 점으로", "상대 돌을 하나라도 끼울 수 있는\n자리에만 둘 수 있어요.\n둘 수 있는 곳은 작은 점으로 보여요.\n가운데 4개로 시작, 흑(1번)이 먼저!") { c, w, h, p ->
            val cell = minOf(h / 9.6f, w / 9.6f)
            val l = (w - cell * 8f) / 2f
            val t = (h - cell * 8f) / 2f - cell * 0.05f
            RvArt.board(c, p, l, t, 8, 8, cell)
            fill(c, p, l, t, cell, "........".repeat(3) + "...WB..." + "...BW..." + "........".repeat(3), 8)
            val dot = cell * 0.14f
            RvArt.hintDot(c, p, hx(l, cell, 3), hy(t, cell, 2), dot, true)
            RvArt.hintDot(c, p, hx(l, cell, 2), hy(t, cell, 3), dot, true)
            RvArt.hintDot(c, p, hx(l, cell, 5), hy(t, cell, 4), dot, true)
            RvArt.hintDot(c, p, hx(l, cell, 4), hy(t, cell, 5), dot, true)
            finger(c, p, hx(l, cell, 5) + cell * 0.45f, hy(t, cell, 4) + cell * 0.45f, cell * 0.26f)
        },
        RuleCard("둘 곳이 없으면 패스", "둘 곳이 없으면 자동으로 패스 —\n상대가 한 번 더 둬요.\n둘 다 둘 곳이 없거나 판이 가득 차면 끝!\n돌이 많은 쪽 승리, 같으면 둘 다 1위.") { c, w, h, p ->
            val r = h * 0.15f
            val y = h * 0.52f
            RvArt.disc(c, p, w * 0.24f, y, r, true)
            RvArt.disc(c, p, w * 0.76f, y, r, false)
            shout(c, p, "40", w * 0.24f, y + r + h * 0.19f, h * 0.16f, Theme.GOLD)
            shout(c, p, "24", w * 0.76f, y + r + h * 0.19f, h * 0.16f, Theme.TEXT_SUB)
            shout(c, p, ":", w * 0.5f, y + h * 0.06f, h * 0.2f, Theme.TEXT)
            // 이긴 쪽 왕관 대신 금색 고리
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.12f
            p.color = Theme.GOLD
            c.drawCircle(w * 0.24f, y, r * 1.18f, p)
            p.style = Paint.Style.FILL
            shout(c, p, "패스!", w * 0.5f, h * 0.24f, h * 0.18f, Theme.GOLD)
        },
    )
}
