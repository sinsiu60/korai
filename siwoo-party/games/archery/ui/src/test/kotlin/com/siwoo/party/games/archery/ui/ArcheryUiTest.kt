package com.siwoo.party.games.archery.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.archery.ArcheryModule
import com.siwoo.party.games.archery.GameConfig
import com.siwoo.party.games.archery.Phase
import com.siwoo.party.games.archery.Shot
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

/**
 * 양궁 화면: 손놀림을 정해(흔들림 0 시험 고리) 2·3·4인 끝까지 → 순위 (동점 가르기 포함),
 * 쏠 때마다 확대 화면·점수, 숨 참기 배율로 발사, 일시정지 중 흔들림·숨 타이머 멈춤 + 터치 무시, 대기 중 루프 정지.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class ArcheryUiTest {

    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        ArcheryActivity.randomOverride = null
        ArcheryActivity.noiseOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, seed: Int, noise: Float?): ArcheryActivity {
        ArcheryActivity.randomOverride = Random(seed)
        ArcheryActivity.noiseOverride = noise
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), ArcheryModule, all.take(n))
        return Robolectric.buildActivity(ArcheryActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(3000) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun touch(a: ArcheryActivity, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.field.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    /**
     * 바람을 보정해 고리 자리 (tx, ty) 에 꽂기: 누르고 → 바람 반대로 끌고 → holdMs 기다렸다 → 떼기.
     * (흔들림·흩어짐 0 일 때 정확히 그 자리)
     */
    private fun shootAt(a: ArcheryActivity, tx: Float, ty: Float, holdMs: Long = 300L): Shot {
        waitFor("조준") { a.acceptingInput }
        val g = a.game
        val k = g.shotIndex
        val bx = tx - g.wind.driftX
        val by = ty - g.wind.driftY
        val f = a.field.unitPx / GameConfig.AIM_DRAG_GAIN
        val x0 = a.field.width * 0.5f
        val y0 = a.field.height * 0.7f
        val x1 = x0 + (bx - g.aimBaseX) * f
        val y1 = y0 + (by - g.aimBaseY) * f
        touch(a, MotionEvent.ACTION_DOWN, x0, y0)
        assertTrue("누르면 숨 참기", g.holding)
        touch(a, MotionEvent.ACTION_MOVE, (x0 + x1) / 2f, (y0 + y1) / 2f)
        touch(a, MotionEvent.ACTION_MOVE, x1, y1)
        idle(holdMs)
        touch(a, MotionEvent.ACTION_UP, x1, y1)
        assertSame("떼면 발사", Phase.RESULT, g.phase)
        val s = g.lastShot!!
        assertEquals(k, g.shotIndex)
        return s
    }

    /** 끝까지: spot(사람, 몇 발째) 자리에 쏨. 쏠 때마다 확대 화면 확인 */
    private fun playToEnd(a: ArcheryActivity, spot: (Int, Int) -> FloatArray): IntArray {
        var shots = 0
        var guard = 0
        while (!a.isFinishing && guard++ < 4000) {
            val fb = a.finalButton
            if (fb != null) {
                assertSame(Phase.OVER, a.game.phase)
                assertNotNull(a.finalPopup)
                idle(400)
                assertFalse("점수표에서 기다리는 동안 루프 정지", a.looping)
                val f = a.frames
                idle(500)
                assertEquals(f, a.frames)
                fb.performClick()
                idle(50)
                continue
            }
            if (a.acceptingInput) {
                val p = a.game.current
                val k = a.game.arrow
                assertEquals(players(a)[p].name + " 차례", a.header.shownName)
                val xy = spot(p, k)
                val s = shootAt(a, xy[0], xy[1])
                assertEquals(p, s.player)
                assertEquals(k, s.arrow)
                assertEquals("자리 x", xy[0], s.x, 2e-3f)
                assertEquals("자리 y", xy[1], s.y, 2e-3f)
                // 날아가 꽂힌 뒤 확대 화면에 점수
                waitFor("확대 화면") { a.zoom.isShowing }
                assertEquals(s.label, a.zoom.shownLabel)
                shots++
                continue
            }
            idle(50)
        }
        assertTrue("끝남 ($shots 발)", a.isFinishing)
        assertEquals(a.game.totalShots, shots)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    private fun players(a: ArcheryActivity) = all.take(a.game.players)

    private fun xy(x: Float, y: Float = 0f) = floatArrayOf(x, y)

    @Test
    fun twoPlayers_allXBeatsSevens() {
        val a = launch(2, 3, 0f)
        assertFalse("소개 중엔 못 쏨", a.acceptingInput)
        val ranks = playToEnd(a) { p, _ -> if (p == 0) xy(0f) else xy(3.5f, -0.5f) }
        assertArrayEquals(intArrayOf(60, 42), a.game.totals)
        assertArrayEquals(intArrayOf(6, 0), a.game.xCount)
        assertArrayEquals(intArrayOf(1, 2), ranks)
    }

    @Test
    fun threePlayers_tieBrokenByXCount() {
        val a = launch(3, 5, 0f)
        val ranks = playToEnd(a) { p, _ ->
            when (p) {
                0 -> xy(0.8f)        // 10점 (X 아님)
                1 -> xy(0f, 0.2f)    // X
                else -> xy(0f, 5.5f) // 5점
            }
        }
        assertArrayEquals(intArrayOf(60, 60, 30), a.game.totals)
        assertArrayEquals(intArrayOf(0, 6, 0), a.game.xCount)
        assertArrayEquals("합계 같으면 X 많은 쪽", intArrayOf(2, 1, 3), ranks)
    }

    @Test
    fun fourPlayers_tiesAndMiss() {
        val a = launch(4, 8, 0f)
        val ranks = playToEnd(a) { p, k ->
            when (p) {
                0 -> xy(1.5f)                                   // 9점 × 6 = 54
                1 -> xy(-1.5f)                                  // 9점 × 6 = 54
                2 -> if (k == 0) xy(0f) else xy(0f, -2.5f)      // X + 8점 × 5 = 50
                else -> if (k == 3) xy(0f, 10.15f) else xy(1.2f, 0.9f) // M + 9점 × 5 = 45
            }
        }
        assertArrayEquals(intArrayOf(54, 54, 50, 45), a.game.totals)
        assertTrue(a.game.shots[3][3]!!.miss)
        assertArrayEquals("완전 동점은 같은 순위", intArrayOf(1, 1, 3, 4), ranks)
    }

    @Test
    fun holdReleaseUsesBreathCurve_andTurnsAlternate() {
        val a = launch(2, 11, null)
        waitFor("조준") { a.acceptingInput }
        assertTrue("조준 중 루프", a.looping)
        val first = a.game.current
        // 약 1.2초 참았다 떼면 가장 덜 흔들림
        touch(a, MotionEvent.ACTION_DOWN, 300f, 300f)
        idle(1200)
        assertEquals(GameConfig.BREATH_MIN, a.game.swayScaleAt(a.timers.now()), 1e-4f)
        touch(a, MotionEvent.ACTION_UP, 300f, 300f)
        val s1 = a.game.lastShot!!
        assertEquals(GameConfig.BREATH_MIN, s1.swayScale, 1e-4f)
        assertTrue(s1.heldMs in 1100L..1500L)
        waitFor("확대 화면") { a.zoom.isShowing }
        assertEquals(s1.label, a.zoom.shownLabel)
        // 다음 사람 (한 발씩 돌아가며)
        waitFor("다음 차례") { a.acceptingInput }
        assertFalse(a.zoom.isShowing)
        assertEquals(1 - first, a.game.current)
        assertEquals(0, a.game.arrow)
        // 너무 오래 참으면 오히려 크게 흔들림
        touch(a, MotionEvent.ACTION_DOWN, 300f, 300f)
        idle(GameConfig.BREATH_LIMIT_MS + GameConfig.BREATH_GROW_MS + 300)
        touch(a, MotionEvent.ACTION_UP, 300f, 300f)
        val s2 = a.game.lastShot!!
        assertTrue("숨이 차면 배율 > 1 (${s2.swayScale})", s2.swayScale > 1f)
        // 확대 화면을 누르면 결과 뒤 바로 닫힘
        waitFor("확대 화면") { a.zoom.isShowing }
        idle(GameConfig.ZOOM_IN_MS + 200)
        a.zoom.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        a.zoom.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 10f, 10f, 0))
        waitFor("닫힘") { !a.zoom.isShowing }
        waitFor("2발째") { a.acceptingInput }
        assertEquals(first, a.game.current)
        assertEquals(1, a.game.arrow)
    }

    @Test
    fun pauseFreezesSwayAndBreath_andIgnoresTouches() {
        val a = launch(2, 21, null)
        waitFor("조준") { a.acceptingInput }
        touch(a, MotionEvent.ACTION_DOWN, 300f, 300f)
        idle(500)
        assertTrue(a.game.holding)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val now0 = a.timers.now()
        val held = a.game.breath.heldMs(now0)
        val scale = a.game.swayScaleAt(now0)
        val aim0 = FloatArray(2)
        a.game.aimAt(now0, aim0)
        val f = a.frames
        idle(3000)
        val now1 = a.timers.now()
        assertEquals("게임 시계 멈춤", now0, now1)
        assertEquals("숨 참은 시간 그대로", held, a.game.breath.heldMs(now1))
        assertEquals(scale, a.game.swayScaleAt(now1), 0f)
        val aim1 = FloatArray(2)
        a.game.aimAt(now1, aim1)
        assertEquals("조준점 그대로", aim0[0], aim1[0], 0f)
        assertEquals(aim0[1], aim1[1], 0f)
        assertEquals("프레임 없음", f, a.frames)
        // 일시정지 중 떼기·누르기 → 무시 (쏘지 않음)
        touch(a, MotionEvent.ACTION_UP, 300f, 300f)
        touch(a, MotionEvent.ACTION_DOWN, 300f, 300f)
        touch(a, MotionEvent.ACTION_UP, 300f, 300f)
        assertSame(Phase.AIM, a.game.phase)
        assertNull(a.game.lastShot)
        // 계속하기 → 숨 참기는 쏘지 않고 끝, 흔들림 보통
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertFalse(a.game.holding)
        assertEquals(1f, a.game.swayScaleAt(a.timers.now()), 0f)
        assertTrue(a.looping)
        assertTrue(a.acceptingInput)
        // 다시 정상으로 쏠 수 있음
        touch(a, MotionEvent.ACTION_DOWN, 300f, 300f)
        idle(900)
        touch(a, MotionEvent.ACTION_UP, 300f, 300f)
        assertNotNull(a.game.lastShot)
        assertEquals(900.0, a.game.lastShot!!.heldMs.toDouble(), 120.0)
    }

    @Test
    fun moduleInfo() {
        assertEquals("archery", ArcheryModule.id)
        assertEquals("양궁", ArcheryModule.name)
        assertEquals(3, ArcheryModule.rules.size)
        assertSame(GameCategory.TIMING, ArcheryModule.category)
        assertSame(PlayMode.TURNS, ArcheryModule.mode)
        assertEquals(2..4, ArcheryModule.playerCounts)
        assertEquals("6분", ArcheryModule.minutes)
        assertTrue(ArcheryModule.supports(2) && ArcheryModule.supports(4))
        assertFalse(ArcheryModule.supports(5))
    }
}
