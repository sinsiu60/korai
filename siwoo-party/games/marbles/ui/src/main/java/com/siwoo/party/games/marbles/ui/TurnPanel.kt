package com.siwoo.party.games.marbles.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.marbles.GameConfig

/**
 * 오른쪽 위 "지금 차례" 판: 색 동그라미(첫 글자) + 이름(그 사람 색, 크게) + 안내 한 줄
 * + (높이가 되면) 남은 가운데 구슬 10칸 + 몇 바퀴째.
 * 차례가 바뀌면 톡 커졌다 돌아온다. 글자는 바뀔 때(setter)만 만든다 — onDraw 할당 없음.
 */
class TurnPanel(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    private var hint = ""
    private var remaining = GameConfig.TARGETS
    private var roundText = ""
    private var remainText = ""
    private var pop = 0f
    private var anim: ValueAnimator? = null

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val aux = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB; textAlign = Paint.Align.RIGHT }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x55FFFFFF }

    // 배치 (onSizeChanged)
    private var showTray = false
    private var headTop = 0f
    private var headH = 0f
    private var trayTop = 0f
    private var cell = 0f
    private var baseName = 0f
    private var baseHint = 0f
    private var textLeft = 0f
    private var textRight = 0f

    /** 안내 글자 (테스트 확인용) */
    val hintText: String get() = hint
    val playerName: String get() = name

    /** 차례인 사람 (animate = 톡) */
    fun setPlayer(playerName: String, playerColor: Int, animate: Boolean) {
        name = playerName
        initial = playerName.take(1)
        color = playerColor
        initPaint.color = Theme.textOn(playerColor)
        contentDescription = playerName + LABEL_TURN
        fitName()
        if (animate) popNow() else invalidate()
    }

    fun setHint(text: String) {
        if (hint == text) return
        hint = text
        fitHint()
        invalidate()
    }

    /** 남은 가운데 구슬 수 */
    fun setRemaining(n: Int) {
        if (remaining == n && remainText.isNotEmpty()) return
        remaining = n
        remainText = LABEL_REMAIN + n + UNIT
        invalidate()
    }

    /** 몇 바퀴째 */
    fun setRound(r: Int) {
        roundText = r.toString() + "/" + GameConfig.MAX_ROUNDS + UNIT_ROUND
        invalidate()
    }

    private fun popNow() {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = PartySettings(context).t(320)
            addUpdateListener { pop = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stroke.strokeWidth = 2.5f * d
        slotPaint.strokeWidth = 1.2f * d
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        // 글자 덩어리(라벨·이름·안내) 높이 최대 88dp
        val head = minOf(h.toFloat(), 88f * d)
        labelPaint.textSize = minOf(head * 0.15f, 12f * d)
        roundPaint.textSize = labelPaint.textSize
        baseName = minOf(head * 0.3f, 23f * d)
        baseHint = minOf(head * 0.15f, 12f * d)
        val circleR = baseName * 0.62f
        textLeft = rect.left + 10f * d + circleR * 2f + 8f * d
        textRight = rect.right - 8f * d
        initPaint.textSize = circleR
        // 남은 구슬 5칸 × 2줄이 들어갈 만큼 높으면 아래에
        cell = minOf((rect.width() - 20f * d) / 5f, 26f * d)
        val tray = cell * 2f + 22f * d
        showTray = h - head >= tray
        headTop = if (showTray) 0f else (h - head) / 2f
        headH = head
        trayTop = if (showTray) head + (h - head - tray) / 2f + 18f * d else 0f
        fitName()
        fitHint()
    }

    /** 이름이 폭에 맞게 글자 크기를 줄임 (setter·크기 바뀔 때만) */
    private fun fitName() {
        if (baseName <= 0f) return
        var s = baseName
        namePaint.textSize = s
        val maxW = textRight - textLeft
        while (namePaint.measureText(name) > maxW && s > baseName * 0.5f) {
            s *= 0.92f
            namePaint.textSize = s
        }
    }

    private fun fitHint() {
        if (baseHint <= 0f) return
        var s = baseHint
        hintPaint.textSize = s
        val maxW = rect.width() - 18f * d
        while (hintPaint.measureText(hint) > maxW && s > baseHint * 0.6f) {
            s *= 0.92f
            hintPaint.textSize = s
        }
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val r = 14f * d
        fill.color = Theme.CARD
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = color
        c.drawRoundRect(rect, r, r, stroke)

        // 라벨 / 이름 줄 / 안내 — 머리 영역 세로 가운데
        val block = labelPaint.textSize * 1.25f + baseName * 1.25f + baseHint * 1.2f
        var y = headTop + (headH - block) / 2f + labelPaint.textSize
        val left = rect.left + 10f * d
        c.drawText(LABEL_TOP, left, y, labelPaint)
        if (!showTray && roundText.isNotEmpty()) c.drawText(roundText, rect.right - 9f * d, y, roundPaint)
        y += baseName * 1.2f
        val cr = baseName * 0.62f * (1f + 0.25f * pop)
        val ccx = left + baseName * 0.62f
        val ccy = y - baseName * 0.36f
        fill.color = color
        c.drawCircle(ccx, ccy, cr, fill)
        stroke.color = Theme.TEXT
        stroke.strokeWidth = 1.5f * d
        c.drawCircle(ccx, ccy, cr, stroke)
        stroke.strokeWidth = 2.5f * d
        c.drawText(initial, ccx, ccy + initPaint.textSize * 0.36f, initPaint)
        namePaint.color = color
        c.save()
        val sc = 1f + 0.12f * pop
        c.scale(sc, sc, textLeft, y)
        c.drawText(name, textLeft, y, namePaint)
        c.restore()
        y += baseHint * 1.5f
        if (hint.isNotEmpty()) c.drawText(hint, left, y, hintPaint)

        if (showTray) {
            c.drawText(remainText, left, trayTop - 7f * d, labelPaint)
            c.drawText(roundText, rect.right - 9f * d, trayTop - 7f * d, roundPaint)
            val gw = cell * 5f
            val x0 = rect.centerX() - gw / 2f
            for (k in 0 until GameConfig.TARGETS) {
                val x = x0 + (k % 5) * cell + cell / 2f
                val yy = trayTop + (k / 5) * cell + cell / 2f
                if (k < remaining) MarbleArt.draw(c, x, yy, cell * 0.34f, MarbleArt.COLORS[k], aux, shadow = false)
                else c.drawCircle(x, yy, cell * 0.3f, slotPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val LABEL_TOP = "지금 차례"
        private const val LABEL_TURN = " 차례"
        private const val LABEL_REMAIN = "남은 구슬 "
        private const val UNIT = "개"
        private const val UNIT_ROUND = "바퀴"
    }
}
