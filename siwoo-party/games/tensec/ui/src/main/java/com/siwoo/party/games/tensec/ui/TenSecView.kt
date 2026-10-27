package com.siwoo.party.games.tensec.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.Theme

/**
 * 딱 10초 판: MultiTouchZoneView 위에 겹쳐, 구역마다 똑같은 배치(이름·목표·시계 패널·안내·합계)를
 * 그 구역 방향(위쪽 구역 180°)으로 돌려 그린다. 터치는 받지 않는다(아래 구역으로 그대로).
 * 시계·굴러가는 기록은 화면 프레임 루프가 tick(게임 시계)로 갱신한다 — onDraw 할당 없음.
 */
class TenSecView(context: Context, private val zones: MultiTouchZoneView) : View(context) {

    /** 구역 패널 표시 */
    enum class Show { TARGET, CLOCK, HIDDEN, TAPPED, WAIT, ROLL, LANDED }

    private var n = 0
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private val show = Array(MAX) { Show.TARGET }
    private val main = Array(MAX) { CharArray(16) }
    private val mainLen = IntArray(MAX)
    private val bottom = arrayOfNulls<String>(MAX)
    private val total = arrayOfNulls<String>(MAX)
    private val best = BooleanArray(MAX)
    private var targetLabel = ""

    private var clockStart = -1L
    private var visibleMs = 3000L
    private val rollStart = LongArray(MAX)
    private val rollDur = LongArray(MAX)
    private val rollTo = LongArray(MAX)
    private val landAt = LongArray(MAX) { -1 }
    private val pop = FloatArray(MAX)
    /** 기록이 멈출 때 톡 커졌다 돌아오는 시간 (화면이 속도 배율을 곱해 넣는다) */
    var popMs = 260L

