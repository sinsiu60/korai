package com.siwoo.party.games.math.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.math.GameConfig
import com.siwoo.party.games.math.MathLayout

/**
 * 암산 판 (MultiTouchZoneView 위에 겹치는 그림 층, 터치는 아래 구역으로 그대로 전달).
 * 구역마다 같은 문제·같은 보기(같은 순서)를 같은 배치(MathLayout)로, 구역 방향에 맞춰 돌려 그린다 (위쪽 구역 180°):
 * 위: 이름 · 문제 번호 · 점수, 시간 막대 / 가운데: 문제 / 아래: 보기 3개 입체 버튼.
 * 틀리면 그 구역 보기 줄에 잠금 덮개(줄어드는 빨간 막대). 정답 공개 때 모든 구역의 정답 보기가 초록.
 * 움직임은 step(dtMs) 로 진행 (화면이 문제 중·연출 중에만 프레임을 돌린다). onDraw 는 할당 없음.
 */
class MathView(context: Context) : View(context) {

    private val dp = Theme.dpf(context, 1f)
    val layout = MathLayout()
    private var zones: List<Zone> = emptyList()
    private var n = 0

    // ── 플레이어 ──
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private var scores: Array<String> = emptyArray()

    // ── 문제 (setQuestion 때 글자를 만든다) ──
    private var numberLabel = ""
    private var questionText = ""
    private var solvedText = ""
    private val choiceText = Array(GameConfig.CHOICES) { "" }
    private var correct = -1
    /** 정답 공개 중 (winner = 맞힌 구역, -1 = 시간 끝) */
    private var revealed = false
    private var winner = -1
    private var hasQuestion = false

    // ── 구역별 상태 ──
    private var wrong = IntArray(0)
    /** 잠금 남은 비율 1..0 (0 = 잠금 없음) */
    private var lock = FloatArray(0)
    /** 보기 번쩍임 */
    private var flashChoice = IntArray(0)
    private var flashT = FloatArray(0)
    private var timeFrac = 1f
    private var hurry = false
    /** 문제 등장 연출 0..1 */
    private var popT = 1f

    var popMs = GameConfig.QUESTION_POP_MS.toFloat()
    var flashMs = GameConfig.TAP_FLASH_MS.toFloat()

