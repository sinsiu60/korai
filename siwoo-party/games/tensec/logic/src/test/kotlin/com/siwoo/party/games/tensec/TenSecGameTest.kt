package com.siwoo.party.games.tensec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TenSecGameTest {

    /** 한 라운드: 시작 시각 1000 에 begin, 각자 (플레이어, 경과 ms) 로 누름 → 마감 */
    private fun playRound(g: TenSecGame, vararg taps: Pair<Int, Long>) {
        g.startRound()
        g.begin(1000)
        for ((p, ms) in taps) assertSame(Tap.RECORDED, g.tap(p, 1000 + ms))
        if (!g.allIn) g.fillMissing()
        g.endRound()
        g.nextRound()
    }

    @Test fun `목표 시간은 5~15초 정수, 양 끝도 나온다`() {
        val all = HashSet<Int>()
        repeat(500) { seed ->
            val g = TenSecGame(2, Random(seed))
            val s = g.startRound()
            assertTrue(s in GameConfig.TARGET_MIN_SEC..GameConfig.TARGET_MAX_SEC)
            assertEquals(s * 100L, g.targetCs)
            all.add(s)
        }
        assertEquals((GameConfig.TARGET_MIN_SEC..GameConfig.TARGET_MAX_SEC).toSet(), all)
    }

    @Test fun `기록은 0점01초 단위 내림, 차이는 절댓값`() {
        val g = TenSecGame(3, Random(1))
        val target = g.startRound()
        g.begin(5000)
        g.tap(0, 5000 + target * 1000L + 129) // +1.29초 (9ms 버림)
        g.tap(1, 5000 + target * 1000L - 300) // -0.30초
        g.tap(2, 5000 + target * 1000L)        // 딱!
        assertEquals(target * 100L + 12, g.recordCs[0])
        assertEquals(12L, g.diffCs(0))
        assertEquals(30L, g.diffCs(1))
        assertEquals(0L, g.diffCs(2))
        assertArrayEquals(booleanArrayOf(false, false, true), g.closest())
        assertArrayEquals("큰 차이부터 공개, 가장 가까운 사람이 마지막", intArrayOf(1, 0, 2), g.revealOrder())
    }

    @Test fun `한 라운드에 한 사람은 한 번만 기록`() {
        val g = TenSecGame(2, Random(1))
        g.startRound()
        assertSame("시작 전 터치 무시", Tap.IGNORED, g.tap(0, 0))
        g.begin(0)
        assertSame(Tap.RECORDED, g.tap(0, 4000))
        assertSame("두 번째 터치 무시", Tap.IGNORED, g.tap(0, 9000))
        assertEquals(400L, g.recordCs[0])
        assertFalse(g.allIn)
        assertSame(Tap.IGNORED, g.tap(5, 100))
        assertSame(Tap.RECORDED, g.tap(1, 6000))
        assertTrue(g.allIn)
        g.endRound()
        assertSame("마감 뒤 터치 무시", Tap.IGNORED, g.tap(1, 7000))
        assertEquals(600L, g.recordCs[1])
    }

    @Test fun `끝까지 안 누르면 목표+10초로 자동 기록`() {
        val g = TenSecGame(3, Random(2))
        val target = g.startRound()
        g.begin(0)
        assertEquals(target * 1000L + GameConfig.CAP_EXTRA_MS, g.capMs)
        g.tap(0, target * 1000L)
        assertEquals("두 명 자동 기록", 2, g.fillMissing())
        assertTrue(g.allIn)
        assertTrue(g.autoCapped[1] && g.autoCapped[2] && !g.autoCapped[0])
        assertEquals(GameConfig.CAP_EXTRA_MS / 10, g.diffCs(1))
        assertEquals(0, g.fillMissing())
        // 상한을 넘겨 들어온 터치도 상한으로 자른다
        val h = TenSecGame(2, Random(2))
        h.startRound(); h.begin(0)
        h.tap(0, h.capMs + 5000)
        assertEquals(h.capMs / 10, h.recordCs[0])
    }

    @Test fun `라운드 흐름 - 3라운드 후 끝`() {
        val g = TenSecGame(2, Random(3))
        assertEquals(Phase.IDLE, g.phase)
        assertEquals(1, g.roundNumber)
        g.startRound()
        assertEquals(Phase.READY, g.phase)
        try { g.startRound(); error("진행 중 새 라운드 불가") } catch (e: IllegalStateException) { }
        g.begin(0)
        assertEquals(Phase.RUNNING, g.phase)
        assertEquals(1500L, g.elapsedMs(1500))
        g.tap(0, 7000)
        try { g.endRound(); error("전원 기록 전 마감 불가") } catch (e: IllegalStateException) { }
        g.tap(1, 8000)
        g.endRound()
        assertEquals(Phase.DONE, g.phase)
        assertEquals(1, g.roundsDone)
        g.nextRound()
        assertEquals(Phase.IDLE, g.phase)
        assertEquals(2, g.roundNumber)
        playRound(g, 0 to 5000L, 1 to 5000L)
        assertFalse(g.gameOver)
        playRound(g, 0 to 5000L, 1 to 5000L)
        assertTrue(g.gameOver)
        assertEquals(3, g.roundNumber)
        try { g.startRound(); error("끝난 뒤 시작 불가") } catch (e: IllegalStateException) { }
    }

    @Test fun `차이 합이 작은 순 순위, 같으면 공동`() {
        // 목표를 알 수 없으므로 목표 기준으로 누른다
        val g = TenSecGame(4, Random(9))
        repeat(GameConfig.ROUNDS) { r ->
            val t = g.startRound() * 1000L
            g.begin(0)
            g.tap(0, t + 100)            // 0.10
            g.tap(1, t - 100)            // 0.10 (빠르게도 같은 차이)
            g.tap(2, t + 500 + r * 10)   // 0.50, 0.51, 0.52
            g.tap(3, t)                  // 0
            g.endRound(); g.nextRound()
        }
        assertArrayEquals(longArrayOf(30, 30, 153, 0), g.totalCs)
        assertArrayEquals(intArrayOf(2, 2, 4, 1), g.ranks())
        assertEquals(51L, g.roundDiffCs[1][2])
    }

    @Test fun `모두 같으면 모두 1위`() {
        val g = TenSecGame(3, Random(4))
        repeat(GameConfig.ROUNDS) {
            val t = g.startRound() * 1000L
            g.begin(0)
            for (p in 0 until 3) g.tap(p, t + 250)
            g.endRound(); g.nextRound()
        }
        assertArrayEquals(intArrayOf(1, 1, 1), g.ranks())
        assertArrayEquals(booleanArrayOf(true, true, true), g.closest())
    }
}
