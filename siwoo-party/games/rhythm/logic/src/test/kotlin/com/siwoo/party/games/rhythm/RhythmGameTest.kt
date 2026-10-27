package com.siwoo.party.games.rhythm

import com.siwoo.party.common.lane.ItemState
import com.siwoo.party.common.lane.Judgement
import com.siwoo.party.common.lane.LanePattern
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 리듬 노트 규칙: 판정 구간, 콤보, 점수 합, 같은 패턴, 빨라지는 박자, 순위 */
class RhythmGameTest {

    /** 시계 1000 에 시작한 게임 */
    private fun game(n: Int, seed: Long = 7L) = RhythmGame(n, seed).also { it.start(START) }

    /** 경기 시각 t 의 시계 값 */
    private fun at(t: Long) = START + t

    /** 앞뒤 노트와 충분히 떨어진(± 2×MISS_MS) 노트 번호들 — 판정 창을 정확히 시험하기 위해 */
    private fun isolated(g: RhythmGame): List<Int> {
        val p = g.pattern
        return (0 until p.count).filter { i ->
            (i == 0 || p.time(i) - p.time(i - 1) > 2 * GameConfig.MISS_MS) &&
                (i == p.count - 1 || p.time(i + 1) - p.time(i) > 2 * GameConfig.MISS_MS)
        }
    }

    @Test
    fun `판정 구간 - 이르게·늦게 퍼펙트·굿·미스 경계`() {
        val g = game(4)
        val iso = isolated(g)
        assertTrue("고립 노트 충분 ${iso.size}", iso.size >= 12)
        val p = g.pattern
        // (어긋남, 기대 판정) — 음수 = 이르게, 양수 = 늦게
        val cases = listOf(
            0L to Judgement.PERFECT,
            -GameConfig.PERFECT_MS to Judgement.PERFECT,
            GameConfig.PERFECT_MS to Judgement.PERFECT,
            -(GameConfig.PERFECT_MS + 1) to Judgement.GOOD,
            GameConfig.PERFECT_MS + 1 to Judgement.GOOD,
            -GameConfig.GOOD_MS to Judgement.GOOD,
            GameConfig.GOOD_MS to Judgement.GOOD,
            -(GameConfig.GOOD_MS + 1) to Judgement.MISS,
            -GameConfig.MISS_MS to Judgement.MISS,
        )
        for ((k, c) in cases.withIndex()) {
            val i = iso[k]
            val zone = k % 4
            assertSame("어긋남 ${c.first}", c.second, g.tap(zone, at(p.time(i) + c.first)))
            val st = g.tracks[zone].state(i)
            val want = when (c.second) {
                Judgement.PERFECT -> ItemState.PERFECT
                Judgement.GOOD -> ItemState.GOOD
                else -> ItemState.MISS
            }
            assertEquals("노트 상태 ${c.first}", want, st)
        }
        // 너무 이르면(> MISS_MS) 헛손질: 노트는 그대로
        val i = iso[cases.size]
        assertSame(Judgement.NONE, g.tap(0, at(p.time(i) - GameConfig.MISS_MS - 1)))
        assertEquals(ItemState.PENDING, g.tracks[0].state(i))
        // 늦게 GOOD_MS 넘게 지나면 이미 미스 처리 → 그 노트로는 점수 없음 (헛손질)
        assertSame(Judgement.NONE, g.tap(0, at(p.time(i) + GameConfig.GOOD_MS + 1)))
        assertEquals(ItemState.MISS, g.tracks[0].state(i))
    }

    @Test
    fun `두 번 쳐도 한 노트는 한 번만 점수`() {
        val g = game(2)
        val i = isolated(g)[0]
        val t = g.pattern.time(i)
        assertSame(Judgement.PERFECT, g.tap(0, at(t)))
        assertEquals(GameConfig.PERFECT_SCORE, g.score(0))
        // 바로 또 쳐도 (같은 노트 근처) 헛손질
        assertSame(Judgement.NONE, g.tap(0, at(t + 30)))
        assertSame(Judgement.NONE, g.tap(0, at(t + 100)))
        assertEquals(GameConfig.PERFECT_SCORE, g.score(0))
        assertEquals(1, g.combo(0))
        // 다른 구역은 그 노트를 따로 판정
        assertSame(Judgement.GOOD, g.tap(1, at(t + 100)))
        assertEquals(GameConfig.GOOD_SCORE, g.score(1))
    }

