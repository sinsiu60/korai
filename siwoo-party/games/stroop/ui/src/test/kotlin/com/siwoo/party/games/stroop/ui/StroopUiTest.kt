package com.siwoo.party.games.stroop.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.stroop.GameConfig
import com.siwoo.party.games.stroop.Hue
import com.siwoo.party.games.stroop.Phase
import com.siwoo.party.games.stroop.StroopModule
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

/** 색깔 함정 화면: 끝까지 진행 → 순위, 오답 잠금, 4손가락 동시 터치 구역 구분, 위쪽(180°) 구역 버튼 판정, 시간 초과, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class StroopUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { StroopActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): StroopActivity {
        StroopActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), StroopModule, players)
        return Robolectric.buildActivity(StroopActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1200) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitAsking(a: StroopActivity) = waitFor("문제") { a.game.phase == Phase.ASKING && a.zones.touchEnabled }

    private val pt = PointF()

    /** zone 의 hue 버튼을 한 손가락으로 톡 */
    private fun tap(a: StroopActivity, zone: Int, hue: Hue) {
        a.board.buttonCenter(zone, hue, pt)
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    private fun wrongOf(answer: Hue): Hue = Hue.values().first { it != answer }

    @Test
    fun twoPlayersPlayAllQuestionsAndReturnRanks() {
        val a = launch(four.take(2))
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        waitAsking(a)
        val q = a.game.current!!
        // 나래 오답 → -1, 1초 잠금, 잠금 중 정답은 무시
        tap(a, 1, wrongOf(q.answer))
        assertEquals(-1, a.game.scores[1])
        assertTrue(a.game.isLocked(1, a.timers.now()))
        tap(a, 1, q.answer)
        assertEquals(-1, a.game.scores[1])
        assertSame(Phase.ASKING, a.game.phase)
        // 가람 정답 → +1, 문제 끝
        tap(a, 0, q.answer)
        assertEquals(1, a.game.scores[0])
        assertEquals(1, a.game.questionsDone)
        assertEquals(0, a.game.questionWinner)
        // 나머지: 가람이 계속 맞힘
        while (!a.game.gameOver) {
            waitAsking(a)
            tap(a, 0, a.game.current!!.answer)
        }
        assertEquals(GameConfig.STROOP_QUESTIONS, a.game.scores[0])
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1), data.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals(intArrayOf(1, 2), data.getIntArrayExtra(Party.EXTRA_RANKS))
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
        waitAsking(a)
        val ans = a.game.current!!.answer
        val wrong = wrongOf(ans)
        // 구역 0·1·2 는 오답 버튼, 구역 3 은 정답 버튼 — 네 손가락이 모두 누른 채로 하나씩 더해짐
        val hues = arrayOf(wrong, wrong, wrong, ans)
        val order = intArrayOf(2, 0, 1, 3)
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        for (k in 0 until 4) {
            a.board.buttonCenter(order[k], hues[order[k]], pt)
            xs[k] = pt.x
            ys[k] = pt.y
        }
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 4) a.zones.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        for (z in 0 until 4) assertTrue("구역 $z 눌림", a.zones.isPressed(z))
        assertArrayEquals("각자 자기 구역에서만 판정", intArrayOf(-1, -1, -1, 1), a.game.scores)
        assertEquals(3, a.game.questionWinner)
        for (z in 0 until 3) assertTrue(a.game.isLocked(z, a.timers.now()))
        assertFalse(a.game.isLocked(3, a.timers.now()))
        // 모두 뗌
        for (k in 3 downTo 1) a.zones.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + 20))
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, xs.copyOf(1), ys.copyOf(1), t + 30))
        for (z in 0 until 4) assertFalse(a.zones.isPressed(z))
    }

    @Test
    fun topZoneButtonsAreRotated() {
        val a = launch(four, seed = 3)
        waitAsking(a)
        val top = a.zones.zone(0)!!
        assertEquals(180f, top.rotation)
        val bottom = a.zones.zone(2)!!
        // 위쪽 구역: 플레이어 기준 첫 버튼(빨강)은 화면에서 오른쪽, 버튼 줄은 화면 위쪽(그 사람 쪽)
        val red = PointF(); val yellow = PointF()
        a.board.buttonCenter(0, Hue.RED, red)
        a.board.buttonCenter(0, Hue.YELLOW, yellow)
        assertTrue("빨강이 노랑보다 화면 오른쪽", red.x > yellow.x)
        assertTrue(red.x > top.centerX && yellow.x < top.centerX)
        assertTrue("버튼 줄이 구역의 위쪽 끝", red.y < top.centerY && red.y > top.top)
        // 아래 구역은 그대로: 빨강 왼쪽, 버튼 줄 아래쪽
        val bRed = PointF(); val bYellow = PointF()
        a.board.buttonCenter(2, Hue.RED, bRed)
        a.board.buttonCenter(2, Hue.YELLOW, bYellow)
        assertTrue(bRed.x < bYellow.x && bRed.y > bottom.centerY)
        // 같은 배치: 위쪽 구역 버튼 = 아래 구역 버튼을 구역 가운데 기준으로 180° 돌린 자리
        assertEquals(top.centerX - (bRed.x - bottom.centerX), red.x, 0.5f)
        assertEquals(top.centerY - (bRed.y - bottom.centerY), red.y, 0.5f)
        // 판정: 화면 좌표 → 위쪽 구역 버튼
        assertSame(Hue.RED, a.board.buttonAt(0, red.x, red.y))
        assertSame(Hue.YELLOW, a.board.buttonAt(0, yellow.x, yellow.y))
        assertNull("카드(구역 가운데)는 버튼 아님", a.board.buttonAt(0, top.centerX, top.centerY))
        // 실제 터치: 위쪽 구역에서 정답 버튼 자리를 누르면 정답
        val ans = a.game.current!!.answer
        tap(a, 0, ans)
        assertEquals(1, a.game.scores[0])
        assertEquals(0, a.game.questionWinner)
    }

    @Test
    fun timeoutSkipsQuestionAndPauseFreezesIt() {
        val a = launch(four.take(3), seed = 4)
        waitAsking(a)
        // 일시정지 중에는 시간 초과가 오지 않고 터치도 무시
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.board.frozen)
        a.onZoneDown(0, 0f, 0f, SystemClock.uptimeMillis())
        tap(a, 2, a.game.current!!.answer)
        idle(GameConfig.QUESTION_TIMEOUT_MS * 2)
        assertEquals(0, a.game.questionsDone)
        assertSame(Phase.ASKING, a.game.phase)
        assertEquals(0, a.game.scores[2])
        // 계속하기 → 남은 시간 뒤 시간 초과로 넘어감 (아무도 점수 없음)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertFalse(a.board.frozen)
        waitFor("시간 초과") { a.game.questionsDone == 1 }
        assertArrayEquals(intArrayOf(0, 0, 0), a.game.scores)
        assertEquals(-1, a.game.questionWinner)
        waitAsking(a)
        assertEquals(2, a.game.current!!.number)
    }

    @Test
    fun moduleInfo() {
        assertEquals("stroop", StroopModule.id)
        assertEquals(3, StroopModule.rules.size)
        assertTrue(StroopModule.supports(2) && StroopModule.supports(3) && StroopModule.supports(4))
        assertEquals(4, StroopModule.requiredTouches(4))
    }
}
