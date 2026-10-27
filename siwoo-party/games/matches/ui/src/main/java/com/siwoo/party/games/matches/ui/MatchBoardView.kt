package com.siwoo.party.games.matches.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.matches.GameConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 성냥 판 (Canvas 하나로 그린다).
 * - 위 띠: 왼쪽 "남은 성냥" 큰 숫자, 오른쪽 "라운드 n / N" + 라운드 칸 (끝난 라운드는 진 사람 색·첫 글자·해골)
 * - 가운데 쟁반: 성냥 21개 (7개씩 3줄, 조금씩 기울어진 나무 막대 + 빨간 머리). 고르면 살짝 들리며 금빛,
 *   더 못 고르면 좌우로 고개 젓기, 마지막 성냥은 그 자리에서 불이 붙어 타고, 새 라운드엔 하나씩 톡톡 다시 놓인다.
 * - 아래 띠: 고른 칸 3개(고른 만큼 성냥 그림) + 안내 글자. 오른쪽은 [가져가기] 버튼 자리(buttonReserve)로 비워 둔다.
 * 시계는 clock (게임 시간 → 일시정지면 연출도 멈춤). 연출이 진행 중일 때만 postOnAnimation 으로 다시 그린다.
 * onDraw 에서는 객체를 만들지 않는다. 규칙 판정은 하지 않는다 — 누른 자리 번호만 listener 로 알린다.
 */
class MatchBoardView(context: Context) : View(context) {

    /** 연출 시계 (Long 을 상자에 담지 않도록 fun interface) */
    fun interface Clock {
        fun now(): Long
    }

    interface Listener {
        fun onMatchTap(index: Int)
    }

    var listener: Listener? = null
    var clock: Clock = Clock { SystemClock.uptimeMillis() }
    /** 오른쪽 아래 버튼 자리 폭 (px) */
    var buttonReserve = 0f
        set(v) { field = v; fitHint() }

    private val n = GameConfig.MATCHES

    // ── 성냥 상태 ──
    private val present = BooleanArray(n) { true }
    private val selected = BooleanArray(n)
    private val selAt = LongArray(n) { LONG_AGO }
    private var liftDur = 1L
    private val shakeAt = LongArray(n) { LONG_AGO }
    private var shakeDur = 1L
    private var burnIndex = -1
    private var burnAt = 0L
    private var burnDur = 1L
    private var dealing = false
    private var dealAt = 0L
    private var dealDur = 1L
    private var bandShakeAt = LONG_AGO

    // ── 배치 (onSizeChanged) ──
    private val mx = FloatArray(n)
    private val my = FloatArray(n)
    private var len = 0f
    private var colW = 0f
    private var rowH = 0f
    private val tray = RectF()
    private var headerH = 0f
    private var footerH = 0f
    private var frameColor = Theme.GOLD

    // ── 위 띠 ──
    private var remainingText = NUMS[GameConfig.MATCHES]
    private var remainingW = 0f
    private var roundCount = 0
    private var round = 0
    private val losers = IntArray(GameConfig.MAX_PLAYERS) { -1 }
    private var colors = IntArray(0)
    private var initials = arrayOf<String>()
    private var roundText = ""

    // ── 아래 띠 ──
    private var selCount = 0
    private var selLimit = GameConfig.MAX_TAKE
    private var hint = ""

