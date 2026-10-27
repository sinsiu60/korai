package com.siwoo.party.games.stack

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.stack.ui.StackActivity

/** 탑 쌓기: 모음집 등록 정보 (2~4인, 차례대로) */
object StackModule : GameModule {
    override val id = "stack"
    override val name = "탑 쌓기"
    override val tagline = "움직이는 블록을 딱 맞춰 떨어뜨려 하나의 탑을!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "4분"
    override val color = 0xFF5E35B1.toInt()
    override val activityClass = StackActivity::class.java

    private val C = Theme.PLAYER_COLORS
    private val arrow = Path()
    private const val SKY = 0xFF1B2A6B.toInt()
    private const val BASE = 0xFF8D99AE.toInt()

    /** 탑 블록 (아래 그림자 · 몸통 · 밝은 윗면) */
    private fun block(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, color: Int, alpha: Int = 255) {
        val h = b - t
        val rr = h * 0.14f
        p.style = Paint.Style.FILL
        p.color = Theme.shade(color, 0.62f); p.alpha = alpha
        c.drawRoundRect(l, t, r, b, rr, rr, p)
        p.color = color; p.alpha = alpha
        c.drawRoundRect(l, t, r, b - h * 0.16f, rr, rr, p)
        p.color = Theme.shade(color, 1.35f); p.alpha = alpha
        c.drawRoundRect(l, t, r, t + h * 0.24f, rr, rr, p)
        p.alpha = 255
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = SKY
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 땅 + 4층 탑 (조금씩 어긋남) + 떠 있는 블록
        val bh = r * 0.26f
        val base = cy + r * 0.62f
        paint.style = Paint.Style.FILL
        paint.color = 0xFF2E7D32.toInt()
        canvas.drawRect(cx - r * 0.62f, base, cx + r * 0.62f, base + r * 0.1f, paint)
        val w = r * 0.78f
        block(canvas, paint, cx - w / 2f, base - bh, cx + w / 2f, base, BASE)
        block(canvas, paint, cx - w / 2f + r * 0.06f, base - bh * 2f, cx + w / 2f, base - bh, C[0])
        block(canvas, paint, cx - w / 2f + r * 0.06f, base - bh * 3f, cx + w / 2f - r * 0.05f, base - bh * 2f, C[1])
        block(canvas, paint, cx - w / 2f + r * 0.1f, base - bh * 4f, cx + w / 2f - r * 0.05f, base - bh * 3f, C[2])
        // 위에서 움직이는 금색 블록
        val ty = base - bh * 5.25f
        block(canvas, paint, cx - w / 2f + r * 0.28f, ty, cx + w / 2f + r * 0.18f, ty + bh, Theme.GOLD)
    }

