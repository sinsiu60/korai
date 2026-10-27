package com.siwoo.party.games.flood

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class FloodGameTest {

    /** 줄마다 색 숫자 (예: "0112") → 격자 */
    private fun grid(vararg rows: String): IntArray {
        val n = rows.size
        val out = IntArray(n * n)
        for (r in 0 until n) {
            assertEquals(n, rows[r].length)
            for (c in 0 until n) out[r * n + c] = rows[r][c] - '0'
        }
        return out
    }

    private fun game(players: Int, vararg rows: String, first: Int = 0, colors: Int = 6, rounds: Int = 20) =
        FloodGame(players, grid(*rows), first = first, size = rows.size, colorCount = colors, maxRounds = rounds)

    /** 주인 지도 ('.' = 없음, 숫자 = 사람) */
    private fun owners(g: FloodGame): String {
        val sb = StringBuilder()
        for (r in 0 until g.size) {
            if (r > 0) sb.append('/')
            for (c in 0 until g.size) {
                val o = g.ownerAt(r, c)
                sb.append(if (o < 0) '.' else ('0' + o))
            }
        }
        return sb.toString()
    }

    // ── 모서리·시작 땅 ──

    @Test
    fun `인원별 모서리 배치`() {
        assertArrayEquals(intArrayOf(0, 195), FloodGame.corners(2, 14))
        assertArrayEquals(intArrayOf(0, 13, 195), FloodGame.corners(3, 14))
        assertArrayEquals(intArrayOf(0, 13, 195, 182), FloodGame.corners(4, 14))
    }

    @Test
    fun `시작 땅은 모서리와 이어진 같은 색 칸 전부`() {
        val g = game(
            2,
            "0012",
            "0312",
            "1132",
            "4455",
        )
        // 0번: (0,0)(0,1)(1,0) / 1번: (3,3)(3,2) — 대각선은 이어지지 않음
        assertEquals("00../0.../..../..11", owners(g))
        assertEquals(3, g.territory(0))
        assertEquals(2, g.territory(1))
        assertEquals(0, g.colorOf(0))
        assertEquals(5, g.colorOf(1))
        assertEquals(5, g.ownedCount)
    }

    @Test
    fun `모서리 색이 겹치는 판은 만들 수 없음`() {
        try {
            game(2, "01", "20")
            fail("겹치는 모서리")
        } catch (e: IllegalArgumentException) {
            // 기대한 대로
        }
    }

    @Test
    fun `무작위 판은 모서리 색이 사람마다 다름`() {
        for (players in 2..4) {
            for (seed in 0 until 300) {
                val colors = FloodGame.randomColors(players, Random(seed))
                assertEquals(196, colors.size)
                assertTrue(colors.all { it in 0 until 6 })
                val homes = FloodGame.corners(players, 14)
                val set = homes.map { colors[it] }.toSet()
                assertEquals("seed $seed", players, set.size)
                // 게임도 문제없이 만들어지고 시작 땅이 겹치지 않음
                val g = FloodGame(players, colors)
                for (p in 0 until players) {
                    assertEquals(p, g.ownerAt(homes[p]))
                    assertTrue(g.territory(p) >= 1)
                }
                assertEquals((0 until 196).count { g.ownerAt(it) >= 0 }, g.ownedCount)
            }
        }
    }

    @Test
    fun `같은 seed 면 같은 판, 다른 seed 면 다른 판`() {
        val a = FloodGame.randomColors(4, Random(42))
        val b = FloodGame.randomColors(4, Random(42))
        val c = FloodGame.randomColors(4, Random(43))
        assertArrayEquals(a, b)
        assertFalse(a.contentEquals(c))
        val g1 = FloodGame.create(3, Random(7))
        val g2 = FloodGame.create(3, Random(7))
        assertEquals(g1.first, g2.first)
        for (cell in 0 until g1.cellCount) assertEquals(g1.colorAt(cell), g2.colorAt(cell))
    }

    @Test
    fun `모서리 색 겹침은 안 쓴 색으로 다시 뽑음`() {
        // 모든 칸이 0 이 나오는 random → 모서리만 고쳐져야 함
        val zero = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
        val colors = FloodGame.randomColors(4, zero)
        val homes = FloodGame.corners(4, 14)
        assertEquals(0, colors[homes[0]])
        assertEquals(4, homes.map { colors[it] }.toSet().size)
        for (cell in colors.indices) if (cell !in homes) assertEquals(0, colors[cell])
    }

    // ── 선택 불가 색 ──

    @Test
    fun `내 지금 색과 다른 사람의 지금 색은 고를 수 없음`() {
        val g = game(
            3,
            "0123",
            "4512",
            "3451",
            "1232",
        )
        // 0번=0, 1번=3(오른쪽 위), 2번=2(오른쪽 아래)
        assertEquals(0, g.colorOf(0))
        assertEquals(3, g.colorOf(1))
        assertEquals(2, g.colorOf(2))
        assertEquals(listOf(1, 4, 5), g.allowedColors(0))
        assertFalse(g.isAllowed(0, 0))
        assertFalse(g.isAllowed(0, 3))
        assertFalse(g.isAllowed(0, 2))
        assertFalse(g.isAllowed(0, 6))
        assertFalse(g.isAllowed(0, -1))
        assertEquals(0, g.holderOf(0))
        assertEquals(1, g.holderOf(3))
        assertEquals(-1, g.holderOf(4))
        assertEquals(Pick.OWN_COLOR, g.choose(0))
        assertEquals(Pick.TAKEN, g.choose(3))
        assertEquals(Pick.TAKEN, g.choose(2))
        assertEquals(Pick.BAD_COLOR, g.choose(9))
        assertEquals("거절되면 차례 그대로", 0, g.current)
        assertEquals(0, g.moves)
        assertNull(g.lastFlood)
        assertEquals(Pick.OK, g.choose(1))
        assertEquals(1, g.colorOf(0))
        // 이제 1번 차례: 1번 자기 색 3, 0번 새 색 1, 2번 색 2 는 못 고름
        assertEquals(1, g.current)
        assertEquals(listOf(0, 4, 5), g.allowedColors(1))
        assertEquals(Pick.TAKEN, g.choose(1))
    }

    // ── 흡수 범위 ──

    @Test
    fun `흡수는 이어진 주인 없는 같은 색 칸만, 여러 단계로 이어짐`() {
        val g = game(
            2,
            "01115",
            "02215",
            "11315",
            "14115",
            "44443",
        )
        // 0번 땅 = (0,0),(1,0) 색 0 / 1번 = (4,4) 색 3
        assertEquals(2, g.territory(0))
        assertEquals(1, g.territory(1))
        assertEquals(10, g.gainIf(0, 1))
        assertEquals("미리 세기는 판을 바꾸지 않음", 2, g.territory(0))
        assertArrayEquals(intArrayOf(1, 10, 2, 15, 11, 3, 8, 13, 18, 17), g.gainCells(0, 1))
        assertEquals(0, g.gainCells(0, 3).size)
        assertEquals(-1, g.ownerAt(1))
        assertEquals(Pick.OK, g.choose(1))
        val f = g.lastFlood!!
        // 1 색 줄이 꼬불꼬불 이어져 (3,2) 까지 흡수, 2·3·4 색 칸은 그대로
        assertEquals("0000./0..0./00.0./0.00./....1", owners(g))
        assertEquals(10, f.gain)
        assertEquals(12, g.territory(0))
        // 원래 땅에서의 거리 순
        assertArrayEquals(intArrayOf(1, 10, 2, 15, 11, 3, 8, 13, 18, 17), f.absorbed)
        assertArrayEquals(intArrayOf(1, 1, 2, 2, 2, 3, 4, 5, 6, 7), f.absorbDepth)
        assertEquals(7, f.maxAbsorbDepth)
        // 원래 땅 색 바꾸기: 본진(0) 거리 0, (1,0) 거리 1
        assertArrayEquals(intArrayOf(0, 5), f.recolored)
        assertArrayEquals(intArrayOf(0, 1), f.recolorDepth)
        assertEquals(1, f.maxRecolorDepth)
        assertEquals(0, f.oldColor)
        assertEquals(1, f.color)
        assertEquals(0, f.player)
        assertFalse(f.gameOver)
        // 내 땅은 모두 새 색, 흡수 안 된 칸은 원래 색
        for (cell in 0 until g.cellCount) if (g.ownerAt(cell) == 0) assertEquals(1, g.colorAt(cell))
        assertEquals(3, g.colorAt(2, 2))
        assertEquals(2, g.colorAt(1, 1))
        // 다음 차례(1번)에 4 → 아래 줄 4 색 넷 + (3,1) 흡수 (0번 땅은 건너뛰고)
        assertEquals(Pick.OK, g.choose(4))
        assertEquals("0000./0..0./00.0./0100./11111", owners(g))
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 4), g.lastFlood!!.absorbDepth)
        // 0번이 2 → (1,1),(1,2) 두 칸 (2 색은 그 둘뿐)
        assertEquals(Pick.OK, g.choose(2))
        assertEquals(2, g.lastFlood!!.gain)
        // 원래 땅 물결은 본진에서 거리 순
        val rd = g.lastFlood!!.recolorDepth
        for (k in 1 until rd.size) assertTrue(rd[k - 1] <= rd[k])
        assertEquals(12, rd.size)
    }

    @Test
    fun `흡수 연쇄 - 넓어진 땅에서 다음 번에 다시 퍼짐`() {
        val g = game(
            2,
            "0101",
            "1010",
            "0101",
            "2023",
        )
        // 0번 = (0,0) 색 0 하나 / 1번 = (3,3) 색 3 하나
        assertEquals(1, g.territory(0))
        assertEquals(1, g.territory(1))
        g.choose(1) // (0,1),(1,0) 흡수
        assertEquals(3, g.territory(0))
        g.choose(2) // 1번: (3,2) 흡수
        assertEquals(2, g.territory(1))
        g.choose(0) // 0번: 넓어진 땅 둘레의 0 색 (0,2),(1,1),(2,0)
        val f = g.lastFlood!!
        assertEquals(3, f.gain)
        assertArrayEquals(intArrayOf(1, 1, 1), f.absorbDepth)
        assertEquals("000./00../0.../..11", owners(g))
    }

    @Test
    fun `다른 사람 땅은 건너지도 빼앗지도 않음`() {
        val g = game(
            2,
            "0111",
            "3333",
            "1113",
            "1113",
        )
        // 1번 시작 땅 = 3 색 벽 6칸
        assertEquals("0.../1111/...1/...1", owners(g))
        // 0번이 1 → 위 줄 1 셋만. 1번 벽 너머 1 색 여섯 칸은 못 감
        assertEquals(3, g.gainIf(0, 1))
        assertEquals(Pick.OK, g.choose(1))
        assertEquals("0000/1111/...1/...1", owners(g))
        // 1번은 1 을 못 고름 (0번 색) → 벽 너머 1 색 칸은 둘 다 당장은 못 가짐
        assertEquals(Pick.TAKEN, g.choose(1))
        assertEquals(0, g.gainIf(1, 2))
        assertEquals(Pick.OK, g.choose(2))
        assertEquals(0, g.lastFlood!!.gain)
        // 0번이 1 번 땅 색(2)을 고를 수 없고, 다른 색을 골라도 1번 땅은 그대로
        assertEquals(Pick.TAKEN, g.choose(2))
        assertEquals(Pick.OK, g.choose(5))
        assertEquals("0000/1111/...1/...1", owners(g))
        assertEquals(4, g.territory(0))
        assertEquals(6, g.territory(1))
        // 0번이 1 을 버렸으니 이제 1번이 1 을 골라 여섯 칸 흡수
        assertEquals(6, g.gainIf(1, 1))
        assertEquals(Pick.OK, g.choose(1))
        assertEquals("0000/1111/1111/1111", owners(g))
        for (cell in 0 until g.cellCount) {
            val o = g.ownerAt(cell)
            if (o >= 0) assertEquals(g.colorOf(o), g.colorAt(cell))
        }
    }

    @Test
    fun `흡수할 칸이 없어도 고를 수 있고 땅 색만 바뀜`() {
        val g = game(2, "012", "345", "123")
        assertEquals(0, g.gainIf(0, 5))
        assertEquals(Pick.OK, g.choose(5))
        assertEquals(5, g.colorOf(0))
        assertEquals(1, g.territory(0))
        assertEquals(1, g.current)
    }

    // ── 넘김 ──

    @Test
    fun `고를 색이 하나도 없으면 넘김`() {
        // 4인·4색 → 모두 막힘
        val g = game(
            4,
            "0012",
            "3021",
            "1230",
            "1003",
            colors = 4,
        )
        assertFalse(g.canPick)
        assertTrue(g.allowedColors(0).isEmpty())
        assertEquals(Pick.TAKEN, g.choose(1))
        assertTrue(g.pass())
        assertEquals(1, g.current)
        assertEquals(1, g.moves)
        // 6색 판에서는 넘길 수 없음
        val h = game(2, "01", "23")
        assertTrue(h.canPick)
        assertFalse(h.pass())
        assertEquals(0, h.current)
    }

    // ── 끝 ──

    @Test
    fun `모든 칸에 주인이 생기면 끝`() {
        val g = game(
            2,
            "011",
            "112",
            "222",
        )
        // 1번 시작 땅 = 2 색 넷. 0번이 1 → 1 색 넷 흡수 → 9칸 모두 주인 있음
        assertEquals(4, g.territory(1))
        assertEquals(Pick.OK, g.choose(1))
        assertEquals(9, g.ownedCount)
        assertTrue(g.gameOver)
        assertEquals(EndReason.ALL_OWNED, g.endReason)
        assertTrue(g.lastFlood!!.gameOver)
        assertEquals(Pick.OVER, g.choose(3))
        assertFalse(g.pass())
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }

    @Test
    fun `정해진 바퀴가 다 돌면 끝`() {
        val g = FloodGame(3, FloodGame.randomColors(3, Random(5), size = 8), first = 1, size = 8, maxRounds = 3)
        assertEquals(1, g.round)
        var newRounds = 0
        // 3바퀴 × 3명 = 9수
        for (k in 0 until 9) {
            assertFalse("수 $k 전에는 안 끝남", g.gameOver)
            val allowed = g.allowedColors(g.current)
            assertEquals(Pick.OK, g.choose(allowed.first()))
            if (g.lastFlood!!.newRound) newRounds++
            if (k == 2) assertEquals("한 바퀴 뒤 2바퀴째", 2, g.round)
            if (!g.gameOver) assertEquals((1 + k + 1) % 3, g.current)
        }
        assertEquals(2, newRounds)
        assertTrue(g.gameOver)
        assertEquals(EndReason.ROUNDS, g.endReason)
        assertEquals(3, g.round)
        assertEquals(9, g.moves)
        assertTrue(g.ownedCount < 64)
    }

    @Test
    fun `기본 판 20바퀴 안에 끝남`() {
        for (players in 2..4) {
            for (seed in 0 until 20) {
                val rnd = Random(seed * 10 + players)
                val g = FloodGame.create(players, rnd)
                assertEquals(14, g.size)
                assertEquals(20, g.maxRounds)
                var guard = 0
                while (!g.gameOver) {
                    if (!g.canPick) { assertTrue(g.pass()); continue }
                    val allowed = g.allowedColors(g.current)
                    assertTrue(allowed.size >= 2)
                    // 가장 많이 흡수하는 색 (아니면 무작위)
                    val c = if (seed % 2 == 0) allowed.maxByOrNull { g.gainIf(g.current, it) }!! else allowed[rnd.nextInt(allowed.size)]
                    val before = g.territory(g.current)
                    val gain = g.gainIf(g.current, c)
                    val p = g.current
                    assertEquals(Pick.OK, g.choose(c))
                    assertEquals(before + gain, g.territory(p))
                    guard++
                    assertTrue(guard <= 20 * players)
                }
                assertTrue(g.endReason == EndReason.ALL_OWNED || g.moves == 20 * players)
                // 땅 수 합 = 주인 있는 칸 수, 사람 색끼리는 늘 다름
                assertEquals((0 until 196).count { g.ownerAt(it) >= 0 }, (0 until players).sumOf { g.territory(it) })
                assertEquals(players, (0 until players).map { g.colorOf(it) }.toSet().size)
                val ranks = g.ranks()
                assertTrue(ranks.contains(1))
            }
        }
    }

    // ── 순위 ──

    @Test
    fun `순위는 땅 넓은 순, 동점은 같은 순위`() {
        // 0번 3칸, 1번 3칸, 2번 1칸, 3번 2칸
        val g = game(
            4,
            "0012",
            "0345",
            "5453",
            "1433",
        )
        assertEquals(3, g.territory(0))
        assertEquals(1, g.territory(1))
        assertEquals(3, g.territory(2))
        assertEquals(1, g.territory(3))
        assertArrayEquals(intArrayOf(1, 3, 1, 3), g.ranks())
        val h = game(2, "01", "23")
        assertArrayEquals(intArrayOf(1, 1), h.ranks())
        val k = game(3, "001", "345", "512")
        assertNotEquals(k.territory(0), k.territory(1))
        assertArrayEquals(intArrayOf(1, 2, 2), k.ranks())
    }
}
