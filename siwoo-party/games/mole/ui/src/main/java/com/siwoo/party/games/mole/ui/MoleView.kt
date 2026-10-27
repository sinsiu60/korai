package com.siwoo.party.games.mole.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.mole.GameConfig
import kotlin.math.sin

/**
 * 두더지 판 (MultiTouchZoneView 위에 겹치는 그림 층, 터치는 아래 구역이 받는다).
 * 구역마다 같은 3 × 2 구멍 배치를 그 사람 방향으로 돌려 그린다 (위쪽 구역 180°).
 * 두더지는 모든 구역 공통(setHole), 잡힘·점수 글자·흔들림은 구역별.
 * 시간은 경기 시작 기준 ms (setNow) — 일시정지하면 화면도 그대로 멈춘다.
 */
class MoleView(context: Context) : View(context) {

    private val dp = Theme.dpf(context, 1f)
    private var n = 0
    private var zones: List<Zone> = emptyList()
    private val names = ArrayList<String>()
    private var colors = IntArray(0)
    private var scores = arrayOfNulls<String>(0)
    private var timeText = ""
    private var hurry = false
    private var now = 0L

    // ── 구멍 (모든 구역 공통) ──
    private val holeId = IntArray(GameConfig.HOLES) { -1 }
    private val holeKind = IntArray(GameConfig.HOLES)
    private val holeStart = LongArray(GameConfig.HOLES)
    private val holeEnd = LongArray(GameConfig.HOLES)

    // ── 구역별 ──
    private var hitAt = Array(0) { LongArray(0) }
    private var hitId = Array(0) { IntArray(0) }
    private var popHole = Array(0) { IntArray(0) }
    private var popKind = Array(0) { IntArray(0) }
    private var popAt = Array(0) { LongArray(0) }
    private var popNext = IntArray(0)
    private var shakeAt = LongArray(0)

    /** 연출 시간 (속도 배율 적용된 값) */
    var riseMs = 110L
    var sinkMs = 280L
    var popMs = 650L
    var shakeMs = 420L

    // ── 배치 (구역 안 좌표, 모든 구역 같은 크기) ──
    private var zw = 0f
    private var zh = 0f
    private var u = dp
    private var pad = 0f
    private var headerY = 0f
    private var gridLeft = 0f
    private var gridTop = 0f
    private var cellW = 0f
    private var cellH = 0f
    private var holeRx = 0f
    private var holeRy = 0f
    private var moleR = 0f
    private val cellX = FloatArray(GameConfig.HOLES)
    private val holeY = FloatArray(GameConfig.HOLES)

