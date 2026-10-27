package com.siwoo.party.games.rps.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.rps.Foul
import com.siwoo.party.games.rps.Hand
import kotlin.math.abs
import kotlin.math.cos

/**
 * 가위바위보 판: MultiTouchZoneView 위에 겹쳐, 구역마다 똑같은 배치
 * (이름·상태 / 구호 / [가위][바위][보] 버튼 / 0.6초 막대, 공개 때는 큰 손 카드)를 그 구역 방향(위쪽 180°)으로 돌려 그린다.
 * 터치는 받지 않고 아래 구역이 받는다 → 화면이 hit() 로 버튼을 판정 (회전 고려).
 * 진행 연출(막대·뒤집기·도장)은 화면 프레임 루프가 tick(게임 시계)로 갱신한다 — onDraw 할당 없음.
 */
class RpsView(context: Context, private val zones: MultiTouchZoneView) : View(context) {

    /** 구역 표시 */
    enum class Mode { READY, CHANT, PICKED, FOUL, REVEAL, OUT }

    private var n = 0
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private val state = Array(MAX) { Mode.READY }
    private val cardHand = arrayOfNulls<Hand>(MAX)
    private val cardFoul = Array(MAX) { Foul.NONE }
    private val status = arrayOfNulls<String>(MAX)
    private val statusColor = IntArray(MAX)
    private val outRank = arrayOfNulls<String>(MAX)
    private val winner = BooleanArray(MAX)
    /** 탈락했지만 이번 판 카드는 아직 보여 준다 */
    private val showCard = BooleanArray(MAX)
    private var chant = ""
    private var chantHot = false

    private var openStart = -1L
    private var openDur = 600L
    private var openFrac = 0f
    /** "보!" 창이 열려 있는 동안 */
    private var windowOn = false
    private var flipStart = -1L
    private var flipDur = 500L
    private var flip = 1f
    private val stampAt = LongArray(MAX) { -1 }
    private val stampF = FloatArray(MAX) { 1f }
    /** 도장 찍히는 시간 (화면이 속도 배율을 곱해 넣는다) */
    var stampMs = 380L

    private val rect = RectF()
    private val card = RectF()
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
    private val chantPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    fun setPlayers(list: Array<String>, playerColors: IntArray) {
        n = list.size
        names = list
        colors = playerColors
        invalidate()
    }

    fun state(i: Int): Mode = state[i]

    /** 새 판 준비: 살아 있는 구역은 버튼, 탈락 구역은 도장만 */
    fun ready(alive: BooleanArray) {
        chant = "준비"
        chantHot = false
        openStart = -1
        windowOn = false
        openFrac = 0f
        flipStart = -1
        for (i in 0 until n) {
            showCard[i] = false
            if (alive[i]) {
                state[i] = Mode.READY
                status[i] = null
                cardHand[i] = null
                cardFoul[i] = Foul.NONE
            } else {
                state[i] = Mode.OUT
            }
        }
        invalidate()
    }

    /** 구호 ("가위…", "바위…") — 이때부터 버튼이 눌리면 반칙 */
    fun chant(word: String) {
        chant = word
        chantHot = false
        for (i in 0 until n) if (state[i] == Mode.READY) state[i] = Mode.CHANT
        invalidate()
    }

    /** "보!" — 창 열림 (now = 게임 시계) */
    fun open(word: String, now: Long, durMs: Long) {
        chant = word
        chantHot = true
        openStart = now
        openDur = durMs.coerceAtLeast(1)
        openFrac = 1f
        windowOn = true
        for (i in 0 until n) if (state[i] == Mode.READY) state[i] = Mode.CHANT
        invalidate()
    }

    /** 손을 냄 (무엇을 냈는지는 공개 때까지 숨김) */
    fun picked(i: Int) {
        state[i] = Mode.PICKED
        invalidate()
    }

    /** 이른 반칙 */
    fun earlyFoul(i: Int) {
        state[i] = Mode.FOUL
        invalidate()
    }

