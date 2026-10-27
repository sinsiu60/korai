package com.siwoo.party.games.math.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.math.GameConfig
import com.siwoo.party.games.math.MathModule
import com.siwoo.party.games.math.Phase
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

/** 암산 대결 화면: 10문제 끝까지 → 순위, 잠금, 4손가락 동시 터치, 위쪽 구역 보기 판정, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class MathUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val two = four.take(2)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { MathActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): MathActivity {
        MathActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), MathModule, players)
        return Robolectric.buildActivity(MathActivity::class.java, i).setup().get()
    }

    private fun tapAt(a: MathActivity, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun choose(a: MathActivity, zone: Int, k: Int) {
        val pt = FloatArray(2)
        a.choicePoint(zone, k, pt)
        tapAt(a, pt[0], pt[1])
    }

    private fun wrongOf(a: MathActivity, n: Int = 1) = (a.game.current.correctIndex + n) % GameConfig.CHOICES

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitAsking(a: MathActivity, number: Int) =
        waitFor("${number}번 문제") { a.game.phase == Phase.ASKING && a.game.number == number && a.zones.touchEnabled }

    private fun ranksOf(a: MathActivity): IntArray {
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
    private fun fourFingers(a: MathActivity, xs: FloatArray, ys: FloatArray) {
        val t = SystemClock.uptimeMillis()
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 4) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        for (z in 0 until 4) assertTrue("구역 $z 눌림", a.zones.isPressed(z))
        for (k in 3 downTo 1) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + 10 + k))
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, xs.copyOf(1), ys.copyOf(1), t + 20))
    }

    @Test
    fun twoPlayersTenQuestionsToTheEnd() {
        val a = launch(two)
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        for (n in 1..GameConfig.MATH_QUESTIONS) {
            waitAsking(a, n)
            assertTrue("문제 중 프레임 루프", a.looping)
            val q = a.game.current
            assertEquals(a.game.questionsStage(n), q.stage)
            when {
                n <= 6 -> choose(a, 0, q.correctIndex)
                n <= 9 -> {
                    // 가람이 먼저 틀림 → 잠금 → 바로 정답 눌러도 무시 → 나래가 맞힘
                    choose(a, 0, wrongOf(a))
                    assertTrue(a.game.isLocked(0, a.timers.now()))
                    idle(100)
                    assertTrue("잠금 덮개", a.board.isLockShown(0))
                    choose(a, 0, q.correctIndex)
                    assertEquals("잠금 중 정답 무시", -1, a.game.winner)
                    choose(a, 1, q.correctIndex)
                    assertEquals(1, a.game.winner)
                }
                else -> Unit // 아무도 안 누름 → 시간 끝
            }
            if (n < 10) assertTrue(a.game.phase == Phase.CLOSED)
        }
        waitFor("마지막 문제 시간 끝") { a.game.gameOver }
        assertEquals(-1, a.game.winner)
        assertArrayEquals(intArrayOf(6, 3), a.game.scores)
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        idle(2000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    private fun com.siwoo.party.games.math.MathGame.questionsStage(n: Int) = questions[n - 1].stage

    @Test
    fun fourFingersAtOnceEachInOwnZone() {
        val a = launch(four, seed = 9)
        waitAsking(a, 1)
        val order = intArrayOf(3, 0, 1, 2)
        val pt = FloatArray(2)
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        // 네 사람 동시에 각자 오답 (구역마다 다른 오답) → 각자 자기 구역만 잠김
        val picked = IntArray(4)
        for (k in 0 until 4) {
            val z = order[k]
            picked[z] = wrongOf(a, 1 + z % 2)
            a.choicePoint(z, picked[z], pt); xs[k] = pt[0]; ys[k] = pt[1]
        }
        fourFingers(a, xs, ys)
        for (z in 0 until 4) {
            assertEquals("구역 $z 는 자기가 누른 보기만 틀림", 1 shl picked[z], a.game.wrongMask[z])
            assertTrue(a.game.isLocked(z, a.timers.now()))
        }
        assertArrayEquals(intArrayOf(0, 0, 0, 0), a.game.scores)
        assertSame(Phase.ASKING, a.game.phase)
        // 잠금이 풀린 뒤 네 사람 동시에 정답 → 먼저 닿은 손가락(구역 3)만 +1
        idle(GameConfig.MATH_LOCK_MS + 100)
        for (z in 0 until 4) assertFalse(a.game.isLocked(z, a.timers.now()))
        val c = a.game.current.correctIndex
        for (k in 0 until 4) { a.choicePoint(order[k], c, pt); xs[k] = pt[0]; ys[k] = pt[1] }
        fourFingers(a, xs, ys)
        assertEquals(3, a.game.winner)
        assertArrayEquals(intArrayOf(0, 0, 0, 1), a.game.scores)
        waitAsking(a, 2)
        assertArrayEquals("새 문제 → 틀린 표시 초기화", intArrayOf(0, 0, 0, 0), a.game.wrongMask)
    }

    @Test
    fun topZoneChoicesAreRotated() {
        val a = launch(four, seed = 3)
        waitAsking(a, 1)
        val top = a.zones.zone(0)!!
        assertEquals(180f, top.rotation, 0f)
        val c = a.game.current.correctIndex
        val b = a.board.layout.choices[c]
        // 위쪽 구역에서 "돌리지 않은" 정답 자리 = 보기가 아님 (그 사람 기준 문제 위쪽) → 무시
        tapAt(a, top.centerX + b.centerX, top.centerY + b.centerY)
        assertEquals(0, a.game.wrongMask[0])
        assertEquals(-1, a.game.winner)
        // 위쪽 구역의 보기 순서는 화면에서 오른쪽→왼쪽 (그 사람 기준 왼쪽→오른쪽)
        val p0 = FloatArray(2)
        val p2 = FloatArray(2)
        a.choicePoint(0, 0, p0)
        a.choicePoint(0, 2, p2)
        assertTrue(p0[0] > p2[0])
        assertTrue(top.contains(p0[0], p0[1]) && top.contains(p2[0], p2[1]))
        // 돌린 자리의 오답 → 그 보기가 틀림으로
        val w = wrongOf(a)
        choose(a, 0, w)
        assertEquals(1 shl w, a.game.wrongMask[0])
        // 오른쪽 위 구역(180°)이 돌린 정답 자리를 누르면 정답
        choose(a, 1, c)
        assertEquals(1, a.game.winner)
        assertArrayEquals(intArrayOf(0, 1, 0, 0), a.game.scores)
    }

    @Test
    fun pausedTapsIgnoredAndClockStops() {
        val a = launch(two)
        waitAsking(a, 1)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지 중 프레임 멈춤", a.looping)
        val c = a.game.current.correctIndex
        val pt = FloatArray(2)
        a.choicePoint(0, c, pt)
        a.onZoneDown(0, pt[0], pt[1], SystemClock.uptimeMillis())
        idle(GameConfig.QUESTION_TIME_MS + 5000)
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
        assertEquals("math", MathModule.id)
        assertSame(GameCategory.REFLEX, MathModule.category)
        assertEquals(3, MathModule.rules.size)
        assertEquals(4, MathModule.needsMultiTouch)
        assertTrue(MathModule.supports(2) && MathModule.supports(3) && MathModule.supports(4))
    }
}
