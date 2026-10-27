package com.siwoo.party.common.timerbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/**
 * 줄어드는 시간 막대: 가로 둥근 막대가 오른쪽에서 왼쪽으로 줄어든다.
 * 남은 비율이 50% 넘으면 초록, 30~50% 금색, 30% 이하(warnFraction) 빨강 + 깜빡임(진행 중일 때만).
 * 왼쪽 초시계 그림 + (선택) 오른쪽 남은 초 ("12초" — 초마다 바뀌는 글자는 미리 만들어 둔다).
 *
 * 두 가지 쓰는 법:
 *  1) start(totalMs) { 시간 끝 } — 스스로 프레임마다 다시 그리며 줄어든다. 시계는 기본으로 게임 시계(activity.timers.now(),
 *     일시정지되면 멈춤)라 일시정지 중엔 다시 그리지 않고 쉬었다가 다시 이어진다. stop() 으로 멈춤.
 *  2) setFraction(f, remainingMs) — 게임이 자기 루프에서 직접 비율을 넣는다 (이때는 스스로 다시 그리지 않음).
 * 게임 규칙상 중요한 시간 끝 처리는 timers.after 로 따로 해도 된다 (onTimeUp 은 화면 프레임 기준이라 조금 늦을 수 있음).
 */
class TimerBarView(context: Context) : View(context) {

    /** 남은 비율 0..1 */
    var fraction = 1f
        private set

    /** 남은 시간 (ms, 모르면 -1) */
    var remainingMs = -1L
        private set

    /** start() 로 줄어드는 중 */
    var running = false
        private set

    /** 이 비율 이하이면 빨강 + 깜빡임 */
    var warnFraction = 0.3f

    /** 오른쪽에 남은 초 글자 표시 */
    var showLabel = true
        set(v) { field = v; relayout(); invalidate() }

    /** 시간 끝 (start 로 돌릴 때 한 번) */
    var onTimeUp: (() -> Unit)? = null

    /** 시계 (ms) — 숫자 상자(boxing) 없이 부르려고 따로 둔 인터페이스 */
    fun interface Clock { fun now(): Long }

    /** 시계 (기본: 게임 시계 activity.timers.now() — 일시정지 시간 제외). 예) bar.clock = TimerBarView.Clock { myTime() } */
    var clock: Clock = Clock { (context as? PartyGameActivity)?.timers?.now() ?: SystemClock.uptimeMillis() }

    /** 일시정지 중인지 (기본: PartyGameActivity.gamePaused) — 이때는 다시 그리지 않는다 */
    var pausedCheck: () -> Boolean = { (context as? PartyGameActivity)?.gamePaused == true }

    /** 30% 이하 (빨강) */
    val warning: Boolean get() = fraction <= warnFraction

    /** 남은 초 (올림) */
    val secondsLeft: Int get() = if (remainingMs < 0) -1 else ceil(remainingMs / 1000.0).toInt()

    private var startAt = 0L
    private var totalMs = 0L
    private var userPaused = false
    private var pausedLeft = 0L
    private var looping = false
    private var label = ""
    /** 깜빡임 밝기 0..1 (값을 넣을 때 계산 → onDraw 는 읽기만) */
    private var blink = 0f
    private val darkGreen = Theme.shade(Theme.GREEN, 0.6f)
    private val darkGold = Theme.shade(Theme.GOLD, 0.6f)
    private val darkRed = Theme.shade(Theme.RED, 0.6f)
    private var labels: Array<String> = emptyArray()

    private val track = RectF()
    private val bar = RectF()
    private val pill = RectF()
    private var iconX = 0f
    private var iconR = 0f
    private var labelOff = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    private val frame = object : Runnable {
        override fun run() {
            looping = false
            if (!running) return
            if (userPaused || pausedCheck()) {
                // 일시정지 중: 다시 그리지 않고 가끔 확인만
                looping = true
                postDelayed(this, PAUSE_POLL_MS)
                return
            }
            tick()
            if (running) { looping = true; postOnAnimation(this) }
        }
    }