    /** 모든 구역 카드를 동시에 뒤집어 공개. hands/fouls = 이번 판 */
    fun reveal(hands: Array<Hand?>, fouls: Array<Foul>, now: Long, durMs: Long) {
        windowOn = false
        openStart = -1
        flipStart = now
        flipDur = durMs.coerceAtLeast(1)
        flip = 0f
        for (i in 0 until n) {
            if (state[i] == Mode.OUT) continue
            state[i] = Mode.REVEAL
            cardHand[i] = hands[i]
            cardFoul[i] = fouls[i]
            showCard[i] = true
        }
        invalidate()
    }

    /** 이름 줄 오른쪽 상태 글자 */
    fun setStatus(i: Int, text: String?, color: Int) {
        status[i] = text
        statusColor[i] = color
        invalidate()
    }

    /** 탈락 도장 (rankText 예: "3위") — 이번 판 카드는 그대로 두고 위에 찍는다 */
    fun eliminate(i: Int, rankText: String, now: Long) {
        state[i] = Mode.OUT
        outRank[i] = rankText
        stampAt[i] = now
        stampF[i] = 0f
        invalidate()
    }

    fun setWinner(i: Int) {
        winner[i] = true
        invalidate()
    }

    /** 막대·뒤집기·도장이 움직이는 중인지 */
    val busy: Boolean
        get() {
            if (openStart >= 0 || flipStart >= 0) return true
            for (i in 0 until n) if (stampAt[i] >= 0) return true
            return false
        }

    fun tick(now: Long): Boolean {
        var moving = false
        if (openStart >= 0) {
            val f = (now - openStart).toFloat() / openDur
            if (f >= 1f) {
                openFrac = 0f
                openStart = -1
            } else {
                openFrac = 1f - f
                moving = true
            }
        }
        if (flipStart >= 0) {
            val f = (now - flipStart).toFloat() / flipDur
            if (f >= 1f) {
                flip = 1f
                flipStart = -1
            } else {
                flip = f
                moving = true
            }
        }
        for (i in 0 until n) if (stampAt[i] >= 0) {
            val f = (now - stampAt[i]).toFloat() / stampMs.coerceAtLeast(1)
            if (f >= 1f) {
                stampF[i] = 1f
                stampAt[i] = -1
            } else {
                stampF[i] = f
                moving = true
            }
        }
        invalidate()
        return moving
    }

    // ── 배치 (구역 가운데 기준, 회전 전 좌표) ──

    private fun base(w: Float, h: Float) = minOf(h, w * 0.7f)
    private fun buttonW(w: Float, b: Float) = minOf(w * 0.25f, b * 0.38f)

    /**
     * 구역 i 안의 (x, y)(뷰 좌표)가 어느 버튼인지: 0 가위 / 1 바위 / 2 보, 아니면 -1.
     * 구역 방향으로 되돌려(회전) 같은 배치로 판정한다. 버튼 사이 틈의 절반까지는 가까운 버튼으로 본다.
     */
    fun hit(i: Int, x: Float, y: Float): Int {
        val z = zones.zone(i) ?: return -1
        val a = Math.toRadians(-z.rotation.toDouble())
        val dx0 = x - z.centerX
        val dy0 = y - z.centerY
        val dx = (dx0 * cos(a) - dy0 * kotlin.math.sin(a)).toFloat()
        val dy = (dx0 * kotlin.math.sin(a) + dy0 * cos(a)).toFloat()
        val b = base(z.width, z.height)
        val bw = buttonW(z.width, b)
        val gap = bw * 0.14f
        val by = b * 0.15f
        if (abs(dy - by) > bw / 2f + gap) return -1
        for (k in 0 until 3) {
            val bx = (k - 1) * (bw + gap)
            if (abs(dx - bx) <= bw / 2f + gap / 2f) return k
        }
        return -1
    }

