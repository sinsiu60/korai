package com.siwoo.party.games.connect4

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.connect4.ui.C4Art
import com.siwoo.party.games.connect4.ui.Connect4Activity

/** 사목 떨어뜨리기: 모음집 등록 정보 (2인 전용, 차례대로) */
object Connect4Module : GameModule {
    override val id = "connect4"
    override val name = "사목 떨어뜨리기"
    override val tagline = "돌을 톡 떨어뜨려 넷을 먼저 한 줄로!"
    override val playerCounts = GameConfig.PLAYERS..GameConfig.PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "5분"
    override val color = 0xFF3949AB.toInt()
    override val activityClass = Connect4Activity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val YELLOW = Theme.PLAYER_COLORS[3]
    private val arrow = Path()

    /** 그림 속 판 구멍 (i, j: 위에서부터) 중심 */
    private fun hx(l: Float, cell: Float, i: Int) = l + cell * (i + 0.5f)
    private fun hy(t: Float, cell: Float, j: Int) = t + cell * (j + 0.5f)

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF2B3C8F.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 4 × 3 판 + 떨어지는 돌
        val cell = r * 0.36f
        val l = cx - cell * 2f
        val t = cy - cell * 0.95f
        C4Art.frame(canvas, paint, l, t, 4, 3, cell)
        val dr = cell * 0.38f
        C4Art.disc(canvas, paint, hx(l, cell, 0), hy(t, cell, 2), dr, RED)
        C4Art.disc(canvas, paint, hx(l, cell, 1), hy(t, cell, 2), dr, YELLOW)
        C4Art.disc(canvas, paint, hx(l, cell, 1), hy(t, cell, 1), dr, RED)
        C4Art.disc(canvas, paint, hx(l, cell, 2), hy(t, cell, 2), dr, YELLOW)
        C4Art.disc(canvas, paint, hx(l, cell, 2), hy(t, cell, 1), dr, YELLOW)
        C4Art.disc(canvas, paint, hx(l, cell, 2), hy(t, cell, 0), dr, RED)
        C4Art.disc(canvas, paint, hx(l, cell, 3), hy(t, cell, 2), dr, YELLOW)
        // 위에서 떨어지는 빨간 돌 + 움직임 줄
        val fx = hx(l, cell, 3)
        val fy = t - cell * 0.62f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dr * 0.2f
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawLine(fx - dr * 0.45f, fy - dr * 1.2f, fx - dr * 0.45f, fy - dr * 1.8f, paint)
        canvas.drawLine(fx + dr * 0.45f, fy - dr * 1.2f, fx + dr * 0.45f, fy - dr * 1.8f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        C4Art.disc(canvas, paint, fx, fy, dr, RED)
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

    /** 무늬로 판 채우기: pattern 의 글자 R/Y/. (위 줄부터) */
    private fun fill(c: Canvas, p: Paint, l: Float, t: Float, cell: Float, pattern: String, cols: Int) {
        val dr = cell * 0.38f
        for (k in pattern.indices) {
            val ch = pattern[k]
            if (ch == '.') continue
            C4Art.disc(c, p, hx(l, cell, k % cols), hy(t, cell, k / cols), dr, if (ch == 'R') RED else YELLOW)
        }
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("줄을 골라 톡!", "내 차례에 원하는 줄(열)을 누르면\n돌이 위에서 떨어져\n그 줄의 가장 아래 빈칸에 쌓여요.\n꽉 찬 줄은 고를 수 없어요.") { c, w, h, p ->
            val cell = h / 5.8f
            val l = (w - cell * 7f) / 2f
            val t = h - cell * 4f - h * 0.04f
            C4Art.frame(c, p, l, t, 7, 4, cell)
            fill(c, p, l, t, cell, "...R..." + "..YY..." + ".RRY.Y." + "YRYRRY.", 7)
            // 4번째 열 위에 떠 있는 돌 + 아래 화살표 + 손가락
            val x = hx(l, cell, 4)
            val y = t - cell * 0.62f
            p.style = Paint.Style.FILL
            p.color = 0x33FFFFFF
            c.drawRect(x - cell * 0.5f, t, x + cell * 0.5f, t + cell * 4f, p)
            C4Art.disc(c, p, x, y, cell * 0.38f, RED)
            p.color = Theme.GOLD
            arrow.reset()
            arrow.moveTo(x - cell * 0.2f, t + cell * 0.25f)
            arrow.lineTo(x + cell * 0.2f, t + cell * 0.25f)
            arrow.lineTo(x, t + cell * 0.48f)
            arrow.close()
            c.drawPath(arrow, p)
            p.color = 0xFFFFE0C8.toInt()
            c.drawCircle(x + cell * 0.55f, y + cell * 0.25f, cell * 0.24f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = cell * 0.05f
            p.color = 0xFF8A5A26.toInt()
            c.drawCircle(x + cell * 0.55f, y + cell * 0.25f, cell * 0.24f, p)
            p.style = Paint.Style.FILL
        },
        RuleCard("넷을 한 줄로", "가로·세로·대각선으로\n내 돌 넷을 먼저 이으면 승리!\n상대가 넷을 만들기 전에\n가운데를 막는 것도 중요해요.") { c, w, h, p ->
            val cell = h / 5.6f
            val l = (w - cell * 7f) / 2f
            val t = h - cell * 4f - h * 0.05f
            C4Art.frame(c, p, l, t, 7, 4, cell)
            fill(c, p, l, t, cell, "....R.." + "...RY.." + "..RYY.." + ".RYYR.Y", 7)
            // 대각선 넷: 금색 고리
            p.style = Paint.Style.STROKE
            p.strokeWidth = cell * 0.09f
            p.color = Theme.GOLD
            for (k in 0..3) c.drawCircle(hx(l, cell, 1 + k), hy(t, cell, 3 - k), cell * 0.42f, p)
            p.style = Paint.Style.FILL
            shout(c, p, "넷!", w * 0.84f, h * 0.2f, h * 0.17f, Theme.GOLD)
        },
        RuleCard("판이 다 차면 무승부", "번갈아 하나씩 떨어뜨려요.\n먼저 둘 사람은 무작위로 정해요.\n${GameConfig.CONNECT4_COLS * GameConfig.CONNECT4_ROWS}칸이 다 찰 때까지 넷이 없으면\n무승부 — 둘 다 1위!") { c, w, h, p ->
            val cell = h / 6.6f
            val l = (w - cell * 7f) / 2f
            val t = h * 0.05f
            C4Art.frame(c, p, l, t, 7, 6, cell)
            fill(c, p, l, t, cell, "RYRYRYR" + "RRYRYRY" + "YRYRYRY" + "YRYRYRY" + "RYRYRYR" + "YRYRYRY", 7)
            shout(c, p, "무승부", w * 0.5f, h * 0.58f, h * 0.2f, Theme.GOLD)
        },
    )
}
