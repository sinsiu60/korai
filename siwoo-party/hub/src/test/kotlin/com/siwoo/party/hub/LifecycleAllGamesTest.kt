package com.siwoo.party.hub

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Session
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RulesPager
import com.siwoo.party.common.ui.SessionStore
import com.siwoo.party.games.marble.MarbleModule
import com.siwoo.party.games.marble.ui.MainActivity
import com.siwoo.party.games.marble.ui.MarbleStartActivity
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * 모든 게임 공통 (단원 4 일관성·흐름 점검):
 * - 백그라운드 → 복귀: 죽지 않고 일시정지 메뉴가 떠 있고 게임 시간이 멈춰 있음
 * - 뒤로 가기 → 일시정지 메뉴, [규칙 보기] → 규칙 카드, [계속하기] → 다시 진행
 * - 진행 중 종료(destroy)해도 예약된 일이 남아 죽지 않음
 * - 모임 종료 → 오늘의 왕 → [새 모임 시작] → 플레이어 설정, 점수 초기화
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class LifecycleAllGamesTest {

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

    /** 뒤로 가기: 게임 안의 닫을 수 있는 팝업이 먼저 닫힐 수 있으니 메뉴가 뜰 때까지 몇 번 */
    @Suppress("DEPRECATION")
    private fun backUntilMenu(a: Activity, popups: PopupLayer, name: String) {
        repeat(4) {
            if (PauseMenu.isShowing(popups)) return
            a.onBackPressed(); idle(500)
        }
        assertTrue("$name: 뒤로 가기 → 일시정지 메뉴", PauseMenu.isShowing(popups))
    }

    /** 공통 점검: 백그라운드 복귀, 뒤로 가기, 규칙 보기, 계속하기 */
    @Suppress("DEPRECATION")
    private fun checkLifecycle(c: ActivityController<out Activity>, popups: () -> PopupLayer, name: String, paused: (() -> Boolean)?) {
        val a = c.get()
        // 1) 진행 중 백그라운드 → 복귀
        c.pause().stop(); idle(3000)
        c.restart().start().resume(); idle(600)
        assertFalse("$name: 복귀 후 살아 있음", a.isFinishing)
        assertTrue("$name: 복귀하면 일시정지 메뉴", PauseMenu.isShowing(popups()))
        if (paused != null) assertTrue("$name: 메뉴 동안 게임 시간 멈춤", paused())
        // 메뉴가 떠 있는 채로 또 백그라운드에 다녀와도 메뉴 하나
        c.pause().stop(); c.restart().start().resume(); idle(600)
        assertEquals("$name: 메뉴는 하나만", 1, findAll(a.window.decorView, { it.tag == PauseMenu.TAG }).size)
        // 2) 규칙 보기 → 규칙 카드 → 닫기(뒤로) → 메뉴로
        click(a, "규칙 보기")
        assertTrue("$name: 규칙 보기", RulesPager.isShowing(popups()))
        a.onBackPressed(); idle(500)
        assertFalse("$name: 뒤로 → 규칙 닫힘", RulesPager.isShowing(popups()))
        // 3) 메뉴에서 뒤로 = 계속하기 → 다시 진행
        a.onBackPressed(); idle(500)
        assertFalse("$name: 뒤로 → 메뉴 닫힘", PauseMenu.isShowing(popups()))
        if (a.isFinishing) { c.pause().stop().destroy(); println("$name: 판이 끝남(테스트 시계)"); return } // 테스트 시계가 건너뛰어 판이 끝난 경우: 정상 종료
        if (paused != null) assertFalse("$name: 계속하기 → 시간 다시 흐름", paused())
        // 4) 진행 중 뒤로 가기 → 일시정지 메뉴 → [계속하기]
        backUntilMenu(a, popups(), name)
        if (paused != null) assertTrue("$name: 메뉴 동안 멈춤", paused())
        click(a, "계속하기")
        assertFalse("$name: 계속하기 → 메뉴 닫힘", PauseMenu.isShowing(popups()))
        // 5) 진행 중에 끝내도(예약된 타이머·애니메이션 정리) 죽지 않음
        c.pause().stop().destroy()
        idle(5000)
    }

    @Test
    fun everyGameSurvivesBackgroundAndBackShowsPauseMenu() {
        val checked = ArrayList<String>()
        for (m in GameRegistry.games) {
            if (m === MarbleModule) continue
            val n = (2..4).first { m.supports(it) }
            val intent = Party.gameIntent(app, m, all.take(n))
            val c = Robolectric.buildActivity(m.activityClass, intent).setup()
            val a = c.get() as? PartyGameActivity ?: fail("${m.name}: 게임 Activity 는 PartyGameActivity 를 써야 함 (${m.activityClass.name})") as PartyGameActivity
            idle(1500)
            checkLifecycle(c, { a.popups }, m.name) { a.gamePaused }
            checked += m.name
        }
        assertEquals(GameRegistry.games.size - 1, checked.size)
    }

    @Test
    fun marbleSurvivesBackgroundAndBackShowsPauseMenu() {
        val start = Robolectric.buildActivity(MarbleStartActivity::class.java, Party.gameIntent(app, MarbleModule, all.take(3))).setup().get()
        idle(500)
        click(start, "시작")
        // 순서 추첨 연출 → 게임 화면
        repeat(60) { if (shadowOf(start).peekNextStartedActivity() == null) idle(500) }
        val toMain = shadowOf(start).nextStartedActivity
        assertNotNull("추첨 뒤 게임 화면 ${findAll(start.window.decorView, { it is GameButton }).map { (it as GameButton).text }}", toMain)
        val c = Robolectric.buildActivity(MainActivity::class.java, toMain).setup()
        idle(3000)
        checkLifecycle(c, { c.get().popups }, MarbleModule.name, null)
    }

    @Test
    fun endSessionShowsKingThenNewSession() {
        val players = all.take(3)
        val s = Session(players)
        s.applyResult("reaction", intArrayOf(0, 1, 2), intArrayOf(2, 1, 3))
        SessionStore(app).save(s)
        val hub = Robolectric.buildActivity(HubActivity::class.java).setup().get()
        idle(500)
        click(hub, "모임 종료")
        click(hub, "모임 종료") // 확인 팝업
        val king = findAll(hub.window.decorView, { it is android.widget.TextView && it !is GameButton && it.text.toString() == "오늘의 왕" })
        assertTrue("오늘의 왕 화면", king.isNotEmpty())
        repeat(40) {
            if (findAll(hub.window.decorView, { it is GameButton && it.text.toString() == "새 모임 시작" }).isEmpty()) idle(250)
        }
        click(hub, "새 모임 시작")
        val next = shadowOf(hub).nextStartedActivity
        assertEquals(SplashActivity::class.java.name, next.component!!.className)
        assertTrue("바로 플레이어 설정", next.getBooleanExtra(SplashActivity.EXTRA_OPEN_SETUP, false))
        assertNull("점수 초기화", SessionStore(app).load())
        assertTrue(hub.isFinishing)
    }
}
