package com.siwoo.party.games.boxes

import com.siwoo.party.common.Ranking

/** 선 긋기 결과 */
sealed class Move {
    /** 무시: 이미 그은 선·없는 선·게임 끝 */
    object Rejected : Move()

    /**
     * 선을 그음.
     * @param boxes 이 선으로 완성된 칸 (0~2개, player 차지)
     * @param keepsTurn 칸을 하나라도 완성하면 같은 사람이 한 번 더
     * @param next 다음 차례 (keepsTurn 이면 player 그대로)
     * @param gameOver 마지막 선이었음
     */
    class Drawn(val player: Int, val line: Int, val boxes: IntArray, val keepsTurn: Boolean, val next: Int, val gameOver: Boolean) : Move()
}

/**
 * 땅따먹기(점과 상자) 규칙.
 * - 2인 6×6 점(5×5칸), 3~4인 7×7 점(6×6칸)
 * - 차례인 사람이 이웃한 두 점 사이에 선을 하나 긋는다
 * - 그 선으로 칸의 네 변이 모두 이어지면 그 칸을 차지하고 한 번 더 (한 선으로 2칸까지)
 *   칸을 못 만들면 다음 사람 (players 순서대로 돈다)
 * - 모든 선을 그으면 끝, 순위는 차지한 칸이 많은 순 (같으면 공동 순위)
 */
class BoxesGame(val playerCount: Int, dots: Int = BoxesGrid.dotsFor(playerCount)) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val grid = BoxesGrid(dots)

    /** 그은 선 */
    val drawn = BooleanArray(grid.lineCount)
    /** 선을 그은 사람 (-1 = 아직) */
    val lineOwner = IntArray(grid.lineCount) { -1 }
    /** 칸 주인 (-1 = 아직) */
    val boxOwner = IntArray(grid.boxCount) { -1 }
    /** 플레이어별 차지한 칸 수 */
    val scores = IntArray(playerCount)

    /** 지금 차례 */
    var current = 0
        private set
    /** 마지막으로 그은 선 (-1 = 없음) */
    var lastLine = -1
        private set
    /** 그은 선 수 */
    var drawnCount = 0
        private set

    val gameOver: Boolean get() = drawnCount >= grid.lineCount

    fun isDrawn(line: Int): Boolean = drawn[line]

    /** 네 변이 모두 그어졌는지 */
    fun isClosed(box: Int): Boolean =
        drawn[grid.top(box)] && drawn[grid.bottom(box)] && drawn[grid.left(box)] && drawn[grid.right(box)]

    /** 지금 차례인 사람이 line 을 긋는다 */
    fun draw(line: Int): Move {
        if (gameOver || line !in 0 until grid.lineCount || drawn[line]) return Move.Rejected
        val p = current
        drawn[line] = true
        lineOwner[line] = p
        lastLine = line
        drawnCount++
        val a = claim(grid.boxBefore(line), p)
        val b = claim(grid.boxAfter(line), p)
        val boxes = when {
            a >= 0 && b >= 0 -> intArrayOf(a, b)
            a >= 0 -> intArrayOf(a)
            b >= 0 -> intArrayOf(b)
            else -> NONE
        }
        val keeps = boxes.isNotEmpty()
        if (!keeps && !gameOver) current = (current + 1) % playerCount
        return Move.Drawn(p, line, boxes, keeps, current, gameOver)
    }

    /** 칸이 막 완성됐으면 p 가 차지하고 번호를, 아니면 -1 */
    private fun claim(box: Int, p: Int): Int {
        if (box < 0 || boxOwner[box] >= 0 || !isClosed(box)) return -1
        boxOwner[box] = p
        scores[p]++
        return box
    }

    /** player 가 차지한 칸 (판 순서: 위 줄부터) */
    fun boxesOf(player: Int): IntArray {
        val out = IntArray(scores[player])
        var k = 0
        for (b in boxOwner.indices) if (boxOwner[b] == player) out[k++] = b
        return out
    }

    /** 최종 순위 (1부터, 동점 허용): 칸 수가 많은 순 */
    fun finalRanks(): IntArray = Ranking.competition(scores.map { it.toLong() })

    companion object {
        private val NONE = IntArray(0)
    }
}
