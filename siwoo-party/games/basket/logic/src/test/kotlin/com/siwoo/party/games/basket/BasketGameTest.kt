package com.siwoo.party.games.basket

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 점수 규칙(2·3점, 연속 불꽃 보너스), 차례·거리 진행, 합계 순위 */
class BasketGameTest {

    /** 진행 중인 슛을 끝까지 */
    private fun fly(g: BasketGame) {
        var n = 0
        while (g.advance(16) && n < 2000) n++
        assertSame(Phase.SETTLED, g.phase)
    }

    // ── 2점 / 3점 ──

    @Test fun `3점 라인보다 멀면 3점 아니면 2점`() {
        assertEquals(2, Scoring.basePoints(2.4f))
        assertEquals(2, Scoring.basePoints(6.1f))
        assertEquals("라인 위는 2점", 2, Scoring.basePoints(GameConfig.THREE_PT_LINE))
        assertEquals(3, Scoring.basePoints(7.0f))
        assertEquals(3, Scoring.basePoints(8.2f))
        assertTrue(Scoring.isThree(7.6f))
        assertFalse(Scoring.isThree(5.4f))
    }

    @Test fun `슛 자리는 10개이고 점점 멀어지며 뒤쪽은 3점`() {
        val d = GameConfig.DISTANCES
        assertEquals(GameConfig.SHOTS, d.size)
        for (i in 1 until d.size) assertTrue(d[i] > d[i - 1])
        val threes = d.count { it > GameConfig.THREE_PT_LINE }
        assertEquals(3, threes)
        assertTrue("마지막 자리는 3점", Scoring.isThree(d.last()))
        assertFalse("첫 자리는 2점", Scoring.isThree(d.first()))
        val g = BasketGame(2)
        for (r in 0 until GameConfig.SHOTS) assertEquals(d[r], g.distanceAt(r), 0f)
    }

    @Test fun `슛 수를 줄이면 가까운 곳부터 먼 곳까지 고르게`() {
        val g = BasketGame(2, 4)
        assertEquals(GameConfig.DISTANCES.first(), g.distanceAt(0), 0f)
        assertEquals(GameConfig.DISTANCES.last(), g.distanceAt(3), 0f)
        assertTrue(g.distanceAt(1) < g.distanceAt(2))
        assertEquals(GameConfig.DISTANCES.first(), BasketGame(2, 1).distanceAt(0), 0f)
    }

    // ── 연속 보너스 ──

    @Test fun `연속 두 골부터 불꽃 - 불꽃일 때 넣으면 +1`() {
        assertFalse(Scoring.onFire(0))
        assertFalse(Scoring.onFire(1))
        assertTrue(Scoring.onFire(2))
        assertEquals(0, Scoring.bonus(0))
        assertEquals(0, Scoring.bonus(1))
        assertEquals(1, Scoring.bonus(2))
        assertEquals(1, Scoring.bonus(5))
        assertEquals(2, Scoring.points(3f, 0, true))
        assertEquals(2, Scoring.points(3f, 1, true))
        assertEquals("불꽃 2점 자리", 3, Scoring.points(3f, 2, true))
        assertEquals("불꽃 3점 자리", 4, Scoring.points(7.6f, 3, true))
        assertEquals("놓치면 0", 0, Scoring.points(7.6f, 3, false))
        assertEquals(3, Scoring.nextStreak(2, true))
        assertEquals("놓치면 연속 0", 0, Scoring.nextStreak(4, false))
    }

    @Test fun `게임에서 - 불꽃 시작·보너스·놓치면 꺼짐`() {
        val g = BasketGame(1)
        // 1골: 2점, 불꽃 아님
        g.forceShot(true)
        assertEquals(2, g.outcome.points)
        assertEquals(0, g.outcome.bonus)
        assertFalse(g.outcome.fireStarted)
        assertFalse(g.onFire(0))
        g.next()
        // 2골: 2점, 불꽃 붙음
        g.forceShot(true)
        assertEquals(2, g.outcome.points)
        assertTrue("두 번째 연속 골에 불꽃", g.outcome.fireStarted)
        assertTrue(g.onFire(0))
        g.next()
        // 3골: 불꽃 보너스 +1
        g.forceShot(true)
        assertEquals(1, g.outcome.bonus)
        assertEquals(3, g.outcome.points)
        assertFalse("이미 불꽃", g.outcome.fireStarted)
        assertEquals(3, g.outcome.streakAfter)
        g.next()
        // 놓침: 0점, 불꽃 꺼짐
        g.forceShot(false)
        assertEquals(0, g.outcome.points)
        assertTrue(g.outcome.fireLost)
        assertFalse(g.onFire(0))
        assertEquals(0, g.streaks[0])
        g.next()
        // 다시 1골: 보너스 없음
        g.forceShot(true)
        assertEquals(0, g.outcome.bonus)
        assertEquals(2, g.outcome.points)
        assertEquals(2 + 2 + 3 + 0 + 2, g.total(0))
        assertEquals(4, g.makes[0])
    }

