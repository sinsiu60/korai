package com.siwoo.party.common.secret

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Handoff
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 공통 비밀 확인 화면: 넘기기 화면 → 꾹 누르는 동안만 보임(떼면 바로 가림) → 한 번 눌러 봐야 [확인 완료] →
 * 확인 뒤엔 비밀이 지워지고 화면 어디에도 남지 않음, 일시정지되면 가림, 도중 close, 넘기기 화면만
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class SecretScreenTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(): SecretTestActivity {
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), TestModule, players)
        val a = Robolectric.buildActivity(SecretTestActivity::class.java, i).setup().get()
        idle(300)
        return a
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    private fun touch(v: View, action: Int) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, v.width / 2f, v.height / 2f, 0)
        v.dispatchTouchEvent(e)
        e.recycle()
    }

    /** 넘기기 화면의 [확인] 누르기 */
    private fun passHandoff(a: SecretTestActivity, h: SecretHandle, name: String) {
        assertNotNull(h.handoffPopup)
        val top = a.popups.top()!!
        assertEquals(Handoff.TAG, top.content.tag)
        val texts = all(top.content).filterIsInstance<TextView>().joinToString(" ") { it.text }
        assertTrue(texts, texts.contains(name))
        assertFalse("넘기기 단계에선 비밀 화면 없음", h.showingSecret)
        all(top.content).filterIsInstance<GameButton>().single().performClick()
        idle(300)
        assertNull(h.handoffPopup)
    }

    /** 화면 어디에도 이 글자가 없고, 남은 SecretHoldView 는 모두 비어 있음 */
    private fun assertNowhere(a: SecretTestActivity, secret: String) {
        for (v in all(a.window.decorView)) {
            if (v is TextView) assertFalse("글자 '${v.text}' 에 비밀", v.text.toString().contains(secret))
            if (v is SecretHoldView) {
                assertFalse(v.hasSecret)
                assertEquals("", v.shownText)
            }
        }
    }

    @Test
    fun holdShowsOnlyWhilePressedAndHidesInstantly() {
        val a = launch()
        var done = 0
        val h = SecretScreen.show(a, 1, TextSecret("나의 비밀 숫자", "427", "아무에게도 보여 주지 마세요"), title = "나래님의 숫자") { done++ }
        passHandoff(a, h, "나래")
        val s = h.holdView!!
        assertTrue(s.width > 0 && s.height > 0)
        assertTrue(s.hasSecret)
        assertEquals("", s.shownText)
        assertFalse(s.revealed)
        touch(s, MotionEvent.ACTION_DOWN)
        assertTrue(s.revealed)
        assertEquals("427", s.shownText)
        // 떼는 즉시 (시간을 흘리지 않고) 가림
        touch(s, MotionEvent.ACTION_UP)
        assertFalse(s.revealed)
        assertEquals("", s.shownText)
        touch(s, MotionEvent.ACTION_DOWN)
        assertEquals("427", s.shownText)
        touch(s, MotionEvent.ACTION_CANCEL)
        assertEquals("", s.shownText)
        // 안 보이게 되면 가림
        touch(s, MotionEvent.ACTION_DOWN)
        s.visibility = View.INVISIBLE
        assertEquals("", s.shownText)
        assertEquals(0, done)
    }

    @Test
    fun confirmDisabledUntilHeldOnceThenSecretIsClearedEverywhere() {
        val a = launch()
        var done = 0
        val h = SecretScreen.show(a, 0, TextSecret("나의 비밀 숫자", "583")) {
            done++
        }
        passHandoff(a, h, "가람")
        val s = h.holdView!!
        val ok = h.confirmButton!!
        assertFalse("한 번 꾹 누르기 전엔 확인 불가", ok.isEnabled)
        ok.performClick()
        assertEquals(0, done)
        assertTrue(h.isOpen)
        touch(s, MotionEvent.ACTION_DOWN)
        touch(s, MotionEvent.ACTION_UP)
        assertTrue(s.heldOnce)
        assertTrue(ok.isEnabled)
        ok.performClick()
        assertEquals(1, done)
        assertFalse(h.isOpen)
        // 비밀은 지워지고 화면도 사라짐
        assertFalse(s.hasSecret)
        assertEquals("", s.shownText)
        assertNull(h.holdView)
        assertNull(h.confirmButton)
        assertFalse(s.isAttachedToWindow)
        assertFalse(a.popups.isShowing)
        assertNowhere(a, "583")
        // 다시 눌러도 아무 일 없음
        ok.performClick()
        assertEquals(1, done)
    }

    @Test
    fun pauseHidesTheCardAndBlocksRevealAndConfirm() {
        val a = launch()
        var done = 0
        val h = SecretScreen.show(a, 2, TextSecret("비밀", "7")) { done++ }
        passHandoff(a, h, "다온")
        val s = h.holdView!!
        touch(s, MotionEvent.ACTION_DOWN)
        assertEquals("7", s.shownText)
        // 꾹 누른 채 일시정지 메뉴 → 바로 가려짐 (게임이 hide 를 부르지 않아도)
        a.showPauseMenu()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(s.revealed)
        assertEquals("", s.shownText)
        // 일시정지 중엔 눌러도 안 보이고, 확인도 안 됨
        touch(s, MotionEvent.ACTION_DOWN)
        assertFalse(s.revealed)
        h.confirmButton!!.performClick()
        assertEquals(0, done)
        // 다시 이어 하면 정상
        touch(s, MotionEvent.ACTION_UP)
        @Suppress("DEPRECATION") a.onBackPressed()
        repeat(100) { if (a.gamePaused) idle(50) }
        assertFalse(PauseMenu.isShowing(a.popups))
        assertFalse(a.gamePaused)
        touch(s, MotionEvent.ACTION_DOWN)
        assertEquals("7", s.shownText)
        // 게임이 직접 가릴 수도 있음 (onGamePause 등)
        h.hide()
        assertEquals("", s.shownText)
        h.confirmButton!!.performClick()
        assertEquals(1, done)
    }

    @Test
    fun containerModeFillsAndEmptiesTheContainer() {
        val a = launch()
        val box = FrameLayout(a)
        a.stage.addView(box, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        var done = false
        val h = SecretScreen.show(a, 1, TextSecret("제시어", "바나나"), handoff = false, container = box) { done = true }
        assertNull(h.handoffPopup)
        assertTrue(h.showingSecret)
        assertEquals(1, box.childCount)
        assertFalse("팝업 없이 container 안에", a.popups.isShowing)
        idle(100)
        val s = h.holdView!!
        assertSame(s, all(box).filterIsInstance<SecretHoldView>().single())
        touch(s, MotionEvent.ACTION_DOWN)
        touch(s, MotionEvent.ACTION_UP)
        h.confirmButton!!.performClick()
        assertTrue(done)
        assertEquals(0, box.childCount)
        assertNowhere(a, "바나나")
    }

    @Test
    fun closeDuringHandoffOrSecretRemovesEverythingWithoutDone() {
        val a = launch()
        var done = 0
        val h1 = SecretScreen.show(a, 0, TextSecret("비밀", "111")) { done++ }
        assertNotNull(h1.handoffPopup)
        h1.close()
        idle(100)
        assertNull(h1.handoffPopup)
        assertFalse(a.popups.isShowing)
        assertFalse(h1.isOpen)

        val h2 = SecretScreen.show(a, 1, TextSecret("비밀", "222")) { done++ }
        passHandoff(a, h2, "나래")
        val s = h2.holdView!!
        touch(s, MotionEvent.ACTION_DOWN)
        h2.close()
        assertFalse(s.hasSecret)
        assertEquals("", s.shownText)
        assertFalse(a.popups.isShowing)
        assertNowhere(a, "222")
        assertEquals(0, done)
    }

    @Test
    fun handoffOnlyCoversUntilConfirmed() {
        val a = launch()
        var ok = 0
        val h = SecretScreen.handoffOnly(a, 2, okText = "내 카드 보기") { ok++ }
        val top = a.popups.top()!!
        assertEquals(Handoff.TAG, top.content.tag)
        assertTrue(all(top.content).filterIsInstance<TextView>().any { it.text.contains("다온") })
        val b = all(top.content).filterIsInstance<GameButton>().single()
        assertEquals("내 카드 보기", b.text.toString())
        b.performClick()
        assertEquals(1, ok)
        assertNull(h.handoffPopup)
        assertFalse(h.isOpen)
        // 닫은 넘기기 화면은 [확인] 을 눌러도 onOk 안 불림
        val h2 = SecretScreen.handoffOnly(a, 0) { ok++ }
        val b2 = all(a.popups.top()!!.content).filterIsInstance<GameButton>().single()
        h2.close()
        b2.performClick()
        assertEquals(1, ok)
    }

    @Test
    fun holdViewClearAndContentText() {
        val a = launch()
        val v = SecretHoldView(a)
        a.stage.addView(v, FrameLayout.LayoutParams(600, 400))
        idle(100)
        // 비밀이 없으면 눌러도 보이지 않음
        touch(v, MotionEvent.ACTION_DOWN)
        assertFalse(v.revealed)
        touch(v, MotionEvent.ACTION_UP)
        val t = TextSecret("제목", "큰글자", "작은글자")
        v.setContent(t)
        var changes = 0
        v.onHoldChanged = { changes++ }
        touch(v, MotionEvent.ACTION_DOWN)
        assertEquals("큰글자", v.shownText)
        v.clear()
        assertFalse(v.revealed)
        assertFalse(v.heldOnce)
        assertEquals("", t.big)
        assertEquals("", t.title)
        assertEquals("", v.shownText)
        assertEquals(1, changes)
    }
}
