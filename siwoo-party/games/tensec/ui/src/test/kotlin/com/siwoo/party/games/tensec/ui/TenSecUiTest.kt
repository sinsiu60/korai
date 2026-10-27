package com.siwoo.party.games.tensec.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.tensec.GameConfig
import com.siwoo.party.games.tensec.Phase
import com.siwoo.party.games.tensec.TenSecModule
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

/** 딱 10초 화면: 4인·2인 3라운드 끝까지 → 순위, 시간 초과 자동 기록, 4손가락 동시 터치, 일시정지 중 시계 멈춤 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class TenSecUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { TenSecActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): TenSecActivity {
        TenSecActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), TenSecModule, players)
        return Robolectric.buildActivity(TenSecActivity::class.java, i).setup().get()
    }

    private fun press(a: TenSecActivity, zone: Int) {
        val z = a.zones.zone(zone)!!
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, z.centerX, z.centerY + 30f, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, z.centerX, z.centerY + 30f, 0))
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(2000) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun elapsed(a: TenSecActivity) = a.game.elapsedMs(a.timers.now())

    private fun ranksOf(a: TenSecActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun fourPlayersThreeRoundsWithAutoCap() {
        val a = launch(four)
        assertFalse("목표 공개 중엔 터치 무시", a.zones.touchEnabled)
        repeat(GameConfig.ROUNDS) { r ->
            waitFor("라운드 ${r + 1} 시작") { a.game.phase == Phase.RUNNING }
            assertEquals(r + 1, a.game.roundNumber)
            val target = a.game.targetSec * 1000L
            assertSame(TenSecView.Show.CLOCK, a.board.state(0))
            assertTrue("시계 도는 중 프레임 루프", a.looping)
            waitFor("3초 지나 타이머 숨김") { elapsed(a) >= GameConfig.TIMER_VISIBLE_MS + 100 }
            assertSame(TenSecView.Show.HIDDEN, a.board.state(0))
            // 가람은 목표 근처, 나래는 1초 늦게, 다온은 2초 일찍, 라온은 안 누름(목표+10초 자동)
            waitFor("다온 차례") { elapsed(a) >= target - 2000 }
            press(a, 2)
            assertSame(TenSecView.Show.TAPPED, a.board.state(2))
            press(a, 2) // 두 번째는 무시
            waitFor("가람 차례") { elapsed(a) >= target }
            press(a, 0)
            waitFor("나래 차례") { elapsed(a) >= target + 1000 }
            press(a, 1)
            assertEquals(Phase.RUNNING, a.game.phase)
            waitFor("자동 기록") { a.game.phase == Phase.DONE }
            assertTrue(a.game.autoCapped[3])
            assertFalse(a.game.autoCapped[0])
            assertEquals(GameConfig.CAP_EXTRA_MS / 10, a.game.diffCs(3))
            assertFalse("공개 중엔 터치 무시", a.zones.touchEnabled)
            // 한 명씩 공개 → 가장 가까운 사람 강조
            waitFor("모두 공개") { (0 until 4).all { a.board.state(it) == TenSecView.Show.LANDED } }
        }
        val ranks = ranksOf(a)
        assertArrayEquals(intArrayOf(0, 1, 2, 3), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals(a.game.ranks(), ranks)
        assertEquals("안 누른 라온이 꼴찌", 4, ranks[3])
        assertEquals("목표 근처 가람이 1위", 1, ranks[0])
        idle(2000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun fourFingersAtOnceEachRegisterInOwnZoneOnly() {
        val a = launch(four, seed = 8)
        waitFor("시작") { a.game.phase == Phase.RUNNING }
        waitFor("4초") { elapsed(a) >= 4000 }
        // 창(루트) 좌표로 네 구역 가운데에 손가락이 하나씩 더해짐 (모두 누른 채) — 판 View 를 거쳐 구역으로 가야 한다
        val loc = IntArray(2)
        a.zones.getLocationInWindow(loc)
        val order = intArrayOf(3, 0, 1, 2)
        val xs = FloatArray(4) { a.zones.zone(order[it])!!.centerX + loc[0] }
        val ys = FloatArray(4) { a.zones.zone(order[it])!!.centerY + loc[1] }
        val t = SystemClock.uptimeMillis()
        val root = a.window.decorView
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs, ys, 1, t))
        assertEquals(1, a.game.recordCs.count { it >= 0 })
        assertTrue(a.game.recordCs[3] >= 0)
        for (k in 1 until 4) root.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN or (k shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs, ys, k + 1, t + k))
        assertTrue("네 명 모두 기록", a.game.allIn)
        assertEquals(Phase.DONE, a.game.phase)
        val rec = a.game.recordCs.copyOf()
        // 같은 순간이라 기록이 거의 같고, 구역마다 정확히 한 번
        assertTrue(rec.max() - rec.min() <= 1)
        for (z in 0 until 4) assertTrue("구역 $z 눌림", a.zones.isPressed(z))
        // 손가락을 떼고 다시 눌러도 바뀌지 않음
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_CANCEL, xs, ys, 4, t + 20))
        press(a, 1)
        assertArrayEquals(rec, a.game.recordCs)
    }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, n: Int, t: Long): MotionEvent {
        val props = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    @Test
    fun twoPlayersCloserWinsAndPauseFreezesClock() {
        val a = launch(four.take(2), seed = 3)
        repeat(GameConfig.ROUNDS) { r ->
            waitFor("라운드 시작") { a.game.phase == Phase.RUNNING }
            val target = a.game.targetSec * 1000L
            if (r == 0) {
                waitFor("2초") { elapsed(a) >= 2000 }
                @Suppress("DEPRECATION") a.onBackPressed()
                idle(100)
                assertTrue(PauseMenu.isShowing(a.popups))
                assertFalse("일시정지 중 프레임 멈춤", a.looping)
                val before = elapsed(a)
                a.onZoneDown(0, 10f, 10f, SystemClock.uptimeMillis()) // 일시정지 중 터치 무시
                idle(30_000)
                assertEquals("일시정지 중 시계 멈춤", before, elapsed(a))
                assertEquals("안전장치도 멈춤", Phase.RUNNING, a.game.phase)
                assertEquals(-1L, a.game.recordCs[0])
                @Suppress("DEPRECATION") a.onBackPressed()
                waitFor("계속하기") { !a.gamePaused }
                assertTrue("타이머가 아직 보이는 중이면 루프 재개", a.looping)
            }
            waitFor("목표") { elapsed(a) >= target + 200 }
            press(a, 0)
            waitFor("목표+1.5초") { elapsed(a) >= target + 1500 }
            press(a, 1)
            waitFor("공개") { a.game.phase == Phase.DONE }
        }
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
    }

    @Test
    fun moduleInfo() {
        assertEquals("tensec", TenSecModule.id)
        assertEquals(3, TenSecModule.rules.size)
        assertEquals(com.siwoo.party.common.ui.GameCategory.REFLEX, TenSecModule.category)
        assertEquals(4, TenSecModule.needsMultiTouch)
        assertEquals("9.84", TenSecActivity.secText(984))
        assertEquals("10.05", TenSecActivity.secText(1005))
        val out = CharArray(16)
        val n = TenSecView.fmtCs(1523, out, true)
        assertEquals("15.23초", String(out, 0, n))
        assertEquals("0.07", String(out, 0, TenSecView.fmtCs(7, out, false)))
    }
}
