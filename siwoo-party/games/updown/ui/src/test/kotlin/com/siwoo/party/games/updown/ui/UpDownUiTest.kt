package com.siwoo.party.games.updown.ui

import android.app.Activity
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.numpad.NumPadView
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.updown.Phase
import com.siwoo.party.games.updown.UpDownModule
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** 폭탄 숫자 업다운 화면: 패드로 끝까지 진행해 순위를 돌려주는지, 범위 밖 입력 막기, 강제 마지막, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class UpDownUiTest {

    private val p4 = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { UpDownActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(300) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun launch(players: List<PlayerInfo>, seed: Int): UpDownActivity {
        UpDownActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), UpDownModule, players)
        return Robolectric.buildActivity(UpDownActivity::class.java, i).setup().get()
    }

    private fun tap(pad: NumPadView, key: Int) {
        val r = RectF()
        assertTrue(pad.keyRect(key, r))
        val t = SystemClock.uptimeMillis()
        pad.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, r.centerX(), r.centerY(), 0))
        pad.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, r.centerX(), r.centerY(), 0))
    }

    private fun waitTurn(a: UpDownActivity) = waitFor("차례") { (!a.busy && a.game.phase == Phase.PLAYING) || a.game.phase == Phase.OVER }

    /** 패드를 실제로 눌러 v 입력 + 확인 */
    private fun enter(a: UpDownActivity, v: Int) {
        waitTurn(a)
        a.pad.clear()
        for (ch in v.toString()) tap(a.pad, ch - '0')
        tap(a.pad, NumPadView.KEY_OK)
    }

    /** 폭탄을 피해 범위 가운데를 고르는 사람 / 폭탄을 바로 누르는 사람 */
    private fun safePick(a: UpDownActivity): Int {
        val g = a.game
        for (v in (g.minAllowed + g.maxAllowed) / 2 downTo g.minAllowed) if (v != g.bomb) return v
        for (v in g.minAllowed..g.maxAllowed) if (v != g.bomb) return v
        return g.bomb
    }

    @Test
    fun fourPlayersPlayAllRoundsWithNumpadAndReturnRanks() {
        val a = launch(p4, 7)
        // 라운드마다: loser 가 폭탄을 누를 때까지 모두 안전한 숫자
        val losers = intArrayOf(1, 1, 3, 2) // 라운드별 폭탄 누를 사람 (가능하면)
        var round = 0
        while (a.game.phase != Phase.OVER) {
            waitTurn(a)
            if (a.game.phase == Phase.OVER) break
            round = a.game.round
            val g = a.game
            val p = g.current
            val target = losers[round - 1]
            val v = if (p == target || g.isForced) g.bomb else safePick(a)
            if (g.isForced) assertEquals("강제 마지막: 패드에 폭탄 숫자가 미리 들어감", g.bomb.toString(), a.pad.value)
            val before = g.skulls.sum()
            enter(a, v)
            if (v == g.bomb) waitFor("폭발 처리") { g.skulls.sum() == before + 1 }
        }
        assertEquals(4, round)
        assertEquals(4, a.game.skulls.sum())
        waitFor("끝") { shadowOf(a).resultIntent != null }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(a.game.ranks(), ranks)
        // 해골 적은 순·동점 같은 순위 확인
        val sk = a.game.skulls
        for (i in 0 until 4) assertEquals(1 + sk.count { it < sk[i] }, ranks[i])
    }

    @Test
    fun outOfRangeIsBlockedAndForcedFinalAndPause() {
        val a = launch(p4.take(2), 3)
        waitTurn(a)
        val g = a.game
        // 폭탄 바로 위·아래를 잘라 폭탄만 남김 (폭탄이 끝 숫자면 한쪽만)
        val b = g.bomb
        if (b < 100) { enter(a, b + 1); waitTurn(a); assertEquals(b, g.maxAllowed) }
        // 잘린 값은 패드가 확인을 막음
        if (b < 100) {
            a.pad.value = (b + 1).toString()
            assertFalse("잘린 값은 확인 불가", a.pad.canConfirm)
            assertFalse(a.pad.isKeyEnabled(NumPadView.KEY_OK))
            a.pad.clear()
        }
        if (b > 1) { enter(a, b - 1); waitTurn(a); assertEquals(b, g.minAllowed) }
        assertTrue(g.isForced)
        assertEquals(b.toString(), a.pad.value)
        assertTrue(a.pad.canConfirm)
        // 일시정지 중엔 확인해도 무시
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        a.pad.press(NumPadView.KEY_OK)
        assertEquals(0, g.skulls.sum())
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("재개") { !a.gamePaused && !PauseMenu.isShowing(a.popups) }
        val p = g.current
        tap(a.pad, NumPadView.KEY_OK)
        waitFor("폭발") { g.skulls[p] == 1 }
        assertEquals(b, a.bar.revealedBomb)
        // 2라운드: 다른 사람이 시작, 바로 폭탄
        waitTurn(a)
        assertEquals(2, g.round)
        assertEquals((g.firstStarter + 1) % 2, g.current)
        assertEquals(100, g.maxAllowed)
        val q = g.current
        enter(a, g.bomb)
        waitFor("끝") { shadowOf(a).resultIntent != null }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        val expected = if (p == q) IntArray(2) { if (it == p) 2 else 1 } else intArrayOf(1, 1)
        assertArrayEquals(expected, ranks)
    }
}
