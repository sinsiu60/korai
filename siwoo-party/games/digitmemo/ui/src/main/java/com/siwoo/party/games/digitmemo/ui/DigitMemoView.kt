package com.siwoo.party.games.digitmemo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.digitmemo.Box
import com.siwoo.party.games.digitmemo.DigitLayout
import com.siwoo.party.games.digitmemo.GameConfig
import com.siwoo.party.games.digitmemo.RoundResult

/**
 * 숫자 순간 기억 판 (MultiTouchZoneView 위에 겹치는 그림 층, 터치는 아래 구역으로 그대로 전달).
 * 구역마다 같은 배치(DigitLayout)를 구역 방향으로 돌려 그린다 (위쪽 구역 180°) — 숫자열도 각 구역에 그 사람 쪽으로 크게 보인다.
 * 위: 이름 · 라운드 · 점수, 시간 막대
 * 보이기: 숫자열을 크게 / 입력: 숫자 칸 줄(입력한 숫자) + 작은 숫자 패드 [1~5 ⌫][6~9 0 확인] / 제출 뒤: 패드에 "제출 완료" 덮개
 * 공개: 내 입력을 자리마다 초록(맞음)·빨강(틀림)으로, 패드 자리에 정답과 결과(+2 가장 빨리 / +1 정답 / 오답 / 시간 초과)
 * contentHidden = true 면 (일시정지) 숫자·패드를 그리지 않는다.
 * 움직임은 step(dtMs) 로 진행 (화면이 연출·입력 중에만 프레임을 돌린다). onDraw 는 할당 없음.
 */
class DigitMemoView(context: Context) : View(context) {

    enum class Mode { NONE, READY, SHOWING, INPUT, REVEAL }

    private val dp = Theme.dpf(context, 1f)
    val layout = DigitLayout()
    private var zones: List<Zone> = emptyList()
    private var n = 0

    var mode = Mode.NONE
        private set

    /** 일시정지 중: 숫자·패드를 가림 */
    var contentHidden = false
        set(v) { field = v; invalidate() }

    // ── 플레이어 ──
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private var scores: Array<String> = emptyArray()

    // ── 라운드 (setRound 때 글자를 만든다) ──
    private var roundLabel = ""
    private var readyLabel = ""
    private var answerText = ""
    private val answer = CharArray(MAX_DIGITS)
    private var len = 0

    // ── 구역별 입력 ──
    private var input = Array(0) { CharArray(MAX_DIGITS) }
    private var inputLen = IntArray(0)
    private var confirmable = BooleanArray(0)
    private var submitted = BooleanArray(0)
    private var submitTime: Array<String> = emptyArray()

    // ── 공개 ──
    private var resultText: Array<String> = emptyArray()
    private var resultColor = IntArray(0)
    private var fastest = -1

    // ── 연출 ──
    private var flashKey = IntArray(0)
    private var flashT = FloatArray(0)
    private var popT = 1f
    private var timeFrac = 1f
    private var hurry = false
    var popMs = GameConfig.POP_MS.toFloat()
    var flashMs = GameConfig.KEY_FLASH_MS.toFloat()

