package com.siwoo.party.games.simon

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.simon.ui.SimonActivity
import com.siwoo.party.games.simon.ui.SimonArt
import kotlin.math.cos
import kotlin.math.sin

/** 패턴 기억 릴레이: 모음집 등록 정보 */
object SimonModule : GameModule {
    override val id = "simon"
    override val name = "패턴 기억 릴레이"
    override val tagline = "지금까지의 색 순서를 그대로 누르고 하나 더! 까먹으면 탈락"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "5분"
    override val color = 0xFF2E9E6A.toInt()
    override val activityClass = SimonActivity::class.java

    private val path = Path()
    private val rect = RectF()
    private val iconLevels = floatArrayOf(1f, 0f, 0f, 0f)
    private val ruleLevels1 = floatArrayOf(0f, 0f, 0f, 1f)
    private val ruleLevels2 = floatArrayOf(0f, 0f, 1f, 0f)
    private val off = floatArrayOf(0f, 0f, 0f, 0f)
    private val numbers = arrayOf("1", "2", "3", "4")

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.42f
        SimonArt.drawBoard(canvas, cx, cy, r, iconLevels, paint, rect)
        SimonArt.drawFace(canvas, cx, cy, r, "+1", paint)
        paint.textAlign = Paint.Align.LEFT
    }

    /** 버튼 i 가운데 x·y (규칙 그림용) */
    private fun padX(cx: Float, r: Float, i: Int): Float =
        cx + (r * 0.68f * cos(Math.toRadians((SimonArt.startAngle(i) + 45f).toDouble()))).toFloat()

    private fun padY(cy: Float, r: Float, i: Int): Float =
        cy + (r * 0.68f * sin(Math.toRadians((SimonArt.startAngle(i) + 45f).toDouble()))).toFloat()

    /** 번호 동그라미 (순서) */
    private fun badge(c: Canvas, x: Float, y: Float, s: Float, text: String, fill: Int, ink: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0x88000000.toInt()
        c.drawCircle(x, y + s * 0.08f, s, p)
        p.color = fill
        c.drawCircle(x, y, s, p)
        p.color = ink
        p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = true
        p.textSize = s * 1.2f
        c.drawText(text, x, y + p.textSize * 0.36f, p)
        p.isFakeBoldText = false
    }

    private fun arrow(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w
        p.color = color
        c.drawLine(x0, y0, x1, y1, p)
        val a = Math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())
        val hl = w * 3.2f
        path.reset()
        path.moveTo(x1 + (cos(a) * w).toFloat(), y1 + (sin(a) * w).toFloat())
        path.lineTo(x1 - (cos(a - 0.5) * hl).toFloat(), y1 - (sin(a - 0.5) * hl).toFloat())
        path.lineTo(x1 - (cos(a + 0.5) * hl).toFloat(), y1 - (sin(a + 0.5) * hl).toFloat())
        path.close()
        p.style = Paint.Style.FILL
        c.drawPath(path, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard(
            "순서를 그대로 + 새 색 하나",
            "지금까지의 순서를 처음부터 그대로 눌러요.\n다 맞히면 새 색 하나를 더해요.\n그다음은 다음 사람 차례!\n첫 사람은 색 하나로 시작해요.",
        ) { c, w, h, p ->
            val r = h * 0.44f
            val cx = w * 0.4f
            val cy = h * 0.5f
            SimonArt.drawBoard(c, cx, cy, r, ruleLevels1, p, rect)
            SimonArt.drawFace(c, cx, cy, r, "4", p)
            // 지금까지의 순서 1 → 2 → 3 (초록·빨강·파랑), 노랑이 새로 더한 색
            val s = r * 0.13f
            for (k in 0 until 3) badge(c, padX(cx, r, k), padY(cy, r, k), s, numbers[k], Theme.PAPER, Theme.INK, p)
            badge(c, padX(cx, r, 3), padY(cy, r, 3), s * 1.25f, "+1", Theme.GOLD, Theme.INK, p)
            // 오른쪽: "+1" 말풍선 화살표
            val bx = w * 0.82f
            val by = h * 0.3f
            arrow(c, bx - r * 0.1f, by + r * 0.3f, padX(cx, r, 3) + s * 2.2f, padY(cy, r, 3) - s * 0.6f, h * 0.018f, Theme.GOLD, p)
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            c.drawRoundRect(bx - h * 0.17f, by - h * 0.09f, bx + h * 0.17f, by + h * 0.09f, h * 0.05f, h * 0.05f, p)
            p.color = Theme.INK
            p.textAlign = Paint.Align.CENTER
            p.isFakeBoldText = true
            p.textSize = h * 0.09f
            c.drawText("새 색!", bx, by + p.textSize * 0.36f, p)
            p.isFakeBoldText = false
        },
        RuleCard(
            "틀리거나 5초 멈추면 탈락",
            "순서는 다시 보여 주지 않아요!\n틀린 색을 누르거나 5초 동안 안 누르면 탈락.\n순서는 그대로, 다음 사람이 이어서 해요.",
        ) { c, w, h, p ->
            val r = h * 0.42f
            val cx = w * 0.33f
            val cy = h * 0.5f
            SimonArt.drawBoard(c, cx, cy, r, ruleLevels2, p, rect)
            SimonArt.drawFace(c, cx, cy, r, "7", p)
            // 잘못 누른 노랑 ✕
            val x = padX(cx, r, 3)
            val y = padY(cy, r, 3)
            val d = r * 0.13f
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.TEXT
            p.strokeWidth = r * 0.06f
            c.drawLine(x - d, y - d, x + d, y + d, p)
            c.drawLine(x + d, y - d, x - d, y + d, p)
            // 오른쪽: 초시계 "5초" + 빨간 막대
            val wx = w * 0.77f
            val wy = h * 0.4f
            val wr = h * 0.2f
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD_DARK
            c.drawRoundRect(wx - wr * 0.22f, wy - wr * 1.3f, wx + wr * 0.22f, wy - wr * 0.92f, wr * 0.08f, wr * 0.08f, p)
            p.color = Theme.PAPER
            c.drawCircle(wx, wy, wr, p)
            p.color = Theme.RED
            rect.set(wx - wr * 0.8f, wy - wr * 0.8f, wx + wr * 0.8f, wy + wr * 0.8f)
            c.drawArc(rect, -90f, -60f, true, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = wr * 0.12f
            p.color = Theme.RED_DARK
            c.drawCircle(wx, wy, wr, p)
            p.style = Paint.Style.FILL
            p.color = Theme.INK
            p.textAlign = Paint.Align.CENTER
            p.isFakeBoldText = true
            p.textSize = wr * 0.7f
            c.drawText("5초", wx, wy + wr * 0.62f, p)
            p.isFakeBoldText = false
            // 시간 막대 (거의 다 줄어든 빨강)
            val bw = h * 0.5f
            val bt = h * 0.74f
            p.color = Theme.BG_DEEP
            c.drawRoundRect(wx - bw / 2f, bt, wx + bw / 2f, bt + h * 0.07f, h * 0.035f, h * 0.035f, p)
            p.color = Theme.RED
            c.drawRoundRect(wx - bw / 2f + h * 0.01f, bt + h * 0.01f, wx - bw / 2f + bw * 0.22f, bt + h * 0.06f, h * 0.025f, h * 0.025f, p)
        },
        RuleCard(
            "최후의 1인이 1위",
            "한 명만 남을 때까지 계속!\n가운데 큰 숫자가 지금 순서 길이예요.\n늦게 탈락할수록 높은 순위.",
        ) { c, w, h, p ->
            val colW = w * 0.17f
            val base = h * 0.9f
            for (k in 0 until 4) {
                val x = w * (if (k == 0) 0.5f else if (k == 1) 0.29f else if (k == 2) 0.71f else 0.88f)
                val ch = h * (if (k == 0) 0.42f else if (k == 1) 0.3f else if (k == 2) 0.2f else 0.12f)
                val cw = if (k == 3) colW * 0.7f else colW
                p.style = Paint.Style.FILL
                p.color = if (k == 0) Theme.GOLD_DARK else Theme.CARD_LIGHT
                c.drawRoundRect(x - cw / 2f, base - ch, x + cw / 2f, base, h * 0.02f, h * 0.02f, p)
                val r = h * (if (k == 0) 0.09f else 0.07f)
                val cy = base - ch - r * 1.15f
                p.color = SimonArt.PAD[k]
                c.drawCircle(x, cy, r, p)
                p.color = 0xFFFFFFFF.toInt()
                p.textAlign = Paint.Align.CENTER
                p.textSize = h * 0.09f
                p.isFakeBoldText = true
                c.drawText(numbers[k], x, base - h * 0.03f, p)
                p.isFakeBoldText = false
                if (k == 0) Ui.drawCrown(c, x, cy - r * 1.05f, r * 1.3f, path, p)
                else {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = r * 0.25f
                    p.strokeCap = Paint.Cap.ROUND
                    p.color = Theme.RED
                    val d = r * 0.5f
                    c.drawLine(x - d, cy - d, x + d, cy + d, p)
                    c.drawLine(x + d, cy - d, x - d, cy + d, p)
                }
            }
            // 왼쪽 위 작은 판 (길이 12)
            val sr = h * 0.17f
            SimonArt.drawBoard(c, w * 0.12f, h * 0.22f, sr, off, p, rect)
            SimonArt.drawFace(c, w * 0.12f, h * 0.22f, sr, "12", p)
        },
    )
}
