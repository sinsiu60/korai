package com.siwoo.party.games.baseball.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.baseball.GameConfig

/**
 * 숫자 야구 기록표: 두 사람의 추측 기록을 나란히 (추측은 모두 공개).
 * 칸마다: 위 머리띠(색 동그라미·이름·비밀 숫자 칸 — 끝나기 전엔 "?") + 줄마다 "번호 · 숫자 3칸 · S/B 판정 알약".
 * 지금 추측하는 사람 칸은 금색 테두리. 새 줄은 잠깐 튀어나온다 (진행 중일 때만 다시 그림).
 * 줄이 넘치면 최근 기록만 보이고 맨 위에 "▲ 이전 n개".
 * 문자열은 기록이 추가될 때만 만든다 → onDraw 할당 없음.
 */
class RecordBoardView(context: Context) : View(context) {

    /** 애니메이션 시계 (게임 시간 — 일시정지 때 멈춤) */
    fun interface Clock { fun now(): Long }

    var clock: Clock = Clock { SystemClock.uptimeMillis() }

    private class Row(val number: String, val strikes: Int, val balls: Int, val index: String)

    private val n = GameConfig.PLAYERS
    private val names = Array(n) { "" }
    private val initials = Array(n) { "" }
    private val colors = IntArray(n) { Theme.GRAY }
    private val rows = List(n) { ArrayList<Row>() }
    private val secrets = Array(n) { "" }
    private val moreTexts = Array(n) { "" }
    private val nameSize = FloatArray(n)
    private var current = -1
    private var lastChance = -1
    private var revealStart = -1L
    private var revealMs = 1L

    private var popPlayer = -1
    private var popStart = 0L
    private var popMs = 1L

    // 배치
    private val panels = Array(n) { RectF() }
    private val bandShader = arrayOfNulls<Shader>(n)
    private var headH = 0f
    private var rowH = 0f
    private var pad = 0f
    private var capacity = 1
    private val tmp = RectF()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val initialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val headDigitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val indexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    /** 사람별 기록 수 (테스트용) */
    fun count(p: Int): Int = rows[p].size

    /** 사람별 공개된 비밀 ("" 이면 아직 가려짐) — 테스트용 */
    fun shownSecret(p: Int): String = secrets[p]

    fun setup(playerNames: List<String>, playerColors: IntArray) {
        for (p in 0 until n) {
            names[p] = playerNames[p]
            initials[p] = playerNames[p].take(1)
            colors[p] = playerColors[p]
        }
        relayout()
        invalidate()
    }

    /** 지금 추측하는 사람 (-1 = 없음) */
    fun setCurrent(p: Int) {
        if (current != p) { current = p; invalidate() }
    }

    /** 마지막 기회 중인 사람 (-1 = 없음) */
    fun setLastChance(p: Int) {
        if (lastChance != p) { lastChance = p; invalidate() }
    }

    /** 기록 한 줄 추가 (popMs 동안 튀어나옴) */
    fun addGuess(p: Int, number: String, strikes: Int, balls: Int, popDur: Long) {
        val list = rows[p]
        list.add(Row(number, strikes, balls, (list.size + 1).toString()))
        updateMore(p)
        popPlayer = p
        popStart = clock.now()
        popMs = popDur.coerceAtLeast(1)
        invalidate()
    }

    /** 비밀 숫자 공개 (판 끝) */
    fun reveal(s0: String, s1: String, ms: Long) {
        secrets[0] = s0
        secrets[1] = s1
        revealStart = clock.now()
        revealMs = ms.coerceAtLeast(1)
        invalidate()
    }

    /** p 칸의 최근 줄 가운데 (연출용, 이 View 좌표) */
    fun lastRowCenter(p: Int, out: android.graphics.PointF) {
        val r = panels[p]
        val shown = minOf(rows[p].size, capacity)
        val slot = (shown - 1).coerceAtLeast(0)
        out.set(r.centerX(), r.top + headH + pad * 0.5f + rowH * (slot + 0.5f))
    }