    /** 구역 i 의 k 번 버튼 가운데 (뷰 좌표, 회전 반영) → out. 테스트·연출용 */
    fun buttonCenter(i: Int, k: Int, out: android.graphics.PointF) {
        val z = zones.zone(i) ?: return
        val b = base(z.width, z.height)
        val bw = buttonW(z.width, b)
        val dx = (k - 1) * (bw + bw * 0.14f)
        val dy = b * 0.15f
        val a = Math.toRadians(z.rotation.toDouble())
        out.set(z.centerX + (dx * cos(a) - dy * kotlin.math.sin(a)).toFloat(), z.centerY + (dx * kotlin.math.sin(a) + dy * cos(a)).toFloat())
    }

    override fun onDraw(c: Canvas) {
        for (i in 0 until n) {
            val z = zones.zone(i) ?: continue
            c.save()
            c.rotate(z.rotation, z.centerX, z.centerY)
            drawZone(c, i, z.centerX, z.centerY, z.width, z.height)
            c.restore()
        }
    }

    private fun drawZone(c: Canvas, i: Int, cx: Float, cy: Float, w: Float, h: Float) {
        val b = base(w, h)
        val bw = buttonW(w, b)
        val gap = bw * 0.14f
        val rowW = bw * 3f + gap * 2f
        val st = state[i]

        // 탈락 구역은 어둡게
        if (st == Mode.OUT) {
            val inset = Theme.dpf(context, 6f)
            rect.set(cx - w / 2f + inset, cy - h / 2f + inset, cx + w / 2f - inset, cy + h / 2f - inset)
            fill.color = 0x88000814.toInt()
            c.drawRoundRect(rect, b * 0.05f, b * 0.05f, fill)
        }

        // 이름 · 상태
        val nameY = cy - b * 0.36f
        namePaint.textSize = b * 0.09f
        namePaint.color = if (st == Mode.OUT) Theme.GRAY else colors[i]
        c.drawText(names[i], cx - rowW / 2f, nameY, namePaint)
        val stt = status[i]
        if (stt != null) {
            statusPaint.textSize = b * 0.075f
            statusPaint.color = statusColor[i]
            c.drawText(stt, cx + rowW / 2f, nameY, statusPaint)
        }

        when (st) {
            Mode.READY, Mode.CHANT, Mode.PICKED, Mode.FOUL -> drawButtons(c, i, cx, cy, b, bw, gap, rowW, st)
            Mode.REVEAL -> drawCard(c, i, cx, cy, b)
            else -> { // OUT
                if (showCard[i]) drawCard(c, i, cx, cy, b)
                drawStamp(c, i, cx, cy, b)
            }
        }
    }