    /** totalMs 동안 줄어들기 시작 (onTimeUp 은 0 이 될 때 한 번) */
    fun start(totalMs: Long, onTimeUp: (() -> Unit)? = null) {
        this.totalMs = totalMs.coerceAtLeast(1)
        if (onTimeUp != null) this.onTimeUp = onTimeUp
        ensureLabels(this.totalMs)
        startAt = clock.now()
        userPaused = false
        running = true
        set(1f, this.totalMs)
        loop()
    }

    /** 멈춤 (지금 모양 그대로, 다시 그리지 않음) */
    fun stop() {
        running = false
        blink = 0f
        removeCallbacks(frame)
        looping = false
        invalidate()
    }

    /** 잠시 멈춤 (게임 시계가 아닌 시계를 쓸 때. 게임 시계면 일시정지 때 자동으로 멈춘다) */
    fun pause() {
        if (!running || userPaused) return
        tick()
        userPaused = true
        blink = 0f
        pausedLeft = remainingMs.coerceAtLeast(0)
        invalidate()
    }

    fun resume() {
        if (!running || !userPaused) return
        userPaused = false
        startAt = clock.now() - (totalMs - pausedLeft)
        removeCallbacks(frame) // 일시정지 중 확인 예약을 지우고 바로 다음 프레임부터
        looping = false
        loop()
    }

    /**
     * 게임이 직접 비율 넣기 (start 를 쓰지 않을 때). remainingMs >= 0 이면 오른쪽에 남은 초 표시.
     * 매 프레임 불러도 된다 (초가 바뀔 때만 글자를 고름, 처음 보는 큰 초일 때만 글자 배열을 늘림).
     */
    fun setFraction(f: Float, remainingMs: Long = -1L) {
        if (remainingMs >= 0) ensureLabels(remainingMs)
        set(f, remainingMs, animate = true)
    }

    private fun set(f: Float, rem: Long, animate: Boolean = running) {
        fraction = f.coerceIn(0f, 1f)
        remainingMs = rem
        // 깜빡임: 30% 이하이고 줄어드는 중일 때만
        blink = if (animate && fraction > 0f && fraction <= warnFraction)
            0.5f + 0.5f * sin(SystemClock.uptimeMillis() * (2.0 * PI / BLINK_MS)).toFloat() else 0f
        val s = secondsLeft
        label = if (s < 0) "" else labels.getOrElse(s) { "" }
        updateBar()
        invalidate()
    }

    private fun loop() {
        if (looping) return
        looping = true
        postOnAnimation(frame)
    }

    private fun tick() {
        val left = (totalMs - (clock.now() - startAt)).coerceAtLeast(0)
        set(left.toFloat() / totalMs, left)
        if (left <= 0 && running) {
            running = false
            blink = 0f
            invalidate()
            onTimeUp?.invoke()
        }
    }

    /** "0초" ~ "N초" 글자를 미리 만든다 (값이 바뀔 때만) */
    private fun ensureLabels(ms: Long) {
        val n = ceil(ms / 1000.0).toInt() + 1
        if (labels.size >= n) return
        labels = Array(n) { "${it}초" }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (running) loop()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        looping = false
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val pad = h * 0.08f
        iconR = h * 0.4f
        iconX = pad + iconR
        var right = w - pad
        if (showLabel) {
            val pw = h * 1.9f
            pill.set(w - pad - pw, h * 0.12f, w - pad, h * 0.88f)
            right = pill.left - h * 0.25f
            labelPaint.textSize = pill.height() * 0.62f
            val fm = labelPaint.fontMetrics
            labelOff = -(fm.ascent + fm.descent) / 2f
        }
        val th = h * 0.56f
        track.set(iconX + iconR + h * 0.2f, (h - th) / 2f, right, (h + th) / 2f)
        updateBar()
    }

    private fun updateBar() {
        if (track.width() <= 0f) return
        val ins = track.height() * 0.14f
        val minW = track.height() - ins * 2
        val full = track.width() - ins * 2
        val bw = if (fraction <= 0f) 0f else (full * fraction).coerceAtLeast(minW)
        bar.set(track.left + ins, track.top + ins, track.left + ins + bw, track.bottom - ins)
    }

    /** 지금 막대 색 */
    private fun barColor(): Int = when {
        fraction <= warnFraction -> Theme.RED
        fraction <= 0.5f -> Theme.GOLD
        else -> Theme.GREEN
    }

