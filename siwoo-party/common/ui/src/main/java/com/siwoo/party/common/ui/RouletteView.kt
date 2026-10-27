package com.siwoo.party.common.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 공용 룰렛 (칸 수·글자·색은 setSlices 로 바깥에서 준다. 기본은 벌칙 8칸).
 * - 대기: "터치하여 돌리기" 깜빡임 → 터치하면 onSpinRequest
 * - spin(): 엔진이 이미 정한 칸에 멈추도록 3초 동안 점점 느려지며 회전, 칸을 지날 때마다 onTick
 * - 대형이면 마지막 1초 동안 테두리가 붉게 빛나고 onShake 로 화면을 살짝 흔든다
 * - 멈추면 결과 칸이 커졌다 돌아온다(확대) → onEnd
 */
class RouletteView(context: Context) : View(context) {

    var onSpinRequest: (() -> Unit)? = null

    private var rot = 0f                 // 바퀴 회전 각도(도)
    private var ready = true
    private var spinning = false
    private var resultSlot = -1
    private var resultStart = 0L
    private var glow = 0f                // 대형: 붉은 테두리 강도
    private var anim: ValueAnimator? = null
    private var idleAnim: ValueAnimator? = null
    private var blink = 1f

    private val wheel = RectF()
    private val slice = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val prompt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Color.WHITE }
    private val path = Path()
    private var cx = 0f
    private var cy = 0f
    private var r = 0f

    // 칸 정보 (setSlices 에서 한 번만 준비 → onDraw 에서 분기·할당 없음)
    private var labels: Array<String> = Array(8) { GRADE_LABELS[GRADE_PATTERN[it]] }
    private var sliceColors = IntArray(8) { GRADE_COLORS[GRADE_PATTERN[it]] }
    private var darkText = BooleanArray(8) { GRADE_PATTERN[it] == 1 }
    private var bolt = BooleanArray(8) { GRADE_PATTERN[it] == 2 }
    private var count = 8
    private var sliceDeg = 45f

    /** 바늘 아래 글자 */
    var promptText = "터치하여 돌리기"

    init {
        isClickable = true
        contentDescription = "룰렛"
    }

    /**
     * 칸 구성. dark: 밝은 칸이라 글자를 어둡게, bolt: 번개 표시(대형 벌칙 등).
     * 칸 0 이 처음에 바늘 아래에 있고 시계 방향으로 이어진다.
     */
    fun setSlices(labels: Array<String>, colors: IntArray, dark: BooleanArray? = null, bolt: BooleanArray? = null) {
        require(labels.isNotEmpty() && labels.size == colors.size)
        this.labels = labels
        sliceColors = colors.copyOf()
        count = labels.size
        darkText = dark ?: BooleanArray(count)
        this.bolt = bolt ?: BooleanArray(count)
        sliceDeg = 360f / count
        label.textSize = r * (if (count > 8) 0.12f else 0.16f)
        invalidate()
    }

    /** 다시 돌릴 수 있게 대기 상태로 */
    fun reset() {
        anim?.cancel()
        ready = true
        spinning = false
        resultSlot = -1
        glow = 0f
        if (isAttachedToWindow && idleAnim?.isRunning != true) idleAnim?.start()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 대기 중 "터치하여 돌리기" 깜빡임 (대기 중에만)
        idleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (ready) { blink = 0.35f + 0.65f * abs(sin(it.animatedFraction * PI.toFloat())); invalidate() }
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        idleAnim?.cancel()
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val promptH = h * 0.14f
        r = minOf(w.toFloat(), h - promptH) / 2f * 0.9f
        cx = w / 2f
        cy = (h - promptH) / 2f + r * 0.06f
        wheel.set(cx - r, cy - r, cx + r, cy + r)
        label.textSize = r * (if (count > 8) 0.12f else 0.16f)
        prompt.textSize = minOf(promptH * 0.5f, r * 0.17f)
    }

    /**
     * 엔진이 정한 slot 에서 멈추도록 회전.
     * @param large 대형이면 마지막 1초 붉은 빛 + 흔들림
     */
    fun spin(slot: Int, durMs: Long, large: Boolean, onTick: () -> Unit, onShake: (Float) -> Unit, onEnd: () -> Unit) {
        ready = false
        spinning = true
        resultSlot = -1
        idleAnim?.cancel()
        val start = ((rot % 360f) + 360f) % 360f
        // 칸 k 의 가운데가 맨 위(바늘)에 오려면 회전 각도 ≡ -k*칸각도 (mod 360)
        var end = -slot * sliceDeg
        while (end < start + 360f * 5f) end += 360f
        val dur = durMs.coerceAtLeast(1)
        var lastSlice = slice(start)
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur
            interpolator = DecelerateInterpolator(2.2f) // 점점 느려짐
            addUpdateListener {
                val f = it.animatedValue as Float
                rot = start + (end - start) * f
                val s = slice(rot)
                if (s != lastSlice) { lastSlice = s; onTick() }
                val remain = dur * (1f - it.animatedFraction)
                if (large && remain <= 1000f) {
                    glow = 0.5f + 0.5f * abs(sin(it.animatedFraction * PI.toFloat() * 14f))
                    onShake(Theme.dpf(context, 4f) * (remain / 1000f + 0.3f))
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    spinning = false
                    onShake(0f)
                    rot = end
                    resultSlot = slot
                    resultStart = SystemClock.uptimeMillis()
                    // 결과 칸 확대 연출
                    val zoom = ValueAnimator.ofFloat(0f, 1f)
                    zoom.duration = 650
                    zoom.addUpdateListener { invalidate() }
                    zoom.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (!large) glow = 0f
                            onEnd()
                        }
                    })
                    zoom.start()
                }
            })
            start()
        }
    }

    private fun slice(angle: Float): Int = ((((-angle + sliceDeg / 2f) % 360f + 360f) % 360f / sliceDeg).toInt()).coerceAtMost(count - 1)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && ready && !spinning) {
            performClick()
            onSpinRequest?.invoke()
            return true
        }
        return event.actionMasked == MotionEvent.ACTION_DOWN || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(c: Canvas) {
        // 대형: 붉게 빛나는 바깥 테두리 (블러 없이 두께·알파가 다른 원 여러 겹)
        if (glow > 0f) {
            for (k in 3 downTo 1) {
                stroke.color = 0xFFFF1744.toInt()
                stroke.alpha = ((0.2f + 0.5f * glow) * 255 / k).toInt()
                stroke.strokeWidth = r * 0.05f * k
                c.drawCircle(cx, cy, r * 1.02f, stroke)
            }
        }
        // 테두리 (금색)
        slice.color = Theme.GOLD_DARK
        c.drawCircle(cx, cy, r * 1.04f, slice)

        val zoomF = if (resultSlot >= 0) ((SystemClock.uptimeMillis() - resultStart) / 650f).coerceIn(0f, 1f) else 0f
        for (k in 0 until count) {
            val startAngle = -90f + rot + k * sliceDeg - sliceDeg / 2f
            slice.color = sliceColors[k]
            if (k % 2 == 1 && sliceColors[k] == sliceColors[(k + count - 1) % count]) slice.color = Theme.shade(slice.color, 0.88f)
            var scale = 1f
            if (k == resultSlot) scale = 1f + 0.12f * sin(zoomF * PI.toFloat()) + 0.04f
            if (scale != 1f) {
                c.save()
                c.scale(scale, scale, cx, cy)
            }
            c.drawArc(wheel, startAngle, sliceDeg, true, slice)
            stroke.color = Color.WHITE
            stroke.alpha = 255
            stroke.strokeWidth = if (k == resultSlot) r * 0.03f else r * 0.012f
            c.drawArc(wheel, startAngle, sliceDeg, true, stroke)
            // 글자: 칸 가운데, 바깥쪽에서 안쪽으로
            c.save()
            c.rotate(rot + k * sliceDeg, cx, cy)
            label.color = if (darkText[k]) Theme.INK else Color.WHITE
            c.drawText(labels[k], cx, cy - r * 0.62f, label)
            if (bolt[k]) drawBolt(c, cx, cy - r * 0.38f, r * 0.13f)
            c.restore()
            if (scale != 1f) c.restore()
        }
        // 가운데 허브
        slice.color = Theme.CARD
        c.drawCircle(cx, cy, r * 0.2f, slice)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = r * 0.03f
        c.drawCircle(cx, cy, r * 0.2f, stroke)
        // 바늘 (맨 위, 아래를 가리킴)
        path.reset()
        path.moveTo(cx - r * 0.1f, cy - r * 1.12f)
        path.lineTo(cx + r * 0.1f, cy - r * 1.12f)
        path.lineTo(cx, cy - r * 0.88f)
        path.close()
        slice.color = Theme.GOLD
        c.drawPath(path, slice)
        stroke.color = Theme.INK
        stroke.strokeWidth = r * 0.015f
        c.drawPath(path, stroke)

        if (ready) {
            prompt.alpha = (blink * 255).toInt()
            c.drawText(promptText, cx, height - prompt.textSize * 0.4f, prompt)
        }
    }

    private fun drawBolt(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x + s * 0.2f, y - s)
        path.lineTo(x - s * 0.5f, y + s * 0.15f)
        path.lineTo(x, y + s * 0.15f)
        path.lineTo(x - s * 0.2f, y + s)
        path.lineTo(x + s * 0.5f, y - s * 0.15f)
        path.lineTo(x, y - s * 0.15f)
        path.close()
        slice.color = Theme.GOLD_LIGHT
        c.drawPath(path, slice)
    }

    companion object {
        /** 등급 색: 소형 초록 / 중형 노랑 / 대형 빨강 */
        val GRADE_COLORS = intArrayOf(0xFF27AE60.toInt(), 0xFFF2B705.toInt(), 0xFFE53935.toInt())
        val GRADE_LABELS = arrayOf("소형", "중형", "대형")
        /** 기본 8칸 배치 (소·중·소·대·소·중·소·중) */
        val GRADE_PATTERN = intArrayOf(0, 1, 0, 2, 0, 1, 0, 1)

        /** 등급 번호 배열(0 소형, 1 중형, 2 대형)로 칸 구성 */
        fun gradeSlices(view: RouletteView, grades: IntArray) {
            view.setSlices(
                Array(grades.size) { GRADE_LABELS[grades[it]] },
                IntArray(grades.size) { GRADE_COLORS[grades[it]] },
                BooleanArray(grades.size) { grades[it] == 1 },
                BooleanArray(grades.size) { grades[it] == 2 },
            )
        }
    }
}
