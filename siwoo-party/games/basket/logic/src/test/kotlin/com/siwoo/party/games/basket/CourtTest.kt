package com.siwoo.party.games.basket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 골대 물리: 림 튕김·결정성·골 판정·모든 거리에서 넣는 방법이 있는지·스와이프 → 슛 */
class CourtTest {

    private class Rec : Court.Listener {
        var rims = 0
        var boards = 0
        var mades = 0
        var floors = 0
        var cleanMade = false
        override fun onRim(x: Float, y: Float, speed: Float) { rims++ }
        override fun onBoard(x: Float, y: Float, speed: Float) { boards++ }
        override fun onMade(clean: Boolean) { mades++; cleanMade = clean }
        override fun onFloor() { floors++ }
    }

    /** 끝날 때까지 한 단계씩 */
    private fun run(c: Court): Int {
        var n = 0
        while (c.flying && n < 2000) { c.step(); n++ }
        assertFalse("비행이 끝나야 함", c.flying)
        return n
    }

    /** (x, y) 에서 가만히 떨어뜨림 */
    private fun drop(x: Float, y: Float = 3.7f): Court {
        val c = Court()
        c.launch(x, y, 0f, 90f)
        return c
    }

    private fun shot(distance: Float, angle: Float, power: Float): Court {
        val c = Court()
        c.launch(-distance, GameConfig.RELEASE_Y, Aim.speed(distance, angle, power), angle)
        run(c)
        return c
    }

    // ── 골 판정 ──

    @Test fun `림 가운데로 떨어지면 깨끗한 골`() {
        val c = drop(0f)
        val r = Rec()
        c.listener = r
        run(c)
        assertTrue(c.made)
        assertTrue(c.clean)
        assertEquals(0, c.rimHits)
        assertEquals(1, r.mades)
        assertTrue(r.cleanMade)
        assertEquals("바닥에 닿아 끝", 1, r.floors)
        assertEquals(GameConfig.BALL_R, c.ball.y, 1e-4f)
    }

    @Test fun `아래에서 위로 림을 지난 공은 다시 내려와도 골 아님`() {
        val c = Court()
        // 림 바로 밑에서 똑바로 위로 → 림 사이를 올라갔다가 그대로 다시 내려옴
        c.launch(0f, 1.6f, 6f, 90f)
        var wentAbove = false
        var crossedDown = false
        var prevY = c.ball.y
        while (c.flying) {
            c.step()
            if (c.ball.y > GameConfig.RIM_Y) wentAbove = true
            if (prevY > GameConfig.RIM_Y && c.ball.y <= GameConfig.RIM_Y && abs(c.ball.x) < GameConfig.RIM_R) crossedDown = true
            prevY = c.ball.y
        }
        assertTrue("림 위로 올라감", wentAbove)
        assertTrue("다시 림 사이로 내려옴", crossedDown)
        assertTrue(c.passedUp)
        assertFalse("아래에서 올라간 공은 골 아님", c.made)
    }

    @Test fun `림 밖으로 떨어지면 골 아님`() {
        val c = drop(-0.9f)
        run(c)
        assertFalse(c.made)
        assertTrue(c.airball)
        assertEquals(0, c.rimHits)
    }

    @Test fun `앞 림을 맞고 안쪽으로 튕겨 들어가도 골 (깨끗하지 않음)`() {
        // 공 가운데가 앞 쇠테보다 조금 안쪽 → 쇠테에 맞고 림 안으로
        val c = drop(-GameConfig.RIM_R + 0.06f)
        val r = Rec()
        c.listener = r
        run(c)
        assertTrue("림 맞음", c.rimHits >= 1)
        assertTrue(r.rims >= 1)
        assertTrue("튕겨 들어감", c.made)
        assertFalse(c.clean)
        assertEquals(1, r.mades)
        assertFalse(r.cleanMade)
    }

    @Test fun `앞 림 바깥쪽을 맞으면 밖으로 튕겨 나감`() {
        val c = drop(-GameConfig.RIM_R - 0.06f)
        run(c)
        assertTrue(c.rimHits >= 1)
        assertFalse(c.made)
        assertFalse(c.airball)
        assertTrue("슛 쪽(왼쪽)으로 튕김", c.ball.x < -GameConfig.RIM_R)
    }

