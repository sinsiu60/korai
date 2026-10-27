package com.siwoo.party.games.blackjack

import com.siwoo.party.common.Ranking
import com.siwoo.party.common.cards.Card
import kotlin.random.Random

/** 라운드 진행 단계 */
enum class Phase {
    /** 참가자가 차례로 베팅 (current = 베팅할 사람) */
    BETTING,
    /** 카드를 받은 뒤 플레이어가 차례로 히트·스탠드·더블 (current = 차례인 사람) */
    PLAYER_TURNS,
    /** 딜러 구멍 카드 공개 → 17 이상까지 뽑기 ([dealerDraw]) → [settle] */
    DEALER,
    /** 정산 끝 (outcomes·profits 확인) → [nextRound] */
    ROUND_OVER,
    GAME_OVER,
}

/** 한 판 결과 */
enum class Outcome(val label: String) {
    BLACKJACK("블랙잭"),
    WIN("승리"),
    PUSH("무승부"),
    LOSE("패배"),
    BUST("버스트"),
}

/**
 * 블랙잭 규칙 (순수 Kotlin). 난수는 주입한다. 규칙 세부는 [GameConfig] 주석 참고.
 *
 * 흐름: (라운드 시작 → BETTING) [bet] × 참가자 → 자동으로 나눠 주기 → PLAYER_TURNS [hit]/[stand]/[double]
 *  → DEALER [dealerDraw] 반복 → [settle] → ROUND_OVER → [nextRound] → … → GAME_OVER ([ranks]).
 * 칩: 베팅하면 그만큼 [chips] 에서 빠져 [bets] 로 간다. 정산 때 이기면 베팅 + 이익, 비기면 베팅만 돌아온다.
 */
