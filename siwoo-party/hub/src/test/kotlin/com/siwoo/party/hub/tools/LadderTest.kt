package com.siwoo.party.hub.tools

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 사다리: 1:1 대응(순열), 가로줄 끝 겹침 없음, 경로가 같은 자리에서 만나지 않음, seed 결정성 */
class LadderTest {

    private val seeds = 0 until 300

    @Test fun `시작마다 서로 다른 도착 (순열)`() {
        for (n in 2..6) for (s in seeds) {
            val l = Ladder.generate(n, Random(s))
            val m = l.mapping()
            assertEquals("n=$n seed=$s", (0 until n).toSet(), m.toSet())
        }
    }

    @Test fun `같은 단에서 한 세로줄에 가로줄 끝이 둘 생기지 않는다`() {
        for (n in 2..6) for (s in seeds) {
            val l = Ladder.generate(n, Random(s))
            for (r in 0 until l.rows) {
                val touch = IntArray(n)
                for (c in 0 until n - 1) if (l.rungs[r][c]) { touch[c]++; touch[c + 1]++ }
                assertTrue("n=$n seed=$s r=$r", touch.all { it <= 1 })
            }
        }
    }

    @Test fun `경로는 매 단마다 서로 다른 세로줄에 있다 (만나거나 겹치지 않음)`() {
        for (n in 2..6) for (s in seeds) {
            val l = Ladder.generate(n, Random(s))
            val routes = (0 until n).map { l.route(it) }
            for (r in 0 until l.rows) {
                val at = routes.map { it[r] }
                assertEquals("n=$n seed=$s r=$r", n, at.toSet().size)
                assertTrue(at.all { it in 0 until n })
            }
            // 한 단에 한 칸 넘게 움직이지 않음, 가로줄이 있는 곳으로만 움직임
            for (st in 0 until n) {
                var prev = st
                for (r in 0 until l.rows) {
                    val c = routes[st][r]
                    assertTrue(kotlin.math.abs(c - prev) <= 1)
                    if (c == prev + 1) assertTrue(l.rungs[r][prev])
                    if (c == prev - 1) assertTrue(l.rungs[r][c])
                    if (c == prev) { assertFalse(l.hasRung(r, c)); assertFalse(l.hasRung(r, c - 1)) }
                    prev = c
                }
                assertEquals(prev, l.end(st))
            }
        }
    }

    @Test fun `이웃한 세로줄 쌍마다 가로줄이 하나 이상`() {
        for (n in 2..6) for (s in seeds) {
            val l = Ladder.generate(n, Random(s))
            for (c in 0 until n - 1) assertTrue("n=$n seed=$s c=$c", (0 until l.rows).any { l.rungs[it][c] })
        }
    }

    @Test fun `흔들림은 순서를 바꾸지 않을 만큼 작다`() {
        for (s in seeds) {
            val l = Ladder.generate(6, Random(s))
            for (row in l.shift) for (v in row) assertTrue(v >= -Ladder.MAX_SHIFT && v <= Ladder.MAX_SHIFT && Ladder.MAX_SHIFT < 0.5f)
        }
    }

    @Test fun `같은 seed 면 같은 사다리`() {
        for (n in 2..6) for (s in 0 until 50) {
            val a = Ladder.generate(n, Random(s))
            val b = Ladder.generate(n, Random(s))
            for (r in 0 until a.rows) {
                assertArrayEquals(a.rungs[r], b.rungs[r])
                assertArrayEquals(a.shift[r], b.shift[r], 0f)
            }
            assertArrayEquals(a.mapping(), b.mapping())
        }
        // 다른 seed 는 대개 다르다
        val diff = (0 until 50).count { s ->
            !Ladder.generate(5, Random(s)).rungs.contentDeepEquals(Ladder.generate(5, Random(s + 1000)).rungs)
        }
        assertTrue(diff > 45)
    }

    @Test fun `도착은 고르게 섞인다`() {
        val n = 4
        val hits = Array(n) { IntArray(n) }
        val rnd = Random(11)
        repeat(4000) {
            val l = Ladder.generate(n, rnd)
            for (st in 0 until n) hits[st][l.end(st)]++
        }
        // 모든 시작이 모든 도착에 닿을 수 있다 (너무 치우치지 않음)
        for (st in 0 until n) for (e in 0 until n) assertTrue("$st→$e ${hits[st][e]}", hits[st][e] > 400)
    }

    @Test fun `잘못된 세로줄 수는 거절`() {
        var thrown = 0
        for (n in listOf(1, 7)) try { Ladder.generate(n, Random(0)) } catch (e: IllegalArgumentException) { thrown++ }
        assertEquals(2, thrown)
    }

    @Test fun `직접 만든 사다리 경로`() {
        // 세로줄 3: 0단 (0-1), 1단 (1-2)
        val rungs = arrayOf(booleanArrayOf(true, false), booleanArrayOf(false, true))
        val l = Ladder(3, 2, rungs, Array(2) { FloatArray(2) })
        assertArrayEquals(intArrayOf(1, 2), l.route(0))
        assertArrayEquals(intArrayOf(2, 0, 1), l.mapping())
        assertNotEquals(0, l.rungCount())
    }
}
