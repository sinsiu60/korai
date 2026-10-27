package com.siwoo.party.games.speed.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.Zone
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.CardBitmaps
import com.siwoo.party.common.swipe.ZoneTouchInput
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.speed.GameConfig
import com.siwoo.party.games.speed.SpeedGame

/**
 * 스피드 카드 판 전체 (한 View). 두 사람이 폰의 긴 변 양쪽에 마주 앉는다.
 * - 아래 절반 = 0번 플레이어 구역(바로 보임), 위 절반 = 1번 플레이어 구역(180° 돌려 그 사람 쪽을 향함).
 *   각 구역: [이름·남은 장수] [뽑을 더미] [칸 4장] [다시] — 두 구역 배치는 화면 가운데를 중심으로 정확히 점대칭 (공정).
 * - 가운데 더미 2개: 가운데 0 은 0번 쪽에서 본 왼쪽(0번을 향해 바로), 가운데 1 은 1번 쪽에서 본 왼쪽(1번을 향해) → 서로 같은 조건.
 * - 터치: 공통 ZoneTouchInput (구역마다 손가락 하나, 구역 밖으로 끌어도 계속 추적) → 두 사람이 동시에 각자 끌기.
 *   자기 칸의 카드만 잡을 수 있고, 놓을 때 가운데 더미 근처면 listener.onDrop → 안 되면 제자리로 날아 돌아간다.
 *
 * 좌표: 각 플레이어의 "자기 좌표" = 그 사람이 보는 방향의 화면 전체 좌표 (0번 = 화면 좌표, 1번 = 화면 180° 회전).
 * 줄 배치는 자기 좌표에서 두 사람이 똑같고, 그릴 때 1번은 canvas 를 화면 가운데 기준 180° 돌려서 그린다.
 * onDraw 에서 할당 없음 (숫자 글자는 미리 만든 표, 카드 그림은 CardBitmaps 미리 그림).
 */
class SpeedTableView(context: Context, private val bitmaps: CardBitmaps) : View(context) {

    interface Listener {
        /** 지금 카드를 잡아도 되는지 (진행 중) */
        fun canDrag(p: Int): Boolean
        /** p 가 칸 i 의 카드를 가운데 k 에 놓음 → 냈으면 true (판 상태는 이미 바뀌어 있어야 함) */
        fun onDrop(p: Int, i: Int, k: Int): Boolean
        /** [다시] 버튼을 누름 */
        fun onAgain(p: Int)
    }

    /** 연출 시계 (일시정지 시간 제외) — 상자에 담지 않도록 fun interface */
    fun interface Clock { fun now(): Long }

    var listener: Listener? = null
    var clock: Clock = Clock { android.os.SystemClock.uptimeMillis() }
    /** 연출 시간 배율 (activity.t 와 같은 값) */
    var speed: Float = 1f

    private var game: SpeedGame? = null
    private val names = arrayOf("", "")
    private val colors = IntArray(2)

    /** 칸 카드를 앞면으로 보여 주는지 (시작 전엔 뒷면) */
    var revealed = false
        set(v) { field = v; invalidate() }

    /** 이긴 사람 (-1 없음) → 그 구역 금색 빛 */
    var winner = -1
        set(v) { field = v; invalidate() }

    /** 구역 터치 (2구역) */
    val input = ZoneTouchInput(2)

    // ── [다시] 버튼 상태 ──
    private val again = IntArray(2)
    private val messages = arrayOf("", "")

    // ── 배치 (자기 좌표, px) ──
    private var W = 0f
    private var H = 0f
    var cardW = 0f
        private set
    var cardH = 0f
        private set
    private var centerW = 0f
    private var centerH = 0f
    private var gap = 0f
    private var rowCy = 0f
    private val slotCx = FloatArray(GameConfig.SLOTS)
    private var pileCx = 0f
    /** 자기 좌표에서 "내 왼쪽" 가운데 더미 x / 오른쪽 x (y 는 H/2) */
    private var centerLeftX = 0f
    private var centerRightX = 0f
    private val btn = RectF()
    private val rowRect = RectF()
    private var nameCx = 0f
    private var nameW = 0f
    private var msgY = 0f
    private val nameSize = FloatArray(2)

