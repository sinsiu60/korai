package com.siwoo.party.games.sprint.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.sprint.Foot
import com.siwoo.party.games.sprint.GameConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * 100m 달리기 무대 (화면 전체 위에 겹치는 그림 층, 터치는 아래 MultiTouchZoneView 로 그대로 전달).
 * - 가운데 띠: 레인(플레이어 색 이름표) · 10 m 눈금 · 출발선 · 체크 결승선 · 옆모습 선수 · 오른쪽 기록
 *   띠 위쪽 가운데: 출발 신호등 + 신호/경주 시계 알약
 * - 각 구역의 바깥쪽(그 사람 가까이): [왼발] [오른발] 입체 버튼 + 가운데 이름·거리·상태.
 *   위쪽 구역은 180° 돌려 그린다 → 그 사람 기준 왼쪽이 왼발. 판정은 footAt(구역 절반 기준).
 *   도착하면 버튼 대신 순위·기록 판.
 * 움직임은 step(dtMs) 로 진행 (화면이 경주 중·연출 중에만 프레임을 돌린다).
 */
class SprintView(context: Context) : View(context) {

    private val u = Theme.dpf(context, 1f)

    // ── 플레이어 ──
    private var n = 0
    private val colors = IntArray(MAX)
    private val names = arrayOfNulls<String>(MAX)
    private val nameSize = FloatArray(MAX)
    private val status = arrayOfNulls<String>(MAX)
    private val statusColor = IntArray(MAX)
    private val msg = arrayOfNulls<String>(MAX)
    private val msgColor = IntArray(MAX)
    private val msgT = FloatArray(MAX)
    private val distText = arrayOfNulls<String>(MAX)
    private val record = arrayOfNulls<String>(MAX)
    private val rank = IntArray(MAX)
    private val winner = BooleanArray(MAX)
    private val frozen = BooleanArray(MAX)
    private val lastFoot = arrayOfNulls<Foot>(MAX)
    private var zones: List<Zone> = emptyList()

    // ── 움직임 ──
    private val targetCm = FloatArray(MAX)
    private val shownCm = FloatArray(MAX)
    private val stride = FloatArray(MAX)
    private val strideTarget = FloatArray(MAX)
    private val sinceStep = FloatArray(MAX)
    private val flashL = FloatArray(MAX)
    private val flashR = FloatArray(MAX)
    private val shakeT = FloatArray(MAX) { -1f }
    private val finishFlash = FloatArray(MAX)
    private var crouch = 0f
    private var crouchTarget = 0f
    private var cheer = 0f
    private var stepMs = GameConfig.STEP_ANIM_MS.toFloat()
    private var flashMs = GameConfig.PRESS_FLASH_MS.toFloat()
    private var shakeMs = GameConfig.SHAKE_MS.toFloat()
    private var msgMs = GameConfig.MSG_MS.toFloat()
    private var finishMs = GameConfig.FINISH_FLASH_MS.toFloat()

    // ── 신호·시계 ──
    /** 0 없음, 1 준비, 2 차렷, 3 땅/경주 중 */
    private var signal = 0
    private var pillText: String? = WAIT
    private val clock = CharArray(12)
    private var clockLen = 0
    private var clockCs = -1L
    private var clockWarn = false
    private var pillPulse = 0f
    /** 버튼 쓸 수 있음 (소개 중·끝난 뒤엔 흐리게) */
    private var live = false

    // ── 배치 (relayout) ──
    private var sc = 1f
    private var bandTop = 0f
    private var bandBottom = 0f
    private var bandL = 0f
    private var bandR = 0f
    private var headerH = 0f
    private var laneH = 0f
    private var lanesTop = 0f
    private var startX = 0f
    private var finishX = 0f
    private val region = Array(MAX) { RectF() }
    private val btnL = Array(MAX) { RectF() }
    private val btnR = Array(MAX) { RectF() }
    private val colX = FloatArray(MAX)
    private val colY = FloatArray(MAX)
    private var btnRadius = 0f
    private var btnDepth = 0f

