package com.siwoo.party.games.liardice.ui

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
import com.siwoo.party.common.dice.DiceSet
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.liardice.DoubtResult
import com.siwoo.party.games.liardice.GameConfig
import com.siwoo.party.games.liardice.LiarDiceGame
import kotlin.math.PI
import kotlin.math.sin

/**
 * 가운데 주사위 탁자 (하나의 View, 세 가지 모습):
 * - CUPS: 남은 사람마다 엎어 둔 주사위 컵 (라운드 시작 때 달그락 흔들림)
 * - BID: 지금 걸려 있는 부른 수를 크게 (공통 DiceSet 주사위 1개 + "× N개 이상") + 아래에 각자 컵과 남은 주사위 수
 * - REVEAL: 의심! → 줄마다 컵이 열리며 모든 주사위 공개(공통 DiceSet) → 해당 눈이 아닌 주사위는 흐려지고
 *   해당 눈(금색 테)·와일드 1(하늘색 테)을 하나씩 세며 오른쪽 숫자가 올라감 → 판정 문구
 * 시간은 게임 시계(clock = 일시정지 시간 제외)로 계산하고, 움직이는 동안에만 postOnAnimation 으로 다시 그린다 (멈추면 그리지 않음).
 * onDraw 에서는 객체를 만들지 않는다 (글자는 setter 에서만 만든다).
 */
class TableView(context: Context) : View(context) {

    enum class Mode { CUPS, BID, REVEAL }

    /** 게임 시계 (일시정지 시간 제외) */
    fun interface Clock { fun now(): Long }

    var clock: Clock = Clock { SystemClock.uptimeMillis() }

    var mode = Mode.CUPS
        private set

    // ── 플레이어 ──
    private var n = 0
    private val names = Array(MAX_P) { "" }
    private val colors = IntArray(MAX_P)
    private val lightColors = IntArray(MAX_P)
    private val counts = IntArray(MAX_P)
    private val countTexts = Array(MAX_P) { "" }
    private var current = -1
    private var caption = ""

    // ── 컵 흔들기 ──
    private var shakeStart = 0L
    private var shakeEnd = 0L

    // ── 부른 수 ──
    /** 지금 크게 보이는 부른 수 (0 = 없음) */
    var bidCount = 0
        private set
    var bidFace = 0
        private set
    private var bidText = ""
    private var bidWho = ""
    private var totalText = ""
    private var wildText = ""
    private var popStart = 0L
    private var popEnd = 0L
    private val bidDie = DiceSet(this).apply { count = 1 }

    // ── 공개 ──
    private val sets = Array(MAX_P) { DiceSet(this) }
    private val rowPlayer = IntArray(MAX_P)
    private var rows = 0
    private val match = Array(MAX_P) { BooleanArray(DiceSet.MAX) }
    private val wildHit = Array(MAX_P) { BooleanArray(DiceSet.MAX) }
    private val orderRow = IntArray(MAX_P * DiceSet.MAX)
    private val orderIdx = IntArray(MAX_P * DiceSet.MAX)
    /** 공개된 주사위 중 해당 눈으로 세어지는 개수 */
    var matchTotal = 0
        private set
    /** 지금까지 센 개수 */
    var counted = 0
        private set
    private var openStart = 0L
    private var openPerRow = 1L
    private var openStagger = 0L
    private var dimStart = Long.MAX_VALUE
    private var dimMs = 1L
    private var headText = ""
    private var headSub = ""
    private var bidLine = ""
    private var countLabel = ""
    private var verdict = ""
    private var verdictSub = ""
    private var verdictColor = Theme.GOLD
    private var legend = ""
    /** 판정 문구가 나왔는지 */
    val hasVerdict: Boolean get() = verdict.isNotEmpty()

    // ── 배치 ──
    private val rowY = FloatArray(MAX_P)
    private var rowH = 0f
    private var dieS = 0f
    private var nameX = 0f
    private var counterX = 0f
    private var tableTop = 0f
    private val felt = RectF()

