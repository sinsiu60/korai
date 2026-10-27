package com.siwoo.party.games.rhythm.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.lane.ItemState
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.rhythm.GameConfig
import com.siwoo.party.games.rhythm.RhythmModule
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

/** 리듬 노트 화면: 4손가락 동시 판정, 2·3·4인 60초 끝까지, 일시정지(노트·박자음 멈춤) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class RhythmUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { RhythmActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): RhythmActivity {
        RhythmActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), RhythmModule, players)
        return Robolectric.buildActivity(RhythmActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(3000) { if (cond()) return; idle(20) }
        error("$what 가 되지 않음")
    }

    /** 경기 시간 */
    private fun el(a: RhythmActivity) = a.game.elapsed(a.timers.now())

    /** 경기 시간이 t 가 될 때까지 조금씩 흘림 */
    private fun waitUntil(a: RhythmActivity, t: Long) {
        while (el(a) < t) idle(minOf(4L, t - el(a)).coerceAtLeast(1L))
    }

    /** 구역 zone 가운데를 한 손가락으로 톡 — 화면(stage)에 보내서 위에 겹친 LaneView 를 지나 구역으로 가는지도 확인 */
    private fun tap(a: RhythmActivity, zone: Int) {
        val z = a.zones.zone(zone)!!
        val stage = a.zones.parent as ViewGroup
        val t = SystemClock.uptimeMillis()
        stage.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, z.centerX, z.centerY, 0))
        stage.dispatchTouchEvent(MotionEvent.obtain(t, t + 5, MotionEvent.ACTION_UP, z.centerX, z.centerY, 0))
    }

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun ranksOf(a: RhythmActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    /** from 이후 앞뒤 노트와 2 × MISS_MS 넘게 떨어진 첫 노트 */
    private fun isolatedAfter(a: RhythmActivity, from: Long): Int {
        val p = a.game.pattern
        for (i in 1 until p.count - 1) {
            if (p.time(i) < from) continue
            if (p.time(i) - p.time(i - 1) > 2 * GameConfig.MISS_MS && p.time(i + 1) - p.time(i) > 2 * GameConfig.MISS_MS) return i
        }
        error("고립 노트 없음")
    }

    @Test
    fun fourFingersAtTheJudgeTimeAreJudgedPerZone() {
        val a = launch(four)
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        waitFor("경기 시작") { a.playing }
        val i = isolatedAfter(a, el(a) + 1_000)
        val t0 = a.game.pattern.time(i)
        waitUntil(a, t0 - 3)
        val order = intArrayOf(3, 0, 1, 2)
        val xs = FloatArray(4) { a.zones.zone(order[it])!!.centerX }
        val ys = FloatArray(4) { a.zones.zone(order[it])!!.centerY }
        val t = SystemClock.uptimeMillis()
        // 손가락 셋 (구역 3, 0, 1) 동시에
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 3) {
            a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN or (k shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        }
        for (z in intArrayOf(3, 0, 1)) {
            assertTrue(a.zones.isPressed(z))
            assertEquals("구역 $z 퍼펙트", ItemState.PERFECT, a.game.tracks[z].state(i))
            assertEquals(GameConfig.PERFECT_SCORE, a.game.score(z))
        }
        assertEquals("안 누른 구역 2 는 그대로", ItemState.PENDING, a.game.tracks[2].state(i))
        assertEquals(0, a.game.score(2))
        // 조금 늦게 넷째 손가락 (구역 2) → 자기 판정만
        idle(90)
        val late = el(a) - t0
        assertTrue("늦음 $late", late in (GameConfig.PERFECT_MS + 1)..GameConfig.GOOD_MS)
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN or (3 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs, ys, t + 100))
        assertTrue(a.zones.isPressed(2))
        assertEquals("구역 2 굿", ItemState.GOOD, a.game.tracks[2].state(i))
        assertEquals(GameConfig.GOOD_SCORE, a.game.score(2))
        for (z in intArrayOf(3, 0, 1)) assertEquals(ItemState.PERFECT, a.game.tracks[z].state(i))
        // 구역 0 손가락만 떼었다 다시 → 같은 노트는 두 번 점수 안 남
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs, ys, t + 110))
        assertFalse(a.zones.isPressed(0))
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs, ys, t + 120))
        assertTrue(a.zones.isPressed(0))
        for (z in intArrayOf(3, 0, 1)) assertEquals("구역 $z 점수 그대로", GameConfig.PERFECT_SCORE, a.game.score(z))
        assertEquals(1, a.game.combo(0))
    }

    /**
     * 60초 끝까지: 구역 0 = 모든 노트 정확히, 1 = 짝수 번째 노트만 정확히, 2 = 안 침, 3 = 모든 노트 100ms 늦게(굿).
     * 점수 0 > 1 > 3 > 2.
     */
    private fun playToEnd(n: Int, seed: Int): RhythmActivity {
        val a = launch(four.take(n), seed)
        waitFor("경기 시작") { a.playing }
        assertTrue(a.looping)
        val p = a.game.pattern
        val next = IntArray(n)
        val offset = longArrayOf(0L, 0L, -1L, 100L)
        while (a.playing) {
            val e = el(a)
            for (z in 0 until n) {
                if (offset[z] < 0) continue
                while (next[z] < p.count && (e > p.time(next[z]) + offset[z] + GameConfig.PERFECT_MS || (z == 1 && next[z] % 2 == 1))) next[z]++
                if (next[z] < p.count && e >= p.time(next[z]) + offset[z]) {
                    tap(a, z)
                    next[z]++
                }
            }
            idle(8)
        }
        assertTrue(a.game.isOver(a.timers.now()))
        // 박자음: 모든 박자가 제 시각에 (밀린 것 없이)
        assertEquals(a.game.beats.size, a.beatsFired)
        assertEquals(0, a.beatsSkipped)
        assertTrue("박자음 늦음 ${a.maxTickLateMs}", a.maxTickLateMs <= 20)
        val tr0 = a.game.tracks[0]
        assertEquals("구역 0 모두 퍼펙트", p.count, tr0.perfects)
        assertEquals(3 * p.count, a.game.score(0))
        assertEquals(p.count, a.game.maxCombo(0))
        assertTrue(a.game.tracks[1].perfects >= p.count / 2 - 1)
        assertEquals("구역 1 은 하나 걸러 → 콤보 1", 1, a.game.tracks[1].maxCombo)
        for (z in 0 until n) assertTrue("구역 $z 모두 판정 끝", a.game.tracks[z].finished)
        return a
    }

    @Test
    fun twoPlayersPlayToTheEnd() {
        val a = playToEnd(2, 8)
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun threePlayersPlayToTheEnd() {
        val a = playToEnd(3, 3)
        assertEquals(0, a.game.score(2))
        assertArrayEquals(intArrayOf(1, 2, 3), ranksOf(a))
    }

    @Test
    fun fourPlayersPlayToTheEnd() {
        val a = playToEnd(4, 11)
        val s = IntArray(4) { a.game.score(it) }
        assertTrue("점수 ${s.toList()}", s[0] > s[1] && s[1] > s[3] && s[3] > s[2])
        assertTrue(a.game.tracks[3].goods > a.game.pattern.count / 2)
        assertArrayEquals(intArrayOf(1, 2, 4, 3), ranksOf(a))
    }

    @Test
    fun pauseFreezesNotesAndBeat() {
        val a = launch(four.take(2), seed = 4)
        waitFor("경기 시작") { a.playing }
        idle(3_000)
        assertTrue(a.beatsFired > 0)
        assertEquals("박자음 = 지난 박자 수", a.game.beatsUpTo(el(a)), a.beatsFired)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(50)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.looping)
        val e = el(a)
        val beats = a.beatsFired
        val drawn = a.lanes.nowMs
        val misses = a.game.tracks[0].misses
        idle(20_000)
        assertEquals("일시정지 중 시간 멈춤", e, el(a))
        assertEquals("일시정지 중 박자음 없음", beats, a.beatsFired)
        assertEquals("노트 그림 멈춤", drawn, a.lanes.nowMs)
        assertEquals("지나간 노트 처리도 멈춤", misses, a.game.tracks[0].misses)
        assertTrue(a.playing)
        // 일시정지 중 구역 이벤트는 무시
        a.onZoneDown(0, 0f, 0f, SystemClock.uptimeMillis())
        assertEquals(0, a.game.score(0))
        assertEquals(misses, a.game.tracks[0].misses)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        idle(2_000)
        assertTrue(el(a) > e)
        assertTrue(a.beatsFired > beats)
        assertEquals("다시 시작해도 박자음 = 지난 박자 수", a.game.beatsUpTo(el(a)), a.beatsFired)
        assertTrue("박자음 늦음 ${a.maxTickLateMs}", a.maxTickLateMs <= 20)
        assertTrue(a.lanes.nowMs > drawn)
    }

    @Test
    fun beatTicksRespectSoundSettingAndAreReleased() {
        // 효과음 꺼짐 → 박자음 트랙을 만들지도 않음
        val off = RhythmTicks()
        off.play(true, false)
        assertEquals(0, off.created)
        // 코드로 만든 짧은 틱: 45ms, 소리가 있고 끝은 거의 0
        val s = RhythmTicks.samples(GameConfig.ACCENT_HZ)
        assertEquals(GameConfig.SAMPLE_RATE * GameConfig.TICK_MS / 1000, s.size)
        assertTrue(s.maxOf { kotlin.math.abs(it.toInt()) } > 8_000)
        assertTrue(kotlin.math.abs(s.last().toInt()) < 1_000)
        // 화면이 끝나면 release
        RhythmActivity.randomOverride = Random(1)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), RhythmModule, four.take(2))
        val ctl = Robolectric.buildActivity(RhythmActivity::class.java, i).setup()
        val a = ctl.get()
        waitFor("경기 시작") { a.playing }
        idle(1_500)
        assertTrue(a.beatsFired > 0)
        ctl.pause().stop().destroy()
        assertEquals(0, a.ticks.created)
        assertFalse(a.looping)
    }

    @Test
    fun moduleInfo() {
        assertEquals("rhythm", RhythmModule.id)
        assertEquals("리듬 노트", RhythmModule.name)
        assertEquals(GameCategory.REFLEX, RhythmModule.category)
        assertEquals(PlayMode.SIMULTANEOUS, RhythmModule.mode)
        assertEquals("2분", RhythmModule.minutes)
        assertEquals(3, RhythmModule.rules.size)
        assertEquals(2..4, RhythmModule.playerCounts)
        assertEquals(4, RhythmModule.requiredTouches(4))
        assertEquals(60_000L, GameConfig.GAME_MS)
    }
}
