package com.siwoo.party.games.flood

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 색 고르기 결과 */
enum class Pick {
    /** 골랐음 → [FloodGame.lastFlood] */
    OK,
    /** 지금 내 색 (바꿔도 의미 없음) */
    OWN_COLOR,
    /** 다른 사람이 지금 쓰는 색 */
    TAKEN,
    /** 없는 색 번호 */
    BAD_COLOR,
    /** 이미 끝남 */
    OVER,
}

/** 왜 끝났는지 */
enum class EndReason { NONE, ALL_OWNED, ROUNDS }

/**
 * 한 번 색을 고른 결과 (화면 연출용).
 * - recolored: 원래 내 땅 (본진에서 가까운 순), recolorDepth: 본진에서의 거리(0 = 본진)
 * - absorbed: 새로 흡수한 칸 (원래 땅에서 가까운 순), absorbDepth: 원래 땅에서의 거리(1부터)
 */
class Flood(
    val player: Int,
    val color: Int,
    val oldColor: Int,
    val recolored: IntArray,
    val recolorDepth: IntArray,
    val absorbed: IntArray,
    val absorbDepth: IntArray,
    /** 이 수로 새 바퀴가 시작됨 */
    val newRound: Boolean,
    val gameOver: Boolean,
) {
    val gain: Int get() = absorbed.size
    val maxRecolorDepth: Int get() = if (recolorDepth.isEmpty()) 0 else recolorDepth[recolorDepth.size - 1]
    val maxAbsorbDepth: Int get() = if (absorbDepth.isEmpty()) 0 else absorbDepth[absorbDepth.size - 1]
}

/**
 * 색 번지기 점령 규칙 (순수 Kotlin).
 *
 * - size × size 격자, 칸마다 색(0 until colorCount). 플레이어는 모서리에서 시작한다:
 *   2인 = 왼쪽 위·오른쪽 아래, 3인 = 왼쪽 위·오른쪽 위·오른쪽 아래, 4인 = 왼쪽 위부터 시계 방향 네 모서리.
 * - 시작 땅 = 내 모서리 칸 + 그 칸과 이어진 같은 색 칸 전부. 모서리 칸 색은 사람마다 다르다(만들 때 맞춤).
 * - 내 차례: 색 하나를 고른다. 지금 내 색·다른 사람의 지금 색은 고를 수 없다.
 *   → 내 땅 전체가 그 색으로 바뀌고, 내 땅과 (상하좌우로) 이어진 "주인 없는" 그 색 칸을 이어지는 데까지 흡수한다.
 *   다른 사람 땅은 절대 빼앗지 않는다.
 * - 고를 색이 하나도 없으면(색 가짓수가 적을 때만 가능) [pass] 로 넘어간다.
 * - 모든 칸에 주인이 생기거나 maxRounds 바퀴가 끝나면 끝. 땅이 넓은 순 (동점 같은 순위).
 *
 * 차례: first 부터 0,1,2… 순서로 돌고, 다시 first 차례가 오면 새 바퀴.
 */
