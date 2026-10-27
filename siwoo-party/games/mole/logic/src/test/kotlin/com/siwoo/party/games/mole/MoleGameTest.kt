package com.siwoo.party.games.mole

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MoleGameTest {

    private fun game(n: Int, seed: Int = 1) = MoleGame(n, Random(seed)).also { it.start(1000) }

    /** 경기 시각 t(시작 기준)의 게임 시계 값 */
    private fun at(t: Long) = 1000 + t

    private fun first(g: MoleGame, kind: Kind) = g.spawns.first { it.kind == kind }

    @Test
    fun `일반 +1, 황금 +3, 폭탄 -2`() {
        val g = game(2)
        val n = first(g, Kind.NORMAL)
        val gold = first(g, Kind.GOLD)
        val bomb = first(g, Kind.BOMB)
        assertSame(Hit.NORMAL, g.hit(0, n.hole, at(n.atMs)))
        assertEquals(1, g.score[0])
        assertSame(Hit.GOLD, g.hit(0, gold.hole, at(gold.atMs + 10)))
        assertEquals(4, g.score[0])
        assertSame(Hit.BOMB, g.hit(0, bomb.hole, at(bomb.endMs - 1)))
        assertEquals(2, g.score[0])
        assertEquals(2, g.caught[0])
        assertEquals(1, g.bombs[0])
        assertEquals(0, g.score[1])
    }

    @Test
    fun `폭탄만 치면 점수가 음수가 될 수 있다`() {
        val g = game(2, seed = 3)
        for (b in g.spawns.filter { it.kind == Kind.BOMB }) g.hit(1, b.hole, at(b.atMs))
        val nb = g.spawns.count { it.kind == Kind.BOMB }
        assertTrue(nb >= GameConfig.MIN_BOMB)
        assertEquals(nb * GameConfig.POINTS_BOMB, g.score[1])
        assertArrayEquals(intArrayOf(1, 2), g.finalRanks())
    }

    @Test
    fun `한 두더지는 구역마다 한 번만`() {
        val g = game(3)
        val s = g.spawns[0]
        assertSame(Hit.NORMAL, g.hit(0, s.hole, at(s.atMs + 5)))
        assertSame(Hit.ALREADY, g.hit(0, s.hole, at(s.atMs + 50)))
        assertSame(Hit.ALREADY, g.hit(0, s.hole, at(s.atMs + 100)))
        assertEquals(1, g.score[0])
        // 다른 구역은 같은 두더지를 따로 잡을 수 있다
        assertSame(Hit.NORMAL, g.hit(1, s.hole, at(s.atMs + 100)))
        assertSame(Hit.NORMAL, g.hit(2, s.hole, at(s.endMs - 1)))
        assertEquals(1, g.score[1])
        assertEquals(1, g.score[2])
    }

    @Test
    fun `빈 구멍이나 들어간 뒤에 치면 빗나감, 점수 변화 없음`() {
        val g = game(2)
        val s = g.spawns[0]
        assertSame("나오기 전", Hit.MISS, g.hit(0, s.hole, at(s.atMs - 1)))
        assertSame("들어간 뒤", Hit.MISS, g.hit(0, s.hole, at(s.endMs)))
        val other = (s.hole + 1) % GameConfig.HOLES
        if (g.activeSpawn(other, at(s.atMs)) == null) assertSame(Hit.MISS, g.hit(0, other, at(s.atMs)))
        assertEquals(0, g.score[0])
        assertTrue(g.misses[0] >= 2)
    }

    @Test
    fun `시작 전과 끝난 뒤, 잘못된 구멍은 무시`() {
        val g = MoleGame(2, Random(1))
        assertSame(Hit.IGNORED, g.hit(0, 0, 0))
        g.start(0)
        assertSame(Hit.IGNORED, g.hit(0, -1, 100))
        assertSame(Hit.IGNORED, g.hit(0, GameConfig.HOLES, 100))
        assertSame(Hit.IGNORED, g.hit(5, 0, 100))
        assertTrue(g.isOver(GameConfig.GAME_MS))
        assertSame(Hit.IGNORED, g.hit(0, g.spawns.last().hole, GameConfig.GAME_MS))
        assertEquals(0, g.misses[0])
    }

    @Test
    fun `모든 구역이 같은 순서표, 같은 시각에 같은 결과`() {
        val g = game(4, seed = 9)
        for (s in g.spawns) {
            val t = at(s.atMs + s.upMs / 2)
            val results = (0 until 4).map { g.hit(it, s.hole, t) }
            assertEquals("두더지 ${s.id}", 1, results.distinct().size)
        }
        assertEquals(1, g.score.distinct().size)
        assertArrayEquals(intArrayOf(1, 1, 1, 1), g.finalRanks())
        // 같은 씨앗이면 같은 순서표
        val a = MolePattern.generate(Random(5))
        val b = MolePattern.generate(Random(5))
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].atMs, b[i].atMs)
            assertEquals(a[i].hole, b[i].hole)
            assertSame(a[i].kind, b[i].kind)
            assertEquals(a[i].upMs, b[i].upMs)
        }
    }

    @Test
    fun `등장 시간과 간격이 점점 짧아진다`() {
        for (seed in 1..20) {
            val list = MolePattern.generate(Random(seed))
            for (i in 1 until list.size) {
                assertTrue("시간 순서", list[i].atMs > list[i - 1].atMs)
                assertTrue("올라와 있는 시간은 줄기만", list[i].upMs <= list[i - 1].upMs)
            }
            assertEquals(GameConfig.UP_START_MS - 0, MolePattern.upMsAt(0.0))
            assertTrue(list.first().upMs > list.last().upMs + 400)
            val gapsFirst = (1..10).map { list[it].atMs - list[it - 1].atMs }.average()
            val n = list.size
            val gapsLast = (n - 10 until n).map { list[it].atMs - list[it - 1].atMs }.average()
            assertTrue("간격도 짧아짐 $gapsFirst > $gapsLast", gapsFirst > gapsLast)
        }
    }

    @Test
    fun `순서표 규칙 - 시간 안에 끝, 같은 구멍 안 겹침, 종류 최소 개수`() {
        for (seed in 1..30) {
            val list = MolePattern.generate(Random(seed))
            assertTrue(list.size in 35..90)
            assertSame("첫 두더지는 일반", Kind.NORMAL, list[0].kind)
            assertTrue(list.all { it.endMs <= GameConfig.GAME_MS && it.hole in 0 until GameConfig.HOLES })
            assertTrue(list.count { it.kind == Kind.GOLD } >= GameConfig.MIN_GOLD)
            assertTrue(list.count { it.kind == Kind.BOMB } >= GameConfig.MIN_BOMB)
            for (h in 0 until GameConfig.HOLES) {
                val inHole = list.filter { it.hole == h }
                for (i in 1 until inHole.size) assertTrue(inHole[i].atMs >= inHole[i - 1].endMs + GameConfig.HOLE_REST_MS)
            }
            list.forEachIndexed { i, s -> assertEquals(i, s.id) }
        }
    }

    @Test
    fun `activeSpawn 은 올라와 있는 동안만`() {
        val g = game(2, seed = 4)
        val s = g.spawns[3]
        assertSame(s, g.activeSpawn(s.hole, at(s.atMs)))
        assertSame(s, g.activeSpawn(s.hole, at(s.endMs - 1)))
        val after = g.activeSpawn(s.hole, at(s.endMs))
        assertTrue(after == null || after.id != s.id)
        assertNull(MoleGame(2, Random(1)).activeSpawn(0, 5000))
    }

    @Test
    fun `점수 순 순위, 동점은 같은 순위`() {
        val g = game(4, seed = 7)
        val normals = g.spawns.filter { it.kind == Kind.NORMAL }
        val golds = g.spawns.filter { it.kind == Kind.GOLD }
        // 0번: 일반 3개 = 3점, 1번: 황금 1개 = 3점, 2번: 일반 5개 = 5점, 3번: 0점
        for (s in normals.take(3)) g.hit(0, s.hole, at(s.atMs))
        g.hit(1, golds[0].hole, at(golds[0].atMs))
        for (s in normals.take(5)) g.hit(2, s.hole, at(s.atMs))
        assertArrayEquals(intArrayOf(3, 3, 5, 0), g.score)
        assertArrayEquals(intArrayOf(2, 2, 1, 4), g.finalRanks())
    }

    @Test
    fun `시간과 남은 시간`() {
        val g = game(2)
        assertEquals(GameConfig.GAME_MS, g.remainingMs(1000))
        assertEquals(GameConfig.GAME_MS - 2500, g.remainingMs(3500))
        assertTrue(!g.isOver(at(GameConfig.GAME_MS - 1)))
        assertTrue(g.isOver(at(GameConfig.GAME_MS)))
        assertEquals(0, g.remainingMs(at(GameConfig.GAME_MS + 500)))
    }
}
