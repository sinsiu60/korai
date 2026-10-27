package com.siwoo.party.games.spintop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/** 경기장 물리(회전력 감소·쓰러짐·제한 시간)와 판 진행(준비 → 대결 → 판 결과 → 3판 합계) */
class SpinTopGameTest {

    /** 소리 없이 일어난 일 세기 */
    private class Log : Arena.Listener {
        var hits = 0
        var rims = 0
        val falls = ArrayList<Int>()
        var ends = 0
        var timeUp = false
        override fun onHit(a: Top, b: Top, x: Float, y: Float, speed: Float) { hits++ }
        override fun onRim(top: Top, x: Float, y: Float, speed: Float) { rims++ }
        override fun onFall(top: Top) { falls.add(top.id) }
        override fun onEnd(timeUp: Boolean) { ends++; this.timeUp = timeUp }
    }

    private fun arena(n: Int, seed: Int = 1, capMs: Long = GameConfig.BATTLE_CAP_MS): Arena {
        val a = Arena(Random(seed), capMs)
        a.setTops(List(n) { Top(it, it) })
        return a
    }

    @Test
    fun `부딪히기 전에는 회전력이 기본 감소만큼 줄어든다`() {
        val a = arena(2)
        val log = Log()
        a.listener = log
        a.launch(floatArrayOf(0.8f, 0.6f), floatArrayOf(0.5f, 0.5f))
        repeat(30) { a.step() } // 0.5초 — 출발 자리가 멀어 아직 안 부딪힘
        assertEquals(0, log.hits)
        assertEquals(0, log.rims)
        assertEquals(0.8f - GameConfig.BASE_DECAY * 0.5f, a.tops[0].power, 1e-4f)
        assertEquals(0.6f - GameConfig.BASE_DECAY * 0.5f, a.tops[1].power, 1e-4f)
        // 무늬 회전 속도는 회전력에 비례
        assertEquals(a.tops[0].power * GameConfig.VISUAL_SPIN, a.tops[0].spin, 1e-3f)
    }

    @Test
    fun `정면 충돌 — 안정성이 높은 팽이가 덜 깎인다`() {
        val a = arena(2)
        val log = Log()
        a.listener = log
        a.launch(floatArrayOf(0.8f, 0.8f), floatArrayOf(1f, 0.15f))
        // 정면으로 마주 보게 다시 놓기 (무작위 각도 없이)
        val t0 = a.tops[0]
        val t1 = a.tops[1]
        t0.x = -3f; t0.y = 0f; t0.setVelocity(6f, 0f)
        t1.x = 3f; t1.y = 0f; t1.setVelocity(-6f, 0f)
        var guard = 0
        while (log.hits == 0 && guard < 120) { a.step(); guard++ }
        assertEquals(1, log.hits)
        val lost0 = 0.8f - t0.power
        val lost1 = 0.8f - t1.power
        assertTrue("안정성 높은 쪽 덜 깎임 ($lost0 < $lost1)", lost0 < lost1)
        // 충돌로 깎인 양이 기본 감소보다 훨씬 큼
        assertTrue(lost1 > GameConfig.BASE_DECAY * 0.5f * 4f)
    }

    @Test
    fun `세게 부딪힐수록 더 깎인다`() {
        fun lossAt(speed: Float): Float {
            val a = arena(2)
            val log = Log()
            a.listener = log
            a.launch(floatArrayOf(0.8f, 0.8f), floatArrayOf(0.5f, 0.5f))
            val t0 = a.tops[0]
            val t1 = a.tops[1]
            t0.x = -1.2f; t0.y = 0f; t0.setVelocity(speed, 0f)
            t1.x = 1.2f; t1.y = 0f; t1.setVelocity(-speed, 0f)
            var guard = 0
            while (log.hits == 0 && guard < 120) { a.step(); guard++ }
            return 0.8f - t0.power - GameConfig.BASE_DECAY * guard / 60f
        }
        val slow = lossAt(2f)
        val fast = lossAt(8f)
        assertTrue("빠른 충돌이 더 깎임 ($fast > $slow)", fast > slow * 2f)
    }