    @Test fun `3점 자리 불꽃 보너스`() {
        val g = BasketGame(1)
        // 7번째 자리까지 모두 넣기 (불꽃 유지) → 8번째(7.0m) 3점 + 1
        repeat(7) { g.forceShot(true); g.next() }
        assertEquals(7.0f, g.distance, 0f)
        g.forceShot(true)
        assertEquals(3, g.outcome.base)
        assertEquals(1, g.outcome.bonus)
        assertEquals(4, g.outcome.points)
        // 합: 2+2 + (3×5) + 4 = 23
        assertEquals(23, g.total(0))
    }

    @Test fun `연속은 사람마다 따로 센다`() {
        val g = BasketGame(2)
        g.forceShot(true); g.next() // 0 골
        g.forceShot(false); g.next() // 1 놓침
        g.forceShot(true); g.next() // 0 골 → 불꽃
        assertTrue(g.onFire(0))
        assertFalse(g.onFire(1))
        g.forceShot(true); g.next() // 1 골 (연속 1)
        assertFalse(g.onFire(1))
        g.forceShot(true) // 0 골 + 보너스
        assertEquals(1, g.outcome.bonus)
    }

    // ── 차례·진행 ──

    @Test fun `돌아가며 한 번씩 - 한 바퀴마다 다음 자리`() {
        val g = BasketGame(3)
        val seen = mutableListOf<Int>()
        val rounds = mutableListOf<Int>()
        while (g.phase != Phase.OVER) {
            seen += g.current
            rounds += g.round
            assertEquals(g.distanceAt(g.round), g.distance, 0f)
            assertEquals(-g.distance, g.court.ball.x, 0f)
            assertEquals(GameConfig.RELEASE_Y, g.court.ball.y, 0f)
            g.forceShot(seen.size % 2 == 0)
            val newRound = g.next()
            assertEquals(seen.size % 3 == 0, newRound)
        }
        assertEquals(30, seen.size)
        assertEquals(30, g.throws)
        for (i in seen.indices) {
            assertEquals(i % 3, seen[i])
            assertEquals(i / 3, rounds[i])
        }
        for (p in 0 until 3) for (r in 0 until GameConfig.SHOTS) assertTrue(g.marks[p][r] != 0)
    }

    @Test fun `단계가 맞지 않으면 무시`() {
        val g = BasketGame(2)
        assertFalse("결과 전 next 무시", g.next())
        assertTrue(g.launch(55f, Aim.idealSpeed(g.distance, 55f)))
        assertSame(Phase.FLYING, g.phase)
        assertFalse("나는 중 또 던지기 무시", g.launch(55f, 5f))
        assertFalse(g.forceShot(true))
        fly(g)
        assertFalse("결과 보는 중 던지기 무시", g.shootSwipe(75f, 0.5f))
        assertEquals(1, g.throws)
    }

    @Test fun `스와이프 → 슛 - 알맞게 밀면 골, 약하게 밀면 놓침`() {
        val g = BasketGame(2)
        assertTrue(g.shootSwipe(GameConfig.SWIPE_CENTER_DEG, GameConfig.SWEET_POWER))
        val v = g.court.ball.speed
        assertEquals(Aim.idealSpeed(g.distance, GameConfig.LAUNCH_BASE_DEG), v, 1e-3f)
        fly(g)
        assertTrue(g.outcome.made)
        assertTrue(g.outcome.clean)
        assertEquals(2, g.outcome.points)
        assertEquals(2, g.total(0))
        g.next()
        assertTrue(g.shootSwipe(GameConfig.SWIPE_CENTER_DEG, 0.05f))
        fly(g)
        assertFalse(g.outcome.made)
        assertEquals(0, g.total(1))
        assertEquals(1, g.marks[1][0])
        assertEquals(2, g.marks[0][0])
    }

    @Test fun `물리로 끝까지 - 결과가 결정적`() {
        fun play(): IntArray {
            val g = BasketGame(2)
            var k = 0
            while (g.phase != Phase.OVER) {
                val power = floatArrayOf(0.5f, 0.3f, 0.55f, 0.8f, 0.5f)[k % 5]
                val ang = floatArrayOf(75f, 90f, 60f, 75f, 80f)[k % 5]
                assertTrue(g.shootSwipe(ang, power))
                fly(g)
                g.next()
                k++
            }
            return g.scores.copyOf()
        }
        val a = play()
        val b = play()
        assertArrayEquals(a, b)
        assertTrue("누군가는 넣음", a.sum() > 0)
    }

    // ── 순위 ──

    @Test fun `합계 점수 순위 (동점 같은 순위)`() {
        val g = BasketGame(4, 2)
        // 2슛이면 2.4m(2점)·8.2m(3점). 바퀴 1: 0 골, 1 놓침, 2 골, 3 골 / 바퀴 2: 0 골, 1 놓침, 2 놓침, 3 골
        val plan = booleanArrayOf(true, false, true, true, true, false, false, true)
        for (m in plan) { g.forceShot(m); g.next() }
        assertSame(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(5, 0, 2, 5), g.scores)
        assertArrayEquals(intArrayOf(1, 4, 3, 1), g.ranks())
    }

    @Test fun `끝나면 더 못 던짐`() {
        val g = BasketGame(2, 1)
        g.forceShot(true); g.next()
        g.forceShot(false)
        assertTrue(g.next())
        assertSame(Phase.OVER, g.phase)
        assertFalse(g.forceShot(true))
        assertFalse(g.next())
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }
}
