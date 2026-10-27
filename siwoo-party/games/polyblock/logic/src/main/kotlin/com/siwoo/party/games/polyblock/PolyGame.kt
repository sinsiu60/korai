package com.siwoo.party.games.polyblock

import com.siwoo.party.common.Ranking

/** 이 자리에 놓을 수 있는지 판정 결과 */
enum class Fit {
    OK,
    /** 판 밖으로 나감 */
    OUTSIDE,
    /** 이미 놓은 조각과 겹침 */
    OVERLAP,
    /** 내 조각과 변이 닿음 */
    EDGE,
    /** 첫 조각이 내 모서리 칸을 덮지 않음 */
    NO_CORNER,
    /** 내 조각과 꼭짓점으로 닿지 않음 */
    NO_TOUCH,
    /** 이미 쓴 조각 / 끝난 판 */
    UNAVAILABLE,
}

/** 한 수: 조각 piece 를 모양 orient 로, 왼쪽 위 (x, y) 에 */
data class Move(val piece: Int, val orient: Int, val x: Int, val y: Int)

/**
 * 조각 배치 대결 규칙 (2~4인). 순수 Kotlin.
 * - 판: 3·4인 BOARD_BIG², 2인 BOARD_SMALL². 칸 번호 = y * size + x, board[칸] = EMPTY 또는 놓은 사람 번호
 * - 시작 모서리: 4인 왼위·오른위·오른아래·왼아래 순 (3인은 앞의 셋), 2인은 왼위·오른아래 (마주 보는 두 모서리)
 * - 첫 조각은 자기 모서리 칸을 덮어야 한다. 이후 조각은 자기 조각과 꼭짓점으로 하나 이상 닿고, 변으로는 닿으면 안 된다.
 *   다른 사람 조각과는 마음대로 닿아도 된다.
 * - 차례가 왔을 때 놓을 수 있는 조각이 하나도 없으면 자동 패스 → 그 사람은 끝(out). 21개를 모두 놓아도 끝.
 *   모두 끝나면 판 종료.
 * - 점수 = 남은 조각 칸 수 (적을수록 좋음), 21개 모두 놓았으면 ALL_PLACED_SCORE(−15). 같은 점수는 같은 순위.
 */
