package com.siwoo.party.games.chosung.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.chosung.GameConfig

/**
 * 초성 퀴즈 가운데 판: 카테고리 · 초성 글자 타일 · 시간 막대 · 상태 + [O 정답] [X 오답] 판정 버튼.
 * dual(3·4인)이면 문제 칸을 위(180° 돌림)·아래 두 번 그려 맞은편 사람도 읽을 수 있고, 판정 버튼은 가운데 줄.
 * 판 위의 터치는 모두 여기서 받는다 (버저 구역으로 새지 않음). 판정 버튼은 누군가 답하는 중에만 켜진다.
 * 시간 막대·남은 초는 문제가 열려 있거나 답하는 중에만 프레임마다 다시 그린다 (일시정지 = frozen 이면 멈춤).
 */
class ChosungPanelView(context: Context, val dual: Boolean) : View(context) {

    fun interface Clock { fun now(): Long }
    fun interface Listener { fun onJudge(correct: Boolean) }

    enum class Mode { IDLE, OPEN, ANSWERING, RESULT }

    var clock: Clock = Clock { SystemClock.uptimeMillis() }
    var listener: Listener? = null
    var frozen = false
        set(v) { field = v; if (!v) invalidate() }
    /** 반짝 길이 (속도 배율 적용된 값) */
    var flashMs = GameConfig.FLASH_MS

    var mode = Mode.IDLE
        private set
    private var category = ""
    private var categoryColor = Theme.Party.NEON_PURPLE
    private var counter = ""
    /** 타일에 보일 글자 (초성/힌트/정답) */
    private var chars = CharArray(0)
    private var hintIndex = -1
    private var sub = ""
    private var subColor = Theme.GOLD
    private var resultColor = ChosungArt.OK_GREEN
    // 시간 막대: 문제가 열려 있던 시간 = openBase + (열려 있으면 now - openSince)
    private var openBase = 0L
    private var openSince = 0L
    // 답하는 중
    private var deadline = 0L
    private var answerTexts: Array<String> = emptyArray()
    private var answerColor = Theme.GOLD
    // 반짝
    private var hintFlashAt = Long.MIN_VALUE / 2
    private var wrongFlashAt = Long.MIN_VALUE / 2
    private var pressed = -1

    // ── 배치 ──
    private val d = Theme.dpf(context, 1f)
    private val body = RectF()
    /** 문제 칸 (single: 하나, dual: 아래 칸 — 위 칸은 이걸 판 가운데 기준 180° 돌린 자리) */
    private val info = RectF()
    private val row = RectF()
    private val tilesBox = RectF()
    private val okRect = RectF()
    private val noRect = RectF()
    private var tileW = 0f
    private var tileStart = FloatArray(0)
    private var tilesTotal = 0f
    private var subY = 0f
    private var barTop = 0f
    private var barBottom = 0f
    private var statusY = 0f

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tileText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private var bodyShader: Shader? = null

    init {
        isClickable = true
        contentDescription = "초성 퀴즈 문제"
    }

    // ── 상태 바꾸기 (문자열은 여기서만 만든다) ──

    /** 새 문제: 카테고리·초성·번호 (시간 막대는 0부터) */
    fun showQuestion(categoryName: String, categoryIndex: Int, shown: String, counterText: String) {
        category = categoryName
        categoryColor = ChosungArt.CATEGORY_COLORS[categoryIndex % ChosungArt.CATEGORY_COLORS.size]
        counter = counterText
        hintIndex = -1
        sub = ""
        pressed = -1
        setChars(shown)
        setOpen(0L)
        contentDescription = "$categoryName $shown"
    }

    /** 힌트: 글자 하나 공개 */
    fun showHint(shown: String, index: Int) {
        hintIndex = index
        setChars(shown)
        sub = HINT_TEXT
        subColor = Theme.GOLD
        hintFlashAt = clock.now()
        contentDescription = "$category $shown"
        invalidate()
    }

