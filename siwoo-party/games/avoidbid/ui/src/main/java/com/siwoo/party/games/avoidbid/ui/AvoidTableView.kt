package com.siwoo.party.games.avoidbid.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.avoidbid.GameConfig
import com.siwoo.party.games.avoidbid.Scoring
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * 피하기 경매 탁자 (화면 전체 한 장).
 * - 왼쪽: 플레이어 줄 (인원 수만큼 세로로). 줄 왼쪽 머리 = 색 동그라미·이름·칩 더미·칩 수·점수,
 *   오른쪽 = 가져간 카드 (작은 순). 연속 숫자 묶음은 금색 틀로 묶고, 점수에 들어가는 가장 낮은 숫자만 금색 테두리,
 *   나머지는 흐리게 겹쳐 놓는다.
 * - 오른쪽 열: 위 = 지금 차례 이름 + 안내, 가운데 = 덱(남은 장수)과 공개 카드(위에 쌓인 칩), 아래 = 버튼 자리(액티비티가 올림).
 * - 연출(한 번에 하나): 덱 → 가운데로 뒤집으며 나오기 / 칩 1개가 카드 위로 날아가기 / 카드와 칩이 가져간 사람에게 날아가기.
 *   시계는 GameTimers.now() 라 일시정지하면 멈춘다. 연출 중일 때만 postOnAnimation 으로 다시 그린다.
 *   연출이 끝나도 마지막 위치에 그대로 그려 두고, 액티비티가 상태를 바꾸며 [endFlight] 를 부른다 (빈틈 없음).
 * onDraw 할당 없음: 글자는 setter 에서만 만든다.
 */
class AvoidTableView(context: Context) : View(context) {

    /** 한 사람 자리 */
    private class Seat {
        var name = ""
        var initial = ""
        var color = Theme.GOLD
        var initColor = Theme.TEXT
        var chips = 0
        var chipsText = ""
        var scoreText = ""
        var rankText = ""
        var finalText = ""
        var rank = 0
        val cards = IntArray(GameConfig.USED_CARDS)
        val heads = BooleanArray(GameConfig.USED_CARDS)
        /** 묶음 안(머리 아님)인지 */
        val inRun = BooleanArray(GameConfig.USED_CARDS)
        var count = 0
        /** 아직 날아오는 중이라 그리지 않을 카드 (-1 없음) */
        var hidden = -1
        // 배치
        val row = RectF()
        val area = RectF()
        val cl = FloatArray(GameConfig.USED_CARDS)
        val ct = FloatArray(GameConfig.USED_CARDS)
        var cw = 0f
        var ch = 0f
        /** 묶음 틀 (l,t,r,b × 최대 12개) */
        val br = FloatArray(GameConfig.USED_CARDS * 4)
        var brCount = 0
        var nameSize = 0f
        var nameX = 0f
        var nameY = 0f
        var circleX = 0f
        var circleY = 0f
        var circleR = 0f
        var stackX = 0f
        var stackBottom = 0f
        var coinR = 0f
        var textX = 0f
        var headerR = 0f
    }

    private var seats: Array<Seat> = emptyArray()
    private var n = 0
    private var current = -1

    /** 게임 시계 (일시정지되면 멈춤). 없으면 SystemClock */
    var clock: GameTimers? = null

    // ── 가운데 ──
    private var centerCard = -1
    private var pot = 0
    private var potText = ""
    private var deckCount = 0
    private var deckText = ""
    private var removedText = ""
    private var turnName = ""
    private var turnSuffix = ""
    private var turnColor = Theme.GOLD
    private var hint = ""
    private var hintWarn = false

    // ── 연출 ──
    private var flight = NONE
    private var fStart = 0L
    private var fDur = 1L
    private var fCard = -1
    private var fPlayer = 0
    private var fCoins = 0
    private var fx0 = 0f; private var fy0 = 0f; private var fw0 = 0f; private var fh0 = 0f
    private var fx1 = 0f; private var fy1 = 0f; private var fw1 = 0f; private var fh1 = 0f
    private var animPaused = false
    private var looping = false
    private val frame = Runnable { onFrame() }

    // ── 배치 (px) ──
    private val d = Theme.dpf(context, 1f)
    private val pad = d * 8f
    private val rcRect = RectF()
    private val headRect = RectF()
    private val cardRect = RectF()
    private val deckRect = RectF()
    private var potX = 0f
    private var potY = 0f
    private var potCoinR = 0f

