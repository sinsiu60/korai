package com.siwoo.party.common.lane

import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 레인: 같은 시드 같은 패턴, 판정 창 경계, 지나가면 MISS, 받기 겹침, 빨라지는 박자, 위치 결정성, 구역 좌표 회전 */
class LaneTest {

    private fun rhythm(seed: Long = 7L) = LanePattern.generate(
        LanePatternConfig(lanes = 1, startBpm = 100f, endBpm = 160f, rampMs = 40_000L, durationMs = 45_000L, restChance = 0.2f),
        seed,
    )

    private fun eggs(seed: Long = 3L) = LanePattern.generate(
        LanePatternConfig(
            lanes = 4, startBpm = 60f, endBpm = 110f, rampMs = 50_000L, durationMs = 60_000L,
            restChance = 0.1f, kindWeights = intArrayOf(80, 8, 12), maxLaneStep = 2,
        ),
        seed,
    )

    @Test
    fun `같은 시드 - 모든 구역 같은 패턴, 다른 시드 - 다른 패턴`() {
        val a = eggs(11L); val b = eggs(11L); val c = eggs(12L)
        assertTrue(a.count > 30)
        assertTrue(a.sameAs(b))
        assertFalse(a.sameAs(c))
        // 한 패턴을 구역 4개가 함께 써도 진행 상태는 각자
        val tracks = Array(4) { LaneTrack(a, zone = it) }
        val t0 = a.time(0)
        assertEquals(Judgement.PERFECT, tracks[0].judge(t0))
        tracks[1].update(t0 + 500)
        assertEquals(ItemState.PERFECT, tracks[0].state(0))
        assertEquals(ItemState.MISS, tracks[1].state(0))
        assertEquals(ItemState.PENDING, tracks[2].state(0))
        // 종류 3가지가 모두 나오고 레인 범위 안, 시간순
        val seen = BooleanArray(3)
        for (i in 0 until a.count) {
            seen[a.kind(i)] = true
            assertTrue(a.lane(i) in 0..3)
            if (i > 0) {
                assertTrue(a.time(i) > a.time(i - 1))
                assertTrue("레인 이동 ≤ 2", kotlin.math.abs(a.lane(i) - a.lane(i - 1)) <= 2)
            }
        }
        assertTrue(seen.all { it })
    }

    @Test
    fun `판정 창 경계 - 퍼펙트·굿·미스·없음`() {
        val c = LaneJudgeConfig(perfectMs = 50, goodMs = 120, missMs = 200)
        assertEquals(Judgement.PERFECT, LaneTrack.judgeOffset(0, c))
        assertEquals(Judgement.PERFECT, LaneTrack.judgeOffset(50, c))
        assertEquals(Judgement.PERFECT, LaneTrack.judgeOffset(-50, c))
        assertEquals(Judgement.GOOD, LaneTrack.judgeOffset(51, c))
        assertEquals(Judgement.GOOD, LaneTrack.judgeOffset(-120, c))
        assertEquals(Judgement.MISS, LaneTrack.judgeOffset(-121, c))
        assertEquals(Judgement.MISS, LaneTrack.judgeOffset(200, c))
        assertEquals(Judgement.NONE, LaneTrack.judgeOffset(201, c))
    }

    @Test
    fun `치기 - 점수 3·1·0, 콤보, 이른 헛손질은 MISS 로 소모`() {
        val p = rhythm()
        val tr = LaneTrack(p, LaneJudgeConfig(perfectMs = 50, goodMs = 120, missMs = 200))
        // 첫 물체보다 한참 전 헛손질 → NONE (점수·콤보 그대로)
        assertEquals(Judgement.NONE, tr.judge(p.time(0) - 500))
        assertEquals(ItemState.PENDING, tr.state(0))
        assertEquals(Judgement.PERFECT, tr.judge(p.time(0) + 30))
        assertEquals(Judgement.GOOD, tr.judge(p.time(1) - 100))
        assertEquals(3 + 1, tr.score)
        assertEquals(2, tr.combo)
        assertEquals(Judgement.MISS, tr.judge(p.time(2) - 180))
        assertEquals(ItemState.MISS, tr.state(2))
        assertEquals(0, tr.combo)
        assertEquals(2, tr.maxCombo)
        assertEquals(4, tr.score)
        // 같은 물체를 두 번 칠 수 없음
        assertEquals(Judgement.NONE, tr.judge(p.time(2)))
        assertEquals(1, tr.perfects); assertEquals(1, tr.goods); assertEquals(1, tr.misses)
    }

    @Test
    fun `판정선을 지나도록 안 치면 MISS, 알림`() {
        val p = rhythm()
        val tr = LaneTrack(p, LaneJudgeConfig(perfectMs = 50, goodMs = 120, missMs = 200))
        val got = ArrayList<Int>()
        tr.listener = LaneTrack.Listener { _, i, st -> if (st == ItemState.MISS) got.add(i) }
        tr.update(p.time(0) + 120)
        assertEquals(ItemState.PENDING, tr.state(0))
        tr.update(p.time(0) + 121)
        assertEquals(ItemState.MISS, tr.state(0))
        assertEquals(listOf(0), got)
        // 끝까지 흘리면 전부 MISS, 끝
        tr.update(p.lastTime + 1000)
        assertTrue(tr.finished)
        assertEquals(p.count, tr.misses)
        assertEquals(0, tr.score)
        tr.reset()
        assertEquals(0, tr.misses)
        assertEquals(ItemState.PENDING, tr.state(0))
    }

