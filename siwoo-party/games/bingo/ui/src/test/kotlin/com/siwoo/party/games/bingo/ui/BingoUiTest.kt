package com.siwoo.party.games.bingo.ui

import android.app.Activity
import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.bingo.BingoModule
import com.siwoo.party.games.bingo.GameConfig
import com.siwoo.party.games.bingo.Phase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** 빙고 화면: 판 채우기(직접·랜덤)·비밀 지움(정보 누출 없음)·부르기·동시 빙고, 2·3·4인 끝까지 → 순위, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class BingoUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        BingoActivity.randomOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(600) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun launch(players: List<PlayerInfo>, seed: Int = 3): BingoActivity {
        BingoActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(app, BingoModule, players)
        return Robolectric.buildActivity(BingoActivity::class.java, i).setup().get()
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    private fun tapAt(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private val pt = PointF()

    private fun tapCell(a: BingoActivity, cell: Int) {
        val b = a.screen!!.board
        assertTrue("칸 위치", b.cellCenter(cell, pt))
        tapAt(b, pt.x, pt.y)
    }

    /** 넘기기 화면의 버튼 누르기 → 내 판 화면 */
    private fun passHandoff(a: BingoActivity, expect: Int) {
        waitFor("넘기기 화면") { a.handoffPopup != null }
        assertNull("넘기기 단계엔 판 화면 없음", a.screen)
        assertNoBoardInHierarchy(a)
        all(a.handoffPopup!!.content).filterIsInstance<GameButton>().single().performClick()
        idle(100)
        val s = a.screen!!
        assertEquals(expect, s.player)
        assertTrue(s.board.width > 0)
    }

    /** 화면 어디에도 비밀 판(BoardView·판 화면)이 남아 있지 않고, 글자에도 판 숫자 배치가 없음 */
    private fun assertNoBoardInHierarchy(a: BingoActivity, secretText: String = "") {
        val views = all(a.window.decorView)
        assertTrue("판 View 없음", views.none { it is BoardView })
        assertTrue("판 화면 없음", views.none { it.tag == BoardScreen.TAG })
        if (secretText.isNotEmpty()) {
            for (v in views) if (v is TextView) assertFalse("글자에 판 내용 없음", v.text.toString().contains(secretText))
            for (v in views) assertFalse(v.contentDescription?.toString()?.contains(secretText) == true)
        }
    }

    /** 판 채우기 [완료] → 판 내용 지워짐 + 화면에서 사라짐 */
    private fun finishSetupAndCheckWiped(a: BingoActivity) {
        val s = a.screen!!
        val content = s.content
        val board = s.board
        val text = content.text
        assertTrue(text.isNotEmpty())
        assertTrue(content.hasNumbers)
        assertTrue(s.doneButton!!.isEnabled)
        s.doneButton!!.performClick()
        idle(50)
        assertNull(a.screen)
        assertEquals("판 내용 글자 지움", "", content.text)
        assertFalse("판 숫자 지움", content.hasNumbers)
        assertNull("판 View 의 내용 참조도 버림", board.content)
        assertNull("판 View 는 화면에서 떨어짐", board.parent)
        assertNoBoardInHierarchy(a, text)
    }

    /** 랜덤 배치로 판 채우기 */
    private fun setupRandom(a: BingoActivity, p: Int) {
        passHandoff(a, p)
        val s = a.screen!!
        assertFalse("빈 판은 완료 불가", s.doneButton!!.isEnabled)
        s.randomButton!!.performClick()
        val b = a.game.boards[p]
        assertTrue(b.isComplete)
        assertEquals((1..25).toList(), b.numbers().sorted())
        assertEquals(b.numbers().joinToString(",") { it.toString() }, s.content.text.replace('/', ',').replace(' ', ','))
        finishSetupAndCheckWiped(a)
    }

    /** 칸을 0..24 차례로 눌러 판 채우기 (칸 c = 숫자 c+1) */
    private fun setupManual(a: BingoActivity, p: Int) {
        passHandoff(a, p)
        val s = a.screen!!
        val b = a.game.boards[p]
        tapCell(a, 0)
        tapCell(a, 1)
        assertEquals(1, b.numberAt(0))
        assertEquals(2, b.numberAt(1))
        assertEquals("3", s.infoBig.text.toString())
        tapCell(a, 0) // 마지막 숫자가 아님 → 그대로
        assertEquals(1, b.numberAt(0))
        tapCell(a, 1) // 마지막 숫자(2) → 뺌
        assertEquals(0, b.numberAt(1))
        assertEquals(1, b.placedCount)
        assertTrue(s.resetButton!!.isEnabled)
        s.resetButton!!.performClick()
        assertTrue(b.isEmpty)
        for (c in 0 until 25) {
            tapCell(a, c)
            assertEquals(c + 1, b.numberAt(c))
            if (c < 24) assertFalse(s.doneButton!!.isEnabled)
        }
        assertTrue(b.isComplete)
        finishSetupAndCheckWiped(a)
    }

    /** 한 차례: 넘기기 → 이미 부른 숫자는 못 고름 → number 고르고 [부르기] → 판 지워짐 → 모든 판 표시 */
    private fun playTurn(a: BingoActivity, number: Int) {
        val g = a.game
        val p = g.current
        passHandoff(a, p)
        val s = a.screen!!
        val content = s.content
        assertEquals(g.lines(p), content.lines)
        assertFalse("고르기 전엔 [부르기] 꺼짐", s.callButton!!.isEnabled)
        if (g.callCount > 0) {
            val old = g.lastCall
            tapCell(a, g.boards[p].cellOf(old))
            assertEquals("이미 부른 숫자는 못 고름", 0, a.selectedNumber)
            assertFalse(s.callButton!!.isEnabled)
        }
        tapCell(a, g.boards[p].cellOf(number))
        assertEquals(number, a.selectedNumber)
        assertTrue(s.callButton!!.isEnabled)
        val before = g.callCount
        val text = content.text
        s.callButton!!.performClick()
        assertEquals(before + 1, g.callCount)
        assertEquals(number, g.lastCall)
        for (b in g.boards) assertTrue("모든 판에 표시", b.isMarked(b.cellOf(number)))
        assertNull(a.screen)
        assertEquals("", content.text)
        assertNoBoardInHierarchy(a, text)
        // 모두에게: 큰 숫자 + 부른 숫자표 + 줄 수
        assertEquals(number, a.callView.number)
        assertTrue(a.callView.isCalled(number))
        assertTrue(a.countText.text.toString().contains("${g.callCount} / ${GameConfig.CELLS}"))
    }

    /** 끝까지: 아직 안 부른 가장 작은 숫자를 부른다 */
    private fun playToEnd(a: BingoActivity, order: (Int) -> Int = { it }) {
        val g = a.game
        while (g.phase == Phase.PLAY) {
            val n = (1..25).map(order).first { g.canCall(it) }
            playTurn(a, n)
        }
        assertEquals(Phase.OVER, g.phase)
        assertTrue(g.winners.isNotEmpty())
        waitFor("모두의 판 공개") { a.revealView.visibility == View.VISIBLE }
        assertEquals(a.players.size, a.revealView.count)
        waitFor("결과 반환") { shadowOf(a).resultCode == Activity.RESULT_OK }
        val data = shadowOf(a).resultIntent
        assertArrayEquals(IntArray(a.players.size) { a.players[it].id }, data.getIntArrayExtra(Party.EXTRA_IDS))
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(g.ranks(), ranks)
        for (w in g.winners) assertEquals(1, ranks[w])
        for (p in a.players.indices) if (p !in g.winners) assertTrue(ranks[p] > 1)
    }

    @Test
    fun twoPlayersSameBoardsBingoTogetherSharedFirst() {
        val a = launch(four.take(2), seed = 1)
        // 둘 다 칸을 같은 순서로 채움 → 같은 판 → 같은 부르기에서 함께 빙고
        setupManual(a, 0)
        setupManual(a, 1)
        waitFor("첫 차례 넘기기") { a.handoffPopup != null }
        assertEquals(Phase.PLAY, a.game.phase)
        assertEquals(a.game.starter, a.game.current)
        playToEnd(a)
        assertEquals(listOf(0, 1), a.game.winners)
        assertArrayEquals(intArrayOf(1, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
        // 1..15 를 불러 가로 3줄 (부른 사람이 누구든 함께)
        assertEquals(15, a.game.callCount)
    }

    @Test
    fun threePlayersRandomBoardsToEnd() {
        val a = launch(four.take(3), seed = 7)
        for (p in 0 until 3) setupRandom(a, p)
        playToEnd(a)
    }

    @Test
    fun fourPlayersRandomBoardsToEnd() {
        val a = launch(four, seed = 12)
        for (p in 0 until 4) setupRandom(a, p)
        // 큰 숫자부터 부르기
        playToEnd(a) { 26 - it }
        assertTrue(a.game.callCount >= 5)
    }

    @Test
    fun partialFillThenRandomKeepsPlacedNumbers() {
        val a = launch(four.take(2), seed = 4)
        passHandoff(a, 0)
        val s = a.screen!!
        tapCell(a, 12)
        tapCell(a, 0)
        assertEquals("나머지 랜덤", s.randomButton!!.text.toString())
        s.randomButton!!.performClick()
        val b = a.game.boards[0]
        assertTrue(b.isComplete)
        assertEquals(1, b.numberAt(12))
        assertEquals(2, b.numberAt(0))
        assertEquals("랜덤 배치", s.randomButton!!.text.toString())
        finishSetupAndCheckWiped(a)
        // 다음 사람은 빈 판으로 시작 (앞사람 판 내용이 새 화면에 없음)
        passHandoff(a, 1)
        assertTrue(a.game.boards[1].isEmpty)
        assertFalse(a.screen!!.content.hasNumbers)
    }

    @Test
    fun pauseHidesBoardAndBlocksInput() {
        val a = launch(four.take(3), seed = 5)
        passHandoff(a, 0)
        val s = a.screen!!
        assertEquals(View.VISIBLE, s.board.visibility)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        assertEquals("일시정지하면 판 가림", View.INVISIBLE, s.board.visibility)
        s.randomButton!!.performClick()
        assertTrue("일시정지 중 입력 무시", a.game.boards[0].isEmpty)
        @Suppress("DEPRECATION") a.onBackPressed() // 계속하기
        waitFor("계속하기") { !a.gamePaused }
        assertEquals(View.VISIBLE, s.board.visibility)
        s.randomButton!!.performClick()
        assertTrue(a.game.boards[0].isComplete)
        finishSetupAndCheckWiped(a)
        setupRandom(a, 1)
        setupRandom(a, 2)
        // 부르기 화면에서도 일시정지 중엔 [부르기] 무시
        val p = a.game.current
        passHandoff(a, p)
        tapCell(a, a.game.boards[p].cellOf(5))
        assertEquals(5, a.selectedNumber)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(a.gamePaused)
        assertEquals(View.INVISIBLE, a.screen!!.board.visibility)
        a.screen!!.callButton!!.performClick()
        assertEquals(0, a.game.callCount)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        a.screen!!.callButton!!.performClick()
        assertEquals(1, a.game.callCount)
        assertNotNull(a.game.lastResult)
    }
}
