package com.siwoo.party.games.alkkagi

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
import com.siwoo.party.games.alkkagi.ui.AlkkagiActivity
import com.siwoo.party.games.alkkagi.ui.StoneArt

/** 알까기: 모음집 등록 정보 (2인 전용, 차례대로) */
object AlkkagiModule : GameModule {
    override val id = "alkkagi"
    override val name = "알까기"
    override val tagline = "내 알을 튕겨 상대 알을 판 밖으로!"
    override val playerCounts = GameConfig.PLAYERS..GameConfig.PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "5분"
    override val color = 0xFF827717.toInt()
    override val activityClass = AlkkagiActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val crown = Path()
    private val arrow = Path()

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
        // 나무판 (네모, 큰 격자) — 작게 그려도 알아보게 칸 수를 줄인다
        val half = r * 0.62f
        val step = half * 2f / 3f
        StoneArt.wood(canvas, paint, cx - half, cy - half * 0.78f, cx + half, cy + half * 0.78f, step)
        // 빨간 알이 파란 알을 쳐서 오른쪽 위 판 밖으로
        val sr = r * 0.19f
        val rx = cx - half * 0.2f
        val ry = cy + half * 0.38f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = sr * 0.24f
        paint.color = 0xDDFFFFFF.toInt()
        canvas.drawLine(rx - sr * 2.1f, ry + sr * 0.95f, rx - sr * 1.35f, ry + sr * 0.6f, paint)
        canvas.drawLine(rx - sr * 1.75f, ry + sr * 1.6f, rx - sr * 1.1f, ry + sr * 1.15f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        StoneArt.stone(canvas, paint, rx, ry, sr, RED)
        StoneArt.stone(canvas, paint, cx + half * 0.32f, cy - half * 0.08f, sr, BLUE)
        StoneArt.stone(canvas, paint, cx + half * 0.92f, cy - half * 0.7f, sr * 0.7f, BLUE, 190)
    }

    /** 규칙 그림 공통: 카드 안에 나무판 (오른쪽 가장자리가 보이게 할 수 있음) */
    private fun board(c: Canvas, w: Float, h: Float, p: Paint, right: Float): Float {
        val step = h / 6.2f
        StoneArt.wood(c, p, -step * 0.5f, h * 0.08f, right, h * 0.86f, step)
        return step
    }

    /** 외곽선 있는 굵은 글자 */
    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF2A1A08.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("내 알을 뒤로 끌었다 놓기", "내 차례에 내 알을 누르고\n뒤로 끌었다가 놓으면\n새총처럼 반대쪽으로 튕겨 나가요.\n많이 끌수록 세게! (짧으면 취소)") { c, w, h, p ->
            val s = board(c, w, h, p, w * 1.1f)
            val r = s * 0.46f
            val x = w * 0.38f
            val y = h * 0.52f
            // 고무줄 + 손가락
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = r * 0.22f
            p.color = 0xCCFFFFFF.toInt()
            val fx = x - s * 1.7f
            val fy = y + s * 0.75f
            c.drawLine(x, y, fx, fy, p)
            // 날아갈 방향 점선 + 화살촉
            p.color = 0xFFFFB000.toInt()
            p.strokeWidth = r * 0.26f
            val ex = x + s * 3.1f
            val ey = y - s * 1.37f
            StoneArt.dotted(c, p, x + s * 0.9f, y - s * 0.4f, ex, ey, s * 0.2f)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
            arrow.reset()
            arrow.moveTo(ex + s * 0.42f, ey - s * 0.19f)
            arrow.lineTo(ex - s * 0.07f, ey - s * 0.38f)
            arrow.lineTo(ex + s * 0.1f, ey + s * 0.15f)
            arrow.close()
            c.drawPath(arrow, p)
            StoneArt.stone(c, p, x, y, r, RED)
            // 힘 게이지 고리
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.24f
            p.color = 0x66000000
            c.drawCircle(x, y, r * 1.55f, p)
            p.color = 0xFFFFB000.toInt()
            c.drawArc(x - r * 1.55f, y - r * 1.55f, x + r * 1.55f, y + r * 1.55f, -90f, 230f, false, p)
            // 손가락
            p.style = Paint.Style.FILL
            p.color = 0xFFFFE0C8.toInt()
            c.drawCircle(fx, fy, r * 0.62f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.12f
            p.color = 0xFF8A5A26.toInt()
            c.drawCircle(fx, fy, r * 0.62f, p)
            StoneArt.stone(c, p, w * 0.86f, h * 0.24f, r, BLUE)
            StoneArt.stone(c, p, w * 0.86f, h * 0.68f, r, BLUE)
        },
        RuleCard("상대 알을 판 밖으로!", "알의 가운데가 판 밖으로 나가면\n떨어져서 사라져요 → 퇴장!\n모든 알이 멈추면 차례가 넘어가요.\n(내 알만 떨어져도 넘어가요)") { c, w, h, p ->
            val edge = w * 0.66f
            val s = board(c, w, h, p, edge)
            val r = s * 0.46f
            val y = h * 0.47f
            // 친 알(멈춤) → 맞은 알이 판 밖으로 떨어지며 작아짐
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = r * 0.2f
            p.color = 0xAAFFFFFF.toInt()
            for (k in 0..2) c.drawLine(edge - s * 3.4f - k * s * 0.5f, y + (k - 1) * r * 0.5f, edge - s * 2.6f - k * s * 0.5f, y + (k - 1) * r * 0.5f, p)
            p.strokeCap = Paint.Cap.BUTT
            StoneArt.stone(c, p, edge - s * 2.1f, y, r, RED)
            StoneArt.stone(c, p, edge - s * 0.25f, y, r, BLUE, 255)
            StoneArt.stone(c, p, edge + s * 0.85f, y + s * 0.1f, r * 0.7f, BLUE, 170)
            StoneArt.stone(c, p, edge + s * 1.75f, y + s * 0.25f, r * 0.38f, BLUE, 90)
            shout(c, p, "퇴장!", w * 0.8f, h * 0.27f, h * 0.17f, Theme.GOLD)
        },
        RuleCard("마지막 알을 지켜라", "한쪽 알 ${GameConfig.ALKKAGI_STONES}개가 모두 떨어지면 끝!\n알이 남은 사람 1위, 진 사람 2위.\n한 번에 두 사람의 마지막 알이\n함께 떨어지면 튕긴 사람이 져요.") { c, w, h, p ->
            val s = board(c, w, h, p, w * 1.1f)
            val r = s * 0.46f
            StoneArt.stone(c, p, w * 0.3f, h * 0.56f, r * 1.25f, RED)
            p.style = Paint.Style.FILL
            Ui.drawCrown(c, w * 0.3f, h * 0.56f - r * 1.6f, r * 2.2f, crown, p)
            // 떨어진 상대 알 자리 (빈 고리)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.14f
            p.color = 0xAA3A2410.toInt()
            for (k in 0 until GameConfig.ALKKAGI_STONES) {
                c.drawCircle(w * 0.6f + (k % 2) * s * 1.1f, h * 0.2f + (k / 2) * s * 0.95f, r * 0.8f, p)
            }
            shout(c, p, "1위", w * 0.3f, h * 0.88f, h * 0.13f, Theme.GOLD)
        },
    )
}