    @Test
    fun `여러 레인 - 그 레인 물체만 판정`() {
        val p = eggs()
        val tr = LaneTrack(p)
        val lane0 = p.lane(0)
        val other = (lane0 + 1) % 4
        // 같은 시간 다른 레인 물체가 없으면 NONE
        var otherNear = false
        for (i in 0 until p.count) if (p.lane(i) == other && kotlin.math.abs(p.time(i) - p.time(0)) <= 220) otherNear = true
        if (!otherNear) assertEquals(Judgement.NONE, tr.judge(p.time(0), other))
        assertEquals(Judgement.PERFECT, tr.judge(p.time(0), lane0))
    }

    @Test
    fun `받기 겹침 - 바구니 반 폭 + 물체 반 폭`() {
        // 레인 2 가운데 = 2.5, 바구니 반 폭 0.5, 물체 반 폭 0.35 → 1.65 ~ 3.35
        assertTrue(LaneTrack.catchOverlap(2, 2.5f, 0.5f, 0.35f))
        assertTrue(LaneTrack.catchOverlap(2, 1.65f, 0.5f, 0.35f))
        assertTrue(LaneTrack.catchOverlap(2, 3.35f, 0.5f, 0.35f))
        assertFalse(LaneTrack.catchOverlap(2, 1.6f, 0.5f, 0.35f))
        assertFalse(LaneTrack.catchOverlap(2, 3.4f, 0.5f, 0.35f))
        assertFalse(LaneTrack.catchOverlap(0, 3.5f, 0.5f, 0.35f))
    }

    @Test
    fun `받기 진행 - 받음·떨어짐, 종류 점수 +1·+3·-3`() {
        val p = eggs()
        val tr = LaneTrack(p, LaneJudgeConfig(catchScores = intArrayOf(1, 3, -3)))
        var expect = 0
        var caught = 0
        var t = 0L
        // 짝수 번째는 바구니를 그 레인에, 홀수 번째는 먼 곳에
        for (i in 0 until p.count) {
            val lane = p.lane(i)
            val basket = if (i % 2 == 0) lane + 0.5f else (if (lane < 2) 3.6f else 0.4f)
            // 판정선 직전엔 아직 처리 안 됨
            tr.updateCatch(p.time(i) - 1, basket, 0.5f)
            assertEquals(ItemState.PENDING, tr.state(i))
            t = p.time(i)
            tr.updateCatch(t, basket, 0.5f)
            if (i % 2 == 0) {
                assertEquals(ItemState.CAUGHT, tr.state(i))
                expect += intArrayOf(1, 3, -3)[p.kind(i)]
                caught++
            } else {
                assertEquals(ItemState.DROPPED, tr.state(i))
            }
        }
        assertTrue(tr.finished)
        assertEquals(expect, tr.score)
        assertEquals(caught, tr.caught)
        assertEquals(p.count - caught, tr.dropped)
        assertEquals(-3, tr.catchScore(2))
    }

    @Test
    fun `빨라지는 박자 - 뒤로 갈수록 간격이 줄고 떨어지는 시간도 짧아짐`() {
        val c = LanePatternConfig(lanes = 1, startBpm = 80f, endBpm = 160f, rampMs = 30_000L, durationMs = 40_000L, restChance = 0f)
        val p = LanePattern.generate(c, 1L)
        assertEquals(750f, 60_000f / LanePattern.bpmAt(c, 0), 0.01f)
        assertEquals(375f, 60_000f / LanePattern.bpmAt(c, 30_000), 0.01f)
        val firstGap = p.time(1) - p.time(0)
        val lastGap = p.time(p.count - 1) - p.time(p.count - 2)
        assertTrue(firstGap in 700..760)
        assertTrue(lastGap in 370..380)
        var prev = 1_000_000L
        for (i in 1 until p.count) {
            val g = p.time(i) - p.time(i - 1)
            assertTrue("간격이 늘지 않음 $i: $prev → $g", g <= prev + 2)
            prev = g
        }
        assertTrue(p.fallMs(p.count - 1) < p.fallMs(0))
        assertEquals(LanePattern.fallAt(c, p.time(0)), p.fallMs(0))
        assertEquals(1200L, LanePattern.fallAt(c, 35_000))
        // 떨어지는 시간이 줄어도 나타나는 시간은 시간순 (추월 없음)
        for (i in 1 until p.count) assertTrue(p.spawnTime(i) >= p.spawnTime(i - 1))
    }

