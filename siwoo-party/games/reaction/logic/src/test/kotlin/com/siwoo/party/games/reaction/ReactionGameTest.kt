package com.siwoo.party.games.reaction

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReactionGameTest {

    /** 한 라운드를 끝까지: 순서대로 (플레이어, ms) 누름 */
    private fun playRound(g: ReactionGame, vararg presses: Pair<Int, Long>) {
        g.startRound()
        g.go()
        for ((p, ms) in presses) g.touch(p, ms)
        assertTrue(g.endRound())
    }

    @Test fun `대기 시간은 1500~5000ms, 속임수는 약 20%`() {
        val g = ReactionGame(2, Random(3))
        var fakes = 0
        repeat(5000) {
            val plan = g.startRound()
            if (plan.hasFake) {
                fakes++
                assertTrue(plan.fakeAtMs in GameConfig.WAIT_MIN_MS..GameConfig.WAIT_MAX_MS)
                assertTrue("속임수가 먼저, 끝난 뒤 지금!", plan.goAtMs >= plan.fakeAtMs + GameConfig.FAKE_SHOW_MS + GameConfig.AFTER_FAKE_MIN_MS)
            } else {
                assertTrue(plan.goAtMs in GameConfig.WAIT_MIN_MS..GameConfig.WAIT_MAX_MS)
            }
        }
        assertEquals(0.2, fakes / 5000.0, 0.02)
    }

    @Test fun `초록 전에 누르면 실격, 그 라운드 터치는 무시`() {
        val g = ReactionGame(3, Random(1))
        g.startRound()
        val r = g.touch(1, 0)
        assertTrue(r is Touch.Disqualified && !r.allOut)
        assertTrue(g.disqualified[1])
        g.go()
        assertSame("실격자는 무시", Touch.Ignored, g.touch(1, 100))
        val w = g.touch(2, 250)
        assertTrue(w is Touch.Won && w.player == 2 && w.ms == 250L)
        val late = g.touch(0, 400)
        assertTrue(late is Touch.Late)
        assertSame("두 번 누르기 무시", Touch.Ignored, g.touch(0, 500))
        assertTrue(g.allResponded())
        assertTrue(g.endRound())
        assertArrayEquals(intArrayOf(0, 0, 1), g.wins)
    }

    @Test fun `속임수 중에 누르면 실격`() {
        val g = ReactionGame(2, Random(1))
        g.startRound()
        g.showFake()
        assertEquals(Phase.FAKE, g.phase)
        assertTrue(g.touch(0, 0) is Touch.Disqualified)
        g.endFake()
        assertEquals(Phase.WAITING, g.phase)
        g.go()
        assertTrue(g.touch(1, 300) is Touch.Won)
    }

    @Test fun `전원 실격이면 라운드 무효 후 다시`() {
        val g = ReactionGame(2, Random(1))
        g.startRound()
        g.touch(0, 0)
        val r = g.touch(1, 0)
        assertTrue(r is Touch.Disqualified && r.allOut)
        assertEquals(Phase.IDLE, g.phase)
        assertEquals("무효 라운드는 세지 않음", 0, g.roundsDone)
        g.startRound()
        assertFalse("다시 시작하면 실격 해제", g.disqualified.any { it })
        assertEquals(1, g.roundNumber)
    }

    @Test fun `지금 뒤 아무도 안 누르면 무효`() {
        val g = ReactionGame(2, Random(1))
        g.startRound(); g.go()
        assertFalse(g.endRound())
        assertEquals(0, g.roundsDone)
    }

    @Test fun `5라운드 후 끝, 승수 순위`() {
        val g = ReactionGame(3, Random(2))
        repeat(3) { playRound(g, 0 to 200L, 1 to 300L) }
        repeat(2) { playRound(g, 1 to 250L) }
        assertTrue(g.gameOver)
        assertArrayEquals(intArrayOf(1, 2, 3), g.finalRanks()) // A 3승, B 2승, C 0승(기록 없음)
    }

    @Test fun `승수 동점이면 평균 반응 시간이 빠른 쪽이 위`() {
        val g = ReactionGame(2, Random(2))
        playRound(g, 0 to 200L, 1 to 260L)
        playRound(g, 1 to 300L, 0 to 320L)
        // 승 1:1, 평균 A 260 / B 280
        assertEquals(260L, g.averageMs(0))
        assertArrayEquals(intArrayOf(1, 2), g.finalRanks())
    }

    @Test fun `승수와 평균이 모두 같으면 공동 순위`() {
        val g = ReactionGame(4, Random(2))
        playRound(g, 0 to 200L, 1 to 300L)
        playRound(g, 1 to 200L, 0 to 300L)
        // A·B 1승 평균 250, C·D 기록 없음
        assertNull(g.averageMs(2))
        assertArrayEquals(intArrayOf(1, 1, 3, 3), g.finalRanks())
    }
}
