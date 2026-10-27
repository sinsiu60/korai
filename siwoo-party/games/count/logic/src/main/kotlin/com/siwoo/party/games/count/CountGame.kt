package com.siwoo.party.games.count

import com.siwoo.party.common.Ranking
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 라운드 하나: 그림 count 개를 showMs 동안 보여 준다.
 * choices = 보기 4개 (모든 구역 같은 순서), 정답은 correctIndex 번째. seed = 그림 배치 씨앗.
 */
class Round(val number: Int, val count: Int, val showMs: Long, val choices: IntArray, val seed: Long) {
    val correctIndex: Int = choices.indexOf(count)
}

/**
 * 라운드 만들기.
 * - 개수: 1라운드 COUNT_MIN → 마지막 COUNT_MAX 로 고르게 늘어난 기준값 ± COUNT_JITTER (범위 안으로)
 * - 보여 주는 시간: SHOW_FIRST_MS → SHOW_LAST_MS 로 고르게 줄어듦
 * - 보기: 정답 + 가까운 오답 3개 (±1..NEAR_MAX, 하나는 꼭 ±1), 모두 다르고 1 이상, 섞음
 */
class RoundGen(private val random: Random) {

    /** number 라운드(1부터)의 기준 개수 */
    fun baseCount(number: Int, rounds: Int = GameConfig.ROUNDS): Int {
        if (rounds <= 1) return GameConfig.COUNT_MIN
        val f = (number - 1).toFloat() / (rounds - 1)
        return (GameConfig.COUNT_MIN + (GameConfig.COUNT_MAX - GameConfig.COUNT_MIN) * f).roundToInt()
    }

    /** number 라운드의 그림 보여 주는 시간 (ms) */
    fun showMs(number: Int, rounds: Int = GameConfig.ROUNDS): Long {
        if (rounds <= 1) return GameConfig.SHOW_FIRST_MS
        val f = (number - 1).toDouble() / (rounds - 1)
        return (GameConfig.SHOW_FIRST_MS + (GameConfig.SHOW_LAST_MS - GameConfig.SHOW_FIRST_MS) * f).roundToInt().toLong()
    }

    fun count(number: Int, rounds: Int = GameConfig.ROUNDS): Int {
        val j = random.nextInt(-GameConfig.COUNT_JITTER, GameConfig.COUNT_JITTER + 1)
        return (baseCount(number, rounds) + j).coerceIn(GameConfig.COUNT_MIN, GameConfig.COUNT_MAX)
    }

    /** 정답 answer 의 보기 (CHOICES 개, 섞음) */
    fun choicesFor(answer: Int): IntArray {
        val near = ArrayList<Int>()
        for (d in 1..GameConfig.NEAR_MAX) { near.add(answer - d); near.add(answer + d) }
        val ok = near.filter { it >= 1 }
        val ones = ok.filter { abs(it - answer) == 1 }.shuffled(random)
        val list = ArrayList<Int>(GameConfig.CHOICES)
        list.add(answer)
        list.add(ones.first())
        for (c in ok.shuffled(random)) {
            if (list.size >= GameConfig.CHOICES) break
            if (c !in list) list.add(c)
        }
        list.shuffle(random)
        return list.toIntArray()
    }

    fun make(number: Int, rounds: Int = GameConfig.ROUNDS): Round {
        val n = count(number, rounds)
        return Round(number, n, showMs(number, rounds), choicesFor(n), random.nextLong())
    }

    fun makeSet(rounds: Int = GameConfig.ROUNDS): List<Round> = (1..rounds).map { make(it, rounds) }
}

/** 단계: READY(라운드 사이) → SHOWING(그림 보임) → ASKING(보기 누르기) → CLOSED / GAME_OVER */
enum class Phase { READY, SHOWING, ASKING, CLOSED, GAME_OVER }

