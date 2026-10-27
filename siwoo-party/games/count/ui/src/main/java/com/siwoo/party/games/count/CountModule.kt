package com.siwoo.party.games.count

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.count.ui.CountActivity
import com.siwoo.party.games.count.ui.CountView

/** 몇 개였지?: 모음집 등록 정보 */
object CountModule : GameModule {
    override val id = "count"
    override val name = "몇 개였지?"
    override val tagline = "번쩍! 사라진 점과 별, 몇 개였지?"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "3분"
    override val color = 0xFFE0567A.toInt()
    override val activityClass = CountActivity::class.java

    // ── 글자 (그릴 때 만들지 않게 미리) ──
    private val LOCK_SEC = secText(GameConfig.LOCK_MS)
    private val SHOW_FIRST = secText(GameConfig.SHOW_FIRST_MS)
    private val SHOW_LAST = secText(GameConfig.SHOW_LAST_MS)
    private val LABELS = arrayOf("8", "6", "7", "9")
    private const val ANSWER = "7"
    private val ALL_PLAIN = intArrayOf(0, 0, 0, 0)
    private val RIGHT_THIRD = intArrayOf(0, 0, 1, 0)
    private val WRONG_FIRST = intArrayOf(2, 0, 0, 0)
    private val PAPER_DARK = 0xFFE3D3A8.toInt()
    private val BLUE_DARK = Theme.shade(Theme.BLUE, 0.45f)
    private val GRAY_DARK = Theme.shade(Theme.GRAY, 0.5f)

    /** 규칙 그림의 점 7개 (칸 안 비율 위치, 모양, 색) */
    private val DOT_X = floatArrayOf(0.18f, 0.4f, 0.66f, 0.84f, 0.28f, 0.55f, 0.78f)
    private val DOT_Y = floatArrayOf(0.3f, 0.22f, 0.3f, 0.62f, 0.7f, 0.62f, 0.25f)
    private val DOT_SHAPE = intArrayOf(0, 1, 0, 2, 1, 0, 3)
    private val DOT_COLOR = intArrayOf(0, 1, 2, 3, 4, 5, 1)

