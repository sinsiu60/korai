package com.siwoo.party.games.yut

import com.siwoo.party.games.yut.YutResult.BACKDO
import com.siwoo.party.games.yut.YutResult.DO
import com.siwoo.party.games.yut.YutResult.GAE
import com.siwoo.party.games.yut.YutResult.GEOL
import com.siwoo.party.games.yut.YutResult.MO
import com.siwoo.party.games.yut.YutResult.YUT
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class YutGameTest {

    // ── 도우미 ──

    /** 결과 r 이 나오는 윷가락 (빽도 표시 가락 = 0번) */
    private fun sticks(r: YutResult): BooleanArray = when (r) {
        BACKDO -> booleanArrayOf(true, false, false, false)
        DO -> booleanArrayOf(false, true, false, false)
        GAE -> booleanArrayOf(false, true, true, false)
        GEOL -> booleanArrayOf(false, true, true, true)
        YUT -> booleanArrayOf(true, true, true, true)
        MO -> booleanArrayOf(false, false, false, false)
    }

    private fun game(players: Int = 2, pieces: Int = 4, backdoEnter: Boolean = false, nak: Boolean = false) =
        YutGame(players, YutRules(pieces, nak, backdoEnter), Random(1))

    /** p 차례, 대기열 [r] 로 말 k 를 움직인다 */
    private fun moveOne(g: YutGame, p: Int, k: Int, r: YutResult): MoveOutcome {
        g.setupTurn(p, r)
        return g.move(0, k)!!
    }

    /** 말 k 를 결과 r 로 움직인 뒤 도착 칸 (-1 = 골인) */
    private fun dest(g: YutGame, k: Int, r: YutResult): Int {
        g.setupTurn(0, r)
        return g.plan(0, k)!!.dest
    }

    // ── 판·지름길 ──

    @Test
    fun `판은 29칸이고 분기 노드는 모·뒷모·방`() {
        assertEquals(29, YutBoard.NODES)
        val branches = (0 until YutBoard.NODES).filter { YutBoard.isBranch(it) }
        assertEquals(listOf(5, 10, 22), branches)
        assertEquals(20, YutBoard.shortcut(5))
        assertEquals(25, YutBoard.shortcut(10))
        assertEquals(27, YutBoard.shortcut(22))
        // 모든 칸이 어떤 길에든 들어 있다
        val seen = BooleanArray(29)
        for (r in YutBoard.ROUTES) for (n in r) seen[n] = true
        assertTrue(seen.all { it })
        // 방을 지나갈 때는 들어온 대각선을 그대로 직진
        assertEquals(23, YutBoard.next(22, 21))
        assertEquals(27, YutBoard.next(22, 26))
    }

    @Test
    fun `모든 길의 칸 순서와 길이`() {
        val outer = IntArray(21) { if (it == 20) 0 else it }
        assertArrayEquals(outer, YutBoard.ROUTES[YutBoard.ROUTE_OUTER])
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5, 20, 21, 22, 23, 24, 15, 16, 17, 18, 19, 0), YutBoard.ROUTES[YutBoard.ROUTE_MO])
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5, 20, 21, 22, 27, 28, 0), YutBoard.ROUTES[YutBoard.ROUTE_MO_BANG])
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 25, 26, 22, 27, 28, 0), YutBoard.ROUTES[YutBoard.ROUTE_DWIMO])
        assertEquals("바깥 한 바퀴", 20, YutBoard.goalIndex(YutBoard.ROUTE_OUTER))
        assertEquals("모 → 방 → 찌모", 16, YutBoard.goalIndex(YutBoard.ROUTE_MO))
        assertEquals("모 → 방 → 참먹이", 11, YutBoard.goalIndex(YutBoard.ROUTE_MO_BANG))
        assertEquals("뒷모 → 방 → 참먹이", 16, YutBoard.goalIndex(YutBoard.ROUTE_DWIMO))
        // 지름길 길은 바깥 길과 분기 노드까지 같다 (분기 직전까지 공통)
        for (i in 0..5) assertEquals(i, YutBoard.ROUTES[YutBoard.ROUTE_MO][i])
        for (i in 0..10) assertEquals(i, YutBoard.ROUTES[YutBoard.ROUTE_DWIMO][i])
    }

    @Test
    fun `도만 나와 한 칸씩 가면 각 지름길 걸음 수대로 골인`() {
        // 모에 멈춘 뒤 방에도 멈추면: 모(5) → 20 → 21 → 방 → 27 → 28 → 골인 = 5 + 6 걸음 = 11
        val g = game()
        g.setupPiece(0, 0, -1)
        var steps = 0
        val path = ArrayList<Int>()
        while (g.state(0, 0) != PieceState.DONE) {
            val m = moveOne(g, 0, 0, if (steps == 0) MO else DO)
            steps += m.plan.result.steps
            path.add(if (m.goal) -1 else m.plan.dest)
        }
        assertEquals(listOf(5, 20, 21, 22, 27, 28, -1), path)
        assertEquals(11, steps)

        // 바깥 길을 도로만: 20걸음 (모·뒷모에 멈추지 않게 처음은 걸, 4에서 개 → 6)
        val g2 = game()
        g2.setupPiece(0, 0, 4)
        assertEquals(6, dest(g2, 0, GAE))
        g2.setupPiece(0, 0, 9)
        assertEquals(11, dest(g2, 0, GAE))
        g2.setupPiece(0, 0, 14)
        assertEquals(16, dest(g2, 0, GAE))
        g2.setupPiece(0, 0, 19)
        assertEquals(-1, dest(g2, 0, DO))

        // 모 지름길에서 방을 지나가면 찌모 쪽으로: 5 → (개) 21 → (개) 22 지나 23 → 24 → 15 → … → 골인
        val g3 = game()
        g3.setupPiece(0, 0, 5)
        assertEquals(21, moveOne(g3, 0, 0, GAE).plan.dest)
        val m = moveOne(g3, 0, 0, GAE)
        assertArrayEquals(intArrayOf(22, 23), m.plan.path)
        assertEquals(15, moveOne(g3, 0, 0, GAE).plan.dest)
        assertEquals("찌모는 분기 노드가 아님 → 아래 변", 19, moveOne(g3, 0, 0, YUT).plan.dest)
        assertTrue(moveOne(g3, 0, 0, DO).goal)

        // 뒷모 지름길: 10 → 25 → 26 → 22(지나감) → 27 → 28 → 골인
        val g4 = game()
        g4.setupPiece(0, 0, 10)
        val m4 = moveOne(g4, 0, 0, GEOL)
        assertArrayEquals(intArrayOf(25, 26, 22), m4.plan.path)
        val m5 = moveOne(g4, 0, 0, GEOL)
        assertArrayEquals("방에 멈췄든 아니든 참먹이 쪽", intArrayOf(27, 28, 0), m5.plan.path)
        assertTrue(m5.goal)
    }

    @Test
    fun `정확히 멈춘 경우만 지름길 진입 - 지나가면 바깥 길 그대로`() {
        val g = game()
        // 3에서 걸: 4, 모(지나감), 6
        g.setupPiece(0, 0, 3)
        g.setupTurn(0, GEOL)
        assertArrayEquals(intArrayOf(4, 5, 6), g.plan(0, 0)!!.path)
        // 4에서 모: 5 지나 9
        g.setupPiece(0, 0, 4)
        assertEquals(9, dest(g, 0, MO))
        // 8에서 걸: 뒷모 지나 11
        g.setupPiece(0, 0, 8)
        assertEquals(11, dest(g, 0, GEOL))
        // 모에 멈춘 말은 지름길 (도 → 20)
        g.setupPiece(0, 0, 5)
        assertEquals(20, dest(g, 0, DO))
        // 뒷모에 멈춘 말은 지름길 (도 → 25)
        g.setupPiece(0, 0, 10)
        assertEquals(25, dest(g, 0, DO))
        // 모 대각선에서 방을 지나가면 직진 (20 에서 걸 → 21, 22, 23)
        g.setupPiece(0, 0, 20)
        g.setupTurn(0, GEOL)
        assertArrayEquals(intArrayOf(21, 22, 23), g.plan(0, 0)!!.path)
        // 방에 멈춘 말 (모 쪽에서 옴)은 참먹이 쪽 (도 → 27)
        g.setupPiece(0, 0, 21)
        moveOne(g, 0, 0, DO)
        assertEquals(22, g.node(0, 0))
        assertEquals(27, dest(g, 0, DO))
        // 찌모는 분기 노드가 아니다
        g.setupPiece(0, 0, 14)
        moveOne(g, 0, 0, DO)
        assertEquals(16, dest(g, 0, DO))
        // 모로 판에 바로 올라가도 모에 멈춘 것 → 다음은 지름길
        g.setupPiece(0, 1, -1)
        moveOne(g, 0, 1, MO)
        assertEquals(5, g.node(0, 1))
        assertEquals(20, dest(g, 1, DO))
    }

    // ── 업기·잡기 ──

    @Test
    fun `업기 - 내 말 위에 도착하면 묶음으로 같이 이동`() {
        val g = game()
        g.setupPiece(0, 0, 3)
        g.setupPiece(0, 1, 1)
        val m = moveOne(g, 0, 1, GAE)
        assertTrue(m.joined)
        assertEquals(2, m.stackCount)
        assertEquals(2, g.stackSize(0, 0))
        assertTrue(g.isStackLead(0, 0))
        assertFalse(g.isStackLead(0, 1))
        // 다음 이동: 어느 말을 골라도 둘이 같이
        val m2 = moveOne(g, 0, 1, GEOL)
        assertArrayEquals(intArrayOf(0, 1), m2.plan.pieces)
        assertEquals(6, g.node(0, 0))
        assertEquals(6, g.node(0, 1))
        // 대기 말을 새로 올려 업기 (3개 묶음)
        g.setupPiece(0, 2, -1)
        g.setupPiece(0, 0, 2)
        g.setupPiece(0, 1, 2)
        val m3 = moveOne(g, 0, 2, GAE)
        assertTrue(m3.joined)
        assertEquals(3, m3.stackCount)
        // 묶음이 함께 골인
        g.setupPiece(0, 3, -2)
        g.setupPiece(0, 0, 19); g.setupPiece(0, 1, 19); g.setupPiece(0, 2, 19)
        val m4 = moveOne(g, 0, 0, GEOL)
        assertTrue(m4.goal)
        assertEquals(4, g.goalCount(0))
    }

    @Test
    fun `잡기 - 상대 묶음 전체 대기로 + 이동 뒤 한 번 더 던지기`() {
        val g = game()
        g.setupPiece(1, 0, 4)
        g.setupPiece(1, 1, 4)
        g.setupPiece(0, 0, 2)
        g.setupTurn(0, GAE, DO)
        val m = g.move(0, 0)!!
        assertEquals(1, m.capturedPlayer)
        assertArrayEquals(intArrayOf(0, 1), m.captured)
        assertTrue(m.extraThrow)
        assertEquals(PieceState.WAITING, g.state(1, 0))
        assertEquals(PieceState.WAITING, g.state(1, 1))
        assertEquals(2 + 2, g.waitingCount(1))
        assertEquals(4, g.node(0, 0))
        // 남은 도는 대기열에 그대로, 먼저 한 번 더 던진다
        assertFalse(m.turnEnded)
        assertEquals(0, g.current)
        assertEquals(Phase.THROW, g.phase)
        assertEquals(listOf(DO), g.queue)
        val t = g.throwWith(sticks(GEOL))
        assertFalse(t.turnEnded)
        assertEquals(listOf(DO, GEOL), g.queue)
        assertEquals(Phase.MOVE, g.phase)
        // 대기열을 다 쓰면 차례 끝
        g.move(0, 0)
        val last = g.move(0, 0)!!
        assertTrue(last.turnEnded)
        assertEquals(1, last.next)
        assertEquals(Phase.THROW, g.phase)

        // 잡은 뒤 대기열이 비어도 한 번 더 던짐 (차례 유지)
        val g2 = game()
        g2.setupPiece(1, 0, 3)
        g2.setupPiece(0, 0, 1)
        val m2 = moveOne(g2, 0, 0, GAE)
        assertTrue(m2.extraThrow)
        assertFalse(m2.turnEnded)
        assertEquals(Phase.THROW, g2.phase)
        assertEquals(1, g2.throwsLeft)
        // 빽도로도 잡는다
        val g3 = game()
        g3.setupPiece(1, 0, 6)
        g3.setupPiece(0, 0, 7)
        val m3 = moveOne(g3, 0, 0, BACKDO)
        assertEquals(1, m3.capturedPlayer)
        assertTrue(m3.extraThrow)
    }

    // ── 빽도 ──

    @Test
    fun `빽도 - 지나온 길로 한 칸 뒤`() {
        val g = game()
        g.setupPiece(0, 0, 7)
        assertEquals(6, dest(g, 0, BACKDO))
        g.setupPiece(0, 0, 20)
        assertEquals("모 대각선 첫 칸 → 모", 5, dest(g, 0, BACKDO))
        g.setupPiece(0, 0, 25)
        assertEquals(10, dest(g, 0, BACKDO))
        // 방: 들어온 대각선으로
        g.setupPiece(0, 0, 22, YutBoard.ROUTE_DWIMO)
        assertEquals(26, dest(g, 0, BACKDO))
        g.setupPiece(0, 0, 22, YutBoard.ROUTE_MO)
        assertEquals(21, dest(g, 0, BACKDO))
        // 찌모: 바깥 길로 왔으면 14, 모 지름길로 왔으면 24
        g.setupPiece(0, 0, 15, YutBoard.ROUTE_OUTER)
        assertEquals(14, dest(g, 0, BACKDO))
        g.setupPiece(0, 0, 15, YutBoard.ROUTE_MO)
        assertEquals(24, dest(g, 0, BACKDO))
        // 27: 뒷모 쪽이든 모 쪽이든 방으로
        g.setupPiece(0, 0, 27, YutBoard.ROUTE_DWIMO)
        assertEquals(22, dest(g, 0, BACKDO))

        // 실제로 지나온 길 기억: 21 → 걸(방 지나 23) → 빽도 22 → 빽도 21 (모 쪽)
        val g2 = game()
        g2.setupPiece(0, 0, 21)
        moveOne(g2, 0, 0, GAE)
        assertEquals(23, g2.node(0, 0))
        moveOne(g2, 0, 0, BACKDO)
        assertEquals(22, g2.node(0, 0))
        moveOne(g2, 0, 0, BACKDO)
        assertEquals(21, g2.node(0, 0))
        // 빽도로 모에 돌아오면 모에 멈춘 것 → 다음 이동은 지름길
        moveOne(g2, 0, 0, BACKDO)
        moveOne(g2, 0, 0, BACKDO)
        assertEquals(5, g2.node(0, 0))
        assertEquals(20, dest(g2, 0, DO))
        // 빽도로 모 앞(4)까지 물러나면 다시 바깥 길: 개 → 모 지나 6
        moveOne(g2, 0, 0, BACKDO)
        assertEquals(4, g2.node(0, 0))
        assertEquals(6, dest(g2, 0, GAE))

        // 업힌 묶음은 함께 뒤로
        val g3 = game()
        g3.setupPiece(0, 0, 9)
        g3.setupPiece(0, 1, 9)
        moveOne(g3, 0, 1, BACKDO)
        assertEquals(8, g3.node(0, 0))
        assertEquals(8, g3.node(0, 1))
    }

    @Test
    fun `도에서 빽도면 참먹이에 선 채로 남고 다음 이동에 골인`() {
        val g = game()
        g.setupPiece(0, 0, 1)
        val m = moveOne(g, 0, 0, BACKDO)
        assertFalse(m.goal)
        assertEquals(0, m.plan.dest)
        assertEquals(PieceState.BOARD, g.state(0, 0))
        assertEquals(0, g.node(0, 0))
        // 어떤 앞 결과든 골인 (지나갈 칸 없음)
        g.setupTurn(0, DO)
        val p = g.plan(0, 0)!!
        assertTrue(p.goal)
        assertEquals(0, p.path.size)
        // 참먹이에서 또 빽도면 날윷(19)으로
        assertEquals(19, dest(g, 0, BACKDO))
        assertTrue(moveOne(g, 0, 0, MO).goal)
    }

    @Test
    fun `빽도 - 판에 말이 없으면 버림`() {
        val g = game()
        val t = g.throwWith(sticks(BACKDO))
        assertEquals(BACKDO, t.result)
        assertEquals(listOf(BACKDO), t.discarded)
        assertTrue(t.turnEnded)
        assertEquals(1, g.current)
        assertTrue(g.queue.isEmpty())
        assertEquals(Phase.THROW, g.phase)
        // 대기 말은 빽도로 못 올림
        g.setupTurn(1, BACKDO, DO)
        assertNull(g.plan(0, 0))
        assertNotNull(g.plan(1, 0))
    }

    @Test
    fun `빽도 대기말 올리기 옵션 - 출발점 바로 뒤에 올리고 도면 골인`() {
        val g = game(backdoEnter = true)
        val t = g.throwWith(sticks(BACKDO))
        assertTrue(t.discarded.isEmpty())
        assertFalse(t.turnEnded)
        assertEquals(Phase.MOVE, g.phase)
        val m = g.move(0, 2)!!
        assertEquals(-1, m.plan.from)
        assertArrayEquals(intArrayOf(19), m.plan.path)
        assertEquals(19, g.node(0, 2))
        assertTrue(m.turnEnded)
        // 다음 차례에 도면 골인
        assertTrue(moveOne(g, 0, 2, DO).goal)
        assertEquals(1, g.goalCount(0))
    }

    // ── 대기열 ──

    @Test
    fun `윷·모가 연속으로 나오면 다 던진 뒤 대기열을 원하는 순서로`() {
        val g = game()
        assertEquals(Phase.THROW, g.phase)
        val t1 = g.throwWith(sticks(YUT))
        assertTrue(t1.again)
        assertEquals(Phase.THROW, g.phase)
        assertEquals(0, g.current)
        val t2 = g.throwWith(sticks(MO))
        assertTrue(t2.again)
        assertEquals(Phase.THROW, g.phase)
        // 던지는 중에는 이동 불가
        assertFalse(g.canMove(0, 0))
        val t3 = g.throwWith(sticks(GEOL))
        assertFalse(t3.again)
        assertEquals(Phase.MOVE, g.phase)
        assertEquals(listOf(YUT, MO, GEOL), g.queue)
        // 걸 먼저, 그다음 모 (같은 말: 3 → 8), 마지막 윷 (8 → 12)
        g.move(2, 0)
        assertEquals(3, g.node(0, 0))
        assertEquals(listOf(YUT, MO), g.queue)
        g.move(1, 0)
        assertEquals(8, g.node(0, 0))
        val last = g.move(0, 0)!!
        assertEquals(12, g.node(0, 0))
        assertTrue(last.turnEnded)
        assertEquals(1, g.current)
        // 낙: 윷 다음 낙이면 던지기를 멈추고 이미 나온 윷만 씀
        val t4 = g.throwWith(sticks(YUT))
        assertTrue(t4.again)
        val t5 = g.throwWith(sticks(DO), nak = true)
        assertNull(t5.result)
        assertTrue(t5.nak)
        assertEquals(Phase.MOVE, g.phase)
        assertEquals(listOf(YUT), g.queue)
        // 첫 던지기가 낙이면 차례 끝
        g.move(0, 0)
        assertEquals(0, g.current)
        val t6 = g.throwWith(sticks(GAE), nak = true)
        assertTrue(t6.turnEnded)
        assertEquals(1, g.current)
    }

    @Test
    fun `쓸 수 없는 결과만 남으면 자동으로 버림`() {
        // 도·빽도: 판에 말이 없어도 도로 올린 뒤 빽도를 쓸 수 있으니 버리지 않음
        val g = game()
        g.setupTurn(0)
        g.throwWith(sticks(YUT))
        g.throwWith(sticks(BACKDO))
        assertEquals(Phase.MOVE, g.phase)
        assertEquals(listOf(YUT, BACKDO), g.queue)
        assertFalse(g.isUsable(BACKDO))
        assertNull("지금은 빽도를 고를 수 없음", g.plan(1, 0))
        g.move(0, 0)
        assertTrue(g.isUsable(BACKDO))
        val m = g.move(0, 0)!!
        assertEquals(3, g.node(0, 0))
        assertTrue(m.discarded.isEmpty())

        // 마지막 판 위 말이 골인하고 빽도만 남으면 버리고 차례 끝
        val g2 = game(pieces = 2)
        g2.setupPiece(0, 0, 19)
        g2.setupTurn(0, DO, BACKDO)
        val m2 = g2.move(0, 0)!!
        assertTrue(m2.goal)
        assertFalse(m2.playerFinished)
        assertEquals(listOf(BACKDO), m2.discarded)
        assertTrue(m2.turnEnded)
        assertEquals(1, g2.current)
        assertTrue(g2.queue.isEmpty())
    }

    // ── 골인 ──

    @Test
    fun `골인 판정 - 참먹이에 도착하거나 지나가면 골인`() {
        val g = game()
        g.setupPiece(0, 0, 19)
        g.setupTurn(0, DO)
        val p = g.plan(0, 0)!!
        assertTrue("참먹이에 정확히 도착해도 골인", p.goal)
        assertArrayEquals(intArrayOf(0), p.path)
        assertEquals(-1, p.dest)
        g.setupPiece(0, 0, 18)
        g.setupTurn(0, MO)
        val p2 = g.plan(0, 0)!!
        assertTrue("지나가면 골인", p2.goal)
        assertArrayEquals("참먹이까지만 그림", intArrayOf(19, 0), p2.path)
        g.setupPiece(0, 0, 17)
        assertEquals(18, dest(g, 0, DO))
        // 방에 멈춘 말: 걸이면 27, 28, 참먹이 → 골인 / 개면 28
        g.setupPiece(0, 0, 22, YutBoard.ROUTE_MO)
        assertEquals(-1, dest(g, 0, GEOL))
        assertEquals(28, dest(g, 0, GAE))
        // 골인한 말은 패널로, 판에서 사라짐
        g.setupPiece(0, 0, 19)
        val m = moveOne(g, 0, 0, YUT)
        assertTrue(m.goal)
        assertEquals(PieceState.DONE, g.state(0, 0))
        assertEquals(-1, g.node(0, 0))
        assertEquals(1, g.goalCount(0))
        assertEquals(0, m.stackCount)
        // 골인한 말은 다시 고를 수 없음
        g.setupTurn(0, DO)
        assertNull(g.plan(0, 0))
    }

    // ── 순위 ──

    @Test
    fun `2인 - 먼저 다 골인한 사람 1위, 남은 사람 자동 꼴찌`() {
        val g = game(players = 2, pieces = 2)
        g.setupPiece(0, 0, -2)
        g.setupPiece(0, 1, 18)
        g.setupTurn(0, GAE, YUT)
        val m = g.move(0, 1)!!
        assertTrue(m.playerFinished)
        assertEquals(1, m.rank)
        assertTrue(m.gameOver)
        assertEquals(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(1, 2), g.ranks)
        assertTrue("남은 결과는 버림", g.queue.isEmpty())
    }

    @Test
    fun `3인 - 1위 확정 후 계속하면 끝난 사람은 차례에서 빠지고 마지막 1명은 꼴찌`() {
        val g = game(players = 3, pieces = 2)
        g.setupPiece(1, 0, -2)
        g.setupPiece(1, 1, 19)
        val m = moveOne(g, 1, 1, DO)
        assertTrue(m.playerFinished)
        assertEquals(1, m.rank)
        assertFalse(m.gameOver)
        assertEquals(3, g.activeCount + 1)
        assertEquals(2, g.current)
        // 2 → 0 → (1 건너뜀) → 2
        g.throwWith(sticks(GAE), nak = true)
        assertEquals(0, g.current)
        g.throwWith(sticks(GAE), nak = true)
        assertEquals(2, g.current)
        // 2가 다 골인 → 2위, 0은 3위
        g.setupPiece(2, 0, -2)
        g.setupPiece(2, 1, 19)
        val m2 = moveOne(g, 2, 1, DO)
        assertEquals(2, m2.rank)
        assertTrue(m2.gameOver)
        assertArrayEquals(intArrayOf(3, 1, 2), g.ranks)
    }

    @Test
    fun `중간 종료 순위 - 골인 수 다음 진행 거리 합, 같으면 공동`() {
        val g = game(players = 4, pieces = 4)
        // 1: 다 골인 → 1위 확정
        for (k in 0..2) g.setupPiece(1, k, -2)
        g.setupPiece(1, 3, 19)
        moveOne(g, 1, 3, DO)
        assertEquals(1, g.finishRank[1])
        // 0: 골인 1, 판 위 7 (진행 7)
        g.setupPiece(0, 0, -2)
        g.setupPiece(0, 1, 7)
        // 2: 골인 1, 판 위 방 (모 쪽에서 멈춤: 남은 3걸음 → 진행 17)
        g.setupPiece(2, 0, -2)
        g.setupPiece(2, 1, 22, YutBoard.ROUTE_MO)
        // 3: 골인 0, 판 위 2 + 3 (진행 5)
        g.setupPiece(3, 0, 2)
        g.setupPiece(3, 1, 3)
        assertEquals(7, g.progressSum(0))
        assertEquals(17, g.progressSum(2))
        assertEquals(5, g.progressSum(3))
        assertArrayEquals(intArrayOf(3, 1, 2, 4), g.stopHere())
        assertEquals(Phase.OVER, g.phase)
        assertTrue(g.stoppedEarly)

        // 동점: 골인 수·진행 거리 같으면 공동 순위
        val h = game(players = 3, pieces = 2)
        h.setupPiece(0, 0, -2)
        h.setupPiece(0, 1, 19)
        moveOne(h, 0, 1, DO)
        h.setupPiece(1, 0, 4)
        h.setupPiece(2, 0, 4, YutBoard.ROUTE_OUTER)
        assertArrayEquals(intArrayOf(1, 2, 2), h.stopHere())
        // 진행 거리: 지름길 반영 (모에 선 말은 남은 걸음 11 → 진행 9 / 바깥 6 은 진행 6)
        val k = game()
        k.setupPiece(0, 0, 5)
        k.setupPiece(1, 0, 6)
        assertEquals(9, k.progress(0, 0))
        assertEquals(6, k.progress(1, 0))
        k.setupPiece(0, 0, 0)
        assertEquals("참먹이에 선 말도 1걸음 남음", 19, k.progress(0, 0))
    }

    // ── 던지기 확률 ──

    @Test
    fun `윷가락 결과 - 배 개수와 빽도 표시 가락`() {
        // 16가지 경우를 모두 확인
        for (mask in 0 until 16) {
            val f = BooleanArray(4) { (mask shr it) and 1 == 1 }
            val n = f.count { it }
            val r = YutThrow.result(f)
            val expect = when (n) {
                0 -> MO
                1 -> if (f[GameConfig.BACKDO_STICK]) BACKDO else DO
                2 -> GAE
                3 -> GEOL
                else -> YUT
            }
            assertEquals(expect, r)
            if (r == BACKDO) assertArrayEquals("빽도는 표시 가락 하나만 배일 때뿐", booleanArrayOf(true, false, false, false), f)
        }
    }

    @Test
    fun `던지기 확률 - 배 확률 0점5`() {
        val p = GameConfig.YUT_FLAT_RATE
        assertEquals(0.5, p, 0.0)
        assertEquals(1.0 / 16, YutThrow.probability(MO), 1e-12)
        assertEquals(1.0 / 16, YutThrow.probability(BACKDO), 1e-12)
        assertEquals(3.0 / 16, YutThrow.probability(DO), 1e-12)
        assertEquals(6.0 / 16, YutThrow.probability(GAE), 1e-12)
        assertEquals(4.0 / 16, YutThrow.probability(GEOL), 1e-12)
        assertEquals(1.0 / 16, YutThrow.probability(YUT), 1e-12)
        assertEquals(1.0, YutResult.values().sumOf { YutThrow.probability(it) }, 1e-12)
        // 다른 배 확률에서도 합은 1
        assertEquals(1.0, YutResult.values().sumOf { YutThrow.probability(it, 0.6) }, 1e-12)
        // 실제로 많이 던지면 이론 확률에 가깝다
        val rnd = Random(42)
        val n = 200_000
        val cnt = IntArray(YutResult.values().size)
        var flats = 0
        repeat(n) {
            val s = YutThrow.roll(rnd)
            flats += s.count { it }
            cnt[YutThrow.result(s).ordinal]++
        }
        assertEquals(0.5, flats / (n * 4.0), 0.005)
        for (r in YutResult.values()) assertEquals(r.label, YutThrow.probability(r), cnt[r.ordinal] / n.toDouble(), 0.005)
    }

    @Test
    fun `낙 확률 - 켜면 약 5퍼센트, 끄면 없음`() {
        assertEquals(0.05, GameConfig.YUT_NAK_RATE, 0.0)
        val rnd = Random(7)
        val n = 100_000
        var naks = 0
        repeat(n) { if (YutThrow.nak(rnd)) naks++ }
        assertEquals(0.05, naks / n.toDouble(), 0.004)

        // 게임: 낙 켬 → 던지기 일부가 무효 / 끔 → 무효 없음
        fun countNaks(nak: Boolean): Int {
            val g = YutGame(2, YutRules(4, nak, false), Random(3))
            var c = 0
            repeat(4000) {
                val t = g.throwYut()
                if (t.nak) c++
                g.setupTurn(0)
            }
            return c
        }
        val on = countNaks(true)
        assertTrue("낙 켬: $on", abs(on / 4000.0 - 0.05) < 0.015)
        assertEquals(0, countNaks(false))
    }

    @Test
    fun `난수 게임 여러 판이 끝까지 진행되고 순위가 맞다`() {
        for (seed in 1..30) {
            val players = 2 + seed % 3
            val g = YutGame(players, YutRules(2 + seed % 3, seed % 2 == 0, seed % 5 == 0), Random(seed))
            val rnd = Random(seed * 31)
            var guard = 0
            while (g.phase != Phase.OVER) {
                check(guard++ < 20000) { "끝나지 않음 $seed" }
                if (g.phase == Phase.THROW) { g.throwYut(); continue }
                // 쓸 수 있는 아무 결과·말
                val qi = g.queue.indices.filter { g.isUsable(g.queue[it]) }.random(rnd)
                val k = (0 until g.pieces).filter { g.plan(qi, it) != null }.random(rnd)
                g.move(qi, k)
                // 판 위 같은 칸에 두 사람 말이 함께 있으면 안 됨
                for (node in 0 until YutBoard.NODES) {
                    val owners = (0 until players).count { g.piecesAt(it, node).isNotEmpty() }
                    assertTrue(owners <= 1)
                }
            }
            val r = g.ranks!!
            assertEquals((1..players).toList(), r.sorted())
            assertEquals(players, r[(0 until players).first { g.goalCount(it) < g.pieces }])
        }
    }
}
