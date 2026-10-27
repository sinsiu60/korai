package com.siwoo.party.games.umul

/**
 * 우물고누 판 모양 (점 5개, 선 7개).
 *
 * ```
 *  TL ───── TR        TL(0)  TR(1)
 *  │ ╲     ╱ │
 *  │   C(4)  │        네 귀퉁이 + 가운데
 *  │ ╱     ╲ │
 *  BL  (우물) BR      BL(2)  BR(3)
 * ```
 * 선: 귀퉁이 4곳 ↔ 가운데(대각선 4개), 왼쪽 변 TL–BL, 오른쪽 변 TR–BR, 위 변 TL–TR.
 * 아래 변 BL–BR 자리는 우물(연못)이라 **선이 없다** — 그래서 BL·BR 은 이웃이 둘뿐이다.
 */
object UmulBoard {
    const val TL = 0
    const val TR = 1
    const val BL = 2
    const val BR = 3
    const val C = 4
    const val POINTS = 5

    /** 선 목록 (양 끝 점) */
    val LINES: Array<IntArray> = arrayOf(
        intArrayOf(TL, C), intArrayOf(TR, C), intArrayOf(BL, C), intArrayOf(BR, C),
        intArrayOf(TL, BL), intArrayOf(TR, BR), intArrayOf(TL, TR),
    )

    /** 우물 쪽 두 귀퉁이 (서로 연결 없음) */
    val WELL: IntArray = intArrayOf(BL, BR)

    /** 점마다 이웃 점 */
    val NEIGHBORS: Array<IntArray> = Array(POINTS) { p ->
        LINES.filter { it[0] == p || it[1] == p }.map { if (it[0] == p) it[1] else it[0] }.sorted().toIntArray()
    }

    private val adj = Array(POINTS) { a -> BooleanArray(POINTS) { b -> b in NEIGHBORS[a] } }

    /** a 와 b 가 선으로 바로 이어졌는지 */
    fun adjacent(a: Int, b: Int): Boolean = a in 0 until POINTS && b in 0 until POINTS && adj[a][b]

    /**
     * 처음 배치 (players 순서의 주인, 빈 점 = EMPTY).
     * 1번(0)은 왼쪽 변 TL·BL, 2번(1)은 오른쪽 변 TR·BR — 둘 다 우물 옆 귀퉁이를 하나씩 갖고 가운데가 빈다.
     */
    fun startOwners(): IntArray = intArrayOf(0, 1, 0, 1, UmulRound.EMPTY)
}

/** 한 수 결과 */
enum class Move {
    /** 옮김 (lastFrom → lastTo) */
    MOVED,
    /** 내 말이 아님 (빈 점·상대 말·없는 점) */
    NOT_MINE,
    /** 빈 점과 선으로 이어지지 않아 못 움직임 */
    BLOCKED,
    /** 첫 수에 상대를 바로 가두는 수 (금지) */
    FORBIDDEN_FIRST,
    /** 이미 끝난 판 */
    OVER,
}

/** 한 판의 상태 */
enum class RoundState { PLAY, WON, DRAW }

/**
 * 우물고누 한 판 (2인). 순수 Kotlin.
 * - 말 4개가 점 5개 중 4개를 차지하므로 빈 점은 늘 하나. 내 차례에 빈 점과 선으로 이어진 내 말 하나를 빈 점으로 옮긴다
 *   (그래서 움직일 수 있는 말은 늘 "빈 점으로 한 칸"뿐 — 말을 누르면 갈 곳이 하나로 정해진다).
 * - 차례가 된 사람이 움직일 말이 하나도 없으면(갇힘) 그 사람이 진다 → 방금 둔 사람 승리.
 * - **첫 수 금지**: 두 사람 각자의 첫 수(판의 1·2번째 수)로 상대를 곧바로 가두는 수는 둘 수 없다.
 *   (처음 배치에서 먼저 두는 사람이 우물 옆 말을 가운데로 올리면 상대가 바로 갇히기 때문.)
 *   금지 수를 빼면 둘 수가 없을 때는 금지를 풀어 준다 (이 배치에선 일어나지 않음 — 안전장치).
 * - 두 사람 합쳐 moveLimit 수가 지나도록 아무도 갇히지 않으면 무승부 판.
 */
