package com.siwoo.party.games.tuho

import com.siwoo.party.common.aim.Gauge
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 투호 진행: 차례 순서, 게이지 멈춤 → 결과, 점수 합계·순위, 꽂힌 화살 */
class TuhoGameTest {

    companion object {
        /**
         * 결과마다 그 결과가 나오는 게이지 멈춤 시각 (각도 게이지 시작 뒤 ms, 힘 게이지 시작 뒤 ms).
         * 올라가는 반 주기 안에서 정수 ms 로 찾고, 같은 결과가 이어지는 구간의 가운데를 고른다.
         */
        val plans: Map<Outcome, LongArray> by lazy {
            val out = HashMap<Outcome, LongArray>()
            val angleMs = longArrayOf(500L, 700L, 1000L, 200L, 1150L)
            for (o in Outcome.values()) {
                for (am in angleMs) {
                    if (out.containsKey(o)) break
                    val av = Gauge.triangle(am, GameConfig.ANGLE_PERIOD_MS)
                    var runStart = -1L
                    var best = -1L
                    var bestLen = 0L
                    val half = GameConfig.POWER_PERIOD_MS / 2
                    for (pm in 0L..half + 1) {
                        val hit = pm <= half && TuhoField.judgeGauge(av, Gauge.triangle(pm, GameConfig.POWER_PERIOD_MS)).outcome == o
                        if (hit && runStart < 0) runStart = pm
                        if (!hit && runStart >= 0) {
                            val len = pm - runStart
                            if (len > bestLen) { bestLen = len; best = runStart + len / 2 }
                            runStart = -1
                        }
                    }
                    if (best >= 0 && bestLen >= 3) out[o] = longArrayOf(am, best)
                }
            }
            out
        }
    }

    /** 지금 사람이 결과 o 가 나오게 던짐 (시각 now 부터) → 다음 시각 */
    private fun throwFor(g: TuhoGame, o: Outcome, now: Long): Long {
        val p = plans[o] ?: error("$o 계획 없음")
        assertTrue(g.beginAim(now))
        assertTrue(g.angleLive)
        val deg = g.stopAngle(now + p[0])
        assertEquals(TuhoField.angleFor(Gauge.triangle(p[0], GameConfig.ANGLE_PERIOD_MS)), deg, 1e-5f)
        assertSame(Phase.POWER, g.phase)
        val s = g.stopPower(now + p[0] + p[1])!!
        assertSame(o, s.outcome)
        assertSame(Phase.FLIGHT, g.phase)
        assertSame(s, g.land())
        assertSame(Phase.RESULT, g.phase)
        return now + p[0] + p[1] + 3000L
    }

    @Test
    fun `모든 결과의 게이지 계획이 있다`() {
        for (o in Outcome.values()) assertNotNull("$o", plans[o])
        println("TUHO 계획: " + plans.entries.joinToString { it.key.name + "=" + it.value.toList() })
    }

    @Test
    fun `한 사람씩 한 개씩 돌아가며 던진다`() {
        assertEquals(0, TuhoGame.playerOf(0, 3))
        assertEquals(1, TuhoGame.playerOf(1, 3))
        assertEquals(0, TuhoGame.playerOf(3, 3))
        assertEquals(0, TuhoGame.arrowOf(2, 3))
        assertEquals(1, TuhoGame.arrowOf(3, 3))
        assertEquals(9, TuhoGame.arrowOf(29, 3))
        val g = TuhoGame(3)
        val order = ArrayList<Int>()
        val arrows = ArrayList<Int>()
        var now = 0L
        do {
            order.add(g.current)
            arrows.add(g.arrow)
            now = throwFor(g, Outcome.FLOOR, now)
        } while (g.next())
        assertEquals(30, order.size)
        assertEquals(List(30) { it % 3 }, order)
        assertEquals(List(30) { it / 3 }, arrows)
        assertSame(Phase.OVER, g.phase)
        for (p in 0 until 3) assertTrue(g.scores[p].all { it == 0 })
    }

