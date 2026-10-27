package com.siwoo.party.games.liardice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LiarDiceGameTest {

    private fun game(n: Int = 2, wild: Boolean = true, start: Int = 0, seed: Int = 1): LiarDiceGame {
        val g = LiarDiceGame(n, wild, Random(seed), start)
        g.startRound()
        return g
    }

    // ── 부르기 유효성 ──

    @Test fun `첫 부르기는 무엇이든 되고 다음 사람 차례`() {
        val g = game()
        assertNull(g.bid)
        assertTrue(g.canBid(1, 2))
        assertTrue(g.bid(3, 4))
        assertEquals(Bid(3, 4), g.bid)
        assertEquals(0, g.bidder)
        assertEquals(1, g.current)
    }

    @Test fun `개수가 더 많으면 눈이 낮아도 됨`() {
        val g = game()
        g.bid(3, 5)
        assertTrue(g.canBid(4, 2))
        assertTrue(g.bid(4, 2))
    }

    @Test fun `개수가 같으면 더 높은 눈만`() {
        val g = game()
        g.bid(3, 4)
        assertTrue(g.canBid(3, 5))
        assertTrue(g.canBid(3, 6))
        assertFalse("같은 수", g.canBid(3, 4))
        assertFalse("같은 개수 낮은 눈", g.canBid(3, 3))
    }

    @Test fun `더 낮은 부르기는 거절되고 차례가 그대로`() {
        val g = game()
        g.bid(3, 4)
        assertFalse(g.canBid(2, 6))
        assertFalse(g.bid(2, 6))
        assertEquals(Bid(3, 4), g.bid)
        assertEquals(1, g.current)
    }

    @Test fun `와일드 켜면 1 눈은 부를 수 없음`() {
        val g = game(wild = true)
        assertFalse(g.isFaceAllowed(1))
        assertFalse(g.canBid(1, 1))
        assertFalse(g.canBid(5, 1))
        assertEquals(2, g.lowestFace)
        assertEquals(Bid(1, 2), g.minBid())
    }

    @Test fun `와일드 끄면 1 눈도 가장 낮은 눈으로 부를 수 있음`() {
        val g = game(wild = false)
        assertTrue(g.isFaceAllowed(1))
        assertEquals(Bid(1, 1), g.minBid())
        assertTrue(g.bid(2, 1))
        assertTrue("같은 개수 더 높은 눈", g.canBid(2, 2))
        assertFalse("같은 개수 1 다시", g.canBid(2, 1))
        assertTrue(g.canBid(3, 1))
    }

    @Test fun `개수는 1 이상 전체 주사위 수 이하`() {
        val g = game(n = 3)
        assertEquals(15, g.totalDice)
        assertFalse(g.canBid(0, 3))
        assertFalse(g.canBid(16, 3))
        assertTrue(g.canBid(15, 3))
        assertFalse(g.canBid(3, 7))
        assertFalse(g.canBid(3, 0))
    }

    @Test fun `최대 부르기 뒤에는 올릴 수 없음`() {
        val g = game()
        assertTrue(g.bid(10, 6))
        assertFalse(g.canRaise)
        assertNull(g.minBid())
        assertTrue(g.canDoubt)
    }

    @Test fun `minBid 는 같은 개수 다음 눈 또는 한 개 더`() {
        val g = game()
        g.bid(2, 3)
        assertEquals(Bid(2, 4), g.minBid())
        g.bid(2, 6)
        assertEquals(Bid(3, 2), g.minBid())
    }

    @Test fun `라운드 첫 사람은 의심할 수 없음`() {
        val g = game()
        assertFalse(g.canDoubt)
        assertNull(g.doubt())
        assertEquals(Phase.BID, g.phase)
        g.bid(1, 2)
        assertTrue(g.canDoubt)
    }

    // ── 와일드 세기 ──

    @Test fun `와일드 켜면 1은 다른 눈으로 셈`() {
        val g = game(wild = true)
        g.setDice(0, intArrayOf(1, 1, 4, 4, 6))
        g.setDice(1, intArrayOf(4, 2, 3, 1, 5))
        assertEquals("4 세 개 + 1 세 개", 6, g.countFace(4))
        assertEquals(4, g.countFace(6))
        assertEquals(4, g.countFace(2))
        assertTrue(g.matches(1, 5))
        assertFalse(g.matches(2, 5))
    }

    @Test fun `와일드 끄면 1은 1로만 셈`() {
        val g = game(wild = false)
        g.setDice(0, intArrayOf(1, 1, 4, 4, 6))
        g.setDice(1, intArrayOf(4, 2, 3, 1, 5))
        assertEquals(3, g.countFace(4))
        assertEquals(3, g.countFace(1))
        assertEquals(1, g.countFace(6))
        assertFalse(g.matches(1, 5))
    }

    // ── 의심 판정 ──

    @Test fun `실제가 부른 수 이상이면 의심한 사람이 잃음`() {
        val g = game(wild = true)
        g.setDice(0, intArrayOf(1, 1, 4, 4, 6))
        g.setDice(1, intArrayOf(4, 2, 3, 1, 5))
        g.bid(6, 4) // 0번: 정확히 6개
        val r = g.doubt()!!
        assertEquals(0, r.bidder)
        assertEquals(1, r.doubter)
        assertEquals(6, r.actual)
        assertTrue(r.bidHeld)
        assertEquals(1, r.loser)
        assertEquals(4, g.diceLeft[1])
        assertEquals(5, g.diceLeft[0])
        assertArrayEquals(intArrayOf(5, 5), r.shownDice)
        assertFalse(r.eliminated)
        assertEquals(Phase.REVEAL, g.phase)
        assertEquals("진 사람부터", 1, r.nextStarter)
    }

    @Test fun `실제가 부른 수보다 적으면 부른 사람이 잃음`() {
        val g = game(wild = true)
        g.setDice(0, intArrayOf(1, 1, 4, 4, 6))
        g.setDice(1, intArrayOf(4, 2, 3, 1, 5))
        g.bid(3, 5)
        g.bid(7, 4) // 1번: 실제 6개
        val r = g.doubt()!!
        assertEquals(1, r.bidder)
        assertEquals(0, r.doubter)
        assertFalse(r.bidHeld)
        assertEquals(1, r.loser)
        assertEquals(4, g.diceLeft[1])
        assertEquals(9, g.totalDice)
    }

    // ── 라운드 시작·탈락·순위 ──

    @Test fun `다음 라운드는 진 사람부터, 남은 주사위만 굴림`() {
        val g = game(n = 3, start = 0)
        g.setDice(0, intArrayOf(2, 2, 2, 2, 2))
        g.setDice(1, intArrayOf(3, 3, 3, 3, 3))
        g.setDice(2, intArrayOf(5, 5, 5, 5, 5))
        g.bid(1, 6) // 0: 6 없음
        g.doubt()   // 1 이 의심 → 0 이 잃음
        assertEquals(0, g.starter)
        assertEquals(0, g.startRound())
        assertEquals(2, g.round)
        assertEquals(0, g.current)
        assertNull(g.bid)
        assertEquals(0, g.dice[0][4]) // 4개만 굴림
        assertTrue(g.dice[0][3] in 1..6)
    }

    @Test fun `진 사람이 탈락하면 다음 사람부터`() {
        val g = LiarDiceGame(3, true, Random(5), 1)
        g.diceLeft[1] = 1
        g.startRound()
        g.setDice(1, intArrayOf(3))
        g.bid(9, 6) // 1번이 터무니없이 부름
        val r = g.doubt()!! // 2번 의심 → 1번 잃고 탈락
        assertEquals(1, r.loser)
        assertTrue(r.eliminated)
        assertFalse(r.gameOver)
        assertFalse(g.isAlive(1))
        assertEquals(2, r.nextStarter)
        assertEquals(2, g.startRound())
        assertEquals(0, g.nextAlive(2))
        assertEquals("탈락자는 건너뜀", 2, g.nextAlive(0))
    }

    @Test fun `탈락한 사람은 차례에서 빠짐`() {
        val g = LiarDiceGame(3, true, Random(5), 0)
        g.diceLeft[1] = 0
        g.eliminationOrder.add(1)
        g.startRound()
        assertEquals(10, g.totalDice)
        g.bid(1, 2)
        assertEquals(2, g.current)
    }

    @Test fun `한 명 남으면 끝, 탈락 순서로 순위`() {
        val g = LiarDiceGame(4, true, Random(9), 0)
        g.diceLeft[2] = 0; g.eliminationOrder.add(2) // 첫 탈락 → 4위
        g.diceLeft[0] = 0; g.eliminationOrder.add(0) // 둘째 → 3위
        g.diceLeft[3] = 1
        g.startRound()
        assertEquals(1, g.current)
        g.setDice(1, intArrayOf(6, 6, 6, 6, 6))
        g.setDice(3, intArrayOf(2))
        g.bid(5, 6)
        val r = g.doubt()!! // 3번 의심 → 실제 5개 → 3번 잃음 → 탈락
        assertEquals(3, r.loser)
        assertTrue(r.eliminated)
        assertTrue(r.gameOver)
        assertEquals(Phase.OVER, g.phase)
        assertEquals(1, g.winner)
        assertEquals(-1, r.nextStarter)
        assertArrayEquals(intArrayOf(3, 1, 4, 2), g.finalRanks())
        assertFalse(g.canBid(1, 2))
    }

    @Test fun `시작 사람은 주입하거나 난수로`() {
        assertEquals(2, LiarDiceGame(3, true, Random(1), 2).starter)
        val s = (0 until 40).map { LiarDiceGame(4, true, Random(it)).starter }.toSet()
        assertEquals(setOf(0, 1, 2, 3), s)
    }

    // ── 무작위 끝까지 ──

    @Test fun `무작위 대결은 항상 끝나고 순위가 올바름`() {
        for (seed in 0 until 300) {
            val n = 2 + seed % 3
            val rnd = Random(seed * 31 + 7)
            val g = LiarDiceGame(n, seed % 2 == 0, Random(seed), -1)
            var rounds = 0
            while (g.phase != Phase.OVER) {
                g.startRound()
                rounds++
                assertTrue("라운드 수 $seed", rounds <= n * GameConfig.START_DICE)
                var guard = 0
                while (g.phase == Phase.BID) {
                    guard++
                    assertTrue(guard < 200)
                    val min = g.minBid()
                    if (g.canDoubt && (min == null || rnd.nextInt(3) == 0)) {
                        assertNotNull(g.doubt())
                    } else {
                        val m = min!!
                        val count = if (rnd.nextBoolean() && m.count < g.totalDice) m.count + 1 else m.count
                        val face = if (count > m.count) g.lowestFace + rnd.nextInt(GameConfig.FACES - g.lowestFace + 1) else m.face
                        assertTrue(g.bid(count, face))
                    }
                }
                assertEquals(rounds * 1, g.diceLeft.fold(n * GameConfig.START_DICE) { a, d -> a - d })
            }
            assertEquals(1, g.aliveCount)
            assertEquals(n - 1, g.eliminationOrder.size)
            val ranks = g.finalRanks()
            assertEquals((1..n).toSet(), ranks.toSet())
            assertEquals(1, ranks[g.winner])
        }
    }
}
