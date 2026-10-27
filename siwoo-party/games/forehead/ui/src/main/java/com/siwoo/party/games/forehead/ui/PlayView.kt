package com.siwoo.party.games.forehead.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 이마에 댄 폰 화면 (바깥 사람들이 보는 면): 화면 전체를 채우는 아주 큰 제시어.
 * 상태: 준비(이마에 대세요) → 카운트(3·2·1) → 단어 → 정답(초록)/패스(주황) 번쩍 → … → 시간 끝(빨강)
 * 글자 크기는 값이 바뀔 때만 계산 → onDraw 할당 없음. 튀어나오는 연출은 짧은 ValueAnimator (진행 중에만 다시 그림).
 * 터치 조작이면 왼쪽 절반 = 정답, 오른쪽 절반 = 패스 (onTap).
 */
class PlayView(context: Context) : View(context) {

    enum class Mode { READY, COUNT, WORD, FLASH, TIME_UP }

    fun interface TapListener { fun onTap(left: Boolean) }

    var mode = Mode.READY
        private set
    /** 지금 크게 보이는 단어 (WORD 일 때) */
    var word = ""
        private set
    /** 정답 화면인지 (FLASH 일 때, false = 패스) */
    var flashCorrect = false
        private set
    /** 터치 조작 안내 (왼쪽 정답 / 오른쪽 패스) */
    var touchHints = false
        set(v) { field = v; invalidate() }
    var onTap: TapListener? = null

