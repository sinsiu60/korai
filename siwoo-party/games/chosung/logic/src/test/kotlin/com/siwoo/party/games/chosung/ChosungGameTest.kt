package com.siwoo.party.games.chosung

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChosungTest {

    @Test fun `받침 없는 글자와 받침 있는 글자`() {
        assertEquals("ㄱㄴㄷ", Chosung.extract("가나다"))
        assertEquals("ㅎㄱ", Chosung.extract("한글"))
        assertEquals("ㄱㅂㅌ", Chosung.extract("갈비탕"))
        assertEquals("ㅅㄱㅅ", Chosung.extract("삼겹살"))
        assertEquals("ㄷㄹ", Chosung.extract("닭륙")) // 겹받침도 초성만
    }

    @Test fun `쌍자음 ㄲ ㄸ ㅃ ㅆ ㅉ`() {
        assertEquals("ㄲㄸㅃㅆㅉ", Chosung.extract("까따빠싸짜"))
        assertEquals("ㄸㅂㅇ", Chosung.extract("떡볶이"))
        assertEquals("ㄱㅊㅉㄱ", Chosung.extract("김치찌개"))
        assertEquals("ㅃㅇㅃ", Chosung.extract("빵위빵"))
        assertEquals("ㅆㄹ", Chosung.extract("씨름"))
        assertEquals("ㅉㅃ", Chosung.extract("짬뽕"))
    }

    @Test fun `19개 초성 전부 (호환용 자모)`() {
        val first = "가까나다따라마바빠사싸아자짜차카타파하"
        assertEquals("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ", Chosung.extract(first))
        // 마지막 음절(힣)과 경계
        assertEquals('ㅎ', Chosung.initialOf('힣'))
        assertEquals('ㄱ', Chosung.initialOf('가'))
        assertEquals('ㄱ', Chosung.initialOf('각'))
        for (c in Chosung.INITIALS) assertTrue("호환용 자모 영역", c.code in 0x3131..0x314E)
        assertEquals(19, Chosung.INITIALS.size)
    }

    @Test fun `공백 숫자 영문 기호는 그대로`() {
        assertEquals("ㅂㅎㄱ ㅈㅈㅅ", Chosung.extract("비행기 조종사"))
        assertEquals("ㅂㄹ 2ㄱ", Chosung.extract("배려 2개"))
        assertEquals("TV ㄹㅁㅋ", Chosung.extract("TV 리모컨"))
        assertEquals("ㅇㅇㅍ15", Chosung.extract("아이폰15"))
        assertEquals("ㄷ-ㅇ!", Chosung.extract("두-이!"))
        assertEquals("ㄱ", Chosung.extract("ㄱ")) // 이미 자모
        assertEquals("", Chosung.extract(""))
        assertEquals(2, Chosung.syllableCount("TV 리모"))
    }

    @Test fun `힌트 글자만 원래대로`() {
        val r = BooleanArray(4).also { it[2] = true }
        assertEquals("ㄱㅊ찌ㄱ", Chosung.masked("김치찌개", r))
    }
}

class ChosungWordsTest {

    @Test fun `기본 단어 6 카테고리 × 30개, 모두 쓸 수 있는 단어, 중복 없음`() {
        assertEquals(6, ChosungWords.CATEGORIES.size)
        val all = ArrayList<String>()
        for ((k, l) in ChosungWords.DEFAULT.withIndex()) {
            assertEquals(ChosungWords.CATEGORIES[k], 30, l.size)
            for (w in l) assertEquals(w, w, ChosungWords.clean(w))
            all.addAll(l)
        }
        assertEquals("카테고리 사이에도 겹치는 단어 없음", all.size, all.toSet().size)
    }

    @Test fun `정리 - 공백 제거, 한글 2글자 미만·너무 긴 단어 빼기, 모자라면 기본 단어`() {
        assertNull(ChosungWords.clean("  "))
        assertNull(ChosungWords.clean("밥"))
        assertNull(ChosungWords.clean("TV"))
        assertNull(ChosungWords.clean("가나다라마바사아자차카타파"))
        assertEquals("TV 리모컨", ChosungWords.clean(" TV 리모컨 "))
        val l = ChosungWords.sanitize(0, listOf(" 마라탕 ", "마라탕", "밥", "", "꿔바로우"))
        assertEquals(listOf("마라탕", "꿔바로우", "김치찌개", "떡볶이", "비빔밥"), l)
        assertEquals(listOf(0), ChosungWords.shortCategories(listOf(listOf("마라탕"), ChosungWords.DEFAULT[1])))
    }

