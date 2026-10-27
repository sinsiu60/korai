package com.siwoo.party.games.sevens.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.CardBitmaps
import com.siwoo.party.common.cards.Suit
import com.siwoo.party.common.ui.Theme

/**
 * 7 이어붙이기 판: 무늬별 줄 4개(♠ ♥ ♦ ♣) × A..K 13칸, 7 은 가운데.
 * - 놓인 카드: 작은 종이 카드(숫자 + 무늬). 빈 칸은 흐린 테두리와 숫자, 7 칸은 금색 점선.
 * - 공개 카드(탈락자 카드): 반투명 카드 + 원래 주인 색 테두리 → 자리가 나면 [place] 로 진하게.
 * - 놓을 자리 표시: 지금 사람이 낼 수 있는 자리(약한 금색), 고른 카드 자리(진한 금색).
 * - 막 놓인 칸은 잠깐 반짝인다 (그동안만 postOnAnimation, 멈추면 다시 그리지 않음).
 * 판 규칙은 SevensGame 에 있고, 여기는 화면에 보일 상태만 따로 들고 있다 (날아가는 카드가 내려앉은 뒤에 반영하려고).
 * onDraw 에서 객체를 만들지 않는다.
 */
class BoardView(context: Context) : View(context) {

    /** 칸 상태: 0 빈 칸, 1 놓임, 2 공개 카드 (card.id 순) */
    private val state = ByteArray(Card.STANDARD_COUNT)
    private val ghostColor = IntArray(Card.STANDARD_COUNT)
    /** 놓을 자리 표시: 0 없음, 1 낼 수 있음, 2 고른 카드 */
    private val target = ByteArray(Card.STANDARD_COUNT)
    private val pulseAt = LongArray(Card.STANDARD_COUNT)

    /** 반짝임 시간 (게임이 속도 배율을 곱해 넣는다) */
    var pulseMs = 650L

    private var loopOn = false
    private var paused = false
    private val frame = Runnable { onFrame() }

    private val density = context.resources.displayMetrics.density

