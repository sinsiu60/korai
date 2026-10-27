package com.siwoo.party.common.secret

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme

/**
 * 꾹 누르고 있는 동안만 비밀이 보이는 카드 (라이어 게임 제시어 카드에서 공통화 — 손패·비밀 숫자·비밀 주사위 확인에 재사용).
 * - 가려짐(공통 모양): 남색 그라데이션 카드 + 금색 점선 + 동심원 위 감은 눈 + "꾹 누르고 있는 동안만 보여요"
 * - 보임: setContent 로 넣은 SecretContent 가 카드 영역을 그린다
 * - 손가락을 대는 순간(ACTION_DOWN) 보이고, 떼는 순간(UP/CANCEL) 바로 가려진다 (서서히 사라지지 않음)
 * - 화면에서 안 보이게 되거나(visibility) 창 포커스를 잃거나 게임이 일시정지되면 바로 가린다
 * - 다음 사람으로 넘어가기 전에 clear() → 내용 참조까지 지운다 (전환 화면 뒤에 이전 사람 정보가 남지 않음)
 * onDraw 에서는 객체를 만들지 않는다 (글자 크기·Shader 는 크기·글자가 바뀔 때만).
 */
class SecretHoldView(context: Context) : View(context) {

    /** 보여 줄 비밀 (clear 뒤엔 null) */
    var content: SecretContent? = null
        private set

    /** 지금 누르고 있어 보이는 중 */
    var revealed = false
        private set

    /** 한 번이라도 꾹 눌러 봤는지 ([확인 완료] 켜기용) */
    var heldOnce = false
        private set

    /** 보임(true)·가림(false)이 바뀔 때 */
    var onHoldChanged: ((Boolean) -> Unit)? = null

    /** 게임이 일시정지 중인지 (기본: PartyGameActivity.gamePaused). 일시정지 중엔 보이지 않는다 */
    var pausedCheck: () -> Boolean = { (context as? PartyGameActivity)?.gamePaused == true }

    /** 지금 화면에 보이는 비밀 글자 (가려져 있으면 "") — 테스트·확인용 */
    val shownText: String get() = if (revealed) content?.text ?: "" else ""

    /** 비밀이 들어 있는지 (clear 뒤엔 false) */
    val hasSecret: Boolean get() = content != null

    private var hint = HINT
    private var hintSub = HINT_SUB

    private val card = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private var hiddenShader: Shader? = null

    /** 일시정지 메뉴가 뜨면(화면 배치가 바뀜) 바로 가린다 */
    private val layoutWatch = ViewTreeObserver.OnGlobalLayoutListener { if (revealed && pausedCheck()) hide() }

    init {
        contentDescription = DESC
        isClickable = true
    }

    /** 비밀 넣기 (가려진 상태, heldOnce 초기화) */
    fun setContent(c: SecretContent) {
        content = c
        revealed = false
        heldOnce = false
        if (card.width() > 0f) c.layout(this, card)
        invalidate()
    }

    /** 가려진 면 안내 문구 바꾸기 */
    fun setHint(hint: String, sub: String = HINT_SUB) {
        this.hint = hint
        hintSub = sub
        fit()
        invalidate()
    }

    /** 비밀 지우기 (다음 사람으로 넘어가기 전) — 내용의 글자까지 지운다 */
    fun clear() {
        content?.clear()
        content = null
        revealed = false
        heldOnce = false
        invalidate()
    }

