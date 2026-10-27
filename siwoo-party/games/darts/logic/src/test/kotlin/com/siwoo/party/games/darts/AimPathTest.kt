package com.siwoo.party.games.darts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** 흔들리는 조준점: 같은 seed 면 같은 궤적, 크기·라운드 배율, 빗나감 범위 */
class AimPathTest {

    private fun sample(seed: Long, round: Int = 1, darts: Int = 3): List<Float> {
        val a = AimPath(seed)
        val out = FloatArray(2)
        val list = ArrayList<Float>()
        repeat(darts) {
            a.newDart(round)
            var t = 0L
            while (t < 4000L) {
                a.point(t, out)
                list.add(out[0]); list.add(out[1])
                t += 37L
            }
            a.scatter(out)
            list.add(out[0]); list.add(out[1])
        }
        return list
    }

    @Test
    fun `같은 seed 면 궤적·빗나감이 똑같다`() {
        assertEquals(sample(42L), sample(42L))
        assertEquals(sample(7L, round = 5), sample(7L, round = 5))
    }

    @Test
    fun `seed 가 다르면 궤적이 다르다`() {
        assertNotEquals(sample(1L), sample(2L))
    }

    @Test
    fun `같은 시각이면 같은 위치 (프레임 수와 무관)`() {
        val a = AimPath(9L)
        a.newDart(1)
        val p = FloatArray(2)
        val q = FloatArray(2)
        a.point(1234L, p)
        a.point(50L, q)
        a.point(1234L, q)
        assertEquals(p[0], q[0], 0f)
        assertEquals(p[1], q[1], 0f)
    }

    @Test
    fun `조준점은 8자 크기 안에서 움직이고 가운데 근처도 지난다`() {
        val a = AimPath(3L)
        val out = FloatArray(2)
        val lim = sqrt(GameConfig.SWAY_AMP_X * GameConfig.SWAY_AMP_X + GameConfig.SWAY_AMP_Y * GameConfig.SWAY_AMP_Y) + GameConfig.SWAY_JITTER * 2f
        repeat(5) {
            a.newDart(1)
            var minR = Float.MAX_VALUE
            var maxR = 0f
            var t = 0L
            while (t < GameConfig.SWAY_PERIOD_MS) {
                a.point(t, out)
                val r = Board.radius(out[0], out[1])
                minR = minOf(minR, r)
                maxR = maxOf(maxR, r)
                t += 5L
            }
            assertTrue("바깥 한계 $maxR", maxR <= lim)
            assertTrue("트리플 고리 너머까지 $maxR", maxR > GameConfig.R_TREBLE_OUT)
            assertTrue("불 근처를 지남 $minR", minR < GameConfig.R_OUTER_BULL)
        }
    }

    @Test
    fun `라운드가 갈수록 흔들림이 조금씩 커지고 최대가 있다`() {
        assertEquals(1f, AimPath.scaleFor(1), 1e-6f)
        assertEquals(1.03f, AimPath.scaleFor(2), 1e-5f)
        assertEquals(1.21f, AimPath.scaleFor(8), 1e-5f)
        assertEquals(1.21f, AimPath.scaleFor(15), 1e-5f)
        val a = AimPath(5L)
        a.newDart(8)
        assertEquals(1.21f, a.scale, 1e-5f)
    }

    @Test
    fun `빗나감은 SCATTER_MM 원 안`() {
        val a = AimPath(11L)
        val out = FloatArray(2)
        repeat(500) {
            out[0] = 0f; out[1] = 0f
            a.scatter(out)
            assertTrue(Board.radius(out[0], out[1]) <= GameConfig.SCATTER_MM + 1e-4f)
        }
    }

    @Test
    fun `궤적 미리 보기는 떨림 없는 8자`() {
        val a = AimPath(4L)
        a.newDart(1)
        val xs = FloatArray(GameConfig.TRAIL_POINTS)
        val ys = FloatArray(GameConfig.TRAIL_POINTS)
        a.trail(xs, ys)
        // 미리 보기 뒤에도 떨림 설정은 그대로
        assertEquals(GameConfig.SWAY_JITTER, a.sway.jitter, 0f)
        for (k in xs.indices) assertTrue(Board.radius(xs[k], ys[k]) <= GameConfig.SWAY_AMP_X * 1.01f + 1f + GameConfig.SWAY_AMP_Y)
        assertNotEquals(xs[0], xs[GameConfig.TRAIL_POINTS / 4])
    }
}
