package com.siwoo.party.common.numpad

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 큰 숫자 입력 패드 (숫자 야구·업다운용). 화면 하나(View)에 Canvas 로 그린다.
 *  위: 입력값 칸 (자릿수만큼 네모 칸, 다음에 넣을 칸은 금색 테두리) + 안내/오류 한 줄 ("1~100 사이" 등)
 *  아래: 3×4 입체 버튼 (1~9, ⌫ 지우기, 0, ✓ 확인) — 누르면 면이 내려가고, 쓸 수 없는 키는 회색
 * 규칙(자릿수·첫 자리 0·같은 숫자 금지·범위)은 NumInput (common/logic). ✓ 는 규칙에 맞을 때만 켜진다.
 * 문자열·Shader·Path 는 값·크기가 바뀔 때만 만든다 → onDraw 할당 없음.
 *
 * 예) 숫자 야구: pad.configure(3, distinctDigits = true, exactDigits = true); pad.onConfirm = { s -> guess(s) }
 *     업다운:   pad.configure(3, allowLeadingZero = false, min = 1, max = 100); 매 차례 pad.setRange(lo, hi)
 */
class NumPadView(context: Context) : View(context) {

    /** 입력 규칙과 값 */
    val input = NumInput(3)

    /** 값이 바뀔 때 (숫자 넣기·지우기·clear·value 설정) */
    var onChange: ((String) -> Unit)? = null

    /** ✓ 를 눌렀을 때 (규칙에 맞을 때만). 값은 그대로 남는다 — 필요하면 clear() */
    var onConfirm: ((String) -> Unit)? = null

    /** 키가 눌려 받아들여졌을 때 (효과음용: 0~9, KEY_BACK, KEY_OK) */
    var onKey: ((Int) -> Unit)? = null

    /** 위쪽 입력값 칸·안내 줄을 그릴지 (false 면 버튼만 꽉 차게) */
    var showDisplay = true
        set(v) { field = v; relayout(); invalidate() }

    /** 지금 넣은 값 (글자). 설정하면 규칙에 맞는 앞부분까지만 들어감 */
    var value: String
        get() = input.text
        set(v) { input.set(v); changed() }

    /** 지금 넣은 값 (숫자, 비어 있으면 null) */
    val number: Int? get() = input.value

    val canConfirm: Boolean get() = input.canConfirm

    /** 지금 안내 줄에 보이는 글자 */
    var message = ""
        private set

    /** 안내 줄이 오류(빨강)인지 */
    var messageIsError = false
        private set

