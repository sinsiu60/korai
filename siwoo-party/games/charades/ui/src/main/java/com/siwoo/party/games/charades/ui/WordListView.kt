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
import com.siwoo.party.games.charades.Answer
import com.siwoo.party.games.charades.GameConfig
import com.siwoo.party.games.charades.WordResult
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * 차례가 끝난 뒤 단어 목록 공개: 왼쪽 "맞힌 단어" 판(초록 칩 — 앞 동그라미 = 맞힌 사람 색), 오른쪽 "패스" 판(주황 칩, 회색 = 시간 끝에 보던 단어).
 * 칩은 나온 순서대로 하나씩 나타난다 (진행 중에만 postOnAnimation). 배치·글자·문자열은 setResults/크기 바뀔 때만 → onDraw 할당 없음.
 */
class WordListView(context: Context) : View(context) {

    // 맞힌 단어 (최대 MAX_CORRECT 칸, 넘치면 마지막 칸 "+N")
    private val cWords = arrayOfNulls<String>(MAX_CORRECT)
    private val cColors = IntArray(MAX_CORRECT)
    private val cOrder = IntArray(MAX_CORRECT)
    private val cSize = FloatArray(MAX_CORRECT)
    /** 맞힌 사람 이름 첫 글자 (개인전, null = 체크 표시) */
    private val cInitial = arrayOfNulls<String>(MAX_CORRECT)
    private val cRects = Array(MAX_CORRECT) { RectF() }
    private var cn = 0
    private var more = ""
    // 패스 + 시간 끝 단어 (최대 MAX_SIDE 칸)
    private val sWords = arrayOfNulls<String>(MAX_SIDE)
    private val sTimeout = BooleanArray(MAX_SIDE)
    private val sOrder = IntArray(MAX_SIDE)
    private val sSize = FloatArray(MAX_SIDE)
    private val sRects = Array(MAX_SIDE) { RectF() }
    private var sn = 0
    private var total = 0

    private var correctHead = ""
    private var passHead = ""
    private var hasTimeout = false

    /** 지금 보이는 칩 수 (연출, 결과 순서 기준) */
    private var shown = 0f

