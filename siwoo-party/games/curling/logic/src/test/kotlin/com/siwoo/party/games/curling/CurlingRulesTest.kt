package com.siwoo.party.games.curling

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 득점·스톤 배분·던지는 순서·순위 (순수 규칙 함수) */
class CurlingRulesTest {

    private val r = GameConfig.HOUSE_R

    /** (편, 거리) 쌍으로 득점 */
    private fun score(vararg s: Pair<Int, Float>) =
        CurlingRules.scoreEnd(IntArray(s.size) { s[it].first }, FloatArray(s.size) { s[it].second })

    // ── 엔드 득점 ──

    @Test
    fun `가장 가까운 스톤 하나만 안쪽이면 1점`() {
        val e = score(0 to 1f, 1 to 2f, 0 to 3f, 1 to 0.5f + 2f)
        assertEquals(0, e.side)
        assertEquals(1, e.points)
        assertArrayEquals(intArrayOf(0), e.counting)
        assertEquals(0, e.shot)
        assertEquals(1, e.rival)
        assertFalse(e.blank)
    }

    @Test
    fun `상대 가장 가까운 스톤보다 안쪽인 내 스톤을 모두 센다`() {
        // 1번 편: 0.4, 1.0, 2.5 / 0번 편 가장 가까운 것 3.0
        val e = score(1 to 2.5f, 0 to 3f, 1 to 0.4f, 0 to 4f, 1 to 1f, 1 to 5f)
        assertEquals(1, e.side)
        assertEquals(3, e.points)
        assertArrayEquals("가까운 순", intArrayOf(2, 4, 0), e.counting)
        assertEquals(1, e.rival)
    }

    @Test
    fun `상대 스톤이 하우스에 없으면 하우스 안 내 스톤 전부`() {
        val e = score(0 to 1f, 0 to r, 0 to r + GameConfig.STONE_R, 1 to r + GameConfig.STONE_R + 0.01f, 0 to 20f)
        assertEquals(0, e.side)
        assertEquals("걸친 스톤(거리 = 하우스 + 반지름)까지 3개", 3, e.points)
        assertEquals("하우스 밖 상대 스톤은 상대가 아님", -1, e.rival)
    }

    @Test
    fun `하우스가 비면 블랭크`() {
        val e = score(0 to 9f, 1 to 12f, 0 to -1f, 1 to Float.NaN)
        assertTrue(e.blank)
        assertEquals(-1, e.side)
        assertEquals(-1, e.shot)
        assertEquals(0, score().points)
    }

    @Test
    fun `가장 가까운 두 스톤이 다른 편이고 거리가 같으면 블랭크`() {
        val e = score(0 to 1.5f, 1 to 1.5f, 0 to 2f)
        assertTrue(e.tie)
        assertTrue(e.blank)
        assertEquals(-1, e.side)
        assertEquals(0, e.points)
    }

    @Test
    fun `상대 가장 가까운 스톤과 같은 거리인 내 스톤은 세지 않는다`() {
        val e = score(0 to 0.5f, 0 to 2f, 1 to 2f)
        assertEquals(0, e.side)
        assertEquals(1, e.points)
        assertArrayEquals(intArrayOf(0), e.counting)
    }

    @Test
    fun `같은 편끼리 같은 거리는 둘 다 센다`() {
        val e = score(0 to 1f, 0 to 1f, 1 to 3f)
        assertEquals(2, e.points)
        assertFalse(e.tie)
    }

    @Test
    fun `3인전은 다른 두 사람 모두가 상대`() {
        // 0번 0.5·1.5·4.0, 1번 2.0, 2번 1.8 → 상대 가장 가까운 것 1.8 → 0번 2점
        val e = score(0 to 0.5f, 1 to 2f, 2 to 1.8f, 0 to 1.5f, 1 to 6f, 2 to 3f, 0 to 4f)
        assertEquals(0, e.side)
        assertEquals(2, e.points)
        assertEquals(2, e.rival)
        // 2번이 가장 가깝고 0번이 다음 → 2번 1점
        val e2 = score(0 to 1f, 1 to 0.9f, 2 to 0.3f, 2 to 0.95f)
        assertEquals(2, e2.side)
        assertEquals(1, e2.points)
        assertEquals(1, e2.rival)
        // 가장 가까운 자리에 1번·2번이 같은 거리 → 블랭크
        val e3 = score(0 to 2f, 1 to 1f, 2 to 1f)
        assertTrue(e3.tie)
        assertEquals(0, e3.points)
    }

