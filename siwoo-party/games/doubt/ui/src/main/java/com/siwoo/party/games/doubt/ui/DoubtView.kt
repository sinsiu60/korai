package com.siwoo.party.games.doubt.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.ui.Theme

/**
 * "의심!" 창 (화면 전체를 덮는 층). 플레이어마다 동시 터치 게임과 같은 구역(2인 좌/우, 3인 위 둘·아래, 4인 네 귀퉁이)을 나눠
 * 그 사람 쪽으로 돌려(위쪽 구역 180°) 같은 내용을 그린다:
 *   이름 · 선언(종이 카드 숫자 + "× N장!") · 큰 [의심!] 버튼 · 줄어드는 3초 막대
 * 낸 사람 구역은 "기다려요", 이미 끝난 사람 구역은 "구경 중". 낸 사람의 마지막 카드면 구역마다 "마지막 카드!".
 * 손가락 하나로 충분하다: 버튼을 먼저 누른 사람 한 명만 [onDoubt] (그 뒤 누름은 무시). 여러 손가락이 동시에 와도 먼저 온 것만.
 * 시간은 게임 시계([clock], 일시정지 제외)로 그리고, 열려 있을 때만 postOnAnimation 으로 다시 그린다. onDraw 할당 없음.
 */
class DoubtView(context: Context) : View(context) {

    /** 지금 시각 (게임 시계) — Activity 가 timers.now() 를 넣는다 */
    var clock: () -> Long = { android.os.SystemClock.uptimeMillis() }

    /** 나타나는 시간 (Activity 가 속도 배율 t() 를 적용해 넣는다) */
    var fadeMs = 160L

    /** 먼저 누른 사람 (구역 번호 = 플레이어 번호) */
    var onDoubt: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private var players: List<PlayerInfo> = emptyList()
    private var zones: List<Zone> = emptyList()
    private var role = IntArray(0)
    private var declarer = -1
    private var rankLabel = "A"
    private var countText = ""
    /** 마지막 카드였는지 (구역마다 "마지막 카드!" 표시) */
    private var lastCard = false
    private var openAt = 0L
    private var durMs = 1L
    /** 버튼을 누를 수 있는 동안 */
    var isOpen = false
        private set
    /** 의심한 사람 (-1 = 아직) */
    var winner = -1
        private set
    private var paused = false
    private var looping = false
    private val frame = Runnable { onFrame() }

    private val btn = Array(MAX) { RectF() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = Theme.INK }
    private val rect = RectF()

    init {
        visibility = GONE
        contentDescription = "의심 창"
    }

    /** 구역 역할 */
    fun roleOf(i: Int): Int = role.getOrElse(i) { ROLE_NONE }

    /**
     * 창 준비 + 열기. eligible[i] = i 가 의심할 수 있는지, declarer = 낸 사람.
     * 글자는 여기서 만든다.
     */
    fun open(list: List<PlayerInfo>, declarer: Int, rank: Int, count: Int, eligible: BooleanArray, windowMs: Long, lastCard: Boolean = false) {
        this.lastCard = lastCard
        players = list
        this.declarer = declarer
        role = IntArray(list.size) { i ->
            when {
                i == declarer -> ROLE_DECLARER
                eligible[i] -> ROLE_DOUBTER
                else -> ROLE_OUT
            }
        }
        rankLabel = Card.rankLabel(rank)
        countText = "× " + count + "장!"
        durMs = windowMs.coerceAtLeast(1L)
        openAt = clock()
        winner = -1
        isOpen = true
        visibility = VISIBLE
        relayout()
        startLoop()
        invalidate()
    }

    /** 시간이 다 됐거나 누가 눌렀을 때: 더 이상 누를 수 없음 (그림은 남김) */
    fun lock() {
        isOpen = false
        stopLoop()
        invalidate()
    }

    /** 창 닫기 */
    fun close() {
        isOpen = false
        winner = -1
        stopLoop()
        visibility = GONE
    }

    fun pause() {
        paused = true
        stopLoop()
    }

    fun resume() {
        paused = false
        if (isOpen) startLoop()
        invalidate()
    }

