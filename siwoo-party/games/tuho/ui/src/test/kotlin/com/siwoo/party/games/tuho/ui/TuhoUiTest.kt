package com.siwoo.party.games.tuho.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.aim.Gauge
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.tuho.GameConfig
import com.siwoo.party.games.tuho.Outcome
import com.siwoo.party.games.tuho.Phase
import com.siwoo.party.games.tuho.TuhoField
import com.siwoo.party.games.tuho.TuhoGame
import com.siwoo.party.games.tuho.TuhoModule
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** 투호 화면: 게이지를 정해진 시각에 멈추는 대본으로 2·3·4인 끝까지 → 순위, 일시정지(게이지·비행 멈춤), 루프 정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class TuhoUiTest {

    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    companion object {
        /** 결과마다 (각도 게이지 시작 뒤 ms, 힘 게이지 시작 뒤 ms) — 같은 결과가 이어지는 구간의 가운데 */
        val plans: Map<Outcome, LongArray> by lazy {
            val out = HashMap<Outcome, LongArray>()
            for (o in Outcome.values()) {
                for (am in longArrayOf(500L, 700L, 1000L, 200L, 1150L)) {
                    if (out.containsKey(o)) break
                    val av = Gauge.triangle(am, GameConfig.ANGLE_PERIOD_MS)
                    var runStart = -1L
                    var best = -1L
                    var bestLen = 0L
                    val half = GameConfig.POWER_PERIOD_MS / 2
                    for (pm in 0L..half + 1) {
                        val hit = pm <= half && TuhoField.judgeGauge(av, Gauge.triangle(pm, GameConfig.POWER_PERIOD_MS)).outcome == o
                        if (hit && runStart < 0) runStart = pm
                        if (!hit && runStart >= 0) {
                            val len = pm - runStart
                            if (len > bestLen) { bestLen = len; best = runStart + len / 2 }
                            runStart = -1
                        }
                    }
                    if (best >= 0 && bestLen >= 3) out[o] = longArrayOf(am, best)
                }
            }
            out
        }
    }

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int): TuhoActivity {
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), TuhoModule, all.take(n))
        val a = Robolectric.buildActivity(TuhoActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(4000) { if (cond()) return; idle(10) }
        error("$what 가 되지 않음")
    }

    private fun tapCourt(a: TuhoActivity) {
        val t = SystemClock.uptimeMillis()
        val x = a.court.width * 0.6f
        val y = a.court.height * 0.4f
        a.court.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.court.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, x, y, 0))
    }

    /** 게임 시계가 target 이 될 때까지 정확히 흘림 */
    private fun idleUntil(a: TuhoActivity, target: Long) {
        val left = target - a.timers.now()
        assertTrue("이미 지난 시각 ($left)", left >= 0)
        if (left > 0) idle(left)
        assertEquals(target, a.timers.now())
    }

    /** 지금 사람이 결과 o 가 나오게 던지고 결과 처리까지 */
    private fun throwFor(a: TuhoActivity, o: Outcome) {
        val plan = plans[o] ?: error("$o 계획 없음")
        waitFor("각도 게이지") { a.acceptingAngle }
        val who = a.game.current
        val arrow = a.game.arrow
        assertTrue(a.header.shownName.contains(all[who].name))
        // ① 각도: 시작 뒤 plan[0] ms 에 터치
        val t0 = a.game.angleGauge.startMs
        idleUntil(a, t0 + plan[0])
        assertTrue(a.looping)
        tapCourt(a)
        assertSame(Phase.POWER, a.game.phase)
        assertEquals(Gauge.triangle(plan[0], GameConfig.ANGLE_PERIOD_MS), a.game.angleValue, 0f)
        assertFalse(a.acceptingAngle)
        // ② 힘: 시작 뒤 plan[1] ms 에 터치
        val t1 = a.game.powerGauge.startMs
        assertEquals(t0 + plan[0], t1)
        idleUntil(a, t1 + plan[1])
        tapCourt(a)
        assertSame(Phase.FLIGHT, a.game.phase)
        assertEquals(Gauge.triangle(plan[1], GameConfig.POWER_PERIOD_MS), a.game.powerValue, 0f)
        assertSame(o, a.game.shot!!.outcome)
        // 비행 중엔 더 눌러도 무시
        tapCourt(a)
        assertSame(Phase.FLIGHT, a.game.phase)
        waitFor("착지") { a.game.phase != Phase.FLIGHT }
        assertEquals(o.points, a.game.scores[who][arrow])
    }

    private fun playToEnd(n: Int, script: (player: Int, arrow: Int) -> Outcome, expectedRanks: IntArray) {
        val a = launch(n)
        assertFalse("소개 중엔 못 누름", a.acceptingAngle)
        val expected = IntArray(n)
        for (k in 0 until n * GameConfig.ARROWS) {
            val p = TuhoGame.playerOf(k, n)
            val arrow = TuhoGame.arrowOf(k, n)
            val o = script(p, arrow)
            throwFor(a, o)
            expected[p] += o.points
            assertEquals(expected[p], a.game.totals[p])
        }
        waitFor("게임 끝") { a.isFinishing }
        assertSame(Phase.OVER, a.game.phase)
        assertArrayEquals(expected, a.game.totals)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(expectedRanks, ranks)
        assertArrayEquals(TuhoGame.ranksOf(expected), ranks)
        assertArrayEquals(IntArray(n) { it }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        println("TUHO n=$n totals=${a.game.totals.toList()} ranks=${ranks.toList()}")
    }

    @Test
    fun playTwoPlayers() = playToEnd(2, { p, k ->
        if (p == 0) Outcome.MOUTH else if (k % 2 == 0) Outcome.EAR_NEAR else Outcome.FLOOR
    }, intArrayOf(1, 2))

    @Test
    fun playThreePlayers() = playToEnd(3, { p, k ->
        when (p) {
            0 -> if (k % 2 == 0) Outcome.EAR_FAR else Outcome.RIM
            1 -> Outcome.MOUTH
            else -> Outcome.BODY
        }
    }, intArrayOf(2, 1, 3))

    @Test
    fun playFourPlayers() = playToEnd(4, { p, k ->
        when (p) {
            0 -> Outcome.MOUTH
            1 -> if (k % 2 == 0) Outcome.EAR_NEAR else Outcome.EAR_FAR
            2 -> Outcome.MOUTH
            else -> if (k % 2 == 0) Outcome.FLOOR else Outcome.RIM
        }
    }, intArrayOf(2, 1, 2, 4))

    @Test
    fun stuckArrowsStayForTheRound() {
        val a = launch(2)
        throwFor(a, Outcome.MOUTH)
        assertEquals(1, a.game.stuckCount(0))
        throwFor(a, Outcome.EAR_FAR)
        assertEquals(1, a.game.stuckCount(0))
        assertEquals(1, a.game.stuckCount(2))
        // 다음 순번이 시작되면 치움
        waitFor("다음 순번 각도") { a.acceptingAngle }
        assertEquals(1, a.game.arrow)
        assertEquals(0, a.game.stuck.size)
        assertFalse("조준할 때 지난 화살 지움", a.court.hasFlight)
    }

    @Test
    fun loopStopsWhileWaiting() {
        val a = launch(2)
        waitFor("각도 게이지") { a.acceptingAngle }
        assertTrue(a.looping)
        throwFor(a, Outcome.BODY)
        // 튕김 연출 끝 → 루프 멈춤 → 대기 중엔 프레임이 하나도 안 돈다
        waitFor("루프 정지") { !a.looping }
        assertSame(Phase.RESULT, a.game.phase)
        val f = a.frames
        idle(600)
        assertEquals(f, a.frames)
    }

    @Test
    fun pauseFreezesGauge() {
        val a = launch(2)
        waitFor("각도 게이지") { a.acceptingAngle }
        val t0 = a.game.angleGauge.startMs
        idleUntil(a, t0 + 400L)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        assertFalse("일시정지 중엔 못 멈춤", a.acceptingAngle)
        val v = a.game.angleGauge.valueAt(a.timers.now())
        val f = a.frames
        idle(4000)
        assertEquals("게이지 바늘이 그대로", v, a.game.angleGauge.valueAt(a.timers.now()), 0f)
        assertEquals(f, a.frames)
        a.court.listener?.onCourtTap()
        assertSame("일시정지 중 터치 무시", Phase.ANGLE, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        // 게임 시계는 멈춘 시간을 빼므로, 시작 뒤 700ms 에 멈추면 정확히 그 값
        idleUntil(a, t0 + 700L)
        tapCourt(a)
        assertEquals(Gauge.triangle(700L, GameConfig.ANGLE_PERIOD_MS), a.game.angleValue, 0f)
        // 힘 게이지도 일시정지하면 멈춤
        val t1 = a.game.powerGauge.startMs
        idleUntil(a, t1 + 300L)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        val pv = a.game.powerGauge.valueAt(a.timers.now())
        idle(3000)
        assertEquals(pv, a.game.powerGauge.valueAt(a.timers.now()), 0f)
        assertFalse(a.acceptingPower)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.acceptingPower)
    }

    @Test
    fun pauseFreezesFlight() {
        val a = launch(2)
        val plan = plans[Outcome.MOUTH]!!
        waitFor("각도 게이지") { a.acceptingAngle }
        idleUntil(a, a.game.angleGauge.startMs + plan[0])
        tapCourt(a)
        idleUntil(a, a.game.powerGauge.startMs + plan[1])
        tapCourt(a)
        assertSame(Phase.FLIGHT, a.game.phase)
        idle(250)
        assertSame(Phase.FLIGHT, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.looping)
        val sec = a.court.flightSec(a.timers.now())
        assertTrue(sec > 0.0 && sec < a.game.shot!!.t)
        val f = a.frames
        idle(5000)
        assertEquals("비행이 멈춤", sec, a.court.flightSec(a.timers.now()), 0.0)
        assertEquals(f, a.frames)
        assertSame("일시정지 중엔 착지 안 함", Phase.FLIGHT, a.game.phase)
        assertEquals(-1, a.game.scores[0][0])
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        waitFor("착지") { a.game.phase == Phase.RESULT }
        assertEquals(GameConfig.MOUTH_POINTS, a.game.scores[0][0])
        assertEquals(1, a.game.stuckCount(0))
    }

    @Test
    fun moduleInfo() {
        assertEquals("tuho", TuhoModule.id)
        assertEquals("투호", TuhoModule.name)
        assertEquals(3, TuhoModule.rules.size)
        assertEquals("6분", TuhoModule.minutes)
        assertEquals(2..4, TuhoModule.playerCounts)
        assertTrue(TuhoModule.supports(4))
        assertFalse(TuhoModule.supports(5))
        assertEquals(0, TuhoModule.needsMultiTouch)
        assertSame(com.siwoo.party.common.ui.PlayMode.TURNS, TuhoModule.mode)
        assertSame(com.siwoo.party.common.ui.GameCategory.TIMING, TuhoModule.category)
    }
}