    @Test
    fun `팀전은 팀원 스톤을 함께 센다`() {
        val n = 4
        // 0번·2번 = 0팀, 1번·3번 = 1팀
        assertEquals(0, CurlingRules.sideOf(2, n))
        assertEquals(1, CurlingRules.sideOf(3, n))
        val owners = intArrayOf(0, 1, 2, 3, 2, 1)
        val dist = floatArrayOf(0.5f, 2.5f, 1.2f, 3f, 2f, 7.5f)
        val e = CurlingRules.scoreEnd(IntArray(owners.size) { CurlingRules.sideOf(owners[it], n) }, dist)
        assertEquals(0, e.side)
        assertEquals("0번 1개 + 2번 2개", 3, e.points)
        assertArrayEquals(intArrayOf(0, 2, 4), e.counting)
    }

    // ── 스톤 배분 ──

    @Test
    fun `인원별 스톤 배분`() {
        assertEquals(4, CurlingRules.stonesPerPlayer(2))
        assertEquals(3, CurlingRules.stonesPerPlayer(3))
        assertEquals(2, CurlingRules.stonesPerPlayer(4))
        assertEquals(8, CurlingRules.stonesPerEnd(2))
        assertEquals(9, CurlingRules.stonesPerEnd(3))
        assertEquals(8, CurlingRules.stonesPerEnd(4))
        assertEquals(4, CurlingRules.stonesPerSide(4))
        assertEquals(2, CurlingRules.sideCount(4))
        assertEquals(3, CurlingRules.sideCount(3))
        assertTrue(CurlingRules.isTeamMode(4))
        assertFalse(CurlingRules.isTeamMode(3))
        // 순서표에서도 1인당 개수가 맞다
        for (n in 2..4) for (start in 0 until CurlingRules.sideCount(n)) for (end in 0 until GameConfig.ENDS) {
            val o = CurlingRules.throwOrder(n, start, end)
            for (p in 0 until n) assertEquals("${n}인 $p", CurlingRules.stonesPerPlayer(n), o.count { it == p })
        }
    }

    // ── 던지는 순서 ──

    @Test
    fun `2인과 3인은 한 개씩 번갈아`() {
        assertArrayEquals(intArrayOf(0, 1, 0, 1, 0, 1, 0, 1), CurlingRules.throwOrder(2, 0, 0))
        assertArrayEquals(intArrayOf(1, 0, 1, 0, 1, 0, 1, 0), CurlingRules.throwOrder(2, 1, 2))
        assertArrayEquals(intArrayOf(2, 0, 1, 2, 0, 1, 2, 0, 1), CurlingRules.throwOrder(3, 2, 0))
    }

    @Test
    fun `팀전은 팀이 번갈아 던지고 팀 안에서 두 개씩 이어서, 엔드마다 팀 안 순서를 바꾼다`() {
        assertArrayEquals(intArrayOf(0, 1, 0, 1, 2, 3, 2, 3), CurlingRules.throwOrder(4, 0, 0))
        assertArrayEquals(intArrayOf(3, 2, 3, 2, 1, 0, 1, 0), CurlingRules.throwOrder(4, 1, 1))
        assertArrayEquals(intArrayOf(1, 0, 1, 0, 3, 2, 3, 2), CurlingRules.throwOrder(4, 1, 2))
    }

    @Test
    fun `득점한 편이 다음 엔드에 먼저, 블랭크면 다음 편`() {
        assertEquals(1, CurlingRules.nextStartSide(0, 1, 2))
        assertEquals(0, CurlingRules.nextStartSide(0, 0, 2))
        assertEquals(1, CurlingRules.nextStartSide(0, -1, 2))
        assertEquals(0, CurlingRules.nextStartSide(2, -1, 3))
        assertEquals(2, CurlingRules.nextStartSide(0, 2, 3))
    }

    // ── 순위 ──

    @Test
    fun `합계 점수 순위 (동점 같은 순위, 팀원은 팀 순위)`() {
        assertArrayEquals(intArrayOf(2, 1), CurlingRules.ranks(2, intArrayOf(1, 3)))
        assertArrayEquals(intArrayOf(1, 1), CurlingRules.ranks(2, intArrayOf(2, 2)))
        assertArrayEquals(intArrayOf(1, 3, 1), CurlingRules.ranks(3, intArrayOf(2, 0, 2)))
        assertArrayEquals(intArrayOf(2, 1, 2, 1), CurlingRules.ranks(4, intArrayOf(1, 4)))
    }
}
