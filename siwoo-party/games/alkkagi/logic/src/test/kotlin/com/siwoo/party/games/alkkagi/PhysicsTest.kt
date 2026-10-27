package com.siwoo.party.games.alkkagi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class PhysicsTest {

    private val eps = 1e-4f

    private fun stone(id: Int, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f) =
        Stone(id, 0).also { it.x = x; it.y = y; it.vx = vx; it.vy = vy }

    private fun px(vararg s: Stone) = s.fold(0f) { acc, it -> acc + it.vx }
    private fun py(vararg s: Stone) = s.fold(0f) { acc, it -> acc + it.vy }
    private fun energy(vararg s: Stone) = s.fold(0f) { acc, it -> acc + it.vx * it.vx + it.vy * it.vy }

    // ── 충돌: 운동량 보존 ──

    @Test fun `정면 충돌 - 같은 질량이면 속도를 맞바꾸고 운동량 보존`() {
        val a = stone(0, 5f, 5f, vx = 7f)
        val b = stone(1, 6.9f, 5f)
        val hit = Physics.collide(a, b)
        assertEquals("충돌 세기 = 다가오던 상대 속도", 7f, hit, eps)
        assertEquals(0f, a.vx, eps)
        assertEquals(0f, a.vy, eps)
        assertEquals(7f, b.vx, eps)
        assertEquals(0f, b.vy, eps)
        assertEquals(7f, px(a, b), eps)
        assertEquals("겹침도 풀림", 2f * GameConfig.STONE_R, b.x - a.x, eps)
    }

    @Test fun `서로 마주 오는 정면 충돌도 속도 교환`() {
        val a = stone(0, 5f, 5f, vx = 4f, vy = 1f)
        val b = stone(1, 6.95f, 5f, vx = -3f, vy = -2f)
        Physics.collide(a, b)
        assertEquals(-3f, a.vx, eps)
        assertEquals("법선에 수직인 성분은 그대로", 1f, a.vy, eps)
        assertEquals(4f, b.vx, eps)
        assertEquals(-2f, b.vy, eps)
    }

    @Test fun `비스듬한 충돌 - 운동량 벡터와 에너지 보존`() {
        val a = stone(0, 3f, 3f, vx = 9f, vy = 2f)
        val b = stone(1, 4.2f, 4.5f, vx = -1f, vy = 0.5f)
        val p0x = px(a, b)
        val p0y = py(a, b)
        val e0 = energy(a, b)
        val hit = Physics.collide(a, b)
        assertTrue(hit > 0f)
        assertEquals(p0x, px(a, b), eps)
        assertEquals(p0y, py(a, b), eps)
        assertEquals("탄성 충돌", e0, energy(a, b), 1e-3f)
        // 맞은 알은 중심을 잇는 방향으로 밀려난다
        val nx = b.x - a.x
        val ny = b.y - a.y
        assertTrue((b.vx - a.vx) * nx + (b.vy - a.vy) * ny >= -eps)
    }

    @Test fun `무작위 충돌 100번 모두 운동량 보존`() {
        val r = Random(3)
        repeat(100) {
            val a = stone(0, 10f, 10f, r.nextFloat() * 20f - 10f, r.nextFloat() * 20f - 10f)
            val ang = r.nextDouble() * Math.PI * 2
            val d = 0.5f + r.nextFloat() * 1.45f
            val b = stone(1, 10f + d * kotlin.math.cos(ang).toFloat(), 10f + d * kotlin.math.sin(ang).toFloat(),
                r.nextFloat() * 20f - 10f, r.nextFloat() * 20f - 10f)
            val p0x = px(a, b)
            val p0y = py(a, b)
            Physics.collide(a, b)
            assertEquals(p0x, px(a, b), 1e-3f)
            assertEquals(p0y, py(a, b), 1e-3f)
        }
    }

    @Test fun `떨어져 있거나 멀어지는 중이면 속도 그대로`() {
        val a = stone(0, 5f, 5f, vx = 3f)
        val b = stone(1, 7.5f, 5f)
        assertEquals(-1f, Physics.collide(a, b), 0f)
        assertEquals(3f, a.vx, 0f)
        // 겹쳤지만 서로 멀어지는 중 → 밀어내기만
        val c = stone(2, 5f, 5f, vx = -3f)
        val d = stone(3, 6.5f, 5f, vx = 2f)
        assertEquals(0f, Physics.collide(c, d), 0f)
        assertEquals(-3f, c.vx, 0f)
        assertEquals(2f, d.vx, 0f)
        assertEquals(2f, d.x - c.x, eps)
    }

    @Test fun `한 단계에 여러 알 충돌 - 일렬로 붙은 알은 맨 끝 알이 튀어 나감`() {
        // 0 이 붙어 있는 1·2 를 정면으로 침 (뉴턴의 요람)
        val s = arrayOf(stone(0, 4f, 10f, vx = 20f), stone(1, 6.05f, 10f), stone(2, 8.05f, 10f))
        val t = Table(s)
        val p0 = px(*s)
        repeat(6) { t.step() }
        assertTrue("맨 끝 알이 가장 빠름: ${s[2].vx}", s[2].vx > 15f)
        assertTrue(abs(s[0].vx) < 2f && abs(s[1].vx) < 2f)
        // 6단계 동안 마찰만큼만 운동량이 줄었다
        assertTrue(px(*s) <= p0 && px(*s) > p0 - 3f * GameConfig.FRICTION * 6 * GameConfig.STEP_SEC - 0.1f)
        for (i in s.indices) for (j in i + 1 until s.size) {
            val dx = s[j].x - s[i].x
            val dy = s[j].y - s[i].y
            assertTrue("겹침 없음", dx * dx + dy * dy >= 4f - 1e-3f)
        }
    }

    // ── 판 밖 판정 ──

    @Test fun `중심이 판 밖이면 떨어짐, 걸쳐 있어도 중심이 안이면 판 위`() {
        val w = GameConfig.BOARD_W
        val h = GameConfig.BOARD_H
        assertTrue(Physics.isOffBoard(-0.01f, 5f))
        assertTrue(Physics.isOffBoard(w + 0.01f, 5f))
        assertTrue(Physics.isOffBoard(5f, -0.5f))
        assertTrue(Physics.isOffBoard(5f, h + 0.3f))
        // 반지름 1 → 몸의 절반 이상이 밖이어도 중심은 안
        assertFalse(Physics.isOffBoard(0.1f, 5f))
        assertFalse(Physics.isOffBoard(w - 0.2f, h - 0.9f))
        assertFalse(Physics.isOffBoard(0f, 0f))
    }

    @Test fun `판 밖으로 미끄러진 알은 떨어지는 연출 뒤 사라짐`() {
        val s = arrayOf(stone(0, 1.5f, 10f, vx = -9f), stone(1, 12f, 10f))
        val t = Table(s, fallMs = 500)
        var fell = 0
        t.listener = object : Table.Listener {
            override fun onCollide(x: Float, y: Float, speed: Float) {}
            override fun onFall(stone: Stone) { fell++ }
        }
        var k = 0
        while (s[0].onBoard && k < 600) { t.step(); k++ }
        assertSame(StoneState.FALLING, s[0].state)
        assertEquals(1, fell)
        assertTrue("중심이 밖", s[0].x < 0f)
        assertFalse("떨어지는 중엔 아직 정지 아님", t.settled)
        // 약 0.5초(30단계) 뒤 사라짐
        repeat(29) { t.step() }
        assertTrue(s[0].fall > 0.9f)
        repeat(3) { t.step() }
        assertSame(StoneState.GONE, s[0].state)
        assertTrue(t.settled)
        assertSame(StoneState.ON_BOARD, s[1].state)
    }

    @Test fun `걸쳐서 멈춘 알은 떨어지지 않음`() {
        // 가장자리 쪽으로 천천히 → 중심이 판 안쪽 0.2 부근에서 멈춤
        val v = 4f
        val s = arrayOf(stone(0, 0.8f, 10f, vx = -v))
        val t = Table(s)
        repeat(300) { t.step() }
        assertTrue(t.settled)
        assertSame("중심 x=${s[0].x}", StoneState.ON_BOARD, s[0].state)
        assertTrue(s[0].x > 0f && s[0].x < GameConfig.STONE_R)
    }

    // ── 정지 판정 ──

    @Test fun `마찰로 느려지다 임계값 아래면 완전히 정지`() {
        val s = stone(0, 0f, 0f, vx = 0.13f)
        Physics.applyFriction(s, GameConfig.STEP_SEC)
        assertEquals(0f, s.vx, 0f)
        assertFalse(s.moving)
        val f = stone(1, 0f, 0f, vx = 3f, vy = 4f)
        Physics.applyFriction(f, GameConfig.STEP_SEC)
        assertTrue("방향은 그대로, 속도만 줄어듦", f.speed < 5f && f.speed > 4.5f)
        assertEquals(4f / 3f, f.vy / f.vx, eps)
    }

    @Test fun `여러 알이 부딪혀도 유한한 단계 안에 모두 정지`() {
        val g = AlkkagiGame()
        // 가운데로 최대 힘 샷 여러 개
        g.stones[0].vx = GameConfig.ALKKAGI_MAX_POWER
        g.stones[3].vx = GameConfig.ALKKAGI_MAX_POWER
        g.stones[3].vy = -5f
        g.stones[12].vx = -GameConfig.ALKKAGI_MAX_POWER
        val t = g.table
        var k = 0
        while (!t.settled && k < 10_000) { t.step(); k++ }
        assertTrue(t.settled)
        // 최대 속도 32 / 감속 16 → 2초(120단계) 남짓 + 떨어짐 연출
        assertTrue("단계 수 $k", k < 400)
        for (s in g.stones) if (s.onBoard) assertFalse(s.moving)
    }

    // ── 고정 단계 누적기 ──

    private fun shotTable(): Table {
        val g = AlkkagiGame()
        g.flick(5, -4.5f, 1.2f)
        return g.table
    }

    private fun snapshot(t: Table): List<Float> = t.stones.flatMap { listOf(it.x, it.y, it.vx, it.vy, it.fall, it.state.ordinal.toFloat()) }

    @Test fun `프레임을 어떻게 쪼개도 같은 결과`() {
        val a = shotTable()
        val b = shotTable()
        val c = shotTable()
        // a: 1초를 정확히 16·17ms 로, b: 무작위 1..100ms, c: 100ms 씩
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

    @Test fun `자투리 시간은 모아 두었다가 다음 단계에`() {
        val t = Table(arrayOf(stone(0, 5f, 5f)))
        assertEquals(0, t.advance(10))
        assertEquals("10 + 7 = 17ms ≥ 16.67ms", 1, t.advance(7))
        assertEquals(0, t.advance(16))
        assertEquals(1, t.advance(1))
        assertEquals("한 프레임은 최대 100ms(6단계)까지만", 6, t.advance(5000))
    }
}
