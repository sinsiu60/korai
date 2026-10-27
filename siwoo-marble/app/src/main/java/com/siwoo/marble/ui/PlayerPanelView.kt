package com.siwoo.marble.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.siwoo.marble.logic.Money

/**
 * 보드 옆 플레이어 패널 (Canvas 로 직접 그림).
 * 말 색상·이름·현재 돈·보유 땅 수·총자산·상태 표시.
 * - 현재 차례: 테두리가 천천히 빛나고(알파만 변화) 살짝 커짐
 * - 돈 변화: 숫자가 빠르게 올라가거나 내려가며(빨강/초록) 옆에 "-200,000"이 떠올랐다 사라짐
 * - 파산: 흑백 + "파산" 도장
 * onDraw 에서 쓰는 문자열은 값이 바뀔 때 미리 만들어 둔다.
 */
class PlayerPanelView(context: Context) : View(context) {

    // ── 데이터 ──
    var playerId = -1
        private set
    private var name = ""
    private var initial = ""
    private var color = Color.GRAY
    private var shownMoney = 0
    private var moneyText = ""
    private var infoText = ""
    private var statusText = ""
    private var isCurrent = false
    // 벌칙 지속 효과 아이콘
    private var fxOneDie = false
    private var fxDoubleToll = false
    private var fxHalfSalary = false
    private var fxSkip = false
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Color.WHITE }
    private var bankrupt = false

    // ── 연출 상태 ──
    private var moneyAnim: ValueAnimator? = null
    private var moneyTrend = 0          // -1 감소(빨강), +1 증가(초록)
    private var floatText = ""
    private var floatColor = 0
    private var floatProgress = 1f      // 1 = 끝남
    private var floatWidth = 0f
    private var floatAnim: ValueAnimator? = null
    private var glow = 0f
    private var glowAnim: ValueAnimator? = null
    private var stamp = 0f              // 파산 도장 진행 (0~1)
    private var stampAnim: ValueAnimator? = null

    // ── 그리기 도구 ──
    private val r = Theme.dpf(context, 16f)
    private val card = RectF()
    private val tmp = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val initialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val moneyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT_SUB }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GOLD_LIGHT; typeface = Typeface.DEFAULT_BOLD }
    private val floatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.RED }
    private val stampBox = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Theme.RED }
    private var bgShader: Shader? = null
    private var grayShader: Shader? = null

    private var stampWidth = 0f
    private var moneyX = 0f
    private var moneyY = 0f

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun bind(id: Int, name: String, color: Int) {
        playerId = id
        this.name = name
        this.initial = if (name.isEmpty()) "?" else name.substring(0, 1)
        this.color = color
        invalidate()
    }

    /** 보유 땅 수·총자산·상태 문자열을 갱신 (상태가 바뀔 때만 호출) */
    fun setInfo(lands: Int, total: Int, status: String) {
        infoText = "땅 ${lands}곳 · 총 ${Money.comma(total)}"
        statusText = status
        contentDescription = "$name ${Money.won(shownMoney)} $infoText"
        invalidate()
    }

    /** 벌칙 지속 효과 (적용되면 사라짐) */
    fun setEffects(oneDie: Boolean, doubleToll: Boolean, halfSalary: Boolean, skip: Boolean) {
        if (oneDie == fxOneDie && doubleToll == fxDoubleToll && halfSalary == fxHalfSalary && skip == fxSkip) return
        fxOneDie = oneDie; fxDoubleToll = doubleToll; fxHalfSalary = halfSalary; fxSkip = skip
        invalidate()
    }

    fun setMoneyNow(v: Int) {
        moneyAnim?.cancel()
        moneyAnim = null
        moneyTrend = 0
        shownMoney = v
        moneyText = Money.won(v)
        invalidate()
    }

    val shown: Int get() = shownMoney

    /** 돈 숫자를 빠르게 카운트하며 바꾸고, 변화량을 숫자 옆에 띄운다 */
    fun animateMoney(target: Int, durMs: Long) {
        moneyAnim?.cancel() // 이전 카운트를 먼저 끝내고 그 값에서 시작
        moneyAnim = null
        val from = shownMoney
        val delta = target - from
        if (delta == 0) return
        moneyTrend = if (delta < 0) -1 else 1
        moneyAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durMs.coerceAtLeast(1)
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val v = from + (delta * it.animatedFraction).toInt()
                if (v != shownMoney) {
                    shownMoney = v
                    moneyText = Money.won(v)
                }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    shownMoney = target
                    moneyText = Money.won(target)
                    moneyTrend = 0
                    invalidate()
                }
            })
            start()
        }
        floatText = (if (delta > 0) "+" else "") + Money.comma(delta)
        floatColor = if (delta < 0) Theme.RED else Theme.GREEN
        floatWidth = floatPaint.measureText(floatText)
        floatAnim?.cancel()
        floatAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (durMs * 1.2f).toLong().coerceAtLeast(1)
            addUpdateListener { floatProgress = it.animatedFraction; invalidate() }
            start()
        }
        floatProgress = 0f
    }

    fun setCurrent(current: Boolean) {
        if (current == isCurrent && (glowAnim != null) == (current && !bankrupt)) return
        isCurrent = current
        glowAnim?.cancel()
        glowAnim = null
        if (current && !bankrupt) {
            glowAnim = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                duration = 1800
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { glow = it.animatedValue as Float; invalidate() }
                start()
            }
            animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).start()
        } else {
            glow = 0f
            animate().scaleX(1f).scaleY(1f).setDuration(300).start()
        }
        invalidate()
    }

    fun setBankrupt(b: Boolean, animate: Boolean) {
        if (bankrupt == b) return
        bankrupt = b
        stampAnim?.cancel()
        if (b) {
            setCurrent(false)
            if (animate) {
                stampAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 600
                    interpolator = OvershootInterpolator(2f)
                    addUpdateListener { stamp = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else {
                stamp = 1f
            }
        } else {
            stamp = 0f
        }
        invalidate()
    }

    /** 코인이 날아올/떠날 돈 숫자 위치 (이 View 좌표) */
    fun moneyAnchor(out: PointF) {
        out.set(moneyX + Theme.dpf(context, 50f), moneyY - moneyPaint.textSize * 0.35f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = Theme.dpf(context, 4f)
        card.set(pad, pad, w - pad, h - pad)
        bgShader = LinearGradient(0f, 0f, 0f, h.toFloat(), Theme.CARD_LIGHT, Theme.CARD, Shader.TileMode.CLAMP)
        grayShader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF4A4F63.toInt(), 0xFF2C2F3B.toInt(), Shader.TileMode.CLAMP)
        val base = minOf(w / 9.5f, h / 6.2f)
        namePaint.textSize = base * 0.95f
        initialPaint.textSize = base * 1.05f
        moneyPaint.textSize = base * 1.25f
        infoPaint.textSize = base * 0.72f
        statusPaint.textSize = base * 0.7f
        floatPaint.textSize = base * 0.9f
        stampPaint.textSize = base * 2.2f
        stampBox.strokeWidth = base * 0.25f
        stampWidth = stampPaint.measureText(STAMP)
        borderPaint.strokeWidth = Theme.dpf(context, 3f)
    }

    override fun onDraw(c: Canvas) {
        if (playerId < 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val base = namePaint.textSize / 0.95f
        val pc = if (bankrupt) 0xFF8A8F9E.toInt() else color

        // 현재 차례: 바깥으로 번지는 빛 (블러 없이 두께·알파가 다른 테두리 여러 겹)
        if (isCurrent && !bankrupt) {
            for (k in 3 downTo 1) {
                borderPaint.color = pc
                borderPaint.alpha = ((0.18f + 0.35f * glow) * 255 / k).toInt()
                borderPaint.strokeWidth = Theme.dpf(context, 2f) * k * 1.4f
                c.drawRoundRect(card, r, r, borderPaint)
            }
        }
        bgPaint.shader = if (bankrupt) grayShader else bgShader
        c.drawRoundRect(card, r, r, bgPaint)
        borderPaint.strokeWidth = Theme.dpf(context, if (isCurrent && !bankrupt) 3f else 1.5f)
        borderPaint.color = if (isCurrent && !bankrupt) pc else 0x44FFFFFF
        if (isCurrent && !bankrupt) borderPaint.alpha = (150 + 105 * glow).toInt()
        c.drawRoundRect(card, r, r, borderPaint)

        // 말 아이콘 + 이름
        val pad = card.left + base * 0.7f
        val ar = base * 0.95f
        val acy = card.top + base * 0.6f + ar
        avatarPaint.color = pc
        c.drawCircle(pad + ar, acy, ar, avatarPaint)
        avatarPaint.color = 0x66FFFFFF
        c.drawCircle(pad + ar * 0.7f, acy - ar * 0.35f, ar * 0.3f, avatarPaint)
        initialPaint.color = Theme.textOn(pc)
        c.drawText(initial, pad + ar, acy + initialPaint.textSize * 0.36f, initialPaint)
        namePaint.color = if (bankrupt) 0xFFB0B4C0.toInt() else Theme.TEXT
        c.drawText(name, pad + ar * 2f + base * 0.45f, acy + namePaint.textSize * 0.36f, namePaint)

        // 벌칙 지속 효과 아이콘 (오른쪽 위)
        if (!bankrupt) drawEffects(c, card.right - base * 0.75f, acy, base * 0.62f)

        // 돈
        moneyX = pad
        moneyY = acy + ar + base * 1.75f
        moneyPaint.color = when {
            bankrupt -> 0xFFB0B4C0.toInt()
            moneyTrend < 0 -> Theme.RED
            moneyTrend > 0 -> Theme.GREEN
            shownMoney < 0 -> Theme.RED
            else -> Theme.GOLD
        }
        c.drawText(moneyText, moneyX, moneyY, moneyPaint)

        // 보유 땅 수 · 총자산 / 상태
        c.drawText(infoText, pad, moneyY + base * 1.15f, infoPaint)
        if (statusText.isNotEmpty()) c.drawText(statusText, pad, moneyY + base * 2.05f, statusPaint)

        // 돈 변화량이 떠올랐다 사라짐
        if (floatProgress < 1f) {
            floatPaint.color = floatColor
            floatPaint.alpha = ((1f - floatProgress) * 255).toInt()
            c.drawText(floatText, w - card.left - floatWidth - base * 0.4f,
                moneyY - base * 1.6f * floatProgress - base * 0.2f, floatPaint)
        }

        // 파산 도장
        if (bankrupt && stamp > 0f) {
            c.save()
            c.translate(w / 2f, h / 2f)
            c.rotate(-15f)
            val s = 2f - stamp // 크게 찍혔다가 제자리
            c.scale(s, s)
            val tw = stampWidth
            tmp.set(-tw / 2 - base * 0.5f, -stampPaint.textSize * 0.9f, tw / 2 + base * 0.5f, stampPaint.textSize * 0.35f)
            stampBox.alpha = (minOf(1f, stamp) * 255).toInt()
            stampPaint.alpha = stampBox.alpha
            c.drawRoundRect(tmp, base * 0.4f, base * 0.4f, stampBox)
            c.drawText(STAMP, 0f, 0f, stampPaint)
            c.restore()
        }
    }

    /** 작은 아이콘: 주사위1(파랑) / 통행료×2(빨강) / 월급½(주황) / 쉬기(회색) — 오른쪽에서 왼쪽으로 */
    private fun drawEffects(c: Canvas, right: Float, cy: Float, rad: Float) {
        var x = right
        badgeText.textSize = rad * 0.95f
        if (fxSkip) { badge(c, x, cy, rad, 0xFF78909C.toInt(), "쉼"); x -= rad * 2.3f }
        if (fxHalfSalary) { badge(c, x, cy, rad, 0xFFFF8F00.toInt(), "½"); x -= rad * 2.3f }
        if (fxDoubleToll) { badge(c, x, cy, rad, 0xFFE53935.toInt(), "×2"); x -= rad * 2.3f }
        if (fxOneDie) {
            // 눈이 하나인 주사위
            tmp.set(x - rad, cy - rad, x + rad, cy + rad)
            badgePaint.color = Color.WHITE
            c.drawRoundRect(tmp, rad * 0.35f, rad * 0.35f, badgePaint)
            badgePaint.color = 0xFF1565C0.toInt()
            c.drawCircle(x, cy, rad * 0.32f, badgePaint)
        }
    }

    private fun badge(c: Canvas, x: Float, cy: Float, rad: Float, color: Int, text: String) {
        badgePaint.color = color
        c.drawCircle(x, cy, rad, badgePaint)
        c.drawText(text, x, cy + badgeText.textSize * 0.36f, badgeText)
    }

    fun cancelAnimations() {
        moneyAnim?.cancel(); moneyAnim = null
        floatAnim?.cancel(); floatAnim = null
        floatProgress = 1f
        moneyTrend = 0
    }

    override fun onDetachedFromWindow() {
        cancelAnimations()
        glowAnim?.cancel()
        stampAnim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val STAMP = "파산"
    }
}
