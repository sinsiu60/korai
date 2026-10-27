package com.siwoo.party.games.clap369.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.clap369.GameConfig

/**
 * 가운데 차례 판: 왼쪽 "방금" 원(앞사람이 한 숫자) · 가운데 "○○ 차례" + "말하면서 누르세요!" · 오른쪽 이번 차례에 친 짝 표시.
 * 탈락 때는 빨간 판 + 이유. 테두리는 지금 사람 색. 짝 말풍선 톡 튀는 것만 진행 중일 때 다시 그린다.
 */
class TurnView(context: Context) : View(context) {

    fun interface Clock { fun now(): Long }

    var clock: Clock = Clock { SystemClock.uptimeMillis() }
    var flashMs = GameConfig.FLASH_MS
    var frozen = false
        set(v) { field = v; if (!v) invalidate() }

    private var name = ""
    private var color = Theme.Party.NEON_PINK
    private var title = ""
    private var lastText = ""
    private var claps = 0
    private var clapAt = Long.MIN_VALUE / 2
    private var outMode = false
    private var outLine = ""
    private var ready = true

    private val d = Theme.dpf(context, 1f)
    private val body = RectF()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private var shader: Shader? = null

    /** 시작 전 안내 */
    fun setReady() {
        ready = true
        outMode = false
        claps = 0
        invalidate()
    }

    /** 새 차례: name 이 말할 차례, last = 앞사람이 한 숫자 (0 이면 아직 없음) */
    fun setTurn(name: String, color: Int, last: Int) {
        ready = false
        outMode = false
        this.name = name
        this.color = color
        title = "$name 차례"
        lastText = if (last <= 0) START else last.toString()
        claps = 0
        contentDescription = title
        invalidate()
    }

    /** 이번 차례에 친 짝 수 */
    fun setClaps(n: Int) {
        claps = n
        clapAt = clock.now()
        invalidate()
    }

    /** 탈락: "○○ 탈락!" + 이유 한 줄 */
    fun showOut(name: String, line: String) {
        outMode = true
        title = "$name 탈락!"
        outLine = line
        contentDescription = "$title $line"
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        body.set(3f * d, 3f * d, w - 3f * d, h - 3f * d)
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Theme.Party.CARD_LIGHT, Theme.Party.CARD, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val h = body.height()
        val r = 18f * d
        val now = clock.now()
        // 판
        p.style = Paint.Style.FILL
        p.color = 0xFF000000.toInt()
        p.shader = shader
        c.drawRoundRect(body, r, r, p)
        p.shader = null
        if (outMode) {
            p.color = 0x55FF2040
            c.drawRoundRect(body, r, r, p)
        }
        val edge = if (outMode) Theme.RED else if (ready) Theme.Party.NEON_PINK else color
        stroke.color = edge
        stroke.alpha = 80
        stroke.strokeWidth = 7f * d
        c.drawRoundRect(body, r, r, stroke)
        stroke.alpha = 255
        stroke.strokeWidth = 2.5f * d
        c.drawRoundRect(body, r, r, stroke)

        val cy = body.centerY()
        if (ready) {
            text.textSize = h * 0.3f
            text.color = Theme.GOLD
            c.drawText(READY_1, body.centerX(), cy - h * 0.02f, text)
            text.textSize = h * 0.18f
            text.color = Theme.Party.TEXT_SUB
            c.drawText(SAY_HINT, body.centerX(), cy + h * 0.28f, text)
            return
        }
        // 왼쪽 "방금" 원
        val lr = h * 0.36f
        val lx = body.left + h * 0.2f + lr
        p.color = Theme.Party.BG_DEEP
        c.drawCircle(lx, cy + h * 0.06f, lr, p)
        stroke.color = Theme.Party.NEON_SKY
        stroke.strokeWidth = 2f * d
        c.drawCircle(lx, cy + h * 0.06f, lr, stroke)
        text.color = Theme.Party.TEXT_SUB
        text.textSize = h * 0.14f
        c.drawText(LAST, lx, body.top + h * 0.17f, text)
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = if (lastText.length >= 3) lr * 0.72f else lr * 0.9f
        c.drawText(lastText, lx, cy + h * 0.06f + text.textSize * 0.36f, text)

        // 가운데 이름 + 안내
        val mx = body.centerX()
        text.textSize = h * 0.34f
        text.color = if (outMode) 0xFFFFFFFF.toInt() else color
        text.setShadowLayer(5f * d, 0f, 2f * d, 0x99000000.toInt())
        c.drawText(title, mx, cy - h * 0.02f, text)
        text.clearShadowLayer()
        text.textSize = h * 0.2f
        text.color = if (outMode) Theme.GOLD_LIGHT else Theme.GOLD
        c.drawText(if (outMode) outLine else SAY_HINT, mx, cy + h * 0.32f, text)

        // 오른쪽: 이번 차례 짝 수 (손뼉 아이콘 + "짝 ×N")
        var anim = false
        if (!outMode && claps > 0) {
            val f = ((now - clapAt).toFloat() / flashMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            if (f < 1f) anim = true
            val s = h * 0.42f * (1f + 0.25f * (1f - f))
            val rx = body.right - h * 0.95f
            p.color = 0x44FF3FA4
            c.drawCircle(rx, cy - h * 0.06f, h * 0.36f, p)
            Clap369Art.clap(c, rx, cy, s, p, f < 1f)
            text.textSize = h * 0.17f
            text.color = Theme.Party.NEON_PINK
            c.drawText(CLAP_COUNT[claps.coerceAtMost(CLAP_COUNT.size - 1)], rx, body.bottom - h * 0.08f, text)
        }
        if (anim && !frozen) postInvalidateOnAnimation()
    }

    companion object {
        private const val START = "시작"
        private const val LAST = "방금"
        private const val READY_1 = "3 · 6 · 9 가 들어가면 짝!"
        private const val SAY_HINT = "말하면서 누르세요!"
        private val CLAP_COUNT = Array(6) { "짝 ×$it" }
    }
}
