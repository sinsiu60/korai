package com.siwoo.party.games.reaction.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.reaction.GameConfig
import com.siwoo.party.games.reaction.Phase
import com.siwoo.party.games.reaction.ReactionModule
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** 반응속도 결투 화면: 실격 표시, 5라운드 진행 후 순위를 허브로 돌려주는지, 일시정지 후 라운드 다시 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class ReactionUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))

    @After fun tearDown() { ReactionActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(): ReactionActivity {
        ReactionActivity.randomOverride = Random(5)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), ReactionModule, players)
        return Robolectric.buildActivity(ReactionActivity::class.java, i).setup().get()
    }

    private fun press(a: ReactionActivity, zone: Int) {
        val z = a.zones.zone(zone)!!
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, z.centerX, z.centerY, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, z.centerX, z.centerY, 0))
    }

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    /** 4명이 "지금!"에 동시에 손가락을 올려도 각자 자기 구역으로만 판정, 가장 먼저 닿은 손가락이 승 */
    @Test
    fun fourFingersAtOnceEachCountOnlyInTheirOwnZone() {
        ReactionActivity.randomOverride = Random(5)
        val four = players + PlayerInfo(3, "라온", 4)
        val a = Robolectric.buildActivity(ReactionActivity::class.java,
            Party.gameIntent(RuntimeEnvironment.getApplication(), ReactionModule, four)).setup().get()
        idle(2000)
        waitForGo(a)
        val order = intArrayOf(2, 0, 3, 1) // 위쪽(180°) 구역 2가 가장 먼저
        val xs = FloatArray(4) { a.zones.zone(order[it])!!.centerX }
        val ys = FloatArray(4) { a.zones.zone(order[it])!!.centerY }
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 4) {
            a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN or (k shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        }
        for (z in 0 until 4) {
            assertTrue("구역 $z 눌림", a.zones.isPressed(z))
            assertFalse("구역 $z 실격 아님", a.game.disqualified[z])
        }
        assertEquals("가장 먼저 닿은 구역 2가 승", 1, a.game.wins[2])
        for (z in intArrayOf(0, 1, 3)) assertEquals("구역 $z 는 승 없음", 0, a.game.wins[z])
    }

    /** 다음 "지금!"까지 시간을 흘린다 */
    private fun waitForGo(a: ReactionActivity) {
        repeat(200) { if (a.game.phase == Phase.GO) return; idle(50) }
        error("지금! 이 나오지 않음 (${a.game.phase})")
    }

    @Test
    fun earlyTouchDisqualifiesAndFiveRoundsReturnRanks() {
        val a = launch()
        idle(2000) // 라운드 소개
        assertEquals(Phase.WAITING, a.game.phase)
        press(a, 2) // 초록 전 → 실격
        assertTrue(a.game.disqualified[2])
        waitForGo(a)
        press(a, 2) // 실격자는 무시
        press(a, 0) // 가람 승
        assertEquals(1, a.game.wins[0])
        assertEquals(-1L, a.game.roundTimes[2])
        // 나머지 4라운드: 나래가 매번 1등
        repeat(4) {
            repeat(200) { if (a.game.phase == Phase.WAITING) return@repeat; idle(100) }
            waitForGo(a)
            press(a, 1)
            press(a, 0)
        }
        idle(8000)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1, 2), data.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals(intArrayOf(2, 1, 3), data.getIntArrayExtra(Party.EXTRA_RANKS))
        assertEquals(GameConfig.REACTION_ROUNDS, a.game.roundsDone)
    }

    @Test
    fun backgroundDuringRoundPausesAndRestartsTheRound() {
        val c = Robolectric.buildActivity(ReactionActivity::class.java,
            Party.gameIntent(RuntimeEnvironment.getApplication(), ReactionModule, players)).also { ReactionActivity.randomOverride = Random(5) }.setup()
        val a = c.get()
        idle(2000)
        assertEquals(Phase.WAITING, a.game.phase)
        c.pause().stop()
        idle(10_000) // 백그라운드 동안 "지금!"이 나오면 안 된다
        assertEquals(Phase.WAITING, a.game.phase)
        c.start().resume()
        idle(100)
        assertTrue("돌아오면 일시정지 메뉴", PauseMenu.isShowing(a.popups))
        // 계속하기 → 라운드 다시 시작
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(3000)
        waitForGo(a)
        press(a, 1)
        assertEquals(1, a.game.wins[1])
        assertEquals("일시정지 전 라운드는 세지 않음", 0, a.game.roundsDone)
    }
}
