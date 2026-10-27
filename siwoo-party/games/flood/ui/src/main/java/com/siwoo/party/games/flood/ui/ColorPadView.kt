package com.siwoo.party.games.flood.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import kotlin.math.PI
import kotlin.math.sin

/**
 * 오른쪽 색 고르기 판: 큰 둥근 색 버튼 6개 (2열 × 3줄).
 * - 고를 수 있는 색: 반짝이는 입체 버튼 + 오른쪽 위 "+n"(흡수할 칸 수)
 * - 못 고르는 색(내 지금 색·다른 사람 지금 색): 회색 + ✕ + 그 색을 가진 사람 첫 글자 동그라미
 * - 누르고 있으면 미리보기(onPreview), 그 버튼 위에서 떼면 확정(onPick), 못 고르는 색을 누르면 onBlocked + 흔들림
 * 판 테두리는 지금 차례 사람 색. 글자는 값이 바뀔 때(setter)만 만든다. onDraw 할당 없음.
 */
class ColorPadView(context: Context) : View(context) {

    interface Listener {
        /** 지금 고를 수 있는지 (연출 중·일시정지면 false) */
        fun canPick(): Boolean
        /** 누르고 있는 색 (-1 = 뗌·벗어남) */
        fun onPreview(color: Int)
        fun onPick(color: Int)
        /** 못 고르는 색을 누름 */
        fun onBlocked(color: Int)
    }

    var listener: Listener? = null

    private val n = FloodArt.PALETTE.size
    private val enabled = BooleanArray(n)
    private val blocker = IntArray(n) { -1 }
    private val gain = IntArray(n)
    private val shakeStart = LongArray(n)
    private var shakeMs = 1L
    private var playerColors = IntArray(0)
    private var initials = emptyArray<String>()
    private var initColors = IntArray(0)
    private var turnColor = Theme.GOLD
    private var hint = ""
    private var hintWarn = false

    /** 누르고 있는 버튼 (-1 = 없음) */
    var pressed = -1
        private set

    // ── 배치 ──
    private val cx = FloatArray(n)
    private val cy = FloatArray(n)
    private var r = 0f
    private val rect = RectF()
    private var titleY = 0f
    private var hintY = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val gainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private var hintSize = 0f

    private var looping = false
    private val frame = Runnable { onFrame() }

    init {
        isClickable = true
        contentDescription = "색 고르기"
    }

    private fun now(): Long = SystemClock.uptimeMillis()

    /** 사람 색·이름 첫 글자 (못 고르는 색에 누구 색인지 표시) */
    fun setPlayers(colors: IntArray, names: Array<String>) {
        playerColors = colors
        initials = Array(names.size) { names[it].take(1) }
        initColors = IntArray(colors.size) { Theme.textOn(colors[it]) }
        invalidate()
    }

    /** 버튼 상태: 색마다 고를 수 있는지, 그 색을 가진 사람(-1 = 없음), 흡수할 칸 수 */
    fun setState(allowed: BooleanArray, holders: IntArray, gains: IntArray) {
        for (i in 0 until n) {
            enabled[i] = allowed[i]
            blocker[i] = holders[i]
            gain[i] = gains[i]
        }
        if (pressed >= 0 && !enabled[pressed]) pressed = -1
        invalidate()
    }

    fun isEnabled(color: Int): Boolean = enabled[color]
    fun blockerOf(color: Int): Int = blocker[color]
    fun gainOf(color: Int): Int = gain[color]

    /** 테두리 색 (지금 차례) */
    fun setTurnColor(color: Int) {
        if (turnColor == color) return
        turnColor = color
        invalidate()
    }

    /** 아래 안내 한 줄 (warn 이면 빨강) */
    fun setHint(text: String, warn: Boolean = false) {
        if (hint == text && hintWarn == warn) return
        hint = text
        hintWarn = warn
        fitHint()
        invalidate()
    }

    val hintText: String get() = hint

    fun setShakeMs(ms: Long) { shakeMs = ms.coerceAtLeast(1L) }

    /** 못 고르는 버튼 흔들기 */
    fun shake(color: Int) {
        shakeStart[color] = now()
        ensureRunning()
    }

    /** 버튼 가운데 (이 뷰 좌표) */
    fun buttonCenter(color: Int, out: PointF) { out.set(cx[color], cy[color]) }

    /** 손 떼기 (일시정지 등) */
    fun cancelPress() {
        if (pressed < 0) return
        pressed = -1
        invalidate()
        listener?.onPreview(-1)
    }

    private fun ensureRunning() {
        invalidate()
        if (looping) return
        looping = true
        postOnAnimation(frame)
    }

