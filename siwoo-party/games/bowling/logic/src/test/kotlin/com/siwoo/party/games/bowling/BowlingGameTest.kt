package com.siwoo.party.games.bowling

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 게임 진행(사람·프레임·투구 차례, 핀 다시 세우기, 순위)과 레인 물리(쓰러짐 판정·거터·멈춤) */
class BowlingGameTest {

    /** 물리 없이 한 번 굴리고 다음 차례로 */
    private fun roll(g: BowlingGame, n: Int): RollOutcome {
        assertTrue(g.forceRoll(n))
        assertSame(Phase.SETTLED, g.phase)
        val o = g.outcome
        g.nextTurn()
        return o
    }

    /** 물리로 굴려 끝까지 */
    private fun simulate(l: Lane, maxFrames: Int = 3000): Int {
        var k = 0
        while (l.advance(16) && k < maxFrames) k++
        return k
    }

    @Test
    fun `한 사람이 한 프레임을 다 굴리면 다음 사람`() {
        val g = BowlingGame(3, 10)
        assertEquals(0, g.current)
        var o = roll(g, 6)
        assertFalse(o.frameEnded)
        assertEquals("2구째도 같은 사람", 0, g.current)
        assertEquals("남은 핀 그대로", 4, g.lane.standingCount)
        o = roll(g, 3)
        assertTrue(o.frameEnded)
        assertEquals(1, g.current)
        assertEquals("다음 사람은 새로 세운 10개", 10, g.lane.standingCount)
        o = roll(g, 10)
        assertSame(RollMark.STRIKE, o.mark)
        assertTrue("스트라이크 → 바로 다음 사람", o.frameEnded)
        assertEquals(2, g.current)
        roll(g, 0); roll(g, 0)
        assertEquals("한 바퀴 돌아 처음 사람의 2프레임", 0, g.current)
        assertEquals(1, g.card.frame)
        assertEquals(9, g.total(0))
        assertEquals("스트라이크 보너스 아직", 0, g.total(1))
    }

    @Test
    fun `투구 결과 종류`() {
        val g = BowlingGame(2, 10)
        assertSame(RollMark.PINS, roll(g, 7).mark)
        assertSame(RollMark.SPARE, roll(g, 3).mark)
        assertSame(RollMark.MISS, roll(g, 0).mark)
        assertSame("0 뒤 10개도 스페어", RollMark.SPARE, roll(g, 10).mark)
        assertSame(RollMark.STRIKE, roll(g, 10).mark)
    }

    @Test
    fun `마지막 프레임 보너스 투구엔 핀을 새로 세움`() {
        val g = BowlingGame(2, 2)
        roll(g, 0); roll(g, 0) // 0번 1프레임
        roll(g, 0); roll(g, 0) // 1번 1프레임
        // 0번 마지막 프레임: X → 다시 10개 → 7 → 남은 3개 → / 스페어
        var o = roll(g, 10)
        assertSame(RollMark.STRIKE, o.mark)
        assertFalse("마지막 프레임 스트라이크는 보너스", o.frameEnded)
        assertEquals(0, g.current)
        assertEquals(10, g.lane.standingCount)
        o = roll(g, 7)
        assertEquals(3, g.lane.standingCount)
        o = roll(g, 3)
        assertSame(RollMark.SPARE, o.mark)
        assertTrue(o.frameEnded)
        assertEquals(1, g.current)
        assertEquals(20, g.total(0))
        // 1번 마지막 프레임: 9 / → 보너스 X
        roll(g, 9)
        o = roll(g, 1)
        assertSame(RollMark.SPARE, o.mark)
        assertEquals("스페어 뒤 다시 10개", 10, g.lane.standingCount)
        assertSame(Phase.AIM, g.phase)
        o = roll(g, 10)
        assertSame(RollMark.STRIKE, o.mark)
        assertSame("모두 끝", Phase.OVER, g.phase)
        assertEquals(20, g.total(1))
        assertArrayEquals("동점은 같은 순위", intArrayOf(1, 1), g.ranks())
    }

