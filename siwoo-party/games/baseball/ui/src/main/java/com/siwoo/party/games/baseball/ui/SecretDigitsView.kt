package com.siwoo.party.games.baseball.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.baseball.GameConfig

/**
 * 비밀 숫자 입력 칸 (가려진 표시): 넣은 숫자는 금색 동그라미(●)로만 보이고,
 * 칸을 꾹 누르고 있는 동안만 진짜 숫자가 보인다 (손을 떼거나 일시정지·화면에서 사라지면 바로 가려짐).
 * clear() 로 숫자 참조까지 지운다 (다음 사람으로 넘어가기 전).
 * onDraw 할당 없음.
 */
class SecretDigitsView(context: Context) : View(context) {

    /** 지금 넣은 숫자 (가려진 상태여도 들고 있음 — clear 로 지움) */
    var digits: String = ""
        private set

    /** 지금 누르고 있어 숫자가 보이는 중 */
    var revealed = false
        private set

    /** 화면에 보이는 숫자 (가려져 있으면 "") — 테스트용 */
    val shownText: String get() = if (revealed) digits else ""

    private val slotDigits = Array(GameConfig.DIGITS) { "" }
    private val slots = Array(GameConfig.DIGITS) { RectF() }
    private val eyeRect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Theme.GOLD }
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val eyeOpen = Path()
    private val eyeShut = Path()
    private var depth = 0f

    init {
        isClickable = true
        contentDescription = "비밀 숫자 (꾹 눌러 확인)"
    }

    fun setDigits(s: String) {
        digits = s
        for (i in 0 until GameConfig.DIGITS) slotDigits[i] = if (i < s.length) DIGIT_STR[s[i] - '0'] else ""
        invalidate()
    }

    /** 바로 가리기 */
    fun hide() {
        if (revealed) { revealed = false; invalidate() }
    }

    /** 비밀 지우기 */
    fun clear() {
        revealed = false
        setDigits("")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val n = GameConfig.DIGITS
        depth = Theme.dpf(context, 4f)
        val eyeW = h * 0.62f
        val gap = h * 0.14f
        val s = minOf(h - depth, (w - eyeW - gap * n) / n)
        val total = s * n + gap * (n - 1) + gap + eyeW
        var x = (w - total) / 2f
        val top = (h - depth - s) / 2f
        for (i in 0 until n) { slots[i].set(x, top, x + s, top + s); x += s + gap }
        eyeRect.set(x, top, x + eyeW, top + s)
        digitPaint.textSize = s * 0.66f
        eye.strokeWidth = eyeW * 0.07f
        val ew = eyeW * 0.4f
        val eh = eyeW * 0.24f
        eyeOpen.reset()
        eyeOpen.moveTo(-ew, 0f)
        eyeOpen.quadTo(0f, -eh * 2f, ew, 0f)
        eyeOpen.quadTo(0f, eh * 2f, -ew, 0f)
        eyeShut.reset()
        eyeShut.moveTo(-ew, -eh * 0.2f)
        eyeShut.quadTo(0f, eh * 1.6f, ew, -eh * 0.2f)
        for (k in -1..1) {
            eyeShut.moveTo(k * ew * 0.55f, eh * 0.55f)
            eyeShut.lineTo(k * ew * 0.75f, eh * 1.15f)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if ((context as? PartyGameActivity)?.gamePaused == true) return true
                revealed = true
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> hide()
        }
        return true
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) hide()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) hide()
    }

    override fun onDraw(c: Canvas) {
        if (width <= 0) return
        val len = digits.length
        for (i in 0 until GameConfig.DIGITS) {
            val r = slots[i]
            val rr = r.height() * 0.2f
            val filled = i < len
            fill.color = 0x55000000
            c.drawRoundRect(r.left, r.top + depth, r.right, r.bottom + depth, rr, rr, fill)
            fill.color = when {
                filled && revealed -> Theme.PAPER
                filled -> Theme.CARD_LIGHT
                else -> Theme.BG_DEEP
            }
            c.drawRoundRect(r, rr, rr, fill)
            stroke.strokeWidth = Theme.dpf(context, if (i == len) 3f else 1.5f)
            stroke.color = if (i == len) Theme.GOLD else if (filled) Theme.GOLD_DARK else Theme.CARD_BORDER
            c.drawRoundRect(r, rr, rr, stroke)
            if (filled) {
                if (revealed) {
                    c.drawText(slotDigits[i], r.centerX(), r.centerY() + digitPaint.textSize * 0.36f, digitPaint)
                } else {
                    fill.color = Theme.GOLD
                    c.drawCircle(r.centerX(), r.centerY(), r.height() * 0.16f, fill)
                }
            }
        }
        // 눈 단추 (누르는 동안 뜬 눈)
        val er = eyeRect
        val rr = er.height() * 0.3f
        fill.color = if (revealed) Theme.GOLD_DARK else Theme.CARD
        c.drawRoundRect(er, rr, rr, fill)
        stroke.strokeWidth = Theme.dpf(context, 1.5f)
        stroke.color = Theme.GOLD
        c.drawRoundRect(er, rr, rr, stroke)
        c.save()
        c.translate(er.centerX(), er.centerY())
        eye.color = if (revealed) Theme.TEXT else Theme.GOLD
        if (revealed) {
            c.drawPath(eyeOpen, eye)
            fill.color = Theme.TEXT
            c.drawCircle(0f, 0f, er.width() * 0.09f, fill)
        } else {
            c.drawPath(eyeShut, eye)
        }
        c.restore()
    }

    companion object {
        private val DIGIT_STR = Array(10) { it.toString() }
    }
}
