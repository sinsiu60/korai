package com.siwoo.party.games.drawing.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.drawing.GameConfig

/**
 * 그리기 도구 (한 View 에 칸 9개, 2열):
 *   [검정][빨강] / [파랑][초록] / [가늘게][굵게] / [지우개][되돌리기] / [전체 지우기]
 * 고른 색·굵기는 금색 테두리. 칸 위치는 크기가 바뀔 때만 계산 → onDraw 할당 없음.
 */
class ToolBarView(context: Context) : View(context) {

    fun interface Listener { fun onTool(tool: Int) }

    var listener: Listener? = null

    /** 고른 색 (0..3, ERASER) */
    var selectedColor = 0
        set(v) { field = v; invalidate() }
    var selectedWidth = 0
        set(v) { field = v; invalidate() }
    var canUndo = false
        set(v) { if (field != v) { field = v; invalidate() } }
    var canClear = false
        set(v) { if (field != v) { field = v; invalidate() } }
    var toolsEnabled = true
        set(v) { field = v; invalidate() }

    private val cells = Array(TOOL_COUNT) { RectF() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val path = Path()
    private var pressed = -1

    /** 칸 가운데 좌표 (테스트에서 누르기) */
    fun cell(tool: Int): RectF = cells[tool]

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val gap = Theme.dpf(context, 5f)
        val cw = (w - gap) / 2f
        val rows = 5
        val ch = (h - gap * (rows - 1)) / rows
        for (t in 0 until TOOL_COUNT) {
            val r = t / 2
            val c = t % 2
            val top = r * (ch + gap)
            if (t == CLEAR) cells[t].set(0f, top, w.toFloat(), top + ch)
            else cells[t].set(c * (cw + gap), top, c * (cw + gap) + cw, top + ch)
        }
        label.textSize = minOf(ch * 0.3f, Theme.dpf(context, 13f))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!toolsEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = hit(e.x, e.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val t = hit(e.x, e.y)
                val p = pressed
                pressed = -1
                invalidate()
                if (t >= 0 && t == p) listener?.onTool(t)
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }

    private fun hit(x: Float, y: Float): Int {
        for (t in 0 until TOOL_COUNT) if (cells[t].contains(x, y)) return t
        return -1
    }

    private fun isSelected(t: Int): Boolean = when (t) {
        in 0 until GameConfig.COLOR_COUNT -> selectedColor == t
        THIN -> selectedWidth == 0
        THICK -> selectedWidth == 1
        ERASER -> selectedColor == GameConfig.ERASER
        else -> false
    }

    private fun isDisabled(t: Int): Boolean = !toolsEnabled || (t == UNDO && !canUndo) || (t == CLEAR && !canClear)

    override fun onDraw(c: Canvas) {
        for (t in 0 until TOOL_COUNT) {
            val r = cells[t]
            if (r.width() <= 0f) continue
            val rr = r.height() * 0.22f
            val sel = isSelected(t)
            val dis = isDisabled(t)
            // 칸 (아래 두께 + 면)
            fill.color = Theme.Party.BG_DEEP
            c.drawRoundRect(r, rr, rr, fill)
            fill.color = when {
                t == pressed -> Theme.Party.NEON_PURPLE
                sel -> Theme.Party.CARD_LIGHT
                else -> Theme.Party.CARD
            }
            c.drawRoundRect(r.left, r.top, r.right, r.bottom - r.height() * 0.08f, rr, rr, fill)
            if (sel) {
                stroke.color = Theme.GOLD
                stroke.strokeWidth = r.height() * 0.07f
                c.drawRoundRect(r, rr, rr, stroke)
            }
            val cx = r.centerX()
            val cy = r.centerY() - r.height() * 0.04f
            val s = minOf(r.width(), r.height())
            val alpha = if (dis) 90 else 255
            when (t) {
                in 0 until GameConfig.COLOR_COUNT -> {
                    fill.color = DrawCanvasView.PEN_COLORS[t]
                    fill.alpha = alpha
                    c.drawCircle(cx, cy, s * 0.3f, fill)
                    stroke.color = Theme.TEXT
                    stroke.alpha = if (dis) 60 else 200
                    stroke.strokeWidth = s * 0.05f
                    c.drawCircle(cx, cy, s * 0.3f, stroke)
                    stroke.alpha = 255
                    fill.alpha = 255
                }
                THIN, THICK -> {
                    stroke.color = Theme.TEXT
                    stroke.alpha = alpha
                    stroke.strokeWidth = if (t == THIN) s * 0.07f else s * 0.2f
                    c.drawLine(cx - r.width() * 0.26f, cy + s * 0.1f, cx + r.width() * 0.26f, cy - s * 0.1f, stroke)
                    stroke.alpha = 255
                }
                ERASER -> drawEraser(c, cx, cy, s, alpha)
                UNDO -> drawUndo(c, cx, cy, s, alpha)
                CLEAR -> {
                    drawTrash(c, cx - label.measureText(CLEAR_TEXT) * 0.5f - s * 0.28f, cy, s, alpha)
                    label.alpha = alpha
                    c.drawText(CLEAR_TEXT, cx + s * 0.18f, cy + label.textSize * 0.36f, label)
                    label.alpha = 255
                }
            }
        }
    }

    private fun drawEraser(c: Canvas, cx: Float, cy: Float, s: Float, alpha: Int) {
        c.save()
        c.rotate(-30f, cx, cy)
        fill.color = 0xFFFF8FB1.toInt()
        fill.alpha = alpha
        c.drawRoundRect(cx - s * 0.3f, cy - s * 0.15f, cx + s * 0.1f, cy + s * 0.15f, s * 0.05f, s * 0.05f, fill)
        fill.color = 0xFF5B8DEF.toInt()
        fill.alpha = alpha
        c.drawRoundRect(cx + s * 0.05f, cy - s * 0.15f, cx + s * 0.32f, cy + s * 0.15f, s * 0.05f, s * 0.05f, fill)
        fill.alpha = 255
        c.restore()
    }

    private fun drawUndo(c: Canvas, cx: Float, cy: Float, s: Float, alpha: Int) {
        stroke.color = Theme.TEXT
        stroke.alpha = alpha
        stroke.strokeWidth = s * 0.09f
        c.drawArc(cx - s * 0.22f, cy - s * 0.2f, cx + s * 0.26f, cy + s * 0.28f, 200f, 250f, false, stroke)
        stroke.alpha = 255
        fill.color = Theme.TEXT
        fill.alpha = alpha
        path.reset()
        path.moveTo(cx - s * 0.36f, cy - s * 0.04f)
        path.lineTo(cx - s * 0.12f, cy - s * 0.06f)
        path.lineTo(cx - s * 0.26f, cy + s * 0.16f)
        path.close()
        c.drawPath(path, fill)
        fill.alpha = 255
    }

    private fun drawTrash(c: Canvas, cx: Float, cy: Float, s: Float, alpha: Int) {
        fill.color = Theme.RED
        fill.alpha = alpha
        c.drawRoundRect(cx - s * 0.16f, cy - s * 0.12f, cx + s * 0.16f, cy + s * 0.26f, s * 0.04f, s * 0.04f, fill)
        c.drawRoundRect(cx - s * 0.22f, cy - s * 0.22f, cx + s * 0.22f, cy - s * 0.15f, s * 0.03f, s * 0.03f, fill)
        c.drawRect(cx - s * 0.06f, cy - s * 0.27f, cx + s * 0.06f, cy - s * 0.21f, fill)
        fill.alpha = 255
    }

    companion object {
        // 칸 번호 (0..3 = 펜 색)
        const val THIN = 4
        const val THICK = 5
        const val ERASER = 6
        const val UNDO = 7
        const val CLEAR = 8
        const val TOOL_COUNT = 9
        const val CLEAR_TEXT = "전체 지우기"
    }
}