class PolyGame(val playerCount: Int, val first: Int = 0) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
        require(first in 0 until playerCount)
    }

    val size: Int = if (playerCount == 2) GameConfig.BOARD_SMALL else GameConfig.BOARD_BIG
    val cells = size * size
    val board = IntArray(cells) { EMPTY }

    /** 플레이어별 시작 모서리 칸 번호 */
    val corners = IntArray(playerCount) { p ->
        val last = size - 1
        val c = if (playerCount == 2) (if (p == 0) 0 else 2) else p
        when (c) {
            0 -> 0
            1 -> last
            2 -> last * size + last
            else -> last * size
        }
    }

    /** left[p][조각] = 아직 안 쓴 조각 */
    private val left = Array(playerCount) { BooleanArray(GameConfig.PIECE_COUNT) { true } }
    /** 놓은 조각 수 */
    val placedCount = IntArray(playerCount)
    /** 더 이상 둘 수 없는 사람 (패스했거나 다 놓음) */
    val out = BooleanArray(playerCount)

    var current = first
        private set
    var over = false
        private set

    /** 마지막으로 놓은 사람·칸들 (연출용) */
    var lastPlayer = -1
        private set
    val lastCells = IntArray(Pieces.MAX_SIZE)
    var lastCount = 0
        private set

    fun index(x: Int, y: Int): Int = y * size + x
    fun inside(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size
    fun at(x: Int, y: Int): Int = board[index(x, y)]

    fun hasPiece(p: Int, piece: Int): Boolean = left[p][piece]

    /** 남은 조각 수 */
    fun piecesLeft(p: Int): Int = GameConfig.PIECE_COUNT - placedCount[p]

    /** 남은 조각 칸 수 */
    fun cellsLeft(p: Int): Int {
        var s = 0
        for (i in 0 until GameConfig.PIECE_COUNT) if (left[p][i]) s += Pieces.ALL[i].size
        return s
    }

    /** 21개를 모두 놓았는지 */
    fun allPlaced(p: Int): Boolean = placedCount[p] == GameConfig.PIECE_COUNT

    /** 점수 (적을수록 좋음) */
    fun score(p: Int): Int = if (allPlaced(p)) GameConfig.ALL_PLACED_SCORE else cellsLeft(p)

    /** players 순서의 순위 (점수 낮을수록 1위, 동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(List(playerCount) { score(it).toLong() }, higherIsBetter = false)

    /** 아직 첫 조각을 안 놓았는지 */
    fun isFirstMove(p: Int): Boolean = placedCount[p] == 0

    private fun own(x: Int, y: Int, p: Int): Boolean = x in 0 until size && y in 0 until size && board[y * size + x] == p

    /** p 가 piece 를 orient 모양으로 왼쪽 위 (x, y) 에 놓을 수 있는지 (조각 보유·차례는 보지 않음) */
    fun fit(p: Int, piece: Int, orient: Int, x: Int, y: Int): Fit {
        val o = Pieces.ALL[piece].orients[orient]
        if (x < 0 || y < 0 || x + o.w > size || y + o.h > size) return Fit.OUTSIDE
        val first = isFirstMove(p)
        var corner = false
        var touch = false
        for (k in 0 until o.size) {
            val cx = x + o.xs[k]
            val cy = y + o.ys[k]
            val i = cy * size + cx
            if (board[i] != EMPTY) return Fit.OVERLAP
            if (own(cx - 1, cy, p) || own(cx + 1, cy, p) || own(cx, cy - 1, p) || own(cx, cy + 1, p)) return Fit.EDGE
            if (i == corners[p]) corner = true
            if (!touch && (own(cx - 1, cy - 1, p) || own(cx + 1, cy - 1, p) || own(cx - 1, cy + 1, p) || own(cx + 1, cy + 1, p))) touch = true
        }
        if (first) return if (corner) Fit.OK else Fit.NO_CORNER
        return if (touch) Fit.OK else Fit.NO_TOUCH
    }

    /** 지금 차례 사람 기준 판정 (조각 보유·판 종료 포함) */
    fun check(piece: Int, orient: Int, x: Int, y: Int): Fit {
        if (over || out[current] || piece !in 0 until GameConfig.PIECE_COUNT || !left[current][piece]) return Fit.UNAVAILABLE
        if (orient !in Pieces.ALL[piece].orients.indices) return Fit.UNAVAILABLE
        return fit(current, piece, orient, x, y)
    }

    fun canPlace(piece: Int, orient: Int, x: Int, y: Int): Boolean = check(piece, orient, x, y) == Fit.OK

    /** 지금 차례 사람이 조각을 놓는다. 성공하면 true (차례는 advance() 로 넘긴다) */
    fun place(piece: Int, orient: Int, x: Int, y: Int): Boolean {
        if (check(piece, orient, x, y) != Fit.OK) return false
        val p = current
        val o = Pieces.ALL[piece].orients[orient]
        for (k in 0 until o.size) {
            val i = (y + o.ys[k]) * size + x + o.xs[k]
            board[i] = p
            lastCells[k] = i
        }
        lastCount = o.size
        lastPlayer = p
        left[p][piece] = false
        placedCount[p]++
        if (allPlaced(p)) out[p] = true
        return true
    }

    /**
     * 다음 차례로. 차례가 온 사람이 둘 곳이 없으면 자동 패스(out)하고 그다음 사람으로.
     * 새로 패스한 사람들을 순서대로 돌려준다. 아무도 둘 수 없으면 over.
     * (방금 둔 사람 자신에게 다시 차례가 돌아올 수도 있다 — 다른 사람이 모두 끝났을 때)
     */
    fun advance(): List<Int> {
        val passed = ArrayList<Int>()
        if (over) return passed
        var p = current
        repeat(playerCount) {
            p = (p + 1) % playerCount
            if (!out[p]) {
                if (hasMove(p)) {
                    current = p
                    return passed
                }
                out[p] = true
                passed.add(p)
            }
        }
        over = true
        return passed
    }

    // ── 둘 곳 찾기 (꼭짓점 기준 탐색) ──

    /**
     * p 가 새 조각의 칸을 둘 수 있는 "꼭짓점 자리" 표시.
     * 첫 조각이면 자기 모서리, 아니면 빈 칸 중 내 조각과 변으로 안 닿고 꼭짓점으로 닿는 칸.
     * mark[칸] = true, 개수를 돌려준다. (mark 크기 ≥ cells)
     */
    fun anchors(p: Int, mark: BooleanArray): Int {
        java.util.Arrays.fill(mark, 0, cells, false)
        if (allPlaced(p)) return 0
        if (isFirstMove(p)) {
            val c = corners[p]
            if (board[c] != EMPTY) return 0
            mark[c] = true
            return 1
        }
        var n = 0
        for (y in 0 until size) for (x in 0 until size) {
            val i = y * size + x
            if (board[i] != EMPTY) continue
            if (own(x - 1, y, p) || own(x + 1, y, p) || own(x, y - 1, p) || own(x, y + 1, p)) continue
            if (own(x - 1, y - 1, p) || own(x + 1, y - 1, p) || own(x - 1, y + 1, p) || own(x + 1, y + 1, p)) {
                mark[i] = true
                n++
            }
        }
        return n
    }

    private val anchorBuf = BooleanArray(cells)

    /** p 가 둘 수 있는 수 하나 (큰 조각 먼저). 없으면 null */
    fun findMove(p: Int): Move? {
        if (allPlaced(p)) return null
        if (anchors(p, anchorBuf) == 0) return null
        for (piece in GameConfig.PIECE_COUNT - 1 downTo 0) {
            if (!left[p][piece]) continue
            val m = findMoveFor(p, piece)
            if (m != null) return m
        }
        return null
    }

    /** p 가 이 조각을 둘 수 있는 수 하나 (anchorBuf 가 채워진 상태에서) */
    private fun findMoveFor(p: Int, piece: Int): Move? {
        val shape = Pieces.ALL[piece]
        for (a in 0 until cells) {
            if (!anchorBuf[a]) continue
            val ax = a % size
            val ay = a / size
            for (oi in shape.orients.indices) {
                val o = shape.orients[oi]
                // 조각의 칸 k 를 꼭짓점 자리에 맞춘다
                for (k in 0 until o.size) {
                    val x = ax - o.xs[k]
                    val y = ay - o.ys[k]
                    if (fit(p, piece, oi, x, y) == Fit.OK) return Move(piece, oi, x, y)
                }
            }
        }
        return null
    }

    /** p 가 이 조각을 둘 곳이 있는지 */
    fun canUse(p: Int, piece: Int): Boolean {
        if (!left[p][piece]) return false
        if (anchors(p, anchorBuf) == 0) return false
        return findMoveFor(p, piece) != null
    }

    /** p 가 지금 둘 수 있는 수가 하나라도 있는지 */
    fun hasMove(p: Int): Boolean = findMove(p) != null

    /** 판 위 p 의 칸 수 */
    fun count(p: Int): Int = board.count { it == p }

    companion object {
        const val EMPTY = -1
    }
}
