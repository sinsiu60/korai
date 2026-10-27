package com.siwoo.party.common.cards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 카드 한 장의 화면 상태 (CardTableView 가 그린다). 값은 직접 바꿔도 되고(다음 그리기에 반영 — 바꾼 뒤
 * [CardTableView.invalidate]), 움직임은 CardTableView 의 moveTo/flip/liftTo 로.
 *
 * - (x, y) = 카드 가운데(px), rotation = 도(시계 +), scale = 크기 배율(1 = CardBitmaps 폭)
 * - lift = 위로 떠오른 px ("낼 수 있는 카드" 표시), highlight = 금색 테두리
 * - stack = 아래에 깔린 두께 장수(0..6, 덱 더미처럼 보이게)
 * - tag = 게임이 붙이는 표시 (주인 번호 등)
 */
class CardSprite internal constructor(private val host: CardTableView, card: Card) {

    /** 카드 (바꾸면 그림을 미리 만들어 둔다) */
    var card: Card = card
        set(v) { field = v; host.warm(v); host.invalidate() }
    var x = 0f
    var y = 0f
    var rotation = 0f
    var scale = 1f
    /** 앞면이 보이는지 (뒤집기 연출 중에는 절반 지점에서 바뀐다) */
    var faceUp = true
    /** 0..1 */
    var alpha = 1f
    var lift = 0f
    var highlight = false
    var visible = true
    /** false 면 터치 판정에서 빠진다 */
    var tappable = true
    var stack = 0
    var tag = 0

    val isMoving: Boolean get() = mOn
    val isFlipping: Boolean get() = fOn
    val isAnimating: Boolean get() = mOn || fOn || lOn

    // ── 이동 ──
    internal var mOn = false
    internal var mStart = 0L
    internal var mDur = 0L
    internal var x0 = 0f; internal var y0 = 0f; internal var r0 = 0f; internal var s0 = 1f
    internal var x1 = 0f; internal var y1 = 0f; internal var r1 = 0f; internal var s1 = 1f
    internal var mEnd: (() -> Unit)? = null

    // ── 뒤집기 ──
    internal var fOn = false
    internal var fStart = 0L
    internal var fDur = 0L
    internal var fFrom = true
    internal var fEnd: (() -> Unit)? = null
    /** 그릴 때 가로·세로 배율 (뒤집기 중) */
    internal var flipSx = 1f
    internal var flipSy = 1f

    // ── 떠오름 ──
    internal var lOn = false
    internal var lStart = 0L
    internal var lDur = 0L
    internal var l0 = 0f
    internal var l1 = 0f
}

/**
 * 카드 테이블: 카드 여러 장을 CardBitmaps 그림으로 그리고 이동·뒤집기·떠오름 연출을 한다.
 * 원카드·도둑잡기·블랙잭 등 카드 게임 공용. 손패 부채꼴 자리는 [FanLayout] 으로 계산해 [moveToFan].
 *
 * - 그리기 순서 = [sprites] 순서 (뒤 번호가 위). [bringToFront] 로 바꿈.
 * - 연출 시간은 부르는 쪽이 정한다 (게임은 t(ms) 로 속도 배율 적용해서 넘길 것).
 * - 움직이는 것이 있을 때만 프레임(postOnAnimation)이 돈다. 멈춰 있으면 다시 그리지 않는다.
 * - 일시정지: 게임의 onGamePause 에서 [pause], onGameResume 에서 [resume] (남은 시간 그대로 이어짐).
 * - 같은 카드에 새 moveTo/flip 을 걸면 이전 것은 콜백 없이 대체된다.
 * - 창에서 떨어지면(onDetachedFromWindow) 모든 연출을 끝 상태로 맞추고 콜백은 버린다 (Activity 참조 누수 방지).
 *   CardBitmaps 는 만든 쪽(게임) 것 — 게임이 끝날 때 bitmaps.release() 는 게임이 부른다.
 * - 터치: 카드를 누르면(누른 카드와 뗀 카드가 같을 때) [onCardTapped] (sprites 번호, 위 카드 먼저).
 * - onDraw 에서 객체를 만들지 않는다 (카드 그림은 올릴 때 미리 만들어 둠).
 */
class CardTableView(context: Context, bitmaps: CardBitmaps) : View(context) {

    /** 카드 그림 (바꾸면 다시 그림). 소유·release 는 게임 쪽 */
    var bitmaps: CardBitmaps = bitmaps
        set(v) { field = v; for (s in list) warm(s.card); invalidate() }

    private val list = ArrayList<CardSprite>()

    /** 그리는 순서대로 (0 = 맨 아래) */
    val sprites: List<CardSprite> get() = list

    /** 카드 누름: sprites 번호 */
    var onCardTapped: ((Int) -> Unit)? = null
    var tapEnabled = true

