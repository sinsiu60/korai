package com.siwoo.party.games.airhockey.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.airhockey.AirHockeyGame
import com.siwoo.party.games.airhockey.GameConfig

/**
 * 에어하키 판 (네온 테이블 + 말렛 둘 + 퍽 + 점수). 좌표는 AirHockeyGame 의 판 단위를 그대로 그린다.
 * - 판(바닥·선·테두리·골문)은 크기가 바뀔 때 비트맵에 한 번 그려 두고 매 프레임 붙이기만 한다 (release 에서 recycle)
 * - 손가락: 누른 자리가 왼쪽 반이면 0번, 오른쪽 반이면 1번 말렛을 그 손가락(포인터 id)이 뗄 때까지 움직인다.
 *   같은 반쪽의 두 번째 손가락은 무시. 가운데 선을 넘어가도 말렛은 선에서 멈춘다(게임이 자름).
 * - 골 연출(하얀 번쩍임 + 점수 숫자 커짐)은 짧은 ValueAnimator 로, 연출 중에만 다시 그린다.
 * 움직임은 화면(Activity)의 프레임 루프가 game.advance 뒤 invalidate 한다 — 이 View 는 스스로 반복해서 그리지 않는다.
 */
class TableView(context: Context) : View(context) {

    interface Listener {
        /** player 의 손가락이 판 단위 (x, y) 에 있음 (누름·끌기) */
        fun onDrag(player: Int, x: Float, y: Float)
        /** player 의 손가락을 뗌 */
        fun onRelease(player: Int)
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: AirHockeyGame? = null
    private val colors = IntArray(GameConfig.PLAYERS)
    private val names = arrayOf("", "")

    // ── 배치 (onSizeChanged) ──
    /** 판 단위 1 = unitPx 픽셀, 판 왼쪽 위 = (ox, oy) */
    var unitPx = 1f
        private set
    var ox = 0f
        private set
    var oy = 0f
        private set
    private var tableBmp: Bitmap? = null

    // ── 손가락 (포인터 id, −1 = 없음) ──
    private val pointer = intArrayOf(-1, -1)

    // ── 퍽 잔상 (최근 프레임 자리, 고리 버퍼) ──
    private val trailX = FloatArray(GameConfig.TRAIL)
    private val trailY = FloatArray(GameConfig.TRAIL)
    private var trailN = 0
    private var trailAt = 0

    // ── 글자 (값이 바뀔 때만 만든다) ──
    private val scoreText = Array(GameConfig.WIN_SCORE + 1) { it.toString() }
    private val shownScore = IntArray(GameConfig.PLAYERS)
    /** 판 위 안내 (시작 전) — 빈 글자면 안 그림 */
    private var hint = ""
    private val goalLabel = "먼저 " + GameConfig.WIN_SCORE + "점!"

    // ── 골 연출 ──
    /** 골 연출 진행 0..1 (−1 = 없음) */
    private var fxT = -1f
    private var fxScorer = -1
    private var flashMs = GameConfig.GOAL_FLASH_MS
    private var popMs = GameConfig.SCORE_POP_MS
    private var fxAnim: ValueAnimator? = null
    /** 이긴 사람 (−1 = 아직) */
    private var winner = -1

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = 0xDD000000.toInt()
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        isClickable = true
    }

    /** 게임·이름·색 연결 */
    fun bind(game: AirHockeyGame, playerNames: Array<String>, playerColors: IntArray) {
        this.game = game
        for (i in colors.indices) {
            colors[i] = playerColors[i]
            names[i] = playerNames[i]
        }
        if (width > 0) buildTable()
        invalidate()
    }

    /** 점수 표시 갱신 */
    fun setScores(a: Int, b: Int) {
        shownScore[0] = a.coerceIn(0, GameConfig.WIN_SCORE)
        shownScore[1] = b.coerceIn(0, GameConfig.WIN_SCORE)
        invalidate()
    }

    fun setHint(text: String) {
        hint = text
        invalidate()
    }

    fun setWinner(p: Int) {
        winner = p
        invalidate()
    }

    /** 연출 시간 (속도 배율 적용된 값) */
    fun setFxTimes(flash: Long, pop: Long) {
        flashMs = flash.coerceAtLeast(1)
        popMs = pop.coerceAtLeast(1)
    }

