package com.siwoo.party.games.simon

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimonGameTest {

    /** 지금 차례인 사람이 순서를 그대로 누르고 color 를 더한다 (now 부터 100ms 간격). 끝난 시각을 돌려준다 */
    private fun playTurn(g: SimonGame, color: Int, start: Long): Long {
        var now = start
        g.beginTurn(now)
        for (c in g.sequence.toList()) {
            now += 100
            assertTrue(g.press(c, now) is Press.Correct)
        }
        now += 100
        assertTrue(g.press(color, now) is Press.Added)
        return now
    }

    @Test
    fun `첫 사람은 색 하나만 눌러 순서를 시작한다`() {
        val g = SimonGame(3)
        assertEquals(1, g.start(1))
        g.beginTurn(0)
        assertEquals(Phase.ADD, g.phase)
        assertEquals(-1, g.expected)
        val r = g.press(2, 500) as Press.Added
        assertEquals(1, r.player)
        assertEquals(2, r.color)
        assertEquals(1, r.length)
        assertEquals(2, r.next)
        assertEquals(listOf(2), g.sequence)
        assertEquals(Phase.BETWEEN, g.phase)
        assertEquals(1, g.best[1])
    }

    @Test
    fun `순서를 처음부터 그대로 눌러야 하고 다 맞히면 새 색 추가 단계`() {
        val g = SimonGame(2)
        g.start(0)
        playTurn(g, 0, 0)
        playTurn(g, 3, 1000)
        assertEquals(listOf(0, 3), g.sequence)
        // 0번 차례: 0, 3 을 그대로
        g.beginTurn(2000)
        assertEquals(Phase.REPEAT, g.phase)
        assertEquals(0, g.expected)
        val a = g.press(0, 2100) as Press.Correct
        assertEquals(1, a.progress)
        assertEquals(2, a.length)
        assertFalse(a.needAdd)
        assertEquals(3, g.expected)
        val b = g.press(3, 2200) as Press.Correct
        assertTrue(b.needAdd)
        assertEquals(Phase.ADD, g.phase)
        assertEquals(-1, g.expected)
    }

    @Test
    fun `새 색을 더하면 길이가 하나 늘고 다음 사람 차례`() {
        val g = SimonGame(3)
        g.start(0)
        playTurn(g, 1, 0)
        playTurn(g, 1, 1000)
        g.beginTurn(2000)
        g.press(1, 2100); g.press(1, 2200)
        val r = g.press(2, 2300) as Press.Added
        assertEquals(3, r.length)
        assertEquals(listOf(1, 1, 2), g.sequence)
        assertEquals(0, r.next)
        assertEquals(0, g.current)
        assertEquals(3, g.best[2])
        // 차례 사이에는 눌러도 무시
        assertSame(Press.Ignored, g.press(0, 2400))
        assertEquals(3, g.length)
    }

    @Test
    fun `틀린 색을 누르면 탈락하고 정답 색을 알려 준다`() {
        val g = SimonGame(3)
        g.start(0)
        playTurn(g, 2, 0)
        playTurn(g, 1, 1000)
        g.beginTurn(2000)
        g.press(2, 2100)
        val r = g.press(3, 2200) as Press.Out
        assertEquals(2, r.player)
        assertEquals(OutReason.WRONG, r.reason)
        assertEquals(3, r.pressed)
        assertEquals(1, r.expected)
        assertFalse(r.gameOver)
        assertEquals(0, r.next)
        assertFalse(g.alive[2])
        assertEquals(listOf(2), g.eliminated)
        assertEquals(Phase.BETWEEN, g.phase)
        // 순서는 그대로 이어진다
        assertEquals(listOf(2, 1), g.sequence)
        g.beginTurn(3000)
        assertEquals(Phase.REPEAT, g.phase)
        assertEquals(0, g.progress)
        assertEquals(2, g.expected)
    }

    @Test
    fun `5초 동안 누르지 않으면 시간 초과 탈락`() {
        val g = SimonGame(2)
        g.start(0)
        g.beginTurn(10_000)
        assertEquals(15_000, g.deadline)
        assertEquals(3000, g.remainingMs(12_000))
        assertSame(Press.Ignored, g.checkTimeout(14_999))
        val r = g.checkTimeout(15_000) as Press.Out
        assertEquals(OutReason.TIMEOUT, r.reason)
        assertEquals(0, r.player)
        assertEquals(-1, r.pressed)
        assertTrue(r.gameOver)
        assertEquals(1, g.winner)
    }

    @Test
    fun `누를 때마다 제한 시간이 다시 5초`() {
        val g = SimonGame(3)
        g.start(0)
        playTurn(g, 0, 0)
        playTurn(g, 1, 1000)
        g.beginTurn(2000)
        g.press(0, 6000) // 4초 뒤 눌러 아슬아슬하게 성공
        assertEquals(11_000, g.deadline)
        assertSame(Press.Ignored, g.checkTimeout(10_000))
        // 새 색 추가 단계에서도 시간 초과
        g.press(1, 10_500)
        assertEquals(Phase.ADD, g.phase)
        val r = g.checkTimeout(15_600) as Press.Out
        assertEquals(OutReason.TIMEOUT, r.reason)
        assertEquals(2, r.player)
        assertEquals(-1, r.expected)
    }

    @Test
    fun `마감이 지난 뒤 누르면 맞는 색이어도 시간 초과`() {
        val g = SimonGame(2)
        g.start(1)
        playTurn(g, 3, 0)
        g.beginTurn(1000)
        val r = g.press(3, 6000) as Press.Out
        assertEquals(OutReason.TIMEOUT, r.reason)
        assertEquals(0, r.player)
    }

    @Test
    fun `차례는 탈락한 사람을 건너뛴다`() {
        val g = SimonGame(4)
        g.start(0)
        playTurn(g, 0, 0)               // 0 → 1
        g.beginTurn(1000)
        g.press(2, 1100)                 // 1 탈락 (정답 0)
        assertEquals(2, g.current)
        playTurn(g, 1, 2000)             // 2 → 3
        assertEquals(3, g.current)
        playTurn(g, 2, 3000)             // 3 → 0
        assertEquals(0, g.current)
        playTurn(g, 3, 4000)             // 0 → 2 (1 건너뜀)
        assertEquals(2, g.current)
        assertEquals(2, g.nextAlive(1))
        assertEquals(listOf(0, 1, 2, 3), g.sequence)
    }

    @Test
    fun `최후의 1인이 남으면 끝`() {
        val g = SimonGame(3)
        g.start(2)
        g.beginTurn(0)
        g.checkTimeout(5000)             // 2 탈락 (빈 순서에서 시간 초과)
        assertEquals(0, g.current)
        assertTrue(g.sequence.isEmpty())
        g.beginTurn(6000)
        assertEquals(Phase.ADD, g.phase)  // 순서가 비어 있으니 다시 첫 색
        g.press(1, 6100)                 // 0 → 1
        g.beginTurn(7000)
        val r = g.press(0, 7100) as Press.Out // 1 탈락
        assertTrue(r.gameOver)
        assertEquals(0, r.next)
        assertEquals(Phase.GAME_OVER, g.phase)
        assertEquals(0, g.winner)
        assertSame(Press.Ignored, g.press(1, 7200))
        assertSame(Press.Ignored, g.checkTimeout(99_000))
    }

    @Test
    fun `순위는 생존자 1위 그다음 늦게 탈락한 순`() {
        // 탈락 순서 2(시간 초과) → 0(틀림) → 3(틀림), 1 생존
        val h = SimonGame(4, Random(1))
        h.start(2)
        h.beginTurn(0); h.checkTimeout(5000)     // 2 탈락
        h.beginTurn(6000); h.press(0, 6100)      // 3 추가 [0] → 0
        h.beginTurn(7000); h.press(1, 7100)      // 0 틀림 (정답 0) → 탈락 → 1
        h.beginTurn(8000); h.press(0, 8100); h.press(2, 8200) // 1 추가 [0,2] → 3
        h.beginTurn(9000); h.press(2, 9100)      // 3 틀림 → 끝
        assertTrue(h.gameOver)
        assertEquals(listOf(2, 0, 3), h.eliminated)
        assertArrayEquals(intArrayOf(3, 1, 4, 2), h.finalRanks())
        assertEquals(1, h.winner)
    }

    @Test
    fun `무작위 첫 차례와 잘못된 입력은 무시`() {
        val g = SimonGame(4, Random(7))
        val f = g.start()
        assertTrue(f in 0 until 4)
        assertSame("차례 열기 전", Press.Ignored, g.press(0, 0))
        g.beginTurn(0)
        assertSame(Press.Ignored, g.press(-1, 10))
        assertSame(Press.Ignored, g.press(4, 10))
        assertEquals(Phase.ADD, g.phase)
        assertEquals(1, g.turns)
    }
}
