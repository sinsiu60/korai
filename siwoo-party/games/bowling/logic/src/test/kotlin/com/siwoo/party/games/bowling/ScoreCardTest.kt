package com.siwoo.party.games.bowling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 정식 10핀 점수 계산: 스트라이크·스페어 보너스, 마지막 프레임 보너스 투구, 5프레임, 누적 점수, 칸 글자 */
class ScoreCardTest {

    private fun card(frames: Int, vararg rolls: Int): ScoreCard {
        val c = ScoreCard(frames)
        for (r in rolls) c.roll(r)
        return c
    }

    private fun marks(c: ScoreCard, f: Int): String = (0 until c.boxes(f)).joinToString("|") { c.mark(f, it) }

    @Test
    fun `퍼펙트 게임 300점`() {
        val c = card(10, *IntArray(12) { 10 })
        assertTrue(c.done)
        assertEquals(12, c.rollCount)
        assertEquals(300, c.total)
        for (f in 0 until 10) assertEquals(30 * (f + 1), c.cumulative(f))
        assertEquals("|X", marks(c, 0))
        assertEquals("X|X|X", marks(c, 9))
    }

    @Test
    fun `모두 9 스페어 + 마지막 9 = 190점`() {
        val rolls = ArrayList<Int>()
        repeat(10) { rolls.add(9); rolls.add(1) }
        rolls.add(9)
        val c = card(10, *rolls.toIntArray())
        assertTrue(c.done)
        assertEquals(21, c.rollCount)
        assertEquals(190, c.total)
        assertEquals("9|/", marks(c, 0))
        assertEquals("9|/|9", marks(c, 9))
    }

    @Test
    fun `모두 거터면 0점`() {
        val c = card(10, *IntArray(20))
        assertTrue(c.done)
        assertEquals(20, c.rollCount)
        assertEquals(0, c.total)
        assertEquals(0, c.cumulative(9))
        assertEquals("-|-", marks(c, 3))
        assertEquals("-|-|", marks(c, 9))
    }

    @Test
    fun `게임 중간 스트라이크와 스페어 보너스`() {
        // X | 7 3 | 4 2 | ...
        val c = card(10, 10, 7, 3, 4, 2)
        assertEquals(20, c.frameScore(0)) // 10 + 7 + 3
        assertEquals(14, c.frameScore(1)) // 10 + 4
        assertEquals(6, c.frameScore(2))
        assertEquals(20, c.cumulative(0))
        assertEquals(34, c.cumulative(1))
        assertEquals(40, c.cumulative(2))
        assertEquals(40, c.total)
        assertEquals("|X", marks(c, 0))
        assertEquals("7|/", marks(c, 1))
        assertEquals("4|2", marks(c, 2))
        // 연속 스트라이크 (터키)
        val t = card(10, 10, 10, 10, 0, 0)
        assertEquals(30, t.frameScore(0))
        assertEquals(20, t.frameScore(1))
        assertEquals(10, t.frameScore(2))
        assertEquals(60, t.total)
    }

    @Test
    fun `마지막 프레임 X X X`() {
        val c = card(10, *IntArray(18), 10, 10, 10)
        assertTrue(c.done)
        assertEquals(30, c.frameScore(9))
        assertEquals(30, c.total)
        assertEquals("X|X|X", marks(c, 9))
    }

    @Test
    fun `마지막 프레임 9 스페어 후 X`() {
        val c = card(10, *IntArray(18), 9, 1)
        assertFalse("스페어 → 보너스 투구", c.done)
        assertEquals(9, c.frame)
        assertEquals(2, c.rollInFrame)
        assertEquals("보너스 투구는 새로 세운 10개", 10, c.standing)
        assertNull("보너스 전엔 미확정", c.frameScore(9))
        c.roll(10)
        assertTrue(c.done)
        assertEquals(20, c.frameScore(9))
        assertEquals(20, c.total)
        assertEquals("9|/|X", marks(c, 9))
    }

    @Test
    fun `마지막 프레임 9 - 는 두 구로 끝`() {
        val c = card(10, *IntArray(18), 9, 0)
        assertTrue("오픈 프레임 → 보너스 없음", c.done)
        assertEquals(20, c.rollCount)
        assertEquals(9, c.total)
        assertEquals("9|-|", marks(c, 9))
    }

