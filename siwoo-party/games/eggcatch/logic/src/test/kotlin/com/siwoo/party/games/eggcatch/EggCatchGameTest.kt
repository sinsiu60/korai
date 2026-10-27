package com.siwoo.party.games.eggcatch

import com.siwoo.party.common.lane.ItemState
import com.siwoo.party.common.lane.LanePattern
import com.siwoo.party.common.lane.LaneTrack
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 계란 받기 규칙: 점수(+1/+3/−3, 놓침 0), 바구니 겹침 경계, 같은 패턴, 빨라짐, 45초 끝, 순위 */
class EggCatchGameTest {

    private fun game(n: Int, seed: Long = 7L) = EggCatchGame(n, seed).also { it.start(START) }

    /** 경기 시각 t 의 시계 값 */
    private fun at(t: Long) = START + t

    /** 레인 lane 가운데 (레인 단위) */
    private fun center(lane: Int) = lane + 0.5f

    /** lane 에서 가장 먼 바구니 자리 (절대 안 겹침) */
    private fun far(lane: Int) = if (lane < 2) EggCatchGame.MAX_BASKET else EggCatchGame.MIN_BASKET

    private fun firstOf(g: EggCatchGame, kind: Int): Int = (0 until g.pattern.count).first { g.pattern.kind(it) == kind }

    @Test
    fun `계란 +1 · 황금 +3 · 폭탄 −3, 놓친 계란과 피한 폭탄은 0점`() {
        val g = game(2)
        val p = g.pattern
        var want = 0
        var eggs = 0; var golds = 0; var bombs = 0
        for (i in 0 until p.count) {
            // 구역 0 = 모두 받음, 구역 1 = 모두 피함
            g.setBasket(0, center(p.lane(i)))
            g.setBasket(1, far(p.lane(i)))
            val before0 = g.score(0)
            g.update(at(p.time(i)))
            assertEquals(ItemState.CAUGHT, g.tracks[0].state(i))
            assertEquals(ItemState.DROPPED, g.tracks[1].state(i))
            val k = p.kind(i)
            val d = g.score(0) - before0
            when (k) {
                GameConfig.KIND_EGG -> { assertEquals(1, d); eggs++ }
                GameConfig.KIND_GOLD -> { assertEquals(3, d); golds++ }
                else -> { assertEquals(-3, d); bombs++ }
            }
            want += d
            assertEquals("놓치기만 하면 0점", 0, g.score(1))
        }
        assertTrue("세 종류 모두 나옴 $eggs $golds $bombs", eggs > 0 && golds > 0 && bombs > 0)
        assertEquals(eggs + 3 * golds - 3 * bombs, g.score(0))
        assertEquals(want, g.score(0))
        assertEquals(eggs, g.eggs[0]); assertEquals(golds, g.golds[0]); assertEquals(bombs, g.bombs[0])
        assertEquals("깨진 계란 = 놓친 계란 + 황금 계란 (폭탄 제외)", eggs + golds, g.broken[1])
        assertEquals(0, g.broken[0])
        assertEquals(0, g.bombs[1])
        assertEquals(EggCatchGame.scoreOf(GameConfig.KIND_EGG), 1)
        assertEquals(EggCatchGame.scoreOf(GameConfig.KIND_GOLD), 3)
        assertEquals(EggCatchGame.scoreOf(GameConfig.KIND_BOMB), -3)
    }

    @Test
    fun `폭탄 하나만 피하면 0점, 받으면 −3`() {
        val g = game(2)
        val p = g.pattern
        val b = firstOf(g, GameConfig.KIND_BOMB)
        // 폭탄 앞의 물체는 둘 다 피함
        for (i in 0 until b) { g.setBasket(0, far(p.lane(i))); g.setBasket(1, far(p.lane(i))); g.update(at(p.time(i))) }
        assertEquals(0, g.score(0)); assertEquals(0, g.score(1))
        val broken1 = g.broken[1]
        g.setBasket(0, center(p.lane(b)))
        g.setBasket(1, far(p.lane(b)))
        g.update(at(p.time(b) - 1))
        assertEquals("닿기 전엔 판정 없음", ItemState.PENDING, g.tracks[0].state(b))
        g.update(at(p.time(b)))
        assertEquals(-3, g.score(0))
        assertEquals("피한 폭탄은 0점", 0, g.score(1))
        assertEquals(ItemState.DROPPED, g.tracks[1].state(b))
        assertEquals("폭탄은 깨진 계란이 아님", broken1, g.broken[1])
    }