    private fun onFrame() {
        if (!looping) return
        invalidate()
        val t = now()
        var any = false
        for (i in 0 until n) if (t - shakeStart[i] < shakeMs) any = true
        if (any) postOnAnimation(frame) else looping = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = Theme.dpf(context, 1f)
        stroke.strokeWidth = d * 2.5f
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        titlePaint.textSize = minOf(d * 15f, w * 0.13f)
        titleY = rect.top + d * 8f + titlePaint.textSize
        hintSize = minOf(d * 12f, w * 0.1f)
        hintY = rect.bottom - d * 10f
        // 버튼 영역: 제목 아래 ~ 안내 위
        val top = titleY + d * 6f
        val bottom = hintY - hintSize - d * 6f
        val rowH = (bottom - top) / 3f
        val colW = (rect.width() - d * 8f) / 2f
        r = minOf(rowH * 0.36f, colW * 0.38f)
        for (i in 0 until n) {
            cx[i] = rect.left + d * 4f + colW * (i % 2 + 0.5f)
            cy[i] = top + rowH * (i / 2 + 0.5f)
        }
        gainPaint.textSize = r * 0.44f
        initPaint.textSize = r * 0.42f
        fitHint()
    }

    private fun fitHint() {
        if (width <= 0) return
        hintPaint.textSize = hintSize
        val maxW = rect.width() - Theme.dpf(context, 10f)
        val tw = hintPaint.measureText(hint)
        if (tw > maxW && tw > 0f) hintPaint.textSize = hintSize * maxW / tw
    }

    override fun onDraw(c: Canvas) {
        if (r <= 0f) return
        val t = now()
        val rr = Theme.dpf(context, 16f)
        fill.style = Paint.Style.FILL
        fill.color = Theme.CARD
        c.drawRoundRect(rect, rr, rr, fill)
        stroke.color = turnColor
        c.drawRoundRect(rect, rr, rr, stroke)
        c.drawText(TITLE, rect.centerX(), titleY, titlePaint)

        for (i in 0 until n) {
            var x = cx[i]
            val y = cy[i]
            val sd = t - shakeStart[i]
            if (sd >= 0L && sd < shakeMs) {
                val f = sd.toFloat() / shakeMs
                x += sin(f * PI.toFloat() * 6f) * r * 0.18f * (1f - f)
            }
            val on = enabled[i]
            val press = i == pressed
            val rad = if (press) r * 0.92f else r
            if (press) {
                // 누르고 있는 버튼: 흰 고리
                fill.color = 0xFFFFFFFF.toInt()
                fill.alpha = 90
                c.drawCircle(x, y, r * 1.22f, fill)
                fill.alpha = 255
            }
            FloodArt.button(c, fill, x, y, rad, i, on)
            if (on) {
                // 흡수할 칸 수
                val g = gain[i]
                val bx = x + r * 0.72f
                val by = y - r * 0.72f
                val bw = gainPaint.measureText(GAIN[g.coerceIn(0, GAIN.size - 1)]) / 2f + r * 0.14f
                val bh = r * 0.28f
                fill.color = Theme.INK
                c.drawRoundRect(bx - bw - r * 0.04f, by - bh - r * 0.04f, bx + bw + r * 0.04f, by + bh + r * 0.04f, bh, bh, fill)
                fill.color = if (g > 0) Theme.GOLD else Theme.GRAY
                c.drawRoundRect(bx - bw, by - bh, bx + bw, by + bh, bh, bh, fill)
                gainPaint.color = if (g > 0) Theme.INK else Theme.TEXT
                c.drawText(GAIN[g.coerceIn(0, GAIN.size - 1)], bx, by + gainPaint.textSize * 0.36f, gainPaint)
            } else {
                FloodArt.cross(c, fill, x, y, rad * 0.36f)
                val b = blocker[i]
                if (b >= 0 && b < playerColors.size) {
                    // 이 색을 가진 사람
                    val bx = x + r * 0.7f
                    val by = y + r * 0.66f
                    FloodArt.badge(c, fill, bx, by, r * 0.3f, playerColors[b])
                    initPaint.color = initColors[b]
                    c.drawText(initials[b], bx, by + initPaint.textSize * 0.36f, initPaint)
                }
            }
        }

        if (hint.isNotEmpty()) {
            hintPaint.color = if (hintWarn) Theme.RED else Theme.TEXT_SUB
            c.drawText(hint, rect.centerX(), hintY, hintPaint)
        }
    }

    // ── 터치: 누르는 동안 미리보기, 그 버튼 위에서 떼면 확정 ──

    private fun hit(x: Float, y: Float): Int {
        val lim = r * 1.2f
        for (i in 0 until n) {
            val dx = x - cx[i]
            val dy = y - cy[i]
            if (dx * dx + dy * dy <= lim * lim) return i
        }
        return -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (listener?.canPick() != true) return true
                val i = hit(e.x, e.y)
                if (i < 0) return true
                if (enabled[i]) {
                    pressed = i
                    invalidate()
                    listener?.onPreview(i)
                } else {
                    shake(i)
                    listener?.onBlocked(i)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressed >= 0 && hit(e.x, e.y) != pressed) cancelPress()
            }
            MotionEvent.ACTION_UP -> {
                val i = pressed
                if (i >= 0) {
                    pressed = -1
                    invalidate()
                    performClick()
                    listener?.onPreview(-1)
                    if (hit(e.x, e.y) == i && listener?.canPick() == true) listener?.onPick(i)
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelPress()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TITLE = "색 고르기"
        /** "+n" 글자 (그릴 때 만들지 않도록 미리) */
        private val GAIN = Array(197) { "+$it" }
    }
}
