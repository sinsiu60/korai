package com.siwoo.party.hub

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import com.siwoo.party.common.ui.Theme

/**
 * 허브 오른쪽 큰 "🎉 파티 존" 입구 카드.
 * 네온 보라·분홍 테두리가 천천히 빛난다: 빛 테두리는 따로 한 번만 그린 층(하드웨어 레이어)이고
 * 투명도만 초당 12번 바꾼다 → 카드를 다시 그리지 않음. 화면이 안 보이면(onPause·detach) 멈춘다.
 */
class PartyEntranceView(context: Context) : FrameLayout(context) {

    private val face = Face(context)
    private val glow = Glow(context)
    private val handler = Handler(Looper.getMainLooper())
    private var t0 = 0L
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val phase = ((System.currentTimeMillis() - t0) % PERIOD_MS) / PERIOD_MS.toFloat()
            // 0.35 ~ 1.0 사이로 숨 쉬듯
            glow.alpha = 0.35f + 0.65f * (0.5f - 0.5f * Math.cos(phase * Math.PI * 2).toFloat())
            handler.postDelayed(this, FRAME_MS)
        }
    }

    init {
        isClickable = true
        contentDescription = "파티 존"
        addView(glow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(face, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        glow.setLayerType(LAYER_TYPE_HARDWARE, null)
        face.isClickable = false
    }

    /** 파티 게임 수 */
    fun setCount(n: Int) = face.setCount(n)

    fun startGlow() {
        if (running) return
        running = true
        t0 = System.currentTimeMillis()
        handler.post(tick)
    }

    fun stopGlow() {
        running = false
        handler.removeCallbacks(tick)
    }

    val glowing: Boolean get() = running

    override fun onDetachedFromWindow() {
        stopGlow()
        super.onDetachedFromWindow()
    }

    /** 바깥 빛 테두리 (여러 겹 선, 한 번만 그림) */
    private class Glow(context: Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val d = Theme.dpf(context, 1f)
            for (k in 0 until 6) {
                val inset = d * (1f + k * 1.6f)
                r.set(inset, inset, w - inset, h - inset)
                p.strokeWidth = d * 3f
                p.color = if (k % 2 == 0) Theme.Party.NEON_PINK else Theme.Party.NEON_PURPLE
                p.alpha = 200 - k * 32
                c.drawRoundRect(r, d * 20f, d * 20f, p)
            }
        }
    }

    /** 카드 본체: 보라 그라데이션 + 큰 글자 + 네온 사람·말풍선 그림 */
    private class Face(context: Context) : View(context) {
        private val r = RectF()
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
        private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.Party.NEON_SKY }
        private var shader: Shader? = null
        private var countText = ""

        fun setCount(n: Int) {
            countText = "파티 게임 ${n}종"
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Theme.Party.CARD_LIGHT, Theme.Party.BG_DEEP, Shader.TileMode.CLAMP)
            title.textSize = minOf(w * 0.17f, h * 0.1f)
            sub.textSize = title.textSize * 0.55f
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val d = Theme.dpf(context, 1f)
            val inset = d * 9f
            r.set(inset, inset, w - inset, h - inset)
            bg.shader = shader
            c.drawRoundRect(r, d * 16f, d * 16f, bg)
            bg.shader = null
            line.strokeWidth = d * 2.5f
            line.color = Theme.Party.NEON_PINK
            c.drawRoundRect(r, d * 16f, d * 16f, line)
            // 네온 그림: 두 사람 + 말풍선 + 별
            val cx = w / 2f
            val cy = h * 0.36f
            val s = minOf(w, h) * 0.2f
            line.strokeWidth = d * 3f
            line.color = Theme.Party.NEON_SKY
            c.drawCircle(cx - s * 0.7f, cy - s * 0.3f, s * 0.28f, line)
            c.drawArc(cx - s * 1.2f, cy + s * 0.05f, cx - s * 0.2f, cy + s * 1.0f, 200f, 140f, false, line)
            line.color = Theme.Party.NEON_PINK
            c.drawCircle(cx + s * 0.7f, cy - s * 0.3f, s * 0.28f, line)
            c.drawArc(cx + s * 0.2f, cy + s * 0.05f, cx + s * 1.2f, cy + s * 1.0f, 200f, 140f, false, line)
            line.color = Theme.GOLD
            r.set(cx - s * 0.45f, cy - s * 1.35f, cx + s * 0.45f, cy - s * 0.8f)
            c.drawRoundRect(r, s * 0.2f, s * 0.2f, line)
            c.drawLine(cx - s * 0.1f, cy - s * 0.8f, cx - s * 0.25f, cy - s * 0.6f, line)
            c.drawText("🎉 파티 존", cx, h * 0.72f, title)
            c.drawText(countText, cx, h * 0.72f + sub.textSize * 1.5f, sub)
        }
    }

    companion object {
        const val PERIOD_MS = 2600L
        const val FRAME_MS = 83L
    }
}