    /** p 칸 머리띠 가운데 (연출용) */
    fun headCenter(p: Int, out: android.graphics.PointF) {
        val r = panels[p]
        out.set(r.centerX(), r.top + headH / 2f)
    }

    private fun updateMore(p: Int) {
        val hidden = rows[p].size - capacity
        moreTexts[p] = if (hidden > 0) "▲ 이전 기록 ${hidden + 1}개" else ""
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val gap = Theme.dpf(context, 8f)
        pad = Theme.dpf(context, 6f)
        val pw = (w - gap) / 2f
        for (p in 0 until n) {
            val l = p * (pw + gap)
            panels[p].set(l, 0f, l + pw, h)
            bandShader[p] = LinearGradient(0f, 0f, 0f, Theme.dpf(context, 46f), Theme.shade(colors[p], 1.15f), Theme.shade(colors[p], 0.7f),
                Shader.TileMode.CLAMP)
        }
        headH = Theme.dpf(context, 46f)
        val body = h - headH - pad * 1.5f - Theme.dpf(context, 3f)
        rowH = Theme.dpf(context, 30f)
        capacity = (body / rowH).toInt().coerceAtLeast(2)
        rowH = body / capacity
        namePaint.textSize = Theme.dpf(context, 16f)
        initialPaint.textSize = Theme.dpf(context, 14f)
        smallPaint.textSize = Theme.dpf(context, 10f)
        headDigitPaint.textSize = Theme.dpf(context, 15f)
        digitPaint.textSize = rowH * 0.56f
        indexPaint.textSize = rowH * 0.42f
        pillPaint.textSize = rowH * 0.44f
        for (p in 0 until n) updateMore(p)
        // 이름 글자 크기: 동그라미와 비밀 칸 사이에 들어가게 줄임
        val s = headH * 0.5f
        val avail = pw - pad * 2 - headH * 0.6f - Theme.dpf(context, 12f) - (s * 3 + s * 0.36f)
        for (p in 0 until n) {
            namePaint.textSize = Theme.dpf(context, 16f)
            while (namePaint.measureText(names[p]) > avail && namePaint.textSize > Theme.dpf(context, 9f)) namePaint.textSize *= 0.92f
            nameSize[p] = namePaint.textSize
        }
    }

    override fun onDraw(c: Canvas) {
        if (width <= 0) return
        val now = clock.now()
        var animating = false
        for (p in 0 until n) {
            if (drawPanel(c, p, now)) animating = true
        }
        if (animating) postInvalidateOnAnimation()
    }

