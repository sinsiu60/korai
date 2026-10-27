package com.siwoo.party.games.tug.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * 줄다리기 판 (화면 전체 위에 겹치는 그림 층, 터치는 아래 구역으로 그대로 전달).
 * - 가운데 가로 밧줄 + 빨간 깃발, 양 끝 금색 승리선, 줄 당기는 사람(원+선)
 * - 위 가운데 남은 시간
 * - 2인일 때는 구역 아래쪽에 이름·탭 수 (구역 가운데는 밧줄이 지나가므로)
 * 움직임은 step(dtMs) 로 진행한다 (화면이 경기 중·연출 중에만 프레임을 돌린다).
 */
class TugView(context: Context) : View(context) {

    private val u = Theme.dpf(context, 1f)

    // ── 팀 정보 (setup 에서 한 번) ──
    /** 팀별 인원 (1 또는 2), 색, 2인용 이름 */
    private var perSide = 1
    private val colors = Array(2) { IntArray(2) }
    private val soloNames = arrayOf("", "")
    private val soloSubs = arrayOf("", "")
    private var showSoloLabels = false

    // ── 상태 ──
    /** 깃발 목표 (−1 왼쪽 승리선 .. +1 오른쪽 승리선) 와 화면 위치 (부드럽게 따라감) */
    var target = 0f
    var flag = 0f
        private set
    /** 따라가는 시간 상수 (ms, 속도 배율 적용된 값) */
    var followMs = 110f
    /** 경기 중: 밧줄이 팽팽하게 떨린다 */
    var playing = false
    private var amp = 0f
    private var phase = 0f
    private val kick = FloatArray(2)
    /** 끝 연출 0..1 (−1 = 아직) */
    private var endT = -1f
    private var endMs = 1300f
    /** 0 왼쪽 승, 1 오른쪽 승, 2 무승부 */
    private var result = -1
    private var timeText = "30"
    private var hurry = false
    private var hurryPulse = 0f

