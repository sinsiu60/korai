package com.siwoo.party.games.speed

import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Suit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpeedGameTest {

    private fun s(r: Int) = Card.of(Suit.SPADE, r)
    private fun h(r: Int) = Card.of(Suit.HEART, r)
    private fun d(r: Int) = Card.of(Suit.DIAMOND, r)
    private fun c(r: Int) = Card.of(Suit.CLUB, r)

    // ── 나누기 ──

    @Test
    fun `52장을 26장씩 칸 4장 더미 21장 가운데 1장씩`() {
        val g = SpeedGame.deal(Random(1))
        for (p in 0..1) {
            assertEquals(4, g.slotCount(p))
            assertEquals(GameConfig.START_DRAW_PILE, g.drawSize(p))
            assertEquals(21, g.drawSize(p))
            assertEquals(25, g.cardsLeft(p))
        }
        assertEquals(1, g.centerSize(0))
        assertEquals(1, g.centerSize(1))
        assertEquals(52, g.totalCards())
        // 모든 카드가 한 번씩 (조커 없음)
        val all = HashSet<Card>()
        for (p in 0..1) for (i in 0 until 4) all.add(g.slot(p, i)!!)
        for (k in 0..1) all.add(g.centerTop(k)!!)
        assertEquals(10, all.size)
        assertTrue(all.none { it.isJoker })
        assertFalse(g.isOver)
        assertEquals(-1, g.winner)
    }

    @Test
    fun `섞기는 난수에 따라 달라지고 같은 씨앗이면 같다`() {
        val a = SpeedGame.deal(Random(7))
        val b = SpeedGame.deal(Random(7))
        val c = SpeedGame.deal(Random(8))
        val sa = (0 until 4).map { a.slot(0, it) } + a.centerTop(0)
        assertEquals(sa, (0 until 4).map { b.slot(0, it) } + b.centerTop(0))
        assertFalse(sa == (0 until 4).map { c.slot(0, it) } + c.centerTop(0))
    }

    // ── 낼 수 있는 카드 ──

    @Test
    fun `1 차이만 이어지고 K와 A도 이어진다`() {
        assertTrue(SpeedGame.adjacent(5, 6))
        assertTrue(SpeedGame.adjacent(6, 5))
        assertTrue(SpeedGame.adjacent(13, 1))
        assertTrue(SpeedGame.adjacent(1, 13))
        assertTrue(SpeedGame.adjacent(12, 13))
        assertTrue(SpeedGame.adjacent(1, 2))
        assertFalse(SpeedGame.adjacent(5, 5))
        assertFalse(SpeedGame.adjacent(5, 7))
        assertFalse(SpeedGame.adjacent(13, 2))
        assertFalse(SpeedGame.adjacent(12, 1))
    }

    @Test
    fun `가운데 두 더미 모두에 낼 수 있고 무늬는 상관없다`() {
        val g = SpeedGame.custom(
            listOf(listOf(h(6), c(13), s(9), null), listOf(d(2), d(2), d(2), d(2))),
            listOf(emptyList(), emptyList()),
            listOf(listOf(s(5)), listOf(d(1))),
        )
        assertTrue("6 → 5 위", g.canPlay(0, 0, 0))
        assertFalse("6 → A 위 안 됨", g.canPlay(0, 0, 1))
        assertTrue("K → A 위 (이어짐)", g.canPlay(0, 1, 1))
        assertFalse("K → 5 안 됨", g.canPlay(0, 1, 0))
        assertFalse("9 는 둘 다 안 됨", g.canPlay(0, 2, 0) || g.canPlay(0, 2, 1))
        assertFalse("빈 칸", g.canPlay(0, 3, 0))
        assertTrue("2 → A 위", g.canPlay(1, 0, 1))
        assertArrayEquals(intArrayOf(0, 0), g.firstMove(0))
    }

    @Test
    fun `안 맞는 카드나 빈 칸은 내지 못하고 그대로`() {
        val g = SpeedGame.custom(
            listOf(listOf(h(9), null, null, null), listOf(d(2), null, null, null)),
            listOf(listOf(s(3)), emptyList()),
            listOf(listOf(s(5)), listOf(d(1))),
        )
        assertSame(PlayResult.NOT_ADJACENT, g.play(0, 0, 0))
        assertSame(PlayResult.EMPTY_SLOT, g.play(0, 1, 0))
        assertEquals(h(9), g.slot(0, 0))
        assertEquals(s(5), g.centerTop(0))
        assertEquals(1, g.drawSize(0))
    }

    @Test
    fun `같은 더미에 거의 동시에 내면 먼저 낸 쪽만 놓인다`() {
        // 가운데 5: 둘 다 6 을 들고 있음 → 먼저 처리된 0번이 놓이고, 1번의 6 은 이제 6 위라 안 맞음
        val g = SpeedGame.custom(
            listOf(listOf(h(6), null, null, null), listOf(c(6), s(10), null, null)),
            listOf(listOf(h(10)), listOf(c(10))),
            listOf(listOf(s(5)), listOf(d(1))),
        )
        assertSame(PlayResult.PLAYED, g.play(0, 0, 0))
        assertSame(PlayResult.NOT_ADJACENT, g.play(1, 0, 0))
        assertEquals(h(6), g.centerTop(0))
        assertEquals(c(6), g.slot(1, 0))
    }

    // ── 보충 ──

    @Test
    fun `낸 칸은 자기 더미 맨 위 카드로 채운다`() {
        val g = SpeedGame.custom(
            listOf(listOf(h(6), c(12), null, null), listOf(d(2), null, null, null)),
            listOf(listOf(s(3), s(4)), listOf(c(9))),
            listOf(listOf(s(5)), listOf(d(1))),
        )
        assertSame(PlayResult.PLAYED, g.play(0, 0, 0))
        assertTrue(g.lastRefilled)
        assertEquals("맨 위 ♠3 이 칸 0 으로", s(3), g.slot(0, 0))
        assertEquals(1, g.drawSize(0))
        assertEquals(1, g.drawSize(1))
        assertEquals("상대 더미는 그대로", d(2), g.slot(1, 0))
        assertEquals(h(6), g.centerTop(0))
        assertEquals(2, g.centerSize(0))
    }

    @Test
    fun `더미가 비면 빈 칸으로 남는다`() {
        val g = SpeedGame.custom(
            listOf(listOf(h(6), c(4), null, null), listOf(d(9), null, null, null)),
            listOf(emptyList(), listOf(c(9))),
            listOf(listOf(s(5)), listOf(d(1))),
        )
        assertSame(PlayResult.PLAYED, g.play(0, 0, 0))
        assertFalse(g.lastRefilled)
        assertNull(g.slot(0, 0))
        assertEquals(1, g.cardsLeft(0))
    }

    // ── 막힘 · [다시] ──

    @Test
    fun `둘 다 낼 카드가 없으면 막힘`() {
        val g = SpeedGame.custom(
            listOf(listOf(h(9), c(9), s(9), d(9)), listOf(d(3), c(3), null, null)),
            listOf(listOf(s(7)), listOf(c(10))),
            listOf(listOf(s(5)), listOf(d(12))),
        )
        assertFalse(g.hasMove(0))
        assertFalse(g.hasMove(1))
        assertTrue(g.stuck)
        assertNull(g.firstMove(0))
    }

    @Test
    fun `한 사람만 막히면 막힘 아님`() {
        val g = SpeedGame.custom(
            listOf(listOf(h(9), c(9), s(9), d(9)), listOf(d(4), c(3), null, null)),
            listOf(listOf(s(7)), listOf(c(10))),
            listOf(listOf(s(5)), listOf(d(12))),
        )
        assertFalse(g.hasMove(0))
        assertTrue("1번은 4 → 5", g.hasMove(1))
        assertFalse(g.stuck)
        assertSame("막히지 않았으면 [다시] 무시", AgainResult.NOT_STUCK, g.pressAgain(0))
        assertFalse(g.isReady(0))
    }

    @Test
    fun `다시는 두 사람 모두 눌러야 각자 더미에서 한 장씩 뒤집는다`() {
        val g = SpeedGame.custom(
            listOf(listOf(h(9), c(9), s(9), d(9)), listOf(d(3), c(3), null, null)),
            listOf(listOf(s(7), s(8)), listOf(c(10), c(11))),
            listOf(listOf(s(5)), listOf(d(12))),
        )
        assertSame(AgainResult.WAITING, g.pressAgain(0))
        assertTrue(g.isReady(0))
        assertSame(AgainResult.ALREADY, g.pressAgain(0))
        assertEquals("한 사람만 눌러선 그대로", s(5), g.centerTop(0))
        assertEquals(d(12), g.centerTop(1))
        assertSame(AgainResult.FLIPPED, g.pressAgain(1))
        assertEquals("0번 더미 맨 위 → 가운데 0", s(7), g.centerTop(0))
        assertEquals("1번 더미 맨 위 → 가운데 1", c(10), g.centerTop(1))
        assertEquals(1, g.drawSize(0))
        assertEquals(1, g.drawSize(1))
        assertSame(FlipSource.OWN, g.lastFlip[0])
        assertFalse(g.isReady(0) || g.isReady(1))
        assertEquals(1, g.flips)
        // 이제 0번의 9 를 가운데 1(♣10) 위에 낼 수 있음
        assertTrue(g.hasMove(0))
        assertFalse(g.stuck)
    }

    @Test
    fun `자기 더미가 비면 가운데 밑바닥 카드를 다시 뒤집는다`() {
        val g = SpeedGame.custom(
            listOf(listOf(h(9), null, null, null), listOf(d(3), null, null, null)),
            listOf(emptyList(), listOf(c(11))),
            listOf(listOf(c(2), h(1), s(5)), listOf(d(12))),
        )
        assertTrue(g.stuck)
        g.pressAgain(1)
        assertSame(AgainResult.FLIPPED, g.pressAgain(0))
        assertSame(FlipSource.RESERVE, g.lastFlip[0])
        assertSame(FlipSource.OWN, g.lastFlip[1])
        assertEquals("밑바닥 ♣2 가 가운데 0 맨 위로", c(2), g.centerTop(0))
        assertEquals(3, g.centerSize(0))
        assertEquals(c(11), g.centerTop(1))
        assertEquals(1, g.cardsLeft(0))
    }

    // ── 승리 ──

    @Test
    fun `칸과 더미를 모두 먼저 비운 사람이 1위`() {
        val g = SpeedGame.custom(
            listOf(listOf(null, h(6), null, null), listOf(d(9), null, null, null)),
            listOf(emptyList(), listOf(c(9))),
            listOf(listOf(s(5)), listOf(d(1))),
        )
        assertEquals(1, g.cardsLeft(0))
        assertSame(PlayResult.WON, g.play(0, 1, 0))
        assertTrue(g.isOver)
        assertEquals(0, g.winner)
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
        assertFalse("끝나면 막힘 아님", g.stuck)
        assertSame(PlayResult.OVER, g.play(1, 0, 0))
    }

    @Test
    fun `칸이 비어도 더미가 남으면 이기지 않는다`() {
        val g = SpeedGame.custom(
            listOf(listOf(h(6), null, null, null), listOf(d(9), null, null, null)),
            listOf(listOf(s(2)), emptyList()),
            listOf(listOf(s(5)), listOf(d(1))),
        )
        assertSame(PlayResult.PLAYED, g.play(0, 0, 0))
        assertEquals(s(2), g.slot(0, 0))
        assertFalse(g.isOver)
    }

    @Test
    fun `1번 플레이어가 이기면 순위는 2 1`() {
        val g = SpeedGame.custom(
            listOf(listOf(h(9), null, null, null), listOf(null, null, d(2), null)),
            listOf(emptyList(), emptyList()),
            listOf(listOf(s(5)), listOf(d(1))),
        )
        assertSame(PlayResult.WON, g.play(1, 2, 1))
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test
    fun `무작위로 끝까지 두면 항상 한 사람이 이기고 카드 52장 유지`() {
        for (seed in 0 until 60) {
            val r = Random(seed)
            val g = SpeedGame.deal(r)
            var steps = 0
            while (!g.isOver && steps < 5000) {
                steps++
                assertEquals(52, g.totalCards())
                if (g.stuck) {
                    g.pressAgain(0); assertSame(AgainResult.FLIPPED, g.pressAgain(1))
                    continue
                }
                val p = if (r.nextBoolean()) 0 else 1
                val m = g.firstMove(p) ?: g.firstMove(1 - p)!!
                val who = if (g.firstMove(p) != null) p else 1 - p
                val res = g.play(who, m[0], m[1])
                assertTrue(res == PlayResult.PLAYED || res == PlayResult.WON)
            }
            assertTrue("seed $seed 끝남", g.isOver)
            assertEquals(0, g.cardsLeft(g.winner))
            assertTrue(g.cardsLeft(1 - g.winner) > 0)
            assertEquals(52, g.totalCards())
        }
    }
}
