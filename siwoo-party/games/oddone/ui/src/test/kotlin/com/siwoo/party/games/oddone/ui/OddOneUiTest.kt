package com.siwoo.party.games.oddone.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.oddone.GameConfig
import com.siwoo.party.games.oddone.OddOneModule
import com.siwoo.party.games.oddone.Phase
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

/**
 * 하나만 다른 모양 화면: 2·3·4인 끝까지 진행 → 순위, 오답 잠금, 4손가락 동시 터치 구역 구분,
 * 위쪽(180°) 구역 칸 판정, 시간 초과, 일시정지.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class OddOneUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { OddOneActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): OddOneActivity {
        OddOneActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), OddOneModule, players)
        return Robolectric.buildActivity(OddOneActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1200) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitAsking(a: OddOneActivity) = waitFor("문제") { a.game.phase == Phase.ASKING && a.zones.touchEnabled }

    private val pt = PointF()

    /** zone 구역의 cell 칸을 한 손가락으로 톡 */
    private fun tap(a: OddOneActivity, zone: Int, cell: Int) {
        a.board.cellCenter(zone, cell, pt)
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    private fun odd(a: OddOneActivity) = a.game.current!!.oddIndex
    private fun wrongOf(a: OddOneActivity) = (odd(a) + 1) % GameConfig.CELLS

    /** 끝까지 진행: winnerOf(라운드 번호) 가 찾는다 (-1 이면 시간 초과) → 결과 순위 */
    private fun playToEnd(a: OddOneActivity, winnerOf: (Int) -> Int): IntArray {
        while (!a.game.gameOver) {
            waitAsking(a)
            val w = winnerOf(a.game.current!!.number)
            if (w >= 0) tap(a, w, odd(a)) else waitFor("시간 초과") { a.game.phase == Phase.IDLE }
        }
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun twoPlayersPlayAllRoundsAndReturnRanks() {
        val a = launch(four.take(2))
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        waitAsking(a)
        // 나래 오답 → 1초 잠금 (감점 없음), 잠금 중 정답은 무시
        tap(a, 1, wrongOf(a))
        assertEquals(0, a.game.scores[1])
        assertTrue(a.game.isLocked(1, a.timers.now()))
        tap(a, 1, odd(a))
        assertEquals(0, a.game.scores[1])
        assertSame(Phase.ASKING, a.game.phase)
        // 격자 밖(구역 위쪽 띠)은 무시
        val z = a.zones.zone(0)!!
        val cnt = a.game.roundsDone
        a.onZoneDown(0, z.left + 12f, z.top + 12f, 0)
        assertEquals(cnt, a.game.roundsDone)
        assertFalse(a.game.isLocked(0, a.timers.now()))
        // 가람 정답 → +1, 라운드 끝
        tap(a, 0, odd(a))
        assertEquals(1, a.game.scores[0])
        assertEquals(1, a.game.roundsDone)
        assertEquals(0, a.game.roundWinner)
        // 나머지: 가람 9, 나래 5 → 가람 10점
        val ranks = playToEnd(a) { if (it % 3 == 0) 1 else 0 }
        assertArrayEquals(intArrayOf(10, 5), a.game.scores)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals(intArrayOf(1, 2), ranks)
    }

    @Test
    fun threePlayersWithTieAndTimeoutRound() {
        val a = launch(four.take(3), seed = 12)
        // 라운드 1 시간 초과, 나머지 2·3·4… 를 셋이 돌아가며 → 다온 5, 가람 5, 나래 4
        val ranks = playToEnd(a) { if (it == 1) -1 else it % 3 }
        assertArrayEquals(intArrayOf(5, 4, 5), a.game.scores)
        assertArrayEquals(intArrayOf(1, 3, 1), ranks)
    }

    @Test
    fun fourPlayersPlayToEnd() {
        val a = launch(four, seed = 21)
        val ranks = playToEnd(a) { if (it <= 6) 3 else if (it <= 10) 1 else if (it <= 13) 0 else 2 }
        assertArrayEquals(intArrayOf(3, 4, 2, 6), a.game.scores)
        assertArrayEquals(intArrayOf(3, 2, 4, 1), ranks)
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
        val ans = odd(a)
        val wrong = wrongOf(a)
        // 구역 0·2·3 은 틀린 칸, 구역 1 은 정답 칸 — 네 손가락이 모두 누른 채로 하나씩 더해짐 (정답은 마지막)
        val cells = intArrayOf(wrong, ans, wrong, wrong)
        val order = intArrayOf(2, 0, 3, 1)
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        for (k in 0 until 4) {
            a.board.cellCenter(order[k], cells[order[k]], pt)
            xs[k] = pt.x
            ys[k] = pt.y
            assertEquals("손가락 $k 는 구역 ${order[k]} 안", order[k], com.siwoo.party.common.TouchZones.zoneAt((0 until 4).map { a.zones.zone(it)!! }, pt.x, pt.y))
        }
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 4) a.zones.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        for (z in 0 until 4) assertTrue("구역 $z 눌림", a.zones.isPressed(z))
        assertArrayEquals("각자 자기 구역에서만 판정", intArrayOf(0, 1, 0, 0), a.game.scores)
        assertEquals(1, a.game.roundWinner)
        for (z in intArrayOf(0, 2, 3)) assertTrue("구역 $z 잠김", a.game.isLocked(z, a.timers.now()))
        assertFalse(a.game.isLocked(1, a.timers.now()))
        for (k in 3 downTo 1) a.zones.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + 20))
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, xs.copyOf(1), ys.copyOf(1), t + 30))
        for (z in 0 until 4) assertFalse(a.zones.isPressed(z))

        // 다음 라운드: 네 손가락이 동시에 정답 칸 → 먼저 닿은 손가락(구역 2) 하나만 +1
        waitAsking(a)
        val ans2 = odd(a)
        val order2 = intArrayOf(2, 3, 0, 1)
        for (k in 0 until 4) {
            a.board.cellCenter(order2[k], ans2, pt)
            xs[k] = pt.x
            ys[k] = pt.y
        }
        val t2 = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t2))
        for (k in 1 until 4) a.zones.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t2 + k))
        assertArrayEquals(intArrayOf(0, 1, 1, 0), a.game.scores)
        assertEquals(2, a.game.roundWinner)
        assertEquals(2, a.game.roundsDone)
    }

    @Test
    fun topZoneCellsAreRotated() {
        val a = launch(four, seed = 3)
        waitAsking(a)
        val top = a.zones.zone(0)!!
        val bottom = a.zones.zone(2)!!
        assertEquals(180f, top.rotation)
        // 위쪽 구역: 그 사람 기준 왼쪽 위 칸(0)은 화면에서 오른쪽 아래, 오른쪽 아래 칸(15)은 왼쪽 위
        val c0 = PointF(); val c15 = PointF()
        a.board.cellCenter(0, 0, c0)
        a.board.cellCenter(0, 15, c15)
        assertTrue(c0.x > c15.x && c0.y > c15.y)
        val b0 = PointF()
        a.board.cellCenter(2, 0, b0)
        assertTrue("아래 구역은 그대로", b0.x < bottom.centerX && b0.y < bottom.centerY)
        // 같은 배치: 위쪽 칸 = 아래 구역 같은 칸을 구역 가운데 기준 180° 돌린 자리
        assertEquals(top.centerX - (b0.x - bottom.centerX), c0.x, 0.5f)
        assertEquals(top.centerY - (b0.y - bottom.centerY), c0.y, 0.5f)
        for (c in 0 until GameConfig.CELLS) {
            a.board.cellCenter(0, c, pt)
            assertEquals(c, a.board.cellAt(0, pt.x, pt.y))
            a.board.cellCenter(3, c, pt)
            assertEquals(c, a.board.cellAt(3, pt.x, pt.y))
        }
        // 실제 터치: 위쪽 구역에서 정답 칸 자리
        tap(a, 0, odd(a))
        assertEquals(1, a.game.scores[0])
        assertEquals(0, a.game.roundWinner)
    }

    @Test
    fun timeoutSkipsRoundAndPauseFreezesIt() {
        val a = launch(four.take(3), seed = 4)
        waitAsking(a)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.board.frozen)
        tap(a, 2, odd(a))
        idle(GameConfig.ROUND_TIMEOUT_MS * 2)
        assertEquals(0, a.game.roundsDone)
        assertSame(Phase.ASKING, a.game.phase)
        assertEquals(0, a.game.scores[2])
        // 계속하기 → 남은 시간 뒤 시간 초과로 넘어감 (아무도 점수 없음)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertFalse(a.board.frozen)
        val resumedAt = a.timers.now()
        waitFor("시간 초과") { a.game.roundsDone == 1 }
        assertTrue("일시정지 시간은 제한 시간에 안 들어감", a.timers.now() - resumedAt > GameConfig.ROUND_TIMEOUT_MS / 2)
        assertArrayEquals(intArrayOf(0, 0, 0), a.game.scores)
        assertEquals(-1, a.game.roundWinner)
        assertFalse("공개 중엔 터치 무시", a.zones.touchEnabled)
        waitAsking(a)
        assertEquals(2, a.game.current!!.number)
    }

    @Test
    fun moduleInfo() {
        assertEquals("oddone", OddOneModule.id)
        assertEquals("하나만 다른 모양", OddOneModule.name)
        assertEquals("3분", OddOneModule.minutes)
        assertEquals(3, OddOneModule.rules.size)
        assertTrue(OddOneModule.supports(2) && OddOneModule.supports(3) && OddOneModule.supports(4))
        assertFalse(OddOneModule.supports(1) || OddOneModule.supports(5))
        assertEquals(4, OddOneModule.requiredTouches(4))
    }
}
