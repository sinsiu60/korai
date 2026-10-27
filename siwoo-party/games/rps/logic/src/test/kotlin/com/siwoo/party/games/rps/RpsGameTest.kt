package com.siwoo.party.games.rps

import com.siwoo.party.games.rps.Hand.PAPER
import com.siwoo.party.games.rps.Hand.ROCK
import com.siwoo.party.games.rps.Hand.SCISSORS
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RpsGameTest {

    /** 한 판: "보!" 를 1000 에, 손은 순서대로 (살아 있는 사람만, null = 안 냄) 100ms 뒤에 낸다 */
    private fun play(g: RpsGame, vararg hands: Hand?): ThrowResult {
        g.startThrow()
        g.open(1000)
        var k = 0
        for (p in 0 until g.playerCount) {
            if (!g.alive[p]) continue
            val h = hands[k++]
            if (h != null) assertSame(Press.ACCEPTED, g.press(p, h, 1100))
        }
        return g.resolve()
    }

    @Test fun `이기는 손`() {
        assertTrue(ROCK.beats(SCISSORS) && SCISSORS.beats(PAPER) && PAPER.beats(ROCK))
        assertFalse(SCISSORS.beats(ROCK) || PAPER.beats(SCISSORS) || ROCK.beats(PAPER) || ROCK.beats(ROCK))
    }

    @Test fun `2인 무승부 - 같은 손이면 다시`() {
        for (h in Hand.values()) {
            val g = RpsGame(2)
            val r = play(g, h, h)
            assertSame(Replay.ALL_SAME, r.replay)
            assertTrue(r.eliminated.isEmpty())
            assertEquals(2, g.aliveCount)
            assertSame(Phase.IDLE, g.phase)
        }
    }

    @Test fun `3인 무승부 - 모두 같거나 세 가지 다`() {
        val g = RpsGame(3)
        assertSame(Replay.ALL_SAME, play(g, PAPER, PAPER, PAPER).replay)
        assertSame(Replay.ALL_THREE, play(g, ROCK, PAPER, SCISSORS).replay)
        assertEquals(3, g.aliveCount)
        assertEquals(2, g.throwNumber)
    }

    @Test fun `4인 무승부 - 모두 같거나 세 가지 다`() {
        val g = RpsGame(4)
        assertSame(Replay.ALL_SAME, play(g, ROCK, ROCK, ROCK, ROCK).replay)
        assertSame(Replay.ALL_THREE, play(g, ROCK, PAPER, SCISSORS, SCISSORS).replay)
        assertSame(Replay.ALL_THREE, play(g, PAPER, PAPER, ROCK, SCISSORS).replay)
        assertEquals(4, g.aliveCount)
        assertArrayEquals(intArrayOf(0, 0, 0, 0), g.rank)
    }

    @Test fun `탈락 판정 - 진 손을 낸 사람 모두 탈락, 같은 순위`() {
        val g = RpsGame(4)
        val r = play(g, PAPER, ROCK, PAPER, ROCK)
        assertNull(r.replay)
        assertEquals(listOf(1, 3), r.eliminated)
        assertEquals("둘이 함께 탈락 → 공동 3위", 3, r.rank)
        assertArrayEquals(intArrayOf(0, 3, 0, 3), g.rank)
        assertArrayEquals(booleanArrayOf(true, false, true, false), g.alive)
        // 남은 두 명: 가위 > 보
        val r2 = play(g, PAPER, SCISSORS)
        assertEquals(listOf(0), r2.eliminated)
        assertEquals(2, r2.rank)
        assertTrue(g.isOver)
        assertEquals(2, g.winner)
        assertArrayEquals(intArrayOf(2, 3, 1, 3), g.ranks())
    }

    @Test fun `3인 한 명만 지면 3위`() {
        val g = RpsGame(3)
        val r = play(g, ROCK, ROCK, SCISSORS)
        assertEquals(listOf(2), r.eliminated)
        assertEquals(3, r.rank)
        assertFalse(g.isOver)
    }

    @Test fun `늦은 입력 - 0점6초를 넘기면 반칙 패배 (경계 포함)`() {
        val g = RpsGame(3)
        g.startThrow()
        g.open(5000)
        assertSame("정확히 0.6초는 인정", Press.ACCEPTED, g.press(0, ROCK, 5000 + GameConfig.WINDOW_MS))
        assertSame("0.6초 넘으면 무시", Press.IGNORED, g.press(1, PAPER, 5000 + GameConfig.WINDOW_MS + 1))
        assertNull(g.hand[1])
        // 2번은 아예 안 냄
        val r = g.resolve()
        assertSame(Foul.LATE, g.foul[1])
        assertSame(Foul.LATE, g.foul[2])
        assertEquals("늦은 두 명이 함께 탈락", listOf(1, 2), r.eliminated)
        assertEquals(2, r.rank)
        assertTrue(g.isOver)
        assertArrayEquals(intArrayOf(1, 2, 2), g.ranks())
    }

    @Test fun `이른 입력 - 보 전에 누르면 반칙, 이후 버튼 무시`() {
        val g = RpsGame(2)
        g.startThrow()
        assertSame(Press.FOUL_EARLY, g.press(0, SCISSORS, 100))
        assertSame(Foul.EARLY, g.foul[0])
        g.open(1000)
        assertSame("반칙한 사람은 다시 낼 수 없음", Press.IGNORED, g.press(0, ROCK, 1100))
        assertSame(Press.ACCEPTED, g.press(1, PAPER, 1200))
        assertSame("한 번 낸 손은 못 바꿈", Press.IGNORED, g.press(1, SCISSORS, 1300))
        assertSame(PAPER, g.hand[1])
        assertTrue(g.allDecided)
        val r = g.resolve()
        assertEquals(listOf(0), r.eliminated)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test fun `모두 반칙이면 아무도 탈락하지 않고 다시`() {
        val g = RpsGame(3)
        g.startThrow()
        g.press(0, ROCK, 0)              // 이른 반칙
        g.open(1000)
        g.press(1, ROCK, 1700)           // 늦음
        val r = g.resolve()              // 2번 안 냄
        assertSame(Replay.ALL_FOUL, r.replay)
        assertTrue(r.eliminated.isEmpty())
        assertEquals(3, g.aliveCount)
        // 다음 판은 반칙이 지워진다
        g.startThrow()
        assertTrue(g.foul.all { it == Foul.NONE })
        assertTrue(g.hand.all { it == null })
    }

    @Test fun `반칙한 사람은 지고, 제대로 낸 사람끼리는 보통 판정`() {
        // 반칙 + 나머지 같은 손 → 반칙자만 탈락
        val g = RpsGame(3)
        val r = play(g, null, ROCK, ROCK)
        assertEquals(listOf(0), r.eliminated)
        assertEquals(3, r.rank)
        assertArrayEquals(booleanArrayOf(false, true, true), g.alive)
        // 반칙 + 나머지 두 가지 손 → 반칙자와 진 손 함께 탈락 (공동 순위)
        val h = RpsGame(4)
        val r2 = play(h, null, ROCK, SCISSORS, ROCK)
        assertEquals(listOf(0, 2), r2.eliminated)
        assertEquals(3, r2.rank)
        // 반칙 + 세 가지 다 → 반칙자만 탈락
        val k = RpsGame(4)
        val r3 = play(k, ROCK, PAPER, SCISSORS, null)
        assertEquals(listOf(3), r3.eliminated)
        assertEquals(4, r3.rank)
        // 한 명만 제대로 냄 → 그 사람이 남고 나머지 탈락
        val m = RpsGame(3)
        val r4 = play(m, null, null, PAPER)
        assertEquals(listOf(0, 1), r4.eliminated)
        assertTrue(m.isOver)
        assertEquals(2, m.winner)
        assertArrayEquals(intArrayOf(2, 2, 1), m.ranks())
    }

    @Test fun `최후의 1인까지 - 탈락 순서로 순위`() {
        val g = RpsGame(4)
        assertSame(Replay.ALL_SAME, play(g, ROCK, ROCK, ROCK, ROCK).replay)
        assertEquals(listOf(2), play(g, PAPER, PAPER, ROCK, PAPER).eliminated)   // 다온 4위
        assertArrayEquals(intArrayOf(0, 0, 4, 0), g.rank)
        assertSame(Press.IGNORED, run { g.startThrow(); g.press(2, ROCK, 0) })
        g.abortThrow()
        assertSame(Phase.IDLE, g.phase)
        assertSame(Replay.ALL_THREE, play(g, ROCK, PAPER, SCISSORS).replay)
        assertEquals(listOf(0), play(g, SCISSORS, ROCK, ROCK).eliminated)       // 가람 3위
        assertFalse(g.isOver)
        assertEquals(-1, g.winner)
        assertEquals(listOf(1), play(g, SCISSORS, ROCK).eliminated)             // 나래 2위
        assertTrue(g.isOver)
        assertEquals(3, g.winner)
        assertArrayEquals(intArrayOf(3, 2, 4, 1), g.ranks())
        try { g.startThrow(); error("끝난 뒤 시작 불가") } catch (e: IllegalStateException) { }
    }

    @Test fun `판 밖이나 탈락자의 누름은 무시`() {
        val g = RpsGame(2)
        assertSame(Press.IGNORED, g.press(0, ROCK, 0))
        g.startThrow(); g.open(0)
        assertSame(Press.IGNORED, g.press(7, ROCK, 10))
        try { g.ranks(); error("끝나기 전 순위 없음") } catch (e: IllegalStateException) { }
    }
}