    // ── 그리기 도구 ──
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }

    init {
        isClickable = false
        isFocusable = false
        timePaint.textSize = 28f * u
        smallPaint.textSize = 11f * u
        namePaint.textSize = 24f * u
        subPaint.textSize = 17f * u
    }

    /**
     * 팀 구성. leftColors/rightColors = 팀원 색 (안쪽 사람부터),
     * soloLeft/soloRight = 2인일 때 아래쪽에 쓸 이름 (4인은 null → 구역이 이름을 쓴다)
     */
    fun setup(leftColors: IntArray, rightColors: IntArray, soloLeft: String?, soloRight: String?) {
        perSide = leftColors.size
        for (k in 0 until perSide) {
            colors[0][k] = leftColors[k]
            colors[1][k] = rightColors[k]
        }
        showSoloLabels = soloLeft != null && soloRight != null
        soloNames[0] = soloLeft ?: ""
        soloNames[1] = soloRight ?: ""
        invalidate()
    }

    /** 2인 아래쪽 글자 (탭 수 등) */
    fun setSoloSub(side: Int, text: String) {
        soloSubs[side] = text
        invalidate()
    }

    /** 남은 시간 글자, hurry = 빨갛게 */
    fun setTime(text: String, hurry: Boolean) {
        timeText = text
        if (hurry && !this.hurry) hurryPulse = 1f
        this.hurry = hurry
        timePaint.textSize = (if (text.length > 3) 20f else 28f) * u
        invalidate()
    }

    /** 초 단위가 바뀔 때마다 톡 (마지막 몇 초) */
    fun pulseTime() {
        hurryPulse = 1f
    }

    /** 그 팀이 탭 → 밧줄이 출렁, 그 팀 사람이 뒤로 홱 */
    fun kick(side: Int) {
        kick[side] = 1f
        amp = minOf(amp + 1.6f * u, 7f * u)
    }

    /** 승패 결정: side 0 왼쪽 / 1 오른쪽 / 2 무승부. 밧줄이 이긴 쪽으로 끌려가고 진 쪽은 넘어진다 */
    fun finish(side: Int, durMs: Long) {
        result = side
        playing = false
        endT = 0f
        endMs = durMs.coerceAtLeast(1).toFloat()
        if (side == 0) target = -1.12f
        if (side == 1) target = 1.12f
    }

    val ended: Boolean get() = endT >= 1f

    /**
     * 시간 dtMs 만큼 진행하고 다시 그린다. 아직 움직일 게 남았으면 true.
     */
    fun step(dtMs: Float): Boolean {
        val follow = if (endT >= 0f) endMs * 0.3f else followMs
        flag += (target - flag) * (1f - exp(-dtMs / follow.coerceAtLeast(1f)))
        amp *= exp(-dtMs / 260f)
        phase += dtMs * 0.024f
        if (phase > 1000f) phase -= 1000f
        kick[0] *= exp(-dtMs / 140f)
        kick[1] *= exp(-dtMs / 140f)
        hurryPulse *= exp(-dtMs / 220f)
        if (endT in 0f..1f) endT = (endT + dtMs / endMs).coerceAtMost(1f)
        invalidate()
        return playing || abs(target - flag) > 0.002f || amp > 0.1f * u || kick[0] > 0.02f || kick[1] > 0.02f ||
            hurryPulse > 0.02f || (endT >= 0f && endT < 1f)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val half = w * 0.3f
        val ropeY = cy - 8f * u
        val fu = u * FIGURE_SCALE
        val ground = ropeY + 38f * fu
        val e = if (endT < 0f) 0f else endT * endT * (3f - 2f * endT)

        // 판 바탕 (밧줄이 지나가는 띠)
        rect.set(6f * u, cy - 60f * u, w - 6f * u, cy + 58f * u)
        band.style = Paint.Style.FILL
        band.color = 0xB30A1240.toInt()
        c.drawRoundRect(rect, 18f * u, 18f * u, band)
        band.style = Paint.Style.STROKE
        band.strokeWidth = 1.5f * u
        band.color = Theme.CARD_BORDER
        c.drawRoundRect(rect, 18f * u, 18f * u, band)
        // 땅
        band.color = 0x33FFFFFF
        band.strokeWidth = 2f * u
        c.drawLine(rect.left + 14f * u, ground + 2f * u, rect.right - 14f * u, ground + 2f * u, band)

        // 가운데 표시 (작은 삼각 눈금)
        p.style = Paint.Style.FILL
        p.color = 0x99FFFFFF.toInt()
        c.drawCircle(cx, rect.top + 7f * u, 3f * u, p)
        c.drawCircle(cx, rect.bottom - 7f * u, 3f * u, p)
        TugArt.winLine(c, p, cx, rect.top + 14f * u, rect.bottom - 14f * u, u * 0.45f, 0x55FFFFFF)

        // 승리선 (닿은 쪽은 금색으로 밝게)
        val leftHot = result == 0 && e > 0f
        val rightHot = result == 1 && e > 0f
        TugArt.winLine(c, p, cx - half, rect.top + 18f * u, rect.bottom - 8f * u, u, if (leftHot) Theme.GOLD_LIGHT else Theme.GOLD)
        TugArt.winLine(c, p, cx + half, rect.top + 18f * u, rect.bottom - 8f * u, u, if (rightHot) Theme.GOLD_LIGHT else Theme.GOLD)
        c.drawText(LINE_LABEL, cx - half, rect.top + 13f * u, smallPaint)
        c.drawText(LINE_LABEL, cx + half, rect.top + 13f * u, smallPaint)

        // 밧줄: 깃발 위치만큼 밀림
        val shift = flag * half
        val jitter = if (playing) 0.7f * u * sin(phase * 7f) else 0f
        TugArt.rope(c, p, -10f * u, w + 10f * u, ropeY, 9f * u, amp + jitter, 120f * u, phase, shift)

        // 사람: 밧줄과 함께 조금 끌려감(화면 밖으로 나가지 않게 승리선까지만), 진 쪽은 끝 연출 때 더 끌려가며 넘어짐
        val charShift = flag.coerceIn(-1f, 1f) * half * 0.08f
        for (side in 0..1) {
            val dir = if (side == 0) 1f else -1f
            val lose = (result == 0 && side == 1) || (result == 1 && side == 0)
            val win = (result == 0 && side == 0) || (result == 1 && side == 1)
            for (k in 0 until perSide) {
                var x = cx - dir * (half + 36f * u + k * 38f * u) + charShift
                var lean = 14f + 9f * kick[side] - 3f * k
                var fall = 0f
                var lift = 0f
                if (lose) {
                    x += dir * e * (30f + k * 10f) * u
                    lean *= 1f - e
                    fall = 78f * e
                } else if (win) {
                    lean = 14f + 6f * e
                    lift = abs(sin(e * 3f * PI_F)) * 7f * u * e
                }
                TugArt.figure(c, p, x, ground - lift, fu, dir, lean, fall, colors[side][k], win && e >= 1f)
            }
        }

        // 깃발 (밧줄 가운데)
        val fx = cx + shift
        val fy = TugArt.ropeY(fx, -10f * u, w + 20f * u, ropeY, amp + jitter, 120f * u, phase)
        TugArt.pennant(c, p, fx, fy, 34f * u, sin(phase * 2.3f) * 4f * u + (target - flag) * -40f * u)

        drawTimer(c, cx)
        if (showSoloLabels) {
            drawSolo(c, w * 0.25f, h, 0)
            drawSolo(c, w * 0.75f, h, 1)
        }
    }

    private fun drawTimer(c: Canvas, cx: Float) {
        val s = 1f + 0.18f * hurryPulse
        val hw = 46f * u * s
        val hh = 22f * u * s
        val cyT = 30f * u
        rect.set(cx - hw, cyT - hh, cx + hw, cyT + hh)
        band.style = Paint.Style.FILL
        band.color = if (hurry) Theme.RED_DARK else Theme.BG_DEEP
        c.drawRoundRect(rect, hh, hh, band)
        band.style = Paint.Style.STROKE
        band.strokeWidth = 2.5f * u
        band.color = if (hurry) Theme.RED else Theme.GOLD
        c.drawRoundRect(rect, hh, hh, band)
        // 시계 모양 작은 원
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * u
        p.color = if (hurry) Theme.GOLD_LIGHT else Theme.GOLD
        val ix = cx - hw + 17f * u * s
        c.drawCircle(ix, cyT, 7f * u, p)
        c.drawLine(ix, cyT, ix, cyT - 4.5f * u, p)
        c.drawLine(ix, cyT, ix + 3.5f * u, cyT, p)
        c.save()
        c.scale(s, s, cx, cyT)
        c.drawText(timeText, cx + 9f * u, cyT + timePaint.textSize * 0.36f, timePaint)
        c.restore()
    }

    private fun drawSolo(c: Canvas, x: Float, h: Float, side: Int) {
        namePaint.color = colors[side][0]
        val y = h - 52f * u
        c.drawText(soloNames[side], x, y, namePaint)
        c.drawText(soloSubs[side], x, y + 26f * u, subPaint)
    }

    companion object {
        private const val LINE_LABEL = "승리선"
        private const val PI_F = Math.PI.toFloat()
        /** 사람 크기 배율 */
        private const val FIGURE_SCALE = 1.3f
    }
}