    // ── 그리기 도구 ──
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT; color = Theme.GOLD }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
    private val popPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val popEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF1A1030.toInt()
    }
    private val rect = RectF()
    private val path = Path()

    init {
        isClickable = false
        isFocusable = false
    }

    fun setup(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size
        names.clear()
        names.addAll(playerNames)
        colors = playerColors.copyOf()
        scores = arrayOfNulls(n)
        for (i in 0 until n) scores[i] = "0점"
        hitAt = Array(n) { LongArray(GameConfig.HOLES) { -1L } }
        hitId = Array(n) { IntArray(GameConfig.HOLES) { -1 } }
        popHole = Array(n) { IntArray(POPS) }
        popKind = Array(n) { IntArray(POPS) }
        popAt = Array(n) { LongArray(POPS) { Long.MIN_VALUE / 2 } }
        popNext = IntArray(n)
        shakeAt = LongArray(n) { Long.MIN_VALUE / 2 }
        relayout()
    }

    fun setScore(i: Int, text: String) { scores[i] = text; invalidate() }

    fun setTime(text: String, hurry: Boolean) {
        timeText = text
        this.hurry = hurry
        invalidate()
    }

    /** 경기 시작 기준 지금 시각 → 다시 그림 */
    fun setNow(t: Long) {
        now = t
        invalidate()
    }

    /** 구멍에 새 두더지 (같은 id 면 무시) */
    fun setHole(hole: Int, id: Int, kind: Int, start: Long, end: Long) {
        if (holeId[hole] == id) return
        holeId[hole] = id
        holeKind[hole] = kind
        holeStart[hole] = start
        holeEnd[hole] = end
        invalidate()
    }

    /** zone 에서 hole 의 두더지를 잡음: 어지러워하며 들어감 + 점수 글자, 폭탄이면 흔들림 */
    fun hit(zone: Int, hole: Int, id: Int, kind: Int, at: Long) {
        hitAt[zone][hole] = at
        hitId[zone][hole] = id
        val k = popNext[zone]
        popNext[zone] = (k + 1) % POPS
        popHole[zone][k] = hole
        popKind[zone][k] = kind
        popAt[zone][k] = at
        if (kind == KIND_BOMB) shakeAt[zone] = at
        invalidate()
    }

    /** 연출이 남았는지 (끝난 뒤 프레임을 더 돌릴지) */
    fun busy(t: Long): Boolean {
        for (z in 0 until n) {
            if (t - shakeAt[z] < shakeMs) return true
            for (k in 0 until POPS) if (t - popAt[z][k] < popMs) return true
            for (h in 0 until GameConfig.HOLES) if (hitAt[z][h] >= 0 && t - hitAt[z][h] < sinkMs) return true
        }
        for (h in 0 until GameConfig.HOLES) if (holeId[h] >= 0 && t < holeEnd[h]) return true
        return false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || n == 0) return
        zones = TouchZones.layout(n, width.toFloat(), height.toFloat(), Theme.dpf(context, 10f))
        val z = zones[0]
        zw = z.width
        zh = z.height
        val k = minOf(zw / (300f * dp), zh / (165f * dp)).coerceIn(0.72f, 1.4f)
        u = dp * k
        pad = 14f * u
        val header = 24f * u
        namePaint.textSize = 16f * u
        scorePaint.textSize = 17f * u
        timePaint.textSize = 15f * u
        popPaint.textSize = 20f * u
        popEdge.textSize = 20f * u
        popEdge.strokeWidth = 4f * u
        headerY = 10f * u + header * 0.72f
        val top = 10f * u + header + 2f * u
        val bottom = zh - 10f * u
        val gridW = zw - 2 * pad
        cellW = gridW / GameConfig.HOLE_COLS
        cellH = minOf((bottom - top) / GameConfig.HOLE_ROWS, cellW * 1.1f)
        gridLeft = pad
        gridTop = top + ((bottom - top) - cellH * GameConfig.HOLE_ROWS) / 2f
        holeRx = minOf(cellW * 0.4f, cellH * 0.6f)
        holeRy = holeRx * 0.3f
        moleR = minOf(holeRx * 0.74f, cellH * 0.3f)
        for (h in 0 until GameConfig.HOLES) {
            val c = h % GameConfig.HOLE_COLS
            val r = h / GameConfig.HOLE_COLS
            cellX[h] = gridLeft + cellW * (c + 0.5f)
            holeY[h] = gridTop + cellH * r + cellH * 0.78f
        }
        invalidate()
    }

    /** 구역 zone 안 (x, y) (이 View 좌표) 가 가리키는 구멍 (없으면 -1). 칸 전체가 판정 영역 */
    fun holeAt(zone: Int, x: Float, y: Float): Int {
        val z = zones.getOrNull(zone) ?: return -1
        val lx = if (z.rotation == 180f) z.right - x else x - z.left
        val ly = if (z.rotation == 180f) z.bottom - y else y - z.top
        if (lx < gridLeft || ly < gridTop) return -1
        val c = ((lx - gridLeft) / cellW).toInt()
        val r = ((ly - gridTop) / cellH).toInt()
        if (c !in 0 until GameConfig.HOLE_COLS || r !in 0 until GameConfig.HOLE_ROWS) return -1
        return r * GameConfig.HOLE_COLS + c
    }

    /** 구역 zone 의 hole 두더지 몸 가운데 (이 View 좌표) → out */
    fun moleCenter(zone: Int, hole: Int, out: PointF) {
        val z = zones.getOrNull(zone) ?: return
        val lx = cellX[hole]
        val ly = holeY[hole] - moleR * 1.1f
        if (z.rotation == 180f) out.set(z.right - lx, z.bottom - ly) else out.set(z.left + lx, z.top + ly)
    }

    override fun onDraw(c: Canvas) {
        for (zi in zones.indices) {
            val z = zones[zi]
            val i = z.index
            if (i >= n) continue
            c.save()
            c.rotate(z.rotation, z.centerX, z.centerY)
            c.translate(z.left, z.top)
            // 폭탄 흔들림
            val st = now - shakeAt[i]
            if (st in 0 until shakeMs) {
                val f = 1f - st.toFloat() / shakeMs
                c.translate(sin(st * 0.11f) * 7f * u * f, sin(st * 0.157f + 1f) * 4f * u * f)
            }
            drawZone(c, i, st)
            c.restore()
        }
    }

    private fun drawZone(c: Canvas, i: Int, shakeT: Long) {
        // 머리: 이름 + 점수, 시간
        namePaint.color = colors[i]
        c.drawText(names[i], pad, headerY, namePaint)
        val s = scores[i]
        if (s != null) c.drawText(s, pad + namePaint.measureText(names[i]) + 10f * u, headerY, scorePaint)
        timePaint.color = if (hurry) Theme.RED else Theme.TEXT
        c.drawText(timeText, zw - pad, headerY, timePaint)
        val tw = timePaint.measureText(timeText)
        val cr = 6.5f * u
        val ccx = zw - pad - tw - 10f * u
        val ccy = headerY - timePaint.textSize * 0.36f
        line.color = timePaint.color
        line.strokeWidth = 1.8f * u
        c.drawCircle(ccx, ccy, cr, line)
        c.drawLine(ccx, ccy, ccx, ccy - cr * 0.6f, line)
        c.drawLine(ccx, ccy, ccx + cr * 0.45f, ccy, line)

        for (h in 0 until GameConfig.HOLES) drawHole(c, i, h)
        drawPops(c, i)

        // 폭탄 맞으면 빨간 번쩍
        if (shakeT in 0 until shakeMs) {
            p.style = Paint.Style.FILL
            p.color = Theme.RED
            p.alpha = ((1f - shakeT.toFloat() / shakeMs) * 110).toInt()
            rect.set(4f * dp, 4f * dp, zw - 4f * dp, zh - 4f * dp)
            c.drawRoundRect(rect, 18f * dp, 18f * dp, p)
            p.alpha = 255
        }
    }

    private fun drawHole(c: Canvas, i: Int, h: Int) {
        val cx = cellX[h]
        val hy = holeY[h]
        // 흙더미 + 구멍
        p.style = Paint.Style.FILL
        p.color = DIRT_DARK
        rect.set(cx - holeRx * 1.12f, hy - holeRy * 1.25f, cx + holeRx * 1.12f, hy + holeRy * 1.45f)
        c.drawOval(rect, p)
        p.color = HOLE
        rect.set(cx - holeRx, hy - holeRy, cx + holeRx, hy + holeRy)
        c.drawOval(rect, p)

        // 두더지
        val id = holeId[h]
        if (id >= 0) {
            val hit = hitId[i][h] == id && hitAt[i][h] >= 0
            var rise = 0f
            if (hit) {
                val f = (now - hitAt[i][h]).toFloat() / sinkMs
                if (f < 1f) rise = 1f - f * f
            } else if (now >= holeStart[h] && now < holeEnd[h]) {
                val a = ((now - holeStart[h]).toFloat() / riseMs).coerceIn(0f, 1f)
                val b = ((holeEnd[h] - now).toFloat() / riseMs).coerceIn(0f, 1f)
                rise = minOf(a, b)
            }
            if (rise > 0f) {
                c.save()
                c.clipRect(cx - cellW * 0.5f, hy - cellH, cx + cellW * 0.5f, hy)
                val top = hy - moleR * 2.15f * rise
                when (holeKind[h]) {
                    KIND_BOMB -> drawBomb(c, cx, top, hit)
                    else -> drawMole(c, cx, top, holeKind[h] == KIND_GOLD, hit)
                }
                c.restore()
            }
        }
        // 구멍 앞 테두리 (두더지 아래쪽을 가림)
        line.color = DIRT
        line.strokeWidth = holeRy * 0.55f
        rect.set(cx - holeRx, hy - holeRy, cx + holeRx, hy + holeRy)
        c.drawArc(rect, 0f, 180f, false, line)
    }

    /** 두더지: top = 머리 꼭대기 */
    private fun drawMole(c: Canvas, cx: Float, top: Float, gold: Boolean, dazed: Boolean) {
        val r = moleR
        p.style = Paint.Style.FILL
        // 몸
        p.color = if (gold) Theme.GOLD else MOLE
        rect.set(cx - r, top, cx + r, top + r * 3f)
        c.drawRoundRect(rect, r, r, p)
        // 배
        p.color = if (gold) Theme.GOLD_LIGHT else MOLE_BELLY
        rect.set(cx - r * 0.62f, top + r * 1.05f, cx + r * 0.62f, top + r * 3.2f)
        c.drawOval(rect, p)
        if (gold) {
            line.color = Theme.GOLD_DARK
            line.strokeWidth = 1.6f * u
            rect.set(cx - r, top, cx + r, top + r * 3f)
            c.drawRoundRect(rect, r, r, line)
        }
        // 눈
        val ey = top + r * 0.62f
        val ex = r * 0.38f
        if (dazed) {
            line.color = Theme.INK
            line.strokeWidth = 2f * u
            val d = r * 0.14f
            c.drawLine(cx - ex - d, ey - d, cx - ex + d, ey + d, line)
            c.drawLine(cx - ex + d, ey - d, cx - ex - d, ey + d, line)
            c.drawLine(cx + ex - d, ey - d, cx + ex + d, ey + d, line)
            c.drawLine(cx + ex + d, ey - d, cx + ex - d, ey + d, line)
        } else {
            p.color = Theme.INK
            c.drawCircle(cx - ex, ey, r * 0.13f, p)
            c.drawCircle(cx + ex, ey, r * 0.13f, p)
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(cx - ex + r * 0.04f, ey - r * 0.05f, r * 0.045f, p)
            c.drawCircle(cx + ex + r * 0.04f, ey - r * 0.05f, r * 0.045f, p)
        }
        // 볼
        p.color = 0x55FF7A8A
        c.drawCircle(cx - r * 0.62f, ey + r * 0.32f, r * 0.15f, p)
        c.drawCircle(cx + r * 0.62f, ey + r * 0.32f, r * 0.15f, p)
        // 코 + 이빨
        p.color = NOSE
        rect.set(cx - r * 0.2f, ey + r * 0.12f, cx + r * 0.2f, ey + r * 0.4f)
        c.drawOval(rect, p)
        p.color = 0xFFFFFFFF.toInt()
        rect.set(cx - r * 0.13f, ey + r * 0.44f, cx + r * 0.13f, ey + r * 0.66f)
        c.drawRoundRect(rect, r * 0.04f, r * 0.04f, p)
        line.color = 0x66000000
        line.strokeWidth = 1f * u
        c.drawLine(cx, ey + r * 0.44f, cx, ey + r * 0.66f, line)
        // 황금 반짝
        if (gold && !dazed) {
            val tw = 0.6f + 0.4f * sin(now * 0.012f)
            drawSparkle(c, cx + r * 0.95f, top + r * 0.15f, r * 0.32f * tw)
            drawSparkle(c, cx - r * 1.0f, top + r * 0.9f, r * 0.22f * (1.6f - tw))
        }
    }

    private fun drawSparkle(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y - s)
        path.lineTo(x + s * 0.25f, y - s * 0.25f)
        path.lineTo(x + s, y)
        path.lineTo(x + s * 0.25f, y + s * 0.25f)
        path.lineTo(x, y + s)
        path.lineTo(x - s * 0.25f, y + s * 0.25f)
        path.lineTo(x - s, y)
        path.lineTo(x - s * 0.25f, y - s * 0.25f)
        path.close()
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        c.drawPath(path, p)
    }

    /** 폭탄: 검은 공 + 심지 불꽃 + 해골 눈 */
    private fun drawBomb(c: Canvas, cx: Float, top: Float, hit: Boolean) {
        val r = moleR * 1.05f
        val cy = top + r * 1.25f
        // 심지
        line.color = FUSE
        line.strokeWidth = 2.4f * u
        path.reset()
        path.moveTo(cx + r * 0.35f, cy - r * 0.85f)
        path.quadTo(cx + r * 0.55f, cy - r * 1.35f, cx + r * 0.95f, cy - r * 1.15f)
        c.drawPath(path, line)
        // 불꽃
        if (!hit) {
            val f = 0.75f + 0.25f * sin(now * 0.05f)
            p.style = Paint.Style.FILL
            p.color = 0xFFFF9F1C.toInt()
            c.drawCircle(cx + r * 0.98f, cy - r * 1.17f, r * 0.24f * f, p)
            p.color = 0xFFFFE08A.toInt()
            c.drawCircle(cx + r * 0.98f, cy - r * 1.17f, r * 0.12f * f, p)
        }
        p.style = Paint.Style.FILL
        p.color = BOMB
        c.drawCircle(cx, cy, r, p)
        p.color = 0xFF4A4F66.toInt()
        rect.set(cx - r * 0.3f, cy - r * 1.05f, cx + r * 0.45f, cy - r * 0.72f)
        c.drawRoundRect(rect, r * 0.08f, r * 0.08f, p)
        // 반사광
        p.color = 0x55FFFFFF
        c.drawCircle(cx - r * 0.4f, cy - r * 0.4f, r * 0.22f, p)
        // 빨간 화난 눈
        p.color = Theme.RED
        c.drawCircle(cx - r * 0.32f, cy + r * 0.05f, r * 0.15f, p)
        c.drawCircle(cx + r * 0.32f, cy + r * 0.05f, r * 0.15f, p)
        line.color = Theme.RED
        line.strokeWidth = 2f * u
        c.drawLine(cx - r * 0.55f, cy - r * 0.28f, cx - r * 0.15f, cy - r * 0.12f, line)
        c.drawLine(cx + r * 0.55f, cy - r * 0.28f, cx + r * 0.15f, cy - r * 0.12f, line)
    }

    /** 점수 글자 (+1 / +3 / -2) 가 위로 떠오르며 사라짐 */
    private fun drawPops(c: Canvas, i: Int) {
        for (k in 0 until POPS) {
            val t = now - popAt[i][k]
            if (t < 0 || t >= popMs) continue
            val f = t.toFloat() / popMs
            val h = popHole[i][k]
            val kind = popKind[i][k]
            val x = cellX[h]
            // 위쪽 줄은 머리(이름·점수)를 가리지 않게
            val y = maxOf(holeY[h] - moleR * 2.3f - f * 18f * u, gridTop + 12f * u)
            val a = ((1f - f) * 255).toInt().coerceIn(0, 255)
            val scale = if (f < 0.15f) 0.7f + 2f * f else 1f
            popPaint.textSize = 20f * u * scale * (if (kind == KIND_GOLD) 1.25f else 1f)
            popEdge.textSize = popPaint.textSize
            popPaint.color = when (kind) { KIND_GOLD -> Theme.GOLD; KIND_BOMB -> Theme.RED; else -> 0xFFFFFFFF.toInt() }
            popPaint.alpha = a
            popEdge.alpha = a
            val text = when (kind) { KIND_GOLD -> PLUS3; KIND_BOMB -> MINUS2; else -> PLUS1 }
            if (kind == KIND_BOMB) drawBoom(c, x, holeY[h] - moleR * 1.2f, t)
            c.drawText(text, x, y, popEdge)
            c.drawText(text, x, y, popPaint)
        }
    }

    /** 폭탄 터짐: 주황 원이 커지며 사라짐 */
    private fun drawBoom(c: Canvas, x: Float, y: Float, t: Long) {
        val f = (t.toFloat() / (popMs * 0.5f)).coerceAtMost(1f)
        if (f >= 1f) return
        p.style = Paint.Style.FILL
        p.color = 0xFFFF9F1C.toInt()
        p.alpha = ((1f - f) * 220).toInt()
        c.drawCircle(x, y, moleR * (0.8f + 1.3f * f), p)
        p.color = 0xFFFFE08A.toInt()
        p.alpha = ((1f - f) * 230).toInt()
        c.drawCircle(x, y, moleR * (0.4f + 0.8f * f), p)
        p.alpha = 255
    }

    companion object {
        const val KIND_NORMAL = 0
        const val KIND_GOLD = 1
        const val KIND_BOMB = 2
        private const val POPS = 4
        private const val PLUS1 = "+1"
        private const val PLUS3 = "+3"
        private const val MINUS2 = "-2"
        private const val MOLE = 0xFF8B5A3C.toInt()
        private const val MOLE_BELLY = 0xFFD6A77F.toInt()
        private const val NOSE = 0xFFFF8FA3.toInt()
        private const val DIRT = 0xFF6B4A2E.toInt()
        private const val DIRT_DARK = 0xFF3E2A1A.toInt()
        private const val HOLE = 0xFF120A06.toInt()
        private const val BOMB = 0xFF23263A.toInt()
        private const val FUSE = 0xFFC9A26B.toInt()
    }
}
