package com.siwoo.party.games.stroop

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.stroop.ui.StroopActivity
import com.siwoo.party.games.stroop.ui.StroopBoardView

/** 색깔 함정: 모음집 등록 정보 */
object StroopModule : GameModule {
    override val id = "stroop"
    override val name = "색깔 함정"
    override val tagline = "글자 색일까, 글자 뜻일까? 헷갈리면 지는 거야!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "2분"
    override val color = 0xFFD94C8A.toInt()
    override val activityClass = StroopActivity::class.java

    private val oval = RectF()
    private val C = StroopBoardView.HUE_COLORS
    private const val SKY = 0xFF8FE3FF.toInt()

    /** 색 이름 글자를 다른 색으로 (어두운 테두리) */
    private fun word(c: Canvas, text: String, ink: Int, cx: Float, cy: Float, size: Float, p: Paint) {
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = size * 0.12f
        p.color = 0xFF050A24.toInt()
        c.drawText(text, cx, cy + size * 0.36f, p)
        p.style = Paint.Style.FILL
        p.color = ink
        c.drawText(text, cx, cy + size * 0.36f, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        // 네 색 조각 원
        oval.set(cx - r, cy - r, cx + r, cy + r)
        for (k in 0 until 4) {
            paint.color = C[k]
            canvas.drawArc(oval, -135f + k * 90f, 90f, true, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.1f
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, r * 0.95f, paint)
        // 가운데 어두운 알약 + 파란색으로 쓴 "빨강"
        paint.style = Paint.Style.FILL
        paint.color = 0xF00A1236.toInt()
        canvas.drawRoundRect(cx - r * 0.82f, cy - r * 0.42f, cx + r * 0.82f, cy + r * 0.42f, r * 0.3f, r * 0.3f, paint)
        word(canvas, Hue.RED.label, C[1], cx, cy, r * 0.62f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    /** 규칙 그림: 위 규칙 알약 + 카드 글자 + 버튼 4개 (선택: highlight 버튼 금색 테두리, wrong 버튼 X) */
    private fun scene(c: Canvas, w: Float, h: Float, p: Paint, rule: Rule?, highlight: Int, wrong: Int, lock: Boolean) {
        val pad = w * 0.08f
        // 규칙 알약
        if (rule != null) {
            p.style = Paint.Style.FILL
            p.color = if (rule == Rule.INK) Theme.GOLD else SKY
            c.drawRoundRect(w * 0.18f, h * 0.06f, w * 0.82f, h * 0.22f, h * 0.08f, h * 0.08f, p)
            p.color = Theme.INK
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.095f
            c.drawText(rule.label, w / 2f, h * 0.14f + p.textSize * 0.36f, p)
        }
        // 카드
        p.style = Paint.Style.FILL
        p.color = 0xFF0A1236.toInt()
        c.drawRoundRect(pad, h * 0.27f, w - pad, h * 0.6f, h * 0.06f, h * 0.06f, p)
        word(c, Hue.RED.label, C[1], w / 2f, h * 0.435f, h * 0.2f, p)
        // 버튼
        val gap = w * 0.03f
        val bw = (w - 2 * pad - 3 * gap) / 4f
        val top = h * 0.66f
        val bottom = h * 0.92f
        for (k in 0 until 4) {
            val l = pad + k * (bw + gap)
            p.style = Paint.Style.FILL
            p.color = Theme.shade(C[k], 0.55f)
            c.drawRoundRect(l, top + h * 0.02f, l + bw, bottom, h * 0.05f, h * 0.05f, p)
            p.color = C[k]
            c.drawRoundRect(l, top, l + bw, bottom - h * 0.02f, h * 0.05f, h * 0.05f, p)
            if (k == highlight) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = h * 0.025f
                p.color = Theme.GOLD
                c.drawRoundRect(l - h * 0.015f, top - h * 0.015f, l + bw + h * 0.015f, bottom + h * 0.015f, h * 0.06f, h * 0.06f, p)
            }
            if (k == wrong) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = h * 0.03f
                p.color = 0xFFFFFFFF.toInt()
                val cx = l + bw / 2f
                val cy = (top + bottom) / 2f
                val s = h * 0.07f
                c.drawLine(cx - s, cy - s, cx + s, cy + s, p)
                c.drawLine(cx + s, cy - s, cx - s, cy + s, p)
            }
        }
        if (lock) {
            p.style = Paint.Style.FILL
            p.color = 0xB3050A24.toInt()
            c.drawRoundRect(pad - 4f, top - 4f, w - pad + 4f, bottom + 4f, h * 0.05f, h * 0.05f, p)
            p.color = 0xFFFFFFFF.toInt()
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = h * 0.12f
            c.drawText("-1점 · 1초 잠금", w / 2f, (top + bottom) / 2f + p.textSize * 0.36f, p)
        }
        p.textAlign = Paint.Align.LEFT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("규칙을 먼저 보세요", "문제마다 맨 위에 규칙이 나와요.\n노란 알약 = 글자 색을 눌러라\n하늘색 알약 = 글자 뜻을 눌러라\n색 이름은 늘 다른 색으로 적혀 있어요.") { c, w, h, p ->
            scene(c, w, h, p, Rule.INK, -1, -1, false)
        },
        RuleCard("맞는 색을 가장 먼저!", "글자 색이면 칠해진 색(여기선 파랑),\n글자 뜻이면 적힌 색(여기선 빨강)을 눌러요.\n가장 먼저 맞힌 사람 +1점!") { c, w, h, p ->
            scene(c, w, h, p, Rule.INK, 1, -1, false)
        },
        RuleCard("틀리면 -1점, 1초 잠금", "틀리면 1점을 잃고 1초 동안 못 눌러요.\n6초 안에 아무도 못 맞히면 넘어가요.\n15문제 뒤 점수 순 (같으면 같은 순위).") { c, w, h, p ->
            scene(c, w, h, p, Rule.WORD, -1, 1, true)
        },
    )
}
