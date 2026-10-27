package com.siwoo.party.games.janggi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class JanggiGameTest {

    private val C = Side.CHO
    private val H = Side.HAN

    /** 빈 판 + 궁 둘 (서로 다른 세로줄에 두어 빅장이 안 되게) */
    private fun empty(choGeneral: Pair<Int, Int> = 3 to 9, hanGeneral: Pair<Int, Int> = 5 to 0, bikjang: Boolean = false, undo: Boolean = true): JanggiGame {
        val g = JanggiGame(undoEnabled = undo, bikjangRule = bikjang)
        g.clearBoard()
        g.put(choGeneral.first, choGeneral.second, C, Kind.GENERAL)
        g.put(hanGeneral.first, hanGeneral.second, H, Kind.GENERAL)
        g.reset(C)
        return g
    }

    private val buf = IntArray(JanggiGame.MAX_TARGETS)

    private fun pseudo(g: JanggiGame, x: Int, y: Int): Set<Pair<Int, Int>> {
        val n = g.pseudoTargets(g.index(x, y), buf)
        return (0 until n).map { buf[it] % 9 to buf[it] / 9 }.toSet()
    }

    private fun legal(g: JanggiGame, x: Int, y: Int): Set<Pair<Int, Int>> {
        val n = g.legalTargets(g.index(x, y), buf)
        return (0 until n).map { buf[it] % 9 to buf[it] / 9 }.toSet()
    }

    private fun mv(g: JanggiGame, fx: Int, fy: Int, tx: Int, ty: Int): Boolean = g.move(g.index(fx, fy), g.index(tx, ty))

    private fun kindAt(g: JanggiGame, x: Int, y: Int): Kind? = g.at(x, y).let { if (it == Piece.EMPTY) null else Piece.kind(it) }
    private fun sideAt(g: JanggiGame, x: Int, y: Int): Int = Piece.side(g.at(x, y))

    // ── 상차림 · 시작 배치 ──

    @Test
    fun `시작 배치 - 차 포 사 궁 졸 자리와 16개씩`() {
        val g = JanggiGame()
        for (side in 0..1) {
            val back = if (side == C) 9 else 0
            val f = if (side == C) -1 else 1
            assertEquals(Kind.CHARIOT, kindAt(g, 0, back)); assertEquals(Kind.CHARIOT, kindAt(g, 8, back))
            assertEquals(Kind.GUARD, kindAt(g, 3, back)); assertEquals(Kind.GUARD, kindAt(g, 5, back))
            assertNull(kindAt(g, 4, back))
            assertEquals(Kind.GENERAL, kindAt(g, 4, back + f))
            assertEquals(Kind.CANNON, kindAt(g, 1, back + 2 * f)); assertEquals(Kind.CANNON, kindAt(g, 7, back + 2 * f))
            for (x in 0..8 step 2) assertEquals(Kind.SOLDIER, kindAt(g, x, back + 3 * f))
            for (x in 0..8) if (x != 4) assertEquals(side, sideAt(g, x, back))
            assertEquals(16, g.board.count { it != Piece.EMPTY && Piece.side(it) == side })
        }
        assertEquals(C, g.turn)
        assertEquals(g.index(4, 8), g.generalSquare(C))
        assertEquals(g.index(4, 1), g.generalSquare(H))
        assertFalse(g.inCheck(C)); assertFalse(g.inCheck(H)); assertFalse(g.isBikjang())
    }

    @Test
    fun `상차림 네 가지 - 초는 왼쪽부터 한은 판 건너편에서 본 왼쪽부터`() {
        val M = Kind.HORSE
        val S = Kind.ELEPHANT
        val expect = mapOf(
            Formation.MSMS to listOf(M, S, M, S),
            Formation.SMSM to listOf(S, M, S, M),
            Formation.MSSM to listOf(M, S, S, M),
            Formation.SMMS to listOf(S, M, M, S),
        )
        for ((form, kinds) in expect) {
            assertEquals(form.label, kinds.joinToString("") { if (it == M) "마" else "상" })
            val g = JanggiGame(choForm = form, hanForm = form)
            // 초: x = 1, 2, 6, 7
            assertEquals(kinds, listOf(1, 2, 6, 7).map { kindAt(g, it, 9) })
            // 한: 자기 왼쪽 = 화면 오른쪽 → x = 7, 6, 2, 1
            assertEquals(kinds, listOf(7, 6, 2, 1).map { kindAt(g, it, 0) })
        }
        // 양쪽 다르게
        val g = JanggiGame(choForm = Formation.SMSM, hanForm = Formation.MSSM)
        assertEquals(Kind.ELEPHANT, kindAt(g, 1, 9)); assertEquals(Kind.HORSE, kindAt(g, 2, 9))
        assertEquals(Kind.HORSE, kindAt(g, 1, 0)); assertEquals(Kind.ELEPHANT, kindAt(g, 2, 0))
        assertEquals(Kind.ELEPHANT, kindAt(g, 6, 0)); assertEquals(Kind.HORSE, kindAt(g, 7, 0))
    }

    @Test
    fun `초가 먼저 두고 상대 기물은 못 움직임`() {
        val g = JanggiGame()
        assertFalse("한 기물은 초 차례에 못 둠", mv(g, 0, 3, 0, 4))
        assertTrue(mv(g, 0, 6, 0, 5))
        assertEquals(H, g.turn)
        assertFalse(mv(g, 2, 6, 2, 5))
        assertTrue(mv(g, 0, 3, 1, 3))
        assertEquals(C, g.turn)
        assertEquals(2, g.moveCount)
        assertFalse("빈 칸", mv(g, 4, 4, 4, 3))
        assertFalse("규칙에 없는 이동", mv(g, 2, 6, 2, 4))
    }

    // ── 기물별 이동 ──

    @Test
    fun `차 - 가로세로로 막힐 때까지 상대는 잡고 내 기물 앞에서 멈춤`() {
        val g = empty()
        g.put(1, 5, C, Kind.CHARIOT)
        assertEquals(17, pseudo(g, 1, 5).size)
        g.put(1, 2, H, Kind.HORSE)   // 위: 잡을 수 있음
        g.put(4, 5, C, Kind.SOLDIER) // 오른쪽: 내 기물
        val t = pseudo(g, 1, 5)
        assertTrue((1 to 2) in t); assertFalse((1 to 1) in t)
        assertTrue((3 to 5) in t); assertFalse((4 to 5) in t); assertFalse((5 to 5) in t)
        assertTrue((0 to 5) in t); assertTrue((1 to 9) in t)
        assertFalse("대각선은 궁성 밖에서 못 감", (2 to 4) in t)
    }

    @Test
    fun `차 - 궁성 대각선`() {
        val g = empty(choGeneral = 3 to 9, hanGeneral = 4 to 0)
        g.put(3, 7, C, Kind.CHARIOT)
        val t = pseudo(g, 3, 7)
        assertTrue((4 to 8) in t); assertTrue((5 to 9) in t)
        // 가운데가 막히면 반대 모서리 못 감
        g.put(4, 8, C, Kind.SOLDIER)
        val t2 = pseudo(g, 3, 7)
        assertFalse((4 to 8) in t2); assertFalse((5 to 9) in t2)
        g.remove(4, 8)
        // 가운데에서는 네 모서리
        g.remove(3, 7)
        g.put(4, 8, C, Kind.CHARIOT)
        val t3 = pseudo(g, 4, 8)
        assertTrue((3 to 7) in t3); assertTrue((5 to 7) in t3); assertTrue((5 to 9) in t3)
        assertFalse("내 궁", (3 to 9) in t3)
        // 변 가운데(4,7)에서는 대각선 없음
        g.remove(4, 8)
        g.put(4, 7, C, Kind.CHARIOT)
        val t4 = pseudo(g, 4, 7)
        assertFalse((3 to 8) in t4); assertFalse((5 to 8) in t4); assertFalse((3 to 6) in t4)
        // 상대 궁성 대각선으로 잡기
        g.remove(4, 7)
        g.put(3, 2, C, Kind.CHARIOT)
        g.put(5, 0, H, Kind.GUARD)
        val t5 = pseudo(g, 3, 2)
        assertTrue((4 to 1) in t5); assertTrue("대각선 끝 상대 사 잡기", (5 to 0) in t5)
    }

    @Test
    fun `마 - 여덟 곳과 멱`() {
        val g = empty()
        g.put(4, 5, C, Kind.HORSE)
        assertEquals(setOf(6 to 4, 6 to 6, 2 to 4, 2 to 6, 3 to 3, 5 to 3, 3 to 7, 5 to 7), pseudo(g, 4, 5))
        // 위쪽 멱 (4,4) 막힘 → (3,3)(5,3) 못 감
        g.put(4, 4, H, Kind.SOLDIER)
        val t = pseudo(g, 4, 5)
        assertFalse((3 to 3) in t); assertFalse((5 to 3) in t)
        assertEquals(6, t.size)
        // 대각선 칸에 있는 기물은 멱이 아님
        g.remove(4, 4)
        g.put(5, 4, C, Kind.SOLDIER)
        assertEquals(8, pseudo(g, 4, 5).size)
        // 판 가장자리
        val e = empty()
        e.put(0, 9, C, Kind.HORSE)
        assertEquals(setOf(1 to 7, 2 to 8), pseudo(e, 0, 9))
    }

    @Test
    fun `상 - 여덟 곳과 두 군데 멱`() {
        val g = empty()
        g.put(4, 5, C, Kind.ELEPHANT)
        assertEquals(setOf(2 to 2, 6 to 2, 2 to 8, 6 to 8, 1 to 3, 7 to 3, 1 to 7, 7 to 7), pseudo(g, 4, 5))
        // 첫 멱 (4,4) → 위쪽 둘 막힘
        g.put(4, 4, C, Kind.SOLDIER)
        var t = pseudo(g, 4, 5)
        assertFalse((2 to 2) in t); assertFalse((6 to 2) in t); assertEquals(6, t.size)
        g.remove(4, 4)
        // 둘째 멱 (5,3) → (6,2) 만 막힘
        g.put(5, 3, H, Kind.SOLDIER)
        t = pseudo(g, 4, 5)
        assertFalse((6 to 2) in t); assertTrue((2 to 2) in t); assertEquals(7, t.size)
        // 도착 칸의 상대는 잡고 내 기물은 못 감
        g.remove(5, 3)
        g.put(2, 2, H, Kind.CHARIOT)
        g.put(7, 3, C, Kind.CHARIOT)
        t = pseudo(g, 4, 5)
        assertTrue((2 to 2) in t); assertFalse((7 to 3) in t)
    }

    @Test
    fun `포 - 한 기물을 넘어야 이동과 공격`() {
        val g = empty()
        g.put(1, 7, C, Kind.CANNON)
        assertTrue("넘을 기물이 없으면 못 움직임", pseudo(g, 1, 7).isEmpty())
        g.put(1, 5, C, Kind.SOLDIER) // 다리
        var t = pseudo(g, 1, 7)
        assertFalse("다리 앞 빈 칸은 못 감", (1 to 6) in t)
        assertFalse("다리 자리도 못 감", (1 to 5) in t)
        assertEquals(setOf(1 to 4, 1 to 3, 1 to 2, 1 to 1, 1 to 0), t)
        // 넘은 뒤 상대 기물은 잡고, 그 너머는 못 감
        g.put(1, 2, H, Kind.HORSE)
        t = pseudo(g, 1, 7)
        assertEquals(setOf(1 to 4, 1 to 3, 1 to 2), t)
        // 둘을 넘을 수 없음
        g.put(1, 3, H, Kind.SOLDIER)
        t = pseudo(g, 1, 7)
        assertEquals(setOf(1 to 4, 1 to 3), t)
    }

    @Test
    fun `포 - 포를 넘을 수도 잡을 수도 없음`() {
        val g = empty()
        g.put(1, 7, C, Kind.CANNON)
        g.put(1, 5, H, Kind.CANNON) // 다리가 포
        assertTrue("포는 다리가 될 수 없음", pseudo(g, 1, 7).filter { it.first == 1 }.isEmpty())
        g.remove(1, 5)
        g.put(1, 5, C, Kind.SOLDIER)
        g.put(1, 2, H, Kind.CANNON)
        val t = pseudo(g, 1, 7)
        assertTrue((1 to 3) in t)
        assertFalse("상대 포는 못 잡음", (1 to 2) in t)
        assertFalse((1 to 1) in t)
        // 가로 방향도 같음: 다리 다음 상대 차는 잡음
        g.put(4, 7, H, Kind.SOLDIER)
        g.put(7, 7, H, Kind.CHARIOT)
        val t2 = pseudo(g, 1, 7)
        assertTrue((5 to 7) in t2); assertTrue((6 to 7) in t2); assertTrue((7 to 7) in t2); assertFalse((8 to 7) in t2)
    }

    @Test
    fun `포 - 궁성 대각선은 가운데 기물을 넘어서`() {
        val g = empty(choGeneral = 3 to 9, hanGeneral = 4 to 0)
        g.put(3, 2, C, Kind.CANNON)
        assertFalse("가운데가 비면 대각선 못 감", (5 to 0) in pseudo(g, 3, 2))
        g.put(4, 1, H, Kind.GUARD)
        assertTrue("가운데 사를 넘어 빈 모서리로", (5 to 0) in pseudo(g, 3, 2))
        g.put(5, 0, H, Kind.CHARIOT)
        assertTrue("넘어서 차 잡기", (5 to 0) in pseudo(g, 3, 2))
        g.put(5, 0, H, Kind.CANNON)
        assertFalse("포는 못 잡음", (5 to 0) in pseudo(g, 3, 2))
        g.remove(5, 0)
        g.put(4, 1, C, Kind.CANNON)
        assertFalse("가운데가 포면 못 넘음", (5 to 0) in pseudo(g, 3, 2))
        // 가운데 자리에서는 대각선으로 넘을 곳이 없음 (한 칸뿐)
        val g2 = empty(choGeneral = 3 to 9, hanGeneral = 4 to 0)
        g2.put(4, 8, C, Kind.CANNON)
        g2.put(3, 7, C, Kind.SOLDIER)
        assertFalse((2 to 6) in pseudo(g2, 4, 8))
    }

    @Test
    fun `졸병 - 앞과 옆, 뒤로는 못 감`() {
        val g = empty()
        g.put(4, 5, C, Kind.SOLDIER)
        assertEquals(setOf(4 to 4, 3 to 5, 5 to 5), pseudo(g, 4, 5))
        g.put(4, 4, H, Kind.SOLDIER)
        assertEquals("한 병은 아래가 앞", setOf(4 to 5, 3 to 4, 5 to 4), pseudo(g, 4, 4))
        // 초 졸이 (4,4) 한 병을 잡을 수 있음
        assertTrue((4 to 4) in pseudo(g, 4, 5))
        // 끝줄에서는 옆으로만
        val e = empty()
        e.put(0, 0, C, Kind.SOLDIER)
        assertEquals(setOf(1 to 0), pseudo(e, 0, 0))
    }

    @Test
    fun `졸병 - 상대 궁성 대각선은 앞쪽으로만`() {
        val g = empty(choGeneral = 3 to 9, hanGeneral = 5 to 0)
        g.put(3, 2, C, Kind.SOLDIER)
        assertEquals(setOf(3 to 1, 2 to 2, 4 to 2, 4 to 1), pseudo(g, 3, 2))
        g.remove(3, 2)
        g.put(4, 1, C, Kind.SOLDIER)
        assertEquals("가운데: 앞·옆 + 앞쪽 두 모서리", setOf(4 to 0, 3 to 1, 5 to 1, 3 to 0, 5 to 0), pseudo(g, 4, 1))
        g.remove(4, 1)
        g.put(4, 2, C, Kind.SOLDIER)
        assertEquals("변 가운데에선 대각선 없음", setOf(4 to 1, 3 to 2, 5 to 2), pseudo(g, 4, 2))
        // 한 병이 초 궁성으로
        g.put(5, 7, H, Kind.SOLDIER)
        assertTrue((4 to 8) in pseudo(g, 5, 7))
        assertFalse("궁성 밖 대각선은 없음", (4 to 6) in pseudo(g, 5, 7) || (6 to 8) in pseudo(g, 5, 7))
    }

    @Test
    fun `궁과 사 - 궁성 안에서 선을 따라 한 칸`() {
        val g = empty(choGeneral = 4 to 8, hanGeneral = 5 to 0)
        assertEquals("가운데: 여덟 방향", setOf(3 to 7, 4 to 7, 5 to 7, 3 to 8, 5 to 8, 3 to 9, 4 to 9, 5 to 9), pseudo(g, 4, 8))
        val g2 = empty(choGeneral = 3 to 9, hanGeneral = 5 to 0)
        assertEquals("모서리: 가로·세로 + 가운데로 대각선", setOf(3 to 8, 4 to 9, 4 to 8), pseudo(g2, 3, 9))
        val g3 = empty(choGeneral = 4 to 9, hanGeneral = 5 to 0)
        assertEquals("변 가운데: 대각선 없음", setOf(3 to 9, 5 to 9, 4 to 8), pseudo(g3, 4, 9))
        // 사는 궁성 밖으로 못 나감
        g3.put(3, 7, C, Kind.GUARD)
        assertEquals(setOf(4 to 7, 3 to 8, 4 to 8), pseudo(g3, 3, 7))
        g3.put(5, 8, C, Kind.GUARD)
        assertEquals(setOf(5 to 7, 4 to 8, 5 to 9), pseudo(g3, 5, 8))
        // 한 궁도 같은 규칙
        val g4 = empty(choGeneral = 3 to 9, hanGeneral = 5 to 2)
        assertEquals(setOf(5 to 1, 4 to 2, 4 to 1), pseudo(g4, 5, 2))
    }

    // ── 자살수 금지 ──

    @Test
    fun `자살수 - 핀 걸린 기물과 공격받는 칸으로 가는 궁`() {
        val g = empty(choGeneral = 4 to 9, hanGeneral = 3 to 0)
        g.put(4, 7, C, Kind.HORSE)
        g.put(4, 2, H, Kind.CHARIOT)
        assertFalse(g.inCheck(C))
        assertTrue("마가 비키면 차에게 장군 → 못 움직임", legal(g, 4, 7).isEmpty())
        assertTrue(pseudo(g, 4, 7).isNotEmpty())
        assertFalse(mv(g, 4, 7, 3, 5))
        // 차로 막고 있으면 같은 줄로는 움직일 수 있음
        g.remove(4, 7)
        g.put(4, 7, C, Kind.CHARIOT)
        val t = legal(g, 4, 7)
        assertTrue((4 to 5) in t); assertTrue((4 to 2) in t); assertFalse((3 to 7) in t)
        // 궁은 공격받는 칸으로 못 감: 한 차가 3번 세로줄을 지킴
        val k = empty(choGeneral = 4 to 8, hanGeneral = 5 to 0)
        k.put(3, 0, H, Kind.CHARIOT)
        val kt = legal(k, 4, 8)
        assertFalse((3 to 7) in kt); assertFalse((3 to 8) in kt); assertFalse((3 to 9) in kt)
        assertTrue((5 to 7) in kt)
        assertFalse(mv(k, 4, 8, 3, 8))
    }

    @Test
    fun `자살수 - 포 다리를 치우면 장군 (드러난 장군)`() {
        val g = empty(choGeneral = 4 to 9, hanGeneral = 3 to 0)
        g.put(4, 2, H, Kind.CANNON)
        g.put(4, 6, C, Kind.SOLDIER)
        g.put(4, 7, C, Kind.HORSE)
        assertFalse("다리가 둘이면 장군 아님", g.inCheck(C))
        assertTrue("마가 비키면 졸 하나만 다리 → 장군", legal(g, 4, 7).isEmpty())
        // 졸이 옆으로 비키면 마 하나가 다리 → 장군. 앞 (4,5) 는 같은 줄이라 다리 둘 그대로 → 됨
        assertEquals(setOf(4 to 5), legal(g, 4, 6))
        g.remove(4, 6)
        g.put(4, 4, C, Kind.SOLDIER)
        assertTrue((4 to 3) in legal(g, 4, 4))
        assertFalse((3 to 4) in legal(g, 4, 4))
    }

    @Test
    fun `빅장은 금지수가 아님 - 궁 사이 기물이 비켜도 둘 수 있음`() {
        val g = empty(choGeneral = 4 to 8, hanGeneral = 4 to 1)
        g.put(4, 5, C, Kind.CHARIOT)
        g.put(8, 3, H, Kind.SOLDIER)
        assertTrue((0 to 5) in legal(g, 4, 5))
        assertTrue(mv(g, 4, 5, 0, 5))
        assertTrue(g.isBikjang())
        assertFalse("궁은 궁을 공격하지 않음", g.inCheck(H))
        assertSame(Phase.PLAY, g.phase)
        assertFalse("빅장 옵션 끔이면 불리지 않음", g.bikjangCalled)
        assertTrue(mv(g, 8, 3, 8, 4))
        assertSame("빅장 그대로여도 계속", Phase.PLAY, g.phase)
    }

    // ── 외통 판정 ──

    @Test
    fun `외통 - 두 차로 끝줄 막기`() {
        val g = empty(choGeneral = 3 to 9, hanGeneral = 4 to 0)
        g.put(0, 1, C, Kind.CHARIOT)
        g.put(1, 5, C, Kind.CHARIOT)
        assertFalse(g.inCheck(H))
        assertTrue(mv(g, 1, 5, 1, 0))
        assertTrue(g.inCheck(H))
        assertTrue(g.isCheckmate(H))
        assertSame(Phase.OVER, g.phase)
        assertEquals(C, g.winner)
        assertEquals(EndReason.CHECKMATE, g.endReason)
        assertArrayEquals(intArrayOf(1, 2), g.ranks(0))
        assertArrayEquals(intArrayOf(2, 1), g.ranks(1))
        assertFalse("끝난 판엔 못 둠", mv(g, 4, 0, 4, 1))
    }

    @Test
    fun `외통 - 마가 다리 되는 포 장군, 사가 막아도 여전히 다리`() {
        val g = empty(choGeneral = 3 to 9, hanGeneral = 4 to 0)
        g.put(3, 0, H, Kind.GUARD)
        g.put(5, 0, H, Kind.GUARD)
        g.put(4, 3, C, Kind.CANNON)
        g.put(2, 2, C, Kind.HORSE)
        g.put(3, 3, C, Kind.HORSE)
        assertFalse(g.inCheck(H))
        assertTrue(mv(g, 3, 3, 4, 1))
        assertTrue(g.inCheck(H))
        // 궁이 (4,1) 마를 잡으면 (2,2) 마에게, 사가 잡으면 사가 다리 → 둘 다 안 됨
        assertTrue(legal(g, 4, 0).isEmpty())
        assertTrue(legal(g, 3, 0).isEmpty())
        assertTrue(legal(g, 5, 0).isEmpty())
        assertSame(Phase.OVER, g.phase)
        assertEquals(EndReason.CHECKMATE, g.endReason)
        assertEquals(C, g.winner)
    }

    @Test
    fun `장군이지만 외통 아님 - 피하거나 막을 수 있음`() {
        // 피하기
        val g = empty(choGeneral = 3 to 9, hanGeneral = 4 to 1)
        g.put(0, 5, C, Kind.CHARIOT)
        assertTrue(mv(g, 0, 5, 4, 5))
        assertTrue(g.inCheck(H))
        assertFalse(g.isCheckmate(H))
        assertSame(Phase.PLAY, g.phase)
        val esc = legal(g, 4, 1)
        assertTrue((3 to 1) in esc); assertFalse("같은 줄은 안 됨", (4 to 2) in esc); assertFalse((4 to 0) in esc)
        assertTrue(mv(g, 4, 1, 3, 1))
        assertFalse(g.inCheck(H))
        // 막기: 사가 궁성 대각선으로 가운데를 막는 것만 가능
        val b = empty(choGeneral = 3 to 9, hanGeneral = 4 to 0)
        b.put(3, 0, H, Kind.GUARD)
        b.put(5, 0, H, Kind.GUARD)
        b.put(8, 5, C, Kind.CHARIOT)
        assertTrue(mv(b, 8, 5, 4, 5))
        assertTrue(b.inCheck(H))
        assertFalse(b.isCheckmate(H))
        assertTrue(legal(b, 4, 0).isEmpty())
        assertEquals(setOf(4 to 1), legal(b, 3, 0))
        assertEquals(setOf(4 to 1), legal(b, 5, 0))
        assertTrue(mv(b, 5, 0, 4, 1))
        assertFalse(b.inCheck(H))
        assertSame(Phase.PLAY, b.phase)
    }

    @Test
    fun `둘 수 있는 수가 없으면 장군이 아니어도 패배`() {
        val g = empty(choGeneral = 5 to 9, hanGeneral = 3 to 0)
        g.put(4, 5, C, Kind.CHARIOT)
        g.put(8, 5, C, Kind.CHARIOT)
        assertTrue(mv(g, 8, 5, 8, 1))
        assertFalse(g.inCheck(H))
        assertFalse(g.hasLegalMove(H))
        assertSame(Phase.OVER, g.phase)
        assertEquals(EndReason.NO_MOVES, g.endReason)
        assertEquals(C, g.winner)
    }

    // ── 무르기 ──

    @Test
    fun `무르기 - 잡은 기물까지 되돌리고 한 번만`() {
        val g = empty()
        g.put(0, 5, C, Kind.CHARIOT)
        g.put(0, 2, H, Kind.CHARIOT)
        g.put(8, 2, H, Kind.SOLDIER)
        assertFalse(g.canUndo(C))
        assertTrue(mv(g, 0, 5, 0, 2))
        assertEquals(1, g.capturedCount[C])
        assertEquals(Piece.code(H, Kind.CHARIOT), g.captured[C][0])
        assertTrue(g.canUndo(C))
        assertFalse("한은 방금 안 뒀음", g.canUndo(H))
        assertFalse(g.undo(H))
        assertTrue(g.undo(C))
        assertEquals(Piece.code(C, Kind.CHARIOT), g.at(0, 5))
        assertEquals(Piece.code(H, Kind.CHARIOT), g.at(0, 2))
        assertEquals(0, g.capturedCount[C])
        assertEquals(C, g.turn)
        assertEquals(0, g.moveCount)
        assertEquals(0, g.undosLeft[C])
        assertEquals(1, g.undosLeft[H])
        // 두 번째는 안 됨
        assertTrue(mv(g, 0, 5, 0, 3))
        assertFalse(g.canUndo(C))
        // 상대가 두면 못 무름
        assertTrue(mv(g, 8, 2, 8, 3))
        assertTrue(g.canUndo(H))
        assertTrue(mv(g, 0, 3, 0, 4))
        assertFalse(g.canUndo(H))
    }

    @Test
    fun `무르기 끔이면 못 무름`() {
        val g = JanggiGame(undoEnabled = false)
        assertTrue(mv(g, 0, 6, 0, 5))
        assertFalse(g.canUndo(C))
        assertFalse(g.undo(C))
        assertEquals(0, g.undosLeft[C])
    }

    // ── 빅장 · 수 제한 · 기권 ──

    @Test
    fun `빅장 옵션 - 풀지 않으면 무승부, 풀면 계속`() {
        fun setup(): JanggiGame {
            val g = empty(choGeneral = 4 to 8, hanGeneral = 4 to 1, bikjang = true)
            g.put(4, 5, C, Kind.CHARIOT)
            g.put(8, 3, H, Kind.SOLDIER)
            g.put(0, 9, C, Kind.SOLDIER)
            return g
        }
        val keep = setup()
        assertTrue(mv(keep, 4, 5, 0, 5))
        assertTrue("빅장 불림", keep.bikjangCalled)
        assertSame(Phase.PLAY, keep.phase)
        assertTrue(mv(keep, 8, 3, 8, 4))
        assertSame(Phase.OVER, keep.phase)
        assertTrue(keep.isDraw)
        assertEquals(EndReason.BIKJANG, keep.endReason)
        assertArrayEquals(intArrayOf(1, 1), keep.ranks(0))

        val br = setup()
        assertTrue(mv(br, 4, 5, 0, 5))
        assertTrue(mv(br, 4, 1, 3, 1))
        assertFalse(br.bikjangCalled)
        assertSame(Phase.PLAY, br.phase)
        // 무르기로 되돌리면 빅장 상태도 되돌아감
        assertTrue(br.undo(H))
        assertTrue(br.bikjangCalled)
    }

    @Test
    fun `수 제한 - 점수 판정, 같은 기물이면 덤으로 한 승리`() {
        val g = JanggiGame(moveLimit = 2)
        assertEquals(720, g.score(C))
        assertEquals(735, g.score(H))
        assertTrue(mv(g, 0, 6, 0, 5))
        assertSame(Phase.PLAY, g.phase)
        assertTrue(mv(g, 0, 3, 1, 3))
        assertSame(Phase.OVER, g.phase)
        assertEquals(EndReason.MOVE_LIMIT, g.endReason)
        assertEquals(H, g.winner)
        // 초가 차를 하나 더 가지고 있으면 초 승리
        val lim = JanggiGame(moveLimit = 1)
        lim.clearBoard()
        lim.put(3, 9, C, Kind.GENERAL); lim.put(5, 0, H, Kind.GENERAL)
        lim.put(0, 5, C, Kind.CHARIOT); lim.put(8, 2, H, Kind.SOLDIER)
        lim.reset(C)
        assertTrue(mv(lim, 0, 5, 0, 4))
        assertEquals(C, lim.winner)
        assertEquals(130, lim.score(C)); assertEquals(35, lim.score(H))
    }

    @Test
    fun `기권과 순위`() {
        val g = JanggiGame()
        assertArrayEquals(intArrayOf(1, 1), g.ranks(0)) // 진행 중엔 의미 없음 (무승부와 같음)
        assertTrue(g.resign(C))
        assertEquals(H, g.winner)
        assertEquals(EndReason.RESIGN, g.endReason)
        assertArrayEquals("초 = 1번 사람", intArrayOf(1, 2), g.ranks(1))
        assertArrayEquals(intArrayOf(2, 1), g.ranks(0))
        assertFalse(g.resign(H))
    }

    @Test
    fun `시작판 첫수 - 졸 앞옆, 마 멱, 포는 다리 필요`() {
        val g = JanggiGame(choForm = Formation.MSMS)
        assertEquals(setOf(0 to 5, 1 to 6), legal(g, 0, 6))
        // 초 (1,9) 마: 위 (1,8) 비었음 → (0,7) (2,7); 옆 (2,9) 상이 막음
        assertEquals(setOf(0 to 7, 2 to 7), legal(g, 1, 9))
        // 포 (1,7): 위로 (1,6) 비고 (1,3) 다리 없음... 오른쪽 (2,7)~(6,7) 비고 (7,7) 포 → 못 넘음
        assertTrue(legal(g, 1, 7).isEmpty())
        // 궁: 궁성 안 빈 칸
        assertEquals(setOf(4 to 7, 3 to 7, 5 to 7, 3 to 8, 5 to 8, 4 to 9), legal(g, 4, 8))
        var total = 0
        for (i in g.board.indices) if (g.board[i] != Piece.EMPTY && Piece.side(g.board[i]) == C) total += g.legalTargets(i, buf)
        assertTrue(total > 20)
    }
}
