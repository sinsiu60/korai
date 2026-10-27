package com.siwoo.party.games.bingo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BingoGameTest {

    /** 1..25 를 칸 순서대로 (칸 c = 숫자 c+1) */
    private val straight = IntArray(25) { it + 1 }

    /** 흩어진 배치 (칸 c = (c×7 mod 25)+1) — 작은 숫자들로는 줄이 잘 안 생김 */
    private val scatter = IntArray(25) { (it * 7) % 25 + 1 }

    /** 열 우선 배치: 숫자 n 은 (n-1)%5 행, (n-1)/5 열 → 1..5 가 첫 세로 줄 */
    private val transposed = IntArray(25) { c -> (c % 5) * 5 + c / 5 + 1 }

    private fun board(nums: IntArray = straight) = BingoBoard().apply { setNumbers(nums) }

    /** 칸 번호들 표시 (straight 판 기준: 칸 c 의 숫자 = c+1) */
    private fun BingoBoard.markCells(vararg cells: Int) { for (c in cells) assertTrue(mark(numberAt(c))) }

    /** 모든 판을 채우고 부르기 단계로 */
    private fun ready(n: Int, seed: Int = 1, layouts: List<IntArray>? = null): BingoGame {
        val g = BingoGame(n, Random(seed))
        repeat(n) { p ->
            assertEquals(p, g.setupPlayer)
            if (layouts != null) g.boards[p].setNumbers(layouts[p]) else g.randomFill()
            assertTrue(g.finishSetup())
        }
        assertEquals(Phase.PLAY, g.phase)
        assertEquals(g.starter, g.current)
        return g
    }

    // ── 줄 판정 ──

    @Test fun `줄은 가로 5 세로 5 대각선 2 해서 12개`() {
        assertEquals(12, BingoLines.LINES.size)
        assertEquals(GameConfig.LINE_COUNT, BingoLines.LINES.size)
        assertArrayEquals(intArrayOf(0, 6, 12, 18, 24), BingoLines.LINES[10])
        assertArrayEquals(intArrayOf(4, 8, 12, 16, 20), BingoLines.LINES[11])
    }

    @Test fun `가로 한 줄`() {
        for (r in 0 until 5) {
            val b = board()
            b.markCells(*IntArray(4) { r * 5 + it })
            assertEquals("4칸은 아직", 0, b.lines)
            b.markCells(r * 5 + 4)
            assertEquals(1, b.lines)
            assertTrue(b.lineDone(r))
        }
    }

    @Test fun `세로 한 줄`() {
        for (c in 0 until 5) {
            val b = board()
            b.markCells(*IntArray(5) { it * 5 + c })
            assertEquals(1, b.lines)
            assertTrue(b.lineDone(5 + c))
            assertFalse(b.lineDone(c))
        }
    }

    @Test fun `대각선 두 개`() {
        val b = board()
        b.markCells(0, 6, 12, 18, 24)
        assertEquals(1, b.lines)
        assertTrue(b.lineDone(10))
        val d = board()
        d.markCells(4, 8, 12, 16, 20)
        assertEquals(1, d.lines)
        assertTrue(d.lineDone(11))
        assertFalse(d.lineDone(10))
    }

    @Test fun `여러 줄을 함께 센다`() {
        val b = board()
        b.markCells(0, 1, 2, 3, 4) // 가로 1
        b.markCells(5, 10, 15, 20) // 세로 1 (0 은 이미)
        assertEquals(2, b.lines)
        b.markCells(6, 12, 18, 24) // 대각선 ↘
        assertEquals(3, b.lines)
        // 칸 하나로 두 줄이 동시에 완성: 가로 2번째 줄(5..9)과 세로 4번째 줄(3,8,13,18,23)이 8 만 남은 상태
        val c = board()
        c.markCells(5, 6, 7, 9, 3, 13, 18, 23)
        assertEquals(0, c.lines)
        c.markCells(8)
        assertEquals(2, c.lines)
        // 다 표시하면 12줄
        val all = board()
        all.markCells(*IntArray(25) { it })
        assertEquals(12, all.lines)
    }

    @Test fun `가운데 칸은 4줄에 걸려 있다`() {
        val b = board()
        // 가운데(12)를 뺀 가로 3번째·세로 3번째·두 대각선
        val cells = intArrayOf(10, 11, 13, 14, 2, 7, 17, 22, 0, 6, 18, 24, 4, 8, 16, 20)
        b.markCells(*cells)
        assertEquals(0, b.lines)
        b.markCells(12)
        assertEquals("가운데 하나로 4줄", 4, b.lines)
        assertEquals(4, BingoLines.LINES.count { 12 in it })
    }

    // ── 판 채우기 ──

    @Test fun `칸을 누르면 1부터 차례로 들어가고 마지막 것을 뺄 수 있다`() {
        val b = BingoBoard()
        assertTrue(b.place(12))
        assertTrue(b.place(0))
        assertFalse("이미 찬 칸", b.place(12))
        assertEquals(1, b.numberAt(12))
        assertEquals(2, b.numberAt(0))
        assertEquals(3, b.nextNumber)
        assertEquals(0, b.undoLast())
        assertEquals(0, b.numberAt(0))
        assertEquals(2, b.nextNumber)
        assertEquals(-1, b.cellOf(2))
        b.clear()
        assertTrue(b.isEmpty)
        assertEquals(-1, b.undoLast())
        for (c in 24 downTo 0) assertTrue(b.place(c))
        assertTrue(b.isComplete)
        assertFalse("다 찼으면 더 못 넣음", b.place(3))
        assertEquals(25, b.numberAt(0))
    }

    private fun assertPermutation(b: BingoBoard) {
        assertTrue(b.isComplete)
        assertEquals((1..25).toList(), b.numbers().sorted())
        for (n in 1..25) assertEquals(n, b.numberAt(b.cellOf(n)))
    }

    @Test fun `랜덤 배치는 1~25 의 순열`() {
        val rnd = Random(7)
        val seen = HashSet<String>()
        repeat(200) {
            val b = BingoBoard()
            b.fillRandom(rnd)
            assertPermutation(b)
            seen.add(b.numbers().joinToString(","))
        }
        assertTrue("매번 다른 배치", seen.size > 190)
        // 다 찬 판에서 다시 누르면 전체를 새로 섞음
        val b = BingoBoard()
        b.fillRandom(rnd)
        val first = b.numbers()
        b.fillRandom(rnd)
        assertPermutation(b)
        assertFalse(first.contentEquals(b.numbers()))
    }

    @Test fun `일부만 채운 판은 빈칸만 남은 숫자로 채운다`() {
        val b = BingoBoard()
        b.place(12) // 1 은 가운데
        b.place(0) // 2 는 왼쪽 위
        b.fillRandom(Random(3))
        assertPermutation(b)
        assertEquals(1, b.numberAt(12))
        assertEquals(2, b.numberAt(0))
    }

    @Test fun `다 채워야 다음 사람으로, 마지막 사람 뒤엔 부르기`() {
        val g = BingoGame(3, Random(2))
        assertEquals(Phase.SETUP, g.phase)
        assertFalse("빈 판으로는 못 끝냄", g.finishSetup())
        g.boards[0].place(3)
        assertFalse(g.finishSetup())
        assertNull("판 채우는 중엔 부를 수 없음", g.call(1))
        g.randomFill()
        assertTrue(g.finishSetup())
        assertEquals(1, g.setupPlayer)
        g.randomFill(); assertTrue(g.finishSetup())
        g.randomFill(); assertTrue(g.finishSetup())
        assertEquals(Phase.PLAY, g.phase)
        for (b in g.boards) assertPermutation(b)
    }

    // ── 부르기 ──

    @Test fun `부른 숫자는 모든 판에 표시된다`() {
        val g = ready(4, seed = 5)
        val r = g.call(17)!!
        assertEquals(17, r.number)
        for (b in g.boards) {
            val c = b.cellOf(17)
            assertTrue(b.isMarked(c))
            assertEquals(1, b.marked.count { it })
        }
        assertEquals(17, g.lastCall)
        assertEquals(1, g.callCount)
        assertTrue(g.isCalled(17))
    }

    @Test fun `이미 부른 숫자는 다시 부를 수 없다`() {
        val g = ready(2)
        val first = g.current
        assertNotNull(g.call(9))
        val second = g.current
        assertEquals((first + 1) % 2, second)
        assertFalse(g.canCall(9))
        assertNull(g.call(9))
        assertEquals("차례도 그대로", second, g.current)
        assertEquals(1, g.callCount)
        assertFalse(g.canCall(0))
        assertFalse(g.canCall(26))
        assertNull(g.call(26))
    }

    @Test fun `차례는 starter 부터 돌아가며`() {
        val g = ready(3, seed = 9)
        val s = g.starter
        val callers = (1..6).map { g.call(it)!!.caller }
        assertEquals(listOf(s, (s + 1) % 3, (s + 2) % 3, s, (s + 1) % 3, (s + 2) % 3), callers)
    }

    @Test fun `3줄 먼저 완성하면 그 즉시 끝, 혼자 1위`() {
        val g = ready(2, layouts = listOf(straight, scatter))
        // 0번 판: 가로 1·2 줄(1..10) + 세로 첫 줄(1,6,11,16,21) → 21 에서 3줄. 1번 판(흩어진 배치)은 1줄뿐
        val calls = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 16)
        for (n in calls) {
            val r = g.call(n)!!
            assertFalse(r.gameOver)
        }
        assertEquals(2, g.lines(0))
        val r = g.call(21)!!
        assertTrue(r.gameOver)
        assertEquals(Phase.OVER, g.phase)
        assertEquals(listOf(0), r.newBingo)
        assertEquals(listOf(0), g.winners)
        assertEquals(3, g.lines(0))
        assertNull("끝난 뒤엔 부르기 없음", g.call(22))
        val ranks = g.ranks()
        assertEquals(1, g.lines(1))
        assertEquals(1, ranks[0])
        assertEquals(2, ranks[1])
    }

    @Test fun `동시에 빙고면 함께 1위 (부른 사람 우선 없음)`() {
        // 두 판이 같은 배치 → 같은 부르기로 함께 3줄
        val g = ready(3, layouts = listOf(straight, straight, scatter))
        val caller = g.current
        val seq = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
        for (n in seq) assertFalse(g.call(n)!!.gameOver)
        assertEquals(2, g.lines(0))
        assertEquals(2, g.lines(1))
        val r = g.call(15)!!
        assertTrue(r.gameOver)
        assertEquals(listOf(0, 1), r.newBingo)
        assertEquals(listOf(0, 1), g.winners)
        val ranks = g.ranks()
        assertEquals(1, ranks[0])
        assertEquals(1, ranks[1])
        assertEquals(3, ranks[2])
        assertTrue(caller in 0..2)
    }

    @Test fun `한 번에 3줄을 넘어 4줄이 돼도 다른 빙고와 같은 1위`() {
        val g = ready(2, layouts = listOf(straight, straight))
        // 0·1번 판 같은 배치 → 가운데(13)로 4줄 동시
        val pre = intArrayOf(11, 12, 14, 15, 3, 8, 18, 23, 1, 7, 19, 25, 5, 9, 17, 21)
        for (n in pre) assertFalse(g.call(n)!!.gameOver)
        val r = g.call(13)!!
        assertTrue(r.gameOver)
        assertEquals(4, r.linesAfter[0])
        assertEquals(4, r.gained(0))
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    @Test fun `나머지는 줄 수 순, 같으면 같은 순위`() {
        val g = ready(4, layouts = listOf(straight, straight.copyOf(), scatter, transposed))
        // 1..10 → 판0·1: 가로 2줄 / 판2(흩어짐): 0줄 / 판3(열 우선): 세로 2줄
        for (n in 1..10) assertNotNull(g.call(n))
        assertEquals(2, g.lines(0))
        assertEquals(2, g.lines(1))
        assertEquals(0, g.lines(2))
        assertEquals(2, g.lines(3))
        assertArrayEquals("진행 중 순위", intArrayOf(1, 1, 4, 1), g.ranks())
        // 21..25: 판0·1 맨 아래 가로, 판3 마지막 세로 → 21 에서 셋이 동시에 빙고
        for (n in intArrayOf(25, 24, 23, 22)) assertFalse(g.call(n)!!.gameOver)
        val r = g.call(21)!!
        assertTrue(r.gameOver)
        assertEquals(listOf(0, 1, 3), r.winners)
        assertEquals(0, g.lines(2))
        assertArrayEquals(intArrayOf(1, 1, 4, 1), g.ranks())
    }

    @Test fun `빙고 없는 사람끼리는 줄 수 경쟁 순위`() {
        val g = ready(4, layouts = listOf(straight, scatter, transposed, IntArray(25) { 25 - it }))
        // 1..5: 판0 가로 1줄, 판2 세로 1줄, 판3(거꾸로) 맨 아래 가로 1줄, 판1 0줄
        for (n in 1..5) g.call(n)
        assertArrayEquals(intArrayOf(1, 4, 1, 1), g.ranks())
        // 6..15: 판0 가로 3줄 → 빙고. 판2 세로 3줄, 판3 아래 3줄도 동시 → 판1 만 4위
        for (n in 6..14) assertFalse(g.call(n)!!.gameOver)
        assertTrue(g.call(15)!!.gameOver)
        assertEquals(listOf(0, 2, 3), g.winners)
        assertArrayEquals(intArrayOf(1, 4, 1, 1), g.ranks())
    }

    @Test fun `줄 수 순위 - 같은 줄 수는 같은 순위, 그 다음은 건너뜀`() {
        val g = ready(3, layouts = listOf(straight, transposed, scatter))
        // 1,2,3,4,5 → 판0 가로 1줄, 판1 세로 1줄 / 6,7,8,9,10 → 판0 2줄, 판1 2줄
        for (n in 1..10) g.call(n)
        // 11,12 → 판0 셋째 가로·판1 셋째 세로 일부 (아직 2줄)
        g.call(11); g.call(12)
        assertEquals(2, g.lines(0))
        assertEquals(2, g.lines(1))
        assertEquals(0, g.lines(2))
        assertArrayEquals(intArrayOf(1, 1, 3), g.ranks())
    }

    @Test fun `무작위 게임은 언제나 빙고로 끝나고 1위가 있다`() {
        repeat(100) { seed ->
            val n = 2 + seed % 3
            val g = ready(n, seed = seed)
            val rnd = Random(seed * 31 + 1)
            while (g.phase == Phase.PLAY) {
                val left = (1..25).filter { g.canCall(it) }
                assertNotNull(g.call(left[rnd.nextInt(left.size)]))
            }
            assertTrue(g.winners.isNotEmpty())
            val ranks = g.ranks()
            for (w in g.winners) assertEquals(1, ranks[w])
            for (p in 0 until n) if (p !in g.winners) {
                assertTrue(g.lines(p) < GameConfig.WIN_LINES)
                assertTrue(ranks[p] > g.winners.size)
            }
            assertTrue(g.callCount < 25)
        }
    }
}
