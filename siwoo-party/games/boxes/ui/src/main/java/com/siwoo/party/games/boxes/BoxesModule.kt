package com.siwoo.party.games.boxes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.boxes.ui.BoxesActivity

/** 땅따먹기: 모음집 등록 정보 */
object BoxesModule : GameModule {
    override val id = "boxes"
    override val name = "땅따먹기"
    override val tagline = "점 사이에 선을 긋고, 네 변을 닫아 내 땅으로!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val minutes = "6분"
    override val color = 0xFF5E35B1.toInt()
    override val activityClass = BoxesActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val GREEN = Theme.PLAYER_COLORS[2]
    private val YELLOW = Theme.PLAYER_COLORS[3]
    private val RED_DARK = Theme.shade(RED, 0.5f)
    private val BLUE_DARK = Theme.shade(BLUE, 0.5f)
    private const val GA = "가"
    private const val NA = "나"
    private const val DA = "다"
    private const val AGAIN = "한 번 더!"
    private val crownPath = Path()

    /** 3인 판 예시 (규칙 그림 3): 3×3칸 주인 (0 빨강, 1 파랑, 2 초록) */
    private val RULE3_OWNERS = intArrayOf(0, 0, 1, 2, 0, 1, 0, 2, 1)
    private val RULE3_COLORS = intArrayOf(RED, BLUE, GREEN)
    private val RULE3_INITIALS = arrayOf(GA, NA, DA)

    // ── 그리기 도우미 (할당 없음) ──

    /** 금색 점 */
    private fun dot(c: Canvas, x: Float, y: Float, r: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD_DARK
        c.drawCircle(x, y + r * 0.18f, r * 1.12f, p)
        p.color = Theme.GOLD
        c.drawCircle(x, y, r, p)
        p.color = Theme.GOLD_LIGHT
        c.drawCircle(x - r * 0.3f, y - r * 0.32f, r * 0.38f, p)
    }

    /** 점 격자 (cols × rows 칸) */
    private fun dots(c: Canvas, l: Float, t: Float, cell: Float, cols: Int, rows: Int, p: Paint) {
        for (r in 0..rows) for (k in 0..cols) dot(c, l + k * cell, t + r * cell, cell * 0.1f, p)
    }

    /** 빈 자리 (희미한 선) */
    private fun slots(c: Canvas, l: Float, t: Float, cell: Float, cols: Int, rows: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = cell * 0.03f
        p.color = 0x2EFFFFFF
        for (r in 0..rows) c.drawLine(l, t + r * cell, l + cols * cell, t + r * cell, p)
        for (k in 0..cols) c.drawLine(l + k * cell, t, l + k * cell, t + rows * cell, p)
        p.style = Paint.Style.FILL
    }

