package com.siwoo.party.games.archery.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.archery.GameConfig
import com.siwoo.party.games.archery.Shot
import com.siwoo.party.games.archery.Target

/**
 * 위쪽 차례 표시: [n / 6 발] (사람 색 점) "가람 차례" + 안내 한 줄 | 지금 사람의 화살 6칸 (쏜 칸은 점수 색 + X/10/…/M).
 * 글자는 setter 에서만 만든다 (onDraw 할당 없음 — 칸 글자는 Target.LABELS 를 그대로 씀).
 */
class TurnHeaderView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)

    private var roundText = ""
    private var nameText = ""
    private var hintText = ""
    private var color = Theme.GOLD
    /** 칸마다 점수 색인 (−1 = 아직, 0 = M, 1..10, 11 = X) */
    private val chips = IntArray(GameConfig.ARROWS) { -1 }
    private val chipScore = IntArray(GameConfig.ARROWS)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT_SUB }
    private val chipText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    /** 지금 보이는 글자 (테스트용) */
    val shownName: String get() = nameText
    val shownHint: String get() = hintText

    fun setRound(arrow: Int) {
        roundText = (arrow + 1).toString() + " / " + GameConfig.ARROWS + " 발"
        invalidate()
    }

    fun setTurn(name: String, playerColor: Int) {
        nameText = name
        color = playerColor
        invalidate()
    }

    fun setHint(text: String) {
        if (hintText == text) return
        hintText = text
        invalidate()
    }

    /** 이 사람의 화살 기록으로 칸 채우기 */
    fun setShots(list: Array<Shot?>) {
        for (k in chips.indices) {
            val s = list.getOrNull(k)
            chips[k] = if (s == null) -1 else Target.index(s.score, s.isX)
            chipScore[k] = s?.score ?: 0
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pillText.textSize = 13f * d
        namePaint.textSize = minOf(18f * d, h * 0.4f)
        hintPaint.textSize = minOf(12f * d, h * 0.27f)
        ring.strokeWidth = 1.4f * d
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        p.color = 0x55101840
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, 14f * d, 14f * d, p)
        // 발 알약
        val pw = pillText.measureText(roundText) + 18f * d
        p.color = Theme.GOLD
        rect.set(8f * d, h / 2f - 12f * d, 8f * d + pw, h / 2f + 12f * d)
        c.drawRoundRect(rect, 12f * d, 12f * d, p)
        c.drawText(roundText, rect.centerX(), h / 2f + pillText.textSize * 0.36f, pillText)
        // 사람 색 점 + 이름 + 안내
        val dr = h * 0.2f
        val dx = rect.right + 10f * d + dr
        p.color = color
        c.drawCircle(dx, h / 2f, dr, p)
        ring.color = 0xAAFFFFFF.toInt()
        c.drawCircle(dx, h / 2f, dr, ring)
        val tx = dx + dr + 9f * d
        // 화살 칸 (오른쪽)
        val cr = minOf(10.5f * d, h * 0.28f)
        val gap = 4f * d
        val chipsW = GameConfig.ARROWS * (cr * 2f + gap) + 8f * d
        c.save()
        c.clipRect(0f, 0f, w - chipsW - 4f * d, h)
        c.drawText(nameText, tx, h * 0.47f, namePaint)
        c.drawText(hintText, tx, h * 0.47f + hintPaint.textSize * 1.25f, hintPaint)
        c.restore()
        chipText.textSize = cr * 0.95f
        var cx = w - chipsW + cr + 2f * d
        val cy = h / 2f
        for (k in chips.indices) {
            val idx = chips[k]
            if (idx < 0) {
                ring.color = 0x66FFFFFF
                c.drawCircle(cx, cy, cr * 0.85f, ring)
            } else {
                val sc = chipScore[k]
                p.color = ArcheryArt.scoreColor(sc)
                c.drawCircle(cx, cy, cr, p)
                ring.color = if (idx == 11) Theme.GOLD else 0x88FFFFFF.toInt()
                c.drawCircle(cx, cy, cr, ring)
                chipText.color = ArcheryArt.scoreInk(sc)
                c.drawText(Target.LABELS[idx], cx, cy + chipText.textSize * 0.36f, chipText)
            }
            cx += cr * 2f + gap
        }
    }
}
