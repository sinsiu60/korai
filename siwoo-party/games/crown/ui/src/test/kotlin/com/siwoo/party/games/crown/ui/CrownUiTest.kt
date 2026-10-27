package com.siwoo.party.games.crown.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.crown.CrownGame.Companion.CENTER
import com.siwoo.party.games.crown.CrownModule
import com.siwoo.party.games.crown.GameConfig
import com.siwoo.party.games.crown.Phase
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

/** 왕관 쟁탈 화면: 끝까지 진행 → 순위 (2·3·4인), 4손가락 동시 빼앗기·쿨다운, 떼면 가운데로, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class CrownUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>): CrownActivity {
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), CrownModule, players)
        return Robolectric.buildActivity(CrownActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitPlaying(a: CrownActivity) = waitFor("게임 시작") { a.game.phase == Phase.PLAYING && a.zones.touchEnabled }

    /** 게임 시간으로 ms 만큼 흘림 (일시정지 제외 시계 기준) */
    private fun play(a: CrownActivity, ms: Long) {
        val until = a.timers.now() + ms
        while (a.timers.now() < until && !a.isFinishing) idle(minOf(100L, until - a.timers.now()).coerceAtLeast(1L))
    }

    private fun ranksOf(a: CrownActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    /**
     * 여러 손가락을 따로 누르고 떼는 도우미 (진짜 멀티 터치 MotionEvent: DOWN / POINTER_DOWN / POINTER_UP / UP).
     * 손가락 id 는 0..9. stage(겹친 그림 층 포함)로 보낸다.
     */
    private class Fingers(private val target: View) {
        private val ids = ArrayList<Int>()
        private val xs = HashMap<Int, Float>()
        private val ys = HashMap<Int, Float>()
        private val t0 = SystemClock.uptimeMillis()

        private fun event(action: Int): MotionEvent {
            val n = ids.size
            val props = Array(n) { k -> MotionEvent.PointerProperties().apply { id = ids[k]; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(n) { k -> MotionEvent.PointerCoords().apply { x = xs[ids[k]]!!; y = ys[ids[k]]!!; pressure = 1f; size = 1f } }
            val t = SystemClock.uptimeMillis()
            return MotionEvent.obtain(t0, t, action, n, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        }

        fun down(id: Int, x: Float, y: Float) {
            ids.add(id); xs[id] = x; ys[id] = y
            val action = if (ids.size == 1) MotionEvent.ACTION_DOWN
            else MotionEvent.ACTION_POINTER_DOWN or ((ids.size - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            target.dispatchTouchEvent(event(action))
        }

        fun up(id: Int) {
            val k = ids.indexOf(id)
            check(k >= 0)
            val action = if (ids.size == 1) MotionEvent.ACTION_UP
            else MotionEvent.ACTION_POINTER_UP or (k shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            target.dispatchTouchEvent(event(action))
            ids.removeAt(k)
        }
    }

    /** 구역 zone 안 점 (구역 가운데에서 살짝 비킴 — 손가락마다 다른 자리) */
    private fun Fingers.press(a: CrownActivity, zone: Int, finger: Int = zone) {
        val z = a.zones.zone(zone)!!
        down(finger, z.centerX - 20f + finger * 3f, z.centerY + 10f)
    }

    @Test
    fun twoPlayersToTheEnd() {
        val a = launch(four.take(2))
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        waitPlaying(a)
        assertTrue("게임 중 프레임 루프", a.looping)
        assertEquals(CENTER, a.game.holder)
        val f = Fingers(a.stage)
        // 가람이 가운데 왕관을 줍고 약 8초
        f.press(a, 0)
        assertEquals(0, a.game.holder)
        assertEquals(0, a.art.crownAt)
        play(a, 8000)
        // 나래가 빼앗아 끝까지
        f.press(a, 1)
        assertEquals(1, a.game.holder)
        assertTrue("빼앗은 사람 쿨다운 고리", a.art.cooldownShown(1) > 0f)
        assertEquals(0f, a.art.cooldownShown(0), 0f)
        waitFor("시간 끝") { a.game.gameOver }
        assertFalse(a.zones.touchEnabled)
        val s0 = a.game.score(0)
        val s1 = a.game.score(1)
        assertTrue("가람 약 80점: $s0", s0 in 75..90)
        assertTrue("두 사람 합 ≈ 45초 × 10: ${s0 + s1}", s0 + s1 in 440..GameConfig.GAME_TIME_SEC * GameConfig.POINTS_PER_SEC)
        assertEquals(s0, a.art.scoreShown(0))
        assertEquals(s1, a.art.scoreShown(1))
        assertArrayEquals(intArrayOf(2, 1), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        f.up(1); f.up(0)
        idle(2000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun threePlayersToTheEnd() {
        val a = launch(four.take(3))
        waitPlaying(a)
        val f = Fingers(a.stage)
        f.press(a, 0)
        play(a, 4000)
        f.press(a, 1) // 1 이 빼앗음 (0 은 누르고만 있음)
        play(a, 9000)
        f.up(1) // 1 이 떼면 가운데로 → 누르던 0 은 못 가져감
        assertEquals(CENTER, a.game.holder)
        play(a, 500)
        assertEquals(CENTER, a.game.holder)
        f.press(a, 2) // 2 가 새로 눌러 줍고 끝까지
        assertEquals(2, a.game.holder)
        val r = ranksOf(a)
        assertTrue(a.game.score(0) in 35..50)
        assertTrue(a.game.score(1) in 85..100)
        assertTrue(a.game.score(2) > a.game.score(1))
        assertArrayEquals(intArrayOf(3, 2, 1), r)
    }

    @Test
    fun fourPlayersToTheEnd() {
        val a = launch(four)
        waitPlaying(a)
        val f = Fingers(a.stage)
        f.press(a, 3)
        play(a, 18_000)
        f.press(a, 0) // 0 이 빼앗음
        f.up(3)
        play(a, 13_000)
        f.up(0) // 가운데로
        assertEquals(CENTER, a.game.holder)
        f.press(a, 2) // 2 가 줍고 끝까지 (~14초)
        val r = ranksOf(a)
        // 3 ≈ 180, 0 ≈ 130, 2 ≈ 140 보다 적거나 비슷 → 순위는 실제 점수로 확인
        assertEquals(0, a.game.score(1))
        assertTrue(a.game.score(3) > a.game.score(0))
        assertEquals(1, r[3])
        assertEquals(4, r[1])
        val expect = com.siwoo.party.common.Ranking.competition(List(4) { a.game.score(it).toLong() })
        assertArrayEquals(expect, r)
    }

    @Test
    fun fourFingersStealsAndCooldownsPerZone() {
        val a = launch(four)
        waitPlaying(a)
        val f = Fingers(a.stage)
        // 네 손가락이 0,1,2,3 순서로 거의 동시에 → 0 이 줍고 1·2·3 이 차례로 빼앗음
        for (z in 0 until 4) f.press(a, z)
        for (z in 0 until 4) assertTrue("구역 $z 눌림", a.zones.isPressed(z))
        for (z in 0 until 4) assertTrue(a.game.holding[z])
        assertEquals(3, a.game.holder)
        assertArrayEquals(intArrayOf(0, 1, 1, 1), a.game.steals)
        idle(50)
        assertEquals("가운데에서 주운 0 은 쿨다운 없음", 0f, a.art.cooldownShown(0), 0f)
        for (z in 1..3) assertTrue("구역 $z 쿨다운", a.art.cooldownShown(z) > 0f)
        // 1 이 떼고 쿨다운 중 다시 누름 → 못 빼앗음
        f.up(1)
        assertEquals(3, a.game.holder)
        f.press(a, 1, finger = 5)
        assertEquals(3, a.game.holder)
        // 0 은 쿨다운 없음 → 다시 눌러 빼앗음 (손가락 하나 더)
        f.press(a, 0, finger = 6)
        assertEquals(0, a.game.holder)
        assertEquals(3, a.game.lastVictim)
        // 2초 지나면 1 도 빼앗을 수 있음
        play(a, GameConfig.STEAL_COOLDOWN_MS + 100)
        for (z in 1..3) assertEquals("구역 $z 쿨다운 끝", 0f, a.art.cooldownShown(z), 0f)
        f.up(5)
        f.press(a, 1, finger = 7)
        assertEquals(1, a.game.holder)
        // 점수는 왕관을 가진 동안만: 0·1·2 는 짧게, 3 은 처음 잠깐
        assertTrue(a.game.score(0) >= 20)
        // 0 의 손가락 하나만 떼면 아직 누르는 중, 1 이 다 떼면 가운데로
        f.up(6)
        assertTrue(a.game.holding[0])
        f.up(7)
        assertEquals(CENTER, a.game.holder)
        assertEquals(CENTER, a.art.crownAt)
        // 누르고 있던 0·2·3 은 다시 눌러야 줍는다
        play(a, 300)
        assertEquals(CENTER, a.game.holder)
        f.up(2)
        f.press(a, 2, finger = 8)
        assertEquals(2, a.game.holder)
    }

    @Test
    fun releaseSendsCrownToCenterAndFlies() {
        val a = launch(four.take(2))
        waitPlaying(a)
        val f = Fingers(a.stage)
        f.press(a, 1)
        assertTrue("왕관이 날아가는 중", a.art.flying)
        waitFor("도착") { !a.art.flying }
        assertTrue(a.zones.isPressed(1))
        a.game.update(a.timers.now())
        val before = a.game.heldMs[1]
        play(a, 1000)
        a.game.update(a.timers.now())
        assertTrue("누르는 동안 쌓임", a.game.heldMs[1] - before in 1000L..1150L)
        val s = a.game.score(1)
        f.up(1)
        assertEquals(CENTER, a.game.holder)
        assertEquals(CENTER, a.art.crownAt)
        play(a, 1000)
        assertEquals("떼면 점수 멈춤", s, a.game.score(1))
    }

    @Test
    fun pauseFreezesScoringAndClock() {
        val a = launch(four.take(2))
        waitPlaying(a)
        val f = Fingers(a.stage)
        f.press(a, 0)
        play(a, 1500)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지 중 프레임 멈춤", a.looping)
        val score = a.game.score(0)
        val shown = a.art.scoreShown(0)
        val left = a.game.timeLeft(a.timers.now())
        idle(GameConfig.GAME_TIME_MS + 5000)
        assertSame("일시정지 중 시간이 가지 않음", Phase.PLAYING, a.game.phase)
        a.game.update(a.timers.now())
        assertEquals("일시정지 중 점수 그대로", score, a.game.score(0))
        assertEquals(shown, a.art.scoreShown(0))
        assertEquals(left, a.game.timeLeft(a.timers.now()))
        // 일시정지 중 누르기는 무시
        a.onZoneDown(1, 10f, 10f, SystemClock.uptimeMillis())
        assertEquals(0, a.game.holder)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        play(a, 1000)
        assertTrue("다시 점수가 쌓임", a.game.score(0) >= score + 9)
        assertEquals(0, a.game.holder)
    }

    @Test
    fun moduleInfo() {
        assertEquals("crown", CrownModule.id)
        assertEquals("왕관 쟁탈", CrownModule.name)
        assertSame(GameCategory.REFLEX, CrownModule.category)
        assertSame(PlayMode.SIMULTANEOUS, CrownModule.mode)
        assertEquals("1분", CrownModule.minutes)
        assertEquals(3, CrownModule.rules.size)
        assertEquals(4, CrownModule.needsMultiTouch)
        assertTrue(CrownModule.supports(2) && CrownModule.supports(3) && CrownModule.supports(4))
        assertFalse(CrownModule.supports(5))
    }
}
