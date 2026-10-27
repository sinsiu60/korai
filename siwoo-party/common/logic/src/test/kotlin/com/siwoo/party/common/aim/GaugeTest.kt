package com.siwoo.party.common.aim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GaugeTest {

    @Test
    fun `0에서 1까지 왕복하고 주기마다 되풀이된다`() {
        val g = Gauge(1000L)
        g.start(5000L)
        assertEquals(0f, g.valueAt(5000L), 1e-6f)
        assertEquals(0.5f, g.valueAt(5250L), 1e-6f)
        assertEquals(1f, g.valueAt(5500L), 1e-6f)
        assertEquals(0.5f, g.valueAt(5750L), 1e-6f)
        assertEquals(0f, g.valueAt(6000L), 1e-6f)
        for (t in 5000L..9000L step 13) {
            val v = g.valueAt(t)
            assertTrue(v in 0f..1f)
            assertEquals("주기", v, g.valueAt(t + 1000L), 1e-5f)
        }
        assertTrue(g.risingAt(5100L))
        assertFalse(g.risingAt(5600L))
        // 시작 전 시각은 0
        assertEquals(0f, g.valueAt(4000L), 0f)
    }

    @Test
    fun `triangle 은 지난 시간으로 바로 계산한다`() {
        assertEquals(0f, Gauge.triangle(0, 1000), 1e-6f)
        assertEquals(1f, Gauge.triangle(500, 1000), 1e-6f)
        assertEquals(0.5f, Gauge.triangle(1250, 1000), 1e-6f)
        assertEquals(0f, Gauge.triangle(-50, 1000), 1e-6f)
        assertEquals(30f, Gauge.map(0.5f, 10f, 50f), 1e-6f)
    }

    @Test
    fun `시작 전과 멈춘 뒤 값`() {
        val g = Gauge(1000L)
        assertFalse(g.running)
        assertEquals(0f, g.valueAt(123L), 0f)
        g.start(0L)
        assertTrue(g.running)
        val v = g.stop(400L)
        assertEquals(0.8f, v, 1e-6f)
        assertTrue(g.stopped)
        assertFalse(g.running)
        assertEquals("멈춘 값 유지", 0.8f, g.valueAt(9999L), 0f)
        g.start(10_000L)
        assertFalse(g.stopped)
        assertEquals(0f, g.valueAt(10_000L), 1e-6f)
        g.reset()
        assertEquals(0f, g.valueAt(10_300L), 0f)
    }

    @Test
    fun `톱니 모드는 0에서 1로 올라갔다 다시 0`() {
        val g = Gauge(1000L, pingPong = false)
        g.start(0L)
        assertEquals(0.25f, g.valueAt(250L), 1e-6f)
        assertEquals(0.9f, g.valueAt(900L), 1e-6f)
        assertEquals(0.1f, g.valueAt(1100L), 1e-6f)
        assertTrue(g.risingAt(700L))
    }

    @Test
    fun `이징은 값 범위를 지키고 SINE 은 끝에서 느리다`() {
        val g = Gauge(1000L, easing = Easing.SINE)
        g.start(0L)
        assertEquals(0f, g.valueAt(0L), 1e-6f)
        assertEquals(0.5f, g.valueAt(250L), 1e-5f)
        assertEquals(1f, g.valueAt(500L), 1e-6f)
        // 맨 위 근처 50ms 동안 움직인 양 < 가운데 50ms 동안 움직인 양
        val top = abs(g.valueAt(500L) - g.valueAt(450L))
        val mid = abs(g.valueAt(275L) - g.valueAt(225L))
        assertTrue("$top < $mid", top < mid)
        for (t in 0L..1000L step 7) assertTrue(g.valueAt(t) in 0f..1f)
    }

    @Test
    fun `주기를 바꾸면 빨라지고 바늘이 튀지 않는다`() {
        val g = Gauge(1000L, minPeriodMs = 300L)
        g.start(0L)
        val before = g.valueAt(200L)
        g.speedUp(2f, nowMs = 200L)
        assertEquals(500L, g.periodMs)
        assertEquals("이어짐", before, g.valueAt(200L), 1e-5f)
        // 새 속도: 100ms 에 0.4 만큼 (주기 500 → 왕복 1 당 250ms)
        assertEquals(before + 0.4f, g.valueAt(300L), 1e-4f)
        // 최소 주기 아래로는 안 감
        g.speedUp(10f, 300L)
        assertEquals(300L, g.periodMs)
        g.speedUp(10f, 300L)
        assertEquals(300L, g.periodMs)
        g.resetSpeed(400L)
        assertEquals(1000L, g.periodMs)
        assertEquals(1000L, g.basePeriodMs)
        // 멈춘 상태에서 바꾸면 다음 start 부터 새 주기
        g.stop(500L)
        g.setPeriod(2000L)
        g.start(0L)
        assertEquals(1f, g.valueAt(1000L), 1e-6f)
        g.setBasePeriod(800L)
        assertEquals(800L, g.periodMs)
    }

    @Test
    fun `정확도 - 구간 안은 1, 밖은 falloff 까지 줄어 0`() {
        val z = SweetZone(0.5f, 0.1f, 0.2f)
        assertEquals(1f, z.accuracy(0.5f), 0f)
        assertEquals(1f, z.accuracy(0.59f), 0f)
        assertEquals(1f, z.accuracy(0.41f), 0f)
        assertEquals(0.5f, z.accuracy(0.7f), 1e-5f)
        assertEquals(0.5f, z.accuracy(0.3f), 1e-5f)
        assertEquals(0f, z.accuracy(0.8f), 1e-6f)
        assertEquals(0f, z.accuracy(1f), 0f)
        assertTrue(0.55f in z)
        assertFalse(0.65f in z)
        // 멀어질수록 줄어듦 (단조)
        var prev = 1f
        var v = 0.5f
        while (v <= 1f) { val a = z.accuracy(v); assertTrue(a <= prev + 1e-6f); prev = a; v += 0.01f }
        // falloff 0 이면 구간 밖은 바로 0
        assertEquals(0f, SweetZone(0.5f, 0.1f, 0f).accuracy(0.61f), 0f)
    }

    @Test
    fun `여러 구간이면 가장 좋은 값과 구간 번호`() {
        val g = Gauge(1000L, zones = listOf(
            SweetZone(0.5f, 0.15f, 0.1f, weight = 0.6f),
            SweetZone(0.5f, 0.03f, 0.05f),
            SweetZone(0.9f, 0.04f, 0.1f),
        ))
        assertEquals(1f, g.accuracy(0.5f), 0f)
        assertEquals("넓은 구간만", 0.6f, g.accuracy(0.6f), 1e-6f)
        assertEquals(1f, g.accuracy(0.92f), 0f)
        assertEquals(0f, g.accuracy(0.05f), 0f)
        assertEquals(1, g.zoneIndex(0.5f))
        assertEquals(0, g.zoneIndex(0.62f))
        assertEquals(2, g.zoneIndex(0.9f))
        assertEquals(-1, g.zoneIndex(0.1f))
        assertEquals(0f, Gauge(1000L).accuracy(0.5f), 0f)
        g.start(0L)
        g.stop(250L)
        assertEquals(1f, g.stoppedAccuracy(), 0f)
    }
}
