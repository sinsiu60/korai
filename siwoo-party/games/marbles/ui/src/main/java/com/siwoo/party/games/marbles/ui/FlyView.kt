package com.siwoo.party.games.marbles.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.math.sin

/**
 * 획득한 구슬이 마당에서 내 패널로 포물선을 그리며 날아가는 층 (게임 화면 전체를 덮는 투명 View, 터치 안 받음).
 * 날아가는 동안에만 postInvalidateOnAnimation 으로 다시 그리고, 모두 도착하면 멈춘다.
 * 슬롯은 미리 만든 배열(최대 MAX)을 재사용 — 그리기 중 할당 없음.
 */
class FlyView(context: Context, private val skins: MarbleSkins) : View(context) {

    private val x0 = FloatArray(MAX)
    private val y0 = FloatArray(MAX)
    private val x1 = FloatArray(MAX)
    private val y1 = FloatArray(MAX)
    private val start = LongArray(MAX)
    private val dur = LongArray(MAX)
    private val radius = FloatArray(MAX)
    /** 0..9 가운데 구슬 색, 100+i = i번 플레이어의 내 구슬 */
    private val skin = IntArray(MAX)
    private val used = BooleanArray(MAX)
    private val aux = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 날아가는 중인 구슬 수 */
    var flying = 0
        private set

    init {
        isClickable = false
        isFocusable = false
    }

    /**
     * (sx, sy) → (ex, ey) 로 delayMs 뒤 flightMs 동안 날아감 (이 View 좌표, px).
     * target = 가운데 구슬 색 번호 또는 −1 (그때는 player 의 내 구슬)
     */
    fun fly(sx: Float, sy: Float, ex: Float, ey: Float, r: Float, target: Int, player: Int, delayMs: Long, flightMs: Long) {
        var slot = -1
        for (i in 0 until MAX) if (!used[i]) { slot = i; break }
        if (slot < 0) return
        used[slot] = true
        x0[slot] = sx
        y0[slot] = sy
        x1[slot] = ex
        y1[slot] = ey
        radius[slot] = r
        skin[slot] = if (target >= 0) target else PLAYER_BASE + player
        start[slot] = SystemClock.uptimeMillis() + delayMs
        dur[slot] = flightMs.coerceAtLeast(1L)
        flying++
        postInvalidateOnAnimation()
    }

    /** 모두 지움 */
    fun clear() {
        for (i in 0 until MAX) used[i] = false
        flying = 0
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (flying == 0) return
        val now = SystemClock.uptimeMillis()
        for (i in 0 until MAX) {
            if (!used[i]) continue
            val t = now - start[i]
            if (t < 0L) {
                draw(c, i, 0f)
                continue
            }
            if (t >= dur[i]) {
                used[i] = false
                flying--
                continue
            }
            draw(c, i, t.toFloat() / dur[i])
        }
        if (flying > 0) postInvalidateOnAnimation()
    }

    private fun draw(c: Canvas, i: Int, f: Float) {
        // 천천히 출발 → 빨라짐 (ease-in-out), 위로 솟았다가 내려앉음
        val e = f * f * (3f - 2f * f)
        val dx = x1[i] - x0[i]
        val dy = y1[i] - y0[i]
        val lift = (kotlin.math.abs(dx) * 0.25f + radius[i] * 3f) * sin(e * PI_F)
        val x = x0[i] + dx * e
        val y = y0[i] + dy * e - lift
        val r = radius[i] * (1f + 0.35f * sin(e * PI_F) - 0.35f * e)
        val k = skin[i]
        val alpha = if (f > 0.85f) ((1f - (f - 0.85f) / 0.15f) * 255).toInt().coerceIn(0, 255) else 255
        if (k >= PLAYER_BASE) {
            val p = k - PLAYER_BASE
            val body = if (skins.baseR > 0f) skins.player[p] else null
            MarbleArt.draw(c, x, y, r, skins.playerColor(p), aux, body, skins.baseR, e * 540f, alpha, true, true)
        } else {
            val body = if (skins.baseR > 0f) skins.target[k] else null
            MarbleArt.draw(c, x, y, r, MarbleArt.COLORS[k], aux, body, skins.baseR, e * 540f, alpha, true, false)
        }
    }

    override fun onDetachedFromWindow() {
        clear()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MAX = 16
        private const val PLAYER_BASE = 100
        private const val PI_F = 3.1415927f
    }
}
