package com.siwoo.party.games.updown

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UpDownGameTest {

    private fun game(n: Int = 3, bomb: Int = 42, first: Int = 0): UpDownGame {
        val g = UpDownGame(n, Random(1), first)
        g.startRound(bomb)
        return g
    }

    // ── 범위 축소 ──

    @Test fun `처음 범위는 1부터 100`() {
        val g = game()
        assertEquals(1, g.minAllowed)
        assertEquals(100, g.maxAllowed)
        assertEquals(100, g.remaining)
        assertFalse(g.isForced)
    }

    @Test fun `폭탄보다 크면 위쪽이 잘림`() {
        val g = game(bomb = 42)
        val s = g.guess(70)!!
        assertEquals(Verdict.DOWN, s.verdict)
        assertEquals(101, s.high) // 잘리기 전
        assertEquals(70, g.high)
        assertEquals(69, g.maxAllowed)
        assertEquals(1, g.minAllowed)
    }

    @Test fun `폭탄보다 작으면 아래쪽이 잘림`() {
        val g = game(bomb = 42)
        val s = g.guess(10)!!
        assertEquals(Verdict.UP, s.verdict)
        assertEquals(0, s.low)
        assertEquals(10, g.low)
        assertEquals(11, g.minAllowed)
        assertEquals(100, g.maxAllowed)
    }

    @Test fun `양쪽이 차례로 잘림`() {
        val g = game(bomb = 42)
        g.guess(50); g.guess(30); g.guess(45); g.guess(40)
        assertEquals(41, g.minAllowed)
        assertEquals(44, g.maxAllowed)
        assertEquals(4, g.remaining)
    }

    // ── 폭탄 판정 ──

    @Test fun `폭탄을 말하면 해골 +1 그리고 라운드 끝`() {
        val g = game(bomb = 42)
        g.guess(50) // 0번
        val s = g.guess(42)!! // 1번
        assertEquals(Verdict.BOOM, s.verdict)
        assertEquals(1, s.player)
        assertTrue(s.roundOver)
        assertFalse(s.gameOver)
        assertArrayEquals(intArrayOf(0, 1, 0), g.skulls)
        assertEquals(Phase.READY, g.phase)
        assertNull("라운드 끝난 뒤엔 못 냄", g.guess(43))
    }

    @Test fun `폭탄이 끝 숫자여도 판정`() {
        val g = game(bomb = 1)
        assertEquals(Verdict.BOOM, g.guess(1)!!.verdict)
        val g2 = game(bomb = 100)
        assertEquals(Verdict.UP, g2.guess(99)!!.verdict)
        assertTrue(g2.isForced)
        assertEquals(Verdict.BOOM, g2.guess(100)!!.verdict)
    }

    // ── 잘못된 입력 ──

    @Test fun `범위 밖이나 잘린 값은 거절되고 차례 그대로`() {
        val g = game(bomb = 42)
        assertNull(g.guess(0))
        assertNull(g.guess(101))
        g.guess(60) // 0번
        assertEquals(1, g.current)
        assertNull("잘린 값 자체", g.guess(60))
        assertNull("잘린 위쪽", g.guess(75))
        g.guess(20) // 1번
        assertNull("잘린 값 자체", g.guess(20))
        assertNull("잘린 아래쪽", g.guess(5))
        assertFalse(g.isValid(20))
        assertTrue(g.isValid(21))
        assertTrue(g.isValid(59))
        assertEquals(2, g.current)
        assertEquals(2, g.steps.size)
    }

    @Test fun `시작 전에는 못 냄`() {
        val g = UpDownGame(2, Random(3))
        assertNull(g.guess(50))
        assertEquals(Phase.READY, g.phase)
    }

    // ── 강제 마지막 ──

    @Test fun `폭탄만 남으면 다음 사람은 폭탄을 낼 수밖에 없음`() {
        val g = game(n = 2, bomb = 42)
        g.guess(43) // 0번 → 위 잘림
        g.guess(41) // 1번 → 아래 잘림
        assertTrue(g.isForced)
        assertEquals(1, g.remaining)
        assertEquals(42, g.minAllowed)
        assertEquals(42, g.maxAllowed)
        assertNull(g.guess(41))
        assertNull(g.guess(43))
        val s = g.guess(42)!!
        assertEquals(0, s.player)
        assertEquals(Verdict.BOOM, s.verdict)
        assertFalse(g.isForced)
    }

    // ── 라운드 · 순위 ──

    @Test fun `라운드 수는 인원수이고 시작 사람이 돌아감`() {
        for (n in 2..4) {
            val g = UpDownGame(n, Random(n), firstStarter = 1)
            assertEquals(n, g.totalRounds)
            for (r in 1..n) {
                val s = g.startRound()
                assertEquals(r, g.round)
                assertEquals((1 + r - 1) % n, s)
                assertEquals(s, g.current)
                assertTrue(g.bomb in GameConfig.MIN_NUMBER..GameConfig.MAX_NUMBER)
                val step = g.guess(g.bomb)!!
                assertEquals(r == n, step.gameOver)
            }
            assertEquals(Phase.OVER, g.phase)
            assertEquals(n, g.skulls.sum())
        }
    }

    @Test fun `새 라운드는 범위가 처음으로`() {
        val g = game(n = 2, bomb = 42)
        g.guess(50); g.guess(42)
        g.startRound(7)
        assertEquals(1, g.minAllowed)
        assertEquals(100, g.maxAllowed)
        assertEquals(1, g.current)
        assertTrue(g.steps.isEmpty())
    }

    @Test fun `해골 적은 순 순위 동점은 같은 순위`() {
        val g = UpDownGame(4, Random(5), 0)
        // 1라운드: 0번 폭탄, 2라운드: 1번 시작 → 1번 폭탄, 3라운드: 2번 시작 → 2번 안 맞고 3번 폭탄, 4라운드: 3번 시작 → 3번 폭탄
        g.startRound(10); g.guess(10)
        g.startRound(10); g.guess(10)
        g.startRound(10); g.guess(50); g.guess(10)
        g.startRound(10); g.guess(10)
        assertEquals(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(1, 1, 0, 2), g.skulls)
        assertArrayEquals(intArrayOf(2, 2, 1, 4), g.ranks())
    }

    @Test fun `모두 같으면 모두 1위`() {
        val g = UpDownGame(2, Random(5), 0)
        g.startRound(30); g.guess(30)
        g.startRound(30); g.guess(30)
        assertArrayEquals(intArrayOf(1, 1), g.skulls)
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    @Test fun `차례대로 돌아감`() {
        val g = game(n = 3, bomb = 42, first = 2)
        assertEquals(2, g.current)
        g.guess(90); assertEquals(0, g.current)
        g.guess(10); assertEquals(1, g.current)
        g.guess(80); assertEquals(2, g.current)
    }
}
