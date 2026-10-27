package com.siwoo.party.games.redlight.ui

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
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.redlight.GameConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * 무궁화 꽃이 피었습니다 판 (MultiTouchZoneView 위에 겹치는 그림 층, 터치는 아래 구역이 받는다).
 * 구역마다 같은 배치를 그 사람 방향으로 돌려 그린다(위쪽 구역 180°):
 * - 위: 이름 · 남은 시간
 * - 외치는 글자 10칸 (나타난 글자는 금색, 술래가 보면 빨강)
 * - 가로 레인: 왼쪽 출발선 → 말(플레이어 색) → 금색 도착선 → 오른쪽 끝 술래(도형 캐릭터)
 * - 아래: 상태 글자 (누르고 있으면 전진 / 걸렸다! / 도착!)
 * 움직임은 step(dtMs) 로 진행 (화면이 경기·연출 중에만 프레임을 돌린다).
 */
class RedlightView(context: Context) : View(context) {

    private val dp = Theme.dpf(context, 1f)
    private var n = 0
    private var zones: List<Zone> = emptyList()
    private val names = ArrayList<String>()
    private var colors = IntArray(0)

    // ── 상태 ──
    private var progress = FloatArray(0)
    private var moving = BooleanArray(0)
    private var arrived = BooleanArray(0)
    private var flash = FloatArray(0)
    private var backT = FloatArray(0)
    private var backFrom = FloatArray(0)
    private var status = arrayOfNulls<String>(0)
    private var statusColor = IntArray(0)
    private var letters = 0
    private var pop = 0f
    /** 술래 방향 0 = 뒤돌아 있음 .. 1 = 쳐다봄, 목표 */
    private var turn = 0f
    private var looking = false
    private var watching = false
    private var glow = 0f
    private var bob = 0f
    private var timeText = ""
    private var hurry = false

    /** 연출 시간 (속도 배율 적용된 값) */
    var turnMs = 160f
    var backMs = 650f
    var flashMs = 700f

    // ── 배치 (구역 안 좌표, 모든 구역 같은 크기) ──
    private var zw = 0f
    private var zh = 0f
    private var u = dp
    private var pad = 0f
    private var headerY = 0f
    private var phraseY = 0f
    private var laneTop = 0f
    private var laneBottom = 0f
    private var groundY = 0f
    private var startX = 0f
    private var finishX = 0f
    private var taggerX = 0f
    private var taggerH = 0f
    private var runnerR = 0f
    private var footerY = 0f
    private var letterSize = 0f
    private val letterX = FloatArray(GameConfig.PHRASE_LEN)
    private val phrase = GameConfig.PHRASE.toCharArray()

    // ── 그리기 도구 ──
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val letterEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
        color = 0xFF3A1A00.toInt()
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()

    init {
        isClickable = false
        isFocusable = false
    }

    fun setup(playerNames: List<String>, playerColors: IntArray, hint: String) {
        n = playerNames.size
        names.clear()
        names.addAll(playerNames)
        colors = playerColors.copyOf()
        progress = FloatArray(n)
        moving = BooleanArray(n)
        arrived = BooleanArray(n)
        flash = FloatArray(n)
        backT = FloatArray(n) { 1f }
        backFrom = FloatArray(n)
        status = arrayOfNulls(n)
        for (i in 0 until n) status[i] = hint
        statusColor = IntArray(n) { Theme.TEXT_SUB }
        relayout()
    }

    fun setProgress(i: Int, v: Float) {
        if (progress[i] != v) { progress[i] = v; invalidate() }
    }

    fun setMoving(i: Int, v: Boolean) { moving[i] = v }

    fun setArrived(i: Int) { arrived[i] = true; invalidate() }

    fun setStatus(i: Int, text: String, color: Int) {
        if (status[i] === text && statusColor[i] == color) return
        status[i] = text
        statusColor[i] = color
        invalidate()
    }

    /** 나타난 글자 수. 늘어나면 새 글자가 톡 커진다 */
    fun setLetters(count: Int) {
        if (count > letters) pop = 1f
        letters = count
        invalidate()
    }

    /** 술래가 돌아봄(true) / 다시 뒤돎(false) */
    fun setLooking(v: Boolean) { looking = v; if (!v) watching = false; invalidate() }

    /** 유예가 끝나 눈이 빛남 */
    fun setWatching(v: Boolean) { watching = v; invalidate() }

    fun setTime(text: String, hurry: Boolean) {
        timeText = text
        this.hurry = hurry
        invalidate()
    }

    /** 걸림: from 위치에서 출발점으로 미끄러져 돌아감 + 빨간 번쩍 */
    fun caught(i: Int, from: Float) {
        backFrom[i] = from
        backT[i] = 0f
        flash[i] = 1f
        invalidate()
    }

