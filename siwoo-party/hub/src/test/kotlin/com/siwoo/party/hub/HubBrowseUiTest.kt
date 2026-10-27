package com.siwoo.party.hub

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.HubBrowse
import com.siwoo.party.common.HubFilter
import com.siwoo.party.common.HubSort
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Session
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.IconCache
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * 단원 12 허브 탐색: 탭(즐겨찾기·전체·카테고리)·필터 칩·정렬·이름/초성 검색·즐겨찾기,
 * 앱을 다시 켜도 그대로 유지되는지, 랜덤 설정 검색.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class HubBrowseUiTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun findAll(v: View, pred: (View) -> Boolean, out: MutableList<View> = ArrayList()): MutableList<View> {
        if (v.visibility != View.VISIBLE) return out
        if (pred(v)) out.add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) findAll(v.getChildAt(i), pred, out)
        return out
    }

    private fun click(a: Activity, label: String) {
        val b = findAll(a.window.decorView, { it is GameButton && it.text.toString() == label && it.isEnabled }).lastOrNull() as GameButton?
            ?: fail("버튼 없음: $label / ${findAll(a.window.decorView, { it is GameButton }).map { (it as GameButton).text }}")
        (b as GameButton).performClick()
        idle(500)
    }

    private fun hub(n: Int = 4): HubActivity {
        if (SessionStore(app).load() == null) SessionStore(app).save(Session(players.take(n)))
        val h = Robolectric.buildActivity(HubActivity::class.java).setup().get()
        idle(600)
        return h
    }

    @Test
    fun `필터 칩 - 기본은 지금 인원 가능만, 동시 터치·차례대로·2인 전용·10분 이하`() {
        SessionStore(app).save(Session(players)) // 4인
        val h = hub()
        assertEquals(setOf(HubFilter.PLAYABLE_NOW), h.filterBar.selected)
        assertEquals("4인이면 2인 전용 게임은 안 보임", GameRegistry.hubGames.filter { it.supports(4) }.map { it.id }.toSet(), h.shownIds().toSet())
        h.filterBar.toggle(HubFilter.PLAYABLE_NOW); idle(200)
        h.filterBar.toggle(HubFilter.TWO_ONLY); idle(200)
        val two = h.shownIds()
        assertTrue(two.isNotEmpty())
        assertTrue(two.all { GameRegistry.byId(it)!!.playerCounts == 2..2 })
        h.filterBar.toggle(HubFilter.TWO_ONLY); idle(200)
        h.filterBar.toggle(HubFilter.SIMULTANEOUS); idle(200)
        assertTrue(h.shownIds().all { GameRegistry.byId(it)!!.mode == com.siwoo.party.common.ui.PlayMode.SIMULTANEOUS })
        h.filterBar.toggle(HubFilter.TURNS); idle(200)
        assertEquals("둘 다 켜면 전부", GameRegistry.hubGames.size, h.shownIds().size)
        h.filterBar.toggle(HubFilter.SIMULTANEOUS); idle(200)
        h.filterBar.toggle(HubFilter.TURNS); idle(200)
        h.filterBar.toggle(HubFilter.SHORT); idle(200)
        assertTrue(h.shownIds().all { HubBrowse.parseMinutes(GameRegistry.byId(it)!!.minutes)[1] <= 10 })
        assertFalse("20~40분 마블은 빠짐", "marble" in h.shownIds())
    }

    @Test
    fun `초성 검색 - 탭과 상관없이 찾고 검색어도 유지`() {
        val h = hub()
        h.tabs.tap(HubBrowse.TAB_FIRST_GROUP + 0); idle(300)
        h.setQuery("ㅇㄲㄱ"); idle(300)
        assertTrue(h.searchOpen)
        assertTrue("4인이면 2인 전용 알까기는 필터에 걸림", h.shownIds().isEmpty())
        h.filterBar.toggle(HubFilter.PLAYABLE_NOW); idle(200) // 알까기는 2인 전용 → 필터 끄면 보임
        assertEquals(listOf("alkkagi"), h.shownIds())
        h.setQuery("반응"); idle(300)
        assertEquals(listOf("reaction"), h.shownIds())
        h.setQuery("ㅁㄱㅎ"); idle(300)
        assertEquals("'무궁화 꽃이…' 초성 부분", listOf("redlight"), h.shownIds())
        // 다시 켜도 검색어 유지
        val h2 = hub()
        assertTrue(h2.searchOpen)
        assertEquals(listOf("redlight"), h2.shownIds())
        // 검색 닫기 → 원래 탭(보드게임)
        h2.setQuery(""); idle(300)
        assertFalse(h2.searchOpen)
        assertEquals(HubBrowse.TAB_FIRST_GROUP + 0, h2.tab)
        assertEquals(GameRegistry.inCategory(GameCategory.BOARD).map { it.id }.toSet(), h2.shownIds().toSet())
    }

    @Test
    fun `즐겨찾기 - 빠른 메뉴 별, 즐겨찾기 탭, 탭 수, 재시작 후 유지`() {
        PartySettings(app).hubFilters = emptySet()
        val h = hub()
        h.tabs.tap(HubBrowse.TAB_FAVORITES); idle(300)
        assertTrue("처음엔 비어 있음", h.shownIds().isEmpty())
        h.tabs.tap(HubBrowse.TAB_ALL); idle(300)
        assertTrue(h.longPress("yacht")); idle(500)
        click(h, "☆ 즐겨찾기")
        assertTrue(PartySettings(app).isFavorite("yacht"))
        assertTrue("카드 모서리 별", h.cardFor("yacht")!!.favorite)
        h.popups.dismissAll(); idle(300)
        assertTrue(h.longPress("tug")); idle(500)
        click(h, "☆ 즐겨찾기")
        h.popups.dismissAll(); idle(300)
        h.tabs.tap(HubBrowse.TAB_FAVORITES); idle(300)
        assertEquals(setOf("yacht", "tug"), h.shownIds().toSet())
        // 다시 켜면 즐겨찾기 탭 그대로
        val h2 = hub()
        assertEquals(HubBrowse.TAB_FAVORITES, h2.tab)
        assertEquals(setOf("yacht", "tug"), h2.shownIds().toSet())
        // 해제
        assertTrue(h2.longPress("yacht")); idle(500)
        click(h2, "★ 즐겨찾기 해제")
        h2.popups.dismissAll(); idle(300)
        assertEquals(listOf("tug"), h2.shownIds())
    }

    @Test
    fun `정렬 - 이름순 · 많이 한 순(플레이 기록) · 짧은 시간순, 재시작 후 유지`() {
        PartySettings(app).hubFilters = emptySet()
        val h = hub()
        h.tabs.tap(HubBrowse.TAB_FIRST_GROUP + 0); idle(300) // 보드게임 (모두 차례대로)
        h.setSort(HubSort.NAME); idle(200)
        val board = GameRegistry.inCategory(GameCategory.BOARD)
        assertEquals(board.map { it.name }.sorted(), h.shownIds().map { GameRegistry.byId(it)!!.name })
        // 게임을 끝까지 하면 플레이 횟수 기록 → 많이 한 순
        val m = GameHostActivity::class.java.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, android.content.Intent::class.java)
        m.isAccessible = true
        repeat(2) { m.invoke(h, GameHostActivity.REQ_GAME, Activity.RESULT_OK, Party.resultIntent("yacht", intArrayOf(0, 1, 2, 3), intArrayOf(1, 2, 3, 4))) }
        m.invoke(h, GameHostActivity.REQ_GAME, Activity.RESULT_OK, Party.resultIntent("boxes", intArrayOf(0, 1, 2, 3), intArrayOf(1, 2, 3, 4)))
        assertEquals(2, PartySettings(app).plays("yacht"))
        h.setSort(HubSort.PLAYED); idle(200)
        assertEquals(listOf("yacht", "boxes"), h.shownIds().take(2))
        h.setSort(HubSort.SHORT); idle(200)
        val mins = h.shownIds().map { HubBrowse.parseMinutes(GameRegistry.byId(it)!!.minutes)[0] }
        assertEquals(mins.sorted(), mins)
        val h2 = hub()
        assertEquals(HubSort.SHORT, h2.filterBar.sort)
        assertEquals(h.shownIds(), h2.shownIds())
    }

    @Test
    fun `탭 옆 게임 수와 아이콘 캐시`() {
        val h = hub()
        val labels = h.tabs.labels
        assertEquals(6, labels.size)
        assertEquals("★ 즐겨찾기", labels[0])
        assertEquals("전체", labels[1])
        // 같은 크기 아이콘은 한 번만 그림 (다시 그려도 늘지 않음)
        val before = IconCache.drawn
        h.tabs.tap(HubBrowse.TAB_FIRST_GROUP + 1); idle(300)
        h.tabs.tap(HubBrowse.TAB_ALL); idle(300)
        h.tabs.tap(HubBrowse.TAB_FIRST_GROUP + 1); idle(300)
        val after = IconCache.drawn
        h.tabs.tap(HubBrowse.TAB_ALL); idle(300)
        h.tabs.tap(HubBrowse.TAB_FIRST_GROUP + 1); idle(300)
        assertEquals("두 번째부터는 캐시", after, IconCache.drawn)
        assertTrue(after >= before)
        assertTrue(IconCache.bytes() <= 4 * 1024 * 1024)
    }

    @Test
    fun `랜덤 설정 - 모든 게임 스위치, 검색, 펼치기`() {
        val h = hub()
        RandomSettingsPopup.show(h); idle(600)
        val sc = h.randomScreen!!
        assertEquals(GameRegistry.games.map { it.id }.toSet(), sc.ids.toSet())
        assertTrue("처음엔 접혀 있음", sc.shownIds().isEmpty())
        sc.expand(GameCategory.REFLEX, true); idle(200)
        assertEquals(GameRegistry.inCategory(GameCategory.REFLEX).map { it.id }, sc.shownIds())
        sc.setQuery("ㄸㅈㅊ"); idle(200)
        assertEquals("초성 검색은 접힘과 상관없이", listOf("ddakji"), sc.shownIds())
        sc.setQuery(""); idle(200)
        // 새 게임은 기본 켬 (파티 게임은 허브 랜덤 기본 끔)
        for (g in GameRegistry.games) assertEquals(g.id, g.category != GameCategory.PARTY, sc.isOn(g.id))
    }
}
