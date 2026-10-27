package com.siwoo.party.games.boxes.ui

import com.siwoo.party.common.ui.PartySettings
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 오른쪽 위 "지금 차례" 판: 윗줄 라벨(왼쪽) + 남은 선 수(오른쪽), 색 동그라미(첫 글자) + 이름(그 사람 색, 크게), 안내 한 줄.
 * 높이가 넉넉하면(2~3인) 아래에 "땅 현황" 막대(사람별 차지한 칸 비율, 회색 = 남은 칸)를 보여 준다.
 * 차례가 바뀌면 톡 커졌다 돌아온다. 글자는 바뀔 때(setter)만 만든다.
 */
class TurnView(context: Context) : View(context) {

    private var label = LABEL_TURN
    private var right = ""
    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    private var sub = ""
    private var pop = 0f
    // 땅 현황
    private var shareColors = IntArray(0)
    private var shares = IntArray(0)
    private var shareTotal = 0
    private var shareText = ""
    private var showBar = false
    private val barRect = RectF()
    private val barPath = Path()
    private var anim: ValueAnimator? = null

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB; textAlign = Paint.Align.RIGHT }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    /** 지금 보이는 안내 (테스트용) */
    val subText: String get() = sub
    val playerName: String get() = name

    /** 차례인 사람 (animate 면 톡) */
    fun setPlayer(playerName: String, playerColor: Int, animate: Boolean) {
        name = playerName
        initial = playerName.take(1)
        color = playerColor
        initPaint.color = Theme.textOn(playerColor)
        contentDescription = playerName + LABEL_SUFFIX
        fitName()
        if (animate) popNow() else invalidate()
    }

    /** 땅 현황 막대 색 (플레이어 순서) */
    fun setShareColors(colors: IntArray) {
        shareColors = colors
        shares = IntArray(colors.size)
    }

    /** 땅 현황: 사람별 칸 수 / 전체 칸 수 */
    fun setShares(scores: IntArray, total: Int) {
        var sum = 0
        for (i in shares.indices) { shares[i] = scores[i]; sum += scores[i] }
        shareTotal = total
        shareText = sum.toString() + " / " + total + "칸"
        invalidate()
    }

    fun setLabel(text: String) {
        if (label == text) return
        label = text
        invalidate()
    }

    /** 오른쪽 위 작은 글자 (남은 선 수 등) */
    fun setRight(text: String) {
        if (right == text) return
        right = text
        invalidate()
    }

    fun setSub(text: String) {
        if (sub == text) return
        sub = text
        fitSub()
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

    private var baseName = 0f
    private var baseSub = 0f
    private var textLeft = 0f
    private var textRight = 0f
    private var headTop = 0f
    private var headH = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = Theme.dpf(context, 1f)
        stroke.strokeWidth = d * 2.5f
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        // 글자 덩어리(라벨·이름·안내) 높이는 최대 84dp. 아래에 땅 현황 막대가 들어갈 만큼 높으면 위쪽에, 아니면 가운데
        val head = minOf(h.toFloat(), d * 84f)
        labelPaint.textSize = minOf(head * 0.15f, d * 12f)
        rightPaint.textSize = labelPaint.textSize
        baseName = minOf(head * 0.3f, d * 24f)
        baseSub = minOf(head * 0.15f, d * 13f)
        val circleR = baseName * 0.62f
        textLeft = rect.left + d * 10f + circleR * 2f + d * 8f
        textRight = rect.right - d * 8f
        initPaint.textSize = circleR
        val barArea = d * 40f
        showBar = h - head >= barArea
        headTop = if (showBar) (h - head - barArea) / 2f else (h - head) / 2f
        headH = head
        if (showBar) {
            val top = headTop + head + d * 18f
            barRect.set(rect.left + d * 10f, top, rect.right - d * 10f, top + d * 12f)
            barPath.reset()
            barPath.addRoundRect(barRect, d * 6f, d * 6f, Path.Direction.CW)
        }
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
        val maxW = rect.width() - Theme.dpf(context, 20f)
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

        val block = labelPaint.textSize * 1.25f + baseName * 1.25f + baseSub * 1.2f
        var y = headTop + (headH - block) / 2f + labelPaint.textSize
        c.drawText(label, rect.left + d * 10f, y, labelPaint)
        if (right.isNotEmpty()) c.drawText(right, rect.right - d * 10f, y, rightPaint)
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
        y += baseSub * 1.6f
        if (sub.isNotEmpty()) c.drawText(sub, rect.left + d * 10f, y, subPaint)

        if (showBar && shareTotal > 0) {
            val ly = barRect.top - d * 5f
            c.drawText(LABEL_SHARE, barRect.left, ly, labelPaint)
            c.drawText(shareText, barRect.right, ly, rightPaint)
            c.save()
            c.clipPath(barPath)
            fill.color = 0x40000000
            c.drawRect(barRect, fill)
            var x = barRect.left
            val unit = barRect.width() / shareTotal
            for (i in shares.indices) {
                if (shares[i] <= 0) continue
                fill.color = shareColors[i]
                c.drawRect(x, barRect.top, x + unit * shares[i], barRect.bottom, fill)
                x += unit * shares[i]
            }
            c.restore()
            stroke.color = Theme.CARD_BORDER
            stroke.strokeWidth = d * 1f
            c.drawPath(barPath, stroke)
            stroke.strokeWidth = d * 2.5f
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        const val LABEL_TURN = "지금 차례"
        private const val LABEL_SHARE = "땅 현황"
        private const val LABEL_SUFFIX = " 차례"
    }
}
