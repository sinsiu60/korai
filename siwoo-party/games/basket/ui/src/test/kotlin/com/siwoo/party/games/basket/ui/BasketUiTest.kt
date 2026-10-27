package com.siwoo.party.games.basket.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.basket.BasketModule
import com.siwoo.party.games.basket.GameConfig
import com.siwoo.party.games.basket.Phase
import com.siwoo.party.games.basket.Scoring
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** 농구 슛 화면: 계산한 스와이프(골/놓침)로 끝까지 → 순위 (2·3·4인), 불꽃, 루프 정지, 일시정지, 잘못 민 스와이프 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class BasketUiTest {

    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        BasketActivity.shotsOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int): BasketActivity {
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), BasketModule, all.take(n))
        return Robolectric.buildActivity(BasketActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: BasketActivity) = waitFor("슛 차례") { a.acceptingInput }
    private fun waitSettled(a: BasketActivity) = waitFor("공 떨어짐") { a.game.phase != Phase.FLYING }

    /**
     * 코트 아래쪽에서 angleDeg(0 = 오른쪽, 90 = 위) 방향으로 코트 높이의 lengthRef 배만큼 민다.
     * 세기 power 0..1 이 되도록 걸리는 시간을 맞춘다 (스와이프 판정 기준 길이 = 코트 높이).
     */
    private fun swipe(a: BasketActivity, angleDeg: Float, power: Float, lengthRef: Float = 0.4f) {
        val v = a.court
        val ref = v.height.toFloat()
        val len = ref * lengthRef
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = (cos(rad) * len).toFloat()
        val dy = (-sin(rad) * len).toFloat()
        val x0 = v.width * 0.4f
        val y0 = v.height * 0.85f
        val speedNorm = GameConfig.SWIPE_MIN_SPEED + power * (GameConfig.SWIPE_FULL_SPEED - GameConfig.SWIPE_MIN_SPEED)
        val dur = (1000f * lengthRef / speedNorm).toLong()
        val t0 = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x0, y0, 0))
        val steps = 10
        for (k in 1..steps) {
            val f = k / steps.toFloat()
            val t = t0 + dur * k / steps
            v.dispatchTouchEvent(MotionEvent.obtain(t0, t, if (k == steps) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE, x0 + dx * f, y0 + dy * f, 0))
        }
    }

    /** 넣는 스와이프 (기본 각도·알맞은 세기) / 놓치는 스와이프 (아주 약하게) */
    private fun make(a: BasketActivity) = swipe(a, GameConfig.SWIPE_CENTER_DEG, GameConfig.SWEET_POWER)
    private fun miss(a: BasketActivity) = swipe(a, GameConfig.SWIPE_CENTER_DEG, 0.06f)

    /**
     * plan[k] = k 번째 슛(모두 합쳐, 돌아가는 순서)을 넣을지. 끝까지 스와이프로 → 결과·점수·순위 확인.
     */
    private fun playToEnd(a: BasketActivity, plan: (Int) -> Boolean) {
        val n = a.players.size
        val shots = a.game.shots
        val expected = IntArray(n)
        val streak = IntArray(n)
        for (k in 0 until n * shots) {
            waitReady(a)
            val p = k % n
            val r = k / n
            assertEquals("차례 ($k)", p, a.game.current)
            assertEquals("바퀴 ($k)", r, a.game.round)
            val shouldMake = plan(k)
            assertEquals("불꽃 표시 ($k)", Scoring.onFire(streak[p]), a.court.fire)
            assertEquals(Scoring.onFire(streak[p]), a.header.shownFire)
            if (shouldMake) make(a) else miss(a)
            assertEquals("스와이프로 슛 ($k)", k + 1, a.game.throws)
            assertSame(Phase.FLYING, a.game.phase)
            assertTrue("나는 동안 루프", a.looping)
            waitSettled(a)
            assertFalse("떨어지면 루프도 멈춤", a.looping)
            assertEquals("계산한 대로 골/놓침 ($k, ${a.game.distance}m)", shouldMake, a.game.outcome.made)
            expected[p] += Scoring.points(a.game.distanceAt(r), streak[p], shouldMake)
            streak[p] = Scoring.nextStreak(streak[p], shouldMake)
            assertEquals(expected[p], a.game.total(p))
        }
        waitFor("결과") { a.isFinishing }
        assertSame(Phase.OVER, a.game.phase)
        assertArrayEquals(expected, a.game.scores)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(Ranking.competition(expected.map { it.toLong() }), ranks)
        assertArrayEquals(IntArray(n) { it }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
    }

    @Test
    fun twoPlayersTenShotsBySwipes() {
        val a = launch(2)
        assertEquals(GameConfig.SHOTS, a.game.shots)
        assertFalse("소개 중엔 못 던짐", a.acceptingInput)
        // 0번: 거의 다 넣음(불꽃·보너스), 1번: 두 번에 한 번
        playToEnd(a) { k -> if (k % 2 == 0) k != 8 else (k / 2) % 2 == 0 }
        assertEquals(1, a.game.ranks()[0])
        assertEquals(2, a.game.ranks()[1])
    }

    @Test
    fun threePlayersToEnd() {
        BasketActivity.shotsOverride = 3
        val a = launch(3)
        // 0: 골·골·골, 1: 놓침만, 2: 골·놓침·골 (3슛 = 2.4m·4.8m·8.2m)
        playToEnd(a) { k -> when (k % 3) { 0 -> true; 1 -> false; else -> k / 3 != 1 } }
        assertArrayEquals(intArrayOf(1, 3, 2), a.game.ranks())
    }

    @Test
    fun fourPlayersToEndWithTie() {
        BasketActivity.shotsOverride = 2
        val a = launch(4)
        // 0·3 은 둘 다 골 (같은 점수 → 공동 1위), 1 은 첫 골만, 2 는 모두 놓침
        playToEnd(a) { k -> when (k % 4) { 0, 3 -> true; 1 -> k < 4; else -> false } }
        assertArrayEquals(intArrayOf(1, 3, 4, 1), a.game.ranks())
    }

    @Test
    fun fireAfterTwoMakesAndBonus() {
        val a = launch(2)
        waitReady(a)
        assertFalse(a.court.fire)
        make(a); waitSettled(a) // 가람 1골
        waitReady(a)
        miss(a); waitSettled(a) // 나래 놓침
        waitReady(a)
        assertFalse("1골은 아직", a.court.fire)
        make(a); waitSettled(a) // 가람 2골 → 불꽃
        assertTrue(a.game.outcome.fireStarted)
        waitFor("불꽃 모드 배너") { a.fireBanners == 1 }
        assertEquals(BasketActivity.FIRE_ON, a.lastBanner)
        waitReady(a)
        assertEquals("나래 차례엔 불꽃 없음", 1, a.game.current)
        assertFalse(a.court.fire)
        miss(a); waitSettled(a)
        waitReady(a)
        assertEquals(0, a.game.current)
        assertTrue("가람 차례 → 공에 불꽃", a.court.fire)
        assertTrue(a.header.shownFire)
        assertTrue(a.game.onFire(0))
        make(a)
        // 나는 동안 불꽃 입자가 뿜어져 나온다
        var maxAlive = 0
        repeat(200) {
            if (a.game.phase != Phase.FLYING) return@repeat
            idle(16)
            maxAlive = maxOf(maxAlive, a.court.fireAlive)
        }
        assertTrue("불꽃 입자 ($maxAlive)", maxAlive > 5)
        waitSettled(a)
        assertTrue(a.game.outcome.made)
        assertEquals("불꽃 보너스", 1, a.game.outcome.bonus)
        assertEquals(2 + 2 + 3, a.game.total(0))
        waitFor("결과 배너") { a.lastBanner == "3점!" }
        // 이제 놓치면 꺼짐
        waitReady(a)
        miss(a); waitSettled(a)
        waitReady(a)
        miss(a); waitSettled(a)
        assertTrue(a.game.outcome.fireLost)
        assertFalse(a.court.fire)
        assertFalse(a.game.onFire(0))
    }

    @Test
    fun loopStopsWhenSettled() {
        val a = launch(2)
        waitReady(a)
        assertFalse("대기 중엔 루프 없음", a.looping)
        val f0 = a.frames
        idle(2000)
        assertEquals("대기 중엔 프레임이 안 돈다", f0, a.frames)
        make(a)
        assertTrue(a.looping)
        waitSettled(a)
        assertFalse(a.looping)
        val f1 = a.frames
        assertTrue("나는 동안 프레임이 돌았다 (${f1 - f0})", f1 > f0 + 20)
        idle(3000)
        assertEquals("멈춘 뒤엔 다음 프레임이 예약되지 않음", f1, a.frames)
        assertFalse(a.game.court.flying)
    }

    @Test
    fun pauseFreezesBall() {
        val a = launch(2)
        waitReady(a)
        swipe(a, 80f, 0.5f)
        idle(250)
        assertSame(Phase.FLYING, a.game.phase)
        assertTrue(a.looping)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val bx = a.game.court.ball.x
        val by = a.game.court.ball.y
        val f = a.frames
        idle(4000)
        assertEquals("일시정지 중엔 공이 멈춤", bx, a.game.court.ball.x, 0f)
        assertEquals(by, a.game.court.ball.y, 0f)
        assertEquals(f, a.frames)
        assertSame(Phase.FLYING, a.game.phase)
        assertFalse("일시정지 중엔 못 던짐", a.acceptingInput)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        waitSettled(a)
        assertFalse(a.looping)
        assertTrue("다시 움직였다", a.game.court.ball.x != bx || a.game.court.ball.y != by)
    }

    @Test
    fun badSwipeDoesNotShoot() {
        val a = launch(2)
        waitReady(a)
        // 너무 짧게
        swipe(a, 75f, 0.5f, lengthRef = 0.04f)
        assertSame(Phase.AIM, a.game.phase)
        assertEquals(0, a.game.throws)
        assertEquals("더 길게 밀어요!", a.header.shownHint)
        // 아래로 밀기
        swipe(a, -90f, 0.5f, lengthRef = 0.3f)
        assertSame(Phase.AIM, a.game.phase)
        assertEquals(0, a.game.throws)
        assertEquals("골대 쪽 위로 밀어요!", a.header.shownHint)
        assertFalse(a.looping)
        // 제대로 밀면 슛
        make(a)
        assertEquals(1, a.game.throws)
        assertSame(Phase.FLYING, a.game.phase)
        // 나는 중엔 또 못 던짐
        assertFalse(a.acceptingInput)
        make(a)
        assertEquals(1, a.game.throws)
    }

    @Test
    fun headerShowsSeat() {
        val a = launch(3)
        waitReady(a)
        assertEquals("가람 차례", a.header.shownName)
        assertEquals("2.4m · 2점", a.header.shownSpot)
        assertNotNull(a.strip)
    }

    @Test
    fun moduleInfo() {
        assertEquals("basket", BasketModule.id)
        assertEquals("농구 슛", BasketModule.name)
        assertEquals(3, BasketModule.rules.size)
        assertEquals("6분", BasketModule.minutes)
        assertEquals(2..4, BasketModule.playerCounts)
        assertFalse(BasketModule.supports(1))
        assertFalse(BasketModule.supports(5))
        assertTrue(BasketModule.supports(3))
    }
}