    /** 문제가 열려 있음 (버저 받는 중): 지금까지 열려 있던 시간 openElapsed 부터 흐름 */
    fun setOpen(openElapsed: Long) {
        mode = Mode.OPEN
        openBase = openElapsed
        openSince = clock.now()
        pressed = -1
        invalidate()
    }

    /** name 이 답하는 중 (deadlineMs 까지). 시간 막대는 openElapsed 에서 멈춤 */
    fun setAnswering(name: String, color: Int, deadlineMs: Long, openElapsed: Long) {
        mode = Mode.ANSWERING
        openBase = openElapsed
        deadline = deadlineMs
        answerColor = color
        val secs = ((GameConfig.ANSWER_MS + 999) / 1000).toInt()
        answerTexts = Array(secs + 1) { if (dual) "$name 답하세요! ${it}초" else "$name 답하세요! · ${it}초" }
        pressed = -1
        invalidate()
    }

    /** 오답 반짝 (X 버튼) — 이어서 setOpen 또는 showResult 를 부른다 */
    fun flashWrong() {
        wrongFlashAt = clock.now()
        invalidate()
    }

    /** 문제 끝: 정답 단어를 타일로 + 한 줄 (예: "가람 정답!" / "정답 공개") */
    fun showResult(word: String, line: String, correct: Boolean) {
        mode = Mode.RESULT
        hintIndex = -1
        resultColor = if (correct) ChosungArt.OK_GREEN else Theme.GOLD_DARK
        sub = line
        subColor = if (correct) ChosungArt.OK_GREEN else Theme.GOLD
        pressed = -1
        setChars(word)
        contentDescription = "정답 $word"
        invalidate()
    }

    /** 문제 사이 (빈 판) */
    fun clear() {
        mode = Mode.IDLE
        sub = ""
        hintIndex = -1
        setChars("")
        invalidate()
    }

    /** 판정 버튼 가운데 (이 View 기준 좌표) */
    fun buttonCenter(correct: Boolean, out: PointF) {
        val r = if (correct) okRect else noRect
        out.set(r.centerX(), r.centerY())
    }

    val judgeEnabled: Boolean get() = mode == Mode.ANSWERING

    private fun setChars(s: String) {
        chars = s.toCharArray()
        fitTiles()
    }

