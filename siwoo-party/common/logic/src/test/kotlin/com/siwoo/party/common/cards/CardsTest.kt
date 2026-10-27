package com.siwoo.party.common.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CardsTest {

    // ── Card ──

    @Test fun `카드 54장은 모두 다르고 무늬별 13장`() {
        assertEquals(54, Card.ALL.size)
        assertEquals(54, Card.ALL.toSet().size)
        assertEquals(54, Card.ALL.map { it.shortName }.toSet().size)
        for (s in Suit.values()) {
            val ofSuit = Card.ALL.filter { it.suit == s }
            assertEquals(13, ofSuit.size)
            assertEquals((1..13).toList(), ofSuit.map { it.rank })
        }
        assertEquals(2, Card.ALL.count { it.isJoker })
        assertEquals(52, Card.STANDARD.size)
        assertTrue(Card.STANDARD.none { it.isJoker })
    }

    @Test fun `id 왕복과 같은 인스턴스`() {
        for (id in 0 until Card.COUNT) {
            val c = Card.of(id)
            assertEquals(id, c.id)
            assertSame(c, Card.of(id))
            if (!c.isJoker) assertSame(c, Card.of(c.suit!!, c.rank))
        }
        assertEquals(0, Card.of(Suit.SPADE, 1).id)
        assertEquals(51, Card.of(Suit.CLUB, 13).id)
        assertEquals(Card.BLACK_JOKER_ID, Card.BLACK_JOKER.id)
        assertEquals(Card.RED_JOKER_ID, Card.RED_JOKER.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `범위 밖 id 는 오류`() {
        Card.of(54)
    }

    @Test fun `글자와 색`() {
        assertEquals("A", Card.of(Suit.SPADE, 1).label)
        assertEquals("10", Card.of(Suit.HEART, 10).label)
        assertEquals("J", Card.of(Suit.DIAMOND, 11).label)
        assertEquals("Q", Card.of(Suit.CLUB, 12).label)
        assertEquals("K", Card.of(Suit.CLUB, 13).label)
        assertEquals("JOKER", Card.RED_JOKER.label)
        assertEquals("♥10", Card.of(Suit.HEART, 10).shortName)
        assertTrue(Card.of(Suit.HEART, 5).isRed)
        assertTrue(Card.of(Suit.DIAMOND, 5).isRed)
        assertFalse(Card.of(Suit.SPADE, 5).isRed)
        assertFalse(Card.of(Suit.CLUB, 5).isRed)
        assertTrue(Card.RED_JOKER.isRed)
        assertFalse(Card.BLACK_JOKER.isRed)
        assertTrue(Card.RED_JOKER.isRedJoker)
        assertNull(Card.RED_JOKER.suit)
        assertEquals(0, Card.BLACK_JOKER.rank)
        assertTrue(Card.of(Suit.SPADE, 11).isCourt)
        assertFalse(Card.of(Suit.SPADE, 10).isCourt)
    }

    // ── Deck ──

    @Test fun `덱은 52장 또는 조커 포함 54장이고 중복 없음`() {
        for (j in 0..2) {
            val d = Deck(j)
            assertEquals(52 + j, d.size)
            assertEquals(52 + j, d.toList().toSet().size)
            assertEquals(j, d.toList().count { it.isJoker })
        }
        // 정렬된 새 덱은 위에서부터 ♠A
        assertEquals(Card.of(Suit.SPADE, 1), Deck().draw())
    }

    @Test fun `같은 시드로 섞으면 같은 순서이고 카드는 그대로`() {
        val a = Deck(2).also { it.shuffle(Random(42)) }
        val b = Deck(2).also { it.shuffle(Random(42)) }
        val c = Deck(2).also { it.shuffle(Random(7)) }
        assertEquals(a.toList(), b.toList())
        assertNotEquals(a.toList(), c.toList())
        assertNotEquals(Deck(2).toList(), a.toList())
        assertEquals(Card.ALL.toSet(), a.toList().toSet())
        assertEquals(54, a.size)
    }

    @Test fun `뽑기`() {
        val d = Deck.shuffled(Random(1))
        val top = d.peek()
        val first = d.draw()
        assertEquals(top, first)
        assertEquals(51, d.size)
        val five = d.draw(5)
        assertEquals(5, five.size)
        assertEquals(46, d.size)
        assertFalse(first in d)
        assertTrue(five.none { it in d })
        val rest = d.draw(100)
        assertEquals(46, rest.size)
        assertTrue(d.isEmpty)
        assertNull(d.drawOrNull())
        assertNull(d.peek())
        assertEquals(52, (listOf(first) + five + rest).toSet().size)
    }

    @Test(expected = IllegalStateException::class)
    fun `빈 덱에서 draw 는 오류`() {
        val d = Deck()
        d.draw(52)
        d.draw()
    }

    @Test fun `밑에 넣기와 위에 넣기`() {
        val d = Deck()
        d.draw(50) // ♠A..  50장 뽑고 ♣Q ♣K 남음
        assertEquals(2, d.size)
        d.addToBottom(Card.RED_JOKER)
        d.addToTop(Card.BLACK_JOKER)
        assertEquals(Card.BLACK_JOKER, d.toList().first())
        assertEquals(Card.RED_JOKER, d.toList().last())
        d.addToBottom(listOf(Card.of(0), Card.of(1)))
        assertEquals(listOf(Card.of(0), Card.of(1)), d.toList().takeLast(2))
        assertTrue(d.remove(Card.RED_JOKER))
        assertFalse(Card.RED_JOKER in d)
    }

    @Test fun `버린 더미로 다시 채우면 맨 위 카드는 남고 결정적으로 섞임`() {
        fun run(seed: Int): Pair<Deck, MutableList<Card>> {
            val d = Deck.shuffled(Random(3))
            val discard = d.draw(50).toMutableList() // 끝 = 맨 위
            val visible = discard.last()
            val moved = d.refillFrom(discard, Random(seed), keepTop = 1)
            assertEquals(49, moved)
            assertEquals(listOf(visible), discard)
            assertEquals(51, d.size)
            return d to discard
        }
        val (a, pileA) = run(9)
        val (b, _) = run(9)
        assertEquals(a.toList(), b.toList())
        // 원래 남아 있던 2장은 여전히 위에
        val orig = Deck.shuffled(Random(3)).toList().takeLast(2)
        assertEquals(orig, a.toList().take(2))
        // 52장 그대로
        assertEquals(Card.STANDARD.toSet(), (a.toList() + pileA).toSet())
        // 옮길 것이 없으면 0
        assertEquals(0, a.refillFrom(pileA, Random(1), keepTop = 1))
    }

    @Test fun `id 배열로 저장하고 복원`() {
        val d = Deck.shuffled(Random(11), jokerCount = 2)
        d.draw(10)
        val back = Deck.fromIds(d.toIds())
        assertEquals(d.toList(), back.toList())
        assertEquals(d.draw(), back.draw())
    }

    // ── FanLayout ──

    private val w = 112f
    private val h = 157f

    @Test fun `부채꼴은 1장부터 20장까지 좌우 대칭이고 폭 안에 들어감`() {
        val f = FanLayout(4) // 작은 용량 → 자동으로 늘어나는지도 확인
        val avail = 900f
        val cx = 640f
        for (n in 1..20) {
            f.layout(n, avail, w, h, cx, 500f)
            assertEquals(n, f.count)
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            for (i in 0 until n) {
                val j = n - 1 - i
                assertEquals("n=$n x 대칭", cx - f.xs[i], f.xs[j] - cx, 0.01f)
                assertEquals("n=$n y 대칭", f.ys[i], f.ys[j], 0.01f)
                assertEquals("n=$n 회전 대칭", -f.rots[i], f.rots[j], 0.01f)
                if (i > 0) assertTrue("왼쪽→오른쪽 순서", f.xs[i] > f.xs[i - 1] || f.gap == 0f)
                assertTrue("가운데보다 아래(또는 같음)", f.ys[i] >= 500f - 0.01f)
                val e = FanLayout.halfExtentX(f.rots[i], w, h)
                minX = minOf(minX, f.xs[i] - e)
                maxX = maxOf(maxX, f.xs[i] + e)
            }
            assertTrue("n=$n 왼쪽 넘침 $minX", minX >= cx - avail / 2f - 0.5f)
            assertTrue("n=$n 오른쪽 넘침 $maxX", maxX <= cx + avail / 2f + 0.5f)
            assertTrue(f.rots[0] <= 0f)
        }
        // 많으면 겹친다 (간격 < 카드 폭)
        f.layout(20, avail, w, h, cx, 500f)
        assertTrue(f.gap < w)
        // 적으면 maxGapFrac 이하
        f.layout(3, avail, w, h, cx, 500f)
        assertEquals(w * 0.62f, f.gap, 0.01f)
    }

    @Test fun `회전 없이 일렬로도 배치`() {
        val f = FanLayout()
        f.layout(5, 2000f, w, h, 1000f, 300f, maxSpreadDeg = 0f, maxGapFrac = 1.1f)
        for (i in 0 until 5) {
            assertEquals(0f, f.rots[i], 0f)
            assertEquals(300f, f.ys[i], 0f)
        }
        assertEquals(w * 1.1f, f.xs[1] - f.xs[0], 0.01f)
    }

    @Test fun `맞은편 손패는 180도 돌린 배치`() {
        val f = FanLayout()
        f.layout(5, 600f, w, h, 640f, 100f)
        val x0 = f.xs[0]
        val y0 = f.ys[0]
        val r0 = f.rots[0]
        f.turnAround(640f, 100f)
        assertEquals(1280f - x0, f.xs[0], 0.01f)
        assertEquals(200f - y0, f.ys[0], 0.01f)
        assertEquals(r0 + 180f, f.rots[0], 0.01f)
        // 바깥 카드는 가운데보다 위로 (아치 반대)
        assertTrue(f.ys[0] < f.ys[2])
        // 돌린 카드 위 판정도 맞음
        assertEquals(4, f.hit(f.xs[4], f.ys[4]))
    }

    @Test fun `터치 판정은 위 카드부터`() {
        val f = FanLayout()
        f.layout(7, 900f, w, h, 640f, 500f)
        // 각 카드의 왼쪽 부분(오른쪽 이웃이 덮지 않는 곳)을 누르면 그 카드
        for (i in 0 until 7) assertEquals(i, f.hit(f.xs[i] - w * 0.25f, f.ys[i]))
        // 두 카드가 겹치는 곳 → 위(오른쪽) 카드
        val midX = (f.xs[2] + f.xs[3]) / 2f
        assertEquals(3, f.hit(midX, f.ys[3]))
        // 바깥
        assertEquals(-1, f.hit(10f, 10f))
        assertEquals(-1, f.hit(640f, 500f + h))
        // 떠오른 카드는 원래 자리 아래쪽 대신 위쪽에서 잡힌다
        val lift = FloatArray(7)
        lift[3] = 40f
        assertEquals(3, f.hit(f.xs[3], f.ys[3] - h / 2f - 30f, lift))
        assertEquals(-1, f.hit(f.xs[3], f.ys[3] - h / 2f - 30f))
    }

    @Test fun `회전한 카드 판정`() {
        // 90도 돌면 가로·세로가 바뀐다
        assertTrue(FanLayout.contains(70f, 0f, 0f, 0f, 90f, w, h))
        assertFalse(FanLayout.contains(0f, 70f, 0f, 0f, 90f, w, h))
        assertTrue(FanLayout.contains(0f, 70f, 0f, 0f, 0f, w, h))
        // 시계 방향 30도: 카드 위쪽 끝은 오른쪽으로 기운다
        val top = h / 2f - 5f
        val r = Math.toRadians(30.0)
        val px = (top * Math.sin(r)).toFloat()
        val py = (-top * Math.cos(r)).toFloat()
        assertTrue(FanLayout.contains(px, py, 0f, 0f, 30f, w, h))
        assertFalse(FanLayout.contains(-px - 20f, py, 0f, 0f, 30f, w, h))
    }
}