    @Test
    fun `콤보 - 맞히면 늘고 미스·지나침이면 끊김, 최대 콤보는 남음`() {
        val g = game(2)
        val p = g.pattern
        for (i in 0 until 5) g.tap(0, at(p.time(i)))
        assertEquals(5, g.combo(0))
        assertEquals(5, g.maxCombo(0))
        // 노트 5 는 치지 않고 지나감 → 미스, 콤보 0
        g.update(at(p.time(5) + GameConfig.GOOD_MS + 1))
        assertEquals(ItemState.MISS, g.tracks[0].state(5))
        assertEquals(0, g.combo(0))
        assertEquals(5, g.maxCombo(0))
        // 다시 이어 치기 (굿도 콤보)
        g.tap(0, at(p.time(6) + 100))
        g.tap(0, at(p.time(7)))
        assertEquals(2, g.combo(0))
        // 이르게 쳐서 미스 → 끊김
        val iso = isolated(g).first { it > 8 }
        for (i in 8 until iso) g.tap(0, at(p.time(i)))
        val before = g.combo(0)
        assertTrue(before >= 2)
        assertSame(Judgement.MISS, g.tap(0, at(p.time(iso) - GameConfig.GOOD_MS - 20)))
        assertEquals(0, g.combo(0))
        assertEquals(maxOf(5, before), g.maxCombo(0))
        // 헛손질은 콤보를 끊지 않음
        g.tap(0, at(p.time(iso + 1)))
        assertEquals(1, g.combo(0))
        val far = isolated(g).first { it > iso + 2 }
        for (i in iso + 2 until far) g.tap(0, at(p.time(i)))
        val c = g.combo(0)
        assertSame(Judgement.NONE, g.tap(0, at(p.time(far) - GameConfig.MISS_MS - 50)))
        assertEquals(c, g.combo(0))
    }

    @Test
    fun `점수 합 - 퍼펙트 3 굿 1 미스 0`() {
        val g = game(3)
        val p = g.pattern
        // 고립 노트(앞뒤 440ms 이상 떨어짐)는 번갈아 굿·건너뛰기, 나머지는 정확히 퍼펙트
        val iso = isolated(g).toHashSet()
        var expected = 0
        var flip = 0
        for (i in 0 until p.count) {
            val t = p.time(i)
            if (i in iso) {
                if (flip++ % 2 == 0) { assertSame(Judgement.GOOD, g.tap(0, at(t + GameConfig.GOOD_MS))); expected += 1 }
                else g.update(at(t + GameConfig.GOOD_MS + 1))
            } else {
                assertSame(Judgement.PERFECT, g.tap(0, at(t))); expected += 3
            }
        }
        g.update(at(GameConfig.GAME_MS))
        val tr = g.tracks[0]
        assertEquals(expected, g.score(0))
        assertEquals(tr.perfects * 3 + tr.goods * 1, g.score(0))
        assertEquals(p.count, tr.perfects + tr.goods + tr.misses)
        assertTrue(tr.finished)
        // 아무것도 안 친 구역: 모두 미스, 0점
        assertEquals(0, g.score(1))
        assertEquals(p.count, g.tracks[1].misses)
        // 전부 퍼펙트면 3 × 노트 수
        val g2 = game(2)
        for (i in 0 until p.count) g2.tap(1, at(p.time(i)))
        assertEquals(3 * p.count, g2.score(1))
        assertEquals(p.count, g2.maxCombo(1))
    }

    @Test
    fun `모든 구역 같은 패턴 - 같은 시드 같은 노트, 구역 진행은 따로`() {
        val g = game(4, seed = 42L)
        for (z in 1 until 4) assertSame(g.tracks[0].pattern, g.tracks[z].pattern)
        assertTrue(g.pattern.sameAs(RhythmGame(2, 42L).pattern))
        assertFalse(g.pattern.sameAs(RhythmGame(2, 43L).pattern))
        assertEquals(1, g.pattern.lanes)
        // 구역 0 만 쳐도 다른 구역 상태는 그대로
        g.tap(0, at(g.pattern.time(0)))
        assertEquals(ItemState.PERFECT, g.tracks[0].state(0))
        for (z in 1 until 4) assertEquals(ItemState.PENDING, g.tracks[z].state(0))
    }

