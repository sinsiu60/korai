package com.siwoo.party.games.snakeladder

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 게임 규칙 설정 */
data class SnakeRules(
    /** 맵 번호 (SnakeMaps.ALL) */
    val mapIndex: Int = GameConfig.DEFAULT_MAP,
    /** 정확히 100 에 멈춰야 도착 (넘치면 남은 눈만큼 되돌아감) */
    val exactFinish: Boolean = GameConfig.EXACT_FINISH_DEFAULT,
)

/** 주사위 한 번의 결과 (화면 연출이 이 순서대로 보여 준다) */
class SnakeMove(
    val player: Int,
    val die: Int,
    /** 출발 칸 (0 = 아직 판 밖 출발점) */
    val from: Int,
    /** 한 칸씩 밟는 칸들 (마지막 = 걸어서 멈춘 칸). 정확히 100 규칙에서 넘치면 100 을 찍고 되돌아온 칸들이 이어진다 */
    val walk: IntArray,
    /** 100 을 넘쳐 되돌아왔는지 */
    val bounced: Boolean,
    /** 걸어서 멈춘 칸 */
    val landed: Int,
    /** 차례대로 탄 사다리·뱀 (보통 0~1개, 연쇄가 있으면 여러 개) */
    val jumps: List<Jump>,
    /** 최종 칸 */
    val to: Int,
    /** 이번에 100 에 도착했는지 */
    val finished: Boolean,
    /** 도착했으면 도착 순위 (1부터), 아니면 0 */
    val finishRank: Int,
    /** 6 이 나와서 한 번 더 (도착하면 없음) */
    val extraRoll: Boolean,
    /** 이번 주사위로 차례가 끝났는지 */
    val turnEnded: Boolean,
    /** 게임이 끝났는지 */
    val gameOver: Boolean,
)

/** 이동 규칙 (순수 함수) */
object SnakeMoves {
    /**
     * from 에서 die 만큼 한 칸씩 걷는 길.
     * - 기본: 100 을 넘으면 100 에서 멈춤 (넘어도 도착)
     * - exact = true: 넘친 만큼 100 에서 되돌아감 (97 에서 5 → 98, 99, 100, 99, 98)
     */
    fun walk(from: Int, die: Int, exact: Boolean): IntArray {
        val out = ArrayList<Int>(die)
        var s = from
        var dir = 1
        for (k in 0 until die) {
            if (s == GameConfig.SQUARES) {
                if (!exact) break
                dir = -1
            }
            s += dir
            out.add(s)
        }
        return out.toIntArray()
    }

    /** square 에 멈춘 뒤 타는 사다리·뱀 목록 (연쇄면 이어서, 최대 MAX_CHAIN) */
    fun resolve(map: SnakeMap, square: Int): List<Jump> {
        val out = ArrayList<Jump>(1)
        var s = square
        while (out.size < GameConfig.MAX_CHAIN) {
            val j = map.jumpAt(s) ?: break
            out.add(j)
            s = j.to
        }
        return out
    }
}

/**
 * 뱀과 사다리 한 판 (2~4인).
 * - 모두 출발점(0)에서 시작, 차례대로 주사위 1개. 6 이 나오면 한 번 더 (연속 횟수 제한 없음)
 * - 사다리 아래 칸에 멈추면 위로, 뱀 머리에 멈추면 꼬리로. 말끼리는 같은 칸에 함께 설 수 있음 (잡기 없음)
 * - 100 에 도착한 순서가 순위. 처음 도착한 사람이 나온 그 바퀴(0번 → 마지막 사람)까지만 하고 끝
 *   → 같은 바퀴에 도착한 사람은 도착 순서로, 나머지는 위치(높은 칸) 순, 같은 칸이면 같은 순위
 * - 아직 도착하지 않은 사람이 1명 이하가 되면 바로 끝
 */
class SnakeGame(
    val playerCount: Int,
    val rules: SnakeRules = SnakeRules(),
    private val random: Random = Random.Default,
) {
    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val map: SnakeMap = SnakeMaps.get(rules.mapIndex)

    private val pos = IntArray(playerCount)
    private val order = ArrayList<Int>()

    /** 지금 차례 */
    var current = 0
        private set
    var over = false
        private set
    /** 몇 번째 바퀴 (1부터) */
    var round = 1
        private set
    /** 누군가 도착해서 이번 바퀴가 마지막 */
    var lastRound = false
        private set
    /** 이번 차례에 이미 굴린 횟수 */
    var rollsThisTurn = 0
        private set

    fun position(p: Int): Int = pos[p]
    fun isFinished(p: Int): Boolean = pos[p] == GameConfig.SQUARES
    /** 도착 순위 (0 = 아직) */
    fun finishRank(p: Int): Int = order.indexOf(p) + 1
    val finishOrder: List<Int> get() = order
    val unfinishedCount: Int get() = playerCount - order.size

    fun roll(): SnakeMove = rollWith(random.nextInt(1, GameConfig.DIE_FACES + 1))

    /** 정해진 눈으로 굴리기 (테스트·연출 확인용) */
    fun rollWith(die: Int): SnakeMove {
        check(!over) { "게임이 끝났어요" }
        require(die in 1..GameConfig.DIE_FACES)
        val p = current
        val from = pos[p]
        val walk = SnakeMoves.walk(from, die, rules.exactFinish)
        val landed = if (walk.isEmpty()) from else walk[walk.size - 1]
        var bounced = false
        for (s in walk) if (s == GameConfig.SQUARES && landed != GameConfig.SQUARES) bounced = true
        val jumps = SnakeMoves.resolve(map, landed)
        val to = if (jumps.isEmpty()) landed else jumps[jumps.size - 1].to
        pos[p] = to
        rollsThisTurn++
        val finished = to == GameConfig.SQUARES
        var rank = 0
        if (finished) {
            order.add(p)
            rank = order.size
            lastRound = true
        }
        val extra = !finished && die == GameConfig.EXTRA_ROLL_FACE
        var turnEnded = false
        if (!extra) {
            turnEnded = true
            advance()
        }
        return SnakeMove(p, die, from, walk, bounced, landed, jumps, to, finished, rank, extra, turnEnded, over)
    }

    /** 다음 사람으로 (도착한 사람은 건너뜀). 바퀴가 끝날 때 누군가 도착했으면 게임 끝 */
    private fun advance() {
        rollsThisTurn = 0
        if (unfinishedCount <= 1) {
            over = true
            return
        }
        var i = current + 1
        while (i < playerCount && isFinished(i)) i++
        if (i < playerCount) {
            current = i
            return
        }
        // 바퀴 끝
        if (lastRound) {
            over = true
            return
        }
        round++
        i = 0
        while (isFinished(i)) i++
        current = i
    }

    /**
     * 순위 (players 순서, 1부터). 도착한 사람은 도착 순서, 나머지는 위치가 높은 순 (같은 칸 = 같은 순위).
     * 게임 중간에 불러도 지금 상태로 계산한다.
     */
    fun ranks(): IntArray {
        val values = (0 until playerCount).map { p ->
            val r = finishRank(p)
            if (r > 0) (FINISH_SCORE - r).toLong() else pos[p].toLong()
        }
        return Ranking.competition(values)
    }

    private companion object {
        /** 도착한 사람의 순위 값 (어떤 위치보다 큼) */
        const val FINISH_SCORE = 10_000
    }
}