    @Test
    fun `점수 순 순위`() {
        val g = BowlingGame(4, 1)
        roll(g, 3); roll(g, 4)      // 7
        roll(g, 10); roll(g, 2); roll(g, 3) // 15
        roll(g, 9); roll(g, 1); roll(g, 5)  // 15
        roll(g, 0); roll(g, 0)      // 0
        assertSame(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(3, 1, 1, 4), g.ranks())
        assertEquals(10, g.throws)
    }

    @Test
    fun `5프레임 2인 게임 끝까지`() {
        val g = BowlingGame(2, 5)
        var guard = 0
        while (g.phase != Phase.OVER && guard < 100) {
            if (g.current == 0) roll(g, 10) else { roll(g, 4); if (g.phase == Phase.AIM && g.current == 1) roll(g, 5) }
            guard++
        }
        assertSame(Phase.OVER, g.phase)
        assertEquals(150, g.total(0))
        assertEquals(45, g.total(1))
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
        assertFalse("끝난 뒤엔 못 굴림", g.forceRoll(3))
    }

    @Test
    fun `스와이프 값 변환`() {
        assertEquals(0f, Aim.angleDeg(0f), 0f)
        assertEquals(4f, Aim.angleDeg(10f), 1e-4f)
        assertEquals("각도 제한", GameConfig.MAX_ANGLE_DEG, Aim.angleDeg(60f), 0f)
        assertEquals(-GameConfig.MAX_ANGLE_DEG, Aim.angleDeg(-60f), 0f)
        assertEquals(GameConfig.MIN_SPEED, Aim.speed(0f), 0f)
        assertEquals(GameConfig.MAX_SPEED, Aim.speed(1f), 0f)
        assertEquals(GameConfig.MAX_SPEED, Aim.speed(3f), 0f)
        assertEquals(1f, Aim.hook(2f), 0f)
        assertEquals(-0.5f, Aim.hook(-0.5f), 0f)
    }

    // ── 레인 물리 ──

    @Test
    fun `핀 쓰러짐 판정 기준`() {
        assertFalse(Lane.displaced(0.2f, 0.2f))
        assertFalse(Lane.displaced(GameConfig.KNOCK_DIST, 0f))
        assertTrue(Lane.displaced(GameConfig.KNOCK_DIST + 0.01f, 0f))
        assertTrue(Lane.displaced(0.3f, 0.3f))
        assertFalse(Lane.knocks(GameConfig.KNOCK_SPEED - 0.01f))
        assertTrue(Lane.knocks(GameConfig.KNOCK_SPEED))
    }

    @Test
    fun `살짝 밀린 핀은 서 있고 많이 밀린 핀은 쓰러짐`() {
        val l = Lane()
        val p = l.pins[6]
        // 아주 느리게 밀림 (부딪힘 없음): 기준보다 짧게 미끄러짐 → 서 있음
        l.launch(5f, 0f, 10f, 0f)
        l.ball.active = false // 공은 빼고 핀만
        p.setVelocity(0f, 0.9f)
        simulate(l)
        assertTrue("조금 밀림 → 서 있음", p.standing)
        assertTrue(p.y > p.spotY)
        assertEquals(0, l.knockedThisRoll)
        // 많이 밀림 → 쓰러짐
        l.launch(5f, 0f, 10f, 0f)
        l.ball.active = false
        p.setVelocity(0f, 2.6f)
        simulate(l)
        assertSame(PinState.FALLEN, p.state)
        assertEquals(1, l.knockedThisRoll)
        l.removeFallen()
        assertSame(PinState.REMOVED, p.state)
        assertFalse(p.active)
        assertEquals(9, l.standingCount)
    }

    @Test
    fun `거터 판정`() {
        assertFalse(Lane.isOffLane(0f))
        assertFalse(Lane.isOffLane(GameConfig.LANE_W))
        assertTrue(Lane.isOffLane(-0.01f))
        assertTrue(Lane.isOffLane(GameConfig.LANE_W + 0.01f))
        // 가장자리에서 바깥으로 비스듬히 → 거터 볼, 핀은 하나도 안 쓰러짐
        val l = Lane()
        l.launch(GameConfig.BALL_R, -GameConfig.MAX_ANGLE_DEG, 15f, 0f)
        simulate(l)
        assertFalse(l.rolling)
        assertTrue(l.ball.inGutter)
        assertTrue(l.gutterBall)
        assertEquals(0, l.knockedThisRoll)
        assertEquals(10, l.standingCount)
        // 훅이 너무 세서 아래 거터로
        val h = Lane()
        h.launch(GameConfig.LANE_W - 1.6f, 0f, GameConfig.MIN_SPEED, 1f)
        simulate(h)
        assertTrue(h.gutterBall)
        assertEquals(0, h.knockedThisRoll)
    }

