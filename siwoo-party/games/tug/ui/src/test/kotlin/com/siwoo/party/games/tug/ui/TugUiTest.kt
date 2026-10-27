package com.siwoo.party.games.tug.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.tug.GameConfig
import com.siwoo.party.games.tug.Phase
import com.siwoo.party.games.tug.Side
import com.siwoo.party.games.tug.TugModule
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

/** 연타 줄다리기 화면: 2인·4인 끝까지 진행 → 순위, 시간 종료·연장 무승부, 일시정지 중 탭 무시, 3인 거절 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class TugUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val two = four.take(2)

    /**
     * 프레임 루프(postOnAnimation)가 돌 때 Robolectric 이 프레임마다 시계를 저절로 당겨
     * 한 번의 idle 에 경기 전체가 지나가 버리지 않도록, 프레임은 idle 로 흘린 시간만큼만 돈다.
     */
    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { TugActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): TugActivity {
        TugActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), TugModule, players)
        return Robolectric.buildActivity(TugActivity::class.java, i).setup().get()
    }

    private fun press(a: TugActivity, zone: Int) {
        val z = a.zones.zone(zone)!!
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, z.centerX, z.centerY + 20f, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, z.centerX, z.centerY + 20f, 0))
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1200) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitPlaying(a: TugActivity) = waitFor("경기 시작") { a.game.phase == Phase.PLAYING }

    private fun ranksOf(a: TugActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun twoPlayersLeftReachesWinLine() {
        val a = launch(two)
        assertFalse("시작 전엔 탭 무시", a.zones.touchEnabled)
        waitPlaying(a)
        assertTrue("경기 중 프레임 루프", a.looping)
        // 왼쪽(가람) 초당 약 14회 연타, 나래는 가끔
        var k = 0
        while (!a.game.isOver && k < 300) {
            press(a, 0)
            if (k % 5 == 0) press(a, 1)
            idle(70)
            k++
        }
        assertSame(Side.LEFT, a.game.winner)
        assertEquals(GameConfig.TUG_WIN_DIFF_SOLO, -a.game.pull)
        assertTrue("시간 안에 끝남", a.game.taps[0] < 15 * GameConfig.TUG_TIME_SEC)
        val ranks = ranksOf(a)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals(intArrayOf(1, 2), ranks)
        idle(3000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun fourPlayersTeamRevealThenRightTeamWins() {
        val a = launch(four, seed = 11)
        val rv = a.reveal!!
        assertEquals("팀 뽑는 동안 구역은 가림", View.INVISIBLE, a.zones.visibility)
        assertEquals(View.VISIBLE, rv.visibility)
        waitPlaying(a)
        assertEquals(View.GONE, rv.visibility)
        assertEquals(View.VISIBLE, a.zones.visibility)
        assertEquals(2, a.teams.left.size)
        assertEquals(2, a.teams.right.size)
        // 구역 1·3 = 오른쪽 팀
        assertSame(Side.RIGHT, a.teams.sideOf(a.teams.zonePlayer[1]))
        assertSame(Side.RIGHT, a.teams.sideOf(a.teams.zonePlayer[3]))
        var k = 0
        while (!a.game.isOver && k < 400) {
            press(a, 1)
            press(a, 3)
            if (k % 4 == 0) press(a, 0)
            idle(80)
            k++
        }
        assertSame(Side.RIGHT, a.game.winner)
        val ranks = ranksOf(a)
        val expected = IntArray(4) { if (a.teams.sideOf(it) == Side.RIGHT) 1 else 3 }
        assertArrayEquals("원래 플레이어 순서로 이긴 팀 1위, 진 팀 3위", expected, ranks)
        assertArrayEquals(intArrayOf(0, 1, 2, 3), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
    }

    @Test
    fun timeUpCloserSideWins() {
        val a = launch(two)
        waitPlaying(a)
        repeat(3) { press(a, 1); idle(100) }
        waitFor("시간 종료") { a.game.isOver }
        assertSame(Side.RIGHT, a.game.winner)
        assertArrayEquals(intArrayOf(2, 1), ranksOf(a))
    }

    @Test
    fun exactTieGoesToOvertimeThenDraw() {
        val a = launch(four, seed = 3)
        waitPlaying(a)
        waitFor("연장전") { a.game.phase == Phase.OVERTIME }
        assertTrue(a.zones.touchEnabled)
        waitFor("연장 끝") { a.game.isOver }
        assertTrue(a.game.draw)
        assertNull(a.game.winner)
        assertArrayEquals(intArrayOf(1, 1, 1, 1), ranksOf(a))
    }

    @Test
    fun pausedTapsDoNotCountAndClockStops() {
        val a = launch(two)
        waitPlaying(a)
        press(a, 0)
        assertEquals(1, a.game.taps[0])
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지 중 프레임 멈춤", a.looping)
        val before = a.game.remainingMs(a.timers.now())
        // 일시정지 중 탭은 세지 않음 (구역에 직접 보내도)
        repeat(5) { a.onZoneDown(0, 10f, 10f, SystemClock.uptimeMillis()); idle(100) }
        idle(40_000)
        assertEquals(1, a.game.taps[0])
        assertEquals("일시정지 중 시간이 줄지 않음", Phase.PLAYING, a.game.phase)
        assertEquals(before, a.game.remainingMs(a.timers.now()))
        // 계속하기
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertFalse(PauseMenu.isShowing(a.popups))
        assertTrue(a.looping)
        press(a, 0)
        assertEquals(2, a.game.taps[0])
    }

    @Test
    fun threePlayersRejected() {
        assertFalse(TugModule.supports(3))
        assertTrue(TugModule.supports(2) && TugModule.supports(4))
        assertEquals("2·4인", TugModule.playersLabel)
        assertEquals("2·4인용", TugModule.unavailableReason(3))
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), TugModule, four.take(3))
        val a = Robolectric.buildActivity(TugActivity::class.java, i).setup().get()
        assertTrue("3인은 바로 닫힘", a.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
    }

    @Test
    fun rulesAndIcon() {
        assertEquals(3, TugModule.rules.size)
        assertEquals("tug", TugModule.id)
        assertEquals("1분", TugModule.minutes)
    }
}
