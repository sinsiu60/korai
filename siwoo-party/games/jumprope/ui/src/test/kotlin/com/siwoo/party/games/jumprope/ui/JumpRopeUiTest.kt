package com.siwoo.party.games.jumprope.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.jumprope.JumpRopeModule
import com.siwoo.party.games.jumprope.Miss
import com.siwoo.party.games.jumprope.Phase
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

/** 줄넘기 생존 화면: 4손가락 동시 점프, 한 명만 놓치면 그 사람만 넘어짐, 2·3·4인 끝까지 → 순위, 일시정지하면 줄이 멈춤 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class JumpRopeUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        JumpRopeActivity.randomOverride = null
        JumpRopeActivity.doubleChanceOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, chance: Double = 0.0): JumpRopeActivity {
        JumpRopeActivity.randomOverride = Random(3)
        JumpRopeActivity.doubleChanceOverride = chance
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), JumpRopeModule, players)
        return Robolectric.buildActivity(JumpRopeActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(20) }
        error("$what 가 되지 않음")
    }

    private fun waitPlaying(a: JumpRopeActivity) = waitFor("줄 돌기 시작") { a.game.phase == Phase.PLAYING && a.looping }

    /** 다음 접촉 lead ms 전까지 시간을 흘린다 */
    private fun untilContact(a: JumpRopeActivity, lead: Long = 4) {
        repeat(2000) {
            val now = a.timers.now()
            if (a.game.nextContactAt(now) - now <= lead) return
            idle(4)
        }
        error("접촉 시각에 닿지 않음")
    }

    // ── 여러 손가락 ──

    private fun ev(a: JumpRopeActivity, action: Int, zoneIdx: IntArray, count: Int): MotionEvent {
        val props = Array(count) { k -> MotionEvent.PointerProperties().apply { id = k; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(count) { k ->
            val z = a.zones.zone(zoneIdx[k])!!
            MotionEvent.PointerCoords().apply { x = z.centerX + 12f; y = z.centerY + 8f; pressure = 1f; size = 1f }
        }
        val t = SystemClock.uptimeMillis()
        return MotionEvent.obtain(t, t, action, count, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun pointer(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    /** zoneIdx 구역들에 손가락을 동시에 내렸다 올림 (한 손가락씩 차례로 down, 같은 시각) */
    private fun fingers(a: JumpRopeActivity, zoneIdx: IntArray) {
        val n = zoneIdx.size
        if (n == 0) return
        a.zones.dispatchTouchEvent(ev(a, MotionEvent.ACTION_DOWN, zoneIdx, 1))
        for (k in 1 until n) a.zones.dispatchTouchEvent(ev(a, pointer(MotionEvent.ACTION_POINTER_DOWN, k), zoneIdx, k + 1))
        for (k in n - 1 downTo 1) a.zones.dispatchTouchEvent(ev(a, pointer(MotionEvent.ACTION_POINTER_UP, k), zoneIdx, k + 1))
        a.zones.dispatchTouchEvent(ev(a, MotionEvent.ACTION_UP, zoneIdx, 1))
    }

    /** 다음 접촉: tapping 구역만 제때 누르고 판정이 끝날 때까지 */
    private fun play(a: JumpRopeActivity, vararg tapping: Int) {
        val idx = a.game.pending
        untilContact(a)
        fingers(a, tapping)
        waitFor("접촉 $idx 판정") { a.game.pending > idx || a.game.phase == Phase.OVER }
    }

    private fun ranksOf(a: JumpRopeActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun fourFingersAtContactAllJumpThenOneMissFallsAlone() {
        val a = launch(four)
        assertFalse("시작 전엔 탭 무시", a.zones.touchEnabled)
        waitPlaying(a)
        untilContact(a)
        fingers(a, intArrayOf(0, 1, 2, 3))
        for (i in 0 until 4) assertTrue("$i 점프", a.scene.jumping(i))
        waitFor("첫 판정") { a.game.pending == 1 }
        assertArrayEquals(intArrayOf(1, 1, 1, 1), a.game.jumps)
        for (i in 0 until 4) assertFalse(a.game.out[i])
        // 두 번째: 다온(구역 2)만 안 누름 → 다온만 넘어짐
        play(a, 0, 1, 3)
        assertTrue(a.game.out[2])
        assertEquals(Miss.MISS, a.game.reason[2])
        assertTrue(a.scene.fallen(2))
        for (i in intArrayOf(0, 1, 3)) {
            assertFalse(a.game.out[i])
            assertFalse(a.scene.fallen(i))
        }
        assertTrue(a.scene.sub(2).startsWith("탈락"))
        assertEquals(Phase.PLAYING, a.game.phase)
        // 탈락한 다온이 나중에 눌러도 무시
        play(a, 0, 1, 2, 3)
        assertArrayEquals(intArrayOf(3, 3, 1, 3), a.game.jumps)
    }

    @Test
    fun twoPlayersFullGame() {
        val a = launch(four.take(2))
        waitPlaying(a)
        play(a, 0, 1)
        play(a, 0)
        assertEquals(Phase.OVER, a.game.phase)
        assertEquals(0, a.game.winner)
        assertFalse("끝나면 탭 무시", a.zones.touchEnabled)
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        idle(3000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun threePlayersFullGame() {
        val a = launch(four.take(3))
        waitPlaying(a)
        play(a, 0, 1)   // 다온 탈락
        play(a, 1)      // 가람 탈락 → 나래 승
        assertEquals(1, a.game.winner)
        assertArrayEquals(intArrayOf(2, 1, 3), ranksOf(a))
    }

    @Test
    fun fourPlayersFullGameWithSharedRank() {
        val a = launch(four)
        waitPlaying(a)
        play(a, 1, 2)   // 가람·라온 같은 접촉에서 탈락
        play(a, 0, 1, 2, 3)
        play(a, 2)      // 나래 탈락 → 다온 승
        assertEquals(2, a.game.winner)
        assertArrayEquals(intArrayOf(3, 2, 1, 3), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1, 2, 3), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
    }

    @Test
    fun allMissTogetherShareFirst() {
        val a = launch(four.take(3))
        waitPlaying(a)
        play(a, 0, 1, 2)
        play(a)
        assertEquals(-1, a.game.winner)
        assertArrayEquals(intArrayOf(1, 1, 1), ranksOf(a))
    }

    @Test
    fun earlyTapJumpsThenTripsWhenRopeArrives() {
        val a = launch(four.take(2))
        waitPlaying(a)
        idle(200)
        fingers(a, intArrayOf(1)) // 줄이 아직 머리 위 → 너무 일찍
        assertTrue(a.game.out[1])
        assertEquals(Miss.EARLY, a.game.reason[1])
        assertTrue(a.scene.sub(1).contains("일찍"))
        assertFalse("줄이 오기 전엔 아직 서 있음", a.scene.fallen(1))
        play(a, 0)
        assertTrue("줄이 바닥을 칠 때 넘어짐", a.scene.fallen(1))
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
    }

    @Test
    fun doubleSpinNeedsTwoTaps() {
        val a = launch(four.take(2), chance = 1.0)
        waitPlaying(a)
        repeat(6) { play(a, 0, 1) }
        assertTrue(a.game.next.doubleFirst)
        play(a, 0, 1)   // 쌩쌩이 첫 바퀴
        play(a, 0)      // 두 번째 바퀴: 나래는 안 누름
        assertTrue(a.game.out[1])
        assertEquals(0, a.game.winner)
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
    }

    @Test
    fun pauseStopsTheRope() {
        val a = launch(four.take(2))
        waitPlaying(a)
        play(a, 0, 1)
        idle(200)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지 중 프레임 멈춤", a.looping)
        val phase = a.scene.ropePhase
        val ropeNow = a.game.ropePhase(a.timers.now())
        idle(6000)
        assertEquals("화면 줄이 멈춤", phase, a.scene.ropePhase, 0.0001f)
        assertEquals("규칙 줄도 멈춤", ropeNow, a.game.ropePhase(a.timers.now()), 0.0001f)
        assertEquals("일시정지 중엔 판정 없음", 1, a.game.pending)
        assertFalse(a.game.out[0] || a.game.out[1])
        // 일시정지 중 터치는 무시
        a.onZoneDown(0, 10f, 10f, SystemClock.uptimeMillis())
        assertFalse(a.game.out[0])
        // 계속하기 → 잠깐 멈췄다가 다시 돈다
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        assertTrue("돌아오면 줄 잠깐 멈춤", a.game.holding(a.timers.now()))
        idle(500)
        assertEquals(ropeNow, a.game.ropePhase(a.timers.now()), 0.0001f)
        play(a, 0, 1)
        assertEquals(2, a.game.pending)
        assertFalse(a.game.out[0] || a.game.out[1])
    }

    @Test
    fun moduleInfo() {
        assertEquals("jumprope", JumpRopeModule.id)
        assertEquals(3, JumpRopeModule.rules.size)
        assertEquals("2분", JumpRopeModule.minutes)
        assertEquals("2~4인", JumpRopeModule.playersLabel)
        assertEquals(4, JumpRopeModule.requiredTouches(4))
    }
}
