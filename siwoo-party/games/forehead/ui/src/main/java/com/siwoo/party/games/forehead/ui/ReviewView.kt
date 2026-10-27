package com.siwoo.party.games.forehead.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.os.SystemClock
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.forehead.Answer
import com.siwoo.party.games.forehead.WordResult
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * 차례가 끝난 뒤 단어 목록 공개: 칩이 순서대로 하나씩 나타난다.
 * 초록 칩(✓) = 정답, 주황 칩(↷) = 패스, 회색 칩 = 시간 끝에 보고 있던 단어.
 * 칩 배치·글자 크기는 setResults/크기 바뀔 때만 계산 → onDraw 할당 없음. 칩은 최대 MAX_CHIPS 개 (넘으면 마지막 칸에 "+N").
 */
class ReviewView(context: Context) : View(context) {

    private val words = arrayOfNulls<String>(MAX_CHIPS)
    private val kinds = IntArray(MAX_CHIPS)
    private val rects = Array(MAX_CHIPS) { RectF() }
    private var n = 0
    private var more = ""
    /** 지금 보이는 칩 수 (연출) */
    private var shown = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.Party.TEXT_SUB }
    private val path = Path()
    private var iconS = 0f

    /** 칩이 하나씩 나타나는 연출: 진행 중에만 프레임마다 (postOnAnimation) */
    private var revealStart = 0L
    private var revealMs = 0L
    private var revealing = false
    private val frame = object : Runnable {
        override fun run() {
            if (!revealing) return
            val f = ((SystemClock.uptimeMillis() - revealStart).toFloat() / revealMs).coerceIn(0f, 1f)
            shown = f * n
            invalidate()
            if (f >= 1f) revealing = false else postOnAnimation(this)
        }
    }

    private fun stopReveal() {
        revealing = false
        removeCallbacks(frame)
    }

    /** 칩 개수 (테스트 확인용) */
    val chipCount: Int get() = n

    /** 결과 넣기 + 칩이 하나씩 나타나는 연출 (chipMs = 칩 하나 간격, 0 이면 바로) */
    fun setResults(list: List<WordResult>, chipMs: Long) {
        val total = list.size
        n = minOf(total, MAX_CHIPS)
        val cut = total > MAX_CHIPS
        if (cut) n = MAX_CHIPS - 1
        for (k in 0 until MAX_CHIPS) words[k] = null
        for (k in 0 until n) {
            words[k] = list[k].word
            kinds[k] = list[k].answer.ordinal
        }
        more = if (cut) "+${total - n}" else ""
        layoutChips()
        stopReveal()
        if (chipMs <= 0 || n == 0) {
            shown = n.toFloat()
            invalidate()
        } else {
            shown = 0f
            revealMs = chipMs * n
            revealStart = SystemClock.uptimeMillis()
            revealing = true
            postOnAnimation(frame)
        }
    }

    /** 연출 건너뛰기 (모두 보이기) */
    fun showAll() {
        stopReveal()
        shown = n.toFloat()
        invalidate()
    }

    fun clear() {
        stopReveal()
        n = 0
        more = ""
        for (k in 0 until MAX_CHIPS) words[k] = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        emptyPaint.textSize = h * 0.08f
        layoutChips()
    }

    /** 칩 수에 맞춰 열 수·칩 크기를 정한다 (가로로 길쭉한 칩, 모두 화면 안에) */
    private fun layoutChips() {
        val w = width.toFloat()
        val h = height.toFloat()
        val slots = n + if (more.isNotEmpty()) 1 else 0
        if (w <= 0f || slots == 0) return
        val gap = h * 0.025f
        // 칩 가로:세로 ≈ 3.6:1 이 되게 열 수를 고른다
        var cols = ceil(sqrt(slots * w / (h * 3.6f))).toInt().coerceIn(1, 6)
        var rows = ceil(slots / cols.toFloat()).toInt()
        while (cols < 6 && (h - gap * (rows - 1)) / rows < h * 0.1f) { cols++; rows = ceil(slots / cols.toFloat()).toInt() }
        val cw = (w - gap * (cols - 1)) / cols
        val ch = minOf((h - gap * (rows - 1)) / rows, cw / 2.6f, h * 0.2f)
        for (k in 0 until slots) {
            val r = k / cols
            val c = k % cols
            val x = c * (cw + gap)
            val y = r * (ch + gap)
            rects[k].set(x, y, x + cw, y + ch)
        }
        iconS = ch * 0.5f
        // 가장 긴 단어가 칩 안에 들어가는 글자 크기 (모두 같은 크기)
        text.textSize = ch * 0.42f
        val maxW = cw - ch * 1.15f
        for (k in 0 until n) {
            val s = words[k] ?: continue
            val tw = text.measureText(s)
            if (tw > maxW) text.textSize *= maxW / tw
        }
    }

    override fun onDraw(c: Canvas) {
        if (n == 0 && more.isEmpty()) {
            c.drawText(EMPTY, width / 2f, height * 0.45f, emptyPaint)
            return
        }
        val visible = shown.toInt()
        for (k in 0 until n) {
            if (k >= visible + 1) break
            val s = words[k] ?: continue
            val r = rects[k]
            val a = if (k < visible) 1f else (shown - visible)
            if (a <= 0f) continue
            val kind = kinds[k]
            fill.color = when (kind) {
                CORRECT -> ForeheadArt.CORRECT_DARK
                PASS -> ForeheadArt.PASS_DARK
                else -> ForeheadArt.TIMEOUT
            }
            fill.alpha = (a * 255).toInt()
            val rr = r.height() / 2f
            c.drawRoundRect(r, rr, rr, fill)
            fill.color = when (kind) {
                CORRECT -> ForeheadArt.CORRECT
                PASS -> ForeheadArt.PASS
                else -> Theme.GRAY
            }
            fill.alpha = (a * 255).toInt()
            c.drawRoundRect(r.left, r.top, r.right, r.bottom - r.height() * 0.12f, rr, rr, fill)
            val ix = r.left + r.height() * 0.52f
            val iy = r.centerY() - r.height() * 0.05f
            art.alpha = 255
            when (kind) {
                CORRECT -> ForeheadArt.drawCheck(c, ix, iy, iconS, Theme.TEXT, art)
                PASS -> ForeheadArt.drawPassArrow(c, ix, iy, iconS, Theme.TEXT, art, path)
                else -> ForeheadArt.drawWatch(c, ix, iy, iconS * 0.42f, 0.25f, art)
            }
            text.alpha = (a * 255).toInt()
            c.drawText(s, r.left + r.height() * 0.98f, r.centerY() + text.textSize * 0.3f, text)
        }
        text.alpha = 255
        if (more.isNotEmpty() && visible >= n) {
            val r = rects[n]
            fill.color = Theme.Party.CARD_LIGHT
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill)
            text.textAlign = Paint.Align.CENTER
            c.drawText(more, r.centerX(), r.centerY() + text.textSize * 0.36f, text)
            text.textAlign = Paint.Align.LEFT
        }
    }

    override fun onDetachedFromWindow() {
        stopReveal()
        super.onDetachedFromWindow()
    }

    companion object {
        const val MAX_CHIPS = 48
        private val CORRECT = Answer.CORRECT.ordinal
        private val PASS = Answer.PASS.ordinal
        const val EMPTY = "단어가 없어요"
    }
}
