package com.siwoo.party.games.horserace.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.horserace.GameConfig
import com.siwoo.party.games.horserace.HorseRaceGame

/**
 * 베팅 화면의 말 카드 6장 (한 줄) + 아래 안내 한 줄.
 * 카드: 번호 색 띠("3번 말") · 서 있는 말 그림 · "총 70칩" · 건 사람 줄(색 점 + 이름 + 금액, 지금 차례 사람은 금색).
 * 카드를 누르면 listener 로 알린다 (고른 카드는 금테 + 살짝 위로). 글자는 refresh 때만 만든다.
 */
class BetBoardView(context: Context) : View(context) {

    fun interface Listener { fun onHorseTap(horse: Int) }

    var listener: Listener? = null
    var touchEnabled = true

    private val painter = HorsePainter()
    private val d = Theme.dpf(context, 1f)
    private val cards = Array(GameConfig.HORSES) { RectF() }
    private var selected = -1
    private var current = -1
    private var lift = 0f
    private var liftAnim: ValueAnimator? = null
    private var downCard = -1

    private var rawNames: List<String> = emptyList()
    private var fitNames: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private var playerCount = 0
    /** lines[h] = 그 말에 건 사람 (차례 순서), amounts 글자 */
    private val linePlayer = Array(GameConfig.HORSES) { IntArray(GameConfig.MAX_PLAYERS) }
    private val lineCount = IntArray(GameConfig.HORSES)
    private var lineAmount: Array<Array<String>> = Array(GameConfig.HORSES) { Array(GameConfig.MAX_PLAYERS) { "" } }
    private val totalText = Array(GameConfig.HORSES) { NONE }
    private val hasTotal = BooleanArray(GameConfig.HORSES)
    private var hint = ""
    private var hintWarn = false

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val amtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    fun setPlayers(names: List<String>, playerColors: IntArray) {
        rawNames = names
        colors = playerColors.copyOf()
        playerCount = names.size
        fitAll()
        invalidate()
    }

    /** 게임 상태에서 카드 글자 다시 만들기 (베팅이 바뀔 때마다) */
    fun refresh(game: HorseRaceGame) {
        current = game.currentBettor
        for (h in 0 until GameConfig.HORSES) {
            val total = game.horseTotal(h)
            hasTotal[h] = total > 0
            totalText[h] = if (total > 0) "총 " + total + "칩" else NONE
            var n = 0
            // 지금 차례 사람을 맨 위에
            if (current >= 0 && game.bets[current][h] > 0) {
                linePlayer[h][n] = current
                lineAmount[h][n] = game.bets[current][h].toString()
                n++
            }
            for (p in 0 until game.playerCount) {
                if (p == current || game.bets[p][h] <= 0) continue
                linePlayer[h][n] = p
                lineAmount[h][n] = game.bets[p][h].toString()
                n++
            }
            lineCount[h] = n
        }
        invalidate()
    }

