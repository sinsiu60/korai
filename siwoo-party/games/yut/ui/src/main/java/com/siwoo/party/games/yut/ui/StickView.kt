package com.siwoo.party.games.yut.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.yut.GameConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 화면 오른쪽 아래 멍석 위 윷가락 4개.
 * - 던질 수 있을 때: 멍석 테두리가 금색, 아래에 "터치해서 던지기" — 누르면 listener 로 알림
 * - 던지기 연출: 가락마다 조금씩 늦게 떠올라 공중에서 돌며(긴 축으로도 뒤집히며) 떨어지고, 한 번 튕긴 뒤 멈춘다
 *   → 멈춘 면이 결과(배/등). 끝나면 결과 글자를 크게 (윷·모는 금색)
 * 연출 시계는 GameTimers.now() — 일시정지 중에는 프레임 루프를 멈춘다. onDraw 에서는 객체를 만들지 않는다.
 */
class StickView(context: Context) : View(context) {

    interface Listener {
        fun onThrowTapped()
    }

    var listener: Listener? = null
    var timers: GameTimers? = null

    /** 던질 수 있는 상태 (테두리 금색 + 안내) */
    var throwable = false
        set(v) { if (field != v) { field = v; invalidate() } }

    /** 지금 보이는 면 (true = 배) */
    private val faces = BooleanArray(GameConfig.STICKS) { it % 2 == 0 }
    private val startFaces = BooleanArray(GameConfig.STICKS)
    private val halfTurns = IntArray(GameConfig.STICKS)
    private val spin = FloatArray(GameConfig.STICKS)
    private val restDeg = floatArrayOf(-7f, 4f, -3f, 8f)
    private val endDeg = FloatArray(GameConfig.STICKS) { restDeg[it] }
    private val startDeg = FloatArray(GameConfig.STICKS)
    private val drift = FloatArray(GameConfig.STICKS)
    private var tossStart = 0L
    private var tossDur = 0L
    private var tossCount = 0
    private var nak = false

    private var hint = ""
    private var resultText = ""
    private var resultColor = Theme.TEXT
    private var resultStart = 0L

