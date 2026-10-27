package com.siwoo.party.games.ludo

/**
 * 십자형 판 모양 (15×15 칸, 좌표는 칸 단위 0~15 — 칸 (c, r) 의 가운데는 (c + 0.5, r + 0.5)).
 *
 * 자리(seat) 0 = 왼쪽 위, 1 = 오른쪽 위, 2 = 오른쪽 아래, 3 = 왼쪽 아래 (시계 방향). 자리 s 의 모든 좌표는
 * 자리 0 의 좌표를 판 가운데(7.5, 7.5) 기준 시계 방향으로 90° × s 돌린 것.
 * - 바깥 길 52칸: 0번 = 자리 0 출발 칸 (1, 6), 시계 방향으로 번호가 늘어난다. 자리 s 의 출발 칸 = 13 × s.
 * - 집 통로: 자리 0 은 (1, 7) ~ (5, 7), 그다음 가운데 집 (왼쪽 삼각형).
 * - 대기 자리: 왼쪽 위 6×6 마당 안 네 곳.
 * 화면·아이콘·규칙 카드가 같이 쓴다 (Android 의존성 없음).
 */
object LudoBoard {
    const val SIZE = 15

    /** 바깥 길 칸 가운데 좌표 */
    val TRACK_X = FloatArray(GameConfig.TRACK)
    val TRACK_Y = FloatArray(GameConfig.TRACK)
    /** 안전 칸 (출발 칸 + 별 칸) */
    val SAFE = BooleanArray(GameConfig.TRACK)
    /** 별 칸 (출발 칸이 아닌 안전 칸) */
    val STAR = BooleanArray(GameConfig.TRACK)

    /** 집 통로 [seat][0..4] */
    val LANE_X = Array(GameConfig.SEATS) { FloatArray(GameConfig.HOME_LANE) }
    val LANE_Y = Array(GameConfig.SEATS) { FloatArray(GameConfig.HOME_LANE) }
    /** 집 안 말 자리 [seat][piece] (가운데 삼각형 안) */
    val GOAL_X = Array(GameConfig.SEATS) { FloatArray(GameConfig.PIECES) }
    val GOAL_Y = Array(GameConfig.SEATS) { FloatArray(GameConfig.PIECES) }
    /** 대기 자리 [seat][piece] */
    val YARD_X = Array(GameConfig.SEATS) { FloatArray(GameConfig.PIECES) }
    val YARD_Y = Array(GameConfig.SEATS) { FloatArray(GameConfig.PIECES) }
    /** 마당(6×6) 왼쪽 위 [seat] */
    val YARD_L = FloatArray(GameConfig.SEATS)
    val YARD_T = FloatArray(GameConfig.SEATS)

    /** 자리 0 의 바깥 길 13칸 (칸 좌표) — 나머지 자리는 이것을 돌린다 */
    private val BASE = intArrayOf(1, 6, 2, 6, 3, 6, 4, 6, 5, 6, 6, 5, 6, 4, 6, 3, 6, 2, 6, 1, 6, 0, 7, 0, 8, 0)
    private val BASE_YARD = floatArrayOf(2f, 2f, 4f, 2f, 2f, 4f, 4f, 4f)
    private val BASE_GOAL = floatArrayOf(6.42f, 6.92f, 6.42f, 8.08f, 6.98f, 7.5f, 6.42f, 7.5f)

    private val tmp = FloatArray(2)

    /** (x, y) 를 판 가운데 기준 시계 방향 90° × times 회전해 tmp 에 */
    private fun rot(x: Float, y: Float, times: Int) {
        var a = x
        var b = y
        repeat(((times % 4) + 4) % 4) {
            val na = SIZE - b
            b = a
            a = na
        }
        tmp[0] = a
        tmp[1] = b
    }

    init {
        for (s in 0 until GameConfig.SEATS) {
            for (i in 0 until GameConfig.SEAT_SPAN) {
                rot(BASE[i * 2] + 0.5f, BASE[i * 2 + 1] + 0.5f, s)
                val n = s * GameConfig.SEAT_SPAN + i
                TRACK_X[n] = tmp[0]
                TRACK_Y[n] = tmp[1]
            }
            SAFE[startOf(s)] = true
            val star = (startOf(s) + GameConfig.STAR_OFFSET) % GameConfig.TRACK
            SAFE[star] = true
            STAR[star] = true
            for (j in 0 until GameConfig.HOME_LANE) {
                rot(1f + j + 0.5f, 7.5f, s)
                LANE_X[s][j] = tmp[0]
                LANE_Y[s][j] = tmp[1]
            }
            for (k in 0 until GameConfig.PIECES) {
                rot(BASE_YARD[k * 2], BASE_YARD[k * 2 + 1], s)
                YARD_X[s][k] = tmp[0]
                YARD_Y[s][k] = tmp[1]
                rot(BASE_GOAL[k * 2], BASE_GOAL[k * 2 + 1], s)
                GOAL_X[s][k] = tmp[0]
                GOAL_Y[s][k] = tmp[1]
            }
            // 마당 가운데를 돌려서 왼쪽 위를 구함
            rot(3f, 3f, s)
            YARD_L[s] = tmp[0] - 3f
            YARD_T[s] = tmp[1] - 3f
        }
    }

    /** 자리 s 의 출발 칸 (바깥 길 번호) */
    fun startOf(seat: Int): Int = seat * GameConfig.SEAT_SPAN

    /** 자리 s 의 말이 진행 pos(0~50)에 있을 때 바깥 길 번호 (-1 = 바깥 길이 아님) */
    fun trackIndex(seat: Int, pos: Int): Int =
        if (pos in 0..GameConfig.LAST_TRACK) (startOf(seat) + pos) % GameConfig.TRACK else -1

    /**
     * 자리 s 의 말 k 가 진행 pos 에 있을 때 그릴 좌표 (칸 단위) → out[0], out[1].
     * pos −1 = 대기 자리, 0~50 = 바깥 길, 51~55 = 집 통로, 56 = 집 안.
     */
    fun point(seat: Int, k: Int, pos: Int, out: FloatArray) {
        when {
            pos < 0 -> { out[0] = YARD_X[seat][k]; out[1] = YARD_Y[seat][k] }
            pos <= GameConfig.LAST_TRACK -> {
                val n = trackIndex(seat, pos)
                out[0] = TRACK_X[n]; out[1] = TRACK_Y[n]
            }
            pos < GameConfig.HOME -> {
                val j = pos - GameConfig.LAST_TRACK - 1
                out[0] = LANE_X[seat][j]; out[1] = LANE_Y[seat][j]
            }
            else -> { out[0] = GOAL_X[seat][k]; out[1] = GOAL_Y[seat][k] }
        }
    }
}
