package com.siwoo.party.games.gomoku

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GomokuGameTest {

    private val n = GameConfig.GOMOKU_SIZE

    /**
     * 흑(first)·백 번갈아 두기. black 칸들과 white 칸들을 차례로 섞어 둔다 (흑 먼저).
     * 좌표는 (x, y) 쌍 배열.
     */
    private fun play(g: GomokuGame, black: IntArray, white: IntArray) {
        var b = 0
        var w = 0
        while (b < black.size / 2 || w < white.size / 2) {
            if (g.current == g.first && b < black.size / 2) {
                assertTrue(g.place(black[b * 2], black[b * 2 + 1])); b++
            } else if (g.current != g.first && w < white.size / 2) {
                assertTrue(g.place(white[w * 2], white[w * 2 + 1])); w++
            } else error("순서가 맞지 않음")
        }
    }

    /** 승리 줄을 (x, y) 쌍 목록으로 */
    private fun line(g: GomokuGame): List<Pair<Int, Int>> = (0 until g.winLength).map { g.winLine[it] % n to g.winLine[it] / n }

    // ── 5개 이상 판정 ──

    @Test
    fun `가로 정확히 5개면 승리`() {
        val g = GomokuGame(0)
        play(g, intArrayOf(3, 7, 4, 7, 5, 7, 6, 7, 7, 7), intArrayOf(3, 8, 4, 8, 5, 8, 6, 8))
        assertSame(Phase.OVER, g.phase)
        assertEquals(0, g.winner)
        assertEquals(5, g.winLength)
        assertEquals(listOf(3 to 7, 4 to 7, 5 to 7, 6 to 7, 7 to 7), line(g))
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }

    @Test
    fun `4개로는 승리 아님`() {
        val g = GomokuGame(0)
        play(g, intArrayOf(3, 7, 4, 7, 5, 7, 6, 7), intArrayOf(3, 8, 4, 8, 5, 8))
        assertSame(Phase.PLAY, g.phase)
        assertEquals(-1, g.winner)
        assertEquals(1, g.current)
    }

    @Test
    fun `6개 장목도 승리 - 가운데를 채워 이어짐`() {
        val g = GomokuGame(1)
        // 흑(1번): 2,3,4 _ 6,7 → 5 를 두면 2..7 여섯 개
        play(g, intArrayOf(2, 5, 3, 5, 4, 5, 6, 5, 7, 5, 5, 5), intArrayOf(0, 0, 0, 2, 0, 4, 0, 6, 0, 8))
        assertSame(Phase.OVER, g.phase)
        assertEquals(1, g.winner)
        assertEquals(6, g.winLength)
        assertEquals(listOf(2 to 5, 3 to 5, 4 to 5, 5 to 5, 6 to 5, 7 to 5), line(g))
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test
    fun `세로 다섯 개 - 가운데 돌로 완성해도 줄 전체`() {
        val g = GomokuGame(0)
        play(g, intArrayOf(9, 2, 9, 3, 9, 5, 9, 6, 9, 4), intArrayOf(1, 1, 1, 3, 1, 5, 1, 7))
        assertEquals(0, g.winner)
        assertEquals(listOf(9 to 2, 9 to 3, 9 to 4, 9 to 5, 9 to 6), line(g))
    }

    @Test
    fun `대각선 양방향 다섯 개`() {
        val a = GomokuGame(0)
        play(a, intArrayOf(2, 2, 3, 3, 4, 4, 5, 5, 6, 6), intArrayOf(10, 0, 10, 2, 10, 4, 10, 6))
        assertEquals(0, a.winner)
        assertEquals(listOf(2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 6), line(a))
        val b = GomokuGame(0)
        play(b, intArrayOf(8, 2, 7, 3, 6, 4, 5, 5, 4, 6), intArrayOf(0, 10, 2, 10, 4, 10, 6, 10))
        assertEquals(0, b.winner)
        assertEquals(listOf(8 to 2, 7 to 3, 6 to 4, 5 to 5, 4 to 6), line(b))
    }

    @Test
    fun `상대 돌이 끼면 끊긴다`() {
        val g = GomokuGame(0)
        // 흑 0,1,2,3 _ 5 / 백 4 → 흑 6 을 둬도 0..3 과 5,6 은 이어지지 않음
        play(g, intArrayOf(0, 0, 1, 0, 2, 0, 3, 0, 5, 0, 6, 0), intArrayOf(4, 0, 9, 9, 9, 11, 9, 13, 11, 9))
        assertSame(Phase.PLAY, g.phase)
        assertEquals(-1, g.winner)
    }

    // ── 가장자리 ──

    @Test
    fun `가장자리 줄 - 왼쪽 위 모서리부터 가로`() {
        val g = GomokuGame(0)
        play(g, intArrayOf(0, 0, 1, 0, 2, 0, 3, 0, 4, 0), intArrayOf(0, 1, 1, 1, 2, 1, 3, 1))
        assertEquals(0, g.winner)
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0), line(g))
    }

    @Test
    fun `가장자리 줄 - 오른쪽 아래 모서리로 세로`() {
        val g = GomokuGame(1)
        play(g, intArrayOf(14, 10, 14, 11, 14, 12, 14, 13, 14, 14), intArrayOf(13, 10, 13, 11, 13, 12, 13, 13))
        assertEquals(1, g.winner)
        assertEquals(listOf(14 to 10, 14 to 11, 14 to 12, 14 to 13, 14 to 14), line(g))
    }

    @Test
    fun `대각선 모서리 - 오른쪽 위에서 왼쪽 아래`() {
        val g = GomokuGame(0)
        play(g, intArrayOf(14, 0, 13, 1, 12, 2, 11, 3, 10, 4), intArrayOf(0, 0, 0, 1, 0, 2, 0, 3))
        assertEquals(0, g.winner)
        assertEquals(listOf(14 to 0, 13 to 1, 12 to 2, 11 to 3, 10 to 4), line(g))
    }

    @Test
    fun `대각선 모서리 - 왼쪽 아래 끝`() {
        val g = GomokuGame(0)
        play(g, intArrayOf(0, 14, 1, 13, 2, 12, 3, 11, 4, 10), intArrayOf(7, 7, 7, 8, 7, 9, 7, 10))
        assertEquals(0, g.winner)
        assertEquals(listOf(4 to 10, 3 to 11, 2 to 12, 1 to 13, 0 to 14), line(g))
    }

    @Test
    fun `가장자리 대각선 4개는 승리 아님 - 판 밖으로 이어지지 않음`() {
        val g = GomokuGame(0)
        // ↘ 대각선 (11,11)..(14,14) 네 개뿐 (15 는 판 밖)
        play(g, intArrayOf(11, 11, 12, 12, 13, 13, 14, 14), intArrayOf(0, 0, 0, 2, 0, 4))
        assertSame(Phase.PLAY, g.phase)
        // ↙ 대각선 (3,11)..(0,14) 네 개
        val h = GomokuGame(0)
        play(h, intArrayOf(3, 11, 2, 12, 1, 13, 0, 14), intArrayOf(9, 0, 9, 2, 9, 4))
        assertSame(Phase.PLAY, h.phase)
    }

    @Test
    fun `줄을 넘어가며 잇지 않음 - 행 끝과 다음 행 처음`() {
        val g = GomokuGame(0)
        // 칸 번호로는 연속 (12,13,14 | 다음 행 0,1) 이지만 가로로는 이어지지 않음
        play(g, intArrayOf(12, 4, 13, 4, 14, 4, 0, 5, 1, 5), intArrayOf(7, 9, 7, 11, 7, 13, 9, 13))
        assertSame(Phase.PLAY, g.phase)
        assertEquals(-1, g.winner)
        // 세로로 열 끝(아래)과 다음 열 처음(위)도 이어지지 않음
        val h = GomokuGame(0)
        play(h, intArrayOf(3, 12, 3, 13, 3, 14, 4, 0, 4, 1), intArrayOf(9, 9, 9, 11, 11, 9, 11, 11))
        assertSame(Phase.PLAY, h.phase)
        // 대각선이 오른쪽 끝에서 왼쪽으로 넘어가지 않음 (13,0)(14,1) → (0,3)(1,4)(2,5)
        val k = GomokuGame(0)
        play(k, intArrayOf(13, 0, 14, 1, 0, 3, 1, 4, 2, 5), intArrayOf(7, 9, 7, 11, 7, 13, 9, 13))
        assertSame(Phase.PLAY, k.phase)
    }

    @Test
    fun `백도 이길 수 있다`() {
        val g = GomokuGame(0)
        play(g, intArrayOf(0, 0, 0, 2, 0, 4, 0, 6, 0, 8), intArrayOf(5, 5, 6, 5, 7, 5, 8, 5, 9, 5))
        assertEquals("백(1번) 승리", 1, g.winner)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
        assertFalse(g.place(1, 1))
    }

    // ── 미리보기 (두 번 눌러 놓기) ──

    @Test
    fun `처음 터치는 미리보기 같은 자리 한 번 더 누르면 놓기`() {
        val g = GomokuGame(0)
        assertSame(Tap.PREVIEW, g.tap(7, 7))
        assertEquals(g.index(7, 7), g.preview)
        assertEquals(GomokuGame.EMPTY, g.at(7, 7))
        assertEquals(0, g.current)
        assertSame("다른 빈 곳 → 미리보기 이동", Tap.MOVED, g.tap(8, 8))
        assertEquals(g.index(8, 8), g.preview)
        assertSame(Tap.PLACED, g.tap(8, 8))
        assertEquals(0, g.at(8, 8))
        assertEquals(-1, g.preview)
        assertEquals(1, g.current)
        assertSame("돌 있는 곳은 무시", Tap.OCCUPIED, g.tap(8, 8))
        assertEquals(-1, g.preview)
        assertSame(Tap.IGNORED, g.tap(-1, 3))
        assertSame(Tap.IGNORED, g.tap(3, 15))
        assertSame(Tap.PREVIEW, g.tap(0, 0))
        assertSame("미리보기 중 돌 있는 곳 → 미리보기 유지", Tap.OCCUPIED, g.tap(8, 8))
        assertEquals(g.index(0, 0), g.preview)
        assertEquals(1, g.moveCount)
    }

    // ── 무르기 ──

    @Test
    fun `무르기 - 방금 둔 사람만 상대가 두기 전에 한 번`() {
        val g = GomokuGame(0)
        assertFalse("아직 둔 돌 없음", g.canUndo(0))
        g.place(7, 7)
        assertTrue(g.canUndo(0))
        assertFalse("상대는 내 돌을 못 무름", g.canUndo(1))
        assertTrue(g.undo(0))
        assertEquals(GomokuGame.EMPTY, g.at(7, 7))
        assertEquals("다시 내 차례", 0, g.current)
        assertEquals(0, g.moveCount)
        assertEquals(0, g.undosLeft[0])
        assertEquals(1, g.undosLeft[1])
        g.place(6, 6)
        assertFalse("한 판에 한 번뿐", g.canUndo(0))
        assertFalse(g.undo(0))
        assertEquals(0, g.at(6, 6))
        // 상대가 두고 나면 그 전 수는 못 무름
        g.place(1, 1)
        assertTrue(g.canUndo(1))
        g.place(2, 2)
        assertFalse("상대가 이미 둠", g.canUndo(1))
        assertFalse(g.undo(1))
        g.place(3, 3)
        assertTrue(g.undo(1))
        assertEquals(GomokuGame.EMPTY, g.at(3, 3))
        assertEquals(1, g.current)
        assertEquals(0, g.undosLeft[1])
    }

    @Test
    fun `무르기 끄면 못 무름`() {
        val g = GomokuGame(1, undoEnabled = false)
        g.place(7, 7)
        assertFalse(g.canUndo(1))
        assertFalse(g.undo(1))
        assertArrayEquals(intArrayOf(0, 0), g.undosLeft)
    }

    @Test
    fun `무르기는 미리보기를 지우고 이긴 뒤엔 못 무름`() {
        val g = GomokuGame(0)
        g.place(0, 0)
        g.tap(5, 5)
        assertTrue(g.undo(0))
        assertEquals(-1, g.preview)
        play(g, intArrayOf(3, 7, 4, 7, 5, 7, 6, 7, 7, 7), intArrayOf(3, 8, 4, 8, 5, 8, 6, 8))
        assertSame(Phase.OVER, g.phase)
        assertFalse("끝난 판은 무르기 없음", g.canUndo(1))
        assertSame(Tap.IGNORED, g.tap(10, 10))
    }

    // ── 무승부 ──

    /** 오목이 하나도 없는 꽉 찬 무늬: (x + 2y) % 4 < 2 → 흑 (가로·세로·대각선 모두 최대 2개) */
    private fun drawPattern(x: Int, y: Int): Boolean = (x + 2 * y) % 4 < 2

    @Test
    fun `판이 가득 차면 무승부 둘 다 1위`() {
        val g = GomokuGame(1)
        val blacks = ArrayList<Int>()
        val whites = ArrayList<Int>()
        for (y in 0 until n) for (x in 0 until n) (if (drawPattern(x, y)) blacks else whites).add(g.index(x, y))
        assertEquals(113, blacks.size)
        assertEquals(112, whites.size)
        var b = 0
        var w = 0
        while (g.phase == Phase.PLAY) {
            val i = if (g.isBlack(g.current)) blacks[b++] else whites[w++]
            assertTrue(g.place(i % n, i / n))
        }
        assertEquals(n * n, g.moveCount)
        assertTrue(g.isDraw)
        assertEquals(-1, g.winner)
        assertEquals(0, g.winLength)
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
        assertFalse(g.canUndo(0))
        assertFalse(g.canUndo(1))
    }

    @Test
    fun `마지막 칸으로 오목이 되면 무승부가 아니라 승리`() {
        // 작은 판(5줄): 흑 13·백 12 로 판을 다 채우는데, 마지막 흑 (4,4) 로 맨 아래 줄 다섯이 완성
        val g2 = GomokuGame(0, size = 5)
        // 흑 13: 맨 아래 줄 (0..4,4) 다섯 + 오목이 안 되는 8칸 / 백 12
        val b2 = intArrayOf(0, 0, 1, 0, 4, 0, 2, 1, 3, 1, 0, 2, 3, 2, 1, 3, 0, 4, 1, 4, 2, 4, 3, 4, 4, 4)
        val w2 = intArrayOf(2, 0, 3, 0, 0, 1, 1, 1, 4, 1, 1, 2, 2, 2, 4, 2, 0, 3, 2, 3, 3, 3, 4, 3)
        assertEquals(13, b2.size / 2)
        assertEquals(12, w2.size / 2)
        play(g2, b2, w2)
        assertEquals(25, g2.moveCount)
        assertSame(Phase.OVER, g2.phase)
        assertEquals("판이 다 찼어도 오목이면 승리", 0, g2.winner)
        assertFalse(g2.isDraw)
        assertEquals(5, g2.winLength)
    }
}
