package com.siwoo.party.games.stack

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StackGameTest {

    private val tol = GameConfig.STACK_PERFECT_TOL

    /** 게임 시계에서 블록 왼쪽 끝이 target 이 되는 시각 (16ms 단위로 찾기, 오차 = 한 프레임 이동 거리) */
    private fun timeFor(g: StackGame, start: Long, accept: (Float) -> Boolean): Long {
        var t = start
        while (t < start + 60_000) {
            if (accept(g.blockX(t))) return t
            t += 1
        }
        error("못 찾음")
    }

    @Test
    fun `왼쪽으로 튀어나오면 왼쪽이 잘린다`() {
        val c = StackGame.cut(40f, 30f, 32f, 30f, tol)
        assertSame(DropKind.CUT, c.kind)
        assertEquals(40f, c.placedX, 1e-4f)
        assertEquals(22f, c.placedW, 1e-4f)
        assertEquals(32f, c.cutX, 1e-4f)
        assertEquals(8f, c.cutW, 1e-4f)
    }

    @Test
    fun `오른쪽으로 튀어나오면 오른쪽이 잘린다`() {
        val c = StackGame.cut(40f, 30f, 51f, 30f, tol)
        assertSame(DropKind.CUT, c.kind)
        assertEquals(51f, c.placedX, 1e-4f)
        assertEquals(19f, c.placedW, 1e-4f)
        assertEquals(70f, c.cutX, 1e-4f)
        assertEquals(11f, c.cutW, 1e-4f)
        // 남은 폭 + 잘린 폭 = 원래 폭
        assertEquals(30f, c.placedW + c.cutW, 1e-4f)
    }

    @Test
    fun `퍼펙트는 허용 오차 안이면 폭 유지 + 위치 맞춤`() {
        for (d in floatArrayOf(-tol, -tol / 2f, 0f, tol / 3f, tol)) {
            val c = StackGame.cut(40f, 30f, 40f + d, 30f, tol)
            assertSame("차이 $d", DropKind.PERFECT, c.kind)
            assertEquals(40f, c.placedX, 0f)
            assertEquals(30f, c.placedW, 0f)
            assertEquals(0f, c.cutW, 0f)
        }
        val off = StackGame.cut(40f, 30f, 40f + tol + 0.05f, 30f, tol)
        assertSame("오차 밖이면 잘림", DropKind.CUT, off.kind)
        assertTrue(off.placedW < 30f)
    }

    @Test
    fun `완전히 빗나가면 MISS`() {
        assertSame(DropKind.MISS, StackGame.cut(40f, 30f, 70f, 30f, tol).kind) // 딱 붙음 = 겹침 0
        assertSame(DropKind.MISS, StackGame.cut(40f, 30f, 75f, 30f, tol).kind)
        assertSame(DropKind.MISS, StackGame.cut(40f, 30f, 5f, 30f, tol).kind)
        assertSame(DropKind.CUT, StackGame.cut(40f, 30f, 69f, 30f, tol).kind)
    }

    @Test
    fun `층이 오를수록 빨라지고 최대에서 멈춘다`() {
        assertEquals(GameConfig.SPEED_BASE, StackGame.speed(1), 0f)
        var prev = StackGame.speed(1)
        for (lv in 2..15) {
            val s = StackGame.speed(lv)
            assertEquals(GameConfig.SPEED_BASE + GameConfig.SPEED_STEP * (lv - 1), s, 1e-4f)
            assertTrue(s > prev)
            prev = s
        }
        assertEquals(GameConfig.SPEED_MAX, StackGame.speed(500), 0f)
    }

    @Test
    fun `왕복 위치는 0 ~ range 를 오간다`() {
        val range = 70f
        val sp = 50f // 칸/초 → 70칸 = 1400ms
        assertEquals(0f, StackGame.position(0, sp, range, true), 1e-3f)
        assertEquals(35f, StackGame.position(700, sp, range, true), 1e-3f)
        assertEquals(70f, StackGame.position(1400, sp, range, true), 1e-3f)
        assertEquals(35f, StackGame.position(2100, sp, range, true), 1e-3f)
        assertEquals(70f, StackGame.position(0, sp, range, false), 1e-3f)
        assertEquals(35f, StackGame.position(700, sp, range, false), 1e-3f)
        for (t in 0L..10_000L step 37) assertTrue(StackGame.position(t, sp, range, true) in 0f..range)
    }

    @Test
    fun `높은 층 블록이 같은 시간에 더 멀리 간다`() {
        val g = StackGame(2, 0)
        g.startMove(0)
        val d1 = abs(g.blockX(300) - g.blockX(0))
        // 9개 퍼펙트로 쌓기
        var t = 1000L
        repeat(9) {
            g.startMove(t)
            val tx = g.top.x
            t = timeFor(g, t) { abs(it - tx) <= tol / 2f }
            assertSame(DropKind.PERFECT, g.drop(t).kind)
        }
        assertEquals(10, g.level)
        g.startMove(t)
        val d10 = abs(g.blockX(t + 300) - g.blockX(t))
        assertTrue("1층 $d1 < 10층 $d10", d10 > d1 * 1.5f)
    }

    @Test
    fun `떨어뜨리면 남은 폭이 다음 블록 폭이 되고 차례가 넘어간다`() {
        val g = StackGame(3, 1)
        g.startMove(0)
        assertEquals(GameConfig.START_W, g.width, 0f)
        val tx = g.top.x
        // 왼쪽에서 출발 → 받침보다 6칸 왼쪽에서 멈춤
        val t = timeFor(g, 0) { it >= tx - 6f }
        val r = g.drop(t)
        assertSame(DropKind.CUT, r.kind)
        assertEquals(1, r.player)
        assertEquals(1, r.level)
        assertEquals(tx, r.placedX, 1e-4f)
        assertEquals(GameConfig.START_W - (tx - r.droppedX), r.placedW, 1e-4f)
        assertEquals(r.placedW, g.width, 0f)
        assertEquals(2, g.tower.size)
        assertEquals(1, g.tower[1].owner)
        assertEquals(2, g.current)
        assertEquals(1, g.height)
        assertSame(Phase.MOVING, g.phase)
    }

    @Test
    fun `퍼펙트면 폭이 그대로이고 위치가 맞춰진다`() {
        val g = StackGame(2, 0)
        g.startMove(0)
        val tx = g.top.x
        val t = timeFor(g, 0) { it > tx && it - tx <= tol }
        val r = g.drop(t)
        assertSame(DropKind.PERFECT, r.kind)
        assertEquals(tx, g.top.x, 0f)
        assertEquals(GameConfig.START_W, g.width, 0f)
        assertEquals(1, g.perfects)
    }

    @Test
    fun `완전히 빗나가면 그 라운드 패배 + 다음 라운드는 진 사람 다음부터`() {
        val g = StackGame(3, 0)
        g.startMove(0)
        // 0번: 0칸(왼쪽 끝)에서 떨어뜨림 → 받침(36칸)과 안 겹침
        val r = g.drop(0)
        assertSame(DropKind.MISS, r.kind)
        assertEquals(0, r.player)
        assertEquals(GameConfig.START_W, r.cutW, 0f)
        assertArrayEquals(intArrayOf(1, 0, 0), g.losses)
        assertSame(Phase.ROUND_OVER, g.phase)
        assertEquals(1, g.tower.size)
        g.nextRound()
        assertEquals(2, g.round)
        assertEquals("진 사람(0) 다음", 1, g.current)
        assertSame(Phase.MOVING, g.phase)
        assertEquals(1, g.tower.size)
        assertEquals(GameConfig.START_W, g.width, 0f)
        // 1번 성공, 2번 빗나감
        g.startMove(10_000)
        val tx = g.top.x
        assertTrue(g.drop(timeFor(g, 10_000) { abs(it - tx) < 10f }).kind != DropKind.MISS)
        g.startMove(20_000)
        // 2층은 오른쪽 끝에서 출발 → 바로 떨어뜨리면 빗나감
        assertSame(DropKind.MISS, g.drop(20_000).kind)
        assertArrayEquals(intArrayOf(1, 0, 1), g.losses)
        g.nextRound()
        assertEquals(3, g.round)
        assertEquals("진 사람(2) 다음 = 0", 0, g.current)
        g.startMove(30_000)
        g.drop(30_000)
        assertSame("마지막 라운드 끝", Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(2, 0, 1), g.losses)
    }

    @Test
    fun `순위는 패배 수 적은 순, 같으면 같은 순위`() {
        val g = StackGame(4, 0)
        // 라운드 1: 2번 패배, 라운드 2: 3번(=2번 다음)... 직접 빗나감 만들기
        g.startMove(0)
        g.drop(0) // 0번 빗나감
        g.nextRound()
        assertEquals(1, g.current)
        g.startMove(0)
        g.drop(0) // 1번 빗나감
        g.nextRound()
        assertEquals(2, g.current)
        g.startMove(0)
        g.drop(0) // 2번 빗나감
        assertSame(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(1, 1, 1, 0), g.losses)
        assertArrayEquals(intArrayOf(2, 2, 2, 1), g.ranks())
    }

    @Test
    fun `같은 사람이 두 번 지면 꼴찌`() {
        val g = StackGame(3, 0)
        g.startMove(0); g.drop(0) // 0 패
        g.nextRound() // 1부터
        g.startMove(0)
        val tx = g.top.x
        g.drop(timeFor(g, 0) { abs(it - tx) < 8f }) // 1 성공
        g.startMove(0); g.drop(0) // 2 빗나감(오른쪽 끝)
        g.nextRound() // 0부터
        g.startMove(0); g.drop(0) // 0 패
        assertArrayEquals(intArrayOf(2, 0, 1), g.losses)
        assertArrayEquals(intArrayOf(3, 1, 2), g.ranks())
    }
}
