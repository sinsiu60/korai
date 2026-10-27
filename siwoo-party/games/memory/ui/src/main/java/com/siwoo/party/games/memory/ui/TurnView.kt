package com.siwoo.party.games.memory.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 오른쪽 위 "지금 차례" 판: 색 동그라미(첫 글자) + 이름(그 사람 색, 크게) + 안내 한 줄.
 * 높이가 넉넉하면(2~3인) 아래에 "남은 그림" 12칸을 보여 준다 (찾은 그림은 빈 동그라미).
 * 차례가 바뀌면 톡 커졌다 돌아온다. 글자는 바뀔 때(setter)만 만든다.
 */
class TurnView(context: Context) : View(context) {

    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    private var sub = ""
    private val found = BooleanArray(CardArt.SYMBOL_COUNT)
    private var pop = 0f
    private var anim: ValueAnimator? = null

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x44FFFFFF }

    /** 트레이(남은 그림)를 그릴 만큼 높은지 — onSizeChanged 에서 정함 */
    private var showTray = false
    private var trayTop = 0f
    private var headTop = 0f
    private var headH = 0f
    private var cellSize = 0f

    /** 차례인 사람 (바뀌면 톡) */
    fun setPlayer(playerName: String, playerColor: Int, animate: Boolean) {
        name = playerName
        initial = playerName.take(1)
        color = playerColor
        initPaint.color = Theme.textOn(playerColor)
        contentDescription = playerName + LABEL
        fitName()
        if (animate) popNow() else invalidate()
    }

    fun setSub(text: String) {
        if (sub == text) return
        sub = text
        fitSub()
        invalidate()
    }

    /** 찾은 그림 표시 (CardArt 번호) */
    fun setFound(symbol: Int) {
        found[symbol] = true
        invalidate()
    }

    private fun popNow() {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 320
            addUpdateListener { pop = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private var baseName = 0f
    private var baseSub = 0f
    private var textLeft = 0f
    private var textRight = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = Theme.dpf(context, 1f)
        stroke.strokeWidth = d * 2.5f
        slotPaint.strokeWidth = d * 1.2f
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        // 글자 덩어리(라벨·이름·안내) 높이는 최대 92dp
        val head = minOf(h.toFloat(), d * 92f)
        labelPaint.textSize = minOf(head * 0.15f, d * 12f)
        baseName = minOf(head * 0.3f, d * 24f)
        baseSub = minOf(head * 0.15f, d * 12.5f)
        val circleR = baseName * 0.62f
        textLeft = rect.left + d * 10f + circleR * 2f + d * 8f
        textRight = rect.right - d * 8f
        initPaint.textSize = circleR
        // 트레이(남은 그림 4열 × 3줄)가 들어갈 만큼 높으면 글자는 위, 트레이는 남는 곳 가운데.
        // 아니면 글자 덩어리를 전체 높이 가운데에
        cellSize = minOf((rect.width() - d * 20f) / 4f, d * 28f)
        val tray = cellSize * 3f + d * 18f
        showTray = h - head >= tray
        headTop = if (showTray) 0f else (h - head) / 2f
        headH = head
        trayTop = if (showTray) head + (h - head - tray) / 2f + d * 16f else 0f
        fitName()
        fitSub()
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

    private fun fitSub() {
        if (baseSub <= 0f) return
        var s = baseSub
        subPaint.textSize = s
        val maxW = rect.width() - Theme.dpf(context, 16f)
        while (subPaint.measureText(sub) > maxW && s > baseSub * 0.6f) {
            s *= 0.92f
            subPaint.textSize = s
        }
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val d = Theme.dpf(context, 1f)
        val r = d * 14f
        fill.color = Theme.CARD
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = color
        c.drawRoundRect(rect, r, r, stroke)

        // 덩어리: 라벨 / 이름 줄 / 안내 — 머리 영역 세로 가운데
        val block = labelPaint.textSize * 1.25f + baseName * 1.25f + baseSub * 1.2f
        var y = headTop + (headH - block) / 2f + labelPaint.textSize
        c.drawText(LABEL_TOP, rect.left + d * 10f, y, labelPaint)
        y += baseName * 1.2f
        // 색 동그라미 + 첫 글자 (톡 커짐)
        val cr = baseName * 0.62f * (1f + 0.25f * pop)
        val ccx = rect.left + d * 10f + baseName * 0.62f
        val ccy = y - baseName * 0.36f
        fill.color = color
        c.drawCircle(ccx, ccy, cr, fill)
        stroke.color = Theme.TEXT
        stroke.strokeWidth = d * 1.5f
        c.drawCircle(ccx, ccy, cr, stroke)
        stroke.strokeWidth = d * 2.5f
        c.drawText(initial, ccx, ccy + initPaint.textSize * 0.36f, initPaint)
        namePaint.color = color
        c.save()
        val sc = 1f + 0.12f * pop
        c.scale(sc, sc, textLeft, y)
        c.drawText(name, textLeft, y, namePaint)
        c.restore()
        y += baseSub * 1.5f
        if (sub.isNotEmpty()) c.drawText(sub, rect.left + d * 10f, y, subPaint)

        if (showTray) {
            c.drawText(LABEL_TRAY, rect.left + d * 10f, trayTop - d * 5f, labelPaint)
            val gw = cellSize * 4f
            val left = rect.centerX() - gw / 2f
            for (k in 0 until CardArt.SYMBOL_COUNT) {
                val x = left + (k % 4) * cellSize + cellSize / 2f
                val yy = trayTop + (k / 4) * cellSize + cellSize / 2f
                if (found[k]) c.drawCircle(x, yy, cellSize * 0.3f, slotPaint)
                else CardArt.drawSymbol(c, k, x, yy, cellSize * 0.36f, art)
            }
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val LABEL = " 차례"
        private const val LABEL_TOP = "지금 차례"
        private const val LABEL_TRAY = "남은 그림"
    }
}
