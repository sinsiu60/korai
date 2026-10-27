package com.siwoo.party.games.baseball.ui

import android.app.Activity
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.numpad.NumPadView
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.baseball.BaseballModule
import com.siwoo.party.games.baseball.Phase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** 숫자 야구 화면: 비밀 입력이 가려지고 지워지는지, 패드로 끝까지 진행해 순위를 돌려주는지, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class BaseballUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { BaseballActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(300) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun launch(seed: Int): BaseballActivity {
        BaseballActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), BaseballModule, players)
        return Robolectric.buildActivity(BaseballActivity::class.java, i).setup().get()
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    /** 넘기기 화면의 버튼 누르기 */
    private fun passHandoff(a: BaseballActivity) {
        waitFor("넘기기 화면") { a.handoffPopup != null }
        all(a.handoffPopup!!.content).filterIsInstance<GameButton>().single().performClick()
        idle(100)
    }

    /** 패드 키를 실제 터치로 누르기 */
    private fun tap(pad: NumPadView, key: Int) {
        val r = RectF()
        assertTrue(pad.keyRect(key, r))
        val t = SystemClock.uptimeMillis()
        pad.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, r.centerX(), r.centerY(), 0))
        pad.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, r.centerX(), r.centerY(), 0))
    }

    private fun type(pad: NumPadView, s: String) {
        for (ch in s) tap(pad, ch - '0')
        tap(pad, NumPadView.KEY_OK)
    }

    /** 화면에 보이는 글자에 s 가 있는지 */
    private fun visibleText(a: Activity, s: String): Boolean =
        all(a.window.decorView).filterIsInstance<TextView>().any { it.isShown && it.text.contains(s) }

    /** 두 사람 비밀 정하기 (가림·지움 확인 포함) */
    private fun enterSecrets(a: BaseballActivity, s0: String, s1: String) {
        passHandoff(a)
        assertEquals(0, a.entryPlayer)
        val pad0 = a.entryPad!!
        val digits0 = a.entryDigits!!
        assertFalse(pad0.showDisplay)
        for (ch in s0) tap(pad0, ch - '0')
        assertEquals(s0, digits0.digits)
        assertEquals("가려진 상태", "", digits0.shownText)
        assertFalse(visibleText(a, s0))
        // 꾹 누르는 동안만 보임
        val t = SystemClock.uptimeMillis()
        digits0.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 5f, 5f, 0))
        assertEquals(s0, digits0.shownText)
        digits0.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, 5f, 5f, 0))
        assertEquals("", digits0.shownText)
        tap(pad0, NumPadView.KEY_OK)
        idle(50)
        // 확인 뒤: 입력 화면이 사라지고 숫자도 지워짐, 다음 사람 넘기기 화면이 덮음
        assertNull(a.entryPad)
        assertNull(a.entryDigits)
        assertEquals("", digits0.digits)
        assertEquals("", pad0.value)
        assertNull("비밀 입력 칸이 화면에 남지 않음", all(a.window.decorView).firstOrNull { it is SecretDigitsView })
        assertNotNull(a.handoffPopup)
        assertFalse(visibleText(a, s0))
        passHandoff(a)
        assertEquals(1, a.entryPlayer)
        assertEquals("새 입력 화면은 비어 있음", "", a.entryPad!!.value)
        // 같은 숫자는 안 들어감
        tap(a.entryPad!!, s1[0] - '0')
        tap(a.entryPad!!, s1[0] - '0')
        assertEquals(1, a.entryPad!!.value.length)
        tap(a.entryPad!!, NumPadView.KEY_BACK)
        type(a.entryPad!!, s1)
        idle(50)
        assertNull(a.entryPad)
        assertEquals(s0, a.game.secretOf(0))
        assertEquals(s1, a.game.secretOf(1))
    }

    private fun waitTurn(a: BaseballActivity) = waitFor("차례") { !a.busy || a.game.phase == Phase.OVER }

    private fun guess(a: BaseballActivity, s: String) {
        waitTurn(a)
        type(a.pad, s)
    }

    @Test
    fun secretsHiddenThenFirstHitsAndLastChanceMissesFirstWins() {
        val a = launch(3)
        enterSecrets(a, "102", "347")
        waitTurn(a)
        val f = a.game.first
        val sec = arrayOf("102", "347")
        // 1회: 둘 다 빗나감
        guess(a, "561")
        assertEquals(1, a.board.count(f))
        guess(a, "589")
        assertEquals(1, a.board.count(1 - f))
        assertEquals(2, a.game.inning)
        // 2회: 선공이 맞힘 → 마지막 기회 → 후공 실패
        guess(a, sec[1 - f])
        waitFor("마지막 기회") { a.game.phase == Phase.LAST_CHANCE && !a.busy }
        assertEquals(1 - f, a.game.current)
        assertEquals("", a.board.shownSecret(0))
        guess(a, "987")
        waitFor("끝") { shadowOf(a).resultIntent != null }
        assertEquals(sec[0], a.board.shownSecret(0))
        assertEquals(sec[1], a.board.shownSecret(1))
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        val expected = IntArray(2) { if (it == f) 1 else 2 }
        assertArrayEquals(expected, ranks)
    }

    @Test
    fun lastChanceHitIsDraw() {
        val a = launch(8)
        enterSecrets(a, "012", "985")
        val f = a.game.first
        val sec = arrayOf("012", "985")
        guess(a, sec[1 - f])
        waitFor("마지막 기회") { a.game.phase == Phase.LAST_CHANCE && !a.busy }
        guess(a, sec[f])
        waitFor("끝") { shadowOf(a).resultIntent != null }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(1, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun secondHitsFirstWinsAndPauseBlocksInput() {
        val a = launch(5)
        enterSecrets(a, "456", "789")
        val f = a.game.first
        val sec = arrayOf("456", "789")
        guess(a, "123")
        waitTurn(a)
        // 일시정지 중엔 패드로 추측 못 함
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        a.pad.value = sec[f]
        a.pad.press(NumPadView.KEY_OK)
        assertEquals(0, a.board.count(1 - f))
        @Suppress("DEPRECATION") a.onBackPressed() // 계속하기
        waitFor("재개") { !a.gamePaused && !PauseMenu.isShowing(a.popups) }
        a.pad.clear()
        guess(a, sec[f]) // 후공이 먼저 맞힘 → 바로 승리
        assertEquals(1, a.board.count(1 - f))
        waitFor("끝") { shadowOf(a).resultIntent != null }
        val expected = IntArray(2) { if (it == f) 2 else 1 }
        assertArrayEquals(expected, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }
}