    // ── 끌기 (플레이어별, 자기 좌표) ──
    private val dragSlot = intArrayOf(-1, -1)
    private val dragX = FloatArray(2)
    private val dragY = FloatArray(2)
    private val grabDx = FloatArray(2)
    private val grabDy = FloatArray(2)
    private val hover = intArrayOf(-1, -1)

    // ── 날아가는 카드 (고정 풀) ──
    private val flyOn = BooleanArray(MAX_FLY)
    private val flyCard = arrayOfNulls<Card>(MAX_FLY)
    private val flyFace = BooleanArray(MAX_FLY)
    /** 그 카드를 그릴 플레이어 좌표계 */
    private val flyFrame = IntArray(MAX_FLY)
    private val flyX0 = FloatArray(MAX_FLY)
    private val flyY0 = FloatArray(MAX_FLY)
    private val flyX1 = FloatArray(MAX_FLY)
    private val flyY1 = FloatArray(MAX_FLY)
    private val flyW0 = FloatArray(MAX_FLY)
    private val flyW1 = FloatArray(MAX_FLY)
    private val flyRot1 = FloatArray(MAX_FLY)
    private val flyStart = LongArray(MAX_FLY)
    private val flyDur = LongArray(MAX_FLY)
    /** 도착지: 0 없음, 1 칸(flyA=p, flyB=i), 2 가운데(flyA=k), 3 뽑을 더미(flyA=p) */
    private val flyKind = IntArray(MAX_FLY)
    private val flyA = IntArray(MAX_FLY)
    private val flyB = IntArray(MAX_FLY)

    // ── 그리기 도구 ──
    private val rect = RectF()
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val msgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val tmp = PointF()

    private var loopOn = false
    private val frame = Runnable { onFrame() }

    init {
        input.listener = object : ZoneTouchInput.Listener {
            override fun onDown(zone: Int, lx: Float, ly: Float, timeMs: Long) = down(zone, lx, ly)
            override fun onMove(zone: Int, lx: Float, ly: Float, timeMs: Long) = move(zone, lx, ly)
            override fun onUp(zone: Int, lx: Float, ly: Float, timeMs: Long, canceled: Boolean) = up(zone, lx, ly, canceled)
        }
    }

    fun setup(g: SpeedGame, playerNames: List<String>, playerColors: IntArray) {
        game = g
        for (p in 0..1) { names[p] = playerNames[p]; colors[p] = playerColors[p] }
        fitNames()
        invalidate()
    }

    /** 판을 바꿈 (테스트·다시 시작) */
    fun setGame(g: SpeedGame) { game = g; invalidate() }

    /** [다시] 버튼 상태 */
    fun setAgain(p: Int, state: Int) {
        if (again[p] == state) return
        again[p] = state
        invalidate()
    }

    fun againState(p: Int): Int = again[p]

    /** 구역 안내 문구 (가운데 더미와 자기 줄 사이, 그 사람 쪽을 향해) */
    fun setMessage(p: Int, text: String) {
        if (messages[p] == text) return
        messages[p] = text
        invalidate()
    }

    fun message(p: Int): String = messages[p]

