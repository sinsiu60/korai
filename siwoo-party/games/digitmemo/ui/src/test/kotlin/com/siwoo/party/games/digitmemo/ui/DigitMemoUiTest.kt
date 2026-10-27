package com.siwoo.party.games.digitmemo.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.digitmemo.DigitLayout
import com.siwoo.party.games.digitmemo.DigitMemoGame
import com.siwoo.party.games.digitmemo.DigitMemoModule
import com.siwoo.party.games.digitmemo.GameConfig
import com.siwoo.party.games.digitmemo.Phase
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

/** 숫자 순간 기억 화면: 8라운드 끝까지 → 순위(2·3·4인), 4손가락 동시 입력, 위쪽 구역 회전 판정, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class DigitMemoUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { DigitMemoActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): DigitMemoActivity {
        DigitMemoActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), DigitMemoModule, players)
        return Robolectric.buildActivity(DigitMemoActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitInput(a: DigitMemoActivity, round: Int) =
        waitFor("${round}라운드 입력") { a.game.phase == Phase.INPUT && a.game.round == round && a.zones.touchEnabled }

    /** 겹친 그림 층을 포함한 stage 로 한 손가락 탭 */
    private fun tapAt(a: DigitMemoActivity, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.stage.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.stage.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun tapKey(a: DigitMemoActivity, zone: Int, key: Int) {
        val pt = FloatArray(2)
        a.keyPoint(zone, key, pt)
        tapAt(a, pt[0], pt[1])
    }

    private fun typeAndConfirm(a: DigitMemoActivity, zone: Int, s: String) {
        for (ch in s) tapKey(a, zone, ch - '0')
        assertEquals(s, a.game.input(zone))
        tapKey(a, zone, DigitLayout.KEY_OK)
        assertTrue("구역 $zone 제출", a.game.isSubmitted(zone))
    }

    private fun wrongOf(s: String): String = s.substring(0, s.length - 1) + ((s.last() - '0' + 1) % 10)

    private fun ranksOf(a: DigitMemoActivity): IntArray {
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

    /** 손가락 4개를 하나씩 더해 모두 누른 채 → 뗌 (zoneOrder 순서로 닿음, keys[zone] 키) */
    private fun fourFingers(a: DigitMemoActivity, zoneOrder: IntArray, keys: IntArray) {
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        val pt = FloatArray(2)
        for (k in 0 until 4) { a.keyPoint(zoneOrder[k], keys[zoneOrder[k]], pt); xs[k] = pt[0]; ys[k] = pt[1] }
        val t = SystemClock.uptimeMillis()
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 4) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        for (z in 0 until 4) assertTrue("구역 $z 눌림", a.zones.isPressed(z))
        for (k in 3 downTo 1) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + 10 + k))
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, xs.copyOf(1), ys.copyOf(1), t + 20))
    }

    // ── 끝까지 ──

    @Test
    fun twoPlayersEightRoundsToTheEnd() {
        val a = launch(four.take(2))
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        for (r in 1..GameConfig.ROUNDS) {
            waitInput(a, r)
            assertEquals(DigitMemoGame.digitsFor(r), a.game.answer.length)
            assertTrue("입력 중 프레임 루프", a.looping)
            // 0번 먼저 정답 → 1번 정답 (모두 제출 → 바로 공개)
            typeAndConfirm(a, 0, a.game.answer)
            typeAndConfirm(a, 1, a.game.answer)
            assertSame(Phase.REVEAL, a.game.phase)
            assertArrayEquals(intArrayOf(2, 1), a.game.lastResult!!.points)
        }
        assertTrue(a.game.gameOver)
        assertArrayEquals(intArrayOf(16, 8), a.game.scores)
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        idle(2000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun threePlayersTieAndTimeLimit() {
        val a = launch(four.take(3), seed = 8)
        for (r in 1..GameConfig.ROUNDS) {
            waitInput(a, r)
            val ans = a.game.answer
            // 홀수 라운드 0번이 먼저, 짝수 라운드 1번이 먼저 — 둘 다 정답. 2번은 틀리거나(앞 7라운드) 안 냄(마지막)
            if (r % 2 == 1) { typeAndConfirm(a, 0, ans); typeAndConfirm(a, 1, ans) } else { typeAndConfirm(a, 1, ans); typeAndConfirm(a, 0, ans) }
            if (r < GameConfig.ROUNDS) {
                typeAndConfirm(a, 2, wrongOf(ans))
            } else {
                // 마지막 라운드: 2번은 시간 끝까지 안 냄 → 제한 시간에 마감
                for (ch in ans) tapKey(a, 2, ch - '0')
                assertSame(Phase.INPUT, a.game.phase)
                idle(DigitMemoGame.timeLimitMs(ans.length) - 2000)
                assertSame("제한 시간 전", Phase.INPUT, a.game.phase)
                waitFor("시간 끝") { a.game.phase == Phase.REVEAL }
                assertFalse("시간 끝 → 미제출", a.game.isSubmitted(2))
                assertFalse(a.game.lastResult!!.correct[2])
            }
            assertSame(Phase.REVEAL, a.game.phase)
        }
        assertArrayEquals(intArrayOf(12, 12, 0), a.game.scores)
        assertArrayEquals(intArrayOf(1, 1, 3), ranksOf(a))
    }

    @Test
    fun fourPlayersEightRoundsToTheEnd() {
        val a = launch(four, seed = 21)
        for (r in 1..GameConfig.ROUNDS) {
            waitInput(a, r)
            val ans = a.game.answer
            // 3번 가장 빠른 정답, 2번 정답, 1번 오답, 0번은 홀수 라운드만 정답
            typeAndConfirm(a, 1, wrongOf(ans))
            typeAndConfirm(a, 3, ans)
            typeAndConfirm(a, 2, ans)
            typeAndConfirm(a, 0, if (r % 2 == 1) ans else wrongOf(ans))
            assertArrayEquals(intArrayOf(if (r % 2 == 1) 1 else 0, 0, 1, 2), a.game.lastResult!!.points)
            assertTrue("제출 표시", a.board.isSubmittedShown(3))
        }
        assertArrayEquals(intArrayOf(4, 0, 8, 16), a.game.scores)
        assertArrayEquals(intArrayOf(3, 4, 2, 1), ranksOf(a))
    }

    // ── 동시 입력 ──

    @Test
    fun fourFingersTypeIndependentlyAndFirstConfirmGetsTwo() {
        val a = launch(four, seed = 9)
        // 숫자가 보이는 동안엔 패드가 안 먹음
        waitFor("숫자 보이기") { a.game.phase == Phase.SHOWING }
        tapKey(a, 0, 1)
        assertEquals("", a.game.input(0))
        waitInput(a, 1)
        val ans = a.game.answer
        val bad = wrongOf(ans)
        val want = arrayOf(ans, ans, bad, ans)
        val order = intArrayOf(3, 0, 1, 2)
        for (k in ans.indices) {
            fourFingers(a, order, IntArray(4) { want[it][k] - '0' })
            for (z in 0 until 4) assertEquals("구역 $z 는 자기 손가락만", want[z].substring(0, k + 1), a.game.input(z))
        }
        assertEquals(ans, a.board.inputOf(0))
        // 1번만 지우고 다시 → 다른 구역은 그대로
        tapKey(a, 1, DigitLayout.KEY_BACK)
        assertEquals(ans.dropLast(1), a.game.input(1))
        assertEquals(ans, a.game.input(0))
        tapKey(a, 1, ans.last() - '0')
        // 네 사람 동시에 [확인] — 먼저 닿은 손가락(구역 3)이 가장 빠른 정답
        fourFingers(a, order, IntArray(4) { DigitLayout.KEY_OK })
        assertSame("모두 제출 → 공개", Phase.REVEAL, a.game.phase)
        val r = a.game.lastResult!!
        assertEquals(3, r.fastest)
        assertArrayEquals(intArrayOf(1, 1, 0, 2), r.points)
        assertArrayEquals(intArrayOf(1, 1, 0, 2), a.game.scores)
        // 다음 라운드: 입력이 비워짐, 제출 다시 가능
        waitInput(a, 2)
        for (z in 0 until 4) { assertEquals("", a.game.input(z)); assertFalse(a.game.isSubmitted(z)) }
    }

    @Test
    fun confirmOncePerRoundAndTopZoneRotated() {
        val a = launch(four, seed = 3)
        waitInput(a, 1)
        val top = a.zones.zone(0)!!
        assertEquals(180f, top.rotation, 0f)
        // 위쪽 구역(180°): 그 사람 기준 왼쪽 위 '1' 키는 화면에서 오른쪽 아래
        val p1 = FloatArray(2)
        val p5 = FloatArray(2)
        a.keyPoint(0, 1, p1)
        a.keyPoint(0, 5, p5)
        assertTrue(p1[0] > p5[0])
        assertTrue(top.contains(p1[0], p1[1]) && top.contains(p5[0], p5[1]))
        // 돌리지 않은 자리(그 사람 기준으로는 패드 위쪽 숫자 칸 줄 근처)를 누르면 아무 키도 아님
        val k6 = a.board.layout.keys[6]
        tapAt(a, top.centerX + k6.centerX, top.centerY + k6.centerY)
        assertEquals("", a.game.input(0))
        // 자릿수를 다 채우기 전 [확인]은 무시
        tapKey(a, 0, 1)
        tapKey(a, 0, DigitLayout.KEY_OK)
        assertFalse(a.game.isSubmitted(0))
        tapKey(a, 0, DigitLayout.KEY_BACK)
        typeAndConfirm(a, 0, a.game.answer)
        // 제출 뒤에는 더 못 고침·두 번 못 냄
        tapKey(a, 0, DigitLayout.KEY_BACK)
        tapKey(a, 0, DigitLayout.KEY_OK)
        assertEquals(a.game.answer, a.game.input(0))
        assertEquals(0, a.game.submission(0)!!.seq)
        assertEquals(1, a.game.submittedCount)
        assertTrue(a.board.isSubmittedShown(0))
    }

    // ── 일시정지 ──

    @Test
    fun pauseHidesDigitsAndFreezesTimers() {
        val a = launch(four.take(2))
        // 숫자가 보이는 중에 일시정지 → 가림, 1초가 지나도 계속 보이기 단계
        waitFor("숫자 보이기") { a.game.phase == Phase.SHOWING }
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue("숫자 가림", a.board.contentHidden)
        assertFalse("일시정지 중 프레임 멈춤", a.looping)
        idle(5000)
        assertSame(Phase.SHOWING, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertFalse(a.board.contentHidden)
        waitInput(a, 1)
        // 입력 중 일시정지 → 패드 가림, 제한 시간 멈춤, 터치 무시
        tapKey(a, 0, 7)
        val left = a.game.timeLeft(a.timers.now())
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(a.board.contentHidden)
        val pt = FloatArray(2)
        a.keyPoint(0, 8, pt)
        a.onZoneDown(0, pt[0], pt[1], SystemClock.uptimeMillis())
        idle(DigitMemoGame.timeLimitMs(4) + 5000)
        assertSame("일시정지 중 시간이 가지 않음", Phase.INPUT, a.game.phase)
        assertEquals("7", a.game.input(0))
        assertTrue(a.game.timeLeft(a.timers.now()) >= left - 200)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        tapKey(a, 0, 8)
        assertEquals("78", a.game.input(0))
    }

    @Test
    fun moduleInfo() {
        assertEquals("digitmemo", DigitMemoModule.id)
        assertEquals("숫자 순간 기억", DigitMemoModule.name)
        assertSame(GameCategory.REFLEX, DigitMemoModule.category)
        assertSame(PlayMode.SIMULTANEOUS, DigitMemoModule.mode)
        assertEquals("3분", DigitMemoModule.minutes)
        assertEquals(3, DigitMemoModule.rules.size)
        assertEquals(4, DigitMemoModule.needsMultiTouch)
        assertTrue(DigitMemoModule.supports(2) && DigitMemoModule.supports(3) && DigitMemoModule.supports(4))
        assertFalse(DigitMemoModule.supports(5))
    }
}
