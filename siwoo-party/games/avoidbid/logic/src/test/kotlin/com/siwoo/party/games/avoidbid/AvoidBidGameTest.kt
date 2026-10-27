package com.siwoo.party.games.avoidbid

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AvoidBidGameTest {

    // ── 연속 묶음 점수 ──

    @Test
    fun `연속 묶음은 가장 낮은 숫자만 센다`() {
        assertEquals(12, Scoring.cardPoints(listOf(12, 13, 14)))
        assertEquals(listOf(12..14), Scoring.runs(listOf(12, 13, 14)))
    }

    @Test
    fun `낱장 카드는 각각 더한다`() {
        assertEquals(3 + 10 + 35, Scoring.cardPoints(listOf(3, 10, 35)))
        assertEquals(3, Scoring.runs(listOf(3, 10, 35)).size)
        assertEquals(0, Scoring.cardPoints(emptyList()))
        assertEquals(20, Scoring.cardPoints(listOf(20)))
    }

    @Test
    fun `묶음 여러 개와 낱장이 섞이면 묶음마다 가장 낮은 숫자`() {
        // 5-6-7, 10, 20-21, 33-34-35
        val cards = listOf(5, 6, 7, 10, 20, 21, 33, 34, 35)
        assertEquals(5 + 10 + 20 + 33, Scoring.cardPoints(cards))
        assertEquals(listOf(5..7, 10..10, 20..21, 33..35), Scoring.runs(cards))
    }

    @Test
    fun `순서가 섞인 입력도 같은 점수`() {
        val cards = listOf(21, 7, 35, 5, 10, 34, 20, 6, 33)
        assertEquals(5 + 10 + 20 + 33, Scoring.cardPoints(cards))
        assertEquals(listOf(5..7, 10..10, 20..21, 33..35), Scoring.runs(cards))
    }

    @Test
    fun `점수는 카드 점수에서 남은 칩을 뺀다`() {
        assertEquals(5 + 10 - 7, Scoring.score(listOf(10, 5, 6), 7))
        assertEquals(-11, Scoring.score(emptyList(), 11))
    }

    @Test
    fun `화면용 묶음 머리 표시`() {
        val sorted = intArrayOf(3, 4, 5, 9, 11, 12)
        val out = BooleanArray(8)
        Scoring.markRunHeads(sorted, sorted.size, out)
        assertArrayEquals(booleanArrayOf(true, false, false, true, true, false), out.copyOf(6))
    }

    // ── 덱 ──

    @Test
    fun `9장을 빼고 24장을 쓴다`() {
        repeat(20) { seed ->
            val g = AvoidBidGame.deal(3, Random(seed))
            assertEquals(GameConfig.REMOVED_CARDS, g.removed.size)
            assertEquals(9, g.removed.size)
            // 공개 카드 1장 + 덱 23장 = 24장
            assertEquals(GameConfig.USED_CARDS - 1, g.deckRemaining)
            assertEquals(24, GameConfig.USED_CARDS)
            val used = ArrayList<Int>()
            used.add(g.card)
            while (!g.gameOver) used.add(g.take().nextCard.takeIf { it >= 0 } ?: break)
            assertEquals(24, used.size)
            // 쓴 카드 + 뺀 카드 = 3~35 전부, 겹침 없음
            assertEquals((3..35).toList(), (used + g.removed).sorted())
        }
    }

    @Test
    fun `시작 상태`() {
        val g = AvoidBidGame.deal(4, Random(1))
        assertEquals(0, g.current)
        assertEquals(0, g.pot)
        assertArrayEquals(IntArray(4) { 11 }, g.chips)
        assertTrue(g.card in 3..35)
        assertFalse(g.gameOver)
        for (p in 0 until 4) assertEquals(-11, g.score(p))
    }

    // ── 넘기기·가져가기 ──

    private fun game(n: Int, vararg deck: Int) = AvoidBidGame(n, deck.toList(), listOf(1, 2))

    @Test
    fun `넘기면 칩 1개가 카드 위로 가고 다음 사람 차례`() {
        val g = game(3, 20, 30)
        val r = g.pass()
        assertEquals(Passed(0, 20, 1, 10, 1), r)
        assertEquals(1, g.current)
        assertEquals(1, g.pot)
        g.pass()
        g.pass()
        assertEquals("한 바퀴 돌아 처음 사람", 0, g.current)
        assertEquals(3, g.pot)
        assertArrayEquals(intArrayOf(10, 10, 10), g.chips)
        assertEquals("카드는 그대로", 20, g.card)
    }

    @Test
    fun `칩이 0개면 넘길 수 없고 반드시 가져가야 한다`() {
        val g = game(2, 20, 30)
        g.chips[0] = 0
        assertFalse(g.canPass())
        val failed = runCatching { g.pass() }
        assertTrue(failed.exceptionOrNull() is IllegalStateException)
        assertEquals("상태는 그대로", 0, g.current)
        assertEquals(0, g.pot)
        val t = g.take()
        assertEquals(0, t.player)
        assertEquals(20, t.card)
        // 가져간 사람이 계속하므로 여전히 0번 차례, 칩도 여전히 0개라 또 가져가야 한다
        assertEquals(0, g.current)
        assertFalse(g.canPass())
    }

    @Test
    fun `가져가면 카드 위의 칩도 함께 받는다`() {
        val g = game(3, 25, 8, 9)
        g.pass(); g.pass(); g.pass(); g.pass() // 0,1,2,0 이 넘김 → 칩 4개
        assertEquals(1, g.current)
        val t = g.take()
        assertEquals(1, t.player)
        assertEquals(25, t.card)
        assertEquals(4, t.chips)
        assertEquals(0, g.pot)
        assertArrayEquals(intArrayOf(9, 10 + 4, 10), g.chips)
        assertEquals(listOf(25), g.cardsOf(1))
        assertEquals(25 - 14, g.score(1))
    }

    @Test
    fun `가져간 사람이 다음 카드를 보고 계속 고른다`() {
        val g = game(3, 25, 8, 9)
        g.pass()
        val t = g.take()
        assertEquals(1, t.player)
        assertEquals(8, t.nextCard)
        assertEquals(8, g.card)
        assertEquals("가져간 사람이 그대로 차례", 1, g.current)
        assertEquals(1, g.deckRemaining)
        assertFalse(t.gameOver)
        assertFalse(t.joinedRun)
        g.take()
        val t3 = g.take()
        assertTrue("8 → 9 이어짐", t3.joinedRun)
        assertEquals(listOf(8, 9, 25), g.cardsOf(1))
        assertEquals("칩 11 + 받은 1", 8 + 25 - 12, g.score(1))
    }

    @Test
    fun `덱이 비면 게임 끝`() {
        val g = game(2, 10, 11)
        g.take()
        assertFalse(g.gameOver)
        val t = g.take()
        assertTrue(t.gameOver)
        assertTrue(g.gameOver)
        assertEquals(-1, g.card)
        assertEquals(0, g.deckRemaining)
        assertFalse(g.canPass())
        assertTrue(runCatching { g.take() }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { g.pass() }.exceptionOrNull() is IllegalStateException)
    }

    // ── 순위 ──

    @Test
    fun `점수가 낮을수록 높은 순위`() {
        // 0번: 30 가져감(칩 11) = 19, 1번: 5 가져감 + 칩 1 = 5 - 12 = -7, 2번: 아무것도 없음, 칩 10 = -10
        val g = game(3, 30, 5)
        g.take() // 0번 30
        g.pass() // 0번 칩 10 → 5 위에 1개
        val t = g.take() // 1번 5 + 칩 1 → 게임 끝
        assertTrue(t.gameOver)
        assertArrayEquals(intArrayOf(30 - 10, 5 - 12, -11), g.scores())
        assertArrayEquals(intArrayOf(3, 2, 1), g.finalRanks())
    }

    @Test
    fun `같은 점수는 같은 순위`() {
        // 0번이 12·13·14 를 모두 가져가고, 칩 수를 바꿔 같은 점수를 만든다
        val g = game(3, 12, 13, 14)
        g.take(); g.take() // 0번 12, 13 → 12 - 11 = 1
        g.pass() // 0번 칩 10 → 12 - 10 = 2
        g.pass() // 1번 칩 10
        g.pass() // 2번 칩 10
        g.chips[2] = 11 // 점수 맞추기 (2번: -11)
        val t = g.take() // 0번 14 + 칩 3 → 12 - 13 = -1
        assertTrue(t.gameOver)
        // 0번 -1, 1번 -10, 2번 -11
        assertArrayEquals(intArrayOf(-1, -10, -11), g.scores())
        assertArrayEquals(intArrayOf(3, 2, 1), g.finalRanks())
        g.chips[1] = 11
        assertArrayEquals("동점 공동 1위", intArrayOf(3, 1, 1), g.finalRanks())
        g.chips[0] = 12 + 11 // 0번도 12 - 23 = -11
        assertArrayEquals("모두 동점", intArrayOf(1, 1, 1), g.finalRanks())
    }

    @Test
    fun `무작위로 끝까지 진행해도 규칙이 지켜진다`() {
        for (n in 2..4) repeat(30) { seed ->
            val rnd = Random(seed * 7 + n)
            val g = AvoidBidGame.deal(n, rnd)
            val total = n * GameConfig.START_CHIPS
            var takes = 0
            var guard = 0
            while (!g.gameOver && guard++ < 1000) {
                if (g.canPass() && rnd.nextInt(3) != 0) g.pass() else { g.take(); takes++ }
                // 칩 총합은 늘 같다
                assertEquals(total, g.chips.sum() + g.pot)
                assertTrue(g.chips.all { it >= 0 })
            }
            assertTrue(g.gameOver)
            assertEquals(24, takes)
            assertEquals(24, (0 until n).sumOf { g.cardsOf(it).size })
            val ranks = g.finalRanks()
            val scores = g.scores()
            for (i in 0 until n) for (j in 0 until n) {
                if (scores[i] < scores[j]) assertTrue(ranks[i] < ranks[j])
                if (scores[i] == scores[j]) assertEquals(ranks[i], ranks[j])
            }
            assertTrue(ranks.contains(1))
        }
    }
}
