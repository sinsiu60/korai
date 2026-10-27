package com.siwoo.party.games.umul.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.umul.GameConfig
import com.siwoo.party.games.umul.RoundState
import com.siwoo.party.games.umul.UmulBoard
import com.siwoo.party.games.umul.UmulRound
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 우물고누 나무판 (점 5개 + 새긴 선 7개 + 아래 변 자리의 우물).
 * 위쪽 한 줄에 "N판째 · 남은 수 M" 을 쓰고, 그 아래 정사각 나무판.
 * - 나무판(나뭇결·새긴 선·점 자리·돌 두른 연못)은 크기가 바뀔 때 비트맵으로 한 번 그린다 (release 에서 recycle)
 * - 돌 광택은 플레이어마다 RadialGradient 를 크기가 바뀔 때 만들고 translate 해서 재사용 (그리기 중 할당 없음)
 * - 내 차례엔 움직일 수 있는 말 둘레가 금빛, 그 말에서 빈 점까지 선을 따라 금빛 길, 빈 점(갈 곳)에 금색 고리.
 *   첫 수 금지에 걸린 말엔 빨간 ✕ 표시.
 * - 연출(새 판 말 놓기·선을 따라 미끄러지기·막힌 말 흔들기·갇힌 말 떨기)은 진행 중일 때만 다시 그린다. 시계 = listener.now() (게임 시계)
 */
class WellBoardView(context: Context) : View(context) {

    interface Listener {
        /** 지금 말을 눌러도 되는지 */
        fun canTap(): Boolean
        /** 점 point 를 눌렀음 (누른 점에서 손을 뗐을 때) */
        fun onPoint(point: Int)
        /** 게임 시계 (ms) */
        fun now(): Long
        /** 일시정지 중이면 연출을 다시 그리지 않음 */
        fun isPausedNow(): Boolean
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var round: UmulRound? = null
    private val colors = IntArray(GameConfig.PLAYERS)

    // ── 연출 시간 (화면이 t() 적용해서 넣음) ──
    var slideMs = GameConfig.SLIDE_MS
    var denyMs = GameConfig.DENY_MS
    var trapMs = GameConfig.TRAP_MS
    var setupMs = GameConfig.SETUP_MS

    // ── 배치 ──
    /** 점 사이 간격 (귀퉁이 ↔ 귀퉁이) */
    var spanPx = 1f
        private set
    private val ptX = FloatArray(UmulBoard.POINTS)
    private val ptY = FloatArray(UmulBoard.POINTS)
    private var stoneR = 1f
    private var headY = 0f
    private val plank = RectF()
    private var bg: Bitmap? = null
    private var bgLeft = 0f
    private var bgTop = 0f

    // ── 상태 ──
    private var slideFrom = -1
    private var slideTo = -1
    private var slideAt = 0L
    private var denyPoint = -1
    private var denyAt = 0L
    private var trapWho = -1
    private var trapAt = 0L
    private var setupAt = -1L
    private var pressPoint = -1
    private var headText = ""
    private var headWarn = false
    private val buf = IntArray(4)

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val stonePaint = Array(GameConfig.PLAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val headStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = Theme.BG_DEEP
    }
    private val oval = RectF()

    init {
        isClickable = true
    }

    fun bind(r: UmulRound, playerColors: IntArray) {
        round = r
        for (i in colors.indices) colors[i] = playerColors[i]
        slideFrom = -1
        slideTo = -1
        denyPoint = -1
        trapWho = -1
        pressPoint = -1
        if (width > 0) makeShaders()
        invalidate()
    }

    /** 위쪽 안내 줄 (값이 바뀔 때만 부름) */
    fun setHeader(text: String, warn: Boolean) {
        if (text == headText && warn == headWarn) return
        headText = text
        headWarn = warn
        invalidate()
    }

    val shownHeader: String get() = headText

    /** 점 i 의 중심 → 이 View 좌표 */
    fun pointCenter(i: Int, out: PointF) {
        out.set(ptX[i], ptY[i])
    }

    /** 새 판: 말이 하나씩 톡톡 놓임 */
    fun startSetup() {
        setupAt = listener?.now() ?: 0L
        invalidate()
    }

    /** from → to 로 선을 따라 미끄러지기 (판 상태는 이미 옮겨진 뒤) */
    fun startSlide(from: Int, to: Int) {
        slideFrom = from
        slideTo = to
        slideAt = listener?.now() ?: 0L
        invalidate()
    }

