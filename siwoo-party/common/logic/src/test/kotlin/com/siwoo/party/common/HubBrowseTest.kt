package com.siwoo.party.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubBrowseTest {

    private fun e(
        id: String, name: String, group: Int, sim: Boolean = false, two: Boolean = false, now: Boolean = true,
        min: Int = 5, max: Int = min, order: Int = 0, plays: Int = 0, fav: Boolean = false,
    ) = BrowseEntry(id, name, group, sim, two, now, min, max, order, plays, fav)

    private val list = listOf(
        e("marble", "시우 마블", 0, min = 20, max = 40, order = 0, plays = 1),
        e("yut", "윷놀이", 0, min = 15, order = 1, plays = 5, fav = true),
        e("janggi", "장기", 0, two = true, now = false, min = 15, order = 2),
        e("memory", "짝 맞추기", 1, min = 4, order = 3, plays = 2),
        e("reaction", "반응속도 결투", 2, sim = true, min = 2, order = 4, plays = 9, fav = true),
        e("airhockey", "에어하키", 2, sim = true, two = true, now = false, min = 3, order = 5),
        e("tug", "연타 줄다리기", 2, sim = true, min = 1, order = 6),
        e("alkkagi", "알까기", 3, two = true, now = false, min = 5, order = 7),
    )

    private fun ids(rows: List<BrowseRow>) = rows.filterIsInstance<BrowseRow.Item>().map { it.entry.id }

    @Test
    fun `초성 검색`() {
        assertTrue(Chosung.matches("에어하키", "ㅇㅎㅋ"))
        assertTrue(Chosung.matches("에어하키", "ㅇㅇㅎㅋ"))
        assertTrue(Chosung.matches("에어하키", "어하"))
        assertTrue(Chosung.matches("에어하키", "ㅇ하ㅋ"))
        assertTrue("공백 무시", Chosung.matches("100m 달리기", "ㄷㄹ"))
        assertTrue(Chosung.matches("100m 달리기", "100M"))
        assertTrue(Chosung.matches("뱀과 사다리", "뱀과사"))
        assertTrue("빈 검색어", Chosung.matches("장기", " "))
        assertFalse(Chosung.matches("에어하키", "ㅎㅇ"))
        assertFalse(Chosung.matches("장기", "장기판"))
        assertFalse("초성이 아닌 자모는 그대로 비교", Chosung.matches("에어하키", "ㅐ"))
    }

    @Test
    fun `검색은 탭과 상관없이 모든 카테고리`() {
        val rows = HubBrowse.rows(list, HubBrowse.TAB_FIRST_GROUP + 0, emptySet(), HubSort.DEFAULT, "ㅇㅎㅋ")
        assertEquals(listOf("airhockey"), ids(rows))
        assertTrue(rows.first() is BrowseRow.Header)
    }

    @Test
    fun `지금 인원 가능만`() {
        val r = HubBrowse.rows(list, HubBrowse.TAB_ALL, setOf(HubFilter.PLAYABLE_NOW), HubSort.DEFAULT, "")
        assertEquals(listOf("marble", "yut", "memory", "reaction", "tug"), ids(r))
    }

    @Test
    fun `2인 전용 · 동시 터치 · 차례대로 · 10분 이하`() {
        assertEquals(listOf("janggi", "airhockey", "alkkagi"), ids(HubBrowse.rows(list, HubBrowse.TAB_ALL, setOf(HubFilter.TWO_ONLY), HubSort.DEFAULT, "")))
        assertEquals(listOf("reaction", "airhockey", "tug"), ids(HubBrowse.rows(list, HubBrowse.TAB_ALL, setOf(HubFilter.SIMULTANEOUS), HubSort.DEFAULT, "")))
        assertEquals(listOf("marble", "yut", "janggi", "memory", "alkkagi"), ids(HubBrowse.rows(list, HubBrowse.TAB_ALL, setOf(HubFilter.TURNS), HubSort.DEFAULT, "")))
        assertEquals("둘 다 켜면 전부", list.size, ids(HubBrowse.rows(list, HubBrowse.TAB_ALL, setOf(HubFilter.SIMULTANEOUS, HubFilter.TURNS), HubSort.DEFAULT, "")).size)
        assertEquals(listOf("memory", "reaction", "airhockey", "tug", "alkkagi"), ids(HubBrowse.rows(list, HubBrowse.TAB_ALL, setOf(HubFilter.SHORT), HubSort.DEFAULT, "")))
    }

    @Test
    fun `정렬은 소제목 묶음 안에서`() {
        val name = HubBrowse.rows(list, HubBrowse.TAB_FIRST_GROUP + 2, emptySet(), HubSort.NAME, "")
        assertEquals(listOf("reaction", "airhockey", "tug"), ids(name))
        val played = HubBrowse.rows(list, HubBrowse.TAB_FIRST_GROUP + 0, emptySet(), HubSort.PLAYED, "")
        assertEquals(listOf("yut", "marble", "janggi"), ids(played))
        val short = HubBrowse.rows(list, HubBrowse.TAB_FIRST_GROUP + 2, emptySet(), HubSort.SHORT, "")
        assertEquals(listOf("tug", "reaction", "airhockey"), ids(short))
    }

    @Test
    fun `즐겨찾기 탭과 소제목`() {
        val rows = HubBrowse.rows(list, HubBrowse.TAB_FAVORITES, emptySet(), HubSort.DEFAULT, "")
        assertEquals(listOf("yut", "reaction"), ids(rows))
        // 카테고리 제목 → 소제목 → 카드
        assertTrue(rows[0] is BrowseRow.Header)
        assertTrue(rows[1] is BrowseRow.Sub && !(rows[1] as BrowseRow.Sub).simultaneous)
        val subs = rows.filterIsInstance<BrowseRow.Sub>()
        assertEquals(2, subs.size)
        // 카테고리 탭에는 카테고리 제목 없이 소제목만, 동시 터치가 먼저
        val reflex = HubBrowse.rows(list + e("pirate", "해적 룰렛", 2, order = 9), HubBrowse.TAB_FIRST_GROUP + 2, emptySet(), HubSort.DEFAULT, "")
        assertTrue(reflex.none { it is BrowseRow.Header })
        assertEquals(true, (reflex[0] as BrowseRow.Sub).simultaneous)
        assertEquals(3, (reflex[0] as BrowseRow.Sub).count)
        assertEquals("pirate", ids(reflex).last())
    }

    @Test
    fun `시간 글자 읽기`() {
        assertArrayEquals(intArrayOf(20, 40), HubBrowse.parseMinutes("20~40분"))
        assertArrayEquals(intArrayOf(5, 5), HubBrowse.parseMinutes("5분/판"))
        assertArrayEquals(intArrayOf(10, 10), HubBrowse.parseMinutes("10분"))
        assertArrayEquals(intArrayOf(0, 0), HubBrowse.parseMinutes("?"))
    }
}
