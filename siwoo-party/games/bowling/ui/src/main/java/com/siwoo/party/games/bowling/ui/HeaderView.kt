package com.siwoo.party.games.bowling.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bowling.GameConfig

/**
 * 위쪽 차례 표시줄: [색 점 + 이름 차례] [N / M 프레임 · K구] [남은 핀 미니 삼각형] [안내 글].
 * 글자는 setter 에서만 만든다 (onDraw 할당 없음).
 */
class HeaderView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var title = ""
    private var titleShown = ""
    private var titleColor = Theme.GOLD
    private var sub = ""
    private var hint = ""
    private val standing = BooleanArray(GameConfig.PINS) { true }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val rect = RectF()

    /** 테스트용 */
    val shownTitle: String get() = title
    val shownSub: String get() = sub
    val shownHint: String get() = hint

    fun setTurn(text: String, color: Int) {
        title = text
        titleColor = color
        fit()
        invalidate()
    }

    fun setSub(text: String) {
        if (sub != text) { sub = text; invalidate() }
    }

    fun setHint(text: String) {
        if (hint != text) { hint = text; invalidate() }
    }

    /** 핀별 서 있음 (번호 순 0..9) */
    fun setPins(up: BooleanArray) {
        for (i in standing.indices) standing[i] = up[i]
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        titlePaint.textSize = 19f * d
        subPaint.textSize = 13f * d
        hintPaint.textSize = 12f * d
        fit()
    }

    private fun fit() {
        titlePaint.textSize = 19f * d
        titleShown = BowlingArt.fit(title, titlePaint, TITLE_DP * d)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        bg.color = Theme.CARD
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, 12f * d, 12f * d, bg)
        // 색 점 + 이름
        val cy = h / 2f
        fill.color = titleColor
        c.drawCircle(16f * d, cy, 7f * d, fill)
        fill.color = 0x66FFFFFF
        c.drawCircle(14f * d, cy - 2f * d, 2.5f * d, fill)
        titlePaint.color = Theme.TEXT
        var x = 30f * d
        c.drawText(titleShown, x, cy + 7f * d, titlePaint)
        x += titlePaint.measureText(titleShown) + 14f * d
        subPaint.color = Theme.GOLD
        c.drawText(sub, x, cy + 5f * d, subPaint)
        x += subPaint.measureText(sub) + 16f * d
        // 남은 핀 삼각형 (위에서 본 배치: 1번 왼쪽, 오른쪽으로 갈수록 줄이 늘어남)
        val s = h * 0.2f
        val pr = s * 0.36f
        for (i in 0 until GameConfig.PINS) {
            val row = ROW[i]
            val px = x + row * s * 0.87f + pr
            val py = cy + COL[i] * s
            fill.color = if (standing[i]) BowlingArt.PIN_WHITE else 0x40FFFFFF
            c.drawCircle(px, py, pr, fill)
            if (standing[i]) {
                fill.color = BowlingArt.PIN_RED
                c.drawCircle(px, py, pr * 0.35f, fill)
            }
        }
        // 안내 (오른쪽 끝)
        if (hint.isNotEmpty()) {
            hintPaint.color = Theme.TEXT_SUB
            c.drawText(hint, w - 12f * d, cy + 5f * d, hintPaint)
        }
    }

    companion object {
        private const val TITLE_DP = 150f
        private val ROW = intArrayOf(0, 1, 1, 2, 2, 2, 3, 3, 3, 3)
        private val COL = floatArrayOf(0f, -0.5f, 0.5f, -1f, 0f, 1f, -1.5f, -0.5f, 0.5f, 1.5f)
    }
}
