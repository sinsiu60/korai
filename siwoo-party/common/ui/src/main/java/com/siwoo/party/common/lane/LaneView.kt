package com.siwoo.party.common.lane

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import com.siwoo.party.common.swipe.ZoneTouchInput
import com.siwoo.party.common.ui.Theme

/**
 * 모든 구역의 레인을 한 번에 그리는 투명 View — MultiTouchZoneView 와 같은 크기로 그 위에 겹친다.
 * 구역 배치는 MultiTouchZoneView 와 똑같이 TouchZones.layout(n, w, h, 10dp) 로 스스로 계산한다.
 * 구역마다 LaneDrawer 하나 (drawer(i) 로 판정선 위치·painter 등 조절).
 *
 * 게임 루프(postOnAnimation 등)에서 매 프레임 setNow(게임 시간) → 다시 그림. 이 View 는 스스로 반복해서 그리지 않는다.
 * 터치: 기본은 받지 않아(false) 아래 MultiTouchZoneView 로 그대로 내려간다 (리듬 = 누름만 필요).
 *  끌기(바구니·스와이프)가 필요하면 input = ZoneTouchInput(...) 을 넣는다 → 이 View 가 터치를 받아 input 에 주고,
 *  forwardTo(보통 MultiTouchZoneView) 가 있으면 같은 이벤트를 그쪽 onTouchEvent 에도 넘겨 눌림 표시·onZoneDown 도 유지.
 */
class LaneView(context: Context) : View(context) {

    private var drawers: Array<LaneDrawer> = emptyArray()
    private var tracks: Array<LaneTrack> = emptyArray()
    private var laneCount = 1
    private var maxLaneWidth = 0f
    /** 구역 배치 (크기가 정해진 뒤) */
    var zones: List<Zone> = emptyList()
        private set
    /** 그리는 기준 게임 시간 (ms) */
    var nowMs = 0L
        private set
    /** 끌기 입력 (선택) */
    var input: ZoneTouchInput? = null
        set(v) { field = v; v?.setZones(zones, inset) }
    /** 같은 터치를 함께 넘길 View (선택, 예: MultiTouchZoneView) */
    var forwardTo: View? = null
    /** 구역마다 그릴지 (탈락한 플레이어 등 false) */
    private var visibleZone = BooleanArray(0)
    private val inset = Theme.dpf(context, 8f)

    /**
     * tracks[i] = 구역 i(플레이어 i)의 진행, colors[i] = 플레이어 색, lanes = 구역당 레인 수.
     * 모든 track 이 같은 LanePattern 을 쓰게 만들 것 (공정).
     * maxLaneWidthDp > 0 이면 레인 폭을 그 이하로 (리듬 1레인: 110 정도 → 구역 가운데 세로 길).
     * labels = 구역 모서리 이름표 (레인 바탕이 아래 MultiTouchZoneView 이름을 가리므로 보통 플레이어 이름).
     */
    fun setup(tracks: Array<LaneTrack>, colors: IntArray, lanes: Int, maxLaneWidthDp: Float = 0f, labels: List<String>? = null) {
        require(tracks.size in 2..4 && colors.size >= tracks.size) { "구역 2~4개" }
        this.tracks = tracks
        laneCount = lanes
        maxLaneWidth = if (maxLaneWidthDp > 0f) Theme.dpf(context, maxLaneWidthDp) else 0f
        drawers = Array(tracks.size) { i -> LaneDrawer(context).also { it.color = colors[i]; it.label = labels?.getOrNull(i) } }
        visibleZone = BooleanArray(tracks.size) { true }
        relayout()
    }

    fun drawer(i: Int): LaneDrawer = drawers[i]
    fun track(i: Int): LaneTrack = tracks[i]

    /** 모든 구역의 물체 그리기 방법 */
    fun setPainter(p: LaneItemPainter?) { for (d in drawers) d.painter = p }

    /** 구역 i 를 그릴지 */
    fun setZoneVisible(i: Int, v: Boolean) { visibleZone[i] = v; invalidate() }

    /** 게임 시간 갱신 + 다시 그림 */
    fun setNow(ms: Long) {
        nowMs = ms
        invalidate()
    }

    /** drawer 의 judgeFrac 등을 바꾼 뒤 */
    fun relayout() {
        if (width == 0 || height == 0 || drawers.isEmpty()) return
        zones = TouchZones.layout(drawers.size, width.toFloat(), height.toFloat(), Theme.dpf(context, 10f))
        for (i in drawers.indices) drawers[i].setZone(zones[i], laneCount, inset, maxLaneWidth)
        input?.setZones(zones, inset)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = relayout()

    // ---------- 좌표 (화면 터치 → 구역·레인) ----------
    /** 화면 (x, y) 가 속한 구역 (없으면 -1) */
    fun zoneAt(x: Float, y: Float): Int {
        val z = TouchZones.zoneAt(zones, x, y)
        return if (z < drawers.size) z else -1
    }
    /** 구역 zone 안에서 화면 (x, y) 의 레인 번호 (회전 고려) */
    fun laneAt(zone: Int, x: Float, y: Float): Int = drawers[zone].laneAt(x, y)
    /** 구역 zone 안에서 화면 (x, y) 의 레인 단위 가로 위치 (바구니) */
    fun laneUnitsAt(zone: Int, x: Float, y: Float): Float = drawers[zone].laneUnitsAt(x, y)
    /** 구역 안 좌표 x(ZoneTouchInput 이 주는 lx) → 레인 단위 */
    fun laneUnitsLocal(zone: Int, lx: Float): Float = drawers[zone].layout.laneUnits(lx)

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val inp = input ?: return false
        inp.onTouchEvent(e)
        forwardTo?.onTouchEvent(e)
        return true
    }

    override fun onDraw(c: Canvas) {
        val n = minOf(drawers.size, zones.size)
        for (i in 0 until n) {
            if (visibleZone[i]) drawers[i].draw(c, tracks[i], nowMs)
        }
    }
}
