package com.siwoo.marble.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
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
 * 보드 + 말 + 건물 + 주사위를 그리는 커스텀 View.
 *
 * 저사양 최적화:
 *  - 칸 배경/이름/색 띠는 크기가 정해질 때 RGB_565 Bitmap 한 장에 미리 그려 둔다.
 *  - onDraw 에서는 캐시 비트맵 + 소유자 띠 + 건물 + 말 + 주사위만 그린다.
 *  - onDraw 안에서는 객체를 만들지 않는다 (Paint/RectF/Path/문자열은 모두 필드에 미리 준비).
 *  - invalidate() 는 애니메이션 중이거나 상태가 바뀔 때(refresh)만 호출한다.
 */
class BoardView(context: Context) : View(context) {

    interface Listener {
        fun onTileTapped(index: Int)
    }

    var listener: Listener? = null

    private var state: GameState? = null
    private val tiles: Array<Tile> = BoardData.tiles

    // ── 기하 ──
    private var side = 0f
    private var u = 0f          // 일반 칸의 폭
    private var depth = 0f      // 칸의 깊이(= 모서리 크기)
    private var band = 0f       // 색 띠 두께
    private val tileRects = Array(GameConfig.BOARD_SIZE) { RectF() }
    private val tileSides = IntArray(GameConfig.BOARD_SIZE)

    // ── 캐시 ──
    private var boardBitmap: Bitmap? = null

    // ── 말 / 애니메이션 상태 ──
    private val pieceTile = IntArray(GameConfig.MAX_PLAYERS)
    private var movingPlayer = -1
    private var movingX = 0f
    private var movingY = 0f
    private var moveAnim: ValueAnimator? = null
    private var diceAnim: ValueAnimator? = null
    private var die1 = 0
    private var die2 = 0
    private var diceRolling = false

    /** 우주여행 칸 선택 모드 */
    var selectMode: Boolean = false
        set(v) { field = v; invalidate() }

    // ── 미리 계산해 둔 동적 문자열 (onDraw 에서 문자열 연결 금지) ──
    private var fundText = ""
    private var roundText = ""
    private val selectText = "이동할 칸을 터치하세요"

    // ── Paint (모두 재사용) ──
    private val bitmapPaint = Paint()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val tmpRect = RectF()
    private val roofPath = Path()

    init {
        // 소프트웨어 레이어를 쓰지 않는다 (하드웨어 가속 그대로)
        isClickable = true
        contentDescription = "게임 보드"
    }

    // ─────────────────────────────────────────────
    // 외부 API
    // ─────────────────────────────────────────────

    fun bind(s: GameState) {
        state = s
        die1 = s.lastDie1
        die2 = s.lastDie2
        syncPieces()
        refresh()
    }

    /** 게임 상태가 바뀌었을 때 한 번만 다시 그린다 */
    fun refresh() {
        val s = state ?: return
        fundText = "사회복지기금 " + Money.won(s.welfareFund)
        roundText = if (s.turnLimit > 0) "${s.round} / ${s.turnLimit} 라운드" else "${s.round} 라운드"
        invalidate()
    }

    /** 말 표시 위치를 실제 상태와 맞춘다 */
    fun syncPieces() {
        val s = state ?: return
        for (k in s.players.indices) pieceTile[k] = s.players[k].position
        movingPlayer = -1
    }

    val isAnimating: Boolean
        get() = (moveAnim?.isRunning == true) || (diceAnim?.isRunning == true)

    /** 진행 중인 애니메이션을 콜백 없이 즉시 정리 (백그라운드 전환 시) */
    fun cancelAnimations() {
        moveAnim?.let { it.removeAllListeners(); it.removeAllUpdateListeners(); it.cancel() }
        diceAnim?.let { it.removeAllListeners(); it.removeAllUpdateListeners(); it.cancel() }
        moveAnim = null
        diceAnim = null
        diceRolling = false
        state?.let { die1 = it.lastDie1; die2 = it.lastDie2 }
        syncPieces()
        invalidate()
    }

