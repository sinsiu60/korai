package com.siwoo.party.games.stack.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.stack.DropKind
import com.siwoo.party.games.stack.GameConfig
import com.siwoo.party.games.stack.Phase
import com.siwoo.party.games.stack.StackModule
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
import kotlin.math.abs
import kotlin.random.Random

/** 탑 쌓기 화면: 터치 타이밍으로 3라운드 끝까지 → 순위, 잘림·퍼펙트 반영, 루프 정지, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class StackUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { StackActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, seed: Int = 1): StackActivity {
        StackActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), StackModule, players.take(n))
        return Robolectric.buildActivity(StackActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun tap(a: StackActivity) {
        val t = SystemClock.uptimeMillis()
        a.tower.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, a.tower.width / 2f, a.tower.height / 2f, 0))
        a.tower.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, a.tower.width / 2f, a.tower.height / 2f, 0))
    }

    /** 블록 위치가 조건을 만족할 때까지 한 프레임씩 흘리고 터치 */
    private fun tapWhen(a: StackActivity, what: String, cond: (x: Float) -> Boolean) {
        waitFor("터치 가능") { a.acceptingInput }
        repeat(4000) {
            if (cond(a.game.blockX(a.timers.now()))) { tap(a); return }
            idle(16)
        }
        error("$what 위치가 안 옴")
    }

    private fun perfect(a: StackActivity) {
        val tx = a.game.top.x
        tapWhen(a, "퍼펙트") { abs(it - tx) <= GameConfig.STACK_PERFECT_TOL }
    }

    private fun miss(a: StackActivity) {
        val top = a.game.top
        val w = a.game.width
        tapWhen(a, "빗나감") { it + w < top.x - 0.5f || it > top.right + 0.5f }
    }

    private fun waitLanded(a: StackActivity, placed: Int) = waitFor("놓임") { a.game.placedTotal >= placed && a.tower.fallingPieces == 0 }

    @Test
    fun playThreeRoundsToTheEnd() {
        val a = launch(3)
        assertFalse("소개 중엔 못 누름", a.acceptingInput)
        val g = a.game
        val expected = IntArray(3)
        var placed = 0
        for (round in 1..GameConfig.STACK_ROUNDS) {
            waitFor("라운드 $round") { a.acceptingInput && g.round == round }
            assertEquals(0, g.height)
            // 라운드마다 (round + 1)개 성공한 뒤 빗나감
            repeat(round + 1) { k ->
                val who = g.current
                assertEquals("차례 카드", players[who].name, a.turn.shownName)
                perfect(a)
                placed++
                waitFor("다음 차례") { a.acceptingInput }
                assertEquals("이번 라운드 높이", k + 1, g.height)
                assertEquals((who + 1) % 3, g.current)
            }
            val loser = g.current
            miss(a)
            expected[loser]++
            assertArrayEquals(expected, g.losses)
            assertFalse(a.acceptingInput)
            if (round < GameConfig.STACK_ROUNDS) {
                waitFor("다음 라운드") { g.round == round + 1 }
                assertEquals("진 사람 다음부터", (loser + 1) % 3, g.current)
            }
        }
        assertSame(Phase.OVER, g.phase)
        assertEquals(placed, g.perfects)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(g.ranks(), ranks)
        // 기대 순위 직접 계산
        val exp = IntArray(3) { i -> 1 + expected.count { it < expected[i] } }
        assertArrayEquals(exp, ranks)
    }

    @Test
    fun offsetDropCutsAndShrinks() {
        val a = launch(2)
        val g = a.game
        waitFor("시작") { a.acceptingInput }
        val tx = g.top.x
        val w0 = g.width
        // 받침보다 오른쪽으로 7~10칸 어긋나게
        tapWhen(a, "어긋남") { it - tx in 7f..10f }
        val shift = g.top.x - tx
        assertTrue("잘린 폭만큼 줄어듦", g.width < w0 - 6f)
        assertEquals(w0 - shift, g.width, 0.01f)
        assertTrue("잘린 조각이 떨어짐", a.looping)
        waitFor("조각 떨어짐") { a.tower.fallingPieces > 0 }
        waitLanded(a, 1)
        waitFor("다음 차례") { a.acceptingInput }
        assertEquals(1, g.height)
        assertEquals(0, g.perfects)
    }

    @Test
    fun loopStopsWhenIdleAndPauseFreezesBlock() {
        val a = launch(2)
        val g = a.game
        waitFor("시작") { a.acceptingInput }
        assertTrue("움직이는 동안 루프", a.looping)
        idle(300)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val x = g.blockX(a.timers.now())
        val f = a.frames
        idle(3000)
        assertEquals("일시정지 중엔 블록이 멈춤", x, g.blockX(a.timers.now()), 0f)
        assertEquals(f, a.frames)
        tap(a)
        assertEquals("일시정지 중 터치 무시", 0, g.placedTotal)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 다시 움직임", a.looping)
        assertTrue(a.acceptingInput)
        // 빗나감 → 조각이 다 떨어지면 배너 대기 중엔 루프가 멈춤
        miss(a)
        assertSame(Phase.ROUND_OVER, g.phase)
        waitFor("루프 정지") { !a.looping }
        val f1 = a.frames
        idle(500)
        assertEquals("배너 대기 중엔 프레임 없음", f1, a.frames)
        assertSame(Phase.ROUND_OVER, g.phase)
        waitFor("다음 라운드") { a.acceptingInput && g.round == 2 }
        assertTrue(a.looping)
    }

    @Test
    fun fourPlayersShowSkulls() {
        val a = launch(4, seed = 3)
        val g = a.game
        waitFor("시작") { a.acceptingInput }
        val first = g.current
        miss(a)
        assertEquals(1, g.losses[first])
        waitFor("다음 라운드") { a.acceptingInput && g.round == 2 }
        assertEquals((first + 1) % 4, g.current)
    }

    @Test
    fun moduleInfo() {
        assertEquals("stack", StackModule.id)
        assertEquals(3, StackModule.rules.size)
        assertSame(GameCategory.TIMING, StackModule.category)
        assertSame(PlayMode.TURNS, StackModule.mode)
        assertEquals(2..4, StackModule.playerCounts)
        assertTrue(DropKind.values().size == 3)
    }
}
