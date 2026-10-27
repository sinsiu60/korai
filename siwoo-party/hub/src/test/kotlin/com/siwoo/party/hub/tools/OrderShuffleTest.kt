package com.siwoo.party.hub.tools

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 순서 정하기: 순열, 연출용 바꾸기가 결과와 일치, seed 결정성, 고른 분포 */
class OrderShuffleTest {

    @Test fun `결과는 순열`() {
        for (n in 1..6) for (s in 0 until 200) {
            val p = OrderShuffle.plan(n, Random(s))
            assertEquals((0 until n).toSet(), p.order.toSet())
            assertEquals(n, p.order.size)
        }
    }

    @Test fun `바꾸기를 차례로 하면 결과 순서`() {
        for (n in 2..6) for (s in 0 until 200) {
            val p = OrderShuffle.plan(n, Random(s))
            assertArrayEquals(p.order, p.replay())
            assertTrue(p.swaps.size >= OrderShuffle.DECOY_BASE + n)
            for (sw in p.swaps) {
                assertTrue(sw[0] != sw[1])
                assertTrue(sw[0] in 0 until n && sw[1] in 0 until n)
            }
        }
    }

    @Test fun `같은 seed 면 같은 순서와 연출`() {
        for (s in 0 until 50) {
            val a = OrderShuffle.plan(4, Random(s))
            val b = OrderShuffle.plan(4, Random(s))
            assertArrayEquals(a.order, b.order)
            assertEquals(a.swaps.map { it.toList() }, b.swaps.map { it.toList() })
        }
    }

    @Test fun `1번 자리는 고르게`() {
        val first = IntArray(4)
        val rnd = Random(3)
        repeat(4000) { first[OrderShuffle.plan(4, rnd).order[0]]++ }
        for (h in first) assertTrue("치우침 $h", h in 850..1150)
        // 순열 24가지가 모두 나온다
        val seen = HashSet<List<Int>>()
        repeat(2000) { seen.add(OrderShuffle.permutation(4, rnd).toList()) }
        assertEquals(24, seen.size)
    }
}
