package com.siwoo.party.games.minigolf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** 미니 골프 규칙: 물 벌타, 최대 타수, 컵 빠르기, 홀인원, 모래·경사, 차례, 합계·순위, 물리 정지 */
class MinigolfGameTest {

    private fun roll(g: MinigolfGame) {
        var guard = 0
        while (g.phase == Phase.ROLLING && guard++ < 5000) g.step()
        assertTrue("공이 멈춤", g.phase != Phase.ROLLING)
    }

    private fun roll(s: CourseSim) {
        var guard = 0
        while (s.rolling && guard++ < 5000) s.step()
        assertFalse(s.rolling)
    }

    /** 넓은 빈 시험 코스 (구역 하나) */
    private fun flat(vararg areas: Area) = HoleDef(1, "시험", 3, floatArrayOf(1f, 1f, 31f, 1f, 31f, 17f, 1f, 17f), 3f, 9f, 29f, 3f, areas = areas.toList())

    /** p 의 홀 하나를 강제로 끝내기 (벽 쪽으로 약하게 6번) */
    private fun burn(g: MinigolfGame) {
        val p = g.current
        while (g.phase == Phase.AIM && g.current == p) {
            assertTrue(g.shoot(-1f, 0f, 0.1f))
            roll(g)
        }
    }

    // ── 물 ──

    @Test
    fun `물에 빠지면 벌타 1타 더하고 친 자리로 돌아간다`() {
        val g = MinigolfGame(2, listOf(Course.HOLES[3])) // 4번 연못
        var water = 0
        var wx = 0f
        g.listener = object : MinigolfGame.Listener {
            override fun onWater(x: Float, y: Float, turnOver: Boolean) {
                water++
                wx = x
                assertFalse(turnOver)
            }
        }
        // 먼저 조금 앞으로 (친 자리가 티가 아니게)
        assertTrue(g.shoot(1f, 0f, 0.3f))
        roll(g)
        assertSame(Phase.AIM, g.phase)
        assertEquals(1, g.strokes)
        val bx = g.sim.ball.x
        val by = g.sim.ball.y
        assertTrue("앞으로 굴렀다", bx > Course.HOLES[3].teeX + 1f)
        // 연못 한가운데로
        assertTrue(g.shoot(1f, 0f, 0.6f))
        roll(g)
        assertEquals("물 한 번", 1, water)
        assertTrue("연못에서 빠짐 $wx", wx > 11f && wx < 21f)
        assertEquals("친 1타 + 벌타 1 → 3타", 3, g.strokes)
        assertEquals(1, g.penalties)
        assertSame(Phase.AIM, g.phase)
        assertEquals("친 자리로", bx, g.sim.ball.x, 1e-4f)
        assertEquals(by, g.sim.ball.y, 1e-4f)
        assertTrue(g.sim.ball.active)
        assertFalse(g.sim.ball.moving)
    }

    @Test
    fun `물 벌타로 최대 타수에 닿으면 넣지 못하고 최대 타수로 끝`() {
        val g = MinigolfGame(2, listOf(Course.HOLES[3]))
        var endedByWater = false
        g.listener = object : MinigolfGame.Listener {
            override fun onWater(x: Float, y: Float, turnOver: Boolean) { endedByWater = turnOver }
        }
        repeat(2) { assertTrue(g.shoot(1f, 0f, 0.6f)); roll(g) } // 물 두 번 = 4타
        assertEquals(4, g.strokes)
        assertTrue(g.shoot(-1f, 0f, 0.05f)); roll(g) // 5타
        assertTrue(g.shoot(1f, 0f, 0.6f)); roll(g) // 6타 + 벌타 = 7 → 6
        assertTrue(endedByWater)
        assertSame(Phase.TURN_END, g.phase)
        assertEquals(GameConfig.MAX_STROKES, g.scores[0][0])
        assertFalse(g.lastResult!!.holed)
    }

    // ── 최대 타수 ──

