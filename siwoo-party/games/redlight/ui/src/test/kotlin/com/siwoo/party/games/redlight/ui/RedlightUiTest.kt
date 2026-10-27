package com.siwoo.party.games.redlight.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.redlight.GameConfig
import com.siwoo.party.games.redlight.Phase
import com.siwoo.party.games.redlight.RedlightModule
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

/** 무궁화 꽃이 피었습니다 화면: 4손가락 동시 누름, 2인 끝까지, 제한 시간 순위, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class RedlightUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { RedlightActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): RedlightActivity {
        RedlightActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), RedlightModule, players)
        return Robolectric.buildActivity(RedlightActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(3000) { if (cond()) return; idle(30) }
        error("$what 가 되지 않음")
    }

    private fun waitChant(a: RedlightActivity) = waitFor("경기 시작") { a.game.phase == Phase.CHANT }

    /** 한 손가락 누르기/떼기 (다른 손가락이 없을 때) */
    private fun down(a: RedlightActivity, zone: Int) {
        val z = a.zones.zone(zone)!!
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, z.centerX, z.centerY, 0))
    }

    private fun up(a: RedlightActivity, zone: Int) {
        val z = a.zones.zone(zone)!!
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, z.centerX, z.centerY, 0))
    }

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun pointerAction(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    private fun ranksOf(a: RedlightActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun fourFingersAtOnceEachHoldOnlyTheirOwnZone() {
        val a = launch(four)
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        waitChant(a)
        assertTrue(a.zones.touchEnabled)
        assertEquals(180f, a.zones.zone(0)!!.rotation)
        assertEquals(0f, a.zones.zone(3)!!.rotation)
        // 오른쪽 아래(3) → 왼쪽 위(0) → 오른쪽 위(1) → 왼쪽 아래(2) 순서로 손가락 4개를 모두 누른 채로
        val order = intArrayOf(3, 0, 1, 2)
        val xs = FloatArray(4) { a.zones.zone(order[it])!!.centerX }
        val ys = FloatArray(4) { a.zones.zone(order[it])!!.centerY }
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        assertTrue(a.game.holding[3])
        assertFalse(a.game.holding[0] || a.game.holding[1] || a.game.holding[2])
        for (k in 1 until 4) {
            a.zones.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
            for (q in 0 until 4) assertEquals("손가락 ${k + 1}개: 구역 $q", q in order.copyOf(k + 1), a.game.holding[q])
        }
        for (z in 0 until 4) assertTrue(a.zones.isPressed(z))
        idle(120)
        assertEquals(Phase.CHANT, a.game.phase)
        val p0 = a.game.progress[0]
        assertTrue(p0 > 0.0)
        for (z in 1 until 4) assertEquals("모두 같이 누르면 같이 전진", p0, a.game.progress[z], 1e-9)
        // 두 번째 손가락(왼쪽 위 = 구역 0)만 뗌
        a.zones.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, 1), xs, ys, t + 10))
        assertFalse(a.game.holding[0])
        assertTrue(a.game.holding[1] && a.game.holding[2] && a.game.holding[3])
        val frozen = a.game.progress[0]
        idle(80)
        if (a.game.phase == Phase.CHANT) {
            assertEquals("뗀 구역은 멈춤", frozen, a.game.progress[0], 1e-12)
            assertTrue(a.game.progress[1] > frozen)
        }
    }

    @Test
    fun twoPlayersPlayToTheEnd() {
        val a = launch(four.take(2), seed = 8)
        waitChant(a)
        assertTrue("경기 중 프레임 루프", a.looping)
        var held = false
        var caughtTried = false
        var k = 0
        while (!a.game.isOver && k < 4000) {
            if (a.game.phase == Phase.CHANT && !held) { down(a, 0); held = true }
            if (a.game.phase == Phase.LOOK && held) { up(a, 0); held = false }
            // 나래: 술래가 쳐다보는 중(유예 뒤)에 한 번 눌러 걸림
            if (!caughtTried && a.game.watching && !held) {
                down(a, 1)
                assertEquals(1, a.game.caughtCount[1])
                up(a, 1)
                caughtTried = true
            }
            idle(30)
            k++
        }
        assertTrue(a.game.isOver)
        assertFalse(a.game.timeUp)
        assertEquals(0, a.game.caughtCount[0])
        assertTrue(caughtTried)
        assertEquals(listOf(0), a.game.arrivalOrder)
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        idle(3000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun timeUpRanksByDistanceWithTies() {
        val a = launch(four.take(3), seed = 2)
        waitChant(a)
        down(a, 1)
        idle(200)
        up(a, 1)
        assertTrue(a.game.progress[1] > 0.0)
        waitFor("시간 종료") { a.game.isOver }
        assertTrue(a.game.timeUp)
        assertArrayEquals(intArrayOf(2, 1, 2), ranksOf(a))
    }

    @Test
    fun pauseFreezesRunnerAndClock() {
        val a = launch(four.take(2), seed = 4)
        waitChant(a)
        down(a, 0)
        idle(150)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(50)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지 중 프레임 멈춤", a.looping)
        assertFalse("일시정지 중엔 누르지 않은 것으로", a.game.holding[0])
        val p = a.game.progress[0]
        val remain = a.game.remainingMs()
        val phase = a.game.phase
        idle(10_000)
        assertEquals(p, a.game.progress[0], 0.0)
        assertEquals(remain, a.game.remainingMs())
        assertEquals(phase, a.game.phase)
        // 계속하기 → 아직 손가락이 눌려 있으니 다시 전진
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        assertTrue(a.game.holding[0])
        idle(60)
        if (a.game.phase == Phase.CHANT) assertTrue(a.game.progress[0] > p)
        up(a, 0)
        assertFalse(a.game.holding[0])
    }

    @Test
    fun moduleInfo() {
        assertEquals("redlight", RedlightModule.id)
        assertEquals(GameCategory.REFLEX, RedlightModule.category)
        assertEquals(3, RedlightModule.rules.size)
        assertEquals(2..4, RedlightModule.playerCounts)
        assertEquals(4, RedlightModule.requiredTouches(4))
        assertEquals(90_000L, GameConfig.TIME_LIMIT_MS)
    }
}
