package com.siwoo.party.games.umul

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.umul.ui.UmulActivity
import com.siwoo.party.games.umul.ui.UmulArt

/** 우물고누: 모음집 등록 정보 (2인 전용, 차례대로, 3판 2선승) */
object UmulModule : GameModule {
    override val id = "umul"
    override val name = "우물고누"
    override val tagline = "말 두 개로 상대를 꽁꽁 가둬라!"
    override val playerCounts = GameConfig.PLAYERS..GameConfig.PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "3분"
    override val color = 0xFF8D5A2B.toInt()
    override val activityClass = UmulActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val arrow = Path()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF3A2410.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 작은 나무판 + 말 넷
        val side = r * 0.88f
        val l = cx - side / 2f
        val t = cy - side / 2f - r * 0.06f
        UmulArt.board(canvas, paint, l, t, side)
        val sr = side * 0.15f
        UmulArt.stone(canvas, paint, UmulArt.px(l, side, UmulBoard.TL), UmulArt.py(t, side, UmulBoard.TL), sr, RED)
        UmulArt.stone(canvas, paint, UmulArt.px(l, side, UmulBoard.BL), UmulArt.py(t, side, UmulBoard.BL), sr, RED)
        UmulArt.stone(canvas, paint, UmulArt.px(l, side, UmulBoard.TR), UmulArt.py(t, side, UmulBoard.TR), sr, BLUE)
        UmulArt.stone(canvas, paint, UmulArt.px(l, side, UmulBoard.C), UmulArt.py(t, side, UmulBoard.C), sr, BLUE)
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

    /** 규칙 그림용 판 + 말 (owners: TL, TR, BL, BR, C 순서의 0=빨강 1=파랑 −1=빈 점) */
    private fun scene(c: Canvas, p: Paint, cx: Float, cy: Float, side: Float, o0: Int, o1: Int, o2: Int, o3: Int, o4: Int) {
        val l = cx - side / 2f
        val t = cy - side / 2f
        UmulArt.board(c, p, l, t, side)
        val sr = side * 0.14f
        for (i in 0 until UmulBoard.POINTS) {
            val o = when (i) { 0 -> o0; 1 -> o1; 2 -> o2; 3 -> o3; else -> o4 }
            if (o < 0) continue
            UmulArt.stone(c, p, UmulArt.px(l, side, i), UmulArt.py(t, side, i), sr, if (o == 0) RED else BLUE)
        }
    }

