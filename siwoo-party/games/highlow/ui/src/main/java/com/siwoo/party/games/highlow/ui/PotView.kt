package com.siwoo.party.games.highlow.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 이번 차례 점수 판: 왼쪽 칩 더미(성공 한 번마다 한 개, 색이 단계마다 바뀜) + 오른쪽 큰 숫자 + "맞히면 n점" 안내.
 * - [setPot] 점수가 오르면 숫자가 통통 튄다
 * - [burst] 실패: 좌우로 흔들리며 칩이 흩어져 떨어지고 숫자가 빨간 0 이 된다
 * - [save] 저장: 칩이 위로 떠오르며 사라진다 (코인 비행은 화면 쪽 fx.coins)
 * 연출 중에만 postOnAnimation 으로 다시 그린다. onDraw 할당 없음 (글자는 setter 에서 만든다).
 */
class PotView(context: Context) : View(context) {

    private val d = context.resources.displayMetrics.density
    private val rect = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x660A1230 }
    private val bgStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x44FFC93C; strokeWidth = 1.2f * d }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB; textSize = 11.5f * d }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val numShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFF5A3A00.toInt() }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD_LIGHT }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB; textSize = 11.5f * d }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x44FFFFFF; strokeWidth = 1.5f * d }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 지금 점수 (테스트용) */
    var pot = 0L
        private set
    var chips = 0
        private set
    /** 큰 숫자 글자 (테스트용) */
    var numText = "0"
        private set
    var hint = ""
        private set
    private var unitW = 0f

    private var mode = NONE
    private var animStart = 0L
    private var animMs = 1L
    private var animT = 1f
    private var animating = false
    /** 흩어지는 칩 수 (burst·save 때 사라지는 칩) */
    private var goneChips = 0
    private val frame = Runnable { onFrame() }

    /** 점수·칩 개수·안내 글자 바꾸기. animate 면 숫자가 통통 */
    fun setPot(value: Long, streak: Int, nextValue: Long, animate: Boolean, popMs: Long) {
        pot = value
        chips = streak.coerceIn(0, MAX_CHIPS)
        numText = value.toString()
        hint = if (streak <= 0) "맞히면 " + nextValue + "점!" else "한 번 더 맞히면 " + nextValue + "점"
        numPaint.color = Theme.GOLD
        if (animate) start(POP, popMs) else { stopAnim(); mode = NONE }
        invalidate()
    }

    /** 실패: 흔들리며 칩이 흩어지고 숫자가 0 */
    fun burst(durationMs: Long) {
        goneChips = chips
        chips = 0
        pot = 0L
        numText = "0"
        hint = "저장 안 한 점수는 0점"
        numPaint.color = Theme.RED
        start(BURST, durationMs)
        invalidate()
    }

    /** 저장: 칩이 떠오르며 사라진다 (점수 숫자는 그대로 두고 연출 뒤 setPot 으로 비움) */
    fun save(durationMs: Long) {
        goneChips = chips
        chips = 0
        hint = "저장 완료!"
        start(SAVE, durationMs)
        invalidate()
    }

    /** 차례 사이 (아무것도 없는 판) */
    fun idle(text: String) {
        stopAnim()
        mode = NONE
        pot = 0L
        chips = 0
        numText = "0"
        hint = text
        numPaint.color = Theme.GOLD
        invalidate()
    }

    private fun start(m: Int, ms: Long) {
        mode = m
        animStart = SystemClock.uptimeMillis()
        animMs = ms.coerceAtLeast(1L)
        animT = 0f
        if (!animating) {
            animating = true
            postOnAnimation(frame)
        }
    }

    private fun stopAnim() {
        animating = false
        removeCallbacks(frame)
        animT = 1f
    }

    private fun onFrame() {
        if (!animating) return
        val t = (SystemClock.uptimeMillis() - animStart).toFloat() / animMs
        animT = t.coerceIn(0f, 1f)
        invalidate()
        if (t >= 1f) {
            animating = false
            if (mode != NONE) {
                mode = NONE
                goneChips = 0
            }
        } else postOnAnimation(frame)
    }

    val isAnimating: Boolean get() = animating

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnim()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        numPaint.textSize = minOf(h * 0.4f, w * 0.26f)
        numShadow.textSize = numPaint.textSize
        unitPaint.textSize = numPaint.textSize * 0.36f
        unitW = unitPaint.measureText(UNIT)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val shake = if (mode == BURST && animT < 1f) (Math.sin(animT * Math.PI * 9).toFloat() * (1f - animT) * 9f * d) else 0f
        c.save()
        c.translate(shake, 0f)
        val r = 12f * d
        rect.set(d, d, w - d, h - d)
        c.drawRoundRect(rect, r, r, bg)
        c.drawRoundRect(rect, r, r, bgStroke)
        c.drawText(TITLE, 10f * d, 8f * d + titlePaint.textSize, titlePaint)

        // 칩 더미 (왼쪽)
        val rx = minOf(w * 0.16f, h * 0.2f)
        val th = rx * 0.26f
        val cx = 12f * d + rx
        val base = h - 14f * d - rx * 0.4f - th
        if (chips == 0 && goneChips == 0) {
            rect.set(cx - rx, base - rx * 0.4f, cx + rx, base + rx * 0.4f)
            c.drawOval(rect, emptyPaint)
        }
        HighLowArt.chipStack(c, cx, base, rx, chips, chipPaint)
        if (goneChips > 0 && animT < 1f) {
            // 흩어지는(실패) 또는 떠오르는(저장) 칩
            for (k in 0 until goneChips) {
                val t = animT
                val fade = ((1f - t) * 255).toInt().coerceIn(0, 255)
                chipPaint.alpha = fade
                val x: Float
                val y: Float
                if (mode == BURST) {
                    val dir = if (k % 2 == 0) 1f else -1f
                    x = cx + dir * (k + 1) * rx * 0.55f * t
                    y = base - k * th + t * t * h * 0.6f
                } else {
                    x = cx
                    y = base - k * th - t * h * 0.5f
                }
                HighLowArt.chip(c, x, y, rx, th, k, chipPaint)
            }
            chipPaint.alpha = 255
        }

        // 큰 숫자 (오른쪽)
        val nx = cx + rx + (w - (cx + rx)) / 2f - unitW * 0.5f
        val ny = h * 0.62f
        var sc = 1f
        if (mode == POP && animT < 1f) sc = 1f + 0.4f * (1f - animT) * (1f - animT)
        c.save()
        c.scale(sc, sc, nx, ny - numPaint.textSize * 0.35f)
        c.drawText(numText, nx + 2f * d, ny + 2.5f * d, numShadow)
        c.drawText(numText, nx, ny, numPaint)
        val nw = numPaint.measureText(numText)
        c.drawText(UNIT, nx + nw / 2f + 3f * d, ny, unitPaint)
        c.restore()
        if (hint.isNotEmpty()) c.drawText(hint, nx + unitW * 0.5f, h - 10f * d, hintPaint)
        c.restore()
    }

    companion object {
        /** 그려 보이는 칩 최대 개수 */
        const val MAX_CHIPS = 9
        private const val NONE = 0
        private const val POP = 1
        private const val BURST = 2
        private const val SAVE = 3
        private const val TITLE = "이번 차례 점수"
        private const val UNIT = "점"
    }
}
