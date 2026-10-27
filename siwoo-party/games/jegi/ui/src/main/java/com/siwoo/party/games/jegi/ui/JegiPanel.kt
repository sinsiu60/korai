package com.siwoo.party.games.jegi.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.jegi.GameConfig

/**
 * 오른쪽 위 차례 판: [1번째 도전 / 2] 알약 | 색 동그라미 + 이름 | 이번 도전 개수(아주 크게) |
 * 판정 구간 막대 (찰수록 줄어듦) | 이 사람의 1차·2차 기록 | 안내 한 줄.
 * 높이가 모자라면 전체를 같은 비율로 줄인다. 글자는 setter 에서만 만든다 (onDraw 할당 없음).
 */
class JegiPanel(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)

    private var attemptText = ""
    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    private var countText = "0"
    private var windowText = ""
    private var windowFrac = 1f
    private var recordText = ""
    private var hint = ""
    private var pop = 0f
    private var anim: ValueAnimator? = null

    private val rect = RectF()
    private val tmp = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.INK; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB; textAlign = Paint.Align.RIGHT }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Theme.GOLD }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }

    /** 1 dp 를 몇 px 로 그릴지 (높이가 모자라면 줄어듦) */
    private var u = 1f

    // 테스트 확인용
    val hintText: String get() = hint
    val playerName: String get() = name
    val shownCount: String get() = countText
    val shownRecord: String get() = recordText
    val shownAttempt: String get() = attemptText

    fun setAttempt(attempt: Int, total: Int) {
        attemptText = attempt.toString() + "번째 도전 / " + total
        invalidate()
    }

    /** 차례인 사람 (animate = 톡) */
    fun setPlayer(playerName: String, playerColor: Int, animate: Boolean) {
        name = playerName
        initial = playerName.take(1)
        color = playerColor
        initPaint.color = Theme.textOn(playerColor)
        contentDescription = playerName + " 차례"
        fitName()
        if (animate) popNow() else invalidate()
    }

    fun setCount(text: String) {
        if (countText == text) return
        countText = text
        fitBig()
        invalidate()
    }

    /** 판정 구간 (± ms) → 막대 길이 */
    fun setWindow(ms: Long) {
        windowText = "판정 구간 ±" + ms + "ms"
        val span = (GameConfig.WINDOW_START_MS - GameConfig.WINDOW_MIN_MS).toFloat()
        windowFrac = 0.25f + 0.75f * ((ms - GameConfig.WINDOW_MIN_MS) / span).coerceIn(0f, 1f)
        invalidate()
    }

    /** 이 사람의 도전 기록 줄 ("1차 7개 · 2차 –") */
    fun setRecord(text: String) {
        recordText = text
        invalidate()
    }

    fun setHint(text: String) {
        if (hint == text) return
        hint = text
        fitHint()
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
        u = minOf(d, h / NEED_DP)
        stroke.strokeWidth = 2.5f * d
        rect.set(stroke.strokeWidth, stroke.strokeWidth, w - stroke.strokeWidth, h - stroke.strokeWidth)
        pillText.textSize = 12f * u
        subPaint.textSize = 11f * u
        initPaint.textSize = 14f * u
        unitPaint.textSize = 18f * u
        smallPaint.textSize = 12.5f * u
        fitName()
        fitBig()
        fitHint()
    }

    private fun fitName() {
        if (rect.width() <= 0f) return
        var s = 21f * u
        namePaint.textSize = s
        val maxW = rect.width() - 20f * u - 30f * u
        while (namePaint.measureText(name) > maxW && s > 10f * u) {
            s *= 0.92f
            namePaint.textSize = s
        }
    }

    private fun fitBig() {
        if (rect.width() <= 0f) return
        bigPaint.textSize = 50f * u
    }

    private fun fitHint() {
        if (rect.width() <= 0f) return
        var s = 12.5f * u
        hintPaint.textSize = s
        val maxW = rect.width() - 20f * u
        while (hintPaint.measureText(hint) > maxW && s > 7f * u) {
            s *= 0.93f
            hintPaint.textSize = s
        }
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val r = 14f * d
        fill.color = Theme.CARD
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = color
        stroke.strokeWidth = 2.5f * d
        c.drawRoundRect(rect, r, r, stroke)
        val block = NEED_DP * u - 16f * u
        var y = rect.top + (rect.height() - block) / 2f
        val left = rect.left + 10f * u
        val right = rect.right - 10f * u

        // ① 도전 알약
        if (attemptText.isNotEmpty()) {
            val pw = pillText.measureText(attemptText) + 16f * u
            tmp.set(left, y, left + pw, y + 20f * u)
            fill.color = Theme.GOLD
            c.drawRoundRect(tmp, 10f * u, 10f * u, fill)
            c.drawText(attemptText, tmp.centerX(), tmp.centerY() + pillText.textSize * 0.36f, pillText)
        }
        y += 26f * u

        // ② 이름 줄
        val cr = 12f * u * (1f + 0.25f * pop)
        val ccx = left + 12f * u
        val ccy = y + 14f * u
        fill.color = color
        c.drawCircle(ccx, ccy, cr, fill)
        stroke.color = Theme.TEXT
        stroke.strokeWidth = 1.5f * d
        c.drawCircle(ccx, ccy, cr, stroke)
        c.drawText(initial, ccx, ccy + initPaint.textSize * 0.36f, initPaint)
        namePaint.color = color
        val nx = left + 30f * u
        c.save()
        val sc = 1f + 0.12f * pop
        c.scale(sc, sc, nx, ccy)
        c.drawText(name, nx, ccy + namePaint.textSize * 0.36f, namePaint)
        c.restore()
        y += 32f * u

        // ③ 큰 개수
        val cx = rect.centerX() + 12f * u
        val by = y + 46f * u
        c.drawText(countText, cx, by, bigPaint)
        c.drawText(UNIT, cx + 4f * u, by, unitPaint)
        y += 56f * u

        // ④ 판정 구간 막대
        if (windowText.isNotEmpty()) {
            c.drawText(windowText, left + 2f * u, y + 11f * u, smallPaint)
            tmp.set(left, y + 17f * u, right, y + 25f * u)
            fill.color = 0x44000000
            c.drawRoundRect(tmp, 4f * u, 4f * u, fill)
            val mid = tmp.centerX()
            val half = tmp.width() / 2f * windowFrac
            tmp.set(mid - half, tmp.top, mid + half, tmp.bottom)
            fill.color = Theme.GOLD
            c.drawRoundRect(tmp, 4f * u, 4f * u, fill)
            fill.color = Theme.TEXT
            c.drawRect(mid - 1f * u, tmp.top - 2f * u, mid + 1f * u, tmp.bottom + 2f * u, fill)
        }
        y += 30f * u

        // ⑤ 기록 줄
        if (recordText.isNotEmpty()) {
            smallPaint.color = Theme.TEXT_SUB
            c.drawText(recordText, left + 2f * u, y + 12f * u, smallPaint)
            smallPaint.color = Theme.TEXT
        }
        y += 20f * u
        // ⑥ 안내
        if (hint.isNotEmpty()) c.drawText(hint, left + 2f * u, y + 12f * u, hintPaint)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val UNIT = "개"
        /** 내용 전체가 필요한 높이 (dp, 여백 포함): 알약 26 + 이름 32 + 개수 56 + 막대 30 + 기록 20 + 안내 16 + 위아래 16 */
        private const val NEED_DP = 26f + 32f + 56f + 30f + 20f + 16f + 16f
    }
}
