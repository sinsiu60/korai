package com.siwoo.party.games.bingo.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.OvershootInterpolator
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bingo.GameConfig

/**
 * 모두가 보는 가운데 판:
 *   왼쪽: 방금 부른 숫자 공 ("17!") + 아래 두 줄 글자 (누가 불렀는지 / 새로 완성된 줄)
 *   오른쪽: 1~25 부른 숫자표 (부른 숫자는 금색, 방금 부른 숫자는 고리)
 * showCall 때만 짧게 움직이고(공이 튀어나오며 커지고 고리가 퍼짐) 끝나면 다시 그리지 않는다. onDraw 할당 없음.
 */
class CallView(context: Context) : View(context) {

    private val called = BooleanArray(GameConfig.CELLS + 1)
    /** 지금 크게 보이는 숫자 (0 = 아직 없음) */
    var number = 0
        private set
    var caption = ""
        private set
    var sub = ""
        private set
    /** 부른 숫자 개수 */
    var calledCount = 0
        private set

    private var anim: ValueAnimator? = null
    /** 공 크기 (0→1, 튀어나옴) */
    private var popF = 1f
    /** 퍼지는 고리 (0→1) */
    private var ringF = 1f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val overshoot = OvershootInterpolator(2.6f)
    private var ballShader: Shader? = null

    // 배치 (onSizeChanged)
    private var ballCx = 0f
    private var ballCy = 0f
    private var ballR = 0f
    private var capY = 0f
    private var subY = 0f
    private var gridL = 0f
    private var gridT = 0f
    private var gridS = 0f
    private var gCell = 0f
    private var gGap = 0f
    private var textW = 0f
    private var baseCap = 0f
    private var baseSub = 0f
    private var titleY = 0f

    init {
        contentDescription = "부른 숫자"
    }

    /** 처음 상태 글자 (아직 부른 숫자 없음) */
    fun setIdle(caption: String, sub: String) {
        this.caption = caption
        this.sub = sub
        fitText()
        invalidate()
    }

