package com.siwoo.party.games.minefield

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 칸 열기 결과 */
sealed class OpenResult {
    /** 무시 (판 밖·이미 열린 칸·터진 지뢰·게임 끝) */
    object Ignored : OpenResult()
    /** 깃발이 꽂힌 칸 — 먼저 깃발을 뽑아야 열 수 있다 (차례 그대로) */
    class Blocked(val cell: Int) : OpenResult()
    /**
     * 안전: cells 를 열었다 (BFS 순서, dist 는 같은 순서의 시작 칸으로부터 거리).
     * gained = 이번에 얻은 점수(= 연 칸 수). next = 다음 차례(게임이 끝났으면 -1)
     */
    class Safe(val player: Int, val cells: IntArray, val dist: IntArray, val gained: Int, val next: Int, val gameOver: Boolean) : OpenResult()
    /** 지뢰를 밟음: player 탈락. next = 다음 차례(게임이 끝났으면 -1) */
    class Boom(val player: Int, val cell: Int, val next: Int, val gameOver: Boolean) : OpenResult()
}

/** 깃발 토글 결과 */
enum class FlagResult { ADDED, REMOVED, IGNORED }

/** 끝난 까닭 */
enum class EndReason { NONE, LAST_SURVIVOR, CLEARED }

/**
 * 지뢰 피하기 규칙 (함께 하는 지뢰 찾기).
 * - cols × rows 판, 지뢰 mines 개. 지뢰는 첫 칸을 연 순간에 깔린다:
 *   첫 칸과 그 주변(FIRST_SAFE_RADIUS)에는 지뢰가 없다 → 첫 칸은 언제나 0 이라 넓게 열린다
 * - 차례인 사람이 칸 하나를 연다. 숫자 칸이면 그 칸 1개, 0 칸이면 이어진 0 지역과 그 둘레 숫자 칸까지 자동으로 열린다.
 *   연 칸 수가 그대로 점수. 그다음 살아 있는 다음 사람 차례
 *   (0 지역에 잘못 꽂힌 깃발이 있으면 그 칸도 깃발을 뽑고 함께 열린다 — 0 칸 옆은 언제나 안전하므로)
 * - 깃발: 차례 소모 없이 숨은 칸에 꽂거나 뽑는다. 모두가 같이 쓰는 표시(누가 꽂았는지 기록). 깃발 칸은 직접 열 수 없다
 * - 지뢰를 밟으면 그 사람 탈락, 지뢰는 터진 채로 판에 남고 다음 사람 차례
 * - 끝: 살아남은 사람이 1명 이하이거나, 안전한 칸이 모두 열리면
 * - 순위: 살아남은 사람이 위(점수 많은 순, 같으면 공동), 탈락한 사람은 아래(늦게 탈락할수록 위)
 * 난수는 밖에서 주입 → 테스트에서 같은 배치를 재현할 수 있다.
 */
