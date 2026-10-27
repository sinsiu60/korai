package com.siwoo.party.games.spintop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 규칙 계산: 연타 → 회전력, 게이지 → 안정성, 회전력 감소, 판 순위·점수, 최종 순위 */
class SpinRulesTest {

    @Test
    fun `연타 수가 많을수록 회전력이 오르지만 점점 덜 오른다`() {
        var prev = SpinRules.spinPower(0)
        var prevGain = Float.MAX_VALUE
        for (n in 5..GameConfig.MASH_MAX_TAPS step 5) {
            val p = SpinRules.spinPower(n)
            val gain = p - prev
            assertTrue("오름 ($n)", gain > 0f)
            assertTrue("체감 ($n: $gain < $prevGain)", gain < prevGain)
            prev = p
            prevGain = gain
        }
    }

    @Test
    fun `회전력은 최저값과 1 사이로 자른다`() {
        assertEquals(GameConfig.MIN_POWER, SpinRules.spinPower(0), 1e-6f)
        assertEquals(GameConfig.MIN_POWER, SpinRules.spinPower(-5), 1e-6f)
        // 최대 연타 수 위로는 더 오르지 않음
        assertEquals(SpinRules.spinPower(GameConfig.MASH_MAX_TAPS), SpinRules.spinPower(500), 1e-6f)
        assertTrue(SpinRules.spinPower(500) <= 1f)
        // 대표 값: 20번 ≈ 0.64, 40번 ≈ 0.84 (최저값 0.2 를 더한 뒤)
        assertEquals(0.2f + 0.8f * (1f - Math.exp(-20.0 / 25.0).toFloat()), SpinRules.spinPower(20), 1e-4f)
        assertTrue(SpinRules.spinPower(40) in 0.8f..0.9f)
    }

    @Test
    fun `게이지 가운데에서 멈추면 안정성 최고, 끝으로 갈수록 낮다`() {
        assertEquals(1f, SpinRules.stability(0.5f), 1e-6f)
        assertEquals(1f, SpinRules.stability(0.5f + GameConfig.GAUGE_HALF), 1e-6f)
        assertEquals(1f, SpinRules.stability(0.5f - GameConfig.GAUGE_HALF), 1e-6f)
        assertEquals(GameConfig.MIN_STABILITY, SpinRules.stability(0f), 1e-6f)
        assertEquals(GameConfig.MIN_STABILITY, SpinRules.stability(1f), 1e-6f)
        // 좌우 대칭, 가운데로 갈수록 높아짐
        assertEquals(SpinRules.stability(0.3f), SpinRules.stability(0.7f), 1e-5f)
        assertTrue(SpinRules.stability(0.2f) < SpinRules.stability(0.3f))
        assertTrue(SpinRules.stability(0.3f) < SpinRules.stability(0.4f))
        // 범위 밖 값은 잘라서
        assertEquals(GameConfig.MIN_STABILITY, SpinRules.stability(-1f), 1e-6f)
    }

    @Test
    fun `기본 감소는 시간에 비례`() {
        assertEquals(GameConfig.BASE_DECAY, SpinRules.baseDecay(1f), 1e-6f)
        assertEquals(GameConfig.BASE_DECAY * 2.5f, SpinRules.baseDecay(2.5f), 1e-6f)
    }

    @Test
    fun `충돌 감소는 충돌 세기에 비례하고 안정성이 높으면 덜 깎인다`() {
        val a = SpinRules.collisionLoss(2f, 0.5f)
        val b = SpinRules.collisionLoss(4f, 0.5f)
        assertEquals(a * 2f, b, 1e-6f)
        assertEquals(0f, SpinRules.collisionLoss(0f, 0.5f), 0f)
        val low = SpinRules.collisionLoss(5f, 0.1f)
        val high = SpinRules.collisionLoss(5f, 1f)
        assertTrue("안정성 높으면 덜 깎임 ($high < $low)", high < low)
        // 공식: 세기 × k × (1 − 안정성 × 막음)
        assertEquals(5f * GameConfig.COLLIDE_K * (1f - GameConfig.STAB_GUARD), high, 1e-6f)
        // 테두리는 덜 깎임
        assertTrue(SpinRules.collisionLoss(5f, 0.5f, rim = true) < SpinRules.collisionLoss(5f, 0.5f))
        // 센 상대와 부딪히면 더 깎인다 (같으면 배수 1)
        val vsStrong = SpinRules.collisionLoss(5f, 0.5f, own = 0.3f, other = 0.9f)
        val vsWeak = SpinRules.collisionLoss(5f, 0.5f, own = 0.9f, other = 0.3f)
        assertTrue(vsStrong > SpinRules.collisionLoss(5f, 0.5f))
        assertTrue(vsWeak < SpinRules.collisionLoss(5f, 0.5f))
        assertEquals(SpinRules.collisionLoss(5f, 0.5f), SpinRules.collisionLoss(5f, 0.5f, own = 0.4f, other = 0.4f), 1e-6f)
    }

    @Test
    fun `판 순위는 쓰러진 순서 — 늦게 쓰러질수록 위, 같은 단계는 같은 순위`() {
        // 0: 끝까지 돎, 1: 100 단계, 2: 50 단계, 3: 100 단계 (1 과 동시)
        val r = SpinRules.roundRanks(longArrayOf(-1, 100, 50, 100), floatArrayOf(0.3f, 0f, 0f, 0f))
        assertArrayEquals(intArrayOf(1, 2, 4, 2), r)
        // 마지막 둘이 동시에 쓰러지면 둘 다 1등
        assertArrayEquals(intArrayOf(1, 1, 3), SpinRules.roundRanks(longArrayOf(200, 200, 10), FloatArray(3)))
        // 2인
        assertArrayEquals(intArrayOf(2, 1), SpinRules.roundRanks(longArrayOf(30, -1), floatArrayOf(0f, 0.1f)))
    }

    @Test
    fun `제한 시간 — 남은 팽이는 회전력 순, 같으면 같은 순위`() {
        val r = SpinRules.roundRanks(longArrayOf(-1, -1, 300, -1), floatArrayOf(0.4f, 0.6f, 0f, 0.4f))
        assertArrayEquals(intArrayOf(2, 1, 4, 2), r)
    }

    @Test
    fun `판 점수 — 1등 인원 수, 한 칸 내려갈 때마다 1점 적게, 동점은 같은 점수`() {
        assertArrayEquals(intArrayOf(4, 3, 1, 3), SpinRules.points(intArrayOf(1, 2, 4, 2)))
        assertArrayEquals(intArrayOf(2, 1), SpinRules.points(intArrayOf(1, 2)))
        assertArrayEquals(intArrayOf(3, 3, 1), SpinRules.points(intArrayOf(1, 1, 3)))
        assertEquals(4, GameConfig.roundPoints(1, 4))
        assertEquals(1, GameConfig.roundPoints(4, 4))
    }

    @Test
    fun `최종 순위 — 합계 점수 경쟁 순위`() {
        assertArrayEquals(intArrayOf(2, 1, 2, 4), SpinRules.finalRanks(intArrayOf(7, 9, 7, 3)))
        assertArrayEquals(intArrayOf(1, 1), SpinRules.finalRanks(intArrayOf(5, 5)))
    }
}
