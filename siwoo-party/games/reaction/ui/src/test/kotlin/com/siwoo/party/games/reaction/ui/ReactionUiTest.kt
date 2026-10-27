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