    /** 연출이 남았는지 */
    val busy: Boolean
        get() {
            if (pop > 0.01f || abs(turn - (if (looking) 1f else 0f)) > 0.001f) return true
            for (i in 0 until n) if (flash[i] > 0.01f || backT[i] < 1f) return true
            return false
        }

    fun step(dtMs: Float) {
        pop *= exp(-dtMs / 120f)
        if (pop < 0.01f) pop = 0f
        val target = if (looking) 1f else 0f
        val d = dtMs / turnMs.coerceAtLeast(1f)
        turn = if (turn < target) minOf(target, turn + d) else maxOf(target, turn - d)
        glow += dtMs * 0.012f
        if (glow > 1000f) glow -= 1000f
        bob += dtMs * 0.022f
        if (bob > 1000f) bob -= 1000f
        for (i in 0 until n) {
            flash[i] *= exp(-dtMs / (flashMs * 0.35f).coerceAtLeast(1f))
            if (flash[i] < 0.01f) flash[i] = 0f
            if (backT[i] < 1f) backT[i] = minOf(1f, backT[i] + dtMs / backMs.coerceAtLeast(1f))
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || n == 0) return
        // MultiTouchZoneView 와 같은 구역
        zones = TouchZones.layout(n, width.toFloat(), height.toFloat(), Theme.dpf(context, 10f))
        val z = zones[0]
        zw = z.width
        zh = z.height
        val k = minOf(zw / (300f * dp), zh / (150f * dp)).coerceIn(0.72f, 1.3f)
        u = dp * k
        pad = 16f * u
        val header = 20f * u
        val phraseH = 26f * u
        var laneH = 56f * u
        val footer = 16f * u
        val gaps = 5f * u + 7f * u + 6f * u
        val total = header + phraseH + laneH + footer + gaps
        val margin = 12f * u
        val leftover = zh - 2 * margin - total
        if (leftover > 0) laneH += minOf(leftover * 0.6f, 90f * u)
        val block = header + phraseH + laneH + footer + gaps
        var y = (zh - block) / 2f
        namePaint.textSize = 16f * u
        timePaint.textSize = 15f * u
        headerY = y + header * 0.78f
        y += header + 5f * u
        phraseY = y + phraseH / 2f
        y += phraseH + 7f * u
        laneTop = y
        laneBottom = y + laneH
        y += laneH + 6f * u
        statusPaint.textSize = 14f * u
        footerY = y + footer * 0.8f
        smallPaint.textSize = 9f * u

        taggerH = laneH * 0.86f
        groundY = laneBottom - laneH * 0.12f
        runnerR = minOf(laneH * 0.2f, 15f * u)
        taggerX = zw - pad - taggerH * 0.32f
        finishX = taggerX - taggerH * 0.5f
        startX = pad + runnerR + 8f * u

        // 글자 10칸 + 띄어쓰기 2곳
        val gapUnits = 0.55f
        val units = GameConfig.PHRASE_LEN + GameConfig.PHRASE_GAPS.size * gapUnits
        val slot = minOf((zw - 2 * pad) / units, 28f * u)
        letterSize = slot * 0.86f
        letterPaint.textSize = letterSize
        letterEdge.textSize = letterSize
        letterEdge.strokeWidth = letterSize * 0.16f
        var x = (zw - slot * units) / 2f + slot / 2f
        for (i in 0 until GameConfig.PHRASE_LEN) {
            letterX[i] = x
            x += slot
            for (g in GameConfig.PHRASE_GAPS) if (g == i) x += slot * gapUnits
        }
        invalidate()
    }

    /** 말 위치 (구역 안 좌표 x) */
    private fun runnerLocalX(i: Int): Float {
        val f = if (backT[i] < 1f) {
            val e = 1f - (1f - backT[i]) * (1f - backT[i])
            backFrom[i] * (1f - e)
        } else progress[i]
        return startX + (finishX - startX) * f.coerceIn(0f, 1f)
    }

