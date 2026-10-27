package com.siwoo.party.games.digitmemo

import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.digitmemo.ui.DigitMemoActivity

/** 숫자 순간 기억: 모음집 등록 정보 */
object DigitMemoModule : GameModule {
    override val id = "digitmemo"
    override val name = "숫자 순간 기억"
    override val tagline = "딱 1초! 본 숫자를 그대로 빠르게 입력하라"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "3분"
    override val color = 0xFF6A3FC8.toInt()
    override val activityClass = DigitMemoActivity::class.java

    private val NAVY = 0xFF0E1640.toInt()
    private val KEY = Theme.shade(Theme.BLUE, 1.05f)
    private val KEY_DARK = Theme.shade(Theme.BLUE, 0.45f)
    private val WHITE = 0xFFFFFFFF.toInt()
    // 규칙 그림용 글자 (그릴 때 만들지 않게 미리)
    private val SHOW = arrayOf("5", "2", "8", "1")
    private val TYPED = arrayOf("5", "2", "8")
    private val KEYS = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val RESULTS = arrayOf("+2 가장 빨리!", "+1 정답!", "틀렸어요")
    private val RESULT_COLORS = intArrayOf(Theme.GOLD, Theme.GREEN, Theme.RED)
    private val TIMES = arrayOf("1.8초", "2.6초", "1.2초")
    private const val SEC = "1초"
    private const val OK = "확인"
    private val LIMIT_FIRST = (DigitMemoGame.timeLimitMs(GameConfig.START_DIGITS) / 1000).toString()
    private val MAX_DIGITS = DigitMemoGame.digitsFor(GameConfig.ROUNDS)

    private fun text(c: Canvas, p: Paint, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        p.style = Paint.Style.FILL
        p.color = color
        p.textAlign = align
        p.textSize = size
        p.isFakeBoldText = true
        c.drawText(s, x, y + size * 0.36f, p)
        p.isFakeBoldText = false
        p.textAlign = Paint.Align.LEFT
    }

