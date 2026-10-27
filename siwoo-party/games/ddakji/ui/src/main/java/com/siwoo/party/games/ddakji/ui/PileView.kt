package com.siwoo.party.games.ddakji.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.ddakji.GameConfig

/**
 * 양옆 플레이어 패널: 역할 알약(공격!/수비) · 큰 이름 · 쌓인 딱지 더미 · "N장".
 * 공격자는 금색 테두리, 이기면 왕관. 글자는 setter 에서만 만든다 (onDraw 할당 없음).
 */
class PileView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var name = ""
    private var color = Theme.GOLD
    private var count = GameConfig.DDAKJI_COUNT
    private var countText = COUNTS[GameConfig.DDAKJI_COUNT]
    private var attacking = false
    private var winner = false

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val crown = Path()

    private var pillY = 0f
    private var nameY = 0f
    private var pileTop = 0f
    private var pileBottom = 0f
    private var countY = 0f
    private var ds = 0f

    val shownCount: Int get() = count
    val isAttacking: Boolean get() = attacking

    fun setPlayer(playerName: String, playerColor: Int) {
        name = playerName
        color = playerColor
        contentDescription = playerName
        fitName()
        invalidate()
    }

    fun setCount(n: Int) {
        val v = n.coerceIn(0, COUNTS.size - 1)
        if (v == count) return
        count = v
        countText = COUNTS[v]
        invalidate()
    }

    fun setAttacking(v: Boolean) {
        if (attacking == v) return
        attacking = v
        invalidate()
    }

    fun setWinner(v: Boolean) {
        winner = v
        invalidate()
    }

    /** 더미 맨 위 딱지 가운데 (이 View 좌표) — 날아오는 딱지 도착점 */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stroke.strokeWidth = 2.5f * d
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        pillPaint.textSize = 14f * d
        countPaint.textSize = 22f * d
        pillY = rect.top + 20f * d
        nameY = pillY + 40f * d
        countY = rect.bottom - 14f * d
        ds = minOf(rect.width() * 0.36f, 42f * d)
        pileBottom = countY - 22f * d - ds * 0.45f
        pileTop = nameY + 10f * d
        fitName()
    }

    private fun fitName() {
        var s = 24f * d
        namePaint.textSize = s
        val maxW = rect.width() - 12f * d
        while (maxW > 0f && namePaint.measureText(name) > maxW && s > 10f * d) {
            s *= 0.92f
            namePaint.textSize = s
        }
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val cx = rect.centerX()
        val r = 14f * d
        fill.color = if (attacking) Theme.CARD_LIGHT else Theme.CARD
        c.drawRoundRect(rect, r, r, fill)
        if (attacking) {
            stroke.color = 0x55FFC93C
            stroke.strokeWidth = 6f * d
            c.drawRoundRect(rect, r, r, stroke)
            stroke.strokeWidth = 2.5f * d
            stroke.color = Theme.GOLD
        } else stroke.color = Theme.CARD_BORDER
        c.drawRoundRect(rect, r, r, stroke)

        if (winner) {
            fill.color = Theme.GOLD
            Ui.drawCrown(c, cx, pillY + 12f * d, 34f * d, crown, fill)
        } else {
            val label = if (attacking) ATTACK else DEFEND
            val pw = pillPaint.measureText(label) / 2f + 12f * d
            fill.color = if (attacking) Theme.GOLD else 0x33FFFFFF
            c.drawRoundRect(cx - pw, pillY - 12f * d, cx + pw, pillY + 12f * d, 12f * d, 12f * d, fill)
            pillPaint.color = if (attacking) Theme.INK else Theme.TEXT_SUB
            c.drawText(label, cx, pillY + pillPaint.textSize * 0.36f, pillPaint)
        }
        namePaint.color = color
        c.drawText(name, cx, nameY, namePaint)

        // 딱지 더미 (아래부터 비스듬히 쌓기, 많으면 MAX_SHOWN 장까지)
        val n = minOf(count, MAX_SHOWN)
        val step = minOf(ds * 0.55f, (pileBottom - pileTop - ds) / maxOf(1, MAX_SHOWN))
        for (k in 0 until n) {
            val y = pileBottom - k * step
            val x = cx + (if (k % 2 == 0) -1f else 1f) * ds * 0.06f
            DdakjiArt.draw(c, art, x, y, ds, 0.45f, color, if (k % 3 == 0) 4f else -3f, ds * 0.08f)
        }
        if (count == 0) {
            stroke.color = 0x55FFFFFF
            stroke.strokeWidth = 1.5f * d
            c.drawOval(cx - ds, pileBottom - ds * 0.45f, cx + ds, pileBottom + ds * 0.45f, stroke)
            stroke.strokeWidth = 2.5f * d
        }
        c.drawText(countText, cx, countY, countPaint)
    }

    companion object {
        private const val ATTACK = "공격!"
        private const val DEFEND = "수비"
        private const val MAX_SHOWN = 10
        private val COUNTS = Array(GameConfig.DDAKJI_COUNT * 2 + 1) { "${it}장" }
    }
}