    /** 말 가운데 (이 View 좌표) → out */
    fun runnerCenter(i: Int, out: PointF) {
        val z = zones.getOrNull(i) ?: return
        val lx = runnerLocalX(i)
        val ly = groundY - runnerR
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
            drawZone(c, i)
            c.restore()
        }
    }

    private fun drawZone(c: Canvas, i: Int) {
        val color = colors[i]
        // 이름 · 시간
        namePaint.color = color
        c.drawText(names[i], pad, headerY, namePaint)
        timePaint.color = if (hurry) Theme.RED else Theme.TEXT
        c.drawText(timeText, zw - pad, headerY, timePaint)
        val tw = timePaint.measureText(timeText)
        drawClock(c, zw - pad - tw - 10f * u, headerY - timePaint.textSize * 0.36f, 6.5f * u, timePaint.color)

        drawPhrase(c)
        drawLane(c, i, color)

        // 상태
        val s = status[i]
        if (s != null) {
            statusPaint.color = statusColor[i]
            c.drawText(s, zw / 2f, footerY, statusPaint)
        }
        // 걸림 번쩍
        if (flash[i] > 0f) {
            p.style = Paint.Style.FILL
            p.color = Theme.RED
            p.alpha = (flash[i] * 120).toInt()
            rect.set(4f * dp, 4f * dp, zw - 4f * dp, zh - 4f * dp)
            c.drawRoundRect(rect, 18f * dp, 18f * dp, p)
            p.alpha = 255
        }
    }

    private fun drawClock(c: Canvas, cx: Float, cy: Float, r: Float, col: Int) {
        line.color = col
        line.strokeWidth = 1.8f * u
        c.drawCircle(cx, cy, r, line)
        c.drawLine(cx, cy, cx, cy - r * 0.6f, line)
        c.drawLine(cx, cy, cx + r * 0.45f, cy, line)
    }

    private fun drawPhrase(c: Canvas) {
        val shownColor = if (looking) Theme.RED else Theme.GOLD
        val baseOff = letterSize * 0.36f
        for (k in 0 until GameConfig.PHRASE_LEN) {
            val x = letterX[k]
            if (k < letters) {
                val big = if (k == letters - 1 && pop > 0f) 1f + 0.4f * pop else 1f
                letterPaint.textSize = letterSize * big
                letterEdge.textSize = letterSize * big
                letterPaint.color = shownColor
                c.drawText(phrase, k, 1, x, phraseY + baseOff * big, letterEdge)
                c.drawText(phrase, k, 1, x, phraseY + baseOff * big, letterPaint)
            } else {
                p.style = Paint.Style.FILL
                p.color = 0x40FFFFFF
                c.drawCircle(x, phraseY, letterSize * 0.13f, p)
            }
        }
        letterPaint.textSize = letterSize
        letterEdge.textSize = letterSize
    }

    private fun drawLane(c: Canvas, i: Int, color: Int) {
        // 땅
        p.style = Paint.Style.FILL
        p.color = 0x8C050A26.toInt()
        rect.set(pad - 6f * u, laneTop, zw - pad + 6f * u, laneBottom)
        c.drawRoundRect(rect, 12f * u, 12f * u, p)
        line.strokeWidth = 1.5f * u
        line.color = if (looking) Theme.RED else 0x33FFFFFF
        c.drawRoundRect(rect, 12f * u, 12f * u, line)
        // 땅 선 + 눈금
        line.color = 0x40FFFFFF
        line.strokeWidth = 2f * u
        c.drawLine(pad, groundY, zw - pad, groundY, line)
        for (q in 1..3) {
            val x = startX + (finishX - startX) * q / 4f
            c.drawLine(x, groundY - 3f * u, x, groundY + 3f * u, line)
        }
        // 출발선 (흰 점선)
        line.color = 0xCCFFFFFF.toInt()
        line.strokeWidth = 2f * u
        var y = laneTop + 6f * u
        while (y < groundY) {
            c.drawLine(startX, y, startX, minOf(y + 4f * u, groundY), line)
            y += 8f * u
        }
        // 도착선 (금색)
        line.color = Theme.GOLD
        line.strokeWidth = 3f * u
        c.drawLine(finishX, laneTop + 6f * u, finishX, groundY, line)
        smallPaint.color = Theme.GOLD
        c.drawText("도착", finishX, laneTop + 6f * u - 2f * u + smallPaint.textSize, smallPaint)
        smallPaint.color = 0xCCFFFFFF.toInt()
        c.drawText("출발", startX, groundY + smallPaint.textSize + 1.5f * u, smallPaint)

        // 지나온 길 (플레이어 색)
        val rx = runnerLocalX(i)
        if (rx > startX + 1f) {
            p.color = color
            p.alpha = 90
            rect.set(startX, groundY - 3f * u, rx, groundY + 3f * u)
            c.drawRoundRect(rect, 3f * u, 3f * u, p)
            p.alpha = 255
        }
        drawTagger(c)
        drawRunner(c, i, rx, color)
    }

    private fun drawRunner(c: Canvas, i: Int, x: Float, color: Int) {
        val r = runnerR
        val hop = if (moving[i] && !arrived[i]) abs(sin(bob)) * 4f * u else 0f
        val cy = groundY - r - hop
        // 그림자
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        rect.set(x - r * 0.8f, groundY - r * 0.18f, x + r * 0.8f, groundY + r * 0.18f)
        c.drawOval(rect, p)
        p.color = color
        c.drawCircle(x, cy, r, p)
        line.color = 0xFFFFFFFF.toInt()
        line.strokeWidth = 2f * u
        c.drawCircle(x, cy, r, line)
        // 눈 (술래 쪽 = 오른쪽을 본다)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x + r * 0.18f, cy - r * 0.18f, r * 0.26f, p)
        c.drawCircle(x + r * 0.62f, cy - r * 0.18f, r * 0.22f, p)
        p.color = Theme.INK
        c.drawCircle(x + r * 0.26f, cy - r * 0.16f, r * 0.12f, p)
        c.drawCircle(x + r * 0.68f, cy - r * 0.16f, r * 0.1f, p)
        if (arrived[i]) {
            Ui.drawCrown(c, x, cy - r * 0.95f, r * 1.3f, path, p)
        }
    }

    /** 술래: 몸통(사다리꼴) + 머리. 뒤돌면 머리카락만, 쳐다보면 얼굴 + 빨간 눈 */
    private fun drawTagger(c: Canvas) {
        val h = taggerH
        val cx = taggerX
        val headR = h * 0.2f
        val headY = groundY - h + headR
        val bodyTop = headY + headR * 0.85f
        // 빨간 경고 빛 (쳐다볼 때)
        if (turn > 0.5f) {
            p.style = Paint.Style.FILL
            p.color = Theme.RED
            val a = if (watching) 0.55f + 0.25f * sin(glow) else 0.35f
            p.alpha = (a * 90 * (turn - 0.5f) * 2f).toInt().coerceIn(0, 255)
            c.drawCircle(cx, headY + headR * 0.4f, h * 0.55f, p)
            p.alpha = 255
        }
        // 그림자
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        rect.set(cx - h * 0.3f, groundY - h * 0.04f, cx + h * 0.3f, groundY + h * 0.04f)
        c.drawOval(rect, p)

        val sx = abs(cos(Math.PI * turn).toFloat()).coerceAtLeast(0.08f)
        val front = turn > 0.5f
        c.save()
        c.scale(sx, 1f, cx, 0f)
        // 몸통
        path.reset()
        path.moveTo(cx - h * 0.14f, bodyTop)
        path.lineTo(cx + h * 0.14f, bodyTop)
        path.lineTo(cx + h * 0.27f, groundY)
        path.lineTo(cx - h * 0.27f, groundY)
        path.close()
        p.color = SHIRT
        c.drawPath(path, p)
        p.color = SHIRT_DARK
        rect.set(cx - h * 0.27f, groundY - h * 0.1f, cx + h * 0.27f, groundY)
        c.drawRect(rect, p)
        // 머리
        if (front) {
            p.color = SKIN
            c.drawCircle(cx, headY, headR, p)
            // 앞머리
            p.color = HAIR
            rect.set(cx - headR, headY - headR, cx + headR, headY + headR * 0.1f)
            c.drawArc(rect, 180f, 180f, true, p)
            // 눈
            val ex = headR * 0.42f
            val ey = headY + headR * 0.22f
            if (watching) {
                p.color = Theme.RED
                p.alpha = (110 + 60 * sin(glow)).toInt().coerceIn(0, 255)
                c.drawCircle(cx - ex, ey, headR * 0.42f, p)
                c.drawCircle(cx + ex, ey, headR * 0.42f, p)
                p.alpha = 255
                p.color = 0xFFFF2030.toInt()
            } else {
                p.color = Theme.INK
            }
            c.drawCircle(cx - ex, ey, headR * 0.17f, p)
            c.drawCircle(cx + ex, ey, headR * 0.17f, p)
            if (watching) {
                p.color = 0xFFFFFFFF.toInt()
                c.drawCircle(cx - ex - headR * 0.05f, ey - headR * 0.06f, headR * 0.06f, p)
                c.drawCircle(cx + ex - headR * 0.05f, ey - headR * 0.06f, headR * 0.06f, p)
            }
        } else {
            // 뒤통수: 머리카락 + 묶은 머리
            p.color = HAIR
            c.drawCircle(cx, headY, headR, p)
            c.drawCircle(cx, headY - headR * 0.95f, headR * 0.38f, p)
            p.color = SHIRT
            rect.set(cx - headR * 0.32f, headY - headR * 0.72f, cx + headR * 0.32f, headY - headR * 0.56f)
            c.drawRoundRect(rect, headR * 0.1f, headR * 0.1f, p)
            // 얼굴 가린 손 (외치는 중)
            p.color = SKIN
            c.drawCircle(cx - headR * 0.92f, headY + headR * 0.25f, headR * 0.28f, p)
            c.drawCircle(cx + headR * 0.92f, headY + headR * 0.25f, headR * 0.28f, p)
        }
        c.restore()
    }

    companion object {
        private const val SHIRT = 0xFFE85D3A.toInt()
        private const val SHIRT_DARK = 0xFF2A3166.toInt()
        private const val SKIN = 0xFFFFD3A8.toInt()
        private const val HAIR = 0xFF3B2416.toInt()
    }
}