    private val keyOn = BooleanArray(12)
    private val keys = Array(12) { RectF() }
    private val faceOn = arrayOfNulls<Shader>(12)
    private val faceOff = arrayOfNulls<Shader>(12)
    private val baseOn = IntArray(12)
    private val baseOff = IntArray(12)
    private var pressed = -1
    private val slots = Array(9) { RectF() }
    private var slotTexts = Array(9) { "" }
    private val msgRect = RectF()
    private var depth = 0f
    private var radius = 0f
    private var digitOff = 0f
    private var slotOff = 0f
    private var msgOff = 0f
    private val tmp = RectF()

    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val base = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Theme.TEXT
    }
    private val keyText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT
        setShadowLayer(3f, 0f, 2f, 0x66000000)
    }
    private val slotText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val msgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val backPath = Path()
    private val okPath = Path()

    init {
        isClickable = true
        contentDescription = "숫자 입력 패드"
        changed(notify = false)
    }

    /** 규칙 정하기 (넣은 값은 지움) */
    fun configure(
        maxDigits: Int,
        allowLeadingZero: Boolean = true,
        distinctDigits: Boolean = false,
        exactDigits: Boolean = false,
        min: Int? = null,
        max: Int? = null,
    ) {
        input.configure(maxDigits, allowLeadingZero, distinctDigits, exactDigits, min, max)
        relayout()
        changed()
    }

    /** 범위만 바꾸기 (업다운). 넣은 값은 그대로 */
    fun setRange(min: Int?, max: Int?) {
        input.setRange(min, max)
        changed(notify = false)
    }

    fun clear() {
        input.clear()
        changed()
    }

    /** 키 누르기 (0~9, KEY_BACK, KEY_OK). 받아들여졌으면 true — 테스트·키보드용 */
    fun press(key: Int): Boolean {
        if (!isEnabled || !isKeyEnabled(key)) return false
        when (key) {
            KEY_BACK -> { input.backspace(); changed() }
            KEY_OK -> {}
            else -> { input.type(key); changed() }
        }
        onKey?.invoke(key)
        if (key == KEY_OK) onConfirm?.invoke(input.text)
        return true
    }

    /** 지금 이 키를 쓸 수 있는지 */
    fun isKeyEnabled(key: Int): Boolean = when (key) {
        KEY_BACK -> !input.isEmpty
        KEY_OK -> input.canConfirm
        in 0..9 -> input.canType(key)
        else -> false
    }

    /** 키 자리 (테스트용, View 좌표) */
    fun keyRect(key: Int, out: RectF): Boolean {
        val k = GRID.indexOf(key)
        if (k < 0) return false
        out.set(keys[k])
        return true
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        pressed = -1
        invalidate()
    }

    /** 값·규칙이 바뀐 뒤: 키 상태·칸 글자·안내 문구를 미리 만든다 (onDraw 에서 만들지 않게) */
    private fun changed(notify: Boolean = true) {
        for (k in 0 until 12) keyOn[k] = isKeyEnabled(GRID[k])
        val t = input.text
        for (i in 0 until 9) slotTexts[i] = if (i < t.length) DIGITS[t[i] - '0'] else ""
        val err = input.error
        message = when {
            err == null -> OK_TEXT
            else -> err
        }
        messageIsError = err != null && !input.isEmpty
        fitMessage()
        invalidate()
        if (notify) onChange?.invoke(t)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val gap = Theme.dpf(context, 8f)
        depth = Theme.dpf(context, 4f)
        var top = 0f
        if (showDisplay) {
            val dh = (h * 0.2f).coerceAtMost(Theme.dpf(context, 64f))
            val n = input.maxDigits
            val s = minOf(dh, (w - gap * (n - 1)) / n)
            val total = s * n + gap * (n - 1)
            var x = (w - total) / 2f
            for (i in 0 until 9) {
                if (i < n) { slots[i].set(x, 0f, x + s, dh); x += s + gap } else slots[i].setEmpty()
            }
            slotText.textSize = dh * 0.66f
            slotOff = textCenter(slotText)
            val mh = (h * 0.09f).coerceAtMost(Theme.dpf(context, 28f))
            msgRect.set(0f, dh + gap * 0.3f, w, dh + gap * 0.3f + mh)
            msgPaint.textSize = mh * 0.62f
            fitMessage()
            top = msgRect.bottom + gap * 0.5f
        }
        // 3×4 버튼 (너무 넓으면 가운데로 모음)
        val gridH = h - top
        var kh = (gridH - gap * 3 - depth) / 4f
        var kw = (w - gap * 2) / 3f
        if (kw > kh * 1.9f) kw = kh * 1.9f
        if (kh > kw * 1.1f) kh = kw * 1.1f
        val gw = kw * 3 + gap * 2
        val left = (w - gw) / 2f
        val gtop = top + (gridH - (kh * 4 + gap * 3 + depth)) / 2f
        radius = minOf(kw, kh) * 0.22f
        for (k in 0 until 12) {
            val col = k % 3
            val row = k / 3
            val x = left + col * (kw + gap)
            val y = gtop + row * (kh + gap)
            keys[k].set(x, y, x + kw, y + kh + depth)
            val c = keyColor(GRID[k])
            faceOn[k] = LinearGradient(0f, y, 0f, y + kh, Theme.shade(c, 1.35f), c, Shader.TileMode.CLAMP)
            faceOff[k] = LinearGradient(0f, y, 0f, y + kh, Theme.shade(Theme.GRAY, 1.2f), Theme.GRAY, Shader.TileMode.CLAMP)
            baseOn[k] = Theme.shade(c, 0.55f)
            baseOff[k] = Theme.shade(Theme.GRAY, 0.6f)
        }
        keyText.textSize = kh * 0.56f
        digitOff = textCenter(keyText)
        icon.strokeWidth = kh * 0.07f
        // ⌫ 아이콘 (가운데 기준)
        val s = kh * 0.32f
        backPath.reset()
        backPath.moveTo(-s * 1.15f, 0f)
        backPath.lineTo(-s * 0.5f, -s * 0.7f)
        backPath.lineTo(s * 1.05f, -s * 0.7f)
        backPath.lineTo(s * 1.05f, s * 0.7f)
        backPath.lineTo(-s * 0.5f, s * 0.7f)
        backPath.close()
        val xs = s * 0.32f
        backPath.moveTo(s * 0.25f - xs, -xs)
        backPath.lineTo(s * 0.25f + xs, xs)
        backPath.moveTo(s * 0.25f + xs, -xs)
        backPath.lineTo(s * 0.25f - xs, xs)
        // ✓ 아이콘
        okPath.reset()
        okPath.moveTo(-s * 0.85f, 0f)
        okPath.lineTo(-s * 0.25f, s * 0.6f)
        okPath.lineTo(s * 0.9f, -s * 0.65f)
    }

    private fun keyColor(key: Int): Int = when (key) {
        KEY_BACK -> BACK_COLOR
        KEY_OK -> Theme.GREEN_DARK
        else -> DIGIT_COLOR
    }

    /** 글자를 세로 가운데에 놓는 기준선 보정 (크기 바뀔 때만) */
    private fun textCenter(p: Paint): Float {
        val fm = p.fontMetrics
        return -(fm.ascent + fm.descent) / 2f
    }

    private fun fitMessage() {
        val w = msgRect.width()
        if (w <= 0f) return
        val full = msgRect.height() * 0.62f
        msgPaint.textSize = full
        while (msgPaint.measureText(message) > w * 0.96f && msgPaint.textSize > Theme.dpf(context, 8f)) msgPaint.textSize *= 0.92f
        msgOff = textCenter(msgPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val k = hit(e.x, e.y)
                pressed = if (k >= 0 && keyOn[k]) k else -1
                if (pressed >= 0) parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (pressed >= 0 && hit(e.x, e.y) != pressed) { pressed = -1; invalidate() }
            MotionEvent.ACTION_UP -> {
                val k = pressed
                pressed = -1
                invalidate()
                if (k >= 0 && hit(e.x, e.y) == k) press(GRID[k])
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }

    private fun hit(x: Float, y: Float): Int {
        for (k in 0 until 12) if (keys[k].contains(x, y)) return k
        return -1
    }

    override fun onDraw(c: Canvas) {
        if (width <= 0) return
        if (showDisplay) drawDisplay(c)
        val on = isEnabled
        for (k in 0 until 12) drawKey(c, k, on && keyOn[k])
    }

    private fun drawDisplay(c: Canvas) {
        val n = input.maxDigits
        val len = input.text.length
        val ok = input.canConfirm
        for (i in 0 until n) {
            val r = slots[i]
            val rr = r.height() * 0.2f
            val filled = i < len
            fill.color = 0x55000000
            c.drawRoundRect(r.left, r.top + depth * 0.6f, r.right, r.bottom + depth * 0.6f, rr, rr, fill)
            fill.color = if (filled) Theme.PAPER else Theme.BG_DEEP
            c.drawRoundRect(r, rr, rr, fill)
            val next = i == len && isEnabled
            stroke.strokeWidth = Theme.dpf(context, if (next) 3f else 1.5f)
            stroke.color = when {
                next -> Theme.GOLD
                filled && messageIsError -> Theme.RED
                filled && ok -> Theme.GREEN
                filled -> Theme.GOLD_DARK
                else -> Theme.CARD_BORDER
            }
            tmp.set(r)
            val ins = stroke.strokeWidth / 2f
            tmp.inset(ins, ins)
            c.drawRoundRect(tmp, rr, rr, stroke)
            if (filled) {
                slotText.color = Theme.INK
                c.drawText(slotTexts[i], r.centerX(), r.centerY() + slotOff, slotText)
            } else if (next) {
                // 깜빡이지 않는 입력 표시 막대
                fill.color = Theme.GOLD
                val bw = r.width() * 0.3f
                c.drawRoundRect(r.centerX() - bw / 2f, r.bottom - r.height() * 0.24f, r.centerX() + bw / 2f, r.bottom - r.height() * 0.16f,
                    bw, bw, fill)
            }
        }
        msgPaint.color = when {
            messageIsError -> Theme.RED
            input.canConfirm -> Theme.GREEN
            else -> Theme.TEXT_SUB
        }
        c.drawText(message, msgRect.centerX(), msgRect.centerY() + msgOff, msgPaint)
    }

    private fun drawKey(c: Canvas, k: Int, on: Boolean) {
        val r = keys[k]
        val down = on && pressed == k
        val off = if (down) depth else 0f
        val a = if (on) 255 else 140
        if (!down) {
            base.color = if (on) baseOn[k] else baseOff[k]
            base.alpha = a
            c.drawRoundRect(r.left, r.top + depth, r.right, r.bottom, radius, radius, base)
        }
        face.shader = if (on) faceOn[k] else faceOff[k]
        face.alpha = a
        c.save()
        c.translate(0f, off)
        tmp.set(r.left, r.top, r.right, r.bottom - depth)
        c.drawRoundRect(tmp, radius, radius, face)
        // 윗면 광택
        gloss.alpha = if (on) 48 else 24
        c.drawRoundRect(r.left + radius * 0.5f, r.top + depth * 0.5f, r.right - radius * 0.5f, r.top + (r.height() - depth) * 0.42f,
            radius * 0.7f, radius * 0.7f, gloss)
        val cx = r.centerX()
        val cy = r.top + (r.height() - depth) / 2f
        val key = GRID[k]
        keyText.alpha = if (on) 255 else 170
        icon.alpha = keyText.alpha
        when (key) {
            KEY_BACK -> { c.translate(cx, cy); c.drawPath(backPath, icon) }
            KEY_OK -> { c.translate(cx, cy); c.drawPath(okPath, icon.apply { strokeWidth *= 1.5f }); icon.strokeWidth /= 1.5f }
            else -> c.drawText(DIGITS[key], cx, cy + digitOff, keyText)
        }
        c.restore()
    }

    companion object {
        const val KEY_BACK = 10
        const val KEY_OK = 11
        /** 화면 배치 순서: 1 2 3 / 4 5 6 / 7 8 9 / ⌫ 0 ✓ */
        private val GRID = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, KEY_BACK, 0, KEY_OK)
        private val DIGITS = Array(10) { it.toString() }
        private const val OK_TEXT = "좋아요! 확인을 누르세요"
        private const val DIGIT_COLOR = 0xFF3B5BC9.toInt()
        private const val BACK_COLOR = 0xFFE0782A.toInt()
    }
}
