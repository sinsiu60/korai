package com.siwoo.party.games.polyblock

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.polyblock.ui.BlockArt
import com.siwoo.party.games.polyblock.ui.PolyblockActivity

/** 조각 배치 대결: 모음집 등록 정보 (2~4인, 차례대로) */
object PolyblockModule : GameModule {
    override val id = "polyblock"
    override val name = "조각 배치 대결"
    override val tagline = "모서리에서 출발! 꼭짓점으로만 이어 조각을 가장 많이 놓는 사람이 승리"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "15~25분"
    override val color = 0xFF2F6FD6.toInt()
    override val activityClass = PolyblockActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val GREEN = Theme.PLAYER_COLORS[2]
    private val YELLOW = Theme.PLAYER_COLORS[3]

    // 아이콘: 6×6 판 네 모서리에서 출발한 바람개비 조각 + 가운데에서 꼭짓점으로 만나는 네 색
    private val ICON_RED = intArrayOf(0, 0, 1, 0, 2, 0, 0, 1, 1, 1, 2, 2)
    private val ICON_BLUE = intArrayOf(5, 0, 5, 1, 5, 2, 4, 0, 4, 1, 3, 2)
    private val ICON_GREEN = intArrayOf(5, 5, 4, 5, 3, 5, 5, 4, 4, 4, 3, 3)
    private val ICON_YELLOW = intArrayOf(0, 5, 0, 4, 0, 3, 1, 5, 1, 4, 2, 3)

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val half = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawRoundRect(cx - half + size * 0.02f, cy - half + size * 0.04f, cx + half + size * 0.02f, cy + half + size * 0.04f, size * 0.1f, size * 0.1f, paint)
        paint.color = 0xFF141E4E.toInt()
        canvas.drawRoundRect(cx - half, cy - half, cx + half, cy + half, size * 0.1f, size * 0.1f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.03f
        paint.color = Theme.GOLD
        canvas.drawRoundRect(cx - half, cy - half, cx + half, cy + half, size * 0.1f, size * 0.1f, paint)
        paint.style = Paint.Style.FILL
        val cs = half * 2f * 0.84f / 6f
        val l = cx - cs * 3f
        val t = cy - cs * 3f
        BlockArt.cells(canvas, paint, ICON_RED, l, t, cs, RED)
        BlockArt.cells(canvas, paint, ICON_BLUE, l, t, cs, BLUE)
        BlockArt.cells(canvas, paint, ICON_GREEN, l, t, cs, GREEN)
        BlockArt.cells(canvas, paint, ICON_YELLOW, l, t, cs, YELLOW)
    }

    // ── 규칙 그림 도우미 ──

    /** 그림 영역 가운데 cols×rows 격자. 칸 크기를 돌려주고 왼쪽 위는 gl·gt 에 */
    private var gl = 0f
    private var gt = 0f

    private fun grid(c: Canvas, p: Paint, w: Float, h: Float, cols: Int, rows: Int, left: Float = -1f): Float {
        val cs = minOf(w * 0.96f / cols, h * 0.92f / rows)
        gl = if (left >= 0f) left else (w - cs * cols) / 2f
        gt = (h - cs * rows) / 2f
        p.style = Paint.Style.FILL
        p.color = 0xFF141E4E.toInt()
        c.drawRect(gl, gt, gl + cs * cols, gt + cs * rows, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, cs * 0.04f)
        p.color = 0xFF2C3A78.toInt()
        for (i in 1 until cols) c.drawLine(gl + i * cs, gt, gl + i * cs, gt + rows * cs, p)
        for (j in 1 until rows) c.drawLine(gl, gt + j * cs, gl + cols * cs, gt + j * cs, p)
        p.style = Paint.Style.FILL
        return cs
    }

    /** 미리보기 칸들 (반투명 + 초록/빨강 테두리) */
    private fun preview(c: Canvas, p: Paint, xy: IntArray, cs: Float, color: Int, ok: Boolean) {
        BlockArt.cells(c, p, xy, gl, gt, cs, color, 150)
        p.style = Paint.Style.STROKE
        p.strokeWidth = cs * 0.12f
        p.color = if (ok) Theme.GREEN else Theme.RED
        var i = 0
        while (i + 1 < xy.size) {
            val l = gl + xy[i] * cs
            val t = gt + xy[i + 1] * cs
            c.drawRect(l + cs * 0.06f, t + cs * 0.06f, l + cs * 0.94f, t + cs * 0.94f, p)
            i += 2
        }
        p.style = Paint.Style.FILL
    }

