package com.siwoo.party.games.colorcode.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.colorcode.GameConfig

/**
 * 색 코드 입력판 (비밀 코드 정하기·추리 공용): 위 4칸 홈 + 아래 큰 색 단추 6개 (3개씩 두 줄, 색 이름 표시).
 * - 색 단추를 누르면 비어 있는 맨 앞 칸에 들어간다. 칸을 누르면 그 칸이 빠진다. backspace() 는 맨 뒤 칸을 뺀다.
 * - 중복 금지면 이미 쓴 색 단추는 흐려지고(빗금) 눌러도 안 들어간다 (onReject). 4칸이 다 차면 모든 단추가 흐려진다.
 * - 새로 들어간 말은 톡 튀어나온다 (진행 중일 때만 다시 그림).
 * clear() 로 넣은 색을 모두 지운다 (비밀 화면을 닫을 때).
 * onDraw 할당 없음.
 */
class CodeInputView(context: Context) : View(context) {

    /** 애니메이션 시계 (게임 시간 — 일시정지 때 멈춤) */
    fun interface Clock { fun now(): Long }

    var clock: Clock = Clock { SystemClock.uptimeMillis() }

    /** 칸마다 색 (-1 = 빈칸) */
    private val slots = IntArray(GameConfig.SLOTS) { -1 }

    var allowDuplicates = false
        set(v) { field = v; invalidate() }

    /** 입력 받기 (끄면 단추가 흐려지고 눌러도 무시) */
    var inputEnabled = true
        set(v) { if (field != v) { field = v; pressed = -1; invalidate() } }

    /** 칸 내용이 바뀔 때 */
    var onChange: (() -> Unit)? = null
    /** 색이 들어갔을 때 (효과음) */
    var onPut: ((Int) -> Unit)? = null
    /** 넣을 수 없을 때 (가득 참·이미 쓴 색) */
    var onReject: (() -> Unit)? = null

    /** 말이 튀어나오는 시간 (화면이 t() 를 곱해 넣는다) */
    var popMs = 260L

    private var popSlot = -1
    private var popStart = 0L

    // 배치
    private val tray = RectF()
    private val slotCx = FloatArray(GameConfig.SLOTS)
    private var slotCy = 0f
    private var slotR = 0f
    private val btnCx = FloatArray(GameConfig.COLORS)
    private val btnCy = FloatArray(GameConfig.COLORS)
    private var btnR = 0f
    private var labelDy = 0f
    private var showLabels = true

