package com.siwoo.party.games.colorcode.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.colorcode.GameConfig

/**
 * 추리 기록판: 두 사람의 판을 나란히 (추리 기록은 모두 공개 — 비밀 코드는 끝날 때까지 들고 있지도 않는다).
 * 판마다: 머리띠(색 동그라미·이름 + 오른쪽 "정답" 4칸 — 끝나기 전엔 "?", reveal 때 한 칸씩 뒤집혀 공개)
 *   + 10줄 (번호 · 말 4개 · 2×2 핀, 금색 먼저).
 * 지금 추리하는 사람 판은 금색 테두리 + 다음 줄에 입력 중인 색을 미리 보여 준다. 마지막 기회는 빨강 테두리.
 * 맞힌 줄은 금색 띠, 그 뒤 줄은 흐리게. 새 줄은 말이 튀어나오고 핀이 하나씩 꽂힌다 (진행 중일 때만 다시 그림).
 * onDraw 할당 없음 (번호 글자는 미리 만든 배열).
 */
class RecordBoardView(context: Context) : View(context) {

    /** 애니메이션 시계 (게임 시간 — 일시정지 때 멈춤) */
    fun interface Clock { fun now(): Long }

    var clock: Clock = Clock { SystemClock.uptimeMillis() }

    private val n = GameConfig.PLAYERS
    private val maxRows = GameConfig.MAX_TRIES
    private val names = Array(n) { "" }
    private val initials = Array(n) { "" }
    private val colors = IntArray(n) { Theme.GRAY }
    private val nameSize = FloatArray(n)

    // 기록 (사람 × 줄 × 칸)
    private val guesses = Array(n) { Array(maxRows) { IntArray(GameConfig.SLOTS) } }
    private val golds = Array(n) { IntArray(maxRows) }
    private val silvers = Array(n) { IntArray(maxRows) }
    private val counts = IntArray(n)
    private val solvedRow = IntArray(n) { -1 }

    /** 지금 입력 중인 색 (지금 차례 판의 다음 줄에 미리 보기) */
    private val pending = IntArray(GameConfig.SLOTS) { -1 }

    /** 정답 (판 p 가 맞혀야 하는 상대 코드) — reveal 전엔 비어 있음 */
    private val targets = Array(n) { IntArray(GameConfig.SLOTS) { -1 } }
    private var revealed = false
    private var revealStart = 0L
    private var revealMs = 1L

    private var current = -1
    private var lastChance = -1

    private var popPlayer = -1
    private var popRow = -1
    private var popStart = 0L
    private var popMs = 1L