    /** 점 a → b 금색 화살표 (선을 따라) */
    private fun move(c: Canvas, p: Paint, l: Float, t: Float, side: Float, a: Int, b: Int, color: Int) {
        val x0 = UmulArt.px(l, side, a)
        val y0 = UmulArt.py(t, side, a)
        val x1 = UmulArt.px(l, side, b)
        val y1 = UmulArt.py(t, side, b)
        val dx = x1 - x0
        val dy = y1 - y0
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        val ux = dx / len
        val uy = dy / len
        val sx = x0 + ux * side * 0.2f
        val sy = y0 + uy * side * 0.2f
        val ex = x1 - ux * side * 0.12f
        val ey = y1 - uy * side * 0.12f
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = side * 0.05f
        p.color = color
        c.drawLine(sx, sy, ex - ux * side * 0.06f, ey - uy * side * 0.06f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        val h = side * 0.1f
        arrow.reset()
        arrow.moveTo(ex, ey)
        arrow.lineTo(ex - ux * h - uy * h * 0.6f, ey - uy * h + ux * h * 0.6f)
        arrow.lineTo(ex - ux * h + uy * h * 0.6f, ey - uy * h - ux * h * 0.6f)
        arrow.close()
        c.drawPath(arrow, p)
    }

    /** 빨간 ✕ */
    private fun cross(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = Theme.RED
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.3f
        p.color = 0xFFFFFFFF.toInt()
        c.drawLine(x - r * 0.42f, y - r * 0.42f, x + r * 0.42f, y + r * 0.42f, p)
        c.drawLine(x + r * 0.42f, y - r * 0.42f, x - r * 0.42f, y + r * 0.42f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("선을 따라 한 칸", "말은 한 사람에 2개씩.\n내 차례에 말 하나를 선을 따라\n이어진 빈 점으로 한 칸 옮겨요.\n아래 우물 쪽은 길이 없어요!") { c, w, h, p ->
            val side = h * 0.6f
            val cx = w * 0.5f
            val cy = h * 0.43f
            scene(c, p, cx, cy, side, 0, 1, 0, 1, -1)
            val l = cx - side / 2f
            val t = cy - side / 2f
            move(c, p, l, t, side, UmulBoard.TL, UmulBoard.C, Theme.GOLD)
            // 우물 쪽 ✕ (길 없음)
            cross(c, p, cx, t + side * 1.02f, side * 0.07f)
        },
        RuleCard("가두면 승리!", "차례가 왔는데 움직일 말이\n하나도 없으면 그 판은 져요.\n단, 첫 수로 상대를 곧바로\n가두는 수는 금지예요.") { c, w, h, p ->
            val side = h * 0.52f
            // 왼쪽: 갇힌 판 (빨강 TL·BL 은 빈 점 BR 과 안 이어짐)
            val cx1 = w * 0.3f
            val cy = h * 0.42f
            scene(c, p, cx1, cy, side, 0, 1, 0, -1, 1)
            val l1 = cx1 - side / 2f
            val t1 = cy - side / 2f
            p.style = Paint.Style.STROKE
            p.strokeWidth = side * 0.035f
            p.color = Theme.RED
            c.drawCircle(UmulArt.px(l1, side, UmulBoard.TL), UmulArt.py(t1, side, UmulBoard.TL), side * 0.18f, p)
            c.drawCircle(UmulArt.px(l1, side, UmulBoard.BL), UmulArt.py(t1, side, UmulBoard.BL), side * 0.18f, p)
            p.style = Paint.Style.FILL
            shout(c, p, "갇힘!", cx1, h * 0.95f, h * 0.13f, Theme.RED)
            // 오른쪽: 첫 수 금지 (우물 옆 말을 가운데로)
            val cx2 = w * 0.72f
            scene(c, p, cx2, cy, side, 0, 1, 0, 1, -1)
            val l2 = cx2 - side / 2f
            val t2 = cy - side / 2f
            move(c, p, l2, t2, side, UmulBoard.BL, UmulBoard.C, Theme.RED)
            cross(c, p, UmulArt.px(l2, side, UmulBoard.BL) + side * 0.13f, UmulArt.py(t2, side, UmulBoard.BL) - side * 0.13f, side * 0.08f)
            shout(c, p, "첫 수 금지", cx2, h * 0.95f, h * 0.13f, Theme.GOLD)
        },
        RuleCard("3판 2선승", "판마다 먼저 두는 사람이 바뀌어요.\n2판을 먼저 이기면 최종 승리!\n" + GameConfig.MOVE_LIMIT + "수 안에 아무도 못 가두면\n무승부 판 — 다음 판으로.") { c, w, h, p ->
            // 두 사람: 위에 돌, 아래에 이긴 판 동그라미 (빨강 ●● / 파랑 ●○)
            val dr = h * 0.065f
            val sy = h * 0.4f
            val dy = h * 0.68f
            for (side in 0..1) {
                val cx = if (side == 0) w * 0.27f else w * 0.73f
                UmulArt.stone(c, p, cx, sy, h * 0.12f, if (side == 0) RED else BLUE)
                for (k in 0..1) {
                    val x = cx + (k - 0.5f) * dr * 2.8f
                    val won = side == 0 || k == 0
                    if (won) {
                        p.style = Paint.Style.FILL
                        p.color = Theme.GOLD_DARK
                        c.drawCircle(x, dy + dr * 0.12f, dr, p)
                        p.color = Theme.GOLD
                        c.drawCircle(x, dy, dr, p)
                    } else {
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = dr * 0.22f
                        p.color = Theme.TEXT_SUB
                        c.drawCircle(x, dy, dr * 0.88f, p)
                        p.style = Paint.Style.FILL
                    }
                }
            }
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            com.siwoo.party.common.ui.Ui.drawCrown(c, w * 0.27f, h * 0.22f, h * 0.18f, arrow, p)
            shout(c, p, "2 : 1", w * 0.5f, sy + h * 0.06f, h * 0.15f, Theme.TEXT)
            shout(c, p, "최종 승리!", w * 0.27f, h * 0.95f, h * 0.12f, Theme.GOLD)
        },
    )
}
