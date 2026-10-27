package com.siwoo.party.games.highlow

import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Suit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HighLowGameTest {

    private fun c(rank: Int, suit: Suit = Suit.SPADE) = Card.of(suit, rank)

    /** 기준 카드 base, 다음에 나올 카드 next… 로 정한 게임 */
    private fun gameN(n: Int, base: Card, vararg next: Card): HighLowGame {
        val g = HighLowGame(n, Random(3))
        g.setBaseForTest(base)
        g.stackTop(next.toList())
        return g
    }

    // ── 시작 ──

    @Test fun `처음엔 첫 사람 차례 기준 카드 공개 점수 0`() {
        val g = HighLowGame(3, Random(1))
        assertEquals(0, g.current)
        assertEquals(Phase.GUESSING, g.phase)
        assertEquals(0L, g.pot)
        assertEquals(1L, g.nextPot)
        assertFalse(g.canStop)
        assertEquals(51, g.deckSize)
        assertFalse(g.base.isJoker)
        assertEquals(1, g.turnsStarted[0])
        assertEquals(0, g.turnsStarted[1])
        assertEquals(15, g.totalTurns)
    }

    @Test fun `조커 없는 52장`() {
        val g = HighLowGame(2, Random(9))
        val all = g.deck.toList() + g.base
        assertEquals(52, all.size)
        assertEquals(52, all.toSet().size)
        assertTrue(all.none { it.isJoker })
    }

    @Test fun `높은 숫자 낮은 숫자 종류 수 A 낮음 K 높음`() {
        val g = gameN(2, c(7))
        assertEquals(6, g.higherRanks)
        assertEquals(6, g.lowerRanks)
        g.setBaseForTest(c(Card.ACE, Suit.HEART))
        assertEquals(12, g.higherRanks)
        assertEquals(0, g.lowerRanks)
        g.setBaseForTest(c(Card.KING, Suit.CLUB))
        assertEquals(0, g.higherRanks)
        assertEquals(12, g.lowerRanks)
    }

    // ── 배수 ──

    @Test fun `배수 성공할 때마다 1 2 4 8 두 배`() {
        val g = gameN(2, c(2), c(5), c(9), c(3), c(12, Suit.HEART))
        val r1 = g.guess(Guess.HIGHER)
        assertTrue(r1.success)
        assertEquals(1, r1.streak)
        assertEquals(1L, r1.pot)
        assertEquals(1L, g.pot)
        assertEquals(2L, g.nextPot)
        val r2 = g.guess(Guess.HIGHER)
        assertEquals(2L, r2.pot)
        val r3 = g.guess(Guess.LOWER)
        assertEquals(4L, r3.pot)
        val r4 = g.guess(Guess.HIGHER)
        assertEquals(8L, r4.pot)
        assertEquals(4, g.streak)
        assertEquals(Phase.GUESSING, g.phase)
    }

    @Test fun `점수 공식 2의 k-1 제곱`() {
        assertEquals(0L, HighLowGame.potFor(0))
        assertEquals(1L, HighLowGame.potFor(1))
        assertEquals(2L, HighLowGame.potFor(2))
        assertEquals(4L, HighLowGame.potFor(3))
        assertEquals(8L, HighLowGame.potFor(4))
        assertEquals(512L, HighLowGame.potFor(10))
        assertTrue(HighLowGame.potFor(200) > 0L) // 넘치지 않음
    }

    @Test fun `공개된 카드가 다음 기준 카드`() {
        val g = gameN(2, c(4), c(8, Suit.DIAMOND))
        val r = g.guess(Guess.HIGHER)
        assertEquals(c(4), r.base)
        assertEquals(c(8, Suit.DIAMOND), r.drawn)
        assertEquals(c(8, Suit.DIAMOND), g.base)
        assertEquals(c(4), g.discard.last())
        assertEquals(5, g.higherRanks)
    }

    // ── 저장 ──

    @Test fun `저장 멈추면 점수가 합계로 가고 다음 사람`() {
        val g = gameN(3, c(3), c(6), c(10))
        g.guess(Guess.HIGHER)
        g.guess(Guess.HIGHER)
        assertTrue(g.canStop)
        val s = g.stop()!!
        assertEquals(0, s.player)
        assertEquals(2L, s.saved)
        assertEquals(2L, s.total)
        assertEquals(2, s.streak)
        assertEquals(2L, g.totals[0])
        assertEquals(0L, g.pot)
        assertEquals(Phase.TURN_OVER, g.phase)
        assertEquals(TurnEnd.SAVED, g.lastEnd)
        assertEquals(1, g.turnsDone[0])
        assertTrue(g.nextTurn())
        assertEquals(1, g.current)
        assertEquals(Phase.GUESSING, g.phase)
        assertEquals(0, g.streak)
        assertEquals(1, g.turnsStarted[1])
        assertEquals(2, g.turnNumber)
    }

    @Test fun `성공 전에는 저장 불가`() {
        val g = gameN(2, c(7))
        assertFalse(g.canStop)
        assertNull(g.stop())
        assertEquals(Phase.GUESSING, g.phase)
        assertEquals(0L, g.totals[0])
    }

    @Test fun `저장은 쌓인다`() {
        val g = gameN(2, c(2), c(5))
        g.guess(Guess.HIGHER)
        g.stop()
        g.nextTurn()
        // 2번 사람 실패
        g.setBaseForTest(c(9))
        g.stackTop(listOf(c(11)))
        g.guess(Guess.LOWER)
        g.nextTurn()
        // 다시 1번 사람: 3연속 → 4점
        g.setBaseForTest(c(Card.ACE))
        g.stackTop(listOf(c(4), c(8), c(12)))
        repeat(3) { g.guess(Guess.HIGHER) }
        assertEquals(4L, g.stop()!!.saved)
        assertEquals(5L, g.totals[0])
        assertEquals(0L, g.totals[1])
    }

    // ── 실패 ──

    @Test fun `틀리면 저장 안 된 점수 0 차례 끝`() {
        val g = gameN(2, c(3), c(6), c(9), c(4))
        g.guess(Guess.HIGHER)
        g.guess(Guess.HIGHER)
        assertEquals(2L, g.pot)
        val r = g.guess(Guess.HIGHER) // 4 < 9 → 실패
        assertFalse(r.success)
        assertFalse(r.tie)
        assertEquals(2L, r.lostPot)
        assertEquals(0L, r.pot)
        assertEquals(0L, g.pot)
        assertEquals(0L, g.totals[0])
        assertEquals(Phase.TURN_OVER, g.phase)
        assertEquals(TurnEnd.FAILED, g.lastEnd)
        assertFalse(g.canStop)
        assertNull(g.stop())
    }

    @Test fun `같은 숫자는 실패 높다든 낮다든`() {
        val g = gameN(2, c(8), c(8, Suit.HEART))
        val r = g.guess(Guess.HIGHER)
        assertFalse(r.success)
        assertTrue(r.tie)
        g.nextTurn()
        g.setBaseForTest(c(5, Suit.CLUB))
        g.stackTop(listOf(c(5, Suit.DIAMOND)))
        val r2 = g.guess(Guess.LOWER)
        assertFalse(r2.success)
        assertTrue(r2.tie)
    }

    @Test fun `실패해도 이미 저장한 점수는 그대로`() {
        val g = gameN(2, c(2), c(7))
        g.guess(Guess.HIGHER)
        g.stop()
        g.nextTurn()
        g.nextTurn() // 차례 끝이 아니면 아무 일 없음
        assertEquals(1, g.current)
        g.setBaseForTest(c(5))
        g.stackTop(listOf(c(9)))
        g.guess(Guess.LOWER)
        g.nextTurn()
        g.setBaseForTest(c(10))
        g.stackTop(listOf(c(2), c(3)))
        g.guess(Guess.LOWER)
        val r = g.guess(Guess.LOWER) // 3 > 2 실패
        assertFalse(r.success)
        assertEquals(1L, r.lostPot)
        assertEquals(1L, g.totals[0])
    }

    @Test fun `고르는 중이 아니면 고를 수 없음`() {
        val g = gameN(2, c(8), c(8, Suit.HEART))
        g.guess(Guess.HIGHER)
        try {
            g.guess(Guess.HIGHER)
            error("예외가 나야 함")
        } catch (e: IllegalStateException) {
            // 기대
        }
    }

    // ── 차례 수·끝 ──

    @Test fun `1인당 5차례 돌아가며 그 뒤 게임 끝`() {
        for (n in 2..4) {
            val g = HighLowGame(n, Random(n * 7))
            val order = ArrayList<Int>()
            var guard = 0
            while (!g.isOver && guard++ < 1000) {
                order.add(g.current)
                // 실패할 때까지 계속 높다 (A면 저장해서 끝)
                while (g.phase == Phase.GUESSING) {
                    if (g.canStop && g.streak >= 2) g.stop() else g.guess(if (g.higherRanks >= g.lowerRanks) Guess.HIGHER else Guess.LOWER)
                }
                g.nextTurn()
            }
            assertTrue(g.isOver)
            assertEquals(5 * n, order.size)
            for (k in order.indices) assertEquals(k % n, order[k])
            for (i in 0 until n) {
                assertEquals(5, g.turnsDone[i])
                assertEquals(5, g.turnsStarted[i])
            }
            assertEquals(5 * n, g.turnNumber)
            assertFalse(g.nextTurn())
            assertEquals(Phase.OVER, g.phase)
        }
    }

    @Test fun `마지막 차례 표시`() {
        val g = HighLowGame(2, Random(4), turnsPerPlayer = 2)
        assertFalse(g.isLastTurnOfPlayer)
        // 실패시키기
        fun fail() {
            g.setBaseForTest(c(Card.KING, Suit.DIAMOND))
            g.stackTop(listOf(c(Card.KING, Suit.CLUB)))
            g.guess(Guess.LOWER)
        }
        fail(); g.nextTurn()
        fail(); g.nextTurn()
        assertEquals(0, g.current)
        assertTrue(g.isLastTurnOfPlayer)
        fail(); g.nextTurn()
        fail()
        assertFalse(g.nextTurn())
        assertTrue(g.isOver)
    }

    // ── 순위 ──

    @Test fun `순위 저장 합 많은 순 동점 같은 순위`() {
        val g = HighLowGame(4, Random(2))
        g.totals[0] = 5; g.totals[1] = 12; g.totals[2] = 5; g.totals[3] = 0
        assertArrayEquals(intArrayOf(2, 1, 2, 4), g.ranks())
        g.totals[3] = 12
        assertArrayEquals(intArrayOf(3, 1, 3, 1), g.ranks())
        g.totals.fill(0)
        assertArrayEquals(intArrayOf(1, 1, 1, 1), g.ranks())
    }

    @Test fun `끝까지 진행한 순위는 합계와 일치`() {
        val g = HighLowGame(3, Random(77))
        while (!g.isOver) {
            while (g.phase == Phase.GUESSING) {
                if (g.canStop && g.streak >= g.current + 1) g.stop()
                else g.guess(if (g.higherRanks >= g.lowerRanks) Guess.HIGHER else Guess.LOWER)
            }
            g.nextTurn()
        }
        val r = g.ranks()
        for (i in 0 until 3) assertEquals(1 + g.totals.count { it > g.totals[i] }, r[i])
    }

    // ── 다시 섞기 ──

    @Test fun `덱이 비면 버린 카드를 다시 섞는다`() {
        val g = HighLowGame(2, Random(5), turnsPerPlayer = 100)
        var sawReshuffle = false
        var guard = 0
        while (!sawReshuffle && guard++ < 500) {
            if (g.phase != Phase.GUESSING) g.nextTurn()
            val before = g.deckSize
            val r = g.guess(if (g.higherRanks >= g.lowerRanks) Guess.HIGHER else Guess.LOWER)
            if (r.reshuffled) {
                sawReshuffle = true
                assertEquals(0, before)
                assertTrue(g.reshuffles >= 1)
                // 보이는 기준 카드 1장 + 덱 + 버린 더미 = 52장, 겹침 없음
                val all = g.deck.toList() + g.discard + g.base
                assertEquals(52, all.size)
                assertEquals(52, all.toSet().size)
                // 섞을 때 들고 있던 기준 카드(r.base)는 덱에 없음 → 버린 더미 맨 위
                assertFalse(r.base in g.deck)
                assertEquals(r.base, g.discard.last())
            }
        }
        assertTrue(sawReshuffle)
    }

    @Test fun `차례 시작 카드에서 다시 섞여도 표시`() {
        val g = HighLowGame(2, Random(6))
        // 기준 카드 K, 덱을 비우기: 남은 카드를 모두 버린 더미로
        g.setBaseForTest(c(Card.KING))
        while (g.deck.isNotEmpty) g.discard.add(g.deck.draw())
        assertNull(g.stop()) // 불가
        // K 보다 높은 카드는 없음 → 실패로 차례 끝: 이 뽑기에서 섞임
        val r = g.guess(Guess.HIGHER)
        assertTrue(r.reshuffled)
        assertFalse(r.success)
        assertTrue(g.nextTurn())
        assertFalse(g.lastDrawReshuffled)
        // 다시 덱 비우고 다음 차례 시작
        g.setBaseForTest(c(Card.KING, Suit.HEART))
        g.stackTop(listOf(c(Card.KING, Suit.DIAMOND)))
        g.guess(Guess.HIGHER)
        while (g.deck.isNotEmpty) g.discard.add(g.deck.draw())
        assertTrue(g.nextTurn())
        assertTrue(g.lastDrawReshuffled)
        assertEquals(2, g.reshuffles)
        val all = g.deck.toList() + g.discard + g.base
        assertEquals(52, all.toSet().size)
        assertEquals(52, all.size)
    }
}
