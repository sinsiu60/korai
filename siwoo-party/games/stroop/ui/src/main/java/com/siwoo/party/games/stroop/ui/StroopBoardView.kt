package com.siwoo.party.games.stroop.ui

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
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.stroop.Hue
import com.siwoo.party.games.stroop.Question
import com.siwoo.party.games.stroop.Rule
import com.siwoo.party.games.stroop.ZoneFrame

/**
 * 색깔 함정 그림 층 (MultiTouchZoneView 위에 겹침, 터치는 받지 않음).
 * 구역마다 같은 내용을 그 플레이어 쪽으로 돌려 그린다 (위쪽 구역 180°):
 *   맨 위(화면 가운데 쪽) 규칙 알약 "글자 색을 눌러라 / 글자 뜻을 눌러라"
 *   가운데 카드: 색 이름 글자(다른 색으로 칠함) + 이름·점수
 *   맨 아래(플레이어 쪽) 색 버튼 4개 — 순서는 플레이어 기준 왼쪽부터 빨강·파랑·초록·노랑으로 모든 구역 같음
 * 버튼 판정(buttonAt)도 같은 배치를 회전해서 계산한다.
 * 애니메이션(반짝·잠금 막대)은 진행 중일 때만 다시 그린다.
 */
class StroopBoardView(context: Context, private val zoneView: MultiTouchZoneView, private val names: List<String>, private val colors: IntArray) : View(context) {

    /** 게임 시간 (일시정지 제외) */
    fun interface Clock { fun now(): Long }

    var clock: Clock = Clock { SystemClock.uptimeMillis() }
    /** 일시정지 중엔 애니메이션 루프를 멈춘다 */
    var frozen = false
        set(v) { field = v; if (!v) invalidate() }
    /** 반짝·+1 떠오름 길이 (속도 배율 적용된 값) */
    var flashMs = 650L

    private val n = names.size
    private var question: Question? = null
    /** 문제가 끝나 정답을 보여 주는 중이면 정답 색 */
    private var reveal: Hue? = null
    private var winner = -1
    private var counterText = ""
    private val scoreText = Array(n) { "0점" }
    private val flashKind = IntArray(n) // 0 없음, 1 정답, 2 오답
    private val flashHue = IntArray(n)
    private val flashStart = LongArray(n)
    private val lockStart = LongArray(n)
    private val lockEnd = LongArray(n) { Long.MIN_VALUE }

    // ── 구역별 배치 (플레이어 기준 좌표) ──
    private val zw = FloatArray(n)
    private val zh = FloatArray(n)
    private val header = Array(n) { RectF() }
    private val card = Array(n) { RectF() }
    private val btn = Array(n) { Array(HUE_COUNT) { RectF() } }
    private val wordSize = FloatArray(n)
    private val pt = FloatArray(2)

