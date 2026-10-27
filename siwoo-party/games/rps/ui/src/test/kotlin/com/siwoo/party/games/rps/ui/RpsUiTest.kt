package com.siwoo.party.games.rps.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.rps.Foul
import com.siwoo.party.games.rps.GameConfig
import com.siwoo.party.games.rps.Hand
import com.siwoo.party.games.rps.Phase
import com.siwoo.party.games.rps.RpsModule
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

/** 가위바위보 토너먼트 화면: 4인 끝까지(공동 탈락·무승부·이른 반칙) → 순위, 늦은 입력, 4손가락 동시 터치, 일시정지 후 판 다시 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class RpsUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val pt = PointF()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>): RpsActivity {
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), RpsModule, players)
        return Robolectric.buildActivity(RpsActivity::class.java, i).setup().get()
    }

    private fun idx(h: Hand) = RpsView.HANDS.indexOf(h)

    /** zone 구역의 h 버튼을 누름 (구역 회전 반영) */
    private fun press(a: RpsActivity, zone: Int, h: Hand) {
        a.board.buttonCenter(zone, idx(h), pt)
        val t = SystemClock.uptimeMillis()
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.zones.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    private fun waitFor(what: String, step: Long = 50, cond: () -> Boolean) {
        repeat(3000) { if (cond()) return; idle(step) }
        error("$what 가 되지 않음")
    }

    private fun waitOpen(a: RpsActivity) = waitFor("보!", 20) { a.game.phase == Phase.OPEN }
    private fun waitChant(a: RpsActivity) = waitFor("가위…", 20) { a.game.phase == Phase.CHANT }
    /** 판정이 끝나고 다음 판 준비 / 끝 */
    private fun waitNext(a: RpsActivity, throwNo: Int) = waitFor("다음 판") { a.game.throwNumber > throwNo || a.isFinishing }

    @Test
    fun fourPlayersToTheLastOneStanding() {
        val a = launch(four)
        assertFalse("시작 전엔 터치 무시", a.zones.touchEnabled)
        // 1판: 바위 바위 가위 가위 → 다온·라온 함께 탈락 (공동 3위)
        waitOpen(a)
        assertTrue("보! 동안 막대 루프", a.looping)
        press(a, 0, Hand.ROCK); press(a, 1, Hand.ROCK); press(a, 2, Hand.SCISSORS)
        assertSame(RpsView.Mode.PICKED, a.board.state(0))
        press(a, 3, Hand.SCISSORS)
        assertFalse("모두 내면 바로 판정", a.zones.touchEnabled)
        assertArrayEquals(intArrayOf(0, 0, 3, 3), a.game.rank)
        assertSame(RpsView.Mode.REVEAL, a.board.state(0))
        waitFor("탈락 도장") { a.board.state(2) == RpsView.Mode.OUT }
        assertSame(RpsView.Mode.OUT, a.board.state(3))
        // 2판: 보 보 → 무승부 다시
        waitNext(a, 1)
        waitOpen(a)
        press(a, 2, Hand.ROCK) // 탈락자는 무시
        assertNull(a.game.hand[2])
        press(a, 0, Hand.PAPER); press(a, 1, Hand.PAPER)
        assertEquals(2, a.game.aliveCount)
        // 3판: 가람이 "가위…" 때 누름(이른 반칙), 나래 바위 → 가람 탈락
        waitNext(a, 2)
        waitChant(a)
        press(a, 0, Hand.PAPER)
        assertSame(Foul.EARLY, a.game.foul[0])
        assertSame(RpsView.Mode.FOUL, a.board.state(0))
        waitOpen(a)
        press(a, 0, Hand.SCISSORS) // 반칙자는 무시
        press(a, 1, Hand.ROCK)
        assertTrue(a.game.isOver)
        assertEquals(1, a.game.winner)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1, 2, 3), data.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals(intArrayOf(2, 1, 3, 3), data.getIntArrayExtra(Party.EXTRA_RANKS))
        assertEquals(3, a.game.throwNumber)
        idle(2000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun lateInputLosesAndAllFoulReplays() {
        val a = launch(four.take(2))
        // 1판: 가람 이른 반칙, 나래 안 냄(늦음) → 모두 반칙 → 다시
        waitChant(a)
        press(a, 0, Hand.ROCK)
        waitFor("판정", 20) { a.game.phase == Phase.IDLE }
        assertSame(Foul.LATE, a.game.foul[1])
        assertEquals(2, a.game.aliveCount)
        // 2판: 가람 바로 바위, 나래는 0.6초 지나 보 → 무시, 늦은 반칙 → 나래 탈락
        waitNext(a, 1)
        waitOpen(a)
        press(a, 0, Hand.ROCK)
        idle(GameConfig.WINDOW_MS + 100)
        press(a, 1, Hand.PAPER)
        assertNull("0.6초 뒤 버튼은 무시", a.game.hand[1])
        assertSame(Foul.LATE, a.game.foul[1])
        waitFor("결과") { a.isFinishing }
        assertArrayEquals(intArrayOf(1, 2), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, n: Int, t: Long): MotionEvent {
        val props = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    @Test
    fun fourFingersAtOnceEachHitOwnZoneAndButton() {
        val a = launch(four)
        waitOpen(a)
        // 구역마다 다른 버튼 (위쪽 구역은 180° 돌아간 배치) — 창(루트) 좌표로 4손가락이 하나씩 더해짐 (모두 누른 채)
        val want = arrayOf(Hand.PAPER, Hand.SCISSORS, Hand.ROCK, Hand.PAPER)
        val order = intArrayOf(3, 0, 1, 2)
        val loc = IntArray(2)
        a.zones.getLocationInWindow(loc)
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        for (k in 0 until 4) {
            a.board.buttonCenter(order[k], idx(want[order[k]]), pt)
            xs[k] = pt.x + loc[0]
            ys[k] = pt.y + loc[1]
        }
        val root = a.window.decorView
        val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs, ys, 1, t))
        for (k in 1 until 4) root.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN or (k shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs, ys, k + 1, t + k))
        for (z in 0 until 4) assertTrue("구역 $z 눌림", a.zones.isPressed(z))
        // 각자 자기 구역·자기 버튼만 (세 가지가 다 나와 무승부)
        assertArrayEquals(want, a.game.hand)
        assertEquals(4, a.game.aliveCount)
        assertTrue(a.game.foul.all { it == Foul.NONE })
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_CANCEL, xs, ys, 4, t + 20))
    }

    @Test
    fun hitTestFollowsZoneRotation() {
        val a = launch(four)
        idle(200)
        for (z in 0 until 4) for (k in 0 until 3) {
            a.board.buttonCenter(z, k, pt)
            assertEquals("구역 $z 버튼 $k", k, a.board.hit(z, pt.x, pt.y))
        }
        // 위쪽 구역(180°)은 화면 왼쪽에 보 버튼이 온다 (그 사람 기준 왼쪽이 가위)
        val p0 = PointF(); val p2 = PointF()
        a.board.buttonCenter(0, idx(Hand.SCISSORS), p0)
        a.board.buttonCenter(0, idx(Hand.PAPER), p2)
        assertTrue(p0.x > p2.x)
        // 구역 가운데 위쪽(이름 줄)은 버튼이 아님
        val zz = a.zones.zone(2)!!
        assertEquals(-1, a.board.hit(2, zz.centerX, zz.top + 10f))
    }

    @Test
    fun pauseDuringChantRestartsTheThrow() {
        val a = launch(four.take(3))
        waitChant(a)
        press(a, 0, Hand.ROCK) // 이른 반칙
        assertSame(Foul.EARLY, a.game.foul[0])
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        a.onZoneDown(1, 0f, 0f, SystemClock.uptimeMillis())
        idle(20_000)
        assertEquals("일시정지 중엔 판이 진행되지 않음", Phase.CHANT, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertEquals("멈춘 판은 없던 일", Phase.IDLE, a.game.phase)
        assertTrue("반칙도 지워짐", a.game.foul.all { it == Foul.NONE })
        waitOpen(a)
        press(a, 0, Hand.ROCK); press(a, 1, Hand.ROCK); press(a, 2, Hand.SCISSORS)
        assertArrayEquals(intArrayOf(0, 0, 3), a.game.rank)
    }

    @Test
    fun moduleInfo() {
        assertEquals("rps", RpsModule.id)
        assertEquals(3, RpsModule.rules.size)
        assertSame(GameCategory.REFLEX, RpsModule.category)
        assertEquals(4, RpsModule.needsMultiTouch)
        assertTrue(RpsModule.supports(2) && RpsModule.supports(3) && RpsModule.supports(4))
    }
}