    /** 기본 카드 폭·높이(px, scale 1) */
    val cardWidth: Float get() = bitmaps.cardWidthPx.toFloat()
    val cardHeight: Float get() = bitmaps.cardHeightPx.toFloat()

    var paused = false
        private set
    private var pauseAt = 0L
    private var pausedTotal = 0L
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var pending = ArrayList<() -> Unit>()
    private var running = ArrayList<() -> Unit>()
    private var downIndex = -1

    private val density = context.resources.displayMetrics.density
    private val rect = RectF()
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x000000; alpha = 70 }
    private val edgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B2766.toInt() }
    private val edgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFF4EEDC.toInt() }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Theme.GOLD; alpha = 90 }
    private val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Theme.GOLD }

    /** 일시정지 시간을 뺀 연출 시계 */
    private fun now(): Long = (if (paused) pauseAt else SystemClock.uptimeMillis()) - pausedTotal

    internal fun warm(card: Card) {
        bitmaps.face(card)
        bitmaps.back()
    }

    // ── 카드 올리기·빼기 ──

    fun addSprite(card: Card, x: Float, y: Float, rotation: Float = 0f, faceUp: Boolean = true, scale: Float = 1f): CardSprite {
        val s = CardSprite(this, card)
        s.x = x; s.y = y; s.rotation = rotation; s.faceUp = faceUp; s.scale = scale
        warm(card)
        list.add(s)
        invalidate()
        return s
    }

    /** 카드 빼기 (진행 중 연출은 콜백 없이 버림) */
    fun removeSprite(s: CardSprite) {
        drop(s)
        list.remove(s)
        invalidate()
    }

    /** 전부 빼기 */
    fun clearSprites() {
        for (s in list) drop(s)
        list.clear()
        invalidate()
    }

    fun indexOf(s: CardSprite): Int = list.indexOf(s)

    /** 맨 위로 (그리기·터치 순서) */
    fun bringToFront(s: CardSprite) {
        if (list.remove(s)) list.add(s)
        invalidate()
    }

    /** 맨 아래로 */
    fun sendToBack(s: CardSprite) {
        if (list.remove(s)) list.add(0, s)
        invalidate()
    }

    /** sprites 순서 안에서 index 자리로 */
    fun moveToIndex(s: CardSprite, index: Int) {
        if (list.remove(s)) list.add(index.coerceIn(0, list.size), s)
        invalidate()
    }

    // ── 연출 ──

    /** (x, y, rotation) 으로 이동 */
    fun moveTo(s: CardSprite, x: Float, y: Float, rotation: Float, durationMs: Long, onEnd: (() -> Unit)? = null) {
        animateTo(s, x, y, rotation, s.scale, durationMs, 0L, onEnd)
    }

    /** 이동 + 크기 + 시작 지연 (delayMs 동안은 제자리) */
    fun animateTo(s: CardSprite, x: Float, y: Float, rotation: Float, scale: Float, durationMs: Long, delayMs: Long = 0L, onEnd: (() -> Unit)? = null) {
        s.x0 = s.x; s.y0 = s.y; s.r0 = s.rotation; s.s0 = s.scale
        s.x1 = x; s.y1 = y; s.r1 = rotation; s.s1 = scale
        s.mStart = now() + delayMs.coerceAtLeast(0L)
        s.mDur = durationMs.coerceAtLeast(0L)
        s.mEnd = onEnd
        s.mOn = true
        ensureLoop()
    }

    /** 뒤집기: 가로로 접혔다 펴지며 절반에서 면이 바뀐다 */
    fun flip(s: CardSprite, durationMs: Long, delayMs: Long = 0L, onEnd: (() -> Unit)? = null) {
        if (s.fOn) { // 뒤집는 중이면 원래 면 기준으로 다시
            s.faceUp = s.fFrom
        }
        s.fFrom = s.faceUp
        s.fStart = now() + delayMs.coerceAtLeast(0L)
        s.fDur = durationMs.coerceAtLeast(0L)
        s.fEnd = onEnd
        s.fOn = true
        ensureLoop()
    }

    /** faceUp 쪽이 보이게 뒤집기 (이미 그 면이면 연출 없이 다음 프레임에 onEnd) */
    fun flipTo(s: CardSprite, faceUp: Boolean, durationMs: Long, delayMs: Long = 0L, onEnd: (() -> Unit)? = null) {
        val target = if (s.fOn) !s.fFrom else s.faceUp
        if (target == faceUp) {
            if (onEnd != null) { pending.add(onEnd); ensureLoop() }
            return
        }
        flip(s, durationMs, delayMs, onEnd)
    }

    /** 떠오름 높이(px)를 부드럽게 바꿈 (낼 수 있는 카드 표시 등) */
    fun liftTo(s: CardSprite, lift: Float, durationMs: Long) {
        s.l0 = s.lift
        s.l1 = lift
        s.lStart = now()
        s.lDur = durationMs.coerceAtLeast(0L)
        s.lOn = true
        ensureLoop()
    }

    /**
     * 손패를 부채꼴 자리로: sprites[i] → fan 의 i 번째 자리 (fan.layout 을 먼저 부를 것).
     * staggerMs 간격으로 차례로 출발 (나눠 주기 연출). 모두 도착하면 onEnd 한 번.
     * scale 은 fan.cardWidth / 기본 카드 폭.
     */
    fun moveToFan(hand: List<CardSprite>, fan: FanLayout, durationMs: Long, staggerMs: Long = 0L, onEnd: (() -> Unit)? = null) {
        val n = minOf(hand.size, fan.count)
        if (n == 0) {
            if (onEnd != null) { pending.add(onEnd); ensureLoop() }
            return
        }
        val sc = if (cardWidth > 0f) fan.cardWidth / cardWidth else 1f
        var left = n
        val done: () -> Unit = { left--; if (left == 0) onEnd?.invoke() }
        for (i in 0 until n) {
            animateTo(hand[i], fan.xs[i], fan.ys[i], fan.rots[i], sc, durationMs, i * staggerMs, done)
        }
    }

    /** 무엇이든 움직이는 중인지 */
    val isAnimating: Boolean
        get() {
            for (i in 0 until list.size) if (list[i].isAnimating) return true
            return false
        }

    /** 연출 일시정지 (게임 onGamePause) */
    fun pause() {
        if (paused) return
        pauseAt = SystemClock.uptimeMillis()
        paused = true
        stopLoop()
    }

    /** 이어서 (게임 onGameResume) */
    fun resume() {
        if (!paused) return
        pausedTotal += SystemClock.uptimeMillis() - pauseAt
        paused = false
        if (isAnimating || pending.isNotEmpty()) ensureLoop()
    }

    /**
     * 모든 연출을 끝 상태로 바로 맞춤. runCallbacks = true 면 onEnd 들을 지금 부른다 (건너뛰기),
     * false 면 버린다 (정리).
     */
    fun cancelAll(runCallbacks: Boolean = false) {
        for (i in 0 until list.size) finish(list[i])
        if (!runCallbacks) pending.clear()
        stopLoop()
        invalidate()
        if (runCallbacks) runPending()
    }

    /** 이 카드의 연출만 끝 상태로 (runCallbacks 면 onEnd 를 지금) */
    fun cancel(s: CardSprite, runCallbacks: Boolean = false) {
        val m = s.mEnd
        val f = s.fEnd
        s.mEnd = null; s.fEnd = null
        finish(s)
        invalidate()
        if (runCallbacks) { m?.invoke(); f?.invoke() }
    }

    private fun finish(s: CardSprite) {
        if (s.mOn) {
            s.mOn = false
            s.x = s.x1; s.y = s.y1; s.rotation = s.r1; s.scale = s.s1
            s.mEnd?.let { pending.add(it) }
            s.mEnd = null
        }
        if (s.fOn) {
            s.fOn = false
            s.faceUp = !s.fFrom
            s.flipSx = 1f; s.flipSy = 1f
            s.fEnd?.let { pending.add(it) }
            s.fEnd = null
        }
        if (s.lOn) {
            s.lOn = false
            s.lift = s.l1
        }
    }

    private fun drop(s: CardSprite) {
        s.mEnd = null; s.fEnd = null
        if (s.mOn || s.fOn || s.lOn) finish(s)
    }

    // ── 프레임 ──

    private fun ensureLoop() {
        if (paused || loopOn) return
        loopOn = true
        postOnAnimation(frame)
    }


    private fun stopLoop() {
        loopOn = false
        removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val t = now()
        var busy = false
        for (i in 0 until list.size) if (step(list[i], t)) busy = true
        invalidate()
        if (busy) postOnAnimation(frame) else loopOn = false
        runPending()
    }

    private fun runPending() {
        if (pending.isEmpty()) return
        val r = pending
        pending = running
        running = r
        for (i in 0 until r.size) r[i]()
        r.clear()
    }

    /** 한 카드 진행. 아직 움직이면 true */
    private fun step(s: CardSprite, t: Long): Boolean {
        var busy = false
        if (s.mOn) {
            val e = t - s.mStart
            if (e >= 0) {
                val p = if (s.mDur <= 0) 1f else (e.toFloat() / s.mDur).coerceAtMost(1f)
                val q = easeOut(p)
                s.x = s.x0 + (s.x1 - s.x0) * q
                s.y = s.y0 + (s.y1 - s.y0) * q
                s.rotation = s.r0 + (s.r1 - s.r0) * q
                s.scale = s.s0 + (s.s1 - s.s0) * q
                if (p >= 1f) {
                    s.mOn = false
                    s.mEnd?.let { pending.add(it) }
                    s.mEnd = null
                }
            }
            if (s.mOn) busy = true
        }
        if (s.fOn) {
            val e = t - s.fStart
            if (e >= 0) {
                val p = if (s.fDur <= 0) 1f else (e.toFloat() / s.fDur).coerceAtMost(1f)
                val a = p * PI.toFloat()
                s.flipSx = abs(cos(a))
                s.flipSy = 1f + 0.08f * sin(a)
                s.faceUp = if (p < 0.5f) s.fFrom else !s.fFrom
                if (p >= 1f) {
                    s.fOn = false
                    s.flipSx = 1f; s.flipSy = 1f
                    s.faceUp = !s.fFrom
                    s.fEnd?.let { pending.add(it) }
                    s.fEnd = null
                }
            }
            if (s.fOn) busy = true
        }
        if (s.lOn) {
            val e = t - s.lStart
            val p = if (s.lDur <= 0) 1f else (e.toFloat() / s.lDur).coerceIn(0f, 1f)
            s.lift = s.l0 + (s.l1 - s.l0) * easeOut(p)
            if (p >= 1f) s.lOn = false else busy = true
        }
        return busy
    }

    private fun easeOut(p: Float): Float {
        val u = 1f - p
        return 1f - u * u * u
    }

    // ── 터치 ──

    /** (x, y) 아래 카드 번호 (위 카드 먼저), 없으면 -1. 안 보이거나 tappable=false 인 카드는 건너뜀 */
    fun hit(x: Float, y: Float): Int {
        val w = cardWidth
        val h = cardHeight
        for (i in list.size - 1 downTo 0) {
            val s = list[i]
            if (!s.visible || !s.tappable || s.alpha < 0.05f) continue
            if (FanLayout.contains(x, y, s.x, s.y - s.lift, s.rotation, w * s.scale, h * s.scale)) return i
        }
        return -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!tapEnabled) return false
                downIndex = hit(e.x, e.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val i = if (tapEnabled) hit(e.x, e.y) else -1
                val d = downIndex
                downIndex = -1
                if (i >= 0 && i == d) {
                    performClick()
                    onCardTapped?.invoke(i)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                downIndex = -1
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    // ── 그리기 (할당 없음) ──

    override fun onDraw(c: Canvas) {
        for (i in 0 until list.size) {
            val s = list[i]
            if (!s.visible || s.alpha <= 0f || s.scale <= 0f) continue
            drawSprite(c, s)
        }
    }

    private fun drawSprite(c: Canvas, s: CardSprite) {
        val bmp = if (s.faceUp) bitmaps.face(s.card) else bitmaps.back()
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val hw = bw / 2f
        val hh = bh / 2f
        val r = bitmaps.cornerRadius
        val a = (s.alpha.coerceIn(0f, 1f) * 255f).toInt()
        c.save()
        c.translate(s.x, s.y - s.lift)
        c.rotate(s.rotation)
        c.scale(s.scale * s.flipSx, s.scale * s.flipSy)
        rect.set(-hw, -hh, hw, hh)
        // 그림자 (떠오를수록 멀리)
        val sh = 2f * density + s.lift * 0.12f
        c.translate(sh * 0.4f, sh)
        shadowPaint.alpha = 70 * a / 255
        c.drawRoundRect(rect, r, r, shadowPaint)
        c.translate(-sh * 0.4f, -sh)
        // 더미 두께
        val layers = s.stack.coerceIn(0, 6)
        if (layers > 0) {
            val d = 1.6f * density
            edgeFill.alpha = a
            edgeStroke.alpha = a
            edgeStroke.strokeWidth = density
            for (k in layers downTo 1) {
                c.translate(0f, d * k)
                c.drawRoundRect(rect, r, r, edgeFill)
                c.drawRoundRect(rect, r, r, edgeStroke)
                c.translate(0f, -d * k)
            }
        }
        bmpPaint.alpha = a
        c.drawBitmap(bmp, -hw, -hh, bmpPaint)
        if (s.highlight) {
            glowPaint.strokeWidth = 7f * density
            glowPaint.alpha = 90 * a / 255
            c.drawRoundRect(rect, r, r, glowPaint)
            hiPaint.strokeWidth = 2.5f * density
            hiPaint.alpha = a
            c.drawRoundRect(rect, r, r, hiPaint)
        }
        c.restore()
    }

    // ── 수명 ──

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isAnimating) ensureLoop()
    }

    override fun onDetachedFromWindow() {
        // 화면에서 떨어지면 정리: 연출은 끝 상태로, 콜백(게임 Activity 를 잡고 있을 수 있음)은 버림
        cancelAll(runCallbacks = false)
        super.onDetachedFromWindow()
    }
}
