package com.siwoo.party.games.marbles.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.marbles.GameConfig
import com.siwoo.party.games.marbles.MarbleState
import com.siwoo.party.games.marbles.MarblesGame
import com.siwoo.party.games.marbles.MarblesModule
import com.siwoo.party.games.marbles.Phase
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
import kotlin.math.sqrt
import kotlin.random.Random

/** 구슬치기 화면: 2·3·4인 끝까지 → 순위, 놓기(링 밖으로 당김), 멈추면 루프 정지, 일시정지, 한 번 더 배너 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class MarblesUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))
    private val pt = PointF()

    /** 프레임(postOnAnimation)은 idle 로 흘린 시간만큼만 돈다 (TugUiTest 와 같은 방식) */
    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { MarblesActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, seed: Int = 1): MarblesActivity {
        MarblesActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), MarblesModule, players.take(n))
        return Robolectric.buildActivity(MarblesActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun touch(a: MarblesActivity, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.field.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    /** 마당 단위 (ux, uy) 를 눌렀다 뗌 → 내 구슬 놓기 */
    private fun tapPlace(a: MarblesActivity, ux: Float, uy: Float) {
        a.field.toScreen(ux, uy, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        touch(a, MotionEvent.ACTION_UP, pt.x, pt.y)
    }

    /** 내 구슬을 눌러 (tx, ty) 반대쪽으로 power 만큼 끌었다 놓기 → (tx, ty) 쪽으로 쏨 */
    private fun shootToward(a: MarblesActivity, tx: Float, ty: Float, power: Float) {
        val s = a.game.shooter
        val dx = tx - s.x
        val dy = ty - s.y
        val len = sqrt(dx * dx + dy * dy)
        val pull = GameConfig.MAX_DRAG * power * a.field.unitPx
        a.field.toScreen(s.x, s.y, pt)
        val x0 = pt.x
        val y0 = pt.y
        val fx = x0 - dx / len * pull
        val fy = y0 - dy / len * pull
        touch(a, MotionEvent.ACTION_DOWN, x0, y0)
        touch(a, MotionEvent.ACTION_MOVE, (x0 + fx) / 2f, (y0 + fy) / 2f)
        touch(a, MotionEvent.ACTION_MOVE, fx, fy)
        touch(a, MotionEvent.ACTION_UP, fx, fy)
    }

    /** 한 차례: 링 가장자리에 가장 가까운 구슬 쪽 링 밖에 놓고 그 구슬로 세게 */
    private fun playTurn(a: MarblesActivity, power: Float) {
        val g = a.game
        var best = g.targets.first { it.inPlay }
        var bd = -1f
        for (t in g.targets) if (t.inPlay) {
            val d = (t.x - GameConfig.RING_X) * (t.x - GameConfig.RING_X) + (t.y - GameConfig.RING_Y) * (t.y - GameConfig.RING_Y)
            if (d > bd) { bd = d; best = t }
        }
        var ux = best.x - GameConfig.RING_X
        var uy = best.y - GameConfig.RING_Y
        val l = sqrt(ux * ux + uy * uy)
        if (l < 1e-3f) { ux = 0f; uy = 1f } else { ux /= l; uy /= l }
        tapPlace(a, GameConfig.RING_X + ux * (GameConfig.PLACE_MIN_DIST + 0.5f), GameConfig.RING_Y + uy * (GameConfig.PLACE_MIN_DIST + 0.5f))
        assertTrue("놓임", g.shooterPlaced)
        assertTrue(MarblesGame.isPlaceable(g.shooter.x, g.shooter.y))
        shootToward(a, best.x, best.y, power)
    }

    private fun waitReady(a: MarblesActivity) = waitFor("내 차례 입력") { a.acceptingInput || a.game.over }

    private fun waitStopped(a: MarblesActivity) = waitFor("모두 멈춤") { a.game.phase != Phase.MOVING }

    /** n 명이 끝까지 → 결과 순위 */
    private fun playToEnd(n: Int, seed: Int) {
        val a = launch(n, seed)
        a.beeper.enabled = false
        assertFalse("소개 중엔 못 놓음", a.acceptingInput)
        waitReady(a)
        var shots = 0
        var extras = 0
        while (!a.game.over && shots < 200) {
            waitReady(a)
            if (a.game.over) break
            val shooter = a.game.current
            val before = a.game.captured[shooter]
            val again0 = a.againCount
            playTurn(a, 0.85f + (shots % 3) * 0.075f)
            assertSame("쏨", Phase.MOVING, a.game.phase)
            assertTrue("구르는 동안 프레임 루프", a.looping)
            waitStopped(a)
            assertFalse("모두 멈추면 루프 멈춤", a.looping)
            assertFalse("내 구슬은 치움", a.game.shooterPlaced)
            shots++
            val got = a.game.captured[shooter] - before
            if (a.game.over) break
            waitReady(a)
            if (got > 0) {
                extras++
                assertEquals("획득 → 한 번 더", shooter, a.game.current)
                assertEquals(MarblesActivity.AGAIN, a.lastBanner)
                assertEquals(again0 + 1, a.againCount)
            } else {
                assertEquals("다음 사람", (shooter + 1) % n, a.game.current)
                assertEquals(a.players[a.game.current].name + " 차례", a.lastBanner)
            }
        }
        assertTrue("끝까지 진행 ($shots 번)", a.game.over)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(Ranking.competition(a.game.captured.map { it.toLong() }), ranks)
        assertArrayEquals(IntArray(n) { it }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        assertEquals("획득 합 = 사라진 구슬", GameConfig.TARGETS - a.game.remaining, a.game.captured.sum())
        println("marbles ${n}p: shots=$shots extras=$extras captured=${a.game.captured.toList()} ranks=${ranks.toList()} remaining=${a.game.remaining}")
    }

    @Test fun playTwoPlayers() = playToEnd(2, 1)

    @Test fun playThreePlayers() = playToEnd(3, 2)

    @Test fun playFourPlayers() = playToEnd(4, 3)

    @Test
    fun placeIsClampedOutsideRingAndShooterDragAims() {
        val a = launch(2)
        waitReady(a)
        assertFalse(a.game.shooterPlaced)
        // 링 가운데를 누르면 링 밖(아래)으로
        tapPlace(a, GameConfig.RING_X, GameConfig.RING_Y + 0.5f)
        assertTrue(a.game.shooterPlaced)
        assertEquals(GameConfig.RING_X, a.game.shooter.x, 1e-3f)
        assertEquals(GameConfig.RING_Y + GameConfig.PLACE_MIN_DIST, a.game.shooter.y, 1e-3f)
        assertSame(Phase.AIM, a.game.phase)
        // 다른 곳을 누르면 옮김
        tapPlace(a, 2f, 3f)
        assertEquals(2f, a.game.shooter.x, 1e-3f)
        assertEquals(3f, a.game.shooter.y, 1e-3f)
        // 내 구슬을 누르고 끌면 새총 (구슬은 그대로)
        a.field.toScreen(2f, 3f, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        touch(a, MotionEvent.ACTION_MOVE, pt.x - a.field.unitPx * 3f, pt.y)
        assertTrue("조준 중", a.field.aiming)
        assertEquals(2f, a.game.shooter.x, 1e-3f)
        // 짧게 되돌려 놓으면 취소
        touch(a, MotionEvent.ACTION_MOVE, pt.x - a.field.unitPx * 0.3f, pt.y)
        touch(a, MotionEvent.ACTION_UP, pt.x - a.field.unitPx * 0.3f, pt.y)
        assertFalse(a.field.aiming)
        assertSame("취소 → 그대로 조준 단계", Phase.AIM, a.game.phase)
        assertEquals(0, a.game.shots)
        assertFalse(a.looping)
    }

    @Test
    fun loopFullyStopsWhenSettled() {
        val a = launch(2)
        waitReady(a)
        assertFalse("대기 중엔 루프 없음", a.looping)
        val f0 = a.frames
        idle(2000)
        assertEquals("대기 중엔 프레임이 하나도 안 돈다", f0, a.frames)
        val me = a.game.current
        // 왼쪽 가장자리에서 약하게 위로 (아무것도 안 건드림)
        tapPlace(a, 2f, GameConfig.RING_Y)
        shootToward(a, 2f, 0f, 0.4f)
        assertSame(Phase.MOVING, a.game.phase)
        assertTrue(a.looping)
        waitStopped(a)
        assertFalse(a.looping)
        val f1 = a.frames
        assertTrue("구르는 동안 프레임이 돌았다 (${f1 - f0})", f1 > f0 + 3)
        idle(3000)
        assertEquals("멈춘 뒤엔 다음 프레임이 예약되지 않음", f1, a.frames)
        assertFalse(a.looping)
        waitReady(a)
        assertEquals("못 맞힘 → 다음 사람", 1 - me, a.game.current)
        idle(3000)
        assertEquals(f1, a.frames)
    }

    @Test
    fun pauseMidRollFreezesPhysicsAndResumeContinues() {
        val a = launch(3)
        waitReady(a)
        playTurn(a, 1f)
        idle(120)
        assertSame(Phase.MOVING, a.game.phase)
        assertTrue(a.looping)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val xs = a.game.targets.map { it.x } + a.game.shooter.x
        val steps = a.game.world.steps
        val f = a.frames
        idle(4000)
        assertEquals("일시정지 중엔 물리가 멈춤", xs, a.game.targets.map { it.x } + a.game.shooter.x)
        assertEquals(steps, a.game.world.steps)
        assertEquals(f, a.frames)
        assertSame("아직 구르던 중", Phase.MOVING, a.game.phase)
        assertFalse("일시정지 중엔 못 놓음", a.acceptingInput)
        // 계속하기 → 다시 돈다
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        waitStopped(a)
        assertFalse(a.looping)
        assertTrue(a.frames > f)
    }

    @Test
    fun extraTurnBannerThenMissPassesTurn() {
        val a = launch(2, seed = 5)
        a.beeper.enabled = false
        waitReady(a)
        val g = a.game
        val me = g.current
        // 구슬 둘만 남김: 하나는 링 오른쪽 가장자리 근처(가로줄 위), 하나는 위쪽(길에서 비킴)
        for (t in g.targets) if (t.id > 1) g.removeTarget(t.id)
        g.putTarget(0, GameConfig.RING_X + GameConfig.RING_R - 1.2f, GameConfig.RING_Y)
        g.putTarget(1, GameConfig.RING_X, GameConfig.RING_Y - 4.5f)
        tapPlace(a, 1.5f, GameConfig.RING_Y)
        shootToward(a, GameConfig.FIELD_W, GameConfig.RING_Y, 1f)
        waitStopped(a)
        assertSame(MarbleState.CAPTURED, g.targets[0].state)
        assertFalse("판정 연출 중엔 못 놓음", a.acceptingInput)
        waitReady(a)
        assertEquals("한 번 더", me, g.current)
        assertEquals(MarblesActivity.AGAIN, a.lastBanner)
        assertEquals(1, a.againCount)
        assertEquals(1, g.captured[me])
        assertEquals(1, g.remaining)
        assertFalse(g.shooterPlaced)
        assertEquals(MarblesActivity.HINT_PLACE, a.panel.hintText)
        // 이번엔 마당 밖으로 헛방 → 다음 사람
        tapPlace(a, 1.5f, GameConfig.RING_Y)
        shootToward(a, -5f, GameConfig.RING_Y, 0.6f)
        waitStopped(a)
        waitReady(a)
        assertEquals(1 - me, g.current)
        assertEquals(a.players[1 - me].name + " 차례", a.lastBanner)
        assertEquals(1, a.againCount)
        assertEquals(1, g.missedTurns)
    }

    @Test
    fun moduleInfo() {
        assertEquals("marbles", MarblesModule.id)
        assertEquals("구슬치기", MarblesModule.name)
        assertEquals(3, MarblesModule.rules.size)
        assertEquals("5분", MarblesModule.minutes)
        assertEquals(2..4, MarblesModule.playerCounts)
        assertEquals("2~4인", MarblesModule.playersLabel)
        assertNull(MarblesModule.unavailableReason(4))
        assertFalse(MarblesModule.supports(5))
    }
}
