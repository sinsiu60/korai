package com.siwoo.party.games.clap369

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Clap369Test {

    @Test fun `짝 개수 - 대표 숫자`() {
        val expect = mapOf(
            1 to 0, 2 to 0, 3 to 1, 4 to 0, 6 to 1, 9 to 1, 10 to 0, 11 to 0, 13 to 1, 16 to 1, 19 to 1, 20 to 0,
            30 to 1, 31 to 1, 33 to 2, 36 to 2, 39 to 2, 40 to 0, 45 to 0, 63 to 2, 66 to 2, 69 to 2, 70 to 0,
            93 to 2, 96 to 2, 99 to 2, 100 to 0, 103 to 1, 333 to 3, 0 to 0,
        )
        for ((n, c) in expect) assertEquals("$n", c, Clap369.claps(n))
    }

    @Test fun `짝 개수 - 1부터 100까지 전부 (글자로 센 것과 같음)`() {
        var noClap = 0
        for (n in 1..100) {
            val byText = n.toString().count { it == '3' || it == '6' || it == '9' }
            assertEquals("$n", byText, Clap369.claps(n))
            if (byText == 0) noClap++
        }
        // 3·6·9 없는 수: 한 자리 6개(1 2 4 5 7 8) + 두 자리 6×7=42개(첫째 1 2 4 5 7 8, 둘째 0 1 2 4 5 7 8) + 100
        assertEquals(6 + 42 + 1, noClap)
        // 3·6·9 없는 숫자들은 [숫자]
        for (n in intArrayOf(1, 2, 4, 5, 7, 8, 10, 11, 12, 14, 20, 21, 22, 24, 40, 41, 50, 57, 70, 78, 80, 88, 100)) assertEquals(0, Clap369.claps(n))
    }

    @Test fun `제한 시간 - 1~9는 3초, 10의 배수마다 0·2초씩, 최소 1·2초`() {
        for (n in 1..9) assertEquals(3000L, Clap369.limitMs(n))
        assertEquals(2800L, Clap369.limitMs(10))
        assertEquals(2800L, Clap369.limitMs(19))
        assertEquals(2600L, Clap369.limitMs(20))
        assertEquals(2400L, Clap369.limitMs(39))
        assertEquals(1400L, Clap369.limitMs(80))
        assertEquals(1200L, Clap369.limitMs(90))
        assertEquals(1200L, Clap369.limitMs(99))
        assertEquals(1200L, Clap369.limitMs(100))
        assertEquals(1200L, Clap369.limitMs(250))
        // 줄어들기만 하고 바뀌는 곳은 10의 배수
        for (n in 2..200) {
            assertTrue(Clap369.limitMs(n) <= Clap369.limitMs(n - 1))
            if (Clap369.limitMs(n) != Clap369.limitMs(n - 1)) assertEquals(0, n % 10)
            assertTrue(Clap369.limitMs(n) >= GameConfig.LIMIT_MIN_MS)
        }
    }
}

class Clap369GameTest {

    /** 지금 숫자를 정확히 입력하고 확인 시간까지 흘림. 다음 차례 시작 시각을 돌려준다 */
    private fun sayRight(g: Clap369Game, at: Long): Long {
        val need = g.needed
        var r: Press = Press.Ignored
        if (need == 0) r = g.press(Key.NUMBER, at)
        else repeat(need) { r = g.press(Key.CLAP, at + it * 50L) }
        assertTrue("완료여야 함: ${g.number}", r is Press.Done)
        val end = (r as Press.Done).settleUntil
        assertTrue(g.update(end) is Tick.Next)
        return end
    }

