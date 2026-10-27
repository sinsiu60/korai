package com.siwoo.party.games.matches.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 화면 전체를 덮는 투명한 층: 가져간 성냥이 판에서 플레이어 패널 칸으로 빙글 돌며 포물선으로 날아간다.
 * 칸 MAX 개를 미리 만들어 두고 재사용한다. 날아가는 중일 때만 postOnAnimation 으로 다시 그린다.
 * 시계는 판과 같은 게임 시계 (일시정지면 멈춤). 터치는 받지 않는다.
 */
class FlyLayer(context: Context) : View(context) {

    var clock: MatchBoardView.Clock = MatchBoardView.Clock { android.os.SystemClock.uptimeMillis() }

    private val active = BooleanArray(MAX)
    private val x0 = FloatArray(MAX)
    private val y0 = FloatArray(MAX)
    private val x1 = FloatArray(MAX)
    private val y1 = FloatArray(MAX)
    private val a0 = FloatArray(MAX)
    private val len = FloatArray(MAX)
    private val start = LongArray(MAX)
    private val dur = LongArray(MAX)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame = Runnable { onFrame() }
    private var looping = false
    private var framesPaused = false

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** 날아가는 성냥이 있는지 */
    val isFlying: Boolean get() = active.any { it }

    /** (fx, fy) 에서 deg 만큼 기울어진 길이 l 의 성냥이 delayMs 뒤 출발해 flightMs 동안 (tx, ty) 로 */
    fun fly(fx: Float, fy: Float, deg: Float, l: Float, tx: Float, ty: Float, delayMs: Long, flightMs: Long) {
        var k = -1
        for (i in 0 until MAX) if (!active[i]) { k = i; break }
        if (k < 0) return
        active[k] = true
        x0[k] = fx; y0[k] = fy; x1[k] = tx; y1[k] = ty
        a0[k] = deg
        len[k] = l
        start[k] = clock.now() + delayMs
        dur[k] = flightMs.coerceAtLeast(1)
        invalidate()
        if (!looping && !framesPaused) { looping = true; postOnAnimation(frame) }
    }

    fun pauseFrames() {
        framesPaused = true
        looping = false
        removeCallbacks(frame)
    }

    fun resumeFrames() {
        framesPaused = false
        if (isFlying && !looping) { looping = true; postOnAnimation(frame) }
    }

    private fun onFrame() {
        if (!looping) return
        val t = clock.now()
        var any = false
        for (k in 0 until MAX) if (active[k]) { if (t - start[k] >= dur[k]) active[k] = false else any = true }
        invalidate()
        if (any) postOnAnimation(frame) else looping = false
    }

    override fun onDraw(c: Canvas) {
        val t = clock.now()
        val lift = Theme.dpf(context, 70f)
        for (k in 0 until MAX) {
            if (!active[k]) continue
            val q = (t - start[k]).toFloat() / dur[k]
            if (q < 0f) {
                // 출발 전: 제자리에서 살짝 들린 채 기다림
                MatchArt.drawMatch(c, x0[k], y0[k], len[k], a0[k], 0f, 1f, paint)
                continue
            }
            if (q >= 1f) continue
            val e = 1f - (1f - q) * (1f - q) * (1f - q)
            // 2차 베지어 (가운데 위로 솟음)
            val cx = (x0[k] + x1[k]) / 2f
            val cy = minOf(y0[k], y1[k]) - lift
            val u = 1f - e
            val x = u * u * x0[k] + 2f * u * e * cx + e * e * x1[k]
            val y = u * u * y0[k] + 2f * u * e * cy + e * e * y1[k]
            val s = 1f - 0.55f * e
            val alpha = if (q > 0.85f) 1f - (q - 0.85f) / 0.15f else 1f
            MatchArt.drawMatch(c, x, y, len[k] * s, a0[k] + 540f * e, 0f, alpha, paint)
        }
    }

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MAX = 8
    }
}