    @Test
    fun `회전력이 0 이 되면 쓰러지고 물리에서 빠진다`() {
        val a = arena(3)
        val log = Log()
        a.listener = log
        a.launch(floatArrayOf(0.8f, 0.8f, 0.8f), floatArrayOf(0.5f, 0.5f, 0.5f))
        val t = a.tops[1]
        t.power = GameConfig.BASE_DECAY / 120f // 다음 단계에 0 아래
        a.step()
        assertTrue(t.fallen)
        assertFalse(t.spinning)
        assertFalse("물리에서 빠짐", t.active)
        assertEquals(0f, t.power, 0f)
        assertEquals(0f, t.spin, 0f)
        assertFalse(t.moving)
        assertEquals(1L, t.fallStep)
        assertEquals(listOf(1), log.falls)
        assertEquals(2, a.spinningCount)
        assertTrue("둘이 남았으니 계속", a.running)
        // 쓰러진 팽이는 더 움직이지 않음
        val x = t.x
        repeat(30) { a.step() }
        assertEquals(x, t.x, 0f)
    }

    @Test
    fun `한 팽이만 남으면 대결 끝 — 동시에 쓰러진 팽이는 같은 순위`() {
        val a = arena(4)
        val log = Log()
        a.listener = log
        a.launch(floatArrayOf(0.8f, 0.8f, 0.8f, 0.8f), floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f))
        a.tops[2].power = 0.0001f
        a.step() // 2 쓰러짐 (1단계)
        a.tops[0].power = 0.0001f
        a.tops[3].power = 0.0001f
        a.step() // 0, 3 동시에 (2단계) → 1 만 남아 끝
        assertFalse(a.running)
        assertTrue(a.ended)
        assertFalse(a.timeUp)
        assertEquals(1, log.ends)
        assertArrayEquals(intArrayOf(2, 1, 4, 2), a.ranks())
        // 끝난 뒤엔 진행하지 않음
        val s = a.steps
        assertFalse(a.advance(1000L))
        a.step()
        assertEquals(s, a.steps)
    }

    @Test
    fun `제한 시간이 지나면 남은 팽이는 회전력 순으로`() {
        val a = arena(3, capMs = 1000L)
        val log = Log()
        a.listener = log
        a.launch(floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f))
        var guard = 0
        while (a.running && guard < 1000) { a.step(); guard++ }
        assertTrue(a.timeUp)
        assertEquals(60, guard)
        assertEquals(1000L, a.elapsedMs)
        assertEquals(3, a.spinningCount)
        val p = FloatArray(3) { a.tops[it].power }
        val r = a.ranks()
        for (i in 0 until 3) for (j in 0 until 3) if (p[i] > p[j]) assertTrue(r[i] < r[j])
        assertArrayEquals(SpinRules.roundRanks(longArrayOf(-1, -1, -1), p), r)
        assertTrue("남은 팽이는 멈춤", a.tops.none { it.moving })
    }

    @Test
    fun `같은 입력이면 프레임을 어떻게 나눠도 같은 결과`() {
        fun play(frame: Long): Pair<IntArray, Long> {
            val a = arena(3, seed = 7)
            a.launch(floatArrayOf(0.7f, 0.9f, 0.5f), floatArrayOf(0.4f, 0.8f, 0.6f))
            while (a.advance(frame)) { /* 진행 */ }
            return a.ranks() to a.steps
        }
        val (r1, s1) = play(16L)
        val (r2, s2) = play(33L)
        assertArrayEquals(r1, r2)
        assertEquals(s1, s2)
        assertTrue("대결 10~30초 (${s1 / 60f})", s1 in 10L * 60..30L * 60)
    }

    @Test
    fun `팽이는 경기장 안에 머문다`() {
        val a = arena(4, seed = 3)
        a.launch(floatArrayOf(1f, 1f, 1f, 1f), floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f))
        var maxD = 0f
        while (a.running) {
            a.step()
            for (t in a.tops) maxD = maxOf(maxD, sqrt(t.x * t.x + t.y * t.y))
        }
        assertTrue("최대 거리 $maxD", maxD <= GameConfig.ARENA_R - GameConfig.TOP_R + 0.2f)
    }

    // ── 판 진행 ──

    /** 지금 준비하는 사람: taps 번 연타 → 줄 감기 끝 → 게이지를 at(0..1 왕복의 위상)에서 멈춤 */
    private fun prep(g: SpinTopGame, taps: Int, phaseFrac: Float, now: Long) {
        assertSame(Phase.READY, g.phase)
        assertSame(MashTap.STARTED, g.mashTap())
        repeat(taps - 1) { assertSame(MashTap.COUNTED, g.mashTap()) }
        assertSame(Phase.MASH, g.phase)
        g.endMash()
        assertSame(Phase.GAUGE, g.phase)
        assertFalse("게이지 시작 전엔 못 멈춤", g.gaugeLive)
        assertEquals(-1f, g.stopGauge(now), 0f)
        g.startGauge(now)
        assertTrue(g.gaugeLive)
        g.stopGauge(now + (GameConfig.GAUGE_PERIOD_MS * phaseFrac).toLong())
        assertSame(Phase.SHOW, g.phase)
    }

    @Test
    fun `준비 — 연타 수로 회전력, 게이지로 안정성`() {
        val g = SpinTopGame(2, Random(1))
        assertEquals(0, g.preparer)
        assertSame(MashTap.IGNORED, SpinTopGame(2).also { it.mashTap(); it.endMash() }.mashTap())
        prep(g, 30, 0.25f, 1000L) // 위상 1/4 → 값 0.5 (가운데)
        assertEquals(SpinRules.spinPower(30), g.powers[0], 1e-6f)
        assertEquals(1f, g.stabilities[0], 1e-6f)
        assertEquals(0.5f, g.gaugeValues[0], 1e-3f)
        assertTrue(g.prepped[0])
        assertTrue(g.nextPrep())
        assertEquals(1, g.preparer)
        prep(g, 10, 0.02f, 5000L) // 거의 0 → 안정성 낮음
        assertTrue(g.stabilities[1] < 0.3f)
        assertFalse(g.nextPrep())
        assertSame(Phase.LAUNCH, g.phase)
    }

    @Test
    fun `연타는 최대 수까지만 센다`() {
        val g = SpinTopGame(2)
        g.mashTap()
        repeat(200) { g.mashTap() }
        assertEquals(GameConfig.MASH_MAX_TAPS, g.taps[0])
        assertSame(MashTap.IGNORED, g.mashTap())
        assertEquals(SpinRules.spinPower(GameConfig.MASH_MAX_TAPS), g.endMash(), 1e-6f)
    }

    @Test
    fun `3판 진행 — 판마다 준비 순서가 돌고, 판 점수 합계로 최종 순위`() {
        for (n in 2..4) {
            val g = SpinTopGame(n, Random(n))
            val sums = IntArray(n)
            for (r in 0 until GameConfig.ROUNDS) {
                assertEquals(r, g.round)
                assertEquals("판마다 첫 준비자", r % n, g.preparer)
                for (k in 0 until n) {
                    val who = g.preparer
                    prep(g, 15 + who * 12 + r * 5, 0.1f + 0.05f * k, 100L * k)
                    g.nextPrep()
                }
                assertSame(Phase.LAUNCH, g.phase)
                g.startBattle()
                assertSame(Phase.BATTLE, g.phase)
                assertTrue(g.arena.running)
                var guard = 0
                while (g.advance(16L) && guard < 10_000) guard++
                assertSame(Phase.ROUND_END, g.phase)
                assertFalse(g.arena.running)
                val res = g.results[r]
                assertNotNull(res)
                res!!
                assertArrayEquals(g.arena.ranks(), res.ranks)
                assertArrayEquals(SpinRules.points(res.ranks), res.points)
                for (i in 0 until n) sums[i] += res.points[i]
                assertArrayEquals(sums, g.totals)
                // 끝난 뒤엔 더 진행하지 않음
                assertFalse(g.advance(16L))
                val more = g.nextRound()
                assertEquals(r < GameConfig.ROUNDS - 1, more)
                if (more) {
                    assertSame(Phase.READY, g.phase)
                    assertTrue(g.tops.none { it.launched || it.fallen })
                    assertTrue(g.prepped.none { it })
                }
            }
            assertSame(Phase.OVER, g.phase)
            assertArrayEquals(SpinRules.finalRanks(sums), g.ranks())
            // 총점 = 판마다 점수 합 (동점 없으면 n(n+1)/2 × 3)
            assertTrue(g.totals.sum() <= GameConfig.ROUNDS * n * (n + 1) / 2 + GameConfig.ROUNDS * n)
        }
    }

    @Test
    fun `판 승자 — 1등이 한 명일 때만`() {
        val g = SpinTopGame(2, Random(4))
        repeat(2) { prep(g, 40, 0.25f, 0L); g.nextPrep() }
        g.startBattle()
        g.tops[1].power = 0.0001f
        g.advance(17L)
        assertSame(Phase.ROUND_END, g.phase)
        assertEquals(0, g.roundWinner())
        assertArrayEquals(intArrayOf(2, 1), g.results[0]!!.points)
    }
}
