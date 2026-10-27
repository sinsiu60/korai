package com.siwoo.party.games.yut.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.yut.GameConfig
import com.siwoo.party.games.yut.PieceState
import com.siwoo.party.games.yut.YutGame

/**
 * 화면 왼쪽 플레이어 패널 (사람마다 카드 한 장).
 * - 색 동그라미(첫 글자) + 이름 + 오른쪽 "골인 n/4" (다 골인하면 순위 도장)
 * - 아래 줄: 말 자리 — 대기 중인 말은 색 원 + 번호, 판 위에 나간 말은 빈 고리, 골인한 말은 금색 체크
 * - 지금 차례 카드는 밝은 바탕 + 금색 테두리 (반복 애니메이션 없이)
 * - 새 말을 올릴 수 있을 때 지금 차례 사람의 대기 말에 금색 고리, 고른 말은 위로 살짝 떠오름 → 누르면 listener
 * onDraw 에서는 객체를 만들지 않는다 (글자는 sync 때 바뀐 것만 만든다).
 */
class PanelView(context: Context) : View(context) {

    interface Listener {
        fun onWaitingTapped(player: Int, piece: Int)
    }

    var listener: Listener? = null
    var timers: GameTimers? = null

    private var n = 0
    private var pieces = 0
    private var names = emptyArray<String>()
    private var initials = emptyArray<String>()
    private var colors = IntArray(0)
    private var darks = IntArray(0)
    private var textColors = IntArray(0)
    private val state = Array(GameConfig.MAX_PLAYERS) { arrayOfNulls<PieceState>(GameConfig.PIECES_MAX) }
    private val goals = IntArray(GameConfig.MAX_PLAYERS) { -1 }
    private val goalText = Array(GameConfig.MAX_PLAYERS) { "" }
    private val rankText = Array(GameConfig.MAX_PLAYERS) { "" }
    private val flashStart = LongArray(GameConfig.MAX_PLAYERS) { -100000L }

    var current = -1
        private set
    private val selectable = BooleanArray(GameConfig.PIECES_MAX)
    var selectedWaiting = -1
        private set

