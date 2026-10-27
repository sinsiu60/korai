package com.siwoo.party.games.snakeladder

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.snakeladder.ui.BoardPainter
import com.siwoo.party.games.snakeladder.ui.SnakeActivity
import com.siwoo.party.games.snakeladder.ui.SnakeArt
import com.siwoo.party.games.snakeladder.ui.SnakeSettings
import com.siwoo.party.games.snakeladder.ui.SnakeShape

/** 뱀과 사다리: 모음집 등록 정보 */
object SnakeLadderModule : GameModule {
    override val id = "snakeladder"
    override val name = "뱀과 사다리"
    override val tagline = "사다리 타고 쑥! 뱀 만나면 주르륵~ 100번 칸에 먼저 도착!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "10분"
    override val color = 0xFF2E9E5B.toInt()
    override val activityClass = SnakeActivity::class.java

    override val settingsLabel: String get() = "뱀과 사다리 설정"

    override fun showSettings(popups: PopupLayer) {
        SnakeSettings.show(popups)
    }

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val GREEN = Theme.PLAYER_COLORS[2]
    private val YELLOW = Theme.PLAYER_COLORS[3]
    private val RED_DARK = Theme.shade(RED, 0.5f)
    private val BLUE_DARK = Theme.shade(BLUE, 0.5f)
    private val GREEN_DARK = Theme.shade(GREEN, 0.5f)
    private val YELLOW_DARK = Theme.shade(YELLOW, 0.5f)

    private val painter = BoardPainter()
    private val snake = SnakeShape()
    private val path = Path()
    private val pt = PointF()
    private val pt2 = PointF()
    private val rect = RectF()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    private const val AGAIN = "한 번 더!"
    private const val LADDER_UP = "쑥!"
    private const val SNAKE_DOWN = "주르륵"
    private val RANKS = arrayOf("1위", "2위", "3위")

    /** 외곽선 글자 */
    private fun outlined(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
        text.textSize = size
        text.style = Paint.Style.STROKE
        text.strokeJoin = Paint.Join.ROUND
        text.strokeWidth = size * 0.16f
        text.color = Theme.INK
        c.drawText(s, x, y, text)
        text.style = Paint.Style.FILL
        text.color = color
        c.drawText(s, x, y, text)
    }

