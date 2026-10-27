package com.siwoo.party.games.gomoku

/** 진행 단계 */
enum class Phase { PLAY, OVER }

/** 판 터치 결과 */
enum class Tap {
    /** 빈 곳에 미리보기 돌을 새로 띄움 */
    PREVIEW,
    /** 다른 빈 곳을 눌러 미리보기를 옮김 */
    MOVED,
    /** 미리보기 자리를 한 번 더 눌러 돌을 놓음 */
    PLACED,
    /** 이미 돌이 있는 자리 (무시) */
    OCCUPIED,
    /** 판 밖 / 끝난 판 (무시) */
    IGNORED,
}

/**
 * 오목 규칙 (2인, 금지수 없음). 순수 Kotlin.
 * - 칸 번호 = y * size + x. board[칸] = EMPTY 또는 놓은 사람(플레이어 번호 0/1)
 * - 먼저 두는 사람(first)이 흑. 차례는 번갈아.
 * - 가로·세로·대각선으로 WIN_LENGTH 개 이상 이으면 승리 (장목도 승리). 판이 다 차면 무승부.
 * - 오터치 방지: tap() 으로 처음 누르면 미리보기, 같은 자리를 다시 누르면 놓는다. 다른 빈 곳을 누르면 미리보기가 옮겨 간다.
 * - 무르기: 방금 둔 사람이 상대가 두기 전에 자기 마지막 돌을 물릴 수 있다 (한 판에 UNDO_PER_PLAYER 번, undoEnabled 일 때).
 */
class GomokuGame(
    /** 먼저 두는 사람 (흑) */
    val first: Int,
    val undoEnabled: Boolean = GameConfig.UNDO_DEFAULT,
    val size: Int = GameConfig.GOMOKU_SIZE,
) {
    init {
        require(first in 0 until GameConfig.PLAYERS)
        require(size >= 1)
    }

    val cells = size * size
    val board = IntArray(cells) { EMPTY }

    var phase = Phase.PLAY
        private set
    /** 지금 둘 사람 */
    var current = first
        private set
    /** 이긴 사람 (−1 = 아직 없음 / 무승부) */
    var winner = -1
        private set
    val isDraw: Boolean get() = phase == Phase.OVER && winner < 0

    /** 승리한 줄: 한쪽 끝부터 순서대로 칸 번호 (winLength 개) */
    val winLine = IntArray(size.coerceAtLeast(GameConfig.WIN_LENGTH))
    var winLength = 0
        private set

    /** 미리보기 칸 (−1 = 없음) */
    var preview = -1
        private set

    private val moves = IntArray(cells)
    /** 지금까지 판에 놓인 돌 수 */
    var moveCount = 0
        private set
    /** 마지막으로 놓인 칸 (−1 = 없음) */
    val lastMove: Int get() = if (moveCount > 0) moves[moveCount - 1] else -1

    /** 남은 무르기 횟수 */
    val undosLeft = IntArray(GameConfig.PLAYERS) { if (undoEnabled) GameConfig.UNDO_PER_PLAYER else 0 }

    /** 플레이어 p 의 돌이 흑인지 */
    fun isBlack(p: Int): Boolean = p == first

    fun index(x: Int, y: Int): Int = y * size + x
    fun inside(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size
    fun at(x: Int, y: Int): Int = board[index(x, y)]

    /** 판 터치 (오터치 방지 2단계) */
    fun tap(x: Int, y: Int): Tap {
        if (phase != Phase.PLAY || !inside(x, y)) return Tap.IGNORED
        val i = index(x, y)
        if (board[i] != EMPTY) return Tap.OCCUPIED
        if (preview == i) {
            place(x, y)
            return Tap.PLACED
        }
        val had = preview >= 0
        preview = i
        return if (had) Tap.MOVED else Tap.PREVIEW
    }

    /** 미리보기 지우기 */
    fun clearPreview() {
        preview = -1
    }

    /** 지금 차례인 사람이 (x, y) 에 바로 놓기 (미리보기 없이 — 테스트·내부용). 놓았으면 true */
    fun place(x: Int, y: Int): Boolean {
        if (phase != Phase.PLAY || !inside(x, y)) return false
        val i = index(x, y)
        if (board[i] != EMPTY) return false
        val p = current
        board[i] = p
        moves[moveCount++] = i
        preview = -1
        val len = lineThrough(x, y, winLine)
        if (len >= GameConfig.WIN_LENGTH) {
            winLength = len
            winner = p
            phase = Phase.OVER
        } else if (moveCount == cells) {
            phase = Phase.OVER
        } else {
            current = 1 - p
        }
        return true
    }

    /** 플레이어 p 가 지금 무를 수 있는지: 방금 둔 사람이 p 이고, 상대가 아직 안 뒀고, 횟수가 남음 */
    fun canUndo(p: Int): Boolean =
        phase == Phase.PLAY && moveCount > 0 && board[lastMove] == p && current != p && undosLeft[p] > 0

    /** 무르기: p 의 마지막 돌을 빼고 다시 p 의 차례. 했으면 true */
    fun undo(p: Int): Boolean {
        if (!canUndo(p)) return false
        val i = moves[--moveCount]
        board[i] = EMPTY
        undosLeft[p]--
        current = p
        preview = -1
        return true
    }

    /** 순위 (players 순서): 이긴 사람 1위·진 사람 2위, 무승부는 둘 다 1위 */
    fun ranks(): IntArray =
        if (winner < 0) IntArray(GameConfig.PLAYERS) { 1 } else IntArray(GameConfig.PLAYERS) { if (it == winner) 1 else 2 }

    /**
     * (x, y) 의 돌을 지나는 가장 긴 줄 (가로·세로·대각선 넷 중). out 에 한쪽 끝부터 순서대로 칸 번호를 담고 길이를 돌려준다.
     * 빈 칸이면 0.
     */
    fun lineThrough(x: Int, y: Int, out: IntArray): Int {
        val who = at(x, y)
        if (who == EMPTY) return 0
        var bestLen = 0
        var bestDir = 0
        var bestStart = 0
        for (d in 0 until 4) {
            val dx = DX[d]
            val dy = DY[d]
            // 뒤쪽 끝 찾기
            var sx = x
            var sy = y
            while (inside(sx - dx, sy - dy) && at(sx - dx, sy - dy) == who) { sx -= dx; sy -= dy }
            var len = 1
            while (inside(sx + dx * len, sy + dy * len) && at(sx + dx * len, sy + dy * len) == who) len++
            if (len > bestLen) {
                bestLen = len
                bestDir = d
                bestStart = index(sx, sy)
            }
        }
        val sx = bestStart % size
        val sy = bestStart / size
        val n = minOf(bestLen, out.size)
        for (k in 0 until n) out[k] = index(sx + DX[bestDir] * k, sy + DY[bestDir] * k)
        return bestLen
    }

    companion object {
        const val EMPTY = -1
        /** 방향: 가로 →, 세로 ↓, 대각선 ↘, 대각선 ↙ */
        private val DX = intArrayOf(1, 0, 1, -1)
        private val DY = intArrayOf(0, 1, 1, 1)
    }
}
