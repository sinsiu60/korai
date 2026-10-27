package com.siwoo.party.games.curling

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 진행(엔드·차례·합계·순위) + 물리(컬·스위핑·호그 라인·아웃) */
class CurlingGameTest {

    private val tx = GameConfig.TEE_X
    private val ty = GameConfig.TEE_Y

    /** 한 엔드를 places 자리(던지는 순서대로)에 놓아 끝냄 */
    private fun playEnd(g: CurlingGame, places: List<Pair<Float, Float>>) {
        assertEquals(g.stonesPerEnd, places.size)
        for ((x, y) in places) g.skipThrowTo(x, y)
        assertSame(Phase.SCORED, g.phase)
    }

    /** 놓은 자리 밖(호그 라인 너머·하우스 밖) */
    private val far = 28f to 2.5f

    // ── 진행 ──

    @Test
    fun `2인 3엔드 합계와 순위, 득점한 사람이 다음 엔드 먼저`() {
        val g = CurlingGame(2, firstSide = 0)
        assertArrayEquals(intArrayOf(0, 1, 0, 1, 0, 1, 0, 1), g.order)
        // 1엔드: 0번 스톤(짝수 순서)이 0.5·1.5, 1번 가장 가까운 것 2.5 → 0번 2점
        playEnd(g, listOf(tx + 0.5f to ty, tx to ty + 2.5f, tx - 1.5f to ty, far, far, far, far, far))
        assertEquals(0, g.lastResult.side)
        assertEquals(2, g.lastResult.points)
        assertTrue(g.nextEnd())
        assertEquals("득점한 0번이 먼저", 0, g.startSide)
        assertEquals(0, g.order[0])
        // 2엔드: 1번 3점
        playEnd(g, listOf(far, tx to ty + 0.3f, far, tx + 1f to ty, far, tx - 2f to ty, far, far))
        assertEquals(1, g.lastResult.side)
        assertEquals(3, g.lastResult.points)
        assertTrue(g.nextEnd())
        assertEquals(1, g.startSide)
        assertEquals(1, g.order[0])
        // 3엔드: 블랭크
        playEnd(g, List(8) { far })
        assertTrue(g.lastResult.blank)
        assertTrue(g.lastEnd)
        assertFalse(g.nextEnd())
        assertSame(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(2, 3), g.sideTotals)
        assertArrayEquals(intArrayOf(2, 0), g.endScores[0])
        assertArrayEquals(intArrayOf(0, 3), g.endScores[1])
        assertArrayEquals(intArrayOf(0, 0), g.endScores[2])
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
        assertEquals(24, g.throwsMade)
    }

    @Test
    fun `3인 3엔드 동점 순위`() {
        val g = CurlingGame(3, firstSide = 1)
        assertArrayEquals(intArrayOf(1, 2, 0, 1, 2, 0, 1, 2, 0), g.order)
        // 1엔드: 2번이 가장 가까움, 다음은 0번 → 2번 1점
        playEnd(g, listOf(far, tx to ty + 0.2f, tx + 1f to ty, far, far, far, far, far, far))
        assertEquals(2, g.lastResult.side)
        assertEquals(1, g.lastResult.points)
        g.nextEnd()
        assertEquals(2, g.startSide)
        assertArrayEquals(intArrayOf(2, 0, 1, 2, 0, 1, 2, 0, 1), g.order)
        // 2엔드: 0번이 하우스에 혼자 → 0번 1점
        playEnd(g, listOf(far, tx to ty - 3f, far, far, far, far, far, far, far))
        assertEquals(0, g.lastResult.side)
        g.nextEnd()
        // 3엔드: 블랭크
        playEnd(g, List(9) { far })
        g.nextEnd()
        assertSame(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(1, 0, 1), g.sideTotals)
        assertArrayEquals(intArrayOf(1, 3, 1), g.ranks())
    }

    @Test
    fun `4인 팀전 팀원 스톤을 함께 세고 팀원이 같은 순위`() {
        val g = CurlingGame(4, firstSide = 0)
        assertTrue(g.teamMode)
        assertEquals(2, g.sides)
        // 순서 0 1 0 1 2 3 2 3 → 0팀 스톤 = 순서 0,2,4,6
        playEnd(g, listOf(tx to ty + 1f, tx to ty + 3f, far, far, tx - 2f to ty, far, far, far))
        assertEquals(0, g.lastResult.side)
        assertEquals("0번 + 2번 스톤 = 2점", 2, g.lastResult.points)
        assertEquals(0, g.stones[g.lastResult.counting[0]].owner)
        assertEquals(2, g.stones[g.lastResult.counting[1]].owner)
        g.nextEnd()
        assertArrayEquals("득점한 0팀이 먼저, 팀 안 순서 바뀜", intArrayOf(2, 3, 2, 3, 0, 1, 0, 1), g.order)
        // 2엔드: 1팀(3번·1번) 3점
        playEnd(g, listOf(far, tx to ty, far, tx + 1f to ty, far, tx - 1.5f to ty, far, far))
        assertEquals(1, g.lastResult.side)
        assertEquals(3, g.lastResult.points)
        g.nextEnd()
        playEnd(g, List(8) { far })
        g.nextEnd()
        assertArrayEquals(intArrayOf(2, 3), g.sideTotals)
        assertEquals(3, g.total(1))
        assertEquals(3, g.total(3))
        assertArrayEquals(intArrayOf(2, 1, 2, 1), g.ranks())
    }

