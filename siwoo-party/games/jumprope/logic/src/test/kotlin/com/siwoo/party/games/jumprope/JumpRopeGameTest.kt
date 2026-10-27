package com.siwoo.party.games.jumprope

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JumpRopeGameTest {

    /** 알림 기록 */
    private class Log : JumpListener {
        val slaps = ArrayList<Int>()
        val caught = ArrayList<String>()
        val done = ArrayList<Int>()
        val levels = ArrayList<Int>()
        var over = -2
        override fun onSlap(c: Contact) { slaps.add(c.index) }
        override fun onCaught(player: Int, c: Contact, why: Miss) { caught.add("$player-${c.index}-$why") }
        override fun onContactDone(c: Contact) { done.add(c.index) }
        override fun onLevelUp(level: Int) { levels.add(level) }
        override fun onGameOver(winner: Int) { over = winner }
    }

    private fun game(n: Int, chance: Double = 0.0): Pair<JumpRopeGame, Log> {
        val g = JumpRopeGame(n, Random(7), chance)
        val log = Log()
        g.listener = log
        g.start(0L)
        return g to log
    }

    /** 다음 접촉에서 tapping 사람들이 딱 맞춰 누르고, 판정 구간이 끝날 때까지 시간을 흘린다 */
    private fun contact(g: JumpRopeGame, vararg tapping: Int) {
        val c = g.next
        for (p in tapping) assertEquals(Tap.HIT, g.tap(p, c.timeMs))
        g.update(c.timeMs + c.windowMs + 1)
    }

    @Test
    fun `판정 구간 안에 누르면 점프 성공 (양 끝 포함)`() {
        val (g, log) = game(3)
        val c = g.next
        assertEquals(0, c.index)
        assertEquals(GameConfig.LEAD_IN_MS, c.timeMs)
        assertEquals(GameConfig.WINDOW_START_MS, c.windowMs)
        assertEquals(Tap.HIT, g.tap(0, c.timeMs - c.windowMs))
        assertEquals(Tap.HIT, g.tap(1, c.timeMs))
        assertEquals(Tap.HIT, g.tap(2, c.timeMs + c.windowMs))
        g.update(c.timeMs + c.windowMs + 1)
        assertEquals(listOf(0), log.slaps)
        assertEquals(listOf(0), log.done)
        assertTrue(log.caught.isEmpty())
        assertArrayEquals(intArrayOf(1, 1, 1), g.jumps)
        assertEquals(1, g.pending)
        assertEquals(Phase.PLAYING, g.phase)
    }

    @Test
    fun `너무 일찍 누르면 바로 걸린다`() {
        val (g, log) = game(3)
        val c = g.next
        assertEquals(Tap.CAUGHT, g.tap(1, c.timeMs - c.windowMs - 1))
        assertTrue(g.out[1])
        assertEquals(0, g.outAt[1])
        assertEquals(Miss.EARLY, g.reason[1])
        assertEquals(listOf("1-0-EARLY"), log.caught)
        // 걸린 사람의 탭은 무시
        assertEquals(Tap.IGNORED, g.tap(1, c.timeMs))
        // 판정 전 아주 이른 탭(줄이 머리 위에 있을 때)도 걸림
        assertEquals(Tap.CAUGHT, g.tap(2, 10L))
        assertEquals(Miss.EARLY, g.reason[2])
    }

    @Test
    fun `안 누르면 걸리고 조금 늦게 누르면 늦었어요로 표시`() {
        val (g, log) = game(3)
        val c = g.next
        g.tap(0, c.timeMs)
        g.tap(1, c.timeMs)
        // 2 는 안 누름 → 구간이 끝나면 걸림
        g.update(c.timeMs + c.windowMs)
        assertFalse("구간 끝 순간까지는 아직 판정 전", g.out[2])
        g.update(c.timeMs + c.windowMs + 1)
        assertTrue(g.out[2])
        assertEquals(Miss.MISS, g.reason[2])
        assertEquals(listOf("2-0-MISS"), log.caught)
        // 판정 직후 누름 → 늦었어요
        assertEquals(Tap.LATE_NOTE, g.tap(2, c.timeMs + c.windowMs + 50))
        assertEquals(Miss.LATE, g.reason[2])
        // 프레임 없이 늦은 탭이 먼저 와도 판정 후 늦었어요 (구간 밖 = 걸림)
        val (g2, _) = game(2)
        val d = g2.next
        g2.tap(0, d.timeMs)
        assertEquals(Tap.LATE_NOTE, g2.tap(1, d.timeMs + d.windowMs + 5))
        assertTrue(g2.out[1])
        assertEquals(Miss.LATE, g2.reason[1])
        assertEquals(0, g2.winner)
    }

    @Test
    fun `같은 구간 두 번째 탭은 무시`() {
        val (g, _) = game(2)
        val c = g.next
        assertEquals(Tap.HIT, g.tap(0, c.timeMs - 30))
        assertEquals(Tap.IGNORED, g.tap(0, c.timeMs + 30))
        g.tap(1, c.timeMs)
        g.update(c.timeMs + c.windowMs + 1)
        assertEquals(1, g.jumps[0])
        assertFalse(g.out[0])
        // 다음 접촉은 새로 눌러야 한다
        val d = g.next
        assertEquals(1, d.index)
        assertEquals(Tap.HIT, g.tap(0, d.timeMs + 10))
    }

    @Test
    fun `10번마다 줄이 빨라지고 판정 구간이 조금 줄어든다`() {
        val (g, log) = game(2)
        for (k in 1 until 10) assertEquals(GameConfig.PERIOD_START_MS, g.contact(k).intervalMs)
        val p1 = JumpRopeGame.periodFor(1)
        assertTrue(p1 < GameConfig.PERIOD_START_MS)
        assertEquals(p1, g.contact(10).intervalMs)
        assertEquals(1, g.contact(10).level)
        assertEquals(JumpRopeGame.periodFor(2), g.contact(20).intervalMs)
        assertTrue(g.contact(20).intervalMs < g.contact(19).intervalMs)
        assertTrue(g.contact(20).windowMs < g.contact(0).windowMs)
        assertEquals(GameConfig.PERIOD_MIN_MS, JumpRopeGame.periodFor(30))
        assertTrue(JumpRopeGame.windowFor(30, 1000) >= GameConfig.WINDOW_MIN_MS)
        repeat(10) { contact(g, 0, 1) }
        assertEquals(listOf(1), log.levels)
        assertEquals(1, g.level)
        repeat(10) { contact(g, 0, 1) }
        assertEquals(listOf(1, 2), log.levels)
        assertArrayEquals(intArrayOf(20, 20), g.jumps)
    }

    @Test
    fun `쌩쌩이는 빠른 두 바퀴라 두 번 눌러야 한다`() {
        val (g, log) = game(3, chance = 1.0)
        val first = g.contact(GameConfig.DOUBLE_MIN_INDEX)
        val second = g.contact(GameConfig.DOUBLE_MIN_INDEX + 1)
        assertTrue(first.double && first.doubleFirst)
        assertTrue(second.double && !second.doubleFirst)
        assertEquals(JumpRopeGame.doubleInterval(GameConfig.PERIOD_START_MS), first.intervalMs)
        assertEquals(first.intervalMs, second.intervalMs)
        assertTrue("판정 구간이 겹치지 않음", first.windowMs + second.windowMs < second.intervalMs)
        assertFalse(g.contact(GameConfig.DOUBLE_MIN_INDEX + 2).double)
        assertTrue(g.contact(GameConfig.DOUBLE_MIN_INDEX + GameConfig.DOUBLE_GAP).doubleFirst)
        for (k in 0 until GameConfig.DOUBLE_MIN_INDEX) assertFalse(g.contact(k).double)
        repeat(GameConfig.DOUBLE_MIN_INDEX) { contact(g, 0, 1, 2) }
        // 쌩쌩이 첫 접촉: 모두 성공
        assertTrue(g.spinningDouble(first.timeMs - 10))
        contact(g, 0, 1, 2)
        // 두 번째 접촉: 2 는 한 번만 누르고 끝 → 걸림
        contact(g, 0, 1)
        assertTrue(g.out[2])
        assertEquals(second.index, g.outAt[2])
        assertEquals(Miss.MISS, g.reason[2])
        assertEquals("2-${second.index}-MISS", log.caught.last())
        assertFalse(g.out[0] || g.out[1])
        assertArrayEquals(intArrayOf(8, 8, 7), g.jumps)
    }

    @Test
    fun `같은 접촉에서 걸린 사람은 같은 순위`() {
        val (g, log) = game(3)
        contact(g, 0, 1, 2)
        // 1 은 너무 일찍, 2 는 안 누름 → 같은 접촉 1번
        val c = g.next
        assertEquals(Tap.CAUGHT, g.tap(1, c.timeMs - 500))
        contact(g, 0)
        assertEquals(Phase.OVER, g.phase)
        assertEquals(0, log.over)
        assertArrayEquals(intArrayOf(1, 2, 2), g.ranks())
    }

    @Test
    fun `남은 사람이 모두 같은 접촉에서 걸리면 함께 1위`() {
        val (g, log) = game(4)
        contact(g, 0, 1, 2) // 3 탈락
        contact(g, 1, 2)    // 0 탈락
        contact(g)          // 1·2 동시 탈락 → 끝
        assertEquals(Phase.OVER, g.phase)
        assertEquals(-1, g.winner)
        assertEquals(-1, log.over)
        assertArrayEquals(intArrayOf(3, 1, 1, 4), g.ranks())
    }

    @Test
    fun `최후 1인이 승리하고 탈락 역순으로 순위`() {
        val (g, log) = game(4)
        contact(g, 0, 1, 2)       // 접촉 0: 3 탈락
        assertEquals(Phase.PLAYING, g.phase)
        val c = g.next
        g.tap(1, c.timeMs - c.windowMs - 200) // 접촉 1: 1 너무 일찍
        contact(g, 0, 2)
        assertEquals(2, g.alive)
        contact(g, 2)             // 접촉 2: 0 탈락 → 2 혼자 남음
        assertEquals(Phase.OVER, g.phase)
        assertEquals(2, g.winner)
        assertEquals(2, log.over)
        assertArrayEquals(intArrayOf(2, 3, 1, 4), g.ranks())
        assertEquals(listOf(0, 1, 2), log.done)
        // 끝난 뒤 탭·시간은 무시
        assertEquals(Tap.IGNORED, g.tap(2, 99_999))
        g.update(99_999)
        assertEquals(listOf(0, 1, 2), log.done)
        assertNull(g.reason[2])
    }

    @Test
    fun `줄 위상과 일시정지 뒤 멈춤`() {
        val g = JumpRopeGame(2, Random(1), 0.0)
        assertEquals(0.5f, g.ropePhase(0), 0.001f)
        g.start(1000)
        assertEquals(0.5f, g.ropePhase(1000), 0.001f)
        assertEquals(0.75f, g.ropePhase(1500), 0.001f)
        assertEquals(0f, g.ropePhase(2000), 0.001f)
        assertEquals(0.5f, g.ropePhase(2600), 0.001f)
        // 2600 에 1500ms 멈춤 → 그동안 위상 그대로, 다음 접촉이 1500 밀림
        g.tap(0, 2000)
        g.tap(1, 2000)
        g.update(2600)
        assertEquals(1, g.pending)
        g.hold(2600, 1500)
        assertTrue(g.holding(3000))
        assertEquals(0.5f, g.ropePhase(3500), 0.001f)
        assertEquals(Tap.IGNORED, g.tap(0, 3500))
        assertEquals(2600L + 600 + 1500, g.nextContactAt(4100))
        assertEquals(0.75f, g.ropePhase(4100 + 300), 0.001f)
        // 밀린 시각에 맞춰 누르면 성공
        assertEquals(Tap.HIT, g.tap(0, 4700))
        assertEquals(Tap.HIT, g.tap(1, 4700 + 100))
        g.update(4700 + 200)
        assertEquals(2, g.pending)
        assertFalse(g.out[0] || g.out[1])
    }
}
