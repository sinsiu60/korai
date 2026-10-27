package com.siwoo.party.games.yut.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.yut.YutResult

/**
 * 화면 오른쪽 위: 지금 차례(색 동그라미 + 이름) + 안내 한 줄 + "이동 대기열" 결과 칩.
 * - 던진 결과가 칩으로 톡 쌓인다. 고른 칩은 금색, 지금 쓸 수 없는 칩(빽도 등)은 흐리게
 * - 쓸 수 없는 결과를 버릴 때: 칩에 붉은 줄이 그어지며 흐려져 사라진다
 * 칩을 누르면 listener 로 알림. 글자는 값이 바뀔 때(setter)만 만든다. onDraw 에서는 객체를 만들지 않는다.
 */
class QueueView(context: Context) : View(context) {

    interface Listener {
        fun onChipTapped(index: Int)
    }

    var listener: Listener? = null
    var timers: GameTimers? = null

    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    private var initColor = Theme.TEXT
    private var hint = ""

    private val chips = ArrayList<YutResult>()
    private val chipStart = LongArray(MAX_CHIPS)
    private val usable = BooleanArray(MAX_CHIPS) { true }
    /** 고른 칩 (-1 = 없음) */
    var selected = -1
        private set
    private var discardStart = 0L
    private var discardDur = 0L
    private var discarding = false

