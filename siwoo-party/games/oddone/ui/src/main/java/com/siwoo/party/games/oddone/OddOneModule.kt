package com.siwoo.party.games.oddone

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.oddone.ui.OddBoardView
import com.siwoo.party.games.oddone.ui.OddOneActivity
import com.siwoo.party.games.oddone.ui.ShapeArt

/** 하나만 다른 모양: 모음집 등록 정보 */
object OddOneModule : GameModule {
    override val id = "oddone"
    override val name = "하나만 다른 모양"
    override val tagline = "16개 중 딱 하나! 누가 먼저 찾을까?"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "3분"
    override val color = 0xFF2BB5A0.toInt()
    override val activityClass = OddOneActivity::class.java

    private const val TEAL = 0xFF38D3BE.toInt()
    private const val TILE = 0xFF253578.toInt()
    private val pink = Hsl.toArgb(330f, GameConfig.BASE_SAT, GameConfig.BASE_LIGHT)
    private val pinkLight = Hsl.toArgb(330f, GameConfig.BASE_SAT, GameConfig.BASE_LIGHT + 0.2f)

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.42f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawRoundRect(cx - r + r * 0.06f, cy - r + r * 0.1f, cx + r + r * 0.06f, cy + r + r * 0.1f, r * 0.3f, r * 0.3f, paint)
        paint.color = 0xFF0A1236.toInt()
        canvas.drawRoundRect(cx - r, cy - r, cx + r, cy + r, r * 0.3f, r * 0.3f, paint)
        // 3×3 하트, 가운데 오른쪽 하나만 기울어짐
        val pitch = r * 0.6f
        for (k in 0 until 9) {
            val x = cx + (k % 3 - 1) * pitch
            val y = cy + (k / 3 - 1) * pitch
            val odd = k == 5
            ShapeArt.drawOne(canvas, Shape.HEART, x, y, pitch * 0.4f, if (odd) 35f else 0f, pink, r * 0.04f, paint)
        }
        // 찾았다! 금색 고리
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.09f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx + pitch, cy, pitch * 0.5f, paint)
        paint.style = Paint.Style.FILL
    }

    /**
     * 규칙 그림: 4×4 격자 (odd 칸만 kind 로 다름).
     * mark 0 없음, 1 다른 하나 금색 고리 + "+1", 2 오답 X + 잠금
     */
    private fun grid(c: Canvas, gx: Float, gy: Float, side: Float, p: Paint, shape: Shape, odd: Int, kind: Diff, mark: Int, wrong: Int = -1) {
        p.style = Paint.Style.FILL
        p.color = 0xFF0A1236.toInt()
        c.drawRoundRect(gx - side * 0.03f, gy - side * 0.03f, gx + side * 1.03f, gy + side * 1.03f, side * 0.06f, side * 0.06f, p)
        val gap = side * 0.03f
        val pitch = (side + gap) / 4f
        val cell = pitch - gap
        for (k in 0 until 16) {
            val l = gx + (k % 4) * pitch
            val t = gy + (k / 4) * pitch
            p.style = Paint.Style.FILL
            p.color = TILE
            c.drawRoundRect(l, t, l + cell, t + cell, cell * 0.18f, cell * 0.18f, p)
            val isOdd = k == odd
            val col = if (isOdd && kind == Diff.COLOR) pinkLight else pink
            val rot = if (isOdd && kind == Diff.ROTATION) 30f else 0f
            val sc = if (isOdd && kind == Diff.SIZE) 1.25f else 1f
            ShapeArt.drawOne(c, shape, l + cell / 2f, t + cell / 2f, cell * OddBoardView.SHAPE_RADIUS * sc, rot, col, cell * 0.04f, p)
            if (mark == 1 && !isOdd) {
                p.color = 0x8C050A24.toInt()
                c.drawRoundRect(l, t, l + cell, t + cell, cell * 0.18f, cell * 0.18f, p)
            }
        }
        val ol = gx + (odd % 4) * pitch
        val ot = gy + (odd / 4) * pitch
        if (mark == 1) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = side * 0.025f
            p.color = Theme.GOLD
            c.drawRoundRect(ol - 2f, ot - 2f, ol + cell + 2f, ot + cell + 2f, cell * 0.18f, cell * 0.18f, p)
        }
        if (mark == 2) {
            p.style = Paint.Style.FILL
            p.color = 0x99050A24.toInt()
            c.drawRoundRect(gx - side * 0.03f, gy - side * 0.03f, gx + side * 1.03f, gy + side * 1.03f, side * 0.06f, side * 0.06f, p)
            val wx = gx + (wrong % 4) * pitch + cell / 2f
            val wy = gy + (wrong / 4) * pitch + cell / 2f
            val s = cell * 0.32f
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = s * 0.45f
            p.color = 0xFFFFFFFF.toInt()
            c.drawLine(wx - s, wy - s, wx + s, wy + s, p)
            c.drawLine(wx + s, wy - s, wx - s, wy + s, p)
            p.strokeWidth = s * 0.26f
            p.color = Theme.RED_DARK
            c.drawLine(wx - s, wy - s, wx + s, wy + s, p)
            c.drawLine(wx + s, wy - s, wx - s, wy + s, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.color = color
        c.drawText(s, x, y + size * 0.36f, p)
        p.textAlign = Paint.Align.LEFT
    }

    /** 색·기울기·크기 예시 하나: 위에 이름, 아래 보통 하트 + 다른 하트 (가운데 cx) */
    private fun example(c: Canvas, cx: Float, y: Float, s: Float, kind: Diff, p: Paint) {
        label(c, kind.label, cx, y - s * 0.5f, s * 0.36f, Theme.TEXT_SUB, p)
        val hy = y + s * 0.18f
        ShapeArt.drawOne(c, Shape.HEART, cx - s * 0.48f, hy, s * 0.32f, 0f, pink, s * 0.04f, p)
        val col = if (kind == Diff.COLOR) pinkLight else pink
        ShapeArt.drawOne(c, Shape.HEART, cx + s * 0.48f, hy, s * 0.32f * (if (kind == Diff.SIZE) 1.25f else 1f), if (kind == Diff.ROTATION) 30f else 0f, col, s * 0.04f, p)
    }

    /** 남은 시간 시계 (금색 고리 3/4 + 글자) */
    private fun clock(c: Canvas, cx: Float, cy: Float, r: Float, text: String, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.CARD
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.16f
        p.color = 0x33FFFFFF
        c.drawCircle(cx, cy, r * 0.92f, p)
        p.color = Theme.GOLD
        c.drawArc(cx - r * 0.92f, cy - r * 0.92f, cx + r * 0.92f, cy + r * 0.92f, -90f, 270f, false, p)
        label(c, text, cx, cy, r * 0.62f, Theme.GOLD, p)
    }

    /** 빨간 자물쇠 + 글자 */
    private fun lock(c: Canvas, cx: Float, cy: Float, s: Float, text: String, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.16f
        p.color = 0xFFFFFFFF.toInt()
        c.drawArc(cx - s * 0.32f, cy - s * 0.72f, cx + s * 0.32f, cy + s * 0.05f, 180f, 180f, false, p)
        c.drawLine(cx - s * 0.32f, cy - s * 0.33f, cx - s * 0.32f, cy, p)
        c.drawLine(cx + s * 0.32f, cy - s * 0.33f, cx + s * 0.32f, cy, p)
        p.style = Paint.Style.FILL
        p.color = Theme.RED
        c.drawRoundRect(cx - s * 0.5f, cy - s * 0.05f, cx + s * 0.5f, cy + s * 0.6f, s * 0.12f, s * 0.12f, p)
        label(c, text, cx, cy + s * 1.05f, s * 0.42f, Theme.RED, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("딱 하나만 달라요", "16개 모양이 모두 같은데 한 칸만\n색·기울기·크기 중 하나가 살짝 달라요.\n라운드가 갈수록 차이가 작아져요!") { c, w, h, p ->
            val side = h * 0.84f
            val gx = w * 0.05f
            grid(c, gx, h * 0.08f, side, p, Shape.HEART, 6, Diff.SIZE, 0)
            val s = h * 0.2f
            val ex = (gx + side * 1.03f + w) / 2f
            example(c, ex, h * 0.2f, s, Diff.COLOR, p)
            example(c, ex, h * 0.52f, s, Diff.ROTATION, p)
            example(c, ex, h * 0.84f, s, Diff.SIZE, p)
        },
        RuleCard("먼저 찾아 누르면 +1", "모두 같은 격자, 같은 자리예요.\n다른 하나를 가장 먼저 누른 사람 +1점!\n그 라운드는 바로 끝나요.") { c, w, h, p ->
            val side = h * 0.84f
            val gx = (w - side) / 2f
            grid(c, gx, h * 0.08f, side, p, Shape.HEART, 9, Diff.ROTATION, 1)
            label(c, "+1", gx + side + (w - gx - side) / 2f, h * 0.45f, h * 0.2f, Theme.GOLD, p)
        },
        RuleCard("틀리면 1초 잠금", "틀린 칸을 누르면 1초 동안 못 눌러요.\n10초 안에 아무도 못 찾으면 공개하고 넘어가요.\n15라운드 뒤 점수 순 (같으면 같은 순위).") { c, w, h, p ->
            val side = h * 0.84f
            val gx = (w - side) / 2f
            grid(c, gx, h * 0.08f, side, p, Shape.HEART, 2, Diff.COLOR, 2, wrong = 13)
            lock(c, gx + side + (w - gx - side) / 2f, h * 0.4f, h * 0.2f, "1초", p)
            clock(c, gx / 2f, h * 0.45f, minOf(gx * 0.4f, h * 0.15f), "10초", p)
        },
    )
}