class FloodGame(
    val players: Int,
    initialColors: IntArray,
    val first: Int = 0,
    val size: Int = GameConfig.SIZE,
    val colorCount: Int = GameConfig.COLORS,
    val maxRounds: Int = GameConfig.MAX_ROUNDS,
) {
    val cellCount = size * size

    private val color = initialColors.copyOf()
    private val owner = IntArray(cellCount) { NONE }
    /** 사람별 본진(모서리) 칸 */
    val home: IntArray = corners(players, size)
    private val area = IntArray(players)

    /** 지금 차례 */
    var current = first
        private set
    /** 지금 바퀴 (1부터, 끝나면 마지막 바퀴 그대로) */
    var round = 1
        private set
    /** 지금까지 둔 수 (넘김 포함) */
    var moves = 0
        private set
    var endReason = EndReason.NONE
        private set
    val gameOver: Boolean get() = endReason != EndReason.NONE
    /** 마지막으로 고른 결과 */
    var lastFlood: Flood? = null
        private set

    // BFS 작업용 (매번 만들지 않게)
    private val queue = IntArray(cellCount)
    private val dist = IntArray(cellCount)

    init {
        require(players in 1..4) { "인원은 1~4" }
        require(initialColors.size == cellCount) { "칸 수가 맞지 않음" }
        require(first in 0 until players)
        for (c in color) require(c in 0 until colorCount) { "색 번호 범위 밖" }
        for (p in 0 until players) for (q in 0 until p) {
            require(color[home[p]] != color[home[q]]) { "모서리 색이 겹침" }
        }
        for (p in 0 until players) {
            owner[home[p]] = p
            area[p] = 1 + spread(p, color[home[p]], null, null)
        }
    }

    fun colorAt(cell: Int): Int = color[cell]
    fun ownerAt(cell: Int): Int = owner[cell]
    fun colorAt(row: Int, col: Int): Int = color[row * size + col]
    fun ownerAt(row: Int, col: Int): Int = owner[row * size + col]

    /** p 의 땅 칸 수 */
    fun territory(p: Int): Int = area[p]
    /** 주인 있는 칸 수 */
    val ownedCount: Int get() = area.sum()

    /** p 의 지금 색 (= 본진 칸 색, 땅 전체가 이 색) */
    fun colorOf(p: Int): Int = color[home[p]]

    /** 지금 이 색을 쓰는 사람 (-1 = 없음) */
    fun holderOf(c: Int): Int {
        for (p in 0 until players) if (colorOf(p) == c) return p
        return NONE
    }

    /** p 가 c 를 고를 수 있는지 */
    fun isAllowed(p: Int, c: Int): Boolean = c in 0 until colorCount && holderOf(c) == NONE

    /** p 가 고를 수 있는 색들 */
    fun allowedColors(p: Int): List<Int> = (0 until colorCount).filter { isAllowed(p, it) }

    /** 지금 차례가 고를 색이 있는지 (없으면 넘김) */
    val canPick: Boolean get() = !gameOver && (0 until colorCount).any { isAllowed(current, it) }

    /** p 가 c 를 고르면 몇 칸을 흡수하는지 (판은 그대로) */
    fun gainIf(p: Int, c: Int): Int {
        if (!isAllowed(p, c)) return 0
        return spread(p, c, null, null, claim = false)
    }

    /** p 가 c 를 고르면 흡수할 칸들 (미리보기용, 판은 그대로; 못 고르는 색이면 빈 배열) */
    fun gainCells(p: Int, c: Int): IntArray {
        if (!isAllowed(p, c)) return IntArray(0)
        val out = IntArray(cellCount)
        val n = spread(p, c, out, null, claim = false)
        return out.copyOf(n)
    }

    /** 지금 차례가 색 c 를 고름 */
    fun choose(c: Int): Pick {
        if (gameOver) return Pick.OVER
        if (c !in 0 until colorCount) return Pick.BAD_COLOR
        val p = current
        if (colorOf(p) == c) return Pick.OWN_COLOR
        if (holderOf(c) != NONE) return Pick.TAKEN
        val old = colorOf(p)

        // 원래 땅: 본진에서의 거리 순 (색 바꾸기 물결용)
        val n0 = territoryByDistance(p)
        val recolored = queue.copyOf(n0)
        val recolorDepth = IntArray(n0) { dist[recolored[it]] }
        for (cell in recolored) color[cell] = c

        // 흡수: 원래 땅 전체에서 동시에 퍼지는 BFS (거리 순)
        val tmpCells = IntArray(cellCount)
        val tmpDepth = IntArray(cellCount)
        val gained = spread(p, c, tmpCells, tmpDepth)
        area[p] += gained
        val absorbed = tmpCells.copyOf(gained)
        val absorbDepth = tmpDepth.copyOf(gained)

        val newRound = advance()
        val f = Flood(p, c, old, recolored, recolorDepth, absorbed, absorbDepth, newRound, gameOver)
        lastFlood = f
        return Pick.OK
    }

    /** 고를 색이 없을 때 넘김 (고를 색이 있으면 false) */
    fun pass(): Boolean {
        if (gameOver || canPick) return false
        lastFlood = null
        advance()
        return true
    }

    /** 순위 (players 순서, 1부터, 땅 넓은 순, 동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(List(players) { area[it].toLong() })

    /** 다음 차례로. 새 바퀴가 시작되면 true. 끝 판정도 여기서 */
    private fun advance(): Boolean {
        moves++
        if (ownedCount >= cellCount) {
            endReason = EndReason.ALL_OWNED
            return false
        }
        current = (current + 1) % players
        if (current == first) {
            if (round >= maxRounds) {
                endReason = EndReason.ROUNDS
                return false
            }
            round++
            return true
        }
        return false
    }

    /**
     * p 의 땅에서 상하좌우로 이어진 "주인 없는 c 색" 칸을 BFS 로 찾는다.
     * claim 이면 p 땅으로 만들고, outCells/outDepth 에 거리 순으로 적는다. 찾은 칸 수를 돌려준다.
     */
    private fun spread(p: Int, c: Int, outCells: IntArray?, outDepth: IntArray?, claim: Boolean = true): Int {
        var head = 0
        var tail = 0
        for (cell in 0 until cellCount) {
            if (owner[cell] == p) {
                queue[tail++] = cell
                dist[cell] = 0
            } else dist[cell] = -1
        }
        var found = 0
        while (head < tail) {
            val cell = queue[head++]
            val r = cell / size
            val col = cell % size
            for (k in 0 until 4) {
                val nr = r + DR[k]
                val nc = col + DC[k]
                if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue
                val nb = nr * size + nc
                if (dist[nb] >= 0 || owner[nb] != NONE || color[nb] != c) continue
                dist[nb] = dist[cell] + 1
                queue[tail++] = nb
                if (claim) owner[nb] = p
                if (outCells != null) outCells[found] = nb
                if (outDepth != null) outDepth[found] = dist[nb]
                found++
            }
        }
        return found
    }

    /** p 의 땅을 본진에서 가까운 순으로 queue 에 담는다 (거리는 dist). 칸 수를 돌려준다 */
    private fun territoryByDistance(p: Int): Int {
        for (cell in 0 until cellCount) dist[cell] = -1
        var head = 0
        var tail = 0
        queue[tail++] = home[p]
        dist[home[p]] = 0
        while (head < tail) {
            val cell = queue[head++]
            val r = cell / size
            val col = cell % size
            for (k in 0 until 4) {
                val nr = r + DR[k]
                val nc = col + DC[k]
                if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue
                val nb = nr * size + nc
                if (dist[nb] >= 0 || owner[nb] != p) continue
                dist[nb] = dist[cell] + 1
                queue[tail++] = nb
            }
        }
        return tail
    }

    companion object {
        const val NONE = -1
        private val DR = intArrayOf(-1, 1, 0, 0)
        private val DC = intArrayOf(0, 0, -1, 1)

        /** 인원별 모서리 칸: 2인 ↖↘, 3인 ↖↗↘, 4인 ↖↗↘↙ (시계 방향) */
        fun corners(players: Int, size: Int): IntArray {
            val tl = 0
            val tr = size - 1
            val br = size * size - 1
            val bl = size * (size - 1)
            return when (players) {
                1 -> intArrayOf(tl)
                2 -> intArrayOf(tl, br)
                3 -> intArrayOf(tl, tr, br)
                else -> intArrayOf(tl, tr, br, bl)
            }
        }

        /**
         * 무작위 격자 (같은 random 이면 같은 판).
         * 칸마다 색을 고른 뒤, 모서리 칸 색이 앞 사람과 겹치면 아직 안 쓴 색 중에서 다시 뽑는다.
         */
        fun randomColors(players: Int, random: Random, size: Int = GameConfig.SIZE, colorCount: Int = GameConfig.COLORS): IntArray {
            require(players <= colorCount) { "색이 인원보다 적음" }
            val colors = IntArray(size * size) { random.nextInt(colorCount) }
            val homes = corners(players, size)
            for (p in homes.indices) {
                val used = BooleanArray(colorCount)
                for (q in 0 until p) used[colors[homes[q]]] = true
                if (used[colors[homes[p]]]) {
                    val free = (0 until colorCount).filter { !used[it] }
                    colors[homes[p]] = free[random.nextInt(free.size)]
                }
            }
            return colors
        }

        /** 무작위 판으로 새 게임 (먼저 할 사람도 random 으로) */
        fun create(players: Int, random: Random): FloodGame {
            val colors = randomColors(players, random)
            return FloodGame(players, colors, first = random.nextInt(players))
        }
    }
}
