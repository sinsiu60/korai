package com.siwoo.party.games.alkkagi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AlkkagiGameTest {

    /** 기록용 알림 */
    private class Log : AlkkagiGame.Listener {
        val events = ArrayList<String>()
        var collisions = 0
        override fun onCollide(x: Float, y: Float, speed: Float) { collisions++ }
        override fun onFall(stone: Stone, byShooter: Boolean) { events.add("fall ${stone.id} ${if (byShooter) "own" else "opp"}") }
        override fun onTurnEnd(shooter: Int, knocked: Int, lost: Int, next: Int) { events.add("turn $shooter $knocked $lost $next") }
        override fun onGameOver(winner: Int) { events.add("over $winner") }
    }

    private fun game(first: Int = 0): Pair<AlkkagiGame, Log> {
        val g = AlkkagiGame(first, fallMs = 300)
        val log = Log()
        g.listener = log
        return g to log
    }

    /** 멈출 때까지 16ms 프레임으로 진행 (최대 30초) */
    private fun run(g: AlkkagiGame): Int {
        var frames = 0
        while (g.advance(16) && frames < 30 * 60) frames++
        assertFalse("모두 멈춤", g.phase == Phase.MOVING)
        return frames
    }

    /** 0번 알만 남기고 나머지 정리하는 도우미 */
    private fun clearAllBut(g: AlkkagiGame, vararg keep: Int) {
        for (s in g.stones) if (s.id !in keep) g.remove(s.id)
    }

    // ── 시작 ──

    @Test fun `시작 배치 - 각자 8개, 좌우 대칭, 겹침 없음`() {
        val g = AlkkagiGame()
        assertEquals(GameConfig.ALKKAGI_STONES, g.remaining(0))
        assertEquals(GameConfig.ALKKAGI_STONES, g.remaining(1))
        for (k in 0 until GameConfig.ALKKAGI_STONES) {
            val a = g.stones[k]
            val b = g.stones[k + GameConfig.ALKKAGI_STONES]
            assertEquals(0, a.owner)
            assertEquals(1, b.owner)
            assertTrue("1번은 왼쪽", a.x < GameConfig.BOARD_W / 2)
            assertEquals(GameConfig.BOARD_W - a.x, b.x, 1e-5f)
            assertEquals(a.y, b.y, 0f)
        }
        for (i in g.stones.indices) for (j in i + 1 until g.stones.size) {
            val dx = g.stones[i].x - g.stones[j].x
            val dy = g.stones[i].y - g.stones[j].y
            assertTrue(dx * dx + dy * dy > 4f)
        }
        assertSame(Phase.AIM, g.phase)
        assertEquals(0, g.current)
        assertEquals(1, AlkkagiGame(1).current)
    }

    // ── 튕기기 ──

    @Test fun `끌기 거리가 짧으면 취소 - 차례 그대로, 알도 그대로`() {
        val (g, _) = game()
        val s = g.stones[4]
        val x = s.x
        assertSame(Flick.TOO_SHORT, g.flick(4, -0.5f, 0.5f)) // 0.707 < 0.8
        assertSame(Phase.AIM, g.phase)
        assertEquals(0, g.current)
        assertFalse(s.moving)
        assertEquals(x, s.x, 0f)
        assertEquals(0, g.shots)
        assertFalse(g.advance(100))
    }

    @Test fun `힘은 끌기 거리에 비례하고 최대 힘으로 제한`() {
        assertEquals(0f, AlkkagiGame.launchSpeed(0f), 0f)
        assertEquals(GameConfig.ALKKAGI_MAX_POWER / 2, AlkkagiGame.launchSpeed(GameConfig.ALKKAGI_MAX_DRAG / 2), 1e-4f)
        assertEquals(GameConfig.ALKKAGI_MAX_POWER, AlkkagiGame.launchSpeed(GameConfig.ALKKAGI_MAX_DRAG), 1e-4f)
        assertEquals(GameConfig.ALKKAGI_MAX_POWER, AlkkagiGame.launchSpeed(GameConfig.ALKKAGI_MAX_DRAG * 5), 1e-4f)
        assertEquals(1f, AlkkagiGame.power(100f), 0f)

        val (g, _) = game()
        // 왼쪽 위로 끌면(−x, −y) 오른쪽 아래로 튕겨 나감
        assertSame(Flick.SHOT, g.flick(4, -30f, -40f))
        val s = g.stones[4]
        assertEquals("최대 힘으로 제한", GameConfig.ALKKAGI_MAX_POWER, s.speed, 1e-3f)
        assertTrue(s.vx > 0f && s.vy > 0f)
        assertEquals(4f / 3f, s.vy / s.vx, 1e-4f)
        assertSame(Phase.MOVING, g.phase)
        assertEquals(1, g.shots)
        // 움직이는 동안은 아무 알도 못 튕김
        assertSame(Flick.NOT_NOW, g.flick(5, -3f, 0f))
    }

    @Test fun `내 알만 튕길 수 있음 - 상대 알·떨어진 알은 안 됨`() {
        val (g, _) = game()
        for (k in 8 until 16) {
            assertFalse(g.canFlick(k))
            assertSame(Flick.NOT_YOURS, g.flick(k, 3f, 0f))
        }
        assertSame(Phase.AIM, g.phase)
        g.remove(2)
        assertSame(Flick.NOT_YOURS, g.flick(2, -3f, 0f))
        assertTrue(g.canFlick(0))
        val (g1, _) = game(first = 1)
        assertSame(Flick.NOT_YOURS, g1.flick(0, -3f, 0f))
        assertSame(Flick.SHOT, g1.flick(8, 3f, 0f))
        assertTrue("1번 알은 왼쪽으로", g1.stones[8].vx < 0f)
    }

    // ── 차례 ──

    @Test fun `모두 멈추면 차례가 넘어감`() {
        val (g, log) = game()
        assertSame(Flick.SHOT, g.flick(4, -1.5f, 0f)) // 약하게 → 아무것도 안 떨어짐
        assertTrue(g.advance(16))
        run(g)
        assertSame(Phase.AIM, g.phase)
        assertEquals(1, g.current)
        assertEquals(listOf("turn 0 0 0 1"), log.events)
        assertFalse("멈춘 뒤엔 물리가 돌지 않음", g.advance(16))
        assertEquals(8, g.remaining(0))
        assertEquals(8, g.remaining(1))
    }

    @Test fun `내 알만 떨어져도 차례가 넘어감`() {
        val (g, log) = game()
        // 0번 알(4,4)을 왼쪽 위 판 밖으로: 오른쪽 아래로 끌기
        assertSame(Flick.SHOT, g.flick(0, 4f, 0.5f))
        run(g)
        assertEquals(listOf("fall 0 own", "turn 0 0 1 1"), log.events)
        assertSame(StoneState.GONE, g.stones[0].state)
        assertEquals(7, g.remaining(0))
        assertEquals(8, g.remaining(1))
        assertEquals(1, g.current)
        assertSame(Phase.AIM, g.phase)
    }

    @Test fun `상대 알을 판 밖으로 - 떨어진 알 수 기록`() {
        val (g, log) = game()
        // 0번 앞줄 알(8,8)로 오른쪽 상대 알(16,8)을 정면으로
        assertSame(Flick.SHOT, g.flick(5, -10f, 0f))
        run(g)
        assertTrue(log.collisions > 0)
        assertTrue("상대 알이 떨어짐: ${log.events}", log.events.any { it.endsWith("opp") })
        assertTrue(g.remaining(1) < 8)
        assertEquals(1, g.current)
    }

    // ── 끝·순위 ──

    @Test fun `상대 마지막 알을 떨어뜨리면 승리`() {
        val (g, log) = game()
        clearAllBut(g, 0, 8)
        g.place(0, 14f, 10f)
        g.place(8, 18f, 10f)
        assertSame(Flick.SHOT, g.flick(0, -6f, 0f))
        run(g)
        assertSame(Phase.OVER, g.phase)
        assertEquals(0, g.winner)
        assertEquals("over 0", log.events.last())
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
        assertSame(Flick.NOT_NOW, g.flick(0, -6f, 0f))
    }

    @Test fun `내 마지막 알이 떨어지면 패배`() {
        val (g, _) = game(first = 1)
        clearAllBut(g, 0, 8, 9)
        g.place(0, 4f, 10f)
        g.place(8, 22f, 10f)
        g.place(9, 20f, 4f)
        // 1번이 0번의 마지막 알을 맞혀 왼쪽 밖으로
        g.place(8, 7f, 10f)
        assertSame(Flick.SHOT, g.flick(8, 5f, 0f))
        run(g)
        assertSame(Phase.OVER, g.phase)
        assertEquals(1, g.winner)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test fun `양쪽 마지막 알이 한 번에 떨어지면 튕긴 사람이 진다`() {
        val (g, log) = game()
        clearAllBut(g, 0, 8)
        // 0번 알이 1번 알을 비스듬히 맞힘 → 둘 다 오른쪽 가장자리 밖으로 (맞은 알은 아래로, 친 알은 위로 비껴 감)
        g.place(0, 18f, 10f)
        g.place(8, 22f, 11.6f)
        assertSame(Flick.SHOT, g.flick(0, -6f, 0f))
        run(g)
        assertTrue(log.events.toString(), log.events.contains("fall 0 own") && log.events.contains("fall 8 opp"))
        assertEquals(0, g.remaining(0))
        assertEquals(0, g.remaining(1))
        assertSame(Phase.OVER, g.phase)
        assertEquals("튕긴 0번이 짐", 1, g.winner)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test fun `여러 판을 번갈아 두면 결국 끝남`() {
        val (g, _) = game()
        var turns = 0
        while (g.phase != Phase.OVER && turns < 200) {
            // 내 알 중 상대 알과 가장 가까운 쌍을 골라 정면으로 최대 힘
            var best = -1
            var target = -1
            var bd = Float.MAX_VALUE
            for (a in g.stones) if (a.owner == g.current && a.onBoard) for (b in g.stones) if (b.owner != g.current && b.onBoard) {
                val d = (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
                if (d < bd) { bd = d; best = a.id; target = b.id }
            }
            val a = g.stones[best]
            val b = g.stones[target]
            assertSame(Flick.SHOT, g.flick(best, a.x - b.x, a.y - b.y))
            run(g)
            turns++
        }
        assertSame(Phase.OVER, g.phase)
        assertTrue(g.winner in 0..1)
        assertEquals(1, g.ranks()[g.winner])
        assertEquals(2, g.ranks()[1 - g.winner])
    }
}
