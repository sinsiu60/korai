package com.siwoo.party.games.traceshape.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import com.siwoo.party.common.swipe.ZoneTouchInput
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.traceshape.GameConfig
import com.siwoo.party.games.traceshape.RoundResult
import com.siwoo.party.games.traceshape.Shape
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 도형 따라 그리기 판 (MultiTouchZoneView 위에 겹치는 그림 층).
 * 터치는 이 View 가 받아 ZoneTouchInput 으로 구역 안 좌표(위쪽 구역 180° 풀어 줌)로 바꾸고, forwardTo(구역 View)에도 넘겨 눌림 표시를 유지한다.
 * 구역마다 같은 배치를 그 사람 방향으로 그린다:
 *   위: 이름 · "2/5 · 별" · 합계 점수, 시간 막대
 *   가운데: 도형 상자 (점선 도형 + 금색 시작점 + 진행 방향 화살촉 + 열린 도형은 끝점 고리) 위에 내 그림
 *   옆(넓은 구역) 또는 아래(2인 세로 구역): 안내 칸 — 그리는 중(진행 %·남은 시간·완료!) / 공개(정확도·속도·점수) / 비교(이번 라운드 순서) / 최종
 * 비교 단계에서는 상자에 모두의 그림을 각자 색으로 겹쳐 그린다 (내 그림이 맨 위).
 * 도형·그림 Path 는 상자 좌표(0..1)로 만들고 그릴 때 캔버스를 상자 크기로 늘린다. onDraw 는 할당 없음.
 */
class TraceShapeView(context: Context) : View(context) {

    enum class Mode { NONE, READY, DRAWING, REVEAL, COMPARE, FINAL }

    private val dp = Theme.dpf(context, 1f)
    val input = ZoneTouchInput(MAX_ZONES)
    /** 같은 터치를 넘길 View (구역 눌림 표시) */
    var forwardTo: View? = null
    private val inset = 8f * dp
    private var zones: List<Zone> = emptyList()
    private var n = 0

    var mode = Mode.NONE
        private set

    // ── 구역별 배치 (구역 안 좌표) ──
    private val zw = FloatArray(MAX_ZONES)
    private val zh = FloatArray(MAX_ZONES)
    private val headH = FloatArray(MAX_ZONES)
    private val boxL = FloatArray(MAX_ZONES)
    private val boxT = FloatArray(MAX_ZONES)
    private val boxS = FloatArray(MAX_ZONES)
    private val panL = FloatArray(MAX_ZONES)
    private val panT = FloatArray(MAX_ZONES)
    private val panW = FloatArray(MAX_ZONES)
    private val panH = FloatArray(MAX_ZONES)
    private val legendSize = FloatArray(MAX_ZONES)

    // ── 플레이어 ──
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private var totals: Array<String> = emptyArray()

    // ── 라운드 ──
    private var roundLabel = ""
    private var shape: Shape? = null
    private val target = Path()
    private val arrowPath = Path()
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var openShape = false
    private val strokes = Array(MAX_ZONES) { Path() }
    private val hasStroke = BooleanArray(MAX_ZONES)
    private val tmp = FloatArray(4)

    // ── 그리는 중 ──
    private val done = BooleanArray(MAX_ZONES)
    private val doneText = Array(MAX_ZONES) { "" }
    private val doneBonus = Array(MAX_ZONES) { "" }
    private val progress = IntArray(MAX_ZONES) { -1 }
    private val progressText = Array(MAX_ZONES) { "" }
    private var timeFrac = 1f
    private var hurry = false
    private var timeText = ""
    private var shownSec = -1

    // ── 공개·비교·최종 ──
    private val accText = Array(MAX_ZONES) { "" }
    private val bonusText = Array(MAX_ZONES) { "" }
    private val bonusColor = IntArray(MAX_ZONES)
    private val ptsText = Array(MAX_ZONES) { "" }
    private val order = IntArray(MAX_ZONES)
    private val best = BooleanArray(MAX_ZONES)
    private val rankText = Array(MAX_ZONES) { "" }
    private val finalText = Array(MAX_ZONES) { "" }
    private val rankFirst = BooleanArray(MAX_ZONES)

    // ── 연출 ──
    private val popT = FloatArray(MAX_ZONES) { 1f }
    var popMs = GameConfig.POP_MS.toFloat()

