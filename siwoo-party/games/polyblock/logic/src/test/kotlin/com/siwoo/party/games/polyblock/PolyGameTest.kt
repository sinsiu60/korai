package com.siwoo.party.games.polyblock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PolyGameTest {

    private fun id(name: String): Int = Pieces.ALL.first { it.name == name }.id

    /** 기본 모양(변환 0)의 모양 번호 */
    private fun o0(name: String): Int = Pieces.shape(id(name)).orientOf(0)

    /** 지금 차례 사람이 놓고 다음 차례로 */
    private fun put(g: PolyGame, name: String, x: Int, y: Int, t: Int = 0) {
        val s = Pieces.shape(id(name))
        assertTrue("$name 놓기 (${g.check(s.id, s.orientOf(t), x, y)})", g.place(s.id, s.orientOf(t), x, y))
        g.advance()
    }

    /** 모두 끝날 때까지 "큰 조각 먼저" 로 둔다 */
    private fun playOut(g: PolyGame): Int {
        var moves = 0
        while (!g.over) {
            val m = g.findMove(g.current)
            assertNotNull("차례인 사람은 둘 곳이 있어야 함", m)
            assertTrue(g.place(m!!.piece, m.orient, m.x, m.y))
            g.advance()
            moves++
        }
        return moves
    }

    // ── 조각 21개 ──

    @Test
    fun `조각은 21개이고 칸 수 합은 89`() {
        assertEquals(GameConfig.PIECE_COUNT, Pieces.ALL.size)
        assertEquals(GameConfig.TOTAL_CELLS, Pieces.ALL.sumOf { it.size })
        val bySize = IntArray(6)
        for (s in Pieces.ALL) bySize[s.size]++
        assertArrayEquals(intArrayOf(0, 1, 1, 2, 5, 12), bySize)
        // 21개가 모두 서로 다른 모양 (어떻게 돌리고 뒤집어도 겹치지 않음)
        val keys = HashSet<Long>()
        for (s in Pieces.ALL) for (o in s.orients) assertTrue("${s.name} 모양이 다른 조각과 같음", keys.add(o.key))
        // 이어진 모양인지 (칸끼리 변으로 연결)
        for (s in Pieces.ALL) {
            val o = s.orients[0]
            val seen = BooleanArray(o.size)
            val stack = ArrayList<Int>()
            seen[0] = true
            stack.add(0)
            while (stack.isNotEmpty()) {
                val a = stack.removeAt(stack.size - 1)
                for (b in 0 until o.size) if (!seen[b] && Math.abs(o.xs[a] - o.xs[b]) + Math.abs(o.ys[a] - o.ys[b]) == 1) {
                    seen[b] = true
                    stack.add(b)
                }
            }
            assertTrue("${s.name} 이어짐", seen.all { it })
        }
    }

    // ── 회전·뒤집기 ──

    @Test
    fun `조각별 서로 다른 방향 수`() {
        val expected = mapOf(
            "1" to 1, "2" to 2, "I3" to 2, "V3" to 4,
            "I4" to 2, "L4" to 8, "T4" to 4, "O4" to 1, "S4" to 4,
            "F" to 8, "I5" to 2, "L5" to 8, "N" to 8, "P" to 8, "T5" to 4,
            "U" to 4, "V5" to 4, "W" to 4, "X" to 1, "Y" to 8, "Z5" to 4,
        )
        for (s in Pieces.ALL) assertEquals(s.name, expected.getValue(s.name), s.orients.size)
        // 펜토미노 12개: 1·2·4·8 가지
        val pento = Pieces.ALL.filter { it.size == 5 }.map { it.orients.size }.sorted()
        assertEquals(listOf(1, 2, 4, 4, 4, 4, 4, 8, 8, 8, 8, 8), pento)
        assertEquals(91, Pieces.ALL.sumOf { it.orients.size })
    }

    @Test
    fun `네 번 돌리면 제자리이고 두 번 뒤집어도 제자리`() {
        for (t in 0 until 8) {
            var r = t
            repeat(4) { r = Pieces.rotate(r) }
            assertEquals(t, r)
            assertEquals(t, Pieces.flip(Pieces.flip(t)))
            assertTrue(Pieces.rotate(t) in 0..7)
            assertTrue(Pieces.flip(t) in 0..7)
        }
        // 회전·뒤집기로 8가지 변환이 모두 나온다
        val seen = HashSet<Int>()
        var t = 0
        repeat(4) { seen.add(t); seen.add(Pieces.flip(t)); t = Pieces.rotate(t) }
        assertEquals(8, seen.size)
    }

    /** 모양을 직접 돌리고 뒤집은 결과와 rotate/flip 변환이 같은 모양인지 */
    @Test
    fun `회전·뒤집기 버튼 변환이 실제 모양과 일치`() {
        fun key(xs: IntArray, ys: IntArray): Long {
            val mx = xs.minOrNull()!!
            val my = ys.minOrNull()!!
            var bits = 0L
            for (k in xs.indices) bits = bits or (1L shl ((ys[k] - my) * 5 + xs[k] - mx))
            return bits
        }
        for (s in Pieces.ALL) for (t in 0 until 8) {
            val o = s.orient(t)
            // 시계 방향 90°: (x, y) → (−y, x)
            val rx = IntArray(o.size) { -o.ys[it] }
            val ry = IntArray(o.size) { o.xs[it] }
            assertEquals("${s.name} t=$t 회전", key(rx, ry), s.orient(Pieces.rotate(t)).key)
            // 좌우 뒤집기: (x, y) → (−x, y)
            val fx = IntArray(o.size) { -o.xs[it] }
            assertEquals("${s.name} t=$t 뒤집기", key(fx, o.ys), s.orient(Pieces.flip(t)).key)
        }
        // 정사각형·십자는 돌려도 그대로, 막대는 두 번 돌리면 그대로
        val o4 = Pieces.shape(id("O4"))
        assertEquals(o4.orientOf(0), o4.orientOf(Pieces.rotate(0)))
        val i5 = Pieces.shape(id("I5"))
        assertEquals(5, i5.orient(0).w)
        assertEquals(1, i5.orient(Pieces.rotate(0)).w)
        assertEquals(5, i5.orient(Pieces.rotate(0)).h)
        assertEquals(i5.orientOf(0), i5.orientOf(Pieces.rotate(Pieces.rotate(0))))
    }

    // ── 판·모서리 ──

    @Test
    fun `인원별 판 크기와 시작 모서리`() {
        val g2 = PolyGame(2)
        assertEquals(14, g2.size)
        assertArrayEquals(intArrayOf(0, 13 * 14 + 13), g2.corners)
        val g3 = PolyGame(3)
        assertEquals(20, g3.size)
        assertArrayEquals(intArrayOf(0, 19, 19 * 20 + 19), g3.corners)
        val g4 = PolyGame(4)
        assertArrayEquals(intArrayOf(0, 19, 19 * 20 + 19, 19 * 20), g4.corners)
    }

    @Test
    fun `첫 조각은 자기 모서리 칸을 덮어야 함`() {
        val g = PolyGame(4)
        val l5 = id("L5")
        val o = o0("L5") // 세로 4칸 + 오른쪽 아래 1칸 (2×4)
        assertSame(Fit.NO_CORNER, g.check(l5, o, 1, 0))
        assertSame(Fit.NO_CORNER, g.check(l5, o, 5, 5))
        assertSame("남의 모서리(오른위)는 안 됨", Fit.NO_CORNER, g.check(l5, o, 18, 0))
        assertSame(Fit.OUTSIDE, g.check(l5, o, -1, 0))
        assertSame(Fit.OUTSIDE, g.check(l5, o, 19, 0))
        assertSame(Fit.OK, g.check(l5, o, 0, 0))
        put(g, "L5", 0, 0)
        assertEquals(1, g.current)
        // 1번(오른위): 자기 모서리 (19, 0) 을 덮어야
        val v3 = id("V3")
        assertSame(Fit.NO_CORNER, g.check(v3, o0("V3"), 17, 0))
        assertSame(Fit.OK, g.check(v3, Pieces.shape(v3).orientOf(Pieces.flip(0)), 18, 0)) // (19,0)(19,1)(18,1)
        // 2인: 마주 보는 모서리
        val g2 = PolyGame(2)
        assertSame(Fit.OK, g2.check(id("1"), 0, 0, 0))
        put(g2, "1", 0, 0)
        assertSame(Fit.NO_CORNER, g2.check(id("1"), 0, 13, 0))
        assertSame(Fit.OK, g2.check(id("1"), 0, 13, 13))
        assertTrue(g2.isFirstMove(1))
        assertFalse(g2.isFirstMove(0))
    }

    @Test
    fun `꼭짓점으로만 닿고 변으로 닿으면 안 됨`() {
        val g = PolyGame(2)
        put(g, "O4", 0, 0)        // 0번: (0,0)~(1,1)
        put(g, "O4", 12, 12)      // 1번: (12,12)~(13,13)
        val one = id("1")
        assertSame("겹침", Fit.OVERLAP, g.check(one, 0, 1, 1))
        assertSame("변으로 닿음", Fit.EDGE, g.check(one, 0, 2, 0))
        assertSame("변으로 닿음", Fit.EDGE, g.check(one, 0, 0, 2))
        assertSame("안 닿음", Fit.NO_TOUCH, g.check(one, 0, 3, 3))
        assertSame("꼭짓점만", Fit.OK, g.check(one, 0, 2, 2))
        g.board[g.index(3, 2)] = 1     // 남의 조각과는 변으로 닿아도 됨
        assertSame("남의 조각 옆", Fit.OK, g.check(one, 0, 2, 2))
        g.board[g.index(3, 2)] = PolyGame.EMPTY
        // 막대 I3 가 꼭짓점으로 닿지만 한 칸이 변으로 닿으면 안 됨: (2,1)~(2,3) 세로 → (2,1) 이 (1,1) 과 변으로 닿음
        val i3 = Pieces.shape(id("I3"))
        val vertical = i3.orientOf(Pieces.rotate(0))
        assertSame(Fit.EDGE, g.check(i3.id, vertical, 2, 1))
        assertSame(Fit.OK, g.check(i3.id, vertical, 2, 2))
        put(g, "I3", 2, 2, Pieces.rotate(0)) // 0번: (2,2)~(2,4)
        // 1번 조각은 0번 조각과 변으로 닿아도 된다 (자기 조각과 꼭짓점만 지키면)
        put(g, "I5", 7, 11)       // 1번: (7,11)~(11,11) — (11,11) 이 (12,12) 와 꼭짓점
        assertEquals(0, g.current)
        // 0번이 1번 조각 옆 (변으로 닿기) — 자기 조각과는 꼭짓점: (3,5) 는 (2,4) 와 꼭짓점
        assertSame(Fit.OK, g.check(one, 0, 3, 5))
        put(g, "1", 3, 5)
        // 1번: 0번 조각 바로 옆이어도 되지만 자기 조각과 꼭짓점이 없으면 안 됨
        assertSame(Fit.NO_TOUCH, g.check(one, 0, 4, 5))
        assertSame(Fit.OK, g.check(one, 0, 6, 10))
        put(g, "1", 6, 10)
        // 0번: 가로 2칸 (4,6)(5,6) 은 (3,5) 와 꼭짓점
        assertSame(Fit.OK, g.check(id("2"), 0, 4, 6))
        // 이미 쓴 조각은 다시 못 씀
        assertSame(Fit.UNAVAILABLE, g.check(one, 0, 4, 6))
        assertFalse(g.hasPiece(0, one))
    }

    @Test
    fun `놓은 칸·남은 칸·차례 넘기기`() {
        val g = PolyGame(3, first = 2)
        assertEquals(2, g.current)
        assertSame("십자는 모서리를 못 덮음", Fit.NO_CORNER, g.check(id("X"), 0, 17, 17))
        assertFalse(g.place(id("X"), 0, 17, 17))
        assertEquals(PolyGame.EMPTY, g.at(18, 18))
        assertTrue(g.place(id("1"), 0, 19, 19))
        assertEquals(2, g.at(19, 19))
        assertEquals(88, g.cellsLeft(2))
        assertEquals(20, g.piecesLeft(2))
        assertEquals(88, g.score(2))
        assertEquals(1, g.lastCount)
        assertEquals(2, g.lastPlayer)
        assertTrue(g.advance().isEmpty())
        assertEquals("2 다음은 0", 0, g.current)
    }

    // ── 자동 패스·종료 ──

    @Test
    fun `둘 곳이 없으면 자동 패스하고 다시 차례가 오지 않음`() {
        val g = PolyGame(2)
        put(g, "O4", 0, 0)             // 0번 (0,0)~(1,1): 꼭짓점 자리는 (2,2) 하나뿐
        val mark = BooleanArray(g.cells)
        assertEquals(1, g.anchors(0, mark))
        assertTrue(mark[g.index(2, 2)])
        assertTrue(g.place(id("O4"), 0, 12, 12)) // 1번
        g.board[g.index(2, 2)] = 1     // 1번 칸이 유일한 꼭짓점 자리를 막음 (판 상황 만들기)
        assertEquals(0, g.anchors(0, mark))
        assertFalse(g.hasMove(0))
        assertNull(g.findMove(0))
        assertFalse(g.canUse(0, id("1")))
        val passed = g.advance()
        assertEquals(listOf(0), passed)
        assertTrue(g.out[0])
        assertEquals("다시 1번 차례", 1, g.current)
        assertFalse(g.over)
        // 1번 혼자 끝까지 — 0번 차례는 다시 오지 않음
        while (!g.over) {
            assertEquals(1, g.current)
            val m = g.findMove(1)!!
            assertTrue(g.place(m.piece, m.orient, m.x, m.y))
            g.advance()
        }
        assertTrue(g.out[1])
        assertEquals(85, g.score(0))
        assertTrue(g.score(1) < 85)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test
    fun `모두 패스하면 판이 끝남 (2·3·4인)`() {
        for (n in 2..4) {
            val g = PolyGame(n, first = n - 1)
            val t0 = System.nanoTime()
            val moves = playOut(g)
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertTrue("${n}인 끝까지 두기가 빨라야 함 (${ms}ms)", ms < 3000)
            assertTrue(g.over)
            assertTrue(moves > n * 8)
            for (p in 0 until n) {
                assertTrue(g.out[p])
                assertFalse(g.hasMove(p))
                assertEquals(g.count(p), GameConfig.TOTAL_CELLS - g.cellsLeft(p))
            }
            assertTrue("끝난 뒤 advance 는 아무것도 안 함", g.advance().isEmpty())
            assertFalse(g.place(0, 0, 0, 0))
            assertSame(Fit.UNAVAILABLE, g.check(0, 0, 0, 0))
            // 순위 = 점수 오름차순
            val r = g.ranks()
            for (a in 0 until n) for (b in 0 until n) if (g.score(a) < g.score(b)) assertTrue(r[a] < r[b])
            assertEquals(1, r.minOrNull())
        }
    }

    // ── 점수 ──

    @Test
    fun `점수는 남은 칸 수, 같으면 같은 순위`() {
        val g = PolyGame(4)
        put(g, "I5", 0, 0)               // 0번 남은 84
        put(g, "2", 18, 0)               // 1번 남은 87
        put(g, "I3", 17, 19)             // 2번 남은 86
        put(g, "2", 0, 19)               // 3번 남은 87
        assertEquals(84, g.score(0))
        assertEquals(87, g.score(1))
        assertEquals(86, g.score(2))
        assertEquals(87, g.score(3))
        assertArrayEquals(intArrayOf(1, 3, 2, 3), g.ranks())
    }

    @Test
    fun `21개를 모두 놓으면 보너스로 −15점`() {
        // 4인 판(20×20)에서 0번 혼자 두기 (나머지는 이미 끝난 상태로)
        val g = PolyGame(4)
        for (p in 1..3) g.out[p] = true
        var moves = 0
        while (!g.over) {
            val m = g.findMove(0)!!
            assertTrue(g.place(m.piece, m.orient, m.x, m.y))
            moves++
            g.advance()
        }
        assertEquals(21, moves)
        assertTrue(g.allPlaced(0))
        assertEquals(0, g.cellsLeft(0))
        assertEquals(0, g.piecesLeft(0))
        assertTrue("다 놓으면 바로 끝", g.out[0])
        assertEquals(GameConfig.ALL_PLACED_SCORE, g.score(0))
        assertEquals(-15, g.score(0))
        assertEquals(89, g.score(1))
        assertArrayEquals(intArrayOf(1, 2, 2, 2), g.ranks())
    }
}
