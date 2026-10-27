package com.siwoo.party.games.reaction

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 라운드 단계 */
enum class Phase { IDLE, WAITING, FAKE, GO, DONE }

/** 이번 라운드 예정: fakeAtMs 에 속임수(-1 이면 없음), goAtMs 에 "지금!" (라운드 시작 기준) */
class RoundPlan(val fakeAtMs: Long, val goAtMs: Long) {
    val hasFake: Boolean get() = fakeAtMs >= 0
}

/** 터치 결과 */
sealed class Touch {
    /** 무시 (이미 실격·이미 누름·라운드 밖) */
    object Ignored : Touch()
    /** 초록 전에 누름 → 실격. allOut 이면 전원 실격 → 라운드 무효 */
    class Disqualified(val player: Int, val allOut: Boolean) : Touch()
    /** 1등 */
    class Won(val player: Int, val ms: Long) : Touch()
    /** 1등 뒤에 누름 (반응 시간만 기록) */
    class Late(val player: Int, val ms: Long) : Touch()
}

/**
 * 반응속도 결투 규칙.
 * - 라운드: 대기(1.5~5초) → (20% 속임수 0.6초) → "지금!" → 먼저 누른 사람이 승리
 * - "지금!" 전에 누르면 그 라운드 실격, 전원 실격이면 라운드 무효(다시)
 * - 최종 순위: 라운드 승수 → 같으면 평균 반응 시간이 빠른 순 (둘 다 같으면 공동 순위)
 * 시간은 화면이 넘겨준다(ms) → 테스트에서 그대로 재현 가능.
 */
class ReactionGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val wins = IntArray(playerCount)
    private val sumMs = LongArray(playerCount)
    private val countMs = IntArray(playerCount)

    /** 끝난(유효한) 라운드 수 */
    var roundsDone = 0
        private set
    var phase = Phase.IDLE
        private set
    val disqualified = BooleanArray(playerCount)
    /** 이번 라운드 반응 시간 (-1 = 안 누름) */
    val roundTimes = LongArray(playerCount) { -1 }
    var roundWinner = -1
        private set

    val gameOver: Boolean get() = roundsDone >= GameConfig.REACTION_ROUNDS
    /** 지금 하는 라운드 번호 (1부터) */
    val roundNumber: Int get() = (roundsDone + 1).coerceAtMost(GameConfig.REACTION_ROUNDS)

    /** 새 라운드 시작 (무효 라운드 다시 시작도 이것) */
    fun startRound(): RoundPlan {
        check(!gameOver) { "게임이 끝났습니다" }
        disqualified.fill(false)
        roundTimes.fill(-1)
        roundWinner = -1
        phase = Phase.WAITING
        val wait = GameConfig.WAIT_MIN_MS + (random.nextDouble() * (GameConfig.WAIT_MAX_MS - GameConfig.WAIT_MIN_MS)).toLong()
        return if (random.nextDouble() < GameConfig.REACTION_FAKE_RATE) {
            val after = GameConfig.AFTER_FAKE_MIN_MS + (random.nextDouble() * (GameConfig.AFTER_FAKE_MAX_MS - GameConfig.AFTER_FAKE_MIN_MS)).toLong()
            RoundPlan(wait, wait + GameConfig.FAKE_SHOW_MS + after)
        } else {
            RoundPlan(-1, wait)
        }
    }

    fun showFake() {
        if (phase == Phase.WAITING) phase = Phase.FAKE
    }

    fun endFake() {
        if (phase == Phase.FAKE) phase = Phase.WAITING
    }

    /** "지금!" */
    fun go() {
        if (phase == Phase.WAITING || phase == Phase.FAKE) phase = Phase.GO
    }

    /**
     * 플레이어 터치. msSinceGo 는 "지금!" 이후 경과 시간 (GO 단계에서만 의미 있음)
     */
    fun touch(player: Int, msSinceGo: Long): Touch {
        if (player !in 0 until playerCount) return Touch.Ignored
        if (disqualified[player] || roundTimes[player] >= 0) return Touch.Ignored
        return when (phase) {
            Phase.WAITING, Phase.FAKE -> {
                disqualified[player] = true
                val allOut = disqualified.all { it }
                if (allOut) phase = Phase.IDLE // 무효 → 다시 startRound
                Touch.Disqualified(player, allOut)
            }
            Phase.GO, Phase.DONE -> {
                if (phase == Phase.DONE && roundWinner < 0) return Touch.Ignored
                val ms = msSinceGo.coerceAtLeast(0)
                roundTimes[player] = ms
                sumMs[player] += ms
                countMs[player]++
                if (roundWinner < 0) {
                    roundWinner = player
                    wins[player]++
                    Touch.Won(player, ms)
                } else {
                    Touch.Late(player, ms)
                }
            }
            Phase.IDLE -> Touch.Ignored
        }
    }

    /** 실격이 아닌 사람이 모두 눌렀는지 */
    fun allResponded(): Boolean = (0 until playerCount).all { disqualified[it] || roundTimes[it] >= 0 }

    /**
     * 라운드 마무리. 1등이 있으면 유효 라운드로 세고 true,
     * 아무도 안 눌렀으면(시간 초과) 무효로 false (다시 startRound)
     */
    fun endRound(): Boolean {
        if (roundWinner < 0) {
            phase = Phase.IDLE
            return false
        }
        phase = Phase.DONE
        roundsDone++
        return true
    }

    /** 평균 반응 시간 (기록 없으면 null) */
    fun averageMs(player: Int): Long? = if (countMs[player] == 0) null else sumMs[player] / countMs[player]

    /** 최종 순위 (1부터, 동점 허용): 승수 많은 순 → 평균 반응 시간 빠른 순 */
    fun finalRanks(): IntArray {
        // 정렬 키 하나로: 승수가 클수록, 평균이 작을수록 큰 값
        val keys = (0 until playerCount).map { p ->
            val avg = averageMs(p) ?: NO_TIME
            wins[p].toLong() * KEY_SCALE + (NO_TIME - avg.coerceAtMost(NO_TIME))
        }
        return Ranking.competition(keys)
    }

    companion object {
        private const val NO_TIME = 999_999L
        private const val KEY_SCALE = 10_000_000L
    }
}