/** 보기 누름 결과 */
enum class Answer {
    /** 답할 때가 아님 (그림 보는 중·이미 누가 맞힘·시간 끝) */
    IGNORED,
    /** 잠금 중 (틀린 뒤 LOCK_MS) */
    LOCKED,
    /** 틀림 → 잠금 시작 */
    WRONG,
    /** 먼저 맞힘 → +1, 라운드 끝 */
    CORRECT,
}

/**
 * 몇 개였지? 규칙.
 * - 12라운드. 가운데에 그림이 잠깐 보였다 사라지면 각 구역에 같은 보기 4개
 * - 먼저 맞힌 사람 +1, 그 라운드 끝. 틀리면 그 사람만 LOCK_MS 잠금 (새 라운드에 풀림)
 * - 답 시간(ANSWER_TIME_MS)이 끝나면 점수 없이 다음 라운드
 * - 순위: 점수 순 (동점 같은 순위)
 * 시간·난수는 밖에서 넣는다 → 테스트에서 그대로 재현 가능.
 */
class CountGame(val playerCount: Int, random: Random = Random.Default, rounds: List<Round>? = null) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val rounds: List<Round> = rounds ?: RoundGen(random).makeSet()
    val scores = IntArray(playerCount)
    /** 잠금이 풀리는 게임 시각 (0 = 잠금 없음) */
    val lockUntil = LongArray(playerCount)
    /** 이번 라운드에서 사람마다 틀린 보기 (비트) */
    val wrongMask = IntArray(playerCount)

    var phase = Phase.READY
        private set
    /** 지금 라운드 번호 0.. (시작 전 -1) */
    var index = -1
        private set
    /** 이번 라운드를 맞힌 사람 (-1 = 아직/없음) */
    var winner = -1
        private set

    val current: Round get() = rounds[index.coerceAtLeast(0)]
    /** 1부터 */
    val number: Int get() = index + 1
    val total: Int get() = rounds.size
    val hasNext: Boolean get() = index + 1 < rounds.size
    val gameOver: Boolean get() = phase == Phase.GAME_OVER

    /** 다음 라운드 → SHOWING (그림 보이기 시작, 잠금·틀린 표시 초기화) */
    fun nextRound(): Round {
        check(phase == Phase.READY || phase == Phase.CLOSED) { "다음 라운드로 갈 수 없습니다: $phase" }
        check(hasNext) { "라운드가 끝났습니다" }
        index++
        winner = -1
        lockUntil.fill(0)
        wrongMask.fill(0)
        phase = Phase.SHOWING
        return current
    }

    /** 그림이 사라짐 → 보기 누르기 시작 */
    fun hide() {
        check(phase == Phase.SHOWING) { "그림을 보여 주는 중이 아닙니다: $phase" }
        phase = Phase.ASKING
    }

    fun isLocked(player: Int, nowMs: Long): Boolean = nowMs < lockUntil[player]

    /** player 가 choice 번째 보기를 누름 (nowMs = 게임 시간) */
    fun answer(player: Int, choice: Int, nowMs: Long): Answer {
        if (player !in 0 until playerCount || choice !in 0 until GameConfig.CHOICES) return Answer.IGNORED
        if (phase != Phase.ASKING) return Answer.IGNORED
        if (isLocked(player, nowMs)) return Answer.LOCKED
        if (choice == current.correctIndex) {
            scores[player]++
            winner = player
            close()
            return Answer.CORRECT
        }
        lockUntil[player] = nowMs + GameConfig.LOCK_MS
        wrongMask[player] = wrongMask[player] or (1 shl choice)
        return Answer.WRONG
    }

    /** 답 시간 끝 (아무도 못 맞힘) */
    fun timeUp() {
        if (phase == Phase.ASKING) close()
    }

    private fun close() {
        phase = if (hasNext) Phase.CLOSED else Phase.GAME_OVER
    }

    /** 최종 순위 (1부터, 점수 많은 순, 동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(scores.map { it.toLong() })
}