    private val d = Theme.dpf(context, 1f)
    private val rect = RectF()
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val word = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val wordEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF050A24.toInt() }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    // ── 상태 바꾸기 (값이 바뀔 때만 문자열을 만든다) ──

    fun showQuestion(q: Question, total: Int) {
        question = q
        reveal = null
        winner = -1
        counterText = "${q.number}/$total"
        contentDescription = q.rule.label + " " + q.word.label
        invalidate()
    }

    /** 문제 사이 빈 카드 ("준비") */
    fun clearQuestion() {
        question = null
        reveal = null
        winner = -1
        for (i in 0 until n) flashKind[i] = 0
        invalidate()
    }

    /** 문제 끝: 정답 버튼을 모든 구역에서 금색으로, winner(-1 이면 아무도) */
    fun showAnswer(answer: Hue, winnerZone: Int) {
        reveal = answer
        winner = winnerZone
        if (winnerZone >= 0) flash(winnerZone, 1, answer)
        invalidate()
    }

    fun setScore(i: Int, score: Int) {
        scoreText[i] = "${score}점"
        invalidate()
    }

    /** 오답: 버튼 X + -1, untilMs(게임 시간)까지 잠금 표시 */
    fun showWrong(i: Int, hue: Hue, untilMs: Long) {
        flash(i, 2, hue)
        lockStart[i] = clock.now()
        lockEnd[i] = untilMs
        invalidate()
    }

    private fun flash(i: Int, kind: Int, hue: Hue) {
        flashKind[i] = kind
        flashHue[i] = hue.ordinal
        flashStart[i] = clock.now()
    }

    // ── 배치·판정 ──

    private fun zoneOf(i: Int): Zone? {
        val z = zoneView.zone(i) ?: return null
        if (z.width != zw[i] || z.height != zh[i]) layoutZone(i, z.width, z.height)
        return z
    }

    private fun layoutZone(i: Int, w: Float, h: Float) {
        zw[i] = w
        zh[i] = h
        val pad = 10f * d
        val headH = (h * 0.17f).coerceIn(24f * d, 34f * d)
        val btnH = (h * 0.30f).coerceIn(44f * d, 90f * d)
        val gap = BTN_GAP_DP * d
        val btnW = (w - 2f * pad - (HUE_COUNT - 1) * gap) / HUE_COUNT
        val btnTop = h - pad - btnH
        header[i].set(pad, pad, w - pad, pad + headH)
        card[i].set(pad, header[i].bottom + 6f * d, w - pad, btnTop - 8f * d)
        for (k in 0 until HUE_COUNT) {
            val l = pad + k * (btnW + gap)
            btn[i][k].set(l, btnTop, l + btnW, btnTop + btnH)
        }
        wordSize[i] = minOf(card[i].height() * 0.66f, card[i].width() * 0.25f)
    }

    /** 화면 좌표 (x, y)(MultiTouchZoneView 기준)가 zone 구역의 몇 번째 색 버튼인지 (없으면 null) */
    fun buttonAt(zone: Int, x: Float, y: Float): Hue? {
        val z = zoneOf(zone) ?: return null
        ZoneFrame.toLocal(z, x, y, pt)
        val slack = BTN_GAP_DP * d / 2f
        for (k in 0 until HUE_COUNT) {
            val r = btn[zone][k]
            if (pt[0] >= r.left - slack && pt[0] < r.right + slack && pt[1] >= r.top - slack && pt[1] < r.bottom + slack) return HUES[k]
        }
        return null
    }

    /** zone 구역 hue 버튼 가운데의 화면 좌표 → out */
    fun buttonCenter(zone: Int, hue: Hue, out: PointF) {
        val z = zoneOf(zone) ?: return
        val r = btn[zone][hue.ordinal]
        ZoneFrame.toScreen(z, r.centerX(), r.centerY(), pt)
        out.set(pt[0], pt[1])
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val now = clock.now()
        var animating = false
        for (i in 0 until n) {
            val z = zoneOf(i) ?: continue
            c.save()
            c.translate(z.left, z.top)
            c.rotate(z.rotation, z.width / 2f, z.height / 2f)
            if (drawZone(c, i, now)) animating = true
            c.restore()
        }
        drawCounter(c)
        if (animating && !frozen) postInvalidateOnAnimation()
    }

    /** 한 구역 (플레이어 기준 좌표). 애니메이션 중이면 true */
    private fun drawZone(c: Canvas, i: Int, now: Long): Boolean {
        val q = question
        var anim = false
        drawRuleBadge(c, header[i], q?.rule)
        // 카드
        val cr = card[i]
        val rad = 14f * d
        fill.color = 0xE60A1236.toInt()
        c.drawRoundRect(cr, rad, rad, fill)
        stroke.strokeWidth = 1.5f * d
        stroke.color = if (winner == i) Theme.GOLD else 0x33FFFFFF
        c.drawRoundRect(cr, rad, rad, stroke)
        // 이름 (왼쪽 위) · 점수 (오른쪽 위)
        val ts = (cr.height() * 0.2f).coerceIn(11f * d, 15f * d)
        small.textSize = ts
        small.textAlign = Paint.Align.LEFT
        small.color = colors[i]
        c.drawText(names[i], cr.left + 10f * d, cr.top + ts + 5f * d, small)
        small.textAlign = Paint.Align.RIGHT
        small.color = Theme.GOLD
        c.drawText(scoreText[i], cr.right - 10f * d, cr.top + ts + 5f * d, small)
        // 글자
        val ws = wordSize[i]
        val wy = cr.centerY() + ws * 0.36f + ts * 0.25f
        if (q != null) {
            word.textSize = ws
            wordEdge.textSize = ws
            wordEdge.strokeWidth = ws * 0.1f
            c.drawText(q.word.label, cr.centerX(), wy, wordEdge)
            word.color = HUE_COLORS[q.ink.ordinal]
            c.drawText(q.word.label, cr.centerX(), wy, word)
        } else {
            word.textSize = ws * 0.6f
            word.color = 0x55B8C3F0
            c.drawText(WAIT_MARK, cr.centerX(), cr.centerY() + ws * 0.22f + ts * 0.25f, word)
        }
        // 버튼
        val f = flashFraction(i, now)
        if (f < 1f) anim = true
        val rv = reveal?.ordinal ?: -1
        for (k in 0 until HUE_COUNT) {
            var dx = 0f
            if (flashKind[i] == 2 && flashHue[i] == k && f < 1f) dx = (Math.sin(f * Math.PI * 6.0) * (1f - f) * 6f * d).toFloat()
            drawButton(c, btn[i][k], k, dx, rv >= 0 && rv != k)
            if (rv == k) {
                stroke.color = Theme.GOLD
                stroke.strokeWidth = 4f * d
                rect.set(btn[i][k]); rect.inset(-3f * d, -3f * d)
                c.drawRoundRect(rect, 14f * d, 14f * d, stroke)
            }
            if (flashKind[i] == 2 && flashHue[i] == k && f < 1f) drawX(c, btn[i][k], dx)
        }
        // 잠금
        if (now < lockEnd[i]) {
            anim = true
            drawLock(c, i, now)
        }
        // +1 / -1 떠오름
        if (flashKind[i] != 0 && f < 1f) {
            label.textSize = (cr.height() * 0.42f).coerceAtMost(40f * d)
            label.color = if (flashKind[i] == 1) Theme.GOLD else Theme.RED
            label.alpha = (255 * (1f - f * f)).toInt()
            val y = cr.centerY() + label.textSize * 0.6f - f * 12f * d
            val x = cr.right - label.textSize * 0.95f
            c.drawText(if (flashKind[i] == 1) PLUS else MINUS, x, y, label)
            label.alpha = 255
        }
        return anim
    }

    private fun flashFraction(i: Int, now: Long): Float {
        if (flashKind[i] == 0) return 1f
        return ((now - flashStart[i]).toFloat() / flashMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    private fun drawRuleBadge(c: Canvas, area: RectF, rule: Rule?) {
        val h = area.height()
        label.textSize = h * 0.56f
        val text = rule?.label ?: READY
        val icon = if (rule == null) 0f else h * 0.72f
        val tw = label.measureText(text)
        val w = (tw + icon + h * (if (rule == null) 1.2f else 1.5f)).coerceAtMost(area.width())
        rect.set(area.centerX() - w / 2f, area.top, area.centerX() + w / 2f, area.bottom)
        fill.color = 0x66000000
        c.drawRoundRect(rect.left, rect.top + 2f * d, rect.right, rect.bottom + 2f * d, h / 2f, h / 2f, fill)
        fill.color = if (rule == null) 0xFF3A4675.toInt() else if (rule == Rule.INK) Theme.GOLD else SKY
        c.drawRoundRect(rect, h / 2f, h / 2f, fill)
        val tx = rect.left + h * 0.6f + icon + tw / 2f + (if (rule == null) 0f else h * 0.15f)
        label.color = if (rule == null) Theme.TEXT_SUB else Theme.INK
        c.drawText(text, if (rule == null) rect.centerX() else tx, area.centerY() + label.textSize * 0.36f, label)
        if (rule == null) return
        val icx = rect.left + h * 0.6f + icon / 2f
        val icy = area.centerY()
        val r = icon / 2f
        if (rule == Rule.INK) {
            // 물감: 네 색 조각 원
            rect.set(icx - r, icy - r, icx + r, icy + r)
            for (k in 0 until HUE_COUNT) {
                fill.color = HUE_COLORS[k]
                c.drawArc(rect, -90f + k * 90f, 90f, true, fill)
            }
            stroke.color = Theme.INK
            stroke.strokeWidth = 1.5f * d
            c.drawCircle(icx, icy, r, stroke)
        } else {
            // 글자: 흰 네모 안 "가"
            fill.color = 0xFFFFFFFF.toInt()
            c.drawRoundRect(icx - r, icy - r, icx + r, icy + r, r * 0.35f, r * 0.35f, fill)
            stroke.color = Theme.INK
            stroke.strokeWidth = 1.5f * d
            c.drawRoundRect(icx - r, icy - r, icx + r, icy + r, r * 0.35f, r * 0.35f, stroke)
            val keep = label.textSize
            label.textSize = r * 1.35f
            label.color = Theme.INK
            c.drawText(GA, icx, icy + label.textSize * 0.36f, label)
            label.textSize = keep
        }
    }

    /** 입체 색 버튼 (아래 그림자 + 윗면 + 빛) */
    private fun drawButton(c: Canvas, r: RectF, k: Int, dx: Float, dim: Boolean) {
        val rad = minOf(r.width(), r.height()) * 0.22f
        val depth = 4f * d
        val col = HUE_COLORS[k]
        fill.color = Theme.shade(col, 0.55f)
        c.drawRoundRect(r.left + dx, r.top + depth, r.right + dx, r.bottom, rad, rad, fill)
        fill.color = col
        c.drawRoundRect(r.left + dx, r.top, r.right + dx, r.bottom - depth, rad, rad, fill)
        fill.color = 0x40FFFFFF
        c.drawRoundRect(r.left + dx + 5f * d, r.top + 4f * d, r.right + dx - 5f * d, r.top + (r.height() - depth) * 0.38f, rad * 0.6f, rad * 0.6f, fill)
        if (dim) {
            fill.color = 0x99000814.toInt()
            c.drawRoundRect(r.left + dx, r.top, r.right + dx, r.bottom, rad, rad, fill)
        }
    }

    private fun drawX(c: Canvas, r: RectF, dx: Float) {
        val s = minOf(r.width(), r.height()) * 0.26f
        val cx = r.centerX() + dx
        val cy = r.centerY() - 2f * d
        stroke.color = 0xFFFFFFFF.toInt()
        stroke.strokeWidth = 9f * d
        c.drawLine(cx - s, cy - s, cx + s, cy + s, stroke)
        c.drawLine(cx + s, cy - s, cx - s, cy + s, stroke)
        stroke.color = Theme.RED_DARK
        stroke.strokeWidth = 5f * d
        c.drawLine(cx - s, cy - s, cx + s, cy + s, stroke)
        c.drawLine(cx + s, cy - s, cx - s, cy + s, stroke)
    }

    /** 버튼 줄을 어둡게 덮고 자물쇠 + 줄어드는 막대 */
    private fun drawLock(c: Canvas, i: Int, now: Long) {
        val first = btn[i][0]
        val last = btn[i][HUE_COUNT - 1]
        rect.set(first.left - 3f * d, first.top - 3f * d, last.right + 3f * d, first.bottom + 3f * d)
        fill.color = 0xB3050A24.toInt()
        c.drawRoundRect(rect, 14f * d, 14f * d, fill)
        val total = (lockEnd[i] - lockStart[i]).coerceAtLeast(1)
        val left = ((lockEnd[i] - now).toFloat() / total).coerceIn(0f, 1f)
        // 자물쇠
        val s = (rect.height() * 0.36f).coerceAtMost(26f * d)
        val cx = rect.centerX()
        val cy = rect.centerY() - s * 0.15f
        stroke.color = 0xFFFFFFFF.toInt()
        stroke.strokeWidth = s * 0.16f
        c.drawArc(cx - s * 0.32f, cy - s * 0.72f, cx + s * 0.32f, cy + s * 0.05f, 180f, 180f, false, stroke)
        c.drawLine(cx - s * 0.32f, cy - s * 0.33f, cx - s * 0.32f, cy, stroke)
        c.drawLine(cx + s * 0.32f, cy - s * 0.33f, cx + s * 0.32f, cy, stroke)
        fill.color = Theme.RED
        c.drawRoundRect(cx - s * 0.5f, cy - s * 0.05f, cx + s * 0.5f, cy + s * 0.6f, s * 0.12f, s * 0.12f, fill)
        fill.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy + s * 0.25f, s * 0.09f, fill)
        // 남은 시간 막대
        val bw = rect.width() * 0.6f
        val by = rect.bottom - 9f * d
        stroke.strokeWidth = 5f * d
        stroke.color = 0x44FFFFFF
        c.drawLine(cx - bw / 2f, by, cx + bw / 2f, by, stroke)
        stroke.color = Theme.RED
        if (left > 0.01f) c.drawLine(cx - bw / 2f, by, cx - bw / 2f + bw * left, by, stroke)
    }

    /** 문제 번호 (2인 위 가운데, 3인 왼쪽 아래 빈 곳, 4인 가운데) */
    private fun drawCounter(c: Canvas) {
        if (counterText.isEmpty() || width == 0) return
        val r = 19f * d
        val cx: Float
        val cy: Float
        when (n) {
            2 -> { cx = width / 2f; cy = r + 6f * d }
            3 -> { cx = width / 8f; cy = height * 0.75f }
            else -> { cx = width / 2f; cy = height / 2f }
        }
        val rr = if (n == 3) r * 1.5f else r
        fill.color = 0x66000000
        c.drawCircle(cx, cy + 2f * d, rr, fill)
        fill.color = Theme.CARD
        c.drawCircle(cx, cy, rr, fill)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = 2.5f * d
        c.drawCircle(cx, cy, rr - 1.5f * d, stroke)
        label.textSize = rr * 0.62f
        label.color = Theme.GOLD
        c.drawText(counterText, cx, cy + label.textSize * 0.36f, label)
    }

    companion object {
        const val HUE_COUNT = 4
        private const val BTN_GAP_DP = 8f
        private val HUES = Hue.values()
        /** 빨강·파랑·초록·노랑 (Hue 순서) — 어두운 바탕에서 서로 잘 구분되는 색 */
        val HUE_COLORS = intArrayOf(0xFFFF3B3B.toInt(), 0xFF3D8BFF.toInt(), 0xFF22C55E.toInt(), 0xFFFFD21F.toInt())
        private const val SKY = 0xFF8FE3FF.toInt()
        private const val READY = "준비..."
        private const val WAIT_MARK = "?"
        private const val PLUS = "+1"
        private const val MINUS = "-1"
        private const val GA = "가"
    }
}