    @Test fun `숫자 수는 숫자 1번, 짝 수는 짝을 정확히 N번`() {
        val g = Clap369Game(2)
        g.startTurn(0)
        assertSame(Phase.INPUT, g.phase)
        assertEquals(3000L, g.deadline)
        val d = g.press(Key.NUMBER, 500)
        assertTrue(d is Press.Done && d.number == 1 && d.settleUntil == 500 + GameConfig.SETTLE_MS)
        assertSame(Phase.SETTLE, g.phase)
        assertSame("확인 시간 전엔 그대로", Tick.None, g.update(500 + GameConfig.SETTLE_MS - 1))
        val n = g.update(500 + GameConfig.SETTLE_MS)
        assertTrue(n is Tick.Next && n.player == 1 && n.number == 2)
        assertEquals("다음 차례는 확인 끝 시각부터", 900L + 3000L, g.deadline)
        var t = 900L
        t = sayRight(g, t) // 2
        // 3: 짝 1번
        assertEquals(3, g.number)
        assertEquals(0, g.current)
        val c = g.press(Key.CLAP, t + 100)
        assertTrue(c is Press.Done && c.claps == 1)
    }

    @Test fun `33은 짝 2번 - 한 번은 모자람, 세 번은 너무 많음`() {
        // 모자람: 마감까지 1번만
        var g = jumpTo(33)
        val start = g.turnStart
        assertTrue(g.press(Key.CLAP, start + 100) is Press.Clapped)
        assertEquals(1, g.clapCount)
        assertSame(Tick.None, g.update(start + g.limitMs - 1))
        val t = g.update(start + g.limitMs)
        assertTrue(t is Tick.Out && t.out.reason == Fail.TOO_FEW)
        // 너무 많음: 2번에 완료, 확인 시간 안에 3번째
        g = jumpTo(33)
        val s2 = g.turnStart
        g.press(Key.CLAP, s2 + 100)
        assertTrue(g.press(Key.CLAP, s2 + 200) is Press.Done)
        val over = g.press(Key.CLAP, s2 + 200 + GameConfig.SETTLE_MS - 1)
        assertTrue(over is Press.Out && over.reason == Fail.TOO_MANY && over.number == 33)
        // 짝 수에 [숫자] → 탈락
        g = jumpTo(36)
        val w = g.press(Key.NUMBER, g.turnStart + 10)
        assertTrue(w is Press.Out && w.reason == Fail.SHOULD_CLAP)
        // 짝 친 다음 [숫자] 도 탈락
        g = jumpTo(39)
        g.press(Key.CLAP, g.turnStart + 10)
        assertTrue((g.press(Key.NUMBER, g.turnStart + 20) as Press.Out).reason == Fail.SHOULD_CLAP)
        // 숫자 수에 [짝] → 탈락, [숫자] 두 번도 탈락
        g = jumpTo(40)
        assertTrue((g.press(Key.CLAP, g.turnStart + 10) as Press.Out).reason == Fail.SHOULD_SAY)
        g = jumpTo(41)
        g.press(Key.NUMBER, g.turnStart + 10)
        assertTrue((g.press(Key.NUMBER, g.turnStart + 20) as Press.Out).reason == Fail.TOO_MANY)
    }

    @Test fun `아무것도 안 누르면 시간 초과, 마감 시각에 누르면 늦음`() {
        var g = jumpTo(13)
        val t = g.update(g.deadline)
        assertTrue(t is Tick.Out && t.out.reason == Fail.TIMEOUT)
        g = jumpTo(14)
        val late = g.press(Key.NUMBER, g.deadline)
        assertTrue(late is Press.Out && late.reason == Fail.TIMEOUT)
        g = jumpTo(15)
        assertTrue(g.press(Key.NUMBER, g.deadline - 1) is Press.Done)
    }

    @Test fun `확인 시간이 지난 뒤 누르면 다음 사람 입력`() {
        val g = Clap369Game(3)
        g.startTurn(0)
        g.press(Key.NUMBER, 100) // 가: 1
        // 화면이 update 를 부르기 전에 다음 사람이 바로 누름 → 2 는 나 차례
        val r = g.press(Key.NUMBER, 100 + GameConfig.SETTLE_MS)
        assertTrue(r is Press.Done && r.number == 2)
        assertEquals(1, g.current)
    }

    @Test fun `일시정지 뒤 차례 다시 시작`() {
        val g = jumpTo(23)
        g.press(Key.CLAP, g.turnStart + 10) // 23 은 1번이라 완료
        assertSame(Phase.SETTLE, g.phase)
        g.restartTurn(99_999) // 확인 중이면 아무 일 없음
        assertSame(Phase.SETTLE, g.phase)
        val h = jumpTo(36)
        h.press(Key.CLAP, h.turnStart + 10)
        h.restartTurn(50_000)
        assertEquals(0, h.clapCount)
        assertEquals(50_000L + Clap369.limitMs(36), h.deadline)
    }

