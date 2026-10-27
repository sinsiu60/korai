package com.siwoo.party.games.spotdiff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

/** 장면 생성: 차이 정확히 5곳·겹치지 않음·보이는 크기, 같은 seed 같은 장면, 정답 판정 */
class SceneGeneratorTest {

    private val seeds = 0L until 600L

    @Test
    fun `차이는 항상 정확히 5곳이고 서로 다른 물체`() {
        for (seed in seeds) {
            val s = SceneGenerator.generate(seed)
            assertEquals("seed $seed", GameConfig.DIFFS_PER_SCENE, s.differences.size)
            assertEquals("seed $seed 서로 다른 물체", 5, s.differences.map { it.original.id }.toSet().size)
            for ((i, d) in s.differences.withIndex()) assertEquals(i, d.index)
            // 오른쪽 그림 = 왼쪽에서 차이만 반영
            val removed = s.differences.count { it.type == DiffType.REMOVED }
            assertEquals(s.left.size - removed, s.right.size)
            var changedSeen = 0
            for (o in s.left) {
                val d = s.differences.firstOrNull { it.original === o }
                val r = s.right.firstOrNull { it.id == o.id }
                if (d == null) {
                    assertTrue("그대로인 물체는 같은 것", r === o)
                } else if (d.type == DiffType.REMOVED) {
                    assertNull("seed $seed 사라진 물체", r)
                } else {
                    assertTrue(r === d.changed)
                    changedSeen++
                }
            }
            assertEquals(5 - removed, changedSeen)
        }
    }

    @Test
    fun `차이는 서로 겹치지 않고 눈에 보일 만큼 크다`() {
        for (seed in seeds) {
            val s = SceneGenerator.generate(seed)
            val ds = s.differences
            for (a in ds.indices) for (b in a + 1 until ds.size) {
                assertFalse("seed $seed 차이 $a·$b 겹침", ds[a].overlaps(ds[b]))
            }
            for (d in ds) {
                val o = d.original
                assertTrue("seed $seed 차이 물체가 큼", SceneGenerator.eligible(o))
                assertTrue(min(o.w, o.h) >= GameConfig.DIFF_MIN_SIDE)
                assertTrue(o.w * o.h >= GameConfig.DIFF_MIN_AREA)
                when (d.type) {
                    DiffType.COLOR -> {
                        val c = d.changed!!
                        assertTrue("seed $seed 색이 확실히 다름", Palette.dist(o.color, c.color) >= GameConfig.MIN_COLOR_DIST)
                        assertEquals(o.w, c.w, 0f)
                        assertEquals(o.cx, c.cx, 0f)
                    }
                    DiffType.REMOVED -> assertNull(d.changed)
                    DiffType.SIZE -> {
                        val c = d.changed!!
                        val f = c.w / o.w
                        assertTrue("seed $seed 크기 30% 넘게 ($f)", abs(f - 1f) >= 0.3f)
                        assertEquals(f, c.h / o.h, 0.001f)
                        assertEquals(o.color, c.color)
                        // 줄여도 너무 작지 않음
                        assertTrue(min(c.w, c.h) >= 11.9f)
                        assertTrue(c.inside(GameConfig.SCENE_W, GameConfig.SCENE_H, 0f))
                        if (!o.kind.sky) assertEquals("땅 물체는 바닥 그대로", o.bottom, c.bottom, 0.01f)
                    }
                }
            }
            // 오른쪽 그림에서도 물체끼리 겹치지 않음 (키운 물체 포함)
            val r = s.right
            for (a in r.indices) for (b in a + 1 until r.size) assertFalse("seed $seed 오른쪽 겹침", r[a].overlaps(r[b], 0f))
        }
    }

    @Test
    fun `장면은 물체 12~20개를 겹치지 않게 그림 안에 놓는다`() {
        val kinds = HashSet<Kind>()
        val types = HashSet<DiffType>()
        for (seed in seeds) {
            val s = SceneGenerator.generate(seed)
            assertTrue("seed $seed 개수 ${s.left.size}", s.left.size in GameConfig.MIN_OBJECTS..GameConfig.MAX_OBJECTS)
            for (o in s.left) assertTrue(o.inside(GameConfig.SCENE_W, GameConfig.SCENE_H, 0f))
            for (a in s.left.indices) for (b in a + 1 until s.left.size) assertFalse(s.left[a].overlaps(s.left[b], 0f))
            assertTrue(s.left.any { it.kind == Kind.HOUSE })
            assertTrue(s.theme in 0 until Scene.THEMES)
            // 장면마다 세 종류가 다 나온다
            assertEquals("seed $seed 차이 종류", 3, s.differences.map { it.type }.toSet().size)
            s.left.mapTo(kinds) { it.kind }
            s.differences.mapTo(types) { it.type }
        }
        assertEquals("모든 물체 종류가 나옴", Kind.values().toSet(), kinds)
        assertEquals(DiffType.values().toSet(), types)
    }