class MinefieldGame(
    val playerCount: Int,
    private val random: Random = Random.Default,
    firstPlayer: Int = 0,
    val cols: Int = GameConfig.COLS,
    val rows: Int = GameConfig.ROWS,
    val mines: Int = GameConfig.MINES,
) {
    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
        require(firstPlayer in 0 until playerCount)
        require(mines in 1 until cols * rows - 9)
    }

    val cellCount = cols * rows
    val safeTotal = cellCount - mines

    private val mine = BooleanArray(cellCount)
    private val adj = IntArray(cellCount)
    /** 열린 안전 칸 */
    private val opened = BooleanArray(cellCount)
    /** 칸을 연 사람 (-1 = 아직) */
    private val openedBy = IntArray(cellCount) { -1 }
    /** 깃발을 꽂은 사람 (-1 = 깃발 없음) */
    private val flagBy = IntArray(cellCount) { -1 }
    /** 터진 지뢰: 밟은 사람 (-1 = 안 터짐) */
    private val boomBy = IntArray(cellCount) { -1 }

    var minesPlaced = false
        private set

    /** 플레이어별 점수 (연 칸 수) */
    val scores = IntArray(playerCount)
    /** 살아 있는지 */
    val alive = BooleanArray(playerCount) { true }
    /** 탈락 순서 (0 = 처음 탈락, -1 = 생존) */
    val elimOrder = IntArray(playerCount) { -1 }
    var eliminatedCount = 0
        private set

    var current = firstPlayer
        private set
    var safeOpened = 0
        private set
    var flagCount = 0
        private set
    var boomCount = 0
        private set
    var endReason = EndReason.NONE
        private set

    val gameOver: Boolean get() = endReason != EndReason.NONE
    val aliveCount: Int get() = playerCount - eliminatedCount
    /** 아직 드러나지 않은(깃발로도 표시 안 된) 지뢰 수 추정: 지뢰 − 깃발 − 터진 지뢰 */
    val minesLeftEstimate: Int get() = mines - flagCount - boomCount

    // ── 칸 조회 ──
    fun col(k: Int): Int = k % cols
    fun row(k: Int): Int = k / cols
    fun index(col: Int, row: Int): Int = row * cols + col
    fun isMine(k: Int): Boolean = mine[k]
    /** 주변 8칸의 지뢰 수 (지뢰가 깔린 뒤에만 의미 있음) */
    fun adjacent(k: Int): Int = adj[k]
    fun isOpen(k: Int): Boolean = opened[k]
    fun openedBy(k: Int): Int = openedBy[k]
    fun isFlagged(k: Int): Boolean = flagBy[k] >= 0
    fun flagOwner(k: Int): Int = flagBy[k]
    fun isExploded(k: Int): Boolean = boomBy[k] >= 0
    fun explodedBy(k: Int): Int = boomBy[k]
    /** 아직 숨은 칸 (열리지도 터지지도 않음, 깃발은 상관없음) */
    fun isHidden(k: Int): Boolean = !opened[k] && boomBy[k] < 0

    /** 주변 칸 번호를 out 에 담고 개수를 돌려준다 (할당 없음) */
    fun neighbors(k: Int, out: IntArray): Int {
        val c = col(k)
        val r = row(k)
        var n = 0
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val cc = c + dc
            val rr = r + dr
            if (cc < 0 || rr < 0 || cc >= cols || rr >= rows) continue
            out[n++] = index(cc, rr)
        }
        return n
    }

    private val nb = IntArray(8)

    /**
     * 지뢰를 직접 깐다 (테스트·재현용). 첫 칸을 열기 전에만 가능.
     * 개수는 mines 와 같아야 한다.
     */
    fun placeMines(cells: Collection<Int>) {
        check(!minesPlaced) { "이미 지뢰가 깔림" }
        require(cells.toSet().size == mines && cells.all { it in 0 until cellCount })
        for (k in cells) mine[k] = true
        finishPlacement()
    }

    /** 첫 칸 first 와 그 주변을 비우고 무작위로 깐다 */
    private fun placeMinesAvoiding(first: Int) {
        val fc = col(first)
        val fr = row(first)
        val rad = GameConfig.FIRST_SAFE_RADIUS
        val candidates = (0 until cellCount).filter { k ->
            kotlin.math.abs(col(k) - fc) > rad || kotlin.math.abs(row(k) - fr) > rad
        }.shuffled(random)
        for (i in 0 until mines) mine[candidates[i]] = true
        finishPlacement()
    }

    private fun finishPlacement() {
        minesPlaced = true
        for (k in 0 until cellCount) {
            val n = neighbors(k, nb)
            var m = 0
            for (i in 0 until n) if (mine[nb[i]]) m++
            adj[k] = m
        }
    }

    /** 지금 차례인 사람이 칸 k 를 연다 */
    fun open(k: Int): OpenResult {
        if (gameOver || k !in 0 until cellCount) return OpenResult.Ignored
        if (opened[k] || boomBy[k] >= 0) return OpenResult.Ignored
        if (flagBy[k] >= 0) return OpenResult.Blocked(k)
        if (!minesPlaced) placeMinesAvoiding(k)
        val p = current
        if (mine[k]) {
            boomBy[k] = p
            boomCount++
            alive[p] = false
            elimOrder[p] = eliminatedCount
            eliminatedCount++
            if (aliveCount <= 1) endReason = EndReason.LAST_SURVIVOR
            val next = advance()
            return OpenResult.Boom(p, k, next, gameOver)
        }
        // BFS: 0 칸에서만 퍼진다. 둘레 숫자 칸까지 열고 멈춤
        val cells = IntArray(cellCount)
        val dist = IntArray(cellCount)
        var head = 0
        var tail = 0
        openCell(k, p)
        cells[tail] = k; dist[tail] = 0; tail++
        while (head < tail) {
            val cur = cells[head]
            val d = dist[head]
            head++
            if (adj[cur] != 0) continue
            val n = neighbors(cur, nb)
            for (i in 0 until n) {
                val q = nb[i]
                if (opened[q] || mine[q] || boomBy[q] >= 0) continue
                openCell(q, p)
                cells[tail] = q; dist[tail] = d + 1; tail++
            }
        }
        scores[p] += tail
        if (safeOpened >= safeTotal) endReason = EndReason.CLEARED
        val next = advance()
        return OpenResult.Safe(p, cells.copyOf(tail), dist.copyOf(tail), tail, next, gameOver)
    }

    private fun openCell(q: Int, p: Int) {
        if (flagBy[q] >= 0) { flagBy[q] = -1; flagCount-- } // 잘못 꽂힌 깃발은 뽑고 연다
        opened[q] = true
        openedBy[q] = p
        safeOpened++
    }

    /** 다음 살아 있는 사람에게 차례를 넘긴다 (끝났으면 -1) */
    private fun advance(): Int {
        if (gameOver) return -1
        var n = current
        do { n = (n + 1) % playerCount } while (!alive[n])
        current = n
        return n
    }

    /** 지금 차례인 사람이 칸 k 에 깃발을 꽂거나 뽑는다 (차례 소모 없음) */
    fun toggleFlag(k: Int): FlagResult {
        if (gameOver || k !in 0 until cellCount) return FlagResult.IGNORED
        if (opened[k] || boomBy[k] >= 0) return FlagResult.IGNORED
        return if (flagBy[k] >= 0) {
            flagBy[k] = -1
            flagCount--
            FlagResult.REMOVED
        } else {
            flagBy[k] = current
            flagCount++
            FlagResult.ADDED
        }
    }

    /** 끝났을 때 아직 숨은 지뢰 (드러내기 연출용) */
    fun hiddenMines(): List<Int> = (0 until cellCount).filter { mine[it] && boomBy[it] < 0 }

    /**
     * 최종 순위 (1부터, 동점 허용):
     * 생존자 = SURVIVOR_BONUS + 점수 (점수 같으면 공동), 탈락자 = 탈락 순서(늦을수록 큼) → 생존자 아래.
     */
    fun finalRanks(): IntArray = Ranking.competition(List(playerCount) { rankKey(it) })

    private fun rankKey(i: Int): Long =
        if (alive[i]) GameConfig.SURVIVOR_BONUS + scores[i] else elimOrder[i].toLong()
}
