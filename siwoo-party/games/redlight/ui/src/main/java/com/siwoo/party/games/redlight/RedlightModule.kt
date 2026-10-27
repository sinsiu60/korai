package com.siwoo.party.games.redlight

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.redlight.ui.RedlightActivity

/** 무궁화 꽃이 피었습니다: 모음집 등록 정보 */
object RedlightModule : GameModule {
    override val id = "redlight"
    override val name = "무궁화 꽃이 피었습니다"
    override val tagline = "술래가 돌아보기 전에! 꾹 누르고 전진, 들키면 처음부터"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "2분"
    override val color = 0xFFD9437A.toInt()
    override val activityClass = RedlightActivity::class.java

    private val path = Path()
    private val phraseChars = GameConfig.PHRASE.toCharArray()
    private const val PETAL = 0xFFF48FB1.toInt()
    private const val PETAL_DARK = 0xFFD9437A.toInt()
    private const val HEART = 0xFFB0103A.toInt()
    private const val SHIRT = 0xFFE85D3A.toInt()
    private const val SKIN = 0xFFFFD3A8.toInt()
    private const val HAIR = 0xFF3B2416.toInt()

    /** 무궁화 꽃 (cx, cy, 반지름 r) */
    private fun flower(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        p.style = Paint.Style.FILL
        for (k in 0 until 5) {
            val a = Math.toRadians(-90.0 + k * 72.0)
            val px = cx + (r * 0.5f * Math.cos(a)).toFloat()
            val py = cy + (r * 0.5f * Math.sin(a)).toFloat()
            p.color = PETAL_DARK
            c.drawCircle(px, py, r * 0.52f, p)
            p.color = PETAL
            c.drawCircle(px, py, r * 0.46f, p)
        }
        p.color = HEART
        c.drawCircle(cx, cy, r * 0.3f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.06f
        p.strokeCap = Paint.Cap.ROUND
        for (k in 0 until 5) {
            val a = Math.toRadians(-54.0 + k * 72.0)
            c.drawLine(cx, cy, cx + (r * 0.55f * Math.cos(a)).toFloat(), cy + (r * 0.55f * Math.sin(a)).toFloat(), p)
        }
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD
        c.drawCircle(cx, cy - r * 0.05f, r * 0.12f, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.05f, cy + r * 0.08f, r, paint)
        paint.color = 0xFF2A1F5C.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.94f, paint)
        flower(canvas, cx, cy, r * 0.74f, paint)
    }

    // ── 규칙 그림 ──

    /** 레인 하나 (top..top+lh): 출발선, 도착선, 말(prog 0..1), 오른쪽 끝 술래 */
    private fun lane(c: Canvas, w: Float, top: Float, lh: Float, prog: Float, color: Int, looking: Boolean, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0x8C050A26.toInt()
        c.drawRoundRect(w * 0.05f, top, w * 0.95f, top + lh, lh * 0.18f, lh * 0.18f, p)
        val ground = top + lh * 0.88f
        val sx = w * 0.12f
        val fx = w * 0.76f
        p.style = Paint.Style.STROKE
        p.strokeWidth = lh * 0.04f
        p.color = 0xCCFFFFFF.toInt()
        c.drawLine(sx, top + lh * 0.1f, sx, ground, p)
        p.color = Theme.GOLD
        p.strokeWidth = lh * 0.06f
        c.drawLine(fx, top + lh * 0.1f, fx, ground, p)
        // 말
        val r = lh * 0.2f
        val x = sx + (fx - sx) * prog
        p.style = Paint.Style.FILL
        p.color = color
        c.drawCircle(x, ground - r, r, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x + r * 0.35f, ground - r * 1.2f, r * 0.24f, p)
        // 술래
        tagger(c, w * 0.86f, ground, lh * 0.8f, looking, p)
    }

