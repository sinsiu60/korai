package com.siwoo.party.games.charades.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 설명자 화면 (설명자만 보는 면): 화면 가운데 아주 큰 제시어 + 위 카테고리·정답/패스 알약 + 아래 "말 금지" 안내.
 * 상태: 준비(단어 없음, 몸으로만 안내) → 단어 → 정답(화면 전체 초록 번쩍)/패스(주황) → 다음 단어 … → 시간 끝(빨강)
 * 글자 크기·문자열은 값이 바뀔 때만 → onDraw 할당 없음. 튀어나오는 연출은 진행 중에만 postOnAnimation.
 */
class ActView(context: Context) : View(context) {

    enum class Mode { READY, WORD, CORRECT, PASS, TIME_UP }

    var mode = Mode.READY
        private set
    /** 지금 크게 보이는 단어 (WORD 일 때만, 아니면 "") */
    var word = ""
        private set

    private var category = ""
    private var categoryColor = CharadesArt.MIXED_COLOR
    private var counter = ""
    private var flashWord = ""
    private var flashSub = ""
    private var pop = 1f

    private val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val flashWordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT; color = Theme.Party.NEON_PINK }
    private val readyTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val readySub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.Party.TEXT_SUB }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val frame = RectF()
    private val catPill = RectF()
    private val countPill = RectF()
    private val warnPill = RectF()
    private var warnIconR = 0f
    private var warnTextX = 0f

    // ── 튀어나오기 연출 (진행 중에만 프레임마다, 일시정지 땐 멈춤) ──
    private var popMs = 220L
    private var popStart = 0L
    private var popping = false
    private var popPaused = false
    private var popPausedAt = 0L
    private val popFrame = object : Runnable {
        override fun run() {
            if (!popping || popPaused) return
            val f = ((SystemClock.uptimeMillis() - popStart).toFloat() / popMs).coerceIn(0f, 1f)
            val t = f - 1f
            pop = 0.55f + 0.45f * (t * t * (3f * t + 2f) + 1f)
            invalidate()
            if (f >= 1f) popping = false else postOnAnimation(this)
        }
    }

    /** 튀어나오는 연출 시간 (속도 배율 적용한 값) */
    fun setPopMs(ms: Long) { popMs = ms.coerceAtLeast(1) }

    fun setCategory(name: String, color: Int) {
        if (category == name && categoryColor == color) return
        category = name
        categoryColor = color
        relayout()
        invalidate()
    }

    /** 정답·패스 알약 (값이 바뀔 때만 글자를 만든다) */
    fun setCounts(correct: Int, passUsed: Int, passMax: Int) {
        counter = "정답 $correct · 패스 $passUsed/$passMax"
        relayout()
        invalidate()
    }

    /** 준비 화면 (단어 없음) */
    fun showReady() {
        mode = Mode.READY
        word = ""
        stopPop()
        pop = 1f
        invalidate()
    }

    fun showWord(w: String) {
        mode = Mode.WORD
        word = w
        fitWord()
        popIn()
    }

    /** 정답: 화면 전체 초록 (answered = 방금 맞힌 단어, sub = 점수 안내) */
    fun flashCorrect(answered: String, sub: String) {
        mode = Mode.CORRECT
        word = ""
        flashWord = answered
        flashSub = sub
        fitFlash()
        popIn()
    }

    /** 패스: 화면 전체 주황 */
    fun flashPass(passed: String, sub: String) {
        mode = Mode.PASS
        word = ""
        flashWord = passed
        flashSub = sub
        fitFlash()
        popIn()
    }

    fun showTimeUp() {
        mode = Mode.TIME_UP
        word = ""
        popIn()
    }

    /** 단어 지우기 (차례가 끝나면 — 다음 사람에게 남지 않게) */
    fun clearWord() {
        mode = Mode.READY
        word = ""
        flashWord = ""
        flashSub = ""
        stopPop()
        pop = 1f
        invalidate()
    }

    fun pauseAnim() {
        if (!popping || popPaused) return
        popPaused = true
        popPausedAt = SystemClock.uptimeMillis()
        removeCallbacks(popFrame)
    }

    fun resumeAnim() {
        if (!popPaused) return
        popPaused = false
        popStart += SystemClock.uptimeMillis() - popPausedAt
        if (popping) postOnAnimation(popFrame)
    }

    private fun stopPop() {
        popping = false
        popPaused = false
        removeCallbacks(popFrame)
    }

    private fun popIn() {
        stopPop()
        pop = 0.55f
        popStart = SystemClock.uptimeMillis()
        popping = true
        postOnAnimation(popFrame)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val hf = h.toFloat()
        val inset = hf * 0.02f
        frame.set(inset, inset, w - inset, hf - inset)
        smallPaint.textSize = hf * 0.065f
        subPaint.textSize = hf * 0.075f
        warnPaint.textSize = hf * 0.07f
        readyTitle.textSize = hf * 0.13f
        readySub.textSize = hf * 0.065f
        wordPaint.setShadowLayer(hf * 0.03f, 0f, hf * 0.008f, Theme.Party.NEON_PINK)
        fitWord()
        fitFlash()
        relayout()
    }

    /** 단어가 화면을 가득 채우게 (폭 92%, 높이 46% 이내) */
    private fun fitWord() {
        if (width <= 0 || word.isEmpty()) return
        wordPaint.textSize = height * 0.46f
        val tw = wordPaint.measureText(word)
        val maxW = width * 0.92f
        if (tw > maxW) wordPaint.textSize *= maxW / tw
    }

    private fun fitFlash() {
        if (width <= 0) return
        flashWordPaint.textSize = height * 0.16f
        val tw = flashWordPaint.measureText(flashWord)
        val maxW = width * 0.8f
        if (tw > maxW) flashWordPaint.textSize *= maxW / tw
    }

    private fun relayout() {
        if (width <= 0) return
        val h = height.toFloat()
        val ph = h * 0.11f
        val top = frame.top + h * 0.04f
        val cw = if (category.isEmpty()) 0f else smallPaint.measureText(category) + ph * 1.2f
        catPill.set(frame.left + h * 0.04f, top, frame.left + h * 0.04f + cw, top + ph)
        val kw = if (counter.isEmpty()) 0f else smallPaint.measureText(counter) + ph * 1.2f
        countPill.set(frame.right - h * 0.04f - kw, top, frame.right - h * 0.04f, top + ph)
        // 아래 "말 금지" 안내: 아이콘 + 글자, 가운데 정렬
        val wh = h * 0.13f
        warnIconR = wh * 0.42f
        val tw = warnPaint.measureText(NO_TALK_TEXT)
        val total = warnIconR * 2.4f + tw + wh * 0.9f
        val left = (width - total) / 2f
        warnPill.set(left, frame.bottom - h * 0.04f - wh, left + total, frame.bottom - h * 0.04f)
        warnTextX = warnPill.left + wh * 0.45f + warnIconR * 2.4f
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        when (mode) {
            Mode.CORRECT, Mode.PASS -> drawFlash(c, w, h, cx, cy)
            Mode.TIME_UP -> {
                c.drawColor(Theme.RED_DARK)
                drawFrame(c, Theme.RED)
                c.save()
                c.scale(pop, pop, cx, cy)
                bigPaint.textSize = h * 0.3f
                c.drawText(TIME_UP, cx, cy + h * 0.1f, bigPaint)
                c.restore()
            }
            Mode.READY -> {
                c.drawColor(Theme.Party.BG_DEEP)
                drawFrame(c, Theme.Party.NEON_PINK)
                CharadesArt.drawActor(c, cx - h * 0.3f, h * 0.33f, h * 0.4f, Theme.Party.NEON_SKY, 0, art)
                CharadesArt.drawNoTalk(c, cx + h * 0.28f, h * 0.27f, h * 0.15f, art, path)
                c.drawText(READY_TITLE, cx, h * 0.72f, readyTitle)
                c.drawText(READY_SUB, cx, h * 0.86f, readySub)
            }
            else -> drawWord(c, cx, cy)
        }
    }

    private fun drawFrame(c: Canvas, color: Int) {
        val r = height * 0.06f
        stroke.color = color
        stroke.alpha = 70
        stroke.strokeWidth = height * 0.03f
        c.drawRoundRect(frame, r, r, stroke)
        stroke.alpha = 255
        stroke.strokeWidth = height * 0.01f
        c.drawRoundRect(frame, r, r, stroke)
    }

    private fun drawWord(c: Canvas, cx: Float, cy: Float) {
        c.drawColor(Theme.Party.BG_DEEP)
        drawFrame(c, Theme.Party.NEON_PINK)
        val st = smallPaint.textSize * 0.36f
        if (category.isNotEmpty()) {
            fill.color = categoryColor
            c.drawRoundRect(catPill, catPill.height() / 2f, catPill.height() / 2f, fill)
            c.drawText(category, catPill.centerX(), catPill.centerY() + st, smallPaint)
        }
        if (counter.isNotEmpty()) {
            fill.color = Theme.Party.CARD_LIGHT
            c.drawRoundRect(countPill, countPill.height() / 2f, countPill.height() / 2f, fill)
            c.drawText(counter, countPill.centerX(), countPill.centerY() + st, smallPaint)
        }
        // 말 금지 안내
        fill.color = 0x33FF3FB4
        val wr = warnPill.height() / 2f
        c.drawRoundRect(warnPill, wr, wr, fill)
        stroke.color = Theme.Party.NEON_PINK
        stroke.strokeWidth = warnPill.height() * 0.05f
        c.drawRoundRect(warnPill, wr, wr, stroke)
        CharadesArt.drawNoTalk(c, warnPill.left + warnPill.height() * 0.45f + warnIconR * 1.2f, warnPill.centerY(), warnIconR, art, path)
        c.drawText(NO_TALK_TEXT, warnTextX, warnPill.centerY() + warnPaint.textSize * 0.36f, warnPaint)
        if (word.isEmpty()) return
        // 단어 (위 알약과 아래 안내 사이 가운데)
        val wy = (catPill.bottom + warnPill.top) / 2f
        c.save()
        c.scale(pop, pop, cx, wy)
        c.drawText(word, cx, wy + wordPaint.textSize * 0.36f, wordPaint)
        c.restore()
    }

    private fun drawFlash(c: Canvas, w: Float, h: Float, cx: Float, cy: Float) {
        val correct = mode == Mode.CORRECT
        c.drawColor(if (correct) CharadesArt.CORRECT else CharadesArt.PASS)
        drawFrame(c, Theme.TEXT)
        c.save()
        c.scale(pop, pop, cx, cy)
        bigPaint.textSize = h * 0.28f
        val ty = cy - h * 0.04f
        val s = h * 0.26f
        if (correct) CharadesArt.drawCheck(c, cx - w * 0.26f, ty - h * 0.1f, s, Theme.TEXT, art)
        else CharadesArt.drawPassArrow(c, cx - w * 0.26f, ty - h * 0.14f, s, Theme.TEXT, art, path)
        c.drawText(if (correct) CORRECT_TEXT else PASS_TEXT, cx + w * 0.05f, ty, bigPaint)
        flashWordPaint.color = if (correct) FLASH_WORD_CORRECT else FLASH_WORD_PASS
        if (flashWord.isNotEmpty()) c.drawText(flashWord, cx, cy + h * 0.2f, flashWordPaint)
        if (flashSub.isNotEmpty()) c.drawText(flashSub, cx, cy + h * 0.36f, subPaint)
        c.restore()
    }

    override fun onDetachedFromWindow() {
        stopPop()
        pop = 1f
        super.onDetachedFromWindow()
    }

    /** 테스트·확인용: 지금 화면에 크게 보이는 글자 */
    val shownText: String
        get() = when (mode) {
            Mode.WORD -> word
            Mode.CORRECT -> CORRECT_TEXT
            Mode.PASS -> PASS_TEXT
            Mode.TIME_UP -> TIME_UP
            Mode.READY -> READY_TITLE
        }

    companion object {
        private const val FLASH_WORD_CORRECT = 0xFF0B5A2C.toInt()
        private const val FLASH_WORD_PASS = 0xFF7A3A00.toInt()
        const val CORRECT_TEXT = "정답!"
        const val PASS_TEXT = "패스"
        const val TIME_UP = "시간 끝!"
        const val READY_TITLE = "몸으로만 설명하세요!"
        const val READY_SUB = "말·소리·입 모양·글자 쓰기는 금지 · 곧 제시어가 나와요"
        const val NO_TALK_TEXT = "말하면 안 돼요! 몸짓으로만"
    }
}