    /** 칸 하나 그리기 (애니메이션 중이면 true) */
    private fun drawPanel(c: Canvas, p: Int, now: Long): Boolean {
        val r = panels[p]
        val rad = Theme.dpf(context, 14f)
        var animating = false
        // 그림자 + 바탕
        fill.color = 0x55000000
        c.drawRoundRect(r.left, r.top + Theme.dpf(context, 3f), r.right, r.bottom, rad, rad, fill)
        fill.color = Theme.CARD
        tmp.set(r.left, r.top, r.right, r.bottom - Theme.dpf(context, 3f))
        c.drawRoundRect(tmp, rad, rad, fill)
        // 머리띠
        band.shader = bandShader[p]
        c.save()
        c.clipRect(r.left, r.top, r.right, r.top + headH)
        c.drawRoundRect(tmp, rad, rad, band)
        c.restore()
        // 색 동그라미 + 이름
        val cy = r.top + headH / 2f
        val cr = headH * 0.3f
        fill.color = Theme.GOLD
        c.drawCircle(r.left + pad + cr + 2f, cy, cr + Theme.dpf(context, 1.5f), fill)
        fill.color = colors[p]
        c.drawCircle(r.left + pad + cr + 2f, cy, cr, fill)
        initialPaint.color = Theme.textOn(colors[p])
        c.drawText(initials[p], r.left + pad + cr + 2f, cy + initialPaint.textSize * 0.36f, initialPaint)
        // 비밀 숫자 칸 (오른쪽)
        val s = headH * 0.5f
        val sg = s * 0.18f
        val sx0 = r.right - pad - s * 3 - sg * 2
        val sTop = cy - s / 2f
        val secret = secrets[p]
        var revealK = 1f
        if (secret.isNotEmpty() && revealStart >= 0) {
            val e = now - revealStart
            if (e < revealMs) { revealK = e.toFloat() / revealMs; animating = true }
        }
        for (k in 0 until GameConfig.DIGITS) {
            val x = sx0 + k * (s + sg)
            if (secret.isNotEmpty()) {
                val kk = ((revealK * 3f) - k).coerceIn(0f, 1f) // 한 칸씩 차례로 뒤집힘
                c.save()
                c.scale(if (kk < 0.5f) 1f - kk * 2f else kk * 2f - 1f, 1f, x + s / 2f, sTop + s / 2f)
                if (kk < 0.5f) hiddenSlot(c, x, sTop, s) else {
                    fill.color = Theme.GOLD_LIGHT
                    c.drawRoundRect(x, sTop, x + s, sTop + s, s * 0.2f, s * 0.2f, fill)
                    headDigitPaint.color = Theme.INK
                    c.drawText(DIGIT_STR[secret[k] - '0'], x + s / 2f, sTop + s / 2f + headDigitPaint.textSize * 0.36f, headDigitPaint)
                }
                c.restore()
            } else hiddenSlot(c, x, sTop, s)
        }
        // 이름 (동그라미와 비밀 칸 사이)
        val nameX = r.left + pad + cr * 2 + Theme.dpf(context, 8f)
        val maxName = sx0 - nameX - Theme.dpf(context, 4f)
        c.save()
        c.clipRect(nameX, r.top, nameX + maxName, r.top + headH)
        namePaint.textSize = nameSize[p]
        c.drawText(names[p], nameX, cy + namePaint.textSize * 0.36f, namePaint)
        c.restore()
        // 줄들
        val list = rows[p]
        val body = r.top + headH + pad * 0.5f
        val more = list.size > capacity
        val first = if (more) list.size - (capacity - 1) else 0
        var slot = 0
        if (more) {
            smallPaint.color = Theme.TEXT_SUB
            c.drawText(moreTexts[p], r.centerX(), body + rowH / 2f + smallPaint.textSize * 0.36f, smallPaint)
            slot = 1
        }
        if (list.isEmpty()) {
            smallPaint.color = Theme.TEXT_SUB
            c.drawText(EMPTY_TEXT, r.centerX(), body + rowH * 1.2f, smallPaint)
        }
        for (i in first until list.size) {
            val row = list[i]
            val top = body + slot * rowH
            var k = 1f
            if (p == popPlayer && i == list.size - 1) {
                val e = now - popStart
                if (e < popMs) { k = e.toFloat() / popMs; animating = true }
            }
            drawRow(c, r, top, row, i == list.size - 1, k)
            slot++
        }
        // 지금 차례 테두리 (마지막 기회는 빨강)
        if (p == current || p == lastChance) {
            stroke.color = if (p == lastChance) Theme.RED else Theme.GOLD
            stroke.strokeWidth = Theme.dpf(context, 3f)
            val ins = stroke.strokeWidth / 2f
            tmp.set(r.left + ins, r.top + ins, r.right - ins, r.bottom - Theme.dpf(context, 3f) - ins)
            c.drawRoundRect(tmp, rad, rad, stroke)
        } else {
            stroke.color = Theme.CARD_BORDER
            stroke.strokeWidth = Theme.dpf(context, 1f)
            c.drawRoundRect(tmp, rad, rad, stroke)
        }
        return animating
    }

