package com.siwoo.party.games.archery

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** 양궁 진행: 숨 참기 흔들림 곡선, 발사, 차례 순서, 합계·동점 가르기·순위, 시드 결정성 */
class ArcheryGameTest {

    /** 바람·흔들림·흩어짐을 무시하고 원하는 점수 자리에 꽂기: 바람만큼 반대로 조준 (noise = 0) */
    private fun hit(g: ArcheryGame, x: Float, y: Float = 0f): Shot {
        val s = g.shootAt(x - g.wind.driftX, y - g.wind.driftY)!!
        assertEquals(x, s.x, 1e-4f)
        assertEquals(y, s.y, 1e-4f)
        g.next()
        return s
    }

    /** 점수별 꽂을 거리 (X = 0, 10점(X 아님) = 0.8, 그 밖은 고리 가운데) */
    private fun spot(score: Int, x: Boolean = false): Float = when {
        x -> 0f
        score == 10 -> 0.8f
        score == 0 -> 12f
        else -> (GameConfig.RINGS - score) + 0.5f
    }

    // ── 숨 참기 흔들림 곡선 ──

    @Test
    fun `숨 참기 - 누르면 흔들림이 줄어 최소가 되고 2초 뒤에는 오히려 커지며 떼면 보통으로`() {
        val g = ArcheryGame(2, 1L)
        val t0 = 10_000L
        val base = g.swayAmplitudeAt(t0)
        assertEquals("안 누르면 보통", GameConfig.SWAY_AMP_X, base, 1e-5f)
        assertEquals(1f, g.swayScaleAt(t0), 0f)
        assertTrue(g.press(t0))
        assertTrue(g.holding)
        // 누르는 동안 점점 줄어듦 (calm 까지)
        var prev = g.swayScaleAt(t0)
        var t = t0 + 50
        while (t <= t0 + GameConfig.BREATH_CALM_MS) {
            val m = g.swayScaleAt(t)
            assertTrue("줄어듦 @${t - t0}", m < prev)
            prev = m
            t += 50
        }
        assertEquals("약 1초에 최소", GameConfig.BREATH_MIN, g.swayScaleAt(t0 + GameConfig.BREATH_CALM_MS), 1e-4f)
        assertEquals("2초까지 최소 유지", GameConfig.BREATH_MIN, g.swayScaleAt(t0 + GameConfig.BREATH_LIMIT_MS), 1e-4f)
        assertEquals(GameConfig.SWAY_AMP_X * GameConfig.BREATH_MIN, g.swayAmplitudeAt(t0 + 1500), 1e-4f)
        // 2초 지나면 커짐
        prev = g.swayScaleAt(t0 + GameConfig.BREATH_LIMIT_MS)
        t = t0 + GameConfig.BREATH_LIMIT_MS + 100
        while (t <= t0 + GameConfig.BREATH_LIMIT_MS + GameConfig.BREATH_GROW_MS) {
            val m = g.swayScaleAt(t)
            assertTrue("커짐 @${t - t0}", m > prev)
            prev = m
            t += 100
        }
        val late = g.swayScaleAt(t0 + GameConfig.BREATH_LIMIT_MS + GameConfig.BREATH_GROW_MS + 2000)
        assertEquals("숨이 차면 보통보다 크게", GameConfig.BREATH_MAX, late, 1e-4f)
        assertTrue(late > 1f)
        // 숨만 놓으면 보통으로
        g.cancelHold(t0 + 5000)
        assertFalse(g.holding)
        assertEquals(1f, g.swayScaleAt(t0 + 5001), 0f)
        assertSame("쏘지 않음", Phase.AIM, g.phase)
    }

    @Test
    fun `떼는 순간의 조준점과 배율로 쏘고 그 뒤 흔들림은 보통`() {
        val g = ArcheryGame(2, 3L)
        val t0 = 1_000L
        assertNull("누르지 않고 떼면 무시", g.release(t0))
        g.press(t0)
        val t1 = t0 + 1200
        val aim = FloatArray(2)
        g.aimAt(t1, aim)
        val wind = g.wind
        val s = g.release(t1)!!
        assertEquals(aim[0], s.aimX, 1e-5f)
        assertEquals(aim[1], s.aimY, 1e-5f)
        assertEquals(1200L, s.heldMs)
        assertEquals(GameConfig.BREATH_MIN, s.swayScale, 1e-5f)
        // 꽂힌 자리 = 조준 + 바람 + 흩어짐(SCATTER 안)
        val sx = s.x - s.aimX - wind.driftX
        val sy = s.y - s.aimY - wind.driftY
        assertTrue("흩어짐 ≤ SCATTER", sqrt(sx * sx + sy * sy) <= GameConfig.SCATTER + 1e-4f)
        assertEquals(Target.scoreAt(s.x, s.y), s.score)
        assertSame(Phase.RESULT, g.phase)
        assertFalse(g.holding)
        assertEquals(1f, g.swayScaleAt(t1 + 10), 0f)
        assertNull("결과 중엔 못 쏨", g.release(t1 + 20))
        assertFalse(g.press(t1 + 20))
    }