    @Test fun `림 튕김 - 반발 방향과 세기`() {
        val c = drop(-GameConfig.RIM_R - 0.06f, 3.5f)
        var vyBefore = 0f
        var bounced = false
        while (c.flying && !bounced) {
            val vy = c.ball.vy
            c.step()
            if (c.rimHits > 0) { bounced = true; vyBefore = vy }
        }
        assertTrue(bounced)
        assertTrue("위로 튕김", c.ball.vy > 0f)
        assertTrue("왼쪽으로 튕김", c.ball.vx < 0f)
        // 반발 계수 < 1 → 튕긴 빠르기가 떨어지던 빠르기보다 작다
        assertTrue(c.ball.speed < abs(vyBefore))
    }

    @Test fun `보드 맞고 들어가면 뱅크 골`() {
        // 보드 앞에서 림 쪽으로 조금 높이 → 보드에 부딪혀 림 안으로
        val c = Court()
        c.launch(0.1f, 3.6f, 2.2f, 20f)
        run(c)
        assertTrue("보드 맞음", c.boardHits >= 1)
        assertFalse(c.clean)
    }

    @Test fun `뒤 림과 보드 사이로는 공이 빠져 골이 아니다`() {
        val gapX = (GameConfig.RIM_R + GameConfig.RIM_TUBE + GameConfig.BOARD_X - GameConfig.BOARD_HALF) / 2f
        val c = drop(gapX)
        run(c)
        assertFalse(c.made)
        assertTrue("공 지름보다 틈이 넓음", GameConfig.BOARD_X - GameConfig.BOARD_HALF - GameConfig.RIM_R - GameConfig.RIM_TUBE > 2f * GameConfig.BALL_R)
    }

    // ── 결정성 ──

    @Test fun `같은 슛은 같은 결과 - 림 튕김 포함`() {
        val a = Court()
        val b = Court()
        // 앞 림에 맞는 짧은 슛
        val sp = Aim.speed(5.4f, 50f, 0.3f)
        a.launch(-5.4f, GameConfig.RELEASE_Y, sp, 50f)
        b.launch(-5.4f, GameConfig.RELEASE_Y, sp, 50f)
        val na = run(a)
        val nb = run(b)
        assertEquals(na, nb)
        assertEquals(a.ball.x, b.ball.x, 0f)
        assertEquals(a.ball.y, b.ball.y, 0f)
        assertEquals(a.rimHits, b.rimHits)
        assertEquals(a.made, b.made)
        assertTrue("림에 맞는 슛이어야 함", a.rimHits > 0)
    }

    @Test fun `프레임을 어떻게 쪼개도 같은 결과`() {
        val sp = Aim.speed(4.2f, 55f, 0.62f)
        val a = Court()
        val b = Court()
        a.launch(-4.2f, GameConfig.RELEASE_Y, sp, 55f)
        b.launch(-4.2f, GameConfig.RELEASE_Y, sp, 55f)
        var g = 0
        while (a.advance(16) && g < 1000) g++
        val sizes = longArrayOf(7, 33, 16, 50, 1, 24)
        var k = 0
        g = 0
        while (b.advance(sizes[k++ % sizes.size]) && g < 2000) g++
        assertEquals(a.steps, b.steps)
        assertEquals(a.ball.x, b.ball.x, 0f)
        assertEquals(a.ball.y, b.ball.y, 0f)
        assertEquals(a.made, b.made)
        assertEquals(a.rimHits, b.rimHits)
    }

    @Test fun `멈춘 뒤엔 advance 가 아무것도 안 함`() {
        val c = drop(0f)
        run(c)
        val x = c.ball.x
        val y = c.ball.y
        assertFalse(c.advance(100))
        assertEquals(x, c.ball.x, 0f)
        assertEquals(y, c.ball.y, 0f)
        assertFalse("날아가는 중엔 다시 못 던짐", Court().apply { launch(0f, 2f, 1f, 90f) }.launch(0f, 2f, 1f, 90f))
    }

    // ── 넣는 방법·조준 ──

