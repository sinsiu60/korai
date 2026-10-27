package com.siwoo.party.games.boxes

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxesGameTest {

    private fun drawn(m: Move): Move.Drawn = m as Move.Drawn

    // ── 판 크기 ──

    @Test
    fun `인원별 판 크기 - 2인 6x6 점 3~4인 7x7 점`() {
        assertEquals(GameConfig.BOXES_GRID_2P, BoxesGame(2).grid.dots)
        assertEquals(GameConfig.BOXES_GRID_34P, BoxesGame(3).grid.dots)
        assertEquals(GameConfig.BOXES_GRID_34P, BoxesGame(4).grid.dots)
        assertEquals(6, BoxesGrid.dotsFor(2))
        assertEquals(7, BoxesGrid.dotsFor(4))
        assertEquals(25, BoxesGame(2).grid.boxCount)
        assertEquals(36, BoxesGame(4).grid.boxCount)
    }

    @Test
    fun `선 개수 - 6x6 점은 60개 7x7 점은 84개`() {
        assertEquals(60, BoxesGame(2).grid.lineCount)
        assertEquals(84, BoxesGame(3).grid.lineCount)
        assertEquals(84, BoxesGame(4).grid.lineCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `1인은 안 됨`() { BoxesGame(1) }

    @Test(expected = IllegalArgumentException::class)
    fun `5인은 안 됨`() { BoxesGame(5) }

    @Test
    fun `칸의 네 변과 선 양쪽 칸이 서로 맞음`() {
        val g = BoxesGrid(7)
        for (b in 0 until g.boxCount) {
            assertEquals(b, g.boxAfter(g.top(b)))
            assertEquals(b, g.boxBefore(g.bottom(b)))
            assertEquals(b, g.boxAfter(g.left(b)))
            assertEquals(b, g.boxBefore(g.right(b)))
        }
        // 가장자리 선은 한쪽만 칸
        assertEquals(-1, g.boxBefore(g.hLine(0, 2)))
        assertEquals(-1, g.boxAfter(g.hLine(6, 2)))
        assertEquals(-1, g.boxBefore(g.vLine(3, 0)))
        assertEquals(-1, g.boxAfter(g.vLine(3, 6)))
        // 모든 선 번호가 서로 다름
        val all = HashSet<Int>()
        for (r in 0 until 7) for (c in 0 until 6) all.add(g.hLine(r, c))
        for (r in 0 until 6) for (c in 0 until 7) all.add(g.vLine(r, c))
        assertEquals(84, all.size)
        assertEquals((0 until 84).toSet(), all)
    }

    // ── 차례 ──

    @Test
    fun `칸을 못 만들면 다음 사람 - players 순서로 돈다`() {
        val g = BoxesGame(3)
        val grid = g.grid
        val m1 = drawn(g.draw(grid.hLine(0, 0)))
        assertEquals(0, m1.player)
        assertFalse(m1.keepsTurn)
        assertEquals(1, m1.next)
        assertEquals(1, g.current)
        drawn(g.draw(grid.hLine(0, 3)))
        assertEquals(2, g.current)
        drawn(g.draw(grid.vLine(4, 6)))
        assertEquals("한 바퀴 돌아 처음 사람", 0, g.current)
        assertEquals(grid.vLine(4, 6), g.lastLine)
        assertEquals(0, g.lineOwner[grid.hLine(0, 0)])
        assertEquals(1, g.lineOwner[grid.hLine(0, 3)])
    }

    @Test
    fun `칸을 완성하면 추가 턴 - 같은 사람이 한 번 더`() {
        val g = BoxesGame(2)
        val grid = g.grid
        val b = grid.box(2, 2)
        g.draw(grid.top(b))     // 0
        g.draw(grid.bottom(b))  // 1
        g.draw(grid.left(b))    // 0
        assertEquals(1, g.current)
        val m = drawn(g.draw(grid.right(b))) // 1 이 완성
        assertEquals(1, m.player)
        assertArrayEquals(intArrayOf(b), m.boxes)
        assertTrue(m.keepsTurn)
        assertEquals(1, m.next)
        assertEquals("완성한 사람이 계속", 1, g.current)
        assertEquals(1, g.boxOwner[b])
        assertArrayEquals(intArrayOf(0, 1), g.scores)
        // 이어서 칸을 못 만들면 다음 사람
        val m2 = drawn(g.draw(grid.hLine(0, 0)))
        assertFalse(m2.keepsTurn)
        assertEquals(0, g.current)
    }

    @Test
    fun `한 선으로 2칸 완성 - 둘 다 차지하고 2점`() {
        val g = BoxesGame(2)
        val grid = g.grid
        val a = grid.box(1, 1)
        val b = grid.box(1, 2) // a 오른쪽 칸, 가운데 세로선을 함께 씀
        val shared = grid.right(a)
        assertEquals(shared, grid.left(b))
        // 공유 선만 빼고 두 칸의 나머지 변 6개를 긋는다 (아무도 칸을 못 만듦)
        for (line in intArrayOf(grid.top(a), grid.bottom(a), grid.left(a), grid.top(b), grid.bottom(b), grid.right(b))) {
            val m = drawn(g.draw(line))
            assertEquals(0, m.boxes.size)
        }
        assertEquals(0, g.current) // 6번 그어 0 → 1 → … → 0
        val m = drawn(g.draw(shared))
        assertEquals(0, m.player)
        assertEquals(2, m.boxes.size)
        assertEquals(setOf(a, b), m.boxes.toSet())
        assertTrue(m.keepsTurn)
        assertEquals(0, g.boxOwner[a])
        assertEquals(0, g.boxOwner[b])
        assertEquals("점수 +2", 2, g.scores[0])
        assertEquals(0, g.current)
        assertArrayEquals(intArrayOf(a, b), g.boxesOf(0))
    }

    @Test
    fun `이미 그은 선은 거절`() {
        val g = BoxesGame(2)
        val line = g.grid.vLine(2, 3)
        assertTrue(g.draw(line) is Move.Drawn)
        assertEquals(1, g.current)
        assertSame(Move.Rejected, g.draw(line))
        assertEquals("차례도 그대로", 1, g.current)
        assertEquals(1, g.drawnCount)
        assertEquals(0, g.lineOwner[line])
        // 없는 선 번호도 거절
        assertSame(Move.Rejected, g.draw(-1))
        assertSame(Move.Rejected, g.draw(g.grid.lineCount))
    }

    // ── 종료 ──

    /** 모든 선을 차례로 그으며 마지막 선에서만 끝나는지 */
    private fun playAll(players: Int, expectedLines: Int) {
        val g = BoxesGame(players)
        assertEquals(expectedLines, g.grid.lineCount)
        for (line in 0 until g.grid.lineCount) {
            assertFalse("선 ${line}개 그은 뒤에는 아직", g.gameOver)
            val m = drawn(g.draw(line))
            assertEquals(line == g.grid.lineCount - 1, m.gameOver)
        }
        assertTrue(g.gameOver)
        assertEquals(g.grid.boxCount, g.scores.sum())
        assertTrue(g.boxOwner.all { it >= 0 })
        assertSame("끝난 뒤에는 거절", Move.Rejected, g.draw(0))
    }

    @Test
    fun `종료 판정 - 모든 선을 그었을 때 정확히 끝남 2인 60개`() = playAll(2, 60)

    @Test
    fun `종료 판정 - 3인 4인 84개`() {
        playAll(3, 84)
        playAll(4, 84)
    }

    @Test
    fun `마지막 선이 칸을 완성해도 끝`() {
        val g = BoxesGame(2)
        val last = g.grid.vLine(4, 5)
        for (line in 0 until g.grid.lineCount) if (line != last) g.draw(line)
        assertFalse(g.gameOver)
        val m = drawn(g.draw(last))
        assertTrue(m.gameOver)
        assertEquals(1, m.boxes.size)
        assertTrue(m.keepsTurn)
    }

    // ── 순위 ──

    @Test
    fun `순위 - 칸이 많은 순 같으면 공동`() {
        val g = BoxesGame(4)
        g.scores[0] = 10; g.scores[1] = 13; g.scores[2] = 10; g.scores[3] = 3
        assertArrayEquals(intArrayOf(2, 1, 2, 4), g.finalRanks())
        g.scores[0] = 9; g.scores[1] = 9; g.scores[2] = 9; g.scores[3] = 9
        assertArrayEquals(intArrayOf(1, 1, 1, 1), g.finalRanks())
    }

    @Test
    fun `순위 - 실제로 끝까지 둔 판`() {
        val g = BoxesGame(2)
        for (line in 0 until g.grid.lineCount) g.draw(line)
        val ranks = g.finalRanks()
        val s = g.scores
        when {
            s[0] > s[1] -> assertArrayEquals(intArrayOf(1, 2), ranks)
            s[0] < s[1] -> assertArrayEquals(intArrayOf(2, 1), ranks)
            else -> assertArrayEquals(intArrayOf(1, 1), ranks)
        }
        assertEquals(25, s.sum())
    }

    // ── 가까운 선 고르기 ──

    @Test
    fun `가까운 선 - 선 가운데와 선 위를 누르면 그 선`() {
        val grid = BoxesGrid(6)
        val none = BooleanArray(grid.lineCount)
        for (line in 0 until grid.lineCount) {
            assertEquals(line, grid.nearestFreeLine(grid.midX(line), grid.midY(line), none))
        }
        // 선에서 조금 비켜 눌러도 (0.3칸) 그 선
        assertEquals(grid.hLine(2, 1), grid.nearestFreeLine(1.5f, 2.3f, none))
        assertEquals(grid.hLine(2, 1), grid.nearestFreeLine(1.6f, 1.72f, none))
        assertEquals(grid.vLine(3, 4), grid.nearestFreeLine(4.35f, 3.5f, none))
        assertEquals(grid.vLine(3, 4), grid.nearestFreeLine(3.7f, 3.4f, none))
    }

    @Test
    fun `가까운 선 - 점 근처는 손가락이 기운 쪽 선`() {
        val grid = BoxesGrid(6)
        val none = BooleanArray(grid.lineCount)
        // 점 (2, 3) 바로 오른쪽 → 오른쪽 가로선
        assertEquals(grid.hLine(3, 2), grid.nearestFreeLine(2.2f, 3.02f, none))
        // 바로 아래 → 아래 세로선
        assertEquals(grid.vLine(3, 2), grid.nearestFreeLine(2.03f, 3.25f, none))
        // 바로 왼쪽 → 왼쪽 가로선
        assertEquals(grid.hLine(3, 1), grid.nearestFreeLine(1.8f, 2.98f, none))
    }

    @Test
    fun `가까운 선 - 그은 선은 건너뛰고 다음으로 가까운 빈 선`() {
        val grid = BoxesGrid(6)
        val drawn = BooleanArray(grid.lineCount)
        val b = grid.box(2, 2)
        // 칸 아래쪽 변 근처 (아래 변 0.2, 왼쪽 변 0.4)
        assertEquals(grid.bottom(b), grid.nearestFreeLine(2.4f, 2.8f, drawn))
        drawn[grid.bottom(b)] = true
        // 아래 변이 그어졌으면 그다음으로 가까운 왼쪽 변
        assertEquals(grid.left(b), grid.nearestFreeLine(2.4f, 2.8f, drawn))
        // 위 변 근처를 누르면 아래 변(그음)은 건너뛰고 위 변
        assertEquals(grid.top(b), grid.nearestFreeLine(2.5f, 2.1f, drawn))
        // 칸 가운데: 세 변을 그었으면 남은 한 변
        drawn[grid.top(b)] = true
        drawn[grid.left(b)] = true
        assertEquals(grid.right(b), grid.nearestFreeLine(2.5f, 2.5f, drawn))
        // 네 변 다 그었으면 가운데는 없음
        drawn[grid.right(b)] = true
        assertEquals(-1, grid.nearestFreeLine(2.5f, 2.5f, drawn))
        // 점 근처: 오른쪽 가로선이 그어졌으면 아래 세로선
        val d2 = BooleanArray(grid.lineCount)
        d2[grid.hLine(3, 2)] = true
        assertEquals(grid.vLine(3, 2), grid.nearestFreeLine(2.15f, 3.05f, d2))
    }

    @Test
    fun `가까운 선 - 판에서 먼 곳은 없음`() {
        val grid = BoxesGrid(7)
        val none = BooleanArray(grid.lineCount)
        assertEquals(-1, grid.nearestFreeLine(-0.6f, 3.5f, none))
        assertEquals(-1, grid.nearestFreeLine(3f, -0.7f, none))
        assertEquals(-1, grid.nearestFreeLine(6.8f, 6.8f, none))
        assertEquals(-1, grid.nearestFreeLine(20f, 20f, none))
        // 판 바로 바깥(0.4칸)은 가장자리 선
        assertEquals(grid.vLine(2, 0), grid.nearestFreeLine(-0.4f, 2.5f, none))
        assertEquals(grid.hLine(6, 3), grid.nearestFreeLine(3.5f, 6.4f, none))
        // 모두 그은 판은 어디도 없음
        val all = BooleanArray(grid.lineCount) { true }
        assertEquals(-1, grid.nearestFreeLine(2.5f, 2f, all))
    }

    @Test
    fun `선분 거리 계산`() {
        val grid = BoxesGrid(6)
        val h = grid.hLine(1, 1) // (1,1)-(2,1)
        assertEquals(0f, grid.distanceToLine(h, 1.5f, 1f), 1e-5f)
        assertEquals(0.3f, grid.distanceToLine(h, 1.2f, 1.3f), 1e-5f)
        assertEquals(0.5f, grid.distanceToLine(h, 2.3f, 1.4f), 1e-5f) // 끝점에서 (0.3, 0.4)
        val v = grid.vLine(0, 3) // (3,0)-(3,1)
        assertEquals(3f, grid.x0(v), 0f)
        assertEquals(0f, grid.y0(v), 0f)
        assertEquals(3f, grid.x1(v), 0f)
        assertEquals(1f, grid.y1(v), 0f)
        assertEquals(0.5f, grid.midY(v), 0f)
        assertEquals(0.25f, grid.distanceToLine(v, 3.25f, 0.5f), 1e-5f)
    }
}
