package com.siwoo.party.games.sequence

import com.siwoo.party.common.TouchZones
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SequenceGameTest {

    private fun cellOf(g: SequenceGame, number: Int) = g.layout.indexOf(number)

    /** player 가 from..to 를 순서대로 누름 (시간 now) */
    private fun run(g: SequenceGame, player: Int, from: Int, to: Int, now: Long = 0) {
        for (n in from..to) {
            val r = g.tap(player, cellOf(g, n), now)
            assertTrue("숫자 $n: $r", r is Tap.Correct || r is Tap.Finished)
        }
    }

    @Test fun `모든 구역이 같은 배치, 1~16 한 번씩, 라운드마다 다시 섞음`() {
        val g = SequenceGame(4, Random(1))
        g.startRound()
        assertEquals((1..16).toList(), g.layout.sorted())
        for (cell in 0 until GameConfig.CELLS) {
            val n = g.numberAt(0, cell)
            for (p in 1 until 4) assertEquals("구역 $p 칸 $cell", n, g.numberAt(p, cell))
        }
        val first = g.layout.copyOf()
        run(g, 0, 1, 16); run(g, 1, 1, 16); run(g, 2, 1, 16)
        g.endRound()
        g.startRound()
        assertEquals((1..16).toList(), g.layout.sorted())
        assertFalse("다음 라운드는 다른 배치", first.contentEquals(g.layout))
    }

    @Test fun `순서대로 누르면 다음 숫자로 진행, 이미 누른 숫자는 무시`() {
        val g = SequenceGame(2, Random(2))
        g.startRound()
        val r = g.tap(0, cellOf(g, 1), 0)
        assertTrue(r is Tap.Correct)
        assertEquals(1, (r as Tap.Correct).number)
        assertEquals(2, g.next[0])
        assertEquals(1, g.next[1])
        // 1 을 다시 누르면 무시 (벌칙 없음)
        assertSame(Tap.Ignored, g.tap(0, cellOf(g, 1), 10))
        assertFalse(g.isFrozen(0, 10))
        run(g, 0, 2, 15)
        val f = g.tap(0, cellOf(g, 16), 20)
        assertTrue(f is Tap.Finished)
        assertEquals(1, (f as Tap.Finished).place)
        assertTrue(g.isFinished(0))
        assertSame("끝낸 뒤 누름 무시", Tap.Ignored, g.tap(0, cellOf(g, 3), 30))
    }

    @Test fun `틀리면 반 초 정지, 그동안 입력 무시`() {
        val g = SequenceGame(3, Random(3))
        g.startRound()
        run(g, 1, 1, 2, 0)
        val w = g.tap(1, cellOf(g, 7), 1000)
        assertTrue(w is Tap.Wrong)
        w as Tap.Wrong
        assertEquals(7, w.number)
        assertEquals(3, w.expected)
        assertEquals(1000 + GameConfig.WRONG_FREEZE_MS, w.untilMs)
        assertEquals("틀려도 진행은 그대로", 3, g.next[1])
        // 정지 중엔 맞는 숫자도 무시
        assertTrue(g.tap(1, cellOf(g, 3), 1200) is Tap.Frozen)
        assertTrue(g.tap(1, cellOf(g, 3), 1499) is Tap.Frozen)
        assertEquals(3, g.next[1])
        // 다른 사람은 그대로 진행
        assertTrue(g.tap(0, cellOf(g, 1), 1200) is Tap.Correct)
        // 0.5초 뒤 다시
        assertFalse(g.isFrozen(1, 1500))
        assertTrue(g.tap(1, cellOf(g, 3), 1500) is Tap.Correct)
        assertEquals(4, g.next[1])
    }

    @Test fun `라운드 끝 - 완료 순서로 등수와 점수, 마지막 한 명은 꼴찌`() {
        val g = SequenceGame(4, Random(4))
        g.startRound()
        run(g, 2, 1, 16)
        run(g, 0, 1, 16)
        run(g, 3, 1, 5)
        assertFalse(g.roundShouldEnd)
        run(g, 3, 6, 16)
        assertTrue("3명 끝 → 라운드 끝", g.roundShouldEnd)
        assertEquals(listOf(2, 0, 3), g.finishOrder)
        g.endRound()
        assertSame(Phase.ROUND_OVER, g.phase)
        assertArrayEquals(intArrayOf(2, 4, 1, 3), g.roundPlaces)
        assertArrayEquals(intArrayOf(3, 1, 4, 2), g.roundPoints)
        assertArrayEquals(intArrayOf(3, 1, 4, 2), g.totalPoints)
        assertSame("라운드 끝난 뒤 누름 무시", Tap.Ignored, g.tap(1, cellOf(g, 1), 0))
    }

    @Test fun `2인은 한 명이 끝내면 라운드 끝`() {
        val g = SequenceGame(2, Random(5))
        g.startRound()
        run(g, 1, 1, 16)
        assertTrue(g.roundShouldEnd)
        g.endRound()
        assertArrayEquals(intArrayOf(2, 1), g.roundPlaces)
        assertArrayEquals(intArrayOf(1, 2), g.roundPoints)
    }

    @Test fun `시간 초과 - 남은 사람은 누른 개수 순, 같으면 같은 등수`() {
        val g = SequenceGame(4, Random(6))
        g.startRound()
        run(g, 3, 1, 16)
        run(g, 0, 1, 9)
        run(g, 1, 1, 9)
        run(g, 2, 1, 4)
        g.endRound()
        assertArrayEquals(intArrayOf(2, 2, 4, 1), g.roundPlaces)
        assertArrayEquals(intArrayOf(3, 3, 1, 4), g.roundPoints)
    }

    @Test fun `3라운드 합계로 최종 순위, 동점은 같은 순위`() {
        val g = SequenceGame(3, Random(7))
        // R1: 0,1,2 / R2: 1,0,2 / R3: 2,0,1 → 0: 3+2+2=7, 1: 2+3+1=6, 2: 1+1+3=5
        val orders = listOf(intArrayOf(0, 1, 2), intArrayOf(1, 0, 2), intArrayOf(2, 0, 1))
        for (o in orders) {
            g.startRound()
            run(g, o[0], 1, 16)
            run(g, o[1], 1, 16)
            assertTrue(g.roundShouldEnd)
            g.endRound()
        }
        assertTrue(g.gameOver)
        assertArrayEquals(intArrayOf(7, 6, 5), g.totalPoints)
        assertArrayEquals(intArrayOf(1, 2, 3), g.finalRanks())
        try {
            g.startRound(); error("3라운드 뒤 시작되면 안 됨")
        } catch (e: IllegalStateException) { /* 정상 */ }

        // 동점
        val t = SequenceGame(2, Random(9))
        t.startRound(); run(t, 0, 1, 16); t.endRound()
        t.startRound(); run(t, 1, 1, 16); t.endRound()
        assertArrayEquals(intArrayOf(3, 3), t.totalPoints)
        assertArrayEquals("2라운드 뒤 동점 → 같은 순위", intArrayOf(1, 1), t.finalRanks())
    }

    @Test fun `구역 좌표 - 위쪽 구역 칸은 180도 돌아 있다`() {
        val zs = TouchZones.layout(4, 1280f, 720f, 20f)
        val out = FloatArray(2)
        ZoneFrame.toScreen(zs[1], 0f, 0f, out)
        assertEquals(zs[1].right, out[0], 0.01f)
        assertEquals(zs[1].bottom, out[1], 0.01f)
        ZoneFrame.toLocal(zs[1], zs[1].right - 5f, zs[1].bottom - 7f, out)
        assertEquals(5f, out[0], 0.01f)
        assertEquals(7f, out[1], 0.01f)
    }
}