    // ── 그리기 도구 ──
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = 0xCCFFFFFF.toInt()
        // 상자 좌표(0..1) 기준 점선 간격
        pathEffect = DashPathEffect(floatArrayOf(0.03f, 0.024f), 0f)
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Theme.GOLD }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF0A0F2E.toInt()
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val rect = RectF()

    init {
        isClickable = false
        isFocusable = false
        namePaint.textSize = 15f * dp
        roundPaint.textSize = 13f * dp
        totalPaint.textSize = 15f * dp
    }

    fun setup(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size
        names = playerNames.toTypedArray()
        colors = playerColors
        totals = Array(n) { "0점" }
        relayout()
    }

    fun setTotal(i: Int, score: Int) { totals[i] = "${score}점"; invalidate() }

    // ── 배치 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = relayout()

    fun relayout() {
        if (width == 0 || n == 0) return
        zones = TouchZones.layout(n, width.toFloat(), height.toFloat(), 10f * dp)
        input.setZones(zones, inset)
        val pad = 8f * dp
        for (i in 0 until n) {
            val f = input.frame(i)
            val w = f.width
            val h = f.height
            zw[i] = w
            zh[i] = h
            if (w >= h * 1.3f) {
                // 넓은 구역 (3·4인): 상자 왼쪽, 안내 칸 오른쪽
                headH[i] = 27f * dp
                val avail = h - headH[i] - 4f * dp - 4f * dp
                val s = minOf(avail, w * 0.56f)
                val pw = minOf(w - s - 3f * pad, 150f * dp)
                val group = s + pad + pw
                boxS[i] = s
                boxL[i] = (w - group) / 2f
                boxT[i] = headH[i] + 4f * dp + (avail - s) / 2f
                panL[i] = boxL[i] + s + pad
                panT[i] = boxT[i]
                panW[i] = pw
                panH[i] = s
            } else {
                // 세로 구역 (2인): 상자 위, 안내 칸 아래
                headH[i] = 30f * dp
                val ph = 84f * dp
                val avail = h - headH[i] - ph - 2f * pad
                val s = minOf(avail, w - 2f * pad)
                boxS[i] = s
                boxL[i] = (w - s) / 2f
                boxT[i] = headH[i] + pad / 2f + (avail - s) / 2f
                panL[i] = pad
                panW[i] = w - 2f * pad
                panT[i] = boxT[i] + s + pad
                panH[i] = h - panT[i] - pad / 2f
            }
        }
        fitLegend()
        invalidate()
    }

    /** 구역 안 좌표 → 상자 좌표 (out[0], out[1]) */
    fun toBox(zone: Int, lx: Float, ly: Float, out: FloatArray) {
        out[0] = (lx - boxL[zone]) / boxS[zone]
        out[1] = (ly - boxT[zone]) / boxS[zone]
    }

    /** 상자 좌표 → 화면 좌표 (테스트·연출용) */
    fun boxToScreen(zone: Int, bx: Float, by: Float, out: FloatArray) {
        val f = input.frame(zone)
        val lx = boxL[zone] + bx * boxS[zone]
        val ly = boxT[zone] + by * boxS[zone]
        out[0] = f.toScreenX(lx, ly)
        out[1] = f.toScreenY(lx, ly)
    }

    // ── 라운드 ──

    /** 새 라운드: 도형을 보여 주고(아직 못 그림) 그림·표시를 비운다 */
    fun setRound(round: Int, total: Int, s: Shape) {
        shape = s
        roundLabel = "$round/$total · ${s.kind.label}"
        ShapeArt.build(s, target, 0f, 0f, 1f, tmp = tmp)
        ShapeArt.arrow(s, arrowPath, ARROW_U, 0.045f, 0f, 0f, 1f, tmp)
        startX = s.startX; startY = s.startY
        endX = s.endX; endY = s.endY
        openShape = !s.closed
        for (i in 0 until MAX_ZONES) {
            strokes[i].reset()
            hasStroke[i] = false
            done[i] = false
            progress[i] = -1
            progressText[i] = "따라간 길 0%"
            popT[i] = 1f
            best[i] = false
        }
        timeFrac = 1f
        hurry = false
        shownSec = -1
        setTime(1f, GameConfig.ROUND_MS, false)
        mode = Mode.READY
        invalidate()
    }

    fun startDrawing() { mode = Mode.DRAWING; invalidate() }

    /** 새 조각 시작 (상자 좌표) */
    fun penDown(i: Int, x: Float, y: Float) {
        strokes[i].moveTo(x, y)
        // 점 하나만 찍어도 보이게 아주 짧은 선
        strokes[i].lineTo(x + 0.0005f, y)
        hasStroke[i] = true
        invalidate()
    }

    fun penMove(i: Int, x: Float, y: Float) {
        if (!hasStroke[i]) { penDown(i, x, y); return }
        strokes[i].lineTo(x, y)
        invalidate()
    }

    /** 진행(덮은 비율 %) — 값이 바뀔 때만 글자를 만든다 */
    fun setProgress(i: Int, pct: Int) {
        if (progress[i] == pct) return
        progress[i] = pct
        progressText[i] = "따라간 길 $pct%"
        invalidate()
    }

    /** 자동 완료 표시 (걸린 시간·예상 속도 배수) */
    fun setDone(i: Int, timeMs: Long, bonus: Float) {
        done[i] = true
        val t = (timeMs + 50) / 100
        doneText[i] = "${t / 10}.${t % 10}초"
        doneBonus[i] = "속도 ×${fmt2(bonus)}"
        popT[i] = 0f
        invalidate()
    }

    fun isDoneShown(i: Int): Boolean = done[i]

    /** 남은 시간 (막대 비율, 남은 ms) — 초가 바뀔 때만 글자를 만든다 */
    fun setTime(frac: Float, leftMs: Long, isHurry: Boolean) {
        timeFrac = frac.coerceIn(0f, 1f)
        hurry = isHurry
        val sec = ((leftMs + 999) / 1000).toInt()
        if (sec != shownSec) {
            shownSec = sec
            timeText = "남은 ${sec}초"
        }
        invalidate()
    }

    /** 라운드 공개: 각자 정확도·속도 배수·점수 */
    fun reveal(r: RoundResult) {
        for (i in 0 until n) {
            val p = r.players[i]
            accText[i] = "정확도 ${p.accuracy.roundToInt()}%"
            bonusText[i] = if (p.finished) "속도 ×${fmt2(p.bonus)}" else "미완성 ×1.0"
            bonusColor[i] = if (p.finished) Theme.GREEN else Theme.GRAY
            ptsText[i] = "+${p.points}점"
            popT[i] = 0f
        }
        val b = r.best()
        for (i in 0 until n) best[i] = i in b && r.players[i].points > 0
        // 비교용 순서: 점수 높은 순 (같으면 앞 사람 먼저)
        val idx = (0 until n).sortedWith(compareByDescending<Int> { r.players[it].points }.thenBy { it })
        for (k in 0 until n) order[k] = idx[k]
        fitLegend()
        mode = Mode.REVEAL
        invalidate()
    }

    /** 모두의 그림 겹쳐 비교 */
    fun compare() { mode = Mode.COMPARE; invalidate() }

    /** 최종: 합계와 순위 */
    fun final(ranks: IntArray, scores: IntArray) {
        for (i in 0 until n) {
            finalText[i] = "합계 ${scores[i]}점"
            rankText[i] = "${ranks[i]}위"
            rankFirst[i] = ranks[i] == 1
            popT[i] = 0f
        }
        mode = Mode.FINAL
        invalidate()
    }

    /** 비교 칸 글자 크기: 가장 긴 이름 + 점수가 칸에 들어가게 */
    private fun fitLegend() {
        if (n == 0) return
        for (i in 0 until n) {
            val rowH = panH[i] / n
            var size = minOf(rowH * 0.5f, 15f * dp)
            legendPaint.textSize = size
            var widest = 0f
            for (k in 0 until n) {
                val w = legendPaint.measureText(names[k]) + legendPaint.measureText(ptsText[k].ifEmpty { "+150점" })
                if (w > widest) widest = w
            }
            val room = panW[i] - size * 2.6f
            if (widest > room && widest > 0f) size *= room / widest
            legendSize[i] = size
        }
    }

    /** 연출 진행. 아직 움직이는 중이면 true */
    fun step(dtMs: Float): Boolean {
        var busy = false
        for (i in 0 until n) {
            if (popT[i] < 1f) {
                popT[i] = (popT[i] + dtMs / popMs.coerceAtLeast(1f)).coerceAtMost(1f)
                busy = true
            }
        }
        if (busy) invalidate()
        return busy
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        input.onTouchEvent(e)
        forwardTo?.onTouchEvent(e)
        return true
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        if (n == 0 || zones.isEmpty()) return
        for (i in 0 until n) {
            val f = input.frame(i)
            c.save()
            c.translate(f.centerX, f.centerY)
            c.rotate(f.rotation)
            c.translate(-f.width / 2f, -f.height / 2f)
            drawHeader(c, i)
            if (mode != Mode.NONE) {
                drawBox(c, i)
                drawPanel(c, i)
            }
            c.restore()
        }
    }

    private fun drawHeader(c: Canvas, i: Int) {
        val w = zw[i]
        val pad = 10f * dp
        val base = headH[i] - 10f * dp
        namePaint.color = colors[i]
        c.drawText(names[i], pad, base, namePaint)
        c.drawText(totals[i], w - pad, base, totalPaint)
        if (mode != Mode.NONE) c.drawText(roundLabel, w / 2f, base, roundPaint)
        // 시간 막대
        val y = headH[i] - 5f * dp
        val bh = 4f * dp
        rect.set(pad, y, w - pad, y + bh)
        fill.color = 0x33FFFFFF
        c.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
        val frac = when (mode) {
            Mode.READY -> 1f
            Mode.DRAWING -> timeFrac
            else -> 0f
        }
        if (frac > 0f) {
            rect.set(pad, y, pad + (w - 2f * pad) * frac, y + bh)
            fill.color = if (hurry) Theme.RED else Theme.GREEN
            c.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
        }
    }

    private fun drawBox(c: Canvas, i: Int) {
        val l = boxL[i]
        val t = boxT[i]
        val s = boxS[i]
        val r = 12f * dp
        rect.set(l, t, l + s, t + s)
        fill.color = 0x40000000
        c.drawRoundRect(rect, r, r, fill)
        val glow = (mode == Mode.DRAWING && done[i]) || ((mode == Mode.REVEAL || mode == Mode.COMPARE) && best[i]) ||
            (mode == Mode.FINAL && rankFirst[i])
        line.color = if (glow) Theme.GOLD else colors[i]
        line.alpha = if (glow) 255 else 110
        line.strokeWidth = (if (glow) 3f else 2f) * dp
        c.drawRoundRect(rect, r, r, line)
        line.alpha = 255

        c.save()
        c.translate(l, t)
        c.scale(s, s)
        // 점선 도형
        dash.strokeWidth = 3.5f * dp / s
        c.drawPath(target, dash)
        // 끝점 고리 (열린 도형)
        if (openShape) {
            line.color = 0xCCFFFFFF.toInt()
            line.strokeWidth = 2.5f * dp / s
            c.drawCircle(endX, endY, 7f * dp / s, line)
        }
        // 그림
        val w = 5f * dp / s
        if (mode == Mode.COMPARE || mode == Mode.FINAL) {
            for (k in n - 1 downTo 0) {
                val p = order[k]
                if (p == i || !hasStroke[p]) continue
                line.color = colors[p]
                line.alpha = 170
                line.strokeWidth = w * 0.7f
                c.drawPath(strokes[p], line)
            }
            line.alpha = 255
        }
        if (hasStroke[i]) {
            line.color = colors[i]
            line.strokeWidth = w
            c.drawPath(strokes[i], line)
        }
        // 시작점 + 화살촉 (그림 위)
        fill.color = 0xFF0A0F2E.toInt()
        c.drawCircle(startX, startY, 8.5f * dp / s, fill)
        fill.color = Theme.GOLD
        c.drawCircle(startX, startY, 6.5f * dp / s, fill)
        c.drawPath(arrowPath, fill)
        c.restore()
    }

    private fun drawPanel(c: Canvas, i: Int) {
        val l = panL[i]
        val t = panT[i]
        val w = panW[i]
        val h = panH[i]
        rect.set(l, t, l + w, t + h)
        fill.color = 0x30000000
        c.drawRoundRect(rect, 10f * dp, 10f * dp, fill)
        val pop = popScale(popT[i])
        when (mode) {
            Mode.READY -> {
                row(c, i, 0, 2, "준비…", Theme.TEXT, 1.15f, 1f)
                row(c, i, 1, 2, "금색 점에서 시작!", Theme.GOLD, 0.8f, 1f)
            }
            Mode.DRAWING -> if (done[i]) {
                row(c, i, 0, 3, "완료!", Theme.GOLD, 1.4f, pop)
                row(c, i, 1, 3, doneText[i], Theme.TEXT, 0.9f, 1f)
                row(c, i, 2, 3, doneBonus[i], Theme.GREEN, 0.85f, 1f)
            } else {
                row(c, i, 0, 3, "그려요!", Theme.TEXT, 1.15f, 1f)
                row(c, i, 1, 3, progressText[i], Theme.TEXT_SUB, 0.85f, 1f)
                row(c, i, 2, 3, timeText, if (hurry) Theme.RED else Theme.TEXT, 0.9f, 1f)
            }
            Mode.REVEAL -> {
                row(c, i, 0, 3, accText[i], Theme.TEXT, 0.95f, 1f)
                row(c, i, 1, 3, bonusText[i], bonusColor[i], 0.9f, 1f)
                row(c, i, 2, 3, ptsText[i], Theme.GOLD, 1.35f, pop)
            }
            Mode.COMPARE -> drawLegend(c, i, l, t, w, h)
            Mode.FINAL -> {
                row(c, i, 0, 2, finalText[i], Theme.TEXT, 0.95f, 1f)
                row(c, i, 1, 2, rankText[i], if (rankFirst[i]) Theme.GOLD else Theme.TEXT_SUB, 1.6f, pop)
            }
            else -> {}
        }
    }

    /** 안내 칸 k 번째 줄 (rows 줄 중) 가운데에 글자. scale = 기본 크기 배수, pop = 커지는 연출 배수 */
    private fun row(c: Canvas, i: Int, k: Int, rows: Int, text: String, color: Int, scale: Float, pop: Float) {
        val rowH = panH[i] / rows
        val wide = panW[i] > panH[i] * 1.6f
        val base = if (wide) minOf(rowH * 0.62f, 17f * dp) else minOf(rowH * 0.42f, 16f * dp)
        var size = base * scale
        textPaint.textSize = size
        // 칸 폭에 맞춤
        val tw = textPaint.measureText(text)
        val room = panW[i] - 10f * dp
        if (tw > room) size *= room / tw
        size *= pop
        val cx = panL[i] + panW[i] / 2f
        val cy = panT[i] + rowH * (k + 0.5f)
        val y = cy + size * 0.36f
        outline.textSize = size
        outline.strokeWidth = size * 0.16f
        c.drawText(text, cx, y, outline)
        textPaint.textSize = size
        textPaint.color = color
        c.drawText(text, cx, y, textPaint)
    }

    /** 비교: 이번 라운드 점수 순서 (색 점 + 이름 + 점수), 내 줄은 밝게 */
    private fun drawLegend(c: Canvas, i: Int, l: Float, t: Float, w: Float, h: Float) {
        val rowH = h / n
        val size = legendSize[i]
        legendPaint.textSize = size
        val pad = 8f * dp
        for (k in 0 until n) {
            val p = order[k]
            val top = t + rowH * k
            val cy = top + rowH / 2f
            if (p == i) {
                rect.set(l + 3f * dp, top + 2f * dp, l + w - 3f * dp, top + rowH - 2f * dp)
                fill.color = 0x2EFFFFFF
                c.drawRoundRect(rect, 8f * dp, 8f * dp, fill)
            }
            fill.color = colors[p]
            c.drawCircle(l + pad + size * 0.4f, cy, size * 0.4f, fill)
            legendPaint.textAlign = Paint.Align.LEFT
            legendPaint.color = if (best[p]) Theme.GOLD else Theme.TEXT
            c.drawText(names[p], l + pad + size * 1.1f, cy + size * 0.36f, legendPaint)
            legendPaint.textAlign = Paint.Align.RIGHT
            legendPaint.color = if (best[p]) Theme.GOLD else Theme.TEXT_SUB
            c.drawText(ptsText[p], l + w - pad, cy + size * 0.36f, legendPaint)
        }
    }

    /** 커졌다 돌아오는 배수 (0.6 → 약 1.2 → 1) */
    private fun popScale(t: Float): Float = if (t >= 1f) 1f else 0.6f + 0.4f * t + 0.25f * sin(PI.toFloat() * t)

    private fun fmt2(v: Float): String {
        val x = (v * 100f).roundToInt()
        val frac = x % 100
        return "${x / 100}." + (if (frac < 10) "0$frac" else "$frac")
    }

    companion object {
        const val MAX_ZONES = 4
        /** 화살촉 위치 (길이 비율) */
        const val ARROW_U = 0.08f
    }
}