    @Test
    fun `6타 안에 못 넣으면 6타로 기록하고 다음 사람 차례`() {
        val g = MinigolfGame(3, listOf(Course.HOLES[0]))
        var stops = 0
        var endedAt = -1
        g.listener = object : MinigolfGame.Listener {
            override fun onStopped(turnOver: Boolean) {
                stops++
                if (turnOver) endedAt = stops
            }
        }
        val first = g.current
        for (k in 1..GameConfig.MAX_STROKES) {
            assertSame(Phase.AIM, g.phase)
            assertTrue(g.shoot(0f, -1f, 0.08f)) // 위 벽 쪽으로 살짝
            roll(g)
            assertEquals(k, minOf(g.strokes, GameConfig.MAX_STROKES))
        }
        assertEquals("6번째에 끝남", GameConfig.MAX_STROKES, endedAt)
        assertSame(Phase.TURN_END, g.phase)
        assertFalse("더는 못 침", g.shoot(1f, 0f, 1f))
        assertEquals(GameConfig.MAX_STROKES, g.scores[first][0])
        assertFalse(g.lastResult!!.holed)
        assertFalse(g.lastResult!!.holeInOne)
        assertSame(Phase.AIM, g.nextTurn())
        assertNotEquals(first, g.current)
        assertEquals("새 사람은 0타", 0, g.strokes)
        assertEquals(Course.HOLES[0].teeX, g.sim.ball.x, 0f)
    }

    // ── 컵 ──

    @Test
    fun `빠른 공은 컵 위를 넘어가고 느린 공은 들어간다`() {
        val h = Course.HOLES[0]
        // 빠르게: 컵 위를 지나는 동안 들어가지 않음
        val fast = CourseSim(h)
        fast.placeBall(h.cupX - 2f, h.cupY)
        fast.shoot(GameConfig.CUP_CAPTURE_SPEED * 2.5f, 0f)
        var passed = false
        var guard = 0
        while (fast.rolling && guard++ < 600) {
            fast.step()
            if (fast.ball.x > h.cupX + GameConfig.CUP_R + 0.1f) { passed = true; break }
        }
        assertTrue("컵을 지나감", passed)
        assertTrue("아직 공이 코스 위", fast.ball.active)
        assertNotEquals(Outcome.CUP, fast.outcome)
        // 느리게: 들어감
        val slow = CourseSim(h)
        slow.placeBall(h.cupX - 1.5f, h.cupY)
        slow.shoot(GameConfig.CUP_CAPTURE_SPEED * 0.6f, 0f)
        roll(slow)
        assertSame(Outcome.CUP, slow.outcome)
        assertFalse("컵에 들어간 공은 코스에서 빠짐", slow.ball.active)
        // 기준 빠르기 바로 위·아래 (Hole 판정)
        slow.ball.active = true
        slow.ball.place(h.cupX, h.cupY)
        slow.ball.setVelocity(GameConfig.CUP_CAPTURE_SPEED * 1.05f, 0f)
        assertFalse(slow.cup.captures(slow.ball))
        slow.ball.setVelocity(GameConfig.CUP_CAPTURE_SPEED * 0.95f, 0f)
        assertTrue(slow.cup.captures(slow.ball))
    }

    @Test
    fun `컵을 빠르게 지나면 가장자리에 걸려 방향이 조금 꺾인다`() {
        val h = Course.HOLES[0]
        val s = CourseSim(h)
        var lips = 0
        s.listener = object : CourseSim.Listener {
            override fun onLip(x: Float, y: Float) { lips++ }
        }
        s.placeBall(h.cupX - 3f, h.cupY + 0.3f)
        s.shoot(20f, 0f)
        repeat(12) { s.step() }
        assertEquals(1, lips)
        assertTrue("꺾여서 세로 속도가 생김", abs(s.ball.vy) > 0.5f)
    }

    // ── 홀인원 ──

    @Test
    fun `한 번에 넣으면 홀인원 · 두 번이면 아님`() {
        val h = Course.HOLES[0]
        val dist = h.cupX - h.teeX
        // 곧은 길: 알맞은 힘을 찾는다
        var power = -1f
        for (k in 1..200) {
            val p = k / 200f
            val s = CourseSim(h)
            s.shoot(CourseSim.speedFor(p), 0f)
            roll(s)
            if (s.outcome == Outcome.CUP) { power = p; break }
        }
        assertTrue("곧은 길 $dist 단위를 한 번에 넣는 힘이 있다", power > 0f)
        val g = MinigolfGame(2, listOf(h))
        var holed: TurnResult? = null
        g.listener = object : MinigolfGame.Listener {
            override fun onHoled(result: TurnResult) { holed = result }
        }
        assertTrue(g.shoot(1f, 0f, power))
        roll(g)
        assertTrue(holed!!.holed)
        assertTrue("홀인원", holed!!.holeInOne)
        assertEquals(1, g.scores[0][0])
        assertEquals("홀인원", MinigolfGame.scoreName(1, h.par, true))
        // 다음 사람: 약하게 한 번 + 알맞게 한 번 → 2타 (홀인원 아님)
        assertSame(Phase.AIM, g.nextTurn())
        assertEquals(1, g.current)
        assertTrue(g.shoot(1f, 0f, 0.35f))
        roll(g)
        assertSame(Phase.AIM, g.phase)
        val left = g.hole.cupX - g.sim.ball.x
        var p2 = -1f
        for (k in 1..200) {
            val p = k / 200f
            val s = CourseSim(h)
            s.placeBall(g.sim.ball.x, g.sim.ball.y)
            s.shoot(CourseSim.speedFor(p), 0f)
            roll(s)
            if (s.outcome == Outcome.CUP) { p2 = p; break }
        }
        assertTrue("남은 $left 단위", p2 > 0f)
        assertTrue(g.shoot(1f, 0f, p2))
        roll(g)
        assertSame(Phase.TURN_END, g.phase)
        assertEquals(2, holed!!.score)
        assertFalse(holed!!.holeInOne)
        assertEquals("파", MinigolfGame.scoreName(2, 2, true))
        assertEquals("버디", MinigolfGame.scoreName(2, 3, true))
        assertEquals("보기", MinigolfGame.scoreName(4, 3, true))
    }

