package com.siwoo.party.games.horserace

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 게임 단계 */
enum class Phase { BETTING, RACING, RESULT, OVER }

/** 베팅 시도 결과 */
enum class BetResult {
    OK,
    /** 베팅 단계가 아님 */
    NOT_BETTING,
    /** 말 번호가 0~5 가 아님 */
    BAD_HORSE,
    /** 금액이 0 이하이거나 BET_UNIT 의 배수가 아님 */
    NOT_UNIT,
    /** 남은 칩보다 많음 */
    NOT_ENOUGH_CHIPS,
}

/**
 * 한 레이스의 정산.
 * stake[p] = 건 칩 합, payout[p] = 받은 칩 (건 칩 포함), net = payout - stake
 */
class RaceResult(
    val raceNumber: Int,
    val first: Int,
    val second: Int,
    val stake: IntArray,
    val payout: IntArray,
    val chipsAfter: IntArray,
) {
    fun net(p: Int): Int = payout[p] - stake[p]
}

/**
 * 주사위 경마 규칙 (순수 Kotlin). 난수는 밖에서 주입.
 *
 * 흐름: 레이스마다 [BETTING] 차례대로 베팅 → [RACING] 주사위로 말 전진 → 2착까지 들어오면 자동 정산 → [RESULT]
 *  → nextRace() → … RACES 판 뒤(또는 베팅할 수 있는 사람이 1명 이하가 되면) [OVER], 칩 순 순위.
 * 베팅 규칙
 * - 금액은 BET_UNIT(10)의 배수, 남은 칩 이하. 거는 순간 칩에서 빠진다. 같은 말에 또 걸면 더해진다. 여러 마리 가능.
 * - 칩이 BET_UNIT 미만인 사람은 베팅 차례가 오지 않는다 (구경). 칩이 늘 방법이 없으므로 사실상 탈락.
 * - 차례인 사람은 이번 레이스에 MIN_BET_PER_RACE 이상 걸어야 차례를 마칠 수 있다.
 * - 차례 순서는 레이스마다 한 칸씩 돌아간다 (1레이스 = 0번 사람부터, 2레이스 = 1번 사람부터 …).
 */
class HorseRaceGame(val playerCount: Int, private val random: Random) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val chips = IntArray(playerCount) { GameConfig.START_CHIPS }
    /** bets[p][말] = 이번 레이스에 건 칩 */
    val bets = Array(playerCount) { IntArray(GameConfig.HORSES) }

    /** 지금 레이스 번호 (1부터) */
    var raceNumber = 1
        private set
    var phase = Phase.BETTING
        private set
    /** 이번 레이스 베팅 순서 (칩이 있는 사람만) */
    var bettingOrder: List<Int> = emptyList()
        private set
    private var orderIndex = 0
    /** 지금 레이스 (RACING·RESULT 단계) */
    var race: Race? = null
        private set
    /** 마지막 정산 */
    var lastResult: RaceResult? = null
        private set
    /** 베팅할 사람이 1명 이하라 RACES 전에 끝났는지 */
    var endedEarly = false
        private set

    init {
        buildOrder()
    }

    val gameOver: Boolean get() = phase == Phase.OVER

    /** 베팅할 수 있는 칩이 있는지 */
    fun canBet(p: Int): Boolean = chips[p] >= GameConfig.BET_UNIT

    /** 지금 베팅 차례인 사람 (-1 = 없음) */
    val currentBettor: Int
        get() = if (phase == Phase.BETTING && orderIndex < bettingOrder.size) bettingOrder[orderIndex] else -1

    /** 이번 레이스에 p 가 건 칩 합 */
    fun betTotal(p: Int): Int = bets[p].sum()

    /** 말 h 에 걸린 칩 합 (모든 사람) */
    fun horseTotal(h: Int): Int {
        var s = 0
        for (p in 0 until playerCount) s += bets[p][h]
        return s
    }

    private fun buildOrder() {
        val start = (raceNumber - 1) % playerCount
        bettingOrder = (0 until playerCount).map { (start + it) % playerCount }.filter { canBet(it) }
        orderIndex = 0
    }

    /** 금액 검사만 (칩 변화 없음) */
    fun validate(player: Int, horse: Int, amount: Int): BetResult = when {
        phase != Phase.BETTING || player != currentBettor -> BetResult.NOT_BETTING
        horse !in 0 until GameConfig.HORSES -> BetResult.BAD_HORSE
        amount <= 0 || amount % GameConfig.BET_UNIT != 0 -> BetResult.NOT_UNIT
        amount > chips[player] -> BetResult.NOT_ENOUGH_CHIPS
        else -> BetResult.OK
    }

    /** 지금 차례인 사람이 말 horse 에 amount 칩을 건다 */
    fun placeBet(horse: Int, amount: Int): BetResult {
        val p = currentBettor
        val r = validate(p, horse, amount)
        if (r != BetResult.OK) return r
        chips[p] -= amount
        bets[p][horse] += amount
        return BetResult.OK
    }

    /** 지금 차례인 사람의 이번 레이스 베팅을 모두 무르기 (칩 돌려받음) */
    fun clearBets(): Boolean {
        val p = currentBettor
        if (p < 0) return false
        val total = betTotal(p)
        if (total == 0) return false
        chips[p] += total
        bets[p].fill(0)
        return true
    }

    /** 차례를 마칠 수 있는지 (최소 베팅) */
    fun canFinishBetting(): Boolean {
        val p = currentBettor
        return p >= 0 && betTotal(p) >= GameConfig.MIN_BET_PER_RACE
    }

    /** 차례 마침 → 다음 사람, 모두 마쳤으면 레이스 시작(RACING). 못 마치면 false */
    fun finishBetting(): Boolean {
        if (!canFinishBetting()) return false
        orderIndex++
        if (orderIndex >= bettingOrder.size) {
            phase = Phase.RACING
            race = Race(random)
        }
        return true
    }

    /** 주사위 한 번. 2착까지 들어오면 정산하고 RESULT 로 */
    fun raceStep(): RaceStep {
        check(phase == Phase.RACING)
        val r = race!!
        val s = r.step()
        if (r.isOver) settle(r)
        return s
    }

    private fun settle(r: Race) {
        val stake = IntArray(playerCount) { betTotal(it) }
        val payout = IntArray(playerCount) { Payout.total(bets[it], r.first, r.second) }
        for (p in 0 until playerCount) chips[p] += payout[p]
        lastResult = RaceResult(raceNumber, r.first, r.second, stake, payout, chips.copyOf())
        phase = Phase.RESULT
    }

    /** 베팅할 수 있는 사람 수 */
    fun bettableCount(): Int = (0 until playerCount).count { canBet(it) }

    /** 지금 결과 다음에 게임이 끝나는지 (마지막 레이스였거나 베팅할 사람이 1명 이하) */
    val willEnd: Boolean get() = raceNumber >= GameConfig.RACES || bettableCount() <= 1

    /** 결과를 본 뒤 다음 레이스로 (마지막이었으면 OVER). 끝났으면 true */
    fun nextRace(): Boolean {
        check(phase == Phase.RESULT)
        for (b in bets) b.fill(0)
        race = null
        if (raceNumber >= GameConfig.RACES) {
            phase = Phase.OVER
            return true
        }
        if (bettableCount() <= 1) {
            endedEarly = true
            phase = Phase.OVER
            return true
        }
        raceNumber++
        phase = Phase.BETTING
        buildOrder()
        return false
    }

    /** 칩 순 순위 (동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(chips.map { it.toLong() })

    /** 최종 순위 (끝난 뒤) */
    fun finalRanks(): IntArray = ranks()
}
