package com.siwoo.party.games.janggi.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.janggi.EndReason
import com.siwoo.party.games.janggi.Formation
import com.siwoo.party.games.janggi.JanggiGame
import com.siwoo.party.games.janggi.JanggiModule
import com.siwoo.party.games.janggi.Kind
import com.siwoo.party.games.janggi.Phase
import com.siwoo.party.games.janggi.Piece
import com.siwoo.party.games.janggi.Side
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** 장기 화면: 시작 화면(상차림·옵션) → 터치로 두기 → 외통·기권·빅장으로 끝 → 순위, 무르기, 판 회전, 일시정지, 3인 거절 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class JanggiUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))
    private val two = players.take(2)
    private val pt = PointF()
    private val app get() = RuntimeEnvironment.getApplication()
    private val C = Side.CHO
    private val H = Side.HAN

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        JanggiSettings.reset(app)
    }

    @After fun tearDown() {
        JanggiActivity.randomOverride = null
        JanggiActivity.positionOverride = null
        JanggiSettings.reset(app)
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(seed: Int = 1): JanggiActivity {
        JanggiActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(app, JanggiModule, two)
        return Robolectric.buildActivity(JanggiActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1200) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: JanggiActivity) = waitFor("입력 가능") { a.acceptingInput }

    private fun start(a: JanggiActivity) {
        assertNotNull(a.setup)
        a.setup!!.startButton.performClick()
        assertNull(a.setup)
        waitReady(a)
    }

    private fun sq(x: Int, y: Int) = y * 9 + x

    /** 교차점 (x, y) 를 한 번 톡 */
    private fun tap(a: JanggiActivity, x: Int, y: Int) {
        a.board.pointCenter(sq(x, y), pt)
        val t = SystemClock.uptimeMillis()
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    /** 고르고 → 옮기기 → 다음 차례까지 기다림 */
    private fun play(a: JanggiActivity, fx: Int, fy: Int, tx: Int, ty: Int) {
        val before = a.game.moveCount
        tap(a, fx, fy)
        assertEquals("고름", sq(fx, fy), a.board.selectedSquare)
        assertTrue("갈 수 있는 칸", a.board.isTarget(sq(tx, ty)))
        tap(a, tx, ty)
        assertEquals(before + 1, a.game.moveCount)
        if (a.game.phase == Phase.PLAY) waitReady(a)
    }

    private fun ranks(a: JanggiActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    private fun findButton(v: View, text: String): GameButton? {
        if (v is GameButton && v.text == text && v.isShown) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findButton(v.getChildAt(i), text)?.let { return it }
        return null
    }

    @Test
    fun setupScreenFormationsThenOpeningMoves() {
        val a = launch(1)
        val su = a.setup!!
        assertFalse("시작 화면에선 못 둠", a.acceptingInput)
        tap(a, 0, 6)
        assertEquals(-1, a.board.selectedSquare)
        // 기본 상차림 = 마상마상, 초를 상마상마 · 한을 마상상마로
        assertTrue(su.tiles[C][Formation.MSMS.ordinal].picked)
        su.tiles[C][Formation.SMSM.ordinal].performClick()
        su.tiles[H][Formation.MSSM.ordinal].performClick()
        assertTrue(su.tiles[C][Formation.SMSM.ordinal].picked)
        assertFalse(su.tiles[C][Formation.MSMS.ordinal].picked)
        assertEquals("미리보기 판도 바뀜", Piece.code(C, Kind.ELEPHANT), a.game.at(1, 9))
        assertEquals(Formation.SMSM, JanggiSettings.formation(app, C))
        start(a)
        assertEquals(Formation.SMSM, a.game.choForm)
        assertEquals(Formation.MSSM, a.game.hanForm)
        assertEquals(Piece.code(H, Kind.ELEPHANT), a.game.at(6, 0))
        assertEquals(C, a.game.turn)
        assertTrue(a.sides[C].isCurrent)
        assertFalse(a.sides[H].isCurrent)
        // 상대 기물은 못 고름
        tap(a, 0, 3)
        assertEquals(-1, a.board.selectedSquare)
        // 졸 고르기 → 점 둘, 다른 기물로 바꿔 고르기, 빈 곳 = 취소
        tap(a, 0, 6)
        assertEquals(2, a.board.shownTargets)
        tap(a, 4, 8)
        assertEquals(sq(4, 8), a.board.selectedSquare)
        tap(a, 4, 4)
        assertEquals(-1, a.board.selectedSquare)
        // 포는 시작판에서 갈 곳이 없음
        tap(a, 1, 7)
        assertEquals(sq(1, 7), a.board.selectedSquare)
        assertEquals(0, a.board.shownTargets)
        assertEquals("갈 곳이 없는 기물이에요", a.sides[C].shownHint)
        play(a, 0, 6, 0, 5)
        assertEquals(H, a.game.turn)
        assertTrue(a.sides[H].isCurrent)
        assertFalse("판 회전 끔", a.board.flipped)
        play(a, 2, 3, 2, 4)
        assertEquals(C, a.game.turn)
        assertEquals(2, a.game.moveCount)
    }

    @Test
    fun checkmateEndsWithRanks() {
        JanggiActivity.positionOverride = { g ->
            g.clearBoard()
            g.put(3, 9, C, Kind.GENERAL); g.put(4, 0, H, Kind.GENERAL)
            g.put(0, 1, C, Kind.CHARIOT); g.put(1, 5, C, Kind.CHARIOT)
            g.put(8, 3, H, Kind.SOLDIER)
            g.reset(C)
        }
        val a = launch(2)
        start(a)
        val cho = a.choPlayer
        play(a, 1, 5, 1, 0)
        assertSame(Phase.OVER, a.game.phase)
        assertEquals(EndReason.CHECKMATE, a.game.endReason)
        assertFalse(a.acceptingInput)
        val r = ranks(a)
        assertEquals(1, r[cho])
        assertEquals(2, r[1 - cho])
        assertTrue(a.sides[C].isWinner)
    }

    @Test
    fun checkShownThenEscapeThenResign() {
        JanggiActivity.positionOverride = { g ->
            g.clearBoard()
            g.put(3, 9, C, Kind.GENERAL); g.put(4, 1, H, Kind.GENERAL)
            g.put(0, 5, C, Kind.CHARIOT)
            g.put(8, 3, H, Kind.SOLDIER)
            g.reset(C)
        }
        val a = launch(3)
        start(a)
        play(a, 0, 5, 4, 5)
        assertTrue("장군", a.game.inCheck(H))
        assertTrue(a.sides[H].isChecked)
        // 같은 줄로는 못 피함
        tap(a, 4, 1)
        assertFalse(a.board.isTarget(sq(4, 2)))
        assertTrue(a.board.isTarget(sq(3, 1)))
        tap(a, 3, 1)
        waitReady(a)
        assertFalse(a.sides[H].isChecked)
        assertEquals(C, a.game.turn)
        // 초 기권 → 확인 → 한 승리
        assertEquals(View.VISIBLE, a.resignButtons[C].visibility)
        a.resignButtons[C].performClick()
        idle(300)
        findButton(a.popups, "기권")!!.performClick()
        assertEquals(EndReason.RESIGN, a.game.endReason)
        assertEquals(H, a.game.winner)
        val r = ranks(a)
        assertEquals(1, r[1 - a.choPlayer])
        assertEquals(2, r[a.choPlayer])
    }

    @Test
    fun undoOncePerPlayer() {
        val a = launch(4)
        start(a)
        assertEquals(View.INVISIBLE, a.undoButtons[C].visibility)
        play(a, 0, 6, 0, 5)
        assertEquals("방금 둔 초에게 무르기", View.VISIBLE, a.undoButtons[C].visibility)
        assertEquals(View.INVISIBLE, a.undoButtons[H].visibility)
        a.undoButtons[C].performClick()
        waitReady(a)
        assertEquals(Piece.code(C, Kind.SOLDIER), a.game.at(0, 6))
        assertEquals(Piece.EMPTY, a.game.at(0, 5))
        assertEquals(C, a.game.turn)
        assertEquals(0, a.sides[C].shownUndoLeft)
        assertEquals(View.INVISIBLE, a.undoButtons[C].visibility)
        play(a, 0, 6, 0, 5)
        assertEquals("한 번 썼으면 버튼 없음", View.INVISIBLE, a.undoButtons[C].visibility)
        play(a, 0, 3, 0, 4)
        assertEquals(View.VISIBLE, a.undoButtons[H].visibility)
        play(a, 2, 6, 2, 5)
        assertEquals("상대가 두면 사라짐", View.INVISIBLE, a.undoButtons[H].visibility)
        a.undoButtons[H].performClick()
        assertEquals(Piece.code(H, Kind.SOLDIER), a.game.at(0, 4))
    }

    @Test
    fun undoOffOption() {
        val a = launch(5)
        // 시작 화면 옵션 버튼으로 끄기
        a.setup!!.options[0].performClick()
        assertFalse(JanggiSettings.undo(app))
        start(a)
        assertFalse(a.game.undoEnabled)
        play(a, 0, 6, 0, 5)
        assertEquals(View.INVISIBLE, a.undoButtons[C].visibility)
        assertEquals(0, a.sides[C].shownUndoLeft)
    }

    @Test
    fun rotateOptionFlipsBoardEachTurn() {
        JanggiSettings.setRotate(app, true)
        val a = launch(6)
        start(a)
        assertFalse(a.board.flipped)
        // 회전 안 됐을 때 초 졸 (0,6) 은 화면 아래쪽 왼편
        a.board.pointCenter(sq(0, 6), pt)
        val x0 = pt.x
        val y0 = pt.y
        play(a, 0, 6, 0, 5)
        assertTrue("한 차례엔 판이 돌아 한이 아래", a.board.flipped)
        assertFalse(a.board.isRotating)
        a.board.pointCenter(sq(8, 3), pt)
        assertTrue("돌아간 판에서 한 병 (8,3) 은 화면 아래쪽 왼편", pt.x < a.board.width / 2f && pt.y > a.board.height / 2f)
        assertEquals(sq(0, 6), a.board.cellAt(a.board.width - x0, a.board.height - y0))
        play(a, 8, 3, 8, 4)
        assertFalse("다시 초 차례 → 원래대로", a.board.flipped)
        // 무르기하면 판도 되돌아감
        a.undoButtons[H].performClick()
        waitReady(a)
        assertEquals(H, a.game.turn)
        assertTrue(a.board.flipped)
    }

    @Test
    fun bikjangOptionDraw() {
        JanggiSettings.setBikjang(app, true)
        JanggiActivity.positionOverride = { g ->
            g.clearBoard()
            g.put(4, 8, C, Kind.GENERAL); g.put(4, 1, H, Kind.GENERAL)
            g.put(4, 5, C, Kind.CHARIOT)
            g.put(8, 3, H, Kind.SOLDIER)
            g.reset(C)
        }
        val a = launch(7)
        start(a)
        assertTrue(a.game.bikjangRule)
        play(a, 4, 5, 0, 5)
        assertTrue(a.game.bikjangCalled)
        play(a, 8, 3, 8, 4)
        assertTrue(a.game.isDraw)
        assertEquals(EndReason.BIKJANG, a.game.endReason)
        assertArrayEquals(intArrayOf(1, 1), ranks(a))
    }

    @Test
    fun pauseMenuBlocksTapsAndResumes() {
        val a = launch(8)
        start(a)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingInput)
        tap(a, 0, 6)
        assertEquals("일시정지 중엔 못 고름", -1, a.board.selectedSquare)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.acceptingInput)
        play(a, 0, 6, 0, 5)
        assertEquals(1, a.game.moveCount)
    }

    @Test
    fun threePlayersRejected() {
        assertFalse(JanggiModule.supports(3))
        assertTrue(JanggiModule.supports(2))
        assertEquals("2인 전용", JanggiModule.unavailableReason(3))
        val i = Party.gameIntent(app, JanggiModule, players)
        val a = Robolectric.buildActivity(JanggiActivity::class.java, i).setup().get()
        assertTrue("3인은 바로 닫힘", a.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
    }

    @Test
    fun moduleInfo() {
        assertEquals("janggi", JanggiModule.id)
        assertEquals("장기", JanggiModule.name)
        assertEquals(3, JanggiModule.rules.size)
        assertEquals(2..2, JanggiModule.playerCounts)
        assertEquals(GameCategory.BOARD, JanggiModule.category)
        assertEquals(PlayMode.TURNS, JanggiModule.mode)
        assertEquals("20~30분", JanggiModule.minutes)
        assertEquals("장기 설정", JanggiModule.settingsLabel)
        assertEquals(listOf(0, 100, 150, 200), (0 until 4).map { JanggiSettings.nextLimit(listOf(200, 0, 100, 150)[it]) })
        assertTrue(JanggiGame().phase == Phase.PLAY)
    }
}