    /** 바로 가리기 (손을 떼지 않고 화면이 바뀌는 경우·일시정지 등) */
    fun hide() = show(false)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!pausedCheck()) show(true)
            }
            MotionEvent.ACTION_MOVE -> if (revealed && pausedCheck()) show(false)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> show(false)
        }
        return true
    }

    private fun show(v: Boolean) {
        if (v && content == null) return
        if (revealed == v) return
        revealed = v
        if (v) heldOnce = true
        invalidate()
        onHoldChanged?.invoke(v)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) hide()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) hide()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutWatch)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(layoutWatch)
        revealed = false
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = Theme.dpf(context, 4f)
        card.set(pad, pad, w - pad, h - pad)
        hiddenShader = LinearGradient(0f, 0f, 0f, h.toFloat(), Theme.CARD_LIGHT, Theme.BG_DEEP, Shader.TileMode.CLAMP)
        val d = Theme.dpf(context, 8f)
        dash.pathEffect = DashPathEffect(floatArrayOf(d, d * 0.7f), 0f)
        dash.strokeWidth = Theme.dpf(context, 2f)
        fit()
        if (card.width() > 0f) content?.layout(this, card)
    }

    /** 안내 글자 크기 (크기·문구가 바뀔 때만) */
    private fun fit() {
        val h = card.height()
        val w = card.width()
        if (h <= 0f) return
        hintPaint.textSize = h * 0.105f
        shrink(hintPaint, hint, w * 0.86f)
        subPaint.textSize = h * 0.07f
        shrink(subPaint, hintSub, w * 0.86f)
    }

    private fun shrink(p: Paint, s: String, maxW: Float) {
        while (s.isNotEmpty() && p.measureText(s) > maxW && p.textSize > Theme.dpf(context, 8f)) p.textSize *= 0.92f
    }

    override fun onDraw(c: Canvas) {
        if (card.width() <= 0f) return
        val h = card.height()
        val r = h * 0.1f
        // 그림자
        fill.shader = null
        fill.color = 0x55000000
        c.drawRoundRect(card.left + h * 0.02f, card.top + h * 0.04f, card.right + h * 0.02f, card.bottom + h * 0.04f, r, r, fill)
        val ct = content
        if (revealed && ct != null) ct.draw(c, card) else drawHidden(c, h, r)
    }

    private fun drawHidden(c: Canvas, h: Float, r: Float) {
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = hiddenShader
        c.drawRoundRect(card, r, r, fill)
        fill.shader = null
        val inset = h * 0.06f
        dash.color = Theme.GOLD
        c.drawRoundRect(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset, r * 0.7f, r * 0.7f, dash)
        // 누르는 곳 (동심원) + 감은 눈
        val cx = card.centerX()
        val cy = card.top + h * 0.36f
        stroke.color = Theme.GOLD
        stroke.strokeWidth = h * 0.018f
        for (k in 1..3) {
            stroke.alpha = 220 - k * 55
            c.drawCircle(cx, cy, h * (0.1f + k * 0.055f), stroke)
        }
        stroke.alpha = 255
        drawClosedEye(c, cx, cy - h * 0.02f, h * 0.2f)
        c.drawText(hint, cx, card.top + h * 0.75f, hintPaint)
        c.drawText(hintSub, cx, card.top + h * 0.87f, subPaint)
    }

    /** 감은 눈 (중심, 폭 w) + 속눈썹 */
    private fun drawClosedEye(c: Canvas, cx: Float, cy: Float, w: Float) {
        stroke.color = Theme.GOLD_LIGHT
        stroke.strokeWidth = w * 0.08f
        c.drawArc(cx - w / 2f, cy - w * 0.4f, cx + w / 2f, cy + w * 0.3f, 15f, 150f, false, stroke)
        c.drawLine(cx, cy + w * 0.3f, cx, cy + w * 0.44f, stroke)
        c.drawLine(cx - w * 0.26f, cy + w * 0.24f, cx - w * 0.34f, cy + w * 0.36f, stroke)
        c.drawLine(cx + w * 0.26f, cy + w * 0.24f, cx + w * 0.34f, cy + w * 0.36f, stroke)
    }

    companion object {
        const val HINT = "꾹 누르고 있는 동안만 보여요"
        const val HINT_SUB = "손을 떼면 바로 가려져요 · 다른 사람은 보지 마세요"
        private const val DESC = "비밀 확인 카드"
    }
}