    fun endSlide() {
        slideFrom = -1
        slideTo = -1
        invalidate()
    }

    /** 못 움직이는 말 흔들기 */
    fun deny(point: Int) {
        denyPoint = point
        denyAt = listener?.now() ?: 0L
        invalidate()
    }

    /** 갇힌 사람의 말이 부들부들 떨고 빨간 고리 */
    fun startTrap(who: Int) {
        trapWho = who
        trapAt = listener?.now() ?: 0L
        invalidate()
    }

    /** 테스트용: 연출 중인지 */
    val animating: Boolean
        get() {
            val now = listener?.now() ?: return false
            return slideActive(now) || denyActive(now) || trapActive(now) || setupActive(now)
        }

    val sliding: Boolean get() = slideFrom >= 0

    private fun slideActive(now: Long) = slideFrom >= 0 && now - slideAt < slideMs
    private fun denyActive(now: Long) = denyPoint >= 0 && now - denyAt < denyMs
    private fun trapActive(now: Long) = trapWho >= 0 && now - trapAt < trapMs
    private fun setupActive(now: Long) = setupAt >= 0 && now - setupAt < setupMs

    // ── 크기·비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        layoutBoard(w, h)
    }

    private fun layoutBoard(w: Int, h: Int) {
        val headH = HEAD_DP * d
        val pad = 4f * d
        val k = 1f + 2f * MARGIN
        spanPx = minOf((w - 2f * pad) / k, (h - headH - 2f * pad) / k).coerceAtLeast(1f)
        val plankSize = spanPx * k
        val top = (h - headH - plankSize) / 2f
        headY = top + headH * 0.62f
        plank.set((w - plankSize) / 2f, top + headH, (w + plankSize) / 2f, top + headH + plankSize)
        val l = plank.left + spanPx * MARGIN
        val t = plank.top + spanPx * MARGIN
        for (i in 0 until UmulBoard.POINTS) {
            ptX[i] = UmulArt.px(l, spanPx, i)
            ptY[i] = UmulArt.py(t, spanPx, i)
        }
        stoneR = spanPx * STONE
        rim.strokeWidth = maxOf(1f, spanPx * 0.012f)
        headPaint.textSize = 16f * d
        headStroke.textSize = 16f * d
        headStroke.strokeWidth = 4f * d
        makeShaders()
        buildBg()
    }

