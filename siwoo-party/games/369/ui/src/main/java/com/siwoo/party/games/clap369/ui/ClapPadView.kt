package com.siwoo.party.games.clap369.ui

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
import com.siwoo.party.games.clap369.GameConfig
import com.siwoo.party.games.clap369.Key

/**
 * 큰 버튼 두 개: 왼쪽 [숫자] (지금 숫자를 크게 적음 — 3·6·9 가 있으면 누르면 안 되는 함정),
 * 오른쪽 [짝!] (손뼉 그림). 손가락이 닿는 순간(DOWN) 바로 반응한다 (빠른 짝짝 연타).
 * 꺼져 있으면(차례 사이) 회색. 누른 반짝임은 진행 중일 때만 다시 그린다.
 */
class ClapPadView(context: Context) : View(context) {

    fun interface Listener { fun onKey(key: Key) }
    fun interface Clock { fun now(): Long }

    var listener: Listener? = null
    var clock: Clock = Clock { SystemClock.uptimeMillis() }
    var flashMs = GameConfig.FLASH_MS
    var frozen = false
        set(v) { field = v; if (!v) invalidate() }

    /** 버튼을 받는지 */
    var active = false
        set(v) { field = v; if (!v) { held[0] = false; held[1] = false }; invalidate() }

    private var numberText = "1"
    private val held = BooleanArray(2)
    private val flashAt = LongArray(2) { Long.MIN_VALUE / 2 }
    private val pointerKey = IntArray(10) { -1 }

    private val d = Theme.dpf(context, 1f)
    private val numRect = RectF()
    private val clapRect = RectF()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        isClickable = true
        contentDescription = "숫자 / 짝 버튼"
    }

    fun setNumber(n: Int) {
        numberText = n.toString()
        invalidate()
    }

    /** 버튼 가운데 (이 View 좌표) */
    fun buttonCenter(key: Key, out: PointF) {
        val r = if (key == Key.NUMBER) numRect else clapRect
        out.set(r.centerX(), r.centerY())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val gap = 14f * d
        val pad = 4f * d
        val nw = (w - gap - 2f * pad) * 0.52f
        numRect.set(pad, pad, pad + nw, h - pad)
        clapRect.set(numRect.right + gap, pad, w - pad, h - pad)
    }

    private fun keyAt(x: Float, y: Float): Int {
        val s = 6f * d
        if (x >= numRect.left - s && x <= numRect.right + s && y >= numRect.top - s && y <= numRect.bottom + s) return 0
        if (x >= clapRect.left - s && x <= clapRect.right + s && y >= clapRect.top - s && y <= clapRect.bottom + s) return 1
        return -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val i = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val id = e.getPointerId(i)
                val k = keyAt(e.getX(i), e.getY(i))
                if (id < pointerKey.size) pointerKey[id] = k
                if (k >= 0 && active) {
                    held[k] = true
                    flashAt[k] = clock.now()
                    invalidate()
                    listener?.onKey(if (k == 0) Key.NUMBER else Key.CLAP)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(i)
                if (id < pointerKey.size) {
                    val k = pointerKey[id]
                    pointerKey[id] = -1
                    if (k >= 0) { held[k] = false; invalidate() }
                }
            }
            MotionEvent.ACTION_CANCEL -> { pointerKey.fill(-1); held[0] = false; held[1] = false; invalidate() }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val now = clock.now()
        var anim = false
        val depth = 7f * d
        for (k in 0 until 2) {
            val r = if (k == 0) numRect else clapRect
            val f = ((now - flashAt[k]).toFloat() / flashMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            if (f < 1f) anim = true
            // 누른 순간 빛
            if (f < 1f && active) {
                p.style = Paint.Style.FILL
                p.color = if (k == 0) Theme.Party.NEON_SKY else Theme.Party.NEON_PINK
                p.alpha = (120 * (1f - f)).toInt()
                val g = 8f * d * (0.5f + f)
                c.drawRoundRect(r.left - g, r.top - g, r.right + g, r.bottom + g, r.height() * 0.25f, r.height() * 0.25f, p)
                p.alpha = 255
            }
            val col = if (k == 0) NUMBER_BLUE else CLAP_PINK
            Clap369Art.slab(c, r, col, held[k] && active, !active, depth, p)
            val off = if (held[k] && active) depth * 0.8f else 0f
            val faceH = r.height() - depth
            val txt = if (active) 0xFFFFFFFF.toInt() else 0x99FFFFFF.toInt()
            if (k == 0) {
                // 숫자 크게 + "숫자" 작게
                big.textSize = (faceH * 0.52f).coerceAtMost(r.width() / (numberText.length * 0.62f + 0.4f))
                big.color = txt
                big.setShadowLayer(4f * d, 0f, 2f * d, 0x66000000)
                c.drawText(numberText, r.centerX(), r.top + off + faceH * 0.56f, big)
                big.clearShadowLayer()
                small.textSize = faceH * 0.15f
                small.color = if (active) 0xFFE0F7FF.toInt() else 0x88FFFFFF.toInt()
                c.drawText(SAY, r.centerX(), r.top + off + faceH * 0.86f, small)
            } else {
                val s = faceH * 0.5f
                val pop = if (f < 1f && active) 1f + 0.12f * (1f - f) else 1f
                c.save()
                c.scale(pop, pop, r.centerX() - r.width() * 0.17f, r.top + off + faceH * 0.52f)
                Clap369Art.clap(c, r.centerX() - r.width() * 0.17f, r.top + off + faceH * 0.52f, s, p, active)
                c.restore()
                big.textSize = faceH * 0.36f
                big.color = txt
                big.setShadowLayer(4f * d, 0f, 2f * d, 0x66000000)
                c.drawText(CLAP, r.centerX() + r.width() * 0.2f, r.top + off + faceH * 0.62f, big)
                big.clearShadowLayer()
            }
        }
        if (anim && !frozen) postInvalidateOnAnimation()
    }

    companion object {
        const val NUMBER_BLUE = 0xFF2B8CFF.toInt()
        const val CLAP_PINK = 0xFFFF3FA4.toInt()
        private const val SAY = "숫자 말하기"
        private const val CLAP = "짝!"
    }
}
