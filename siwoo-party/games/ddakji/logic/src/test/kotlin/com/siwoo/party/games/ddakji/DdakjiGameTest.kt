package com.siwoo.party.games.ddakji

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DdakjiGameTest {

    /** nextFloat 이 항상 0 (= 무조건 뒤집힘) 또는 거의 1 (= 무조건 버팀) 인 난수 */
    private class Fixed(var flip: Boolean) : Random() {
        override fun nextBits(bitCount: Int): Int = if (flip) 0 else (-1 ushr (32 - bitCount))
    }

    private val minP = GameConfig.CHANCE_MIN
    private val maxP = GameConfig.CHANCE_MAX

    /** 힘 단계에서 시각 t 에 힘을, t+dt 에 각도를 멈춤 */
    private fun hit(g: DdakjiGame, t: Long, dt: Long = 300): Strike {
        g.startTurn(t)
        g.stopPower(t + 500)
        return g.stopAngle(t + 500 + dt)
    }

    @Test
    fun `게이지는 0에서 1까지 왕복한다`() {
        val p = 1000L
        assertEquals(0f, DdakjiGame.gauge(0, p), 1e-6f)
        assertEquals(0.5f, DdakjiGame.gauge(250, p), 1e-6f)
        assertEquals(1f, DdakjiGame.gauge(500, p), 1e-6f)
        assertEquals(0.5f, DdakjiGame.gauge(750, p), 1e-6f)
        assertEquals(0f, DdakjiGame.gauge(1000, p), 1e-6f)
        assertEquals(0.5f, DdakjiGame.gauge(1250, p), 1e-6f)
        for (t in 0L..5000L step 7) assertTrue(DdakjiGame.gauge(t, p) in 0f..1f)
    }

    @Test
    fun `확률은 항상 최소와 최대 사이`() {
        var lo = 1f
        var hi = 0f
        for (i in 0..100) for (j in 0..100) {
            val c = DdakjiGame.chance(i / 100f, j / 100f)
            assertTrue("($i,$j) → $c", c in minP..maxP)
            lo = minOf(lo, c)
            hi = maxOf(hi, c)
        }
        assertEquals("최악은 최소", minP, lo, 1e-6f)
        assertEquals("최고는 최대", maxP, hi, 1e-6f)
    }

    @Test
    fun `최적 구간이면 최대 확률`() {
        assertEquals(maxP, DdakjiGame.chance(GameConfig.POWER_SWEET, GameConfig.ANGLE_SWEET), 1e-6f)
        assertEquals(maxP, DdakjiGame.chance(GameConfig.POWER_SWEET + GameConfig.POWER_SWEET_HALF * 0.9f,
            GameConfig.ANGLE_SWEET - GameConfig.ANGLE_SWEET_HALF * 0.9f), 1e-6f)
        assertTrue("구간 밖은 최대보다 작음", DdakjiGame.chance(GameConfig.POWER_SWEET + GameConfig.POWER_SWEET_HALF + 0.05f, GameConfig.ANGLE_SWEET) < maxP)
        // 둘 다 크게 빗나가면 최소
        assertEquals(minP, DdakjiGame.chance(0f, 0f), 1e-6f)
        assertEquals(minP, DdakjiGame.chance(0.1f, 1f), 1e-6f)
    }

    @Test
    fun `최적 구간에서 멀어질수록 확률이 줄어든다`() {
        // 힘: 최적 가운데에서 위·아래로 멀어지기 (각도는 최적)
        for (dir in intArrayOf(-1, 1)) {
            var prev = DdakjiGame.chance(GameConfig.POWER_SWEET, GameConfig.ANGLE_SWEET)
            var strict = 0
            var v = GameConfig.POWER_SWEET
            while (v in 0f..1f) {
                val c = DdakjiGame.chance(v, GameConfig.ANGLE_SWEET)
                assertTrue("힘 $v: $c ≤ $prev", c <= prev + 1e-6f)
                if (c < prev - 1e-6f) strict++
                prev = c
                v += dir * 0.01f
            }
            assertTrue("실제로 줄어듦 ($dir)", strict > 5)
        }
        // 각도
        for (dir in intArrayOf(-1, 1)) {
            var prev = DdakjiGame.chance(GameConfig.POWER_SWEET, GameConfig.ANGLE_SWEET)
            var strict = 0
            var v = GameConfig.ANGLE_SWEET
            while (v in 0f..1f) {
                val c = DdakjiGame.chance(GameConfig.POWER_SWEET, v)
                assertTrue("각도 $v: $c ≤ $prev", c <= prev + 1e-6f)
                if (c < prev - 1e-6f) strict++
                prev = c
                v += dir * 0.01f
            }
            assertTrue("실제로 줄어듦 ($dir)", strict > 5)
        }
        // 정확도 자체
        assertEquals(1f, DdakjiGame.powerAccuracy(GameConfig.POWER_SWEET), 0f)
        assertEquals(0f, DdakjiGame.powerAccuracy(0f), 0f)
        assertTrue(DdakjiGame.angleAccuracy(0.3f) > DdakjiGame.angleAccuracy(0.2f))
    }

    @Test
    fun `게이지는 게임 시계로 움직이고 멈춘 값이 저장된다`() {
        val g = DdakjiGame(0, Fixed(true))
        g.startTurn(1000)
        assertEquals(0f, g.powerAt(1000), 1e-6f)
        val half = GameConfig.POWER_PERIOD_MS / 2
        assertEquals(1f, g.powerAt(1000 + half), 1e-6f)
        assertEquals(g.powerAt(1300), g.stopPower(1300), 0f)
        assertSame(Phase.ANGLE, g.phase)
        assertEquals(0f, g.angleAt(1300), 1e-6f)
        val s = g.stopAngle(1300 + GameConfig.ANGLE_PERIOD_MS / 4)
        assertEquals(0.5f, s.angle, 1e-6f)
        assertEquals(DdakjiGame.chance(s.power, s.angle), s.chance, 0f)
        assertSame(Phase.STRIKE, g.phase)
    }

    @Test
    fun `뒤집으면 딱지를 가져오고 한 번 더 공격`() {
        val g = DdakjiGame(1, Fixed(true))
        val s = hit(g, 0)
        assertTrue(s.flipped)
        assertEquals(1, s.attacker)
        // 판정만으로는 수가 안 바뀜 (연출 뒤 resolve)
        assertArrayEquals(intArrayOf(5, 5), g.counts)
        g.resolve()
        assertArrayEquals(intArrayOf(4, 6), g.counts)
        assertEquals("공격 유지", 1, g.attacker)
        assertSame(Phase.POWER, g.phase)
        assertEquals(1, g.flips)
    }

    @Test
    fun `못 뒤집으면 공격 교대`() {
        val g = DdakjiGame(0, Fixed(false))
        val s = hit(g, 0)
        assertFalse(s.flipped)
        g.resolve()
        assertArrayEquals(intArrayOf(5, 5), g.counts)
        assertEquals(1, g.attacker)
        assertEquals(0, g.defender)
        assertSame(Phase.POWER, g.phase)
    }

    @Test
    fun `딱지를 모두 잃으면 끝`() {
        val r = Fixed(true)
        val g = DdakjiGame(0, r)
        repeat(GameConfig.DDAKJI_COUNT - 1) {
            hit(g, it * 5000L)
            g.resolve()
            assertSame(Phase.POWER, g.phase)
        }
        assertArrayEquals(intArrayOf(9, 1), g.counts)
        // 한 번 실패 → 교대 → 1번이 하나 되찾음
        r.flip = false
        hit(g, 100_000); g.resolve()
        assertEquals(1, g.attacker)
        r.flip = true
        hit(g, 200_000); g.resolve()
        assertArrayEquals(intArrayOf(8, 2), g.counts)
        assertEquals(1, g.attacker)
        r.flip = false
        hit(g, 300_000); g.resolve()
        assertEquals(0, g.attacker)
        r.flip = true
        hit(g, 400_000); g.resolve()
        assertSame(Phase.POWER, g.phase)
        hit(g, 500_000); g.resolve()
        assertSame(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(10, 0), g.counts)
        assertEquals(0, g.winner)
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
        assertNotNull(g.last)
        assertEquals(GameConfig.DDAKJI_COUNT * 2, g.counts.sum())
    }

    @Test
    fun `2번이 이기면 순위 2 1`() {
        val g = DdakjiGame(1, Fixed(true))
        repeat(GameConfig.DDAKJI_COUNT) { hit(g, it * 5000L); g.resolve() }
        assertSame(Phase.OVER, g.phase)
        assertEquals(1, g.winner)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test
    fun `확률대로 뒤집힌다 (시드 고정 대략 비율)`() {
        val rnd = Random(42)
        var flipped = 0
        val n = 400
        // 최적에 가깝게: 힘은 0.78 (주기 1500 → 585ms), 각도 0.5 (주기 1200 → 300ms)
        repeat(n) {
            val g = DdakjiGame(0, rnd)
            g.startTurn(0)
            g.stopPower(585)
            val s = g.stopAngle(585 + 300)
            assertEquals(maxP, s.chance, 1e-3f)
            if (s.flipped) flipped++
        }
        assertTrue("뒤집힘 비율 ${flipped}/$n", flipped.toFloat() / n > 0.75f)
    }
}
