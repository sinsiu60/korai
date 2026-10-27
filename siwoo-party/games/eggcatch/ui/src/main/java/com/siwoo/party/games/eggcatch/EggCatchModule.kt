package com.siwoo.party.games.eggcatch

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.eggcatch.ui.EggArt
import com.siwoo.party.games.eggcatch.ui.EggCatchActivity

/** 계란 받기: 모음집 등록 정보 */
object EggCatchModule : GameModule {
    override val id = "eggcatch"
    override val name = "계란 받기"
    override val tagline = "똑같이 떨어지는 계란을 바구니로 쏙! 폭탄은 피해요"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "1분"
    override val color = 0xFFFFB13B.toInt()
    override val activityClass = EggCatchActivity::class.java

    private const val SKY = 0xFF1E3C8C.toInt()
    private const val SKY_LIGHT = 0xFF3A62C4.toInt()
    private const val PINK = 0xFFFF5FA8.toInt()

    private fun label(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.textSize = size
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.color = color
        c.drawText(text, x, y, p)
        p.typeface = null
        p.textAlign = Paint.Align.LEFT
    }

    /** 레인 4개 바탕 (x0..x1, y0..y1) + 풀밭 */
    private fun lanes(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.BG_DEEP
        c.drawRoundRect(x0, y0, x1, y1, (x1 - x0) * 0.04f, (x1 - x0) * 0.04f, p)
        val lw = (x1 - x0) / 4f
        for (k in 0 until 4) {
            p.color = color
            p.alpha = if (k % 2 == 0) 30 else 48
            c.drawRect(x0 + k * lw + 1f, y0, x0 + (k + 1) * lw - 1f, y1, p)
        }
        p.alpha = 255
        p.color = EggArt.GRASS
        c.drawRect(x0, y1 - (y1 - y0) * 0.06f, x1, y1, p)
    }

