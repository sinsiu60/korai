package com.siwoo.party.games.bobber

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BobberGameTest {

    /** 가짜 입질이 있는 라운드가 나올 때까지 (점수 없이) 넘긴다 */
    private fun roundWithFake(g: BobberGame): BiteSchedule {
        while (true) {
            val s = g.startRound()
            if (s.fakeCount > 0) return s
            g.resolve()
        }
    }

    @Test
    fun `가짜 입질 중 터치는 그 라운드만 실격`() {
        val g = BobberGame(3, Random(7))
        val s = roundWithFake(g)
        val before = g.roundsDone
        val r = g.touch(1, s.fakeAt[0] + 10)
        assertTrue(r is Tap.Early)
        r as Tap.Early
        assertTrue("가짜 입질 중", r.duringFake)
        assertFalse(r.allOut)
        assertTrue(g.disqualified[1])
        // 실격자는 진짜 입질 뒤에 눌러도 무시
        assertSame(Tap.Ignored, g.touch(1, s.biteAt + 50))
        // 다른 사람은 그대로 진행
        assertTrue(g.touch(0, s.biteAt + 300) is Tap.Valid)
        val res = g.resolve()
        assertArrayEquals(intArrayOf(0), res.winners)
        assertEquals(1, g.score[0])
        assertEquals(0, g.score[1])
        assertEquals(1, g.earlyCount[1])
        // 다음 라운드엔 실격이 풀린다
        val s2 = g.startRound()
        assertFalse(g.disqualified[1])
        assertTrue(g.touch(1, s2.biteAt + 100) is Tap.Valid)
        assertEquals(before + 1, g.roundsDone)
    }

    @Test
    fun `아무 일 없을 때 미리 눌러도 실격 (가짜 입질 아님)`() {
        val g = BobberGame(2, Random(1))
        val s = g.startRound()
        val r = g.touch(0, 100) // 첫 사건 전 (FIRST_MIN_MS 이전)
        assertTrue(r is Tap.Early)
        assertFalse((r as Tap.Early).duringFake)
        // 진짜 입질 1ms 전도 실격
        val r2 = g.touch(1, s.biteAt - 1)
        assertTrue(r2 is Tap.Early)
        assertTrue("둘 다 실격", (r2 as Tap.Early).allOut)
        val res = g.resolve()
        assertEquals(Outcome.ALL_OUT, res.outcome)
        assertEquals(0, res.winners.size)
        assertArrayEquals(intArrayOf(0, 0), g.score)
        assertEquals("전원 실격도 라운드로 센다", 1, g.roundsDone)
    }

    @Test
    fun `진짜 입질 순간 누르면 정상, 반응 시간 0`() {
        val g = BobberGame(2, Random(2))
        val s = g.startRound()
        val r = g.touch(0, s.biteAt)
        assertTrue(r is Tap.Valid)
        assertEquals(0L, (r as Tap.Valid).reactionMs)
        assertTrue(r.first)
    }

    @Test
    fun `첫 정상 터치가 이기고 거의 동시 터치는 시각으로 가린다`() {
        val g = BobberGame(4, Random(3))
        val s = g.startRound()
        // 처리 순서는 3, 0, 1, 2 지만 터치 시각은 1 이 가장 빠르다
        val a = g.touch(3, s.biteAt + 260) as Tap.Valid
        assertTrue(a.first)
        val b = g.touch(0, s.biteAt + 240) as Tap.Valid
        assertFalse(b.first)
        g.touch(1, s.biteAt + 231)
        g.touch(2, s.biteAt + 400)
        assertTrue(g.allResponded())
        val res = g.resolve()
        assertEquals(Outcome.CAUGHT, res.outcome)
        assertArrayEquals(intArrayOf(1), res.winners)
        assertEquals(231L, res.bestMs)
        assertArrayEquals(intArrayOf(0, 1, 0, 0), g.score)
        assertEquals(231L, g.caughtMs[1])
        // 두 번 누르면 무시
        assertSame(Tap.Ignored, g.touch(1, s.biteAt + 500))
    }

    @Test
    fun `같은 ms 동시 터치는 둘 다 한 마리씩`() {
        val g = BobberGame(3, Random(4))
        val s = g.startRound()
        g.touch(0, s.biteAt + 200)
        g.touch(2, s.biteAt + 200)
        g.touch(1, s.biteAt + 201)
        val res = g.resolve()
        assertArrayEquals(intArrayOf(0, 2), res.winners)
        assertArrayEquals(intArrayOf(1, 0, 1), g.score)
    }

    @Test
    fun `1점5초 안에 아무도 안 누르면 점수 없음`() {
        val g = BobberGame(2, Random(5))
        val s = g.startRound()
        assertEquals(s.biteAt + GameConfig.BITE_WINDOW_MS, s.endAt)
        // 시간이 지난 터치는 무시
        assertSame(Tap.Ignored, g.touch(0, s.endAt + 1))
        val res = g.resolve()
        assertEquals(Outcome.NO_TAP, res.outcome)
        assertEquals(-1L, res.bestMs)
        assertArrayEquals(intArrayOf(0, 0), g.score)
        assertEquals(1, g.roundsDone)
        // 마지막 순간(정확히 1.5초)은 인정
        val s2 = g.startRound()
        assertTrue(g.touch(1, s2.endAt) is Tap.Valid)
        assertArrayEquals(intArrayOf(1), g.resolve().winners)
    }

    @Test
    fun `예정표는 씨앗이 같으면 똑같다`() {
        val a = BobberGame(2, Random(42))
        val b = BobberGame(4, Random(42))
        repeat(GameConfig.ROUNDS) {
            val sa = a.startRound()
            val sb = b.startRound()
            assertArrayEquals(sa.fakeAt, sb.fakeAt)
            assertArrayEquals(sa.fakeKind, sb.fakeKind)
            assertEquals(sa.biteAt, sb.biteAt)
            a.resolve(); b.resolve()
        }
        // 다른 씨앗이면 (거의 항상) 다르다
        val c = BiteSchedule.generate(Random(43))
        val d = BiteSchedule.generate(Random(42))
        assertTrue(c.biteAt != d.biteAt || !c.fakeAt.contentEquals(d.fakeAt))
    }

    @Test
    fun `예정표 범위와 순서`() {
        val r = Random(9)
        val counts = IntArray(GameConfig.FAKES_MAX + 1)
        repeat(500) {
            val s = BiteSchedule.generate(r)
            assertTrue(s.fakeCount in GameConfig.FAKES_MIN..GameConfig.FAKES_MAX)
            counts[s.fakeCount]++
            var prevEnd = 0L
            for (k in 0 until s.fakeCount) {
                assertTrue(s.fakeAt[k] >= prevEnd)
                if (k == 0) assertTrue(s.fakeAt[0] in GameConfig.FIRST_MIN_MS..GameConfig.FIRST_MAX_MS)
                prevEnd = s.fakeAt[k] + GameConfig.FAKE_MS + GameConfig.GAP_MIN_MS
                assertTrue(s.fakeKind[k] == FakeKind.DIP || s.fakeKind[k] == FakeKind.WOBBLE)
            }
            assertTrue("진짜 입질은 마지막 가짜 입질 뒤", s.biteAt >= prevEnd)
            if (s.fakeCount == 0) assertTrue(s.biteAt in GameConfig.FIRST_MIN_MS..GameConfig.FIRST_MAX_MS)
            assertFalse(s.isFakeAt(s.biteAt))
        }
        for (n in 0..GameConfig.FAKES_MAX) assertTrue("가짜 입질 ${n}번도 나온다", counts[n] > 0)
    }

    @Test
    fun `찌 움직임은 시각만의 함수 - 모든 구역 같은 모습`() {
        val s = BiteSchedule(longArrayOf(1000, 2500), intArrayOf(FakeKind.DIP, FakeKind.WOBBLE), 4000)
        val copy = BiteSchedule(longArrayOf(1000, 2500), intArrayOf(FakeKind.DIP, FakeKind.WOBBLE), 4000)
        for (t in 0L..6000L step 7) {
            assertEquals(s.sinkAt(t), copy.sinkAt(t), 0f)
            assertEquals(s.tiltAt(t), copy.tiltAt(t), 0f)
        }
        // 기다릴 땐 아주 살짝만
        assertTrue(kotlin.math.abs(s.sinkAt(500)) <= GameConfig.IDLE_BOB + 1e-4f)
        assertEquals(0f, s.tiltAt(500), 0f)
        // 톡톡 가짜 입질: 살짝 잠기지만 진짜 입질만큼은 아님
        val dip = (1000L until 1000L + GameConfig.FAKE_MS).maxOf { s.sinkAt(it) }
        assertTrue(dip > 0.15f && dip < 0.5f)
        // 흔들 가짜 입질: 기울어짐
        val tilt = (2500L until 2500L + GameConfig.FAKE_MS).maxOf { kotlin.math.abs(s.tiltAt(it)) }
        assertTrue(tilt > 0.5f)
        assertEquals(0, s.fakeIndexAt(1100))
        assertEquals(1, s.fakeIndexAt(2600))
        assertEquals(-1, s.fakeIndexAt(2000))
        // 진짜 입질: 쑥 잠겨서 그대로
        assertFalse(s.isBitten(3999))
        assertTrue(s.isBitten(4000))
        assertEquals(1f, s.sinkAt(4000 + GameConfig.PLUNGE_MS), 1e-4f)
        assertEquals(1f, s.sinkAt(5000), 1e-4f)
        assertEquals(0f, s.tiltAt(5000), 0f)
    }

    @Test
    fun `10라운드로 끝나고 순위는 물고기 수 (동점 허용)`() {
        val g = BobberGame(4, Random(11))
        assertEquals(1, g.roundNumber)
        // 라운드마다 이기는 사람: 0 이 4번, 1 이 4번, 2 가 1번, 1번은 아무도 안 누름
        val plan = intArrayOf(0, 1, 0, 1, 2, 0, 1, -1, 0, 1)
        for ((idx, w) in plan.withIndex()) {
            assertFalse(g.gameOver)
            assertEquals("라운드 사이엔 다음 라운드 번호", idx + 1, g.roundNumber)
            val s = g.startRound()
            assertEquals(idx + 1, g.roundNumber)
            if (w >= 0) {
                g.touch(w, s.biteAt + 180)
                g.touch(3, s.biteAt + 400)
            }
            g.resolve()
        }
        assertTrue(g.gameOver)
        assertEquals(GameConfig.ROUNDS, g.roundsDone)
        assertEquals(10, g.roundNumber)
        assertArrayEquals(intArrayOf(4, 4, 1, 0), g.score)
        assertArrayEquals(intArrayOf(1, 1, 3, 4), g.finalRanks())
        try {
            g.startRound()
            error("10라운드 뒤에는 시작할 수 없어야 함")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `라운드 밖 터치는 무시`() {
        val g = BobberGame(2, Random(12))
        assertSame(Tap.Ignored, g.touch(0, 99_999))
        val s = g.startRound()
        assertSame(Tap.Ignored, g.touch(5, s.biteAt + 10))
        g.touch(0, s.biteAt + 10)
        g.resolve()
        assertEquals(Phase.DONE, g.phase)
        assertSame(Tap.Ignored, g.touch(1, s.biteAt + 20))
        assertArrayEquals(intArrayOf(1, 2), g.finalRanks())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `인원은 2~4명`() {
        BobberGame(5)
    }
}