    /** 규칙 그림: 하늘 + 땅 + 블록들 (층 높이 bh) */
    private fun scene(c: Canvas, w: Float, h: Float, p: Paint): Float {
        p.style = Paint.Style.FILL
        p.color = SKY
        c.drawRect(0f, 0f, w, h, p)
        p.color = 0xFF2E7D32.toInt()
        c.drawRect(0f, h * 0.88f, w, h, p)
        p.color = 0xFF66BB6A.toInt()
        c.drawRect(0f, h * 0.88f, w, h * 0.9f, p)
        return h * 0.14f
    }

    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF10163A.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    private fun arrows(c: Canvas, p: Paint, cx: Float, cy: Float, half: Float, s: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.25f
        p.color = Theme.GOLD_LIGHT
        c.drawLine(cx - half, cy, cx + half, cy, p)
        p.style = Paint.Style.FILL
        arrow.reset()
        arrow.moveTo(cx - half - s, cy); arrow.lineTo(cx - half + s * 0.2f, cy - s * 0.7f); arrow.lineTo(cx - half + s * 0.2f, cy + s * 0.7f); arrow.close()
        arrow.moveTo(cx + half + s, cy); arrow.lineTo(cx + half - s * 0.2f, cy - s * 0.7f); arrow.lineTo(cx + half - s * 0.2f, cy + s * 0.7f); arrow.close()
        c.drawPath(arrow, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("차례대로 하나의 탑을", "블록이 좌우로 움직여요. 내 차례에\n화면을 터치하면 블록이 떨어져요.\n모두 돌아가며 같은 탑을 쌓아요.\n높이 올라갈수록 블록이 빨라져요!") { c, w, h, p ->
            val bh = scene(c, w, h, p)
            val g = h * 0.88f
            val bw = w * 0.3f
            val x = w * 0.42f
            block(c, p, x - bw / 2f, g - bh, x + bw / 2f, g, BASE)
            block(c, p, x - bw / 2f, g - bh * 2f, x + bw / 2f, g - bh, C[0])
            block(c, p, x - bw / 2f, g - bh * 3f, x + bw / 2f, g - bh * 2f, C[1])
            // 움직이는 블록
            val my = g - bh * 4.6f
            block(c, p, x - bw / 2f + w * 0.12f, my, x + bw / 2f + w * 0.12f, my + bh, C[2])
            arrows(c, p, x + w * 0.12f, my - bh * 0.55f, bw * 0.55f, bh * 0.35f)
            // 손가락
            p.style = Paint.Style.FILL
            p.color = 0xFFFFE0C8.toInt()
            c.drawCircle(w * 0.84f, h * 0.3f, h * 0.075f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = 0xFF8A5A26.toInt()
            c.drawCircle(w * 0.84f, h * 0.3f, h * 0.075f, p)
            p.color = Theme.GOLD_LIGHT
            c.drawCircle(w * 0.84f, h * 0.3f, h * 0.12f, p)
        },
        RuleCard("벗어난 부분은 잘려요", "아래 블록 밖으로 나간 부분은\n잘려 떨어지고, 남은 폭이 다음 블록 폭!\n거의 딱 맞으면 \"퍼펙트!\" — 폭이 그대로예요.") { c, w, h, p ->
            val bh = scene(c, w, h, p)
            val g = h * 0.88f
            val bw = w * 0.32f
            val x = w * 0.36f
            block(c, p, x - bw / 2f, g - bh, x + bw / 2f, g, BASE)
            block(c, p, x - bw / 2f, g - bh * 2f, x + bw / 2f, g - bh, C[0])
            // 오른쪽으로 어긋나 놓인 블록: 남은 부분 + 기울어 떨어지는 조각
            val off = bw * 0.3f
            block(c, p, x - bw / 2f + off, g - bh * 3f, x + bw / 2f, g - bh * 2f, C[1])
            c.save()
            c.rotate(28f, x + bw / 2f + off * 0.9f, g - bh * 1.9f)
            block(c, p, x + bw / 2f + off * 0.4f, g - bh * 2.4f, x + bw / 2f + off * 1.4f, g - bh * 1.4f, C[1], 200)
            c.restore()
            // 점선 (아래 블록 오른쪽 끝)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            p.color = 0xAAFFFFFF.toInt()
            var y = g - bh * 3.6f
            while (y < g - bh * 1.9f) { c.drawLine(x + bw / 2f, y, x + bw / 2f, y + bh * 0.18f, p); y += bh * 0.34f }
            shout(c, p, "퍼펙트!", w * 0.78f, h * 0.28f, h * 0.13f, Theme.GOLD)
        },
        RuleCard("빗나가면 라운드 패배", "블록이 아래 블록에 하나도 안 걸치면\n그 사람이 라운드 패배 (해골 1개).\n다음 라운드는 진 사람 다음부터!\n${GameConfig.STACK_ROUNDS}라운드 뒤 패배가 적은 순서로 순위.") { c, w, h, p ->
            val bh = scene(c, w, h, p)
            val g = h * 0.88f
            val bw = w * 0.22f
            val x = w * 0.4f
            for (k in 0 until 4) {
                val o = (k % 2) * bw * 0.06f
                block(c, p, x - bw / 2f + o, g - bh * (k + 1), x + bw / 2f + o, g - bh * k, if (k == 0) BASE else C[k - 1])
            }
            // 완전히 옆으로 떨어지는 블록
            c.save()
            c.rotate(35f, x + bw * 1.3f, g - bh * 3f)
            block(c, p, x + bw * 0.85f, g - bh * 3.5f, x + bw * 1.75f, g - bh * 2.5f, C[3], 220)
            c.restore()
            // 해골
            val sx = w * 0.82f
            val sy = h * 0.34f
            val s = h * 0.2f
            p.style = Paint.Style.FILL
            p.color = 0xFFF2F2F2.toInt()
            c.drawCircle(sx, sy - s * 0.08f, s * 0.42f, p)
            c.drawRect(sx - s * 0.22f, sy + s * 0.15f, sx + s * 0.22f, sy + s * 0.45f, p)
            p.color = Theme.INK
            c.drawCircle(sx - s * 0.15f, sy - s * 0.08f, s * 0.11f, p)
            c.drawCircle(sx + s * 0.15f, sy - s * 0.08f, s * 0.11f, p)
        },
    )
}
