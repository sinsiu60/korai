package com.siwoo.party.common.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone

/**
 * 동시 터치 게임용 플레이어 구역.
 * 2인 좌/우, 3인 왼쪽 위·오른쪽 위·아래 가운데, 4인 네 귀퉁이 (common TouchZones).
 * 구역 테두리는 플레이어 색, 이름·안내 글자는 그 사람 쪽으로 돌려 쓴다(위쪽 구역 180°).
 * 손가락마다 어느 구역에서 눌렀는지 추적해 listener 에 알린다.
 */
class MultiTouchZoneView(context: Context) : View(context) {

    interface Listener {
        fun onZoneDown(zone: Int, x: Float, y: Float)
        fun onZoneUp(zone: Int) {}
    }

    var listener: Listener? = null
    private var players: List<PlayerInfo> = emptyList()
    private var zones: List<Zone> = emptyList()
    private val labels = ArrayList<String>()
    private val pressed = IntArray(MAX_POINTERS) // 구역별 눌린 손가락 수
    private val pointerZone = IntArray(MAX_POINTERS) { -1 }
    private var hint = ""

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }

    fun setPlayers(list: List<PlayerInfo>, hintText: String = "") {
        players = list
        hint = hintText
        labels.clear()
        for (p in list) labels.add(p.name)
        relayout()
    }

    /** 구역 안내 글자 바꾸기 (예: "준비!" / "지금!") */
    fun setHint(text: String) {
        hint = text
        invalidate()
    }

    fun zone(i: Int): Zone? = zones.getOrNull(i)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || players.isEmpty()) return
        zones = TouchZones.layout(players.size, width.toFloat(), height.toFloat(), Theme.dpf(context, 10f))
        border.strokeWidth = Theme.dpf(context, 4f)
        namePaint.textSize = Theme.dpf(context, 22f)
        hintPaint.textSize = Theme.dpf(context, 15f)
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
                    listener?.onZoneDown(z, e.getX(idx), e.getY(idx))
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
            listener?.onZoneUp(z)
            invalidate()
        }
    }

    override fun onDraw(c: Canvas) {
        val r = Theme.dpf(context, 18f)
        for (z in zones) {
            val color = Theme.playerColor(players[z.index].colorIndex)
            rect.set(z.left + border.strokeWidth, z.top + border.strokeWidth, z.right - border.strokeWidth, z.bottom - border.strokeWidth)
            fill.color = color
            fill.alpha = if (pressed[z.index] > 0) 110 else 40
            c.drawRoundRect(rect, r, r, fill)
            border.color = color
            c.drawRoundRect(rect, r, r, border)
            c.save()
            c.rotate(z.rotation, z.centerX, z.centerY)
            namePaint.color = color
            c.drawText(labels[z.index], z.centerX, z.centerY, namePaint)
            if (hint.isNotEmpty()) c.drawText(hint, z.centerX, z.centerY + namePaint.textSize * 1.3f, hintPaint)
            c.restore()
        }
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