    private fun tagger(c: Canvas, cx: Float, ground: Float, h: Float, looking: Boolean, p: Paint) {
        val headR = h * 0.2f
        val headY = ground - h + headR
        p.style = Paint.Style.FILL
        if (looking) {
            p.color = 0x44FF4D5E
            c.drawCircle(cx, headY + headR * 0.4f, h * 0.55f, p)
        }
        path.reset()
        path.moveTo(cx - h * 0.14f, headY + headR * 0.85f)
        path.lineTo(cx + h * 0.14f, headY + headR * 0.85f)
        path.lineTo(cx + h * 0.27f, ground)
        path.lineTo(cx - h * 0.27f, ground)
        path.close()
        p.color = SHIRT
        c.drawPath(path, p)
        if (looking) {
            p.color = SKIN
            c.drawCircle(cx, headY, headR, p)
            p.color = HAIR
            c.drawArc(cx - headR, headY - headR, cx + headR, headY + headR * 0.1f, 180f, 180f, true, p)
            p.color = 0xFFFF2030.toInt()
            c.drawCircle(cx - headR * 0.42f, headY + headR * 0.22f, headR * 0.2f, p)
            c.drawCircle(cx + headR * 0.42f, headY + headR * 0.22f, headR * 0.2f, p)
        } else {
            p.color = HAIR
            c.drawCircle(cx, headY, headR, p)
            c.drawCircle(cx, headY - headR * 0.95f, headR * 0.38f, p)
        }
    }

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

    override val rules: List<RuleCard> = listOf(
        RuleCard("꾹 누르고 있으면 전진", "자기 색 구역을 누르고 있는 동안 내 말이 술래 쪽으로 걸어가요.\n구역마다 같은 레인·같은 술래가 있어요.") { c, w, h, p ->
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.02f
            p.color = Theme.PLAYER_COLORS[0]
            c.drawRoundRect(w * 0.03f, h * 0.06f, w * 0.97f, h * 0.94f, h * 0.06f, h * 0.06f, p)
            lane(c, w, h * 0.22f, h * 0.4f, 0.45f, Theme.PLAYER_COLORS[0], false, p)
            // 손가락
            p.style = Paint.Style.FILL
            p.color = 0xFFFFE0C2.toInt()
            c.drawRoundRect(w * 0.3f, h * 0.68f, w * 0.38f, h * 0.98f, w * 0.04f, w * 0.04f, p)
            label(c, "꾹", w * 0.48f, h * 0.84f, h * 0.12f, Theme.GOLD, p)
        },
        RuleCard("글자가 다 나오면 손 떼기!", "\"무궁화 꽃이 피었습니다\"가 한 글자씩 나와요. 속도는 매번 달라요!\n마지막 글자 뒤 술래가 휙 돌아보고 눈이 빨갛게 빛나요.") { c, w, h, p ->
            val slot = w * 0.085f
            p.style = Paint.Style.FILL
            p.textSize = h * 0.12f
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.color = Theme.RED
            for (k in 0 until GameConfig.PHRASE_LEN) c.drawText(phraseChars, k, 1, w * 0.5f + (k - 4.5f) * slot, h * 0.2f, p)
            p.typeface = null
            p.textAlign = Paint.Align.LEFT
            lane(c, w, h * 0.32f, h * 0.55f, 0.55f, Theme.PLAYER_COLORS[1], true, p)
        },
        RuleCard("들키면 출발점으로", "돌아본 뒤 0.3초가 지나도 누르고 있으면 출발점으로 돌아가요.\n먼저 술래에게 닿은 순서대로 순위! 90초가 끝나면 멀리 간 순서.") { c, w, h, p ->
            lane(c, w, h * 0.2f, h * 0.5f, 0f, Theme.PLAYER_COLORS[2], true, p)
            // 되돌아가는 화살표
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.035f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.RED
            val y = h * 0.82f
            c.drawLine(w * 0.66f, y, w * 0.16f, y, p)
            c.drawLine(w * 0.16f, y, w * 0.22f, y - h * 0.06f, p)
            c.drawLine(w * 0.16f, y, w * 0.22f, y + h * 0.06f, p)
            label(c, "0.3초", w * 0.42f, y - h * 0.05f, h * 0.09f, Theme.TEXT, p)
        },
    )
}