    @Test fun `모든 거리에서 기본 각도 알맞은 세기로 넣을 수 있다`() {
        for (d in GameConfig.DISTANCES) {
            val c = shot(d, GameConfig.LAUNCH_BASE_DEG, GameConfig.SWEET_POWER)
            assertTrue("거리 $d 골", c.made)
            assertTrue("거리 $d 깨끗", c.clean)
            // 각도를 바꿔도 넣는 세기가 있다
            for (a in floatArrayOf(48f, 64f)) {
                var ok = false
                var p = 0.3f
                while (p <= 0.7f && !ok) { if (shot(d, a, p).made) ok = true; p += 0.01f }
                assertTrue("거리 $d 각도 $a", ok)
            }
        }
    }

    @Test fun `너무 약하거나 너무 세면 놓친다`() {
        var strongMade = 0
        for (d in GameConfig.DISTANCES) {
            assertFalse("거리 $d 약함", shot(d, GameConfig.LAUNCH_BASE_DEG, 0f).made)
            val strong = shot(d, GameConfig.LAUNCH_BASE_DEG, 1f)
            assertTrue("거리 $d 세면 보드·뒤 림에 맞음", strong.boardHits + strong.rimHits > 0)
            if (strong.made) strongMade++
        }
        // 아주 세게 던져도 보드 맞고 들어가는 운은 가끔만
        assertTrue("세게 던져 들어간 수 $strongMade", strongMade <= 2)
    }

    @Test fun `멀수록 넣는 세기 범위가 좁다`() {
        fun window(d: Float): Int {
            var n = 0
            var p = 0f
            while (p <= 1.0001f) { if (shot(d, GameConfig.LAUNCH_BASE_DEG, p).made) n++; p += 0.01f }
            return n
        }
        val near = window(GameConfig.DISTANCES.first())
        val far = window(GameConfig.DISTANCES.last())
        assertTrue("가까움 $near > 멂 $far", near > far)
    }

    @Test fun `알맞은 세기는 림 가운데 조금 뒤를 지나는 빠르기`() {
        for (d in GameConfig.DISTANCES) for (a in floatArrayOf(40f, 55f, 70f)) {
            val v = Aim.idealSpeed(d, a)
            // 장애물 없는 포물선으로 림 높이(내려올 때)를 지나는 x
            val rad = Math.toRadians(a.toDouble())
            val vx = v * Math.cos(rad)
            val vy = v * Math.sin(rad)
            val dy = (GameConfig.RIM_Y - GameConfig.RELEASE_Y).toDouble()
            val t = (vy + Math.sqrt(vy * vy - 2 * GameConfig.GRAVITY * dy)) / GameConfig.GRAVITY
            val x = -d + vx * t
            assertEquals("거리 $d 각도 $a", GameConfig.AIM_BEYOND.toDouble(), x, 0.01)
        }
    }

    @Test fun `스와이프 각도 → 발사 각도`() {
        assertEquals(GameConfig.LAUNCH_BASE_DEG, Aim.launchAngle(GameConfig.SWIPE_CENTER_DEG), 1e-4f)
        assertEquals(64f, Aim.launchAngle(90f), 1e-4f) // 똑바로 위로 밀면 더 높게
        assertEquals(46f, Aim.launchAngle(60f), 1e-4f)
        assertEquals(GameConfig.LAUNCH_MAX_DEG, Aim.launchAngle(170f), 0f)
        assertEquals(GameConfig.LAUNCH_MIN_DEG, Aim.launchAngle(-10f), 0f)
        // 위로 갈수록 높은 각도 (단조)
        var prev = -1f
        var s = -10f
        while (s <= 150f) { val a = Aim.launchAngle(s); assertTrue(a >= prev); prev = a; s += 5f }
    }

    @Test fun `세기 → 빠르기 (세기 클수록 빠름, 역계산)`() {
        for (d in GameConfig.DISTANCES) {
            val a = 55f
            var prev = 0f
            var p = 0f
            while (p <= 1f) {
                val v = Aim.speed(d, a, p)
                assertTrue(v > prev)
                assertEquals(p, Aim.powerFor(d, a, v), 1e-3f)
                prev = v
                p += 0.1f
            }
            assertEquals(Aim.idealSpeed(d, a), Aim.speed(d, a, GameConfig.SWEET_POWER), 1e-4f)
            assertEquals("0..1 밖은 자름", Aim.speed(d, a, 1f), Aim.speed(d, a, 3f), 0f)
        }
        // 먼 거리일수록 빨라야 함
        assertTrue(Aim.idealSpeed(8.2f, 55f) > Aim.idealSpeed(2.4f, 55f))
    }
}
