package com.siwoo.party.games.bowling.ui

import android.app.Activity
import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.bowling.BowlingModule
import com.siwoo.party.games.bowling.GameConfig
import com.siwoo.party.games.bowling.Phase
import com.siwoo.party.games.bowling.RollMark
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 볼링 화면: 스와이프로 끝까지 → 순위 (2·3·4인), 스트라이크 배너, 루프 정지, 일시정지, 잘못 민 스와이프, 프레임 수 설정 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class BowlingUiTest {

    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))
    private val pt = PointF()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        BowlingActivity.framesOverride = null
        RuntimeEnvironment.getApplication().getSharedPreferences(BowlingSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int): BowlingActivity {
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), BowlingModule, all.take(n))
        return Robolectric.buildActivity(BowlingActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: BowlingActivity) = waitFor("굴릴 차례") { a.acceptingInput }
    private fun waitSettled(a: BowlingActivity) = waitFor("굴림 끝") { a.game.phase != Phase.ROLLING }

    /**
     * 어프로치에서 레인 높이 laneY 의 공을 잡고 오른쪽으로 레인 폭만큼 민다.
     * power 0..1 이 되도록 걸리는 시간을 맞추고, devDeg 만큼 비스듬히(+ = 아래), spin 만큼 활처럼 휘게(+ = 아래로 휘는 훅).
     */
    private fun swipe(a: BowlingActivity, laneY: Float, power: Float, devDeg: Float = 0f, spin: Float = 0f, lengthRef: Float = 1f) {
        val v = a.lane
        val ref = GameConfig.LANE_W * v.unitPx
        v.toScreen(-4.5f, laneY, pt)
        val x0 = pt.x
        val y0 = pt.y
        val len = ref * lengthRef
        val rad = Math.toRadians(devDeg.toDouble())
        val dx = (cos(rad) * len).toFloat()
        val dy = (sin(rad) * len).toFloat()
        val speedNorm = GameConfig.SWIPE_MIN_SPEED + power * (GameConfig.SWIPE_FULL_SPEED - GameConfig.SWIPE_MIN_SPEED)
        val dur = (1000f * lengthRef / speedNorm).toLong()
        val t0 = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x0, y0, 0))
        val steps = 10
        for (k in 1..steps) {
            val f = k / steps.toFloat()
            val bulge = (-spin * GameConfig.SWIPE_FULL_BOW * len * sin(PI * f)).toFloat()
            val x = x0 + dx * f
            val y = y0 + dy * f + bulge
            val t = t0 + dur * k / steps
            v.dispatchTouchEvent(MotionEvent.obtain(t0, t, if (k == steps) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE, x, y, 0))
        }
    }

    /** 순서대로 바꿔 가며 쓰는 투구 (스트라이크·거터·보통이 섞임) */
    private val plays = listOf(
        floatArrayOf(5.6f, 0.6f, 0f, 0f),
        floatArrayOf(3.2f, 0.4f, 3f, 0.3f),
        floatArrayOf(7.4f, 0.8f, -4f, -0.2f),
        floatArrayOf(1.2f, 0.5f, -12f, 0f),
        floatArrayOf(4.6f, 0.3f, 2f, 0.6f),
        floatArrayOf(6.1f, 0.9f, -1f, 0f),
    )

    /** 끝까지 스와이프로 → 순위 확인 */
    private fun playToEnd(a: BowlingActivity) {
        var k = 0
        while (!a.isFinishing && k < 300) {
            waitFor("굴릴 차례 또는 최종") { a.acceptingInput || a.finalButton != null || a.isFinishing }
            val b = a.finalButton
            if (b != null) {
                assertSame(Phase.OVER, a.game.phase)
                b.performClick()
                break
            }
            if (a.isFinishing) break
            val p = plays[k % plays.size]
            val before = a.game.throws
            swipe(a, p[0], p[1], p[2], p[3])
            assertEquals("스와이프로 굴림 ($k)", before + 1, a.game.throws)
            assertSame(Phase.ROLLING, a.game.phase)
            assertTrue("굴러가는 동안 루프", a.looping)
            waitSettled(a)
            assertFalse("멈추면 루프도 멈춤", a.looping)
            k++
        }
        waitFor("결과") { a.isFinishing }
        for (c in a.game.cards) assertTrue(c.done)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(a.game.ranks(), ranks)
        // 점수가 높을수록 순위가 앞
        for (i in ranks.indices) for (j in ranks.indices) {
            if (a.game.total(i) > a.game.total(j)) assertTrue(ranks[i] < ranks[j])
            if (a.game.total(i) == a.game.total(j)) assertEquals(ranks[i], ranks[j])
        }
        assertArrayEquals(IntArray(a.players.size) { it }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
    }

    @Test
    fun twoPlayersFiveFramesBySwipes() {
        BowlingSettings.setChoice(RuntimeEnvironment.getApplication(), GameConfig.FRAMES_SHORT)
        val a = launch(2)
        assertEquals("설정한 5프레임", 5, a.game.frames)
        assertFalse("소개 중엔 못 굴림", a.acceptingInput)
        waitReady(a)
        playToEnd(a)
    }

    @Test
    fun threePlayersToEnd() {
        BowlingActivity.framesOverride = 2
        val a = launch(3)
        waitReady(a)
        playToEnd(a)
    }

    @Test
    fun fourPlayersToEnd() {
        BowlingActivity.framesOverride = 1
        val a = launch(4)
        waitReady(a)
        playToEnd(a)
    }

    @Test
    fun strikeBannerWhenAllTenFall() {
        val a = launch(2)
        waitReady(a)
        assertEquals(0, a.game.current)
        swipe(a, 5.6f, 0.6f)
        assertSame(Phase.ROLLING, a.game.phase)
        waitSettled(a)
        assertSame(RollMark.STRIKE, a.game.outcome.mark)
        assertEquals(10, a.game.outcome.knocked)
        assertEquals("쓰러진 핀은 회색으로 남아 있다가", 0, a.game.lane.standingCount)
        waitFor("스트라이크 배너") { a.strikeBanners == 1 }
        assertEquals(BowlingActivity.STRIKE, a.lastBanner)
        waitFor("점수판 잠깐") { a.boardPopup != null }
        waitReady(a)
        assertNull(a.boardPopup)
        assertEquals("스트라이크 → 다음 사람", 1, a.game.current)
        assertEquals("다음 사람은 새로 세운 10개", 10, a.game.lane.standingCount)
        assertEquals("X", a.game.cards[0].mark(0, 1))
    }

    @Test
    fun loopStopsWhenSettled() {
        val a = launch(2)
        waitReady(a)
        assertFalse("대기 중엔 루프 없음", a.looping)
        val f0 = a.frames
        idle(2000)
        assertEquals("대기 중엔 프레임이 안 돈다", f0, a.frames)
        swipe(a, 3.2f, 0.4f, 3f, 0.3f)
        assertTrue(a.looping)
        waitSettled(a)
        assertFalse(a.looping)
        val f1 = a.frames
        assertTrue("굴러가는 동안 프레임이 돌았다 (${f1 - f0})", f1 > f0 + 20)
        idle(3000)
        assertEquals("멈춘 뒤엔 다음 프레임이 예약되지 않음", f1, a.frames)
        assertFalse(a.game.lane.rolling)
    }

    @Test
    fun pauseFreezesPhysics() {
        val a = launch(2)
        waitReady(a)
        swipe(a, 5f, 0.3f)
        idle(300)
        assertSame(Phase.ROLLING, a.game.phase)
        assertTrue(a.looping)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val bx = a.game.lane.ball.x
        val f = a.frames
        idle(4000)
        assertEquals("일시정지 중엔 물리가 멈춤", bx, a.game.lane.ball.x, 0f)
        assertEquals(f, a.frames)
        assertSame(Phase.ROLLING, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        waitSettled(a)
        assertFalse(a.looping)
        assertTrue(a.game.lane.ball.x > bx || !a.game.lane.ball.active)
    }

    @Test
    fun badSwipeDoesNotThrow() {
        val a = launch(2)
        waitReady(a)
        // 너무 짧게
        swipe(a, 5f, 0.5f, lengthRef = 0.1f)
        assertSame(Phase.AIM, a.game.phase)
        assertEquals(0, a.game.throws)
        assertEquals("더 길게 밀어요!", a.header.shownHint)
        // 뒤로(왼쪽) 밀기
        swipe(a, 5f, 0.5f, devDeg = 180f, lengthRef = 0.3f)
        assertSame(Phase.AIM, a.game.phase)
        assertEquals(0, a.game.throws)
        // 레인 위(어프로치 밖)를 누르면 잡히지 않음
        a.lane.toScreen(10f, 5f, pt)
        val t = SystemClock.uptimeMillis()
        assertFalse(a.lane.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0)))
        assertFalse(a.lane.holding)
        assertFalse(a.looping)
        // 제대로 밀면 굴러감
        swipe(a, 5f, 0.5f)
        assertEquals(1, a.game.throws)
        assertSame(Phase.ROLLING, a.game.phase)
    }

    @Test
    fun scoreBoardButton() {
        val a = launch(2)
        waitReady(a)
        a.boardButton.performClick()
        assertNotNull(a.boardPopup)
        assertFalse("점수판이 떠 있으면 못 굴림", a.acceptingInput)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("점수판 닫힘") { a.boardPopup == null }
        assertTrue(a.acceptingInput)
    }

    @Test
    fun framesSettingDefaults() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals(10, BowlingSettings.frames(ctx, 2))
        assertEquals(5, BowlingSettings.frames(ctx, 3))
        assertEquals(5, BowlingSettings.frames(ctx, 4))
        assertEquals(5, launch(3).game.frames)
        BowlingSettings.setChoice(ctx, GameConfig.FRAMES_LONG)
        assertEquals(10, BowlingSettings.frames(ctx, 4))
        assertEquals(10, launch(4).game.frames)
        BowlingSettings.setChoice(ctx, GameConfig.FRAMES_SHORT)
        assertEquals(5, launch(2).game.frames)
    }

    @Test
    fun moduleInfo() {
        assertEquals("bowling", BowlingModule.id)
        assertEquals("볼링", BowlingModule.name)
        assertEquals(3, BowlingModule.rules.size)
        assertEquals("10분", BowlingModule.minutes)
        assertEquals(2..4, BowlingModule.playerCounts)
        assertNotNull(BowlingModule.settingsLabel)
        assertFalse(BowlingModule.supports(1))
        assertFalse(BowlingModule.supports(5))
    }
}
