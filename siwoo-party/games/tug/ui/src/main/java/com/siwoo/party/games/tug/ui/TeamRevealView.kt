package com.siwoo.party.games.tug.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Theme

/**
 * 4인 팀 뽑기 연출: 가운데 판에 "왼쪽 팀 VS 오른쪽 팀" 두 칸씩,
 * 이름표가 양쪽을 오가며 깜빡이다(룰렛) 점점 느려지고 최종 팀에 멈춘다 → 금색 테두리.
 * 단계 진행은 화면(TugActivity)이 timers 로 show() 를 불러서 한다 (일시정지되면 같이 멈춤).
 */
class TeamRevealView(context: Context) : View(context) {

    private val u = Theme.dpf(context, 1f)
    private var names = emptyArray<String>()
    private var colors = IntArray(0)
    /** 플레이어별 지금 보이는 쪽 (0 왼쪽, 1 오른쪽) */
    private var sides = IntArray(0)
    private var settled = false
    private var pop = 0f
    private var anim: ValueAnimator? = null

    private val panel = RectF()
    private val chip = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val vsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private var title = TITLE_SHUFFLE

    init {
        isClickable = false
        isFocusable = false
        titlePaint.textSize = 26f * u
        headPaint.textSize = 16f * u
        namePaint.textSize = 19f * u
        vsPaint.textSize = 30f * u
    }

    fun setPlayers(list: List<PlayerInfo>, playerColors: IntArray) {
        names = Array(list.size) { list[it].name }
        colors = playerColors.copyOf()
        sides = IntArray(list.size) { it % 2 }
        settled = false
        title = TITLE_SHUFFLE
        invalidate()
    }

    /** 이번 칸 배치 보여 주기 (final = 확정) */
    fun show(arrangement: IntArray, final: Boolean) {
        for (k in sides.indices) sides[k] = arrangement[k]
        settled = final
        title = if (final) TITLE_DONE else TITLE_SHUFFLE
        // 바뀔 때 톡
        anim?.cancel()
        anim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = if (final) 420 else 140
            addUpdateListener { pop = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** 플레이어 player 의 이름표 가운데 (이 뷰 좌표) → out */
    fun chipCenter(player: Int, out: PointF) {
        chipRect(player)
        out.set(chip.centerX(), chip.centerY())
    }

    /** 이름표 자리: 같은 쪽 안에서는 원래 순서대로 위→아래 */
    private fun chipRect(player: Int) {
        layoutPanel()
        val side = sides[player]
        var slot = 0
        for (k in 0 until player) if (sides[k] == side) slot++
        val colW = panel.width() * 0.36f
        val cx = if (side == 0) panel.left + panel.width() * 0.25f else panel.right - panel.width() * 0.25f
        val top = panel.top + 86f * u + slot * 54f * u
        chip.set(cx - colW / 2f, top, cx + colW / 2f, top + 44f * u)
    }

    private fun layoutPanel() {
        val w = width.toFloat()
        val h = height.toFloat()
        val pw = minOf(w - 40f * u, 520f * u)
        val ph = minOf(h - 40f * u, 236f * u)
        panel.set((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onDraw(c: Canvas) {
        if (width == 0 || names.isEmpty()) return
        layoutPanel()
        val r = 20f * u
        // 판
        fill.color = 0x66000000
        c.save()
        c.translate(0f, 6f * u)
        c.drawRoundRect(panel, r, r, fill)
        c.restore()
        fill.color = Theme.CARD
        c.drawRoundRect(panel, r, r, fill)
        stroke.strokeWidth = 2f * u
        stroke.color = Theme.GOLD_DARK
        c.drawRoundRect(panel, r, r, stroke)

        val cx = panel.centerX()
        c.drawText(title, cx, panel.top + 38f * u, titlePaint)
        val lx = panel.left + panel.width() * 0.25f
        val rx = panel.right - panel.width() * 0.25f
        c.drawText(LEFT_TEAM, lx, panel.top + 72f * u, headPaint)
        c.drawText(RIGHT_TEAM, rx, panel.top + 72f * u, headPaint)
        // 가운데 VS + 세로선
        stroke.color = Theme.CARD_BORDER
        stroke.strokeWidth = 1.5f * u
        c.drawLine(cx, panel.top + 56f * u, cx, panel.bottom - 18f * u, stroke)
        fill.color = Theme.CARD
        c.drawCircle(cx, panel.top + 132f * u, 26f * u, fill)
        c.drawText(VS, cx, panel.top + 132f * u + vsPaint.textSize * 0.36f, vsPaint)

        for (k in names.indices) {
            chipRect(k)
            val s = 1f + 0.12f * pop
            c.save()
            c.scale(s, s, chip.centerX(), chip.centerY())
            if (settled) {
                stroke.color = Theme.GOLD
                stroke.strokeWidth = 5f * u
                c.drawRoundRect(chip, 14f * u, 14f * u, stroke)
            }
            fill.color = colors[k]
            c.drawRoundRect(chip, 14f * u, 14f * u, fill)
            stroke.color = 0x88FFFFFF.toInt()
            stroke.strokeWidth = 1.5f * u
            c.drawRoundRect(chip, 14f * u, 14f * u, stroke)
            namePaint.color = Theme.textOn(colors[k])
            c.drawText(names[k], chip.centerX(), chip.centerY() + namePaint.textSize * 0.36f, namePaint)
            c.restore()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TITLE_SHUFFLE = "팀 뽑는 중..."
        private const val TITLE_DONE = "팀 결정!"
        private const val LEFT_TEAM = "왼쪽 팀"
        private const val RIGHT_TEAM = "오른쪽 팀"
        private const val VS = "VS"
    }
}
