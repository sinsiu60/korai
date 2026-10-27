package com.siwoo.party.games.reversi.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.reversi.EndReason
import com.siwoo.party.games.reversi.Phase
import com.siwoo.party.games.reversi.ReversiGame
import com.siwoo.party.games.reversi.ReversiModule
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

/** 뒤집기 돌 화면: 칸 터치로 끝까지(흑 승·백 승·끝까지 두기) → 순위, 뒤집기 중 입력 막기, 패스 배너, 못 두는 자리, 일시정지, 3인 거절 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class ReversiUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))
    private val two = players.take(2)
    private val pt = PointF()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(): ReversiActivity {
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), ReversiModule, two)
        return Robolectric.buildActivity(ReversiActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1200) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: ReversiActivity) = waitFor("내 차례 입력") { a.acceptingInput }

    /** 칸 (x, y) 를 톡 */
    private fun tap(a: ReversiActivity, x: Int, y: Int) {
        a.board.cellCenter(x, y, pt)
        val t = SystemClock.uptimeMillis()
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    /** 두고 연출(·패스 배너)이 끝날 때까지 */
    private fun move(a: ReversiActivity, x: Int, y: Int) {
        val before = a.game.moveCount
        tap(a, x, y)
        assertEquals("($x,$y) 에 놓임", before + 1, a.game.moveCount)
        if (a.game.phase == Phase.PLAY) waitReady(a) else waitFor("연출 끝") { !a.moving }
    }

    private fun play(a: ReversiActivity, vararg xy: Int) {
        var i = 0
        while (i < xy.size) { move(a, xy[i], xy[i + 1]); i += 2 }
    }

    private fun ranks(a: ReversiActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun blackWipesOutWhiteByTouches() {
        val a = launch()
        assertFalse("소개 중엔 못 둠", a.acceptingInput)
        tap(a, 3, 2)
        assertEquals(0, a.game.moveCount)
        waitReady(a)
        assertEquals(ReversiGame.BLACK, a.game.current)
        assertTrue(a.sides[0].isCurrent)
        assertFalse(a.sides[1].isCurrent)
        assertEquals(4, a.game.legalCount)
        assertEquals(2, a.sides[0].shownCount)
        assertEquals(2, a.sides[1].shownCount)
        // 첫 수: 연출 중엔 입력 막힘, 놓은 돌은 바로 +1 → 뒤집히면 +1 더
        tap(a, 3, 2)
        assertEquals(1, a.game.moveCount)
        assertTrue(a.moving)
        assertFalse(a.acceptingInput)
        assertEquals(3, a.sides[0].shownCount)
        idle(30)
        assertTrue("뒤집기 연출 중", a.board.animating)
        tap(a, 2, 2)
        assertEquals("연출 중엔 무시", 1, a.game.moveCount)
        waitReady(a)
        assertFalse(a.board.animating)
        assertEquals(4, a.sides[0].shownCount)
        assertEquals(1, a.sides[1].shownCount)
        assertTrue(a.sides[1].isCurrent)
        // 가장 짧은 판: 9수 만에 백이 0개
        play(a, 2, 2, 1, 2, 3, 1, 4, 0, 3, 5, 3, 6, 4, 2, 5, 3)
        assertSame(Phase.OVER, a.game.phase)
        assertSame(EndReason.NO_MOVES, a.game.endReason)
        assertEquals(13, a.sides[0].shownCount)
        assertEquals(0, a.sides[1].shownCount)
        assertTrue(a.sides[0].isWinner)
        assertFalse(a.acceptingInput)
        assertArrayEquals(intArrayOf(1, 2), ranks(a))
    }

    @Test
    fun whiteWinsByTouches() {
        val a = launch()
        waitReady(a)
        play(a, 3, 2, 2, 2, 1, 2, 4, 2, 5, 4, 0, 2, 2, 3, 4, 5, 5, 3, 6, 3)
        assertSame(Phase.OVER, a.game.phase)
        assertEquals(ReversiGame.WHITE, a.game.winner)
        assertEquals(0, a.sides[0].shownCount)
        assertEquals(14, a.sides[1].shownCount)
        assertTrue(a.sides[1].isWinner)
        assertArrayEquals(intArrayOf(2, 1), ranks(a))
    }

    @Test
    fun autoPassShowsBannerAndSamePlayerGoesAgain() {
        val a = launch()
        waitReady(a)
        play(a, 3, 2, 2, 2, 1, 2, 1, 1, 5, 4, 0, 2, 0, 0)
        assertEquals(0, a.passBanners)
        // 백의 이 수 뒤 흑은 둘 곳이 없음 → 패스 배너 동안 입력 막힘 → 백 한 번 더
        tap(a, 2, 0)
        assertEquals(8, a.game.moveCount)
        assertEquals(ReversiGame.BLACK, a.game.passed)
        waitFor("패스 배너") { a.passing }
        assertEquals(1, a.passBanners)
        assertFalse(a.acceptingInput)
        assertTrue(a.sides[1].isCurrent)
        assertFalse(a.sides[0].isCurrent)
        waitReady(a)
        assertEquals(ReversiGame.WHITE, a.game.current)
        assertTrue(a.game.legalCount > 0)
    }

    @Test
    fun playToTheEndWithFirstLegalMove() {
        val a = launch()
        waitReady(a)
        val out = IntArray(64)
        var guard = 0
        while (a.game.phase == Phase.PLAY) {
            val c = a.game.legalMoves(a.game.current, out)
            assertTrue(c > 0)
            move(a, out[0] % 8, out[0] / 8)
            assertTrue(++guard <= 60)
            if (a.game.phase == Phase.PLAY) {
                assertEquals(a.game.count(0), a.sides[0].shownCount)
                assertEquals(a.game.count(1), a.sides[1].shownCount)
            }
        }
        val b = a.game.count(0)
        val w = a.game.count(1)
        val r = ranks(a)
        when {
            b > w -> assertArrayEquals(intArrayOf(1, 2), r)
            b < w -> assertArrayEquals(intArrayOf(2, 1), r)
            else -> assertArrayEquals(intArrayOf(1, 1), r)
        }
    }

    @Test
    fun illegalTapIsRejected() {
        val a = launch()
        waitReady(a)
        tap(a, 0, 0)
        assertEquals(0, a.game.moveCount)
        assertEquals("끼울 돌이 없는 자리예요", a.sides[0].shownHint)
        idle(30)
        assertTrue("빨간 X 깜빡임", a.board.animating)
        waitFor("깜빡임 끝") { !a.board.animating }
        tap(a, 3, 3)
        assertEquals(0, a.game.moveCount)
        assertEquals("이미 돌이 있어요", a.sides[0].shownHint)
        assertTrue(a.acceptingInput)
        idle(1000)
        assertFalse("대기 중엔 연출 없음", a.board.animating)
        move(a, 2, 3)
        assertEquals(ReversiGame.BLACK, a.game.at(3, 3))
    }

    @Test
    fun pauseDuringFlipFreezesAndResumeContinues() {
        val a = launch()
        waitReady(a)
        tap(a, 4, 5)
        idle(60)
        assertTrue(a.moving)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        idle(3000)
        assertTrue("일시정지 중엔 연출이 안 끝남", a.moving)
        assertFalse(a.acceptingInput)
        tap(a, 5, 3)
        assertEquals(1, a.game.moveCount)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        waitReady(a)
        assertFalse(a.moving)
        assertEquals(ReversiGame.WHITE, a.game.current)
        assertEquals(4, a.sides[0].shownCount)
        assertEquals(1, a.sides[1].shownCount)
    }

    @Test
    fun threeAndFourPlayersRejected() {
        assertFalse(ReversiModule.supports(3))
        assertFalse(ReversiModule.supports(4))
        assertTrue(ReversiModule.supports(2))
        assertEquals("2인 전용", ReversiModule.unavailableReason(3))
        assertNull(ReversiModule.unavailableReason(2))
        for (k in 3..4) {
            val i = Party.gameIntent(RuntimeEnvironment.getApplication(), ReversiModule, players.take(k))
            val a = Robolectric.buildActivity(ReversiActivity::class.java, i).setup().get()
            assertTrue("${k}인은 바로 닫힘", a.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
        }
    }

    @Test
    fun moduleInfo() {
        assertEquals("reversi", ReversiModule.id)
        assertEquals("뒤집기 돌", ReversiModule.name)
        assertEquals(3, ReversiModule.rules.size)
        assertEquals(2..2, ReversiModule.playerCounts)
        assertEquals("10분", ReversiModule.minutes)
        assertSame(PlayMode.TURNS, ReversiModule.mode)
        assertSame(GameCategory.BOARD, ReversiModule.category)
    }
}
