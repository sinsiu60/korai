package com.siwoo.party.games.archery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 양궁 점수: 고리 경계·X 와 10·빗나감, 바람에 밀리는 방향·크기 */
class TargetTest {

    private val r = GameConfig.ARROW_R
    private val eps = 0.001f

    @Test
    fun `고리마다 경계 안쪽은 높은 점수, 바로 바깥은 한 점 낮음`() {
        for (k in 1..GameConfig.RINGS) {
            val edge = k.toFloat()
            val inner = GameConfig.RINGS + 1 - k
            assertEquals("고리 $k 경계 (화살이 선에 걸침)", inner, Target.score(edge + r))
            assertEquals("고리 $k 안쪽", inner, Target.score(edge - 0.5f))
            assertEquals("고리 $k 바로 바깥", inner - 1, Target.score(edge + r + eps))
        }
    }

    @Test
    fun `가운데는 X 이고 10점, X 링 바깥 10점 고리는 X 아님`() {
        assertEquals(10, Target.score(0f))
        assertTrue(Target.isX(0f))
        assertTrue("X 경계", Target.isX(GameConfig.X_R + r))
        assertEquals(10, Target.score(GameConfig.X_R + r + eps))
        assertFalse("X 링 바로 바깥", Target.isX(GameConfig.X_R + r + eps))
        assertEquals("10점이지만 X 아님", 10, Target.score(0.9f))
        assertFalse(Target.isX(0.9f))
        assertEquals("X", Target.label(10, true))
        assertEquals("10", Target.label(10, false))
        assertEquals(11, Target.index(10, true))
    }

    @Test
    fun `과녁 밖은 0점 M`() {
        assertEquals(1, Target.score(GameConfig.FACE_R + r))
        assertEquals(Target.MISS, Target.score(GameConfig.FACE_R + r + eps))
        assertEquals(Target.MISS, Target.score(25f))
        assertEquals("M", Target.label(Target.MISS, false))
        assertFalse(Target.isX(25f))
        assertEquals(-1, Target.colorBand(0))
    }

    @Test
    fun `점수 색 묶음 - 금 10 9, 빨강 8 7, 파랑 6 5, 검정 4 3, 흰색 2 1`() {
        val bands = intArrayOf(-1, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4)
        for (s in 0..10) assertEquals("점수 $s", bands[s], Target.colorBand(s))
    }

    @Test
    fun `방향과 상관없이 거리로만 점수`() {
        for (deg in 0 until 360 step 30) {
            val a = Math.toRadians(deg.toDouble())
            val d = 4.5f
            assertEquals(6, Target.scoreAt((cos(a) * d).toFloat(), (sin(a) * d).toFloat()))
        }
        assertEquals(8, Target.scoreAt(1.8f, 1.8f)) // 거리 2.55
    }

    @Test
    fun `바람은 부는 쪽으로 세기 비례만큼 민다`() {
        val right = Wind(0, 20)
        assertEquals(2f * GameConfig.WIND_DRIFT, right.driftX, 1e-5f)
        assertEquals(0f, right.driftY, 1e-5f)
        val down = Wind(90, 10)
        assertEquals(0f, down.driftX, 1e-5f)
        assertEquals("아래 (+y)", GameConfig.WIND_DRIFT, down.driftY, 1e-5f)
        val left = Wind(180, 30)
        assertEquals(-3f * GameConfig.WIND_DRIFT, left.driftX, 1e-5f)
        val up = Wind(270, 40)
        assertEquals("위 (−y)", -4f * GameConfig.WIND_DRIFT, up.driftY, 1e-5f)
        val diag = Wind(45, 40)
        val len = sqrt(diag.driftX * diag.driftX + diag.driftY * diag.driftY)
        assertEquals("크기 = 세기 × WIND_DRIFT", 4f * GameConfig.WIND_DRIFT, len, 1e-4f)
        assertTrue(diag.driftX > 0f && diag.driftY > 0f)
        assertEquals(0f, Wind.CALM.driftX, 0f)
        assertEquals("2.4", Wind(0, 24).speedText)
        assertEquals("0.5", Wind(0, 5).speedText)
        // 가운데를 조준하면 바람만큼 밀려 꽂힘
        assertEquals(right.driftX, ArcheryGame.land(0f, right.driftX, 0f), 0f)
    }

    @Test
    fun `무작위 바람은 범위 안, 방향은 간격 단위, 시드가 같으면 같음`() {
        val a = Random(11)
        val b = Random(11)
        var dirs = HashSet<Int>()
        repeat(500) {
            val w = Wind.random(a)
            val v = Wind.random(b)
            assertEquals(w.dirDeg, v.dirDeg)
            assertEquals(w.speed10, v.speed10)
            assertTrue(w.speed10 in GameConfig.WIND_MIN10..GameConfig.WIND_MAX10)
            assertEquals(0, w.dirDeg % GameConfig.WIND_STEP_DEG)
            assertTrue(w.dirDeg in 0 until 360)
            dirs.add(w.dirDeg)
        }
        assertEquals("모든 방향이 나옴", 360 / GameConfig.WIND_STEP_DEG, dirs.size)
        val max = Wind(0, GameConfig.WIND_MAX10)
        assertTrue("가장 센 바람도 3 고리 안쪽", abs(max.driftX) < 3f)
    }
}
