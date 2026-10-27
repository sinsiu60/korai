package com.siwoo.party.games.yut

/**
 * 표준 윷판 29칸 (노드 그래프).
 *
 * ```
 *  10 ─ 9 ─ 8 ─ 7 ─ 6 ─ 5        5  = 모 (오른쪽 위 모서리)
 *  │ 25               20 │       10 = 뒷모 (왼쪽 위 모서리)
 *  11   26         21   4        15 = 찌모 (왼쪽 아래 모서리)
 *  12        22         3        22 = 방 (중앙)
 *  13   23         27   2        0  = 참먹이 (오른쪽 아래, 출발·골인)
 *  14 24               28 1
 *  15 ─16 ─17 ─18 ─19 ─ 0
 * ```
 * - 바깥 20칸 0~19: 참먹이(0)에서 오른쪽 변을 올라가 모(5) → 위 변을 왼쪽으로 뒷모(10)
 *   → 왼쪽 변을 내려가 찌모(15) → 아래 변을 오른쪽으로 다시 참먹이(0). (시계 반대 방향)
 * - 모 쪽 대각선: 5 → 20 → 21 → 방(22) → 23 → 24 → 찌모(15)
 * - 뒷모 쪽 대각선: 10 → 25 → 26 → 방(22) → 27 → 28 → 참먹이(0)
 * - 지름길 분기 노드는 모(5)·뒷모(10)·방(22). 이동을 "시작할 때" 그 칸에 서 있던 말만 지름길로 들어간다
 *   (= 그 칸에 정확히 멈춘 경우만). 지나가기만 하면 원래 길을 그대로 간다.
 *   - 모에 멈춤 → 대각선으로 방 쪽 (20)
 *   - 뒷모에 멈춤 → 대각선으로 방 쪽 (25)
 *   - 방에 멈춤 → 참먹이로 가는 짧은 길 (27)
 *   - 방을 지나감 → 들어온 대각선을 그대로 직진 (21 쪽에서 오면 23, 26 쪽에서 오면 27)
 *
 * 말의 이동은 이 그래프에서 미리 뽑은 네 가지 길(ROUTES)의 위치(길 번호 + 몇 번째)로 기록한다.
 * 같은 칸이라도 지나온 길이 다를 수 있어(예: 찌모 15 는 바깥 길·모 지름길 모두) 빽도는 "지나온 길"로 한 칸 뒤.
 */
object YutBoard {
    const val NODES = 29

    /** 참먹이 (출발·골인) */
    const val START = 0
    /** 모 (오른쪽 위 모서리) */
    const val MO = 5
    /** 뒷모 (왼쪽 위 모서리) */
    const val DWIMO = 10
    /** 찌모 (왼쪽 아래 모서리) */
    const val JJIMO = 15
    /** 방 (중앙) */
    const val CENTER = 22

    /**
     * 그냥 지나갈 때의 다음 칸. 방(22)은 모 쪽 대각선에서 들어온 경우 기준 (23);
     * 뒷모 쪽 대각선(26)에서 들어오면 next() 가 27 로 바꾼다.
     */
    private val NEXT = intArrayOf(
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 0, // 바깥 0~19
        21, 22, 23, 24, JJIMO, // 모 대각선 20, 21, 방 22, 23, 24
        26, CENTER, 28, START, // 뒷모 대각선 25, 26, 27, 28
    )

    /** 정확히 멈춘 뒤 다음 이동이 들어가는 지름길 (분기 노드가 아니면 -1) */
    private val SHORTCUT = IntArray(NODES) { -1 }.also {
        it[MO] = 20
        it[DWIMO] = 25
        it[CENTER] = 27
    }

    /** 지름길 분기 노드인지 (모·뒷모·방) */
    fun isBranch(node: Int): Boolean = SHORTCUT[node] >= 0

    /** 분기 노드에 멈췄을 때 들어가는 지름길 첫 칸 (-1 = 분기 노드 아님) */
    fun shortcut(node: Int): Int = SHORTCUT[node]

    /** 지나갈 때의 다음 칸 (from = 직전 칸; 방에서만 의미가 있다) */
    fun next(node: Int, from: Int): Int = if (node == CENTER && from == 26) 27 else NEXT[node]

    /** 큰 원으로 그리는 칸 (네 모서리 + 방) */
    fun isMajor(node: Int): Boolean = node == START || node == MO || node == DWIMO || node == JJIMO || node == CENTER

    // ── 길 ──

    /** 바깥 한 바퀴: 0→1…19→0 (20걸음) */
    const val ROUTE_OUTER = 0
    /** 모 지름길: 0…5→20→21→22→23→24→15→16…19→0 (16걸음) — 방은 지나감 */
    const val ROUTE_MO = 1
    /** 모 → 방에 멈춤 → 참먹이: 0…5→20→21→22→27→28→0 (11걸음) */
    const val ROUTE_MO_BANG = 2
    /** 뒷모 지름길: 0…10→25→26→22→27→28→0 (16걸음) */
    const val ROUTE_DWIMO = 3
    const val ROUTE_COUNT = 4