    /** 숫자 판 (가운데 cx, cy, 폭 w, 높이 h): 어두운 판 + 금색 테두리 + 큰 숫자 */
    private fun digitBoard(c: Canvas, p: Paint, cx: Float, cy: Float, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        p.color = NAVY
        c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, h * 0.2f, h * 0.2f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.06f
        p.color = Theme.GOLD
        c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, h * 0.2f, h * 0.2f, p)
        val cell = w * 0.9f / SHOW.size
        for (k in SHOW.indices) text(c, p, SHOW[k], cx - w * 0.45f + cell * (k + 0.5f), cy, h * 0.68f, WHITE)
    }

    /** 구역 하나 (가운데 cx, cy, 폭 w, 높이 h, 뒤집힘, 색) — 숫자 판 */
    private fun showZone(c: Canvas, p: Paint, cx: Float, cy: Float, w: Float, h: Float, flip: Boolean, color: Int) {
        c.save()
        c.translate(cx, cy)
        if (flip) c.rotate(180f)
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = 50
        c.drawRoundRect(-w / 2f, -h / 2f, w / 2f, h / 2f, h * 0.12f, h * 0.12f, p)
        p.alpha = 255
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.035f
        p.color = color
        c.drawRoundRect(-w / 2f, -h / 2f, w / 2f, h / 2f, h * 0.12f, h * 0.12f, p)
        digitBoard(c, p, 0f, 0f, w * 0.8f, h * 0.6f)
        c.restore()
    }

    /** 작은 키 (입체) */
    private fun key(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, face: Int, base: Int) {
        val rad = (b - t) * 0.22f
        val d = (b - t) * 0.1f
        p.style = Paint.Style.FILL
        p.color = base
        c.drawRoundRect(l, t + d, r, b, rad, rad, p)
        p.color = face
        c.drawRoundRect(l, t, r, b - d, rad, rad, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = color
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 숫자 판
        digitBoard(canvas, paint, cx, cy - r * 0.22f, r * 1.3f, r * 0.62f)
        // 작은 패드 두 줄
        val kw = r * 0.2f
        val g = r * 0.06f
        for (row in 0..1) {
            for (col in 0..4) {
                val l = cx - r * 0.65f + col * (kw + g)
                val t = cy + r * 0.2f + row * (kw + g)
                val isOk = row == 1 && col == 4
                key(canvas, paint, l, t, l + kw, t + kw, if (isOk) Theme.GREEN else KEY, if (isOk) Theme.GREEN_DARK else KEY_DARK)
            }
        }
        // 번쩍 (1초) 표시: 오른쪽 위 작은 번개
        paint.style = Paint.Style.FILL
        paint.color = Theme.GOLD
        canvas.drawCircle(cx + r * 0.62f, cy - r * 0.6f, r * 0.16f, paint)
        paint.color = NAVY
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.05f
        canvas.drawLine(cx + r * 0.62f, cy - r * 0.6f, cx + r * 0.62f, cy - r * 0.7f, paint)
        canvas.drawLine(cx + r * 0.62f, cy - r * 0.6f, cx + r * 0.69f, cy - r * 0.56f, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard(
            "숫자가 딱 1초!",
            "숫자열이 1초 동안만 보였다 사라져요.\n각자 구역에 자기 쪽으로 똑같이 보여요.\n${GameConfig.START_DIGITS}자리에서 시작해 라운드마다 1자리씩\n늘어나요 (마지막 ${MAX_DIGITS}자리).",
        ) { c, w, h, p ->
            showZone(c, p, w * 0.44f, h * 0.25f, w * 0.76f, h * 0.42f, true, Theme.PLAYER_COLORS[0])
            showZone(c, p, w * 0.44f, h * 0.75f, w * 0.76f, h * 0.42f, false, Theme.PLAYER_COLORS[1])
            // 1초 시계
            val x = w * 0.91f
            val y = h * 0.5f
            val s = h * 0.075f
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            c.drawCircle(x, y, s, p)
            text(c, p, SEC, x, y, s * 0.8f, NAVY)
        },
        RuleCard(
            "내 패드로 입력 → [확인]",
            "숫자가 사라지면 구역마다 작은 숫자 패드!\n본 숫자를 그대로 누르고 [확인].\n지우기 키로 고칠 수 있지만 제출은 한 번뿐!\n제한 시간 ${LIMIT_FIRST}초 (자리가 늘면 1초씩 더)",
        ) { c, w, h, p ->
            // 숫자 칸 줄
            val sw = w * 0.1f
            for (k in 0 until 4) {
                val l = w * 0.27f + k * (sw + w * 0.02f)
                p.style = Paint.Style.FILL
                p.color = if (k < TYPED.size) 0xFF2B3C8F.toInt() else 0x66000000
                c.drawRoundRect(l, h * 0.06f, l + sw, h * 0.3f, sw * 0.18f, sw * 0.18f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = h * 0.012f
                p.color = if (k == TYPED.size) Theme.GOLD else 0x40FFFFFF
                c.drawRoundRect(l, h * 0.06f, l + sw, h * 0.3f, sw * 0.18f, sw * 0.18f, p)
                if (k < TYPED.size) text(c, p, TYPED[k], l + sw / 2f, h * 0.18f, h * 0.15f, WHITE)
            }
            // 패드 두 줄
            val g = w * 0.02f
            val kw = (w * 0.86f - 5 * g) / 6.5f
            val kh = h * 0.24f
            for (row in 0..1) {
                val t = h * 0.38f + row * (kh + g)
                for (col in 0..4) {
                    val l = w * 0.07f + col * (kw + g)
                    key(c, p, l, t, l + kw, t + kh, KEY, KEY_DARK)
                    text(c, p, KEYS[row * 5 + col], l + kw / 2f, t + kh * 0.45f, kh * 0.5f, WHITE)
                }
                val l = w * 0.07f + 5 * (kw + g)
                val isOk = row == 1
                key(c, p, l, t, w * 0.93f, t + kh, if (isOk) Theme.GREEN else Theme.shade(Theme.RED, 0.9f), if (isOk) Theme.GREEN_DARK else Theme.RED_DARK)
                if (isOk) {
                    text(c, p, OK, (l + w * 0.93f) / 2f, t + kh * 0.45f, kh * 0.4f, WHITE)
                } else {
                    // 지우기 모양
                    val cx = (l + w * 0.93f) / 2f
                    val cy = t + kh * 0.45f
                    val s = kh * 0.2f
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = h * 0.012f
                    p.color = WHITE
                    c.drawLine(cx - s * 1.4f, cy, cx - s * 0.6f, cy - s, p)
                    c.drawLine(cx - s * 1.4f, cy, cx - s * 0.6f, cy + s, p)
                    c.drawLine(cx - s * 0.6f, cy - s, cx + s * 1.4f, cy - s, p)
                    c.drawLine(cx - s * 0.6f, cy + s, cx + s * 1.4f, cy + s, p)
                    c.drawLine(cx + s * 1.4f, cy - s, cx + s * 1.4f, cy + s, p)
                    c.drawLine(cx - s * 0.05f, cy - s * 0.45f, cx + s * 0.85f, cy + s * 0.45f, p)
                    c.drawLine(cx + s * 0.85f, cy - s * 0.45f, cx - s * 0.05f, cy + s * 0.45f, p)
                }
            }
            // 1 을 누르는 손가락
            p.style = Paint.Style.FILL
            p.color = 0x99FFFFFF.toInt()
            c.drawCircle(w * 0.07f + kw * 0.7f, h * 0.38f + kh * 0.7f, kh * 0.32f, p)
        },
        RuleCard(
            "가장 빠른 정답 +2",
            "정답 중 가장 빨리 [확인]한 사람 +${GameConfig.POINTS_FASTEST}점,\n다른 정답자는 +${GameConfig.POINTS_CORRECT}점, 틀리면 0점.\n${GameConfig.ROUNDS}라운드 점수 합계로 순위\n(같은 점수면 같은 순위)",
        ) { c, w, h, p ->
            for (k in 0..2) {
                val t = h * (0.08f + k * 0.3f)
                val b = t + h * 0.24f
                val col = RESULT_COLORS[k]
                p.style = Paint.Style.FILL
                p.color = Theme.shade(col, 0.45f)
                c.drawRoundRect(w * 0.06f, t, w * 0.94f, b, h * 0.06f, h * 0.06f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = h * (if (k == 0) 0.018f else 0.01f)
                p.color = col
                c.drawRoundRect(w * 0.06f, t, w * 0.94f, b, h * 0.06f, h * 0.06f, p)
                // 플레이어 색 점 + 걸린 시간 + 결과
                p.style = Paint.Style.FILL
                p.color = Theme.PLAYER_COLORS[k + 1]
                c.drawCircle(w * 0.13f, (t + b) / 2f, h * 0.06f, p)
                text(c, p, TIMES[k], w * 0.2f, (t + b) / 2f, h * 0.09f, Theme.TEXT_SUB, Paint.Align.LEFT)
                text(c, p, RESULTS[k], w * 0.66f, (t + b) / 2f, h * 0.11f, WHITE)
            }
        },
    )
}
