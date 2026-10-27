package com.siwoo.party.games.spotdiff.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.spotdiff.GameConfig
import com.siwoo.party.games.spotdiff.Phase
import com.siwoo.party.games.spotdiff.Scene
import com.siwoo.party.games.spotdiff.SpotDiffModule
import com.siwoo.party.games.spotdiff.SpotGame
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** 틀린 그림 찾기 화면: 끝까지 진행 → 순위(2·3·4인), 4손가락 동시 버저, 오답·시간 초과 잠금, 장면 시간 끝, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class SpotDiffUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { SpotDiffActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): SpotDiffActivity {
        SpotDiffActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), SpotDiffModule, players)
        return Robolectric.buildActivity(SpotDiffActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitSearch(a: SpotDiffActivity) = waitFor("버저 열림") { a.inputOpen && a.game.phase == Phase.SEARCH }

    private val pt = PointF()

    private fun touch(a: SpotDiffActivity, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.view.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun buzz(a: SpotDiffActivity, p: Int) {
        a.view.buzzerCenter(p, pt)
        touch(a, pt.x, pt.y)
    }

    private fun tapScene(a: SpotDiffActivity, pic: Int, sx: Float, sy: Float) {
        a.view.sceneToView(pic, sx, sy, pt)
        touch(a, pt.x, pt.y)
    }

    /** 어떤 차이와도 먼 그림 좌표 */
    private fun miss(s: Scene): FloatArray {
        var y = 4f
        while (y < GameConfig.SCENE_H) {
            var x = 4f
            while (x < GameConfig.SCENE_W) {
                if (s.differences.none { it.hit(x, y) }) return floatArrayOf(x, y)
                x += 9f
            }
            y += 9f
        }
        error("빈 곳 없음")
    }

    /** 3장면 15곳을 finder[k] 가 차례로 찾는다 (짝수 번째는 왼쪽 그림, 홀수는 오른쪽 그림을 터치) */
    private fun playAll(a: SpotDiffActivity, finder: IntArray) {
        var g = 0
        for (sc in 0 until GameConfig.SCENES) {
            waitSearch(a)
            assertEquals(sc, a.game.sceneIndex)
            val s = a.game.scene!!
            for (d in s.differences) {
                waitSearch(a)
                val p = finder[g]
                buzz(a, p)
                assertSame(Phase.ANSWER, a.game.phase)
                assertEquals(p, a.game.answerer)
                tapScene(a, g % 2, d.cx, d.cy)
                assertEquals(p, a.game.foundBy[d.index])
                g++
            }
            assertEquals(5, a.game.foundCount)
            assertFalse("장면 끝나면 버저 닫힘", a.inputOpen)
        }
    }

    private fun finishAndRanks(a: SpotDiffActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        return data.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun twoPlayersFullGameRanks() {
        val a = launch(four.take(2), 3)
        assertFalse("시작 전엔 버저 막힘", a.inputOpen)
        buzz(a, 0)
        assertSame(Phase.READY, a.game.phase)
        // 가람 9곳, 나래 6곳 → 1·2위
        val finder = IntArray(15) { if (it % 5 < 3) 0 else 1 }
        playAll(a, finder)
        assertTrue(a.game.gameOver)
        assertArrayEquals(intArrayOf(9, 6), a.game.scores)
        val ranks = finishAndRanks(a)
        assertArrayEquals(intArrayOf(1, 2), ranks)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
    }

    @Test
    fun threePlayersTieRanks() {
        val a = launch(four.take(3), 4)
        // 가람 4, 나래 7, 다온 4 → 2·1·2위 (동점 같은 순위)
        val finder = intArrayOf(1, 1, 0, 2, 1, 0, 2, 1, 1, 0, 2, 1, 0, 2, 1)
        playAll(a, finder)
        assertArrayEquals(intArrayOf(4, 7, 4), a.game.scores)
        assertArrayEquals(intArrayOf(2, 1, 2), finishAndRanks(a))
    }

    @Test
    fun fourPlayersWithWrongAnswersRanks() {
        val a = launch(four, 6)
        waitSearch(a)
        // 라온 오답 → -1, 2초 잠금
        val m = miss(a.game.scene!!)
        buzz(a, 3)
        tapScene(a, 1, m[0], m[1])
        assertEquals(-1, a.game.scores[3])
        assertTrue(a.game.isLocked(3, a.timers.now()))
        buzz(a, 3)
        assertSame("잠금 중 버저 무시", Phase.SEARCH, a.game.phase)
        // 다온은 시간 초과 (3초 동안 아무것도 안 누름)
        buzz(a, 2)
        assertEquals(2, a.game.answerer)
        waitFor("시간 초과") { a.game.phase == Phase.SEARCH }
        assertEquals(-1, a.game.scores[2])
        assertTrue(a.game.isLocked(2, a.timers.now()))
        waitFor("잠금 풀림") { !a.game.isLocked(2, a.timers.now()) && !a.game.isLocked(3, a.timers.now()) }
        // 라온 6, 다온 4, 나래 3, 가람 2 → 합계 라온 5, 다온 3, 나래 3, 가람 2 → 4·2·2·1위
        val finder = intArrayOf(3, 2, 1, 0, 3, 2, 1, 0, 3, 2, 1, 3, 2, 3, 3)
        playAll(a, finder)
        assertArrayEquals(intArrayOf(2, 3, 3, 5), a.game.scores)
        assertArrayEquals(intArrayOf(4, 2, 2, 1), finishAndRanks(a))
    }

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun pointerAction(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    @Test
    fun fourFingersOnFourBuzzersOnlyFirstCounts() {
        val a = launch(four, 9)
        waitSearch(a)
        val order = intArrayOf(2, 0, 3, 1)
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        for (k in 0 until 4) {
            a.view.buzzerCenter(order[k], pt)
            xs[k] = pt.x
            ys[k] = pt.y
            assertEquals(order[k], a.view.layout!!.panelAt(pt.x, pt.y))
        }
        val t = SystemClock.uptimeMillis()
        a.view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 4) a.view.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        assertSame(Phase.ANSWER, a.game.phase)
        assertEquals("먼저 누른 다온만", 2, a.game.answerer)
        assertArrayEquals(intArrayOf(0, 0, 0, 0), a.game.scores)
        for (k in 3 downTo 1) a.view.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + 20))
        a.view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, xs.copyOf(1), ys.copyOf(1), t + 30))
        // 다온이 정답 → 다음은 다시 아무나
        val d = a.game.scene!!.differences[2]
        tapScene(a, 1, d.cx, d.cy)
        assertArrayEquals(intArrayOf(0, 0, 1, 0), a.game.scores)
        assertSame(Phase.SEARCH, a.game.phase)
        buzz(a, 1)
        assertEquals(1, a.game.answerer)
    }

    @Test
    fun pauseFreezesAnswerTimeAndSceneTimeUpReveals() {
        val a = launch(four.take(3), 4)
        waitSearch(a)
        buzz(a, 1)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.view.frozen)
        idle(GameConfig.ANSWER_MS * 3)
        assertSame("일시정지 중엔 시간 초과 없음", Phase.ANSWER, a.game.phase)
        assertEquals(0, a.game.scores[1])
        // 일시정지 중 터치 무시
        a.onBuzzer(0)
        a.onPictureTap(1, a.game.scene!!.differences[0].cx, a.game.scene!!.differences[0].cy)
        assertEquals(0, a.game.foundCount)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertFalse(a.view.frozen)
        waitFor("시간 초과") { a.game.phase == Phase.SEARCH }
        assertEquals(-1, a.game.scores[1])
        // 한 곳 찾고 나머지는 장면 시간 끝 → 공개 → 다음 장면
        waitFor("잠금 풀림") { !a.game.isLocked(1, a.timers.now()) }
        val d0 = a.game.scene!!.differences[0]
        buzz(a, 0)
        tapScene(a, 0, d0.cx, d0.cy)
        assertEquals(1, a.game.scores[0])
        var waited = 0L
        while (a.game.sceneIndex == 0 && a.game.phase == Phase.SEARCH && waited < GameConfig.SCENE_TIME_LIMIT_MS + 5000) { idle(500); waited += 500 }
        assertTrue("90초 제한 근처에서 끝 ($waited)", waited >= GameConfig.SCENE_TIME_LIMIT_MS - 20_000)
        assertNotNull(a.game.scene)
        assertEquals(SpotGame.REVEALED, a.game.foundBy[1])
        assertEquals(0, a.game.foundBy[0])
        assertArrayEquals("공개는 점수 없음", intArrayOf(1, -1, 0), a.game.scores)
        waitSearch(a)
        assertEquals(1, a.game.sceneIndex)
    }

    @Test
    fun moduleInfo() {
        assertEquals("spotdiff", SpotDiffModule.id)
        assertEquals("틀린 그림 찾기 대결", SpotDiffModule.name)
        assertEquals(3, SpotDiffModule.rules.size)
        assertEquals("5분", SpotDiffModule.minutes)
        assertTrue(SpotDiffModule.supports(2) && SpotDiffModule.supports(3) && SpotDiffModule.supports(4))
        assertFalse(SpotDiffModule.supports(5))
        assertEquals(4, SpotDiffModule.requiredTouches(4))
    }
}