    /** i 번 [의심!] 버튼 가운데 (이 View 좌표) — 테스트·연출용 */
    fun buttonCenter(i: Int, out: PointF): Boolean {
        val z = zones.getOrNull(i) ?: return false
        val r = btn[i]
        if (z.rotation == 180f) out.set(2f * z.centerX - r.centerX(), 2f * z.centerY - r.centerY())
        else out.set(r.centerX(), r.centerY())
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || players.isEmpty()) return
        zones = TouchZones.layout(players.size, width.toFloat(), height.toFloat(), 10f * density)
        // 버튼: 구역 가운데에서 그 사람 쪽(아래)으로 조금 (회전 전 좌표)
        for (z in zones) {
            val bw = minOf(z.width * 0.62f, 190f * density)
            val bh = minOf(z.height * 0.3f, 60f * density)
            val cy = z.centerY + contentShift(z) + 14f * density
            btn[z.index].set(z.centerX - bw / 2f, cy - bh / 2f, z.centerX + bw / 2f, cy + bh / 2f)
        }
        rankPaint.textSize = 26f * density
    }

    /** 내용(선언·버튼·막대)을 구역 안에서 세로 가운데에 놓기 위한 이동 */
    private fun contentShift(z: Zone): Float = if (z.height > 220f * density) 30f * density else 12f * density

    // ── 터치: 먼저 누른 한 명 ──

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        val m = e.actionMasked
        if (m == MotionEvent.ACTION_DOWN || m == MotionEvent.ACTION_POINTER_DOWN) {
            val idx = e.actionIndex
            press(e.getX(idx), e.getY(idx))
        }
        return true // 창이 떠 있는 동안 아래 테이블은 누를 수 없음
    }

    private fun press(x: Float, y: Float) {
        if (!isOpen || paused || winner >= 0) return
        val zi = TouchZones.zoneAt(zones, x, y)
        if (zi < 0 || role[zi] != ROLE_DOUBTER) return
        val z = zones[zi]
        // 회전을 풀어 버튼 좌표로
        val lx = if (z.rotation == 180f) 2f * z.centerX - x else x
        val ly = if (z.rotation == 180f) 2f * z.centerY - y else y
        val r = btn[zi]
        val slop = 14f * density
        if (lx < r.left - slop || lx > r.right + slop || ly < r.top - slop || ly > r.bottom + slop) return
        winner = zi
        lock()
        onDoubt?.invoke(zi)
    }

    // ── 그리기 루프 (열려 있을 때만) ──

    private fun startLoop() {
        if (looping || paused) return
        looping = true
        postOnAnimation(frame)
    }

    private fun stopLoop() {
        looping = false
        removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!looping) return
        invalidate()
        if (isOpen && !paused) postOnAnimation(frame) else looping = false
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        super.onDetachedFromWindow()
    }

    /** 0..1 남은 시간 */
    private fun remaining(): Float {
        if (!isOpen) return if (winner >= 0) 1f else 0f
        val e = clock() - openAt
        return (1f - e.toFloat() / durMs).coerceIn(0f, 1f)
    }

    override fun onDraw(c: Canvas) {
        if (zones.isEmpty()) return
        val fade = ((clock() - openAt) / fadeMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        fill.color = Theme.BG_DEEP
        fill.alpha = (240 * fade).toInt()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.alpha = 255
        val left = remaining()
        val rr = 18f * density
        for (z in zones) {
            val i = z.index
            val color = Theme.playerColor(players[i].colorIndex)
            val win = winner == i
            val out = role[i] == ROLE_OUT || (winner >= 0 && !win)
            rect.set(z.left + 3f * density, z.top + 3f * density, z.right - 3f * density, z.bottom - 3f * density)
            fill.color = if (out) Theme.GRAY else color
            fill.alpha = if (win) 110 else if (role[i] == ROLE_DECLARER) 30 else 55
            c.drawRoundRect(rect, rr, rr, fill)
            fill.alpha = 255
            if (win) {
                stroke.color = Theme.GOLD
                for (k in 3 downTo 1) {
                    stroke.alpha = 70 * (4 - k)
                    stroke.strokeWidth = 4f * density * k
                    c.drawRoundRect(rect, rr, rr, stroke)
                }
                stroke.alpha = 255
            } else {
                stroke.color = if (out) Theme.GRAY else color
                stroke.strokeWidth = 4f * density
                c.drawRoundRect(rect, rr, rr, stroke)
            }
            c.save()
            c.rotate(z.rotation, z.centerX, z.centerY)
            drawZone(c, z, color, left, win)
            c.restore()
        }
    }

    /** 구역 하나 (회전 전 좌표: 그 사람 쪽이 아래) */
    private fun drawZone(c: Canvas, z: Zone, color: Int, left: Float, win: Boolean) {
        val i = z.index
        val b = btn[i]
        // 이름: 왼쪽 위
        text.textAlign = Paint.Align.LEFT
        text.textSize = 16f * density
        text.color = Theme.shade(color, 1.3f)
        c.drawText(players[i].name, z.left + 16f * density, z.top + 28f * density, text)
        if (lastCard) {
            // 오른쪽 위: 낸 사람의 마지막 카드
            text.textAlign = Paint.Align.RIGHT
            text.textSize = 14f * density
            text.color = Theme.RED
            c.drawText(LAST_CARD, z.right - 16f * density, z.top + 27f * density, text)
        }
        text.textAlign = Paint.Align.CENTER
        // 선언: 종이 카드 + "× N장!" (버튼 위)
        val declY = b.top - 34f * density
        val cw = 30f * density
        val chh = 42f * density
        text.textSize = 26f * density
        val tw = text.measureText(countText)
        val total = cw + 8f * density + tw
        val x0 = z.centerX - total / 2f
        rect.set(x0, declY - chh / 2f, x0 + cw, declY + chh / 2f)
        fill.color = Theme.PAPER
        c.drawRoundRect(rect, 5f * density, 5f * density, fill)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = 2f * density
        c.drawRoundRect(rect, 5f * density, 5f * density, stroke)
        rankPaint.textSize = (if (rankLabel.length > 1) 18f else 24f) * density
        c.drawText(rankLabel, rect.centerX(), rect.centerY() + rankPaint.textSize * 0.36f, rankPaint)
        text.color = Theme.GOLD
        text.textAlign = Paint.Align.LEFT
        c.drawText(countText, rect.right + 8f * density, declY + text.textSize * 0.36f, text)
        text.textAlign = Paint.Align.CENTER
        when (role[i]) {
            ROLE_DOUBTER -> drawButton(c, b, left, win)
            ROLE_DECLARER -> {
                text.textSize = 22f * density
                text.color = Theme.TEXT_SUB
                c.drawText(if (winner >= 0) CAUGHT_WAIT else WAIT, b.centerX(), b.centerY() + 8f * density, text)
                drawBar(c, b, left)
            }
            else -> {
                text.textSize = 20f * density
                text.color = Theme.GRAY
                c.drawText(WATCH, b.centerX(), b.centerY() + 7f * density, text)
            }
        }
    }

    private fun drawButton(c: Canvas, b: RectF, left: Float, win: Boolean) {
        val r = 16f * density
        val dim = winner >= 0 && !win
        val depth = 5f * density
        // 입체: 아래 그림자층 + 윗면
        rect.set(b.left, b.top + depth, b.right, b.bottom + depth)
        fill.color = if (win) Theme.GOLD_DARK else if (dim) 0xFF3A4066.toInt() else Theme.RED_DARK
        c.drawRoundRect(rect, r, r, fill)
        fill.color = if (win) Theme.GOLD else if (dim) Theme.GRAY else Theme.RED
        c.drawRoundRect(b, r, r, fill)
        stroke.color = if (win) Theme.GOLD_LIGHT else 0x66FFFFFF
        stroke.strokeWidth = 2f * density
        c.drawRoundRect(b, r, r, stroke)
        text.textSize = 28f * density
        text.color = if (win) Theme.INK else Theme.TEXT
        c.drawText(LABEL, b.centerX(), b.centerY() + 10f * density, text)
        if (!dim) drawBar(c, b, left)
    }

    /** 버튼 아래 줄어드는 시간 막대 */
    private fun drawBar(c: Canvas, b: RectF, left: Float) {
        val y = b.bottom + 16f * density
        val hh = 4f * density
        rect.set(b.left, y - hh, b.right, y + hh)
        fill.color = 0x44FFFFFF
        c.drawRoundRect(rect, hh, hh, fill)
        if (left > 0f) {
            rect.right = b.left + b.width() * left
            fill.color = if (left < 0.34f) Theme.RED else Theme.GOLD
            c.drawRoundRect(rect, hh, hh, fill)
        }
    }

    companion object {
        const val MAX = 4
        const val ROLE_NONE = 0
        const val ROLE_DOUBTER = 1
        const val ROLE_DECLARER = 2
        const val ROLE_OUT = 3
        const val LABEL = "의심!"
        const val WAIT = "기다려요…"
        const val CAUGHT_WAIT = "두근두근…"
        const val WATCH = "구경 중"
        const val LAST_CARD = "마지막 카드!"
    }
}
