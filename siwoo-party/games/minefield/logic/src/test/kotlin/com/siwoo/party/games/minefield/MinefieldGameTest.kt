package com.siwoo.party.games.minefield

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MinefieldGameTest {

    private fun idx(c: Int, r: Int) = r * GameConfig.COLS + c

    /**
     * 시험용 판 (12×8, 지뢰 15개):
     * 오른쪽 아래 구석에 지뢰를 몰아 둔다 → 왼쪽 위는 넓은 0 지역.
     * 지뢰: 9~11열 × 4~7줄(12칸) + (11,0) (0,7) (5,7). 안전한 칸은 모두 0 지역과 이어져 있다
     */
    private val mineCells: List<Int> = buildList {
        for (r in 4..7) for (c in 9..11) add(idx(c, r))
        add(idx(11, 0)); add(idx(0, 7)); add(idx(5, 7))
    }

    private fun game(players: Int = 3, first: Int = 0) =
        MinefieldGame(players, Random(1), first).also { it.placeMines(mineCells) }

    // ── 주변 숫자 ──

    @Test fun `주변 지뢰 숫자를 8방향으로 센다`() {
        val g = game()
        assertEquals(0, g.adjacent(idx(0, 0)))
        // (10,0): 이웃 (11,0) → 1
        assertEquals(1, g.adjacent(idx(10, 0)))
        // (8,4): 이웃 (9,3)x (9,4) (9,5) → 2
        assertEquals(2, g.adjacent(idx(8, 4)))
        // (8,5): (9,4) (9,5) (9,6) → 3
        assertEquals(3, g.adjacent(idx(8, 5)))
        // (10,3): (9,4) (10,4) (11,4) → 3
        assertEquals(3, g.adjacent(idx(10, 3)))
        // (8,7): (9,6) (9,7) → 2, (7,7): 없음 → 0
        assertEquals(2, g.adjacent(idx(8, 7)))
        assertEquals(0, g.adjacent(idx(7, 7)))
        // (6,6)·(4,6): (5,7) → 1, (1,6): (0,7) → 1, (10,2): 없음 → 0
        assertEquals(1, g.adjacent(idx(6, 6)))
        assertEquals(1, g.adjacent(idx(4, 6)))
        assertEquals(1, g.adjacent(idx(1, 6)))
        assertEquals(0, g.adjacent(idx(10, 2)))
        // 무작위 판에서도 숫자 = 실제 이웃 지뢰 수
        val r = MinefieldGame(2, Random(5))
        r.open(idx(5, 4))
        val nb = IntArray(8)
        for (k in 0 until r.cellCount) {
            val n = r.neighbors(k, nb)
            assertEquals((0 until n).count { r.isMine(nb[it]) }, r.adjacent(k))
        }
        // 구석 칸 이웃 3개, 가장자리 5개, 가운데 8개
        assertEquals(3, r.neighbors(0, nb))
        assertEquals(5, r.neighbors(idx(5, 0), nb))
        assertEquals(8, r.neighbors(idx(5, 4), nb))
    }

    // ── 확장 ──

    @Test fun `0 칸은 이어진 0 지역과 둘레 숫자 칸까지 열고 지뢰는 열지 않는다`() {
        val g = game()
        val r = g.open(idx(0, 0))
        assertTrue(r is OpenResult.Safe)
        r as OpenResult.Safe
        // 지뢰가 아닌 칸은 모두 0 지역과 이어져 있으므로 이 판에서는 안전한 칸이 전부 열리고 지뢰는 하나도 안 열린다
        for (k in 0 until g.cellCount) {
            if (g.isMine(k)) assertFalse("지뢰 $k 는 열리지 않음", g.isOpen(k))
            else assertTrue("안전 칸 $k 열림", g.isOpen(k))
        }
        // 숫자 칸(둘레)도 열림
        assertTrue(g.isOpen(idx(8, 5)))
        assertTrue(g.isOpen(idx(10, 0)))
        // 숫자 칸 너머로는 퍼지지 않음: (10,0)은 열렸지만 (11,0)은 지뢰라 닫힘
        assertFalse(g.isOpen(idx(11, 0)))
        // 점수 = 연 칸 수, BFS 순서·거리
        assertEquals(r.cells.size, r.gained)
        assertEquals(r.gained, g.scores[0])
        assertEquals(r.gained, g.safeOpened)
        assertEquals(idx(0, 0), r.cells[0])
        assertEquals(0, r.dist[0])
        for (i in 1 until r.dist.size) assertTrue("거리는 줄지 않음", r.dist[i] >= r.dist[i - 1])
        // 거리 = 체비셰프 거리 (0 지역이 막힘 없이 이어진 왼쪽 위)
        val at = r.cells.indexOf(idx(3, 2))
        assertEquals(3, r.dist[at])
        assertEquals(r.cells.size, r.cells.distinct().size)
    }

    @Test fun `숫자 칸은 그 칸만 열고 1점`() {
        val g = game()
        val r = g.open(idx(8, 5)) as OpenResult.Safe
        assertEquals(1, r.gained)
        assertArrayEquals(intArrayOf(idx(8, 5)), r.cells)
        assertEquals(1, g.scores[0])
        assertFalse(g.isOpen(idx(7, 5)))
        // 이미 열린 칸은 무시, 차례 그대로
        assertEquals(1, g.current)
        assertSame(OpenResult.Ignored, g.open(idx(8, 5)))
        assertEquals(1, g.current)
    }

    @Test fun `숫자 칸에 막힌 작은 0 지역은 그만큼만 열린다`() {
        // 가운데 세로 벽(5열, 0~6줄 7개 + 다른 곳 8개)으로 나눠 왼쪽만 열리는지
        val mines = (0..6).map { idx(5, it) } + listOf(idx(11, 7), idx(10, 7), idx(9, 7), idx(11, 6), idx(10, 6), idx(9, 6), idx(11, 5), idx(10, 5))
        val g = MinefieldGame(2, Random(1)).also { it.placeMines(mines) }
        val r = g.open(idx(0, 0)) as OpenResult.Safe
        // 왼쪽 0~3열은 0, 4열은 숫자(벽 옆) → 4열에서 멈춘다. (5,7)은 지뢰가 아니지만 (4,7)이 숫자라 막힘
        for (k in r.cells) assertFalse(g.isMine(k))
        assertEquals(8 * 5, r.gained)
        assertFalse(g.isOpen(idx(5, 7)))
        assertFalse(g.isOpen(idx(6, 0)))
        // 4열(벽 옆)은 숫자 칸이라 열렸지만 거기서 더 퍼지지 않는다
        assertTrue(g.isOpen(idx(4, 3)))
        assertTrue(g.adjacent(idx(4, 3)) > 0)
        assertFalse(g.isOpen(idx(5, 3)))
        assertEquals(r.gained, r.cells.size)
        assertEquals(g.safeOpened, r.gained)
    }

    // ── 첫 칸 안전 ──

    @Test fun `첫 칸과 그 주변은 여러 씨앗에서 언제나 안전하고 0 이다`() {
        for (seed in 0 until 400) {
            val rnd = Random(seed)
            val g = MinefieldGame(2 + seed % 3, rnd)
            val first = rnd.nextInt(g.cellCount)
            assertFalse(g.minesPlaced)
            val r = g.open(first)
            assertTrue("씨앗 $seed: 첫 칸은 안전", r is OpenResult.Safe)
            assertTrue(g.minesPlaced)
            assertEquals(GameConfig.MINES, (0 until g.cellCount).count { g.isMine(it) })
            for (k in 0 until g.cellCount) {
                if (abs(g.col(k) - g.col(first)) <= 1 && abs(g.row(k) - g.row(first)) <= 1) {
                    assertFalse("씨앗 $seed: 첫 칸 주변 $k 안전", g.isMine(k))
                }
            }
            assertEquals(0, g.adjacent(first))
            assertTrue("첫 칸은 넓게 열림", (r as OpenResult.Safe).gained >= 4)
            assertEquals(0, g.eliminatedCount)
        }
    }

    @Test fun `구석 첫 칸도 안전`() {
        for (seed in 0 until 50) {
            val g = MinefieldGame(2, Random(seed))
            assertTrue(g.open(g.cellCount - 1) is OpenResult.Safe)
            assertEquals(0, g.adjacent(g.cellCount - 1))
        }
    }

    // ── 깃발 ──

    @Test fun `깃발은 차례를 쓰지 않고 깃발 칸은 열 수 없다`() {
        val g = game(players = 3)
        assertEquals(0, g.current)
        assertEquals(FlagResult.ADDED, g.toggleFlag(idx(11, 0)))
        assertEquals(0, g.current)
        assertTrue(g.isFlagged(idx(11, 0)))
        assertEquals(0, g.flagOwner(idx(11, 0)))
        assertEquals(1, g.flagCount)
        assertEquals(GameConfig.MINES - 1, g.minesLeftEstimate)
        // 깃발 칸 열기 → 막힘, 차례·판 그대로
        val b = g.open(idx(11, 0))
        assertTrue(b is OpenResult.Blocked)
        assertEquals(0, g.current)
        assertFalse(g.isExploded(idx(11, 0)))
        assertTrue(g.alive[0])
        // 같은 사람이 다른 칸을 열면 그제야 차례가 넘어간다
        g.open(idx(8, 5))
        assertEquals(1, g.current)
        // 다른 사람이 뽑을 수 있다 (공용 표시)
        assertEquals(FlagResult.REMOVED, g.toggleFlag(idx(11, 0)))
        assertEquals(1, g.current)
        assertFalse(g.isFlagged(idx(11, 0)))
        assertEquals(0, g.flagCount)
        // 열린 칸에는 깃발을 못 꽂는다
        assertEquals(FlagResult.IGNORED, g.toggleFlag(idx(8, 5)))
        // 뽑은 뒤에는 열 수 있다 (지뢰라 탈락)
        assertTrue(g.open(idx(11, 0)) is OpenResult.Boom)
        // 터진 지뢰에도 깃발 못 꽂음
        assertEquals(FlagResult.IGNORED, g.toggleFlag(idx(11, 0)))
    }

    @Test fun `첫 칸 전에도 깃발은 꽂을 수 있고 첫 칸 지뢰 배치에 영향 없음`() {
        val g = MinefieldGame(2, Random(3))
        assertEquals(FlagResult.ADDED, g.toggleFlag(5))
        assertTrue(g.open(5) is OpenResult.Blocked)
        assertFalse(g.minesPlaced)
        g.toggleFlag(5)
        assertTrue(g.open(5) is OpenResult.Safe)
    }

    @Test fun `0 지역에 잘못 꽂힌 깃발은 뽑히며 함께 열린다`() {
        val g = game()
        g.toggleFlag(idx(2, 2)) // 실제로는 안전한 칸
        g.toggleFlag(idx(9, 4)) // 진짜 지뢰
        assertEquals(2, g.flagCount)
        val r = g.open(idx(0, 0)) as OpenResult.Safe
        assertTrue(g.isOpen(idx(2, 2)))
        assertFalse(g.isFlagged(idx(2, 2)))
        assertTrue(idx(2, 2) in r.cells)
        assertTrue(g.isFlagged(idx(9, 4)))
        assertEquals(1, g.flagCount)
    }

    // ── 탈락·끝 ──

    @Test fun `지뢰를 밟으면 탈락하고 지뢰는 남으며 다음 살아 있는 사람 차례`() {
        val g = game(players = 4)
        g.open(idx(8, 5)) // 0: 1점
        val b = g.open(idx(9, 4)) as OpenResult.Boom // 1: 펑
        assertEquals(1, b.player)
        assertEquals(2, b.next)
        assertFalse(b.gameOver)
        assertFalse(g.alive[1])
        assertEquals(0, g.elimOrder[1])
        assertTrue(g.isExploded(idx(9, 4)))
        assertEquals(1, g.explodedBy(idx(9, 4)))
        assertSame(OpenResult.Ignored, g.open(idx(9, 4)))
        assertEquals(GameConfig.MINES - 1, g.minesLeftEstimate)
        g.open(idx(8, 4)) // 2
        g.open(idx(8, 6)) // 3
        assertEquals("탈락한 1번은 건너뜀", 0, g.current)
        g.open(idx(8, 7)) // 0
        assertEquals(2, g.current)
    }

    @Test fun `최후 1인이 남으면 끝`() {
        val g = game(players = 3)
        g.open(idx(9, 4)) // 0 탈락
        assertEquals(1, g.current)
        val b = g.open(idx(9, 5)) as OpenResult.Boom // 1 탈락 → 2만 남음
        assertTrue(b.gameOver)
        assertEquals(-1, b.next)
        assertEquals(EndReason.LAST_SURVIVOR, g.endReason)
        assertTrue(g.gameOver)
        assertSame(OpenResult.Ignored, g.open(idx(0, 0)))
        assertEquals(FlagResult.IGNORED, g.toggleFlag(idx(0, 0)))
        assertArrayEquals(intArrayOf(3, 2, 1), g.finalRanks())
    }

    @Test fun `2인은 한 명이 밟으면 바로 끝`() {
        val g = game(players = 2, first = 1)
        assertEquals(1, g.current)
        g.open(idx(8, 5)) // 1: 숫자 칸
        val b = g.open(idx(10, 4)) as OpenResult.Boom // 0 탈락
        assertTrue(b.gameOver)
        assertArrayEquals(intArrayOf(2, 1), g.finalRanks())
    }

    @Test fun `안전한 칸이 모두 열리면 끝`() {
        val g = game(players = 3)
        val r = g.open(idx(0, 0)) as OpenResult.Safe
        // 이 판은 안전한 칸이 모두 한 지역 → 첫 수에 다 열림
        assertEquals(GameConfig.SAFE_CELLS, g.safeOpened)
        assertTrue(r.gameOver)
        assertEquals(EndReason.CLEARED, g.endReason)
        assertEquals(-1, r.next)
        assertArrayEquals(intArrayOf(1, 2, 2), g.finalRanks())
        assertEquals(GameConfig.MINES, g.hiddenMines().size)
    }

    @Test fun `여러 번에 나눠 열어도 마지막 안전 칸에서 끝`() {
        val g = game(players = 2)
        val safe = (0 until g.cellCount).filter { !g.isMine(it) }
        // 숫자 칸만 하나씩 열다가 나머지는 0 칸으로
        val numbers = safe.filter { g.adjacent(it) > 0 }
        for (k in numbers) g.open(k)
        assertEquals(numbers.size, g.safeOpened)
        assertFalse(g.gameOver)
        var last: OpenResult? = null
        while (!g.gameOver) last = g.open(safe.first { !g.isOpen(it) })
        assertTrue((last as OpenResult.Safe).gameOver)
        assertEquals(EndReason.CLEARED, g.endReason)
        assertEquals(GameConfig.SAFE_CELLS, g.scores.sum())
    }

    // ── 순위 ──

    @Test fun `순위는 생존 우선 그다음 점수, 탈락자는 늦게 탈락할수록 위`() {
        val g = game(players = 4)
        g.open(idx(8, 5)) // 0: 1점
        g.open(idx(9, 4)) // 1: 탈락 (첫 번째)
        g.open(idx(8, 4)) // 2: 1점
        g.open(idx(8, 6)) // 3: 1점
        g.open(idx(9, 5)) // 0: 탈락 (두 번째)
        // 남은 2·3 중 2가 크게 엶 → 안전 칸 전부 열림
        val r = g.open(idx(0, 0)) as OpenResult.Safe
        assertTrue(r.gameOver)
        assertEquals(EndReason.CLEARED, g.endReason)
        // 2: 생존·최다, 3: 생존, 0: 나중 탈락, 1: 처음 탈락 (0 은 점수 1 이지만 탈락이라 아래)
        assertArrayEquals(intArrayOf(3, 4, 1, 2), g.finalRanks())
    }

    @Test fun `생존자끼리 점수가 같으면 공동 순위`() {
        val g = game(players = 3)
        g.open(idx(8, 5)) // 0: 1
        g.open(idx(8, 4)) // 1: 1
        g.open(idx(9, 4)) // 2: 탈락
        assertArrayEquals(intArrayOf(1, 1, 3), g.finalRanks())
        // 탈락자는 점수가 있어도 점수 0 인 생존자보다 아래
        val h = game(players = 3)
        h.open(idx(8, 5)) // 0: 1점
        h.toggleFlag(idx(0, 0)) // 1: 깃발만 (차례 그대로)
        h.open(idx(8, 7)) // 1: 1점
        h.open(idx(8, 6)) // 2: 1점
        h.open(idx(9, 4)) // 0: 탈락
        assertEquals(0, h.elimOrder[0])
        assertArrayEquals(intArrayOf(3, 1, 1), h.finalRanks())
    }

    @Test fun `무작위 판을 끝까지 두면 언제나 끝나고 순위는 1부터`() {
        for (seed in 0 until 60) {
            val rnd = Random(seed)
            val players = 2 + seed % 3
            val g = MinefieldGame(players, rnd, firstPlayer = seed % players)
            var guard = 0
            while (!g.gameOver && guard++ < 500) {
                val hidden = (0 until g.cellCount).filter { g.isHidden(it) && !g.isFlagged(it) }
                g.open(hidden[rnd.nextInt(hidden.size)])
            }
            assertTrue(g.gameOver)
            val ranks = g.finalRanks()
            assertEquals(1, ranks.minOrNull())
            // 생존자는 모두 탈락자보다 위
            for (a in 0 until players) for (b in 0 until players) {
                if (g.alive[a] && !g.alive[b]) assertTrue(ranks[a] < ranks[b])
            }
            assertEquals(g.safeOpened, g.scores.sum())
        }
    }
}
