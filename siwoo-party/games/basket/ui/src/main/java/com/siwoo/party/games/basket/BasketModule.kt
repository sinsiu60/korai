package com.siwoo.party.games.basket

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.basket.ui.BasketActivity
import com.siwoo.party.games.basket.ui.BasketArt

/** 농구 슛: 모음집 등록 정보 (2~4인, 차례대로) */
object BasketModule : GameModule {
    override val id = "basket"
    override val name = "농구 슛"
    override val tagline = "위로 쓱 밀어 골인! 연속 골엔 불꽃"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "6분"
    override val color = 0xFFD9601E.toInt()
    override val activityClass = BasketActivity::class.java

    private val path = Path()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF1E2E78.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        // 마루
        canvas.save()
        path.reset()
        path.addCircle(cx, cy, r * 0.93f, Path.Direction.CW)
        canvas.clipPath(path)
        paint.color = BasketArt.WOOD
        canvas.drawRect(cx - r, cy + r * 0.55f, cx + r, cy + r, paint)
        canvas.restore()
        // 골대 + 들어가는 공 + 점 궤적
        BasketArt.hoop(canvas, paint, cx + r * 0.22f, cy - r * 0.12f, r * 0.3f)
        paint.color = 0xCCFFFFFF.toInt()
        for (k in 0 until 4) {
            val f = k / 4f
            canvas.drawCircle(cx - r * 0.7f + f * r * 0.6f, cy + r * 0.25f - f * r * 0.65f + f * f * r * 0.2f, r * 0.04f, paint)
        }
        BasketArt.ball(canvas, paint, cx + r * 0.12f, cy - r * 0.45f, r * 0.2f, 0.4f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        paint.style = Paint.Style.FILL
    }

    /** 규칙 그림 바탕: 체육관 벽 + 마루 (마루 윗선 y 를 돌려줌) */
    private fun gym(c: Canvas, w: Float, h: Float, p: Paint): Float {
        val floor = h * 0.8f
        p.style = Paint.Style.FILL
        p.color = 0xFF1E2E78.toInt()
        c.drawRect(0f, 0f, w, floor, p)
        p.color = BasketArt.WOOD
        c.drawRect(0f, floor, w, h, p)
        p.color = 0x22000000
        c.drawRect(0f, floor, w, floor + h * 0.03f, p)
        return floor
    }

