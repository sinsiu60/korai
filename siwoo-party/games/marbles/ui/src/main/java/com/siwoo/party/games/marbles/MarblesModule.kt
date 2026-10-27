package com.siwoo.party.games.marbles

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
import com.siwoo.party.games.marbles.ui.MarbleArt
import com.siwoo.party.games.marbles.ui.MarblesActivity

/** 구슬치기: 모음집 등록 정보 (2~4인, 차례대로) */
object MarblesModule : GameModule {
    override val id = "marbles"
    override val name = "구슬치기"
    override val tagline = "내 구슬을 튕겨 링 밖으로 쳐내면 내 것!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "5분"
    override val color = 0xFF9C5B2E.toInt()
    override val activityClass = MarblesActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val C = MarbleArt.COLORS
    private val crown = Path()
    private val arrow = Path()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF2B3C8F.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        // 흙 마당 (동그랗게)
        paint.color = MarbleArt.DIRT_DARK
        canvas.drawCircle(cx, cy, r * 0.86f, paint)
        paint.color = MarbleArt.DIRT
        canvas.drawCircle(cx - r * 0.03f, cy - r * 0.03f, r * 0.8f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 링
        MarbleArt.ring(canvas, paint, cx + r * 0.06f, cy - r * 0.04f, r * 0.48f, r * 0.05f)
        // 가운데 구슬 무리 + 링 밖으로 튀어 나가는 구슬
        val m = r * 0.16f
        MarbleArt.draw(canvas, cx - r * 0.02f, cy + r * 0.02f, m, C[6], paint, shadow = true)
        MarbleArt.draw(canvas, cx + r * 0.3f, cy - r * 0.16f, m, C[3], paint, shadow = true)
        MarbleArt.draw(canvas, cx + r * 0.24f, cy + r * 0.24f, m, C[1], paint, shadow = true)
        MarbleArt.draw(canvas, cx + r * 0.58f, cy - r * 0.56f, m, C[8], paint, shadow = true)
        // 내 구슬 (금테) + 지나온 줄
        val sx = cx - r * 0.46f
        val sy = cy + r * 0.44f
        val sm = m * 1.22f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = sm * 0.24f
        paint.color = 0xDDFFFFFF.toInt()
        canvas.drawLine(sx - sm * 2.0f, sy + sm * 1.0f, sx - sm * 1.3f, sy + sm * 0.6f, paint)
        canvas.drawLine(sx - sm * 1.5f, sy + sm * 1.7f, sx - sm * 0.9f, sy + sm * 1.2f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        MarbleArt.draw(canvas, sx, sy, sm, RED, paint, shadow = true, shooter = true)
        paint.style = Paint.Style.FILL
    }

    /** 규칙 그림 공통: 카드 안에 흙 마당 + 링 (가운데 cx, cy, 반지름 rr) */
    private fun ground(c: Canvas, w: Float, h: Float, p: Paint, cx: Float, cy: Float, rr: Float) {
        MarbleArt.dirt(c, p, w * 0.03f, h * 0.06f, w * 0.97f, h * 0.94f, h * 0.06f)
        MarbleArt.ring(c, p, cx, cy, rr, h * 0.025f)
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

    /** 점선 (점을 하나씩 — 할당 없음) */
    private fun dots(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, n: Int, r: Float) {
        p.style = Paint.Style.FILL
        for (k in 0..n) {
            val f = k.toFloat() / n
            c.drawCircle(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, r, p)
        }
    }

    /** 가운데 구슬 무리 */
    private fun cluster(c: Canvas, p: Paint, cx: Float, cy: Float, m: Float) {
        val s = m * 2.15f
        MarbleArt.draw(c, cx, cy, m, C[0], p)
        MarbleArt.draw(c, cx + s, cy - m * 0.3f, m, C[3], p)
        MarbleArt.draw(c, cx - s * 0.6f, cy + s * 0.85f, m, C[6], p)
        MarbleArt.draw(c, cx + s * 0.5f, cy + s * 0.9f, m, C[2], p)
        MarbleArt.draw(c, cx - s * 0.75f, cy - s * 0.75f, m, C[8], p)
        MarbleArt.draw(c, cx + s * 0.35f, cy - s * 1.0f, m, C[9], p)
    }

    /** 손가락 */
    private fun finger(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFFFFE0C8.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.18f
        p.color = 0xFF8A5A26.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("링 밖에 내 구슬 놓기", "내 차례에 링 밖 아무 곳이나 누르면\n내 구슬(금테)이 그 자리에 놓여요.\n링 안을 누르면 링 밖으로 당겨지고,\n다른 곳을 누르면 옮길 수 있어요.") { c, w, h, p ->
            val cx = w * 0.42f
            val cy = h * 0.5f
            val rr = h * 0.3f
            ground(c, w, h, p, cx, cy, rr)
            // 놓을 수 있는 곳 경계 (점선 원)
            p.color = 0xCCFFF2A0.toInt()
            for (k in 0 until 36) {
                val a = k * 0.17453292f
                c.drawCircle(cx + kotlin.math.cos(a) * rr * 1.22f, cy + kotlin.math.sin(a) * rr * 1.22f, h * 0.008f, p)
            }
            val m = h * 0.045f
            cluster(c, p, cx, cy, m)
            // 링 밖에 놓인 내 구슬 + 누른 손가락
            val sx = cx + rr * 1.55f
            val sy = cy + rr * 0.55f
            MarbleArt.draw(c, sx, sy, m * 1.25f, RED, p, shooter = true)
            finger(c, p, sx + m * 2.2f, sy + m * 2.4f, m * 1.1f)
            // 링 안을 누르면 → 밖으로 (화살표)
            p.color = 0xEEFFFFFF.toInt()
            dots(c, p, cx - rr * 0.55f, cy + rr * 0.35f, cx - rr * 1.25f, cy + rr * 0.78f, 5, h * 0.011f)
            arrow.reset()
            val ax = cx - rr * 1.38f
            val ay = cy + rr * 0.86f
            arrow.moveTo(ax, ay)
            arrow.lineTo(ax + m * 1.2f, ay - m * 0.15f)
            arrow.lineTo(ax + m * 0.45f, ay - m * 1.05f)
            arrow.close()
            c.drawPath(arrow, p)
            MarbleArt.draw(c, cx - rr * 1.5f, cy + rr * 1.0f, m * 1.25f, RED, p, shooter = true, alpha = 150)
        },
        RuleCard("뒤로 끌었다 놓아 쏘기", "내 구슬을 누르고 뒤로 끌었다 놓으면\n새총처럼 반대쪽으로 굴러가요.\n많이 끌수록 세게! (짧으면 취소)\n쏜 구슬은 다시 내 손으로 돌아와요.") { c, w, h, p ->
            val cx = w * 0.6f
            val cy = h * 0.5f
            val rr = h * 0.3f
            ground(c, w, h, p, cx, cy, rr)
            val m = h * 0.045f
            cluster(c, p, cx, cy, m)
            val sx = cx - rr * 1.55f
            val sy = cy + rr * 0.45f
            // 고무줄 + 손가락
            val fx = sx - m * 3.6f
            val fy = sy + m * 2.4f
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = m * 0.35f
            p.color = 0xCCFFE08A.toInt()
            c.drawLine(sx, sy, fx, fy, p)
            p.strokeCap = Paint.Cap.BUTT
            // 날아갈 방향 점선
            p.color = 0xFFFFFFFF.toInt()
            val dx = (sx - fx)
            val dy = (sy - fy)
            dots(c, p, sx + dx * 0.45f, sy + dy * 0.45f, sx + dx * 2.1f, sy + dy * 2.1f, 6, h * 0.012f)
            // 힘 고리
            p.style = Paint.Style.STROKE
            p.strokeWidth = m * 0.3f
            p.color = 0x66000000
            c.drawCircle(sx, sy, m * 2f, p)
            p.color = Theme.GOLD
            c.drawArc(sx - m * 2f, sy - m * 2f, sx + m * 2f, sy + m * 2f, -90f, 250f, false, p)
            p.style = Paint.Style.FILL
            MarbleArt.draw(c, sx, sy, m * 1.25f, RED, p, shooter = true)
            finger(c, p, fx, fy, m * 1.1f)
        },
        RuleCard("링 밖으로 = 내 것!", "가운데가 링 밖으로 나간 구슬은 내 것!\n하나라도 얻으면 한 번 더 쏴요.\n구슬 ${GameConfig.TARGETS}개가 다 없어지면 끝,\n많이 모은 순서로 순위 (최대 ${GameConfig.MAX_ROUNDS}바퀴)") { c, w, h, p ->
            val cx = w * 0.36f
            val cy = h * 0.52f
            val rr = h * 0.3f
            ground(c, w, h, p, cx, cy, rr)
            val m = h * 0.045f
            MarbleArt.draw(c, cx - m * 1.2f, cy + m * 0.4f, m, C[6], p)
            MarbleArt.draw(c, cx + m * 1.4f, cy - m * 1.6f, m, C[2], p)
            // 선에 걸침 (가운데는 안) → 아직 아님 / 가운데가 밖 → 획득
            MarbleArt.draw(c, cx + rr * 0.92f, cy + rr * 0.35f, m, C[3], p)
            val ox = cx + rr * 1.35f
            val oy = cy - rr * 0.55f
            p.style = Paint.Style.STROKE
            p.strokeWidth = m * 0.25f
            p.color = Theme.GOLD
            c.drawCircle(ox, oy, m * 1.45f, p)
            p.style = Paint.Style.FILL
            MarbleArt.draw(c, ox, oy, m, C[0], p)
            // 내 패널로 날아감
            p.color = 0xEEFFFFFF.toInt()
            dots(c, p, ox + m * 1.8f, oy - m * 0.6f, w * 0.8f, h * 0.3f, 5, h * 0.01f)
            shout(c, p, "+1", w * 0.84f, h * 0.29f, h * 0.15f, Theme.GOLD)
            shout(c, p, "한 번 더!", w * 0.8f, h * 0.62f, h * 0.11f, Theme.TEXT)
            p.color = Theme.GOLD
            Ui.drawCrown(c, w * 0.8f, h * 0.86f, h * 0.16f, crown, p)
            MarbleArt.draw(c, w * 0.92f, h * 0.81f, m * 0.9f, BLUE, p, shooter = true)
        },
    )
}