    // ── 그리기 도구 ──
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Theme.GOLD }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val bigOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF0A0F2E.toInt()
    }
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val okPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val answerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val rect = RectF()
    private val slot = Box()
    private val backPath = Path()
    private val checkPath = Path()
    private val keyBase = Theme.shade(Theme.BLUE, 0.45f)
    private val keyFace = Theme.shade(Theme.BLUE, 1.05f)
    private val backBase = Theme.shade(Theme.RED, 0.5f)
    private val backFace = Theme.shade(Theme.RED, 0.9f)
    private val okBase = Theme.GREEN_DARK
    private val grayBase = Theme.shade(Theme.GRAY, 0.5f)
    private var bigBaseSize = 0f
    private var answerSize = 0f
    private var answerLabelW = 0f
    private var answerW = 0f
    private var backS = 0f

    init {
        isClickable = false
        isFocusable = false
    }

    fun setup(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size
        names = playerNames.toTypedArray()
        colors = playerColors
        scores = Array(n) { "0점" }
        input = Array(n) { CharArray(MAX_DIGITS) }
        inputLen = IntArray(n)
        confirmable = BooleanArray(n)
        submitted = BooleanArray(n)
        submitTime = Array(n) { "" }
        resultText = Array(n) { "" }
        resultColor = IntArray(n)
        flashKey = IntArray(n) { -1 }
        flashT = FloatArray(n)
        relayout()
    }

    fun setScore(i: Int, score: Int) { scores[i] = "${score}점"; invalidate() }

    /** 새 라운드 준비 (숫자는 아직 안 보임) */
    fun setRound(round: Int, total: Int, digits: String) {
        roundLabel = "$round / $total"
        readyLabel = "${digits.length}자리 · 잘 보세요!"
        answerText = digits
        len = digits.length.coerceAtMost(MAX_DIGITS)
        for (k in 0 until len) answer[k] = digits[k]
        inputLen.fill(0)
        confirmable.fill(false)
        submitted.fill(false)
        fastest = -1
        flashKey.fill(-1)
        flashT.fill(0f)
        timeFrac = 1f
        hurry = false
        mode = Mode.READY
        fitText()
        invalidate()
    }

    /** 숫자열 보이기 (1초) */
    fun showDigits() { mode = Mode.SHOWING; popT = 0f; invalidate() }

    /** 숫자를 가리고 패드 보이기 */
    fun startInput() { mode = Mode.INPUT; invalidate() }

    /** 구역 i 의 입력 (값이 바뀔 때만) */
    fun setInput(i: Int, text: String, canConfirm: Boolean) {
        val l = text.length.coerceAtMost(MAX_DIGITS)
        for (k in 0 until l) input[i][k] = text[k]
        inputLen[i] = l
        confirmable[i] = canConfirm
        invalidate()
    }

    fun inputOf(i: Int): String = String(input[i], 0, inputLen[i])

    /** 제출 완료 표시 (걸린 시간 글자) */
    fun setSubmitted(i: Int, timeText: String) {
        submitted[i] = true
        submitTime[i] = timeText
        confirmable[i] = false
        invalidate()
    }

    fun isSubmittedShown(i: Int): Boolean = submitted[i]

    /** 정답 공개 */
    fun reveal(r: RoundResult) {
        fastest = r.fastest
        for (i in 0 until n) {
            val s = r.submissions[i]
            resultText[i] = when {
                i == r.fastest -> "+${r.points[i]} 가장 빨리!"
                r.correct[i] -> "+${r.points[i]} 정답!"
                s == null -> "시간 초과"
                else -> "틀렸어요"
            }
            resultColor[i] = when {
                i == r.fastest -> Theme.GOLD
                r.correct[i] -> Theme.GREEN
                s == null -> Theme.GRAY
                else -> Theme.RED
            }
        }
        mode = Mode.REVEAL
        invalidate()
    }

    fun flash(i: Int, key: Int) { flashKey[i] = key; flashT[i] = 1f; invalidate() }

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
        val l = layout
        namePaint.textSize = minOf(l.cellH * 0.1f, 17f * dp)
        scorePaint.textSize = namePaint.textSize
        subPaint.textSize = namePaint.textSize * 0.85f
        val k0 = l.keys[1]
        keyPaint.textSize = minOf(k0.height * 0.5f, k0.width * 0.6f)
        okPaint.textSize = minOf(k0.height * 0.4f, l.keys[DigitLayout.KEY_OK].width * 0.32f)
        resultPaint.textSize = minOf(k0.height * 0.5f, 22f * dp)
        bigBaseSize = l.flash.height * 0.55f
        // 지우기 아이콘 (가운데 0,0 기준, 키 높이 기준 크기)
        val s = k0.height * 0.22f
        backS = s
        backPath.reset()
        backPath.moveTo(-s * 1.5f, 0f)
        backPath.lineTo(-s * 0.7f, -s)
        backPath.lineTo(s * 1.4f, -s)
        backPath.lineTo(s * 1.4f, s)
        backPath.lineTo(-s * 0.7f, s)
        backPath.close()
        checkPath.reset()
        checkPath.moveTo(-s, 0f)
        checkPath.lineTo(-s * 0.25f, s * 0.75f)
        checkPath.lineTo(s * 1.1f, -s * 0.8f)
        fitText()
        invalidate()
    }

    /** 자릿수에 맞춰 큰 숫자·정답 글자 크기 (값이 바뀔 때만 잰다) */
    private fun fitText() {
        val l = layout
        if (l.cellW <= 0f || len == 0) return
        // 보이기: 자리마다 같은 폭 칸 (숫자 폭 ≈ 글자 크기 × 0.6)
        val cell = (l.flash.width - 2f * FLASH_INSET * dp) / len
        val big = minOf(bigBaseSize, cell / 0.66f)
        bigPaint.textSize = big
        bigOutline.textSize = big
        bigOutline.strokeWidth = big * 0.1f
        layout.slot(0, len, slot)
        slotPaint.textSize = minOf(slot.height * 0.62f, slot.width * 1.05f)
        // 공개: "정답" 글자 + 숫자열이 패드 첫 줄 폭에 들어가게
        val row = l.keys[1]
        answerPaint.textSize = row.height * 0.6f
        answerLabelW = subPaint.measureText(ANSWER_LABEL)
        val maxW = l.pad.width - answerLabelW - 12f * dp
        val w = answerPaint.measureText(answerText)
        answerSize = if (w > maxW) answerPaint.textSize * maxW / w else answerPaint.textSize
        answerPaint.textSize = answerSize
        answerW = answerPaint.measureText(answerText)
    }

    override fun onDraw(c: Canvas) {
        if (zones.isEmpty()) return
        for (z in zones) {
            val i = z.index
            c.save()
            c.translate(z.centerX, z.centerY)
            c.rotate(z.rotation)
            drawHeader(c, i)
            if (!contentHidden) {
                when (mode) {
                    Mode.READY -> drawReady(c)
                    Mode.SHOWING -> drawDigits(c)
                    Mode.INPUT -> {
                        drawSlots(c, i, false)
                        drawPad(c, i)
                        if (submitted[i]) drawSubmitted(c, i)
                    }
                    Mode.REVEAL -> {
                        drawSlots(c, i, true)
                        drawResult(c, i)
                    }
                    else -> Unit
                }
            }
            c.restore()
        }
    }

    private fun drawHeader(c: Canvas, i: Int) {
        val l = layout
        val x = l.cellW * 0.47f
        namePaint.color = colors[i]
        val ty = l.headerY + namePaint.textSize * 0.36f
        c.drawText(names[i], -x, ty, namePaint)
        c.drawText(scores[i], x, ty, scorePaint)
        subPaint.color = Theme.TEXT_SUB
        c.drawText(roundLabel, 0f, ty, subPaint)
        val b = l.timeBar
        val r = b.height / 2f
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        rect.set(b.left, b.top, b.right, b.bottom)
        c.drawRoundRect(rect, r, r, p)
        if (mode == Mode.INPUT && timeFrac > 0f && !contentHidden) {
            p.color = when {
                hurry -> Theme.RED
                timeFrac < 0.5f -> Theme.GOLD
                else -> Theme.GREEN
            }
            rect.set(b.left, b.top, b.left + b.width * timeFrac, b.bottom)
            c.drawRoundRect(rect, r, r, p)
        }
    }

    /** 어두운 판 (숫자 보이는 자리) */
    private fun panel(c: Canvas, b: Box, border: Int) {
        val rad = 12f * dp
        p.style = Paint.Style.FILL
        p.color = 0xA60A0F2E.toInt()
        rect.set(b.left, b.top, b.right, b.bottom)
        c.drawRoundRect(rect, rad, rad, p)
        line.color = border
        line.strokeWidth = 2.5f * dp
        c.drawRoundRect(rect, rad, rad, line)
    }

    private fun drawReady(c: Canvas) {
        val f = layout.flash
        panel(c, f, 0x55FFFFFF)
        resultPaint.color = Theme.TEXT_SUB
        c.drawText(readyLabel, 0f, f.centerY + resultPaint.textSize * 0.36f, resultPaint)
    }

    /** 숫자열을 크게 (자리마다 같은 폭 칸, 등장 때 살짝 커짐) */
    private fun drawDigits(c: Canvas) {
        val f = layout.flash
        panel(c, f, Theme.GOLD)
        val e = popT
        val sc = 0.7f + 0.3f * (1f - (1f - e) * (1f - e))
        c.save()
        c.scale(sc, sc, 0f, f.centerY)
        val inset = FLASH_INSET * dp
        val cell = (f.width - 2f * inset) / len
        val y = f.centerY + bigPaint.textSize * 0.36f
        for (k in 0 until len) {
            val x = f.left + inset + cell * (k + 0.5f)
            c.drawText(answer, k, 1, x, y, bigOutline)
            c.drawText(answer, k, 1, x, y, bigPaint)
        }
        c.restore()
    }

    /** 숫자 칸 줄: 입력 중엔 넣은 숫자 + 다음 칸 금색 테두리, 공개 땐 자리마다 맞음 초록·틀림 빨강 */
    private fun drawSlots(c: Canvas, i: Int, reveal: Boolean) {
        val inLen = inputLen[i]
        for (k in 0 until len) {
            layout.slot(k, len, slot)
            val rad = slot.width * 0.18f
            p.style = Paint.Style.FILL
            p.color = when {
                !reveal -> if (k < inLen) 0xFF2B3C8F.toInt() else 0x66000000
                k >= inLen -> 0x66000000
                !submitted[i] -> Theme.shade(Theme.GRAY, 0.7f)
                input[i][k] == answer[k] -> Theme.GREEN_DARK
                else -> Theme.RED_DARK
            }
            rect.set(slot.left, slot.top, slot.right, slot.bottom)
            c.drawRoundRect(rect, rad, rad, p)
            val cursor = !reveal && !submitted[i] && k == inLen
            line.color = if (cursor) Theme.GOLD else 0x40FFFFFF
            line.strokeWidth = (if (cursor) 2.5f else 1.2f) * dp
            c.drawRoundRect(rect, rad, rad, line)
            if (k < inLen) {
                c.drawText(input[i], k, 1, slot.centerX, slot.centerY + slotPaint.textSize * 0.36f, slotPaint)
            }
        }
    }

    private fun drawPad(c: Canvas, i: Int) {
        val depth = 3f * dp
        val dim = submitted[i]
        for (k in 0 until DigitLayout.KEY_COUNT) {
            val b = layout.keys[k]
            val rad = minOf(b.height * 0.25f, 10f * dp)
            val isOk = k == DigitLayout.KEY_OK
            val isBack = k == DigitLayout.KEY_BACK
            val off = isOk && !confirmable[i]
            p.style = Paint.Style.FILL
            p.color = when {
                off -> grayBase
                isOk -> okBase
                isBack -> backBase
                else -> keyBase
            }
            if (dim) p.alpha = 90
            rect.set(b.left, b.top + depth, b.right, b.bottom)
            c.drawRoundRect(rect, rad, rad, p)
            p.color = when {
                off -> Theme.GRAY
                isOk -> Theme.GREEN
                isBack -> backFace
                else -> keyFace
            }
            if (dim) p.alpha = 90
            rect.set(b.left, b.top, b.right, b.bottom - depth)
            c.drawRoundRect(rect, rad, rad, p)
            // 광택
            p.color = 0x2EFFFFFF
            rect.set(b.left + rad * 0.4f, b.top + depth * 0.5f, b.right - rad * 0.4f, b.top + (b.height - depth) * 0.42f)
            c.drawRoundRect(rect, rad * 0.6f, rad * 0.6f, p)
            // 번쩍
            if (flashKey[i] == k && flashT[i] > 0f) {
                p.color = 0xFFFFFFFF.toInt()
                p.alpha = (170 * flashT[i]).toInt()
                rect.set(b.left, b.top, b.right, b.bottom - depth)
                c.drawRoundRect(rect, rad, rad, p)
                p.alpha = 255
            }
            val cy = b.top + (b.height - depth) / 2f
            when {
                isOk -> {
                    okPaint.alpha = if (off || dim) 130 else 255
                    c.drawText(OK_LABEL, b.centerX, cy + okPaint.textSize * 0.36f, okPaint)
                }
                isBack -> {
                    c.save()
                    c.translate(b.centerX, cy)
                    line.color = if (dim) 0x80FFFFFF.toInt() else Theme.TEXT
                    line.strokeWidth = 2f * dp
                    c.drawPath(backPath, line)
                    val s = backS * 0.45f
                    val xc = backS * 0.35f
                    c.drawLine(xc - s, -s, xc + s, s, line)
                    c.drawLine(xc + s, -s, xc - s, s, line)
                    c.restore()
                }
                else -> {
                    keyPaint.alpha = if (dim) 110 else 255
                    c.drawText(DIGITS, k, 1, b.centerX, cy + keyPaint.textSize * 0.36f, keyPaint)
                }
            }
        }
        keyPaint.alpha = 255
        okPaint.alpha = 255
    }

    /** 제출 뒤: 패드 위 덮개 + 체크 + "제출 완료 · 1.8초" */
    private fun drawSubmitted(c: Canvas, i: Int) {
        val pad = layout.pad
        val rad = 12f * dp
        p.style = Paint.Style.FILL
        p.color = 0xD00A0F2E.toInt()
        rect.set(pad.left - 2f * dp, pad.top - 2f * dp, pad.right + 2f * dp, pad.bottom + 2f * dp)
        c.drawRoundRect(rect, rad, rad, p)
        line.color = Theme.GREEN
        line.strokeWidth = 2f * dp
        c.drawRoundRect(rect, rad, rad, line)
        val y1 = pad.top + pad.height * 0.36f
        c.save()
        c.translate(-pad.width * 0.3f, y1)
        line.strokeWidth = 4f * dp
        c.drawPath(checkPath, line)
        c.restore()
        resultPaint.color = Theme.GREEN
        c.drawText(SUBMITTED_LABEL, 0f, y1 + resultPaint.textSize * 0.36f, resultPaint)
        subPaint.color = Theme.TEXT_SUB
        c.drawText(submitTime[i], 0f, pad.top + pad.height * 0.76f + subPaint.textSize * 0.36f, subPaint)
    }

    /** 공개: 패드 첫 줄 자리에 "정답 0428…", 둘째 줄 자리에 결과 띠 */
    private fun drawResult(c: Canvas, i: Int) {
        val l = layout
        val row1 = l.keys[1]
        val row2 = l.keys[6]
        val pad = l.pad
        // 정답 줄
        val rad = 10f * dp
        p.style = Paint.Style.FILL
        p.color = 0xA60A0F2E.toInt()
        rect.set(pad.left, row1.top, pad.right, row1.bottom)
        c.drawRoundRect(rect, rad, rad, p)
        line.color = Theme.GOLD
        line.strokeWidth = 2f * dp
        c.drawRoundRect(rect, rad, rad, line)
        val total = answerLabelW + 10f * dp + answerW
        val x0 = -total / 2f
        subPaint.color = Theme.TEXT_SUB
        val cy1 = row1.centerY
        subPaint.textAlign = Paint.Align.LEFT
        c.drawText(ANSWER_LABEL, x0, cy1 + subPaint.textSize * 0.36f, subPaint)
        subPaint.textAlign = Paint.Align.CENTER
        answerPaint.textAlign = Paint.Align.LEFT
        c.drawText(answerText, x0 + answerLabelW + 10f * dp, cy1 + answerPaint.textSize * 0.36f, answerPaint)
        answerPaint.textAlign = Paint.Align.CENTER
        // 결과 띠
        val color = resultColor[i]
        p.color = Theme.shade(color, 0.45f)
        rect.set(pad.left + pad.width * 0.08f, row2.top, pad.right - pad.width * 0.08f, row2.bottom)
        c.drawRoundRect(rect, rad, rad, p)
        line.color = color
        line.strokeWidth = (if (i == fastest) 3f else 2f) * dp
        c.drawRoundRect(rect, rad, rad, line)
        resultPaint.color = if (color == Theme.GRAY) Theme.TEXT_SUB else Theme.TEXT
        c.drawText(resultText[i], 0f, row2.centerY + resultPaint.textSize * 0.36f, resultPaint)
    }

    companion object {
        const val MAX_DIGITS = 16
        /** 큰 숫자 판 안쪽 여백 (dp) */
        private const val FLASH_INSET = 10f
        private val DIGITS = "0123456789".toCharArray()
        private const val OK_LABEL = "확인"
        private const val ANSWER_LABEL = "정답"
        private const val SUBMITTED_LABEL = "제출 완료"
    }
}
