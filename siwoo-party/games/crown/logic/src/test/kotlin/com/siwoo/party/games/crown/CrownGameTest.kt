package com.siwoo.party.games.crown

import com.siwoo.party.games.crown.CrownGame.Companion.CENTER
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CrownGameTest {

    private fun started(n: Int = 4, at: Long = 1000L) = CrownGame(n).also { it.start(at) }

    @Test
    fun `시작하면 왕관은 가운데 점수 0`() {
        val g = started()
        assertSame(Phase.PLAYING, g.phase)
        assertEquals(CENTER, g.holder)
        for (p in 0 until 4) assertEquals(0, g.score(p))
        assertEquals(GameConfig.GAME_TIME_MS, g.timeLeft(1000L))
    }

    @Test
    fun `시작 전 누르기는 무시`() {
        val g = CrownGame(2)
        assertSame(Press.IGNORED, g.press(0, 0L))
        assertEquals(CENTER, g.holder)
        assertFalse(g.holding[0])
    }

    @Test
    fun `가운데 왕관은 처음 새로 누른 사람이 가져감`() {
        val g = started()
        assertSame(Press.TOOK_CENTER, g.press(2, 1100L))
        assertEquals(2, g.holder)
        assertTrue(g.holding[2])
    }

    @Test
    fun `가운데 왕관 줍기는 쿨다운이 없고 생기지도 않음`() {
        val g = started(2, 0L)
        g.press(0, 0L)
        assertSame(Press.STOLE, g.press(1, 100L))
        assertEquals(GameConfig.STEAL_COOLDOWN_MS - 100L, g.cooldownLeft(1, 200L))
        // 1 이 손을 떼면 가운데로 → 쿨다운 중인 1 도 다시 누르면 줍는다
        assertTrue(g.release(1, 300L))
        assertEquals(CENTER, g.holder)
        assertSame(Press.TOOK_CENTER, g.press(1, 400L))
        assertEquals(1, g.holder)
        // 줍기로는 쿨다운이 새로 생기지 않음 (100ms 에 빼앗은 것 기준 그대로)
        assertEquals(0L, g.cooldownLeft(1, 100L + GameConfig.STEAL_COOLDOWN_MS))
        assertEquals(0L, g.cooldownLeft(0, 400L))
    }

    @Test
    fun `쿨다운 2초 안에는 못 빼앗고 지나면 빼앗음`() {
        val g = started(3, 0L)
        g.press(0, 0L) // 0 이 가운데 왕관
        assertSame(Press.STOLE, g.press(1, 500L)) // 1 이 빼앗음 → 1 의 쿨다운 2초
        assertEquals(0, g.lastVictim)
        assertSame(Press.STOLE, g.press(0, 600L)) // 0 은 쿨다운 없음 → 다시 빼앗음
        assertEquals(0, g.holder)
        // 1 은 500 + 2000 = 2500 전까지 못 빼앗음
        g.release(1, 700L)
        assertSame(Press.COOLDOWN, g.press(1, 800L))
        assertEquals(0, g.holder)
        g.release(1, 900L)
        assertSame(Press.COOLDOWN, g.press(1, 2499L))
        assertEquals(1L, g.cooldownLeft(1, 2499L))
        assertEquals(0, g.holder)
        g.release(1, 2499L)
        assertSame(Press.STOLE, g.press(1, 2500L))
        assertEquals(1, g.holder)
        assertEquals(0, g.lastVictim)
        assertEquals(2, g.steals[1])
        assertEquals(1, g.steals[0])
        // 쿨다운은 사람마다 따로: 0 은 600 + 2000 = 2600 까지
        g.release(0, 2550L)
        assertSame(Press.COOLDOWN, g.press(0, 2550L))
        // 2 는 빼앗은 적 없음 → 바로 가능
        assertSame(Press.STOLE, g.press(2, 2560L))
        assertEquals(2, g.holder)
        assertEquals(1, g.lastVictim)
    }

    @Test
    fun `점수는 왕관 주인이 누르는 동안만 ms 단위로 정확히 쌓임`() {
        val g = started(3, 0L)
        g.press(1, 0L) // 처음 누른 1 이 가운데 왕관을 가져감
        assertEquals(1, g.holder)
        g.press(2, 0L) // 같은 순간 뒤에 들어온 2 가 빼앗음 → 1 은 누르고만 있음
        assertEquals(2, g.holder)
        // 2 가 1234ms 동안 가짐, 1 은 누르고 있어도 0
        g.update(1234L)
        assertEquals(1234L, g.heldMs[2])
        assertEquals(0L, g.heldMs[1])
        assertEquals(12, g.score(2)) // 1234ms × 10/초 = 12.34 → 12
        // 잘게 나눠 update 해도 같음
        for (t in 1235L..1300L) g.update(t)
        assertEquals(1300L, g.heldMs[2])
        assertEquals(13, g.score(2))
        // 같은 시각 update 는 아무것도 안 함, 과거 시각도 무시
        g.update(1300L)
        g.update(1000L)
        assertEquals(1300L, g.heldMs[2])
        // 손 떼면 멈춤
        g.release(2, 1350L)
        assertEquals(1350L, g.heldMs[2])
        g.update(5000L)
        assertEquals(1350L, g.heldMs[2])
        assertEquals(0L, g.heldMs[0])
        assertEquals(0L, g.heldMs[1])
    }

    @Test
    fun `빼앗긴 사람은 그때까지 점수만 가짐`() {
        val g = started(2, 0L)
        g.press(0, 0L)
        g.press(1, 777L)
        g.update(2000L)
        assertEquals(777L, g.heldMs[0])
        assertEquals(1223L, g.heldMs[1])
        assertEquals(7, g.score(0))
        assertEquals(12, g.score(1))
    }

    @Test
    fun `주인이 손을 떼면 왕관은 가운데로`() {
        val g = started(2, 0L)
        g.press(0, 0L)
        assertFalse("주인 아닌 사람이 떼면 그대로", g.release(1, 10L))
        assertEquals(0, g.holder)
        assertTrue(g.release(0, 500L))
        assertEquals(CENTER, g.holder)
        assertFalse(g.holding[0])
        g.update(3000L)
        assertEquals(500L, g.heldMs[0])
    }

    @Test
    fun `가운데로 돌아와도 누르고 있던 사람은 다시 눌러야 가져감`() {
        val g = started(3, 0L)
        g.press(0, 0L)
        g.press(1, 10L) // 1 이 빼앗음
        g.press(2, 20L) // 2 가 빼앗음 (1 은 계속 누르는 중)
        assertEquals(2, g.holder)
        assertTrue(g.holding[1])
        g.release(2, 1000L)
        assertEquals(CENTER, g.holder)
        // 누르고만 있는 1 은 가져가지 못하고 점수도 늘지 않음 (가졌던 10~20ms 만)
        g.update(2000L)
        assertEquals(CENTER, g.holder)
        assertEquals(10L, g.heldMs[1])
        // 새로 누른 0 이 가져감 (1 보다 나중에 누르기 시작했어도)
        assertSame(Press.TOOK_CENTER, g.press(0, 2100L))
        assertEquals(0, g.holder)
    }

    @Test
    fun `주인이 손가락을 하나 더 대도 변화 없음`() {
        val g = started(2, 0L)
        g.press(0, 0L)
        assertSame(Press.KEEP, g.press(0, 100L))
        assertEquals(0, g.holder)
        assertEquals(0, g.steals[0])
    }

    @Test
    fun `동시에 누르면 들어온 순서대로`() {
        // 같은 시각 0, 1, 2 순서: 0 이 줍고, 1 이 빼앗고, 2 가 빼앗음
        val g = started(3, 0L)
        assertSame(Press.TOOK_CENTER, g.press(0, 50L))
        assertSame(Press.STOLE, g.press(1, 50L))
        assertSame(Press.STOLE, g.press(2, 50L))
        assertEquals(2, g.holder)
        assertEquals(1, g.lastVictim)
        // 반대 순서면 결과도 반대
        val h = started(3, 0L)
        h.press(2, 50L); h.press(1, 50L); h.press(0, 50L)
        assertEquals(0, h.holder)
        // 둘 다 쿨다운이면 먼저 들어온 주운 사람이 지킴
        val k = started(2, 0L)
        k.press(0, 0L); k.press(1, 10L); k.press(0, 20L) // 0·1 모두 쿨다운
        k.release(0, 30L)
        k.release(1, 40L) // 이미 주인 아님
        assertEquals(CENTER, k.holder)
        assertSame(Press.TOOK_CENTER, k.press(1, 100L))
        assertSame(Press.COOLDOWN, k.press(0, 100L))
        assertEquals(1, k.holder)
    }

    @Test
    fun `45초가 지나면 끝나고 점수는 더 쌓이지 않음`() {
        val g = started(2, 10_000L)
        val end = 10_000L + GameConfig.GAME_TIME_MS
        assertEquals(end, g.endMs)
        g.press(0, 10_000L)
        g.update(end - 1)
        assertFalse(g.isTimeUp(end - 1))
        assertSame(Phase.PLAYING, g.phase)
        assertEquals(1L, g.timeLeft(end - 1))
        // 끝 시각을 지나서 update 해도 끝 시각까지만 계산
        g.update(end + 5000L)
        assertTrue(g.gameOver)
        assertTrue(g.isTimeUp(end))
        assertEquals(GameConfig.GAME_TIME_MS, g.heldMs[0])
        assertEquals(GameConfig.GAME_TIME_SEC * GameConfig.POINTS_PER_SEC, g.score(0))
        assertEquals(0L, g.timeLeft(end + 5000L))
        // 끝난 뒤 누르기는 무시, 떼기는 누름 상태만 정리
        assertSame(Press.IGNORED, g.press(1, end + 6000L))
        assertFalse(g.release(0, end + 6000L))
        assertFalse(g.holding[0])
    }

    @Test
    fun `끝 시각에 누르면 무시하고 끝남`() {
        val g = started(2, 0L)
        assertSame(Press.IGNORED, g.press(0, GameConfig.GAME_TIME_MS))
        assertTrue(g.gameOver)
        assertEquals(CENTER, g.holder)
    }

    @Test
    fun `순위는 점수 높은 순 동점은 같은 순위`() {
        val g = started(4, 0L)
        // 0: 3000ms, 1: 1000ms, 2: 3000ms, 3: 0
        g.press(0, 0L); g.release(0, 3000L)
        g.press(1, 3000L); g.release(1, 4000L)
        g.press(2, 4000L); g.release(2, 7000L)
        g.update(GameConfig.GAME_TIME_MS)
        assertTrue(g.gameOver)
        assertArrayEquals(intArrayOf(30, 10, 30, 0), IntArray(4) { g.score(it) })
        assertArrayEquals(intArrayOf(1, 3, 1, 4), g.ranks())
    }

    @Test
    fun `순위는 화면에 보이는 정수 점수 기준`() {
        // 1050ms 와 1099ms 는 둘 다 10점 → 공동 1위
        val g = started(2, 0L)
        g.press(0, 0L); g.release(0, 1050L)
        g.press(1, 2000L); g.release(1, 3099L)
        assertEquals(10, g.score(0))
        assertEquals(10, g.score(1))
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    @Test
    fun `인원 범위`() {
        for (n in 2..4) assertEquals(n, CrownGame(n).playerCount)
        assertTrue(runCatching { CrownGame(1) }.isFailure)
        assertTrue(runCatching { CrownGame(5) }.isFailure)
    }
}
