package com.siwoo.party.games.darts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 라운드 점수제 합계·순위, 301 빼기·버스트·완료·완료 순서·최대 라운드 */
class DartsGameTest {

    private val xy = FloatArray(2)

    /** 그 칸 가운데로 던짐 */
    private fun DartsGame.hit(ring: Ring, n: Int = 20): ThrowResult {
        Board.center(ring, n, xy)
        return throwDart(xy[0], xy[1])!!
    }

    /** 한 차례 (3발 또는 버스트·완료까지) 던지고 endTurn */
    private fun DartsGame.turn(vararg shots: Pair<Ring, Int>): Boolean {
        for (s in shots) {
            if (phase != Phase.THROW) break
            hit(s.first, s.second)
        }
        assertSame(Phase.TURN_OVER, phase)
        return endTurn()
    }

    private val T20 = Ring.TRIPLE to 20
    private val S1 = Ring.SINGLE_INNER to 1
    private val OUT = Ring.OUT to 0

    // ── 라운드 점수제 ──

    @Test
    fun `라운드제 한 차례 3발 합계`() {
        val g = DartsGame(2)
        val a = g.hit(Ring.TRIPLE, 20)
        assertEquals(60, a.counted)
        assertFalse(a.turnOver)
        assertEquals(0, a.dart)
        g.hit(Ring.BULL)
        val c = g.hit(Ring.DOUBLE, 5)
        assertEquals(2, c.dart)
        assertTrue(c.turnOver)
        assertEquals(120, c.turnTotal)
        assertEquals(120, g.scores[0])
        assertSame(Phase.TURN_OVER, g.phase)
        assertNull("차례 끝나면 더 못 던짐", g.throwDart(0f, 0f))
        assertFalse(g.endTurn())
        assertEquals(1, g.current)
        assertEquals(0, g.dartInTurn)
        assertEquals(0, g.turnTotal)
        assertNull(g.turnHits[0])
    }

    @Test
    fun `180 은 트리플 20 세 발`() {
        val g = DartsGame(2)
        g.hit(Ring.TRIPLE, 20); g.hit(Ring.TRIPLE, 20)
        val r = g.hit(Ring.TRIPLE, 20)
        assertEquals(180, r.turnTotal)
    }

    @Test
    fun `라운드제 8라운드 뒤 끝, 합계 순위`() {
        val g = DartsGame(3, DartsMode.ROUNDS, first = 1)
        var turns = 0
        while (!g.over) {
            val p = g.current
            assertEquals(turns / 3 + 1, g.round)
            // 1번은 트리플 20, 0번은 싱글 20, 2번은 싱글 20 (0번과 같음)
            val shot = if (p == 1) T20 else (Ring.SINGLE_OUTER to 20)
            g.turn(shot, shot, shot)
            turns++
        }
        assertEquals(24, turns)
        assertEquals(8 * 60, g.scores[0])
        assertEquals(8 * 180, g.scores[1])
        assertEquals(8 * 60, g.scores[2])
        assertArrayEquals(intArrayOf(2, 1, 2), g.ranks())
        assertNull(g.throwDart(0f, 0f))
    }

    @Test
    fun `라운드제 순서는 first 부터 번호 순`() {
        val g = DartsGame(4, first = 2)
        val order = ArrayList<Int>()
        repeat(8) { order.add(g.current); g.turn(OUT, OUT, OUT) }
        assertEquals(listOf(2, 3, 0, 1, 2, 3, 0, 1), order)
        assertEquals(3, g.round)
    }

    @Test
    fun `라운드제 4인 다른 점수 순위`() {
        val g = DartsGame(4)
        while (!g.over) {
            val n = when (g.current) { 0 -> 5; 1 -> 20; 2 -> 1; else -> 12 }
            g.turn(Ring.SINGLE_INNER to n, Ring.SINGLE_INNER to n, Ring.SINGLE_INNER to n)
        }
        assertArrayEquals(intArrayOf(3, 1, 4, 2), g.ranks())
        assertEquals(8 * 3 * 12, g.scores[3])
    }