class UmulRound(
    val starter: Int,
    val moveLimit: Int = GameConfig.MOVE_LIMIT,
    start: IntArray = UmulBoard.startOwners(),
) {
    init {
        require(starter in 0 until GameConfig.PLAYERS)
        require(moveLimit >= 1)
        require(start.size == UmulBoard.POINTS)
        require(start.count { it == EMPTY } == 1)
        require((0 until GameConfig.PLAYERS).all { p -> start.count { it == p } == GameConfig.PIECES })
    }

    /** owners[점] = 주인(0/1) 또는 EMPTY */
    val owners: IntArray = start.copyOf()

    var state = RoundState.PLAY
        private set
    var current = starter
        private set
    /** 이긴 사람 (WON 일 때), 아니면 −1 */
    var winner = -1
        private set
    /** 갇혀서 진 사람 (WON 일 때), 아니면 −1 */
    val loser: Int get() = if (winner < 0) -1 else 1 - winner

    var moveCount = 0
        private set
    var lastFrom = -1
        private set
    var lastTo = -1
        private set

    /** 지금 빈 점 */
    val empty: Int get() = owners.indexOf(EMPTY)

    /** 남은 수 (한도까지) */
    val movesLeft: Int get() = (moveLimit - moveCount).coerceAtLeast(0)

    /** 지금 수가 그 사람의 첫 수인지 (판의 1·2번째 수) */
    val isFirstMove: Boolean get() = moveCount < GameConfig.PLAYERS

    fun owner(point: Int): Int = owners[point]

    /** p 의 말 중 빈 점과 이어진 말이 하나라도 있는지 (금지 규칙과 무관한 순수 갇힘 판정) */
    fun hasAnyMove(p: Int): Boolean = hasAnyMove(owners, p)

    /** from 의 말을 빈 점으로 옮겼을 때 상대가 바로 갇히는지 */
    fun wouldTrap(from: Int): Boolean {
        val e = empty
        if (owners[from] == EMPTY || !UmulBoard.adjacent(from, e)) return false
        val who = owners[from]
        owners[e] = who
        owners[from] = EMPTY
        val trapped = !hasAnyMove(owners, 1 - who)
        owners[from] = who
        owners[e] = EMPTY
        return trapped
    }

    /** 지금 차례인 사람이 from 의 말을 옮기면 첫 수 금지에 걸리는지 */
    fun isForbidden(from: Int): Boolean {
        if (!isFirstMove || state != RoundState.PLAY) return false
        if (owners[from] != current || !UmulBoard.adjacent(from, empty)) return false
        if (!wouldTrap(from)) return false
        // 금지 수 말고 다른 수가 있을 때만 금지 (전부 금지면 풀어 줌)
        for (q in UmulBoard.NEIGHBORS[empty]) if (q != from && owners[q] == current && !wouldTrap(q)) return true
        return false
    }

    /** 지금 차례인 사람이 from 의 말을 (규칙상) 옮길 수 있는지 */
    fun canMove(from: Int): Boolean =
        state == RoundState.PLAY && from in 0 until UmulBoard.POINTS && owners[from] == current &&
            UmulBoard.adjacent(from, empty) && !isForbidden(from)

    /** 지금 차례인 사람이 옮길 수 있는 말의 점 목록 (out 에 채우고 개수 반환, 할당 없음) */
    fun movable(out: IntArray): Int {
        var n = 0
        for (p in 0 until UmulBoard.POINTS) if (canMove(p) && n < out.size) out[n++] = p
        return n
    }

    /** 지금 차례인 사람이 from 의 말을 빈 점으로 옮김 */
    fun move(from: Int): Move {
        if (state != RoundState.PLAY) return Move.OVER
        if (from !in 0 until UmulBoard.POINTS || owners[from] != current) return Move.NOT_MINE
        val to = empty
        if (!UmulBoard.adjacent(from, to)) return Move.BLOCKED
        if (isForbidden(from)) return Move.FORBIDDEN_FIRST
        val p = current
        owners[to] = p
        owners[from] = EMPTY
        lastFrom = from
        lastTo = to
        moveCount++
        if (!hasAnyMove(owners, 1 - p)) {
            winner = p
            state = RoundState.WON
        } else if (moveCount >= moveLimit) {
            state = RoundState.DRAW
        } else {
            current = 1 - p
        }
        return Move.MOVED
    }

    companion object {
        const val EMPTY = -1

        fun hasAnyMove(owners: IntArray, p: Int): Boolean {
            val e = owners.indexOf(EMPTY)
            if (e < 0) return false
            for (q in UmulBoard.NEIGHBORS[e]) if (owners[q] == p) return true
            return false
        }
    }
}