    @Test
    fun `흔들림이 줄면 조준점이 가운데 근처에 머문다`() {
        val g = ArcheryGame(2, 5L)
        val out = FloatArray(2)
        var calmMax = 0f
        var freeMax = 0f
        for (t in 0L until 2600L step 20) {
            g.aimAt(t, out)
            freeMax = maxOf(freeMax, abs(out[0]))
        }
        g.press(100_000L)
        for (t in 101_000L until 102_000L step 20) {
            g.aimAt(t, out)
            calmMax = maxOf(calmMax, abs(out[0]))
        }
        assertTrue("보통 흔들림 $freeMax", freeMax > GameConfig.SWAY_AMP_X * 0.8f)
        assertTrue("숨 참은 흔들림 $calmMax", calmMax < freeMax * 0.35f)
    }

    @Test
    fun `끌어서 조준을 옮기고 제한 원 밖으로는 못 감, 새 화살은 가운데부터`() {
        val g = ArcheryGame(2, 1L, noise = 0f)
        g.press(0L)
        g.moveAim(2f, -1f)
        g.moveAim(0.5f, 0f)
        assertEquals(2.5f, g.aimBaseX, 1e-5f)
        assertEquals(-1f, g.aimBaseY, 1e-5f)
        val out = FloatArray(2)
        g.aimAt(500L, out)
        assertEquals("noise 0 → 흔들림 없음", 2.5f, out[0], 1e-5f)
        g.moveAim(100f, 0f)
        val d = sqrt(g.aimBaseX * g.aimBaseX + g.aimBaseY * g.aimBaseY)
        assertEquals(GameConfig.AIM_LIMIT, d, 1e-3f)
        val s = g.release(800L)!!
        assertEquals(g.aimBaseX + s.wind.driftX, s.x, 1e-4f)
        g.next()
        assertEquals(0f, g.aimBaseX, 0f)
        assertEquals(0f, g.aimBaseY, 0f)
    }

    // ── 차례 ──

    @Test
    fun `한 발씩 돌아가며 6발씩, 처음 쏘는 사람부터`() {
        for (n in 2..4) {
            val first = n - 1
            val g = ArcheryGame(n, 7L, first, noise = 0f)
            assertEquals(n * GameConfig.ARROWS, g.totalShots)
            val seen = ArrayList<Int>()
            var k = 0
            while (g.phase != Phase.OVER) {
                assertEquals((first + k) % n, g.current)
                assertEquals(k / n, g.arrow)
                assertEquals(g.current, g.shooterAt(k))
                seen.add(g.current)
                val s = hit(g, 0f)
                assertEquals(k / n, s.arrow)
                k++
            }
            assertEquals(n * GameConfig.ARROWS, k)
            for (p in 0 until n) {
                assertEquals(GameConfig.ARROWS, seen.count { it == p })
                assertEquals(GameConfig.ARROWS, g.shotsBy(p))
            }
            // 처음 n 발은 사람마다 1발씩
            assertEquals((0 until n).map { (first + it) % n }, seen.take(n))
            assertNull(g.shootAt(0f, 0f))
            assertSame(Phase.OVER, g.next())
        }
    }

    // ── 합계·동점·순위 ──

    @Test
    fun `합계와 X 개수, 10점 개수`() {
        val g = ArcheryGame(2, 9L, 0, noise = 0f)
        val p0 = listOf(10 to true, 10 to false, 9 to false, 7 to false, 0 to false, 10 to true)
        val p1 = listOf(1 to false, 2 to false, 3 to false, 4 to false, 5 to false, 6 to false)
        for (k in 0 until GameConfig.ARROWS) {
            val a = hit(g, spot(p0[k].first, p0[k].second))
            assertEquals(p0[k].first, a.score)
            assertEquals(p0[k].second, a.isX)
            val b = hit(g, 0f, spot(p1[k].first))
            assertEquals(p1[k].first, b.score)
        }
        assertEquals(46, g.totals[0])
        assertEquals(2, g.xCount[0])
        assertEquals(3, g.tenCount[0])
        assertEquals(21, g.totals[1])
        assertEquals(0, g.xCount[1])
        assertTrue(g.shots[0][4]!!.miss)
        assertEquals("M", g.shots[0][4]!!.label)
        assertEquals("X", g.shots[0][0]!!.label)
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
        assertEquals(listOf(0), g.winners())
    }

