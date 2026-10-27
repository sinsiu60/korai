package com.siwoo.party.games.matches

import com.siwoo.party.common.Ranking

/** 가져가기 결과 */
sealed class Take {
    /** 무시 (개수가 1~3 밖·남은 수보다 많음·라운드 끝난 뒤·게임 끝) */
    object Invalid : Take()

    /** 가져감: 남은 성냥 remaining, 다음 차례 next */
    class Took(val player: Int, val count: Int, val remaining: Int, val next: Int) : Take()

    /**
     * 마지막 성냥을 가져감 → player 가 이번 라운드 패배(+1패).
     * gameOver 가 아니면 화면 연출 뒤 nextRound() 로 다음 라운드를 시작한다.
     */
    class Lost(val player: Int, val count: Int, val round: Int, val gameOver: Boolean) : Take()
}

/**
 * 성냥개비 가져가기 규칙 (님 게임의 "마지막을 가져가면 지는" 판).
 * - 한 판(라운드)에 성냥 21개(GameConfig.MATCHES). 자리 순서대로 돌아가며 1~3개씩 가져간다.
 * - 마지막 성냥을 가져간 사람이 그 라운드 패배(+1패). 남은 수보다 많이 가져갈 수는 없다.
 * - 라운드 수 = 인원 수. 라운드마다 첫 차례가 한 칸씩 돈다 (r 라운드 첫 차례 = r % 인원) → 모두 한 번씩 먼저 한다.
 * - 모든 라운드가 끝나면 패배가 적은 순서로 순위 (같으면 공동 순위).
 * 어떤 성냥을 골랐는지는 상관없고 개수만 본다 (자리 고르기는 MatchPile).
 */
class MatchesGame(val playerCount: Int, val totalMatches: Int = GameConfig.MATCHES) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
        require(totalMatches >= 1)
    }

    /** 라운드 수 = 인원 수 */
    val rounds: Int = playerCount

    /** 지금 라운드 (0부터) */
    var round = 0
        private set
    /** 지금 차례 */
    var current = starterOf(0)
        private set
    /** 이번 라운드에 남은 성냥 */
    var remaining = totalMatches
        private set
    /** 이번 라운드가 끝나고 nextRound() 를 기다리는 중 */
    var roundOver = false
        private set
    var gameOver = false
        private set

    /** 플레이어별 패배 수 */
    val losses = IntArray(playerCount)
    /** 라운드별 패배한 사람 (-1 = 아직) */
    val roundLoser = IntArray(rounds) { -1 }
    /** 이번 라운드에 플레이어별로 가져간 성냥 수 */
    val takenThisRound = IntArray(playerCount)

    /** r 라운드의 첫 차례 */
    fun starterOf(r: Int): Int = r % playerCount

    /** 지금 가져갈 수 있는 최대 개수 (남은 수와 3 중 작은 것) */
    val maxTake: Int get() = minOf(GameConfig.MAX_TAKE, remaining)

    fun canTake(count: Int): Boolean =
        !gameOver && !roundOver && count >= GameConfig.MIN_TAKE && count <= maxTake

    /** 지금 차례인 사람이 count 개를 가져간다 */
    fun take(count: Int): Take {
        if (!canTake(count)) return Take.Invalid
        val p = current
        remaining -= count
        takenThisRound[p] += count
        if (remaining == 0) {
            losses[p]++
            roundLoser[round] = p
            roundOver = true
            if (round == rounds - 1) gameOver = true
            return Take.Lost(p, count, round, gameOver)
        }
        current = (current + 1) % playerCount
        return Take.Took(p, count, remaining, current)
    }

    /** 다음 라운드 시작 (성냥 다시 21개, 첫 차례는 다음 사람). 시작했으면 true */
    fun nextRound(): Boolean {
        if (!roundOver || gameOver) return false
        round++
        remaining = totalMatches
        current = starterOf(round)
        roundOver = false
        takenThisRound.fill(0)
        return true
    }

    /** 최종 순위 (1부터, 동점 허용): 패배가 적은 순 */
    fun finalRanks(): IntArray = Ranking.competition(losses.map { it.toLong() }, higherIsBetter = false)
}
