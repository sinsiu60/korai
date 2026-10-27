package com.siwoo.party.games.redlight

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RedlightGameTest {

    private fun game(n: Int, seed: Int = 1) = RedlightGame(n, Random(seed)).also { it.start(0) }

    /** 지금 바퀴에서 술래가 돌아보는 시각 (게임 시계) */
    private fun turnTime(g: RedlightGame) = g.cycleStart + g.plan.turnAt

    /**
     * 안전하게 진행: holders 는 외치는 동안 누르고, 술래가 돌아보면 바로 손을 뗀다 (10ms 단위).
     * startAt[p] 이후부터 참여.
     */
    private fun playSafe(g: RedlightGame, holders: IntArray, until: Long, startAt: LongArray = LongArray(holders.size)) {
        var t = g.cursor
        while (t < until && !g.isOver) {
            t += 10
            g.update(t)
            for ((k, p) in holders.withIndex()) {
                if (t < startAt[k]) continue
                if (g.phase == Phase.CHANT && !g.holding[p]) g.press(p, t)
                if (g.phase == Phase.LOOK && g.holding[p]) g.release(p, t)
            }
        }
    }

    @Test
    fun `누르고 있는 동안만 전진하고 술래가 쳐다보면 멈춘다`() {
        val g = game(2)
        g.press(0, 0)
        g.update(1000)
        assertEquals(1000.0 / GameConfig.TRACK_MS, g.progress[0], 1e-9)
        assertEquals(0.0, g.progress[1], 0.0)
        g.release(0, 1000)
        g.update(1500)
        assertEquals(1000.0 / GameConfig.TRACK_MS, g.progress[0], 1e-9)
        // 돌아보는 순간 다시 누르고 유예 안에 뗌 → 이동 없음
        val turn = turnTime(g)
        g.press(0, turn)
        assertEquals(Phase.LOOK, g.phase)
        g.release(0, turn + 100)
        assertEquals(1000.0 / GameConfig.TRACK_MS, g.progress[0], 1e-9)
    }

    @Test
    fun `유예 안에 손을 떼면 안전, 유예 뒤에도 누르고 있으면 출발점으로`() {
        val g = game(3, seed = 7)
        g.press(0, 0)
        g.press(1, 0)
        val turn = turnTime(g)
        g.update(turn)
        assertEquals(Phase.LOOK, g.phase)
        val before = g.progress[0]
        assertTrue(before > 0.0)
        // 0번: 유예 끝나기 1ms 전에 뗌 → 안전
        g.release(0, turn + GameConfig.GRACE_MS - 1)
        // 1번: 계속 누름 → 유예 끝에 걸림
        g.update(turn + GameConfig.GRACE_MS + 50)
        assertEquals(before, g.progress[0], 1e-9)
        assertEquals(0, g.caughtCount[0])
        assertEquals(0.0, g.progress[1], 0.0)
        assertEquals(1, g.caughtCount[1])
        assertEquals(before, g.caughtFrom[1], 1e-9)
        assertTrue(g.watching)
    }

    @Test
    fun `유예가 끝나는 순간 떼면 늦다`() {
        val g = game(2, seed = 3)
        g.press(0, 0)
        val turn = turnTime(g)
        g.release(0, turn + GameConfig.GRACE_MS)
        assertEquals(1, g.caughtCount[0])
        assertEquals(0.0, g.progress[0], 0.0)
    }

    @Test
    fun `쳐다보는 중에 새로 누르면 바로 출발점으로, 다음 바퀴에 다시 출발`() {
        val g = game(2, seed = 4)
        g.press(1, 0)
        val turn = turnTime(g)
        g.release(1, turn + 10)
        val saved = g.progress[1]
        assertTrue(saved > 0.0)
        // 유예 안에 누르면 아직 괜찮다
        assertFalse(g.press(1, turn + 100))
        g.release(1, turn + 200)
        assertEquals(saved, g.progress[1], 1e-9)
        // 유예 뒤 누름 → 걸림
        assertTrue(g.press(1, turn + GameConfig.GRACE_MS + 100))
        assertEquals(0.0, g.progress[1], 0.0)
        assertEquals(1, g.caughtCount[1])
        // 계속 누르고 있어도 다시 외치기 시작하면 출발점부터 전진
        val nextCycle = turn + g.plan.lookMs
        g.update(nextCycle + 500)
        assertEquals(Phase.CHANT, g.phase)
        assertEquals(1, g.cycle)
        assertEquals(500.0 / GameConfig.TRACK_MS, g.progress[1], 1e-9)
    }

    @Test
    fun `글자는 한 글자씩 나타나고 마지막 글자 뒤 돌아본다`() {
        val g = game(2, seed = 9)
        assertEquals(0, g.lettersShown)
        g.update(g.cycleStart + g.plan.letterAt[0])
        assertEquals(1, g.lettersShown)
        g.update(g.cycleStart + g.plan.letterAt[4] + 1)
        assertEquals(5, g.lettersShown)
        g.update(g.cycleStart + g.plan.letterAt[9])
        assertEquals(10, g.lettersShown)
        assertEquals(Phase.CHANT, g.phase)
        g.update(turnTime(g))
        assertEquals(Phase.LOOK, g.phase)
        assertFalse("돌아본 직후는 유예", g.watching)
    }

    @Test
    fun `바퀴마다 글자 속도가 다르고 중간에 빨라지기도 한다`() {
        val r = Random(42)
        val plans = List(60) { CyclePlan.random(r) }
        for (p in plans) {
            assertEquals(GameConfig.PHRASE_LEN, p.letterAt.size)
            for (i in 0 until GameConfig.PHRASE_LEN) assertTrue(p.interval(i) > 0)
            assertEquals(p.letterAt.last() + GameConfig.TURN_DELAY_MS, p.turnAt)
            assertTrue(p.lookMs in GameConfig.LOOK_MIN_MS..GameConfig.LOOK_MAX_MS)
        }
        assertTrue("바퀴마다 다른 속도", plans.map { it.turnAt }.distinct().size > 30)
        val fast = plans.filter { it.speedUpAt >= 0 }
        assertTrue("빨라지는 바퀴가 있다", fast.isNotEmpty())
        assertTrue("안 빨라지는 바퀴도 있다", fast.size < plans.size)
        for (p in fast) {
            val k = p.speedUpAt
            assertTrue(k in GameConfig.SPEEDUP_FROM..GameConfig.SPEEDUP_TO)
            if (k > 0) assertTrue("빨라진 뒤 간격이 더 짧다", p.interval(k) < p.interval(k - 1))
        }
    }

    @Test
    fun `먼저 도착한 순서대로 순위, 한 명 남으면 끝`() {
        val g = game(3, seed = 11)
        // 0번 처음부터, 1번 0.8초 늦게 참여, 2번 안 함
        playSafe(g, intArrayOf(0, 1), 120_000, longArrayOf(0, 800))
        assertTrue(g.isOver)
        assertFalse(g.timeUp)
        assertEquals(listOf(0, 1), g.arrivalOrder)
        assertTrue(g.arrivedAt[0] < g.arrivedAt[1])
        assertEquals(1.0, g.progress[0], 0.0)
        assertFalse(g.arrived(2))
        assertArrayEquals(intArrayOf(1, 2, 3), g.finalRanks())
    }

    @Test
    fun `2인은 먼저 도착하면 바로 끝`() {
        val g = game(2, seed = 12)
        playSafe(g, intArrayOf(1), 120_000)
        assertTrue(g.isOver)
        assertEquals(listOf(1), g.arrivalOrder)
        assertArrayEquals(intArrayOf(2, 1), g.finalRanks())
    }

    @Test
    fun `늦게 누른 만큼 뒤처지고 같이 누르면 같은 위치`() {
        val g = game(4, seed = 2)
        g.press(0, 0)
        g.press(1, 50)
        g.press(2, 50)
        val turn = turnTime(g)
        g.release(0, turn); g.release(1, turn); g.release(2, turn)
        assertTrue(g.progress[0] > g.progress[1])
        assertEquals(g.progress[1], g.progress[2], 1e-12)
    }

    @Test
    fun `제한 시간이면 진행 거리 순, 같은 거리는 같은 순위`() {
        val g = game(4, seed = 5)
        // 0·1번은 똑같이 0.5초씩, 2·3번은 안 움직임
        g.press(0, 0); g.press(1, 0)
        g.release(0, 500); g.release(1, 500)
        g.update(GameConfig.TIME_LIMIT_MS + 10)
        assertTrue(g.isOver)
        assertTrue(g.timeUp)
        assertEquals(0L, g.remainingMs())
        assertArrayEquals(intArrayOf(1, 1, 3, 3), g.finalRanks())
    }

    @Test
    fun `제한 시간이어도 도착한 사람이 거리보다 앞선다`() {
        val g = game(4, seed = 6)
        playSafe(g, intArrayOf(2), 60_000)
        assertTrue(g.arrived(2))
        assertFalse(g.isOver)
        // 다음 바퀴가 막 시작될 때 0번만 0.1초 전진
        while (!(g.phase == Phase.CHANT && g.cursor - g.cycleStart < 20)) g.update(g.cursor + 10)
        val t0 = g.cursor
        g.press(0, t0)
        g.release(0, t0 + 100)
        assertEquals(100.0 / GameConfig.TRACK_MS, g.progress[0], 1e-9)
        // 아무도 더 안 움직이고 시간 종료
        g.update(GameConfig.TIME_LIMIT_MS)
        assertTrue(g.timeUp)
        val r = g.finalRanks()
        assertEquals(1, r[2])
        assertEquals(2, r[0])
        assertEquals(3, r[1])
        assertEquals(3, r[3])
    }

    @Test
    fun `나눠서 계산해도 한 번에 계산해도 결과가 같다`() {
        val a = game(2, seed = 21)
        val b = game(2, seed = 21)
        a.press(0, 0); b.press(0, 0)
        var t = 0L
        while (t < 7000) { t += 7; a.update(t) }
        b.update(t)
        assertEquals(a.phase, b.phase)
        assertEquals(a.progress[0], b.progress[0], 1e-9)
        assertEquals(a.caughtCount[0], b.caughtCount[0])
        assertEquals(a.cycle, b.cycle)
    }

    @Test
    fun `시작 전과 끝난 뒤의 누름은 무시`() {
        val g = RedlightGame(2, Random(1))
        assertFalse(g.press(0, 0))
        assertFalse(g.holding[0])
        g.start(0)
        g.update(GameConfig.TIME_LIMIT_MS)
        assertTrue(g.isOver)
        assertFalse(g.press(0, GameConfig.TIME_LIMIT_MS + 5))
        assertFalse(g.holding[0])
        assertNotEquals(Phase.CHANT, g.phase)
    }
}