class BlackjackGame(
    val playerCount: Int,
    random: Random,
    val shoe: Shoe = Shoe(random),
) {
    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "인원: $playerCount" }
    }

    /** 보유 칩 (테이블에 건 칩 제외) */
    val chips = IntArray(playerCount) { GameConfig.START_CHIPS }
    /** 이번 라운드 베팅 (더블이면 두 배) */
    val bets = IntArray(playerCount)
    val hands: List<Hand> = List(playerCount) { Hand() }
    val dealer = Hand()
    val doubled = BooleanArray(playerCount)
    /** 이번 라운드 참가 (라운드 시작 때 칩이 있었음). false = 관전 */
    val playing = BooleanArray(playerCount)
    /** 이번 라운드 행동 끝 (스탠드·버스트·더블·블랙잭) */
    private val done = BooleanArray(playerCount)
    /** 정산 결과 (관전자는 null) */
    val outcomes = arrayOfNulls<Outcome>(playerCount)
    /** 정산 이익 (+이익 / 0 / −베팅) */
    val profits = IntArray(playerCount)

    /** 1..ROUNDS */
    var round = 0
        private set
    var phase = Phase.BETTING
        private set
    /** 베팅·행동할 사람 (없으면 -1) */
    var current = -1
        private set
    /** 이번 라운드 시작 때 다시 섞었는지 */
    var reshuffledThisRound = false
        private set
    /** 딜러 구멍 카드가 공개됐는지 (DEALER 단계부터) */
    val holeRevealed: Boolean get() = phase == Phase.DEALER || phase == Phase.ROUND_OVER || phase == Phase.GAME_OVER
    /** 딜러가 처음 두 장 블랙잭이라 플레이어 차례 없이 바로 공개됐는지 */
    var dealerPeekedBlackjack = false
        private set

    val gameOver: Boolean get() = phase == Phase.GAME_OVER

    init {
        startRound()
    }

    fun isSpectator(i: Int): Boolean = !playing[i]
    fun isDone(i: Int): Boolean = done[i]

    // ── 베팅 ──

    /**
     * 실제로 걸리는 칩 (못 걸면 0): 칩이 MIN_BET 미만이면 고른 것과 상관없이 남은 칩 전부,
     * 아니면 고른 금액이 보유 칩 이하일 때만 그대로.
     */
    fun effectiveBet(player: Int, amount: Int): Int {
        val have = chips[player]
        if (have <= 0 || amount <= 0) return 0
        if (have < GameConfig.MIN_BET) return have
        return if (amount <= have && amount in GameConfig.BET_OPTIONS) amount else 0
    }

    /** 지금 베팅할 사람이 이 금액을 고를 수 있는지 */
    fun canBet(amount: Int): Boolean = phase == Phase.BETTING && current >= 0 && effectiveBet(current, amount) > 0

    /** 칩이 적어 남은 칩 전부를 걸어야 하는지 */
    fun mustBetAll(player: Int): Boolean = chips[player] in 1 until GameConfig.MIN_BET

    /** 지금 차례 사람의 베팅. 모두 걸면 바로 나눠 준다 (phase 가 PLAYER_TURNS 또는 DEALER 로) */
    fun bet(amount: Int): Boolean {
        if (!canBet(amount)) return false
        val p = current
        val b = effectiveBet(p, amount)
        chips[p] -= b
        bets[p] = b
        val next = nextPlaying(p)
        if (next >= 0) current = next else deal()
        return true
    }

    // ── 나눠 주기 ──

    /** 참가자 1장씩 → 딜러 앞면 → 참가자 1장씩 → 딜러 구멍 카드 */
    private fun deal() {
        for (r in 0 until 2) {
            for (i in 0 until playerCount) if (playing[i]) hands[i].add(shoe.draw())
            dealer.add(shoe.draw())
        }
        for (i in 0 until playerCount) if (playing[i] && hands[i].isBlackjack) done[i] = true
        if (GameConfig.DEALER_PEEKS && dealer.isBlackjack) {
            dealerPeekedBlackjack = true
            for (i in 0 until playerCount) done[i] = true
        }
        phase = Phase.PLAYER_TURNS
        current = -1
        advance()
    }

    /** 다음 행동할 사람으로 (없으면 딜러 단계) */
    private fun advance() {
        for (i in 0 until playerCount) {
            if (playing[i] && !done[i]) {
                current = i
                return
            }
        }
        current = -1
        phase = Phase.DEALER
    }

    // ── 플레이어 차례 ──

    fun canHit(): Boolean = phase == Phase.PLAYER_TURNS && current >= 0 && !done[current]

    /** 첫 두 장이고, 보유 칩이 베팅 이상일 때만 */
    fun canDouble(): Boolean = canHit() && hands[current].size == 2 && chips[current] >= bets[current] && bets[current] > 0

    /** 한 장 더. 버스트거나 21 이면 차례 끝. 받은 카드 (못 하면 null) */
    fun hit(): Card? {
        if (!canHit()) return null
        val p = current
        val c = shoe.draw()
        val h = hands[p]
        h.add(c)
        if (h.isBust || (GameConfig.AUTO_STAND_ON_21 && h.best == GameConfig.BUST_OVER)) {
            done[p] = true
            advance()
        }
        return c
    }

    fun stand(): Boolean {
        if (!canHit()) return false
        done[current] = true
        advance()
        return true
    }

    /** 베팅 두 배 + 정확히 한 장 받고 스탠드. 받은 카드 (못 하면 null) */
    fun double(): Card? {
        if (!canDouble()) return null
        val p = current
        chips[p] -= bets[p]
        bets[p] *= 2
        doubled[p] = true
        val c = shoe.draw()
        hands[p].add(c)
        done[p] = true
        advance()
        return c
    }

    // ── 딜러 ──

    /** 버스트·블랙잭이 아닌 참가자 손패가 남아 있는지 (없으면 딜러는 뽑지 않음) */
    val anyLiveHand: Boolean
        get() {
            for (i in 0 until playerCount) if (playing[i] && !hands[i].isBust && !hands[i].isBlackjack) return true
            return false
        }

    /** 딜러 규칙: 17 미만이면 뽑는다 (soft 17 은 DEALER_HITS_SOFT_17 에 따라) */
    fun dealerMustDraw(): Boolean {
        if (phase != Phase.DEALER || dealerPeekedBlackjack || !anyLiveHand) return false
        return dealerRuleDraws(dealer)
    }

    /** 딜러 한 장 (뽑을 필요 없으면 null) */
    fun dealerDraw(): Card? {
        if (!dealerMustDraw()) return null
        val c = shoe.draw()
        dealer.add(c)
        return c
    }

    // ── 정산 ──

    /** 딜러가 다 뽑은 뒤 정산 → ROUND_OVER. 이미 했거나 아직이면 false */
    fun settle(): Boolean {
        if (phase != Phase.DEALER || dealerMustDraw()) return false
        for (i in 0 until playerCount) {
            if (!playing[i]) {
                outcomes[i] = null
                profits[i] = 0
                continue
            }
            val o = outcomeOf(hands[i], dealer)
            val pr = profit(o, bets[i])
            outcomes[i] = o
            profits[i] = pr
            chips[i] += bets[i] + pr
        }
        phase = Phase.ROUND_OVER
        current = -1
        return true
    }

    // ── 라운드 ──

    val isLastRound: Boolean get() = round >= GameConfig.ROUNDS

    /** 정산 뒤 다음 라운드 (마지막이었거나 모두 칩이 없으면 GAME_OVER). 진행했으면 true */
    fun nextRound(): Boolean {
        if (phase != Phase.ROUND_OVER) return false
        if (isLastRound || chips.all { it <= 0 }) {
            collectCards()
            phase = Phase.GAME_OVER
            current = -1
            return true
        }
        startRound()
        return true
    }

    private fun collectCards() {
        for (h in hands) {
            shoe.discard(h.cards)
            h.clear()
        }
        shoe.discard(dealer.cards)
        dealer.clear()
    }

    private fun startRound() {
        collectCards()
        round++
        reshuffledThisRound = false
        if (shoe.needsReshuffle) {
            shoe.reshuffle()
            reshuffledThisRound = true
        }
        dealerPeekedBlackjack = false
        for (i in 0 until playerCount) {
            playing[i] = chips[i] > 0
            done[i] = !playing[i]
            bets[i] = 0
            doubled[i] = false
            outcomes[i] = null
            profits[i] = 0
        }
        phase = Phase.BETTING
        current = nextPlaying(-1)
    }

    /** after 다음 참가자 (없으면 -1) */
    private fun nextPlaying(after: Int): Int {
        for (i in after + 1 until playerCount) if (playing[i]) return i
        return -1
    }

    /** 칩 많은 순 순위 (동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(chips.map { it.toLong() })

    companion object {
        /** 딜러 손패가 규칙상 더 뽑아야 하는지 */
        fun dealerRuleDraws(h: Hand): Boolean {
            val b = h.best
            if (b < GameConfig.DEALER_STAND) return true
            return GameConfig.DEALER_HITS_SOFT_17 && b == GameConfig.DEALER_STAND && h.isSoft
        }

        /** 플레이어 손패 대 딜러 손패 */
        fun outcomeOf(player: Hand, dealer: Hand): Outcome = when {
            player.isBust -> Outcome.BUST
            player.isBlackjack -> if (dealer.isBlackjack) Outcome.PUSH else Outcome.BLACKJACK
            dealer.isBlackjack -> Outcome.LOSE
            dealer.isBust -> Outcome.WIN
            player.best > dealer.best -> Outcome.WIN
            player.best < dealer.best -> Outcome.LOSE
            else -> Outcome.PUSH
        }

        /** 결과별 이익: 블랙잭 1.5배(내림), 승 +베팅, 무 0, 패·버스트 −베팅 */
        fun profit(o: Outcome, bet: Int): Int = when (o) {
            Outcome.BLACKJACK -> bet * GameConfig.BLACKJACK_PAY_NUM / GameConfig.BLACKJACK_PAY_DEN
            Outcome.WIN -> bet
            Outcome.PUSH -> 0
            Outcome.LOSE, Outcome.BUST -> -bet
        }
    }
}
