package com.siwoo.party.games.bingo

import com.siwoo.party.common.Ranking
import kotlin.random.Random

enum class Phase { SETUP, PLAY, OVER }

/**
 * 숫자 하나를 부른 결과.
 * @param linesBefore / linesAfter 사람마다 부르기 전·후 완성 줄 수
 * @param newBingo 이번에 처음 WIN_LINES 줄 이상이 된 사람들
 * @param winners 게임이 끝났으면 1위들 (아니면 빈 목록)
 */
class CallResult(
    val number: Int,
    val caller: Int,
    val linesBefore: IntArray,
    val linesAfter: IntArray,
    val newBingo: List<Int>,
    val winners: List<Int>,
    val gameOver: Boolean,
) {
    /** p 가 이번에 새로 완성한 줄 수 */
    fun gained(p: Int): Int = linesAfter[p] - linesBefore[p]
}

/**
 * 빙고 규칙 (2~4인, 화면 없음).
 * 1) 판 채우기(SETUP): 0번부터 차례로 각자 자기 판에 1..25 를 배치 → finishSetup
 * 2) 부르기(PLAY): starter 부터 차례로 아직 안 부른 숫자를 하나 부르면 모든 판에 표시된다.
 *    한 번 부른 뒤 누구든 WIN_LINES 줄 이상이면 그 즉시 끝(OVER). 그 부르기로 함께 도달한 사람은 모두 1위(부른 사람 우선 없음).
 * 3) 순위: 빙고한 사람 1위, 나머지는 완성 줄 수 순 (같으면 같은 순위).
 */
class BingoGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val boards = Array(playerCount) { BingoBoard() }

    var phase = Phase.SETUP
        private set

    /** 판 채우는 중인 사람 (SETUP) */
    var setupPlayer = 0
        private set

    /** 처음 부를 사람 (무작위) */
    val starter = random.nextInt(playerCount)

    /** 지금 부를 사람 (PLAY) */
    var current = starter
        private set

    private val called = BooleanArray(GameConfig.CELLS + 1)
    private val order = IntArray(GameConfig.CELLS)

    /** 지금까지 부른 숫자 개수 */
    var callCount = 0
        private set

    /** 마지막으로 부른 숫자 (없으면 0) */
    val lastCall: Int get() = if (callCount == 0) 0 else order[callCount - 1]

    /** k 번째(0부터)로 부른 숫자 */
    fun calledAt(k: Int): Int = order[k]

    fun isCalled(number: Int): Boolean = number in 1..GameConfig.CELLS && called[number]

    /** 끝났을 때 1위들 */
    var winners: List<Int> = emptyList()
        private set

    var lastResult: CallResult? = null
        private set

    fun lines(p: Int): Int = boards[p].lines

    // ── 판 채우기 ──

    /** 판 채우는 사람의 빈칸을 무작위로 채움 */
    fun randomFill(p: Int = setupPlayer) {
        check(phase == Phase.SETUP)
        boards[p].fillRandom(random)
    }

    /** 지금 사람이 판을 다 채웠으면 다음 사람으로 (마지막 사람이면 부르기 시작). 성공하면 true */
    fun finishSetup(): Boolean {
        if (phase != Phase.SETUP || !boards[setupPlayer].isComplete) return false
        setupPlayer++
        if (setupPlayer >= playerCount) {
            setupPlayer = playerCount - 1
            phase = Phase.PLAY
            current = starter
        }
        return true
    }

    // ── 부르기 ──

    fun canCall(number: Int): Boolean = phase == Phase.PLAY && number in 1..GameConfig.CELLS && !called[number]

    /** 지금 사람이 number 를 부름 → 모든 판에 표시. 부를 수 없는 숫자면 null */
    fun call(number: Int): CallResult? {
        if (!canCall(number)) return null
        val caller = current
        val before = IntArray(playerCount) { lines(it) }
        called[number] = true
        order[callCount++] = number
        for (b in boards) b.mark(number)
        val after = IntArray(playerCount) { lines(it) }
        val newBingo = (0 until playerCount).filter { before[it] < GameConfig.WIN_LINES && after[it] >= GameConfig.WIN_LINES }
        val reached = (0 until playerCount).filter { after[it] >= GameConfig.WIN_LINES }
        val over = reached.isNotEmpty() || callCount == GameConfig.CELLS
        if (over) {
            phase = Phase.OVER
            winners = reached
        } else {
            current = (current + 1) % playerCount
        }
        val r = CallResult(number, caller, before, after, newBingo, winners, over)
        lastResult = r
        return r
    }

    /**
     * 순위 (players 순서, 1부터, 동점 허용): 빙고(WIN_LINES 이상)한 사람은 줄 수와 상관없이 모두 1위,
     * 나머지는 완성 줄 수가 많은 순.
     */
    fun ranks(): IntArray =
        Ranking.competition(List(playerCount) { minOf(lines(it), GameConfig.WIN_LINES).toLong() })
}
