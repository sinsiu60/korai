package com.siwoo.party.games.janggi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.janggi.GameConfig
import com.siwoo.party.games.janggi.Kind
import com.siwoo.party.games.janggi.Piece
import com.siwoo.party.games.janggi.Side

/**
 * 판 옆 진영 패널 (왼쪽 = 초, 오른쪽 = 한).
 * 위에서부터: 차례 알약("내 차례!"/"장군!"/"기다려요", 이기면 왕관·무승부면 "무승부") · 큰 궁 말(플레이어 색 고리) · 이름 ·
 * "초 · 먼저 둬요"/"한 · 덤 1.5" · 상차림 · 잡은 기물(작은 말 두 줄) · 점수(수 제한일 때) · 무르기 남은 횟수 · 안내 한 줄.
 * 아래쪽 BUTTON_SPACE_DP 는 화면이 겹쳐 놓는 [무르기][기권] 버튼 자리. 글자는 값이 바뀔 때(setter)만 만든다.
 */
class JanggiSideView(context: Context, val side: Int) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var name = ""
    private var color = Theme.GOLD
    private var current = false
    private var check = false
    private var winner = false
    private var draw = false
    private var undoEnabled = true
    private var undoLeft = GameConfig.UNDO_PER_PLAYER
    private var hint = ""
    private var formText = ""
    private var scoreText = ""
    private var leftText = ""
    /** 잡은 상대 기물 (게임 배열을 그대로 읽음) */
    private var capturedArr: IntArray? = null
    private var capturedN = 0

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
    private var bigY = 0f
    private var bigR = 0f
    private var nameY = 0f
    private var roleY = 0f
    private var formY = 0f
    private var capY = 0f
    private var capR = 0f
    private var scoreY = 0f
    private var undoY = 0f
    private var hintY = 0f
    private val roleText = if (side == Side.CHO) "초(楚) · 먼저 둬요" else "한(漢) · 덤 1.5점"

    fun setPlayer(playerName: String, playerColor: Int) {
        name = playerName
        color = playerColor
        contentDescription = playerName
        if (width > 0) fitName()
        invalidate()
    }

    fun setFormation(label: String) {
        formText = "상차림 $label"
        invalidate()
    }

    fun setCaptured(arr: IntArray, n: Int) {
        capturedArr = arr
        capturedN = n
        invalidate()
    }

    fun setUndo(enabled: Boolean, left: Int) {
        if (undoEnabled == enabled && undoLeft == left) return
        undoEnabled = enabled
        undoLeft = left
        invalidate()
    }

    /** 점수 줄 (수 제한일 때만, 빈 글자면 숨김) */
    fun setScore(score: String, left: String) {
        if (scoreText == score && leftText == left) return
        scoreText = score
        leftText = left
        invalidate()
    }

    fun setCurrent(v: Boolean) {
        if (current == v) return
        current = v
        invalidate()
    }

    fun setCheck(v: Boolean) {
        if (check == v) return
        check = v
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
    val isChecked: Boolean get() = check
    val shownHint: String get() = hint
    val shownUndoLeft: Int get() = undoLeft
    val isWinner: Boolean get() = winner

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        stroke.strokeWidth = 2.5f * d
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        bigR = minOf(rect.width() * 0.2f, 25f * d)
        capR = minOf((rect.width() - 12f * d) / 16.8f, 8.5f * d)
        // 덩어리를 버튼 자리 위 공간의 세로 가운데에
        val block = 22f * d + 12f * d + 2f * bigR + 24f * d + 18f * d + 17f * d + 10f * d + 4f * capR + 4f * d + 22f * d + 20f * d + 22f * d
        val avail = rect.height() - BUTTON_SPACE_DP * d
        val top = rect.top + maxOf(4f * d, (avail - block) / 2f)
        pillY = top + 11f * d
        bigY = pillY + 11f * d + 12f * d + bigR
        nameY = bigY + bigR + 24f * d
        roleY = nameY + 18f * d
        formY = roleY + 17f * d
        capY = formY + 10f * d + capR
        scoreY = capY + 3f * capR + 4f * d + 18f * d
        undoY = scoreY + 20f * d
        hintY = undoY + 22f * d
        subPaint.textSize = 11.5f * d
        pillPaint.textSize = 13f * d
        fitName()
        fitHint()
    }

    private fun fitName() {
        var s = 18f * d
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
        // 카드: 차례면 밝게 + 금색(장군이면 빨강) 테두리
        fill.color = if (current) Theme.CARD_LIGHT else Theme.CARD
        c.drawRoundRect(rect, r, r, fill)
        if (current) {
            stroke.color = if (check) 0x66FF4D5E else 0x55FFC93C
            stroke.strokeWidth = 6f * d
            c.drawRoundRect(rect, r, r, stroke)
            stroke.strokeWidth = 2.5f * d
            stroke.color = if (check) Theme.RED else Theme.GOLD
        } else {
            stroke.color = Theme.CARD_BORDER
        }
        c.drawRoundRect(rect, r, r, stroke)

        // 위: 왕관 / 무승부 / 장군 / 차례 알약
        if (winner) {
            fill.color = Theme.GOLD
            Ui.drawCrown(c, cx, pillY + 10f * d, 30f * d, crown, fill)
        } else if (draw) {
            pillPaint.color = Theme.GOLD_LIGHT
            c.drawText(DRAW, cx, pillY + pillPaint.textSize * 0.36f, pillPaint)
        } else if (current) {
            val label = if (check) CHECK else TURN
            val pw = pillPaint.measureText(label) / 2f + 10f * d
            fill.color = if (check) Theme.RED else Theme.GOLD
            c.drawRoundRect(cx - pw, pillY - 11f * d, cx + pw, pillY + 11f * d, 11f * d, 11f * d, fill)
            pillPaint.color = if (check) Theme.TEXT else Theme.INK
            c.drawText(label, cx, pillY + pillPaint.textSize * 0.36f, pillPaint)
        } else {
            pillPaint.color = Theme.TEXT_SUB
            c.drawText(WAIT, cx, pillY + pillPaint.textSize * 0.36f, pillPaint)
        }

        // 큰 궁 말 + 플레이어 색 고리
        stroke.color = color
        stroke.strokeWidth = 3.5f * d
        c.drawCircle(cx, bigY, bigR * 1.16f, stroke)
        stroke.strokeWidth = 2.5f * d
        JanggiArt.piece(c, art, cx, bigY, bigR, side, Kind.GENERAL)

        // 이름 · 진영 · 상차림
        namePaint.color = color
        c.drawText(name, cx, nameY, namePaint)
        subPaint.color = Theme.TEXT_SUB
        c.drawText(roleText, cx, roleY, subPaint)
        if (formText.isNotEmpty()) c.drawText(formText, cx, formY, subPaint)

        // 잡은 상대 기물: 바탕 상자 + 8개씩 두 줄 (줄마다 가운데 맞춤, 없으면 "잡은 기물 없음")
        val arr = capturedArr
        val step = capR * 2f + 1.5f * d
        val rowH = capR * 2f + 4f * d
        fill.color = 0x22FFFFFF
        c.drawRoundRect(cx - step * 4f - 2f * d, capY - capR - 3f * d, cx + step * 4f + 2f * d, capY + capR + rowH + 3f * d, 6f * d, 6f * d, fill)
        if (arr == null || capturedN == 0) {
            subPaint.color = 0x88B8C3F0.toInt()
            c.drawText(NONE, cx, capY + rowH / 2f + subPaint.textSize * 0.36f, subPaint)
            subPaint.color = Theme.TEXT_SUB
        } else {
            val n = minOf(capturedN, 16)
            for (k in 0 until n) {
                val code = arr[k]
                val inRow = if (k < 8) minOf(n, 8) else n - 8
                val x = cx + ((k % 8) - (inRow - 1) / 2f) * step
                val y = capY + (k / 8) * rowH
                JanggiArt.piece(c, art, x, y, capR, Piece.side(code), Piece.kind(code))
            }
        }

        // 점수 (수 제한일 때)
        if (scoreText.isNotEmpty()) {
            subPaint.color = Theme.GOLD_LIGHT
            c.drawText(scoreText, cx, scoreY, subPaint)
            subPaint.color = Theme.TEXT_SUB
        }

        // 무르기: "무르기" + 남은 횟수 금색 동그라미 (끔이면 "무르기 없음")
        if (!undoEnabled) {
            c.drawText(UNDO_OFF, cx, undoY, subPaint)
        } else {
            val n = GameConfig.UNDO_PER_PLAYER
            val dot = 5f * d
            val gap = 4f * d
            val labelW = subPaint.measureText(UNDO_LABEL)
            val total = labelW + 6f * d + n * dot * 2f + (n - 1) * gap
            val ux = cx - total / 2f
            c.drawText(UNDO_LABEL, ux + labelW / 2f, undoY, subPaint)
            val dy = undoY - subPaint.textSize * 0.36f
            for (k in 0 until n) {
                val x = ux + labelW + 6f * d + dot + k * (dot * 2f + gap)
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
        else if (leftText.isNotEmpty()) c.drawText(leftText, cx, hintY, subPaint)
    }

    companion object {
        /** 패널 아래 [무르기][기권] 버튼 자리 (dp) */
        const val BUTTON_SPACE_DP = 50f
        private const val TURN = "내 차례!"
        private const val CHECK = "장군!"
        private const val WAIT = "기다려요"
        private const val DRAW = "무승부"
        private const val NONE = "잡은 기물 없음"
        private const val UNDO_LABEL = "무르기"
        private const val UNDO_OFF = "무르기 없음"
    }
}
