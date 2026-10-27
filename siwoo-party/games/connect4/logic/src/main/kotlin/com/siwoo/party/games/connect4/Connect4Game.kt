package com.siwoo.party.games.connect4

import kotlin.math.sqrt

/** 진행 단계 */
enum class Phase { PLAY, OVER }

/** 열에 돌 떨어뜨리기 결과 */
enum class Drop {
    /** 놓임 (lastCol / lastRow) */
    PLACED,
    /** 그 열이 꽉 참 (거절) */
    FULL,
    /** 없는 열 */
    OUT,
    /** 이미 끝난 판 */
    OVER,
}

/**
 * 사목 떨어뜨리기 규칙 (2인). 순수 Kotlin.
 * - 칸 번호 = row * cols + col, row 0 = 맨 아래 줄. board[칸] = EMPTY 또는 놓은 사람(0/1)
 * - 열을 고르면 그 열의 가장 낮은 빈 칸에 돌이 쌓인다. 차례는 번갈아. 먼저 두는 사람(first)은 화면이 정한다(무작위).
 * - 가로·세로·대각선으로 WIN_LENGTH 개 이상 이으면 승리. 판이 다 차면 무승부(둘 다 1위).
 */
class Connect4Game(
    val first: Int,
    val cols: Int = GameConfig.CONNECT4_COLS,
    val rows: Int = GameConfig.CONNECT4_ROWS,
) {
    init {
        require(first in 0 until GameConfig.PLAYERS)
        require(cols >= 1 && rows >= 1)
    }

    val cells = cols * rows
    val board = IntArray(cells) { EMPTY }
    /** 열마다 쌓인 돌 수 */
    val heights = IntArray(cols)
    private val placed = IntArray(GameConfig.PLAYERS)

    var phase = Phase.PLAY
        private set
    var current = first
        private set
    var winner = -1
        private set
    val isDraw: Boolean get() = phase == Phase.OVER && winner < 0

    /** 승리 줄: 한쪽 끝부터 순서대로 칸 번호 (winLength 개, 4개보다 길 수 있음) */
    val winLine = IntArray(maxOf(cols, rows))
    var winLength = 0
        private set

    var moveCount = 0
        private set
    var lastCol = -1
        private set
    var lastRow = -1
        private set

    fun index(col: Int, row: Int): Int = row * cols + col
    fun inside(col: Int, row: Int): Boolean = col in 0 until cols && row in 0 until rows
    fun at(col: Int, row: Int): Int = board[index(col, row)]
    fun isFull(col: Int): Boolean = heights[col] >= rows

    /** 플레이어 p 에게 남은 돌 수 (먼저 두는 사람이 홀수 칸이면 하나 더) */
    fun discsLeft(p: Int): Int = (if (p == first) (cells + 1) / 2 else cells / 2) - placed[p]

    /** 지금 차례인 사람이 col 열에 떨어뜨리기 */
    fun drop(col: Int): Drop {
        if (phase != Phase.PLAY) return Drop.OVER
        if (col !in 0 until cols) return Drop.OUT
        if (isFull(col)) return Drop.FULL
        val p = current
        val row = heights[col]++
        board[index(col, row)] = p
        placed[p]++
        moveCount++
        lastCol = col
        lastRow = row
        val len = lineThrough(col, row, winLine)
        if (len >= GameConfig.WIN_LENGTH) {
            winLength = len
            winner = p
            phase = Phase.OVER
        } else if (moveCount == cells) {
            phase = Phase.OVER
        } else {
            current = 1 - p
        }
        return Drop.PLACED
    }

    /** 순위 (players 순서): 이긴 사람 1위·진 사람 2위, 무승부는 둘 다 1위 */
    fun ranks(): IntArray =
        if (winner < 0) IntArray(GameConfig.PLAYERS) { 1 } else IntArray(GameConfig.PLAYERS) { if (it == winner) 1 else 2 }

    /** (col, row) 의 돌을 지나는 가장 긴 줄. out 에 한쪽 끝부터 칸 번호, 길이를 돌려줌 (빈 칸이면 0) */
    fun lineThrough(col: Int, row: Int, out: IntArray): Int {
        val who = at(col, row)
        if (who == EMPTY) return 0
        var bestLen = 0
        var bestDir = 0
        var bestStart = 0
        for (d in 0 until 4) {
            val dc = DC[d]
            val dr = DR[d]
            var sc = col
            var sr = row
            while (inside(sc - dc, sr - dr) && at(sc - dc, sr - dr) == who) { sc -= dc; sr -= dr }
            var len = 1
            while (inside(sc + dc * len, sr + dr * len) && at(sc + dc * len, sr + dr * len) == who) len++
            if (len > bestLen) {
                bestLen = len
                bestDir = d
                bestStart = index(sc, sr)
            }
        }
        val sc = bestStart % cols
        val sr = bestStart / cols
        val n = minOf(bestLen, out.size)
        for (k in 0 until n) out[k] = index(sc + DC[bestDir] * k, sr + DR[bestDir] * k)
        return bestLen
    }

    companion object {
        const val EMPTY = -1
        /** 방향: 가로 →, 세로 ↑, 대각선 ↗, 대각선 ↘ */
        private val DC = intArrayOf(1, 0, 1, 1)
        private val DR = intArrayOf(0, 1, 1, -1)
    }
}