    /** 길마다 지나는 칸 (0번째 = 참먹이 출발, 마지막 = 참먹이 골인) */
    val ROUTES: Array<IntArray> = arrayOf(
        walk(),
        walk(MO),
        walk(MO, CENTER),
        walk(DWIMO),
    )

    /** 그래프를 따라 참먹이에서 참먹이까지 걷는다. turns 에 있는 분기 노드에서만 지름길로 */
    private fun walk(vararg turns: Int): IntArray {
        val out = ArrayList<Int>()
        out.add(START)
        var node = START
        var from = -1
        while (true) {
            val nx = if (node != START && turns.contains(node)) SHORTCUT[node] else next(node, from)
            from = node
            node = nx
            out.add(node)
            if (node == START) break
        }
        return out.toIntArray()
    }

    /** 길의 골인 위치 (= 참먹이까지 걸음 수) */
    fun goalIndex(route: Int): Int = ROUTES[route].size - 1

    /** 길 route 의 index 번째 칸 */
    fun nodeAt(route: Int, index: Int): Int = ROUTES[route][index]

    /** 바깥 한 바퀴 걸음 수 (진행 거리 기준) */
    val OUTER_STEPS: Int get() = goalIndex(ROUTE_OUTER)

    /**
     * 이동을 시작할 때의 길 정리: 서 있는 칸과 지나온 길로 "지금부터 갈 길"을 정한다.
     * - 모·뒷모에 서 있으면 지름길, 방에 서 있으면 참먹이 쪽 짧은 길 (정확히 멈춘 경우만 지름길)
     * - 빽도로 분기 노드 바로 앞까지 물러난 말은 다시 바깥 길(또는 들어온 대각선)로
     * - 찌모~날윷(15~19)과 방 아래 27·28 은 길이 둘일 수 있어 지나온 길을 유지 (빽도 방향이 다름)
     * 돌려주는 값: 길 번호 * 100 + 위치 (할당 없이 둘을 함께 돌려주려고)
     */
    fun normalize(route: Int, index: Int): Int {
        val node = ROUTES[route][index]
        val r = when (node) {
            START -> ROUTE_OUTER // 참먹이에 선 말 (빽도로만 생김): 바깥 길 끝
            in 1..4, in 6..9, in 11..14 -> ROUTE_OUTER
            MO, 20, 21, 23, 24 -> ROUTE_MO
            DWIMO, 25, 26 -> ROUTE_DWIMO
            CENTER, 27, 28 -> if (route == ROUTE_DWIMO) ROUTE_DWIMO else ROUTE_MO_BANG
            else -> if (route == ROUTE_OUTER) ROUTE_OUTER else ROUTE_MO // 15~19
        }
        val i = if (node == START) goalIndex(ROUTE_OUTER) else ROUTES[r].indexOf(node)
        return r * 100 + i
    }

    // ── 화면 좌표 (판 한 변 = 1, 왼쪽 위 (0,0)) ──

    val X = FloatArray(NODES)
    val Y = FloatArray(NODES)

    init {
        for (k in 0..5) { X[k] = 1f; Y[k] = 1f - k / 5f } // 오른쪽 변 (아래 → 위)
        for (k in 0..5) { X[5 + k] = 1f - k / 5f; Y[5 + k] = 0f } // 위 변 (오른쪽 → 왼쪽)
        for (k in 0..5) { X[10 + k] = 0f; Y[10 + k] = k / 5f } // 왼쪽 변 (위 → 아래)
        for (k in 1..4) { X[15 + k] = k / 5f; Y[15 + k] = 1f } // 아래 변 (왼쪽 → 오른쪽)
        // 모 대각선 (1,0) → (0,1): 중앙까지 세 칸씩
        val d = floatArrayOf(1f / 6f, 2f / 6f, 3f / 6f, 4f / 6f, 5f / 6f)
        for (k in 0..4) { X[20 + k] = 1f - d[k]; Y[20 + k] = d[k] }
        // 뒷모 대각선 (0,0) → (1,1): 25, 26, (22), 27, 28
        X[25] = d[0]; Y[25] = d[0]
        X[26] = d[1]; Y[26] = d[1]
        X[27] = d[3]; Y[27] = d[3]
        X[28] = d[4]; Y[28] = d[4]
    }

    /** 칸 이름 (모서리·중앙만, 나머지는 빈 글자) */
    fun majorName(node: Int): String = when (node) {
        START -> "참먹이"
        MO -> "모"
        DWIMO -> "뒷모"
        JJIMO -> "찌모"
        CENTER -> "방"
        else -> ""
    }
}
