package com.siwoo.party.games.stack.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.stack.DropKind
import com.siwoo.party.games.stack.DropResult
import com.siwoo.party.games.stack.GameConfig
import com.siwoo.party.games.stack.StackGame
import kotlin.random.Random

/**
 * 탑 화면: 밤하늘 + 땅 + 쌓인 블록(놓은 사람 색) + 좌우로 움직이는 블록 + 떨어지는 조각.
 * 세로 좌표는 "층" 단위 (받침 = 0층, 블록 하나 높이 = 1층). 카메라(cam = 화면 맨 아래 층)는 탑을 따라 부드럽게 올라간다.
 * 모든 움직임은 게임 시계(now, 일시정지 제외)로 계산하고, Activity 의 프레임 루프가 [now] 를 넣고 invalidate 한다.
 * 이 View 는 스스로 반복해서 그리지 않는다. onDraw 할당 없음.
 */
class TowerView(context: Context) : View(context) {

    fun interface Listener {
        fun onTap()
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: StackGame? = null
    private var colors = IntArray(GameConfig.MAX_PLAYERS)
    private var initials: Array<String> = emptyArray()

    /** 게임 시계 (프레임마다 Activity 가 넣음) */
    var now = 0L

    /** 움직이는 블록을 보일지 (차례 중) */
    var showMoving = false
        set(v) { field = v; invalidate() }

    /** 위쪽 안내 한 줄 */
    var hint = ""
        set(v) { if (field != v) { field = v; invalidate() } }

    // ── 배치 ──
    /** 판 1칸 = scale 픽셀 */
    private var scale = 1f
    private var fieldLeft = 0f
    private var groundY = 0f
    /** 블록 높이 (픽셀) */
    var blockPx = 1f
        private set
    private var visibleLevels = 8f

    // ── 카메라 ──
    private var camFrom = 0f
    private var camTo = 0f
    private var camStart = 0L
    private var camMs = 1L

    // ── 떨어뜨리는 중 ──
    private var dropping = false
    private var dropX = 0f
    private var dropW = 0f
    private var dropFrom = 0f
    private var dropTo = 0f
    private var dropStart = 0L
    private var dropMs = 1L
    private var dropColor = 0
    private var dropInitial = ""
    /** 떨어지는 동안 탑 맨 위(방금 놓인 블록)는 숨김 */
    private var hideTop = false

    // ── 떨어지는 조각 (고정 풀) ──
    private class Piece {
        var on = false
        var x = 0f
        var w = 0f
        var level = 0f
        var dist = 0f
        var dir = 1f
        var color = 0
        var start = 0L
        var ms = 1L
    }
    private val pieces = Array(3) { Piece() }

    // ── 퍼펙트 빛 ──
    private var flashLevel = -1
    private var flashStart = 0L
    private var flashMs = 1L

    // ── 그리기 도구 ──
    private val sky = Paint()
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x55FFFFFF }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Theme.GOLD_LIGHT }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val ruler = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = 0x99FFFFFF.toInt() }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val hintShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xAA000000.toInt(); style = Paint.Style.STROKE }
    private val rect = RectF()
    private val starX = FloatArray(STARS)
    private val starY = FloatArray(STARS)
    private val starR = FloatArray(STARS)

    init {
        val r = Random(7)
        for (k in 0 until STARS) {
            starX[k] = r.nextFloat()
            starY[k] = r.nextFloat()
            starR[k] = 0.6f + r.nextFloat() * 1.2f
        }
        isClickable = true
    }

    fun bind(g: StackGame, playerColors: IntArray, names: List<String>) {
        game = g
        colors = playerColors
        initials = Array(names.size) { names[it].take(1) }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val side = 26f * d
        scale = (w - side * 2f) / GameConfig.FIELD_W
        fieldLeft = side
        blockPx = GameConfig.BLOCK_H * scale
        groundY = h - 22f * d
        visibleLevels = groundY / blockPx
        sky.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF0A0F35.toInt(), 0xFF1B2A6B.toInt(), 0xFF3A3F8F.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        edge.strokeWidth = 1.2f * d
        glow.strokeWidth = 3.5f * d
        guide.strokeWidth = 1.2f * d
        guide.pathEffect = DashPathEffect(floatArrayOf(5f * d, 5f * d), 0f)
        initPaint.textSize = blockPx * 0.52f
        ruler.textSize = 10f * d
        hintPaint.textSize = 15f * d
        hintShadow.textSize = 15f * d
        hintShadow.strokeWidth = 3.5f * d
        val target = targetCam()
        camFrom = target
        camTo = target
    }

    // ── 카메라 ──

    /** 맨 위 블록과 그 위 움직이는 블록이 화면 위쪽 1/4 아래에 오도록 */
    private fun targetCam(): Float {
        val g = game ?: return 0f
        return maxOf(0f, g.tower.size + HOVER_GAP + 1f - visibleLevels * 0.74f)
    }

    fun cam(): Float {
        val f = ((now - camStart).toFloat() / camMs).coerceIn(0f, 1f)
        val e = 1f - (1f - f) * (1f - f)
        return camFrom + (camTo - camFrom) * e
    }

    /** 탑 높이에 맞춰 스크롤 시작 (라운드가 바뀌면 바로 맞춤) */
    fun scrollToTop(ms: Long, instant: Boolean = false) {
        val c = cam()
        camFrom = if (instant) targetCam() else c
        camTo = targetCam()
        camStart = now
        camMs = maxOf(1L, ms)
        invalidate()
    }

    private fun yOf(level: Float, cam: Float): Float = groundY - (level - cam) * blockPx
    private fun xOf(units: Float): Float = fieldLeft + units * scale

    /** 움직이는 블록이 떠 있는 층 */
    private fun hoverLevel(): Float = (game?.tower?.size ?: 1) + HOVER_GAP

    // ── 연출 시작 ──

    /** 터치 직후: 움직이던 블록이 탑 위로 떨어진다 (빗나감이면 바로 조각으로 떨어짐) */
    fun startDrop(r: DropResult, ms: Long, color: Int) {
        showMoving = false
        dropX = r.droppedX
        dropW = if (r.kind == DropKind.PERFECT) r.placedW else r.placedW + r.cutW
        dropFrom = r.level + HOVER_GAP
        dropTo = r.level.toFloat()
        dropStart = now
        dropMs = maxOf(1L, ms)
        dropColor = color
        dropInitial = if (r.player in initials.indices) initials[r.player] else ""
        dropping = true
        hideTop = r.kind != DropKind.MISS
        invalidate()
    }

    /** 떨어뜨림이 끝났는지 */
    fun dropDone(): Boolean = dropping && now - dropStart >= dropMs

    fun endDrop() {
        dropping = false
        hideTop = false
        invalidate()
    }

    /** 잘린 조각(또는 빗나간 블록)을 떨어뜨림 */
    fun addPiece(x: Float, w: Float, level: Float, color: Int, dir: Float, ms: Long) {
        var p = pieces[0]
        for (q in pieces) if (!q.on) { p = q; break }
        p.on = true
        p.x = x
        p.w = w
        p.level = level
        p.dir = dir
        p.color = color
        p.start = now
        p.ms = maxOf(1L, ms)
        // 화면 아래로 완전히 사라질 만큼
        p.dist = level - minOf(cam(), camTo) + 2.5f
        invalidate()
    }

    fun flash(level: Int, ms: Long) {
        flashLevel = level
        flashStart = now
        flashMs = maxOf(1L, ms)
        invalidate()
    }

    /** 새 라운드: 연출 모두 지우고 카메라 바닥으로 */
    fun resetRound() {
        dropping = false
        hideTop = false
        for (p in pieces) p.on = false
        flashLevel = -1
        scrollToTop(1L, instant = true)
    }

    /** 아직 진행 중인 연출이 있는지 (프레임 루프 유지 판단) */
    fun busy(): Boolean {
        if (dropping) return true
        if (now - camStart < camMs) return true
        if (flashLevel >= 0 && now - flashStart < flashMs) return true
        for (p in pieces) if (p.on) {
            if (now - p.start < p.ms) return true
            p.on = false
        }
        return false
    }

    /** 테스트용: 떨어지는 조각 수 */
    val fallingPieces: Int get() = pieces.count { it.on && now - it.start < it.ms }

    // ── 그리기 ──

    private fun block(c: Canvas, x: Float, w: Float, level: Float, cam: Float, color: Int, alpha: Int = 255) {
        val l = xOf(x)
        val r = xOf(x + w)
        val b = yOf(level, cam)
        val t = b - blockPx
        if (b < -blockPx || t > height + blockPx) return
        val rr = minOf(4f * d, (r - l) / 3f)
        rect.set(l, t, r, b)
        fill.color = Theme.shade(color, 0.62f)
        fill.alpha = alpha
        c.drawRoundRect(rect, rr, rr, fill)
        rect.set(l, t, r, b - blockPx * 0.16f)
        fill.color = color
        fill.alpha = alpha
        c.drawRoundRect(rect, rr, rr, fill)
        rect.set(l, t, r, t + blockPx * 0.24f)
        fill.color = Theme.shade(color, 1.35f)
        fill.alpha = alpha
        c.drawRoundRect(rect, rr, rr, fill)
        rect.set(l, t, r, b)
        edge.color = 0x66000000
        edge.alpha = alpha * 0x66 / 255
        c.drawRoundRect(rect, rr, rr, edge)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val g = game ?: return
        val cam = cam()
        c.drawRect(0f, 0f, w, h, sky)
        // 별 (카메라보다 천천히 움직임)
        val drift = cam * blockPx * 0.3f
        for (k in 0 until STARS) {
            var y = (starY[k] * h + drift) % h
            if (y < 0f) y += h
            c.drawCircle(starX[k] * w, y, starR[k] * d, star)
        }
        // 땅
        val gy = yOf(0f, cam)
        if (gy < h) {
            fill.color = 0xFF2E7D32.toInt()
            c.drawRect(0f, gy, w, h, fill)
            fill.color = 0xFF5D4037.toInt()
            c.drawRect(0f, gy + 7f * d, w, h, fill)
            fill.color = 0xFF66BB6A.toInt()
            c.drawRect(0f, gy, w, gy + 3f * d, fill)
        }
        // 층 눈금 (5층마다)
        val lo = maxOf(5, ((cam.toInt() / 5) * 5))
        var lv = lo
        while (lv < cam + visibleLevels + 1f && lv < FLOOR_LABELS.size) {
            val y = yOf(lv.toFloat(), cam)
            edge.color = 0x55FFFFFF
            c.drawLine(2f * d, y, fieldLeft - 4f * d, y, edge)
            c.drawText(FLOOR_LABELS[lv], 3f * d, y - 3f * d, ruler)
            lv += 5
        }
        // 탑
        val n = g.tower.size - (if (hideTop) 1 else 0)
        for (i in 0 until n) {
            val b = g.tower[i]
            block(c, b.x, b.w, i.toFloat(), cam, if (b.owner < 0) BASE_COLOR else colors[b.owner])
        }
        // 퍼펙트 빛
        if (flashLevel in 0 until g.tower.size && !hideTop) {
            val f = (now - flashStart).toFloat() / flashMs
            if (f in 0f..1f) {
                val b = g.tower[flashLevel]
                val grow = f * 7f * d
                glow.alpha = ((1f - f) * 255).toInt()
                rect.set(xOf(b.x) - grow, yOf(flashLevel + 1f, cam) - grow, xOf(b.right) + grow, yOf(flashLevel.toFloat(), cam) + grow)
                c.drawRoundRect(rect, 6f * d, 6f * d, glow)
            }
        }
        // 움직이는 블록 + 안내 점선
        if (showMoving && g.tower.isNotEmpty()) {
            val top = g.top
            val hv = hoverLevel()
            val y0 = yOf(g.tower.size.toFloat(), cam)
            val y1 = yOf(hv, cam)
            c.drawLine(xOf(top.x), y0, xOf(top.x), y1, guide)
            c.drawLine(xOf(top.right), y0, xOf(top.right), y1, guide)
            val x = g.blockX(now)
            val col = colors[g.current]
            block(c, x, g.width, hv, cam, col)
            initPaint.color = Theme.textOn(col)
            if (g.width * scale > initPaint.textSize * 1.2f) {
                c.drawText(initials[g.current], xOf(x + g.width / 2f), yOf(hv, cam) - blockPx * 0.5f + initPaint.textSize * 0.3f, initPaint)
            }
        }
        // 떨어지는 블록
        if (dropping) {
            val f = ((now - dropStart).toFloat() / dropMs).coerceIn(0f, 1f)
            val lvl = dropFrom + (dropTo - dropFrom) * f * f
            block(c, dropX, dropW, lvl, cam, dropColor)
            initPaint.color = Theme.textOn(dropColor)
            if (dropW * scale > initPaint.textSize * 1.2f) {
                c.drawText(dropInitial, xOf(dropX + dropW / 2f), yOf(lvl, cam) - blockPx * 0.5f + initPaint.textSize * 0.3f, initPaint)
            }
        }
        // 잘린 조각 (기울며 떨어지고 끝에서 흐려짐)
        for (p in pieces) {
            if (!p.on) continue
            val f = (now - p.start).toFloat() / p.ms
            if (f < 0f || f > 1f) continue
            val lvl = p.level - p.dist * f * f
            val sx = p.dir * f * PIECE_DRIFT
            val cx = xOf(p.x + sx + p.w / 2f)
            val cy = yOf(lvl, cam) - blockPx / 2f
            val a = if (f > 0.7f) 1f - (f - 0.7f) / 0.3f else 1f
            c.save()
            c.rotate(p.dir * f * 75f, cx, cy)
            block(c, p.x + sx, p.w, lvl, cam, p.color, (a * 255).toInt())
            c.restore()
        }
        // 안내
        if (hint.isNotEmpty()) {
            c.drawText(hint, w / 2f, 24f * d, hintShadow)
            c.drawText(hint, w / 2f, 24f * d, hintPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            listener?.onTap()
            return true
        }
        return event.actionMasked == MotionEvent.ACTION_UP || super.onTouchEvent(event)
    }

    companion object {
        /** 움직이는 블록이 탑 위에 떠 있는 간격 (층) */
        const val HOVER_GAP = 0.9f
        /** 떨어지는 조각이 옆으로 밀려나는 거리 (칸) */
        private const val PIECE_DRIFT = 6f
        private const val STARS = 46
        private const val BASE_COLOR = 0xFF8D99AE.toInt()
        private val FLOOR_LABELS = Array(301) { "${it}층" }
    }
}
