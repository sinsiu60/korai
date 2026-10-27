package com.siwoo.party.games.tuho

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.tuho.ui.TuhoActivity
import com.siwoo.party.games.tuho.ui.TuhoArt

/** 투호: 모음집 등록 정보 (2~4인, 차례대로, 1인당 화살 10개) */
object TuhoModule : GameModule {
    override val id = "tuho"
    override val name = "투호"
    override val tagline = "각도와 힘을 멈춰 항아리에 화살을 쏙!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "6분"
    override val color = 0xFF2F7D6B.toInt()
    override val activityClass = TuhoActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val GREEN = Theme.PLAYER_COLORS[2]
    private val r = RectF()

    /** 항아리 높이(입구까지)를 h 픽셀로 그릴 때의 s (1미터 = s 픽셀) */
    private fun scaleFor(h: Float): Float = h / GameConfig.MOUTH_Y

    /** 구멍에 꽂힌 화살 (그림용) */
    private fun stuck(c: Canvas, p: Paint, jx: Float, fy: Float, s: Float, o: Opening, off: Float, tiltDeg: Float, color: Int) {
        val t = Math.toRadians(tiltDeg.toDouble())
        val dx = Math.sin(t).toFloat()
        val dy = Math.cos(t).toFloat()
        val depth = 0.07f * s
        val x = jx + (o.cx - GameConfig.JAR_X) * s + off * o.half * s + dx / dy * depth
        TuhoArt.arrow(c, p, x, fy - o.top * s + depth, dx, dy, GameConfig.ARROW_LEN * s, color)
    }

