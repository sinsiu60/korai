package com.siwoo.party.games.mole.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.mole.GameConfig
import com.siwoo.party.games.mole.Kind
import com.siwoo.party.games.mole.MoleModule
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

/** 두더지 잡기 대결 화면: 4손가락 동시 터치, 2인 끝까지, 무승부, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class MoleUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { MoleActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): MoleActivity {
        MoleActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), MoleModule, players)
        return Robolectric.buildActivity(MoleActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(3000) { if (cond()) return; idle(20) }
        error("$what 가 되지 않음")
    }

    private fun now(a: MoleActivity) = a.timers.now()

    /** 구역 zone 의 hole 두더지 위치를 한 손가락으로 톡 */
    private fun tap(a: MoleActivity, zone: Int, hole: Int) {
        val pt = PointF()
        a.board.moleCenter(zone, hole, pt)
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 5, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun ranksOf(a: MoleActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    /** 올라온 두더지가 있는 구멍 (kinds 중, 아직 1/3 이상 남은 것) */
    private fun upHole(a: MoleActivity, vararg kinds: Kind): Int {
        for (h in 0 until GameConfig.HOLES) {
            val s = a.game.activeSpawn(h, now(a)) ?: continue
            if (s.kind in kinds && s.endMs - a.game.elapsed(now(a)) > s.upMs / 3) return h
        }
        return -1
    }

    @Test
    fun fourFingersAtOnceEachHitOnlyTheirOwnZone() {
        val a = launch(four)
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        waitFor("경기 시작") { a.playing }
        var hole = -1
        waitFor("두더지") { hole = upHole(a, Kind.NORMAL, Kind.GOLD); hole >= 0 }
        val s = a.game.activeSpawn(hole, now(a))!!
        // 같은 구멍이 구역마다 다른 화면 위치 (위쪽 구역은 180° 돌림) → 각자 자기 구역의 그 구멍을 동시에
        val order = intArrayOf(3, 0, 1, 2)
        val pts = Array(4) { PointF().also { p -> a.board.moleCenter(order[it], hole, p) } }
        val xs = FloatArray(4) { pts[it].x }
        val ys = FloatArray(4) { pts[it].y }
        for (k in 0 until 4) {
            assertEquals("구역 판정", order[k], com.siwoo.party.common.TouchZones.zoneAt(
                com.siwoo.party.common.TouchZones.layout(4, a.zones.width.toFloat(), a.zones.height.toFloat(), 10f * a.resources.displayMetrics.density), xs[k], ys[k]))
            assertEquals("구멍 판정", hole, a.board.holeAt(order[k], xs[k], ys[k]))
        }
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        assertTrue(a.game.hits[3][s.id])
        for (q in 0..2) assertFalse("아직 안 누른 구역 $q", a.game.hits[q][s.id])
        for (k in 1 until 4) {
            a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN or (k shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        }
        for (z in 0 until 4) {
            assertTrue(a.zones.isPressed(z))
            assertTrue("구역 $z 이 자기 두더지를 잡음", a.game.hits[z][s.id])
            assertEquals("구역 $z 는 딱 한 번", s.kind.points, a.game.score[z])
            assertEquals(1, a.game.caught[z])
        }
        // 한 손가락만 떼고 다시 같은 두더지 → 이미 잡음
        a.zones.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs, ys, t + 10))
        assertFalse(a.zones.isPressed(0))
        assertTrue(a.zones.isPressed(3))
        for (z in 0 until 4) assertEquals(s.kind.points, a.game.score[z])
    }

    @Test
    fun twoPlayersPlayToTheEnd() {
        val a = launch(four.take(2), seed = 8)
        waitFor("경기 시작") { a.playing }
        assertTrue(a.looping)
        // 가람: 일반·황금만 잡음, 나래: 폭탄만 침
        while (a.playing) {
            val h = upHole(a, Kind.NORMAL, Kind.GOLD)
            if (h >= 0 && !a.game.hits[0][a.game.activeSpawn(h, now(a))!!.id]) tap(a, 0, h)
            val b = upHole(a, Kind.BOMB)
            if (b >= 0 && !a.game.hits[1][a.game.activeSpawn(b, now(a))!!.id]) tap(a, 1, b)
            idle(40)
        }
        assertTrue(a.game.isOver(now(a)))
        assertTrue("가람 점수 ${a.game.score[0]}", a.game.score[0] > 10)
        assertTrue("나래 점수 ${a.game.score[1]}", a.game.score[1] < 0)
        assertEquals(0, a.game.caught[1])
        assertTrue(a.game.bombs[1] >= 1)
        assertArrayEquals(intArrayOf(1, 2), ranksOf(a))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        idle(3000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun nobodyHitsEveryoneTied() {
        val a = launch(four.take(3), seed = 2)
        waitFor("경기 시작") { a.playing }
        // 빈 구멍 터치는 빗나감 (점수 그대로)
        tap(a, 2, 0)
        waitFor("경기 끝") { !a.playing }
        assertArrayEquals(intArrayOf(0, 0, 0), a.game.score)
        assertArrayEquals(intArrayOf(1, 1, 1), ranksOf(a))
    }

    @Test
    fun pauseFreezesMolesAndIgnoresTaps() {
        val a = launch(four.take(2), seed = 4)
        waitFor("경기 시작") { a.playing }
        idle(1500)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(50)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.looping)
        val el = a.game.elapsed(now(a))
        idle(40_000)
        assertEquals("일시정지 중 시간 멈춤", el, a.game.elapsed(now(a)))
        assertTrue(a.playing)
        // 일시정지 중 직접 구역 이벤트를 보내도 무시
        for (h in 0 until GameConfig.HOLES) a.onZoneDown(0, 0f, 0f, SystemClock.uptimeMillis())
        assertEquals(0, a.game.misses[0] + a.game.caught[0] + a.game.bombs[0])
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        idle(200)
        assertTrue(a.game.elapsed(now(a)) > el)
    }

    @Test
    fun moduleInfo() {
        assertEquals("mole", MoleModule.id)
        assertEquals(GameCategory.REFLEX, MoleModule.category)
        assertEquals(3, MoleModule.rules.size)
        assertEquals(2..4, MoleModule.playerCounts)
        assertEquals(4, MoleModule.requiredTouches(4))
        assertEquals(30_000L, GameConfig.GAME_MS)
    }
}
