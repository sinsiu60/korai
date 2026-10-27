package com.siwoo.party.games.balloon

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BalloonGameTest {

    /** 탭 제한에 걸리지 않게 시간 간격을 두고 n 번 탭 */
    private fun pumpN(g: BalloonGame, p: Int, n: Int, clock: LongArray): Pump {
        var last = Pump.IGNORED
        repeat(n) {
            clock[0] += 150
            last = g.pump(p, clock[0])
        }
        return last
    }

    private fun started(players: Int, seed: Int = 1): BalloonGame {
        val g = BalloonGame(players, Random(seed))
        g.startRound()
        return g
    }

    @Test
    fun `멈춤 - 지금 크기가 점수 그 뒤 탭은 무시`() {
        val g = started(2)
        g.setThresholdForTest(0, 40)
        val clock = longArrayOf(0)
        assertSame(Pump.GREW, pumpN(g, 0, 17, clock))
        assertEquals(17, g.sizes[0])
        assertTrue(g.stop(0))
        assertSame(BalloonState.STOPPED, g.states[0])
        assertEquals(17, g.scoreOf(0))
        // 멈춘 뒤 탭·멈춤은 무시, 크기 그대로
        assertSame(Pump.IGNORED, pumpN(g, 0, 5, clock))
        assertFalse(g.stop(0))
        assertEquals(17, g.sizes[0])
        assertEquals(17, g.scoreOf(0))
    }

    @Test
    fun `터짐 - 터지는 크기에 닿는 순간 0점`() {
        val g = started(2)
        g.setThresholdForTest(1, 20)
        val clock = longArrayOf(0)
        assertSame(Pump.GREW, pumpN(g, 1, 19, clock))
        assertSame(BalloonState.INFLATING, g.states[1])
        assertEquals("19 = 터지는 크기 바로 아래 → 아직 안전", 19, g.sizes[1])
        assertSame(Pump.POPPED, pumpN(g, 1, 1, clock))
        assertSame(BalloonState.POPPED, g.states[1])
        assertEquals(0, g.scoreOf(1))
        // 터진 뒤에는 탭·멈춤 무시
        assertSame(Pump.IGNORED, pumpN(g, 1, 3, clock))
        assertFalse(g.stop(1))
        assertEquals(0, g.scoreOf(1))
    }

    @Test
    fun `터짐 - 크기가 터지는 크기를 넘어도 터짐`() {
        val g = started(2)
        val clock = longArrayOf(0)
        pumpN(g, 0, 10, clock)
        // 이미 크기 10 인데 터지는 크기를 그보다 작게 → 다음 탭에 바로 터짐 (같거나 넘으면)
        g.setThresholdForTest(0, 5)
        assertSame(Pump.POPPED, pumpN(g, 0, 1, clock))
        assertEquals(0, g.scoreOf(0))
    }

    @Test
    fun `터지는 크기 - 범위 안 구역마다 다름 라운드마다 새로`() {
        val seen = HashSet<Int>()
        for (seed in 0 until 300) {
            val g = BalloonGame(4, Random(seed))
            repeat(GameConfig.BALLOON_ROUNDS) {
                g.startRound()
                val th = IntArray(4) { g.popThreshold(it) }
                for (v in th) {
                    assertTrue("범위 $v", v in GameConfig.POP_MIN..GameConfig.POP_MAX)
                    seen.add(v)
                }
                assertEquals("구역마다 다른 값 ${th.toList()}", 4, th.toSet().size)
                g.timeUp()
                g.endRound()
            }
        }
        // 범위 전체가 고르게 나온다 (양 끝 포함)
        assertTrue(GameConfig.POP_MIN in seen)
        assertTrue(GameConfig.POP_MAX in seen)
        assertEquals(GameConfig.POP_MAX - GameConfig.POP_MIN + 1, seen.size)
    }

    @Test
    fun `같은 seed 면 같은 터지는 크기`() {
        val a = started(3, seed = 42)
        val b = started(3, seed = 42)
        for (p in 0 until 3) assertEquals(a.popThreshold(p), b.popThreshold(p))
    }

    @Test
    fun `탭 제한 - 1초에 최대 횟수까지만 커짐`() {
        val g = started(2)
        g.setThresholdForTest(0, GameConfig.POP_MAX)
        var grew = 0
        var limited = 0
        // 1초 안에 30번 (여러 손가락 마구 두드리기)
        for (k in 0 until 30) {
            when (g.pump(0, 1000L + k * 30L)) {
                Pump.GREW -> grew++
                Pump.LIMITED -> limited++
                else -> Unit
            }
        }
        assertEquals(GameConfig.MAX_TAPS_PER_SEC, grew)
        assertEquals(30 - GameConfig.MAX_TAPS_PER_SEC, limited)
        assertEquals(GameConfig.MAX_TAPS_PER_SEC, g.sizes[0])
        // 다른 사람은 영향 없음
        assertSame(Pump.GREW, g.pump(1, 1100))
        // 1초가 지나면 다시 인정
        assertSame(Pump.GREW, g.pump(0, 2100))
    }

    @Test
    fun `라운드 끝 - 모두 멈춤 또는 터짐`() {
        val g = started(3)
        g.setThresholdForTest(2, 15)
        val clock = longArrayOf(0)
        pumpN(g, 0, 8, clock)
        assertTrue(g.stop(0))
        assertFalse(g.allDone)
        pumpN(g, 2, 15, clock)
        assertSame(BalloonState.POPPED, g.states[2])
        assertFalse("1번이 아직 키우는 중", g.allDone)
        assertTrue("크기 0 이어도 멈출 수 있음", g.stop(1))
        assertTrue(g.allDone)
        val scores = g.endRound()
        assertArrayEquals(intArrayOf(8, 0, 0), scores)
        assertSame(Phase.ROUND_OVER, g.phase)
        assertEquals(1, g.roundsDone)
        assertEquals(listOf(0), g.roundBest(scores))
        // 라운드 밖에서는 탭·멈춤 무시
        assertSame(Pump.IGNORED, g.pump(1, 99_999))
        assertFalse(g.stop(1))
    }

    @Test
    fun `시간 끝 - 남은 풍선은 지금 크기로 자동 확정`() {
        val g = started(2)
        g.setThresholdForTest(0, 40)
        g.setThresholdForTest(1, 13)
        val clock = longArrayOf(0)
        pumpN(g, 0, 11, clock)
        pumpN(g, 1, 13, clock) // 터짐
        assertEquals(1, g.timeUp())
        assertSame(BalloonState.STOPPED, g.states[0])
        assertSame(BalloonState.POPPED, g.states[1])
        assertArrayEquals(intArrayOf(11, 0), g.endRound())
    }

    @Test(expected = IllegalStateException::class)
    fun `키우는 중이면 라운드를 끝낼 수 없음`() {
        val g = started(2)
        g.stop(0)
        g.endRound()
    }

    @Test
    fun `세 라운드 합계 순위 - 동점 같은 순위`() {
        val g = BalloonGame(4, Random(7))
        val clock = longArrayOf(0)
        // 라운드별 [멈춘 크기, -1 = 터짐]
        val plan = arrayOf(
            intArrayOf(10, 20, -1, 5),
            intArrayOf(15, -1, 30, 5),
            intArrayOf(-1, 5, 5, 20),
        )
        for (r in 0 until GameConfig.BALLOON_ROUNDS) {
            g.startRound()
            assertEquals(r + 1, g.roundNumber)
            for (p in 0 until 4) {
                val n = plan[r][p]
                if (n < 0) {
                    g.setThresholdForTest(p, GameConfig.POP_MIN)
                    assertSame(Pump.POPPED, pumpN(g, p, GameConfig.POP_MIN, clock))
                } else {
                    g.setThresholdForTest(p, GameConfig.POP_MAX)
                    pumpN(g, p, n, clock)
                    assertTrue(g.stop(p))
                }
            }
            g.endRound()
        }
        assertTrue(g.gameOver)
        // 합계: 25, 25, 35, 30
        assertArrayEquals(intArrayOf(25, 25, 35, 30), g.totals)
        assertArrayEquals(intArrayOf(3, 3, 1, 2), g.ranks())
        assertArrayEquals(intArrayOf(10, 20, 0, 5), g.roundScores[0])
    }

    @Test
    fun `모두 0점이면 모두 1위`() {
        val g = BalloonGame(2, Random(1))
        repeat(GameConfig.BALLOON_ROUNDS) {
            g.startRound()
            g.timeUp()
            assertTrue(g.roundBest(g.endRound()).isEmpty())
        }
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    @Test
    fun `배치 - 위쪽 구역은 180도 돌려서 같은 버튼`() {
        val l = BalloonLayout()
        l.layout(630f, 350f)
        assertTrue("버튼은 칸 안", l.stop.left > -l.cellW / 2 && l.stop.right < l.cellW / 2 && l.stop.bottom < l.cellH / 2)
        assertFalse("풍선 최대 크기와 버튼이 겹치지 않음", l.balloonX + l.rMax > l.stop.left)
        val out = FloatArray(2)
        // 아래쪽 구역(0°): 버튼 가운데 화면 좌표 → 다시 사람 기준으로
        BalloonLayout.toScreen(300f, 500f, 0f, l.stop.centerX, l.stop.centerY, out)
        assertEquals(300f + l.stop.centerX, out[0], 0.01f)
        assertEquals(500f + l.stop.centerY, out[1], 0.01f)
        // 위쪽 구역(180°): 사람 기준 오른쪽 아래 = 화면 왼쪽 위
        BalloonLayout.toScreen(300f, 170f, 180f, l.stop.centerX, l.stop.centerY, out)
        assertEquals(300f - l.stop.centerX, out[0], 0.01f)
        assertEquals(170f - l.stop.centerY, out[1], 0.01f)
        val back = FloatArray(2)
        BalloonLayout.toLocal(300f, 170f, 180f, out[0], out[1], back)
        assertTrue(l.stop.contains(back[0], back[1]))
        // 위쪽 구역에서 돌리지 않은 자리는 버튼이 아님
        BalloonLayout.toLocal(300f, 170f, 180f, 300f + l.stop.centerX, 170f + l.stop.centerY, back)
        assertFalse(l.stop.contains(back[0], back[1]))
    }

    @Test
    fun `반지름 - 크기에 따라 커지고 최대에서 멈춤`() {
        val l = BalloonLayout()
        l.layout(630f, 350f)
        assertEquals(l.rMin, l.radius(0), 0.01f)
        assertTrue(l.radius(10) < l.radius(20))
        assertEquals(l.rMax, l.radius(GameConfig.POP_MAX), 0.01f)
        assertEquals(l.rMax, l.radius(GameConfig.POP_MAX + 9), 0.01f)
    }
}
