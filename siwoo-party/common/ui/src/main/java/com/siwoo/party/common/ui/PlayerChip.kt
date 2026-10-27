package com.siwoo.party.common.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 플레이어 칩: 색 동그라미(이름 첫 글자) + 이름 + 누적 점수. 선두면 위에 왕관.
 * 점수는 숫자가 올라가는 애니메이션(animateScore)으로 바뀐다.
 */
class PlayerChip(context: Context) : View(context) {
    var playerId = -1
        private set
    private var name = ""
    private var initial = ""
    private var color = Theme.GRAY
    private var shown = 0
    private var scoreText = "0점"
    private var leader = false
    private var anim: ValueAnimator? = null

    private val crownSpace = Theme.dpf(context, 14f)
    private val pillH = Theme.dpf(context, 40f)
    private val pad = Theme.dpf(context, 6f)
    private val pill = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = Theme.dpf(context, 2f) }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT; textSize = Theme.dpf(context, 14f) }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD; textSize = Theme.dpf(context, 13f) }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; textSize = Theme.dpf(context, 15f) }
    private val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crownPath = Path()

    fun bind(id: Int, name: String, colorIndex: Int, score: Int, leader: Boolean) {
        playerId = id
        this.name = name
        initial = name.take(1)
        color = Theme.playerColor(colorIndex)
        initPaint.color = Theme.textOn(color)
        setScoreNow(score)
        this.leader = leader
        contentDescription = "$name ${score}점"
        requestLayout()
        invalidate()
    }

    fun setScoreNow(v: Int) {
        anim?.cancel()
        shown = v
        scoreText = "${v}점"
        invalidate()
    }

    fun setLeader(v: Boolean) {
        if (leader == v) return
        leader = v
        invalidate()
    }

    /** 점수 숫자가 올라가고 칩이 통통 튄다 */
    fun animateScore(to: Int, durMs: Long) {
        anim?.cancel()
        val from = shown
        if (from == to) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durMs.coerceAtLeast(1)
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val v = from + ((to - from) * (it.animatedValue as Float)).toInt()
                if (v != shown) { shown = v; scoreText = "${v}점"; invalidate() }
            }
            start()
        }
        scaleX = 1.15f; scaleY = 1.15f
        animate().scaleX(1f).scaleY(1f).setDuration(durMs.coerceAtLeast(1)).setInterpolator(OvershootInterpolator(3f)).start()
        contentDescription = "$name ${to}점"
    }

    /** 점수 글자 위치 (이 뷰 좌표) — 점수가 날아오는 목표 */
    fun scoreAnchor(out: PointF) {
        out.set(pad * 2 + pillH * 0.8f + scorePaint.measureText(scoreText) / 2f, crownSpace + pillH * 0.72f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val textW = maxOf(namePaint.measureText(name), scorePaint.measureText("999점"))
        val w = (pad * 2 + pillH * 0.8f + pad + textW + pad * 2).toInt()
        val h = (crownSpace + pillH).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(c: Canvas) {
        pill.set(stroke.strokeWidth, crownSpace, width - stroke.strokeWidth, height - stroke.strokeWidth)
        bg.color = Theme.CARD
        c.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, bg)
        stroke.color = if (leader) Theme.GOLD else color
        c.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, stroke)
        val r = pill.height() * 0.38f
        val cx = pill.left + pad + r
        val cy = pill.centerY()
        bg.color = color
        c.drawCircle(cx, cy, r, bg)
        c.drawText(initial, cx, cy + initPaint.textSize * 0.36f, initPaint)
        val tx = cx + r + pad
        c.drawText(name, tx, cy - Theme.dpf(context, 2f), namePaint)
        c.drawText(scoreText, tx, cy + scorePaint.textSize + Theme.dpf(context, 1f), scorePaint)
        if (leader) Ui.drawCrown(c, cx, crownSpace + Theme.dpf(context, 4f), r * 1.5f, crownPath, crownPaint)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}

/** 플레이어 칩 줄 (허브 위쪽, 결과 화면 위쪽) */
class ChipsBar(context: Context) : android.widget.LinearLayout(context) {
    val chips = ArrayList<PlayerChip>()

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
    }

    /** 칩을 새로 만든다. scoreOf: 표시할 점수, leaders: 왕관을 쓸 사람 id */
    fun bind(players: List<com.siwoo.party.common.PlayerInfo>, scoreOf: (Int) -> Int, leaders: Set<Int>) {
        removeAllViews()
        chips.clear()
        for ((k, p) in players.withIndex()) {
            val chip = PlayerChip(context)
            chip.bind(p.id, p.name, p.colorIndex, scoreOf(p.id), p.id in leaders)
            chips.add(chip)
            addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                if (k > 0) leftMargin = Theme.dp(context, 8f)
            })
        }
    }

    fun chip(id: Int): PlayerChip? = chips.firstOrNull { it.playerId == id }

    fun setLeaders(leaders: Set<Int>) {
        for (c in chips) c.setLeader(c.playerId in leaders)
    }
}
