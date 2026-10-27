package com.siwoo.party.games.sprint.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.sprint.Foot
import com.siwoo.party.games.sprint.GameConfig
import com.siwoo.party.games.sprint.Phase
import com.siwoo.party.games.sprint.SprintModule
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
 * 100m 달리기 화면: 4구역 동시 터치(위쪽 구역은 180° → 왼발/오른발이 뒤집힘), 부정 출발은 그 구역만 정지,
 * 2·3·4인 끝까지 달려 순위(동점 포함), 제한 시간 → 거리순, 일시정지하면 시계·입력이 멈춤.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class SprintUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        SprintActivity.randomOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    /** 프레임 단위(16ms)로 시간을 흘린다 (한 번에 크게 건너뛰면 프레임이 하나만 돈다) */
    private fun frames(ms: Long) {
        var left = ms
        while (left > 0) { idle(16); left -= 16 }
    }

    private fun launch(players: List<PlayerInfo>): SprintActivity {
        SprintActivity.randomOverride = Random(5)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), SprintModule, players)
        return Robolectric.buildActivity(SprintActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(20) }
        error("$what 가 되지 않음")
    }

    private fun waitRunning(a: SprintActivity) = waitFor("땅!") { a.game.phase == Phase.RUNNING }

    // ── 여러 손가락 ──

    /** 손가락 하나 = (구역, 발) */
    private class Finger(val zone: Int, val foot: Foot)

    private fun ev(a: SprintActivity, action: Int, f: List<Finger>, count: Int, t: Long): MotionEvent {
        val props = Array(count) { k -> MotionEvent.PointerProperties().apply { id = k; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(count) { k ->
            MotionEvent.PointerCoords().apply {
                x = a.track.buttonCenterX(f[k].zone, f[k].foot)
                y = a.track.buttonCenterY(f[k].zone, f[k].foot)
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(t, t, action, count, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun pointer(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    /** 손가락들을 같은 순간에 차례로 내렸다가 올린다 */
    private fun fingers(a: SprintActivity, f: List<Finger>) {
        val n = f.size
        if (n == 0) return
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(ev(a, MotionEvent.ACTION_DOWN, f, 1, t))
        for (k in 1 until n) a.zones.dispatchTouchEvent(ev(a, pointer(MotionEvent.ACTION_POINTER_DOWN, k), f, k + 1, t))
        for (k in n - 1 downTo 1) a.zones.dispatchTouchEvent(ev(a, pointer(MotionEvent.ACTION_POINTER_UP, k), f, k + 1, t))
        a.zones.dispatchTouchEvent(ev(a, MotionEvent.ACTION_UP, f, 1, t))
    }

    private fun tap(a: SprintActivity, zone: Int, foot: Foot) = fingers(a, listOf(Finger(zone, foot)))

    private fun ranksOf(a: SprintActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    /**
     * 각자 delay[i] 번째 차례부터 왼발·오른발 번갈아 한 번씩 (한 차례 = 모두 같은 순간의 한 MotionEvent 묶음).
     * delay 가 같으면 같은 ms 에 도착 → 같은 순위. delay < 0 이면 안 달림.
     */
    private fun race(a: SprintActivity, delay: IntArray, gapMs: Long = 30) {
        var round = 0
        while (a.game.phase == Phase.RUNNING && round < 200 && delay.indices.any { delay[it] >= 0 && !a.game.finished(it) }) {
            val list = ArrayList<Finger>()
            for (i in delay.indices) {
                val d = delay[i]
                if (d < 0 || round < d || a.game.finished(i)) continue
                list.add(Finger(i, if ((round - d) % 2 == 0) Foot.LEFT else Foot.RIGHT))
            }
            fingers(a, list)
            idle(gapMs)
            round++
        }
    }

    @Test
    fun fourZonesPressTogetherEachMovesOnlyOwnRunner() {
        val a = launch(four)
        assertFalse("소개 중엔 터치 무시", a.zones.touchEnabled)
        waitRunning(a)
        // 위쪽 구역(180°)은 그 사람 기준 왼발이 화면 오른쪽
        for (i in 0 until 4) {
            val z = a.zones.zone(i)!!
            val lx = a.track.buttonCenterX(i, Foot.LEFT)
            val ly = a.track.buttonCenterY(i, Foot.LEFT)
            assertTrue("구역 $i 안", z.contains(lx, ly))
            assertEquals(i, com.siwoo.party.common.TouchZones.zoneAt(listOf(a.zones.zone(0)!!, a.zones.zone(1)!!, a.zones.zone(2)!!, a.zones.zone(3)!!), lx, ly))
            if (z.rotation == 180f) assertTrue("구역 $i 왼발 = 화면 오른쪽", lx > z.centerX) else assertTrue(lx < z.centerX)
            assertEquals(Foot.LEFT, a.track.footAt(i, lx))
            assertEquals(Foot.RIGHT, a.track.footAt(i, a.track.buttonCenterX(i, Foot.RIGHT)))
        }
        // 네 사람 동시에 왼발
        fingers(a, (0 until 4).map { Finger(it, Foot.LEFT) })
        assertArrayEquals(intArrayOf(1, 1, 1, 1), a.game.steps)
        for (i in 0 until 4) assertEquals(Foot.LEFT, a.game.lastFoot[i])
        // 여덟 손가락 (네 사람 × 오른발·왼발) 동시에 → 각자 두 걸음
        fingers(a, (0 until 4).flatMap { listOf(Finger(it, Foot.RIGHT), Finger(it, Foot.LEFT)) })
        assertArrayEquals(intArrayOf(3, 3, 3, 3), a.game.steps)
        // 나래만 같은 발(왼발 연속) → 나래만 무효
        fingers(a, listOf(Finger(0, Foot.RIGHT), Finger(1, Foot.LEFT), Finger(2, Foot.RIGHT), Finger(3, Foot.RIGHT)))
        assertArrayEquals(intArrayOf(4, 3, 4, 4), a.game.steps)
        assertTrue("나래 구역 흔들림", a.track.shaking(1))
        assertFalse(a.track.shaking(0))
        // 다온만 다섯 걸음 → 다온 선수만 앞으로
        frames(600)
        val before = FloatArray(4) { a.track.runnerX(it) }
        for (k in 0 until 5) { tap(a, 2, if (k % 2 == 0) Foot.LEFT else Foot.RIGHT); frames(32) }
        frames(600)
        assertArrayEquals(intArrayOf(4, 3, 9, 4), a.game.steps)
        assertTrue(a.track.runnerX(2) > before[2] + 1f)
        for (i in intArrayOf(0, 1, 3)) assertEquals(before[i], a.track.runnerX(i), 0.01f)
        assertEquals(9f * GameConfig.STEP_CM, a.track.shownDistance(2), 0.01f)
    }

    @Test
    fun falseStartFreezesOnlyThatZone() {
        val a = launch(four.take(3))
        waitFor("준비") { a.game.phase == Phase.SET }
        tap(a, 1, Foot.LEFT)
        assertTrue(a.game.falseStart[1])
        assertFalse(a.game.falseStart[0] || a.game.falseStart[2])
        assertEquals("부정 출발!", a.track.statusOf(1))
        assertEquals(0, a.game.steps[1])
        waitRunning(a)
        idle(20)
        assertTrue("나래 정지 표시", a.track.isFrozen(1))
        assertFalse(a.track.isFrozen(0) || a.track.isFrozen(2))
        // 모두 같이 눌러도 나래만 못 나감
        fingers(a, (0 until 3).map { Finger(it, Foot.LEFT) })
        fingers(a, (0 until 3).map { Finger(it, Foot.RIGHT) })
        assertArrayEquals(intArrayOf(2, 0, 2), a.game.steps)
        assertEquals(null, a.game.lastFoot[1])
        // 1초가 지나면 나래도 달린다
        waitFor("정지 풀림") { a.timers.now() >= a.game.frozenUntil(1) + 20 }
        idle(40)
        assertFalse("정지 표시 해제", a.track.isFrozen(1))
        assertEquals("달려요!", a.track.statusOf(1))
        fingers(a, (0 until 3).map { Finger(it, Foot.LEFT) })
        assertArrayEquals(intArrayOf(3, 1, 3), a.game.steps)
    }

    @Test
    fun twoPlayersFullRace() {
        val a = launch(four.take(2))
        waitRunning(a)
        race(a, intArrayOf(2, 0))
        assertEquals(Phase.OVER, a.game.phase)
        assertTrue(a.game.finishMs[1] < a.game.finishMs[0])
        assertEquals(1, a.track.rankOf(1))
        assertEquals(2, a.track.rankOf(0))
        assertFalse("끝나면 터치 무시", a.zones.touchEnabled)
        assertArrayEquals(intArrayOf(2, 1), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        idle(3000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun threePlayersFullRace() {
        val a = launch(four.take(3))
        waitRunning(a)
        race(a, intArrayOf(1, 3, 0))
        assertArrayEquals(intArrayOf(2, 3, 1), ranksOf(a))
    }

    @Test
    fun fourPlayersFullRaceWithTie() {
        val a = launch(four)
        waitRunning(a)
        // 나래 1위, 가람·다온 같은 순간 도착(공동 2위), 라온 4위
        race(a, intArrayOf(1, 0, 1, 2))
        assertEquals(a.game.finishMs[0], a.game.finishMs[2])
        assertArrayEquals(intArrayOf(2, 1, 2, 4), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1, 2, 3), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
    }

    @Test
    fun timeLimitRanksRemainingByDistance() {
        val a = launch(four.take(3))
        waitRunning(a)
        race(a, intArrayOf(0, -1, -1))           // 가람만 도착
        assertTrue(a.game.finished(0))
        for (k in 0 until 30) { tap(a, 1, if (k % 2 == 0) Foot.LEFT else Foot.RIGHT); idle(20) }
        for (k in 0 until 12) { tap(a, 2, if (k % 2 == 0) Foot.LEFT else Foot.RIGHT); idle(20) }
        assertEquals(Phase.RUNNING, a.game.phase)
        repeat(400) { if (a.game.phase == Phase.OVER) return@repeat; idle(200) }
        assertTrue(a.game.timedOut)
        assertEquals(GameConfig.TIME_LIMIT_MS, a.game.elapsed(a.timers.now()))
        assertEquals("40.00초", a.track.clockText())
        // 끝난 뒤 누름은 무시
        tap(a, 2, Foot.RIGHT)
        assertEquals(12, a.game.steps[2])
        assertArrayEquals(intArrayOf(1, 2, 3), ranksOf(a))
    }

    @Test
    fun pauseFreezesClockAndIgnoresInput() {
        val a = launch(four.take(2))
        waitRunning(a)
        for (k in 0 until 4) { tap(a, 0, if (k % 2 == 0) Foot.LEFT else Foot.RIGHT); idle(100) }
        idle(800)
        assertEquals(4, a.game.steps[0])
        @Suppress("DEPRECATION") a.onBackPressed()
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        assertFalse("일시정지 중 루프 멈춤", a.looping)
        val el = a.game.elapsed(a.timers.now())
        val clock = a.track.clockText()
        idle(3000)
        assertEquals("경주 시계 멈춤", el, a.game.elapsed(a.timers.now()))
        assertEquals(clock, a.track.clockText())
        // 일시정지 중 누름 무시
        a.onZoneDown(0, a.track.buttonCenterX(0, Foot.LEFT), a.track.buttonCenterY(0, Foot.LEFT), SystemClock.uptimeMillis())
        a.onZoneDown(1, a.track.buttonCenterX(1, Foot.LEFT), a.track.buttonCenterY(1, Foot.LEFT), SystemClock.uptimeMillis())
        assertArrayEquals(intArrayOf(4, 0), a.game.steps)
        // 계속하기 → 시계가 다시 가고 누르면 달린다
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        idle(500)
        assertTrue(a.game.elapsed(a.timers.now()) > el)
        assertNotEquals(clock, a.track.clockText())
        tap(a, 1, Foot.LEFT)
        assertArrayEquals(intArrayOf(4, 1), a.game.steps)
    }

    @Test
    fun moduleInfo() {
        assertEquals("sprint", SprintModule.id)
        assertEquals("100m 달리기", SprintModule.name)
        assertEquals(3, SprintModule.rules.size)
        assertEquals("1분", SprintModule.minutes)
        assertEquals("2~4인", SprintModule.playersLabel)
        assertEquals(4, SprintModule.requiredTouches(4))
    }
}
