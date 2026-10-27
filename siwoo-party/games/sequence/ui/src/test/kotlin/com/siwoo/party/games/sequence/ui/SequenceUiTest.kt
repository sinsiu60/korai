package com.siwoo.party.games.sequence.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.sequence.GameConfig
import com.siwoo.party.games.sequence.Phase
import com.siwoo.party.games.sequence.SequenceModule
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

/** 숫자 순서 터치 화면: 3라운드 끝까지 → 순위, 오답 정지, 4손가락 동시 터치 구역 구분, 위쪽(180°) 구역 칸 판정, 시간 제한, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class SequenceUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { SequenceActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): SequenceActivity {
        SequenceActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), SequenceModule, players)
        return Robolectric.buildActivity(SequenceActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(2400) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitPlaying(a: SequenceActivity) = waitFor("라운드 시작") { a.game.phase == Phase.PLAYING && a.zones.touchEnabled }

    private val pt = PointF()

    private fun cellOf(a: SequenceActivity, n: Int) = a.game.layout.indexOf(n)

    /** zone 에서 숫자 n 칸을 톡 */
    private fun tapNumber(a: SequenceActivity, zone: Int, n: Int) {
        a.board.cellCenter(zone, cellOf(a, n), pt)
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    private fun runAll(a: SequenceActivity, zone: Int) {
        for (n in a.game.next[zone]..GameConfig.CELLS) tapNumber(a, zone, n)
    }

    @Test
    fun fourPlayersThreeRoundsReturnTotalRanks() {
        val a = launch(four)
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        waitPlaying(a)
        // 오답 → 0.5초 정지, 그동안 맞는 숫자도 무시
        tapNumber(a, 1, 5)
        assertTrue(a.game.isFrozen(1, a.timers.now()))
        tapNumber(a, 1, 1)
        assertEquals(1, a.game.next[1])
        waitFor("정지 풀림") { !a.game.isFrozen(1, a.timers.now()) }
        tapNumber(a, 1, 1)
        assertEquals(2, a.game.next[1])
        // 라운드 순서: R1 0,1,2 / R2 1,0,3 / R3 0,3,1  → 0: 4+3+4=11, 1: 3+4+2=9, 2: 2+1+1=4, 3: 1+2+3=6
        val orders = listOf(intArrayOf(0, 1, 2), intArrayOf(1, 0, 3), intArrayOf(0, 3, 1))
        for ((r, o) in orders.withIndex()) {
            if (r > 0) waitPlaying(a)
            for (z in o) runAll(a, z)
            assertEquals("라운드 ${r + 1} 끝", r + 1, a.game.roundsDone)
            assertFalse(a.zones.touchEnabled)
        }
        assertArrayEquals(intArrayOf(11, 9, 4, 6), a.game.totalPoints)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1, 2, 3), data.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals(intArrayOf(1, 2, 4, 3), data.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun pointerAction(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    @Test
    fun fourFingersAtOnceEachCountOnlyInOwnZone() {
        val a = launch(four, seed = 9)
        waitPlaying(a)
        // 구역 3 → 0 → 1 → 2 순서로 손가락이 더해짐 (모두 누른 채). 구역 1 만 틀린 숫자(2)
        val zonesOrder = intArrayOf(3, 0, 1, 2)
        val numbers = intArrayOf(1, 1, 2, 1)
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        for (k in 0 until 4) {
            a.board.cellCenter(zonesOrder[k], cellOf(a, numbers[k]), pt)
            xs[k] = pt.x
            ys[k] = pt.y
        }
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 4) a.zones.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        for (z in 0 until 4) assertTrue("구역 $z 눌림", a.zones.isPressed(z))
        assertArrayEquals("각자 자기 구역에서만 진행", intArrayOf(2, 1, 2, 2), a.game.next)
        val now = a.timers.now()
        assertTrue(a.game.isFrozen(1, now))
        for (z in intArrayOf(0, 2, 3)) assertFalse(a.game.isFrozen(z, now))
        for (k in 3 downTo 1) a.zones.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + 20))
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, xs.copyOf(1), ys.copyOf(1), t + 30))
        for (z in 0 until 4) assertFalse(a.zones.isPressed(z))
    }

    @Test
    fun topZoneCellsAreRotated() {
        val a = launch(four, seed = 3)
        waitPlaying(a)
        for (zone in 0 until 4) {
            val z = a.zones.zone(zone)!!
            val first = PointF(); val last = PointF()
            a.board.cellCenter(zone, 0, first)  // 플레이어 기준 왼쪽 위
            a.board.cellCenter(zone, 15, last)  // 플레이어 기준 오른쪽 아래
            if (z.rotation == 180f) {
                assertTrue("위쪽 구역 $zone: 첫 칸이 화면 오른쪽 아래", first.x > last.x && first.y > last.y)
            } else {
                assertTrue("아래 구역 $zone: 첫 칸이 화면 왼쪽 위", first.x < last.x && first.y < last.y)
            }
            // 모든 칸: 화면 좌표 → 같은 칸, 그리고 그 구역 안
            for (cell in 0 until GameConfig.CELLS) {
                a.board.cellCenter(zone, cell, pt)
                assertTrue(z.contains(pt.x, pt.y))
                assertEquals("구역 $zone 칸 $cell", cell, a.board.cellAt(zone, pt.x, pt.y))
            }
        }
        // 같은 배치를 180° 돌린 자리: 위 구역 0 의 칸 = 아래 구역 2 의 칸을 구역 가운데 기준으로 뒤집은 곳
        val top = a.zones.zone(0)!!
        val bottom = a.zones.zone(2)!!
        val pTop = PointF(); val pBottom = PointF()
        a.board.cellCenter(0, 6, pTop)
        a.board.cellCenter(2, 6, pBottom)
        assertEquals(top.centerX - (pBottom.x - bottom.centerX), pTop.x, 0.5f)
        assertEquals(top.centerY - (pBottom.y - bottom.centerY), pTop.y, 0.5f)
        // 실제 터치: 위쪽 구역 1 에서 1, 2, 3
        tapNumber(a, 1, 1); tapNumber(a, 1, 2); tapNumber(a, 1, 3)
        assertEquals(4, a.game.next[1])
        assertEquals(1, a.game.next[0])
    }

    @Test
    fun timeLimitEndsRoundByProgressAndPauseStopsIt() {
        val a = launch(four.take(2), seed = 4)
        waitPlaying(a)
        for (n in 1..5) tapNumber(a, 0, n)
        for (n in 1..3) tapNumber(a, 1, n)
        // 일시정지: 제한 시간이 흐르지 않고 터치 무시
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        tapNumber(a, 1, 4)
        idle(GameConfig.ROUND_TIME_LIMIT_MS + 5000)
        assertEquals(Phase.PLAYING, a.game.phase)
        assertEquals(4, a.game.next[1])
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        waitFor("시간 끝") { a.game.roundsDone == 1 }
        assertArrayEquals("누른 개수 순", intArrayOf(1, 2), a.game.roundPlaces)
        assertArrayEquals(intArrayOf(2, 1), a.game.totalPoints)
        waitPlaying(a)
        assertEquals(2, a.game.roundNumber)
    }

    @Test
    fun moduleInfo() {
        assertEquals("sequence", SequenceModule.id)
        assertEquals(3, SequenceModule.rules.size)
        assertTrue(SequenceModule.supports(2) && SequenceModule.supports(3) && SequenceModule.supports(4))
        assertEquals(4, SequenceModule.requiredTouches(4))
    }
}
