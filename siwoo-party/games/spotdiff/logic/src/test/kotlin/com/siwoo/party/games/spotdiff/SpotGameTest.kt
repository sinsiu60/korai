package com.siwoo.party.games.spotdiff

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 버저·답 시간·잠금·장면 넘김·3장면 합산 순위 (시간은 주입) */
class SpotGameTest {

    private fun game(n: Int = 3) = SpotGame(n, Random(7))

    /** 어떤 차이와도 먼 곳 (오답 자리) */
    private fun missPoint(s: Scene): FloatArray {
        var y = 2f
        while (y < GameConfig.SCENE_H) {
            var x = 2f
            while (x < GameConfig.SCENE_W) {
                if (s.differences.none { it.hit(x, y) }) return floatArrayOf(x, y)
                x += 7f
            }
            y += 7f
        }
        error("빈 곳 없음")
    }

    @Test
    fun `버저를 먼저 누른 사람만 답하고 나머지는 막힘`() {
        val g = game(4)
        assertSame(Buzz.Ignored, g.buzz(0, 0))
        g.startNextScene()
        assertSame(Phase.SEARCH, g.phase)
        val r = g.buzz(2, 1000)
        assertTrue(r is Buzz.Accepted)
        assertEquals(2, (r as Buzz.Accepted).player)
        assertEquals(1000 + GameConfig.ANSWER_MS, r.deadlineMs)
        assertSame(Phase.ANSWER, g.phase)
        assertEquals(2, g.answerer)
        for (p in listOf(0, 1, 3, 2)) {
            val b = g.buzz(p, 1001)
            assertTrue(b is Buzz.Busy)
            assertEquals(2, (b as Buzz.Busy).answerer)
        }
        assertSame(Buzz.Ignored, g.buzz(9, 1001))
    }

    @Test
    fun `정답이면 +1 하고 다시 모두 버저`() {
        val g = game()
        val s = g.startNextScene()
        g.buzz(1, 0)
        val d = s.differences[3]
        val a = g.answer(d.cx + 3f, d.cy - 2f, 2500)
        assertTrue(a is Answer.Correct)
        a as Answer.Correct
        assertEquals(1, a.player)
        assertEquals(3, a.diff)
        assertFalse(a.sceneDone)
        assertArrayEquals(intArrayOf(0, 1, 0), g.scores)
        assertTrue(g.found[3])
        assertEquals(1, g.foundBy[3])
        assertEquals(1, g.foundCount)
        assertSame(Phase.SEARCH, g.phase)
        assertEquals(-1, g.answerer)
        // 이미 찾은 곳을 또 누르면 오답
        g.buzz(0, 3000)
        val again = g.answer(d.cx, d.cy, 3100)
        assertTrue(again is Answer.Wrong)
        assertArrayEquals(intArrayOf(-1, 1, 0), g.scores)
        assertEquals(1, g.foundCount)
    }

    @Test
    fun `오답은 -1 그리고 2초 버저 잠금`() {
        val g = game()
        val s = g.startNextScene()
        val miss = missPoint(s)
        g.buzz(0, 10_000)
        val a = g.answer(miss[0], miss[1], 11_000)
        assertTrue(a is Answer.Wrong)
        a as Answer.Wrong
        assertFalse(a.timeout)
        assertEquals(11_000 + GameConfig.LOCK_MS, a.untilMs)
        assertEquals(-1, g.scores[0])
        assertSame(Phase.SEARCH, g.phase)
        // 잠금 중: 0번은 못 누르고 다른 사람은 된다
        assertTrue(g.isLocked(0, 12_999))
        val l = g.buzz(0, 12_000)
        assertTrue(l is Buzz.Locked)
        assertEquals(13_000L, (l as Buzz.Locked).untilMs)
        assertSame(Phase.SEARCH, g.phase)
        assertTrue(g.buzz(1, 12_100) is Buzz.Accepted)
        g.answer(s.differences[0].cx, s.differences[0].cy, 12_200)
        // 2초 지나면 풀림
        assertFalse(g.isLocked(0, 13_000))
        assertTrue(g.buzz(0, 13_000) is Buzz.Accepted)
    }

    @Test
    fun `3초 안에 못 누르면 시간 초과 오답`() {
        val g = game()
        val s = g.startNextScene()
        g.buzz(2, 5000)
        // 아직 시간 남음
        assertSame(Answer.Ignored, g.answerTimeout(5000 + GameConfig.ANSWER_MS - 1))
        assertSame(Phase.ANSWER, g.phase)
        val t = g.answerTimeout(5000 + GameConfig.ANSWER_MS)
        assertTrue(t is Answer.Wrong)
        t as Answer.Wrong
        assertTrue(t.timeout)
        assertEquals(2, t.player)
        assertEquals(-1, g.scores[2])
        assertTrue(g.isLocked(2, 5000 + GameConfig.ANSWER_MS + GameConfig.LOCK_MS - 1))
        // 이미 끝난 답 시간은 다시 처리 안 함
        assertSame(Answer.Ignored, g.answerTimeout(99_999))
        // 마감 지나서 누른 터치도 시간 초과 (정답 자리라도)
        g.buzz(1, 20_000)
        val d = s.differences[0]
        val late = g.answer(d.cx, d.cy, 20_000 + GameConfig.ANSWER_MS + 1)
        assertTrue(late is Answer.Wrong && late.timeout)
        assertFalse(g.found[0])
        // 마감 바로 그때는 인정
        g.buzz(0, 30_000)
        assertTrue(g.answer(d.cx, d.cy, 30_000 + GameConfig.ANSWER_MS) is Answer.Correct)
        // 찾는 중에 그림을 눌러도 무시
        assertSame(Answer.Ignored, g.answer(d.cx, d.cy, 40_000))
    }

