package com.siwoo.party.games.drawing

/**
 * 그림 선 목록 (순수 Kotlin, 할당 없음): 모든 점은 처음 만든 고정 크기 배열 하나(points)에 이어서 저장한다.
 * 선 i = points[start(i) * 2 ..] 에 (x, y) 가 len(i) 개, 색 번호 color(i), 굵기 번호 width(i).
 * 좌표 단위는 화면이 정한다 (보통 캔버스 높이로 나눈 값 → x 0..가로/세로비, y 0..1).
 *
 * 한도
 * - 선 수 maxStrokes / 점 합계 maxTotalPoints 를 넘으면 가장 오래된 선부터 "굳힌다" (onBake 로 알려 준 뒤 목록에서 뺌 → 되돌리기 불가).
 *   화면은 굳힌 선을 바탕 비트맵에 그려 둔다.
 * - 선 하나가 maxPointsPerStroke 점을 넘으면 그 선을 끝내고 마지막 점에서 새 선을 이어 시작한다 (add 가 SPLIT).
 * - 이전 점과 minDist 보다 가까운 점은 버린다 (add 가 SKIPPED).
 */
class StrokeModel(
    val maxStrokes: Int = GameConfig.MAX_STROKES,
    val maxPointsPerStroke: Int = GameConfig.MAX_POINTS_PER_STROKE,
    val maxTotalPoints: Int = GameConfig.MAX_TOTAL_POINTS,
    private val minDist: Float = GameConfig.MIN_POINT_DIST,
) {
    /** 가장 오래된 선(번호 0)을 굳히기 직전에 불린다 */
    fun interface BakeListener { fun onBake(model: StrokeModel, index: Int) }

    var onBake: BakeListener? = null

    /** 점 좌표 (x0, y0, x1, y1, …) — 고정 크기 */
    val points = FloatArray(maxTotalPoints * 2)
    private val starts = IntArray(maxStrokes)
    private val lens = IntArray(maxStrokes)
    private val colors = IntArray(maxStrokes)
    private val widths = IntArray(maxStrokes)

    /** 기억하는 선 수 (그리는 중인 선 포함) */
    var strokeCount = 0
        private set
    /** 모든 선의 점 합계 */
    var totalPoints = 0
        private set
    /** 지금까지 굳힌 선 수 (지우기 전까지) */
    var bakedCount = 0
        private set
    /** 손가락이 닿아 선을 그리는 중 */
    var drawing = false
        private set
    /** 지금까지 받은 점 (버린 점 포함, 확인용) */
    var receivedPoints = 0L
        private set

    fun start(i: Int): Int = starts[i]
    fun len(i: Int): Int = lens[i]
    fun color(i: Int): Int = colors[i]
    fun width(i: Int): Int = widths[i]
    fun x(i: Int, k: Int): Float = points[(starts[i] + k) * 2]
    fun y(i: Int, k: Int): Float = points[(starts[i] + k) * 2 + 1]

    /** 그리는 중인 선 번호 (아니면 -1) */
    val current: Int get() = if (drawing) strokeCount - 1 else -1

    /** 되돌릴 선이 있는지 */
    val canUndo: Boolean get() = !drawing && strokeCount > 0

    /** 그린 것이 있는지 (굳힌 것 포함) */
    val isEmpty: Boolean get() = strokeCount == 0 && bakedCount == 0

    /** 새 선 시작 */
    fun begin(x: Float, y: Float, color: Int, width: Int) {
        if (drawing) end()
        if (strokeCount >= maxStrokes) bakeOldest()
        // 새 점 2개(이어 그릴 때 시작점 + 새 점) 자리를 늘 남겨 둔다
        while (totalPoints + 2 > maxTotalPoints && strokeCount > 0) bakeOldest()
        val i = strokeCount
        starts[i] = totalPoints
        lens[i] = 0
        colors[i] = color
        widths[i] = width
        strokeCount++
        drawing = true
        put(x, y)
        receivedPoints++
    }

    /** 점 추가 결과 */
    companion object {
        const val ADDED = 0
        const val SKIPPED = 1
        /** 앞 선이 꽉 차서 끝내고, 마지막 점에서 새 선을 이어 시작한 뒤 이 점을 넣음 */
        const val SPLIT = 2
        const val NOT_DRAWING = 3
    }

    fun add(x: Float, y: Float): Int {
        if (!drawing) return NOT_DRAWING
        receivedPoints++
        val i = strokeCount - 1
        val n = lens[i]
        val lx = points[(starts[i] + n - 1) * 2]
        val ly = points[(starts[i] + n - 1) * 2 + 1]
        val dx = x - lx
        val dy = y - ly
        if (dx * dx + dy * dy < minDist * minDist) return SKIPPED
        if (n >= maxPointsPerStroke || totalPoints + 1 > maxTotalPoints) {
            // 이 선은 여기서 끝, 마지막 점에서 이어서 새 선
            val c = colors[i]
            val w = widths[i]
            drawing = false
            begin(lx, ly, c, w)
            receivedPoints--
            put(x, y)
            return SPLIT
        }
        put(x, y)
        return ADDED
    }

    /** 손가락 뗌 → 선 완성 */
    fun end() {
        drawing = false
    }

    /** 마지막 선 지우기 (굳힌 선은 못 지움) */
    fun undo(): Boolean {
        if (!canUndo) return false
        strokeCount--
        totalPoints = starts[strokeCount]
        return true
    }

    /** 전부 지우기 */
    fun clear() {
        strokeCount = 0
        totalPoints = 0
        bakedCount = 0
        drawing = false
    }

    private fun put(x: Float, y: Float) {
        val i = strokeCount - 1
        val p = totalPoints * 2
        points[p] = x
        points[p + 1] = y
        lens[i]++
        totalPoints++
    }

    /** 가장 오래된 선을 굳힘: 알려 주고 목록에서 뺀 뒤 나머지를 앞으로 당긴다 (배열 복사, 할당 없음) */
    private fun bakeOldest() {
        if (strokeCount == 0) return
        onBake?.onBake(this, 0)
        val n0 = lens[0]
        System.arraycopy(points, n0 * 2, points, 0, (totalPoints - n0) * 2)
        for (k in 1 until strokeCount) {
            starts[k - 1] = starts[k] - n0
            lens[k - 1] = lens[k]
            colors[k - 1] = colors[k]
            widths[k - 1] = widths[k]
        }
        strokeCount--
        totalPoints -= n0
        bakedCount++
    }
}
