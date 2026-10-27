package com.siwoo.party.games.drawing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DrawingGameTest {

    private fun game(n: Int, seed: Int = 1) = DrawingGame(n, DrawingWords.DEFAULT, Random(seed))

    // ── 점수 ──

    @Test fun `맞히면 맞힌 사람 +2 그린 사람 +1`() {
        val g = game(3)
        assertEquals(Phase.SECRET, g.phase)
        assertEquals(0, g.drawer)
        g.startDrawing()
        assertTrue(g.guess(2))
        assertArrayEquals(intArrayOf(1, 0, 2), g.scores)
        assertEquals(2, g.guesser)
        assertEquals(Phase.RESULT, g.phase)
        assertFalse("결과 단계에선 더 못 맞힘", g.guess(1))
        assertArrayEquals(intArrayOf(1, 0, 2), g.scores)
    }

    @Test fun `시간 초과는 아무도 점수 없음`() {
        val g = game(4)
        g.startDrawing()
        g.timeout()
        assertArrayEquals(intArrayOf(0, 0, 0, 0), g.scores)
        assertEquals(-1, g.guesser)
        assertEquals(-1, g.history.last().guesser)
        assertEquals(g.word, g.history.last().word)
    }

    @Test fun `그린 사람 자신이나 없는 사람은 맞힐 수 없음, 그리기 전에도 안 됨`() {
        val g = game(3)
        assertFalse("제시어 확인 중", g.guess(1))
        g.startDrawing()
        assertFalse(g.guess(0))
        assertFalse(g.guess(3))
        assertFalse(g.guess(-1))
        assertEquals(Phase.DRAWING, g.phase)
        assertArrayEquals(intArrayOf(0, 0, 0), g.scores)
    }

    // ── 차례 ──

    @Test fun `차례 순서 - 각자 2번씩 0 1 2 0 1 2`() {
        val g = game(3)
        assertEquals(6, g.totalTurns)
        val order = ArrayList<Int>()
        val rounds = ArrayList<Int>()
        do {
            order.add(g.drawer); rounds.add(g.round)
            g.startDrawing()
            g.timeout()
        } while (g.nextTurn())
        assertEquals(listOf(0, 1, 2, 0, 1, 2), order)
        assertEquals(listOf(1, 1, 1, 2, 2, 2), rounds)
        assertEquals(Phase.DONE, g.phase)
        assertEquals(6, g.history.size)
    }

    @Test fun `4인은 8차례, 각자 정확히 2번`() {
        val g = game(4)
        val count = IntArray(4)
        do {
            count[g.drawer]++
            g.startDrawing(); g.guess((g.drawer + 1) % 4)
        } while (g.nextTurn())
        assertArrayEquals(intArrayOf(2, 2, 2, 2), count)
        // 모두 그렸고 모두 다음 사람이 맞힘 → 각자 1×2 + 2×2 = 6
        assertArrayEquals(intArrayOf(6, 6, 6, 6), g.scores)
        assertArrayEquals(intArrayOf(1, 1, 1, 1), g.ranks())
    }

    @Test fun `한 게임 안 제시어 중복 없음, 카테고리와 단어가 맞음`() {
        val g = game(4, 9)
        do {
            assertTrue(g.word in DrawingWords.DEFAULT[g.category])
            g.startDrawing(); g.timeout()
        } while (g.nextTurn())
        assertEquals(8, g.usedWords.size)
        assertEquals(8, g.usedWords.toSet().size)
    }

    // ── 순위 ──

    @Test fun `합산 순위 - 동점은 같은 순위`() {
        val g = game(3)
        // 차례: 0 그림 → 1 맞힘 / 1 그림 → 2 맞힘 / 2 그림 → 시간 끝 / 0 그림 → 2 맞힘 / 1 그림 → 0 맞힘 / 2 그림 → 시간 끝
        val plan = listOf(1, 2, -1, 2, 0, -1)
        var k = 0
        do {
            g.startDrawing()
            if (plan[k] >= 0) g.guess(plan[k]) else g.timeout()
            k++
        } while (g.nextTurn())
        // 0: 그림 +1 +1, 맞힘 +2 = 4 / 1: 맞힘 +2, 그림 +1 +1 = 4 / 2: 맞힘 +2 +2 = 4
        assertArrayEquals(intArrayOf(4, 4, 4), g.scores)
        assertArrayEquals(intArrayOf(1, 1, 1), g.ranks())
    }

    @Test fun `합산 순위 - 4인 2등 동점`() {
        val g = game(4)
        val plan = listOf(1, 0, 1, 0, -1, -1, -1, -1)
        var k = 0
        do {
            g.startDrawing()
            if (plan[k] >= 0) g.guess(plan[k]) else g.timeout()
            k++
        } while (g.nextTurn())
        // 0: 그림1(1이 맞힘)+1, 1그림 맞힘 +2, 3그림 맞힘 +2 = 5 / 1: +2 +1(그림, 0이 맞힘) +2 = 5 / 2: 그림 → 1 맞힘 +1 / 3: 그림 → 0 맞힘 +1
        assertArrayEquals(intArrayOf(5, 5, 1, 1), g.scores)
        assertArrayEquals(intArrayOf(1, 1, 3, 3), g.ranks())
    }

    @Test fun `제시어 저장·불러오기`() {
        val mine = DrawingWords.DEFAULT.toMutableList()
        mine[3] = listOf(" 해 ", "해", "", "달", "별", "구름")
        val back = DrawingWords.decode(DrawingWords.encode(DrawingWords.sanitizeAll(mine)))
        assertEquals(listOf("해", "달", "별", "구름", "비"), back[3])
        assertEquals(DrawingWords.DEFAULT, DrawingWords.decode(null))
        assertEquals(DrawingWords.DEFAULT, DrawingWords.decode("[1,2"))
        for (l in DrawingWords.DEFAULT) {
            assertEquals(GameConfig.DEFAULT_WORDS, l.size)
            assertTrue(l.all { it.isNotBlank() && it.length <= GameConfig.MAX_WORD_LEN })
        }
        val all = DrawingWords.DEFAULT.flatten()
        assertEquals("카테고리끼리도 중복 없음", all.size, all.toSet().size)
    }

    // ── 선 목록 (되돌리기·지우기) ──

    @Test fun `선 그리기 - 시작·점 추가·끝, 가까운 점은 버림`() {
        val m = StrokeModel(minDist = 0.01f)
        assertTrue(m.isEmpty)
        m.begin(0.1f, 0.1f, 1, 0)
        assertTrue(m.drawing)
        assertEquals(StrokeModel.ADDED, m.add(0.2f, 0.1f))
        assertEquals(StrokeModel.SKIPPED, m.add(0.205f, 0.1f))
        assertEquals(StrokeModel.ADDED, m.add(0.3f, 0.2f))
        m.end()
        assertFalse(m.drawing)
        assertEquals(1, m.strokeCount)
        assertEquals(3, m.len(0))
        assertEquals(1, m.color(0))
        assertEquals(0.3f, m.x(0, 2), 0f)
        assertEquals(0.2f, m.y(0, 2), 0f)
        assertEquals(StrokeModel.NOT_DRAWING, m.add(0.5f, 0.5f))
    }

    @Test fun `되돌리기는 마지막 선부터, 다 지우면 더 못 함`() {
        val m = StrokeModel(minDist = 0f)
        for (s in 0 until 3) {
            m.begin(s.toFloat(), 0f, s, s % 2)
            m.add(s + 0.5f, 1f)
            m.end()
        }
        assertEquals(3, m.strokeCount)
        assertEquals(6, m.totalPoints)
        assertTrue(m.undo())
        assertEquals(2, m.strokeCount)
        assertEquals(4, m.totalPoints)
        assertEquals(1, m.color(1))
        // 되돌린 뒤 새 선은 그 자리에
        m.begin(9f, 9f, 3, 1); m.add(9.5f, 9f); m.end()
        assertEquals(3, m.color(2))
        assertEquals(9f, m.x(2, 0), 0f)
        assertTrue(m.undo()); assertTrue(m.undo()); assertTrue(m.undo())
        assertFalse(m.undo())
        assertTrue(m.isEmpty)
    }

    @Test fun `그리는 중엔 되돌리기 안 됨, 전체 지우기는 굳힌 것까지`() {
        val m = StrokeModel(maxStrokes = 2, minDist = 0f)
        m.begin(0f, 0f, 0, 0)
        assertFalse(m.canUndo)
        assertFalse(m.undo())
        m.end()
        m.begin(1f, 0f, 0, 0); m.end()
        m.begin(2f, 0f, 0, 0); m.end()
        assertEquals(1, m.bakedCount)
        assertFalse(m.isEmpty)
        m.clear()
        assertTrue(m.isEmpty)
        assertEquals(0, m.strokeCount)
        assertEquals(0, m.totalPoints)
        assertEquals(0, m.bakedCount)
    }

    @Test fun `선 수 한도 - 넘으면 가장 오래된 선을 굳힘 (알림 후 제거, 되돌리기 불가)`() {
        val m = StrokeModel(maxStrokes = 3, minDist = 0f)
        val baked = ArrayList<Float>()
        m.onBake = StrokeModel.BakeListener { model, i -> baked.add(model.x(i, 0)) }
        for (s in 0 until 5) { m.begin(s.toFloat(), 0f, 0, 0); m.add(s + 0.5f, 0f); m.end() }
        assertEquals(3, m.strokeCount)
        assertEquals(listOf(0f, 1f), baked)
        assertEquals(2, m.bakedCount)
        assertEquals(2f, m.x(0, 0), 0f)
        assertEquals(4.5f, m.x(2, 1), 0f)
        assertEquals(6, m.totalPoints)
        repeat(3) { assertTrue(m.undo()) }
        assertFalse("굳힌 선은 못 되돌림", m.undo())
        assertFalse(m.isEmpty)
    }

    @Test fun `선 하나가 너무 길면 끊어서 이어 그림 (SPLIT), 이음점 같음`() {
        val m = StrokeModel(maxPointsPerStroke = 4, minDist = 0f)
        m.begin(0f, 0f, 2, 1)
        val results = (1..6).map { m.add(it.toFloat(), 0f) }
        m.end()
        assertEquals(listOf(StrokeModel.ADDED, StrokeModel.ADDED, StrokeModel.ADDED, StrokeModel.SPLIT, StrokeModel.ADDED, StrokeModel.ADDED), results)
        assertEquals(2, m.strokeCount)
        assertEquals(4, m.len(0))
        assertEquals(4, m.len(1))
        assertEquals("이음점", m.x(0, 3), m.x(1, 0), 0f)
        assertEquals(2, m.color(1))
        assertEquals(1, m.width(1))
        assertEquals(7L, m.receivedPoints)
    }

    @Test fun `점 합계 한도 - 오래 그려도 배열은 그대로, 한도 안`() {
        val m = StrokeModel(maxStrokes = 50, maxPointsPerStroke = 100, maxTotalPoints = 1000, minDist = 0f)
        val buf = m.points
        var baked = 0
        m.onBake = StrokeModel.BakeListener { _, _ -> baked++ }
        var t = 0
        repeat(20) { s ->
            m.begin(t * 0.001f, 0f, s % 4, s % 2)
            repeat(400) { t++; m.add(t * 0.001f, (t % 7) * 0.01f) }
            m.end()
            assertTrue(m.totalPoints <= 1000)
            assertTrue(m.strokeCount <= 50)
            for (i in 0 until m.strokeCount) assertTrue(m.len(i) <= 100)
        }
        assertSame("배열을 새로 만들지 않음", buf, m.points)
        assertTrue(baked > 0)
        assertEquals(20L * 401, m.receivedPoints)
        // 남은 선의 점이 이어져 있는지 (굳힌 만큼 앞으로 당겨짐)
        for (i in 1 until m.strokeCount) assertEquals(m.start(i - 1) + m.len(i - 1), m.start(i))
    }
}
