package com.siwoo.marble.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.siwoo.marble.GameConfig
import com.siwoo.marble.data.BoardData
import com.siwoo.marble.logic.Money
import com.siwoo.marble.model.GameState
import com.siwoo.marble.model.Tile
import com.siwoo.marble.model.TileType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 보드 + 말 + 소유 깃발 + 건물 + 발광 + 주사위를 그리는 커스텀 View.
 *
 * 저사양 최적화:
 *  - 칸 배경·이름·가격·색 띠·모서리 그림은 크기가 정해질 때 RGB_565 비트맵 한 장으로 미리 그린다.
 *  - 매 프레임에는 캐시 비트맵 위에 깃발·건물·발광·말·주사위만 그린다.
 *  - 발광은 블러 없이 RadialGradient 셰이더를 한 번 만들어 두고 위치(Matrix)와 알파만 바꾼다.
 *  - onDraw 안에서 객체를 만들지 않는다. 화면 갱신은 애니메이션이 진행 중일 때만.
 *
 * 화면에 보이는 소유 상태(dispOwner/dispLevel)는 GameState 와 따로 두어
 * 연출 순서대로(코인 도착 → 깃발) 바뀌게 한다.
 */
class BoardView(context: Context) : View(context) {

    interface Listener {
        fun onTileTapped(index: Int)
        fun onDiceTapped()
    }

    var listener: Listener? = null

    private var state: GameState? = null
    private val tiles: Array<Tile> = BoardData.tiles
    private val playerColors = IntArray(GameConfig.MAX_PLAYERS)

    // ── 기하 ──
    private var side = 0f
    private var u = 0f
    private var depth = 0f
    private var band = 0f
    private val tileRects = Array(GameConfig.BOARD_SIZE) { RectF() }
    private val tileSides = IntArray(GameConfig.BOARD_SIZE)
    private var cx = 0f
    private var cy = 0f
    private val diceRect = RectF()

    private var boardBitmap: Bitmap? = null

    // ── 화면에 보이는 소유 상태 ──
    private val dispOwner = IntArray(GameConfig.BOARD_SIZE) { -1 }
    private val dispLevel = IntArray(GameConfig.BOARD_SIZE)

    // ── 말 ──
    private val pieceTile = IntArray(GameConfig.MAX_PLAYERS)
    private val pieceHidden = BooleanArray(GameConfig.MAX_PLAYERS)
    private var movingPlayer = -1
    private var movingX = 0f
    private var movingY = 0f
    private var moveAnim: ValueAnimator? = null

    // ── 주사위 ──
    private var die1 = 1
    private var die2 = 1
    var diceReady: Boolean = false
        set(v) { if (field != v) { field = v; updateAmbient(); invalidate() } }
    private var rolling = false
    private var rollAnim: ValueAnimator? = null
    private var nextFaceAt = 0L
    private var shake1X = 0f; private var shake1Y = 0f; private var rot1 = 0f
    private var shake2X = 0f; private var shake2Y = 0f; private var rot2 = 0f
    private var resultSum = 0
    private var resultText = ""
    private var resultStart = 0L
    private var resultUntil = 0L

    // ── 발광 / 번쩍임 / 깃발 꽂기 ──
    private var offerTile = -1
    private var offerBuild = false
    private var flashTile = -1
    private var flashColor = 0
    private var flashStart = 0L
    private var flashDur = 1L
    private var plantTile = -1
    private var plantStart = 0L
    private var plantDur = 1L
    private var popTile = -1
    private var popStart = 0L

    // ── 안내 문구 (미리 만든 문자열) ──
    private var fundText = ""
    private var roundText = ""
    private var notice: String? = null
    private var noticeColor = Theme.RED
    private var noticeWidth = 0f
    private val promptText = "터치하여 굴리기"
    private val buyText = "터치하여 구매"
    private val buildText = "터치하여 건설"
    private var promptWidth = 0f
    private var buyWidth = 0f
    private var buildWidth = 0f

