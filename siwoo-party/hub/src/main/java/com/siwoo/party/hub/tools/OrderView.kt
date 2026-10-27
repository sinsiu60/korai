package com.siwoo.party.hub.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 순서 정하기 카드 (Canvas 카드 n장).
 * 이름이 보이는 카드 → 모두 뒤집힘 → 두 장씩 자리 바꾸기(섞기) → 자리 아래 1번·2번… → 1번부터 차례로 뒤집어 공개.
 * 연출은 [tick] 이 돌 때만. onDraw 할당 없음.
 */
class OrderView(context: Context) : View(context) {

    /** 연출 속도 배율 (PartySettings.speedFactor, 박싱 없는 Float 필드) */
    var speedFactor = 1f

    interface Listener {
        /** k번째 자리(0부터) 카드가 공개됨 */
        fun onReveal(slot: Int, player: Int)
        fun onDone()
    }

    enum class Phase { INTRO, FLIP_DOWN, SWAP, BADGES, REVEAL, DONE }

    var listener: Listener? = null
    var phase = Phase.DONE
        private set

    private var n = 0
    private var names = emptyArray<String>()
    private var initials = emptyArray<String>()
    private var colors = IntArray(0)
    /** 자리 k 에 있는 사람 */
    val slotPlayer = IntArray(MAX_CARDS)
    /** 사람별: 앞면이 보이는지, 번호가 공개됐는지 */
    private val faceUp = BooleanArray(MAX_CARDS)
    private val numbered = BooleanArray(MAX_CARDS)
    private var swaps: List<IntArray> = emptyList()
    private var step = 0
    private var phaseMs = 0f
    private var revealIdx = 0
    private var badgeAlpha = 0f

    private var cardW = 0f
    private var cardH = 0f
    private var cardTop = 0f
    private val slotCx = FloatArray(MAX_CARDS)