    /** 주사위 (한 변 s, 눈 v) */
    private fun die(c: Canvas, x: Float, y: Float, s: Float, v: Int, deg: Float, p: Paint) {
        c.save()
        c.translate(x, y)
        c.rotate(deg)
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        rect.set(-s / 2 + s * 0.06f, -s / 2 + s * 0.09f, s / 2 + s * 0.06f, s / 2 + s * 0.09f)
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, p)
        p.color = 0xFFFFFFFF.toInt()
        rect.set(-s / 2, -s / 2, s / 2, s / 2)
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.05f
        p.color = Theme.GOLD_DARK
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, p)
        p.style = Paint.Style.FILL
        p.color = if (v == 1) 0xFFD32F2F.toInt() else Theme.INK
        val pr = s * 0.095f
        val a = s * 0.23f
        when (v) {
            6 -> for (k in -1..1) { c.drawCircle(-a, k * a, pr, p); c.drawCircle(a, k * a, pr, p) }
            5 -> { c.drawCircle(0f, 0f, pr, p); c.drawCircle(-a, -a, pr, p); c.drawCircle(a, a, pr, p); c.drawCircle(a, -a, pr, p); c.drawCircle(-a, a, pr, p) }
            3 -> { c.drawCircle(0f, 0f, pr, p); c.drawCircle(-a, -a, pr, p); c.drawCircle(a, a, pr, p) }
            else -> c.drawCircle(0f, 0f, pr * 1.3f, p)
        }
        c.restore()
    }

    /** 체크무늬 칸 (cols × rows, 왼쪽 위 l, t, 칸 u) */
    private fun checker(c: Canvas, l: Float, t: Float, cols: Int, rows: Int, u: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = SnakeArt.FRAME
        val fr = u * 0.18f
        c.drawRoundRect(l - fr, t - fr, l + cols * u + fr, t + rows * u + fr, fr, fr, p)
        for (r in 0 until rows) for (k in 0 until cols) {
            p.color = if ((r + k) % 2 == 0) SnakeArt.CELL_A else SnakeArt.CELL_B
            c.drawRect(l + k * u, t + r * u, l + (k + 1) * u + 0.5f, t + (r + 1) * u + 0.5f, p)
        }
    }

    /** 굽은 화살표 (from → to, 위로 볼록) */
    private fun arrow(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, bend: Float, w: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w
        p.color = color
        path.reset()
        path.moveTo(x1, y1)
        val mx = (x1 + x2) / 2f
        val my = (y1 + y2) / 2f - bend
        path.quadTo(mx, my, x2, y2)
        c.drawPath(path, p)
        val dx = x2 - mx
        val dy = y2 - my
        val l = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val ux = dx / l
        val uy = dy / l
        val hs = w * 3f
        c.drawLine(x2, y2, x2 - ux * hs - uy * hs * 0.6f, y2 - uy * hs + ux * hs * 0.6f, p)
        c.drawLine(x2, y2, x2 - ux * hs + uy * hs * 0.6f, y2 - uy * hs - ux * hs * 0.6f, p)
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        // 4×4 체크 판 + 사다리 + 뱀 + 주사위
        val u = size * 0.17f
        val l = cx - u * 2f
        val t = cy - u * 2f
        checker(canvas, l, t, 4, 4, u, paint)
        SnakeArt.drawLadder(canvas, l + u * 0.9f, t + u * 3.6f, l + u * 1.5f, t + u * 0.4f, u * 1.15f, 0f, paint)
        snake.build(l + u * 3.3f, t + u * 0.6f, l + u * 2.6f, t + u * 3.5f, u * 1.15f, false)
        snake.draw(canvas, SnakeArt.SNAKE_COLORS[0], SnakeArt.SNAKE_SPOTS[0], SnakeArt.SNAKE_BELLY[0], 0f, paint)
        SnakeArt.drawToken(canvas, l + u * 0.5f, t + u * 3.5f, u * 0.3f, RED, RED_DARK, false, paint)
        die(canvas, cx + u * 1.85f, cy + u * 1.85f, u * 1.25f, 6, 12f, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("주사위를 굴려 출발!", "차례가 되면 오른쪽 주사위(또는 [굴리기])를 눌러요. 나온 눈만큼 1번 칸부터 지그재그로 올라가요.\n6이 나오면 한 번 더 굴려요! 같은 칸에 여러 말이 함께 설 수 있어요.") { c, w, h, p ->
            // 왼쪽: 지그재그 판 아래쪽 (5×4) + 깡충 화살표, 오른쪽: 주사위 6 + 한 번 더
            val u = minOf(w * 0.6f / 5f, h * 0.84f / 4f)
            val l = w * 0.05f
            val t = (h - u * 4f) / 2f
            checker(c, l, t, 5, 4, u, p)
            text.textSize = u * 0.3f
            text.color = SnakeArt.NUM
            text.textAlign = Paint.Align.LEFT
            for (r in 0 until 4) for (k in 0 until 5) {
                val n = r * 5 + (if (r % 2 == 0) k else 4 - k) + 1
                c.drawText(BoardPainter.NUMS[n], l + u * k + u * 0.07f, t + u * (3 - r) + u * 0.32f, text)
            }
            text.textAlign = Paint.Align.CENTER
            // 1 → 2 → 3 → 4 깡충, 5 에서 위로 꺾여 6 → 7
            val by = t + u * 3.6f
            for (k in 0 until 3) arrow(c, l + u * (0.5f + k), by, l + u * (1.5f + k), by, u * 0.45f, u * 0.06f, Theme.GOLD_DARK, p)
            arrow(c, l + u * 4.75f, t + u * 3.4f, l + u * 4.75f, t + u * 2.7f, -u * 0.3f, u * 0.06f, Theme.GOLD_DARK, p)
            arrow(c, l + u * 4.4f, t + u * 2.6f, l + u * 3.6f, t + u * 2.6f, u * 0.3f, u * 0.06f, Theme.GOLD_DARK, p)
            SnakeArt.drawToken(c, l + u * 3.5f, t + u * 3.55f, u * 0.3f, RED, RED_DARK, true, p)
            SnakeArt.drawToken(c, l + u * 0.32f, t + u * 3.62f, u * 0.2f, BLUE, BLUE_DARK, false, p)
            SnakeArt.drawToken(c, l + u * 0.72f, t + u * 3.62f, u * 0.2f, GREEN, GREEN_DARK, false, p)
            val right = l + u * 5f
            val dx = right + (w - right) / 2f
            die(c, dx, h * 0.4f, minOf(h * 0.34f, (w - right) * 0.55f), 6, -10f, p)
            outlined(c, AGAIN, dx, h * 0.82f, minOf(h * 0.12f, (w - right) * 0.2f), Theme.GOLD)
        },
        RuleCard("사다리는 쑥, 뱀은 주르륵", "사다리 아래 칸에 멈추면 사다리를 타고 꼭대기로 올라가요.\n뱀 머리에 멈추면 뱀 몸을 따라 꼬리까지 미끄러져 내려가요. 맵은 설정에서 3종 중에 골라요.") { c, w, h, p ->
            val u = minOf(w * 0.92f / 6f, h * 0.88f / 4f)
            val l = (w - u * 6f) / 2f
            val t = (h - u * 4f) / 2f
            checker(c, l, t, 6, 4, u, p)
            // 왼쪽: 사다리 (아래 → 위) + 오르는 말
            SnakeArt.drawLadder(c, l + u * 1.2f, t + u * 3.5f, l + u * 1.8f, t + u * 0.5f, u, 0.8f, p)
            SnakeArt.drawToken(c, l + u * 1.55f, t + u * 1.75f, u * 0.32f, RED, RED_DARK, false, p)
            arrow(c, l + u * 0.45f, t + u * 3.3f, l + u * 0.75f, t + u * 0.8f, -u * 0.5f, u * 0.08f, Theme.GOLD, p)
            outlined(c, LADDER_UP, l + u * 2.65f, t + u * 0.75f, u * 0.5f, Theme.GOLD)
            // 오른쪽: 뱀 (머리 위 → 꼬리 아래) + 미끄러지는 말
            snake.build(l + u * 4.2f, t + u * 0.5f, l + u * 4.8f, t + u * 3.5f, u, false)
            snake.draw(c, SnakeArt.SNAKE_COLORS[1], SnakeArt.SNAKE_SPOTS[1], SnakeArt.SNAKE_BELLY[1], 0.7f, p)
            snake.point(0.45f, pt)
            SnakeArt.drawToken(c, pt.x, pt.y, u * 0.32f, BLUE, BLUE_DARK, false, p)
            arrow(c, l + u * 5.6f, t + u * 0.7f, l + u * 5.6f, t + u * 3.3f, u * 0.3f, u * 0.08f, 0xFFFF8A80.toInt(), p)
            outlined(c, SNAKE_DOWN, l + u * 3f, t + u * 3.8f, u * 0.42f, 0xFFFF8A80.toInt())
        },
        RuleCard("100번 칸에 먼저 도착", "100번 칸에 도착한 순서가 순위예요. 누군가 도착하면 그 바퀴까지만 하고 끝!\n아직 못 온 사람은 높이 올라간 순서. 설정에서 [정확히 100 도착]을 켜면 넘친 눈만큼 되돌아가요.") { c, w, h, p ->
            val s = minOf(h * 0.88f, w * 0.6f)
            val l = w * 0.04f
            val t = (h - s) / 2f
            painter.layout(SnakeMaps.CLASSIC, l, t, s)
            painter.drawAll(c, false, p)
            painter.center(GameConfig.SQUARES, pt)
            val cell = painter.cell
            SnakeArt.drawToken(c, pt.x, pt.y, cell * 0.4f, YELLOW, YELLOW_DARK, true, p)
            painter.center(83, pt2)
            SnakeArt.drawToken(c, pt2.x, pt2.y, cell * 0.34f, RED, RED_DARK, false, p)
            painter.center(66, pt2)
            SnakeArt.drawToken(c, pt2.x, pt2.y, cell * 0.34f, BLUE, BLUE_DARK, false, p)
            // 오른쪽: 순위 (도착 → 1위, 나머지는 높이 순)
            val rl = l + s + (w - l - s) * 0.12f
            val rw = (w - l - s) * 0.8f
            val tr = minOf(rw * 0.16f, h * 0.07f)
            val ts = minOf(h * 0.12f, rw * 0.3f)
            for (k in 0 until 3) {
                val y = h * (0.28f + 0.26f * k)
                val col = if (k == 0) YELLOW else if (k == 1) RED else BLUE
                val dk = if (k == 0) YELLOW_DARK else if (k == 1) RED_DARK else BLUE_DARK
                SnakeArt.drawToken(c, rl + tr, y, tr, col, dk, k == 0, p)
                if (k == 0) Ui.drawCrown(c, rl + tr, y - tr * 1.2f, tr * 1.6f, path, p)
                outlined(c, RANKS[k], rl + tr * 2.6f + ts * 0.9f, y + ts * 0.36f, ts, if (k == 0) Theme.GOLD else Theme.TEXT)
            }
        },
    )
}
