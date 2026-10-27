package com.siwoo.party.games.alkkagi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.alkkagi.GameConfig

/**
 * 판 옆 플레이어 패널 (왼쪽 = 1번, 오른쪽 = 2번 — 각자 알이 놓인 쪽).
 * 위에서부터: 차례 표시(금색 "내 차례!") · 큰 알(첫 글자) · 이름 · "남은 알 N개" · 작은 알 8칸(떨어진 알은 빈 고리).
 * 차례면 금색 테두리, 이기면 왕관. 글자는 값이 바뀔 때(setter)만 만든다.
 */
class SideView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    private var count = GameConfig.ALKKAGI_STONES
    private var countText = COUNTS[GameConfig.ALKKAGI_STONES]
    private var current = false
    private var winner = false
    private var hint = ""

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bigStone = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smallStone = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val crown = Path()

    // ── 배치 (onSizeChanged) ──
    private var bigR = 0f
    private var smallR = 0f
    private var bigY = 0f
    private var nameY = 0f
    private var countY = 0f
    private var gridTop = 0f
    private var cell = 0f
    private var hintY = 0f
    private var pillY = 0f

    fun setPlayer(playerName: String, playerColor: Int) {
        name = playerName
        initial = playerName.take(1)
        color = playerColor
        initPaint.color = Theme.textOn(playerColor)
        contentDescription = playerName
        if (width > 0) { makeShaders(); fitName() }
        invalidate()
    }

    fun setCount(n: Int) {
        if (n == count) return
        count = n.coerceIn(0, GameConfig.ALKKAGI_STONES)
        countText = COUNTS[count]
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

    /** 패널 아래 안내 한 줄 (차례일 때만 보임) */
    fun setHint(text: String) {
        if (hint == text) return
        hint = text
        fitHint()
        invalidate()
    }

    val isCurrent: Boolean get() = current
    val shownCount: Int get() = count

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        stroke.strokeWidth = 2.5f * d
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        val inner = rect.width()
        // 덩어리(차례 알약·큰 알·이름·남은 알·작은 알 8칸)를 아래 안내 줄 위 공간의 세로 가운데에
        bigR = minOf(inner * 0.3f, 28f * d)
        cell = minOf(inner / 4.3f, 23f * d)
        smallR = cell * 0.38f
        val block = 22f * d + 14f * d + 2f * bigR + 26f * d + 26f * d + 8f * d + 2f * cell
        val avail = rect.height() - 30f * d
        val top = rect.top + maxOf(8f * d, (avail - block) / 2f)
        pillY = top + 11f * d
        bigY = pillY + 11f * d + 14f * d + bigR
        initPaint.textSize = bigR * 0.95f
        nameY = bigY + bigR + 24f * d
        subPaint.textSize = 11f * d
        countPaint.textSize = 15f * d
        pillPaint.textSize = 13f * d
        countY = nameY + 26f * d
        gridTop = countY + 8f * d
        hintY = rect.bottom - 12f * d
        makeShaders()
        fitName()
        fitHint()
    }

    private fun makeShaders() {
        bigStone.shader = RadialGradient(-bigR * 0.35f, -bigR * 0.42f, bigR * 1.45f,
            intArrayOf(StoneArt.shade(color, 1.75f), StoneArt.shade(color, 1.12f), color, StoneArt.shade(color, 0.5f)),
            floatArrayOf(0f, 0.22f, 0.55f, 1f), Shader.TileMode.CLAMP)
        smallStone.shader = RadialGradient(-smallR * 0.35f, -smallR * 0.42f, smallR * 1.45f,
            intArrayOf(StoneArt.shade(color, 1.6f), color, StoneArt.shade(color, 0.5f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    private fun fitName() {
        var s = 19f * d
        namePaint.textSize = s
        val maxW = rect.width() - 10f * d
        while (namePaint.measureText(name) > maxW && s > 10f * d) {
            s *= 0.92f
            namePaint.textSize = s
        }
    }

    private fun fitHint() {
        var s = 11.5f * d
        hintPaint.textSize = s
        val maxW = rect.width() - 8f * d
        while (hint.isNotEmpty() && hintPaint.measureText(hint) > maxW && s > 8f * d) {
            s *= 0.92f
            hintPaint.textSize = s
        }
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val cx = rect.centerX()
        val r = 14f * d
        // 카드: 차례면 조금 밝게 + 금색 테두리(바깥 빛 한 겹)
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

        // 위: 차례 알약 또는 왕관
        if (winner) {
            fill.style = Paint.Style.FILL
            Ui.drawCrown(c, cx, pillY + 10f * d, 30f * d, crown, fill)
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

        // 큰 알 + 첫 글자
        c.save()
        c.translate(cx, bigY)
        c.drawCircle(bigR * 0.1f, bigR * 0.18f, bigR, shadow)
        c.drawCircle(0f, 0f, bigR, bigStone)
        c.restore()
        c.drawText(initial, cx, bigY + initPaint.textSize * 0.36f, initPaint)

        // 이름 · 남은 알
        namePaint.color = color
        c.drawText(name, cx, nameY, namePaint)
        c.drawText(LEFT_LABEL, cx - 12f * d, countY, subPaint)
        c.drawText(countText, cx + 25f * d, countY, countPaint)

        // 작은 알 8칸 (4 × 2): 남은 알은 광택 알, 떨어진 알은 빈 고리
        val cols = 4
        val left = cx - cell * cols / 2f
        stroke.strokeWidth = 1.3f * d
        stroke.color = 0x55FFFFFF
        for (k in 0 until GameConfig.ALKKAGI_STONES) {
            val x = left + (k % cols) * cell + cell / 2f
            val y = gridTop + (k / cols) * cell + cell / 2f
            if (k < count) {
                c.save()
                c.translate(x, y)
                c.drawCircle(0f, 0f, smallR, smallStone)
                c.restore()
            } else {
                c.drawCircle(x, y, smallR * 0.85f, stroke)
            }
        }
        stroke.strokeWidth = 2.5f * d
        if (current && hint.isNotEmpty()) c.drawText(hint, cx, hintY, hintPaint)
    }

    companion object {
        private const val TURN = "내 차례!"
        private const val WAIT = "기다려요"
        private const val LEFT_LABEL = "남은 알"
        private val COUNTS = Array(GameConfig.ALKKAGI_STONES + 1) { "${it}개" }
    }
}
