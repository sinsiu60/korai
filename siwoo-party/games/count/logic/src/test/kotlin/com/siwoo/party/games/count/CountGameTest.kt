package com.siwoo.party.games.count

import com.siwoo.party.common.TouchZones
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class CountGameTest {

    // ── 라운드 일정 ──

    @Test
    fun `개수는 5~20 안에서 라운드마다 늘어난다`() {
        val gen = RoundGen(Random(0))
        assertEquals(GameConfig.COUNT_MIN, gen.baseCount(1))
        assertEquals(GameConfig.COUNT_MAX, gen.baseCount(GameConfig.ROUNDS))
        for (r in 2..GameConfig.ROUNDS) assertTrue("기준 개수는 줄지 않음 $r", gen.baseCount(r) >= gen.baseCount(r - 1))
        repeat(300) { seed ->
            val set = RoundGen(Random(seed)).makeSet()
            assertEquals(GameConfig.ROUNDS, set.size)
            for ((i, r) in set.withIndex()) {
                assertEquals(i + 1, r.number)
                assertTrue("범위 ${r.count}", r.count in GameConfig.COUNT_MIN..GameConfig.COUNT_MAX)
                assertTrue("기준에서 ±${GameConfig.COUNT_JITTER}", abs(r.count - gen.baseCount(r.number)) <= GameConfig.COUNT_JITTER)
                if (i > 0) assertTrue("크게 줄지 않음", r.count >= set[i - 1].count - 1)
            }
            val first = set.take(4).sumOf { it.count }
            val last = set.takeLast(4).sumOf { it.count }
            assertTrue("뒤로 갈수록 많아짐", last > first + 20)
            assertTrue(set.last().count >= 19 && set.first().count <= 6)
        }
    }

    @Test
    fun `보여 주는 시간은 900ms 에서 400ms 로 줄어든다`() {
        val gen = RoundGen(Random(1))
        assertEquals(GameConfig.SHOW_FIRST_MS, gen.showMs(1))
        assertEquals(GameConfig.SHOW_LAST_MS, gen.showMs(GameConfig.ROUNDS))
        assertEquals(900L, GameConfig.SHOW_FIRST_MS)
        assertEquals(400L, GameConfig.SHOW_LAST_MS)
        for (r in 2..GameConfig.ROUNDS) assertTrue("라운드 $r 더 짧음", gen.showMs(r) < gen.showMs(r - 1))
        val set = gen.makeSet()
        for (r in set) assertEquals(gen.showMs(r.number), r.showMs)
    }

    // ── 보기 ──

    @Test
    fun `보기는 4개 서로 다르고 정답과 가까운 오답`() {
        val gen = RoundGen(Random(7))
        var sawPlus = false
        var sawMinus = false
        for (ans in GameConfig.COUNT_MIN..GameConfig.COUNT_MAX) repeat(60) {
            val c = gen.choicesFor(ans)
            assertEquals(GameConfig.CHOICES, c.size)
            assertEquals("서로 다름", c.size, c.distinct().size)
            assertEquals("정답 한 번", 1, c.count { it == ans })
            for (v in c) {
                assertTrue(v >= 1)
                assertTrue("가까움 $ans→$v", abs(v - ans) <= GameConfig.NEAR_MAX)
            }
            assertTrue("±1 오답 하나는 꼭", c.any { abs(it - ans) == 1 })
            if (c.any { it > ans }) sawPlus = true
            if (c.any { it < ans }) sawMinus = true
        }
        assertTrue(sawPlus && sawMinus)
    }

    @Test
    fun `정답 위치는 섞인다`() {
        val gen = RoundGen(Random(3))
        val seen = HashSet<Int>()
        repeat(200) { seen.add(gen.choicesFor(12).indexOf(12)) }
        assertEquals(setOf(0, 1, 2, 3), seen)
        val r = gen.make(5)
        assertEquals(r.count, r.choices[r.correctIndex])
    }

    @Test
    fun `같은 씨앗이면 같은 판`() {
        val a = RoundGen(Random(42)).makeSet()
        val b = RoundGen(Random(42)).makeSet()
        for (i in a.indices) {
            assertEquals(a[i].count, b[i].count)
            assertArrayEquals(a[i].choices, b[i].choices)
            assertEquals(a[i].seed, b[i].seed)
        }
    }

    // ── 그림 배치 ──

    @Test
    fun `그림은 칸 안에서 겹치지 않는다`() {
        for (aspect in floatArrayOf(0.8f, 0.9f, 1.3f, 1.8f, 2.1f, 2.7f)) {
            val base = DotLayout.baseRadius(aspect)
            for (n in GameConfig.COUNT_MIN..GameConfig.COUNT_MAX) repeat(15) { s ->
                val dots = DotLayout.generate(n, aspect, s * 1000L + n)
                assertEquals(n, dots.size)
                for (d in dots) {
                    assertTrue("크기 고정 범위", d.r <= base * GameConfig.DOT_SCALE_MAX + 1e-4f)
                    assertTrue("작아지지 않음 ($aspect, $n)", d.r >= base * GameConfig.DOT_SCALE_MIN - 1e-4f)
                    assertTrue("칸 안 x", d.x - d.r >= 0f && d.x + d.r <= aspect)
                    assertTrue("칸 안 y", d.y - d.r >= 0f && d.y + d.r <= 1f)
                    assertTrue(d.color in 0 until GameConfig.DOT_COLORS)
                }
                for (i in dots.indices) for (j in i + 1 until dots.size) {
                    val a = dots[i]
                    val b = dots[j]
                    val dist = Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                    assertTrue("겹침 없음 ($aspect, $n)", dist >= (a.r + b.r).toDouble())
                    assertFalse(DotLayout.overlaps(a, b))
                }
            }
        }
    }

    @Test
    fun `배치는 씨앗으로 정해지고 모양이 섞인다`() {
        val a = DotLayout.generate(20, 1.6f, 99L)
        val b = DotLayout.generate(20, 1.6f, 99L)
        for (i in a.indices) {
            assertEquals(a[i].x, b[i].x, 0f)
            assertEquals(a[i].y, b[i].y, 0f)
            assertSame(a[i].shape, b[i].shape)
        }
        val c = DotLayout.generate(20, 1.6f, 100L)
        assertNotEquals(a[0].x, c[0].x)
        assertTrue("모양 여러 가지", a.map { it.shape }.distinct().size >= 3)
        assertTrue("색 여러 가지", a.map { it.color }.distinct().size >= 3)
    }

    @Test
    fun `크기는 개수와 상관없다`() {
        val few = DotLayout.generate(5, 1.5f, 1L)
        val many = DotLayout.generate(20, 1.5f, 1L)
        val base = DotLayout.baseRadius(1.5f)
        for (d in few + many) assertTrue(d.r >= base * GameConfig.DOT_SCALE_MIN - 1e-4f)
    }

    // ── 잠금·먼저 맞히기 ──

    private fun fixed(count: Int, choices: IntArray) = Round(1, count, 900L, choices, 1L)

    private fun game(n: Int, rounds: Int = 3) =
        CountGame(n, rounds = List(rounds) { fixed(7, intArrayOf(6, 7, 9, 8)) })

    @Test
    fun `그림을 보는 동안은 누를 수 없다`() {
        val g = game(2)
        assertSame(Answer.IGNORED, g.answer(0, 1, 0))
        g.nextRound()
        assertSame(Phase.SHOWING, g.phase)
        assertSame(Answer.IGNORED, g.answer(0, 1, 100))
        g.hide()
        assertSame(Phase.ASKING, g.phase)
        assertSame(Answer.CORRECT, g.answer(0, 1, 200))
    }

    @Test
    fun `틀리면 1초 잠금 그동안 정답도 무시`() {
        val g = game(3)
        g.nextRound(); g.hide()
        assertEquals(1000L, GameConfig.LOCK_MS)
        assertSame(Answer.WRONG, g.answer(0, 0, 1000))
        assertTrue(g.isLocked(0, 1500))
        assertFalse(g.isLocked(1, 1500))
        assertSame(Answer.LOCKED, g.answer(0, 1, 1999))
        assertEquals(-1, g.winner)
        assertEquals(1 shl 0, g.wrongMask[0])
        assertFalse(g.isLocked(0, 2000))
        assertSame(Answer.CORRECT, g.answer(0, 1, 2000))
        assertEquals(0, g.winner)
        assertArrayEquals(intArrayOf(1, 0, 0), g.scores)
    }

    @Test
    fun `먼저 맞힌 사람만 점수 그다음은 무시`() {
        val g = game(4)
        g.nextRound(); g.hide()
        assertSame(Answer.WRONG, g.answer(3, 2, 10))
        assertSame(Answer.CORRECT, g.answer(2, 1, 11))
        assertSame(Answer.IGNORED, g.answer(1, 1, 12))
        assertSame(Phase.CLOSED, g.phase)
        assertArrayEquals(intArrayOf(0, 0, 1, 0), g.scores)
        // 새 라운드: 잠금·틀린 표시 풀림
        g.nextRound()
        assertFalse(g.isLocked(3, 12))
        assertEquals(0, g.wrongMask[3])
        assertEquals(-1, g.winner)
    }

    @Test
    fun `시간 끝이면 점수 없이 다음 마지막이면 게임 끝`() {
        val g = game(2, rounds = 2)
        g.nextRound(); g.hide(); g.timeUp()
        assertSame(Phase.CLOSED, g.phase)
        assertArrayEquals(intArrayOf(0, 0), g.scores)
        g.nextRound(); g.hide()
        assertSame(Answer.CORRECT, g.answer(1, 1, 5))
        assertTrue(g.gameOver)
        assertFalse(g.hasNext)
        g.timeUp()
        assertTrue(g.gameOver)
    }

    @Test
    fun `잘못된 번호는 무시`() {
        val g = game(2)
        g.nextRound(); g.hide()
        assertSame(Answer.IGNORED, g.answer(2, 1, 0))
        assertSame(Answer.IGNORED, g.answer(0, 4, 0))
        assertSame(Answer.IGNORED, g.answer(0, -1, 0))
    }

    @Test(expected = IllegalStateException::class)
    fun `보는 중에 다음 라운드 불가`() {
        val g = game(2)
        g.nextRound()
        g.nextRound()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `인원은 2~4명`() {
        CountGame(5)
    }

    // ── 순위 ──

    @Test
    fun `점수 순위 동점은 같은 순위`() {
        val g = game(4, rounds = 5)
        val who = intArrayOf(1, 1, 3, 3, 0)
        for (w in who) { g.nextRound(); g.hide(); g.answer(w, 1, 0) }
        assertTrue(g.gameOver)
        assertArrayEquals(intArrayOf(1, 2, 0, 2), g.scores)
        assertArrayEquals(intArrayOf(3, 1, 4, 1), g.ranks())
    }

    @Test
    fun `모두 0점이면 모두 1위`() {
        val g = game(3, rounds = 1)
        g.nextRound(); g.hide(); g.timeUp()
        assertArrayEquals(intArrayOf(1, 1, 1), g.ranks())
    }

    @Test
    fun `기본 판은 12라운드`() {
        val g = CountGame(2, Random(5))
        assertEquals(12, g.total)
        assertEquals(12, GameConfig.ROUNDS)
    }

    // ── 화면 배치 ──

    @Test
    fun `내용 칸은 그림 칸과 겹치지 않고 크기가 같다`() {
        val w = 1280f
        val h = 720f
        for (n in 2..4) {
            val zones = TouchZones.layout(n, w, h, 20f)
            val l = CountLayout()
            l.layout(zones, w, h, 16f)
            assertTrue(l.flash.contains(w / 2f, h / 2f))
            for (z in zones) {
                val b = l.contents[z.index]
                assertFalse("구역 ${z.index} 내용이 그림 칸을 가림 ($n 인)", b.intersects(l.flash))
                assertTrue("구역 안", b.left >= z.left && b.right <= z.right && b.top >= z.top && b.bottom <= z.bottom)
                assertEquals(l.cellW, b.width, 0.01f)
                assertEquals(l.cellH, b.height, 0.01f)
                val pt = FloatArray(2)
                val tmp = FloatArray(2)
                for (k in 0 until GameConfig.CHOICES) {
                    l.choiceCenter(z.index, k, pt)
                    assertTrue("보기는 자기 구역 안", z.contains(pt[0], pt[1]))
                    assertFalse("보기는 그림 칸 밖", l.flash.contains(pt[0], pt[1]))
                    assertEquals(k, l.choiceAt(z.index, pt[0], pt[1], tmp))
                }
            }
            assertEquals(n == 2, l.tall)
            // 보기끼리 겹치지 않음
            for (a in 0 until GameConfig.CHOICES) for (b in a + 1 until GameConfig.CHOICES) assertFalse(l.choices[a].intersects(l.choices[b]))
        }
    }

    @Test
    fun `위쪽 구역은 돌려서 보기 순서가 뒤집힌다`() {
        val zones = TouchZones.layout(4, 1280f, 720f, 20f)
        val l = CountLayout()
        l.layout(zones, 1280f, 720f, 16f)
        val a = FloatArray(2)
        val b = FloatArray(2)
        l.choiceCenter(0, 0, a)
        l.choiceCenter(0, 3, b)
        assertTrue("위쪽: 화면에서 오른쪽→왼쪽", a[0] > b[0])
        assertTrue("위쪽: 보기는 화면 위 가장자리 쪽", a[1] < l.contents[0].centerY)
        l.choiceCenter(2, 0, a)
        l.choiceCenter(2, 3, b)
        assertTrue(a[0] < b[0])
        assertTrue(a[1] > l.contents[2].centerY)
    }
}