    // ── 301 ──

    @Test
    fun `301 은 빼기`() {
        val g = DartsGame(2, DartsMode.X01)
        assertEquals(301, g.remaining[0])
        g.hit(Ring.TRIPLE, 20)
        assertEquals(241, g.remaining[0])
        g.hit(Ring.OUTER_BULL)
        assertEquals(216, g.remaining[0])
        val r = g.hit(Ring.SINGLE_INNER, 7)
        assertEquals(209, g.remaining[0])
        assertEquals(92, r.turnTotal)
        assertTrue(r.turnOver)
        assertEquals(209, g.shownScore(0))
        assertEquals(0, g.scores[0])
    }

    @Test
    fun `301 버스트 - 이번 차례 점수 원래대로, 차례 끝`() {
        val g = DartsGame(2, DartsMode.X01, start = 100)
        g.turn(OUT, OUT, OUT)
        g.turn(OUT, OUT, OUT)
        // 0번: 100 → 트리플 20 (40) → 트리플 20 → 버스트
        assertEquals(0, g.current)
        assertEquals(100, g.turnStartRemaining)
        g.hit(Ring.TRIPLE, 20)
        assertEquals(40, g.remaining[0])
        val r = g.hit(Ring.TRIPLE, 20)
        assertTrue(r.bust)
        assertTrue(r.turnOver)
        assertEquals(0, r.counted)
        assertEquals(0, r.turnTotal)
        assertEquals("버스트 → 차례 시작 점수", 100, g.remaining[0])
        assertTrue(g.turnBust)
        assertEquals("버스트하면 남은 다트는 못 던짐", 2, g.dartInTurn)
        assertSame(Phase.TURN_OVER, g.phase)
        assertFalse(g.endTurn())
        assertEquals(1, g.current)
        assertFalse(g.turnBust)
    }

    @Test
    fun `301 첫 다트 버스트`() {
        val g = DartsGame(2, DartsMode.X01, start = 10)
        val r = g.hit(Ring.SINGLE_INNER, 11)
        assertTrue(r.bust)
        assertEquals(10, g.remaining[0])
    }

    @Test
    fun `301 정확히 0 이면 완료 (더블 아웃 없음), 2인은 바로 끝`() {
        val g = DartsGame(2, DartsMode.X01)
        assertFalse(g.turn(T20, T20, T20)) // 121
        assertFalse(g.turn(OUT, OUT, OUT))
        g.hit(Ring.TRIPLE, 20) // 61
        g.hit(Ring.TRIPLE, 20) // 1
        assertEquals(1, g.remaining[0])
        val r = g.hit(Ring.SINGLE_INNER, 1) // 싱글로 끝내도 됨
        assertTrue(r.finished)
        assertTrue(r.turnOver)
        assertFalse(r.bust)
        assertEquals(0, g.remaining[0])
        assertTrue(g.endTurn())
        assertTrue(g.over)
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }

    @Test
    fun `301 첫째나 둘째 다트로 끝내면 거기서 차례 끝`() {
        val g = DartsGame(3, DartsMode.X01, start = 50)
        val r = g.hit(Ring.BULL)
        assertTrue(r.finished)
        assertTrue(r.turnOver)
        assertEquals(1, g.dartInTurn)
        assertFalse(g.endTurn())
        assertEquals(1, g.current)
    }

