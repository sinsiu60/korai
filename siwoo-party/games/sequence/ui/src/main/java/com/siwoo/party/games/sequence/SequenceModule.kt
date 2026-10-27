package com.siwoo.party.games.sequence

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
import com.siwoo.party.games.sequence.ui.SequenceActivity

/** 숫자 순서 터치: 모음집 등록 정보 */
object SequenceModule : GameModule {
    override val id = "sequence"
    override val name = "숫자 순서 터치"
    override val tagline = "1부터 16까지, 누구 손가락이 제일 빠를까?"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "3분"
    override val color = 0xFF2E9BD6.toInt()
    override val activityClass = SequenceActivity::class.java

    private val path = Path()
    private val ICON_NUMS = arrayOf("3", "1", "2", "4")
    private val ICON_FILLED = booleanArrayOf(false, true, true, false)
    /** 규칙 그림용 4×4 배치 (예시) */
    private val SAMPLE = intArrayOf(7, 2, 13, 10, 1, 16, 5, 12, 9, 4, 14, 3, 15, 8, 11, 6)
    private val SAMPLE_TEXT = Array(17) { it.toString() }
    private val BARS = floatArrayOf(0.5f, 0.75f, 0.35f)
    private val PODIUM_LABELS = arrayOf("2등", "1등", "3등")
    private val PODIUM_PTS = arrayOf("+3점", "+4점", "+2점")

    /** 숫자 칸 하나 (filled = 눌린 칸) */
    private fun tile(c: Canvas, l: Float, t: Float, w: Float, h: Float, label: String, filled: Boolean, wrong: Boolean, color: Int, p: Paint) {
        val rad = minOf(w, h) * 0.2f
        val depth = minOf(w, h) * 0.08f
        p.style = Paint.Style.FILL
        if (filled) {
            p.color = color
            c.drawRoundRect(l, t + depth, l + w, t + h, rad, rad, p)
        } else {
            p.color = if (wrong) Theme.RED_DARK else 0xFFC9BFA4.toInt()
            c.drawRoundRect(l, t + depth, l + w, t + h, rad, rad, p)
            p.color = if (wrong) 0xFF2A0A18.toInt() else Theme.PAPER
            c.drawRoundRect(l, t, l + w, t + h - depth, rad, rad, p)
            if (wrong) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = minOf(w, h) * 0.07f
                p.color = Theme.RED
                c.drawRoundRect(l, t, l + w, t + h - depth, rad, rad, p)
                p.style = Paint.Style.FILL
            }
        }
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = minOf(h * 0.62f, w * 0.5f)
        p.color = if (filled) Theme.textOn(color) else if (wrong) Theme.RED else Theme.INK
        c.drawText(label, l + w / 2f, t + h / 2f + p.textSize * 0.33f, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val s = size * 0.78f
        val l = cx - s / 2f
        val t = cy - s / 2f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawRoundRect(l + s * 0.03f, t + s * 0.05f, l + s * 1.03f, t + s * 1.05f, s * 0.18f, s * 0.18f, paint)
        paint.color = 0xFF0B1340.toInt()
        canvas.drawRoundRect(l, t, l + s, t + s, s * 0.18f, s * 0.18f, paint)
        val g = s * 0.06f
        val w = (s - 3 * g) / 2f
        for (k in 0 until 4) {
            val tl = l + g + (k % 2) * (w + g)
            val tt = t + g + (k / 2) * (w + g)
            tile(canvas, tl, tt, w, w, ICON_NUMS[k], ICON_FILLED[k], false, Theme.PLAYER_COLORS[1], paint)
        }
        // 다음 숫자(3) 금색 테두리
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.045f
        paint.color = Theme.GOLD
        canvas.drawRoundRect(l + g * 0.6f, t + g * 0.6f, l + g * 1.4f + w, t + g * 1.4f + w, w * 0.22f, w * 0.22f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
    }

    /** 규칙 그림: (l, t) 에 한 변 size 인 4×4 숫자판. upTo 까지 누름, wrong 칸은 빨강 */
    private fun board(c: Canvas, l: Float, t: Float, size: Float, upTo: Int, wrong: Int, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFF070D2E.toInt()
        c.drawRoundRect(l - size * 0.03f, t - size * 0.03f, l + size * 1.03f, t + size * 1.03f, size * 0.06f, size * 0.06f, p)
        val g = size * 0.035f
        val w = (size - 3 * g) / 4f
        for (k in 0 until 16) {
            val n = SAMPLE[k]
            tile(c, l + (k % 4) * (w + g), t + (k / 4) * (w + g), w, w, SAMPLE_TEXT[n], n <= upTo, n == wrong, color, p)
        }
        p.textAlign = Paint.Align.LEFT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("모두 같은 숫자판", "구역마다 1~16을 섞은 4×4 숫자판이 나와요.\n모든 사람이 똑같은 배치예요\n(맞은편 사람 것은 돌려서 보여요).") { c, w, h, p ->
            val s = minOf(h * 0.72f, w * 0.42f)
            board(c, w * 0.27f - s / 2f, h * 0.14f, s, 0, -1, Theme.PLAYER_COLORS[0], p)
            board(c, w * 0.73f - s / 2f, h * 0.14f, s, 0, -1, Theme.PLAYER_COLORS[1], p)
        },
        RuleCard("1부터 순서대로 톡톡!", "누른 숫자는 내 색으로 채워져요.\n틀린 숫자를 누르면 0.5초 동안 멈춰요.\n(이미 누른 숫자는 다시 눌러도 괜찮아요)") { c, w, h, p ->
            val s = h * 0.8f
            board(c, w / 2f - s / 2f, h * 0.1f, s, 4, 9, Theme.PLAYER_COLORS[2], p)
        },
        RuleCard("3라운드 합계로 순위", "먼저 끝낸 순서대로 점수! (4인: 4·3·2·1점)\n한 명만 남으면 그 라운드는 끝.\n3라운드 합계로 순위 (동점은 같은 순위).") { c, w, h, p ->
            p.style = Paint.Style.FILL
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            for (k in 0 until 3) {
                val bw = w * 0.22f
                val l = w * 0.14f + k * (bw + w * 0.04f)
                val top = h * (0.95f - BARS[k] * 0.8f)
                p.color = Theme.PLAYER_COLORS[k]
                c.drawRoundRect(l, top, l + bw, h * 0.95f, h * 0.03f, h * 0.03f, p)
                p.color = 0xFFFFFFFF.toInt()
                p.textSize = h * 0.11f
                c.drawText(PODIUM_LABELS[k], l + bw / 2f, top + p.textSize * 1.2f, p)
                p.color = Theme.GOLD
                p.textSize = h * 0.09f
                c.drawText(PODIUM_PTS[k], l + bw / 2f, top - h * 0.04f, p)
                if (k == 1) Ui.drawCrown(c, l + bw / 2f, top - h * 0.16f, bw * 0.45f, path, p)
            }
            p.textAlign = Paint.Align.LEFT
        },
    )
}
