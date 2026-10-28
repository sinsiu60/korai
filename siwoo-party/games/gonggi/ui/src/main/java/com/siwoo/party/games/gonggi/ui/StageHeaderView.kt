package com.siwoo.party.games.gonggi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.gonggi.GameConfig
import com.siwoo.party.games.gonggi.GonggiGame

/**
 * 위쪽 차례 표시: [목표 20점] "가람 차례" + 안내 한 줄 | 단계 칸 5개 (1단 2단 3단 4단 꺾기, 지금 단계 금색 + 던지기 점).
 * 글자는 setter 에서만 만든다 (onDraw 할당 없음).
 */
class StageHeaderView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)

    private var goalText = ""
    private var nameText = ""
    private var hintText = ""
    private var color = Theme.GOLD
    /** 지금 단계 (−1 = 표시 안 함), 던지기 번호 */
    private var stage = -1
    private var sub = 0

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
    val shownStage: Int get() = stage

    fun setGoal(target: Int) {
        goalText = "목표 " + target + "점"
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

    fun setStage(stageIndex: Int, subStep: Int) {
        if (stage == stageIndex && sub == subStep) return
        stage = stageIndex
        sub = subStep
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pillText.textSize = 13f * d
        namePaint.textSize = minOf(19f * d, h * 0.4f)
        hintPaint.textSize = minOf(12.5f * d, h * 0.27f)
        chipText.textSize = 12.5f * d
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        p.style = Paint.Style.FILL
        p.color = 0x55101840
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, 14f * d, 14f * d, p)
        // 목표 알약
        val pw = pillText.measureText(goalText) + 18f * d
        p.color = Theme.GOLD
        rect.set(8f * d, h / 2f - 12f * d, 8f * d + pw, h / 2f + 12f * d)
        c.drawRoundRect(rect, 12f * d, 12f * d, p)
        c.drawText(goalText, rect.centerX(), h / 2f + pillText.textSize * 0.36f, pillText)
        // 이름 앞 색 공깃돌
        val sx = rect.right + 18f * d
        GonggiArt.stone(c, sx, h / 2f, 9f * d, 0)
        p.color = color
        c.drawCircle(sx, h / 2f + 13f * d, 3f * d, p)
        val tx = sx + 16f * d
        // 단계 칸 (오른쪽)
        val chipW = CHIP_W * d
        val gap = 4f * d
        val trackW = GameConfig.STAGE_COUNT * chipW + (GameConfig.STAGE_COUNT - 1) * gap
        val x0 = w - trackW - 8f * d
        c.save()
        c.clipRect(0f, 0f, x0 - 6f * d, h)
        c.drawText(nameText, tx, h * 0.47f, namePaint)
        c.drawText(hintText, tx, h * 0.47f + hintPaint.textSize * 1.25f, hintPaint)
        c.restore()
        val top = h * 0.12f
        val bot = h * 0.62f
        for (k in 0 until GameConfig.STAGE_COUNT) {
            val l = x0 + k * (chipW + gap)
            rect.set(l, top, l + chipW, bot)
            val cur = k == stage
            val done = stage >= 0 && k < stage
            p.color = when {
                cur -> Theme.GOLD
                done -> color
                else -> 0x33FFFFFF
            }
            c.drawRoundRect(rect, 8f * d, 8f * d, p)
            if (cur) {
                ring.strokeWidth = 1.5f * d
                ring.color = Theme.GOLD_LIGHT
                c.drawRoundRect(rect, 8f * d, 8f * d, ring)
            }
            chipText.color = when {
                cur -> Theme.INK
                done -> Theme.textOn(color)
                else -> Theme.TEXT_SUB
            }
            c.drawText(GameConfig.STAGE_NAMES[k], rect.centerX(), rect.centerY() + chipText.textSize * 0.36f, chipText)
            // 던지기 점
            val steps = GonggiGame.stepsOf(k)
            val dr = 2.6f * d
            val dg = 3.2f * d
            var dx = rect.centerX() - (steps * dr * 2f + (steps - 1) * dg) / 2f + dr
            val dy = bot + (h - bot) / 2f
            for (s in 0 until steps) {
                val filled = done || (cur && s < sub)
                p.color = when {
                    filled -> Theme.GOLD
                    cur && s == sub -> Theme.TEXT
                    else -> 0x44FFFFFF
                }
                c.drawCircle(dx, dy, if (cur && s == sub) dr * 1.25f else dr, p)
                dx += dr * 2f + dg
            }
        }
    }

    companion object {
        private const val CHIP_W = 40f
    }
}
