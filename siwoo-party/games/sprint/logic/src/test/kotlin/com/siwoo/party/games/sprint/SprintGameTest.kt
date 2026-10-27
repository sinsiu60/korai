package com.siwoo.party.games.sprint

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SprintGameTest {

    private val goAt = 10_000L

    /** 준비 → 땅(goAt) 까지 진행한 게임 */
    private fun running(n: Int): SprintGame {
        val g = SprintGame(n)
        g.arm()
        g.signal(goAt)
        return g
    }

    /** i 가 at 부터 gap 간격으로 번갈아 count 걸음 (왼발부터). 마지막 누른 시각 */
    private fun run(g: SprintGame, i: Int, count: Int, at: Long, gap: Long = 100L, startFoot: Foot = Foot.LEFT): Long {
        var t = at
        var f = startFoot
        repeat(count) {
            g.press(i, f, t)
            f = if (f == Foot.LEFT) Foot.RIGHT else Foot.LEFT
            t += gap
        }
        return t - gap
    }

    @Test
    fun `60걸음이면 100m`() {
        val g = running(2)
        assertEquals(60, g.stepsToFinish)
        assertTrue(59 * GameConfig.STEP_CM < GameConfig.TRACK_CM)
        assertTrue(60 * GameConfig.STEP_CM >= GameConfig.TRACK_CM)
    }

    @Test
    fun `같은 발 연속은 한 번만 인정`() {
        val g = running(2)
        assertEquals(Press.STEP, g.press(0, Foot.LEFT, goAt + 100))
        assertEquals(Press.SAME_FOOT, g.press(0, Foot.LEFT, goAt + 200))
        assertEquals(1, g.steps[0])
        assertEquals(GameConfig.STEP_CM, g.distanceCm(0))
        // 왼발 다음 오른발은 인정
        assertEquals(Press.STEP, g.press(0, Foot.RIGHT, goAt + 300))
        assertEquals(2, g.steps[0])
        assertEquals(Press.SAME_FOOT, g.press(0, Foot.RIGHT, goAt + 400))
        assertEquals(Press.STEP, g.press(0, Foot.LEFT, goAt + 500))
        assertEquals(3, g.steps[0])
        assertEquals(Foot.LEFT, g.lastFoot[0])
        // 다른 사람과는 무관
        assertEquals(0, g.steps[1])
    }

    @Test
    fun `첫 걸음은 오른발부터도 된다`() {
        val g = running(2)
        assertEquals(Press.STEP, g.press(1, Foot.RIGHT, goAt + 50))
        assertEquals(Press.STEP, g.press(1, Foot.LEFT, goAt + 90))
        assertEquals(2, g.steps[1])
    }

    @Test
    fun `소개 중에는 눌러도 무시`() {
        val g = SprintGame(3)
        assertEquals(Press.IGNORED, g.press(0, Foot.LEFT, 5))
        assertFalse(g.falseStart[0])
        assertEquals(0, g.steps[0])
    }

    @Test
    fun `부정 출발은 신호부터 1초 정지`() {
        val g = SprintGame(3)
        g.arm()
        assertEquals(Press.FALSE_START, g.press(1, Foot.LEFT, goAt - 500))
        assertEquals(Press.FALSE_START, g.press(1, Foot.RIGHT, goAt - 400)) // 여러 번 해도 벌칙은 한 번
        assertTrue(g.falseStart[1])
        assertFalse(g.falseStart[0])
        assertEquals(0, g.steps[1])
        assertEquals(null, g.lastFoot[1])
        g.signal(goAt)
        val until = goAt + GameConfig.FALSE_START_PENALTY_MS
        assertEquals(until, g.frozenUntil(1))
        assertTrue(g.frozen(1, goAt + 10))
        assertFalse(g.frozen(0, goAt + 10))
        // 벌칙 중 누름은 전진 없음 (발 기록도 안 남음)
        assertEquals(Press.FROZEN, g.press(1, Foot.LEFT, goAt + 100))
        assertEquals(Press.FROZEN, g.press(1, Foot.RIGHT, until - 1))
        assertEquals(0, g.steps[1])
        assertEquals(null, g.lastFoot[1])
        // 다른 사람은 바로 달린다
        assertEquals(Press.STEP, g.press(0, Foot.LEFT, goAt + 100))
        // 벌칙이 끝나면 전진
        assertFalse(g.frozen(1, until))
        assertEquals(Press.STEP, g.press(1, Foot.RIGHT, until))
        assertEquals(Press.STEP, g.press(1, Foot.LEFT, until + 80))
        assertEquals(2, g.steps[1])
    }

    @Test
    fun `신호보다 앞선 터치가 늦게 처리되면 부정 출발`() {
        val g = running(2)
        assertEquals(Press.FALSE_START, g.press(0, Foot.LEFT, goAt - 3))
        assertTrue(g.falseStart[0])
        assertEquals(Press.FROZEN, g.press(0, Foot.RIGHT, goAt + 500))
        assertEquals(Press.STEP, g.press(0, Foot.RIGHT, goAt + GameConfig.FALSE_START_PENALTY_MS))
    }

    @Test
    fun `도착 순서대로 순위와 기록`() {
        val g = running(4)
        // 2번이 가장 빠르고, 0번, 3번 순 — 1번은 아직 달리는 중
        val end2 = run(g, 2, 60, goAt + 50, 90)
        val end0 = run(g, 0, 60, goAt + 60, 100)
        val end3 = run(g, 3, 60, goAt + 70, 120)
        run(g, 1, 30, goAt + 40, 100)
        assertEquals(end2 - goAt, g.finishMs[2])
        assertEquals(end0 - goAt, g.finishMs[0])
        assertEquals(end3 - goAt, g.finishMs[3])
        assertEquals(-1L, g.finishMs[1])
        assertEquals(1, g.finishRank(2))
        assertEquals(2, g.finishRank(0))
        assertEquals(3, g.finishRank(3))
        assertEquals(0, g.finishRank(1))
        assertEquals(Phase.RUNNING, g.phase)
        run(g, 1, 30, goAt + 9000, 100, Foot.LEFT) // 30걸음 더 (31번째가 왼발이라 번갈아 이어짐)
        assertTrue(g.finished(1))
        assertEquals(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(2, 4, 1, 3), g.ranks())
        assertEquals(g.finishMs[1], g.elapsed(goAt + 99_000))
        assertEquals("5.36초", SprintGame.formatRecord(g.finishMs[2]))
    }

    @Test
    fun `같은 ms 에 도착하면 같은 순위`() {
        val g = running(3)
        run(g, 0, 60, goAt + 100, 100)
        run(g, 1, 60, goAt + 100, 100)
        run(g, 2, 60, goAt + 100, 110)
        assertEquals(g.finishMs[0], g.finishMs[1])
        assertEquals(1, g.finishRank(0))
        assertEquals(1, g.finishRank(1))
        assertArrayEquals(intArrayOf(1, 1, 3), g.ranks())
    }

    @Test
    fun `제한 시간이면 남은 사람은 거리순`() {
        val g = running(4)
        run(g, 3, 60, goAt + 100, 100)
        run(g, 0, 20, goAt + 100, 100)
        run(g, 1, 45, goAt + 100, 100)
        run(g, 2, 20, goAt + 100, 100)
        assertFalse(g.update(goAt + GameConfig.TIME_LIMIT_MS - 1))
        assertEquals(Phase.RUNNING, g.phase)
        assertTrue(g.update(goAt + GameConfig.TIME_LIMIT_MS))
        assertTrue(g.timedOut)
        assertEquals(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(3, 2, 3, 1), g.ranks())
        assertEquals(GameConfig.TIME_LIMIT_MS, g.elapsed(goAt + 99_999))
        // 끝난 뒤 누름은 무시
        assertEquals(Press.IGNORED, g.press(0, Foot.RIGHT, goAt + GameConfig.TIME_LIMIT_MS + 5))
        assertEquals(20, g.steps[0])
        assertFalse(g.timeUp())
    }

    @Test
    fun `아무도 못 들어오고 거리도 같으면 모두 1위`() {
        val g = running(2)
        run(g, 0, 10, goAt + 100)
        run(g, 1, 10, goAt + 150)
        g.timeUp()
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    @Test
    fun `도착 뒤 누름은 무시`() {
        val g = running(2)
        val end = run(g, 0, 60, goAt + 100, 100)
        val rec = g.finishMs[0]
        assertEquals(end - goAt, rec)
        assertEquals(Press.IGNORED, g.press(0, Foot.LEFT, end + 100))
        assertEquals(Press.IGNORED, g.press(0, Foot.RIGHT, end + 200))
        assertEquals(60, g.steps[0])
        assertEquals(GameConfig.TRACK_CM, g.distanceCm(0))
        assertEquals(rec, g.finishMs[0])
        assertEquals(1, g.arrival[0])
        assertEquals(Phase.RUNNING, g.phase)
    }

    @Test
    fun `경주 시계`() {
        val g = SprintGame(2)
        assertEquals(0L, g.elapsed(123))
        g.arm()
        g.signal(goAt)
        assertEquals(0L, g.elapsed(goAt - 5))
        assertEquals(1234L, g.elapsed(goAt + 1234))
        assertEquals(GameConfig.TIME_LIMIT_MS, g.elapsed(goAt + 999_999))
    }

    @Test
    fun `기록 글자`() {
        assertEquals("12.34초", SprintGame.formatRecord(12_345))
        assertEquals("9.05초", SprintGame.formatRecord(9_050))
        assertEquals("0.00초", SprintGame.formatRecord(-3))
        assertEquals("60m", SprintGame.formatMeters(6_012))
    }
}