    private fun makeShaders() {
        val r = stoneR
        for (i in colors.indices) {
            val c = colors[i]
            stonePaint[i].shader = RadialGradient(-r * 0.35f, -r * 0.42f, r * 1.55f,
                intArrayOf(UmulArt.shade(c, 1.7f), UmulArt.shade(c, 1.15f), c, UmulArt.shade(c, 0.5f)),
                floatArrayOf(0f, 0.2f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
    }

    /** 나무판: 그림자 + 나뭇결 판 + 테두리 + 새긴 선 + 점 자리 + 돌 두른 우물 */
    private fun buildBg() {
        release()
        val s = spanPx
        val sh = 8f * d
        val bw = ceil(plank.width() + sh).toInt().coerceAtLeast(1)
        val bh = ceil(plank.height() + sh).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val pw = plank.width()
        val ph = plank.height()
        val rr = s * 0.07f
        bgLeft = plank.left
        bgTop = plank.top
        // 그림자
        p.color = 0x77000000
        c.drawRoundRect(sh * 0.5f, sh, pw + sh * 0.5f, ph + sh, rr, rr, p)
        // 판 옆면(두께) + 윗면
        p.color = UmulArt.WOOD_EDGE
        c.drawRoundRect(0f, 0f, pw, ph, rr, rr, p)
        val e = s * 0.035f
        p.shader = LinearGradient(0f, 0f, pw, ph,
            intArrayOf(UmulArt.WOOD_LIGHT, UmulArt.WOOD, UmulArt.shade(UmulArt.WOOD, 0.86f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(e, e, pw - e, ph - e * 1.8f, rr, rr, p)
        p.shader = null
        // 나뭇결 (물결 줄)
        c.save()
        c.clipRect(e, e, pw - e, ph - e * 1.8f)
        val grain = Path()
        p.style = Paint.Style.STROKE
        val lines = 16
        for (k in 0 until lines) {
            val y0 = ph * (k + 0.5f) / lines
            grain.reset()
            var x = 0f
            grain.moveTo(x, y0)
            while (x < pw) {
                x += s * 0.04f
                val y = y0 + sin(x / pw * 6.3f + k * 1.7f) * s * 0.025f + sin(x / pw * 17f + k) * s * 0.006f
                grain.lineTo(x, y)
            }
            p.strokeWidth = s * (if (k % 3 == 0) 0.012f else 0.006f)
            p.color = UmulArt.WOOD_DARK
            p.alpha = if (k % 3 == 0) 70 else 45
            c.drawPath(grain, p)
        }
        // 옹이 하나
        p.alpha = 60
        for (k in 0 until 3) {
            p.strokeWidth = s * 0.008f
            oval.set(pw * 0.78f - s * (0.07f + k * 0.035f), ph * 0.2f - s * (0.025f + k * 0.017f),
                pw * 0.78f + s * (0.07f + k * 0.035f), ph * 0.2f + s * (0.025f + k * 0.017f))
            c.drawOval(oval, p)
        }
        p.alpha = 255
        c.restore()
        // 윗면 테두리 빛
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * d
        p.color = 0x66FFF2D0
        c.drawRoundRect(e + d, e + d, pw - e - d, ph - e * 1.8f - d, rr, rr, p)

        val ox = -plank.left
        val oy = -plank.top
        // 새긴 선: 밝은 아래 테 + 어두운 홈 (우물 쪽 BL–BR 은 없음)
        p.strokeCap = Paint.Cap.ROUND
        for (line in UmulBoard.LINES) {
            val x0 = ptX[line[0]] + ox
            val y0 = ptY[line[0]] + oy
            val x1 = ptX[line[1]] + ox
            val y1 = ptY[line[1]] + oy
            p.strokeWidth = s * 0.05f
            p.color = 0x88FFE6B8.toInt()
            c.drawLine(x0 + d, y0 + 1.5f * d, x1 + d, y1 + 1.5f * d, p)
            p.color = UmulArt.GROOVE
            c.drawLine(x0, y0, x1, y1, p)
            p.strokeWidth = s * 0.016f
            p.color = 0xFF2A1606.toInt()
            c.drawLine(x0, y0, x1, y1, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        // 우물: 돌 두른 연못 (아래 변 가운데)
        val wx = (ptX[UmulBoard.BL] + ptX[UmulBoard.BR]) / 2f + ox
        val wy = ptY[UmulBoard.BL] + oy + s * 0.02f
        val rx = s * 0.25f
        val ry = s * 0.17f
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        oval.set(wx - rx * 1.1f, wy - ry * 1.0f, wx + rx * 1.12f, wy + ry * 1.22f)
        c.drawOval(oval, p)
        UmulArt.well(c, p, wx, wy, rx, ry)
        // 물 그라데이션 덧칠 (깊어 보이게)
        p.shader = RadialGradient(wx - rx * 0.2f, wy - ry * 0.25f, rx * 0.8f,
            intArrayOf(0x4DFFFFFF, 0x00FFFFFF, 0x33000020),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        oval.set(wx - rx * 0.74f, wy - ry * 0.62f, wx + rx * 0.74f, wy + ry * 0.72f)
        c.drawOval(oval, p)
        p.shader = null
        // 테두리 돌멩이
        val n = 14
        for (k in 0 until n) {
            val a = (2.0 * PI * k / n).toFloat()
            val x = wx + cos(a) * rx * 0.9f
            val y = wy + sin(a) * ry * 0.88f
            val r = s * (if (k % 2 == 0) 0.032f else 0.026f)
            p.color = UmulArt.STONE_RIM_DARK
            c.drawCircle(x, y + r * 0.25f, r, p)
            p.color = if (k % 3 == 0) 0xFFA9A69E.toInt() else 0xFF9A978F.toInt()
            c.drawCircle(x, y, r * 0.9f, p)
            p.color = 0x55FFFFFF
            c.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.35f, p)
        }
        // 점 자리 (오목한 구멍)
        for (i in 0 until UmulBoard.POINTS) {
            val x = ptX[i] + ox
            val y = ptY[i] + oy
            val r = s * 0.06f
            p.color = 0x99FFE6B8.toInt()
            c.drawCircle(x, y + 1.5f * d, r, p)
            p.color = 0xFF2A1606.toInt()
            c.drawCircle(x, y, r, p)
            p.color = UmulArt.GROOVE
            c.drawCircle(x, y + r * 0.2f, r * 0.7f, p)
        }
        bg = bmp
    }

    fun release() {
        bg?.recycle()
        bg = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (bg == null && width > 0) buildBg()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ── 터치 ──

    /** (x, y) 에 가장 가까운 점 (판정 반경 밖이면 −1) */
    fun pointAt(x: Float, y: Float): Int {
        var best = -1
        var bestD = spanPx * HIT
        for (i in 0 until UmulBoard.POINTS) {
            val dx = x - ptX[i]
            val dy = y - ptY[i]
            val dd = sqrt(dx * dx + dy * dy)
            if (dd < bestD) {
                bestD = dd
                best = i
            }
        }
        return best
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (round == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (listener?.canTap() != true) return false
                val p = pointAt(e.x, e.y)
                if (p < 0) return false
                pressPoint = p
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> return pressPoint >= 0
            MotionEvent.ACTION_UP -> {
                val p = pressPoint
                if (p < 0) return false
                pressPoint = -1
                invalidate()
                if (pointAt(e.x, e.y) == p && listener?.canTap() == true) listener?.onPoint(p)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressPoint = -1
                invalidate()
                return true
            }
        }
        return pressPoint >= 0
    }

    /** 일시정지 등에서 누름 취소 */
    fun cancelPress() {
        if (pressPoint < 0) return
        pressPoint = -1
        invalidate()
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val r = round ?: return
        val now = listener?.now() ?: 0L
        val b = bg
        if (b != null && !b.isRecycled) c.drawBitmap(b, bgLeft, bgTop, bmpPaint)

        // 위쪽 안내 줄
        if (headText.isNotEmpty()) {
            headPaint.color = if (headWarn) Theme.RED else Theme.GOLD_LIGHT
            c.drawText(headText, plank.centerX(), headY, headStroke)
            c.drawText(headText, plank.centerX(), headY, headPaint)
        }

        val sliding = slideActive(now)
        val setup = setupActive(now)
        val hints = listener?.canTap() == true && !sliding && !setup && slideFrom < 0 && r.state == RoundState.PLAY
        val e = r.empty
        var nMov = 0
        if (hints) {
            nMov = r.movable(buf)
            // 갈 길 (말 → 빈 점, 선을 따라 금빛)
            stroke.strokeWidth = spanPx * 0.06f
            stroke.color = Theme.GOLD
            stroke.alpha = 150
            for (k in 0 until nMov) c.drawLine(ptX[buf[k]], ptY[buf[k]], ptX[e], ptY[e], stroke)
            stroke.alpha = 255
            // 갈 곳 (빈 점) 고리
            fill.color = 0x55FFC93C
            c.drawCircle(ptX[e], ptY[e], stoneR * 0.95f, fill)
            stroke.strokeWidth = 3f * d
            stroke.color = Theme.GOLD
            c.drawCircle(ptX[e], ptY[e], stoneR * 0.95f, stroke)
            fill.color = Theme.GOLD
            c.drawCircle(ptX[e], ptY[e], spanPx * 0.03f, fill)
            // 움직일 수 있는 말 둘레 빛
            for (k in 0 until nMov) {
                fill.color = 0x66FFE08A
                c.drawCircle(ptX[buf[k]], ptY[buf[k]], stoneR * 1.32f, fill)
            }
        }

        // 쌓인 말 (미끄러지는 말은 따로)
        val denying = denyActive(now)
        val trapping = trapActive(now)
        var stoneIndex = 0
        for (i in 0 until UmulBoard.POINTS) {
            val who = r.owner(i)
            if (who == UmulRound.EMPTY) continue
            val order = stoneIndex++
            if (slideFrom >= 0 && i == slideTo) continue
            var x = ptX[i]
            var y = ptY[i]
            var scale = 1f
            if (denying && i == denyPoint) {
                val f = (now - denyAt).toFloat() / denyMs
                x += sin((now - denyAt) * 0.075f) * stoneR * 0.3f * (1f - f)
            }
            if (trapping && who == trapWho) {
                val f = (now - trapAt).toFloat() / trapMs
                x += sin((now - trapAt) * 0.11f + i) * stoneR * 0.14f * (1f - f)
                y += cos((now - trapAt) * 0.13f + i) * stoneR * 0.07f * (1f - f)
            }
            if (setup) {
                val start = setupMs * order / 8f
                val f = ((now - setupAt - start) / (setupMs * 0.5f)).coerceIn(0f, 1f)
                if (f <= 0f) continue
                scale = backOut(f)
            }
            if (i == pressPoint) scale *= 1.07f
            drawStone(c, x, y, who, scale, 0f)
        }

        // 미끄러지는 말 (선을 따라, 가운데서 살짝 들림)
        if (slideFrom >= 0) {
            val who = r.owner(slideTo)
            if (who != UmulRound.EMPTY) {
                val f = if (sliding) ((now - slideAt).toFloat() / slideMs).coerceIn(0f, 1f) else 1f
                val g = f * f * (3f - 2f * f)
                val x = ptX[slideFrom] + (ptX[slideTo] - ptX[slideFrom]) * g
                val y = ptY[slideFrom] + (ptY[slideTo] - ptY[slideFrom]) * g
                val lift = sin(f * PI.toFloat())
                drawStone(c, x, y, who, 1f + 0.12f * lift, lift)
            }
        }

        // 첫 수 금지에 걸린 말: 빨간 ✕ 배지
        if (hints && r.isFirstMove) {
            for (q in UmulBoard.NEIGHBORS[e]) {
                if (r.owner(q) == r.current && r.isForbidden(q)) {
                    val bx = ptX[q] + stoneR * 0.72f
                    val by = ptY[q] - stoneR * 0.72f
                    val br = stoneR * 0.36f
                    fill.color = Theme.RED_DARK
                    c.drawCircle(bx, by + d, br, fill)
                    fill.color = Theme.RED
                    c.drawCircle(bx, by, br, fill)
                    stroke.color = 0xFFFFFFFF.toInt()
                    stroke.strokeWidth = br * 0.28f
                    c.drawLine(bx - br * 0.42f, by - br * 0.42f, bx + br * 0.42f, by + br * 0.42f, stroke)
                    c.drawLine(bx + br * 0.42f, by - br * 0.42f, bx - br * 0.42f, by + br * 0.42f, stroke)
                }
            }
        }

        // 갇힌 말: 빨간 고리 (떠는 동안은 퍼지는 고리도)
        if (trapWho >= 0) {
            for (i in 0 until UmulBoard.POINTS) {
                if (r.owner(i) != trapWho) continue
                stroke.color = Theme.RED
                stroke.strokeWidth = 3.5f * d
                c.drawCircle(ptX[i], ptY[i], stoneR * 1.18f, stroke)
                if (trapping) {
                    val f = (now - trapAt).toFloat() / trapMs
                    stroke.alpha = (220 * (1f - f)).toInt()
                    c.drawCircle(ptX[i], ptY[i], stoneR * (1.2f + 0.8f * f), stroke)
                    stroke.alpha = 255
                }
            }
            // 빈 점(막힌 길)에도 빨간 ✕
            val ex = ptX[e]
            val ey = ptY[e]
            val xr = stoneR * 0.42f
            stroke.color = Theme.RED
            stroke.strokeWidth = 4f * d
            c.drawLine(ex - xr, ey - xr, ex + xr, ey + xr, stroke)
            c.drawLine(ex + xr, ey - xr, ex - xr, ey + xr, stroke)
        }

        if ((sliding || denying || trapping || setup) && listener?.isPausedNow() != true) postInvalidateOnAnimation()
    }

    /** 광택 돌 (lift 0..1 = 들린 정도 → 그림자가 멀어짐) */
    private fun drawStone(c: Canvas, x: Float, y: Float, who: Int, scale: Float, lift: Float) {
        val r = stoneR * scale
        fill.color = 0x66000000
        val off = r * (0.18f + 0.3f * lift)
        oval.set(x - r * 0.98f + off * 0.4f, y - r * 0.8f + off, x + r * 0.98f + off * 0.4f, y + r * 0.95f + off)
        c.drawOval(oval, fill)
        c.save()
        c.translate(x, y)
        c.scale(scale, scale)
        c.drawCircle(0f, 0f, stoneR, stonePaint[who])
        rim.color = UmulArt.shade(colors[who], 0.4f)
        c.drawCircle(0f, 0f, stoneR - rim.strokeWidth / 2f, rim)
        c.restore()
    }

    private fun backOut(f: Float): Float {
        val s = 1.70158f
        val t = f - 1f
        return 1f + t * t * ((s + 1f) * t + s)
    }

    companion object {
        /** 위쪽 안내 줄 높이 (dp) */
        private const val HEAD_DP = 28f
        /** 점 바깥 나무판 여백 (점 간격 비율) */
        private const val MARGIN = 0.27f
        /** 돌 반지름 (점 간격 비율) */
        private const val STONE = 0.13f
        /** 터치 판정 반경 (점 간격 비율) */
        private const val HIT = 0.3f
    }
}