    private val rect = RectF()
    private val box = RectF()
    private val chipRect = RectF()
    private var chipW = 0f
    private var chipH = 0f
    private var chipGap = 0f
    private var perRow = 3

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD_LIGHT }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0x80B8C3F0.toInt() }

    private var looping = false
    private val frame = Runnable { onFrame() }

    init {
        isClickable = true
        contentDescription = "이동 대기열"
    }

    private fun now(): Long = timers?.now() ?: SystemClock.uptimeMillis()

    /** 지금 차례 사람 */
    fun setPlayer(playerName: String, playerColor: Int) {
        name = playerName
        initial = playerName.take(1)
        color = playerColor
        initColor = Theme.textOn(playerColor)
        fitName()
        invalidate()
    }

    fun setHint(text: String) {
        if (hint == text) return
        hint = text
        fitHint()
        invalidate()
    }

    val hintShown: String get() = hint
    val chipCount: Int get() = chips.size
    fun chipAt(i: Int): YutResult = chips[i]

    /** 결과 칩 추가 (톡 나타남) */
    fun push(r: YutResult) {
        if (chips.size >= MAX_CHIPS) return
        finishDiscard()
        chips.add(r)
        chipStart[chips.size - 1] = now()
        usable[chips.size - 1] = true
        relayoutChips()
        ensureRunning()
    }

    /** 칩 하나 빼기 (쓴 결과) */
    fun removeAt(i: Int) {
        if (i !in chips.indices) return
        chips.removeAt(i)
        for (k in i until chips.size) { chipStart[k] = chipStart[k + 1]; usable[k] = usable[k + 1] }
        selected = -1
        relayoutChips()
        invalidate()
    }

    /** 남은 칩을 모두 버림 (붉은 줄 + 흐려지며 사라짐) */
    fun discardAll(durMs: Long) {
        if (chips.isEmpty()) return
        discarding = true
        selected = -1
        discardStart = now()
        discardDur = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    /** 버리는 중이던 칩 정리 */
    private fun finishDiscard() {
        if (!discarding) return
        discarding = false
        chips.clear()
        relayoutChips()
    }

    fun clear() {
        discarding = false
        chips.clear()
        selected = -1
        relayoutChips()
        invalidate()
    }

    fun setSelected(i: Int) {
        if (selected == i) return
        selected = i
        invalidate()
    }

    /** 칩 i 를 지금 쓸 수 있는지 (흐리게 표시) */
    fun setUsable(i: Int, v: Boolean) {
        if (i !in 0 until MAX_CHIPS || usable[i] == v) return
        usable[i] = v
        invalidate()
    }

    /** 칩 가운데 (이 뷰 좌표) */
    fun chipCenter(i: Int, out: PointF) {
        chipBounds(i, chipRect)
        out.set(chipRect.centerX(), chipRect.centerY())
    }

    val isAnimating: Boolean get() = looping

    private fun ensureRunning() {
        invalidate()
        if (looping || timers?.paused == true) return
        looping = true
        postOnAnimation(frame)
    }

    private fun anyActive(t: Long): Boolean {
        if (discarding) {
            if (t - discardStart < discardDur) return true
            finishDiscard()
            return false
        }
        for (k in chips.indices) if (t - chipStart[k] < POP_MS) return true
        return false
    }

    private fun onFrame() {
        if (!looping) return
        if (timers?.paused == true) { looping = false; return }
        invalidate()
        if (anyActive(now())) postOnAnimation(frame) else { looping = false; invalidate() }
    }

    fun pauseAnims() {
        looping = false
        removeCallbacks(frame)
    }

    fun resumeAnims() {
        if (anyActive(now())) ensureRunning()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = Theme.dpf(context, 1f)
        rect.set(d * 2, d * 2, w - d * 2, h - d * 2)
        initPaint.textSize = d * 13f
        subPaint.textSize = d * 10.5f
        emptyPaint.textSize = d * 10.5f
        box.set(rect.left + d * 6, rect.top + d * 58, rect.right - d * 6, rect.bottom - d * 6)
        fitName()
        fitHint()
        relayoutChips()
    }

    private fun fitName() {
        if (rect.width() <= 0f) return
        var s = Theme.dpf(context, 17f)
        namePaint.textSize = s
        val max = rect.width() - Theme.dpf(context, 46f) - subPaint.measureText(TURN)
        while (namePaint.measureText(name) > max && s > Theme.dpf(context, 10f)) {
            s *= 0.92f
            namePaint.textSize = s
        }
    }

    private fun fitHint() {
        if (rect.width() <= 0f) return
        var s = Theme.dpf(context, 12f)
        hintPaint.textSize = s
        while (hintPaint.measureText(hint) > rect.width() - Theme.dpf(context, 16f) && s > Theme.dpf(context, 8f)) {
            s *= 0.92f
            hintPaint.textSize = s
        }
    }

    /** 칩 크기: 한 줄 3개 (많으면 4개), 칩 상자 높이에 맞춘다 */
    private fun relayoutChips() {
        if (box.width() <= 0f) return
        val d = Theme.dpf(context, 1f)
        chipGap = d * 5
        perRow = if (chips.size > 6) 4 else 3
        val inner = box.width() - d * 12
        chipW = (inner - chipGap * (perRow - 1)) / perRow
        val rows = ((chips.size + perRow - 1) / perRow).coerceAtLeast(2)
        val availH = box.height() - d * 22
        chipH = minOf(d * 30f, (availH - chipGap * (rows - 1)) / rows)
        chipPaint.textSize = minOf(chipH * 0.55f, chipW * 0.36f)
    }

    private fun chipBounds(i: Int, out: RectF) {
        val d = Theme.dpf(context, 1f)
        val row = i / perRow
        val col = i % perRow
        val l = box.left + d * 6 + col * (chipW + chipGap)
        val t = box.top + d * 18 + row * (chipH + chipGap)
        out.set(l, t, l + chipW, t + chipH)
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val t = now()
        val d = Theme.dpf(context, 1f)
        // 카드
        fill.color = Theme.CARD
        c.drawRoundRect(rect, d * 14, d * 14, fill)
        stroke.color = color
        stroke.alpha = 200
        stroke.strokeWidth = d * 2.5f
        c.drawRoundRect(rect, d * 14, d * 14, stroke)
        stroke.alpha = 255
        // 차례: 색 동그라미 + 이름 + "차례"
        val cy = rect.top + d * 20
        val cx = rect.left + d * 20
        fill.color = color
        c.drawCircle(cx, cy, d * 11, fill)
        initPaint.color = initColor
        c.drawText(initial, cx, cy + initPaint.textSize * 0.36f, initPaint)
        namePaint.color = color
        val nx = cx + d * 16
        c.drawText(name, nx, cy + namePaint.textSize * 0.36f, namePaint)
        c.drawText(TURN, nx + namePaint.measureText(name) + d * 4, cy + subPaint.textSize * 0.4f, subPaint)
        // 안내
        c.drawText(hint, rect.left + d * 9, rect.top + d * 48, hintPaint)
        // 대기열 상자
        fill.color = 0x40000000
        c.drawRoundRect(box, d * 10, d * 10, fill)
        c.drawText(LABEL, box.left + d * 8, box.top + d * 13, subPaint)
        if (chips.isEmpty()) {
            c.drawText(EMPTY, box.centerX(), box.centerY() + d * 8, emptyPaint)
            return
        }
        val da = if (discarding) ((t - discardStart).toFloat() / discardDur).coerceIn(0f, 1f) else 0f
        for (i in chips.indices) {
            chipBounds(i, chipRect)
            val r = chips[i]
            val pop = ((t - chipStart[i]).toFloat() / POP_MS).coerceIn(0f, 1f)
            val s = if (pop < 1f) 0.3f + 0.7f * pop + 0.25f * kotlin.math.sin(pop * Math.PI.toFloat()) else 1f
            c.save()
            c.scale(s, s, chipRect.centerX(), chipRect.centerY())
            if (discarding) c.translate(0f, da * da * chipH * 0.8f)
            val sel = i == selected
            val can = usable[i] && !discarding
            val a = if (discarding) (1f - da) else if (can) 1f else 0.45f
            val rr = chipH * 0.42f
            // 두께 + 면
            fill.color = if (sel) Theme.GOLD_DARK else 0xFF8A6A3E.toInt()
            fill.alpha = (a * 255).toInt()
            c.drawRoundRect(chipRect.left, chipRect.top + d * 2.5f, chipRect.right, chipRect.bottom + d * 2.5f, rr, rr, fill)
            fill.color = if (sel) Theme.GOLD else chipFace(r)
            fill.alpha = (a * 255).toInt()
            c.drawRoundRect(chipRect, rr, rr, fill)
            if (sel) {
                stroke.color = Theme.TEXT
                stroke.strokeWidth = d * 2f
                c.drawRoundRect(chipRect, rr, rr, stroke)
            }
            chipPaint.color = if (r == YutResult.BACKDO) YutArt.SEAL else YutArt.INK
            chipPaint.alpha = (a * 255).toInt()
            c.drawText(r.label, chipRect.centerX(), chipRect.centerY() + chipPaint.textSize * 0.36f, chipPaint)
            if (discarding || !usable[i]) {
                // 쓸 수 없음: 붉은 빗금
                stroke.color = Theme.RED
                stroke.alpha = (a * 255).toInt().coerceAtLeast(if (discarding) 0 else 200)
                stroke.strokeWidth = d * 2.5f
                c.drawLine(chipRect.left + d * 6, chipRect.bottom - d * 5, chipRect.right - d * 6, chipRect.top + d * 5, stroke)
                stroke.alpha = 255
            }
            c.restore()
        }
        fill.alpha = 255
    }

    private fun chipFace(r: YutResult): Int = when (r) {
        YutResult.MO, YutResult.YUT -> 0xFFFFE3A0.toInt()
        YutResult.BACKDO -> 0xFFF6D9C8.toInt()
        else -> YutArt.HANJI
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
            if (discarding) return true
            for (i in chips.indices) {
                chipBounds(i, chipRect)
                chipRect.inset(-chipGap / 2f, -chipGap / 2f)
                if (chipRect.contains(e.x, e.y)) { listener?.onChipTapped(i); break }
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        const val MAX_CHIPS = 12
        private const val POP_MS = 320L
        private const val TURN = "차례"
        private const val LABEL = "이동 대기열"
        private const val EMPTY = "던진 결과가 여기 쌓여요"
    }
}
