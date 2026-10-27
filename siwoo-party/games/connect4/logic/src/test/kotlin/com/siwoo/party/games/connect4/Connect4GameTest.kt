package com.siwoo.party.games.connect4

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Connect4GameTest {

    private val cols = GameConfig.CONNECT4_COLS

    /** 열 순서대로 번갈아 떨어뜨리기 */
    private fun play(g: Connect4Game, vararg seq: Int) {
        for (c in seq) assertSame("열 $c", Drop.PLACED, g.drop(c))
    }

    /** 승리 줄을 (열, 행) 쌍 목록으로 */
    private fun line(g: Connect4Game): List<Pair<Int, Int>> = (0 until g.winLength).map { g.winLine[it] % cols to g.winLine[it] / cols }

    @Test
    fun `돌은 열의 가장 낮은 빈 칸에 쌓이고 차례가 바뀐다`() {
        val g = Connect4Game(1)
        assertEquals(1, g.current)
        play(g, 3)
        assertEquals(1, g.at(3, 0))
        assertEquals(0, g.current)
        play(g, 3)
        assertEquals(0, g.at(3, 1))
        assertEquals(3, g.lastCol)
        assertEquals(1, g.lastRow)
        assertEquals(2, g.heights[3])
        assertEquals(20, g.discsLeft(1))
        assertEquals(20, g.discsLeft(0))
    }

    @Test
    fun `가로 넷 승리`() {
        val g = Connect4Game(0)
        // 0: 0,1,2,3 (맨 아래) / 1: 위에 쌓기
        play(g, 0, 0, 1, 1, 2, 2, 3)
        assertSame(Phase.OVER, g.phase)
        assertEquals(0, g.winner)
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0), line(g))
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }

    @Test
    fun `세로 넷 승리`() {
        val g = Connect4Game(1)
        play(g, 6, 5, 6, 5, 6, 5, 6)
        assertEquals(1, g.winner)
        assertEquals(listOf(6 to 0, 6 to 1, 6 to 2, 6 to 3), line(g))
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test
    fun `셋으로는 승리 아님`() {
        val g = Connect4Game(0)
        play(g, 0, 0, 1, 1, 2, 2)
        assertSame(Phase.PLAY, g.phase)
        assertEquals(-1, g.winner)
        // 세로 셋 + 가로 셋 (끊긴 넷: 0,1,2 _ 4)
        val h = Connect4Game(0)
        play(h, 0, 6, 1, 6, 2, 6, 4, 5)
        assertSame(Phase.PLAY, h.phase)
        val v = Connect4Game(0)
        play(v, 2, 3, 2, 3, 2)
        assertSame(Phase.PLAY, v.phase)
    }

    @Test
    fun `대각선 오른쪽 위로 넷`() {
        val g = Connect4Game(0)
        // 0 이 (0,0)(1,1)(2,2)(3,3) — 1 은 받침
        play(g, 0, 1, 1, 2, 2, 3, 2, 3, 3, 6, 3)
        assertSame(Phase.OVER, g.phase)
        assertEquals(0, g.winner)
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2, 3 to 3), line(g))
    }

    @Test
    fun `대각선 오른쪽 아래로 넷`() {
        val g = Connect4Game(1)
        // 1 이 (3,3)(4,2)(5,1)(6,0)
        play(g, 6, 5, 5, 4, 4, 3, 4, 3, 3, 0, 3)
        assertEquals(1, g.winner)
        assertEquals(listOf(3 to 3, 4 to 2, 5 to 1, 6 to 0), line(g))
    }

    @Test
    fun `대각선 가운데 돌로 완성해도 줄 전체`() {
        val g = Connect4Game(0)
        // 0: (0,0)(1,1)(3,3) 먼저, 마지막에 (2,2) — 1 은 받침
        play(g, 0, 1, 1, 3, 3, 3, 3, 2, 6, 2, 2)
        assertEquals(0, g.winner)
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2, 3 to 3), line(g))
    }

    @Test
    fun `다섯 개 이어져도 줄 전체를 돌려준다`() {
        val g = Connect4Game(0)
        // 0: 0,1 _ 3,4 → 2 로 다섯
        play(g, 0, 0, 1, 1, 3, 3, 4, 4, 2)
        assertEquals(0, g.winner)
        assertEquals(5, g.winLength)
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0), line(g))
    }

    @Test
    fun `꽉 찬 열은 거절되고 차례 그대로`() {
        val g = Connect4Game(0)
        play(g, 2, 2, 2, 2, 2, 2)
        assertTrue(g.isFull(2))
        assertEquals(0, g.current)
        assertSame(Drop.FULL, g.drop(2))
        assertEquals("차례 그대로", 0, g.current)
        assertEquals(6, g.moveCount)
        assertSame(Drop.OUT, g.drop(7))
        assertSame(Drop.OUT, g.drop(-1))
        assertSame(Drop.PLACED, g.drop(3))
        assertEquals(1, g.current)
    }

    /** 무승부 순서 (탐색으로 찾은 것: 어느 순간에도 넷이 안 생김) */
    private val drawSeq = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6,
        1, 0, 3, 2, 5, 4, 0, 6, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6,
    )

    @Test
    fun `판이 다 차면 무승부 둘 다 1위`() {
        val g = Connect4Game(1)
        for ((k, c) in drawSeq.withIndex()) {
            assertSame(Phase.PLAY, g.phase)
            assertSame("수 $k", Drop.PLACED, g.drop(c))
        }
        assertEquals(42, g.moveCount)
        assertSame(Phase.OVER, g.phase)
        assertTrue(g.isDraw)
        assertEquals(-1, g.winner)
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
        assertEquals(0, g.discsLeft(0))
        assertEquals(0, g.discsLeft(1))
        for (c in 0 until cols) assertTrue(g.isFull(c))
        assertSame(Drop.OVER, g.drop(0))
    }

    @Test
    fun `끝난 뒤엔 못 둔다`() {
        val g = Connect4Game(0)
        play(g, 0, 1, 0, 1, 0, 1, 0)
        assertSame(Drop.OVER, g.drop(5))
        assertEquals(7, g.moveCount)
    }

    // ── 튕김 ──

    @Test
    fun `튕김 - 떨어져서 바닥에서 멈추고 바닥 아래로 내려가지 않음`() {
        for (dist in floatArrayOf(1f, 3f, 6f, 7f)) {
            val total = Bounce.totalMs(dist)
            val fall = Bounce.fallMs(dist)
            assertTrue(total > fall)
            assertEquals(0f, Bounce.y(0f, dist), 0f)
            assertEquals(dist, Bounce.y(total + 1f, dist), 0f)
            assertEquals(dist, Bounce.y(fall, dist), 1e-3f)
            var prev = 0f
            var t = 0f
            while (t < fall) {
                val y = Bounce.y(t, dist)
                assertTrue("처음엔 계속 내려감", y >= prev)
                prev = y
                t += 5f
            }
            t = 0f
            var minAfter = dist
            while (t <= total + 20f) {
                val y = Bounce.y(t, dist)
                assertTrue("바닥 아래로 안 감 ($dist, $t)", y <= dist + 1e-4f)
                assertTrue(y >= 0f)
                if (t > fall) minAfter = minOf(minAfter, y)
                t += 3f
            }
            assertTrue("한 번은 위로 튕김 ($dist)", minAfter < dist - 0.05f)
            assertTrue("튕김은 낮게 (${dist - minAfter})", dist - minAfter < dist * 0.2f)
            assertEquals(0, Bounce.phase(1f, dist))
            assertEquals(1, Bounce.phase(fall + 1f, dist))
            assertEquals(-1, Bounce.phase(total + 1f, dist))
        }
        assertTrue("맨 위에서 바닥까지 1초 안", Bounce.totalMs(7f) < 1000f)
    }
}
