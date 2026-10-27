package com.siwoo.party.games.snakeladder

/** 사다리(아래 → 위) 또는 뱀(머리 → 꼬리) 하나: from 칸에 멈추면 to 칸으로 */
data class Jump(val from: Int, val to: Int) {
    val isLadder: Boolean get() = to > from
}

/** 맵 하나: 이름 + 사다리 8개 + 뱀 8개 */
class SnakeMap(val name: String, val ladders: List<Jump>, val snakes: List<Jump>) {
    /** 칸 → 그 칸에서 시작하는 사다리·뱀 (없으면 null). 1..100 */
    private val at = arrayOfNulls<Jump>(GameConfig.SQUARES + 1)

    init {
        for (j in ladders + snakes) if (j.from in 1..GameConfig.SQUARES) at[j.from] = j
    }

    /** square 에서 시작하는 사다리·뱀 */
    fun jumpAt(square: Int): Jump? = if (square in 1..GameConfig.SQUARES) at[square] else null

    val all: List<Jump> get() = ladders + snakes
}

/**
 * 판 좌표 (지그재그). 줄 row 0 = 맨 아래, 칸 col 0 = 왼쪽.
 * 1~10 은 왼쪽 → 오른쪽, 11~20 은 오른쪽 → 왼쪽 … 그래서 100번 칸은 왼쪽 위.
 */
object Board {
    /** 칸 번호(1..100) → 줄 (0 = 아래) */
    fun row(square: Int): Int = (square - 1) / GameConfig.SIDE

    /** 칸 번호(1..100) → 열 (0 = 왼쪽) */
    fun col(square: Int): Int {
        val r = row(square)
        val k = (square - 1) % GameConfig.SIDE
        return if (r % 2 == 0) k else GameConfig.SIDE - 1 - k
    }

    /** (줄, 열) → 칸 번호 */
    fun square(row: Int, col: Int): Int {
        val k = if (row % 2 == 0) col else GameConfig.SIDE - 1 - col
        return row * GameConfig.SIDE + k + 1
    }
}

/** 기본 맵 3종 + 맵 검증 */
object SnakeMaps {
    private fun j(from: Int, to: Int) = Jump(from, to)

    val CLASSIC = SnakeMap(
        "클래식",
        listOf(j(3, 22), j(8, 30), j(28, 55), j(36, 44), j(51, 67), j(58, 77), j(71, 92), j(80, 99)),
        listOf(j(17, 7), j(34, 12), j(47, 26), j(62, 19), j(74, 53), j(87, 24), j(95, 75), j(98, 79)),
    )

    val DEN = SnakeMap(
        "뱀 소굴",
        listOf(j(2, 38), j(7, 14), j(15, 26), j(21, 42), j(32, 51), j(50, 69), j(63, 81), j(78, 97)),
        listOf(j(16, 6), j(46, 25), j(49, 11), j(56, 34), j(64, 43), j(89, 68), j(93, 73), j(98, 58)),
    )

    val COASTER = SnakeMap(
        "롤러코스터",
        listOf(j(4, 25), j(9, 31), j(20, 41), j(33, 52), j(40, 59), j(54, 86), j(65, 83), j(72, 91)),
        listOf(j(27, 5), j(37, 18), j(47, 29), j(61, 39), j(70, 48), j(85, 57), j(94, 76), j(97, 62)),
    )

    val ALL: List<SnakeMap> = listOf(CLASSIC, DEN, COASTER)

    fun get(index: Int): SnakeMap = ALL[index.coerceIn(0, ALL.size - 1)]

    /**
     * 맵 검증 → 문제 목록 (빈 목록 = 정상).
     * - 사다리 8개·뱀 8개
     * - 사다리는 위로(다른 줄로), 뱀은 아래로(다른 줄로)
     * - 모든 끝점은 2..99 (출발·도착 칸 금지)
     * - 끝점이 겹치지 않음: 한 칸이 두 사다리·뱀의 시작이거나, 한쪽 끝이 다른 쪽 시작인(연쇄) 경우 없음
     */
    fun validate(map: SnakeMap): List<String> {
        val errors = ArrayList<String>()
        if (map.ladders.size != GameConfig.LADDERS) errors.add("사다리 ${map.ladders.size}개")
        if (map.snakes.size != GameConfig.SNAKES) errors.add("뱀 ${map.snakes.size}개")
        for (l in map.ladders) {
            if (l.to <= l.from) errors.add("사다리 ${l.from}→${l.to} 가 위로 가지 않음")
            else if (Board.row(l.to) <= Board.row(l.from)) errors.add("사다리 ${l.from}→${l.to} 가 같은 줄")
        }
        for (s in map.snakes) {
            if (s.to >= s.from) errors.add("뱀 ${s.from}→${s.to} 가 아래로 가지 않음")
            else if (Board.row(s.to) >= Board.row(s.from)) errors.add("뱀 ${s.from}→${s.to} 가 같은 줄")
        }
        val seen = HashSet<Int>()
        for (x in map.all) for (sq in intArrayOf(x.from, x.to)) {
            if (sq !in 2 until GameConfig.SQUARES) errors.add("끝점 $sq 이 판 안쪽(2~99)이 아님")
            if (!seen.add(sq)) errors.add("칸 $sq 이 겹침")
        }
        return errors
    }
}