    /** 판 단위 (x, y) → 이 View 좌표 */
    fun toScreen(x: Float, y: Float, out: PointF) {
        out.set(ox + x * unitPx, oy + y * unitPx)
    }

    /** View x → 판 단위 */
    fun unitX(px: Float): Float = (px - ox) / unitPx
    fun unitY(py: Float): Float = (py - oy) / unitPx

    /** 지금 손가락이 잡고 있는 포인터 id (테스트·디버그용, −1 = 없음) */
    fun pointerOf(player: Int): Int = pointer[player]

    // ── 잔상 ──

    /** 프레임마다 퍽 자리를 잔상에 넣는다 */
    fun recordTrail() {
        val g = game ?: return
        if (!g.puck.active) return
        trailX[trailAt] = g.puck.x
        trailY[trailAt] = g.puck.y
        trailAt = (trailAt + 1) % GameConfig.TRAIL
        if (trailN < GameConfig.TRAIL) trailN++
    }

    fun clearTrail() {
        trailN = 0
        trailAt = 0
    }

    // ── 골 연출 ──

    /** 골: 하얀 번쩍임 + 득점한 사람 점수 숫자 커짐 */
    fun goalFx(scorer: Int) {
        fxScorer = scorer
        fxAnim?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = maxOf(flashMs, popMs)
        a.interpolator = LinearInterpolator()
        a.addUpdateListener {
            fxT = it.animatedValue as Float
            invalidate()
        }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                fxT = -1f
                invalidate()
            }
        })
        fxAnim = a
        fxT = 0f
        a.start()
    }

    /** 일시정지: 연출 멈춤 + 잡고 있던 손가락 놓기 */
    fun pauseFx() {
        fxAnim?.let { if (it.isRunning) it.pause() }
        releaseAll()
    }

    fun resumeFx() {
        fxAnim?.let { if (it.isPaused) it.resume() }
    }

    /** 잡고 있던 손가락 모두 놓기 */
    fun releaseAll() {
        for (p in pointer.indices) {
            if (pointer[p] != -1) {
                pointer[p] = -1
                listener?.onRelease(p)
            }
        }
    }

    fun release() {
        fxAnim?.cancel()
        fxAnim = null
        tableBmp?.recycle()
        tableBmp = null
    }

    // ── 크기·판 비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val side = 6f * d
        val vert = 22f * d
        unitPx = minOf(
            (w - 2f * side) / (GameConfig.TABLE_W + 2f * HockeyArt.POCKET_DEPTH),
            (h - 2f * vert) / GameConfig.TABLE_H,
        )
        ox = (w - GameConfig.TABLE_W * unitPx) / 2f
        oy = (h - GameConfig.TABLE_H * unitPx) / 2f
        label.textSize = 15f * d
        outline.textSize = 15f * d
        outline.strokeWidth = 3.5f * d
        big.textSize = 7.5f * unitPx
        buildTable()
    }

    private fun buildTable() {
        if (width <= 0 || height <= 0) return
        tableBmp?.recycle()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        HockeyArt.table(c, Paint(Paint.ANTI_ALIAS_FLAG), ox, oy, unitPx, colors[0], colors[1], RectF(), true)
        tableBmp = bmp
    }

    // ── 그리기 (할당 없음) ──

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val bmp = tableBmp
        if (bmp != null) canvas.drawBitmap(bmp, 0f, 0f, bmpPaint)
        val u = unitPx
        val w = GameConfig.TABLE_W * u
        val h = GameConfig.TABLE_H * u
        val popping = fxT >= 0f
        val popF = if (popping) (fxT * maxOf(flashMs, popMs) / popMs).coerceAtMost(1f) else 1f
        val flashF = if (popping) (fxT * maxOf(flashMs, popMs) / flashMs).coerceAtMost(1f) else 1f

        // 골문 번쩍 (득점당한 골문이 득점한 사람 색으로)
        if (popping && fxScorer >= 0) {
            val conceded = 1 - fxScorer
            val gx = if (conceded == 0) ox else ox + w
            val gy = oy + h / 2f
            fill.style = Paint.Style.FILL
            fill.color = HockeyArt.alpha(colors[fxScorer], (200 * (1f - popF)).toInt())
            canvas.drawCircle(gx, gy, (GameConfig.GOAL_W * 0.5f + 2.5f * popF) * u, fill)
        }

        // 점수 (각 반쪽 가운데에 크게, 옅게)
        for (p in 0 until GameConfig.PLAYERS) {
            val cx = ox + (if (p == 0) 0.25f else 0.75f) * w
            val cy = oy + h / 2f + big.textSize * 0.36f
            var scale = 1f
            var a = 0x40
            if (popping && p == fxScorer) {
                val s = if (popF < 0.3f) popF / 0.3f else 1f - (popF - 0.3f) / 0.7f
                scale = 1f + 0.55f * s
                a = 0x40 + (0x9F * s).toInt()
            }
            if (winner == p) a = 0xC0
            big.color = HockeyArt.alpha(colors[p], a)
            canvas.save()
            canvas.scale(scale, scale, cx, oy + h / 2f)
            canvas.drawText(scoreText[shownScore[p]], cx, cy, big)
            canvas.restore()
        }

        // 이름 (각 반쪽 위)
        val ny = oy + 1.15f * u + label.textSize * 0.36f
        for (p in 0 until GameConfig.PLAYERS) {
            val cx = ox + (if (p == 0) 0.25f else 0.75f) * w
            canvas.drawText(names[p], cx, ny, outline)
            label.color = colors[p]
            canvas.drawText(names[p], cx, ny, label)
        }
        // 목표 점수 (판 위 가운데, 테두리 바깥)
        val ty = oy - 7f * d
        canvas.drawText(goalLabel, ox + w / 2f, ty, outline)
        label.color = Theme.GOLD
        canvas.drawText(goalLabel, ox + w / 2f, ty, label)
        // 안내 (시작 전, 각 반쪽 아래)
        if (hint.isNotEmpty()) {
            val hy = oy + h - 1.2f * u
            label.color = Theme.TEXT
            for (p in 0 until GameConfig.PLAYERS) {
                val cx = ox + (if (p == 0) 0.25f else 0.75f) * w
                canvas.drawText(hint, cx, hy, outline)
                canvas.drawText(hint, cx, hy, label)
            }
        }

        // 퍽 잔상 + 퍽
        val puck = g.puck
        if (puck.active) {
            val pr = GameConfig.PUCK_R * u
            fill.style = Paint.Style.FILL
            for (k in 0 until trailN) {
                // 오래된 것부터: 작고 옅게
                val idx = (trailAt - trailN + k + GameConfig.TRAIL) % GameConfig.TRAIL
                val f = (k + 1).toFloat() / (trailN + 1)
                fill.color = HockeyArt.alpha(Theme.GOLD, (70 * f).toInt())
                canvas.drawCircle(ox + trailX[idx] * u, oy + trailY[idx] * u, pr * (0.45f + 0.45f * f), fill)
            }
            val glow = (puck.speed / GameConfig.PUCK_MAX_SPEED).coerceIn(0f, 1f)
            HockeyArt.puck(canvas, art, ox + puck.x * u, oy + puck.y * u, pr, glow)
        }

        // 말렛
        val mr = GameConfig.MALLET_R * u
        for (p in 0 until GameConfig.PLAYERS) {
            val m = g.mallets[p]
            HockeyArt.mallet(canvas, art, ox + m.x * u, oy + m.y * u, mr, colors[p])
        }

        // 하얀 번쩍임
        if (popping && flashF < 1f) {
            fill.style = Paint.Style.FILL
            fill.color = HockeyArt.alpha(0xFFFFFFFF.toInt(), (190 * (1f - flashF) * (1f - flashF)).toInt())
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        }
    }

    // ── 손가락 (포인터별) ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                // 누른 자리의 반쪽 주인 (가운데 선 기준)
                val p = if (x < ox + GameConfig.CENTER_X * unitPx) 0 else 1
                if (pointer[p] == -1) {
                    pointer[p] = e.getPointerId(i)
                    listener?.onDrag(p, unitX(x), unitY(e.getY(i)))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (p in pointer.indices) {
                    val id = pointer[p]
                    if (id == -1) continue
                    val i = e.findPointerIndex(id)
                    if (i < 0) continue
                    listener?.onDrag(p, unitX(e.getX(i)), unitY(e.getY(i)))
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val id = e.getPointerId(e.actionIndex)
                for (p in pointer.indices) {
                    if (pointer[p] == id) {
                        pointer[p] = -1
                        listener?.onRelease(p)
                    }
                }
                if (e.actionMasked == MotionEvent.ACTION_UP) releaseAll()
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        return true
    }
}
