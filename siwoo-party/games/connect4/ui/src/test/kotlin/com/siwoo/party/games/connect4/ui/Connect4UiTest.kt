package com.siwoo.party.games.connect4.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.connect4.Connect4Game
import com.siwoo.party.games.connect4.Connect4Module
import com.siwoo.party.games.connect4.GameConfig
import com.siwoo.party.games.connect4.Phase
import org.junit.After
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
import kotlin.random.Random

/** 사목 떨어뜨리기 화면: 열 터치로 끝까지(승리·무승부) → 순위, 떨어지는 동안 입력 막기, 꽉 찬 열 거절, 일시정지, 3인 거절 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class Connect4UiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))
    private val two = players.take(2)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { Connect4Activity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(seed: Int = 1): Connect4Activity {
        Connect4Activity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), Connect4Module, two)
        return Robolectric.buildActivity(Connect4Activity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1200) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: Connect4Activity) = waitFor("내 차례 입력") { a.acceptingInput }

    /** 열 col 을 톡 (판 가운데 높이) */
    private fun tapCol(a: Connect4Activity, col: Int) {
        val x = a.board.columnX(col)
        val y = a.board.height / 2f
        val t = SystemClock.uptimeMillis()
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0))
    }

    /** 떨어뜨리고 착지까지 기다림 */
    private fun drop(a: Connect4Activity, col: Int) {
        val before = a.game.moveCount
        tapCol(a, col)
        assertEquals("열 $col 에 놓임", before + 1, a.game.moveCount)
        if (a.game.phase == Phase.PLAY) waitReady(a) else waitFor("착지") { !a.dropping }
    }

    private fun ranks(a: Connect4Activity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun firstPlayerWinsDiagonalByTouches() {
        val a = launch(1)
        assertFalse("소개 중엔 못 둠", a.acceptingInput)
        tapCol(a, 3)
        assertEquals(0, a.game.moveCount)
        waitReady(a)
        val f = a.game.first
        assertTrue(a.sides[f].isCurrent)
        assertFalse(a.sides[1 - f].isCurrent)
        assertEquals(21, a.sides[f].shownDiscsLeft)
        // 첫 수: 떨어지는 동안 입력 막힘 + 연출 중
        tapCol(a, 0)
        assertEquals(1, a.game.moveCount)
        assertTrue(a.dropping)
        assertTrue(a.board.falling)
        assertFalse(a.acceptingInput)
        idle(30)
        assertTrue("떨어지는 연출 중", a.board.animating)
        tapCol(a, 1)
        assertEquals("떨어지는 중엔 무시", 1, a.game.moveCount)
        waitReady(a)
        assertFalse(a.board.falling)
        assertEquals(20, a.sides[f].shownDiscsLeft)
        assertEquals(1 - f, a.game.current)
        assertTrue(a.sides[1 - f].isCurrent)
        // 대각선 ↗ : (0,0)(1,1)(2,2)(3,3) — 나머지 수는 받침
        for (c in intArrayOf(1, 1, 2, 2, 3, 2, 3, 3, 6, 3)) drop(a, c)
        assertSame(Phase.OVER, a.game.phase)
        assertEquals(f, a.game.winner)
        assertEquals(4, a.game.winLength)
        assertFalse(a.acceptingInput)
        idle(50)
        assertTrue("승리 넷 빛나는 중", a.board.animating)
        val r = ranks(a)
        assertEquals(1, r[f])
        assertEquals(2, r[1 - f])
    }

    @Test
    fun fullBoardDrawGivesBothFirst() {
        val a = launch(2)
        waitReady(a)
        val seq = intArrayOf(
            0, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6,
            1, 0, 3, 2, 5, 4, 0, 6, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6,
        )
        for (c in seq) drop(a, c)
        assertEquals(42, a.game.moveCount)
        assertTrue(a.game.isDraw)
        assertEquals(0, a.sides[0].shownDiscsLeft)
        assertEquals(0, a.sides[1].shownDiscsLeft)
        assertArrayEquals(intArrayOf(1, 1), ranks(a))
    }

    @Test
    fun fullColumnIsRejectedWithShake() {
        val a = launch(3)
        waitReady(a)
        repeat(GameConfig.CONNECT4_ROWS) { drop(a, 2) }
        assertTrue(a.game.isFull(2))
        val cur = a.game.current
        idle(1000)
        assertFalse("대기 중엔 연출 없음", a.board.animating)
        tapCol(a, 2)
        assertEquals("거절", GameConfig.CONNECT4_ROWS, a.game.moveCount)
        assertEquals("차례 그대로", cur, a.game.current)
        assertFalse(a.dropping)
        assertTrue(a.acceptingInput)
        assertEquals("꽉 찬 줄이에요!", a.sides[cur].shownHint)
        idle(30)
        assertTrue("흔들기 연출", a.board.animating)
        waitFor("흔들기 끝") { !a.board.animating }
        // 다른 열은 그대로 됨
        drop(a, 4)
        assertEquals(cur, a.game.at(4, 0))
        assertEquals(1 - cur, a.game.current)
    }

    @Test
    fun pauseDuringFallFreezesAndResumeLands() {
        val a = launch(4)
        waitReady(a)
        tapCol(a, 3)
        idle(60)
        assertTrue(a.dropping)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        idle(3000)
        assertTrue("일시정지 중엔 착지 안 함", a.dropping)
        assertFalse(a.acceptingInput)
        tapCol(a, 4)
        assertEquals(1, a.game.moveCount)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        waitReady(a)
        assertFalse(a.dropping)
        assertEquals(a.game.first, a.game.at(3, 0))
        assertEquals(Connect4Game.EMPTY, a.game.at(4, 0))
    }

    @Test
    fun threePlayersRejected() {
        assertFalse(Connect4Module.supports(3))
        assertTrue(Connect4Module.supports(2))
        assertEquals("2인 전용", Connect4Module.unavailableReason(3))
        assertNull(Connect4Module.unavailableReason(2))
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), Connect4Module, players)
        val a = Robolectric.buildActivity(Connect4Activity::class.java, i).setup().get()
        assertTrue("3인은 바로 닫힘", a.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
    }

    @Test
    fun moduleInfo() {
        assertEquals("connect4", Connect4Module.id)
        assertEquals(3, Connect4Module.rules.size)
        assertEquals(2..2, Connect4Module.playerCounts)
        assertEquals("5분", Connect4Module.minutes)
        assertEquals(GameCategory.CARD_BRAIN, Connect4Module.category)
    }
}