    override fun onDraw(c: Canvas) {
        if (track.width() <= 0f) return
        val warn = fraction <= warnFraction
        val color = barColor()
        // 바깥 빛 (깜빡임)
        if (blink > 0f) {
            fill.color = Theme.RED
            fill.alpha = (blink * 110).toInt()
            val g = track.height() * 0.22f
            c.drawRoundRect(track.left - g, track.top - g, track.right + g, track.bottom + g, track.height(), track.height(), fill)
            fill.alpha = 255
        }
        // 홈 (어두운 안쪽 + 테두리)
        val r = track.height() / 2f
        fill.color = Theme.BG_DEEP
        c.drawRoundRect(track, r, r, fill)
        stroke.strokeWidth = track.height() * 0.08f
        stroke.color = if (warn) Theme.RED_DARK else Theme.GOLD_DARK
        c.drawRoundRect(track, r, r, stroke)
        // 막대 (아래쪽 어두운 두께 + 면 + 윗면 광택)
        if (bar.width() > 0f) {
            val br = bar.height() / 2f
            fill.color = if (warn) darkRed else if (fraction <= 0.5f) darkGold else darkGreen
            c.drawRoundRect(bar, br, br, fill)
            fill.color = if (blink > 0f) blend(color, blink) else color
            c.drawRoundRect(bar.left, bar.top, bar.right, bar.bottom - bar.height() * 0.18f, br, br, fill)
            fill.color = 0x55FFFFFF
            c.drawRoundRect(bar.left + br * 0.5f, bar.top + bar.height() * 0.12f, bar.right - br * 0.5f, bar.top + bar.height() * 0.4f,
                br * 0.5f, br * 0.5f, fill)
        }
        drawWatch(c, color, warn)
        if (showLabel && label.isNotEmpty()) {
            val pr = pill.height() / 2f
            fill.color = if (warn) Theme.RED_DARK else Theme.CARD
            c.drawRoundRect(pill, pr, pr, fill)
            stroke.strokeWidth = pill.height() * 0.07f
            stroke.color = if (warn) Theme.RED else Theme.GOLD
            c.drawRoundRect(pill, pr, pr, stroke)
            labelPaint.color = if (warn) Theme.TEXT else Theme.GOLD_LIGHT
            c.drawText(label, pill.centerX(), pill.centerY() + labelOff, labelPaint)
        }
    }

    /** 초시계: 몸통 + 꼭지 + 남은 비율만큼 채운 부채꼴 */
    private fun drawWatch(c: Canvas, color: Int, warn: Boolean) {
        val cy = height / 2f + iconR * 0.08f
        val r = iconR * 0.82f
        fill.color = Theme.GOLD_DARK
        c.drawRoundRect(iconX - r * 0.22f, cy - r * 1.22f, iconX + r * 0.22f, cy - r * 0.9f, r * 0.08f, r * 0.08f, fill)
        fill.color = Theme.PAPER
        c.drawCircle(iconX, cy, r, fill)
        fill.color = color
        val ir = r * 0.78f
        c.drawArc(iconX - ir, cy - ir, iconX + ir, cy + ir, -90f, -360f * fraction, true, fill)
        stroke.color = if (warn) Theme.RED_DARK else Theme.GOLD_DARK
        stroke.strokeWidth = r * 0.16f
        c.drawCircle(iconX, cy, r, stroke)
        stroke.color = Theme.INK
        stroke.strokeWidth = r * 0.12f
        c.drawLine(iconX, cy, iconX, cy - r * 0.55f, stroke)
    }

    /** 색을 흰색 쪽으로 t 만큼 (깜빡임) — 할당 없음 */
    private fun blend(c: Int, t: Float): Int {
        val k = t * 0.45f
        val r = (c shr 16 and 0xFF) + ((255 - (c shr 16 and 0xFF)) * k).toInt()
        val g = (c shr 8 and 0xFF) + ((255 - (c shr 8 and 0xFF)) * k).toInt()
        val b = (c and 0xFF) + ((255 - (c and 0xFF)) * k).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        /** 깜빡임 한 번 (ms) */
        const val BLINK_MS = 600.0
        private const val PAUSE_POLL_MS = 120L
    }
}
