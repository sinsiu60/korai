package com.siwoo.party.games.updown.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.updown.GameConfig

/**
 * 폭탄 숫자 업다운의 범위 막대: 1~100 을 가로 막대로, 지금 열린 범위만 주황 막대로 빛난다.
 * 위: 안내 + 큰 범위 글자("24 ~ 66") · 판정("DOWN ▼"/"UP ▲") / 가운데: 열린 범위 위에 "?" 폭탄 · 막대 · 눈금 / 아래: 마지막 추측 한 줄.
 * 연출: 잘린 조각이 위쪽(큰 수)은 오른쪽 위로, 아래쪽(작은 수)은 왼쪽 아래로 돌며 날아감 · 폭발(번쩍·파편·고리) 뒤 폭탄 숫자 공개.
 * 문자열·Shader 는 값·크기가 바뀔 때만 만든다 → onDraw 할당 없음. 연출 중일 때만 다시 그린다.
 */
class RangeBarView(context: Context) : View(context) {

    /** 애니메이션 시계 (게임 시간 — 일시정지 때 멈춤) */
    fun interface Clock { fun now(): Long }

    var clock: Clock = Clock { SystemClock.uptimeMillis() }

    /** 잘린 값 (열린 범위 = low+1 ~ high-1) */
    var low = GameConfig.MIN_NUMBER - 1
        private set
    var high = GameConfig.MAX_NUMBER + 1
        private set

    /** 큰 범위 글자 (테스트용) */
    var rangeText = ""
        private set
    var verdictText = ""
        private set
    var caption = ""
        private set
    /** 공개된 폭탄 (-1 = 숨김) */
    var revealedBomb = -1
        private set

    private var headText = ""
    private var countText = ""
    private var lowLabel = ""
    private var highLabel = ""
    private var verdictSub = ""
    private var verdictColor = Theme.GOLD
    private var verdictStart = -1L
    private var bombLabel = ""
    private var forced = false

    // 잘린 조각
    private var pieceFrom = 0
    private var pieceTo = -1
    private var pieceUp = true
    private var pieceStart = -1L
    private var pieceMs = 1L

    // 폭발
    private var boomStart = -1L
    private var boomMs = 1L