    @Test
    fun `빨라지는 박자 - BPM 100 에서 160, 노트는 박자 격자 위, 마지막 노트는 경기 끝 전`() {
        val g = game(2)
        val c = g.pattern.config
        assertEquals(100f, LanePattern.bpmAt(c, 0L), 0.01f)
        assertEquals(130f, LanePattern.bpmAt(c, GameConfig.RAMP_MS / 2), 0.01f)
        assertEquals(160f, LanePattern.bpmAt(c, GameConfig.RAMP_MS), 0.01f)
        assertEquals(160f, LanePattern.bpmAt(c, GameConfig.GAME_MS), 0.01f)
        assertEquals(100, g.bpmAt(at(0)))
        assertEquals(160, g.bpmAt(at(59_000)))
        val b = g.beats
        // 준비 박자 2개 + 첫 박 = 첫 노트
        assertEquals(GameConfig.COUNT_IN_BEATS, g.countIn)
        assertEquals(800L, b[0])
        assertEquals(1_400L, b[1])
        assertEquals(GameConfig.LEAD_IN_MS, b[2])
        assertEquals(GameConfig.LEAD_IN_MS, g.pattern.time(0))
        // 박자 간격이 600ms(100bpm) 에서 375ms(160bpm) 로 줄어듦
        assertEquals(600L, b[2] - b[1])
        assertTrue("둘째 간격 ${b[3] - b[2]}", b[3] - b[2] in 580L..600L)
        val lastGap = b[b.size - 1] - b[b.size - 2]
        assertTrue("끝 간격 $lastGap", lastGap in 374L..376L)
        for (k in 3 until b.size) assertTrue("간격이 늘지 않음 $k", b[k] - b[k - 1] <= b[k - 1] - b[k - 2] + 1)
        // 모든 노트는 박자 위(정박) 또는 두 박 사이 가운데(엇박)
        val set = b.toHashSet()
        var on = 0
        for (i in 0 until g.pattern.count) {
            val t = g.pattern.time(i)
            if (t in set) { on++; continue }
            val k = g.beatsUpTo(t) - 1
            assertTrue("엇박 $t", k >= 0)
            // 마지막 박 뒤 엇박은 다음 박이 목록에 없으므로 그 박의 간격으로
            val next = if (k + 1 < b.size) b[k + 1] else b[k] + (60_000.0 / LanePattern.bpmAt(c, b[k])).toLong()
            assertTrue("엇박 $t", kotlin.math.abs(t - (b[k] + next) / 2) <= 1)
        }
        assertTrue(on > g.pattern.count * 3 / 4)
        assertTrue(g.pattern.lastTime <= GameConfig.LAST_NOTE_MS)
        assertTrue(g.pattern.lastTime + GameConfig.GOOD_MS < GameConfig.GAME_MS)
        // 노트가 점점 촘촘해짐: 뒤 15초 노트 수 > 앞 15초
        val early = (0 until g.pattern.count).count { g.pattern.time(it) < 17_000 }
        val late = (0 until g.pattern.count).count { g.pattern.time(it) >= 43_500 }
        assertTrue("앞 $early 뒤 $late", late > early)
        // 강박: 첫 박부터 4박마다
        assertFalse(g.isAccent(0))
        assertTrue(g.isAccent(2))
        assertFalse(g.isAccent(3))
        assertTrue(g.isAccent(6))
        assertEquals(0, g.beatsUpTo(799))
        assertEquals(1, g.beatsUpTo(800))
        assertEquals(b.size, g.beatsUpTo(GameConfig.GAME_MS))
    }

    @Test
    fun `경기 전·후 터치는 무시, 60초에 끝`() {
        val g = RhythmGame(2, 1L)
        assertSame(Judgement.NONE, g.tap(0, 5_000))
        assertFalse(g.isOver(5_000))
        g.start(START)
        assertTrue(g.isRunning(at(59_999)))
        assertTrue(g.isOver(at(GameConfig.GAME_MS)))
        assertEquals(0L, g.remainingMs(at(70_000)))
        assertSame(Judgement.NONE, g.tap(0, at(GameConfig.GAME_MS + 1)))
        assertEquals(0, g.score(0))
    }

    @Test
    fun `순위 - 점수 순, 같으면 최대 콤보, 그것도 같으면 같은 순위`() {
        val g = game(4)
        val p = g.pattern
        // 0: 앞 10개 퍼펙트(30점, 콤보 10)  1: 앞 5 퍼펙트 + 1 건너뛰고 5 퍼펙트(30점, 콤보 5)
        // 2: 30 굿(30점, 콤보 30)              3: 아무것도 (0점)
        for (i in 0 until 10) g.tap(0, at(p.time(i)))
        for (i in 0 until 11) if (i != 5) g.tap(1, at(p.time(i)))
        for (i in 0 until 30) g.tap(2, at(p.time(i) + 100))
        g.update(at(GameConfig.GAME_MS))
        assertEquals(30, g.score(0)); assertEquals(30, g.score(1)); assertEquals(30, g.score(2)); assertEquals(0, g.score(3))
        assertEquals(10, g.maxCombo(0)); assertEquals(5, g.maxCombo(1)); assertEquals(30, g.maxCombo(2))
        assertArrayEquals(intArrayOf(2, 3, 1, 4), g.finalRanks())
        // 점수·최대 콤보가 같으면 같은 순위 (경쟁 순위)
        val h = game(3)
        for (z in 0 until 2) for (i in 0 until 4) h.tap(z, at(h.pattern.time(i)))
        h.update(at(GameConfig.GAME_MS))
        assertArrayEquals(intArrayOf(1, 1, 3), h.finalRanks())
        // 점수가 콤보보다 먼저
        assertTrue(RhythmGame.rankKey(4, 0) > RhythmGame.rankKey(3, 999))
        // 아무도 안 치면 모두 1위
        val z = game(2)
        z.update(at(GameConfig.GAME_MS))
        assertArrayEquals(intArrayOf(1, 1), z.finalRanks())
    }

    companion object {
        const val START = 1_000L
    }
}