    @Test
    fun `같은 seed 는 같은 장면`() {
        val a = SceneGenerator.generate(42)
        val b = SceneGenerator.generate(42)
        assertEquals(a.left.size, b.left.size)
        for (i in a.left.indices) {
            assertEquals(a.left[i].kind, b.left[i].kind)
            assertEquals(a.left[i].cx, b.left[i].cx, 0f)
            assertEquals(a.left[i].color, b.left[i].color)
        }
        for (i in 0 until 5) {
            assertEquals(a.differences[i].type, b.differences[i].type)
            assertEquals(a.differences[i].cx, b.differences[i].cx, 0f)
        }
        val c = SceneGenerator.generate(43)
        assertFalse(a.left.size == c.left.size && a.left.indices.all { a.left[it].cx == c.left[it].cx })
    }

    @Test
    fun `물체마다 처음 색에서 확실히 다른 바꿀 색이 있다`() {
        for (k in Kind.values()) for (c in k.colors) {
            assertTrue("$k ${Integer.toHexString(c)}", k.farColors(c).isNotEmpty())
        }
        assertEquals(0.0, Palette.dist(Palette.RED, Palette.RED), 0.0)
        assertTrue(Palette.dist(Palette.WHITE, Palette.BLACK) > 300)
    }

    @Test
    fun `반경 안을 누르면 그 차이 · 이미 찾은 차이는 다시 안 됨`() {
        for (seed in 0L until 100L) {
            val s = SceneGenerator.generate(seed)
            val found = BooleanArray(5)
            for (d in s.differences) {
                assertEquals("가운데", d.index, s.findAt(d.cx, d.cy, found))
                // 상자 모서리 쪽도 반경 안
                val r = d.hitRadius * 0.95f
                val hit = s.findAt(d.cx + r, d.cy, found)
                assertTrue("seed $seed 반경 안", hit >= 0)
                assertTrue(d.hitRadius >= GameConfig.MIN_HIT_RADIUS + GameConfig.HIT_SLOP)
                assertTrue(d.hit(d.cx, d.cy - r))
                assertFalse(d.hit(d.cx + d.hitRadius + 1f, d.cy))
            }
            // 찾은 차이는 빠짐
            val d0 = s.differences[0]
            found[0] = true
            assertTrue(s.findAt(d0.cx, d0.cy, found) != 0)
            for (i in 0 until 5) found[i] = true
            for (d in s.differences) assertEquals(-1, s.findAt(d.cx, d.cy, found))
        }
    }

    @Test
    fun `차이가 없는 곳은 -1`() {
        var checked = 0
        for (seed in 0L until 50L) {
            val s = SceneGenerator.generate(seed)
            val found = BooleanArray(5)
            // 판정 반경 밖인 칸들을 찍어 본다
            var y = 5f
            while (y < GameConfig.SCENE_H) {
                var x = 5f
                while (x < GameConfig.SCENE_W) {
                    val inAny = s.differences.any { it.hit(x, y) }
                    assertEquals(inAny, s.findAt(x, y, found) >= 0)
                    if (!inAny) checked++
                    x += 20f
                }
                y += 20f
            }
        }
        assertTrue(checked > 1000)
        assertNotNull(SceneGenerator.generate(0))
    }

    @Test
    fun `판정 반경이 겹치면 가까운 차이`() {
        val k = Kind.CAR
        val a = SceneObject(0, k, 100f, 300f, 40f, 30f, Palette.RED, Palette.BLACK, false)
        val b = SceneObject(1, k, 150f, 300f, 40f, 30f, Palette.RED, Palette.BLACK, false)
        val da = Difference(0, DiffType.REMOVED, a, null)
        val db = Difference(1, DiffType.COLOR, b, b.recolored(Palette.BLUE))
        val filler = (2 until 5).map { Difference(it, DiffType.REMOVED, SceneObject(it, k, 50f + it * 60f, 80f, 30f, 30f, Palette.RED, Palette.BLACK, false), null) }
        val s = Scene(1, 0, listOf(a, b), listOf(b), listOf(da, db) + filler)
        val found = BooleanArray(5)
        assertEquals(0, s.findAt(124f, 300f, found))
        assertEquals(1, s.findAt(130f, 300f, found))
        found[0] = true
        assertEquals("찾은 건 빼고 다음 후보", 1, s.findAt(124f, 300f, found))
    }
}
