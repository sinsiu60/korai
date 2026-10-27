package com.siwoo.party.games.stroop

import com.siwoo.party.common.TouchZones
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StroopGameTest {

    @Test fun `글자 색 규칙이면 칠해진 색이 정답`() {
        val g = StroopGame(2, Random(1))
        val q = g.ask(Hue.RED, Hue.BLUE, Rule.INK)
        assertSame(Hue.BLUE, q.answer)
        val wrong = g.press(0, Hue.RED, 0) // 글자 뜻을 누름 → 오답
        assertTrue(wrong is Press.Wrong)
        val right = g.press(1, Hue.BLUE, 10)
        assertTrue(right is Press.Correct)
        assertArrayEquals(intArrayOf(-1, 1), g.scores)
    }

    @Test fun `글자 뜻 규칙이면 글자가 뜻하는 색이 정답`() {
        val g = StroopGame(2, Random(1))
        val q = g.ask(Hue.GREEN, Hue.YELLOW, Rule.WORD)
        assertSame(Hue.GREEN, q.answer)
        assertTrue(g.press(0, Hue.YELLOW, 0) is Press.Wrong) // 칠해진 색 → 오답
        assertTrue(g.press(1, Hue.GREEN, 5) is Press.Correct)
    }

    @Test fun `먼저 맞힌 사람만 +1, 그 문제는 바로 끝`() {
        val g = StroopGame(4, Random(2))
        g.ask(Hue.BLUE, Hue.RED, Rule.INK)
        val r = g.press(2, Hue.RED, 100)
        assertTrue(r is Press.Correct)
        assertEquals(2, (r as Press.Correct).player)
        assertEquals(Phase.IDLE, g.phase)
        assertEquals(2, g.questionWinner)
        assertEquals(1, g.questionsDone)
        // 늦게 누른 정답·오답 모두 무시, 점수 변화 없음
        assertSame(Press.Ignored, g.press(0, Hue.RED, 110))
        assertSame(Press.Ignored, g.press(1, Hue.GREEN, 120))
        assertArrayEquals(intArrayOf(0, 0, 1, 0), g.scores)
        assertFalse("오답 무시면 잠금도 없음", g.isLocked(1, 130))
    }

    @Test fun `오답은 -1 그리고 1초 잠금, 잠금 중 입력은 무시`() {
        val g = StroopGame(3, Random(3))
        g.ask(Hue.YELLOW, Hue.GREEN, Rule.WORD) // 정답 노랑
        val w = g.press(0, Hue.RED, 1000)
        assertTrue(w is Press.Wrong)
        assertEquals(1000 + GameConfig.WRONG_LOCK_MS, (w as Press.Wrong).untilMs)
        assertEquals(-1, g.scores[0])
        assertTrue(g.isLocked(0, 1500))
        // 잠금 중엔 정답을 눌러도 무시, 점수도 그대로
        assertTrue(g.press(0, Hue.YELLOW, 1500) is Press.Locked)
        assertTrue(g.press(0, Hue.RED, 1999) is Press.Locked)
        assertEquals(-1, g.scores[0])
        assertEquals(Phase.ASKING, g.phase)
        // 다른 사람은 잠기지 않음
        assertFalse(g.isLocked(1, 1500))
        // 1초 지나면 다시 누를 수 있음
        assertFalse(g.isLocked(0, 2000))
        assertTrue(g.press(0, Hue.YELLOW, 2000) is Press.Correct)
        assertEquals(0, g.scores[0])
    }

    @Test fun `잠금은 다음 문제까지 이어진다`() {
        val g = StroopGame(2, Random(3))
        g.ask(Hue.RED, Hue.BLUE, Rule.INK)
        g.press(0, Hue.GREEN, 0)
        g.press(1, Hue.BLUE, 100)
        g.ask(Hue.GREEN, Hue.RED, Rule.WORD)
        assertTrue(g.press(0, Hue.GREEN, 500) is Press.Locked)
        assertTrue(g.press(0, Hue.GREEN, 1000) is Press.Correct)
    }

    @Test fun `오답을 여러 번 하면 점수가 음수가 될 수 있다`() {
        val g = StroopGame(2)
        g.ask(Hue.RED, Hue.BLUE, Rule.INK)
        g.press(0, Hue.RED, 0)
        g.press(0, Hue.GREEN, 1000)
        g.press(0, Hue.YELLOW, 2000)
        assertEquals(-3, g.scores[0])
    }

    @Test fun `시간 초과는 점수 없이 넘어감`() {
        val g = StroopGame(2)
        g.ask(Hue.RED, Hue.BLUE, Rule.INK)
        assertTrue(g.timeout())
        assertEquals(1, g.questionsDone)
        assertEquals(-1, g.questionWinner)
        assertFalse("두 번 끝나지 않음", g.timeout())
        assertSame(Press.Ignored, g.press(0, Hue.BLUE, 0))
        assertArrayEquals(intArrayOf(0, 0), g.scores)
    }

    @Test fun `문제 생성 - 글자와 색은 항상 다르고 두 규칙이 모두 나온다, 15문제 뒤 끝`() {
        val g = StroopGame(2, Random(7))
        val rules = HashSet<Rule>()
        var prev: Question? = null
        var n = 0
        while (!g.gameOver) {
            val q = g.nextQuestion()
            n++
            assertEquals(n, q.number)
            assertNotEquals(q.word, q.ink)
            prev?.let { assertNotEquals("같은 글자 연속 안 나옴", it.word, q.word) }
            rules.add(q.rule)
            prev = q
            g.timeout()
        }
        assertEquals(GameConfig.STROOP_QUESTIONS, n)
        assertEquals(2, rules.size)
        try {
            g.nextQuestion(); error("끝난 뒤 문제가 나오면 안 됨")
        } catch (e: IllegalStateException) { /* 정상 */ }
        // 많이 뽑으면 두 규칙이 비슷하게
        var ink = 0
        repeat(200) {
            val gg = StroopGame(2, Random(it))
            if (gg.nextQuestion().rule == Rule.INK) ink++
        }
        assertTrue(ink in 70..130)
    }

    @Test fun `순위 - 점수 순, 동점은 같은 순위`() {
        val g = StroopGame(4)
        // 0: +2, 1: +2, 2: -1, 3: 0
        g.ask(Hue.RED, Hue.BLUE, Rule.INK); g.press(0, Hue.BLUE, 0)
        g.ask(Hue.RED, Hue.BLUE, Rule.INK); g.press(1, Hue.BLUE, 0)
        g.ask(Hue.RED, Hue.BLUE, Rule.WORD); g.press(2, Hue.BLUE, 0); g.press(0, Hue.RED, 10)
        g.ask(Hue.GREEN, Hue.BLUE, Rule.WORD); g.press(1, Hue.GREEN, 0)
        assertArrayEquals(intArrayOf(2, 2, -1, 0), g.scores)
        assertArrayEquals(intArrayOf(1, 1, 4, 3), g.finalRanks())
    }

    @Test fun `구역 좌표 - 위쪽 구역은 180도 돌아 있다`() {
        val zs = TouchZones.layout(4, 1280f, 720f, 20f)
        val out = FloatArray(2)
        val top = zs[0]
        // 플레이어 기준 왼쪽 위 (10, 10) → 화면에서는 구역의 오른쪽 아래
        ZoneFrame.toScreen(top, 10f, 10f, out)
        assertEquals(top.right - 10f, out[0], 0.01f)
        assertEquals(top.bottom - 10f, out[1], 0.01f)
        ZoneFrame.toLocal(top, out[0], out[1], out)
        assertEquals(10f, out[0], 0.01f)
        assertEquals(10f, out[1], 0.01f)
        // 아래 구역은 그대로 (평행 이동만)
        val bottom = zs[3]
        ZoneFrame.toScreen(bottom, 10f, 10f, out)
        assertEquals(bottom.left + 10f, out[0], 0.01f)
        assertEquals(bottom.top + 10f, out[1], 0.01f)
    }
}
