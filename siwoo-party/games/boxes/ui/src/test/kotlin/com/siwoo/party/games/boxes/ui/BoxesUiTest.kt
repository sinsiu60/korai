package com.siwoo.party.games.boxes.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.boxes.BoxesModule
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

/** 땅따먹기 화면: 터치(대기·이동·떼기)로 2인 판을 끝까지 → 칸 수 순위를 허브로, 미리보기·취소·일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class BoxesUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    /** 연출 프레임은 idle 로 흘린 시간만큼만 돈다 (한 번의 idle 에 시계가 크게 건너뛰지 않게) */
    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(ps: List<PlayerInfo>): BoxesActivity =
        Robolectric.buildActivity(BoxesActivity::class.java,
            Party.gameIntent(RuntimeEnvironment.getApplication(), BoxesModule, ps)).setup().get()

    private val pt = PointF()
    private var downTime = 0L

    private fun touch(a: BoxesActivity, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = t
        a.board.dispatchTouchEvent(MotionEvent.obtain(downTime, t, action, x, y, 0))
    }

    /** 선 가운데를 누르고 살짝 움직였다 뗌 */
    private fun drawLine(a: BoxesActivity, line: Int) {
        a.board.lineCenter(line, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y + 3f)
        assertEquals("누르는 동안 미리보기", line, a.board.previewLine)
        touch(a, MotionEvent.ACTION_MOVE, pt.x + 2f, pt.y)
        touch(a, MotionEvent.ACTION_UP, pt.x + 2f, pt.y)
        assertEquals("떼면 미리보기 사라짐", -1, a.board.previewLine)
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(600) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: BoxesActivity) = waitFor("터치 받기") { a.acceptingTaps }

    @Test
    fun twoPlayersDrawEveryLineByTouchToTheEndAndReturnRanks() {
        val a = launch(players.take(2))
        a.beeper.enabled = false
        val g = a.game
        assertEquals("2인 6×6 점", 6, g.grid.dots)
        assertEquals(60, g.grid.lineCount)
        assertEquals(2, a.strip.tiles.size)
        assertEquals(Theme.playerColor(0), a.glow.color)
        waitReady(a)

        var boxesSeen = 0
        for (line in 0 until g.grid.lineCount) {
            waitReady(a)
            val before = g.current
            val scoreBefore = g.scores.sum()
            drawLine(a, line)
            assertTrue("선이 그어짐 $line", g.isDrawn(line))
            assertTrue(a.board.isLineShown(line))
            val gained = g.scores.sum() - scoreBefore
            if (g.gameOver) break
            if (gained > 0) {
                boxesSeen += gained
                assertEquals("칸을 닫으면 한 번 더", before, g.current)
            } else {
                assertEquals("못 닫으면 다음 사람", (before + 1) % 2, g.current)
                assertEquals("테두리는 다음 사람 색", Theme.playerColor(g.current), a.glow.color)
            }
        }
        assertTrue(g.gameOver)
        assertEquals(25, g.scores.sum())
        // 끝: 칸 세기 연출 → 결과
        waitFor("칸 세기") { a.counting }
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1), data.getIntArrayExtra(Party.EXTRA_IDS))
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals("칸 수 순위", Ranking.competition(g.scores.map { it.toLong() }), ranks)
        if (g.scores[0] > g.scores[1]) assertArrayEquals(intArrayOf(1, 2), ranks)
        else if (g.scores[0] < g.scores[1]) assertArrayEquals(intArrayOf(2, 1), ranks)
        // 판의 모든 칸이 색칠되고 칸 세기 번호가 붙음
        for (b in 0 until g.grid.boxCount) {
            assertEquals(g.boxOwner[b], a.board.boxOwnerShown(b))
            assertTrue(a.board.countShown(b) > 0)
        }
    }

    @Test
    fun previewFollowsFingerAndFarReleaseCancels() {
        val a = launch(players.take(3))
        a.beeper.enabled = false
        val g = a.game
        assertEquals("3인 7×7 점", 7, g.grid.dots)
        waitReady(a)
        val b = a.board
        val l1 = g.grid.hLine(2, 2)
        val l2 = g.grid.vLine(4, 5)
        // 누르면 미리보기
        b.lineCenter(l1, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        assertEquals(l1, b.previewLine)
        // 다른 선으로 옮기면 미리보기도 따라감
        b.lineCenter(l2, pt)
        touch(a, MotionEvent.ACTION_MOVE, pt.x, pt.y)
        assertEquals(l2, b.previewLine)
        // 판 밖 먼 곳으로 옮기면 미리보기 없음 → 거기서 떼면 취소
        val farX = b.originX - b.cell * 0.8f
        val farY = b.originY + b.cell * 2.5f
        touch(a, MotionEvent.ACTION_MOVE, farX, farY)
        assertEquals(-1, b.previewLine)
        touch(a, MotionEvent.ACTION_UP, farX, farY)
        assertEquals(0, g.drawnCount)
        assertEquals(0, g.current)
        // 먼 곳을 눌렀다 떼도 아무 일 없음
        touch(a, MotionEvent.ACTION_DOWN, farX, farY)
        assertEquals(-1, b.previewLine)
        touch(a, MotionEvent.ACTION_UP, farX, farY)
        idle(500)
        assertEquals(0, g.drawnCount)
        assertEquals(0, g.current)
        // 이미 그은 선 위는 그 선이 아닌 다른 빈 선을 고름
        drawLine(a, l1)
        assertEquals(1, g.drawnCount)
        waitReady(a)
        b.lineCenter(l1, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        assertTrue(b.previewLine != l1)
        touch(a, MotionEvent.ACTION_CANCEL, pt.x, pt.y)
        assertEquals(-1, b.previewLine)
        assertEquals(1, g.drawnCount)
    }

    @Test
    fun pauseMenuBlocksDrawingAndFourPlayersUseBigBoard() {
        val a = launch(players.take(2))
        a.beeper.enabled = false
        waitReady(a)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingTaps)
        val line = a.game.grid.hLine(0, 0)
        a.board.lineCenter(line, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        assertEquals("일시정지 중 미리보기 없음", -1, a.board.previewLine)
        touch(a, MotionEvent.ACTION_UP, pt.x, pt.y)
        assertEquals(0, a.game.drawnCount)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(PauseMenu.isShowing(a.popups))
        waitReady(a)
        drawLine(a, line)
        assertEquals(1, a.game.drawnCount)

        val b = launch(players)
        assertEquals(4, b.strip.tiles.size)
        assertEquals(7, b.game.grid.dots)
        assertEquals(84, b.game.grid.lineCount)
        waitFor("판 배치") { b.board.cell > 0f }
        waitReady(b)
        assertEquals(Theme.playerColor(0), b.glow.color)
    }
}