    /** 누르고 있는 대상: 0..5 = 색 단추, 10..13 = 칸, -1 = 없음 */
    private var pressed = -1

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        isClickable = true
        contentDescription = "색 코드 입력판"
    }

    /** 넣은 칸 수 */
    val filled: Int get() = slots.count { it >= 0 }

    val isComplete: Boolean get() = filled == GameConfig.SLOTS

    /** 칸 i 의 색 (-1 = 빈칸) */
    fun colorAt(i: Int): Int = slots[i]

    /** 다 채웠으면 코드 복사본, 아니면 null */
    fun code(): IntArray? = if (isComplete) slots.copyOf() else null

    /** 이미 쓴 색인지 */
    fun isUsed(color: Int): Boolean = slots.any { it == color }

    /** 이 색 단추를 지금 누를 수 있는지 */
    fun canPut(color: Int): Boolean = inputEnabled && !isComplete && (allowDuplicates || !isUsed(color))

    /** 색 넣기 (비어 있는 맨 앞 칸). 못 넣으면 false */
    fun put(color: Int): Boolean {
        if (!inputEnabled) return false
        if (!canPut(color)) {
            onReject?.invoke()
            return false
        }
        val i = slots.indexOf(-1)
        slots[i] = color
        popSlot = i
        popStart = clock.now()
        onPut?.invoke(color)
        onChange?.invoke()
        invalidate()
        return true
    }

    /** 칸 i 빼기 */
    fun removeAt(i: Int): Boolean {
        if (!inputEnabled || slots[i] < 0) return false
        slots[i] = -1
        if (popSlot == i) popSlot = -1
        onChange?.invoke()
        invalidate()
        return true
    }

    /** 맨 뒤에 채운 칸 빼기 */
    fun backspace(): Boolean {
        for (i in GameConfig.SLOTS - 1 downTo 0) if (slots[i] >= 0) return removeAt(i)
        return false
    }

    /** 코드를 한 번에 넣기 (무작위 채우기) */
    fun setCode(code: IntArray) {
        for (i in 0 until GameConfig.SLOTS) slots[i] = code[i]
        popSlot = -1
        onChange?.invoke()
        invalidate()
    }

    /** 모두 지우기 */
    fun clear() {
        slots.fill(-1)
        popSlot = -1
        pressed = -1
        onChange?.invoke()
        invalidate()
    }

    /** 색 단추 가운데 (테스트·연출용) */
    fun buttonCenter(color: Int, out: PointF) { out.set(btnCx[color], btnCy[color]) }

    /** 칸 가운데 */
    fun slotCenter(i: Int, out: PointF) { out.set(slotCx[i], slotCy) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout(w.toFloat(), h.toFloat())
    }

    private fun relayout(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        // 위 홈: 높이의 28% (폭에 맞춰 줄임)
        val trayH = minOf(h * 0.3f, w / 4.4f)
        val tw = minOf(w, trayH * 4.6f)
        tray.set((w - tw) / 2f, 0f, (w + tw) / 2f, trayH)
        slotR = trayH * 0.36f
        slotCy = tray.centerY()
        val step = tw / GameConfig.SLOTS
        for (i in 0 until GameConfig.SLOTS) slotCx[i] = tray.left + step * (i + 0.5f)
        // 아래 색 단추: 3개씩 두 줄
        val top = trayH + Theme.dpf(context, 8f)
        val rowH = (h - top) / 2f
        val colW = w / 3f
        val labelH = Theme.dpf(context, 13f)
        showLabels = rowH > Theme.dpf(context, 44f)
        val avail = if (showLabels) rowH - labelH else rowH
        btnR = minOf(avail * 0.44f, colW * 0.4f)
        labelPaint.textSize = Theme.dpf(context, 10.5f)
        for (k in 0 until GameConfig.COLORS) {
            val col = k % 3
            val row = k / 3
            btnCx[k] = colW * (col + 0.5f)
            btnCy[k] = top + rowH * row + avail / 2f
        }
        labelDy = btnR + Theme.dpf(context, 3f) + labelPaint.textSize * 0.9f
    }

    private fun hit(x: Float, y: Float): Int {
        for (k in 0 until GameConfig.COLORS) {
            val dx = x - btnCx[k]
            val dy = y - btnCy[k]
            val rr = btnR * 1.15f
            if (dx * dx + dy * dy <= rr * rr) return k
        }
        for (i in 0 until GameConfig.SLOTS) {
            val dx = x - slotCx[i]
            val dy = y - slotCy
            val rr = slotR * 1.2f
            if (dx * dx + dy * dy <= rr * rr) return 10 + i
        }
        return -1
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!inputEnabled || (context as? PartyGameActivity)?.gamePaused == true) return true
                pressed = hit(e.x, e.y)
                if (pressed >= 0) invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val p = pressed
                pressed = -1
                invalidate()
                if (p < 0 || hit(e.x, e.y) != p) return true
                if ((context as? PartyGameActivity)?.gamePaused == true) return true
                if (p >= 10) removeAt(p - 10) else put(p)
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        if (width <= 0) return
        var animating = false
        val now = clock.now()
        // 홈 판
        val rr = tray.height() * 0.5f
        fill.style = Paint.Style.FILL
        fill.color = 0x55000000
        c.drawRoundRect(tray.left, tray.top + Theme.dpf(context, 3f), tray.right, tray.bottom + Theme.dpf(context, 1f), rr, rr, fill)
        fill.color = ColorArt.BOARD
        c.drawRoundRect(tray.left, tray.top, tray.right, tray.bottom - Theme.dpf(context, 2f), rr, rr, fill)
        stroke.strokeWidth = Theme.dpf(context, 1.5f)
        stroke.color = Theme.GOLD_DARK
        c.drawRoundRect(tray.left, tray.top, tray.right, tray.bottom - Theme.dpf(context, 2f), rr, rr, stroke)
        val next = slots.indexOf(-1)
        for (i in 0 until GameConfig.SLOTS) {
            val col = slots[i]
            val cx = slotCx[i]
            val cy = slotCy - Theme.dpf(context, 1f)
            if (col < 0) {
                ColorArt.hole(c, cx, cy, slotR, fill)
                if (i == next && inputEnabled) {
                    stroke.color = Theme.GOLD
                    stroke.strokeWidth = Theme.dpf(context, 2.5f)
                    c.drawCircle(cx, cy, slotR * 0.92f, stroke)
                }
            } else {
                var r = slotR
                if (i == popSlot) {
                    val e = now - popStart
                    if (e < popMs) {
                        val k = e.toFloat() / popMs
                        r = slotR * (1.35f - 0.35f * (1f - (1f - k) * (1f - k)))
                        animating = true
                    }
                }
                if (pressed == 10 + i) r *= 0.9f
                ColorArt.peg(c, cx, cy, r, col, fill)
            }
        }
        // 색 단추
        for (k in 0 until GameConfig.COLORS) {
            val used = !allowDuplicates && isUsed(k)
            val can = inputEnabled && !used && !isComplete
            val r = if (pressed == k) btnR * 0.9f else btnR
            val cx = btnCx[k]
            val cy = btnCy[k]
            // 받침
            fill.style = Paint.Style.FILL
            fill.color = 0x40000000
            c.drawCircle(cx, cy + r * 0.12f, r * 1.1f, fill)
            fill.color = if (can) 0xFF2B3D8E.toInt() else 0xFF1D2A66.toInt()
            c.drawCircle(cx, cy, r * 1.08f, fill)
            ColorArt.peg(c, cx, cy, r * 0.94f, k, fill, if (can) 255 else 90)
            if (used && inputEnabled) {
                // 이미 쓴 색: 빗금
                stroke.color = 0xCCFFFFFF.toInt()
                stroke.strokeWidth = Theme.dpf(context, 2.5f)
                c.drawLine(cx - r * 0.6f, cy + r * 0.6f, cx + r * 0.6f, cy - r * 0.6f, stroke)
            }
            if (showLabels) {
                labelPaint.color = if (can) Theme.TEXT else Theme.GRAY
                c.drawText(ColorArt.NAMES[k], cx, cy + labelDy, labelPaint)
            }
        }
        if (animating) postInvalidateOnAnimation()
    }
}
