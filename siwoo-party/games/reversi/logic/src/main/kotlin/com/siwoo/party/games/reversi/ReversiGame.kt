package com.siwoo.party.games.reversi

import com.siwoo.party.common.Ranking

/** 진행 단계 */
enum class Phase { PLAY, OVER }

/** 돌 놓기 결과 */
enum class Move {
    /** 놓임 (flipped 에 뒤집힌 돌) */
    PLACED,
    /** 빈 칸이지만 하나도 못 뒤집는 자리 (거절) */
    ILLEGAL,
    /** 이미 돌이 있는 칸 */
    OCCUPIED,
    /** 판 밖 */
    OUT,
    /** 이미 끝난 판 */
    OVER,
}

/** 끝난 이유 */
enum class EndReason {
    /** 아직 안 끝남 */
    NONE,
    /** 판이 가득 참 */
    FULL,
    /** 빈 칸이 있지만 둘 다 둘 곳이 없음 */
    NO_MOVES,
}

/**
 * 뒤집기 돌(리버시) 규칙 (2인). 순수 Kotlin.
 * - 칸 번호 = y * size + x, y 0 = 맨 위 줄. board[칸] = EMPTY 또는 돌 주인(0 = 1번 흑, 1 = 2번 백)
 * - 가운데 4칸(흑 2·백 2, 대각선으로 엇갈림)으로 시작, 흑(0)이 먼저 둔다.
 * - 새 돌과 내 돌 사이에 상대 돌이 빈틈 없이 끼면(8방향 어디든) 모두 내 돌로 뒤집힌다. 하나도 못 뒤집는 자리엔 못 둔다.
 * - 다음 사람이 둘 곳이 없으면 자동 패스(passed 에 기록), 둘 다 없으면(판이 가득 찬 경우 포함) 끝.
 * - 끝나면 돌이 많은 쪽 1위, 같으면 둘 다 1위.
 */
class ReversiGame(val size: Int = GameConfig.BOARD_SIZE) {

    init {
        require(size >= 4 && size % 2 == 0)
    }

    val cells = size * size
    val board = IntArray(cells) { EMPTY }
    /** 사람마다 판 위의 돌 수 */
    val counts = IntArray(GameConfig.PLAYERS)
    /** 지금 차례인 사람이 둘 수 있는 칸 (매 수마다 갱신) */
    val legal = BooleanArray(cells)
    var legalCount = 0
        private set

    var phase = Phase.PLAY
        private set
    var current = BLACK
        private set
    var endReason = EndReason.NONE
        private set

    var moveCount = 0
        private set
    /** 마지막으로 놓은 칸 (−1 = 아직 없음) */
    var lastMove = -1
        private set
    var lastMover = -1
        private set
    /** 방금 수 때문에 차례를 건너뛴 사람 (−1 = 패스 없음) */
    var passed = -1
        private set
    var passCount = 0
        private set

    /** 방금 뒤집힌 돌 칸 번호: 놓은 돌에서 가까운 순서 (같은 거리는 방향 순서) */
    val flipped = IntArray(cells)
    /** flipped[i] 가 놓은 돌에서 몇 칸 떨어졌는지 (1부터) */
    val flipDist = IntArray(cells)
    var flipCount = 0
        private set

    /** 방향마다 뒤집히는 돌 수 (계산용) */
    private val run = IntArray(8)

    init {
        reset()
    }

    /** 처음 상태: 가운데 4칸 (왼쪽 위·오른쪽 아래 = 백, 오른쪽 위·왼쪽 아래 = 흑) */
    fun reset() {
        board.fill(EMPTY)
        val m = size / 2
        board[index(m - 1, m - 1)] = WHITE
        board[index(m, m)] = WHITE
        board[index(m, m - 1)] = BLACK
        board[index(m - 1, m)] = BLACK
        phase = Phase.PLAY
        current = BLACK
        endReason = EndReason.NONE
        moveCount = 0
        lastMove = -1
        lastMover = -1
        passed = -1
        passCount = 0
        flipCount = 0
        recount()
        refreshLegal()
    }