    fun animateDice(d1: Int, d2: Int, durationMs: Long, onTick: () -> Unit, onEnd: () -> Unit) {
        diceAnim?.cancel()
        diceRolling = true
        var lastSlot = -1
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = durationMs
        anim.interpolator = LinearInterpolator()
        anim.addUpdateListener { a ->
            val slot = (a.currentPlayTime / 70L).toInt()
            if (slot != lastSlot) {
                lastSlot = slot
                die1 = 1 + RANDOM.nextInt(6)
                die2 = 1 + RANDOM.nextInt(6)
                onTick()
                invalidate()
            }
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                diceRolling = false
                die1 = d1
                die2 = d2
                diceAnim = null
                invalidate()
                onEnd()
            }
        })
        diceAnim = anim
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
        anim.duration = n * stepMs
        anim.interpolator = LinearInterpolator()
        anim.addUpdateListener { a ->
            val v = a.animatedFraction * n
            var step = v.toInt()
            if (step >= n) step = n - 1
            val frac = v - step
            val ta = wrap(from + dir * step)
            val tb = wrap(ta + dir)
            setMovingPoint(playerId, ta, tb, frac, u * 0.45f)
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
    fun animateJump(playerId: Int, from: Int, to: Int, durationMs: Long, onEnd: () -> Unit) {
        moveAnim?.cancel()
        movingPlayer = playerId
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = durationMs
        anim.addUpdateListener { a ->
            setMovingPoint(playerId, from, to, a.animatedFraction, u * 2.2f)
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

    // ─────────────────────────────────────────────
    // 크기 / 캐시
    // ─────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) w else minOf(w, h)
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
        layoutTiles()
        buildRoofPath()
        rebuildBitmap(s)
    }

    override fun onDetachedFromWindow() {
        cancelAnimations()
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
                i < 10 -> {
                    val right = side - d - (i - 1) * u
                    r.set(right - u, side - d, right, side); tileSides[i] = BOTTOM
                }
                i == 10 -> { r.set(0f, side - d, d, side); tileSides[i] = CORNER }
                i < 20 -> {
                    val bottom = side - d - (i - 11) * u
                    r.set(0f, bottom - u, d, bottom); tileSides[i] = LEFT
                }
                i == 20 -> { r.set(0f, 0f, d, d); tileSides[i] = CORNER }
                i < 30 -> {
                    val left = d + (i - 21) * u
                    r.set(left, 0f, left + u, d); tileSides[i] = TOP
                }
                i == 30 -> { r.set(side - d, 0f, side, d); tileSides[i] = CORNER }
                else -> {
                    val top = d + (i - 31) * u
                    r.set(side - d, top, side, top + u); tileSides[i] = RIGHT
                }
            }
        }
    }

    private fun buildRoofPath() {
        // 단위 크기(1x1) 지붕 삼각형. 그릴 때 scale/translate 로 위치만 바꾼다.
        roofPath.reset()
        roofPath.moveTo(0f, 0.5f)
        roofPath.lineTo(0.5f, 0f)
        roofPath.lineTo(1f, 0.5f)
        roofPath.close()
    }

    /** 보드 배경(칸·이름·가격·색 띠·모서리 그림·가운데 제목)을 비트맵 한 장으로 */
    private fun rebuildBitmap(size: Int) {
        boardBitmap?.recycle()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(CENTER_BG)

        // 가운데 제목
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val cx = side / 2f
        val cy = side / 2f
        p.textSize = u * 1.05f
        p.color = 0xFF1B5E20.toInt()
        c.drawText("시우 마블", cx, cy - u * 2.3f, p)
        p.textSize = u * 0.34f
        p.typeface = Typeface.DEFAULT
        p.color = 0xFF558B2F.toInt()
        c.drawText("SIWOO MARBLE", cx, cy - u * 1.75f, p)

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
            color = 0xFF212121.toInt()
        }

        fill.color = when (t.type) {
            TileType.CITY -> 0xFFFFFDF7.toInt()
            TileType.SPECIAL -> 0xFFF3E9DF.toInt()
            TileType.GOLDEN_KEY -> 0xFFFFF4C2.toInt()
            TileType.START -> 0xFFFFEBEE.toInt()
            TileType.ISLAND -> 0xFFE1F5FE.toInt()
            TileType.WELFARE_RECEIVE, TileType.WELFARE_PAY -> 0xFFFCE4EC.toInt()
            TileType.SPACE -> 0xFF1A237E.toInt()
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
        }

        // 이름 / 가격
        val pad = u * 0.06f
        val maxW = inner.width() - pad * 2
        when (t.type) {
            TileType.CITY, TileType.SPECIAL -> {
                text.textSize = u * 0.27f
                val lines = splitToFit(t.shortName, text, maxW)
                val price = Money.short(t.price)
                drawLines(c, lines, price, inner, text, u * 0.22f, 0xFF5D4037.toInt())
            }
            TileType.GOLDEN_KEY -> {
                text.textSize = u * 0.26f
                text.color = 0xFF8D6E00.toInt()
                drawKeyIcon(c, inner.centerX(), inner.top + inner.height() * 0.32f, u * 0.2f)
                val lines = splitToFit(t.shortName, text, maxW)
                drawLinesAt(c, lines, inner.centerX(), inner.top + inner.height() * 0.62f, text)
            }
            TileType.WELFARE_PAY -> {
                text.textSize = u * 0.25f
                text.color = 0xFFAD1457.toInt()
                drawLines(c, splitToFit(t.shortName, text, maxW), "기부 " + Money.short(GameConfig.WELFARE_DONATION), inner, text, u * 0.2f, 0xFF880E4F.toInt())
            }
            else -> drawCorner(c, t, r, text)
        }

        // 테두리
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, u * 0.025f)
            color = 0xFF8D8D8D.toInt()
        }
        c.drawRect(r, border)
    }

    private fun drawCorner(c: Canvas, t: Tile, r: RectF, text: Paint) {
        val cx = r.centerX()
        val f = Paint(Paint.ANTI_ALIAS_FLAG)
        text.textSize = u * 0.3f
        when (t.type) {
            TileType.START -> {
                // 진행 방향(왼쪽) 화살표
                f.color = 0xFFE53935.toInt()
                val path = Path()
                val y = r.top + r.height() * 0.38f
                val w = r.width() * 0.62f
                path.moveTo(cx - w / 2, y)
                path.lineTo(cx - w / 2 + u * 0.4f, y - u * 0.32f)
                path.lineTo(cx - w / 2 + u * 0.4f, y - u * 0.12f)
                path.lineTo(cx + w / 2, y - u * 0.12f)
                path.lineTo(cx + w / 2, y + u * 0.12f)
                path.lineTo(cx - w / 2 + u * 0.4f, y + u * 0.12f)
                path.lineTo(cx - w / 2 + u * 0.4f, y + u * 0.32f)
                path.close()
                c.drawPath(path, f)
                text.color = 0xFFB71C1C.toInt()
                text.textSize = u * 0.36f
                c.drawText("출발", cx, r.top + r.height() * 0.72f, text)
                text.textSize = u * 0.22f
                text.typeface = Typeface.DEFAULT
                c.drawText("월급 " + Money.short(GameConfig.SALARY), cx, r.top + r.height() * 0.9f, text)
                text.typeface = Typeface.DEFAULT_BOLD
            }
            TileType.ISLAND -> {
                // 바다 + 모래섬 + 야자수
                val sea = RectF(r.left + u * 0.15f, r.top + r.height() * 0.42f, r.right - u * 0.15f, r.top + r.height() * 0.66f)
                f.color = 0xFF4FC3F7.toInt(); c.drawOval(sea, f)
                f.color = 0xFFFFE082.toInt()
                c.drawOval(RectF(cx - u * 0.38f, sea.top + u * 0.02f, cx + u * 0.38f, sea.bottom - u * 0.06f), f)
                f.color = 0xFF6D4C41.toInt()
                c.drawRect(cx - u * 0.04f, r.top + r.height() * 0.2f, cx + u * 0.04f, sea.top + u * 0.1f, f)
                f.color = 0xFF43A047.toInt()
                c.drawOval(RectF(cx - u * 0.36f, r.top + r.height() * 0.13f, cx, r.top + r.height() * 0.24f), f)
                c.drawOval(RectF(cx, r.top + r.height() * 0.13f, cx + u * 0.36f, r.top + r.height() * 0.24f), f)
                text.color = 0xFF01579B.toInt()
                c.drawText("무인도", cx, r.top + r.height() * 0.86f, text)
            }
            TileType.WELFARE_RECEIVE -> {
                // 하트
                f.color = 0xFFE91E63.toInt()
                val hy = r.top + r.height() * 0.36f
                val s = u * 0.26f
                c.drawCircle(cx - s * 0.5f, hy - s * 0.2f, s * 0.55f, f)
                c.drawCircle(cx + s * 0.5f, hy - s * 0.2f, s * 0.55f, f)
                val path = Path()
                path.moveTo(cx - s * 1.03f, hy - s * 0.05f)
                path.lineTo(cx + s * 1.03f, hy - s * 0.05f)
                path.lineTo(cx, hy + s * 1.0f)
                path.close()
                c.drawPath(path, f)
                text.color = 0xFF880E4F.toInt()
                text.textSize = u * 0.25f
                c.drawText("사회복지", cx, r.top + r.height() * 0.74f, text)
                c.drawText("기금", cx, r.top + r.height() * 0.9f, text)
            }
            TileType.SPACE -> {
                // 별 + 로켓
                f.color = Color.WHITE
                c.drawCircle(r.left + u * 0.25f, r.top + u * 0.25f, u * 0.04f, f)
                c.drawCircle(r.right - u * 0.3f, r.top + u * 0.45f, u * 0.05f, f)
                c.drawCircle(r.left + u * 0.35f, r.top + r.height() * 0.62f, u * 0.035f, f)
                val ry = r.top + r.height() * 0.38f
                f.color = 0xFFE53935.toInt()
                val fin = Path()
                fin.moveTo(cx - u * 0.28f, ry + u * 0.3f)
                fin.lineTo(cx + u * 0.28f, ry + u * 0.3f)
                fin.lineTo(cx, ry)
                fin.close()
                c.drawPath(fin, f)
                f.color = 0xFFECEFF1.toInt()
                c.drawOval(RectF(cx - u * 0.15f, ry - u * 0.42f, cx + u * 0.15f, ry + u * 0.32f), f)
                f.color = 0xFF29B6F6.toInt()
                c.drawCircle(cx, ry - u * 0.1f, u * 0.07f, f)
                text.color = Color.WHITE
                c.drawText("우주여행", cx, r.top + r.height() * 0.88f, text)
            }
            else -> Unit
        }
    }

    private fun drawKeyIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB300.toInt() }
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8D6E00.toInt(); style = Paint.Style.STROKE; strokeWidth = s * 0.18f
        }
        c.drawCircle(cx - s * 0.7f, cy, s * 0.55f, f)
        c.drawCircle(cx - s * 0.7f, cy, s * 0.55f, st)
        c.drawRect(cx - s * 0.2f, cy - s * 0.14f, cx + s * 1.1f, cy + s * 0.14f, f)
        c.drawRect(cx + s * 0.6f, cy, cx + s * 0.8f, cy + s * 0.45f, f)
        c.drawRect(cx + s * 0.9f, cy, cx + s * 1.1f, cy + s * 0.35f, f)
    }

    /** 칸 폭에 들어가지 않으면 두 줄로 나눈다 (비트맵 생성 시에만 호출) */
    private fun splitToFit(name: String, p: Paint, maxW: Float): Array<String> {
        if (p.measureText(name) <= maxW || name.length < 3) return arrayOf(name)
        val half = (name.length + 1) / 2
        val a = name.substring(0, half)
        val b = name.substring(half)
        // 그래도 넓으면 글자를 줄인다
        while (p.measureText(a) > maxW && p.textSize > u * 0.16f) p.textSize *= 0.92f
        return arrayOf(a, b)
    }

    private fun drawLines(c: Canvas, lines: Array<String>, sub: String, area: RectF, p: Paint, subSize: Float, subColor: Int) {
        val nameH = p.textSize * 1.08f
        val total = nameH * lines.size + subSize * 1.15f
        var y = area.centerY() - total / 2 + p.textSize * 0.92f
        for (line in lines) {
            c.drawText(line, area.centerX(), y, p)
            y += nameH
        }
        val oldSize = p.textSize
        val oldColor = p.color
        p.textSize = subSize
        p.color = subColor
        p.typeface = Typeface.DEFAULT
        c.drawText(sub, area.centerX(), y + subSize * 0.12f, p)
        p.textSize = oldSize
        p.color = oldColor
        p.typeface = Typeface.DEFAULT_BOLD
    }

    private fun drawLinesAt(c: Canvas, lines: Array<String>, cx: Float, top: Float, p: Paint) {
        var y = top + p.textSize * 0.4f
        for (line in lines) {
            c.drawText(line, cx, y, p)
            y += p.textSize * 1.08f
        }
    }

    // ─────────────────────────────────────────────
    // 매 프레임 그리기 (할당 금지 구역)
    // ─────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val bmp = boardBitmap ?: return
        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        val s = state ?: return

        // 소유자 표시 + 건물
        for (i in 0 until GameConfig.BOARD_SIZE) {
            val owner = s.owner[i]
            if (owner < 0) continue
            drawOwnership(canvas, i, GameConfig.PLAYER_COLORS[s.players[owner].colorIndex], s.level[i])
        }

        drawCenter(canvas, s)

        // 말: 현재 플레이어를 맨 위에
        val cur = s.current
        val players = s.players
        for (k in players.indices) {   // 인덱스 반복: Iterator 객체를 만들지 않는다
            val p = players[k]
            if (p.bankrupt || k == cur) continue
            drawPiece(canvas, p.id, GameConfig.PLAYER_COLORS[p.colorIndex], false)
        }
        val cp = s.players[cur]
        if (!cp.bankrupt) drawPiece(canvas, cp.id, GameConfig.PLAYER_COLORS[cp.colorIndex], !s.gameOver)
    }

    private fun drawOwnership(c: Canvas, i: Int, color: Int, level: Int) {
        val r = tileRects[i]
        // 칸 전체를 옅게 물들이고 바깥쪽에 진한 띠
        fillPaint.color = color
        fillPaint.alpha = 60
        c.drawRect(r, fillPaint)
        fillPaint.alpha = 255
        val t = u * 0.14f
        when (tileSides[i]) {
            BOTTOM -> c.drawRect(r.left, r.bottom - t, r.right, r.bottom, fillPaint)
            TOP -> c.drawRect(r.left, r.top, r.right, r.top + t, fillPaint)
            LEFT -> c.drawRect(r.left, r.top, r.left + t, r.bottom, fillPaint)
            RIGHT -> c.drawRect(r.right - t, r.top, r.right, r.bottom, fillPaint)
        }
        if (level <= 0) return
        // 건물 아이콘: 색 띠 위에 단계 수만큼
        val sz = band * 0.82f
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
            1 -> { // 별장: 집 모양
                fillPaint.color = 0xFF2E7D32.toInt()
                c.drawRect(x + s * 0.12f, y + s * 0.45f, x + s * 0.88f, y + s, fillPaint)
                c.save()
                c.translate(x, y)
                c.scale(s, s)
                c.drawPath(roofPath, fillPaint)
                c.restore()
            }
            2 -> { // 빌딩
                fillPaint.color = 0xFF1565C0.toInt()
                c.drawRect(x + s * 0.15f, y, x + s * 0.85f, y + s, fillPaint)
                c.drawRect(x + s * 0.15f, y, x + s * 0.85f, y + s, strokePaint)
            }
            else -> { // 호텔
                fillPaint.color = 0xFFC62828.toInt()
                c.drawRect(x, y + s * 0.1f, x + s, y + s, fillPaint)
                c.drawRect(x, y + s * 0.1f, x + s, y + s, strokePaint)
            }
        }
    }

    private fun drawCenter(c: Canvas, s: GameState) {
        val cx = side / 2f
        val cy = side / 2f
        textPaint.color = 0xFFAD1457.toInt()
        textPaint.textSize = u * 0.36f
        c.drawText(fundText, cx, cy - u * 1.05f, textPaint)

        if (die1 > 0 && die2 > 0) {
            val ds = u * 1.25f
            val gap = u * 0.35f
            drawDie(c, cx - gap / 2 - ds, cy - u * 0.65f, ds, die1)
            drawDie(c, cx + gap / 2, cy - u * 0.65f, ds, die2)
        }

        textPaint.color = 0xFF33691E.toInt()
        textPaint.textSize = u * 0.34f
        c.drawText(roundText, cx, cy + u * 1.3f, textPaint)

        if (selectMode) {
            textPaint.textSize = u * 0.4f
            val w = textPaint.measureText(selectText)
            tmpRect.set(cx - w / 2 - u * 0.3f, cy + u * 1.75f, cx + w / 2 + u * 0.3f, cy + u * 2.5f)
            fillPaint.color = 0xFF1A237E.toInt()
            c.drawRoundRect(tmpRect, u * 0.3f, u * 0.3f, fillPaint)
            textPaint.color = Color.WHITE
            c.drawText(selectText, cx, cy + u * 2.27f, textPaint)
        }
    }

    private fun drawDie(c: Canvas, x: Float, y: Float, s: Float, v: Int) {
        tmpRect.set(x, y, x + s, y + s)
        fillPaint.color = if (diceRolling) 0xFFFFF8E1.toInt() else Color.WHITE
        c.drawRoundRect(tmpRect, s * 0.18f, s * 0.18f, fillPaint)
        strokePaint.color = 0xFF424242.toInt()
        strokePaint.strokeWidth = s * 0.05f
        c.drawRoundRect(tmpRect, s * 0.18f, s * 0.18f, strokePaint)
        fillPaint.color = if (v == 1) 0xFFD32F2F.toInt() else 0xFF212121.toInt()
        val pips = PIPS[v - 1]
        val r = s * (if (v == 1) 0.13f else 0.095f)
        var k = 0
        while (k < pips.size) {
            c.drawCircle(x + pips[k] * s, y + pips[k + 1] * s, r, fillPaint)
            k += 2
        }
    }

    private fun drawPiece(c: Canvas, playerId: Int, color: Int, highlight: Boolean) {
        val x: Float
        val y: Float
        if (playerId == movingPlayer) {
            x = movingX; y = movingY
        } else {
            val t = pieceTile[playerId]
            x = pieceX(t) + OFF_X[playerId] * u * 0.24f
            y = pieceY(t) + OFF_Y[playerId] * u * 0.2f
        }
        val rad = u * 0.2f
        // 그림자
        fillPaint.color = 0x55000000
        c.drawCircle(x + rad * 0.12f, y + rad * 0.2f, rad, fillPaint)
        fillPaint.color = color
        c.drawCircle(x, y, rad, fillPaint)
        strokePaint.color = if (highlight) Color.WHITE else 0xFF212121.toInt()
        strokePaint.strokeWidth = if (highlight) rad * 0.32f else rad * 0.16f
        c.drawCircle(x, y, rad, strokePaint)
        if (highlight) {
            strokePaint.color = 0xFF212121.toInt()
            strokePaint.strokeWidth = rad * 0.1f
            c.drawCircle(x, y, rad * 1.18f, strokePaint)
        }
    }

    private fun setMovingPoint(playerId: Int, a: Int, b: Int, frac: Float, hop: Float) {
        val ox = OFF_X[playerId] * u * 0.24f
        val oy = OFF_Y[playerId] * u * 0.2f
        movingX = pieceX(a) + (pieceX(b) - pieceX(a)) * frac + ox
        movingY = pieceY(a) + (pieceY(b) - pieceY(a)) * frac + oy - sin(frac * PI).toFloat() * hop
    }

    /** 말 기준점: 칸 가운데에서 바깥쪽으로 조금 (이름을 덜 가리도록) */
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
                val idx = tileAt(event.x, event.y)
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

        private const val CENTER_BG = 0xFFDCEDC8.toInt()

        /** 한 칸에 여러 말이 있을 때의 위치 (플레이어 0~3) */
        private val OFF_X = floatArrayOf(-1f, 1f, -1f, 1f)
        private val OFF_Y = floatArrayOf(-1f, -1f, 1f, 1f)

        private val RANDOM = java.util.Random()

        /** 주사위 눈 위치 (0~1 정규 좌표, x,y 쌍) */
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