    @Test
    fun `위치 - 생성 0, 판정선 1, 지나면 1 초과, 같은 시간이면 같은 값`() {
        val p = rhythm()
        val i = 5
        assertEquals(0f, p.position(i, p.spawnTime(i)), 1e-5f)
        assertEquals(1f, p.position(i, p.time(i)), 1e-5f)
        assertEquals(0.5f, p.position(i, p.time(i) - p.fallMs(i) / 2), 1e-3f)
        assertTrue(p.position(i, p.time(i) + 100) > 1f)
        assertTrue(p.position(i, p.spawnTime(i) - 1) < 0f)
        val q = rhythm()
        for (t in longArrayOf(1000, 5000, 12_345)) assertEquals(p.position(i, t), q.position(i, t), 0f)
        // 그리기 범위: 나타난 물체 ~ 판정선 지난 지 afterMs 안
        val tr = LaneTrack(p)
        val now = p.time(i)
        val from = tr.drawFrom(now); val to = tr.drawTo(now)
        assertTrue(from <= i && i < to)
        for (k in from until to) {
            val pos = tr.position(k, now)
            assertTrue(pos >= 0f)
            assertTrue(now - p.time(k) <= tr.judgeConfig.afterMs)
        }
        assertEquals(0, tr.drawTo(0))
    }

    @Test
    fun `구역 좌표 - 아래 구역은 그대로, 위 구역(180도)은 뒤집힘, 왕복 일치`() {
        val zones = TouchZones.layout(4, 1280f, 720f)
        val f = ZoneFrame()
        // 아래 왼쪽 구역 (회전 0): 로컬 = 화면 - 왼쪽 위
        f.set(zones[2])
        assertEquals(640f, f.width, 1e-3f)
        assertEquals(360f, f.height, 1e-3f)
        assertEquals(10f, f.toLocalX(10f, 370f), 1e-3f)
        assertEquals(10f, f.toLocalY(10f, 370f), 1e-3f)
        // 위 왼쪽 구역 (180°): 화면 오른쪽 아래 = 로컬 왼쪽 위
        f.set(zones[0])
        assertEquals(180f, f.rotation, 0f)
        assertEquals(0f, f.toLocalX(640f, 360f), 1e-3f)
        assertEquals(0f, f.toLocalY(640f, 360f), 1e-3f)
        assertEquals(630f, f.toLocalX(10f, 300f), 1e-3f)
        assertEquals(60f, f.toLocalY(10f, 300f), 1e-3f)
        // 왕복
        for (x in floatArrayOf(5f, 100f, 600f)) for (y in floatArrayOf(3f, 200f, 350f)) {
            val lx = f.toLocalX(x, y); val ly = f.toLocalY(x, y)
            assertEquals(x, f.toScreenX(lx, ly), 1e-3f)
            assertEquals(y, f.toScreenY(lx, ly), 1e-3f)
        }
        assertTrue(f.contains(100f, 100f))
        assertFalse(f.contains(700f, 100f))
        // 90° 는 폭·높이가 바뀐다
        f.set(Zone(0, 0f, 0f, 200f, 100f, 90f))
        assertEquals(100f, f.width, 1e-3f)
        assertEquals(200f, f.height, 1e-3f)
        assertEquals(f.width / 2, f.toLocalX(100f, 50f), 1e-3f)
        val lx = f.toLocalX(30f, 20f); val ly = f.toLocalY(30f, 20f)
        assertEquals(30f, f.toScreenX(lx, ly), 1e-3f)
        assertEquals(20f, f.toScreenY(lx, ly), 1e-3f)
    }

    @Test
    fun `레인 배치 - 위 구역에서 화면 왼쪽을 누르면 그 플레이어의 오른쪽 레인`() {
        val zones = TouchZones.layout(4, 1280f, 720f)
        val f = ZoneFrame().set(zones[0])
        val l = LaneLayout()
        l.layout(f, 4, judgeFrac = 0.8f)
        assertEquals(160f, l.laneWidth, 1e-3f)
        assertEquals(80f, l.laneCenterX(0), 1e-3f)
        assertEquals(288f, l.judgeY, 1e-3f)
        assertEquals(l.judgeY, l.itemY(1f), 1e-3f)
        assertEquals(l.spawnY, l.itemY(0f), 1e-3f)
        // 화면 왼쪽 끝(x=20) → 로컬 x=620 → 레인 3
        assertEquals(3, l.laneAt(f.toLocalX(20f, 100f)))
        assertEquals(0, l.laneAt(f.toLocalX(620f, 100f)))
        // 아래 구역은 그대로
        val g = ZoneFrame().set(zones[2])
        l.layout(g, 4)
        assertEquals(0, l.laneAt(g.toLocalX(20f, 500f)))
        assertEquals(3.875f, l.laneUnits(620f), 1e-3f)
        assertEquals(620f, l.unitsToX(3.875f), 1e-3f)
        assertEquals(0, l.laneAt(-50f))
        assertEquals(3, l.laneAt(5000f))
        // 레인 폭 제한 → 가운데로 모음
        l.layout(g, 2, maxLaneWidth = 100f)
        assertEquals(100f, l.laneWidth, 1e-3f)
        assertEquals(220f, l.laneLeft, 1e-3f)
        assertNotEquals(0f, l.laneLeft)
    }
}
