package com.siwoo.party.games.charades

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CharadesGameTest {

    private fun game(n: Int, seed: Int = 1, words: List<List<String>> = CharadesWords.DEFAULT) =
        CharadesGame(n, words, Random(seed)).also { it.setup(GameConfig.MIXED) }

    // ───────────── 팀 나누기 ─────────────

    @Test
    fun `팀 무작위 배정은 언제나 2 대 2`() {
        val seenA = HashSet<Set<Int>>()
        repeat(300) { seed ->
            val t = CharadesTeams.assign(4, Random(seed))
            assertEquals(2, t.teamOf.count { it == 0 })
            assertEquals(2, t.teamOf.count { it == 1 })
            assertEquals(2, t.members[0].size)
            assertEquals(2, t.members[1].size)
            for (team in 0..1) for (p in t.members[team]) assertEquals(team, t.teamOf[p])
            // 네 명 모두 정확히 한 번씩
            assertEquals(setOf(0, 1, 2, 3), (t.members[0] + t.members[1]).toSet())
            for (p in 0..3) assertEquals(t.teamOf[p], t.teamOf[t.teammate(p)])
            seenA.add(t.members[0].toSet())
        }
        // 무작위: 가능한 모든 팀 구성(6가지)이 나온다
        assertEquals(6, seenA.size)
    }

    @Test
    fun `4인은 팀전 3인은 개인전`() {
        assertEquals(CharadesMode.TEAM, game(4).mode)
        assertNotNull(game(4).teams)
        assertEquals(CharadesMode.SOLO, game(3).mode)
        assertNull(game(3).teams)
        assertEquals(-1, game(3).teamOf(0))
    }

    // ───────────── 차례 순서 ─────────────

    @Test
    fun `팀전 차례 순서는 A1 B1 A2 B2 로 팀이 번갈아 모두 한 번씩`() {
        repeat(40) { seed ->
            val g = game(4, seed)
            val t = g.teams!!
            assertArrayEquals(intArrayOf(t.members[0][0], t.members[1][0], t.members[0][1], t.members[1][1]), g.order)
            assertEquals(4, g.totalTurns)
            val explainers = ArrayList<Int>()
            do {
                explainers.add(g.explainer)
                g.beginTurn()
                g.timeUp()
            } while (g.nextTurn())
            assertEquals(Phase.DONE, g.phase)
            assertEquals(g.order.toList(), explainers)
            assertEquals(setOf(0, 1, 2, 3), explainers.toSet())
            // 팀이 번갈아
            for (k in 1 until 4) assertTrue(g.teamOf(explainers[k]) != g.teamOf(explainers[k - 1]))
            assertEquals(0, g.teamOf(explainers[0]))
        }
    }

    @Test
    fun `개인전 차례 순서는 0 1 2 모두 한 번씩`() {
        val g = game(3)
        val explainers = ArrayList<Int>()
        do {
            explainers.add(g.explainer)
            assertEquals(listOf(0, 1, 2).filter { it != g.explainer }, g.guessersOf(g.explainer))
            g.beginTurn()
            g.timeUp()
        } while (g.nextTurn())
        assertEquals(listOf(0, 1, 2), explainers)
        assertTrue(g.isLastTurn)
    }

    // ───────────── 점수 ─────────────

    @Test
    fun `팀전 점수 - 팀원이 맞히면 설명자 팀 +1`() {
        val g = game(4, 3)
        val t = g.teams!!
        val e = g.explainer
        val team = g.teamOf(e)
        assertEquals(listOf(t.teammate(e)), g.guessersOf(e))
        g.beginTurn()
        g.correct()
        g.correct()
        g.pass()
        g.correct()
        assertEquals(3, g.correctCount)
        assertEquals(3, g.teamScores[team])
        assertEquals(0, g.teamScores[1 - team])
        assertEquals(3, g.scores[e])
        // 정답 결과에 맞힌 사람 = 팀원
        assertTrue(g.turnResults.filter { it.answer == Answer.CORRECT }.all { it.guesser == t.teammate(e) })
        // 같은 팀 둘 다 팀 점수로 보인다
        for (p in t.members[team]) assertEquals(3, g.displayScore(p))
        for (p in t.members[1 - team]) assertEquals(0, g.displayScore(p))
        assertEquals(3, g.timeUp())
        g.nextTurn()
        // B팀 차례: 2개
        g.beginTurn()
        g.correct(); g.correct()
        g.timeUp()
        assertEquals(3, g.teamScores[team])
        assertEquals(2, g.teamScores[1 - team])
        assertEquals(team, g.winningTeam)
    }

    @Test
    fun `팀전은 맞힌 사람이 언제나 설명자의 팀원`() {
        val g = game(4, 3)
        g.beginTurn()
        val other = g.teams!!.members[1 - g.teamOf(g.explainer)][0]
        // 팀전은 guesser 를 무시하고 항상 팀원 (생략 가능)
        g.correct(other)
        assertEquals(g.teams!!.teammate(g.explainer), g.turnResults[0].guesser)
    }

    @Test
    fun `개인전 점수 - 맞힌 사람 +1 설명자 +1`() {
        val g = game(3)
        assertEquals(0, g.explainer)
        g.beginTurn()
        g.correct(1)
        g.correct(2)
        g.correct(1)
        assertArrayEquals(intArrayOf(3, 2, 1), g.scores)
        assertEquals(listOf(1, 2, 1), g.turnResults.map { it.guesser })
        g.timeUp()
        g.nextTurn()
        assertEquals(1, g.explainer)
        g.beginTurn()
        g.correct(2)
        g.timeUp()
        assertArrayEquals(intArrayOf(3, 3, 2), g.scores)
        assertEquals(3, g.displayScore(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `개인전에서 설명자 본인을 맞힌 사람으로 넣으면 오류`() {
        val g = game(3)
        g.beginTurn()
        g.correct(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `개인전에서 맞힌 사람을 빼먹으면 오류`() {
        val g = game(3)
        g.beginTurn()
        g.correct()
    }

    // ───────────── 패스 ─────────────

    @Test
    fun `패스는 차례마다 3번까지`() {
        val g = game(3)
        g.beginTurn()
        assertEquals(3, g.passesLeft)
        for (k in 1..3) {
            assertTrue(g.canPass)
            val before = g.word!!.text
            val next = g.pass()
            assertNotNull(next)
            assertTrue(next!!.text != before)
            assertEquals(3 - k, g.passesLeft)
        }
        assertFalse(g.canPass)
        val stuck = g.word!!.text
        assertNull("4번째 패스는 거절", g.pass())
        assertEquals("단어 그대로", stuck, g.word!!.text)
        assertEquals(3, g.passCount)
        // 정답은 여전히 가능, 패스는 계속 0
        g.correct(1)
        assertNull(g.pass())
        assertEquals(GameConfig.MAX_PASSES, g.turnResults.count { it.answer == Answer.PASS })
        g.timeUp()
        // 다음 차례에는 다시 3번
        g.nextTurn()
        g.beginTurn()
        assertEquals(3, g.passesLeft)
        assertNotNull(g.pass())
    }

    @Test
    fun `패스는 점수가 없다`() {
        val g = game(4, 9)
        g.beginTurn()
        g.pass(); g.pass()
        g.timeUp()
        assertArrayEquals(intArrayOf(0, 0), g.teamScores)
        assertArrayEquals(intArrayOf(0, 0, 0, 0), g.scores)
    }

    // ───────────── 단어 ─────────────

    @Test
    fun `한 게임 동안 같은 단어는 다시 나오지 않는다`() {
        repeat(10) { seed ->
            val g = game(4, seed)
            do {
                g.beginTurn()
                repeat(40) { k -> if (k % 5 == 4 && g.canPass) g.pass() else g.correct() }
                g.timeUp()
            } while (g.nextTurn())
            // 4차례 × 41단어 = 164 > 한 카테고리 30, 전체 150 → 전체를 다 쓰기 전까지 겹치지 않음
            val firstAll = g.shownWords.take(150)
            assertEquals(firstAll.size, firstAll.toSet().size)
        }
    }

    @Test
    fun `카테고리를 고르면 그 카테고리 단어부터 다 쓰고 다른 카테고리로 이어 간다`() {
        val g = CharadesGame(3, CharadesWords.DEFAULT, Random(4))
        g.setup(2)
        assertEquals("직업", g.categoryName)
        g.beginTurn()
        val first = ArrayList<CharadesWord>()
        first.add(g.word!!)
        repeat(34) { first.add(g.correct(1)) }
        val jobs = first.take(30)
        assertTrue(jobs.all { it.category == 2 })
        assertEquals(CharadesWords.DEFAULT[2].toSet(), jobs.map { it.text }.toSet())
        assertTrue(first.drop(30).all { it.category != 2 })
        assertEquals(first.size, first.map { it.text }.toSet().size)
    }

    @Test
    fun `카테고리 사이 같은 글자 단어도 한 번만`() {
        val lists = listOf(listOf("가", "나", "다", "라", "마"), listOf("가", "나", "바", "사", "아"), listOf("자", "차", "카", "타", "파"),
            listOf("하", "거", "너", "더", "러"), listOf("머", "버", "서", "어", "저"))
        val g = CharadesGame(3, lists, Random(2))
        g.setup(GameConfig.MIXED)
        g.beginTurn()
        repeat(22) { g.correct(1) }
        // 서로 다른 단어 23개 → 모두 다름
        assertEquals(23, g.shownWords.toSet().size)
    }

    @Test
    fun `시간 끝 - 보던 단어는 TIMEOUT, 정답 화면 도중이면 넣지 않음`() {
        val g = game(3)
        g.beginTurn()
        g.correct(2)
        val last = g.word!!.text
        assertEquals(1, g.timeUp())
        assertEquals(Answer.TIMEOUT, g.turnResults.last().answer)
        assertEquals(last, g.turnResults.last().word)
        assertEquals(Phase.REVIEW, g.phase)
        g.nextTurn()
        g.beginTurn()
        g.correct(0)
        g.timeUp(currentShown = false)
        assertEquals(1, g.turnResults.size)
        assertEquals(Answer.CORRECT, g.turnResults[0].answer)
    }

    @Test
    fun `저장 JSON 왕복 및 깨진 값은 기본 단어`() {
        val lists = CharadesWords.DEFAULT.mapIndexed { k, l -> if (k == 1) listOf(" 탁구 ", "탁구", "", "피구", "족구", "씨름", "수영", "아주아주아주긴운동이름입니다") else l }
        val back = CharadesWords.decode(CharadesWords.encode(CharadesWords.sanitizeAll(lists)))
        assertEquals(listOf("탁구", "피구", "족구", "씨름", "수영", "아주아주아주긴운동이"), back[1])
        assertEquals(CharadesWords.DEFAULT[0], back[0])
        assertEquals(CharadesWords.DEFAULT, CharadesWords.decode("{깨짐"))
        assertEquals(CharadesWords.DEFAULT, CharadesWords.decode(null))
        // 너무 적으면 기본 단어로 채움
        assertEquals(GameConfig.MIN_WORDS, CharadesWords.sanitize(4, listOf("단추")).size)
        for (l in CharadesWords.DEFAULT) {
            assertEquals(GameConfig.DEFAULT_WORDS, l.size)
            assertEquals(l.size, l.toSet().size)
            assertTrue(l.all { it.length <= GameConfig.MAX_WORD_LEN })
        }
        assertEquals(GameConfig.CATEGORY_COUNT, CharadesWords.CATEGORIES.size)
        // 기본 단어는 카테고리 사이에도 겹치지 않는다
        val all = CharadesWords.DEFAULT.flatten()
        assertEquals(all.size, all.toSet().size)
    }

    // ───────────── 순위 ─────────────

    private fun playTeam(seed: Int, perTurn: IntArray): CharadesGame {
        val g = game(4, seed)
        var k = 0
        do {
            g.beginTurn()
            repeat(perTurn[k++]) { g.correct() }
            g.timeUp()
        } while (g.nextTurn())
        return g
    }

    @Test
    fun `팀전 순위 - 이긴 팀 둘 다 1위, 진 팀 둘 다 3위`() {
        // 차례: A1 B1 A2 B2 → A = 3+2 = 5, B = 1+1 = 2
        val g = playTeam(7, intArrayOf(3, 1, 2, 1))
        val t = g.teams!!
        assertEquals(5, g.teamScores[0])
        assertEquals(2, g.teamScores[1])
        val r = g.ranks()
        for (p in t.members[0]) assertEquals(1, r[p])
        for (p in t.members[1]) assertEquals(3, r[p])
        assertEquals(listOf(1, 1, 3, 3), r.sorted())
        assertEquals(0, g.winningTeam)
    }

    @Test
    fun `팀전 순위 - B팀이 이기면 B팀이 1위`() {
        val g = playTeam(11, intArrayOf(0, 4, 1, 0))
        val t = g.teams!!
        val r = g.ranks()
        for (p in t.members[1]) assertEquals(1, r[p])
        for (p in t.members[0]) assertEquals(3, r[p])
        assertEquals(1, g.winningTeam)
    }

    @Test
    fun `팀전 동점이면 모두 1위`() {
        val g = playTeam(5, intArrayOf(2, 3, 2, 1))
        assertEquals(g.teamScores[0], g.teamScores[1])
        assertArrayEquals(intArrayOf(1, 1, 1, 1), g.ranks())
        assertEquals(-1, g.winningTeam)
        val z = playTeam(6, intArrayOf(0, 0, 0, 0))
        assertArrayEquals(intArrayOf(1, 1, 1, 1), z.ranks())
    }

    @Test
    fun `개인전 순위 - 경쟁 순위와 동점`() {
        val g = game(3)
        // 0 설명: 1 이 2개, 2 가 1개 → [3, 2, 1]
        g.beginTurn(); g.correct(1); g.correct(1); g.correct(2); g.timeUp(); g.nextTurn()
        // 1 설명: 0 이 1개 → [4, 3, 1]
        g.beginTurn(); g.correct(0); g.timeUp(); g.nextTurn()
        // 2 설명: 1 이 1개 → [4, 4, 2]
        g.beginTurn(); g.correct(1); g.timeUp()
        assertFalse(g.nextTurn())
        assertArrayEquals(intArrayOf(4, 4, 2), g.scores)
        assertArrayEquals(intArrayOf(1, 1, 3), g.ranks())
    }

    @Test
    fun `개인전 단독 1위`() {
        val g = game(3)
        g.beginTurn(); g.correct(2); g.correct(2); g.timeUp(); g.nextTurn()
        g.beginTurn(); g.correct(2); g.timeUp(); g.nextTurn()
        g.beginTurn(); g.timeUp()
        // 0: 2, 1: 1, 2: 3
        assertArrayEquals(intArrayOf(2, 1, 3), g.scores)
        assertArrayEquals(intArrayOf(2, 3, 1), g.ranks())
    }

    @Test(expected = IllegalStateException::class)
    fun `시작 전 정답은 오류`() {
        val g = CharadesGame(3, CharadesWords.DEFAULT, Random(1))
        g.correct(1)
    }
}
