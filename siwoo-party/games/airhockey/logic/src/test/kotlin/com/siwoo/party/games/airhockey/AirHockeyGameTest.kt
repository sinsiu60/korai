package com.siwoo.party.games.airhockey

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 에어하키 규칙·물리: 득점, 골문 옆 벽 반사, 재시작 자리, 말렛 반쪽 제한, 말렛 → 퍽 운동량, 7점 승리 */
class AirHockeyGameTest {

    private val w = GameConfig.TABLE_W
    private val h = GameConfig.TABLE_H
    private val midY = h / 2f

    /** 시작하고, 말렛 둘을 퍽 길에서 치운다 (위 구석) */
    private fun playing(): AirHockeyGame {
        val g = AirHockeyGame()
        g.start()
        moveMalletsAway(g)
        return g
    }

    private fun moveMalletsAway(g: AirHockeyGame) {
        g.mallets[0].place(2f, 2f)
        g.mallets[1].place(w - 2f, 2f)
        g.setTarget(0, 2f, 2f)
        g.setTarget(1, w - 2f, 2f)
    }

    /** 조건이 될 때까지 1/60초씩 (최대 n 단계) */
    private fun runUntil(g: AirHockeyGame, n: Int = 600, cond: () -> Boolean): Boolean {
        repeat(n) {
            if (cond()) return true
            g.advance(17)
        }
        return cond()
    }

    @Test
    fun `처음엔 퍽이 가운데 멈춰 있고 시작 전엔 진행 안 함`() {
        val g = AirHockeyGame()
        assertSame(Phase.READY, g.phase)
        assertEquals(GameConfig.CENTER_X, g.puck.x, 0f)
        assertEquals(midY, g.puck.y, 0f)
        assertFalse(g.puck.moving)
        g.setTarget(0, 10f, 3f)
        assertEquals("시작 전엔 단계가 돌지 않음", 0, g.advance(100))
        g.start()
        assertSame(Phase.PLAY, g.phase)
        assertTrue(g.advance(100) > 0)
    }

    @Test
    fun `퍽이 오른쪽 골문을 지나면 왼쪽 사람 득점`() {
        val g = playing()
        var scored = -1
        g.listener = object : AirHockeyGame.Listener {
            override fun onGoal(scorer: Int) { scored = scorer }
        }
        g.puck.place(w - 5f, midY)
        g.puck.setVelocity(20f, 1.5f)
        assertTrue("골", runUntil(g) { g.phase != Phase.PLAY })
        assertEquals(0, scored)
        assertArrayEquals(intArrayOf(1, 0), g.score)
        assertSame(Phase.GOAL, g.phase)
        assertFalse("골 뒤 퍽은 세계에서 빠짐", g.puck.active)
        assertEquals("골 뒤엔 진행 멈춤", 0, g.advance(100))
    }

    @Test
    fun `퍽이 왼쪽 골문을 지나면 오른쪽 사람 득점`() {
        val g = playing()
        g.puck.place(6f, midY + 1f)
        g.puck.setVelocity(-25f, -2f)
        assertTrue(runUntil(g) { g.phase != Phase.PLAY })
        assertArrayEquals(intArrayOf(0, 1), g.score)
        assertEquals(1, g.lastScorer)
    }