    /** 구호 + 버튼 3개 + 0.6초 막대 */
    private fun drawButtons(c: Canvas, i: Int, cx: Float, cy: Float, b: Float, bw: Float, gap: Float, rowW: Float, st: Mode) {
        // 구호 / 내 상태
        chantPaint.textSize = b * (if (chantHot && st == Mode.CHANT) 0.2f else 0.16f)
        val line: String
        when (st) {
            Mode.PICKED -> { line = PICKED_TEXT; chantPaint.color = Theme.GREEN }
            Mode.FOUL -> { line = EARLY_TEXT; chantPaint.color = Theme.RED }
            else -> { line = chant; chantPaint.color = if (chantHot) Theme.GOLD else if (st == Mode.READY) Theme.TEXT_SUB else Theme.TEXT }
        }
        if (st == Mode.PICKED || st == Mode.FOUL) chantPaint.textSize = b * 0.12f
        c.drawText(line, cx, cy - b * 0.15f + chantPaint.textSize * 0.36f, chantPaint)

        val dim = st == Mode.PICKED || st == Mode.FOUL
        val hot = windowOn && st == Mode.CHANT
        val by = cy + b * 0.15f
        val bh = bw
        val r = bw * 0.16f
        for (k in 0 until 3) {
            val bx = cx + (k - 1) * (bw + gap)
            rect.set(bx - bw / 2f, by - bh / 2f, bx + bw / 2f, by + bh / 2f)
            // 그림자
            fill.color = 0x66000000
            rect.offset(0f, bh * 0.05f)
            c.drawRoundRect(rect, r, r, fill)
            rect.offset(0f, -bh * 0.05f)
            fill.color = if (hot) BTN_HOT else BTN
            fill.alpha = if (dim) 110 else 255
            c.drawRoundRect(rect, r, r, fill)
            fill.alpha = 255
            stroke.strokeWidth = b * (if (hot) 0.016f else 0.008f)
            stroke.color = if (hot) Theme.GOLD else 0x55FFFFFF
            if (dim) stroke.alpha = 70
            c.drawRoundRect(rect, r, r, stroke)
            HandArt.draw(c, HANDS[k], bx, by - bh * 0.09f, bh * 0.6f, art, if (dim) 90 else 255)
            labelPaint.textSize = bh * 0.17f
            labelPaint.color = if (hot) Theme.GOLD_LIGHT else Theme.TEXT
            labelPaint.alpha = if (dim) 90 else 255
            c.drawText(HANDS[k].label, bx, by + bh * 0.42f, labelPaint)
        }
        // 0.6초 막대 ("보!" 동안 줄어듦)
        val barY = cy + b * 0.41f
        val bt = b * 0.035f
        rect.set(cx - rowW / 2f, barY - bt / 2f, cx + rowW / 2f, barY + bt / 2f)
        fill.color = 0x44FFFFFF
        c.drawRoundRect(rect, bt / 2f, bt / 2f, fill)
        if (windowOn && openFrac > 0f && !dim) {
            rect.right = rect.left + rowW * openFrac
            fill.color = if (openFrac > 0.35f) Theme.GOLD else Theme.RED
            c.drawRoundRect(rect, bt / 2f, bt / 2f, fill)
        }
    }

    /** 공개 카드: 뒷면 → (가로로 접히며) → 앞면(손 모양 / 반칙) */
    private fun drawCard(c: Canvas, i: Int, cx: Float, cy: Float, b: Float) {
        val ch = b * 0.66f
        val cw = ch * 0.8f
        val ccy = cy + b * 0.07f
        val sx = abs(cos(Math.PI * flip)).toFloat().coerceAtLeast(0.02f)
        // 뒤집히며 살짝 커짐
        val grow = 1f + 0.14f * kotlin.math.sin(Math.PI * flip).toFloat()
        val face = flip >= 0.5f
        c.save()
        c.scale(sx * grow, grow, cx, ccy)
        card.set(cx - cw / 2f, ccy - ch / 2f, cx + cw / 2f, ccy + ch / 2f)
        val r = cw * 0.12f
        fill.color = 0x77000000
        card.offset(cw * 0.03f, ch * 0.04f)
        c.drawRoundRect(card, r, r, fill)
        card.offset(-cw * 0.03f, -ch * 0.04f)
        val foul = cardFoul[i]
        val hand = cardHand[i]
        if (!face) {
            fill.color = CARD_BACK
            c.drawRoundRect(card, r, r, fill)
            stroke.strokeWidth = cw * 0.03f
            stroke.color = Theme.GOLD
            stroke.alpha = 255
            c.drawRoundRect(card, r, r, stroke)
            chantPaint.textSize = ch * 0.4f
            chantPaint.color = Theme.GOLD
            c.drawText(QUESTION, cx, ccy + chantPaint.textSize * 0.36f, chantPaint)
        } else if (foul != Foul.NONE || hand == null) {
            fill.color = FOUL_FACE
            c.drawRoundRect(card, r, r, fill)
            stroke.strokeWidth = cw * 0.03f
            stroke.color = Theme.RED
            stroke.alpha = 255
            c.drawRoundRect(card, r, r, stroke)
            val d = cw * 0.22f
            val xy = ccy - ch * 0.1f
            stroke.strokeWidth = cw * 0.09f
            c.drawLine(cx - d, xy - d, cx + d, xy + d, stroke)
            c.drawLine(cx + d, xy - d, cx - d, xy + d, stroke)
            labelPaint.textSize = ch * 0.12f
            labelPaint.color = Theme.RED_DARK
            labelPaint.alpha = 255
            c.drawText(if (foul == Foul.EARLY) EARLY_CARD else LATE_CARD, cx, ccy + ch * 0.36f, labelPaint)
        } else {
            fill.color = Theme.PAPER
            c.drawRoundRect(card, r, r, fill)
            stroke.strokeWidth = cw * (if (winner[i]) 0.05f else 0.03f)
            stroke.color = if (winner[i]) Theme.GOLD else colors[i]
            stroke.alpha = 255
            c.drawRoundRect(card, r, r, stroke)
            HandArt.draw(c, hand, cx, ccy - ch * 0.08f, ch * 0.62f, art)
            labelPaint.textSize = ch * 0.13f
            labelPaint.color = Theme.INK
            labelPaint.alpha = 255
            c.drawText(hand.label, cx, ccy + ch * 0.38f, labelPaint)
        }
        c.restore()
        if (winner[i] && face) {
            fill.color = Theme.GOLD
            Ui.drawCrown(c, cx, ccy - ch * 0.45f, cw * 0.34f, path, fill)
        }
    }

