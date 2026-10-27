package com.siwoo.party.games.digitmemo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DigitMemoGameTest {

    private fun sub(text: String, t: Long, seq: Int) = Submission(text, t, seq)

    /** 입력 단계까지 진행한 게임 (now = 1000) */
    private fun inputGame(n: Int = 4, seed: Int = 1): DigitMemoGame {
        val g = DigitMemoGame(n, Random(seed))
        g.startRound()
        g.show()
        g.startInput(1000L)
        return g
    }

    private fun typeAll(g: DigitMemoGame, i: Int, s: String) {
        for (ch in s) assertTrue(g.type(i, ch - '0'))
    }

    /** 정답과 한 자리 다른 숫자열 */
    private fun wrongOf(s: String): String = ((s[0] - '0' + 1) % 10).toString() + s.substring(1)

    // ── 숫자 기억 채점 ──

    @Test
    fun `채점 - 글자 그대로 같아야만 정답`() {
        assertTrue(DigitScoring.isCorrect("0428", "0428"))
        assertFalse("앞자리 0 빠짐", DigitScoring.isCorrect("0428", "428"))
        assertFalse("자리 바뀜", DigitScoring.isCorrect("0428", "0482"))
        assertFalse("한 자리 더", DigitScoring.isCorrect("0428", "04281"))
        assertFalse("미제출", DigitScoring.isCorrect("0428", null))
    }

    @Test
    fun `채점 - 가장 빠른 정답 2점 나머지 정답 1점 오답 0점`() {
        val subs = listOf(sub("1234", 900, 2), sub("1234", 500, 0), sub("1243", 300, 1), null)
        assertEquals(1, DigitScoring.fastest("1234", subs))
        assertArrayEquals(intArrayOf(1, 2, 0, 0), DigitScoring.score("1234", subs))
    }

    @Test
    fun `채점 - 오답이 더 빨라도 가장 빠른 정답자가 2점`() {
        val subs = listOf(sub("9999", 10, 0), sub("5555", 800, 1))
        assertEquals(1, DigitScoring.fastest("5555", subs))
        assertArrayEquals(intArrayOf(0, 2), DigitScoring.score("5555", subs))
    }

    @Test
    fun `채점 - 같은 시각이면 먼저 들어온 제출이 2점`() {
        val subs = listOf(sub("777", 500, 2), sub("777", 500, 1), sub("777", 500, 3))
        assertEquals(1, DigitScoring.fastest("777", subs))
        assertArrayEquals(intArrayOf(1, 2, 1), DigitScoring.score("777", subs))
    }

    @Test
    fun `채점 - 정답자가 없으면 모두 0점`() {
        val subs = listOf(sub("1111", 100, 0), null, sub("2222", 200, 1))
        assertEquals(-1, DigitScoring.fastest("3333", subs))
        assertArrayEquals(intArrayOf(0, 0, 0), DigitScoring.score("3333", subs))
    }

    @Test
    fun `라운드 마감 - 점수 반영과 결과`() {
        val g = inputGame(4)
        val a = g.answer
        typeAll(g, 0, a); typeAll(g, 1, a); typeAll(g, 2, wrongOf(a))
        assertTrue(g.confirm(1, 2000))
        assertTrue(g.confirm(2, 2100))
        assertTrue(g.confirm(0, 2500))
        assertFalse(g.allSubmitted)
        val r = g.closeRound()
        assertSame(Phase.REVEAL, g.phase)
        assertEquals(1, r.fastest)
        assertArrayEquals(intArrayOf(1, 2, 0, 0), r.points)
        assertArrayEquals(booleanArrayOf(true, true, false, false), r.correct)
        assertNull("미제출", r.submissions[3])
        assertArrayEquals(intArrayOf(1, 2, 0, 0), g.scores)
        assertSame(r, g.lastResult)
    }

    @Test
    fun `라운드 마감 - 같은 시각 제출은 들어온 순서로`() {
        val g = inputGame(3)
        val a = g.answer
        for (i in 0 until 3) typeAll(g, i, a)
        assertTrue(g.confirm(2, 1500))
        assertTrue(g.confirm(0, 1500))
        assertTrue(g.confirm(1, 1500))
        assertTrue(g.allSubmitted)
        assertArrayEquals(intArrayOf(1, 1, 2), g.closeRound().points)
    }

    // ── 숫자열 생성 ──

    @Test
    fun `라운드별 자릿수는 4자리부터 1자리씩 11자리까지`() {
        assertEquals(8, GameConfig.ROUNDS)
        val expected = (4..11).toList()
        assertEquals(expected, (1..GameConfig.ROUNDS).map { DigitMemoGame.digitsFor(it) })
        val g = DigitMemoGame(2, Random(7))
        val lens = ArrayList<Int>()
        repeat(GameConfig.ROUNDS) {
            val s = g.startRound()
            lens.add(s.length)
            assertEquals(s, g.answer)
            assertTrue("숫자만: $s", s.all { it in '0'..'9' })
            g.show()
            g.startInput(0L)
            g.closeRound()
        }
        assertEquals(expected, lens)
        assertTrue(g.gameOver)
    }

    @Test
    fun `숫자열은 난수로 만들어지고 0에서 9까지 고루 나온다`() {
        val seen = BooleanArray(10)
        val r = Random(3)
        repeat(50) { for (ch in DigitMemoGame.generate(8, r)) seen[ch - '0'] = true }
        assertTrue(seen.all { it })
        assertNotEquals(DigitMemoGame.generate(11, Random(1)), DigitMemoGame.generate(11, Random(2)))
        assertEquals(DigitMemoGame.generate(11, Random(5)), DigitMemoGame.generate(11, Random(5)))
    }

    @Test(expected = IllegalStateException::class)
    fun `8라운드 다음은 없다`() {
        val g = DigitMemoGame(2, Random(1))
        repeat(GameConfig.ROUNDS) { g.startRound(); g.show(); g.startInput(0); g.closeRound() }
        g.startRound()
    }

    // ── 입력 ──

    @Test
    fun `보이는 동안에는 입력 불가 - 입력은 자릿수까지만`() {
        val g = DigitMemoGame(2, Random(1))
        g.startRound()
        assertSame(Phase.READY, g.phase)
        assertFalse(g.type(0, 3))
        g.show()
        assertSame(Phase.SHOWING, g.phase)
        assertFalse(g.type(0, 3))
        g.startInput(0)
        repeat(g.digits) { assertTrue(g.type(0, 3)) }
        assertFalse("자릿수 초과", g.type(0, 3))
        assertEquals("3333", g.input(0))
        assertTrue(g.backspace(0))
        assertEquals("333", g.input(0))
        assertFalse("다 안 채우면 확인 불가", g.canConfirm(0))
        assertFalse(g.confirm(0, 10))
        assertFalse(g.type(0, 10))
        assertEquals("", g.input(1))
        assertFalse("빈 칸 지우기", g.backspace(1))
    }

    @Test
    fun `제출은 라운드마다 한 번 - 제출 뒤에는 고칠 수 없음`() {
        val g = inputGame(2)
        typeAll(g, 0, g.answer)
        assertTrue(g.confirm(0, 1200))
        assertTrue(g.isSubmitted(0))
        assertFalse("두 번째 제출", g.confirm(0, 1300))
        assertFalse(g.backspace(0))
        assertFalse(g.type(0, 1))
        assertEquals(1200L, g.submission(0)!!.timeMs)
        assertEquals(0, g.submission(0)!!.seq)
        assertEquals(1, g.submittedCount)
        g.closeRound()
        // 다음 라운드엔 다시 낼 수 있음
        g.startRound(); g.show(); g.startInput(5000)
        assertFalse(g.isSubmitted(0))
        assertEquals("", g.input(0))
        typeAll(g, 0, g.answer)
        assertTrue(g.confirm(0, 5100))
    }

    @Test
    fun `제한 시간 - 기본 12초에 자리당 1초 - 마감 뒤 제출은 받지 않음`() {
        assertEquals(16_000L, DigitMemoGame.timeLimitMs(4))
        assertEquals(23_000L, DigitMemoGame.timeLimitMs(11))
        val g = inputGame(2)
        val limit = DigitMemoGame.timeLimitMs(4)
        assertEquals(1000L + limit, g.deadline)
        assertEquals(limit, g.timeLeft(1000))
        assertFalse(g.isTimeUp(1000 + limit - 1))
        assertTrue(g.isTimeUp(1000 + limit))
        assertEquals(0L, g.timeLeft(1000 + limit + 500))
        typeAll(g, 0, g.answer)
        typeAll(g, 1, g.answer)
        assertTrue("마감 직전", g.confirm(1, 1000 + limit - 1))
        assertFalse("마감 시각", g.confirm(0, 1000 + limit))
        val r = g.closeRound()
        assertArrayEquals(intArrayOf(0, 2), r.points)
        assertEquals(0L, g.timeLeft(99_999))
    }

    @Test
    fun `입력 시작보다 이른 시각은 시작 시각으로`() {
        val g = inputGame(2)
        typeAll(g, 0, g.answer)
        assertTrue(g.confirm(0, 500))
        assertEquals(1000L, g.submission(0)!!.timeMs)
    }

    // ── 순위 ──

    @Test
    fun `순위 - 점수 순 경쟁 순위 동점 같은 순위`() {
        assertArrayEquals(intArrayOf(2, 1, 4, 2), DigitMemoGame.ranksOf(intArrayOf(5, 9, 1, 5)))
        assertArrayEquals(intArrayOf(1, 1), DigitMemoGame.ranksOf(intArrayOf(0, 0)))
    }

    @Test
    fun `순위 - 8라운드 끝까지 진행`() {
        val g = DigitMemoGame(3, Random(11))
        var t = 0L
        for (r in 1..GameConfig.ROUNDS) {
            g.startRound()
            g.show()
            g.startInput(t)
            // 0번은 늘 가장 빠른 정답, 1번은 홀수 라운드만 정답, 2번은 늘 오답
            typeAll(g, 0, g.answer)
            typeAll(g, 1, if (r % 2 == 1) g.answer else wrongOf(g.answer))
            typeAll(g, 2, wrongOf(g.answer))
            assertTrue(g.confirm(0, t + 100))
            assertTrue(g.confirm(1, t + 200))
            assertTrue(g.confirm(2, t + 50))
            assertFalse(g.gameOver)
            g.closeRound()
            t += 30_000
        }
        assertTrue(g.gameOver)
        assertArrayEquals(intArrayOf(16, 4, 0), g.scores)
        assertArrayEquals(intArrayOf(1, 2, 3), g.ranks())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `인원은 2명에서 4명`() {
        DigitMemoGame(5)
    }

    // ── 배치 ──

    @Test
    fun `패드 배치 - 키 12개가 겹치지 않고 판정이 맞음`() {
        val l = DigitLayout()
        l.layout(300f, 160f)
        for (k in 0 until DigitLayout.KEY_COUNT) {
            val b = l.keys[k]
            assertTrue(b.width > 0 && b.height > 0)
            assertEquals("키 $k 가운데", k, l.keyAt(b.centerX, b.centerY))
            assertTrue("칸 안", b.left >= -150f && b.right <= 150f && b.top >= -80f && b.bottom <= 80f)
            for (j in 0 until k) {
                val o = l.keys[j]
                assertFalse("$j/$k 겹침", b.left < o.right && o.left < b.right && b.top < o.bottom && o.top < b.bottom)
            }
        }
        assertEquals(-1, l.keyAt(0f, l.slotRow.centerY))
        assertTrue("숫자 칸은 패드 위", l.slotRow.bottom < l.pad.top)
        // 11자리 칸도 칸 줄 안에 들어감
        val s = Box()
        l.slot(0, 11, s)
        assertTrue(s.left >= l.slotRow.left - 0.01f)
        l.slot(10, 11, s)
        assertTrue(s.right <= l.slotRow.right + 0.01f)
    }

    @Test
    fun `좌표 회전 - 180도 구역은 뒤집혀서 판정`() {
        val out = FloatArray(2)
        DigitLayout.toLocal(100f, 100f, 180f, 130f, 80f, out)
        assertEquals(-30f, out[0], 0.01f)
        assertEquals(20f, out[1], 0.01f)
        DigitLayout.toScreen(100f, 100f, 180f, out[0], out[1], out)
        assertEquals(130f, out[0], 0.01f)
        assertEquals(80f, out[1], 0.01f)
    }
}
