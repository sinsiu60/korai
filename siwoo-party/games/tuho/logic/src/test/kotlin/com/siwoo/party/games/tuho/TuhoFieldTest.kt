package com.siwoo.party.games.tuho

import com.siwoo.party.common.aim.Gauge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 투호 판정 (순수 기하): 입구 2점·귀 3점·테두리·몸통·바닥, 구멍 경계, 포물선 식, 게이지 → 각도·힘, 해 존재 */
class TuhoFieldTest {

    private val mouth = TuhoField.mouth
    private val near = TuhoField.earNear
    private val far = TuhoField.earFar

    /** 각도 deg 로 (x, y) 를 지나도록 던짐 */
    private fun through(deg: Float, x: Double, y: Double): Shot {
        val v = TuhoField.speedThrough(deg, x, y)
        assertFalse("($x, $y) 를 지나는 속도가 있어야 함", v.isNaN())
        return TuhoField.judge(deg, v.toFloat())
    }

    /** 구멍 위 끝 높이에서 x 를 지나도록 던짐 (Float 속도 반올림을 피하려고 Double 로 직접 판정 확인) */
    private fun at(o: Opening, deg: Float, x: Double): Shot = through(deg, x, o.top.toDouble())

    // ── 들어감 ──

    @Test
    fun `입구 가운데는 2점`() {
        for (deg in floatArrayOf(25f, 40f, 55f)) {
            val s = at(mouth, deg, mouth.cx.toDouble())
            assertSame("각도 $deg", Outcome.MOUTH, s.outcome)
            assertEquals(2, s.points)
            assertEquals(mouth.cx.toDouble(), s.x, 1e-3)
            assertEquals(mouth.top.toDouble(), s.y, 1e-9)
            assertTrue("내려가며 들어감", s.hitVy < 0.0)
            assertEquals(0.0, s.bounceT, 0.0)
        }
    }

    @Test
    fun `가까운 귀 가운데는 3점`() {
        for (deg in floatArrayOf(25f, 45f, 60f)) {
            val s = at(near, deg, near.cx.toDouble())
            assertSame("각도 $deg", Outcome.EAR_NEAR, s.outcome)
            assertEquals(3, s.points)
        }
    }

    @Test
    fun `먼 귀 가운데는 3점 (가파르게 넘겨야 함)`() {
        for (deg in floatArrayOf(55f, 60f, 65f)) {
            val s = at(far, deg, far.cx.toDouble())
            assertSame("각도 $deg", Outcome.EAR_FAR, s.outcome)
            assertEquals(3, s.points)
        }
        // 너무 낮게(평평하게) 던지면 입구·테두리·목에 먼저 걸린다 (먼 귀까지 못 감)
        val flat = at(far, 25f, far.cx.toDouble())
        assertNotEquals(Outcome.EAR_FAR, flat.outcome)
        assertTrue(flat.t < TuhoField.descendT(25f, flat.speed, far.top.toDouble()))
    }

    // ── 구멍 경계 ──

    private fun edges(o: Opening, deg: Float) {
        val eps = 1e-5
        val c = o.cx.toDouble()
        val h = o.half.toDouble()
        // 안쪽 경계 바로 안 → 들어감
        assertSame("왼쪽 경계 안", o.outcome, at(o, deg, c - h + eps).outcome)
        assertSame("오른쪽 경계 안", o.outcome, at(o, deg, c + h - eps).outcome)
        // 바로 밖 → 테두리 (튕김, 0점)
        val l = at(o, deg, c - h - eps)
        val r = at(o, deg, c + h + eps)
        assertSame("왼쪽 경계 밖", Outcome.RIM, l.outcome)
        assertSame("오른쪽 경계 밖", Outcome.RIM, r.outcome)
        assertEquals(0, l.points)
        // 테두리 바깥 끝 바로 안 → 테두리, 더 밖 → 이 구멍과 무관 (다른 구멍·몸통·바닥)
        val rim = GameConfig.RIM.toDouble()
        assertSame(Outcome.RIM, at(o, deg, c - h - rim + eps).outcome)
        assertSame(Outcome.RIM, at(o, deg, c + h + rim - eps).outcome)
        val out = at(o, deg, c + h + rim + 1e-3)
        assertNotEquals(o.outcome, out.outcome)
        assertTrue("구멍 높이에서 맞은 게 아님", out.outcome != Outcome.RIM || out.y != o.top.toDouble() || kotlin.math.abs(out.x - c) > h + rim)
    }

    @Test fun `입구 경계`() = edges(mouth, 45f)

    @Test fun `가까운 귀 경계`() = edges(near, 45f)

    @Test fun `먼 귀 경계`() = edges(far, 62f)