    /** 탈락 도장 (빨간 테두리, 비스듬히, 찍힐 때 크게 → 제자리) */
    private fun drawStamp(c: Canvas, i: Int, cx: Float, cy: Float, b: Float) {
        val f = stampF[i]
        val s = 1f + 0.8f * (1f - f) * (1f - f)
        val sy = cy + b * 0.07f
        c.save()
        c.rotate(-12f, cx, sy)
        c.scale(s, s, cx, sy)
        val sw = b * 0.62f
        val sh = b * 0.3f
        rect.set(cx - sw / 2f, sy - sh / 2f, cx + sw / 2f, sy + sh / 2f)
        fill.color = 0xCC2A0710.toInt()
        fill.alpha = (255 * f.coerceAtLeast(0.3f)).toInt().coerceAtMost(220)
        c.drawRoundRect(rect, sh * 0.2f, sh * 0.2f, fill)
        fill.alpha = 255
        stroke.strokeWidth = b * 0.022f
        stroke.color = Theme.RED
        c.drawRoundRect(rect, sh * 0.2f, sh * 0.2f, stroke)
        chantPaint.textSize = sh * 0.46f
        chantPaint.color = Theme.RED
        val rk = outRank[i]
        if (rk != null) {
            c.drawText(OUT_TEXT, cx - sw * 0.12f, sy + chantPaint.textSize * 0.36f, chantPaint)
            chantPaint.textSize = sh * 0.36f
            chantPaint.color = Theme.TEXT
            c.drawText(rk, cx + sw * 0.28f, sy + chantPaint.textSize * 0.36f, chantPaint)
        } else {
            c.drawText(OUT_TEXT, cx, sy + chantPaint.textSize * 0.36f, chantPaint)
        }
        c.restore()
    }

    companion object {
        private const val MAX = 4
        private const val BTN = 0xFF2E4396.toInt()
        private const val BTN_HOT = 0xFF4361D8.toInt()
        private const val CARD_BACK = 0xFF1B2A6B.toInt()
        private const val FOUL_FACE = 0xFFFFE3E6.toInt()
        private const val QUESTION = "?"
        private const val PICKED_TEXT = "냈어요!"
        private const val EARLY_TEXT = "반칙! 너무 빨라요"
        private const val EARLY_CARD = "빨랐어요"
        private const val LATE_CARD = "늦었어요"
        private const val OUT_TEXT = "탈락"
        /** 버튼 순서 (각자 기준 왼쪽부터) */
        val HANDS = arrayOf(Hand.SCISSORS, Hand.ROCK, Hand.PAPER)
    }
}