    /** 옆에서 본 사람 (발 (x, floor), 키 hgt) — 공을 머리 위로 */
    private fun shooter(c: Canvas, p: Paint, x: Float, floor: Float, hgt: Float, color: Int) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = hgt * 0.07f
        p.color = 0xFF2A1E18.toInt()
        c.drawLine(x - hgt * 0.03f, floor - hgt * 0.45f, x - hgt * 0.07f, floor, p)
        c.drawLine(x + hgt * 0.03f, floor - hgt * 0.45f, x + hgt * 0.06f, floor, p)
        p.color = 0xFFE7B08A.toInt()
        p.strokeWidth = hgt * 0.05f
        c.drawLine(x, floor - hgt * 0.75f, x + hgt * 0.1f, floor - hgt * 1.05f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRoundRect(x - hgt * 0.1f, floor - hgt * 0.8f, x + hgt * 0.1f, floor - hgt * 0.45f, hgt * 0.04f, hgt * 0.04f, p)
        p.color = 0xFFE7B08A.toInt()
        c.drawCircle(x, floor - hgt * 0.9f, hgt * 0.08f, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("위로 쓱 밀어 슛!", "내 차례에 화면을 누르고 골대 쪽 위로\n쓱 밀었다 떼요. 빠르게 밀수록 세게,\n더 위로 밀수록 높이 던져요.\n미는 동안 점선으로 날아갈 길이 보여요.") { c, w, h, p ->
            val floor = gym(c, w, h, p)
            val u = h * 0.2f
            shooter(c, p, w * 0.2f, floor, u * 2.6f, Theme.PLAYER_COLORS[0])
            BasketArt.hoop(c, p, w * 0.78f, h * 0.32f, u * 0.4f)
            // 궤적 점선
            p.color = 0xEEFFFFFF.toInt()
            val x0 = w * 0.25f
            val y0 = floor - u * 3.0f
            val x1 = w * 0.76f
            val y1 = h * 0.3f
            for (k in 1..8) {
                val f = k / 9f
                val x = x0 + (x1 - x0) * f
                val y = y0 + (y1 - y0) * f - h * 0.3f * 4f * f * (1f - f)
                c.drawCircle(x, y, u * 0.06f, p)
            }
            BasketArt.ball(c, p, w * 0.235f, floor - u * 2.95f, u * 0.2f, 0.3f)
            // 손가락 스와이프 자국 (위로)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = u * 0.16f
            p.color = 0x99FFFFFF.toInt()
            c.drawLine(w * 0.42f, h * 0.72f, w * 0.5f, h * 0.38f, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
            p.color = 0xFFFFE0C8.toInt()
            c.drawCircle(w * 0.5f, h * 0.38f, u * 0.17f, p)
        },
        RuleCard("갈수록 멀어져요", "모두 같은 자리에서 한 번씩 던지면\n다음 바퀴는 더 먼 자리! 모두 10슛.\n2점짜리 골, 3점 라인 밖에서는 3점.\n림·보드에 맞고 들어가도 골이에요.") { c, w, h, p ->
            val floor = gym(c, w, h, p)
            val u = h * 0.2f
            BasketArt.hoop(c, p, w * 0.86f, h * 0.3f, u * 0.32f)
            // 슛 자리 점들 (오른쪽에서 왼쪽으로 멀어짐)
            val line3 = w * 0.3f
            for (k in 0 until 10) {
                val x = w * 0.74f - k * w * 0.068f
                p.color = if (x < line3) Theme.GOLD else 0xCCFFFFFF.toInt()
                c.drawOval(x - u * 0.12f, floor + h * 0.07f, x + u * 0.12f, floor + h * 0.12f, p)
            }
            // 3점 라인
            p.color = 0xEEFFFFFF.toInt()
            c.drawRect(line3 - u * 0.03f, floor, line3 + u * 0.03f, h, p)
            BasketArt.shout(c, p, "2점", w * 0.55f, h * 0.55f, h * 0.15f, Theme.TEXT)
            BasketArt.shout(c, p, "3점!", w * 0.15f, h * 0.55f, h * 0.18f, Theme.GOLD)
            // 가까운 곳 → 먼 곳 화살표
            p.color = 0xAAFFFFFF.toInt()
            p.style = Paint.Style.STROKE
            p.strokeWidth = u * 0.06f
            c.drawLine(w * 0.72f, h * 0.68f, w * 0.36f, h * 0.68f, p)
            p.style = Paint.Style.FILL
            path.reset()
            path.moveTo(w * 0.33f, h * 0.68f)
            path.lineTo(w * 0.37f, h * 0.68f - u * 0.15f)
            path.lineTo(w * 0.37f, h * 0.68f + u * 0.15f)
            path.close()
            c.drawPath(path, p)
        },
        RuleCard("연속 골이면 불꽃!", "두 번 연속으로 넣으면 공에 불이 붙어요.\n불꽃일 때 넣으면 골마다 +1점 보너스!\n놓치면 불꽃이 꺼져요.\n10슛이 끝나면 합계 점수로 순위.") { c, w, h, p ->
            gym(c, w, h, p)
            val u = h * 0.2f
            val bx = w * 0.3f
            val by = h * 0.55f
            BasketArt.fireCrown(c, p, path, bx, by, u * 0.62f, 0.8f)
            BasketArt.ball(c, p, bx, by, u * 0.62f, 0.6f)
            // 불꽃 꼬리
            for (k in 1..5) {
                p.color = if (k < 3) BasketArt.FLAME_ORANGE else BasketArt.FLAME_RED
                p.alpha = 220 - k * 35
                c.drawCircle(bx - k * u * 0.45f, by + k * u * 0.12f, u * (0.4f - k * 0.05f), p)
            }
            p.alpha = 255
            BasketArt.shout(c, p, "+1", w * 0.62f, h * 0.5f, h * 0.3f, BasketArt.FLAME_YELLOW)
            BasketArt.shout(c, p, "골! 골! 골!", w * 0.66f, h * 0.82f, h * 0.13f, Theme.TEXT)
        },
    )
}
