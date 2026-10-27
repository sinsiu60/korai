package com.siwoo.party.games.airhockey.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.airhockey.AirHockeyModule
import com.siwoo.party.games.airhockey.GameConfig
import com.siwoo.party.games.airhockey.Phase
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

/** 에어하키 화면: 두 손가락 동시 조작(포인터별), 끝까지 진행 → 순위, 골 뒤 쉬는 동안 루프 멈춤, 일시정지, 3인 거절 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class AirHockeyUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))
    private val two = players.take(2)
    private val pt = PointF()
    private val midY = GameConfig.TABLE_H / 2f

    /** 프레임(postOnAnimation)은 idle 로 흘린 시간만큼만 돈다 */
    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    /** 시간을 16ms 씩 흘린다 (한 번에 크게 흘리면 프레임이 한 번만 돌아 최대 프레임 시간으로 잘린다) */
    private fun idle(ms: Long) {
        var left = ms
        while (left > 0) {
            val d = minOf(16L, left)
            ShadowLooper.idleMainLooper(d, TimeUnit.MILLISECONDS)
            left -= d
        }
    }

    private fun launch(): AirHockeyActivity {
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), AirHockeyModule, two)
        return Robolectric.buildActivity(AirHockeyActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(16) }
        error("$what 가 되지 않음")
    }

    private fun waitPlay(a: AirHockeyActivity) = waitFor("경기 중") { a.game.phase == Phase.PLAY && a.looping }

    // ── 여러 손가락 MotionEvent ──

    /** 손가락들 (ids[k] 포인터가 판 단위 (xs[k], ys[k])) */
    private fun ev(a: AirHockeyActivity, action: Int, ids: IntArray, xs: FloatArray, ys: FloatArray): MotionEvent {
        val n = ids.size
        val props = Array(n) { MotionEvent.PointerProperties().apply { id = ids[it]; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(n) {
            a.table.toScreen(xs[it], ys[it], pt)
            val px = pt.x
            val py = pt.y
            MotionEvent.PointerCoords().apply { x = px; y = py; pressure = 1f; size = 1f }
        }
        val t = SystemClock.uptimeMillis()
        return MotionEvent.obtain(t, t, action, n, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun send(a: AirHockeyActivity, action: Int, ids: IntArray, xs: FloatArray, ys: FloatArray) {
        a.table.dispatchTouchEvent(ev(a, action, ids, xs, ys))
    }

    private fun pointerAction(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    /** 한 손가락으로 (x0,y0) → (x1,y1) 끌고 놓기 (steps 번 나눠, 사이사이 시간 흘림) */
    private fun drag(a: AirHockeyActivity, x0: Float, y0: Float, x1: Float, y1: Float, steps: Int = 8, stepMs: Long = 16) {
        val id = intArrayOf(0)
        send(a, MotionEvent.ACTION_DOWN, id, floatArrayOf(x0), floatArrayOf(y0))
        for (k in 1..steps) {
            val f = k.toFloat() / steps
            send(a, MotionEvent.ACTION_MOVE, id, floatArrayOf(x0 + (x1 - x0) * f), floatArrayOf(y0 + (y1 - y0) * f))
            idle(stepMs)
        }
        idle(300)
        send(a, MotionEvent.ACTION_UP, id, floatArrayOf(x1), floatArrayOf(y1))
    }

    @Test
    fun twoFingersEachDriveOnlyTheirOwnMallet() {
        val a = launch()
        assertFalse("시작 전엔 루프 없음", a.looping)
        waitPlay(a)
        val g = a.game
        val w = GameConfig.TABLE_W
        // 왼쪽 손가락(id 0) 누름 → 오른쪽 손가락(id 1) 더함
        send(a, MotionEvent.ACTION_DOWN, intArrayOf(0), floatArrayOf(4f), floatArrayOf(midY))
        send(a, pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), intArrayOf(0, 1), floatArrayOf(4f, w - 4f), floatArrayOf(midY, midY))
        assertEquals(0, a.table.pointerOf(0))
        assertEquals(1, a.table.pointerOf(1))
        // 둘 다 동시에 끌기: 왼쪽은 위로, 오른쪽은 아래로
        for (k in 1..10) {
            send(a, MotionEvent.ACTION_MOVE, intArrayOf(0, 1), floatArrayOf(4f + k * 0.5f, w - 4f - k * 0.3f), floatArrayOf(midY - k * 0.5f, midY + k * 0.5f))
            idle(16)
        }
        assertEquals(9f, g.targetX[0], 0.05f)
        assertEquals(midY - 5f, g.targetY[0], 0.05f)
        assertEquals(w - 7f, g.targetX[1], 0.05f)
        assertEquals(midY + 5f, g.targetY[1], 0.05f)
        idle(400)
        assertEquals("왼쪽 말렛이 왼쪽 손가락을 따라감", 9f, g.mallets[0].x, 0.05f)
        assertEquals(midY - 5f, g.mallets[0].y, 0.05f)
        assertEquals("오른쪽 말렛이 오른쪽 손가락을 따라감", w - 7f, g.mallets[1].x, 0.05f)
        assertEquals(midY + 5f, g.mallets[1].y, 0.05f)

        // 같은 반쪽(왼쪽)의 세 번째 손가락은 무시
        send(a, pointerAction(MotionEvent.ACTION_POINTER_DOWN, 2), intArrayOf(0, 1, 2), floatArrayOf(9f, w - 7f, 3f), floatArrayOf(midY - 5f, midY + 5f, 15f))
        send(a, MotionEvent.ACTION_MOVE, intArrayOf(0, 1, 2), floatArrayOf(9f, w - 7f, 2f), floatArrayOf(midY - 5f, midY + 5f, 16f))
        assertEquals(0, a.table.pointerOf(0))
        assertEquals(9f, g.targetX[0], 0.05f)
        assertEquals(midY - 5f, g.targetY[0], 0.05f)
        send(a, pointerAction(MotionEvent.ACTION_POINTER_UP, 2), intArrayOf(0, 1, 2), floatArrayOf(9f, w - 7f, 2f), floatArrayOf(midY - 5f, midY + 5f, 16f))
        assertEquals("남는 손가락을 떼도 주인은 그대로", 0, a.table.pointerOf(0))

        // 왼쪽 손가락이 가운데 선을 넘어가면 말렛은 선에서 멈춤
        send(a, MotionEvent.ACTION_MOVE, intArrayOf(0, 1), floatArrayOf(w - 2f, w - 7f), floatArrayOf(midY - 5f, midY + 5f))
        idle(400)
        assertEquals(GameConfig.CENTER_X - GameConfig.MALLET_R, g.mallets[0].x, 0.01f)
        assertEquals("오른쪽 말렛은 그대로", w - 7f, g.mallets[1].x, 0.05f)

        // 왼쪽 손가락(index 0, id 0)만 뗌 → 오른쪽은 계속 조작
        send(a, pointerAction(MotionEvent.ACTION_POINTER_UP, 0), intArrayOf(0, 1), floatArrayOf(w - 2f, w - 7f), floatArrayOf(midY - 5f, midY + 5f))
        assertEquals(-1, a.table.pointerOf(0))
        assertEquals(1, a.table.pointerOf(1))
        val lx = g.mallets[0].x
        val ly = g.mallets[0].y
        for (k in 1..6) {
            send(a, MotionEvent.ACTION_MOVE, intArrayOf(1), floatArrayOf(w - 7f + k * 0.5f), floatArrayOf(midY + 5f - k))
            idle(16)
        }
        idle(300)
        assertEquals(w - 4f, g.mallets[1].x, 0.05f)
        assertEquals(midY - 1f, g.mallets[1].y, 0.05f)
        assertEquals("뗀 왼쪽 말렛은 그대로", lx, g.mallets[0].x, 0f)
        assertEquals(ly, g.mallets[0].y, 0f)
        send(a, MotionEvent.ACTION_UP, intArrayOf(1), floatArrayOf(w - 4f), floatArrayOf(midY - 1f))
        assertEquals(-1, a.table.pointerOf(1))

        // 새 손가락은 오른쪽 반에서 누르면 오른쪽 말렛 (id 가 0 이어도)
        send(a, MotionEvent.ACTION_DOWN, intArrayOf(0), floatArrayOf(w - 3f), floatArrayOf(4f))
        assertEquals(-1, a.table.pointerOf(0))
        assertEquals(0, a.table.pointerOf(1))
        assertEquals(w - 3f, g.targetX[1], 0.05f)
        send(a, MotionEvent.ACTION_UP, intArrayOf(0), floatArrayOf(w - 3f), floatArrayOf(4f))
    }

    @Test
    fun playToSevenThenRanks() {
        val a = launch()
        waitPlay(a)
        val g = a.game
        val w = GameConfig.TABLE_W
        // 오른쪽 말렛을 위 구석으로 치움
        drag(a, w - 4f, midY, w - 2f, 2f)
        assertEquals(w - 2f, g.mallets[1].x, 0.05f)
        // 왼쪽 말렛으로 가운데 퍽을 오른쪽으로 세게 침 (손가락은 선을 넘어가도 말렛은 선에서 멈춤)
        drag(a, 4f, midY, w / 2f + 3f, midY, steps = 6, stepMs = 16)
        waitFor("첫 골") { g.score[0] == 1 }
        assertTrue("골 연출 중엔 물리 루프 멈춤", !a.looping)
        assertSame(Phase.GOAL, g.phase)
        val f0 = a.frames
        idle(200)
        assertEquals("쉬는 동안 프레임 없음", f0, a.frames)
        // 득점당한 오른쪽에서 퍽 재시작 (멈춘 채)
        waitPlay(a)
        assertEquals(GameConfig.CENTER_X + GameConfig.SERVE_OFFSET, g.puck.x, 0.01f)
        assertEquals(midY, g.puck.y, 0.01f)
        assertFalse(g.puck.moving)
        // 왼쪽 말렛도 구석으로 치움
        drag(a, g.mallets[0].x, g.mallets[0].y, 2f, 2f)
        // 이제 오른쪽(나래)이 7점: 퍽을 왼쪽 골문으로 보냄 (테스트 훅)
        var guard = 0
        while (g.phase != Phase.OVER && guard < 20) {
            waitPlay(a)
            g.puck.place(g.puck.x, midY)
            g.puck.setVelocity(-30f, 0f)
            val before = g.score[1]
            waitFor("골 ${before + 1}") { g.score[1] == before + 1 }
            guard++
        }
        assertSame(Phase.OVER, g.phase)
        assertArrayEquals(intArrayOf(1, GameConfig.WIN_SCORE), g.score)
        assertEquals(1, g.winner)
        assertFalse(a.looping)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(2, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        val f = a.frames
        idle(2000)
        assertEquals("끝나면 프레임 루프 완전히 멈춤", f, a.frames)
    }

    @Test
    fun leftPlayerWinsSevenNil() {
        val a = launch()
        waitPlay(a)
        val g = a.game
        drag(a, GameConfig.TABLE_W - 4f, midY, GameConfig.TABLE_W - 2f, 2f)
        drag(a, 4f, midY, 2f, 2f)
        while (g.phase != Phase.OVER) {
            waitPlay(a)
            g.puck.setVelocity(30f, 0f)
            val before = g.score[0]
            waitFor("골") { g.score[0] == before + 1 }
            // 매번 득점당한 오른쪽에서 다시 놓임
            if (g.phase == Phase.GOAL) {
                waitPlay(a)
                assertTrue(g.puck.x > GameConfig.CENTER_X)
            }
        }
        waitFor("결과") { a.isFinishing }
        assertArrayEquals(intArrayOf(1, 2), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun pauseStopsLoopAndResumeContinues() {
        val a = launch()
        waitPlay(a)
        val g = a.game
        g.puck.setVelocity(6f, 4f)
        idle(200)
        assertTrue(a.looping)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val x = g.puck.x
        val y = g.puck.y
        val f = a.frames
        idle(3000)
        assertEquals("일시정지 중엔 물리가 멈춤", x, g.puck.x, 0f)
        assertEquals(y, g.puck.y, 0f)
        assertEquals("다음 프레임이 예약되지 않음", f, a.frames)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        idle(300)
        assertTrue(a.frames > f)
        assertTrue("퍽이 다시 움직임", g.puck.x != x || g.puck.y != y)
    }

    @Test
    fun pauseDuringGoalBreakHoldsRestart() {
        val a = launch()
        waitPlay(a)
        val g = a.game
        drag(a, GameConfig.TABLE_W - 4f, midY, GameConfig.TABLE_W - 2f, 2f)
        g.puck.setVelocity(30f, 0f)
        waitFor("골") { g.phase == Phase.GOAL }
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        idle(GameConfig.GOAL_PAUSE_MS * 3)
        assertSame("일시정지 중엔 재시작 안 함", Phase.GOAL, g.phase)
        assertFalse(a.looping)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitPlay(a)
        assertEquals(GameConfig.CENTER_X + GameConfig.SERVE_OFFSET, g.puck.x, 0.01f)
    }

    @Test
    fun threePlayersRejected() {
        assertFalse(AirHockeyModule.supports(3))
        assertTrue(AirHockeyModule.supports(2))
        assertEquals("2인 전용", AirHockeyModule.unavailableReason(3))
        assertNull(AirHockeyModule.unavailableReason(2))
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), AirHockeyModule, players)
        val a = Robolectric.buildActivity(AirHockeyActivity::class.java, i).setup().get()
        assertTrue("3인은 바로 닫힘", a.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
    }

    @Test
    fun moduleInfo() {
        assertEquals("airhockey", AirHockeyModule.id)
        assertEquals("에어하키", AirHockeyModule.name)
        assertEquals(3, AirHockeyModule.rules.size)
        assertEquals("3분", AirHockeyModule.minutes)
        assertEquals(2..2, AirHockeyModule.playerCounts)
        assertSame(PlayMode.SIMULTANEOUS, AirHockeyModule.mode)
        assertSame(GameCategory.REFLEX, AirHockeyModule.category)
        assertEquals(2, AirHockeyModule.needsMultiTouch)
        assertEquals(2, AirHockeyModule.requiredTouches(2))
    }
}