/**
 * 떨어지며 튕기는 돌의 위치 (칸 단위). 거리 dist 칸을 정지 상태에서 떨어져 바닥에 닿고,
 * RESTITUTION 비율로 최대 MAX_BOUNCES 번 되튄 뒤 멈춘다. 할당 없음 (그리기 중에 매 프레임 불러도 됨).
 * 시간 t 는 배율 1 기준 ms — 화면은 (지난 시간 / 속도 배율) 을 넣는다.
 */
object Bounce {

    /** 바닥에 처음 닿는 시간 (ms) */
    fun fallMs(dist: Float): Float = if (dist <= 0f) 0f else sqrt(2f * dist / GameConfig.GRAVITY) * 1000f

    /** 처음 닿는 속도 (칸/초) */
    private fun impact(dist: Float): Float = sqrt(2f * GameConfig.GRAVITY * maxOf(dist, 0f))

    /** 튕김 k 번째(1부터)의 되튀는 속도 — 너무 느리면 0 */
    private fun bounceSpeed(dist: Float, k: Int): Float {
        if (k > GameConfig.MAX_BOUNCES) return 0f
        var v = impact(dist)
        for (i in 0 until k) v *= GameConfig.RESTITUTION
        return if (v < GameConfig.MIN_BOUNCE_SPEED) 0f else v
    }

    /** 전체 연출 시간 (ms, 배율 1) */
    fun totalMs(dist: Float): Float {
        var total = fallMs(dist)
        for (k in 1..GameConfig.MAX_BOUNCES) {
            val v = bounceSpeed(dist, k)
            if (v <= 0f) break
            total += 2f * v / GameConfig.GRAVITY * 1000f
        }
        return total
    }

    /** t ms 뒤 출발점에서 내려온 거리 (0 → dist, 튕길 때는 dist 보다 조금 위) */
    fun y(tMs: Float, dist: Float): Float {
        if (dist <= 0f) return 0f
        val g = GameConfig.GRAVITY
        var t = tMs / 1000f
        if (t <= 0f) return 0f
        val tf = fallMs(dist) / 1000f
        if (t < tf) return 0.5f * g * t * t
        t -= tf
        for (k in 1..GameConfig.MAX_BOUNCES) {
            val v = bounceSpeed(dist, k)
            if (v <= 0f) break
            val tb = 2f * v / g
            if (t < tb) return dist - (v * t - 0.5f * g * t * t)
            t -= tb
        }
        return dist
    }

    /** 몇 번째 튕김 중인지 (0 = 처음 떨어지는 중, 끝나면 −1) — 소리 내기용 */
    fun phase(tMs: Float, dist: Float): Int {
        if (dist <= 0f) return -1
        var t = tMs / 1000f
        val tf = fallMs(dist) / 1000f
        if (t < tf) return 0
        t -= tf
        for (k in 1..GameConfig.MAX_BOUNCES) {
            val v = bounceSpeed(dist, k)
            if (v <= 0f) break
            val tb = 2f * v / GameConfig.GRAVITY
            if (t < tb) return k
            t -= tb
        }
        return -1
    }
}