    private val matchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT; textAlign = Paint.Align.RIGHT }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT_SUB }
    private val tokenText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val path = Path()

    private val frame = Runnable { onFrame() }
    private var looping = false
    private var framesPaused = false

    init {
        isClickable = true
        contentDescription = "성냥 판"
    }

    private fun now(): Long = clock.now()

    // ── 바깥에서 부르는 것 ──

    /** 라운드 칸에 쓸 사람별 색·첫 글자, 라운드 수 */
    fun setup(playerColors: IntArray, playerInitials: Array<String>, rounds: Int) {
        colors = playerColors
        initials = playerInitials
        roundCount = rounds
        losers.fill(-1)
        setRound(0)
    }

    /** 지금 라운드 (0부터) */
    fun setRound(r: Int) {
        round = r
        roundText = ROUND_PREFIX + (r + 1) + " / " + roundCount
        invalidate()
    }

    /** r 라운드에 진 사람 표시 */
    fun setLoser(r: Int, player: Int) {
        losers[r] = player
        invalidate()
    }

    fun setRemaining(count: Int) {
        remainingText = NUMS[count.coerceIn(0, NUMS.size - 1)]
        remainingW = bigPaint.measureText(remainingText)
        invalidate()
    }

    /** 고른 개수 / 이번에 고를 수 있는 최대 */
    fun setSelection(count: Int, limit: Int) {
        selCount = count
        selLimit = limit
        invalidate()
    }

    fun setHint(text: String) {
        if (hint == text) return
        hint = text
        fitHint()
        invalidate()
    }

    fun setFrameColor(color: Int) {
        if (frameColor != color) { frameColor = color; invalidate() }
    }

    /** i 번 성냥 고르기/취소 (durMs 동안 들리거나 내려앉음) */
    fun setSelected(i: Int, sel: Boolean, durMs: Long) {
        if (selected[i] == sel) return
        val t = now()
        // 들리는 중에 바뀌면 지금 높이에서 이어지게
        val cur = lift(i, t)
        selected[i] = sel
        liftDur = durMs.coerceAtLeast(1)
        selAt[i] = t - ((if (sel) cur else 1f - cur) * liftDur).toLong()
        kick()
    }

    /** 더 못 고름: i 번 성냥과 아래 고른 칸이 고개를 젓는다 */
    fun shake(i: Int, durMs: Long) {
        val t = now()
        shakeDur = durMs.coerceAtLeast(1)
        if (i in 0 until n) shakeAt[i] = t
        bandShakeAt = t
        kick()
    }

    /** 가져간 성냥을 판에서 바로 뺀다 (날아가는 그림은 FlyLayer 가) */
    fun remove(i: Int) {
        present[i] = false
        selected[i] = false
        selAt[i] = LONG_AGO
        invalidate()
    }

    /** 마지막 성냥: 판에서 빼되 그 자리에서 불이 붙어 타며 사라진다 */
    fun burn(i: Int, durMs: Long) {
        remove(i)
        burnIndex = i
        burnAt = now()
        burnDur = durMs.coerceAtLeast(1)
        kick()
    }

    /** 새 라운드: 21개가 하나씩 톡톡 다시 놓인다 */
    fun deal(durMs: Long) {
        for (i in 0 until n) { present[i] = true; selected[i] = false; selAt[i] = LONG_AGO; shakeAt[i] = LONG_AGO }
        burnIndex = -1
        dealing = true
        dealAt = now()
        dealDur = durMs.coerceAtLeast(1)
        kick()
    }

    /** 일시정지: 프레임을 멈춘다 (시계도 멈추므로 다시 이으면 그 자리부터) */
    fun pauseFrames() {
        framesPaused = true
        looping = false
        removeCallbacks(frame)
    }

    fun resumeFrames() {
        framesPaused = false
        kick()
    }

    fun isShown(i: Int): Boolean = present[i]
    fun isSelectedShown(i: Int): Boolean = selected[i]
    val isBurning: Boolean get() = burnIndex >= 0
    /** 연출 프레임이 도는 중 */
    val isAnimating: Boolean get() = looping
    val hintText: String get() = hint
    val remainingShown: String get() = remainingText

    /** i 번 성냥 가운데 (이 뷰 좌표) */
    fun matchCenter(i: Int, out: PointF) { out.set(mx[i], my[i]) }
    fun matchAngle(i: Int): Float = TILT[i]
    val matchLength: Float get() = len

    // ── 프레임 ──

    private fun kick() {
        invalidate()
        if (!looping && !framesPaused) {
            looping = true
            postOnAnimation(frame)
        }
    }

    private fun onFrame() {
        if (!looping) return
        invalidate()
        if (anyActive(now())) postOnAnimation(frame) else looping = false
    }

    /** 진행 중인 연출이 있는지 (끝난 것은 여기서 정리) */
    private fun anyActive(t: Long): Boolean {
        var any = false
        for (i in 0 until n) {
            if (t - selAt[i] < liftDur) any = true
            if (t - shakeAt[i] < shakeDur) any = true
        }
        if (t - bandShakeAt < shakeDur) any = true
        if (burnIndex >= 0) { if (t - burnAt >= burnDur) burnIndex = -1 else any = true }
        if (dealing) { if (t - dealAt >= dealDur) dealing = false else any = true }
        return any
    }

    /** 들린 정도 0~1 */
    private fun lift(i: Int, t: Long): Float {
        val q = ((t - selAt[i]).toFloat() / liftDur).coerceIn(0f, 1f)
        return if (selected[i]) q else 1f - q
    }

    // ── 배치 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = Theme.dpf(context, 1f)
        headerH = minOf(d * 58f, h * 0.2f)
        footerH = minOf(d * 58f, h * 0.2f)
        val side = d * 4f
        tray.set(side, headerH, w - side, h - footerH)
        labelPaint.textSize = d * 12f
        roundPaint.textSize = d * 13f
        bigPaint.textSize = minOf(d * 34f, headerH * 0.6f)
        unitPaint.textSize = d * 14f
        tokenText.textSize = d * 11f
        stroke.strokeWidth = d * 2.5f
        val inner = d * 10f
        val iw = tray.width() - inner * 2
        val ih = tray.height() - inner * 2
        colW = iw / GameConfig.PER_ROW
        rowH = ih / GameConfig.ROWS
        len = minOf(rowH * 0.8f, colW * 1.6f)
        for (i in 0 until n) {
            val r = i / GameConfig.PER_ROW
            val k = i % GameConfig.PER_ROW
            mx[i] = tray.left + inner + colW * (k + 0.5f) + JITTER[i] * colW * 0.12f
            my[i] = tray.top + inner + rowH * (r + 0.5f) + len * 0.04f
        }
        remainingW = bigPaint.measureText(remainingText)
        fitHint()
    }

    /** 안내 글자를 버튼 자리 앞까지 맞춤 (setter·크기 바뀔 때만) */
    private fun fitHint() {
        if (width <= 0) return
        val d = Theme.dpf(context, 1f)
        var s = d * 15f
        hintPaint.textSize = s
        val maxW = width - buttonReserve - hintLeft() - d * 8f
        while (hintPaint.measureText(hint) > maxW && s > d * 9f) {
            s *= 0.92f
            hintPaint.textSize = s
        }
    }

    private fun slotSize(): Float = minOf(Theme.dpf(context, 30f), footerH * 0.62f)
    private fun hintLeft(): Float = Theme.dpf(context, 6f) + slotSize() * 3f + Theme.dpf(context, 6f) * 2f + Theme.dpf(context, 12f)

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        if (len <= 0f) return
        val t = now()
        val d = Theme.dpf(context, 1f)
        drawHeader(c, d)
        // 쟁반: 짙은 바탕 + 지금 차례 색 테두리
        val r = d * 14f
        fill.color = 0x40000000
        c.drawRoundRect(tray, r, r, fill)
        // 줄마다 성냥이 서 있는 홈 (옅은 그림자 띠)
        fill.color = 0x26000000
        for (row in 0 until GameConfig.ROWS) {
            val gy = my[row * GameConfig.PER_ROW] + len * 0.5f
            c.drawRoundRect(tray.left + d * 12f, gy - d * 2f, tray.right - d * 12f, gy + d * 5f, d * 3.5f, d * 3.5f, fill)
        }
        stroke.color = frameColor
        stroke.strokeWidth = d * 2.5f
        c.drawRoundRect(tray, r, r, stroke)

        val dealStep = if (dealing) dealDur * 0.6f / n else 0f
        val pop = dealDur * 0.4f
        for (i in 0 until n) {
            if (!present[i]) continue
            var scale = 1f
            if (dealing) {
                val q = ((t - dealAt - dealStep * i) / pop).coerceIn(0f, 1f)
                if (q <= 0f) continue
                scale = if (q < 0.7f) q / 0.7f * 1.15f else 1.15f - 0.15f * (q - 0.7f) / 0.3f
            }
            val l = lift(i, t)
            var x = mx[i]
            val sq = (t - shakeAt[i]).toFloat() / shakeDur
            if (sq in 0f..1f) x += sin(sq * 6f * PI_F) * d * 6f * (1f - sq)
            val y = my[i] - l * len * 0.16f
            val ml = len * scale * (1f + 0.06f * l)
            if (l > 0f) {
                // 금빛 테두리 (막대 둘레)
                c.save()
                c.translate(x, y)
                c.rotate(TILT[i])
                glowPaint.strokeWidth = ml * MatchArt.THICK * 4.4f
                glowPaint.color = MatchArt.fade(Theme.GOLD, 0.28f * l)
                c.drawLine(0f, -ml * 0.47f, 0f, ml * 0.47f, glowPaint)
                glowPaint.strokeWidth = ml * MatchArt.THICK * 2.3f
                glowPaint.color = MatchArt.fade(Theme.GOLD, 0.85f * l)
                c.drawLine(0f, -ml * 0.47f, 0f, ml * 0.47f, glowPaint)
                fill.color = MatchArt.fade(Theme.GOLD, 0.35f * l)
                c.drawOval(-ml * 0.2f, ml * 0.5f + d * 2f, ml * 0.2f, ml * 0.5f + d * 6f, fill)
                c.restore()
            }
            MatchArt.drawMatch(c, x, y, ml, TILT[i], 0f, 1f, matchPaint)
        }
        if (burnIndex >= 0) drawBurning(c, t, d)
        drawFooter(c, t, d)
    }

    /** 마지막 성냥: 번쩍 → 불꽃 → 타 들어감 → 연기와 함께 사라짐 */
    private fun drawBurning(c: Canvas, t: Long, d: Float) {
        val i = burnIndex
        val q = ((t - burnAt).toFloat() / burnDur).coerceIn(0f, 1f)
        val x = mx[i]
        val y = my[i]
        val ang = TILT[i] * PI_F / 180f
        val dist = len / 2f - len * MatchArt.HEAD_LEN * 0.5f
        val hx = x + dist * sin(ang)
        val hy = y - dist * cos(ang)
        // 번쩍
        if (q < 0.14f) {
            val f = q / 0.14f
            fill.color = MatchArt.fade(0xFFFFE9A8.toInt(), 0.8f * (1f - f))
            c.drawCircle(hx, hy, len * (0.25f + 0.65f * f), fill)
        }
        val alpha = if (q > 0.86f) 1f - (q - 0.86f) / 0.14f else 1f
        MatchArt.drawMatch(c, x, y, len, TILT[i], (q / 0.8f).coerceIn(0f, 1f), alpha, matchPaint)
        // 불꽃: 확 커졌다가 → 유지 → 사그라짐. 불씨는 타 들어가는 쪽을 따라 조금 내려간다
        val env = when {
            q < 0.1f -> q / 0.1f * 1.35f
            q < 0.2f -> 1.35f - (q - 0.1f) / 0.1f * 0.35f
            q < 0.72f -> 1f
            q < 0.86f -> 1f - (q - 0.72f) / 0.14f
            else -> 0f
        }
        if (env > 0f) {
            val down = len * 0.2f * (q / 0.8f).coerceIn(0f, 1f)
            val sway = sin(t * 0.03f) * 0.8f + sin(t * 0.071f) * 0.4f
            MatchArt.drawFlame(c, hx + down * sin(ang), hy + down * cos(ang) + len * 0.05f, len * 0.5f * env, sway, 1f, path, matchPaint)
        }
        // 연기 세 덩이
        if (q > 0.68f) {
            val s = (q - 0.68f) / 0.32f
            for (k in 0 until 3) {
                val f = (s - k * 0.18f).coerceIn(0f, 1f)
                if (f <= 0f) continue
                fill.color = MatchArt.fade(0xFF9AA0B5.toInt(), 0.45f * (1f - f))
                c.drawCircle(hx + sin(f * 5f + k) * d * 6f, hy - len * 0.2f - f * len * 0.8f, len * (0.08f + 0.12f * f), fill)
            }
        }
    }

    private fun drawHeader(c: Canvas, d: Float) {
        // 왼쪽: 남은 성냥 큰 숫자
        val left = d * 8f
        c.drawText(LABEL_LEFT, left, d * 15f, labelPaint)
        val base = headerH - d * 8f
        c.drawText(remainingText, left, base, bigPaint)
        c.drawText(UNIT, left + remainingW + d * 4f, base, unitPaint)
        // 오른쪽: 라운드 n / N + 라운드 칸
        val right = width - d * 8f
        c.drawText(roundText, right, d * 15f, roundPaint)
        val rr = minOf(d * 12f, headerH * 0.2f)
        val gap = rr * 2.5f
        val cy = headerH - d * 8f - rr
        for (k in 0 until roundCount) {
            val cx = right - rr - (roundCount - 1 - k) * gap
            val lp = losers[k]
            if (lp >= 0) {
                fill.color = colors[lp]
                c.drawCircle(cx, cy, rr, fill)
                tokenText.color = Theme.textOn(colors[lp])
                c.drawText(initials[lp], cx, cy + tokenText.textSize * 0.36f, tokenText)
                MatchArt.drawSkull(c, cx + rr * 0.8f, cy + rr * 0.6f, rr * 0.8f, fill)
            } else if (k == round) {
                fill.color = Theme.CARD_LIGHT
                c.drawCircle(cx, cy, rr, fill)
                stroke.color = Theme.GOLD
                stroke.strokeWidth = d * 2.5f
                c.drawCircle(cx, cy, rr, stroke)
                tokenText.color = Theme.GOLD
                c.drawText(ROUND_NUMS[k], cx, cy + tokenText.textSize * 0.36f, tokenText)
            } else {
                stroke.color = 0x55FFFFFF
                stroke.strokeWidth = d * 1.5f
                c.drawCircle(cx, cy, rr, stroke)
                tokenText.color = Theme.TEXT_SUB
                c.drawText(ROUND_NUMS[k], cx, cy + tokenText.textSize * 0.36f, tokenText)
            }
        }
    }

    private fun drawFooter(c: Canvas, t: Long, d: Float) {
        val s = slotSize()
        val cy = height - footerH / 2f
        var x = d * 6f
        val sq = (t - bandShakeAt).toFloat() / shakeDur
        if (sq in 0f..1f) x += sin(sq * 6f * PI_F) * d * 6f * (1f - sq)
        val r = s * 0.22f
        for (k in 0 until GameConfig.MAX_TAKE) {
            val l = x + k * (s + d * 6f)
            when {
                k < selCount -> {
                    fill.color = 0x33FFC93C
                    c.drawRoundRect(l, cy - s / 2f, l + s, cy + s / 2f, r, r, fill)
                    stroke.color = Theme.GOLD
                    stroke.strokeWidth = d * 2f
                    c.drawRoundRect(l, cy - s / 2f, l + s, cy + s / 2f, r, r, stroke)
                    MatchArt.drawMatch(c, l + s / 2f, cy, s * 0.95f, 35f, 0f, 1f, matchPaint)
                }
                k < selLimit -> {
                    fill.color = 0x26000000
                    c.drawRoundRect(l, cy - s / 2f, l + s, cy + s / 2f, r, r, fill)
                    stroke.color = 0x55FFFFFF
                    stroke.strokeWidth = d * 1.5f
                    c.drawRoundRect(l, cy - s / 2f, l + s, cy + s / 2f, r, r, stroke)
                }
                else -> {
                    // 남은 성냥이 모자라 못 쓰는 칸
                    fill.color = 0x40000000
                    c.drawRoundRect(l, cy - s / 2f, l + s, cy + s / 2f, r, r, fill)
                    stroke.color = 0x33FFFFFF
                    stroke.strokeWidth = d * 1.5f
                    c.drawLine(l + s * 0.3f, cy - s * 0.2f, l + s * 0.7f, cy + s * 0.2f, stroke)
                    c.drawLine(l + s * 0.7f, cy - s * 0.2f, l + s * 0.3f, cy + s * 0.2f, stroke)
                }
            }
        }
        if (hint.isNotEmpty()) c.drawText(hint, hintLeft(), cy + hintPaint.textSize * 0.36f, hintPaint)
    }

    // ── 터치 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val i = hit(e.x, e.y)
                if (i >= 0) listener?.onMatchTap(i)
                return true
            }
            MotionEvent.ACTION_UP -> { performClick(); return true }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** 누른 곳에서 가장 가까운 남은 성냥 (칸 안), 없으면 -1 */
    fun hit(x: Float, y: Float): Int {
        val t = now()
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in 0 until n) {
            if (!present[i]) continue
            val dx = abs(x - mx[i])
            val dy = abs(y - (my[i] - lift(i, t) * len * 0.16f))
            if (dx > colW / 2f || dy > rowH / 2f) continue
            val dd = dx * 1.6f + dy
            if (dd < bestD) { bestD = dd; best = i }
        }
        return best
    }

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val PI_F = Math.PI.toFloat()
        private const val LONG_AGO = Long.MIN_VALUE / 4
        /** 성냥마다 조금씩 다른 기울기 (도) — 손으로 놓은 듯 */
        private val TILT = floatArrayOf(
            -5f, 3f, -2f, 6f, -4f, 2f, -6f,
            4f, -1f, 5f, -3f, 1f, -5f, 3f,
            -3f, 6f, -2f, 4f, -6f, 2f, -4f,
        )
        /** 가로 어긋남 (칸 폭 × 0.12 배) */
        private val JITTER = floatArrayOf(
            0.3f, -0.5f, 0.2f, -0.1f, 0.6f, -0.4f, 0.1f,
            -0.3f, 0.4f, -0.6f, 0.2f, -0.2f, 0.5f, -0.1f,
            0.4f, -0.2f, 0.1f, -0.5f, 0.3f, 0.6f, -0.4f,
        )
        private val NUMS = Array(GameConfig.MATCHES + 1) { it.toString() }
        private val ROUND_NUMS = Array(GameConfig.MAX_PLAYERS) { (it + 1).toString() }
        private const val LABEL_LEFT = "남은 성냥"
        private const val UNIT = "개"
        private const val ROUND_PREFIX = "라운드 "
    }
}