    /** 숫자를 불렀다: 크게 튀어나오는 연출 (ms 동안만 다시 그림) */
    fun showCall(n: Int, caption: String, sub: String, ms: Long) {
        number = n
        if (!called[n]) {
            called[n] = true
            calledCount++
        }
        this.caption = caption
        this.sub = sub
        fitText()
        anim?.cancel()
        popF = 0f
        ringF = 0f
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms.coerceAtLeast(1)
            addUpdateListener {
                val f = it.animatedValue as Float
                popF = overshoot.getInterpolation((f / 0.6f).coerceAtMost(1f))
                ringF = f
                invalidate()
            }
            start()
        }
    }

    /** 아래 작은 글자만 바꾸기 */
    fun setSub(s: String) {
        sub = s
        fitText()
        invalidate()
    }

    fun isCalled(n: Int): Boolean = called[n]

    /** 연출 중 (테스트용) */
    val animating: Boolean get() = anim?.isRunning == true

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = Theme.dpf(context, 8f)
        // 오른쪽 숫자표: 높이에 맞춘 정사각형 (제목 줄 몫 빼고)
        val title = h * 0.1f
        gridS = minOf(h - pad * 2f - title, w * 0.5f)
        gridL = w - pad - gridS
        gridT = pad + title + (h - pad * 2f - title - gridS) / 2f
        titleY = gridT - title * 0.3f
        gGap = gridS * 0.02f
        gCell = (gridS - gGap * 4f) / 5f
        numPaint.textSize = gCell * 0.44f
        titlePaint.textSize = title * 0.62f
        // 왼쪽 공
        val leftW = gridL - pad * 2f
        textW = leftW * 0.96f
        ballCx = pad + leftW / 2f
        ballR = minOf(leftW * 0.36f, h * 0.29f)
        ballCy = pad + ballR * 1.15f
        bigPaint.textSize = ballR * 0.56f
        baseCap = h * 0.075f
        baseSub = h * 0.062f
        capY = ballCy + ballR * 1.15f + baseCap * 1.1f
        subY = capY + baseCap * 1.35f
        ballShader = RadialGradient(ballCx - ballR * 0.35f, ballCy - ballR * 0.4f, ballR * 1.4f,
            BingoArt.BALL_LIGHT, BingoArt.BALL, Shader.TileMode.CLAMP)
        stroke.strokeWidth = Theme.dpf(context, 2f)
        fitText()
    }

    /** 글자가 넘치면 줄인다 (글자·크기가 바뀔 때만) */
    private fun fitText() {
        if (textW <= 0f) return
        capPaint.textSize = baseCap
        while (caption.isNotEmpty() && capPaint.measureText(caption) > textW && capPaint.textSize > 8f) capPaint.textSize *= 0.92f
        subPaint.textSize = baseSub
        while (sub.isNotEmpty() && subPaint.measureText(sub) > textW && subPaint.textSize > 8f) subPaint.textSize *= 0.92f
        bigPaint.textSize = ballR * (if (number >= 10) 0.5f else 0.62f)
    }

    override fun onDraw(c: Canvas) {
        if (gridS <= 0f) return
        drawBall(c)
        if (caption.isNotEmpty()) c.drawText(caption, ballCx, capY, capPaint)
        if (sub.isNotEmpty()) c.drawText(sub, ballCx, subY, subPaint)
        drawGrid(c)
    }

    private fun drawBall(c: Canvas) {
        // 퍼지는 고리
        if (number > 0 && ringF < 1f) {
            stroke.color = Theme.GOLD
            stroke.alpha = ((1f - ringF) * 230).toInt()
            val sw = stroke.strokeWidth
            stroke.strokeWidth = sw * 2f
            c.drawCircle(ballCx, ballCy, ballR * (1f + ringF * 0.55f), stroke)
            stroke.strokeWidth = sw
            stroke.alpha = 255
        }
        val r = ballR * (0.35f + 0.65f * popF)
        // 받침 그림자
        fill.shader = null
        fill.color = 0x55000000
        c.drawCircle(ballCx + r * 0.05f, ballCy + r * 0.09f, r, fill)
        if (number == 0) {
            fill.color = Theme.CARD_LIGHT
            c.drawCircle(ballCx, ballCy, r, fill)
        } else {
            fill.color = 0xFFFFFFFF.toInt()
            fill.shader = ballShader
            c.drawCircle(ballCx, ballCy, r, fill)
            fill.shader = null
        }
        fill.color = 0xFFFFFFFF.toInt()
        c.drawCircle(ballCx, ballCy, r * 0.66f, fill)
        stroke.color = if (number == 0) Theme.GRAY else Theme.GOLD_DARK
        c.drawCircle(ballCx, ballCy, r * 0.66f, stroke)
        val ts = bigPaint.textSize
        bigPaint.textSize = ts * (r / ballR)
        bigPaint.color = if (number == 0) Theme.GRAY else Theme.INK
        c.drawText(BingoArt.SHOUT[number], ballCx, ballCy + bigPaint.textSize * 0.36f, bigPaint)
        bigPaint.textSize = ts
    }

    private fun drawGrid(c: Canvas) {
        c.drawText(TITLE, gridL + gridS / 2f, titleY, titlePaint)
        val rr = gCell * 0.2f
        for (k in 0 until GameConfig.CELLS) {
            val n = k + 1
            val l = gridL + (k % 5) * (gCell + gGap)
            val t = gridT + (k / 5) * (gCell + gGap)
            val on = called[n]
            fill.color = if (on) Theme.GOLD else Theme.CARD
            c.drawRoundRect(l, t, l + gCell, t + gCell, rr, rr, fill)
            if (!on) {
                stroke.color = Theme.CARD_BORDER
                c.drawRoundRect(l, t, l + gCell, t + gCell, rr, rr, stroke)
            }
            if (n == number) {
                stroke.color = 0xFFFFFFFF.toInt()
                val sw = stroke.strokeWidth
                stroke.strokeWidth = sw * 1.6f
                c.drawRoundRect(l - sw, t - sw, l + gCell + sw, t + gCell + sw, rr, rr, stroke)
                stroke.strokeWidth = sw
            }
            numPaint.color = if (on) Theme.INK else Theme.TEXT_SUB
            c.drawText(BingoArt.NUM[n], l + gCell / 2f, t + gCell / 2f + numPaint.textSize * 0.36f, numPaint)
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TITLE = "부른 숫자"
    }
}
