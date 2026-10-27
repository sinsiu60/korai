package com.siwoo.party.games.codetile.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.codetile.GameConfig

/**
 * 숫자 고르기 패드: 0~11 을 타일 모양 버튼 12개(6×2)로. 고른 상대 타일과 같은 색 타일로 그린다.
 * - 고른 타일이 없으면 회색으로 꺼져 있고 안내 글자만
 * - 이미 모두에게 공개된 같은 색 숫자는 빗금(그 타일일 수 없음 — 공개 정보만 씀)
 * - 누르면 onKey(숫자), 고른 숫자는 금색 테두리로 들려 있음
 * onDraw 할당 없음.
 */
class GuessPadView(context: Context) : View(context) {

    var onKey: ((Int) -> Unit)? = null

    /** 패드가 켜져 있는지 (상대 타일을 골랐을 때) */
    var active = false
        private set
    private var black = false
    private val crossed = BooleanArray(GameConfig.NUMBERS)
    /** 고른 숫자 (-1 = 없음) */
    var selected = -1
        private set

    private val keys = Array(GameConfig.NUMBERS) { RectF() }
    private var downKey = -1
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private var hint = ""

    init {
        contentDescription = DESC
        isClickable = true
    }

    /** 고른 타일 색과 빗금 칠 숫자로 켜기 */
    fun open(targetBlack: Boolean, crossedNumbers: BooleanArray) {
        active = true
        black = targetBlack
        for (k in 0 until GameConfig.NUMBERS) crossed[k] = crossedNumbers[k]
        selected = -1
        invalidate()
    }

    /** 끄기 (안내 글자만) */
    fun close(hintText: String) {
        active = false
        selected = -1
        hint = hintText
        invalidate()
    }

    fun select(number: Int) {
        selected = number
        invalidate()
    }

    fun isCrossed(number: Int): Boolean = crossed[number]

    /** 숫자 버튼 가운데 (테스트용) */
    fun keyCenterX(k: Int): Float = keys[k].centerX()
    fun keyCenterY(k: Int): Float = keys[k].centerY()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cols = 6
        val rows = 2
        val gx = w * 0.025f
        var kw = (w - gx * (cols + 1)) / cols
        var kh = (h - gx * (rows + 1)) / rows
        // 타일 비율에 가깝게 (너무 길쭉하지 않게)
        kh = minOf(kh, kw / TileArt.ASPECT)
        kw = minOf(kw, kh * 0.95f)
        val totalW = cols * kw + (cols - 1) * gx
        val totalH = rows * kh + (rows - 1) * gx
        val ox = (w - totalW) / 2f
        val oy = (h - totalH) / 2f
        for (k in 0 until GameConfig.NUMBERS) {
            val c = k % cols
            val r = k / cols
            val l = ox + c * (kw + gx)
            val t = oy + r * (kh + gx)
            keys[k].set(l, t, l + kw, t + kh)
        }
        hintPaint.textSize = minOf(h * 0.18f, Theme.dpf(context, 13f))
        stroke.strokeWidth = Theme.dpf(context, 2f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled || !active) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> downKey = keyAt(e.x, e.y)
            MotionEvent.ACTION_UP -> {
                val k = keyAt(e.x, e.y)
                if (k >= 0 && k == downKey) onKey?.invoke(k)
                downKey = -1
            }
            MotionEvent.ACTION_CANCEL -> downKey = -1
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): Int {
        for (k in 0 until GameConfig.NUMBERS) {
            val r = keys[k]
            val m = r.width() * 0.08f
            if (x >= r.left - m && x <= r.right + m && y >= r.top - m && y <= r.bottom + m) return k
        }
        return -1
    }

    override fun onDraw(c: Canvas) {
        if (!active) {
            // 꺼진 패드: 흐린 빈 타일 + 안내
            for (k in 0 until GameConfig.NUMBERS) {
                val r = keys[k]
                fill.color = 0x22FFFFFF
                c.drawRoundRect(r, r.width() * 0.16f, r.width() * 0.16f, fill)
            }
            if (hint.isNotEmpty()) {
                fill.color = 0xCC0B1340.toInt()
                val hh = hintPaint.textSize * 1.9f
                val cy = height / 2f
                c.drawRoundRect(0f, cy - hh / 2f, width.toFloat(), cy + hh / 2f, hh / 2f, hh / 2f, fill)
                c.drawText(hint, width / 2f, cy - (hintPaint.descent() + hintPaint.ascent()) / 2f, hintPaint)
            }
            return
        }
        for (k in 0 until GameConfig.NUMBERS) {
            val r = keys[k]
            val sel = k == selected
            val lift = if (sel) r.height() * 0.1f else 0f
            val x = crossed[k]
            TileArt.draw(c, r.left, r.top - lift, r.width(), r.height(), black, k, if (x) 90 else 255)
            if (x) {
                stroke.color = Theme.RED
                c.drawLine(r.left + r.width() * 0.2f, r.bottom - r.height() * 0.25f, r.right - r.width() * 0.2f, r.top + r.height() * 0.2f, stroke)
            }
            if (sel) TileArt.outline(c, r.left, r.top - lift, r.width(), r.height(), Theme.GOLD, Theme.dpf(context, 3f))
        }
    }

    companion object {
        const val DESC = "숫자 고르기"
    }
}