    private val mat = RectF()
    private var stickLen = 0f
    private var stickW = 0f
    private val restX = FloatArray(GameConfig.STICKS)
    private var restY = 0f
    private var apexY = 0f
    private var hintY = 0f
    /** 멍석 결 (가로줄 y 좌표) */
    private var weave = FloatArray(0)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val resultOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = YutArt.INK
    }

    private var looping = false
    private val frame = Runnable { onFrame() }

    init {
        isClickable = true
        contentDescription = "윷가락"
    }

    private fun now(): Long = timers?.now() ?: SystemClock.uptimeMillis()

    /** 아래 안내 글자 (값이 바뀔 때만) */
    fun setHint(text: String) {
        if (hint == text) return
        hint = text
        fitHint()
        invalidate()
    }

    /** 지금 보이는 가락 면 (테스트·화면 확인용) */
    fun faceShown(i: Int): Boolean = faces[i]

    val resultShown: String get() = resultText

    /**
     * 던지기: flats 가 최종 면. 가락마다 다른 높이·회전으로 떠올랐다 떨어져 튕긴 뒤 멈춘다.
     * nak 이면 가락 하나가 멍석 밖으로 튕겨 나간 채 멈춘다.
     */
    fun toss(flats: BooleanArray, isNak: Boolean, durMs: Long) {
        tossCount++
        nak = isNak
        for (i in 0 until GameConfig.STICKS) {
            startFaces[i] = faces[i]
            faces[i] = flats[i]
            startDeg[i] = endDeg[i] % 360f
            // 긴 축으로 뒤집히는 반 바퀴 수: 시작 면과 끝 면이 다르면 홀수
            val base = 4 + (tossCount * 3 + i * 5) % 3
            halfTurns[i] = base
            if ((base % 2 == 1) != (startFaces[i] != flats[i])) halfTurns[i]++
            // 공중 회전: 반 바퀴 2~3번 + 조금 (끝나면 다시 세로로 눕도록 아래에서 맞춤)
            spin[i] = (if ((tossCount + i) % 2 == 0) 1f else -1f) * (360f + ((tossCount + i) % 2) * 180f) + ((tossCount * 37 + i * 71) % 40 - 20)
            drift[i] = ((tossCount * 17 + i * 23) % 9 - 4) * 0.012f
            var target = restDeg[i] + ((tossCount * 13 + i * 29) % 15 - 7)
            if (isNak && i == 3) {
                // 마지막 가락이 멍석 밖으로
                target = 70f
                drift[i] = 0.36f
            }
            val land = startDeg[i] + spin[i]
            endDeg[i] = target + Math.round((land - target) / 180f) * 180f
        }
        resultText = ""
        tossStart = now()
        tossDur = durMs.coerceAtLeast(1)
        ensureRunning()
    }

    /** 결과 글자를 크게 (톡 튀어나옴) */
    fun showResult(text: String, color: Int) {
        resultText = text
        resultColor = color
        resultStart = now()
        fitResult()
        ensureRunning()
    }

    fun clearResult() {
        if (resultText.isEmpty()) return
        resultText = ""
        invalidate()
    }

    /** 던지는 중 */
    val isTossing: Boolean get() = tossDur > 0 && now() - tossStart < tossDur

    val isAnimating: Boolean get() = looping

    private fun ensureRunning() {
        invalidate()
        if (looping || timers?.paused == true) return
        looping = true
        postOnAnimation(frame)
    }

    private fun onFrame() {
        if (!looping) return
        if (timers?.paused == true) { looping = false; return }
        invalidate()
        val t = now()
        if (t - tossStart < tossDur || (resultText.isNotEmpty() && t - resultStart < POP_MS)) postOnAnimation(frame) else looping = false
    }

    fun pauseAnims() {
        looping = false
        removeCallbacks(frame)
    }

    fun resumeAnims() {
        val t = now()
        if (t - tossStart < tossDur || (resultText.isNotEmpty() && t - resultStart < POP_MS)) ensureRunning()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val m = Theme.dpf(context, 2f)
        mat.set(m, m, w - m, h - m)
        val hintBand = Theme.dpf(context, 24f)
        hintY = mat.bottom - hintBand * 0.36f
        stickLen = minOf((mat.height() - hintBand) * 0.5f, mat.width() * 0.8f)
        stickW = minOf(stickLen * 0.19f, mat.width() * 0.13f)
        for (i in 0 until GameConfig.STICKS) restX[i] = mat.left + mat.width() * (0.2f + 0.2f * i)
        restY = mat.bottom - hintBand - stickLen * 0.5f - Theme.dpf(context, 6f)
        apexY = mat.top + stickLen * 0.5f
        val n = ((mat.height() - hintBand) / Theme.dpf(context, 9f)).toInt().coerceAtLeast(1)
        weave = FloatArray(n * 4)
        for (k in 0 until n) {
            val y = mat.top + Theme.dpf(context, 9f) * (k + 0.5f)
            weave[k * 4] = mat.left + m * 3
            weave[k * 4 + 1] = y
            weave[k * 4 + 2] = mat.right - m * 3
            weave[k * 4 + 3] = y
        }
        hintPaint.textSize = Theme.dpf(context, 12f)
        fitHint()
        fitResult()
    }

    private fun fitHint() {
        if (mat.width() <= 0f) return
        var s = Theme.dpf(context, 12f)
        hintPaint.textSize = s
        while (hintPaint.measureText(hint) > mat.width() * 0.92f && s > Theme.dpf(context, 8f)) {
            s *= 0.92f
            hintPaint.textSize = s
        }
    }

    private fun fitResult() {
        if (mat.width() <= 0f) return
        var s = minOf(mat.width() * 0.36f, Theme.dpf(context, 46f))
        resultPaint.textSize = s
        while (resultPaint.measureText(resultText) > mat.width() * 0.9f && s > Theme.dpf(context, 14f)) {
            s *= 0.9f
            resultPaint.textSize = s
        }
        resultOutline.textSize = s
        resultOutline.strokeWidth = s * 0.16f
    }

    override fun onDraw(c: Canvas) {
        if (mat.width() <= 0f) return
        val t = now()
        val r = Theme.dpf(context, 14f)
        // 멍석
        fill.style = Paint.Style.FILL
        fill.color = YutArt.MAT_DARK
        c.drawRoundRect(mat, r, r, fill)
        fill.color = YutArt.MAT
        val e = Theme.dpf(context, 4f)
        c.drawRoundRect(mat.left + e, mat.top + e, mat.right - e, mat.bottom - e, r * 0.75f, r * 0.75f, fill)
        stroke.color = YutArt.MAT_LINE
        stroke.strokeWidth = Theme.dpf(context, 1f)
        c.drawLines(weave, stroke)
        // 안내 띠
        fill.color = 0x66000000
        val hb = Theme.dpf(context, 24f)
        c.drawRoundRect(mat.left + e, mat.bottom - hb, mat.right - e, mat.bottom - e, r * 0.6f, r * 0.6f, fill)
        if (throwable) {
            stroke.color = Theme.GOLD
            stroke.strokeWidth = Theme.dpf(context, 3f)
            c.drawRoundRect(mat, r, r, stroke)
        }
        if (hint.isNotEmpty()) {
            hintPaint.color = if (throwable) Theme.GOLD_LIGHT else Theme.TEXT_SUB
            c.drawText(hint, mat.centerX(), hintY, hintPaint)
        }

        // 윷가락
        val e2 = t - tossStart
        val tossing = tossDur > 0 && e2 < tossDur
        for (i in 0 until GameConfig.STICKS) {
            if (!tossing) {
                val dx = drift[i] * mat.width()
                drawStickAt(c, i, restX[i] + dx, restY, endDeg[i], 1f, faces[i])
                continue
            }
            // 가락마다 조금씩 늦게 출발
            val delay = i * 0.05f
            val f = ((e2.toFloat() / tossDur - delay) / (1f - 0.15f)).coerceIn(0f, 1f)
            val y: Float
            val deg: Float
            val face: Boolean
            val squash: Float
            if (f < LAND) {
                // 떠올랐다 떨어짐 (포물선) + 회전 + 긴 축 뒤집기
                val u = f / LAND
                y = restY - (restY - apexY) * 4f * u * (1f - u)
                val ease = 1f - (1f - u) * (1f - u)
                deg = startDeg[i] + spin[i] * ease
                val flip = halfTurns[i] * PI.toFloat() * ease
                val cs = cos(flip)
                squash = abs(cs)
                face = if (cs >= 0f) startFaces[i] else !startFaces[i]
            } else {
                // 한 번 튕기고 멈춤
                val v = (f - LAND) / (1f - LAND)
                val b = if (v < 0.6f) 4f * (v / 0.6f) * (1f - v / 0.6f) else 0f
                y = restY - (restY - apexY) * 0.14f * b
                deg = startDeg[i] + spin[i] + (endDeg[i] - startDeg[i] - spin[i]) * (1f - (1f - v) * (1f - v))
                face = faces[i]
                squash = 1f
            }
            // 공중에서는 멍석 가운데 쪽으로 모였다가 제자리로 (가장자리에서 잘리지 않게)
            val air = if (f < LAND) sin(f / LAND * PI.toFloat()) else 0f
            val midX = mat.centerX() + (i - 1.5f) * mat.width() * 0.07f
            val dx = drift[i] * mat.width() * f
            drawStickAt(c, i, restX[i] + (midX - restX[i]) * air * 0.85f + dx, y, deg, squash, face)
        }

        // 결과 글자
        if (resultText.isNotEmpty() && !tossing) {
            val pf = ((t - resultStart).toFloat() / POP_MS).coerceIn(0f, 1f)
            val s = if (pf < 1f) 0.4f + 0.6f * pf + 0.3f * sin(pf * PI.toFloat()) else 1f
            val x = mat.centerX()
            val y = mat.top + (restY - stickLen * 0.5f - mat.top) * 0.5f + resultPaint.textSize * 0.36f
            c.save()
            c.scale(s, s, x, y)
            resultPaint.color = resultColor
            c.drawText(resultText, x, y, resultOutline)
            c.drawText(resultText, x, y, resultPaint)
            c.restore()
        }
    }

    private fun drawStickAt(c: Canvas, i: Int, x: Float, y: Float, deg: Float, squash: Float, flat: Boolean) {
        YutArt.drawStick(c, x, y, stickLen, stickW, deg, squash, flat, i == GameConfig.BACKDO_STICK, fill, markPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
            if (throwable) listener?.onThrowTapped()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        /** 떨어져 닿는 시점 (던지기 진행 비율) */
        private const val LAND = 0.72f
        /** 결과 글자 튀어나오는 시간 (실시간 연출, 짧음) */
        private const val POP_MS = 320L
    }
}