    /** 점선 포물선 (x0,y0) → 꼭짓점 높이 apex 위 → (x1,y1) */
    private fun dottedArc(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, lift: Float, dot: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        val n = 16
        for (k in 1 until n) {
            val f = k / n.toFloat()
            val x = x0 + (x1 - x0) * f
            val y = y0 + (y1 - y0) * f - lift * 4f * f * (1f - f)
            c.drawCircle(x, y, dot, p)
        }
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val s = scaleFor(size * 0.4f)
        val fy = cy + size * 0.34f
        val jx = cx + size * 0.12f
        // 마루
        paint.style = Paint.Style.FILL
        paint.color = 0x66000000
        r.set(cx - size * 0.42f, fy - size * 0.02f, cx + size * 0.42f, fy + size * 0.05f)
        canvas.drawRoundRect(r, size * 0.03f, size * 0.03f, paint)
        // 날아오는 길 (점선)
        val m = TuhoField.mouth
        dottedArc(canvas, paint, cx - size * 0.4f, cy + size * 0.12f, jx, fy - m.top * s, size * 0.32f, size * 0.022f, Theme.GOLD_LIGHT)
        TuhoArt.jarShadow(canvas, paint, jx, fy, s)
        TuhoArt.jarBody(canvas, paint, jx, fy, s)
        stuck(canvas, paint, jx, fy, s, m, 0f, 14f, RED)
        stuck(canvas, paint, jx, fy, s, TuhoField.earNear, 0f, 10f, BLUE)
        TuhoArt.jarFront(canvas, paint, jx, fy, s)
        paint.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("① 각도 게이지를 멈춰요", "내 차례가 되면 손 둘레의 각도 바늘이\n왔다 갔다 해요 (20°~65°).\n원하는 각도에서 화면을 터치!") { c, w, h, p ->
            val fy = h * 0.9f
            val s = h * 0.62f
            // 바닥
            p.style = Paint.Style.FILL
            p.color = 0xFF8A5A2E.toInt()
            c.drawRect(w * 0.06f, fy, w * 0.94f, fy + h * 0.04f, p)
            val px = w * 0.3f
            val tipX = px + 0.24f * s
            val tipY = fy - GameConfig.RELEASE_Y * s
            val a = Math.toRadians(42.0)
            val dx = Math.cos(a).toFloat()
            val dy = -Math.sin(a).toFloat()
            val hold = GameConfig.ARROW_LEN * s * 0.45f
            // 부채꼴 띠 + 바늘
            val rad = GameConfig.ARROW_LEN * s * 1.25f
            r.set(tipX - rad, tipY - rad, tipX + rad, tipY + rad)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.05f
            p.color = 0x66FFFFFF
            c.drawArc(r, -GameConfig.ANGLE_MAX, GameConfig.ANGLE_MAX - GameConfig.ANGLE_MIN, false, p)
            TuhoArt.thrower(c, p, px, fy, s, RED, tipX - dx * hold, tipY - dy * hold)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.02f
            p.color = Theme.TEXT
            c.drawLine(tipX, tipY, tipX + dx * (rad + h * 0.05f), tipY + dy * (rad + h * 0.05f), p)
            p.strokeCap = Paint.Cap.BUTT
            TuhoArt.arrow(c, p, tipX, tipY, dx, dy, GameConfig.ARROW_LEN * s, RED)
            // 왕복 화살표
            TuhoArt.shout(c, p, "↺ 왔다 갔다", w * 0.8f, h * 0.3f, h * 0.1f, Theme.GOLD_LIGHT)
            TuhoArt.shout(c, p, "42°", tipX + rad * 0.8f, tipY + h * 0.06f, h * 0.09f, Theme.TEXT)
            // 손가락 터치
            p.style = Paint.Style.FILL
            p.color = 0xFFFFE0C8.toInt()
            c.drawCircle(w * 0.78f, h * 0.6f, h * 0.07f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            p.color = Theme.GOLD
            c.drawCircle(w * 0.78f, h * 0.6f, h * 0.11f, p)
            c.drawCircle(w * 0.78f, h * 0.6f, h * 0.15f, p)
            TuhoArt.shout(c, p, "터치!", w * 0.78f, h * 0.86f, h * 0.1f, Theme.GOLD)
        },
        RuleCard("② 힘 게이지를 멈추면 슝!", "이어서 힘 막대가 오르내려요.\n터치하면 그 각도·힘으로 날아가요.\n바람은 없어요 — 같은 각도·힘이면 늘 같은 곳!") { c, w, h, p ->
            val fy = h * 0.9f
            // 힘 막대
            val bl = w * 0.08f
            val br = w * 0.14f
            val bt = h * 0.12f
            val bb = h * 0.86f
            p.style = Paint.Style.FILL
            p.color = 0xAA1A1030.toInt()
            r.set(bl, bt, br, bb)
            c.drawRoundRect(r, (br - bl) / 2f, (br - bl) / 2f, p)
            p.color = 0xDDE0502A.toInt()
            c.drawRect(bl + h * 0.012f, bt + (bb - bt) * 0.38f, br - h * 0.012f, bb - h * 0.012f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            p.color = Theme.GOLD
            c.drawRoundRect(r, (br - bl) / 2f, (br - bl) / 2f, p)
            p.style = Paint.Style.FILL
            p.color = Theme.TEXT
            val ny = bt + (bb - bt) * 0.38f
            c.drawRect(br + h * 0.01f, ny - h * 0.012f, br + h * 0.06f, ny + h * 0.012f, p)
            TuhoArt.shout(c, p, "▲▼", (bl + br) / 2f, bt - h * 0.025f, h * 0.07f, Theme.TEXT)
            // 바닥 + 포물선 + 항아리
            p.color = 0xFF8A5A2E.toInt()
            c.drawRect(w * 0.2f, fy, w * 0.94f, fy + h * 0.04f, p)
            val s = scaleFor(h * 0.42f)
            val jx = w * 0.8f
            val m = TuhoField.mouth
            dottedArc(c, p, w * 0.24f, fy - h * 0.4f, jx, fy - m.top * s, h * 0.55f, h * 0.014f, Theme.GOLD_LIGHT)
            TuhoArt.jarShadow(c, p, jx, fy, s)
            TuhoArt.jarBody(c, p, jx, fy, s)
            TuhoArt.jarFront(c, p, jx, fy, s)
            // 날아가는 화살 (꼭짓점 근처)
            TuhoArt.arrow(c, p, w * 0.56f, fy - h * 0.4f - h * 0.5f, 0.97f, 0.24f, h * 0.24f, BLUE)
            TuhoArt.shout(c, p, "슝!", w * 0.42f, h * 0.3f, h * 0.11f, Theme.GOLD)
        },
        RuleCard("③ 입구 2점 · 귀 3점", "가운데 입구 2점, 양옆의 좁은 귀 3점! 탁!\n테두리·몸통에 맞거나 바닥에 떨어지면 0점.\n한 개씩 돌아가며 10개씩, 합계로 순위.") { c, w, h, p ->
            val fy = h * 0.92f
            p.style = Paint.Style.FILL
            p.color = 0xFF8A5A2E.toInt()
            c.drawRect(w * 0.08f, fy, w * 0.92f, fy + h * 0.04f, p)
            val s = scaleFor(h * 0.4f)
            val jx = w * 0.42f
            TuhoArt.jarShadow(c, p, jx, fy, s)
            TuhoArt.jarBody(c, p, jx, fy, s)
            stuck(c, p, jx, fy, s, TuhoField.mouth, 0f, 12f, RED)
            stuck(c, p, jx, fy, s, TuhoField.earNear, 0f, 8f, BLUE)
            stuck(c, p, jx, fy, s, TuhoField.earFar, 0f, 16f, GREEN)
            TuhoArt.jarFront(c, p, jx, fy, s)
            // 점수 꼬리표
            val m = TuhoField.mouth
            TuhoArt.shout(c, p, "입구 2", jx, fy - (m.top + GameConfig.ARROW_LEN + 0.06f) * s, h * 0.085f, Theme.GOLD_LIGHT)
            TuhoArt.shout(c, p, "귀 3", jx - (GameConfig.EAR_DX + 0.3f) * s, fy - GameConfig.EAR_NEAR_Y * s - h * 0.02f, h * 0.085f, Theme.GOLD)
            TuhoArt.shout(c, p, "귀 3", jx + (GameConfig.EAR_DX + 0.36f) * s, fy - GameConfig.EAR_FAR_Y * s - h * 0.02f, h * 0.085f, Theme.GOLD)
            // 오른쪽: 바닥에 떨어진 화살 = 0점
            TuhoArt.arrow(c, p, w * 0.86f, fy + h * 0.01f, 0.9f, 0.43f, h * 0.22f, Theme.GRAY)
            TuhoArt.shout(c, p, "0점", w * 0.8f, fy - h * 0.14f, h * 0.08f, Theme.TEXT_SUB)
        },
    )
}