    @Test
    fun `동점이면 X 개수, 그다음 10점 개수, 그래도 같으면 같은 순위`() {
        // 0: 10점 6발(X 0) = 60, 1: X 6발 = 60, 2: X 1발 + 10 5발 = 60, 3: 0번과 똑같이 = 60
        val g = ArcheryGame(4, 2L, 0, noise = 0f)
        for (k in 0 until GameConfig.ARROWS) {
            hit(g, spot(10))
            hit(g, spot(10, true))
            hit(g, if (k == 0) spot(10, true) else spot(10))
            hit(g, 0f, spot(10))
        }
        assertArrayEquals(intArrayOf(60, 60, 60, 60), g.totals)
        assertArrayEquals(intArrayOf(0, 6, 1, 0), g.xCount)
        assertArrayEquals(intArrayOf(3, 1, 2, 3), g.ranks())
        assertEquals(listOf(1), g.winners())

        // 합계·X 같고 10점 개수만 다름: 0번 X 1 + 10 1 + 9 4 = 56, 1번 X 1 + 10 0 + 9 5 + … 같은 합계 맞추기
        val h = ArcheryGame(2, 4L, 0, noise = 0f)
        val a = listOf(spot(10, true), spot(10), spot(8), spot(8), spot(8), spot(8)) // 10+10+32 = 52, X1, 10점 2
        val b = listOf(spot(10, true), spot(9), spot(9), spot(9), spot(8), spot(7)) // 10+27+8+7 = 52, X1, 10점 1
        for (k in 0 until GameConfig.ARROWS) { hit(h, a[k]); hit(h, b[k]) }
        assertArrayEquals(intArrayOf(52, 52), h.totals)
        assertArrayEquals(intArrayOf(1, 1), h.xCount)
        assertArrayEquals(intArrayOf(2, 1), h.tenCount)
        assertArrayEquals(intArrayOf(1, 2), h.ranks())

        // 합계가 높으면 X 가 적어도 이김
        assertTrue(ArcheryGame.rankKey(51, 0, 0) > ArcheryGame.rankKey(50, 6, 6))
        assertTrue(ArcheryGame.rankKey(50, 1, 0) > ArcheryGame.rankKey(50, 0, 6))
    }

    @Test
    fun `모두 빗나가면 모두 공동 1위`() {
        val g = ArcheryGame(3, 6L, 1, noise = 0f)
        while (g.phase != Phase.OVER) hit(g, 0f, 15f)
        assertArrayEquals(intArrayOf(0, 0, 0), g.totals)
        assertArrayEquals(intArrayOf(1, 1, 1), g.ranks())
        assertEquals(listOf(0, 1, 2), g.winners())
    }

    // ── 결정성 ──

    @Test
    fun `같은 시드와 같은 손놀림이면 같은 바람 같은 결과`() {
        fun play(seed: Long): List<Shot> {
            val g = ArcheryGame(3, seed, 0)
            val list = ArrayList<Shot>()
            var t = 5_000L
            while (g.phase != Phase.OVER) {
                g.press(t)
                g.moveAim(-g.wind.driftX, -g.wind.driftY)
                t += 700 + (list.size % 4) * 300
                list.add(g.release(t)!!)
                g.next()
                t += 3_000
            }
            return list
        }
        val a = play(42L)
        val b = play(42L)
        assertEquals(a, b)
        val c = play(43L)
        assertFalse("다른 시드는 다른 바람", a.map { it.wind.dirDeg to it.wind.speed10 } == c.map { it.wind.dirDeg to it.wind.speed10 })
        // 바람은 손놀림과 상관없이 같은 순서
        val g1 = ArcheryGame(2, 42L, noise = 0f)
        val g2 = ArcheryGame(2, 42L, noise = 0f)
        repeat(6) {
            assertEquals(g1.wind.dirDeg, g2.wind.dirDeg)
            assertEquals(g1.wind.speed10, g2.wind.speed10)
            g1.shootAt(0f, 0f); g1.next()
            g2.press(0L); g2.moveAim(3f, 1f); g2.release(1500L); g2.next()
        }
    }

    @Test
    fun `발마다 바람이 바뀐다`() {
        val g = ArcheryGame(2, 8L, noise = 0f)
        val winds = HashSet<Pair<Int, Int>>()
        while (g.phase != Phase.OVER) {
            winds.add(g.wind.dirDeg to g.wind.speed10)
            assertTrue(g.wind.speed10 >= GameConfig.WIND_MIN10)
            assertNotNull(g.shootAt(0f, 0f))
            g.next()
        }
        assertTrue("12발 중 바람이 여러 가지 (${winds.size})", winds.size >= 8)
    }
}
