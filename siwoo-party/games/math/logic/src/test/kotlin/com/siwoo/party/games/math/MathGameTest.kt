package com.siwoo.party.games.math

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MathGameTest {

    /** 보기 공통 검사: 3개, 서로 다름, 정답 포함, 0 이상, 오답은 정답 가까이 */
    private fun checkChoices(q: Question) {
        assertEquals(GameConfig.CHOICES, q.choices.size)
        assertEquals("서로 다른 보기 ${q.choices.toList()}", GameConfig.CHOICES, q.choices.toSet().size)
        assertTrue("정답 포함", q.answer in q.choices)
        assertEquals(q.answer, q.choices[q.correctIndex])
        for (c in q.choices) {
            assertTrue("0 이상 ${q.text} ${q.choices.toList()}", c >= 0)
            assertTrue("정답 가까이 ${q.text} $c", abs(c - q.answer) <= GameConfig.NEAR_MAX)
        }
    }

    @Test
    fun `1단계 - 한 자리 덧셈만 정답 검증`() {
        val gen = QuestionGen(Random(1))
        repeat(2000) {
            val q = gen.generate(1)
            assertSame(Op.ADD, q.op)
            assertTrue(q.a in 1..9 && q.b in 1..9)
            assertEquals(q.a + q.b, q.answer)
            assertEquals(1, q.stage)
            checkChoices(q)
        }
    }

    @Test
    fun `2단계 - 두 자리 덧셈 뺄셈 정답 검증`() {
        val gen = QuestionGen(Random(2))
        val ops = HashSet<Op>()
        repeat(2000) {
            val q = gen.generate(2)
            ops.add(q.op)
            assertTrue("두 자리 ${q.text}", q.a in 10..99 && q.b in 10..99)
            when (q.op) {
                Op.ADD -> assertEquals(q.a + q.b, q.answer)
                Op.SUB -> { assertEquals(q.a - q.b, q.answer); assertTrue("답 양수 ${q.text}", q.answer > 0) }
                Op.MUL -> error("2단계에 곱셈 ${q.text}")
            }
            checkChoices(q)
        }
        assertEquals(setOf(Op.ADD, Op.SUB), ops)
    }

    @Test
    fun `3단계 - 곱셈 섞기 정답 검증`() {
        val gen = QuestionGen(Random(3))
        val ops = HashMap<Op, Int>()
        repeat(3000) {
            val q = gen.generate(3)
            ops[q.op] = (ops[q.op] ?: 0) + 1
            assertEquals(3, q.stage)
            assertEquals(q.op.apply(q.a, q.b), q.answer)
            if (q.op == Op.MUL) {
                assertEquals(q.a * q.b, q.answer)
                val table = q.a in 2..9 && q.b in 2..9
                val big = q.a in 11..19 && q.b in 2..5
                assertTrue("곱셈 범위 ${q.text}", table || big)
            }
            checkChoices(q)
        }
        assertTrue("곱셈이 대부분 $ops", (ops[Op.MUL] ?: 0) > 1500)
        assertTrue("덧뺄셈도 섞임 $ops", (ops[Op.ADD] ?: 0) > 0 && (ops[Op.SUB] ?: 0) > 0)
        // 곱셈 고정
        repeat(200) { assertSame(Op.MUL, gen.generate(3, forceMul = true).op) }
    }

    @Test
    fun `한 판 - 단계가 점점 오르고 같은 문제 없음`() {
        for (seed in 0 until 300) {
            val set = QuestionGen(Random(seed)).makeSet()
            assertEquals(GameConfig.MATH_QUESTIONS, set.size)
            val stages = set.map { it.stage }
            assertEquals(listOf(1, 1, 1, 2, 2, 2, 3, 3, 3, 3), stages)
            assertEquals("같은 문제 없음", set.size, set.map { it.text }.toSet().size)
            assertSame("3단계 첫 문제는 곱셈", Op.MUL, set[GameConfig.STAGE2_LAST].op)
            for (q in set) checkChoices(q)
        }
    }

    @Test
    fun `정답 위치가 고르게 섞임`() {
        val gen = QuestionGen(Random(9))
        val pos = IntArray(GameConfig.CHOICES)
        repeat(3000) { pos[gen.generate(1 + it % 3).correctIndex]++ }
        for (n in pos) assertTrue("정답 위치 ${pos.toList()}", n > 800)
    }

    @Test
    fun `같은 seed 면 같은 문제`() {
        val a = QuestionGen(Random(42)).makeSet().map { it.text + it.choices.toList() }
        val b = QuestionGen(Random(42)).makeSet().map { it.text + it.choices.toList() }
        assertEquals(a, b)
    }

    private fun fixed(): List<Question> = List(GameConfig.MATH_QUESTIONS) { Question(1, 2, Op.ADD, 3, intArrayOf(4, 5, 6)) }

    @Test
    fun `먼저 맞힌 사람만 +1 그 뒤는 무시`() {
        val g = MathGame(3, questions = fixed())
        g.nextQuestion()
        assertEquals(1, g.current.correctIndex)
        assertSame(Answer.CORRECT, g.answer(2, 1, 1000))
        assertEquals(2, g.winner)
        assertSame(Phase.CLOSED, g.phase)
        // 같은 순간 늦게 들어온 정답·오답은 무시
        assertSame(Answer.IGNORED, g.answer(0, 1, 1000))
        assertSame(Answer.IGNORED, g.answer(1, 0, 1001))
        assertArrayEquals(intArrayOf(0, 0, 1), g.scores)
        assertEquals(0L, g.lockUntil[1])
    }

    @Test
    fun `틀리면 1점5초 잠금 그 사람만`() {
        val g = MathGame(2, questions = fixed())
        g.nextQuestion()
        assertSame(Answer.WRONG, g.answer(0, 0, 5000))
        assertTrue(g.isLocked(0, 5000))
        assertEquals(1, g.wrongMask[0])
        assertSame("잠금 중엔 정답도 무시", Answer.LOCKED, g.answer(0, 1, 5000 + GameConfig.MATH_LOCK_MS - 1))
        assertEquals(0, g.scores[0])
        // 다른 사람은 잠기지 않음
        assertFalse(g.isLocked(1, 5000))
        assertSame(Answer.WRONG, g.answer(1, 2, 5600))
        // 1.5초 지나면 다시 누를 수 있음
        assertFalse(g.isLocked(0, 5000 + GameConfig.MATH_LOCK_MS))
        assertSame(Answer.CORRECT, g.answer(0, 1, 5000 + GameConfig.MATH_LOCK_MS))
        assertArrayEquals(intArrayOf(1, 0), g.scores)
    }

    @Test
    fun `새 문제가 나오면 잠금 풀림`() {
        val g = MathGame(2, questions = fixed())
        g.nextQuestion()
        g.answer(0, 0, 1000)
        g.timeUp()
        assertSame(Phase.CLOSED, g.phase)
        assertEquals(-1, g.winner)
        g.nextQuestion()
        assertFalse(g.isLocked(0, 1100))
        assertEquals(0, g.wrongMask[0])
        assertSame(Answer.CORRECT, g.answer(0, 1, 1100))
    }

    @Test
    fun `시간 끝 - 점수 없이 문제 닫힘`() {
        val g = MathGame(2, questions = fixed())
        assertSame("시작 전 무시", Answer.IGNORED, g.answer(0, 1, 0))
        g.nextQuestion()
        g.timeUp()
        assertSame(Answer.IGNORED, g.answer(0, 1, 10))
        assertArrayEquals(intArrayOf(0, 0), g.scores)
    }

    @Test
    fun `열 문제 끝 순위 - 점수 순 동점 같은 순위`() {
        val g = MathGame(4, Random(5))
        // 맞힐 사람: 0,0,0,1,1,1,2,2,-,3 → 점수 3,3,2,1
        val who = intArrayOf(0, 0, 0, 1, 1, 1, 2, 2, -1, 3)
        var t = 0L
        for (n in 0 until GameConfig.MATH_QUESTIONS) {
            val q = g.nextQuestion()
            assertEquals(n + 1, g.number)
            t += 3000
            if (who[n] < 0) g.timeUp() else {
                // 다른 사람이 먼저 틀림
                val wrong = (q.correctIndex + 1) % GameConfig.CHOICES
                assertSame(Answer.WRONG, g.answer((who[n] + 1) % 4, wrong, t))
                assertSame(Answer.CORRECT, g.answer(who[n], q.correctIndex, t + 10))
            }
        }
        assertTrue(g.gameOver)
        assertFalse(g.hasNext)
        assertArrayEquals(intArrayOf(3, 3, 2, 1), g.scores)
        assertArrayEquals(intArrayOf(1, 1, 3, 4), g.ranks())
    }

    @Test
    fun `모두 0점이면 모두 1위`() {
        val g = MathGame(3, Random(1))
        repeat(GameConfig.MATH_QUESTIONS) { g.nextQuestion(); g.timeUp() }
        assertTrue(g.gameOver)
        assertArrayEquals(intArrayOf(1, 1, 1), g.ranks())
    }

    @Test
    fun `배치 - 보기 3개 겹치지 않고 위쪽 구역은 돌려서 판정`() {
        val l = MathLayout()
        l.layout(598f, 318f)
        for (k in 0 until 2) assertTrue("보기 사이 틈", l.choices[k].right < l.choices[k + 1].left)
        assertTrue(l.choices[0].left >= -l.cellW / 2 && l.choices[2].right <= l.cellW / 2)
        assertTrue(l.choices[0].bottom <= l.cellH / 2)
        assertTrue("문제 글자 자리는 시간 막대와 보기 사이", l.questionY > l.timeBar.bottom && l.questionY < l.choices[0].top)
        val out = FloatArray(2)
        val back = FloatArray(2)
        // 위쪽 구역(180°): 사람 기준 왼쪽 보기 = 화면 오른쪽
        val c0 = l.choices[0]
        MathLayout.toScreen(320f, 170f, 180f, c0.centerX, c0.centerY, out)
        assertTrue(out[0] > 320f)
        MathLayout.toLocal(320f, 170f, 180f, out[0], out[1], back)
        assertEquals(0, l.choiceAt(back[0], back[1]))
        // 같은 화면 자리를 돌리지 않고(0°) 보면 보기가 아님 (문제 위쪽)
        MathLayout.toLocal(320f, 170f, 0f, out[0], out[1], back)
        assertEquals(-1, l.choiceAt(back[0], back[1]))
        // 위쪽 구역에서 사람 기준 오른쪽 보기 = 화면 왼쪽
        MathLayout.toScreen(320f, 170f, 180f, l.choices[2].centerX, l.choices[2].centerY, out)
        assertTrue(out[0] < 320f)
        MathLayout.toLocal(320f, 170f, 180f, out[0], out[1], back)
        assertEquals(2, l.choiceAt(back[0], back[1]))
        // 보기 사이 틈은 -1
        assertEquals(-1, l.choiceAt((l.choices[0].right + l.choices[1].left) / 2f, c0.centerY))
    }
}
