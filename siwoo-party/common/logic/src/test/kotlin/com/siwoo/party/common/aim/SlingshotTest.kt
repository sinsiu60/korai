package com.siwoo.party.common.aim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class SlingshotTest {

    @Test
    fun `당긴 반대쪽으로 쏘고 힘은 거리 비율`() {
        val s = Slingshot(maxDistance = 100f, minDistance = 10f)
        s.begin(200f, 200f)
        assertTrue(s.active)
        s.move(170f, 240f) // 왼쪽 아래로 50 당김 (3-4-5)
        assertEquals(50f, s.distance, 1e-4f)
        assertEquals(0.6f, s.dirX, 1e-5f)
        assertEquals(-0.8f, s.dirY, 1e-5f)
        assertEquals(1f, sqrt(s.dirX * s.dirX + s.dirY * s.dirY), 1e-5f)
        assertEquals(0.5f, s.power, 1e-5f)
        assertFalse(s.cancelled)
        assertEquals(kotlin.math.atan2(-0.8f, 0.6f), s.angle, 1e-5f)
        // 당긴 점 = 손가락 (최대 안쪽)
        assertEquals(170f, s.pullX, 1e-4f)
        assertEquals(240f, s.pullY, 1e-4f)
    }

    @Test
    fun `끈 쪽으로 쏘는 설정`() {
        val s = Slingshot(maxDistance = 100f, pullBack = false)
        s.begin(0f, 0f)
        s.move(0f, 40f)
        assertEquals(0f, s.dirX, 1e-6f)
        assertEquals(1f, s.dirY, 1e-6f)
        assertEquals((PI / 2).toFloat(), s.angle, 1e-5f)
    }

    @Test
    fun `최대 거리에서 힘은 1로 잘리고 당긴 점도 잘린다`() {
        val s = Slingshot(maxDistance = 100f, minDistance = 10f)
        s.begin(0f, 0f)
        s.move(300f, 0f)
        assertEquals(1f, s.power, 0f)
        assertEquals(300f, s.distance, 1e-4f)
        assertEquals(100f, s.clampedDistance, 1e-4f)
        assertEquals(100f, s.pullX, 1e-4f)
        assertEquals(0f, s.pullY, 1e-4f)
        assertEquals(-1f, s.dirX, 1e-6f)
        // 힘 곡선
        val e = Slingshot(maxDistance = 100f, powerEasing = Easing.EASE_IN)
        e.begin(0f, 0f); e.move(50f, 0f)
        assertEquals(0.25f, e.power, 1e-5f)
    }

    @Test
    fun `최소 거리보다 짧으면 취소`() {
        val s = Slingshot(maxDistance = 100f, minDistance = 20f)
        s.begin(50f, 50f)
        s.move(55f, 60f)
        assertTrue(s.cancelled)
        assertFalse("짧게 떼면 안 쏨", s.release(55f, 60f))
        assertFalse(s.active)
        s.begin(50f, 50f)
        assertEquals("제자리", 0f, s.dirX, 0f)
        assertEquals(0f, s.power, 0f)
        assertTrue(s.release(50f, 90f))
        assertEquals(0.4f, s.power, 1e-5f)
        assertEquals("값은 남음", -1f, s.dirY, 1e-6f)
        assertFalse("끄는 중 아니면 false", s.release(0f, 0f))
        s.begin(0f, 0f); s.move(80f, 0f); s.cancel()
        assertFalse(s.active)
        assertEquals(0f, s.distance, 0f)
        // 기본 최소 거리 = 최대의 15%
        assertEquals(15f, Slingshot(maxDistance = 100f).minDistance, 1e-5f)
    }

    @Test
    fun `안내 점선은 쏘는 방향으로 고르게`() {
        val s = Slingshot(maxDistance = 100f)
        s.begin(100f, 100f)
        s.move(100f, 160f) // 아래로 당김 → 위로 쏨
        val out = FloatArray(20)
        val n = s.guidePoints(10f, 20f, 15f, 5, out)
        assertEquals(5, n)
        for (k in 0 until n) {
            assertEquals(10f, out[2 * k], 1e-4f)
            assertEquals(20f - 15f * (k + 1), out[2 * k + 1], 1e-4f)
        }
        // 배열이 작으면 거기까지
        assertEquals(3, s.guidePoints(0f, 0f, 1f, 10, FloatArray(6)))
        // 길이로: 마지막 점이 정확히 length
        val m = s.guideLine(0f, 0f, 120f, 8, out)
        assertEquals(8, m)
        assertEquals(-120f, out[2 * 7 + 1], 1e-3f)
        assertEquals(-15f, out[1], 1e-4f)
        // 방향이 없으면 0개
        s.begin(0f, 0f)
        assertEquals(0, s.guidePoints(0f, 0f, 10f, 5, out))
        assertEquals(0, s.guideLine(0f, 0f, 10f, 0, out))
    }

    @Test
    fun `포물선 안내는 중력으로 휜다`() {
        val s = Slingshot(maxDistance = 100f)
        s.begin(0f, 0f)
        s.move(-100f, 0f) // 왼쪽으로 끝까지 → 오른쪽으로 힘 1
        val out = FloatArray(10)
        val n = s.arcPoints(0f, 0f, maxSpeed = 200f, gravity = 100f, stepSec = 0.5f, count = 5, out = out)
        assertEquals(5, n)
        assertEquals(100f, out[0], 1e-3f)
        assertEquals(12.5f, out[1], 1e-3f)
        assertEquals(200f, out[2], 1e-3f)
        assertEquals(50f, out[3], 1e-3f)
        // 점점 더 아래로 (y 증가량이 커짐)
        assertTrue(out[5] - out[3] > out[3] - out[1])
    }
}