    @Test fun `저장 JSON 왕복과 깨진 값`() {
        val lists = ChosungWords.DEFAULT.toMutableList()
        lists[2] = listOf("가나", "칠레", "페루", "쿠바", "케냐", "라오스")
        val back = ChosungWords.decode(ChosungWords.encode(lists))
        assertEquals(lists[2], back[2])
        assertEquals(ChosungWords.DEFAULT[0], back[0])
        assertSame(ChosungWords.DEFAULT, ChosungWords.decode("{깨짐"))
        assertSame(ChosungWords.DEFAULT, ChosungWords.decode(null))
    }

    @Test fun `문제 후보는 같은 단어를 한 번만`() {
        val pool = ChosungWords.pool(listOf(listOf("사과", "배추"), listOf("사과", "포도")))
        assertEquals(listOf("사과", "배추", "포도"), pool.map { it.word })
        assertEquals("음식", pool[0].categoryName)
    }
}

class ChosungGameTest {

    private val pool = ChosungWords.pool(ChosungWords.DEFAULT)

    private fun game(n: Int = 3, seed: Int = 1) = ChosungGame(n, pool, Random(seed))

    @Test fun `15문제, 단어가 겹치지 않음`() {
        for (seed in 0 until 30) {
            val g = game(seed = seed)
            assertEquals(GameConfig.CHOSUNG_QUESTIONS, g.total)
            val words = ArrayList<String>()
            var now = 0L
            while (!g.gameOver) {
                val q = g.nextQuestion(now)
                words.add(q.word)
                assertEquals(Chosung.extract(q.word), q.initials)
                assertEquals(q.initials, g.display())
                now += GameConfig.REVEAL_AT_MS
                assertSame("20초가 지나면 (힌트 건너뛰고) 정답 공개", Tick.Reveal, g.update(now))
            }
            assertEquals(15, words.size)
            assertEquals("같은 단어가 두 번 나오면 안 됨", 15, words.toSet().size)
        }
        // 단어가 모자라면 있는 만큼 (같은 단어 중복 후보는 하나로)
        val small = ChosungGame(2, listOf(Entry(0, "사과"), Entry(1, "사과"), Entry(0, "포도")), Random(1))
        assertEquals(2, small.total)
    }

    @Test fun `정답 +1 문제 끝, 오답 -1 그 문제 잠금`() {
        val g = game(3)
        g.nextQuestion(0)
        val b = g.buzz(1, 500)
        assertTrue(b is Buzz.Accepted && b.deadlineMs == 500 + GameConfig.ANSWER_MS)
        assertSame(Phase.ANSWERING, g.phase)
        // 답하는 동안 다른 버저 막힘
        val busy = g.buzz(0, 600)
        assertTrue(busy is Buzz.Busy && busy.answerer == 1)
        // 오답 → -1, 잠금, 문제 다시 열림
        val w = g.judge(false, 2000)
        assertTrue(w is Verdict.Wrong && w.player == 1 && w.score == -1 && !w.timeout && !w.allLocked)
        assertSame(Phase.OPEN, g.phase)
        assertTrue(g.locked[1])
        assertTrue(g.buzz(1, 2100) is Buzz.Locked)
        // 다른 사람 정답 → +1, 문제 끝
        assertTrue(g.buzz(2, 2200) is Buzz.Accepted)
        val c = g.judge(true, 3000)
        assertTrue(c is Verdict.Correct && c.player == 2 && c.score == 1)
        assertSame(Phase.IDLE, g.phase)
        assertEquals(2, g.questionWinner)
        assertEquals(1, g.questionsDone)
        assertArrayEquals(intArrayOf(0, -1, 1), g.scores)
        assertSame("문제 사이엔 판정·버저 무시", Verdict.Ignored, g.judge(true, 3100))
        assertSame(Buzz.Ignored, g.buzz(0, 3100))
        // 다음 문제에서는 잠금 풀림
        g.nextQuestion(4000)
        assertFalse(g.locked[1])
        assertTrue(g.buzz(1, 4100) is Buzz.Accepted)
    }

