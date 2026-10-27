package com.siwoo.party.hub

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ScoreRules
import com.siwoo.party.common.Session
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.HubFilter
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.ResultActivity
import com.siwoo.party.common.ui.SessionStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * 등록된 모든 게임: 허브 카드(파티 게임은 파티 존 카드) → 소개 → (첫 판) 규칙 카드 3장 → 게임 Activity 실행(플레이어 전달)
 * → 결과(순위) → 점수 반영·저장 → 결과 화면(꼴찌 벌칙 룰렛 버튼) → 허브.
 * 인원 변경에 따른 카드 활성/비활성, 랜덤 후보도 확인.
 * (게임 안 진행은 각 게임 모듈의 화면 테스트가 끝까지 진행해 순위를 돌려주는 것까지 확인)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class AllGamesHubTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))

    @Before
    fun device() {
        // Robolectric 기기는 멀티터치 기능이 없다고 나온다 → 실제 폰처럼 5점 터치로 둔다
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

    private fun cards(a: Activity): List<GameCardView> =
        findAll(a.window.decorView, { it is GameCardView }).map { it as GameCardView }

    @Test
    fun everyGameRunsThroughHubAndScoresAccumulate() {
        for (n in 2..4) {
            val players = all.take(n)
            SessionStore(app).save(Session(players))
            PartySettings(app).hubFilters = emptySet() // 못 하는 게임 카드도 보이게 (필터 끔)
            val hub = Robolectric.buildActivity(HubActivity::class.java).setup().get()
            idle(500)
            val pz = Robolectric.buildActivity(PartyZoneActivity::class.java).setup().get()
            idle(500)
            // [전체] 탭: 카테고리 → 방식(동시 터치 / 차례대로) 묶음 순
            val expected = GameCategory.HUB.flatMap { c ->
                val g = GameRegistry.inCategory(c)
                g.filter { it.mode == PlayMode.SIMULTANEOUS } + g.filter { it.mode != PlayMode.SIMULTANEOUS }
            }.map { it.id }
            assertEquals("허브 [전체] 탭 = 파티 존이 아닌 모든 게임", expected, hub.shownIds())
            assertEquals("파티 존 = 파티 게임", GameRegistry.partyGames.map { it.id }, cards(pz).map { it.module.id })
            assertEquals(GameRegistry.games.size, hub.shownIds().size + cards(pz).size)
            var played = 0
            val cardGetters = hub.shownIds().map { { hub.cardFor(it)!! } } + cards(pz).map { c -> { c } }
            for (getCard in cardGetters) {
                val card = getCard()
                val m = card.module
                val host = card.context as GameHostActivity
                assertEquals("${m.name} ${n}인 카드 활성/비활성", m.unavailableReason(n), card.unavailable)
                assertTrue("${m.name}: 규칙 3장", m.rules.size == 3)
                val playable = host.randomCandidates(GameRegistry.games, partyRandom = false, avoidRepeat = false)
                if (card.unavailable != null) {
                    card.performClick(); idle(300)
                    assertTrue("못 하는 게임은 소개가 안 뜸", !host.popups.isShowing)
                    assertTrue(m !in playable)
                    continue
                }
                // 허브 랜덤: 일반 게임은 기본 포함, 파티 게임은 기본 빠짐
                assertEquals(m.category != GameCategory.PARTY, m in playable)
                card.performClick(); idle(700)
                click(host, if (m.hasSavedGame(host, players)) "이어하기" else "시작")
                // 첫 판이면 규칙 카드 3장이 먼저 (다음 → 다음 → 시작!)
                val started0 = shadowOf(host).nextStartedActivityForResult
                val started = if (started0 == null) {
                    click(host, "다음"); click(host, "다음"); click(host, "시작!")
                    shadowOf(host).nextStartedActivityForResult
                } else started0
                assertNotNull("${m.name} 실행", started)
                assertTrue("규칙을 봤다고 기록", PartySettings(app).rulesSeen(m.id))
                assertEquals(m.activityClass.name, started.intent.component!!.className)
                assertEquals(players.map { it.name }, Party.players(started.intent)!!.map { it.name })

                // 게임이 순위를 돌려줌 (마지막 사람이 1위)
                val ranks = IntArray(n) { n - it }
                val before = host.session.score(players.last().id)
                shadowOf(host).receiveResult(started.intent, Activity.RESULT_OK, Party.resultIntent(m.id, IntArray(n) { players[it].id }, ranks))
                val pts = ScoreRules.points(ranks)
                assertEquals("${m.name} 점수 반영 (파티 존 기본 켬)", before + pts[n - 1], host.session.score(players.last().id))
                assertEquals(host.session.score(players.last().id), SessionStore(app).load()!!.score(players.last().id))
                val toResult = shadowOf(host).nextStartedActivityForResult
                assertEquals(ResultActivity::class.java.name, toResult.intent.component!!.className)
                val res = Robolectric.buildActivity(ResultActivity::class.java, toResult.intent).setup().get()
                repeat(80) { if (!res.finished) idle(250) }
                assertTrue(res.finished)
                // 게임 후 벌칙(기본 켬) → 꼴찌 벌칙 룰렛 버튼
                assertNotNull(findAll(res.window.decorView, { it is GameButton && it.text == "꼴찌 벌칙 룰렛" }).firstOrNull())
                click(res, "허브로")
                assertEquals(Activity.RESULT_OK, shadowOf(res).resultCode)
                shadowOf(host).receiveResult(toResult.intent, Activity.RESULT_OK, shadowOf(res).resultIntent)
                idle(300)
                assertNull("허브로 돌아오면 다시 실행하지 않음", shadowOf(host).nextStartedActivityForResult)
                played++
                // 다른 화면이 저장한 점수를 다음 화면이 다시 읽도록 (실제로는 onResume)
                for (a in listOf<GameHostActivity>(hub, pz)) if (a !== host) a.reloadSession()
            }
            assertEquals(played, SessionStore(app).load()!!.history.size)
            assertTrue("${n}인으로 할 수 있는 게임이 있어야 함", played > 0)
        }
    }

    @Test
    fun changingPlayerCountUpdatesCardsImmediately() {
        SessionStore(app).save(Session(all))
        val settings = PartySettings(app)
        settings.hubFilters = emptySet()
        val hub = Robolectric.buildActivity(HubActivity::class.java).setup().get()
        idle(500)
        fun state() = hub.shownIds().associateWith { hub.cardFor(it)!!.unavailable == null }
        assertEquals(GameRegistry.hubGames.associate { it.id to it.supports(4) }, state())
        // [지금 인원 가능만] 켜면 못 하는 게임은 목록에서 빠짐
        hub.filterBar.toggle(HubFilter.PLAYABLE_NOW); idle(200)
        assertEquals(GameRegistry.hubGames.filter { it.supports(4) }.map { it.id }.toSet(), hub.shownIds().toSet())
        hub.filterBar.toggle(HubFilter.PLAYABLE_NOW); idle(200)
        // 4인 → 3인
        click(hub, "플레이어 변경")
        click(hub, "인원 변경")
        idle(400)
        click(hub, "3인")
        idle(800)
        click(hub, "완료")
        idle(600)
        assertEquals(3, hub.session.players.size)
        assertEquals(GameRegistry.hubGames.associate { it.id to it.supports(3) }, state())
        assertEquals("허브 랜덤 후보 = 3인으로 되는 일반 게임", GameRegistry.hubGames.filter { it.supports(3) }, hub.eligibleGames())
        // 3인 → 2인
        click(hub, "플레이어 변경"); click(hub, "인원 변경"); idle(400); click(hub, "2인"); idle(800); click(hub, "완료"); idle(600)
        assertEquals(GameRegistry.hubGames.associate { it.id to it.supports(2) }, state())
        assertEquals(GameRegistry.hubGames.filter { it.supports(2) }, hub.eligibleGames())
        hub.filterBar.toggle(HubFilter.PLAYABLE_NOW); idle(200)
        assertEquals(GameRegistry.hubGames.filter { it.supports(2) }.map { it.id }.toSet(), hub.shownIds().toSet())
        // 점수는 이름으로 유지
        assertArrayEquals(intArrayOf(0, 1), hub.session.players.map { it.id }.toIntArray())
    }
}