    @Test
    fun `바구니 선에서 겹침 경계 — 가운데 거리 0·75 까지 받음`() {
        val reach = GameConfig.BASKET_HALF + GameConfig.ITEM_HALF
        assertEquals(0.75f, reach, 0f)
        // 레인 가운데에 둔 바구니는 그 레인만, 레인 사이에 두면 양쪽 레인 모두
        for (lane in 0 until GameConfig.LANES) {
            assertTrue(LaneTrack.catchOverlap(lane, center(lane), GameConfig.BASKET_HALF, GameConfig.ITEM_HALF))
            if (lane > 0) assertFalse(LaneTrack.catchOverlap(lane - 1, center(lane), GameConfig.BASKET_HALF, GameConfig.ITEM_HALF))
            if (lane < 3) assertFalse(LaneTrack.catchOverlap(lane + 1, center(lane), GameConfig.BASKET_HALF, GameConfig.ITEM_HALF))
        }
        assertTrue(LaneTrack.catchOverlap(1, 2f, GameConfig.BASKET_HALF, GameConfig.ITEM_HALF))
        assertTrue(LaneTrack.catchOverlap(2, 2f, GameConfig.BASKET_HALF, GameConfig.ITEM_HALF))
        assertFalse(LaneTrack.catchOverlap(0, 2f, GameConfig.BASKET_HALF, GameConfig.ITEM_HALF))
        assertFalse(LaneTrack.catchOverlap(3, 2f, GameConfig.BASKET_HALF, GameConfig.ITEM_HALF))

        // 실제 게임: 계란 하나를 정확히 경계(받음)·경계 밖 0.01(놓침)
        val g = game(4)
        val p = g.pattern
        val i = (0 until p.count).first { p.kind(it) == GameConfig.KIND_EGG }
        val lane = p.lane(i)
        val dir = if (lane <= 1) 1f else -1f
        for (j in 0 until i) { for (z in 0 until 4) g.setBasket(z, far(p.lane(j))); g.update(at(p.time(j))) }
        assertEquals(center(lane) + dir * reach, g.setBasket(0, center(lane) + dir * reach), 0f)
        g.setBasket(1, center(lane) + dir * (reach + 0.01f))
        g.setBasket(2, center(lane) - dir * 0.2f)
        g.setBasket(3, center(lane) + dir * 0.74f)
        g.update(at(p.time(i)))
        assertEquals("정확히 경계 = 받음", ItemState.CAUGHT, g.tracks[0].state(i))
        assertEquals("경계 밖 = 놓침", ItemState.DROPPED, g.tracks[1].state(i))
        assertEquals(ItemState.CAUGHT, g.tracks[2].state(i))
        assertEquals(ItemState.CAUGHT, g.tracks[3].state(i))
        assertArrayEquals(intArrayOf(1, 0, 1, 1), IntArray(4) { g.score(it) })
        assertEquals(1, g.broken[1])
    }

    @Test
    fun `바구니는 레인 영역 안으로 잘린다 — 양끝 레인도 받을 수 있음`() {
        val g = game(2)
        assertEquals(GameConfig.LANES / 2f, g.basket(0), 0f)
        assertEquals(0.5f, g.setBasket(0, -3f), 0f)
        assertEquals(3.5f, g.setBasket(1, 99f), 0f)
        assertEquals(0.5f, g.basket(0), 0f)
        assertEquals(3.5f, g.basket(1), 0f)
        assertEquals(2f, g.setBasket(0, Float.NaN), 0f)
        assertTrue(LaneTrack.catchOverlap(0, EggCatchGame.MIN_BASKET, GameConfig.BASKET_HALF, GameConfig.ITEM_HALF))
        assertTrue(LaneTrack.catchOverlap(3, EggCatchGame.MAX_BASKET, GameConfig.BASKET_HALF, GameConfig.ITEM_HALF))
        // 없는 구역은 무시
        assertEquals(0f, g.setBasket(5, 1f), 0f)
    }

    @Test
    fun `모든 구역이 같은 패턴 — 같은 움직임이면 같은 점수`() {
        val g = game(4, seed = 21L)
        for (tr in g.tracks) assertSame(g.pattern, tr.pattern)
        assertTrue(EggCatchGame(3, 21L).pattern.sameAs(g.pattern))
        assertFalse(EggCatchGame(3, 22L).pattern.sameAs(g.pattern))
        val p = g.pattern
        for (i in 0 until p.count) {
            val u = if (i % 3 == 0) far(p.lane(i)) else center(p.lane(i))
            for (z in 0 until 4) g.setBasket(z, u)
            g.update(at(p.time(i)))
        }
        val s = IntArray(4) { g.score(it) }
        assertTrue(s.all { it == s[0] })
        assertArrayEquals(intArrayOf(1, 1, 1, 1), g.finalRanks())
        for (z in 1 until 4) for (i in 0 until p.count) assertEquals(g.tracks[0].state(i), g.tracks[z].state(i))
    }

