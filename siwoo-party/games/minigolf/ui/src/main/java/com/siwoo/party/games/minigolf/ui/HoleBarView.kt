package com.siwoo.party.games.minigolf.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.minigolf.GameConfig

/**
 * 코스 위 머리 줄: 왼쪽 "3번 홀 / 연못 · 파 3" | 가운데 지금 치는 사람 (색 동그라미 + "가람 차례") | 오른쪽 타수 구슬 6개
 * (친 타수 = 하양, 벌타 = 빨강, 남은 타 = 빈 고리). 글자는 setter 에서만 만든다 (onDraw 할당 없음).
 */
class HoleBarView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)

    private var holeText = ""
    private var infoText = ""
    private var playerText = ""
    private var playerColor = Theme.GOLD
    private var strokes = 0
    private var penalties = 0
    private var strokeText = STROKE_TEXT[0]

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT_SUB }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT; textAlign = Paint.Align.CENTER }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT_SUB; textAlign = Paint.Align.RIGHT }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    fun setHole(number: Int, name: String, par: Int, holes: Int) {
        holeText = number.toString() + "번 홀"
        infoText = name + " · 파 " + par + if (holes > 1) "  (" + number + "/" + holes + ")" else ""
        invalidate()
    }

    fun setPlayer(name: String, color: Int) {
        playerText = name + " 차례"
        playerColor = color
        invalidate()
    }

    fun setStrokes(n: Int, penalty: Int) {
        if (n == strokes && penalty == penalties) return
        strokes = n
        penalties = penalty
        strokeText = STROKE_TEXT[n.coerceIn(0, STROKE_TEXT.size - 1)]
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        holePaint.textSize = 17f * d
        infoPaint.textSize = 11.5f * d
        namePaint.textSize = 17f * d
        smallPaint.textSize = 11f * d
        ring.strokeWidth = 1.6f * d
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        bg.color = 0x55000000
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, 12f * d, 12f * d, bg)
        // 왼쪽: 홀
        val pad = 12f * d
        c.drawText(holeText, pad, h * 0.5f, holePaint)
        c.drawText(infoText, pad, h * 0.5f + 14f * d, infoPaint)
        // 가운데: 지금 치는 사람
        if (playerText.isNotEmpty()) {
            val tw = namePaint.measureText(playerText)
            val cx = w * 0.5f + 10f * d
            val base = h / 2f + namePaint.textSize * 0.35f
            dot.color = playerColor
            c.drawCircle(cx - tw / 2f - 12f * d, h / 2f, 7f * d, dot)
            ring.color = 0xCCFFFFFF.toInt()
            c.drawCircle(cx - tw / 2f - 12f * d, h / 2f, 7f * d, ring)
            c.drawText(playerText, cx, base, namePaint)
        }
        // 오른쪽: 타수 구슬
        val pr = 6f * d
        val gap = 4.5f * d
        val n = GameConfig.MAX_STROKES
        var x = w - pad - pr
        val y = h * 0.42f
        for (i in n - 1 downTo 0) {
            val shot = strokes - penalties
            when {
                i < shot -> { dot.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, y, pr, dot) }
                i < strokes -> { dot.color = Theme.RED; c.drawCircle(x, y, pr, dot) }
                else -> { ring.color = 0x66FFFFFF; c.drawCircle(x, y, pr - ring.strokeWidth / 2f, ring) }
            }
            x -= pr * 2f + gap
        }
        c.drawText(strokeText, w - pad, h * 0.42f + pr + 14f * d, smallPaint)
    }

    companion object {
        private val STROKE_TEXT = Array(GameConfig.MAX_STROKES + 2) { if (it == 0) "아직 안 침" else "${it}타" }
    }
}
