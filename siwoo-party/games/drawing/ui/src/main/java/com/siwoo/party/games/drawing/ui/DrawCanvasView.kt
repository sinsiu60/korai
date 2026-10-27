package com.siwoo.party.games.drawing.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.games.drawing.GameConfig
import com.siwoo.party.games.drawing.StrokeModel

/**
 * 손가락 그림판.
 * - 선은 StrokeModel(고정 크기 float 배열)에 저장. 좌표 = px / 캔버스 높이.
 * - 완성된 선은 committed 비트맵에 바로 그려 두고, onDraw 는 비트맵 1장 + 그리는 중인 선(live Path 1개)만 그린다.
 * - 되돌리기 = committed 를 바탕(base) + 남은 선 목록으로 다시 그림.
 * - 선 수·점 수 한도를 넘어 굳힌 오래된 선은 base 비트맵에 그린다 (되돌리기 불가).
 * - 아주 긴 선은 모델이 끊어(SPLIT) 앞부분을 바로 비트맵에 합친다 → live Path 가 끝없이 길어지지 않음.
 * - Path 는 처음에 2개(live, tmp)만 만들고 재사용, 터치·그리기 중 할당 없음. 비트맵은 release() 에서 recycle.
 */
class DrawCanvasView(context: Context) : View(context) {

    val model = StrokeModel()

    /** 지금 색 (0..3 펜, GameConfig.ERASER = 지우개) */
    var color = 0
    /** 굵기 0 = 가늘게, 1 = 굵게 */
    var widthIndex = 0
    /** 그릴 수 있는지 (그리는 사람 차례 중에만) */
    var touchEnabled = false
        set(v) {
            if (!v) endStroke()
            field = v
        }
    /** 선 하나를 다 그렸을 때 */
    var onStrokeEnd: Runnable? = null

    private var base: Bitmap? = null
    private var baseCanvas: Canvas? = null
    private var baseUsed = false
    private var committed: Bitmap? = null
    private var committedCanvas: Canvas? = null