    private val rect = RectF()
    private val inner = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val nameText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val bandText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val statusText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val qText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }

    init {
        statusText.textSize = Theme.dpf(context, 17f)
        statusText.setShadowLayer(Theme.dpf(context, 2f), 0f, Theme.dpf(context, 1f), 0xFF000000.toInt())
    }

    fun setPlayers(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size.coerceAtMost(MAX_CARDS)
        names = Array(n) { playerNames[it] }
        initials = Array(n) { playerNames[it].take(1) }
        colors = playerColors.copyOf(n)
        for (k in 0 until n) { slotPlayer[k] = k; faceUp[k] = true; numbered[k] = false }
        phase = Phase.DONE
        layoutCards()
        invalidate()
    }

    /** 섞기 시작: 지금 배치에서 plan 의 바꾸기를 차례로 */
    fun start(plan: OrderShuffle) {
        swaps = plan.swaps
        step = 0
        revealIdx = 0
        phaseMs = 0f
        badgeAlpha = 0f
        for (k in 0 until n) numbered[k] = false
        phase = if (faceUp.take(n).all { it } && faceUp.isNotEmpty()) Phase.INTRO else Phase.FLIP_DOWN
        invalidate()
    }

    fun slotCenterX(k: Int): Float = slotCx[k]
    fun cardCenterY(): Float = cardTop + cardH / 2f

    /** 모든 공개가 끝난 뒤 순서 (자리 k → 사람) */
    fun order(): IntArray = slotPlayer.copyOf(n)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutCards()

    private fun layoutCards() {
        if (width == 0 || n == 0) return
        val gap = Theme.dpf(context, 18f)
        val statusH = Theme.dpf(context, 34f)
        val badgeH = Theme.dpf(context, 44f)
        val availH = height - statusH - badgeH - Theme.dpf(context, 10f)
        cardW = minOf((width - gap * (n + 1)) / n, Theme.dpf(context, 160f), availH / 1.42f)
        cardH = cardW * 1.42f
        cardTop = statusH + (availH - cardH) / 2f + Theme.dpf(context, 6f)
        val total = n * cardW + (n - 1) * gap
        for (k in 0 until n) slotCx[k] = (width - total) / 2f + cardW / 2f + k * (cardW + gap)
        // 가장 긴 이름이 카드 폭에 들어가게
        var size = cardW * 0.24f
        nameText.textSize = size
        while (size > Theme.dpf(context, 10f) && names.any { nameText.measureText(it) > cardW * 0.84f }) {
            size *= 0.92f
            nameText.textSize = size
        }
        bandText.textSize = cardW * 0.2f
        badgeText.textSize = Theme.dpf(context, 16f)
        qText.textSize = cardW * 0.3f
        stroke.strokeWidth = Theme.dpf(context, 3f)
    }

    private fun dur(ms: Long): Float = (ms * speedFactor).coerceAtLeast(1f)

    /** 한 프레임. 움직이는 중이면 true */
    fun tick(dt: Long): Boolean {
        if (phase == Phase.DONE) return false
        phaseMs += dt
        when (phase) {
            Phase.INTRO -> if (phaseMs >= dur(INTRO_MS)) next(Phase.FLIP_DOWN)
            Phase.FLIP_DOWN -> if (phaseMs >= dur(FLIP_MS)) {
                for (k in 0 until n) faceUp[k] = false
                next(if (swaps.isEmpty()) Phase.BADGES else Phase.SWAP)
            }
            Phase.SWAP -> if (phaseMs >= dur(SWAP_MS)) {
                val s = swaps[step]
                val t = slotPlayer[s[0]]; slotPlayer[s[0]] = slotPlayer[s[1]]; slotPlayer[s[1]] = t
                step++
                if (step >= swaps.size) next(Phase.BADGES) else phaseMs -= dur(SWAP_MS)
            }
            Phase.BADGES -> {
                badgeAlpha = (phaseMs / dur(BADGE_MS)).coerceIn(0f, 1f)
                if (phaseMs >= dur(BADGE_MS)) next(Phase.REVEAL)
            }
            Phase.REVEAL -> if (phaseMs >= dur(FLIP_MS) && !faceUp[slotPlayer[revealIdx]]) {
                val p = slotPlayer[revealIdx]
                faceUp[p] = true
                numbered[p] = true
                listener?.onReveal(revealIdx, p)
            } else if (phaseMs >= dur(FLIP_MS) + dur(REVEAL_GAP_MS)) {
                revealIdx++
                if (revealIdx >= n) {
                    phase = Phase.DONE
                    listener?.onDone()
                } else {
                    phaseMs = 0f
                }
            }
            Phase.DONE -> {}
        }
        invalidate()
        return phase != Phase.DONE
    }

    private fun next(p: Phase) {
        phase = p
        phaseMs = 0f
    }

    /** 지금 단계 진행 0..1 */
    private fun frac(ms: Long): Float = (phaseMs / dur(ms)).coerceIn(0f, 1f)

    override fun onDraw(c: Canvas) {
        if (n == 0 || cardW <= 0f) return
        // 위쪽 상태 글자
        val status = when (phase) {
            Phase.INTRO -> S_INTRO
            Phase.FLIP_DOWN, Phase.SWAP -> S_SHUFFLE
            Phase.BADGES, Phase.REVEAL -> S_REVEAL
            else -> if (numbered[slotPlayer[0]]) S_DONE else S_READY // DONE
        }
        c.drawText(status, width / 2f, Theme.dpf(context, 24f), statusText)

        // 자리 번호 (섞기가 끝난 뒤)
        val showBadges = phase == Phase.BADGES || phase == Phase.REVEAL || (phase == Phase.DONE && numbered[slotPlayer[0]])
        if (showBadges) {
            val a = if (phase == Phase.BADGES) badgeAlpha else 1f
            val by = cardTop + cardH + Theme.dpf(context, 24f)
            val bw = Theme.dpf(context, 46f)
            val bh = Theme.dpf(context, 28f)
            for (k in 0 until n) {
                rect.set(slotCx[k] - bw / 2f, by - bh / 2f, slotCx[k] + bw / 2f, by + bh / 2f)
                val lit = numbered[slotPlayer[k]]
                fill.color = if (lit) Theme.GOLD else Theme.CARD_LIGHT
                fill.alpha = (255 * a).toInt()
                c.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
                stroke.color = Theme.GOLD
                stroke.alpha = (255 * a).toInt()
                stroke.strokeWidth = Theme.dpf(context, 2f)
                c.drawRoundRect(rect, bh / 2f, bh / 2f, stroke)
                badgeText.color = if (lit) Theme.INK else Theme.GOLD_LIGHT
                badgeText.alpha = (255 * a).toInt()
                c.drawText(NUMBERS[k], slotCx[k], by + badgeText.textSize * 0.36f, badgeText)
            }
            stroke.alpha = 255
            badgeText.alpha = 255
        }

        // 카드: 움직이지 않는 카드 먼저, 움직이는 카드는 위에
        val swapping = phase == Phase.SWAP && step < swaps.size
        val sa = if (swapping) swaps[step][0] else -1
        val sb = if (swapping) swaps[step][1] else -1
        for (k in 0 until n) if (k != sa && k != sb) drawSlotCard(c, k, slotCx[k], 0f)
        if (swapping) {
            val f = easeInOut(frac(SWAP_MS))
            val lift = cardH * 0.13f * sin(PI * f).toFloat()
            drawSlotCard(c, sa, slotCx[sa] + (slotCx[sb] - slotCx[sa]) * f, -lift)
            drawSlotCard(c, sb, slotCx[sb] + (slotCx[sa] - slotCx[sb]) * f, lift)
        }
    }

    /** 자리 k 의 카드를 (cx, 위아래 dy) 에 그린다 (뒤집기 중이면 가로로 눌림) */
    private fun drawSlotCard(c: Canvas, slot: Int, cx: Float, dy: Float) {
        val p = slotPlayer[slot]
        var showFront = faceUp[p]
        var sx = 1f
        if (phase == Phase.FLIP_DOWN) {
            val f = frac(FLIP_MS)
            sx = abs(cos(PI * f).toFloat())
            showFront = f < 0.5f
        } else if (phase == Phase.REVEAL && slot == revealIdx && !faceUp[p]) {
            val f = frac(FLIP_MS)
            sx = abs(cos(PI * f).toFloat())
            showFront = f >= 0.5f
        }
        val top = cardTop + dy
        rect.set(cx - cardW / 2f, top, cx + cardW / 2f, top + cardH)
        c.save()
        c.scale(sx.coerceAtLeast(0.02f), 1f, cx, top + cardH / 2f)
        // 그림자
        fill.color = 0x55000000
        fill.alpha = 0x55
        rect.offset(0f, Theme.dpf(context, 4f))
        c.drawRoundRect(rect, radius(), radius(), fill)
        rect.offset(0f, -Theme.dpf(context, 4f))
        if (showFront) drawFront(c, p, cx, top, revealing = phase == Phase.REVEAL && slot == revealIdx) else drawBack(c)
        c.restore()
    }

    private fun radius() = cardW * 0.1f

    private fun drawFront(c: Canvas, p: Int, cx: Float, top: Float, revealing: Boolean) {
        val r = radius()
        val col = colors[p]
        val band = cardH * 0.24f
        fill.color = col
        fill.alpha = 255
        c.drawRoundRect(rect, r, r, fill)
        inner.set(rect.left, rect.top + band, rect.right, rect.bottom)
        fill.color = Theme.PAPER
        c.drawRoundRect(inner, r, r, fill)
        c.drawRect(inner.left, inner.top, inner.right, inner.top + r, fill)
        stroke.color = if (numbered[p] || revealing) Theme.GOLD else col
        stroke.strokeWidth = Theme.dpf(context, 3f)
        c.drawRoundRect(rect, r, r, stroke)
        // 띠: 번호(공개 뒤) 또는 이니셜 동그라미
        val bandCy = rect.top + band / 2f
        val slot = slotOf(p)
        if (numbered[p] || (revealing && slot >= 0)) {
            bandText.color = Theme.textOn(col)
            c.drawText(NUMBERS[slot], cx, bandCy + bandText.textSize * 0.36f, bandText)
        } else {
            fill.color = Theme.shade(col, 1.35f)
            for (k in -1..1) c.drawCircle(cx + k * cardW * 0.16f, bandCy, cardW * 0.035f, fill)
        }
        // 이름
        c.drawText(names[p], cx, rect.top + band + (cardH - band) * 0.48f + nameText.textSize * 0.36f, nameText)
        // 아래 이니셜 동그라미
        val iy = rect.bottom - cardH * 0.17f
        fill.color = col
        c.drawCircle(cx, iy, cardW * 0.12f, fill)
        qText.color = Theme.textOn(col)
        val old = qText.textSize
        qText.textSize = cardW * 0.13f
        c.drawText(initials[p], cx, iy + qText.textSize * 0.36f, qText)
        qText.textSize = old
    }

    private fun drawBack(c: Canvas) {
        val r = radius()
        fill.color = Theme.BG_TOP
        fill.alpha = 255
        c.drawRoundRect(rect, r, r, fill)
        val inset = cardW * 0.07f
        inner.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        // 다이아몬드 무늬
        c.save()
        c.clipRect(inner)
        stroke.color = Theme.GOLD
        stroke.alpha = 70
        stroke.strokeWidth = Theme.dpf(context, 1.5f)
        val g = cardW * 0.16f
        var x = inner.left - inner.height()
        while (x < inner.right) {
            c.drawLine(x, inner.bottom, x + inner.height(), inner.top, stroke)
            c.drawLine(x, inner.top, x + inner.height(), inner.bottom, stroke)
            x += g
        }
        c.restore()
        stroke.alpha = 160
        c.drawRoundRect(inner, r * 0.6f, r * 0.6f, stroke)
        stroke.alpha = 255
        stroke.strokeWidth = Theme.dpf(context, 3f)
        c.drawRoundRect(rect, r, r, stroke)
        // 가운데 금색 원 + ?
        fill.color = Theme.GOLD
        c.drawCircle(rect.centerX(), rect.centerY(), cardW * 0.22f, fill)
        qText.color = Theme.INK
        c.drawText(QUESTION, rect.centerX(), rect.centerY() + qText.textSize * 0.36f, qText)
    }

    private fun slotOf(p: Int): Int {
        for (k in 0 until n) if (slotPlayer[k] == p) return k
        return -1
    }

    companion object {
        const val MAX_CARDS = 6
        /** 연출 시간 (× 속도 배율) */
        const val INTRO_MS = 900L
        const val FLIP_MS = 420L
        const val SWAP_MS = 300L
        const val BADGE_MS = 450L
        const val REVEAL_GAP_MS = 380L
        val NUMBERS = arrayOf("1번", "2번", "3번", "4번", "5번", "6번")
        const val QUESTION = "?"
        const val S_INTRO = "이름 카드를 섞어 순서를 정해요"
        const val S_SHUFFLE = "섞는 중…"
        const val S_REVEAL = "1번부터 공개!"
        const val S_DONE = "순서가 정해졌어요"
        const val S_READY = "[섞기]를 누르면 시작해요"

        fun easeInOut(f: Float): Float = if (f < 0.5f) 2f * f * f else 1f - (-2f * f + 2f) * (-2f * f + 2f) / 2f
    }
}