    fun index(x: Int, y: Int): Int = y * size + x
    fun inside(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size
    fun at(x: Int, y: Int): Int = board[index(x, y)]
    fun count(p: Int): Int = counts[p]
    val isFull: Boolean get() = counts[0] + counts[1] == cells

    /** 이긴 사람 (끝나기 전이거나 무승부면 −1) */
    val winner: Int
        get() = if (phase != Phase.OVER || counts[0] == counts[1]) -1 else if (counts[0] > counts[1]) 0 else 1
    val isDraw: Boolean get() = phase == Phase.OVER && counts[0] == counts[1]

    /**
     * p 가 (x, y) 에 놓으면 뒤집히는 돌 수. 빈 칸이 아니면 0.
     * out/dist 를 주면 뒤집히는 칸을 놓은 돌에서 가까운 순서로 채운다. 할당 없음.
     */
    fun flipsFor(p: Int, x: Int, y: Int, out: IntArray? = null, dist: IntArray? = null): Int {
        if (!inside(x, y) || at(x, y) != EMPTY) return 0
        val o = 1 - p
        var total = 0
        var longest = 0
        for (d in 0 until 8) {
            val dx = DX[d]
            val dy = DY[d]
            var k = 1
            while (inside(x + dx * k, y + dy * k) && at(x + dx * k, y + dy * k) == o) k++
            // 상대 돌이 하나 이상 이어지고, 그 끝이 판 안의 내 돌이어야 끼임 (빈칸·판 끝이면 0)
            val n = if (k > 1 && inside(x + dx * k, y + dy * k) && at(x + dx * k, y + dy * k) == p) k - 1 else 0
            run[d] = n
            total += n
            if (n > longest) longest = n
        }
        if (out != null && total > 0) {
            var c = 0
            for (k in 1..longest) for (d in 0 until 8) {
                if (k > run[d]) continue
                out[c] = index(x + DX[d] * k, y + DY[d] * k)
                if (dist != null) dist[c] = k
                c++
            }
        }
        return total
    }

    /** p 가 (x, y) 에 둘 수 있는지 */
    fun isLegal(p: Int, x: Int, y: Int): Boolean = flipsFor(p, x, y) > 0

    /** p 가 둘 수 있는 칸 번호를 out 에 (작은 번호부터), 개수를 돌려줌 */
    fun legalMoves(p: Int, out: IntArray): Int {
        var c = 0
        for (i in 0 until cells) {
            if (board[i] != EMPTY) continue
            if (flipsFor(p, i % size, i / size) > 0) {
                if (c < out.size) out[c] = i
                c++
            }
        }
        return c
    }

    /** p 가 둘 곳이 하나라도 있는지 */
    fun hasMove(p: Int): Boolean {
        for (i in 0 until cells) {
            if (board[i] == EMPTY && flipsFor(p, i % size, i / size) > 0) return true
        }
        return false
    }

    /** 지금 차례인 사람이 (x, y) 에 놓기 */
    fun place(x: Int, y: Int): Move {
        if (phase != Phase.PLAY) return Move.OVER
        if (!inside(x, y)) return Move.OUT
        if (at(x, y) != EMPTY) return Move.OCCUPIED
        val p = current
        val n = flipsFor(p, x, y, flipped, flipDist)
        if (n == 0) return Move.ILLEGAL
        val cell = index(x, y)
        board[cell] = p
        for (i in 0 until n) board[flipped[i]] = p
        flipCount = n
        counts[p] += n + 1
        counts[1 - p] -= n
        moveCount++
        lastMove = cell
        lastMover = p
        passed = -1
        advance(p)
        return Move.PLACED
    }

    /** p 가 방금 둔 뒤 다음 차례 정하기: 상대 → (없으면) 자동 패스로 다시 p → (둘 다 없으면) 끝 */
    private fun advance(p: Int) {
        val o = 1 - p
        when {
            hasMove(o) -> current = o
            hasMove(p) -> {
                passed = o
                passCount++
                current = p
            }
            else -> {
                phase = Phase.OVER
                endReason = if (isFull) EndReason.FULL else EndReason.NO_MOVES
            }
        }
        refreshLegal()
    }

    /**
     * 테스트·연습용: 판을 글자로 채우기 (위 줄부터, 'B' = 흑(0), 'W' = 백(1), 그 밖 = 빈칸).
     * toMove 차례로 시작 — 그 사람이 둘 곳이 없으면 바로 자동 패스, 둘 다 없으면 끝.
     */
    fun load(rows: List<String>, toMove: Int) {
        require(rows.size == size && rows.all { it.length == size })
        for (y in 0 until size) for (x in 0 until size) {
            board[index(x, y)] = when (rows[y][x]) {
                'B' -> BLACK
                'W' -> WHITE
                else -> EMPTY
            }
        }
        recount()
        phase = Phase.PLAY
        endReason = EndReason.NONE
        moveCount = 0
        lastMove = -1
        lastMover = -1
        passed = -1
        passCount = 0
        flipCount = 0
        current = toMove
        if (!hasMove(toMove)) advance(1 - toMove) else refreshLegal()
    }

    /** 순위 (players 순서): 돌이 많은 쪽 1위, 같으면 둘 다 1위 */
    fun ranks(): IntArray = Ranking.competition(listOf(counts[0].toLong(), counts[1].toLong()))

    private fun recount() {
        counts.fill(0)
        for (v in board) if (v != EMPTY) counts[v]++
    }

    private fun refreshLegal() {
        legalCount = 0
        for (i in 0 until cells) {
            val ok = phase == Phase.PLAY && board[i] == EMPTY && flipsFor(current, i % size, i / size) > 0
            legal[i] = ok
            if (ok) legalCount++
        }
    }

    companion object {
        const val EMPTY = -1
        /** 1번 플레이어 = 흑 (먼저) */
        const val BLACK = 0
        /** 2번 플레이어 = 백 */
        const val WHITE = 1
        /** 8방향: →, ←, ↓, ↑, ↘, ↗, ↙, ↖ */
        private val DX = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        private val DY = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
    }
}
