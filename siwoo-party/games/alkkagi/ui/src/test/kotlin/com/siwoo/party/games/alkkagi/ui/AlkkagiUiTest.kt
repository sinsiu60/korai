package com.siwoo.party.games.alkkagi.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.alkkagi.AlkkagiModule
import com.siwoo.party.games.alkkagi.GameConfig
import com.siwoo.party.games.alkkagi.Phase
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

/** 알까기 화면: 끌어서 튕기며 끝까지 → 순위, 상대 알 터치 무시, 짧은 끌기 취소, 멈추면 루프 정지, 일시정지, 3인 거절 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class AlkkagiUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))
    private val two = players.take(2)
    private val pt = PointF()

    /** 프레임(postOnAnimation)은 idle 로 흘린 시간만큼만 돈다 (TugUiTest 와 같은 방식) */
    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { AlkkagiActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(seed: Int = 1): AlkkagiActivity {
        AlkkagiActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), AlkkagiModule, two)
        return Robolectric.buildActivity(AlkkagiActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1200) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun touch(a: AlkkagiActivity, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    /** 알 id 를 누르고 (dx, dy) 판 단위만큼 끌었다 놓기 */
    private fun drag(a: AlkkagiActivity, id: Int, dx: Float, dy: Float) {
        a.board.stoneCenter(id, pt)
        val k = a.board.unitPx
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        touch(a, MotionEvent.ACTION_MOVE, pt.x + dx * k * 0.5f, pt.y + dy * k * 0.5f)
        touch(a, MotionEvent.ACTION_MOVE, pt.x + dx * k, pt.y + dy * k)
        touch(a, MotionEvent.ACTION_UP, pt.x + dx * k, pt.y + dy * k)
    }

    /** 내 알 중 상대 알과 가장 가까운 것을 골라 그 상대 알 쪽으로 최대 힘 */
    private fun shootNearest(a: AlkkagiActivity) {
        val g = a.game
        var best = -1
        var target = -1
        var bd = Float.MAX_VALUE
        for (s in g.stones) if (s.owner == g.current && s.onBoard) for (o in g.stones) if (o.owner != g.current && o.onBoard) {
            val d = (s.x - o.x) * (s.x - o.x) + (s.y - o.y) * (s.y - o.y)
            if (d < bd) { bd = d; best = s.id; target = o.id }
        }
        val s = g.stones[best]
        val o = g.stones[target]
        val len = sqrt(bd)
        val m = GameConfig.ALKKAGI_MAX_DRAG * 1.2f
        drag(a, best, (s.x - o.x) / len * m, (s.y - o.y) / len * m)
    }

    private fun waitReady(a: AlkkagiActivity) = waitFor("내 차례 입력") { a.acceptingInput }

    private fun waitStopped(a: AlkkagiActivity) = waitFor("모두 멈춤") { a.game.phase != Phase.MOVING }

    @Test
    fun playByDraggingUntilOneSideIsEmpty() {
        val a = launch()
        assertFalse("소개 중엔 못 튕김", a.acceptingInput)
        waitReady(a)
        assertTrue(a.sides[a.game.current].isCurrent)
        var turns = 0
        while (a.game.phase != Phase.OVER && turns < 80) {
            val shooter = a.game.current
            shootNearest(a)
            assertSame("튕김", Phase.MOVING, a.game.phase)
            assertTrue("움직이는 동안 프레임 루프", a.looping)
            waitStopped(a)
            assertFalse("모두 멈추면 루프 멈춤", a.looping)
            for (p in 0..1) assertEquals(a.game.remaining(p), a.sides[p].shownCount)
            if (a.game.phase == Phase.AIM) {
                assertEquals("차례가 넘어감", 1 - shooter, a.game.current)
                assertTrue(a.sides[1 - shooter].isCurrent)
                assertFalse(a.sides[shooter].isCurrent)
                waitReady(a)
            }
            turns++
        }
        assertSame("끝까지 진행 ($turns 차례)", Phase.OVER, a.game.phase)
        val w = a.game.winner
        assertTrue(a.game.remaining(1 - w) == 0)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(if (w == 0) intArrayOf(1, 2) else intArrayOf(2, 1), ranks)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
    }

    @Test
    fun loopFullyStopsWhenIdle() {
        val a = launch()
        waitReady(a)
        assertFalse("대기 중엔 루프 없음", a.looping)
        val f0 = a.frames
        idle(2000)
        assertEquals("대기 중엔 프레임이 하나도 안 돈다", f0, a.frames)
        val me = a.game.current
        // 약하게 한 번 (아무것도 안 떨어짐)
        val id = if (me == 0) 4 else 12
        drag(a, id, if (me == 0) -1.5f else 1.5f, 0f)
        assertSame(Phase.MOVING, a.game.phase)
        assertTrue(a.looping)
        waitStopped(a)
        assertFalse(a.looping)
        val f1 = a.frames
        assertTrue("움직이는 동안 프레임이 돌았다 (${f1 - f0})", f1 > f0 + 3)
        idle(3000)
        assertEquals("멈춘 뒤엔 다음 프레임이 예약되지 않음", f1, a.frames)
        assertFalse(a.looping)
        assertEquals(1 - me, a.game.current)
    }

    @Test
    fun pauseMidMotionStopsLoopAndResumeContinues() {
        val a = launch()
        waitReady(a)
        shootNearest(a)
        idle(120)
        assertSame(Phase.MOVING, a.game.phase)
        assertTrue(a.looping)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val xs = a.game.stones.map { it.x }
        val f = a.frames
        idle(4000)
        assertEquals("일시정지 중엔 물리가 멈춤", xs, a.game.stones.map { it.x })
        assertEquals(f, a.frames)
        assertSame("아직 움직이던 중", Phase.MOVING, a.game.phase)
        // 계속하기 → 다시 돈다
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        waitStopped(a)
        assertFalse(a.looping)
        assertTrue(a.frames > f)
    }

    @Test
    fun touchingOpponentStoneDoesNothing() {
        val a = launch()
        waitReady(a)
        val me = a.game.current
        val opp = if (me == 0) 13 else 5
        val x0 = a.game.stones[opp].x
        drag(a, opp, if (me == 0) 4f else -4f, 0f)
        assertEquals("조준도 시작 안 됨", -1, a.board.aimId)
        assertSame(Phase.AIM, a.game.phase)
        assertEquals(me, a.game.current)
        assertEquals(0, a.game.shots)
        assertEquals(x0, a.game.stones[opp].x, 0f)
        assertFalse(a.looping)
    }

    @Test
    fun tooShortDragCancels() {
        val a = launch()
        waitReady(a)
        val me = a.game.current
        val id = if (me == 0) 5 else 13
        a.board.stoneCenter(id, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        touch(a, MotionEvent.ACTION_MOVE, pt.x - a.board.unitPx * 0.4f, pt.y)
        assertEquals("내 알은 조준 시작", id, a.board.aimId)
        touch(a, MotionEvent.ACTION_UP, pt.x - a.board.unitPx * 0.4f, pt.y)
        assertEquals(-1, a.board.aimId)
        assertSame("취소 → 그대로 조준 단계", Phase.AIM, a.game.phase)
        assertEquals(me, a.game.current)
        assertEquals(0, a.game.shots)
        assertFalse(a.game.stones[id].moving)
        assertFalse(a.looping)
        // 다시 길게 끌면 튕겨짐
        drag(a, id, if (me == 0) -3f else 3f, 0f)
        assertEquals(1, a.game.shots)
        assertSame(Phase.MOVING, a.game.phase)
    }

    @Test
    fun threePlayersRejected() {
        assertFalse(AlkkagiModule.supports(3))
        assertFalse(AlkkagiModule.supports(4))
        assertTrue(AlkkagiModule.supports(2))
        assertEquals("2인", AlkkagiModule.playersLabel)
        assertEquals("2인 전용", AlkkagiModule.unavailableReason(3))
        assertNull(AlkkagiModule.unavailableReason(2))
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), AlkkagiModule, players)
        val a = Robolectric.buildActivity(AlkkagiActivity::class.java, i).setup().get()
        assertTrue("3인은 바로 닫힘", a.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
    }

    @Test
    fun moduleInfo() {
        assertEquals("alkkagi", AlkkagiModule.id)
        assertEquals(3, AlkkagiModule.rules.size)
        assertEquals("5분", AlkkagiModule.minutes)
        assertEquals(2..2, AlkkagiModule.playerCounts)
    }
}
