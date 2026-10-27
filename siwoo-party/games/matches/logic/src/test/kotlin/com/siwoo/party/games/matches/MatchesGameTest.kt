package com.siwoo.party.games.matches

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchesGameTest {

    /** 1개씩만 가져가며 라운드를 끝까지 → 진 사람 */
    private fun playOnes(g: MatchesGame): Int {
        while (true) {
            when (val r = g.take(1)) {
                is Take.Lost -> return r.player
                is Take.Took -> Unit
                Take.Invalid -> error("1개는 언제나 가져갈 수 있어야 함")
            }
        }
    }

    @Test
    fun `처음엔 성냥 21개 첫 라운드는 0번부터`() {
        val g = MatchesGame(3)
        assertEquals(21, GameConfig.MATCHES)
        assertEquals(21, g.remaining)
        assertEquals(0, g.round)
        assertEquals(0, g.current)
        assertEquals(3, g.maxTake)
        assertFalse(g.roundOver)
        assertFalse(g.gameOver)
    }

    @Test
    fun `1개에서 3개까지만 가져갈 수 있다`() {
        val g = MatchesGame(2)
        assertSame(Take.Invalid, g.take(0))
        assertSame(Take.Invalid, g.take(4))
        assertSame(Take.Invalid, g.take(-1))
        assertEquals("무시하면 그대로", 21, g.remaining)
        assertEquals(0, g.current)
        for (k in 1..3) {
            val r = g.take(k) as Take.Took
            assertEquals(k, r.count)
        }
        assertEquals(21 - 6, g.remaining)
        assertArrayEquals(intArrayOf(1 + 3, 2), g.takenThisRound)
    }

    @Test
    fun `가져가면 다음 사람 차례로 자리 순서대로 돈다`() {
        val g = MatchesGame(4)
        val r1 = g.take(2) as Take.Took
        assertEquals(0, r1.player)
        assertEquals(19, r1.remaining)
        assertEquals(1, r1.next)
        g.take(1); g.take(1)
        assertEquals(3, g.current)
        g.take(3)
        assertEquals("한 바퀴 돌아 다시 0번", 0, g.current)
        assertEquals(21 - 7, g.remaining)
    }

    @Test
    fun `남은 수보다 많이는 못 가져간다`() {
        val g = MatchesGame(2, totalMatches = 4)
        g.take(2) // 남은 2
        assertEquals(2, g.maxTake)
        assertSame(Take.Invalid, g.take(3))
        assertFalse(g.canTake(3))
        assertTrue(g.canTake(2))
        g.take(1) // 남은 1
        assertEquals(1, g.maxTake)
        assertSame(Take.Invalid, g.take(2))
        assertEquals(1, g.remaining)
        assertEquals("무시되면 차례도 그대로", 0, g.current)
    }

    @Test
    fun `마지막 성냥을 가져간 사람이 라운드 패배`() {
        val g = MatchesGame(3)
        // 0,1,2,0,1,2,... 3개씩 → 21 = 3 × 7: 7번째(0,1,2,0,1,2,0) = 0번이 마지막
        var last: Take = Take.Invalid
        repeat(7) { last = g.take(3) }
        val lost = last as Take.Lost
        assertEquals(0, lost.player)
        assertEquals(3, lost.count)
        assertEquals(0, lost.round)
        assertFalse(lost.gameOver)
        assertEquals(0, g.remaining)
        assertTrue(g.roundOver)
        assertArrayEquals(intArrayOf(1, 0, 0), g.losses)
        assertEquals(0, g.roundLoser[0])
        // 라운드가 끝나면 nextRound 전까지 가져가기 무시
        assertSame(Take.Invalid, g.take(1))
    }

    @Test
    fun `남은 1개를 혼자 가져가도 패배`() {
        val g = MatchesGame(2, totalMatches = 5)
        g.take(3); g.take(1) // 남은 1, 0번 차례
        val lost = g.take(1) as Take.Lost
        assertEquals(0, lost.player)
        assertEquals(1, g.losses[0])
    }

    @Test
    fun `라운드마다 첫 차례가 한 칸씩 돈다`() {
        val g = MatchesGame(4)
        val starters = IntArray(4)
        for (r in 0 until 4) {
            assertEquals(r, g.round)
            starters[r] = g.current
            assertEquals(21, g.remaining)
            playOnes(g)
            if (r < 3) assertTrue(g.nextRound())
        }
        assertArrayEquals(intArrayOf(0, 1, 2, 3), starters)
        assertEquals(1, g.starterOf(5))
    }

    @Test
    fun `다음 라운드는 성냥 21개로 다시 이번 라운드 가져간 수도 0`() {
        val g = MatchesGame(3)
        assertFalse("라운드 중엔 다음 라운드 못 감", g.nextRound())
        playOnes(g)
        assertTrue(g.nextRound())
        assertEquals(1, g.round)
        assertEquals(21, g.remaining)
        assertFalse(g.roundOver)
        assertArrayEquals(intArrayOf(0, 0, 0), g.takenThisRound)
        assertFalse("두 번 연속은 안 됨", g.nextRound())
    }

    @Test
    fun `라운드 수는 인원 수`() {
        for (n in 2..4) {
            val g = MatchesGame(n)
            assertEquals(n, g.rounds)
            var played = 0
            while (true) {
                played++
                playOnes(g)
                if (g.gameOver) break
                assertTrue(g.nextRound())
            }
            assertEquals(n, played)
            assertTrue(g.gameOver)
            assertFalse("끝나면 다음 라운드 없음", g.nextRound())
            assertSame(Take.Invalid, g.take(1))
            assertEquals(n, g.losses.sum())
            assertTrue(g.roundLoser.all { it >= 0 })
        }
    }

    @Test
    fun `1개씩만 가져가면 진 사람은 첫 차례에서 20칸 뒤`() {
        // 21번째 성냥 = 첫 차례 + 20 번째 사람
        val g = MatchesGame(3)
        val losers = IntArray(3)
        for (r in 0 until 3) {
            losers[r] = playOnes(g)
            assertEquals((r + 20) % 3, losers[r])
            g.nextRound()
        }
        assertArrayEquals(intArrayOf(2, 0, 1), losers)
    }

    @Test
    fun `마지막 라운드 패배면 gameOver`() {
        val g = MatchesGame(2)
        playOnes(g)
        g.nextRound()
        var last: Take = Take.Invalid
        while (last !is Take.Lost) last = g.take(1)
        assertTrue((last as Take.Lost).gameOver)
        assertEquals(1, last.round)
        assertTrue(g.gameOver)
    }

    @Test
    fun `순위는 패배가 적은 순 동점은 공동`() {
        val g = MatchesGame(4)
        // 라운드별 진 사람을 정해서 진행: 3, 0, 3, 0 → 패배 [2,0,0,2]
        val plan = intArrayOf(3, 0, 3, 0)
        for (r in 0 until 4) {
            loseBy(g, plan[r])
            if (r < 3) g.nextRound()
        }
        assertArrayEquals(intArrayOf(2, 0, 0, 2), g.losses)
        assertArrayEquals(intArrayOf(3, 1, 1, 3), g.finalRanks())
    }

    @Test
    fun `순위 3인 모두 다르게와 모두 같게`() {
        val g = MatchesGame(3)
        val plan = intArrayOf(1, 1, 2)
        for (r in 0 until 3) { loseBy(g, plan[r]); if (r < 2) g.nextRound() }
        assertArrayEquals(intArrayOf(0, 2, 1), g.losses)
        assertArrayEquals(intArrayOf(1, 3, 2), g.finalRanks())

        val h = MatchesGame(3)
        for (r in 0 until 3) { loseBy(h, r); if (r < 2) h.nextRound() }
        assertArrayEquals(intArrayOf(1, 1, 1), h.finalRanks())

        val two = MatchesGame(2)
        loseBy(two, 0); two.nextRound(); loseBy(two, 0)
        assertArrayEquals(intArrayOf(2, 1), two.finalRanks())
    }

    /** loser 가 마지막 성냥을 가져가도록 모두가 협력해서 진행 (가능한 수를 찾아 둠) */
    private fun loseBy(g: MatchesGame, loser: Int) {
        while (!g.roundOver) {
            val k = (1..g.maxTake).first { ok(g.playerCount, g.remaining, g.current, it, loser) }
            g.take(k)
        }
        assertEquals(loser, g.roundLoser[g.round])
    }

    /** rem 개가 남고 mover 차례일 때 k 개를 가져가면 결국 loser 가 마지막을 가져가게 만들 수 있는지 */
    private fun ok(n: Int, rem: Int, mover: Int, k: Int, loser: Int): Boolean {
        val left = rem - k
        if (left == 0) return mover == loser
        val next = (mover + 1) % n
        return (1..minOf(3, left)).any { ok(n, left, next, it, loser) }
    }
}
