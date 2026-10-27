package com.siwoo.party.games.colorcode

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ColorCodeGameTest {

    // 색 이름 (읽기 쉽게): A=0 B=1 C=2 D=3 E=4 F=5
    private fun c(s: String): IntArray = IntArray(s.length) { s[it] - 'A' }

    private fun pegs(code: String, guess: String) = ColorCodeGame.score(c(code), c(guess))

    // ───────── 핀 계산 ─────────

    @Test fun `모두 맞으면 금색 4개`() {
        assertEquals(Pegs(4, 0), pegs("ABCD", "ABCD"))
        assertTrue(pegs("ABCD", "ABCD").isSolved)
    }

    @Test fun `하나도 없으면 핀 없음`() {
        assertEquals(Pegs(0, 0), pegs("ABCD", "EFEF"))
    }

    @Test fun `색만 모두 맞으면 은색 4개`() {
        assertEquals(Pegs(0, 4), pegs("ABCD", "DCBA"))
        assertEquals(Pegs(0, 4), pegs("ABCD", "BADC"))
    }

    @Test fun `금색과 은색 섞임`() {
        assertEquals(Pegs(2, 2), pegs("ABCD", "ABDC"))
        assertEquals(Pegs(1, 1), pegs("ABCD", "AEBF"))
        assertEquals(Pegs(0, 2), pegs("ABCD", "EAFB"))
    }

    @Test fun `중복 색 - AABB 와 ABAB`() {
        // 자리 0·3 금색, 나머지 A 하나·B 하나는 자리만 다름
        assertEquals(Pegs(2, 2), pegs("AABB", "ABAB"))
        assertEquals(Pegs(2, 2), pegs("ABAB", "AABB"))
    }

    @Test fun `중복 색 - 금색이 먼저 쓰이면 은색으로 두 번 세지 않음`() {
        // 코드에 A 하나뿐: 추리 AAAA → 금 1, 은 0 (남은 A 는 코드에 없음)
        assertEquals(Pegs(1, 0), pegs("ABCD", "AAAA"))
        // 코드 AAAB, 추리 ABBB → 금: 0번 A, 3번 B = 2 / 공통: A min(3,1)=1 + B min(1,3)=1 = 2 → 은 0
        assertEquals(Pegs(2, 0), pegs("AAAB", "ABBB"))
    }

    @Test fun `중복 색 - 추리에 같은 색이 코드보다 많을 때`() {
        // 코드 AABC, 추리 CAAA → 금: 1번 A / 공통: A min(2,3)=2 + C 1 = 3 → 은 2
        assertEquals(Pegs(1, 2), pegs("AABC", "CAAA"))
        // 코드 ABCD, 추리 BBBB → 금: 1번 B, 은 0
        assertEquals(Pegs(1, 0), pegs("ABCD", "BBBB"))
        // 코드 BBAA, 추리 AABB → 금 0, 은 4
        assertEquals(Pegs(0, 4), pegs("BBAA", "AABB"))
    }

    @Test fun `중복 색 - 코드에 같은 색이 더 많을 때`() {
        // 코드 AAAA, 추리 ABCD → 금 1, 은 0
        assertEquals(Pegs(1, 0), pegs("AAAA", "ABCD"))
        // 코드 AABB, 추리 BCAD → 금 0 / 공통 A 1 + B 1 = 2 → 은 2
        assertEquals(Pegs(0, 2), pegs("AABB", "BCAD"))
        // 코드 ABBA, 추리 BAAB → 은 4
        assertEquals(Pegs(0, 4), pegs("ABBA", "BAAB"))
        // 코드 FFEE, 추리 FEFE → 금 2, 은 2
        assertEquals(Pegs(2, 2), pegs("FFEE", "FEFE"))
    }

    @Test fun `금색 + 은색은 항상 4 이하`() {
        val r = Random(7)
        repeat(500) {
            val a = ColorCodeGame.randomCode(r, true)
            val b = ColorCodeGame.randomCode(r, true)
            val p = ColorCodeGame.score(a, b)
            assertTrue(p.gold + p.silver <= GameConfig.SLOTS)
            assertEquals("대칭", p, ColorCodeGame.score(b, a))
        }
    }

    // ───────── 중복 허용 옵션 ─────────

    @Test fun `중복 끔 - 같은 색이 든 비밀은 거절`() {
        val g = ColorCodeGame(allowDuplicates = false)
        assertFalse(g.setSecret(0, c("AABC")))
        assertFalse(g.hasSecret(0))
        assertTrue(g.setSecret(0, c("ABCD")))
        assertTrue(g.hasSecret(0))
    }

    @Test fun `중복 끔 - 같은 색이 든 추리도 거절`() {
        val g = ColorCodeGame(allowDuplicates = false)
        g.setSecret(0, c("ABCD"))
        g.setSecret(1, c("CDEF"))
        assertNull(g.guess(c("AAAA")))
        assertEquals(0, g.tries(0))
        assertNotNull(g.guess(c("ABCD")))
        assertEquals(1, g.tries(0))
    }

    @Test fun `중복 켬 - 같은 색 비밀과 추리 가능`() {
        val g = ColorCodeGame(allowDuplicates = true)
        assertTrue(g.setSecret(0, c("AAAA")))
        assertTrue(g.setSecret(1, c("AABB")))
        val r = g.guess(c("ABAB"))!!
        assertEquals(Pegs(2, 2), r.record.pegs)
    }

    @Test fun `잘못된 코드 - 길이·색 범위`() {
        assertFalse(ColorCodeGame.isValidCode(c("ABC"), true))
        assertFalse(ColorCodeGame.isValidCode(c("ABCDE"), true))
        assertFalse(ColorCodeGame.isValidCode(intArrayOf(0, 1, 2, 6), true))
        assertFalse(ColorCodeGame.isValidCode(intArrayOf(0, 1, 2, -1), true))
        assertTrue(ColorCodeGame.isValidCode(c("FEDC"), false))
    }

    @Test fun `무작위 코드는 규칙을 지킴`() {
        val r = Random(3)
        repeat(200) {
            assertTrue(ColorCodeGame.isValidCode(ColorCodeGame.randomCode(r, false), false))
            assertTrue(ColorCodeGame.isValidCode(ColorCodeGame.randomCode(r, true), true))
        }
        // 중복 켬이면 언젠가는 같은 색이 나온다
        assertTrue((0 until 200).any { !ColorCodeGame.isValidCode(ColorCodeGame.randomCode(r, true), false) })
    }

    @Test fun `비밀을 다 정하기 전엔 추리 불가`() {
        val g = ColorCodeGame()
        g.setSecret(0, c("ABCD"))
        assertEquals(Phase.SETUP, g.phase)
        assertNull(g.guess(c("ABCD")))
        g.setSecret(1, c("BCDE"))
        assertEquals(Phase.PLAYING, g.phase)
        assertFalse("정한 뒤엔 바꿀 수 없음", g.setSecret(0, c("FEDC")))
    }

    @Test fun `비밀 코드는 복사본으로 저장`() {
        val g = ColorCodeGame()
        val s = c("ABCD")
        g.setSecret(0, s)
        s[0] = 5
        assertArrayEquals(c("ABCD"), g.secretOf(0))
    }

    // ───────── 차례 · 최대 횟수 · 공정 ─────────

    private fun started(first: Int = 0, dup: Boolean = false, s0: String = "ABCD", s1: String = "CDEF"): ColorCodeGame {
        val g = ColorCodeGame(dup, first)
        g.setSecret(0, c(s0))
        g.setSecret(1, c(s1))
        return g
    }

    @Test fun `번갈아 추리 - 선공 다음 후공, 한 바퀴마다 번째 증가`() {
        val g = started(first = 1)
        assertEquals(1, g.current)
        assertEquals(1, g.round)
        g.guess(c("ABEF")) // 1 이 0 의 코드(ABCD)를 추리
        assertEquals(0, g.current)
        assertEquals(1, g.round)
        g.guess(c("ABCF"))
        assertEquals(1, g.current)
        assertEquals(2, g.round)
        assertEquals(Pegs(2, 0), g.history[1][0].pegs)
        assertEquals(Pegs(1, 1), g.history[0][0].pegs) // 0 이 1 의 코드(CDEF)를 추리: F 금, C 은
    }

    @Test fun `최대 10번 - 둘 다 못 맞히면 무승부로 끝`() {
        val g = started()
        repeat(GameConfig.MAX_TRIES) { k ->
            assertEquals(Phase.PLAYING, g.phase)
            assertEquals(k + 1, g.round)
            assertFalse(g.guess(c("ABEF"))!!.gameOver)
            val r = g.guess(c("BCDE"))!!
            assertEquals(k == GameConfig.MAX_TRIES - 1, r.gameOver)
        }
        assertEquals(Phase.OVER, g.phase)
        assertEquals(GameConfig.MAX_TRIES, g.tries(0))
        assertEquals(GameConfig.MAX_TRIES, g.tries(1))
        assertNull("11번째는 없음", g.guess(c("ABCD")))
        assertEquals(GameConfig.UNSOLVED_SCORE, g.score(0))
        assertTrue(g.isDraw)
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    @Test fun `선공이 맞혀도 후공은 같은 번째 추리를 한다 - 못 맞히면 선공 승`() {
        val g = started()
        g.guess(c("ABEF")); g.guess(c("BCDE")) // 1번째
        val r = g.guess(c("CDEF"))!! // 선공 2번째에 정답
        assertTrue(r.record.pegs.isSolved)
        assertTrue(r.lastChanceStarted)
        assertFalse(r.gameOver)
        assertTrue(g.lastChance)
        assertEquals(1, g.current)
        assertEquals(Phase.PLAYING, g.phase)
        val r2 = g.guess(c("ABCE"))!! // 후공 2번째 실패
        assertTrue(r2.gameOver)
        assertFalse(g.lastChance)
        assertEquals(2, g.tries(0))
        assertEquals(2, g.tries(1))
        assertEquals(0, g.winner)
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }

    @Test fun `같은 번째에 둘 다 맞히면 무승부`() {
        val g = started()
        g.guess(c("ABEF")); g.guess(c("BCDE"))
        g.guess(c("BCDA")); g.guess(c("ABCF"))
        assertTrue(g.guess(c("CDEF"))!!.lastChanceStarted) // 3번째
        val r = g.guess(c("ABCD"))!!
        assertTrue(r.gameOver)
        assertEquals(3, g.solvedAt(0))
        assertEquals(3, g.solvedAt(1))
        assertTrue(g.isDraw)
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    @Test fun `후공이 먼저 맞히면 그 번째에서 바로 후공 승`() {
        val g = started(first = 1)
        // 선공 = 1 (0 의 코드 ABCD 를 추리), 후공 = 0 (1 의 코드 CDEF)
        g.guess(c("ABEF"))
        val r = g.guess(c("CDEF"))!!
        assertFalse(r.lastChanceStarted)
        assertTrue(r.gameOver)
        assertEquals(0, g.winner)
        assertEquals(1, g.solvedAt(0))
        assertEquals(-1, g.solvedAt(1))
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }

    @Test fun `10번째에 선공만 맞히면 선공 승, 후공도 10번째 기회`() {
        val g = started()
        repeat(GameConfig.MAX_TRIES - 1) { g.guess(c("ABEF")); g.guess(c("BCDE")) }
        assertEquals(GameConfig.MAX_TRIES, g.round)
        val r = g.guess(c("CDEF"))!!
        assertTrue(r.lastChanceStarted)
        assertFalse(r.gameOver)
        assertTrue(g.guess(c("FEDC"))!!.gameOver)
        assertEquals(GameConfig.MAX_TRIES, g.score(0))
        assertEquals(GameConfig.UNSOLVED_SCORE, g.score(1))
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
    }

    @Test fun `10번째에 후공이 맞히면 후공 승`() {
        val g = started()
        repeat(GameConfig.MAX_TRIES - 1) { g.guess(c("ABEF")); g.guess(c("BCDE")) }
        g.guess(c("ABEF"))
        assertTrue(g.guess(c("ABCD"))!!.gameOver)
        assertEquals(1, g.winner)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test fun `잘못된 추리는 차례를 넘기지 않음`() {
        val g = started()
        assertNull(g.guess(c("ABC")))
        assertNull(g.guess(c("AABC")))
        assertEquals(0, g.current)
        assertEquals(0, g.tries(0))
    }

    @Test fun `기록은 복사본, 번호는 1부터`() {
        val g = started()
        val code = c("ABEF")
        g.guess(code)
        code[0] = 5
        val rec = g.history[0][0]
        assertArrayEquals(c("ABEF"), rec.colors)
        assertEquals(1, rec.number)
        assertEquals(0, rec.player)
    }

    @Test fun `진행 중엔 승자 없음, 순위 요청 불가`() {
        val g = started()
        assertEquals(-1, g.winner)
        assertFalse(g.isDraw)
        try {
            g.ranks()
            error("예외가 나야 함")
        } catch (e: IllegalStateException) {
            // 기대
        }
    }
}