    @Test
    fun `301 완료 순서대로 순위, 끝낸 사람은 건너뜀`() {
        val g = DartsGame(4, DartsMode.X01, start = 60)
        // 0번 못 끝냄, 1번 끝냄
        g.turn(S1, S1, S1) // 0: 57
        g.turn(T20)        // 1: 끝 (1등)
        g.turn(OUT, OUT, OUT) // 2
        g.turn(OUT, OUT, OUT) // 3
        assertEquals(1, g.finishedCount)
        assertEquals(0, g.current)
        // 0번 → 3번이 먼저 끝냄
        g.turn(S1, S1, S1) // 0: 54
        assertEquals("끝낸 1번은 건너뜀", 2, g.current)
        g.turn(OUT, OUT, OUT) // 2
        g.turn(T20)           // 3: 끝 (2등)
        assertEquals(0, g.current)
        assertFalse(g.over)
        g.turn(Ring.TRIPLE to 18) // 0: 54 → 끝 (3등) → 남은 사람 1명 → 끝
        assertTrue(g.over)
        assertArrayEquals(intArrayOf(3, 1, 4, 2), g.ranks())
        assertEquals(0, g.finishOrder[1])
        assertEquals(1, g.finishOrder[3])
        assertEquals(2, g.finishOrder[0])
        assertEquals(-1, g.finishOrder[2])
        assertFalse(g.endedByRoundLimit)
    }

    @Test
    fun `301 최대 라운드까지 못 끝내면 남은 점수가 적은 순`() {
        val g = DartsGame(3, DartsMode.X01, maxRounds = 4)
        var turns = 0
        while (!g.over) {
            when (g.current) {
                0 -> g.turn(S1, S1, S1)                 // 3씩 → 289
                1 -> g.turn(Ring.SINGLE_INNER to 5, OUT, OUT) // 20 → 281
                else -> g.turn(S1, S1, S1)             // 289 (0번과 같음)
            }
            turns++
        }
        assertEquals(12, turns)
        assertTrue(g.endedByRoundLimit)
        assertEquals(289, g.remaining[0])
        assertEquals(281, g.remaining[1])
        assertArrayEquals(intArrayOf(2, 1, 2), g.ranks())
    }

    @Test
    fun `301 일부만 끝낸 뒤 최대 라운드 - 끝낸 사람 먼저, 나머지는 남은 점수 순`() {
        val g = DartsGame(4, DartsMode.X01, start = 60, maxRounds = 2)
        g.turn(S1, S1, S1)          // 0: 57
        g.turn(Ring.TRIPLE to 20)  // 1: 끝 (1등)
        g.turn(Ring.SINGLE_INNER to 10, OUT, OUT) // 2: 50
        g.turn(OUT, OUT, OUT)       // 3: 60
        g.turn(S1, S1, S1)          // 0: 54
        g.turn(Ring.SINGLE_INNER to 10, OUT, OUT) // 2: 40
        assertFalse(g.over)
        g.turn(OUT, OUT, OUT)       // 3: 60 → 다 2번씩 → 끝
        assertTrue(g.over)
        assertTrue(g.endedByRoundLimit)
        assertArrayEquals(intArrayOf(3, 1, 2, 4), g.ranks())
    }

    @Test
    fun `301 순위 계산 - 같은 남은 점수는 같은 순위`() {
        val r = DartsGame.x01Ranks(intArrayOf(0, 40, 40, 0, 12), intArrayOf(1, -1, -1, 0, -1))
        assertArrayEquals(intArrayOf(2, 4, 4, 1, 3), r)
    }

    @Test
    fun `301 라운드 수는 지금 차례 사람 기준`() {
        val g = DartsGame(2, DartsMode.X01)
        assertEquals(1, g.round)
        assertEquals(15, g.totalRounds)
        g.turn(OUT, OUT, OUT)
        assertEquals(1, g.round)
        g.turn(OUT, OUT, OUT)
        assertEquals(2, g.round)
        val r = DartsGame(2)
        assertEquals(8, r.totalRounds)
    }

    @Test
    fun `꽂힌 자리 기억`() {
        val g = DartsGame(2)
        g.throwDart(10f, -20f)
        g.throwDart(-30f, 40f)
        assertEquals(2, g.dartInTurn)
        assertEquals(10f, g.turnX[0], 0f)
        assertEquals(40f, g.turnY[1], 0f)
        assertNotNull(g.turnHits[1])
    }
}