    /** 체크 표시 / X 표시 */
    private fun mark(c: Canvas, p: Paint, x: Float, y: Float, r: Float, ok: Boolean) {
        p.style = Paint.Style.FILL
        p.color = if (ok) Theme.GREEN_DARK else Theme.RED_DARK
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.28f
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFFFFFFFF.toInt()
        if (ok) {
            c.drawLine(x - r * 0.45f, y, x - r * 0.1f, y + r * 0.38f, p)
            c.drawLine(x - r * 0.1f, y + r * 0.38f, x + r * 0.48f, y - r * 0.35f, p)
        } else {
            c.drawLine(x - r * 0.4f, y - r * 0.4f, x + r * 0.4f, y + r * 0.4f, p)
            c.drawLine(x - r * 0.4f, y + r * 0.4f, x + r * 0.4f, y - r * 0.4f, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /** 외곽선 있는 굵은 글자 */
    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF0B1340.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 둥근 버튼 모양 */
    private fun pill(c: Canvas, p: Paint, text: String, cx: Float, cy: Float, hw: Float, hh: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = BlockArt.shade(color, 0.6f)
        c.drawRoundRect(cx - hw, cy - hh + hh * 0.25f, cx + hw, cy + hh + hh * 0.25f, hh * 0.7f, hh * 0.7f, p)
        p.color = color
        c.drawRoundRect(cx - hw, cy - hh, cx + hw, cy + hh, hh * 0.7f, hh * 0.7f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = hh * 1.05f
        c.drawText(text, cx, cy + hh * 0.37f, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    // 규칙 1: 놓인 조각 + 초록(OK)·빨강(안 됨) 미리보기
    private val R1_PLACED = intArrayOf(0, 0, 1, 0, 0, 1)
    private val R1_OK = intArrayOf(2, 1, 3, 1, 4, 1, 3, 2)
    private val R1_BAD = intArrayOf(0, 2, 0, 3, 0, 4)

    // 규칙 2: 빨강 (모서리 정사각형 + 꼭짓점으로 이은 막대), 파랑은 빨강 옆에 붙어도 OK, 빨강 유령은 변이 닿아 X
    private val R2_RED = intArrayOf(0, 0, 1, 0, 0, 1, 1, 1, 2, 2, 2, 3, 2, 4)
    private val R2_BLUE = intArrayOf(3, 2, 3, 3, 3, 4, 4, 4)
    private val R2_GHOST = intArrayOf(2, 0, 3, 0)

    // 규칙 3: 남은 조각들
    private val R3_FEW = intArrayOf(0, 0, 1, 0)
    private val R3_MANY = intArrayOf(0, 0, 1, 0, 2, 0, 0, 1, 2, 1)
    private val R3_RANK = arrayOf("1", "2", "3")
    private val R3_SCORE = arrayOf("−15", "2칸", "5칸")

    override val rules: List<RuleCard> = listOf(
        RuleCard("고르고 · 돌리고 · 놓기", "오른쪽에서 내 조각을 골라요.\n[회전]·[뒤집기]로 모양을 맞추고\n판을 누르거나 끌면 미리보기!\n초록이면 [놓기], 빨강이면 안 돼요.\n판의 작은 점 = 놓을 수 있는 자리") { c, w, h, p ->
            val cs = grid(c, p, w * 0.62f, h, 6, 6, w * 0.02f)
            BlockArt.cells(c, p, R1_PLACED, gl, gt, cs, RED)
            preview(c, p, R1_OK, cs, RED, true)
            preview(c, p, R1_BAD, cs, RED, false)
            mark(c, p, gl + 4.6f * cs, gt + 2.6f * cs, cs * 0.42f, true)
            mark(c, p, gl + 1.15f * cs, gt + 4.4f * cs, cs * 0.42f, false)
            // 꼭짓점 자리 점
            p.color = 0xCCFFFFFF.toInt()
            c.drawCircle(gl + 1.5f * cs, gt + 5.5f * cs, cs * 0.22f, p)
            p.color = RED
            c.drawCircle(gl + 1.5f * cs, gt + 5.5f * cs, cs * 0.16f, p)
            // 버튼
            val bx = w * 0.82f
            pill(c, p, "회전", bx, h * 0.22f, w * 0.14f, h * 0.085f, Theme.BLUE)
            pill(c, p, "뒤집기", bx, h * 0.5f, w * 0.14f, h * 0.085f, Theme.BLUE)
            pill(c, p, "놓기", bx, h * 0.78f, w * 0.14f, h * 0.085f, Theme.GREEN_DARK)
        },
        RuleCard("모서리에서, 꼭짓점으로", "첫 조각은 내 모서리 칸을 덮어요.\n(내 색 꺾쇠가 있는 칸)\n그다음엔 내 조각과 꼭짓점으로만!\n내 조각과 변이 닿으면 안 돼요.\n남의 조각과는 붙어도 괜찮아요.") { c, w, h, p ->
            val cs = grid(c, p, w * 0.64f, h, 6, 6, w * 0.02f)
            // 내 모서리 꺾쇠
            p.style = Paint.Style.STROKE
            p.strokeWidth = cs * 0.22f
            p.strokeCap = Paint.Cap.ROUND
            p.color = RED
            c.drawLine(gl, gt, gl + cs * 2.2f, gt, p)
            c.drawLine(gl, gt, gl, gt + cs * 2.2f, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
            BlockArt.cells(c, p, R2_RED, gl, gt, cs, RED)
            BlockArt.cells(c, p, R2_BLUE, gl, gt, cs, BLUE)
            preview(c, p, R2_GHOST, cs, RED, false)
            // 꼭짓점 닿음 (금색 고리) → OK, 변 닿음 → X
            p.style = Paint.Style.STROKE
            p.strokeWidth = cs * 0.1f
            p.color = Theme.GOLD
            c.drawCircle(gl + 2f * cs, gt + 2f * cs, cs * 0.3f, p)
            p.style = Paint.Style.FILL
            mark(c, p, gl + 1.25f * cs, gt + 2.75f * cs, cs * 0.36f, true)
            mark(c, p, gl + 4.3f * cs, gt + 0.5f * cs, cs * 0.36f, false)
            mark(c, p, gl + 4.6f * cs, gt + 3.3f * cs, cs * 0.36f, true)
            shout(c, p, "꼭짓점 OK", w * 0.83f, h * 0.34f, h * 0.085f, Theme.GREEN)
            shout(c, p, "변은 X", w * 0.83f, h * 0.56f, h * 0.085f, Theme.RED)
            shout(c, p, "남의 색 OK", w * 0.83f, h * 0.78f, h * 0.085f, Theme.GOLD_LIGHT)
        },
        RuleCard("남은 칸이 적을수록 1등", "놓을 곳이 없으면 자동 패스로 끝.\n모두 끝나면 남은 칸 수가 점수,\n적을수록 높은 순위예요.\n21개를 다 놓으면 보너스 −15점!\n판: 3·4인 20×20 · 2인 14×14") { c, w, h, p ->
            val row = h * 0.3f
            val cs = row * 0.36f
            val tx = w * 0.06f
            // 1등: −15 (다 놓음), 2등: 2칸 남음, 3등: 5칸 남음
            for (k in 0 until 3) {
                val y = h * 0.08f + k * row
                p.color = if (k == 0) 0x55FFC93C else 0x33FFFFFF
                c.drawRoundRect(tx, y, w * 0.94f, y + row * 0.86f, row * 0.2f, row * 0.2f, p)
                val col = when (k) { 0 -> GREEN; 1 -> BLUE; else -> RED }
                p.color = col
                c.drawCircle(tx + row * 0.45f, y + row * 0.43f, row * 0.26f, p)
                shout(c, p, R3_RANK[k], tx + row * 0.45f, y + row * 0.43f + row * 0.13f, row * 0.36f, 0xFFFFFFFF.toInt())
                when (k) {
                    0 -> shout(c, p, "21개 모두!", w * 0.42f, y + row * 0.58f, row * 0.4f, Theme.GOLD_LIGHT)
                    1 -> BlockArt.cells(c, p, R3_FEW, w * 0.3f, y + row * 0.43f - cs / 2f, cs, col)
                    else -> BlockArt.cells(c, p, R3_MANY, w * 0.3f, y + row * 0.43f - cs, cs, col)
                }
                shout(c, p, R3_SCORE[k], w * 0.8f, y + row * 0.6f, row * 0.48f, if (k == 0) Theme.GOLD else 0xFFFFFFFF.toInt())
            }
        },
    )
}
