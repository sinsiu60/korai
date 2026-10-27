package com.siwoo.party.games.curling.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.curling.CurlingModule
import com.siwoo.party.games.curling.CurlingRules
import com.siwoo.party.games.curling.GameConfig
import com.siwoo.party.games.curling.Phase
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 컬링 화면: 2·3·4(팀)인 끝까지 → 순위, 멈추면 루프 정지, 일시정지, 스위핑 연타, 휘는 방향 버튼 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class CurlingUiTest {

    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val pt = PointF()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { CurlingActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, seed: Int = 1): CurlingActivity {
        CurlingActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), CurlingModule, all.take(n))
        val a = Robolectric.buildActivity(CurlingActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(2000) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun touch(v: View, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    /** 핵 위 스톤을 눌러 (angleDeg 방향으로 던지게) 반대쪽으로 power 만큼 끌었다 놓기 */
    private fun throwBy(a: CurlingActivity, power: Float, angleDeg: Float) {
        a.sheet.hackScreen(pt)
        val dist = power * a.sheet.sling.maxDistance
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = (-cos(rad) * dist).toFloat()
        val dy = (-sin(rad) * dist).toFloat()
        touch(a.sheet, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        touch(a.sheet, MotionEvent.ACTION_MOVE, pt.x + dx * 0.5f, pt.y + dy * 0.5f)
        touch(a.sheet, MotionEvent.ACTION_MOVE, pt.x + dx, pt.y + dy)
        touch(a.sheet, MotionEvent.ACTION_UP, pt.x + dx, pt.y + dy)
    }

    private fun tapSheet(a: CurlingActivity) {
        val x = a.sheet.width * 0.6f
        val y = a.sheet.height * 0.4f
        touch(a.sheet, MotionEvent.ACTION_DOWN, x, y)
        touch(a.sheet, MotionEvent.ACTION_UP, x, y)
    }

    private fun pickCurl(a: CurlingActivity, dir: Int) {
        a.curl.segmentCenter(dir, pt)
        touch(a.curl, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        touch(a.curl, MotionEvent.ACTION_UP, pt.x, pt.y)
    }

    private fun waitReady(a: CurlingActivity) = waitFor("던질 차례") { a.acceptingInput }

    private fun waitStopped(a: CurlingActivity) = waitFor("모두 멈춤") { a.game.phase != Phase.MOVING }

    private val powers = floatArrayOf(0.45f, 0.5f, 0.38f, 0.55f, 0.9f, 0.3f, 0.48f, 0.42f)
    private val angles = floatArrayOf(0f, -3f, 3f, 5f, -5f, 1f, -1.5f)

    /** 끝까지 (점수판에서 [다음 엔드]) → 순위 확인 */
    private fun playToEnd(n: Int, seed: Int) {
        val a = launch(n, seed)
        assertFalse("소개 중엔 못 던짐", a.acceptingInput)
        var k = 0
        var boards = 0
        var swept = false
        while (!a.isFinishing && k < 200) {
            waitFor("차례·점수판·끝") { a.acceptingInput || a.nextButton != null || a.isFinishing }
            if (a.isFinishing) break
            val btn = a.nextButton
            if (btn != null) {
                boards++
                assertEquals("점수판 엔드", boards, a.game.results.count { it != null })
                btn.performClick()
                assertNull(a.boardPopup)
                continue
            }
            val g = a.game
            val thrower = g.current
            pickCurl(a, if (k % 2 == 0) 1 else -1)
            assertEquals(if (k % 2 == 0) 1 else -1, g.curlDir)
            val before = g.throwsMade
            throwBy(a, powers[k % powers.size], angles[k % angles.size])
            assertEquals("던짐", before + 1, g.throwsMade)
            assertSame(Phase.MOVING, g.phase)
            assertTrue("움직이는 동안 루프", a.looping)
            assertFalse("움직이는 동안 휘는 방향 못 바꿈", a.curl.active)
            if (k % 3 == 0) {
                idle(100)
                repeat(4) { tapSheet(a); idle(30) }
                if (g.sheet.sweep > 0f) swept = true
            }
            waitStopped(a)
            assertFalse("멈추면 루프 멈춤", a.looping)
            if (g.phase == Phase.AIM) assertTrue("다음 사람", g.current != thrower || n == 4)
            k++
        }
        assertTrue("스위핑이 한 번은 먹힘", swept)
        assertEquals("엔드 수만큼 점수판", GameConfig.ENDS, boards)
        assertEquals("모든 스톤", GameConfig.ENDS * CurlingRules.stonesPerEnd(n), k)
        assertSame(Phase.OVER, a.game.phase)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(CurlingRules.ranks(n, a.game.sideTotals), ranks)
        assertArrayEquals(IntArray(n) { it }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        // 합계가 엔드 점수의 합
        for (s in 0 until a.game.sides) assertEquals(a.game.endScores.sumOf { it[s] }, a.game.sideTotals[s])
        if (n == 4) {
            assertEquals("팀원 같은 순위", ranks[0], ranks[2])
            assertEquals(ranks[1], ranks[3])
        }
        println("CURLING n=$n totals=${a.game.sideTotals.toList()} ranks=${ranks.toList()}")
    }

    @Test fun playTwoPlayers() = playToEnd(2, 1)

    @Test fun playThreePlayers() = playToEnd(3, 2)

    @Test fun playFourPlayersTeams() = playToEnd(4, 3)

    @Test
    fun loopFullyStopsWhenSettled() {
        val a = launch(2)
        waitReady(a)
        assertFalse("대기 중엔 루프 없음", a.looping)
        val f0 = a.frames
        idle(2000)
        assertEquals("대기 중엔 프레임이 하나도 안 돈다", f0, a.frames)
        val me = a.game.current
        throwBy(a, 0.45f, 0f)
        assertTrue(a.looping)
        waitStopped(a)
        assertFalse(a.looping)
        val f1 = a.frames
        assertTrue("움직이는 동안 프레임이 돌았다 (${f1 - f0})", f1 > f0 + 30)
        val steps = a.game.sheet.steps
        idle(3000)
        assertEquals("멈춘 뒤엔 다음 프레임이 예약되지 않음", f1, a.frames)
        assertEquals("물리 단계도 멈춤", steps, a.game.sheet.steps)
        assertFalse(a.looping)
        assertTrue(a.game.current != me)
    }

    @Test
    fun pauseFreezesPhysicsAndResumeContinues() {
        val a = launch(2)
        waitReady(a)
        throwBy(a, 0.5f, 0f)
        idle(300)
        assertSame(Phase.MOVING, a.game.phase)
        assertTrue(a.looping)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val s = a.game.currentStone
        val x = s.x
        val steps = a.game.sheet.steps
        val f = a.frames
        idle(4000)
        assertEquals("일시정지 중엔 물리가 멈춤", x, s.x, 0f)
        assertEquals(steps, a.game.sheet.steps)
        assertEquals(f, a.frames)
        assertSame("아직 움직이던 중", Phase.MOVING, a.game.phase)
        // 일시정지 중 연타는 스위핑 안 됨
        tapSheet(a)
        assertEquals(0f, a.game.sheet.sweep, 0f)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        waitStopped(a)
        assertFalse(a.looping)
        assertTrue(s.x > x)
    }

    @Test
    fun sweepTapsAndShortDragCancel() {
        val a = launch(2)
        waitReady(a)
        // 조준 중 짧은 톡 = 취소 (던지지 않음, 스위핑도 아님)
        tapSheet(a)
        assertSame(Phase.AIM, a.game.phase)
        assertEquals(0, a.game.throwsMade)
        throwBy(a, 0.4f, 0f)
        assertSame(Phase.MOVING, a.game.phase)
        idle(100)
        tapSheet(a)
        assertEquals(GameConfig.SWEEP_PER_TAP, a.game.sheet.sweep, 0.02f)
        // 두 번째 손가락도 각각 연타로 센다
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        a.sheet.dispatchTouchEvent(down)
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0 }, MotionEvent.PointerProperties().apply { id = 1 })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = 100f; y = 100f }, MotionEvent.PointerCoords().apply { x = 300f; y = 120f })
        val pd = MotionEvent.obtain(t, t, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        a.sheet.dispatchTouchEvent(pd)
        assertEquals(3, a.game.sheet.sweepTaps)
        assertTrue(a.header.shownHint.contains("스위핑"))
        waitStopped(a)
    }

    @Test
    fun curlButtonsOnlyWhileAiming() {
        val a = launch(2)
        waitReady(a)
        assertEquals(1, a.game.curlDir)
        assertTrue(a.curl.active)
        pickCurl(a, -1)
        assertEquals(-1, a.game.curlDir)
        assertEquals(-1, a.curl.selected)
        throwBy(a, 0.45f, 0f)
        assertEquals(-1, a.game.sheet.curlDir)
        pickCurl(a, 1)
        assertEquals("움직이는 중엔 안 바뀜", -1, a.game.sheet.curlDir)
        waitStopped(a)
        waitReady(a)
        assertTrue(a.curl.active)
        // 왼쪽 컬로 곧게 던진 스톤은 위쪽(y 작음)으로 휘었다
        val s = a.game.stones[0]
        if (s.inPlay) assertTrue("왼쪽으로 휨 (y=${s.y})", s.y < GameConfig.HACK_Y)
    }

    @Test
    fun scoreBoardShowsAfterEnd() {
        val a = launch(2)
        waitReady(a)
        // 7개는 바로 놓고 마지막 하나만 던짐
        val g = a.game
        repeat(g.stonesPerEnd - 1) { i -> g.skipThrowTo(GameConfig.TEE_X + (if (i % 2 == 0) 0.5f + i else 3f + i * 0.3f), GameConfig.TEE_Y + (i % 3 - 1) * 2.5f) }
        waitReady(a)
        throwBy(a, 0.2f, 0f)
        waitFor("점수판") { a.nextButton != null }
        assertNotNull(a.boardPopup)
        assertSame(Phase.SCORED, g.phase)
        assertFalse(a.acceptingInput)
        assertNotNull(g.results[0])
        a.nextButton!!.performClick()
        assertEquals(1, g.end)
        waitReady(a)
    }

    @Test
    fun moduleInfo() {
        assertEquals("curling", CurlingModule.id)
        assertEquals("컬링", CurlingModule.name)
        assertEquals(3, CurlingModule.rules.size)
        assertEquals("10분", CurlingModule.minutes)
        assertEquals(2..4, CurlingModule.playerCounts)
        assertTrue(CurlingModule.supports(4))
        assertFalse(CurlingModule.supports(5))
        assertEquals(0, CurlingModule.needsMultiTouch)
    }
}
