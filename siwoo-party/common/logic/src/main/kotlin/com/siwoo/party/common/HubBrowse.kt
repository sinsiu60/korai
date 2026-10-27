package com.siwoo.party.common

/** 허브 정렬 */
enum class HubSort(val label: String) {
    DEFAULT("기본"),
    NAME("이름순"),
    PLAYED("많이 한 순"),
    SHORT("짧은 시간순"),
}

/** 허브 필터 칩 */
enum class HubFilter(val label: String) {
    PLAYABLE_NOW("지금 인원 가능만"),
    TWO_ONLY("2인 전용"),
    SIMULTANEOUS("동시 터치"),
    TURNS("차례대로"),
    SHORT("10분 이하"),
    ;

    companion object {
        /** 처음 켜져 있는 필터 */
        val DEFAULT: Set<HubFilter> = setOf(PLAYABLE_NOW)
    }
}

/**
 * 허브 목록에 올라가는 게임 하나 (화면과 상관없는 정보만).
 * @param group 카테고리 번호 (허브 탭 순서, 0 부터)
 * @param order 등록 순서 (기본 정렬)
 */
class BrowseEntry(
    val id: String,
    val name: String,
    val group: Int,
    val simultaneous: Boolean,
    val twoOnly: Boolean,
    val playableNow: Boolean,
    val minutesMin: Int,
    val minutesMax: Int,
    val order: Int,
    val plays: Int,
    val favorite: Boolean,
)

/** 허브 목록 한 줄: 카테고리 제목 / 방식 소제목 / 게임 카드 */
sealed class BrowseRow {
    class Header(val group: Int, val count: Int) : BrowseRow()
    class Sub(val group: Int, val simultaneous: Boolean, val count: Int) : BrowseRow()
    class Item(val entry: BrowseEntry) : BrowseRow()
}

/**
 * 허브 탐색: 탭(즐겨찾기 / 전체 / 카테고리) · 필터 칩 · 정렬 · 이름 검색(초성 포함) → 목록 줄.
 * - 검색어가 있으면 탭과 상관없이 모든 카테고리에서 찾는다 (필터는 그대로 적용)
 * - 즐겨찾기·전체·검색 결과는 카테고리 제목 아래 묶고, 각 카테고리 안은 방식 소제목(동시 터치 / 차례대로)으로 묶는다
 * - 정렬은 소제목 묶음 안에서
 * - [동시 터치]와 [차례대로]를 둘 다 켜면 둘 다 보인다 (하나만 켜면 그 방식만)
 */
object HubBrowse {
    const val TAB_FAVORITES = 0
    const val TAB_ALL = 1
    /** 카테고리 탭 번호 = TAB_FIRST_GROUP + group */
    const val TAB_FIRST_GROUP = 2
    const val SHORT_MINUTES = 10

    fun passes(e: BrowseEntry, filters: Set<HubFilter>): Boolean {
        if (HubFilter.PLAYABLE_NOW in filters && !e.playableNow) return false
        if (HubFilter.TWO_ONLY in filters && !e.twoOnly) return false
        val sim = HubFilter.SIMULTANEOUS in filters
        val turns = HubFilter.TURNS in filters
        if (sim != turns) {
            if (sim && !e.simultaneous) return false
            if (turns && e.simultaneous) return false
        }
        if (HubFilter.SHORT in filters && e.minutesMax > SHORT_MINUTES) return false
        return true
    }

    /** 탭·필터·검색어로 고르기 (정렬 전) */
    fun select(entries: List<BrowseEntry>, tab: Int, filters: Set<HubFilter>, query: String): List<BrowseEntry> {
        val q = query.trim()
        return entries.filter { e ->
            val inTab = when {
                q.isNotEmpty() -> Chosung.matches(e.name, q)
                tab == TAB_FAVORITES -> e.favorite
                tab == TAB_ALL -> true
                else -> e.group == tab - TAB_FIRST_GROUP
            }
            inTab && passes(e, filters)
        }
    }

    fun comparator(sort: HubSort): Comparator<BrowseEntry> = when (sort) {
        HubSort.DEFAULT -> compareBy { it.order }
        HubSort.NAME -> compareBy<BrowseEntry> { it.name }.thenBy { it.order }
        HubSort.PLAYED -> compareByDescending<BrowseEntry> { it.plays }.thenBy { it.order }
        HubSort.SHORT -> compareBy<BrowseEntry> { it.minutesMin }.thenBy { it.minutesMax }.thenBy { it.order }
    }

    /** 화면에 그릴 줄 목록 */
    fun rows(entries: List<BrowseEntry>, tab: Int, filters: Set<HubFilter>, sort: HubSort, query: String): List<BrowseRow> {
        val picked = select(entries, tab, filters, query)
        val groupHeaders = query.isNotBlank() || tab == TAB_FAVORITES || tab == TAB_ALL
        val out = ArrayList<BrowseRow>(picked.size + 16)
        val cmp = comparator(sort)
        for (g in picked.map { it.group }.distinct().sorted()) {
            val inGroup = picked.filter { it.group == g }
            if (groupHeaders) out.add(BrowseRow.Header(g, inGroup.size))
            for (sim in booleanArrayOf(true, false)) {
                val part = inGroup.filter { it.simultaneous == sim }.sortedWith(cmp)
                if (part.isEmpty()) continue
                out.add(BrowseRow.Sub(g, sim, part.size))
                for (e in part) out.add(BrowseRow.Item(e))
            }
        }
        return out
    }

    /** "20~40분" → (20, 40), "5분/판" → (5, 5), 숫자가 없으면 (0, 0) */
    fun parseMinutes(text: String): IntArray {
        val nums = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()
        if (nums.isEmpty()) return intArrayOf(0, 0)
        return intArrayOf(nums.first(), if (text.contains('~') && nums.size >= 2) nums[1] else nums.first())
    }
}
