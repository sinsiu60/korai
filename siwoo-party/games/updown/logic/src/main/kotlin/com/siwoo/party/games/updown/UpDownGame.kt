package com.siwoo.party.games.updown

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 추측 판정 */
enum class Verdict {
    /** 폭탄보다 큼 → 위쪽(큰 수)이 잘림, 폭탄은 더 작다 */
    DOWN,
    /** 폭탄보다 작음 → 아래쪽(작은 수)이 잘림, 폭탄은 더 크다 */
    UP,
    /** 폭탄! */
    BOOM,
}

enum class Phase {
    /** 라운드 시작 전 (처음·라운드 사이) */
    READY,
    PLAYING,
    OVER,
}

/**
 * 추측 한 번.
 * @param low/high 추측 전 잘린 값 (열린 범위는 low+1 ~ high-1)
 */
data class Step(
    val player: Int,
    val value: Int,
    val verdict: Verdict,
    val low: Int,
    val high: Int,
    /** 폭탄이면 이 라운드가 끝 */
    val roundOver: Boolean,
    /** 폭탄이고 마지막 라운드였으면 게임 끝 */
    val gameOver: Boolean,
)

/**
 * 폭탄 숫자 업다운 규칙 (2~4인, 순수 Kotlin).
 * - 라운드마다 MIN_NUMBER~MAX_NUMBER 중 폭탄 숫자 하나 (라운드 수 = 인원수, 라운드 시작 사람은 한 명씩 돌아감)
 * - 차례대로 지금 열린 범위 안(잘린 값은 제외) 숫자를 말함 → 폭탄보다 크면 그 위쪽이, 작으면 그 아래쪽이 잘림
 * - 폭탄 숫자를 말하면 그 사람 해골 +1, 라운드 끝 (열린 범위에 폭탄만 남으면 다음 사람은 그 숫자를 말할 수밖에 없음)
 * - 해골 적은 순으로 순위 (같으면 같은 순위)
 */
class UpDownGame(val playerCount: Int, private val random: Random = Random.Default, val firstStarter: Int = 0) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
        require(firstStarter in 0 until playerCount)
    }

    /** 라운드 수 = 인원수 */
    val totalRounds: Int get() = playerCount

    var phase = Phase.READY
        private set

    /** 지금 라운드 (1부터, 시작 전 0) */
    var round = 0
        private set

    /** 이번 라운드 폭탄 숫자 (시작 전 0) */
    var bomb = 0
        private set

    /** 잘린 아래쪽 값 (이 값 이하는 못 냄) */
    var low = GameConfig.MIN_NUMBER - 1
        private set

    /** 잘린 위쪽 값 (이 값 이상은 못 냄) */
    var high = GameConfig.MAX_NUMBER + 1
        private set

    /** 이번 라운드 시작한 사람 */
    var starter = firstStarter
        private set

    /** 지금 차례 */
    var current = firstStarter
        private set

    /** 해골 수 */
    val skulls = IntArray(playerCount)

    /** 이번 라운드 추측 기록 */
    val steps: List<Step> get() = stepList
    private val stepList = ArrayList<Step>()

    /** 낼 수 있는 가장 작은 수 */
    val minAllowed: Int get() = low + 1

    /** 낼 수 있는 가장 큰 수 */
    val maxAllowed: Int get() = high - 1

    /** 남은 숫자 개수 (폭탄 포함) */
    val remaining: Int get() = maxAllowed - minAllowed + 1

    /** 폭탄만 남아 지금 사람은 폭탄을 낼 수밖에 없음 */
    val isForced: Boolean get() = phase == Phase.PLAYING && remaining == 1

    val roundsLeft: Int get() = totalRounds - round

    /** 다음 라운드 시작 (bombOverride = 테스트용). 시작한 사람 번호 */
    fun startRound(bombOverride: Int? = null): Int {
        check(phase == Phase.READY && round < totalRounds)
        round++
        bomb = bombOverride ?: random.nextInt(GameConfig.MIN_NUMBER, GameConfig.MAX_NUMBER + 1)
        require(bomb in GameConfig.MIN_NUMBER..GameConfig.MAX_NUMBER)
        low = GameConfig.MIN_NUMBER - 1
        high = GameConfig.MAX_NUMBER + 1
        starter = (firstStarter + round - 1) % playerCount
        current = starter
        stepList.clear()
        phase = Phase.PLAYING
        return starter
    }

    /** 지금 낼 수 있는 숫자인지 (열린 범위 안) */
    fun isValid(v: Int): Boolean = phase == Phase.PLAYING && v in minAllowed..maxAllowed

    /** 지금 차례 사람이 v 를 냄. 범위 밖·진행 중 아님이면 null (차례 그대로) */
    fun guess(v: Int): Step? {
        if (!isValid(v)) return null
        val p = current
        val lo = low
        val hi = high
        val verdict = when {
            v > bomb -> Verdict.DOWN
            v < bomb -> Verdict.UP
            else -> Verdict.BOOM
        }
        var roundOver = false
        var gameOver = false
        when (verdict) {
            Verdict.DOWN -> high = v
            Verdict.UP -> low = v
            Verdict.BOOM -> {
                skulls[p]++
                roundOver = true
                gameOver = round >= totalRounds
                phase = if (gameOver) Phase.OVER else Phase.READY
            }
        }
        if (!roundOver) current = (p + 1) % playerCount
        val s = Step(p, v, verdict, lo, hi, roundOver, gameOver)
        stepList.add(s)
        return s
    }

    /** 순위 (players 순서, 1부터): 해골 적은 순, 같으면 같은 순위 */
    fun ranks(): IntArray = Ranking.competition(skulls.map { it.toLong() }, higherIsBetter = false)
}
