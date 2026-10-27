package com.siwoo.party.hub

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ScoreRules
import com.siwoo.party.common.Session
import com.siwoo.party.common.ui.GameButton
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
 * 등록된 모든 게임: 허브 카드 → 소개 → (첫 판) 규칙 카드 3장 → 게임 Activity 실행(플레이어 전달)
 * → 결과(순위) → 점수 반영·저장 → 결과 화면(꼴찌 벌칙 룰렛 버튼) → 허브.
 * 인원 변경에 따른 카드 활성/비활성, 랜덤 게임 후보도 확인.
 * (게임 안 진행은 각 게임 모듈의 화면 테스트가 끝까지 진행해 순위를 돌려주는 것까지 확인)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class AllGamesHubTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))

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

    private fun cards(hub: HubActivity): List<GameCardView> =
        findAll(hub.window.decorView, { it is GameCardView }).map { it as GameCardView }.filter { it.module != null }

    @Test
    fun everyGameRunsThroughHubAndScoresAccumulate() {
        for (n in 2..4) {
            val players = all.take(n)
            SessionStore(app).save(Session(players))
            val hub = Robolectric.buildActivity(HubActivity::class.java).setup().get()
            idle(500)
            val cards = cards(hub)
            assertEquals("모든 게임 카드", GameRegistry.games.size, cards.size)
            var played = 0
            for (card in cards) {
                val m = card.module!!
                assertEquals("${m.name} ${n}인 카드 활성/비활성", m.unavailableReason(n), card.unavailable)
                assertTrue("${m.name}: 아이콘·규칙 3장", m.rules.size == 3)
                if (card.unavailable != null) {
                    card.performClick(); idle(300)
                    assertTrue("못 하는 게임은 소개가 안 뜸", !hub.popups.isShowing)
                    assertTrue(m !in hub.eligibleGames())
                    continue
                }
                assertTrue(m in hub.eligibleGames())
                card.performClick(); idle(700)
                click(hub, if (m.hasSavedGame(hub, players)) "이어하기" else "시작")
                // 첫 판이면 규칙 카드 3장이 먼저 (다음 → 다음 → 시작!)
                val started0 = shadowOf(hub).nextStartedActivityForResult
                val started = if (started0 == null) {
                    click(hub, "다음"); click(hub, "다음"); click(hub, "시작!")
                    shadowOf(hub).nextStartedActivityForResult
                } else started0
                assertNotNull("${m.name} 실행", started)
                assertTrue("규칙을 봤다고 기록", PartySettings(app).rulesSeen(m.id))
                assertEquals(m.activityClass.name, started.intent.component!!.className)
                assertEquals(players.map { it.name }, Party.players(started.intent)!!.map { it.name })

                // 게임이 순위를 돌려줌 (마지막 사람이 1위)
                val ranks = IntArray(n) { n - it }
                val before = hub.session.score(players.last().id)
                shadowOf(hub).receiveResult(started.intent, Activity.RESULT_OK, Party.resultIntent(m.id, IntArray(n) { players[it].id }, ranks))
                val pts = ScoreRules.points(ranks)
                assertEquals(before + pts[n - 1], hub.session.score(players.last().id))
                assertEquals(hub.session.score(players.last().id), SessionStore(app).load()!!.score(players.last().id))
                val toResult = shadowOf(hub).nextStartedActivityForResult
                assertEquals(ResultActivity::class.java.name, toResult.intent.component!!.className)
                val res = Robolectric.buildActivity(ResultActivity::class.java, toResult.intent).setup().get()
                repeat(80) { if (!res.finished) idle(250) }
                assertTrue(res.finished)
                // 게임 후 벌칙(기본 켬) → 꼴찌 벌칙 룰렛 버튼
                assertNotNull(findAll(res.window.decorView, { it is GameButton && it.text == "꼴찌 벌칙 룰렛" }).firstOrNull())
                click(res, "허브로")
                assertEquals(Activity.RESULT_OK, shadowOf(res).resultCode)
                shadowOf(hub).receiveResult(toResult.intent, Activity.RESULT_OK, shadowOf(res).resultIntent)
                idle(300)
                assertNull("허브로 돌아오면 다시 실행하지 않음", shadowOf(hub).nextStartedActivityForResult)
                played++
            }
            assertEquals(played, hub.session.history.size)
            assertTrue("${n}인으로 할 수 있는 게임이 있어야 함", played > 0)
        }
    }

    @Test
    fun changingPlayerCountUpdatesCardsImmediately() {
        SessionStore(app).save(Session(all))
        val hub = Robolectric.buildActivity(HubActivity::class.java).setup().get()
        idle(500)
        fun state() = cards(hub).associate { it.module!!.id to (it.unavailable == null) }
        assertEquals(GameRegistry.games.associate { it.id to it.supports(4) }, state())
        // 4인 → 3인
        click(hub, "플레이어 변경")
        click(hub, "인원 변경")
        idle(400)
        click(hub, "3인")
        idle(800)
        click(hub, "완료")
        idle(600)
        assertEquals(3, hub.session.players.size)
        assertEquals(GameRegistry.games.associate { it.id to it.supports(3) }, state())
        assertEquals(GameRegistry.games.filter { it.supports(3) }, hub.eligibleGames())
        // 3인 → 2인
        click(hub, "플레이어 변경"); click(hub, "인원 변경"); idle(400); click(hub, "2인"); idle(800); click(hub, "완료"); idle(600)
        assertEquals(GameRegistry.games.associate { it.id to it.supports(2) }, state())
        // 점수는 이름으로 유지
        assertArrayEquals(intArrayOf(0, 1), hub.session.players.map { it.id }.toIntArray())
    }
}
