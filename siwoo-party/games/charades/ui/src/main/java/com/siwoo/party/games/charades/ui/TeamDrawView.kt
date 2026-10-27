package com.siwoo.party.games.charades.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 4인 팀 뽑기 연출 (네온 파티 판): "A팀 VS B팀" 두 칸, 이름표가 양쪽을 오가며 깜빡이다(룰렛) 점점 느려지고 최종 팀에 멈춘다.
 * 확정되면 금색 테두리 + 이름표 옆에 설명 순서 번호(A1=1, B1=2, A2=3, B2=4).
 * 단계 진행은 화면(CharadesActivity)이 timers 로 show() 를 불러서 한다 (일시정지되면 같이 멈춤).
 * 톡 튀는 연출은 진행 중에만 postOnAnimation, onDraw 할당 없음.
 */
class TeamDrawView(context: Context) : View(context) {

    private val u = Theme.dpf(context, 1f)
    private var names = emptyArray<String>()
    private var colors = IntArray(0)
    /** 플레이어별 지금 보이는 쪽 (0 = A, 1 = B) */
    private var sides = IntArray(0)
    /** 확정 뒤 같은 팀 안 자리(0 위, 1 아래)와 설명 순서 (1부터) */
    private var finalSlot = IntArray(0)
    private var finalOrder = IntArray(0)
    var settled = false
        private set
    private var pop = 0f

