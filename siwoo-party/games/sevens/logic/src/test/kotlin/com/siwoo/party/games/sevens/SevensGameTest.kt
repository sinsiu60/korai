package com.siwoo.party.games.sevens

import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Suit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SevensGameTest {

    private fun s(r: Int) = Card.of(Suit.SPADE, r)
    private fun h(r: Int) = Card.of(Suit.HEART, r)
    private fun d(r: Int) = Card.of(Suit.DIAMOND, r)
    private fun c(r: Int) = Card.of(Suit.CLUB, r)

    @Test fun `2 3 4명 나눠 주기 장수와 52장 전부`() {
        val expect = mapOf(2 to listOf(26, 26), 3 to listOf(18, 17, 17), 4 to listOf(13, 13, 13, 13))
        for ((n, sizes) in expect) {
            for (p in 0 until n) assertEquals(sizes[p], SevensGame.dealSize(n, p))
            repeat(20) { seed ->
                val g = SevensGame.create(n, Random(seed))
                // ♠7 가진 사람은 이미 1장 냈다
                val counts = (0 until n).map { g.hands[it].size + if (it == g.openingPlayer) 1 else 0 }
                assertEquals(sizes, counts)
                val all = g.hands.flatten() + s(7)
                assertEquals(52, all.size)
                assertEquals(52, all.toSet().size)
                assertTrue(all.none { it.isJoker })
                for (p in 0 until n) assertEquals(GameConfig.PASS_TOKENS, g.tokens(p))
            }
        }
    }

    @Test fun `스페이드 7 가진 사람이 자동으로 내고 다음 사람부터`() {
        val g = SevensGame.custom(listOf(listOf(h(2), h(3)), listOf(s(7), c(1)), listOf(d(4), d(5))))
        assertEquals(1, g.openingPlayer)
        assertTrue(g.isOnBoard(s(7)))
        assertEquals(listOf(c(1)), g.hands[1])
        assertEquals(1, g.boardCount)
        assertEquals("♠7 낸 사람 다음", 2, g.current)
        // 손패는 정렬
        val r = SevensGame.create(4, Random(3))
        for (p in 0 until 4) assertEquals(r.hands[p].map { it.id }.sorted(), r.hands[p].map { it.id })
        assertEquals((r.openingPlayer + 1) % 4, r.current)
    }

    @Test fun `놓을 수 있는 카드 - 7 이 줄을 열고 끝에 이어지는 카드만`() {
        val g = SevensGame.custom(
            listOf(listOf(s(6), s(8), s(5), s(9), h(7), h(6), d(8), c(13)), listOf(d(1), d(2))),
            openWithSeven = false,
        )
        // 아무 줄도 안 열림: 7 만 가능
        assertFalse(g.isOpen(Suit.SPADE))
        assertFalse(g.isPlaceable(s(6)))
        assertFalse(g.isPlaceable(s(8)))
        assertTrue(g.isPlaceable(s(7)))
        assertTrue(g.canPlay(h(7)))
        assertFalse("손에 없는 카드는 못 냄", g.canPlay(s(7)))
        assertEquals(listOf(h(7)), g.playableIndices().map { g.hands[0][it] })

        // ♠ 줄 5..8 , ♦ 줄 7 만 열린 판
        val b = SevensGame.custom(
            listOf(listOf(s(4), s(3), s(9), s(10), d(6), d(8), d(9), h(6), h(7), c(13)), listOf(c(1), c(2))),
            board = listOf(s(7), s(6), s(5), s(8), d(7)),
        )
        assertEquals(0, b.current)
        assertEquals(5, b.low(Suit.SPADE))
        assertEquals(8, b.high(Suit.SPADE))
        assertTrue("아래로 이어짐", b.canPlay(s(4)))
        assertFalse("한 칸 건너뜀", b.canPlay(s(3)))
        assertTrue("위로 이어짐", b.canPlay(s(9)))
        assertFalse(b.canPlay(s(10)))
        assertTrue("여러 줄", b.canPlay(d(6)))
        assertTrue(b.canPlay(d(8)))
        assertFalse(b.canPlay(d(9)))
        assertFalse("닫힌 줄", b.canPlay(h(6)))
        assertTrue("7 은 언제나", b.canPlay(h(7)))
        assertFalse(b.canPlay(c(13)))
        assertEquals(listOf(s(4), s(9), h(7), d(6), d(8)).toSet(), b.playableIndices().map { b.hands[0][it] }.toSet())
        // 내면 끝이 늘어난다
        b.play(s(4))
        assertEquals(4, b.low(Suit.SPADE))
        assertTrue(b.isOnBoard(s(4)))
        assertTrue(b.isPlaceable(s(3)))
        assertFalse(b.isPlaceable(s(4)))
        assertEquals(1, b.current)
        // 2번 사람은 낼 카드 없음
        assertTrue(b.playableIndices().isEmpty())
        assertFalse(b.hasPlayable())
    }

    @Test fun `패스 토큰은 낼 카드가 있어도 쓸 수 있고 3개까지`() {
        val g = SevensGame.custom(listOf(listOf(s(6), s(1)), listOf(c(1), c(2))), board = listOf(s(7)))
        assertEquals(0, g.current)
        assertTrue(g.hasPlayable())
        assertTrue(g.canPass)
        assertEquals(2, g.pass())
        assertEquals(1, g.current)
        assertEquals(2, g.pass())
        assertEquals(1, g.pass())
        assertEquals(1, g.pass())
        assertEquals(0, g.pass())
        assertEquals(0, g.pass())
        assertEquals(0, g.tokens(0))
        assertEquals(0, g.tokens(1))
        assertEquals(0, g.current)
        assertFalse(g.canPass)
        try {
            g.pass()
            error("토큰 없이 패스됨")
        } catch (e: IllegalStateException) {
            // 정상
        }
        // 토큰이 없어도 낼 카드가 있으면 탈락 아님
        assertFalse(g.mustEliminate)
        g.play(s(6))
        assertEquals(1, g.current)
        assertTrue("1번은 낼 카드도 토큰도 없음", g.mustEliminate)
    }

    @Test fun `탈락 - 남은 카드는 공개되고 자리가 나면 자동으로 놓인다`() {
        // 0: 탈락할 사람 (♥8, ♥9, ♥5, ♣2) / 1: ♥6 / 2: ♥4
        val g = SevensGame.custom(
            listOf(listOf(h(8), h(9), h(5), c(2)), listOf(h(6), d(1), d(2)), listOf(h(4), d(3), d(4))),
            board = listOf(s(7), h(7)),
            tokens = intArrayOf(0, 3, 3),
        )
        // 0 은 ♥8 을 낼 수 있어서 아직 탈락 아님 → 내고 나서 다시 차례가 오면
        assertFalse(g.mustEliminate)
        g.play(h(8))
        g.pass() // 1
        g.pass() // 2
        assertEquals(0, g.current)
        assertTrue(g.canPlay(h(9)))
        g.play(h(9))
        g.pass(); g.pass()
        assertEquals(0, g.current)
        assertTrue("♥5·♣2 는 못 내고 토큰 없음", g.mustEliminate)
        val e = g.eliminate()
        assertEquals(0, e.player)
        assertTrue(g.isOut(0))
        assertEquals(listOf(0), g.outOrder)
        assertTrue(g.hands[0].isEmpty())
        assertTrue("바로 놓을 자리 없음", e.placed.isEmpty())
        assertEquals(listOf(h(5), c(2)).toSet(), e.pooled.toSet())
        assertEquals(listOf(h(5), c(2)).toSet(), g.pool.toSet())
        assertEquals(0, g.poolOwner(h(5)))
        assertEquals(-1, e.lastStanding)
        assertEquals(1, g.current)
        // 1 이 ♥6 → 공개 카드 ♥5 가 자동으로 놓이고, 2 의 ♥4 가 이어진다
        val r = g.play(h(6))
        assertEquals(listOf(h(5)), r.auto)
        assertTrue(g.isOnBoard(h(5)))
        assertEquals(listOf(c(2)), g.pool)
        assertEquals(-1, g.poolOwner(h(5)))
        assertTrue(g.canPlay(h(4)))
    }

    @Test fun `공개 카드는 이어지는 대로 줄줄이 놓인다`() {
        // 0: ♥5 ♥4 ♥9 ♣1, ♥ 줄 7..8
        val g2 = SevensGame.custom(
            listOf(listOf(h(5), h(4), h(9), c(1)), listOf(h(6), d(1)), listOf(d(3), d(4))),
            board = listOf(s(7), h(7), h(8)), openWithSeven = false, tokens = intArrayOf(0, 3, 3),
        )
        assertTrue(g2.canPlay(h(9)))
        g2.play(h(9))
        g2.pass(); g2.pass()
        // 0: ♥5 ♥4 ♣1, ♥ 줄 7..9 → 낼 것 없음
        assertTrue(g2.mustEliminate)
        val e = g2.eliminate()
        assertTrue(e.placed.isEmpty())
        // 1 이 ♥6 → ♥5, ♥4 가 차례로 자동
        val r = g2.play(h(6))
        assertEquals(listOf(h(5), h(4)), r.auto)
        assertEquals(4, g2.low(Suit.HEART))
        assertEquals(listOf(c(1)), g2.pool)

        // 닫힌 줄 카드만 가진 사람이 탈락 → 모두 공개 카드로 남고, 혼자 남은 사람이 순위를 받으며 끝
        val g3 = SevensGame.custom(
            listOf(listOf(c(8), c(9), c(6), d(13)), listOf(d(1), d(2))),
            board = listOf(s(7), d(7), d(8), d(9), d(10), d(11)), openWithSeven = false, tokens = intArrayOf(0, 3),
        )
        // 0 은 ♣7 이 없어 ♣ 를 못 내고 ♦13 도 못 냄 (♦ 줄 끝 11) → 탈락, ♣7 은 아무 손에도 없으니 그대로 공개
        assertTrue(g3.mustEliminate)
        val e3 = g3.eliminate()
        assertTrue(e3.placed.isEmpty())
        assertEquals(4, e3.pooled.size)
        assertEquals("혼자 남은 1번이 1등으로 끝", 1, e3.lastStanding)
        assertTrue(g3.isOver)
        assertArrayEquals(intArrayOf(2, 1), g3.finalRanks())
    }

    @Test fun `닫힌 줄의 공개 카드는 줄이 열리고 이어지면 줄줄이 놓인다`() {
        // 0 이 ♣9 ♣10 을 공개한 채 탈락 → 1 이 ♣7, ♣8 을 내면 공개 카드가 줄줄이 이어진다
        val g = SevensGame.custom(
            listOf(listOf(c(9), c(10), h(1)), listOf(c(7), c(8), d(1)), listOf(h(13), d(13))),
            board = listOf(s(7)), openWithSeven = false, tokens = intArrayOf(0, 3, 3),
        )
        assertTrue(g.mustEliminate)
        val e = g.eliminate() // 0 탈락: ♣9 ♣10 ♥A 공개
        assertTrue(e.placed.isEmpty())
        assertEquals(1, g.current)
        g.play(c(7))
        g.pass() // 2
        val r = g.play(c(8)) // 1: ♣8 → 공개된 ♣9, ♣10 이 줄줄이
        assertEquals(listOf(c(9), c(10)), r.auto)
        assertEquals(10, g.high(Suit.CLUB))
        assertEquals(listOf(h(1)), g.pool)
    }

    @Test fun `혼자 남으면 다음 순위로 끝 - 다 낸 순서 다음 늦게 탈락한 사람`() {
        // 4명: 1 이 먼저 다 냄(1등), 3 탈락, 0 탈락, 2 혼자 남음(2등) → 늦게 탈락한 0 이 3등, 먼저 탈락한 3 이 4등
        val g = SevensGame.custom(
            listOf(listOf(c(1), c(2)), listOf(s(8)), listOf(s(9), s(10), d(5)), listOf(c(3), c(4))),
            board = listOf(s(7), h(7), d(7)), openWithSeven = false, first = 1, tokens = intArrayOf(0, 3, 3, 0),
        )
        val r1 = g.play(s(8))
        assertEquals(1, r1.rank)
        assertTrue(g.isFinished(1))
        assertEquals(2, g.current)
        g.play(s(9))
        assertEquals(3, g.current)
        assertTrue(g.mustEliminate)
        g.eliminate()
        assertEquals("1 은 다 냈으니 건너뜀", 0, g.current)
        assertTrue(g.mustEliminate)
        val e = g.eliminate()
        assertEquals(2, e.lastStanding)
        assertTrue(g.isOver)
        assertEquals(listOf(1, 2), g.finishOrder)
        assertEquals(listOf(3, 0), g.outOrder)
        assertArrayEquals(intArrayOf(3, 1, 2, 4), g.finalRanks())
    }

    @Test fun `먼저 다 낸 순서대로 순위`() {
        val g = SevensGame.custom(
            listOf(listOf(s(6)), listOf(s(8), s(9)), listOf(s(5), s(10), s(11))),
            board = listOf(s(7)), openWithSeven = false,
        )
        val a = g.play(s(6))
        assertEquals(1, a.rank)
        assertEquals(1, g.current)
        assertEquals(0, g.play(s(8)).rank)
        assertEquals(0, g.play(s(5)).rank)
        assertEquals("0 은 나가서 건너뜀", 1, g.current)
        val b = g.play(s(9))
        assertEquals(2, b.rank)
        assertEquals("혼자 남은 2 가 3등", 2, b.lastStanding)
        assertTrue(g.isOver)
        assertEquals(listOf(0, 1, 2), g.finishOrder)
        assertArrayEquals(intArrayOf(1, 2, 3), g.finalRanks())
    }

    @Test fun `줄이 막히지 않는다 - 끝나기 전에는 언제나 누군가 낼 카드가 있다`() {
        for (n in 2..4) repeat(150) { seed ->
            val g = SevensGame.create(n, Random(seed))
            val rnd = Random(seed * 31 + n)
            var guard = 0
            while (!g.isOver) {
                check(guard++ < 1000) { "끝나지 않음 n=$n seed=$seed" }
                assertTrue("판 밖 카드가 남았으면 누군가는 낼 수 있음", (0 until n).any { g.hasPlayable(it) })
                // 판 밖 카드는 살아 있는 사람 손 또는 공개 카드에만
                val outside = (0 until n).sumOf { g.hands[it].size } + g.pool.size
                assertEquals(52, outside + g.boardCount)
                for (p in g.outOrder) assertTrue(g.hands[p].isEmpty())
                // 공개 카드는 놓을 수 있는 채로 남아 있지 않다
                assertTrue(g.pool.none { g.isPlaceable(it) })
                stepRandom(g, rnd)
            }
        }
    }

    @Test fun `2 3 4명 무작위 판은 언제나 끝나고 순위가 1부터 n 까지`() {
        for (n in 2..4) {
            var outs = 0
            var finishers = 0
            repeat(400) { seed ->
                val g = SevensGame.create(n, Random(seed + 1000 * n))
                val rnd = Random(seed)
                var turns = 0
                while (!g.isOver) {
                    check(turns++ < 52 + 3 * n + n + 5) { "차례가 너무 많음 n=$n seed=$seed" }
                    stepRandom(g, rnd)
                }
                val ranks = g.finalRanks()
                assertEquals((1..n).toList(), ranks.sorted())
                assertEquals(n, g.finishOrder.size + g.outOrder.size)
                for ((i, p) in g.finishOrder.withIndex()) assertEquals(i + 1, ranks[p])
                // 탈락자는 다 낸 사람보다 아래, 늦게 탈락할수록 높음
                for (k in 1 until g.outOrder.size) assertTrue(ranks[g.outOrder[k]] < ranks[g.outOrder[k - 1]])
                outs += g.outOrder.size
                finishers += g.finishOrder.size
            }
            println("n=$n 400판: 다 냄 $finishers, 탈락 $outs")
            assertTrue("탈락도 나온다", outs > 0)
        }
    }

    /** 무작위로 한 수: 탈락해야 하면 탈락, 아니면 대체로 내고 가끔 패스 */
    private fun stepRandom(g: SevensGame, rnd: Random) {
        if (g.mustEliminate) {
            val e = g.eliminate()
            assertTrue("탈락은 놓을 카드가 없을 때만 → 모두 공개 카드로", e.placed.isEmpty())
            return
        }
        val ok = g.playableIndices()
        if (ok.isEmpty() || (g.canPass && rnd.nextInt(6) == 0)) {
            g.pass()
        } else {
            g.play(g.hands[g.current][ok[rnd.nextInt(ok.size)]])
        }
    }
}
