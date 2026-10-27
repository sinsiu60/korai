package com.siwoo.party.games.jegi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class JegiGameTest {

    /** 딱 좋은 순간에 제기 바로 아래를 터치 (성공) → 그 시각 */
    private fun kick(g: JegiGame, offsetMs: Long = 0L): Long {
        val t = g.flight.idealTime + offsetMs
        assertSame(Tap.KICK, g.tap(g.jegiX(t), t))
        return t
    }

    /** n 개 차고 일부러 놓친 뒤 기록까지 */
    private fun play(g: JegiGame, n: Int, start: Long = g.flight.groundTime + 1000L) {
        assertTrue(g.startAttempt(start))
        repeat(n) { kick(g) }
        // 안 차고 지나가게 → 늦음
        assertTrue(g.update(g.flight.idealTime + g.window() + 1))
        assertSame(EndReason.LATE, g.endReason)
        assertEquals(n, g.count)
        g.endAttempt()
    }

    // ── 판정 구간 축소 ──

    @Test
    fun `판정 구간은 처음 값에서 시작해 한 개마다 줄어들고 최소값에서 멈춘다`() {
        assertEquals(GameConfig.WINDOW_START_MS, JegiGame.windowFor(0))
        assertEquals(GameConfig.WINDOW_START_MS - GameConfig.WINDOW_SHRINK_MS, JegiGame.windowFor(1))
        var prev = JegiGame.windowFor(0)
        for (n in 1..GameConfig.MAX_KICKS) {
            val w = JegiGame.windowFor(n)
            assertTrue("줄거나 그대로 ($n)", w <= prev)
            assertTrue("최소값 이상 ($n)", w >= GameConfig.WINDOW_MIN_MS)
            prev = w
        }
        assertEquals(GameConfig.WINDOW_MIN_MS, JegiGame.windowFor(GameConfig.MAX_KICKS))
        assertEquals(GameConfig.WINDOW_MIN_MS, JegiGame.windowFor(10_000))
        // 최소값에 닿는 개수
        val reach = ((GameConfig.WINDOW_START_MS - GameConfig.WINDOW_MIN_MS) / GameConfig.WINDOW_SHRINK_MS).toInt()
        assertEquals(GameConfig.WINDOW_MIN_MS, JegiGame.windowFor(reach))
        assertTrue(JegiGame.windowFor(reach - 1) > GameConfig.WINDOW_MIN_MS)
    }

    @Test
    fun `찬 개수가 늘면 게임의 판정 구간도 줄어든다`() {
        val g = JegiGame(2, 1L)
        g.startAttempt(0L)
        assertEquals(GameConfig.WINDOW_START_MS, g.window())
        for (k in 1..30) {
            kick(g)
            assertEquals(k, g.count)
            assertEquals(JegiGame.windowFor(k), g.window())
        }
        assertEquals(GameConfig.WINDOW_MIN_MS, g.window())
    }

    // ── 차기 판정 (시간 경계) ──

    @Test
    fun `구간 양 끝 경계는 성공이다`() {
        for (side in listOf(-1L, 1L)) {
            val g = JegiGame(2, 7L)
            g.startAttempt(1000L)
            val w = g.window()
            val t = g.flight.idealTime + side * w
            assertSame("경계 $side", Tap.KICK, g.tap(g.jegiX(t), t))
            assertEquals(1, g.count)
            assertSame(Phase.FLYING, g.phase)
        }
    }

    @Test
    fun `구간보다 1ms 이르면 너무 일찍 - 도전 끝`() {
        val g = JegiGame(2, 7L)
        g.startAttempt(1000L)
        val t = g.flight.idealTime - g.window() - 1
        assertSame(Tap.EARLY, g.tap(g.jegiX(t), t))
        assertSame(Phase.ENDED, g.phase)
        assertSame(EndReason.EARLY, g.endReason)
        assertEquals(t, g.endTime)
        assertEquals(0, g.count)
    }

    @Test
    fun `구간보다 1ms 늦으면 늦음 - 터치로도 update 로도`() {
        val g = JegiGame(2, 7L)
        g.startAttempt(1000L)
        kick(g)
        val deadline = g.flight.idealTime + g.window()
        assertFalse("마감 순간까지는 안 끝남", g.update(deadline))
        assertSame(Phase.FLYING, g.phase)
        assertSame(Tap.LATE, g.tap(g.jegiX(deadline + 1), deadline + 1))
        assertSame(Phase.ENDED, g.phase)
        assertSame(EndReason.LATE, g.endReason)
        assertEquals("늦음은 마감 시각에 끝난 것으로", deadline, g.endTime)
        assertEquals(1, g.count)

        val g2 = JegiGame(2, 7L)
        g2.startAttempt(1000L)
        val d2 = g2.flight.idealTime + g2.window()
        assertTrue(g2.update(d2 + 1))
        assertFalse("한 번만", g2.update(d2 + 50))
        assertSame(EndReason.LATE, g2.endReason)
    }

    @Test
    fun `제기가 높이 있을 때 터치는 발만 옮긴다`() {
        val g = JegiGame(2, 3L)
        g.startAttempt(0L)
        val ideal = g.flight.idealTime
        val t = ideal - GameConfig.KICK_ZONE_MS - 1
        assertFalse(g.inKickZone(t))
        assertSame(Tap.MOVED, g.tap(30f, t))
        assertEquals(30f, g.footX, 0f)
        assertSame(Phase.FLYING, g.phase)
        assertEquals(0, g.count)
        // 범위 밖은 끝에 멈춤
        assertSame(Tap.MOVED, g.tap(-50f, t))
        assertEquals(GameConfig.FOOT_MIN, g.footX, 0f)
        assertSame(Tap.MOVED, g.tap(999f, t))
        assertEquals(GameConfig.FOOT_MAX, g.footX, 0f)
        // 차기 구간 시작부터는 이르면 실패
        val z = ideal - GameConfig.KICK_ZONE_MS
        assertTrue(g.inKickZone(z))
        assertSame(Tap.EARLY, g.tap(g.jegiX(z), z))
    }

    // ── 가로 닿기 ──

    @Test
    fun `발 가운데에서 닿는 거리 안이면 성공 밖이면 실패`() {
        assertTrue(JegiGame.inReach(50f, 50f + GameConfig.REACH))
        assertTrue(JegiGame.inReach(50f, 50f - GameConfig.REACH))
        assertFalse(JegiGame.inReach(50f, 50f + GameConfig.REACH + 0.01f))
        assertFalse(JegiGame.inReach(50f, 50f - GameConfig.REACH - 0.01f))

        for (side in listOf(-1f, 1f)) {
            // 닿는 경계 (성공)
            val g = JegiGame(2, 11L)
            g.startAttempt(0L)
            val t = g.flight.idealTime
            val jx = g.jegiX(t)
            assertTrue(jx in 30f..90f)
            assertSame(Tap.KICK, g.tap(jx + side * (GameConfig.REACH - 0.01f), t))
            // 조금 넘으면 실패
            val g2 = JegiGame(2, 11L)
            g2.startAttempt(0L)
            assertSame(Tap.OUT_OF_REACH, g2.tap(jx + side * (GameConfig.REACH + 0.5f), t))
            assertSame(EndReason.REACH, g2.endReason)
            assertSame(Phase.ENDED, g2.phase)
            assertEquals(jx + side * (GameConfig.REACH + 0.5f), g2.footX, 0.001f)
        }
    }

    // ── 놓치면 도전 끝 ──

    @Test
    fun `놓치면 도전이 끝나고 더 차도 무시 - 땅에 떨어진 뒤 기록`() {
        val g = JegiGame(3, 5L)
        g.startAttempt(0L)
        repeat(4) { kick(g) }
        val t = g.flight.idealTime
        assertSame(Tap.OUT_OF_REACH, g.tap(g.jegiX(t) + 40f, t))
        assertSame(Phase.ENDED, g.phase)
        // 더 터치해도 무시
        assertSame(Tap.IGNORED, g.tap(g.jegiX(t + 10), t + 10))
        assertEquals(4, g.count)
        assertFalse(g.update(t + 100000))
        // 놓친 제기는 같은 비행으로 땅까지
        assertTrue(g.landTime > t)
        assertEquals(0f, g.jegiY(g.landTime), 0.001f)
        assertTrue(g.jegiY(g.landTime - 50) > 0f)
        // 기록 → 다음 사람
        assertEquals(0, g.current)
        assertFalse(g.endAttempt())
        assertEquals(4, g.records[0][0])
        assertSame(Phase.WAIT, g.phase)
        assertEquals(1, g.current)
        assertSame(Tap.IGNORED, g.tap(50f, t + 20000))
    }

    @Test
    fun `완벽 개수에 닿으면 도전 끝`() {
        val g = JegiGame(2, 9L)
        g.startAttempt(0L)
        repeat(GameConfig.MAX_KICKS) { kick(g) }
        assertSame(Phase.ENDED, g.phase)
        assertSame(EndReason.PERFECT, g.endReason)
        assertEquals(GameConfig.MAX_KICKS, g.count)
        g.endAttempt()
        assertEquals(GameConfig.MAX_KICKS, g.records[0][0])
    }

    // ── 차례 · 2번 도전 · 순위 ──

    @Test
    fun `차례는 1회차를 한 바퀴 돈 뒤 2회차`() {
        val g = JegiGame(3, 1L, first = 2)
        val seen = ArrayList<Pair<Int, Int>>()
        while (!g.over) {
            seen.add(g.current to g.attempt)
            play(g, 1)
        }
        assertEquals(listOf(2 to 0, 0 to 0, 1 to 0, 2 to 1, 0 to 1, 1 to 1), seen)
        assertEquals(6, g.totalTurns)
        assertFalse("끝나면 시작 안 됨", g.startAttempt(0L))
    }

    @Test
    fun `두 번 도전 중 가장 좋은 기록`() {
        val g = JegiGame(2, 2L)
        play(g, 3) // 0번 1회차
        play(g, 5) // 1번 1회차
        play(g, 7) // 0번 2회차
        play(g, 2) // 1번 2회차
        assertTrue(g.over)
        assertArrayEquals(intArrayOf(3, 7), g.records[0])
        assertArrayEquals(intArrayOf(5, 2), g.records[1])
        assertEquals(7, g.best(0))
        assertEquals(5, g.best(1))
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }

    @Test
    fun `아직 안 한 도전은 -1`() {
        val g = JegiGame(2, 2L)
        assertEquals(-1, g.best(0))
        play(g, 0)
        assertEquals(0, g.best(0))
        assertEquals(-1, g.best(1))
    }

    @Test
    fun `순위 - 최고 기록 같으면 나머지 기록, 그래도 같으면 같은 순위`() {
        val g = JegiGame(4, 3L)
        // 1회차: 0:6 1:6 2:9 3:1  / 2회차: 0:2 1:4 2:0 3:6
        for (n in intArrayOf(6, 6, 9, 1, 2, 4, 0, 6)) play(g, n)
        assertTrue(g.over)
        // 2:9 → 1등, 1:(6,4) → 2등, 0:(6,2) → 3등, 3:(6,1) → 4등
        assertArrayEquals(intArrayOf(3, 2, 1, 4), g.ranks())

        val h = JegiGame(3, 4L)
        // 0:(5,3) 1:(3,5) 2:(4,4) → 0·1 공동 1등, 2 는 3등
        for (n in intArrayOf(5, 3, 4, 3, 5, 4)) play(h, n)
        assertArrayEquals(intArrayOf(1, 1, 3), h.ranks())

        assertTrue(JegiGame.rankKey(intArrayOf(10, 0)) > JegiGame.rankKey(intArrayOf(9, 9)))
        assertTrue(JegiGame.rankKey(intArrayOf(3, 8)) > JegiGame.rankKey(intArrayOf(8, 2)))
        assertEquals(JegiGame.rankKey(intArrayOf(3, 8)), JegiGame.rankKey(intArrayOf(8, 3)))
    }

    // ── 제기 움직임 ──

    @Test
    fun `같은 seed 면 흔들림과 떨어질 자리가 똑같다`() {
        fun trace(seed: Long): List<Float> {
            val g = JegiGame(2, seed)
            g.startAttempt(500L)
            val out = ArrayList<Float>()
            repeat(6) {
                val f = g.flight
                var t = f.t0
                while (t <= f.idealTime) { out.add(g.jegiX(t)); t += 37 }
                out.add(f.targetX)
                out.add(f.swayPhase)
                kick(g)
            }
            return out
        }
        assertEquals(trace(42L), trace(42L))
        assertNotEquals(trace(42L), trace(43L))
    }

    @Test
    fun `제기는 좌우로 흔들리고 마당 안에 머문다`() {
        val g = JegiGame(2, 99L)
        g.startAttempt(0L)
        repeat(60) {
            val f = g.flight
            var t = f.t0
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            while (t <= f.groundTime) {
                val x = g.jegiX(t)
                assertTrue("마당 안 $x", x >= GameConfig.JEGI_MIN - 0.001f && x <= GameConfig.JEGI_MAX + 0.001f)
                if (t >= f.idealTime) { lo = minOf(lo, x); hi = maxOf(hi, x) }
                t += 10
            }
            assertTrue("떨어진 뒤에도 흔들림", hi - lo > 1f)
            kick(g)
        }
    }

    @Test
    fun `비행 - 오르고 꼭대기 찍고 발 높이 지나 땅`() {
        val f = Flight(1000L, 40f, GameConfig.KICK_Y, 70f, 1.3f)
        assertEquals(GameConfig.KICK_Y, f.y(1000L), 0.0001f)
        assertEquals(40f, f.x(1000L), 0.0001f)
        assertEquals(GameConfig.APEX_Y, f.y(1000L + GameConfig.RISE_MS), 0.001f)
        assertTrue(f.y(1000L + GameConfig.RISE_MS / 2) < GameConfig.APEX_Y)
        assertTrue(f.rising(1000L + GameConfig.RISE_MS - 1))
        assertFalse(f.rising(1000L + GameConfig.RISE_MS))
        assertEquals(GameConfig.KICK_Y, f.y(f.idealTime), 0.01f)
        assertEquals(1000L + GameConfig.RISE_MS + GameConfig.FALL_MS, f.idealTime)
        assertEquals(0f, f.y(f.groundTime), 0.0001f)
        assertTrue(f.y(f.groundTime - 20) > 0f)
        // 딱 좋은 순간의 가로 = 떨어질 자리 + 흔들림
        assertEquals(70f + Flight.sway((f.idealTime - 1000L).toFloat(), 1.3f), f.x(f.idealTime), 0.001f)
        // 판정 띠 높이 = 떨어지는 속도 × 구간
        val band = Flight.bandHalf(GameConfig.WINDOW_START_MS)
        assertEquals(GameConfig.KICK_Y + band, f.y(f.idealTime - GameConfig.WINDOW_START_MS), 0.01f)
        assertEquals(GameConfig.KICK_Y - band, f.y(f.idealTime + GameConfig.WINDOW_START_MS), 0.01f)
    }

    @Test
    fun `성공하면 찬 자리에서 다시 떠오른다`() {
        val g = JegiGame(2, 12L)
        g.startAttempt(0L)
        val t = kick(g, 30L)
        assertEquals(t, g.flight.t0)
        assertEquals(g.flight.x0, g.jegiX(t), 0.0001f)
        assertTrue(g.jegiY(t + GameConfig.RISE_MS) > g.jegiY(t))
        assertTrue(kotlin.math.abs(g.flight.targetX - g.flight.x0) <= GameConfig.KICK_DRIFT + 0.001f)
    }
}
