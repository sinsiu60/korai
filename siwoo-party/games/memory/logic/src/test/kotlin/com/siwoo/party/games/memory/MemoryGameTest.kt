package com.siwoo.party.games.memory

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MemoryGameTest {

    /** 그림 s 가 있는 두 자리 */
    private fun pairOf(g: MemoryGame, s: Symbol): IntArray {
        val idx = g.deck.indices.filter { g.deck[it] == s }
        return intArrayOf(idx[0], idx[1])
    }

    /** 서로 다른 그림 두 장 (아직 안 맞춘 것 중) */
    private fun mismatchOf(g: MemoryGame): IntArray {
        val open = g.deck.indices.filter { !g.isMatched(it) }
        val a = open.first()
        val b = open.first { g.deck[it] != g.deck[a] }
        return intArrayOf(a, b)
    }

    private fun match(g: MemoryGame, s: Symbol): Pick {
        val (a, b) = pairOf(g, s).let { it[0] to it[1] }
        assertTrue(g.pick(a) is Pick.First)
        return g.pick(b)
    }

    private fun miss(g: MemoryGame) {
        val m = mismatchOf(g)
        assertTrue(g.pick(m[0]) is Pick.First)
        assertTrue(g.pick(m[1]) is Pick.Mismatch)
        g.hideMismatch()
    }

    @Test fun `카드는 그림 12종이 정확히 2장씩, 24장`() {
        val g = MemoryGame(2, Random(1))
        assertEquals(GameConfig.CARDS, g.deck.size)
        assertEquals(24, GameConfig.CARDS)
        assertEquals(12, Symbol.values().size)
        val counts = g.deck.groupingBy { it }.eachCount()
        assertEquals(12, counts.size)
        assertTrue(counts.values.all { it == 2 })
        // 이름도 모두 다름
        assertEquals(12, Symbol.values().map { it.label }.toSet().size)
    }

    @Test fun `같은 씨앗이면 같은 배치, 다른 씨앗이면 다른 배치 (섞임)`() {
        assertEquals(MemoryGame(2, Random(7)).deck, MemoryGame(3, Random(7)).deck)
        val a = MemoryGame(2, Random(7)).deck
        val b = MemoryGame(2, Random(8)).deck
        assertNotEquals(a, b)
        // 정렬된 순서 그대로가 아님
        val sorted = Symbol.values().flatMap { listOf(it, it) }
        assertNotEquals(sorted, a)
    }

    @Test fun `같은 그림이면 짝, 쌍 획득 후 같은 사람이 한 번 더`() {
        val g = MemoryGame(3, Random(2))
        val r = match(g, Symbol.HEART)
        assertTrue(r is Pick.Match)
        r as Pick.Match
        assertEquals(0, r.player)
        assertFalse(r.gameOver)
        assertEquals(1, g.pairs[0])
        assertEquals("짝을 맞추면 차례 유지", 0, g.current)
        assertTrue(g.isMatched(r.first) && g.isMatched(r.second))
        assertEquals(0, g.owner[r.first])
        assertTrue(g.faceUp[r.first] && g.faceUp[r.second])
        assertEquals(-1, g.firstPick)
        // 이어서 또 맞춤
        assertTrue(match(g, Symbol.MOON) is Pick.Match)
        assertEquals(2, g.pairs[0])
        assertEquals(0, g.current)
    }

    @Test fun `다른 그림이면 다시 덮이고 다음 사람 차례 (순서대로 돌아감)`() {
        val g = MemoryGame(3, Random(3))
        val m = mismatchOf(g)
        assertTrue(g.pick(m[0]) is Pick.First)
        val r = g.pick(m[1])
        assertTrue(r is Pick.Mismatch)
        r as Pick.Mismatch
        assertEquals(0, r.player)
        assertTrue("보여 주는 동안은 앞면", g.faceUp[m[0]] && g.faceUp[m[1]])
        assertTrue(g.mismatchShowing)
        assertEquals("덮기 전에는 아직 같은 차례", 0, g.current)
        assertEquals(1, g.hideMismatch())
        assertFalse(g.faceUp[m[0]] || g.faceUp[m[1]])
        assertFalse(g.mismatchShowing)
        assertEquals(1, g.current)
        miss(g)
        assertEquals(2, g.current)
        miss(g)
        assertEquals("마지막 다음은 처음 사람", 0, g.current)
        assertEquals(0, g.matchedPairs)
        assertArrayEquals(intArrayOf(0, 0, 0), g.pairs)
    }

    @Test fun `세 번째 카드·같은 카드 다시·맞춘 카드는 무시`() {
        val g = MemoryGame(2, Random(4))
        // 같은 카드 두 번
        val first = 0
        assertTrue(g.pick(first) is Pick.First)
        assertSame(Pick.Ignored, g.pick(first))
        assertEquals(first, g.firstPick)
        // 다른 그림으로 두 장째 → 보여 주는 중 세 번째 카드는 무시
        val other = g.deck.indices.first { g.deck[it] != g.deck[first] }
        assertTrue(g.pick(other) is Pick.Mismatch)
        val third = g.deck.indices.first { it != first && it != other }
        assertSame(Pick.Ignored, g.pick(third))
        assertFalse(g.faceUp[third])
        g.hideMismatch()
        assertEquals(1, g.current)
        // 맞춘 카드는 다시 못 고름
        val p = pairOf(g, g.deck[third])
        assertTrue(match(g, g.deck[third]) is Pick.Match)
        assertSame(Pick.Ignored, g.pick(p[0]))
        assertSame(Pick.Ignored, g.pick(p[1]))
        assertEquals(-1, g.firstPick)
        // 범위 밖
        assertSame(Pick.Ignored, g.pick(-1))
        assertSame(Pick.Ignored, g.pick(GameConfig.CARDS))
        // 무시된 고르기는 차례·점수를 바꾸지 않음
        assertEquals(1, g.current)
        assertArrayEquals(intArrayOf(0, 1), g.pairs)
    }

    @Test fun `12쌍을 모두 찾으면 끝, 이후 고르기는 무시`() {
        val g = MemoryGame(2, Random(5))
        val symbols = Symbol.values()
        for ((k, s) in symbols.withIndex()) {
            assertFalse(g.gameOver)
            val r = match(g, s)
            assertTrue(r is Pick.Match)
            assertEquals("마지막 쌍에서만 gameOver", k == symbols.size - 1, (r as Pick.Match).gameOver)
        }
        assertTrue(g.gameOver)
        assertEquals(GameConfig.PAIRS, g.matchedPairs)
        assertTrue(g.owner.all { it == 0 })
        assertSame(Pick.Ignored, g.pick(0))
        assertArrayEquals(intArrayOf(1, 2), g.finalRanks())
    }

    @Test fun `순위는 쌍 수 많은 순, 같으면 공동 순위`() {
        val g = MemoryGame(4, Random(6))
        val s = Symbol.values()
        var k = 0
        // 0번 3쌍 → 놓침
        repeat(3) { match(g, s[k++]) }
        miss(g)
        // 1번 5쌍 → 놓침
        repeat(5) { match(g, s[k++]) }
        miss(g)
        // 2번 바로 놓침 (0쌍)
        miss(g)
        // 3번 나머지 4쌍 → 끝
        assertEquals(3, g.current)
        repeat(4) { match(g, s[k++]) }
        assertTrue(g.gameOver)
        assertArrayEquals(intArrayOf(3, 5, 0, 4), g.pairs)
        assertArrayEquals(intArrayOf(3, 1, 4, 2), g.finalRanks())

        // 동점: 6·6 → 둘 다 1등
        val t = MemoryGame(2, Random(9))
        for (i in 0 until 6) match(t, s[i])
        miss(t)
        for (i in 6 until 12) match(t, s[i])
        assertArrayEquals(intArrayOf(6, 6), t.pairs)
        assertArrayEquals(intArrayOf(1, 1), t.finalRanks())

        // 3인 동점 끼임: 5·5·2 → 1, 1, 3
        val u = MemoryGame(3, Random(10))
        for (i in 0 until 5) match(u, s[i])
        miss(u)
        for (i in 5 until 10) match(u, s[i])
        miss(u)
        for (i in 10 until 12) match(u, s[i])
        assertArrayEquals(intArrayOf(5, 5, 2), u.pairs)
        assertArrayEquals(intArrayOf(1, 1, 3), u.finalRanks())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `인원은 2~4명`() {
        MemoryGame(5)
    }
}
