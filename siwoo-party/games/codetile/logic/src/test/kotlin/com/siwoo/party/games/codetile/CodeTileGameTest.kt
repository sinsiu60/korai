package com.siwoo.party.games.codetile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CodeTileGameTest {

    private fun b(n: Int) = Tiles.code(n, black = true)
    private fun w(n: Int) = Tiles.code(n, black = false)

    // ───────────── 처음 나눠 주기 ─────────────

    @Test
    fun `처음 타일 수는 2인 4장 3인 4장 4인 3장`() {
        for ((players, each) in listOf(2 to 4, 3 to 4, 4 to 3)) {
            val g = CodeTileGame.create(players, Random(players))
            for (p in 0 until players) {
                // 시작하는 사람은 아직 줄에 넣지 않은 뽑은 타일을 따로 들고 있다
                assertEquals("$players 인 $p 번", each, g.rowSize(p))
                assertEquals(each, g.hiddenCount(p))
            }
            assertTrue(g.drawn >= 0)
            assertEquals(GameConfig.TILE_COUNT - players * each - 1, g.pileSize)
            // 24장이 모두 한 번씩
            val seen = ArrayList<Int>()
            for (p in 0 until players) seen.addAll(g.codes(p))
            seen.add(g.drawn)
            assertEquals(players * each + 1, seen.toSet().size)
            assertTrue(seen.all { it in 0 until GameConfig.TILE_COUNT })
            assertEquals(Phase.GUESS, g.phase)
            assertEquals(g.starter, g.current)
        }
    }

    @Test
    fun `나눠 받은 줄은 정렬돼 있다`() {
        repeat(20) { seed ->
            val g = CodeTileGame.create(2 + seed % 3, Random(seed))
            for (p in 0 until g.playerCount) assertEquals(g.codes(p).sorted(), g.codes(p))
        }
    }

    // ───────────── 정렬 규칙 ─────────────

    @Test
    fun `정렬 규칙 숫자 오름차순 같은 숫자는 검정이 왼쪽`() {
        val g = CodeTileGame.withSetup(listOf(listOf(w(5), b(9), b(5), w(0)), listOf(b(1), w(1), b(2))), listOf(b(11)))
        assertEquals(listOf(w(0), b(5), w(5), b(9)), g.codes(0))
        assertTrue(g.isBlackAt(0, 1))
        assertFalse(g.isBlackAt(0, 2))
        assertEquals(5, g.tileAt(0, 1).number)
        assertEquals(5, g.tileAt(0, 2).number)
        assertTrue(Tiles.code(3, true) < Tiles.code(3, false))
        assertTrue(Tiles.code(3, false) < Tiles.code(4, true))
    }

    @Test
    fun `넣는 자리는 정렬 위치`() {
        val row = listOf(b(1), w(3), b(7), w(7), b(10))
        assertEquals(0, Tiles.insertIndex(row, b(0)))
        assertEquals(0, Tiles.insertIndex(row, w(0)))
        assertEquals(1, Tiles.insertIndex(row, w(1)))
        assertEquals(1, Tiles.insertIndex(row, b(3))) // 같은 숫자 검정은 흰색 왼쪽
        assertEquals(2, Tiles.insertIndex(row, w(5)))
        assertEquals(4, Tiles.insertIndex(row, b(8)))
        assertEquals(5, Tiles.insertIndex(row, w(11)))
        assertEquals(0, Tiles.insertIndex(emptyList(), b(4)))
    }

    // ───────────── 정답 처리 ─────────────

    @Test
    fun `정답이면 그 타일 공개 후 계속 추리 가능`() {
        val g = CodeTileGame.withSetup(
            listOf(listOf(b(0), w(4), b(8), w(10)), listOf(w(1), b(3), w(6), b(9))),
            listOf(b(5), w(7)),
        )
        assertEquals(w(7), g.drawn)
        assertTrue(g.canTarget(1, 2))
        assertFalse("내 타일은 못 고름", g.canTarget(0, 0))
        val r = g.guess(1, 2, 6)!!
        assertTrue(r.correct)
        assertTrue(g.isRevealed(1, 2))
        assertEquals(6, g.publicNumber(1, 2))
        assertEquals(-1, g.publicNumber(1, 0))
        assertEquals(Phase.DECIDE, g.phase)
        assertFalse(r.gameOver)
        assertEquals(-1, r.eliminated)
        assertEquals("고르는 중엔 추리 불가", null, g.guess(1, 0, 1))
        assertTrue(g.continueGuess())
        assertEquals(Phase.GUESS, g.phase)
        assertEquals(0, g.current)
        assertFalse("공개된 타일은 다시 못 고름", g.canTarget(1, 2))
        assertNull(g.guess(1, 2, 6))
        assertNull("0~11 밖의 숫자", g.guess(1, 0, 12))
        val r2 = g.guess(1, 0, 1)!!
        assertTrue(r2.correct)
        assertEquals(2, g.correctThisTurn)
        assertEquals(2, g.hiddenCount(1))
        assertEquals(w(7), g.drawn)
    }

    @Test
    fun `멈추면 뽑은 타일을 숨긴 채 정렬 자리에 넣고 다음 사람`() {
        val g = CodeTileGame.withSetup(
            listOf(listOf(b(0), w(4), b(8), w(10)), listOf(w(1), b(3), w(6), b(9))),
            listOf(b(5), w(7)),
        )
        g.guess(1, 1, 3)!!
        val at = g.stop()
        assertEquals(2, at) // 0검 4흰 | 8검 10흰 → 4흰 다음
        assertEquals(listOf(b(0), w(4), w(7), b(8), w(10)), g.codes(0))
        assertFalse("숨긴 채 넣음", g.isRevealed(0, 2))
        assertEquals(w(7), g.codes(0)[2])
        assertEquals(-1, g.publicNumber(0, 2))
        assertEquals(5, g.hiddenCount(0))
        assertEquals(1, g.current)
        assertEquals("다음 사람이 뽑음", b(5), g.drawn)
        assertEquals(Phase.GUESS, g.phase)
        assertEquals(0, g.correctThisTurn)
        assertNull("이미 끝난 차례", g.stop())
    }

    // ───────────── 오답 처리 ─────────────

    @Test
    fun `오답이면 뽑은 타일을 공개한 채 넣고 차례가 넘어간다`() {
        val g = CodeTileGame.withSetup(
            listOf(listOf(b(0), w(4), b(8), w(10)), listOf(w(1), b(3), w(6), b(9))),
            listOf(b(5), w(7)),
        )
        val r = g.guess(1, 0, 2)!!
        assertFalse(r.correct)
        assertFalse("상대 타일은 그대로 숨김", g.isRevealed(1, 0))
        assertEquals(w(7), r.insertedCode)
        assertEquals(2, r.insertedIndex)
        assertTrue(g.isRevealed(0, 2))
        assertEquals(7, g.publicNumber(0, 2))
        assertEquals("숨은 수는 그대로", 4, g.hiddenCount(0))
        assertEquals(5, g.rowSize(0))
        assertFalse(r.penalty)
        assertEquals(1, g.current)
        assertEquals(b(5), g.drawn)
        assertEquals(Phase.GUESS, g.phase)
    }

    @Test
    fun `정답 뒤 계속하다 틀리면 뽑은 타일 공개`() {
        val g = CodeTileGame.withSetup(
            listOf(listOf(b(0), w(4)), listOf(w(1), b(3), w(6))),
            listOf(b(11)),
        )
        g.guess(1, 0, 1)!!
        g.continueGuess()
        val r = g.guess(1, 1, 4)!!
        assertFalse(r.correct)
        assertEquals(2, r.insertedIndex)
        assertEquals(listOf(b(0), w(4), b(11)), g.codes(0))
        assertTrue(g.isRevealed(0, 2))
        assertEquals(1, g.current)
    }

    @Test
    fun `더미가 비면 뽑지 않고 오답이면 내 숨은 타일 하나를 골라 공개`() {
        val g = CodeTileGame.withSetup(
            listOf(listOf(b(0), w(4), b(8)), listOf(w(1), b(3), w(6))),
            emptyList(),
        )
        assertEquals(-1, g.drawn)
        val r = g.guess(1, 0, 5)!!
        assertFalse(r.correct)
        assertTrue(r.penalty)
        assertEquals(-1, r.insertedIndex)
        assertEquals(Phase.PENALTY, g.phase)
        assertEquals(0, g.current)
        assertNull("벌칙 중엔 추리 불가", g.guess(1, 0, 1))
        assertFalse(g.canReveal(5))
        val rr = g.revealOwn(1)!!
        assertEquals(w(4), rr.code)
        assertTrue(g.isRevealed(0, 1))
        assertFalse(g.canReveal(1))
        assertFalse(rr.eliminated)
        assertEquals(1, g.current)
        assertEquals(Phase.GUESS, g.phase)
        assertEquals(-1, g.drawn)
    }

    @Test
    fun `더미가 비고 정답 뒤 멈추면 넣을 타일 없이 넘어감`() {
        val g = CodeTileGame.withSetup(listOf(listOf(b(0), w(4)), listOf(w(1), b(3))), emptyList())
        g.guess(1, 0, 1)!!
        assertEquals(-1, g.stop())
        assertEquals(2, g.rowSize(0))
        assertEquals(1, g.current)
    }

    @Test
    fun `벌칙으로 내 마지막 숨은 타일을 공개하면 탈락`() {
        val g = CodeTileGame.withSetup(
            listOf(listOf(b(0)), listOf(w(1), b(3)), listOf(b(5), w(9))),
            emptyList(),
        )
        g.guess(2, 0, 7)!!
        val rr = g.revealOwn(0)!!
        assertTrue(rr.eliminated)
        assertFalse(rr.gameOver)
        assertTrue(g.isOut(0))
        assertEquals(1, g.current)
        // 2인 남음 → 1번이 2번의 두 타일을 다 맞히면 끝
        g.guess(2, 0, 5)!!
        g.continueGuess()
        val r = g.guess(2, 1, 9)!!
        assertTrue(r.gameOver)
        assertEquals(2, r.eliminated)
        assertEquals(Phase.OVER, g.phase)
        assertEquals(1, g.winner)
        assertArrayEquals(intArrayOf(3, 1, 2), g.ranks())
    }

    // ───────────── 탈락 · 승리 · 순위 ─────────────

    @Test
    fun `모두 공개되면 탈락하고 최후의 1인 승리`() {
        val g = CodeTileGame.withSetup(
            listOf(listOf(b(0), w(4)), listOf(w(1), b(3))),
            listOf(b(9)),
        )
        g.guess(1, 0, 1)!!
        g.continueGuess()
        val r = g.guess(1, 1, 3)!!
        assertTrue(r.correct)
        assertEquals(1, r.eliminated)
        assertTrue(r.gameOver)
        assertTrue(g.isOut(1))
        assertEquals(1, g.aliveCount)
        assertEquals(0, g.winner)
        assertEquals(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
        assertNull(g.stop())
        assertFalse(g.continueGuess())
    }

    @Test
    fun `탈락한 사람은 차례를 건너뛰고 고를 수 없다`() {
        val g = CodeTileGame.withSetup(
            listOf(listOf(b(0), w(4)), listOf(w(1)), listOf(b(5), w(9)), listOf(b(2), w(8))),
            listOf(w(11), b(10), w(3)),
        )
        val r = g.guess(1, 0, 1)!!
        assertEquals(1, r.eliminated)
        assertFalse(r.gameOver)
        assertEquals(Phase.DECIDE, g.phase)
        g.continueGuess()
        assertFalse("탈락한 사람 타일은 못 고름", g.canTarget(1, 0))
        g.stop() // 계속 단계가 아니라 무시
        g.guess(2, 0, 6)!! // 오답 → 넘어감
        assertEquals("1번은 건너뜀", 2, g.current)
        g.guess(3, 0, 0)!! // 오답
        assertEquals(3, g.current)
        g.guess(0, 0, 7)!! // 오답
        assertEquals(0, g.current)
    }

    @Test
    fun `4인 순위는 승자 1위 나머지는 늦게 탈락한 순`() {
        val g = CodeTileGame.withSetup(
            listOf(listOf(b(0)), listOf(w(1)), listOf(b(5)), listOf(b(2))),
            listOf(w(11), b(10), w(3), b(6)),
        )
        // 0번 차례: 2번 탈락 → 멈춤
        assertEquals(2, g.guess(2, 0, 5)!!.eliminated)
        g.stop()
        // 1번 차례: 0번의 타일 두 장(0검 숨김, 6검 숨김) 중 0검 맞힘 → 계속 → 6검 맞혀 0번 탈락
        assertEquals(1, g.current)
        assertEquals(listOf(b(0), b(6)), g.codes(0))
        g.guess(0, 0, 0)!!
        g.continueGuess()
        assertEquals(0, g.guess(0, 1, 6)!!.eliminated)
        g.stop()
        // 3번 차례 (2번·0번 탈락): 1번 맞혀 끝
        assertEquals(3, g.current)
        assertEquals(listOf(w(1), w(3)), g.codes(1))
        assertFalse(g.guess(1, 0, 1)!!.gameOver)
        g.continueGuess()
        val last = g.guess(1, 1, 3)!!
        assertTrue(last.gameOver)
        assertEquals(3, g.winner)
        assertEquals(listOf(2, 0, 1), g.eliminationOrder)
        assertArrayEquals(intArrayOf(3, 2, 4, 1), g.ranks())
    }

    @Test
    fun `공개 정보 isPubliclyRevealed 는 공개된 타일만`() {
        val g = CodeTileGame.withSetup(listOf(listOf(b(0), w(4)), listOf(w(1), b(3))), listOf(b(9)))
        assertFalse(g.isPubliclyRevealed(w(1)))
        g.guess(1, 0, 1)
        assertTrue(g.isPubliclyRevealed(w(1)))
        assertFalse(g.isPubliclyRevealed(b(0)))
    }

    @Test
    fun `무작위로 끝까지 두면 항상 끝나고 순위가 1부터 인원까지`() {
        for (seed in 0 until 60) {
            val players = 2 + seed % 3
            val rnd = Random(seed)
            val g = CodeTileGame.create(players, rnd)
            var steps = 0
            while (g.phase != Phase.OVER) {
                steps++
                assertTrue("끝나지 않음", steps < 2000)
                when (g.phase) {
                    Phase.GUESS -> {
                        val targets = ArrayList<Pair<Int, Int>>()
                        for (p in 0 until players) for (i in 0 until g.rowSize(p)) if (g.canTarget(p, i)) targets.add(p to i)
                        assertTrue(targets.isNotEmpty())
                        val (p, i) = targets[rnd.nextInt(targets.size)]
                        // 절반은 정답(진짜 숫자), 절반은 아무 숫자
                        val n = if (rnd.nextBoolean()) g.tileAt(p, i).number else rnd.nextInt(GameConfig.NUMBERS)
                        assertNotNull(g.guess(p, i, n))
                    }
                    Phase.DECIDE -> if (rnd.nextBoolean()) g.continueGuess() else g.stop()
                    Phase.PENALTY -> {
                        val me = g.current
                        val hidden = (0 until g.rowSize(me)).filter { g.canReveal(it) }
                        assertNotNull(g.revealOwn(hidden[rnd.nextInt(hidden.size)]))
                    }
                    Phase.OVER -> Unit
                }
                for (p in 0 until players) assertEquals(g.codes(p).sorted(), g.codes(p))
            }
            assertEquals(1, g.aliveCount)
            assertEquals((1..players).toList(), g.ranks().sorted())
            assertEquals(1, g.ranks()[g.winner])
            assertEquals(players - 1, g.eliminationOrder.size)
        }
    }
}