    // 배치
    private var barL = 0f
    private var barR = 0f
    private var barT = 0f
    private var barB = 0f
    private var cell = 0f
    private var bombY = 0f
    private var headY = 0f
    private var rangeY = 0f
    private var countY = 0f
    private var verdictY = 0f
    private var verdictSubY = 0f
    private var bombR = 0f
    private val tmp = RectF()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fuse = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD
        setShadowLayer(4f, 0f, 3f, 0x99000000.toInt())
    }
    private val verdictPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 3f, 0x99000000.toInt())
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD_LIGHT }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }

    init {
        setRange(low, high)
    }

    /** 범위 바꾸기 (잘린 값). 연출 없이 바로 */
    fun setRange(lowCut: Int, highCut: Int) {
        low = lowCut
        high = highCut
        val lo = low + 1
        val hi = high - 1
        forced = lo == hi
        rangeText = if (forced) "$lo" else "$lo ~ $hi"
        val n = hi - lo + 1
        countText = if (forced) "남은 숫자는 폭탄뿐!" else "남은 숫자 ${n}개"
        lowLabel = "$lo"
        highLabel = "$hi"
        invalidate()
    }

    /** 새 라운드: 범위·판정·공개 초기화 */
    fun reset(roundTitle: String) {
        headText = roundTitle
        verdictText = ""
        verdictSub = ""
        verdictStart = -1
        pieceTo = -1
        pieceStart = -1
        boomStart = -1
        revealedBomb = -1
        bombLabel = ""
        caption = ""
        setRange(GameConfig.MIN_NUMBER - 1, GameConfig.MAX_NUMBER + 1)
    }

    fun setCaption(s: String) {
        caption = s
        invalidate()
    }

    /**
     * 추측 결과 연출: value 로 잘림.
     * @param down true = 폭탄보다 커서 위쪽(value ~ 이전 high-1)이 잘림, false = 아래쪽(이전 low+1 ~ value)
     */
    fun cut(value: Int, down: Boolean, newLow: Int, newHigh: Int, ms: Long) {
        val oldLow = low
        val oldHigh = high
        if (down) { pieceFrom = value; pieceTo = oldHigh - 1 } else { pieceFrom = oldLow + 1; pieceTo = value }
        pieceUp = down
        pieceStart = clock.now()
        pieceMs = ms.coerceAtLeast(1)
        verdictText = if (down) "DOWN ▼" else "UP ▲"
        verdictSub = if (down) "폭탄은 ${value}보다 작아요" else "폭탄은 ${value}보다 커요"
        verdictColor = if (down) UpDownArt.DOWN else UpDownArt.UP
        verdictStart = pieceStart
        setRange(newLow, newHigh)
    }

    /** 폭발 연출 + 폭탄 공개 */
    fun boom(bomb: Int, ms: Long) {
        revealedBomb = bomb
        bombLabel = "$bomb"
        boomStart = clock.now()
        boomMs = ms.coerceAtLeast(1)
        verdictText = "펑!!"
        verdictSub = "폭탄 숫자는 $bomb"
        verdictColor = Theme.RED
        verdictStart = boomStart
        pieceTo = -1
        invalidate()
    }

    /** 숫자 v 칸의 가운데 x */
    fun xOf(v: Int): Float = barL + (v - GameConfig.MIN_NUMBER + 0.5f) * cell

    /** 폭탄 그림 자리 (연출용, 이 View 좌표) */
    fun bombAnchor(out: android.graphics.PointF) {
        val v = if (revealedBomb > 0) revealedBomb else (low + high) / 2
        out.set(xOf(v), (barT + barB) / 2f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = Theme.dpf(context, 18f)
        barL = pad
        barR = w - pad
        cell = (barR - barL) / (GameConfig.MAX_NUMBER - GameConfig.MIN_NUMBER + 1)
        val hh = h.toFloat()
        headPaint.textSize = minOf(Theme.dpf(context, 12f), hh * 0.045f)
        rangePaint.textSize = minOf(Theme.dpf(context, 38f), hh * 0.135f)
        verdictPaint.textSize = minOf(Theme.dpf(context, 24f), hh * 0.085f)
        subPaint.textSize = minOf(Theme.dpf(context, 12f), hh * 0.045f)
        edgePaint.textSize = minOf(Theme.dpf(context, 13f), hh * 0.046f)
        tickPaint.textSize = minOf(Theme.dpf(context, 10f), hh * 0.036f)
        captionPaint.textSize = minOf(Theme.dpf(context, 13f), hh * 0.048f)
        // 세로 배치 (비율): 안내 0.07 · 범위 0.21 · 남은 개수 0.28 · 판정 0.39 · 판정 설명 0.46 · 폭탄 0.565 · 막대 0.68~0.78 · 눈금 · 마지막 추측
        headY = hh * 0.07f
        rangeY = hh * 0.21f
        countY = hh * 0.28f
        verdictY = hh * 0.39f
        verdictSubY = hh * 0.46f
        barT = hh * 0.68f
        barB = barT + minOf(Theme.dpf(context, 28f), hh * 0.1f)
        bombR = minOf(Theme.dpf(context, 18f), hh * 0.055f)
        bombY = hh * 0.565f
        fuse.shader = LinearGradient(0f, barT, 0f, barB, UpDownArt.FUSE_B, UpDownArt.FUSE_A, Shader.TileMode.CLAMP)
        dash.strokeWidth = Theme.dpf(context, 1.2f)
        dash.pathEffect = DashPathEffect(floatArrayOf(Theme.dpf(context, 4f), Theme.dpf(context, 4f)), 0f)
        dash.color = 0x55FFFFFF
    }

    override fun onDraw(c: Canvas) {
        if (width <= 0) return
        val now = clock.now()
        var animating = false
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        // ── 위: 라운드 안내 · 큰 범위 · 남은 개수 ──
        c.drawText(headText, cx, headY, headPaint)
        rangePaint.color = if (forced) Theme.RED else Theme.GOLD
        c.drawText(rangeText, cx, rangeY, rangePaint)
        subPaint.color = if (forced) Theme.RED else Theme.TEXT_SUB
        c.drawText(countText, cx, countY, subPaint)
        // ── 판정 ──
        if (verdictText.isNotEmpty()) {
            var k = 1f
            if (verdictStart >= 0) {
                val e = now - verdictStart
                val d = 320L
                if (e < d) { k = e.toFloat() / d; animating = true }
            }
            val vy = verdictY
            c.save()
            val sc = if (k < 1f) 1.6f - 0.6f * k else 1f
            c.scale(sc, sc, cx, vy)
            verdictPaint.color = verdictColor
            verdictPaint.alpha = (255 * k.coerceIn(0.2f, 1f)).toInt()
            c.drawText(verdictText, cx, vy, verdictPaint)
            c.restore()
        }
        // ── 막대: 전체 칸(잘린 곳) ──
        val r = (barB - barT) / 2f
        fill.color = UpDownArt.CUT
        c.drawRoundRect(barL, barT, barR, barB, r, r, fill)
        c.drawRoundRect(barL, barT, barR, barB, r, r, dash)
        // 눈금
        line.color = 0x44FFFFFF
        line.strokeWidth = Theme.dpf(context, 1f)
        for (i in TICKS.indices) {
            val v = TICKS[i]
            val x = if (v == GameConfig.MIN_NUMBER) barL else barL + (v - GameConfig.MIN_NUMBER + 1) * cell
            c.drawLine(x, barB + Theme.dpf(context, 2f), x, barB + Theme.dpf(context, 6f), line)
            tickPaint.textAlign = if (i == 0) Paint.Align.LEFT else if (i == TICKS.size - 1) Paint.Align.RIGHT else Paint.Align.CENTER
            c.drawText(TICK_TEXT[i], x, barB + Theme.dpf(context, 6f) + tickPaint.textSize * 1.05f, tickPaint)
        }
        // 열린 범위 (주황 막대)
        val lo = low + 1
        val hi = high - 1
        val ol = barL + (lo - GameConfig.MIN_NUMBER) * cell
        val orr = barL + (hi - GameConfig.MIN_NUMBER + 1) * cell
        val boomOn = revealedBomb > 0
        if (!boomOn) {
            val minW = Theme.dpf(context, 8f)
            val mid = (ol + orr) / 2f
            val l = minOf(ol, mid - minW / 2f)
            val rr = maxOf(orr, mid + minW / 2f)
            fill.color = 0x55FF8A2A
            c.drawRoundRect(l - 3f, barT - 3f, rr + 3f, barB + 3f, r + 3f, r + 3f, fill)
            c.drawRoundRect(l, barT, rr, barB, r, r, fuse)
            fill.color = 0x55FFFFFF
            c.drawRoundRect(l + r * 0.4f, barT + (barB - barT) * 0.14f, rr - r * 0.4f, barT + (barB - barT) * 0.38f, r * 0.3f, r * 0.3f, fill)
            // 양 끝 숫자 (막대 위, 바깥쪽 정렬)
            val ey = barT - edgePaint.textSize * 0.35f
            if (forced) {
                edgePaint.textAlign = Paint.Align.CENTER
                edgePaint.color = Theme.RED
                c.drawText(lowLabel, mid, ey, edgePaint)
            } else {
                edgePaint.color = Theme.GOLD_LIGHT
                edgePaint.textAlign = Paint.Align.LEFT
                val lw = edgePaint.measureText(lowLabel)
                val hw = edgePaint.measureText(highLabel)
                val gap = Theme.dpf(context, 6f)
                var lx = ol
                var hx = orr - hw
                if (hx < lx + lw + gap) { // 좁으면 바깥으로 밀기
                    lx = mid - gap / 2f - lw
                    hx = mid + gap / 2f
                }
                c.drawText(lowLabel, lx, ey, edgePaint)
                c.drawText(highLabel, hx, ey, edgePaint)
            }
            // 숨은 폭탄 "?"
            UpDownArt.bomb(c, mid, bombY, bombR, art, if (forced) EXCL else QUESTION, if (forced) Theme.RED else Theme.GOLD)
        }
        // 잘린 조각 날아가기
        if (pieceTo >= pieceFrom && pieceStart >= 0) {
            val e = now - pieceStart
            if (e < pieceMs) {
                animating = true
                val k = e.toFloat() / pieceMs
                val ease = k * k
                val pl = barL + (pieceFrom - GameConfig.MIN_NUMBER) * cell
                val pr = barL + (pieceTo - GameConfig.MIN_NUMBER + 1) * cell
                val pcx = (pl + pr) / 2f
                val pcy = (barT + barB) / 2f
                c.save()
                if (pieceUp) {
                    c.translate(w * 0.12f * ease, -h * 0.55f * ease)
                    c.rotate(28f * k, pcx, pcy)
                } else {
                    c.translate(-w * 0.12f * ease, h * 0.4f * ease)
                    c.rotate(-28f * k, pcx, pcy)
                }
                fill.color = if (pieceUp) UpDownArt.DOWN else UpDownArt.UP
                fill.alpha = (255 * (1f - k)).toInt()
                c.drawRoundRect(pl, barT, pr, barB, r, r, fill)
                fill.alpha = 255
                c.restore()
                // 자른 자리 번쩍
                val cutX = if (pieceUp) pl else pr
                line.color = Theme.TEXT
                line.alpha = (255 * (1f - k)).toInt()
                line.strokeWidth = Theme.dpf(context, 3f)
                c.drawLine(cutX, barT - (barB - barT) * 0.5f, cutX, barB + (barB - barT) * 0.5f, line)
                line.alpha = 255
            }
        }
        // 폭발
        if (boomOn) {
            val bx = xOf(revealedBomb)
            val by = (barT + barB) / 2f
            var k = 1f
            if (boomStart >= 0) {
                val e = now - boomStart
                if (e < boomMs) { k = e.toFloat() / boomMs; animating = true }
            }
            if (k < 1f) {
                val big = h * 0.45f
                fill.color = 0xFFFFE08A.toInt()
                fill.alpha = (200 * (1f - k)).toInt()
                c.drawCircle(bx, by, big * (0.2f + 0.8f * k), fill)
                fill.color = 0xFFFF6A00.toInt()
                fill.alpha = (230 * (1f - k)).toInt()
                c.drawCircle(bx, by, big * 0.6f * (0.2f + 0.8f * k), fill)
                fill.alpha = 255
                line.color = Theme.GOLD
                line.strokeWidth = Theme.dpf(context, 3f)
                line.alpha = (255 * (1f - k)).toInt()
                for (i in 0 until SHARD_COS.size) {
                    val r0 = big * 0.3f * k + bombR
                    val r1 = r0 + big * 0.35f * (1f - k * 0.5f)
                    c.drawLine(bx + SHARD_COS[i] * r0, by + SHARD_SIN[i] * r0, bx + SHARD_COS[i] * r1, by + SHARD_SIN[i] * r1, line)
                }
                line.alpha = 255
            }
            // 폭탄 공개 (막대 위 자리)
            val s = if (k < 1f) 0.6f + 0.4f * k else 1f
            UpDownArt.bomb(c, bx, by - (barB - barT) * 0.1f, bombR * 1.6f * s, art, bombLabel, Theme.GOLD)
        }
        // 판정 설명 (막대 위)
        if (verdictSub.isNotEmpty()) {
            subPaint.color = verdictColor
            c.drawText(verdictSub, cx, verdictSubY, subPaint)
        }
        // ── 아래: 마지막 추측 ──
        if (caption.isNotEmpty()) c.drawText(caption, cx, h - captionPaint.textSize * 0.7f, captionPaint)
        if (animating) postInvalidateOnAnimation()
    }

    companion object {
        private val TICKS = intArrayOf(1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        private val TICK_TEXT = Array(TICKS.size) { TICKS[it].toString() }
        private val SHARD_COS = FloatArray(10) { Math.cos(Math.PI * 2 * it / 10 + 0.3).toFloat() }
        private val SHARD_SIN = FloatArray(10) { Math.sin(Math.PI * 2 * it / 10 + 0.3).toFloat() }
        private const val QUESTION = "?"
        private const val EXCL = "!"
    }
}
