package com.siwoo.party.games.gomoku.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.gomoku.GameConfig
import com.siwoo.party.games.gomoku.GomokuGame
import com.siwoo.party.games.gomoku.GomokuModule
import com.siwoo.party.games.gomoku.Phase
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

/** 오목 화면: 두 번 눌러 놓기로 끝까지(승리·무승부) → 순위, 무르기 버튼, 무르기 끔 설정, 일시정지, 3인 거절 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class GomokuUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))
    private val two = players.take(2)
    private val pt = PointF()
    private val n = GameConfig.GOMOKU_SIZE

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { GomokuActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(seed: Int = 1): GomokuActivity {
        GomokuActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), GomokuModule, two)
        return Robolectric.buildActivity(GomokuActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1200) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: GomokuActivity) = waitFor("내 차례 입력") { a.acceptingInput }

    /** 교차점 (x, y) 를 한 번 톡 */
    private fun tap(a: GomokuActivity, x: Int, y: Int) {
        a.board.pointCenter(x, y, pt)
        val t = SystemClock.uptimeMillis()
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    /** 두 번 눌러 놓기 */
    private fun put(a: GomokuActivity, x: Int, y: Int) {
        val before = a.game.moveCount
        tap(a, x, y)
        assertEquals("첫 터치는 미리보기", a.game.index(x, y), a.game.preview)
        assertEquals(before, a.game.moveCount)
        tap(a, x, y)
        assertEquals("두 번째 터치로 놓임", before + 1, a.game.moveCount)
    }

    private fun ranks(a: GomokuActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun blackWinsWithFiveByDoubleTaps() {
        val a = launch(1)
        assertFalse("소개 중엔 못 둠", a.acceptingInput)
        tap(a, 7, 7)
        assertEquals(-1, a.game.preview)
        waitReady(a)
        val black = a.game.first
        val white = 1 - black
        assertEquals(black, a.game.current)
        assertTrue(a.sides[black].isCurrent)
        assertFalse(a.sides[white].isCurrent)
        // 미리보기 옮기기: (0,0) → (3,7) 로 옮긴 뒤 확정
        tap(a, 0, 0)
        assertEquals(a.game.index(0, 0), a.game.preview)
        tap(a, 3, 7)
        assertEquals("다른 곳을 누르면 미리보기 이동", a.game.index(3, 7), a.game.preview)
        assertEquals(0, a.game.moveCount)
        tap(a, 3, 7)
        assertEquals(black, a.game.at(3, 7))
        assertEquals(GomokuGame.EMPTY, a.game.at(0, 0))
        assertTrue("차례 넘어감", a.sides[white].isCurrent)
        // 판 밖(가장자리 너머) 터치는 무시
        val t = SystemClock.uptimeMillis()
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 2f, 2f, 0))
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, 2f, 2f, 0))
        assertEquals(-1, a.game.preview)
        // 돌 있는 곳 터치 무시
        tap(a, 3, 7)
        assertEquals(-1, a.game.preview)
        val whites = intArrayOf(3, 9, 4, 9, 5, 9, 6, 9)
        val blacks = intArrayOf(4, 7, 5, 7, 6, 7, 7, 7)
        for (k in 0 until 4) {
            put(a, whites[k * 2], whites[k * 2 + 1])
            idle(30)
            put(a, blacks[k * 2], blacks[k * 2 + 1])
            idle(30)
        }
        assertSame(Phase.OVER, a.game.phase)
        assertEquals(black, a.game.winner)
        assertEquals(5, a.game.winLength)
        assertFalse(a.acceptingInput)
        idle(50)
        assertTrue("승리 줄 빛나는 중", a.board.animating)
        waitFor("빛나기 끝") { !a.board.animating }
        val r = ranks(a)
        assertEquals(1, r[black])
        assertEquals(2, r[white])
    }

    @Test
    fun fullBoardDrawGivesBothFirst() {
        val a = launch(2)
        waitReady(a)
        val blacks = ArrayList<Int>()
        val whites = ArrayList<Int>()
        // (x + 2y) % 4 < 2 → 흑: 어느 방향으로도 셋 이상 이어지지 않는 꽉 찬 무늬 (흑 113 · 백 112)
        for (y in 0 until n) for (x in 0 until n) (if ((x + 2 * y) % 4 < 2) blacks else whites).add(y * n + x)
        var b = 0
        var w = 0
        var moves = 0
        while (a.game.phase == Phase.PLAY) {
            val i = if (a.game.isBlack(a.game.current)) blacks[b++] else whites[w++]
            tap(a, i % n, i / n)
            tap(a, i % n, i / n)
            moves++
            assertEquals(moves, a.game.moveCount)
            if (moves % 20 == 0) idle(20)
        }
        assertEquals(n * n, moves)
        assertTrue(a.game.isDraw)
        assertFalse(a.sides[0].isCurrent)
        assertFalse(a.sides[1].isCurrent)
        assertArrayEquals(intArrayOf(1, 1), ranks(a))
    }

    @Test
    fun undoButtonOnlyForJustMovedPlayerOnce() {
        val a = launch(3)
        waitReady(a)
        val black = a.game.first
        val white = 1 - black
        assertTrue(a.game.undoEnabled)
        assertEquals(View.INVISIBLE, a.undoButtons[black].visibility)
        assertEquals(View.INVISIBLE, a.undoButtons[white].visibility)
        put(a, 7, 7)
        assertEquals("방금 둔 흑에게 무르기", View.VISIBLE, a.undoButtons[black].visibility)
        assertEquals(View.INVISIBLE, a.undoButtons[white].visibility)
        // 백이 미리보기만 띄운 상태 → 아직 무를 수 있음
        tap(a, 8, 8)
        assertEquals(View.VISIBLE, a.undoButtons[black].visibility)
        a.undoButtons[black].performClick()
        assertEquals(GomokuGame.EMPTY, a.game.at(7, 7))
        assertEquals(-1, a.game.preview)
        assertEquals(black, a.game.current)
        assertTrue(a.sides[black].isCurrent)
        assertEquals(0, a.sides[black].shownUndoLeft)
        assertEquals(1, a.sides[white].shownUndoLeft)
        assertEquals(View.INVISIBLE, a.undoButtons[black].visibility)
        idle(200)
        put(a, 6, 6)
        assertEquals("한 번 썼으면 버튼 없음", View.INVISIBLE, a.undoButtons[black].visibility)
        a.undoButtons[black].performClick()
        assertEquals(black, a.game.at(6, 6))
        put(a, 1, 1)
        assertEquals(View.VISIBLE, a.undoButtons[white].visibility)
        put(a, 2, 2)
        assertEquals("상대가 두면 사라짐", View.INVISIBLE, a.undoButtons[white].visibility)
        a.undoButtons[white].performClick()
        assertEquals(white, a.game.at(1, 1))
        assertEquals(white, a.game.current)
    }

    @Test
    fun undoOffSettingHidesButton() {
        val app = RuntimeEnvironment.getApplication()
        GomokuSettings.setUndo(app, false)
        try {
            val a = launch(4)
            waitReady(a)
            assertFalse(a.game.undoEnabled)
            val first = a.game.first
            put(a, 7, 7)
            assertEquals(View.INVISIBLE, a.undoButtons[first].visibility)
            a.undoButtons[first].performClick()
            assertEquals(first, a.game.at(7, 7))
            assertEquals(0, a.sides[first].shownUndoLeft)
        } finally {
            GomokuSettings.setUndo(app, true)
        }
        assertTrue(GomokuSettings.undo(app))
    }

    @Test
    fun pauseMenuBlocksTapsAndResumes() {
        val a = launch(5)
        waitReady(a)
        tap(a, 7, 7)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingInput)
        tap(a, 7, 7)
        assertEquals("일시정지 중엔 안 놓임", 0, a.game.moveCount)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.acceptingInput)
        tap(a, 7, 7)
        assertEquals(1, a.game.moveCount)
    }

    @Test
    fun threePlayersRejected() {
        assertFalse(GomokuModule.supports(3))
        assertTrue(GomokuModule.supports(2))
        assertEquals("2인 전용", GomokuModule.unavailableReason(3))
        assertNull(GomokuModule.unavailableReason(2))
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), GomokuModule, players)
        val a = Robolectric.buildActivity(GomokuActivity::class.java, i).setup().get()
        assertTrue("3인은 바로 닫힘", a.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
    }

    @Test
    fun moduleInfo() {
        assertEquals("gomoku", GomokuModule.id)
        assertEquals(3, GomokuModule.rules.size)
        assertEquals(2..2, GomokuModule.playerCounts)
        assertEquals(com.siwoo.party.common.ui.GameCategory.CARD_BRAIN, GomokuModule.category)
        assertEquals("무르기 설정", GomokuModule.settingsLabel)
    }
}