    private val live = newPath()
    private val tmp = newPath()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33000000 }
    private val srcRect = Rect()
    private val dstRect = RectF()
    private var lastX = 0f
    private var lastY = 0f
    private var activePointer = -1
    /** model.add 도중 (끊김 처리 중 굳히기 확인용) */
    private var adding = false

    /** 마지막 onDraw 에서 drawPath 를 부른 횟수 (성능 점검) */
    var pathsLastFrame = 0
        private set
    /** 지금까지 한 프레임에서 drawPath 를 가장 많이 부른 횟수 */
    var maxPathsPerFrame = 0
        private set
    /** 그린 프레임 수 */
    var frames = 0L
        private set

    init {
        model.onBake = StrokeModel.BakeListener { m, i -> bake(m, i) }
    }

    /** 캔버스 높이 (좌표 단위) */
    private val unit: Float get() = if (height > 0) height.toFloat() else 1f

    // ── 바깥에서 부르는 것 ──

    /** 마지막 선 지우기 */
    fun undo(): Boolean {
        if (model.drawing) endStroke()
        if (!model.undo()) return false
        redrawAll()
        invalidate()
        return true
    }

    /** 전체 지우기 (굳힌 선까지) */
    fun clearAll() {
        model.clear()
        live.reset()
        activePointer = -1
        baseUsed = false
        base?.eraseColor(PAPER)
        committed?.eraseColor(PAPER)
        invalidate()
    }

    /** 그리는 중인 선을 여기서 끝냄 (시간 끝·일시정지·맞힘) */
    fun endStroke() {
        if (!model.drawing) return
        model.end()
        commit(model.strokeCount - 1)
        live.reset()
        activePointer = -1
        invalidate()
    }

    /** 비트맵 정리 (onGameDestroy) */
    fun release() {
        base?.recycle()
        committed?.recycle()
        base = null
        committed = null
        baseCanvas = null
        committedCanvas = null
    }

    val released: Boolean get() = committed == null

    // ── 크기 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        border.strokeWidth = h * 0.006f
        val oldBase = base
        val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        nb.eraseColor(PAPER)
        val bc = Canvas(nb)
        if (oldBase != null && baseUsed && oldh > 0) {
            // 굳힌 선은 크기에 맞춰 늘려 옮긴다 (좌표 단위 = 높이)
            val s = h.toFloat() / oldh
            srcRect.set(0, 0, oldBase.width, oldBase.height)
            dstRect.set(0f, 0f, oldBase.width * s, oldBase.height * s)
            bc.drawBitmap(oldBase, srcRect, dstRect, bitmapPaint)
        }
        oldBase?.recycle()
        base = nb
        baseCanvas = bc
        committed?.recycle()
        val nc = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        committed = nc
        committedCanvas = Canvas(nc)
        redrawAll()
        if (model.drawing) rebuildLive()
    }

    // ── 터치 (할당 없음) ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!touchEnabled || committed == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = e.getPointerId(0)
                val x = e.x
                val y = e.y
                model.begin(x / unit, y / unit, color, widthIndex)
                live.reset()
                live.moveTo(x, y)
                lastX = x
                lastY = y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!model.drawing) return true
                val idx = e.findPointerIndex(activePointer)
                if (idx < 0) return true
                for (h in 0 until e.historySize) addPoint(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h))
                addPoint(e.getX(idx), e.getY(idx))
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // 그리던 손가락만 떼면 선 끝
                if (e.getPointerId(e.actionIndex) == activePointer) finishStroke()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishStroke()
        }
        return true
    }

    private fun finishStroke() {
        if (!model.drawing) return
        endStroke()
        onStrokeEnd?.run()
    }

    private fun addPoint(x: Float, y: Float) {
        adding = true
        val r = model.add(x / unit, y / unit)
        adding = false
        when (r) {
            StrokeModel.ADDED -> {
                live.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + y) / 2f)
                lastX = x
                lastY = y
            }
            StrokeModel.SPLIT -> {
                // 앞 선은 꽉 참 → 비트맵에 합치고, 이음점에서 live 를 새로
                commit(model.strokeCount - 2)
                live.reset()
                live.moveTo(lastX, lastY)
                live.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + y) / 2f)
                lastX = x
                lastY = y
            }
            else -> Unit
        }
    }

    /** 모델에서 live Path 다시 만들기 (크기 바뀜) */
    private fun rebuildLive() {
        val i = model.current
        if (i < 0) return
        buildPath(live, i, closeEnd = false)
        val n = model.len(i)
        lastX = model.x(i, n - 1) * unit
        lastY = model.y(i, n - 1) * unit
    }

    // ── 선 그리기 ──

    /** 선 i 를 path 에 (중간점 2차 곡선, 터치 때와 같은 모양) */
    private fun buildPath(path: Path, i: Int, closeEnd: Boolean) {
        val u = unit
        val n = model.len(i)
        path.reset()
        var px = model.x(i, 0) * u
        var py = model.y(i, 0) * u
        path.moveTo(px, py)
        for (k in 1 until n) {
            val x = model.x(i, k) * u
            val y = model.y(i, k) * u
            path.quadTo(px, py, (px + x) / 2f, (py + y) / 2f)
            px = x
            py = y
        }
        if (closeEnd && n > 1) path.lineTo(px, py)
    }

    private fun widthPx(colorIndex: Int, w: Int): Float {
        val base = if (w == 0) GameConfig.WIDTH_THIN else GameConfig.WIDTH_THICK
        val scale = if (colorIndex == GameConfig.ERASER) (if (w == 0) GameConfig.WIDTH_THICK / GameConfig.WIDTH_THIN else GameConfig.ERASER_SCALE) else 1f
        return base * scale * unit
    }

    private fun paintFor(i: Int): Paint {
        val c = model.color(i)
        strokePaint.color = if (c == GameConfig.ERASER) PAPER else PEN_COLORS[c.coerceIn(0, PEN_COLORS.size - 1)]
        strokePaint.strokeWidth = widthPx(c, model.width(i))
        return strokePaint
    }

    /** 선 i 를 캔버스 c 에 (점 하나면 동그라미) */
    private fun drawStroke(c: Canvas, i: Int) {
        val p = paintFor(i)
        if (model.len(i) == 1) {
            dotPaint.color = p.color
            c.drawCircle(model.x(i, 0) * unit, model.y(i, 0) * unit, p.strokeWidth / 2f, dotPaint)
            return
        }
        buildPath(tmp, i, closeEnd = true)
        c.drawPath(tmp, p)
    }

    private fun commit(i: Int) {
        val cc = committedCanvas ?: return
        if (i < 0 || i >= model.strokeCount) return
        drawStroke(cc, i)
    }

    private fun bake(m: StrokeModel, i: Int) {
        val bc = baseCanvas ?: return
        drawStroke(bc, i)
        baseUsed = true
        // 방금 꽉 차서 끊긴 선(아직 비트맵에 안 그린 선)이 바로 굳는 경우 → 화면용 비트맵에도
        if (adding && i == m.strokeCount - 1) commit(i)
    }

    /** committed = base + 남은 선 모두 (그리는 중인 선 제외) */
    private fun redrawAll() {
        val cc = committedCanvas ?: return
        val b = base
        if (b != null && baseUsed) cc.drawBitmap(b, 0f, 0f, null) else cc.drawColor(PAPER)
        val n = if (model.drawing) model.strokeCount - 1 else model.strokeCount
        for (i in 0 until n) drawStroke(cc, i)
    }

    // ── 화면 ──

    override fun onDraw(c: Canvas) {
        frames++
        var paths = 0
        val bm = committed
        if (bm == null) {
            c.drawColor(PAPER)
        } else {
            c.drawBitmap(bm, 0f, 0f, null)
            val i = model.current
            if (i >= 0) {
                val p = paintFor(i)
                if (model.len(i) == 1) {
                    dotPaint.color = p.color
                    c.drawCircle(lastX, lastY, p.strokeWidth / 2f, dotPaint)
                } else {
                    c.drawPath(live, p)
                    paths++
                }
            }
        }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), border)
        pathsLastFrame = paths
        if (paths > maxPathsPerFrame) maxPathsPerFrame = paths
    }

    override fun onDetachedFromWindow() {
        endStroke()
        super.onDetachedFromWindow()
    }

    companion object {
        /** 종이색 (지우개도 이 색으로 칠함) */
        const val PAPER = 0xFFFFFDF5.toInt()
        /** 펜 4색: 검정·빨강·파랑·초록 */
        val PEN_COLORS = intArrayOf(0xFF1D1D2B.toInt(), 0xFFE53935.toInt(), 0xFF1E6FE0.toInt(), 0xFF2E9E44.toInt())
        val PEN_NAMES = arrayOf("검정", "빨강", "파랑", "초록")

        /** 지금까지 만든 Path 수 (성능 점검: 그리는 동안 늘면 안 됨) */
        var pathsCreated = 0
            private set

        private fun newPath(): Path {
            pathsCreated++
            return Path()
        }
    }
}