    fun setSelected(h: Int) {
        if (selected == h) return
        selected = h
        liftAnim?.cancel()
        if (h >= 0) {
            liftAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180
                addUpdateListener { lift = it.animatedValue as Float; invalidate() }
                start()
            }
        } else lift = 0f
        invalidate()
    }

    val selectedHorse: Int get() = selected

    fun setHint(text: String, warn: Boolean) {
        hint = text
        hintWarn = warn
        invalidate()
    }

    val hintText: String get() = hint

    /** 카드 h 의 가운데 (이 View 좌표) */
    fun cardCenter(h: Int, out: PointF) {
        out.set(cards[h].centerX(), cards[h].centerY())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val gap = d * 6f
        val hintH = d * 24f
        val cw = (w - gap * (GameConfig.HORSES - 1)) / GameConfig.HORSES
        for (k in 0 until GameConfig.HORSES) {
            val l = k * (cw + gap)
            cards[k].set(l, d * 6f, l + cw, h - hintH)
        }
        headPaint.textSize = minOf(d * 15f, cw * 0.16f)
        totalPaint.textSize = minOf(d * 13f, cw * 0.14f)
        linePaint.textSize = minOf(d * 12f, cw * 0.125f)
        amtPaint.textSize = linePaint.textSize
        hintPaint.textSize = d * 12.5f
        fitAll()
    }

    /** 이름을 카드 폭에 맞춰 줄임 (크기·사람이 바뀔 때만) */
    private fun fitAll() {
        val cw = cards[0].width()
        if (cw <= 0f || rawNames.isEmpty()) {
            fitNames = Array(rawNames.size) { rawNames[it] }
            return
        }
        val maxW = cw - d * 18f - amtPaint.measureText("100") - d * 6f
        fitNames = Array(rawNames.size) { i ->
            var s = rawNames[i]
            if (linePaint.measureText(s) > maxW) {
                while (s.length > 1 && linePaint.measureText(s + "…") > maxW) s = s.dropLast(1)
                s += "…"
            }
            s
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!touchEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downCard = cardAt(e.x, e.y)
                return downCard >= 0
            }
            MotionEvent.ACTION_UP -> {
                val k = cardAt(e.x, e.y)
                if (k >= 0 && k == downCard) {
                    performClick()
                    listener?.onHorseTap(k)
                }
                downCard = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> downCard = -1
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun cardAt(x: Float, y: Float): Int {
        for (k in 0 until GameConfig.HORSES) {
            val r = cards[k]
            if (x >= r.left - d * 3f && x <= r.right + d * 3f && y >= r.top - d * 6f && y <= r.bottom) return k
        }
        return -1
    }

    override fun onDraw(c: Canvas) {
        for (k in 0 until GameConfig.HORSES) drawCard(c, k)
        if (hint.isNotEmpty()) {
            hintPaint.color = if (hintWarn) Theme.RED else Theme.TEXT_SUB
            c.drawText(hint, width / 2f, height - d * 7f, hintPaint)
        }
    }

    private fun drawCard(c: Canvas, k: Int) {
        val sel = k == selected
        val up = if (sel) lift * d * 5f else 0f
        val r = cards[k]
        val rad = d * 12f
        c.save()
        c.translate(0f, -up)
        // 바탕
        rect.set(r)
        fill.color = if (sel) Theme.CARD_LIGHT else Theme.CARD
        c.drawRoundRect(rect, rad, rad, fill)
        // 번호 색 띠
        val bandH = headPaint.textSize * 1.9f
        val silk = HorsePainter.SILK[k]
        fill.color = silk
        c.save()
        c.clipRect(r.left, r.top, r.right, r.top + bandH)
        c.drawRoundRect(rect, rad, rad, fill)
        c.restore()
        headPaint.color = HorsePainter.SILK_TEXT[k]
        c.drawText(HEADS[k], r.centerX(), r.top + bandH * 0.5f + headPaint.textSize * 0.36f, headPaint)
        // 말 그림
        val s = minOf(r.width() * 0.7f, (r.height() - bandH) * 0.42f)
        val hy = r.top + bandH + s * 0.62f
        painter.draw(c, r.centerX() - s * 0.03f, hy, s, k, 0f, false)
        // 총액
        var y = hy + s * 0.55f + totalPaint.textSize * 1.1f
        totalPaint.color = if (hasTotal[k]) Theme.GOLD else Theme.TEXT_SUB
        c.drawText(totalText[k], r.centerX(), y, totalPaint)
        // 건 사람 줄
        val lh = linePaint.textSize * 1.45f
        y += lh * 0.25f
        for (i in 0 until lineCount[k]) {
            y += lh
            if (y > r.bottom - d * 4f) break
            val p = linePlayer[k][i]
            fill.color = colors[p]
            c.drawCircle(r.left + d * 9f, y - linePaint.textSize * 0.35f, d * 4f, fill)
            linePaint.color = if (p == current) Theme.GOLD_LIGHT else Theme.TEXT
            c.drawText(fitNames[p], r.left + d * 16f, y, linePaint)
            amtPaint.color = if (p == current) Theme.GOLD else Theme.TEXT
            c.drawText(lineAmount[k][i], r.right - d * 6f, y, amtPaint)
        }
        // 테두리
        rect.set(r)
        if (sel) {
            stroke.color = Theme.GOLD
            stroke.strokeWidth = d * 3f
        } else {
            stroke.color = silk
            stroke.alpha = 150
            stroke.strokeWidth = d * 1.5f
        }
        c.drawRoundRect(rect, rad, rad, stroke)
        stroke.alpha = 255
        c.restore()
    }

    override fun onDetachedFromWindow() {
        liftAnim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val NONE = "아직 없음"
        private val HEADS = Array(GameConfig.HORSES) { (it + 1).toString() + "번 말" }
    }
}