    // ── 배치 ──
    private val cards = Array(GameConfig.MAX_PLAYERS) { RectF() }
    private var slotR = 0f
    private var nameSize = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD; textAlign = Paint.Align.RIGHT }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }

    private var looping = false
    private val frame = Runnable { onFrame() }

    init {
        isClickable = true
        contentDescription = "플레이어 패널"
    }

    private fun now(): Long = timers?.now() ?: SystemClock.uptimeMillis()

    fun setup(playerNames: List<String>, playerColors: IntArray, pieceCount: Int) {
        n = playerNames.size
        pieces = pieceCount
        names = playerNames.toTypedArray()
        initials = Array(n) { playerNames[it].take(1) }
        colors = playerColors
        darks = IntArray(n) { Theme.shade(playerColors[it], 0.45f) }
        textColors = IntArray(n) { Theme.textOn(playerColors[it]) }
        layoutCards()
        invalidate()
    }

    /** 게임 상태 반영 (말 상태·골인 수·순위) */
    fun sync(game: YutGame) {
        for (p in 0 until n) {
            for (k in 0 until pieces) state[p][k] = game.state(p, k)
            val g = game.goalCount(p)
            if (goals[p] != g) {
                goals[p] = g
                goalText[p] = GOAL_PREFIX + g + "/" + pieces
            }
            val r = game.finishRank[p]
            rankText[p] = if (r > 0) RANKS[r] else ""
        }
        invalidate()
    }

    /** 연출 중 잠깐 다르게 보이기 (날아가는 말이 패널에 닿기 전까지는 빈 자리로) */
    fun override(p: Int, k: Int, s: PieceState) {
        state[p][k] = s
        invalidate()
    }

    /** 패널에 보이는 말 상태 (테스트용) */
    fun stateShown(p: Int, k: Int): PieceState? = state[p][k]

    fun setCurrent(p: Int) {
        if (current == p) return
        current = p
        invalidate()
    }

    /** 지금 차례 사람의 대기 말 중 고를 수 있는 것 (null = 없음) */
    fun setSelectable(ks: BooleanArray?) {
        for (k in selectable.indices) selectable[k] = ks != null && ks[k]
        invalidate()
    }

    fun setSelectedWaiting(k: Int) {
        if (selectedWaiting == k) return
        selectedWaiting = k
        invalidate()
    }

    /** 카드가 반짝 (골인·잡혀 돌아옴) */
    fun flash(p: Int) {
        flashStart[p] = now()
        ensureRunning()
    }

    /** 말 자리 가운데 (이 뷰 좌표) */
    fun slotCenter(p: Int, k: Int, out: PointF) {
        val c = cards[p]
        val pad = c.height() * 0.1f
        val x = c.left + pad + slotR * 1.1f + k * slotR * 2.45f
        val y = c.bottom - pad - slotR * 1.05f
        out.set(x, y)
    }

    /** 골인 글자 쪽 (이 뷰 좌표) */
    fun goalCenter(p: Int, out: PointF) {
        val c = cards[p]
        out.set(c.right - c.width() * 0.2f, c.top + c.height() * 0.3f)
    }

    val slotRadius: Float get() = slotR

    private fun ensureRunning() {
        invalidate()
        if (looping || timers?.paused == true) return
        looping = true
        postOnAnimation(frame)
    }

    private fun onFrame() {
        if (!looping) return
        if (timers?.paused == true) { looping = false; return }
        invalidate()
        val t = now()
        var any = false
        for (p in 0 until n) if (t - flashStart[p] < FLASH_MS) any = true
        if (any) postOnAnimation(frame) else looping = false
    }

    fun pauseAnims() {
        looping = false
        removeCallbacks(frame)
    }

    fun resumeAnims() {
        val t = now()
        for (p in 0 until n) if (t - flashStart[p] < FLASH_MS) { ensureRunning(); return }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutCards()
    }

    private fun layoutCards() {
        if (width <= 0 || n == 0) return
        val d = Theme.dpf(context, 1f)
        val gap = d * 6
        val ch = minOf((height - gap * (n - 1)) / n, d * 96)
        val total = ch * n + gap * (n - 1)
        val top = (height - total) / 2f
        for (p in 0 until n) cards[p].set(d * 2, top + p * (ch + gap), width - d * 2, top + p * (ch + gap) + ch)
        slotR = minOf(ch * 0.17f, (width - d * 4 - ch * 0.2f) / (pieces * 2.45f + 0.2f), d * 12)
        nameSize = minOf(ch * 0.2f, d * 15)
        initPaint.textSize = nameSize * 0.9f
        goalPaint.textSize = minOf(ch * 0.16f, d * 12)
        stampPaint.textSize = minOf(ch * 0.24f, d * 18)
        numPaint.textSize = slotR * 1.05f
    }

    override fun onDraw(c: Canvas) {
        if (n == 0 || slotR <= 0f) return
        val d = Theme.dpf(context, 1f)
        val t = now()
        for (p in 0 until n) {
            val card = cards[p]
            val cur = p == current
            val r = card.height() * 0.16f
            fill.color = if (cur) Theme.CARD_LIGHT else Theme.CARD
            c.drawRoundRect(card, r, r, fill)
            val ft = t - flashStart[p]
            if (ft in 0 until FLASH_MS) {
                fill.color = Theme.GOLD_LIGHT
                fill.alpha = (110 * (1f - ft.toFloat() / FLASH_MS)).toInt()
                c.drawRoundRect(card, r, r, fill)
                fill.alpha = 255
            }
            if (cur) {
                stroke.color = Theme.GOLD
                stroke.strokeWidth = d * 3f
                // 왼쪽 차례 표시 삼각 대신 굵은 금색 막대
                fill.color = Theme.GOLD
                c.drawRoundRect(card.left + d * 1.5f, card.top + card.height() * 0.22f, card.left + d * 5f, card.bottom - card.height() * 0.22f, d * 2, d * 2, fill)
            } else {
                stroke.color = colors[p]
                stroke.alpha = 150
                stroke.strokeWidth = d * 1.5f
            }
            c.drawRoundRect(card, r, r, stroke)
            stroke.alpha = 255

            // 위 줄: 색 동그라미 + 이름 + 골인 수
            val pad = card.height() * 0.1f
            val cr = nameSize * 0.78f
            val cx = card.left + pad + cr + d * 3
            val cy = card.top + pad + cr
            fill.color = colors[p]
            c.drawCircle(cx, cy, cr, fill)
            initPaint.color = textColors[p]
            c.drawText(initials[p], cx, cy + initPaint.textSize * 0.36f, initPaint)
            namePaint.textSize = nameSize
            val goalW = goalPaint.measureText(goalText[p])
            val nameMax = card.right - pad - goalW - d * 6 - (cx + cr + d * 5)
            // 긴 이름은 글자를 줄여서
            while (namePaint.measureText(names[p]) > nameMax && namePaint.textSize > nameSize * 0.6f) namePaint.textSize *= 0.92f
            c.drawText(names[p], cx + cr + d * 5, cy + namePaint.textSize * 0.36f, namePaint)
            c.drawText(goalText[p], card.right - pad, cy + goalPaint.textSize * 0.36f, goalPaint)

            // 아래 줄: 말 자리
            for (k in 0 until pieces) {
                val x = card.left + pad + slotR * 1.1f + k * slotR * 2.45f
                var y = card.bottom - pad - slotR * 1.05f
                when (state[p][k]) {
                    PieceState.WAITING -> {
                        val can = cur && selectable[k]
                        if (cur && k == selectedWaiting) y -= slotR * 0.35f
                        if (can) {
                            fill.color = Theme.GOLD
                            fill.alpha = 90
                            c.drawCircle(x, y, slotR * 1.38f, fill)
                            fill.alpha = 255
                        }
                        numPaint.color = textColors[p]
                        YutArt.drawPiece(c, x, y, slotR, colors[p], darks[p], NUMS[k], fill, numPaint)
                        if (cur && k == selectedWaiting) {
                            stroke.color = Theme.GOLD
                            stroke.strokeWidth = slotR * 0.24f
                            c.drawCircle(x, y, slotR * 1.3f, stroke)
                        }
                    }
                    PieceState.BOARD -> {
                        // 판 위에 나감: 빈 자리
                        stroke.color = colors[p]
                        stroke.alpha = 120
                        stroke.strokeWidth = slotR * 0.14f
                        c.drawCircle(x, y, slotR * 0.8f, stroke)
                        stroke.alpha = 255
                    }
                    PieceState.DONE -> {
                        fill.color = Theme.GOLD_DARK
                        c.drawCircle(x, y + slotR * 0.1f, slotR * 0.85f, fill)
                        fill.color = Theme.GOLD
                        c.drawCircle(x, y, slotR * 0.85f, fill)
                        stroke.color = YutArt.INK
                        stroke.strokeWidth = slotR * 0.2f
                        c.drawLine(x - slotR * 0.38f, y, x - slotR * 0.08f, y + slotR * 0.32f, stroke)
                        c.drawLine(x - slotR * 0.08f, y + slotR * 0.32f, x + slotR * 0.42f, y - slotR * 0.3f, stroke)
                    }
                    else -> Unit
                }
            }

            // 순위 도장 (다 골인)
            if (rankText[p].isNotEmpty()) {
                val sx = card.right - pad - stampPaint.textSize * 1.1f
                val sy = card.bottom - pad - slotR
                c.save()
                c.rotate(-12f, sx, sy)
                stroke.color = Theme.GOLD
                stroke.strokeWidth = d * 2f
                c.drawCircle(sx, sy, stampPaint.textSize * 1.0f, stroke)
                c.drawText(rankText[p], sx, sy + stampPaint.textSize * 0.36f, stampPaint)
                c.restore()
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
            val p = current
            if (p in 0 until n) {
                for (k in 0 until pieces) {
                    if (state[p][k] != PieceState.WAITING) continue
                    val x = cards[p].left + cards[p].height() * 0.1f + slotR * 1.1f + k * slotR * 2.45f
                    val y = cards[p].bottom - cards[p].height() * 0.1f - slotR * 1.05f
                    val dx = e.x - x
                    val dy = e.y - y
                    if (dx * dx + dy * dy <= slotR * slotR * 2.6f) { listener?.onWaitingTapped(p, k); break }
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val FLASH_MS = 600L
        private const val GOAL_PREFIX = "골인 "
        private val NUMS = arrayOf("1", "2", "3", "4")
        private val RANKS = arrayOf("", "1위", "2위", "3위", "4위")
    }
}