    @Test
    fun `5곳을 다 찾으면 다음 장면 그리고 3장면 뒤 끝`() {
        val g = game(2)
        var now = 0L
        for (sc in 0 until GameConfig.SCENES) {
            val s = g.startNextScene()
            assertEquals(sc, g.sceneIndex)
            assertEquals(0, g.foundCount)
            assertTrue(g.foundBy.all { it == -1 })
            for (d in s.differences) {
                now += 5000
                assertTrue(g.buzz(sc % 2, now) is Buzz.Accepted)
                val a = g.answer(d.cx, d.cy, now + 500) as Answer.Correct
                assertEquals(d.index == 4, a.sceneDone)
            }
            if (sc < GameConfig.SCENES - 1) {
                assertSame(Phase.SCENE_CLEAR, g.phase)
                assertTrue(g.hasNextScene)
                assertSame("장면 사이엔 버저 무시", Buzz.Ignored, g.buzz(0, now + 1))
            }
        }
        assertSame(Phase.GAME_OVER, g.phase)
        assertTrue(g.gameOver)
        assertFalse(g.hasNextScene)
        assertArrayEquals(intArrayOf(10, 5), g.scores)
        assertArrayEquals(intArrayOf(1, 2), g.finalRanks())
        var threw = false
        try { g.startNextScene() } catch (e: IllegalStateException) { threw = true }
        assertTrue(threw)
    }

    @Test
    fun `장면 중간에는 다음 장면으로 못 넘어감`() {
        val g = game()
        g.startNextScene()
        var threw = false
        try { g.startNextScene() } catch (e: IllegalStateException) { threw = true }
        assertTrue(threw)
    }

    @Test
    fun `장면 시간이 끝나면 남은 차이 공개 (답하는 중이면 기다림)`() {
        val g = game()
        val s = g.startNextScene()
        g.buzz(0, 0)
        g.answer(s.differences[1].cx, s.differences[1].cy, 100)
        g.buzz(1, 200)
        assertNull("답하는 중엔 미룸", g.sceneTimeUp())
        assertSame(Phase.ANSWER, g.phase)
        g.answerTimeout(200 + GameConfig.ANSWER_MS)
        val revealed = g.sceneTimeUp()!!
        assertEquals(listOf(0, 2, 3, 4), revealed)
        assertEquals(SpotGame.REVEALED, g.foundBy[0])
        assertEquals(0, g.foundBy[1])
        assertEquals(5, g.foundCount)
        assertSame(Phase.SCENE_CLEAR, g.phase)
        assertArrayEquals("공개는 점수 없음", intArrayOf(1, -1, 0), g.scores)
        assertNull(g.sceneTimeUp())
        g.startNextScene()
        assertEquals(1, g.sceneIndex)
    }

    @Test
    fun `합산 점수 순위 - 동점 같은 순위, 음수 점수 허용`() {
        val g = game(4)
        val given = SceneGenerator.generate(5)
        g.startNextScene(given)
        val miss = missPoint(given)
        var now = 0L
        fun correct(p: Int, d: Int) { now += 3000; g.buzz(p, now); g.answer(given.differences[d].cx, given.differences[d].cy, now) }
        fun wrong(p: Int) { now += 3000; g.buzz(p, now); g.answer(miss[0], miss[1], now) }
        correct(0, 0)
        correct(1, 1)
        wrong(2)
        wrong(2)
        correct(3, 2)
        // 점수: 1, 1, -2, 1 → 1·1·4·1 위
        assertArrayEquals(intArrayOf(1, 1, -2, 1), g.scores)
        assertArrayEquals(intArrayOf(1, 1, 4, 1), g.finalRanks())
        wrong(3)
        // 1, 1, -2, 0 → 1·1·4·3
        assertArrayEquals(intArrayOf(1, 1, 4, 3), g.finalRanks())
    }

    @Test
    fun `잠금은 장면이 바뀌어도 게임 시간대로 이어짐`() {
        val g = game(2)
        val s = g.startNextScene()
        val miss = missPoint(s)
        // 0번이 4곳, 마지막 1곳 직전 1번이 오답
        var now = 0L
        for (d in 0 until 4) { now += 100; g.buzz(0, now); g.answer(s.differences[d].cx, s.differences[d].cy, now) }
        now += 100
        g.buzz(1, now)
        g.answer(miss[0], miss[1], now)
        val lockedAt = now
        g.buzz(0, now + 1)
        g.answer(s.differences[4].cx, s.differences[4].cy, now + 2)
        g.startNextScene()
        assertTrue(g.buzz(1, lockedAt + 500) is Buzz.Locked)
        assertTrue(g.buzz(1, lockedAt + GameConfig.LOCK_MS) is Buzz.Accepted)
    }
}