    @Test
    fun `마지막 프레임 X 뒤 남은 핀과 스페어 표시`() {
        val c = card(10, *IntArray(18), 10, 7)
        assertFalse(c.done)
        assertEquals("X 뒤 7개 쓰러짐 → 3개 남음", 3, c.standing)
        c.roll(3)
        assertTrue(c.done)
        assertEquals(20, c.frameScore(9))
        assertEquals("X|7|/", marks(c, 9))
        // X X 다음 3구는 새로 세운 판
        val d = card(10, *IntArray(18), 10, 10)
        assertEquals(10, d.standing)
        d.roll(4)
        assertEquals("X|X|4", marks(d, 9))
        assertEquals(24, d.total)
    }

    @Test
    fun `5프레임 모드 - 5번째 프레임이 보너스 투구`() {
        val c = ScoreCard(5)
        assertEquals(11, c.maxRolls)
        c.roll(10); c.roll(10); c.roll(10); c.roll(10)
        assertEquals(4, c.frame)
        assertTrue(c.isLastFrame)
        c.roll(10)
        assertFalse("5프레임 스트라이크 → 보너스", c.done)
        c.roll(10); c.roll(10)
        assertTrue(c.done)
        assertEquals(150, c.total)
        assertEquals("X|X|X", marks(c, 4))
        // 5프레임 스페어 → 보너스 1구
        val s = card(5, 3, 4, 3, 4, 3, 4, 3, 4, 5, 5)
        assertFalse(s.done)
        s.roll(6)
        assertTrue(s.done)
        assertEquals(28 + 16, s.total)
        // 5프레임 오픈 → 끝
        val o = card(5, *IntArray(8), 4, 4)
        assertTrue(o.done)
        assertEquals(8, o.total)
    }

    @Test
    fun `보너스를 기다리는 프레임은 누적 점수가 비어 있음`() {
        val c = card(10, 3, 4, 10)
        assertEquals(7, c.cumulative(0))
        assertNull("스트라이크 보너스 아직", c.frameScore(1))
        assertNull(c.cumulative(1))
        assertEquals("확정된 것만 합계", 7, c.total)
        c.roll(5)
        assertNull("보너스 1구만 → 아직", c.cumulative(1))
        c.roll(5)
        assertEquals(27, c.cumulative(1))
        assertNull("스페어 보너스 아직", c.cumulative(2))
        assertEquals(27, c.total)
        c.roll(2)
        assertEquals(39, c.cumulative(2))
        assertNull("2구째 아직", c.frameScore(3))
        assertEquals(39, c.total)
        assertNull("아직 안 온 프레임", c.frameScore(6))
        assertEquals("", c.mark(6, 0))
    }

    @Test
    fun `프레임과 투구 진행`() {
        val c = ScoreCard(10)
        assertEquals(0, c.frame); assertEquals(0, c.rollInFrame); assertEquals(10, c.standing)
        c.roll(6)
        assertEquals(0, c.frame); assertEquals(1, c.rollInFrame); assertEquals(4, c.standing)
        c.roll(2)
        assertEquals(1, c.frame); assertEquals(0, c.rollInFrame); assertEquals(10, c.standing)
        c.roll(10)
        assertEquals("스트라이크는 바로 다음 프레임", 2, c.frame)
        assertEquals(0, c.rollInFrame)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `서 있는 핀보다 많이 쓰러뜨릴 수 없음`() {
        val c = card(10, 6)
        c.roll(5)
    }

    @Test(expected = IllegalStateException::class)
    fun `끝난 점수표엔 더 굴릴 수 없음`() {
        val c = card(5, *IntArray(10))
        c.roll(0)
    }

    @Test
    fun `칸 글자`() {
        assertEquals("-", ScoreCard.sym(0))
        assertEquals("7", ScoreCard.sym(7))
        assertEquals("X", ScoreCard.sym(10))
        val c = card(10, 0, 10)
        assertEquals("0 뒤 10개 = 스페어", "-|/", marks(c, 0))
        // 마지막 프레임 0 뒤 10개도 스페어 (새로 세운 판 아님)
        val d = card(10, *IntArray(18), 0)
        assertFalse(d.freshRack)
        assertEquals(10, d.standing)
        d.roll(10)
        assertTrue("스페어 → 보너스 1구는 새 판", d.freshRack)
        assertEquals("-|/|", marks(d, 9))
    }
}
