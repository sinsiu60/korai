package com.siwoo.party.common.swipe

import android.view.MotionEvent
import com.siwoo.party.common.Zone
import com.siwoo.party.common.lane.ZoneFrame

/**
 * 동시 터치 구역의 "끌기" 입력 (MultiTouchZoneView 는 누름/뗌만 알려 주므로 스와이프·바구니 끌기용).
 * 구역마다 손가락 하나만 주인: 그 구역에 처음 닿은 손가락이 뗄 때까지 그 구역을 움직인다 (구역 밖으로 나가도 계속).
 * 좌표는 구역 안 좌표(ZoneFrame — 위쪽 구역 180° 를 풀어 그 플레이어가 보는 방향, 위 = 앞)로 바꿔 알린다.
 * 구역마다 SwipeTracker 를 자동으로 채운다 (move 의 중간 기록(historical)까지 넣음) → 뗄 때 tracker(zone).analyze(...).
 *
 * 쓰는 법 (게임의 겹친 View 의 onTouchEvent 에서):
 *   val input = ZoneTouchInput(4).apply { listener = ... }
 *   onSizeChanged → input.setZones(TouchZones.layout(n, w, h, gap))  (또는 LaneView.input 으로 자동)
 *   override fun onTouchEvent(e: MotionEvent) = input.onTouchEvent(e)
 * 시간은 MotionEvent.eventTime (uptime) — 스와이프 속도 계산에는 차이만 쓰므로 괜찮다.
 */
class ZoneTouchInput(val maxZones: Int = 4, sampleCapacity: Int = 64) {

    interface Listener {
        /** 구역 zone 에 손가락이 닿음 (lx, ly = 구역 안 좌표) */
        fun onDown(zone: Int, lx: Float, ly: Float, timeMs: Long) {}
        /** 움직임 (중간 기록은 tracker 에만 들어가고 알림은 이벤트마다 한 번) */
        fun onMove(zone: Int, lx: Float, ly: Float, timeMs: Long) {}
        /** 손가락을 뗌 (canceled = 시스템 취소). tracker(zone) 에 경로가 남아 있다 */
        fun onUp(zone: Int, lx: Float, ly: Float, timeMs: Long, canceled: Boolean) {}
    }

    var listener: Listener? = null
    /** false 면 받기만 하고 알리지 않는다 (연출 중) — 진행 중이던 손가락도 끊는다 */
    var enabled = true
        set(v) { field = v; if (!v) for (z in 0 until maxZones) owner[z] = -1 }

    private var zones: List<Zone> = emptyList()
    private val frames = Array(maxZones) { ZoneFrame() }
    private val trackers = Array(maxZones) { SwipeTracker(sampleCapacity) }
    /** 구역별 주인 손가락 id (-1 = 없음) */
    private val owner = IntArray(maxZones) { -1 }

    /** 구역 배치 (MultiTouchZoneView 와 같은 TouchZones.layout 결과) */
    fun setZones(list: List<Zone>, inset: Float = 0f) {
        zones = list
        for (z in list) if (z.index < maxZones) frames[z.index].set(z, inset)
    }

    fun frame(zone: Int): ZoneFrame = frames[zone]
    fun tracker(zone: Int): SwipeTracker = trackers[zone]
    /** 지금 손가락이 닿아 끌고 있는 구역인지 */
    fun isDown(zone: Int): Boolean = owner[zone] >= 0

    /** 모든 손가락을 놓은 것으로 (라운드 바뀔 때 등, 알림 없음) */
    fun cancelAll() { for (z in 0 until maxZones) { owner[z] = -1; trackers[z].reset() } }

    fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                val x = e.getX(idx); val y = e.getY(idx)
                val z = zoneAt(x, y)
                if (z >= 0 && owner[z] < 0 && enabled) {
                    owner[z] = e.getPointerId(idx)
                    val f = frames[z]
                    val lx = f.toLocalX(x, y); val ly = f.toLocalY(x, y)
                    trackers[z].begin(lx, ly, e.eventTime)
                    listener?.onDown(z, lx, ly, e.eventTime)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (z in 0 until maxZones) {
                    val id = owner[z]
                    if (id < 0) continue
                    val pi = e.findPointerIndex(id)
                    if (pi < 0) continue
                    val f = frames[z]
                    val tr = trackers[z]
                    for (hh in 0 until e.historySize) {
                        val hx = e.getHistoricalX(pi, hh); val hy = e.getHistoricalY(pi, hh)
                        tr.add(f.toLocalX(hx, hy), f.toLocalY(hx, hy), e.getHistoricalEventTime(hh))
                    }
                    val x = e.getX(pi); val y = e.getY(pi)
                    val lx = f.toLocalX(x, y); val ly = f.toLocalY(x, y)
                    tr.add(lx, ly, e.eventTime)
                    listener?.onMove(z, lx, ly, e.eventTime)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = e.actionIndex
                release(e.getPointerId(idx), e.getX(idx), e.getY(idx), e.eventTime, false)
            }
            MotionEvent.ACTION_CANCEL -> {
                for (z in 0 until maxZones) {
                    val id = owner[z]
                    if (id < 0) continue
                    val pi = e.findPointerIndex(id)
                    val f = frames[z]
                    val x = if (pi >= 0) e.getX(pi) else f.toScreenX(trackers[z].endX, trackers[z].endY)
                    val y = if (pi >= 0) e.getY(pi) else f.toScreenY(trackers[z].endX, trackers[z].endY)
                    release(id, x, y, e.eventTime, true)
                }
            }
        }
        return true
    }

    private fun release(id: Int, x: Float, y: Float, t: Long, canceled: Boolean) {
        for (z in 0 until maxZones) {
            if (owner[z] != id) continue
            owner[z] = -1
            val f = frames[z]
            val lx = f.toLocalX(x, y); val ly = f.toLocalY(x, y)
            trackers[z].add(lx, ly, t)
            listener?.onUp(z, lx, ly, t, canceled)
        }
    }

    private fun zoneAt(x: Float, y: Float): Int {
        for (z in zones) if (z.index < maxZones && z.contains(x, y)) return z.index
        return -1
    }
}
