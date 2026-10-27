package com.siwoo.party.games.liar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LiarGameTest {

    /** 모드 고르고 판 시작 → 제시어 확인·설명 건너뛰고 토론 → 투표 단계까지 */
    private fun toVote(g: LiarGame) {
        if (g.phase == Phase.MODE_SELECT) g.chooseMode(LiarMode.NORMAL)
        g.startRound()
        g.beginWordCheck()
        while (!g.wordChecked()) Unit
        g.skipExplain()
        g.startVote()
    }

    /** 투표 순서대로 choose(투표한 사람) 에게 투표 */
    private fun voteAll(g: LiarGame, choose: (Int) -> Int) {
        repeat(g.playerCount) { assertTrue(g.castVote(choose(g.voter))) }
        assertEquals(Phase.COUNT, g.phase)
    }

    // ── 제시어 ──

    @Test fun `기본 제시어는 카테고리 6개 × 20단어, 중복·빈 글자 없음, 길이 제한 이내`() {
        assertEquals(listOf("음식", "장소", "동물", "직업", "스포츠", "물건"), LiarWords.CATEGORIES)
        assertEquals(GameConfig.CATEGORY_COUNT, LiarWords.DEFAULT.size)
        for (l in LiarWords.DEFAULT) {
            assertEquals(20, l.size)
            assertEquals(20, l.toSet().size)
            assertTrue(l.all { it.isNotBlank() && it.length <= GameConfig.MAX_WORD_LEN })
        }
        // 카테고리끼리도 겹치지 않음
        assertEquals(120, LiarWords.DEFAULT.flatten().toSet().size)
    }

    @Test fun `단어 정리 - 공백·중복·빈 줄 제거, 8개 미만이면 기본 단어로 채움`() {
        val s = LiarWords.sanitize(0, listOf("  마라탕 ", "", "마라탕", "   ", "탕후루", "아주아주아주아주긴음식이름입니다"))
        assertEquals(GameConfig.MIN_WORDS, s.size)
        assertEquals("사용자 단어가 앞에", listOf("마라탕", "탕후루", "아주아주아주아주긴음"), s.take(3))
        assertEquals("나머지는 기본 단어", LiarWords.DEFAULT[0].take(5), s.drop(3))
        assertEquals(s.size, s.toSet().size)
        // 기본 단어와 겹치는 사용자 단어는 한 번만
        val s2 = LiarWords.sanitize(2, listOf("사자", "호랑이"))
        assertEquals(GameConfig.MIN_WORDS, s2.size)
        assertEquals(LiarWords.DEFAULT[2].take(8), s2)
        // 다 지우면 기본 단어 8개
        assertEquals(LiarWords.DEFAULT[5].take(8), LiarWords.sanitize(5, emptyList()))
        // 8개 이상이면 그대로, 최대 개수 제한
        val many = (1..60).map { "단어$it" }
        assertEquals(GameConfig.MAX_WORDS, LiarWords.sanitize(1, many).size)
        assertEquals(many.take(9), LiarWords.sanitize(1, many.take(9)))
    }

    @Test fun `저장 JSON - 되읽기, 깨진 값·빠진 카테고리는 기본 단어, 모자란 카테고리 표시`() {
        val edited = LiarWords.DEFAULT.toMutableList()
        edited[3] = LiarWords.DEFAULT[3] + "개발자"
        edited[4] = listOf("컬링", "봅슬레이")
        val text = LiarWords.encode(edited)
        val back = LiarWords.decode(text)
        assertEquals(edited[3], back[3])
        assertEquals(GameConfig.MIN_WORDS, back[4].size)
        assertEquals(listOf("컬링", "봅슬레이"), back[4].take(2))
        assertEquals(listOf(4), LiarWords.shortCategories(edited))
        assertEquals(LiarWords.DEFAULT, LiarWords.decode(null))
        assertEquals(LiarWords.DEFAULT, LiarWords.decode("{망가진"))
        assertEquals(LiarWords.DEFAULT, LiarWords.decode("[1,2]"))
        val partial = LiarWords.decode("{\"동물\": [\"오리\"]}")
        assertEquals(LiarWords.DEFAULT[0], partial[0])
        assertEquals("오리", partial[2][0])
        assertEquals(GameConfig.MIN_WORDS, partial[2].size)
        // 게임에 넘겨도 모든 카테고리가 8개 이상
        val g = LiarGame(3, listOf(listOf("하나"), emptyList()), Random(1))
        assertTrue(g.words.all { it.size >= GameConfig.GUESS_CHOICES })
        assertEquals("하나", g.words[0][0])
    }

    // ── 라이어·카테고리·제시어 ──

    @Test fun `라이어 중복 방지 - N판 동안 모두 정확히 한 번씩 라이어`() {
        for (n in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) for (seed in 0 until 40) {
            val g = LiarGame(n, random = Random(seed))
            assertEquals("판 수 = 인원수", n, g.totalRounds)
            val liars = ArrayList<Int>()
            val cats = ArrayList<Int>()
            g.chooseMode(LiarMode.NORMAL)
            while (!g.gameOver) {
                toVoteFromReady(g)
                liars.add(g.liar)
                cats.add(g.category)
                voteAll(g) { v -> if (v == g.liar) (v + 1) % n else g.liar }
                g.countVotes()
                g.guess(g.guessChoices.first { it != g.word })
            }
            assertEquals((0 until n).toList(), liars.sorted())
            assertEquals("카테고리도 겹치지 않음", n, cats.toSet().size)
            assertEquals(Phase.GAME_OVER, g.phase)
        }
        // 순서는 무작위 (시드마다 다름)
        val orders = (0 until 30).map { LiarGame(4, random = Random(it)).liarOrder }.toSet()
        assertTrue(orders.size > 5)
    }

    private fun toVoteFromReady(g: LiarGame) {
        g.startRound()
        g.beginWordCheck()
        while (!g.wordChecked()) Unit
        g.skipExplain()
        g.startVote()
    }

    @Test fun `일반 모드 - 라이어만 제시어 없음, 나머지는 같은 단어`() {
        val g = LiarGame(4, random = Random(3))
        g.chooseMode(LiarMode.NORMAL)
        g.startRound()
        assertEquals(Phase.CATEGORY, g.phase)
        assertTrue(g.word in g.words[g.category])
        for (p in 0 until 4) {
            if (p == g.liar) assertNull(g.wordFor(p)) else assertEquals(g.word, g.wordFor(p))
        }
        assertNull(g.liarWord)
    }

    @Test fun `바보 모드 - 라이어는 같은 카테고리의 다른 단어`() {
        for (seed in 0 until 200) {
            val g = LiarGame(3, random = Random(seed))
            g.chooseMode(LiarMode.FOOL)
            assertEquals(LiarMode.FOOL, g.mode)
            g.startRound()
            val lw = g.wordFor(g.liar)!!
            assertNotEquals(g.word, lw)
            assertTrue("같은 카테고리", lw in g.words[g.category])
            assertEquals(lw, g.liarWord)
            for (p in 0 until 3) if (p != g.liar) assertEquals(g.word, g.wordFor(p))
        }
        // 함수 단독
        val r = Random(9)
        repeat(500) {
            val list = LiarWords.DEFAULT[it % 6]
            val w = list[it % list.size]
            val f = LiarGame.foolWord(list, w, r)
            assertNotEquals(w, f)
            assertTrue(f in list)
        }
    }

    @Test fun `모드는 첫 판 전에만`() {
        val g = LiarGame(3, random = Random(1))
        assertEquals(Phase.MODE_SELECT, g.phase)
        g.chooseMode(LiarMode.FOOL)
        assertEquals(Phase.READY, g.phase)
        try { g.chooseMode(LiarMode.NORMAL); error("두 번 고르면 안 됨") } catch (e: IllegalStateException) { }
        assertEquals(LiarMode.FOOL, g.mode)
    }

    // ── 순서 ──

    @Test fun `제시어 확인 순서 - 시작 사람부터 한 바퀴`() {
        val g = LiarGame(4, random = Random(5))
        g.chooseMode(LiarMode.NORMAL)
        g.startRound()
        g.beginWordCheck()
        val seen = ArrayList<Int>()
        do { seen.add(g.checker) } while (!g.wordChecked())
        assertEquals(g.turnOrder, seen)
        assertEquals(g.starter, seen[0])
        assertEquals((0 until 4).toList(), seen.sorted())
        assertEquals(Phase.EXPLAIN, g.phase)
    }

    @Test fun `설명 순서 - 시작 사람부터 2바퀴`() {
        assertEquals(listOf(2, 0, 1, 2, 0, 1), LiarGame.explainOrder(2, 3))
        assertEquals(listOf(1, 2, 3, 0, 1, 2, 3, 0), LiarGame.explainOrder(1, 4))
        val g = LiarGame(3, random = Random(8))
        g.chooseMode(LiarMode.NORMAL)
        g.startRound()
        g.beginWordCheck()
        while (!g.wordChecked()) Unit
        val spoken = ArrayList<Int>()
        val laps = ArrayList<Int>()
        do { spoken.add(g.speaker); laps.add(g.lap) } while (!g.nextSpeaker())
        assertEquals(6, spoken.size)
        assertEquals(LiarGame.explainOrder(g.starter, 3), spoken)
        assertEquals(spoken.take(3), spoken.drop(3))
        assertEquals(listOf(1, 1, 1, 2, 2, 2), laps)
        for (p in 0 until 3) assertEquals("한 사람이 두 번씩", 2, spoken.count { it == p })
        assertEquals(Phase.DISCUSS, g.phase)
    }

    // ── 투표 ──

    @Test fun `자기 자신에게는 투표 못 함`() {
        val g = LiarGame(4, random = Random(2))
        toVote(g)
        val v = g.voter
        assertFalse(g.canVote(v, v))
        assertFalse(g.castVote(v))
        assertEquals("거절되면 같은 사람 차례 그대로", v, g.voter)
        assertEquals(0, g.votedCount)
        assertFalse(g.castVote(7))
        assertEquals((0 until 4).filter { it != v }, g.choicesFor(v))
        assertTrue(g.castVote((v + 1) % 4))
        assertEquals(1, g.votedCount)
        assertEquals((v + 1) % 4, g.votes[v])
    }

    @Test fun `최다 득표 한 명 - 라이어면 정답 맞히기`() {
        val g = LiarGame(3, random = Random(4))
        toVote(g)
        val liar = g.liar
        voteAll(g) { v -> if (v == liar) (v + 1) % 3 else liar }
        val t = g.countVotes()
        assertTrue(t is Tally.Accused)
        assertEquals(liar, (t as Tally.Accused).player)
        assertEquals(2, t.counts[liar])
        assertEquals(liar, g.accused)
        assertEquals(Phase.GUESS, g.phase)
    }

    @Test fun `동점이면 동점자만 놓고 재투표`() {
        val g = LiarGame(4, random = Random(6))
        toVote(g)
        val o = g.turnOrder
        // o0→o1, o1→o0, o2→o1, o3→o0 : o0·o1 2표씩 동점
        val plan = mapOf(o[0] to o[1], o[1] to o[0], o[2] to o[1], o[3] to o[0])
        voteAll(g) { plan[it]!! }
        assertArrayEquals(intArrayOf(o[1], o[0], o[1], o[0]), g.ballots())
        val t = g.countVotes()
        assertTrue(t is Tally.Revote)
        assertEquals(setOf(o[0], o[1]), (t as Tally.Revote).tied.toSet())
        assertEquals(Phase.VOTE, g.phase)
        assertTrue(g.revote)
        assertEquals(setOf(o[0], o[1]), g.candidates.toSet())
        assertTrue("표 초기화", g.votes.all { it == -1 })
        // 후보가 아닌 사람은 못 고름, 후보인 사람은 다른 후보만
        assertEquals(o[0], g.voter)
        assertFalse(g.canVote(o[0], o[2]))
        assertEquals(listOf(o[1]), g.choicesFor(o[0]))
        assertEquals(setOf(o[0], o[1]), g.choicesFor(o[2]).toSet())
        assertFalse(g.castVote(o[0]))
        assertFalse(g.castVote(o[3]))
        // 재투표: o1 이 3표
        voteAll(g) { v -> if (v == o[1]) o[0] else o[1] }
        val t2 = g.countVotes()
        assertTrue(t2 is Tally.Accused)
        assertEquals(o[1], (t2 as Tally.Accused).player)
        assertEquals(3, t2.counts[o[1]])
        assertTrue(g.phase == Phase.GUESS || g.phase == Phase.ROUND_OVER)
    }

    @Test fun `재투표도 동점이면 라이어 승리 (안 걸림)`() {
        val g = LiarGame(4, random = Random(7))
        toVote(g)
        val liar = g.liar
        val o = g.turnOrder
        val plan = mapOf(o[0] to o[1], o[1] to o[0], o[2] to o[1], o[3] to o[0])
        voteAll(g) { plan[it]!! }
        assertTrue(g.countVotes() is Tally.Revote)
        // 같은 식으로 또 2:2
        voteAll(g) { plan[it]!! }
        val t = g.countVotes()
        assertTrue(t is Tally.TieAgain)
        assertEquals(-1, g.accused)
        val r = g.lastResult!!
        assertEquals(Outcome.NOT_CAUGHT, r.outcome)
        assertEquals(liar, r.liar)
        assertEquals(-1, r.accused)
        assertNull(r.guess)
        assertEquals(GameConfig.POINTS_NOT_CAUGHT, g.scores[liar])
        assertEquals(GameConfig.POINTS_NOT_CAUGHT, g.scores.sum())
        assertEquals(Phase.ROUND_OVER, g.phase)
    }

    @Test fun `3인 - 1표씩 나뉘면 셋 다 재투표 후보`() {
        val t = LiarGame.tally(intArrayOf(1, 2, 0), listOf(0, 1, 2), revote = false)
        assertTrue(t is Tally.Revote)
        assertEquals(listOf(0, 1, 2), (t as Tally.Revote).tied)
        val t2 = LiarGame.tally(intArrayOf(1, 0, 1), listOf(0, 1), revote = true)
        assertTrue(t2 is Tally.Accused)
        assertEquals(1, (t2 as Tally.Accused).player)
        val t3 = LiarGame.tally(intArrayOf(1, 0, 0, 1), listOf(0, 1), revote = true)
        assertTrue(t3 is Tally.TieAgain)
    }

    // ── 정답 맞히기·점수 ──

    @Test fun `정답 보기 - 같은 카테고리 서로 다른 8개, 정답 포함`() {
        val r = Random(12)
        for (k in 0 until 300) {
            val list = LiarWords.DEFAULT[k % 6]
            val answer = list[k % list.size]
            val c = LiarGame.guessChoices(list, answer, r)
            assertEquals(GameConfig.GUESS_CHOICES, c.size)
            assertEquals(GameConfig.GUESS_CHOICES, c.toSet().size)
            assertTrue(answer in c)
            assertTrue(c.all { it in list })
        }
        // 정답 자리도 섞임
        val pos = (0 until 200).map { LiarGame.guessChoices(LiarWords.DEFAULT[0], "피자", Random(it)).indexOf("피자") }.toSet()
        assertTrue(pos.size >= 6)
        // 단어가 딱 8개인 카테고리도
        val eight = LiarWords.DEFAULT[1].take(8)
        assertEquals(eight.toSet(), LiarGame.guessChoices(eight, eight[3], Random(1)).toSet())
    }

    @Test fun `점수 - 라이어가 안 걸리면 라이어 +2`() {
        assertArrayEquals(intArrayOf(0, 2, 0), LiarGame.points(Outcome.NOT_CAUGHT, 1, 3))
        // 엉뚱한 사람 지목
        val g = LiarGame(3, random = Random(10))
        toVote(g)
        val liar = g.liar
        val innocent = (liar + 1) % 3
        voteAll(g) { v -> if (v == innocent) liar else innocent }
        val t = g.countVotes()
        assertEquals(innocent, (t as Tally.Accused).player)
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertEquals(Outcome.NOT_CAUGHT, g.lastResult!!.outcome)
        assertEquals(innocent, g.lastResult!!.accused)
        assertArrayEquals(LiarGame.points(Outcome.NOT_CAUGHT, liar, 3), g.scores)
    }

    @Test fun `점수 - 걸렸지만 정답 맞히면 라이어 +1`() {
        assertArrayEquals(intArrayOf(0, 0, 0, 1), LiarGame.points(Outcome.CAUGHT_CORRECT, 3, 4))
        val g = LiarGame(4, random = Random(11))
        toVote(g)
        val liar = g.liar
        voteAll(g) { v -> if (v == liar) (v + 1) % 4 else liar }
        g.countVotes()
        assertEquals(GameConfig.GUESS_CHOICES, g.guessChoices.size)
        assertTrue(g.word in g.guessChoices)
        assertEquals(Outcome.CAUGHT_CORRECT, g.guess(g.word))
        val r = g.lastResult!!
        assertEquals(g.word, r.guess)
        assertEquals(1, r.round)
        assertArrayEquals(r.gained, g.scores)
        assertEquals(1, g.scores[liar])
        assertEquals(1, g.scores.sum())
    }

    @Test fun `점수 - 걸리고 정답 틀리면 라이어 빼고 모두 +1`() {
        assertArrayEquals(intArrayOf(1, 0, 1), LiarGame.points(Outcome.CAUGHT_WRONG, 1, 3))
        val g = LiarGame(4, random = Random(13))
        g.chooseMode(LiarMode.FOOL)
        toVote(g)
        val liar = g.liar
        voteAll(g) { v -> if (v == liar) (v + 1) % 4 else liar }
        g.countVotes()
        assertEquals("바보 모드도 같은 방식으로 맞히기", Phase.GUESS, g.phase)
        val wrong = g.guessChoices.first { it != g.word }
        assertEquals(Outcome.CAUGHT_WRONG, g.guess(wrong))
        for (p in 0 until 4) assertEquals(if (p == liar) 0 else 1, g.scores[p])
        assertEquals(wrong, g.lastResult!!.guess)
        assertEquals(g.liarWord, g.lastResult!!.liarWord)
    }

    @Test fun `점수는 판마다 쌓이고 최종 순위는 누적 점수 순 (동점 공동)`() {
        val g = LiarGame(3, random = Random(21))
        g.chooseMode(LiarMode.NORMAL)
        // 1판: 안 걸림 (+2 라이어) / 2판: 걸리고 틀림 / 3판: 걸리고 맞힘
        toVoteFromReady(g)
        val l1 = g.liar
        val inn = (l1 + 1) % 3
        voteAll(g) { v -> if (v == inn) l1 else inn }
        g.countVotes()
        toVoteFromReady(g)
        val l2 = g.liar
        voteAll(g) { v -> if (v == l2) (v + 1) % 3 else l2 }
        g.countVotes(); g.guess(g.guessChoices.first { it != g.word })
        toVoteFromReady(g)
        val l3 = g.liar
        voteAll(g) { v -> if (v == l3) (v + 1) % 3 else l3 }
        g.countVotes(); g.guess(g.word)
        assertTrue(g.gameOver)
        val expected = IntArray(3)
        expected[l1] += 2
        for (p in 0 until 3) if (p != l2) expected[p] += 1
        expected[l3] += 1
        assertArrayEquals(expected, g.scores)
        assertEquals(3, g.results.size)
        val ranks = g.finalRanks()
        for (i in 0 until 3) {
            assertEquals(1 + g.scores.count { it > g.scores[i] }, ranks[i])
        }
        try { g.startRound(); error("끝난 뒤 새 판 안 됨") } catch (e: IllegalStateException) { }
    }

    @Test fun `최종 순위 - 동점은 같은 순위`() {
        val g = LiarGame(4, random = Random(1))
        g.scores[0] = 3; g.scores[1] = 5; g.scores[2] = 3; g.scores[3] = 1
        assertArrayEquals(intArrayOf(2, 1, 2, 4), g.finalRanks())
        g.scores.fill(2)
        assertArrayEquals(intArrayOf(1, 1, 1, 1), g.finalRanks())
    }

    @Test fun `인원은 3~4명`() {
        for (n in listOf(2, 5)) {
            try { LiarGame(n); error("$n 명은 안 됨") } catch (e: IllegalArgumentException) { }
        }
        LiarGame(3); LiarGame(4)
    }
}
