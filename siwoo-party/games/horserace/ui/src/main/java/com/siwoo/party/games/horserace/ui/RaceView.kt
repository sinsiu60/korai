package com.siwoo.party.games.horserace.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.dice.DiceSet
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.horserace.GameConfig
import com.siwoo.party.games.horserace.HorseRaceGame
import kotlin.math.PI

/**
 * 경주 화면 한 장 (Canvas).
 * - 위: "레이스 n / 5" + 굴린 횟수 (오른쪽 위 [빨리 보기] 버튼은 Activity 가 겹쳐 올림, 해설 배너는 fx 층)
 * - 왼쪽: 6개 레인 (번호 패 · 출발선 · 10칸 눈금 · 체크무늬 결승선 · 착순 메달 · 그 말에 건 사람 색 점)
 * - 오른쪽: 초록 주사위 판(공통 DiceSet, 1개) + 사람별 베팅 (번호 패 + 금액, 1·2착 말은 금테)
 * 말은 moveHorse 로 한 칸씩 달리고, 달리는 동안만 postOnAnimation 루프로 다시 그린다 (멈추면 루프 끝).
 * 시간은 clock(= 게임 시간, 일시정지 때 멈춤)을 쓴다. 그리기에서 객체를 만들지 않는다.
 */
class RaceView(context: Context) : View(context) {

    val dice = DiceSet(this).apply { count = 1 }
    /** 게임 시간 (Activity 가 timers.now 로 바꿔 끼움) */
    var clock: () -> Long = { android.os.SystemClock.uptimeMillis() }

    private val painter = HorsePainter()
    private val d = Theme.dpf(context, 1f)

    // ── 상태 ──
    private val shown = FloatArray(GameConfig.HORSES)
    private val fromPos = FloatArray(GameConfig.HORSES)
    private val toPos = FloatArray(GameConfig.HORSES)
    private val moveStart = LongArray(GameConfig.HORSES)
    private val moveDur = LongArray(GameConfig.HORSES)
    private val moving = BooleanArray(GameConfig.HORSES)
    private val phase = FloatArray(GameConfig.HORSES)
    private val place = IntArray(GameConfig.HORSES)
    private var playerCount = 0
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    /** bets[p][h] (레이스 시작 때 복사) */
    private var bets: Array<IntArray> = emptyArray()
    private var amountText: Array<Array<String>> = emptyArray()
    private var chipsText: Array<String> = emptyArray()
    /** 말별로 건 사람 (색 점) */
    private val bettors = Array(GameConfig.HORSES) { IntArray(MAX_BETTORS) }
    private val bettorCount = IntArray(GameConfig.HORSES)
    private var titleText = ""
    private var rollText = ""

    private var looping = false
    private var paused = false
    private val frame = Runnable { onFrame() }

    // ── 배치 ──
    private var topH = 0f
    private var laneL = 0f
    private var laneR = 0f
    private var laneT = 0f
    private var laneH = 0f
    private var labelW = 0f
    private var startX = 0f
    private var finishX = 0f
    private var cell = 0f
    private var horseS = 0f
    private var panelL = 0f
    private var panelR = 0f
    private val diceBox = RectF()
    private var rowsTop = 0f
    private var rowH = 0f

