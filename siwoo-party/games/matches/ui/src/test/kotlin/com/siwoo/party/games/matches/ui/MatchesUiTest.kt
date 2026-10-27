package com.siwoo.party.games.matches.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.matches.GameConfig
import com.siwoo.party.games.matches.MatchesModule
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

/** 성냥개비 가져가기 화면: 터치로 고르고 [가져가기] → 2·3·4인 끝까지 → 패배 적은 순 순위, 고르기 제한·일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class MatchesUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    /** 연출 프레임은 idle 로 흘린 시간만큼만 돈다 */
    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int): MatchesActivity =
        Robolectric.buildActivity(MatchesActivity::class.java,
            Party.gameIntent(RuntimeEnvironment.getApplication(), MatchesModule, players.take(n))).setup().get()
            .also { it.beeper.enabled = false }

    private val pt = PointF()

    private fun tap(a: MatchesActivity, i: Int) {
        a.board.matchCenter(i, pt)
        val t = SystemClock.uptimeMillis()
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(600) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: MatchesActivity) = waitFor("터치 받기") { a.acceptingTaps }

    /** 남은 성냥 중 앞에서부터 k 개를 눌러 고르고 [가져가기] */
    private fun take(a: MatchesActivity, k: Int) {
        waitReady(a)
        val picks = (0 until GameConfig.MATCHES).filter { a.pile.isPresent(it) }.take(k)
        for (i in picks) {
            tap(a, i)
            assertTrue("누르면 들림", a.board.isSelectedShown(i))
        }
        assertEquals(k, a.pile.selectedCount)
        assertTrue(a.takeButton.isEnabled)
        val before = a.game.remaining
        a.takeButton.performClick()
        assertEquals(before - k, a.game.remaining)
        for (i in picks) assertFalse("가져가면 판에서 빠짐", a.board.isShown(i))
    }

    /** loser 가 마지막 성냥을 가져가도록 진행 (모두 협력). 라운드 첫 차례를 돌려준다 */
    private fun playRound(a: MatchesActivity, loser: Int): Int {
        waitReady(a)
        val g = a.game
        assertEquals(21, g.remaining)
        assertEquals(21, a.pile.presentCount)
        val starter = g.current
        val round = g.round
        while (!g.roundOver) {
            val k = (1..g.maxTake).first { ok(g.playerCount, g.remaining, g.current, it, loser) }
            take(a, k)
        }
        assertEquals(loser, g.roundLoser[round])
        assertTrue("마지막 성냥은 그 자리에서 탄다", a.board.isBurning)
        return starter
    }

    private fun ok(n: Int, rem: Int, mover: Int, k: Int, loser: Int): Boolean {
        val left = rem - k
        if (left == 0) return mover == loser
        val next = (mover + 1) % n
        return (1..minOf(3, left)).any { ok(n, left, next, it, loser) }
    }

    /** 계획대로 라운드마다 진 사람을 정해 끝까지 → 돌려받은 순위 */
    private fun playGame(n: Int, plan: IntArray): IntArray {
        val a = launch(n)
        assertEquals(n, a.game.rounds)
        assertEquals(n, a.strip.tiles.size)
        val starters = IntArray(n)
        for (r in 0 until n) {
            starters[r] = playRound(a, plan[r])
        }
        assertArrayEquals("라운드마다 첫 차례가 한 칸씩", IntArray(n) { it }, starters)
        assertTrue(a.game.gameOver)
        waitFor("결과로 넘어감") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(IntArray(n) { it }, data.getIntArrayExtra(Party.EXTRA_IDS))
        return data.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun twoPlayersPlayBothRoundsAndReturnRanks() {
        // 가람이 두 번 다 짐 → 나래 1등
        assertArrayEquals(intArrayOf(2, 1), playGame(2, intArrayOf(0, 0)))
    }

    @Test
    fun threePlayersPlayThreeRoundsAndReturnRanks() {
        // 패배 [0, 2, 1]
        assertArrayEquals(intArrayOf(1, 3, 2), playGame(3, intArrayOf(1, 1, 2)))
    }

    @Test
    fun fourPlayersPlayFourRoundsWithTiesAndReturnRanks() {
        // 패배 [2, 0, 0, 2] → 공동 1등 둘, 공동 3등 둘
        assertArrayEquals(intArrayOf(3, 1, 1, 3), playGame(4, intArrayOf(3, 0, 3, 0)))
    }

    @Test
    fun selectingIsLimitedToThreeAndCanBeUndone() {
        val a = launch(3)
        waitReady(a)
        assertFalse("아무것도 안 고르면 버튼 꺼짐", a.takeButton.isEnabled)
        a.takeButton.performClick()
        assertEquals(21, a.game.remaining)
        tap(a, 0); tap(a, 5); tap(a, 9)
        assertEquals(3, a.pile.selectedCount)
        assertEquals("3개 가져가기", a.takeButton.text.toString())
        tap(a, 12) // 네 번째는 거절 (고개 젓기)
        assertFalse(a.board.isSelectedShown(12))
        assertEquals(3, a.pile.selectedCount)
        assertEquals("한 번에 3개까지만!", a.board.hintText)
        tap(a, 5) // 다시 누르면 취소
        assertFalse(a.board.isSelectedShown(5))
        assertEquals(2, a.pile.selectedCount)
        assertEquals("2개 가져가기", a.takeButton.text.toString())
        tap(a, 12)
        assertTrue(a.board.isSelectedShown(12))
        a.takeButton.performClick()
        assertEquals(18, a.game.remaining)
        assertEquals("18", a.board.remainingShown)
        assertFalse("가져간 뒤 날아가는 동안은 잠김", a.acceptingTaps)
        tap(a, 1)
        assertFalse(a.board.isSelectedShown(1))
        waitReady(a)
        assertFalse("성냥이 다 날아감", a.flyLayer.isFlying)
        assertEquals("다음 사람 차례", 1, a.game.current)
        assertEquals("나래", a.turnView.playerName)
        // 가져간 자리는 다시 못 고름
        tap(a, 0)
        assertEquals(0, a.pile.selectedCount)
        // 남은 수가 적으면 그만큼만 고를 수 있다
        while (a.game.remaining > 2) take(a, minOf(3, a.game.remaining - 2))
        waitReady(a)
        assertEquals(2, a.game.maxTake)
        assertEquals("남은 2개 중 1~2개를 누르세요", a.board.hintText)
    }

    @Test
    fun pauseFreezesTheBurningMatchAndRoundChange() {
        val a = launch(2)
        // 1라운드: 가람이 지도록 진행하다가 마지막 성냥이 탈 때 일시정지
        val g = a.game
        waitReady(a)
        while (!g.roundOver) {
            val k = (1..g.maxTake).first { ok(2, g.remaining, g.current, it, 0) }
            take(a, k)
        }
        assertTrue(a.board.isBurning)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingTaps)
        idle(8000)
        assertTrue("일시정지 동안은 다음 라운드로 안 감", g.roundOver)
        assertEquals(0, g.round)
        assertTrue("불꽃도 멈춤", a.board.isBurning)
        assertFalse(a.board.isAnimating)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(PauseMenu.isShowing(a.popups))
        waitReady(a)
        assertEquals(1, g.round)
        assertEquals("2라운드 첫 차례는 나래", 1, g.current)
        assertEquals(1, g.losses[0])
        assertFalse(a.board.isBurning)
        assertEquals(21, a.pile.presentCount)
        assertTrue((0 until 21).all { a.board.isShown(it) })
    }
}
