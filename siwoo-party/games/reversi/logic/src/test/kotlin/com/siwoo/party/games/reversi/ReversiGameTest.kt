package com.siwoo.party.games.reversi

import com.siwoo.party.games.reversi.ReversiGame.Companion.BLACK
import com.siwoo.party.games.reversi.ReversiGame.Companion.EMPTY
import com.siwoo.party.games.reversi.ReversiGame.Companion.WHITE
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReversiGameTest {

    private val n = GameConfig.BOARD_SIZE

    /** 빈 판에 (x, y, 'B'/'W') 를 찍은 글자 판 */
    private fun rows(vararg stones: Triple<Int, Int, Char>): List<String> {
        val a = Array(n) { CharArray(n) { '.' } }
        for ((x, y, c) in stones) a[y][x] = c
        return a.map { String(it) }
    }

    private fun cell(x: Int, y: Int) = y * n + x

    /** 놓인 뒤 뒤집힌 칸 목록 */
    private fun flippedList(g: ReversiGame): List<Int> = (0 until g.flipCount).map { g.flipped[it] }

    /** 차례대로 두기 (흑부터, 패스는 게임이 알아서) */
    private fun play(g: ReversiGame, vararg xy: Int) {
        var i = 0
        while (i < xy.size) {
            assertSame("(${xy[i]},${xy[i + 1]}) 수", Move.PLACED, g.place(xy[i], xy[i + 1]))
            i += 2
        }
    }

    // ── 시작·둘 곳 ──

    @Test
    fun `처음 판은 가운데 4개 - 흑이 먼저`() {
        val g = ReversiGame()
        assertEquals(WHITE, g.at(3, 3))
        assertEquals(WHITE, g.at(4, 4))
        assertEquals(BLACK, g.at(4, 3))
        assertEquals(BLACK, g.at(3, 4))
        assertEquals(2, g.count(BLACK))
        assertEquals(2, g.count(WHITE))
        assertEquals(60, (0 until g.cells).count { g.board[it] == EMPTY })
        assertEquals(BLACK, g.current)
        assertSame(Phase.PLAY, g.phase)
        assertEquals(-1, g.lastMove)
    }

    @Test
    fun `처음 둘 곳은 정확히 4곳`() {
        val g = ReversiGame()
        val out = IntArray(64)
        val c = g.legalMoves(BLACK, out)
        assertEquals(4, c)
        assertEquals(listOf(cell(3, 2), cell(2, 3), cell(5, 4), cell(4, 5)).sorted(), out.take(c))
        assertEquals(4, g.legalCount)
        assertEquals(setOf(cell(3, 2), cell(2, 3), cell(5, 4), cell(4, 5)), (0 until 64).filter { g.legal[it] }.toSet())
        // 백도 처음엔 대칭으로 4곳
        val w = g.legalMoves(WHITE, out)
        assertEquals(listOf(cell(4, 2), cell(5, 3), cell(2, 4), cell(3, 5)).sorted(), out.take(w))
        // 돌 옆이라도 끼우지 못하면 못 둠
        assertFalse(g.isLegal(BLACK, 2, 2))
        assertFalse(g.isLegal(BLACK, 5, 5))
        assertFalse(g.isLegal(BLACK, 3, 3))
    }

    @Test
    fun `첫 수를 두면 하나 뒤집히고 백 차례 - 둘 곳 갱신`() {
        val g = ReversiGame()
        assertSame(Move.PLACED, g.place(3, 2))
        assertEquals(BLACK, g.at(3, 2))
        assertEquals(BLACK, g.at(3, 3))
        assertEquals(listOf(cell(3, 3)), flippedList(g))
        assertEquals(4, g.count(BLACK))
        assertEquals(1, g.count(WHITE))
        assertEquals(WHITE, g.current)
        assertEquals(cell(3, 2), g.lastMove)
        assertEquals(BLACK, g.lastMover)
        assertEquals(-1, g.passed)
        // 백이 둘 곳: (2,2) (4,2) (2,4)
        assertEquals(setOf(cell(2, 2), cell(4, 2), cell(2, 4)), (0 until 64).filter { g.legal[it] }.toSet())
    }

    @Test
    fun `못 두는 자리는 거절 - 판·차례 그대로`() {
        val g = ReversiGame()
        assertSame(Move.ILLEGAL, g.place(0, 0))
        assertSame(Move.ILLEGAL, g.place(2, 2))
        assertSame(Move.OCCUPIED, g.place(3, 3))
        assertSame(Move.OUT, g.place(8, 0))
        assertSame(Move.OUT, g.place(-1, 3))
        assertEquals(EMPTY, g.at(0, 0))
        assertEquals(BLACK, g.current)
        assertEquals(0, g.moveCount)
        assertEquals(2, g.count(BLACK))
    }

    // ── 8방향 뒤집기 ──

    @Test
    fun `8방향 각각 끼인 돌 둘을 뒤집음`() {
        val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
        for ((dx, dy) in dirs) {
            val g = ReversiGame()
            // (3,4) 에 놓을 때 방향마다 백 둘 + 흑 하나 (y 는 4 ± 3 이 판 안이 되게 1..7)
            val x = 3
            val y = 4
            g.load(rows(Triple(x + dx, y + dy, 'W'), Triple(x + 2 * dx, y + 2 * dy, 'W'), Triple(x + 3 * dx, y + 3 * dy, 'B')), BLACK)
            assertSame("방향 $dx,$dy", Move.PLACED, g.place(x, y))
            assertEquals(2, g.flipCount)
            assertEquals(listOf(cell(x + dx, y + dy), cell(x + 2 * dx, y + 2 * dy)), flippedList(g))
            assertEquals(1, g.flipDist[0])
            assertEquals(2, g.flipDist[1])
            assertEquals(4, g.count(BLACK))
            assertEquals(0, g.count(WHITE))
        }
    }

    @Test
    fun `여러 방향을 한 번에 - 가까운 돌부터 순서`() {
        val g = ReversiGame()
        // (3,3) 에 놓음: → 백 3개, ↑ 백 1개, ↙ 백 2개, ↖ 백 1개 + 끝의 흑
        g.load(rows(
            Triple(4, 3, 'W'), Triple(5, 3, 'W'), Triple(6, 3, 'W'), Triple(7, 3, 'B'),
            Triple(3, 2, 'W'), Triple(3, 1, 'B'),
            Triple(2, 4, 'W'), Triple(1, 5, 'W'), Triple(0, 6, 'B'),
            Triple(2, 2, 'W'), Triple(1, 1, 'B'),
            // 막히지 않은 방향 (↓ 백 하나 뒤 빈칸) 은 안 뒤집힘
            Triple(3, 4, 'W'),
        ), BLACK)
        assertSame(Move.PLACED, g.place(3, 3))
        assertEquals(7, g.flipCount)
        val dists = (0 until g.flipCount).map { g.flipDist[it] }
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 3), dists)
        assertEquals(setOf(cell(4, 3), cell(3, 2), cell(2, 4), cell(2, 2)), flippedList(g).take(4).toSet())
        assertEquals(setOf(cell(5, 3), cell(1, 5)), flippedList(g).subList(4, 6).toSet())
        assertEquals(cell(6, 3), g.flipped[6])
        assertEquals(WHITE, g.at(3, 4))
        assertEquals(1, g.count(WHITE))
        assertEquals(4 + 7 + 1, g.count(BLACK))
    }

    @Test
    fun `빈칸이 끼면 안 뒤집힘`() {
        val g = ReversiGame()
        // (0,0) → : 백 백 빈칸 흑  /  ↓ : 백 하나 + 흑 (이 방향만 뒤집힘)
        g.load(rows(Triple(1, 0, 'W'), Triple(2, 0, 'W'), Triple(4, 0, 'B'), Triple(0, 1, 'W'), Triple(0, 2, 'B')), BLACK)
        assertEquals(1, g.flipsFor(BLACK, 0, 0))
        assertSame(Move.PLACED, g.place(0, 0))
        assertEquals(listOf(cell(0, 1)), flippedList(g))
        assertEquals(WHITE, g.at(1, 0))
        assertEquals(WHITE, g.at(2, 0))
    }

    @Test
    fun `판 끝까지 상대 돌이면 안 뒤집힘`() {
        val g = ReversiGame()
        // (2,5) → : 백이 판 끝(7,5)까지 / ← 바로 옆 흑 (끼인 돌 없음) / ↑ 백 하나 + 흑
        g.load(rows(
            Triple(3, 5, 'W'), Triple(4, 5, 'W'), Triple(5, 5, 'W'), Triple(6, 5, 'W'), Triple(7, 5, 'W'),
            Triple(1, 5, 'B'),
            Triple(2, 4, 'W'), Triple(2, 3, 'B'),
        ), BLACK)
        assertEquals(1, g.flipsFor(BLACK, 2, 5))
        g.place(2, 5)
        assertEquals(listOf(cell(2, 4)), flippedList(g))
        for (x in 3..7) assertEquals(WHITE, g.at(x, 5))
        // 아무 방향도 못 끼우는 자리 → 0 이면 거절
        val h = ReversiGame()
        h.load(rows(Triple(6, 0, 'W'), Triple(7, 0, 'W'), Triple(3, 3, 'W'), Triple(3, 4, 'B')), BLACK)
        assertEquals(BLACK, h.current)
        assertEquals(0, h.flipsFor(BLACK, 5, 0))
        assertSame(Move.ILLEGAL, h.place(5, 0))
    }

    @Test
    fun `내 돌이 바로 옆이면 그 방향은 0 - 상대 돌 사이 빈틈 없어야`() {
        val g = ReversiGame()
        g.load(rows(Triple(4, 4, 'B'), Triple(5, 4, 'W'), Triple(6, 4, 'B'), Triple(2, 4, 'W'), Triple(1, 4, 'B')), BLACK)
        // (3,4): → 바로 흑 (0), ← 백 + 흑 (1)
        assertEquals(1, g.flipsFor(BLACK, 3, 4))
        // 백이 (3,4) 에 둔다면: → 흑 하나 + 백 (1), ← 바로 백 (0)
        assertEquals(1, g.flipsFor(WHITE, 3, 4))
    }

    // ── 패스·끝 ──

    @Test
    fun `둘 곳이 없으면 자동 패스 - 같은 사람이 이어서`() {
        val g = ReversiGame()
        // 흑·백 번갈아 8수: 마지막 백 수 뒤 흑은 둘 곳이 없음
        play(g, 3, 2, 2, 2, 1, 2, 1, 1, 5, 4, 0, 2, 0, 0, 2, 0)
        assertSame(Phase.PLAY, g.phase)
        assertEquals(BLACK, g.passed)
        assertEquals(1, g.passCount)
        assertEquals(WHITE, g.current)
        assertFalse(g.hasMove(BLACK))
        assertTrue(g.legalCount > 0)
        // 백이 다시 두면 패스 기록은 지워짐 (흑이 둘 곳이 생기든 말든 새로 판정)
        val out = IntArray(64)
        assertTrue(g.legalMoves(WHITE, out) > 0)
        g.place(out[0] % n, out[0] / n)
        assertTrue(g.passed == -1 || g.passed == BLACK)
    }

    @Test
    fun `시작할 차례에 둘 곳이 없으면 바로 패스`() {
        val g = ReversiGame()
        // 흑은 둘 곳 없음, 백은 (2,0) 에 둘 수 있음
        g.load(rows(Triple(0, 0, 'W'), Triple(1, 0, 'B')), BLACK)
        assertEquals(WHITE, g.current)
        assertEquals(BLACK, g.passed)
        assertTrue(g.legal[cell(2, 0)])
    }

    @Test
    fun `둘 다 둘 곳이 없으면 끝 - 빈칸 남아도`() {
        val g = ReversiGame()
        // 가장 짧은 판: 9수 만에 백 돌이 모두 사라짐
        play(g, 3, 2, 2, 2, 1, 2, 3, 1, 4, 0, 3, 5, 3, 6, 4, 2, 5, 3)
        assertSame(Phase.OVER, g.phase)
        assertSame(EndReason.NO_MOVES, g.endReason)
        assertFalse(g.isFull)
        assertEquals(13, g.count(BLACK))
        assertEquals(0, g.count(WHITE))
        assertEquals(BLACK, g.winner)
        assertEquals(0, g.legalCount)
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
        assertSame(Move.OVER, g.place(0, 0))
    }

    /** 마지막 빈칸 (7,7) 하나 남은 판: 흑이 두면 (6,7) 하나만 뒤집힘. topRow 로 돌 수 조절 */
    private fun lastCellBoard(topRow: String) = listOf(
        topRow,
        "BBBBBBBB",
        "BBBBBBBB",
        "BBBWWWWW",
        "WWWWWWWW",
        "WWWWWWWW",
        "WWWWWWBB",
        "WWWWWBW.",
    )

    @Test
    fun `판이 가득 차면 끝 - 같으면 무승부 둘 다 1위`() {
        val g = ReversiGame()
        g.load(lastCellBoard("BBBBBBBB"), BLACK)
        assertEquals(30, g.count(BLACK))
        assertEquals(33, g.count(WHITE))
        assertEquals(1, g.legalCount)
        assertSame(Move.PLACED, g.place(7, 7))
        assertEquals(1, g.flipCount)
        assertSame(Phase.OVER, g.phase)
        assertSame(EndReason.FULL, g.endReason)
        assertTrue(g.isFull)
        assertEquals(32, g.count(BLACK))
        assertEquals(32, g.count(WHITE))
        assertTrue(g.isDraw)
        assertEquals(-1, g.winner)
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    @Test
    fun `판이 가득 차면 끝 - 많은 쪽 승리`() {
        val g = ReversiGame()
        g.load(lastCellBoard("WWWWWWWW"), BLACK)
        g.place(7, 7)
        assertSame(EndReason.FULL, g.endReason)
        assertEquals(24, g.count(BLACK))
        assertEquals(40, g.count(WHITE))
        assertEquals(WHITE, g.winner)
        assertFalse(g.isDraw)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test
    fun `순위 - 돌 수로만`() {
        val g = ReversiGame()
        g.load(rows(Triple(0, 0, 'B'), Triple(7, 7, 'W'), Triple(7, 6, 'W')), BLACK)
        // 둘 다 둘 곳이 없음 → 바로 끝, 백 2 : 흑 1
        assertSame(Phase.OVER, g.phase)
        assertSame(EndReason.NO_MOVES, g.endReason)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
        g.load(rows(Triple(0, 0, 'B'), Triple(7, 7, 'W')), WHITE)
        assertSame(Phase.OVER, g.phase)
        assertTrue(g.isDraw)
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    @Test
    fun `아무 수나 끝까지 두면 늘 끝나고 돌 수가 맞음`() {
        val out = IntArray(64)
        for (seed in 1..20) {
            val rnd = kotlin.random.Random(seed)
            val g = ReversiGame()
            var guard = 0
            while (g.phase == Phase.PLAY) {
                val c = g.legalMoves(g.current, out)
                assertTrue(c > 0)
                assertEquals(c, g.legalCount)
                val pick = out[rnd.nextInt(c)]
                assertSame(Move.PLACED, g.place(pick % n, pick / n))
                assertEquals((0 until 64).count { g.board[it] == BLACK }, g.count(BLACK))
                assertEquals((0 until 64).count { g.board[it] == WHITE }, g.count(WHITE))
                assertTrue(++guard <= 60)
            }
            assertEquals(guard, g.moveCount)
            assertFalse(g.hasMove(BLACK))
            assertFalse(g.hasMove(WHITE))
            val r = g.ranks()
            when {
                g.count(BLACK) > g.count(WHITE) -> assertArrayEquals(intArrayOf(1, 2), r)
                g.count(BLACK) < g.count(WHITE) -> assertArrayEquals(intArrayOf(2, 1), r)
                else -> assertArrayEquals(intArrayOf(1, 1), r)
            }
        }
    }
}
