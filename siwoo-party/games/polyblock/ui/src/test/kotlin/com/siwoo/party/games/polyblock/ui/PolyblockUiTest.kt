package com.siwoo.party.games.polyblock.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.polyblock.Move
import com.siwoo.party.games.polyblock.Pieces
import com.siwoo.party.games.polyblock.PolyGame
import com.siwoo.party.games.polyblock.PolyblockModule
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

/** 조각 배치 대결 화면: 2·3·4인 끝까지 → 순위, 미리보기·회전·놓기 조작, 끌기, 자동 패스, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class PolyblockUiTest {

    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))
    private val pt = PointF()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { PolyblockActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, seed: Int = 1): PolyblockActivity {
        PolyblockActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), PolyblockModule, all.take(n))
        val a = Robolectric.buildActivity(PolyblockActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: PolyblockActivity) = waitFor("입력 가능") { a.acceptingInput }

    private fun touch(v: View, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    private fun tap(v: View, x: Float, y: Float) {
        touch(v, MotionEvent.ACTION_DOWN, x, y)
        touch(v, MotionEvent.ACTION_UP, x, y)
    }

    private fun pick(a: PolyblockActivity, piece: Int) {
        a.tray.slotCenter(piece, pt)
        tap(a.tray, pt.x, pt.y)
    }

    private fun tapCell(a: PolyblockActivity, x: Int, y: Int) {
        a.board.cellCenter(x, y, pt)
        tap(a.board, pt.x, pt.y)
    }

    /** [회전]·[뒤집기] 버튼으로 원하는 모양 만들기 (회전 3번 → 뒤집기 → 회전 3번 이면 8가지 모두) */
    private fun orient(a: PolyblockActivity, piece: Int, target: Int) {
        val shape = Pieces.shape(piece)
        for (k in 0 until 8) {
            if (shape.orientOf(a.transform) == target) return
            if (k == 3) a.flipButton.performClick() else a.rotateButton.performClick()
        }
        assertEquals("모양 맞추기", target, shape.orientOf(a.transform))
    }

    /** 화면 조작으로 한 수 두기: 조각 고르기 → 모양 맞추기 → 판 누르기 → [놓기] */
    private fun play(a: PolyblockActivity, m: Move) {
        val p = a.game.current
        val before = a.game.placedCount[p]
        pick(a, m.piece)
        assertEquals(m.piece, a.selected)
        orient(a, m.piece, m.orient)
        val o = Pieces.shape(m.piece).orients[m.orient]
        tapCell(a, m.x + (o.w - 1) / 2, m.y + (o.h - 1) / 2)
        assertTrue("초록 미리보기", a.board.previewOk)
        assertTrue(a.placeButton.isEnabled)
        a.placeButton.performClick()
        assertEquals(before + 1, a.game.placedCount[p])
        assertEquals(p, a.game.at(m.x + o.xs[0], m.y + o.ys[0]))
        assertFalse("놓은 뒤 잠깐 잠김", a.acceptingInput)
    }

    private fun playToEnd(a: PolyblockActivity): Int {
        var moves = 0
        while (true) {
            waitFor("다음 차례 또는 끝") { a.acceptingInput || a.ended }
            if (a.ended) break
            val m = a.game.findMove(a.game.current)!!
            play(a, m)
            moves++
        }
        return moves
    }

    private fun ranks(a: PolyblockActivity, n: Int): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(IntArray(n) { it }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    private fun fullGame(n: Int, seed: Int) {
        val a = launch(n, seed)
        assertEquals(if (n == 2) 14 else 20, a.game.size)
        val moves = playToEnd(a)
        assertTrue(a.game.over)
        assertTrue("${n}인 수 $moves", moves >= n * 8)
        for (p in 0 until n) assertTrue(a.game.out[p])
        val expected = a.game.ranks()
        val r = ranks(a, n)
        assertArrayEquals(expected, r)
        assertEquals(1, r.minOrNull())
        // 순위 = 남은 칸 오름차순
        for (x in 0 until n) for (y in 0 until n) if (a.game.score(x) < a.game.score(y)) assertTrue(r[x] < r[y])
    }

    @Test fun twoPlayersToEnd() = fullGame(2, 3)

    @Test fun threePlayersToEnd() = fullGame(3, 4)

    @Test fun fourPlayersToEnd() = fullGame(4, 5)

    @Test
    fun previewRotateFlipAndPlace() {
        val a = launch(4, 7)
        assertFalse("소개 중엔 못 둠", a.acceptingInput)
        pick(a, 0)
        assertEquals(-1, a.selected)
        waitReady(a)
        val p = a.game.current
        assertFalse(a.rotateButton.isEnabled)
        assertFalse(a.placeButton.isEnabled)
        // 조각 없이 판 누르기 → 안내만
        tapCell(a, 5, 5)
        assertFalse(a.board.previewShown)
        assertTrue(a.turnView.hint.contains("조각을 고르세요"))
        // L5 고르기 → 모서리 아닌 곳은 빨강
        val l5 = Pieces.ALL.first { it.name == "L5" }
        pick(a, l5.id)
        assertEquals(l5.id, a.selected)
        assertTrue(a.rotateButton.isEnabled)
        tapCell(a, 9, 9)
        assertTrue(a.board.previewShown)
        assertFalse("모서리가 아니라 빨강", a.board.previewOk)
        assertFalse(a.placeButton.isEnabled)
        assertTrue(a.turnView.hint.contains("모서리"))
        a.placeButton.performClick()
        assertEquals(0, a.game.placedCount[p])
        // 회전 4번 = 제자리, 뒤집기 2번 = 제자리
        val t0 = a.transform
        a.rotateButton.performClick()
        assertNotEquals(l5.orientOf(t0), l5.orientOf(a.transform))
        repeat(3) { a.rotateButton.performClick() }
        assertEquals(t0, a.transform)
        a.flipButton.performClick()
        a.flipButton.performClick()
        assertEquals(t0, a.transform)
        // 내 모서리 칸을 누르면 (판 안으로 맞춰져) 초록 → 놓기
        val corner = a.game.corners[p]
        val m = (0 until 8).map { l5.orientOf(it) }.distinct().firstNotNullOf { oi ->
            val o = l5.orients[oi]
            val cx = corner % a.game.size
            val cy = corner / a.game.size
            (0 until o.size).map { k -> Move(l5.id, oi, cx - o.xs[k], cy - o.ys[k]) }.firstOrNull { a.game.canPlace(it.piece, it.orient, it.x, it.y) }
        }
        play(a, m)
        assertEquals(5, a.game.count(p))
        assertFalse(a.game.hasPiece(p, l5.id))
        assertFalse(a.board.previewShown)
        waitReady(a)
        assertNotEquals("다음 사람 차례", p, a.game.current)
        assertEquals(-1, a.selected)
    }

    @Test
    fun dragMovesPreview() {
        val a = launch(2, 2)
        waitReady(a)
        val p = a.game.current
        val one = Pieces.ALL.first { it.name == "1" }.id
        pick(a, one)
        val c = a.game.corners[p]
        val n = a.game.size
        // 판 가운데에서 눌러 모서리까지 끌기
        a.board.cellCenter(6, 6, pt)
        touch(a.board, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        assertTrue(a.board.previewShown)
        assertFalse(a.board.previewOk)
        a.board.cellCenter((6 + c % n) / 2, (6 + c / n) / 2, pt)
        touch(a.board, MotionEvent.ACTION_MOVE, pt.x, pt.y)
        a.board.cellCenter(c % n, c / n, pt)
        touch(a.board, MotionEvent.ACTION_MOVE, pt.x, pt.y)
        assertTrue("끌어서 모서리 → 초록", a.board.previewOk)
        touch(a.board, MotionEvent.ACTION_UP, pt.x, pt.y)
        assertTrue(a.board.previewOk)
        a.placeButton.performClick()
        assertEquals(p, a.game.board[c])
    }

    @Test
    fun autoPassSkipsBlockedPlayer() {
        val a = launch(2, 1)
        waitReady(a)
        val first = a.game.current
        val other = 1 - first
        // 상대 모서리를 미리 막아 둔다 (판 상황 만들기) → 첫 수 뒤 상대는 자동 패스
        a.game.board[a.game.corners[other]] = first
        play(a, a.game.findMove(first)!!)
        waitFor("패스 표시") { a.game.out[other] }
        assertEquals(0.5f, a.strip.tiles[other].alpha, 0.01f)
        waitReady(a)
        assertEquals("패스한 사람은 건너뜀", first, a.game.current)
        val moves = playToEnd(a)
        assertTrue(moves > 5)
        assertEquals(89, a.game.score(other))
        val r = ranks(a, 2)
        assertEquals(1, r[first])
        assertEquals(2, r[other])
    }

    @Test
    fun pauseMenuBlocksInputAndResumes() {
        val a = launch(3, 6)
        waitReady(a)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingInput)
        pick(a, 3)
        assertEquals("일시정지 중엔 못 고름", -1, a.selected)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.acceptingInput)
        pick(a, 3)
        assertEquals(3, a.selected)
    }

    @Test
    fun moduleInfo() {
        assertEquals("polyblock", PolyblockModule.id)
        assertEquals("조각 배치 대결", PolyblockModule.name)
        assertEquals(3, PolyblockModule.rules.size)
        assertEquals(2..4, PolyblockModule.playerCounts)
        assertEquals(GameCategory.BOARD, PolyblockModule.category)
        assertEquals(PlayMode.TURNS, PolyblockModule.mode)
        assertEquals("15~25분", PolyblockModule.minutes)
        assertEquals(0, PolyblockModule.needsMultiTouch)
        assertFalse(PolyblockModule.supports(1))
        assertFalse(PolyblockModule.supports(5))
        assertTrue(PolyGame(2).size < PolyGame(4).size)
    }
}