    private val panel = RectF()
    private val chip = RectF()
    private val head = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.Party.BG_DEEP }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val orderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val vsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.Party.TEXT_SUB }
    private var title = TITLE_SHUFFLE

    // ── 톡 튀기 (진행 중에만) ──
    private var popMs = 140L
    private var popStart = 0L
    private var popping = false
    private val popFrame = object : Runnable {
        override fun run() {
            if (!popping) return
            val f = ((SystemClock.uptimeMillis() - popStart).toFloat() / popMs).coerceIn(0f, 1f)
            pop = 1f - f
            invalidate()
            if (f >= 1f) popping = false else postOnAnimation(this)
        }
    }

    init {
        titlePaint.textSize = 26f * u
        headPaint.textSize = 17f * u
        namePaint.textSize = 19f * u
        orderPaint.textSize = 13f * u
        vsPaint.textSize = 30f * u
        subPaint.textSize = 13f * u
    }

    fun setPlayers(list: List<String>, playerColors: IntArray) {
        names = list.toTypedArray()
        colors = playerColors.copyOf()
        sides = IntArray(list.size) { it % 2 }
        finalSlot = IntArray(list.size)
        finalOrder = IntArray(list.size)
        settled = false
        title = TITLE_SHUFFLE
        invalidate()
    }

    /** 섞는 중 배치 보여 주기 (arrangement[p] = 0/1) */
    fun show(arrangement: IntArray, popMs: Long) {
        for (k in sides.indices) sides[k] = arrangement[k]
        settled = false
        title = TITLE_SHUFFLE
        startPop(popMs)
    }

    /** 확정: teamOf[p], order = 설명 순서 (players 번호 배열) */
    fun settle(teamOf: IntArray, order: IntArray, popMs: Long) {
        for (k in sides.indices) sides[k] = teamOf[k]
        val seen = IntArray(2)
        for ((n, p) in order.withIndex()) {
            finalOrder[p] = n + 1
            finalSlot[p] = seen[teamOf[p]]++
        }
        settled = true
        title = TITLE_DONE
        startPop(popMs)
    }

    private fun startPop(ms: Long) {
        popMs = ms.coerceAtLeast(1)
        pop = 1f
        popStart = SystemClock.uptimeMillis()
        if (!popping) {
            popping = true
            postOnAnimation(popFrame)
        }
        invalidate()
    }

    /** 플레이어 player 의 이름표 가운데 (이 뷰 좌표) → out */
    fun chipCenter(player: Int, out: PointF) {
        chipRect(player)
        out.set(chip.centerX(), chip.centerY())
    }

    private fun chipRect(player: Int) {
        layoutPanel()
        val side = sides[player]
        var slot = 0
        if (settled) slot = finalSlot[player]
        else for (k in 0 until player) if (sides[k] == side) slot++
        val colW = panel.width() * 0.34f
        val cx = if (side == 0) panel.left + panel.width() * 0.24f else panel.right - panel.width() * 0.24f
        val top = panel.top + 96f * u + slot * 56f * u
        chip.set(cx - colW / 2f, top, cx + colW / 2f, top + 44f * u)
    }

    private fun layoutPanel() {
        val w = width.toFloat()
        val h = height.toFloat()
        val pw = minOf(w - 32f * u, 540f * u)
        val ph = minOf(h - 24f * u, 262f * u)
        panel.set((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
    }

    override fun onDraw(c: Canvas) {
        if (width == 0 || names.isEmpty()) return
        layoutPanel()
        val r = 22f * u
        // 판 (네온 테두리)
        fill.color = 0x66000000
        c.drawRoundRect(panel.left, panel.top + 6f * u, panel.right, panel.bottom + 6f * u, r, r, fill)
        fill.color = Theme.Party.CARD
        c.drawRoundRect(panel, r, r, fill)
        stroke.color = Theme.Party.NEON_PURPLE
        stroke.alpha = 80
        stroke.strokeWidth = 8f * u
        c.drawRoundRect(panel, r, r, stroke)
        stroke.alpha = 255
        stroke.strokeWidth = 2f * u
        c.drawRoundRect(panel, r, r, stroke)

        val cx = panel.centerX()
        c.drawText(title, cx, panel.top + 38f * u, titlePaint)
        for (s in 0..1) {
            val hx = if (s == 0) panel.left + panel.width() * 0.24f else panel.right - panel.width() * 0.24f
            head.set(hx - 46f * u, panel.top + 54f * u, hx + 46f * u, panel.top + 80f * u)
            fill.color = CharadesArt.TEAM_COLORS[s]
            c.drawRoundRect(head, 13f * u, 13f * u, fill)
            c.drawText(CharadesArt.TEAM_NAMES[s], hx, head.centerY() + headPaint.textSize * 0.36f, headPaint)
        }
        // 가운데 VS + 세로선
        stroke.color = 0x44FFFFFF
        stroke.strokeWidth = 1.5f * u
        c.drawLine(cx, panel.top + 56f * u, cx, panel.bottom - 34f * u, stroke)
        fill.color = Theme.Party.CARD
        c.drawCircle(cx, panel.top + 146f * u, 26f * u, fill)
        c.drawText(VS, cx, panel.top + 146f * u + vsPaint.textSize * 0.36f, vsPaint)
        if (settled) c.drawText(ORDER_HINT, cx, panel.bottom - 14f * u, subPaint)

        for (k in names.indices) {
            chipRect(k)
            val s = 1f + 0.12f * pop
            c.save()
            c.scale(s, s, chip.centerX(), chip.centerY())
            val cr = 14f * u
            if (settled) {
                stroke.color = Theme.GOLD
                stroke.strokeWidth = 5f * u
                c.drawRoundRect(chip, cr, cr, stroke)
            } else {
                stroke.color = CharadesArt.TEAM_COLORS[sides[k]]
                stroke.alpha = 120
                stroke.strokeWidth = 6f * u
                c.drawRoundRect(chip, cr, cr, stroke)
                stroke.alpha = 255
            }
            fill.color = colors[k]
            c.drawRoundRect(chip, cr, cr, fill)
            stroke.color = 0x88FFFFFF.toInt()
            stroke.strokeWidth = 1.5f * u
            c.drawRoundRect(chip, cr, cr, stroke)
            namePaint.color = Theme.textOn(colors[k])
            c.drawText(names[k], chip.centerX(), chip.centerY() + namePaint.textSize * 0.36f, namePaint)
            if (settled) {
                // 설명 순서 번호 (이름표 왼쪽 위 금색 동그라미)
                val bx = chip.left + 2f * u
                val by = chip.top + 2f * u
                fill.color = Theme.GOLD
                c.drawCircle(bx, by, 11f * u, fill)
                c.drawText(ORDER_TEXT[finalOrder[k]], bx, by + orderPaint.textSize * 0.36f, orderPaint)
            }
            c.restore()
        }
    }

    override fun onDetachedFromWindow() {
        popping = false
        removeCallbacks(popFrame)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TITLE_SHUFFLE = "팀 뽑는 중..."
        private const val TITLE_DONE = "팀 결정!"
        private const val VS = "VS"
        private const val ORDER_HINT = "번호 = 설명 순서 (팀이 번갈아 한 명씩)"
        private val ORDER_TEXT = arrayOf("", "1", "2", "3", "4")
    }
}