    @Test fun `탈락한 사람은 차례에서 빠지고 같은 숫자부터 이어감`() {
        val g = Clap369Game(4)
        g.startTurn(0)
        var t = sayRight(g, 0) // 0: 1
        t = sayRight(g, t) // 1: 2
        // 2번 사람이 3 에서 [숫자] → 탈락
        assertEquals(2, g.current)
        val o = g.press(Key.NUMBER, t + 10)
        assertTrue(o is Press.Out && o.player == 2 && !o.gameOver)
        assertSame(Phase.IDLE, g.phase)
        assertSame("탈락 연출 중엔 입력 무시", Press.Ignored, g.press(Key.CLAP, t + 20))
        g.resumeAfterOut(t + 2000)
        assertEquals("다음 살아 있는 사람", 3, g.current)
        assertEquals("같은 숫자부터", 3, g.number)
        t = sayRight(g, t + 2000) // 3: 3
        assertEquals(0, g.current)
        t = sayRight(g, t) // 0: 4
        assertEquals("2번은 건너뜀", 1, g.current)
        t = sayRight(g, t) // 1: 5
        assertEquals(3, g.current)
        assertEquals(1, g.nextAlive(0))
        assertEquals(3, g.nextAlive(1))
        assertEquals(0, g.nextAlive(3))
        // 3번(지금) 시간 초과 → 다음은 0
        g.update(g.deadline)
        g.resumeAfterOut(t + 9000)
        assertEquals(0, g.current)
        assertEquals(6, g.number)
        assertEquals(1, g.nextAlive(0))
        assertEquals(0, g.nextAlive(1))
    }

    @Test fun `탈락 순서로 순위, 마지막 1명 우승`() {
        val g = Clap369Game(4)
        g.startTurn(0)
        // 0 탈락 (1 에서 짝)
        g.press(Key.CLAP, 10)
        g.resumeAfterOut(100)
        // 1 차례: 1 → 맞힘, 2: 2 → 짝 (탈락)
        var t = sayRight(g, 100)
        assertEquals(2, g.current)
        g.press(Key.CLAP, t + 10)
        g.resumeAfterOut(t + 100)
        // 3 차례, 숫자 2 → 맞힘, 1 차례 3 → 시간 초과 탈락 → 끝
        t = sayRight(g, t + 100)
        assertEquals(1, g.current)
        assertEquals(3, g.number)
        val last = g.update(g.deadline)
        assertTrue(last is Tick.Out && last.out.gameOver && last.out.player == 1)
        assertTrue(g.gameOver)
        assertSame(Press.Ignored, g.press(Key.NUMBER, g.deadline + 10))
        assertEquals(listOf(0, 2, 1), g.outOrder)
        assertEquals(3, g.winner())
        // 0 먼저 탈락 → 4위, 2 → 3위, 1 → 2위, 3 → 1위
        assertArrayEquals(intArrayOf(4, 2, 3, 1), g.finalRanks())
        assertFalse(g.alive[1])
    }

    @Test fun `2인 - 한 명 탈락하면 바로 끝`() {
        val g = Clap369Game(2)
        g.startTurn(0)
        val t = sayRight(g, 0)
        val o = g.press(Key.CLAP, t + 10) // 1번 사람, 2 에서 짝
        assertTrue(o is Press.Out && o.gameOver)
        assertArrayEquals(intArrayOf(1, 2), g.finalRanks())
    }

    /** 2인 게임을 n 차례 직전까지 정확히 진행 */
    private fun jumpTo(n: Int): Clap369Game {
        val g = Clap369Game(2)
        g.startTurn(0)
        var t = 0L
        while (g.number < n) t = sayRight(g, t)
        assertSame(Phase.INPUT, g.phase)
        assertEquals(Clap369.limitMs(n), g.deadline - g.turnStart)
        return g
    }
}