    // ── 배치 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val W = w.toFloat()
        val H = h.toFloat()
        body.set(2f * d, 2f * d, W - 2f * d, H - 2f * d)
        bodyShader = LinearGradient(0f, 0f, 0f, H, Theme.Party.CARD_LIGHT, Theme.Party.BG_DEEP, Shader.TileMode.CLAMP)
        val pad = 10f * d
        if (!dual) {
            val btnH = (H * 0.3f).coerceIn(56f * d, 104f * d)
            val gap = 10f * d
            val bw = (W - 2f * pad - gap) / 2f
            okRect.set(pad, H - pad - btnH, pad + bw, H - pad)
            noRect.set(okRect.right + gap, okRect.top, W - pad, okRect.bottom)
            info.set(pad, pad, W - pad, okRect.top - 8f * d)
            row.set(info.left, info.top, info.right, info.top + 26f * d)
            statusY = info.bottom - 4f * d
            barBottom = statusY - 22f * d
            barTop = barBottom - 9f * d
            subY = barTop - 10f * d
            tilesBox.set(info.left, row.bottom + 8f * d, info.right, subY - 22f * d)
        } else {
            val btnH = (H * 0.22f).coerceIn(46f * d, 58f * d)
            val gap = 10f * d
            val bw = (W - 2f * pad - gap) / 2f
            val top = (H - btnH) / 2f
            okRect.set(pad, top, pad + bw, top + btnH)
            noRect.set(okRect.right + gap, top, W - pad, top + btnH)
            // 아래 칸 (위 칸은 판 가운데 기준 180°)
            info.set(pad, okRect.bottom + 6f * d, W - pad, H - 6f * d)
            row.set(info.left, info.top, info.right, info.top + 20f * d)
            statusY = info.bottom - 3f * d
            barTop = row.centerY() - 3f * d
            barBottom = row.centerY() + 3f * d
            subY = statusY
            tilesBox.set(info.left, row.bottom + 4f * d, info.right, statusY - 17f * d)
        }
        fitTiles()
    }

    /** 글자 타일 크기·위치 (글자가 바뀔 때·크기가 바뀔 때만) */
    private fun fitTiles() {
        val n = chars.size
        if (tileStart.size < n) tileStart = FloatArray(n)
        if (n == 0 || tilesBox.width() <= 0f) { tilesTotal = 0f; return }
        var letters = 0
        var spaces = 0
        for (ch in chars) if (ch == ' ') spaces++ else letters++
        val units = letters + (letters - 1).coerceAtLeast(0) * GAP_K + spaces * SPACE_K
        tileW = minOf(tilesBox.height(), tilesBox.width() / units.coerceAtLeast(1f), (if (dual) 46f else 64f) * d)
        var x = 0f
        var prevLetter = false
        for ((i, ch) in chars.withIndex()) {
            if (ch == ' ') {
                tileStart[i] = x
                x += tileW * SPACE_K
                prevLetter = false
            } else {
                if (prevLetter) x += tileW * GAP_K
                tileStart[i] = x
                x += tileW
                prevLetter = true
            }
        }
        tilesTotal = x
        tileText.textSize = tileW * 0.64f
    }

    // ── 터치 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                if (judgeEnabled) {
                    val slack = 4f * d
                    val k = when {
                        x >= okRect.left - slack && x <= okRect.right + slack && y >= okRect.top - slack && y <= okRect.bottom + slack -> 0
                        x >= noRect.left - slack && x <= noRect.right + slack && y >= noRect.top - slack && y <= noRect.bottom + slack -> 1
                        else -> -1
                    }
                    if (k >= 0) {
                        pressed = k
                        invalidate()
                        listener?.onJudge(k == 0)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (pressed >= 0) { pressed = -1; invalidate() }
        }
        return true // 판 위 터치는 버저 구역으로 넘기지 않는다
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val now = clock.now()
        var anim = mode == Mode.OPEN || mode == Mode.ANSWERING
        drawBody(c)
        if (dual) {
            c.save()
            c.rotate(180f, width / 2f, height / 2f)
            if (drawInfo(c, now)) anim = true
            c.restore()
        }
        if (drawInfo(c, now)) anim = true
        drawButtons(c, now)
        if (now - wrongFlashAt < flashMs) anim = true
        if (anim && !frozen) postInvalidateOnAnimation()
    }

    private fun drawBody(c: Canvas) {
        val r = 18f * d
        fill.color = 0x66000000
        c.drawRoundRect(body.left, body.top + 3f * d, body.right, body.bottom + 3f * d, r, r, fill)
        fill.color = 0xFF000000.toInt() // 셰이더에도 알파가 곱해지므로 불투명으로
        fill.shader = bodyShader
        c.drawRoundRect(body, r, r, fill)
        fill.shader = null
        // 네온 테두리 (바깥 빛 + 선)
        stroke.color = Theme.Party.NEON_PINK
        stroke.alpha = 70
        stroke.strokeWidth = 6f * d
        c.drawRoundRect(body, r, r, stroke)
        stroke.alpha = 255
        stroke.strokeWidth = 2f * d
        c.drawRoundRect(body, r, r, stroke)
    }

    /** 문제 칸 하나. 애니메이션 중이면 true */
    private fun drawInfo(c: Canvas, now: Long): Boolean {
        var anim = false
        // 카테고리 알약
        if (category.isNotEmpty() && mode != Mode.IDLE) {
            small.textSize = row.height() * 0.6f
            small.textAlign = Paint.Align.CENTER
            val pw = small.measureText(category) + row.height() * 1.1f
            rect.set(row.left, row.top, row.left + pw, row.bottom)
            fill.color = categoryColor
            c.drawRoundRect(rect, row.height() / 2f, row.height() / 2f, fill)
            small.color = Theme.INK
            c.drawText(category, rect.centerX(), row.centerY() + small.textSize * 0.36f, small)
        }
        // 문제 번호
        if (counter.isNotEmpty()) {
            small.textSize = row.height() * 0.6f
            small.textAlign = Paint.Align.RIGHT
            small.color = Theme.GOLD
            c.drawText(counter, row.right - 2f * d, row.centerY() + small.textSize * 0.36f, small)
        }
        // 글자 타일
        if (chars.isEmpty()) {
            text.textSize = tilesBox.height() * 0.42f
            text.color = Theme.Party.TEXT_SUB
            c.drawText(READY, tilesBox.centerX(), tilesBox.centerY() + text.textSize * 0.36f, text)
        } else {
            if (drawTiles(c, now)) anim = true
        }
        // 시간 막대
        if (mode == Mode.OPEN || mode == Mode.ANSWERING) drawBar(c, now)
        // 아래 글자: single = 힌트/결과 줄 + 상태 줄, dual = 한 줄
        val status = statusText(now)
        if (!dual) {
            if (sub.isNotEmpty()) {
                text.textSize = 15f * d
                text.color = subColor
                c.drawText(sub, info.centerX(), subY, text)
            }
            if (status.isNotEmpty()) {
                text.textSize = 15f * d
                text.color = if (mode == Mode.ANSWERING) answerColor else Theme.Party.TEXT_SUB
                c.drawText(status, info.centerX(), statusY, text)
            }
        } else {
            val line = if (mode == Mode.ANSWERING || sub.isEmpty()) status else sub
            if (line.isNotEmpty()) {
                text.textSize = 13f * d
                text.color = when {
                    mode == Mode.ANSWERING -> answerColor
                    line === sub -> subColor
                    else -> Theme.Party.TEXT_SUB
                }
                c.drawText(line, info.centerX(), statusY, text)
            }
        }
        return anim
    }

    private fun statusText(now: Long): String = when (mode) {
        Mode.OPEN -> BUZZ_TEXT
        Mode.ANSWERING -> {
            val left = ((deadline - now).coerceAtLeast(0) + 999) / 1000
            answerTexts.getOrElse(left.toInt()) { "" }
        }
        else -> ""
    }

    private fun drawTiles(c: Canvas, now: Long): Boolean {
        val x0 = tilesBox.centerX() - tilesTotal / 2f
        val top = tilesBox.centerY() - tileW / 2f
        val hf = ((now - hintFlashAt).toFloat() / flashMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        val rad = tileW * 0.2f
        for ((i, ch) in chars.withIndex()) {
            if (ch == ' ') continue
            val l = x0 + tileStart[i]
            val isHint = i == hintIndex
            val pop = if (isHint && hf < 1f) (1f - hf) * tileW * 0.12f else 0f
            rect.set(l - pop, top - pop, l + tileW + pop, top + tileW + pop)
            // 그림자 + 면
            fill.color = 0x55000000
            c.drawRoundRect(rect.left, rect.top + 2.5f * d, rect.right, rect.bottom + 2.5f * d, rad, rad, fill)
            fill.color = when {
                mode == Mode.RESULT -> resultColor
                isHint -> Theme.GOLD
                else -> 0xFF1C0B3A.toInt()
            }
            c.drawRoundRect(rect, rad, rad, fill)
            stroke.strokeWidth = 1.5f * d
            stroke.color = if (mode == Mode.RESULT || isHint) 0x88FFFFFF.toInt() else Theme.Party.NEON_SKY
            c.drawRoundRect(rect, rad, rad, stroke)
            tileText.color = if (mode == Mode.RESULT || isHint) Theme.INK else 0xFFFFFFFF.toInt()
            c.drawText(chars, i, 1, rect.centerX(), rect.centerY() + tileText.textSize * 0.36f, tileText)
        }
        return hintIndex >= 0 && hf < 1f
    }

    private fun drawBar(c: Canvas, now: Long) {
        val elapsed = openBase + if (mode == Mode.OPEN) (now - openSince).coerceAtLeast(0) else 0L
        val left = 1f - (elapsed.toFloat() / GameConfig.REVEAL_AT_MS).coerceIn(0f, 1f)
        val l: Float
        val r: Float
        if (dual) {
            // 카테고리 알약과 번호 사이
            l = row.left + row.width() * 0.36f
            r = row.right - row.height() * 2.6f
        } else {
            l = info.left + 6f * d
            r = info.right - 6f * d
        }
        val h = barBottom - barTop
        stroke.strokeWidth = h
        stroke.color = 0x55000000
        c.drawLine(l + h / 2f, (barTop + barBottom) / 2f, r - h / 2f, (barTop + barBottom) / 2f, stroke)
        stroke.color = when {
            mode == Mode.ANSWERING -> Theme.GRAY
            left < 0.25f -> Theme.RED
            left < 0.5f -> Theme.GOLD
            else -> Theme.Party.NEON_SKY
        }
        val xe = l + h / 2f + (r - l - h) * left
        if (left > 0.005f) c.drawLine(l + h / 2f, (barTop + barBottom) / 2f, xe, (barTop + barBottom) / 2f, stroke)
        // 힌트 표시 (가운데 = 10초)
        val hx = l + h / 2f + (r - l - h) * (1f - GameConfig.HINT_AT_MS.toFloat() / GameConfig.REVEAL_AT_MS)
        fill.color = if (hintIndex >= 0) Theme.GRAY else Theme.GOLD
        c.drawCircle(hx, (barTop + barBottom) / 2f, h * 0.75f, fill)
    }

    private fun drawButtons(c: Canvas, now: Long) {
        val on = judgeEnabled
        val wf = ((now - wrongFlashAt).toFloat() / flashMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        val depth = 4f * d
        for (k in 0 until 2) {
            val r = if (k == 0) okRect else noRect
            var dx = 0f
            if (k == 1 && wf < 1f) dx = (Math.sin(wf * Math.PI * 6.0) * (1f - wf) * 5f * d).toFloat()
            rect.set(r.left + dx, r.top, r.right + dx, r.bottom)
            ChosungArt.slab(c, rect, if (k == 0) ChosungArt.OK_GREEN else ChosungArt.NO_RED, pressed == k, !on, depth, art)
            val off = if (pressed == k) depth else 0f
            val faceH = r.height() - depth
            val sym = if (on) 0xFFFFFFFF.toInt() else 0x88FFFFFF.toInt()
            val label = if (k == 0) OK_LABEL else NO_LABEL
            text.color = sym
            if (!dual) {
                val s = faceH * 0.3f
                val cy = r.top + off + faceH * 0.4f
                if (k == 0) ChosungArt.ok(c, rect.centerX(), cy, s, sym, art) else ChosungArt.no(c, rect.centerX(), cy, s, sym, art)
                text.textSize = (faceH * 0.2f).coerceAtMost(18f * d)
                c.drawText(label, rect.centerX(), r.top + off + faceH * 0.86f, text)
            } else {
                // 기호는 가운데, 글자는 아래(바로)·위(거꾸로)
                val s = faceH * 0.28f
                val cy = r.top + off + faceH / 2f
                if (k == 0) ChosungArt.ok(c, rect.centerX(), cy, s, sym, art) else ChosungArt.no(c, rect.centerX(), cy, s, sym, art)
                text.textSize = (faceH * 0.19f).coerceAtMost(12f * d)
                val lx = rect.left + rect.width() * 0.2f
                c.drawText(label, rect.right - rect.width() * 0.2f, cy + text.textSize * 0.36f, text)
                c.save()
                c.rotate(180f, lx, cy)
                c.drawText(label, lx, cy + text.textSize * 0.36f, text)
                c.restore()
            }
        }
    }

    companion object {
        private const val GAP_K = 0.12f
        private const val SPACE_K = 0.45f
        private const val READY = "준비"
        private const val BUZZ_TEXT = "아는 사람은 버저를 누르세요!"
        private const val HINT_TEXT = "힌트! 글자 하나 공개"
        private const val OK_LABEL = "정답"
        private const val NO_LABEL = "오답"
    }
}
