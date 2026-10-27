package com.siwoo.party.common.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone

/**
 * 동시 터치 게임용 플레이어 구역 (구역 번호 = 플레이어 순서).
 * 2인 좌/우, 3인 왼쪽 위·오른쪽 위·아래 가운데, 4인 네 귀퉁이 (common TouchZones).
 * - 구역 테두리는 플레이어 색, 이름·안내 글자는 그 사람 쪽으로 돌려 쓴다(위쪽 구역 180°).
 * - 손가락마다 어느 구역에서 눌렀는지 추적해 listener 에 알린다 (최대 10손가락, 동시 터치).
 * - 구역별: 아래 글자(setSub), 흐림+큰 X(setCrossed), 금색 빛(setWinner), 별 개수(setStars)
 */
class MultiTouchZoneView(context: Context) : View(context) {

    interface Listener {
        /** timeMs = 터치 순간 (SystemClock.uptimeMillis 기준) */
        fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long)
        fun onZoneUp(zone: Int) {}
    }

    var listener: Listener? = null
    /** false 면 터치를 받지만 알리지 않는다 (연출 중 등) */
    var touchEnabled = true
    private var players: List<PlayerInfo> = emptyList()
    private var zones: List<Zone> = emptyList()
    private val labels = ArrayList<String>()
    private var subs = arrayOfNulls<String>(0)
    private var crossed = BooleanArray(0)
    private var winner = BooleanArray(0)
    private var stars = IntArray(0)
    private val pressed = IntArray(MAX_POINTERS) // 구역별 눌린 손가락 수
    private val pointerZone = IntArray(MAX_POINTERS) { -1 }
    private var hint = ""

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val starPath = Path()

    fun setPlayers(list: List<PlayerInfo>, hintText: String = "") {
        players = list
        hint = hintText
        labels.clear()
        for (p in list) labels.add(p.name)
        subs = arrayOfNulls(list.size)
        crossed = BooleanArray(list.size)
        winner = BooleanArray(list.size)
        stars = IntArray(list.size)
        relayout()
    }

    /** 모든 구역 공통 안내 글자 (예: "대기") */
    fun setHint(text: String) {
        hint = text
        invalidate()
    }

    /** 구역별 아래 글자 (null 이면 공통 안내) */
    fun setSub(i: Int, text: String?) { subs[i] = text; invalidate() }

    /** 실격: 회색 + 큰 X */
    fun setCrossed(i: Int, v: Boolean) { crossed[i] = v; invalidate() }

    /** 승자: 금색 빛 */
    fun setWinner(i: Int, v: Boolean) { winner[i] = v; invalidate() }

    /** 구역 위쪽에 별 n개 */
    fun setStars(i: Int, n: Int) { stars[i] = n; invalidate() }

    /** 구역 표시 초기화 (글자·X·빛) — 별은 유지 */
    fun clearMarks() {
        for (i in subs.indices) { subs[i] = null; crossed[i] = false; winner[i] = false }
        invalidate()
    }

    fun zone(i: Int): Zone? = zones.getOrNull(i)

    /** 지금 눌려 있는 손가락이 있는 구역인지 */
    fun isPressed(i: Int): Boolean = pressed[i] > 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || players.isEmpty()) return
        zones = TouchZones.layout(players.size, width.toFloat(), height.toFloat(), Theme.dpf(context, 10f))
        border.strokeWidth = Theme.dpf(context, 4f)
        namePaint.textSize = Theme.dpf(context, 22f)
        hintPaint.textSize = Theme.dpf(context, 17f)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val id = e.getPointerId(idx)
                if (id >= MAX_POINTERS) return true
                val z = TouchZones.zoneAt(zones, e.getX(idx), e.getY(idx))
                pointerZone[id] = z
                if (z >= 0) {
                    pressed[z]++
                    if (touchEnabled) listener?.onZoneDown(z, e.getX(idx), e.getY(idx), e.eventTime)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(idx))
            MotionEvent.ACTION_CANCEL -> for (id in 0 until MAX_POINTERS) release(id)
        }
        return true
    }

    private fun release(id: Int) {
        if (id >= MAX_POINTERS) return
        val z = pointerZone[id]
        pointerZone[id] = -1
        if (z >= 0) {
            pressed[z] = (pressed[z] - 1).coerceAtLeast(0)
            if (touchEnabled) listener?.onZoneUp(z)
            invalidate()
        }
    }

    override fun onDraw(c: Canvas) {
        val r = Theme.dpf(context, 18f)
        for (z in zones) {
            val i = z.index
            val color = if (crossed[i]) Theme.GRAY else Theme.playerColor(players[i].colorIndex)
            rect.set(z.left + border.strokeWidth, z.top + border.strokeWidth, z.right - border.strokeWidth, z.bottom - border.strokeWidth)
            fill.color = color
            fill.alpha = when {
                crossed[i] -> 70
                pressed[i] > 0 -> 120
                else -> 40
            }
            c.drawRoundRect(rect, r, r, fill)
            if (winner[i]) {
                border.color = Theme.GOLD
                for (k in 3 downTo 1) {
                    border.alpha = 70 * (4 - k)
                    border.strokeWidth = Theme.dpf(context, 4f) * k
                    c.drawRoundRect(rect, r, r, border)
                }
                border.alpha = 255
                border.strokeWidth = Theme.dpf(context, 4f)
            } else {
                border.color = color
                c.drawRoundRect(rect, r, r, border)
            }
            c.save()
            c.rotate(z.rotation, z.centerX, z.centerY)
            namePaint.color = color
            c.drawText(labels[i], z.centerX, z.centerY, namePaint)
            val sub = subs[i] ?: hint
            if (sub.isNotEmpty()) {
                hintPaint.color = if (winner[i]) Theme.GOLD else Theme.TEXT_SUB
                c.drawText(sub, z.centerX, z.centerY + namePaint.textSize * 1.3f, hintPaint)
            }
            // 승점 별: 이름 위쪽에 한 줄
            val n = stars[i]
            if (n > 0) {
                val s = namePaint.textSize * 0.9f
                val x0 = z.centerX - (n - 1) * s * 0.6f
                for (k in 0 until n) drawStar(c, x0 + k * s * 1.2f, z.centerY - namePaint.textSize * 1.6f, s)
            }
            c.restore()
            if (crossed[i]) {
                // 큰 X
                val d = minOf(rect.width(), rect.height()) * 0.28f
                border.color = Theme.RED
                border.strokeWidth = Theme.dpf(context, 8f)
                c.drawLine(z.centerX - d, z.centerY - d, z.centerX + d, z.centerY + d, border)
                c.drawLine(z.centerX + d, z.centerY - d, z.centerX - d, z.centerY + d, border)
                border.strokeWidth = Theme.dpf(context, 4f)
            }
        }
    }

    private fun drawStar(c: Canvas, x: Float, y: Float, s: Float) {
        starPath.reset()
        for (k in 0 until 10) {
            val a = -Math.PI / 2 + k * Math.PI / 5
            val rad = if (k % 2 == 0) s / 2f else s / 4.6f
            val px = x + (rad * kotlin.math.cos(a)).toFloat()
            val py = y + (rad * kotlin.math.sin(a)).toFloat()
            if (k == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        fill.color = Theme.GOLD
        fill.alpha = 255
        c.drawPath(starPath, fill)
    }

    companion object {
        const val MAX_POINTERS = 10

        /** 기기가 동시에 인식할 수 있는 손가락 수 (기능 선언 기준) */
        fun supportedTouches(context: Context): Int {
            val pm = context.packageManager
            return when {
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> 5
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> 2
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> 2
                else -> 1
            }
        }
    }
}
