package com.siwoo.party.games.ludo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class LudoGameTest {

    private fun game(players: Int = 2, seed: Int = 1) = LudoGame(players, Random(seed))

    /** 자리 s 의 말이 진행 pos 일 때 바깥 길 칸 */
    private fun cell(g: LudoGame, p: Int, pos: Int) = LudoBoard.trackIndex(g.seatOf(p), pos)

    /** 상대 o 의 말이 바깥 길 칸 n 에 서려면 필요한 진행 값 */
    private fun posAt(g: LudoGame, o: Int, n: Int): Int =
        ((n - LudoBoard.startOf(g.seatOf(o))) % GameConfig.TRACK + GameConfig.TRACK) % GameConfig.TRACK

    // ── 판 ──

    @Test
    fun `판은 바깥 52칸이 이어지고 집 통로는 5칸 + 집`() {
        assertEquals(52, GameConfig.TRACK)
        assertEquals(56, GameConfig.HOME)
        // 이웃한 바깥 칸은 가로·세로로 한 칸 (모서리 대각선 칸 이음 포함 최대 √2)
        for (n in 0 until 52) {
            val m = (n + 1) % 52
            val d = abs(LudoBoard.TRACK_X[n] - LudoBoard.TRACK_X[m]) + abs(LudoBoard.TRACK_Y[n] - LudoBoard.TRACK_Y[m])
            assertTrue("칸 $n → $m 거리 $d", d in 0.99f..2.01f)
        }
        // 서로 다른 칸
        val seen = HashSet<Int>()
        for (n in 0 until 52) assertTrue(seen.add((LudoBoard.TRACK_X[n] * 10).toInt() * 1000 + (LudoBoard.TRACK_Y[n] * 10).toInt()))
        // 자리 0 출발 칸 (1,6), 자리 1 출발 칸 (8,1)
        assertEquals(1.5f, LudoBoard.TRACK_X[0], 0.001f)
        assertEquals(6.5f, LudoBoard.TRACK_Y[0], 0.001f)
        assertEquals(8.5f, LudoBoard.TRACK_X[13], 0.001f)
        assertEquals(1.5f, LudoBoard.TRACK_Y[13], 0.001f)
        // 자리 0: 바깥 마지막 칸(진행 50) 바로 옆이 집 통로 첫 칸
        val last = cell(game(), 0, 50)
        assertEquals(0.5f, LudoBoard.TRACK_X[last], 0.001f)
        assertEquals(7.5f, LudoBoard.TRACK_Y[last], 0.001f)
        assertEquals(1.5f, LudoBoard.LANE_X[0][0], 0.001f)
        assertEquals(7.5f, LudoBoard.LANE_Y[0][0], 0.001f)
        // 안전 칸 8개 (출발 4 + 별 4)
        assertEquals(8, LudoBoard.SAFE.count { it })
        assertEquals(4, LudoBoard.STAR.count { it })
        for (s in 0 until 4) {
            assertTrue(LudoBoard.SAFE[13 * s])
            assertTrue(LudoBoard.STAR[13 * s + 8])
        }
    }

    @Test
    fun `2인은 마주 보는 자리, 3인은 세 자리`() {
        assertArrayEquals(intArrayOf(0, 2), game(2).seats)
        assertArrayEquals(intArrayOf(0, 1, 2), game(3).seats)
        assertArrayEquals(intArrayOf(0, 1, 2, 3), game(4).seats)
    }

    // ── 출발 조건 ──

    @Test
    fun `6이 아니면 출발할 수 없고 움직일 말이 없으면 차례가 자동으로 넘어간다`() {
        val g = game()
        for (v in 1..5) {
            g.setupTurn(0)
            val o = g.rollWith(v)
            assertTrue(o.moves.isEmpty())
            assertTrue("눈 $v: 차례 넘어감", o.turnEnded)
            assertFalse(o.again)
            assertEquals(1, o.next)
            assertEquals(1, g.current)
            assertEquals(Phase.ROLL, g.phase)
            assertEquals(4, g.yardCount(0))
        }
    }

    @Test
    fun `6이면 대기 말을 출발 칸에 올리고 한 번 더 굴린다`() {
        val g = game()
        val o = g.rollWith(6)
        assertFalse(o.turnEnded)
        assertEquals(Phase.MOVE, g.phase)
        assertEquals("대기 말 4개 모두 출발 가능", 4, o.moves.size)
        assertTrue(o.moves.all { it.release && it.to == 0 && it.path.contentEquals(intArrayOf(0)) })
        assertTrue("모두 같은 이동 → 자동 이동 후보", g.onlyOneChoice)
        val m = g.move(2)!!
        assertTrue(m.move.release)
        assertEquals(0, g.pos(0, 2))
        assertTrue(m.extraRoll)
        assertTrue(m.bySix)
        assertEquals(0, g.current)
        assertEquals(Phase.ROLL, g.phase)
        // 이어서 3 → 출발 칸에서 3칸
        val o2 = g.rollWith(3)
        assertEquals(1, o2.moves.size)
        assertEquals(0, g.sixesInRow)
        val m2 = g.move(2)!!
        assertArrayEquals(intArrayOf(1, 2, 3), m2.move.path)
        assertEquals(3, g.pos(0, 2))
        assertTrue(m2.turnEnded)
        assertEquals(1, g.current)
    }

    @Test
    fun `6이 나왔는데 움직일 말이 없으면 이동 없이 한 번 더 굴린다`() {
        val g = game()
        // 말 3개 집, 1개는 집까지 2칸 → 6으로는 못 움직임 (대기 말도 없음)
        for (k in 0..2) g.setupPiece(0, k, GameConfig.HOME)
        g.setupPiece(0, 3, 54)
        val o = g.rollWith(6)
        assertTrue(o.moves.isEmpty())
        assertTrue(o.again)
        assertFalse(o.turnEnded)
        assertEquals(0, g.current)
        assertEquals(Phase.ROLL, g.phase)
        assertEquals(1, g.sixesInRow)
    }

    // ── 3연속 6 ──

    @Test
    fun `3연속 6이면 세 번째 6은 무효이고 차례가 끝난다`() {
        val g = game()
        g.setupPiece(0, 0, 10)
        g.rollWith(6); g.move(0)
        assertEquals(16, g.pos(0, 0))
        g.rollWith(6); g.move(0)
        assertEquals(22, g.pos(0, 0))
        assertEquals(2, g.sixesInRow)
        val o = g.rollWith(6)
        assertTrue(o.thirdSix)
        assertEquals(3, o.sixesInRow)
        assertTrue(o.moves.isEmpty())
        assertTrue(o.turnEnded)
        assertEquals("세 번째 6은 이동하지 않음", 22, g.pos(0, 0))
        assertEquals(1, g.current)
        assertEquals(0, g.sixesInRow)
        assertEquals(Phase.ROLL, g.phase)
    }

    @Test
    fun `6 사이에 다른 눈이 나오면 연속이 끊긴다`() {
        val g = game()
        g.setupPiece(0, 0, 0)
        g.rollWith(6); g.move(0)
        // 잡기 보너스로 이어진 굴림에서 4 → 연속 0
        g.setupPiece(1, 0, posAt(g, 1, cell(g, 0, 10)))
        g.rollWith(4)
        assertEquals(0, g.sixesInRow)
        val m = g.move(0)!!
        assertTrue(m.byCapture)
        assertTrue(m.extraRoll)
        g.rollWith(6); g.move(0)
        g.rollWith(6); g.move(0)
        assertEquals(2, g.sixesInRow)
        assertEquals(0, g.current)
    }

    @Test
    fun `움직일 말이 없을 때 나온 6도 연속에 포함된다`() {
        val g = game()
        for (k in 0..3) g.setupPiece(0, k, GameConfig.HOME - 1)
        assertTrue(g.rollWith(6).again)
        assertTrue(g.rollWith(6).again)
        val o = g.rollWith(6)
        assertTrue(o.thirdSix)
        assertTrue(o.turnEnded)
        assertEquals(1, g.current)
    }

    // ── 안전 칸 ──

    @Test
    fun `별 칸과 출발 칸에서는 잡을 수 없고 함께 선다`() {
        val g = game()
        // 별 칸: 자리 0 기준 진행 8 (바깥 8번)
        val star = cell(g, 0, 8)
        assertTrue(LudoBoard.STAR[star])
        g.setupPiece(0, 0, 5)
        g.setupPiece(1, 0, posAt(g, 1, star))
        g.rollWith(3)
        val m = g.move(0)!!
        assertFalse(m.move.capture)
        assertEquals(posAt(g, 1, star), g.pos(1, 0))
        assertEquals(2, g.piecesOnTrack(star).size)
        // 상대 출발 칸 (자리 2 출발 = 바깥 26번 = 자리 0 진행 26)
        g.setupPiece(1, 1, 0)
        g.setupTurn(0)
        g.setupPiece(0, 1, 23)
        g.rollWith(3)
        val m2 = g.move(1)!!
        assertFalse("상대 출발 칸은 안전", m2.move.capture)
        assertEquals(0, g.pos(1, 1))
    }

    @Test
    fun `내 출발 칸에 선 상대 말도 출발할 때 잡지 않는다`() {
        val g = game()
        g.setupPiece(1, 0, posAt(g, 1, LudoBoard.startOf(0)))
        val o = g.rollWith(6)
        assertTrue(o.moves.all { !it.capture })
        g.move(0)
        assertEquals(0, g.pos(0, 0))
        assertEquals(posAt(g, 1, 0), g.pos(1, 0))
    }

    @Test
    fun `같은 색 말이 겹쳐도 길을 막지 않는다`() {
        val g = game()
        // 상대 말 두 개가 20번 칸에 겹쳐 있어도 지나갈 수 있음
        val n = cell(g, 0, 20)
        assertFalse(LudoBoard.SAFE[n])
        g.setupPiece(1, 0, posAt(g, 1, n))
        g.setupPiece(1, 1, posAt(g, 1, n))
        g.setupPiece(0, 0, 18)
        g.rollWith(5)
        val m = g.move(0)!!
        assertEquals(23, g.pos(0, 0))
        assertFalse("지나가기만 하면 잡지 않음", m.move.capture)
        assertEquals(2, g.piecesOnTrack(n).size)
        // 내 말끼리도 같은 칸에 설 수 있음
        g.setupTurn(0)
        g.setupPiece(0, 1, 20)
        g.rollWith(3)
        g.move(1)
        assertEquals(23, g.pos(0, 1))
        assertEquals(2, g.piecesOnTrack(cell(g, 0, 23)).size)
    }

    // ── 잡기 ──

    @Test
    fun `상대 말 위에 멈추면 대기로 돌려보내고 한 번 더 굴린다`() {
        val g = game()
        val n = cell(g, 0, 12)
        assertFalse(LudoBoard.SAFE[n])
        g.setupPiece(0, 0, 9)
        g.setupPiece(1, 0, posAt(g, 1, n))
        g.setupPiece(1, 1, posAt(g, 1, n))
        val o = g.rollWith(3)
        assertTrue(o.moves[0].capture)
        assertArrayEquals(intArrayOf(4, 5), o.moves[0].captures)
        val m = g.move(0)!!
        assertTrue(m.byCapture)
        assertTrue(m.extraRoll)
        assertFalse(m.bySix)
        assertEquals(-1, g.pos(1, 0))
        assertEquals(-1, g.pos(1, 1))
        assertEquals(4, g.yardCount(1))
        assertEquals(0, g.current)
        assertEquals(Phase.ROLL, g.phase)
    }

    @Test
    fun `6으로 잡아도 보너스 굴림은 한 번`() {
        val g = game()
        val n = cell(g, 0, 16)
        g.setupPiece(0, 0, 10)
        g.setupPiece(1, 0, posAt(g, 1, n))
        g.rollWith(6)
        val m = g.move(0)!!
        assertTrue(m.bySix && m.byCapture && m.extraRoll)
        g.rollWith(2)
        g.move(0)
        assertEquals("보너스 한 번만 → 차례 넘어감", 1, g.current)
    }

    @Test
    fun `집 통로에서는 잡히지 않는다`() {
        val g = game(4)
        g.setupPiece(0, 0, 52)
        // 다른 자리 말은 자기 통로만 다니므로 통로 칸은 바깥 길이 아님
        assertEquals(-1, LudoBoard.trackIndex(0, 52))
        g.setupTurn(1)
        g.setupPiece(1, 0, 10)
        g.rollWith(2)
        val m = g.move(0)!!
        assertFalse(m.move.capture)
        assertEquals(52, g.pos(0, 0))
    }

    // ── 집 정확히 ──

    @Test
    fun `집은 정확한 눈으로만 들어간다`() {
        val g = game()
        g.setupPiece(0, 0, 53) // 집까지 3
        g.setupPiece(0, 1, 30)
        val o = g.rollWith(4)
        assertEquals("53 말은 4로 못 감", listOf(1), o.moves.map { it.piece })
        assertNull(g.legalMove(0))
        assertNull(g.move(0))
        g.move(1)
        g.setupTurn(0)
        val o2 = g.rollWith(3)
        assertEquals(listOf(0, 1), o2.moves.map { it.piece })
        val m = g.move(0)!!
        assertTrue(m.move.home)
        assertArrayEquals(intArrayOf(54, 55, 56), m.move.path)
        assertEquals(GameConfig.HOME, g.pos(0, 0))
        assertEquals(1, g.homeCount(0))
        assertTrue("집 보너스 없음", m.turnEnded)
    }

    @Test
    fun `넘치는 눈만 있으면 움직일 말이 없어 차례가 넘어간다`() {
        val g = game()
        for (k in 0..2) g.setupPiece(0, k, GameConfig.HOME)
        g.setupPiece(0, 3, 54)
        val o = g.rollWith(5)
        assertTrue(o.moves.isEmpty())
        assertTrue(o.turnEnded)
        assertEquals(1, g.current)
    }

    @Test
    fun `바깥 50 다음은 자기 집 통로`() {
        val g = game()
        g.setupPiece(0, 0, 48)
        g.rollWith(4)
        val m = g.move(0)!!
        assertArrayEquals(intArrayOf(49, 50, 51, 52), m.move.path)
        val pt = FloatArray(2)
        LudoBoard.point(0, 0, 52, pt)
        assertEquals(LudoBoard.LANE_X[0][1], pt[0], 0.001f)
        assertEquals(LudoBoard.LANE_Y[0][1], pt[1], 0.001f)
    }

    // ── 이동 목록 ──

    @Test
    fun `이동 목록은 집에 든 말을 빼고 말 번호 순`() {
        val g = game()
        g.setupPiece(0, 0, GameConfig.HOME)
        g.setupPiece(0, 1, 3)
        g.setupPiece(0, 2, -1)
        g.setupPiece(0, 3, 40)
        assertEquals(listOf(1, 3), g.movesFor(0, 2).map { it.piece })
        assertEquals(listOf(1, 2, 3), g.movesFor(0, 6).map { it.piece })
        val o = g.rollWith(6)
        assertEquals(3, o.moves.size)
        assertFalse(g.onlyOneChoice)
        assertNotNull(g.legalMove(2))
        assertNull(g.legalMove(0))
        assertEquals(9, g.legalMove(1)!!.to)
        assertEquals(46, g.legalMove(3)!!.to)
        // 굴리기 단계에서는 이동 없음
        g.move(1)
        assertNull(g.legalMove(1))
        assertTrue(g.legalMoves.isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun `고르는 중에는 굴릴 수 없다`() {
        val g = game()
        g.rollWith(6)
        g.rollWith(3)
    }

    // ── 순위 ──

    @Test
    fun `모두 집에 넣은 순서로 순위, 한 명 남으면 꼴찌`() {
        val g = game(3)
        for (k in 0..2) g.setupPiece(1, k, GameConfig.HOME)
        g.setupPiece(1, 3, 55)
        g.setupTurn(1)
        g.rollWith(1)
        val m = g.move(3)!!
        assertTrue(m.playerFinished)
        assertEquals(1, m.rank)
        assertFalse(m.gameOver)
        assertEquals(2, g.current)
        // 1위는 차례에서 빠짐
        for (k in 0..2) g.setupPiece(0, k, GameConfig.HOME)
        g.setupPiece(0, 3, 50)
        val skip = g.rollWith(1) // 다온: 대기 말뿐 → 자동으로 넘어감
        assertTrue(skip.turnEnded)
        assertEquals("1위(나래)는 건너뜀", 0, g.current)
        g.rollWith(6)
        val last = g.move(3)!!
        assertTrue(last.playerFinished)
        assertEquals(2, last.rank)
        assertTrue(last.gameOver)
        assertEquals(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(2, 1, 3), g.ranks)
    }

    @Test
    fun `여기서 종료하면 남은 사람은 집 수 → 진행 합 순`() {
        val g = game(4)
        // 다온이 먼저 다 넣음 (1위)
        for (k in 0..2) g.setupPiece(2, k, GameConfig.HOME)
        g.setupPiece(2, 3, 55)
        g.setupTurn(2)
        g.rollWith(1)
        assertTrue(g.move(3)!!.playerFinished)
        g.setupPiece(0, 0, GameConfig.HOME); g.setupPiece(0, 1, 3)
        g.setupPiece(1, 0, 40); g.setupPiece(1, 1, 40)
        g.setupPiece(3, 0, 40); g.setupPiece(3, 1, 40)
        val r = g.stopHere()
        assertArrayEquals(intArrayOf(2, 3, 1, 3), r)
        assertTrue(g.stoppedEarly)
        assertEquals(Phase.OVER, g.phase)
    }

    /** 진행 합 계산 확인 */
    @Test
    fun `진행 합은 대기 0 출발 1 집 57`() {
        val g = game()
        assertEquals(0, g.progressSum(0))
        g.setupPiece(0, 0, 0)
        g.setupPiece(0, 1, GameConfig.HOME)
        assertEquals(1 + 57, g.progressSum(0))
    }

    @Test
    fun `무작위로 끝까지 두면 항상 끝나고 순위가 1부터 인원까지`() {
        for (players in 2..4) for (seed in 1..20) {
            val g = LudoGame(players, Random(seed))
            val pick = Random(seed + 100)
            var guard = 0
            while (g.phase != Phase.OVER) {
                check(guard++ < 20000)
                if (g.phase == Phase.ROLL) g.roll()
                else g.move(g.legalMoves[pick.nextInt(g.legalMoves.size)].piece)
                // 같은 칸(안전 칸 제외)에 두 색이 서 있지 않음
                for (n in 0 until 52) if (!LudoBoard.SAFE[n]) {
                    val on = g.piecesOnTrack(n)
                    for (a in on) for (b in on) assertEquals(a / 4, b / 4)
                }
            }
            val r = g.ranks!!
            assertEquals((1..players).toList(), r.sorted())
            assertEquals(players - 1, g.finishedCount)
        }
    }
}
