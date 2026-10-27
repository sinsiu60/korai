package com.siwoo.party.games.marbles

import com.siwoo.party.games.marbles.GameConfig.PLACE_MIN_DIST
import com.siwoo.party.games.marbles.GameConfig.RING_R
import com.siwoo.party.games.marbles.GameConfig.RING_X
import com.siwoo.party.games.marbles.GameConfig.RING_Y
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class MarblesGameTest {

    /** 기록용 알림 */
    private class Log : MarblesGame.Listener {
        val ends = ArrayList<ShotEnd>()
        var collisions = 0
        var left = 0
        override fun onCollide(x: Float, y: Float, speed: Float) { collisions++ }
        override fun onLeaveField(m: Marble) { left++ }
        override fun onShotEnd(r: ShotEnd) { ends.add(r) }
    }

    private fun game(n: Int = 2, first: Int = 0, seed: Int = 1): Pair<MarblesGame, Log> {
        val g = MarblesGame(n, first, seed)
        val log = Log()
        g.listener = log
        return g to log
    }

    /** 멈출 때까지 16ms 프레임으로 진행 (최대 60초) */
    private fun run(g: MarblesGame): Int {
        var frames = 0
        while (g.advance(16) && frames < 60 * 60) frames++
        assertFalse("모두 멈춤", g.phase == Phase.MOVING)
        return frames
    }

    /** 지정한 가운데 구슬만 남기고 치움 */
    private fun clearAllBut(g: MarblesGame, vararg keep: Int) {
        for (t in g.targets) if (t.id !in keep) g.removeTarget(t.id)
    }

    /** 왼쪽 가장자리에 놓고 왼쪽(마당 밖)으로 쏘기 = 아무것도 안 건드리는 샷 */
    private fun shootAway(g: MarblesGame) {
        assertTrue(g.place(0f, RING_Y))
        assertSame(Fire.SHOT, g.shoot(-1f, 0f, 0.6f))
        run(g)
    }

    private fun dist(x: Float, y: Float) = sqrt((x - RING_X) * (x - RING_X) + (y - RING_Y) * (y - RING_Y))

    // ── 시작 ──

    @Test fun `시작 배치 - 10개가 링 가운데 겹치지 않게, 시드가 같으면 같은 배치`() {
        for (seed in 0 until 30) {
            val g = MarblesGame(4, 0, seed)
            assertEquals(GameConfig.TARGETS, g.remaining)
            for (t in g.targets) {
                assertTrue(t.inPlay)
                assertTrue("무리 안", dist(t.x, t.y) <= GameConfig.CLUSTER_R + 1e-4f)
            }
            for (i in g.targets.indices) for (j in i + 1 until g.targets.size) {
                val a = g.targets[i]
                val b = g.targets[j]
                val d = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
                assertTrue("겹침 없음 seed=$seed", d >= 2 * GameConfig.TARGET_R)
            }
            assertFalse("내 구슬은 아직 없음", g.shooterPlaced)
            assertSame(Phase.PLACE, g.phase)
        }
        val a = MarblesGame(2, 0, 7)
        val b = MarblesGame(2, 0, 7)
        val c = MarblesGame(2, 0, 8)
        for (i in a.targets.indices) {
            assertEquals(a.targets[i].x, b.targets[i].x, 0f)
            assertEquals(a.targets[i].y, b.targets[i].y, 0f)
        }
        assertTrue("다른 시드는 다른 배치", a.targets.indices.any { a.targets[it].x != c.targets[it].x })
    }

    // ── 놓기 ──

    @Test fun `내 구슬 놓기 - 링 안을 누르면 링 밖으로, 마당 밖을 누르면 마당 안으로 당김`() {
        val (g, _) = game()
        // 링 안 (가운데 조금 아래) → 같은 방향으로 링 밖
        assertTrue(g.place(RING_X, RING_Y + 1f))
        assertSame(Phase.AIM, g.phase)
        assertTrue(g.shooterPlaced)
        assertEquals(RING_X, g.shooter.x, 1e-4f)
        assertEquals(RING_Y + PLACE_MIN_DIST, g.shooter.y, 1e-4f)
        assertTrue("몸 전체가 링 밖", dist(g.shooter.x, g.shooter.y) - GameConfig.SHOOTER_R > RING_R)
        // 링 오른쪽 안 → 오른쪽 밖
        g.place(RING_X + 2f, RING_Y)
        assertEquals(RING_X + PLACE_MIN_DIST, g.shooter.x, 1e-4f)
        assertEquals(RING_Y, g.shooter.y, 1e-4f)
        // 마당 밖 왼쪽 위 → 마당 안 귀퉁이
        g.place(-5f, -5f)
        assertEquals(GameConfig.FIELD_MARGIN, g.shooter.x, 1e-4f)
        assertEquals(GameConfig.FIELD_MARGIN, g.shooter.y, 1e-4f)
        assertTrue(MarblesGame.isPlaceable(g.shooter.x, g.shooter.y))
        // 정확히 가운데 → 아래쪽 링 밖
        g.place(RING_X, RING_Y)
        assertEquals(RING_Y + PLACE_MIN_DIST, g.shooter.y, 1e-4f)
        // 이미 링 밖인 자리는 그대로
        g.place(3f, 4f)
        assertEquals(3f, g.shooter.x, 0f)
        assertEquals(4f, g.shooter.y, 0f)
        // 어디를 눌러도 허용 자리
        val out = FloatArray(2)
        var x = -3f
        while (x < GameConfig.FIELD_W + 3f) {
            var y = -3f
            while (y < GameConfig.FIELD_H + 3f) {
                MarblesGame.clampPlacement(x, y, out)
                assertTrue("($x,$y) → (${out[0]},${out[1]})", MarblesGame.isPlaceable(out[0], out[1]))
                y += 0.7f
            }
            x += 0.7f
        }
        assertFalse(MarblesGame.isPlaceable(RING_X + RING_R + 0.5f, RING_Y))
    }

    @Test fun `내 구슬 놓기 - 링 선에 걸친 가운데 구슬과 겹치지 않게 밀어냄`() {
        val (g, _) = game()
        g.putTarget(0, RING_X + RING_R - 0.1f, RING_Y)
        g.place(RING_X + PLACE_MIN_DIST, RING_Y)
        val t = g.targets[0]
        val d = sqrt((g.shooter.x - t.x) * (g.shooter.x - t.x) + (g.shooter.y - t.y) * (g.shooter.y - t.y))
        assertTrue("겹치지 않음 ($d)", d >= GameConfig.SHOOTER_R + GameConfig.TARGET_R)
        assertTrue(MarblesGame.isPlaceable(g.shooter.x, g.shooter.y))
    }

    @Test fun `쏘기 - 안 놓았으면 못 쏨, 약하면 취소, 굴러가는 중엔 못 놓고 못 쏨`() {
        val (g, _) = game()
        assertSame(Fire.NOT_PLACED, g.shoot(1f, 0f, 1f))
        g.place(2f, RING_Y)
        assertSame(Fire.TOO_WEAK, g.shoot(1f, 0f, GameConfig.MIN_POWER * 0.5f))
        assertSame(Phase.AIM, g.phase)
        assertEquals(0, g.shots)
        assertSame(Fire.SHOT, g.shoot(1f, 0f, 1f))
        assertSame(Phase.MOVING, g.phase)
        assertFalse(g.place(3f, 3f))
        assertSame(Fire.NOT_NOW, g.shoot(1f, 0f, 1f))
        assertEquals(1, g.shots)
        assertEquals(GameConfig.MAX_SPEED, g.shooter.speed, 1e-3f)
    }

    // ── 획득 판정 ──

    @Test fun `획득 판정 - 중심이 링 밖이어야 획득, 몸만 걸치거나 선 위면 아님`() {
        assertFalse("링 선 위", MarblesGame.isOutsideRing(RING_X + RING_R, RING_Y))
        assertTrue(MarblesGame.isOutsideRing(RING_X + RING_R + 0.01f, RING_Y))
        assertFalse(MarblesGame.isOutsideRing(RING_X, RING_Y - RING_R + 0.01f))

        val (g, log) = game()
        clearAllBut(g, 0, 1, 2, 3)
        g.putTarget(0, RING_X + RING_R - 0.3f, RING_Y) // 몸이 선에 걸침, 중심은 안
        g.putTarget(1, RING_X, RING_Y + RING_R + 0.05f) // 중심이 막 밖
        g.putTarget(2, RING_X - RING_R * 0.70710677f, RING_Y - RING_R * 0.70710677f + 0.01f) // 거의 선 위 (안)
        g.putTarget(3, RING_X, RING_Y)
        shootAway(g)
        val r = log.ends.single()
        assertEquals(listOf(1), r.captured)
        assertTrue(r.extraTurn)
        assertSame(MarbleState.CAPTURED, g.targets[1].state)
        assertEquals(0, g.targets[1].capturedBy)
        for (id in listOf(0, 2, 3)) assertTrue("$id 는 그대로", g.targets[id].inPlay)
        assertEquals(1, g.captured[0])
        assertEquals(3, g.remaining)
    }

    @Test fun `획득 - 쳐서 링 밖으로 내보내면 획득하고 한 번 더`() {
        val (g, log) = game(3, first = 1)
        clearAllBut(g, 0)
        g.putTarget(0, RING_X + RING_R - 1.5f, RING_Y)
        g.place(RING_X - PLACE_MIN_DIST - 0.5f, RING_Y)
        assertSame(Fire.SHOT, g.shoot(1f, 0f, 1f))
        run(g)
        assertTrue("부딪힘", log.collisions > 0)
        val r = log.ends.single()
        assertEquals(1, r.shooter)
        assertEquals(listOf(0), r.captured)
        assertTrue(r.extraTurn)
        assertEquals("같은 사람이 한 번 더", 1, r.next)
        assertEquals(1, g.current)
        assertTrue("마지막 구슬 → 끝", r.gameOver)
        assertSame(MarbleState.CAPTURED, g.targets[0].state)
        assertSame("내 구슬은 치움", MarbleState.REMOVED, g.shooter.state)
    }

    @Test fun `한 번 더 - 획득하면 같은 사람, 못 하면 다음 사람`() {
        val (g, log) = game(3, first = 2)
        clearAllBut(g, 0, 1, 5)
        g.putTarget(5, RING_X, RING_Y - 3f)
        g.putTarget(0, RING_X + RING_R + 0.5f, RING_Y) // 이미 링 밖에 있던 것처럼 (판정은 멈췄을 때 자리)
        g.putTarget(1, RING_X - 2f, RING_Y + 2f)
        shootAway(g)
        assertTrue(log.ends.last().extraTurn)
        assertEquals(2, g.current)
        assertSame(Phase.PLACE, g.phase)
        assertFalse("내 구슬은 다시 놓아야 함", g.shooterPlaced)
        assertEquals(0, g.missedTurns)
        // 이번엔 아무것도 못 함 → 다음 사람 (2 → 0)
        shootAway(g)
        val r = log.ends.last()
        assertFalse(r.extraTurn)
        assertTrue(r.captured.isEmpty())
        assertEquals(0, r.next)
        assertEquals(0, g.current)
        assertEquals(1, g.missedTurns)
        assertTrue("내 구슬이 마당 밖으로", r.shooterLeftField)
        assertArrayEquals(intArrayOf(0, 0, 1), g.captured)
    }

    @Test fun `한 번에 여러 개 - 두 구슬 사이를 쳐서 둘 다 링 밖으로`() {
        val (g, log) = game()
        clearAllBut(g, 3, 4)
        val gap = GameConfig.TARGET_R + 0.05f
        g.putTarget(3, RING_X + 4f, RING_Y - gap)
        g.putTarget(4, RING_X + 4f, RING_Y + gap)
        g.place(RING_X - PLACE_MIN_DIST - 0.3f, RING_Y)
        g.shoot(1f, 0f, 1f)
        run(g)
        val r = log.ends.single()
        assertEquals(listOf(3, 4), r.captured.sorted())
        assertEquals(2, g.captured[0])
        assertTrue(r.extraTurn)
        assertTrue(r.gameOver)
    }

    @Test fun `한 번에 여러 개 - 마당 밖으로 나간 구슬도 획득`() {
        val (g, log) = game()
        clearAllBut(g, 0, 1, 2)
        g.putTarget(0, RING_X + RING_R - 0.6f, RING_Y)
        g.putTarget(1, RING_X - 1f, RING_Y - 4f)
        g.putTarget(2, RING_X + RING_R + 1f, RING_Y + 4f) // 원래 링 밖에 있던 것
        g.place(RING_X - PLACE_MIN_DIST, RING_Y + 0.1f)
        g.shoot(1f, 0f, 1f)
        run(g)
        val r = log.ends.single()
        assertTrue("0번이 마당 밖으로", log.left >= 1)
        assertTrue(r.captured.containsAll(listOf(0, 2)))
        assertEquals(r.captured.size, g.captured[0])
        assertSame(MarbleState.CAPTURED, g.targets[0].state)
    }

    @Test fun `내 구슬이 링 안에 멈춰도 치움`() {
        val (g, log) = game()
        clearAllBut(g, 0)
        g.putTarget(0, RING_X, RING_Y - 5f)
        g.place(RING_X - PLACE_MIN_DIST, RING_Y)
        g.shoot(1f, 0f, 0.42f)
        run(g)
        val r = log.ends.single()
        assertTrue("링 안에 멈춤 (${r.shooterX})", r.shooterInRing)
        assertFalse(r.shooterLeftField)
        assertSame(MarbleState.REMOVED, g.shooter.state)
        assertFalse(g.shooterPlaced)
        assertTrue(r.captured.isEmpty())
        assertEquals(1, g.current)
    }

    // ── 물리 루프 ──

    @Test fun `멈추면 advance 가 false 이고 더는 단계를 진행하지 않음`() {
        val (g, _) = game()
        assertFalse("쏘기 전엔 안 돈다", g.advance(16))
        val s0 = g.world.steps
        g.place(2f, RING_Y)
        g.shoot(1f, 0f, 1f)
        assertTrue(g.advance(16))
        run(g)
        val s1 = g.world.steps
        assertTrue(s1 > s0)
        repeat(50) { assertFalse(g.advance(16)) }
        assertEquals("멈춘 뒤엔 단계 없음", s1, g.world.steps)
        for (t in g.targets) if (t.inPlay) assertFalse(t.moving)
    }

    @Test fun `같은 입력이면 같은 결과 (프레임 쪼개기와 무관)`() {
        fun play(frame: Long): List<Float> {
            val g = MarblesGame(2, 0, 3)
            g.place(RING_X - PLACE_MIN_DIST, RING_Y + 0.4f)
            g.shoot(1f, -0.05f, 1f)
            while (g.advance(frame)) Unit
            return g.targets.flatMap { listOf(it.x, it.y) } + g.captured.map { it.toFloat() }
        }
        assertEquals(play(16), play(10))
        assertEquals(play(16), play(50))
    }

    // ── 끝·순위 ──

    @Test fun `모든 구슬이 사라지면 끝`() {
        val (g, log) = game(2, first = 1)
        clearAllBut(g, 0, 1)
        g.putTarget(0, RING_X + RING_R + 1f, RING_Y)
        g.putTarget(1, RING_X - 1f, RING_Y)
        shootAway(g)
        assertFalse(g.over)
        assertEquals(1, g.remaining)
        g.putTarget(1, RING_X, RING_Y + RING_R + 0.4f)
        shootAway(g)
        assertTrue(g.over)
        assertSame(Phase.OVER, g.phase)
        assertTrue(log.ends.last().gameOver)
        assertEquals(0, g.remaining)
        assertArrayEquals(intArrayOf(0, 2), g.captured)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
        assertFalse("끝나면 못 놓음", g.place(2f, 2f))
    }

    @Test fun `순위 - 획득 수 많은 순, 동점은 같은 순위`() {
        val g = MarblesGame(4)
        g.captured[0] = 3; g.captured[1] = 5; g.captured[2] = 0; g.captured[3] = 2
        assertArrayEquals(intArrayOf(2, 1, 4, 3), g.ranks())
        g.captured[0] = 4; g.captured[1] = 4; g.captured[2] = 1; g.captured[3] = 1
        assertArrayEquals(intArrayOf(1, 1, 3, 3), g.ranks())
        val h = MarblesGame(3)
        assertArrayEquals("아무도 못 얻으면 모두 1위", intArrayOf(1, 1, 1), h.ranks())
    }

    @Test fun `최대 바퀴 - 모두 MAX_ROUNDS 번씩 못 맞히면 끝`() {
        for (n in 2..4) {
            val (g, log) = game(n, first = n - 1)
            clearAllBut(g, 0, 1)
            g.putTarget(1, RING_X + RING_R + 1f, RING_Y) // 첫 샷에 하나 획득 → 한 번 더 (못 맞힌 차례로 안 셈)
            g.putTarget(0, RING_X, RING_Y)
            shootAway(g)
            assertEquals(n - 1, g.current)
            val turns = IntArray(n)
            var k = 0
            while (!g.over) {
                turns[g.current]++
                assertEquals(k / n + 1, g.round)
                shootAway(g)
                k++
                assertTrue(k <= GameConfig.MAX_ROUNDS * n)
            }
            assertEquals(GameConfig.MAX_ROUNDS * n, k)
            for (p in 0 until n) assertEquals("모두 같은 차례 수", GameConfig.MAX_ROUNDS, turns[p])
            assertTrue(log.ends.last().gameOver)
            assertEquals("가운데 구슬은 남아 있음", 1, g.remaining)
            val ranks = g.ranks()
            assertEquals(1, ranks[n - 1])
            for (p in 0 until n - 1) assertEquals(2, ranks[p])
        }
    }

    @Test fun `처음 사람과 인원 검사`() {
        val g = MarblesGame(3, 2, 0)
        assertEquals(2, g.current)
        assertEquals(1, g.round)
        assertNotNull(g.ranks())
        try { MarblesGame(1); error("1인 안 됨") } catch (e: IllegalArgumentException) { }
        try { MarblesGame(5); error("5인 안 됨") } catch (e: IllegalArgumentException) { }
    }
}
