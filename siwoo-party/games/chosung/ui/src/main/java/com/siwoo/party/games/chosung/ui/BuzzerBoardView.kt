package com.siwoo.party.games.chosung.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.chosung.GameConfig

/**
 * 버저 그림 층 (MultiTouchZoneView 위에 겹침, 터치는 받지 않음 — 버저 판정은 구역 전체).
 * 구역마다 가운데 판이 덮지 않는 가장 넓은 빈 곳(free)에 큰 둥근 버저 + 이름 + 점수를 그 사람 쪽으로 돌려 그린다.
 * 상태: 준비(밝음) / 다른 사람이 답하는 중(흐림) / 답하는 중(금빛 + 남은 초 고리) / 잠금(회색 + 자물쇠) / 정답(금빛)
 * 애니메이션(남은 초 고리·흔들림·+1/-1)은 진행 중일 때만 다시 그린다.
 */
class BuzzerBoardView(
    context: Context,
    private val zoneView: MultiTouchZoneView,
    private val names: List<String>,
    private val colors: IntArray,
) : View(context) {

    fun interface Clock { fun now(): Long }

    enum class State { IDLE, READY, BUSY, ANSWERING, LOCKED, WINNER }

    var clock: Clock = Clock { SystemClock.uptimeMillis() }
    var frozen = false
        set(v) { field = v; if (!v) invalidate() }
    var flashMs = GameConfig.FLASH_MS

    private val n = names.size
    private val state = Array(n) { State.IDLE }
    private val scoreText = Array(n) { "0점" }
    private var deadline = 0L
    private val flashKind = IntArray(n) // 0 없음, 1 +1, 2 -1, 3 잠김 흔들
    private val flashAt = LongArray(n)

    // 가운데 판 (이 View 좌표). 비어 있으면 판 없음
    private val panel = RectF()
    private val free = Array(n) { RectF() }
    private val zw = FloatArray(n)
    private val zh = FloatArray(n)
    private var panelVersion = 0
    private val laidOutVersion = IntArray(n) { -1 }

    private val d = Theme.dpf(context, 1f)
    private val rect = RectF()
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    // ── 상태 ──

    fun setPanelRect(l: Float, t: Float, r: Float, b: Float) {
        panel.set(l, t, r, b)
        panelVersion++
        invalidate()
    }

    fun setAll(s: State) {
        for (i in 0 until n) state[i] = s
        invalidate()
    }

    fun setState(i: Int, s: State) {
        state[i] = s
        invalidate()
    }

    fun state(i: Int): State = state[i]

    /** i 가 deadlineMs 까지 답함, 나머지(잠기지 않은 사람)는 흐리게 */
    fun setAnswering(i: Int, deadlineMs: Long) {
        deadline = deadlineMs
        for (k in 0 until n) if (state[k] != State.LOCKED) state[k] = if (k == i) State.ANSWERING else State.BUSY
        invalidate()
    }

    /** 다시 열림: 잠기지 않은 사람은 준비 */
    fun reopen() {
        for (k in 0 until n) if (state[k] != State.LOCKED) state[k] = State.READY
        invalidate()
    }

    fun setScore(i: Int, score: Int) {
        scoreText[i] = "${score}점"
        invalidate()
    }

    /** +1 (kind 1) / -1 (kind 2) / 잠김 흔들 (kind 3) */
    fun flash(i: Int, kind: Int) {
        flashKind[i] = kind
        flashAt[i] = clock.now()
        invalidate()
    }

    /** 버저 가운데 (화면 = 이 View 좌표) */
    fun buzzerCenter(i: Int, out: PointF) {
        if (layoutZone(i) == null) return
        val f = free[i]
        out.set(f.centerX(), f.centerY())
    }

    // ── 배치: 구역 - 가운데 판 = 빈 곳 중 가장 넓은(짧은 변 기준) 사각형 ──

    private fun layoutZone(i: Int): Zone? {
        val z = zoneView.zone(i) ?: return null
        if (z.width == zw[i] && z.height == zh[i] && laidOutVersion[i] == panelVersion) return z
        zw[i] = z.width
        zh[i] = z.height
        laidOutVersion[i] = panelVersion
        val f = free[i]
        f.set(z.left, z.top, z.right, z.bottom)
        val hit = !panel.isEmpty && panel.left < z.right && panel.right > z.left && panel.top < z.bottom && panel.bottom > z.top
        if (hit) {
            var best = -1f
            val gap = 6f * d
            // 왼쪽 / 오른쪽 / 위 / 아래
            for (k in 0 until 4) {
                when (k) {
                    0 -> rect.set(z.left, z.top, minOf(z.right, panel.left - gap), z.bottom)
                    1 -> rect.set(maxOf(z.left, panel.right + gap), z.top, z.right, z.bottom)
                    2 -> rect.set(z.left, z.top, z.right, minOf(z.bottom, panel.top - gap))
                    else -> rect.set(z.left, maxOf(z.top, panel.bottom + gap), z.right, z.bottom)
                }
                if (rect.width() <= 0f || rect.height() <= 0f) continue
                val score = minOf(rect.width(), rect.height() * 1.25f)
                if (score > best) { best = score; f.set(rect) }
            }
        }
        val inset = 8f * d
        f.inset(inset, inset)
        return z
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val now = clock.now()
        var anim = false
        for (i in 0 until n) {
            val z = layoutZone(i) ?: continue
            c.save()
            c.rotate(z.rotation, free[i].centerX(), free[i].centerY())
            if (drawBuzzer(c, i, now)) anim = true
            c.restore()
        }
        if (anim && !frozen) postInvalidateOnAnimation()
    }

    private fun drawBuzzer(c: Canvas, i: Int, now: Long): Boolean {
        val f = free[i]
        val wide = f.width() > f.height() * 1.6f
        val nameSize = (minOf(f.width(), f.height()) * 0.13f).coerceIn(13f * d, 20f * d)
        val r: Float
        val cx: Float
        val cy: Float
        if (wide) {
            r = f.height() * 0.4f
            cx = f.centerX()
            cy = f.centerY() - r * 0.06f
        } else {
            r = minOf(f.width() * 0.34f, (f.height() - nameSize * 3.2f) * 0.44f).coerceAtLeast(14f * d)
            cx = f.centerX()
            cy = f.centerY() + nameSize * 0.1f
        }
        val s = state[i]
        var anim = false
        val ff = ((now - flashAt[i]).toFloat() / flashMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        if (flashKind[i] != 0 && ff < 1f) anim = true
        var dx = 0f
        if ((flashKind[i] == 2 || flashKind[i] == 3) && ff < 1f) dx = (Math.sin(ff * Math.PI * 6.0) * (1f - ff) * 7f * d).toFloat()
        val color = colors[i]
        val dim = s == State.BUSY || s == State.LOCKED || s == State.IDLE
        // 금빛 (답하는 중·정답)
        if (s == State.ANSWERING || s == State.WINNER) {
            art.style = Paint.Style.FILL
            art.color = Theme.GOLD
            for (k in 3 downTo 1) {
                art.alpha = 40 * (4 - k)
                c.drawCircle(cx + dx, cy, r * (1.12f + 0.09f * k), art)
            }
            art.alpha = 255
        }
        ChosungArt.buzzer(c, cx + dx, cy, r, color, if (s == State.ANSWERING) 1f else 0f, dim, art)
        // 버저 위 글자
        val label = when (s) {
            State.READY -> BUZZ
            State.ANSWERING -> null
            State.LOCKED -> null
            State.WINNER -> WIN
            else -> null
        }
        val down = if (s == State.ANSWERING) r * 0.16f else 0f
        if (label != null) {
            text.textSize = r * 0.42f
            text.color = 0xFFFFFFFF.toInt()
            text.setShadowLayer(3f * d, 0f, 1.5f * d, 0x99000000.toInt())
            c.drawText(label, cx + dx, cy + down + text.textSize * 0.36f, text)
            text.clearShadowLayer()
        }
        if (s == State.LOCKED) drawLock(c, cx + dx, cy, r * 0.62f)
        if (s == State.ANSWERING) {
            // 남은 초 고리 + 숫자
            val left = ((deadline - now).coerceAtLeast(0)).toFloat() / GameConfig.ANSWER_MS
            stroke.strokeWidth = r * 0.14f
            stroke.color = 0x55000000
            val rr = r * 1.24f
            c.drawCircle(cx, cy, rr, stroke)
            stroke.color = if (left < 0.3f) Theme.RED else Theme.GOLD
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            c.drawArc(rect, -90f, 360f * left, false, stroke)
            val sec = (((deadline - now).coerceAtLeast(0) + 999) / 1000).toInt()
            text.textSize = r * 0.8f
            text.color = 0xFFFFFFFF.toInt()
            text.setShadowLayer(3f * d, 0f, 1.5f * d, 0x99000000.toInt())
            c.drawText(DIGITS[sec.coerceIn(0, 9)], cx, cy + down + text.textSize * 0.36f, text)
            text.clearShadowLayer()
            anim = true
        }
        // 이름·점수
        text.textSize = nameSize
        val nameColor = if (s == State.LOCKED) Theme.GRAY else color
        val scoreColor = Theme.GOLD
        if (wide) {
            val gx = r * 1.35f + (f.width() / 2f - r * 1.35f) / 2f
            text.color = nameColor
            c.drawText(names[i], cx - gx, cy + nameSize * 0.36f, text)
            text.color = scoreColor
            c.drawText(scoreText[i], cx + gx, cy + nameSize * 0.36f, text)
            if (s == State.LOCKED || s == State.ANSWERING) {
                text.textSize = nameSize * 0.72f
                text.color = if (s == State.LOCKED) Theme.Party.TEXT_SUB else Theme.GOLD_LIGHT
                c.drawText(if (s == State.LOCKED) LOCK_TEXT else TALK_TEXT, cx + gx, cy + nameSize * 1.5f, text)
            }
        } else {
            text.color = nameColor
            val topY = cy - r * 1.28f - nameSize * 0.35f
            c.drawText(names[i], cx, topY, text)
            text.color = scoreColor
            text.textSize = nameSize * 0.9f
            val by = cy + r * 1.3f + nameSize * 1.05f
            if (s == State.LOCKED || s == State.ANSWERING) {
                // 점수 옆에 상태
                text.textAlign = Paint.Align.RIGHT
                c.drawText(scoreText[i], cx - nameSize * 0.4f, by, text)
                text.textAlign = Paint.Align.LEFT
                text.color = if (s == State.LOCKED) Theme.Party.TEXT_SUB else Theme.GOLD_LIGHT
                c.drawText(if (s == State.LOCKED) LOCK_TEXT else TALK_TEXT, cx + nameSize * 0.2f, by, text)
                text.textAlign = Paint.Align.CENTER
            } else {
                c.drawText(scoreText[i], cx, by, text)
            }
        }
        // +1 / -1
        if ((flashKind[i] == 1 || flashKind[i] == 2) && ff < 1f) {
            text.textSize = r * 0.75f
            text.color = if (flashKind[i] == 1) Theme.GOLD else Theme.RED
            text.alpha = (255 * (1f - ff * ff)).toInt()
            c.drawText(if (flashKind[i] == 1) PLUS else MINUS, cx + r * 1.15f, cy - r * 0.55f - ff * 14f * d, text)
            text.alpha = 255
        }
        return anim
    }

    private fun drawLock(c: Canvas, cx: Float, cy: Float, s: Float) {
        stroke.color = 0xFFFFFFFF.toInt()
        stroke.strokeWidth = s * 0.16f
        c.drawArc(cx - s * 0.32f, cy - s * 0.72f, cx + s * 0.32f, cy + s * 0.05f, 180f, 180f, false, stroke)
        c.drawLine(cx - s * 0.32f, cy - s * 0.33f, cx - s * 0.32f, cy, stroke)
        c.drawLine(cx + s * 0.32f, cy - s * 0.33f, cx + s * 0.32f, cy, stroke)
        art.style = Paint.Style.FILL
        art.color = Theme.RED
        c.drawRoundRect(cx - s * 0.5f, cy - s * 0.05f, cx + s * 0.5f, cy + s * 0.6f, s * 0.12f, s * 0.12f, art)
        art.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy + s * 0.25f, s * 0.09f, art)
    }

    companion object {
        private const val BUZZ = "버저"
        private const val WIN = "정답!"
        private const val LOCK_TEXT = "잠금"
        private const val TALK_TEXT = "말하세요!"
        private const val PLUS = "+1"
        private const val MINUS = "-1"
        private val DIGITS = Array(10) { it.toString() }
    }
}