    // ── 화면 갱신용 상시 애니메이터 (필요할 때만 돈다, 약 30fps) ──
    private val ambient = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { onAmbientTick() }
    }
    private var lastAmbientDraw = 0L

    // ── Paint (모두 재사용) ──
    private val bitmapPaint = Paint()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = 0xFF0A0F2E.toInt()
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var goldGlow: Shader? = null
    private var greenGlow: Shader? = null
    private val glowMatrix = Matrix()
    private var glowR = 0f
    private val tmpRect = RectF()
    private val path = Path()

    init {
        isClickable = true
        contentDescription = "게임 보드"
    }

    // ─────────────────────────────────────────────
    // 외부 API
    // ─────────────────────────────────────────────

    fun bind(s: GameState) {
        state = s
        for (p in s.players) playerColors[p.id] = Theme.playerColor(p.colorIndex)
        if (s.lastDie1 > 0) { die1 = s.lastDie1; die2 = s.lastDie2 }
        syncFromState()
    }

    /** 화면 표시를 실제 상태와 완전히 맞춘다 (연출이 끝났을 때·복원 시) */
    fun syncFromState() {
        val s = state ?: return
        for (i in 0 until GameConfig.BOARD_SIZE) {
            dispOwner[i] = s.owner[i]
            dispLevel[i] = s.level[i]
        }
        for (k in 0 until s.players.size) {
            pieceTile[k] = s.players[k].position
            pieceHidden[k] = s.players[k].bankrupt
        }
        movingPlayer = -1
        offerTile = s.offerTile
        offerBuild = s.offerKind == com.siwoo.marble.model.OfferKind.BUILD
        refreshTexts()
        updateAmbient()
        invalidate()
    }

    /** 사회복지기금·라운드 문구 갱신 */
    fun refreshTexts() {
        val s = state ?: return
        fundText = "사회복지기금 " + Money.won(s.welfareFund)
        roundText = if (s.turnLimit > 0) "${s.round} / ${s.turnLimit} 라운드" else "${s.round} 라운드"
        invalidate()
    }

    /** 보드 가운데 안내 (잔액 부족 / 우주여행 칸 선택). null 이면 숨김 */
    fun setNotice(text: String?, color: Int) {
        if (text == notice && color == noticeColor) return
        notice = text
        noticeColor = color
        if (text != null) {
            textPaint.textSize = u * 0.34f
            noticeWidth = textPaint.measureText(text)
        }
        updateAmbient()
        invalidate()
    }

    fun setOffer(tile: Int, build: Boolean) {
        offerTile = tile
        offerBuild = build
        updateAmbient()
        invalidate()
    }

    /** 칸이 소유자 색으로 번쩍임 (두 번) */
    fun flash(tile: Int, color: Int, durMs: Long) {
        flashTile = tile
        flashColor = color
        flashStart = SystemClock.uptimeMillis()
        flashDur = durMs.coerceAtLeast(1)
        updateAmbient()
    }

    /** 깃발 꽂기: 이 순간부터 소유 표시 */
    fun plantFlag(tile: Int, owner: Int, durMs: Long) {
        dispOwner[tile] = owner
        dispLevel[tile] = 0
        plantTile = tile
        plantStart = SystemClock.uptimeMillis()
        plantDur = durMs.coerceAtLeast(1)
        if (offerTile == tile) offerTile = -1
        updateAmbient()
    }

    fun setBuilt(tile: Int, level: Int) {
        dispLevel[tile] = level
        popTile = tile
        popStart = SystemClock.uptimeMillis()
        if (offerTile == tile) offerTile = -1
        updateAmbient()
    }

    fun removeTiles(list: IntArray) {
        for (i in list) {
            dispOwner[i] = -1
            dispLevel[i] = 0
            if (offerTile == i) offerTile = -1
        }
        invalidate()
    }

    fun recolorTiles(list: IntArray, owner: Int) {
        for (i in list) dispOwner[i] = owner
        invalidate()
    }

    fun clearOwner(owner: Int) {
        for (i in 0 until GameConfig.BOARD_SIZE) if (dispOwner[i] == owner) { dispOwner[i] = -1; dispLevel[i] = 0 }
        if (owner in pieceHidden.indices) pieceHidden[owner] = true
        invalidate()
    }

    val isAnimating: Boolean
        get() = (moveAnim?.isRunning == true) || (rollAnim?.isRunning == true)

    /** 진행 중인 애니메이션을 콜백 없이 즉시 정리 */
    fun cancelAnimations() {
        moveAnim?.let { it.removeAllListeners(); it.removeAllUpdateListeners(); it.cancel() }
        rollAnim?.let { it.removeAllListeners(); it.removeAllUpdateListeners(); it.cancel() }
        moveAnim = null
        rollAnim = null
        rolling = false
        resultUntil = 0L
        flashTile = -1
        plantTile = -1
        popTile = -1
        state?.let { if (it.lastDie1 > 0) { die1 = it.lastDie1; die2 = it.lastDie2 } }
        syncFromState()
    }

    /** 주사위: 흔들리며 눈이 바뀌다 점점 느려지며 멈춤 → 결과 숫자 크게 → holdMs 뒤 onEnd */
    fun rollDice(d1: Int, d2: Int, rollMs: Long, holdMs: Long, onTick: () -> Unit, onEnd: () -> Unit) {
        rollAnim?.cancel()
        rolling = true
        nextFaceAt = 0L
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = rollMs.coerceAtLeast(1)
        anim.interpolator = LinearInterpolator()
        anim.addUpdateListener { a ->
            val p = a.animatedFraction
            val t = SystemClock.uptimeMillis()
            if (t >= nextFaceAt) {
                die1 = 1 + RANDOM.nextInt(6)
                die2 = 1 + RANDOM.nextInt(6)
                nextFaceAt = t + (45 + 280 * p * p).toLong() // 점점 느려짐
                onTick()
            }
            val amp = u * 0.14f * (1f - p)
            shake1X = (RANDOM.nextFloat() - 0.5f) * 2f * amp; shake1Y = (RANDOM.nextFloat() - 0.5f) * 2f * amp
            shake2X = (RANDOM.nextFloat() - 0.5f) * 2f * amp; shake2Y = (RANDOM.nextFloat() - 0.5f) * 2f * amp
            rot1 = (RANDOM.nextFloat() - 0.5f) * 36f * (1f - p)
            rot2 = (RANDOM.nextFloat() - 0.5f) * 36f * (1f - p)
            invalidate()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                rolling = false
                die1 = d1
                die2 = d2
                shake1X = 0f; shake1Y = 0f; shake2X = 0f; shake2Y = 0f; rot1 = 0f; rot2 = 0f
                resultSum = d1 + d2
                resultText = resultSum.toString()
                resultStart = SystemClock.uptimeMillis()
                resultUntil = resultStart + holdMs
                // 결과 표시 시간 동안 다시 그리기 → 끝나면 onEnd
                val hold = ValueAnimator.ofFloat(0f, 1f)
                hold.duration = holdMs.coerceAtLeast(1)
                hold.addUpdateListener { invalidate() }
                hold.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        rollAnim = null
                        invalidate()
                        onEnd()
                    }
                })
                rollAnim = hold
                hold.start()
            }
        })
        rollAnim = anim
        anim.start()
    }

    /** 한 칸씩 짧게 점프하며 이동 (steps < 0 이면 뒤로) */
    fun animateMove(playerId: Int, from: Int, steps: Int, stepMs: Long, onStep: () -> Unit, onEnd: () -> Unit) {
        val n = abs(steps)
        val dir = if (steps < 0) -1 else 1
        if (n == 0) { onEnd(); return }
        moveAnim?.cancel()
        movingPlayer = playerId
        var lastStep = -1
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = n * stepMs.coerceAtLeast(1)
        anim.interpolator = LinearInterpolator()
        anim.addUpdateListener { a ->
            val v = a.animatedFraction * n
            var step = v.toInt()
            if (step >= n) step = n - 1
            val frac = v - step
            val ta = wrap(from + dir * step)
            val tb = wrap(ta + dir)
            setMovingPoint(playerId, ta, tb, frac, u * 0.55f)
            if (step != lastStep) { lastStep = step; onStep() }
            invalidate()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                pieceTile[playerId] = wrap(from + steps)
                movingPlayer = -1
                moveAnim = null
                invalidate()
                onEnd()
            }
        })
        moveAnim = anim
        anim.start()
    }

    /** 순간이동: 큰 포물선을 그리며 한 번에 이동 */
    fun animateJump(playerId: Int, from: Int, to: Int, durMs: Long, onEnd: () -> Unit) {
        moveAnim?.cancel()
        movingPlayer = playerId
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = durMs.coerceAtLeast(1)
        anim.addUpdateListener { a ->
            setMovingPoint(playerId, from, to, a.animatedFraction, u * 2.6f)
            invalidate()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                pieceTile[playerId] = to
                movingPlayer = -1
                moveAnim = null
                invalidate()
                onEnd()
            }
        })
        moveAnim = anim
        anim.start()
    }

    fun tileCenter(i: Int, out: PointF) {
        val r = tileRects[i]
        out.set(r.centerX(), r.centerY())
    }

    fun boardCenter(out: PointF) {
        out.set(cx, cy - u * 0.45f)
    }

    /** 중앙 문구가 들어갈 수 있는 폭 */
    val centerWidth: Float get() = side - depth * 2f

    // ─────────────────────────────────────────────
    // 상시 애니메이터 (주사위 떠오름·발광·안내 깜빡임·번쩍임)
    // ─────────────────────────────────────────────

    private fun needsAmbient(): Boolean {
        val t = SystemClock.uptimeMillis()
        return (diceReady && !rolling) || offerTile >= 0 || notice != null ||
            (flashTile >= 0 && t < flashStart + flashDur) ||
            (plantTile >= 0 && t < plantStart + plantDur) ||
            (popTile >= 0 && t < popStart + POP_MS)
    }

    private fun updateAmbient() {
        if (!isAttachedToWindow) return
        if (needsAmbient()) {
            if (!ambient.isRunning) ambient.start()
        } else if (ambient.isRunning) {
            ambient.cancel()
        }
        invalidate()
    }

    private fun onAmbientTick() {
        val t = SystemClock.uptimeMillis()
        if (!needsAmbient()) {
            if (flashTile >= 0 && t >= flashStart + flashDur) flashTile = -1
            if (plantTile >= 0 && t >= plantStart + plantDur) plantTile = -1
            if (popTile >= 0 && t >= popStart + POP_MS) popTile = -1
            ambient.cancel()
            invalidate()
            return
        }
        // 저사양: 상시 연출은 약 30fps 로만 갱신 (이동·주사위 애니메이션은 자체 갱신)
        if (t - lastAmbientDraw >= 33) {
            lastAmbientDraw = t
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAmbient()
    }

    // ─────────────────────────────────────────────
    // 크기 / 캐시
    // ─────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) w
        else if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) h
        else minOf(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val s = minOf(w, h)
        if (s <= 0) return
        side = s.toFloat()
        u = side / 12f
        depth = u * 1.5f
        band = u * 0.3f
        cx = side / 2f
        cy = side / 2f
        layoutTiles()
        val ds = u * 1.25f
        val gap = u * 0.35f
        val dcy = cy - u * 0.45f
        diceRect.set(cx - ds - gap / 2 - u * 0.4f, dcy - ds / 2 - u * 0.45f, cx + ds + gap / 2 + u * 0.4f, dcy + ds / 2 + u * 0.9f)
        glowR = u * 1.5f
        goldGlow = RadialGradient(0f, 0f, glowR, intArrayOf(0xEEFFD54F.toInt(), 0x88FFC93C.toInt(), 0x00FFC93C), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        greenGlow = RadialGradient(0f, 0f, glowR, intArrayOf(0xEE69F0AE.toInt(), 0x882ECC71.toInt(), 0x002ECC71), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        textPaint.textSize = u * 0.3f
        promptWidth = textPaint.measureText(promptText)
        buyWidth = textPaint.measureText(buyText)
        buildWidth = textPaint.measureText(buildText)
        notice?.let { textPaint.textSize = u * 0.34f; noticeWidth = textPaint.measureText(it) }
        rebuildBitmap(s)
    }

    override fun onDetachedFromWindow() {
        cancelAnimations()
        ambient.cancel()
        boardBitmap?.recycle()
        boardBitmap = null
        super.onDetachedFromWindow()
    }

    private fun layoutTiles() {
        val d = depth
        for (i in 0 until GameConfig.BOARD_SIZE) {
            val r = tileRects[i]
            when {
                i == 0 -> { r.set(side - d, side - d, side, side); tileSides[i] = CORNER }
                i < 10 -> { val right = side - d - (i - 1) * u; r.set(right - u, side - d, right, side); tileSides[i] = BOTTOM }
                i == 10 -> { r.set(0f, side - d, d, side); tileSides[i] = CORNER }
                i < 20 -> { val bottom = side - d - (i - 11) * u; r.set(0f, bottom - u, d, bottom); tileSides[i] = LEFT }
                i == 20 -> { r.set(0f, 0f, d, d); tileSides[i] = CORNER }
                i < 30 -> { val left = d + (i - 21) * u; r.set(left, 0f, left + u, d); tileSides[i] = TOP }
                i == 30 -> { r.set(side - d, 0f, side, d); tileSides[i] = CORNER }
                else -> { val top = d + (i - 31) * u; r.set(side - d, top, side, top + u); tileSides[i] = RIGHT }
            }
        }
    }

    /** 보드 배경(칸·이름·가격·색 띠·안쪽 그림자·모서리 그림·제목)을 비트맵 한 장으로 */
    private fun rebuildBitmap(size: Int) {
        boardBitmap?.recycle()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        // 가운데: 남색 원형 그라데이션
        val center = Paint(Paint.ANTI_ALIAS_FLAG)
        center.shader = RadialGradient(cx, cy, side * 0.55f, intArrayOf(0xFF2A3C8F.toInt(), 0xFF101A4E.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, side, side, center)
        // 가운데 장식 테두리
        val deco = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = u * 0.05f; color = 0x66FFC93C }
        c.drawRoundRect(RectF(depth + u * 0.35f, depth + u * 0.35f, side - depth - u * 0.35f, side - depth - u * 0.35f), u * 0.4f, u * 0.4f, deco)

        // 제목 (외곽선 + 금색)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = u * 1.0f }
        val titleOutline = Paint(title).apply { style = Paint.Style.STROKE; strokeWidth = u * 0.14f; color = 0xFF060A24.toInt(); strokeJoin = Paint.Join.ROUND }
        val ty = cy - u * 2.85f
        c.drawText("시우 마블", cx, ty, titleOutline)
        title.shader = LinearGradient(0f, ty - u, 0f, ty, Theme.GOLD_LIGHT, Theme.GOLD_DARK, Shader.TileMode.CLAMP)
        c.drawText("시우 마블", cx, ty, title)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = u * 0.28f; color = 0xAAFFE08A.toInt() }
        c.drawText("SIWOO MARBLE", cx, ty + u * 0.45f, sub)

        for (t in tiles) drawTileStatic(c, t)
        boardBitmap = bmp
    }

    private fun drawTileStatic(c: Canvas, t: Tile) {
        val r = tileRects[t.index]
        val sideType = tileSides[t.index]
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            color = 0xFF1A2040.toInt()
        }
        fill.color = when (t.type) {
            TileType.CITY -> 0xFFFFFBF0.toInt()
            TileType.SPECIAL -> 0xFFF2E6D8.toInt()
            TileType.GOLDEN_KEY -> 0xFFFFF0B8.toInt()
            TileType.START -> 0xFFFFE3E6.toInt()
            TileType.ISLAND -> 0xFFD6F2FF.toInt()
            TileType.WELFARE_RECEIVE, TileType.WELFARE_PAY -> 0xFFFCE0EE.toInt()
            TileType.SPACE -> 0xFF14206B.toInt()
        }
        c.drawRect(r, fill)

        // 색 띠 (안쪽 가장자리)
        val inner = RectF(r)
        if (t.type == TileType.CITY || t.type == TileType.SPECIAL) {
            fill.color = if (t.type == TileType.CITY) BoardData.GROUP_COLORS[t.group] else BoardData.SPECIAL_COLOR
            val b = RectF(r)
            when (sideType) {
                BOTTOM -> { b.bottom = r.top + band; inner.top = b.bottom }
                TOP -> { b.top = r.bottom - band; inner.bottom = b.top }
                LEFT -> { b.left = r.right - band; inner.right = b.left }
                RIGHT -> { b.right = r.left + band; inner.left = b.right }
            }
            c.drawRect(b, fill)
            // 띠 윗면 광택
            fill.color = 0x33FFFFFF
            c.drawRect(b.left, b.top, b.right, b.top + (b.bottom - b.top) * 0.35f, fill)
        }

        // 은은한 안쪽 그림자 (테두리에서 안쪽으로 옅어지는 선 3겹)
        val inset = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = u * 0.045f }
        for (k in 0 until 3) {
            inset.color = Color.argb(46 - k * 14, 0, 0, 30)
            val o = u * 0.025f + k * u * 0.045f
            c.drawRect(r.left + o, r.top + o, r.right - o, r.bottom - o, inset)
        }

        val pad = u * 0.06f
        val maxW = inner.width() - pad * 2
        when (t.type) {
            TileType.CITY, TileType.SPECIAL -> {
                text.textSize = u * 0.27f
                val lines = splitToFit(t.shortName, text, maxW)
                drawLines(c, lines, Money.comma(t.price), inner, text, u * 0.2f, 0xFF5D4037.toInt())
            }
            TileType.GOLDEN_KEY -> {
                text.textSize = u * 0.25f
                text.color = 0xFF8D6E00.toInt()
                drawKeyIcon(c, inner.centerX(), inner.top + inner.height() * 0.3f, u * 0.2f)
                val lines = splitToFit(t.shortName, text, maxW)
                drawLinesAt(c, lines, inner.centerX(), inner.top + inner.height() * 0.62f, text)
            }
            TileType.WELFARE_PAY -> {
                text.textSize = u * 0.24f
                text.color = 0xFFAD1457.toInt()
                drawLines(c, splitToFit(t.shortName, text, maxW), "기부 " + Money.comma(GameConfig.WELFARE_DONATION), inner, text, u * 0.18f, 0xFF880E4F.toInt())
            }
            else -> drawCorner(c, t, r, text)
        }

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, u * 0.025f)
            color = 0xFF3C4778.toInt()
        }
        c.drawRect(r, border)
    }

    /** 모서리 칸: 도형으로 그린 큰 아이콘 */
    private fun drawCorner(c: Canvas, t: Tile, r: RectF, text: Paint) {
        val x = r.centerX()
        val f = Paint(Paint.ANTI_ALIAS_FLAG)
        text.textSize = u * 0.3f
        when (t.type) {
            TileType.START -> {
                f.color = 0xFFE53935.toInt()
                val p = Path()
                val y = r.top + r.height() * 0.38f
                val w = r.width() * 0.7f
                p.moveTo(x - w / 2, y)
                p.lineTo(x - w / 2 + u * 0.45f, y - u * 0.36f)
                p.lineTo(x - w / 2 + u * 0.45f, y - u * 0.14f)
                p.lineTo(x + w / 2, y - u * 0.14f)
                p.lineTo(x + w / 2, y + u * 0.14f)
                p.lineTo(x - w / 2 + u * 0.45f, y + u * 0.14f)
                p.lineTo(x - w / 2 + u * 0.45f, y + u * 0.36f)
                p.close()
                c.drawPath(p, f)
                text.color = 0xFFB71C1C.toInt()
                text.textSize = u * 0.36f
                c.drawText("출발", x, r.top + r.height() * 0.72f, text)
                text.textSize = u * 0.2f
                text.typeface = Typeface.DEFAULT
                c.drawText("월급 " + Money.comma(GameConfig.SALARY), x, r.top + r.height() * 0.9f, text)
                text.typeface = Typeface.DEFAULT_BOLD
            }
            TileType.ISLAND -> {
                val sea = RectF(r.left + u * 0.12f, r.top + r.height() * 0.42f, r.right - u * 0.12f, r.top + r.height() * 0.68f)
                f.color = 0xFF29B6F6.toInt(); c.drawOval(sea, f)
                f.color = 0xFFFFE082.toInt()
                c.drawOval(RectF(x - u * 0.4f, sea.top + u * 0.02f, x + u * 0.4f, sea.bottom - u * 0.06f), f)
                f.color = 0xFF6D4C41.toInt()
                c.drawRect(x - u * 0.045f, r.top + r.height() * 0.18f, x + u * 0.045f, sea.top + u * 0.1f, f)
                f.color = 0xFF2E7D32.toInt()
                c.drawOval(RectF(x - u * 0.42f, r.top + r.height() * 0.11f, x, r.top + r.height() * 0.24f), f)
                c.drawOval(RectF(x, r.top + r.height() * 0.11f, x + u * 0.42f, r.top + r.height() * 0.24f), f)
                text.color = 0xFF01579B.toInt()
                c.drawText("무인도", x, r.top + r.height() * 0.88f, text)
            }
            TileType.WELFARE_RECEIVE -> {
                f.color = 0xFFE91E63.toInt()
                val hy = r.top + r.height() * 0.36f
                val s = u * 0.3f
                c.drawCircle(x - s * 0.5f, hy - s * 0.2f, s * 0.55f, f)
                c.drawCircle(x + s * 0.5f, hy - s * 0.2f, s * 0.55f, f)
                val p = Path()
                p.moveTo(x - s * 1.03f, hy - s * 0.05f)
                p.lineTo(x + s * 1.03f, hy - s * 0.05f)
                p.lineTo(x, hy + s * 1.0f)
                p.close()
                c.drawPath(p, f)
                text.color = 0xFF880E4F.toInt()
                text.textSize = u * 0.24f
                c.drawText("사회복지", x, r.top + r.height() * 0.74f, text)
                c.drawText("기금", x, r.top + r.height() * 0.9f, text)
            }
            TileType.SPACE -> {
                f.color = Color.WHITE
                c.drawCircle(r.left + u * 0.25f, r.top + u * 0.25f, u * 0.04f, f)
                c.drawCircle(r.right - u * 0.3f, r.top + u * 0.45f, u * 0.05f, f)
                c.drawCircle(r.left + u * 0.35f, r.top + r.height() * 0.62f, u * 0.035f, f)
                val ry = r.top + r.height() * 0.38f
                f.color = 0xFFFF7043.toInt()
                c.drawOval(RectF(x - u * 0.1f, ry + u * 0.25f, x + u * 0.1f, ry + u * 0.55f), f)
                f.color = 0xFFE53935.toInt()
                val fin = Path()
                fin.moveTo(x - u * 0.3f, ry + u * 0.32f)
                fin.lineTo(x + u * 0.3f, ry + u * 0.32f)
                fin.lineTo(x, ry)
                fin.close()
                c.drawPath(fin, f)
                f.color = 0xFFECEFF1.toInt()
                c.drawOval(RectF(x - u * 0.16f, ry - u * 0.45f, x + u * 0.16f, ry + u * 0.34f), f)
                f.color = 0xFF29B6F6.toInt()
                c.drawCircle(x, ry - u * 0.1f, u * 0.075f, f)
                text.color = Theme.GOLD
                c.drawText("우주여행", x, r.top + r.height() * 0.9f, text)
            }
            else -> Unit
        }
    }

    private fun drawKeyIcon(c: Canvas, x: Float, y: Float, s: Float) {
        val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB300.toInt() }
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8D6E00.toInt(); style = Paint.Style.STROKE; strokeWidth = s * 0.18f }
        c.drawCircle(x - s * 0.7f, y, s * 0.55f, f)
        c.drawCircle(x - s * 0.7f, y, s * 0.55f, st)
        c.drawRect(x - s * 0.2f, y - s * 0.14f, x + s * 1.1f, y + s * 0.14f, f)
        c.drawRect(x + s * 0.6f, y, x + s * 0.8f, y + s * 0.45f, f)
        c.drawRect(x + s * 0.9f, y, x + s * 1.1f, y + s * 0.35f, f)
    }

    private fun splitToFit(name: String, p: Paint, maxW: Float): Array<String> {
        if (p.measureText(name) <= maxW || name.length < 3) return arrayOf(name)
        val half = (name.length + 1) / 2
        val a = name.substring(0, half)
        val b = name.substring(half)
        while (p.measureText(a) > maxW && p.textSize > u * 0.16f) p.textSize *= 0.92f
        return arrayOf(a, b)
    }

    private fun drawLines(c: Canvas, lines: Array<String>, sub: String, area: RectF, p: Paint, subSize: Float, subColor: Int) {
        val nameH = p.textSize * 1.08f
        val total = nameH * lines.size + subSize * 1.15f
        var y = area.centerY() - total / 2 + p.textSize * 0.92f
        for (line in lines) { c.drawText(line, area.centerX(), y, p); y += nameH }
        val oldSize = p.textSize
        val oldColor = p.color
        p.textSize = subSize
        p.color = subColor
        p.typeface = Typeface.DEFAULT
        // 가격이 칸보다 넓으면 줄인다
        while (p.measureText(sub) > area.width() * 0.94f && p.textSize > u * 0.12f) p.textSize *= 0.92f
        c.drawText(sub, area.centerX(), y + subSize * 0.12f, p)
        p.textSize = oldSize
        p.color = oldColor
        p.typeface = Typeface.DEFAULT_BOLD
    }

    private fun drawLinesAt(c: Canvas, lines: Array<String>, x: Float, top: Float, p: Paint) {
        var y = top + p.textSize * 0.4f
        for (line in lines) { c.drawText(line, x, y, p); y += p.textSize * 1.08f }
    }

    // ─────────────────────────────────────────────
    // 매 프레임 그리기 (할당 금지 구역)
    // ─────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val bmp = boardBitmap ?: return
        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        val s = state ?: return
        val t = SystemClock.uptimeMillis()

        // 소유 표시 + 건물 + 깃발
        for (i in 0 until GameConfig.BOARD_SIZE) {
            val o = dispOwner[i]
            if (o >= 0) drawOwnership(canvas, i, playerColors[o], dispLevel[i], t)
        }

        // 통행료: 소유자 색으로 번쩍
        if (flashTile >= 0 && t < flashStart + flashDur) {
            val f = (t - flashStart).toFloat() / flashDur
            fillPaint.color = flashColor
            fillPaint.alpha = (abs(sin(f * PI.toFloat() * 2f)) * 210).toInt()
            canvas.drawRect(tileRects[flashTile], fillPaint)
        }

        // 구매(금색)/건설(초록) 발광
        if (offerTile >= 0) drawOffer(canvas, t)

        drawCenter(canvas, s, t)

        // 말: 현재 플레이어를 맨 위에
        val cur = s.current
        for (k in 0 until s.players.size) {
            if (pieceHidden[k] || k == cur) continue
            drawPiece(canvas, k, playerColors[k], false)
        }
        if (!pieceHidden[cur]) drawPiece(canvas, cur, playerColors[cur], !s.gameOver)

        // 말풍선은 말 위에
        if (offerTile >= 0) drawBubble(canvas, t)
    }

    private fun drawOwnership(c: Canvas, i: Int, color: Int, level: Int, t: Long) {
        val r = tileRects[i]
        fillPaint.color = color
        fillPaint.alpha = 45
        c.drawRect(r, fillPaint)
        // 칸 테두리를 소유자 색으로
        strokePaint.color = color
        strokePaint.strokeWidth = u * 0.075f
        val o = u * 0.04f
        tmpRect.set(r.left + o, r.top + o, r.right - o, r.bottom - o)
        c.drawRect(tmpRect, strokePaint)

        // 깃발 (꽂히는 연출: 위에서 떨어져 튕김)
        var fx: Float
        var fy: Float
        when (tileSides[i]) {
            TOP -> { fx = r.left + u * 0.16f; fy = r.top + u * 0.62f }
            LEFT -> { fx = r.left + u * 0.16f; fy = r.bottom - u * 0.1f }
            RIGHT -> { fx = r.right - u * 0.46f; fy = r.bottom - u * 0.1f }
            else -> { fx = r.left + u * 0.16f; fy = r.bottom - u * 0.1f }
        }
        var scale = 1f
        if (i == plantTile && t < plantStart + plantDur) {
            val f = (t - plantStart).toFloat() / plantDur
            if (f < 0.5f) {
                val q = f / 0.5f
                fy -= (1f - q * q) * u * 1.2f
            } else {
                scale = 1f + 0.35f * sin((f - 0.5f) / 0.5f * PI.toFloat())
            }
        }
        val ph = u * 0.48f * scale
        strokePaint.color = 0xFF3E2723.toInt()
        strokePaint.strokeWidth = u * 0.045f
        c.drawLine(fx, fy, fx, fy - ph, strokePaint)
        path.reset()
        path.moveTo(fx, fy - ph)
        path.lineTo(fx + u * 0.32f * scale, fy - ph + u * 0.1f * scale)
        path.lineTo(fx, fy - ph + u * 0.2f * scale)
        path.close()
        fillPaint.color = color
        c.drawPath(path, fillPaint)
        strokePaint.color = Color.WHITE
        strokePaint.strokeWidth = u * 0.02f
        c.drawPath(path, strokePaint)

        if (level <= 0) return
        // 건물 아이콘: 색 띠 위에 단계 수만큼 (방금 지은 것은 톡 튀어오름)
        var sz = band * 0.82f
        if (i == popTile && t < popStart + POP_MS) sz *= 1f + 0.5f * sin((t - popStart).toFloat() / POP_MS * PI.toFloat())
        val gap = sz * 0.18f
        val total = level * sz + (level - 1) * gap
        for (k in 0 until level) {
            val x: Float
            val y: Float
            when (tileSides[i]) {
                BOTTOM -> { x = r.centerX() - total / 2 + k * (sz + gap); y = r.top + (band - sz) / 2 }
                TOP -> { x = r.centerX() - total / 2 + k * (sz + gap); y = r.bottom - band + (band - sz) / 2 }
                LEFT -> { x = r.right - band + (band - sz) / 2; y = r.centerY() - total / 2 + k * (sz + gap) }
                else -> { x = r.left + (band - sz) / 2; y = r.centerY() - total / 2 + k * (sz + gap) }
            }
            drawBuilding(c, k + 1, x, y, sz)
        }
    }

    private fun drawBuilding(c: Canvas, kind: Int, x: Float, y: Float, s: Float) {
        strokePaint.color = Color.WHITE
        strokePaint.strokeWidth = s * 0.12f
        when (kind) {
            1 -> {
                fillPaint.color = 0xFF2E7D32.toInt()
                c.drawRect(x + s * 0.12f, y + s * 0.45f, x + s * 0.88f, y + s, fillPaint)
                path.reset()
                path.moveTo(x, y + s * 0.5f)
                path.lineTo(x + s * 0.5f, y)
                path.lineTo(x + s, y + s * 0.5f)
                path.close()
                c.drawPath(path, fillPaint)
            }
            2 -> {
                fillPaint.color = 0xFF1565C0.toInt()
                c.drawRect(x + s * 0.15f, y, x + s * 0.85f, y + s, fillPaint)
                c.drawRect(x + s * 0.15f, y, x + s * 0.85f, y + s, strokePaint)
            }
            else -> {
                fillPaint.color = 0xFFC62828.toInt()
                c.drawRect(x, y + s * 0.1f, x + s, y + s, fillPaint)
                c.drawRect(x, y + s * 0.1f, x + s, y + s, strokePaint)
            }
        }
    }

    private fun drawOffer(c: Canvas, t: Long) {
        val r = tileRects[offerTile]
        val pulse = 0.5f + 0.5f * sin(t / 1400f * 2f * PI.toFloat())
        glowMatrix.setTranslate(r.centerX(), r.centerY())
        val sh = (if (offerBuild) greenGlow else goldGlow) ?: return
        sh.setLocalMatrix(glowMatrix)
        glowPaint.shader = sh
        glowPaint.alpha = (90 + 165 * pulse).toInt()
        c.drawCircle(r.centerX(), r.centerY(), glowR * (0.85f + 0.15f * pulse), glowPaint)
        strokePaint.color = if (offerBuild) Theme.GREEN else Theme.GOLD
        strokePaint.alpha = (140 + 115 * pulse).toInt()
        strokePaint.strokeWidth = u * 0.09f
        c.drawRect(r, strokePaint)
    }

    /** "터치하여 구매" 말풍선: 칸에서 보드 가운데 쪽으로 */
    private fun drawBubble(c: Canvas, t: Long) {
        val r = tileRects[offerTile]
        val text = if (offerBuild) buildText else buyText
        val tw = if (offerBuild) buildWidth else buyWidth
        val bw = tw + u * 0.5f
        val bh = u * 0.55f
        val tip = u * 0.18f
        var bx: Float
        var by: Float
        when (tileSides[offerTile]) {
            BOTTOM -> { bx = r.centerX() - bw / 2; by = r.top - tip - bh }
            TOP -> { bx = r.centerX() - bw / 2; by = r.bottom + tip }
            LEFT -> { bx = r.right + tip; by = r.centerY() - bh / 2 }
            else -> { bx = r.left - tip - bw; by = r.centerY() - bh / 2 }
        }
        if (bx < depth * 0.2f) bx = depth * 0.2f
        if (bx + bw > side - depth * 0.2f) bx = side - depth * 0.2f - bw
        val blink = 0.6f + 0.4f * abs(sin(t / 900f * PI.toFloat()))
        fillPaint.color = if (offerBuild) Theme.GREEN_DARK else 0xFFE8A400.toInt()
        fillPaint.alpha = (255 * blink).toInt()
        tmpRect.set(bx, by, bx + bw, by + bh)
        c.drawRoundRect(tmpRect, bh / 2, bh / 2, fillPaint)
        path.reset()
        when (tileSides[offerTile]) {
            BOTTOM -> { path.moveTo(r.centerX() - tip, by + bh); path.lineTo(r.centerX() + tip, by + bh); path.lineTo(r.centerX(), by + bh + tip) }
            TOP -> { path.moveTo(r.centerX() - tip, by); path.lineTo(r.centerX() + tip, by); path.lineTo(r.centerX(), by - tip) }
            LEFT -> { path.moveTo(bx, r.centerY() - tip); path.lineTo(bx, r.centerY() + tip); path.lineTo(bx - tip, r.centerY()) }
            else -> { path.moveTo(bx + bw, r.centerY() - tip); path.lineTo(bx + bw, r.centerY() + tip); path.lineTo(bx + bw + tip, r.centerY()) }
        }
        path.close()
        c.drawPath(path, fillPaint)
        textPaint.textSize = u * 0.3f
        textPaint.color = Color.WHITE
        textPaint.alpha = (255 * blink).toInt()
        c.drawText(text, bx + bw / 2, by + bh * 0.68f, textPaint)
        textPaint.alpha = 255
    }

    private fun drawCenter(c: Canvas, s: GameState, t: Long) {
        textPaint.color = 0xFFFF8FB8.toInt()
        textPaint.textSize = u * 0.32f
        c.drawText(fundText, cx, cy - u * 1.75f, textPaint)

        // 주사위
        val ds = u * 1.25f
        val gap = u * 0.35f
        var dcy = cy - u * 0.45f
        val ready = diceReady && !rolling
        if (ready) dcy += sin(t / 1600f * 2f * PI.toFloat()) * u * 0.1f // 위아래로 천천히
        val showResult = t < resultUntil
        val alpha = if (rolling || showResult || ready) 255 else 95
        drawDie(c, cx - gap / 2 - ds / 2 + shake1X, dcy + shake1Y, ds, die1, rot1, alpha)
        drawDie(c, cx + gap / 2 + ds / 2 + shake2X, dcy + shake2Y, ds, die2, rot2, alpha)

        val py = cy + u * 0.82f
        if (showResult) {
            // 결과 숫자 크게 (톡 튀어나옴)
            val f = ((t - resultStart).toFloat() / 250f).coerceAtMost(1f)
            val sc = 0.5f + 0.5f * f + 0.3f * sin(f * PI.toFloat())
            c.save()
            c.translate(cx, py + u * 0.25f)
            c.scale(sc, sc)
            outlinePaint.textSize = u * 0.95f
            outlinePaint.strokeWidth = u * 0.14f
            c.drawText(resultText, 0f, 0f, outlinePaint)
            textPaint.textSize = u * 0.95f
            textPaint.color = Theme.GOLD
            c.drawText(resultText, 0f, 0f, textPaint)
            c.restore()
        } else if (ready && notice == null) {
            val blink = 0.35f + 0.65f * abs(sin(t / 700f * PI.toFloat()))
            textPaint.textSize = u * 0.3f
            textPaint.color = Color.WHITE
            textPaint.alpha = (255 * blink).toInt()
            c.drawText(promptText, cx, py, textPaint)
            textPaint.alpha = 255
        }

        textPaint.color = 0xFFB8C3F0.toInt()
        textPaint.textSize = u * 0.3f
        c.drawText(roundText, cx, cy + u * 1.65f, textPaint)

        val n = notice
        if (n != null) {
            val blink = 0.55f + 0.45f * abs(sin(t / 800f * PI.toFloat()))
            textPaint.textSize = u * 0.34f
            tmpRect.set(cx - noticeWidth / 2 - u * 0.3f, cy + u * 2.15f, cx + noticeWidth / 2 + u * 0.3f, cy + u * 2.85f)
            fillPaint.color = noticeColor
            fillPaint.alpha = (255 * blink).toInt()
            c.drawRoundRect(tmpRect, u * 0.35f, u * 0.35f, fillPaint)
            textPaint.color = Color.WHITE
            c.drawText(n, cx, cy + u * 2.62f, textPaint)
        }
    }

    private fun drawDie(c: Canvas, x: Float, y: Float, s: Float, v: Int, rot: Float, alpha: Int) {
        c.save()
        c.translate(x, y)
        c.rotate(rot)
        tmpRect.set(-s / 2, -s / 2, s / 2, s / 2)
        // 그림자
        fillPaint.color = 0x55000000
        fillPaint.alpha = alpha / 3
        c.translate(s * 0.05f, s * 0.08f)
        c.drawRoundRect(tmpRect, s * 0.2f, s * 0.2f, fillPaint)
        c.translate(-s * 0.05f, -s * 0.08f)
        fillPaint.color = Color.WHITE
        fillPaint.alpha = alpha
        c.drawRoundRect(tmpRect, s * 0.2f, s * 0.2f, fillPaint)
        strokePaint.color = Theme.GOLD_DARK
        strokePaint.alpha = alpha
        strokePaint.strokeWidth = s * 0.05f
        c.drawRoundRect(tmpRect, s * 0.2f, s * 0.2f, strokePaint)
        val face = if (v in 1..6) v else 1
        fillPaint.color = if (face == 1) 0xFFD32F2F.toInt() else 0xFF1A2040.toInt()
        fillPaint.alpha = alpha
        val pips = PIPS[face - 1]
        val pr = s * (if (face == 1) 0.13f else 0.095f)
        var k = 0
        while (k < pips.size) {
            c.drawCircle((pips[k] - 0.5f) * s, (pips[k + 1] - 0.5f) * s, pr, fillPaint)
            k += 2
        }
        c.restore()
        fillPaint.alpha = 255
        strokePaint.alpha = 255
    }

    private fun drawPiece(c: Canvas, playerId: Int, color: Int, highlight: Boolean) {
        val x: Float
        val y: Float
        if (playerId == movingPlayer) {
            x = movingX; y = movingY
        } else {
            val ti = pieceTile[playerId]
            x = pieceX(ti) + OFF_X[playerId] * u * 0.24f
            y = pieceY(ti) + OFF_Y[playerId] * u * 0.2f
        }
        val rad = u * 0.21f
        fillPaint.color = 0x66000000
        c.drawCircle(x + rad * 0.15f, y + rad * 0.25f, rad, fillPaint)
        fillPaint.color = color
        c.drawCircle(x, y, rad, fillPaint)
        fillPaint.color = 0x88FFFFFF.toInt()
        c.drawCircle(x - rad * 0.3f, y - rad * 0.35f, rad * 0.3f, fillPaint)
        strokePaint.color = if (highlight) Theme.GOLD else 0xFF1A2040.toInt()
        strokePaint.strokeWidth = if (highlight) rad * 0.3f else rad * 0.16f
        c.drawCircle(x, y, rad, strokePaint)
    }

    private fun setMovingPoint(playerId: Int, a: Int, b: Int, frac: Float, hop: Float) {
        val ox = OFF_X[playerId] * u * 0.24f
        val oy = OFF_Y[playerId] * u * 0.2f
        movingX = pieceX(a) + (pieceX(b) - pieceX(a)) * frac + ox
        movingY = pieceY(a) + (pieceY(b) - pieceY(a)) * frac + oy - sin(frac * PI).toFloat() * hop
    }

    private fun pieceX(i: Int): Float {
        val r = tileRects[i]
        return when (tileSides[i]) {
            LEFT -> r.centerX() - u * 0.2f
            RIGHT -> r.centerX() + u * 0.2f
            else -> r.centerX()
        }
    }

    private fun pieceY(i: Int): Float {
        val r = tileRects[i]
        return when (tileSides[i]) {
            BOTTOM -> r.centerY() + u * 0.22f
            TOP -> r.centerY() - u * 0.22f
            else -> r.centerY()
        }
    }

    // ─────────────────────────────────────────────
    // 터치
    // ─────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val x = event.x
                val y = event.y
                if (diceRect.contains(x, y)) {
                    performClick()
                    if (diceReady && !rolling) listener?.onDiceTapped() // 굴릴 수 없을 때는 무시
                    return true
                }
                val idx = tileAt(x, y)
                if (idx >= 0) {
                    performClick()
                    listener?.onTileTapped(idx)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun tileAt(x: Float, y: Float): Int {
        for (i in 0 until GameConfig.BOARD_SIZE) if (tileRects[i].contains(x, y)) return i
        return -1
    }

    private fun wrap(i: Int): Int = ((i % GameConfig.BOARD_SIZE) + GameConfig.BOARD_SIZE) % GameConfig.BOARD_SIZE

    companion object {
        private const val CORNER = 0
        private const val BOTTOM = 1
        private const val LEFT = 2
        private const val TOP = 3
        private const val RIGHT = 4
        private const val POP_MS = 450L

        private val OFF_X = floatArrayOf(-1f, 1f, -1f, 1f)
        private val OFF_Y = floatArrayOf(-1f, -1f, 1f, 1f)
        private val RANDOM = java.util.Random()

        private val PIPS = arrayOf(
            floatArrayOf(0.5f, 0.5f),
            floatArrayOf(0.27f, 0.27f, 0.73f, 0.73f),
            floatArrayOf(0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f),
            floatArrayOf(0.27f, 0.27f, 0.73f, 0.27f, 0.27f, 0.73f, 0.73f, 0.73f),
            floatArrayOf(0.25f, 0.25f, 0.75f, 0.25f, 0.5f, 0.5f, 0.25f, 0.75f, 0.75f, 0.75f),
            floatArrayOf(0.27f, 0.22f, 0.73f, 0.22f, 0.27f, 0.5f, 0.73f, 0.5f, 0.27f, 0.78f, 0.73f, 0.78f),
        )
    }
}
