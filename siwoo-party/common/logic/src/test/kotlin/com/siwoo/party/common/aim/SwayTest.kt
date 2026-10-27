package com.siwoo.party.common.aim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class SwayTest {

    @Test
    fun `8자 궤적은 주기적이고 대칭이다`() {
        val s = Sway(SwayShape.FIGURE8, amplitudeX = 40f, amplitudeY = 20f, periodMs = 2000L)
        assertEquals(0f, s.x(0L), 1e-4f)
        assertEquals(0f, s.y(0L), 1e-4f)
        // 1/4 주기: x 최대, y 다시 0 (8자 가운데로 돌아오는 건 반 주기)
        assertEquals(40f, s.x(500L), 1e-3f)
        assertEquals(0f, s.y(500L), 1e-3f)
        assertEquals(20f, s.y(250L), 1e-3f)
        for (t in 0L..4000L step 37) {
            // 주기
            assertEquals(s.x(t), s.x(t + 2000L), 1e-3f)
            assertEquals(s.y(t), s.y(t + 2000L), 1e-3f)
            // 반 주기 뒤엔 좌우만 뒤집힌 같은 높이 (8자의 두 고리)
            assertEquals(-s.x(t), s.x(t + 1000L), 1e-3f)
            assertEquals(s.y(t), s.y(t + 1000L), 1e-3f)
            // 시간을 거꾸로 하면 가운데 점 대칭
            assertEquals(-s.x(t), s.x(-t), 1e-3f)
            assertEquals(-s.y(t), s.y(-t), 1e-3f)
            // 크기 범위
            assertTrue(abs(s.x(t)) <= 40f + 1e-3f)
            assertTrue(abs(s.y(t)) <= 20f + 1e-3f)
        }
    }

    @Test
    fun `세로 8자는 가로 8자를 돌린 모양`() {
        val h = Sway(SwayShape.FIGURE8, amplitudeX = 30f, periodMs = 1600L)
        val v = Sway(SwayShape.FIGURE8_VERTICAL, amplitudeX = 30f, periodMs = 1600L)
        for (t in 0L..1600L step 50) {
            assertEquals(h.x(t), v.y(t), 1e-3f)
            assertEquals(h.y(t), v.x(t), 1e-3f)
        }
    }

    @Test
    fun `원 궤적은 가운데에서 늘 같은 거리`() {
        val s = Sway(SwayShape.CIRCLE, amplitudeX = 25f, periodMs = 1200L)
        for (t in 0L..2400L step 31) {
            val r = sqrt(s.x(t) * s.x(t) + s.y(t) * s.y(t))
            assertEquals(25f, r, 1e-3f)
            assertEquals(s.x(t), s.x(t + 1200L), 1e-3f)
        }
        assertEquals(25f, s.x(0L), 1e-4f)
        assertEquals(25f, s.y(300L), 1e-3f)
    }

    @Test
    fun `흔들림 크기와 배율은 비례한다`() {
        val a = Sway(SwayShape.FIGURE8, amplitudeX = 10f, amplitudeY = 6f, periodMs = 2000L)
        val b = Sway(SwayShape.FIGURE8, amplitudeX = 30f, amplitudeY = 18f, periodMs = 2000L)
        val out = FloatArray(2)
        for (t in 0L..2000L step 41) {
            assertEquals(a.x(t) * 3f, b.x(t), 1e-3f)
            assertEquals(a.y(t) * 3f, b.y(t), 1e-3f)
            assertEquals(a.x(t) * 0.5f, a.x(t, 0.5f), 1e-4f)
            a.offset(t, 2f, out)
            assertEquals(a.x(t) * 2f, out[0], 1e-4f)
            assertEquals(a.y(t) * 2f, out[1], 1e-4f)
        }
        a.setAmplitude(0f)
        assertEquals(0f, a.x(500L), 0f)
        // phaseMs 로 시작 위치 이동
        val p = Sway(SwayShape.FIGURE8, amplitudeX = 10f, periodMs = 2000L, phaseMs = 500L)
        assertEquals(10f, p.x(0L), 1e-3f)
    }

    @Test
    fun `떨림은 시드로 정해지고 크기 안에서 부드럽다`() {
        val a = Sway(SwayShape.CIRCLE, amplitudeX = 0f, jitter = 5f, jitterPeriodMs = 300L, seed = 3)
        val b = Sway(SwayShape.CIRCLE, amplitudeX = 0f, jitter = 5f, jitterPeriodMs = 300L, seed = 3)
        val c = Sway(SwayShape.CIRCLE, amplitudeX = 0f, jitter = 5f, jitterPeriodMs = 300L, seed = 4)
        var diff = 0
        var moved = 0f
        for (t in 0L..6000L step 10) {
            assertEquals("같은 시드 같은 값", a.x(t), b.x(t), 0f)
            assertEquals(a.y(t), b.y(t), 0f)
            assertTrue(abs(a.x(t)) <= 5f + 1e-4f)
            assertTrue(abs(a.y(t)) <= 5f + 1e-4f)
            if (abs(a.x(t) - c.x(t)) > 1e-3f) diff++
            // 10ms 사이 크게 튀지 않음 (최대 기울기 ≈ 1.5 × 2 × 5 / 300ms)
            assertTrue(abs(a.x(t + 10) - a.x(t)) < 0.6f)
            moved += abs(a.x(t + 10) - a.x(t))
        }
        assertTrue("다른 시드는 다름", diff > 100)
        assertTrue("실제로 떨림", moved > 10f)
        // 음수 시각도 연속
        assertTrue(abs(a.x(-1L) - a.x(0L)) < 0.1f)
        assertNotEquals(0f, a.x(1234L))
    }

    @Test
    fun `숨 참기 - 처음엔 줄고 한계 뒤 다시 커진다`() {
        val h = BreathHold(minMultiplier = 0.3f, calmMs = 600L, holdLimitMs = 2000L, growMs = 1500L, maxMultiplier = 2f)
        assertEquals(1f, h.multiplier(0L), 0f)
        // 줄어드는 구간: 단조 감소
        var prev = 1f
        for (t in 0L..600L step 20) {
            val m = h.multiplier(t)
            assertTrue("$t: $m ≤ $prev", m <= prev + 1e-6f)
            prev = m
        }
        assertEquals(0.3f, h.multiplier(600L), 1e-6f)
        assertEquals("한계까지 가장 작음", 0.3f, h.multiplier(1999L), 1e-6f)
        assertEquals(0.3f, h.multiplier(2000L), 1e-6f)
        // 한계 뒤: 단조 증가, 이어짐
        assertTrue(abs(h.multiplier(2010L) - 0.3f) < 0.01f)
        prev = 0.3f
        for (t in 2000L..3500L step 25) {
            val m = h.multiplier(t)
            assertTrue("$t: $m ≥ $prev", m >= prev - 1e-6f)
            prev = m
        }
        assertEquals(2f, h.multiplier(3500L), 1e-5f)
        assertEquals("그 뒤는 최대", 2f, h.multiplier(10_000L), 0f)
        assertTrue("한계 뒤엔 처음보다 커짐", h.multiplier(3000L) > 1f)
    }

    @Test
    fun `숨 참기 상태 - 누르고 떼기`() {
        val h = BreathHold()
        assertFalse(h.holding)
        assertEquals(1f, h.multiplierAt(500L), 0f)
        assertEquals(1f, h.breathLeft(500L), 0f)
        h.press(1000L)
        assertTrue(h.holding)
        assertEquals(1f, h.multiplierAt(1000L), 1e-6f)
        assertTrue(h.multiplierAt(1500L) < 0.5f)
        assertEquals(0.5f, h.breathLeft(2000L), 1e-6f)
        assertFalse(h.outOfBreath(2900L))
        assertTrue(h.outOfBreath(3100L))
        assertTrue(h.multiplierAt(4300L) > 1f)
        assertEquals(1500L, h.release(2500L))
        assertFalse(h.holding)
        assertEquals(0L, h.release(2600L))
        assertEquals(1f, h.multiplierAt(2600L), 0f)
        assertEquals(0L, h.heldMs(2600L))
    }
}
