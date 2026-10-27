package com.siwoo.party.games.traceshape.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.traceshape.GameConfig
import com.siwoo.party.games.traceshape.Phase
import com.siwoo.party.games.traceshape.TraceShapeModule
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

/** 도형 따라 그리기 화면: 4손가락 동시 그리기(회전 구역), 완벽한 획 자동 완료, 5라운드 끝까지 → 순위(2·3·4인), 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class TraceShapeUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { TraceShapeActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): TraceShapeActivity {
        TraceShapeActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), TraceShapeModule, players)
        return Robolectric.buildActivity(TraceShapeActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(2000) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitDrawing(a: TraceShapeActivity, round: Int) =
        waitFor("${round}라운드 그리기") { a.game.phase == Phase.DRAWING && a.game.round == round && a.board.input.enabled }

    /** 구역 zone 에서 도형의 길이 비율 u 지점 화면 좌표 */
    private fun screenAt(a: TraceShapeActivity, zone: Int, u: Float, out: FloatArray) {
        val p = FloatArray(2)
        a.game.shape.pointAt(u, p)
        a.screenPoint(zone, p[0], p[1], out)
    }

    private fun send(a: TraceShapeActivity, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.stage.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    /** 한 손가락으로 u0 → u1 따라 그림 (up = 끝에서 뗌) */
    private fun trace(a: TraceShapeActivity, zone: Int, u0: Float = 0f, u1: Float = 1f, steps: Int = 160, up: Boolean = true) {
        val pt = FloatArray(2)
        screenAt(a, zone, u0, pt)
        send(a, MotionEvent.ACTION_DOWN, pt[0], pt[1])
        for (k in 1..steps) {
            screenAt(a, zone, u0 + (u1 - u0) * k / steps, pt)
            send(a, MotionEvent.ACTION_MOVE, pt[0], pt[1])
        }
        if (up) send(a, MotionEvent.ACTION_UP, pt[0], pt[1])
    }

    private fun ranksOf(a: TraceShapeActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, n: Int, t: Long): MotionEvent {
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun pointerAction(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    // ── 동시 그리기 ──

    @Test
    fun fourFingersDrawIndependentlyInRotatedZones() {
        val a = launch(four, seed = 9)
        waitDrawing(a, 1)
        assertEquals(180f, a.zones.zone(0)!!.rotation, 0f)
        assertEquals(180f, a.zones.zone(1)!!.rotation, 0f)
        assertEquals(0f, a.zones.zone(2)!!.rotation, 0f)
        // 손가락 k 는 구역 order[k] — 3·1·0·2 순서로 닿음. 구역 3 은 절반만 그리고 멈춘다
        val order = intArrayOf(3, 1, 0, 2)
        val upTo = floatArrayOf(1f, 1f, 1f, 0.5f) // 구역별 목표
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        val pt = FloatArray(2)
        fun place(f: Float) {
            for (k in 0 until 4) {
                val z = order[k]
                screenAt(a, z, minOf(f, upTo[z]), pt)
                xs[k] = pt[0]; ys[k] = pt[1]
                assertTrue("손가락 $k 는 구역 $z 안", a.zones.zone(z)!!.contains(pt[0], pt[1]))
            }
        }
        place(0f)
        val t = SystemClock.uptimeMillis()
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs, ys, 1, t))
        for (k in 1 until 4) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs, ys, k + 1, t + k))
        for (z in 0 until 4) {
            assertTrue("구역 $z 눌림", a.zones.isPressed(z))
            assertTrue("구역 $z 손가락 추적", a.board.input.isDown(z))
            // 첫 점 = 도형 시작점 (위쪽 구역도 회전을 풀어 같은 상자 좌표)
            val tr = a.game.traces[z]
            assertEquals(1, tr.count)
            assertEquals("구역 $z 시작 x", a.game.shape.startX, tr.xs[0], 0.003f)
            assertEquals("구역 $z 시작 y", a.game.shape.startY, tr.ys[0], 0.003f)
        }
        val steps = 160
        for (s in 1..steps) {
            place(s.toFloat() / steps)
            a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, xs, ys, 4, t + 10 + s))
        }
        // 0·1·2 는 완벽하게 → 자동 완료, 3 은 절반 → 미완성
        for (z in 0..2) {
            assertTrue("구역 $z 완료", a.game.isFinished(z))
            assertEquals("구역 $z 정확도", 100f, a.game.evaluate(z).accuracy, 0.5f)
            assertTrue(a.board.isDoneShown(z))
        }
        assertFalse(a.game.isFinished(3))
        val c3 = a.game.traces[3].coverage
        assertTrue("구역 3 은 절반쯤 ($c3)", c3 in 0.45f..0.65f)
        assertTrue("구역 3 그림은 경로 위 (${a.game.traces[3].meanDist})", a.game.traces[3].meanDist < 0.005f)
        for (k in 3 downTo 1) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, k), xs, ys, k + 1, t + 400 + k))
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, xs, ys, 1, t + 410))
        for (z in 0 until 4) assertFalse(a.board.input.isDown(z))
        // 구역 3 이 아직 → 제한 시간에 마감, 미완성은 보너스 없이
        assertSame(Phase.DRAWING, a.game.phase)
        waitFor("시간 끝") { a.game.phase == Phase.REVEAL }
        val r = a.game.lastResult!!
        for (z in 0..2) assertEquals(150, r.players[z].points)
        assertFalse(r.players[3].finished)
        assertEquals(1f, r.players[3].bonus, 0f)
        assertEquals(Math.round(r.players[3].accuracy), r.players[3].points)
    }

    @Test
    fun scriptedPerfectTraceAutoCompletesAndLiftContinues() {
        val a = launch(four.take(2), seed = 4)
        // 그리기 전(배너 중)에는 그려지지 않음
        waitFor("준비") { a.game.phase == Phase.READY && a.game.round == 1 }
        trace(a, 0, 0f, 0.3f)
        assertEquals(0, a.game.traces[0].count)
        waitDrawing(a, 1)
        assertTrue("그리는 중 프레임 루프", a.looping)
        // 0번: 한 번에 끝까지 → 손을 떼기 전에 자동 완료
        trace(a, 0, up = false)
        assertTrue("자동 완료", a.game.isFinished(0))
        assertTrue(a.board.isDoneShown(0))
        val e0 = a.game.evaluate(0)
        assertEquals(100f, e0.accuracy, 0.5f)
        assertEquals(GameConfig.BONUS_MAX, e0.bonus, 0.001f)
        assertEquals(150, e0.points)
        // 완료 뒤에는 더 그려지지 않음
        val c0 = a.game.traces[0].count
        val pt = FloatArray(2)
        a.screenPoint(0, 0.5f, 0.5f, pt)
        send(a, MotionEvent.ACTION_MOVE, pt[0], pt[1])
        send(a, MotionEvent.ACTION_UP, pt[0], pt[1])
        assertEquals(c0, a.game.traces[0].count)
        assertSame(Phase.DRAWING, a.game.phase)
        // 1번: 절반 그리고 손 뗌 → 이어서 나머지 (같은 획으로 이어짐)
        trace(a, 1, 0f, 0.5f)
        assertFalse(a.game.isFinished(1))
        val seg = a.game.traces[1].count
        trace(a, 1, 0.5f, 1f)
        assertTrue("새 조각으로 이어 그림", a.game.traces[1].segStart[seg])
        assertTrue("이어서 완료", a.game.isFinished(1))
        // 모두 완료 → 잠깐 뒤 공개 → 비교 → 2라운드
        waitFor("공개") { a.game.phase == Phase.REVEAL && a.board.mode == TraceShapeView.Mode.REVEAL }
        assertArrayEquals(intArrayOf(150, 150), a.game.totals)
        waitFor("비교") { a.board.mode == TraceShapeView.Mode.COMPARE }
        waitDrawing(a, 2)
        assertEquals("새 라운드 획 비움", 0, a.game.traces[0].count)
        assertFalse(a.game.isFinished(0))
    }

    // ── 끝까지 ──

    @Test
    fun twoPlayersFiveRoundsToTheEnd() {
        val a = launch(four.take(2))
        for (r in 1..GameConfig.ROUNDS) {
            waitDrawing(a, r)
            trace(a, 1, 0f, 0.5f)
            trace(a, 0)
            assertTrue(a.game.isFinished(0))
            waitFor("${r}라운드 공개") { a.game.phase == Phase.REVEAL }
            assertEquals(150, a.game.lastResult!!.players[0].points)
            assertTrue(a.game.lastResult!!.players[1].points in 30..75)
        }
        assertTrue(a.game.gameOver)
        assertEquals(750, a.game.totals[0])
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        idle(2000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun threePlayersTieAndTimeLimit() {
        val a = launch(four.take(3), seed = 8)
        val kinds = HashSet<String>()
        for (r in 1..GameConfig.ROUNDS) {
            waitDrawing(a, r)
            kinds.add(a.game.shape.kind.label)
            // 0·1 완벽, 2 는 안 그림 → 제한 시간에 마감
            trace(a, 0)
            trace(a, 1)
            idle(GameConfig.ROUND_MS - 3000)
            assertSame("제한 시간 전", Phase.DRAWING, a.game.phase)
            waitFor("시간 끝") { a.game.phase == Phase.REVEAL }
            assertEquals(0, a.game.lastResult!!.players[2].points)
        }
        assertEquals("라운드마다 다른 도형", GameConfig.ROUNDS, kinds.size)
        assertArrayEquals(intArrayOf(750, 750, 0), a.game.totals)
        assertArrayEquals(intArrayOf(1, 1, 3), ranksOf(a))
    }

    @Test
    fun fourPlayersFiveRoundsToTheEnd() {
        val a = launch(four, seed = 21)
        for (r in 1..GameConfig.ROUNDS) {
            waitDrawing(a, r)
            // 3번 바로 완벽, 1번 절반, 2번은 5초 뒤 완벽 (보너스 작음), 0번 안 그림
            trace(a, 3)
            trace(a, 1, 0f, 0.5f)
            idle(5000)
            trace(a, 2)
            assertTrue(a.game.isFinished(2))
            waitFor("${r}라운드 공개") { a.game.phase == Phase.REVEAL }
            val p = a.game.lastResult!!.players
            assertEquals(150, p[3].points)
            assertTrue("2번 보너스 ${p[2].bonus}", p[2].bonus in 1.05f..1.45f)
            assertTrue(p[2].points in 101..149)
            assertEquals(0, p[0].points)
        }
        assertArrayEquals(intArrayOf(4, 3, 2, 1), ranksOf(a))
    }

    // ── 일시정지 ──

    @Test
    fun pauseFreezesTimerAndIgnoresInput() {
        val a = launch(four.take(2))
        waitDrawing(a, 1)
        trace(a, 0, 0f, 0.4f)
        val count0 = a.game.traces[0].count
        val left = a.game.timeLeft(a.timers.now())
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지 중 프레임 멈춤", a.looping)
        assertFalse(a.board.input.enabled)
        // 일시정지 중 그리기 무시
        trace(a, 1, 0f, 0.5f)
        trace(a, 0, 0.4f, 1f)
        assertEquals(0, a.game.traces[1].count)
        assertEquals(count0, a.game.traces[0].count)
        idle(GameConfig.ROUND_MS + 5000)
        assertSame("일시정지 중 시간이 가지 않음", Phase.DRAWING, a.game.phase)
        assertTrue(a.game.timeLeft(a.timers.now()) >= left - 200)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        assertTrue(a.board.input.enabled)
        // 다시 대면 같은 획으로 이어서
        trace(a, 0, 0.4f, 1f)
        assertTrue(a.game.isFinished(0))
        trace(a, 1)
        assertTrue(a.game.isFinished(1))
    }

    @Test
    fun moduleInfo() {
        assertEquals("traceshape", TraceShapeModule.id)
        assertEquals("도형 따라 그리기", TraceShapeModule.name)
        assertSame(GameCategory.REFLEX, TraceShapeModule.category)
        assertSame(PlayMode.SIMULTANEOUS, TraceShapeModule.mode)
        assertEquals("3분", TraceShapeModule.minutes)
        assertEquals(3, TraceShapeModule.rules.size)
        assertEquals(4, TraceShapeModule.needsMultiTouch)
        assertTrue(TraceShapeModule.supports(2) && TraceShapeModule.supports(3) && TraceShapeModule.supports(4))
        assertFalse(TraceShapeModule.supports(5))
    }
}
