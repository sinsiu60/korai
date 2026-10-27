package com.siwoo.party.games.simon.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.simon.GameConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 4색 큰 버튼 판 (클래식 사이먼 모양) + 가운데 원판에 순서 길이 큰 숫자.
 * - 버튼을 누르면 listener.onPad(i) (켜는 것은 화면이 받아들였을 때 light 로)
 * - light(i): 잠깐 켜졌다 꺼짐, flash(i): 정답 색 깜빡임, setError(i): 잘못 누른 버튼에 ✕
 * - 가운데: "순서 길이" / 큰 숫자 / 아래 "3 / 7" 또는 새 색 추가 차례면 금색 "+1"
 * 켜짐·깜빡임·숫자 톡 연출이 있을 때만 postOnAnimation 으로 다시 그린다 (대기 중에는 그리지 않음). onDraw 할당 없음.
 */
class SimonBoardView(context: Context) : View(context) {

    fun interface Listener {
        fun onPad(index: Int)
    }

    var listener: Listener? = null

    /** 버튼 밝기 0..1 (프레임마다 계산 → onDraw 는 읽기만) */
    private val levels = FloatArray(GameConfig.COLORS)
    /** 버튼 켜짐: 켜진 시각·꺼지기 시작하는 시각·다 꺼지는 시각 */
    private val holdEnd = LongArray(GameConfig.COLORS)
    private val fadeEnd = LongArray(GameConfig.COLORS)
    private var flashPad = -1
    private var flashStart = 0L
    private var flashMs = 0L
    private var flashTimes = 0
    private var popStart = 0L
    private var popMs = 0L
    private var popScale = 1f
    private var looping = false
    private var paused = false

    /** 잘못 누른 버튼 (✕ 표시, -1 없음) */
    var errorPad = -1
        private set
    /** 새 색을 더할 차례 (가운데 금색 "+1") */
    var adding = false
        private set
    /** 가운데 큰 숫자 (순서 길이) */
    var lengthText = "0"
        private set
    private var progressText = ""
    private val captionText = "순서 길이"
    private val plusText = "+1"

