package com.siwoo.party.games.snakeladder

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SnakeGameTest {

    private fun game(n: Int, map: Int = 0, exact: Boolean = false) = SnakeGame(n, SnakeRules(map, exact), Random(1))

    /** 여러 번 굴리기 */
    private fun SnakeGame.rolls(vararg dice: Int) {
        for (d in dice) rollWith(d)
    }

    // ── 판 좌표 (지그재그) ──

    @Test fun `1번은 왼쪽 아래 10번은 오른쪽 아래 11번은 그 위`() {
        assertEquals(0, Board.row(1)); assertEquals(0, Board.col(1))
        assertEquals(0, Board.row(10)); assertEquals(9, Board.col(10))
        assertEquals(1, Board.row(11)); assertEquals(9, Board.col(11))
        assertEquals(1, Board.row(20)); assertEquals(0, Board.col(20))
        assertEquals(2, Board.row(21)); assertEquals(0, Board.col(21))
        assertEquals(9, Board.row(100)); assertEquals(0, Board.col(100))
        assertEquals(9, Board.row(91)); assertEquals(9, Board.col(91))
    }

    @Test fun `칸 번호와 줄 열은 서로 되돌아온다`() {
        val seen = HashSet<Int>()
        for (s in 1..100) {
            val r = Board.row(s)
            val c = Board.col(s)
            assertTrue(r in 0..9 && c in 0..9)
            assertEquals(s, Board.square(r, c))
            assertTrue("한 칸에 하나", seen.add(r * 10 + c))
        }
        for (r in 0..9) for (c in 0..9) {
            val s = Board.square(r, c)
            assertEquals(r, Board.row(s)); assertEquals(c, Board.col(s))
        }
    }

    @Test fun `이웃한 번호는 판에서도 붙어 있다`() {
        for (s in 1 until 100) {
            val d = Math.abs(Board.row(s) - Board.row(s + 1)) + Math.abs(Board.col(s) - Board.col(s + 1))
            assertEquals("$s 와 ${s + 1}", 1, d)
        }
    }

    // ── 맵 데이터 ──

    @Test fun `기본 맵 3종은 모두 올바르다`() {
        assertEquals(3, SnakeMaps.ALL.size)
        for (m in SnakeMaps.ALL) {
            assertEquals(m.name, emptyList<String>(), SnakeMaps.validate(m))
            assertEquals(8, m.ladders.size)
            assertEquals(8, m.snakes.size)
            for (l in m.ladders) assertTrue("${m.name} 사다리 위로", l.to > l.from && l.isLadder)
            for (s in m.snakes) assertTrue("${m.name} 뱀 아래로", s.to < s.from && !s.isLadder)
            val ends = m.all.flatMap { listOf(it.from, it.to) }
            assertEquals("${m.name} 끝점 32개 모두 다른 칸", 32, ends.toSet().size)
            assertFalse(1 in ends); assertFalse(100 in ends)
            // 사다리이면서 뱀인 칸 없음, 끝점에서 다시 타는 연쇄 없음
            for (x in m.all) assertNull("${m.name} ${x.to} 연쇄", m.jumpAt(x.to))
        }
        assertEquals(3, SnakeMaps.ALL.map { it.name }.toSet().size)
    }

    @Test fun `검증은 잘못된 맵을 잡아낸다`() {
        val good = SnakeMaps.CLASSIC
        fun bad(ladders: List<Jump>, snakes: List<Jump>) = SnakeMaps.validate(SnakeMap("x", ladders, snakes))
        // 개수
        assertTrue(bad(good.ladders.drop(1), good.snakes).isNotEmpty())
        assertTrue(bad(good.ladders, good.snakes + Jump(90, 41)).isNotEmpty())
        // 거꾸로 가는 사다리·뱀
        assertTrue(bad(good.ladders.drop(1) + Jump(30, 3), good.snakes).isNotEmpty())
        assertTrue(bad(good.ladders, good.snakes.drop(1) + Jump(6, 17)).isNotEmpty())
        // 같은 줄
        assertTrue(bad(good.ladders.drop(1) + Jump(2, 9), good.snakes).isNotEmpty())
        // 출발·도착 칸
        assertTrue(bad(good.ladders.drop(1) + Jump(1, 22), good.snakes).isNotEmpty())
        assertTrue(bad(good.ladders, good.snakes.drop(1) + Jump(100, 60)).isNotEmpty())
        // 겹침: 사다리 끝 = 뱀 머리 (연쇄), 같은 칸이 사다리 시작이자 뱀 머리
        assertTrue(bad(good.ladders, good.snakes.drop(1) + Jump(22, 5)).isNotEmpty())
        assertTrue(bad(good.ladders, good.snakes.drop(1) + Jump(36, 5)).isNotEmpty())
    }

    // ── 이동 ──

    @Test fun `출발점에서 굴린 만큼 한 칸씩`() {
        val g = game(2)
        val m = g.rollWith(2)
        assertEquals(0, m.from)
        assertArrayEquals(intArrayOf(1, 2), m.walk)
        assertEquals(2, m.landed); assertEquals(2, m.to)
        assertTrue(m.jumps.isEmpty())
        assertEquals(2, g.position(0))
        assertEquals(1, g.current)
    }

    @Test fun `사다리는 위로 뱀은 아래로`() {
        val g = game(2)
        val up = g.rollWith(3) // 3 → 사다리 22
        assertEquals(3, up.landed)
        assertEquals(listOf(Jump(3, 22)), up.jumps)
        assertEquals(22, up.to)
        assertEquals(22, g.position(0))
        g.rollWith(5) // 1번: 5
        val down = g.rollWith(6) // 0번: 22 → 28 → 사다리 55 (6 이라 한 번 더)
        assertEquals(55, down.to)
        assertTrue(down.extraRoll)
        g.rollWith(4) // 59
        g.rollWith(3) // 1번: 5 → 8 → 사다리 30
        assertEquals(30, g.position(1))
        g.rollWith(3) // 0번: 59 → 62 → 뱀 19
        assertEquals(19, g.position(0))
        g.rollWith(4) // 1번: 30 → 34 → 뱀 12
        assertEquals(12, g.position(1))
    }

    @Test fun `연쇄는 끝까지 따라간다`() {
        // 검증된 맵에는 없지만 규칙은 연쇄를 이어서 탄다: 5 → 40 → 13 → 60
        val m = SnakeMap("연쇄", listOf(Jump(5, 40), Jump(13, 60)), listOf(Jump(40, 13)))
        val jumps = SnakeMoves.resolve(m, 5)
        assertEquals(listOf(Jump(5, 40), Jump(40, 13), Jump(13, 60)), jumps)
        assertTrue(SnakeMoves.resolve(m, 6).isEmpty())
        // 고리여도 멈춤
        val loop = SnakeMap("고리", listOf(Jump(5, 40)), listOf(Jump(40, 5)))
        assertEquals(GameConfig.MAX_CHAIN, SnakeMoves.resolve(loop, 5).size)
    }

    @Test fun `6이 나오면 한 번 더`() {
        val g = game(3)
        val a = g.rollWith(6)
        assertTrue(a.extraRoll); assertFalse(a.turnEnded)
        assertEquals(0, g.current)
        assertEquals(1, g.rollsThisTurn)
        val b = g.rollWith(6)
        assertTrue(b.extraRoll)
        assertEquals(0, g.current)
        val c = g.rollWith(2)
        assertFalse(c.extraRoll); assertTrue(c.turnEnded)
        assertEquals(1, g.current)
        assertEquals(0, g.rollsThisTurn)
        assertEquals(14, g.position(0))
    }

    @Test fun `기본 규칙 - 100을 넘어도 도착`() {
        val walk = SnakeMoves.walk(97, 5, exact = false)
        assertArrayEquals(intArrayOf(98, 99, 100), walk)
        val g = game(2)
        g.rolls(3, 1, 6, 3, 1, 3, 1) // 0번: 22 → 55 → 77 → 99, 1번: 1, 2, 3→22
        assertEquals(99, g.position(0))
        val m = g.rollWith(6) // 99 + 6 → 100 도착 (6 이어도 한 번 더 없음)
        assertTrue(m.finished)
        assertFalse(m.bounced)
        assertFalse(m.extraRoll)
        assertEquals(100, m.to)
        assertEquals(1, m.finishRank)
        assertTrue(m.gameOver)
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }

    @Test fun `정확히 100 규칙 - 넘치면 남은 눈만큼 되돌아감`() {
        assertArrayEquals(intArrayOf(98, 99, 100, 99, 98), SnakeMoves.walk(97, 5, exact = true))
        assertArrayEquals(intArrayOf(100, 99, 98, 97, 96, 95), SnakeMoves.walk(99, 6, exact = true))
        assertArrayEquals(intArrayOf(98, 99, 100), SnakeMoves.walk(97, 3, exact = true))
        val g = game(2, map = 0, exact = true)
        g.rolls(3, 1, 6, 3, 1, 3, 1) // 0번 99, 1번 22
        assertEquals(99, g.position(0))
        g.rollWith(2) // 0번: 99 → 100 → 99 되돌아옴
        assertEquals(99, g.position(0))
        g.rollWith(1) // 1번 23
        val m = g.rollWith(4) // 99 → 100, 99, 98, 97
        assertTrue(m.bounced)
        assertFalse(m.finished)
        assertEquals(97, m.to)
        g.rollWith(1)
        val m2 = g.rollWith(1) // 97 → 98 뱀 → 79
        assertEquals(79, m2.to)
        // 되돌아와 뱀 머리에 멈추면 뱀도 탄다
        val g2 = game(2, map = 0, exact = true)
        g2.rolls(3, 1, 6, 3, 1, 3, 1) // 0번 99
        val m3 = g2.rollWith(3) // 99 → 100, 99, 98 → 뱀 79
        assertTrue(m3.bounced)
        assertEquals(98, m3.landed)
        assertEquals(listOf(Jump(98, 79)), m3.jumps)
        assertEquals(79, g2.position(0))
    }

    @Test fun `정확히 100 규칙 - 딱 맞으면 도착`() {
        val g = game(2, map = 0, exact = true)
        g.rolls(3, 1, 6, 3, 1, 3, 1)
        val m = g.rollWith(1)
        assertTrue(m.finished)
        assertFalse(m.bounced)
        assertEquals(1, g.finishRank(0))
    }

    // ── 순위 ──

    @Test fun `첫 도착이 나온 바퀴까지만 하고 도착 순서 다음 위치 순`() {
        // 4인: 2번이 먼저, 같은 바퀴에 3번도 도착, 0·1번은 같은 칸 → 같은 순위
        val g = game(4)
        g.rolls(1, 1, 3, 3) // 1, 1, 22, 22
        g.rolls(1, 1, 6, 3, 6, 3) // 2, 2, 77, 77
        g.rolls(1, 1, 3, 3) // 3→22, 3→22, 99, 99
        assertEquals(4, g.round)
        g.rolls(1, 1)
        val a = g.rollWith(1)
        assertTrue(a.finished); assertEquals(1, a.finishRank)
        assertFalse("바퀴가 끝날 때까지 계속", a.gameOver)
        assertTrue(g.lastRound)
        assertEquals(3, g.current)
        val b = g.rollWith(1)
        assertTrue(b.finished); assertEquals(2, b.finishRank)
        assertTrue(b.gameOver)
        assertArrayEquals(intArrayOf(3, 3, 1, 2), g.ranks())
    }

    @Test fun `도착한 사람 뒤 순서는 그 바퀴에 한 번 더 하고 앞 순서는 하지 않는다`() {
        val g = game(4)
        g.rolls(1, 3, 2, 1) // 1, 22, 2, 1
        g.rolls(1, 6, 3, 2, 1) // 2, 77, 4, 2
        g.rolls(1, 3, 2, 1) // 3→22, 99, 6, 3→22
        g.rollWith(4) // 0번 26
        val f = g.rollWith(1) // 1번 도착
        assertTrue(f.finished)
        assertFalse(f.gameOver)
        assertEquals(2, g.current)
        g.rollWith(2) // 2번 8 → 30
        val last = g.rollWith(2) // 3번 24 → 바퀴 끝 → 게임 끝 (0번은 더 못 함)
        assertTrue(last.gameOver)
        assertTrue(g.over)
        // 1번 1위, 위치: 2번 30 > 0번 26 > 3번 24
        assertArrayEquals(intArrayOf(3, 1, 2, 4), g.ranks())
    }

    @Test fun `도착하지 않은 사람이 하나 남으면 바로 끝`() {
        val g = game(3)
        g.rolls(3, 3, 1) // 22, 22, 1
        g.rolls(6, 3, 6, 3, 1) // 77, 77, 2
        g.rolls(3, 3, 1) // 99, 99, 3→22
        val a = g.rollWith(1)
        assertTrue(a.finished); assertFalse(a.gameOver)
        val b = g.rollWith(1)
        assertTrue(b.finished); assertTrue(b.gameOver)
        assertArrayEquals(intArrayOf(1, 2, 3), g.ranks())
    }

    @Test fun `도착한 사람은 차례를 건너뛴다`() {
        // 2인은 한 명이 도착하면 끝나므로 3인: 0번 도착 뒤 1, 2번만 바퀴 끝까지 → 끝
        val g = game(3)
        g.rolls(3, 1, 1) // 22, 1, 1
        g.rolls(6, 3, 1, 1) // 77, 2, 2
        g.rolls(3, 1, 1) // 99, 3→22, 3→22
        val f = g.rollWith(1)
        assertTrue(f.finished)
        assertEquals(1, g.current)
        g.rollWith(2) // 1번 24
        val e = g.rollWith(1) // 2번 23
        assertTrue(e.gameOver)
        assertArrayEquals(intArrayOf(1, 2, 3), g.ranks())
    }

    @Test fun `무작위 판은 언젠가 끝나고 순위는 1부터`() {
        for (seed in 0 until 40) for (n in 2..4) for (exact in listOf(false, true)) for (map in 0..2) {
            val g = SnakeGame(n, SnakeRules(map, exact), Random(seed))
            var guard = 0
            while (!g.over) {
                val m = g.roll()
                assertTrue(m.to in 1..100)
                guard++
                assertTrue("너무 오래 걸림", guard < 5000)
            }
            val r = g.ranks()
            assertEquals(1, r.min())
            assertTrue(g.finishOrder.isNotEmpty())
            assertEquals(1, r[g.finishOrder[0]])
            for (p in 0 until n) if (!g.isFinished(p)) for (q in g.finishOrder) assertTrue(r[q] < r[p])
        }
    }

    @Test fun `게임 끝나면 더 굴릴 수 없다`() {
        val g = game(2)
        g.rolls(3, 1, 6, 3, 1, 3, 1, 1)
        assertTrue(g.over)
        try {
            g.rollWith(1)
            error("굴려지면 안 됨")
        } catch (e: IllegalStateException) {
            // 정상
        }
    }
}