    @Test
    fun `점점 빨라짐 — 떨어지는 시간은 줄고 간격은 촘촘`() {
        val c = EggCatchGame.patternConfig()
        assertEquals(GameConfig.START_FALL_MS, LanePattern.fallAt(c, 0L))
        assertEquals(GameConfig.END_FALL_MS, LanePattern.fallAt(c, GameConfig.GAME_MS))
        assertTrue(LanePattern.fallAt(c, 20_000L) in GameConfig.END_FALL_MS + 1 until GameConfig.START_FALL_MS)
        assertEquals(GameConfig.START_BPM, LanePattern.bpmAt(c, 0L), 0.01f)
        assertTrue(LanePattern.bpmAt(c, 40_000L) > 100f)
        var firstSum = 0; var lastSum = 0; var eggN = 0; var goldN = 0; var bombN = 0; var all = 0
        for (seed in 1L..20L) {
            val p = EggCatchGame(2, seed).pattern
            for (i in 1 until p.count) assertTrue("떨어지는 시간은 줄기만", p.fallMs(i) <= p.fallMs(i - 1))
            assertTrue(p.fallMs(p.count - 1) < p.fallMs(0) - 800)
            for (i in 1 until p.count) assertTrue("최소 간격", p.time(i) - p.time(i - 1) >= GameConfig.MIN_GAP_MS)
            for (i in 1 until p.count) assertTrue("레인 이동 ≤ 2", Math.abs(p.lane(i) - p.lane(i - 1)) <= GameConfig.MAX_LANE_STEP)
            firstSum += (0 until p.count).count { p.time(it) < 12_000L }
            lastSum += (0 until p.count).count { p.time(it) in 34_000L until 44_000L + 1 }
            for (i in 0 until p.count) {
                all++
                when (p.kind(i)) { GameConfig.KIND_EGG -> eggN++; GameConfig.KIND_GOLD -> goldN++; else -> bombN++ }
            }
            assertTrue((0 until p.count).all { p.lane(it) in 0 until GameConfig.LANES })
        }
        // 처음 10초(2~12초) 보다 마지막 10초(34~44초)에 물체가 훨씬 많다
        assertTrue("처음 $firstSum, 마지막 $lastSum", lastSum > firstSum * 3 / 2)
        // 비율 80 / 8 / 12 근처
        assertEquals(0.80, eggN.toDouble() / all, 0.05)
        assertEquals(0.08, goldN.toDouble() / all, 0.03)
        assertEquals(0.12, bombN.toDouble() / all, 0.03)
    }

    @Test
    fun `45초에 끝 — 마지막 물체는 끝나기 전, 끝난 뒤엔 변화 없음`() {
        val g = EggCatchGame(3, 5L)
        assertFalse(g.isRunning(START))
        g.update(START + 50_000)
        assertEquals("시작 전엔 판정 없음", 0, g.tracks[0].nextIndex)
        g.start(START)
        assertTrue(g.isRunning(at(0)))
        assertTrue(g.isRunning(at(GameConfig.GAME_MS - 1)))
        assertEquals(1L, g.remainingMs(at(GameConfig.GAME_MS - 1)))
        assertFalse(g.isOver(at(GameConfig.GAME_MS - 1)))
        assertTrue(g.isOver(at(GameConfig.GAME_MS)))
        assertFalse(g.isRunning(at(GameConfig.GAME_MS)))
        assertEquals(0L, g.remainingMs(at(GameConfig.GAME_MS + 5_000)))
        assertTrue(g.pattern.lastTime <= GameConfig.LAST_ITEM_MS)
        assertTrue(GameConfig.LAST_ITEM_MS < GameConfig.GAME_MS)
        assertTrue("첫 물체는 준비 시간 뒤", g.pattern.time(0) >= GameConfig.LEAD_IN_MS)
        assertEquals(45_000L, GameConfig.GAME_MS)
        // 끝 시각까지 모든 물체 판정 끝
        g.update(at(GameConfig.GAME_MS))
        for (tr in g.tracks) assertTrue(tr.finished)
        val s = IntArray(3) { g.score(it) }
        g.setBasket(0, 0f)
        g.update(at(GameConfig.GAME_MS + 10_000))
        assertArrayEquals(s, IntArray(3) { g.score(it) })
    }

    @Test
    fun `순위 — 점수 순, 같은 점수 같은 순위, 음수 가능`() {
        val g = game(4, seed = 3L)
        val p = g.pattern
        for (i in 0 until p.count) {
            val lane = p.lane(i)
            val bomb = p.kind(i) == GameConfig.KIND_BOMB
            // 0·1 = 좋은 것만 받음(동점), 2 = 모두 피함(0점), 3 = 폭탄만 받음(음수)
            val good = if (bomb) far(lane) else center(lane)
            g.setBasket(0, good)
            g.setBasket(1, good)
            g.setBasket(2, far(lane))
            g.setBasket(3, if (bomb) center(lane) else far(lane))
            g.update(at(p.time(i)))
        }
        assertTrue(g.score(0) > 0)
        assertEquals(g.score(0), g.score(1))
        assertEquals(0, g.score(2))
        assertTrue("음수 점수 ${g.score(3)}", g.score(3) < 0)
        assertEquals(-3 * g.bombs[3], g.score(3))
        assertArrayEquals(intArrayOf(1, 1, 3, 4), g.finalRanks())
        assertEquals(listOf(0, 1), g.leaders())
    }

    @Test
    fun `2~4명만`() {
        for (n in 2..4) assertEquals(n, EggCatchGame(n, 1L).tracks.size)
        for (n in intArrayOf(1, 5)) {
            try { EggCatchGame(n, 1L); error("$n 명 허용됨") } catch (e: IllegalArgumentException) { /* 정상 */ }
        }
    }

    companion object {
        const val START = 1_000L
    }
}
