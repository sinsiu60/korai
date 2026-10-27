package com.siwoo.party.games.doubt

import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Suit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class DoubtGameTest {

    private fun s(r: Int) = Card.of(Suit.SPADE, r)
    private fun h(r: Int) = Card.of(Suit.HEART, r)
    private fun d(r: Int) = Card.of(Suit.DIAMOND, r)
    private fun c(r: Int) = Card.of(Suit.CLUB, r)

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try { block() } catch (e: Throwable) { if (e is T) return; throw e }
        fail("예외가 나야 함: " + T::class.java.simpleName)
    }

    // ── 나눠 주기 ──

    @Test
    fun `52장을 모두 나눈다 2인 26장 3인 18 17 17장 4인 13장`() {
        val expect = mapOf(2 to listOf(26, 26), 3 to listOf(18, 17, 17), 4 to listOf(13, 13, 13, 13))
        for ((n, sizes) in expect) {
            val g = DoubtGame(n, Random(n))
            assertEquals(sizes, (0 until n).map { g.count(it) })
            assertEquals(sizes, (0 until n).map { DoubtGame.dealSize(n, it) })
            val all = g.hands.flatten()
            assertEquals(52, all.size)
            assertEquals("같은 카드 없음", 52, all.toSet().size)
            assertTrue("조커 없음", all.none { it.isJoker })
            assertTrue(g.pile.isEmpty())
            assertEquals(0, g.current)
            assertEquals(Card.ACE, g.requiredRank)
            assertEquals(Phase.PLAY, g.phase)
        }
    }

    @Test
    fun `손패는 숫자 순서로 정렬된다`() {
        val g = DoubtGame(3, Random(5))
        for (hnd in g.hands) assertEquals(hnd.map { it.rank }.sorted(), hnd.map { it.rank })
    }

    @Test
    fun `인원은 2~4명`() {
        assertThrows<IllegalArgumentException> { DoubtGame(1, Random(1)) }
        assertThrows<IllegalArgumentException> { DoubtGame(5, Random(1)) }
    }

    // ── 선언 숫자 순서 ──

    @Test
    fun `선언 숫자는 A부터 K까지 차례로 강제되고 K 다음은 A`() {
        assertEquals(2, DoubtGame.nextRank(1))
        assertEquals(13, DoubtGame.nextRank(12))
        assertEquals(Card.ACE, DoubtGame.nextRank(Card.KING))
        // 실제 판에서: 15차례 동안 A,2,…,K,A,2
        val g = DoubtGame(2, Random(3))
        val seen = ArrayList<Int>()
        repeat(15) {
            seen.add(g.requiredRank)
            g.play(listOf(g.hands[g.current][0]))
            g.pass()
        }
        assertEquals((1..13).toList() + listOf(1, 2), seen)
    }

    @Test
    fun `의심이 있어도 없어도 숫자는 다음으로 넘어간다`() {
        val g = DoubtGame.withHands(listOf(listOf(s(1), s(5), s(9)), listOf(h(2), h(6), h(10)), listOf(d(3), d(7))))
        g.play(listOf(s(1)))
        g.doubt(1) // 진짜 → 1번이 가져감
        assertEquals(2, g.requiredRank)
        g.play(listOf(h(2)))
        g.pass()
        assertEquals(3, g.requiredRank)
    }

    // ── 판정 ──

    @Test
    fun `판정 모두 선언한 숫자면 진짜 하나라도 섞이면 거짓`() {
        assertTrue(DoubtGame.isTruthful(listOf(s(7)), 7))
        assertTrue(DoubtGame.isTruthful(listOf(s(7), h(7), d(7), c(7)), 7))
        assertFalse("섞임", DoubtGame.isTruthful(listOf(s(7), h(7), d(8)), 7))
        assertFalse("전부 다름", DoubtGame.isTruthful(listOf(s(2), h(3)), 7))
        assertFalse(DoubtGame.isTruthful(emptyList(), 7))
    }

    @Test
    fun `거짓말이 들키면 낸 사람이 더미 전체를 가져간다`() {
        val g = DoubtGame.withHands(listOf(listOf(s(1), s(2), s(3), h(9)), listOf(h(2), h(3), h(4), h(5)), listOf(d(6), d(7), d(8))))
        // 0번: A 진짜 1장, 통과
        g.play(listOf(s(1)))
        g.pass()
        // 1번: "2 2장" 이라며 ♥2 + ♥5 (섞음) → 2번이 의심
        val pl = g.play(listOf(h(2), h(5)))
        assertEquals(2, pl.rank)
        assertFalse(pl.truthful)
        assertEquals(Phase.DOUBT, g.phase)
        assertEquals(listOf(0, 2), g.doubters())
        val r = g.doubt(2)
        assertTrue(r.caught)
        assertEquals("거짓말한 사람이 가져감", 1, r.taker)
        assertEquals(listOf(s(1), h(2), h(5)), r.taken)
        assertTrue(g.pile.isEmpty())
        assertEquals(2 + 3, g.count(1))
        assertTrue(g.hands[1].containsAll(listOf(s(1), h(2), h(5), h(3), h(4))))
        assertEquals(3, g.count(2))
        assertEquals("다음 사람", 2, g.current)
    }

    @Test
    fun `진짜였으면 의심한 사람이 더미 전체를 가져간다`() {
        val g = DoubtGame.withHands(listOf(listOf(s(1), h(1), d(4)), listOf(h(2), h(3)), listOf(d(6), d(7))))
        val pl = g.play(listOf(s(1), h(1)))
        assertTrue(pl.truthful)
        val r = g.doubt(1)
        assertFalse(r.caught)
        assertEquals("의심한 사람이 가져감", 1, r.taker)
        assertEquals(4, g.count(1))
        assertEquals(1, g.count(0))
        assertEquals(1, g.current)
    }

    @Test
    fun `낸 사람과 끝난 사람은 의심할 수 없다`() {
        val g = DoubtGame.withHands(listOf(listOf(s(1)), listOf(h(2), h(3)), listOf(d(6), d(7))))
        g.play(listOf(s(1)))
        g.pass() // 0번 끝
        assertTrue(g.isFinished(0))
        g.play(listOf(h(2)))
        assertFalse(g.canDoubt(1))
        assertFalse("끝난 사람", g.canDoubt(0))
        assertTrue(g.canDoubt(2))
        assertThrows<IllegalArgumentException> { g.doubt(1) }
        assertThrows<IllegalArgumentException> { g.doubt(0) }
    }

    @Test
    fun `잘못 내기 막기`() {
        val g = DoubtGame.withHands(listOf(listOf(s(1), s(2), s(3), s(4), s(5)), listOf(h(2), h(3))))
        assertThrows<IllegalArgumentException> { g.play(emptyList()) }
        assertThrows<IllegalArgumentException> { g.play(listOf(s(1), s(2), s(3), s(4), s(5))) }
        assertThrows<IllegalArgumentException> { g.play(listOf(h(2))) }
        assertThrows<IllegalArgumentException> { g.play(listOf(s(1), s(1))) }
        assertThrows<IllegalStateException> { g.pass() }
        g.play(listOf(s(1), s(2), s(3), s(4)))
        assertThrows<IllegalStateException> { g.play(listOf(s(5))) }
        assertEquals(4, g.pile.size)
    }

    // ── 마지막 카드 규칙 ──

    @Test
    fun `마지막 카드를 내고 아무도 의심하지 않으면 끝이 확정된다`() {
        val g = DoubtGame.withHands(listOf(listOf(s(9)), listOf(h(2), h(3)), listOf(d(6), d(7))))
        val pl = g.play(listOf(s(9))) // A 라고 거짓말
        assertTrue(pl.emptiedHand)
        assertFalse("의심 창 동안은 아직 확정 아님", g.isFinished(0))
        val r = g.pass()
        assertEquals(0, r.finished)
        assertEquals(1, r.finishedRank)
        assertTrue(g.isFinished(0))
        assertEquals(1, g.current)
        assertFalse(g.isOver)
    }

    @Test
    fun `마지막 카드가 거짓말로 들키면 무효 더미를 가져가고 계속한다`() {
        val g = DoubtGame.withHands(listOf(listOf(s(4), s(9)), listOf(h(2), h(3)), listOf(d(6), d(7))))
        g.play(listOf(s(4)))
        g.pass()
        g.play(listOf(h(2))) // 2 진짜
        g.pass()
        g.play(listOf(d(6))) // 3 이라며 거짓말, 통과
        g.pass()
        // 0번: 마지막 카드 ♠9 를 "4" 라고
        val pl = g.play(listOf(s(9)))
        assertTrue(pl.emptiedHand)
        val r = g.doubt(2)
        assertTrue(r.caught)
        assertEquals(-1, r.finished)
        assertFalse(g.isFinished(0))
        assertEquals("더미 전체(4장)", 4, g.count(0))
        assertEquals(1, g.current)
        assertTrue(g.finishOrder.isEmpty())
    }

    @Test
    fun `마지막 카드가 진짜면 의심받아도 끝 의심한 사람이 더미를 가져간다`() {
        val g = DoubtGame.withHands(listOf(listOf(s(1), h(1)), listOf(h(2), h(3)), listOf(d(6), d(7))))
        g.play(listOf(s(1), h(1)))
        val r = g.doubt(2)
        assertFalse(r.caught)
        assertEquals(2, r.taker)
        assertEquals(0, r.finished)
        assertTrue(g.isFinished(0))
        assertEquals(4, g.count(2))
        assertEquals(1, g.current)
    }

    // ── 순위 ──

    @Test
    fun `다 비운 순서대로 순위 마지막 남은 사람이 꼴찌`() {
        val g = DoubtGame.withHands(listOf(listOf(s(1), s(4)), listOf(h(2)), listOf(d(3), d(5)), listOf(c(6), c(7), c(8))))
        g.play(listOf(s(1))); g.pass()         // A
        g.play(listOf(h(2))); g.pass()         // 2 — 1번 1등
        assertEquals(1, g.ranks[1])
        g.play(listOf(d(3))); g.pass()         // 3
        g.play(listOf(c(6))); g.pass()         // 4 (거짓)
        assertEquals(0, g.current)
        g.play(listOf(s(4))); g.pass()         // 5 (거짓) — 0번 2등
        assertEquals(2, g.ranks[0])
        assertEquals("끝난 1번은 건너뜀", 2, g.current)
        g.play(listOf(d(5))); val r = g.pass() // 6 (거짓) — 2번 3등, 3번 꼴찌
        assertTrue(r.gameOver)
        assertTrue(g.isOver)
        assertArrayEquals(intArrayOf(2, 1, 3, 4), g.finalRanks())
        assertEquals(listOf(1, 0, 2, 3), g.finishOrder)
        assertThrows<IllegalStateException> { g.play(listOf(c(7))) }
    }

    @Test
    fun `2인은 한 명이 끝나면 바로 끝난다`() {
        val g = DoubtGame.withHands(listOf(listOf(s(1)), listOf(h(2), h(3))))
        g.play(listOf(s(1)))
        val r = g.doubt(1)
        assertEquals(0, r.finished)
        assertTrue(r.gameOver)
        assertArrayEquals(intArrayOf(1, 2), g.finalRanks())
    }

    @Test
    fun `차례 제한 정해진 차례를 넘기면 남은 사람은 손패가 적은 순서로 순위`() {
        val g = DoubtGame.withHands(
            listOf(listOf(s(1)), listOf(h(2), h(3), h(4)), listOf(d(3), d(5)), listOf(c(6), c(7), c(8), c(9))),
            maxTurns = 3,
        )
        g.play(listOf(s(1))); g.pass()         // 0번 1등
        g.play(listOf(h(2))); g.pass()         // 1번 2장 남음
        assertFalse(g.isOver)
        g.play(listOf(d(3))); val r = g.pass() // 3차례 → 차례 제한
        assertTrue(r.gameOver)
        assertTrue(g.stalled)
        // 남은 장수: 1번 2장, 2번 1장, 3번 4장
        assertArrayEquals(intArrayOf(1, 3, 2, 4), g.finalRanks())
        assertEquals(listOf(0, 2, 1, 3), g.finishOrder)
    }

    @Test
    fun `차례 제한 같은 장수는 같은 순위`() {
        val g = DoubtGame.withHands(listOf(listOf(s(1), s(2)), listOf(h(2), h(3)), listOf(d(3), d(5), d(6))), maxTurns = 2)
        g.play(listOf(s(1))); g.pass()
        g.play(listOf(h(2))); g.pass()
        assertTrue(g.stalled)
        assertArrayEquals(intArrayOf(1, 1, 3), g.finalRanks())
    }

    // ── 무작위 판 ──

    /**
     * 무작위 전략 (사람 흉내): 선언 숫자 카드가 있으면 그 카드를 모두(가끔 1장 섞어서) 내고, 없으면 거짓말.
     * 거짓말 카드는 내 다음 몇 차례에 올 숫자가 아닌 것부터 (마지막에 진짜로 끝내려고 아껴 둠).
     * 의심: 내 손에 그 숫자가 있어 확실히 거짓이면 늘, 마지막 카드면 반반, 그 밖엔 가끔.
     */
    private fun simulate(n: Int, seed: Int, maxTurns: Int = GameConfig.MAX_TURNS): DoubtGame {
        val rnd = Random(seed)
        val g = DoubtGame.withHands(DoubtGame(n, Random(seed * 31 + 7)).hands.map { it.toList() }, maxTurns = maxTurns)
        var steps = 0
        while (!g.isOver) {
            check(steps++ < 10000) { "끝나지 않음 seed=$seed" }
            val p = g.current
            val hand = g.hands[p]
            assertTrue(hand.isNotEmpty())
            val rank = g.requiredRank
            val truth = hand.filter { it.rank == rank }.take(4)
            // 내 다음 차례들에 올 숫자 (가까운 것일수록 아낌)
            val active = g.playingCount
            fun keep(c: Card): Int {
                for (k in 1..4) if ((rank - 1 + k * active) % 13 + 1 == c.rank) return 5 - k
                return 0
            }
            val others = hand.filter { it.rank != rank }.shuffled(rnd).sortedBy { keep(it) }
            val cards = when {
                truth.isNotEmpty() && truth.size < 4 && others.size > 2 && rnd.nextInt(4) == 0 -> truth + others[0]
                truth.isNotEmpty() -> truth
                else -> others.take(if (others.size > 3 && rnd.nextInt(3) == 0) 2 else 1)
            }
            val total = g.hands.sumOf { it.size } + g.pile.size
            g.play(cards)
            assertEquals("카드 보존", total, g.hands.sumOf { it.size } + g.pile.size)
            val ds = g.doubters()
            assertTrue("의심할 사람은 늘 있다", ds.isNotEmpty())
            val pl = g.lastPlay!!
            var who = -1
            for (q in ds.shuffled(rnd)) {
                val held = g.hands[q].count { it.rank == rank }
                val sure = held + pl.count > 4
                if (sure || (pl.emptiedHand && rnd.nextInt(2) == 0) || rnd.nextInt(10) == 0) { who = q; break }
            }
            if (who >= 0) g.doubt(who) else g.pass()
            assertEquals("카드 보존", 52, g.hands.sumOf { it.size } + g.pile.size)
        }
        return g
    }

    @Test
    fun `무작위 판은 모두 끝나고 순위가 올바르다`() {
        var stalls = 0
        var turns = 0
        for (n in 2..4) for (seed in 1..150) {
            val g = simulate(n, seed)
            assertTrue(g.isOver)
            assertTrue(g.turnCount <= GameConfig.MAX_TURNS)
            val ranks = g.finalRanks()
            assertTrue(ranks.all { it in 1..n })
            assertEquals(n, g.finishOrder.size)
            assertEquals(n, g.finishOrder.toSet().size)
            assertEquals(1, ranks[g.finishOrder[0]])
            // 손패를 비운 사람은 남은 사람보다 앞
            val emptied = (0 until n).filter { g.count(it) == 0 }
            val holding = (0 until n).filter { g.count(it) > 0 }
            for (a in emptied) for (b in holding) assertTrue(ranks[a] < ranks[b])
            if (!g.stalled) {
                assertEquals((1..n).toList(), ranks.sorted())
                assertEquals("꼴찌는 한 명", 1, holding.size)
            } else {
                stalls++
                assertEquals(GameConfig.MAX_TURNS, g.turnCount)
                // 남은 사람은 장수가 적을수록 앞, 같으면 같은 순위
                for (a in holding) for (b in holding) {
                    if (g.count(a) < g.count(b)) assertTrue(ranks[a] < ranks[b])
                    if (g.count(a) == g.count(b)) assertEquals(ranks[a], ranks[b])
                }
            }
            assertNull(g.lastPlay)
            assertTrue(g.pile.isEmpty() || g.turnCount > 0)
            turns += g.turnCount
        }
        println("무작위 450판: 평균 ${turns / 450} 차례, 차례 제한으로 끝 $stalls 판")
    }
}
