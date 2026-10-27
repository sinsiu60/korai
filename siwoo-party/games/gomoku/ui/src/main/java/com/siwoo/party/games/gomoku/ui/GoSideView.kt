package com.siwoo.party.games.gomoku.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.gomoku.GameConfig

/**
 * 판 옆 플레이어 패널 (왼쪽 = 1번, 오른쪽 = 2번).
 * 위에서부터: 차례 알약("내 차례!"/"기다려요", 이기면 왕관·무승부면 "무승부") · 큰 돌(흑/백, 플레이어 색 고리) · 이름 ·
 * "흑 · 먼저"/"백" · 무르기 남은 횟수(금색 동그라미) · 안내 한 줄(차례일 때).
 * 아래쪽 BUTTON_SPACE_DP 는 화면이 겹쳐 놓는 [무르기] 버튼 자리. 글자는 값이 바뀔 때(setter)만 만든다.
 */
class GoSideView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var name = ""
    private var color = Theme.GOLD
    private var black = true
    private var current = false
    private var winner = false
    private var draw = false
    private var undoEnabled = true
    private var undoLeft = GameConfig.UNDO_PER_PLAYER
    private var hint = ""

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val crown = Path()

    // ── 배치 (onSizeChanged) ──
    private var pillY = 0f
    private var stoneY = 0f
    private var stoneR = 0f
    private var nameY = 0f
    private var roleY = 0f
    private var undoY = 0f
    private var hintY = 0f

    fun setPlayer(playerName: String, playerColor: Int, isBlack: Boolean) {
        name = playerName
        color = playerColor
        black = isBlack
        contentDescription = playerName
        if (width > 0) fitName()
        invalidate()
    }

    fun setUndo(enabled: Boolean, left: Int) {
        if (undoEnabled == enabled && undoLeft == left) return
        undoEnabled = enabled
        undoLeft = left
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

    /** 안내 한 줄 (차례일 때만 보임) */
    fun setHint(text: String) {
        if (hint == text) return
        hint = text
        fitHint()
        invalidate()
    }

    val isCurrent: Boolean get() = current
    val shownHint: String get() = hint
    val shownUndoLeft: Int get() = undoLeft

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        stroke.strokeWidth = 2.5f * d
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        stoneR = minOf(rect.width() * 0.26f, 30f * d)
        // 덩어리(알약·큰 돌·이름·흑백·무르기·안내)를 버튼 자리 위 공간의 세로 가운데에
        val block = 22f * d + 14f * d + 2f * stoneR + 28f * d + 22f * d + 28f * d + 30f * d
        val avail = rect.height() - BUTTON_SPACE_DP * d
        val top = rect.top + maxOf(6f * d, (avail - block) / 2f)
        pillY = top + 11f * d
        stoneY = pillY + 11f * d + 14f * d + stoneR
        nameY = stoneY + stoneR + 28f * d
        roleY = nameY + 22f * d
        undoY = roleY + 26f * d
        hintY = undoY + 30f * d
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
        // 카드: 차례면 조금 밝게 + 금색 테두리
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

        // 위: 왕관 / 무승부 / 차례 알약
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

        // 큰 돌 + 플레이어 색 고리
        stroke.color = color
        stroke.strokeWidth = 3.5f * d
        c.drawCircle(cx, stoneY, stoneR + 5f * d, stroke)
        stroke.strokeWidth = 2.5f * d
        GoArt.stone(c, art, cx, stoneY, stoneR, black)

        // 이름 · 흑/백
        namePaint.color = color
        c.drawText(name, cx, nameY, namePaint)
        c.drawText(if (black) ROLE_BLACK else ROLE_WHITE, cx, roleY, subPaint)

        // 무르기: "무르기" + 남은 횟수 금색 동그라미 (끔이면 "무르기 없음")
        if (!undoEnabled) {
            c.drawText(UNDO_OFF, cx, undoY, subPaint)
        } else {
            val n = GameConfig.UNDO_PER_PLAYER
            val dot = 5f * d
            val gap = 4f * d
            val labelW = subPaint.measureText(UNDO_LABEL)
            val total = labelW + 6f * d + n * dot * 2f + (n - 1) * gap
            val x0 = cx - total / 2f
            c.drawText(UNDO_LABEL, x0 + labelW / 2f, undoY, subPaint)
            val dy = undoY - subPaint.textSize * 0.36f
            for (k in 0 until n) {
                val x = x0 + labelW + 6f * d + dot + k * (dot * 2f + gap)
                if (k < undoLeft) {
                    fill.color = Theme.GOLD
                    c.drawCircle(x, dy, dot, fill)
                } else {
                    stroke.color = 0x88FFFFFF.toInt()
                    stroke.strokeWidth = 1.5f * d
                    c.drawCircle(x, dy, dot * 0.85f, stroke)
                    stroke.strokeWidth = 2.5f * d
                }
            }
        }
        if (current && hint.isNotEmpty()) c.drawText(hint, cx, hintY, hintPaint)
    }

    companion object {
        /** 패널 아래 [무르기] 버튼 자리 (dp) */
        const val BUTTON_SPACE_DP = 58f
        private const val TURN = "내 차례!"
        private const val WAIT = "기다려요"
        private const val DRAW = "무승부"
        private const val ROLE_BLACK = "흑 · 먼저 둬요"
        private const val ROLE_WHITE = "백"
        private const val UNDO_LABEL = "무르기"
        private const val UNDO_OFF = "무르기 없음"
    }
}