    // ── 그리기 도구 ──
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Theme.GOLD }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val qPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val qOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF0A0F2E.toInt()
    }
    private val choicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.RED }
    private val rect = RectF()
    private var blueFace: LinearGradient? = null
    private var greenFace: LinearGradient? = null
    private val blueBase = Theme.shade(Theme.BLUE, 0.45f)
    private val greenBase = Theme.GREEN_DARK
    private val grayBase = Theme.shade(Theme.GRAY, 0.5f)
    private var lockTextW = 0f
    private var qSize = 0f
    private var qMaxW = 0f

    init {
        isClickable = false
        isFocusable = false
    }

    fun setup(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size
        names = playerNames.toTypedArray()
        colors = playerColors
        scores = Array(n) { "0점" }
        wrong = IntArray(n)
        lock = FloatArray(n)
        flashChoice = IntArray(n) { -1 }
        flashT = FloatArray(n)
        relayout()
    }

    fun setScore(i: Int, score: Int) { scores[i] = "${score}점"; invalidate() }

    /** 새 문제 (모든 구역 같은 문제·같은 보기 순서) */
    fun setQuestion(number: Int, total: Int, text: String, solved: String, choices: IntArray, correctIndex: Int) {
        numberLabel = "$number / $total"
        questionText = text
        solvedText = solved
        for (k in 0 until GameConfig.CHOICES) choiceText[k] = choices[k].toString()
        correct = correctIndex
        revealed = false
        winner = -1
        hasQuestion = true
        wrong.fill(0)
        lock.fill(0f)
        flashChoice.fill(-1)
        flashT.fill(0f)
        timeFrac = 1f
        hurry = false
        popT = 0f
        fitQuestion()
        invalidate()
    }

    /** 문제 글자가 칸 폭에 들어가게 (값이 바뀔 때만 잰다) */
    private fun fitQuestion() {
        if (qMaxW <= 0f) return
        qPaint.textSize = qSize
        val wq = maxOf(qPaint.measureText(questionText), qPaint.measureText(solvedText))
        val s = if (wq > qMaxW) qSize * qMaxW / wq else qSize
        qPaint.textSize = s
        qOutline.textSize = s
        qOutline.strokeWidth = s * 0.12f
    }

    fun markWrong(i: Int, choice: Int) { wrong[i] = wrong[i] or (1 shl choice); invalidate() }

    fun flash(i: Int, choice: Int) { flashChoice[i] = choice; flashT[i] = 1f; invalidate() }

    /** 잠금 남은 비율 (0 = 풀림) */
    fun setLock(i: Int, frac: Float) {
        if (lock[i] == frac) return
        lock[i] = frac
        invalidate()
    }

    fun isLockShown(i: Int): Boolean = lock[i] > 0f

    /** 정답 공개 (winnerZone = 맞힌 구역, -1 = 아무도 못 맞힘) */
    fun reveal(winnerZone: Int) {
        revealed = true
        winner = winnerZone
        lock.fill(0f)
        invalidate()
    }

    fun setTime(frac: Float, hurrying: Boolean) {
        if (frac == timeFrac && hurrying == hurry) return
        timeFrac = frac
        hurry = hurrying
        invalidate()
    }

    /** 연출 진행. 아직 움직이는 게 있으면 true */
    fun step(dtMs: Float): Boolean {
        var busy = false
        if (popT < 1f) { popT = (popT + dtMs / popMs.coerceAtLeast(1f)).coerceAtMost(1f); busy = true }
        for (i in 0 until n) {
            if (flashT[i] > 0f) { flashT[i] = (flashT[i] - dtMs / flashMs.coerceAtLeast(1f)).coerceAtLeast(0f); busy = true }
        }
        invalidate()
        return busy
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || n == 0) return
        zones = TouchZones.layout(n, width.toFloat(), height.toFloat(), Theme.dpf(context, 10f))
        var minW = Float.MAX_VALUE
        var minH = Float.MAX_VALUE
        for (z in zones) { minW = minOf(minW, z.width); minH = minOf(minH, z.height) }
        layout.layout(minW - 16f * dp, minH - 16f * dp)
        val ch = layout.cellH
        namePaint.textSize = minOf(ch * 0.095f, 17f * dp)
        scorePaint.textSize = namePaint.textSize
        numPaint.textSize = namePaint.textSize * 0.85f
        val c0 = layout.choices[0]
        choicePaint.textSize = minOf(c0.height * 0.48f, c0.width * 0.3f)
        lockPaint.textSize = minOf(c0.height * 0.32f, 18f * dp)
        lockTextW = lockPaint.measureText(LOCK_TEXT)
        qSize = minOf((c0.top - layout.timeBar.bottom) * 0.62f, layout.cellW * 0.14f)
        qMaxW = layout.cellW * 0.92f
        fitQuestion()
        blueFace = LinearGradient(0f, c0.top, 0f, c0.bottom, Theme.shade(Theme.BLUE, 1.35f), Theme.shade(Theme.BLUE, 0.85f), Shader.TileMode.CLAMP)
        greenFace = LinearGradient(0f, c0.top, 0f, c0.bottom, Theme.shade(Theme.GREEN, 1.3f), Theme.GREEN_DARK, Shader.TileMode.CLAMP)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (zones.isEmpty()) return
        for (z in zones) {
            val i = z.index
            c.save()
            c.translate(z.centerX, z.centerY)
            c.rotate(z.rotation)
            drawHeader(c, i)
            if (hasQuestion) {
                drawQuestion(c, i)
                drawChoices(c, i)
                if (lock[i] > 0f && !revealed) drawLock(c, i)
            }
            c.restore()
        }
    }

    private fun drawHeader(c: Canvas, i: Int) {
        val l = layout
        val x = l.cellW * 0.46f
        namePaint.color = colors[i]
        c.drawText(names[i], -x, l.headerY, namePaint)
        c.drawText(scores[i], x, l.headerY, scorePaint)
        c.drawText(numberLabel, 0f, l.headerY, numPaint)
        val b = l.timeBar
        val r = b.height / 2f
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = 0x55000000
        rect.set(b.left, b.top, b.right, b.bottom)
        c.drawRoundRect(rect, r, r, p)
        if (timeFrac > 0f && hasQuestion) {
            p.color = when {
                hurry -> Theme.RED
                timeFrac < 0.5f -> Theme.GOLD
                else -> Theme.GREEN
            }
            rect.set(b.left, b.top, b.left + b.width * timeFrac, b.bottom)
            c.drawRoundRect(rect, r, r, p)
        }
    }

    private fun drawQuestion(c: Canvas, i: Int) {
        val y = layout.questionY + qPaint.textSize * 0.36f
        val e = popT
        val sc = 0.6f + 0.4f * (1f - (1f - e) * (1f - e))
        c.save()
        c.scale(sc, sc, 0f, layout.questionY)
        val text = if (revealed) solvedText else questionText
        qPaint.color = if (revealed) Theme.GOLD else Theme.TEXT
        c.drawText(text, 0f, y, qOutline)
        c.drawText(text, 0f, y, qPaint)
        c.restore()
    }

    private fun drawChoices(c: Canvas, i: Int) {
        val depth = 4f * dp
        for (k in 0 until GameConfig.CHOICES) {
            val b = layout.choices[k]
            val rad = minOf(b.height * 0.28f, 14f * dp)
            val isWrong = (wrong[i] shr k) and 1 == 1
            val isRight = revealed && k == correct
            val dimmed = revealed && !isRight
            p.shader = null
            p.alpha = 255
            // 두께
            p.color = when {
                isRight -> greenBase
                isWrong -> grayBase
                else -> blueBase
            }
            if (dimmed) p.alpha = 110
            rect.set(b.left, b.top + depth, b.right, b.bottom)
            c.drawRoundRect(rect, rad, rad, p)
            // 윗면
            if (isRight) {
                p.color = 0xFFFFFFFF.toInt()
                p.shader = greenFace
            } else if (isWrong) {
                p.color = Theme.GRAY
            } else {
                p.color = 0xFFFFFFFF.toInt()
                p.shader = blueFace
            }
            if (dimmed) p.alpha = 110
            rect.set(b.left, b.top, b.right, b.bottom - depth)
            c.drawRoundRect(rect, rad, rad, p)
            p.shader = null
            p.alpha = 255
            // 광택
            p.color = 0x30FFFFFF
            rect.set(b.left + rad * 0.5f, b.top + depth * 0.6f, b.right - rad * 0.5f, b.top + (b.height - depth) * 0.42f)
            c.drawRoundRect(rect, rad * 0.7f, rad * 0.7f, p)
            // 번쩍
            if (flashChoice[i] == k && flashT[i] > 0f) {
                p.color = 0xFFFFFFFF.toInt()
                p.alpha = (150 * flashT[i]).toInt()
                rect.set(b.left, b.top, b.right, b.bottom - depth)
                c.drawRoundRect(rect, rad, rad, p)
                p.alpha = 255
            }
            // 숫자
            choicePaint.alpha = if (dimmed || isWrong) 140 else 255
            val cy = b.top + (b.height - depth) / 2f
            c.drawText(choiceText[k], b.centerX, cy + choicePaint.textSize * 0.36f, choicePaint)
            choicePaint.alpha = 255
            // 틀린 보기: 빨간 X
            if (isWrong) {
                line.color = Theme.RED
                line.strokeWidth = 4f * dp
                val d = minOf(b.width, b.height) * 0.28f
                c.drawLine(b.centerX - d, cy - d, b.centerX + d, cy + d, line)
                c.drawLine(b.centerX + d, cy - d, b.centerX - d, cy + d, line)
            }
            // 맞힌 사람: 정답 보기에 금색 테두리
            if (isRight && winner == i) {
                line.color = Theme.GOLD
                line.strokeWidth = 3f * dp
                rect.set(b.left - 2f * dp, b.top - 2f * dp, b.right + 2f * dp, b.bottom + 2f * dp)
                c.drawRoundRect(rect, rad, rad, line)
            }
        }
    }

    /** 잠금 덮개: 보기 줄 위 어두운 판 + 자물쇠 + "잠금" + 줄어드는 빨간 막대 */
    private fun drawLock(c: Canvas, i: Int) {
        val l = layout
        val first = l.choices[0]
        val last = l.choices[GameConfig.CHOICES - 1]
        val rad = minOf(first.height * 0.28f, 14f * dp)
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = 0xC0101530.toInt()
        rect.set(first.left - 3f * dp, first.top - 3f * dp, last.right + 3f * dp, last.bottom + 3f * dp)
        c.drawRoundRect(rect, rad, rad, p)
        line.color = Theme.RED
        line.strokeWidth = 2f * dp
        c.drawRoundRect(rect, rad, rad, line)
        // 자물쇠 + 글자 (가운데)
        val cy = first.centerY - first.height * 0.08f
        val s = lockPaint.textSize
        val lx = -lockTextW / 2f - s * 0.9f
        p.color = Theme.RED
        rect.set(lx - s * 0.45f, cy - s * 0.25f, lx + s * 0.45f, cy + s * 0.45f)
        c.drawRoundRect(rect, s * 0.12f, s * 0.12f, p)
        line.strokeWidth = s * 0.14f
        rect.set(lx - s * 0.3f, cy - s * 0.65f, lx + s * 0.3f, cy + s * 0.05f)
        c.drawArc(rect, 180f, 180f, false, line)
        c.drawText(LOCK_TEXT, 0f, cy + s * 0.36f, lockPaint)
        // 남은 시간 막대
        val bw = (last.right - first.left) * 0.6f
        val by = first.bottom - first.height * 0.2f
        p.color = 0x55FFFFFF
        rect.set(-bw / 2f, by, bw / 2f, by + 5f * dp)
        c.drawRoundRect(rect, 2.5f * dp, 2.5f * dp, p)
        p.color = Theme.RED
        rect.set(-bw / 2f, by, -bw / 2f + bw * lock[i], by + 5f * dp)
        c.drawRoundRect(rect, 2.5f * dp, 2.5f * dp, p)
    }

    companion object {
        private const val LOCK_TEXT = "틀렸어요! 잠금"
    }
}
