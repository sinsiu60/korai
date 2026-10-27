package com.siwoo.party.games.liardice

import kotlin.random.Random

/**
 * 부른 수: "전체 주사위 중 [face] 눈이 [count] 개 이상".
 * 더 높은 부르기 = 개수가 더 많거나, 개수가 같으면 더 높은 눈.
 */
data class Bid(val count: Int, val face: Int) {
    /** 직전 부른 수(prev)보다 높은지 (prev 가 없으면 항상 높다) */
    fun beats(prev: Bid?): Boolean = prev == null || count > prev.count || (count == prev.count && face > prev.face)
}

enum class Phase {
    /** 아직 라운드 시작 전 */
    READY,
    /** 차례대로 부르기 (또는 의심) */
    BID,
    /** 의심 → 공개·판정 (다음 startRound 까지) */
    REVEAL,
    /** 한 명만 남음 */
    OVER,
}

/**
 * 의심 판정 결과.
 * @param actual 실제 개수 (와일드 1 포함)
 * @param loser 주사위 1개를 잃은 사람 (실제 ≥ 부른 수 → 의심한 사람, 아니면 부른 사람)
 * @param shownDice 공개 때 각자 가지고 있던 주사위 수 (잃기 전)
 * @param nextStarter 다음 라운드를 시작할 사람 (끝났으면 -1)
 */
class DoubtResult(
    val bidder: Int,
    val doubter: Int,
    val bid: Bid,
    val actual: Int,
    val loser: Int,
    val eliminated: Boolean,
    val gameOver: Boolean,
    val shownDice: IntArray,
    val nextStarter: Int,
) {
    /** 부른 수가 맞았는지 (실제 ≥ 부른 수) */
    val bidHeld: Boolean get() = actual >= bid.count
}

/**
 * 거짓말 주사위 규칙 (순수 Kotlin).
 * - 각자 주사위 5개. 라운드마다 남은 사람이 모두 굴리고, 자기 주사위만 몰래 본다.
 * - 시작하는 사람부터 차례대로 "X 눈이 N개 이상"을 부른다. 직전보다 높아야 한다.
 * - 1 와일드(wildOnes)면 1은 모든 눈으로 센다. 이때 1 눈 자체는 부를 수 없다 (단순한 규칙).
 *   와일드를 끄면 1도 보통 눈 (가장 낮은 눈) 으로 부를 수 있다.
 * - 개수는 1 ~ 남은 전체 주사위 수.
 * - 라운드 첫 사람은 의심할 수 없다. 의심하면 공개 → 실제 ≥ 부른 수면 의심한 사람, 아니면 부른 사람이 1개 잃음.
 * - 주사위를 모두 잃으면 탈락. 한 명 남으면 끝 (늦게 탈락할수록 높은 순위).
 * - 다음 라운드는 진 사람(남아 있으면)부터, 탈락했으면 그 다음 사람부터.
 * 난수는 밖에서 주입한다 (테스트 결정성).
 */