    @Test
    fun `게이지 멈춘 값으로 각도와 힘이 정해진다`() {
        val g = TuhoGame(2)
        assertFalse("WAIT 에선 멈출 수 없음", g.stopAngle(10L).isFinite())
        assertNull(g.stopPower(10L))
        assertTrue(g.beginAim(1000L))
        assertFalse("두 번 시작 안 됨", g.beginAim(1000L))
        assertNull("각도 단계에선 힘 멈춤 안 됨", g.stopPower(1200L))
        val am = 900L
        val deg = g.stopAngle(1000L + am)
        val av = Gauge.triangle(am, GameConfig.ANGLE_PERIOD_MS)
        assertEquals(av, g.angleValue, 1e-6f)
        assertEquals(TuhoField.angleFor(av), deg, 1e-5f)
        assertFalse("힘 단계에선 각도 다시 안 멈춤", g.stopAngle(2000L).isFinite())
        assertTrue(g.powerLive)
        val pm = 1500L // 반 주기 넘어 내려오는 중
        val s = g.stopPower(1000L + am + pm)!!
        val pv = Gauge.triangle(pm, GameConfig.POWER_PERIOD_MS)
        assertEquals(pv, g.powerValue, 1e-6f)
        assertEquals(TuhoField.speedFor(pv), s.speed, 1e-5f)
        assertEquals(TuhoField.judge(deg, TuhoField.speedFor(pv)).outcome, s.outcome)
        assertFalse(g.powerLive)
        assertSame(s, g.shot)
        // 비행이 끝나기 전엔 다음 차례로 못 감
        assertTrue(g.next())
        assertEquals(0, g.throwIndex)
        g.land()
        assertNull("두 번 기록 안 됨", g.land())
        assertTrue(g.next())
        assertEquals(1, g.current)
    }

    @Test
    fun `합계와 순위`() {
        val g = TuhoGame(3)
        // 가람: 입구·귀 / 나래: 귀·귀 / 다온: 입구·바닥 … 를 10번
        val script = arrayOf(
            arrayOf(Outcome.MOUTH, Outcome.EAR_NEAR),
            arrayOf(Outcome.EAR_FAR, Outcome.EAR_NEAR),
            arrayOf(Outcome.MOUTH, Outcome.FLOOR),
        )
        var now = 0L
        do {
            val p = g.current
            now = throwFor(g, script[p][g.arrow % 2], now)
        } while (g.next())
        assertArrayEquals(intArrayOf(25, 30, 10), g.totals)
        assertArrayEquals(intArrayOf(2, 1, 3), g.ranks())
        for (p in 0 until 3) assertEquals(g.totals[p], g.scores[p].sum())
        assertEquals(10, g.hits(0))
        assertEquals(5, g.hits(2))
        // 동점은 같은 순위 (경쟁 순위)
        assertArrayEquals(intArrayOf(1, 1, 3), TuhoGame.ranksOf(intArrayOf(10, 10, 4)))
        assertArrayEquals(intArrayOf(2, 1, 2, 4), TuhoGame.ranksOf(intArrayOf(6, 9, 6, 0)))
        assertArrayEquals(intArrayOf(1, 1), TuhoGame.ranksOf(intArrayOf(0, 0)))
    }

    @Test
    fun `테두리·몸통·바닥은 0점`() {
        val g = TuhoGame(2)
        var now = throwFor(g, Outcome.RIM, 0L)
        g.next()
        now = throwFor(g, Outcome.BODY, now)
        g.next()
        throwFor(g, Outcome.MOUTH, now)
        assertArrayEquals(intArrayOf(2, 0), g.totals)
        assertEquals(0, g.scores[0][0])
        assertEquals(2, g.scores[0][1])
        assertEquals(-1, g.scores[0][2])
        assertSame(Outcome.RIM, g.outcomes[0][0])
        assertSame(Outcome.BODY, g.outcomes[1][0])
    }

    @Test
    fun `꽂힌 화살은 순번 동안 남고 다음 순번에 치운다`() {
        val g = TuhoGame(3)
        var now = throwFor(g, Outcome.MOUTH, 0L)
        assertEquals(1, g.stuckCount(0))
        g.next()
        assertFalse(g.newRound)
        now = throwFor(g, Outcome.MOUTH, now)
        assertEquals(2, g.stuckCount(0))
        assertEquals(1, g.stuck[1].slot)
        assertEquals(1, g.stuck[1].player)
        g.next()
        now = throwFor(g, Outcome.EAR_FAR, now)
        assertEquals(1, g.stuckCount(2))
        assertEquals(0, g.stuckCount(1))
        assertEquals(3, g.stuck.size)
        for (s in g.stuck) assertTrue(s.tiltDeg in -25f..25f)
        g.next()
        assertTrue("새 순번", g.newRound)
        assertEquals(0, g.stuck.size)
        throwFor(g, Outcome.FLOOR, now)
        assertEquals("바닥은 안 꽂힘", 0, g.stuck.size)
    }
}
