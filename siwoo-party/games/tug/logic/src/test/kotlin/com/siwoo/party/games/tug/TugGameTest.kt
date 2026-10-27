package com.siwoo.party.games.tug

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TugGameTest {

    private fun solo(): TugGame = TugGame(Teams.assign(2)).also { it.start(0) }

    /** 4인: 0·3 왼쪽, 1·2 오른쪽 */
    private fun team(): TugGame = TugGame(Teams(listOf(Side.LEFT, Side.RIGHT, Side.RIGHT, Side.LEFT))).also { it.start(0) }

    /** player 가 start 부터 everyMs 간격으로 n 번 탭 */
    private fun tapMany(g: TugGame, player: Int, n: Int, start: Long, everyMs: Long): Int {
        var counted = 0
        for (k in 0 until n) {
            val r = g.tap(player, start + k * everyMs)
            if (r == Tap.COUNTED || r == Tap.WON) counted++
        }
        return counted
    }

    // ── 초당 인정 상한 ──

    @Test fun `1초에 15회까지만 인정, 16번째부터 무시`() {
        val g = solo()
        // 1초 안에 30번 (20ms 간격 → 0..580ms)
        assertEquals(GameConfig.TUG_MAX_TAPS_PER_SEC, tapMany(g, 0, 30, 0, 20))
        assertEquals(15, g.taps[0])
        assertSame("1초가 지나기 전엔 계속 거절", Tap.LIMITED, g.tap(0, 999))
        // 첫 탭(0ms)에서 1초가 지나면 한 칸이 빈다
        assertSame(Tap.COUNTED, g.tap(0, 1000))
        assertSame("다음 칸(20ms 탭)은 1020ms 에 빈다", Tap.LIMITED, g.tap(0, 1010))
        assertSame(Tap.COUNTED, g.tap(0, 1020))
    }

    @Test fun `상한은 슬라이딩 창 - 어느 1초 구간도 15회 이하`() {
        val limiter = TapLimiter(1)
        val accepted = ArrayList<Long>()
        // 들쭉날쭉하게 빠른 연타 (평균 초당 25회)
        var t = 0L
        val r = Random(7)
        repeat(500) {
            t += 10 + r.nextInt(60)
            if (limiter.accept(0, t)) accepted.add(t)
        }
        for (i in accepted.indices) {
            val inWindow = accepted.count { it >= accepted[i] && it < accepted[i] + 1000 }
            assertTrue("구간 ${accepted[i]} 에 $inWindow 회", inWindow <= GameConfig.TUG_MAX_TAPS_PER_SEC)
        }
        assertTrue("상한까지는 거의 다 인정", accepted.size > 15 * (t / 1000) * 0.9)
    }

    @Test fun `상한은 사람마다 따로`() {
        val g = solo()
        assertEquals(15, tapMany(g, 0, 20, 0, 10))
        assertEquals("상대는 영향 없음", 15, tapMany(g, 1, 20, 0, 10))
        assertEquals(0, g.pull)
    }

    @Test fun `초당 15회 이하 속도면 전부 인정`() {
        val g = solo()
        // 70ms 간격 = 초당 약 14회, 약 2.4초 (승리선 40 전까지)
        assertEquals(35, tapMany(g, 1, 35, 0, 70))
        // 리미터만 따로: 70ms 간격 10초 동안 전부 인정
        val limiter = TapLimiter(1)
        for (k in 0 until 143) assertTrue(limiter.accept(0, k * 70L))
    }

    // ── 깃발 위치 ──

    @Test fun `깃발 위치는 탭 차이에 비례`() {
        val g = solo()
        assertEquals(0f, g.flagTarget, 0f)
        tapMany(g, 1, 10, 0, 100)
        assertEquals(10f / GameConfig.TUG_WIN_DIFF_SOLO, g.flagTarget, 1e-6f)
        assertSame(Side.RIGHT, g.leading)
        tapMany(g, 0, 14, 0, 100)
        assertEquals(-4f / GameConfig.TUG_WIN_DIFF_SOLO, g.flagTarget, 1e-6f)
        assertSame(Side.LEFT, g.leading)
    }

    @Test fun `4인은 팀원 합계로 당긴다`() {
        val g = team()
        tapMany(g, 0, 10, 0, 100) // 왼쪽
        tapMany(g, 3, 5, 0, 100)  // 왼쪽
        tapMany(g, 2, 9, 0, 100)  // 오른쪽
        assertEquals(15, g.teamTaps(Side.LEFT))
        assertEquals(9, g.teamTaps(Side.RIGHT))
        assertEquals(-6, g.pull)
        assertEquals(-6f / GameConfig.TUG_WIN_DIFF_TEAM, g.flagTarget, 1e-6f)
    }

    // ── 승리선 도달 ──

    @Test fun `승리선에 닿으면 바로 승리, 그 뒤 탭은 무시`() {
        val g = solo()
        val n = GameConfig.TUG_WIN_DIFF_SOLO
        // 100ms 간격 (초당 10회) → n 번째 탭에서 승리
        for (k in 0 until n - 1) assertSame(Tap.COUNTED, g.tap(0, k * 100L))
        assertEquals(Phase.PLAYING, g.phase)
        assertSame(Tap.WON, g.tap(0, (n - 1) * 100L))
        assertEquals(Phase.DONE, g.phase)
        assertSame(Side.LEFT, g.winner)
        assertEquals(-1f, g.flagTarget, 0f)
        assertSame(Tap.IGNORED, g.tap(1, n * 100L))
        assertSame("끝난 뒤 시간 종료는 영향 없음", Phase.DONE, g.timeUp(GameConfig.TUG_TIME_MS))
        assertSame(Side.LEFT, g.winner)
    }

    @Test fun `4인 승리선은 팀 합계 차이 60`() {
        val g = team()
        var t = 0L
        var last = Tap.IGNORED
        // 오른쪽 둘이 번갈아 (각자 초당 10회)
        while (g.phase == Phase.PLAYING) {
            last = g.tap(1, t)
            if (g.phase == Phase.PLAYING) last = g.tap(2, t + 50)
            t += 100
        }
        assertSame(Tap.WON, last)
        assertEquals(GameConfig.TUG_WIN_DIFF_TEAM, g.pull)
        assertSame(Side.RIGHT, g.winner)
    }

    // ── 시간 종료 판정 ──

    @Test fun `시간 종료 - 깃발이 더 가까운 팀 승리`() {
        val g = solo()
        tapMany(g, 0, 20, 0, 100)
        tapMany(g, 1, 21, 0, 100)
        assertEquals(GameConfig.TUG_TIME_MS - 5000, g.remainingMs(5000))
        assertSame(Phase.DONE, g.timeUp(GameConfig.TUG_TIME_MS))
        assertSame(Side.RIGHT, g.winner)
        assertFalse(g.draw)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test fun `시간이 지난 뒤 판정 전 탭은 세지 않음`() {
        val g = solo()
        g.tap(0, 100)
        assertSame(Tap.IGNORED, g.tap(1, GameConfig.TUG_TIME_MS))
        assertSame(Tap.IGNORED, g.tap(1, GameConfig.TUG_TIME_MS + 1))
        g.timeUp(GameConfig.TUG_TIME_MS + 5)
        assertSame(Side.LEFT, g.winner)
    }

    @Test fun `시간 종료 동점이면 연장전, 연장 중 앞선 팀 승리`() {
        val g = solo()
        tapMany(g, 0, 12, 0, 100)
        tapMany(g, 1, 12, 0, 100)
        val end = GameConfig.TUG_TIME_MS
        assertSame(Phase.OVERTIME, g.timeUp(end))
        assertNull(g.winner)
        assertEquals(GameConfig.TUG_OVERTIME_MS, g.remainingMs(end))
        assertSame("연장 중에도 탭 인정", Tap.COUNTED, g.tap(1, end + 100))
        g.overtimeUp()
        assertEquals(Phase.DONE, g.phase)
        assertSame(Side.RIGHT, g.winner)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test fun `연장 중 승리선에 닿아도 바로 승리`() {
        val g = solo()
        g.timeUp(GameConfig.TUG_TIME_MS)
        assertEquals(Phase.OVERTIME, g.phase)
        val t0 = GameConfig.TUG_TIME_MS
        var last = Tap.IGNORED
        for (k in 0 until GameConfig.TUG_WIN_DIFF_SOLO) last = g.tap(0, t0 + k * 100L)
        // 5초 연장 안에 초당 10회로 40번 = 4초 → 승리
        assertSame(Tap.WON, last)
        assertSame(Side.LEFT, g.winner)
    }

    @Test fun `연장 시간이 지나면 탭 무시, 연장 뒤에도 동점이면 무승부 - 모두 1위`() {
        val g = team()
        g.tap(0, 10)
        g.tap(1, 20)
        g.timeUp(GameConfig.TUG_TIME_MS)
        assertSame(Tap.IGNORED, g.tap(0, GameConfig.TUG_TIME_MS + GameConfig.TUG_OVERTIME_MS))
        g.overtimeUp()
        assertTrue(g.draw)
        assertNull(g.winner)
        assertArrayEquals(intArrayOf(1, 1, 1, 1), g.ranks())
    }

    // ── 순위 ──

    @Test fun `2인 순위 - 이긴 사람 1위, 진 사람 2위`() {
        val g = solo()
        tapMany(g, 0, 3, 0, 100)
        g.timeUp(GameConfig.TUG_TIME_MS)
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }

    @Test fun `4인 순위 - 원래 순서로 이긴 팀 1위, 진 팀 3위`() {
        val g = team() // 0·3 왼쪽, 1·2 오른쪽
        tapMany(g, 3, 5, 0, 100)
        g.timeUp(GameConfig.TUG_TIME_MS)
        assertSame(Side.LEFT, g.winner)
        assertArrayEquals(intArrayOf(1, 3, 3, 1), g.ranks())

        val g2 = team()
        tapMany(g2, 2, 5, 0, 100)
        g2.timeUp(GameConfig.TUG_TIME_MS)
        assertArrayEquals(intArrayOf(3, 1, 1, 3), g2.ranks())
    }

    @Test fun `무작위 배정 4인도 순위는 원래 순서`() {
        repeat(30) { seed ->
            val teams = Teams.assign(4, Random(seed))
            val g = TugGame(teams)
            g.start(0)
            // 오른쪽 팀 첫 사람만 탭
            g.tap(teams.right[0], 10)
            g.timeUp(GameConfig.TUG_TIME_MS)
            val ranks = g.ranks()
            for (p in 0 until 4) assertEquals(if (teams.sideOf(p) == Side.RIGHT) 1 else 3, ranks[p])
        }
    }

    // ── 팀 배정 ──

    @Test fun `4인 팀 배정은 항상 두 명씩, 구역은 같은 팀이 같은 쪽`() {
        val seen = HashSet<String>()
        repeat(200) { seed ->
            val t = Teams.assign(4, Random(seed))
            assertEquals(2, t.left.size)
            assertEquals(2, t.right.size)
            assertEquals(4, (t.left + t.right).toSet().size)
            // 구역 0·2 = 왼쪽, 1·3 = 오른쪽
            assertSame(Side.LEFT, t.sideOf(t.zonePlayer[0]))
            assertSame(Side.LEFT, t.sideOf(t.zonePlayer[2]))
            assertSame(Side.RIGHT, t.sideOf(t.zonePlayer[1]))
            assertSame(Side.RIGHT, t.sideOf(t.zonePlayer[3]))
            for (p in 0 until 4) assertEquals(p, t.zonePlayer[t.playerZone[p]])
            seen.add(t.left.joinToString())
        }
        assertEquals("왼쪽 팀 조합 6가지가 모두 나옴", 6, seen.size)
    }

    @Test fun `2인은 첫 사람 왼쪽, 둘째 오른쪽`() {
        val t = Teams.assign(2, Random(1))
        assertArrayEquals(intArrayOf(0), t.left)
        assertArrayEquals(intArrayOf(1), t.right)
        assertArrayEquals(intArrayOf(0, 1), t.zonePlayer)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `3인은 지원 안 함`() {
        Teams.assign(3)
    }

    @Test fun `시작 전 탭은 무시`() {
        val g = TugGame(Teams.assign(2))
        assertSame(Tap.IGNORED, g.tap(0, 0))
        assertEquals(GameConfig.TUG_TIME_MS, g.remainingMs(0))
    }
}