    // ── 모래·경사 ──

    @Test
    fun `모래에서는 잔디보다 덜 굴러간다`() {
        val grass = CourseSim(flat())
        val sand = CourseSim(flat(Area(AreaKind.SAND, Box(1f, 1f, 31f, 17f))))
        for (s in listOf(grass, sand)) {
            s.placeBall(3f, 9f)
            s.shoot(14f, 0f)
            roll(s)
            assertSame(Outcome.STOPPED, s.outcome)
        }
        val g = grass.ball.x - 3f
        val d = sand.ball.x - 3f
        assertTrue("잔디 $g > 모래 $d", g > d * 2f)
        assertTrue(d > 0f)
        assertSame(AreaKind.SAND, sand.areaUnderBall())
    }

    @Test
    fun `경사는 공을 기울어진 쪽으로 민다`() {
        val flatSim = CourseSim(flat())
        val slopeSim = CourseSim(flat(Area(AreaKind.SLOPE, Box(1f, 1f, 31f, 17f), ay = GameConfig.SLOPE)))
        for (s in listOf(flatSim, slopeSim)) {
            s.placeBall(3f, 5f)
            s.shoot(15f, 0f)
            roll(s)
        }
        assertEquals("평지는 곧게", 5f, flatSim.ball.y, 0.01f)
        assertTrue("경사는 아래로 휨 (${slopeSim.ball.y})", slopeSim.ball.y > 6.5f)
        // 내리막은 더 멀리, 오르막은 덜
        val down = CourseSim(flat(Area(AreaKind.SLOPE, Box(1f, 1f, 31f, 17f), ax = GameConfig.SLOPE)))
        val up = CourseSim(flat(Area(AreaKind.SLOPE, Box(1f, 1f, 31f, 17f), ax = -GameConfig.SLOPE)))
        for (s in listOf(down, up)) {
            s.placeBall(3f, 9f)
            s.shoot(10f, 0f)
            roll(s)
        }
        assertTrue("내리막 ${down.ball.x} > 오르막 ${up.ball.x}", down.ball.x > up.ball.x + 2f)
        // 멈춘 공은 경사에서도 그대로
        assertFalse(slopeSim.advance(1000))
        val y = slopeSim.ball.y
        slopeSim.advance(1000)
        assertEquals(y, slopeSim.ball.y, 0f)
    }

    // ── 차례 ──

    @Test
    fun `한 사람이 홀을 끝까지 친 뒤 다음 사람 · 홀마다 시작하는 사람이 돈다`() {
        val g = MinigolfGame(3, Course.HOLES.take(3))
        val seen = ArrayList<IntArray>()
        for (h in 0 until 3) {
            assertEquals(h, g.holeIndex)
            val order = ArrayList<Int>()
            while (true) {
                val p = g.current
                order.add(p)
                assertEquals("이 홀 아직 기록 없음", -1, g.scores[p][h])
                burn(g)
                assertSame(Phase.TURN_END, g.phase)
                assertEquals("홀을 끝낸 사람 = 친 사람", p, g.lastResult!!.player)
                assertEquals(GameConfig.MAX_STROKES, g.scores[p][h])
                if (g.nextTurn() == Phase.HOLE_END) break
            }
            seen.add(order.toIntArray())
            assertArrayEquals(g.order(h), order.toIntArray())
            val next = g.nextHole()
            assertSame(if (h == 2) Phase.OVER else Phase.AIM, next)
        }
        assertArrayEquals(intArrayOf(0, 1, 2), seen[0])
        assertArrayEquals(intArrayOf(1, 2, 0), seen[1])
        assertArrayEquals(intArrayOf(2, 0, 1), seen[2])
        assertFalse(g.shoot(1f, 0f, 1f))
    }

