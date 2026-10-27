package com.siwoo.party.games.bobber.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.bobber.BobberModule
import com.siwoo.party.games.bobber.GameConfig
import com.siwoo.party.games.bobber.Phase
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

/** 낚시 찌 대결 화면: 4손가락 동시 터치, 가짜 입질 실격, 2·3·4인 끝까지, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class BobberUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { BobberActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): BobberActivity {
        BobberActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), BobberModule, players)
        return Robolectric.buildActivity(BobberActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(3000) { if (cond()) return; idle(20) }
        error("$what 가 되지 않음")
    }

    private fun press(a: BobberActivity, zone: Int) {
        val z = a.zones.zone(zone)!!
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, z.centerX, z.centerY, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 5, MotionEvent.ACTION_UP, z.centerX, z.centerY, 0))
    }

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun bite(a: BobberActivity) = a.game.schedule!!.biteAt

    /** 진짜 입질 + extra ms 까지 기다림 */
    private fun waitBite(a: BobberActivity, extra: Long = 60) {
        waitFor("라운드 시작") { a.roundOpen }
        waitFor("진짜 입질") { a.roundT() >= bite(a) + extra }
    }

    private fun ranksOf(a: BobberActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun fourFingersAfterBiteEarliestWins() {
        val a = launch(four)
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        waitBite(a, 300)
        // 처리 순서 3 → 0 → 2 → 1, 터치 시각은 1 이 가장 빠르다 (MotionEvent 시각)
        val order = intArrayOf(3, 0, 2, 1)
        val lag = longArrayOf(10, 40, 25, 90)
        val xs = FloatArray(4) { a.zones.zone(order[it])!!.centerX }
        val ys = FloatArray(4) { a.zones.zone(order[it])!!.centerY }
        val layout = TouchZones.layout(4, a.zones.width.toFloat(), a.zones.height.toFloat(), 10f * a.resources.displayMetrics.density)
        for (k in 0 until 4) assertEquals("구역 판정", order[k], TouchZones.zoneAt(layout, xs[k], ys[k]))
        val now = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), now - lag[0]))
        for (k in 1 until 4) {
            a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN or (k shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs.copyOf(k + 1), ys.copyOf(k + 1), now - lag[k]))
        }
        for (z in 0 until 4) assertTrue("구역 $z 눌림", a.zones.isPressed(z))
        // 모두 눌렀으니 바로 판정
        assertFalse(a.roundOpen)
        assertEquals(Phase.DONE, a.game.phase)
        for (z in 0 until 4) assertTrue("구역 $z 반응 기록", a.game.reaction[z] >= 0)
        assertArrayEquals(intArrayOf(1), a.game.lastResult!!.winners)
        assertArrayEquals(intArrayOf(0, 1, 0, 0), a.game.score)
        assertTrue(a.game.reaction[1] < a.game.reaction[3])
        assertEquals("MotionEvent 시각 차이 그대로", 80L, a.game.reaction[3] - a.game.reaction[1])
    }

    @Test
    fun fingerDuringFakeDisqualifiesOnlyThatZone() {
        val a = launch(four.take(3), seed = 3)
        var checked = false
        while (!checked) {
            waitFor("라운드 시작") { a.roundOpen }
            val s = a.game.schedule!!
            if (s.fakeCount == 0) {
                waitBite(a)
                press(a, 0)
                waitFor("라운드 끝") { !a.roundOpen }
                continue
            }
            waitFor("가짜 입질") { s.isFakeAt(a.roundT()) && s.isFakeAt(a.roundT() + 20) }
            press(a, 1)
            assertTrue(a.game.disqualified[1])
            assertTrue(a.pond.isDisqualified(1))
            assertFalse(a.game.disqualified[0])
            assertFalse(a.game.disqualified[2])
            assertFalse(a.pond.isDisqualified(0))
            assertTrue("다른 사람은 계속", a.roundOpen)
            waitBite(a)
            assertSame(a.game.schedule, s)
            press(a, 1) // 실격자는 무시
            assertEquals(-1L, a.game.reaction[1])
            press(a, 2)
            waitFor("라운드 끝") { !a.roundOpen }
            assertArrayEquals(intArrayOf(2), a.game.lastResult!!.winners)
            assertEquals(0, a.game.score[1])
            // 다음 라운드엔 실격이 풀린다
            waitFor("다음 라운드") { a.roundOpen }
            assertFalse(a.game.disqualified[1])
            assertFalse(a.pond.isDisqualified(1))
            checked = true
        }
    }

    /** plan(라운드) = 그 라운드에 누를 사람 (-1 = 아무도) */
    private fun playAll(a: BobberActivity, plan: (Int) -> Int) {
        for (r in 0 until GameConfig.ROUNDS) {
            waitFor("라운드 ${r + 1}") { a.roundOpen }
            assertEquals(r, a.game.roundsDone)
            val w = plan(r)
            if (w >= 0) {
                waitBite(a)
                press(a, w)
            }
            waitFor("라운드 ${r + 1} 끝") { !a.roundOpen }
            if (r == 0) {
                // 결과 연출이 끝나면 다음 라운드 전까지 프레임 루프가 멈춘다
                waitFor("루프 멈춤") { !a.looping || a.roundOpen }
                assertFalse("연출 뒤 대기 중엔 다시 그리지 않음", a.looping)
            }
        }
    }

    @Test
    fun twoPlayersFullGame() {
        val a = launch(four.take(2), seed = 8)
        playAll(a) { 1 }
        assertArrayEquals(intArrayOf(0, 10), a.game.score)
        assertArrayEquals(intArrayOf(2, 1), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        assertFalse("끝나면 루프 멈춤", a.looping)
    }

    @Test
    fun threePlayersFullGameWithMissedRound() {
        val a = launch(four.take(3), seed = 9)
        // 0 이 4번 (0·3·6·9 라운드), 2 가 5번, 1 라운드(7)는 아무도 안 누름
        playAll(a) { r -> if (r == 7) -1 else if (r % 3 == 0) 0 else 2 }
        assertEquals(GameConfig.ROUNDS, a.game.roundsDone)
        assertArrayEquals(intArrayOf(4, 0, 5), a.game.score)
        assertArrayEquals(intArrayOf(2, 3, 1), ranksOf(a))
    }

    @Test
    fun fourPlayersFullGameTie() {
        val a = launch(four, seed = 10)
        playAll(a) { r -> if (r % 2 == 0) 0 else 3 }
        assertArrayEquals(intArrayOf(5, 0, 0, 5), a.game.score)
        assertArrayEquals(intArrayOf(1, 3, 3, 1), ranksOf(a))
    }

    @Test
    fun pauseFreezesTheSchedule() {
        val a = launch(four.take(2), seed = 4)
        waitFor("라운드 시작") { a.roundOpen && a.roundT() > 100 }
        val s = a.game.schedule!!
        assertTrue(a.roundT() < s.biteAt - 300)
        assertTrue(a.looping)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(50)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지 중 그리기 멈춤", a.looping)
        val t0 = a.roundT()
        idle(20_000)
        assertEquals("일시정지 중 라운드 시계 멈춤", t0, a.roundT())
        assertTrue(a.roundOpen)
        assertEquals(Phase.OPEN, a.game.phase)
        assertSame("같은 예정표", s, a.game.schedule)
        assertEquals(0, a.game.roundsDone)
        // 일시정지 중 구역 터치는 무시 (실격도 아님)
        a.onZoneDown(0, 0f, 0f, SystemClock.uptimeMillis())
        assertFalse(a.game.disqualified[0])
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        // 이어서 같은 라운드: 진짜 입질 전 터치는 실격, 입질 뒤 터치는 승리
        assertTrue(a.roundT() < s.biteAt)
        press(a, 0)
        assertTrue(a.game.disqualified[0])
        waitBite(a)
        assertSame(s, a.game.schedule)
        press(a, 1)
        waitFor("라운드 끝") { !a.roundOpen }
        assertArrayEquals(intArrayOf(0, 1), a.game.score)
        assertEquals(1, a.game.roundsDone)
    }

    @Test
    fun moduleInfo() {
        assertEquals("bobber", BobberModule.id)
        assertEquals("낚시 찌 대결", BobberModule.name)
        assertEquals(GameCategory.REFLEX, BobberModule.category)
        assertEquals(PlayMode.SIMULTANEOUS, BobberModule.mode)
        assertEquals("3분", BobberModule.minutes)
        assertEquals(3, BobberModule.rules.size)
        assertEquals(2..4, BobberModule.playerCounts)
        assertEquals(4, BobberModule.requiredTouches(4))
        assertEquals(10, GameConfig.ROUNDS)
    }
}