    // 배치
    private var cx = 0f
    private var cy = 0f
    private var r = 0f
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        numPaint.setShadowLayer(Theme.dpf(context, 3f), 0f, Theme.dpf(context, 2f), 0xAA000000.toInt())
        contentDescription = "패턴 판"
    }

    private val frame = object : Runnable {
        override fun run() {
            looping = false
            if (paused) return
            if (update(SystemClock.uptimeMillis())) loop()
            invalidate()
        }
    }

    private fun loop() {
        if (looping || paused) return
        looping = true
        postOnAnimation(frame)
    }

    /** 밝기·숫자 크기 계산. 아직 연출 중이면 true */
    private fun update(now: Long): Boolean {
        var busy = false
        for (i in 0 until GameConfig.COLORS) {
            var v = when {
                now < holdEnd[i] -> 1f
                now < fadeEnd[i] -> (fadeEnd[i] - now).toFloat() / (fadeEnd[i] - holdEnd[i]).coerceAtLeast(1)
                else -> 0f
            }
            if (now < fadeEnd[i]) busy = true
            if (i == flashPad) {
                val e = now - flashStart
                if (e < flashMs) {
                    // flashTimes 번 켜졌다 꺼짐 (켜진 쪽이 조금 길게)
                    val ph = (e * flashTimes % flashMs).toFloat() / flashMs
                    if (ph < 0.62f) v = 1f
                    busy = true
                } else flashPad = -1
            }
            levels[i] = v
        }
        val pe = now - popStart
        if (pe < popMs) {
            val f = pe.toFloat() / popMs
            popScale = 1f + 0.45f * sin(f * Math.PI).toFloat() * (1f - f * 0.3f)
            busy = true
        } else popScale = 1f
        return busy
    }

    /** 버튼 i 를 holdMs 동안 켜고 fadeMs 동안 끈다 */
    fun light(i: Int, holdMs: Long, fadeMs: Long) {
        if (i !in 0 until GameConfig.COLORS) return
        val now = SystemClock.uptimeMillis()
        holdEnd[i] = now + holdMs
        fadeEnd[i] = now + holdMs + fadeMs
        update(now)
        invalidate()
        loop()
    }

    /** 정답 색 깜빡임 (times 번, 전체 ms) */
    fun flash(i: Int, ms: Long, times: Int) {
        flashPad = i
        flashStart = SystemClock.uptimeMillis()
        flashMs = ms.coerceAtLeast(1)
        flashTimes = times.coerceAtLeast(1)
        update(flashStart)
        invalidate()
        loop()
    }

    /** 잘못 누른 버튼 ✕ (-1 지움) */
    fun setError(i: Int) {
        if (errorPad == i) return
        errorPad = i
        invalidate()
    }

    /** 새 색 추가 차례 표시 */
    fun setAdding(v: Boolean) {
        if (adding == v) return
        adding = v
        invalidate()
    }

    /** 가운데 숫자 (순서 길이). pop 이면 톡 커졌다 돌아옴 */
    fun setLength(n: Int, pop: Boolean, popMs: Long) {
        val s = n.toString()
        if (s != lengthText) { lengthText = s; fitNumber() }
        if (pop) {
            popStart = SystemClock.uptimeMillis()
            this.popMs = popMs.coerceAtLeast(1)
            loop()
        }
        invalidate()
    }

    /** 이번 차례 진행 ("3 / 7"). length 0 이면 비움 */
    fun setProgress(done: Int, length: Int) {
        progressText = if (length <= 0) "" else "$done / $length"
        invalidate()
    }

    val progress: String get() = progressText

    /** 켜짐·깜빡임 모두 끔 (차례 초기화) */
    fun clearLights() {
        holdEnd.fill(0)
        fadeEnd.fill(0)
        flashPad = -1
        levels.fill(0f)
        invalidate()
    }

    /** 지금 버튼 i 밝기 (테스트·확인용) */
    fun level(i: Int): Float = levels[i]

    fun pauseAnims() {
        paused = true
        removeCallbacks(frame)
        looping = false
    }

    fun resumeAnims() {
        paused = false
        if (update(SystemClock.uptimeMillis())) loop()
        invalidate()
    }

    /** 버튼 i 가운데 (이 뷰 좌표) */
    fun padCenter(i: Int, out: PointF) {
        val a = Math.toRadians((SimonArt.startAngle(i) + 45f).toDouble())
        val rr = r * (SimonArt.HUB + SimonArt.PAD_OUT) / 2f
        out.set(cx + (rr * cos(a)).toFloat(), cy + (rr * sin(a)).toFloat())
    }

    /** 판 가운데 (이 뷰 좌표) */
    fun hubCenter(out: PointF) = out.set(cx, cy)

    /** (x, y) 가 누르는 버튼 (칸막이·가운데·바깥이면 -1) */
    fun padAt(x: Float, y: Float): Int {
        val dx = x - cx
        val dy = y - cy
        val d = sqrt(dx * dx + dy * dy)
        if (d < r * (SimonArt.HUB + 0.02f) || d > r * 1.0f) return -1
        val g = r * SimonArt.GAP * 0.35f // 칸막이 한가운데만 무시 (애매한 터치)
        if (abs(dx) < g || abs(dy) < g) return -1
        return if (dy < 0) (if (dx < 0) 0 else 1) else (if (dx > 0) 2 else 3)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m == MotionEvent.ACTION_DOWN || m == MotionEvent.ACTION_POINTER_DOWN) {
            val k = e.actionIndex
            val i = padAt(e.getX(k), e.getY(k))
            if (i >= 0) listener?.onPad(i)
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        r = minOf(w, h) / 2f * 0.93f
        val ir = r * 0.37f
        capPaint.textSize = ir * 0.2f
        subPaint.textSize = ir * 0.24f
        fitNumber()
    }

    private fun fitNumber() {
        val ir = r * 0.37f
        numPaint.textSize = ir * 0.95f
        while (numPaint.measureText(lengthText) > ir * 1.5f && numPaint.textSize > 8f) numPaint.textSize *= 0.92f
    }

    override fun onDraw(c: Canvas) {
        if (r <= 0f) return
        SimonArt.drawBoard(c, cx, cy, r, levels, paint, rect)
        // 가운데 얼굴
        val ir = r * 0.37f
        paint.style = Paint.Style.FILL
        paint.color = if (adding) Theme.CARD_LIGHT else Theme.CARD
        c.drawCircle(cx, cy, ir, paint)
        stroke.strokeWidth = r * (if (adding) 0.03f else 0.015f)
        stroke.color = if (adding) Theme.GOLD else Theme.CARD_BORDER
        c.drawCircle(cx, cy, ir - stroke.strokeWidth / 2f, stroke)
        c.drawText(captionText, cx, cy - ir * 0.5f, capPaint)
        c.save()
        c.scale(popScale, popScale, cx, cy + ir * 0.1f)
        c.drawText(lengthText, cx, cy + ir * 0.4f, numPaint)
        c.restore()
        if (adding) {
            subPaint.color = Theme.GOLD
            c.drawText(plusText, cx, cy + ir * 0.78f, subPaint)
        } else if (progressText.isNotEmpty()) {
            subPaint.color = Theme.TEXT_SUB
            c.drawText(progressText, cx, cy + ir * 0.78f, subPaint)
        }
        // 잘못 누른 버튼 ✕
        if (errorPad >= 0) {
            val a = Math.toRadians((SimonArt.startAngle(errorPad) + 45f).toDouble())
            val rr = r * (SimonArt.HUB + SimonArt.PAD_OUT) / 2f
            val x = cx + (rr * cos(a)).toFloat()
            val y = cy + (rr * sin(a)).toFloat()
            val s = r * 0.12f
            stroke.color = 0xCC000000.toInt()
            stroke.strokeWidth = r * 0.075f
            c.drawLine(x - s, y - s, x + s, y + s, stroke)
            c.drawLine(x + s, y - s, x - s, y + s, stroke)
            stroke.color = Theme.TEXT
            stroke.strokeWidth = r * 0.045f
            c.drawLine(x - s, y - s, x + s, y + s, stroke)
            c.drawLine(x + s, y - s, x - s, y + s, stroke)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        looping = false
        super.onDetachedFromWindow()
    }
}
