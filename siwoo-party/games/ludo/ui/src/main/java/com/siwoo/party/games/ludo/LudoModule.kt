package com.siwoo.party.games.ludo

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
import com.siwoo.party.games.ludo.ui.LudoActivity
import com.siwoo.party.games.ludo.ui.LudoArt

/** 말판 레이스: 모음집 등록 정보 */
object LudoModule : GameModule {
    override val id = "ludo"
    override val name = "말판 레이스"
    override val tagline = "주사위 6으로 출발! 상대 말을 잡고 말 4개를 먼저 집에 넣어라"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "15~25분"
    override val color = 0xFF2E9E6B.toInt()
    override val activityClass = LudoActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val GREEN = Theme.PLAYER_COLORS[2]
    private val YELLOW = Theme.PLAYER_COLORS[3]
    private val RED_DARK = LudoArt.dark(RED)
    private val BLUE_DARK = LudoArt.dark(BLUE)
    private val SEAT_COLORS = intArrayOf(RED, BLUE, GREEN, YELLOW)
    private val SEAT_LIGHT = IntArray(4) { LudoArt.light(SEAT_COLORS[it]) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val path = Path()

    private const val START = "출발!"
    private const val VOID = "3연속 6 무효"
    private const val CAUGHT = "잡았다!"
    private const val SAFE = "별 칸은 안전"
    private const val EXACT = "딱 맞게!"
    private const val FIRST = "1위"

    /** 외곽선 글자 */
    private fun outlined(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = size * 0.16f
        p.color = Theme.INK
        c.drawText(s, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(s, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    /** 판 칸 하나 (가운데 x,y, 한 변 u) */
    private fun cell(c: Canvas, x: Float, y: Float, u: Float, color: Int, p: Paint) {
        val h = u / 2f - u * 0.04f
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRect(x - h, y - h, x + h, y + h, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = u * 0.05f
        p.color = LudoArt.LINE
        c.drawRect(x - h, y - h, x + h, y + h, p)
        p.style = Paint.Style.FILL
    }

    /** 통통 뛰는 점선 호 (x0 → x1, 높이 lift) */
    private fun hop(c: Canvas, x0: Float, x1: Float, y: Float, lift: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = lift * 0.12f
        p.color = Theme.GOLD
        path.reset()
        path.moveTo(x0, y)
        path.quadTo((x0 + x1) / 2f, y - lift * 2f, x1, y)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD_LIGHT
        c.drawOval(x - s * 0.22f, y - s, x + s * 0.22f, y + s, p)
        c.drawOval(x - s, y - s * 0.22f, x + s, y + s * 0.22f, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        // 뒤: 판 (살짝 기울임) + 말 몇 개, 앞: 주사위 6
        val bs = size * 0.74f
        val l = cx - bs / 2f - size * 0.05f
        val t = cy - bs / 2f - size * 0.06f
        canvas.save()
        canvas.rotate(-6f, cx, cy)
        LudoArt.drawBoard(canvas, l, t, bs, SEAT_COLORS, SEAT_LIGHT, paint, path)
        val u = bs / LudoBoard.SIZE
        val n = 4
        LudoArt.glow(canvas, l + LudoBoard.TRACK_X[n] * u, t + LudoBoard.TRACK_Y[n] * u, u * 0.75f, paint)
        LudoArt.drawPiece(canvas, l + LudoBoard.TRACK_X[n] * u, t + LudoBoard.TRACK_Y[n] * u, u * 0.75f, RED, RED_DARK, paint)
        LudoArt.drawPiece(canvas, l + LudoBoard.TRACK_X[17] * u, t + LudoBoard.TRACK_Y[17] * u, u * 0.75f, BLUE, BLUE_DARK, paint)
        canvas.restore()
        LudoArt.drawDie(canvas, cx + size * 0.26f, cy + size * 0.24f, size * 0.3f, 6, 14f, paint)
        sparkle(canvas, cx + size * 0.42f, cy + size * 0.02f, size * 0.05f, paint)
        sparkle(canvas, cx - size * 0.4f, cy + size * 0.36f, size * 0.04f, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("6이 나와야 출발", "주사위나 [굴리기]를 눌러 굴려요. 마당의 말은 6이 나와야 출발 칸에 올라가요.\n6이 나오면 움직인 뒤 한 번 더! 단, 6이 세 번 연속이면 세 번째 6은 무효이고 차례가 끝나요.\n움직일 말이 없으면 차례가 저절로 넘어가요.") { c, w, h, p ->
            // 왼쪽: 빨강 마당 + 아래 길 (출발 칸으로 뛰어오르는 말)
            val u = h * 0.11f
            val l = w * 0.04f
            val t = h * 0.04f
            p.style = Paint.Style.FILL
            p.color = RED
            c.drawRoundRect(l, t, l + 6 * u, t + 6 * u, u * 0.5f, u * 0.5f, p)
            p.color = LudoArt.PAPER
            c.drawRoundRect(l + u * 0.8f, t + u * 0.8f, l + u * 5.2f, t + u * 5.2f, u * 0.45f, u * 0.45f, p)
            for (k in 0 until 4) {
                val x = l + LudoBoard.YARD_X[0][k] * u
                val y = t + LudoBoard.YARD_Y[0][k] * u
                p.color = SEAT_LIGHT[0]
                c.drawCircle(x, y, u * 0.68f, p)
                if (k != 2) LudoArt.drawPiece(c, x, y, u * 0.5f, RED, RED_DARK, p)
            }
            val ry = t + 6.55f * u
            for (k in 0 until 6) cell(c, l + (k + 0.5f) * u, ry, u, if (k == 1) RED else 0xFFFFFFFF.toInt(), p)
            val sx = l + 1.5f * u
            // 빈 대기 자리 → 출발 칸으로 뛰어오른 길
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = u * 0.1f
            p.color = Theme.GOLD
            path.reset()
            path.moveTo(l + LudoBoard.YARD_X[0][2] * u, t + LudoBoard.YARD_Y[0][2] * u)
            path.quadTo(l - u * 0.2f, t + 5.2f * u, sx - u * 0.35f, ry - u * 0.3f)
            c.drawPath(path, p)
            p.style = Paint.Style.FILL
            LudoArt.glow(c, sx, ry, u * 0.45f, p)
            LudoArt.drawPiece(c, sx, ry, u * 0.45f, RED, RED_DARK, p)
            // 오른쪽 위: 주사위 6 + 출발!
            val rx = w * 0.79f
            LudoArt.drawDie(c, rx, h * 0.24f, h * 0.26f, 6, -8f, p)
            outlined(c, START, rx, h * 0.5f, h * 0.11f, Theme.GOLD, p)
            // 오른쪽 아래: 6 6 6 → 무효
            val ds = h * 0.13f
            for (k in -1..1) LudoArt.drawDie(c, rx + k * ds * 1.18f, h * 0.68f, ds, 6, 0f, p)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.022f
            p.color = Theme.RED
            val x3 = rx + ds * 1.18f
            c.drawLine(x3 - ds * 0.55f, h * 0.68f - ds * 0.55f, x3 + ds * 0.55f, h * 0.68f + ds * 0.55f, p)
            c.drawLine(x3 + ds * 0.55f, h * 0.68f - ds * 0.55f, x3 - ds * 0.55f, h * 0.68f + ds * 0.55f, p)
            p.style = Paint.Style.FILL
            outlined(c, VOID, rx, h * 0.9f, h * 0.085f, Theme.TEXT, p)
        },
        RuleCard("빛나는 말을 터치 → 잡기", "움직일 수 있는 말이 금색으로 빛나고 도착할 칸에 고리가 생겨요. 말이나 그 칸을 터치하면 칸마다 통통 이동!\n상대 말 위에 딱 멈추면 잡아서 출발 대기로 돌려보내고 한 번 더 굴려요.\n출발 칸과 별 칸은 안전 — 여기서는 잡을 수 없어요.") { c, w, h, p ->
            // 위: 길 6칸, 빨강(발광) → 4칸 앞 파랑 잡기
            val u = h * 0.2f
            val x0 = (w - 6 * u) / 2f
            val ry = h * 0.38f
            for (k in 0 until 6) cell(c, x0 + (k + 0.5f) * u, ry, u, 0xFFFFFFFF.toInt(), p)
            for (k in 0 until 4) hop(c, x0 + (k + 0.5f) * u, x0 + (k + 1.5f) * u, ry - u * 0.3f, u * 0.25f, p)
            val ax = x0 + 0.5f * u
            val bx = x0 + 4.5f * u
            LudoArt.glow(c, ax, ry, u * 0.36f, p)
            LudoArt.drawPiece(c, ax, ry, u * 0.36f, RED, RED_DARK, p)
            p.style = Paint.Style.FILL
            p.color = RED
            p.alpha = 110
            c.drawCircle(bx, ry, u * 0.44f, p)
            p.alpha = 255
            LudoArt.drawPiece(c, bx, ry, u * 0.32f, BLUE, BLUE_DARK, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = u * 0.08f
            p.color = Theme.GOLD
            c.drawCircle(bx, ry, u * 0.44f, p)
            p.style = Paint.Style.FILL
            outlined(c, CAUGHT, bx - u * 0.2f, h * 0.13f, h * 0.12f, Theme.GOLD, p)
            sparkle(c, bx + u * 0.55f, h * 0.14f, h * 0.04f, p)
            // 아래: 별 칸에 빨강·파랑이 나란히 (안전)
            val sx = w * 0.2f
            val sy = h * 0.78f
            cell(c, sx, sy, u, 0xFFFFFFFF.toInt(), p)
            LudoArt.star(c, sx, sy, u * 0.38f, LudoArt.STAR_COLOR, p, path)
            LudoArt.drawPiece(c, sx - u * 0.22f, sy, u * 0.26f, RED, RED_DARK, p)
            LudoArt.drawPiece(c, sx + u * 0.22f, sy, u * 0.26f, BLUE, BLUE_DARK, p)
            outlined(c, SAFE, w * 0.62f, sy + h * 0.04f, h * 0.11f, Theme.TEXT, p)
        },
        RuleCard("말 4개를 먼저 집에", "자기 색 통로를 따라 가운데 집까지! 집에는 딱 맞는 눈으로만 들어가요 (넘치면 그 말은 못 움직여요).\n말 4개를 모두 넣은 순서로 순위가 정해져요. 3~4명이면 1등이 나온 뒤 [끝까지 진행 / 여기서 종료]를 골라요.") { c, w, h, p ->
            // 왼쪽: 빨강 통로 3칸 → 가운데 집 (빨강 삼각형에 말 3개)
            val u = h * 0.16f
            val cy = h * 0.52f
            val l = w * 0.03f
            for (k in 0 until 3) cell(c, l + (k + 0.5f) * u, cy, u, RED, p)
            val sl = l + 3 * u
            val st = cy - 1.5f * u
            val sr = sl + 3 * u
            val sb = cy + 1.5f * u
            val mx = sl + 1.5f * u
            for (s in 0 until 4) {
                path.reset()
                when (s) {
                    0 -> { path.moveTo(sl, sb); path.lineTo(sl, st) }
                    1 -> { path.moveTo(sl, st); path.lineTo(sr, st) }
                    2 -> { path.moveTo(sr, st); path.lineTo(sr, sb) }
                    else -> { path.moveTo(sr, sb); path.lineTo(sl, sb) }
                }
                path.lineTo(mx, cy)
                path.close()
                p.style = Paint.Style.FILL
                p.color = SEAT_COLORS[s]
                c.drawPath(path, p)
            }
            for (k in 0 until 3) {
                LudoArt.drawPiece(c, sl + (LudoBoard.GOAL_X[0][k] - 6f) * u, st + (LudoBoard.GOAL_Y[0][k] - 6f) * u, u * 0.26f, RED, RED_DARK, p)
            }
            // 마지막 말: 통로 끝에서 발광, 집 칸에 고리
            val px = l + 1.5f * u
            LudoArt.glow(c, px, cy, u * 0.38f, p)
            LudoArt.drawPiece(c, px, cy, u * 0.38f, RED, RED_DARK, p)
            hop(c, px, sl + (LudoBoard.GOAL_X[0][3] - 6f) * u, cy - u * 0.3f, u * 0.3f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = u * 0.08f
            p.color = Theme.GOLD
            c.drawCircle(sl + (LudoBoard.GOAL_X[0][3] - 6f) * u, cy, u * 0.3f, p)
            p.style = Paint.Style.FILL
            Ui.drawCrown(c, mx, st - h * 0.03f, h * 0.2f, path, p)
            // 오른쪽: 주사위 2 + 딱 맞게! + 1위
            val rx = w * 0.84f
            LudoArt.drawDie(c, rx, h * 0.3f, h * 0.24f, 2, 8f, p)
            outlined(c, EXACT, rx, h * 0.6f, h * 0.095f, Theme.GOLD, p)
            outlined(c, FIRST, rx, h * 0.85f, h * 0.13f, Theme.GOLD, p)
        },
    )
}