    /** 그은 선 (어두운 테두리 + 색) */
    private fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, dark: Int, w: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = dark
        p.strokeWidth = w * 1.45f
        c.drawLine(x0, y0, x1, y1, p)
        p.color = color
        p.strokeWidth = w
        c.drawLine(x0, y0, x1, y1, p)
        p.style = Paint.Style.FILL
    }

    /** 점선 (미리보기) — 짧은 선분을 이어서 그림 */
    private fun dashed(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, w: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        p.alpha = 70
        p.strokeWidth = w * 3f
        c.drawLine(x0, y0, x1, y1, p)
        p.alpha = 230
        p.strokeWidth = w
        val n = 5
        for (k in 0 until n) {
            val a = k / n.toFloat()
            val b = (k + 0.55f) / n
            c.drawLine(x0 + (x1 - x0) * a, y0 + (y1 - y0) * a, x0 + (x1 - x0) * b, y0 + (y1 - y0) * b, p)
        }
        p.alpha = 255
        p.style = Paint.Style.FILL
    }

    /** 차지한 칸 (색 + 첫 글자) */
    private fun box(c: Canvas, l: Float, t: Float, cell: Float, color: Int, initial: String, p: Paint) {
        val i = cell * 0.1f
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = 225
        c.drawRoundRect(l + i, t + i, l + cell - i, t + cell - i, cell * 0.1f, cell * 0.1f, p)
        p.color = 0x26FFFFFF
        c.drawRoundRect(l + i, t + i, l + cell - i, t + cell / 2f, cell * 0.1f, cell * 0.1f, p)
        p.color = Theme.textOn(color)
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = cell * 0.46f
        c.drawText(initial, l + cell / 2f, t + cell / 2f + p.textSize * 0.36f, p)
        p.textAlign = Paint.Align.LEFT
    }

    /** 네 갈래 반짝이 */
    private fun twinkle(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD_LIGHT
        c.drawOval(x - s * 0.22f, y - s, x + s * 0.22f, y + s, p)
        c.drawOval(x - s, y - s * 0.22f, x + s, y + s * 0.22f, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val cell = size * 0.3f
        val l = cx - cell
        val t = cy - cell
        val w = cell * 0.12f
        // 왼쪽 위 빨강 칸, 오른쪽 아래 파랑 칸
        box(canvas, l, t, cell, RED, GA, paint)
        box(canvas, l + cell, t + cell, cell, BLUE, NA, paint)
        line(canvas, l, t, l + cell, t, RED, RED_DARK, w, paint)
        line(canvas, l, t, l, t + cell, RED, RED_DARK, w, paint)
        line(canvas, l, t + cell, l + cell, t + cell, BLUE, BLUE_DARK, w, paint)
        line(canvas, l + cell, t, l + cell, t + cell, RED, RED_DARK, w, paint)
        line(canvas, l + cell, t + cell * 2, l + cell * 2, t + cell * 2, BLUE, BLUE_DARK, w, paint)
        line(canvas, l + cell * 2, t + cell, l + cell * 2, t + cell * 2, RED, RED_DARK, w, paint)
        line(canvas, l + cell, t + cell, l + cell * 2, t + cell, BLUE, BLUE_DARK, w, paint)
        // 그어지려는 선 (점선)
        dashed(canvas, l + cell, t, l + cell * 2, t, Theme.GOLD_LIGHT, w * 0.9f, paint)
        dots(canvas, l, t, cell, 2, 2, paint)
        twinkle(canvas, l + cell * 2.35f, t - cell * 0.25f, size * 0.07f, paint)
        twinkle(canvas, l - cell * 0.3f, t + cell * 2.3f, size * 0.05f, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("차례에 선 하나 긋기", "자기 차례에 이웃한 두 점 사이에 선을 하나 그어요.\n손가락을 대면 가장 가까운 빈 선이 점선으로 보여요. 손을 떼면 확정, 먼 곳에서 떼면 취소!") { c, w, h, p ->
            val cell = h * 0.24f
            val cols = 4
            val rows = 3
            val l = (w - cell * cols) / 2f
            val t = (h - cell * rows) / 2f
            val lw = cell * 0.11f
            slots(c, l, t, cell, cols, rows, p)
            line(c, l, t, l + cell, t, RED, RED_DARK, lw, p)
            line(c, l + cell * 2, t + cell, l + cell * 2, t + cell * 2, BLUE, BLUE_DARK, lw, p)
            line(c, l + cell * 3, t, l + cell * 4, t, BLUE, BLUE_DARK, lw, p)
            line(c, l, t + cell * 3, l + cell, t + cell * 3, RED, RED_DARK, lw, p)
            line(c, l + cell, t + cell, l + cell, t + cell * 2, RED, RED_DARK, lw, p)
            // 미리보기 (파랑 차례)
            dashed(c, l + cell * 2, t + cell * 2, l + cell * 3, t + cell * 2, BLUE, lw, p)
            dots(c, l, t, cell, cols, rows, p)
            p.style = Paint.Style.STROKE
            p.color = BLUE
            p.strokeWidth = lw * 0.55f
            c.drawCircle(l + cell * 2, t + cell * 2, cell * 0.17f, p)
            c.drawCircle(l + cell * 3, t + cell * 2, cell * 0.17f, p)
            // 손가락 대신 터치 물결
            p.color = Theme.TEXT
            p.strokeWidth = h * 0.016f
            val fx = l + cell * 2.55f
            val fy = t + cell * 2.3f
            c.drawCircle(fx, fy, cell * 0.16f, p)
            p.alpha = 120
            c.drawCircle(fx, fy, cell * 0.3f, p)
            p.alpha = 255
            p.style = Paint.Style.FILL
        },
        RuleCard("칸을 닫으면 내 땅 + 한 번 더", "칸의 네 변을 마지막으로 이은 사람이 그 칸을 차지해요. 내 색으로 칠해지고 이름 첫 글자가 새겨져요.\n칸을 닫으면 한 번 더! 선 하나로 두 칸을 한꺼번에 얻을 수도 있어요.") { c, w, h, p ->
            val cell = h * 0.36f
            val l = (w - cell * 2) / 2f
            val t = h * 0.36f
            val lw = cell * 0.09f
            box(c, l, t, cell, RED, GA, p)
            box(c, l + cell, t, cell, RED, GA, p)
            line(c, l, t, l + cell, t, BLUE, BLUE_DARK, lw, p)
            line(c, l + cell, t, l + cell * 2, t, RED, RED_DARK, lw, p)
            line(c, l, t + cell, l + cell, t + cell, RED, RED_DARK, lw, p)
            line(c, l + cell, t + cell, l + cell * 2, t + cell, BLUE, BLUE_DARK, lw, p)
            line(c, l, t, l, t + cell, BLUE, BLUE_DARK, lw, p)
            line(c, l + cell * 2, t, l + cell * 2, t + cell, RED, RED_DARK, lw, p)
            // 방금 그은 가운데 선 (흰 빛)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.TEXT
            p.alpha = 100
            p.strokeWidth = lw * 2.6f
            c.drawLine(l + cell, t, l + cell, t + cell, p)
            p.alpha = 255
            line(c, l + cell, t, l + cell, t + cell, RED, RED_DARK, lw, p)
            dots(c, l, t, cell, 2, 1, p)
            twinkle(c, l - cell * 0.25f, t + cell * 1.25f, h * 0.05f, p)
            twinkle(c, l + cell * 2.3f, t - cell * 0.1f, h * 0.06f, p)
            twinkle(c, l + cell * 1.5f, t + cell * 1.3f, h * 0.04f, p)
            // "한 번 더!"
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.14f
            p.style = Paint.Style.STROKE
            p.strokeJoin = Paint.Join.ROUND
            p.strokeWidth = p.textSize * 0.16f
            p.color = Theme.INK
            c.drawText(AGAIN, w / 2f, h * 0.22f, p)
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            c.drawText(AGAIN, w / 2f, h * 0.22f, p)
            p.textAlign = Paint.Align.LEFT
        },
        RuleCard("땅이 많은 사람이 1등", "모든 선을 그으면 끝! 한 사람씩 칸을 세어 많이 차지한 순서로 순위 (같으면 공동 순위).\n2인 5×5칸, 3~4인 6×6칸. 세 번째 변을 그으면 상대가 칸을 가져가요, 조심!") { c, w, h, p ->
            val cell = h * 0.2f
            val l = (w - cell * 3) / 2f
            val t = h * 0.3f
            for (k in 0 until 9) {
                val o = RULE3_OWNERS[k]
                box(c, l + (k % 3) * cell, t + (k / 3) * cell, cell, RULE3_COLORS[o], RULE3_INITIALS[o], p)
            }
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = cell * 0.08f
            p.color = 0xCCFFFFFF.toInt()
            for (r in 0..3) c.drawLine(l, t + r * cell, l + cell * 3, t + r * cell, p)
            for (k in 0..3) c.drawLine(l + k * cell, t, l + k * cell, t + cell * 3, p)
            p.style = Paint.Style.FILL
            dots(c, l, t, cell, 3, 3, p)
            // 빨강 4칸 → 왕관
            Ui.drawCrown(c, w * 0.16f, h * 0.36f, h * 0.2f, crownPath, p)
            p.color = RED
            c.drawCircle(w * 0.16f, h * 0.52f, h * 0.075f, p)
            p.color = Theme.TEXT
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.09f
            c.drawText(GA, w * 0.16f, h * 0.52f + p.textSize * 0.36f, p)
            p.color = Theme.GOLD
            p.textSize = h * 0.11f
            c.drawText(FOUR, w * 0.16f, h * 0.76f, p)
            p.textAlign = Paint.Align.LEFT
        },
    )

    private const val FOUR = "4칸"
}