    @Test
    fun `골문 옆 벽에 맞으면 튕겨 나오고 득점 없음`() {
        val g = playing()
        // 말렛은 아래쪽으로 치워 둔다
        g.mallets[0].place(4f, h - 2f)
        g.mallets[1].place(w - 4f, h - 2f)
        g.setTarget(0, 4f, h - 2f)
        g.setTarget(1, w - 4f, h - 2f)
        // 골문 위쪽 벽(왼쪽 끝, y 0..GOAL_TOP)으로 곧장
        val y = (GameConfig.GOAL_TOP - GameConfig.PUCK_R) / 2f + 0.4f
        g.puck.place(8f, y)
        g.puck.setVelocity(-20f, 0f)
        var walls = 0
        g.listener = object : AirHockeyGame.Listener {
            override fun onWall(x: Float, y: Float, speed: Float) { walls++ }
        }
        assertTrue("튕김", runUntil(g) { g.puck.vx > 0f })
        assertTrue(walls >= 1)
        assertSame(Phase.PLAY, g.phase)
        assertArrayEquals(intArrayOf(0, 0), g.score)
        assertTrue("판 안", g.puck.x >= GameConfig.PUCK_R - 0.01f)
        // 반발 계수만큼 느려짐 (마찰·공기 저항 조금 포함)
        assertTrue("느려짐 ${g.puck.vx}", g.puck.vx < 20f * GameConfig.WALL_E + 0.01f)
        assertTrue("거의 그대로 튕김 ${g.puck.vx}", g.puck.vx > 20f * GameConfig.WALL_E - 1.5f)
        // 한참 굴러도 득점 없음
        runUntil(g, 120) { false }
        assertArrayEquals(intArrayOf(0, 0), g.score)
    }

    @Test
    fun `골문 아래쪽 벽과 위아래 긴 벽도 반사`() {
        val g = playing()
        g.puck.place(w - 6f, h - 2f)
        g.puck.setVelocity(18f, 0f)
        assertTrue(runUntil(g) { g.puck.vx < 0f })
        assertSame(Phase.PLAY, g.phase)
        g.puck.place(GameConfig.CENTER_X, midY)
        g.puck.setVelocity(0f, -15f)
        assertTrue(runUntil(g) { g.puck.vy > 0f })
        assertTrue(g.puck.y >= GameConfig.PUCK_R - 0.01f)
        assertArrayEquals(intArrayOf(0, 0), g.score)
    }

    @Test
    fun `재시작하면 득점당한 쪽 가운데 선 근처에 멈춰 놓임`() {
        val g = playing()
        g.puck.place(w - 4f, midY)
        g.puck.setVelocity(20f, 0f)
        runUntil(g) { g.phase != Phase.PLAY }
        assertEquals(0, g.lastScorer)
        g.restart()
        assertSame(Phase.PLAY, g.phase)
        assertTrue(g.puck.active)
        assertFalse("멈춤", g.puck.moving)
        // 1번(오른쪽)이 득점당함 → 오른쪽 반, 가운데 선 근처
        assertEquals(GameConfig.CENTER_X + GameConfig.SERVE_OFFSET, g.puck.x, 0f)
        assertEquals(midY, g.puck.y, 0f)
        assertTrue(g.puck.x > GameConfig.CENTER_X)
        // 이번엔 왼쪽 골문으로 → 왼쪽(0번) 쪽에서 재시작
        g.puck.setVelocity(-30f, 0f)
        runUntil(g) { g.phase != Phase.PLAY }
        assertEquals(1, g.lastScorer)
        g.restart()
        assertEquals(GameConfig.CENTER_X - GameConfig.SERVE_OFFSET, g.puck.x, 0f)
        assertTrue(g.puck.x < GameConfig.CENTER_X)
        assertFalse(g.puck.moving)
        // 놓인 퍽은 가만히 (마찰로 움직이지 않음)
        g.advance(500)
        assertEquals(GameConfig.CENTER_X - GameConfig.SERVE_OFFSET, g.puck.x, 0f)
    }