    // ── 그리기 도구 ──
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val bigOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF0A0F2E.toInt()
    }
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    /** 컵 아래 이름·남은 수 (칸 폭에 맞춰 줄임) */
    private val cupNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val cupCountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val verdictPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private var feltShader: Shader? = null

    // ── 다시 그리기 루프 (움직일 때만) ──
    private var looping = false
    private var frozen = false
    private val frame = Runnable {
        looping = false
        if (!frozen) {
            invalidate()
            if (animating()) kick()
        }
    }

    init {
        contentDescription = "주사위 탁자"
    }

    /** 플레이어 이름·색 (처음 한 번) */
    fun setup(playerNames: List<String>, playerColors: IntArray) {
        n = minOf(playerNames.size, MAX_P)
        for (p in 0 until n) {
            names[p] = playerNames[p]
            colors[p] = playerColors[p]
            lightColors[p] = Theme.shade(playerColors[p], 1.28f)
        }
        invalidate()
    }

    /** 각자 남은 주사위 수 */
    fun setCounts(c: IntArray) {
        for (p in 0 until n) {
            if (counts[p] != c[p] || countTexts[p].isEmpty()) countTexts[p] = if (c[p] > 0) "${c[p]}개" else "탈락"
            counts[p] = c[p]
        }
        fitCupLabels()
        invalidate()
    }

    fun setCurrent(p: Int) {
        if (current == p) return
        current = p
        invalidate()
    }

    /** 위쪽 금색 안내 줄 */
    fun setCaption(s: String) {
        if (caption == s) return
        caption = s
        fitCaption()
        invalidate()
    }

    /** CUPS: 컵을 보여 주고 shakeMs 동안 흔든다 (0 이면 가만히) */
    fun showCups(shakeMs: Long) {
        mode = Mode.CUPS
        verdict = ""
        verdictSub = ""
        val t = clock.now()
        shakeStart = t
        shakeEnd = t + shakeMs
        relayout()
        kick()
    }

    /** BID: 부른 수 판 (아직 없음) */
    fun showBid(total: Int, wild: Boolean) {
        mode = Mode.BID
        verdict = ""
        verdictSub = ""
        bidCount = 0
        bidFace = 0
        bidText = ""
        bidWho = "첫 사람이 불러요"
        totalText = "탁자 위 주사위 ${total}개"
        wildText = if (wild) "1 = 모든 눈 (와일드)" else "1 와일드 없음"
        relayout()
        invalidate()
    }

    /** 새 부른 수 (톡 튀어나옴) */
    fun setBid(count: Int, face: Int, who: String, popMs: Long) {
        bidCount = count
        bidFace = face
        bidText = "× $count"
        bidWho = "${who}님 · ${count}개 이상"
        bidDie.values[0] = face
        val t = clock.now()
        popStart = t
        popEnd = t + popMs
        relayout()
        kick()
    }

    /** REVEAL: 모든 주사위 공개 시작 (컵이 줄마다 열림) */
    fun startReveal(g: LiarDiceGame, r: DoubtResult, bidderName: String, doubterName: String, openMs: Long) {
        mode = Mode.REVEAL
        rows = 0
        matchTotal = 0
        counted = 0
        val face = r.bid.face
        for (p in 0 until g.playerCount) {
            if (r.shownDice[p] <= 0 || rows >= MAX_P) continue
            val row = rows++
            rowPlayer[row] = p
            val ds = sets[row]
            ds.cancel()
            ds.count = r.shownDice[p]
            for (k in 0 until DiceSet.MAX) {
                val v = if (k < r.shownDice[p]) g.dice[p][k] else 1
                ds.values[k] = v
                ds.held[k] = false
                val m = k < r.shownDice[p] && g.matches(v, face)
                match[row][k] = m
                wildHit[row][k] = m && v != face
            }
        }
        // 세는 순서: 줄 순서대로, 줄 안에서는 왼쪽부터
        for (row in 0 until rows) for (k in 0 until sets[row].count) if (match[row][k]) {
            orderRow[matchTotal] = row
            orderIdx[matchTotal] = k
            matchTotal++
        }
        bidDie.values[0] = face
        headText = "× ${r.bid.count}개 이상?"
        headSub = "${bidderName} 부름 → ${doubterName} 의심!"
        bidLine = "부른 수 ${r.bid.count}개"
        countLabel = "세어 보니"
        legend = if (g.wildOnes && face != 1) "하늘색 = 와일드 1" else ""
        verdict = ""
        verdictSub = ""
        val t = clock.now()
        openStart = t
        openStagger = if (rows > 1) openMs / (rows * 2) else 0L
        openPerRow = (openMs - openStagger * (rows - 1)).coerceAtLeast(1)
        dimStart = Long.MAX_VALUE
        popEnd = 0L
        relayout()
        kick()
    }

    /** 해당 눈이 아닌 주사위를 흐리게 */
    fun dimOthers(ms: Long) {
        dimStart = clock.now()
        dimMs = ms.coerceAtLeast(1)
        kick()
    }

    /** 하나 더 세기 (센 개수를 돌려준다). popMs 동안 그 주사위 테가 튐 */
    fun countNext(popMs: Long): Int {
        if (counted < matchTotal) counted++
        val t = clock.now()
        popStart = t
        popEnd = t + popMs
        kick()
        return counted
    }

    /** k 번째(0부터)로 센 주사위의 가운데 (이 View 좌표) */
    fun countedAnchor(k: Int, out: PointF) {
        if (k !in 0 until matchTotal) { out.set(width / 2f, height / 2f); return }
        val row = orderRow[k]
        out.set(sets[row].centerX(orderIdx[k]), rowY[row])
    }

    /** 판정 문구 */
    fun setVerdict(text: String, sub: String, good: Boolean) {
        verdict = text
        verdictSub = sub
        verdictColor = if (good) Theme.GREEN else Theme.RED
        fitRevealTexts()
        invalidate()
    }

    /** 일시정지: 다시 그리기 멈춤 */
    fun pause() {
        frozen = true
        removeCallbacks(frame)
        looping = false
    }

    fun resume() {
        frozen = false
        kick()
    }

    /** 공개 중인 줄 수 */
    val revealRows: Int get() = rows

    /** 공개 줄 row 의 플레이어 */
    fun rowPlayerAt(row: Int): Int = rowPlayer[row]

    /** 공개 줄 row 의 주사위 (DiceSet) */
    fun rowDice(row: Int): DiceSet = sets[row]

    private fun kick() {
        if (looping || frozen) return
        looping = true
        postOnAnimation(frame)
    }

    private fun animating(): Boolean {
        val t = clock.now()
        return when (mode) {
            Mode.CUPS -> t < shakeEnd
            Mode.BID -> t < popEnd
            Mode.REVEAL -> t < openStart + openPerRow + openStagger * rows || (dimStart != Long.MAX_VALUE && t < dimStart + dimMs) || t < popEnd
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        feltShader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF1F7A54.toInt(), 0xFF0F4A33.toInt(), Shader.TileMode.CLAMP)
        relayout()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        looping = false
        bidDie.cancel()
        for (s in sets) s.cancel()
        super.onDetachedFromWindow()
    }

    /** 크기·모드가 바뀔 때만: 위치·글자 크기 */
    private fun relayout() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        captionPaint.textSize = minOf(h * 0.06f, Theme.dpf(context, 16f))
        subPaint.textSize = minOf(h * 0.045f, Theme.dpf(context, 13f))
        midPaint.textSize = minOf(h * 0.055f, Theme.dpf(context, 15f))
        namePaint.textSize = minOf(h * 0.05f, Theme.dpf(context, 14f))
        felt.set(Theme.dpf(context, 4f), h * 0.12f, w - Theme.dpf(context, 4f), h - Theme.dpf(context, 4f))
        fitCaption()
        fitCupLabels()
        when (mode) {
            Mode.CUPS -> Unit
            Mode.BID -> {
                val s = minOf(h * 0.24f, w * 0.26f)
                bigPaint.textSize = s * 0.8f
                bigOutline.textSize = bigPaint.textSize
                bigOutline.strokeWidth = bigPaint.textSize * 0.12f
                val tw = if (bidText.isEmpty()) 0f else bigPaint.measureText(bidText)
                val gap = s * 0.25f
                val total = s + gap + tw
                bidDie.layoutRow(w / 2f - total / 2f + s / 2f, h * 0.34f, s, 0f)
                bigTextX = w / 2f - total / 2f + s + gap
            }
            Mode.REVEAL -> layoutReveal(w, h)
        }
    }

    private var bigTextX = 0f

    private fun layoutReveal(w: Float, h: Float) {
        tableTop = h * 0.2f
        felt.set(Theme.dpf(context, 2f), tableTop, w - Theme.dpf(context, 2f), h - Theme.dpf(context, 2f))
        val nameW = w * 0.18f
        val counterW = w * 0.25f
        nameX = felt.left + nameW / 2f
        counterX = felt.right - counterW / 2f
        val diceL = felt.left + nameW
        val diceR = felt.right - counterW
        val r = maxOf(rows, 1)
        rowH = felt.height() / r
        dieS = minOf(rowH * 0.6f, (diceR - diceL) / (DiceSet.MAX * 1.32f), Theme.dpf(context, 56f))
        for (row in 0 until rows) {
            rowY[row] = felt.top + rowH * (row + 0.5f)
            sets[row].layoutRow((diceL + diceR) / 2f, rowY[row], dieS, dieS * 0.32f)
        }
        val hs = minOf(h * 0.12f, Theme.dpf(context, 34f))
        bidDie.layoutRow(0f, h * 0.1f, hs, 0f) // x 는 그릴 때 글자 폭에 맞춘다 (fitRevealTexts)
        bigPaint.textSize = hs * 0.75f
        bigOutline.textSize = bigPaint.textSize
        bigOutline.strokeWidth = bigPaint.textSize * 0.12f
        numPaint.textSize = minOf(rowH * 0.9f, felt.height() * 0.3f, counterW * 0.55f)
        namePaint.textSize = minOf(h * 0.05f, Theme.dpf(context, 14f))
        for (row in 0 until rows) shrink(namePaint, names[rowPlayer[row]], nameW * 0.88f)
        fitRevealTexts()
    }

    /** 공개 화면 글자 크기 (setter·배치 때만) */
    private fun fitRevealTexts() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || mode != Mode.REVEAL) return
        val counterW = w * 0.25f
        verdictPaint.textSize = minOf(h * 0.075f, Theme.dpf(context, 20f))
        shrink(verdictPaint, verdict, counterW * 0.92f)
        subPaint.textSize = minOf(h * 0.045f, Theme.dpf(context, 13f))
        // 위 줄: [주사위] × N개 이상? — 가운데 정렬 (왼쪽 절반), 오른쪽에 누가 누구를
        val hs = bidDie.size
        val tw = bigPaint.measureText(headText)
        val total = hs + hs * 0.25f + tw
        val cx = w * 0.3f
        bidDie.layoutRow(cx - total / 2f + hs / 2f, h * 0.1f, hs, 0f)
        bigTextX = cx - total / 2f + hs * 1.25f
        midPaint.textSize = minOf(h * 0.055f, Theme.dpf(context, 15f))
        shrink(midPaint, headSub, w * 0.4f)
    }

    /** 컵 아래 글자 크기: 남은 사람 수 칸 폭에 이름·남은 수가 다 들어가게 (크기·인원이 바뀔 때만) */
    private fun fitCupLabels() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        var alive = 0
        for (p in 0 until n) if (counts[p] > 0) alive++
        val slot = (w - Theme.dpf(context, 12f)) / maxOf(alive, 1) * 0.92f
        cupNamePaint.textSize = minOf(h * 0.05f, Theme.dpf(context, 14f))
        cupCountPaint.textSize = minOf(h * 0.045f, Theme.dpf(context, 13f))
        for (p in 0 until n) {
            if (counts[p] <= 0) continue
            shrink(cupNamePaint, names[p], slot)
            shrink(cupCountPaint, countTexts[p], slot)
        }
    }

    private fun fitCaption() {
        val w = width.toFloat()
        if (w <= 0f) return
        captionPaint.textSize = minOf(height * 0.06f, Theme.dpf(context, 16f))
        shrink(captionPaint, caption, w * 0.94f)
    }

    private fun shrink(p: Paint, s: String, maxW: Float) {
        val min = Theme.dpf(context, 8f)
        while (s.isNotEmpty() && p.measureText(s) > maxW && p.textSize > min) p.textSize *= 0.92f
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val t = clock.now()
        val m = mode
        if (m === Mode.CUPS) drawCups(c, w, h, t)
        else if (m === Mode.BID) drawBid(c, w, h, t)
        else drawReveal(c, w, h, t)
    }

    /** 초록 펠트 탁자 */
    private fun drawFelt(c: Canvas) {
        val r = Theme.dpf(context, 16f)
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = feltShader
        c.drawRoundRect(felt, r, r, fill)
        fill.shader = null
        stroke.color = Theme.GOLD_DARK
        stroke.strokeWidth = Theme.dpf(context, 3f)
        c.drawRoundRect(felt, r, r, stroke)
        stroke.color = 0x40FFE08A
        stroke.strokeWidth = Theme.dpf(context, 1f)
        val i = Theme.dpf(context, 6f)
        c.drawRoundRect(felt.left + i, felt.top + i, felt.right - i, felt.bottom - i, r * 0.7f, r * 0.7f, stroke)
    }

    private fun drawCaption(c: Canvas, w: Float, h: Float) {
        if (caption.isNotEmpty()) c.drawText(caption, w / 2f, h * 0.075f, captionPaint)
    }

    // ───────── CUPS ─────────

    private fun drawCups(c: Canvas, w: Float, h: Float, t: Long) {
        drawFelt(c)
        drawCaption(c, w, h)
        var alive = 0
        for (p in 0 until n) if (counts[p] > 0) alive++
        if (alive == 0) return
        val slot = felt.width() / alive
        val cupW = minOf(slot * 0.62f, felt.height() * 0.42f)
        val cupH = cupW * 1.1f
        val base = felt.top + felt.height() * 0.62f
        val shaking = t < shakeEnd
        val prog = if (shaking) (t - shakeStart).toFloat() / (shakeEnd - shakeStart).coerceAtLeast(1) else 1f
        var k = 0
        for (p in 0 until n) {
            if (counts[p] <= 0) continue
            val cx = felt.left + slot * (k + 0.5f)
            var tilt = 0f
            var hop = 0f
            if (shaking) {
                val ph = (t - shakeStart) / 55f + k * 1.7f
                val amp = 1f - prog * 0.6f
                tilt = sin(ph) * 9f * amp
                hop = -kotlin.math.abs(sin(ph * 0.5f)) * cupH * 0.08f * amp
            }
            LiarDiceArt.drawCup(c, cx, base + hop, cupW, cupH, colors[p], art, path, tilt)
            cupNamePaint.color = lightColors[p]
            c.drawText(names[p], cx, base + cupNamePaint.textSize * 1.6f, cupNamePaint)
            cupCountPaint.color = Theme.TEXT_SUB
            c.drawText(countTexts[p], cx, base + cupNamePaint.textSize * 1.6f + cupCountPaint.textSize * 1.5f, cupCountPaint)
            k++
        }
    }

    // ───────── BID ─────────

    private fun drawBid(c: Canvas, w: Float, h: Float, t: Long) {
        // 위쪽: 부른 수 판 (짙은 카드)
        val r = Theme.dpf(context, 16f)
        val boardB = h * 0.6f
        fill.color = 0x33000000
        c.drawRoundRect(Theme.dpf(context, 2f), 0f, w - Theme.dpf(context, 2f), boardB, r, r, fill)
        stroke.color = 0x55FFC93C
        stroke.strokeWidth = Theme.dpf(context, 1.5f)
        c.drawRoundRect(Theme.dpf(context, 2f), 0f, w - Theme.dpf(context, 2f), boardB, r, r, stroke)
        subPaint.color = Theme.TEXT_SUB
        c.drawText(NOW_BID, w / 2f, h * 0.08f, subPaint)
        if (bidCount <= 0) {
            midPaint.color = Theme.TEXT_SUB
            c.drawText(NO_BID, w / 2f, h * 0.34f, midPaint)
            midPaint.color = Theme.TEXT
            c.drawText(bidWho, w / 2f, h * 0.5f, subPaint)
        } else {
            var sc = 1f
            if (t < popEnd) {
                val f = (t - popStart).toFloat() / (popEnd - popStart).coerceAtLeast(1)
                sc = 1f + 0.28f * sin(f * PI.toFloat())
            }
            c.save()
            c.scale(sc, sc, w / 2f, h * 0.34f)
            bidDie.draw(c)
            val ty = h * 0.34f + bigPaint.textSize * 0.36f
            c.drawText(bidText, bigTextX, ty, bigOutline)
            c.drawText(bidText, bigTextX, ty, bigPaint)
            c.restore()
            c.drawText(bidWho, w / 2f, h * 0.53f, midPaint)
        }
        // 아래: 각자 컵 + 남은 주사위
        var alive = 0
        for (p in 0 until n) if (counts[p] > 0) alive++
        if (alive > 0) {
            val top = h * 0.61f
            val slot = (w - Theme.dpf(context, 8f)) / alive
            val cupW = minOf(slot * 0.45f, h * 0.12f)
            val cupH = cupW * 1.05f
            val base = top + cupH
            var k = 0
            for (p in 0 until n) {
                if (counts[p] <= 0) continue
                val cx = Theme.dpf(context, 4f) + slot * (k + 0.5f)
                if (p == current) {
                    fill.color = 0x55FFC93C
                    c.drawOval(cx - cupW * 0.85f, base - cupH * 0.2f, cx + cupW * 0.85f, base + cupH * 0.22f, fill)
                }
                LiarDiceArt.drawCup(c, cx, base, cupW, cupH, colors[p], art, path)
                cupCountPaint.color = if (p == current) Theme.GOLD else lightColors[p]
                c.drawText(countTexts[p], cx, base + cupCountPaint.textSize * 1.45f, cupCountPaint)
                k++
            }
        }
        c.drawText(totalText, w / 2f, h - subPaint.textSize * 1.5f, subPaint)
        subPaint.color = LiarDiceArt.WILD
        c.drawText(wildText, w / 2f, h - subPaint.textSize * 0.25f, subPaint)
        subPaint.color = Theme.TEXT_SUB
    }

    // ───────── REVEAL ─────────

    private fun drawReveal(c: Canvas, w: Float, h: Float, t: Long) {
        // 위 줄: 부른 수 + 누가 누구를
        bidDie.draw(c)
        val ty = h * 0.1f + bigPaint.textSize * 0.36f
        c.drawText(headText, bigTextX, ty, bigOutline)
        c.drawText(headText, bigTextX, ty, bigPaint)
        midPaint.color = Theme.RED
        c.drawText(headSub, w * 0.78f, h * 0.1f + midPaint.textSize * 0.36f, midPaint)
        midPaint.color = Theme.TEXT
        drawFelt(c)
        // 오른쪽 세는 칸 구분선
        val nameW = w * 0.18f
        val counterW = w * 0.25f
        stroke.color = 0x40FFE08A
        stroke.strokeWidth = Theme.dpf(context, 1f)
        c.drawLine(felt.right - counterW, felt.top + Theme.dpf(context, 10f), felt.right - counterW, felt.bottom - Theme.dpf(context, 10f), stroke)
        val dimF = if (dimStart == Long.MAX_VALUE || t < dimStart) 0f else ((t - dimStart).toFloat() / dimMs).coerceIn(0f, 1f)
        val popF = if (t < popEnd) (t - popStart).toFloat() / (popEnd - popStart).coerceAtLeast(1) else 1f
        for (row in 0 until rows) {
            val p = rowPlayer[row]
            val y = rowY[row]
            // 줄 구분선
            if (row > 0) c.drawLine(felt.left + Theme.dpf(context, 10f), felt.top + rowH * row, felt.right - counterW - Theme.dpf(context, 6f), felt.top + rowH * row, stroke)
            // 이름
            namePaint.color = lightColors[p]
            c.drawText(names[p], felt.left + nameW / 2f, y + namePaint.textSize * 0.36f, namePaint)
            val f = ((t - openStart - openStagger * row).toFloat() / openPerRow).coerceIn(0f, 1f)
            val ds = sets[row]
            // 센 주사위 테 (주사위 뒤)
            if (counted > 0) for (k in 0 until counted) {
                if (orderRow[k] != row) continue
                val i = orderIdx[k]
                var s = dieS * 0.66f
                if (k == counted - 1 && popF < 1f) s *= 1f + 0.35f * sin(popF * PI.toFloat())
                fill.color = if (wildHit[row][i]) LiarDiceArt.WILD else Theme.GOLD
                c.drawRoundRect(ds.centerX(i) - s, y - s, ds.centerX(i) + s, y + s, s * 0.35f, s * 0.35f, fill)
            }
            ds.draw(c, (1f - f) * dieS * 0.3f, (255 * f).toInt())
            // 흐리게 (해당 눈이 아닌 주사위)
            if (dimF > 0f) {
                fill.color = Theme.BG_DEEP
                fill.alpha = (165 * dimF).toInt()
                val s = dieS * 0.56f
                for (i in 0 until ds.count) if (!match[row][i]) c.drawRoundRect(ds.centerX(i) - s, y - s, ds.centerX(i) + s, y + s, s * 0.35f, s * 0.35f, fill)
                fill.alpha = 255
            }
            // 컵이 들려 올라가며 사라짐
            if (f < 1f) {
                val cw = dieS * 2.2f
                val ch = minOf(rowH * 0.92f, cw * 1.0f)
                val cx = (ds.centerX(0) + ds.centerX(ds.count - 1)) / 2f
                val lift = f * rowH * 0.9f
                val save = c.saveLayerAlpha(felt.left, felt.top - rowH, felt.right, felt.bottom, (255 * (1f - f)).toInt())
                LiarDiceArt.drawCup(c, cx, y + ch * 0.5f - lift, cw, ch, colors[p], art, path)
                c.restoreToCount(save)
            }
        }
        // 오른쪽: 센 개수
        val cx = counterX
        subPaint.color = Theme.TEXT_SUB
        c.drawText(countLabel, cx, felt.top + felt.height() * 0.12f, subPaint)
        var ns = 1f
        if (popF < 1f) ns = 1f + 0.3f * sin(popF * PI.toFloat())
        c.save()
        val ny = felt.top + felt.height() * 0.38f
        c.scale(ns, ns, cx, ny)
        numPaint.color = if (verdict.isEmpty()) Theme.GOLD else verdictColor
        c.drawText(NUM[counted.coerceIn(0, NUM.size - 1)], cx, ny + numPaint.textSize * 0.36f, numPaint)
        c.restore()
        midPaint.color = Theme.TEXT
        c.drawText(bidLine, cx, felt.top + felt.height() * 0.6f, subPaint)
        if (verdict.isNotEmpty()) {
            verdictPaint.color = verdictColor
            c.drawText(verdict, cx, felt.top + felt.height() * 0.76f, verdictPaint)
            subPaint.color = Theme.TEXT
            c.drawText(verdictSub, cx, felt.top + felt.height() * 0.76f + verdictPaint.textSize * 1.25f, subPaint)
            subPaint.color = Theme.TEXT_SUB
        } else if (legend.isNotEmpty()) {
            subPaint.color = LiarDiceArt.WILD
            c.drawText(legend, cx, felt.top + felt.height() * 0.8f, subPaint)
            subPaint.color = Theme.TEXT_SUB
        }
    }

    companion object {
        private const val MAX_P = 4
        private const val NOW_BID = "지금 부른 수"
        private const val NO_BID = "아직 없음"
        private val NUM = Array(GameConfig.MAX_PLAYERS * GameConfig.START_DICE + 1) { it.toString() }
    }
}
