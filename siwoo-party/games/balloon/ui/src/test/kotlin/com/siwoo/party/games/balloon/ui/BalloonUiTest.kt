package com.siwoo.party.games.balloon.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.balloon.BalloonModule
import com.siwoo.party.games.balloon.BalloonState
import com.siwoo.party.games.balloon.GameConfig
import com.siwoo.party.games.balloon.Phase
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

/** 풍선 치킨 게임 화면: 끝까지 진행 → 순위, 4손가락 동시 터치, 위쪽 구역 [멈춤] 판정, 시간 끝 자동 확정, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class BalloonUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val two = four.take(2)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { BalloonActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): BalloonActivity {
        BalloonActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), BalloonModule, players)
        return Robolectric.buildActivity(BalloonActivity::class.java, i).setup().get()
    }

    private fun tapAt(a: BalloonActivity, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    /** 구역 가운데 (풍선 쪽 — [멈춤] 버튼 아님) 탭 */
    private fun pump(a: BalloonActivity, zone: Int) {
        val z = a.zones.zone(zone)!!
        tapAt(a, z.centerX, z.centerY)
    }

    private fun pressStop(a: BalloonActivity, zone: Int) {
        val pt = FloatArray(2)
        a.stopButtonPoint(zone, pt)
        tapAt(a, pt[0], pt[1])
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitPlaying(a: BalloonActivity) = waitFor("라운드 시작") { a.game.phase == Phase.PLAYING && a.zones.touchEnabled }

    private fun ranksOf(a: BalloonActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    // 4손가락 동시 MotionEvent (공통 MultiTouchTest 방식)
    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun pointerAction(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    /** 손가락 4개를 하나씩 더해 모두 누른 채 → 하나씩 뗌. stage(겹친 그림 층 포함)로 보낸다 */
    private fun fourFingers(a: BalloonActivity, xs: FloatArray, ys: FloatArray) {
        val t = SystemClock.uptimeMillis()
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 4) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        for (z in 0 until 4) assertTrue("구역 $z 눌림", a.zones.isPressed(z))
        for (k in 3 downTo 1) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + 10 + k))
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, xs.copyOf(1), ys.copyOf(1), t + 20))
    }

    @Test
    fun twoPlayersThreeRoundsToTheEnd() {
        val a = launch(two)
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        for (r in 1..GameConfig.BALLOON_ROUNDS) {
            waitPlaying(a)
            assertEquals(r, a.game.roundNumber)
            assertTrue("라운드 중 프레임 루프", a.looping)
            // 가람: 터지는 크기보다 작게 키우고 멈춤
            val safe = minOf(5, a.game.popThreshold(0) - 1)
            repeat(safe) { pump(a, 0); idle(150) }
            assertEquals(safe, a.game.sizes[0])
            pressStop(a, 0)
            assertSame(BalloonState.STOPPED, a.game.states[0])
            // 멈춘 뒤 탭은 무시
            pump(a, 0)
            assertEquals(safe, a.game.sizes[0])
            // 나래: 터질 때까지
            var k = 0
            while (a.game.states[1] == BalloonState.INFLATING && k < 100) { pump(a, 1); idle(120); k++ }
            assertSame(BalloonState.POPPED, a.game.states[1])
            assertEquals(a.game.popThreshold(1), a.game.sizes[1])
            waitFor("라운드 끝") { a.game.roundsDone == r }
            assertEquals(safe, a.game.roundScores[r - 1][0])
            assertEquals(0, a.game.roundScores[r - 1][1])
        }
        assertTrue(a.game.gameOver)
        assertEquals(0, a.game.totals[1])
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        idle(2000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun fourFingersAtOnceEachInOwnZone() {
        val a = launch(four, seed = 9)
        waitPlaying(a)
        // 네 귀퉁이 구역 안쪽 (구역 가운데 근처, [멈춤] 버튼 아님) — 오른쪽 아래부터 하나씩 더함
        val z = IntArray(4) { intArrayOf(3, 0, 1, 2)[it] }
        val xs = FloatArray(4) { a.zones.zone(z[it])!!.centerX - 30f }
        val ys = FloatArray(4) { a.zones.zone(z[it])!!.centerY + 20f }
        fourFingers(a, xs, ys)
        assertArrayEquals("각자 자기 풍선만 1씩", intArrayOf(1, 1, 1, 1), a.game.sizes)
        idle(200)
        fourFingers(a, xs, ys)
        assertArrayEquals(intArrayOf(2, 2, 2, 2), a.game.sizes)
        idle(200)
        // 네 사람이 동시에 각자 [멈춤] (위쪽 구역은 돌린 위치)
        val pt = FloatArray(2)
        for (k in 0 until 4) { a.stopButtonPoint(z[k], pt); xs[k] = pt[0]; ys[k] = pt[1] }
        fourFingers(a, xs, ys)
        for (p in 0 until 4) assertSame("구역 $p 멈춤", BalloonState.STOPPED, a.game.states[p])
        assertArrayEquals(intArrayOf(2, 2, 2, 2), a.game.sizes)
        waitFor("라운드 끝") { a.game.roundsDone == 1 }
        assertArrayEquals(intArrayOf(2, 2, 2, 2), a.game.totals)
    }

    @Test
    fun topZoneStopButtonIsRotated() {
        val a = launch(four, seed = 3)
        waitPlaying(a)
        val top = a.zones.zone(0)!!
        assertEquals(180f, top.rotation, 0f)
        val bottom = a.zones.zone(2)!!
        val s = a.art.layout.stop
        // 위쪽 구역에서 "돌리지 않은" 버튼 자리 = 버튼이 아니라 풍선 탭
        tapAt(a, top.centerX + s.centerX, top.centerY + s.centerY)
        assertSame(BalloonState.INFLATING, a.game.states[0])
        assertEquals(1, a.game.sizes[0])
        // 돌린 자리(화면 왼쪽 위 방향)가 진짜 [멈춤]
        val pt = FloatArray(2)
        a.stopButtonPoint(0, pt)
        assertTrue("위쪽 구역 버튼은 구역 가운데보다 왼쪽 위", pt[0] < top.centerX && pt[1] < top.centerY)
        assertTrue(top.contains(pt[0], pt[1]))
        idle(150)
        tapAt(a, pt[0], pt[1])
        assertSame(BalloonState.STOPPED, a.game.states[0])
        assertEquals(1, a.game.sizes[0])
        // 아래쪽 구역은 돌리지 않은 자리 (오른쪽 아래)
        a.stopButtonPoint(2, pt)
        assertEquals(bottom.centerX + s.centerX, pt[0], 0.5f)
        assertEquals(bottom.centerY + s.centerY, pt[1], 0.5f)
        tapAt(a, pt[0], pt[1])
        assertSame(BalloonState.STOPPED, a.game.states[2])
        assertEquals(0, a.game.sizes[2])
        // 다른 구역은 그대로
        assertSame(BalloonState.INFLATING, a.game.states[1])
        assertSame(BalloonState.INFLATING, a.game.states[3])
    }

    @Test
    fun timeUpAutoConfirmsCurrentSize() {
        val a = launch(two, seed = 1)
        waitPlaying(a)
        repeat(3) { pump(a, 0); idle(150) }
        waitFor("시간 끝 → 라운드 끝") { a.game.roundsDone == 1 }
        assertArrayEquals("3 은 터지는 크기(${GameConfig.POP_MIN} 이상)보다 작음", intArrayOf(3, 0), a.game.roundScores[0])
        assertSame(Phase.ROUND_OVER, a.game.phase)
        assertFalse(a.zones.touchEnabled)
        waitPlaying(a)
        assertEquals(2, a.game.roundNumber)
        assertArrayEquals(intArrayOf(0, 0), a.game.sizes)
    }

    @Test
    fun pausedTapsIgnoredAndClockStops() {
        val a = launch(two)
        waitPlaying(a)
        pump(a, 0)
        assertEquals(1, a.game.sizes[0])
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지 중 프레임 멈춤", a.looping)
        repeat(5) { a.onZoneDown(0, 10f, 10f, SystemClock.uptimeMillis()); idle(100) }
        idle(GameConfig.ROUND_TIME_MS + 5000)
        assertEquals(1, a.game.sizes[0])
        assertSame("일시정지 중 시간이 가지 않음", Phase.PLAYING, a.game.phase)
        assertSame(BalloonState.INFLATING, a.game.states[0])
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        idle(200)
        pump(a, 0)
        assertEquals(2, a.game.sizes[0])
    }

    @Test
    fun moduleInfo() {
        assertEquals("balloon", BalloonModule.id)
        assertSame(GameCategory.REFLEX, BalloonModule.category)
        assertEquals(3, BalloonModule.rules.size)
        assertEquals(4, BalloonModule.needsMultiTouch)
        assertTrue(BalloonModule.supports(2) && BalloonModule.supports(3) && BalloonModule.supports(4))
    }
}
