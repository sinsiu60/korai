package com.siwoo.party.games.polyblock

/**
 * 조각 하나의 한 방향 모양. 칸 좌표는 왼쪽 위가 (0,0) 이 되도록 맞춘 것.
 * xs[k], ys[k] = k 번째 칸, w·h = 차지하는 상자 크기.
 */
class Orient(val xs: IntArray, val ys: IntArray) {
    val size: Int get() = xs.size
    val w: Int = (xs.maxOrNull() ?: 0) + 1
    val h: Int = (ys.maxOrNull() ?: 0) + 1

    /** 칸 정렬 키 (같은 모양 판별) */
    internal val key: Long = run {
        var bits = 0L
        for (k in xs.indices) bits = bits or (1L shl (ys[k] * 5 + xs[k]))
        bits
    }
}

/** 조각 종류 (21개). orients = 돌리기·뒤집기로 나오는 서로 다른 모양들 */
class Shape(val id: Int, val name: String, val orients: List<Orient>, private val byTransform: IntArray) {
    val size: Int get() = orients[0].size

    /** 변환 번호(0..7) → 모양 번호 */
    fun orientOf(transform: Int): Int = byTransform[transform and 7]

    fun orient(transform: Int): Orient = orients[orientOf(transform)]
}

/**
 * 1~5칸 조각 21종 (자유 폴리오미노: 1·1·2·5·12개 = 89칸).
 * 모든 방향 모양은 처음 한 번 미리 계산한다.
 *
 * 변환 번호 t (0..7) = 뒤집기 f(0/1) × 4 + 시계 방향 회전 r(0..3): "먼저 좌우 뒤집고(f) 그다음 r 번 돌린" 모양.
 * - rotate(t): 지금 모양을 시계 방향 90° 한 번 더
 * - flip(t): 지금 모양을 좌우로 뒤집기 (F·R^r·F^f = R^(−r)·F^(f+1))
 */
object Pieces {
    /** (x, y) 쌍 목록. 크기순 → 같은 크기 안에서는 흔한 이름순 */
    private val RAW: Array<Pair<String, IntArray>> = arrayOf(
        "1" to intArrayOf(0, 0),
        "2" to intArrayOf(0, 0, 1, 0),
        "I3" to intArrayOf(0, 0, 1, 0, 2, 0),
        "V3" to intArrayOf(0, 0, 0, 1, 1, 1),
        "I4" to intArrayOf(0, 0, 1, 0, 2, 0, 3, 0),
        "L4" to intArrayOf(0, 0, 0, 1, 0, 2, 1, 2),
        "T4" to intArrayOf(0, 0, 1, 0, 2, 0, 1, 1),
        "O4" to intArrayOf(0, 0, 1, 0, 0, 1, 1, 1),
        "S4" to intArrayOf(1, 0, 2, 0, 0, 1, 1, 1),
        "F" to intArrayOf(1, 0, 2, 0, 0, 1, 1, 1, 1, 2),
        "I5" to intArrayOf(0, 0, 1, 0, 2, 0, 3, 0, 4, 0),
        "L5" to intArrayOf(0, 0, 0, 1, 0, 2, 0, 3, 1, 3),
        "N" to intArrayOf(1, 0, 1, 1, 1, 2, 0, 2, 0, 3),
        "P" to intArrayOf(0, 0, 1, 0, 0, 1, 1, 1, 0, 2),
        "T5" to intArrayOf(0, 0, 1, 0, 2, 0, 1, 1, 1, 2),
        "U" to intArrayOf(0, 0, 2, 0, 0, 1, 1, 1, 2, 1),
        "V5" to intArrayOf(0, 0, 0, 1, 0, 2, 1, 2, 2, 2),
        "W" to intArrayOf(0, 0, 0, 1, 1, 1, 1, 2, 2, 2),
        "X" to intArrayOf(1, 0, 0, 1, 1, 1, 2, 1, 1, 2),
        "Y" to intArrayOf(1, 0, 0, 1, 1, 1, 1, 2, 1, 3),
        "Z5" to intArrayOf(0, 0, 1, 0, 1, 1, 1, 2, 2, 2),
    )

    val ALL: List<Shape> = RAW.mapIndexed { id, (name, xy) -> build(id, name, xy) }

    /** 가장 큰 조각 칸 수 */
    const val MAX_SIZE = 5

    fun shape(id: Int): Shape = ALL[id]

    /** 시계 방향 90° 한 번 더 */
    fun rotate(t: Int): Int = (t and 4) or (((t and 3) + 1) and 3)

    /** 좌우 뒤집기 */
    fun flip(t: Int): Int = ((t and 4) xor 4) or ((4 - (t and 3)) and 3)

    private fun build(id: Int, name: String, xy: IntArray): Shape {
        val orients = ArrayList<Orient>()
        val byT = IntArray(8)
        for (t in 0 until 8) {
            val o = transform(xy, t)
            var found = orients.indexOfFirst { it.key == o.key }
            if (found < 0) {
                orients.add(o)
                found = orients.size - 1
            }
            byT[t] = found
        }
        return Shape(id, name, orients, byT)
    }

    /** 기본 모양에 변환 t 적용 후 왼쪽 위로 맞춤 */
    private fun transform(xy: IntArray, t: Int): Orient {
        val n = xy.size / 2
        val xs = IntArray(n)
        val ys = IntArray(n)
        for (k in 0 until n) {
            var x = xy[k * 2]
            var y = xy[k * 2 + 1]
            if (t and 4 != 0) x = -x
            repeat(t and 3) {
                // 화면 좌표(y 아래)에서 시계 방향 90°: (x, y) → (−y, x)
                val nx = -y
                y = x
                x = nx
            }
            xs[k] = x
            ys[k] = y
        }
        val mx = xs.minOrNull() ?: 0
        val my = ys.minOrNull() ?: 0
        for (k in 0 until n) {
            xs[k] -= mx
            ys[k] -= my
        }
        // 칸 순서를 위→아래, 왼→오른쪽으로 (그리기·비교가 일정하게)
        val order = (0 until n).sortedWith(compareBy({ ys[it] }, { xs[it] }))
        return Orient(IntArray(n) { xs[order[it]] }, IntArray(n) { ys[order[it]] })
    }
}
