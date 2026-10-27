package com.siwoo.party.games.oldmaid

import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Suit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OldMaidGameTest {

    private fun c(s: Suit, r: Int) = Card.of(s, r)
    private val S = Suit.SPADE
    private val H = Suit.HEART
    private val D = Suit.DIAMOND
    private val C = Suit.CLUB
    private val JK = Card.BLACK_JOKER

    @Test fun `53장을 모두 나누고 장수 차이는 1장 이하`() {
        for (n in 2..4) {
            val g = OldMaidGame(n, Random(n))
            val all = g.dealt.flatten()
            assertEquals(GameConfig.TOTAL_CARDS, all.size)
            assertEquals("같은 카드 두 번 없음", 53, all.toSet().size)
            assertEquals("조커 한 장", 1, all.count { it.isJoker })
            val sizes = g.dealt.map { it.size }
            assertTrue(sizes.max() - sizes.min() <= 1)
            assertEquals("0번부터 돌아가며 나눔 → 앞사람이 많거나 같음", 53 / n + (if (53 % n > 0) 1 else 0), sizes[0])
            assertEquals(g.dealt, g.hands)
        }
    }

    @Test fun `같은 숫자 두 장은 짝으로 버림`() {
        val hand = mutableListOf(c(S, 7), c(H, 3), c(D, 7), JK, c(C, 12))
        val pairs = OldMaidGame.removePairs(hand)
        assertEquals(1, pairs.size)
        assertEquals(7, pairs[0].rank)
        assertEquals(listOf(c(H, 3), JK, c(C, 12)), hand)
    }

    @Test fun `같은 숫자 세 장이면 한 장 남음`() {
        val hand = mutableListOf(c(S, 5), c(H, 5), c(D, 5), c(S, 9))
        val pairs = OldMaidGame.removePairs(hand)
        assertEquals(1, pairs.size)
        assertEquals(listOf(c(D, 5), c(S, 9)), hand)
    }

    @Test fun `같은 숫자 네 장이면 두 쌍`() {
        val hand = mutableListOf(c(S, 1), c(H, 1), c(S, 2), c(D, 1), c(C, 1))
        val pairs = OldMaidGame.removePairs(hand)
        assertEquals(2, pairs.size)
        assertTrue(pairs.all { it.rank == 1 })
        assertEquals(listOf(c(S, 2)), hand)
    }

    @Test fun `조커는 짝이 없음`() {
        val hand = mutableListOf(Card.BLACK_JOKER, Card.RED_JOKER)
        assertTrue(OldMaidGame.removePairs(hand).isEmpty())
        assertEquals(2, hand.size)
    }

    @Test fun `처음 짝 버리기 후 손패에는 같은 숫자가 없고 카드 수는 보존`() {
        for (n in 2..4) {
            val g = OldMaidGame(n, Random(10 + n))
            val pairs = g.discardInitialPairs()
            for (p in 0 until n) {
                val ranks = g.hands[p].filter { !it.isJoker }.map { it.rank }
                assertEquals(ranks.size, ranks.toSet().size)
                assertEquals(g.dealt[p].size, g.hands[p].size + pairs[p].size * 2)
            }
            assertEquals(53, g.hands.sumOf { it.size } + g.pile.size)
            assertEquals(pairs.sumOf { it.size } * 2, g.pile.size)
        }
    }

    @Test fun `가져온 카드가 짝이면 버리고 아니면 손패 끝에`() {
        val g = OldMaidGame.withHands(listOf(listOf(c(S, 4), c(S, 8)), listOf(c(H, 4), JK, c(H, 9))), Random(1))
        g.discardInitialPairs()
        assertEquals(0, g.current)
        assertEquals(1, g.target)
        g.beginTurn()
        val k = g.hands[1].indexOf(c(H, 4))
        val r = g.draw(k)
        assertEquals(c(H, 4), r.card)
        assertEquals(c(S, 4), r.pair!!.a)
        assertEquals(c(H, 4), r.pair!!.b)
        assertEquals(listOf(c(S, 8)), g.hands[0])
        assertEquals(listOf(c(S, 4), c(H, 4)), g.pile)
        assertEquals("다음은 빼앗긴 사람 차례", 1, g.current)
        // 1번이 0번에게서 ♠8 → 짝 없음 → 끝에 붙음, 0번은 다 비워서 1등
        g.beginTurn()
        val r2 = g.draw(0)
        assertNull(r2.pair)
        assertEquals(c(S, 8), g.hands[1].last())
        assertEquals(listOf(0), r2.finished)
        assertTrue(r2.gameOver)
        assertEquals(1, g.thief)
        assertArrayEquals(intArrayOf(1, 2), g.finalRanks())
    }

    @Test fun `가져온 조커는 짝이 되지 않음`() {
        val g = OldMaidGame.withHands(listOf(listOf(c(S, 4)), listOf(JK, c(H, 9)), listOf(c(D, 9), c(C, 4))), Random(2))
        g.discardInitialPairs()
        g.beginTurn()
        val r = g.draw(g.hands[1].indexOf(JK))
        assertNull(r.pair)
        assertEquals(0, g.jokerHolder())
        assertFalse(r.gameOver)
    }

    @Test fun `차례와 가져올 사람은 끝난 사람을 건너뜀`() {
        // 1번은 처음 짝 버리기로 바로 끝 → 0번은 2번에게서 가져온다
        val g = OldMaidGame.withHands(listOf(
            listOf(c(S, 2), c(S, 3)),
            listOf(c(H, 5), c(D, 5)),
            listOf(c(H, 2), c(D, 6)),
            listOf(c(H, 3), JK, c(C, 6)),
        ), Random(3))
        g.discardInitialPairs()
        assertTrue(g.isFinished(1))
        assertEquals(1, g.ranks[1])
        assertEquals(0, g.current)
        assertEquals(2, g.target)
        assertEquals(2, g.nextPlaying(0))
        assertEquals(0, g.nextPlaying(3))
        g.beginTurn()
        val r = g.draw(g.hands[2].indexOf(c(H, 2))) // ♠2 짝
        assertEquals(2, r.pair!!.rank)
        assertEquals("1번은 건너뛰고 2번 차례", 2, g.current)
        assertEquals(3, g.target)
        g.beginTurn()
        val r2 = g.draw(g.hands[3].indexOf(c(C, 6))) // 2번 ♦6 짝 → 2번 비어서 끝
        assertEquals(listOf(2), r2.finished)
        assertEquals(2, g.ranks[2])
        assertEquals("2번이 끝났으니 3번 차례, 가져올 사람은 0번", 3, g.current)
        assertEquals(0, g.target)
    }

    @Test fun `빼앗긴 사람과 가져간 사람이 동시에 끝나면 빼앗긴 사람이 먼저`() {
        val g = OldMaidGame.withHands(listOf(listOf(c(S, 7)), listOf(c(H, 7)), listOf(JK, c(D, 1)), listOf(c(C, 1))), Random(4))
        // 2번 ♦A 와 3번 ♣A 는 서로 다른 손패라 처음엔 짝 아님
        g.discardInitialPairs()
        g.beginTurn()
        val r = g.draw(0) // 0번이 1번의 ♥7 → 짝 → 둘 다 빈 손
        assertEquals(listOf(1, 0), r.finished)
        assertEquals(1, g.ranks[1])
        assertEquals(2, g.ranks[0])
        assertEquals("다음은 2번", 2, g.current)
        assertEquals(3, g.target)
        g.beginTurn()
        val r2 = g.draw(0) // 2번이 3번의 ♣A → 짝, 3번 끝 → 2번은 조커만 → 도둑
        assertEquals(listOf(3), r2.finished)
        assertTrue(r2.gameOver)
        assertEquals(2, g.thief)
        assertArrayEquals(intArrayOf(2, 1, 4, 3), g.finalRanks())
    }

    @Test fun `처음부터 빈 손이면 같은 순위로 함께 끝`() {
        val g = OldMaidGame.withHands(listOf(listOf(c(S, 2), c(H, 2)), listOf(c(S, 3), c(H, 3)), listOf(JK, c(S, 9)), listOf(c(H, 9))), Random(5))
        g.discardInitialPairs()
        assertEquals(1, g.ranks[0])
        assertEquals(1, g.ranks[1])
        assertEquals(2, g.current)
        assertEquals(3, g.target)
        g.beginTurn()
        val r = g.draw(0)
        assertTrue(r.gameOver)
        assertArrayEquals(intArrayOf(1, 1, 4, 3), g.finalRanks())
    }

    @Test fun `처음 짝 버리기로 바로 끝날 수도 있음`() {
        val g = OldMaidGame.withHands(listOf(listOf(c(S, 2), c(H, 2)), listOf(JK)), Random(6))
        g.discardInitialPairs()
        assertTrue(g.gameOver)
        assertEquals(1, g.thief)
        assertArrayEquals(intArrayOf(1, 2), g.finalRanks())
    }

    @Test fun `가져오기 전 상대 손패를 섞음`() {
        val big = Card.STANDARD.filter { it.suit == S } + JK
        val g = OldMaidGame.withHands(listOf(listOf(c(H, 1)), big), Random(7))
        g.discardInitialPairs()
        val before = g.hands[1].toList()
        g.beginTurn()
        assertEquals(before.toSet(), g.hands[1].toSet())
        assertTrue("순서가 바뀜", before != g.hands[1])
    }

    @Test fun `무작위 게임은 2·3·4인 모두 끝나고 카드가 보존됨`() {
        for (n in 2..4) {
            repeat(60) { seed ->
                val rnd = Random(seed * 31 + n)
                val g = OldMaidGame(n, rnd)
                g.discardInitialPairs()
                var steps = 0
                while (!g.gameOver) {
                    val t = g.beginTurn()
                    assertTrue(t != g.current && !g.isFinished(t) && !g.isFinished(g.current))
                    g.draw(rnd.nextInt(g.count(t)))
                    assertEquals(53, g.hands.sumOf { it.size } + g.pile.size)
                    for (p in 0 until n) {
                        val ranks = g.hands[p].filter { !it.isJoker }.map { it.rank }
                        assertEquals("손패에 짝이 남지 않음", ranks.size, ranks.toSet().size)
                    }
                    steps++
                    assertTrue("끝나지 않음 n=$n seed=$seed", steps < 5000)
                }
                assertEquals(52, g.pile.size)
                assertEquals(listOf(Card.BLACK_JOKER), g.hands[g.thief])
                assertEquals(g.thief, g.jokerHolder())
                val ranks = g.finalRanks()
                assertEquals("도둑은 꼴찌", n, ranks[g.thief])
                assertEquals(1, ranks.count { it == n })
                assertTrue(ranks.all { it in 1..n })
                assertEquals(n, g.finishOrder.size)
                assertEquals(g.thief, g.finishOrder.last())
            }
        }
    }
}
