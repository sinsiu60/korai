package com.siwoo.party.hub

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Session
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.ResultActivity
import com.siwoo.party.common.ui.RouletteView
import com.siwoo.party.common.ui.SessionStore
import com.siwoo.party.games.marble.MarbleModule
import com.siwoo.party.games.marble.data.CardData
import com.siwoo.party.games.marble.logic.Dice
import com.siwoo.party.games.marble.model.GameState
import com.siwoo.party.games.marble.model.Player
import com.siwoo.party.games.marble.ui.GameStorage
import com.siwoo.party.games.marble.ui.MainActivity
import com.siwoo.party.games.marble.ui.MarbleStartActivity
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * 모음집 흐름 (Robolectric):
 * 허브 카드 → 소개 → (첫 판) 규칙 카드 3장 → 시우 마블 시작(이어하기) → 게임 끝 → [결과 보기]
 * → 허브가 점수 반영·저장 → 결과 화면(시상대, 꼴찌 벌칙 룰렛) → [다시 하기] → 새 마블(15라운드 기본)
 * → 두 번째 결과도 누적되는지
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class PartyFlowTest {

    private class FixedDice(vararg faces: Int) : Dice() {
        private val q = ArrayDeque(faces.toList())
        override fun rollOne(): Int = q.removeFirstOrNull() ?: 1
    }

    private val app get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        MainActivity.diceOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun findAll(v: View, pred: (View) -> Boolean, out: MutableList<View> = ArrayList()): MutableList<View> {
        if (v.visibility != View.VISIBLE) return out
        if (pred(v)) out.add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) findAll(v.getChildAt(i), pred, out)
        return out
    }

    private fun button(a: Activity, label: String): GameButton? =
        findAll(a.window.decorView, { it is GameButton && it.text.toString() == label && it.isEnabled }).lastOrNull() as GameButton?

    private fun click(a: Activity, label: String) {
        val b = button(a, label) ?: fail("버튼 없음: $label / 보이는 버튼 ${findAll(a.window.decorView, { it is GameButton }).map { (it as GameButton).text }}")
        b as GameButton
        b.performClick()
        idle(600)
    }

    private fun tap(v: View) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, v.width / 2f, v.height / 2f, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, v.width / 2f, v.height / 2f, 0))
    }

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))

    /** 끝나기 직전의 마블: 나래는 이미 파산, 가람이 3칸 가면 다온 땅(39번) 통행료로 파산 → 다온 승리 */
    private fun nearEnd(): GameState {
        val list = players.map { p -> Player(p.id, p.name, p.colorIndex).apply { tag = p.id; money = 3_000_000 } }
        val s = GameState(list, deck = CardData.cards.map { it.id }.toMutableList())
        s.turnLimit = 0
        list[1].bankrupt = true; list[1].bankruptOrder = 1; list[1].money = 0
        list[0].money = 0; list[0].position = 36
        s.owner[39] = 2
        s.current = 0
        return s
    }

    private fun settle(a: MainActivity) {
        repeat(400) {
            if (!a.queue.running && !a.turnOverlayShowing) { idle(50); if (!a.queue.running) return }
            idle(250)
        }
        fail("연출이 끝나지 않음")
    }

    @Test
    fun hubToMarbleToResultAndBack_scoresAccumulate() {
        SessionStore(app).save(Session(players))
        GameStorage(app).save(nearEnd())
        assertFalse(PartySettings(app).rulesSeen(MarbleModule.ID))

        // ── 허브: 마블 카드에 "이어하기" 배지 → 소개 팝업 ──
        val hubC = Robolectric.buildActivity(HubActivity::class.java).setup()
        val hub = hubC.get()
        idle(500)
        val card = findAll(hub.window.decorView, { it is GameCardView && it.module === MarbleModule }).single() as GameCardView
        assertTrue("저장된 같은 플레이어 게임 → 이어하기", card.resumable)
        assertNull(card.unavailable)
        card.performClick()
        idle(600)
        click(hub, "이어하기")
        // 첫 판이라 규칙 카드 3장이 먼저
        click(hub, "다음"); click(hub, "다음"); click(hub, "시작!")
        assertTrue(PartySettings(app).rulesSeen(MarbleModule.ID))

        val toGame = shadowOf(hub).nextStartedActivityForResult
        assertNotNull(toGame)
        assertEquals(MarbleStartActivity::class.java.name, toGame.intent.component!!.className)
        assertEquals(HubActivity.REQ_GAME, toGame.requestCode)
        assertEquals(listOf("가람", "나래", "다온"), Party.players(toGame.intent)!!.map { it.name })

        // ── 마블 시작 화면: 진행 중인 게임 → 이어하기 ──
        val start = Robolectric.buildActivity(MarbleStartActivity::class.java, toGame.intent).setup().get()
        idle(500)
        click(start, "이어하기")
        val toMain = shadowOf(start).nextStartedActivity
        assertEquals(MainActivity::class.java.name, toMain.component!!.className)
        assertTrue("결과는 허브로 바로", toMain.flags and Intent.FLAG_ACTIVITY_FORWARD_RESULT != 0)

        // ── 게임: 가람 굴림 → 파산 → 다온 승리 → [결과 보기] ──
        MainActivity.diceOverride = FixedDice(1, 2)
        val main = Robolectric.buildActivity(MainActivity::class.java, toMain).setup().get()
        settle(main)
        main.onDiceTapped()
        settle(main)
        assertTrue(main.stateForTest.gameOver)
        click(main, "결과 보기")
        assertEquals(Activity.RESULT_OK, shadowOf(main).resultCode)
        val result = shadowOf(main).resultIntent
        assertArrayEquals(intArrayOf(0, 1, 2), result.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals("다온 1위, 늦게 파산한 가람 2위, 먼저 파산한 나래 3위", intArrayOf(2, 3, 1), result.getIntArrayExtra(Party.EXTRA_RANKS))
        assertNull("끝난 게임은 저장에서 지움", GameStorage(app).load())

        // ── 허브: 점수 반영 (3인 4/2/0) → 결과 화면 ──
        shadowOf(hub).receiveResult(toGame.intent, Activity.RESULT_OK, result)
        assertEquals(2, hub.session.score(0))
        assertEquals(0, hub.session.score(1))
        assertEquals(4, hub.session.score(2))
        assertEquals("저장도 됨", 4, SessionStore(app).load()!!.score(2))
        val toResult = shadowOf(hub).nextStartedActivityForResult
        assertEquals(ResultActivity::class.java.name, toResult.intent.component!!.className)

        val res = Robolectric.buildActivity(ResultActivity::class.java, toResult.intent).setup().get()
        repeat(60) { if (!res.finished) idle(250) }
        assertTrue("시상대·점수 연출 끝", res.finished)
        // 꼴찌 벌칙 룰렛 (설정 기본 켬) → 돌리고 카드 확인
        click(res, "꼴찌 벌칙 룰렛")
        val wheel = findAll(res.window.decorView, { it is RouletteView }).single()
        tap(wheel)
        idle(6000)
        click(res, "확인")
        assertFalse(res.popups.isShowing)
        // [다시 하기] → 허브가 마블을 다시 연다
        click(res, "다시 하기")
        assertEquals(ResultActivity.RESULT_REPLAY, shadowOf(res).resultCode)
        shadowOf(hub).receiveResult(toResult.intent, ResultActivity.RESULT_REPLAY, shadowOf(res).resultIntent)
        idle(300)
        val again = shadowOf(hub).nextStartedActivityForResult
        assertEquals(MarbleStartActivity::class.java.name, again.intent.component!!.className)

        // ── 새 마블: 이어할 게임 없음 → 게임 방식(기본 15라운드) → 시작 → 순서 추첨 → 게임 화면 ──
        val start2 = Robolectric.buildActivity(MarbleStartActivity::class.java, again.intent).setup().get()
        idle(500)
        click(start2, "시작")
        idle(5000)
        val toMain2 = shadowOf(start2).nextStartedActivity
        assertEquals(MainActivity::class.java.name, toMain2.component!!.className)
        val fresh = GameStorage(app).load()!!
        assertEquals(15, fresh.turnLimit)
        assertEquals(setOf(0, 1, 2), fresh.players.map { it.tag }.toSet())

        // 두 번째 결과(공동 1위 2명)도 누적
        shadowOf(hub).receiveResult(again.intent, Activity.RESULT_OK, Party.resultIntent(MarbleModule.ID, intArrayOf(2, 0, 1), intArrayOf(1, 1, 3)))
        assertEquals(2 + 4, hub.session.score(0))
        assertEquals(0 + 0, hub.session.score(1))
        assertEquals(4 + 4, hub.session.score(2))
        assertEquals(2, SessionStore(app).load()!!.history.size)
        assertEquals(listOf("다온"), hub.session.leaders().map { it.name })
        hubC.pause().stop().destroy()
    }

    /** 허브의 onActivityResult 를 직접 호출 (protected) */
    private fun deliver(hub: HubActivity, request: Int, code: Int, data: Intent?) {
        val m = HubActivity::class.java.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java)
        m.isAccessible = true
        m.invoke(hub, request, code, data)
    }

    @Test
    fun cancelledOrForeignResultGivesNoPoints() {
        SessionStore(app).save(Session(players))
        val hub = Robolectric.buildActivity(HubActivity::class.java).setup().get()
        // 중간에 허브로 나가면(RESULT_CANCELED) 점수 없음
        deliver(hub, HubActivity.REQ_GAME, Activity.RESULT_CANCELED, null)
        assertEquals(0, hub.session.history.size)
        // 모임에 없는 플레이어가 섞인 결과는 무시
        deliver(hub, HubActivity.REQ_GAME, Activity.RESULT_OK, Party.resultIntent(MarbleModule.ID, intArrayOf(0, 9, 2), intArrayOf(1, 2, 3)))
        assertEquals(0, hub.session.history.size)
        assertNull(shadowOf(hub).nextStartedActivityForResult)
    }
}
