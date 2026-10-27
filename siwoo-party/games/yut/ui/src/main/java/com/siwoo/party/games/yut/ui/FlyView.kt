package com.siwoo.party.games.yut.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.Theme
import kotlin.math.PI
import kotlin.math.sin

/**
 * 판에서 패널로 날아가는 말 (게임 화면 전체를 덮는 층, 터치는 받지 않음).
 * - 잡기: 상대 말이 먼저 위로 튕겨 나간 뒤 빙글 돌며 그 사람 패널 대기 자리로 날아감
 * - 골인: 참먹이에서 위로 솟았다가 내 패널 골인 쪽으로
 * 동시에 최대 8개 (미리 만든 칸을 재사용). onDraw 에서는 객체를 만들지 않는다.
 */
class FlyView(context: Context) : View(context) {

    private class Flight {
        var active = false
        var bounce = false
        var x0 = 0f; var y0 = 0f; var x1 = 0f; var y1 = 0f
        var r0 = 0f; var r1 = 0f
        var start = 0L
        var dur = 1L
        var color = 0
        var dark = 0
        var textColor = 0
        var label = ""
    }

    var timers: GameTimers? = null
    private val pool = Array(MAX) { Flight() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private var looping = false
    private val frame = Runnable { onFrame() }

    init {
        isClickable = false
        isFocusable = false
    }

    private fun now(): Long = timers?.now() ?: SystemClock.uptimeMillis()

    /** 말 하나를 (x0,y0) 반지름 r0 → (x1,y1) 반지름 r1 로 날림. bounce = 잡혀서 튕겨 나감 */
    fun launch(x0: Float, y0: Float, r0: Float, x1: Float, y1: Float, r1: Float, color: Int, label: String, durMs: Long, delayMs: Long, bounce: Boolean) {
        val f = pool.firstOrNull { !it.active } ?: return
        f.active = true
        f.bounce = bounce
        f.x0 = x0; f.y0 = y0; f.x1 = x1; f.y1 = y1
        f.r0 = r0; f.r1 = r1
        f.start = now() + delayMs
        f.dur = durMs.coerceAtLeast(1)
        f.color = color
        f.dark = Theme.shade(color, 0.45f)
        f.textColor = Theme.textOn(color)
        f.label = label
        ensureRunning()
    }

    /** 날아가는 말 수 */
    val flying: Int get() = pool.count { it.active }
    val isAnimating: Boolean get() = looping

    private fun ensureRunning() {
        invalidate()
        if (looping || timers?.paused == true) return
        looping = true
        postOnAnimation(frame)
    }

    private fun onFrame() {
        if (!looping) return
        if (timers?.paused == true) { looping = false; return }
        val t = now()
        var any = false
        for (f in pool) {
            if (!f.active) continue
            if (t - f.start >= f.dur) f.active = false else any = true
        }
        invalidate()
        if (any) postOnAnimation(frame) else looping = false
    }

    fun pauseAnims() {
        looping = false
        removeCallbacks(frame)
    }

    fun resumeAnims() {
        if (pool.any { it.active }) ensureRunning()
    }

    fun clearAll() {
        for (f in pool) f.active = false
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val t = now()
        val lift = Theme.dpf(context, 70f)
        for (f in pool) {
            if (!f.active || t < f.start) continue
            val q = ((t - f.start).toFloat() / f.dur).coerceIn(0f, 1f)
            val x: Float
            val y: Float
            val deg: Float
            // 튕겨 나가는 곳: 위로 (화면 위 가장자리면 아래로)
            val ky = if (f.y0 - lift * 0.8f > f.r0 * 2f) f.y0 - lift * 0.8f else f.y0 + lift * 0.6f
            val kx = f.x0 + (f.x1 - f.x0) * 0.08f
            if (f.bounce && q < KICK) {
                val u = q / KICK
                val e = sin(u * PI.toFloat() * 0.5f)
                x = f.x0 + (kx - f.x0) * e
                y = f.y0 + (ky - f.y0) * e
                deg = 360f * u
            } else {
                val u = if (f.bounce) (q - KICK) / (1f - KICK) else q
                val e = u * u * (3f - 2f * u)
                val sx = if (f.bounce) kx else f.x0
                val sy = if (f.bounce) ky else f.y0
                val cx = (sx + f.x1) / 2f
                val cy = maxOf(minOf(sy, f.y1) - lift, f.r0 * 1.5f)
                val a = 1f - e
                x = a * a * sx + 2f * a * e * cx + e * e * f.x1
                y = a * a * sy + 2f * a * e * cy + e * e * f.y1
                deg = 360f + 540f * e
            }
            val r = f.r0 + (f.r1 - f.r0) * q
            c.save()
            c.rotate(deg, x, y)
            numPaint.color = f.textColor
            YutArt.drawPiece(c, x, y, r, f.color, f.dark, f.label, fill, numPaint)
            c.restore()
        }
    }

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MAX = 8
        /** 잡기: 위로 튕겨 나가는 앞부분 비율 */
        private const val KICK = 0.3f
    }
}