    @Test
    fun `경계 값은 들어감 (판정 식)`() {
        // 구멍 반폭과 같은 거리는 안 (경계 포함)
        for (o in TuhoField.openings) {
            val d = o.half.toDouble()
            assertTrue(kotlin.math.abs((o.cx + o.half).toDouble() - o.cx) <= d + 1e-7)
        }
        assertEquals(3, TuhoField.openings.size)
        assertTrue("귀가 입구보다 좁음", near.half < mouth.half && far.half < mouth.half)
        assertTrue("두 귀 높이가 조금 다름", near.top != far.top)
        assertTrue(near.cx < mouth.cx && mouth.cx < far.cx)
    }

    // ── 테두리·몸통·바닥 ──

    @Test
    fun `테두리에 맞으면 튕겨 0점`() {
        for (o in TuhoField.openings) {
            val deg = if (o === far) 62f else 45f
            val s = at(o, deg, (o.cx + o.half + GameConfig.RIM / 2f).toDouble())
            assertSame(Outcome.RIM, s.outcome)
            assertEquals(0, s.points)
            assertTrue(s.outcome.bounces)
            assertTrue("뒤로 튕김", s.bounceVx < 0.0)
            assertTrue("위로 튕김", s.bounceVy > 0.0)
            assertTrue(s.bounceT > 0.0)
            // 튕긴 뒤 바닥에 닿음
            assertEquals(0.0, s.bounceYAt(s.bounceT), 1e-9)
        }
    }

    @Test
    fun `몸통에 맞으면 튕겨 0점`() {
        // 낮고 평평하게 몸통 옆구리로
        val s = through(20f, (GameConfig.JAR_X - 0.2f).toDouble(), 0.3)
        assertSame(Outcome.BODY, s.outcome)
        assertEquals(0, s.points)
        assertTrue(s.bounceT > 0.0)
        // 닿은 자리는 몸통 가장자리 (바로 앞은 밖, 바로 뒤는 안)
        assertFalse(TuhoField.insideSolid(s.xAt(s.t - 1e-4), s.yAt(s.t - 1e-4)))
        assertTrue(TuhoField.insideSolid(s.xAt(s.t + 1e-4), s.yAt(s.t + 1e-4)))
        assertEquals(s.x, s.xAt(s.t), 1e-9)
        // 목 옆면 (입구 높이보다 낮게) → 몸통 판정
        val neck = through(30f, (mouth.cx - mouth.outer - 0.002f).toDouble(), 0.5)
        assertSame(Outcome.BODY, neck.outcome)
        assertTrue("입구보다 아래에서 맞음", neck.y < mouth.top)
        // 목과 귀 사이 틈으로 떨어지면 팔·몸통에 맞음
        val gap = through(55f, (mouth.cx - mouth.outer - 0.015f).toDouble(), near.top.toDouble())
        assertSame(Outcome.BODY, gap.outcome)
    }

    @Test
    fun `바닥에 떨어지면 0점`() {
        val short = TuhoField.judge(GameConfig.ANGLE_MIN, GameConfig.SPEED_MIN)
        assertSame(Outcome.FLOOR, short.outcome)
        assertEquals(0.0, short.y, 0.0)
        assertTrue("짧게 떨어짐", short.x < near.cx - near.outer)
        assertEquals(TuhoField.floorX(GameConfig.ANGLE_MIN, GameConfig.SPEED_MIN), short.x, 1e-9)
        val long = TuhoField.judge(45f, GameConfig.SPEED_MAX)
        assertSame(Outcome.FLOOR, long.outcome)
        assertTrue("항아리 넘어감", long.x > GameConfig.JAR_X + GameConfig.BODY_RX)
        assertEquals(0, long.points)
        assertFalse(long.outcome.bounces)
    }

    // ── 포물선 식 ──

    @Test
    fun `꼭짓점과 바닥 x 는 식과 같다`() {
        val deg = 50f
        val v = 5f
        val th = Math.toRadians(deg.toDouble())
        val vx = v * Math.cos(th)
        val vy = v * Math.sin(th)
        val g = GameConfig.GRAVITY.toDouble()
        assertEquals(vy / g, TuhoField.apexT(deg, v), 1e-12)
        assertEquals(GameConfig.RELEASE_Y + vy * vy / (2 * g), TuhoField.apexY(deg, v), 1e-12)
        assertEquals(GameConfig.RELEASE_X + vx * vy / g, TuhoField.apexX(deg, v), 1e-12)
        val tl = (vy + Math.sqrt(vy * vy + 2 * g * GameConfig.RELEASE_Y)) / g
        assertEquals(GameConfig.RELEASE_X + vx * tl, TuhoField.floorX(deg, v), 1e-12)
        // 숫자로 따라가 본 비행과 비교 (작은 간격)
        var x = GameConfig.RELEASE_X.toDouble()
        var y = GameConfig.RELEASE_Y.toDouble()
        var cvy = vy
        var top = y
        val dt = 1e-5
        while (y > 0.0) {
            x += vx * dt
            y += cvy * dt - g * dt * dt / 2
            cvy -= g * dt
            top = maxOf(top, y)
        }
        assertEquals(TuhoField.floorX(deg, v), x, 1e-3)
        assertEquals(TuhoField.apexY(deg, v), top, 1e-6)
        // Shot 의 위치 식도 같음
        val s = TuhoField.judge(deg, v)
        val ta = TuhoField.apexT(deg, v)
        assertEquals(TuhoField.apexY(deg, v), s.yAt(ta), 1e-9)
        assertTrue(s.yAt(ta - 0.01) < s.yAt(ta) && s.yAt(ta + 0.01) < s.yAt(ta))
        assertEquals(0.0, s.vyAt(ta), 1e-9)
    }