    // ── 그리기 도구 ──
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val crownPath = Path()
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val btnText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val laneName = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val recPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT; color = Theme.TEXT }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        isClickable = false
        isFocusable = false
    }

    /** 플레이어 이름·색 (players 순서) */
    fun setup(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size
        for (i in 0 until n) {
            names[i] = playerNames[i]
            colors[i] = playerColors[i]
            status[i] = ""
            statusColor[i] = Theme.TEXT_SUB
            distText[i] = DIST[0]
        }
        relayout()
    }

    /** 연출 시간 (속도 배율 적용된 값) */
    fun setTimes(step: Long, flash: Long, shake: Long, message: Long, finish: Long) {
        stepMs = step.coerceAtLeast(30).toFloat()
        flashMs = flash.coerceAtLeast(30).toFloat()
        shakeMs = shake.coerceAtLeast(30).toFloat()
        msgMs = message.coerceAtLeast(100).toFloat()
        finishMs = finish.coerceAtLeast(60).toFloat()
    }

    /** 버튼 사용 가능 표시 */
    fun setLive(v: Boolean) {
        live = v
        invalidate()
    }

    /** 구역 상태 글자 (이름·거리 아래) */
    fun setStatus(i: Int, text: String, color: Int = Theme.TEXT_SUB) {
        status[i] = text
        statusColor[i] = color
        invalidate()
    }

    fun statusOf(i: Int): String = status[i] ?: ""

    /** 잠깐 보이는 글자 ("같은 발!" 등) */
    fun flashMessage(i: Int, text: String, color: Int) {
        msg[i] = text
        msgColor[i] = color
        msgT[i] = msgMs
        invalidate()
    }

    /** 출발 신호: 1 준비, 2 차렷, 3 땅 (알약 글자) */
    fun setSignal(state: Int, text: String) {
        signal = state
        pillText = text
        pillPulse = 1f
        crouchTarget = if (state == 2) 1f else 0f
        invalidate()
    }

    /** 경주 시계 (ms). 1/100초가 바뀔 때만 글자를 새로 만든다 (할당 없음) */
    fun setClock(ms: Long, warn: Boolean) {
        pillText = null
        val cs = ms.coerceAtLeast(0L) / 10L
        if (cs == clockCs && warn == clockWarn) return
        clockCs = cs
        clockWarn = warn
        var len = 0
        val sec = cs / 100L
        val frac = (cs % 100L).toInt()
        // 초 (자릿수 역순으로 채우고 뒤집기)
        var s = sec
        val start = len
        do {
            clock[len++] = ('0' + (s % 10L).toInt())
            s /= 10L
        } while (s > 0L && len < 6)
        var a = start
        var b = len - 1
        while (a < b) {
            val tmp = clock[a]; clock[a] = clock[b]; clock[b] = tmp
            a++; b--
        }
        clock[len++] = '.'
        clock[len++] = '0' + frac / 10
        clock[len++] = '0' + frac % 10
        clock[len++] = '초'
        clockLen = len
        invalidate()
    }

    /** 지금 시계 글자 (테스트용) */
    fun clockText(): String = String(clock, 0, clockLen)

    /** 한 걸음 전진: foot 버튼 빛 + 다리 휘두르기 */
    fun step(i: Int, foot: Foot, cm: Int) {
        press(i, foot)
        lastFoot[i] = foot
        strideTarget[i] = if (foot == Foot.LEFT) 1f else -1f
        sinceStep[i] = 0f
        targetCm[i] = cm.toFloat()
        distText[i] = DIST[(cm / 100).coerceIn(0, 100)]
        invalidate()
    }

    /** 버튼만 눌림 빛 (전진 없음) */
    fun press(i: Int, foot: Foot) {
        if (foot == Foot.LEFT) flashL[i] = 1f else flashR[i] = 1f
        invalidate()
    }

    /** 구역 흔들기 ("같은 발!") */
    fun shake(i: Int) {
        shakeT[i] = 0f
        invalidate()
    }

    fun shaking(i: Int): Boolean = shakeT[i] in 0f..0.999f

    /** 부정 출발 정지 표시 */
    fun setFrozen(i: Int, v: Boolean) {
        if (frozen[i] == v) return
        frozen[i] = v
        invalidate()
    }

    fun isFrozen(i: Int): Boolean = frozen[i]

    /** 도착: 순위(1부터)·기록 글자 (순위는 나중 도착자에 따라 다시 불릴 수 있다) */
    fun setResult(i: Int, place: Int, recordText: String, flash: Boolean) {
        rank[i] = place
        record[i] = recordText
        if (flash) finishFlash[i] = 1f
        invalidate()
    }

    /** 못 들어온 사람: 순위와 거리 */
    fun setUnfinished(i: Int, place: Int, distanceText: String) {
        rank[i] = place
        record[i] = distanceText
        invalidate()
    }

    fun rankOf(i: Int): Int = rank[i]

    /** 우승자 (만세 + 왕관) */
    fun setWinner(i: Int) {
        winner[i] = true
        cheer = 0.001f
        invalidate()
    }

    /** 플레이어 i 구역에서 (x, y) 가 그 사람 기준 왼발/오른발 (구역 가로 절반 기준, 180° 구역은 뒤집힘) */
    fun footAt(i: Int, x: Float): Foot {
        val z = zones.getOrNull(i) ?: return Foot.LEFT
        val leftHalf = x < z.centerX
        val rotated = z.rotation == 180f
        return if (leftHalf != rotated) Foot.LEFT else Foot.RIGHT
    }

    /** 테스트용: 버튼 가운데 (화면 좌표) */
    fun buttonCenterX(i: Int, foot: Foot): Float {
        val r = if (foot == Foot.LEFT) btnL[i] else btnR[i]
        val cx = r.centerX()
        val z = zones[i]
        return if (z.rotation == 180f) 2f * region[i].centerX() - cx else cx
    }

    fun buttonCenterY(i: Int, foot: Foot): Float {
        val r = if (foot == Foot.LEFT) btnL[i] else btnR[i]
        val cy = r.centerY()
        return if (zones[i].rotation == 180f) 2f * region[i].centerY() - cy else cy
    }

    /** 테스트용: 선수 위치 (화면 x) / 보이는 거리 */
    fun runnerX(i: Int): Float = startX + (finishX - startX) * (shownCm[i] / GameConfig.TRACK_CM)
    fun shownDistance(i: Int): Float = shownCm[i]

    /** 띠 위·아래 (테스트·배너 위치용) */
    fun bandTopY(): Float = bandTop
    fun bandBottomY(): Float = bandBottom

    /** 결승선 위 레인 i 가운데 (연출 위치) */
    fun finishX(): Float = finishX
    fun laneCenterY(i: Int): Float = lanesTop + laneH * (i + 0.5f)

    /**
     * 시간 dtMs 만큼 연출을 진행하고 다시 그린다. 아직 움직일 게 남았으면 true.
     */
    fun step(dtMs: Float): Boolean {
        var busy = false
        for (i in 0 until n) {
            // 선수 위치: 목표 거리로 부드럽게
            val d = targetCm[i] - shownCm[i]
            if (abs(d) > 4f) {
                shownCm[i] += d * min(1f, dtMs / 70f)
                busy = true
            } else {
                shownCm[i] = targetCm[i]
            }
            // 다리: 걸음이 멈추면 선 자세로
            sinceStep[i] += dtMs
            if (sinceStep[i] > IDLE_MS) strideTarget[i] = 0f
            val sd = strideTarget[i] - stride[i]
            if (abs(sd) > 0.01f) {
                val m = dtMs / stepMs * 2f
                stride[i] += sd.coerceIn(-m, m)
                busy = true
            } else {
                stride[i] = strideTarget[i]
            }
            if (flashL[i] > 0f) { flashL[i] = (flashL[i] - dtMs / flashMs).coerceAtLeast(0f); busy = true }
            if (flashR[i] > 0f) { flashR[i] = (flashR[i] - dtMs / flashMs).coerceAtLeast(0f); busy = true }
            if (shakeT[i] >= 0f && shakeT[i] < 1f) {
                shakeT[i] = (shakeT[i] + dtMs / shakeMs).coerceAtMost(1f)
                busy = true
            }
            if (msgT[i] > 0f) { msgT[i] = (msgT[i] - dtMs).coerceAtLeast(0f); busy = true }
            if (finishFlash[i] > 0f) { finishFlash[i] = (finishFlash[i] - dtMs / finishMs).coerceAtLeast(0f); busy = true }
        }
        val cd = crouchTarget - crouch
        if (abs(cd) > 0.01f) {
            crouch += cd.coerceIn(-dtMs / 250f, dtMs / 250f)
            busy = true
        } else {
            crouch = crouchTarget
        }
        if (pillPulse > 0f) { pillPulse = (pillPulse - dtMs / 300f).coerceAtLeast(0f); busy = true }
        if (cheer > 0f && cheer < 1f) {
            cheer = (cheer + dtMs / 2000f).coerceAtMost(1f)
            busy = true
        }
        invalidate()
        return busy
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || n == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        zones = TouchZones.layout(n, w, h, Theme.dpf(context, 10f))
        sc = (h / (360f * u)).coerceIn(0.75f, 1.3f)
        val k = u * sc
        laneH = when (n) { 2 -> 50f; 3 -> 30f; else -> 26f } * k
        headerH = 26f * k
        val footerH = 14f * k
        val bandH = headerH + n * laneH + footerH
        bandTop = if (n == 2) 10f * k else (h - bandH) / 2f
        bandBottom = bandTop + bandH
        bandL = 8f * u
        bandR = w - 8f * u
        lanesTop = bandTop + headerH
        startX = bandL + 64f * k
        finishX = bandR - 92f * k
        laneName.textSize = when (n) { 2 -> 14f; 3 -> 12f; else -> 11f } * k
        markPaint.textSize = 9.5f * k
        pillPaint.textSize = 15f * k
        recPaint.textSize = recSize()
        badgePaint.textSize = (if (n == 4) 10.5f else 12f) * k
        btnText.textSize = 15f * k
        bigPaint.textSize = 20f * k
        subPaint.textSize = 12f * k
        btnRadius = 14f * k
        btnDepth = 4f * k

        // 구역마다 바깥쪽(그 사람 가까이) 조작 영역
        val pad = 10f * u
        for (i in 0 until n) {
            val z = zones[i]
            val r = region[i]
            if (z.rotation == 180f) {
                r.set(z.left + pad, z.top + pad, z.right - pad, min(z.bottom, bandTop) - pad)
            } else {
                r.set(z.left + pad, maxOf(z.top, bandBottom) + pad, z.right - pad, z.bottom - pad)
            }
            // 회전 기준 = r 가운데 → 돌린 좌표계에서도 같은 사각형. 아래쪽(바깥쪽)에 버튼
            val colW = (r.width() * 0.27f).coerceIn(74f * k, 100f * k)
            val bw = ((r.width() - colW) / 2f).coerceAtMost(150f * k)
            val bh = min(r.height(), (if (n == 2) 150f else 120f) * k)
            btnL[i].set(r.left, r.bottom - bh, r.left + bw, r.bottom)
            btnR[i].set(r.right - bw, r.bottom - bh, r.right, r.bottom)
            colX[i] = r.centerX()
            colY[i] = r.bottom - bh / 2f
            // 이름 크기: 가운데 칸에 맞게
            namePaint.textSize = 16f * k
            val tw = namePaint.measureText(names[i] ?: "")
            nameSize[i] = if (tw > colW - 6f * k) 16f * k * (colW - 6f * k) / tw else 16f * k
        }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        if (w <= 0f || n == 0 || zones.isEmpty()) return
        drawBand(c)
        drawLanes(c)
        drawRunners(c)
        drawHeader(c)
        for (i in 0 until n) drawControls(c, i)
    }

    // ── 가운데 띠 ──

    private fun drawBand(c: Canvas) {
        val k = u * sc
        rect.set(bandL, bandTop, bandR, bandBottom)
        p.style = Paint.Style.FILL
        p.color = 0xE60A1240.toInt()
        c.drawRoundRect(rect, 16f * k, 16f * k, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * u
        p.color = Theme.CARD_BORDER
        c.drawRoundRect(rect, 16f * k, 16f * k, p)
    }

    private fun drawLanes(c: Canvas) {
        val k = u * sc
        val lanesBottom = lanesTop + n * laneH
        val trackL = startX - 14f * k
        val trackR = finishX + 14f * k
        p.style = Paint.Style.FILL
        // 우레탄 트랙
        p.color = SprintArt.TRACK
        c.drawRect(trackL, lanesTop, trackR, lanesBottom, p)
        for (i in 0 until n) {
            val top = lanesTop + laneH * i
            // 레인 색 띠 (플레이어 색, 옅게)
            p.color = colors[i]
            p.alpha = if (frozen[i]) 0 else 46
            c.drawRect(trackL, top, trackR, top + laneH, p)
            p.alpha = 255
            if (frozen[i]) {
                p.color = 0x66FF1E3C
                c.drawRect(trackL, top, trackR, top + laneH, p)
            }
            // 왼쪽 이름표
            rect.set(bandL + 5f * k, top + 3f * k, startX - 18f * k, top + laneH - 3f * k)
            p.color = colors[i]
            c.drawRoundRect(rect, 7f * k, 7f * k, p)
            laneName.color = Theme.textOn(colors[i])
            c.drawText(names[i] ?: "", rect.centerX(), rect.centerY() + laneName.textSize * 0.36f, laneName)
        }
        // 10 m 눈금
        p.strokeWidth = 1.2f * u
        for (m in 1 until 10) {
            val x = startX + (finishX - startX) * m / 10f
            p.color = if (m == 5) 0x88FFFFFF.toInt() else 0x40FFFFFF
            c.drawLine(x, lanesTop, x, lanesBottom, p)
        }
        // 레인 구분선
        p.color = 0xCCFFFFFF.toInt()
        p.strokeWidth = 1.6f * u
        for (i in 0..n) {
            val y = lanesTop + laneH * i
            c.drawLine(trackL, y, trackR, y, p)
        }
        // 출발선 · 결승선
        p.strokeWidth = 3f * k
        p.color = SprintArt.LINE
        c.drawLine(startX, lanesTop, startX, lanesBottom, p)
        SprintArt.checker(c, p, finishX, lanesTop, lanesBottom, 8f * k, 4f * k)
        // 거리 숫자 (띠 아래)
        val my = lanesBottom + 11f * k
        for (m in 0..10) {
            if (n == 4 && m % 2 == 1) continue
            val x = startX + (finishX - startX) * m / 10f
            markPaint.color = if (m == 10) Theme.GOLD else Theme.TEXT_SUB
            c.drawText(MARKS[m], x, my, markPaint)
        }
        // 오른쪽: 순위 배지 + 기록
        for (i in 0 until n) {
            val rec = record[i] ?: continue
            val cy = lanesTop + laneH * (i + 0.5f)
            val r = min(laneH * 0.36f, 11f * k)
            val bx = finishX + 14f * k + r
            val gold = rank[i] == 1
            p.style = Paint.Style.FILL
            p.color = if (gold) Theme.GOLD else Theme.CARD_LIGHT
            c.drawCircle(bx, cy, r, p)
            badgePaint.color = if (gold) Theme.INK else Theme.TEXT
            c.drawText(PLACE_NUM[rank[i].coerceIn(0, 4)], bx, cy + badgePaint.textSize * 0.36f, badgePaint)
            recPaint.color = if (gold) Theme.GOLD_LIGHT else Theme.TEXT
            c.drawText(rec, bx + r + 4f * k, cy + recPaint.textSize * 0.36f, recPaint)
        }
    }

    private fun drawRunners(c: Canvas) {
        val k = u * sc
        val hgt = laneH * 1.3f
        val track = finishX - startX
        for (i in 0 until n) {
            val bottom = lanesTop + laneH * (i + 1)
            val x = startX + track * (shownCm[i] / GameConfig.TRACK_CM) - hgt * 0.1f
            val f = finishFlash[i]
            if (f > 0f) {
                p.style = Paint.Style.FILL
                p.color = Theme.GOLD
                p.alpha = (f * 140f).toInt()
                c.drawCircle(x, bottom - hgt * 0.45f, hgt * (0.5f + 0.5f * (1f - f)), p)
                p.alpha = 255
            }
            val bob = (1f - abs(stride[i])) * abs(stride[i]) * 4f * k * (if (strideTarget[i] == 0f) 0f else 1f)
            var lift = bob
            val cheering = winner[i] && cheer > 0f
            if (cheering) lift += abs(sin(cheer * 6f * PI.toFloat())) * 6f * k * (1f - cheer * 0.5f)
            SprintArt.runner(c, p, x, bottom - 3f * k, hgt, colors[i], stride[i], crouch, cheering, lift)
            if (frozen[i]) {
                // 정지 표시: 빨간 동그라미 "!"
                val bx = x + hgt * 0.32f
                val by = bottom - hgt * 1.0f
                p.style = Paint.Style.FILL
                p.color = Theme.RED
                c.drawCircle(bx, by, 7f * k, p)
                badgePaint.color = Theme.TEXT
                c.drawText(BANG, bx, by + badgePaint.textSize * 0.36f, badgePaint)
            }
            if (cheering) {
                p.style = Paint.Style.FILL
                p.color = Theme.GOLD
                Ui.drawCrown(c, x + hgt * 0.12f, bottom - 3f * k - lift - hgt * 1.02f, hgt * 0.36f, crownPath, p)
            }
        }
    }

    /** 띠 위쪽: 출발 신호등 3개 + 신호/시계 알약 */
    private fun drawHeader(c: Canvas) {
        val k = u * sc
        val cx = (bandL + bandR) / 2f
        val cy = bandTop + headerH / 2f + 1f * k
        val txt = pillText
        val tw = if (txt != null) pillPaint.measureText(txt) else pillPaint.measureText(clock, 0, clockLen)
        val s = 1f + 0.18f * pillPulse
        val hw = (tw / 2f + 14f * k).coerceAtLeast(52f * k) * s
        val hh = 11f * k * s
        val hot = clockWarn && txt == null
        val go = signal == 3 && txt != null
        rect.set(cx - hw, cy - hh, cx + hw, cy + hh)
        p.style = Paint.Style.FILL
        p.color = when {
            hot -> Theme.RED_DARK
            go -> Theme.GREEN_DARK
            signal == 1 || signal == 2 -> 0xFF5A1020.toInt()
            else -> Theme.BG_DEEP
        }
        c.drawRoundRect(rect, hh, hh, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * u
        p.color = when {
            hot -> Theme.RED
            go -> Theme.GREEN
            else -> Theme.GOLD
        }
        c.drawRoundRect(rect, hh, hh, p)
        c.save()
        c.scale(s, s, cx, cy)
        pillPaint.color = if (hot) Theme.GOLD_LIGHT else Theme.TEXT
        if (txt != null) c.drawText(txt, cx, cy + pillPaint.textSize * 0.36f, pillPaint)
        else c.drawText(clock, 0, clockLen, cx, cy + pillPaint.textSize * 0.36f, pillPaint)
        c.restore()
        // 신호등: 준비 = 빨강 1, 차렷 = 빨강 2, 땅 = 초록 3
        val lr = 5f * k
        val lx = cx - hw - 16f * k
        for (j in 0 until 3) {
            val x = lx - (2 - j) * 14f * k
            val green = signal == 3
            val lit = green || (signal >= 1 && j < signal)
            SprintArt.lamp(c, p, x, cy, lr, if (green) Theme.GREEN else Theme.RED, lit)
        }
        val rx = cx + hw + 16f * k
        for (j in 0 until 3) {
            val x = rx + j * 14f * k
            val green = signal == 3
            val lit = green || (signal >= 1 && (2 - j) < signal)
            SprintArt.lamp(c, p, x, cy, lr, if (green) Theme.GREEN else Theme.RED, lit)
        }
    }

    // ── 구역 조작부 ──

    private fun drawControls(c: Canvas, i: Int) {
        val k = u * sc
        val z = zones[i]
        val r = region[i]
        if (r.height() <= 0f) return
        c.save()
        c.rotate(z.rotation, r.centerX(), r.centerY())
        val st = shakeT[i]
        if (st in 0f..0.999f) c.translate(sin(st * PI.toFloat() * 6f) * (1f - st) * 9f * k, 0f)
        if (rank[i] > 0 && record[i] != null) {
            drawResultPlate(c, i, k)
        } else {
            val usable = live && !frozen[i]
            val col = if (usable) colors[i] else Theme.GRAY
            val next = if (usable) lastFoot[i] else null
            drawFootButton(c, i, btnL[i], col, flashL[i], next == Foot.RIGHT, true, k)
            drawFootButton(c, i, btnR[i], col, flashR[i], next == Foot.LEFT, false, k)
            drawColumn(c, i, k)
        }
        c.restore()
    }

    private fun drawFootButton(c: Canvas, i: Int, b: RectF, color: Int, flash: Float, glow: Boolean, left: Boolean, k: Float) {
        val down = if (flash > 0.4f) 1f else flash / 0.4f
        SprintArt.button(c, p, b, color, down, btnDepth, btnRadius, glow, 3f * k)
        val off = btnDepth * down
        val faceH = b.height() - btnDepth
        val iconS = min(faceH * 0.5f, b.width() * 0.42f)
        val textY = b.top + off + faceH - 10f * k
        val iconCy = b.top + off + (faceH - btnText.textSize - 8f * k) / 2f + 4f * k
        SprintArt.footprint(c, p, b.centerX(), iconCy, iconS, left, 0xE6FFFFFF.toInt())
        btnText.color = Theme.textOn(color)
        c.drawText(if (left) LEFT else RIGHT, b.centerX(), textY, btnText)
    }

    /** 가운데 칸: 이름 · 거리 · 상태 */
    private fun drawColumn(c: Canvas, i: Int, k: Float) {
        val x = colX[i]
        val y = colY[i]
        namePaint.textSize = nameSize[i]
        namePaint.color = colors[i]
        c.drawText(names[i] ?: "", x, y - 18f * k, namePaint)
        bigPaint.color = Theme.TEXT
        c.drawText(distText[i] ?: "", x, y + 6f * k, bigPaint)
        val m = msg[i]
        if (msgT[i] > 0f && m != null) {
            subPaint.color = msgColor[i]
            c.drawText(m, x, y + 26f * k, subPaint)
        } else {
            val s = status[i] ?: ""
            if (s.isNotEmpty()) {
                subPaint.color = statusColor[i]
                c.drawText(s, x, y + 26f * k, subPaint)
            }
        }
    }

    /** 도착(또는 끝): 버튼 자리에 순위·기록 판 */
    private fun drawResultPlate(c: Canvas, i: Int, k: Float) {
        val r = region[i]
        val bh = btnL[i].height()
        rect.set(r.left + 6f * k, r.bottom - bh, r.right - 6f * k, r.bottom)
        val gold = rank[i] == 1
        p.style = Paint.Style.FILL
        p.color = if (gold) 0xE65A4300.toInt() else 0xE60A1240.toInt()
        c.drawRoundRect(rect, 16f * k, 16f * k, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f * k
        p.color = if (gold) Theme.GOLD else colors[i]
        c.drawRoundRect(rect, 16f * k, 16f * k, p)
        val cy = rect.centerY()
        namePaint.textSize = nameSize[i]
        namePaint.color = colors[i]
        c.drawText(names[i] ?: "", rect.centerX(), cy - 20f * k, namePaint)
        bigPaint.color = if (gold) Theme.GOLD else Theme.TEXT
        val place = PLACE[rank[i].coerceIn(0, 4)]
        val gap = 8f * k
        val pw = bigPaint.measureText(place)
        recPaint.textSize = 17f * k
        val rec = record[i] ?: ""
        val rw = recPaint.measureText(rec)
        val x0 = rect.centerX() - (pw + gap + rw) / 2f
        bigPaint.textAlign = Paint.Align.LEFT
        bigPaint.textSize = 24f * k
        c.drawText(place, x0, cy + 12f * k, bigPaint)
        recPaint.color = if (gold) Theme.GOLD_LIGHT else Theme.TEXT
        c.drawText(rec, x0 + bigPaint.measureText(place) + gap, cy + 11f * k, recPaint)
        bigPaint.textAlign = Paint.Align.CENTER
        bigPaint.textSize = 20f * k
        recPaint.textSize = recSize()
    }

    /** 레인 오른쪽 기록 글자 크기 */
    private fun recSize(): Float = when (n) { 2 -> 14f; 3 -> 12.5f; else -> 11.5f } * u * sc

    companion object {
        private const val MAX = 4
        private const val IDLE_MS = 380f
        private const val LEFT = "왼발"
        private const val RIGHT = "오른발"
        private const val BANG = "!"
        private const val WAIT = "곧 출발"
        private val MARKS = Array(11) { if (it == 10) "100m" else (it * 10).toString() }
        private val DIST = Array(101) { "${it}m" }
        private val PLACE = arrayOf("", "1위", "2위", "3위", "4위")
        private val PLACE_NUM = arrayOf("", "1", "2", "3", "4")
    }
}