    private var commentL = 0f
    private var commentR = 0f
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB; textAlign = Paint.Align.CENTER }
    private val rollPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD; textAlign = Paint.Align.CENTER }

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD; textAlign = Paint.Align.RIGHT }
    private val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val medalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    /** 지금 달리는 말이 있는지 (테스트·Activity) */
    val anyMoving: Boolean get() = moving.any { it }

    /** 레이스 시작: 말을 출발선에, 베팅 정보 복사 (글자는 여기서 한 번만 만든다) */
    fun setup(game: HorseRaceGame, playerNames: List<String>, playerColors: IntArray) {
        playerCount = game.playerCount
        names = Array(playerCount) { playerNames[it] }
        colors = playerColors.copyOf()
        bets = Array(playerCount) { game.bets[it].copyOf() }
        amountText = Array(playerCount) { p -> Array(GameConfig.HORSES) { h -> bets[p][h].toString() } }
        chipsText = Array(playerCount) { game.chips[it].toString() + CHIPS }
        for (h in 0 until GameConfig.HORSES) {
            shown[h] = 0f; toPos[h] = 0f; fromPos[h] = 0f; moving[h] = false; phase[h] = 0f; place[h] = 0
            bettorCount[h] = 0
            for (p in 0 until playerCount) if (bets[p][h] > 0 && bettorCount[h] < MAX_BETTORS) bettors[h][bettorCount[h]++] = p
        }
        titleText = "레이스 " + game.raceNumber + " / " + GameConfig.RACES
        setRolls(0)
        dice.values[0] = 1
        dice.held[0] = false
        invalidate()
    }

    fun setRolls(n: Int) {
        rollText = n.toString()
        invalidate()
    }

    /** 정산 뒤 칩 글자 갱신 */
    fun setChips(chips: IntArray) {
        chipsText = Array(playerCount) { chips[it].toString() + CHIPS }
        invalidate()
    }

    /** 말 h 를 to 칸까지 durMs 동안 달리게 */
    fun moveHorse(h: Int, to: Int, durMs: Long) {
        fromPos[h] = shown[h]
        toPos[h] = to.toFloat()
        moveStart[h] = clock()
        moveDur[h] = durMs.coerceAtLeast(1)
        moving[h] = true
        startLoop()
    }

    /** 착순 표시 (1·2) */
    fun setPlace(h: Int, p: Int) {
        place[h] = p
        invalidate()
    }

    /** 말 h 의 지금 그려진 가운데 (이 View 좌표) — 별 연출용 */
    fun horseCenter(h: Int, out: android.graphics.PointF) {
        out.set(horseX(shown[h]), laneT + laneH * (h + 0.5f))
    }

    /** 해설 배너 자리 (위쪽 띠 가운데, 이 View 좌표)와 폭 */
    /**
     * 해설 배너 자리: 위쪽 띠의 제목과 [빨리 보기] 버튼 사이 가운데 (이 View 좌표).
     * 돌려주는 값 = 배너 최대 폭. 글자가 COMMENT_MAX_DP 보다 커지지 않도록 폭을 줄여 띠 높이에 맞춘다.
     */
    fun commentFit(text: String, out: android.graphics.PointF): Float {
        val avail = commentR - commentL
        measurePaint.textSize = d * COMMENT_MAX_DP
        val maxW = minOf(avail, measurePaint.measureText(text) / 0.9f + 1f)
        out.set((commentL + commentR) / 2f, topH * 0.5f + d * COMMENT_MAX_DP * 0.4f)
        return maxW
    }
    /** 위쪽 띠 높이 (빨리 보기 버튼 자리) */
    val topBarHeight: Float get() = topH
    val panelWidth: Float get() = panelR - panelL

    private fun horseX(pos: Float): Float = startX + pos * cell - horseS * 0.5f

    // ── 루프 ──

    private fun startLoop() {
        if (looping || paused) return
        looping = true
        postOnAnimation(frame)
    }

    private fun onFrame() {
        looping = false
        if (paused) return
        val now = clock()
        var any = false
        for (h in 0 until GameConfig.HORSES) {
            if (!moving[h]) continue
            val f = ((now - moveStart[h]).toFloat() / moveDur[h]).coerceIn(0f, 1f)
            // 살짝 빨라졌다 느려지는 걸음
            val e = f * f * (3f - 2f * f)
            shown[h] = fromPos[h] + (toPos[h] - fromPos[h]) * e
            phase[h] = f * TWO_PI * 1.5f
            if (f >= 1f) {
                moving[h] = false
                shown[h] = toPos[h]
                phase[h] = 0f
            } else any = true
        }
        invalidate()
        if (any) startLoop()
    }

    fun pause() {
        paused = true
        looping = false
        removeCallbacks(frame)
    }

    fun resume() {
        paused = false
        if (anyMoving) startLoop()
    }

    /** 레이스를 끝낼 때 (남은 이동을 끝 자리로) */
    fun stopAll() {
        for (h in 0 until GameConfig.HORSES) if (moving[h]) { moving[h] = false; shown[h] = toPos[h]; phase[h] = 0f }
        removeCallbacks(frame)
        looping = false
        dice.cancel()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        looping = false
        dice.cancel()
        super.onDetachedFromWindow()
    }

    // ── 배치 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = d * 6f
        topH = d * 42f
        val panelW = minOf(d * 172f, w * 0.3f)
        panelR = w - pad
        panelL = panelR - panelW
        laneL = pad
        laneR = panelL - d * 8f
        laneT = topH
        laneH = (h - pad - laneT) / GameConfig.HORSES
        labelW = minOf(d * 28f, laneH * 0.62f)
        horseS = minOf(laneH * 0.98f, d * 70f)
        startX = laneL + labelW + horseS * 1.05f
        finishX = laneR - d * 52f
        cell = (finishX - startX) / GameConfig.TRACK
        // 오른쪽: 주사위 판 + 사람별 베팅
        diceBox.set(panelL, laneT, panelR, laneT + minOf(d * 84f, h * 0.24f))
        dice.layoutRow(diceBox.centerX() + diceBox.width() * 0.16f, diceBox.centerY(), diceBox.height() * 0.62f, 0f)
        rowsTop = diceBox.bottom + d * 6f
        rowH = 0f
        titlePaint.textSize = d * 19f
        subPaint.textSize = d * 11f
        subPaint.textAlign = Paint.Align.CENTER
        rollPaint.textSize = d * 24f
        legendPaint.textSize = d * 12f
        // 해설 자리: 제목 오른쪽 ~ [빨리 보기] 왼쪽
        commentL = laneL + d * 4f + titlePaint.measureText(TITLE_SAMPLE) + d * 10f
        commentR = w - pad - d * FAST_BUTTON_DP - d * 8f
        namePaint.textSize = d * 13f
        chipPaint.textSize = d * 12.5f
        amountPaint.textSize = d * 11.5f
        medalPaint.textSize = d * 11f
        initPaint.textSize = d * 7.5f
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        if (laneH <= 0f) return
        // 위쪽 제목
        c.drawText(titleText, laneL + d * 4f, topH * 0.5f + titlePaint.textSize * 0.36f, titlePaint)
        drawTrack(c)
        for (h in 0 until GameConfig.HORSES) {
            val cy = laneT + laneH * (h + 0.5f) + horseS * 0.02f
            painter.draw(c, horseX(shown[h]), cy, horseS, h, phase[h], moving[h])
        }
        drawPanel(c)
    }

    private fun drawTrack(c: Canvas) {
        val bottom = laneT + laneH * GameConfig.HORSES
        // 잔디 바탕
        rect.set(laneL, laneT, laneR, bottom)
        fill.color = TURF_DARK
        c.drawRoundRect(rect, d * 12f, d * 12f, fill)
        for (h in 0 until GameConfig.HORSES) {
            val t = laneT + laneH * h
            fill.color = if (h % 2 == 0) TURF_A else TURF_B
            c.drawRect(laneL + labelW + d * 2f, t + d * 1f, laneR - d * 2f, t + laneH - d * 1f, fill)
            // 번호 패
            painter.badge(c, laneL + labelW * 0.55f, t + laneH * 0.5f, labelW * 0.78f, h)
            // 눈금
            fill.color = TICK
            for (k in 1 until GameConfig.TRACK) {
                c.drawCircle(startX + k * cell, t + laneH - d * 4f, d * 1.6f, fill)
            }
        }
        // 출발선
        fill.color = 0xCCFFFFFF.toInt()
        c.drawRect(startX - d * 1.5f, laneT + d * 2f, startX + d * 1.5f, bottom - d * 2f, fill)
        // 결승선 (체크무늬)
        val sq = d * 5f
        var y = laneT + d * 2f
        var k = 0
        while (y < bottom - d * 2f) {
            val y2 = minOf(y + sq, bottom - d * 2f)
            fill.color = if (k % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt()
            c.drawRect(finishX - sq, y, finishX, y2, fill)
            fill.color = if (k % 2 == 0) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
            c.drawRect(finishX, y, finishX + sq, y2, fill)
            y += sq
            k++
        }
        // 착순 메달 + 건 사람 점
        val ex = finishX + sq + d * 4f
        val ew = laneR - d * 4f - ex
        for (h in 0 until GameConfig.HORSES) {
            val cy = laneT + laneH * (h + 0.5f)
            if (place[h] > 0) {
                rect.set(ex, cy - d * 19f, ex + ew, cy - d * 3f)
                fill.color = if (place[h] == 1) Theme.GOLD else SILVER
                c.drawRoundRect(rect, d * 6f, d * 6f, fill)
                c.drawText(if (place[h] == 1) FIRST else SECOND, rect.centerX(), rect.centerY() + medalPaint.textSize * 0.36f, medalPaint)
            }
            val n = bettorCount[h]
            if (n > 0) {
                val r = d * 4.6f
                val gap = minOf(r * 2.4f, (ew - r * 2f) / maxOf(1, n - 1).toFloat())
                val x0 = ex + ew / 2f - gap * (n - 1) / 2f
                for (i in 0 until n) {
                    val p = bettors[h][i]
                    fill.color = colors[p]
                    c.drawCircle(x0 + gap * i, cy + d * 9f, r, fill)
                    stroke.color = 0xCCFFFFFF.toInt()
                    stroke.strokeWidth = d * 1f
                    c.drawCircle(x0 + gap * i, cy + d * 9f, r, stroke)
                }
            }
        }
    }

    private fun drawPanel(c: Canvas) {
        // 주사위 판
        fill.color = FELT
        c.drawRoundRect(diceBox, d * 12f, d * 12f, fill)
        stroke.color = Theme.GOLD_DARK
        stroke.strokeWidth = d * 2f
        c.drawRoundRect(diceBox, d * 12f, d * 12f, stroke)
        dice.draw(c)
        // 굴린 횟수 (주사위 왼쪽)
        val rx = diceBox.left + diceBox.width() * 0.2f
        c.drawText(ROLLS, rx, diceBox.centerY() - d * 10f, subPaint)
        c.drawText(rollText, rx, diceBox.centerY() + d * 18f, rollPaint)
        // 사람별 베팅
        if (playerCount == 0) return
        val bottom = height - d * 6f
        val rh = minOf((bottom - rowsTop) / playerCount, d * ROW_MAX_DP)
        val badge = d * 15f
        for (p in 0 until playerCount) {
            val t = rowsTop + rh * p
            rect.set(panelL, t + d * 2f, panelR, t + rh - d * 2f)
            fill.color = Theme.CARD
            c.drawRoundRect(rect, d * 10f, d * 10f, fill)
            stroke.color = colors[p]
            stroke.strokeWidth = d * 1.5f
            c.drawRoundRect(rect, d * 10f, d * 10f, stroke)
            val nameY = rect.top + d * 6f + namePaint.textSize * 0.9f
            fill.color = colors[p]
            c.drawCircle(rect.left + d * 12f, nameY - namePaint.textSize * 0.35f, d * 5.5f, fill)
            c.drawText(names[p], rect.left + d * 22f, nameY, namePaint)
            c.drawText(chipsText[p], rect.right - d * 8f, nameY, chipPaint)
            // 베팅 패
            var x = rect.left + d * 8f
            var y = nameY + d * 5f
            for (h in 0 until GameConfig.HORSES) {
                val a = bets[p][h]
                if (a <= 0) continue
                val tw = amountPaint.measureText(amountText[p][h])
                val w = badge + d * 3f + tw
                if (x + w > rect.right - d * 6f) {
                    x = rect.left + d * 8f
                    y += badge + d * 3f
                    if (y + badge > rect.bottom - d * 2f) break
                }
                painter.badge(c, x + badge / 2f, y + badge / 2f, badge, h)
                if (place[h] > 0) {
                    stroke.color = if (place[h] == 1) Theme.GOLD else SILVER
                    stroke.strokeWidth = d * 2f
                    rect.set(x - d * 2f, y - d * 2f, x + w + d * 2f, y + badge + d * 2f)
                    c.drawRoundRect(rect, d * 5f, d * 5f, stroke)
                    rect.set(panelL, t + d * 2f, panelR, t + rh - d * 2f)
                }
                amountPaint.color = if (place[h] == 1) Theme.GOLD else if (place[h] == 2) SILVER else Theme.TEXT
                c.drawText(amountText[p][h], x + badge + d * 3f, y + badge * 0.5f + amountPaint.textSize * 0.36f, amountPaint)
                x += w + d * 8f
            }
        }
        // 남는 자리에 배당 안내
        val lt = rowsTop + rh * playerCount + d * 4f
        if (bottom - lt >= d * 30f) {
            rect.set(panelL, lt, panelR, minOf(bottom, lt + d * 34f))
            fill.color = Theme.BG_DEEP
            c.drawRoundRect(rect, d * 10f, d * 10f, fill)
            c.drawText(LEGEND, rect.centerX(), rect.centerY() + legendPaint.textSize * 0.36f, legendPaint)
        }
    }

    companion object {
        /** 한 말에 표시할 수 있는 건 사람 수 (최대 인원) */
        private const val MAX_BETTORS = GameConfig.MAX_PLAYERS
        private const val CHIPS = "칩"
        private const val ROLLS = "굴림"
        private const val LEGEND = "1착 ×3 · 2착 ×1.5"
        private const val TITLE_SAMPLE = "레이스 5 / 5"
        /** 해설 글자 최대 크기 (위쪽 띠에 맞게) */
        private const val COMMENT_MAX_DP = 26f
        /** Activity 가 올리는 [빨리 보기] 버튼 폭 */
        const val FAST_BUTTON_DP = 150f
        private const val ROW_MAX_DP = 68f
        private const val FIRST = "1착"
        private const val SECOND = "2착"
        private const val TWO_PI = (PI * 2).toFloat()
        private const val TURF_A = 0xFF2F8A3E.toInt()
        private const val TURF_B = 0xFF2A7D38.toInt()
        private const val TURF_DARK = 0xFF1C5A28.toInt()
        private const val TICK = 0x66FFFFFF
        private const val FELT = 0xFF1F7A53.toInt()
        const val SILVER = 0xFFCFD8DC.toInt()
    }
}
