package com.siwoo.party.games.reversi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui

/**
 * 판 옆 플레이어 패널 (왼쪽 = 1번 흑, 오른쪽 = 2번 백).
 * 위에서부터: 차례 알약("내 차례!"/"기다려요", 이기면 왕관·무승부면 "무승부") · 큰 돌(흑/백, 플레이어 색 고리) ·
 * [플레이어 색 점] 이름 · "흑 · 먼저"/"백 · 나중" · 돌 수(큰 숫자) · 안내 한 줄(차례일 때).
 * 글자는 값이 바뀔 때(setter)만 만든다.
 */
class RvSideView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var name = ""
    private var color = Theme.GOLD
    private var black = true
    private var current = false
    private var winner = false
    private var draw = false
    private var count = 2
    private var countText = COUNTS[2]
    private var hint = ""
    private var nameW = 0f

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Theme.GOLD }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT; color = Theme.GOLD_LIGHT }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val crown = Path()

    // ── 배치 (onSizeChanged) ──
    private var pillY = 0f
    private var discY = 0f
    private var discR = 0f
    private var nameY = 0f
    private var roleY = 0f
    private var countY = 0f
    private var hintY = 0f

    fun setPlayer(playerName: String, playerColor: Int, isBlack: Boolean) {
        name = playerName
        color = playerColor
        black = isBlack
        contentDescription = playerName
        if (width > 0) fitName()
        invalidate()
    }

    /** 판 위 내 돌 수 */
    fun setCount(n: Int) {
        val v = n.coerceIn(0, COUNTS.size - 1)
        if (v == count) return
        count = v
        countText = COUNTS[v]
        invalidate()
    }

    fun setCurrent(v: Boolean) {
        if (current == v) return
        current = v
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

    /** 안내 한 줄 (차례일 때만 보임). warn 이면 빨간 글씨 */
    fun setHint(text: String, warn: Boolean = false) {
        hintPaint.color = if (warn) Theme.RED else Theme.GOLD_LIGHT
        if (hint == text) { invalidate(); return }
        hint = text
        fitHint()
        invalidate()
    }

    val isCurrent: Boolean get() = current
    val shownHint: String get() = hint
    val shownCount: Int get() = count
    val isWinner: Boolean get() = winner

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        stroke.strokeWidth = 2.5f * d
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        discR = minOf(rect.width() * 0.22f, 27f * d)
        val block = 22f * d + 14f * d + 2f * discR + 30f * d + 22f * d + 46f * d + 30f * d
        val top = rect.top + maxOf(8f * d, (rect.height() - block) / 2f)
        pillY = top + 11f * d
        discY = pillY + 11f * d + 14f * d + discR
        nameY = discY + discR + 30f * d
        roleY = nameY + 22f * d
        countY = roleY + 46f * d
        hintY = countY + 30f * d
        subPaint.textSize = 12f * d
        countPaint.textSize = 34f * d
        unitPaint.textSize = 15f * d
        pillPaint.textSize = 13f * d
        fitName()
        fitHint()
    }

    private fun fitName() {
        var s = 18f * d
        namePaint.textSize = s
        val maxW = rect.width() - 30f * d
        while (namePaint.measureText(name) > maxW && s > 10f * d) {
            s *= 0.92f
            namePaint.textSize = s
        }
        nameW = namePaint.measureText(name)
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

        // 큰 돌 (흑/백) + 플레이어 색 고리
        stroke.color = color
        stroke.strokeWidth = 4f * d
        c.drawCircle(cx, discY, discR + 5f * d, stroke)
        stroke.strokeWidth = 2.5f * d
        RvArt.disc(c, art, cx, discY, discR, black)

        // [색 점] 이름 (가운데 정렬)
        val dotR = 5.5f * d
        val total = dotR * 2f + 6f * d + nameW
        val left = cx - total / 2f
        fill.color = color
        c.drawCircle(left + dotR, nameY - namePaint.textSize * 0.36f, dotR, fill)
        namePaint.color = color
        c.drawText(name, left + dotR * 2f + 6f * d, nameY, namePaint)
        c.drawText(if (black) ROLE_BLACK else ROLE_WHITE, cx, roleY, subPaint)

        // 판 위 내 돌 수 (뒤집힐 때마다 화면이 갱신)
        c.drawText(countText, cx + 10f * d, countY, countPaint)
        c.drawText(UNIT, cx + 13f * d, countY, unitPaint)

        if (current && hint.isNotEmpty()) c.drawText(hint, cx, hintY, hintPaint)
    }

    companion object {
        private const val TURN = "내 차례!"
        private const val WAIT = "기다려요"
        private const val DRAW = "무승부"
        private const val ROLE_BLACK = "흑 · 먼저 둬요"
        private const val ROLE_WHITE = "백 · 두 번째"
        private const val UNIT = "개"
        private val COUNTS = Array(65) { it.toString() }
    }
}
