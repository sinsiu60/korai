package com.siwoo.party.common.dice

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import com.siwoo.party.common.ui.Theme
import kotlin.math.PI
import kotlin.math.sin

/**
 * 공통 주사위 1~5개 (시우 마블에서 옮겨 일반화).
 * View 가 아니라 그리기 담당이라, 보드처럼 큰 View 안(시우 마블)이나 DiceView(야추) 안에서 쓴다.
 * - roll(): 흔들리며 눈이 바뀌다 점점 느려지며 멈춤 → (선택) 결과 글자 크게 → holdMs 뒤 onEnd
 * - held[i] = true 인 주사위(고정)는 굴리지 않고, 금색 테두리 + 살짝 아래로 내려 그린다
 * - 그리기(draw)에서 객체를 만들지 않는다
 */
class DiceSet(private val host: View) {

    /** 보이는 주사위 수 (1~5) */
    var count = 2
        set(v) { field = v.coerceIn(1, MAX); host.invalidate() }
    val values = IntArray(MAX) { 1 }
    val held = BooleanArray(MAX)
    var rolling = false
        private set

    private val shakeX = FloatArray(MAX)
    private val shakeY = FloatArray(MAX)
    private val rot = FloatArray(MAX)
    private val xs = FloatArray(MAX)
    private var rowY = 0f
    /** 주사위 한 변 */
    var size = 0f
        private set
    private var anim: ValueAnimator? = null
    private var nextFaceAt = 0L
    private var resultText = ""
    private var resultStart = 0L
    private var resultUntil = 0L

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val resultOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF0A0F2E.toInt()
    }

    /** 한 줄로 배치: 가운데 (cx, cy), 한 변 s, 간격 gap. count 가 바뀌면 다시 부를 것 */
    fun layoutRow(cx: Float, cy: Float, s: Float, gap: Float) {
        size = s
        rowY = cy
        val total = count * s + (count - 1) * gap
        for (i in 0 until count) xs[i] = cx - total / 2f + s / 2f + i * (s + gap)
    }

    fun setValues(v: IntArray) {
        for (i in 0 until minOf(v.size, MAX)) values[i] = v[i].coerceIn(1, 6)
        host.invalidate()
    }

    val isAnimating: Boolean get() = anim?.isRunning == true

    /** 결과 글자를 보여 주는 중인지 */
    fun showingResult(t: Long = SystemClock.uptimeMillis()): Boolean = t < resultUntil

    /**
     * 굴리기. results[i] 가 i 번째 주사위 최종 눈 (고정된 주사위는 그대로).
     * resultLabel 이 있으면 멈춘 뒤 holdMs 동안 크게 보여 준다 (drawResult 로 그림).
     */
    fun roll(results: IntArray, rollMs: Long, holdMs: Long, resultLabel: String?, onTick: () -> Unit, onEnd: () -> Unit) {
        cancel()
        rolling = true
        nextFaceAt = 0L
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = rollMs.coerceAtLeast(1)
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { va ->
            val p = va.animatedFraction
            val t = SystemClock.uptimeMillis()
            if (t >= nextFaceAt) {
                for (i in 0 until count) if (!held[i]) values[i] = 1 + RANDOM.nextInt(6)
                nextFaceAt = t + (45 + 280 * p * p).toLong() // 점점 느려짐
                onTick()
            }
            val amp = size * 0.12f * (1f - p)
            for (i in 0 until count) {
                if (held[i]) { shakeX[i] = 0f; shakeY[i] = 0f; rot[i] = 0f; continue }
                shakeX[i] = (RANDOM.nextFloat() - 0.5f) * 2f * amp
                shakeY[i] = (RANDOM.nextFloat() - 0.5f) * 2f * amp
                rot[i] = (RANDOM.nextFloat() - 0.5f) * 36f * (1f - p)
            }
            host.invalidate()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                rolling = false
                for (i in 0 until count) {
                    if (!held[i]) values[i] = results.getOrElse(i) { values[i] }.coerceIn(1, 6)
                    shakeX[i] = 0f; shakeY[i] = 0f; rot[i] = 0f
                }
                if (resultLabel == null || holdMs <= 0) {
                    anim = null
                    host.invalidate()
                    onEnd()
                    return
                }
                resultText = resultLabel
                resultStart = SystemClock.uptimeMillis()
                resultUntil = resultStart + holdMs
                val hold = ValueAnimator.ofFloat(0f, 1f)
                hold.duration = holdMs
                hold.addUpdateListener { host.invalidate() }
                hold.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        anim = null
                        host.invalidate()
                        onEnd()
                    }
                })
                anim = hold
                hold.start()
            }
        })
        anim = a
        a.start()
    }

    /** 진행 중인 굴림을 콜백 없이 정리 */
    fun cancel() {
        anim?.let { it.removeAllListeners(); it.removeAllUpdateListeners(); it.cancel() }
        anim = null
        rolling = false
        resultUntil = 0L
        for (i in 0 until MAX) { shakeX[i] = 0f; shakeY[i] = 0f; rot[i] = 0f }
    }

    /** (x, y) 에 있는 주사위 번호, 없으면 -1 */
    fun hit(x: Float, y: Float): Int {
        val h = size * 0.6f
        for (i in 0 until count) if (x >= xs[i] - h && x <= xs[i] + h && y >= rowY - h && y <= rowY + h + size * 0.15f) return i
        return -1
    }

    /** 주사위 i 의 가운데 x */
    fun centerX(i: Int): Float = xs[i]

    /** 주사위 줄 그리기. dy = 위아래 떠오름, alpha = 흐리게(대기) */
    fun draw(c: Canvas, dy: Float = 0f, alpha: Int = 255) {
        for (i in 0 until count) {
            val down = if (held[i]) size * 0.14f else 0f
            drawDie(c, xs[i] + shakeX[i], rowY + dy + shakeY[i] + down, size, values[i], rot[i], alpha, held[i])
        }
    }

    /** 결과 글자 (톡 튀어나옴) — showingResult 동안 */
    fun drawResult(c: Canvas, x: Float, y: Float, textSize: Float, t: Long = SystemClock.uptimeMillis()) {
        if (t >= resultUntil) return
        val f = ((t - resultStart).toFloat() / 250f).coerceIn(0f, 1f)
        val sc = 0.5f + 0.5f * f + 0.3f * sin(f * PI.toFloat())
        c.save()
        c.translate(x, y)
        c.scale(sc, sc)
        resultOutline.textSize = textSize
        resultOutline.strokeWidth = textSize * 0.15f
        c.drawText(resultText, 0f, 0f, resultOutline)
        resultPaint.textSize = textSize
        c.drawText(resultText, 0f, 0f, resultPaint)
        c.restore()
    }

    private fun drawDie(c: Canvas, x: Float, y: Float, s: Float, v: Int, r: Float, alpha: Int, isHeld: Boolean) {
        c.save()
        c.translate(x, y)
        c.rotate(r)
        rect.set(-s / 2, -s / 2, s / 2, s / 2)
        // 그림자
        fill.color = 0x55000000
        fill.alpha = alpha / 3
        c.translate(s * 0.05f, s * 0.08f)
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, fill)
        c.translate(-s * 0.05f, -s * 0.08f)
        fill.color = Color.WHITE
        fill.alpha = alpha
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, fill)
        stroke.color = if (isHeld) Theme.GOLD else Theme.GOLD_DARK
        stroke.alpha = alpha
        stroke.strokeWidth = s * (if (isHeld) 0.1f else 0.05f)
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, stroke)
        val face = if (v in 1..6) v else 1
        fill.color = if (face == 1) 0xFFD32F2F.toInt() else 0xFF1A2040.toInt()
        fill.alpha = alpha
        val pips = PIPS[face - 1]
        val pr = s * (if (face == 1) 0.13f else 0.095f)
        var k = 0
        while (k < pips.size) {
            c.drawCircle((pips[k] - 0.5f) * s, (pips[k + 1] - 0.5f) * s, pr, fill)
            k += 2
        }
        c.restore()
        fill.alpha = 255
        stroke.alpha = 255
    }

    companion object {
        const val MAX = 5
        private val RANDOM = java.util.Random()

        /** 눈 위치 (한 변을 1로) */
        private val PIPS = arrayOf(
            floatArrayOf(0.5f, 0.5f),
            floatArrayOf(0.27f, 0.27f, 0.73f, 0.73f),
            floatArrayOf(0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f),
            floatArrayOf(0.27f, 0.27f, 0.73f, 0.27f, 0.27f, 0.73f, 0.73f, 0.73f),
            floatArrayOf(0.25f, 0.25f, 0.75f, 0.25f, 0.5f, 0.5f, 0.25f, 0.75f, 0.75f, 0.75f),
            floatArrayOf(0.27f, 0.22f, 0.73f, 0.22f, 0.27f, 0.5f, 0.73f, 0.5f, 0.27f, 0.78f, 0.73f, 0.78f),
        )
    }
}