    // ── 그리기 도구 ──
    private val tmp = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val turnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val centerText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66B8C3F0; textAlign = Paint.Align.CENTER }
    private val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private var turnNameW = 0f

    // ── 설정 ──

    /** 플레이어 이름·색 (처음 한 번) */
    fun setup(names: List<String>, colors: IntArray, removedCount: Int) {
        n = names.size
        seats = Array(n) { i ->
            Seat().apply {
                name = names[i]
                initial = names[i].take(1)
                color = colors[i]
                initColor = Theme.textOn(colors[i])
            }
        }
        removedText = "빠진 카드 " + removedCount + "장"
        relayout()
    }

    /** p 의 카드(아무 순서)·칩·점수. hidden = 날아오는 중이라 아직 그리지 않을 카드 */
    fun setPlayer(p: Int, cards: List<Int>, chips: Int, score: Int, hidden: Int = -1) {
        val s = seats[p]
        val sorted = cards.sorted()
        s.count = sorted.size
        for (k in sorted.indices) s.cards[k] = sorted[k]
        Scoring.markRunHeads(s.cards, s.count, s.heads)
        for (k in 0 until s.count) {
            val nextJoins = k + 1 < s.count && s.cards[k + 1] == s.cards[k] + 1
            s.inRun[k] = !s.heads[k] || nextJoins
        }
        s.hidden = hidden
        s.chips = chips
        s.chipsText = "칩 " + chips + "개"
        s.scoreText = "점수 " + score
        layoutCards(s)
        invalidate()
    }

    fun setCurrent(p: Int) {
        current = p
        invalidate()
    }

    /** 오른쪽 위: "이름 차례" + 안내 (warn = 빨간 안내) */
    fun setTurn(name: String, suffix: String, color: Int, hintText: String, warn: Boolean) {
        turnName = name
        turnSuffix = suffix
        turnColor = color
        hint = hintText
        hintWarn = warn
        fitTurn()
        invalidate()
    }

    /** 공개 카드(-1 없음)와 그 위 칩 */
    fun setCenter(card: Int, chips: Int) {
        centerCard = card
        pot = chips
        potText = "칩 " + chips + "개"
        invalidate()
    }

    fun setDeck(count: Int) {
        deckCount = count
        deckText = "남은 " + count + "장"
        invalidate()
    }

    /** 끝: 순위·점수 표시 (이름 동그라미 자리에 순위 메달, 오른쪽 열에 최종 점수표). null 이면 지움 */
    fun setFinal(ranks: IntArray?, scores: IntArray?) {
        ranked = ranks != null && scores != null
        for (i in seats.indices) {
            val s = seats[i]
            s.rank = if (ranked) ranks!![i] else 0
            s.rankText = if (ranked) ranks!![i].toString() + "등" else ""
            s.finalText = if (ranked) scores!![i].toString() + "점" else ""
        }
        if (ranked) {
            val order = seats.indices.sortedBy { seats[it].rank }
            for (k in order.indices) finalOrder[k] = order[k]
        }
        invalidate()
    }

    private var ranked = false
    private val finalOrder = IntArray(GameConfig.MAX_PLAYERS)

    // ── 연출 ──

    private fun now(): Long = clock?.now() ?: SystemClock.uptimeMillis()

    /** 덱 맨 위 카드가 가운데로 나오며 뒤집힘 */
    fun startReveal(card: Int, durMs: Long) {
        flight = REVEAL
        fCard = card
        fx0 = deckRect.left; fy0 = deckRect.top; fw0 = deckRect.width(); fh0 = deckRect.height()
        fx1 = cardRect.left; fy1 = cardRect.top; fw1 = cardRect.width(); fh1 = cardRect.height()
        begin(durMs)
    }

    /** p 의 칩 더미에서 칩 1개가 공개 카드 위로 */
    fun startCoin(p: Int, durMs: Long) {
        val s = seats[p]
        flight = COIN
        fPlayer = p
        fx0 = s.stackX; fy0 = stackTopY(s); fw0 = s.coinR
        fx1 = potX; fy1 = potTopY(pot + 1); fw1 = potCoinR
        begin(durMs)
    }

    /** 공개 카드와 칩 coins 개가 p 의 자리로 (setPlayer(hidden = card) 를 먼저 불러 둘 것) */
    fun startTake(p: Int, card: Int, coins: Int, durMs: Long) {
        val s = seats[p]
        flight = TAKE
        fPlayer = p
        fCard = card
        fCoins = coins
        fx0 = cardRect.left; fy0 = cardRect.top; fw0 = cardRect.width(); fh0 = cardRect.height()
        val k = indexOf(s, card)
        if (k >= 0) {
            fx1 = s.cl[k]; fy1 = s.ct[k]; fw1 = s.cw; fh1 = s.ch
        } else {
            fx1 = s.area.centerX(); fy1 = s.area.centerY(); fw1 = s.cw; fh1 = s.ch
        }
        begin(durMs)
    }

    /** 연출을 끝내고 지움 (상태는 액티비티가 setter 로 맞춤) */
    fun endFlight() {
        flight = NONE
        removeCallbacks(frame)
        looping = false
        invalidate()
    }

    val flying: Boolean get() = flight != NONE

    fun pauseAnim() {
        animPaused = true
        removeCallbacks(frame)
        looping = false
    }

    fun resumeAnim() {
        animPaused = false
        loop()
    }

    private fun begin(durMs: Long) {
        fStart = now()
        fDur = durMs.coerceAtLeast(1)
        loop()
        invalidate()
    }

    private fun loop() {
        if (looping || animPaused || flight == NONE) return
        looping = true
        postOnAnimation(frame)
    }

    private fun onFrame() {
        looping = false
        invalidate()
        if (flight != NONE && progress() < 1f) loop()
    }

    /** 반복 그리기가 돌고 있는지 (테스트용) */
    val animating: Boolean get() = looping

    private fun progress(): Float = ((now() - fStart).toFloat() / fDur).coerceIn(0f, 1f)

    // ── 좌표 도우미 (액티비티 연출용) ──

    /** p 자리에서 card 카드의 가운데 (없으면 카드 영역 가운데) */
    fun slotCenter(p: Int, card: Int, out: PointF) {
        val s = seats[p]
        val k = indexOf(s, card)
        if (k >= 0) out.set(s.cl[k] + s.cw / 2f, s.ct[k] + s.ch / 2f) else out.set(s.area.centerX(), s.area.centerY())
    }

    /** 공개 카드 가운데 */
    fun centerOf(out: PointF) = out.set(cardRect.centerX(), cardRect.centerY())

    /** 오른쪽 열 폭 (버튼 폭과 같게) */
    fun rightColumnWidth(w: Int): Int = rcWidth(w.toFloat()).toInt()

    fun cardCount(p: Int): Int = seats[p].count

    fun shownChips(p: Int): Int = seats[p].chips

    val shownCenter: Int get() = centerCard
    val shownPot: Int get() = pot

    /** 카드가 겹치지 않고 자기 줄 안에 들어가는지 (테스트용) */
    fun cardsInsideRow(p: Int): Boolean {
        val s = seats[p]
        for (k in 0 until s.count) {
            if (s.cl[k] < s.area.left - 0.5f || s.cl[k] + s.cw > s.area.right + 0.5f) return false
            if (s.ct[k] < s.area.top - 0.5f || s.ct[k] + s.ch > s.area.bottom + 0.5f) return false
        }
        return true
    }

    private fun indexOf(s: Seat, card: Int): Int {
        for (k in 0 until s.count) if (s.cards[k] == card) return k
        return -1
    }

    // ── 배치 ──

    private fun rcWidth(w: Float): Float = minOf(d * RIGHT_COLUMN_DP, w * 0.36f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || n == 0) return
        val rcW = rcWidth(w)
        rcRect.set(w - pad - rcW, pad, w - pad, h - pad)
        // 오른쪽 위 차례 판
        headRect.set(rcRect.left, rcRect.top, rcRect.right, rcRect.top + d * HEAD_DP)
        // 가운데 카드 영역 (아래는 버튼 자리)
        val areaT = headRect.bottom + d * 8f
        val areaB = h - pad - d * BUTTON_AREA_DP - d * 10f
        val areaH = areaB - areaT
        val ch = minOf(areaH - d * 12f, d * 150f).coerceAtLeast(d * 40f)
        val cw = ch * 0.7f
        val ccx = rcRect.left + rcW * 0.64f
        cardRect.set(ccx - cw / 2f, areaT + (areaH - ch) / 2f - d * 4f, ccx + cw / 2f, areaT + (areaH - ch) / 2f - d * 4f + ch)
        val dw = cw * 0.46f
        val dh = ch * 0.46f
        deckRect.set(rcRect.left + d * 8f, cardRect.top + d * 6f, rcRect.left + d * 8f + dw, cardRect.top + d * 6f + dh)
        potCoinR = cw * 0.12f
        potX = cardRect.centerX()
        potY = cardRect.bottom - ch * 0.1f
        centerText.textSize = d * 12f
        emptyPaint.textSize = d * 11f
        fitTurn()

        // 왼쪽 플레이어 줄
        val left = pad
        val right = rcRect.left - d * 8f
        val gap = d * 6f
        val rowH = (h - pad * 2f - gap * (n - 1)) / n
        for (i in 0 until n) {
            val s = seats[i]
            val top = pad + i * (rowH + gap)
            s.row.set(left, top, right, top + rowH)
            layoutHeader(s)
            layoutCards(s)
        }
        rankPaint.textSize = d * 13f
    }

    private fun layoutHeader(s: Seat) {
        val r = s.row
        val ip = d * 8f
        val hw = minOf(d * 124f, r.width() * 0.32f)
        s.headerR = r.left + hw
        val ns = minOf(d * 15f, r.height() * 0.2f)
        s.nameSize = ns
        val coinR = ((r.height() - ip * 2f - ns * 1.5f) / 4.9f).coerceIn(d * 4f, d * 9f)
        s.coinR = coinR
        val stackH = coinR * 0.45f * 2f + coinR * 0.32f * (STACK_MAX + 1)
        val block = ns * 1.5f + stackH
        val bt = r.top + (r.height() - block) / 2f
        s.circleR = ns * 0.62f
        s.circleX = r.left + ip + s.circleR
        s.circleY = bt + ns * 0.6f
        s.nameX = s.circleX + s.circleR + d * 6f
        s.nameY = bt + ns * 0.98f
        s.stackX = r.left + ip + coinR + d * 2f
        s.stackBottom = bt + block
        s.textX = s.stackX + coinR + d * 8f
        // 이름 글자 크기는 폭에 맞춤
        var size = ns
        namePaint.textSize = size
        val maxW = s.headerR - s.nameX - d * 4f
        while (namePaint.measureText(s.name) > maxW && size > ns * 0.55f) {
            size *= 0.92f
            namePaint.textSize = size
        }
        s.nameSize = size
        s.area.set(s.headerR + d * 8f, r.top + d * 7f, r.right - d * 9f, r.bottom - d * 7f)
    }

    /** 카드 배치: 묶음 안 카드는 겹치고(보폭 RUN_STRIDE), 묶음 사이는 띄움. 줄에 맞는 가장 큰 크기를 찾는다 */
    private fun layoutCards(s: Seat) {
        val a = s.area
        if (a.width() <= 0f) return
        var ch = minOf(a.height(), d * 66f)
        var guard = 0
        while (guard++ < 60) {
            if (tryLayout(s, ch * 0.72f, ch) || ch < d * 14f) break
            ch *= 0.94f
        }
    }

    private fun tryLayout(s: Seat, cw: Float, ch: Float): Boolean {
        val a = s.area
        val lineGap = d * 5f
        val stride = cw * RUN_STRIDE
        val groupGap = cw * 0.22f
        var x = 0f
        var y = 0f
        var lines = 1
        for (k in 0 until s.count) {
            if (k > 0) {
                if (s.heads[k]) {
                    x += cw + groupGap
                    // 묶음이 한 줄에 들어가면 쪼개지 않고 통째로 다음 줄로
                    var e = k
                    while (e + 1 < s.count && !s.heads[e + 1]) e++
                    val gw = cw + (e - k) * stride
                    if (x + gw > a.width() + 0.5f && gw <= a.width()) {
                        x = 0f
                        y += ch + lineGap
                        lines++
                    }
                } else x += stride
                if (x + cw > a.width() + 0.5f) {
                    x = 0f
                    y += ch + lineGap
                    lines++
                }
            }
            s.cl[k] = x
            s.ct[k] = y
        }
        val totalH = lines * ch + (lines - 1) * lineGap
        if (totalH > a.height() + 0.5f) return false
        s.cw = cw
        s.ch = ch
        // 줄 영역 안에서 세로 가운데
        val oy = a.top + (a.height() - totalH) / 2f
        for (k in 0 until s.count) {
            s.cl[k] += a.left
            s.ct[k] += oy
        }
        // 묶음 틀: 같은 묶음이면서 같은 줄인 연속 구간마다 하나
        s.brCount = 0
        var k = 0
        val m = d * 3f
        while (k < s.count) {
            var e = k
            while (e + 1 < s.count && !s.heads[e + 1] && s.ct[e + 1] == s.ct[k]) e++
            if ((e > k || s.inRun[k]) && s.brCount < GameConfig.USED_CARDS) {
                val b = s.brCount * 4
                s.br[b] = s.cl[k] - m
                s.br[b + 1] = s.ct[k] - m
                s.br[b + 2] = s.cl[e] + cw + m
                s.br[b + 3] = s.ct[k] + ch + m
                s.brCount++
            }
            k = e + 1
        }
        return true
    }

    private fun fitTurn() {
        if (headRect.width() <= 0f) return
        val maxW = headRect.width() - d * 20f
        var size = d * 19f
        turnPaint.textSize = size
        while (turnPaint.measureText(turnName) + turnPaint.measureText(turnSuffix) > maxW && size > d * 10f) {
            size *= 0.92f
            turnPaint.textSize = size
        }
        turnNameW = turnPaint.measureText(turnName)
        var hs = d * 12f
        hintPaint.textSize = hs
        while (hintPaint.measureText(hint) > maxW && hs > d * 8f) {
            hs *= 0.93f
            hintPaint.textSize = hs
        }
    }

    private fun stackTopY(s: Seat): Float {
        val shown = minOf(s.chips, STACK_MAX)
        return s.stackBottom - s.coinR * 0.45f - s.coinR * 0.32f * (shown + 1)
    }

    /** 칩 n 개가 쌓였을 때 맨 위 칩의 윗면 y (가운데 카드 위) */
    private fun potTopY(count: Int): Float {
        val inCol = minOf(count, POT_PER_STACK)
        return potY - potCoinR * 0.32f * inCol
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        if (n == 0 || rcRect.width() <= 0f) return
        val f = if (flight != NONE) progress() else 0f
        for (i in 0 until n) drawSeat(c, i, f)
        drawRight(c, f)
        when (flight) {
            REVEAL -> drawReveal(c, f)
            COIN -> drawCoinFlight(c, f)
            TAKE -> drawTakeFlight(c, f)
        }
    }

    private fun drawSeat(c: Canvas, i: Int, f: Float) {
        val s = seats[i]
        val r = s.row
        val rad = d * 12f
        val cur = i == current || s.rank == 1
        fill.color = if (cur) Theme.CARD_LIGHT else Theme.CARD
        fill.alpha = if (cur) 255 else 215
        c.drawRoundRect(r, rad, rad, fill)
        fill.alpha = 255
        stroke.strokeWidth = if (cur) d * 3f else d * 1.5f
        stroke.color = if (cur) Theme.GOLD else s.color
        stroke.alpha = if (cur) 255 else 150
        c.drawRoundRect(r, rad, rad, stroke)
        stroke.alpha = 255
        // 머리 칸 구분선
        fill.color = 0x22FFFFFF
        c.drawRect(s.headerR, r.top + d * 10f, s.headerR + d * 1.5f, r.bottom - d * 10f, fill)
        if (s.rank > 0) {
            // 끝: 순위 메달
            val mr = s.circleR * 1.2f
            fill.color = medal(s.rank)
            c.drawCircle(s.circleX, s.circleY, mr, fill)
            stroke.color = s.color
            stroke.strokeWidth = d * 2f
            c.drawCircle(s.circleX, s.circleY, mr, stroke)
            rankPaint.textSize = mr * 0.8f
            c.drawText(s.rankText, s.circleX, s.circleY + rankPaint.textSize * 0.36f, rankPaint)
        } else {
            // 색 동그라미 + 첫 글자
            fill.color = s.color
            c.drawCircle(s.circleX, s.circleY, s.circleR, fill)
            initPaint.color = s.initColor
            initPaint.textSize = s.circleR * 1.15f
            c.drawText(s.initial, s.circleX, s.circleY + initPaint.textSize * 0.36f, initPaint)
        }
        namePaint.textSize = s.nameSize
        namePaint.color = if (cur) Theme.GOLD_LIGHT else Theme.TEXT
        c.drawText(s.name, s.nameX, s.nameY, namePaint)
        // 칩 더미 (넘기는 중인 칩은 이미 빠져 있음)
        NumberArt.drawStack(c, s.stackX, s.stackBottom, s.coinR, minOf(s.chips, STACK_MAX), art)
        subPaint.textSize = s.nameSize * 0.82f
        scorePaint.textSize = s.nameSize * 1.0f
        val midY = s.stackBottom - (s.coinR * 0.45f * 2f + s.coinR * 0.32f * (STACK_MAX + 1)) / 2f
        c.drawText(s.chipsText, s.textX, midY - d * 2f, subPaint)
        c.drawText(s.scoreText, s.textX, midY + scorePaint.textSize + d * 1f, scorePaint)

        // 묶음 틀
        for (b in 0 until s.brCount) {
            val o = b * 4
            tmp.set(s.br[o], s.br[o + 1], s.br[o + 2], s.br[o + 3])
            fill.color = 0x40FFC93C
            c.drawRoundRect(tmp, s.cw * 0.2f, s.cw * 0.2f, fill)
            stroke.color = Theme.GOLD
            stroke.strokeWidth = d * 1.6f
            c.drawRoundRect(tmp, s.cw * 0.2f, s.cw * 0.2f, stroke)
        }
        // 카드: 오른쪽부터 그려서 묶음의 낮은 숫자가 위에 오게
        if (s.count == 0) {
            c.drawText(EMPTY, s.area.centerX(), s.area.centerY() + emptyPaint.textSize * 0.36f, emptyPaint)
        }
        for (k in s.count - 1 downTo 0) {
            val v = s.cards[k]
            if (v == s.hidden) continue
            val head = s.heads[k] && s.inRun[k]
            val dim = !s.heads[k]
            NumberArt.drawFace(c, s.cl[k], s.ct[k], s.cw, s.ch, v, art, dim = dim, head = head,
                numX = if (dim) 1f - RUN_STRIDE / 2f else 0.5f, shadow = !dim)
        }
    }

    private fun medal(rank: Int): Int = when (rank) {
        1 -> Theme.GOLD
        2 -> SILVER
        3 -> BRONZE
        else -> Theme.GRAY
    }

    private fun drawRight(c: Canvas, f: Float) {
        // 차례 판
        val rad = d * 12f
        fill.color = Theme.CARD
        c.drawRoundRect(headRect, rad, rad, fill)
        stroke.color = turnColor
        stroke.strokeWidth = d * 2f
        c.drawRoundRect(headRect, rad, rad, stroke)
        val tx = headRect.left + d * 10f
        val ty = headRect.top + d * 8f + turnPaint.textSize * 0.9f
        turnPaint.color = turnColor
        c.drawText(turnName, tx, ty, turnPaint)
        turnPaint.color = Theme.TEXT
        c.drawText(turnSuffix, tx + turnNameW, ty, turnPaint)
        hintPaint.color = if (hintWarn) Theme.RED else Theme.GOLD
        c.drawText(hint, tx, headRect.bottom - d * 9f, hintPaint)

        if (ranked) {
            drawFinal(c)
            return
        }
        // 덱 (뒷면 몇 장 겹침)
        val dw = deckRect.width()
        val dh = deckRect.height()
        val layers = if (deckCount >= 3) 3 else deckCount
        if (layers == 0) {
            stroke.color = 0x55FFFFFF
            stroke.strokeWidth = d * 1.5f
            c.drawRoundRect(deckRect, dw * 0.13f, dw * 0.13f, stroke)
        }
        for (k in layers - 1 downTo 0) {
            val off = k * d * 2.5f
            NumberArt.drawBack(c, deckRect.left + off, deckRect.top + off, dw, dh, art, shadow = k == layers - 1)
        }
        centerText.color = Theme.TEXT
        centerText.textSize = d * 12f
        c.drawText(deckText, deckRect.centerX() + d * 2f, deckRect.bottom + d * 22f, centerText)
        centerText.color = Theme.TEXT_SUB
        centerText.textSize = d * 10f
        c.drawText(removedText, deckRect.centerX() + d * 2f, deckRect.bottom + d * 36f, centerText)

        // 공개 카드 자리
        if (centerCard < 0 && flight != REVEAL) {
            stroke.color = 0x44FFFFFF
            stroke.strokeWidth = d * 1.5f
            c.drawRoundRect(cardRect, cardRect.width() * 0.13f, cardRect.width() * 0.13f, stroke)
        }
        if (centerCard >= 0) {
            NumberArt.drawFace(c, cardRect.left, cardRect.top, cardRect.width(), cardRect.height(), centerCard, art, detail = true)
            drawPot(c, pot)
        }
    }

    /** 끝: 오른쪽 열 최종 점수표 (순위 순) */
    private fun drawFinal(c: Canvas) {
        tmp.set(rcRect.left, headRect.bottom + d * 8f, rcRect.right, rcRect.bottom)
        val rad = d * 12f
        fill.color = Theme.CARD
        c.drawRoundRect(tmp, rad, rad, fill)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = d * 2f
        c.drawRoundRect(tmp, rad, rad, stroke)
        centerText.color = Theme.GOLD
        centerText.textSize = d * 15f
        c.drawText(FINAL_TITLE, tmp.centerX(), tmp.top + d * 26f, centerText)
        centerText.color = Theme.TEXT_SUB
        centerText.textSize = d * 10f
        c.drawText(FINAL_SUB, tmp.centerX(), tmp.top + d * 42f, centerText)
        val top = tmp.top + d * 54f
        val rowH = minOf(d * 40f, (tmp.bottom - d * 10f - top) / n)
        val l = tmp.left
        val r = tmp.right
        for (k in 0 until n) {
            val s = seats[finalOrder[k]]
            val cy = top + rowH * k + rowH / 2f
            val mr = minOf(rowH * 0.38f, d * 13f)
            fill.color = medal(s.rank)
            c.drawCircle(l + d * 12f + mr, cy, mr, fill)
            rankPaint.textSize = mr * 0.8f
            c.drawText(s.rankText, l + d * 12f + mr, cy + rankPaint.textSize * 0.36f, rankPaint)
            namePaint.textSize = minOf(s.nameSize, d * 14f)
            namePaint.color = s.color
            c.drawText(s.name, l + d * 20f + mr * 2f, cy + namePaint.textSize * 0.36f, namePaint)
            scorePaint.textSize = d * 15f
            scorePaint.textAlign = Paint.Align.RIGHT
            c.drawText(s.finalText, r - d * 12f, cy + scorePaint.textSize * 0.36f, scorePaint)
            scorePaint.textAlign = Paint.Align.LEFT
        }
    }

    /** 공개 카드 위 칩 더미 + 개수 띠 */
    private fun drawPot(c: Canvas, count: Int) {
        if (count > 0) {
            val stacks = (minOf(count, POT_MAX) + POT_PER_STACK - 1) / POT_PER_STACK
            val sp = potCoinR * 2.15f
            var left = count.coerceAtMost(POT_MAX)
            for (k in 0 until stacks) {
                val inThis = minOf(left, POT_PER_STACK)
                left -= inThis
                val x = potX + (k - (stacks - 1) / 2f) * sp
                NumberArt.drawStack(c, x, potY + potCoinR * 0.45f + potCoinR * 0.32f, potCoinR, inThis, art)
            }
        }
        // 띠: "칩 N개"
        centerText.textSize = d * 12f
        val pw = centerText.measureText(potText) + d * 16f
        val ph = d * 20f
        tmp.set(cardRect.centerX() - pw / 2f, cardRect.bottom - ph / 2f + d * 4f, cardRect.centerX() + pw / 2f, cardRect.bottom + ph / 2f + d * 4f)
        fill.color = if (count > 0) Theme.GOLD else Theme.CARD_LIGHT
        c.drawRoundRect(tmp, ph / 2f, ph / 2f, fill)
        stroke.color = Theme.INK
        stroke.strokeWidth = d * 1.2f
        c.drawRoundRect(tmp, ph / 2f, ph / 2f, stroke)
        centerText.color = if (count > 0) Theme.INK else Theme.TEXT_SUB
        c.drawText(potText, tmp.centerX(), tmp.centerY() + centerText.textSize * 0.36f, centerText)
    }

    private fun ease(f: Float): Float = f * f * (3f - 2f * f)

    private fun drawReveal(c: Canvas, f: Float) {
        val e = ease(f)
        val w = fw0 + (fw1 - fw0) * e
        val h = fh0 + (fh1 - fh0) * e
        val cx = fx0 + fw0 / 2f + (fx1 + fw1 / 2f - fx0 - fw0 / 2f) * e
        val cy = fy0 + fh0 / 2f + (fy1 + fh1 / 2f - fy0 - fh0 / 2f) * e - h * 0.18f * (4f * e * (1f - e))
        val squash = abs(cos(f * PI.toFloat()))
        c.save()
        c.scale(squash.coerceAtLeast(0.02f), 1f, cx, cy)
        if (f < 0.5f) NumberArt.drawBack(c, cx - w / 2f, cy - h / 2f, w, h, art)
        else NumberArt.drawFace(c, cx - w / 2f, cy - h / 2f, w, h, fCard, art, detail = true)
        c.restore()
    }

    private fun drawCoinFlight(c: Canvas, f: Float) {
        val e = ease(f)
        val a = 1f - e
        val mx = (fx0 + fx1) / 2f
        val my = minOf(fy0, fy1) - d * 50f
        val x = a * a * fx0 + 2f * a * e * mx + e * e * fx1
        val y = a * a * fy0 + 2f * a * e * my + e * e * fy1
        val rx = fw0 + (fw1 - fw0) * e
        NumberArt.drawCoin(c, x, y, rx, art)
    }

    private fun drawTakeFlight(c: Canvas, f: Float) {
        val s = seats[fPlayer]
        // 칩들: 공개 카드 → 칩 더미 (조금씩 늦게 출발)
        val shown = minOf(fCoins, FLY_COINS_MAX)
        val tx = s.stackX
        val ty = stackTopY(s)
        for (k in 0 until shown) {
            val delay = if (shown > 1) 0.3f * k / (shown - 1) else 0f
            val fk = ((f - delay) / 0.7f).coerceIn(0f, 1f)
            if (fk >= 1f) continue
            val e = ease(fk)
            val a = 1f - e
            val x0 = potX
            val y0 = potTopY(maxOf(1, shown - k))
            val mx = (x0 + tx) / 2f
            val my = minOf(y0, ty) - d * 40f
            NumberArt.drawCoin(c, a * a * x0 + 2f * a * e * mx + e * e * tx, a * a * y0 + 2f * a * e * my + e * e * ty,
                potCoinR + (s.coinR - potCoinR) * e, art)
        }
        // 카드: 가운데 → 자리 (살짝 기울며)
        val e = ease(f)
        val w = fw0 + (fw1 - fw0) * e
        val h = fh0 + (fh1 - fh0) * e
        val cx = fx0 + fw0 / 2f + (fx1 + fw1 / 2f - fx0 - fw0 / 2f) * e
        val cy = fy0 + fh0 / 2f + (fy1 + fh1 / 2f - fy0 - fh0 / 2f) * e
        c.save()
        c.rotate(-16f * (4f * e * (1f - e)), cx, cy)
        NumberArt.drawFace(c, cx - w / 2f, cy - h / 2f, w, h, fCard, art, detail = w > d * 60f)
        c.restore()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        looping = false
        super.onDetachedFromWindow()
    }

    companion object {
        /** 오른쪽 열 폭 (dp) */
        const val RIGHT_COLUMN_DP = 200f
        /** 오른쪽 위 차례 판 높이 (dp) */
        const val HEAD_DP = 52f
        /** 버튼 한 개 높이·간격 (dp) — 액티비티가 같은 값으로 버튼을 놓는다 */
        const val BUTTON_DP = 44f
        const val BUTTON_GAP_DP = 6f
        const val BUTTON_AREA_DP = BUTTON_DP * 2 + BUTTON_GAP_DP

        /** 묶음 안 카드 보폭 (카드 폭 비율) */
        private const val RUN_STRIDE = 0.66f
        /** 머리 칸 칩 더미에 그리는 최대 개수 (그 이상은 숫자로) */
        private const val STACK_MAX = 11
        /** 공개 카드 위 칩: 한 더미 5개, 최대 15개까지 그림 */
        private const val POT_PER_STACK = 5
        private const val POT_MAX = 15
        /** 가져갈 때 날아가는 칩 그림 최대 개수 */
        private const val FLY_COINS_MAX = 8

        private const val NONE = 0
        private const val REVEAL = 1
        private const val COIN = 2
        private const val TAKE = 3

        private const val EMPTY = "아직 가져간 카드 없음"
        private const val FINAL_TITLE = "최종 점수"
        private const val FINAL_SUB = "낮을수록 좋아요"
        private const val SILVER = 0xFFC9D3E8.toInt()
        private const val BRONZE = 0xFFD9955A.toInt()
    }
}
