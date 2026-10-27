package com.siwoo.party.common.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PhysicsTest {

    private val eps = 1e-4f

    private fun body(x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, r: Float = 1f, m: Float = 1f, id: Int = 0) =
        Body(id, r, m).also { it.x = x; it.y = y; it.vx = vx; it.vy = vy }

    private fun px(vararg b: Body) = b.fold(0f) { acc, it -> acc + it.mass * it.vx }
    private fun py(vararg b: Body) = b.fold(0f) { acc, it -> acc + it.mass * it.vy }
    private fun energy(vararg b: Body) = b.fold(0f) { acc, it -> acc + 0.5f * it.mass * (it.vx * it.vx + it.vy * it.vy) }

    /** 기록용 알림 */
    private class Log : World.Listener {
        val events = ArrayList<String>()
        var settled = 0
        override fun onCollide(a: Body, b: Body, x: Float, y: Float, speed: Float) { events.add("hit ${a.id} ${b.id}") }
        override fun onWall(body: Body, wall: Wall, x: Float, y: Float, speed: Float) { events.add("wall ${body.id}") }
        override fun onCaptured(body: Body, hole: Hole) { events.add("hole ${body.id}") }
        override fun onTrapped(body: Body, zone: Zone) { events.add("trap ${body.id}") }
        override fun onOut(body: Body) { events.add("out ${body.id}") }
        override fun onSettled() { settled++ }
    }

    /** 멈출 때까지 한 단계씩 (최대 n 단계), 진행한 단계 수 */
    private fun runUntilSettled(w: World, n: Int = 5000): Int {
        var k = 0
        while (!w.settled && k < n) { w.step(); k++ }
        return k
    }

    // ── 충돌: 운동량 보존 ──

    @Test fun `정면 충돌 - 같은 질량 완전 탄성이면 속도를 맞바꿈`() {
        val a = body(5f, 5f, vx = 7f)
        val b = body(6.9f, 5f)
        val hit = PhysicsMath.collideCircles(a, b)
        assertEquals("충돌 세기 = 다가오던 상대 속도", 7f, hit, eps)
        assertEquals(0f, a.vx, eps)
        assertEquals(7f, b.vx, eps)
        assertEquals(7f, px(a, b), eps)
        assertEquals("겹침도 풀림", 2f, b.x - a.x, eps)
    }

    @Test fun `정면 충돌 - 다른 질량도 운동량과 에너지 보존`() {
        val a = body(5f, 5f, vx = 6f, m = 1f)
        val b = body(6.95f, 5f, vx = -1f, m = 3f, r = 1f)
        val p0 = px(a, b)
        val e0 = energy(a, b)
        assertTrue(PhysicsMath.collideCircles(a, b) > 0f)
        assertEquals(p0, px(a, b), 1e-3f)
        assertEquals(e0, energy(a, b), 1e-3f)
        // 1차원 탄성 충돌 공식: va' = ((ma − mb)va + 2 mb vb) / (ma + mb)
        assertEquals((-2f * 6f + 2f * 3f * -1f) / 4f, a.vx, 1e-4f)
        assertEquals((2f * 6f + 2f * -1f) / 4f, b.vx, 1e-4f)
        // 겹침은 질량 비율대로: 가벼운 a 가 3배 더 밀림
        assertEquals(2f, b.x - a.x, eps)
        assertEquals(0.05f * 0.75f, 5f - a.x, 1e-4f)
    }

    @Test fun `비스듬한 충돌 - 같은 질량·다른 질량 모두 운동량 벡터와 에너지 보존`() {
        for (mb in floatArrayOf(1f, 2.5f, 0.4f)) {
            val a = body(3f, 3f, vx = 9f, vy = 2f, r = 1f, m = 1f)
            val b = body(4.2f, 4.5f, vx = -1f, vy = 0.5f, r = 1.2f, m = mb)
            val p0x = px(a, b)
            val p0y = py(a, b)
            val e0 = energy(a, b)
            assertTrue(PhysicsMath.collideCircles(a, b) > 0f)
            assertEquals(p0x, px(a, b), 1e-3f)
            assertEquals(p0y, py(a, b), 1e-3f)
            assertEquals("탄성 충돌 mb=$mb", e0, energy(a, b), 1e-3f)
        }
    }

    @Test fun `반발 계수가 1보다 작으면 운동량은 보존, 에너지는 줄어듦`() {
        val a = body(5f, 5f, vx = 6f).also { it.restitution = 0.5f }
        val b = body(6.9f, 5f, m = 2f)
        val p0 = px(a, b)
        val e0 = energy(a, b)
        PhysicsMath.collideCircles(a, b)
        assertEquals(p0, px(a, b), 1e-3f)
        assertTrue(energy(a, b) < e0 - 0.5f)
        // 떨어지는 상대 속도 = e × 다가오던 상대 속도
        assertEquals(0.5f * 6f, b.vx - a.vx, 1e-4f)
    }

    @Test fun `무작위 충돌 200번 모두 운동량 보존`() {
        val r = Random(5)
        repeat(200) {
            val a = body(10f, 10f, r.nextFloat() * 20f - 10f, r.nextFloat() * 20f - 10f, r = 0.5f + r.nextFloat(), m = 0.2f + r.nextFloat() * 4f)
            val ang = r.nextDouble() * Math.PI * 2
            val d = 0.3f + r.nextFloat() * 1.5f
            val b = body(10f + d * cos(ang).toFloat(), 10f + d * sin(ang).toFloat(),
                r.nextFloat() * 20f - 10f, r.nextFloat() * 20f - 10f, r = 0.5f + r.nextFloat(), m = 0.2f + r.nextFloat() * 4f)
            val p0x = px(a, b)
            val p0y = py(a, b)
            val e0 = energy(a, b)
            PhysicsMath.collideCircles(a, b)
            assertEquals(p0x, px(a, b), 2e-3f)
            assertEquals(p0y, py(a, b), 2e-3f)
            assertEquals(e0, energy(a, b), 1e-2f)
        }
    }

    @Test fun `무한 질량 물체는 밀리지 않고 상대만 튕겨 나감`() {
        val wall = body(10f, 5f, m = Float.POSITIVE_INFINITY, id = 1)
        assertTrue(wall.immovable)
        val a = body(8.1f, 5f, vx = 5f)
        val hit = PhysicsMath.collideCircles(a, wall)
        assertEquals(5f, hit, eps)
        assertEquals("완전 탄성 반사", -5f, a.vx, eps)
        assertEquals(10f, wall.x, 0f)
        assertEquals(0f, wall.vx, 0f)
        assertEquals("겹침은 움직이는 쪽만 풀림", 8f, a.x, eps)

        // 세계 안에서: 말렛(무한 질량)을 움직여 퍽을 침 → 말렛 속도는 그대로
        val w = World()
        val mallet = w.addBody(body(5f, 5f, vx = 10f, m = Float.POSITIVE_INFINITY, r = 1.5f, id = 1))
        val puck = w.addBody(body(7.6f, 5f, id = 2))
        repeat(5) { w.step() }
        assertEquals(10f, mallet.vx, 0f)
        assertEquals(0f, mallet.vy, 0f)
        assertTrue("퍽이 말렛보다 빠르게 튕겨 나감: ${puck.vx}", puck.vx > 15f)
        // 무한 질량끼리는 서로 통과 (아무 일 없음)
        val c = body(0f, 0f, vx = 1f, m = Float.POSITIVE_INFINITY)
        val d = body(0.5f, 0f, m = 0f)
        assertEquals(-1f, PhysicsMath.collideCircles(c, d), 0f)
        assertEquals(1f, c.vx, 0f)
    }

    @Test fun `한 단계에 여러 물체 - 일렬로 붙은 알은 맨 끝 알이 튀어 나감`() {
        val w = World()
        val s = arrayOf(body(4f, 10f, vx = 20f, id = 0), body(6.05f, 10f, id = 1), body(8.05f, 10f, id = 2))
        for (b in s) w.addBody(b)
        repeat(6) { w.step() }
        assertTrue("맨 끝 알이 가장 빠름: ${s[2].vx}", s[2].vx > 15f)
        assertTrue(abs(s[0].vx) < 2f && abs(s[1].vx) < 2f)
        for (i in s.indices) for (j in i + 1 until s.size) {
            val dx = s[j].x - s[i].x
            val dy = s[j].y - s[i].y
            assertTrue("겹침 없음", dx * dx + dy * dy >= 4f - 1e-3f)
        }
    }

    @Test fun `충돌 묶음 비트가 겹치지 않으면 서로 통과`() {
        val w = World()
        val a = w.addBody(body(4f, 5f, vx = 10f).also { it.mask = 1 })
        val b = w.addBody(body(6f, 5f).also { it.mask = 2 })
        val log = Log()
        w.listener = log
        repeat(60) { w.step() }
        assertTrue(log.events.isEmpty())
        assertEquals(0f, b.vx, 0f)
        assertTrue(a.x > 6f)
    }

    // ── 벽 ──

    @Test fun `벽 반사 - 수직으로 부딪히면 반대로, 비스듬히면 법선 성분만 뒤집힘`() {
        val w = World().apply { friction = 0f; drag = 0f }
        val log = Log()
        w.listener = log
        w.addBox(0f, 0f, 20f, 10f, restitution = 1f)
        val a = w.addBody(body(18.5f, 5f, vx = 6f, id = 1))
        repeat(10) { w.step() }
        assertEquals(-6f, a.vx, eps)
        assertEquals(0f, a.vy, eps)
        assertTrue("벽 안쪽", a.x <= 19f + eps)
        assertEquals(listOf("wall 1"), log.events)

        // 비스듬히 (윗벽 y=0): vx 그대로, vy 반전
        val b = body(10f, 1.5f, vx = 3f, vy = -4f, id = 2)
        w.addBody(b)
        repeat(10) { w.step() }
        assertEquals(3f, b.vx, eps)
        assertEquals(4f, b.vy, eps)

        // 반발 0.5 → 법선 속도 절반으로
        val w2 = World().apply { friction = 0f; drag = 0f }
        w2.addWall(0f, 10f, 20f, 10f, restitution = 0.5f)
        val c = w2.addBody(body(5f, 8.5f, vx = 2f, vy = 6f))
        repeat(10) { w2.step() }
        assertEquals(2f, c.vx, eps)
        assertEquals(-3f, c.vy, eps)
    }

    @Test fun `열린 변(골문)으로는 나가고, 기둥(끝점)은 튕김`() {
        val w = World().apply { friction = 0f; drag = 0f }
        val log = Log()
        w.listener = log
        // 오른쪽 변만 가운데 4..6 이 뚫림
        w.addBox(0f, 0f, 20f, 10f, sides = World.SIDE_ALL and World.SIDE_RIGHT.inv())
        w.addWallWithGap(20f, 0f, 20f, 10f, 4f, 6f)
        assertEquals(5, w.walls.size)
        w.setBounds(0f, 0f, 20f, 10f)
        val goal = w.addBody(body(15f, 5f, vx = 10f, r = 0.5f, id = 1))
        val post = w.addBody(body(15f, 2f, vx = 10f, r = 0.5f, id = 2))
        repeat(60) { w.step() }
        assertFalse("골문으로 나감", goal.active)
        assertTrue(post.active)
        assertTrue(post.vx < 0f)
        assertTrue(log.events.toString(), log.events.contains("out 1") && log.events.contains("wall 2"))
    }

    // ── 마찰·구역 ──

    @Test fun `마찰로 느려지다 임계값 아래면 완전히 정지`() {
        val s = body(0f, 0f, vx = 0.13f)
        PhysicsMath.applyFriction(s, 16f, 0.35f, 0.12f, PhysicsConfig.STEP_SEC)
        assertFalse(s.moving)
        val f = body(0f, 0f, vx = 3f, vy = 4f)
        PhysicsMath.applyFriction(f, 16f, 0.35f, 0.12f, PhysicsConfig.STEP_SEC)
        assertTrue("방향은 그대로, 속도만 줄어듦", f.speed < 5f && f.speed > 4.5f)
        assertEquals(4f / 3f, f.vy / f.vx, eps)
    }

    /** 같은 힘으로 굴린 물체가 멈춘 x */
    private fun slide(zone: Zone?): Float {
        val w = World()
        if (zone != null) w.addZone(zone)
        val b = w.addBody(body(0f, 0f, vx = 20f))
        runUntilSettled(w)
        assertTrue(w.settled)
        return b.x
    }

    @Test fun `영역 마찰 - 모래에선 빨리 서고 얼음에선 더 멀리`() {
        val plain = slide(null)
        val sand = slide(Zone.rect(-5f, -5f, 100f, 5f, friction = 3f))
        val ice = slide(Zone.rect(-5f, -5f, 100f, 5f, friction = 0.3f))
        assertTrue("모래 $sand < 보통 $plain", sand < plain * 0.5f)
        assertTrue("얼음 $ice > 보통 $plain", ice > plain * 1.5f)
        // 구역 밖(보통)에서 들어가면 그때부터 느려짐
        val half = slide(Zone.rect(5f, -5f, 100f, 5f, friction = 3f))
        assertTrue(half > sand && half < plain)
        // 물체 마찰 배수(컬링 비질)도 같이 곱해진다
        val w = World()
        val swept = w.addBody(body(0f, 0f, vx = 20f).also { it.friction = 0.3f })
        runUntilSettled(w)
        assertEquals(ice, swept.x, 1e-3f)
    }

    @Test fun `경사 구역은 가속, 그릇 구역은 가운데로 모음`() {
        val w = World().apply { friction = 1f }
        w.addZone(Zone.rect(0f, 0f, 50f, 50f).also { it.ax = 10f })
        val b = w.addBody(body(5f, 25f))
        w.wake()
        assertTrue(w.update(100))
        assertTrue("내리막으로 굴러감", b.vx > 0f && b.x > 5f)

        val w2 = World().apply { friction = 0.5f }
        w2.addZone(Zone.circle(0f, 0f, 20f).also { it.pull = 4f })
        val c = w2.addBody(body(10f, 0f))
        w2.wake()
        var maxV = 0f
        repeat(120) { w2.step(); if (c.vx < maxV) maxV = c.vx }
        assertTrue("가운데 쪽으로 가속: $maxV", maxV < -5f)
    }

    @Test fun `회전이 있으면 휘어져 가고 회전은 점점 줄어 멈춤`() {
        val w = World().apply { friction = 2f }
        val straight = w.addBody(body(0f, 0f, vx = 10f, id = 0).also { it.mask = 0 })
        val curled = w.addBody(body(0f, 0f, vx = 10f, id = 1).also { it.mask = 0; it.spin = 2f; it.curl = 0.2f })
        runUntilSettled(w)
        assertEquals(0f, straight.y, 0f)
        assertTrue("양수 회전 = 화면에서 시계 방향(아래로) 휨: ${curled.y}", curled.y > 1f)
        assertEquals(0f, curled.spin, 0f)
        assertTrue(w.settled)
    }

    // ── 구멍·물 ──

    @Test fun `구멍 판정 - 안에서 느리면 빠짐, 빠르면 지나감, 밖이면 안 빠짐`() {
        val h = Hole(10f, 10f, 1f, maxCaptureSpeed = 3f)
        assertTrue("안 + 느림", h.captures(body(10.5f, 10.3f, vx = 2f)))
        assertTrue("안 + 멈춤", h.captures(body(10f, 10f)))
        assertFalse("안 + 빠름", h.captures(body(10.2f, 10f, vx = 8f)))
        assertFalse("밖 + 멈춤", h.captures(body(11.2f, 10f)))
        assertFalse("몸은 걸쳐도 중심이 밖", h.captures(body(10f, 11.01f)))

        // 세계 안에서: 빠르게 지나가는 공은 그대로, 느리게 굴러 온 공은 빠짐
        val w = World()
        val log = Log()
        w.listener = log
        w.addHole(Hole(10f, 0f, 1f, maxCaptureSpeed = 4f))
        w.addHole(Hole(10f, 20f, 1f, maxCaptureSpeed = 4f))
        val fast = w.addBody(body(0f, 0f, vx = 30f, r = 0.4f, id = 1))
        val slow = w.addBody(body(8.5f, 20f, vx = 6f, r = 0.4f, id = 2))
        runUntilSettled(w)
        assertTrue("빠른 공은 지나감: x=${fast.x}", fast.active && fast.x > 12f)
        assertFalse(slow.active)
        assertEquals(listOf("hole 2"), log.events)
        assertEquals(1, log.settled)
    }

    @Test fun `물 구역에 들어가면 빠짐`() {
        val w = World()
        val log = Log()
        w.listener = log
        w.addZone(Zone.rect(8f, -2f, 12f, 2f).also { it.trap = true })
        val b = w.addBody(body(0f, 0f, vx = 20f, id = 3))
        runUntilSettled(w)
        assertFalse(b.active)
        assertTrue(b.x >= 8f && b.x < 9f)
        assertEquals(listOf("trap 3"), log.events)
    }

    @Test fun `경계 밖으로 중심이 나가면 onOut, 걸쳐 있으면 그대로`() {
        val w = World()
        val log = Log()
        w.listener = log
        w.setBounds(0f, 0f, 24f, 20f)
        val out = w.addBody(body(1.5f, 10f, vx = -9f, id = 1))
        val edge = w.addBody(body(0.8f, 5f, vx = -4f, id = 2))
        runUntilSettled(w)
        assertFalse(out.active)
        assertTrue("속도는 남아 있음 (떨어지는 연출용)", out.vx < 0f)
        assertTrue(edge.active)
        assertTrue(edge.x > 0f && edge.x < 1f)
        assertEquals(listOf("out 1"), log.events)
    }

    // ── 고정 단계·정지 ──

    private fun scene(): World {
        val w = World()
        w.addBox(0f, 0f, 30f, 16f, restitution = 0.8f)
        w.addZone(Zone.rect(10f, 0f, 20f, 8f, friction = 2f))
        w.addHole(Hole(25f, 12f, 0.8f, 3f))
        for (i in 0 until 6) w.addBody(body(6f + i * 2.5f, 4f + (i % 3) * 3f, id = i, m = 1f + i * 0.3f))
        w.bodies[0].setVelocity(28f, 9f)
        w.bodies[3].setVelocity(-12f, 15f)
        w.bodies[3].spin = 3f
        w.bodies[3].curl = 0.1f
        return w
    }

    private fun snapshot(w: World): List<Float> =
        w.bodies.flatMap { listOf(it.x, it.y, it.vx, it.vy, it.spin, it.angle, if (it.active) 1f else 0f) }

    @Test fun `프레임을 어떻게 쪼개도 같은 결과`() {
        val a = scene()
        val b = scene()
        val c = scene()
        var left = 3000L
        var i = 0
        while (left > 0) { val d = minOf(left, if (i++ % 3 == 0) 16L else 17L); a.advance(d); left -= d }
        val r = Random(9)
        left = 3000L
        while (left > 0) { val d = minOf(left, 1L + r.nextInt(100)); b.advance(d); left -= d }
        repeat(30) { c.advance(100) }
        assertEquals("3초 = 180단계", 180L, a.steps)
        assertEquals(180L, b.steps)
        assertEquals(180L, c.steps)
        assertEquals(snapshot(a), snapshot(b))
        assertEquals(snapshot(a), snapshot(c))
    }

    @Test fun `자투리 시간은 모아 두었다가 다음 단계에, 한 프레임은 최대 100ms`() {
        val w = World()
        w.addBody(body(5f, 5f))
        assertEquals(0, w.advance(10))
        assertEquals("10 + 7 = 17ms ≥ 16.67ms", 1, w.advance(7))
        assertEquals(0, w.advance(16))
        assertEquals(1, w.advance(1))
        assertEquals(6, w.advance(5000))
        val f = FixedStep()
        assertEquals(0, f.add(-5))
        assertEquals(6, f.add(1000))
    }

    @Test fun `모두 멈추면 정지 알림은 한 번만, 멈춘 뒤엔 update 가 돌지 않음`() {
        val w = scene()
        val log = Log()
        w.listener = log
        assertTrue(w.isRunning)
        var frames = 0
        while (w.update(16) && frames < 3000) frames++
        assertTrue("유한한 시간 안에 정지: $frames", frames in 10 until 3000)
        assertTrue(w.settled)
        assertFalse(w.isRunning)
        assertEquals(1, log.settled)
        val steps = w.steps
        repeat(20) { assertFalse(w.update(16)) }
        assertEquals("멈춘 뒤엔 단계 진행 없음", steps, w.steps)
        assertEquals(1, log.settled)
        // 다시 굴리면 다시 한 번
        w.bodies.first { it.active }.setVelocity(5f, 0f)
        while (w.update(16)) Unit
        assertEquals(2, log.settled)
        // 처음부터 멈춰 있으면 알림 없음
        val still = World()
        val l2 = Log()
        still.listener = l2
        still.addBody(body(1f, 1f))
        repeat(10) { still.step() }
        assertFalse(still.update(16))
        assertEquals(0, l2.settled)
    }

    @Test fun `회전 각도는 쌓이고 범위 안에 머문다`() {
        val w = World()
        val b = w.addBody(body(0f, 0f).also { it.spin = 20f; it.spinFriction = 0f; it.spinDamping = 0f })
        repeat(120) { w.step() }
        assertEquals(20f, b.spin, 0f)
        assertTrue(b.angle >= -3.1416f && b.angle <= 3.1416f)
        assertFalse(w.settled)
        assertNull(w.zoneAt(0f, 0f))
    }
}