class LiarDiceGame(
    val playerCount: Int,
    val wildOnes: Boolean = GameConfig.WILD_ONES_DEFAULT,
    private val random: Random = Random.Default,
    firstStarter: Int = -1,
) {
    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "인원 $playerCount" }
    }

    /** 각자 남은 주사위 수 */
    val diceLeft = IntArray(playerCount) { GameConfig.START_DICE }
    /** 각자 이번 라운드 주사위 (앞의 diceLeft[p] 개만 유효) */
    val dice: Array<IntArray> = Array(playerCount) { IntArray(GameConfig.START_DICE) { 1 } }

    var phase = Phase.READY
        private set
    var round = 0
        private set
    /** 이번(또는 다음) 라운드를 시작하는 사람 */
    var starter = if (firstStarter in 0 until playerCount) firstStarter else random.nextInt(playerCount)
        private set
    /** 지금 부르기 차례 */
    var current = starter
        private set
    /** 지금 걸려 있는 부른 수 (라운드 첫 사람 전엔 null) */
    var bid: Bid? = null
        private set
    /** 그 수를 부른 사람 */
    var bidder = -1
        private set
    /** 이번 라운드에 부른 횟수 */
    var bidsThisRound = 0
        private set
    /** 탈락 순서 (먼저 탈락한 사람부터) */
    val eliminationOrder = ArrayList<Int>()
    var lastResult: DoubtResult? = null
        private set

    fun isAlive(p: Int) = diceLeft[p] > 0
    val aliveCount: Int get() = diceLeft.count { it > 0 }
    /** 남은 전체 주사위 수 (부를 수 있는 개수 최대) */
    val totalDice: Int get() = diceLeft.sum()
    val winner: Int get() = if (phase == Phase.OVER) diceLeft.indexOfFirst { it > 0 } else -1

    /** p 다음의 살아 있는 사람 (자리 순서) */
    fun nextAlive(p: Int): Int {
        var q = p
        repeat(playerCount) {
            q = (q + 1) % playerCount
            if (isAlive(q)) return q
        }
        return p
    }

    /** 새 라운드: 남은 사람 모두 굴리고, starter 부터 부르기. 시작한 사람을 돌려준다 */
    fun startRound(): Int {
        check(phase == Phase.READY || phase == Phase.REVEAL) { "라운드 시작 불가: $phase" }
        round++
        for (p in 0 until playerCount) {
            for (k in 0 until GameConfig.START_DICE) dice[p][k] = if (k < diceLeft[p]) 1 + random.nextInt(GameConfig.FACES) else 0
        }
        if (!isAlive(starter)) starter = nextAlive(starter)
        current = starter
        bid = null
        bidder = -1
        bidsThisRound = 0
        phase = Phase.BID
        return starter
    }

    /** 테스트·연출용: 주사위 눈을 직접 정한다 (앞에서부터 diceLeft[p] 개) */
    fun setDice(p: Int, values: IntArray) {
        for (k in 0 until diceLeft[p]) dice[p][k] = values[k]
    }

    /** 이 눈을 부를 수 있는지 (와일드면 1은 못 부름) */
    fun isFaceAllowed(face: Int): Boolean = face in 1..GameConfig.FACES && !(wildOnes && face == 1)

    /** 부를 수 있는 가장 낮은 눈 */
    val lowestFace: Int get() = if (wildOnes) 2 else 1

    /** (count, face) 를 지금 부를 수 있는지 */
    fun canBid(count: Int, face: Int): Boolean =
        phase == Phase.BID && isFaceAllowed(face) && count in 1..totalDice && Bid(count, face).beats(bid)

    /** 지금 부를 수 있는 가장 낮은 수 (더 올릴 수 없으면 null → 의심밖에 없음) */
    fun minBid(): Bid? {
        if (phase != Phase.BID) return null
        val b = bid ?: return Bid(1, lowestFace)
        if (b.face < GameConfig.FACES) return Bid(b.count, maxOf(b.face + 1, lowestFace))
        return if (b.count < totalDice) Bid(b.count + 1, lowestFace) else null
    }

    /** 더 높게 부를 수 있는지 */
    val canRaise: Boolean get() = minBid() != null

    /** 지금 차례인 사람이 부른다. 성공하면 다음 사람 차례 */
    fun bid(count: Int, face: Int): Boolean {
        if (!canBid(count, face)) return false
        bid = Bid(count, face)
        bidder = current
        bidsThisRound++
        current = nextAlive(current)
        return true
    }

    /** 지금 차례인 사람이 의심할 수 있는지 (라운드 첫 사람은 불가) */
    val canDoubt: Boolean get() = phase == Phase.BID && bid != null

    /** 주사위 d 가 face 로 세어지는지 (와일드 1 포함) */
    fun matches(d: Int, face: Int): Boolean = d == face || (wildOnes && d == 1 && face != 1)

    /** 살아 있는 사람 모두의 주사위 중 face 로 세어지는 개수 */
    fun countFace(face: Int): Int {
        var n = 0
        for (p in 0 until playerCount) for (k in 0 until diceLeft[p]) if (matches(dice[p][k], face)) n++
        return n
    }

    /** 지금 차례인 사람이 의심 → 판정. 진 사람 주사위 1개 잃음 */
    fun doubt(): DoubtResult? {
        if (!canDoubt) return null
        val b = bid ?: return null
        val doubter = current
        val shown = diceLeft.copyOf()
        val actual = countFace(b.face)
        val loser = if (actual >= b.count) doubter else bidder
        diceLeft[loser]--
        val eliminated = diceLeft[loser] == 0
        if (eliminated) eliminationOrder.add(loser)
        val over = aliveCount <= 1
        phase = if (over) Phase.OVER else Phase.REVEAL
        starter = if (over) -1 else if (isAlive(loser)) loser else nextAlive(loser)
        val r = DoubtResult(bidder, doubter, b, actual, loser, eliminated, over, shown, starter)
        lastResult = r
        return r
    }

    /** 순위 (players 순서, 1부터): 마지막까지 남은 사람 1위, 늦게 탈락할수록 높은 순위. 끝나기 전이면 남은 사람은 공동 1위 */
    fun finalRanks(): IntArray {
        val r = IntArray(playerCount) { 1 }
        for ((k, p) in eliminationOrder.withIndex()) r[p] = playerCount - k
        return r
    }
}
