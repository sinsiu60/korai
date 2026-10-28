package com.siwoo.party.games.gonggi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GonggiGameTest {

    private var now = 1000L

    private fun game(n: Int = 2, target: Int = GameConfig.DEFAULT_TARGET, seed: Int = 7): GonggiGame {
        val g = GonggiGame(n, target, Random(seed))
        assertTrue(g.startTurn())
        return g
    }

    /** 바닥 돌 하나 (없으면 −1) */
    private fun floorStone(g: GonggiGame): Int = g.stones.indices.firstOrNull { g.stones[it].state == StoneState.FLOOR } ?: -1

    /** 한 번 던져 필요한 만큼 집고 착지 순간에 받기 */
    private fun goodToss(g: GonggiGame) {
        assertSame(Phase.READY, g.phase)
        assertTrue(g.tap(now))
        assertSame(Phase.AIR, g.phase)
        repeat(g.required) {
            now += 80
            assertTrue(g.tap(now, floorStone(g)))
        }
        now = g.landAt
        assertTrue(g.tap(now))
        assertSame(Phase.CAUGHT, g.phase)
        now += 500
    }

    /** 지금 단계를 끝까지 (다음 단계로 넘어감) */
    private fun clearStage(g: GonggiGame) {
        val s = g.curStage
        val steps = g.stepsInStage
        repeat(steps) { k ->
            goodToss(g)
            assertEquals(k == steps - 1, g.advance())
        }
        assertEquals(s + 1, g.curStage)
    }

    /** 꺾기: 손등 오차 err, 낚아채기 오차 grabErr */
    private fun kkeokgi(g: GonggiGame, err: Long, grabErr: Long = 0L) {
        assertSame(Phase.KK_READY, g.phase)
        g.tap(now)
        assertSame(Phase.KK_AIR, g.phase)
        now = g.kkLandAt + err
        g.tap(now)
        if (g.phase == Phase.KK_BACK) {
            now += 300
            g.tap(now)
            assertSame(Phase.KK_FLIP, g.phase)
            now = g.kkGrabAt + grabErr
            g.tap(now)
        }
        assertSame(Phase.TURN_END, g.phase)
        now += 500
    }

    /** 1단~4단 모두 + 꺾기 (손등 오차 err) */
    private fun fullCycle(g: GonggiGame, err: Long = 0L) {
        for (s in 0 until GameConfig.STAGE_KKEOKGI) clearStage(g)
        kkeokgi(g, err)
    }

    @Test
    fun `단계 진행 - 1단 하나씩 4번, 2단 둘씩 2번, 3단 셋 다음 하나, 4단 넷, 꺾기`() {
        val g = game()
        val seen = ArrayList<String>()
        while (!g.inKkeokgi) {
            seen.add(GameConfig.STAGE_NAMES[g.curStage] + ":" + g.required)
            goodToss(g)
            g.advance()
        }
        assertEquals(listOf("1단:1", "1단:1", "1단:1", "1단:1", "2단:2", "2단:2", "3단:3", "3단:1", "4단:4"), seen)
        assertSame(Phase.KK_READY, g.phase)
        assertEquals(0, g.required)
        assertTrue("꺾기는 5개 모두 손바닥", g.stones.all { it.state == StoneState.HAND })
        assertEquals(listOf(4, 2, 2, 1, 1), (0..4).map { GonggiGame.stepsOf(it) })
        assertEquals(1, GonggiGame.requiredOf(0, 3))
        assertEquals(3, GonggiGame.requiredOf(2, 0))
        assertEquals(1, GonggiGame.requiredOf(2, 1))
        assertEquals(4, GonggiGame.requiredOf(3, 0))
        assertEquals(0, GonggiGame.requiredOf(4, 0))
    }

    @Test
    fun `단계를 시작할 때 돌 4개를 흩고 1개는 손에`() {
        val g = game()
        assertEquals(StoneState.HAND, g.stones[GameConfig.TOSS_STONE].state)
        assertEquals(4, g.stones.count { it.state == StoneState.FLOOR })
        for (i in 1 until GameConfig.STONES) {
            val s = g.stones[i]
            assertTrue(s.x in GameConfig.SCATTER_LEFT..GameConfig.SCATTER_RIGHT)
            assertTrue(s.y in GameConfig.SCATTER_TOP..GameConfig.SCATTER_BOTTOM)
            for (j in 1 until i) {
                val dx = s.x - g.stones[j].x
                val dy = s.y - g.stones[j].y
                assertTrue("돌 사이 거리", Math.sqrt((dx * dx + dy * dy).toDouble()) >= GameConfig.MIN_GAP - 1e-5)
            }
        }
        // 같은 씨앗이면 같은 자리
        val g2 = game()
        for (i in 0 until GameConfig.STONES) {
            assertEquals(g.stones[i].x, g2.stones[i].x, 0f)
            assertEquals(g.stones[i].y, g2.stones[i].y, 0f)
        }
        // 1단 안에서는 집은 돌이 옆에 쌓이고 남은 돌은 그대로
        val x3 = g.stones[3].x
        goodToss(g)
        g.advance()
        assertEquals(1, g.stones.count { it.state == StoneState.ASIDE })
        assertEquals(3, g.stones.count { it.state == StoneState.FLOOR })
        assertEquals(x3, g.stones[3].x, 0f)
        // 2단이 되면 새로 흩음
        repeat(3) { goodToss(g); g.advance() }
        assertEquals(1, g.curStage)
        assertEquals(4, g.stones.count { it.state == StoneState.FLOOR })
    }

    @Test
    fun `집기 검사 - 너무 많이 집으면 실패`() {
        val g = game()
        g.tap(now)
        assertTrue(g.tap(now + 100, 1))
        assertEquals(1, g.picked)
        assertSame(Phase.AIR, g.phase)
        // 이미 집은 돌을 다시 누르면 무시
        assertFalse(g.tap(now + 150, 1))
        assertSame(Phase.AIR, g.phase)
        // 하나 더 → 실패
        g.tap(now + 200, 2)
        assertSame(Phase.TURN_END, g.phase)
        assertSame(Miss.TOO_MANY, g.miss)
        assertEquals("1단 그대로", 0, g.stage[0])
    }

    @Test
    fun `집기 검사 - 덜 집고 받으면 실패`() {
        val g = game()
        clearStage(g)
        assertEquals(1, g.curStage)
        g.tap(now)
        g.tap(now + 100, floorStone(g))
        g.tap(g.landAt)
        assertSame(Phase.TURN_END, g.phase)
        assertSame(Miss.TOO_FEW, g.miss)
        assertEquals("2단 그대로", 1, g.stage[0])
        assertEquals("떨어진 돌은 다시 바닥", 0, g.stones.count { it.state == StoneState.PICKED })
    }

    @Test
    fun `받기 구간 경계 - 구간 시작과 끝은 성공`() {
        for (edge in longArrayOf(-GameConfig.CATCH_EARLY_MS, GameConfig.CATCH_LATE_MS, 0L)) {
            now = 1000L
            val g = game()
            g.tap(now)
            g.tap(now + 50, floorStone(g))
            g.tap(g.landAt + edge)
            assertSame("오차 $edge", Phase.CAUGHT, g.phase)
            assertEquals(edge, g.lastErrMs)
        }
    }

    @Test
    fun `받기 구간 경계 - 1ms 이르거나 늦으면 실패`() {
        now = 1000L
        var g = game()
        g.tap(now)
        g.tap(now + 50, floorStone(g))
        g.tap(g.catchStart - 1)
        assertSame(Miss.EARLY, g.miss)
        assertSame(Phase.TURN_END, g.phase)

        now = 1000L
        g = game()
        g.tap(now)
        g.tap(now + 50, floorStone(g))
        g.tap(g.catchEnd + 1)
        assertSame(Miss.LATE, g.miss)

        // 안 받으면: update 가 구간 끝까지는 기다리고 그 뒤 실패
        now = 1000L
        g = game()
        g.tap(now)
        g.tap(now + 50, floorStone(g))
        assertFalse(g.update(g.catchEnd))
        assertSame(Phase.AIR, g.phase)
        assertTrue(g.update(g.catchEnd + 1))
        assertSame(Miss.LATE, g.miss)
        assertEquals(StoneState.FLOOR, g.stones[GameConfig.TOSS_STONE].state)
    }

    @Test
    fun `공중 시간은 단계마다 정해진 값`() {
        val g = game()
        while (!g.inKkeokgi) {
            g.tap(now)
            assertEquals(GameConfig.AIR_MS[g.curStage], g.airMs)
            assertEquals(now + GameConfig.AIR_MS[g.curStage], g.landAt)
            repeat(g.required) { g.tap(now + 10, floorStone(g)) }
            g.tap(g.landAt)
            now = g.landAt + 100
            g.advance()
        }
        assertSame(Phase.KK_READY, g.phase)
    }

    @Test
    fun `실패하면 차례가 넘어가고 다음 차례에 그 단계 처음부터`() {
        val g = game(3)
        // 가: 1단·2단 통과, 3단 두 번째 던지기에서 실패
        clearStage(g)
        clearStage(g)
        goodToss(g)
        g.advance()
        assertEquals(2, g.curStage)
        assertEquals(1, g.subStep)
        g.tap(now)
        assertTrue(g.update(g.catchEnd + 1))
        assertSame(Phase.TURN_END, g.phase)
        assertEquals(2, g.stage[0])
        assertTrue(g.nextTurn())
        assertEquals(1, g.current)
        assertTrue(g.startTurn())
        assertEquals("나는 1단부터", 0, g.curStage)
        g.tap(now)
        g.tap(g.catchStart - 10)
        assertTrue(g.nextTurn())
        assertEquals(2, g.current)
        g.startTurn()
        g.tap(now)
        g.tap(g.catchEnd + 5)
        assertTrue(g.nextTurn())
        assertEquals(0, g.current)
        assertEquals("한 바퀴", 1, g.round)
        assertTrue(g.startTurn())
        assertEquals("가는 3단 처음부터", 2, g.curStage)
        assertEquals(0, g.subStep)
        assertEquals(3, g.required)
        assertEquals("새로 흩음", 4, g.stones.count { it.state == StoneState.FLOOR })
        assertSame(Miss.NONE, g.miss)
        assertEquals(2, g.turns[0])
    }

    @Test
    fun `꺾기 손등 받기 정확도 - 개수 문턱`() {
        assertEquals(5, GonggiGame.backCountFor(0))
        assertEquals(5, GonggiGame.backCountFor(70))
        assertEquals(5, GonggiGame.backCountFor(-70))
        assertEquals(4, GonggiGame.backCountFor(71))
        assertEquals(4, GonggiGame.backCountFor(130))
        assertEquals(3, GonggiGame.backCountFor(-200))
        assertEquals(2, GonggiGame.backCountFor(280))
        assertEquals(1, GonggiGame.backCountFor(380))
        assertEquals(0, GonggiGame.backCountFor(381))
        assertEquals(0, GonggiGame.backCountFor(-2000))
    }

    @Test
    fun `꺾기 점수 - 손등에 남은 수만큼, 다음 차례는 1단부터`() {
        val g = game()
        fullCycle(g, err = 150)
        assertEquals(3, g.lastPoints)
        assertEquals(3, g.scores[0])
        assertEquals("다시 1단", 0, g.stage[0])
        assertSame(Miss.NONE, g.miss)
        assertTrue(g.nextTurn())
        assertEquals(1, g.current)
    }

    @Test
    fun `꺾기 실패 - 손등 0개나 낚아채기 놓치면 0점, 다음 차례 꺾기부터`() {
        val g = game()
        for (s in 0 until GameConfig.STAGE_KKEOKGI) clearStage(g)
        kkeokgi(g, err = -500)
        assertSame(Miss.BACK_ZERO, g.miss)
        assertEquals(0, g.scores[0])
        assertEquals(GameConfig.STAGE_KKEOKGI, g.stage[0])
        assertTrue(g.stones.all { it.state == StoneState.FLOOR })
        g.nextTurn(); g.startTurn()
        g.tap(now); g.tap(g.catchStart - 1) // 상대 실패
        g.nextTurn()
        assertTrue(g.startTurn())
        assertSame("꺾기부터", Phase.KK_READY, g.phase)
        // 손등 5개 → 낚아채기 너무 일찍
        kkeokgi(g, err = 0, grabErr = -GameConfig.KK_GRAB_EARLY_MS - 1)
        assertSame(Miss.GRAB_EARLY, g.miss)
        assertEquals(0, g.scores[0])
        g.nextTurn(); g.startTurn(); g.tap(now); g.tap(g.catchStart - 1); g.nextTurn(); g.startTurn()
        // 낚아채기 구간 끝 = 성공 (경계 포함), 손등 4개
        kkeokgi(g, err = 100, grabErr = GameConfig.KK_GRAB_LATE_MS)
        assertEquals(4, g.scores[0])
    }

    @Test
    fun `꺾기 - 손등 받기와 낚아채기를 안 하면 update 로 실패`() {
        val g = game()
        for (s in 0 until GameConfig.STAGE_KKEOKGI) clearStage(g)
        g.tap(now)
        assertFalse(g.update(g.kkLandAt + GameConfig.KK_BACK_THRESHOLDS[0]))
        assertTrue(g.update(g.kkLandAt + GameConfig.KK_BACK_THRESHOLDS[0] + 1))
        assertSame(Miss.BACK_ZERO, g.miss)
        g.nextTurn(); g.startTurn(); g.tap(now); g.tap(g.catchStart - 1); g.nextTurn(); g.startTurn()
        g.tap(now)
        g.tap(g.kkLandAt)
        assertSame(Phase.KK_BACK, g.phase)
        assertEquals(5, g.backCount)
        assertEquals(5, g.stones.count { it.state == StoneState.BACK })
        g.tap(now + 2000)
        assertFalse(g.update(g.kkGrabAt + GameConfig.KK_GRAB_LATE_MS))
        assertTrue(g.update(g.kkGrabAt + GameConfig.KK_GRAB_LATE_MS + 1))
        assertSame(Miss.GRAB_LATE, g.miss)
        assertEquals(0, g.scores[0])
    }

    @Test
    fun `손등에 일부만 얹히면 나머지는 바닥에`() {
        val g = game()
        for (s in 0 until GameConfig.STAGE_KKEOKGI) clearStage(g)
        g.tap(now)
        g.tap(g.kkLandAt + 250)
        assertEquals(2, g.backCount)
        assertEquals(2, g.stones.count { it.state == StoneState.BACK })
        assertEquals(3, g.stones.count { it.state == StoneState.FLOOR })
    }

    @Test
    fun `목표 점수에 먼저 닿은 순서가 순위`() {
        val g = game(3, target = 8)
        // 가 5점, 나 실패, 다 5점 / 가 4점(완주 1등), 다 3점(완주 2등) → 나 3등
        fullCycle(g, 0); g.nextTurn(); g.startTurn()
        g.tap(now); g.tap(g.catchStart - 1); g.nextTurn(); g.startTurn()
        fullCycle(g, 0); g.nextTurn(); g.startTurn()
        assertEquals(0, g.current)
        fullCycle(g, 100)
        assertEquals(9, g.scores[0])
        assertTrue(g.justFinished)
        assertEquals(1, g.place[0])
        assertTrue(g.nextTurn())
        g.startTurn()
        assertEquals(1, g.current)
        g.tap(now); g.tap(g.catchStart - 1)
        assertTrue(g.nextTurn())
        g.startTurn()
        assertEquals(2, g.current)
        fullCycle(g, 150)
        assertEquals(8, g.scores[2])
        assertEquals(2, g.place[2])
        assertFalse("한 명만 남으면 끝", g.nextTurn())
        assertSame(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(1, 3, 2), g.ranks())
    }

    @Test
    fun `2인은 한 명이 완주하면 바로 끝`() {
        val g = game(2, target = 3)
        fullCycle(g, 0)
        assertFalse(g.nextTurn())
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }

    @Test
    fun `완주한 사람은 차례에서 건너뜀`() {
        val g = game(4, target = 5)
        g.tap(now); g.tap(g.catchStart - 1); g.nextTurn(); g.startTurn() // 0 실패
        fullCycle(g, 0) // 1 완주
        assertEquals(1, g.place[1])
        g.nextTurn(); g.startTurn()
        assertEquals(2, g.current)
        g.tap(now); g.tap(g.catchStart - 1); g.nextTurn(); g.startTurn()
        assertEquals(3, g.current)
        g.tap(now); g.tap(g.catchStart - 1); g.nextTurn(); g.startTurn()
        assertEquals(0, g.current)
        g.tap(now); g.tap(g.catchStart - 1); g.nextTurn(); g.startTurn()
        assertEquals("1번 건너뜀", 2, g.current)
        assertEquals(2, GonggiGame.nextPlayer(0, intArrayOf(0, 1, 0, 0)))
        assertEquals(0, GonggiGame.nextPlayer(3, intArrayOf(0, 1, 2, 0)))
        assertEquals(3, GonggiGame.nextPlayer(0, intArrayOf(0, 1, 2, 0)))
    }

    @Test
    fun `최대 바퀴가 지나면 남은 사람은 점수 다음 단계 순`() {
        val g = game(3, target = 30)
        // 1바퀴: 가 꺾기 3점, 나 1단 실패, 다 3단까지 가서 실패
        fullCycle(g, 150)
        g.nextTurn(); g.startTurn()
        g.tap(now); g.tap(g.catchStart - 1)
        g.nextTurn(); g.startTurn()
        clearStage(g); clearStage(g)
        g.tap(now); g.tap(g.catchStart - 1)
        assertEquals(2, g.stage[2])
        // 나머지 바퀴: 모두 바로 실패
        var rounds = 1
        while (g.nextTurn()) {
            g.startTurn()
            g.tap(now); g.tap(g.catchStart - 1)
            if (g.current == 2) rounds++
        }
        assertEquals(GameConfig.MAX_ROUNDS, g.round)
        assertEquals(GameConfig.MAX_ROUNDS, rounds)
        for (p in 0 until 3) assertEquals(GameConfig.MAX_ROUNDS, g.turns[p])
        assertSame(Phase.OVER, g.phase)
        // 가 3점 1등, 다(0점, 3단) 2등, 나(0점, 1단) 3등
        assertArrayEquals(intArrayOf(1, 3, 2), g.ranks())
    }

    @Test
    fun `순위 계산 - 완주 순서 뒤에 남은 사람, 같으면 같은 순위`() {
        assertArrayEquals(intArrayOf(2, 1, 3, 3), GonggiGame.ranksOf(intArrayOf(2, 1, 0, 0), intArrayOf(20, 22, 5, 5), intArrayOf(0, 0, 2, 2)))
        assertArrayEquals(intArrayOf(3, 1, 2, 4), GonggiGame.ranksOf(intArrayOf(0, 1, 0, 0), intArrayOf(5, 20, 5, 4), intArrayOf(1, 0, 3, 4)))
        assertArrayEquals(intArrayOf(1, 1), GonggiGame.ranksOf(intArrayOf(0, 0), intArrayOf(7, 7), intArrayOf(2, 2)))
    }

    @Test
    fun `대기·받은 뒤·끝에는 터치 무시`() {
        val g = GonggiGame(2, 20, Random(1))
        assertFalse(g.tap(now))
        assertSame(Phase.WAIT, g.phase)
        g.startTurn()
        assertFalse(g.startTurn())
        goodToss(g)
        assertFalse(g.tap(now))
        assertSame(Phase.CAUGHT, g.phase)
        assertFalse(g.advance())
        assertSame(Phase.READY, g.phase)
        assertFalse("READY 에서는 advance 무시", g.advance())
    }
}