    @Test
    fun `남은 스톤 수와 해머`() {
        val g = CurlingGame(2, firstSide = 1)
        assertEquals(4, g.remaining(0))
        assertEquals(4, g.remaining(1))
        assertEquals(1, g.current)
        assertEquals(0, g.hammerSide)
        g.skipThrowTo(tx, ty)
        assertEquals(3, g.remaining(1))
        assertEquals(0, g.current)
        assertEquals(4, g.remainingForSide(0))
    }

    // ── 물리 ──

    /** 고정 단계로 끝까지 (tap = 몇 단계마다 스위핑, 0 이면 안 함). 결과: 멈춘 스톤 */
    private fun sim(power: Float, curl: Int, tapEvery: Int = 0, crossX: Float = 32f, out: FloatArray? = null): CurlStone {
        val g = CurlingGame(2, fadeMs = 0L)
        g.setCurl(curl)
        assertTrue(g.throwStone(1f, 0f, power))
        val s = g.currentStone
        var step = 0
        var crossed = false
        while (!g.sheet.settled && step < 3000) {
            if (tapEvery > 0 && step % tapEvery == 0) g.sweep()
            g.sheet.step()
            step++
            if (!crossed && s.x >= crossX) {
                crossed = true
                out?.set(0, s.y - GameConfig.HACK_Y)
            }
        }
        assertTrue("멈춤", g.sheet.settled)
        return s
    }

    @Test
    fun `힘 0점5 쯤이면 하우스까지 가고 휘어진다`() {
        val s = sim(0.5f, 1)
        assertTrue("하우스 안 (x=${s.x}, y=${s.y})", CurlingRules.inHouse(CurlingRules.distance(s.x, s.y)))
        assertTrue("옆으로 1 단위 넘게 휨 (${s.y - ty})", s.y - ty > 1f)
    }

    @Test
    fun `컬 방향 부호 - 오른쪽은 화면 아래, 왼쪽은 화면 위`() {
        val right = sim(0.5f, 1)
        val left = sim(0.5f, -1)
        assertTrue("오른쪽 컬 y=${right.y}", right.y > ty + 0.5f)
        assertTrue("왼쪽 컬 y=${left.y}", left.y < ty - 0.5f)
        assertEquals("좌우 대칭", right.y - ty, ty - left.y, 1e-3f)
        assertEquals(right.x, left.x, 1e-3f)
    }

    @Test
    fun `스위핑하면 더 멀리 가고 덜 휜다`() {
        val a = FloatArray(1)
        val b = FloatArray(1)
        val plain = sim(0.45f, 1, 0, 32f, a)
        val swept = sim(0.45f, 1, 6, 32f, b)
        assertTrue("더 멀리 (${plain.x} → ${swept.x})", swept.x > plain.x + 1f)
        assertTrue("같은 지점(x=32)에서 덜 휨 (${a[0]} → ${b[0]})", b[0] < a[0] * 0.8f)
        assertTrue(a[0] > 0f)
        // 결정적: 같은 입력이면 같은 결과
        val again = sim(0.45f, 1, 6)
        assertEquals(swept.x, again.x, 0f)
        assertEquals(swept.y, again.y, 0f)
    }

    @Test
    fun `스위핑 세기는 연타로 오르고 시간이 지나면 줄어든다, 멈춘 스톤은 못 쓴다`() {
        val g = CurlingGame(2)
        assertFalse("던지기 전엔 안 됨", g.sweep())
        g.throwStone(1f, 0f, 0.5f)
        repeat(3) { assertTrue(g.sweep()) }
        assertEquals(3 * GameConfig.SWEEP_PER_TAP, g.sheet.sweep, 1e-4f)
        repeat(10) { g.sweep() }
        assertEquals("최대 1", 1f, g.sheet.sweep, 0f)
        repeat(30) { g.sheet.step() }
        assertEquals(1f - 30 * GameConfig.SWEEP_DECAY * GameConfig.STEP_SEC, g.sheet.sweep, 1e-3f)
        while (g.phase == Phase.MOVING) g.advance(16)
        assertFalse("멈춘 뒤엔 안 됨", g.sweep())
        assertEquals("다음 던지기 전 처음으로", 0f, g.sheet.sweep, 0f)
    }