/**
 * 3판 2선승 대결. 판마다 먼저 두는 사람이 번갈아 바뀐다 (1판은 firstStarter).
 * - 판을 WINS_NEEDED 번 먼저 이긴 사람이 대결 승리.
 * - 무승부 판(수 한도)은 아무도 점수를 얻지 않고 다음 판으로. 판 수는 MAX_ROUNDS 까지 —
 *   그때까지 승부가 안 나면 이긴 판이 많은 사람 승리, 같으면 무승부(둘 다 1위).
 */
class UmulMatch(
    val firstStarter: Int,
    val winsNeeded: Int = GameConfig.WINS_NEEDED,
    val maxRounds: Int = GameConfig.MAX_ROUNDS,
    val moveLimit: Int = GameConfig.MOVE_LIMIT,
) {
    init {
        require(firstStarter in 0 until GameConfig.PLAYERS)
        require(winsNeeded >= 1 && maxRounds >= 1)
    }

    val wins = IntArray(GameConfig.PLAYERS)
    /** 끝난 판마다 이긴 사람 (무승부 판 = −1) */
    val results = IntArray(maxRounds) { -1 }
    var roundsPlayed = 0
        private set
    var round: UmulRound? = null
        private set

    /** 몇 번째 판인지 (1부터, 진행 중이거나 다음에 시작할 판) */
    val roundNumber: Int get() = roundsPlayed + 1

    /** 다음 판에 먼저 둘 사람 */
    val nextStarter: Int get() = (firstStarter + roundsPlayed) % GameConfig.PLAYERS

    val isOver: Boolean get() = wins.any { it >= winsNeeded } || roundsPlayed >= maxRounds

    /** 대결 승자 (끝났을 때, 무승부면 −1) */
    val winner: Int
        get() = when {
            wins[0] > wins[1] -> 0
            wins[1] > wins[0] -> 1
            else -> -1
        }

    /** 새 판 시작 (앞 판이 끝났어야 함) */
    fun startRound(): UmulRound {
        check(!isOver) { "대결이 이미 끝남" }
        check(round == null || round?.state != RoundState.PLAY) { "앞 판이 아직 진행 중" }
        val r = UmulRound(nextStarter, moveLimit)
        round = r
        return r
    }

    /** 끝난 판 결과 반영 (한 판에 한 번). 반환: 그 판 승자 (무승부 판 −1) */
    fun finishRound(): Int {
        val r = round ?: error("진행 중인 판 없음")
        check(r.state != RoundState.PLAY) { "판이 아직 안 끝남" }
        check(roundsPlayed < maxRounds)
        val w = r.winner
        if (w >= 0) wins[w]++
        results[roundsPlayed] = w
        roundsPlayed++
        round = null
        return w
    }

    /** 순위 (players 순서): 이긴 사람 1위·진 사람 2위, 무승부는 둘 다 1위 */
    fun ranks(): IntArray {
        val w = winner
        return if (w < 0) IntArray(GameConfig.PLAYERS) { 1 } else IntArray(GameConfig.PLAYERS) { if (it == w) 1 else 2 }
    }
}