    @Test
    fun `높이를 내려가며 지나는 x 와 지나는 속도는 서로 맞다`() {
        for (deg in floatArrayOf(20f, 35f, 50f, 65f)) {
            val v = TuhoField.speedThrough(deg, 2.0, 0.6)
            val x = TuhoField.descendX(deg, v.toFloat(), 0.6)
            assertEquals(2.0, x, 1e-5)
        }
        // 못 올라가는 높이
        assertTrue(TuhoField.descendX(20f, 3f, 5.0).isNaN())
        assertTrue(TuhoField.speedThrough(20f, 2.0, 3.0).isNaN())
    }

    // ── 게이지 → 각도·힘 ──

    @Test
    fun `게이지 값을 각도와 힘으로`() {
        assertEquals(GameConfig.ANGLE_MIN, TuhoField.angleFor(0f), 1e-6f)
        assertEquals(GameConfig.ANGLE_MAX, TuhoField.angleFor(1f), 1e-6f)
        assertEquals((GameConfig.ANGLE_MIN + GameConfig.ANGLE_MAX) / 2f, TuhoField.angleFor(0.5f), 1e-5f)
        assertEquals(GameConfig.SPEED_MIN, TuhoField.speedFor(0f), 1e-6f)
        assertEquals(GameConfig.SPEED_MAX, TuhoField.speedFor(1f), 1e-6f)
        assertEquals(GameConfig.SPEED_MIN + (GameConfig.SPEED_MAX - GameConfig.SPEED_MIN) * 0.25f, TuhoField.speedFor(0.25f), 1e-5f)
        assertEquals(GameConfig.ANGLE_MAX, TuhoField.angleFor(1.4f), 1e-6f)
        assertEquals(GameConfig.SPEED_MIN, TuhoField.speedFor(-0.2f), 1e-6f)
        // 왕복 게이지: 반 주기에 끝(1), 4분의 1 주기에 가운데
        val g = Gauge(GameConfig.ANGLE_PERIOD_MS)
        g.start(1000L)
        assertEquals(0.5f, g.valueAt(1000L + GameConfig.ANGLE_PERIOD_MS / 4), 1e-6f)
        assertEquals(1f, g.valueAt(1000L + GameConfig.ANGLE_PERIOD_MS / 2), 1e-6f)
        assertEquals(0.25f, g.valueAt(1000L + GameConfig.ANGLE_PERIOD_MS * 7 / 8), 1e-6f)
        // judgeGauge = judge(angleFor, speedFor)
        val a = TuhoField.judgeGauge(0.4f, 0.3f)
        val b = TuhoField.judge(TuhoField.angleFor(0.4f), TuhoField.speedFor(0.3f))
        assertSame(b.outcome, a.outcome)
        assertEquals(b.x, a.x, 0.0)
    }

    // ── 해가 있는지 (모든 결과가 가능한 각도·힘이 있음) ──

    @Test
    fun `입구와 두 귀 모두 넣을 수 있는 각도·힘이 있다`() {
        val count = HashMap<Outcome, Int>()
        val angleSteps = 60
        val powerSteps = 600
        for (i in 0..angleSteps) for (j in 0..powerSteps) {
            val s = TuhoField.judgeGauge(i / angleSteps.toFloat(), j / powerSteps.toFloat())
            count[s.outcome] = (count[s.outcome] ?: 0) + 1
        }
        println("TUHO 결과 분포: $count")
        for (o in Outcome.values()) assertTrue("$o 가 나올 수 있어야 함", (count[o] ?: 0) > 0)
        // 입구가 귀보다 쉬움 (넓음)
        assertTrue(count[Outcome.MOUTH]!! > count[Outcome.EAR_NEAR]!!)
        assertTrue(count[Outcome.MOUTH]!! > count[Outcome.EAR_FAR]!!)
        // 각도마다: 입구에 넣을 수 있는 힘 구간이 게이지의 2% 이상 (너무 어렵지 않게)
        for (i in 0..angleSteps) {
            var n = 0
            for (j in 0..powerSteps) if (TuhoField.judgeGauge(i / angleSteps.toFloat(), j / powerSteps.toFloat()).outcome == Outcome.MOUTH) n++
            assertTrue("각도 칸 $i 입구 힘 구간 $n", n >= powerSteps * 0.02)
        }
    }
}