    private var count = 3
    private var category = ""
    private var categoryColor = Theme.Party.NEON_PURPLE
    private var counter = ""
    private var readyTitle = ""
    private var readySub = ""
    private var flashWord = ""
    private var readyProgress = 0f
    private var pop = 1f

    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.Party.TEXT_SUB }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val frame = RectF()
    private val pill = RectF()
    private val counterPill = RectF()
    private val ring = RectF()

    /** 튀어나오기 연출: 진행 중에만 프레임마다 (postOnAnimation), 일시정지 땐 멈춤 */
    private var popMs = 220L
    private var popStart = 0L
    private var popping = false
    private var popPaused = false
    private var popPausedAt = 0L
    private val popFrame = object : Runnable {
        override fun run() {
            if (!popping || popPaused) return
            val f = ((SystemClock.uptimeMillis() - popStart).toFloat() / popMs).coerceIn(0f, 1f)
            // 살짝 넘쳤다 돌아오는 곡선 (overshoot 2)
            val t = f - 1f
            pop = 0.55f + 0.45f * (t * t * (3f * t + 2f) + 1f)
            invalidate()
            if (f >= 1f) popping = false else postOnAnimation(this)
        }
    }

    /** 튀어나오는 연출 시간 (속도 배율 적용한 값) */
    fun setPopMs(ms: Long) { popMs = ms.coerceAtLeast(1) }

    fun setCategory(name: String, color: Int) {
        category = name
        categoryColor = color
        relayoutPills()
        invalidate()
    }

    fun setCounts(correct: Int, pass: Int) {
        counter = "정답 $correct · 패스 $pass"
        relayoutPills()
        invalidate()
    }

    /** 준비 화면 (title 큰 글자, sub 안내) */
    fun showReady(title: String, sub: String) {
        mode = Mode.READY
        readyTitle = title
        readySub = sub
        readyProgress = 0f
        fitReady()
        stopPop()
        pop = 1f
        invalidate()
    }

    /** 똑바로 들고 있는 정도 0..1 (센서 조작 준비 화면의 고리) */
    fun setReadyProgress(f: Float) {
        val v = f.coerceIn(0f, 1f)
        if (v == readyProgress) return
        readyProgress = v
        invalidate()
    }

    fun showCount(n: Int) {
        mode = Mode.COUNT
        count = n.coerceIn(0, COUNT_TEXTS.size - 1)
        popIn()
    }

    fun showWord(w: String) {
        mode = Mode.WORD
        word = w
        fitWord()
        popIn()
    }

    /** 정답(초록)/패스(주황) 화면 (answered = 방금 단어) */
    fun flash(correct: Boolean, answered: String) {
        mode = Mode.FLASH
        flashCorrect = correct
        flashWord = answered
        fitFlash()
        popIn()
    }

    fun showTimeUp() {
        mode = Mode.TIME_UP
        word = ""
        popIn()
    }

    /** 단어 지우기 (차례가 끝나면) */
    fun clearWord() {
        word = ""
        flashWord = ""
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
        val wf = w.toFloat()
        val hf = h.toFloat()
        val inset = hf * 0.03f
        frame.set(inset, inset, wf - inset, hf - inset)
        bigPaint.textSize = hf * 0.62f
        subPaint.textSize = hf * 0.065f
        smallPaint.textSize = hf * 0.055f
        wordPaint.setShadowLayer(hf * 0.03f, 0f, hf * 0.008f, Theme.Party.NEON_SKY)
        stroke.strokeWidth = hf * 0.012f
        fitWord()
        fitFlash()
        fitReady()
        relayoutPills()
    }

    private val readyTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val flashWordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }

    /** 단어가 화면을 가득 채우게 (폭 90%, 높이 52% 이내) */
    private fun fitWord() {
        if (width <= 0 || word.isEmpty()) return
        wordPaint.textSize = height * 0.52f
        val tw = wordPaint.measureText(word)
        val maxW = width * 0.9f
        if (tw > maxW) wordPaint.textSize *= maxW / tw
    }

    private fun fitFlash() {
        if (width <= 0) return
        flashWordPaint.textSize = height * 0.13f
        val tw = flashWordPaint.measureText(flashWord)
        val maxW = width * 0.8f
        if (tw > maxW) flashWordPaint.textSize *= maxW / tw
    }

    private fun fitReady() {
        if (width <= 0) return
        readyTitlePaint.textSize = height * 0.14f
        val tw = readyTitlePaint.measureText(readyTitle)
        if (tw > width * 0.9f) readyTitlePaint.textSize *= width * 0.9f / tw
    }

    private fun relayoutPills() {
        if (width <= 0) return
        val h = height.toFloat()
        val ph = h * 0.085f
        val top = frame.top + h * 0.035f
        val cw = if (category.isEmpty()) 0f else smallPaint.measureText(category) + ph * 1.2f
        pill.set(frame.left + h * 0.04f, top, frame.left + h * 0.04f + cw, top + ph)
        val kw = if (counter.isEmpty()) 0f else smallPaint.measureText(counter) + ph * 1.2f
        counterPill.set(frame.right - h * 0.04f - kw, top, frame.right - h * 0.04f, top + ph)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            onTap?.onTap(e.x < width / 2f)
            return true
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        when (mode) {
            Mode.FLASH -> drawFlash(c, w, h, cx, cy)
            Mode.TIME_UP -> {
                c.drawColor(Theme.RED_DARK)
                drawFrame(c, Theme.RED)
                drawPop(c, cx, cy) {
                    bigPaint.textSize = h * 0.3f
                    bigPaint.color = Theme.TEXT
                    c.drawText(TIME_UP, cx, cy + h * 0.1f, bigPaint)
                }
            }
            else -> {
                c.drawColor(Theme.Party.BG_DEEP)
                drawFrame(c, Theme.Party.NEON_PINK)
                when (mode) {
                    Mode.READY -> drawReady(c, w, h, cx, cy)
                    Mode.COUNT -> {
                        drawPop(c, cx, cy) {
                            bigPaint.textSize = h * 0.62f
                            bigPaint.color = Theme.GOLD
                            c.drawText(COUNT_TEXTS[count], cx, cy + h * 0.22f, bigPaint)
                        }
                        c.drawText(COUNT_SUB, cx, h * 0.9f, subPaint)
                    }
                    else -> drawWord(c, w, h, cx, cy)
                }
            }
        }
    }

    /** pop 배율로 확대해 그리기 (inline → 람다 할당 없음) */
    private inline fun drawPop(c: Canvas, cx: Float, cy: Float, body: () -> Unit) {
        c.save()
        c.scale(pop, pop, cx, cy)
        body()
        c.restore()
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

    private fun drawWord(c: Canvas, w: Float, h: Float, cx: Float, cy: Float) {
        // 터치 조작: 왼쪽 초록 / 오른쪽 주황 살짝 + 아래 안내
        if (touchHints) {
            fill.color = ForeheadArt.CORRECT
            fill.alpha = 26
            c.drawRect(frame.left, frame.top, cx, frame.bottom, fill)
            fill.color = ForeheadArt.PASS
            fill.alpha = 26
            c.drawRect(cx, frame.top, frame.right, frame.bottom, fill)
            fill.alpha = 255
            smallPaint.color = ForeheadArt.CORRECT
            c.drawText(HINT_LEFT, w * 0.17f, h * 0.9f, smallPaint)
            smallPaint.color = ForeheadArt.PASS
            c.drawText(HINT_RIGHT, w * 0.83f, h * 0.9f, smallPaint)
            smallPaint.color = Theme.TEXT
        }
        // 카테고리 / 점수 알약
        if (category.isNotEmpty()) {
            fill.color = categoryColor
            c.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, fill)
            c.drawText(category, pill.centerX(), pill.centerY() + smallPaint.textSize * 0.36f, smallPaint)
        }
        if (counter.isNotEmpty()) {
            fill.color = Theme.Party.CARD_LIGHT
            c.drawRoundRect(counterPill, counterPill.height() / 2f, counterPill.height() / 2f, fill)
            c.drawText(counter, counterPill.centerX(), counterPill.centerY() + smallPaint.textSize * 0.36f, smallPaint)
        }
        if (word.isEmpty()) return
        drawPop(c, cx, cy) {
            c.drawText(word, cx, cy + wordPaint.textSize * 0.36f, wordPaint)
        }
    }

    private fun drawFlash(c: Canvas, w: Float, h: Float, cx: Float, cy: Float) {
        val color = if (flashCorrect) ForeheadArt.CORRECT else ForeheadArt.PASS
        val dark = if (flashCorrect) ForeheadArt.CORRECT_DARK else ForeheadArt.PASS_DARK
        c.drawColor(color)
        drawFrame(c, Theme.TEXT)
        drawPop(c, cx, cy) {
            bigPaint.textSize = h * 0.3f
            bigPaint.color = Theme.TEXT
            val ty = cy + h * 0.02f
            // 아이콘 + 큰 글자
            val s = h * 0.26f
            if (flashCorrect) ForeheadArt.drawCheck(c, cx - w * 0.28f, ty - h * 0.1f, s, Theme.TEXT, art)
            else ForeheadArt.drawPassArrow(c, cx - w * 0.28f, ty - h * 0.12f, s, Theme.TEXT, art, path)
            c.drawText(if (flashCorrect) CORRECT_TEXT else PASS_TEXT, cx + w * 0.06f, ty, bigPaint)
            flashWordPaint.color = dark
            if (flashWord.isNotEmpty()) c.drawText(flashWord, cx, cy + h * 0.3f, flashWordPaint)
        }
    }

    private fun drawReady(c: Canvas, w: Float, h: Float, cx: Float, cy: Float) {
        // 이마에 댄 폰 그림 + 진행 고리
        val r = h * 0.13f
        val ay = h * 0.32f
        if (readyProgress > 0f) {
            ring.set(cx - r * 1.95f, ay - r * 1.95f, cx + r * 1.95f, ay + r * 1.95f)
            stroke.color = Theme.Party.CARD_LIGHT
            stroke.strokeWidth = h * 0.02f
            c.drawOval(ring, stroke)
            stroke.color = ForeheadArt.CORRECT
            c.drawArc(ring, -90f, 360f * readyProgress, false, stroke)
        }
        ForeheadArt.drawHeadWithPhone(c, cx, ay, r, Theme.Party.NEON_PURPLE, Theme.Party.NEON_PINK, EMPTY, art, path)
        c.drawText(readyTitle, cx, h * 0.73f, readyTitlePaint)
        c.drawText(readySub, cx, h * 0.86f, subPaint)
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
            Mode.FLASH -> if (flashCorrect) CORRECT_TEXT else PASS_TEXT
            Mode.COUNT -> COUNT_TEXTS[count]
            Mode.TIME_UP -> TIME_UP
            Mode.READY -> readyTitle
        }

    companion object {
        val COUNT_TEXTS = arrayOf("0", "1", "2", "3")
        const val COUNT_SUB = "이마에 대고 기다리세요!"
        const val CORRECT_TEXT = "정답!"
        const val PASS_TEXT = "패스"
        const val TIME_UP = "시간 끝!"
        const val HINT_LEFT = "◀ 여기 터치 = 정답"
        const val HINT_RIGHT = "패스 = 여기 터치 ▶"
        private const val EMPTY = ""
    }
}