    // ── 배치 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        W = w.toFloat(); H = h.toFloat()
        val m = H * 0.03f
        cardH = H * GameConfig.CARD_H_FRAC
        cardW = cardH / CardBitmaps.RATIO
        centerW = cardW * GameConfig.CENTER_SCALE
        centerH = centerW * CardBitmaps.RATIO
        gap = cardW * 0.16f
        rowCy = H - m - cardH / 2f
        val slotsW = GameConfig.SLOTS * cardW + (GameConfig.SLOTS - 1) * gap
        val left = W / 2f - slotsW / 2f
        for (i in 0 until GameConfig.SLOTS) slotCx[i] = left + cardW / 2f + i * (cardW + gap)
        pileCx = left - gap * 1.8f - cardW / 2f
        val bl = left + slotsW + gap * 1.8f
        val bw = minOf(cardW * 1.3f, W - m - bl)
        val bh = cardW * 0.66f
        btn.set(bl, rowCy - bh / 2f, bl + bw, rowCy + bh / 2f)
        val pileLeft = pileCx - cardW / 2f
        nameCx = (m + pileLeft - gap) / 2f
        nameW = pileLeft - gap - m
        rowRect.set(m * 0.5f, rowCy - cardH / 2f - m * 0.6f, W - m * 0.5f, H - m * 0.4f)
        val dx = centerW / 2f + gap * 1.6f
        centerLeftX = W / 2f - dx
        centerRightX = W / 2f + dx
        msgY = (H / 2f + centerH / 2f + rowRect.top) / 2f
        namePaint.textSize = Theme.dpf(context, 16f)
        subPaint.textSize = Theme.dpf(context, 13f)
        badgePaint.textSize = cardW * 0.24f
        btnPaint.textSize = bh * 0.4f
        msgPaint.textSize = Theme.dpf(context, 13f)
        stroke.strokeWidth = Theme.dpf(context, 3f)
        fitNames()
        // 구역: 아래 절반 = 0번(그대로), 위 절반 = 1번(180°)
        input.setZones(listOf(Zone(0, 0f, H / 2f, W, H, 0f), Zone(1, 0f, 0f, W, H / 2f, 180f)))
    }

    /** 긴 이름은 글자 크기를 줄여 이름 칸에 맞춘다 (값이 바뀔 때만) */
    private fun fitNames() {
        if (nameW <= 0f) return
        val base = Theme.dpf(context, 16f)
        for (p in 0..1) {
            namePaint.textSize = base
            val tw = namePaint.measureText(names[p])
            nameSize[p] = if (tw > nameW) (base * nameW / tw).coerceAtLeast(base * 0.55f) else base
        }
        namePaint.textSize = base
    }

    /** 자기 좌표의 가운데 더미 k x (p 쪽에서 볼 때) */
    private fun centerLocalX(p: Int, k: Int): Float = if (k == p) centerLeftX else centerRightX

    /** 화면 좌표 → p 의 자기 좌표 */
    private fun toLocalX(p: Int, sx: Float): Float = if (p == 0) sx else W - sx
    private fun toLocalY(p: Int, sy: Float): Float = if (p == 0) sy else H - sy
    private fun toScreenX(p: Int, lx: Float): Float = if (p == 0) lx else W - lx
    private fun toScreenY(p: Int, ly: Float): Float = if (p == 0) ly else H - ly

    // ── 테스트·연출용 위치 (화면 좌표) ──

    fun slotCenter(p: Int, i: Int, out: PointF) { out.set(toScreenX(p, slotCx[i]), toScreenY(p, rowCy)) }
    fun pileCenter(p: Int, out: PointF) { out.set(toScreenX(p, pileCx), toScreenY(p, rowCy)) }
    fun againCenter(p: Int, out: PointF) { out.set(toScreenX(p, btn.centerX()), toScreenY(p, btn.centerY())) }
    fun centerPileCenter(k: Int, out: PointF) { out.set(toScreenX(k, centerLeftX), H / 2f) }

    /** 끌고 있는 칸 (-1 없음) */
    fun dragSlot(p: Int): Int = dragSlot[p]
    /** 끌고 있는 카드 가운데 (화면 좌표) */
    fun dragCenter(p: Int, out: PointF) { out.set(toScreenX(p, dragX[p]), toScreenY(p, dragY[p])) }
    fun hoverTarget(p: Int): Int = hover[p]

    // ── 터치 ──

    override fun onTouchEvent(event: MotionEvent): Boolean = input.onTouchEvent(event)

    private fun localOf(zone: Int, lx: Float, ly: Float) {
        val f = input.frame(zone)
        val sx = f.toScreenX(lx, ly)
        val sy = f.toScreenY(lx, ly)
        tmp.set(toLocalX(zone, sx), toLocalY(zone, sy))
    }

    private fun down(p: Int, lx: Float, ly: Float) {
        val g = game ?: return
        localOf(p, lx, ly)
        val x = tmp.x; val y = tmp.y
        // [다시] 버튼 (조금 넉넉하게)
        if (x >= btn.left - gap && x <= btn.right + gap && y >= btn.top - gap && y <= btn.bottom + gap) {
            listener?.onAgain(p)
            return
        }
        if (listener?.canDrag(p) != true || !revealed) return
        val hw = cardW * 0.55f
        val hh = cardH * 0.55f
        for (i in 0 until GameConfig.SLOTS) {
            if (g.slot(p, i) == null) continue
            if (x < slotCx[i] - hw || x > slotCx[i] + hw || y < rowCy - hh || y > rowCy + hh) continue
            // 채우는 중이던 카드면 비행을 끝내고 바로 잡는다
            cancelFliesTo(1, p, i)
            dragSlot[p] = i
            grabDx[p] = slotCx[i] - x
            grabDy[p] = rowCy - y
            dragX[p] = slotCx[i]
            dragY[p] = rowCy
            hover[p] = -1
            invalidate()
            return
        }
    }

    private fun move(p: Int, lx: Float, ly: Float) {
        if (dragSlot[p] < 0) return
        localOf(p, lx, ly)
        dragX[p] = tmp.x + grabDx[p]
        dragY[p] = tmp.y + grabDy[p]
        hover[p] = targetAt(p, dragX[p], dragY[p])
        invalidate()
    }

    private fun up(p: Int, lx: Float, ly: Float, canceled: Boolean) {
        val i = dragSlot[p]
        if (i < 0) return
        localOf(p, lx, ly)
        dragX[p] = tmp.x + grabDx[p]
        dragY[p] = tmp.y + grabDy[p]
        val k = if (canceled) -1 else targetAt(p, dragX[p], dragY[p])
        dragSlot[p] = -1
        hover[p] = -1
        val g = game
        if (g != null && k >= 0 && listener?.onDrop(p, i, k) == true) {
            // 놓은 카드 → 가운데 k 로 쏙, 빈 칸은 더미에서 채움
            val placed = g.centerTop(k)
            fly(p, placed, true, dragX[p], dragY[p], cardW * GameConfig.DRAG_SCALE, centerLocalX(p, k), H / 2f, centerW,
                if (k == p) 0f else 180f, GameConfig.PLACE_MS, 2, k, 0, 0L)
            if (g.lastRefilled) refill(p, i, 0L)
        } else {
            snapBack(p, i)
        }
        invalidate()
    }

    /** 끌던 카드가 제자리로 날아 돌아감 */
    private fun snapBack(p: Int, i: Int) {
        val c = game?.slot(p, i) ?: return
        fly(p, c, true, dragX[p], dragY[p], cardW * GameConfig.DRAG_SCALE, slotCx[i], rowCy, cardW, 0f, GameConfig.SNAP_BACK_MS, 1, p, i, 0L)
    }

    /** 놓을 가운데 더미 (카드 가운데가 더미 근처면 가까운 쪽) */
    private fun targetAt(p: Int, x: Float, y: Float): Int {
        val rx = centerW * GameConfig.DROP_REACH
        val ry = centerH * GameConfig.DROP_REACH
        var best = -1
        var bestD = Float.MAX_VALUE
        for (k in 0 until GameConfig.CENTER_PILES) {
            val dx = x - centerLocalX(p, k)
            val dy = y - H / 2f
            if (dx > rx || dx < -rx || dy > ry || dy < -ry) continue
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = k }
        }
        return best
    }

    /** 모든 끌기를 취소하고 카드를 제자리로 (일시정지·판 끝) */
    fun cancelDrags() {
        input.cancelAll()
        for (p in 0..1) {
            val i = dragSlot[p]
            if (i < 0) continue
            dragSlot[p] = -1
            hover[p] = -1
            snapBack(p, i)
        }
        invalidate()
    }

    // ── 연출 ──

    /** 칸 i 를 자기 뽑을 더미에서 채우는 비행 (지금 칸에 있는 카드) */
    fun refill(p: Int, i: Int, delayMs: Long) {
        val c = game?.slot(p, i) ?: return
        fly(p, c, revealed, pileCx, rowCy, cardW, slotCx[i], rowCy, cardW, 0f, GameConfig.REFILL_MS, 1, p, i, delayMs)
    }

    /** 시작: 각자 더미에서 칸 4장을 나눠 놓는다 (뒷면) */
    fun dealAnimation() {
        for (p in 0..1) for (i in 0 until GameConfig.SLOTS) {
            val c = game?.slot(p, i) ?: continue
            fly(p, c, false, pileCx, rowCy, cardW, slotCx[i], rowCy, cardW, 0f, GameConfig.DEAL_FLIGHT_MS, 1, p, i,
                (GameConfig.DEAL_GAP_MS * i * speed).toLong())
        }
    }

    /** 가운데 더미 k 의 맨 위 카드를 p 쪽에서 뒤집어 보냄 (fromPile = 자기 더미에서, 아니면 다른 가운데 더미 밑에서) */
    fun flipToCenter(p: Int, k: Int, fromPile: Boolean, delayMs: Long) {
        val c = game?.centerTop(k) ?: return
        val x0 = if (fromPile) pileCx else centerLocalX(p, 1 - k)
        val y0 = if (fromPile) rowCy else H / 2f
        fly(p, c, true, x0, y0, cardW, centerLocalX(p, k), H / 2f, centerW, if (k == p) 0f else 180f, GameConfig.FLIP_MS, 2, k, 0, delayMs)
    }

    /** 날아가는 카드 하나 시작 (좌표는 frame 플레이어의 자기 좌표, 시간은 배율 전) */
    private fun fly(
        frame: Int, card: Card?, face: Boolean, x0: Float, y0: Float, w0: Float, x1: Float, y1: Float, w1: Float,
        rot1: Float, durMs: Long, kind: Int, a: Int, b: Int, delayMs: Long,
    ) {
        var slot = -1
        for (f in 0 until MAX_FLY) if (!flyOn[f]) { slot = f; break }
        if (slot < 0) return
        flyOn[slot] = true
        flyCard[slot] = card
        flyFace[slot] = face
        flyFrame[slot] = frame
        flyX0[slot] = x0; flyY0[slot] = y0; flyX1[slot] = x1; flyY1[slot] = y1
        flyW0[slot] = w0; flyW1[slot] = w1
        flyRot1[slot] = rot1
        flyStart[slot] = clock.now() + delayMs
        flyDur[slot] = (durMs * speed).toLong().coerceAtLeast(1L)
        flyKind[slot] = kind; flyA[slot] = a; flyB[slot] = b
        ensureLoop()
        invalidate()
    }

    private fun cancelFliesTo(kind: Int, a: Int, b: Int) {
        for (f in 0 until MAX_FLY) if (flyOn[f] && flyKind[f] == kind && flyA[f] == a && flyB[f] == b) {
            flyOn[f] = false
            flyCard[f] = null
        }
    }

    /** 칸으로 날아오는 중이면 그 칸은 비워 그린다 */
    private fun slotHidden(p: Int, i: Int): Boolean {
        for (f in 0 until MAX_FLY) if (flyOn[f] && flyKind[f] == 1 && flyA[f] == p && flyB[f] == i) return true
        return false
    }

    /** 가운데 k 로 날아오는 카드 수 (그만큼 아래 카드를 보여 줌) */
    private fun centerCover(k: Int): Int {
        var n = 0
        for (f in 0 until MAX_FLY) if (flyOn[f] && flyKind[f] == 2 && flyA[f] == k) n++
        return n
    }

    val isAnimating: Boolean
        get() {
            for (f in 0 until MAX_FLY) if (flyOn[f]) return true
            return false
        }

    fun ensureLoop() {
        if (loopOn) return
        loopOn = true
        postOnAnimation(frame)
    }

    fun stopLoop() {
        loopOn = false
        removeCallbacks(frame)
    }

    /** 테스트용: 프레임 루프가 도는지 */
    val looping: Boolean get() = loopOn

    private fun onFrame() {
        if (!loopOn) return
        val now = clock.now()
        var any = false
        for (f in 0 until MAX_FLY) {
            if (!flyOn[f]) continue
            if (now >= flyStart[f] + flyDur[f]) { flyOn[f] = false; flyCard[f] = null } else any = true
        }
        invalidate()
        if (any) postOnAnimation(frame) else loopOn = false
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        if (W <= 0f) return
        // 1) 구역 바탕 + 가운데 더미 + 줄 (플레이어별 자기 좌표)
        for (p in 0..1) {
            c.save()
            if (p == 1) c.rotate(180f, W / 2f, H / 2f)
            drawZone(c, g, p)
            c.restore()
        }
        // 2) 날아가는 카드
        val now = clock.now()
        for (f in 0 until MAX_FLY) {
            if (!flyOn[f]) continue
            val p = flyFrame[f]
            var e = if (now <= flyStart[f]) 0f else ((now - flyStart[f]).toFloat() / flyDur[f]).coerceAtMost(1f)
            if (now < flyStart[f]) {
                // 아직 출발 전: 출발 자리에 그대로 (나눠 주기 대기 카드는 그리지 않음)
                continue
            }
            e = 1f - (1f - e) * (1f - e) * (1f - e)
            c.save()
            if (p == 1) c.rotate(180f, W / 2f, H / 2f)
            val x = flyX0[f] + (flyX1[f] - flyX0[f]) * e
            val y = flyY0[f] + (flyY1[f] - flyY0[f]) * e
            val w = flyW0[f] + (flyW1[f] - flyW0[f]) * e
            drawCard(c, flyCard[f], flyFace[f], x, y, w, flyRot1[f] * e, true)
            c.restore()
        }
        // 3) 끄는 카드 (맨 위)
        for (p in 0..1) {
            val i = dragSlot[p]
            if (i < 0) continue
            c.save()
            if (p == 1) c.rotate(180f, W / 2f, H / 2f)
            drawCard(c, g.slot(p, i), true, dragX[p], dragY[p], cardW * GameConfig.DRAG_SCALE, 0f, true)
            c.restore()
        }
    }

    /** p 의 자기 좌표로 구역 하나 */
    private fun drawZone(c: Canvas, g: SpeedGame, p: Int) {
        val color = colors[p]
        val r = cardW * 0.18f
        // 줄 바탕
        fill.style = Paint.Style.FILL
        fill.color = color
        fill.alpha = 34
        c.drawRoundRect(rowRect, r, r, fill)
        if (winner == p) {
            stroke.color = Theme.GOLD
            val sw = stroke.strokeWidth
            for (k in 3 downTo 1) {
                stroke.alpha = 70 * (4 - k)
                stroke.strokeWidth = sw * k
                c.drawRoundRect(rowRect, r, r, stroke)
            }
            stroke.alpha = 255
            stroke.strokeWidth = sw
        } else {
            stroke.color = color
            stroke.alpha = 150
            c.drawRoundRect(rowRect, r, r, stroke)
            stroke.alpha = 255
        }

        // 가운데 더미 p (내 쪽에서 본 왼쪽, 나를 향해 바로) — 시작 전엔 빈 자리
        if (revealed) drawCenterPile(c, g, p) else drawEmpty(c, centerLeftX, H / 2f, centerW)

        // 이름 + 남은 장수
        namePaint.color = color
        namePaint.textSize = nameSize[p]
        c.drawText(names[p], nameCx, rowCy - Theme.dpf(context, 4f), namePaint)
        val left = g.cardsLeft(p)
        c.drawText(LEFT_LABELS[left.coerceIn(0, Card.STANDARD_COUNT)], nameCx, rowCy + subPaint.textSize * 1.2f, subPaint)

        // 뽑을 더미 (뒷면 몇 겹 + 장수)
        val n = g.drawSize(p)
        if (n > 0) {
            val layers = minOf(3, (n + 6) / 7)
            val off = cardW * 0.035f
            for (k in layers - 1 downTo 0) drawCard(c, null, false, pileCx - off * k, rowCy - off * k, cardW, 0f, k == layers - 1)
            val bx = pileCx
            val by = rowCy + cardH * 0.3f
            rect.set(bx - cardW * 0.26f, by - cardW * 0.17f, bx + cardW * 0.26f, by + cardW * 0.15f)
            fill.color = Theme.GOLD
            fill.alpha = 255
            c.drawRoundRect(rect, cardW * 0.12f, cardW * 0.12f, fill)
            c.drawText(NUMBERS[n.coerceAtMost(Card.STANDARD_COUNT)], bx, by + badgePaint.textSize * 0.36f, badgePaint)
        } else {
            drawEmpty(c, pileCx, rowCy, cardW)
        }

        // 칸 4장
        for (i in 0 until GameConfig.SLOTS) {
            val card = g.slot(p, i)
            if (card == null || dragSlot[p] == i || slotHidden(p, i)) {
                drawEmpty(c, slotCx[i], rowCy, cardW)
                continue
            }
            drawCard(c, card, revealed, slotCx[i], rowCy, cardW, 0f, true)
        }

        // [다시] 버튼
        drawButton(c, p)

        // 안내 문구
        val msg = messages[p]
        if (msg.isNotEmpty()) c.drawText(msg, W / 2f, msgY + msgPaint.textSize * 0.35f, msgPaint)
    }

    private fun drawCenterPile(c: Canvas, g: SpeedGame, k: Int) {
        val x = centerLeftX
        val y = H / 2f
        // 끄는 카드가 이 더미 위에 있으면 테두리
        var ring = -1
        for (p in 0..1) if (hover[p] == k && dragSlot[p] >= 0) ring = p
        val cover = centerCover(k)
        val size = g.centerSize(k)
        val visible = size - cover
        if (visible >= 2) {
            // 밑에 깔린 카드 가장자리 (살짝 비뚤게)
            c.save()
            c.rotate(4f, x, y)
            drawCard(c, g.centerAt(k, cover + 1), true, x, y, centerW, 0f, true)
            c.restore()
        }
        if (visible >= 1) drawCard(c, g.centerAt(k, cover), true, x, y, centerW, 0f, visible < 2)
        else drawEmpty(c, x, y, centerW)
        if (ring >= 0) {
            val hw = centerW / 2f + gap * 0.5f
            val hh = centerH / 2f + gap * 0.5f
            rect.set(x - hw, y - hh, x + hw, y + hh)
            stroke.color = colors[ring]
            c.drawRoundRect(rect, gap, gap, stroke)
        }
    }

    private fun drawButton(c: Canvas, p: Int) {
        val st = again[p]
        val base = when (st) { AGAIN_LIT -> Theme.GOLD; AGAIN_WAIT -> Theme.GREEN; else -> Theme.GRAY }
        val depth = btn.height() * 0.1f
        val r = btn.height() * 0.3f
        if (st == AGAIN_LIT) {
            // 금색 빛
            stroke.color = Theme.GOLD
            for (k in 3 downTo 1) {
                stroke.alpha = 50 * (4 - k)
                val o = gap * 0.25f * k
                rect.set(btn.left - o, btn.top - o, btn.right + o, btn.bottom + o)
                c.drawRoundRect(rect, r + o, r + o, stroke)
            }
            stroke.alpha = 255
        }
        fill.color = Theme.shade(base, 0.55f)
        fill.alpha = if (st == AGAIN_OFF) 150 else 255
        rect.set(btn.left, btn.top + depth, btn.right, btn.bottom)
        c.drawRoundRect(rect, r, r, fill)
        fill.color = base
        fill.alpha = if (st == AGAIN_OFF) 150 else 255
        val press = if (st == AGAIN_WAIT) depth else 0f
        rect.set(btn.left, btn.top + press, btn.right, btn.bottom - depth + press)
        c.drawRoundRect(rect, r, r, fill)
        fill.color = 0x30FFFFFF
        rect.set(btn.left + r * 0.5f, btn.top + press + depth * 0.6f, btn.right - r * 0.5f, btn.top + press + (btn.height() - depth) * 0.42f)
        c.drawRoundRect(rect, r * 0.7f, r * 0.7f, fill)
        btnPaint.color = if (st == AGAIN_LIT) Theme.INK else 0xFFFFFFFF.toInt()
        btnPaint.alpha = if (st == AGAIN_OFF) 170 else 255
        val label = if (st == AGAIN_WAIT) LABEL_WAIT else LABEL_AGAIN
        c.drawText(label, btn.centerX(), btn.centerY() - depth * 0.5f + press + btnPaint.textSize * 0.36f, btnPaint)
    }

    /** 빈 자리: 옅은 테두리 */
    private fun drawEmpty(c: Canvas, x: Float, y: Float, w: Float) {
        val h = w * CardBitmaps.RATIO
        rect.set(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f)
        fill.color = 0x22000000
        fill.alpha = 0x22
        c.drawRoundRect(rect, w * CardBitmaps.CORNER, w * CardBitmaps.CORNER, fill)
        stroke.color = 0x40FFFFFF
        val sw = stroke.strokeWidth
        stroke.strokeWidth = sw * 0.5f
        c.drawRoundRect(rect, w * CardBitmaps.CORNER, w * CardBitmaps.CORNER, stroke)
        stroke.strokeWidth = sw
        stroke.alpha = 255
    }

    /** 카드 한 장: 가운데 (x, y), 폭 w, rot 도. card 가 null 이거나 face=false 면 뒷면 */
    private fun drawCard(c: Canvas, card: Card?, face: Boolean, x: Float, y: Float, w: Float, rot: Float, shadow: Boolean) {
        val h = w * CardBitmaps.RATIO
        c.save()
        if (rot != 0f) c.rotate(rot, x, y)
        val cr = w * CardBitmaps.CORNER
        if (shadow) {
            fill.color = 0x55000000
            fill.alpha = 0x55
            val o = w * 0.04f
            rect.set(x - w / 2f + o, y - h / 2f + o * 1.6f, x + w / 2f + o, y + h / 2f + o * 1.6f)
            c.drawRoundRect(rect, cr, cr, fill)
        }
        rect.set(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f)
        val bmp = if (card != null && face) bitmaps.face(card) else bitmaps.back()
        c.drawBitmap(bmp, null, rect, bmpPaint)
        c.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
    }

    companion object {
        const val MAX_FLY = 16
        const val AGAIN_OFF = 0
        const val AGAIN_LIT = 1
        const val AGAIN_WAIT = 2
        private const val LABEL_AGAIN = "다시"
        private const val LABEL_WAIT = "대기…"
        /** 0..52 숫자 글자 (onDraw 에서 문자열을 만들지 않도록) */
        private val NUMBERS = Array(Card.STANDARD_COUNT + 1) { it.toString() }
        private val LEFT_LABELS = Array(Card.STANDARD_COUNT + 1) { "남은 ${it}장" }
    }
}