    private val leftPanel = RectF()
    private val rightPanel = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val cText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val initialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val sText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.Party.TEXT_SUB }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.Party.TEXT_SUB }
    private val path = Path()

    private var revealStart = 0L
    private var revealMs = 0L
    private var revealing = false
    private val frame = object : Runnable {
        override fun run() {
            if (!revealing) return
            val f = ((SystemClock.uptimeMillis() - revealStart).toFloat() / revealMs).coerceIn(0f, 1f)
            shown = f * total
            invalidate()
            if (f >= 1f) revealing = false else postOnAnimation(this)
        }
    }

    /** 맞힌 칩 수 / 패스·시간 끝 칩 수 (테스트 확인용) */
    val correctChips: Int get() = cn
    val sideChips: Int get() = sn

    /**
     * 결과 넣기 + 칩이 하나씩 나타나는 연출 (chipMs = 칩 하나 간격, 0 이면 바로)
     * guesserColors[p] = 맞힌 사람 색, guesserInitials[p] = 맞힌 사람 이름 첫 글자 (null 이면 체크 표시 — 팀전)
     */
    fun setResults(list: List<WordResult>, guesserColors: IntArray, guesserInitials: Array<String>?, chipMs: Long) {
        cn = 0
        sn = 0
        var correctTotal = 0
        var passTotal = 0
        hasTimeout = false
        for (k in 0 until MAX_CORRECT) { cWords[k] = null; cInitial[k] = null }
        for (k in 0 until MAX_SIDE) sWords[k] = null
        for ((i, r) in list.withIndex()) {
            when (r.answer) {
                Answer.CORRECT -> {
                    correctTotal++
                    if (cn < MAX_CORRECT) {
                        cWords[cn] = r.word
                        cColors[cn] = if (r.guesser in guesserColors.indices) guesserColors[r.guesser] else CharadesArt.CORRECT
                        cOrder[cn] = i
                        cInitial[cn] = guesserInitials?.getOrNull(r.guesser)
                        cn++
                    }
                }
                Answer.PASS, Answer.TIMEOUT -> {
                    if (r.answer == Answer.PASS) passTotal++ else hasTimeout = true
                    if (sn < MAX_SIDE) {
                        sWords[sn] = r.word
                        sTimeout[sn] = r.answer == Answer.TIMEOUT
                        sOrder[sn] = i
                        sn++
                    }
                }
            }
        }
        // 맞힌 단어가 칸보다 많으면 마지막 칸을 "+N"
        if (correctTotal > MAX_CORRECT) {
            cn = MAX_CORRECT - 1
            more = "+${correctTotal - cn}"
        } else {
            more = ""
        }
        total = list.size
        correctHead = "맞힌 단어 ${correctTotal}개"
        passHead = "패스 $passTotal/${GameConfig.MAX_PASSES}"
        layoutAll()
        stopReveal()
        if (chipMs <= 0 || total == 0) {
            shown = total.toFloat()
            invalidate()
        } else {
            shown = 0f
            revealMs = chipMs * total
            revealStart = SystemClock.uptimeMillis()
            revealing = true
            postOnAnimation(frame)
        }
    }

    /** 연출 건너뛰기 (모두 보이기) */
    fun showAll() {
        stopReveal()
        shown = total.toFloat()
        invalidate()
    }

    private fun stopReveal() {
        revealing = false
        removeCallbacks(frame)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutAll()
    }

    private fun layoutAll() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val gap = h * 0.04f
        val lw = (w - gap) * 0.66f
        leftPanel.set(0f, 0f, lw, h)
        rightPanel.set(lw + gap, 0f, w, h)
        headPaint.textSize = h * 0.075f
        emptyPaint.textSize = h * 0.065f
        notePaint.textSize = h * 0.05f
        val nw = notePaint.measureText(NOTE_TIMEOUT)
        val nmax = rightPanel.width() * 0.9f
        if (nw > nmax) notePaint.textSize *= nmax / nw
        val pad = h * 0.04f
        val headH = h * 0.14f
        layoutCorrect(leftPanel.left + pad, leftPanel.top + headH, leftPanel.right - pad, leftPanel.bottom - pad)
        // 오른쪽: 세로 4칸 (패스 3 + 시간 끝 1), 아래 안내 한 줄
        val top = rightPanel.top + headH
        val bottom = rightPanel.bottom - pad - h * 0.08f
        val sg = h * 0.03f
        val ch = minOf((bottom - top - sg * (MAX_SIDE - 1)) / MAX_SIDE, h * 0.17f)
        for (k in 0 until MAX_SIDE) {
            val y = top + k * (ch + sg)
            sRects[k].set(rightPanel.left + pad, y, rightPanel.right - pad, y + ch)
        }
        val maxW = rightPanel.width() - pad * 2 - ch * 1.25f
        for (k in 0 until sn) sSize[k] = fit(sText, sWords[k], ch * 0.44f, maxW)
    }

    /** 맞힌 칩 칸: 칩 가로:세로 ≈ 3.4:1 이 되게 열 수를 고른다 (모두 판 안에) */
    private fun layoutCorrect(l: Float, t: Float, r: Float, b: Float) {
        val slots = cn + if (more.isNotEmpty()) 1 else 0
        if (slots == 0) return
        val w = r - l
        val h = b - t
        val gap = height * 0.03f
        var cols = ceil(sqrt(slots * w / (h * 3.4f))).toInt().coerceIn(1, 4)
        var rows = ceil(slots / cols.toFloat()).toInt()
        while (cols < 4 && (h - gap * (rows - 1)) / rows < height * 0.1f) { cols++; rows = ceil(slots / cols.toFloat()).toInt() }
        val cw = (w - gap * (cols - 1)) / cols
        val ch = minOf((h - gap * (rows - 1)) / rows, cw / 2.4f, height * 0.17f)
        for (k in 0 until slots) {
            val x = l + (k % cols) * (cw + gap)
            val y = t + (k / cols) * (ch + gap)
            cRects[k].set(x, y, x + cw, y + ch)
        }
        val maxW = cw - ch * 1.15f
        for (k in 0 until cn) cSize[k] = fit(cText, cWords[k], ch * 0.46f, maxW)
        cText.textSize = ch * 0.46f
        initialPaint.textSize = ch * 0.36f
    }

    /** 칩마다 글자 크기: 기본 size, 넓으면 칸에 맞게 줄임 (layout 때만) */
    private fun fit(p: Paint, s: String?, size: Float, maxW: Float): Float {
        if (s == null) return size
        p.textSize = size
        val tw = p.measureText(s)
        return if (tw > maxW) size * maxW / tw else size
    }

    override fun onDraw(c: Canvas) {
        if (width == 0) return
        val h = height.toFloat()
        drawPanel(c, leftPanel, CharadesArt.CORRECT, correctHead)
        drawPanel(c, rightPanel, CharadesArt.PASS, passHead)
        // 맞힌 단어
        if (cn == 0 && more.isEmpty()) {
            c.drawText(EMPTY_CORRECT, leftPanel.centerX(), leftPanel.centerY() + h * 0.04f, emptyPaint)
        }
        for (k in 0 until cn) {
            val a = alphaOf(cOrder[k])
            if (a <= 0f) continue
            val s = cWords[k] ?: continue
            drawChip(c, cRects[k], CharadesArt.CORRECT, CharadesArt.CORRECT_DARK, a)
            val r = cRects[k]
            // 맞힌 사람 색 동그라미 + 체크
            val ir = r.height() * 0.32f
            val ix = r.left + r.height() * 0.5f
            val iy = r.centerY() - r.height() * 0.05f
            art.color = cColors[k]
            art.alpha = (a * 255).toInt()
            c.drawCircle(ix, iy, ir, art)
            art.alpha = 255
            val ini = cInitial[k]
            if (ini != null) {
                initialPaint.color = Theme.textOn(cColors[k])
                c.drawText(ini, ix, iy + initialPaint.textSize * 0.36f, initialPaint)
            } else {
                CharadesArt.drawCheck(c, ix, iy, ir * 1.3f, Theme.TEXT, art)
            }
            cText.alpha = (a * 255).toInt()
            cText.textSize = cSize[k]
            c.drawText(s, r.left + r.height() * 0.92f, r.centerY() + cText.textSize * 0.3f, cText)
        }
        cText.alpha = 255
        if (cn > 0) cText.textSize = cRects[0].height() * 0.46f
        if (more.isNotEmpty() && shown >= total) {
            val r = cRects[cn]
            fill.color = Theme.Party.CARD_LIGHT
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill)
            cText.textAlign = Paint.Align.CENTER
            c.drawText(more, r.centerX(), r.centerY() + cText.textSize * 0.36f, cText)
            cText.textAlign = Paint.Align.LEFT
        }
        // 패스 · 시간 끝
        if (sn == 0) {
            c.drawText(EMPTY_PASS, rightPanel.centerX(), rightPanel.centerY() + h * 0.02f, emptyPaint)
        }
        for (k in 0 until sn) {
            val a = alphaOf(sOrder[k])
            if (a <= 0f) continue
            val s = sWords[k] ?: continue
            val r = sRects[k]
            val timeout = sTimeout[k]
            drawChip(c, r, if (timeout) Theme.GRAY else CharadesArt.PASS, if (timeout) CharadesArt.TIMEOUT else CharadesArt.PASS_DARK, a)
            val ix = r.left + r.height() * 0.5f
            val iy = r.centerY() - r.height() * 0.04f
            art.alpha = 255
            if (timeout) CharadesArt.drawWatch(c, ix, iy + r.height() * 0.04f, r.height() * 0.24f, 0f, art)
            else CharadesArt.drawPassArrow(c, ix, iy - r.height() * 0.06f, r.height() * 0.5f, Theme.TEXT, art, path)
            sText.alpha = (a * 255).toInt()
            sText.textSize = sSize[k]
            c.drawText(s, r.left + r.height() * 0.98f, r.centerY() + sText.textSize * 0.3f, sText)
        }
        sText.alpha = 255
        if (hasTimeout) c.drawText(NOTE_TIMEOUT, rightPanel.centerX(), rightPanel.bottom - h * 0.045f, notePaint)
    }

    /** 결과 순서 i 번째 칩의 투명도 (0 = 아직 안 보임) */
    private fun alphaOf(i: Int): Float {
        if (i < shown.toInt()) return 1f
        if (i > shown.toInt()) return 0f
        return shown - i
    }

    private fun drawPanel(c: Canvas, r: RectF, color: Int, head: String) {
        val rr = height * 0.06f
        fill.color = Theme.Party.CARD
        c.drawRoundRect(r, rr, rr, fill)
        stroke.color = color
        stroke.strokeWidth = height * 0.008f
        c.drawRoundRect(r, rr, rr, stroke)
        headPaint.color = color
        c.drawText(head, r.left + height * 0.05f, r.top + height * 0.095f, headPaint)
    }

    private fun drawChip(c: Canvas, r: RectF, color: Int, dark: Int, a: Float) {
        val rr = r.height() / 2f
        fill.color = dark
        fill.alpha = (a * 255).toInt()
        c.drawRoundRect(r, rr, rr, fill)
        fill.color = color
        fill.alpha = (a * 255).toInt()
        c.drawRoundRect(r.left, r.top, r.right, r.bottom - r.height() * 0.12f, rr, rr, fill)
        fill.alpha = 255
    }

    override fun onDetachedFromWindow() {
        stopReveal()
        super.onDetachedFromWindow()
    }

    companion object {
        const val MAX_CORRECT = 32
        /** 패스 최대 3 + 시간 끝 1 */
        const val MAX_SIDE = GameConfig.MAX_PASSES + 1
        const val EMPTY_CORRECT = "맞힌 단어가 없어요"
        const val EMPTY_PASS = "패스 없음"
        const val NOTE_TIMEOUT = "회색 = 시간 끝에 보던 단어"
    }
}
