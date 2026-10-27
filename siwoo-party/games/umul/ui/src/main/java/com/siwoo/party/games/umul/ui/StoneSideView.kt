package com.siwoo.party.games.umul.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.umul.GameConfig

/**
 * 판 옆 플레이어 패널 (왼쪽 = 1번, 오른쪽 = 2번 — 판의 말도 같은 쪽 변에서 시작).
 * 위에서부터: 차례 알약("내 차례!"/"기다려요", 대결에서 이기면 왕관·무승부면 "무승부") · 큰 돌(플레이어 색) · 이름 ·
 * "이번 판 먼저"/"이번 판 나중" · 이긴 판 동그라미(●○) · 안내 한 줄. 글자는 값이 바뀔 때(setter)만 만든다.
 */
class StoneSideView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var name = ""
    private var color = Theme.GOLD
    private var starts = false
    private var current = false
    private var winner = false
    private var draw = false
    private var wins = 0
    private var hint = ""
    private var hintWarn = false

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val crown = Path()

    private var pillY = 0f
    private var stoneY = 0f
    private var stoneR = 0f
    private var nameY = 0f
    private var roleY = 0f
    private var dotsLabelY = 0f
    private var dotsY = 0f
    private var hintY = 0f

    fun setPlayer(playerName: String, playerColor: Int) {
        name = playerName
        color = playerColor
        contentDescription = playerName
        if (width > 0) fitName()
        invalidate()
    }

    /** 이번 판에 먼저 두는지 */
    fun setStarts(v: Boolean) {
        if (starts == v) return
        starts = v
        invalidate()
    }

    fun setCurrent(v: Boolean) {
        if (current == v) return
        current = v
        invalidate()
    }

    fun setWins(n: Int) {
        if (wins == n) return
        wins = n
        invalidate()
    }

    fun setWinner(v: Boolean) {
        winner = v
        invalidate()
    }

    fun setDraw(v: Boolean) {
        draw = v
        invalidate()
    }

    /** 안내 한 줄 (warn 이면 빨간 글씨) — 차례가 아니어도 보임 ("갇혔어요!" 등) */
    fun setHint(text: String, warn: Boolean = false) {
        if (hint == text && hintWarn == warn) return
        hint = text
        hintWarn = warn
        hintPaint.color = if (warn) Theme.RED else Theme.GOLD_LIGHT
        fitHint()
        invalidate()
    }

    val isCurrent: Boolean get() = current
    val shownHint: String get() = hint
    val shownWins: Int get() = wins

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        stroke.strokeWidth = 2.5f * d
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        stoneR = minOf(rect.width() * 0.24f, 30f * d)
        val block = 22f * d + 14f * d + 2f * stoneR + 30f * d + 22f * d + 28f * d + 22f * d + 30f * d
        val top = rect.top + maxOf(8f * d, (rect.height() - block) / 2f)
        pillY = top + 11f * d
        stoneY = pillY + 11f * d + 14f * d + stoneR
        nameY = stoneY + stoneR + 30f * d
        roleY = nameY + 22f * d
        dotsLabelY = roleY + 28f * d
        dotsY = dotsLabelY + 18f * d
        hintY = dotsY + 34f * d
        subPaint.textSize = 12f * d
        pillPaint.textSize = 13f * d
        fitName()
        fitHint()
    }

    private fun fitName() {
        var s = 19f * d
        namePaint.textSize = s
        val maxW = rect.width() - 12f * d
        while (namePaint.measureText(name) > maxW && s > 10f * d) {
            s *= 0.92f
            namePaint.textSize = s
        }
    }

    private fun fitHint() {
        var s = 12f * d
        hintPaint.textSize = s
        val maxW = rect.width() - 10f * d
        while (hint.isNotEmpty() && hintPaint.measureText(hint) > maxW && s > 8f * d) {
            s *= 0.92f
            hintPaint.textSize = s
        }
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val cx = rect.centerX()
        val r = 14f * d
        fill.color = if (current) Theme.CARD_LIGHT else Theme.CARD
        c.drawRoundRect(rect, r, r, fill)
        if (current) {
            stroke.color = 0x55FFC93C
            stroke.strokeWidth = 6f * d
            c.drawRoundRect(rect, r, r, stroke)
            stroke.strokeWidth = 2.5f * d
            stroke.color = Theme.GOLD
        } else {
            stroke.color = Theme.CARD_BORDER
        }
        c.drawRoundRect(rect, r, r, stroke)

        if (winner) {
            fill.color = Theme.GOLD
            Ui.drawCrown(c, cx, pillY + 10f * d, 30f * d, crown, fill)
        } else if (draw) {
            pillPaint.color = Theme.GOLD_LIGHT
            c.drawText(DRAW, cx, pillY + pillPaint.textSize * 0.36f, pillPaint)
        } else if (current) {
            val pw = pillPaint.measureText(TURN) / 2f + 10f * d
            fill.color = Theme.GOLD
            c.drawRoundRect(cx - pw, pillY - 11f * d, cx + pw, pillY + 11f * d, 11f * d, 11f * d, fill)
            pillPaint.color = Theme.INK
            c.drawText(TURN, cx, pillY + pillPaint.textSize * 0.36f, pillPaint)
        } else {
            pillPaint.color = Theme.TEXT_SUB
            c.drawText(WAIT, cx, pillY + pillPaint.textSize * 0.36f, pillPaint)
        }

        UmulArt.stone(c, art, cx, stoneY, stoneR, color)

        namePaint.color = color
        c.drawText(name, cx, nameY, namePaint)
        c.drawText(if (starts) ROLE_FIRST else ROLE_SECOND, cx, roleY, subPaint)

        // 이긴 판 동그라미 (●○)
        c.drawText(WINS_LABEL, cx, dotsLabelY, subPaint)
        val dr = 8f * d
        val gap = 24f * d
        val x0 = cx - gap * (GameConfig.WINS_NEEDED - 1) / 2f
        for (k in 0 until GameConfig.WINS_NEEDED) {
            val x = x0 + gap * k
            if (k < wins) {
                fill.color = Theme.GOLD_DARK
                c.drawCircle(x, dotsY + d, dr, fill)
                fill.color = Theme.GOLD
                c.drawCircle(x, dotsY, dr, fill)
                fill.color = 0x88FFFFFF.toInt()
                c.drawCircle(x - dr * 0.3f, dotsY - dr * 0.3f, dr * 0.3f, fill)
            } else {
                stroke.color = Theme.TEXT_SUB
                stroke.strokeWidth = 2f * d
                c.drawCircle(x, dotsY, dr - d, stroke)
                stroke.strokeWidth = 2.5f * d
            }
        }
        if (hint.isNotEmpty()) c.drawText(hint, cx, hintY, hintPaint)
    }

    companion object {
        private const val TURN = "내 차례!"
        private const val WAIT = "기다려요"
        private const val DRAW = "무승부"
        private const val ROLE_FIRST = "이번 판 먼저"
        private const val ROLE_SECOND = "이번 판 나중"
        private const val WINS_LABEL = "이긴 판"
    }
}
