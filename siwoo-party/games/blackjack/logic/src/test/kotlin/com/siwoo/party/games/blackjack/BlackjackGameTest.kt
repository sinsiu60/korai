package com.siwoo.party.games.blackjack

import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Suit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BlackjackGameTest {

    private fun c(rank: Int, suit: Suit = Suit.SPADE): Card = Card.of(suit, rank)
    private val A = Card.ACE
    private val K = Card.KING
    private val Q = Card.QUEEN

    private fun hand(vararg ranks: Int): Hand = Hand().also { h -> for ((k, r) in ranks.withIndex()) h.add(c(r, Suit.values()[k % 4])) }

    /** 인원 n, 칩 설정 후 베팅까지. deal = 나눠 줄 순서 (p0, p1, …, 딜러 앞, p0, p1, …, 딜러 구멍, 그 뒤 히트용) */
    private fun game(n: Int, vararg deal: Card, seed: Int = 1): BlackjackGame {
        val g = BlackjackGame(n, Random(seed))
        g.shoe.stackTop(deal.toList())
        return g
    }

    private fun betAll(g: BlackjackGame, amount: Int = GameConfig.BET_SMALL) {
        while (g.phase == Phase.BETTING) assertTrue(g.bet(amount))
    }

    /** 딜러 단계 끝까지 + 정산 */
    private fun finishRound(g: BlackjackGame) {
        while (g.phase == Phase.PLAYER_TURNS) g.stand()
        while (g.dealerDraw() != null) Unit
        assertTrue(g.settle())
    }

    // ── A 계산 ──

    @Test fun `A 는 1 또는 11 - soft 와 hard`() {
        val a6 = hand(A, 6)
        assertTrue(a6.isSoft)
        assertEquals(7, a6.hard)
        assertEquals(17, a6.best)
        assertEquals("7/17", a6.label())

        val a6k = hand(A, 6, K)
        assertFalse("10 을 더하면 A 는 1", a6k.isSoft)
        assertEquals(17, a6k.best)
        assertEquals("17", a6k.label())

        val aa = hand(A, A)
        assertEquals(12, aa.best)
        assertEquals("2/12", aa.label())

        val aa9 = hand(A, A, 9)
        assertEquals("A 하나만 11", 21, aa9.best)
        assertEquals("21", aa9.label())
        assertFalse(aa9.isBlackjack)

        val four = hand(A, A, A, A, 7)
        assertEquals(21, four.best)
        assertTrue(four.isSoft)

        val five = hand(A, A, A, A, 8, 9)
        assertEquals("A 넷 모두 1", 21, five.best)
        assertFalse(five.isSoft)

        assertTrue(hand(A, K).isBlackjack)
        assertTrue(hand(Q, A).isBlackjack)
        assertFalse("세 장 21 은 블랙잭 아님", hand(A, 5, 5).isBlackjack)
        assertEquals("21", hand(A, K).label())
        assertEquals(10, Hand.points(c(Card.JACK)))
        assertEquals("앞 두 장만", "4/14", Hand.labelOf(listOf(c(A), c(3), c(K)), 2))
        assertEquals(14, Hand.bestOf(listOf(c(A), c(3), c(K)), 2))
        assertEquals(14, Hand.bestOf(listOf(c(A), c(3), c(K))))
    }

    @Test fun `버스트 - 21 초과면 즉시 차례 끝 패배`() {
        assertTrue(hand(K, 6, 9).isBust)
        assertFalse(hand(A, K, K).isBust) // 21
        assertTrue(hand(A, K, K, 2).isBust)
        // p0: 10,6 → 히트 9 = 25 버스트 / p1: 10,8 / 딜러: 10,6 → 5 = 21? 딜러가 뽑아도 버스트는 패배
        val g = game(2, c(10), c(10, Suit.HEART), c(10, Suit.CLUB), c(6), c(8), c(6, Suit.HEART), c(9), c(2))
        betAll(g, 20)
        assertEquals(Phase.PLAYER_TURNS, g.phase)
        assertEquals(0, g.current)
        assertEquals(c(9), g.hit())
        assertTrue(g.hands[0].isBust)
        assertTrue(g.isDone(0))
        assertEquals("버스트하면 다음 사람", 1, g.current)
        assertTrue("다음 사람은 첫 두 장이라 더블 가능", g.canDouble())
        g.stand()
        assertEquals(Phase.DEALER, g.phase)
        assertEquals(c(2), g.dealerDraw()) // 16 → 18
        assertNull(g.dealerDraw())
        assertTrue(g.settle())
        assertEquals(Outcome.BUST, g.outcomes[0])
        assertEquals(-20, g.profits[0])
        assertEquals(80, g.chips[0])
        assertEquals(Outcome.PUSH, g.outcomes[1])
        assertEquals(100, g.chips[1])
    }

    @Test fun `모두 버스트면 딜러는 뽑지 않음`() {
        val g = game(2, c(10), c(10), c(10), c(6), c(6), c(2), c(K), c(K), c(5))
        betAll(g)
        g.hit()
        g.hit()
        assertEquals(Phase.DEALER, g.phase)
        assertTrue(g.holeRevealed)
        assertFalse(g.anyLiveHand)
        assertFalse(g.dealerMustDraw())
        assertNull(g.dealerDraw())
        assertEquals(2, g.dealer.size)
        assertTrue(g.settle())
        assertEquals(Outcome.BUST, g.outcomes[1])
    }

    // ── 딜러 ──

    @Test fun `딜러는 17 미만이면 뽑고 17 이상 soft 17 포함 멈춤`() {
        assertTrue(BlackjackGame.dealerRuleDraws(hand(10, 6)))
        assertFalse(BlackjackGame.dealerRuleDraws(hand(10, 7)))
        assertFalse("soft 17 에서도 멈춤 (S17)", BlackjackGame.dealerRuleDraws(hand(A, 6)))
        assertFalse(GameConfig.DEALER_HITS_SOFT_17)
        assertTrue(BlackjackGame.dealerRuleDraws(hand(A, 5)))
        assertFalse(BlackjackGame.dealerRuleDraws(hand(A, 7)))
        assertTrue("soft 가 hard 로 바뀌어 12", BlackjackGame.dealerRuleDraws(hand(A, 6, 5)))
        assertFalse(BlackjackGame.dealerRuleDraws(hand(10, 6, 5)))

        // 딜러 2,3 → 4(9) → 7(16) → 2(18) 멈춤
        val g = game(2, c(10), c(10), c(2), c(9), c(9), c(3), c(4), c(7), c(2), c(K))
        betAll(g)
        g.stand(); g.stand()
        assertEquals(Phase.DEALER, g.phase)
        assertTrue(g.dealerMustDraw())
        assertEquals(c(4), g.dealerDraw())
        assertEquals(c(7), g.dealerDraw())
        assertEquals(16, g.dealer.best)
        assertEquals(c(2), g.dealerDraw())
        assertEquals(18, g.dealer.best)
        assertFalse(g.dealerMustDraw())
        assertNull(g.dealerDraw())
        assertTrue(g.settle())
        assertEquals(Outcome.WIN, g.outcomes[0])
        assertEquals(Outcome.WIN, g.outcomes[1])
        assertEquals(110, g.chips[0])
        assertFalse("두 번 정산 안 됨", g.settle())
    }

    @Test fun `딜러가 정산 전에 다 뽑아야 함`() {
        val g = game(2, c(10), c(10), c(K), c(9), c(9), c(6), c(K))
        betAll(g)
        g.stand(); g.stand()
        assertFalse("16 이면 더 뽑기 전 정산 불가", g.settle())
        g.dealerDraw()
        assertTrue(g.dealer.isBust)
        assertTrue(g.settle())
        assertEquals(Outcome.WIN, g.outcomes[0])
    }

    // ── 블랙잭 배당 ──

    @Test fun `블랙잭은 1점5배 내림 - 딜러도 블랙잭이면 무승부`() {
        assertEquals(15, BlackjackGame.profit(Outcome.BLACKJACK, 10))
        assertEquals(30, BlackjackGame.profit(Outcome.BLACKJACK, 20))
        assertEquals(75, BlackjackGame.profit(Outcome.BLACKJACK, 50))
        assertEquals("내림", 10, BlackjackGame.profit(Outcome.BLACKJACK, 7))
        assertEquals(20, BlackjackGame.profit(Outcome.WIN, 20))
        assertEquals(0, BlackjackGame.profit(Outcome.PUSH, 20))
        assertEquals(-20, BlackjackGame.profit(Outcome.LOSE, 20))
        assertEquals(-20, BlackjackGame.profit(Outcome.BUST, 20))

        // p0 블랙잭(A,K), p1 20 → p0 는 차례 없이 넘어감, 딜러 19
        val g = game(2, c(A), c(10), c(10), c(K), c(Q), c(9))
        betAll(g, 20)
        assertTrue(g.hands[0].isBlackjack)
        assertTrue(g.isDone(0))
        assertEquals("블랙잭은 차례 건너뜀", 1, g.current)
        finishRound(g)
        assertEquals(Outcome.BLACKJACK, g.outcomes[0])
        assertEquals(30, g.profits[0])
        assertEquals(130, g.chips[0])
        assertEquals(Outcome.WIN, g.outcomes[1])
        assertEquals(120, g.chips[1])
    }

    @Test fun `딜러 블랙잭은 바로 공개 - 플레이어 블랙잭은 무승부 나머지는 패배`() {
        // p0 A,Q 블랙잭 / p1 K,Q 20 / 딜러 A + K 블랙잭
        val g = game(2, c(A), c(K), c(A, Suit.HEART), c(Q), c(Q, Suit.HEART), c(K, Suit.HEART))
        betAll(g, 50)
        assertTrue(g.dealer.isBlackjack)
        assertTrue(g.dealerPeekedBlackjack)
        assertEquals("플레이어 차례 없이 딜러 공개", Phase.DEALER, g.phase)
        assertFalse(g.canHit())
        assertFalse(g.dealerMustDraw())
        assertTrue(g.settle())
        assertEquals(Outcome.PUSH, g.outcomes[0])
        assertEquals(100, g.chips[0])
        assertEquals(Outcome.LOSE, g.outcomes[1])
        assertEquals(50, g.chips[1])
        // 비교 함수: 딜러 블랙잭은 세 장 21 도 이김
        assertEquals(Outcome.LOSE, BlackjackGame.outcomeOf(hand(7, 7, 7), hand(A, K)))
        assertEquals(Outcome.WIN, BlackjackGame.outcomeOf(hand(10, 9), hand(10, 6, 9)))
        assertEquals(Outcome.PUSH, BlackjackGame.outcomeOf(hand(10, 8), hand(9, 9)))
        assertEquals(Outcome.LOSE, BlackjackGame.outcomeOf(hand(10, 7), hand(9, 9)))
    }

    // ── 더블 ──

    @Test fun `더블은 베팅 두 배 정확히 한 장 그리고 스탠드`() {
        // p0 5,6 = 11 → 더블 K = 21 / p1 10,7 / 딜러 10,8
        val g = game(2, c(5), c(10), c(10, Suit.HEART), c(6), c(7), c(8), c(K), c(2))
        betAll(g, 20)
        assertEquals(80, g.chips[0])
        assertTrue(g.canDouble())
        assertEquals(c(K), g.double())
        assertEquals(40, g.bets[0])
        assertEquals(60, g.chips[0])
        assertTrue(g.doubled[0])
        assertEquals("정확히 한 장", 3, g.hands[0].size)
        assertTrue(g.isDone(0))
        assertEquals(1, g.current)
        // p1: 히트 뒤에는 더블 불가
        assertTrue(g.canDouble())
        g.hit() // 2 → 19
        assertEquals(3, g.hands[1].size)
        assertFalse("첫 두 장일 때만", g.canDouble())
        assertNull(g.double())
        g.stand()
        while (g.dealerDraw() != null) Unit
        g.settle()
        assertEquals(Outcome.WIN, g.outcomes[0])
        assertEquals(40, g.profits[0])
        assertEquals(140, g.chips[0])
        assertEquals(Outcome.WIN, g.outcomes[1])
    }

    @Test fun `더블 후 버스트 - 두 배 잃음`() {
        val g = game(2, c(10), c(10), c(10), c(6), c(7), c(8), c(K))
        betAll(g, 50)
        assertEquals(c(K), g.double()) // 26
        assertTrue(g.hands[0].isBust)
        g.stand()
        finishRound(g)
        assertEquals(Outcome.BUST, g.outcomes[0])
        assertEquals(-100, g.profits[0])
        assertEquals(0, g.chips[0])
    }

    @Test fun `칩이 베팅보다 적으면 더블 불가`() {
        val g = game(2, c(5), c(5), c(10), c(6), c(6), c(8))
        g.chips[0] = 60
        g.bet(50) // 남은 10 < 50
        g.bet(50) // p1: 남은 50 = 50 → 가능
        assertEquals(10, g.chips[0])
        assertEquals(0, g.current)
        assertFalse(g.canDouble())
        assertNull(g.double())
        assertEquals(50, g.bets[0])
        g.stand()
        assertTrue(g.canDouble())
    }

    // ── 베팅 ──

    @Test fun `가진 칩보다 많이 못 걸고 10 미만이면 전부`() {
        val g = BlackjackGame(3, Random(3))
        g.chips[0] = 35
        g.chips[1] = 7
        assertEquals(0, g.current)
        assertFalse(g.canBet(50))
        assertFalse(g.bet(50))
        assertFalse("목록에 없는 금액", g.canBet(30))
        assertFalse(g.canBet(0))
        assertTrue(g.canBet(20))
        assertEquals(0, g.current)
        assertTrue(g.bet(20))
        assertEquals(15, g.chips[0])
        // p1: 7 개뿐 → 무엇을 골라도 7 전부
        assertEquals(1, g.current)
        assertTrue(g.mustBetAll(1))
        assertEquals(7, g.effectiveBet(1, 50))
        assertEquals(7, g.effectiveBet(1, 10))
        assertTrue(g.bet(50))
        assertEquals(7, g.bets[1])
        assertEquals(0, g.chips[1])
        assertFalse(g.mustBetAll(2))
        assertTrue(g.bet(50))
        assertEquals(50, g.bets[2])
        assertTrue("모두 걸면 나눠 줌", g.phase != Phase.BETTING)
        assertFalse("베팅 단계가 아니면", g.bet(10))
    }

    @Test fun `나눠 주는 순서 - 참가자 딜러 참가자 딜러`() {
        val g = game(3, c(2), c(3), c(4), c(5), c(6), c(7), c(8), c(9))
        betAll(g)
        assertEquals(listOf(c(2), c(6)), g.hands[0].cards)
        assertEquals(listOf(c(3), c(7)), g.hands[1].cards)
        assertEquals(listOf(c(4), c(8)), g.hands[2].cards)
        assertEquals("딜러 앞면 · 구멍", listOf(c(5), c(9)), g.dealer.cards)
        assertFalse(g.holeRevealed)
    }

    @Test fun `21 이 되면 자동 스탠드`() {
        val g = game(2, c(10), c(10), c(10), c(5), c(8), c(8), c(6))
        betAll(g)
        assertEquals(c(6), g.hit()) // 21
        assertEquals(21, g.hands[0].best)
        assertTrue(g.isDone(0))
        assertEquals(1, g.current)
    }

    // ── 관전 ──

    @Test fun `칩 0 이면 다음 라운드부터 관전`() {
        // p1 이 칩 10 을 모두 걸고 짐
        val g = game(3, c(10), c(10), c(10), c(10), c(9), c(6), c(9), c(9))
        g.chips[1] = 10
        betAll(g, 10)
        finishRound(g) // 딜러 19: p0 19 무, p1 16 패, p2 19 무
        assertEquals(Outcome.LOSE, g.outcomes[1])
        assertEquals(0, g.chips[1])
        assertTrue(g.nextRound())
        assertEquals(2, g.round)
        assertEquals(Phase.BETTING, g.phase)
        assertTrue(g.isSpectator(1))
        assertFalse(g.isSpectator(0))
        assertEquals(0, g.current)
        assertTrue(g.bet(10))
        assertEquals("관전자 건너뜀", 2, g.current)
        assertTrue(g.bet(10))
        assertTrue(g.hands[1].isEmpty)
        assertEquals(2, g.hands[0].size)
        while (g.phase == Phase.PLAYER_TURNS) {
            assertTrue(g.current != 1)
            g.stand()
        }
        while (g.dealerDraw() != null) Unit
        g.settle()
        assertNull(g.outcomes[1])
        assertEquals(0, g.profits[1])
        assertEquals(0, g.chips[1])
    }

    @Test fun `모두 칩이 0 이면 바로 끝 - 모두 공동 1등`() {
        val g = game(2, c(10), c(10), c(10), c(6), c(6), c(9))
        g.chips[0] = 10
        g.chips[1] = 10
        betAll(g, 10)
        finishRound(g)
        assertArrayEquals(intArrayOf(0, 0), g.chips)
        assertTrue(g.nextRound())
        assertTrue(g.gameOver)
        assertEquals(1, g.round)
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    // ── 5 라운드 + 순위 ──

    @Test fun `5 라운드 뒤 칩 많은 순 동점은 같은 순위`() {
        val g = BlackjackGame(3, Random(9))
        repeat(GameConfig.ROUNDS) { r ->
            assertEquals(r + 1, g.round)
            assertEquals(Phase.BETTING, g.phase)
            // p0·p1 은 20 (같은 결과), p2 는 16, 딜러 10,9 = 19 → 0·1 승, 2 패
            g.shoe.stackTop(listOf(c(10), c(10, Suit.HEART), c(10, Suit.CLUB), c(10, Suit.DIAMOND),
                c(K), c(K, Suit.HEART), c(6), c(9)))
            betAll(g, 10)
            while (g.phase == Phase.PLAYER_TURNS) g.stand()
            assertFalse(g.dealerMustDraw())
            assertTrue(g.settle())
            assertEquals(Phase.ROUND_OVER, g.phase)
            assertTrue(g.nextRound())
        }
        assertTrue(g.gameOver)
        assertArrayEquals(intArrayOf(150, 150, 50), g.chips)
        assertArrayEquals(intArrayOf(1, 1, 3), g.ranks())
        assertFalse(g.nextRound())
        assertFalse(g.bet(10))
        assertTrue("카드는 모두 걷힘", g.hands.all { it.isEmpty } && g.dealer.isEmpty)
    }

    @Test fun `무작위 5 라운드 - 칩 합 보존과 순위`() {
        for (seed in 1..20) {
            val g = BlackjackGame(4, Random(seed))
            val rnd = Random(seed * 31)
            var guard = 0
            while (!g.gameOver && guard++ < 1000) {
                when (g.phase) {
                    Phase.BETTING -> g.bet(GameConfig.BET_OPTIONS[rnd.nextInt(3)].takeIf { g.canBet(it) } ?: GameConfig.BET_SMALL)
                    Phase.PLAYER_TURNS -> when {
                        g.canDouble() && rnd.nextInt(5) == 0 -> g.double()
                        g.hands[g.current].best < 16 -> g.hit()
                        else -> g.stand()
                    }
                    Phase.DEALER -> if (g.dealerDraw() == null) g.settle()
                    Phase.ROUND_OVER -> {
                        for (i in 0 until 4) if (g.outcomes[i] != null) assertEquals(g.outcomes[i], BlackjackGame.outcomeOf(g.hands[i], g.dealer))
                        assertTrue(g.dealer.best >= GameConfig.DEALER_STAND || !g.anyLiveHand || g.dealerPeekedBlackjack)
                        g.nextRound()
                    }
                    Phase.GAME_OVER -> Unit
                }
                assertTrue(g.chips.all { it >= 0 })
            }
            assertTrue(g.gameOver)
            assertEquals(GameConfig.ROUNDS, g.round)
            val ranks = g.ranks()
            for (i in 0 until 4) assertEquals(1 + g.chips.count { it > g.chips[i] }, ranks[i])
            // 카드 장수 보존
            assertEquals(g.shoe.total, g.shoe.remaining + g.shoe.discards.size)
        }
    }

    // ── 슈 ──

    @Test fun `슈는 2벌 104장 - 남은 카드가 기준 미만이면 라운드 시작 때 다시 섞기`() {
        val shoe = Shoe(Random(5))
        assertEquals(104, shoe.total)
        assertEquals(104, shoe.remaining)
        assertEquals("같은 카드 두 장씩", 2, (0 until 104).map { shoe.draw() }.count { it == c(A) })
        assertEquals(0, shoe.remaining)

        // 라운드 1 → 2: 충분히 남아 있으면 섞지 않음
        val g = BlackjackGame(4, Random(7))
        assertFalse(g.reshuffledThisRound)
        betAll(g)
        finishRound(g)
        assertTrue(g.nextRound())
        assertFalse(g.reshuffledThisRound)
        assertEquals(0, g.shoe.reshuffles)
        // 기준 + 5 장만 남기고 한 라운드 → 기준 미만 → 다음 라운드 시작 때 섞기
        while (g.shoe.remaining > GameConfig.RESHUFFLE_BELOW + 5) g.shoe.discard(listOf(g.shoe.draw()))
        betAll(g)
        finishRound(g)
        assertTrue(g.shoe.needsReshuffle)
        val inHands = g.hands.sumOf { it.size } + g.dealer.size
        assertEquals("장수 보존", 104, g.shoe.remaining + g.shoe.discards.size + inHands)
        assertTrue(g.nextRound())
        assertTrue(g.reshuffledThisRound)
        assertEquals("버린 카드 모두 돌아옴", 104, g.shoe.remaining)
        assertEquals(0, g.shoe.discards.size)
        assertEquals(1, g.shoe.reshuffles)
    }

    @Test fun `슈가 비면 버린 카드로 채워 뽑음`() {
        val shoe = Shoe(Random(2), decks = 1)
        val out = ArrayList<Card>()
        repeat(52) { out.add(shoe.draw()) }
        assertEquals(0, shoe.remaining)
        shoe.discard(out.subList(0, 40))
        val next = shoe.draw()
        assertTrue(next in out)
        assertEquals(39, shoe.remaining)
        assertEquals(0, shoe.discards.size)
        // 위에 쌓기: 장수 그대로
        val s2 = Shoe(Random(3))
        s2.stackTop(listOf(c(A), c(K), c(A)))
        assertEquals(104, s2.remaining)
        assertEquals(c(A), s2.draw())
        assertEquals(c(K), s2.draw())
        assertEquals(c(A), s2.draw())
    }
}