    // 배치
    private val panels = Array(n) { RectF() }
    private val bandShader = arrayOfNulls<Shader>(n)
    private var headH = 0f
    private var rowH = 0f
    private var pad = 0f
    private var rowsTop = 0f
    private var pegR = 0f
    private var pegStep = 0f
    private var idxW = 0f
    private var pinStep = 0f
    private var pinR = 0f
    private var contentW = 0f
    private var tgtR = 0f
    private var tgtStep = 0f
    private val tmp = RectF()
    private val arrow = Path()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pegPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val initialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val indexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val qPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }

    /** 판 p 의 줄 수 (테스트용) */
    fun count(p: Int): Int = counts[p]

    /** 판 p 의 줄 r 핀 (테스트용) */
    fun goldAt(p: Int, r: Int): Int = golds[p][r]
    fun silverAt(p: Int, r: Int): Int = silvers[p][r]
    fun colorAt(p: Int, r: Int, slot: Int): Int = guesses[p][r][slot]

    /** 판 p 머리띠에 공개된 정답 (공개 전이면 null) — 테스트용 */
    fun shownTarget(p: Int): IntArray? = if (revealed) targets[p].copyOf() else null

    /** 지금 미리 보기로 그리는 입력 색 (테스트용) */
    fun pendingAt(slot: Int): Int = pending[slot]

    fun setup(playerNames: List<String>, playerColors: IntArray) {
        for (p in 0 until n) {
            names[p] = playerNames[p]
            initials[p] = playerNames[p].take(1)
            colors[p] = playerColors[p]
        }
        relayout()
        invalidate()
    }

    /** 지금 추리하는 사람 (-1 = 없음) */
    fun setCurrent(p: Int) {
        if (current != p) { current = p; invalidate() }
    }

    /** 마지막 기회 중인 사람 (-1 = 없음) */
    fun setLastChance(p: Int) {
        if (lastChance != p) { lastChance = p; invalidate() }
    }

    /** 입력 중인 색 미리 보기 (colorAt 순서, -1 빈칸) */
    fun setPending(slot: Int, color: Int) {
        if (pending[slot] != color) { pending[slot] = color; invalidate() }
    }

    fun clearPending() {
        pending.fill(-1)
        invalidate()
    }

    /** 기록 한 줄 추가 (popDur 동안 말이 튀고 핀이 하나씩 꽂힘) */
    fun addGuess(p: Int, code: IntArray, gold: Int, silver: Int, popDur: Long) {
        val r = counts[p]
        if (r >= maxRows) return
        for (i in 0 until GameConfig.SLOTS) guesses[p][r][i] = code[i]
        golds[p][r] = gold
        silvers[p][r] = silver
        counts[p] = r + 1
        if (gold == GameConfig.SLOTS) solvedRow[p] = r
        pending.fill(-1)
        popPlayer = p
        popRow = r
        popStart = clock.now()
        popMs = popDur.coerceAtLeast(1)
        invalidate()
    }

    /** 정답 공개 (판 끝): target0 = 판 0 이 맞혀야 했던 코드(= 사람 1 의 비밀) */
    fun reveal(target0: IntArray, target1: IntArray, ms: Long) {
        for (i in 0 until GameConfig.SLOTS) {
            targets[0][i] = target0[i]
            targets[1][i] = target1[i]
        }
        revealed = true
        revealStart = clock.now()
        revealMs = ms.coerceAtLeast(1)
        invalidate()
    }

    /** 판 p 의 최근 줄 가운데 (연출용, 이 View 좌표) */
    fun lastRowCenter(p: Int, out: PointF) {
        val r = panels[p]
        val row = (counts[p] - 1).coerceAtLeast(0)
        out.set(r.centerX(), rowsTop + rowH * (row + 0.5f))
    }

    /** 판 p 머리띠 가운데 (연출용) */
    fun headCenter(p: Int, out: PointF) {
        val r = panels[p]
        out.set(r.centerX(), r.top + headH / 2f)
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
        headH = Theme.dpf(context, 40f)
        for (p in 0 until n) {
            val l = p * (pw + gap)
            panels[p].set(l, 0f, l + pw, h)
            bandShader[p] = LinearGradient(0f, 0f, 0f, headH, Theme.shade(colors[p], 1.1f), Theme.shade(colors[p], 0.62f), Shader.TileMode.CLAMP)
        }
        rowsTop = headH + pad * 0.6f
        rowH = (h - rowsTop - pad - Theme.dpf(context, 3f)) / maxRows
        // 한 줄: 번호 · 말 4개 · 핀 2×2 — 폭에 맞춰 말 간격 정함
        idxW = rowH * 1.0f
        pinStep = rowH * 0.42f
        pinR = rowH * 0.165f
        val pinsW = pinStep * 2f + rowH * 0.2f
        pegStep = minOf(rowH * 1.25f, (pw - pad * 2f - idxW - pinsW) / GameConfig.SLOTS)
        pegR = minOf(rowH * 0.42f, pegStep * 0.42f)
        contentW = idxW + pegStep * GameConfig.SLOTS + pinsW
        indexPaint.textSize = rowH * 0.48f
        initialPaint.textSize = Theme.dpf(context, 13f)
        // 머리띠 정답 칸
        tgtR = headH * 0.2f
        tgtStep = tgtR * 2.3f
        qPaint.textSize = tgtR * 1.3f
        // 화살표 (지금 줄 표시, 왼쪽 → 오른쪽)
        arrow.reset()
        arrow.moveTo(-rowH * 0.2f, -rowH * 0.22f)
        arrow.lineTo(rowH * 0.22f, 0f)
        arrow.lineTo(-rowH * 0.2f, rowH * 0.22f)
        arrow.close()
        // 이름 글자 크기: 동그라미와 정답 칸 사이에 들어가게
        val avail = pw - pad * 2f - headH * 0.6f - Theme.dpf(context, 12f) - (tgtStep * GameConfig.SLOTS + Theme.dpf(context, 4f))
        for (p in 0 until n) {
            namePaint.textSize = Theme.dpf(context, 15f)
            while (namePaint.measureText(names[p]) > avail && namePaint.textSize > Theme.dpf(context, 9f)) namePaint.textSize *= 0.92f
            nameSize[p] = namePaint.textSize
        }
    }

    override fun onDraw(c: Canvas) {
        if (width <= 0) return
        val now = clock.now()
        var animating = false
        for (p in 0 until n) if (drawPanel(c, p, now)) animating = true
        if (animating) postInvalidateOnAnimation()
    }

    /** 판 하나 (애니메이션 중이면 true) */
    private fun drawPanel(c: Canvas, p: Int, now: Long): Boolean {
        val r = panels[p]
        val rad = Theme.dpf(context, 14f)
        val depth = Theme.dpf(context, 3f)
        var animating = false
        fill.style = Paint.Style.FILL
        fill.color = 0x55000000
        c.drawRoundRect(r.left, r.top + depth, r.right, r.bottom, rad, rad, fill)
        fill.color = ColorArt.BOARD
        tmp.set(r.left, r.top, r.right, r.bottom - depth)
        c.drawRoundRect(tmp, rad, rad, fill)
        // 머리띠
        band.shader = bandShader[p]
        c.save()
        c.clipRect(r.left, r.top, r.right, r.top + headH)
        c.drawRoundRect(tmp, rad, rad, band)
        c.restore()
        val cy = r.top + headH / 2f
        val cr = headH * 0.3f
        val ccx = r.left + pad + cr + 2f
        fill.color = Theme.GOLD
        c.drawCircle(ccx, cy, cr + Theme.dpf(context, 1.5f), fill)
        fill.color = colors[p]
        c.drawCircle(ccx, cy, cr, fill)
        initialPaint.color = Theme.textOn(colors[p])
        c.drawText(initials[p], ccx, cy + initialPaint.textSize * 0.36f, initialPaint)
        // 정답 칸 (오른쪽)
        val tx0 = r.right - pad - tgtStep * GameConfig.SLOTS + tgtStep / 2f
        var revealK = 1f
        if (revealed) {
            val e = now - revealStart
            if (e < revealMs) { revealK = e.toFloat() / revealMs; animating = true }
        }
        fill.color = 0x55000000
        c.drawRoundRect(tx0 - tgtStep / 2f - Theme.dpf(context, 1f), cy - tgtR * 1.3f, r.right - pad + Theme.dpf(context, 1f), cy + tgtR * 1.3f,
            tgtR * 1.3f, tgtR * 1.3f, fill)
        for (i in 0 until GameConfig.SLOTS) {
            val x = tx0 + i * tgtStep
            val col = targets[p][i]
            if (revealed && col >= 0) {
                val kk = (revealK * GameConfig.SLOTS - i).coerceIn(0f, 1f) // 한 칸씩 차례로 뒤집힘
                c.save()
                c.scale(if (kk < 0.5f) 1f - kk * 2f else kk * 2f - 1f, 1f, x, cy)
                if (kk < 0.5f) hiddenTarget(c, x, cy) else ColorArt.peg(c, x, cy, tgtR, col, pegPaint)
                c.restore()
            } else hiddenTarget(c, x, cy)
        }
        // 이름
        val nameX = ccx + cr + Theme.dpf(context, 6f)
        val maxName = tx0 - tgtStep / 2f - nameX - Theme.dpf(context, 4f)
        c.save()
        c.clipRect(nameX, r.top, nameX + maxName, r.top + headH)
        namePaint.textSize = nameSize[p]
        c.drawText(names[p], nameX, cy + namePaint.textSize * 0.36f, namePaint)
        c.restore()
        // 줄들
        val x0 = r.left + (r.width() - contentW) / 2f
        val solved = solvedRow[p]
        val active = p == current && counts[p] < maxRows && solved < 0
        for (row in 0 until maxRows) {
            val top = rowsTop + row * rowH
            val rcy = top + rowH / 2f
            val done = row < counts[p]
            val isActive = active && row == counts[p]
            val dim = solved in 0 until row
            // 줄 바탕
            if (row == solved) {
                fill.color = 0x55FFC93C
                c.drawRoundRect(r.left + pad * 0.5f, top + rowH * 0.05f, r.right - pad * 0.5f, top + rowH * 0.95f, rowH * 0.3f, rowH * 0.3f, fill)
            } else if (isActive) {
                fill.color = if (p == lastChance) 0x40FF4D5E else 0x33FFFFFF
                c.drawRoundRect(r.left + pad * 0.5f, top + rowH * 0.05f, r.right - pad * 0.5f, top + rowH * 0.95f, rowH * 0.3f, rowH * 0.3f, fill)
            } else if (row % 2 == 1) {
                fill.color = 0x14FFFFFF
                c.drawRect(r.left + pad * 0.5f, top, r.right - pad * 0.5f, top + rowH, fill)
            }
            // 번호 또는 화살표
            val ix = x0 + idxW / 2f
            if (isActive) {
                fill.color = if (p == lastChance) Theme.RED else Theme.GOLD
                c.save()
                c.translate(ix, rcy)
                c.drawPath(arrow, fill)
                c.restore()
            } else {
                indexPaint.color = if (dim) 0x55B8C3F0 else if (done) Theme.TEXT_SUB else 0x66B8C3F0
                c.drawText(INDEX[row], ix, rcy + indexPaint.textSize * 0.36f, indexPaint)
            }
            // 말 4개
            var pop = 1f
            if (done && p == popPlayer && row == popRow) {
                val e = now - popStart
                if (e < popMs) { pop = e.toFloat() / popMs; animating = true }
            }
            for (i in 0 until GameConfig.SLOTS) {
                val px = x0 + idxW + pegStep * (i + 0.5f)
                val col = if (done) guesses[p][row][i] else if (isActive) pending[i] else -1
                if (col < 0) {
                    ColorArt.hole(c, px, rcy, pegR, pegPaint)
                } else {
                    var pr = pegR
                    if (pop < 1f) {
                        // 칸마다 조금씩 늦게 튀어나옴 (앞 절반 시간)
                        val k = ((pop * 2f) - i * 0.15f).coerceIn(0f, 1f)
                        pr = pegR * (1.4f - 0.4f * k)
                    }
                    ColorArt.peg(c, px, rcy, pr, col, pegPaint, if (dim) 110 else if (done) 255 else 225)
                }
            }
            // 핀 2×2
            val pcx = x0 + idxW + pegStep * GameConfig.SLOTS + rowH * 0.1f + pinStep
            if (done) {
                val shown = if (pop < 1f) ((pop - 0.45f) / 0.55f * GameConfig.SLOTS + 1f).toInt().coerceIn(0, GameConfig.SLOTS) else GameConfig.SLOTS
                ColorArt.pins(c, pcx, rcy, pinStep, pinR, golds[p][row], silvers[p][row], shown, pegPaint)
            } else {
                ColorArt.pins(c, pcx, rcy, pinStep, pinR, 0, 0, 0, pegPaint)
            }
        }
        // 지금 차례 테두리 (마지막 기회는 빨강)
        if (p == current || p == lastChance) {
            stroke.color = if (p == lastChance) Theme.RED else Theme.GOLD
            stroke.strokeWidth = Theme.dpf(context, 3f)
            val ins = stroke.strokeWidth / 2f
            tmp.set(r.left + ins, r.top + ins, r.right - ins, r.bottom - depth - ins)
            c.drawRoundRect(tmp, rad, rad, stroke)
        } else {
            stroke.color = Theme.CARD_BORDER
            stroke.strokeWidth = Theme.dpf(context, 1f)
            c.drawRoundRect(tmp, rad, rad, stroke)
        }
        return animating
    }

    /** 가려진 정답 칸 ("?") */
    private fun hiddenTarget(c: Canvas, x: Float, y: Float) {
        fill.color = 0xFF0C1338.toInt()
        c.drawCircle(x, y, tgtR, fill)
        stroke.color = Theme.GOLD_DARK
        stroke.strokeWidth = Theme.dpf(context, 1f)
        c.drawCircle(x, y, tgtR, stroke)
        c.drawText(QUESTION, x, y + qPaint.textSize * 0.36f, qPaint)
    }

    companion object {
        private val INDEX = Array(GameConfig.MAX_TRIES) { (it + 1).toString() }
        private const val QUESTION = "?"
    }
}