    @Test
    fun `말렛은 자기 반쪽 밖으로 못 나감`() {
        val g = playing()
        // 왼쪽 사람이 가운데 선을 넘어 오른쪽 끝까지 끌기
        g.setTarget(0, w - 1f, -5f)
        assertEquals(GameConfig.CENTER_X - GameConfig.MALLET_R, g.targetX[0], 0f)
        assertEquals(GameConfig.MALLET_R, g.targetY[0], 0f)
        g.setTarget(1, 0f, h + 10f)
        assertEquals(GameConfig.CENTER_X + GameConfig.MALLET_R, g.targetX[1], 0f)
        assertEquals(h - GameConfig.MALLET_R, g.targetY[1], 0f)
        repeat(120) {
            g.advance(17)
            assertTrue("왼쪽 말렛 x ${g.mallets[0].x}", g.mallets[0].x <= GameConfig.CENTER_X - GameConfig.MALLET_R + 1e-3f)
            assertTrue(g.mallets[0].x >= GameConfig.MALLET_R - 1e-3f)
            assertTrue(g.mallets[1].x >= GameConfig.CENTER_X + GameConfig.MALLET_R - 1e-3f)
            assertTrue(g.mallets[1].y <= h - GameConfig.MALLET_R + 1e-3f)
        }
        // 끝내 가운데 선에 닿은 자리에서 멈춤
        assertEquals(GameConfig.CENTER_X - GameConfig.MALLET_R, g.mallets[0].x, 1e-3f)
        assertEquals(GameConfig.CENTER_X + GameConfig.MALLET_R, g.mallets[1].x, 1e-3f)
        // 반대쪽 끝 벽 너머로도 못 감
        g.setTarget(0, -10f, midY)
        assertEquals(GameConfig.MALLET_R, g.targetX[0], 0f)
    }

    @Test
    fun `말렛은 최대 속도로 손가락을 따라가고 손을 떼면 멈춤`() {
        val g = playing()
        g.mallets[0].place(3f, midY)
        g.setTarget(0, 13f, midY)
        g.step()
        assertEquals(GameConfig.MALLET_MAX_SPEED, g.mallets[0].vx, 0.01f)
        assertEquals(3f + GameConfig.MALLET_MAX_SPEED * GameConfig.STEP_SEC, g.mallets[0].x, 1e-3f)
        repeat(30) { g.step() }
        assertEquals(13f, g.mallets[0].x, 1e-3f)
        g.release(0)
        g.step()
        assertFalse(g.mallets[0].moving)
        assertEquals(13f, g.mallets[0].x, 1e-3f)
    }

    @Test
    fun `움직이는 말렛에 맞은 멈춘 퍽은 그 방향으로 속도를 얻음`() {
        val g = playing()
        g.puck.place(10f, midY)
        g.mallets[0].place(5f, midY)
        g.setTarget(0, 9f, midY)
        var hits = 0
        var hitBy = -1
        g.listener = object : AirHockeyGame.Listener {
            override fun onHit(player: Int, x: Float, y: Float, speed: Float) { hits++; hitBy = player }
        }
        assertTrue(runUntil(g, 60) { g.puck.moving })
        assertTrue(hits >= 1)
        assertEquals(0, hitBy)
        assertTrue("오른쪽으로 (${g.puck.vx})", g.puck.vx > 10f)
        assertTrue("곧게", abs(g.puck.vy) < 0.5f)
        assertTrue("말렛 빠르기보다 빠름 (반발)", g.puck.vx > GameConfig.MALLET_MAX_SPEED * 0.3f)
        assertTrue("최대 속도 이하", g.puck.speed <= GameConfig.PUCK_MAX_SPEED + 1e-3f)
        // 말렛은 밀리지 않음
        assertTrue(g.mallets[0].x <= 9f + 1e-3f)

        // 비스듬히 아래로 치면 아래쪽으로
        val g2 = playing()
        g2.puck.place(10f, midY)
        g2.mallets[0].place(8f, midY - 2f)
        g2.setTarget(0, 11f, midY + 1f)
        assertTrue(runUntil(g2, 60) { g2.puck.moving })
        assertTrue(g2.puck.vx > 0f)
        assertTrue(g2.puck.vy > 0f)
    }

