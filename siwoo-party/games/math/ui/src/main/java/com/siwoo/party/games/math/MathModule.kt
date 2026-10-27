package com.siwoo.party.games.math

import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.math.ui.MathActivity

/** 암산 대결: 모음집 등록 정보 */
object MathModule : GameModule {
    override val id = "math"
    override val name = "암산 대결"
    override val tagline = "머리보다 손이 빠르게! 정답을 먼저 눌러라"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "2분"
    override val color = 0xFF2E7D32.toInt()
    override val activityClass = MathActivity::class.java

    private const val CHALK = 0xFF1E5631.toInt()
    /** "1.5" (잠금 초) */
    private val LOCK_SEC = (GameConfig.MATH_LOCK_MS / 1000.0).toString()
    // 규칙 그림용 (그릴 때 만들지 않게 미리)
    private val LABELS = arrayOf("14", "15", "17")
    private val ALL_PLAIN = intArrayOf(0, 0, 0)
    private val RIGHT_MID = intArrayOf(0, 1, 0)
    private val WRONG_FIRST = intArrayOf(2, 0, 0)
    private val STAGE_Q = arrayOf("3+5", "47−18", "7×8")
    private val STAGE_NAME = arrayOf("1단계", "2단계", "3단계")
    private val STAGE_COLORS = intArrayOf(Theme.GREEN, Theme.GOLD, Theme.RED)
    private val STAGE_DARK = IntArray(3) { Theme.shade(STAGE_COLORS[it], 0.55f) }
    private val GRAY_DARK = Theme.shade(Theme.GRAY, 0.5f)
    private val BLUE_DARK = Theme.shade(Theme.BLUE, 0.45f)

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
            val s = (b - t) * 0.28f
            c.drawLine(cx - s, cy - s, cx + s, cy + s, p)
            c.drawLine(cx + s, cy - s, cx - s, cy + s, p)
        }
    }

    /** 구역 하나 (가운데 cx, cy, 폭 w, 높이 h, 180° 돌림 여부) — 같은 문제·같은 보기 */
    private fun zone(c: Canvas, p: Paint, cx: Float, cy: Float, w: Float, h: Float, flip: Boolean, color: Int, states: IntArray) {
        c.save()
        c.translate(cx, cy)
        if (flip) c.rotate(180f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.03f
        p.color = color
        c.drawRoundRect(-w / 2f, -h / 2f, w / 2f, h / 2f, h * 0.1f, h * 0.1f, p)
        text(c, p, "7 + 8 = ?", 0f, -h * 0.17f, h * 0.2f, 0xFFFFFFFF.toInt())
        val bw = w * 0.27f
        for (k in 0..2) {
            val l = -w * 0.44f + k * (bw + w * 0.035f)
            choice(c, p, l, h * 0.08f, l + bw, h * 0.42f, LABELS[k], states[k])
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
        // 칠판 + 문제
        paint.style = Paint.Style.FILL
        paint.color = 0xFF6D4C2E.toInt()
        canvas.drawRoundRect(cx - r * 0.66f, cy - r * 0.46f, cx + r * 0.66f, cy + r * 0.3f, r * 0.1f, r * 0.1f, paint)
        paint.color = CHALK
        canvas.drawRoundRect(cx - r * 0.58f, cy - r * 0.38f, cx + r * 0.58f, cy + r * 0.22f, r * 0.06f, r * 0.06f, paint)
        text(canvas, paint, "3×4", cx, cy - r * 0.08f, r * 0.42f, 0xFFFFFFFF.toInt())
        // 보기 세 개 (가운데 정답 초록)
        val bw = r * 0.36f
        for (k in 0..2) {
            val l = cx - r * 0.6f + k * (bw + r * 0.06f)
            paint.color = if (k == 1) Theme.GREEN else Theme.BLUE
            canvas.drawRoundRect(l, cy + r * 0.4f, l + bw, cy + r * 0.66f, r * 0.08f, r * 0.08f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.05f
        paint.color = Theme.GOLD
        canvas.drawRoundRect(cx - r * 0.6f + bw + r * 0.06f, cy + r * 0.4f, cx - r * 0.6f + 2 * bw + r * 0.06f, cy + r * 0.66f, r * 0.08f, r * 0.08f, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard(
            "모두 같은 문제를 동시에",
            "각자 구역에 같은 문제·같은 보기 ${GameConfig.CHOICES}개.\n맞은편 쪽은 돌려서 보여 줘요.\n정답을 가장 먼저 누르면 +1점!",
        ) { c, w, h, p ->
            zone(c, p, w * 0.5f, h * 0.26f, w * 0.86f, h * 0.44f, true, Theme.PLAYER_COLORS[0], ALL_PLAIN)
            zone(c, p, w * 0.5f, h * 0.74f, w * 0.86f, h * 0.44f, false, Theme.PLAYER_COLORS[1], RIGHT_MID)
        },
        RuleCard(
            "틀리면 ${LOCK_SEC}초 잠금",
            "틀린 보기를 누르면 그 사람만\n${LOCK_SEC}초 동안 누를 수 없어요.\n찍기보다 정확하게!\n${GameConfig.QUESTION_TIME_SEC}초 안에 못 맞히면 다음 문제로.",
        ) { c, w, h, p ->
            zone(c, p, w * 0.5f, h * 0.5f, w * 0.9f, h * 0.8f, false, Theme.PLAYER_COLORS[2], WRONG_FIRST)
            // 자물쇠
            val x = w * 0.84f
            val y = h * 0.24f
            val s = h * 0.1f
            p.style = Paint.Style.FILL
            p.color = Theme.RED
            c.drawRoundRect(x - s, y - s * 0.4f, x + s, y + s, s * 0.25f, s * 0.25f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.3f
            c.drawArc(x - s * 0.65f, y - s * 1.3f, x + s * 0.65f, y + s * 0.1f, 180f, 180f, false, p)
        },
        RuleCard(
            "점점 어려워져요",
            "1~${GameConfig.STAGE1_LAST}번: 한 자리 덧셈\n${GameConfig.STAGE1_LAST + 1}~${GameConfig.STAGE2_LAST}번: 두 자리 덧셈·뺄셈\n${GameConfig.STAGE2_LAST + 1}~${GameConfig.MATH_QUESTIONS}번: 곱셈 섞기\n맞힌 개수 순 순위 (같으면 같은 순위)",
        ) { c, w, h, p ->
            for (k in 0..2) {
                val l = w * (0.05f + k * 0.31f)
                val top = h * (0.62f - k * 0.2f)
                p.style = Paint.Style.FILL
                p.color = STAGE_DARK[k]
                c.drawRoundRect(l, top, l + w * 0.28f, h * 0.95f, h * 0.04f, h * 0.04f, p)
                p.color = STAGE_COLORS[k]
                c.drawRoundRect(l, top, l + w * 0.28f, top + h * 0.08f, h * 0.04f, h * 0.04f, p)
                text(c, p, STAGE_Q[k], l + w * 0.14f, top - h * 0.1f, h * 0.12f, 0xFFFFFFFF.toInt())
                text(c, p, STAGE_NAME[k], l + w * 0.14f, (top + h * 0.95f) / 2f + h * 0.04f, h * 0.09f, Theme.TEXT_SUB)
            }
        },
    )
}