    // ── 배치 (px) ──
    /** 작은 카드 폭·높이 */
    var miniW = 0f
        private set
    var miniH = 0f
        private set
    private var colW = 0f
    private var x0 = 0f
    private var labelX = 0f
    private val rowY = FloatArray(4)
    private var rowGap = 0f
    private val panel = RectF()
    private val rect = RectF()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val felt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        dash.pathEffect = DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
    }

    // ── 상태 바꾸기 ──

    /** 카드를 판에 놓는다 (공개 카드였으면 진하게). pulse 면 반짝 */
    fun place(card: Card, pulse: Boolean = true) {
        state[card.id] = 1
        target[card.id] = 0
        if (pulse && !paused) {
            pulseAt[card.id] = SystemClock.uptimeMillis()
            ensureLoop()
        }
        invalidate()
    }

    /** 공개 카드로 표시 (주인 색 테두리) */
    fun ghost(card: Card, ownerColor: Int) {
        state[card.id] = 2
        ghostColor[card.id] = ownerColor
        invalidate()
    }

    fun isPlaced(card: Card): Boolean = state[card.id].toInt() == 1
    fun isGhost(card: Card): Boolean = state[card.id].toInt() == 2

    /** 판에 놓인 장수 (화면 기준) */
    val placedCount: Int
        get() {
            var n = 0
            for (i in 0 until Card.STANDARD_COUNT) if (state[i].toInt() == 1) n++
            return n
        }

    /** 놓을 자리 표시 (1 = 낼 수 있음, 2 = 고른 카드) */
    fun setTarget(card: Card, level: Int) {
        val v = level.coerceIn(0, 2).toByte()
        if (target[card.id] == v) return
        target[card.id] = v
        invalidate()
    }

    /** 놓을 자리 표시 모두 지우기 (다음 사람에게 넘기기 전에 — 손패 정보가 남지 않게) */
    fun clearTargets() {
        var any = false
        for (i in target.indices) if (target[i].toInt() != 0) { target[i] = 0; any = true }
        if (any) invalidate()
    }

    /** 표시 중인 자리 표시 수 (테스트용) */
    val targetCount: Int
        get() {
            var n = 0
            for (i in target.indices) if (target[i].toInt() != 0) n++
            return n
        }

    fun pause() {
        paused = true
        for (i in pulseAt.indices) pulseAt[i] = 0L
        stopLoop()
        invalidate()
    }

    fun resume() {
        paused = false
    }

    /** 칸 가운데 (이 View 좌표) */
    fun slotX(rank: Int): Float = x0 + colW * (rank - 1) + colW / 2f
    fun slotY(suit: Suit): Float = rowY[suit.ordinal]

    // ── 배치 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 6f * density
        panel.set(pad * 0.3f, pad * 0.3f, w - pad * 0.3f, h - pad * 0.3f)
        rowGap = 5f * density
        val innerH = h - pad * 2f
        // 높이 기준 작은 카드 크기 → 폭이 모자라면 폭 기준
        var ch = (innerH - rowGap * 3f) / 4f
        var cw = ch / CardBitmaps.RATIO
        val labelW = (cw * 1.05f).coerceAtLeast(18f * density)
        val maxCol = (w - pad * 2f - labelW) / 13f
        if (cw + 3f * density > maxCol) {
            cw = maxCol - 3f * density
            ch = cw * CardBitmaps.RATIO
        }
        miniW = cw
        miniH = ch
        colW = cw + 3f * density
        val total = labelW + colW * 13f
        val left = (w - total) / 2f
        labelX = left + labelW / 2f
        x0 = left + labelW
        val blockH = ch * 4f + rowGap * 3f
        val top = (h - blockH) / 2f
        for (r in 0 until 4) rowY[r] = top + ch / 2f + r * (ch + rowGap)
        felt.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), FELT_TOP, FELT_BOTTOM, Shader.TileMode.CLAMP)
        stroke.strokeWidth = 1.2f * density
    }

    // ── 반짝임 루프 ──

    private fun ensureLoop() {
        if (loopOn || paused) return
        loopOn = true
        postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        var any = false
        for (i in pulseAt.indices) {
            val t = pulseAt[i]
            if (t == 0L) continue
            if (now - t >= pulseMs) pulseAt[i] = 0L else any = true
        }
        invalidate()
        if (any) postOnAnimation(frame) else loopOn = false
    }

    /** 반짝이는 칸이 있는지 (테스트용) */
    val isAnimating: Boolean get() = loopOn

    override fun onDetachedFromWindow() {
        stopLoop()
        super.onDetachedFromWindow()
    }

    // ── 그리기 (할당 없음) ──

    override fun onDraw(c: Canvas) {
        if (miniW <= 0f) return
        val r = 14f * density
        c.drawRoundRect(panel, r, r, felt)
        stroke.color = Theme.GOLD
        stroke.alpha = 120
        stroke.strokeWidth = 1.6f * density
        c.drawRoundRect(panel, r, r, stroke)
        stroke.alpha = 255
        stroke.strokeWidth = 1.2f * density
        val now = SystemClock.uptimeMillis()
        val suits = SUITS
        for (row in 0 until 4) {
            val suit = suits[row]
            val cy = rowY[row]
            // 줄 바탕
            fill.color = LANE
            rect.set(x0 - colW * 0.05f, cy - miniH / 2f - rowGap * 0.35f, x0 + colW * 13.05f, cy + miniH / 2f + rowGap * 0.35f)
            c.drawRoundRect(rect, miniW * 0.2f, miniW * 0.2f, fill)
            // 무늬 표
            val lr = miniW * 0.46f
            fill.color = Theme.PAPER
            c.drawCircle(labelX, cy, lr, fill)
            fill.color = SuitArt.color(suit)
            SuitArt.draw(c, suit, labelX, cy, lr * 1.15f, fill)
            for (rank in 1..13) drawSlot(c, Card.of(suit, rank), slotX(rank), cy, now)
        }
    }

    private fun drawSlot(c: Canvas, card: Card, cx: Float, cy: Float, now: Long) {
        val w = miniW
        val h = miniH
        val st = state[card.id].toInt()
        val tg = target[card.id].toInt()
        val corner = w * 0.12f
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        // 반짝임 (막 놓인 칸)
        var pulse = 0f
        val pt = pulseAt[card.id]
        if (pt != 0L) {
            val p = ((now - pt).toFloat() / pulseMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            pulse = 1f - p
        }
        if (st == 0) {
            if (tg > 0) {
                fill.color = Theme.GOLD
                fill.alpha = if (tg == 2) 150 else 60
                c.drawRoundRect(rect, corner, corner, fill)
                fill.alpha = 255
                stroke.color = if (tg == 2) Theme.GOLD_LIGHT else Theme.GOLD
                stroke.strokeWidth = (if (tg == 2) 2.6f else 1.8f) * density
                c.drawRoundRect(rect, corner, corner, stroke)
                stroke.strokeWidth = 1.2f * density
            } else if (card.rank == 7) {
                dash.color = Theme.GOLD
                dash.alpha = 170
                dash.strokeWidth = 1.4f * density
                c.drawRoundRect(rect, corner, corner, dash)
            } else {
                stroke.color = EMPTY_EDGE
                c.drawRoundRect(rect, corner, corner, stroke)
            }
            text.color = if (card.rank == 7 || tg > 0) Theme.GOLD else EMPTY_TEXT
            text.alpha = if (tg == 2) 255 else if (card.rank == 7 || tg > 0) 200 else 110
            text.textSize = w * (if (card.rank == 10) 0.38f else 0.46f)
            c.drawText(card.label, cx, cy + text.textSize * 0.36f, text)
            text.alpha = 255
            return
        }
        val ghost = st == 2
        val a = if (ghost) 120 else 255
        c.save()
        if (pulse > 0f) {
            val sc = 1f + 0.22f * pulse
            c.scale(sc, sc, cx, cy)
        }
        // 그림자
        fill.color = 0xFF000000.toInt()
        fill.alpha = if (ghost) 30 else 70
        rect.offset(0.8f * density, 1.6f * density)
        c.drawRoundRect(rect, corner, corner, fill)
        rect.offset(-0.8f * density, -1.6f * density)
        // 종이
        fill.color = Theme.PAPER
        fill.alpha = a
        c.drawRoundRect(rect, corner, corner, fill)
        stroke.color = PAPER_EDGE
        stroke.alpha = a
        c.drawRoundRect(rect, corner, corner, stroke)
        stroke.alpha = 255
        // 숫자 + 무늬
        val ink = SuitArt.color(card.suit!!)
        text.color = ink
        text.alpha = a
        text.textSize = w * (if (card.rank == 10) 0.42f else 0.52f)
        c.drawText(card.label, cx, cy - h * 0.06f, text)
        text.alpha = 255
        fill.color = ink
        fill.alpha = a
        SuitArt.draw(c, card.suit!!, cx, cy + h * 0.24f, w * 0.42f, fill)
        fill.alpha = 255
        if (ghost) {
            // 원래 주인 색 점선 테두리
            dash.color = ghostColor[card.id]
            dash.alpha = 255
            dash.strokeWidth = 2f * density
            c.drawRoundRect(rect, corner, corner, dash)
        } else if (card.rank == 7) {
            stroke.color = Theme.GOLD
            stroke.strokeWidth = 1.8f * density
            c.drawRoundRect(rect, corner, corner, stroke)
            stroke.strokeWidth = 1.2f * density
        }
        if (pulse > 0f) {
            stroke.color = Theme.GOLD_LIGHT
            stroke.alpha = (255 * pulse).toInt()
            stroke.strokeWidth = 3f * density
            c.drawRoundRect(rect, corner, corner, stroke)
            stroke.alpha = 255
            stroke.strokeWidth = 1.2f * density
        }
        c.restore()
    }

    companion object {
        private val SUITS = Suit.values()
        private const val FELT_TOP = 0xFF146B45.toInt()
        private const val FELT_BOTTOM = 0xFF0B4A2F.toInt()
        private const val LANE = 0x33000000
        private const val EMPTY_EDGE = 0x55FFFFFF
        private const val EMPTY_TEXT = 0xFFFFFFFF.toInt()
        private const val PAPER_EDGE = 0xFFB8A77A.toInt()
    }
}
