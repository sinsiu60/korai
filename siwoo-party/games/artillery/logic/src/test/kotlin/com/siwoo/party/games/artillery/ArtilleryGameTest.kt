package com.siwoo.party.games.artillery

import com.siwoo.party.common.physics.PhysicsConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class ArtilleryGameTest {

    // ── 도우미 ──

    private fun flatGame(xs: FloatArray, h: Float = 2f, first: Int = 0, wind: Int = 0): ArtilleryGame =
        ArtilleryGame(Terrain.flat(h), xs, first, Random(1), wind)

    /** shooter 가 target 을 직격하는 (각도, 힘) 찾기 — 지금 바람으로 */
    private fun findHit(g: ArtilleryGame, target: Int, from: Int = g.current): IntArray? {
        val imp = Impact()
        val right = g.cannons[target].x > g.cannons[from].x
        for (a0 in 25..85) {
            val a = if (right) a0 else 180 - a0
            for (p in GameConfig.MIN_POWER_PCT..100) {
                g.simulate(a, p, imp, from)
                if (imp.kind == ImpactKind.CANNON && imp.cannon == target) return intArrayOf(a, p)
            }
        }
        return null
    }

    private fun hit(g: ArtilleryGame, target: Int) {
        val s = findHit(g, target)
        assertNotNull("직격 해 찾기 (${g.current} → $target)", s)
        assertSame(FireResult.FIRED, g.fire(s!![0], s[1]))
        g.advanceUntilLanded()
        assertSame(ImpactKind.CANNON, g.report.impact.kind)
        assertEquals(target, g.report.impact.cannon)
    }

    /** 좌우 밖으로 날려 보내기 (빗나감) */
    private fun miss(g: ArtilleryGame) {
        val x = g.cannons[g.current].x
        val a = when {
            x < 30f -> 170
            x > 70f -> 10
            else -> 55
        }
        assertSame(FireResult.FIRED, g.fire(a, 100))
        g.advanceUntilLanded()
        assertSame(ImpactKind.OUT, g.report.impact.kind)
    }

    // ── 포탄 궤적 ──

    @Test
    fun `바람이 없으면 꼭대기를 중심으로 대칭인 포물선`() {
        val t = Terrain.flat(1f)
        val s = Shell()
        // vy = g × 1초 → 정확히 60단계 뒤 꼭대기
        s.launch(20f, 10f, 12f, GameConfig.GRAVITY, 0f)
        val xs = FloatArray(121)
        val ys = FloatArray(121)
        xs[0] = s.x
        ys[0] = s.y
        val imp = Impact()
        for (k in 1..120) {
            assertFalse("아직 안 떨어짐", Ballistics.step(s, t, emptyList(), imp))
            xs[k] = s.x
            ys[k] = s.y
        }
        // 꼭대기 높이 = y0 + vy²/2g
        assertEquals(10f + GameConfig.GRAVITY / 2f, ys[60], 1e-3f)
        for (k in 1..60) {
            assertEquals("높이 대칭 $k", ys[60 - k], ys[60 + k], 2e-3f)
            assertEquals("가로는 등속 $k", xs[60] - xs[60 - k], xs[60 + k] - xs[60], 2e-3f)
        }
        assertEquals("두 번째 꼭대기 반대편 = 출발 높이", 10f, ys[120], 3e-3f)
        assertEquals(20f + 12f * 2f, xs[120], 3e-3f)
    }

    @Test
    fun `평지 사거리는 해석식과 같다`() {
        val g = flatGame(floatArrayOf(10f, 95f), h = 2f)
        val c = g.cannons[0]
        val imp = Impact()
        for ((a, p) in listOf(30 to 60, 45 to 60, 60 to 70, 75 to 80, 45 to 40)) {
            g.simulate(a, p, imp)
            assertSame("$a° $p%", ImpactKind.TERRAIN, imp.kind)
            val rad = Math.toRadians(a.toDouble())
            val v = GameConfig.MAX_SPEED * p / 100.0
            val vx = cos(rad) * v
            val vy = sin(rad) * v
            val x0 = c.muzzleX(a).toDouble()
            val y0 = c.muzzleY(a).toDouble()
            val gr = GameConfig.GRAVITY.toDouble()
            val tLand = (vy + sqrt(vy * vy + 2 * gr * (y0 - 2.0))) / gr
            val expected = x0 + vx * tLand
            assertEquals("$a° $p% 착탄 x", expected.toFloat(), imp.x, 0.05f)
            assertEquals(2f, imp.y, 0.01f)
        }
    }

    @Test
    fun `바람은 착탄 지점을 바람 쪽으로 민다`() {
        val g = flatGame(floatArrayOf(10f, 95f))
        val imp = Impact()
        g.simulate(50, 60, imp, windLevel = 0)
        val calm = imp.x
        g.simulate(50, 60, imp, windLevel = 3)
        val right = imp.x
        g.simulate(50, 60, imp, windLevel = -3)
        val left = imp.x
        assertTrue("오른쪽 바람 → 더 멀리 ($calm → $right)", right > calm + 1f)
        assertTrue("왼쪽 바람 → 덜 감 ($calm → $left)", left < calm - 1f)
        // 강한 바람일수록 더 많이
        g.simulate(50, 60, imp, windLevel = GameConfig.WIND_LEVELS)
        assertTrue(imp.x > right)
        // 바람 가속도 = 단계 × WIND_PER_LEVEL
        assertEquals(3 * GameConfig.WIND_PER_LEVEL, Ballistics.windAccel(3), 1e-6f)
    }

    @Test
    fun `언덕에 막히면 그 언덕 표면에서 터진다`() {
        // x 40~46 에 높이 30 의 벽
        val hs = FloatArray(GameConfig.SAMPLES) { i ->
            val x = i * GameConfig.WORLD_W / (GameConfig.SAMPLES - 1)
            if (x in 40f..46f) 30f else 2f
        }
        val g = ArtilleryGame(Terrain(hs), floatArrayOf(10f, 90f), 0, Random(1), 0)
        val imp = Impact()
        g.simulate(30, 80, imp)
        assertSame(ImpactKind.TERRAIN, imp.kind)
        assertTrue("벽 앞에서 멈춤 (${imp.x})", imp.x in 39f..40.6f)
        assertTrue(imp.y < 30f)
        assertEquals("땅 표면 위치", g.terrain.heightAt(imp.x), imp.y, 0.05f)
        // 높이 쏘면 넘어간다
        g.simulate(70, 90, imp)
        assertTrue("넘어감 (${imp.x})", imp.x > 46f || imp.kind == ImpactKind.OUT)
        // 선분-땅 교차: 이미 땅 속이면 0, 안 닿으면 −1
        assertEquals(0f, Ballistics.segmentGround(20f, 1f, 21f, 3f, g.terrain), 0f)
        assertEquals(-1f, Ballistics.segmentGround(20f, 5f, 21f, 4f, g.terrain), 0f)
    }

    @Test
    fun `좌우 밖으로 나가면 빗나감 (피해 없음)`() {
        val g = flatGame(floatArrayOf(10f, 90f))
        val imp = Impact()
        g.simulate(160, 80, imp)
        assertSame(ImpactKind.OUT, imp.kind)
        assertEquals(0f, imp.x, 0f)
        g.simulate(20, 100, imp, from = 1)
        assertSame(ImpactKind.OUT, imp.kind)
        assertEquals(GameConfig.WORLD_W, imp.x, 0f)
        // 실제로 쏘면 아무도 안 다침
        assertSame(FireResult.FIRED, g.fire(160, 80))
        assertSame(Phase.FLYING, g.phase)
        g.advanceUntilLanded()
        assertSame(Phase.RESOLVED, g.phase)
        assertFalse(g.report.anyDamage)
        assertEquals(100, g.cannons[0].hp)
        assertEquals(100, g.cannons[1].hp)
        assertFalse(ImpactKind.OUT.explodes)
    }

    @Test
    fun `고정 단계 - 프레임을 어떻게 쪼개도 같은 결과`() {
        val a = flatGame(floatArrayOf(10f, 90f))
        val b = flatGame(floatArrayOf(10f, 90f))
        a.fire(55, 70)
        b.fire(55, 70)
        while (a.phase == Phase.FLYING) a.advance(16)
        val r = Random(5)
        while (b.phase == Phase.FLYING) b.advance(1L + r.nextInt(40))
        assertEquals(a.report.impact.x, b.report.impact.x, 0f)
        assertEquals(a.report.impact.steps, b.report.impact.steps)
        assertTrue("자취가 남음", a.shell.trailCount > 3)
        // 날아가는 시간이 단계 수와 맞다
        assertTrue(a.report.impact.steps > PhysicsConfig.STEPS_PER_SEC)
    }

    @Test
    fun `약한 힘은 안 쏘고 날아가는 중엔 못 쏜다`() {
        val g = flatGame(floatArrayOf(10f, 90f))
        assertSame(FireResult.TOO_WEAK, g.fire(45, GameConfig.MIN_POWER_PCT - 1))
        assertEquals(0, g.shots)
        assertSame(FireResult.FIRED, g.fire(45, 50))
        assertSame(FireResult.NOT_NOW, g.fire(45, 50))
        assertEquals(1, g.shots)
        assertEquals(45, g.cannons[0].lastAngle)
        assertEquals(50, g.cannons[0].lastPower)
        assertFalse("판정 전엔 다음 차례 없음", g.nextTurn())
    }

    // ── 피해 계산 ──

    @Test
    fun `피해 - 직격 최대, 거리에 비례해 줄고, 반경 밖은 0`() {
        assertEquals(GameConfig.MAX_DAMAGE, ArtilleryGame.blastDamage(0f))
        assertEquals(38, ArtilleryGame.blastDamage(2f)) // 50 × 0.75 = 37.5 → 38
        assertEquals(25, ArtilleryGame.blastDamage(4f))
        assertEquals(13, ArtilleryGame.blastDamage(6f)) // 12.5 → 13
        assertEquals(0, ArtilleryGame.blastDamage(GameConfig.BLAST_R))
        assertEquals(0, ArtilleryGame.blastDamage(20f))
        // 거리가 늘수록 줄어듦 (단조)
        var prev = Int.MAX_VALUE
        var d = 0f
        while (d <= GameConfig.BLAST_R) {
            val v = ArtilleryGame.blastDamage(d)
            assertTrue(v <= prev)
            prev = v
            d += 0.25f
        }
        // 대포 위치 기준
        val g = flatGame(floatArrayOf(20f, 80f))
        val out = IntArray(2)
        val c = g.cannons[0]
        g.damageAt(c.x + GameConfig.CANNON_R + 4f, c.y, -1, out)
        assertArrayEquals(intArrayOf(25, 0), out)
        g.damageAt(c.x + GameConfig.CANNON_R + 20f, c.y, -1, out)
        assertArrayEquals(intArrayOf(0, 0), out)
        g.damageAt(c.x + 5f, c.y + 5f, 1, out)
        assertEquals("직격은 거리와 상관없이 최대", GameConfig.MAX_DAMAGE, out[1])
    }

    @Test
    fun `직격하면 최대 피해`() {
        val g = flatGame(floatArrayOf(15f, 70f))
        hit(g, 1)
        assertEquals(GameConfig.MAX_DAMAGE, g.report.damage[1])
        assertEquals(GameConfig.START_HP - GameConfig.MAX_DAMAGE, g.cannons[1].hp)
        assertEquals(0, g.report.damage[0])
        assertEquals(100, g.report.hpBefore[1])
    }

    @Test
    fun `가까이 있는 대포는 한꺼번에 맞는다`() {
        // 30, 36 두 대포 사이 땅에 떨어뜨림 (발사는 90 에서)
        val g = flatGame(floatArrayOf(30f, 36f, 90f), first = 2)
        val imp = Impact()
        var found: IntArray? = null
        loop@ for (a in 100..140) for (p in 20..100) {
            g.simulate(a, p, imp)
            if (imp.kind == ImpactKind.TERRAIN && imp.x in 32.6f..33.4f) {
                found = intArrayOf(a, p)
                break@loop
            }
        }
        assertNotNull(found)
        g.fire(found!![0], found[1])
        g.advanceUntilLanded()
        val r = g.report
        assertTrue("둘 다 맞음 ${r.damage.toList()}", r.damage[0] > 0 && r.damage[1] > 0)
        assertEquals(0, r.damage[2])
        for (i in 0..1) {
            val expected = ArtilleryGame.blastDamage(ArtilleryGame.distanceToBody(g.cannons[i], r.impact.x, r.impact.y))
            assertEquals(expected, r.damage[i])
            assertEquals(100 - expected, g.cannons[i].hp)
        }
    }

    @Test
    fun `자기 대포도 맞는다 (바로 위로 쏘면 자폭)`() {
        val g = flatGame(floatArrayOf(20f, 80f))
        g.fire(90, 50)
        g.advanceUntilLanded()
        assertSame(ImpactKind.CANNON, g.report.impact.kind)
        assertEquals(0, g.report.impact.cannon)
        assertEquals(GameConfig.MAX_DAMAGE, g.report.damage[0])
        assertEquals(50, g.cannons[0].hp)
        // 발밑 근처 땅에 쏘면 조금 다침
        g.nextTurn()
        g.fire(180, 15) // 1번이 왼쪽으로 약하게 → 자기 근처
        g.advanceUntilLanded()
        assertTrue("자기 근처 폭발 → 자기 피해 ${g.report.damage.toList()}", g.report.damage[1] > 0)
    }

    @Test
    fun `체력 0 이면 탈락하고 차례에서 빠진다`() {
        val g = flatGame(floatArrayOf(10f, 50f, 90f))
        g.setHp(1, 30)
        hit(g, 1)
        assertEquals(0, g.cannons[1].hp)
        assertFalse(g.cannons[1].alive)
        assertTrue(g.report.eliminated[1])
        assertEquals(1, g.report.eliminatedCount)
        assertEquals("실제로 깎인 양만", 30, g.report.damage[1])
        assertEquals(1, g.cannons[1].eliminatedAt)
        assertFalse(g.over)
        assertTrue(g.nextTurn())
        assertEquals("1번 건너뜀", 2, g.current)
        miss(g)
        g.nextTurn()
        assertEquals(0, g.current)
        miss(g)
        g.nextTurn()
        assertEquals(2, g.current)
        // 잔해는 포탄을 막지 않는다
        val imp = Impact()
        g.simulate(170, 25, imp)
        assertTrue(imp.cannon != 1)
    }

    @Test
    fun `순위 - 늦게 탈락할수록 높고 최후 1인 승리`() {
        val g = flatGame(floatArrayOf(10f, 37f, 63f, 90f))
        // 0 → 3 탈락, 1 → 0 탈락 (반복 직격)
        for (i in 0..3) g.setHp(i, 40)
        hit(g, 3)
        assertTrue(g.report.eliminated[3])
        g.nextTurn()
        assertEquals(1, g.current)
        hit(g, 0)
        assertTrue(g.report.eliminated[0])
        g.nextTurn()
        assertEquals(2, g.current)
        hit(g, 1)
        assertTrue(g.report.eliminated[1])
        assertTrue(g.over)
        assertSame(EndReason.LAST_STANDING, g.endReason)
        assertFalse(g.nextTurn())
        assertArrayEquals(intArrayOf(3, 2, 1, 4), g.ranks())
    }

    @Test
    fun `같은 발사에 함께 탈락하면 같은 순위`() {
        val g = flatGame(floatArrayOf(30f, 36f, 60f, 90f), first = 3)
        g.setHp(0, 5)
        g.setHp(1, 5)
        val imp = Impact()
        var found: IntArray? = null
        loop@ for (a in 100..150) for (p in 20..100) {
            g.simulate(a, p, imp)
            if (imp.kind == ImpactKind.TERRAIN && imp.x in 32.6f..33.4f) {
                found = intArrayOf(a, p)
                break@loop
            }
        }
        assertNotNull(found)
        g.fire(found!![0], found[1])
        g.advanceUntilLanded()
        assertEquals(2, g.report.eliminatedCount)
        assertFalse(g.over)
        g.nextTurn()
        assertEquals("탈락한 0·1 건너뛰고 2", 2, g.current)
        g.setHp(3, 10)
        hit(g, 3)
        assertTrue(g.over)
        assertArrayEquals(intArrayOf(3, 3, 1, 2), g.ranks())
    }

    @Test
    fun `마지막 둘이 함께 쓰러지면 공동 1등`() {
        val g = flatGame(floatArrayOf(40f, 45f), first = 0)
        g.setHp(0, 5)
        g.setHp(1, 5)
        hit(g, 1)
        assertTrue("자기도 폭발 반경 안", g.report.damage[0] > 0)
        assertTrue(g.over)
        assertSame(EndReason.ALL_DOWN, g.endReason)
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    @Test
    fun `최대 발사 수가 되면 남은 사람은 체력 순`() {
        val g = flatGame(floatArrayOf(10f, 50f, 90f))
        g.setHp(0, 60)
        g.setHp(1, 90)
        g.setHp(2, 60)
        assertEquals(3 * GameConfig.MAX_TURNS_PER_PLAYER, g.maxShots)
        var n = 0
        while (!g.over) {
            miss(g)
            n++
            if (!g.over) assertTrue(g.nextTurn())
        }
        assertEquals(g.maxShots, n)
        assertSame(EndReason.MAX_TURNS, g.endReason)
        assertArrayEquals(intArrayOf(2, 1, 2), g.ranks())
    }

    @Test
    fun `최대 발사 수 - 탈락한 사람은 남은 사람 아래`() {
        val g = flatGame(floatArrayOf(10f, 50f, 90f))
        g.setHp(2, 10)
        hit(g, 2)
        g.nextTurn()
        while (!g.over) {
            miss(g)
            if (!g.over) g.nextTurn()
        }
        assertSame(EndReason.MAX_TURNS, g.endReason)
        assertEquals(g.maxShots, g.shots)
        // 0번은 100, 1번은 100 → 공동 1, 2번은 탈락 3등
        assertArrayEquals(intArrayOf(1, 1, 3), g.ranks())
    }

    @Test
    fun `바람은 차례마다 범위 안에서 바뀐다`() {
        val g = ArtilleryGame.create(2, 0, 7, Random(3))
        val seen = HashSet<Int>()
        repeat(40) {
            assertTrue(g.wind in -GameConfig.WIND_LEVELS..GameConfig.WIND_LEVELS)
            seen.add(g.wind)
            // 아래로 쏴서 바로 판정
            g.fire(if (g.current == 0) 170 else 10, 100)
            g.advanceUntilLanded()
            if (g.over) return@repeat
            g.nextTurn()
        }
        assertTrue("여러 바람 (${seen.size})", seen.size >= 5)
    }

    // ── 지형 ──

    @Test
    fun `지형은 시드로 결정되고 대포는 평평한 땅 위에`() {
        for (n in 2..4) {
            val xs = Terrain.cannonXs(n)
            assertEquals(GameConfig.CANNON_MARGIN, xs[0], 1e-4f)
            assertEquals(GameConfig.WORLD_W - GameConfig.CANNON_MARGIN, xs[n - 1], 1e-4f)
            for (seed in listOf(1, 42, 777)) {
                val a = Terrain.generate(seed, xs)
                val b = Terrain.generate(seed, xs)
                assertArrayEquals("같은 시드 → 같은 땅", a.heights, b.heights, 0f)
                for (h in a.heights) assertTrue(h in GameConfig.MIN_H..GameConfig.MAX_H)
                val g = ArtilleryGame(a, xs, 0, Random(seed))
                for (c in g.cannons) {
                    assertEquals("땅 위에 놓임", a.heightAt(c.x), c.groundY, 1e-4f)
                    assertEquals(c.groundY + GameConfig.CANNON_LIFT, c.y, 1e-4f)
                    assertTrue("평평 (${a.bumpiness(c.x, GameConfig.PAD_HALF * 0.9f)})", a.bumpiness(c.x, GameConfig.PAD_HALF * 0.9f) < 0.01f)
                    assertTrue("포신 근처가 땅 위", c.y > a.heightAt(c.x - GameConfig.CANNON_R) && c.y > a.heightAt(c.x + GameConfig.CANNON_R))
                }
            }
        }
        val xs = Terrain.cannonXs(2)
        assertFalse("다른 시드 → 다른 땅", Terrain.generate(1, xs).heights.contentEquals(Terrain.generate(2, xs).heights))
        // 언덕이 있다 (평지가 아님)
        val t = Terrain.generate(5, xs)
        assertTrue(t.maxHeight - t.heights.min() > 3f)
    }

    @Test
    fun `높이 함수는 표본 사이를 직선으로 잇는다`() {
        val t = Terrain(floatArrayOf(0f, 10f, 4f), 20f)
        assertEquals(5f, t.heightAt(5f), 1e-5f)
        assertEquals(7f, t.heightAt(15f), 1e-5f)
        assertEquals(0f, t.heightAt(-3f), 0f)
        assertEquals(4f, t.heightAt(25f), 0f)
    }

    @Test
    fun `create 는 같은 시드면 같은 전장`() {
        val a = ArtilleryGame.create(3, 1, 99, Random(1))
        val b = ArtilleryGame.create(3, 1, 99, Random(1))
        assertArrayEquals(a.terrain.heights, b.terrain.heights, 0f)
        assertEquals(a.wind, b.wind)
        assertEquals(1, a.current)
        assertEquals(3, a.cannons.size)
    }
}