    private fun hiddenSlot(c: Canvas, x: Float, y: Float, s: Float) {
        fill.color = 0x66000000
        c.drawRoundRect(x, y, x + s, y + s, s * 0.2f, s * 0.2f, fill)
        headDigitPaint.color = Theme.GOLD
        c.drawText(QUESTION, x + s / 2f, y + s / 2f + headDigitPaint.textSize * 0.36f, headDigitPaint)
    }

    /** 기록 한 줄: 번호 · 숫자 3칸 · 판정 (k = 등장 진행 0~1) */
    private fun drawRow(c: Canvas, r: RectF, top: Float, row: Row, latest: Boolean, k: Float) {
        val cy = top + rowH / 2f
        val s = rowH * 0.78f
        c.save()
        if (k < 1f) {
            val e = 1f - (1f - k) * (1f - k)
            val sc = 1.35f - 0.35f * e
            c.scale(sc, sc, r.centerX(), cy)
        }
        if (latest) {
            fill.color = 0x22FFFFFF
            c.drawRoundRect(r.left + pad * 0.6f, top + rowH * 0.06f, r.right - pad * 0.6f, top + rowH * 0.94f, rowH * 0.2f, rowH * 0.2f, fill)
        }
        // 번호
        val ix = r.left + pad + rowH * 0.42f
        c.drawText(row.index, ix, cy + indexPaint.textSize * 0.36f, indexPaint)
        // 숫자 칸
        val dg = s * 0.12f
        var x = ix + rowH * 0.5f
        for (d in 0 until GameConfig.DIGITS) {
            BaseballArt.digitBox(c, x, cy - s / 2f, s, DIGIT_STR[row.number[d] - '0'], fill, digitPaint)
            x += s + dg
        }
        // 판정 알약
        val px = x + rowH * 0.15f
        val avail = r.right - pad - px
        val ph = rowH * 0.66f
        val hit = row.strikes == GameConfig.DIGITS
        if (row.strikes == 0 && row.balls == 0) {
            pill(c, px, cy, avail, ph, BaseballArt.OUT, OUT_TEXT, true)
        } else if (hit) {
            pill(c, px, cy, avail, ph, BaseballArt.STRIKE, HIT_TEXT, true)
        } else {
            val pw = (avail - rowH * 0.12f) / 2f
            pill(c, px, cy, pw, ph, BaseballArt.STRIKE, S_TEXT[row.strikes], row.strikes > 0)
            pill(c, px + pw + rowH * 0.12f, cy, pw, ph, BaseballArt.BALL, B_TEXT[row.balls], row.balls > 0)
        }
        c.restore()
    }

    private fun pill(c: Canvas, l: Float, cy: Float, w: Float, h: Float, color: Int, label: String, on: Boolean) {
        val rr = h / 2f
        fill.color = if (on) Theme.shade(color, 0.55f) else 0xFF1A2560.toInt()
        c.drawRoundRect(l, cy - h / 2f + h * 0.1f, l + w, cy + h / 2f + h * 0.1f, rr, rr, fill)
        fill.color = if (on) color else 0xFF2A3570.toInt()
        c.drawRoundRect(l, cy - h / 2f, l + w, cy + h / 2f, rr, rr, fill)
        pillPaint.color = if (on) Theme.textOn(color) else 0xFF6F7BB0.toInt()
        c.drawText(label, l + w / 2f, cy + pillPaint.textSize * 0.36f, pillPaint)
    }

    companion object {
        private val DIGIT_STR = Array(10) { it.toString() }
        private val S_TEXT = Array(GameConfig.DIGITS + 1) { "${it}S" }
        private val B_TEXT = Array(GameConfig.DIGITS + 1) { "${it}B" }
        private const val OUT_TEXT = "아웃"
        private const val HIT_TEXT = "정답!"
        private const val QUESTION = "?"
        private const val EMPTY_TEXT = "아직 추측이 없어요"
    }
}