    private val star = Path().apply {
        for (k in 0 until 10) {
            val a = -Math.PI / 2 + k * Math.PI / 5
            val rad = if (k % 2 == 0) 1.18f else 0.5f
            val x = (rad * Math.cos(a)).toFloat()
            val y = (rad * Math.sin(a)).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    private val tri = Path().apply {
        for (k in 0 until 3) {
            val a = -Math.PI / 2 + k * Math.PI * 2 / 3
            val x = (1.2f * Math.cos(a)).toFloat()
            val y = (1.2f * Math.sin(a) + 0.15f).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun secText(ms: Long): String = if (ms % 1000L == 0L) (ms / 1000L).toString() else (ms / 1000.0).toString()

    private fun text(c: Canvas, p: Paint, s: String, x: Float, y: Float, size: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.isFakeBoldText = true
        c.drawText(s, x, y + size * 0.36f, p)
        p.isFakeBoldText = false
        p.textAlign = Paint.Align.LEFT
    }

    /** 모양 하나 (0 동그라미, 1 별, 2 네모, 3 세모) */
    private fun shape(c: Canvas, p: Paint, kind: Int, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        when (kind) {
            0 -> c.drawCircle(x, y, r, p)
            2 -> c.drawRoundRect(x - r * 0.86f, y - r * 0.86f, x + r * 0.86f, y + r * 0.86f, r * 0.25f, r * 0.25f, p)
            else -> {
                c.save()
                c.translate(x, y)
                c.scale(r, r)
                c.drawPath(if (kind == 1) star else tri, p)
                c.restore()
            }
        }
    }

    /** 그림 칸 (금색 액자 + 종이 + 점 7개). dots = false 면 감은 눈 */
    private fun panel(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, dots: Boolean) {
        val h = b - t
        val rad = h * 0.1f
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD
        c.drawRoundRect(l, t, r, b, rad, rad, p)
        val f = h * 0.06f
        p.color = if (dots) Theme.PAPER else 0xFF0B1238.toInt()
        c.drawRoundRect(l + f, t + f, r - f, b - f, rad * 0.7f, rad * 0.7f, p)
        if (dots) {
            val w = r - l - 2 * f
            val ih = h - 2 * f
            val dr = minOf(w, ih) * 0.08f
            for (k in DOT_X.indices) shape(c, p, DOT_SHAPE[k], l + f + DOT_X[k] * w, t + f + DOT_Y[k] * ih, dr, CountView.PALETTE[DOT_COLOR[k]])
        } else {
            val cx = (l + r) / 2f
            val cy = (t + b) / 2f
            val s = h * 0.2f
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.14f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.TEXT_SUB
            c.drawArc(cx - s, cy - s * 0.5f, cx + s, cy + s * 0.5f, 20f, 140f, false, p)
            c.drawArc(cx - s, cy - s * 0.5f, cx + s, cy + s * 0.5f, 200f, 140f, false, p)
            p.strokeCap = Paint.Cap.BUTT
        }
    }

    /** 입체 보기 버튼 (state 0 보통, 1 정답 초록, 2 틀림 회색+X) */
    private fun choice(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, label: String, state: Int) {
        val rad = (b - t) * 0.28f
        val d = (b - t) * 0.1f
        p.style = Paint.Style.FILL
        p.color = when (state) { 1 -> Theme.GREEN_DARK; 2 -> GRAY_DARK; else -> BLUE_DARK }
        c.drawRoundRect(l, t + d, r, b, rad, rad, p)
        p.color = when (state) { 1 -> Theme.GREEN; 2 -> Theme.GRAY; else -> Theme.BLUE }
        c.drawRoundRect(l, t, r, b - d, rad, rad, p)
        text(c, p, label, (l + r) / 2f, (t + b - d) / 2f, (b - t) * 0.48f, 0xFFFFFFFF.toInt())
        if (state == 2) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = (b - t) * 0.09f
            p.color = Theme.RED
            val cx = (l + r) / 2f
            val cy = (t + b - d) / 2f
            val s = (b - t) * 0.26f
            c.drawLine(cx - s, cy - s, cx + s, cy + s, p)
            c.drawLine(cx + s, cy - s, cx - s, cy + s, p)
        }
    }

    /** 구역 하나의 보기 줄 (가운데 cx, cy, 폭 w, 높이 h, 180° 돌림 여부) */
    private fun choiceRow(c: Canvas, p: Paint, cx: Float, cy: Float, w: Float, h: Float, flip: Boolean, color: Int, states: IntArray) {
        c.save()
        c.translate(cx, cy)
        if (flip) c.rotate(180f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.05f
        p.color = color
        c.drawRoundRect(-w / 2f, -h / 2f, w / 2f, h / 2f, h * 0.2f, h * 0.2f, p)
        val gap = w * 0.03f
        val bw = (w * 0.92f - gap * 3) / 4f
        for (k in 0..3) {
            val l = -w * 0.46f + k * (bw + gap)
            choice(c, p, l, -h * 0.34f, l + bw, h * 0.36f, LABELS[k], states[k])
        }
        c.restore()
    }

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
        // 번쩍이는 그림 칸
        panel(canvas, paint, cx - r * 0.64f, cy - r * 0.5f, cx + r * 0.64f, cy + r * 0.38f, true)
        // 물음표 배지
        val bx = cx + r * 0.42f
        val by = cy + r * 0.44f
        val br = r * 0.27f
        paint.style = Paint.Style.FILL
        paint.color = Theme.GOLD_DARK
        canvas.drawCircle(bx, by, br, paint)
        paint.color = Theme.GOLD
        canvas.drawCircle(bx, by, br * 0.84f, paint)
        text(canvas, paint, "?", bx, by, br * 1.2f, Theme.INK)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard(
            "가운데를 잘 보세요!",
            "가운데 그림 칸에 점·별이 잠깐\n보였다 사라져요. 몇 개였는지 세요!\n개수는 ${GameConfig.COUNT_MIN}개 → ${GameConfig.COUNT_MAX}개로 늘고\n보이는 시간은 ${SHOW_FIRST}초 → ${SHOW_LAST}초로 짧아져요.",
        ) { c, w, h, p ->
            panel(c, p, w * 0.06f, h * 0.16f, w * 0.56f, h * 0.84f, true)
            // 화살표 → 감은 눈 칸
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.035f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.GOLD
            c.drawLine(w * 0.6f, h * 0.5f, w * 0.68f, h * 0.5f, p)
            c.drawLine(w * 0.66f, h * 0.44f, w * 0.69f, h * 0.5f, p)
            c.drawLine(w * 0.66f, h * 0.56f, w * 0.69f, h * 0.5f, p)
            p.strokeCap = Paint.Cap.BUTT
            panel(c, p, w * 0.72f, h * 0.3f, w * 0.96f, h * 0.7f, false)
        },
        RuleCard(
            "먼저 맞히면 +1",
            "각자 구역에 같은 보기 ${GameConfig.CHOICES}개.\n맞은편 쪽은 돌려서 보여 줘요.\n정답을 가장 먼저 누르면 +1점!\n${GameConfig.ANSWER_TIME_SEC}초 안에 아무도 못 맞히면 다음 라운드.",
        ) { c, w, h, p ->
            choiceRow(c, p, w * 0.5f, h * 0.15f, w * 0.9f, h * 0.24f, true, Theme.PLAYER_COLORS[0], ALL_PLAIN)
            panel(c, p, w * 0.3f, h * 0.32f, w * 0.7f, h * 0.68f, true)
            // 정답 배지
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            c.drawCircle(w * 0.7f, h * 0.66f, h * 0.08f, p)
            text(c, p, ANSWER, w * 0.7f, h * 0.66f, h * 0.1f, Theme.INK)
            choiceRow(c, p, w * 0.5f, h * 0.85f, w * 0.9f, h * 0.24f, false, Theme.PLAYER_COLORS[1], RIGHT_THIRD)
        },
        RuleCard(
            "틀리면 ${LOCK_SEC}초 잠금",
            "틀린 보기를 누르면 그 사람만\n${LOCK_SEC}초 동안 누를 수 없어요.\n${GameConfig.ROUNDS}라운드 뒤 맞힌 개수 순 순위\n(같으면 같은 순위)",
        ) { c, w, h, p ->
            choiceRow(c, p, w * 0.5f, h * 0.62f, w * 0.92f, h * 0.36f, false, Theme.PLAYER_COLORS[2], WRONG_FIRST)
            // 자물쇠
            val x = w * 0.5f
            val y = h * 0.22f
            val s = h * 0.1f
            p.style = Paint.Style.FILL
            p.color = Theme.RED
            c.drawRoundRect(x - s, y - s * 0.4f, x + s, y + s, s * 0.25f, s * 0.25f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.3f
            c.drawArc(x - s * 0.65f, y - s * 1.3f, x + s * 0.65f, y + s * 0.1f, 180f, 180f, false, p)
            p.style = Paint.Style.FILL
            p.color = PAPER_DARK
            c.drawCircle(x, y + s * 0.3f, s * 0.2f, p)
        },
    )
}