    /** 왼쪽·오른쪽 화살표 쌍 */
    private fun arrows(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.12f
        p.color = Theme.GOLD_LIGHT
        c.drawLine(cx - s, cy, cx - s * 0.35f, cy, p)
        c.drawLine(cx - s, cy, cx - s * 0.75f, cy - s * 0.25f, p)
        c.drawLine(cx - s, cy, cx - s * 0.75f, cy + s * 0.25f, p)
        c.drawLine(cx + s, cy, cx + s * 0.35f, cy, p)
        c.drawLine(cx + s, cy, cx + s * 0.75f, cy - s * 0.25f, p)
        c.drawLine(cx + s, cy, cx + s * 0.75f, cy + s * 0.25f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /** 손가락 (끝 x, y, 크기 s) */
    private fun finger(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFFFFD7B0.toInt()
        c.drawRoundRect(x - s * 0.22f, y - s * 0.1f, x + s * 0.22f, y + s * 1.2f, s * 0.22f, s * 0.22f, p)
        p.color = 0xFFFFE9D6.toInt()
        c.drawOval(x - s * 0.13f, y - s * 0.02f, x + s * 0.13f, y + s * 0.3f, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.05f, cy + r * 0.08f, r, paint)
        paint.color = SKY
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = SKY_LIGHT
        canvas.drawCircle(cx, cy - r * 0.15f, r * 0.72f, paint)
        // 풀밭
        canvas.save()
        canvas.clipRect(cx - r, cy + r * 0.62f, cx + r, cy + r)
        paint.color = EggArt.GRASS
        canvas.drawCircle(cx, cy, r, paint)
        canvas.restore()
        // 떨어지는 황금 계란 · 계란
        EggArt.egg(canvas, cx + r * 0.36f, cy - r * 0.42f, r * 0.27f, true, 255, paint)
        EggArt.twinkle(canvas, cx + r * 0.58f, cy - r * 0.66f, r * 0.12f, 0xFFFFFFFF.toInt(), 230, paint)
        EggArt.egg(canvas, cx - r * 0.34f, cy - r * 0.2f, r * 0.22f, false, 255, paint)
        // 바구니 + 담긴 계란
        EggArt.egg(canvas, cx - r * 0.18f, cy + r * 0.2f, r * 0.17f, false, 255, paint)
        EggArt.egg(canvas, cx + r * 0.16f, cy + r * 0.18f, r * 0.17f, true, 255, paint)
        EggArt.basket(canvas, cx, cy + r * 0.3f, r * 1.1f, r * 0.5f, PINK, 0f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.94f, paint)
        paint.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("손가락으로 바구니를 좌우로", "내 구역 어디든 손가락을 대고 좌우로 끌면 바구니가 따라와요.\n모두에게 똑같은 계란이 똑같이 떨어져요!") { c, w, h, p ->
            val x0 = w * 0.1f
            val x1 = w * 0.6f
            val y0 = h * 0.04f
            val y1 = h * 0.96f
            lanes(c, x0, y0, x1, y1, PINK, p)
            val lw = (x1 - x0) / 4f
            val er = minOf(lw * 0.36f, h * 0.1f)
            EggArt.egg(c, x0 + lw * 1.5f, h * 0.24f, er, false, 255, p)
            EggArt.egg(c, x0 + lw * 2.5f, h * 0.48f, er, false, 255, p)
            val rimY = h * 0.72f
            EggArt.basket(c, x0 + lw * 2.5f, rimY, lw * 1.05f, h * 0.16f, PINK, 0f, p)
            // 바구니 선
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            p.color = Theme.GOLD
            p.alpha = 140
            c.drawLine(x0 + 4f, rimY, x1 - 4f, rimY, p)
            p.alpha = 255
            p.style = Paint.Style.FILL
            // 손가락 + 화살표
            finger(c, w * 0.8f, h * 0.48f, h * 0.36f, p)
            arrows(c, w * 0.8f, h * 0.3f, w * 0.12f, p)
        },
        RuleCard("계란 +1 · 황금 +3 · 폭탄 −3", "바구니 선에 닿을 때 바구니에 들어가면 받은 거예요.\n황금 계란은 3점! 폭탄을 받으면 펑! 3점이 깎여요.") { c, w, h, p ->
            val y = h * 0.42f
            val r = minOf(h * 0.2f, w * 0.1f)
            EggArt.egg(c, w * 0.2f, y, r, false, 255, p)
            EggArt.egg(c, w * 0.5f, y, r, true, 255, p)
            EggArt.twinkle(c, w * 0.5f + r * 0.7f, y - r * 0.7f, r * 0.3f, 0xFFFFFFFF.toInt(), 240, p)
            EggArt.twinkle(c, w * 0.5f - r * 0.85f, y + r * 0.1f, r * 0.2f, Theme.GOLD_LIGHT, 240, p)
            EggArt.bomb(c, w * 0.8f, y + r * 0.1f, r * 0.8f, 1f, 255, p)
            label(c, "+1", w * 0.2f, h * 0.9f, h * 0.16f, Theme.TEXT, p)
            label(c, "+3", w * 0.5f, h * 0.9f, h * 0.16f, Theme.GOLD, p)
            label(c, "−3", w * 0.8f, h * 0.9f, h * 0.16f, Theme.RED, p)
        },
        RuleCard("45초, 점점 빨라져요", "놓친 계란은 땅에 떨어져 깨질 뿐(0점), 폭탄은 피하면 0점.\n갈수록 빠르고 촘촘해져요. 45초 뒤 점수 순!") { c, w, h, p ->
            // 깨진 계란 + 피한 폭탄
            p.style = Paint.Style.FILL
            p.color = EggArt.GRASS
            c.drawRoundRect(w * 0.04f, h * 0.68f, w * 0.56f, h * 0.8f, h * 0.03f, h * 0.03f, p)
            EggArt.splat(c, w * 0.2f, h * 0.72f, minOf(h * 0.5f, w * 0.26f), false, 255, p)
            EggArt.egg(c, w * 0.42f, h * 0.36f, minOf(h * 0.14f, w * 0.07f), false, 255, p)
            // 빨라짐 줄
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.GOLD_LIGHT
            p.strokeWidth = h * 0.02f
            c.drawLine(w * 0.42f, h * 0.06f, w * 0.42f, h * 0.18f, p)
            c.drawLine(w * 0.36f, h * 0.1f, w * 0.36f, h * 0.2f, p)
            c.drawLine(w * 0.48f, h * 0.1f, w * 0.48f, h * 0.2f, p)
            p.strokeCap = Paint.Cap.BUTT
            label(c, "0점", w * 0.18f, h * 0.96f, h * 0.12f, Theme.TEXT_SUB, p)
            // 시계
            val cx = w * 0.79f
            val cy = h * 0.42f
            val r = minOf(h * 0.24f, w * 0.15f)
            p.style = Paint.Style.FILL
            p.color = Theme.CARD_LIGHT
            c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.12f
            p.color = Theme.GOLD
            c.drawCircle(cx, cy, r, p)
            p.strokeCap = Paint.Cap.ROUND
            p.color = 0xFFFFFFFF.toInt()
            c.drawLine(cx, cy, cx, cy - r * 0.65f, p)
            c.drawLine(cx, cy, cx + r * 0.45f, cy + r * 0.2f, p)
            p.strokeCap = Paint.Cap.BUTT
            label(c, "45초", cx, cy + r + h * 0.16f, h * 0.12f, Theme.TEXT, p)
        },
    )
}
