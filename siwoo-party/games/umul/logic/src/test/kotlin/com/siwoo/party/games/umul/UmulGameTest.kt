package com.siwoo.party.games.umul

import com.siwoo.party.games.umul.UmulBoard.BL
import com.siwoo.party.games.umul.UmulBoard.BR
import com.siwoo.party.games.umul.UmulBoard.C
import com.siwoo.party.games.umul.UmulBoard.TL
import com.siwoo.party.games.umul.UmulBoard.TR
import com.siwoo.party.games.umul.UmulRound.Companion.EMPTY
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UmulGameTest {

    /** 순서대로 옮기기 (전부 MOVED 여야 함) */
    private fun play(r: UmulRound, vararg from: Int) {
        for (f in from) assertSame("점 $f 에서 옮기기", Move.MOVED, r.move(f))
    }

    /** 상대를 가두지 않는 수 중 번호가 가장 작은 말 (없으면 아무 수) — 무승부까지 끌기용 */
    private fun safeMove(r: UmulRound): Int {
        var any = -1
        for (p in 0 until UmulBoard.POINTS) if (r.canMove(p)) {
            if (!r.wouldTrap(p)) return p
            if (any < 0) any = p
        }
        return any
    }

    // ── 판 모양 (선·이웃) ──

    @Test
    fun `선은 일곱 개이고 우물 쪽 아래 변은 연결이 없다`() {
        assertEquals(7, UmulBoard.LINES.size)
        assertFalse("우물 쪽 BL–BR 은 연결 없음", UmulBoard.adjacent(BL, BR))
        assertFalse(UmulBoard.adjacent(BR, BL))
        assertArrayEquals(intArrayOf(BL, BR), UmulBoard.WELL)
        // 대각선 4개 (귀퉁이 ↔ 가운데)
        for (k in intArrayOf(TL, TR, BL, BR)) {
            assertTrue(UmulBoard.adjacent(k, C))
            assertTrue(UmulBoard.adjacent(C, k))
        }
        // 세 변
        assertTrue(UmulBoard.adjacent(TL, TR))
        assertTrue(UmulBoard.adjacent(TL, BL))
        assertTrue(UmulBoard.adjacent(TR, BR))
        // 마주 보는 귀퉁이(대각선 건너편)는 가운데를 거쳐야 함
        assertFalse(UmulBoard.adjacent(TL, BR))
        assertFalse(UmulBoard.adjacent(TR, BL))
        // 자기 자신·판 밖
        for (p in 0 until UmulBoard.POINTS) assertFalse(UmulBoard.adjacent(p, p))
        assertFalse(UmulBoard.adjacent(-1, C))
        assertFalse(UmulBoard.adjacent(C, 5))
    }

    @Test
    fun `이웃 목록 — 우물 옆 귀퉁이는 이웃이 둘뿐`() {
        assertArrayEquals(intArrayOf(TR, BL, C), UmulBoard.NEIGHBORS[TL])
        assertArrayEquals(intArrayOf(TL, BR, C), UmulBoard.NEIGHBORS[TR])
        assertArrayEquals(intArrayOf(TL, C), UmulBoard.NEIGHBORS[BL])
        assertArrayEquals(intArrayOf(TR, C), UmulBoard.NEIGHBORS[BR])
        assertArrayEquals(intArrayOf(TL, TR, BL, BR), UmulBoard.NEIGHBORS[C])
        // 대칭: a 가 b 의 이웃이면 b 도 a 의 이웃
        for (a in 0 until UmulBoard.POINTS) for (b in UmulBoard.NEIGHBORS[a]) assertTrue(a in UmulBoard.NEIGHBORS[b])
        val degreeSum = UmulBoard.NEIGHBORS.sumOf { it.size }
        assertEquals("선 7개 × 양 끝", 14, degreeSum)
    }

    @Test
    fun `처음 배치 — 1번은 왼쪽 변, 2번은 오른쪽 변, 가운데가 빈다`() {
        val r = UmulRound(0)
        assertArrayEquals(intArrayOf(0, 1, 0, 1, EMPTY), r.owners)
        assertEquals(C, r.empty)
        assertEquals(0, r.current)
        assertSame(RoundState.PLAY, r.state)
        assertEquals(GameConfig.MOVE_LIMIT, r.movesLeft)
        assertTrue(r.isFirstMove)
    }

    // ── 첫 수 금지 ──

    @Test
    fun `첫 수 금지 — 먼저 두는 1번이 우물 옆 말을 가운데로 올리면 상대가 바로 갇히므로 금지`() {
        val r = UmulRound(0)
        assertTrue("BL→C 는 2번을 가둠", r.wouldTrap(BL))
        assertTrue(r.isForbidden(BL))
        assertFalse(r.canMove(BL))
        assertSame(Move.FORBIDDEN_FIRST, r.move(BL))
        // 아무것도 바뀌지 않음
        assertArrayEquals(intArrayOf(0, 1, 0, 1, EMPTY), r.owners)
        assertEquals(0, r.moveCount)
        assertEquals(0, r.current)
        // 다른 첫 수(TL→C)는 됨
        assertFalse(r.wouldTrap(TL))
        assertTrue(r.canMove(TL))
        val out = IntArray(4)
        assertEquals(1, r.movable(out))
        assertEquals(TL, out[0])
        assertSame(Move.MOVED, r.move(TL))
        assertEquals(TL, r.lastFrom)
        assertEquals(C, r.lastTo)
        assertEquals(1, r.current)
        assertSame(RoundState.PLAY, r.state)
    }

    @Test
    fun `첫 수 금지 — 2번이 먼저 둘 때도 우물 옆 BR 은 못 올림`() {
        val r = UmulRound(1)
        assertTrue(r.isForbidden(BR))
        assertSame(Move.FORBIDDEN_FIRST, r.move(BR))
        assertTrue(r.canMove(TR))
        assertSame(Move.MOVED, r.move(TR))
        assertEquals(0, r.current)
    }

    @Test
    fun `첫 수가 지나면 같은 가두기 수가 허용되어 이긴다`() {
        val r = UmulRound(0)
        // TL→C, TR→TL, C→TR, TL→C, TR→TL, C→TR : 다시 처음 배치로 1번 차례 (이번엔 첫 수가 아님)
        play(r, TL, TR, C, TL, TR, C)
        assertArrayEquals(intArrayOf(0, 1, 0, 1, EMPTY), r.owners)
        assertEquals(0, r.current)
        assertFalse(r.isFirstMove)
        assertFalse("첫 수가 아니면 금지 아님", r.isForbidden(BL))
        assertTrue(r.canMove(BL))
        assertSame(Move.MOVED, r.move(BL))
        assertSame(RoundState.WON, r.state)
        assertEquals(0, r.winner)
        assertEquals(1, r.loser)
        assertEquals(7, r.moveCount)
    }

    @Test
    fun `첫 수 금지 때문에 둘 수가 하나도 없어지는 일은 없다 — 모든 배치`() {
        // 금지 수를 빼면 둘 수가 없을 땐 금지를 풀어 주는 안전장치 — 어떤 배치에서도 "갇히지 않았는데 못 두는" 일이 없어야 함
        var checked = 0
        for (start in allStarts()) for (p in 0..1) {
            val r = UmulRound(p, start = start)
            if (!r.hasAnyMove(p)) continue
            val out = IntArray(4)
            assertTrue("둘 수 있는 말이 있으면 금지 때문에 막히지 않음", r.movable(out) > 0)
            checked++
        }
        assertEquals("배치 30가지 × 차례 2 중 갇히지 않은 경우", 56, checked)
    }

    /** 말 2+2 와 빈 점 하나의 모든 배치 */
    private fun allStarts(): List<IntArray> {
        val res = ArrayList<IntArray>()
        for (e in 0 until 5) for (a in 0 until 5) for (b in a + 1 until 5) {
            if (a == e || b == e) continue
            val o = IntArray(5) { 1 }
            o[e] = EMPTY
            o[a] = 0
            o[b] = 0
            res.add(o)
        }
        return res
    }

    // ── 갇힘 판정 ──

    @Test
    fun `갇힘 판정 — 차례가 된 사람이 움직일 말이 없으면 그 사람이 진다`() {
        val r = UmulRound(0)
        // TL→C, TR→TL, C→TR, TL→C, TR→TL, BR→TR : 빈 점 BR 의 이웃(TR·C)이 모두 2번 말 → 1번 갇힘
        play(r, TL, TR, C, TL, TR)
        assertSame(RoundState.PLAY, r.state)
        play(r, BR)
        assertSame(RoundState.WON, r.state)
        assertEquals(BR, r.empty)
        assertFalse(r.hasAnyMove(0))
        assertTrue(r.hasAnyMove(1))
        assertEquals(1, r.winner)
        assertEquals(0, r.loser)
        assertEquals("갇힌 사람 차례에서 멈춤", 1, r.current)
        // 끝난 판엔 둘 수 없음
        assertSame(Move.OVER, r.move(TL))
        assertEquals(6, r.moveCount)
    }

    @Test
    fun `갇힘 판정 — 빈 점의 이웃만 본다`() {
        // 빈 점 C 는 모든 점의 이웃 → 아무도 안 갇힘
        assertTrue(UmulRound.hasAnyMove(intArrayOf(0, 1, 0, 1, EMPTY), 0))
        assertTrue(UmulRound.hasAnyMove(intArrayOf(0, 1, 0, 1, EMPTY), 1))
        // 빈 점 BL (이웃 TL·C) 둘 다 2번 → 1번 갇힘, 2번은 움직일 수 있음
        val o = intArrayOf(1, 0, EMPTY, 0, 1)
        assertFalse(UmulRound.hasAnyMove(o, 0))
        assertTrue(UmulRound.hasAnyMove(o, 1))
        // 빈 점 TL (이웃 TR·BL·C) 은 셋 중 둘을 상대가 가져도 한 곳이 남음 → 귀퉁이 위쪽에선 안 갇힘
        assertTrue(UmulRound.hasAnyMove(intArrayOf(EMPTY, 1, 0, 0, 1), 0))
    }

    @Test
    fun `막힌 말·남의 말·빈 점은 못 옮긴다`() {
        val r = UmulRound(0)
        r.move(TL) // 1번 TL→C, 빈 점 TL
        // 2번 차례: BR 은 TL 과 이어지지 않음
        assertSame(Move.BLOCKED, r.move(BR))
        assertSame(Move.NOT_MINE, r.move(C))
        assertSame(Move.NOT_MINE, r.move(TL))
        assertSame(Move.NOT_MINE, r.move(9))
        assertEquals(1, r.moveCount)
        assertEquals(1, r.current)
        val out = IntArray(4)
        assertEquals(1, r.movable(out))
        assertEquals(TR, out[0])
    }

    // ── 수 한도 (무승부 판) ──

    @Test
    fun `수 한도 — 아무도 갇히지 않고 한도까지 가면 무승부 판`() {
        val r = UmulRound(0)
        while (r.state == RoundState.PLAY) {
            val f = safeMove(r)
            assertTrue(f >= 0)
            assertFalse("안전한 수만 두면 갇히지 않음", r.wouldTrap(f))
            r.move(f)
        }
        assertSame(RoundState.DRAW, r.state)
        assertEquals(GameConfig.MOVE_LIMIT, r.moveCount)
        assertEquals(0, r.movesLeft)
        assertEquals(-1, r.winner)
        assertEquals(-1, r.loser)
        assertSame(Move.OVER, r.move(safeMove(r).coerceAtLeast(0)))
    }

    @Test
    fun `수 한도의 마지막 수로 가두면 무승부가 아니라 승리`() {
        // 7번째 수로 이기는 순서를 한도 7 로
        val r = UmulRound(0, moveLimit = 7)
        play(r, TL, TR, C, TL, TR, C)
        assertEquals(1, r.movesLeft)
        r.move(BL)
        assertSame(RoundState.WON, r.state)
        assertEquals(0, r.winner)
        // 한도 6: 6번째 수가 가두지 않으면 무승부
        val d = UmulRound(0, moveLimit = 6)
        play(d, TL, TR, C, TL, TR, C)
        assertSame(RoundState.DRAW, d.state)
    }

    // ── 3판 2선승 ──

    /** 판 r 에서 starter 가 이기는 순서 (7수) */
    private fun starterWins(r: UmulRound) {
        if (r.starter == 0) play(r, TL, TR, C, TL, TR, C, BL) else play(r, TR, TL, C, TR, TL, C, BR)
        assertSame(RoundState.WON, r.state)
        assertEquals(r.starter, r.winner)
    }

    /** 판 r 에서 starter 가 지는 순서 (6수) */
    private fun starterLoses(r: UmulRound) {
        if (r.starter == 0) play(r, TL, TR, C, TL, TR, BR) else play(r, TR, TL, C, TR, TL, BL)
        assertSame(RoundState.WON, r.state)
        assertEquals(1 - r.starter, r.winner)
    }

    private fun drawRound(r: UmulRound) {
        while (r.state == RoundState.PLAY) r.move(safeMove(r))
        assertSame(RoundState.DRAW, r.state)
    }

    @Test
    fun `3판 2선승 — 먼저 두는 사람이 번갈아, 2판 먼저 이기면 끝`() {
        val m = UmulMatch(1)
        assertEquals(1, m.roundNumber)
        val r1 = m.startRound()
        assertEquals(1, r1.starter)
        starterWins(r1) // 2번 승
        assertEquals(1, m.finishRound())
        assertArrayEquals(intArrayOf(0, 1), m.wins)
        assertFalse(m.isOver)
        assertEquals(2, m.roundNumber)
        val r2 = m.startRound()
        assertEquals("2판은 1번이 먼저", 0, r2.starter)
        starterWins(r2) // 1번 승
        assertEquals(0, m.finishRound())
        assertFalse("1 : 1", m.isOver)
        val r3 = m.startRound()
        assertEquals(1, r3.starter)
        starterLoses(r3) // 1번 승
        assertEquals(0, m.finishRound())
        assertTrue(m.isOver)
        assertArrayEquals(intArrayOf(2, 1), m.wins)
        assertEquals(0, m.winner)
        assertArrayEquals(intArrayOf(1, 2), m.ranks())
        assertEquals(3, m.roundsPlayed)
        assertArrayEquals(intArrayOf(1, 0, 0, -1, -1), m.results)
    }

    @Test
    fun `3판 2선승 — 2 대 0 이면 두 판으로 끝`() {
        val m = UmulMatch(0)
        starterWins(m.startRound())
        m.finishRound()
        starterLoses(m.startRound()) // 2판은 2번이 먼저 → 지면 1번 승
        m.finishRound()
        assertTrue(m.isOver)
        assertArrayEquals(intArrayOf(2, 0), m.wins)
        assertArrayEquals(intArrayOf(1, 2), m.ranks())
    }

    @Test
    fun `진행 중인 판이 있으면 새 판을 못 시작하고, 끝난 대결은 판을 못 연다`() {
        val m = UmulMatch(0)
        val r = m.startRound()
        assertThrows { m.startRound() }
        assertThrows { m.finishRound() }
        starterWins(r)
        m.finishRound()
        starterLoses(m.startRound())
        m.finishRound()
        assertTrue(m.isOver)
        assertThrows { m.startRound() }
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalStateException) {
            return
        }
        throw AssertionError("예외가 나야 함")
    }

    @Test
    fun `무승부 판은 점수 없이 다음 판 — 먼저 두는 사람은 계속 번갈아`() {
        val m = UmulMatch(0)
        drawRound(m.startRound())
        assertEquals(-1, m.finishRound())
        assertArrayEquals(intArrayOf(0, 0), m.wins)
        assertFalse(m.isOver)
        val r2 = m.startRound()
        assertEquals(1, r2.starter)
        starterWins(r2)
        m.finishRound()
        val r3 = m.startRound()
        assertEquals(0, r3.starter)
        starterLoses(r3)
        m.finishRound()
        assertTrue("2번이 2승", m.isOver)
        assertArrayEquals(intArrayOf(2, 1), m.ranks())
        assertArrayEquals(intArrayOf(-1, 1, 1, -1, -1), m.results)
    }

    @Test
    fun `판 수 한도 — 무승부 판이 이어지면 이긴 판이 많은 사람, 같으면 둘 다 1위`() {
        val m = UmulMatch(0)
        repeat(GameConfig.MAX_ROUNDS) {
            assertFalse(m.isOver)
            drawRound(m.startRound())
            m.finishRound()
        }
        assertTrue(m.isOver)
        assertEquals(-1, m.winner)
        assertArrayEquals(intArrayOf(1, 1), m.ranks())

        // 1승 + 무승부 4판 → 1승한 사람 승리
        val m2 = UmulMatch(0)
        starterLoses(m2.startRound()) // 2번 승
        m2.finishRound()
        repeat(GameConfig.MAX_ROUNDS - 1) {
            drawRound(m2.startRound())
            m2.finishRound()
        }
        assertTrue(m2.isOver)
        assertEquals(1, m2.winner)
        assertArrayEquals(intArrayOf(2, 1), m2.ranks())
    }

    @Test
    fun `완벽하게 두면 첫 수 금지 덕분에 먼저 두는 사람이 바로 이기지 못한다`() {
        // 처음 배치에서 먼저 두는 사람의 수는 하나뿐(TL→C)이고, 그 뒤 상대도 바로 갇히지 않는다
        for (s in 0..1) {
            val r = UmulRound(s)
            val out = IntArray(4)
            assertEquals(1, r.movable(out))
            r.move(out[0])
            assertSame(RoundState.PLAY, r.state)
            assertTrue(r.hasAnyMove(r.current))
        }
    }
}
