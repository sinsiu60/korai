package com.siwoo.party.games.count.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.count.CountModule
import com.siwoo.party.games.count.GameConfig
import com.siwoo.party.games.count.Phase
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

/** 몇 개였지? 화면: 12라운드 끝까지 → 순위(2·3·4인), 잠금, 4손가락 동시 터치, 그림 보는 중 무시, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class CountUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { CountActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): CountActivity {
        CountActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), CountModule, players)
        return Robolectric.buildActivity(CountActivity::class.java, i).setup().get()
    }

    private fun tapAt(a: CountActivity, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun choose(a: CountActivity, zone: Int, k: Int) {
        val pt = FloatArray(2)
        a.choicePoint(zone, k, pt)
        tapAt(a, pt[0], pt[1])
    }

    private fun wrongOf(a: CountActivity, d: Int = 1) = (a.game.current.correctIndex + d) % GameConfig.CHOICES

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(2000) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitAsking(a: CountActivity, number: Int) =
        waitFor("${number}라운드 보기") { a.game.phase == Phase.ASKING && a.game.number == number && a.zones.touchEnabled }

    private fun ranksOf(a: CountActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun pointerAction(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    /** 손가락 4개를 하나씩 더해 모두 누른 채 → 뗌. stage(겹친 그림 층 포함)로 보낸다 */
    private fun fourFingers(a: CountActivity, xs: FloatArray, ys: FloatArray) {
        val t = SystemClock.uptimeMillis()
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 4) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        for (z in 0 until 4) assertTrue("구역 $z 눌림", a.zones.isPressed(z))
        for (k in 3 downTo 1) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + 10 + k))
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, xs.copyOf(1), ys.copyOf(1), t + 20))
    }

    /** winners[n] = n+1 라운드를 맞힐 사람 (-1 = 아무도 안 누름 → 시간 끝) */
    private fun play(a: CountActivity, winners: IntArray) {
        assertEquals(GameConfig.ROUNDS, winners.size)
        for (n in 1..GameConfig.ROUNDS) {
            waitAsking(a, n)
            assertFalse("보기 중엔 그림이 가려짐", a.board.dotsVisible)
            val w = winners[n - 1]
            if (w >= 0) {
                choose(a, w, a.game.current.correctIndex)
                assertEquals(w, a.game.winner)
                assertTrue("공개 때 그림 다시", a.board.dotsVisible)
            }
            if (n < GameConfig.ROUNDS) waitFor("${n}라운드 끝") { a.game.phase != Phase.ASKING }
        }
        waitFor("게임 끝") { a.game.gameOver }
    }

    @Test
    fun twoPlayersToTheEnd() {
        val a = launch(four.take(2))
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        // 1라운드: 그림 보이는 동안은 보기 누름 무시
        waitFor("그림 보임") { a.game.number == 1 && a.board.dotsVisible }
        assertSame(Phase.SHOWING, a.game.phase)
        choose(a, 0, a.game.current.correctIndex)
        val pt = FloatArray(2)
        a.choicePoint(0, a.game.current.correctIndex, pt)
        a.onZoneDown(0, pt[0], pt[1], SystemClock.uptimeMillis())
        assertArrayEquals(intArrayOf(0, 0), a.game.scores)
        assertEquals(0, a.game.wrongMask[0])
        // 2라운드: 가람이 틀림 → 잠금 → 정답 눌러도 무시 → 나래가 맞힘
        waitAsking(a, 1)
        assertTrue("답하는 중 프레임 루프", a.looping)
        choose(a, 1, a.game.current.correctIndex)
        waitAsking(a, 2)
        val c = a.game.current.correctIndex
        choose(a, 0, wrongOf(a))
        assertTrue(a.game.isLocked(0, a.timers.now()))
        idle(100)
        assertTrue("잠금 덮개", a.board.isLockShown(0))
        choose(a, 0, c)
        assertEquals("잠금 중 정답 무시", -1, a.game.winner)
        choose(a, 1, c)
        assertEquals(1, a.game.winner)
        // 나머지: 3~8 가람, 9~11 나래, 12 시간 끝
        for (n in 3..GameConfig.ROUNDS) {
            waitAsking(a, n)
            when {
                n <= 8 -> choose(a, 0, a.game.current.correctIndex)
                n <= 11 -> choose(a, 1, a.game.current.correctIndex)
                else -> Unit
            }
        }
        waitFor("마지막 라운드 시간 끝") { a.game.gameOver }
        assertEquals(-1, a.game.winner)
        assertArrayEquals(intArrayOf(6, 5), a.game.scores)
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        idle(2000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun threePlayersToTheEnd() {
        val a = launch(four.take(3), seed = 8)
        // 1~11라운드 n%3 이 맞힘, 12라운드 시간 끝 → 3, 4, 4점
        play(a, IntArray(GameConfig.ROUNDS) { if (it == GameConfig.ROUNDS - 1) -1 else (it + 1) % 3 })
        assertArrayEquals(intArrayOf(3, 4, 4), a.game.scores)
        assertArrayEquals(intArrayOf(3, 1, 1), ranksOf(a))
    }

    @Test
    fun fourPlayersToTheEnd() {
        val a = launch(four, seed = 11)
        // 1~5라운드 라온, 6~12라운드 n%3 → 3, 2, 2, 5점
        play(a, IntArray(GameConfig.ROUNDS) { if (it < 5) 3 else (it + 1) % 3 })
        assertArrayEquals(intArrayOf(3, 2, 2, 5), a.game.scores)
        assertArrayEquals(intArrayOf(2, 3, 3, 1), ranksOf(a))
    }

    @Test
    fun fourFingersAtOnceEachInOwnZone() {
        val a = launch(four, seed = 9)
        waitAsking(a, 1)
        val order = intArrayOf(3, 0, 1, 2)
        val pt = FloatArray(2)
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        // 네 사람 동시에 각자 다른 오답 → 각자 자기 구역만 잠김
        val picked = IntArray(4)
        for (k in 0 until 4) {
            val z = order[k]
            picked[z] = wrongOf(a, 1 + z % 3)
            a.choicePoint(z, picked[z], pt); xs[k] = pt[0]; ys[k] = pt[1]
        }
        fourFingers(a, xs, ys)
        for (z in 0 until 4) {
            assertEquals("구역 $z 는 자기가 누른 보기만 틀림", 1 shl picked[z], a.game.wrongMask[z])
            assertTrue(a.game.isLocked(z, a.timers.now()))
        }
        assertArrayEquals(intArrayOf(0, 0, 0, 0), a.game.scores)
        assertSame(Phase.ASKING, a.game.phase)
        // 잠금 중 네 사람 동시에 정답 → 모두 무시
        val c = a.game.current.correctIndex
        for (k in 0 until 4) { a.choicePoint(order[k], c, pt); xs[k] = pt[0]; ys[k] = pt[1] }
        fourFingers(a, xs, ys)
        assertEquals(-1, a.game.winner)
        // 잠금이 풀린 뒤 네 사람 동시에 정답 → 먼저 닿은 손가락(구역 3)만 +1
        idle(GameConfig.LOCK_MS + 100)
        for (z in 0 until 4) assertFalse(a.game.isLocked(z, a.timers.now()))
        fourFingers(a, xs, ys)
        assertEquals(3, a.game.winner)
        assertArrayEquals(intArrayOf(0, 0, 0, 1), a.game.scores)
        assertTrue(a.zones.isPressed(3).not())
        waitAsking(a, 2)
        assertArrayEquals("새 라운드 → 틀린 표시 초기화", intArrayOf(0, 0, 0, 0), a.game.wrongMask)
    }

    @Test
    fun topZoneChoicesAreRotated() {
        val a = launch(four, seed = 3)
        waitAsking(a, 1)
        val p0 = FloatArray(2)
        val p3 = FloatArray(2)
        a.choicePoint(0, 0, p0)
        a.choicePoint(0, 3, p3)
        assertTrue("위쪽 구역 보기 순서는 화면에서 오른쪽→왼쪽", p0[0] > p3[0])
        val top = a.zones.zone(0)!!
        assertTrue(top.contains(p0[0], p0[1]) && top.contains(p3[0], p3[1]))
        // 그림 칸을 누르면 무시
        val f = a.board.layout.flash
        tapAt(a, f.centerX, f.centerY)
        tapAt(a, f.left + 4f, f.top + 4f)
        for (z in 0 until 4) assertEquals(0, a.game.wrongMask[z])
        val c = a.game.current.correctIndex
        choose(a, 1, c)
        assertEquals(1, a.game.winner)
    }

    @Test
    fun timeUpRevealsAndMovesOn() {
        val a = launch(four.take(2), seed = 4)
        waitAsking(a, 1)
        idle(GameConfig.ANSWER_TIME_MS + 200)
        waitFor("시간 끝") { a.game.phase == Phase.CLOSED }
        assertTrue("시간 끝 → 그림 다시", a.board.dotsVisible)
        assertArrayEquals(intArrayOf(0, 0), a.game.scores)
        waitAsking(a, 2)
    }

    @Test
    fun pauseFreezesFlashAndAnswerTime() {
        val a = launch(four.take(2))
        // 그림이 보이는 순간 일시정지 → 그림이 그대로, 라운드가 넘어가지 않음
        waitFor("그림 보임") { a.game.number == 1 && a.board.dotsVisible }
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        idle(5000)
        assertSame(Phase.SHOWING, a.game.phase)
        assertTrue(a.board.dotsVisible)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        waitAsking(a, 1)
        // 답하는 중 일시정지 → 시간이 가지 않고 누름 무시
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지 중 프레임 멈춤", a.looping)
        val c = a.game.current.correctIndex
        val pt = FloatArray(2)
        a.choicePoint(0, c, pt)
        a.onZoneDown(0, pt[0], pt[1], SystemClock.uptimeMillis())
        idle(GameConfig.ANSWER_TIME_MS + 5000)
        assertSame("일시정지 중 시간이 가지 않음", Phase.ASKING, a.game.phase)
        assertArrayEquals(intArrayOf(0, 0), a.game.scores)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        choose(a, 0, c)
        assertArrayEquals(intArrayOf(1, 0), a.game.scores)
    }

    @Test
    fun moduleInfo() {
        assertEquals("count", CountModule.id)
        assertEquals("몇 개였지?", CountModule.name)
        assertSame(GameCategory.REFLEX, CountModule.category)
        assertSame(PlayMode.SIMULTANEOUS, CountModule.mode)
        assertEquals("3분", CountModule.minutes)
        assertEquals(3, CountModule.rules.size)
        assertEquals(4, CountModule.needsMultiTouch)
        assertTrue(CountModule.supports(2) && CountModule.supports(3) && CountModule.supports(4))
        assertFalse(CountModule.supports(1) || CountModule.supports(5))
    }
}