    @Test
    fun `빠른 말렛으로 세게 쳐도 퍽 최대 속도를 넘지 않고 벽을 뚫지 않음`() {
        val g = playing()
        g.puck.place(12f, midY)
        g.mallets[0].place(2f, midY)
        g.setTarget(0, 14.6f, midY)
        var maxSpeed = 0f
        repeat(240) {
            g.advance(17)
            if (g.phase != Phase.PLAY) return@repeat
            maxSpeed = maxOf(maxSpeed, g.puck.speed)
            assertTrue(g.puck.y in 0f..h)
        }
        assertTrue(maxSpeed > 20f)
        assertTrue(maxSpeed <= GameConfig.PUCK_MAX_SPEED + 1e-3f)
        assertArrayEquals("곧장 오른쪽 골문으로", intArrayOf(1, 0), g.score)
    }

    @Test
    fun `벽에 끼여 밖으로 밀려나도 판 안으로 돌아옴`() {
        val g = playing()
        // 퍽을 위쪽 벽에 붙이고 말렛으로 눌러 짓누름
        g.puck.place(8f, GameConfig.PUCK_R)
        g.mallets[0].place(8f, 5f)
        g.setTarget(0, 8f, GameConfig.MALLET_R)
        repeat(60) {
            g.advance(17)
            assertTrue(g.puck.active)
            assertTrue("y ${g.puck.y}", g.puck.y >= GameConfig.PUCK_R - 1e-3f)
        }
        assertArrayEquals(intArrayOf(0, 0), g.score)
        assertSame(Phase.PLAY, g.phase)
    }

    @Test
    fun `7점 먼저 내면 승리`() {
        val g = playing()
        var goals = 0
        g.listener = object : AirHockeyGame.Listener {
            override fun onGoal(scorer: Int) { goals++ }
        }
        // 1번이 3점, 0번이 7점
        val order = intArrayOf(1, 0, 0, 1, 0, 1, 0, 0, 0, 0)
        for ((k, s) in order.withIndex()) {
            if (k > 0) g.restart()
            moveMalletsAway(g)
            g.puck.place(GameConfig.CENTER_X, midY)
            g.puck.setVelocity(if (s == 0) 30f else -30f, 0f)
            assertTrue(runUntil(g) { g.phase != Phase.PLAY })
            if (k < order.size - 1) assertSame("$k 번째 골", Phase.GOAL, g.phase)
        }
        assertEquals(order.size, goals)
        assertArrayEquals(intArrayOf(GameConfig.WIN_SCORE, 3), g.score)
        assertSame(Phase.OVER, g.phase)
        assertEquals(0, g.winner)
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
        // 끝나면 재시작·진행 없음
        g.restart()
        assertSame(Phase.OVER, g.phase)
        assertEquals(0, g.advance(1000))
    }

    @Test
    fun `오른쪽이 7점이면 순위 반대`() {
        val g = playing()
        repeat(GameConfig.WIN_SCORE) { k ->
            if (k > 0) g.restart()
            moveMalletsAway(g)
            g.puck.place(GameConfig.CENTER_X, midY)
            g.puck.setVelocity(-30f, 0f)
            runUntil(g) { g.phase != Phase.PLAY }
        }
        assertSame(Phase.OVER, g.phase)
        assertEquals(1, g.winner)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test
    fun `프레임을 어떻게 쪼개도 같은 결과`() {
        fun run(frames: LongArray): Pair<Float, Float> {
            val g = playing()
            g.puck.place(10f, 6f)
            g.puck.setVelocity(23f, 17f)
            var t = 0L
            var k = 0
            while (t < 2000L) {
                val d = frames[k++ % frames.size]
                g.advance(d)
                t += d
            }
            return g.puck.x to g.puck.y
        }
        val a = run(longArrayOf(16L, 17L, 17L))
        val b = run(longArrayOf(10L, 40L, 50L))
        assertEquals(a.first, b.first, 1e-4f)
        assertEquals(a.second, b.second, 1e-4f)
    }
}
