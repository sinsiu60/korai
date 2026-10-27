package com.siwoo.party.games.pirate

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PirateGameTest {

    private fun traps(g: PirateGame): List<Int> = (0 until GameConfig.HOLES).filter { g.isTrap(it) }
    private fun safeHole(g: PirateGame): Int = (0 until GameConfig.HOLES).first { !g.isTrap(it) && !g.isUsed(it) }

    /** 지금 차례인 사람이 함정을 찌르게 한다 → 그 사람 번호 */
    private fun hitTrap(g: PirateGame): Int {
        val p = g.current
        val r = g.pick(traps(g).first())
        assertTrue(r is Pick.Trap && r.player == p)
        return p
    }

    @Test fun `함정 구멍을 찌르면 탈락, 안전 구멍은 다음 차례`() {
        val g = PirateGame(3, 1, Random(7))
        val first = g.startRound(0)
        assertEquals(0, first)
        val safe = g.pick(safeHole(g))
        assertTrue(safe is Pick.Safe && safe.player == 0 && safe.next == 1)
        assertEquals(1, g.current)
        assertTrue(g.alive[0])
        val trapHole = traps(g).single()
        val r = g.pick(trapHole)
        assertTrue(r is Pick.Trap && r.player == 1 && !r.gameOver)
        assertFalse(g.alive[1])
        assertEquals(listOf(1), g.eliminated)
        assertEquals(Phase.ROUND_OVER, g.phase)
    }

    @Test fun `이미 칼이 꽂힌 구멍과 없는 구멍은 무시`() {
        val g = PirateGame(2, 1, Random(1))
        g.startRound(0)
        val h = safeHole(g)
        assertTrue(g.pick(h) is Pick.Safe)
        assertEquals(0, g.holeOwner[h])
        assertTrue(g.isUsed(h))
        assertFalse(g.canPick(h))
        assertSame(Pick.Ignored, g.pick(h))
        assertSame(Pick.Ignored, g.pick(-1))
        assertSame(Pick.Ignored, g.pick(GameConfig.HOLES))
        assertEquals("무시된 터치는 차례를 넘기지 않음", 1, g.current)
        assertEquals(GameConfig.HOLES - 1, g.emptyHoles)
    }

    @Test fun `판이 끝난 뒤 고르기는 무시`() {
        val g = PirateGame(3, 1, Random(2))
        g.startRound(0)
        hitTrap(g)
        assertSame(Pick.Ignored, g.pick(safeHole(g)))
        assertSame("시작 전에도 무시", Pick.Ignored, PirateGame(2).pick(0))
    }

    @Test fun `함정 수 - 2인은 1개 고정, 3~4인은 1 또는 2`() {
        assertEquals(1, PirateGame.trapsFor(2, 1))
        assertEquals(1, PirateGame.trapsFor(2, 2))
        assertEquals(1, PirateGame.trapsFor(3, 1))
        assertEquals(2, PirateGame.trapsFor(3, 2))
        assertEquals(2, PirateGame.trapsFor(4, 2))
        assertEquals("범위 밖 설정은 맞춤", 2, PirateGame.trapsFor(4, 9))
        assertEquals(1, PirateGame.trapsFor(4, 0))
        val two = PirateGame(2, 2, Random(3))
        two.startRound()
        assertEquals(1, two.roundTraps)
        assertEquals(1, traps(two).size)
        val four = PirateGame(4, 2, Random(3))
        four.startRound()
        assertEquals(2, four.roundTraps)
        assertEquals(2, traps(four).size)
        assertEquals(GameConfig.PIRATE_TRAPS, PirateGame(4).trapSetting)
    }

    @Test fun `3인 2함정 설정 - 2명 남은 판은 함정 1개`() {
        val g = PirateGame(3, 2, Random(4))
        g.startRound(0)
        assertEquals(2, traps(g).size)
        hitTrap(g)
        g.startRound()
        assertEquals(1, g.roundTraps)
        assertEquals(1, traps(g).size)
    }

    @Test fun `함정은 구멍 수를 넘지 않고 서로 다른 구멍`() {
        for (seed in 0 until 500) {
            val g = PirateGame(4, 2, Random(seed))
            g.startRound()
            val t = traps(g)
            assertEquals(2, t.size) // isTrap 은 구멍마다 하나 → 개수가 맞으면 서로 다름
            assertTrue(t.all { it in 0 until GameConfig.HOLES })
            assertTrue(g.roundTraps <= GameConfig.HOLES)
        }
        // 위치가 고르게 퍼지는지 (특정 구멍에 몰리지 않음)
        val counts = IntArray(GameConfig.HOLES)
        for (seed in 0 until 2400) {
            val g = PirateGame(2, 1, Random(seed))
            g.startRound()
            counts[traps(g).single()]++
        }
        assertTrue(counts.all { it in 50..160 })
    }

    @Test fun `차례는 탈락자를 건너뛴다`() {
        val g = PirateGame(4, 1, Random(5))
        g.startRound(1)
        assertEquals(1, hitTrap(g)) // 1번 탈락
        assertEquals("탈락자 다음 사람부터", 2, g.nextStarter)
        assertEquals(2, g.startRound())
        assertEquals(3, (g.pick(safeHole(g)) as Pick.Safe).next)
        assertEquals(0, (g.pick(safeHole(g)) as Pick.Safe).next)
        assertEquals("1번은 건너뜀", 2, (g.pick(safeHole(g)) as Pick.Safe).next)
        assertEquals(2, g.nextAlive(0))
        assertEquals("탈락자 기준으로도 다음 생존자", 2, g.nextAlive(1))
    }

    @Test fun `마지막 사람이 탈락하면 다음 판은 맨 앞의 생존자부터`() {
        val g = PirateGame(3, 1, Random(6))
        g.startRound(2)
        assertEquals(2, hitTrap(g))
        assertEquals(0, g.nextStarter)
    }

    @Test fun `다음 판은 통이 비워지고 함정을 새로 숨긴다`() {
        val g = PirateGame(3, 1, Random(8))
        g.startRound(0)
        repeat(5) { g.pick(safeHole(g)) }
        assertEquals(GameConfig.HOLES - 5, g.emptyHoles)
        hitTrap(g)
        assertEquals(1, g.round)
        g.startRound()
        assertEquals(2, g.round)
        assertEquals(Phase.PICKING, g.phase)
        assertEquals(GameConfig.HOLES, g.emptyHoles)
        assertTrue(g.holeOwner.all { it == -1 })
        assertEquals(1, traps(g).size)
        // 여러 판에 걸쳐 함정 위치가 바뀐다
        val seen = HashSet<Int>()
        val h = PirateGame(4, 1, Random(9))
        repeat(3) {
            h.startRound(); seen.add(traps(h).single())
            if (it < 2) hitTrap(h)
        }
        assertTrue(seen.size >= 2)
    }

    @Test fun `판 시작은 판이 끝났을 때만`() {
        val g = PirateGame(2, 1, Random(1))
        g.startRound()
        try { g.startRound(); error("진행 중에 새 판") } catch (e: IllegalStateException) { /* 정상 */ }
    }

    @Test fun `첫 판 시작 사람은 무작위`() {
        val seen = HashSet<Int>()
        for (seed in 0 until 60) seen.add(PirateGame(4, 1, Random(seed)).startRound())
        assertEquals(setOf(0, 1, 2, 3), seen)
    }

    @Test fun `한 명 남으면 끝, 탈락 순위는 늦게 탈락한 순`() {
        val g = PirateGame(4, 2, Random(10))
        g.startRound(2)
        assertEquals(2, hitTrap(g))
        g.startRound()
        assertEquals(3, g.current)
        g.pick(safeHole(g)) // 3 안전
        assertEquals(0, hitTrap(g))
        assertFalse(g.gameOver)
        g.startRound()
        assertEquals("0번 다음 생존자 = 1번", 1, g.current)
        g.pick(safeHole(g)) // 1 안전
        val p = g.current
        assertEquals(3, p)
        val r = g.pick(traps(g).single())
        assertTrue(r is Pick.Trap && r.gameOver)
        assertTrue(g.gameOver)
        assertEquals(1, g.winner)
        assertEquals(listOf(2, 0, 3), g.eliminated)
        assertArrayEquals(intArrayOf(3, 1, 4, 2), g.finalRanks())
        assertSame(Pick.Ignored, g.pick(safeHole(g)))
    }

    @Test fun `2인 - 한 번 탈락하면 바로 끝`() {
        val g = PirateGame(2, 2, Random(11))
        g.startRound(1)
        g.pick(safeHole(g))
        assertEquals(0, g.current)
        assertEquals(0, hitTrap(g))
        assertTrue(g.gameOver)
        assertEquals(1, g.winner)
        assertArrayEquals(intArrayOf(2, 1), g.finalRanks())
    }

    @Test fun `빈 구멍이 함정만 남으면 반드시 찌르게 되어 판이 끝난다`() {
        val g = PirateGame(3, 2, Random(12))
        g.startRound(0)
        while (g.phase == Phase.PICKING) {
            val h = (0 until GameConfig.HOLES).firstOrNull { !g.isUsed(it) && !g.isTrap(it) } ?: traps(g).first { !g.isUsed(it) }
            g.pick(h)
        }
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertEquals(1, g.eliminated.size)
        assertEquals(GameConfig.HOLES - 22 - 1, g.emptyHoles) // 안전 22개 + 함정 1개 사용
    }

    @Test fun `끝까지 무작위로 해도 순위는 1부터 인원수까지 하나씩`() {
        for (seed in 0 until 200) {
            val rnd = Random(seed)
            val n = 2 + seed % 3
            val g = PirateGame(n, 1 + seed % 2, rnd)
            while (!g.gameOver) {
                g.startRound()
                while (g.phase == Phase.PICKING) {
                    val empty = (0 until GameConfig.HOLES).filter { !g.isUsed(it) }
                    g.pick(empty[rnd.nextInt(empty.size)])
                }
            }
            assertEquals(n - 1, g.eliminated.size)
            assertEquals((1..n).toList(), g.finalRanks().sorted())
            assertEquals(1, g.finalRanks()[g.winner])
        }
    }
}