    private val rect = RectF()
    private val shadow = RectF()
    private val check = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Theme.GOLD }
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    fun setPlayers(list: Array<String>, playerColors: IntArray) {
        n = list.size
        names = list
        colors = playerColors
        invalidate()
    }

    /** 지금 패널 상태 (테스트·화면용) */
    fun state(i: Int): Show = show[i]

    /** 이번 라운드 목표 공개: 모든 패널에 "7초" */
    fun showTarget(sec: Int) {
        targetLabel = "목표 ${sec}초"
        val s = "${sec}초"
        clockStart = -1
        for (i in 0 until n) {
            show[i] = Show.TARGET
            mainLen[i] = copy(s, main[i])
            bottom[i] = "이 시간에 딱 맞춰 터치!"
            best[i] = false
            landAt[i] = -1
            pop[i] = 0f
        }
        invalidate()
    }

    /** 타이머 시작 (now = 게임 시계). visible 동안만 숫자가 보인다 */
    fun startClock(now: Long, visible: Long) {
        clockStart = now
        visibleMs = visible
        for (i in 0 until n) {
            show[i] = Show.CLOCK
            mainLen[i] = fmtCs(0, main[i], false)
            bottom[i] = "${visible / 1000}초 뒤 타이머가 사라져요"
        }
        invalidate()
    }

    /** 이 구역은 터치 완료 */
    fun setTapped(i: Int, autoRecorded: Boolean = false) {
        show[i] = Show.TAPPED
        bottom[i] = if (autoRecorded) "시간 초과" else "터치 완료!"
        invalidate()
    }

    /** 전원 입력 끝 → 공개 대기 */
    fun setWaiting() {
        clockStart = -1
        for (i in 0 until n) {
            show[i] = Show.WAIT
            bottom[i] = "기록 공개 대기"
        }
        invalidate()
    }

    /** 기록 굴리기 시작: 0 → recordCs 로 숫자가 올라가다 land 에서 멈춘다 */
    fun roll(i: Int, recordCs: Long, now: Long, durMs: Long) {
        show[i] = Show.ROLL
        rollStart[i] = now
        rollDur[i] = durMs.coerceAtLeast(1)
        rollTo[i] = recordCs
        mainLen[i] = fmtCs(0, main[i], true)
        bottom[i] = "두구두구…"
        invalidate()
    }

    /** 기록 확정: 기록·차이·합계 */
    fun land(i: Int, recordCs: Long, diffText: String, totalText: String, now: Long) {
        show[i] = Show.LANDED
        mainLen[i] = fmtCs(recordCs, main[i], true)
        bottom[i] = diffText
        total[i] = totalText
        landAt[i] = now
        invalidate()
    }

    /** 가장 가까운 사람 (금색) */
    fun setBest(i: Int, v: Boolean) {
        best[i] = v
        invalidate()
    }

    /** 시계·굴리기·톡 연출이 진행 중인지 (프레임 루프가 필요한지) */
    val busy: Boolean
        get() {
            if (clockStart >= 0) return true
            for (i in 0 until n) if (show[i] == Show.ROLL || landAt[i] >= 0) return true
            return false
        }

    /** 프레임마다 (now = 게임 시계). 아직 움직이는 게 있으면 true */
    fun tick(now: Long): Boolean {
        var moving = false
        if (clockStart >= 0) {
            val e = now - clockStart
            var anyClock = false
            for (i in 0 until n) if (show[i] == Show.CLOCK) {
                if (e >= visibleMs) {
                    show[i] = Show.HIDDEN
                    bottom[i] = "목표라고 생각할 때 터치!"
                } else {
                    mainLen[i] = fmtCs(e / 10, main[i], false)
                    anyClock = true
                }
            }
            if (anyClock) moving = true else clockStart = -1
        }
        for (i in 0 until n) {
            if (show[i] == Show.ROLL) {
                val f = ((now - rollStart[i]).toFloat() / rollDur[i]).coerceIn(0f, 1f)
                val g = 1f - (1f - f) * (1f - f) * (1f - f)
                mainLen[i] = fmtCs((rollTo[i] * g).toLong(), main[i], true)
                moving = true
            }
            if (landAt[i] >= 0) {
                val f = (now - landAt[i]).toFloat() / popMs.coerceAtLeast(1)
                if (f >= 1f) {
                    pop[i] = 0f
                    landAt[i] = -1
                } else {
                    pop[i] = if (f < 0.35f) f / 0.35f else (1f - f) / 0.65f
                    moving = true
                }
            }
        }
        invalidate()
        return moving
    }

    override fun onDraw(c: Canvas) {
        for (i in 0 until n) {
            val z = zones.zone(i) ?: continue
            c.save()
            c.rotate(z.rotation, z.centerX, z.centerY)
            drawZone(c, i, z.centerX, z.centerY, z.width, z.height)
            c.restore()
        }
    }

    /** 구역 하나 (회전된 좌표, 가운데 cx·cy 기준) */
    private fun drawZone(c: Canvas, i: Int, cx: Float, cy: Float, w: Float, h: Float) {
        val b = minOf(h, w * 0.68f)
        val pw = minOf(w * 0.72f, b * 1.3f)
        val ph = b * 0.42f
        val pcy = cy - b * 0.03f
        rect.set(cx - pw / 2f, pcy - ph / 2f, cx + pw / 2f, pcy + ph / 2f)
        val r = b * 0.07f

        // 이름 (왼쪽) · 목표 (오른쪽)
        namePaint.textSize = b * 0.1f
        namePaint.color = colors[i]
        c.drawText(names[i], rect.left + b * 0.02f, rect.top - b * 0.045f, namePaint)
        if (targetLabel.isNotEmpty()) {
            targetPaint.textSize = b * 0.075f
            c.drawText(targetLabel, rect.right - b * 0.02f, rect.top - b * 0.045f, targetPaint)
        }

        // 패널 (그림자 + 어두운 화면 + 테두리)
        val s = 1f + 0.12f * pop[i]
        c.save()
        c.scale(s, s, cx, pcy)
        shadow.set(rect)
        shadow.offset(b * 0.012f, b * 0.022f)
        fill.color = 0x66000000
        c.drawRoundRect(shadow, r, r, fill)
        fill.color = if (best[i]) PANEL_BEST else PANEL
        c.drawRoundRect(rect, r, r, fill)
        stroke.strokeWidth = b * (if (best[i]) 0.022f else 0.014f)
        stroke.color = if (best[i]) Theme.GOLD else colors[i]
        c.drawRoundRect(rect, r, r, stroke)

        val st = show[i]
        when (st) {
            Show.HIDDEN, Show.WAIT -> {
                mainPaint.textSize = b * 0.22f
                mainPaint.color = if (st == Show.HIDDEN) Theme.TEXT_SUB else Theme.GRAY
                c.drawText(QUESTION, cx, pcy + mainPaint.textSize * 0.36f, mainPaint)
            }
            Show.TAPPED -> {
                val d = ph * 0.26f
                check.reset()
                check.moveTo(cx - d * 1.1f, pcy)
                check.lineTo(cx - d * 0.25f, pcy + d * 0.8f)
                check.lineTo(cx + d * 1.2f, pcy - d * 0.75f)
                stroke.strokeWidth = b * 0.05f
                stroke.color = Theme.GOLD
                c.drawPath(check, stroke)
            }
            else -> {
                mainPaint.textSize = b * 0.22f
                mainPaint.color = when {
                    st == Show.TARGET -> Theme.GOLD
                    st == Show.CLOCK -> LCD
                    best[i] -> Theme.GOLD_LIGHT
                    else -> Theme.TEXT
                }
                c.drawText(main[i], 0, mainLen[i], cx, pcy + mainPaint.textSize * 0.36f, mainPaint)
            }
        }
        c.restore()

        // 안내 / 차이
        val sub = bottom[i]
        if (sub != null) {
            subPaint.textSize = b * 0.085f
            subPaint.color = when {
                best[i] -> Theme.GOLD
                st == Show.LANDED -> Theme.TEXT
                else -> Theme.TEXT_SUB
            }
            c.drawText(sub, cx, cy + b * 0.3f, subPaint)
        }
        val tot = total[i]
        if (tot != null && st == Show.LANDED) {
            totalPaint.textSize = b * 0.065f
            c.drawText(tot, cx, cy + b * 0.41f, totalPaint)
        }
    }

    companion object {
        private const val MAX = 4
        private const val PANEL = 0xFF0B1238.toInt()
        private const val PANEL_BEST = 0xFF3A2A08.toInt()
        private const val LCD = 0xFF7CFFB2.toInt()
        private const val QUESTION = "? ? ?"

        /** 문자열을 out 에 복사 (setter 에서만) */
        private fun copy(s: String, out: CharArray): Int {
            val len = minOf(s.length, out.size)
            for (k in 0 until len) out[k] = s[k]
            return len
        }

        /** 0.01초 단위 cs 를 "12.34"(+ "초") 로 out 에 쓴다 (할당 없음). 글자 수 반환 */
        fun fmtCs(cs: Long, out: CharArray, unit: Boolean): Int {
            val v = cs.coerceAtLeast(0)
            var sec = v / 100
            val frac = (v % 100).toInt()
            var digits = 1
            var t = sec
            while (t >= 10) { t /= 10; digits++ }
            var k = digits - 1
            while (k >= 0) {
                out[k] = ('0' + (sec % 10).toInt())
                sec /= 10
                k--
            }
            var p = digits
            out[p++] = '.'
            out[p++] = '0' + frac / 10
            out[p++] = '0' + frac % 10
            if (unit) out[p++] = '초'
            return p
        }
    }
}
