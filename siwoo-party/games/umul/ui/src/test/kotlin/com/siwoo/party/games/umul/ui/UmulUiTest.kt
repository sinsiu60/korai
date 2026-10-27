package com.siwoo.party.games.umul.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.umul.GameConfig
import com.siwoo.party.games.umul.RoundState
import com.siwoo.party.games.umul.UmulBoard.BL
import com.siwoo.party.games.umul.UmulBoard.BR
import com.siwoo.party.games.umul.UmulBoard.C
import com.siwoo.party.games.umul.UmulBoard.TL
import com.siwoo.party.games.umul.UmulBoard.TR
import com.siwoo.party.games.umul.UmulModule
import com.siwoo.party.games.umul.UmulRound
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

/** 우물고누 화면: 말 터치로 3판 2선승 끝까지 → 순위, 첫 수 금지·빈 점 터치·상대 말 터치, 무승부 판, 일시정지, 3인 거절 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class UmulUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))
    private val two = players.take(2)
    private val pt = PointF()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { UmulActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    /** seed 로 1판 먼저 둘 사람을 정해 띄움 (Random(seed).nextInt(2) 와 같은 값) */
    private fun launch(seed: Int): UmulActivity {
        UmulActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), UmulModule, two)
        val a = Robolectric.buildActivity(UmulActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: UmulActivity) = waitFor("내 차례 입력") { a.acceptingInput }

    /** 점 i 를 톡 */
    private fun tap(a: UmulActivity, i: Int) {
        a.board.pointCenter(i, pt)
        val t = SystemClock.uptimeMillis()
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    /** 점 i 를 눌러 한 수 두고 도착까지 기다림 */
    private fun move(a: UmulActivity, i: Int) {
        val r = a.round
        val before = r.moveCount
        tap(a, i)
        assertEquals("점 $i 를 눌러 한 수", before + 1, r.moveCount)
        if (r.state == RoundState.PLAY) waitReady(a) else waitFor("도착") { !a.sliding }
    }

    /** 이번 판을 starter 가 이기게 (7수) 또는 지게 (6수) */
    private fun playRound(a: UmulActivity, starterWins: Boolean) {
        waitReady(a)
        val r = a.round
        val seq = if (r.starter == 0) {
            if (starterWins) intArrayOf(TL, TR, C, TL, TR, C, BL) else intArrayOf(TL, TR, C, TL, TR, BR)
        } else {
            if (starterWins) intArrayOf(TR, TL, C, TR, TL, C, BR) else intArrayOf(TR, TL, C, TR, TL, BL)
        }
        for (p in seq) move(a, p)
        assertEquals(RoundState.WON, r.state)
        assertEquals(if (starterWins) r.starter else 1 - r.starter, r.winner)
    }

    /** 다음 판이 시작될 때까지 */
    private fun waitNextRound(a: UmulActivity, roundsPlayed: Int) =
        waitFor("다음 판") { a.match.roundsPlayed == roundsPlayed && a.acceptingInput }

    private fun ranks(a: UmulActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun fullMatchByTouches_firstMoveRule_twoOne() {
        val a = launch(1)
        val s = a.match.firstStarter
        assertFalse("소개 중엔 못 둠", a.acceptingInput)
        tap(a, if (s == 0) TL else TR)
        waitReady(a)
        val r1 = a.round
        assertEquals("소개 중 터치는 무시", 0, r1.moveCount)
        assertEquals(s, r1.current)
        assertTrue(a.sides[s].isCurrent)
        assertFalse(a.sides[1 - s].isCurrent)
        assertEquals(UmulActivity.HINT_FIRST, a.sides[s].shownHint)
        assertEquals("1판째 · 남은 수 " + GameConfig.MOVE_LIMIT, a.board.shownHeader)

        // 첫 수 금지: 우물 옆 말을 가운데로 → 거절 (흔들기 + 빨간 안내)
        val well = if (s == 0) BL else BR
        tap(a, well)
        assertEquals("금지 수는 안 둠", 0, r1.moveCount)
        assertEquals(UmulActivity.HINT_FORBIDDEN, a.sides[s].shownHint)
        idle(30)
        assertTrue("흔들기 연출", a.board.animating)
        // 상대 말 → 거절
        tap(a, if (s == 0) TR else TL)
        assertEquals(0, r1.moveCount)
        assertEquals(UmulActivity.HINT_NOT_MINE, a.sides[s].shownHint)

        // 빈 점(가운데)을 누르면 옮길 수 있는 유일한 말(위쪽 귀퉁이)이 감
        tap(a, C)
        assertEquals(1, r1.moveCount)
        assertEquals(if (s == 0) TL else TR, r1.lastFrom)
        assertEquals(C, r1.lastTo)
        assertTrue(a.sliding)
        assertTrue(a.board.sliding)
        assertFalse(a.acceptingInput)
        idle(20)
        assertTrue("미끄러지는 연출 중", a.board.animating)
        tap(a, if (s == 0) TR else TL)
        assertEquals("미끄러지는 중엔 무시", 1, r1.moveCount)
        waitReady(a)
        assertFalse(a.board.sliding)
        assertEquals(1 - s, r1.current)
        assertTrue(a.sides[1 - s].isCurrent)
        assertEquals("1판째 · 남은 수 " + (GameConfig.MOVE_LIMIT - 1), a.board.shownHeader)
        // 나머지: 먼저 둔 사람이 7수째에 상대를 가둠
        val rest = if (s == 0) intArrayOf(TR, C, TL, TR, C, BL) else intArrayOf(TL, C, TR, TL, C, BR)
        for (p in rest) move(a, p)
        assertEquals(RoundState.WON, r1.state)
        assertEquals(s, r1.winner)
        assertEquals(UmulActivity.HINT_TRAPPED, a.sides[1 - s].shownHint)
        idle(30)
        assertTrue("갇힌 말 떠는 중", a.board.animating)

        // 2판: 먼저 두는 사람이 바뀜 → 이번엔 2판 먼저 둔 사람(1 − s)이 이김 → 1 : 1
        waitNextRound(a, 1)
        assertEquals(1, a.sides[s].shownWins)
        assertEquals(0, a.sides[1 - s].shownWins)
        assertEquals(1 - s, a.round.starter)
        assertEquals(UmulRound(0).owners.toList(), a.round.owners.toList())
        assertEquals("2판째 · 남은 수 " + GameConfig.MOVE_LIMIT, a.board.shownHeader)
        playRound(a, starterWins = true)

        // 3판: 다시 s 가 먼저 → s 가 짐 → 1 − s 가 2 : 1 로 최종 승리
        waitNextRound(a, 2)
        assertEquals(1, a.sides[0].shownWins)
        assertEquals(1, a.sides[1].shownWins)
        assertEquals(s, a.round.starter)
        playRound(a, starterWins = false)
        val r = ranks(a)
        assertEquals(1, r[1 - s])
        assertEquals(2, r[s])
        assertEquals(2, a.match.wins[1 - s])
        assertEquals(3, a.match.roundsPlayed)
    }

    @Test
    fun twoStraightWinsEndAfterTwoRounds() {
        val a = launch(2)
        val s = a.match.firstStarter
        playRound(a, starterWins = true) // s 승
        waitNextRound(a, 1)
        playRound(a, starterWins = false) // 2판은 1 − s 먼저 → 져서 s 승
        val r = ranks(a)
        assertEquals(1, r[s])
        assertEquals(2, r[1 - s])
        assertEquals(2, a.match.roundsPlayed)
    }

    @Test
    fun moveLimitDrawRoundGivesNoPointAndNextRound() {
        val a = launch(3)
        val s = a.match.firstStarter
        waitReady(a)
        val r1 = a.round
        // 상대를 가두지 않는 수만 두어 수 한도까지
        while (r1.state == RoundState.PLAY) {
            var f = -1
            for (p in 0 until 5) if (r1.canMove(p) && !r1.wouldTrap(p)) { f = p; break }
            assertTrue(f >= 0)
            if (r1.movesLeft == GameConfig.MOVE_WARN) assertTrue(a.board.shownHeader.endsWith(GameConfig.MOVE_WARN.toString()))
            move(a, f)
        }
        assertEquals(RoundState.DRAW, r1.state)
        assertEquals(GameConfig.MOVE_LIMIT, r1.moveCount)
        waitNextRound(a, 1)
        assertArrayEquals("무승부 판은 점수 없음", intArrayOf(0, 0), a.match.wins)
        assertEquals(0, a.sides[0].shownWins)
        assertEquals(1 - s, a.round.starter)
        assertEquals("2판째 · 남은 수 " + GameConfig.MOVE_LIMIT, a.board.shownHeader)
        // 2판·3판: 1 − s 가 연달아 이김 (2판 먼저 = 1 − s 승, 3판 먼저 = s 짐)
        playRound(a, starterWins = true)
        waitNextRound(a, 2)
        playRound(a, starterWins = false)
        val r = ranks(a)
        assertEquals(1, r[1 - s])
        assertEquals(2, r[s])
    }

    @Test
    fun pauseDuringSlideFreezesAndResumeLands() {
        val a = launch(4)
        waitReady(a)
        val s = a.round.starter
        tap(a, if (s == 0) TL else TR)
        idle(40)
        assertTrue(a.sliding)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        idle(3000)
        assertTrue("일시정지 중엔 도착 안 함", a.sliding)
        assertFalse(a.acceptingInput)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        waitReady(a)
        assertFalse(a.sliding)
        assertEquals(1 - s, a.round.current)
        assertEquals(s, a.round.owner(C))
        idle(1000)
        assertFalse("대기 중엔 연출 없음", a.board.animating)
    }

    @Test
    fun threePlayersRejected() {
        assertFalse(UmulModule.supports(3))
        assertFalse(UmulModule.supports(4))
        assertTrue(UmulModule.supports(2))
        assertEquals("2인 전용", UmulModule.unavailableReason(3))
        assertNull(UmulModule.unavailableReason(2))
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), UmulModule, players)
        val a = Robolectric.buildActivity(UmulActivity::class.java, i).setup().get()
        assertTrue("3인은 바로 닫힘", a.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
    }

    @Test
    fun moduleInfo() {
        assertEquals("umul", UmulModule.id)
        assertEquals("우물고누", UmulModule.name)
        assertEquals(3, UmulModule.rules.size)
        assertEquals(2..2, UmulModule.playerCounts)
        assertEquals("3분", UmulModule.minutes)
        assertEquals(PlayMode.TURNS, UmulModule.mode)
        assertEquals(GameCategory.BOARD, UmulModule.category)
    }
}