    @Test
    fun `포켓에 맞으면 스트라이크, 루프는 멈춤`() {
        val l = Lane()
        assertTrue(l.launch(5.6f, 0f, 16f, 0f))
        assertTrue(l.rolling)
        assertFalse("굴리는 중엔 또 못 굴림", l.launch(5f, 0f, 10f, 0f))
        val frames = simulate(l)
        assertTrue("시간 안에 멈춤 ($frames)", frames < 3000)
        assertFalse(l.rolling)
        assertFalse("다 멈춘 뒤엔 더 진행하지 않음", l.advance(16))
        assertEquals(10, l.knockedThisRoll)
        assertFalse(l.gutterBall)
        assertEquals(0, l.standingCount)
        for (b in l.world.bodies) assertFalse("모두 정지", b.active && b.moving)
    }

    @Test
    fun `훅은 굴러갈수록 휜다`() {
        val a = Lane()
        val b = Lane()
        a.launch(5f, 0f, 14f, 0f)
        b.launch(5f, 0f, 14f, 1f)
        // 앞쪽(1/4)에선 거의 차이 없고, 핀 앞에선 크게 벌어짐
        while (a.ball.x < GameConfig.HEAD_X / 4f) { a.step(); b.step() }
        val early = b.ball.y - a.ball.y
        while (a.ball.x < GameConfig.HEAD_X - 2f) { a.step(); b.step() }
        val late = b.ball.y - a.ball.y
        assertTrue("아래로 휨 ($late)", late > 1f)
        assertTrue("처음엔 조금 ($early)", early < late * 0.1f)
        // 반대 훅은 위로
        val c = Lane()
        c.launch(5f, 0f, 14f, -1f)
        while (c.ball.x < GameConfig.HEAD_X - 2f) c.step()
        assertTrue(c.ball.y < 5f - 1f)
    }

    @Test
    fun `같은 입력이면 프레임을 어떻게 나눠도 같은 결과`() {
        val a = Lane()
        val b = Lane()
        a.launch(4.3f, 1.5f, 17f, -0.4f)
        b.launch(4.3f, 1.5f, 17f, -0.4f)
        while (a.advance(16)) Unit
        var k = 0
        while (b.advance(if (k++ % 2 == 0) 7L else 33L)) Unit
        assertEquals(a.knockedThisRoll, b.knockedThisRoll)
        for (i in 0 until GameConfig.PINS) {
            assertEquals(a.pins[i].x, b.pins[i].x, 0f)
            assertEquals(a.pins[i].state, b.pins[i].state)
        }
    }

    @Test
    fun `굴린 결과가 점수표로`() {
        val g = BowlingGame(2, 10)
        assertTrue(g.throwBall(5.6f, 0f, (16f - GameConfig.MIN_SPEED) / (GameConfig.MAX_SPEED - GameConfig.MIN_SPEED), 0f))
        assertSame(Phase.ROLLING, g.phase)
        assertFalse("굴리는 중엔 또 못 굴림", g.throwBall(5f, 0f, 1f, 0f))
        while (g.advance(16)) Unit
        assertSame(Phase.SETTLED, g.phase)
        assertSame(RollMark.STRIKE, g.outcome.mark)
        assertEquals(10, g.outcome.knocked)
        assertEquals(1, g.cards[0].rollCount)
        assertTrue(g.nextTurn())
        assertEquals(1, g.current)
        // 거터 볼 → GUTTER
        g.launch(GameConfig.BALL_R, -GameConfig.MAX_ANGLE_DEG, 15f, 0f)
        while (g.advance(16)) Unit
        assertSame(RollMark.GUTTER, g.outcome.mark)
        assertFalse(g.nextTurn())
        assertEquals("거터 뒤 2구째", 1, g.card.rollInFrame)
        assertEquals(10, g.lane.standingCount)
    }
}