    // ── 합계·순위 ──

    @Test
    fun `타수 합이 적을수록 위 · 같으면 같은 순위`() {
        val g = MinigolfGame(4, Course.HOLES.take(2))
        val s = arrayOf(intArrayOf(3, 6), intArrayOf(2, 5), intArrayOf(4, 4), intArrayOf(6, 6))
        for (p in 0 until 4) for (h in 0 until 2) g.scores[p][h] = s[p][h]
        assertEquals(9, g.total(0))
        assertEquals(7, g.total(1))
        assertEquals(8, g.total(2))
        assertEquals(12, g.total(3))
        assertArrayEquals(intArrayOf(3, 1, 2, 4), g.ranks())
        assertEquals(listOf(1), g.winners())
        g.scores[2][1] = 3 // 7 동점
        assertArrayEquals(intArrayOf(3, 1, 1, 4), g.ranks())
        assertEquals(listOf(1, 2), g.winners())
        assertEquals(Course.HOLES[0].par + Course.HOLES[1].par, g.totalPar)
        assertEquals(g.totalPar, g.parSoFar(0))
        // 아직 안 친 홀은 합에 안 들어감
        g.scores[3][1] = -1
        assertEquals(6, g.total(3))
        assertEquals(Course.HOLES[0].par, g.parSoFar(3))
    }

    @Test
    fun `전체 9홀 기본 · 인원 2~4`() {
        val g = MinigolfGame(2)
        assertEquals(9, g.holes.size)
        assertEquals(Course.HOLES.sumOf { it.par }, g.totalPar)
        assertTrue(runCatching { MinigolfGame(1) }.isFailure)
        assertTrue(runCatching { MinigolfGame(5) }.isFailure)
    }

    // ── 물리 정지·장애물 ──

    @Test
    fun `공이 멈추면 진행이 완전히 멈추고 장애물도 멈춘다`() {
        val h = Course.HOLES[5] // 미닫이 문
        val g = MinigolfGame(2, listOf(h))
        val y0 = g.sim.sliderOffsetY(0)
        assertFalse("치기 전엔 진행 없음", g.advance(1000))
        assertEquals(0L, g.sim.courseSteps)
        assertTrue(g.shoot(1f, 0f, 0.3f))
        var frames = 0
        while (g.advance(16)) frames++
        assertTrue("구르는 동안 진행 ($frames)", frames > 5)
        val steps = g.sim.courseSteps
        assertTrue("장애물 시계가 흘렀다", steps > 0)
        assertNotEquals(y0, g.sim.sliderOffsetY(0))
        val y1 = g.sim.sliderOffsetY(0)
        assertFalse("멈춘 뒤엔 진행 안 함", g.advance(5000))
        assertEquals(steps, g.sim.courseSteps)
        assertEquals(y1, g.sim.sliderOffsetY(0), 0f)
        // 다음 사람은 장애물 처음 자리에서
        burn(g)
        g.nextTurn()
        assertEquals(0L, g.sim.courseSteps)
        assertEquals(y0, g.sim.sliderOffsetY(0), 0f)
    }

    @Test
    fun `움직이는 장애물에 맞은 공은 튕겨 나온다`() {
        val h = Course.HOLES[5]
        val s = CourseSim(h)
        // 블록 바로 앞에서 블록 쪽으로
        val b = h.sliders[0].box
        s.placeBall(b.left - 2f, b.centerY)
        s.shoot(12f, 0f)
        roll(s)
        assertTrue("블록을 뚫지 않음 (${s.ball.x})", s.ball.x < b.left || s.ball.y < b.top - 1f || s.ball.y > b.bottom + 1f || s.ball.x > b.right)
        assertTrue("코스 안", h.insideOutline(s.ball.x, s.ball.y))
    }

    @Test
    fun `프레임을 어떻게 쪼개도 같은 결과`() {
        val h = Course.HOLES[8]
        val a = CourseSim(h)
        val b = CourseSim(h)
        a.shoot(-3f, -20f)
        b.shoot(-3f, -20f)
        while (a.advance(16)) Unit
        var k = 0
        while (b.advance(if (k++ % 2 == 0) 7L else 33L)) Unit
        assertEquals(a.ball.x, b.ball.x, 0f)
        assertEquals(a.ball.y, b.ball.y, 0f)
        assertEquals(a.courseSteps, b.courseSteps)
        val d = sqrt((a.ball.x - h.teeX) * (a.ball.x - h.teeX) + (a.ball.y - h.teeY) * (a.ball.y - h.teeY))
        assertTrue("움직였다 $d", d > 1f)
    }
}
