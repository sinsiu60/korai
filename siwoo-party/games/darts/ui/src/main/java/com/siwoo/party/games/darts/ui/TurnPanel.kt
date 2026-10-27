package com.siwoo.party.games.darts.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.darts.GameConfig

/**
 * 오른쪽 위 차례 판: [3 / 8 라운드] 알약 + 방식 | 색 동그라미 + 이름(크게) + 점수 |
 * 이번 차례 다트 3칸 (번호 · "트리플 20 = 60" · +60) | 합계 줄 | 안내 한 줄.
 * 높이가 모자라면 전체를 같은 비율로 줄인다. 글자는 setter 에서만 만든다 (onDraw 할당 없음).
 */
class TurnPanel(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)

    private var roundText = ""
    private var modeText = ""
    private var name = ""
    private var initial = ""
    private var color = Theme.GOLD
    private var scoreText = ""
    private var totalText = ""
    private var totalColor = Theme.TEXT
    private var hint = ""
    private val dartLabel = arrayOfNulls<String>(SLOTS)
    private val dartPoints = arrayOfNulls<String>(SLOTS)
    private val dartColor = IntArray(SLOTS)
    private var pop = 0f
    private var anim: ValueAnimator? = null

    private val rect = RectF()
    private val tmp = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.INK; textAlign = Paint.Align.CENTER }
    private val modePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB; textAlign = Paint.Align.RIGHT }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD; textAlign = Paint.Align.RIGHT }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val ptsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
    private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }

    /** 1 dp 를 몇 px 로 그릴지 (높이가 모자라면 줄어듦) */
    private var u = 1f

    // 테스트 확인용
    val hintText: String get() = hint
    val playerName: String get() = name
    val totalShown: String get() = totalText
    fun dartShown(i: Int): String? = dartLabel[i]

    fun setRound(round: Int, total: Int) {
        roundText = round.toString() + " / " + total + " 라운드"
        invalidate()
    }

    fun setMode(label: String) {
        modeText = label
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

    /** 이름 옆 점수 글자 ("123점" / "남은 187") */
    fun setScore(text: String) {
        if (scoreText == text) return
        scoreText = text
        fitName()
        invalidate()
    }

    /** i 번째 다트 칸 (label null = 아직 안 던짐) */
    fun setDart(i: Int, label: String?, points: String?, textColor: Int) {
        dartLabel[i] = label
        dartPoints[i] = points
        dartColor[i] = textColor
        invalidate()
    }

    fun clearDarts() {
        for (i in 0 until SLOTS) { dartLabel[i] = null; dartPoints[i] = null }
        invalidate()
    }

    fun setTotal(text: String, textColor: Int) {
        totalText = text
        totalColor = textColor
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
        modePaint.textSize = 12f * u
        initPaint.textSize = 14f * u
        numPaint.textSize = 11f * u
        labelPaint.textSize = 14f * u
        ptsPaint.textSize = 14f * u
        totalPaint.textSize = 14f * u
        fitName()
        fitHint()
    }

    /** 이름이 점수 글자 앞까지 들어가게 크기를 줄임 (setter·크기 바뀔 때만) */
    private fun fitName() {
        if (rect.width() <= 0f) return
        scorePaint.textSize = 19f * u
        var s = 21f * u
        namePaint.textSize = s
        val maxW = rect.width() - 24f * u - 26f * u - scorePaint.measureText(scoreText) - 10f * u
        while (namePaint.measureText(name) > maxW && s > 10f * u) {
            s *= 0.92f
            namePaint.textSize = s
        }
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
        // 내용 덩어리를 세로 가운데에
        val block = NEED_DP * u - 16f * u
        var y = rect.top + (rect.height() - block) / 2f
        val left = rect.left + 10f * u
        val right = rect.right - 10f * u

        // ① 라운드 알약 + 방식
        val pw = pillText.measureText(roundText) + 16f * u
        tmp.set(left, y, left + pw, y + 20f * u)
        fill.color = Theme.GOLD
        c.drawRoundRect(tmp, 10f * u, 10f * u, fill)
        c.drawText(roundText, tmp.centerX(), tmp.centerY() + pillText.textSize * 0.36f, pillText)
        c.drawText(modeText, right, tmp.centerY() + modePaint.textSize * 0.36f, modePaint)
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
        c.drawText(scoreText, right, ccy + scorePaint.textSize * 0.36f, scorePaint)
        y += 32f * u

        // ③ 다트 3칸
        for (i in 0 until SLOTS) {
            val rowY = y + i * ROW_DP * u
            tmp.set(left, rowY + 1f * u, right, rowY + (ROW_DP - 2f) * u)
            fill.color = if (dartLabel[i] != null) Theme.CARD_LIGHT else 0x33000000
            c.drawRoundRect(tmp, 6f * u, 6f * u, fill)
            val bx = left + 11f * u
            val by = tmp.centerY()
            if (dartLabel[i] != null) {
                fill.color = color
                c.drawCircle(bx, by, 7.5f * u, fill)
                numPaint.color = Theme.textOn(color)
            } else {
                stroke.color = 0x88FFFFFF.toInt()
                stroke.strokeWidth = 1.2f * d
                c.drawCircle(bx, by, 7f * u, stroke)
                numPaint.color = Theme.TEXT_SUB
            }
            c.drawText(NUMS[i], bx, by + numPaint.textSize * 0.36f, numPaint)
            val label = dartLabel[i]
            if (label != null) {
                labelPaint.color = dartColor[i]
                c.drawText(label, bx + 14f * u, by + labelPaint.textSize * 0.36f, labelPaint)
                val pts = dartPoints[i]
                if (pts != null) {
                    ptsPaint.color = dartColor[i]
                    c.drawText(pts, right - 6f * u, by + ptsPaint.textSize * 0.36f, ptsPaint)
                }
            }
        }
        y += SLOTS * ROW_DP * u + 4f * u

        // ④ 합계 줄
        if (totalText.isNotEmpty()) {
            totalPaint.color = totalColor
            c.drawText(totalText, left + 2f * u, y + 14f * u, totalPaint)
        }
        y += 22f * u
        // ⑤ 안내
        if (hint.isNotEmpty()) c.drawText(hint, left + 2f * u, y + 12f * u, hintPaint)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val SLOTS = GameConfig.DARTS_PER_TURN
        /** 다트 한 칸 높이 (dp) */
        private const val ROW_DP = 22f
        /** 내용 전체가 필요한 높이 (dp, 여백 포함): 알약 26 + 이름 32 + 다트 3×22 + 4 + 합계 22 + 안내 16 + 위아래 16 */
        private const val NEED_DP = 26f + 32f + SLOTS * ROW_DP + 4f + 22f + 16f + 16f
        private val NUMS = arrayOf("1", "2", "3")
    }
}