    @Test
    fun `호그 라인을 못 넘으면 빠지고 다음 차례`() {
        val g = CurlingGame(2, fadeMs = 0L)
        var hog = 0
        g.listener = object : CurlingGame.Listener {
            override fun onOut(stone: CurlStone, hog2: Boolean) { if (hog2) hog++ }
        }
        g.throwStone(1f, 0f, 0f)
        var n = 0
        while (g.advance(16) && n < 2000) n++
        assertEquals(1, hog)
        assertSame(Phase.AIM, g.phase)
        assertEquals(1, g.current)
        assertSame(StoneState.GONE, g.stones[0].state)
    }

    @Test
    fun `세게 던지면 백 라인 뒤로 아웃`() {
        val g = CurlingGame(2, fadeMs = 0L)
        var outs = 0
        g.listener = object : CurlingGame.Listener {
            override fun onOut(stone: CurlStone, hog: Boolean) { if (!hog) outs++ }
        }
        g.throwStone(1f, 0f, 1f)
        var n = 0
        while (g.advance(16) && n < 3000) n++
        assertEquals(1, outs)
        assertSame(StoneState.GONE, g.stones[0].state)
        assertSame(Phase.AIM, g.phase)
    }

    @Test
    fun `테이크아웃 - 던진 스톤이 하우스의 스톤을 쳐내고 부딪힌 뒤엔 휘지 않는다`() {
        val g = CurlingGame(2, fadeMs = 0L)
        var hits = 0
        g.listener = object : CurlingGame.Listener {
            override fun onCollide(x: Float, y: Float, speed: Float) { hits++ }
        }
        g.skipThrowTo(tx, ty) // 0번 스톤 티 위
        g.setCurl(1)
        g.throwStone(1f, 0f, 0.95f) // 1번이 세게, 곧게 (휘어 맞도록 거의 정면)
        var n = 0
        while (g.advance(16) && n < 3000) n++
        assertTrue("부딪힘", hits > 0)
        assertTrue(g.stones[1].hit)
        assertTrue("티 위 스톤이 밀려남", g.stones[0].state != StoneState.IN_PLAY || abs(g.stones[0].x - tx) > 1f)
    }

    @Test
    fun `조준 각도는 제한되고 힘은 빠르기로`() {
        val max = Math.toRadians(GameConfig.MAX_AIM_DEG.toDouble()).toFloat()
        assertEquals(0f, CurlingGame.aimAngle(1f, 0f), 0f)
        assertEquals(max, CurlingGame.aimAngle(1f, 1f), 1e-5f)
        assertEquals(-max, CurlingGame.aimAngle(1f, -1f), 1e-5f)
        assertEquals("뒤쪽은 가까운 끝", max, CurlingGame.aimAngle(-1f, 0.1f), 1e-5f)
        assertEquals(GameConfig.MIN_SPEED, CurlingGame.launchSpeed(0f), 0f)
        assertEquals(GameConfig.MAX_SPEED, CurlingGame.launchSpeed(2f), 0f)
        val g = CurlingGame(2)
        g.throwStone(1f, 1f, 0.5f)
        val s = g.currentStone
        assertEquals(max, kotlin.math.atan2(s.vy, s.vx), 1e-4f)
        assertFalse("움직이는 중엔 컬 못 바꿈", g.setCurl(-1))
        assertFalse("움직이는 중엔 못 던짐", g.throwStone(1f, 0f, 1f))
    }

    @Test
    fun `물리는 움직일 때만 돌고 멈추면 advance 가 false`() {
        val g = CurlingGame(2)
        assertFalse(g.advance(16))
        val st = g.sheet.steps
        g.throwStone(1f, 0f, 0.5f)
        var n = 0
        while (g.advance(16)) n++
        assertTrue(n > 60)
        val st2 = g.sheet.steps
        assertTrue(st2 > st)
        assertFalse(g.advance(1000))
        assertEquals("멈춘 뒤엔 단계가 늘지 않음", st2, g.sheet.steps)
    }

    @Test
    fun `엔드가 끝나면 결과 기록, 다음 엔드에 새 스톤`() {
        val g = CurlingGame(2)
        var scored: EndResult? = null
        g.listener = object : CurlingGame.Listener {
            override fun onEndScored(end: Int, result: EndResult) { scored = result }
        }
        repeat(8) { g.skipThrowTo(far.first, far.second) }
        assertNotNull(scored)
        assertNotNull(g.results[0])
        val old = g.stones
        g.nextEnd()
        assertEquals(1, g.end)
        assertTrue(old !== g.stones)
        assertTrue(g.stones.all { it.state == StoneState.WAITING })
        assertEquals(8, g.sheet.world.bodies.size)
    }
}