    @Test fun `5초 안에 판정 없으면 오답`() {
        val g = game(2)
        g.nextQuestion(0)
        g.buzz(0, 1000)
        assertEquals(GameConfig.ANSWER_MS, g.msUntilNextEvent(1000))
        assertSame(Tick.None, g.update(1000 + GameConfig.ANSWER_MS - 1))
        val t = g.update(1000 + GameConfig.ANSWER_MS)
        assertTrue(t is Tick.AnswerTimeout && t.verdict.timeout && t.verdict.player == 0 && !t.verdict.allLocked)
        assertEquals(-1, g.scores[0])
        assertSame(Phase.OPEN, g.phase)
        // 둘 다 틀리면 문제 끝 (정답 공개)
        g.buzz(1, 7000)
        val w = g.judge(false, 7500)
        assertTrue(w is Verdict.Wrong && w.allLocked)
        assertSame(Phase.IDLE, g.phase)
        assertEquals(-1, g.questionWinner)
        assertEquals(1, g.questionsDone)
        assertArrayEquals(intArrayOf(-1, -1), g.scores)
    }

    @Test fun `10초 힌트 글자 하나, 20초 정답 공개`() {
        val g = game(2, seed = 3)
        val q = g.nextQuestion(1000)
        assertEquals(GameConfig.HINT_AT_MS, g.msUntilNextEvent(1000))
        assertSame(Tick.None, g.update(1000 + GameConfig.HINT_AT_MS - 1))
        assertEquals(q.initials, g.display())
        val h = g.update(1000 + GameConfig.HINT_AT_MS)
        assertTrue(h is Tick.Hint)
        val i = (h as Tick.Hint).index
        assertTrue(Chosung.isSyllable(q.word[i]))
        val shown = g.display()
        // 공개한 글자 하나만 원래 글자, 나머지는 초성
        var diff = 0
        for (k in q.word.indices) if (shown[k] != q.initials[k]) { diff++; assertEquals(q.word[k], shown[k]) }
        assertEquals(1, diff)
        assertSame("힌트는 한 번만", Tick.None, g.update(1000 + GameConfig.HINT_AT_MS + 10))
        assertEquals(GameConfig.REVEAL_AT_MS - GameConfig.HINT_AT_MS - 10, g.msUntilNextEvent(1000 + GameConfig.HINT_AT_MS + 10))
        assertSame(Tick.Reveal, g.update(1000 + GameConfig.REVEAL_AT_MS))
        assertSame(Phase.IDLE, g.phase)
        assertEquals(-1, g.questionWinner)
        assertEquals(1, g.questionsDone)
        assertArrayEquals(intArrayOf(0, 0), g.scores)
    }

    @Test fun `답하는 동안은 힌트·공개 시계가 멈춤`() {
        val g = game(2, seed = 4)
        g.nextQuestion(0)
        g.buzz(0, 8000) // 8초 열린 뒤 버저
        assertEquals(8000, g.openElapsed(12_000))
        g.judge(false, 12_000) // 4초 동안 답함 → 오답, 다시 열림
        assertEquals(8000, g.openElapsed(12_000))
        assertSame(Tick.None, g.update(13_000)) // 열린 시간 9초
        assertEquals(1000, g.msUntilNextEvent(13_000))
        assertTrue(g.update(14_000) is Tick.Hint) // 열린 시간 10초
        assertSame(Tick.None, g.update(23_999))
        assertSame(Tick.Reveal, g.update(24_000)) // 열린 시간 20초
    }

    @Test fun `점수 순위 - 동점은 같은 순위`() {
        val g = game(4, seed = 2)
        var now = 0L
        fun q(winner: Int, vararg wrongs: Int) {
            g.nextQuestion(now)
            for (p in wrongs) { g.buzz(p, now + 100); g.judge(false, now + 200) }
            if (winner >= 0) { g.buzz(winner, now + 300); g.judge(true, now + 400) }
            now += 1000
        }
        q(0); q(0); q(1); q(1); q(2, 3); q(-1, 3)
        // 0: 2, 1: 2, 2: 1, 3: -2
        assertArrayEquals(intArrayOf(2, 2, 1, -2), g.scores)
        assertArrayEquals(intArrayOf(1, 1, 3, 4), g.finalRanks())
        assertArrayEquals(intArrayOf(2, 2, 1, 0), g.corrects)
        assertNotEquals(Phase.ANSWERING, g.phase)
    }
}
