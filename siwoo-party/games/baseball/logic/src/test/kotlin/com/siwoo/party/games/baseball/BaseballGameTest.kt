package com.siwoo.party.games.baseball

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseballGameTest {

    /** 0번 비밀 a, 1번 비밀 b 로 시작한 판 */
    private fun game(a: String = "123", b: String = "456", first: Int = 0): BaseballGame {
        val g = BaseballGame(first)
        assertTrue(g.setSecret(0, a))
        assertTrue(g.setSecret(1, b))
        return g
    }

    // ── S/B 판정 ──

    @Test fun `모두 맞으면 3스트라이크`() {
        val j = BaseballGame.judge("427", "427")
        assertEquals(Judge(3, 0), j)
        assertTrue(j.isHit)
        assertEquals("3S", j.label)
    }

    @Test fun `숫자만 맞으면 볼`() {
        assertEquals(Judge(0, 3), BaseballGame.judge("427", "742"))
        assertEquals(Judge(0, 1), BaseballGame.judge("427", "759"))
        assertEquals(Judge(0, 2), BaseballGame.judge("427", "275"))
    }

    @Test fun `스트라이크와 볼 섞임`() {
        assertEquals(Judge(1, 2), BaseballGame.judge("427", "472"))
        assertEquals(Judge(1, 1), BaseballGame.judge("427", "471"))
        assertEquals("1S 1B", BaseballGame.judge("427", "471").label)
        assertEquals(Judge(2, 0), BaseballGame.judge("427", "421"))
    }

    @Test fun `하나도 없으면 아웃`() {
        val j = BaseballGame.judge("427", "513")
        assertEquals(Judge(0, 0), j)
        assertTrue(j.isOut)
        assertFalse(j.isHit)
        assertEquals("아웃", j.label)
    }

    @Test fun `0 이 들어간 자리도 판정`() {
        assertEquals(Judge(1, 0), BaseballGame.judge("012", "089"))
        assertEquals(Judge(0, 1), BaseballGame.judge("012", "905"))
        assertEquals(Judge(1, 2), BaseballGame.judge("012", "021"))
        assertEquals(Judge(3, 0), BaseballGame.judge("908", "908"))
        assertEquals(Judge(1, 2), BaseballGame.judge("908", "980"))
        assertEquals(Judge(0, 0), BaseballGame.judge("908", "123"))
    }

    // ── 비밀 숫자 규칙 ──

    @Test fun `서로 다른 숫자 3자리만 비밀로 됨`() {
        assertTrue(BaseballGame.isValidNumber("123"))
        assertTrue(BaseballGame.isValidNumber("987"))
        assertTrue("맨 앞 0 가능", BaseballGame.isValidNumber("012"))
        assertTrue(BaseballGame.isValidNumber("102"))
        assertFalse("중복", BaseballGame.isValidNumber("112"))
        assertFalse("중복 0", BaseballGame.isValidNumber("100"))
        assertFalse("중복 떨어짐", BaseballGame.isValidNumber("121"))
        assertFalse("짧음", BaseballGame.isValidNumber("12"))
        assertFalse("김", BaseballGame.isValidNumber("1234"))
        assertFalse("숫자 아님", BaseballGame.isValidNumber("1a3"))
        assertFalse(BaseballGame.isValidNumber(""))
    }

    @Test fun `잘못된 비밀은 거절되고 둘 다 정해야 시작`() {
        val g = BaseballGame(0)
        assertFalse(g.setSecret(0, "113"))
        assertFalse(g.hasSecret(0))
        assertTrue(g.setSecret(0, "013"))
        assertEquals(Phase.SETUP, g.phase)
        assertNull("시작 전 추측 불가", g.guess("123"))
        assertTrue(g.setSecret(1, "789"))
        assertEquals(Phase.PLAYING, g.phase)
        assertEquals("013", g.secretOf(0))
        assertFalse("시작 뒤엔 비밀 못 바꿈", g.setSecret(1, "456"))
    }

    @Test fun `잘못된 추측은 무시되고 차례도 그대로`() {
        val g = game()
        assertNull(g.guess("455"))
        assertNull(g.guess("45"))
        assertEquals(0, g.current)
        assertTrue(g.history[0].isEmpty())
    }

    // ── 차례 ──

    @Test fun `번갈아 추측하고 상대 비밀로 판정 기록`() {
        val g = game("123", "456", first = 0)
        assertEquals(0, g.current)
        val r = g.guess("465")!!
        assertEquals(Judge(1, 2), r.guess.judge) // 0번은 1번 비밀 456 을 맞힘
        assertEquals(1, g.current)
        assertEquals(1, g.inning)
        val r2 = g.guess("321")!!
        assertEquals(Judge(1, 2), r2.guess.judge) // 1번은 0번 비밀 123
        assertEquals(0, g.current)
        assertEquals(2, g.inning)
        g.guess("789")
        assertEquals(1, g.current)
        assertEquals(2, g.history[0].size)
        assertEquals(1, g.history[1].size)
        assertEquals("789", g.history[0][1].number)
        assertEquals(2, g.history[0][1].inning)
    }

    @Test fun `후공이 정해지면 후공부터`() {
        val g = game(first = 1)
        assertEquals(1, g.current)
        assertEquals(0, g.second)
        g.guess("789")
        assertEquals(0, g.current)
        assertEquals(1, g.inning)
        g.guess("789")
        assertEquals(1, g.current)
        assertEquals(2, g.inning)
    }

    // ── 마지막 기회 ──

    @Test fun `선공이 맞히면 후공에게 마지막 기회`() {
        val g = game("123", "456")
        val r = g.guess("456")!!
        assertTrue(r.guess.judge.isHit)
        assertTrue(r.lastChanceStarted)
        assertFalse(r.gameOver)
        assertEquals(Phase.LAST_CHANCE, g.phase)
        assertEquals(1, g.current)
    }

    @Test fun `마지막 기회에 맞히면 무승부`() {
        val g = game("123", "456")
        g.guess("456")
        val r = g.guess("123")!!
        assertTrue(r.gameOver)
        assertEquals(-1, r.winner)
        assertTrue(g.isDraw)
        assertArrayEquals(intArrayOf(1, 1), g.ranks())
    }

    @Test fun `마지막 기회에 못 맞히면 선공 승`() {
        val g = game("123", "456")
        g.guess("456")
        val r = g.guess("132")!!
        assertTrue(r.gameOver)
        assertEquals(0, r.winner)
        assertEquals(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(1, 2), g.ranks())
        assertNull("끝난 뒤 추측 불가", g.guess("123"))
    }

    @Test fun `선공이 1번일 때 마지막 기회 실패면 1번 승`() {
        val g = game("123", "456", first = 1)
        g.guess("123") // 1번이 0번 비밀을 맞힘
        assertEquals(Phase.LAST_CHANCE, g.phase)
        assertEquals(0, g.current)
        g.guess("789")
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test fun `후공이 먼저 맞히면 바로 승리`() {
        val g = game("123", "456")
        g.guess("789") // 선공 못 맞힘
        val r = g.guess("123")!!
        assertTrue(r.gameOver)
        assertFalse(r.lastChanceStarted)
        assertEquals(1, r.winner)
        assertArrayEquals(intArrayOf(2, 1), g.ranks())
    }

    @Test fun `여러 회 뒤 맞힘`() {
        val g = game("102", "347")
        g.guess("123"); g.guess("120")
        g.guess("374"); g.guess("201")
        assertEquals(3, g.inning)
        val r = g.guess("347")!!
        assertNotNull(r)
        assertEquals(Phase.LAST_CHANCE, g.phase)
        g.guess("012")
        assertEquals(0, g.winner)
        assertEquals(3, g.history[0].size)
        assertEquals(3, g.history[1].size)
    }
}
