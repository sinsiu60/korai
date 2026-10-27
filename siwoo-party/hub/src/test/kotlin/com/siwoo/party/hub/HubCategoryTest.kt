package com.siwoo.party.hub

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.GroupState
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Session
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.BrowseRow
import com.siwoo.party.common.HubBrowse
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.ResultActivity
import com.siwoo.party.common.ui.RouletteView
import com.siwoo.party.common.ui.SessionStore
import com.siwoo.party.common.ui.SlotMachineView
import com.siwoo.party.common.ui.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * 단원 5 점검: 카테고리 탭·파티 존 표시, 랜덤 설정(카테고리/게임 스위치 연동·저장·조건 필터·0개 안내·연속 방지·슬롯머신),
 * 파티 설정 점수 반영 끔, 빠른 메뉴.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class HubCategoryTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))

    @Before
    fun setUp() {
        PartySettings(app).touchPoints = 5
    }

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
        idle(600)
    }

    private fun hub(n: Int = 4, session: Session = Session(players.take(n))): HubActivity {
        SessionStore(app).save(session)
        val h = Robolectric.buildActivity(HubActivity::class.java).setup().get()
        idle(600)
        return h
    }

    private fun cardIds(a: Activity) = findAll(a.window.decorView, { it is GameCardView }).map { (it as GameCardView).module.id }

    @Test
    fun `기존 11종이 올바른 카테고리와 파티 존에 표시된다`() {
        val expect = mapOf(
            "marble" to GameCategory.BOARD, "yut" to GameCategory.BOARD, "yacht" to GameCategory.BOARD, "boxes" to GameCategory.BOARD,
            "memory" to GameCategory.CARD_BRAIN,
            "reaction" to GameCategory.REFLEX, "tug" to GameCategory.REFLEX,
            "alkkagi" to GameCategory.TIMING, "pirate" to GameCategory.TIMING,
            "liar" to GameCategory.PARTY, "bomb" to GameCategory.PARTY,
        )
        for ((id, cat) in expect) assertEquals(id, cat, GameRegistry.byId(id)!!.category)
        PartySettings(app).hubFilters = emptySet()
        val h = hub()
        val tabs = h.tabs
        assertEquals("처음 탭 = 전체", HubBrowse.TAB_ALL, h.tab)
        // 전체: 파티 게임 없음
        assertFalse(h.shownIds().contains("liar"))
        assertFalse(h.shownIds().contains("bomb"))
        for ((k, cat) in GameCategory.HUB.withIndex()) {
            tabs.tap(HubBrowse.TAB_FIRST_GROUP + k); idle(400)
            val g = GameRegistry.inCategory(cat)
            // 카테고리 안은 방식 소제목(동시 터치 → 차례대로) 묶음
            assertEquals(cat.label, (g.filter { it.mode == PlayMode.SIMULTANEOUS } + g.filter { it.mode != PlayMode.SIMULTANEOUS }).map { it.id }, h.shownIds())
            assertTrue(h.shownRows().none { it is BrowseRow.Header })
        }
        tabs.tap(HubBrowse.TAB_ALL); idle(400)
        assertEquals(GameRegistry.hubGames.size, h.shownIds().size)
        // 파티 존
        assertTrue(h.partyEntrance.glowing)
        h.partyEntrance.performClick(); idle(300)
        assertEquals(PartyZoneActivity::class.java.name, shadowOf(h).nextStartedActivity.component!!.className)
        val pz = Robolectric.buildActivity(PartyZoneActivity::class.java).setup().get()
        idle(600)
        assertEquals(GameRegistry.partyGames.map { it.id }.sorted(), cardIds(pz).sorted())
        assertTrue(findAll(pz.window.decorView, { it is GameCardView }).all { (it as GameCardView).module.partyTags.isNotEmpty() })
    }

    @Test
    fun `랜덤 설정 - 카테고리 스위치와 게임 스위치 연동, 앱을 다시 켜도 유지`() {
        val h = hub()
        RandomSettingsPopup.show(h); idle(600)
        val sc = h.randomScreen!!
        val board = GameRegistry.inCategory(GameCategory.BOARD)
        assertEquals(GroupState.ON, sc.groupState(GameCategory.BOARD))
        assertEquals("파티 게임은 허브 랜덤 기본 끔", GroupState.OFF, sc.groupState(GameCategory.PARTY))
        val before = sc.count()
        // 카테고리 스위치 끄기 → 안의 게임 전부 꺼짐
        sc.toggleGroup(GameCategory.BOARD); idle(300)
        for (g in board) assertFalse(g.name, sc.isOn(g.id))
        assertEquals(GroupState.OFF, sc.groupState(GameCategory.BOARD))
        assertEquals(before - board.size, sc.count())
        assertTrue("실시간 개수 표시", sc.countText.text.toString().endsWith("${before - board.size}개"))
        // 게임 하나만 켜면 "일부"
        sc.toggle("yut"); idle(300)
        assertEquals(GroupState.PARTIAL, sc.groupState(GameCategory.BOARD))
        // 일부 상태에서 카테고리 스위치 → 전부 켬
        sc.toggleGroup(GameCategory.BOARD); idle(300)
        assertEquals(GroupState.ON, sc.groupState(GameCategory.BOARD))
        // 다시 일부로 만들어 두고 앱 재시작
        sc.toggle("marble"); idle(300)
        assertFalse(PartySettings(app).hubRandom(GameRegistry.byId("marble")!!))
        val h2 = hub()
        val marbleCard = h2.cardFor("marble")!!
        assertFalse("카드 주사위 배지도 꺼짐", marbleCard.randomOn)
        RandomSettingsPopup.show(h2); idle(600)
        assertEquals(GroupState.PARTIAL, h2.randomScreen!!.groupState(GameCategory.BOARD))
        assertFalse("marble" in h2.eligibleGames().map { it.id })
    }

    @Test
    fun `랜덤 후보 - 인원과 기기 조건으로 거르고 0개면 안내`() {
        val s = PartySettings(app)
        // 2점 터치 기기, 4인 → 4명 동시 터치 게임은 빠짐
        s.touchPoints = 2
        val h = hub(4)
        val ids = h.eligibleGames().map { it.id }
        assertFalse("reaction" in ids)
        assertFalse("tug" in ids)
        assertTrue("marble" in ids)
        // 3인으로 못 하는 게임(알까기 2인 전용) 제외
        val h3 = hub(3)
        assertFalse("alkkagi" in h3.eligibleGames().map { it.id })
        // 전부 끄면 0개 → 안내 + [랜덤 설정 열기]
        for (m in GameRegistry.games) s.setHubRandom(m.id, false)
        click(h3, "🎲 랜덤 게임")
        assertTrue(h3.popups.isShowing)
        click(h3, "랜덤 설정 열기")
        assertNotNull("랜덤 설정이 열림", h3.randomScreen)
        assertEquals(0, h3.randomScreen!!.count())
    }

    @Test
    fun `같은 게임 연속 방지 - 직전 게임 제외, 후보가 하나면 예외`() {
        val s = PartySettings(app)
        val session = Session(players)
        session.applyResult("yut", intArrayOf(0, 1, 2, 3), intArrayOf(1, 2, 3, 4))
        val h = hub(session = session)
        assertFalse("yut" in h.eligibleGames().map { it.id })
        s.avoidRepeat = false
        assertTrue("yut" in h.eligibleGames().map { it.id })
        s.avoidRepeat = true
        for (m in GameRegistry.games) s.setHubRandom(m.id, m.id == "yut")
        assertEquals(listOf("yut"), h.eligibleGames().map { it.id })
    }

    @Test
    fun `후보가 8개 이하면 원형 룰렛, 9개 이상이면 슬롯머신`() {
        val s = PartySettings(app)
        val h = hub(4)
        // 보드게임만 켜서 8개 이하로
        for (m in GameRegistry.games) s.setHubRandom(m.id, m.category == GameCategory.BOARD)
        assertTrue(h.eligibleGames().size in 1..8)
        click(h, "🎲 랜덤 게임")
        assertTrue(h.randomView is RouletteView)
        h.popups.dismissAll(); idle(300)
        for (m in GameRegistry.games) s.setHubRandom(m.id, true)
        assertTrue(h.eligibleGames().size >= 9)
        click(h, "🎲 랜덤 게임")
        val slot = h.randomView as SlotMachineView
        click(h, "돌리기")
        repeat(100) { if (slot.spinning) idle(200) }
        idle(1500)
        // 뽑힌 게임 소개 팝업
        assertNotNull(findAll(h.window.decorView, { it is GameButton && it.text.toString() == "시작" }).firstOrNull())
    }

    @Test
    fun `파티 설정 점수 반영 끔이면 누적 점수에 더하지 않는다`() {
        val s = PartySettings(app)
        s.partyScore = false
        s.partyPenalty = false
        SessionStore(app).save(Session(players))
        val pz = Robolectric.buildActivity(PartyZoneActivity::class.java).setup().get()
        idle(600)
        // 파티 랜덤 후보는 파티 게임만
        assertTrue(pz.partyCandidates().all { it.category == GameCategory.PARTY })
        val m = GameHostActivity::class.java.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, android.content.Intent::class.java)
        m.isAccessible = true
        m.invoke(pz, GameHostActivity.REQ_GAME, Activity.RESULT_OK, Party.resultIntent("liar", intArrayOf(0, 1, 2, 3), intArrayOf(1, 2, 3, 4)))
        val saved = SessionStore(app).load()!!
        assertEquals(0, saved.score(0))
        assertEquals(1, saved.history.size)
        assertFalse(saved.history[0].scored)
        val res = shadowOf(pz).nextStartedActivityForResult.intent
        assertFalse(res.getBooleanExtra(ResultActivity.EXTRA_SCORED, true))
        assertFalse(res.getBooleanExtra(ResultActivity.EXTRA_PENALTY, true))
        // 결과 화면: 점수 대신 순위, 벌칙 룰렛 없음
        val ra = Robolectric.buildActivity(ResultActivity::class.java, res).setup().get()
        idle(3000)
        assertTrue(findAll(ra.window.decorView, { it is GameButton && it.text == "꼴찌 벌칙 룰렛" }).isEmpty())
        // 켜면 더한다
        s.partyScore = true
        m.invoke(pz, GameHostActivity.REQ_GAME, Activity.RESULT_OK, Party.resultIntent("liar", intArrayOf(0, 1, 2, 3), intArrayOf(1, 2, 3, 4)))
        assertTrue(SessionStore(app).load()!!.score(0) > 0)
    }

    @Test
    fun `카드 길게 누르기 - 랜덤 포함 끄기와 규칙 보기`() {
        val h = hub()
        val card = h.cardFor("boxes")!!
        assertTrue(card.randomOn)
        card.performLongClick(); idle(500)
        click(h, "🎲 랜덤에서 빼기")
        assertFalse(card.randomOn)
        assertFalse(PartySettings(app).hubRandom(card.module))
        click(h, "규칙 보기")
        assertTrue(com.siwoo.party.common.ui.RulesPager.isShowing(h.popups))
        assertEquals(Theme.GOLD, Theme.GOLD) // (테마 상수 접근 확인)
    }
}
